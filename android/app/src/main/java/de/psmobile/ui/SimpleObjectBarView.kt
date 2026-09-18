package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Die schwebende Leiste am ausgewaehlten Objekt - Gegenstueck zu
 * `ios/PSMobile/Screens/SimpleObjectBarView.swift`.
 *
 * Sie erscheint, sobald ein Objekt angetippt ist, und verschwindet mit
 * der Auswahl. Bewusst oben und nicht unten: unten liegt bereits das
 * Modelle-Blatt, und das ausgewaehlte Objekt selbst soll sichtbar
 * bleiben.
 */
@Composable
fun SimpleObjectBarView(
    model: SlicerModel,
    objekt: PsmCore.ObjectInfo,
    /** Im Simple Mode setzt dieser Knopf die Auswahl zurueck. */
    zeigtZurueck: Boolean,
    onClearSelection: () -> Unit,
    /** Meldet, ob das Flaechenwerkzeug an ist. */
    onFlaechenwahl: (Boolean) -> Unit = {},
    /** Nur der Advanced Mode uebergibt das - im Simple Mode sitzt die Griffwahl schon im Werkzeug-Blatt. */
    gizmo: PsmViewport.Gizmo? = null,
    onGizmoChange: (PsmViewport.Gizmo) -> Unit = {},
    /** Obergrenze in Punkten, statt der festen ps.pt(640). */
    maxBreite: Dp? = null,
) {
    val ps = LocalPsScale.current
    var zeigeSchnitt by remember { mutableStateOf(false) }
    var flaechenwahl by remember { mutableStateOf(false) }
    var schnittHoehe by remember { mutableFloatStateOf(0f) }

    Row(
        Modifier
            .widthIn(max = maxBreite ?: ps.pt(640))
            .fillMaxWidth()
            .wrapContentHeight()
            .background(PrusaColors.panel.copy(alpha = 0.95f))
            .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(2)))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ps.pt(4))
            .testTag("objektleiste"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (gizmo != null) {
            griffKnopf("arrow.up.and.down.and.arrow.left.and.right",
                st("Move", "Verschieben"), PsmViewport.Gizmo.MOVE, gizmo, onGizmoChange)
            griffKnopf("arrow.triangle.2.circlepath",
                st("Rotate", "Drehen"), PsmViewport.Gizmo.ROTATE, gizmo, onGizmoChange)
            griffKnopf("arrow.up.left.and.arrow.down.right",
                st("Scale", "Skalieren"), PsmViewport.Gizmo.SCALE, gizmo, onGizmoChange)
            griffKnopf("hand.point.up.left",
                st("None", "Kein"), PsmViewport.Gizmo.NONE, gizmo, onGizmoChange)
            VerticalDivider(
                modifier = Modifier.height(ps.pt(28)).padding(horizontal = ps.pt(2)),
                color = PrusaColors.divider,
            )
        }
        objektAktion("✂", st("Cut", "Schneiden"), kennung = "objekt.schneiden") {
            schnittHoehe = objekt.sizeMm.third / 2
            zeigeSchnitt = true
        }
        objektAktion("⧅", st("Split", "Teilen"), kennung = "objekt.teilen") {
            model.split(objekt.id)
        }
        objektAktion("⧉", st("Clone", "Klonen"), kennung = "objekt.klonen") {
            model.duplicate(listOf(objekt.id))
        }
        objektAktion("⭳", st("Drop", "Ablegen"), kennung = "objekt.ablegen") {
            model.dropToBed(objekt.id)
        }
        // Ein Tippen, kein Nachdenken: die groesste ebene Flaeche kommt nach unten.
        objektAktion("⬓", st("Lay flat", "Hinlegen"), kennung = "objekt.hinlegen") {
            model.layFlat(objekt.id)
        }
        // Und fuer die Faelle, in denen die groesste Flaeche nicht die gemeinte ist: eine antippen.
        objektAktion("◈", st("On face", "Auf Fläche"), kennung = "objekt.aufflaeche", aktiv = flaechenwahl) {
            flaechenwahl = !flaechenwahl
            onFlaechenwahl(flaechenwahl)
        }
        objektAktion("⇔", st("Fit", "Einpassen"), kennung = "objekt.einpassen") {
            model.fitToBed(objekt.id)
        }
        objektAktion("✖", st("Remove", "Entfernen"), kennung = "objekt.entfernen") {
            model.removeObjects(listOf(objekt.id))
            onClearSelection()
        }
        if (zeigtZurueck) {
            objektAktion("←", st("Back", "Zurück"), kennung = "objekt.zurueck", aktion = onClearSelection)
        }
    }

    if (zeigeSchnitt) {
        Dialog(
            onDismissRequest = { zeigeSchnitt = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(
                    Modifier
                        .padding(ps.pt(16))
                        .widthIn(max = ps.pt(520))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(PrusaColors.panel),
                ) {
                    schnittBlatt(
                        model = model,
                        objekt = objekt,
                        schnittHoehe = schnittHoehe,
                        onSchnittHoeheChange = { schnittHoehe = it },
                        onClose = { zeigeSchnitt = false },
                        onClearSelection = onClearSelection,
                    )
                }
            }
        }
    }
}

