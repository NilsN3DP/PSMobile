package de.psmobile.shared.net

import de.psmobile.shared.rules.SimpleModeState

/** Herkunft des Druckerprofils. Nur ein bewusst lokal eingerichtetes
 * physisches Geraet darf die experimentelle Lichtfunktion erhalten. */
enum class LightingPrinterProfile { MANUAL_PHYSICAL, CLOUD, DEMO, SIMULATED, UNKNOWN }

/** Ergebnis der streng fail-closed Capability-Pruefung. */
enum class LightingCapability { SUPPORTED, UNSUPPORTED }

/** Die Bedienarten, unabhaengig vom spaeter dokumentierten Hardware-Protokoll. */
enum class LightingMode { AUTO, MANUAL, ANIMATION, OFF }

/** Status der letzten Capability-/Verbindungspruefung fuer die UI und das Sende-Gate. */
enum class LightingConnectionStatus { ONLINE, OFFLINE, PROBE_FAILED }

/** Warum kein Lichtbefehl gesendet werden darf. */
enum class LightingCommandGate { ALLOWED, DISABLED, UNSUPPORTED, OFFLINE, PROBE_FAILED }

/** Zustande, die die Automatik kennt. Unbekanntes wird bewusst nicht gemappt. */
enum class LightingPrinterState { IDLE, HEATING, PRINTING, PAUSED, ERROR, COMPLETED, UNKNOWN }

data class LightingColor(val red: Int, val green: Int, val blue: Int) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255)
    }
}

/** Nicht geheime, pro Drucker persistierbare Voreinstellung. */
data class LightingSettings(
    val optIn: Boolean = false,
    val mode: LightingMode = LightingMode.AUTO,
    val brightness: Int = 100,
    val color: LightingColor = LightingColor(255, 255, 255),
    val animation: String? = null,
) {
    init { require(brightness in 0..100) }
}

/** Werte, die von einem erfolgreichen und vertrauenswuerdigen Probe stammen muessen. */
data class LightingProbe(
    val profile: LightingPrinterProfile,
    val firmware: String?,
    val model: String?,
)

/** Eine Automatikvorgabe. Ein null-Ergebnis bedeutet: bestehenden Lichtzustand nicht anfassen. */
data class LightingAutomaticSetting(val color: LightingColor, val brightness: Int)

/**
 * Explizite Grenze fuer die erst noch zu belegende Hardware-Integration.
 *
 * Eine Plattform darf diese Schnittstelle erst mit einem Adapter verbinden,
 * wenn der Prusa-CFW-Endpunkt samt Request-/Response-Vertrag autoritativ
 * dokumentiert ist. Bis dahin ist kein HTTP-Pfad hinterlegt.
 */
interface LightingEndpointAdapter {
    fun dispatch(settings: LightingSettings): LightingEndpointResult
}

sealed interface LightingEndpointResult {
    data class BlockedBySafetyGate(val gate: LightingCommandGate) : LightingEndpointResult
    data object BlockedPendingDocumentedEndpoint : LightingEndpointResult
}

/** Sicherer Platzhalter: selbst bei einem erlaubten UI-Gate wird nichts an Hardware gesendet. */
object PendingDocumentedLightingEndpointAdapter : LightingEndpointAdapter {
    override fun dispatch(settings: LightingSettings): LightingEndpointResult =
        LightingEndpointResult.BlockedPendingDocumentedEndpoint
}

/** Gemeinsame fail-closed Regeln fuer die experimentelle Beleuchtung. */
object PrusaLinkLighting {
    private val supportedFirmware = Regex("""^6\.5\.3(?:[-+].*)?$""")

    fun capability(probe: LightingProbe): LightingCapability = when {
        probe.profile != LightingPrinterProfile.MANUAL_PHYSICAL -> LightingCapability.UNSUPPORTED
        probe.firmware?.trim()?.matches(supportedFirmware) != true -> LightingCapability.UNSUPPORTED
        !isCoreOneMini(probe.model) -> LightingCapability.UNSUPPORTED
        else -> LightingCapability.SUPPORTED
    }

