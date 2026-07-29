package de.psmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmViewport
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

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

private val SIDEBAR_WIDTH = 340.dp
private val TOOL_SIZE = 56.dp

@Composable
fun SlicerScreen(
    service: SlicerService?,
    onPickFile: (android.net.Uri) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onPickBackupFolder: () -> Unit,
) {
    if (service == null) {
        Box(
            Modifier.fillMaxSize().background(PrusaColors.Background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = PrusaColors.Orange) }
        return
    }
    SlicerContent(service, onPickFile, onShare, onPickBackupFolder)
}

@Composable
private fun SlicerContent(
    service: SlicerService,
    onPickFile: (android.net.Uri) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onPickBackupFolder: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickFile) }

    val objects by service.objects.collectAsState()
    val progress by service.progress.collectAsState()
    val presets by service.presets.collectAsState()
    val sceneRevision by service.sceneRevision.collectAsState()
    val configRevision by service.configRevision.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val sceneController = remember { SceneController() }
    // Navigation liegt im Service, damit ein eingehendes Modell die
    // Ansicht aufs Bett zurueckholen kann - Befund A1.
    val screen by service.screen.collectAsState()
    val settingsTab = (screen as? SlicerService.Screen.Settings)?.tab
    var settingsMode by remember { mutableStateOf(PsmCore.Mode.SIMPLE) }
    val showPrinters = screen is SlicerService.Screen.Printers
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sendState by service.sendState.collectAsState()
    var linkPrinters by remember { mutableStateOf(de.psmobile.net.PrinterStore.all(ctx)) }

    if (showPrinters) {
        PrintersScreen(
            presetNames = presets.printers,
            onClose = {
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
                onTabChange = { service.showScreen(SlicerService.Screen.Settings(it)) },
            )
            return
        }
    }

    val selected = objects.firstOrNull { it.id == selectedId } ?: objects.firstOrNull()

    // Zustand der G-Code-Vorschau. Der Viewport haelt die Werkzeugwege, hier
    // steht nur, was die Bedienelemente davon zeigen muessen.
    // Solange das Skalieren-Werkzeug an ist, greift die Spreizgeste das
    // Objekt statt der Kamera.
    var scaleTool by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var layerCount by remember { mutableStateOf(0) }
    var layerLo by remember { mutableStateOf(0) }
    var layerHi by remember { mutableStateOf(0) }

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

    Row(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ToolStrip(hasSelection = selected != null) { name ->
            // Zuordnung nach den Werkzeugnamen aus GLCanvas3D.cpp
            when (name) {
                "add"       -> picker.launch(arrayOf("*/*"))
                "delete"    -> selected?.let { service.removeObject(it.id) }
                "deleteall" -> service.clearBed()
                "arrange"   -> service.arrange()
                "copy"      -> selected?.let { service.duplicate(it.id) }
                "more"      -> selected?.let { service.duplicate(it.id) }
                "fewer"     -> selected?.let { service.removeObject(it.id) }
            }
        }

        // Echter GLES-Viewport auf Basis der Shader aus PrusaSlicer.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            SceneView(
                core = service.coreOrNull,
                shaderDir = remember(service) { service.shaderDir() },
                selectedId = selected?.id,
                onSelect = { id -> selectedId = if (id >= 0) id else null },
                invalidateKey = sceneRevision,
                controller = sceneController,
                modifier = Modifier.fillMaxSize(),
            )
            ViewBar(
                sceneController,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )

            // Die zwei Reiter des Desktops: "3D editor view" und "Preview".
            ViewModeTabs(
                preview = previewMode,
                previewEnabled = layerCount > 0 || progress is SlicerService.Progress.Done,
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
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )

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
                        .padding(end = 12.dp, top = 56.dp, bottom = 72.dp),
                )
            }
        }

        Sidebar(
            service = service,
            presets = presets,
            objects = objects,
            selected = selected,
            progress = progress,
            onSelect = { selectedId = it },
            onShare = onShare,
            onOpenSettings = { service.showScreen(SlicerService.Screen.Settings(it)) },
            onManagePrinters = { service.showScreen(SlicerService.Screen.Printers) },
            linkPrinters = linkPrinters,
            sendState = sendState,
            scaleTool = scaleTool,
            onScaleToolChange = { on ->
                scaleTool = on
                sceneController.scaleTool = on
            },
            modifier = Modifier.width(SIDEBAR_WIDTH).fillMaxHeight(),
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
    onTool: (String) -> Unit,
) {
    val tools = PsUi.toolbar
    // Welche Werkzeuge ohne Auswahl sinnlos sind - entspricht den
    // enabling_callbacks im Original.
    val needsSelection = setOf("delete", "copy", "more", "fewer",
                               "splitobjects", "splitvolumes", "settings")
    val notYet = setOf("paste", "layersediting", "undo", "redo",
                       "arrangecurrent", "splitobjects", "splitvolumes", "settings")

    Column(
        Modifier
            .width(TOOL_SIZE + 16.dp)
            .fillMaxHeight()
            .background(PrusaColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tools.forEach { tool ->
            val enabled = tool.name !in notYet &&
                          (tool.name !in needsSelection || hasSelection)
            ToolButton(tool, enabled) { onTool(tool.name) }
        }
    }
}

@Composable
private fun ToolButton(tool: PsUi.Tool, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(TOOL_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) PrusaColors.PanelRaised else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PsIcon(
            name = tool.icon,
            modifier = Modifier.size(28.dp).alpha(if (enabled) 1f else 0.3f),
            contentDescription = PsUi.tr(tool.tooltip),
        )
    }
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
    onEdit: () -> Unit,
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
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(parseColor(extruder.color) ?: PrusaColors.PanelRaised)
                .border(
                    1.dp,
                    if (extruder.color.isEmpty()) PrusaColors.Divider else Color.White.copy(alpha = 0.45f),
                    RoundedCornerShape(4.dp),
                )
                .clickable { pickColor = true },
            contentAlignment = Alignment.Center,
        ) {
            // Ohne eigene Farbe gilt die des Filaments - das sagt der Strich.
            if (extruder.color.isEmpty())
                Text("–", color = PrusaColors.TextMuted, fontSize = 13.sp)
        }

        Box(Modifier.weight(1f)) {
            PresetCombo(filaments, extruder.filament, onEdit = onEdit, onSelect = onFilament)
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        title = { Text("Farbe des Extruders", color = PrusaColors.TextPrimary, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sechs je Reihe: bei 48 dp Zielgroesse passt das in die
                // Dialogbreite, ohne dass man zielen muss.
                swatches.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { hex ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parseColor(hex) ?: Color.Gray)
                                    .border(
                                        if (hex.equals(current, true)) 3.dp else 1.dp,
                                        if (hex.equals(current, true)) PrusaColors.Orange
                                        else PrusaColors.Divider,
                                        RoundedCornerShape(4.dp),
                                    )
                                    .clickable { onPick(hex) },
                            )
                        }
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Eigener Wert, z. B. #3399FF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onPick(manual.trim()) },
                enabled = manual.isBlank() || parseColor(manual.trim()) != null,
            ) { Text("Übernehmen", color = PrusaColors.Orange) }
        },
        dismissButton = {
            Row {
                // Ohne eigene Farbe gilt wieder die des Filaments.
                androidx.compose.material3.TextButton(onClick = { onPick("") }) {
                    Text("Vom Filament", color = PrusaColors.TextMuted)
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Abbrechen", color = PrusaColors.TextMuted)
                }
            }
        },
    )
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
            "Oben" to PsmViewport.View.TOP,
            "Vorn" to PsmViewport.View.FRONT,
            "Hinten" to PsmViewport.View.BACK,
            "Links" to PsmViewport.View.LEFT,
            "Rechts" to PsmViewport.View.RIGHT,
        )
        views.forEach { (label, v) ->
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { controller.setView(v) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = PrusaColors.TextPrimary, fontSize = 13.sp)
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
    objects: List<PsmCore.ObjectInfo>,
    selected: PsmCore.ObjectInfo?,
    progress: SlicerService.Progress,
    onSelect: (Int) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onOpenSettings: (String) -> Unit,
    onManagePrinters: () -> Unit,
    linkPrinters: List<de.psmobile.net.PrusaLink.Printer>,
    sendState: String?,
    scaleTool: Boolean,
    onScaleToolChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = progress is SlicerService.Progress.Running

    Column(
        modifier
            .background(PrusaColors.Panel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Beschriftungen wie im Original, uebersetzt aus dessen Katalog.
            SectionLabel(PsUi.tr("Printer"))
            PresetCombo(presets.printers, presets.selectedPrinter,
                        onEdit = { onOpenSettings("printer") }) {
                service.selectPreset(PsmCore.PresetType.PRINTER, it)
            }

            SectionLabel(PsUi.tr("Print settings"))
            PresetCombo(presets.prints, presets.selectedPrint,
                        onEdit = { onOpenSettings("print") }) {
                service.selectPreset(PsmCore.PresetType.PRINT, it)
            }

            // Ein Kopf sieht aus wie bisher, ab zwei wird je Extruder
            // gewaehlt. Ein MMU3 hat fuenf Wege, ein XL bis zu fuenf
            // Koepfe - ohne eigene Wahl je Kopf bekaemen alle dasselbe.
            if (presets.extruders.size <= 1) {
                SectionLabel(PsUi.tr("Filament"))
                PresetCombo(presets.filaments, presets.selectedFilament,
                            onEdit = { onOpenSettings("filament") }) {
                    service.selectPreset(PsmCore.PresetType.FILAMENT, it)
                }
            } else {
                SectionLabel(PsUi.tr("Filament") + " · ${presets.extruders.size} Extruder")
                presets.extruders.forEach { ex ->
                    ExtruderRow(
                        extruder = ex,
                        filaments = presets.filaments,
                        onFilament = { service.setExtruderFilament(ex.index, it) },
                        onColor = { service.setExtruderColor(ex.index, it) },
                        onEdit = { onOpenSettings("filament") },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PrusaColors.Divider)

            // Die frueheren "Schnelleinstellungen" waren handverlesen und
            // damit Nachbau. Ersetzt durch den vollstaendigen Einstellungs-
            // bildschirm hinter dem Zahnrad - Struktur und Stufen kommen
            // aus PrusaSlicer selbst. Siehe E-12.

            if (objects.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PrusaColors.Divider)
                SectionLabel("Objekte")
                objects.forEach { obj ->
                    ObjectRow(obj, obj.id == selected?.id, { onSelect(obj.id) }) {
                        service.removeObject(obj.id)
                    }
                }
            }

            // Bedienelemente zum ausgewaehlten Objekt, direkt unter der
            // Liste - am Desktop das Objektmanipulator-Feld rechts unten.
            selected?.let { obj ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PrusaColors.Divider)
                ObjectPanel(
                    service = service,
                    obj = obj,
                    scaleToolActive = scaleTool,
                    onScaleToolChange = onScaleToolChange,
                )
            }
        }

        ProgressBlock(progress)

        Button(
            onClick = { if (isRunning) service.cancelSlice() else service.startSlice() },
            enabled = objects.isNotEmpty() || isRunning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) PrusaColors.PanelRaised else PrusaColors.Orange,
                contentColor = PrusaColors.TextPrimary,
            ),
        ) {
            Text(
                if (isRunning) "Abbrechen" else "Jetzt slicen",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        // An `progress` haengen statt an einem eigenen Zustand: nach
        // "Bett leeren" faellt progress auf Idle zurueck, damit
        // verschwinden Senden und Export mit. Befund B5.
        if (progress is SlicerService.Progress.Done && linkPrinters.isNotEmpty()) {
            // Direkt an den ersten eingerichteten Drucker.
            val target = linkPrinters.first()
            Button(
                onClick = { service.sendToPrinter(target, false) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.PanelRaised,
                    contentColor = PrusaColors.TextPrimary,
                ),
            ) { Text("An " + target.name + " senden") }
        }

        sendState?.let {
            Text(it, color = PrusaColors.TextMuted, fontSize = 12.sp,
                 modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }

        androidx.compose.material3.TextButton(
            onClick = onManagePrinters,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Drucker verwalten", color = PrusaColors.TextMuted, fontSize = 12.sp) }

        if (progress is SlicerService.Progress.Done) {
            OutlinedButton(
                onClick = { service.shareableGcodeUri()?.let(onShare) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text("G-Code exportieren", Modifier.padding(start = 8.dp))
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
@Composable
private fun PresetCombo(
    options: List<String>,
    selected: String,
    onEdit: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected.ifBlank { "—" },
                color = PrusaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = PrusaColors.TextMuted)
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) { PsIcon("cog.svg", Modifier.size(16.dp)) }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PrusaColors.PanelRaised),
        ) {
            options.forEach { name ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = if (name == selected) PrusaColors.Orange else PrusaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = { expanded = false; onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun ObjectRow(
    obj: PsmCore.ObjectInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) PrusaColors.Orange.copy(alpha = 0.18f) else PrusaColors.PanelRaised)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                obj.name.ifBlank { "Objekt ${obj.id}" },
                color = PrusaColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "%.0f × %.0f × %.0f mm".format(obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third),
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
            )
            if (obj.outsideBed) {
                Text("außerhalb des Bettes", color = PrusaColors.Danger, fontSize = 11.sp)
            }
        }
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Delete, "Entfernen", tint = PrusaColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

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
                Text("Fertig in %.1f s".format(progress.seconds),
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
    }
    Spacer(Modifier.height(2.dp))
}
