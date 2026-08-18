package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

internal sealed interface SurfaceToolMode {
    data object Flatten : SurfaceToolMode
    data object Measure : SurfaceToolMode
    data class Paint(
        val tool: PsmCore.PaintTool,
        val state: Int,
        val radiusMm: Float = 5f,
        val mode: PsmCore.PaintMode = PsmCore.PaintMode.BRUSH,
        val shape: PsmCore.PaintShape = PsmCore.PaintShape.SPHERE,
        val fillAngleDeg: Float = 30f,
    ) : SurfaceToolMode {
        /**
         * Derselbe Optionswert geht an den Kern und an den Viewport -
         * der Zeiger auf dem Modell zeigt damit genau das, was der
         * naechste Strich tut.
         */
        fun toOptions(): PsmCore.PaintOptions = PsmCore.PaintOptions(
            tool = tool,
            state = state,
            mode = mode,
            shape = shape,
            radiusMm = radiusMm,
            fillAngleDeg = fillAngleDeg,
        ).normalizedForTool()
    }
}

/** Text that tells the user what the next touch on the model will do. */
internal fun surfaceToolInstruction(mode: SurfaceToolMode): String = when (mode) {
    SurfaceToolMode.Flatten ->
        PsUi.appText("Tap a model face that should rest on the print bed.", "Tippe auf eine Modellfläche, die auf dem Druckbett liegen soll.")
    SurfaceToolMode.Measure ->
        PsUi.appText("Tap two points on the model to measure their distance.", "Tippe nacheinander zwei Punkte auf dem Modell, um den Abstand zu messen.")
    is SurfaceToolMode.Paint -> when (mode.tool) {
        PsmCore.PaintTool.SUPPORT -> if (mode.state == 2)
            PsUi.appText("Paint faces where automatic supports must be blocked.", "Streiche über Flächen, auf denen kein automatischer Support entstehen soll.")
        else
            PsUi.appText("Paint faces where supports should be generated.", "Streiche über die Flächen, auf denen Support erzeugt werden soll.")
        PsmCore.PaintTool.SEAM ->
            PsUi.appText("Paint the face where the seam should be preferred.", "Streiche über die Fläche, auf der die Naht bevorzugt liegen soll.")
        PsmCore.PaintTool.FUZZY ->
            PsUi.appText("Paint faces that should receive a rough fuzzy surface.", "Streiche über die Flächen, die eine raue Fuzzy-Oberfläche erhalten sollen.")
        PsmCore.PaintTool.MMU ->
            PsUi.appText("Paint faces that should print with the selected extruder.", "Streiche über Flächen, die mit dem gewählten Extruder gedruckt werden sollen.")
    }
}

private fun surfaceToolTitle(mode: SurfaceToolMode): String = when (mode) {
    SurfaceToolMode.Flatten -> PsUi.appText("Lay on face", "Fläche flach legen")
    SurfaceToolMode.Measure -> PsUi.appText("Measure", "Messen")
    is SurfaceToolMode.Paint -> when (mode.tool) {
        PsmCore.PaintTool.SUPPORT -> if (mode.state == 2) PsUi.appText("Block supports", "Support blockieren") else PsUi.appText("Paint supports", "Support malen")
        PsmCore.PaintTool.SEAM -> PsUi.appText("Paint seam", "Naht malen")
        PsmCore.PaintTool.FUZZY -> PsUi.appText("Paint fuzzy skin", "Fuzzy Skin malen")
        PsmCore.PaintTool.MMU -> PsUi.appText("Paint extruder / ColorMix colour", "Extruder-/ColorMix-Farbe malen")
    }
}

/** A concise outcome statement for a tool card, before a potentially disruptive action. */
internal fun geometryToolDescription(label: String): String = when (label) {
    "Fläche flach legen" -> PsUi.appText("Orient the model on a selected face.", "Modell über eine gewählte Fläche ausrichten.")
    "Schneiden" -> PsUi.appText("Set a horizontal cut line.", "Horizontale Schnittlinie einstellen.")
    "Vereinfachen" -> PsUi.appText("Reduce triangles · undo is available.", "Dreiecke reduzieren · Rückgängig möglich.")
    "In Objekte teilen" -> PsUi.appText("Edit disconnected meshes separately.", "Getrennte Netze separat bearbeiten.")
    "In Volumen teilen" -> PsUi.appText("Edit parts in the same object separately.", "Teile im selben Objekt getrennt bearbeiten.")
    "Modifier hinzufügen" -> PsUi.appText("Add a region for cutouts, supports or settings.", "Bereich für Ausschnitt, Support oder Einstellungen.")
    "Variable Schichthöhen" -> PsUi.appText("Fine details, faster simple areas.", "Details fein, einfache Bereiche schneller drucken.")
    "Text prägen" -> PsUi.appText("Add text as a raised or cut volume.", "Text als erhabenes oder ausgeschnittenes Volumen.")
    "SVG prägen" -> PsUi.appText("Add SVG as a raised or cut volume.", "SVG als erhabenes oder ausgeschnittenes Volumen.")
    "Messen" -> PsUi.appText("Tap two points on the model.", "Zwei Punkte auf dem Modell antippen.")
    "Support malen" -> PsUi.appText("Enforce supports only on selected faces.", "Support nur auf gewählten Flächen erzwingen.")
    "Support blockieren" -> PsUi.appText("Block automatic supports locally.", "Automatischen Support lokal verhindern.")
    "Naht malen" -> PsUi.appText("Set the preferred Z-seam position.", "Bevorzugte Position der Z-Naht festlegen.")
    "Fuzzy Skin malen" -> PsUi.appText("Apply a rough surface only locally.", "Raue Oberfläche nur lokal anwenden.")
    "Bemalung beenden" -> PsUi.appText("Leave the active surface tool safely.", "Aktives Flächenwerkzeug sicher verlassen.")
    else -> PsUi.appText("Tool for the selected model.", "Werkzeug für das ausgewählte Modell.")
}

