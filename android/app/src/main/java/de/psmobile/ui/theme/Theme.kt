package de.psmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Farbwelt an PrusaSlicer angelehnt.
 *
 * Bewusst kein Material-You-Dynamic-Color: Die App soll aussehen wie der
 * Slicer, den man vom Desktop kennt, und nicht wie das Systemthema des
 * Geraets. Wiedererkennbarkeit schlaegt hier Systemintegration.
 *
 * Ebenfalls bewusst durchgehend dunkel - so wie die meisten den Slicer
 * betreiben, und die 3D-Flaeche fuegt sich ohne Bruch ein.
 */
object PrusaColors {
    val Orange       = Color(0xFFED6B21)   // Prusa-Akzent
    val OrangeDim    = Color(0xFFC2551A)

    val Background   = Color(0xFF232426)   // Fensterhintergrund
    val Panel        = Color(0xFF2B2D30)   // Seitenleisten, Toolbar
    val PanelRaised  = Color(0xFF35373B)   // Eingabefelder, Karten
    val Divider      = Color(0xFF3F4246)

    val TextPrimary  = Color(0xFFE6E6E6)
    val TextMuted    = Color(0xFF9BA0A6)
    val Danger       = Color(0xFFE05252)
    val Ok           = Color(0xFF5BB75B)

    /** Verlauf der 3D-Flaeche, wie im Slicer hinter dem Druckbett. */
    val SceneGradient = Brush.verticalGradient(
        listOf(Color(0xFF3C4045), Color(0xFF212327))
    )

    val BedFill      = Color(0xFF44484D)
    val BedGrid      = Color(0xFF585D63)
    val BedBorder    = Color(0xFF6E747B)
}

private val Scheme = darkColorScheme(
    primary            = PrusaColors.Orange,
    onPrimary          = Color.White,
    secondary          = PrusaColors.OrangeDim,
    background         = PrusaColors.Background,
    onBackground       = PrusaColors.TextPrimary,
    surface            = PrusaColors.Panel,
    onSurface          = PrusaColors.TextPrimary,
    surfaceVariant     = PrusaColors.PanelRaised,
    onSurfaceVariant   = PrusaColors.TextMuted,
    outline            = PrusaColors.Divider,
    error              = PrusaColors.Danger,
)

@Composable
fun PSMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
