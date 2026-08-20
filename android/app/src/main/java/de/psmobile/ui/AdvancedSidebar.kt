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
import de.psmobile.shared.rules.FilamentCatalog
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.psTouch
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
 * Seitenleiste und Inspektor des Advanced Mode.
 *
 * Herausgeloest aus SlicerScreen.kt: die Datei trug mit 3172 Zeilen
 * den Bildschirmaufbau, den Gizmo-Zustand, die Einstellungsdialoge
 * und den Slice-Ablauf gleichzeitig. Das iOS-Gegenstueck kommt mit
 * gut der Haelfte aus - und genau in so einer Datei verstecken sich
 * die Unterschiede zwischen den beiden Apps.
 *
 * Verschoben wurde nur, nichts umgeschrieben: dasselbe Paket,
 * dieselben Funktionen. Was ueber die Dateigrenze hinweg gebraucht
 * wird, ist von private auf internal gehoben.
 */
/* ------------------------------------------------------------------ */
/* Seitenleiste                                                        */
/* ------------------------------------------------------------------ */

@Composable
internal fun Sidebar(
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
    usbTarget: String?,
    onExportToUsb: () -> Unit,
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
    // Vier Bereiche untereinander, nicht vier Reiter. Der Grund steht
    // auf iOS im Quelltext und gilt hier genauso: vier Reiter heissen,
    // dass drei Viertel des Gesuchten unsichtbar sind. So sieht man alle
    // Ueberschriften und klappt auf, was man gerade braucht.
    var offeneBereiche by remember {
        mutableStateOf(setOf(InspectorSection.PROFILES))
    }
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

    // Nils' Wunsch: rechts automatisch das Passende zeigen - bei
    // ausgewaehltem Objekt die objektbezogenen Einstellungen (Edit),
    // ohne Auswahl die plattenbezogenen (Profiles: Drucker/Filament/
    // Druckprofil). iOS macht das noch nicht (dort bleibt der Reiter
    // stehen, bis man selbst wechselt) - hier bewusst vorausgegangen,
    // sollte fuer Gleichstand auch nach iOS uebernommen werden.
    //
    // TOOLS wird beim Auswaehlen nicht angetastet: wer gerade malt und
    // ein anderes Objekt antippt, wird nicht aus dem Werkzeug gerissen.
    LaunchedEffect(selected?.id) {
        offeneBereiche = if (selected == null) {
            // Ohne Auswahl haben Bearbeiten und Werkzeuge nichts zu
            // zeigen - zu, aber sichtbar, damit man weiss, dass es sie
            // gibt.
            offeneBereiche - InspectorSection.TRANSFORM - InspectorSection.TOOLS
        } else {
            offeneBereiche + InspectorSection.TRANSFORM
        }
    }

    LaunchedEffect(filamentPickerIndex) {
        inspectorScroll.scrollTo(0)
    }

    LaunchedEffect(offeneBereiche, selected?.id) {
        if (InspectorSection.TOOLS !in offeneBereiche ||
            layerEditorObjectId != selected?.id) {
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
            Modifier.fillMaxWidth().height(psTouch(48)),
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
                        .clip(RoundedCornerShape(Corners.CARD.dp))
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

        // Die drei Einstellungsseiten stehen oben im Band, nicht in
        // einem der Bereiche: sie wirken auf das Profil, und das Profil
        // steht rechts. Oben in die Werkzeugleiste gehoert, was auf den
        // Viewport wirkt. Wortgleich zu `einstellungsbereiche` in
        // AdvancedWorkspaceView.swift.
        EinstellungsZeile(
            PsUi.tr("Print Settings"),
            onClick = { onOpenSettings("print") },
        )
        EinstellungsZeile(
            PsUi.tr("Filament Settings"),
            onClick = { onOpenSettings("filament") },
        )
        EinstellungsZeile(
            PsUi.tr("Printer Settings"),
            onClick = { onOpenSettings("printer") },
        )
        HorizontalDivider(color = PrusaColors.Divider)

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
            Bereich(
                titel = PsUi.appText("Profiles", "Profile"),
                offen = InspectorSection.PROFILES in offeneBereiche,
                moeglich = true,
                onToggle = { offeneBereiche = umschalten(offeneBereiche, InspectorSection.PROFILES) },
            ) {
                    val materialHead = filamentPickerIndex
                    if (materialHead != null) {
                        // Same composable as Simple Mode, deliberately kept in this
                        // sidebar composition so its device scale stays identical.
                        SimpleMaterialChooser(
                            katalog = service.filamentCatalog(),
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
                        modifier = Modifier.fillMaxWidth().height(psTouch(52)),
                        shape = RoundedCornerShape(Corners.CARD.dp),
                    ) {
                        Text(
                            advancedText("Manage printers", "Drucker verwalten"),
                            color = PrusaColors.TextPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    if (presets.extruders.size >= 2) {
                        OutlinedButton(
                            onClick = { service.showScreen(SlicerService.Screen.ColorMix) },
                            modifier = Modifier.fillMaxWidth().height(psTouch(52)),
                            shape = RoundedCornerShape(Corners.CARD.dp),
                        ) {
                            Text("ColorMix", color = PrusaColors.TextPrimary, fontSize = 14.sp)
                        }
                    }
                    }
                }

            Bereich(
                titel = PsUi.appText("Objects", "Objekte"),
                offen = InspectorSection.OBJECTS in offeneBereiche,
                moeglich = true,
                onToggle = { offeneBereiche = umschalten(offeneBereiche, InspectorSection.OBJECTS) },
            ) {
                    if (objects.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(Corners.CARD.dp))
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
                                        advancedText("Import via + in the toolbar", "Über + in der Werkzeugleiste importieren"),
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
                            modifier = Modifier.fillMaxWidth().heightIn(min = psTouch(56)),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onSelectAll,
                                modifier = Modifier.height(psTouch(48)),
                            ) { Text(PsUi.appText("Select all", "Alle auswählen")) }
                            TextButton(
                                onClick = onClearSelection,
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.height(psTouch(48)),
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
                                advancedText("No matches", "Keine Treffer"),
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
                                onClick = {
                                    offeneBereiche = offeneBereiche + InspectorSection.TRANSFORM
                                },
                                modifier = Modifier.fillMaxWidth().height(psTouch(54)),
                                shape = RoundedCornerShape(Corners.CARD.dp),
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

            Bereich(
                titel = PsUi.appText("Edit", "Bearbeiten"),
                offen = InspectorSection.TRANSFORM in offeneBereiche,
                moeglich = selected != null,
                onToggle = { offeneBereiche = umschalten(offeneBereiche, InspectorSection.TRANSFORM) },
            ) { selected?.let { obj ->
                    Text(
                        obj.name.ifBlank { advancedText("Object ${obj.id}", "Objekt ${obj.id}") },
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
                } }

            Bereich(
                titel = PsUi.appText("Tools", "Werkzeuge"),
                offen = InspectorSection.TOOLS in offeneBereiche,
                moeglich = selected != null,
                onToggle = { offeneBereiche = umschalten(offeneBereiche, InspectorSection.TOOLS) },
            ) {
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

        ProgressBlock(progress)

        Button(
            onClick = { if (isRunning) service.cancelSlice() else service.startSlice() },
            enabled = objects.isNotEmpty() || isRunning,
            modifier = Modifier.fillMaxWidth().height(psTouch(58)),
            shape = RoundedCornerShape(Corners.CARD.dp),
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

        // Nur sinnvoll, wenn es ueberhaupt etwas zu verteilen gibt.
        // Waehrend des Laufs steht hier, welches Bett gerade dran ist -
        // ohne die zweite Zahl saehe man beim dritten von fuenf Betten
        // dieselben 40 Prozent wie beim ersten und wuesste nicht, warum
        // es wieder von vorn anfaengt.
        if (beds.size > 1) {
            val bettLauf by service.bettFortschritt.collectAsState()
            Text(
                bettLauf?.let { (i, n) ->
                    PsUi.appText("Bed $i/$n", "Bett $i/$n")
                } ?: PsUi.appText("Slice all beds", "Alle Betten slicen"),
                color = PrusaColors.Orange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corners.CARD.dp))
                    .clickable(enabled = bettLauf == null && !isRunning) {
                        service.startSliceAlleBetten()
                    }
                    .heightIn(min = psTouch(40))
                    .padding(vertical = 11.dp),
            )
        }

        // Nach "alle Betten schneiden" liegen mehrere Dateien vor. Ohne
        // diese Liste entstuenden sie unsichtbar - man saehe nur
        // "Fertig" und haette keinen Weg zu vier der fuenf Dateien.
        // Gegenstueck zu SliceSheet.swift:88ff.
        val gcodeDateien by service.gcodeDateien.collectAsState()
        if (progress is SlicerService.Progress.Done && gcodeDateien.size > 1) {
            Text(
                PsUi.appText(
                    "${gcodeDateien.size} G-code files",
                    "${gcodeDateien.size} G-Code-Dateien",
                ),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
            gcodeDateien.forEach { datei ->
                val ziel = linkPrinters.singleOrNull()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Corners.FIELD.dp))
                        .background(PrusaColors.PanelRaised)
                        .clickable {
                            if (ziel != null) service.sendToPrinter(ziel, false, datei)
                            else service.shareableGcodeUri(datei)?.let(onShare)
                        }
                        .heightIn(min = psTouch(40))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        datei.name,
                        color = PrusaColors.TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (ziel != null) advancedText("Send", "Senden")
                        else advancedText("Export", "Exportieren"),
                        color = PrusaColors.Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
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
                    modifier = Modifier.fillMaxWidth().height(psTouch(52)),
                    shape = RoundedCornerShape(Corners.CARD.dp),
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
                    ScaledOverlay {
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
        }

        sendState?.let {
            Text(it, color = PrusaColors.TextMuted, fontSize = 12.sp,
                 modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }

        if (progress is SlicerService.Progress.Done) {
            OutlinedButton(
                onClick = { service.shareableGcodeUri()?.let(onShare) },
                modifier = Modifier.fillMaxWidth().height(psTouch(52)),
                shape = RoundedCornerShape(Corners.CARD.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text(advancedText("Export G-code", "G-Code exportieren"),
                     Modifier.padding(start = 8.dp))
            }
            // Nur wenn wirklich etwas angeschlossen ist. Ein Knopf, der
            // beim Antippen "kein Stick da" sagt, ist schlechter als
            // keiner.
            usbTarget?.let { label ->
                OutlinedButton(
                    onClick = onExportToUsb,
                    modifier = Modifier.fillMaxWidth().height(psTouch(52)).padding(top = 6.dp),
                    shape = RoundedCornerShape(Corners.CARD.dp),
                ) {
                    Text("⏻", color = PrusaColors.Orange)
                    Text(label, Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    pendingPresetSwitch?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingPresetSwitch = null },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(advancedText("Unsaved profile changes", "Ungespeicherte Profiländerungen")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        advancedText(
                            "${pending.changes.size} values were changed. " +
                                "What should happen when switching to „${pending.target}“?",
                            "${pending.changes.size} Werte wurden geändert. " +
                                "Was soll beim Wechsel zu „${pending.target}“ passieren?",
                        )
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
                            advancedText(
                                "… and ${pending.changes.size - 5} more",
                                "… und ${pending.changes.size - 5} weitere",
                            ),
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
                ) { Text(advancedText("Transfer to target", "Auf Ziel übertragen")) }
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
                    ) { Text(advancedText("Save as…", "Speichern unter…")) }
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
            title = { Text(advancedText("Save custom profile", "Eigenes Profil speichern")) },
            text = {
                OutlinedTextField(
                    value = savePresetName,
                    onValueChange = { savePresetName = it },
                    singleLine = true,
                    label = { Text(advancedText("Profile name", "Profilname")) },
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
                ) { Text(advancedText("Save", "Speichern")) }
            },
            dismissButton = {
                TextButton(onClick = { savePresetFor = null }) {
                    Text(advancedText("Cancel", "Abbrechen"))
                }
            },
        )
    }
}

/**
 * Eine Zeile, die eine Einstellungsseite oeffnet.
 *
 * Gegenstueck zu `einstellungsZeile` in `AdvancedWorkspaceView.swift`.
 * Der Name kommt aus PrusaSlicers eigenem Katalog, nicht von hier.
 */
@Composable
private fun EinstellungsZeile(titel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .clickable(onClick = onClick)
            .heightIn(min = psTouch(44))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titel,
            color = PrusaColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = PrusaColors.TextMuted, fontSize = 15.sp)
    }
}

/**
 * Ein aufklappbarer Bereich des Inspektors.
 *
 * Gegenstueck zu `bereich(_:)` in `AdvancedWorkspaceView.swift`. Ohne
 * Auswahl bleiben *Bearbeiten* und *Werkzeuge* sichtbar, aber gedaempft
 * und zu - verschwaenden sie, waere nicht zu erkennen, dass es sie gibt.
 */
@Composable
private fun Bereich(
    titel: String,
    offen: Boolean,
    moeglich: Boolean,
    onToggle: () -> Unit,
    inhalt: @Composable () -> Unit,
) {
    val sichtbarOffen = offen && moeglich
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .clickable(enabled = moeglich, onClick = onToggle)
                .heightIn(min = psTouch(40))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                titel.uppercase(),
                color = if (moeglich) PrusaColors.TextMuted
                        else PrusaColors.TextMuted.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (sichtbarOffen) "\u25be" else "\u25b8",
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        if (sichtbarOffen) inhalt()
    }
}

/** Einen Bereich auf- oder zuklappen. */
private fun umschalten(
    offen: Set<InspectorSection>,
    bereich: InspectorSection,
): Set<InspectorSection> =
    if (bereich in offen) offen - bereich else offen + bereich

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
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
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
                Modifier.size(44.dp).clip(RoundedCornerShape(Corners.FIELD.dp))
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
            .clip(RoundedCornerShape(Corners.CARD.dp))
            .background(
                if (isSelected) PrusaColors.Orange.copy(alpha = 0.18f)
                else PrusaColors.PanelRaised
            )
            .then(
                if (isPrimary)
                    Modifier.border(
                        1.dp, PrusaColors.Orange, RoundedCornerShape(Corners.CARD.dp)
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
                    .clip(RoundedCornerShape(Corners.FIELD.dp))
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
                    obj.name.ifBlank { advancedText("Object ${obj.id}", "Objekt ${obj.id}") },
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
                    Text(advancedText("outside the bed", "außerhalb des Bettes"), color = PrusaColors.Danger, fontSize = 11.sp)
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
                    .clip(RoundedCornerShape(Corners.FIELD.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    advancedText("Remove", "Entfernen"),
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
        PsmCore.VolumeType.SUPPORT_BLOCKER -> advancedText("Support blocker", "Stützblocker")
        PsmCore.VolumeType.SUPPORT_ENFORCER -> advancedText("Support enforcer", "Stützverstärker")
        PsmCore.VolumeType.UNKNOWN -> "Volumen"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = psTouch(58))
            .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(Corners.FIELD.dp))
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
                inheritedLabel = advancedText("From object", "Vom Objekt"),
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
                .height(psTouch(44))
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(PrusaColors.Background.copy(alpha = 0.55f))
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
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
            ScaledOverlay {
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
            advancedText("Project changed while slicing – slice again", "Projekt wurde während des Slicens geändert – erneut slicen"),
            color = PrusaColors.Orange, fontSize = 12.sp, maxLines = 2,
        )
    }
    Spacer(Modifier.height(2.dp))
}
