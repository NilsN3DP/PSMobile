package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale

/**
 * Mobile Bedienung der Prusa-TriangleSelector-Werkzeuge - Port von
 * `ios/PSMobile/Screens/PaintView.swift`.
 *
 * Die Oberflaeche besitzt genau einen Optionswert. Derselbe Wert geht
 * an den Kern und an den Viewport; markierte Facetten werden hier nie
 * gespiegelt oder nachgerechnet.
 *
 * [options]/[onOptionsChange] entsprechen `@Binding var options`.
 */
@Composable
fun PaintView(
    model: SlicerModel = LocalSlicerModel.current,
    objektId: Int,
    options: PsmCore.PaintOptions,
    onOptionsChange: (PsmCore.PaintOptions) -> Unit,
) {
    val ps = LocalPsScale.current

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Paint", "Bemalen").uppercase(),
            color = PrusaColors.textMuted,
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wahl(st("Off", "Aus"), an = options.tool == null, kennung = "malen.aus") {
                onOptionsChange(options.copy(tool = null))
            }
            Werkzeug(PsUiCatalog.tr("Supports"), PsmCore.PaintTool.SUPPORT,
                kennung = "malen.stuetzen", options = options, onOptionsChange = onOptionsChange)
            Werkzeug(PsUiCatalog.tr("Seam"), PsmCore.PaintTool.SEAM,
                kennung = "malen.naht", options = options, onOptionsChange = onOptionsChange)
            if (model.extruderCount > 1) {
                Werkzeug("MMU", PsmCore.PaintTool.MMU,
                    kennung = "malen.mmu", options = options, onOptionsChange = onOptionsChange)
            }
        }

        val aktiv = options.tool
        if (aktiv != null) {
            ZustandsWahl(aktiv, model, options, onOptionsChange)
            ModusWahl(options, onOptionsChange)

            if (options.mode == PsmCore.PaintMode.BRUSH) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wahl(st("Circle", "Kreis"),
                        an = options.shape == PsmCore.PaintShape.CIRCLE,
                        kennung = "malen.form.kreis") {
                        onOptionsChange(options.copy(shape = PsmCore.PaintShape.CIRCLE))
                    }
                    Wahl(st("Sphere", "Kugel"),
                        an = options.shape == PsmCore.PaintShape.SPHERE,
                        kennung = "malen.form.kugel") {
                        onOptionsChange(options.copy(shape = PsmCore.PaintShape.SPHERE))
                    }
                }
                Regler(
                    titel = st("Size", "Größe"),
                    wert = options.radiusMm,
                    onWertChange = { onOptionsChange(options.copy(radiusMm = it)) },
                    bereich = 1f..20f,
                    kennung = "malen.radius",
                    ausgabe = String.format(Locale.US, "%.0f mm", options.radiusMm),
                )
            } else {
                Regler(
                    titel = st("Angle", "Winkel"),
                    wert = options.fillAngleDeg,
                    onWertChange = { onOptionsChange(options.copy(fillAngleDeg = it)) },
                    bereich = 0f..90f,
                    kennung = "malen.winkel",
                    ausgabe = String.format(Locale.US, "%.0f°", options.fillAngleDeg),
                )
            }

            Text(
                st("Object (all copies)", "Objekt (alle Kopien)"),
                modifier = Modifier.testTag("malen.scope"),
                color = PrusaColors.textMuted,
                fontSize = ps.font(11),
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    st("Marked", "Markiert") + ": ${model.paintCount(objektId, aktiv)}",
                    modifier = Modifier.testTag("malen.anzahl"),
                    color = PrusaColors.textMuted,
                    fontSize = ps.font(11),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = ps.touch(40))
                        .clickable { model.clearPaint(objektId, aktiv) }
                        .testTag("malen.loeschen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        st("Clear", "Löschen"),
                        color = PrusaColors.danger,
                        fontSize = ps.font(12),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModusWahl(
    options: PsmCore.PaintOptions,
    onOptionsChange: (PsmCore.PaintOptions) -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.supportedModes.forEach { mode ->
            Wahl(modusName(mode), an = options.mode == mode, kennung = modusKennung(mode)) {
                onOptionsChange(options.copy(mode = mode))
            }
        }
    }
}

