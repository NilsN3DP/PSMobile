package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteSettingRulesTest {

    @Test
    fun `favorites follow catalog order and drop unavailable keys`() {
        assertEquals(
            listOf("layer_height", "fill_density"),
            FavoriteSettingRules.sanitize(
                setOf("fill_density", "gone", "layer_height"),
                listOf("layer_height", "fill_density", "brim_width"),
            ),
        )
    }

    @Test
    fun `duplicate catalog keys appear once at their first available position`() {
        assertEquals(
            listOf("layer_height", "fill_density"),
            FavoriteSettingRules.sanitize(
                setOf("fill_density", "layer_height"),
                listOf("layer_height", "fill_density", "layer_height", "fill_density"),
            ),
        )
    }

    @Test
    fun `empty favorites or catalog produce no entries`() {
        assertEquals(emptyList(), FavoriteSettingRules.sanitize(emptySet(), listOf("layer_height")))
        assertEquals(emptyList(), FavoriteSettingRules.sanitize(setOf("layer_height"), emptyList()))
    }
}
