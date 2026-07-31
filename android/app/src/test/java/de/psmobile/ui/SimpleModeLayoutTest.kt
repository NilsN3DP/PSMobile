package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SimpleModeLayoutTest {
    @Test fun portraitUsesFloatingReferenceToolbar() = assertEquals(
        ToolbarPlacement.FLOATING_TOP, SimpleModeLayout.toolbarPlacement(411, 891),
    )

    @Test fun landscapeKeepsWorkspaceAndUsesCompactRow() = assertEquals(
        ToolbarPlacement.COMPACT_HORIZONTAL, SimpleModeLayout.toolbarPlacement(891, 411),
    )

    @Test fun tabletUsesReferenceToolbarRow() = assertEquals(
        ToolbarPlacement.TABLET_ROW, SimpleModeLayout.toolbarPlacement(700, 900),
    )

    @Test fun neverUsesDashboardCards() = assertFalse(SimpleModeLayout.usesDashboardCards())

    @Test fun landscapeToolbarFitsAllSixActionsWithoutHorizontalClipping() {
        val width = SimpleModeLayout.toolbarButtonWidthDp(800, ToolbarPlacement.COMPACT_HORIZONTAL)
        assertEquals(64, width)
        assertEquals(true, width * 6 + 30 <= 800)
    }

    @Test fun portraitUsesCompactButtonsWithoutReducingTouchHeight() = assertEquals(
        55, SimpleModeLayout.toolbarButtonWidthDp(411, ToolbarPlacement.FLOATING_TOP),
    )

    @Test fun narrowPhoneKeepsAllSixToolbarActionsInsideTheViewport() {
        val width = SimpleModeLayout.toolbarButtonWidthDp(360, ToolbarPlacement.FLOATING_TOP)
        assertEquals(55, width)
        assertEquals(true, width * 6 + 30 <= 360)
    }

    @Test fun landscapeOverlaysExpandInsteadOfHidingTheirControlsBelowTheFold() = assertEquals(
        true, SimpleModeLayout.usesExpandedOverlay(800, 533),
    )
}
