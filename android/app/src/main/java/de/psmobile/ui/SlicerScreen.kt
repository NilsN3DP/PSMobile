package de.psmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import de.psmobile.core.PsmViewport
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.uiScaleFor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Oberflaeche im PrusaSlicer-Zuschnitt, aber fuer Touch gebaut.
 *
 * Aufteilung wie am Desktop:
 *   links   schmale Werkzeugleiste
 *   mitte   Bett, vollflaechig
 *   rechts  Seitenleiste mit Profilen, Objekten, Einstellungen, Slicen
 *
 * Unterschiede zum Desktop, bewusst:
 *   - Zielgeraet ist das Tablet im Querformat. Auf schmalen Geraeten
 *     klappt die Seitenleiste weg statt zu schrumpfen.
 *   - Alle Ziele mindestens 48 dp. Am Desktop sind die Werkzeugsymbole
 *     32 px und die Kombifelder 22 px hoch - mit dem Finger unbedienbar.
 *   - Statt der dreispaltigen Parametertabelle nur die fuenf Regler, die
 *     den Alltag abdecken. Die vollstaendige Liste kommt spaeter als
 *     eigener Bildschirm, generiert aus PrintConfig.
 */

private val SIDEBAR_WIDTH = 380.dp
private val TOOL_SIZE = 64.dp
private val TOOL_RAIL_WIDTH = 80.dp
private val TOUCH_TARGET = 48.dp

private fun advancedText(english: String, german: String): String = PsUi.appText(english, german)

private enum class InspectorSection {
    PROFILES,
    OBJECTS,
    TRANSFORM,
    TOOLS,
}

private data class PendingPresetSwitch(
    val type: PsmCore.PresetType,
    val target: String,
    val changes: List<PsmCore.Change>,
)

@Composable
fun SlicerScreen(
    service: SlicerService?,
    onOpenSimple: () -> Unit,
    onPickFile: (List<android.net.Uri>) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onPickBackupFolder: () -> Unit,
    onNewProject: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: () -> Unit,
    canReloadProject: Boolean,
    onReloadProject: () -> Unit,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
    onAddSvg: (Int, Float, PsmCore.VolumeType) -> Unit,
) {
    if (service == null) {
        Box(
            Modifier.fillMaxSize().background(PrusaColors.Background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = PrusaColors.Orange) }
        return
    }
    SlicerContent(
        service = service,
        onOpenSimple = onOpenSimple,
        onPickFile = onPickFile,
        onShare = onShare,
        onPickBackupFolder = onPickBackupFolder,
        onNewProject = onNewProject,
        onSaveProject = onSaveProject,
        onSaveProjectAs = onSaveProjectAs,
        canReloadProject = canReloadProject,
        onReloadProject = onReloadProject,
        onExportPlate = onExportPlate,
        onRepairStl = onRepairStl,
        onConvertGcode = onConvertGcode,
        onAddSvg = onAddSvg,
    )
}

