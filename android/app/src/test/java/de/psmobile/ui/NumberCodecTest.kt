package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberCodecTest {
    @Test
    fun outputIsLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12.5", NumberCodec.oneDecimal(12.5f))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun parserAcceptsDotAndComma() {
        assertEquals(12.5f, NumberCodec.parseFloat("12.5"))
        assertEquals(12.5f, NumberCodec.parseFloat("12,5"))
        assertEquals(-90f, NumberCodec.parseFloat("-90"))
    }
}
