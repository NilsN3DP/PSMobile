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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.shared.rules.PreviewLayerMetrics
import de.psmobile.shared.rules.PreviewRange
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Gemeinsame Preview-Karte - Port von
 * `ios/PSMobile/Screens/FinalPreviewPanel.swift`. Nur ihre Anordnung
 * unterscheidet sich: Tablet rechts kompakt, Telefon unten als Sheet.
 *
 * Nur fuer den Simple Mode - der Advanced Mode zeigt Statistik und
 * Legende direkt im rechten Menueband ([PreviewStatsRow] /
 * [PreviewLegendPicker]) und ersetzt die beiden Schichtregler durch
 * eigene Randregler am Viewport.
 *
 * Auf iOS traegt der Snapshot die Schichten, Rollen und Extruder als
 * Listen; auf Android stehen im Snapshot nur die Anzahlen, die Listen
 * kommen aus `model.core` - deshalb das zusaetzliche `model`.
 */
@Composable
fun FinalPreviewOverlay(
    snapshot: PsmCore.PreviewSnapshot,
    range: PreviewRange,
    onRangeChange: (PreviewRange) -> Unit,
    view: PsmViewport.PreviewView,
    onViewChange: (PsmViewport.PreviewView) -> Unit,
    hiddenRoles: Set<Int>,
    onHiddenRolesChange: (Set<Int>) -> Unit,
    hiddenExtruders: Set<Int>,
    onHiddenExtrudersChange: (Set<Int>) -> Unit,
    onEditor: () -> Unit,
    /**
     * Was oben frei bleiben muss - Kopf und Werkzeugleiste des Simple Mode.
     * Bis zum 16.09.2026 lag die Seitenkarte im kurzen Fenster (Telefon
     * quer) mittig und damit auf Slice- und Vorschau-Knopf (S23 FE).
     * Gegenstueck: `topInset` in FinalPreviewPanel.swift.
     */
    topInset: Dp = 0.dp,
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    // Nach dem Fenster, nicht nach dem Geraet - ein Telefon quer ist breit
    // genug fuer die Seitenkarte, ein iPad im schmalen Split View nicht.
    val isPad = ps.windowSize.width >= 600.dp

    // Oben unter der Werkzeugleiste, nicht mittig: auf iOS fuellt der
    // ScrollView seine 85 % Hoehe und zeigt die Karte oben - Android
    // sass bis zum 16.09.2026 mit CenterEnd auf halber Hoehe (Emulator).
    Box(
        contentAlignment = if (isPad) Alignment.TopEnd else Alignment.BottomCenter,
        // Rechts bleibt die Werkzeugspalte (58 + 10 pt) frei - sonst lagen
        // "Ansicht" und "Bett" hinter der Karte.
        modifier = Modifier.fillMaxSize()
            .padding(top = if (isPad) topInset else 0.dp, end = if (isPad) ps.pt(68) else 0.dp),
    ) {
        val panelModifier = (if (isPad)
            Modifier.width(minOf(ps.pt(330), ps.windowSize.width * 0.36f))
        else
            Modifier.fillMaxWidth())
            .heightIn(max = if (isPad) (ps.windowSize.height - topInset) * 0.85f else ps.pt(360))
            .padding(if (isPad) ps.pt(14) else 0.dp)
        // Auf dem Telefon ist die feste Bogenhoehe zu knapp fuer Kopf,
        // Statistik, beide Schichtregler, Farbmodus und die
        // Rollen-Chips zusammen - ein Scrollcontainer haelt den unteren
        // Regler erreichbar, statt ihn abzuschneiden.
        // Auch die Seitenkarte scrollt: im kurzen Fenster (Telefon quer)
        // passt sie sonst nicht zwischen Werkzeugleiste und Rand.
        Box(panelModifier.verticalScroll(rememberScrollState())) {
            PreviewPanelKarte(
                snapshot, range, onRangeChange, view, onViewChange,
                hiddenRoles, onHiddenRolesChange, hiddenExtruders, onHiddenExtrudersChange,
                onEditor, isPad, model,
            )
        }
        PSMarke(name = "vorschau.panel")
        PSMarke(name = if (isPad) "vorschau.seitenkarte" else "vorschau.bottomsheet")
    }
}