@Composable
private fun SlicerContent(
    service: SlicerService,
    onOpenSimple: () -> Unit,
    onPickFile: (List<android.net.Uri>) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onPickBackupFolder: () -> Unit,
    onNewProject: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: () -> Unit,
    canReloadProject: Boolean,
    onReloadProject: () -> Unit,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
    onAddSvg: (Int, Float, PsmCore.VolumeType) -> Unit,
) {
    // Mehrere Dateien auf einmal: eine Baugruppe besteht selten aus
    // genau einem Teil, und der Umweg ueber sechs einzelne Auswahlen
    // ist auf einem Tablet besonders muehsam.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPickFile(uris) }

    val objects by service.objects.collectAsState()
    val beds by service.beds.collectAsState()
    val lockedBeds by service.lockedBeds.collectAsState()
    val progress by service.progress.collectAsState()
    val presets by service.presets.collectAsState()
    val colorMix by service.colorMix.collectAsState()
    val history by service.history.collectAsState()
    val volumes by service.volumes.collectAsState()
    val sceneRevision by service.sceneRevision.collectAsState()
    val configRevision by service.configRevision.collectAsState()
    val toolMessage by service.toolMessage.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Android hat hier keine sinnvolle System-Zwischenablage fuer
    // 3D-Objekte. Innerhalb des offenen Projekts merken wir deshalb die
    // Quell-ID und erzeugen beim Einfuegen eine echte Modellkopie.
    var copiedObjectId by remember { mutableStateOf<Int?>(null) }
    val sceneController = remember { SceneController() }
    // Nach einem Zug am Griff oder einer Spreizgeste aendert der Viewport
    // das Modell direkt. Ohne diese Rueckmeldung zeigten Objektliste und
    // Zahlenfelder weiter die alten Werte.
    LaunchedEffect(sceneController) {
        sceneController.onScaled = { service.notifyViewportChanged() }
    }
    // Navigation liegt im Service, damit ein eingehendes Modell die
    // Ansicht aufs Bett zurueckholen kann - Befund A1.
    val screen by service.screen.collectAsState()
    val settingsTab = (screen as? SlicerService.Screen.Settings)?.tab
    var settingsMode by remember { mutableStateOf(PsmCore.Mode.SIMPLE) }
    val showPrinters = screen is SlicerService.Screen.Printers
    val showWizard = screen is SlicerService.Screen.Wizard
    val showColorMix = screen is SlicerService.Screen.ColorMix
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sendState by service.sendState.collectAsState()
    var linkPrinters by remember { mutableStateOf(de.psmobile.net.PrinterStore.all(ctx)) }
    var confirmNewProject by remember { mutableStateOf(false) }
    var confirmReloadProject by remember { mutableStateOf(false) }

    if (showWizard) {
        AdvancedWizardScreen(service = service, onClose = service::showBed)
        return
    }

    if (showColorMix) {
        ColorMixScreen(service = service, onClose = service::showBed)
        return
    }

    if (showPrinters) {
        PrintersScreen(
            presetNames = presets.printers,
            onClose = {
                service.refreshPresets()
                service.showBed()
                linkPrinters = de.psmobile.net.PrinterStore.all(ctx)
            },
            onPickBackupFolder = onPickBackupFolder,
            onReopenSetup = { service.reopenSetup() },
        )
        return
    }

    // Vollbild-Einstellungen wie die Tabs im Desktop-Fenster.
    settingsTab?.let { tab ->
        service.coreOrNull?.let { core ->
            SettingsScreen(
                core = core,
                tab = tab,
                mode = settingsMode,
                onModeChange = { settingsMode = it },
                configRevision = configRevision,
                onClose = { service.showBed(); service.refreshQuickSettings() },
                onSettingChanged = { service.notifyConfigChanged() },
                onTabChange = { service.showScreen(SlicerService.Screen.Settings(it)) },
            )
            return
        }
    }

    val selected = objects.firstOrNull { it.id == selectedId }
    // Die Höhe ist Objekt-spezifisch. Nicht cachen: Der Layer-Dialog kann
    // das Profil bei unverändertem Objekt-ID direkt ändern. Die Anzeige im
    // Arbeitsbereich muss unmittelbar nach „Übernehmen“ den echten Kernwert
    // lesen, nicht bis zu einer zufälligen nächsten Szenenänderung warten.
    val selectedLayerProfile = selected?.let { service.layerProfile(it.id) }.orEmpty()
    val activeBed = beds.firstOrNull { it.active }?.index ?: 0
    val extruderOptions = buildList {
        repeat(presets.extruders.size.coerceAtLeast(1)) { index ->
            add(ExtruderChoice(index + 1, "Extruder ${index + 1}"))
        }
        colorMix.recipes.forEach { recipe ->
            add(ExtruderChoice(recipe.id, "ColorMix ${recipe.id}"))
        }
    }

    LaunchedEffect(activeBed) {
        selectedId = null
        selectedIds = emptySet()
    }

    LaunchedEffect(objects.map { it.id }) {
        val reconciled = SelectionModel(
            LinkedHashSet(selectedIds),
            selectedId,
        ).reconcile(objects.map { it.id })
        selectedIds = reconciled.ids
        selectedId = reconciled.primaryId
    }

    // Zustand der G-Code-Vorschau. Der Viewport haelt die Werkzeugwege, hier
    // steht nur, was die Bedienelemente davon zeigen muessen.
    // Solange das Skalieren-Werkzeug an ist, greift die Spreizgeste das
    // Objekt statt der Kamera.
    var scaleTool by remember { mutableStateOf(false) }
    // Welche Griffe am Objekt stehen.
    var gizmo by remember { mutableStateOf(de.psmobile.core.PsmViewport.Gizmo.NONE) }
    var previewMode by remember { mutableStateOf(false) }
    var layerCount by remember { mutableStateOf(0) }
    var layerLo by remember { mutableStateOf(0) }
    var layerHi by remember { mutableStateOf(0) }
    var surfaceMode by remember { mutableStateOf<SurfaceToolMode?>(null) }
    var measureStart by remember { mutableStateOf<PsmViewport.SurfaceHit?>(null) }
    var measureText by remember { mutableStateOf<String?>(null) }

    // PrusaSlicer springt nach dem Slicen von selbst in die Vorschau.
    LaunchedEffect(progress) {
        if (progress is SlicerService.Progress.Done) {
            sceneController.enterPreview { n ->
                layerCount = n
                layerLo = 0
                layerHi = (n - 1).coerceAtLeast(0)
                previewMode = n > 0
            }
        } else if (previewMode) {
            sceneController.enterEditor()
            previewMode = false
            layerCount = 0
        }
    }

    // Jede Aenderung am Bett macht das Slice-Ergebnis ungueltig, also
    // zurueck in den Editor.
    //
    // Das muss getrennt vom Fortschritt laufen: hingen beide an einem
    // LaunchedEffect, sprang ein neu geladenes Objekt sofort wieder in die
    // Vorschau des vorherigen Slices - das Objekt war dann unsichtbar.
    var seenRevision by remember { mutableStateOf(sceneRevision) }
    LaunchedEffect(sceneRevision) {
        if (sceneRevision != seenRevision) {
            seenRevision = sceneRevision
            if (previewMode) {
                sceneController.enterEditor()
                previewMode = false
                layerCount = 0
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Ab 1000 dp bleibt der Inspektor dauerhaft sichtbar. Auf kleineren
        // Tablets und im Hochformat liegt er als einblendbares Panel ueber
        // dem Bett, statt die eigentliche Arbeitsflaeche zu zerquetschen.
        val permanentInspector = maxWidth >= 1000.dp
        // Unterhalb dieser Breite passen Projektaktionen, Bettwaehler,
        // Simple und Panel nicht mehr nebeneinander. Statt den Bettwaehler
        // stumm abzuschneiden, ruecken die Abstaende enger zusammen.
        val tightChrome = maxWidth < 700.dp
        // Die Breite war fest, ohne Bezug zum Fenster. Auf einem rund
        // 535 dp breiten Fenster belegte das Panel damit 380 dp - fast
        // drei Viertel - und legte sich ueber die Ansichtsleiste am
        // unteren Rand. Deshalb zusaetzlich eine Obergrenze relativ zum
        // verfuegbaren Platz.
        val usable = maxWidth - TOOL_RAIL_WIDTH
        val inspectorWidth = when {
            // Sehr schmal: als volles Blatt neben der Werkzeugleiste.
            maxWidth < 460.dp -> usable
            else -> minOf(
                if (maxWidth >= 1280.dp) 400.dp else SIDEBAR_WIDTH,
                // Mehr als gut die Haelfte darf der Inspektor nie
                // beanspruchen, sonst bleibt vom Bett nichts uebrig.
                usable * 0.55f,
            )
        }
        var inspectorOpen by remember(permanentInspector) {
            mutableStateOf(permanentInspector)
        }

        Row(Modifier.fillMaxSize()) {
            ToolStrip(
                hasSelection = selectedIds.isNotEmpty(),
                // Die Quelle darf auf einem anderen Bett liegen. Der Core
                // kennt alle Betten, waehrend `objects` nur das aktive zeigt.
                canPaste = copiedObjectId != null,
                canUndo = history.canUndo &&
                    progress !is SlicerService.Progress.Running,
                canRedo = history.canRedo &&
                    progress !is SlicerService.Progress.Running,
            ) { name ->
                // Zuordnung nach den Werkzeugnamen aus GLCanvas3D.cpp
                when (name) {
                    "add"       -> picker.launch(arrayOf("*/*"))
                    "undo"      -> service.undo()
                    "redo"      -> service.redo()
                    "delete"    -> {
                        service.removeObjects(selectedIds)
                        selectedIds = emptySet()
                        selectedId = null
                    }
                    "deleteall" -> service.clearBed()
                    // Die mobile Arbeitsfläche zeigt immer genau das aktive
                    // Bett. "Aktuelle Platte anordnen" ist daher keine
                    // zweite, eingeschränkte Operation, sondern dieselbe
                    // getestete Arrange-Funktion auf diesem Bett.
                    "arrange", "arrangecurrent" -> service.arrange()
                    "copy"      -> selected?.let { copiedObjectId = it.id }
                    "paste"     -> copiedObjectId?.let {
                        if (!service.duplicate(it, activeBed))
                            copiedObjectId = null
                    }
                    "more"      -> selected?.let {
                        service.setInstances(it.id, it.instances + 1)
                    }
                    "fewer"     -> selected?.let {
                        service.setInstances(it.id, (it.instances - 1).coerceAtLeast(1))
                    }
                    "splitobjects" -> selected?.let { service.splitIntoObjects(it.id) }
                    "splitvolumes" -> selected?.let { service.splitIntoVolumes(it.id) }
                    "settings" -> service.showScreen(SlicerService.Screen.Settings("print"))
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                WorkspaceBar(
                    beds = beds,
                    onSelectBed = service::selectBed,
                    onAddBed = service::addBed,
                    onRemoveBed = service::removeBed,
                    lockedBeds = lockedBeds,
                    onToggleBedLock = service::toggleBedLock,
                    onNew = {
                        if (objects.isNotEmpty() || beds.size > 1)
                            confirmNewProject = true
                        else
                            onNewProject()
                    },
                    onSave = onSaveProject,
                    onSaveAs = onSaveProjectAs,
                    onReload = if (canReloadProject) {
                        { confirmReloadProject = true }
                    } else null,
                    onOpenSimple = onOpenSimple,
                    actionsEnabled = progress !is SlicerService.Progress.Running,
                    showInspectorAction = !permanentInspector,
                    inspectorOpen = inspectorOpen,
                    onToggleInspector = { inspectorOpen = !inspectorOpen },
                    tight = tightChrome,
                )

                // Echter GLES-Viewport auf Basis der Shader aus PrusaSlicer.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SceneView(
                        core = service.coreOrNull,
                        shaderDir = remember(service) { service.shaderDir() },
                        selectedId = selected?.id,
                        selectedIds = selectedIds,
                        onSelect = { id ->
                            selectedId = id.takeIf { it >= 0 }
                            selectedIds = selectedId?.let { setOf(it) } ?: emptySet()
                        },
                        onSurfaceTap = surfaceMode?.let { activeTool ->
                            { hit ->
                                when (activeTool) {
                                    SurfaceToolMode.Flatten -> {
                                        service.layOnFacet(hit)
                                        surfaceMode = null
                                    }
                                    SurfaceToolMode.Measure -> {
                                        val start = measureStart
                                        if (start == null) {
                                            measureStart = hit
                                            measureText =
                                                "Erster Messpunkt gesetzt · zweiten Punkt antippen"
                                        } else {
                                            val dx = hit.x - start.x
                                            val dy = hit.y - start.y
                                            val dz = hit.z - start.z
                                            val distance = sqrt(dx * dx + dy * dy + dz * dz)
                                            measureText =
                                                "Abstand: %.3f mm".format(distance)
                                            measureStart = null
                                        }
                                    }
                                    is SurfaceToolMode.Paint ->
                                        service.paintFacet(
                                            hit, activeTool.tool,
                                            activeTool.state, activeTool.radiusMm,
                                        )
                                }
                            }
                        },
                        invalidateKey = sceneRevision,
                        controller = sceneController,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Variable Schichthöhe darf keine unsichtbare
                    // Hintergrund-Einstellung sein: Nach dem Übernehmen
                    // bleibt eine kompakte, farbige Höhenkarte direkt im
                    // Arbeitsbereich sichtbar. Sie gehört zum aktuell
                    // ausgewählten Modell und verschwindet bei Preview bzw.
                    // wenn die Inspectorfläche offen ist.
                    if (!previewMode && !inspectorOpen && selected != null && selectedLayerProfile.isNotEmpty()) {
                        LayerProfileSceneOverlay(
                            objectHeight = selected.sizeMm.third.toDouble(),
                            profile = selectedLayerProfile,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 18.dp, bottom = 96.dp),
                        )
                    }
                    // Ein Werkzeugfehler darf nicht ausschliesslich im
                    // Seitenpanel stehen: Bei geschlossenem Panel wäre er
                    // für Touch-Nutzung unsichtbar. Die kompakte Meldung
                    // bleibt direkt am Arbeitsbereich und kann weggetippt
                    // werden.
                    toolMessage?.let { message ->
                        Surface(
                            modifier = Modifier
                                // Der Inspector liegt als Overlay über dem
                                // rechten Arbeitsbereich. Eine mittige
                                // Meldung wäre dort teilweise oder komplett
                                // verdeckt. Links neben dem Panel bleibt sie
                                // auf Tablet und im schmalen Querformat immer
                                // antipp- und lesbar.
                                .align(Alignment.TopStart)
                                .padding(top = 14.dp, start = 126.dp, end = 18.dp)
                                .widthIn(max = 420.dp)
                                .clickable { service.clearToolMessage() },
                            shape = RoundedCornerShape(10.dp),
                            color = PrusaColors.Panel.copy(alpha = 0.96f),
                            shadowElevation = 6.dp,
                        ) {
                            Text(
                                message,
                                color = PrusaColors.Orange,
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                    // Ansicht und Editor/Preview stehen in EINER Leiste.
                    // Zwei frei schwebende Leisten ueberlappten sich auf
                    // kleineren Tablets, obwohl jede fuer sich gut passte.
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewModeTabs(
                            preview = previewMode,
                            previewEnabled = layerCount > 0 ||
                                progress is SlicerService.Progress.Done,
                            onSelect = { wantPreview ->
                                if (wantPreview) {
                                    sceneController.enterPreview { n ->
                                        layerCount = n
                                        layerLo = 0
                                        layerHi = (n - 1).coerceAtLeast(0)
                                        previewMode = n > 0
                                    }
                                } else {
                                    sceneController.enterEditor()
                                    previewMode = false
                                }
                            },
                        )
                        ViewBar(sceneController)
                    }

                    if (previewMode && layerCount > 1) {
                        LayerSlider(
                            count = layerCount,
                            low = layerLo,
                            high = layerHi,
                            onChange = { lo, hi ->
                                layerLo = lo; layerHi = hi
                                sceneController.setLayerRange(lo, hi)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp, top = 12.dp, bottom = 84.dp),
                        )
                    }
                }
            }

            if (permanentInspector) {
                Sidebar(
                    service = service,
                    presets = presets,
                    extruderOptions = extruderOptions,
                    objects = objects,
                    volumes = volumes,
                    beds = beds,
                    selected = selected,
                    selectedIds = selectedIds,
                    surfaceMode = surfaceMode,
                    measureText = measureText,
                    progress = progress,
                    onSelect = {
                        val next = SelectionModel.single(it)
                        selectedId = next.primaryId
                        selectedIds = next.ids
                    },
                    onToggleSelection = { id ->
                        val next = SelectionModel(
                            LinkedHashSet(selectedIds), selectedId
                        ).toggle(id)
                        selectedIds = next.ids
                        selectedId = next.primaryId
                    },
                    onSelectAll = {
                        val next = SelectionModel.all(objects.map { it.id })
                        selectedIds = next.ids
                        selectedId = next.primaryId
                    },
                    onClearSelection = {
                        selectedIds = emptySet()
                        selectedId = null
                    },
                    onSurfaceMode = { mode ->
                        if (mode != null && previewMode) {
                            sceneController.enterEditor()
                            previewMode = false
                        }
                        surfaceMode = mode
                        if (mode !is SurfaceToolMode.Measure) {
                            measureStart = null
                            if (mode != null) measureText = null
                        }
                    },
                    onExportPlate = onExportPlate,
                    onRepairStl = onRepairStl,
                    onConvertGcode = onConvertGcode,
                    onAddSvg = onAddSvg,
                    onShare = onShare,
                    onOpenSettings = { service.showScreen(SlicerService.Screen.Settings(it)) },
                    onManagePrinters = { service.showScreen(SlicerService.Screen.Printers) },
                    linkPrinters = linkPrinters,
                    sendState = sendState,
                    scaleTool = scaleTool,
                    gizmo = gizmo,
                    onGizmoChange = { g ->
                        gizmo = g
                        sceneController.setGizmo(g)
                    },
                    configRevision = configRevision,
                    onScaleToolChange = { on ->
                        scaleTool = on
                        sceneController.scaleTool = on
                    },
                    modifier = Modifier.width(inspectorWidth).fillMaxHeight(),
                )
            }
        }

        if (!permanentInspector && inspectorOpen) {
            // Eine leichte Abdunklung macht klar, dass der Inspektor ueber
            // dem Arbeitsbereich liegt und durch Tippen daneben schliesst.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = TOOL_RAIL_WIDTH)
                    .background(Color.Black.copy(alpha = 0.26f))
                    .clickable { inspectorOpen = false }
                    .zIndex(1f),
            )
            Sidebar(
                service = service,
                presets = presets,
                extruderOptions = extruderOptions,
                objects = objects,
                volumes = volumes,
                beds = beds,
                selected = selected,
                selectedIds = selectedIds,
                surfaceMode = surfaceMode,
                measureText = measureText,
                progress = progress,
                onSelect = {
                    val next = SelectionModel.single(it)
                    selectedId = next.primaryId
                    selectedIds = next.ids
                },
                onToggleSelection = { id ->
                    val next = SelectionModel(
                        LinkedHashSet(selectedIds), selectedId
                    ).toggle(id)
                    selectedIds = next.ids
                    selectedId = next.primaryId
                },
                onSelectAll = {
                    val next = SelectionModel.all(objects.map { it.id })
                    selectedIds = next.ids
                    selectedId = next.primaryId
                },
                onClearSelection = {
                    selectedIds = emptySet()
                    selectedId = null
                },
                onSurfaceMode = { mode ->
                    if (mode != null && previewMode) {
                        sceneController.enterEditor()
                        previewMode = false
                    }
                    surfaceMode = mode
                    if (mode !is SurfaceToolMode.Measure) {
                        measureStart = null
                        if (mode != null) measureText = null
                    }
                },
                onExportPlate = onExportPlate,
                onRepairStl = onRepairStl,
                onConvertGcode = onConvertGcode,
                onAddSvg = onAddSvg,
                onShare = onShare,
                onOpenSettings = { service.showScreen(SlicerService.Screen.Settings(it)) },
                onManagePrinters = { service.showScreen(SlicerService.Screen.Printers) },
                linkPrinters = linkPrinters,
                sendState = sendState,
                scaleTool = scaleTool,
                gizmo = gizmo,
                onGizmoChange = { g ->
                    gizmo = g
                    sceneController.setGizmo(g)
                },
                configRevision = configRevision,
                onScaleToolChange = { on ->
                    scaleTool = on
                    sceneController.scaleTool = on
                },
                onClose = { inspectorOpen = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(inspectorWidth)
                    .fillMaxHeight()
                    .zIndex(2f),
            )
        }
    }

    if (confirmNewProject) {
        AlertDialog(
            onDismissRequest = { confirmNewProject = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(advancedText("New project", "Neues Projekt")) },
            text = {
                Text(
                    advancedText(
                        "The current project will be closed. Unsaved changes will be lost.",
                        "Das aktuelle Projekt wird geschlossen. Nicht gespeicherte Änderungen gehen verloren.",
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmNewProject = false
                        onNewProject()
                    },
                ) { Text(advancedText("Create new", "Neu anlegen"), color = PrusaColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewProject = false }) {
                    Text(advancedText("Cancel", "Abbrechen"))
                }
            },
        )
    }

    if (confirmReloadProject) {
        AlertDialog(
            onDismissRequest = { confirmReloadProject = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(advancedText("Reload project", "Projekt neu laden")) },
            text = {
                Text(
                    advancedText(
                        "The saved 3MF file will be reloaded from storage. Unsaved changes in the current project will be lost.",
                        "Die gespeicherte 3MF-Datei wird erneut vom Datenträger geladen. Nicht gespeicherte Änderungen im aktuellen Projekt gehen verloren.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReloadProject = false
                    onReloadProject()
                }) { Text(advancedText("Reload", "Neu laden")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReloadProject = false }) { Text(advancedText("Cancel", "Abbrechen")) }
            },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Werkzeugleiste                                                      */
/* ------------------------------------------------------------------ */

/**
 * Werkzeugleiste aus PrusaSlicers eigener Definition.
 *
 * Reihenfolge, Icon-Datei und Tooltip stammen aus GLCanvas3D.cpp und
 * werden von build/scripts/extract-ui.py als toolbar.json uebernommen.
 * Nichts davon ist hier ausgewaehlt oder benannt - siehe E-12.
 *
 * Am Desktop laeuft diese Leiste waagerecht ueber dem Bett. Auf dem
 * Tablet steht sie senkrecht links: quer wuerde sie bei 56 dp Zielgroesse
 * die halbe Bettbreite fressen.
 */
@Composable
private fun ToolStrip(
    hasSelection: Boolean,
    canPaste: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onTool: (String) -> Unit,
) {
    val tools = PsUi.toolbar
    // Welche Werkzeuge ohne Auswahl sinnlos sind - entspricht den
    // enabling_callbacks im Original.
    val needsSelection = setOf("delete", "copy", "more", "fewer",
                               "splitobjects", "splitvolumes")
    val notYet = setOf("layersediting")

    Column(
        Modifier
            .width(TOOL_RAIL_WIDTH)
            .fillMaxHeight()
            .background(PrusaColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tools.filterNot { it.name in notYet }.forEach { tool ->
            val enabled = (tool.name !in needsSelection || hasSelection) &&
                          (tool.name != "paste" || canPaste) &&
                          (tool.name != "undo" || canUndo) &&
                          (tool.name != "redo" || canRedo)
            ToolButton(tool, enabled) { onTool(tool.name) }
        }
    }
}

@Composable
private fun ToolButton(tool: PsUi.Tool, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TOOL_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) PrusaColors.PanelRaised else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PsIcon(
                name = tool.icon,
                modifier = Modifier.size(27.dp).alpha(if (enabled) 1f else 0.3f),
                contentDescription = PsUi.tr(tool.tooltip),
            )
            Text(
                shortToolLabel(tool),
                color = PrusaColors.TextMuted.copy(alpha = if (enabled) 1f else 0.35f),
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

/** Kurze Rail-Texte; der vollstaendige Desktop-Tooltip bleibt am Icon. */
private fun shortToolLabel(tool: PsUi.Tool): String = when (tool.name) {
    "add" -> advancedText("Import", "Import")
    "undo" -> advancedText("Undo", "Zurück")
    "redo" -> advancedText("Redo", "Vor")
    "delete" -> advancedText("Delete", "Löschen")
    "deleteall" -> advancedText("Clear", "Leeren")
    "arrange" -> advancedText("Arrange", "Anordnen")
    "arrangecurrent" -> advancedText("Current bed", "Akt. Bett")
    "copy" -> advancedText("Copy", "Kopieren")
    "paste" -> advancedText("Paste", "Einfügen")
    "more" -> advancedText("+ copy", "+ Kopie")
    "fewer" -> advancedText("− copy", "− Kopie")
    "splitobjects" -> advancedText("Objects", "Objekte")
    "splitvolumes" -> advancedText("Volumes", "Volumen")
    "settings" -> advancedText("Options", "Optionen")
    else -> PsUi.tr(tool.tooltip)
}

/**
 * Ansichtsleiste ueber dem Bett - Gegenstueck zur Ansichts-Werkzeugleiste
 * unten im Slicer. Wichtig auf dem Tablet: nach ein paar Wischern ist man
 * schnell unter dem Bett, und ohne festen Blickwinkel findet man nicht
 * zurueck.
 */
/**
 * Ein Extruder in der Seitenleiste: Nummer, Farbfeld, Filamentauswahl.
 *
 * Am Desktop steht das in der Sidebar untereinander, ein Kombifeld je
 * Extruder mit einem Farbquadrat davor. Genauso hier, nur mit Zielen in
 * Fingergroesse.
 */
@Composable
private fun ExtruderRow(
    extruder: SlicerService.Extruder,
    filaments: List<String>,
    onFilament: (String) -> Unit,
    onColor: (String) -> Unit,
    onPickFilament: () -> Unit,
) {
    var pickColor by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${extruder.index + 1}",
            color = PrusaColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(14.dp),
        )

        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parseColor(extruder.color) ?: PrusaColors.PanelRaised)
                .border(
                    1.dp,
                    if (extruder.color.isEmpty()) PrusaColors.Divider else Color.White.copy(alpha = 0.45f),
                    RoundedCornerShape(8.dp),
                )
                .clickable { pickColor = true },
            contentAlignment = Alignment.Center,
        ) {
            // Ohne eigene Farbe gilt die des Filaments - das sagt der Strich.
            if (extruder.color.isEmpty())
                Text("–", color = PrusaColors.TextMuted, fontSize = 13.sp)
        }

        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(9.dp))
                .clickable(onClick = onPickFilament)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    extruder.filament.ifBlank { advancedText("Choose material", "Material auswählen") },
                    color = PrusaColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
                Text(advancedText("Choose spool & material", "Spule & Material wählen"), color = PrusaColors.TextMuted, fontSize = 11.sp)
            }
            Text("›", color = PrusaColors.Orange, fontSize = 24.sp)
        }
    }

    if (pickColor) {
        ColorPickerDialog(
            current = extruder.color,
            onPick = { onColor(it); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

/** Same visual entry point for a single-nozzle printer and a multi-head bank. */
@Composable
private fun MaterialPickerButton(selected: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PrusaColors.PanelRaised)
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                selected.ifBlank { advancedText("Choose material", "Material auswählen") },
                color = PrusaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
            )
            Text(advancedText("Choose spool & material", "Spule & Material wählen"), color = PrusaColors.TextMuted, fontSize = 11.sp)
        }
        Text("›", color = PrusaColors.Orange, fontSize = 24.sp)
    }
}

/**
 * A compact head bank for MMU/INDX/XL printers.
 *
 * The previous layout rendered one complete preset selector per head. That is
 * fine for two nozzles, but turns an INDX 8T into an eight-screen-long list.
 * The bank keeps every physical head visible in one compact line and opens exactly one generous
 * editor below it, so colour and material are still independently editable.
 */
@Composable
private fun ExtruderBank(
    extruders: List<SlicerService.Extruder>,
    selectedIndex: Int,
    filaments: List<String>,
    onSelect: (Int) -> Unit,
    onFilament: (Int, String) -> Unit,
    onColor: (Int, String) -> Unit,
    onPickFilament: (Int) -> Unit,
) {
    if (extruders.isEmpty()) return
    val normalized = normalizedExtruderIndex(selectedIndex, extruders.size)
    val selected = extruders[normalized]

    Text(
        PsUi.appText(
            "Choose T1–T8 · colour and material per tool",
            "T1–T8 auswählen · Farbe und Material je Werkzeug",
        ),
        color = PrusaColors.TextMuted,
        fontSize = 12.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        extruderHeadGroups(extruders.size, columns = 8).forEach { group ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.forEach { index ->
                    val head = extruders[index]
                    val active = index == normalized
                    Column(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrusaColors.PanelRaised else PrusaColors.Panel)
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) PrusaColors.Orange else PrusaColors.Divider,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "T${index + 1}",
                            color = if (active) PrusaColors.TextPrimary else PrusaColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(parseColor(head.color) ?: PrusaColors.Divider),
                        )
                    }
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Text(
        advancedText("Edit T${selected.index + 1}", "T${selected.index + 1} bearbeiten"),
        color = PrusaColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
    ExtruderRow(
        extruder = selected,
        filaments = filaments,
        onFilament = { onFilament(selected.index, it) },
        onColor = { onColor(selected.index, it) },
        onPickFilament = { onPickFilament(selected.index) },
    )
}

/**
 * Druckdauer wie am Desktop: Tage, Stunden, Minuten - nur was noetig ist.
 * "14 min" statt "0 h 14 min", "2 h 07 min" statt "127 min".
 */
private fun formatDuration(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val d = total / 86400
    val h = (total % 86400) / 3600
    val m = (total % 3600) / 60
    return when {
        d > 0 -> "%d d %d h %02d min".format(d, h, m)
        h > 0 -> "%d h %02d min".format(h, m)
        else  -> "%d min".format(m)
    }
}

/** "#RRGGBB" nach Compose-Color. Null, wenn nichts oder Unsinn drinsteht. */
private fun parseColor(rgb: String): Color? {
    val hex = rgb.removePrefix("#")
    if (hex.length != 6) return null
    val v = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or v)
}

/**
 * Farbwahl fuer einen Extruder.
 *
 * Die Vorschlaege sind die Filamentfarben, die Prusa in seinen eigenen
 * Profilen verwendet - damit trifft man die uebliche Rolle meist mit
 * einem Tipp. Freie Eingabe als Hex bleibt daneben moeglich.
 */
@Composable
private fun ColorPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val swatches = listOf(
        "#FF8000", "#ED6B21", "#DD2222", "#B02020", "#F062A0", "#A349A4",
        "#5B31A8", "#2850C8", "#17A9E0", "#0FB0A0", "#22A03C", "#8ACB2E",
        "#F2E200", "#C8A020", "#7A5230", "#FFFFFF", "#B0B0B0", "#1A1A1A",
    )
    var manual by remember { mutableStateOf(current) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(18.dp),
            shape = RoundedCornerShape(18.dp),
            color = PrusaColors.Panel,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    advancedText("Extruder colour", "Farbe des Extruders"),
                    color = PrusaColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    advancedText(
                        "Choose a colour for this tool or enter your own hex value.",
                        "Farbe für dieses Werkzeug wählen oder einen eigenen Hex-Wert eingeben.",
                    ),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                )
                // Fünf klare Spalten behalten eine mindestens 44-dp große
                // Trefferfläche auch bei der schmalen Seitenansicht.
                swatches.chunked(5).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { hex ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseColor(hex) ?: Color.Gray)
                                    .border(
                                        if (hex.equals(current, true)) 3.dp else 1.dp,
                                        if (hex.equals(current, true)) PrusaColors.Orange
                                        else PrusaColors.Divider,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onPick(hex) },
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text(advancedText("Custom value, e.g. #3399FF", "Eigener Wert, z. B. #3399FF")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrusaColors.TextPrimary,
                        unfocusedTextColor = PrusaColors.TextPrimary,
                        focusedLabelColor = PrusaColors.Orange,
                        unfocusedLabelColor = PrusaColors.TextMuted,
                        focusedContainerColor = PrusaColors.PanelRaised,
                        unfocusedContainerColor = PrusaColors.PanelRaised,
                    ),
                )
                HorizontalDivider(color = PrusaColors.Divider)
                Button(
                    onClick = { onPick(manual.trim()) },
                    enabled = manual.isBlank() || parseColor(manual.trim()) != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrusaColors.Orange,
                        contentColor = PrusaColors.TextPrimary,
                    ),
                ) { Text(advancedText("Apply", "Übernehmen")) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { onPick("") },
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(advancedText("Use filament", "Vom Filament"), maxLines = 1) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(advancedText("Cancel", "Abbrechen"), maxLines = 1) }
                }
            }
        }
    }
}