    fun commandGate(
        settings: LightingSettings,
        capability: LightingCapability,
        connection: LightingConnectionStatus,
    ): LightingCommandGate = when {
        !settings.optIn -> LightingCommandGate.DISABLED
        capability != LightingCapability.SUPPORTED -> LightingCommandGate.UNSUPPORTED
        connection == LightingConnectionStatus.PROBE_FAILED -> LightingCommandGate.PROBE_FAILED
        connection != LightingConnectionStatus.ONLINE -> LightingCommandGate.OFFLINE
        else -> LightingCommandGate.ALLOWED
    }

    fun resetToAuto(settings: LightingSettings): LightingSettings =
        settings.copy(mode = LightingMode.AUTO, animation = null)

    fun automatic(state: LightingPrinterState): LightingAutomaticSetting? = when (state) {
        LightingPrinterState.IDLE -> LightingAutomaticSetting(LightingColor(255, 255, 255), 25)
        LightingPrinterState.HEATING -> LightingAutomaticSetting(LightingColor(255, 64, 0), 70)
        LightingPrinterState.PRINTING -> LightingAutomaticSetting(LightingColor(0, 180, 255), 70)
        LightingPrinterState.PAUSED -> LightingAutomaticSetting(LightingColor(255, 180, 0), 70)
        LightingPrinterState.ERROR -> LightingAutomaticSetting(LightingColor(255, 0, 0), 100)
        LightingPrinterState.COMPLETED -> LightingAutomaticSetting(LightingColor(0, 200, 80), 60)
        LightingPrinterState.UNKNOWN -> null
    }

    private fun isCoreOneMini(model: String?): Boolean = model
        ?.lowercase()
        ?.filter { it.isLetterOrDigit() }
        ?.contains("coreonemini") == true
}

/**
 * Alles an der PrusaLink-Anbindung, was keine Steckdose braucht.
 *
 * Adressen zusammensetzen, Dateinamen entschaerfen, Antwortcodes
 * deuten - das sind Entscheidungen, keine Netzwerkaufrufe. Sie stehen
 * hier, damit beide Apps dieselben treffen. Was uebrig bleibt, ist auf
 * jeder Plattform eine andere Bibliothek: HttpURLConnection auf
 * Android, URLSession auf iOS.
 *
 * Die Endpunkte und Kopfzeilen stammen aus PrusaSlicers OctoPrint.cpp,
 * nicht aus einer nachgebauten Dokumentation.
 */
object PrusaLinkRules {

    /** Vorgabe bei PrusaLink; der Nutzer kann sie aendern. */
    const val DEFAULT_USER = "maker"

    const val TIMEOUT_MS = 15_000

    enum class Auth { API_KEY, USER_PASSWORD }

    /** Zustand abfragen - dient zugleich als Test der Anmeldedaten. */
    const val STATUS_PATH = "/api/v1/status"

    /**
     * Ohne Schema gilt HTTPS.
     *
     * Ein Drucker im eigenen Netz wird gern als nackte IP eingetragen.
     * Daraus http:// zu machen waere bequemer und falsch: die
     * Zugangsdaten gingen im Klartext ueber das Netz.
     */
    fun baseUrl(host: String): String {
        val h = host.trim().trimEnd('/')
        return if (h.startsWith("http://", ignoreCase = true) ||
            h.startsWith("https://", ignoreCase = true)
        ) {
            h
        } else {
            "https://$h"
        }
    }

    /**
     * Warum die Verbindung so nicht zustande kommt. Null heisst: in
     * Ordnung.
     */
    fun transportError(host: String, allowInsecureHttp: Boolean): String? = when {
        host.isBlank() -> SimpleModeState.text("Address missing", "Adresse fehlt")
        baseUrl(host).startsWith("http://", ignoreCase = true) && !allowInsecureHttp ->
            SimpleModeState.text(
                "HTTP is not enabled for this printer",
                "HTTP ist für diesen Drucker nicht freigegeben",
            )
        else -> null
    }

