package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetPickerTest {
    @Test
    fun profileSearchIsCaseInsensitiveAndIgnoresLeadingWhitespace() {
        val presets = listOf("0.20mm SPEED @COREONE", "0.15mm QUALITY @COREONE")

        assertEquals(
            listOf("0.20mm SPEED @COREONE"),
            filterPresetOptions(presets, "  speed"),
        )
    }

    @Test
    fun emptyProfileSearchKeepsEveryAvailablePreset() {
        val presets = listOf("PLA", "PETG")

        assertEquals(presets, filterPresetOptions(presets, ""))
    }

    @Test
    fun materialChooserUsesTheSameSearchButKeepsTheTouchGridBounded() {
        val materials = (1..20).map { "Prusament PLA $it" }

        assertEquals(
            listOf("Prusament PLA 20"),
            advancedFilamentCards(materials, "pla 20"),
        )
        assertEquals(12, advancedFilamentCards(materials, "").size)
    }
}
