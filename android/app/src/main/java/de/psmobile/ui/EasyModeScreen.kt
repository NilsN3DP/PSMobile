package de.psmobile.ui

import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.EasyPanel
import de.psmobile.shared.rules.EasyReadiness


/** Curated, touch-first entry point backed by the same SlicerService session as Advanced. */
@Composable
fun EasyModeScreen(
    service: SlicerService,
    window: Window,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onStartSlice: () -> Unit,
) {
    val presets by service.presets.collectAsState()
    val quick by service.quickSettings.collectAsState()
    val objects by service.objects.collectAsState()
    val configuration = LocalConfiguration.current
    val widthClass = remember(configuration.screenWidthDp) {
        EasyModeLayout.widthClass(configuration.screenWidthDp)
    }
    var currentPanel by rememberSaveable { mutableStateOf(EasyPanel.HOME) }
    var profileQueries by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    val readiness = remember(objects.size, presets) {
        EasyModeState.readiness(
            modelCount = objects.size,
            printer = presets.selectedPrinter,
            filament = presets.selectedFilament,
            printSettings = presets.selectedPrint,
        )
    }
    val printerModelLabel = remember(presets.selectedPrinter) {
        EasyModeState.printerModelLabel(presets.selectedPrinter)
    }

    EasyDarkSystemBars(window)
    Box(
        modifier = Modifier.fillMaxSize().background(PrusaColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (widthClass == EasyWidthClass.COMPACT) {
            EasyCompactContent(
                currentPanel = currentPanel,
                isShortScreen = configuration.screenHeightDp < 560,
                modelCount = objects.size,
                printer = printerModelLabel,
                filament = presets.selectedFilament,
                supportsEnabled = quick.supports == "1",
                adhesionEnabled = quick.brim.toFloatOrNull()?.let { it > 0f } == true,
                printSettings = presets.selectedPrint,
                readiness = readiness,
                service = service,
                presets = presets,
                queries = profileQueries,
                onProfileQueryChange = { panel, value ->
                    profileQueries = EasyModeState.profileQueryAfterChange(profileQueries, panel, value)
                },
                onOpenPanel = { panel ->
                    currentPanel = if (currentPanel == panel) EasyPanel.HOME else panel
                },
                onDismissPanel = { currentPanel = EasyPanel.HOME },
                onPickFile = onPickFile,
                onOpenAdvanced = onOpenAdvanced,
                onOpenPrinterSetup = onOpenPrinterSetup,
                onStartSlice = onStartSlice,
            )
        } else {
            EasyTabletContent(
                widthClass = widthClass,
                currentPanel = currentPanel,
                modelCount = objects.size,
                printer = printerModelLabel,
                filament = presets.selectedFilament,
                readiness = readiness,
                service = service,
                presets = presets,
                queries = profileQueries,
                onProfileQueryChange = { panel, value ->
                    profileQueries = EasyModeState.profileQueryAfterChange(profileQueries, panel, value)
                },
                onOpenPanel = { panel ->
                    currentPanel = EasyModeState.panelAfterToolbarSelection(currentPanel, panel)
                },
                onDismissPanel = { currentPanel = EasyPanel.HOME },
                onPickFile = onPickFile,
                onOpenAdvanced = onOpenAdvanced,
                onOpenPrinterSetup = onOpenPrinterSetup,
                onStartSlice = onStartSlice,
            )
        }
    }
}

@Composable
private fun EasyCompactContent(
    currentPanel: EasyPanel,
    isShortScreen: Boolean,
    modelCount: Int,
    printer: String,
    filament: String,
    supportsEnabled: Boolean,
    adhesionEnabled: Boolean,
    printSettings: String,
    readiness: EasyReadiness,
    service: SlicerService,
    presets: SlicerService.Presets,
    queries: Map<String, String>,
    onProfileQueryChange: (EasyPanel, String) -> Unit,
    onOpenPanel: (EasyPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onStartSlice: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (currentPanel == EasyPanel.HOME) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            ) {
                EasyCompactHeader(
                    printer = printer,
                    onOpenProjects = { onOpenPanel(EasyPanel.PROJECTS) },
                    onOpenPrinter = { onOpenPanel(EasyPanel.PRINTER) },
                    onOpenAdvanced = onOpenAdvanced,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .padding(
                            bottom = EasyModeLayout.compactDockReservedHeightDp().dp +
                                navigationBottom,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EasyHome(modelCount, printer, filament, supportsEnabled, adhesionEnabled, printSettings, readiness, onPickFile, onOpenPanel)
                }
            }
            EasyPrintDock(
                readiness = readiness,
                onStartSlice = onStartSlice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + navigationBottom),
            )
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EasyCompactHeader(
                printer = printer,
                onOpenProjects = { onOpenPanel(EasyPanel.PROJECTS) },
                onOpenPrinter = { onOpenPanel(EasyPanel.PRINTER) },
                onOpenAdvanced = onOpenAdvanced,
            )
        }
        EasySelectionSheet(currentPanel, isShortScreen, service, presets, queries, onProfileQueryChange, onDismissPanel, onOpenAdvanced, onOpenPrinterSetup, onPickFile)
    }
}