private fun geometryToolLabel(label: String): String = when (label) {
    "Fläche flach legen" -> PsUi.appText("Lay on face", label)
    "Schneiden" -> PsUi.appText("Cut", label)
    "Vereinfachen" -> PsUi.appText("Simplify", label)
    "In Objekte teilen" -> PsUi.appText("Split into objects", label)
    "In Volumen teilen" -> PsUi.appText("Split into volumes", label)
    "Modifier hinzufügen" -> PsUi.appText("Add modifier", label)
    "Variable Schichthöhen" -> PsUi.appText("Variable layer heights", label)
    "Text prägen" -> PsUi.appText("Emboss text", label)
    "SVG prägen" -> PsUi.appText("Emboss SVG", label)
    "Messen" -> PsUi.appText("Measure", label)
    "Support malen" -> PsUi.appText("Paint supports", label)
    "Support blockieren" -> PsUi.appText("Block supports", label)
    "Naht malen" -> PsUi.appText("Paint seam", label)
    "Fuzzy Skin malen" -> PsUi.appText("Paint fuzzy skin", label)
    "Bemalung beenden" -> PsUi.appText("Finish painting", label)
    else -> label
}

private enum class GeometryDialog {
    CUT,
    SIMPLIFY,
    ADD_VOLUME,
}

internal data class LayerPreviewSegment(val fromZ: Double, val toZ: Double, val heightMm: Double)

/** Converts profile control points into the colored vertical bands shown to the user. */
internal fun layerProfilePreviewSegments(
    objectHeight: Double,
    points: List<Pair<Double, Double>>,
): List<LayerPreviewSegment> {
    val sorted = points.sortedBy { it.first }
    return sorted.mapIndexedNotNull { index, (z, layerHeight) ->
        val end = sorted.getOrNull(index + 1)?.first ?: objectHeight
        if (end > z && layerHeight > 0.0) LayerPreviewSegment(z, end, layerHeight) else null
    }
}

/** The cut dialog exposes the useful output combinations without three cramped toggles. */
internal enum class CutResult(val label: String) {
    BOTH("Beide"),
    UPPER("Nur oben"),
    LOWER("Nur unten"),
    ;

    /** Der Enum-Wert bleibt deutsch (er wird gespeichert/verglichen),
     *  angezeigt wird die uebersetzte Fassung. */
    fun anzeige(): String = when (this) {
        BOTH -> PsUi.appText("Both", label)
        UPPER -> PsUi.appText("Upper only", label)
        LOWER -> PsUi.appText("Lower only", label)
    }
}

internal fun retainedCutParts(result: CutResult): Pair<Boolean, Boolean> = when (result) {
    CutResult.BOTH -> true to true
    CutResult.UPPER -> true to false
    CutResult.LOWER -> false to true
}

/**
 * Touch-Inspektor fuer Werkzeuge, die am Desktop als Gizmos oder
 * Kontextmenues verteilt sind. Jede Aktion hat mindestens 48 dp und
 * bleibt deshalb auch auf 8-Zoll-Tablets sicher bedienbar.
 */
