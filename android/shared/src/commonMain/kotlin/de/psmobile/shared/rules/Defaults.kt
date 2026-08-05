package de.psmobile.shared.rules

/**
 * Was nach der Ersteinrichtung eingestellt sein soll.
 *
 * Zwei bewusste Abweichungen von PrusaSlicers Voreinstellung:
 *
 * **Filament.** Bisher stand nach der Einrichtung das erste Filament der
 * Liste im Feld - bei einem MK4S also "Ultrafuse PET", weil die Liste
 * alphabetisch beginnt und nicht nach Wahrscheinlichkeit. Wer einen
 * Prusa einrichtet, druckt zuerst PLA.
 *
 * **Fuellmuster.** Prusa setzt Grid. Gyroid ist in allen Richtungen
 * gleich fest, hat keine Kreuzungspunkte, an denen die Duese aufsetzt,
 * und ist bei gleicher Dichte leiser.
 *
 * Beides ist eine Regel und keine Anzeige - deshalb hier und nicht in
 * einer der beiden Oberflaechen.
 */
object Defaults {

    /** Fuellmuster, das wir setzen. PrusaSlicers Schluesselwort. */
    const val FILL_PATTERN = "gyroid"

    /**
     * Das Filament, das nach der Einrichtung stehen soll.
     *
     * Die Reihenfolge ist die der Wahrscheinlichkeit, nicht des
     * Alphabets: erst genau Prusament PLA, dann irgendein Prusament PLA
     * mit Zusatz, dann irgendein PLA, sonst nichts - dann bleibt es bei
     * dem, was der Kern gewaehlt hat.
     *
     * @param names die kompatiblen Filamentprofile, wie der Kern sie meldet
     */
    fun preferredFilament(names: List<String>): String? {
        if (names.isEmpty()) return null
        return names.firstOrNull { it.equals("Prusament PLA", ignoreCase = true) }
            ?: names.firstOrNull { it.startsWith("Prusament PLA", ignoreCase = true) }
            ?: names.firstOrNull { it.contains("Prusament PLA", ignoreCase = true) }
            ?: names.firstOrNull { it.contains("Prusa PLA", ignoreCase = true) }
            ?: names.firstOrNull { it.contains("PLA", ignoreCase = true) }
    }
}