@Composable
private fun PreviewPanelKarte(
    snapshot: PsmCore.PreviewSnapshot,
    range: PreviewRange,
    onRangeChange: (PreviewRange) -> Unit,
    view: PsmViewport.PreviewView,
    onViewChange: (PsmViewport.PreviewView) -> Unit,
    hiddenRoles: Set<Int>,
    onHiddenRolesChange: (Set<Int>) -> Unit,
    hiddenExtruders: Set<Int>,
    onHiddenExtrudersChange: (Set<Int>) -> Unit,
    onEditor: () -> Unit,
    isPad: Boolean,
    model: SlicerModel,
) {
    val ps = LocalPsScale.current
    val ecke = if (isPad) ps.pt(8) else ps.pt(14)
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(9)),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .shadow(ps.pt(12), RoundedCornerShape(ecke))
            .clip(RoundedCornerShape(ecke))
            .background(PrusaColors.panel.copy(alpha = 0.97f))
            .border(1.dp, PrusaColors.divider, RoundedCornerShape(ecke))
            .padding(ps.pt(12)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                st("Final G-code", "Finaler G-Code"),
                fontSize = ps.font(15),
                fontWeight = FontWeight.Bold,
                color = PrusaColors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            // `.buttonStyle(.bordered)`: getoente Flaeche, Akzentschrift.
            Row(
                horizontalArrangement = Arrangement.spacedBy(ps.pt(5)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = ps.touch(44))
                    .clip(RoundedCornerShape(ps.pt(8)))
                    .background(PrusaColors.orange.copy(alpha = 0.18f))
                    .clickable(onClick = onEditor)
                    .padding(horizontal = ps.pt(10))
                    .testTag("vorschau.editor"),
            ) {
                SfSymbol("cube", Modifier.size(ps.font(12).value.dp), tint = PrusaColors.orange)
                Text(
                    st("Editor", "Editor"),
                    fontSize = ps.font(12),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.orange,
                )
            }
        }

        PreviewStatsRow(range = range, snapshot = snapshot, model = model)
        PreviewLayerBereich(range = range, onRangeChange = onRangeChange)

        PreviewLegendPicker(
            snapshot = snapshot,
            view = view,
            onViewChange = onViewChange,
            hiddenRoles = hiddenRoles,
            onHiddenRolesChange = onHiddenRolesChange,
            hiddenExtruders = hiddenExtruders,
            onHiddenExtrudersChange = onHiddenExtrudersChange,
            model = model,
        )
    }
}

@Composable
private fun PreviewLayerBereich(range: PreviewRange, onRangeChange: (PreviewRange) -> Unit) {
    val ps = LocalPsScale.current
    val obergrenze = maxOf(range.layerCount - 1, 1)
    val farben = SliderDefaults.colors(
        thumbColor = PrusaColors.orange,
        activeTrackColor = PrusaColors.orange,
        inactiveTrackColor = PrusaColors.panelRaised,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = PrusaColors.textMuted,
        disabledActiveTrackColor = PrusaColors.divider,
        disabledInactiveTrackColor = PrusaColors.panelRaised,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                st("Layer range", "Schichtbereich"),
                fontSize = ps.font(11),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${range.lower + 1}–${range.upper + 1}/${range.layerCount}",
                fontSize = ps.font(11),
                fontFamily = FontFamily.Monospace,
                color = PrusaColors.textMuted,
            )
        }
        Slider(
            value = range.lower.toFloat(),
            onValueChange = { onRangeChange(range.withLower(it.roundToInt())) },
            valueRange = 0f..obergrenze.toFloat(),
            steps = maxOf(obergrenze - 1, 0),
            enabled = range.layerCount >= 2,
            colors = farben,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = st("Lower layer", "Untere Schicht") }
                .testTag("vorschau.layer.unten"),
        )
        Slider(
            value = range.upper.toFloat(),
            onValueChange = { onRangeChange(range.withUpper(it.roundToInt())) },
            valueRange = 0f..obergrenze.toFloat(),
            steps = maxOf(obergrenze - 1, 0),
            enabled = range.layerCount >= 2,
            colors = farben,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = st("Upper layer", "Obere Schicht") }
                .testTag("vorschau.layer.oben"),
        )
    }
}

/**
 * Zeit/Filament/Hoehenbereich fuer den aktuell gewaehlten Schichtbereich.
 * Eigenstaendig, damit die schwebende Karte im Simple Mode und das
 * rechte Menueband im Advanced Mode dieselbe Rechnung verwenden.
 */