    /** Sind die Anmeldedaten vollstaendig? */
    fun isComplete(
        host: String,
        allowInsecureHttp: Boolean,
        auth: Auth,
        apiKey: String,
        username: String,
        password: String,
    ): Boolean = transportError(host, allowInsecureHttp) == null && when (auth) {
        Auth.API_KEY -> apiKey.isNotBlank()
        Auth.USER_PASSWORD -> username.isNotBlank() && password.isNotBlank()
    }

    /**
     * Der Pfad fuer den Upload.
     *
     * Der Dateiname wird entschaerft: er kommt aus einem Modellnamen und
     * damit letztlich von irgendeinem Downloadportal. Ein Schraegstrich
     * darin waere ein anderer Pfad auf dem Drucker.
     */
    fun uploadPath(storage: String, fileName: String): String {
        val sicher = fileName.map {
            if (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') it else '_'
        }.joinToString("")
        return "/api/v1/files/$storage/$sicher"
    }

    /** Die Kopfzeile fuer "nach dem Hochladen drucken". PrusaLink erwartet ?1 / ?0. */
    fun printAfterHeader(printAfter: Boolean): String = if (printAfter) "?1" else "?0"

    /**
     * Was ein Antwortcode bedeutet. Null heisst: hat geklappt.
     *
     * Bewusst je Code ein eigener Satz. "HTTP 409" sagt dem Nutzer
     * nichts; dass die Datei schon da ist oder der Drucker gerade
     * beschaeftigt ist, sagt ihm, was er tun kann.
     */
    fun probeError(code: Int, auth: Auth): String? = when {
        code in 200..299 -> null
        code == 401 -> if (auth == Auth.USER_PASSWORD) {
            SimpleModeState.text(
                "Username or password rejected",
                "Benutzername oder Passwort abgelehnt",
            )
        } else {
            SimpleModeState.text("API key rejected", "API-Schlüssel abgelehnt")
        }
        code == 403 -> SimpleModeState.text("Access denied (HTTP 403)", "Zugriff verweigert (HTTP 403)")
        code == 404 -> SimpleModeState.text(
            "No PrusaLink at this address",
            "Kein PrusaLink unter dieser Adresse",
        )
        else -> "HTTP $code"
    }

    fun uploadError(code: Int): String? = when {
        code in 200..299 -> null
        code == 401 -> SimpleModeState.text("Login rejected", "Anmeldung abgelehnt")
        code == 409 -> SimpleModeState.text(
            "File already exists or printer busy",
            "Datei existiert bereits oder Drucker beschäftigt",
        )
        code == 413 -> SimpleModeState.text(
            "File too large for the storage",
            "Datei zu groß für den Speicher",
        )
        else -> "HTTP $code"
    }

    fun uploadOk(printAfter: Boolean): String = if (printAfter) {
        SimpleModeState.text("Sent, print started", "Gesendet, Druck gestartet")
    } else {
        SimpleModeState.text("Sent", "Gesendet")
    }

    /**
     * Aus der Statusantwort etwas Lesbares machen.
     *
     * Bewusst ohne JSON-Bibliothek: gebraucht werden zwei Felder aus
     * einer Antwort, deren Aufbau bekannt ist. Faellt eines aus, steht
     * da eben nur "verbunden" - und das stimmt dann auch.
     */
    fun describeStatus(body: String): String {
        val zustand = feld(body, "state")
        val duese = zahl(body, "temp_nozzle")
        return buildString {
            append(zustand?.ifBlank { null } ?: SimpleModeState.text("connected", "verbunden"))
            if (duese != null) {
                append("  ·  " + SimpleModeState.text("Nozzle ", "Düse ") + duese.toInt() + " °C")
            }
        }
    }

    private fun feld(body: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

    private fun zahl(body: String, name: String): Double? =
        Regex("\"$name\"\\s*:\\s*(-?[0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
}
