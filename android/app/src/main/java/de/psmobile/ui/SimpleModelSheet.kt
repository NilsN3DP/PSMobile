package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors

/** Die C-ABI erlaubt 36 Druckbetten; siehe PSM_MAX_BEDS. */
private const val MAX_BEDS = 36

private fun t(english: String, german: String) = SimpleModeState.text(english, german)

/**
 * Das Modelle-Blatt am unteren Rand, nach dem Vorbild von EasyPrint.
 *
 * Es loest den einzelnen Knopf "Modell hinzufuegen" ab, sobald etwas auf
 * dem Bett liegt. Damit ist im Simple Mode zum ersten Mal erreichbar,
 * was bisher nur die Advanced-Werkzeugleiste bot: anordnen, klonen,
 * entfernen und auf ein anderes Bett schieben.
 *
 * Zusammengeklappt bleibt nur die Kopfzeile stehen, damit das Bett
 * sichtbar bleibt. Welche Aktion wann etwas bewirkt, entscheidet
 * [SimpleModelSheetState].
 */
@Composable
internal fun SimpleModelSheet(
    service: SlicerService,
    objects: List<PsmCore.ObjectInfo>,
    beds: List<PsmCore.Bed>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onPickFile: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var picked by remember { mutableStateOf(emptySet<Int>()) }
    var moveOpen by remember { mutableStateOf(false) }

    // Geloeschte oder verschobene Objekte duerfen nicht als Geister in der
    // Auswahl bleiben - sonst nennt die Kopfzeile eine Zahl, zu der es
    // keine Zeilen mehr gibt.
    val ids = objects.map { it.id }
    LaunchedEffect(ids) { picked = SimpleModelSheetState.pruned(picked, ids) }

    val activeBed = beds.indexOfFirst { it.active }.coerceAtLeast(0)
    val bedCount = beds.size.coerceAtLeast(1)
    val actions = SimpleModelSheetState.enabledActions(
        objectsOnBed = objects.size,
        selected = picked,
        bedCount = bedCount,
        maxBeds = MAX_BEDS,
    )

    Column(
        modifier
            .padding(end = 12.dp, bottom = bottomInset + 12.dp)
            .widthIn(max = 460.dp)
            .background(PrusaColors.Panel, RoundedCornerShape(2.dp))
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(2.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SimpleModelSheetState.headline(picked),
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (picked.isEmpty()) {
                SheetAction(
                    "▣", t("Select all", "Alle wählen"),
                    SimpleModelSheetState.Action.SELECT_ALL in actions,
                ) { picked = SimpleModelSheetState.selectAll(ids) }
                SheetAction("＋", t("More", "Weitere"), true, onPickFile)
                SheetAction(
                    "▤", t("Arrange", "Anordnen"),
                    SimpleModelSheetState.Action.ARRANGE in actions,
                ) { service.arrange() }
            } else {
                SheetAction("✕", t("Cancel", "Abbrechen"), true) { picked = emptySet() }
                SheetAction(
                    "▤", t("Arrange", "Anordnen"),
                    SimpleModelSheetState.Action.ARRANGE in actions,
                ) { service.arrange() }
                SheetAction(
                    "➜", t("Move to", "Ziehen zu"),
                    SimpleModelSheetState.Action.MOVE_TO_BED in actions,
                ) { moveOpen = true }
                SheetAction(
                    "⧉", t("Clone", "Klonen"),
                    SimpleModelSheetState.Action.CLONE in actions,
                ) { service.duplicateObjects(picked) }
                SheetAction(
                    "✖", t("Remove", "Entfernen"),
                    SimpleModelSheetState.Action.REMOVE in actions,
                ) {
                    service.removeObjects(picked)
                    picked = emptySet()
                }
            }
            SheetAction(
                if (expanded) "⌄" else "⌃",
                t("Collapse", "Einklappen"), true,
            ) { expanded = !expanded }
        }

        if (expanded) {
            HorizontalDivider(color = PrusaColors.Divider)
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                objects.forEach { obj ->
                    SimpleModelRow(
                        obj = obj,
                        checked = obj.id in picked,
                        highlighted = obj.id == selectedId && picked.isEmpty(),
                        selectionMode = picked.isNotEmpty(),
                        onToggle = { picked = SimpleModelSheetState.toggle(picked, obj.id) },
                        onSelect = { onSelect(obj.id) },
                    )
                }
            }
        }
    }

    if (moveOpen) {
        val targets = SimpleModelSheetState.moveTargets(bedCount, activeBed, MAX_BEDS)
        AlertDialog(
            onDismissRequest = { moveOpen = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = { Text(t("Move to bed", "Auf Bett verschieben")) },
            text = {
                Column {
                    targets.forEach { target ->
                        val isNew = target >= beds.size
                        OutlinedButton(
                            onClick = {
                                if (isNew) service.addBed()
                                picked.forEach { service.moveObjectToBed(it, target) }
                                picked = emptySet()
                                moveOpen = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(
                                if (isNew) t("New bed", "Neues Bett")
                                else t("Bed ", "Bett ") + (target + 1)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveOpen = false }) {
                    Text(t("Cancel", "Abbrechen"), color = PrusaColors.Orange)
                }
            },
        )
    }
}

@Composable
private fun SheetAction(
    glyph: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            glyph,
            color = if (enabled) PrusaColors.TextPrimary else PrusaColors.TextMuted,
            fontSize = 17.sp,
        )
        Text(
            label,
            color = if (enabled) PrusaColors.TextMuted else PrusaColors.Divider,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun SimpleModelRow(
    obj: PsmCore.ObjectInfo,
    checked: Boolean,
    highlighted: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (checked || highlighted) PrusaColors.PanelRaised else PrusaColors.Panel
            )
            .clickable { if (selectionMode) onToggle() else onSelect() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(2.dp)).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (checked) "☑" else "☐",
                color = if (checked) PrusaColors.Orange else PrusaColors.TextMuted,
                fontSize = 17.sp,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                obj.name,
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "%.0f × %.0f × %.0f mm".format(
                    obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third,
                ) + if (obj.instances > 1) " · " + obj.instances + "×" else "",
                color = PrusaColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        // Rechts der Kopf, mit dem gedruckt wird - wie die Spule in EasyPrint.
        Text(
            "T" + obj.extruder.coerceAtLeast(1),
            color = PrusaColors.Orange,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
