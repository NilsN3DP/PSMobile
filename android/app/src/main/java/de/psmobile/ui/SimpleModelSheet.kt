package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.SimpleModelSheetState

/** Die C-ABI erlaubt 36 Druckbetten; siehe PSM_MAX_BEDS. */
private const val MAX_BEDS = 36

/** Hoehe einer Modellzeile und wie viele davon ohne Scrollen sichtbar sind. */
private val ROW_HEIGHT = 52.dp
private const val VISIBLE_ROWS = 3

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
    var picked by remember { mutableStateOf(emptyList<Int>()) }
    var moveOpen by remember { mutableStateOf(false) }
    val showThumbs = remember(objects.size) { service.thumbnailsEnabled() }

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
            .widthIn(max = 380.dp)
            .background(PrusaColors.Panel, RoundedCornerShape(2.dp))
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(2.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SimpleModelSheetState.headline(picked),
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
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
                SheetAction("✕", t("Cancel", "Abbrechen"), true) { picked = emptyList() }
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
                ) { service.duplicateObjects(picked.toSet()) }
                SheetAction(
                    "✖", t("Remove", "Entfernen"),
                    SimpleModelSheetState.Action.REMOVE in actions,
                ) {
                    service.removeObjects(picked.toSet())
                    picked = emptyList()
                }
            }
            SheetAction(
                if (expanded) "⌄" else "⌃",
                t("Collapse", "Einklappen"), true,
            ) { expanded = !expanded }
        }

        if (expanded) {
            HorizontalDivider(color = PrusaColors.Divider)
            // Gedeckelte Hoehe statt frei mitwachsend: sonst schob sich
            // das Blatt mit jeder weiteren Datei weiter ueber das Bett,
            // bis vom Modell nichts mehr zu sehen war. Bis zu drei Zeilen
            // sind sichtbar, der Rest wird gescrollt. Bei weniger als
            // drei bleibt kein Leerraum stehen.
            Column(
                Modifier
                    .height(ROW_HEIGHT * objects.size.coerceAtMost(VISIBLE_ROWS))
                    .verticalScroll(rememberScrollState()),
            ) {
                objects.forEach { obj ->
                    SimpleModelRow(
                        obj = obj,
                        checked = obj.id in picked,
                        highlighted = obj.id == selectedId && picked.isEmpty(),
                        selectionMode = picked.isNotEmpty(),
                        showThumb = showThumbs,
                        onToggle = { picked = SimpleModelSheetState.toggle(picked, obj.id) },
                        onSelect = { onSelect(obj.id) },
                    )
                }
            }

            // Verschieben legt bei Bedarf ein weiteres Bett an. Ohne eine
            // Auswahl kaeme man dort nie wieder hin - das Objekt waere
            // verschwunden statt verschoben.
            if (beds.size > 1) {
                HorizontalDivider(color = PrusaColors.Divider)
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    beds.forEachIndexed { index, bed ->
                        val active = index == activeBed
                        Box(
                            Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (active) PrusaColors.Orange else PrusaColors.PanelRaised
                                )
                                .clickable { service.selectBed(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                t("Bed ", "Bett ") + (index + 1) + " · " + bed.objectCount,
                                color = if (active) PrusaColors.Background
                                        else PrusaColors.TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (moveOpen) {
        MoveToBedDialog(
            service = service,
            beds = beds,
            activeBed = activeBed,
            ids = picked,
            onDone = { picked = emptyList(); moveOpen = false },
            onDismiss = { moveOpen = false },
        )
    }
}

/**
 * Objekte auf ein anderes Bett schieben.
 *
 * Wird von zwei Stellen gebraucht - der Auswahl im Modelle-Blatt und der
 * Leiste am einzelnen Objekt -, deshalb hier einmal statt zweimal.
 */
@Composable
internal fun MoveToBedDialog(
    service: SlicerService,
    beds: List<PsmCore.Bed>,
    activeBed: Int,
    ids: List<Int>,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = SimpleModelSheetState.moveTargets(
        beds.size.coerceAtLeast(1), activeBed, MAX_BEDS,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
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
                            ids.forEach { service.moveObjectToBed(it, target) }
                            onDone()
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
            TextButton(onClick = onDismiss) {
                Text(t("Cancel", "Abbrechen"), color = PrusaColors.Orange)
            }
        },
    )
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
    showThumb: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (checked || highlighted) PrusaColors.PanelRaised else PrusaColors.Panel
            )
            .clickable { if (selectionMode) onToggle() else onSelect() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(2.dp)).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (checked) "☑" else "☐",
                color = if (checked) PrusaColors.Orange else PrusaColors.TextMuted,
                fontSize = 15.sp,
            )
        }
        if (showThumb) ObjectProportionThumb(obj)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                obj.name,
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "%.0f × %.0f × %.0f mm".format(
                    obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third,
                ) + if (obj.instances > 1) " · " + obj.instances + "×" else "",
                color = PrusaColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        // Rechts der Kopf, mit dem gedruckt wird - wie die Spule in EasyPrint.
        Text(
            "T" + obj.extruder.coerceAtLeast(1),
            color = PrusaColors.Orange,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Kleine Vorschau je Zeile.
 *
 * Ein echtes gerendertes Miniaturbild braeuchte einen zweiten
 * Offscreen-Durchlauf im Viewport je Objekt; das steht als eigene
 * Aufgabe an. Bis dahin zeigt der Platzhalter wenigstens die
 * tatsaechlichen Proportionen des Huellquaders - damit lassen sich
 * flache Platten und hohe Tuerme in der Liste bereits unterscheiden,
 * statt dass alle Zeilen gleich aussehen.
 */
@Composable
private fun ObjectProportionThumb(obj: PsmCore.ObjectInfo) {
    val w = obj.sizeMm.first.coerceAtLeast(0.1f)
    val d = obj.sizeMm.second.coerceAtLeast(0.1f)
    val h = obj.sizeMm.third.coerceAtLeast(0.1f)
    val longest = maxOf(w, d, h)
    val boxW = (26f * (maxOf(w, d) / longest)).coerceAtLeast(4f)
    val boxH = (26f * (h / longest)).coerceAtLeast(4f)
    Box(
        Modifier.size(36.dp).background(PrusaColors.Background, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(boxW.dp, boxH.dp)
                .background(PrusaColors.Orange, RoundedCornerShape(1.dp)),
        )
    }
}