@Composable
internal fun GeometryTools(
    service: SlicerService,
    selected: PsmCore.ObjectInfo?,
    volumes: List<PsmCore.VolumeInfo>,
    extruderOptions: List<ExtruderChoice>,
    surfaceMode: SurfaceToolMode?,
    measureText: String?,
    onSurfaceMode: (SurfaceToolMode?) -> Unit,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
    onAddSvg: (Int, Float, PsmCore.VolumeType) -> Unit,
    onOpenLayerEditor: (Int) -> Unit,
) {
    if (selected == null) {
        Text(
            PsUi.appText("Select an object first to use the model tools.", "Für Modellwerkzeuge zuerst ein Objekt auswählen."),
            color = PrusaColors.TextMuted,
            modifier = Modifier.padding(vertical = 18.dp),
        )
        return
    }

    var dialog by remember { mutableStateOf<GeometryDialog?>(null) }
    var textDialog by remember { mutableStateOf(false) }
    var svgDialog by remember { mutableStateOf(false) }
    var colour by remember(selected.id, selected.colour) {
        mutableStateOf(selected.colour.ifBlank { "#FF8000" })
    }
    var wipeInfill by remember(selected.id, selected.wipeIntoInfill) {
        mutableStateOf(selected.wipeIntoInfill)
    }
    var wipeObjects by remember(selected.id, selected.wipeIntoObjects) {
        mutableStateOf(selected.wipeIntoObjects)
    }
    var brushRadius by remember { mutableStateOf(3f) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        surfaceMode?.let { mode ->
            ActiveSurfaceToolCard(
                title = surfaceToolTitle(mode),
                instruction = surfaceToolInstruction(mode),
                onCancel = { onSurfaceMode(null) },
            )
        }
        ToolHeading(PsUi.appText("Geometry", "Geometrie"))
        ToolSectionHint(PsUi.appText("Change the shape, split the model or add local regions.", "Form ändern, Modell aufteilen oder lokale Bereiche ergänzen."))
        ToolButtonRow(
            "Fläche flach legen",
            geometryToolDescription("Fläche flach legen"),
            surfaceMode is SurfaceToolMode.Flatten,
            { onSurfaceMode(
                if (surfaceMode is SurfaceToolMode.Flatten) null
                else SurfaceToolMode.Flatten
            ) },
            "Schneiden",
            geometryToolDescription("Schneiden"),
            false,
            { dialog = GeometryDialog.CUT },
        )
        ToolButtonRow(
            "Vereinfachen",
            geometryToolDescription("Vereinfachen"),
            false,
            { dialog = GeometryDialog.SIMPLIFY },
            "In Objekte teilen",
            geometryToolDescription("In Objekte teilen"),
            false,
            { service.splitIntoObjects(selected.id) },
        )
        ToolButtonRow(
            "In Volumen teilen",
            geometryToolDescription("In Volumen teilen"),
            false,
            { service.splitIntoVolumes(selected.id) },
            "Modifier hinzufügen",
            geometryToolDescription("Modifier hinzufügen"),
            false,
            { dialog = GeometryDialog.ADD_VOLUME },
        )
        ToolActionCard(
            label = "Variable Schichthöhen",
            description = geometryToolDescription("Variable Schichthöhen"),
            onClick = { onOpenLayerEditor(selected.id) },
            modifier = Modifier.fillMaxWidth(),
        )
        ToolButtonRow(
            "Text prägen",
            geometryToolDescription("Text prägen"),
            false,
            { textDialog = true },
            "SVG prägen",
            geometryToolDescription("SVG prägen"),
            false,
            { svgDialog = true },
        )

        val removable = volumes.filter { it.type != PsmCore.VolumeType.MODEL_PART }
        if (removable.isNotEmpty()) {
            ToolHeading(PsUi.appText("Modifiers in object", "Modifier im Objekt"))
            removable.forEach { volume ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            PrusaColors.PanelRaised,
                            RoundedCornerShape(9.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            volume.name.ifBlank { "${PsUi.appText("Volume", "Volumen")} ${volume.index + 1}" },
                            color = PrusaColors.TextPrimary,
                            maxLines = 1,
                        )
                        Text(
                            volume.type.displayName(),
                            color = PrusaColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            service.removeVolume(selected.id, volume.index)
                        },
                        modifier = Modifier.height(48.dp),
                    ) { Text(PsUi.appText("Remove", "Entfernen"), color = PrusaColors.Danger) }
                }
            }
        }

        HorizontalDivider(color = PrusaColors.Divider)
        ToolHeading(PsUi.appText("Surface", "Oberfläche"))
        ToolSectionHint(PsUi.appText("Activate a tool, then tap or paint directly on the model.", "Ein Werkzeug aktivieren und danach direkt auf dem Modell tippen oder streichen."))
        ToolButtonRow(
            "Messen",
            geometryToolDescription("Messen"),
            surfaceMode is SurfaceToolMode.Measure,
            { onSurfaceMode(
                if (surfaceMode is SurfaceToolMode.Measure) null
                else SurfaceToolMode.Measure
            ) },
            "Support malen",
            geometryToolDescription("Support malen"),
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.SUPPORT,
            {
                onSurfaceMode(malwerkzeug(surfaceMode, brushRadius,
                    PsmCore.PaintTool.SUPPORT, 1))
            },
        )
        ToolButtonRow(
            "Support blockieren",
            geometryToolDescription("Support blockieren"),
            (surfaceMode as? SurfaceToolMode.Paint)?.let {
                it.tool == PsmCore.PaintTool.SUPPORT && it.state == 2
            } == true,
            {
                onSurfaceMode(malwerkzeug(surfaceMode, brushRadius,
                    PsmCore.PaintTool.SUPPORT, 2))
            },
            "Naht malen",
            geometryToolDescription("Naht malen"),
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.SEAM,
            {
                onSurfaceMode(malwerkzeug(surfaceMode, brushRadius,
                    PsmCore.PaintTool.SEAM, 1))
            },
        )
        ToolButtonRow(
            "Fuzzy Skin malen",
            geometryToolDescription("Fuzzy Skin malen"),
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.FUZZY,
            {
                onSurfaceMode(malwerkzeug(surfaceMode, brushRadius,
                    PsmCore.PaintTool.FUZZY, 1))
            },
            "Bemalung beenden",
            geometryToolDescription("Bemalung beenden"),
            false,
            { onSurfaceMode(null) },
        )

        // Die Feineinstellungen gehoeren zum aktiven Werkzeug. Ohne
        // aktives Werkzeug haetten sie nichts, worauf sie wirken.
        (surfaceMode as? SurfaceToolMode.Paint)?.let { malen ->
            val optionen = malen.toOptions()

            PaintOptionRow(PsUi.appText("State", "Zustand")) {
                if (malen.tool == PsmCore.PaintTool.MMU) {
                    PaintChoice(PsUi.appText("Erase", "Radieren"), malen.state == 0) {
                        onSurfaceMode(malen.copy(state = 0))
                    }
                    extruderOptions.forEach { extruder ->
                        PaintChoice(extruder.label, malen.state == extruder.id) {
                            onSurfaceMode(malen.copy(state = extruder.id))
                        }
                    }
                } else {
                    PaintChoice(PsUi.appText("Enforce", "Verstärken"), malen.state == 1) {
                        onSurfaceMode(malen.copy(state = 1))
                    }
                    // Fuzzy kennt im Kern keinen zweiten
                    // Facettenzustand - dort waere der Knopf ein Fehler.
                    if (malen.tool == PsmCore.PaintTool.SUPPORT ||
                        malen.tool == PsmCore.PaintTool.SEAM) {
                        PaintChoice(PsUi.appText("Block", "Blockieren"), malen.state == 2) {
                            onSurfaceMode(malen.copy(state = 2))
                        }
                    }
                    PaintChoice(PsUi.appText("Erase", "Radieren"), malen.state == 0) {
                        onSurfaceMode(malen.copy(state = 0))
                    }
                }
            }

            // Nicht jedes Werkzeug kann jeden Modus: Naht und Fuzzy
            // kennen im Kern nur den Pinsel, den Eimer gibt es nur fuer
            // MMU. Ein Knopf, der nichts tut, waere schlimmer als keiner.
            if (optionen.supportedModes.size > 1) {
                PaintOptionRow(PsUi.appText("Mode", "Modus")) {
                    optionen.supportedModes.forEach { modus ->
                        PaintChoice(paintModeName(modus), optionen.mode == modus) {
                            onSurfaceMode(malen.copy(mode = modus))
                        }
                    }
                }
            }

            if (optionen.mode == PsmCore.PaintMode.BRUSH) {
                PaintOptionRow(PsUi.appText("Shape", "Form")) {
                    PaintChoice(
                        PsUi.appText("Circle", "Kreis"),
                        malen.shape == PsmCore.PaintShape.CIRCLE,
                    ) { onSurfaceMode(malen.copy(shape = PsmCore.PaintShape.CIRCLE)) }
                    PaintChoice(
                        PsUi.appText("Sphere", "Kugel"),
                        malen.shape == PsmCore.PaintShape.SPHERE,
                    ) { onSurfaceMode(malen.copy(shape = PsmCore.PaintShape.SPHERE)) }
                }
                PaintSlider(
                    label = PsUi.appText("Size", "Größe"),
                    value = malen.radiusMm,
                    range = 1f..20f,
                    readout = "${malen.radiusMm.roundToInt()} mm",
                ) {
                    brushRadius = it
                    onSurfaceMode(malen.copy(radiusMm = it))
                }
            } else {
                PaintSlider(
                    label = PsUi.appText("Angle", "Winkel"),
                    value = malen.fillAngleDeg,
                    range = 0f..90f,
                    readout = "${malen.fillAngleDeg.roundToInt()}°",
                ) { onSurfaceMode(malen.copy(fillAngleDeg = it)) }
            }

            // Ohne diese Zahl ist nicht zu sehen, ob ein Strich
            // ueberhaupt etwas bewirkt hat.
            val revision by service.paintRevision.collectAsState()
            val markiert = remember(selected.id, malen.tool, revision) {
                service.paintCount(selected.id, malen.tool)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${PsUi.appText("Marked", "Markiert")}: $markiert",
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { service.clearPaint(selected.id, malen.tool) },
                    modifier = Modifier.height(48.dp),
                ) { Text(PsUi.appText("Clear", "Löschen"), color = PrusaColors.Danger) }
            }
        }

        if (extruderOptions.size > 1) {
            Text(
                PsUi.appText("Extruder / ColorMix colour", "Extruder-/ColorMix-Farbe"),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                extruderOptions.forEach { extruder ->
                    val state = extruder.id
                    // Gleichheit ueber Werkzeug und Zustand, nicht ueber
                    // den ganzen Optionswert: Radius, Modus und Form
                    // duerfen die Auswahl nicht mitbestimmen.
                    val aktiv = (surfaceMode as? SurfaceToolMode.Paint)?.let {
                        it.tool == PsmCore.PaintTool.MMU && it.state == state
                    } == true
                    FilterChip(
                        selected = aktiv,
                        onClick = {
                            onSurfaceMode(
                                (surfaceMode as? SurfaceToolMode.Paint)
                                    ?.copy(tool = PsmCore.PaintTool.MMU, state = state)
                                    ?: SurfaceToolMode.Paint(
                                        PsmCore.PaintTool.MMU, state, brushRadius
                                    )
                            )
                        },
                        label = { Text(extruder.label) },
                        modifier = Modifier.height(48.dp),
                        colors = prusaFilterChipColors(),
                        border = prusaFilterChipBorder(aktiv),
                    )
                }
            }
        }
        measureText?.let {
            Text(
                it,
                color = PrusaColors.Orange,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        PrusaColors.Orange.copy(alpha = 0.12f),
                        RoundedCornerShape(9.dp),
                    )
                    .padding(12.dp),
            )
        }

        HorizontalDivider(color = PrusaColors.Divider)
        ToolHeading(PsUi.appText("Multicolour object", "Multicolor-Objekt"))
        ToolSectionHint(PsUi.appText("Object colour and wipe behaviour apply only to the selected model.", "Objektfarbe und Wipe-Verhalten gelten nur für das ausgewählte Modell."))
        OutlinedTextField(
            value = colour,
            onValueChange = { colour = it },
            label = { Text(PsUi.appText("Object colour · #RRGGBB", "Objektfarbe · #RRGGBB")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { service.setObjectColour(selected.id, colour.trim()) },
            enabled = Regex("#[0-9a-fA-F]{6}").matches(colour.trim()),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrusaColors.PanelRaised,
            ),
        ) { Text(PsUi.appText("Apply colour", "Farbe übernehmen")) }
        ToggleRow("In Infill wischen", wipeInfill) {
            wipeInfill = it
            service.setObjectWipe(selected.id, wipeInfill, wipeObjects)
        }
        ToggleRow(PsUi.appText("Wipe into other objects", "In andere Objekte wischen"), wipeObjects) {
            wipeObjects = it
            service.setObjectWipe(selected.id, wipeInfill, wipeObjects)
        }

        HorizontalDivider(color = PrusaColors.Divider)
        ProjectTools(
            service = service,
            onExportPlate = onExportPlate,
            onRepairStl = onRepairStl,
            onConvertGcode = onConvertGcode,
        )
    }

    if (textDialog) {
        TextEmbossDialog(
            onDismiss = { textDialog = false },
            onApply = { text, size, depth, type ->
                service.addTextVolume(
                    selected.id, text, size, depth, type
                )
                textDialog = false
            },
        )
    }
    if (svgDialog) {
        SvgEmbossDialog(
            onDismiss = { svgDialog = false },
            onApply = { depth, type ->
                onAddSvg(selected.id, depth, type)
                svgDialog = false
            },
        )
    }

    when (dialog) {
        GeometryDialog.CUT -> CutDialog(
            objectHeight = selected.sizeMm.third,
            onDismiss = { dialog = null },
            onApply = { z, upper, lower, asParts ->
                service.cutObject(selected.id, z, upper, lower, asParts)
                dialog = null
            },
        )
        GeometryDialog.SIMPLIFY -> SimplifyDialog(
            triangles = selected.triangles,
            onDismiss = { dialog = null },
            onApply = {
                service.simplifyObject(selected.id, it)
                dialog = null
            },
        )
        GeometryDialog.ADD_VOLUME -> AddVolumeDialog(
            onDismiss = { dialog = null },
            onApply = { type, shape, x, y, z ->
                service.addPrimitiveVolume(
                    selected.id, type, shape, x, y, z
                )
                dialog = null
            },
        )
        null -> Unit
    }
}

