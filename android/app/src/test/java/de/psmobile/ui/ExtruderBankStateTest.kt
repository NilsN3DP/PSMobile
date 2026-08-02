package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtruderBankStateTest {
    @Test
    fun `groups eight toolheads into two touch friendly rows`() {
        assertEquals(
            listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7)),
            extruderHeadGroups(count = 8),
        )
    }

    @Test
    fun `wide sidebar can keep all eight toolheads in one bank`() {
        assertEquals(
            listOf(listOf(0, 1, 2, 3, 4, 5, 6, 7)),
            extruderHeadGroups(count = 8, columns = 8),
        )
    }

    @Test
    fun `selection is always kept inside the available toolheads`() {
        assertEquals(0, normalizedExtruderIndex(index = -3, count = 2))
        assertEquals(1, normalizedExtruderIndex(index = 10, count = 2))
        assertEquals(0, normalizedExtruderIndex(index = 0, count = 0))
    }

    @Test
    fun `multi head selection targets its inline editor while one head does not scroll`() {
        assertEquals(0, extruderEditorScrollTarget(1))
        assertEquals(260, extruderEditorScrollTarget(8))
    }
}
