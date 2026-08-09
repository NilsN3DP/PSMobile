package de.psmobile.net

import android.util.Log
import de.psmobile.shared.net.DigestAuth
import de.psmobile.shared.net.PrusaLinkRules
import de.psmobile.shared.net.PrusaLinkRules.Auth
import de.psmobile.shared.net.LightingPrinterProfile
import de.psmobile.shared.net.LightingCapability
import de.psmobile.shared.net.LightingConnectionStatus
import de.psmobile.shared.net.LightingEndpointResult
import de.psmobile.shared.net.LightingSettings
import de.psmobile.shared.net.PendingDocumentedLightingEndpointAdapter
import de.psmobile.shared.net.PrusaLinkLighting
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * PrusaLink-Anbindung.
 *
 * Bewusst nativ statt ueber libcurl: libslic3r enthaelt keine einzige
 * curl-Referenz, der Netzwerkcode sitzt beim Desktop nur in der
 * GUI-Schicht. Siehe docs/entscheidungen.md, E-09.
 *
 * Zwei Anmeldeverfahren, genau wie PrusaSlicer sie in OctoPrint.cpp
 * unterscheidet (`PrusaLink::set_auth`):
 *   - aeltere Firmware: Kopfzeile `X-Api-Key`
 *   - ab PrusaLink 0.7: HTTP-Digest mit Benutzername und Passwort
 * Digest, nicht Basic - Basic wird abgelehnt.
 *
 * Endpunkte:
 *   GET  /api/v1/status                    Zustand, dient als Verbindungstest
 *   PUT  /api/v1/files/{storage}/{name}    Datei hochladen
 *
 * Was hier steht, ist die Verbindung selbst. Adressen zusammensetzen,
 * Dateinamen entschaerfen und Antwortcodes deuten sind Entscheidungen
 * und liegen in [PrusaLinkRules] im gemeinsamen Modul - sonst
 * beantwortet iOS dieselbe Frage anders, ohne dass es auffaellt.
 */
object PrusaLink {

    private const val TAG = "PrusaLink"

    /** Vorgabe bei PrusaLink; der Nutzer kann sie aendern. */
    const val DEFAULT_USER = PrusaLinkRules.DEFAULT_USER



