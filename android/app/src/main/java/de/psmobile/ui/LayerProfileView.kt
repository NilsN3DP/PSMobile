package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.LayerProfile
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale

/**
 * Variable Schichthoehen - Port von
 * `ios/PSMobile/Screens/LayerProfileView.swift`.
 *
 * PrusaSlicer laesst am Desktop eine Kurve ueber die Modellhoehe malen.
 * Mit dem Finger ist das nicht zu treffen, deshalb Stuetzstellen als
 * Zahlenpaare: ab welcher Hoehe gilt welche Schichtdicke. Was dazwischen
 * liegt, ergibt sich.
 *
 * Ob ein Profil gueltig ist - mindestens zwei Punkte, steigende Z-Werte,
 * positive Hoehen - entscheidet `LayerProfile` im gemeinsamen Modul.
 * Hier steht die Anzeige und ein Balken, der zeigt, was dabei
 * herauskommt.
 */
@Composable
fun LayerProfileView(
    model: SlicerModel = LocalSlicerModel.current,
    objekt: PsmCore.ObjectInfo,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val ps = LocalPsScale.current
    var zeilen by remember { mutableStateOf<List<LayerProfile.Row>>(emptyList()) }
    /** 0 = fein und glatt, 1 = grob und schnell - dieselbe Skala wie am
     *  Desktop, 0.5 ist dessen Vorschlag. */
    var qualitaet by remember { mutableFloatStateOf(0.5f) }

    val hoehe: Double = objekt.sizeMm.third.toDouble()
    val anwendbar: Boolean = LayerProfile.canApply(zeilen)
    val baender: List<LayerProfile.Segment> = LayerProfile.preview(hoehe, zeilen)

    LaunchedEffect(Unit) {
        val vorhanden = model.layerProfile(objekt.id)
        zeilen = LayerProfile.fromPoints(hoehe, vorhanden)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(12)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                PsUiCatalog.tr("Variable layer height"),
                color = PrusaColors.textPrimary,
                fontSize = ps.font(20),
            )
            Text(
                st("From this height, the given layer thickness applies.",
                    "Ab dieser Höhe gilt die angegebene Schichtdicke."),
                color = PrusaColors.textMuted,
                fontSize = ps.font(12),
            )

            // Die Stellen blaettern, die Knoepfe bleiben stehen: mit zehn
            // Stuetzstellen (Adaptiv) schoben sie sonst Schieber und Knoepfe
            // aus dem Blatt, und Abbrechen war unerreichbar (S23 FE, 16.09.2026).
            Row(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(16)),
                verticalAlignment = Alignment.Top,
            ) {
                Vorschau(hoehe = hoehe, baender = baender)
                Stellen(
                    zeilen = zeilen,
                    hoehe = hoehe,
                    onZeilenChange = { zeilen = it },
                )
            }

            Adaptiv(
                qualitaet = qualitaet,
                onQualitaetChange = { qualitaet = it },
                onBerechnen = {
                    val berechnet = model.layerProfileAdaptive(objekt.id, qualitaet)
                    if (berechnet.isNotEmpty()) {
                        zeilen = LayerProfile.fromPoints(hoehe, berechnet)
                    }
                },
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .heightIn(min = ps.touch(44))
                        .clickable {
                            model.clearLayerProfile(objekt.id)
                            zeilen = LayerProfile.defaults(hoehe)
                        }
                        .testTag("schichten.zuruecksetzen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Reset", "Zurücksetzen"), color = PrusaColors.textMuted)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = ps.touch(44))
                        .clickable(onClick = onClose)
                        .testTag("schichten.abbrechen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
                }
                Box(
                    Modifier
                        .height(ps.touch(48))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(if (anwendbar) PrusaColors.orange else PrusaColors.panelRaised)
                        .clickable(enabled = anwendbar) {
                            model.setLayerProfile(objekt.id, LayerProfile.points(zeilen))
                            onClose()
                        }
                        .padding(horizontal = ps.pt(20))
                        .testTag("schichten.uebernehmen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Apply", "Übernehmen"), color = Color.White)
                }
            }
        }
        PSMarke(name = "schichten")
    }
}

/**
 * Ein Balken ueber die Modellhoehe. Duenne Schichten dunkler, dicke
 * heller - so sieht man auf einen Blick, wo fein gedruckt wird, ohne
 * die Zahlen zu lesen.
 */
