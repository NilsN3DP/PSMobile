package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionModelTest {
    @Test
    fun deletedIdsDisappearAndPrimaryFallsBackInStableOrder() {
        val state = SelectionModel(
            linkedSetOf(7, 3, 9),
            primaryId = 3,
        ).reconcile(listOf(7, 9, 11))
        assertEquals(linkedSetOf(7, 9), state.ids)
        assertEquals(7, state.primaryId)
    }

    @Test
    fun togglePromotesAddedObjectAndFallsBackWhenRemoved() {
        val two = SelectionModel.single(4).toggle(8)
        assertEquals(linkedSetOf(4, 8), two.ids)
        assertEquals(8, two.primaryId)
        val one = two.toggle(8)
        assertEquals(linkedSetOf(4), one.ids)
        assertEquals(4, one.primaryId)
    }

    @Test
    fun selectAllAndClearAreDeterministic() {
        val all = SelectionModel.all(listOf(5, 2, 9))
        assertEquals(linkedSetOf(5, 2, 9), all.ids)
        assertEquals(5, all.primaryId)
        assertNull(SelectionModel.single(null).primaryId)
    }
}
