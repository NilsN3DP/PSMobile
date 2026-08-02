package de.psmobile.ui

import de.psmobile.ui.SimpleModelSheetState.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleModelSheetStateTest {

    private val maxBeds = 36

    @Test
    fun `toggling picks and unpicks the same model`() {
        val once = SimpleModelSheetState.toggle(emptySet(), 7)
        assertEquals(setOf(7), once)
        assertTrue(SimpleModelSheetState.toggle(once, 7).isEmpty())
    }

    @Test
    fun `deleted models do not stay selected as ghosts`() {
        assertEquals(
            setOf(1, 3),
            SimpleModelSheetState.pruned(setOf(1, 2, 3), listOf(1, 3, 5)),
        )
    }

    @Test
    fun `arranging needs at least two objects`() {
        assertFalse(
            Action.ARRANGE in
                SimpleModelSheetState.enabledActions(1, emptySet(), 1, maxBeds),
        )
        assertTrue(
            Action.ARRANGE in
                SimpleModelSheetState.enabledActions(2, emptySet(), 1, maxBeds),
        )
    }

    @Test
    fun `clone and remove only appear with a selection`() {
        val none = SimpleModelSheetState.enabledActions(3, emptySet(), 1, maxBeds)
        assertFalse(Action.CLONE in none)
        assertFalse(Action.REMOVE in none)

        val some = SimpleModelSheetState.enabledActions(3, setOf(1), 1, maxBeds)
        assertTrue(Action.CLONE in some)
        assertTrue(Action.REMOVE in some)
    }

    @Test
    fun `moving to another bed stays possible while a new bed may be created`() {
        assertTrue(
            Action.MOVE_TO_BED in
                SimpleModelSheetState.enabledActions(3, setOf(1), 1, maxBeds),
        )
        // Alle Betten aufgebraucht und nur eines vorhanden waere der
        // einzige Fall ohne Ziel - er kann nicht eintreten, aber die
        // Regel muss ihn trotzdem sauber abbilden.
        assertFalse(
            Action.MOVE_TO_BED in
                SimpleModelSheetState.enabledActions(3, setOf(1), 1, 1),
        )
    }

    @Test
    fun `adding more is always possible, even on an empty bed`() {
        assertTrue(
            Action.ADD_MORE in
                SimpleModelSheetState.enabledActions(0, emptySet(), 1, maxBeds),
        )
        assertFalse(
            Action.SELECT_ALL in
                SimpleModelSheetState.enabledActions(0, emptySet(), 1, maxBeds),
        )
    }

    @Test
    fun `the headline switches to a count once something is picked`() {
        assertEquals("MODELS", SimpleModelSheetState.headline(emptySet()))
        assertTrue(SimpleModelSheetState.headline(setOf(1, 2)).contains("2"))
    }

    @Test
    fun `move targets skip the current bed and offer one new one`() {
        assertEquals(listOf(1, 2), SimpleModelSheetState.moveTargets(2, 0, maxBeds))
        assertEquals(listOf(0, 2), SimpleModelSheetState.moveTargets(2, 1, maxBeds))
        // Bei erschoepften Betten kommt kein neues mehr dazu.
        assertEquals(listOf(1), SimpleModelSheetState.moveTargets(2, 0, 2))
    }
}
