package de.psmobile.ui

import de.psmobile.shared.rules.EasyPanel

/** Pure, derived UI state for Easy Mode; selections remain owned by SlicerService. */
data class EasyReadiness(
    val missing: List<String>,
) {
    val canPrint: Boolean get() = missing.isEmpty()
}

enum class EasyProfileKind {
    PRINTER,
    FILAMENT,
    PRINT_SETTINGS,
}

data class EasyProfileSetupHint(
    val message: String,
)

data class EasyPrinterChoice(
    val label: String,
    val rawPreset: String,
)

data class EasyPrinterModelChoice(
    val label: String,
    val variants: List<EasyPrinterChoice>,
)

object EasyModeState {
    private val nozzleSuffix = Regex(
        pattern = """\s+(?:HF\s*)?\d+(?:[.,]\d+)?\s*mm?\s+nozzle$|\s+(?:HF\s*)?\d+(?:[.,]\d+)?\s+nozzle$""",
        option = RegexOption.IGNORE_CASE,
    )

    /** Project-local presets occasionally carry a temporary cache file name. */
    fun profileDisplayLabel(rawPreset: String): String =
        rawPreset.substringBefore(" (")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("^\\d+-"), "")
            .ifBlank { rawPreset }

    fun profileQueryAfterChange(
        queries: Map<String, String>,
        panel: EasyPanel,
        value: String,
    ): Map<String, String> = queries + (panel.name to value)

    fun profileQueryFor(queries: Map<String, String>, panel: EasyPanel): String =
        queries[panel.name].orEmpty()

    /** A second tap on an active toolbar action clears its contextual panel. */
    fun panelAfterToolbarSelection(currentPanel: EasyPanel, selectedPanel: EasyPanel): EasyPanel =
        if (currentPanel == selectedPanel) EasyPanel.HOME else selectedPanel

    fun panelTitle(panel: EasyPanel): String = when (panel) {
        EasyPanel.HOME -> "Easy Print"
        EasyPanel.PROJECTS -> "Projekt"
        EasyPanel.PRINTER -> "Druckermodell"
        EasyPanel.FILAMENT -> "Filament"
        EasyPanel.SUPPORTS -> "Supports"
        EasyPanel.ADHESION -> "Haftung"
        EasyPanel.PRINT_SETTINGS -> "Print Settings"
    }

    fun emptySearchMessage(panel: EasyPanel): String = when (panel) {
        EasyPanel.PRINTER -> "Keine Druckerprofile gefunden."
        EasyPanel.FILAMENT -> "Keine Filamentprofile gefunden."
        EasyPanel.PRINT_SETTINGS -> "Keine Print-Settings-Profile gefunden."
        else -> "Keine Auswahl verfügbar."
    }

    fun profileSetupAction(kind: EasyProfileKind): String = when (kind) {
        EasyProfileKind.PRINTER -> "Drucker einrichten"
        EasyProfileKind.FILAMENT -> "Filament einrichten"
        EasyProfileKind.PRINT_SETTINGS -> "Print Settings einrichten"
    }

    fun profileSetupHint(
        kind: EasyProfileKind,
        options: List<String>,
    ): EasyProfileSetupHint? {
        if (options.isNotEmpty()) return null

        val message = when (kind) {
            EasyProfileKind.PRINTER -> "Kein Druckerprofil eingerichtet."
            EasyProfileKind.FILAMENT -> "Keine Filamentprofile verfügbar."
            EasyProfileKind.PRINT_SETTINGS -> "Keine Print-Settings-Profile verfügbar."
        }
        return EasyProfileSetupHint(message)
    }

    fun filterPresets(presets: List<String>, query: String): List<String> {
        val normalizedQuery = query.trim().lowercase()
        return presets
            .asSequence()
            .filter { normalizedQuery.isEmpty() || it.lowercase().contains(normalizedQuery) }
            .sortedBy { it.lowercase() }
            .toList()
    }

    fun printerModelChoices(rawPresets: List<String>): List<EasyPrinterChoice> =
        rawPresets
            .map { rawPreset ->
                EasyPrinterChoice(
                    label = profileDisplayLabel(rawPreset).replace(nozzleSuffix, "").trim(),
                    rawPreset = rawPreset,
                )
            }
            .distinctBy { it.label.lowercase() }
            .sortedBy { it.label.lowercase() }

    fun printerModelsWithNozzles(rawPresets: List<String>): List<EasyPrinterModelChoice> =
        rawPresets
            .map { rawPreset -> rawPreset to profileDisplayLabel(rawPreset) }
            .groupBy { (_, displayName) -> displayName.replace(nozzleSuffix, "").trim() }
            .map { (label, presets) ->
                EasyPrinterModelChoice(
                    label = label,
                    variants = presets
                        .map { (rawPreset, displayName) ->
                            EasyPrinterChoice(
                                displayName.substringAfter(label).removePrefix(" ").ifBlank { "Standard" },
                                rawPreset,
                            )
                        }
                        .sortedBy { it.label },
                )
            }
            .sortedBy { it.label.lowercase() }

    fun printerModelLabel(rawPreset: String): String =
        printerModelChoices(listOf(rawPreset)).singleOrNull()?.label.orEmpty()

    fun readiness(
        modelCount: Int,
        printer: String,
        filament: String,
        printSettings: String,
    ): EasyReadiness = EasyReadiness(
        buildList {
            if (modelCount <= 0) add("Modell")
            if (printer.isBlank()) add("Drucker")
            if (filament.isBlank()) add("Filament")
            if (printSettings.isBlank()) add("Print Settings")
        },
    )
}
