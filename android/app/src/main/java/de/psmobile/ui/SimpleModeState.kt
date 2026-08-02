package de.psmobile.ui

/** Pure labels and panel state for the reference-aligned Simple Mode UI. */
enum class SimplePanel {
    WORKSPACE,
    PROJECTS,
    PRINTER,
    MATERIAL,
    SETTINGS,
    SUPPORTS,
    ADHESION,
    PRINT_SETTINGS,
}

enum class SimpleSupportChoice {
    DISABLED,
    SNUG_EVERYWHERE,
    ORGANIC_EVERYWHERE,
    SNUG_BUILD_PLATE,
    ORGANIC_BUILD_PLATE,
}

object SimpleModeState {
    /** Simple Mode owns a few curated phrases absent from the desktop PO catalog. */
    fun text(english: String, german: String): String =
        if (PsUi.language == "de") german else english

    fun visibleBrand() = "Simple Mode"

    fun toolbarLabels() = listOf(
        "Projects",
        "Printer",
        "Material",
        "Settings",
        "Preview",
        "G-Code",
    )

    fun printerLabel(rawPreset: String): String = EasyModeState.printerModelLabel(rawPreset)

    fun materialTypes() = listOf("PLA", "PETG", "ASA", "ABS", "FLEX")

    fun supportGroups() = listOf("Disabled", "Everywhere", "Build plate only")

    fun supportConfig(choice: SimpleSupportChoice): Map<String, String> = when (choice) {
        SimpleSupportChoice.DISABLED -> mapOf("support_material" to "0")
        SimpleSupportChoice.SNUG_EVERYWHERE -> supportConfig("snug", buildPlateOnly = false)
        SimpleSupportChoice.ORGANIC_EVERYWHERE -> supportConfig("organic", buildPlateOnly = false)
        SimpleSupportChoice.SNUG_BUILD_PLATE -> supportConfig("snug", buildPlateOnly = true)
        SimpleSupportChoice.ORGANIC_BUILD_PLATE -> supportConfig("organic", buildPlateOnly = true)
    }

    private fun supportConfig(style: String, buildPlateOnly: Boolean) = mapOf(
        "support_material" to "1",
        "support_material_auto" to "1",
        "support_material_buildplate_only" to if (buildPlateOnly) "1" else "0",
        "support_material_style" to style,
    )

    fun selectedSupportChoice(
        supports: String,
        automatic: String,
        buildPlateOnly: String,
        style: String,
    ): SimpleSupportChoice = when {
        supports != "1" -> SimpleSupportChoice.DISABLED
        buildPlateOnly == "1" && style == "organic" -> SimpleSupportChoice.ORGANIC_BUILD_PLATE
        buildPlateOnly == "1" -> SimpleSupportChoice.SNUG_BUILD_PLATE
        style == "organic" -> SimpleSupportChoice.ORGANIC_EVERYWHERE
        automatic == "1" -> SimpleSupportChoice.SNUG_EVERYWHERE
        else -> SimpleSupportChoice.SNUG_EVERYWHERE
    }

    fun adhesionChoices() = listOf("Disabled", "Automatic", "Outline around the model")

    fun printSettingsColumns() = listOf("Print Settings", "Infill", "Shell Thickness")

    fun projectSummaryCopy(): Triple<String, String, String> = Triple(
        text("Current project", "Aktuelles Projekt"),
        text("No printer selected", "Drucker nicht gewählt"),
        text("This session", "Diese Sitzung"),
    )

    fun backDestination(panel: SimplePanel): SimplePanel = when (panel) {
        SimplePanel.SUPPORTS,
        SimplePanel.ADHESION,
        SimplePanel.PRINT_SETTINGS -> SimplePanel.SETTINGS
        else -> SimplePanel.WORKSPACE
    }
}
