package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureAnchorTest {
    @Test
    fun `lifting one pinch pointer reanchors to the surviving pointer`() {
        val anchor = remainingPointerAnchor(
            points = listOf(100f to 240f, 300f to 260f),
            liftedIndex = 1,
        )

        assertEquals(100f to 240f, anchor)
    }
}
