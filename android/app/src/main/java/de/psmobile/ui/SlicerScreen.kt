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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import de.psmobile.shared.rules.BedInput
import de.psmobile.shared.rules.BedStripContract
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
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

internal fun advancedText(english: String, german: String): String = PsUi.appText(english, german)

internal enum class InspectorSection {
    PROFILES,
    OBJECTS,
    TRANSFORM,
    TOOLS,
}

internal data class PendingPresetSwitch(
    val type: PsmCore.PresetType,
    val target: String,
    val changes: List<PsmCore.Change>,
)

@Composable
fun SlicerScreen(
    service: SlicerService?,
    onOpenSimple: () -> Unit,
    onHome: () -> Unit,
    onAppSettings: () -> Unit,
    onPickFile: (List<android.net.Uri>) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    usbTarget: String?,
    onExportToUsb: () -> Unit,
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
    onControllerReady: (SceneController) -> Unit = {},
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
        onHome = onHome,
        onAppSettings = onAppSettings,
        onPickFile = onPickFile,
        onShare = onShare,
        usbTarget = usbTarget,
        onExportToUsb = onExportToUsb,
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
        onControllerReady = onControllerReady,
    )
}

@Composable
private fun SlicerContent(
    service: SlicerService,
    onOpenSimple: () -> Unit,
    onHome: () -> Unit,
    onAppSettings: () -> Unit,
    onPickFile: (List<android.net.Uri>) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    usbTarget: String?,
    onExportToUsb: () -> Unit,
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
    onControllerReady: (SceneController) -> Unit = {},
) {
    // Mehrere Dateien auf einmal: eine Baugruppe besteht selten aus
    // genau einem Teil, und der Umweg ueber sechs einzelne Auswahlen
    // ist auf einem Tablet besonders muehsam.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPickFile(uris) }

    val objects by service.objects.collectAsState()
    val beds by service.beds.collectAsState()
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
    LaunchedEffect(sceneController) { onControllerReady(sceneController) }
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
    val showColorMix = screen is SlicerService.Screen.ColorMix
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sendState by service.sendState.collectAsState()
    var linkPrinters by remember { mutableStateOf(de.psmobile.net.PrinterStore.all(ctx)) }
    var confirmNewProject by remember { mutableStateOf(false) }
    var confirmReloadProject by remember { mutableStateOf(false) }
    var confirmLeaveProject by remember { mutableStateOf(false) }

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
                    onToggleBedLock = service::toggleBedLock,
                    onRenameBed = service::renameBed,
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
                    onHome = {
                        if (service.hasUnsavedChanges) confirmLeaveProject = true else onHome()
                    },
        onAppSettings = onAppSettings,
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
                                                advancedText("First measuring point set · tap the second point", "Erster Messpunkt gesetzt · zweiten Punkt antippen")
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
                    usbTarget = usbTarget,
                    onExportToUsb = onExportToUsb,
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
                usbTarget = usbTarget,
                onExportToUsb = onExportToUsb,
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

    if (confirmLeaveProject) {
        AlertDialog(
            onDismissRequest = { confirmLeaveProject = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(advancedText("Unsaved changes", "Ungesicherte Änderungen")) },
            text = {
                Text(
                    advancedText(
                        "A second tap on a mode would discard this project.",
                        "Ein erneutes Tippen auf einen Modus würde dieses Projekt verwerfen.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeaveProject = false
                    onSaveProject()
                    onHome()
                }) { Text(advancedText("Save", "Sichern")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeaveProject = false }) {
                        Text(advancedText("Cancel", "Abbrechen"))
                    }
                    TextButton(onClick = {
                        confirmLeaveProject = false
                        onHome()
                    }) { Text(advancedText("Discard", "Verwerfen"), color = PrusaColors.Danger) }
                }
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
internal fun MaterialPickerButton(selected: String, onClick: () -> Unit) {
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
internal fun ExtruderBank(
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
internal fun formatDuration(seconds: Double): String {
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
        ScaledOverlay {
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
                advancedText("Layer heights active", "Schichthöhen aktiv"),
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
                        advancedText("on the selected model", "am ausgewählten Modell"),
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
// internal, weil der Simple Mode denselben Regler benutzt - die
// Vorschau soll sich in beiden Modi gleich bedienen lassen.
internal fun LayerSlider(
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
    onToggleBedLock: (Int) -> Unit,
    onRenameBed: (Int, String) -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onReload: (() -> Unit)?,
    onOpenSimple: () -> Unit,
    onHome: () -> Unit,
    onAppSettings: () -> Unit,
    actionsEnabled: Boolean,
    showInspectorAction: Boolean,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    tight: Boolean = false,
) {
    // Flaches Icon-ueber-Beschriftung-Werkzeugband statt gefuellter
    // Knopf-Kacheln - Gegenstueck zu `werkzeugleiste`/`werkzeug()` in
    // AdvancedWorkspaceView.swift (iOS). Ein echtes iPad-Foto von Nils
    // zeigte: iOS' Knoepfe haben ueberhaupt keinen Hintergrund, nur
    // Symbol+kleine Beschriftung - Androids vorige Fassung fuellte
    // jeden Knopf als eigene Kachel, was neben iOS "zu fett" wirkte
    // (Nils' Feedback zum Startbildschirm-Redesign galt hier genauso).
    // Die Bettauswahl steht dafuer jetzt in einer eigenen Zeile
    // darunter, wie bei iOS (dort eigene Zeile, nicht in derselben
    // Reihe wie die Werkzeuge).
    Column(Modifier.fillMaxWidth().background(PrusaColors.Panel)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (tight) 4.dp else 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bislang gab es aus dem Advanced Mode gar keinen Weg zurueck
            // zur Startseite (anders als "Simple", das nur den Modus
            // wechselt) - dieselbe Luecke wie bei Simple Mode vor dem
            // Kopfzeilen-Fix.
            WerkzeugKnopf(Icons.Default.Home, advancedText("Start", "Start"), enabled = actionsEnabled, onClick = onHome)
            WerkzeugTrenner()
            WerkzeugKnopf(Icons.Default.InsertDriveFile, advancedText("New", "Neu"), enabled = actionsEnabled, onClick = onNew)
            WerkzeugKnopf(Icons.Default.Save, advancedText("Save", "Sichern"), enabled = actionsEnabled, onClick = onSave)
            WerkzeugKnopf(Icons.Default.SaveAs, advancedText("Save as", "Sichern unter"), enabled = actionsEnabled, onClick = onSaveAs)
            if (onReload != null) {
                WerkzeugKnopf(Icons.Default.Refresh, advancedText("Reload", "Neu laden"), enabled = actionsEnabled, onClick = onReload)
            }
            WerkzeugTrenner()
            WerkzeugKnopf(Icons.Default.Bolt, "Simple", enabled = actionsEnabled, onClick = onOpenSimple)
            // Wer den Startmodus fest eingestellt hat, sieht die
            // Startseite nie wieder - ohne diesen Weg waere die
            // Einstellung nicht mehr erreichbar, die ihn dorthin
            // gebracht hat.
            WerkzeugKnopf(Icons.Default.Settings, advancedText("Settings", "Einstellungen"), enabled = actionsEnabled, onClick = onAppSettings)
            Spacer(Modifier.weight(1f))
            if (showInspectorAction) {
                WerkzeugKnopf(
                    Icons.Default.GridView,
                    "Panel",
                    enabled = true,
                    active = inspectorOpen,
                    onClick = onToggleInspector,
                )
            }
        }
        HorizontalDivider(color = PrusaColors.Divider)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (tight) 6.dp else 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BedSelector(
                beds = beds,
                onSelect = onSelectBed,
                onAdd = onAddBed,
                onRemove = onRemoveBed,
                onToggleLock = onToggleBedLock,
                onRename = onRenameBed,
                schmal = tight,
            )
        }
    }
}

/**
 * Ein Werkzeugband-Knopf: Symbol ueber kleiner Beschriftung, kein
 * eigener Hintergrund - Gegenstueck zu `werkzeug()` in
 * AdvancedWorkspaceView.swift (iOS).
 */
@Composable
private fun WerkzeugKnopf(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val farbe = when {
        active -> PrusaColors.Orange
        enabled -> PrusaColors.TextPrimary
        else -> PrusaColors.TextMuted.copy(alpha = 0.4f)
    }
    Column(
        Modifier
            .width(56.dp)
            .heightIn(min = 46.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = farbe, modifier = Modifier.size(18.dp))
        Text(label, color = farbe, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun WerkzeugTrenner() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .padding(horizontal = 0.dp)
            .background(PrusaColors.Divider),
    )
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
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun BedSelector(
    beds: List<PsmCore.Bed>,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onToggleLock: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    schmal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val strip = AndroidBedStripAdapter.state(beds.map { bed ->
        AndroidBedSnapshot(bed.index, bed.name, bed.locked, bed.objectCount,
            bed.instanceCount, bed.active)
    }, advancedText("Bed", "Bett"))
    val active = strip.items.first { it.active }
    var renameId by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(advancedText("Rename bed", "Bett umbenennen")) },
            text = { androidx.compose.material3.OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                onRename(id, renameText); renameId = null
            }) { Text(advancedText("Save", "Speichern")) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renameId = null }) {
                Text(advancedText("Cancel", "Abbrechen"))
            } },
        )
    }

    if (schmal) {
        var zeigeListe by remember { mutableStateOf(false) }
        Row(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.PanelRaised)
                .clickable { zeigeListe = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                if (active.locked) Icons.Default.Lock else Icons.Default.Layers,
                contentDescription = null,
                tint = PrusaColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
            Column {
                Text(
                    active.name,
                    color = PrusaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    advancedText("${active.objectCount} objects", "${active.objectCount} Objekte"),
                    color = PrusaColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.weight(1f, fill = false))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = PrusaColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        if (zeigeListe) {
            ModalBottomSheet(onDismissRequest = { zeigeListe = false }, containerColor = PrusaColors.Background) {
                ScaledOverlay {
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        advancedText("Beds", "Betten"),
                        color = PrusaColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    strip.items.forEach { bett ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (bett.active) PrusaColors.Orange else PrusaColors.PanelRaised)
                                .clickable { onSelect(bett.id); zeigeListe = false }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (bett.locked) Icons.Default.Lock else Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (bett.active) PrusaColors.Background else PrusaColors.TextPrimary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bett.name,
                                    color = if (bett.active) PrusaColors.Background else PrusaColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    advancedText("${bett.objectCount} objects", "${bett.objectCount} Objekte"),
                                    color = if (bett.active) PrusaColors.Background.copy(alpha = 0.7f) else PrusaColors.TextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = { onToggleLock(bett.id) }) {
                                Icon(
                                    if (bett.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = advancedText("Lock bed", "Bett sperren"),
                                    tint = if (bett.active) PrusaColors.Background else PrusaColors.TextMuted,
                                )
                            }
                            IconButton(onClick = { renameId = bett.id; renameText = bett.name }) {
                                Icon(Icons.Default.Edit, contentDescription = advancedText("Rename bed", "Bett umbenennen"))
                            }
                            if (bett.canRemove) {
                                IconButton(onClick = { onRemove(bett.id) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = advancedText("Remove bed", "Bett entfernen"),
                                        tint = if (bett.active) PrusaColors.Background else PrusaColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = { onAdd(); zeigeListe = false },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(advancedText("Add bed", "Bett hinzufügen"))
                    }
                }
                }
            }
        }
        return
    }

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PrusaColors.Background.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strip.items.forEach { bed ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (bed.active) PrusaColors.Orange else PrusaColors.PanelRaised),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Text(
                "${bed.name} · ${bed.objectCount}",
                color = if (bed.active) Color.White else PrusaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (bed.active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = true,
                        onClick = { if (!bed.active) onSelect(bed.id) },
                        onLongClick = { renameId = bed.id; renameText = bed.name },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            IconButton(onClick = { onToggleLock(bed.id) }, modifier = Modifier.size(44.dp)) {
                Icon(if (bed.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = advancedText("Toggle bed lock", "Bettsperre umschalten"),
                    tint = if (bed.active) Color.White else PrusaColors.TextMuted)
            }
            IconButton(onClick = { renameId = bed.id; renameText = bed.name }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Edit, contentDescription = advancedText("Rename bed", "Bett umbenennen"),
                    tint = if (bed.active) Color.White else PrusaColors.TextMuted)
            }
            }
        }

        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.PanelRaised)
                .clickable(enabled = strip.canAdd, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = advancedText("Add print bed", "Druckbett hinzufügen"),
                 tint = PrusaColors.TextPrimary)
        }

        if (active.canRemove) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrusaColors.PanelRaised)
                .clickable { onRemove(active.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, contentDescription = advancedText("Remove active print bed", "Aktives Druckbett entfernen"),
                     tint = PrusaColors.TextMuted)
            }
        }
    }
}
