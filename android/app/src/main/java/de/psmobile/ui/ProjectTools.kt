package de.psmobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors

@Composable
internal fun ProjectTools(
    service: SlicerService,
    onExportPlate: (PsmCore.PlateFormat) -> Unit,
    onRepairStl: () -> Unit,
    onConvertGcode: () -> Unit,
) {
    var wipe by remember { mutableStateOf(service.wipeTower()) }
    var wipeX by remember(wipe) { mutableStateOf(NumberCodec.oneDecimal(wipe.x)) }
    var wipeY by remember(wipe) { mutableStateOf(NumberCodec.oneDecimal(wipe.y)) }
    var wipeRotation by remember(wipe) {
        mutableStateOf(NumberCodec.oneDecimal(wipe.rotationDegrees))
    }
    var gcodes by remember { mutableStateOf(service.customGcodes()) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var addingGcode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProjectHeading("Wipe-Tower")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProjectNumberField("X · mm", wipeX, { wipeX = it }, Modifier.weight(1f))
            ProjectNumberField("Y · mm", wipeY, { wipeY = it }, Modifier.weight(1f))
            ProjectNumberField(
                PsUi.appText("Rotation · °", "Drehung · °"),
                wipeRotation,
                { wipeRotation = it },
                Modifier.weight(1f),
            )
        }
        val x = NumberCodec.parseFloat(wipeX)
        val y = NumberCodec.parseFloat(wipeY)
        val rotation = NumberCodec.parseFloat(wipeRotation)
        Button(
            onClick = {
                if (x != null && y != null && rotation != null) {
                    wipe = PsmCore.WipeTower(x, y, rotation)
                    service.setWipeTower(wipe)
                }
            },
            enabled = x != null && y != null && rotation != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrusaColors.PanelRaised,
            ),
        ) { Text(PsUi.appText("Apply wipe tower", "Wipe-Tower übernehmen")) }

        HorizontalDivider(color = PrusaColors.Divider)
        ProjectHeading(PsUi.appText("Custom G-code by height", "Custom G-Code nach Höhe"))
        if (gcodes.isEmpty()) {
            Text(
                PsUi.appText("No colour changes, pauses or custom commands yet.", "Noch keine Farbwechsel, Pausen oder eigenen Befehle."),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        gcodes.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "%.2f mm · %s".format(
                            item.printZ, item.type.displayName()
                        ),
                        color = PrusaColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.extra.isNotBlank()) {
                        Text(
                            item.extra,
                            color = PrusaColors.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                        )
                    }
                }
                TextButton(
                    onClick = { editIndex = index },
                    modifier = Modifier.height(48.dp),
                ) { Text(PsUi.appText("Edit", "Ändern")) }
                TextButton(
                    onClick = {
                        gcodes = gcodes.toMutableList().also {
                            it.removeAt(index)
                        }
                        service.replaceCustomGcodes(gcodes)
                    },
                    modifier = Modifier.height(48.dp),
                ) { Text(PsUi.appText("Delete", "Löschen"), color = PrusaColors.Danger) }
            }
        }
        OutlinedButton(
            onClick = { addingGcode = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
        ) { Text(PsUi.appText("Add height command", "Höhenbefehl hinzufügen")) }

        HorizontalDivider(color = PrusaColors.Divider)
        ProjectHeading(PsUi.appText("File tools", "Dateiwerkzeuge"))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onExportPlate(PsmCore.PlateFormat.STL) },
                modifier = Modifier.weight(1f).height(54.dp),
            ) { Text(PsUi.appText("Bed as STL", "Bett als STL"), fontSize = 12.sp) }
            OutlinedButton(
                onClick = { onExportPlate(PsmCore.PlateFormat.OBJ) },
                modifier = Modifier.weight(1f).height(54.dp),
            ) { Text(PsUi.appText("Bed as OBJ", "Bett als OBJ"), fontSize = 12.sp) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRepairStl,
                modifier = Modifier.weight(1f).height(54.dp),
            ) { Text(PsUi.appText("Repair STL", "STL reparieren"), fontSize = 12.sp) }
            OutlinedButton(
                onClick = onConvertGcode,
                modifier = Modifier.weight(1f).height(54.dp),
            ) { Text(PsUi.appText("Convert G-code", "G-Code wandeln"), fontSize = 12.sp) }
        }
        Text(
            PsUi.appText("ZIP import is deliberately not part of this package.", "ZIP-Import ist in diesem Paket bewusst nicht enthalten."),
            color = PrusaColors.TextMuted,
            fontSize = 11.sp,
        )
    }

    if (addingGcode) {
        CustomGcodeDialog(
            initial = PsmCore.CustomGcode(
                printZ = 1.0,
                type = PsmCore.CustomGcodeType.PAUSE,
            ),
            onDismiss = { addingGcode = false },
            onApply = {
                gcodes = (gcodes + it).sortedBy { row -> row.printZ }
                service.replaceCustomGcodes(gcodes)
                addingGcode = false
            },
        )
    }
    editIndex?.let { index ->
        gcodes.getOrNull(index)?.let { initial ->
            CustomGcodeDialog(
                initial = initial,
                onDismiss = { editIndex = null },
                onApply = { updated ->
                    gcodes = gcodes.toMutableList().also {
                        it[index] = updated
                    }.sortedBy { it.printZ }
                    service.replaceCustomGcodes(gcodes)
                    editIndex = null
                },
            )
        }
    }
}

