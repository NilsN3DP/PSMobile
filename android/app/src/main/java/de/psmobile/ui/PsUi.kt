package de.psmobile.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Zugriff auf die aus PrusaSlicer uebernommene Oberflaechen-Definition.
 *
 * Die Daten unter assets/psui erzeugt build/scripts/extract-ui.py aus dem
 * Original - Seitenstruktur aus Tab.cpp, Werkzeuge aus GLCanvas3D.cpp,
 * Beschriftungen aus den .po-Dateien, Icons aus resources/icons.
 * Nichts davon ist hier abgetippt. Siehe docs/entscheidungen.md, E-12.
 */
object PsUi {

    private const val TAG = "PsUi"
    private const val ROOT = "psui"

    /**
     * Ein Parameter auf einer Seite.
     *
     * code markiert die mehrzeiligen G-code-Felder - im Original
     * option.opt.is_code, das ein Textfeld ueber mehrere Zeilen statt
     * einer Eingabezeile bedeutet.
     */
    data class Option(val key: String, val code: Boolean = false)

    data class Group(val title: String, val options: List<Option>)

    /**
     * perExtruder markiert die Extruderseite. PrusaSlicer legt davon zur
     * Laufzeit eine je Duese an ("Extruder 1" bis "Extruder 5"); der
     * Titel traegt hier {n} als Platzhalter fuer die Nummer.
     */
    data class Page(
        val title: String,
        val icon: String,
        val groups: List<Group>,
        val perExtruder: Boolean = false,
    )
    data class Tool(val name: String, val icon: String, val tooltip: String)

    /** Einstellungsseiten je Bereich: "print", "filament", "printer". */
    var tabs: Map<String, List<Page>> = emptyMap()
        private set

    /** Obere Werkzeugleiste in Originalreihenfolge. */
    var toolbar: List<Tool> = emptyList()
        private set

    var availableLanguages: List<String> = listOf("en")
        private set

    /** Englisch ist die Quellsprache von PrusaSlicer und damit Standard. */
    var language: String = "en"
        private set

    private var strings: Map<String, String> = emptyMap()
    private var loaded = false

    fun load(context: Context, lang: String = "en") {
        if (loaded && lang == language) return
        runCatching {
            if (!loaded) {
                tabs = readTabs(context)
                toolbar = readToolbar(context)
                availableLanguages = readLanguages(context)
                loaded = true
            }
            setLanguage(context, lang)
        }.onFailure { Log.e(TAG, "UI-Daten nicht ladbar", it) }
    }

    fun setLanguage(context: Context, lang: String) {
        language = lang
        strings = if (lang == "en") emptyMap() else runCatching {
            val o = JSONObject(read(context, "lang_$lang.json"))
            buildMap(o.length()) {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, o.getString(k))
                }
            }
        }.getOrElse {
            Log.w(TAG, "Sprache $lang nicht verfuegbar, bleibe bei Englisch")
            emptyMap()
        }
    }

    /**
     * Uebersetzt einen englischen Originaltext.
     * Ohne Treffer bleibt das Original stehen - das ist genau der
     * englische Text und damit korrekt, nicht ein Platzhalter.
     */
    fun tr(source: String): String = strings[source] ?: source

    // --- Lesen ------------------------------------------------------------

    private fun read(context: Context, name: String): String =
        context.assets.open("$ROOT/$name").bufferedReader().use { it.readText() }

    private fun readTabs(context: Context): Map<String, List<Page>> {
        val root = JSONObject(read(context, "tabs.json"))
        return buildMap {
            root.keys().forEach { tab ->
                val pages = root.getJSONArray(tab)
                put(tab, (0 until pages.length()).map { i ->
                    val p = pages.getJSONObject(i)
                    val groups = p.getJSONArray("groups")
                    Page(
                        title = p.getString("title"),
                        icon = p.optString("icon"),
                        perExtruder = p.optBoolean("per_extruder"),
                        groups = (0 until groups.length()).map { j ->
                            val g = groups.getJSONObject(j)
                            val opts = g.getJSONArray("options")
                            Group(
                                title = g.optString("title"),
                                options = (0 until opts.length()).map { k ->
                                    val o = opts.getJSONObject(k)
                                    Option(o.getString("key"), o.optBoolean("code"))
                                },
                            )
                        },
                    )
                })
            }
        }
    }

    private fun readToolbar(context: Context): List<Tool> {
        val arr = org.json.JSONArray(read(context, "toolbar.json"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Tool(o.getString("name"), o.optString("icon"), o.optString("tooltip"))
        }
    }

    private fun readLanguages(context: Context): List<String> {
        val o = JSONObject(read(context, "languages.json"))
        val arr = o.getJSONArray("available")
        return (0 until arr.length()).map { arr.getString(it) }
    }

    /** Pfad eines Original-Icons im Asset-Verzeichnis. */
    fun iconAsset(name: String): String = "$ROOT/icons/$name"

    fun iconExists(context: Context, name: String): Boolean =
        runCatching { context.assets.open(iconAsset(name)).close(); true }
            .getOrDefault(false)
}
