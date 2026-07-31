package de.psmobile.ui

enum class ToolbarPlacement { FLOATING_TOP, COMPACT_HORIZONTAL, TABLET_ROW }

object SimpleModeLayout {
    fun toolbarPlacement(widthDp: Int, heightDp: Int): ToolbarPlacement = when {
        widthDp > heightDp -> ToolbarPlacement.COMPACT_HORIZONTAL
        widthDp >= 600 -> ToolbarPlacement.TABLET_ROW
        else -> ToolbarPlacement.FLOATING_TOP
    }

    fun usesDashboardCards(): Boolean = false

    /** Six primary actions must stay visible without a clipped seventh edge. */
    fun toolbarButtonWidthDp(widthDp: Int, placement: ToolbarPlacement): Int = when (placement) {
        ToolbarPlacement.FLOATING_TOP,
        ToolbarPlacement.TABLET_ROW -> ((widthDp - 30) / 6).coerceIn(46, 55)
        ToolbarPlacement.COMPACT_HORIZONTAL -> ((widthDp - 30) / 6).coerceIn(52, 64)
    }

    /** A half-height sheet hid material and support actions on landscape tablets. */
    fun usesExpandedOverlay(widthDp: Int, heightDp: Int): Boolean =
        widthDp > heightDp || widthDp >= 600
}
