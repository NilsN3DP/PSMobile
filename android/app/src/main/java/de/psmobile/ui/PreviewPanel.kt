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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.shared.rules.PreviewRange
import de.psmobile.shared.rules.PreviewRoles
import de.psmobile.shared.ui.Corners
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.psTouch

/*
 * Statistik und Legende der fertigen Vorschau.
 *
 * Beides gab es auf Android bisher gar nicht: nach dem Slicen sah man
 * die Wege, aber nicht, was sie kosten, und man konnte nichts
 * ausblenden. iOS hat es in beiden Modi (FinalPreviewPanel.swift), und
 * die Zahlen kommen aus derselben Rechnung im gemeinsamen Modul.
 */

/** 0xRRGGBBAA aus dem Kern in eine Compose-Farbe. */
private fun rgba(value: Long): Color = Color(
    red = ((value shr 24) and 0xFF).toInt(),
    green = ((value shr 16) and 0xFF).toInt(),
    blue = ((value shr 8) and 0xFF).toInt(),
    alpha = 0xFF,
)

/**
 * Zeit, Filament und Hoehe des eingestellten Schichtbereichs.
 *
 * Nicht des ganzen Drucks: wer den Regler zusammenschiebt, will wissen,
 * was dieser Ausschnitt kostet.
 */
@Composable
internal fun PreviewStatsRow(
    range: PreviewRange,
    data: SlicerService.PreviewData,
    modifier: Modifier = Modifier,
) {
    val werte = range.stats(data.layers)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kennzahl("⏱", PreviewRange.duration(werte.timeSeconds))
        Kennzahl("⎯", "%.2f m".format(werte.filamentMm / 1000.0))
        Kennzahl("⚖", "%.1f g".format(werte.filamentGrams))
        Spacer(Modifier.weight(1f))
        Text(
            "%.2f–%.2f mm".format(werte.zLower, werte.zUpper),
            color = PrusaColors.TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Kennzahl(zeichen: String, wert: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(zeichen, color = PrusaColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.size(4.dp))
        Text(
            wert,
            color = PrusaColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Umschalter zwischen Merkmalen und Extrudern, darunter je ein Chip mit
 * Farbpunkt.
 *
 * Ein Tipp blendet diese Rolle beziehungsweise diesen Extruder aus und
 * wieder ein - genau wie am Desktop. Ausgeblendetes bleibt sichtbar,
 * nur gedaempft: sonst weiss man nicht mehr, was man weggeschaltet hat.
 */
@Composable
internal fun PreviewLegendPicker(
    data: SlicerService.PreviewData,
    view: PsmViewport.PreviewView,
    hiddenRoles: Set<Int>,
    hiddenExtruders: Set<Int>,
    onView: (PsmViewport.PreviewView) -> Unit,
    onToggleRole: (Int) -> Unit,
    onToggleExtruder: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Umschalter(
                PsUi.appText("Features", "Merkmale"),
                view == PsmViewport.PreviewView.FEATURE,
                Modifier.weight(1f),
            ) { onView(PsmViewport.PreviewView.FEATURE) }
            Umschalter(
                PsUi.appText("Extruders", "Extruder"),
                view == PsmViewport.PreviewView.EXTRUDER,
                Modifier.weight(1f),
            ) { onView(PsmViewport.PreviewView.EXTRUDER) }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (view == PsmViewport.PreviewView.FEATURE) {
                data.roles.forEach { rolle ->
                    LegendenChip(
                        farbe = rgba(rolle.colorRgba),
                        text = PsUi.appText(
                            PreviewRoles.name(rolle.role).english,
                            PreviewRoles.name(rolle.role).german,
                        ),
                        versteckt = rolle.role in hiddenRoles,
                    ) { onToggleRole(rolle.role) }
                }
            } else {
                data.extruders.forEach { extruder ->
                    LegendenChip(
                        farbe = rgba(extruder.colorRgba),
                        text = "T${extruder.extruder + 1}",
                        versteckt = extruder.extruder in hiddenExtruders,
                    ) { onToggleExtruder(extruder.extruder) }
                }
            }
        }
    }
}

@Composable
private fun Umschalter(
    text: String,
    aktiv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(psTouch(40))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(if (aktiv) PrusaColors.Orange else PrusaColors.PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (aktiv) PrusaColors.Background else PrusaColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LegendenChip(
    farbe: Color,
    text: String,
    versteckt: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(psTouch(44))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(PrusaColors.PanelRaised)
            .border(
                1.dp,
                if (versteckt) PrusaColors.Divider else farbe,
                RoundedCornerShape(Corners.FIELD.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (versteckt) PrusaColors.Divider else farbe),
        )
        Text(
            text,
            color = if (versteckt) PrusaColors.TextMuted else PrusaColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Was jede Rolle gekostet hat.
 *
 * Nur bei mehr als einem Extruder: bei einem einfarbigen Druck steht
 * dieselbe Zahl zwei Zeilen darueber, und eine Wiederholung ist keine
 * Auskunft.
 *
 * Der Reinigungsturm bekommt eine eigene Spalte. Bei einem
 * Mehrfarbdruck ist er oft die Haelfte des Verbrauchs, und wer nur die
 * Modellzahl sieht, wundert sich ueber die Rolle.
 */
@Composable
internal fun PreviewUsageRows(
    data: SlicerService.PreviewData,
    farbeVon: (Int) -> Color,
    modifier: Modifier = Modifier,
) {
    if (data.usage.size <= 1) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                PsUi.appText("Per tool", "Je Werkzeug"),
                color = PrusaColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                PsUi.appText("Model", "Modell") + " · " + PsUi.appText("Tower", "Turm"),
                color = PrusaColors.TextMuted,
                fontSize = 9.sp,
            )
        }
        data.usage.forEach { u ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(farbeVon(u.extruder)),
                )
                Spacer(Modifier.size(6.dp))
                Text("T${u.extruder + 1}", color = PrusaColors.TextMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "%.1f cm³".format(u.volumeMm3 / 1000.0),
                    color = PrusaColors.TextPrimary,
                    fontSize = 11.sp,
                )
                if (u.wipeTowerMm3 + u.flushMm3 > 0) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "+ %.1f".format((u.wipeTowerMm3 + u.flushMm3) / 1000.0),
                        color = PrusaColors.Orange,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
