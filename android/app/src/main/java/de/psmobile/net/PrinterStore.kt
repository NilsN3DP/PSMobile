package de.psmobile.net

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
    private const val KEY_BACKUP_TREE = "backup_tree_uri"

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
                    PrusaLink.Auth.API_KEY
                else if (!o.has("auth") && o.optString("apiKey").isNotBlank())
                    PrusaLink.Auth.API_KEY
                else PrusaLink.Auth.USER_PASSWORD,
                apiKey = secret?.optString("apiKey").orEmpty(),
                username = secret?.optString("username", PrusaLink.DEFAULT_USER)
                    ?: PrusaLink.DEFAULT_USER,
                password = secret?.optString("password").orEmpty(),
                presetName = o.optString("presetName"),
                storage = o.optString("storage", "usb"),
                allowInsecureHttp = o.optBoolean("allowInsecureHttp", false),
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
                put("auth", if (p.auth == PrusaLink.Auth.API_KEY) "apikey" else "userpass")
                put("credentialRef", credentialRef)
                put("presetName", p.presetName)
                put("storage", p.storage)
                put("allowInsecureHttp", p.allowInsecureHttp)
            })
        }
        /* Metadaten erst nach erfolgreicher Secret-Speicherung ersetzen. */
        prefs(c).edit(commit = true) { putString(KEY_PRINTERS, arr.toString()) }
    }

    fun add(c: Context, p: PrusaLink.Printer) = save(c, all(c) + p)

    fun remove(c: Context, id: String) {
        val remaining = all(c).filterNot { it.id == id }
        save(c, remaining)
        SecretStore.remove(c, "printer-$id")
    }

    fun update(c: Context, p: PrusaLink.Printer) =
        save(c, all(c).map { if (it.id == p.id) p else it })

    /**
     * Nur Druckerprofile anzeigen, zu denen ein eingerichteter
     * PrusaLink-Drucker existiert. Wer drei Geraete hat, will nicht
     * durch zehn Duesenvarianten scrollen.
     */
    fun onlyLinked(c: Context): Boolean = prefs(c).getBoolean(KEY_ONLY_LINKED, false)

    fun setOnlyLinked(c: Context, v: Boolean) =
        prefs(c).edit { putBoolean(KEY_ONLY_LINKED, v) }

    /** Zielordner fuer die Sicherung gesendeter Dateien, als SAF-Baum-URI. */
    fun backupTree(c: Context): String? = prefs(c).getString(KEY_BACKUP_TREE, null)

    fun setBackupTree(c: Context, uri: String?) =
        prefs(c).edit { putString(KEY_BACKUP_TREE, uri) }
}