/**
 * Schnitthoehe waehlen. Ein Zahlenfeld waere hier die schlechtere Wahl:
 * man weiss vorher selten, bei welchem Millimeterwert man schneiden
 * will, sondern ungefaehr wo. Der Regler zeigt den Wert zusaetzlich an.
 */
@Composable
private fun schnittBlatt(
    model: SlicerModel,
    objekt: PsmCore.ObjectInfo,
    schnittHoehe: Float,
    onSchnittHoeheChange: (Float) -> Unit,
    onClose: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(PrusaColors.background)
            .padding(ps.pt(20)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(16)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(st("Cut", "Schneiden"), fontSize = ps.font(18), color = PrusaColors.textPrimary)
        Text("%.1f mm".format(Locale.US, schnittHoehe), fontSize = ps.font(14), color = PrusaColors.textMuted)
        Slider(
            value = schnittHoehe,
            onValueChange = onSchnittHoeheChange,
            valueRange = 0f..maxOf(objekt.sizeMm.third, 1f),
            colors = SliderDefaults.colors(
                thumbColor = PrusaColors.orange,
                activeTrackColor = PrusaColors.orange,
                inactiveTrackColor = PrusaColors.panelRaised,
            ),
            modifier = Modifier.fillMaxWidth().testTag("objekt.schnitthoehe"),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) {
                Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .testTag("objekt.schnitt.ausfuehren")
                    .height(ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(PrusaColors.orange)
                    .clickable {
                        // Beide Haelften behalten und als eigene Objekte,
                        // nicht als Teile eines gemeinsamen: im Simple Mode
                        // gibt es keinen Objektbaum, in dem man Teile
                        // wiederfaende.
                        model.cut(objekt.id, zMm = schnittHoehe)
                        onClose()
                        onClearSelection()
                    }
                    .padding(horizontal = ps.pt(20)),
                contentAlignment = Alignment.Center,
            ) {
                Text(st("Cut", "Schneiden"), color = Color.White)
            }
        }
    }
}

/** Dieselbe Kennung wie zuvor die schwebende Leiste - vorhandene Tests bleiben gueltig. */
@Composable
private fun griffKnopf(
    symbol: String,
    label: String,
    wert: PsmViewport.Gizmo,
    gizmo: PsmViewport.Gizmo,
    onGizmoChange: (PsmViewport.Gizmo) -> Unit,
) {
    val ps = LocalPsScale.current
    val aktiv = gizmo == wert
    val farbe = if (aktiv) PrusaColors.background else PrusaColors.textPrimary
    Column(
        Modifier
            .testTag("advanced.gizmo." + kennungFuer(wert))
            .size(ps.pt(58), ps.touch(46))
            .background(if (aktiv) PrusaColors.orange else Color.Transparent)
            .clickable { onGizmoChange(wert) },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(15).value.dp), tint = farbe)
        Text(label, fontSize = ps.font(8), color = farbe, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun kennungFuer(wert: PsmViewport.Gizmo): String = when (wert) {
    PsmViewport.Gizmo.MOVE -> "move"
    PsmViewport.Gizmo.ROTATE -> "rotate"
    PsmViewport.Gizmo.SCALE -> "scale"
    else -> "none"
}

@Composable
private fun objektAktion(
    glyph: String,
    label: String,
    kennung: String,
    aktiv: Boolean = false,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (aktiv) PrusaColors.background else PrusaColors.textPrimary
    Column(
        Modifier
            .testTag(kennung)
            .size(ps.pt(58), ps.touch(46))
            .background(if (aktiv) PrusaColors.orange else Color.Transparent)
            .clickable(onClick = aktion),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = ps.font(15), color = farbe)
        Text(label, fontSize = ps.font(8), color = farbe, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