/**
 * Inspector-sized editor for variable layer heights. The profile rows may
 * scroll; navigation and apply/reset actions deliberately never do.
 */
@Composable
internal fun LayerProfileToolPage(
    objectHeight: Double,
    initial: List<Pair<Double, Double>>,
    onBack: () -> Unit,
    onApply: (List<Pair<Double, Double>>) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editor by remember(objectHeight, initial) {
        mutableStateOf(LayerProfileEditorState.fromProfile(objectHeight, initial))
    }

    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.height(40.dp),
        ) { Text("← " + PsUi.appText("Tools", "Werkzeuge"), color = PrusaColors.TextPrimary) }
        Text(
            PsUi.appText("Variable layer heights", "Variable Schichthöhen"),
            color = PrusaColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            PsUi.appText("Fine areas are orange, coarse areas grey.", "Feine Bereiche sind orange, grobe grau."),
            color = PrusaColors.TextMuted,
            fontSize = 12.sp,
        )
        LayerProfilePreview(editor.previewSegments, objectHeight)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            editor.rows.forEachIndexed { index, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SizeField(
                        "Z",
                        row.first,
                        { editor = editor.updateRow(index, z = it) },
                        Modifier.weight(1f),
                    )
                    SizeField(
                        PsUi.appText("Height", "Höhe"),
                        row.second,
                        { editor = editor.updateRow(index, height = it) },
                        Modifier.weight(1f),
                    )
                    // Creation remains beside the very first visible row.
                    // A separate button below a scrolling list vanished on
                    // landscape tablets, leaving an apparently static tool.
                    Column(Modifier.width(56.dp).height(56.dp)) {
                        TextButton(
                            onClick = { editor = editor.addPoint() },
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                        ) { Text("+") }
                        TextButton(
                            enabled = editor.rows.size > 2,
                            onClick = { editor = editor.removePoint(index) },
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                        ) { Text("−") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = editor.canApply,
                onClick = { onApply(editor.validPoints) },
                modifier = Modifier.weight(2f).height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.Orange,
                    contentColor = PrusaColors.TextPrimary,
                ),
            ) { Text(PsUi.appText("Apply", "Übernehmen")) }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(PsUi.appText("Reset", "Reset")) }
        }
    }
}

