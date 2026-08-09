package de.psmobile.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zuletzt gesicherte/geoeffnete Projekte - Android-Gegenstueck zu
 * `SlicerModel.recentProjects()` (iOS). Dort ist es ein einfaches
 * Auflisten von 3mf-Dateien unter `Documents/Projects`, weil iOS-Apps ein eigenes,
 * app-verwaltetes Verzeichnis haben. Android hat das nicht - jedes
 * "Speichern" geht ueber das Storage Access Framework an einen vom
 * Nutzer gewaehlten Ort (Downloads, Drive, SD-Karte, ...), deshalb wird
 * hier stattdessen eine Liste von content://-URIs mit Anzeigename und
 * Zeitstempel gefuehrt (dieselbe SharedPreferences-Datei wie
 * PrinterStore.kt, JSONArray-Muster wie dort).
 */
object RecentProjectsStore {

    const val PREFS = "psmobile"
    const val KEY = "recent.projects"
    private const val MAX_ENTRIES = 20

    data class Eintrag(
        val uri: String,
        val name: String,
        val zeitstempelMs: Long,
        /** Pfad zu einer kleinen PNG-Momentaufnahme des Bettinhalts beim
         * letzten Sichern - oder null, solange noch keine entstanden ist
         * (z. B. direkt nach dem Import, bevor der GL-Viewport einen
         * ersten Rahmen gezeichnet hat). Sitzt in `cacheDir`, ueberlebt
         * also keinen Cache-Reinigung - dann faellt die Kachel einfach
         * auf das generische Symbol zurueck. */
        val thumbPath: String? = null,
    )

    fun alle(context: Context): List<Eintrag> {
        val arr = JSONArray(prefs(context).getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Eintrag(
                o.getString("uri"),
                o.getString("name"),
                o.getLong("zeit"),
                o.optString("thumb").ifEmpty { null },
            )
        }.sortedByDescending { it.zeitstempelMs }
    }

    /** Neu ganz oben einsortiert - auch wenn es schon drinstand (dann
     * zaehlt der neue Zeitstempel, nicht die alte Position). */
    fun hinzufuegen(context: Context, uri: String, name: String) {
        val rest = alle(context).filterNot { it.uri == uri }
        val neu = (listOf(Eintrag(uri, name, System.currentTimeMillis())) + rest).take(MAX_ENTRIES)
        speichern(context, neu)
    }

    fun entfernen(context: Context, uri: String) {
        speichern(context, alle(context).filterNot { it.uri == uri })
    }

    /** Traegt eine gerade erzeugte Momentaufnahme nach - kommt immer
     * erst nach `hinzufuegen`, weil das Rendern der Ansicht einen Moment
     * braucht und den Speicher-Erfolg nicht aufhalten soll. */
    fun setzeThumbnail(context: Context, uri: String, path: String) {
        speichern(context, alle(context).map { if (it.uri == uri) it.copy(thumbPath = path) else it })
    }

    private fun speichern(context: Context, liste: List<Eintrag>) {
        val arr = JSONArray()
        liste.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("uri", e.uri)
                    put("name", e.name)
                    put("zeit", e.zeitstempelMs)
                    if (e.thumbPath != null) put("thumb", e.thumbPath)
                }
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
