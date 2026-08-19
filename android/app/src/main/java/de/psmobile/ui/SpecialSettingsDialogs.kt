package de.psmobile.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import de.psmobile.shared.rules.SpecialValueCodec
import de.psmobile.shared.rules.SpecialValueCodec.BedPoint

private data class SubstitutionRow(
    val find: String = "",
    val replace: String = "",
    val params: String = "",
    val notes: String = "",
)

/**
 * PrusaSlicers Desktop-Sonderdialoge in einem tabletgerechten Bereich.
 * Keine waagerechte Desktop-Tabelle wird kopiert: jede Aufgabe öffnet
 * einen großen Touch-Dialog mit mindestens 48-dp-Zielen.
 */
@Composable
internal fun SpecialSettingsPanel(
    core: PsmCore,
    tab: String,
    extruderCount: Int,
    configRevision: Int,
    onChanged: () -> Unit,
) {
    var editBed by remember { mutableStateOf(false) }
    var editMatrix by remember { mutableStateOf(false) }
    var editRamming by remember { mutableStateOf(false) }
    var editSubstitutions by remember { mutableStateOf(false) }
    var compatibility by remember {
        mutableStateOf<Pair<PsmCore.PresetType, String>?>(null)
    }
    var status by remember { mutableStateOf<String?>(null) }
    // Ob die Meldung ein Fehler ist, gehoert ausdruecklich hierher.
    // Vorher entschied das eine Textsuche nach "konnte"/"ungültig" -
    // die haette nach der Uebersetzung jeden englischen Fehler als
    // Erfolg eingefaerbt.
    var statusIstFehler by remember { mutableStateOf(false) }
    var pendingBedAsset by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun apply(action: () -> Unit, success: String) {
        runCatching(action)
            .onSuccess {
                status = success
                statusIstFehler = false
                onChanged()
            }
            .onFailure {
                status = it.message ?: PsUi.appText(
                    "The value could not be saved.", "Wert konnte nicht gespeichert werden.")
                statusIstFehler = true
            }
    }

    val bedAssetPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val key = pendingBedAsset
        pendingBedAsset = null
        if (uri != null && key != null) {
            scope.launch {
                runCatching {
                    val displayName = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }.orEmpty()
                    val safeName = displayName
                        .ifBlank { if (key.endsWith("texture")) "bett.png" else "bett.stl" }
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val folder = File(context.filesDir, "bed-assets").also { it.mkdirs() }
                    val target = File(folder, "${key.substringAfterLast('_')}-$safeName")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { PsUi.appText("The file is not readable.", "Datei ist nicht lesbar.") }
                            target.outputStream().use(input::copyTo)
                        }
                    }
                    core.setPresetValue(PsmCore.PresetType.PRINTER, key, target.absolutePath)
                    target
                }.onSuccess {
                    status = PsUi.appText(
                        "Bed file applied: ${it.name}", "Bettdatei übernommen: ${it.name}")
                    statusIstFehler = false
                    onChanged()
                }.onFailure {
                    status = it.message ?: PsUi.appText(
                        "The bed file could not be applied.",
                        "Bettdatei konnte nicht übernommen werden.")
                    statusIstFehler = true
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                PsUi.appText("Special dialogs", "Spezialdialoge"),
                color = PrusaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                PsUi.appText(
                    "Structured PrusaSlicer values are edited here as points, " +
                        "rows and matrices.",
                    "Strukturierte PrusaSlicer-Werte werden hier als Punkte, Zeilen " +
                        "und Matrizen bearbeitet.",
                ),
                color = PrusaColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
        }

        if (tab == "printer") {
            item {
                SpecialAction(
                    PsUi.appText("Bed shape", "Druckbett-Form"),
                    PsUi.appText("Polygon points in millimetres; invalid or zero-area shapes are rejected.", "Polygonpunkte in Millimetern; ungültige oder flächenlose Formen werden abgewiesen."),
                    PsUi.appText("Edit points", "Punkte bearbeiten"),
                ) { editBed = true }
            }
            item {
                BedAssetAction(
                    title = PsUi.appText("Custom bed texture", "Eigene Betttextur"),
                    current = remember(configRevision) {
                        core.presetValue(PsmCore.PresetType.PRINTER, "bed_custom_texture").orEmpty()
                    },
                    selectLabel = PsUi.appText("Choose image", "Bild auswählen"),
                    onSelect = {
                        pendingBedAsset = "bed_custom_texture"
                        bedAssetPicker.launch(arrayOf("image/png", "image/jpeg", "image/*"))
                    },
                    onClear = {
                        apply(
                            {
                                core.setPresetValue(
                                    PsmCore.PresetType.PRINTER,
                                    "bed_custom_texture",
                                    "",
                                )
                            },
                            PsUi.appText("Custom bed texture removed.", "Eigene Betttextur entfernt."),
                        )
                    },
                )
            }
            item {
                BedAssetAction(
                    title = PsUi.appText("Custom bed model", "Eigenes Bettmodell"),
                    current = remember(configRevision) {
                        core.presetValue(PsmCore.PresetType.PRINTER, "bed_custom_model").orEmpty()
                    },
                    selectLabel = PsUi.appText("Choose STL", "STL auswählen"),
                    onSelect = {
                        pendingBedAsset = "bed_custom_model"
                        bedAssetPicker.launch(
                            arrayOf("model/stl", "application/sla", "application/octet-stream")
                        )
                    },
                    onClear = {
                        apply(
                            {
                                core.setPresetValue(
                                    PsmCore.PresetType.PRINTER,
                                    "bed_custom_model",
                                    "",
                                )
                            },
                            PsUi.appText("Custom bed model removed.", "Eigenes Bettmodell entfernt."),
                        )
                    },
                )
            }
        }

        if (tab == "print") {
            item {
                SpecialAction(
                    "G-Code-Ersetzungen",
                    PsUi.appText("Search/replace with regex, case sensitivity, whole word and single-line mode.", "Suchen/Ersetzen mit Regex, Groß-/Kleinschreibung, ganzem Wort und Einzelzeilenmodus."),
                    "Ersetzungen bearbeiten",
                ) { editSubstitutions = true }
            }
            item {
                SpecialAction(
                    "Purge-Matrix",
                    "$extruderCount × $extruderCount Wischvolumen in mm³, direkt statt durch eine Desktop-Tabelle zu scrollen.",
                    "Matrix bearbeiten",
                ) { editMatrix = true }
            }
            item {
                SpecialAction(
                    PsUi.appText("Compatible printers", "Kompatible Drucker"),
                    PsUi.appText("Limit the print profile to specific printer profiles.", "Druckprofil auf konkrete Druckerprofile begrenzen."),
                    PsUi.appText("Choose profiles", "Profile auswählen"),
                ) {
                    compatibility = PsmCore.PresetType.PRINT to "compatible_printers"
                }
            }
        }

        if (tab == "filament") {
            item {
                SpecialAction(
                    "Ramming-Kurve",
                    PsUi.appText("Line width, line spacing and time/flow control points for the MMU filament change.", "Linienbreite, Linienabstand sowie Zeit-/Fluss-Kontrollpunkte für den MMU-Filamentwechsel."),
                    "Kurve bearbeiten",
                ) { editRamming = true }
            }
            item {
                SpecialAction(
                    PsUi.appText("Compatible print profiles", "Kompatible Druckprofile"),
                    PsUi.appText("Limit the filament to selected print profiles.", "Filament auf ausgewählte Druckprofile begrenzen."),
                    PsUi.appText("Choose profiles", "Profile auswählen"),
                ) {
                    compatibility = PsmCore.PresetType.FILAMENT to "compatible_prints"
                }
            }
            item {
                SpecialAction(
                    PsUi.appText("Compatible printers", "Kompatible Drucker"),
                    PsUi.appText("Limit the filament to selected printer profiles.", "Filament auf ausgewählte Druckerprofile begrenzen."),
                    PsUi.appText("Choose profiles", "Profile auswählen"),
                ) {
                    compatibility = PsmCore.PresetType.FILAMENT to "compatible_printers"
                }
            }
        }

        status?.let { message ->
            item {
                Text(
                    message,
                    color = if (statusIstFehler) PrusaColors.Danger else PrusaColors.Orange,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    if (editBed) {
        BedShapeDialog(
            initial = core.presetValue(PsmCore.PresetType.PRINTER, "bed_shape").orEmpty(),
            onDismiss = { editBed = false },
            onApply = { encoded ->
                apply(
                    {
                        core.setPresetValue(
                            PsmCore.PresetType.PRINTER,
                            "bed_shape",
                            encoded,
                        )
                    },
                    PsUi.appText("Bed shape applied.", "Druckbett-Form übernommen."),
                )
                editBed = false
            },
        )
    }
    if (editMatrix) {
        WipeMatrixDialog(
            initial = core["wiping_volumes_matrix"].orEmpty(),
            useCustom = core["wiping_volumes_use_custom_matrix"] == "1",
            extruderCount = extruderCount,
            onDismiss = { editMatrix = false },
            onApply = { encoded, custom ->
                apply(
                    {
                        core["wiping_volumes_matrix"] = encoded
                        core["wiping_volumes_use_custom_matrix"] = if (custom) "1" else "0"
                    },
                    PsUi.appText("Purge matrix applied.", "Purge-Matrix übernommen."),
                )
                editMatrix = false
            },
        )
    }
    if (editRamming) {
        RammingSettingsDialog(
            initial = core.presetValue(
                PsmCore.PresetType.FILAMENT,
                "filament_ramming_parameters",
            ).orEmpty(),
            onDismiss = { editRamming = false },
            onApply = { encoded ->
                apply(
                    {
                        core.setPresetValue(
                            PsmCore.PresetType.FILAMENT,
                            "filament_ramming_parameters",
                            encoded,
                        )
                    },
                    PsUi.appText("Ramming curve applied.", "Ramming-Kurve übernommen."),
                )
                editRamming = false
            },
        )
    }
    if (editSubstitutions) {
        SubstitutionsDialog(
            initial = core.presetValue(
                PsmCore.PresetType.PRINT,
                "gcode_substitutions",
            ).orEmpty(),
            onDismiss = { editSubstitutions = false },
            onApply = { encoded ->
                apply(
                    {
                        core.setPresetValue(
                            PsmCore.PresetType.PRINT,
                            "gcode_substitutions",
                            encoded,
                        )
                    },
                    PsUi.appText("G-code substitutions applied.", "G-Code-Ersetzungen übernommen."),
                )
                editSubstitutions = false
            },
        )
    }
    compatibility?.let { (type, key) ->
        CompatibilityDialog(
            title = if (key == "compatible_prints")
                PsUi.appText("Compatible print profiles", "Kompatible Druckprofile") else PsUi.appText("Compatible printers", "Kompatible Drucker"),
            initial = core.presetValue(type, key).orEmpty(),
            available = remember(type, key, configRevision) {
                core.presetNames(
                    if (key == "compatible_prints")
                        PsmCore.PresetType.PRINT
                    else
                        PsmCore.PresetType.PRINTER
                )
            },
            onDismiss = { compatibility = null },
            onApply = { encoded ->
                apply(
                    { core.setPresetValue(type, key, encoded) },
                    PsUi.appText("Profile compatibility applied.", "Profilkompatibilität übernommen."),
                )
                compatibility = null
            },
        )
    }
}

@Composable
private fun SpecialAction(
    title: String,
    description: String,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = PrusaColors.Panel,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = PrusaColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = PrusaColors.TextMuted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onClick, modifier = Modifier.height(50.dp)) {
                Text(label)
            }
        }
    }
}