@Composable
private fun EasyTabletContent(
    widthClass: EasyWidthClass,
    currentPanel: EasyPanel,
    modelCount: Int,
    printer: String,
    filament: String,
    readiness: EasyReadiness,
    service: SlicerService,
    presets: SlicerService.Presets,
    queries: Map<String, String>,
    onProfileQueryChange: (EasyPanel, String) -> Unit,
    onOpenPanel: (EasyPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onStartSlice: () -> Unit,
) {
    val hasPersistentContext = EasyModeLayout.contextPanelIsPersistent(widthClass)
    Column(Modifier.fillMaxSize()) {
        EasyToolbar(
            printer = printer,
            currentPanel = currentPanel,
            readiness = readiness,
            onOpenPanel = onOpenPanel,
            onStartSlice = onStartSlice,
        )
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EasyWorkspace(modelCount, onPickFile, Modifier.weight(1f))
            if (hasPersistentContext && currentPanel != EasyPanel.HOME) {
                EasyContextPanel(
                    panel = currentPanel,
                    service = service,
                    presets = presets,
                    queries = queries,
                    onProfileQueryChange = onProfileQueryChange,
                    onDismiss = onDismissPanel,
                    onOpenAdvanced = onOpenAdvanced,
                    onOpenPrinterSetup = onOpenPrinterSetup,
                    onPickFile = onPickFile,
                    modifier = Modifier.width(420.dp),
                )
            }
        }
    }
    if (!hasPersistentContext && currentPanel != EasyPanel.HOME) {
        EasySelectionSheet(currentPanel, false, service, presets, queries, onProfileQueryChange, onDismissPanel, onOpenAdvanced, onOpenPrinterSetup, onPickFile)
    }
}

@Composable
private fun EasyToolbar(
    printer: String,
    currentPanel: EasyPanel,
    readiness: EasyReadiness,
    onOpenPanel: (EasyPanel) -> Unit,
    onStartSlice: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().background(PrusaColors.Panel).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    EasyToolbarAction("Projects", currentPanel == EasyPanel.PROJECTS) { onOpenPanel(EasyPanel.PROJECTS) }
    EasyToolbarAction(printer.ifBlank { "Printer" }, currentPanel == EasyPanel.PRINTER) { onOpenPanel(EasyPanel.PRINTER) }
    EasyToolbarAction("Material", currentPanel == EasyPanel.FILAMENT) { onOpenPanel(EasyPanel.FILAMENT) }
    EasyToolbarAction("Supports", currentPanel == EasyPanel.SUPPORTS) { onOpenPanel(EasyPanel.SUPPORTS) }
    EasyToolbarAction("Print Settings", currentPanel == EasyPanel.PRINT_SETTINGS) { onOpenPanel(EasyPanel.PRINT_SETTINGS) }
    TextButton(onClick = onStartSlice, enabled = readiness.canPrint, modifier = Modifier.heightIn(min = 48.dp)) { Text("Preview") }
    Button(onClick = onStartSlice, enabled = readiness.canPrint, modifier = Modifier.heightIn(min = 48.dp)) { Text("Print") }
}

@Composable
private fun EasyToolbarAction(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    } else {
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}

@Composable
private fun EasyWorkspace(modelCount: Int, onPickFile: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = PrusaColors.Panel),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EasyPrint Workspace", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (modelCount == 0) "Noch kein Modell geladen" else "$modelCount Objekt(e) bereit",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(onClick = onPickFile, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(if (modelCount == 0) "Modell importieren" else "Weiteres Modell importieren")
                }
            }
        }
    }
}

@Composable
private fun EasyContextPanel(
    panel: EasyPanel,
    service: SlicerService,
    presets: SlicerService.Presets,
    queries: Map<String, String>,
    onProfileQueryChange: (EasyPanel, String) -> Unit,
    onDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) = Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = PrusaColors.Panel)) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Schließen") }
        EasySelectionContent(panel, service, presets, queries, onProfileQueryChange, onDismiss, onOpenAdvanced, onOpenPrinterSetup, onPickFile)
    }
}

