package de.psmobile.shared.rules

/**
 * Die Zahlen einer Schicht des fertigen Ergebnisses, ohne Abhängigkeit
 * zum Kern.
 *
 * Die App bildet ihre C-ABI-Datensätze darauf ab; ein Unit-Test kann
 * dieselbe Rechnung prüfen, ohne den großen Slicer-Kern zu linken.
 */
data class PreviewLayerMetrics(
    val zLower: Double,
    val zUpper: Double,
    val timeSeconds: Double,
    val filamentMm: Double,
    val filamentGrams: Double,
)

/**
 * Der beidseitige Schichtbereich der Vorschau.
 *
 * Was der Regler links am Bett einstellt, und was die Statistikzeile
 * darunter zusammenzählt. Die Rechnung steht hier und nicht in den
 * Bildschirmen, damit iOS und Android dieselbe Zahl zeigen — vorher
 * hatte nur iOS sie überhaupt.
 *
 * Gegenstück zu `ios/PSMobile/UI/PreviewRange.swift`.
 */
data class PreviewRange(
    val layerCount: Int,
    val lower: Int = 0,
    val upper: Int = maxOf(layerCount - 1, 0),
) {

    /** Was für den eingestellten Bereich zusammenkommt. */
    data class Stats(
        val timeSeconds: Double = 0.0,
        val filamentMm: Double = 0.0,
        val filamentGrams: Double = 0.0,
        val zLower: Double = 0.0,
        val zUpper: Double = 0.0,
    )

    val isEmpty: Boolean get() = layerCount == 0

    /**
     * Die Griffe können sich nicht überholen: der untere bleibt unter
     * dem oberen, der obere über dem unteren. Ohne das kippt der Bereich
     * um, sobald man einen Griff über den anderen zieht.
     */
    fun withLower(value: Int): PreviewRange =
        if (layerCount == 0) this
        else copy(lower = value.coerceIn(0, upper))

    fun withUpper(value: Int): PreviewRange =
        if (layerCount == 0) this
        else copy(upper = value.coerceIn(lower, layerCount - 1))

    /**
     * Zeit, Filament und Höhe der sichtbaren Schichten.
     *
     * Nicht des ganzen Drucks: wer den Regler zusammenschiebt, will
     * wissen, was dieser Ausschnitt kostet.
     */
    fun stats(layers: List<PreviewLayerMetrics>): Stats {
        if (isEmpty || lower >= layers.size || upper >= layers.size) return Stats()
        val sichtbar = layers.subList(lower, upper + 1)
        if (sichtbar.isEmpty()) return Stats()
        return Stats(
            timeSeconds = sichtbar.sumOf { it.timeSeconds },
            filamentMm = sichtbar.sumOf { it.filamentMm },
            filamentGrams = sichtbar.sumOf { it.filamentGrams },
            zLower = sichtbar.first().zLower,
            zUpper = sichtbar.last().zUpper,
        )
    }

    companion object {
        /** "2:14" — Stunden und Minuten, wie iOS es schreibt. */
        fun duration(seconds: Double): String {
            val gesamt = maxOf(seconds, 0.0).toLong()
            val stunden = gesamt / 3600
            val minuten = (gesamt % 3600) / 60
            return "$stunden:" + minuten.toString().padStart(2, '0')
        }
    }
}

/**
 * Die Namen der Merkmalsrollen, wie PrusaSlicer sie nennt.
 *
 * Die Reihenfolge ist die des C-ABI (`psm_preview_feature_role`) und
 * nicht die von libvgcode — der Index ist der Wert, der über die
 * Schnittstelle kommt.
 */
object PreviewRoles {

    fun name(role: Int): Bilingual = when (role) {
        1 -> Bilingual("Perimeter", "Kontur")
        2 -> Bilingual("External perimeter", "Außenkontur")
        3 -> Bilingual("Overhang perimeter", "Überhangkontur")
        4 -> Bilingual("Internal infill", "Innere Füllung")
        5 -> Bilingual("Solid infill", "Massive Füllung")
        6 -> Bilingual("Top solid infill", "Oberste Füllung")
        7 -> Bilingual("Ironing", "Glätten")
        8 -> Bilingual("Bridge infill", "Brücken")
        9 -> Bilingual("Gap fill", "Lückenfüllung")
        10 -> Bilingual("Skirt", "Skirt")
        11 -> Bilingual("Support material", "Stützmaterial")
        12 -> Bilingual("Support interface", "Stützkontakt")
        13 -> Bilingual("Wipe tower", "Reinigungsturm")
        14 -> Bilingual("Custom", "Eigenes")
        else -> Bilingual("Other", "Sonstiges")
    }
}
