package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyModeLayoutTest {
    @Test
    fun compactEndsBefore600() {
        assertEquals(EasyWidthClass.COMPACT, EasyModeLayout.widthClass(599))
    }

    @Test
    fun mediumStartsAt600AndEndsBefore840() {
        assertEquals(EasyWidthClass.MEDIUM, EasyModeLayout.widthClass(600))
        assertEquals(EasyWidthClass.MEDIUM, EasyModeLayout.widthClass(839))
    }

    @Test
    fun expandedStartsAt840() {
        assertEquals(EasyWidthClass.EXPANDED, EasyModeLayout.widthClass(840))
    }

    @Test
    fun everyWidthClassHasAnExplicitDarkRoot() {
        EasyWidthClass.entries.forEach { widthClass ->
            assertTrue(EasyModeLayout.rootUsesDarkBackground(widthClass))
        }
    }

    @Test
    fun mediumUsesModalContextPanel() {
        assertFalse(EasyModeLayout.contextPanelIsPersistent(EasyWidthClass.MEDIUM))
    }

    @Test
    fun expandedUsesPersistentContextPanel() {
        assertTrue(EasyModeLayout.contextPanelIsPersistent(EasyWidthClass.EXPANDED))
    }

    @Test
    fun selectingAnActiveExpandedToolbarPanelClosesTheContext() {
        assertEquals(
            EasyPanel.HOME,
            EasyModeState.panelAfterToolbarSelection(EasyPanel.PRINTER, EasyPanel.PRINTER),
        )
    }

    @Test
    fun compactDockReservesEnoughScrollClearance() {
        assertTrue(EasyModeLayout.compactDockReservedHeightDp() >= 168)
    }
}
