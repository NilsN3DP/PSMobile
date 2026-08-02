package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SimpleModeStateTest {
    @Test
    fun simpleModeUsesOnlyTheApprovedVisibleName() {
        assertEquals("Simple Mode", SimpleModeState.visibleBrand())
        assertFalse(SimpleModeState.toolbarLabels().any { it.contains("EasyPrint") })
    }

    @Test
    fun englishToolbarUsesEnglishLabelsInsteadOfGermanFallbacks() {
        assertEquals(
            listOf("Projects", "Printer", "Material", "Settings", "Preview", "G-Code"),
            SimpleModeState.toolbarLabels(),
        )
    }

    @Test
    fun simpleProjectSummaryUsesEnglishWhenTheAppLanguageIsEnglish() {
        assertEquals("Current project", applicationText("en", "Current project", "Aktuelles Projekt"))
        assertEquals("This session", applicationText("en", "This session", "Diese Sitzung"))
    }

    @Test
    fun printerUsesModelsRatherThanNozzleVariants() {
        assertEquals("Prusa CORE One", SimpleModeState.printerLabel("Prusa CORE One 0.4 nozzle"))
    }

    @Test
    fun materialQuickFiltersMatchTheReferenceBrowser() {
        assertEquals(listOf("PLA", "PETG", "ASA", "ABS", "FLEX"), SimpleModeState.materialTypes())
    }

    @Test
    fun supportAndAdhesionMenusKeepTheReferenceGroups() {
        assertEquals(listOf("Disabled", "Everywhere", "Build plate only"), SimpleModeState.supportGroups())
        assertEquals(listOf("Disabled", "Automatic", "Outline around the model"), SimpleModeState.adhesionChoices())
    }

    @Test
    fun simpleSupportChoicesUseTheSameRealConfigKeysAsAdvanced() {
        assertEquals(
            mapOf(
                "support_material" to "1",
                "support_material_auto" to "1",
                "support_material_buildplate_only" to "0",
                "support_material_style" to "organic",
            ),
            SimpleModeState.supportConfig(SimpleSupportChoice.ORGANIC_EVERYWHERE),
        )
        assertEquals(
            mapOf(
                "support_material" to "1",
                "support_material_auto" to "1",
                "support_material_buildplate_only" to "1",
                "support_material_style" to "snug",
            ),
            SimpleModeState.supportConfig(SimpleSupportChoice.SNUG_BUILD_PLATE),
        )
    }

    @Test
    fun selectedSupportChoiceReflectsTheActivePrusaSlicerConfiguration() {
        assertEquals(
            SimpleSupportChoice.ORGANIC_BUILD_PLATE,
            SimpleModeState.selectedSupportChoice("1", "1", "1", "organic"),
        )
        assertEquals(
            SimpleSupportChoice.DISABLED,
            SimpleModeState.selectedSupportChoice("0", "1", "0", "snug"),
        )
    }

    @Test
    fun printSettingsUsesTheThreeReferenceColumns() {
        assertEquals(listOf("Print Settings", "Infill", "Shell Thickness"), SimpleModeState.printSettingsColumns())
    }

    @Test
    fun nested_simple_settings_go_back_to_settings_before_workspace() {
        assertEquals(SimplePanel.SETTINGS, SimpleModeState.backDestination(SimplePanel.SUPPORTS))
        assertEquals(SimplePanel.SETTINGS, SimpleModeState.backDestination(SimplePanel.ADHESION))
        assertEquals(SimplePanel.SETTINGS, SimpleModeState.backDestination(SimplePanel.PRINT_SETTINGS))
        assertEquals(SimplePanel.WORKSPACE, SimpleModeState.backDestination(SimplePanel.MATERIAL))
    }
}
