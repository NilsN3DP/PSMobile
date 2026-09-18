package de.psmobile.net

import de.psmobile.shared.net.PrusaLinkRules.Auth
import de.psmobile.shared.net.LightingPrinterProfile
import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Speichert die eingerichteten PrusaLink-Drucker.
 *
 * Bewusst in den App-eigenen Einstellungen und nicht im Datadir von
 * libslic3r: Das sind Geraete des Nutzers, keine Slicer-Profile.
 *
 * Nicht geheime Metadaten liegen als JSON in den normalen Preferences.
 * API-Schluessel und Passwoerter liegen getrennt, per Android Keystore
 * mit AES-GCM verschluesselt in SecretStore.
 */
object PrinterStore {

    private const val PREFS = "psmobile"
    private const val KEY_PRINTERS = "prusalink_printers"
    private const val KEY_ONLY_LINKED = "only_linked_printers"
    private const val KEY_LOCAL_PAIRING_OPT_IN = "experimental_local_pairing_opt_in"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): List<PrusaLink.Printer> = runCatching {
        val arr = JSONArray(prefs(c).getString(KEY_PRINTERS, "[]"))
        var migrated = false
        val printers = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id", UUID.randomUUID().toString())
            val credentialRef = o.optString("credentialRef", "printer-$id")

            /* Einmalige Migration der alten Klartextstruktur. Erst das
             * Secret dauerhaft schreiben; save() entfernt die alten
             * JSON-Felder erst danach. */
            val legacyKey = o.optString("apiKey")
            val legacyUser = o.optString("username", PrusaLink.DEFAULT_USER)
            val legacyPassword = o.optString("password")
            if ((legacyKey.isNotBlank() || legacyPassword.isNotBlank()) &&
                SecretStore.get(c, credentialRef) == null) {
                SecretStore.put(
                    c,
                    credentialRef,
                    JSONObject().apply {
                        put("apiKey", legacyKey)
                        put("username", legacyUser)
                        put("password", legacyPassword)
                    }.toString(),
                )
                migrated = true
            }
            val secret = SecretStore.get(c, credentialRef)
                ?.let { JSONObject(it) }

            PrusaLink.Printer(
                id = id,
                name = o.optString("name"),
                host = o.optString("host"),
                // Alte Eintraege ohne "auth" hatten nur einen API-Key.
                auth = if (o.optString("auth", "") == "apikey")
                    Auth.API_KEY
                else if (!o.has("auth") && o.optString("apiKey").isNotBlank())
                    Auth.API_KEY
                else Auth.USER_PASSWORD,
                apiKey = secret?.optString("apiKey").orEmpty(),
                username = secret?.optString("username", PrusaLink.DEFAULT_USER)
                    ?: PrusaLink.DEFAULT_USER,
                password = secret?.optString("password").orEmpty(),
                presetName = o.optString("presetName"),
                storage = o.optString("storage", "usb"),
                allowInsecureHttp = o.optBoolean("allowInsecureHttp", false),
                lightingOptIn = o.optBoolean("lightingOptIn", false),
                // Ohne explizite Herkunft sicher sperren; neue Eintraege
                // erhalten den manuellen Standard im Printer-Konstruktor.
                lightingProfile = o.optString("lightingProfile")
                    .let { raw -> LightingPrinterProfile.entries.firstOrNull { it.name == raw } }
                    ?: LightingPrinterProfile.UNKNOWN,
                localExperimental = o.optBoolean("localExperimental", false),
                localHosts = o.optJSONArray("localHosts")?.let { hosts ->
                    (0 until hosts.length()).mapNotNull { hosts.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty(),
                localModel = o.optString("localModel"),
                localCapabilities = o.optJSONArray("localCapabilities")?.let { caps ->
                    (0 until caps.length()).mapNotNull { caps.optString(it).takeIf(String::isNotBlank) }.toSet()
                }.orEmpty(),
                localNozzleDiameter = if (o.has("localNozzleDiameter")) o.optDouble("localNozzleDiameter") else null,
                localNozzleMaterial = o.optString("localNozzleMaterial", "unknown"),
            )
        }
        if (migrated)
            save(c, printers)
        printers
    }.getOrDefault(emptyList())

    fun save(c: Context, printers: List<PrusaLink.Printer>) {
        val arr = JSONArray()
        printers.forEach { p ->
            val credentialRef = "printer-${p.id}"
            SecretStore.put(
                c,
                credentialRef,
                JSONObject().apply {
                    put("apiKey", p.apiKey)
                    put("username", p.username)
                    put("password", p.password)
                }.toString(),
            )
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("host", p.host)
                put("auth", if (p.auth == Auth.API_KEY) "apikey" else "userpass")
                put("credentialRef", credentialRef)
                put("presetName", p.presetName)
                put("storage", p.storage)
                put("allowInsecureHttp", p.allowInsecureHttp)
                put("lightingOptIn", p.lightingOptIn)
                put("lightingProfile", p.lightingProfile.name)
                put("localExperimental", p.localExperimental)
                put("localHosts", JSONArray(p.localHosts))
                put("localModel", p.localModel)
                put("localCapabilities", JSONArray(p.localCapabilities.toList()))
                p.localNozzleDiameter?.let { put("localNozzleDiameter", it) }
                put("localNozzleMaterial", p.localNozzleMaterial)
            })
        }
        /* Metadaten erst nach erfolgreicher Secret-Speicherung ersetzen. */
        prefs(c).edit(commit = true) { putString(KEY_PRINTERS, arr.toString()) }
    }

    fun add(c: Context, p: PrusaLink.Printer) = save(c, all(c) + p)

    /** Fügt ein lokales Gerät hinzu oder aktualisiert die bekannte Geräte-ID anhand von Modell/Host. */
    fun upsertLocal(c: Context, incoming: PrusaLink.Printer, pairingToken: String) {
        val current = all(c).toMutableList()
        val match = current.indexOfFirst { existing ->
            existing.localExperimental &&
                existing.localModel.equals(incoming.localModel, ignoreCase = true) &&
                (existing.localHosts + existing.host).intersect(incoming.localHosts + incoming.host).isNotEmpty()
        }
        val merged = if (match >= 0) {
            val existing = current[match]
            LocalPrinterMerge.withHost(existing.copy(
                name = incoming.name.ifBlank { existing.name },
                localModel = incoming.localModel.ifBlank { existing.localModel },
                localCapabilities = incoming.localCapabilities.ifEmpty { existing.localCapabilities },
                localNozzleDiameter = incoming.localNozzleDiameter ?: existing.localNozzleDiameter,
                localNozzleMaterial = incoming.localNozzleMaterial.ifBlank { existing.localNozzleMaterial },
                // Die Anmeldedaten sind der Grund, warum ueberhaupt neu
                // gekoppelt wird: der Drucker hat sie gerade frisch
                // ausgegeben (siehe PrusaLink.pairLocal). Vorher hielt
                // existing.copy hier die alten fest - nach einem
                // Werksreset oder einer Anmeldedaten-Rotation meldete
                // die App "gekoppelt", und jeder spaetere Zugriff
                // scheiterte still mit 401. iOS macht es richtig
                // (PrintersView.kopple uebernimmt ergebnis.printer ganz).
                auth = incoming.auth,
                username = incoming.username,
                password = incoming.password,
                allowInsecureHttp = incoming.allowInsecureHttp,
            ), incoming.host)
        } else incoming.copy(localExperimental = true, localHosts = (incoming.localHosts + incoming.host).distinct())
        if (match >= 0) current[match] = merged else current += merged
        save(c, current)
        setLocalPairingToken(c, merged.id, pairingToken)
    }

    fun remove(c: Context, id: String) {
        val remaining = all(c).filterNot { it.id == id }
        save(c, remaining)
        SecretStore.remove(c, "printer-$id")
    }

    /**
     * Alle Drucker weg, samt Geheimnissen - nur fuer
     * `-psm-reset-printers`. Gegenstueck zu der Stelle in
     * `PrinterStore.swift`, die dort `UserDefaults.removeObject` heisst.
     */
    fun entferneAlle(c: Context) {
        all(c).forEach { SecretStore.remove(c, "printer-${it.id}") }
        save(c, emptyList())
    }

    fun update(c: Context, p: PrusaLink.Printer) =
        save(c, all(c).map { if (it.id == p.id) p else it })

    /** Pairing token remains in the encrypted SecretStore and never in printer metadata JSON. */
    fun localPairingToken(c: Context, id: String): String? =
        SecretStore.get(c, "printer-$id")?.let { runCatching { JSONObject(it).optString("localPairingToken").takeIf(String::isNotBlank) }.getOrNull() }

    fun setLocalPairingToken(c: Context, id: String, token: String?) {
        val ref = "printer-$id"
        val current = SecretStore.get(c, ref)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        if (token.isNullOrBlank()) current.remove("localPairingToken") else current.put("localPairingToken", token)
        SecretStore.put(c, ref, current.toString())
    }

    /**
     * Nur Druckerprofile anzeigen, zu denen ein eingerichteter
     * PrusaLink-Drucker existiert. Wer drei Geraete hat, will nicht
     * durch zehn Duesenvarianten scrollen.
     */
    fun onlyLinked(c: Context): Boolean = prefs(c).getBoolean(KEY_ONLY_LINKED, false)

    fun setOnlyLinked(c: Context, v: Boolean) =
        prefs(c).edit { putBoolean(KEY_ONLY_LINKED, v) }

    fun localPairingOptIn(c: Context): Boolean = prefs(c).getBoolean(KEY_LOCAL_PAIRING_OPT_IN, false)

    fun setLocalPairingOptIn(c: Context, enabled: Boolean) =
        prefs(c).edit { putBoolean(KEY_LOCAL_PAIRING_OPT_IN, enabled) }
}
