package de.psmobile.ui

import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors

/** Workspace-first Simple Mode, deliberately separate from Advanced Mode chrome. */
@Composable
fun SimpleModeScreen(
    service: SlicerService,
    window: Window,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onStartSlice: () -> Unit,
) {
    val presets by service.presets.collectAsState()
    val objects by service.objects.collectAsState()
    val quick by service.quickSettings.collectAsState()
    val sceneRevision by service.sceneRevision.collectAsState()
    val configuration = LocalConfiguration.current
    var panel by rememberSaveable { mutableStateOf(SimplePanel.WORKSPACE) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val controller = remember { SceneController() }
    val placement = SimpleModeLayout.toolbarPlacement(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    )
    val compactChrome = placement == ToolbarPlacement.COMPACT_HORIZONTAL
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val workspaceActionBottom = 64.dp + navigationBottom
    // Das Referenzmenü beginnt direkt unter der Toolbar. Auf einem Tablet im
    // Querformat darf es nie unter die Systemnavigation laufen.
    val overlayTop = if (compactChrome) 108.dp else 136.dp
    val overlayMaxHeight = if (compactChrome) {
        (configuration.screenHeightDp - 108).coerceAtLeast(220).dp
    } else if (configuration.screenWidthDp >= 600) 720.dp else 600.dp

    LaunchedEffect(controller) { controller.onScaled = service::notifyViewportChanged }
    SimpleDarkSystemBars(window)
    // Die System-Zurück-Geste schließt erst das aktuelle Simple-Menü. Ohne
    // diesen Handler beendet sie den Slicer trotz offenem Overlay.
    BackHandler(enabled = panel != SimplePanel.WORKSPACE) {
        panel = SimplePanel.WORKSPACE
    }

    Box(Modifier.fillMaxSize().background(PrusaColors.Background)) {
        SimpleSceneWorkspace(
            service = service,
            sceneRevision = sceneRevision,
            selectedId = selectedId,
            onSelect = { selectedId = it.takeIf { id -> id >= 0 } },
            controller = controller,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            SimpleHeader(
                printer = SimpleModeState.printerLabel(presets.selectedPrinter),
                compact = compactChrome,
            )
            SimpleToolbar(
                placement = placement,
                widthDp = configuration.screenWidthDp,
                compact = compactChrome,
                selected = panel,
                canPrint = objects.isNotEmpty() && presets.selectedPrinter.isNotBlank() &&
                    presets.selectedFilament.isNotBlank() && presets.selectedPrint.isNotBlank(),
                onPanel = { panel = if (panel == it) SimplePanel.WORKSPACE else it },
                onStartSlice = onStartSlice,
            )
        }
        SimpleUndoRedo(
            onUndo = service::undo,
            onRedo = service::redo,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = workspaceActionBottom + 12.dp),
        )
        Button(
            onClick = onPickFile,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = workspaceActionBottom + 12.dp).heightIn(min = 56.dp),
        ) { Text("＋ Modell hinzufügen") }
        SimpleBottomNavigation(
            modifier = Modifier.align(Alignment.BottomCenter),
            onOpenProjects = { panel = SimplePanel.PROJECTS },
            onOpenSettings = { panel = SimplePanel.SETTINGS },
            onOpenAdvanced = onOpenAdvanced,
        )
    }

    if (panel != SimplePanel.WORKSPACE) {
        SimpleOverlay(
            panel = panel,
            service = service,
            presets = presets,
            quick = quick,
            brim = quick.brim,
            top = overlayTop,
            maxHeight = overlayMaxHeight,
            onDismiss = { panel = SimplePanel.WORKSPACE },
            onNavigate = { panel = it },
            onPickFile = onPickFile,
            onOpenAdvanced = onOpenAdvanced,
            onOpenPrinterSetup = onOpenPrinterSetup,
        )
    }
}

