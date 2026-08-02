package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyModeStateTest {
    @Test
    fun printerQuerySurvivesAResponsiveBranchChange() {
        val entered = EasyModeState.profileQueryAfterChange(emptyMap(), EasyPanel.PRINTER, "core")

        assertEquals("core", entered[EasyPanel.PRINTER.name])
        assertEquals("core", EasyModeState.profileQueryFor(entered, EasyPanel.PRINTER))
    }

    @Test
    fun updatingOneProfileQueryKeepsOtherPanelQueries() {
        val existing = mapOf(
            EasyPanel.PRINTER.name to "mini",
            EasyPanel.FILAMENT.name to "pla",
            EasyPanel.PRINT_SETTINGS.name to "quality",
        )

        val updated = EasyModeState.profileQueryAfterChange(existing, EasyPanel.FILAMENT, "petg")

        assertEquals("mini", EasyModeState.profileQueryFor(updated, EasyPanel.PRINTER))
        assertEquals("petg", EasyModeState.profileQueryFor(updated, EasyPanel.FILAMENT))
        assertEquals("quality", EasyModeState.profileQueryFor(updated, EasyPanel.PRINT_SETTINGS))
    }

    @Test
    fun printerPanelHasClearTitle() {
        assertEquals("Druckermodell", EasyModeState.panelTitle(EasyPanel.PRINTER))
    }

    @Test
    fun emptyFilamentFilterShowsNoProfilesMessage() {
        assertTrue(EasyModeState.emptySearchMessage(EasyPanel.FILAMENT).contains("Keine"))
    }

    @Test
    fun searchKeepsOnlyMatchingPresetNamesInStableOrder() {
        val result = EasyModeState.filterPresets(
            presets = listOf("Prusa MINI+", "Prusa CORE One", "Bambu Lab A1", "Original Prusa MK4S"),
            query = "  prusa ",
        )

        assertEquals(
            listOf("Original Prusa MK4S", "Prusa CORE One", "Prusa MINI+"),
            result,
        )
    }

    @Test
    fun readinessRequiresModelAndEveryEasyProfileSelection() {
        val incomplete = EasyModeState.readiness(
            modelCount = 0,
            printer = "Prusa MINI+",
            filament = "",
            printSettings = "0.20mm QUALITY",
        )
        val ready = EasyModeState.readiness(
            modelCount = 1,
            printer = "Prusa MINI+",
            filament = "Prusament PLA",
            printSettings = "0.20mm QUALITY",
        )

        assertEquals(listOf("Modell", "Filament"), incomplete.missing)
        assertFalse(incomplete.canPrint)
        assertTrue(ready.canPrint)
    }

    @Test
    fun emptyProfileListsOfferTheExistingPrinterSetupFlow() {
        assertEquals(
            EasyProfileSetupHint("Kein Druckerprofil eingerichtet."),
            EasyModeState.profileSetupHint(EasyProfileKind.PRINTER, emptyList()),
        )
        assertEquals(
            EasyProfileSetupHint("Keine Filamentprofile verfügbar."),
            EasyModeState.profileSetupHint(EasyProfileKind.FILAMENT, emptyList()),
        )
        assertEquals(
            EasyProfileSetupHint("Keine Print-Settings-Profile verfügbar."),
            EasyModeState.profileSetupHint(EasyProfileKind.PRINT_SETTINGS, emptyList()),
        )
        assertEquals(
            null,
            EasyModeState.profileSetupHint(
                EasyProfileKind.PRINTER,
                listOf("Prusa CORE One"),
            ),
        )
    }

    @Test
    fun emptyProfileListsExposeContextualNextActions() {
        assertEquals(
            "Drucker einrichten",
            EasyModeState.profileSetupAction(EasyProfileKind.PRINTER),
        )
        assertEquals(
            "Filament einrichten",
            EasyModeState.profileSetupAction(EasyProfileKind.FILAMENT),
        )
        assertEquals(
            "Print Settings einrichten",
            EasyModeState.profileSetupAction(EasyProfileKind.PRINT_SETTINGS),
        )
    }

    @Test
    fun printerModelChoicesGroupNozzleVariants() {
        val choices = EasyModeState.printerModelChoices(
            listOf(
                "Prusa CORE One 0.4 nozzle",
                "Prusa CORE One 0.6 nozzle",
            ),
        )

        assertEquals(listOf("Prusa CORE One"), choices.map { it.label })
        assertEquals("Prusa CORE One 0.4 nozzle", choices.single().rawPreset)
        assertTrue(choices.single().rawPreset in listOf(
            "Prusa CORE One 0.4 nozzle",
            "Prusa CORE One 0.6 nozzle",
        ))
    }

    @Test
    fun project_local_printer_does_not_expose_its_cache_timestamp() {
        val local = "7221171563878-PSMobile Test Cube.3mf (embedded configuration)"

        assertEquals("PSMobile Test Cube.3mf", EasyModeState.printerModelLabel(local))
        assertEquals(
            "PSMobile Test Cube.3mf",
            EasyModeState.printerModelsWithNozzles(listOf(local)).single().label,
        )
    }

    @Test
    fun project_local_filament_does_not_expose_its_cache_path() {
        assertEquals(
            "PSMobile Test Cube.3mf",
            EasyModeState.profileDisplayLabel("/data/user/0/de.psmobile/cache/import/7-PSMobile Test Cube.3mf (material)"),
        )
    }
}