    data class Printer(
        val id: String,
        val name: String,
        val host: String,               // URL; ohne Schema gilt HTTPS
        val auth: Auth = Auth.USER_PASSWORD,
        val apiKey: String = "",
        val username: String = DEFAULT_USER,
        val password: String = "",
        /** Preset-Name in PSMobile, damit Profil und Geraet zusammenfinden. */
        val presetName: String = "",
        val storage: String = "usb",
        val allowInsecureHttp: Boolean = false,
        /** Nicht geheime, pro Drucker getrennte Experimental-Einwilligung. */
        val lightingOptIn: Boolean = false,
        /** Alte Eintraege bleiben unbekannt und erhalten damit keine Capability. */
        val lightingProfile: LightingPrinterProfile = LightingPrinterProfile.MANUAL_PHYSICAL,
    ) {
        val baseUrl: String get() = PrusaLinkRules.baseUrl(host)

        val transportError: String?
            get() = PrusaLinkRules.transportError(host, allowInsecureHttp)

        /** Anmeldedaten vollstaendig? */
        val isComplete: Boolean
            get() = PrusaLinkRules.isComplete(
                host, allowInsecureHttp, auth, apiKey, username, password,
            )
    }

    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Error(val message: String) : Result
    }

    /**
     * Einzige Lighting-Grenze des Android-Clients.
     *
     * Erst die fail-closed Regeln pruefen, dann den bewusst leeren
     * Adapter. Damit kann kein künftiger UI-Aufrufer versehentlich einen
     * Pfad, Header oder ein Geheimnis an eine undokumentierte CFW-API
     * senden.
     */
    fun lightingEndpointResult(
        p: Printer,
        probedCapability: LightingCapability,
        connection: LightingConnectionStatus,
    ): LightingEndpointResult {
        val capability = if (p.lightingProfile == LightingPrinterProfile.MANUAL_PHYSICAL) {
            probedCapability
        } else {
            LightingCapability.UNSUPPORTED
        }
        val gate = PrusaLinkLighting.commandGate(
            LightingSettings(optIn = p.lightingOptIn), capability, connection,
        )
        return if (gate == de.psmobile.shared.net.LightingCommandGate.ALLOWED) {
            PendingDocumentedLightingEndpointAdapter.dispatch(LightingSettings(optIn = p.lightingOptIn))
        } else {
            LightingEndpointResult.BlockedBySafetyGate(gate)
        }
    }

    /**
     * Digest-Herausforderungen je Drucker merken.
     *
     * Wichtig fuer den Upload: Wer erst sendet und dann eine 401 bekommt,
     * hat die Datei bereits umsonst uebertragen. Deshalb wird die nonce
     * aus einer billigen Anfrage geholt und fuer den PUT wiederverwendet -
     * bei qop=auth ist das mit hochgezaehltem nc ausdruecklich erlaubt.
     */
    private val challenges = ConcurrentHashMap<String, DigestAuth.Challenge>()

    /** Zustand abfragen. Dient zugleich als Test der Anmeldedaten. */
    fun probe(p: Printer): Result = try {
        p.transportError?.let { return Result.Error(it) }
        val (code, body) = request(p, PrusaLinkRules.STATUS_PATH, "GET")
        PrusaLinkRules.probeError(code, p.auth)
            ?.let { Result.Error(it) }
            ?: Result.Ok(PrusaLinkRules.describeStatus(body))
    } catch (t: Throwable) {
        Result.Error(t.message ?: "nicht erreichbar")
    }

    /**
     * Datei hochladen.
     *
     * @param printAfter Druck direkt starten. PrusaLink erwartet die
     *                   Kopfzeile im Format `?1` bzw. `?0`, nicht true/false.
     */
    fun upload(p: Printer, file: File, remoteName: String, printAfter: Boolean): Result = try {
        p.transportError?.let { return Result.Error(it) }
        val path = PrusaLinkRules.uploadPath(p.storage, remoteName)

        // Bei Digest zuerst eine billige Anfrage, um die nonce zu holen -
        // sonst ginge die Datei beim ersten Versuch ins Leere.
        if (p.auth == Auth.USER_PASSWORD && challenges[p.id] == null)
            runCatching { request(p, PrusaLinkRules.STATUS_PATH, "GET") }

        var (code, body) = put(p, path, file, printAfter)

        // Abgelaufene nonce: einmal neu holen und wiederholen.
        if (code == 401 && p.auth == Auth.USER_PASSWORD) {
            challenges.remove(p.id)
            runCatching { request(p, PrusaLinkRules.STATUS_PATH, "GET") }
            val retry = put(p, path, file, printAfter)
            code = retry.first
            body = retry.second
        }

        PrusaLinkRules.uploadError(code)
            ?.let { Result.Error(if (code >= 500) "$it: ${body.take(200)}" else it) }
            ?: Result.Ok(PrusaLinkRules.uploadOk(printAfter))
    } catch (t: Throwable) {
        Log.w(TAG, "Upload fehlgeschlagen", t)
        Result.Error(t.message ?: "Übertragung fehlgeschlagen")
    }

    // --- Innereien --------------------------------------------------------

    private fun request(p: Printer, path: String, method: String): Pair<Int, String> {
        var c = open(p, path, method)
        var code = c.responseCode

        // Erste Digest-Herausforderung einsammeln und wiederholen.
        if (code == 401 && p.auth == Auth.USER_PASSWORD) {
            val ch = DigestAuth.parseChallenge(c.getHeaderField("WWW-Authenticate"))
            c.disconnect()
            if (ch != null) {
                challenges[p.id] = ch
                c = open(p, path, method)
                code = c.responseCode
            } else {
                return 401 to ""
            }
        }

        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        return code to body
    }

    private fun put(p: Printer, path: String, file: File, printAfter: Boolean): Pair<Int, String> {
        val c = open(p, path, "PUT")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setRequestProperty("Print-After-Upload", PrusaLinkRules.printAfterHeader(printAfter))
        c.setRequestProperty("Overwrite", "?1")
        c.setFixedLengthStreamingMode(file.length())

        file.inputStream().use { input -> c.outputStream.use { out -> input.copyTo(out) } }

        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        return code to body
    }

    private fun open(p: Printer, path: String, method: String): HttpURLConnection =
        (URL(p.baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = PrusaLinkRules.TIMEOUT_MS
            readTimeout = PrusaLinkRules.TIMEOUT_MS
            setRequestProperty("Accept", "application/json")

            when (p.auth) {
                Auth.API_KEY -> setRequestProperty("X-Api-Key", p.apiKey)
                Auth.USER_PASSWORD -> challenges[p.id]?.let { ch ->
                    setRequestProperty(
                        "Authorization",
                        DigestAuth.authorization(ch, p.username, p.password, method, path),
                    )
                }
            }
        }
}
