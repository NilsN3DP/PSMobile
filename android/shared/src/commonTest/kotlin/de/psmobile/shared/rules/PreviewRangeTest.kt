package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewRangeTest {

    private fun schichten(anzahl: Int) = (0 until anzahl).map {
        PreviewLayerMetrics(
            zLower = it * 0.2,
            zUpper = (it + 1) * 0.2,
            timeSeconds = 60.0,
            filamentMm = 1000.0,
            filamentGrams = 3.0,
        )
    }

    @Test
    fun neuerBereichUmfasstAlles() {
        val r = PreviewRange(layerCount = 10)
        assertEquals(0, r.lower)
        assertEquals(9, r.upper)
    }

    @Test
    fun griffeUeberholenSichNicht() {
        val r = PreviewRange(layerCount = 10).withUpper(4)
        // Der untere Griff darf nicht ueber den oberen hinaus - er
        // bleibt an ihm haengen statt den Bereich umzudrehen.
        assertEquals(4, r.withLower(8).lower)
        // Und der obere nicht unter den unteren: bei lower = 3 kann
        // upper nicht auf 1, sondern bleibt bei 3.
        assertEquals(3, r.withLower(3).withUpper(1).upper)
    }

    @Test
    fun statistikZaehltNurDenSichtbarenTeil() {
        val r = PreviewRange(layerCount = 10).withLower(2).withUpper(4)
        val s = r.stats(schichten(10))
        // Drei Schichten: 2, 3, 4.
        assertEquals(180.0, s.timeSeconds)
        assertEquals(3000.0, s.filamentMm)
        assertEquals(9.0, s.filamentGrams)
        assertEquals(0.4, s.zLower, 1e-9)
        assertEquals(1.0, s.zUpper, 1e-9)
    }

    @Test
    fun leererBereichLiefertNullen() {
        val s = PreviewRange(layerCount = 0).stats(emptyList())
        assertEquals(0.0, s.timeSeconds)
        assertEquals(0.0, s.zUpper)
    }

    @Test
    fun zuKurzeListeStuerztNicht() {
        val r = PreviewRange(layerCount = 10)
        assertEquals(0.0, r.stats(schichten(3)).timeSeconds)
    }

    @Test
    fun dauerAlsStundenUndMinuten() {
        assertEquals("2:14", PreviewRange.duration(2 * 3600 + 14 * 60 + 30.0))
        assertEquals("0:05", PreviewRange.duration(5 * 60.0))
        assertEquals("0:00", PreviewRange.duration(-1.0))
    }
}