@Composable
fun PreviewStatsRow(
    range: PreviewRange,
    snapshot: PsmCore.PreviewSnapshot,
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    val core = model.core
    val layers = remember(snapshot, core) {
        val c = core ?: return@remember emptyList<PreviewLayerMetrics>()
        (0 until snapshot.layerCount).mapNotNull { i ->
            runCatching { c.previewLayer(i) }.getOrNull()?.let {
                PreviewLayerMetrics(
                    zLower = it.zLower,
                    zUpper = it.zUpper,
                    timeSeconds = it.timeSeconds,
                    filamentMm = it.filamentUsedMm,
                    filamentGrams = it.filamentUsedG,
                )
            }
        }
    }
    val values = range.stats(layers)
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vorschau.statistik"),
    ) {
        PreviewStat("clock", previewDauer(values.timeSeconds))
        PreviewStat("scribble.variable", String.format(Locale.US, "%.2f m", values.filamentMm / 1000))
        PreviewStat("scalemass", String.format(Locale.US, "%.1f g", values.filamentGrams))
        Spacer(Modifier.weight(1f))
        Text(
            String.format(Locale.US, "%.2f–%.2f mm", values.zLower, values.zUpper),
            fontSize = ps.font(11),
            fontFamily = FontFamily.Monospace,
            color = PrusaColors.textMuted,
        )
    }
}

@Composable
private fun PreviewStat(symbol: String, value: String) {
    val ps = LocalPsScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(11).value.dp), tint = PrusaColors.textPrimary)
        Text(
            value,
            fontSize = ps.font(11),
            fontWeight = FontWeight.Medium,
            color = PrusaColors.textPrimary,
        )
    }
}

private fun previewDauer(seconds: Double): String {
    val total = maxOf(seconds.roundToInt(), 0)
    return String.format(Locale.US, "%d:%02d", total / 3600, (total % 3600) / 60)
}

/**
 * Farbmodus-Umschalter (Merkmale/Extruder) plus die dazugehoerigen
 * Ein-/Ausblend-Chips - von Simple- und Advanced-Ansicht gemeinsam
 * genutzt.
 */
@Composable
fun PreviewLegendPicker(
    snapshot: PsmCore.PreviewSnapshot,
    view: PsmViewport.PreviewView,
    onViewChange: (PsmViewport.PreviewView) -> Unit,
    hiddenRoles: Set<Int>,
    onHiddenRolesChange: (Set<Int>) -> Unit,
    hiddenExtruders: Set<Int>,
    onHiddenExtrudersChange: (Set<Int>) -> Unit,
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    val core = model.core
    val roles = remember(snapshot, core) {
        val c = core ?: return@remember emptyList<PsmCore.PreviewRole>()
        (0 until snapshot.roleCount).mapNotNull { runCatching { c.previewRole(it) }.getOrNull() }
    }
    val extruders = remember(snapshot, core) {
        val c = core ?: return@remember emptyList<PsmCore.PreviewExtruder>()
        (0 until snapshot.extruderCount).mapNotNull { runCatching { c.previewExtruder(it) }.getOrNull() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(7)),
        horizontalAlignment = Alignment.Start,
    ) {
        // `Picker(...).pickerStyle(.segmented)`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(7)))
                .background(PrusaColors.panelRaised)
                .padding(ps.pt(2))
                .testTag("vorschau.farbmodus"),
        ) {
            PreviewSegment(
                st("Features", "Merkmale"),
                aktiv = view == PsmViewport.PreviewView.FEATURE,
                modifier = Modifier.weight(1f),
            ) { onViewChange(PsmViewport.PreviewView.FEATURE) }
            PreviewSegment(
                st("Extruders", "Extruder"),
                aktiv = view == PsmViewport.PreviewView.EXTRUDER,
                modifier = Modifier.weight(1f),
            ) { onViewChange(PsmViewport.PreviewView.EXTRUDER) }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(7)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            if (view == PsmViewport.PreviewView.FEATURE) {
                roles.forEach { item ->
                    RollenKnopf(item, hiddenRoles, onHiddenRolesChange)
                }
            } else {
                extruders.forEach { item ->
                    ExtruderKnopf(item, hiddenExtruders, onHiddenExtrudersChange)
                }
            }
        }
    }
}

@Composable
private fun PreviewSegment(text: String, aktiv: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = ps.touch(32))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(if (aktiv) PrusaColors.orange else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Text(
            text,
            fontSize = ps.font(12),
            fontWeight = FontWeight.SemiBold,
            color = if (aktiv) PrusaColors.background else PrusaColors.textPrimary,
        )
    }
}

