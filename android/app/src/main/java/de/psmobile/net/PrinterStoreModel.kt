package de.psmobile.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import de.psmobile.shared.net.PrusaLinkRules.Auth

/**
 * Compose-beobachtbarer Zugang zu den eingerichteten Druckern -
 * Gegenstueck zu `PrinterStore` in `ios/PSMobile/Networking/PrinterStore.swift`
 * (ObservableObject mit `printers`, `upsert`, `remove`, `secret(for:)`,
 * `setSecret(_:for:)`).
 *
 * Die Wahrheit liegt weiterhin in [PrinterStore] (Metadaten als JSON,
 * Geheimnisse im Keystore ueber [SecretStore]). Auf Android tragen die
 * [PrusaLink.Printer]-Werte ihre Geheimnisse in `apiKey`/`password`
 * selbst; das iOS-`Secret(apiKey, password)` wird auf genau diese
 * Felder abgebildet.
 *
 * Der Host-Typ (PrusaLink/OctoPrint) hat in [PrusaLink.Printer] kein
 * Feld; er liegt hier in denselben Preferences, je Drucker-Kennung.
 */
class PrinterStoreModel(
    context: Context,
    schalter: de.psmobile.Startargumente = de.psmobile.Startargumente.LEER,
) {

    /** Womit der Drucker spricht - `PrusaLinkClient.HostType` auf iOS. */
    enum class HostType(val rawValue: String) {
        PRUSA_LINK("prusaLink"),
        OCTOPRINT("octoprint");

        companion object {
            fun from(raw: String?): HostType =
                entries.firstOrNull { it.rawValue == raw } ?: PRUSA_LINK
        }
    }

    /** Was nie in den normalen Einstellungen landen darf - `PrusaLinkClient.Secret`. */
    data class Secret(val apiKey: String = "", val password: String = "")

    private val app: Context = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var printers: List<PrusaLink.Printer> by mutableStateOf(PrinterStore.all(app))
        private set

    private val hostTypes = mutableStateMapOf<String, HostType>()

    init {
        // Die UI-Tests brauchen eine leere Liste, sonst faengt der
        // zweite Durchlauf mit den Druckern des ersten an und prueft
        // nichts mehr. Nur ueber ein Startargument - eine Einstellung
        // dafuer waere ein Schalter, mit dem sich versehentlich alles
        // loeschen liesse. Zeichengleich zu `PrinterStore.swift`.
        if (schalter.contains("-psm-reset-printers")) {
            PrinterStore.entferneAlle(app)
            printers = PrinterStore.all(app)
        }
        printers.forEach { hostTypes[it.id] = HostType.from(prefs.getString(hostTypeKey(it.id), null)) }
    }

    /** Neu aus den Preferences lesen - falls ein anderer Weg gespeichert hat. */
    fun reload() {
        printers = PrinterStore.all(app)
        printers.forEach { p ->
            if (hostTypes[p.id] == null)
                hostTypes[p.id] = HostType.from(prefs.getString(hostTypeKey(p.id), null))
        }
    }

    fun upsert(p: PrusaLink.Printer) {
        val liste = if (printers.any { it.id == p.id })
            printers.map { if (it.id == p.id) p else it }
        else printers + p
        PrinterStore.save(app, liste)
        printers = liste
        if (hostTypes[p.id] == null)
            hostTypes[p.id] = HostType.from(prefs.getString(hostTypeKey(p.id), null))
    }

    fun remove(p: PrusaLink.Printer) {
        // Die Zugangsdaten muessen mit weg - PrinterStore.remove raeumt
        // den SecretStore-Eintrag ab.
        PrinterStore.remove(app, p.id)
        prefs.edit { remove(hostTypeKey(p.id)) }
        hostTypes.remove(p.id)
        printers = PrinterStore.all(app)
    }

    /**
     * Das Geheimnis zum gewaehlten Verfahren. Beide Verfahren haben ihr
     * eigenes Feld - wer von Passwort auf API-Schluessel wechselt,
     * verliert das andere nicht.
     */
    fun secret(p: PrusaLink.Printer): Secret {
        val gespeichert = printers.firstOrNull { it.id == p.id } ?: p
        return if (p.auth == Auth.API_KEY) Secret(apiKey = gespeichert.apiKey, password = "")
        else Secret(apiKey = "", password = gespeichert.password)
    }

    fun setSecret(secret: Secret, p: PrusaLink.Printer) {
        val gespeichert = printers.firstOrNull { it.id == p.id } ?: p
        val neu = if (p.auth == Auth.API_KEY) gespeichert.copy(apiKey = secret.apiKey)
        else gespeichert.copy(password = secret.password)
        upsert(neu)
    }

    fun hostType(p: PrusaLink.Printer): HostType =
        hostTypes[p.id] ?: HostType.from(prefs.getString(hostTypeKey(p.id), null))

    fun setHostType(type: HostType, p: PrusaLink.Printer) {
        prefs.edit { putString(hostTypeKey(p.id), type.rawValue) }
        hostTypes[p.id] = type
    }

    private fun hostTypeKey(id: String) = "$KEY_HOST_TYPE.$id"

    private companion object {
        const val PREFS = "psmobile"
        const val KEY_HOST_TYPE = "printer_host_type"
    }
}
