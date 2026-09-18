package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.SpecialValueCodec
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Die Werte, die als Zeichenkette nicht zu bedienen sind - Gegenstueck
 * zu `ios/PSMobile/Screens/SpecialValueEditors.swift`.
 *
 * PrusaSlicer haelt intern alles als Text, auch eine Bettform
 * ("0x0,250x0,250x210,0x210") und die Reinigungsmengen einer MMU.
 * Gerechnet wird in `SpecialValueCodec` im gemeinsamen Modul. Hier
 * steht nur, wie es aussieht.
 */
object SpecialValueEditors {

    /** Welche Schluessel einen eigenen Bearbeiter haben. */
    fun hasEditor(key: String): Boolean =
        key == "bed_shape" || key == "wiping_volumes_matrix"
}

/**
 * Die Bettform.
 *
 * Der haeufige Fall ist ein Rechteck - dafuer zwei Zahlen statt einer
 * Punktliste. Wer eine andere Form hat, sieht die Punkte einzeln;
 * erfinden kann die Oberflaeche sie nicht.
 */
@Composable
fun BedShapeEditor(model: SlicerModel, onClose: () -> Unit) {
    val ps = LocalPsScale.current
    var punkte by remember { mutableStateOf<List<SpecialValueCodec.BedPoint>>(emptyList()) }
    var breite by remember { mutableStateOf("") }
    var tiefe by remember { mutableStateOf("") }

    /// Vier Punkte, an den Achsen ausgerichtet - dann reichen zwei Zahlen.
    val istRechteck = punkte.size == 4 &&
        punkte.map { round(it.x * 10) }.toSet().size == 2 &&
        punkte.map { round(it.y * 10) }.toSet().size == 2

    val flaecheQcm: Double? =
        if (punkte.size >= 3) SpecialValueCodec.polygonArea(punkte) / 100 else null

    fun laden() {
        val roh = model.config("bed_shape") ?: ""
        punkte = SpecialValueCodec.parseBedShape(roh) ?: emptyList()
        breite = String.format(
            Locale.US, "%.0f",
            (punkte.maxOfOrNull { it.x } ?: 0.0) - (punkte.minOfOrNull { it.x } ?: 0.0),
        )
        tiefe = String.format(
            Locale.US, "%.0f",
            (punkte.maxOfOrNull { it.y } ?: 0.0) - (punkte.minOfOrNull { it.y } ?: 0.0),
        )
    }

    fun uebernehmen() {
        val b = breite.replace(",", ".").toDoubleOrNull() ?: return
        val t = tiefe.replace(",", ".").toDoubleOrNull() ?: return
        if (b <= 0 || t <= 0) return
        val neu = listOf(
            SpecialValueCodec.BedPoint(0.0, 0.0),
            SpecialValueCodec.BedPoint(b, 0.0),
            SpecialValueCodec.BedPoint(b, t),
            SpecialValueCodec.BedPoint(0.0, t),
        )
        model.setConfig("bed_shape", SpecialValueCodec.encodeBedShape(neu))
        onClose()
    }

    LaunchedEffect(Unit) { laden() }

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
            verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(PsUiCatalog.tr("Bed shape"), fontSize = ps.font(20), color = PrusaColors.textPrimary)

            if (istRechteck) {
                Text(st("Rectangular bed", "Rechteckiges Bett"), fontSize = ps.font(12), color = PrusaColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10))) {
                    Zahl(st("Width", "Breite"), breite, { breite = it }, kennung = "bett.breite")
                    Zahl(st("Depth", "Tiefe"), tiefe, { tiefe = it }, kennung = "bett.tiefe")
                }
            } else {
                // Keine erfundene Vereinfachung: was nicht rechteckig ist,
                // wird als das gezeigt, was es ist.
                Text(
                    st("Custom shape with ${punkte.size} points", "Freie Form mit ${punkte.size} Punkten"),
                    fontSize = ps.font(12),
                    color = PrusaColors.textMuted,
                )
                Column(
                    Modifier
                        .heightIn(max = ps.pt(240))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
                ) {
                    punkte.forEachIndexed { i, punkt ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${i + 1}",
                                fontSize = ps.font(11),
                                color = PrusaColors.textMuted,
                                modifier = Modifier.width(ps.pt(24)),
                            )
                            Text(
                                String.format(Locale.US, "%.1f  ×  %.1f mm", punkt.x, punkt.y),
                                fontSize = ps.font(13),
                                color = PrusaColors.textPrimary,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (flaecheQcm != null) {
                Text(
                    st("Area", "Fläche") + String.format(Locale.US, ": %.0f cm²", flaecheQcm),
                    fontSize = ps.font(12),
                    color = PrusaColors.textMuted,
                    modifier = Modifier.testTag("bett.flaeche"),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
                }
                Spacer(Modifier.weight(1f))
                if (istRechteck) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(ps.pt(3)))
                            .background(PrusaColors.orange)
                            .clickable { uebernehmen() }
                            .height(ps.touch(48))
                            .padding(horizontal = ps.pt(20))
                            .testTag("bett.uebernehmen"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(st("Apply", "Übernehmen"), color = Color.White)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        PSMarke(name = "bett")
    }
}

/** Ein beschriftetes Zahlenfeld - `zahl(_:_:kennung:)` im Original. */
@Composable
private fun Zahl(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    kennung: String,
) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4)), horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = ps.font(11), color = PrusaColors.textMuted)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontSize = ps.font(14), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            modifier = Modifier
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panelRaised)
                .height(ps.touch(44))
                .padding(horizontal = ps.pt(10))
                .testTag(kennung),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
}

