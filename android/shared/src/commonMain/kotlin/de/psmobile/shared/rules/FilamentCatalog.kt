package de.psmobile.shared.rules

/**
 * Die Materialauswahl, wie EasyPrint sie zeigt.
 *
 * Dort steht oben ein Suchfeld ("Suche nach Anbieter, Material oder
 * Farbe"), darunter eine Reihe Typ-Knoepfe und eine Reihe Farbpunkte,
 * und darunter Karten mit Hersteller, Typ und Farbe. Das ist mehr als
 * Zierde: eine Liste mit vierhundert Filamentprofilen ist mit dem
 * Finger nicht zu durchsuchen, und niemand kennt den genauen Namen
 * seines Profils - man weiss, welche Rolle im Schrank liegt.
 *
 * Hier stehen die Regeln dazu, damit iOS und Android dieselbe Auswahl
 * treffen. Woher Typ und Farbe kommen, entscheidet der Kern: sie stehen
 * als filament_type und filament_colour im Profil.
 */
object FilamentCatalog {

    /** Ein Filamentprofil, aufbereitet fuer die Anzeige. */
    data class Entry(
        val rawPreset: String,
        val vendor: String,
        val type: String,
        val colorHex: String,
    ) {
        /** Was auf der Karte steht, wenn der Hersteller schon darueber steht. */
        val label: String get() = rawPreset
    }

    /**
     * Die Reihenfolge der Typ-Knoepfe.
     *
     * Nicht alphabetisch: die fuenf gaengigen zuerst, wie in EasyPrint.
     * Wer PLA sucht, soll nicht an ABS und ASA vorbei.
     */
    private val vorne = listOf("PLA", "PETG", "ASA", "ABS", "FLEX")

    /**
     * Der Hersteller steckt im Namen.
     *
     * PrusaSlicer benennt Filamentprofile als "Hersteller Typ", also
     * "Prusament PLA" oder "Generic PETG". Ein eigenes Feld dafuer gibt
     * es nicht - der Herstellername im Buendel ist der des Druckers,
     * nicht der des Filaments.
     */
    fun vendorOf(rawPreset: String): String =
        rawPreset.trim().substringBefore(" ", rawPreset.trim())

    fun entry(rawPreset: String, type: String, colorHex: String): Entry = Entry(
        rawPreset = rawPreset,
        vendor = vendorOf(rawPreset),
        type = type.trim().uppercase(),
        colorHex = normalizeColor(colorHex),
    )

    /** Die vorhandenen Typen, gaengige zuerst. */
    fun types(entries: List<Entry>): List<String> {
        val vorhanden = entries.map { it.type }.filter { it.isNotBlank() }.distinct()
        return vorne.filter { it in vorhanden } + vorhanden.filterNot { it in vorne }.sorted()
    }

    /**
     * Die vorhandenen Farben, haeufigste zuerst.
     *
     * Nach Haeufigkeit und nicht nach Farbkreis: die Punkte sollen die
     * Farben zeigen, die im Bestand wirklich vorkommen, und die
     * haeufigste ist die wahrscheinlichste Wahl.
     */
    fun colors(entries: List<Entry>, limit: Int = 12): List<String> =
        entries.map { it.colorHex }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    /**
     * Sucht ueber Hersteller, Typ und ganzen Namen.
     *
     * Wortweise und ohne Gross-/Kleinschreibung: wer "prusament pla"
     * tippt, meint dasselbe wie "PLA Prusament". Leere Angaben filtern
     * nicht.
     */
    fun filter(
        entries: List<Entry>,
        query: String = "",
        type: String = "",
        colorHex: String = "",
    ): List<Entry> {
        val worte = query.trim().lowercase().split(" ").filter { it.isNotBlank() }
        val farbe = normalizeColor(colorHex)
        return entries.filter { eintrag ->
            if (type.isNotBlank() && !eintrag.type.equals(type, ignoreCase = true)) return@filter false
            if (farbe.isNotBlank() && eintrag.colorHex != farbe) return@filter false
            if (worte.isEmpty()) return@filter true
            val heuhaufen = (eintrag.rawPreset + " " + eintrag.vendor + " " + eintrag.type).lowercase()
            worte.all { heuhaufen.contains(it) }
        }
    }

    /** "Nichts gefunden" gehoert zur Auskunft, nicht in die Oberflaeche. */
    fun emptyMessage(): Bilingual = Bilingual(
        english = "No material matches this search.",
        german = "Kein Material passt zu dieser Suche.",
    )

    fun searchHint(): Bilingual = Bilingual(
        english = "Search by vendor, material or colour",
        german = "Suche nach Anbieter, Material oder Farbe",
    )

    /**
     * Farben vergleichbar machen.
     *
     * PrusaSlicer schreibt sie als "#RRGGBB", mal mit, mal ohne Raute,
     * mal in Kleinbuchstaben. Ohne Vereinheitlichung waeren "#FF8000"
     * und "ff8000" zwei Farben, und der Farbpunkt fuende die Haelfte
     * seiner Rollen nicht.
     */
    fun normalizeColor(value: String): String {
        val roh = value.trim().removePrefix("#").uppercase()
        return if (roh.length == 6 && roh.all { it.isDigit() || it in "ABCDEF" }) "#$roh" else ""
    }
}
