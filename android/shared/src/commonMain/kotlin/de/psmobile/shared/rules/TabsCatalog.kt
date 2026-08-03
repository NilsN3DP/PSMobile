package de.psmobile.shared.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Die aus PrusaSlicer uebernommene Seitenstruktur.
 *
 * Erzeugt wird sie von build/scripts/extract-ui.py aus `Tab.cpp` - 20
 * Seiten, ihre Gruppen und 247 Parameter in Originalreihenfolge. Nichts
 * davon ist abgetippt (E-12).
 *
 * Gelesen wird sie hier, weil beide Apps dieselbe Datei auswerten und
 * dabei dieselben Entscheidungen treffen muessen: welcher Parameter in
 * welche Gruppe gehoert, welche Felder mehrzeilig sind, welche Seite je
 * Extruder vervielfacht wird. Das Laden der Datei bleibt bei den Apps -
 * Android holt sie aus den Assets, iOS aus dem Bundle.
 */
object TabsCatalog {

    /**
     * Ein Parameter auf einer Seite.
     *
     * [code] markiert die mehrzeiligen G-code-Felder - im Original
     * `option.opt.is_code`, was ein Textfeld ueber mehrere Zeilen statt
     * einer Eingabezeile bedeutet.
     *
     * [line] fasst mehrere Parameter zu einer Zeile zusammen, so wie
     * PrusaSlicer "Solid layers" mit oben und unten nebeneinander stellt.
     */
    data class Option(
        val key: String,
        val code: Boolean = false,
        val line: String? = null,
    )

    data class Group(val title: String, val options: List<Option>)

    /**
     * [perExtruder] markiert die Extruderseite. PrusaSlicer legt davon
     * zur Laufzeit eine je Duese an; der Titel traegt {n} als Platzhalter
     * fuer die Nummer.
     */
    data class Page(
        val title: String,
        val icon: String,
        val groups: List<Group>,
        val perExtruder: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param text Inhalt von psui/tabs.json
     * @return Seiten je Bereich: "print", "filament", "printer"
     */
    fun parse(text: String): Map<String, List<Page>> {
        val wurzel = json.parseToJsonElement(text).jsonObject
        return wurzel.mapValues { (_, seiten) ->
            seiten.jsonArray.map { seite ->
                val o = seite.jsonObject
                Page(
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    icon = o["icon"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    perExtruder = o["per_extruder"]?.jsonPrimitive?.booleanOrNull ?: false,
                    groups = o["groups"]?.jsonArray.orEmpty().map { gruppe ->
                        val g = gruppe.jsonObject
                        Group(
                            title = g["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            options = g["options"]?.jsonArray.orEmpty().map { option ->
                                val p = option.jsonObject
                                Option(
                                    key = p["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                    code = p["code"]?.jsonPrimitive?.booleanOrNull ?: false,
                                    line = p["line"]?.jsonPrimitive?.contentOrNull,
                                )
                            },
                        )
                    },
                )
            }
        }
    }

    /**
     * Die Seiten einer Extruderseite auf die tatsaechliche Duesenzahl
     * vervielfachen.
     *
     * PrusaSlicer macht das zur Laufzeit; in der Datei steht sie einmal
     * mit {n} im Titel. Ein Drucker mit fuenf Duesen bekommt fuenf
     * Seiten, einer mit einer Duese genau eine - und dann ohne Nummer,
     * weil "Extruder 1" bei einem einzigen Extruder nur Verwirrung
     * stiftet.
     */
    fun expand(pages: List<Page>, extruderCount: Int): List<Page> =
        pages.flatMap { page ->
            if (!page.perExtruder) listOf(page)
            else if (extruderCount <= 1) {
                listOf(page.copy(title = page.title.replace(" {n}", "").replace("{n}", "")))
            } else {
                (1..extruderCount).map { n ->
                    page.copy(title = page.title.replace("{n}", n.toString()))
                }
            }
        }

    /**
     * Parameter einer Gruppe zu Zeilen zusammenfassen.
     *
     * Zwei Parameter mit derselben [Option.line] stehen im Original
     * nebeneinander. Wer das ignoriert, bekommt untereinander, was
     * zusammengehoert - aus "Solid layers: oben 5, unten 4" werden zwei
     * Zeilen, die nicht mehr erkennen lassen, dass sie ein Paar sind.
     */
    /**
     * Eine Zeile der Gruppe. [title] ist gesetzt, wenn mehrere Parameter
     * unter einer gemeinsamen Beschriftung stehen.
     *
     * Bewusst eine eigene Klasse und kein Pair: Kotlins Typparameter
     * ueberleben die Bruecke nach Swift nicht - dort kaeme ein
     * untypisiertes NSArray an, mit dem sich nichts anfangen laesst.
     */
    data class Line(val title: String?, val options: List<Option>)

    fun lines(group: Group): List<Line> {
        val ergebnis = mutableListOf<Line>()
        var offen: MutableList<Option>? = null
        var offenerName: String? = null
        for (o in group.options) {
            if (o.line != null && o.line == offenerName) {
                offen?.add(o)
            } else {
                offen?.let { ergebnis.add(Line(offenerName, it)) }
                offen = mutableListOf(o)
                offenerName = o.line
            }
        }
        offen?.let { ergebnis.add(Line(offenerName, it)) }
        return ergebnis
    }
}