@Composable
private fun CustomGcodeDialog(
    initial: PsmCore.CustomGcode,
    onDismiss: () -> Unit,
    onApply: (PsmCore.CustomGcode) -> Unit,
) {
    var z by remember { mutableStateOf(initial.printZ.toString()) }
    var type by remember { mutableStateOf(initial.type) }
    var extruder by remember { mutableStateOf(initial.extruder.toString()) }
    var colour by remember { mutableStateOf(initial.colour) }
    var extra by remember { mutableStateOf(initial.extra) }
    val parsedZ = NumberCodec.parseDouble(z)
    val parsedExtruder = extruder.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom G-Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = z,
                    onValueChange = { z = it },
                    label = { Text(PsUi.appText("Print height · mm", "Druckhöhe · mm")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PsmCore.CustomGcodeType.entries.forEach { value ->
                        FilterChip(
                            selected = type == value,
                            onClick = { type = value },
                            label = { Text(value.displayName()) },
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
                if (type == PsmCore.CustomGcodeType.COLOR_CHANGE ||
                    type == PsmCore.CustomGcodeType.TOOL_CHANGE) {
                    OutlinedTextField(
                        value = extruder,
                        onValueChange = { extruder = it },
                        label = { Text(PsUi.appText("Extruder · 0 = default", "Extruder · 0 = Standard")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == PsmCore.CustomGcodeType.COLOR_CHANGE) {
                    OutlinedTextField(
                        value = colour,
                        onValueChange = { colour = it },
                        label = { Text(PsUi.appText("Colour · #RRGGBB", "Farbe · #RRGGBB")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text(PsUi.appText("G-code or note", "G-Code oder Notiz")) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedZ != null && parsedZ >= 0.0 &&
                    parsedExtruder != null && parsedExtruder >= 0,
                onClick = {
                    if (parsedZ != null && parsedExtruder != null) {
                        onApply(
                            PsmCore.CustomGcode(
                                printZ = parsedZ,
                                type = type,
                                extruder = parsedExtruder,
                                colour = colour.trim(),
                                extra = extra,
                            )
                        )
                    }
                },
            ) { Text(PsUi.appText("Apply", "Übernehmen")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun ProjectHeading(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProjectNumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

private fun PsmCore.CustomGcodeType.displayName(): String = when (this) {
    PsmCore.CustomGcodeType.COLOR_CHANGE -> PsUi.appText("Colour change", "Farbwechsel")
    PsmCore.CustomGcodeType.PAUSE -> "Pause"
    PsmCore.CustomGcodeType.TOOL_CHANGE -> PsUi.appText("Tool change", "Werkzeugwechsel")
    PsmCore.CustomGcodeType.TEMPLATE -> PsUi.appText("Template", "Vorlage")
    PsmCore.CustomGcodeType.CUSTOM -> PsUi.appText("Custom code", "Eigener Code")
}