/**
 * Die Reinigungsmengen einer MMU - eine Matrix, wie viel Material beim
 * Wechsel von Position i nach j verworfen wird, als Gitter mit Zeilen-
 * und Spaltenkoepfen.
 */
@Composable
fun WipingVolumesEditor(model: SlicerModel, onClose: () -> Unit) {
    val ps = LocalPsScale.current
    var werte by remember { mutableStateOf<List<Double>>(emptyList()) }

    val n = sqrt(werte.size.toDouble()).roundToInt()

    fun laden() {
        val roh = model.config("wiping_volumes_matrix") ?: ""
        werte = SpecialValueCodec.parseFloats(roh) ?: emptyList()
    }

    fun uebernehmen() {
        val text = SpecialValueCodec.encodeFloats(werte)
        model.setConfig("wiping_volumes_matrix", text)
        onClose()
    }

    LaunchedEffect(Unit) { laden() }

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
            Text(PsUiCatalog.tr("Wipe tower"), fontSize = ps.font(20), color = PrusaColors.textPrimary)
            Text(
                st(
                    "Millimetres purged when changing from the row to the column.",
                    "Millimeter, die beim Wechsel von der Zeile zur Spalte verworfen werden.",
                ),
                fontSize = ps.font(12),
                color = PrusaColors.textMuted,
            )

            if (n < 2) {
                Text(
                    st("Only one extruder - nothing to purge.", "Nur ein Extruder - nichts zu verwerfen."),
                    fontSize = ps.font(13),
                    color = PrusaColors.textMuted,
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = ps.pt(320))
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .padding(ps.pt(4)),
                    verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(4)), verticalAlignment = Alignment.CenterVertically) {
                        Text("", modifier = Modifier.width(ps.pt(28)))
                        for (spalte in 0 until n) Kopf("${spalte + 1}")
                    }
                    for (zeile in 0 until n) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(4)), verticalAlignment = Alignment.CenterVertically) {
                            Kopf("${zeile + 1}")
                            for (spalte in 0 until n) {
                                Zelle(
                                    zeile = zeile, spalte = spalte, n = n, werte = werte,
                                    onWert = { index, z ->
                                        werte = werte.toMutableList().also { it[index] = z }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) {
                    Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(PrusaColors.orange)
                        .clickable { uebernehmen() }
                        .height(ps.touch(48))
                        .padding(horizontal = ps.pt(20))
                        .testTag("reinigung.uebernehmen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Apply", "Übernehmen"), color = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        PSMarke(name = "reinigung")
    }
}

@Composable
private fun Kopf(text: String) {
    val ps = LocalPsScale.current
    Box(Modifier.size(ps.pt(56), ps.pt(28)), contentAlignment = Alignment.Center) {
        Text(text, fontSize = ps.font(11), color = PrusaColors.textMuted)
    }
}

/** Die Diagonale bleibt leer: von einer Position auf sich selbst wechselt niemand. */
@Composable
private fun Zelle(
    zeile: Int,
    spalte: Int,
    n: Int,
    werte: List<Double>,
    onWert: (Int, Double) -> Unit,
) {
    val ps = LocalPsScale.current
    val index = zeile * n + spalte
    if (zeile == spalte) {
        Box(Modifier.size(ps.pt(56), ps.touch(40)), contentAlignment = Alignment.Center) {
            Text("—", fontSize = ps.font(12), color = PrusaColors.textMuted)
        }
    } else {
        BasicTextField(
            value = if (index < werte.size) String.format(Locale.US, "%.0f", werte[index]) else "",
            onValueChange = { neu ->
                if (index < werte.size) {
                    neu.replace(",", ".").toDoubleOrNull()?.let { z -> onWert(index, z) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = ps.font(12), color = PrusaColors.textPrimary, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(PrusaColors.orange),
            modifier = Modifier
                .clip(RoundedCornerShape(ps.pt(3)))
                .background(PrusaColors.panelRaised)
                .size(ps.pt(56), ps.touch(40))
                .testTag("reinigung.$zeile.$spalte"),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
            },
        )
    }
}