@Composable
private fun EasyDarkSystemBars(window: Window) {
    DisposableEffect(window) {
        window.statusBarColor = PrusaColors.Background.value.toInt()
        window.navigationBarColor = PrusaColors.Background.value.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            window.statusBarColor = PrusaColors.Background.value.toInt()
            window.navigationBarColor = PrusaColors.Background.value.toInt()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}

@Composable
private fun EasyCompactHeader(
    printer: String,
    onOpenProjects: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenAdvanced: () -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(PrusaColors.Panel)
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Text("EASY PRINT", color = PrusaColors.Orange, style = MaterialTheme.typography.labelLarge)
    TextButton(onClick = onOpenProjects, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Projects")
    }
    TextButton(onClick = onOpenPrinter, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(printer.ifBlank { "Printer" })
    }
    OutlinedButton(onClick = onOpenAdvanced, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Advanced")
    }
}

@Composable
private fun EasyHome(
    modelCount: Int,
    printer: String,
    filament: String,
    supportsEnabled: Boolean,
    adhesionEnabled: Boolean,
    printSettings: String,
    readiness: EasyReadiness,
    onPickFile: () -> Unit,
    onOpenPanel: (EasyPanel) -> Unit,
) {
    EasySummary("Projekt", if (modelCount == 0) "Noch kein Modell geladen" else "$modelCount Objekt(e) bereit") {
        onOpenPanel(EasyPanel.PROJECTS)
    }
    Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (modelCount == 0) "Modell auswählen" else "Weiteres Modell hinzufügen")
    }
    EasySummary("Druckermodell", printer.ifBlank { "Nicht ausgewählt" }) { onOpenPanel(EasyPanel.PRINTER) }
    EasySummary("Filament", filament.ifBlank { "Nicht ausgewählt" }) { onOpenPanel(EasyPanel.FILAMENT) }
    EasySummary("Supports", if (supportsEnabled) "Automatisch erzeugen" else "Keine Supports") {
        onOpenPanel(EasyPanel.SUPPORTS)
    }
    EasySummary("Haftung", if (adhesionEnabled) "5-mm-Brim" else "Ohne zusätzliche Haftung") {
        onOpenPanel(EasyPanel.ADHESION)
    }
    EasySummary("Print Settings", printSettings.ifBlank { "Nicht ausgewählt" }) {
        onOpenPanel(EasyPanel.PRINT_SETTINGS)
    }
}

@Composable
private fun EasySummary(title: String, summary: String, onClick: () -> Unit) {
    EasySectionCard(Modifier.clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(summary, color = PrusaColors.TextMuted)
    }
}

