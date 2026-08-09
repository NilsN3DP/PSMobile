package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSettingsTest {

    @Test
    fun `toggling adds and removes the same key`() {
        val once = FavoriteSettings.toggle(emptySet(), "fill_density")
        assertEquals(setOf("fill_density"), once)
        assertTrue(FavoriteSettings.toggle(once, "fill_density").isEmpty())
    }

    @Test
    fun `the order follows the pages, not the moment of marking`() {
        val tabOrder = listOf("layer_height", "perimeters", "fill_density", "brim_width")
        // Zuletzt gemerkt zuerst eingetragen - das darf nichts aendern.
        val favorites = setOf("brim_width", "layer_height")
        assertEquals(
            listOf("layer_height", "brim_width"),
            FavoriteSettings.orderedFor(favorites, tabOrder),
        )
    }

    @Test
    fun `keys from other tabs and vanished keys drop out`() {
        val tabOrder = listOf("layer_height", "perimeters")
        val favorites = setOf("layer_height", "filament_diameter", "geloescht_2024")
        assertEquals(
            listOf("layer_height"),
            FavoriteSettings.orderedFor(favorites, tabOrder),
        )
    }

    @Test
    fun `persisted favorites use the shared catalog sanitation`() {
        assertEquals(
            listOf("layer_height", "fill_density"),
            FavoriteSettings.orderedFor(
                setOf("fill_density", "gone", "layer_height"),
                listOf("layer_height", "fill_density", "layer_height"),
            ),
        )
    }

    @Test
    fun `keysOf walks pages and groups and drops duplicates`() {
        val pages = listOf(
            PsUi.Page(
                title = "Layers",
                icon = "",
                perExtruder = false,
                groups = listOf(
                    PsUi.Group("Height", listOf(PsUi.Option("layer_height"))),
                    PsUi.Group("Shell", listOf(PsUi.Option("perimeters"))),
                ),
            ),
            PsUi.Page(
                title = "Infill",
                icon = "",
                perExtruder = false,
                groups = listOf(
                    // Derselbe Schluessel kann auf zwei Seiten stehen.
                    PsUi.Group("Fill", listOf(PsUi.Option("fill_density"), PsUi.Option("layer_height"))),
                ),
            ),
        )
        assertEquals(
            listOf("layer_height", "perimeters", "fill_density"),
            FavoriteSettings.keysOf(pages),
        )
    }
}
