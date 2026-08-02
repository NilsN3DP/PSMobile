package de.psmobile.ui

/**
 * Entscheidet aus der Geometrie, ob ein Objekt einen Rand braucht.
 *
 * Bisher schrieb die Auswahl "Automatic" schlicht denselben Wert wie
 * "Rand um das Modell" und konnte nie als gewaehlt erscheinen. Das war
 * eine vorgetaeuschte Automatik. Hier steht stattdessen die Regel, die
 * ein erfahrener Anwender selbst anwendet:
 *
 * - Ein hohes, schmales Objekt kippt beim Anfahren der Duese leichter,
 *   je groesser das Verhaeltnis von Hoehe zur kuerzeren Grundflaechenkante
 *   ist. Ab dem Vierfachen ist ein Rand die guenstigere Wahl.
 * - Eine sehr kleine Aufstandsflaeche haelt unabhaengig von der Hoehe
 *   schlecht. Unter zehn Millimetern kuerzester Kante gilt dasselbe.
 *
 * PrusaSlicer selbst kennt keine solche Automatik; die Regel ist bewusst
 * einfach und nachvollziehbar gehalten, statt eine Sicherheit zu
 * behaupten, die eine Umrissberechnung nicht hergibt. Die Werte gelten
 * fuer die Huellquader nach allen Transformationen.
 */
object AdhesionAdvice {

    /** Kantenlaengen des Huellquaders in Millimetern. */
    data class Footprint(val widthMm: Float, val depthMm: Float, val heightMm: Float)

    enum class Reason {
        /** Kein Objekt auf dem Bett - es gibt nichts zu beurteilen. */
        NO_OBJECTS,

        /** Alle Objekte stehen breit genug und niedrig genug. */
        STABLE,

        /** Mindestens ein Objekt ist im Verhaeltnis zur Grundflaeche hoch. */
        TALL_AND_NARROW,

        /** Mindestens ein Objekt steht auf sehr wenig Flaeche. */
        SMALL_FOOTPRINT,
    }

    data class Advice(val brimWidthMm: Int, val reason: Reason)

    /** Ab diesem Verhaeltnis Hoehe zu kuerzester Grundkante wird geraten. */
    const val TIPPING_RATIO = 4f

    /** Kuerzeste Grundkante, unter der die Flaeche als klein gilt. */
    const val SMALL_EDGE_MM = 10f

    /** Randbreite, die geraten wird - dieselbe wie bei der Handauswahl. */
    const val SUGGESTED_BRIM_MM = 5

    fun advise(objects: List<Footprint>): Advice {
        if (objects.isEmpty()) return Advice(0, Reason.NO_OBJECTS)

        // Die kleine Flaeche wiegt schwerer als das Verhaeltnis: sie haelt
        // auch dann schlecht, wenn das Objekt flach ist. Deshalb zuerst.
        if (objects.any { shortestEdge(it) < SMALL_EDGE_MM }) {
            return Advice(SUGGESTED_BRIM_MM, Reason.SMALL_FOOTPRINT)
        }
        if (objects.any { it.heightMm > shortestEdge(it) * TIPPING_RATIO }) {
            return Advice(SUGGESTED_BRIM_MM, Reason.TALL_AND_NARROW)
        }
        return Advice(0, Reason.STABLE)
    }

    private fun shortestEdge(f: Footprint) = minOf(f.widthMm, f.depthMm)

    /** Begruendung als Satz, damit die Oberflaeche sie anzeigen kann. */
    fun explain(advice: Advice): String = when (advice.reason) {
        Reason.NO_OBJECTS -> SimpleModeState.text(
            "Nothing on the bed to judge yet",
            "Noch kein Objekt auf dem Bett",
        )
        Reason.STABLE -> SimpleModeState.text(
            "Objects stand wide enough - no outline needed",
            "Objekte stehen breit genug - kein Rand noetig",
        )
        Reason.TALL_AND_NARROW -> SimpleModeState.text(
            "Tall next to a narrow base - an outline holds it down",
            "Hoch bei schmaler Grundflaeche - ein Rand haelt es fest",
        )
        Reason.SMALL_FOOTPRINT -> SimpleModeState.text(
            "Very little contact area - an outline holds it down",
            "Sehr wenig Aufstandsflaeche - ein Rand haelt es fest",
        )
    }
}