@Composable
private fun RowScope.Werkzeug(
    label: String,
    tool: PsmCore.PaintTool,
    kennung: String,
    options: PsmCore.PaintOptions,
    onOptionsChange: (PsmCore.PaintOptions) -> Unit,
) {
    Wahl(label, an = options.tool == tool, kennung = kennung) {
        onOptionsChange(options.copy(tool = tool, state = 1).normalizedForTool())
    }
}

@Composable
private fun ZustandsWahl(
    aktiv: PsmCore.PaintTool,
    model: SlicerModel,
    options: PsmCore.PaintOptions,
    onOptionsChange: (PsmCore.PaintOptions) -> Unit,
) {
    val ps = LocalPsScale.current
    if (aktiv == PsmCore.PaintTool.MMU) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wahl(st("Erase", "Radieren"), an = options.state == 0, kennung = "malen.zustand.0") {
                onOptionsChange(options.copy(state = 0))
            }
            for (nummer in 1..model.extruderCount) {
                Wahl("$nummer", an = options.state == nummer, kennung = "malen.zustand.$nummer") {
                    onOptionsChange(options.copy(state = nummer))
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wahl(PsUiCatalog.tr("Enforce"), an = options.state == 1, kennung = "malen.zustand.1") {
                onOptionsChange(options.copy(state = 1))
            }
            Wahl(st("Block", "Blockieren"), an = options.state == 2, kennung = "malen.zustand.2") {
                onOptionsChange(options.copy(state = 2))
            }
            Wahl(st("Erase", "Radieren"), an = options.state == 0, kennung = "malen.zustand.0") {
                onOptionsChange(options.copy(state = 0))
            }
        }
    }
}

@Composable
private fun Regler(
    titel: String,
    wert: Float,
    onWertChange: (Float) -> Unit,
    bereich: ClosedFloatingPointRange<Float>,
    kennung: String,
    ausgabe: String,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titel,
            modifier = Modifier.width(ps.pt(56)),
            color = PrusaColors.textMuted,
            fontSize = ps.font(12),
        )
        Slider(
            value = wert,
            onValueChange = onWertChange,
            valueRange = bereich,
            modifier = Modifier
                .weight(1f)
                .testTag(kennung),
            colors = SliderDefaults.colors(
                thumbColor = PrusaColors.orange,
                activeTrackColor = PrusaColors.orange,
            ),
        )
        Text(
            ausgabe,
            modifier = Modifier.width(ps.pt(52)),
            color = PrusaColors.textPrimary,
            fontSize = ps.font(12),
            textAlign = TextAlign.End,
        )
    }
}

private fun modusName(mode: PsmCore.PaintMode): String = when (mode) {
    PsmCore.PaintMode.BRUSH -> st("Brush", "Pinsel")
    PsmCore.PaintMode.SMART_FILL -> "Smart Fill"
    PsmCore.PaintMode.BUCKET_FILL -> st("Bucket", "Eimer")
}

private fun modusKennung(mode: PsmCore.PaintMode): String = when (mode) {
    PsmCore.PaintMode.BRUSH -> "malen.modus.pinsel"
    PsmCore.PaintMode.SMART_FILL -> "malen.modus.smart"
    PsmCore.PaintMode.BUCKET_FILL -> "malen.modus.eimer"
}

@Composable
private fun RowScope.Wahl(
    label: String,
    an: Boolean,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val zustand = if (an) st("Selected", "Ausgewählt") else ""
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(if (an) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable(onClick = aktion)
            .semantics { stateDescription = zustand }
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (an) Color.White else PrusaColors.textPrimary,
            fontSize = ps.font(12),
            fontWeight = if (an) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