@Composable
private fun BedAssetAction(
    title: String,
    current: String,
    selectLabel: String,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = PrusaColors.Panel,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PrusaColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                current.substringAfterLast('/').substringAfterLast('\\')
                    .ifBlank { "Standarddarstellung aktiv" },
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelect, modifier = Modifier.height(50.dp)) {
                    Text(selectLabel)
                }
                if (current.isNotBlank()) {
                    TextButton(onClick = onClear, modifier = Modifier.height(50.dp)) {
                        Text(PsUi.appText("Reset", "Zurücksetzen"), color = PrusaColors.Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun BedShapeDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val parsed = SpecialValueCodec.parseBedShape(initial).orEmpty()
    var fields by remember {
        mutableStateOf(
            (if (parsed.size >= 3) parsed else listOf(
                BedPoint(0.0, 0.0),
                BedPoint(250.0, 0.0),
                BedPoint(250.0, 210.0),
                BedPoint(0.0, 210.0),
            )).map { NumberCodecText(it.x) to NumberCodecText(it.y) }
        )
    }
    val points = fields.mapNotNull { (x, y) ->
        val px = NumberCodec.parseDouble(x)
        val py = NumberCodec.parseDouble(y)
        if (px == null || py == null) null else BedPoint(px, py)
    }
    val valid = points.size == fields.size && points.size >= 3 &&
        SpecialValueCodec.polygonArea(points) > 0.0001

    LargeDialog(onDismiss, PsUi.appText("Bed shape", "Druckbett-Form")) {
        Text(
            PsUi.appText("Points run around the bed in order. At least three points, unit mm.", "Punkte laufen der Reihe nach um das Bett. Mindestens drei Punkte, Einheit mm."),
            color = PrusaColors.TextMuted,
            fontSize = 13.sp,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fields.size) { index ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = PrusaColors.TextMuted, modifier = Modifier.width(28.dp))
                    OutlinedTextField(
                        value = fields[index].first,
                        onValueChange = { value ->
                            fields = fields.toMutableList().also {
                                it[index] = value to it[index].second
                            }
                        },
                        label = { Text("X · mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fields[index].second,
                        onValueChange = { value ->
                            fields = fields.toMutableList().also {
                                it[index] = it[index].first to value
                            }
                        },
                        label = { Text("Y · mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            fields = fields.toMutableList().also { it.removeAt(index) }
                        },
                        enabled = fields.size > 3,
                        modifier = Modifier.height(50.dp),
                    ) { Text(PsUi.appText("Delete", "Löschen")) }
                }
            }
        }
        OutlinedButton(
            onClick = {
                val last = fields.lastOrNull() ?: ("0" to "0")
                fields = fields + last
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(PsUi.appText("Add point", "Punkt hinzufügen")) }
        DialogButtons(
            valid = valid,
            onDismiss = onDismiss,
            onApply = { onApply(SpecialValueCodec.encodeBedShape(points)) },
        )
    }
}

@Composable
private fun WipeMatrixDialog(
    initial: String,
    useCustom: Boolean,
    extruderCount: Int,
    onDismiss: () -> Unit,
    onApply: (String, Boolean) -> Unit,
) {
    val count = extruderCount.coerceIn(1, 16)
    val parsed = SpecialValueCodec.parseFloats(initial).orEmpty()
    var custom by remember { mutableStateOf(useCustom) }
    var values by remember {
        mutableStateOf(
            List(count * count) { index ->
                NumberCodecText(
                    when {
                        index < parsed.size -> parsed[index]
                        index / count == index % count -> 0.0
                        else -> 140.0
                    }
                )
            }
        )
    }
    val numbers = values.map { NumberCodec.parseDouble(it) }
    val valid = numbers.all { it != null && it >= 0.0 }

    LargeDialog(onDismiss, "Purge-Matrix") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(PsUi.appText("Custom project values", "Eigene projektbezogene Werte"), color = PrusaColors.TextPrimary)
                Text("Von Zeile → Spalte, jeweils mm³", color = PrusaColors.TextMuted, fontSize = 12.sp)
            }
            Switch(checked = custom, onCheckedChange = { custom = it })
        }
        Column(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(64.dp))
                repeat(count) { to ->
                    Box(Modifier.width(88.dp).height(44.dp), contentAlignment = Alignment.Center) {
                        Text("Zu ${to + 1}", color = PrusaColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
            repeat(count) { from ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Von ${from + 1}", color = PrusaColors.TextMuted, modifier = Modifier.width(64.dp))
                    repeat(count) { to ->
                        val index = from * count + to
                        OutlinedTextField(
                            value = values[index],
                            onValueChange = { value ->
                                values = values.toMutableList().also { it[index] = value }
                            },
                            enabled = from != to && custom,
                            singleLine = true,
                            modifier = Modifier.width(88.dp),
                        )
                    }
                }
            }
        }
        DialogButtons(
            valid = valid,
            onDismiss = onDismiss,
            onApply = {
                onApply(
                    SpecialValueCodec.encodeFloats(numbers.map { it ?: 0.0 }),
                    custom,
                )
            },
        )
    }
}

@Composable
private fun RammingSettingsDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val parsed = SpecialValueCodec.parseRamming(initial)
        ?: SpecialValueCodec.Ramming(
            120.0, 100.0, emptyList(),
            listOf(BedPoint(0.05, 6.6), BedPoint(1.0, 8.0), BedPoint(3.0, 7.0)),
        )
    var width by remember { mutableStateOf(NumberCodecText(parsed.lineWidthPercent)) }
    var spacing by remember { mutableStateOf(NumberCodecText(parsed.lineSpacingPercent)) }
    var controls by remember {
        mutableStateOf(parsed.controlPoints.map {
            NumberCodecText(it.x) to NumberCodecText(it.y)
        })
    }
    var controlsChanged by remember { mutableStateOf(false) }
    val points = controls.mapNotNull { (time, speed) ->
        val x = NumberCodec.parseDouble(time)
        val y = NumberCodec.parseDouble(speed)
        if (x == null || y == null) null else BedPoint(x, y)
    }
    val lineWidth = NumberCodec.parseDouble(width)
    val lineSpacing = NumberCodec.parseDouble(spacing)
    val valid = lineWidth != null && lineWidth in 10.0..300.0 &&
        lineSpacing != null && lineSpacing in 10.0..300.0 &&
        points.size == controls.size && points.size >= 2 &&
        points.zipWithNext().all { (a, b) -> a.x < b.x } &&
        points.all { it.x >= 0.0 && it.y >= 0.0 }

    LargeDialog(onDismiss, "Ramming-Kurve") {
        Text(
            PsUi.appText("Careful: wrong values can jam the MMU filament change.", "Achtung: falsche Werte können beim MMU-Filamentwechsel zu Stau führen."),
            color = PrusaColors.Danger,
            fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                width, { width = it }, label = { Text(PsUi.appText("Line width · %", "Linienbreite · %")) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                spacing, { spacing = it }, label = { Text("Linienabstand · %") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        Text(PsUi.appText("Curve points", "Kurvenpunkte"), color = PrusaColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(controls.size) { index ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        controls[index].first,
                        {
                            controls = controls.toMutableList().also { rows ->
                                rows[index] = it to rows[index].second
                            }
                            controlsChanged = true
                        },
                        label = { Text(PsUi.appText("Time · s", "Zeit · s")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        controls[index].second,
                        {
                            controls = controls.toMutableList().also { rows ->
                                rows[index] = rows[index].first to it
                            }
                            controlsChanged = true
                        },
                        label = { Text(PsUi.appText("Flow · mm³/s", "Fluss · mm³/s")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            controls = controls.toMutableList().also { it.removeAt(index) }
                            controlsChanged = true
                        },
                        enabled = controls.size > 2,
                        modifier = Modifier.height(50.dp),
                    ) { Text(PsUi.appText("Delete", "Löschen")) }
                }
            }
        }
        OutlinedButton(
            onClick = {
                val last = points.lastOrNull() ?: BedPoint(0.0, 0.0)
                controls = controls + (
                    NumberCodecText(last.x + 0.5) to NumberCodecText(last.y)
                )
                controlsChanged = true
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(PsUi.appText("Add curve point", "Kurvenpunkt hinzufügen")) }
        DialogButtons(
            valid = valid,
            onDismiss = onDismiss,
            onApply = {
                if (lineWidth != null && lineSpacing != null) {
                    onApply(
                        SpecialValueCodec.encodeRamming(
                            parsed.copy(
                                lineWidthPercent = lineWidth,
                                lineSpacingPercent = lineSpacing,
                                controlPoints = points,
                            ),
                            rebuildSamples = controlsChanged,
                        )
                    )
                }
            },
        )
    }
}

@Composable
private fun SubstitutionsDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val decoded = SpecialValueCodec.parseStrings(initial).orEmpty()
    var rows by remember {
        mutableStateOf(
            decoded.chunked(4).filter { it.size == 4 }.map {
                SubstitutionRow(it[0], it[1], it[2], it[3])
            }
        )
    }
    var edit by remember { mutableStateOf<Int?>(null) }

    LargeDialog(onDismiss, "G-Code-Ersetzungen") {
        Text(
            PsUi.appText("Substitutions are applied in order to every generated G-code line.", "Ersetzungen werden der Reihe nach auf jede erzeugte G-Code-Zeile angewendet."),
            color = PrusaColors.TextMuted,
            fontSize = 13.sp,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.size) { index ->
                Surface(
                    color = PrusaColors.PanelRaised,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                rows[index].find.ifBlank { "Leeres Suchmuster" },
                                color = PrusaColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "→ ${rows[index].replace}",
                                color = PrusaColors.TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(onClick = { edit = index }, modifier = Modifier.height(50.dp)) {
                            Text(PsUi.appText("Edit", "Ändern"))
                        }
                        TextButton(
                            onClick = {
                                rows = rows.toMutableList().also { it.removeAt(index) }
                            },
                            modifier = Modifier.height(50.dp),
                        ) { Text(PsUi.appText("Delete", "Löschen"), color = PrusaColors.Danger) }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                rows = rows + SubstitutionRow()
                edit = rows.lastIndex
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(PsUi.appText("Add substitution", "Ersetzung hinzufügen")) }
        DialogButtons(
            valid = true,
            onDismiss = onDismiss,
            onApply = {
                onApply(
                    SpecialValueCodec.encodeStrings(
                        rows.flatMap { listOf(it.find, it.replace, it.params, it.notes) }
                    )
                )
            },
        )
    }

    edit?.let { index ->
        rows.getOrNull(index)?.let { current ->
            SubstitutionEditDialog(
                initial = current,
                onDismiss = {
                    if (current == SubstitutionRow() && rows.getOrNull(index) == current)
                        rows = rows.toMutableList().also { it.removeAt(index) }
                    edit = null
                },
                onApply = { updated ->
                    rows = rows.toMutableList().also { it[index] = updated }
                    edit = null
                },
            )
        }
    }
}

@Composable
private fun SubstitutionEditDialog(
    initial: SubstitutionRow,
    onDismiss: () -> Unit,
    onApply: (SubstitutionRow) -> Unit,
) {
    var find by remember { mutableStateOf(initial.find) }
    var replace by remember { mutableStateOf(initial.replace) }
    var notes by remember { mutableStateOf(initial.notes) }
    var regex by remember { mutableStateOf('r' in initial.params.lowercase()) }
    var insensitive by remember { mutableStateOf('i' in initial.params.lowercase()) }
    var wholeWord by remember { mutableStateOf('w' in initial.params.lowercase()) }
    var singleLine by remember { mutableStateOf('s' in initial.params.lowercase()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ersetzung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    find, { find = it }, label = { Text(PsUi.appText("Search", "Suchen")) },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    replace, { replace = it }, label = { Text(PsUi.appText("Replace with", "Ersetzen durch")) },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                CheckLine(PsUi.appText("Regular expression", "Regulärer Ausdruck"), regex) { regex = it }
                CheckLine(PsUi.appText("Ignore case", "Groß-/Kleinschreibung ignorieren"), insensitive) { insensitive = it }
                CheckLine("Ganzes Wort", wholeWord) { wholeWord = it }
                if (regex) CheckLine("Einzelne Zeile abgleichen", singleLine) { singleLine = it }
                OutlinedTextField(
                    notes, { notes = it }, label = { Text(PsUi.appText("Note", "Notiz")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = find.isNotEmpty(),
                onClick = {
                    val params = buildString {
                        if (regex) append('r')
                        if (insensitive) append('i')
                        if (wholeWord) append('w')
                        if (regex && singleLine) append('s')
                    }
                    onApply(SubstitutionRow(find, replace, params, notes))
                },
            ) { Text(PsUi.appText("Apply", "Übernehmen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) } },
    )
}

@Composable
private fun CompatibilityDialog(
    title: String,
    initial: String,
    available: List<String>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val initialValues = SpecialValueCodec.parseStrings(initial).orEmpty()
    var selected by remember {
        mutableStateOf(initialValues.toCollection(linkedSetOf()))
    }
    var query by remember { mutableStateOf("") }
    val visible = available.filter {
        query.isBlank() || it.contains(query, ignoreCase = true)
    }
    LargeDialog(onDismiss, title) {
        Text(
            PsUi.appText(
                "Empty means no restriction. Names already saved but not " +
                    "currently installed are kept.",
                "Leer bedeutet keine Einschränkung. Bereits gespeicherte, aktuell " +
                    "nicht installierte Namen bleiben erhalten.",
            ),
            color = PrusaColors.TextMuted,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(PsUi.appText("Search profiles", "Profile suchen")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    selected = LinkedHashSet(selected).also { it.addAll(visible) }
                },
                modifier = Modifier.height(48.dp),
            ) { Text(PsUi.appText("Select matches", "Treffer auswählen")) }
            TextButton(
                onClick = { selected = linkedSetOf() },
                modifier = Modifier.height(48.dp),
            ) { Text(PsUi.appText("No restriction", "Keine Einschränkung")) }
            Spacer(Modifier.weight(1f))
            Text(
                PsUi.appText("${selected.size} selected", "${selected.size} gewählt"),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(visible) { name ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable {
                            selected = LinkedHashSet(selected).also {
                                if (!it.add(name)) it.remove(name)
                            }
                        }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = name in selected,
                        onCheckedChange = {
                            selected = LinkedHashSet(selected).also {
                                if (!it.add(name)) it.remove(name)
                            }
                        },
                    )
                    Text(
                        name,
                        color = PrusaColors.TextPrimary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            val unavailable = selected.filter { it !in available }
            if (unavailable.isNotEmpty()) {
                item {
                    Text(
                        PsUi.appText("Not installed", "Nicht installiert"),
                        color = PrusaColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(unavailable) { name ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = {
                                selected = LinkedHashSet(selected).also { it.remove(name) }
                            },
                        )
                        Text(
                            name,
                            color = PrusaColors.TextMuted,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        DialogButtons(
            valid = true,
            onDismiss = onDismiss,
            onApply = {
                onApply(SpecialValueCodec.encodeStrings(selected.toList()))
            },
        )
    }
}

@Composable
private fun CheckLine(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, color = PrusaColors.TextPrimary)
    }
}

@Composable
private fun LargeDialog(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Ohne diese Umhuellung zeichnet der Dialog in der Dichte des
        // Geraets statt in der der App: 22 sp Ueberschrift und 980 dp
        // Breite blieben auf einem kleinen Bildschirm stehen, waehrend
        // alles dahinter gestaucht ist. Dieser Rahmen traegt saemtliche
        // Spezialeinstellungs-Dialoge, der Fehler waere also ueberall
        // sichtbar gewesen.
        ScaledOverlay {
            Surface(
                color = PrusaColors.Background,
                shape = RoundedCornerShape(Corners.SHEET.dp),
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f).widthIn(max = 980.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        title,
                        color = PrusaColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(color = PrusaColors.Divider)
                    content()
                }
            }
        }
    }
}

@Composable
private fun DialogButtons(
    valid: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!valid) {
            Text(
                PsUi.appText("Please check your input.", "Bitte Eingaben prüfen."),
                color = PrusaColors.Danger,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.height(50.dp)) {
            Text(PsUi.appText("Cancel", "Abbrechen"))
        }
        Button(
            onClick = onApply,
            enabled = valid,
            modifier = Modifier.height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
        ) { Text(PsUi.appText("Apply", "Übernehmen")) }
    }
}

private fun NumberCodecText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
