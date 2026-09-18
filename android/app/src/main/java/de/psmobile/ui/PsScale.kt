package de.psmobile.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.ui.WindowScale
import de.psmobile.ui.theme.psTouch

/**
 * Gegenstueck zu `PSScale` in `ios/PSMobile/UI/PSScale.swift`.
 *
 * iOS rechnet jedes Mass einzeln durch (`ps.pt(16)`), Android staucht
 * die wirksame Dichte global in [de.psmobile.ui.theme.PSMobileTheme].
 * Beides ist dieselbe Regel aus dem gemeinsamen Modul (`WindowScale`).
 * Damit die portierten Bildschirme Zeile fuer Zeile wie ihr Swift-
 * Original aussehen, gibt es die Aufrufe hier trotzdem - `pt` liefert
 * schlicht den dp-Wert, den das Theme dann staucht.
 */
@Immutable
data class PsScale(
    /** Wie stark das Theme staucht - 1 ab der Referenzgroesse. */
    val factor: Float,
    /**
     * Die Fenstergroesse im selben Massystem wie [pt]: Wer auf iOS
     * `min(ps.pt(380), ps.windowSize.width * 0.4)` schreibt, schreibt
     * hier dasselbe.
     */
    val windowSize: DpSize,
) {
    /** Ein Kastenmass: Abstand, Breite, Hoehe, Eckenradius. */
    fun pt(value: Int): Dp = value.dp
    fun pt(value: Float): Dp = value.dp
    fun pt(value: Double): Dp = value.toFloat().dp

    /** Eine Schriftgroesse - das Theme laesst sie schwaecher schrumpfen. */
    fun font(value: Int): TextUnit = value.sp
    fun font(value: Float): TextUnit = value.sp
    fun font(value: Double): TextUnit = value.toFloat().sp

    /**
     * Zielflaeche fuer Finger und Stift - nie kleiner als 44 physische
     * dp, egal was die Stauchung sagt. Siehe [psTouch].
     */
    fun touch(value: Int = 44): Dp {
        if (factor >= 1f) return value.dp
        val gewuenscht = maxOf(value * factor, 44f)
        return (gewuenscht / factor).dp
    }
    fun touch(value: Float): Dp = touch(value.toInt())

    companion object {
        val referenceWidth: Float = WindowScale.REFERENCE_WIDTH
        val referenceHeight: Float = WindowScale.REFERENCE_HEIGHT
        val minScale: Float = WindowScale.MIN_SCALE
        val fontFollow: Float = WindowScale.FONT_FOLLOW

        fun scaleFor(width: Float, height: Float): Float = WindowScale.forWindow(width, height)
        fun fontScaleFor(scale: Float): Float = WindowScale.fontScale(scale)

        /** Ohne Umgebung: die Referenzgroesse, also unveraendert. */
        val Default = PsScale(1f, DpSize(referenceWidth.dp, referenceHeight.dp))
    }
}

val LocalPsScale = compositionLocalOf { PsScale.Default }

/**
 * Legt die Skalierung aus der tatsaechlichen Fenstergroesse fest -
 * Gegenstueck zu `PSScaleRoot`. Muss innerhalb von [PSMobileTheme]
 * stehen, denn die Fenstergroesse wird hier im gestauchten Massystem
 * gemessen (so wie die Bildschirme rechnen).
 */
@Composable
fun PSScaleRoot(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val factor = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        PsScale.scaleFor(configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat())
    }
    BoxWithConstraints(Modifier) {
        val scale = remember(factor, maxWidth, maxHeight) {
            PsScale(factor, DpSize(maxWidth, maxHeight))
        }
        CompositionLocalProvider(LocalPsScale provides scale, content = content)
    }
}