@Composable
private fun Vorschau(hoehe: Double, baender: List<LayerProfile.Segment>) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .width(ps.pt(72))
            .clip(RoundedCornerShape(ps.pt(3)))
            .testTag("schichten.vorschau"),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        baender.reversed().forEach { band ->
            val anteil = if (hoehe > 0) (band.toZ - band.fromZ) / hoehe else 0.0
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maxOf(ps.pt(2), ps.pt(260) * anteil.toFloat()))
                    .background(farbe(band.heightMm)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    String.format(Locale.US, "%.2f", band.heightMm),
                    modifier = Modifier.padding(start = ps.pt(4)),
                    color = PrusaColors.background,
                    fontSize = ps.font(9),
                )
            }
        }
        if (baender.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ps.pt(260))
                    .background(PrusaColors.panelRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st("Not valid", "Nicht gültig"),
                    color = PrusaColors.textMuted,
                    fontSize = ps.font(11),
                )
            }
        }
    }
}

/**
 * Duenn ist dunkel, dick ist hell - dieselbe Richtung wie in
 * PrusaSlicers eigener Darstellung.
 */
private fun farbe(hoeheMm: Double): Color {
    val anteil = ((hoeheMm - 0.05) / 0.30).coerceIn(0.0, 1.0)
    return Color.hsv(0.08f * 360f, 0.75f, (0.35 + 0.5 * anteil).toFloat())
}

@Composable
private fun Stellen(
    zeilen: List<LayerProfile.Row>,
    hoehe: Double,
    onZeilenChange: (List<LayerProfile.Row>) -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        zeilen.forEachIndexed { i, zeile ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Feld(zeile.z, einheit = "mm", kennung = "schichten.z.$i") { neu ->
                    onZeilenChange(LayerProfile.updateRow(zeilen, i, neu, zeile.height))
                }
                Feld(zeile.height, einheit = st("Layer", "Schicht"), kennung = "schichten.h.$i") { neu ->
                    onZeilenChange(LayerProfile.updateRow(zeilen, i, zeile.z, neu))
                }
                Box(
                    Modifier
                        .size(ps.touch(40), ps.touch(40))
                        .clickable(enabled = zeilen.size > 2) {
                            onZeilenChange(LayerProfile.removePoint(zeilen, i))
                        }
                        .testTag("schichten.weg.$i"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✖", color = PrusaColors.textMuted, fontSize = ps.font(11))
                }
            }
        }
        Box(
            Modifier
                .heightIn(min = ps.touch(44))
                .clickable { onZeilenChange(LayerProfile.addPoint(zeilen, hoehe)) }
                .testTag("schichten.neu"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "＋ " + st("Point", "Stützstelle"),
                color = PrusaColors.orange,
                fontSize = ps.font(12),
            )
        }
    }
}

@Composable
private fun Feld(
    wert: String,
    einheit: String,
    kennung: String,
    uebernehmen: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .size(ps.pt(104), ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(8)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(3)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = wert,
            onValueChange = uebernehmen,
            modifier = Modifier
                .weight(1f)
                .testTag(kennung),
            textStyle = TextStyle(color = PrusaColors.textPrimary, fontSize = ps.font(13)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(PrusaColors.orange),
        )
        Text(einheit, color = PrusaColors.textMuted, fontSize = ps.font(9))
    }
}

/**
 * Aus der Geometrie berechnen statt von Hand Stuetzstellen zu setzen -
 * fuellt nur die Vorschau, angewendet wird erst mit dem bestehenden
 * "Uebernehmen"-Knopf, wie beim manuellen Profil auch.
 */
@Composable
private fun Adaptiv(
    qualitaet: Float,
    onQualitaetChange: (Float) -> Unit,
    onBerechnen: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Breite nach Inhalt, nicht 56 pt fest: auf dem Telefon brach
            // "Adaptive" zu "Adaptiv/e" um (S23 FE, 16.09.2026).
            Text(
                st("Adaptive", "Adaptiv"),
                modifier = Modifier.widthIn(min = ps.pt(56)),
                color = PrusaColors.textMuted,
                fontSize = ps.font(12),
                maxLines = 1,
            )
            Slider(
                value = qualitaet,
                onValueChange = onQualitaetChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("schichten.adaptiv.qualitaet"),
                colors = SliderDefaults.colors(
                    thumbColor = PrusaColors.orange,
                    activeTrackColor = PrusaColors.orange,
                ),
            )
            Box(
                Modifier
                    .heightIn(min = ps.touch(44))
                    .clickable(onClick = onBerechnen)
                    .testTag("schichten.adaptiv.berechnen"),
                contentAlignment = Alignment.Center,
            ) {
                Text(st("Compute", "Berechnen"), color = PrusaColors.orange)
            }
        }
        Text(
            st("Fine and smooth on the left, coarse and fast on the right.",
                "Links fein und glatt, rechts grob und schnell."),
            color = PrusaColors.textMuted,
            fontSize = ps.font(10),
        )
    }
}
