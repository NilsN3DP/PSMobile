package de.psmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test fun phoneUsesTopPageRailInsteadOfFixedDesktopSidebar() {
        assertTrue(SettingsLayout.usesCompactNavigation(360))
    }

    @Test fun tabletKeepsPersistentPageSidebar() {
        assertFalse(SettingsLayout.usesCompactNavigation(800))
    }
}