@Composable
private fun RollenKnopf(
    item: PsmCore.PreviewRole,
    hiddenRoles: Set<Int>,
    onHiddenRolesChange: (Set<Int>) -> Unit,
) {
    val ps = LocalPsScale.current
    val hidden = hiddenRoles.contains(item.role)
    val wert = if (hidden) st("Hidden", "Ausgeblendet") else st("Visible", "Sichtbar")
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(5)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = ps.touch(44))
            .clip(CircleShape)
            .background(
                if (hidden) PrusaColors.background.copy(alpha = 0.65f)
                else PrusaColors.orange.copy(alpha = 0.18f),
            )
            .clickable {
                onHiddenRolesChange(if (hidden) hiddenRoles - item.role else hiddenRoles + item.role)
            }
            .padding(horizontal = ps.pt(9))
            .testTag("vorschau.rolle.${item.role}")
            .semantics { stateDescription = wert },
    ) {
        Box(
            Modifier
                .size(ps.pt(10))
                .clip(CircleShape)
                .background(farbeAusRgba(item.colorRgba)),
        )
        Text(
            previewRollenName(item.role),
            fontSize = ps.font(11),
            fontWeight = FontWeight.Medium,
            color = if (hidden) PrusaColors.textMuted else PrusaColors.textPrimary,
        )
    }
}

@Composable
private fun ExtruderKnopf(
    item: PsmCore.PreviewExtruder,
    hiddenExtruders: Set<Int>,
    onHiddenExtrudersChange: (Set<Int>) -> Unit,
) {
    val ps = LocalPsScale.current
    val hidden = hiddenExtruders.contains(item.extruder)
    val wert = if (hidden) st("Hidden", "Ausgeblendet") else st("Visible", "Sichtbar")
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(5)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = ps.touch(44))
            .clip(CircleShape)
            .background(
                if (hidden) PrusaColors.background.copy(alpha = 0.65f)
                else PrusaColors.orange.copy(alpha = 0.18f),
            )
            .clickable {
                onHiddenExtrudersChange(
                    if (hidden) hiddenExtruders - item.extruder else hiddenExtruders + item.extruder,
                )
            }
            .padding(horizontal = ps.pt(10))
            .testTag("vorschau.extruder.${item.extruder}")
            .semantics { stateDescription = wert },
    ) {
        Box(
            Modifier
                .size(ps.pt(10))
                .clip(CircleShape)
                .background(farbeAusRgba(item.colorRgba)),
        )
        Text(
            "E${item.extruder + 1}",
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = if (hidden) PrusaColors.textMuted else PrusaColors.textPrimary,
        )
    }
}

/** 0xRRGGBBAA aus dem Kern; 0 heisst "keine Farbe". */
private fun farbeAusRgba(rgba: Long): Color {
    if (rgba == 0L) return Color.Transparent
    return Color(
        red = ((rgba shr 24) and 0xff).toInt() / 255f,
        green = ((rgba shr 16) and 0xff).toInt() / 255f,
        blue = ((rgba shr 8) and 0xff).toInt() / 255f,
        alpha = (rgba and 0xff).toInt() / 255f,
    )
}

/**
 * Die Namen der Merkmalsrollen in der Legende - Gegenstueck zu
 * `roleName(_:)` in `FinalPreviewPanel.swift`.
 *
 * Bewusst nicht `PreviewRoles.name()` aus dem gemeinsamen Modul: die
 * Tabelle dort fuehrt andere Namen ("External perimeter" statt
 * "External", "Skirt" statt "Skirt/Brim"), und iOS benutzt sie gar
 * nicht. Solange die Referenz ihre eigenen Namen hat, steht hier
 * dieselbe Liste - sonst zeigt dieselbe Legende auf zwei Geraeten zwei
 * verschiedene Woerter.
 */
private fun previewRollenName(role: Int): String = when (role) {
    1 -> st("Perimeter", "Kontur")
    2 -> st("External", "Außenkontur")
    3 -> st("Overhang", "Überhang")
    4 -> st("Infill", "Füllung")
    5 -> st("Solid infill", "Volle Füllung")
    6 -> st("Top surface", "Deckfläche")
    7 -> st("Ironing", "Glätten")
    8 -> st("Bridge", "Brücke")
    9 -> st("Gap fill", "Lückenfüllung")
    10 -> st("Skirt/Brim", "Schürze/Rand")
    11 -> st("Support", "Stütze")
    12 -> st("Support interface", "Stütz-Kontakt")
    13 -> st("Wipe tower", "Reinigungsturm")
    14 -> st("Custom", "Benutzerdefiniert")
    else -> ""
}
