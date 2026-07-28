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
 * Der API-Schluessel liegt im privaten App-Speicher. Fuer eine
 * Veroeffentlichung gehoert er in EncryptedSharedPreferences - als
 * offener Punkt in docs/07-stopp-punkte.md vermerkt.
 */
object PrinterStore {

    private const val PREFS = "psmobile"
    private const val KEY_PRINTERS = "prusalink_printers"
    private const val KEY_ONLY_LINKED = "only_linked_printers"
    private const val KEY_BACKUP_TREE = "backup_tree_uri"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): List<PrusaLink.Printer> = runCatching {
        val arr = JSONArray(prefs(c).getString(KEY_PRINTERS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PrusaLink.Printer(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                host = o.optString("host"),
                apiKey = o.optString("apiKey"),
                presetName = o.optString("presetName"),
                storage = o.optString("storage", "usb"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(c: Context, printers: List<PrusaLink.Printer>) {
        val arr = JSONArray()
        printers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("host", p.host)
                put("apiKey", p.apiKey)
                put("presetName", p.presetName)
                put("storage", p.storage)
            })
        }
        prefs(c).edit { putString(KEY_PRINTERS, arr.toString()) }
    }

    fun add(c: Context, p: PrusaLink.Printer) = save(c, all(c) + p)

    fun remove(c: Context, id: String) = save(c, all(c).filterNot { it.id == id })

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