/**
 * Umschalter zwischen Bett und Werkzeugwegen.
 *
 * Am Desktop sind das die beiden Reiter unten links am Bett
 * ("3D editor view" / "Preview"), umgesetzt als wxNotebook. Ein Notebook
 * gibt es hier nicht, die Beschriftungen und die Position aber schon.
 */
@Composable
private fun ViewModeTabs(
    preview: Boolean,
    previewEnabled: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrusaColors.Panel.copy(alpha = 0.88f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            PsUi.tr("3D editor view") to false,
            PsUi.tr("Preview") to true,
        ).forEach { (label, isPreview) ->
            val on = preview == isPreview
            val enabled = !isPreview || previewEnabled
            Text(
                label,
                color = when {
                    on       -> Color.White
                    !enabled -> PrusaColors.TextMuted.copy(alpha = 0.4f)
                    else     -> PrusaColors.TextMuted
                },
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) PrusaColors.Orange else Color.Transparent)
                    .clickable(enabled = enabled && !on) { onSelect(isPreview) }
                    .heightIn(min = TOUCH_TARGET)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Persistent viewport legend for an applied variable-layer profile. */
@Composable
private fun LayerProfileSceneOverlay(
    objectHeight: Double,
    profile: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    val segments = layerProfilePreviewSegments(objectHeight.coerceAtLeast(0.01), profile)
    if (segments.isEmpty()) return
    val maxLayer = segments.maxOf { it.heightMm }.coerceAtLeast(0.01)
    Surface(
        modifier = modifier.width(164.dp),
        shape = RoundedCornerShape(10.dp),
        color = PrusaColors.Panel.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Schichthöhen aktiv",
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.width(26.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(PrusaColors.PanelRaised),
                ) {
                    segments.asReversed().forEach { segment ->
                        val size = ((segment.toZ - segment.fromZ) / objectHeight)
                            .toFloat().coerceAtLeast(0.03f)
                        val fine = segment.heightMm / maxLayer < 0.75
                        Box(
                            Modifier.fillMaxWidth().weight(size)
                                .background(if (fine) PrusaColors.Orange else PrusaColors.Divider),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "am ausgewählten Modell",
                        color = PrusaColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                    Text(
                        segments.joinToString(" · ") { "%.2f mm".format(it.heightMm) },
                        color = PrusaColors.TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "orange = fein · grau = grob",
                color = PrusaColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Senkrechter Schichtregler rechts am Bett, mit zwei Griffen wie
 * PrusaSlicers DoubleSlider: der obere begrenzt die sichtbare Hoehe, der
 * untere blendet alles darunter aus.
 *
 * Das Original ist ein selbst gezeichnetes wx-Control, also gibt es nichts
 * zu uebernehmen - nachgebaut mit Griffen in Fingergroesse statt der
 * 12 px am Desktop.
 */
@Composable
private fun LayerSlider(
    count: Int,
    low: Int,
    high: Int,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = 6.dp
    val thumb = 28.dp
    val labelWidth = 44.dp    // Platz fuer bis zu vier Ziffern links der Schiene

    val density = LocalDensity.current
    val thumbPx = with(density) { thumb.toPx() }
    var height by remember { mutableStateOf(1f) }
    // Welchen Griff der Finger gepackt hat - waehrend eines Zuges fest,
    // sonst springt der Regler bei eng beieinander liegenden Griffen.
    var dragTop by remember { mutableStateOf(true) }

    // Die Schiene ist oben und unten um einen halben Griff eingerueckt,
    // sonst raecht sich der Griff an den Enden ueber den Rand hinaus.
    val usable = (height - thumbPx).coerceAtLeast(1f)
    val last = (count - 1).coerceAtLeast(1)

    /** Bildschirm-y (0 = oben) auf Schichtnummer. Schicht 0 liegt unten. */
    fun toLayer(y: Float): Int =
        ((1f - ((y - thumbPx / 2f) / usable).coerceIn(0f, 1f)) * last).roundToInt()

    /** Schichtnummer auf die Mitte ihres Griffs in Pixeln. */
    fun toCenter(layer: Int): Float =
        thumbPx / 2f + (1f - layer.toFloat() / last) * usable

    Box(
        modifier
            .width(labelWidth + thumb + 8.dp)
            .fillMaxHeight()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(count) {
                detectDragGestures(
                    onDragStart = { p ->
                        val l = toLayer(p.y)
                        dragTop = kotlin.math.abs(l - high) <= kotlin.math.abs(l - low)
                        if (dragTop) onChange(low, l.coerceAtLeast(low))
                        else onChange(l.coerceAtMost(high), high)
                    },
                ) { change, _ ->
                    val l = toLayer(change.position.y)
                    if (dragTop) onChange(low, l.coerceAtLeast(low))
                    else onChange(l.coerceAtMost(high), high)
                    change.consume()
                }
            },
    ) {
        with(density) {
            val topC = toCenter(high)
            val botC = toCenter(low)

            // Schiene, an beiden Enden um den halben Griff eingerueckt.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (thumbPx / 2f).toDp(), end = (thumb - track) / 2)
                    .width(track)
                    .height(usable.toDp())
                    .clip(RoundedCornerShape(track / 2))
                    .background(PrusaColors.Panel.copy(alpha = 0.88f)),
            )

            // Der sichtbare Bereich zwischen beiden Griffen.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topC.toDp(), end = (thumb - track) / 2)
                    .width(track)
                    .height((botC - topC).coerceAtLeast(0f).toDp())
                    .background(PrusaColors.Orange),
            )

            listOf(high to topC, low to botC).forEach { (layer, centerPx) ->
                // Beschriftung links neben die Schiene, damit der Griff selbst
                // mittig darauf sitzt und nichts am Rand abgeschnitten wird.
                Text(
                    "$layer",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = (centerPx - 9.dp.toPx()).coerceAtLeast(0f).toDp())
                        .clip(RoundedCornerShape(4.dp))
                        .background(PrusaColors.Panel.copy(alpha = 0.88f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (centerPx - thumbPx / 2f).coerceAtLeast(0f).toDp())
                        .size(thumb)
                        .clip(RoundedCornerShape(thumb / 2))
                        .background(PrusaColors.Orange)
                        .border(2.dp, Color.White, RoundedCornerShape(thumb / 2)),
                )
            }
        }
    }
}

@Composable
private fun ViewBar(controller: SceneController, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrusaColors.Panel.copy(alpha = 0.88f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val views = listOf(
            "Iso" to PsmViewport.View.ISO,
            PsUi.appText("Top", "Oben") to PsmViewport.View.TOP,
            PsUi.appText("Front", "Vorn") to PsmViewport.View.FRONT,
            PsUi.appText("Back", "Hinten") to PsmViewport.View.BACK,
            PsUi.appText("Left", "Links") to PsmViewport.View.LEFT,
            PsUi.appText("Right", "Rechts") to PsmViewport.View.RIGHT,
        )
        views.forEach { (label, v) ->
            Box(
                Modifier
                    .height(TOUCH_TARGET)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { controller.setView(v) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = PrusaColors.TextPrimary, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Feste Kopfzeile des Arbeitsbereichs. Aktionen liegen dadurch nicht mehr
 * ueber dem Modell und bleiben auch bei einer weit herangezoomten Szene
 * eindeutig als Bedienung erkennbar.
 */
@Composable
private fun WorkspaceBar(
    beds: List<PsmCore.Bed>,
    onSelectBed: (Int) -> Unit,
    onAddBed: () -> Unit,
    onRemoveBed: (Int) -> Unit,
    lockedBeds: Set<Int>,
    onToggleBedLock: (Int) -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onReload: (() -> Unit)?,
    onOpenSimple: () -> Unit,
    actionsEnabled: Boolean,
    showInspectorAction: Boolean,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    tight: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(PrusaColors.Panel)
            .padding(horizontal = if (tight) 6.dp else 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectBar(
                onNew = onNew,
                onSave = onSave,
                onSaveAs = onSaveAs,
                onReload = onReload,
                enabled = actionsEnabled,
                tight = tight,
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(PrusaColors.Divider),
            )
            BedSelector(
                beds = beds,
                onSelect = onSelectBed,
                onAdd = onAddBed,
                onRemove = onRemoveBed,
                lockedBeds = lockedBeds,
                onToggleLock = onToggleBedLock,
            )
        }
        ProjectAction("Simple", actionsEnabled, onOpenSimple, tight = tight)
        if (showInspectorAction) {
            // Im Hochformat schnitt die feste Beschriftung den Bettwaehler
            // links ab. Das Rastersymbol allein traegt die Aussage, der
            // Name steht nur, wenn Platz dafuer da ist.
            Box(
                Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (inspectorOpen) PrusaColors.Orange
                        else PrusaColors.PanelRaised
                    )
                    .clickable(onClick = onToggleInspector)
                    .padding(horizontal = if (tight) 12.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = PsUi.appText("Panel", "Panel"),
                        tint = if (inspectorOpen) Color.White else PrusaColors.TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                    if (!tight) {
                        Text(
                            "Panel",
                            color = if (inspectorOpen) Color.White else PrusaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Projektaktionen bleiben sichtbar, statt hinter einem Desktop-Menü zu
 * verschwinden. Die Ziele sind bewusst groß genug für Fingerbedienung.
 */
@Composable
private fun ProjectBar(
    onNew: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onReload: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tight: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PrusaColors.Background.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ProjectAction(advancedText("New", "Neu"), enabled, onNew, tight)
        ProjectAction(advancedText("Save", "Speichern"), enabled, onSave, tight)
        ProjectAction(advancedText("Save as", "Speichern unter"), enabled, onSaveAs, tight)
        if (onReload != null) {
            ProjectAction(advancedText("Reload", "Neu laden"), enabled, onReload, tight)
        }
    }
}

@Composable
private fun ProjectAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tight: Boolean = false,
) {
    Box(
        Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) PrusaColors.PanelRaised
                else PrusaColors.PanelRaised.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (tight) 10.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) PrusaColors.TextPrimary else PrusaColors.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Direkte Mehrbett-Auswahl.
 *
 * PrusaSlicer Desktop legt virtuelle Betten als grosse 2D-Landschaft
 * nebeneinander. Auf Touchgeraeten wuerde das den Nutzer zum blinden
 * horizontalen Scrollen zwingen. Hier bleibt die Kamera auf einem Bett
 * und jeder Chip springt unmittelbar zum gewaehlten Bett.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BedSelector(
    beds: List<PsmCore.Bed>,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    lockedBeds: Set<Int>,
    onToggleLock: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (beds.isEmpty()) return
    val active = beds.firstOrNull { it.active } ?: beds.first()

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PrusaColors.Background.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        beds.forEach { bed ->
            Text(
                "${if (bed.index in lockedBeds) "🔒 " else ""}${advancedText("Bed", "Bett")} ${bed.index + 1} · ${bed.objectCount}",
                color = if (bed.active) Color.White else PrusaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (bed.active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (bed.active) PrusaColors.Orange else PrusaColors.PanelRaised)
                    .combinedClickable(
                        enabled = true,
                        onClick = { if (!bed.active) onSelect(bed.index) },
                        onLongClick = { onToggleLock(bed.index) },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.PanelRaised)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = advancedText("Add print bed", "Druckbett hinzufügen"),
                 tint = PrusaColors.TextPrimary)
        }

        if (beds.size > 1) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrusaColors.PanelRaised)
                    .clickable { onRemove(active.index) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, contentDescription = advancedText("Remove active print bed", "Aktives Druckbett entfernen"),
                     tint = PrusaColors.TextMuted)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Seitenleiste                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun Sidebar(
    service: SlicerService,
    presets: SlicerService.Presets,
    extruderOptions: List<ExtruderChoice>,
    objects: List<PsmCore.ObjectInfo>,
    volumes: Map<Int, List<PsmCore.VolumeInfo>>,
    beds: List<PsmCore.Bed>,
    selected: PsmCore.ObjectInfo?,
    selectedIds: Set<Int>,
    surfaceMode: SurfaceToolMode?,
    measureText: String?,
    progress: SlicerService.Progress,
    onSelect: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSurfaceMode: (SurfaceToolMode?) -> Unit,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
    onAddSvg: (Int, Float, PsmCore.VolumeType) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onOpenSettings: (String) -> Unit,
    onManagePrinters: () -> Unit,
    linkPrinters: List<de.psmobile.net.PrusaLink.Printer>,
    sendState: String?,
    scaleTool: Boolean,
    onScaleToolChange: (Boolean) -> Unit,
    gizmo: de.psmobile.core.PsmViewport.Gizmo,
    onGizmoChange: (de.psmobile.core.PsmViewport.Gizmo) -> Unit,
    configRevision: Int,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isRunning = progress is SlicerService.Progress.Running
    var sendMenu by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(InspectorSection.PROFILES) }
    var selectedExtruderIndex by remember(presets.selectedPrinter) { mutableStateOf(0) }
    var filamentPickerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingPresetSwitch by remember { mutableStateOf<PendingPresetSwitch?>(null) }
    var savePresetFor by remember { mutableStateOf<PsmCore.PresetType?>(null) }
    var savePresetName by remember { mutableStateOf("") }
    var objectQuery by remember { mutableStateOf("") }
    var headEditorRequest by remember { mutableStateOf(0) }
    var layerEditorObjectId by remember { mutableStateOf<Int?>(null) }
    val inspectorScroll = rememberScrollState()
    val toolMessage by service.toolMessage.collectAsState()

    fun requestPresetSwitch(
        type: PsmCore.PresetType,
        current: String,
        target: String,
    ) {
        if (target == current) return
        val changes = presets.changes(type)
        if (changes.isEmpty()) {
            service.selectPreset(type, target)
        } else {
            pendingPresetSwitch = PendingPresetSwitch(type, target, changes)
        }
    }

    LaunchedEffect(selected?.id) {
        if (selected == null &&
            (section == InspectorSection.TRANSFORM ||
                section == InspectorSection.TOOLS))
            section = InspectorSection.OBJECTS
    }

    LaunchedEffect(filamentPickerIndex) {
        inspectorScroll.scrollTo(0)
    }

    // Ein Tabwechsel darf keinen Scrollstand aus dem vorherigen Bereich
    // übernehmen. Sonst öffnet etwa „Werkzeuge“ mitten in der Profilliste
    // und die Orientierung bzw. die Tab-Leiste wirken verschwunden.
    LaunchedEffect(section) {
        inspectorScroll.scrollTo(0)
    }

    LaunchedEffect(section, selected?.id) {
        if (section != InspectorSection.TOOLS || layerEditorObjectId != selected?.id) {
            layerEditorObjectId = null
        }
    }

    // Bei mehreren Köpfen liegt der Editor direkt nach der Bank. Ein
    // Kopfwechsel soll ihn daher sichtbar machen, statt weiteres Scrollen
    // als versteckte Voraussetzung für Material- und Farbwahl zu verlangen.
    LaunchedEffect(headEditorRequest) {
        if (headEditorRequest > 0) {
            inspectorScroll.animateScrollTo(extruderEditorScrollTarget(presets.extruders.size))
        }
    }

    Column(
        modifier
            .background(PrusaColors.Panel)
            .border(1.dp, PrusaColors.Divider)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    PsUi.appText("Workspace", "Arbeitsbereich"),
                    color = PrusaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    beds.firstOrNull { it.active }?.let {
                        PsUi.appText(
                            "Bed ${it.index + 1} · ${it.objectCount} objects",
                            "Bett ${it.index + 1} · ${it.objectCount} Objekte",
                        )
                    } ?: PsUi.appText("No print bed", "Kein Druckbett"),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            onClose?.let { close ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrusaColors.PanelRaised)
                        .clickable(onClick = close),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = PsUi.appText("Close panel", "Panel schließen"),
                        tint = PrusaColors.TextPrimary,
                    )
                }
            }
        }

        InspectorTabs(
            selected = section,
            transformEnabled = selected != null,
            onSelect = { section = it },
        )

        val editingLayersFor = layerEditorObjectId
        if (editingLayersFor != null && selected?.id == editingLayersFor) {
            LayerProfileToolPage(
                objectHeight = selected.sizeMm.third.toDouble(),
                initial = service.layerProfile(editingLayersFor),
                onBack = { layerEditorObjectId = null },
                onApply = { points ->
                    service.setLayerProfile(editingLayersFor, points)
                    layerEditorObjectId = null
                },
                onReset = {
                    service.setLayerProfile(editingLayersFor, emptyList())
                    layerEditorObjectId = null
                },
                modifier = Modifier.weight(1f),
            )
        } else Column(
            Modifier.weight(1f).verticalScroll(inspectorScroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (section) {
                InspectorSection.PROFILES -> {
                    val materialHead = filamentPickerIndex
                    if (materialHead != null) {
                        // Same composable as Simple Mode, deliberately kept in this
                        // sidebar composition so its device scale stays identical.
                        SimpleMaterialChooser(
                            filaments = presets.filaments,
                            selectedExtruder = materialHead,
                            onBack = { filamentPickerIndex = null },
                            onChoose = { filament ->
                                if (presets.extruders.size > 1) {
                                    service.setExtruderFilament(materialHead, filament)
                                } else {
                                    requestPresetSwitch(
                                        PsmCore.PresetType.FILAMENT,
                                        presets.selectedFilament,
                                        filament,
                                    )
                                }
                                filamentPickerIndex = null
                            },
                            onOpenAdvanced = { onOpenSettings("filament") },
                            incompatible = presets.incompatibleFilaments,
                            showIncompatible = presets.showIncompatible,
                            onShowIncompatible = service::setShowIncompatiblePresets,
                        )
                    } else {
                    // Beschriftungen wie im Original, uebersetzt aus dessen Katalog.
                    SectionLabel(PsUi.tr("Printer"))
                    PresetCombo(PsUi.tr("Printer"), presets.printers, presets.selectedPrinter,
                                dirtyCount = presets.printerChanges.size,
                                onEdit = { onOpenSettings("printer") }) {
                        requestPresetSwitch(
                            PsmCore.PresetType.PRINTER,
                            presets.selectedPrinter,
                            it,
                        )
                    }

                    SectionLabel(PsUi.tr("Print settings"))
                    PresetCombo(PsUi.tr("Print settings"), presets.prints, presets.selectedPrint,
                                dirtyCount = presets.printChanges.size,
                                onEdit = { onOpenSettings("print") }) {
                        requestPresetSwitch(
                            PsmCore.PresetType.PRINT,
                            presets.selectedPrint,
                            it,
                        )
                    }

                    // Ein Kopf sieht aus wie bisher, ab zwei wird je Extruder
                    // gewaehlt. Ein MMU3 hat fuenf Wege, ein XL bis zu fuenf
                    // Koepfe - ohne eigene Wahl je Kopf bekaemen alle dasselbe.
                    if (presets.extruders.size <= 1) {
                        SectionLabel(PsUi.tr("Filament"))
                        MaterialPickerButton(
                            selected = presets.selectedFilament,
                            onClick = { filamentPickerIndex = 0 },
                        )
                    } else {
                        SectionLabel(PsUi.tr("Filament") + " · ${presets.extruders.size} Extruder")
                        ExtruderBank(
                            extruders = presets.extruders,
                            selectedIndex = selectedExtruderIndex,
                            filaments = presets.filaments,
                            onSelect = {
                                selectedExtruderIndex = it
                                headEditorRequest += 1
                            },
                            onFilament = { index, filament ->
                                service.setExtruderFilament(index, filament)
                            },
                            onColor = { index, color -> service.setExtruderColor(index, color) },
                            onPickFilament = { index -> filamentPickerIndex = index },
                        )
                    }

                    HorizontalDivider(
                        Modifier.padding(vertical = 4.dp),
                        color = PrusaColors.Divider,
                    )

                    // Die Handvoll Werte, die man staendig anfasst - dieselbe
                    // Auswahl wie in FrequentlyChangedParameters.cpp.
                    service.coreOrNull?.let { c ->
                        QuickSettings(service, c, configRevision)
                    }

                    OutlinedButton(
                        onClick = onManagePrinters,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            advancedText("Manage printers", "Drucker verwalten"),
                            color = PrusaColors.TextPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = { service.showScreen(SlicerService.Screen.Wizard) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            PsUi.appText("Open Advanced wizard", "Advanced-Assistent öffnen"),
                            color = PrusaColors.TextPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    if (presets.extruders.size >= 2) {
                        OutlinedButton(
                            onClick = { service.showScreen(SlicerService.Screen.ColorMix) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("ColorMix", color = PrusaColors.TextPrimary, fontSize = 14.sp)
                        }
                    }
                    }
                }

                InspectorSection.OBJECTS -> {
                    if (objects.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrusaColors.PanelRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    PsUi.appText("No objects yet", "Noch keine Objekte"),
                                    color = PrusaColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    PsUi.appText(
                                        "Import with + in the tool rail",
                                        "Über + in der Werkzeugleiste importieren",
                                    ),
                                    color = PrusaColors.TextMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = objectQuery,
                            onValueChange = { objectQuery = it },
                            singleLine = true,
                            label = { Text(advancedText("Search objects", "Objekte suchen")) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onSelectAll,
                                modifier = Modifier.height(48.dp),
                            ) { Text(PsUi.appText("Select all", "Alle auswählen")) }
                            TextButton(
                                onClick = onClearSelection,
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.height(48.dp),
                            ) { Text(PsUi.appText("Clear", "Aufheben")) }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${selectedIds.size}/${objects.size}",
                                color = PrusaColors.TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                        val visibleObjects = objects.filter {
                            objectQuery.isBlank() ||
                                it.name.contains(objectQuery, ignoreCase = true) ||
                                it.id.toString().contains(objectQuery)
                        }
                        if (visibleObjects.isEmpty()) {
                            Text(
                                "Keine Treffer",
                                color = PrusaColors.TextMuted,
                                modifier = Modifier.padding(vertical = 18.dp),
                            )
                        }
                        visibleObjects.forEach { obj ->
                            ObjectTreeRow(
                                obj = obj,
                                volumes = volumes[obj.id].orEmpty(),
                                extruderOptions = extruderOptions,
                                isSelected = obj.id in selectedIds,
                                isPrimary = obj.id == selected?.id,
                                onSelect = { onSelect(obj.id) },
                                onToggleSelection = {
                                    onToggleSelection(obj.id)
                                },
                                onDelete = { service.removeObject(obj.id) },
                                onObjectExtruder = {
                                    service.setObjectExtruder(obj.id, it)
                                },
                                onVolumeExtruder = { volume, extruder ->
                                    service.setVolumeExtruder(obj.id, volume, extruder)
                                },
                            )
                        }
                        selected?.let {
                            Button(
                                onClick = { section = InspectorSection.TRANSFORM },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrusaColors.PanelRaised,
                                    contentColor = PrusaColors.TextPrimary,
                                ),
                            ) {
                                Icon(
                                    Icons.Default.OpenWith,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    advancedText("Edit selection", "Auswahl bearbeiten"),
                                    modifier = Modifier.padding(start = 10.dp),
                                    fontSize = 15.sp,
                                )
                            }
                        }
                        toolMessage?.let { message ->
                            Text(
                                message,
                                color = PrusaColors.Orange,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { service.clearToolMessage() }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                InspectorSection.TRANSFORM -> selected?.let { obj ->
                    Text(
                        obj.name.ifBlank { "Objekt ${obj.id}" },
                        color = PrusaColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ObjectPanel(
                        service = service,
                        obj = obj,
                        beds = beds,
                        gizmo = gizmo,
                        onGizmoChange = onGizmoChange,
                        scaleToolActive = scaleTool,
                        onScaleToolChange = onScaleToolChange,
                    )
                }

                InspectorSection.TOOLS -> {
                    GeometryTools(
                        service = service,
                        selected = selected,
                        volumes = selected?.let {
                            volumes[it.id].orEmpty()
                        }.orEmpty(),
                        extruderOptions = extruderOptions,
                        surfaceMode = surfaceMode,
                        measureText = measureText,
                        onSurfaceMode = onSurfaceMode,
                        onExportPlate = onExportPlate,
                        onRepairStl = onRepairStl,
                        onConvertGcode = onConvertGcode,
                        onAddSvg = onAddSvg,
                        onOpenLayerEditor = { layerEditorObjectId = it },
                    )
                }
            }
        }

        ProgressBlock(progress)

        Button(
            onClick = { if (isRunning) service.cancelSlice() else service.startSlice() },
            enabled = objects.isNotEmpty() || isRunning,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) PrusaColors.PanelRaised else PrusaColors.Orange,
                contentColor = PrusaColors.TextPrimary,
            ),
        ) {
            Text(
                if (isRunning) PsUi.appText("Cancel", "Abbrechen")
                else PsUi.appText("Slice now", "Jetzt slicen"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }

        // An `progress` haengen statt an einem eigenen Zustand: nach
        // "Bett leeren" faellt progress auf Idle zurueck, damit
        // verschwinden Senden und Export mit. Befund B5.
        if (progress is SlicerService.Progress.Done && linkPrinters.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                val only = linkPrinters.singleOrNull()
                Button(
                    onClick = {
                        if (only != null)
                            service.sendToPrinter(only, false)
                        else
                            sendMenu = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrusaColors.PanelRaised,
                        contentColor = PrusaColors.TextPrimary,
                    ),
                ) {
                    Text(
                        only?.let { "An ${it.name} senden" }
                            ?: advancedText("Choose target printer", "Zieldrucker auswählen")
                    )
                }
                DropdownMenu(
                    expanded = sendMenu,
                    onDismissRequest = { sendMenu = false },
                ) {
                    linkPrinters.forEach { printer ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(printer.name, color = PrusaColors.TextPrimary)
                                    Text(
                                        printer.host,
                                        color = PrusaColors.TextMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            },
                            onClick = {
                                sendMenu = false
                                service.sendToPrinter(printer, false)
                            },
                        )
                    }
                }
            }
        }

        sendState?.let {
            Text(it, color = PrusaColors.TextMuted, fontSize = 12.sp,
                 modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }

        if (progress is SlicerService.Progress.Done) {
            OutlinedButton(
                onClick = { service.shareableGcodeUri()?.let(onShare) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text("G-Code exportieren", Modifier.padding(start = 8.dp))
            }
        }
    }

    pendingPresetSwitch?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingPresetSwitch = null },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text("Ungespeicherte Profiländerungen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${pending.changes.size} Werte wurden geändert. " +
                            "Was soll beim Wechsel zu „${pending.target}“ passieren?"
                    )
                    pending.changes.take(5).forEach { change ->
                        Text(
                            "• ${change.key}: ${change.was.ifBlank { "—" }} → " +
                                change.now.ifBlank { "—" },
                            color = PrusaColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (pending.changes.size > 5) {
                        Text(
                            "… und ${pending.changes.size - 5} weitere",
                            color = PrusaColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        service.selectPresetKeeping(
                            pending.type,
                            pending.target,
                            pending.changes,
                        )
                        pendingPresetSwitch = null
                    },
                ) { Text("Auf Ziel übertragen") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            savePresetFor = pending.type
                            savePresetName = when (pending.type) {
                                PsmCore.PresetType.PRINTER -> presets.selectedPrinter
                                PsmCore.PresetType.PRINT -> presets.selectedPrint
                                PsmCore.PresetType.FILAMENT -> presets.selectedFilament
                            } + " – Eigen"
                            pendingPresetSwitch = null
                        },
                    ) { Text("Speichern unter…") }
                    TextButton(
                        onClick = {
                            service.discardPresetChangesAndSelect(
                                pending.type,
                                pending.target,
                            )
                            pendingPresetSwitch = null
                        },
                    ) {
                        Text("Verwerfen", color = PrusaColors.Danger)
                    }
                }
            },
        )
    }

    savePresetFor?.let { type ->
        AlertDialog(
            onDismissRequest = { savePresetFor = null },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text("Eigenes Profil speichern") },
            text = {
                OutlinedTextField(
                    value = savePresetName,
                    onValueChange = { savePresetName = it },
                    singleLine = true,
                    label = { Text("Profilname") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = savePresetName.isNotBlank(),
                    onClick = {
                        service.savePresetAs(type, savePresetName)
                        savePresetFor = null
                    },
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { savePresetFor = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@Composable
private fun InspectorTabs(
    selected: InspectorSection,
    transformEnabled: Boolean,
    onSelect: (InspectorSection) -> Unit,
) {
    val tabs = listOf(
        InspectorSection.PROFILES to PsUi.appText("Profiles", "Profile"),
        InspectorSection.OBJECTS to PsUi.appText("Objects", "Objekte"),
        InspectorSection.TRANSFORM to PsUi.appText("Edit", "Bearbeiten"),
        InspectorSection.TOOLS to PsUi.appText("Tools", "Werkzeuge"),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrusaColors.Background.copy(alpha = 0.62f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (section, label) ->
            val enabled =
                (section != InspectorSection.TRANSFORM &&
                    section != InspectorSection.TOOLS) ||
                    transformEnabled
            val active = section == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) PrusaColors.Orange else Color.Transparent)
                    .clickable(enabled = enabled && !active) { onSelect(section) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when {
                        active -> Color.White
                        enabled -> PrusaColors.TextPrimary
                        else -> PrusaColors.TextMuted.copy(alpha = 0.45f)
                    },
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Kombifeld im Slicer-Stil.
 *
 * Kein ExposedDropdownMenu von Material: das rendert ein Textfeld mit
 * Tastaturfokus, was hier nur stoert. Eine flache Flaeche mit Menue
 * trifft die Desktop-Optik besser und ist mit 48 dp gut treffbar.
 */
internal fun filterPresetOptions(options: List<String>, query: String): List<String> =
    options.filter { it.contains(query.trim(), ignoreCase = true) }

/** The visual chooser intentionally stays short; its full result list scrolls in settings. */
internal fun advancedFilamentCards(options: List<String>, query: String): List<String> =
    filterPresetOptions(options, query).take(12)

/**
 * Presetauswahl mit einer suchbaren, modal verankerten Liste.
 *
 * Das fruehere [DropdownMenu] war auf einem Tablet nur an das einzelne
 * Feld gebunden: lange Drucker- oder Filamentnamen wurden so riesig und
 * ueberdeckten die nachfolgenden Profilfelder. Ein Dialog hat eine feste,
 * fingerfreundliche Begrenzung und bleibt bei Drehung des Geraets stabil.
 */
@Composable
private fun PresetCombo(
    title: String,
    options: List<String>,
    selected: String,
    dirtyCount: Int = 0,
    onEdit: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember(title) { mutableStateOf("") }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (dirtyCount > 0) 60.dp else 52.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(9.dp))
                .clickable(enabled = options.isNotEmpty()) {
                    query = ""
                    pickerOpen = true
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected.ifBlank { "—" },
                    color = PrusaColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
                if (dirtyCount > 0) {
                    Text(
                        advancedText(
                            "$dirtyCount unsaved change" + if (dirtyCount == 1) "" else "s",
                            "$dirtyCount ungespeicherte Änderung" + if (dirtyCount == 1) "" else "en",
                        ),
                        color = PrusaColors.Orange,
                        maxLines = 1,
                        fontSize = 11.sp,
                    )
                }
            }
            Text("▾", color = PrusaColors.TextMuted)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) { PsIcon("cog.svg", Modifier.size(20.dp)) }
        }

    }

    if (pickerOpen) {
        val matches = filterPresetOptions(options, query)
        // Eine feste Obergrenze von 320 dp liess auf einem Tablet die
        // untere Haelfte des Dialogs leer, obwohl weitere Profile da
        // waren. PSMobileTheme staucht die Dichte, deshalb muss die
        // gemeldete Bildschirmhoehe erst zurueckgerechnet werden.
        val configuration = LocalConfiguration.current
        val logicalHeightDp = configuration.screenHeightDp /
            uiScaleFor(configuration.screenWidthDp, configuration.screenHeightDp)
        val listMaxHeight = (logicalHeightDp * 0.55f).dp
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(title, color = PrusaColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(advancedText("Search", "Suchen")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PrusaColors.TextPrimary,
                            unfocusedTextColor = PrusaColors.TextPrimary,
                            focusedLabelColor = PrusaColors.Orange,
                            unfocusedLabelColor = PrusaColors.TextMuted,
                            focusedContainerColor = PrusaColors.PanelRaised,
                            unfocusedContainerColor = PrusaColors.PanelRaised,
                        ),
                    )
                    Column(
                        Modifier.heightIn(max = listMaxHeight).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (matches.isEmpty()) {
                            Text(advancedText("No matching profiles", "Keine passenden Profile"), color = PrusaColors.TextMuted)
                        }
                        matches.forEach { name ->
                            FilterChip(
                                selected = name == selected,
                                onClick = {
                                    pickerOpen = false
                                    onSelect(name)
                                },
                                label = {
                                    Text(
                                        name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = PrusaColors.PanelRaised,
                                    labelColor = PrusaColors.TextPrimary,
                                    selectedContainerColor = PrusaColors.PanelRaised,
                                    selectedLabelColor = PrusaColors.Orange,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = name == selected,
                                    borderColor = PrusaColors.Divider,
                                    selectedBorderColor = PrusaColors.Orange,
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(advancedText("Done", "Fertig"), color = PrusaColors.Orange)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectTreeRow(
    obj: PsmCore.ObjectInfo,
    volumes: List<PsmCore.VolumeInfo>,
    extruderOptions: List<ExtruderChoice>,
    isSelected: Boolean,
    isPrimary: Boolean,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit,
    onObjectExtruder: (Int) -> Unit,
    onVolumeExtruder: (Int, Int) -> Unit,
) {
    var expanded by remember(obj.id) { mutableStateOf(volumes.size > 1) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) PrusaColors.Orange.copy(alpha = 0.18f)
                else PrusaColors.PanelRaised
            )
            .then(
                if (isPrimary)
                    Modifier.border(
                        1.dp, PrusaColors.Orange, RoundedCornerShape(10.dp)
                    )
                else Modifier
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = onToggleSelection,
                )
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = volumes.isNotEmpty()) { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (volumes.isEmpty()) "•" else if (expanded) "▾" else "›",
                    color = PrusaColors.TextMuted,
                    fontSize = 18.sp,
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.size(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    obj.name.ifBlank { "Objekt ${obj.id}" },
                    color = PrusaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "%.0f × %.0f × %.0f mm · ${volumes.size} Vol.".format(
                        obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third
                    ),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                )
                if (obj.outsideBed) {
                    Text("außerhalb des Bettes", color = PrusaColors.Danger, fontSize = 11.sp)
                }
            }
            if (extruderOptions.size > 1 || obj.extruder > 0) {
                ExtruderPicker(
                    selected = obj.extruder,
                    options = extruderOptions,
                    inheritedLabel = "Standard",
                    onSelect = onObjectExtruder,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Entfernen",
                    tint = PrusaColors.TextMuted,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        if (expanded) {
            volumes.forEach { volume ->
                HorizontalDivider(color = PrusaColors.Divider.copy(alpha = 0.65f))
                VolumeTreeRow(
                    volume = volume,
                    extruderOptions = extruderOptions,
                    onExtruder = { onVolumeExtruder(volume.index, it) },
                )
            }
        }
    }
}

@Composable
private fun VolumeTreeRow(
    volume: PsmCore.VolumeInfo,
    extruderOptions: List<ExtruderChoice>,
    onExtruder: (Int) -> Unit,
) {
    val typeLabel = when (volume.type) {
        PsmCore.VolumeType.MODEL_PART -> "Bauteil"
        PsmCore.VolumeType.NEGATIVE -> "Negativvolumen"
        PsmCore.VolumeType.MODIFIER -> "Modifikator"
        PsmCore.VolumeType.SUPPORT_BLOCKER -> "Stützblocker"
        PsmCore.VolumeType.SUPPORT_ENFORCER -> "Stützverstärker"
        PsmCore.VolumeType.UNKNOWN -> "Volumen"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (volume.type == PsmCore.VolumeType.MODEL_PART)
                        PrusaColors.Orange else PrusaColors.TextMuted
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                volume.name.ifBlank { "$typeLabel ${volume.index + 1}" },
                color = PrusaColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$typeLabel · ${volume.triangles} Dreiecke",
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        if (volume.type == PsmCore.VolumeType.MODEL_PART &&
            (extruderOptions.size > 1 || volume.explicitExtruder > 0)
        ) {
            ExtruderPicker(
                selected = volume.explicitExtruder,
                options = extruderOptions,
                inheritedLabel = "Vom Objekt",
                onSelect = onExtruder,
            )
        }
    }
}

@Composable
private fun ExtruderPicker(
    selected: Int,
    options: List<ExtruderChoice>,
    inheritedLabel: String,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.Background.copy(alpha = 0.55f))
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (selected > 0) "${options.firstOrNull { it.id == selected }?.label ?: "E$selected"} ▾" else "Auto ▾",
                color = if (selected > 0) PrusaColors.Orange else PrusaColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PrusaColors.PanelRaised),
        ) {
            DropdownMenuItem(
                text = { Text(inheritedLabel, color = PrusaColors.TextPrimary) },
                onClick = {
                    expanded = false
                    onSelect(0)
                },
            )
            options.forEach { extruder ->
                DropdownMenuItem(
                    text = {
                        Text(
                            extruder.label,
                            color = if (selected == extruder.id)
                                PrusaColors.Orange else PrusaColors.TextPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(extruder.id)
                    },
                )
            }
        }
    }
}

internal data class ExtruderChoice(val id: Int, val label: String)

@Composable
private fun ProgressBlock(progress: SlicerService.Progress) {
    when (progress) {
        is SlicerService.Progress.Idle -> Unit

        is SlicerService.Progress.Running -> Column(Modifier.fillMaxWidth()) {
            // Phase mitzeigen, nicht nur Prozent - auf dem Tablet kann ein
            // Slice mehrere Minuten dauern.
            Text(progress.stage, color = PrusaColors.TextPrimary, fontSize = 12.sp,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = PrusaColors.Orange,
                trackColor = PrusaColors.PanelRaised,
            )
        }

        is SlicerService.Progress.Done -> {
            val st = progress.stats
            Column(Modifier.fillMaxWidth()) {
                Text(advancedText("Done in %.1f s", "Fertig in %.1f s").format(progress.seconds),
                     color = PrusaColors.Ok, fontSize = 12.sp)
                if (st != null) {
                    Text(
                        buildString {
                            append("%d Layer · %s · %.2f m".format(
                                st.layers, formatDuration(st.printTimeSeconds),
                                st.filamentMm / 1000.0))
                            if (st.filamentGrams > 0.0) append(" · %.1f g".format(st.filamentGrams))
                        },
                        color = PrusaColors.TextMuted, fontSize = 12.sp,
                    )
                }
            }
        }

        is SlicerService.Progress.Failed -> Text(
            progress.message, color = PrusaColors.Danger, fontSize = 12.sp, maxLines = 3,
        )

        is SlicerService.Progress.Cancelled -> Text(
            "abgebrochen", color = PrusaColors.TextMuted, fontSize = 12.sp,
        )

        is SlicerService.Progress.Stale -> Text(
            "Projekt wurde während des Slicens geändert – erneut slicen",
            color = PrusaColors.Orange, fontSize = 12.sp, maxLines = 2,
        )
    }
    Spacer(Modifier.height(2.dp))
}
