package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.shared.rules.SimpleModeState

private fun t(english: String, german: String) = SimpleModeState.text(english, german)

/**
 * Die schwebende Leiste am ausgewaehlten Objekt, nach EasyPrint-Vorbild.
 *
 * Sie erscheint, sobald ein Objekt angetippt ist, und verschwindet mit
 * der Auswahl. Bisher waren Schneiden, Teilen und Klonen im Simple Mode
 * gar nicht erreichbar - man musste in den Advanced Mode wechseln, nur
 * um ein Objekt zu halbieren.
 *
 * Bewusst oben und nicht unten: unten liegen bereits Undo/Redo und das
 * Modelle-Blatt, und das ausgewaehlte Objekt selbst soll sichtbar
 * bleiben.
 */
@Composable
internal fun SimpleObjectBar(
    service: SlicerService,
    obj: PsmCore.ObjectInfo,
    beds: List<PsmCore.Bed>,
    onClearSelection: () -> Unit,
    flaechenwahl: Boolean = false,
    onFlaechenwahl: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var cutOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    Row(
        modifier
            .widthIn(max = 640.dp)
            .background(PrusaColors.Panel.copy(alpha = 0.95f), RoundedCornerShape(Corners.FIELD.dp))
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarAction("✂", t("Cut", "Schneiden")) { cutOpen = true }
        BarAction("⧅", t("Split", "Teilen")) { service.splitIntoObjects(obj.id) }
        BarAction("⧉", t("Clone", "Klonen")) { service.duplicate(obj.id) }
        BarAction("➜", t("Move to", "Ziehen zu")) { moveOpen = true }
        // Platzieren - dieselben vier wie in SimpleObjectBarView.swift.
        BarAction("⭳", t("Drop", "Ablegen")) { service.dropToBed(obj.id) }
        // Ein Tippen, kein Nachdenken: die groesste ebene Flaeche kommt
        // nach unten.
        BarAction("⬓", t("Lay flat", "Hinlegen")) { service.layFlatAuto(obj.id) }
        // Und fuer die Faelle, in denen die groesste Flaeche nicht die
        // gemeinte ist: eine antippen.
        BarAction("◈", t("On face", "Auf Fläche"), aktiv = flaechenwahl) {
            onFlaechenwahl(!flaechenwahl)
        }
        BarAction("⇔", t("Fit", "Einpassen")) { service.fitToBed(obj.id) }
        BarAction("✖", t("Remove", "Entfernen")) {
            service.removeObject(obj.id)
            onClearSelection()
        }
        BarAction("←", t("Back", "Zurück"), onClick = onClearSelection)
    }

    if (moveOpen) {
        MoveToBedDialog(
            service = service,
            beds = beds,
            ids = listOf(obj.id),
            onDone = { moveOpen = false; onClearSelection() },
            onDismiss = { moveOpen = false },
        )
    }

    if (cutOpen) {
        CutDialog(
            heightMm = obj.sizeMm.third,
            onCut = { z ->
                // Beide Haelften behalten und als eigene Objekte, nicht
                // als Teile eines gemeinsamen: im Simple Mode gibt es
                // keinen Objektbaum, in dem man Teile wiederfaende.
                service.cutObject(obj.id, z, keepUpper = true, keepLower = true,
                                  keepAsParts = false)
                cutOpen = false
            },
            onDismiss = { cutOpen = false },
        )
    }
}

/**
 * Schnitthoehe waehlen.
 *
 * Ein Zahlenfeld waere hier die schlechtere Wahl: man weiss vorher
 * selten, bei welchem Millimeterwert man schneiden will, sondern
 * ungefaehr wo. Der Regler zeigt zusaetzlich den Wert.
 */
@Composable
private fun CutDialog(
    heightMm: Float,
    onCut: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val top = heightMm.coerceAtLeast(0.2f)
    var z by remember(heightMm) { mutableStateOf(top / 2f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(t("Cut", "Schneiden")) },
        text = {
            Column {
                Text(
                    "%.1f mm".format(z),
                    color = PrusaColors.Orange,
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = z,
                    onValueChange = { z = it },
                    // Genau am Boden oder ganz oben zu schneiden ergibt
                    // eine leere Haelfte - deshalb ein Rand.
                    valueRange = 0.2f..(top - 0.2f).coerceAtLeast(0.4f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    t(
                        "Both halves stay as separate objects.",
                        "Beide Hälften bleiben als eigene Objekte.",
                    ),
                    color = PrusaColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCut(z) }) {
                Text(t("Cut", "Schneiden"), color = PrusaColors.Orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "Abbrechen")) }
        },
    )
}

@Composable
private fun BarAction(
    glyph: String,
    label: String,
    /** Fuer Werkzeuge, die eingeschaltet bleiben, bis man sie wieder
     *  ausschaltet - "Auf Flaeche" wartet auf den naechsten Tipp im
     *  Viewport und muss das sichtbar machen. */
    aktiv: Boolean = false,
    onClick: () -> Unit,
) {
    val farbe = if (aktiv) PrusaColors.Orange else PrusaColors.TextPrimary
    Column(
        Modifier
            .widthIn(min = 60.dp)
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(glyph, color = farbe, fontSize = 17.sp)
        Text(
            label,
            color = if (aktiv) PrusaColors.Orange else PrusaColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
