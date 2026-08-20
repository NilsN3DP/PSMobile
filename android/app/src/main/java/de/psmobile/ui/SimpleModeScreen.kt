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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import de.psmobile.ui.theme.uiScaleFor
import androidx.compose.foundation.shape.CircleShape
import de.psmobile.shared.rules.FilamentCatalog
import de.psmobile.shared.ui.Corners
import de.psmobile.shared.rules.AdhesionAdvice
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.SliceSummary
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.SimplePanel
import de.psmobile.shared.rules.SimpleSupportChoice

private fun st(english: String, german: String): String = SimpleModeState.text(english, german)

/** Workspace-first Simple Mode, deliberately separate from Advanced Mode chrome. */
@Composable
fun SimpleModeScreen(
    service: SlicerService,
    window: Window,
    onPickFile: () -> Unit,
    onHome: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onAppSettings: () -> Unit,
    onStartSlice: () -> Unit,
    onSaveProject: () -> Unit,
    onRemoteSettings: () -> Unit = {},
    onShareGcode: () -> Unit = {},
    onShareAllGcode: () -> Unit = {},
    onControllerReady: (SceneController) -> Unit = {},
) {
    val remoteSlicePluginAn = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("psmobile", android.content.Context.MODE_PRIVATE)
        .getBoolean(de.psmobile.shared.rules.AppSettings.KEY_PLUGIN_REMOTE_SLICE, true)
    val presets by service.presets.collectAsState()
    val configRevision by service.configRevision.collectAsState()
    val progress by service.progress.collectAsState()
    val objects by service.objects.collectAsState()
    val beds by service.beds.collectAsState()
    val toolMessage by service.toolMessage.collectAsState()
    val quick by service.quickSettings.collectAsState()
    val sceneRevision by service.sceneRevision.collectAsState()
    val configuration = LocalConfiguration.current
    var panel by rememberSaveable { mutableStateOf(SimplePanel.WORKSPACE) }
    // Was fehlt, bevor geschnitten werden kann. Leer heisst: es kann
    // losgehen.
    var hinderungsgruende by remember { mutableStateOf(emptyList<String>()) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    // Welche Griffe am ausgewaehlten Objekt haengen.
    //
    // Der Viewport kennt die drei Gizmos seit langem (PsmViewport.Gizmo,
    // setGizmo/gizmoPick/gizmoDrag) - im Simple Mode waren sie nur nie
    // umschaltbar. Man konnte ein Objekt also ziehen, aber weder drehen
    // noch skalieren, waehrend iOS genau das anbietet.
    var gizmo by remember { mutableStateOf(PsmViewport.Gizmo.NONE) }
    // Die geschnittene Vorschau an Ort und Stelle, wie auf iOS.
    //
    // Der Knopf "Vorschau" in der Werkzeugleiste rief bisher dasselbe auf
    // wie "G-Code": onStartSlice. Er versprach also etwas anderes, als er
    // tat - eine Vorschau gab es im Simple Mode ueberhaupt nicht.
    var previewMode by remember { mutableStateOf(false) }
    var layerCount by remember { mutableStateOf(0) }
    var layerLo by remember { mutableStateOf(0) }
    var layerHi by remember { mutableStateOf(0) }
    // Ob nach dem naechsten fertigen Schnitt die Vorschau aufgehen soll.
    // Der Advanced Mode springt immer hinein; im Simple Mode nur, wenn man
    // wirklich die Vorschau wollte und nicht bloss G-Code.
    var nachDemSchnittZeigen by remember { mutableStateOf(false) }
    // "Auf Flaeche": der naechste Tipp im Viewport waehlt die
    // Flaeche, die nach unten soll. Ein Modus und kein Knopf, weil
    // die Auswahl im Viewport passiert und nicht in der Leiste.
    var flaechenwahl by remember { mutableStateOf(false) }
    var zeigeVerlassenNachfrage by remember { mutableStateOf(false) }
    // Dieselbe Nachfrage wie im Advanced Mode - ein zweites Tippen auf
    // einen Modus oder die Startseite verwirft sonst stillschweigend
    // das offene Projekt.
    val nachHauseGehen = {
        if (service.hasUnsavedChanges) zeigeVerlassenNachfrage = true else onHome()
    }
    val controller = remember { SceneController() }
    LaunchedEffect(controller) { onControllerReady(controller) }

    // Nach einem fertigen Schnitt in die Vorschau - aber nur, wenn sie
    // angefordert war.
    LaunchedEffect(progress) {
        if (progress is SlicerService.Progress.Done && nachDemSchnittZeigen) {
            nachDemSchnittZeigen = false
            // Wer die Vorschau angefordert hat, will sie sehen und nicht
            // das Ergebnisblatt davor. Beim Weg ueber "G-Code" bleibt das
            // Blatt wie gehabt stehen - dort ist es die Antwort.
            service.dismissProgress()
            controller.enterPreview { n ->
                layerCount = n
                layerLo = 0
                layerHi = (n - 1).coerceAtLeast(0)
                previewMode = n > 0
            }
        }
    }

    // Jede Aenderung am Bett macht das Ergebnis ungueltig, also zurueck in
    // den Editor. Bewusst getrennt vom Fortschritt: haengen beide an einem
    // LaunchedEffect, springt ein frisch geladenes Objekt sofort wieder in
    // die Vorschau des vorherigen Schnitts und ist dann unsichtbar.
    var gesehenerStand by remember { mutableStateOf(sceneRevision) }
    LaunchedEffect(sceneRevision) {
        if (sceneRevision != gesehenerStand) {
            gesehenerStand = sceneRevision
            if (previewMode) {
                controller.enterEditor()
                previewMode = false
                layerCount = 0
            }
        }
    }

    // Vorschau oeffnen - und nur rechnen, wenn es sein muss.
    val vorschauZeigen = {
        if (previewMode) {
            controller.enterEditor()
            previewMode = false
            layerCount = 0
        } else if (progress is SlicerService.Progress.Done) {
            // Das Ergebnis passt noch zur Szene, das Hinsehen kostet nichts.
            controller.enterPreview { n ->
                layerCount = n
                layerLo = 0
                layerHi = (n - 1).coerceAtLeast(0)
                previewMode = n > 0
            }
        } else {
            val gruende = SliceSummary.blockers(
                objects = objects.size,
                printer = presets.selectedPrinter,
                filament = presets.selectedFilament,
                print = presets.selectedPrint,
            )
            if (gruende.isEmpty()) {
                nachDemSchnittZeigen = true
                panel = SimplePanel.WORKSPACE
                onStartSlice()
            } else {
                hinderungsgruende = gruende
            }
        }
    }
    val placement = SimpleModeLayout.toolbarPlacement(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    )
    val compactChrome = placement == ToolbarPlacement.COMPACT_HORIZONTAL
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val workspaceActionBottom = navigationBottom
    // PSMobileTheme staucht die Dichte auf kleinen bzw. von Emulatoren als
    // klein gemeldeten Fenstern. Configuration bleibt dabei unskaliert;
    // fuer eine sichtbare Sheet-Hoehe brauchen wir daher die Compose-
    // Koordinaten, nicht die rohen Bildschirm-dp.
    val logicalHeightDp = configuration.screenHeightDp /
        uiScaleFor(configuration.screenWidthDp, configuration.screenHeightDp)
    // Das Referenzmenü beginnt direkt unter der Toolbar. Auf einem Tablet im
    // Querformat darf es nie unter die Systemnavigation laufen.
    val overlayTop = statusTop + if (compactChrome) 108.dp else 136.dp
    val overlayMaxHeight = if (compactChrome) {
        // Das Overlay endet oberhalb der Gestennavigation. Andernfalls
        // verdeckt sie bei breiten Emulatoren die letzte Einstellkarte.
        (logicalHeightDp - 108 - statusTop.value - navigationBottom.value)
            .toInt()
            .coerceAtLeast(220).dp
    } else {
        minOf(
            if (configuration.screenWidthDp >= 600) 720.dp else 600.dp,
            (logicalHeightDp - 136 - statusTop.value - navigationBottom.value).dp,
        )
    }

    LaunchedEffect(controller) { controller.onScaled = service::notifyViewportChanged }
    SimpleDarkSystemBars(window)
    // Die System-Zurück-Geste schließt erst das aktuelle Simple-Menü. Ohne
    // diesen Handler beendet sie den Slicer trotz offenem Overlay.
    BackHandler(enabled = panel != SimplePanel.WORKSPACE) {
        panel = SimpleModeState.backDestination(panel)
    }

    Box(Modifier.fillMaxSize().background(PrusaColors.Background)) {
        SimpleSceneWorkspace(
            service = service,
            sceneRevision = sceneRevision,
        selectedId = selectedId,
        onSelect = { selectedId = it.takeIf { id -> id >= 0 } },
        controller = controller,
        inputEnabled = panel == SimplePanel.WORKSPACE,
        onBlockedInput = {
            if (panel != SimplePanel.WORKSPACE) panel = SimplePanel.WORKSPACE
        },
        // Nur solange der Modus laeuft. Danach ist ein Tipp wieder ein
        // Tipp und dreht nicht versehentlich das Objekt.
        onSurfaceTap = if (flaechenwahl) { hit: de.psmobile.core.PsmViewport.SurfaceHit ->
            service.layOnFacet(hit)
            flaechenwahl = false
        } else null,
        modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            SimpleHeader(
                printer = SimpleModeState.printerLabel(presets.selectedPrinter),
                compact = compactChrome,
                onHome = nachHauseGehen,
                onAppSettings = onAppSettings,
                onOpenAdvanced = onOpenAdvanced,
            )
            var remoteSliceOn by remember { mutableStateOf(service.remoteSliceEnabled) }
            SimpleToolbar(
                placement = placement,
                widthDp = configuration.screenWidthDp,
                compact = compactChrome,
                selected = panel,
                // Immer bedienbar: ein Knopf, der nichts tut und nichts
                // sagt, ist die schlechtere Auskunft als einer, der den
                // Grund nennt.
                canPrint = true,
                showRemoteToggle = remoteSlicePluginAn,
                remoteSliceOn = remoteSliceOn,
                onToggleRemoteSlice = {
                    if (service.remoteSliceHost.isBlank()) {
                        onRemoteSettings()
                    } else {
                        service.remoteSliceEnabled = !service.remoteSliceEnabled
                        remoteSliceOn = service.remoteSliceEnabled
                    }
                },
                onPanel = { panel = if (panel == it) SimplePanel.WORKSPACE else it },
                onPreview = vorschauZeigen,
                previewAn = previewMode,
                onStartSlice = {
                    val gruende = SliceSummary.blockers(
                        objects = objects.size,
                        printer = presets.selectedPrinter,
                        filament = presets.selectedFilament,
                        print = presets.selectedPrint,
                    )
                    if (gruende.isEmpty()) {
                        panel = SimplePanel.WORKSPACE
                        onStartSlice()
                    } else {
                        hinderungsgruende = gruende
                    }
                },
            )
            BedSelector(
                beds = beds,
                onSelect = service::selectBed,
                onAdd = service::addBed,
                onRemove = service::removeBed,
                onToggleLock = service::toggleBedLock,
                onRename = service::renameBed,
                schmal = compactChrome,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        // Leiste am ausgewaehlten Objekt. Nur wenn kein Menue offen ist -
        // sonst schwebt sie ueber dem Overlay und lenkt vom eigentlichen
        // Dialog ab.
        val selectedObject = objects.firstOrNull { it.id == selectedId }
        if (selectedObject != null && panel == SimplePanel.WORKSPACE) {
            SimpleObjectBar(
                service = service,
                obj = selectedObject,
                beds = beds,
                onClearSelection = { selectedId = null },
                flaechenwahl = flaechenwahl,
                onFlaechenwahl = { flaechenwahl = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTop + if (compactChrome) 116.dp else 148.dp),
            )
        }
        SimpleUndoRedo(
            onUndo = service::undo,
            onRedo = service::redo,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = workspaceActionBottom + 12.dp),
        )
        // Werkzeuge am rechten Rand, wie auf iOS. Rechts und nicht unten,
        // weil unten schon Rueckgaengig/Wiederholen und das Modelle-Blatt
        // liegen.
        // Der Schichtregler gehoert zur Vorschau: ohne ihn sieht man nur
        // die aeusserste Huelle und nie, was innen passiert.
        if (previewMode && layerCount > 1) {
            LayerSlider(
                count = layerCount,
                low = layerLo,
                high = layerHi,
                onChange = { lo, hi ->
                    layerLo = lo; layerHi = hi
                    controller.setLayerRange(lo, hi)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 12.dp, bottom = 96.dp),
            )
        }
        if (panel == SimplePanel.WORKSPACE) {
            SimpleWerkzeugSpalte(
                gizmo = gizmo,
                // In der Vorschau gibt es keine Objekte zum Anfassen.
                zeigeGriffe = selectedId != null && !previewMode,
                onGizmo = { g ->
                    // Zweites Tippen auf dasselbe Werkzeug legt es wieder
                    // weg - sonst gibt es keinen Weg zurueck zum blossen
                    // Ziehen ohne die Auswahl aufzugeben.
                    val neu = if (gizmo == g) PsmViewport.Gizmo.NONE else g
                    gizmo = neu
                    controller.setGizmo(neu)
                },
                onAnsicht = { controller.resetView() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            )
        }
        if (objects.isEmpty()) {
            Button(
                onClick = onPickFile,
                shape = RoundedCornerShape(Corners.FIELD.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = workspaceActionBottom + 12.dp).heightIn(min = psTouch(56)),
            ) { Text("＋ " + st("Add model", "Modell hinzufügen")) }
        } else {
            // Sobald etwas auf dem Bett liegt, uebernimmt das Modelle-Blatt
            // sowohl das Hinzufuegen als auch Anordnen, Klonen und
            // Entfernen - so wie in EasyPrint.
            SimpleModelSheet(
                service = service,
                objects = objects,
                beds = beds,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onPickFile = onPickFile,
                bottomInset = workspaceActionBottom,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        // Das Overlay muss in derselben Box wie die SurfaceView liegen.
        // Als Geschwister ausserhalb dieser Box konnte die native 3D-View
        // Beruehrungen neben dem Menue abfangen; ein Tippen ausserhalb
        // schloss das Menue dann nicht.
        if (progress !is SlicerService.Progress.Idle &&
            progress !is SlicerService.Progress.Stale) {
            val gcodeDateien by service.gcodeDateien.collectAsState()
            SimpleSliceSheet(
                progress = progress,
                onCancel = service::cancelSlice,
                onClose = service::dismissProgress,
                onShare = onShareGcode,
                dateien = gcodeDateien.size,
                onShareAll = onShareAllGcode,
            )
        }
        if (hinderungsgruende.isNotEmpty()) {
            SimpleSliceBlockers(hinderungsgruende) { hinderungsgruende = emptyList() }
        }
        toolMessage?.let { message ->
            androidx.compose.material3.Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = PrusaColors.PanelRaised,
                shape = RoundedCornerShape(Corners.CARD.dp),
            ) {
                Text(message, color = PrusaColors.TextPrimary,
                    modifier = Modifier.padding(14.dp).clickable { service.clearToolMessage() })
            }
        }
        if (panel != SimplePanel.WORKSPACE) {
            SimpleOverlay(
                panel = panel,
                service = service,
                presets = presets,
                configRevision = configRevision,
                quick = quick,
                brim = quick.brim,
                objects = objects,
                top = overlayTop,
                maxHeight = overlayMaxHeight,
                compact = compactChrome,
                onDismiss = { panel = SimplePanel.WORKSPACE },
                onNavigate = { panel = it },
                onPickFile = onPickFile,
                onOpenAdvanced = onOpenAdvanced,
                onOpenPrinterSetup = onOpenPrinterSetup,
                onAppSettings = onAppSettings,
            )
        }
        if (zeigeVerlassenNachfrage) {
            AlertDialog(
                onDismissRequest = { zeigeVerlassenNachfrage = false },
                containerColor = PrusaColors.Panel,
                titleContentColor = PrusaColors.TextPrimary,
                textContentColor = PrusaColors.TextMuted,
                title = { Text(st("Unsaved changes", "Ungesicherte Änderungen")) },
                text = { Text(st("A second tap on a mode would discard this project.",
                    "Ein erneutes Tippen auf einen Modus würde dieses Projekt verwerfen.")) },
                confirmButton = {
                    TextButton(onClick = {
                        zeigeVerlassenNachfrage = false
                        onSaveProject()
                        onHome()
                    }) { Text(st("Save", "Sichern")) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { zeigeVerlassenNachfrage = false }) {
                            Text(st("Cancel", "Abbrechen"))
                        }
                        TextButton(onClick = {
                            zeigeVerlassenNachfrage = false
                            onHome()
                        }) { Text(st("Discard", "Verwerfen"), color = PrusaColors.Danger) }
                    }
                },
            )
        }
    }
}

@Composable
private fun SimpleSceneWorkspace(
    service: SlicerService,
    sceneRevision: Int,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    controller: SceneController,
    inputEnabled: Boolean,
    onBlockedInput: () -> Unit,
    onSurfaceTap: ((de.psmobile.core.PsmViewport.SurfaceHit) -> Unit)?,
    modifier: Modifier,
) = SceneView(
    core = service.coreOrNull,
    shaderDir = remember(service) { service.shaderDir() },
    selectedId = selectedId,
    onSelect = onSelect,
    invalidateKey = SimpleWorkspaceState.invalidateKey(sceneRevision),
    onSurfaceTap = onSurfaceTap,
    controller = controller,
    inputEnabled = inputEnabled,
    onBlockedInput = onBlockedInput,
    modifier = modifier,
)

@Composable
private fun SimpleHeader(
    printer: String,
    compact: Boolean,
    onHome: () -> Unit,
    onAppSettings: () -> Unit,
    onOpenAdvanced: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().background(PrusaColors.Background)
        .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 6.dp else 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Icon(
        Icons.Default.Home,
        contentDescription = st("Start page", "Startseite"),
        tint = PrusaColors.TextMuted,
        modifier = Modifier.size(if (compact) 28.dp else 34.dp).clickable(onClick = onHome),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 23.dp else 28.dp)
                .background(PrusaColors.Orange, RoundedCornerShape(Corners.FIELD.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("S", color = PrusaColors.Background, fontSize = if (compact) 13.sp else 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                SimpleModeState.visibleBrand(),
                color = PrusaColors.TextPrimary,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            )
        if (printer.isNotBlank()) Text(printer, color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    var menuOffen by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = st("More", "Mehr"),
            tint = PrusaColors.Orange,
            modifier = Modifier.size(if (compact) 28.dp else 34.dp)
                .clickable(onClick = { menuOffen = true }),
        )
        DropdownMenu(menuOffen, onDismissRequest = { menuOffen = false }) {
            ScaledOverlay {
            DropdownMenuItem(
                text = { Text(st("Start page", "Startseite")) },
                onClick = { menuOffen = false; onHome() },
            )
            DropdownMenuItem(
                text = { Text(st("App settings", "App-Einstellungen")) },
                onClick = { menuOffen = false; onAppSettings() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(st("Expert mode", "Expertenmodus")) },
                onClick = { menuOffen = false; onOpenAdvanced() },
            )
            }
        }
    }
}

@Composable
private fun SimpleToolbar(
    placement: ToolbarPlacement,
    widthDp: Int,
    compact: Boolean,
    selected: SimplePanel,
    canPrint: Boolean,
    showRemoteToggle: Boolean = false,
    remoteSliceOn: Boolean = false,
    onToggleRemoteSlice: () -> Unit = {},
    onPanel: (SimplePanel) -> Unit,
    onStartSlice: () -> Unit,
    onPreview: () -> Unit,
    previewAn: Boolean = false,
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
        SimpleToolbarButton(label = labels[4], selected = previewAn, width = buttonWidth, compact = compact, enabled = canPrint, onClick = onPreview)
        // Lokal/entfernt umschalten - siehe RemoteSliceScreen.kt fuer die
        // Einrichtung. Ohne eingerichteten Server fuehrt das Tippen erst
        // zur Einrichtung statt stumm auf einen leeren Host umzuschalten.
        if (showRemoteToggle) {
            Box(
                Modifier
                    .height(if (compact) 46.dp else 54.dp)
                    .width(if (compact) 40.dp else 46.dp)
                    .clip(RoundedCornerShape(Corners.FIELD.dp))
                    .background(if (remoteSliceOn) PrusaColors.Orange else PrusaColors.Panel)
                    .border(1.dp, if (remoteSliceOn) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
                    .clickable(onClick = onToggleRemoteSlice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (remoteSliceOn) Icons.Default.Cloud else Icons.Default.CloudQueue,
                    contentDescription = st("Remote Slicing", "Remote Slicing"),
                    tint = if (remoteSliceOn) PrusaColors.Background else PrusaColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
    shape = RoundedCornerShape(Corners.FIELD.dp),
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
    "Projects" -> "▣\n" + st("Projects", "Projekte")
    "Printer" -> "▤\n" + st("Printer", "Drucker")
    "Material" -> "◎\n" + st("Material", "Material")
    "Settings" -> "☷\n" + st("Settings", "Einstell.")
    "Preview" -> "▱\n" + st("Preview", "Vorschau")
    "G-Code" -> "➤  G-Code"
    else -> label
}

@Composable
private fun SimpleUndoRedo(onUndo: () -> Unit, onRedo: () -> Unit, modifier: Modifier) = Row(
    modifier.background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)),
) {
    TextButton(onClick = onUndo, modifier = Modifier.height(psTouch(52)).width(72.dp)) { Text("↶\n" + st("Undo", "Rückgängig")) }
    TextButton(onClick = onRedo, modifier = Modifier.height(psTouch(52)).width(72.dp)) { Text("↷\n" + st("Redo", "Wiederholen")) }
}

/**
 * Verschieben, Drehen, Skalieren und die Ansicht zuruecksetzen.
 *
 * Gegenstueck zu `werkzeugspalte` in SimpleModeView.swift. Die Griffe
 * erscheinen nur, wenn etwas ausgewaehlt ist - ohne Objekt haetten sie
 * nichts, woran sie haengen koennten. Das Zuruecksetzen der Ansicht steht
 * immer da, gerade wenn man sich verdreht hat.
 */
@Composable
private fun SimpleWerkzeugSpalte(
    gizmo: PsmViewport.Gizmo,
    zeigeGriffe: Boolean,
    onGizmo: (PsmViewport.Gizmo) -> Unit,
    onAnsicht: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier.background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    if (zeigeGriffe) {
        SimpleWerkzeugKnopf("↔", st("Move", "Verschieben"),
            aktiv = gizmo == PsmViewport.Gizmo.MOVE) { onGizmo(PsmViewport.Gizmo.MOVE) }
        SimpleWerkzeugKnopf("⟳", st("Rotate", "Drehen"),
            aktiv = gizmo == PsmViewport.Gizmo.ROTATE) { onGizmo(PsmViewport.Gizmo.ROTATE) }
        SimpleWerkzeugKnopf("⤢", st("Scale", "Skalieren"),
            aktiv = gizmo == PsmViewport.Gizmo.SCALE) { onGizmo(PsmViewport.Gizmo.SCALE) }
    }
    SimpleWerkzeugKnopf("⌂", st("View", "Ansicht"), aktiv = false, onClick = onAnsicht)
}

@Composable
private fun SimpleWerkzeugKnopf(
    zeichen: String,
    beschriftung: String,
    aktiv: Boolean,
    onClick: () -> Unit,
) = TextButton(
    onClick = onClick,
    modifier = Modifier.height(psTouch(52)).width(72.dp),
    colors = ButtonDefaults.textButtonColors(
        contentColor = if (aktiv) PrusaColors.Orange else PrusaColors.TextPrimary,
    ),
) {
    Text("$zeichen\n$beschriftung", fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleOverlay(
    panel: SimplePanel,
    service: SlicerService,
    presets: SlicerService.Presets,
    configRevision: Int,
    quick: SlicerService.QuickSettings,
    brim: String,
    objects: List<PsmCore.ObjectInfo>,
    top: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (SimplePanel) -> Unit,
    onPickFile: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onAppSettings: () -> Unit,
) {
    // Die Referenz benutzt ein kompaktes, an der Toolbar verankertes Panel,
    // kein vollbreites Smartphone-Sheet.  So bleibt der Druckraum sichtbar
    // und Material/Support-Auswahl fuehlen sich wie ein Teil des Slicers an.
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = top),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Der abgedunkelte Arbeitsbereich ist gleichzeitig die klare,
        // touchfreundliche Schliessen-Flaeche des Kontextmenues.
        Box(
            Modifier.fillMaxSize()
                .background(PrusaColors.Background.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(if (compact) 0.78f else 0.92f).then(
                if (panel == SimplePanel.SETTINGS) {
                    // Der Hub besteht aus zwei Kartenzeilen. Ohne eine
                    // definierte Containerhoehe misst verticalScroll nur
                    // die erste Zeile und schneidet die dritte Karte ab.
                    Modifier.height(minOf(560.dp, maxHeight))
                } else {
                    Modifier.heightIn(max = maxHeight)
                }
            ),
            color = PrusaColors.Panel,
            shape = RoundedCornerShape(Corners.SHEET.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                val onBack = {
                    val destination = SimpleModeState.backDestination(panel)
                    if (destination == SimplePanel.WORKSPACE) onDismiss() else onNavigate(destination)
                }
                TextButton(onClick = onBack) { Text("← " + st("Back", "Zurück")) }
                when (panel) {
                    SimplePanel.PROJECTS -> SimpleProjectsPanel(onPickFile, presets)
                    SimplePanel.PRINTER -> SimplePrinterPanel(service, presets, onDismiss, onOpenPrinterSetup)
                    SimplePanel.MATERIAL -> SimpleMaterialPanel(service, presets, onDismiss, onOpenAdvanced)
                    SimplePanel.SETTINGS -> SimpleSettingsPanel(
                        quick = quick,
                        brim = brim,
                        onPanel = onNavigate,
                        onOpenAdvanced = onOpenAdvanced,
                        onAppSettings = onAppSettings,
                    )
                    SimplePanel.SUPPORTS -> SimpleSupportsPanel(service, quick)
                    SimplePanel.ADHESION -> SimpleAdhesionPanel(service, brim, objects)
                    SimplePanel.PRINT_SETTINGS -> SimplePrintSettingsPanel(service, presets, configRevision, onDismiss, onOpenAdvanced)
                    SimplePanel.WORKSPACE -> Unit
                }
            }
        }
    }
}

@Composable
private fun SimpleProjectsPanel(onPickFile: () -> Unit, presets: SlicerService.Presets) {
    var query by rememberSaveable { mutableStateOf("") }
    val (projectTitle, noPrinter, session) = SimpleModeState.projectSummaryCopy()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(st("PROJECTS", "PROJEKTE"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPickFile) { Text(st("Open model", "Modell öffnen")) }
    }
    TextField(query, { query = it }, label = { Text(st("Search project names", "Projektnamen durchsuchen")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), singleLine = true)
    SimpleProjectRow(
        title = projectTitle,
        printer = presets.selectedPrinter.takeIf { it.isNotBlank() }?.let(EasyModeState::profileDisplayLabel)
            ?: noPrinter,
        material = presets.selectedFilament.takeIf { it.isNotBlank() }?.let(EasyModeState::profileDisplayLabel)
            ?: st("No material selected", "Material nicht gewählt"),
        detail = session,
    )
}

@Composable
private fun SimpleProjectRow(title: String, printer: String, material: String, detail: String) = Row(
    Modifier.fillMaxWidth().padding(top = 4.dp).background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.FIELD.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier.size(70.dp).background(PrusaColors.Background, RoundedCornerShape(Corners.FIELD.dp)),
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
    Text(st("PRINTER", "DRUCKER"), style = MaterialTheme.typography.titleLarge)
    Text(
        st("Choose a printer model · select the nozzle in the slice summary", "Druckermodell wählen · die Düse wird in der Slice-Übersicht festgelegt"),
        color = PrusaColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    )
    val models = remember(presets.printers) { EasyModeState.printerModelsWithNozzles(presets.printers) }
    if (models.isEmpty()) {
        EmptySimplePanel(st("No printer configured", "Noch kein Drucker eingerichtet"), st("Set up printer", "Drucker einrichten"), onSetup)
        return
    }
    models.flatMap { model -> model.variants.map { model to it } }.chunked(2).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { (model, choice) ->
                SimplePrinterCard(
                    model = model.label,
                    nozzle = choice.label,
                    selected = choice.rawPreset == presets.selectedPrinter,
                    onClick = {
                        service.selectPreset(PsmCore.PresetType.PRINTER, choice.rawPreset)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SimplePrinterCard(
    model: String,
    nozzle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .heightIn(min = 162.dp)
        .background(if (selected) PrusaColors.PanelRaised else PrusaColors.Background, RoundedCornerShape(Corners.FIELD.dp))
        .border(1.dp, if (selected) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SimplePrinterGlyph()
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(model, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                if (selected) st("SELECTED · OFFLINE", "AUSGEWÄHLT · OFFLINE") else "OFFLINE",
                color = if (selected) PrusaColors.Orange else PrusaColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    HorizontalDivider(color = PrusaColors.Divider)
    Text(st("Nozzle", "Düse") + "  $nozzle", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
    Text(st("Material is chosen in the next step", "Material wird im nächsten Schritt gewählt"), color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

@Composable
private fun SimplePrinterGlyph() = Box(
    Modifier.width(62.dp).height(72.dp)
        .background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp))
        .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp)),
    contentAlignment = Alignment.Center,
) {
    Box(
        Modifier.width(38.dp).height(psTouch(48))
            .background(PrusaColors.Background, RoundedCornerShape(Corners.FIELD.dp))
            .border(1.dp, PrusaColors.TextMuted.copy(alpha = 0.4f), RoundedCornerShape(Corners.FIELD.dp)),
    )
    Box(
        Modifier.align(Alignment.CenterEnd).width(3.dp).height(psTouch(42))
            .background(PrusaColors.Orange, RoundedCornerShape(Corners.FIELD.dp)),
    )
    Box(
        Modifier.align(Alignment.BottomCenter).width(28.dp).height(3.dp)
            .background(PrusaColors.TextMuted, RoundedCornerShape(Corners.FIELD.dp)),
    )
}

@Composable
private fun SimpleMaterialPanel(service: SlicerService, presets: SlicerService.Presets, onDismiss: () -> Unit, onOpenAdvanced: () -> Unit) {
    var selectedExtruder by rememberSaveable { mutableStateOf(0) }
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    if (chooserOpen) {
        SimpleMaterialChooser(
            katalog = service.filamentCatalog(),
            selectedExtruder = selectedExtruder,
            onBack = { chooserOpen = false },
            onChoose = { filament ->
                if (presets.extruders.size > 1) service.setExtruderFilament(selectedExtruder, filament)
                else service.selectPreset(PsmCore.PresetType.FILAMENT, filament)
                chooserOpen = false
            },
            onOpenAdvanced = onOpenAdvanced,
            incompatible = presets.incompatibleFilaments,
            showIncompatible = presets.showIncompatible,
            onShowIncompatible = service::setShowIncompatiblePresets,
        )
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(st("MATERIAL PALETTE", "MATERIALPALETTE"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            presets.extruders.forEach { service.setExtruderFilament(it.index, presets.selectedFilament) }
        }) { Text(st("Set all", "Alle setzen")) }
    }
    Text(st("Choose T1–T8, then choose a material", "T1–T8 antippen, dann Material auswählen"), color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    val heads = presets.extruders.ifEmpty { listOf(SlicerService.Extruder(0, presets.selectedFilament, "#808080")) }
    heads.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { extruder ->
                val active = selectedExtruder == extruder.index
                Column(
                    Modifier.weight(1f)
                        .background(if (active) PrusaColors.PanelRaised else PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp))
                        .border(1.dp, if (active) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
                        .clickable { selectedExtruder = extruder.index; chooserOpen = true }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(extruder.color.ifBlank { "#808080" })), RoundedCornerShape(Corners.FIELD.dp)))
                        Text("  T${extruder.index + 1}", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        extruder.filament.takeIf { it.isNotBlank() }?.let(EasyModeState::profileDisplayLabel)
                            ?: st("Choose material", "Material auswählen"),
                        color = PrusaColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text(st("Colour for", "Farbe für") + " T${selectedExtruder + 1}", color = PrusaColors.TextMuted, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#212121", "#F5F5F5").forEach { color ->
            Box(
                Modifier.size(36.dp).background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(Corners.SHEET.dp))
                    .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.SHEET.dp))
                    .clickable { service.setExtruderColor(selectedExtruder, color) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SimpleMaterialChooser(
    katalog: List<FilamentCatalog.Entry>,
    selectedExtruder: Int,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onOpenAdvanced: () -> Unit,
    incompatible: Set<String> = emptySet(),
    showIncompatible: Boolean? = null,
    onShowIncompatible: ((Boolean) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var typ by rememberSaveable { mutableStateOf("") }
    var farbe by rememberSaveable { mutableStateOf("") }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←") }
        Text(
            st("CHOOSE MATERIAL", "MATERIAL WÄHLEN"),
            color = PrusaColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            "T${selectedExtruder + 1}",
            color = PrusaColors.Orange,
            style = MaterialTheme.typography.labelLarge,
        )
    }

    /*
     * Die Auswahl, wie EasyPrint sie zeigt: Suchfeld ueber Anbieter,
     * Material und Farbe, darunter Typ-Knoepfe und Farbpunkte, und
     * darunter Karten mit einer echten Spule.
     *
     * Die Regeln dazu stehen im gemeinsamen Modul (FilamentCatalog) und
     * lagen dort seit langem ungenutzt - Android hatte bis hierher nur
     * ein Suchfeld und eine Liste. Eine Liste mit vierhundert Profilen
     * ist mit dem Finger nicht zu durchsuchen, und niemand kennt den
     * genauen Namen seines Profils: man weiss, welche Rolle im Schrank
     * liegt.
     */
    TextField(
        query,
        { query = it },
        label = {
            Text(st(
                FilamentCatalog.searchHint().english,
                FilamentCatalog.searchHint().german,
            ))
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        singleLine = true,
    )

    // Die gaengigen Typen zuerst - wer PLA sucht, soll nicht an ABS
    // vorbei. Ein zweiter Tipp auf denselben Knopf hebt den Filter auf.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 5,
    ) {
        FilamentCatalog.types(katalog).forEach { name ->
            val aktiv = typ == name
            Box(
                Modifier
                    .height(psTouch(44))
                    .clip(RoundedCornerShape(Corners.PILL.dp))
                    .background(if (aktiv) PrusaColors.Orange else PrusaColors.PanelRaised)
                    .clickable { typ = if (aktiv) "" else name }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    color = if (aktiv) PrusaColors.Background else PrusaColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    // Die haeufigsten Farben im Bestand, nicht ein fester Farbkreis: die
    // Punkte sollen zeigen, was wirklich da ist.
    val farben = FilamentCatalog.colors(katalog)
    if (farben.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            farben.forEach { hex ->
                val aktiv = farbe == hex
                Box(
                    Modifier
                        .size(psTouch(44))
                        .clickable { farbe = if (aktiv) "" else hex },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (aktiv) 30.dp else 26.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex) ?: PrusaColors.PanelRaised)
                            .border(
                                if (aktiv) 3.dp else 1.dp,
                                if (aktiv) PrusaColors.Orange else PrusaColors.Divider,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }

    if (showIncompatible != null && onShowIncompatible != null) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .heightIn(min = psTouch(44))
                .clickable { onShowIncompatible(!showIncompatible) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showIncompatible) "☑" else "☐",
                color = if (showIncompatible) PrusaColors.Orange else PrusaColors.TextMuted,
                fontSize = 17.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                st(
                    "Also show materials for other printers",
                    "Auch Materialien für andere Drucker zeigen",
                ),
                color = PrusaColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val treffer = FilamentCatalog.filter(katalog, query, typ, farbe)
    if (treffer.isEmpty()) {
        LeeresPanel(
            nachricht = st(
                FilamentCatalog.emptyMessage().english,
                FilamentCatalog.emptyMessage().german,
            ),
            aktion = st("Set up filament", "Filament einrichten"),
            onAktion = onOpenAdvanced,
        )
    } else {
        treffer.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { eintrag ->
                    val passt = eintrag.rawPreset !in incompatible
                    Column(
                        Modifier.weight(1f)
                            .background(
                                PrusaColors.PanelRaised,
                                RoundedCornerShape(Corners.CARD.dp),
                            )
                            .then(
                                if (passt) Modifier
                                else Modifier.border(
                                    1.dp, PrusaColors.Danger,
                                    RoundedCornerShape(Corners.CARD.dp),
                                )
                            )
                            .clickable { onChoose(eintrag.rawPreset) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            eintrag.vendor,
                            color = PrusaColors.TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Text(
                            eintrag.type.ifBlank { " " },
                            color = PrusaColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        Spule(eintrag.colorHex)
                        Text(
                            eintrag.rawPreset,
                            color = PrusaColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        if (!passt) {
                            Text(
                                st("other printer", "anderer Drucker"),
                                color = PrusaColors.Danger,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Eine Rolle statt eines Farbklecks.
 *
 * In EasyPrint steht auf jeder Karte ein Foto der Spule. Ein Foto je
 * Filament haetten wir nicht, aber die Form allein traegt schon die
 * Auskunft: das hier ist eine Rolle in dieser Farbe - von vorn gesehen
 * wie eine echte Spule, nicht nur ein gefaerbter Kreis. Aeusserer
 * Flansch, aufgewickeltes Filament als Ring, Nabe und Kernloch.
 */
@Composable
private fun Spule(hex: String) {
    val farbe = parseColor(hex) ?: PrusaColors.PanelRaised
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, CircleShape),
        )
        Box(Modifier.size(34.dp).clip(CircleShape).background(farbe))
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(PrusaColors.Background)
                .border(1.dp, PrusaColors.Divider, CircleShape),
        )
        Box(Modifier.size(7.dp).clip(CircleShape).background(PrusaColors.Panel))
    }
}

/**
 * Eine Aussage plus der Knopf, der aus der Lage herausfuehrt.
 *
 * Vorher stand an diesen Stellen eine leere Flaeche oder ein nackter
 * Textknopf. Wer dort landet, hat ein Problem und keinen Weg heraus -
 * iOS hat dafuer seit langem ein eigenes Muster (leeresPanel).
 */
@Composable
internal fun LeeresPanel(nachricht: String, aktion: String, onAktion: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.CARD.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            nachricht,
            color = PrusaColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .height(psTouch(44))
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
                .clickable(onClick = onAktion)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(aktion, color = PrusaColors.Orange, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SimpleSettingsPanel(
    quick: SlicerService.QuickSettings,
    brim: String,
    onPanel: (SimplePanel) -> Unit,
    onOpenAdvanced: () -> Unit,
    onAppSettings: () -> Unit,
) {
    Text(st("SETTINGS", "EINSTELLEN"), style = MaterialTheme.typography.titleLarge)
    Text(
        st("The most important choices for this print.", "Die wichtigsten Entscheidungen für diesen Druck."),
        color = PrusaColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    )
    val supportsOn = quick.supports == "1"
    val cards = listOf(
        SimpleSettingCardData(
            st("Supports", "Stützen"),
            if (supportsOn) {
                "${if (quick.supportBuildPlateOnly == "1") st("Build plate only", "Nur Druckbett") else st("Everywhere", "Überall")} · ${quick.supportStyle.ifBlank { "Snug" }}"
            } else {
                st("No supports", "Keine Stützen")
            },
            "⌂",
            supportsOn,
            SimplePanel.SUPPORTS,
        ),
        SimpleSettingCardData(
            st("Adhesion", "Haftung"),
            if (brim == "0") st("No additional bed adhesion", "Keine zusätzliche Haftung") else st("Outline around the model", "Rand um das Modell"),
            "▱",
            brim != "0",
            SimplePanel.ADHESION,
        ),
        SimpleSettingCardData(
            "Print Settings",
            st("Quality, infill and shell thickness", "Qualität, Infill und Wandstärke"),
            "☷",
            true,
            SimplePanel.PRINT_SETTINGS,
        ),
    )
    cards.chunked(2).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { card ->
                SimpleSettingsCard(card, { onPanel(card.panel) }, Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    TextButton(
        onClick = onOpenAdvanced,
        modifier = Modifier.fillMaxWidth().heightIn(min = psTouch(48)),
    ) { Text(st("Open Advanced Mode", "Advanced Mode öffnen"), color = PrusaColors.TextMuted) }
    // Programm statt Werkstueck: Sprache, Startmodus, Vorschau. Steht hier,
    // weil man die Startseite nicht mehr sieht, wenn der Modus fest ist.
    TextButton(
        onClick = onAppSettings,
        modifier = Modifier.fillMaxWidth().heightIn(min = psTouch(48)),
    ) { Text(st("App settings", "App-Einstellungen"), color = PrusaColors.TextMuted) }
}

private data class SimpleSettingCardData(
    val title: String,
    val detail: String,
    val icon: String,
    val enabled: Boolean,
    val panel: SimplePanel,
)

@Composable
private fun SimpleSettingsCard(
    card: SimpleSettingCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .heightIn(min = 122.dp)
        .background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.FIELD.dp))
        .border(1.dp, if (card.enabled) PrusaColors.Orange.copy(alpha = 0.65f) else PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
        .clickable(onClick = onClick)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(card.icon, color = if (card.enabled) PrusaColors.Orange else PrusaColors.TextMuted, fontSize = 25.sp) }
        Spacer(Modifier.width(10.dp))
        Text(card.title, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
    Text(card.detail, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    Text(st("Open", "Öffnen") + "  ›", color = PrusaColors.Orange, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun EmptySimplePanel(message: String, action: String, onClick: () -> Unit) = Column(
    Modifier.fillMaxWidth().background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.FIELD.dp)).padding(18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(message, color = PrusaColors.TextMuted)
    OutlinedButton(onClick = onClick) { Text(action) }
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
    Text(st("SUPPORTS", "STÜTZEN"), style = MaterialTheme.typography.titleLarge)
    SimpleReferenceChoice(st("Disabled", "Aus"), st("No supports", "Keine Stützen"), selected == SimpleSupportChoice.DISABLED) { choose(SimpleSupportChoice.DISABLED) }
    Text(st("Everywhere", "Überall"), style = MaterialTheme.typography.titleMedium)
    SimpleReferenceChoice(st("Snug", "Anliegend"), st("Straight supports close to the model.", "Gerade Stützen dicht am Modell."), selected == SimpleSupportChoice.SNUG_EVERYWHERE) { choose(SimpleSupportChoice.SNUG_EVERYWHERE) }
    SimpleReferenceChoice(st("Organic", "Organisch"), st("Tree-shaped supports, easy to remove.", "Baumförmige Stützen, leicht zu entfernen."), selected == SimpleSupportChoice.ORGANIC_EVERYWHERE) { choose(SimpleSupportChoice.ORGANIC_EVERYWHERE) }
    Text(st("Build plate only", "Nur vom Druckbett"), style = MaterialTheme.typography.titleMedium)
    SimpleReferenceChoice(st("Snug", "Anliegend"), st("Build plate only", "Nur vom Druckbett"), selected == SimpleSupportChoice.SNUG_BUILD_PLATE) { choose(SimpleSupportChoice.SNUG_BUILD_PLATE) }
    SimpleReferenceChoice(st("Organic", "Organisch"), st("Build plate only", "Nur vom Druckbett"), selected == SimpleSupportChoice.ORGANIC_BUILD_PLATE) { choose(SimpleSupportChoice.ORGANIC_BUILD_PLATE) }
}

@Composable
private fun SimpleAdhesionPanel(
    service: SlicerService,
    brim: String,
    objects: List<PsmCore.ObjectInfo>,
) {
    // "Automatisch" ist bewusst kein dritter Zustand, sondern eine
    // Entscheidungshilfe: sie beurteilt die Objekte auf dem Bett und setzt
    // danach eine der beiden echten Einstellungen. Welche das war, sieht
    // man unmittelbar an der Markierung darueber oder darunter.
    val advice = AdhesionAdvice.advise(
        objects.map {
            AdhesionAdvice.Footprint(it.sizeMm.first, it.sizeMm.second, it.sizeMm.third)
        }
    )
    Text(
        st("INCREASE ADHESION", "HAFTUNG VERBESSERN"),
        style = MaterialTheme.typography.titleLarge,
    )
    SimpleReferenceChoice(
        st("Disabled", "Aus"),
        st("No additional bed adhesion", "Keine zusätzliche Haftung"),
        brim == "0",
    ) { service.setConfig("brim_width", "0") }
    SimpleReferenceChoice(
        st("Decide automatically", "Automatisch entscheiden"),
        AdhesionAdvice.explain(advice),
        selected = false,
    ) { service.setConfig("brim_width", advice.brimWidthMm.toString()) }
    SimpleReferenceChoice(
        st("Outline around the model", "Rand um das Modell"),
        st(
            "A brim helps hold edges down while printing.",
            "Ein Rand hält die Kanten während des Drucks unten.",
        ),
        brim != "0",
    ) { service.setConfig("brim_width", AdhesionAdvice.SUGGESTED_BRIM_MM.toString()) }
}

/**
 * Welche Schluessel unter welcher Spaltenueberschrift stehen - dieselbe
 * Zuordnung wie iOS' `druckprofilPanel` (SimpleModeView.swift), nur
 * ohne die schmale-Bildschirm-Fallunterscheidung, da Android bisher
 * nur ein Layout fuer dieses Panel hat.
 */
private val PRINT_SETTINGS_QUICK_KEYS = mapOf(
    "Print Settings" to listOf("layer_height"),
    "Infill" to listOf("fill_density", "fill_pattern"),
    "Shell Thickness" to listOf("perimeters", "top_solid_layers", "bottom_solid_layers"),
)

@Composable
private fun SimplePrintSettingsPanel(service: SlicerService, presets: SlicerService.Presets, configRevision: Int, onDismiss: () -> Unit, onOpenAdvanced: () -> Unit) {
    Text(st("PRINT SETTINGS", "DRUCKEINSTELLUNGEN"), style = MaterialTheme.typography.titleLarge)
    val core = service.coreOrNull
    if (core != null) {
        SimpleModeState.printSettingsColumns().forEach { title ->
            Text(
                st(title, printSettingsColumnGerman(title)).uppercase(),
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            PRINT_SETTINGS_QUICK_KEYS[title].orEmpty().forEach { key ->
                val meta = remember(key, configRevision) { core.configMeta(key) }
                if (meta != null) {
                    SettingRow(core, meta, configRevision, multiline = false, onChanged = {})
                }
            }
        }
    }
    Text(st("Profiles", "Profile").uppercase(), color = PrusaColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    if (presets.prints.isEmpty()) TextButton(onClick = onOpenAdvanced) { Text(st("Set up print settings", "Druckeinstellungen einrichten")) }
    presets.prints.take(10).forEach { profile ->
        OutlinedButton(onClick = { service.selectPreset(PsmCore.PresetType.PRINT, profile); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (profile == presets.selectedPrint) "✓ $profile" else profile) }
    }
}

/**
 * Die drei Spaltenkoepfe der Druckeinstellungen kommen aus der
 * Referenzliste in [SimpleModeState] und haben dort bewusst englische
 * Namen. Die Uebersetzung gehoert deshalb hierher, nicht in die Liste.
 */
private fun printSettingsColumnGerman(english: String): String = when (english) {
    "Print Settings" -> "Druckeinstellungen"
    "Infill" -> "Füllung"
    "Shell Thickness" -> "Wandstärke"
    else -> english
}

@Composable
private fun SimpleChoice(label: String, selected: Boolean, onClick: () -> Unit) = OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (selected) "✓ $label" else label) }

@Composable
private fun SimpleReferenceChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(top = 10.dp)
        .background(if (selected) PrusaColors.PanelRaised else PrusaColors.Background, RoundedCornerShape(Corners.FIELD.dp))
        .border(1.dp, if (selected) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(58.dp).background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)), contentAlignment = Alignment.Center) {
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
