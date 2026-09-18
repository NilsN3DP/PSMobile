package de.psmobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

/**
 * Traeger fuer die Achse des Reglers - Gegenstueck zum verschachtelten
 * `DualHandleSlider.Achse` auf iOS. Aufruf: `DualHandleSlider.Achse.senkrecht`.
 */
object DualHandleSlider {
    enum class Achse { waagerecht, senkrecht }
}

private enum class DualHandleGriff { unten, oben }

/**
 * Ein Regler mit zwei Griffen fuer einen Bereich - waagerecht oder
 * senkrecht, je nach Anlegestelle am Viewport-Rand. Port von
 * `ios/PSMobile/UI/DualHandleSlider.swift`.
 *
 * `onChange` feuert waehrend des Ziehens, nicht erst beim Loslassen -
 * der Viewport soll live mitlaufen, wie am Desktop auch.
 */
@Composable
fun DualHandleSlider(
    untererWert: Int,
    onUntererWertChange: (Int) -> Unit,
    obererWert: Int,
    onObererWertChange: (Int) -> Unit,
    bereich: IntRange,
    achse: DualHandleSlider.Achse,
    onChange: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ps = LocalPsScale.current
    val density = LocalDensity.current
    val waagerecht = achse == DualHandleSlider.Achse.waagerecht

    val griffDurchmesser = ps.pt(22)
    val trackDicke = ps.pt(4)
    val trefferflaeche = ps.touch(44)

    var ziehGriff by remember { mutableStateOf<DualHandleGriff?>(null) }

    BoxWithConstraints(
        modifier
            .then(
                if (waagerecht) Modifier.fillMaxWidth().height(griffDurchmesser)
                else Modifier.fillMaxHeight().width(griffDurchmesser),
            ),
    ) {
        val breitePx = with(density) { maxWidth.toPx() }
        val hoehePx = with(density) { maxHeight.toPx() }
        val griffPx = with(density) { griffDurchmesser.toPx() }
        val trackPx = with(density) { trackDicke.toPx() }
        val trefferPx = with(density) { trefferflaeche.toPx() }
        val halb = griffPx / 2

        val laenge = if (waagerecht) breitePx else hoehePx
        val nutzbar = maxOf(laenge - griffPx, 1f)
        val spanne = maxOf((bereich.last - bereich.first).toDouble(), 1.0)

        fun position(wert: Int): Float {
            val anteil = (wert - bereich.first).toDouble() / spanne
            return (anteil.coerceIn(0.0, 1.0) * nutzbar).toFloat()
        }

        /** Mittelpunkt eines Griffs in Regler-Koordinaten (px). */
        fun punkt(position: Float): Offset =
            if (waagerecht) Offset(halb + position, hoehePx / 2)
            // Senkrecht: Wert 0 (unterste Schicht) gehoert an den UNTEREN
            // Bildrand, wie am Bett - deshalb gespiegelt.
            else Offset(breitePx / 2, halb + (nutzbar - position))

        val unterePosition = position(untererWert)
        val oberePosition = position(obererWert)

        // Track: der volle Bereich schwach, die Auswahl kraeftig.
        Canvas(Modifier.fillMaxSize()) {
            val radius = CornerRadius(trackPx / 2, trackPx / 2)
            if (waagerecht) {
                drawRoundRect(
                    color = PrusaColors.panel.copy(alpha = 0.9f),
                    topLeft = Offset(halb, (size.height - trackPx) / 2),
                    size = Size(maxOf(size.width - griffPx, 0f), trackPx),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = PrusaColors.orange,
                    topLeft = Offset(halb + unterePosition, (size.height - trackPx) / 2),
                    size = Size(maxOf(oberePosition - unterePosition, 0f), trackPx),
                    cornerRadius = radius,
                )
            } else {
                drawRoundRect(
                    color = PrusaColors.panel.copy(alpha = 0.9f),
                    topLeft = Offset((size.width - trackPx) / 2, halb),
                    size = Size(trackPx, maxOf(size.height - griffPx, 0f)),
                    cornerRadius = radius,
                )
                // Dieselbe Spiegelung wie in punkt() - sonst zeigt der
                // kraeftige Balken den falschen Abschnitt an.
                val oben = nutzbar - oberePosition
                val unten = nutzbar - unterePosition
                drawRoundRect(
                    color = PrusaColors.orange,
                    topLeft = Offset((size.width - trackPx) / 2, halb + oben),
                    size = Size(trackPx, maxOf(unten - oben, 0f)),
                    cornerRadius = radius,
                )
            }
        }

        @Composable
        fun griff(griff: DualHandleGriff, mittelpunkt: Offset, tag: String) {
            val aktiv = ziehGriff == griff
            val skalierung by animateFloatAsState(if (aktiv) 1.25f else 1f)
            // Waehrend des Ziehens aendern sich Werte und Griffposition
            // laufend - die Geste darf davon nicht neu gestartet werden,
            // deshalb liest sie immer den juengsten Stand statt Schluessel.
            val startpunkt by rememberUpdatedState(mittelpunkt)
            val anwenden by rememberUpdatedState<(Offset) -> Unit> { lage ->
                val roh = if (waagerecht) lage.x - griffPx / 2
                // Gespiegelt wie punkt(): ein Finger nahe am oberen
                // Rand muss den hohen Wert treffen.
                else nutzbar - (lage.y - griffPx / 2)
                val anteil = (roh / maxOf(nutzbar, 1f)).toDouble().coerceIn(0.0, 1.0)
                val neu = bereich.first + (anteil * spanne).roundToInt()
                when (griff) {
                    DualHandleGriff.unten -> onUntererWertChange(minOf(maxOf(neu, bereich.first), obererWert))
                    DualHandleGriff.oben -> onObererWertChange(maxOf(minOf(neu, bereich.last), untererWert))
                }
                onChange()
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // requiredSize statt size: der Regler ist quer zur Achse nur
                    // 22 pt breit, und size() liess sich davon auf 22 pt stutzen -
                    // die Kugel sass dann 11 pt neben der Spur, und die
                    // Trefferflaeche war schmaler als gedacht (S23 FE, 16.09.2026).
                    // requiredSize legt das Uebermass mittig um den gestutzten
                    // Platz, deshalb rechnet der Versatz quer mit dem gestutzten
                    // Mass. Drueben (SwiftUI) stutzt ein Rahmen nichts.
                    .offset {
                        IntOffset(
                            (mittelpunkt.x - minOf(trefferPx, breitePx) / 2).roundToInt(),
                            (mittelpunkt.y - minOf(trefferPx, hoehePx) / 2).roundToInt(),
                        )
                    }
                    // Die sichtbare Kugel bleibt klein - die Trefferflaeche
                    // drumherum erreicht das 44-dp-Mindestmass.
                    .requiredSize(trefferflaeche)
                    .clip(CircleShape)
                    .testTag(tag)
                    .pointerInput(griff, waagerecht) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            ziehGriff = griff
                            // Lage des Fingers in Regler-Koordinaten, wie
                            // `wert.location` bei SwiftUI.
                            var lage = Offset(
                                startpunkt.x - trefferPx / 2 + down.position.x,
                                startpunkt.y - trefferPx / 2 + down.position.y,
                            )
                            anwenden(lage)
                            drag(down.id) { change ->
                                lage += change.positionChange()
                                change.consume()
                                anwenden(lage)
                            }
                            ziehGriff = null
                        }
                    },
            ) {
                Box(
                    Modifier
                        .size(griffDurchmesser)
                        .scale(skalierung)
                        .shadow(if (aktiv) ps.pt(3) else 0.dp, CircleShape)
                        .clip(CircleShape)
                        .background(PrusaColors.orange)
                        .border(2.dp, PrusaColors.background, CircleShape),
                )
            }
        }

        griff(
            DualHandleGriff.unten,
            punkt(unterePosition),
            if (waagerecht) "vorschau.move.unten" else "vorschau.schicht.unten",
        )
        griff(
            DualHandleGriff.oben,
            punkt(oberePosition),
            if (waagerecht) "vorschau.move.oben" else "vorschau.schicht.oben",
        )
    }
}