@Composable
private fun SimpleSceneWorkspace(
    service: SlicerService,
    sceneRevision: Int,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    controller: SceneController,
    modifier: Modifier,
) = SceneView(
    core = service.coreOrNull,
    shaderDir = remember(service) { service.shaderDir() },
    selectedId = selectedId,
    onSelect = onSelect,
    invalidateKey = SimpleWorkspaceState.invalidateKey(sceneRevision),
    controller = controller,
    modifier = modifier,
)

@Composable
private fun SimpleHeader(printer: String, compact: Boolean) = Row(
    Modifier.fillMaxWidth().background(PrusaColors.Background)
        .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 6.dp else 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text("●", color = PrusaColors.TextMuted,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "◆  ${SimpleModeState.visibleBrand()}",
            color = PrusaColors.TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        )
        if (printer.isNotBlank()) Text(printer, color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
    Text("♧", color = PrusaColors.Orange,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
}

@Composable
private fun SimpleToolbar(
    placement: ToolbarPlacement,
    widthDp: Int,
    compact: Boolean,
    selected: SimplePanel,
    canPrint: Boolean,
    onPanel: (SimplePanel) -> Unit,
    onStartSlice: () -> Unit,
) {
    val labels = SimpleModeState.toolbarLabels()
    val panels = listOf(SimplePanel.PROJECTS, SimplePanel.PRINTER, SimplePanel.MATERIAL, SimplePanel.SETTINGS)
    val buttonWidth = SimpleModeLayout.toolbarButtonWidthDp(widthDp, placement).dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        panels.forEachIndexed { index, target ->
            SimpleToolbarButton(
                label = labels[index],
                selected = selected == target,
                width = buttonWidth,
                compact = compact,
                onClick = { onPanel(target) },
            )
        }
        Spacer(Modifier.weight(1f))
        SimpleToolbarButton(label = labels[4], selected = false, width = buttonWidth, compact = compact, enabled = canPrint, onClick = onStartSlice)
        SimpleToolbarButton(label = labels[5], selected = true, width = (buttonWidth.value * 1.35f).dp, compact = compact, enabled = canPrint, onClick = onStartSlice)
    }
}

@Composable
private fun SimpleToolbarButton(
    label: String,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(1.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = if (selected) PrusaColors.Orange else PrusaColors.Panel,
        contentColor = if (selected) PrusaColors.Background else PrusaColors.TextPrimary,
    ),
    border = BorderStroke(1.dp, if (selected) PrusaColors.Orange else PrusaColors.Divider),
    modifier = Modifier.height(if (compact) 46.dp else 54.dp).width(width),
    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
) {
    Text(
        simpleToolbarLabel(label),
        maxLines = 2,
        textAlign = TextAlign.Center,
        fontSize = if (compact) 9.sp else 10.sp,
        lineHeight = if (compact) 10.sp else 12.sp,
    )
}

private fun simpleToolbarLabel(label: String): String = when (label) {
    "Projekte" -> "▣\nProjekte"
    "Drucker" -> "▤\nDrucker"
    "Material" -> "◎\nMaterial"
    "Einstellen" -> "☷\nEinstell."
    "Vorschau" -> "▱\nVorschau"
    "G-Code" -> "➤  G-Code"
    else -> label
}

@Composable
private fun SimpleUndoRedo(onUndo: () -> Unit, onRedo: () -> Unit, modifier: Modifier) = Row(
    modifier.background(PrusaColors.Panel, RoundedCornerShape(4.dp)),
) {
    TextButton(onClick = onUndo, modifier = Modifier.height(52.dp).width(72.dp)) { Text("↶\nUndo") }
    TextButton(onClick = onRedo, modifier = Modifier.height(52.dp).width(72.dp)) { Text("↷\nRedo") }
}

@Composable
private fun SimpleBottomNavigation(
    modifier: Modifier,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdvanced: () -> Unit,
) = Row(
    modifier.fillMaxWidth().background(PrusaColors.Panel).padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
) {
    listOf(
        "Projekte" to onOpenProjects,
        "Einstellungen" to onOpenSettings,
        "Advanced" to onOpenAdvanced,
    ).forEach { (label, action) ->
        TextButton(onClick = action, modifier = Modifier.height(48.dp)) {
            Text(label, color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleOverlay(
    panel: SimplePanel,
    service: SlicerService,
    presets: SlicerService.Presets,
    quick: SlicerService.QuickSettings,
    brim: String,
    top: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onNavigate: (SimplePanel) -> Unit,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
) {
    // Die Referenz benutzt ein kompaktes, an der Toolbar verankertes Panel,
    // kein vollbreites Smartphone-Sheet.  So bleibt der Druckraum sichtbar
    // und Material/Support-Auswahl fuehlen sich wie ein Teil des Slicers an.
    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.Background.copy(alpha = 0.72f))
            .padding(top = top),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = maxHeight),
            color = PrusaColors.Panel,
            shape = RoundedCornerShape(3.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                TextButton(onClick = onDismiss) { Text("← Zurück") }
                when (panel) {
                    SimplePanel.PROJECTS -> SimpleProjectsPanel(onPickFile, presets)
                    SimplePanel.PRINTER -> SimplePrinterPanel(service, presets, onDismiss, onOpenPrinterSetup)
                    SimplePanel.MATERIAL -> SimpleMaterialPanel(service, presets, onDismiss, onOpenAdvanced)
                    SimplePanel.SETTINGS -> SimpleSettingsPanel(onPanel = onNavigate)
                    SimplePanel.SUPPORTS -> SimpleSupportsPanel(service, quick)
                    SimplePanel.ADHESION -> SimpleAdhesionPanel(service, brim)
                    SimplePanel.PRINT_SETTINGS -> SimplePrintSettingsPanel(service, presets, onDismiss, onOpenAdvanced)
                    SimplePanel.WORKSPACE -> Unit
                }
            }
        }
    }
}

@Composable
private fun SimpleProjectsPanel(onPickFile: () -> Unit, presets: SlicerService.Presets) {
    var query by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("PROJECTS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPickFile) { Text("Modell öffnen") }
    }
    TextField(query, { query = it }, label = { Text("Search project names") }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), singleLine = true)
    SimpleProjectRow(
        title = "Aktuelles Projekt",
        printer = presets.selectedPrinter.ifBlank { "Drucker nicht gewählt" },
        material = presets.selectedFilament.ifBlank { "Material nicht gewählt" },
        detail = "Diese Sitzung",
    )
}

