package de.psmobile.ui

enum class EasyWidthClass { COMPACT, MEDIUM, EXPANDED }

object EasyModeLayout {
    fun widthClass(widthDp: Int) = when {
        widthDp < 600 -> EasyWidthClass.COMPACT
        widthDp < 840 -> EasyWidthClass.MEDIUM
        else -> EasyWidthClass.EXPANDED
    }

    /** Every Easy layout paints its own root so no theme/default white leaks through. */
    fun rootUsesDarkBackground(widthClass: EasyWidthClass): Boolean = when (widthClass) {
        EasyWidthClass.COMPACT,
        EasyWidthClass.MEDIUM,
        EasyWidthClass.EXPANDED,
        -> true
    }

    /** Large tablets keep the selected Easy setting visible beside the workspace. */
    fun contextPanelIsPersistent(widthClass: EasyWidthClass): Boolean =
        widthClass == EasyWidthClass.EXPANDED

    /** Fixed Compact Preview/Print dock plus its outer spacing. */
    fun compactDockReservedHeightDp(): Int = 168
}
