package de.psmobile.shared.rules

import kotlin.math.min

/**
 * Reine, testbare Speicherentscheidung vor dem nativen Slice.
 *
 * [estimatedPeakBytes] ist der vom Kern vorhergesagte gesamte RSS-Peak,
 * nicht nur eine zusaetzliche Allokation. Androids Java-`memoryClass` ist
 * dafuer ungeeignet, weil libslic3r fast ausschliesslich nativen Speicher
 * verwendet; iOS liefert mit `os_proc_available_memory()` direkt, was der
 * Prozess noch bekommen darf.
 *
 * Stand bis zum 13.09.2026 in android/app (SlicerService), iOS hatte nur
 * das Feld `memoryWarning`, das nie gesetzt wurde. Seitdem hier, damit
 * beide Seiten dieselbe Grenze ziehen.
 */
object SliceMemoryPolicy {
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
        // ergibt, wie gross sein Peak ohne aggressives Verdraengen werden
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

    /** Der Text zur Entscheidung - derselbe auf beiden Plattformen, oder keiner. */
    fun message(d: Decision): Bilingual? {
        if (!d.blocked && !d.warning) return null
        val needMb = d.estimatedPeakBytes / (1024L * 1024L)
        val budgetMb = d.safeBudgetBytes / (1024L * 1024L)
        return if (d.blocked) Bilingual(
            "Slicing stopped safely: this project is expected to need about $needMb MB of RAM, " +
                "the safe limit on this device is about $budgetMb MB. Use “Simplify”, a coarser " +
                "profile, or a device with more RAM.",
            "Slicen sicher gestoppt: Dieses Projekt benötigt voraussichtlich etwa $needMb MB RAM, " +
                "die sichere Grenze auf diesem Gerät liegt bei etwa $budgetMb MB. Nutze " +
                "„Vereinfachen“, ein gröberes Profil oder ein Gerät mit mehr RAM.",
        ) else Bilingual(
            "Memory need about $needMb MB; safe limit on this device: $budgetMb MB. " +
                "Better simplify before slicing.",
            "Speicherbedarf ca. $needMb MB; sichere Grenze auf diesem Gerät: $budgetMb MB. " +
                "Vor dem Slicen besser vereinfachen.",
        )
    }
}
