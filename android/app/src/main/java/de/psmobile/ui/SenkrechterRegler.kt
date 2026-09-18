package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

/**
 * Ein senkrechter Regler, von Hand gebaut - Port von
 * `ios/PSMobile/UI/SenkrechterRegler.swift`.
 *
 * Eine Spur, ein Griff, ein Ziehen, und der Finger trifft, was er sieht.
 * Oben ist der groesste Wert - bei Schichten die einzige Richtung, die
 * niemand erklaeren muss.
 */
@Composable
fun SenkrechterRegler(
    wert: Double,
    onWertChange: (Double) -> Unit,
    maximum: Double,
    beschriftung: String,
    modifier: Modifier = Modifier,
) {
    val ps = LocalPsScale.current
    val griff = ps.pt(22)
    // Die Geste liest immer den juengsten Stand, ohne neu zu starten.
    val aktuellesMaximum by rememberUpdatedState(maximum)
    val aktuellerWertChange by rememberUpdatedState(onWertChange)

    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Fuer Bedienungshilfen und den Test ist ein selbstgezeichneter
        // Regler sonst ein Bild ohne Wert - hier bekommt er ihn zurueck.
        modifier = modifier.semantics {
            contentDescription = beschriftung
            progressBarRangeInfo = ProgressBarRangeInfo(
                wert.toFloat(),
                0f..maxOf(maximum, 0.001).toFloat(),
            )
        },
    ) {
        Text(
            beschriftung,
            fontSize = ps.font(10),
            color = PrusaColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var y = down.position.y
                        fun anwenden() {
                            // Oben ist gross: der Bildschirm zaehlt nach
                            // unten, die Schichten nach oben.
                            val hoehe = size.height.toFloat()
                            val a = 1 - (y / maxOf(hoehe, 1f)).coerceIn(0f, 1f)
                            aktuellerWertChange((a * aktuellesMaximum).roundToInt().toDouble())
                        }
                        anwenden()
                        drag(down.id) { change ->
                            y += change.positionChange().y
                            change.consume()
                            anwenden()
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val hoehe = maxHeight
            val anteil = (if (maximum > 0) wert / maximum else 0.0).coerceIn(0.0, 1.0).toFloat()
            // Spur
            Box(
                Modifier
                    .width(ps.pt(6))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(PrusaColors.panelRaised),
            )
            // Gefuellter Teil - so sieht man den Stand auch ohne auf die
            // Zahl zu schauen.
            Box(
                Modifier
                    .width(ps.pt(6))
                    .height(hoehe * anteil)
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(PrusaColors.orange),
            )
            // Griff
            Box(
                Modifier
                    .offset { IntOffset(0, -((hoehe - griff) * anteil).roundToPx()) }
                    .size(griff)
                    .clip(CircleShape)
                    .background(PrusaColors.orange)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}