@Composable
private fun EasyPrintDock(readiness: EasyReadiness, onStartSlice: () -> Unit, modifier: Modifier = Modifier) =
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = PrusaColors.Panel)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!readiness.canPrint) {
                Text("Es fehlt: ${readiness.missing.first()}", color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onStartSlice,
                    enabled = readiness.canPrint,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Preview") }
                Button(
                    onClick = onStartSlice,
                    enabled = readiness.canPrint,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Print") }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EasySelectionSheet(
    panel: EasyPanel,
    isShortScreen: Boolean,
    service: SlicerService,
    presets: SlicerService.Presets,
    queries: Map<String, String>,
    onProfileQueryChange: (EasyPanel, String) -> Unit,
    onDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onPickFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = if (isShortScreen) Modifier.fillMaxHeight() else Modifier,
        containerColor = PrusaColors.Panel,
    ) {
        // Siehe SlicerScreen.kt (Bettauswahl): ModalBottomSheet traegt die
        // in PSMobileTheme gestauchte Dichte nicht automatisch weiter,
        // ohne das hier waeren Drucker-/Filament-/Profilauswahl neben dem
        // Rest der App aufgeblasen.
        ScaledOverlay {
            EasySelectionContent(panel, service, presets, queries, onProfileQueryChange, onDismiss, onOpenAdvanced, onOpenPrinterSetup, onPickFile)
        }
    }
}

@Composable
private fun EasySelectionContent(
    panel: EasyPanel,
    service: SlicerService,
    presets: SlicerService.Presets,
    queries: Map<String, String>,
    onProfileQueryChange: (EasyPanel, String) -> Unit,
    onDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onPickFile: () -> Unit,
) {
    val quick by service.quickSettings.collectAsState()
    when (panel) {
        EasyPanel.PRINTER -> EasyPrinterModelSelector(presets.printers, presets.selectedPrinter, service, queries[panel.name].orEmpty(), { onProfileQueryChange(panel, it) }, onDismiss, onOpenPrinterSetup)
        EasyPanel.FILAMENT -> EasyProfileSelector(panel, presets.filaments, presets.selectedFilament, PsmCore.PresetType.FILAMENT, service, queries[panel.name].orEmpty(), { onProfileQueryChange(panel, it) }, onDismiss, onOpenAdvanced)
        EasyPanel.PRINT_SETTINGS -> EasyProfileSelector(panel, presets.prints, presets.selectedPrint, PsmCore.PresetType.PRINT, service, queries[panel.name].orEmpty(), { onProfileQueryChange(panel, it) }, onDismiss, onOpenAdvanced)
        EasyPanel.SUPPORTS -> EasySupportsSelector(
            service = service,
            supports = quick.supports,
            brim = quick.brim,
        )
        EasyPanel.ADHESION -> EasyChoiceSelector(
            title = EasyModeState.panelTitle(panel),
            choices = listOf("5-mm-Brim" to "5", "Ohne zusätzliche Haftung" to "0"),
            selected = quick.brim,
            configKey = "brim_width",
            service = service,
            onDismiss = onDismiss,
        )
        EasyPanel.PROJECTS -> EasyProjectSheet(onDismiss, onPickFile)
        EasyPanel.HOME -> Unit
    }
}

@Composable
private fun EasyPrinterModelSelector(
    rawPresets: List<String>,
    selectedRawPreset: String,
    service: SlicerService,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSetup: () -> Unit,
) {
    val choices = remember(rawPresets) { EasyModeState.printerModelChoices(rawPresets) }
    val selectedLabel = remember(selectedRawPreset) {
        EasyModeState.printerModelLabel(selectedRawPreset)
    }
    val filtered = remember(choices, query) {
        val matchingLabels = EasyModeState.filterPresets(choices.map { it.label }, query).toSet()
        choices.filter { it.label in matchingLabels }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(EasyModeState.panelTitle(EasyPanel.PRINTER), style = MaterialTheme.typography.headlineSmall)
        TextField(query, onQueryChange, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Suchen") }, singleLine = true)
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (filtered.isEmpty()) {
                Text(EasyModeState.emptySearchMessage(EasyPanel.PRINTER), color = PrusaColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = onSetup, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Drucker einrichten")
                }
            } else filtered.forEach { choice ->
                TextButton(
                    onClick = {
                        service.selectPreset(PsmCore.PresetType.PRINTER, choice.rawPreset)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (choice.label == selectedLabel) "✓  ${choice.label}" else choice.label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EasyProfileSelector(
    panel: EasyPanel,
    options: List<String>,
    selected: String,
    type: PsmCore.PresetType,
    service: SlicerService,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSetup: () -> Unit,
) {
    val filtered = remember(options, query) { EasyModeState.filterPresets(options, query) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(EasyModeState.panelTitle(panel), style = MaterialTheme.typography.headlineSmall)
        TextField(query, onQueryChange, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Suchen") }, singleLine = true)
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (filtered.isEmpty()) {
                Text(EasyModeState.emptySearchMessage(panel), color = PrusaColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = onSetup, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (panel == EasyPanel.PRINTER) "Drucker einrichten" else "In Advanced einrichten")
                }
            } else filtered.forEach { option ->
                TextButton(
                    onClick = { service.selectPreset(type, option); onDismiss() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (option == selected) "✓  $option" else option, modifier = Modifier.fillMaxWidth()) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EasySupportsSelector(
    service: SlicerService,
    supports: String,
    brim: String,
) {
    Column(
        Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Supports", style = MaterialTheme.typography.headlineSmall)
        listOf("Automatisch erzeugen" to "1", "Keine Supports" to "0").forEach { (label, value) ->
            OutlinedButton(
                onClick = { service.setConfig("support_material", value) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (supports == value) "✓  $label" else label) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Haftung", style = MaterialTheme.typography.headlineSmall)
        listOf("5-mm-Brim" to "5", "Ohne zusätzliche Haftung" to "0").forEach { (label, value) ->
            OutlinedButton(
                onClick = { service.setConfig("brim_width", value) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (brim == value) "✓  $label" else label) }
        }
    }
}

@Composable
private fun EasyChoiceSelector(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    configKey: String,
    service: SlicerService,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        choices.forEach { (label, value) ->
            OutlinedButton(
                onClick = { service.setConfig(configKey, value); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (selected == value) "✓  $label" else label) }
        }
    }
}

@Composable
private fun EasyProjectSheet(onDismiss: () -> Unit, onPickFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Projekt", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onPickFile(); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Modell auswählen")
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Zur Übersicht") }
    }
}

@Composable
private fun EasySectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PrusaColors.Panel),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
