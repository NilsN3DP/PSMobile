package de.psmobile.ui

/** Breakpoint for Advanced settings: phones need their values full-width. */
object SettingsLayout {
    fun usesCompactNavigation(widthDp: Int): Boolean = widthDp < 720
}
