package de.psmobile.net

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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
 */
object PrusaLink {

    private const val TAG = "PrusaLink"
    private const val TIMEOUT_MS = 15_000

    /** Vorgabe bei PrusaLink; der Nutzer kann sie aendern. */
    const val DEFAULT_USER = "maker"

    enum class Auth { API_KEY, USER_PASSWORD }

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
    ) {
        val baseUrl: String
            get() = if (host.startsWith("http://", ignoreCase = true) ||
                        host.startsWith("https://", ignoreCase = true))
                host.trimEnd('/')
            else
                "https://${host.trimEnd('/')}"

        val transportError: String?
            get() = when {
                host.isBlank() -> "Adresse fehlt"
                baseUrl.startsWith("http://", ignoreCase = true) &&
                    !allowInsecureHttp ->
                    "HTTP ist für diesen Drucker nicht freigegeben"
                else -> null
            }

        /** Anmeldedaten vollstaendig? */
        val isComplete: Boolean
            get() = transportError == null && when (auth) {
                Auth.API_KEY -> apiKey.isNotBlank()
                Auth.USER_PASSWORD -> username.isNotBlank() && password.isNotBlank()
            }
    }

    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Error(val message: String) : Result
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
        val (code, body) = request(p, "/api/v1/status", "GET")
        when (code) {
            in 200..299 -> Result.Ok(describe(body))
            401 -> Result.Error(
                if (p.auth == Auth.USER_PASSWORD)
                    "Benutzername oder Passwort abgelehnt"
                else "API-Schlüssel abgelehnt")
            403 -> Result.Error("Zugriff verweigert (HTTP 403)")
            404 -> Result.Error("Kein PrusaLink unter dieser Adresse")
            else -> Result.Error("HTTP $code")
        }
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
        val safe = remoteName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "/api/v1/files/${p.storage}/$safe"

        // Bei Digest zuerst eine billige Anfrage, um die nonce zu holen -
        // sonst ginge die Datei beim ersten Versuch ins Leere.
        if (p.auth == Auth.USER_PASSWORD && challenges[p.id] == null)
            runCatching { request(p, "/api/v1/status", "GET") }

        var (code, body) = put(p, path, file, printAfter)

        // Abgelaufene nonce: einmal neu holen und wiederholen.
        if (code == 401 && p.auth == Auth.USER_PASSWORD) {
            challenges.remove(p.id)
            runCatching { request(p, "/api/v1/status", "GET") }
            val retry = put(p, path, file, printAfter)
            code = retry.first
            body = retry.second
        }

        when (code) {
            in 200..299 -> Result.Ok(
                if (printAfter) "Gesendet, Druck gestartet" else "Gesendet")
            401 -> Result.Error("Anmeldung abgelehnt")
            409 -> Result.Error("Datei existiert bereits oder Drucker beschäftigt")
            413 -> Result.Error("Datei zu groß für den Speicher")
            else -> Result.Error("HTTP $code: ${body.take(200)}")
        }
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
        c.setRequestProperty("Print-After-Upload", if (printAfter) "?1" else "?0")
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
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
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

    /** Aus der Statusantwort etwas Lesbares machen. */
    private fun describe(body: String): String = runCatching {
        val printer = JSONObject(body).optJSONObject("printer")
        val state = printer?.optString("state").orEmpty()
        val nozzle = printer?.optDouble("temp_nozzle", Double.NaN) ?: Double.NaN
        buildString {
            append(state.ifBlank { "verbunden" })
            if (!nozzle.isNaN())
                append("  ·  Düse ${String.format(Locale.ROOT, "%.0f", nozzle)} °C")
        }
    }.getOrDefault("verbunden")
}
