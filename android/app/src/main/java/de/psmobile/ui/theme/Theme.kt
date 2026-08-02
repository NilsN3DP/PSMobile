package de.psmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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

/*
 * Wie stark die Oberflaeche insgesamt verkleinert wird.
 *
 * Die Masse in den Bildschirmen sind fuer ein Tablet mit rund 1000 x 720
 * dp geschrieben. Faellt das Fenster deutlich kleiner aus - Telefon,
 * geteilter Bildschirm, ein Emulator mit gesetzter Groesse -, dann sind
 * dieselben Masse im Verhaeltnis zu gross: von einer Druckerliste blieben
 * anderthalb Zeilen uebrig, von zwei Auswahlkarten eine.
 *
 * Statt in fuenfzehn Bildschirmen mehrere hundert Einzelmasse zu pflegen,
 * wird hier die wirksame Dichte gestaucht. Das trifft alles gleichmaessig:
 * Abstaende, Schrift, Zielflaechen.
 *
 * Die Untergrenze ist bewusst gesetzt - darunter wuerden Zielflaechen
 * physisch zu klein. Wo mehr Platz noetig ist, muss der Bildschirm selbst
 * Inhalt weglassen, wie es der Assistent und der Startbildschirm
 * inzwischen tun.
 */
internal fun uiScaleFor(widthDp: Int, heightDp: Int): Float {
    val byWidth = widthDp / REFERENCE_WIDTH_DP
    val byHeight = heightDp / REFERENCE_HEIGHT_DP
    return minOf(byWidth, byHeight, 1f).coerceAtLeast(MIN_SCALE)
}

internal const val REFERENCE_WIDTH_DP = 1000f
internal const val REFERENCE_HEIGHT_DP = 720f
internal const val MIN_SCALE = 0.7f

/** Wie stark die Schrift dem Kastenmass folgt. */
internal const val FONT_FOLLOW = 0.6f

@Composable
fun PSMobileTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = uiScaleFor(configuration.screenWidthDp, configuration.screenHeightDp)

    val scaled = Density(
        density = base.density * scale,
        // Schrift folgt gedaempft: Text darf nicht so stark schrumpfen
        // wie Kaesten, sonst wird er unleserlich, bevor der Platz
        // wirklich knapp ist.
        fontScale = base.fontScale * (1f - (1f - scale) * FONT_FOLLOW),
    )

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}
