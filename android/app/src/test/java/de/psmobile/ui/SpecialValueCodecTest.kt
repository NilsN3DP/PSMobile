package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialValueCodecTest {
    @Test
    fun bedShapeRoundTripAndArea() {
        val points = SpecialValueCodec.parseBedShape("0x0,250x0,250x210,0x210")!!
        assertEquals(4, points.size)
        assertEquals(52_500.0, SpecialValueCodec.polygonArea(points), 0.001)
        assertEquals(
            "0x0,250x0,250x210,0x210",
            SpecialValueCodec.encodeBedShape(points),
        )
        assertNull(SpecialValueCodec.parseBedShape("0x0,kaputt"))
    }

    @Test
    fun stringVectorMatchesPrusaCStyleEscaping() {
        val values = listOf(
            "find;this",
            "replace\nwith",
            "ri",
            "Notiz mit \"Zitat\" und \\",
            "",
        )
        val encoded = SpecialValueCodec.encodeStrings(values)
        assertEquals(values, SpecialValueCodec.parseStrings(encoded))
        assertNull(SpecialValueCodec.parseStrings("\"nicht geschlossen"))
    }

    @Test
    fun rammingRoundTripKeepsCurve() {
        val original =
            "\"120 100 6.6 6.8 7.2| 0.05 6.6 0.45 6.8 0.95 7.8\""
        val parsed = SpecialValueCodec.parseRamming(original)!!
        assertEquals(120.0, parsed.lineWidthPercent, 0.001)
        assertEquals(3, parsed.sampledSpeeds.size)
        assertEquals(3, parsed.controlPoints.size)
        assertEquals(original, SpecialValueCodec.encodeRamming(parsed))
        val rebuilt = SpecialValueCodec.encodeRamming(parsed, rebuildSamples = true)
        assertTrue(SpecialValueCodec.parseRamming(rebuilt)!!.sampledSpeeds.isNotEmpty())
    }

    @Test
    fun floatMatrixRoundTripUsesStableDecimalPoint() {
        val values = listOf(0.0, 140.0, 98.125)
        assertEquals(values, SpecialValueCodec.parseFloats(
            SpecialValueCodec.encodeFloats(values)
        ))
    }
}
