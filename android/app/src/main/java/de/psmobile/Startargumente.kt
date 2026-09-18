package de.psmobile

import android.content.Intent

/**
 * Gegenstueck zu `ProcessInfo.processInfo.arguments` auf iOS.
 *
 * Die iOS-Oberflaechentests bringen die App per Startargument in einen
 * bekannten Zustand: Drucker vorgeben, Simple Mode oeffnen, einen Wuerfel
 * aufs Bett legen. Android konnte das bis zum 10.09.2026 nicht - jeder
 * Vergleichsrundgang musste sich von Hand durchklicken, und damit war er
 * nicht wiederholbar. Zwei Bildschirmfotos, die aus verschiedenen
 * Vorzustaenden stammen, belegen ueber die Uebersetzung nichts.
 *
 * Die Namen sind zeichengleich zu drueben (`-psm-preset-printer` ...),
 * damit derselbe Rundgang auf beiden Seiten dieselben Woerter benutzt.
 * Auf Android kommen sie als Intent-Extra herein:
 *
 *   adb shell am start -n de.upsm/de.psmobile.MainActivity \
 *       --esa psm-args psm-preset-printer,psm-start-simple,psm-load-cube
 *
 * Der fuehrende Strich darf fehlen: `am` deutet ein fuehrendes "-" im
 * Wert sonst als eigenen Schalter.
 *
 * **Ein Wert, kein globaler Zustand.** Bis zum 11.09.2026 stand die
 * Argumentliste in einem `object` - prozessweit, also fuer alle
 * Aktivitaeten dieselbe. Auf iOS ist das richtig, dort ist jeder Start
 * ein eigener Prozess. Auf Android laufen im Oberflaechentest zwei
 * Aktivitaeten kurz nebeneinander, und der Dienst richtete sich nach den
 * Argumenten der falschen: ein Fall ohne `-psm-load-cube` bekam den
 * Wuerfel des vorigen aufs Bett und prueste damit nicht mehr, was in
 * seinem Namen stand. Die Argumente gehoeren zu dem Intent, der sie
 * bringt.
 */
class Startargumente private constructor(private val argumente: Set<String>) {

    fun contains(name: String): Boolean = name in argumente

    /**
     * Der Pfad hinter `-psm-step-pfad=` - nur zusammen mit -psm-load-step.
     * Der Randfall "STEP laedt" legt die Datei vorher in den Cache der
     * App; iOS bekommt denselben Schalter als launchArgument.
     */
    val stepPfad: String?
        get() = argumente.firstOrNull { it.startsWith("-psm-step-pfad=") }
            ?.removePrefix("-psm-step-pfad=")

    /**
     * Ob die Schalter einen bestimmten Ausgangszustand verlangen.
     *
     * Der Dienst ueberlebt auf Android das Schliessen der Aktivitaet. Ein
     * Oberflaechentest bekaeme sonst das Bett des vorigen Falls, mitsamt
     * dessen Modellen und Druckerwahl.
     */
    fun verlangtFrischenAusgangszustand(): Boolean =
        FRISCHER_AUSGANGSZUSTAND.any { it in argumente }

    override fun toString(): String = argumente.sorted().toString()

    companion object {
        const val EXTRA = "psm-args"

        /** Der Normalfall: die App wurde ohne Schalter gestartet. */
        val LEER = Startargumente(emptySet())

        fun aus(intent: Intent?): Startargumente {
            val roh = intent?.getStringArrayExtra(EXTRA)?.toList().orEmpty()
            if (roh.isEmpty()) return LEER
            return Startargumente(
                roh.mapTo(mutableSetOf()) { if (it.startsWith("-")) it else "-$it" },
            )
        }

        private val FRISCHER_AUSGANGSZUSTAND = setOf(
            "-psm-reset-setup",
            "-psm-preset-printer",
            "-psm-load-cube",
            "-psm-load-kegel",
            "-psm-load-step",
            "-psm-test-eight-extruders",
            "-psm-test-colormix-colors",
        )
    }
}