@Composable
private fun SimpleProjectRow(title: String, printer: String, material: String, detail: String) = Row(
    Modifier.fillMaxWidth().padding(top = 4.dp).background(PrusaColors.PanelRaised, RoundedCornerShape(2.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier.size(70.dp).background(PrusaColors.Background, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("▧", fontSize = 30.sp, color = PrusaColors.TextMuted) }
    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text("▤  $printer", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text("◎  $material", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text("□  $detail", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
    Text("⋮", color = PrusaColors.TextMuted, fontSize = 24.sp)
}

@Composable
private fun SimplePrinterPanel(service: SlicerService, presets: SlicerService.Presets, onDismiss: () -> Unit, onSetup: () -> Unit) {
    Text("PRINTER", style = MaterialTheme.typography.titleLarge)
    val models = remember(presets.printers) { EasyModeState.printerModelsWithNozzles(presets.printers) }
    if (models.isEmpty()) TextButton(onClick = onSetup) { Text("Drucker einrichten") }
    models.forEach { model ->
        Text(model.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.variants.forEach { choice ->
                val selected = choice.rawPreset == presets.selectedPrinter
                Column(
                    Modifier.width(190.dp)
                        .background(if (selected) PrusaColors.PanelRaised else PrusaColors.Background, RoundedCornerShape(2.dp))
                        .border(1.dp, if (selected) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(2.dp))
                        .clickable { service.selectPreset(PsmCore.PresetType.PRINTER, choice.rawPreset); onDismiss() }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("▤", color = PrusaColors.Orange, fontSize = 25.sp)
                    Text(choice.label, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text("Düse ${choice.label.substringAfterLast(" ", "intern")}", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(if (selected) "Ausgewählt" else "Antippen zum Auswählen", color = if (selected) PrusaColors.Orange else PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SimpleMaterialPanel(service: SlicerService, presets: SlicerService.Presets, onDismiss: () -> Unit, onOpenAdvanced: () -> Unit) {
    var selectedExtruder by rememberSaveable { mutableStateOf(0) }
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    if (chooserOpen) {
        SimpleMaterialChooser(
            presets = presets,
            selectedExtruder = selectedExtruder,
            onBack = { chooserOpen = false },
            onChoose = { filament ->
                if (presets.extruders.size > 1) service.setExtruderFilament(selectedExtruder, filament)
                else service.selectPreset(PsmCore.PresetType.FILAMENT, filament)
                chooserOpen = false
            },
            onOpenAdvanced = onOpenAdvanced,
        )
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("MATERIAL PALETTE", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            presets.extruders.forEach { service.setExtruderFilament(it.index, presets.selectedFilament) }
        }) { Text("Set all") }
    }
    Text("Kopf antippen, dann Material auswählen", color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    val heads = presets.extruders.ifEmpty { listOf(SlicerService.Extruder(0, presets.selectedFilament, "#808080")) }
    heads.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { extruder ->
                val active = selectedExtruder == extruder.index
                Column(
                    Modifier.weight(1f)
                        .background(if (active) PrusaColors.PanelRaised else PrusaColors.Panel, RoundedCornerShape(2.dp))
                        .border(1.dp, if (active) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(2.dp))
                        .clickable { selectedExtruder = extruder.index; chooserOpen = true }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(extruder.color.ifBlank { "#808080" })), RoundedCornerShape(7.dp)))
                        Text("  Kopf ${extruder.index + 1}", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(extruder.filament.ifBlank { "Material auswählen" }, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text("Farbe für Kopf ${selectedExtruder + 1}", color = PrusaColors.TextMuted, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#212121", "#F5F5F5").forEach { color ->
            Box(
                Modifier.size(36.dp).background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(18.dp))
                    .border(1.dp, PrusaColors.Divider, RoundedCornerShape(18.dp))
                    .clickable { service.setExtruderColor(selectedExtruder, color) },
            )
        }
    }
}

@Composable
private fun SimpleMaterialChooser(
    presets: SlicerService.Presets,
    selectedExtruder: Int,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←") }
        Text("CHOOSE MATERIAL", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text("Kopf ${selectedExtruder + 1}", color = PrusaColors.Orange, style = MaterialTheme.typography.labelLarge)
    }
    Text("FIND A SPOOL", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    TextField(query, { query = it }, label = { Text("search by vendor, material or color") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SimpleModeState.materialTypes().forEach { type -> OutlinedButton(onClick = { query = type }) { Text(type) } }
    }
    val matches = EasyModeState.filterPresets(presets.filaments, query).take(15)
    if (matches.isEmpty()) {
        TextButton(onClick = onOpenAdvanced) { Text("Filament einrichten") }
    } else {
        matches.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { filament ->
                    Column(
                        Modifier.weight(1f).height(112.dp)
                            .background(PrusaColors.PanelRaised, RoundedCornerShape(2.dp))
                            .clickable { onChoose(filament) }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(filament.substringBeforeLast(" ", filament), color = PrusaColors.TextPrimary, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        Text("━━━━", color = PrusaColors.Orange, fontSize = 18.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SimpleSettingsPanel(onPanel: (SimplePanel) -> Unit) {
    Text("EINSTELLEN", style = MaterialTheme.typography.titleLarge)
    listOf("Supports" to SimplePanel.SUPPORTS, "Haftung" to SimplePanel.ADHESION, "Print Settings" to SimplePanel.PRINT_SETTINGS).forEach { (label, panel) ->
        OutlinedButton(onClick = { onPanel(panel) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(label) }
    }
}

@Composable
private fun SimpleSupportsPanel(service: SlicerService, quick: SlicerService.QuickSettings) {
    fun choose(choice: SimpleSupportChoice) =
        SimpleModeState.supportConfig(choice).forEach { (key, value) -> service.setConfig(key, value) }
    val selected = SimpleModeState.selectedSupportChoice(
        quick.supports,
        quick.supportAuto,
        quick.supportBuildPlateOnly,
        quick.supportStyle,
    )
    Text("SUPPORTS", style = MaterialTheme.typography.titleLarge)
    SimpleReferenceChoice("Disabled", "No supports", selected == SimpleSupportChoice.DISABLED) { choose(SimpleSupportChoice.DISABLED) }
    Text("Everywhere", style = MaterialTheme.typography.titleMedium)
    SimpleReferenceChoice("Snug", "Straight supports close to the model.", selected == SimpleSupportChoice.SNUG_EVERYWHERE) { choose(SimpleSupportChoice.SNUG_EVERYWHERE) }
    SimpleReferenceChoice("Organic", "Tree-shaped supports, easy to remove.", selected == SimpleSupportChoice.ORGANIC_EVERYWHERE) { choose(SimpleSupportChoice.ORGANIC_EVERYWHERE) }
    Text("Build plate only", style = MaterialTheme.typography.titleMedium)
    SimpleReferenceChoice("Snug", "Build plate only", selected == SimpleSupportChoice.SNUG_BUILD_PLATE) { choose(SimpleSupportChoice.SNUG_BUILD_PLATE) }
    SimpleReferenceChoice("Organic", "Build plate only", selected == SimpleSupportChoice.ORGANIC_BUILD_PLATE) { choose(SimpleSupportChoice.ORGANIC_BUILD_PLATE) }
}

@Composable
private fun SimpleAdhesionPanel(service: SlicerService, brim: String) {
    Text("INCREASE ADHESION", style = MaterialTheme.typography.titleLarge)
    SimpleReferenceChoice("Disabled", "No additional bed adhesion", brim == "0") { service.setConfig("brim_width", "0") }
    SimpleReferenceChoice("Automatic", "Chooses an outline when needed", false) { service.setConfig("brim_width", "5") }
    SimpleReferenceChoice("Outline around the model", "A brim helps hold edges down while printing.", brim == "5") { service.setConfig("brim_width", "5") }
}

@Composable
private fun SimplePrintSettingsPanel(service: SlicerService, presets: SlicerService.Presets, onDismiss: () -> Unit, onOpenAdvanced: () -> Unit) {
    Text("PRINT SETTINGS", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SimpleModeState.printSettingsColumns().forEach { title ->
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = PrusaColors.PanelRaised), shape = RoundedCornerShape(2.dp)) { Text(title, modifier = Modifier.padding(10.dp)) }
        }
    }
    if (presets.prints.isEmpty()) TextButton(onClick = onOpenAdvanced) { Text("Print Settings einrichten") }
    presets.prints.take(10).forEach { profile ->
        OutlinedButton(onClick = { service.selectPreset(PsmCore.PresetType.PRINT, profile); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (profile == presets.selectedPrint) "✓ $profile" else profile) }
    }
}

@Composable
private fun SimpleChoice(label: String, selected: Boolean, onClick: () -> Unit) = OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (selected) "✓ $label" else label) }

@Composable
private fun SimpleReferenceChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(top = 10.dp)
        .background(if (selected) PrusaColors.PanelRaised else PrusaColors.Background, RoundedCornerShape(2.dp))
        .border(1.dp, if (selected) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(58.dp).background(PrusaColors.Panel, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
        Text(if (selected) "✓" else "▧", color = if (selected) PrusaColors.Orange else PrusaColors.TextMuted, fontSize = 25.sp)
    }
    Column(Modifier.weight(1f).padding(start = 12.dp)) {
        Text(title, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimpleDarkSystemBars(window: Window) {
    DisposableEffect(window) {
        window.statusBarColor = PrusaColors.Background.value.toInt()
        window.navigationBarColor = PrusaColors.Background.value.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        onDispose { }
    }
}
