package de.psmobile.shared.rules

/** Eine sichtbare, einsbasierte physische Materialposition. */
data class ExtruderPosition(val index: Int, val label: String)

/**
 * Formulierung und Nummerierung fuer alle Plattformen.
 *
 * Die Kern-ABI verwendet weiterhin nullbasierte Indizes. In der Bedienung
 * sind es jedoch Materialpositionen 1 bis N; damit stimmt die Anzeige mit
 * dem Drucker und dem gespeicherten PrusaSlicer-Projekt ueberein.
 */
object ExtruderPresentation {
    fun positions(count: Int): List<ExtruderPosition> =
        (0 until count.coerceAtLeast(0)).map { index ->
            ExtruderPosition(index = index, label = (index + 1).toString())
        }
}
