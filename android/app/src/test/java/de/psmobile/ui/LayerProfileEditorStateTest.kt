package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerProfileEditorStateTest {
    @Test
    fun `editor accepts strictly increasing positive points`() {
        val state = LayerProfileEditorState(
            objectHeight = 20.0,
            rows = listOf("0" to "0.12", "20" to "0.28"),
        )

        assertEquals(listOf(0.0 to 0.12, 20.0 to 0.28), state.validPoints)
        assertTrue(state.canApply)
    }

    @Test
    fun `editor refuses repeated Z points`() {
        val state = LayerProfileEditorState(
            objectHeight = 20.0,
            rows = listOf("0" to "0.2", "0" to "0.2"),
        )

        assertFalse(state.canApply)
    }
}
