package de.psmobile.shared.rules

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Die Automatik soll nachvollziehbar entscheiden. Frueher schrieb sie
 * denselben Wert wie die Handauswahl, egal was auf dem Bett stand -
 * diese Tests halten fest, dass sie das nicht mehr tut.
 */
class AdhesionAdviceTest {

    private fun f(w: Float, d: Float, h: Float) = AdhesionAdvice.Footprint(w, d, h)

    @Test
    fun emptyBedGetsNoBrimAndSaysWhy() {
        val advice = AdhesionAdvice.advise(emptyList())
        assertEquals(0, advice.brimWidthMm)
        assertEquals(AdhesionAdvice.Reason.NO_OBJECTS, advice.reason)
    }

    @Test
    fun aWideFlatObjectNeedsNoOutline() {
        val advice = AdhesionAdvice.advise(listOf(f(60f, 60f, 20f)))
        assertEquals(0, advice.brimWidthMm)
        assertEquals(AdhesionAdvice.Reason.STABLE, advice.reason)
    }

    @Test
    fun aTallNarrowObjectGetsAnOutline() {
        // 15 mm kuerzeste Kante, 80 mm hoch - Verhaeltnis 5,3 und damit
        // ueber der Kippschwelle.
        val advice = AdhesionAdvice.advise(listOf(f(15f, 40f, 80f)))
        assertEquals(AdhesionAdvice.SUGGESTED_BRIM_MM, advice.brimWidthMm)
        assertEquals(AdhesionAdvice.Reason.TALL_AND_NARROW, advice.reason)
    }

    @Test
    fun exactlyAtTheTippingRatioStillCountsAsStable() {
        // 20 mm kuerzeste Kante, genau 80 mm hoch. Die Schwelle ist ein
        // echtes Groesser, nicht Groessergleich.
        val advice = AdhesionAdvice.advise(listOf(f(20f, 30f, 80f)))
        assertEquals(0, advice.brimWidthMm)
        assertEquals(AdhesionAdvice.Reason.STABLE, advice.reason)
    }

    @Test
    fun aFlatButTinyObjectGetsAnOutlineToo() {
        // Flach, also kein Kippfall, aber nur 6 mm Aufstandskante.
        val advice = AdhesionAdvice.advise(listOf(f(6f, 40f, 3f)))
        assertEquals(AdhesionAdvice.SUGGESTED_BRIM_MM, advice.brimWidthMm)
        assertEquals(AdhesionAdvice.Reason.SMALL_FOOTPRINT, advice.reason)
    }

    @Test
    fun oneRiskyObjectAmongStableOnesDecidesForTheWholeBed() {
        val advice = AdhesionAdvice.advise(
            listOf(f(60f, 60f, 20f), f(12f, 12f, 90f), f(80f, 80f, 10f))
        )
        assertEquals(AdhesionAdvice.SUGGESTED_BRIM_MM, advice.brimWidthMm)
    }

    @Test
    fun everyReasonHasItsOwnSentence() {
        val sentences = AdhesionAdvice.Reason.entries.map {
            AdhesionAdvice.explain(AdhesionAdvice.Advice(0, it))
        }
        assertEquals(sentences.size, sentences.toSet().size)
        assertEquals(0, sentences.count { it.isBlank() })
    }
}
