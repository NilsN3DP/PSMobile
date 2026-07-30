package de.psmobile.slicing

import kotlin.math.min

/**
 * Reine, testbare Speicherentscheidung vor dem nativen Slice.
 *
 * [estimatedPeakBytes] ist der vom Core vorhergesagte gesamte RSS-Peak,
 * nicht nur eine zusaetzliche Allokation. Androids Java-`memoryClass` ist
 * hier ungeeignet, weil libslic3r fast ausschliesslich nativen Speicher
 * verwendet.
 */
internal object SliceMemoryPolicy {
    data class Decision(
        val estimatedPeakBytes: Long,
        val safeBudgetBytes: Long,
        val blocked: Boolean,
        val warning: Boolean,
    )

    fun evaluate(
        estimatedPeakBytes: Long,
        totalDeviceBytes: Long,
        availableDeviceBytes: Long,
        currentProcessBytes: Long,
        systemLowMemory: Boolean,
    ): Decision {
        if (estimatedPeakBytes <= 0L ||
            totalDeviceBytes <= 0L ||
            availableDeviceBytes < 0L
        ) {
            return Decision(estimatedPeakBytes, Long.MAX_VALUE, false, false)
        }

        // Dem Vordergrundprozess hoechstens knapp die Haelfte des Geraets
        // zusprechen. Der Rest muss fuer System, Grafiktreiber und andere
        // nicht beendbare Prozesse bleiben.
        val deviceShare = totalDeviceBytes / 100L * 48L

        // Verfuegbarer Speicher plus der bereits vom Prozess belegte Anteil
        // ergibt, wie gross sein Peak ohne aggressives Verdrängen werden
        // darf. Davon bleiben 20 % als Schutz gegen Schaetzfehler und
        // parallele Systemallokationen frei.
        val reclaimableForProcess =
            (availableDeviceBytes + currentProcessBytes).coerceAtLeast(0L)
        val pressureShare = reclaimableForProcess / 100L * 80L
        val safeBudget = min(deviceShare, pressureShare).coerceAtLeast(1L)

        val blocked = systemLowMemory || estimatedPeakBytes > safeBudget
        val warning = !blocked && estimatedPeakBytes > safeBudget / 100L * 80L
        return Decision(estimatedPeakBytes, safeBudget, blocked, warning)
    }
}
