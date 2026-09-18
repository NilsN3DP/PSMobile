package de.psmobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.psmobile.core.PsmViewport
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Der Ansichtswuerfel - Gegenstueck zu `UI/Ansichtswuerfel.swift`.
 *
 * Ein kleiner Wuerfel unten links im Viewport, der sich mit der Szene
 * dreht: seine Flaechen heissen Oben, Vorn, Hinten, Links, Rechts, Unten.
 * Eine Flaeche antippen setzt die feste Blickrichtung, Ziehen dreht die
 * Kamera wie ein Zug im Viewport. Er ersetzt seit dem 14.09.2026 die fuenf
 * Richtungsknoepfe der unteren Leiste (Nils: "wie am PC mit so nem
 * Wuerfel, nicht mit festen Feldern").
 *
 * Die Projektion rechnet mit derselben Kamera wie psm_viewport.cpp:
 * Auge bei (cosP*sinY, -cosP*cosY, sinP), Oben ist +Z. Was der Wuerfel
 * zeigt, ist damit genau das, was die Szene zeigt.
 */

/** Eine Wuerfelflaeche: Normale, Beschriftung, feste Blickrichtung, Kennung. */
private class Flaeche(
    val normale: FloatArray,
    val label: String,
    val ansicht: PsmViewport.View,
    val kennung: String,
    val ecken: List<FloatArray>,
)

private fun flaechen(): List<Flaeche> {
    // Reihenfolge der Ecken: gegen den Uhrzeigersinn von aussen gesehen.
    fun e(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
    return listOf(
        Flaeche(e(0f, 0f, 1f), st("Top", "Oben"), PsmViewport.View.TOP, "wuerfel.oben",
            listOf(e(-1f, -1f, 1f), e(1f, -1f, 1f), e(1f, 1f, 1f), e(-1f, 1f, 1f))),
        Flaeche(e(0f, 0f, -1f), st("Bottom", "Unten"), PsmViewport.View.BOTTOM, "wuerfel.unten",
            listOf(e(-1f, 1f, -1f), e(1f, 1f, -1f), e(1f, -1f, -1f), e(-1f, -1f, -1f))),
        Flaeche(e(0f, -1f, 0f), st("Front", "Vorn"), PsmViewport.View.FRONT, "wuerfel.vorn",
            listOf(e(-1f, -1f, -1f), e(1f, -1f, -1f), e(1f, -1f, 1f), e(-1f, -1f, 1f))),
        Flaeche(e(0f, 1f, 0f), st("Back", "Hinten"), PsmViewport.View.BACK, "wuerfel.hinten",
            listOf(e(1f, 1f, -1f), e(-1f, 1f, -1f), e(-1f, 1f, 1f), e(1f, 1f, 1f))),
        Flaeche(e(-1f, 0f, 0f), st("Left", "Links"), PsmViewport.View.LEFT, "wuerfel.links",
            listOf(e(-1f, 1f, -1f), e(-1f, -1f, -1f), e(-1f, -1f, 1f), e(-1f, 1f, 1f))),
        Flaeche(e(1f, 0f, 0f), st("Right", "Rechts"), PsmViewport.View.RIGHT, "wuerfel.rechts",
            listOf(e(1f, -1f, -1f), e(1f, 1f, -1f), e(1f, 1f, 1f), e(1f, -1f, 1f))),
    )
}

/** Kamerabasis aus yaw/pitch: rechts, oben, zur Kamera hin (alles Weltkoordinaten). */
private class Basis(yaw: Float, pitch: Float) {
    val zurKamera: FloatArray
    val rechts: FloatArray
    val oben: FloatArray

    init {
        val cp = cos(pitch)
        zurKamera = norm(floatArrayOf(cp * sin(yaw), -cp * cos(yaw), sin(pitch)))
        val vor = floatArrayOf(-zurKamera[0], -zurKamera[1], -zurKamera[2])
        rechts = norm(kreuz(vor, floatArrayOf(0f, 0f, 1f)))
        oben = norm(kreuz(rechts, vor))
    }

    /** Bildpunkt (x nach rechts, y nach unten) und Tiefe (zur Kamera hin positiv). */
    fun projiziere(p: FloatArray, mitte: Offset, radius: Float): Triple<Float, Float, Float> =
        Triple(mitte.x + skalar(p, rechts) * radius, mitte.y - skalar(p, oben) * radius, skalar(p, zurKamera))
}

private fun skalar(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun kreuz(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
)
private fun norm(a: FloatArray): FloatArray {
    val l = sqrt(skalar(a, a)).takeIf { it > 1e-6f } ?: 1f
    return floatArrayOf(a[0] / l, a[1] / l, a[2] / l)
}

/** Punkt-in-Viereck fuer die Treffersuche (Halbebenentest, Ecken im Bild). */
private fun trifft(punkte: List<Offset>, p: Offset): Boolean {
    var vorzeichen = 0
    for (i in punkte.indices) {
        val a = punkte[i]; val b = punkte[(i + 1) % punkte.size]
        val k = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val s = if (k > 0f) 1 else if (k < 0f) -1 else 0
        if (s == 0) continue
        if (vorzeichen == 0) vorzeichen = s else if (s != vorzeichen) return false
    }
    return vorzeichen != 0
}

@Composable
fun Ansichtswuerfel(
    yaw: Float,
    pitch: Float,
    onAnsicht: (PsmViewport.View) -> Unit,
    onOrbit: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ps = LocalPsScale.current
    val dichte = LocalDensity.current
    val messer = rememberTextMeasurer()
    val alle = remember { flaechen() }
    val groesse = ps.pt(84)
    val basis = Basis(yaw, pitch)

    // Sichtbare Flaechen mit ihren Bildpunkten - einmal je Bild gerechnet,
    // fuer Zeichnung und Treffersuche gleichermassen.
    val px = with(dichte) { groesse.toPx() }
    val mitte = Offset(px / 2f, px / 2f)
    val radius = px * 0.27f
    val sichtbar = alle.mapNotNull { f ->
        val tiefe = skalar(f.normale, basis.zurKamera)
        if (tiefe <= 0.02f) return@mapNotNull null
        val ecken = f.ecken.map { basis.projiziere(it, mitte, radius) }
        Triple(f, ecken.map { Offset(it.first, it.second) }, tiefe)
    }.sortedBy { it.third }

    Box(
        modifier
            .size(groesse)
            .semantics { contentDescription = st("View cube", "Ansichtswürfel") }
            .testTag("wuerfel")
            .pointerInput(sichtbar) {
                detectTapGestures { p ->
                    sichtbar.lastOrNull { trifft(it.second, p) }?.let { onAnsicht(it.first.ansicht) }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    onOrbit(delta.x, delta.y)
                }
            },
    ) {
        Canvas(Modifier.size(groesse)) {
            for ((f, ecken, tiefe) in sichtbar) {
                val pfad = Path().apply {
                    moveTo(ecken[0].x, ecken[0].y)
                    for (e in ecken.drop(1)) lineTo(e.x, e.y)
                    close()
                }
                // Heller, je frontaler die Flaeche zur Kamera steht.
                val helligkeit = 0.28f + 0.30f * tiefe
                drawPath(pfad, Color(helligkeit, helligkeit, helligkeit + 0.02f, 0.92f))
                drawPath(pfad, PrusaColors.orange.copy(alpha = 0.9f), style = Stroke(width = 1.5f))
                val schwer = ecken.fold(Offset.Zero) { acc, e -> acc + e } / ecken.size.toFloat()
                val text = messer.measure(
                    f.label,
                    TextStyle(color = PrusaColors.textPrimary, fontSize = ps.font(if (tiefe > 0.6f) 9 else 7)),
                )
                if (tiefe > 0.35f) {
                    drawText(text, topLeft = Offset(schwer.x - text.size.width / 2f, schwer.y - text.size.height / 2f))
                }
            }
        }
        // Unsichtbare Treffer je Flaeche - fuer Bedienungshilfen und Tests,
        // wie die Gizmo-Marker: dieselbe Kennung wie drueben.
        for ((f, ecken, _) in sichtbar) {
            val schwer = ecken.fold(Offset.Zero) { acc, e -> acc + e } / ecken.size.toFloat()
            val kante = with(dichte) { 18.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((schwer.x - kante / 2).roundToInt(), (schwer.y - kante / 2).roundToInt()) }
                    .size(18.dp)
                    .testTag(f.kennung)
                    .clickable { onAnsicht(f.ansicht) },
            )
        }
    }
}
