package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors

internal sealed interface SurfaceToolMode {
    data object Flatten : SurfaceToolMode
    data object Measure : SurfaceToolMode
    data class Paint(
        val tool: PsmCore.PaintTool,
        val state: Int,
        val radiusMm: Float = 3f,
    ) : SurfaceToolMode
}

private enum class GeometryDialog {
    CUT,
    SIMPLIFY,
    ADD_VOLUME,
    LAYERS,
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
    extruderCount: Int,
    surfaceMode: SurfaceToolMode?,
    measureText: String?,
    onSurfaceMode: (SurfaceToolMode?) -> Unit,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
    onAddSvg: (Int, Float, PsmCore.VolumeType) -> Unit,
) {
    if (selected == null) {
        Text(
            "Für Modellwerkzeuge zuerst ein Objekt auswählen.",
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
        ToolHeading("Geometrie")
        ToolButtonRow(
            "Fläche flach legen",
            surfaceMode is SurfaceToolMode.Flatten,
            { onSurfaceMode(
                if (surfaceMode is SurfaceToolMode.Flatten) null
                else SurfaceToolMode.Flatten
            ) },
            "Schneiden",
            false,
            { dialog = GeometryDialog.CUT },
        )
        ToolButtonRow(
            "Vereinfachen",
            false,
            { dialog = GeometryDialog.SIMPLIFY },
            "In Objekte teilen",
            false,
            { service.splitIntoObjects(selected.id) },
        )
        ToolButtonRow(
            "In Volumen teilen",
            false,
            { service.splitIntoVolumes(selected.id) },
            "Modifier hinzufügen",
            false,
            { dialog = GeometryDialog.ADD_VOLUME },
        )
        OutlinedButton(
            onClick = { dialog = GeometryDialog.LAYERS },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Variable Schichthöhen") }
        ToolButtonRow(
            "Text prägen",
            false,
            { textDialog = true },
            "SVG prägen",
            false,
            { svgDialog = true },
        )

        val removable = volumes.filter { it.type != PsmCore.VolumeType.MODEL_PART }
        if (removable.isNotEmpty()) {
            ToolHeading("Modifier im Objekt")
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
                            volume.name.ifBlank { "Volumen ${volume.index + 1}" },
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
                    ) { Text("Entfernen", color = PrusaColors.Danger) }
                }
            }
        }

        HorizontalDivider(color = PrusaColors.Divider)
        ToolHeading("Oberfläche")
        ToolButtonRow(
            "Messen",
            surfaceMode is SurfaceToolMode.Measure,
            { onSurfaceMode(
                if (surfaceMode is SurfaceToolMode.Measure) null
                else SurfaceToolMode.Measure
            ) },
            "Support malen",
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.SUPPORT,
            {
                onSurfaceMode(
                    SurfaceToolMode.Paint(
                        PsmCore.PaintTool.SUPPORT, 1, brushRadius
                    )
                )
            },
        )
        ToolButtonRow(
            "Support blockieren",
            (surfaceMode as? SurfaceToolMode.Paint)?.let {
                it.tool == PsmCore.PaintTool.SUPPORT && it.state == 2
            } == true,
            {
                onSurfaceMode(
                    SurfaceToolMode.Paint(
                        PsmCore.PaintTool.SUPPORT, 2, brushRadius
                    )
                )
            },
            "Naht malen",
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.SEAM,
            {
                onSurfaceMode(
                    SurfaceToolMode.Paint(PsmCore.PaintTool.SEAM, 1, brushRadius)
                )
            },
        )
        ToolButtonRow(
            "Fuzzy Skin malen",
            (surfaceMode as? SurfaceToolMode.Paint)?.tool ==
                PsmCore.PaintTool.FUZZY,
            {
                onSurfaceMode(
                    SurfaceToolMode.Paint(PsmCore.PaintTool.FUZZY, 1, brushRadius)
                )
            },
            "Bemalung beenden",
            false,
            { onSurfaceMode(null) },
        )

        Text("Pinselradius · mm", color = PrusaColors.TextMuted, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1f, 3f, 6f, 12f).forEach { radius ->
                FilterChip(
                    selected = brushRadius == radius,
                    onClick = {
                        brushRadius = radius
                        (surfaceMode as? SurfaceToolMode.Paint)?.let {
                            onSurfaceMode(it.copy(radiusMm = radius))
                        }
                    },
                    label = { Text("${radius.toInt()} mm") },
                    modifier = Modifier.height(48.dp),
                )
            }
        }

        if (extruderCount > 1) {
            Text(
                "MMU-Farbe",
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(extruderCount) { index ->
                    val state = index + 1
                    FilterChip(
                        selected = surfaceMode ==
                            SurfaceToolMode.Paint(PsmCore.PaintTool.MMU, state),
                        onClick = {
                            onSurfaceMode(
                                SurfaceToolMode.Paint(
                                    PsmCore.PaintTool.MMU, state, brushRadius
                                )
                            )
                        },
                        label = { Text("E$state") },
                        modifier = Modifier.height(48.dp),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PsmCore.PaintTool.entries.forEach { tool ->
                TextButton(
                    onClick = { service.clearPaint(selected.id, tool) },
                    modifier = Modifier.height(48.dp),
                ) { Text("${tool.displayName()} löschen", fontSize = 12.sp) }
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
        ToolHeading("Multicolor-Objekt")
        OutlinedTextField(
            value = colour,
            onValueChange = { colour = it },
            label = { Text("Objektfarbe · #RRGGBB") },
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
        ) { Text("Farbe übernehmen") }
        ToggleRow("In Infill wischen", wipeInfill) {
            wipeInfill = it
            service.setObjectWipe(selected.id, wipeInfill, wipeObjects)
        }
        ToggleRow("In andere Objekte wischen", wipeObjects) {
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
        GeometryDialog.LAYERS -> LayerProfileDialog(
            objectHeight = selected.sizeMm.third.toDouble(),
            initial = service.layerProfile(selected.id),
            onDismiss = { dialog = null },
            onApply = {
                service.setLayerProfile(selected.id, it)
                dialog = null
            },
        )
        null -> Unit
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
private fun ToolButtonRow(
    first: String,
    firstActive: Boolean,
    onFirst: () -> Unit,
    second: String,
    secondActive: Boolean,
    onSecond: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolButton(first, firstActive, onFirst, Modifier.weight(1f))
        ToolButton(second, secondActive, onSecond, Modifier.weight(1f))
    }
}

@Composable
private fun ToolButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (active) PrusaColors.Orange else PrusaColors.PanelRaised,
            contentColor = PrusaColors.TextPrimary,
        ),
    ) {
        Text(label, fontSize = 12.sp, maxLines = 2)
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
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var asParts by remember { mutableStateOf(false) }
    val parsed = NumberCodec.parseFloat(z)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horizontal schneiden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = z,
                    onValueChange = { z = it },
                    label = { Text("Höhe über Bett · mm") },
                    singleLine = true,
                )
                CheckRow("Oberen Teil behalten", upper) { upper = it }
                CheckRow("Unteren Teil behalten", lower) { lower = it }
                CheckRow("Als Teile eines Objekts", asParts) { asParts = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0f && (upper || lower),
                onClick = { parsed?.let { onApply(it, upper, lower, asParts) } },
            ) { Text("Schneiden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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
                    "Die Änderung ist über Rückgängig vollständig umkehrbar.",
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
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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
    var shape by remember { mutableStateOf(PsmCore.PrimitiveShape.BOX) }
    var x by remember { mutableStateOf("10") }
    var y by remember { mutableStateOf("10") }
    var z by remember { mutableStateOf("10") }
    val px = NumberCodec.parseFloat(x)
    val py = NumberCodec.parseFloat(y)
    val pz = NumberCodec.parseFloat(z)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    roles.forEach {
                        FilterChip(
                            selected = role == it,
                            onClick = { role = it },
                            label = { Text(it.displayName()) },
                            modifier = Modifier.height(48.dp),
                        )
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
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
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
    )
}

@Composable
private fun LayerProfileDialog(
    objectHeight: Double,
    initial: List<Pair<Double, Double>>,
    onDismiss: () -> Unit,
    onApply: (List<Pair<Double, Double>>) -> Unit,
) {
    val defaults = listOf(0.0 to 0.20, objectHeight.coerceAtLeast(0.01) to 0.20)
    var rows by remember {
        mutableStateOf(
            (initial.ifEmpty { defaults }).map {
                it.first.toString() to it.second.toString()
            }
        )
    }
    val parsed = rows.mapNotNull { (z, h) ->
        val pz = NumberCodec.parseDouble(z)
        val ph = NumberCodec.parseDouble(h)
        if (pz == null || ph == null) null else pz to ph
    }
    val valid = parsed.size == rows.size &&
        parsed.size >= 2 &&
        parsed.all { it.first >= 0.0 && it.second > 0.0 } &&
        parsed.zipWithNext().all { (a, b) -> b.first > a.first }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Variable Schichthöhen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "Z-Punkte müssen streng steigen.",
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                )
                rows.forEachIndexed { index, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SizeField(
                            "Z",
                            pair.first,
                            { value ->
                                rows = rows.toMutableList().also {
                                    it[index] = value to it[index].second
                                }
                            },
                            Modifier.weight(1f),
                        )
                        SizeField(
                            "Höhe",
                            pair.second,
                            { value ->
                                rows = rows.toMutableList().also {
                                    it[index] = it[index].first to value
                                }
                            },
                            Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = rows.size > 2,
                            onClick = {
                                rows = rows.toMutableList().also {
                                    it.removeAt(index)
                                }
                            },
                            modifier = Modifier.width(52.dp).height(56.dp),
                        ) { Text("−") }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val lastZ = parsed.lastOrNull()?.first ?: 0.0
                        rows = rows + ((lastZ + 1.0).toString() to "0.2")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Punkt hinzufügen") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onApply(parsed) },
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(emptyList()) }) {
                    Text("Zurücksetzen")
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
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
    PsmCore.VolumeType.MODEL_PART -> "Modellteil"
    PsmCore.VolumeType.NEGATIVE -> "Negativvolumen"
    PsmCore.VolumeType.MODIFIER -> "Modifier"
    PsmCore.VolumeType.SUPPORT_BLOCKER -> "Support-Blocker"
    PsmCore.VolumeType.SUPPORT_ENFORCER -> "Support-Erzwinger"
    PsmCore.VolumeType.UNKNOWN -> "Unbekannt"
}

private fun PsmCore.PrimitiveShape.displayName(): String = when (this) {
    PsmCore.PrimitiveShape.BOX -> "Quader"
    PsmCore.PrimitiveShape.CYLINDER -> "Zylinder"
    PsmCore.PrimitiveShape.SPHERE -> "Kugel"
}

private fun PsmCore.PaintTool.displayName(): String = when (this) {
    PsmCore.PaintTool.SUPPORT -> "Support"
    PsmCore.PaintTool.SEAM -> "Naht"
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
        title = { Text("Text prägen") },
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
                        "Texthöhe",
                        size,
                        { size = it },
                        Modifier.weight(1f),
                    )
                    SizeField(
                        "Prägetiefe",
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
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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
        title = { Text("SVG prägen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nach Bestätigung wird eine SVG-Datei ausgewählt. " +
                        "Geschlossene Pfade werden als 3D-Volumen erzeugt.",
                    color = PrusaColors.TextMuted,
                )
                SizeField(
                    "Prägetiefe",
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
            ) { Text("SVG auswählen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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
            )
        }
    }
}