@Composable
private fun LayerProfilePreview(
    segments: List<LayerPreviewSegment>,
    objectHeight: Double,
) {
    val display = if (segments.isEmpty()) {
        layerProfilePreviewSegments(objectHeight, listOf(0.0 to 0.2))
    } else segments
    Row(
        Modifier.fillMaxWidth().height(62.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(PrusaColors.PanelRaised)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(26.dp).height(46.dp).clip(RoundedCornerShape(4.dp)),
        ) {
            val largest = display.maxOfOrNull { it.heightMm } ?: 0.2
            display.asReversed().forEach { segment ->
                val ratio = ((segment.toZ - segment.fromZ) / objectHeight)
                    .toFloat().coerceAtLeast(0.02f)
                val fine = segment.heightMm < largest * 0.75
                Box(
                    Modifier.fillMaxWidth().weight(ratio)
                        .background(if (fine) PrusaColors.Orange else PrusaColors.Divider),
                )
            }
        }
        Column {
            Text(
                PsUi.appText("${display.size} height ranges", "${display.size} Höhenbereiche"),
                color = PrusaColors.TextPrimary,
                fontSize = 13.sp,
            )
            Text(
                display.joinToString(" · ") { "${"%.2f".format(it.heightMm)} mm" },
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolHeading(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ToolSectionHint(text: String) {
    Text(
        text,
        color = PrusaColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun ToolButtonRow(
    first: String,
    firstDescription: String,
    firstActive: Boolean,
    onFirst: () -> Unit,
    second: String,
    secondDescription: String,
    secondActive: Boolean,
    onSecond: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolActionCard(
            label = first,
            description = firstDescription,
            active = firstActive,
            onClick = onFirst,
            modifier = Modifier.weight(1f),
        )
        ToolActionCard(
            label = second,
            description = secondDescription,
            active = secondActive,
            onClick = onSecond,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToolActionCard(
    label: String,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (active) PrusaColors.Orange else PrusaColors.PanelRaised,
            contentColor = PrusaColors.TextPrimary,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(geometryToolLabel(label), fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = if (active) PrusaColors.TextPrimary else PrusaColors.TextMuted,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ActiveSurfaceToolCard(
    title: String,
    instruction: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrusaColors.Orange.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PrusaColors.Orange, fontWeight = FontWeight.SemiBold)
            Text(instruction, color = PrusaColors.TextPrimary, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.height(44.dp)) {
            Text(PsUi.appText("Finish", "Beenden"))
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = PrusaColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun CutDialog(
    objectHeight: Float,
    onDismiss: () -> Unit,
    onApply: (Float, Boolean, Boolean, Boolean) -> Unit,
) {
    var z by remember { mutableStateOf((objectHeight * 0.5f).toString()) }
    var cutResult by remember { mutableStateOf(CutResult.BOTH) }
    var asParts by remember { mutableStateOf(false) }
    val parsed = NumberCodec.parseFloat(z)
    val (upper, lower) = retainedCutParts(cutResult)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(PsUi.appText("Cut horizontally", "Horizontal schneiden")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = z,
                    onValueChange = { z = it },
                    label = { Text(PsUi.appText("Height above bed · mm", "Höhe über Bett · mm")) },
                    singleLine = true,
                )
                Text(PsUi.appText("Keep parts", "Teile behalten"), color = PrusaColors.TextMuted, fontSize = 12.sp)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CutResult.entries.forEach { option ->
                        FilterChip(
                            selected = cutResult == option,
                            onClick = { cutResult = option },
                            label = { Text(option.anzeige()) },
                            modifier = Modifier.height(48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = PrusaColors.PanelRaised,
                                labelColor = PrusaColors.TextPrimary,
                                selectedContainerColor = PrusaColors.PanelRaised,
                                selectedLabelColor = PrusaColors.Orange,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = cutResult == option,
                                borderColor = PrusaColors.Divider,
                                selectedBorderColor = PrusaColors.Orange,
                            ),
                        )
                    }
                }
                CheckRow(PsUi.appText("As parts of one object", "Als Teile eines Objekts"), asParts) { asParts = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0f && (upper || lower),
                onClick = { parsed?.let { onApply(it, upper, lower, asParts) } },
            ) { Text(PsUi.appText("Cut", "Schneiden")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun SimplifyDialog(
    triangles: Int,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var percent by remember { mutableStateOf("50") }
    val parsed = NumberCodec.parseFloat(percent)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text("Mesh vereinfachen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Aktuell: $triangles Dreiecke",
                    color = PrusaColors.TextMuted,
                )
                OutlinedTextField(
                    value = percent,
                    onValueChange = { percent = it },
                    label = { Text("Verbleibende Dreiecke · %") },
                    singleLine = true,
                )
                Text(
                    PsUi.appText("The change can be fully undone.", "Die Änderung ist über Rückgängig vollständig umkehrbar."),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed in 1f..100f,
                onClick = { parsed?.let { onApply(it / 100f) } },
            ) { Text("Vereinfachen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun AddVolumeDialog(
    onDismiss: () -> Unit,
    onApply: (
        PsmCore.VolumeType,
        PsmCore.PrimitiveShape,
        Float,
        Float,
        Float,
    ) -> Unit,
) {
    val roles = listOf(
        PsmCore.VolumeType.NEGATIVE,
        PsmCore.VolumeType.MODIFIER,
        PsmCore.VolumeType.SUPPORT_BLOCKER,
        PsmCore.VolumeType.SUPPORT_ENFORCER,
    )
    var role by remember { mutableStateOf(PsmCore.VolumeType.MODIFIER) }
    var rolePickerOpen by remember { mutableStateOf(false) }
    var shape by remember { mutableStateOf(PsmCore.PrimitiveShape.BOX) }
    var x by remember { mutableStateOf("10") }
    var y by remember { mutableStateOf("10") }
    var z by remember { mutableStateOf("10") }
    val px = NumberCodec.parseFloat(x)
    val py = NumberCodec.parseFloat(y)
    val pz = NumberCodec.parseFloat(z)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(PsUi.appText("Add modifier", "Modifier hinzufügen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { rolePickerOpen = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Rolle", color = PrusaColors.TextMuted, fontSize = 11.sp)
                        Text(role.displayName(), color = PrusaColors.TextPrimary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PsmCore.PrimitiveShape.entries.forEach {
                        FilterChip(
                            selected = shape == it,
                            onClick = { shape = it },
                            label = { Text(it.displayName()) },
                            modifier = Modifier.height(48.dp),
                            colors = prusaFilterChipColors(),
                            border = prusaFilterChipBorder(shape == it),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SizeField("X", x, { x = it }, Modifier.weight(1f))
                    SizeField("Y", y, { y = it }, Modifier.weight(1f))
                    SizeField("Z", z, { z = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = listOf(px, py, pz).all { it != null && it > 0f },
                onClick = {
                    if (px != null && py != null && pz != null)
                        onApply(role, shape, px, py, pz)
                },
            ) { Text(PsUi.appText("Add", "Hinzufügen")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )

    if (rolePickerOpen) {
        AlertDialog(
            onDismissRequest = { rolePickerOpen = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(PsUi.appText("Choose modifier role", "Modifier-Rolle wählen")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                role = option
                                rolePickerOpen = false
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (role == option)
                                    PrusaColors.Orange else PrusaColors.TextPrimary,
                            ),
                        ) { Text(option.displayName()) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SizeField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("$label · mm") },
        singleLine = true,
        modifier = modifier,
        colors = TextFieldDefaults.colors(
            focusedTextColor = PrusaColors.TextPrimary,
            unfocusedTextColor = PrusaColors.TextPrimary,
            focusedLabelColor = PrusaColors.Orange,
            unfocusedLabelColor = PrusaColors.TextMuted,
            focusedContainerColor = PrusaColors.PanelRaised,
            unfocusedContainerColor = PrusaColors.PanelRaised,
        ),
    )
}


/** Inserts an editable point inside the final segment instead of past the model's top. */
internal fun insertLayerProfilePoint(
    rows: List<Pair<String, String>>,
    objectHeight: Double,
): List<Pair<String, String>> {
    if (rows.size < 2) return rows + ("0.0" to "0.2")
    val beforeEnd = NumberCodec.parseDouble(rows[rows.lastIndex - 1].first) ?: 0.0
    val end = NumberCodec.parseDouble(rows.last().first) ?: objectHeight.coerceAtLeast(0.01)
    val upperBound = minOf(end, objectHeight).coerceAtLeast(0.01)
    if (upperBound <= 0.02) return rows
    val middle = ((beforeEnd + upperBound) / 2.0).coerceIn(0.01, upperBound - 0.01)
    if (middle <= beforeEnd) return rows
    return rows.toMutableList().also { it.add(it.lastIndex, middle.toString() to rows.last().second) }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, color = PrusaColors.TextPrimary)
    }
}

private fun PsmCore.VolumeType.displayName(): String = when (this) {
    PsmCore.VolumeType.MODEL_PART -> PsUi.appText("Model part", "Modellteil")
    PsmCore.VolumeType.NEGATIVE -> PsUi.appText("Negative volume", "Negativvolumen")
    PsmCore.VolumeType.MODIFIER -> PsUi.appText("Modifier", "Modifier")
    PsmCore.VolumeType.SUPPORT_BLOCKER -> PsUi.appText("Support blocker", "Support-Blocker")
    PsmCore.VolumeType.SUPPORT_ENFORCER -> PsUi.appText("Support enforcer", "Support-Erzwinger")
    PsmCore.VolumeType.UNKNOWN -> PsUi.appText("Unknown", "Unbekannt")
}

private fun PsmCore.PrimitiveShape.displayName(): String = when (this) {
    PsmCore.PrimitiveShape.BOX -> PsUi.appText("Box", "Quader")
    PsmCore.PrimitiveShape.CYLINDER -> PsUi.appText("Cylinder", "Zylinder")
    PsmCore.PrimitiveShape.SPHERE -> PsUi.appText("Sphere", "Kugel")
}

private fun PsmCore.PaintTool.displayName(): String = when (this) {
    PsmCore.PaintTool.SUPPORT -> PsUi.appText("Support", "Support")
    PsmCore.PaintTool.SEAM -> PsUi.appText("Seam", "Naht")
    PsmCore.PaintTool.FUZZY -> "Fuzzy"
    PsmCore.PaintTool.MMU -> "MMU"
}

@Composable
private fun TextEmbossDialog(
    onDismiss: () -> Unit,
    onApply: (String, Float, Float, PsmCore.VolumeType) -> Unit,
) {
    var text by remember { mutableStateOf("PSMobile") }
    var size by remember { mutableStateOf("10") }
    var depth by remember { mutableStateOf("1") }
    var type by remember { mutableStateOf(PsmCore.VolumeType.MODEL_PART) }
    val parsedSize = NumberCodec.parseFloat(size)
    val parsedDepth = NumberCodec.parseFloat(depth)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(PsUi.appText("Emboss text", "Text prägen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SizeField(
                        PsUi.appText("Text height", "Texthöhe"),
                        size,
                        { size = it },
                        Modifier.weight(1f),
                    )
                    SizeField(
                        PsUi.appText("Emboss depth", "Prägetiefe"),
                        depth,
                        { depth = it },
                        Modifier.weight(1f),
                    )
                }
                EmbossRolePicker(type) { type = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() &&
                    parsedSize != null && parsedSize > 0f &&
                    parsedDepth != null && parsedDepth > 0f,
                onClick = {
                    if (parsedSize != null && parsedDepth != null)
                        onApply(
                            text.trim(), parsedSize, parsedDepth, type
                        )
                },
            ) { Text(PsUi.appText("Add", "Hinzufügen")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun SvgEmbossDialog(
    onDismiss: () -> Unit,
    onApply: (Float, PsmCore.VolumeType) -> Unit,
) {
    var depth by remember { mutableStateOf("1") }
    var type by remember { mutableStateOf(PsmCore.VolumeType.MODEL_PART) }
    val parsedDepth = NumberCodec.parseFloat(depth)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(PsUi.appText("Emboss SVG", "SVG prägen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nach Bestätigung wird eine SVG-Datei ausgewählt. " +
                        "Geschlossene Pfade werden als 3D-Volumen erzeugt.",
                    color = PrusaColors.TextMuted,
                )
                SizeField(
                    PsUi.appText("Emboss depth", "Prägetiefe"),
                    depth,
                    { depth = it },
                    Modifier.fillMaxWidth(),
                )
                EmbossRolePicker(type) { type = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedDepth != null && parsedDepth > 0f,
                onClick = {
                    parsedDepth?.let { onApply(it, type) }
                },
            ) { Text(PsUi.appText("Choose SVG", "SVG auswählen")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun EmbossRolePicker(
    selected: PsmCore.VolumeType,
    onSelect: (PsmCore.VolumeType) -> Unit,
) {
    val values = listOf(
        PsmCore.VolumeType.MODEL_PART,
        PsmCore.VolumeType.NEGATIVE,
        PsmCore.VolumeType.MODIFIER,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.displayName()) },
                modifier = Modifier.height(48.dp),
                colors = prusaFilterChipColors(),
                border = prusaFilterChipBorder(selected == value),
            )
        }
    }
}

/** Keeps every selectable option in the Advanced tools visually in the app theme. */
@Composable
private fun prusaFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = PrusaColors.PanelRaised,
    labelColor = PrusaColors.TextPrimary,
    selectedContainerColor = PrusaColors.PanelRaised,
    selectedLabelColor = PrusaColors.Orange,
)

@Composable
private fun prusaFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = PrusaColors.Divider,
    selectedBorderColor = PrusaColors.Orange,
)

/**
 * Wechselt das Malwerkzeug und behaelt dabei, was der Nutzer vorher
 * eingestellt hat. Ein Werkzeugwechsel soll nicht Radius, Form und
 * Modus mit zuruecksetzen; nur was zum neuen Werkzeug nicht passt,
 * raeumt normalizedForTool weg.
 */
private fun malwerkzeug(
    aktuell: SurfaceToolMode?,
    radius: Float,
    tool: PsmCore.PaintTool,
    state: Int,
): SurfaceToolMode.Paint {
    val vorher = aktuell as? SurfaceToolMode.Paint
        ?: return SurfaceToolMode.Paint(tool, state, radius)
    val gewaehlt = vorher.copy(tool = tool, state = state)
    val bereinigt = gewaehlt.toOptions()
    return gewaehlt.copy(mode = bereinigt.mode, state = bereinigt.state)
}

private fun paintModeName(mode: PsmCore.PaintMode): String = when (mode) {
    PsmCore.PaintMode.BRUSH -> PsUi.appText("Brush", "Pinsel")
    PsmCore.PaintMode.SMART_FILL -> "Smart Fill"
    PsmCore.PaintMode.BUCKET_FILL -> PsUi.appText("Bucket", "Eimer")
}

/** Eine beschriftete Zeile aus Auswahlknoepfen, waagerecht scrollbar. */
@Composable
private fun PaintOptionRow(label: String, inhalt: @Composable () -> Unit) {
    Text(label, color = PrusaColors.TextMuted, fontSize = 12.sp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { inhalt() }
}

@Composable
private fun PaintChoice(label: String, aktiv: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = aktiv,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.height(48.dp),
        colors = prusaFilterChipColors(),
        border = prusaFilterChipBorder(aktiv),
    )
}

@Composable
private fun PaintSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = PrusaColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = PrusaColors.Orange,
                activeTrackColor = PrusaColors.Orange,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            readout,
            color = PrusaColors.TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.width(56.dp),
        )
    }
}
