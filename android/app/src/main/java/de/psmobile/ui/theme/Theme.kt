package de.psmobile.ui.theme

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.psmobile.shared.ui.WindowScale

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
// Die Zahlen stehen seit E-13 im gemeinsamen Modul, damit iOS dieselben
// verwendet. Hier bleibt nur der Durchgriff - und die Anwendung, denn die
// unterscheidet sich je Plattform: Android staucht die wirksame Dichte,
// iOS rechnet die Masse einzeln durch.
internal fun uiScaleFor(widthDp: Int, heightDp: Int): Float =
    WindowScale.forWindow(widthDp.toFloat(), heightDp.toFloat())

internal const val REFERENCE_WIDTH_DP = WindowScale.REFERENCE_WIDTH
internal const val REFERENCE_HEIGHT_DP = WindowScale.REFERENCE_HEIGHT
internal const val MIN_SCALE = WindowScale.MIN_SCALE

/** Wie stark die Schrift dem Kastenmass folgt. */
internal const val FONT_FOLLOW = WindowScale.FONT_FOLLOW

/*
 * Material3 bringt Schriftgroessen fuer eine Telefon-App mit: eine
 * Dialogueberschrift ist 24 sp, ein Kartentitel 22 sp. Die Bildschirme
 * dieser App arbeiten dagegen durchgehend mit 11 bis 18 sp, weil auf
 * einem Tablet viel gleichzeitig sichtbar bleiben soll.
 *
 * Solange beide Massstaebe nebeneinander liefen, sah jeder Dialog neben
 * dem restlichen Fenster aufgeblasen aus - am deutlichsten die
 * Ueberschrift eines AlertDialog. Die Dichteskalierung aendert daran
 * nichts, sie trifft beide gleich.
 *
 * Deshalb hier eine eigene Staffel in der Hausgroesse. Sie gilt fuer
 * alles, was Material selbst zeichnet: Dialoge, Karten, Knopftexte.
 */
private fun psTypography(): Typography {
    val base = Typography()
    fun TextStyle.at(size: Int, line: Int) =
        copy(fontSize = size.sp, lineHeight = line.sp)
    return base.copy(
        headlineLarge  = base.headlineLarge.at(24, 30),
        headlineMedium = base.headlineMedium.at(20, 26),
        headlineSmall  = base.headlineSmall.at(17, 23),
        titleLarge     = base.titleLarge.at(18, 24),
        titleMedium    = base.titleMedium.at(14, 20),
        titleSmall     = base.titleSmall.at(13, 18),
        bodyLarge      = base.bodyLarge.at(14, 20),
        bodyMedium     = base.bodyMedium.at(13, 18),
        bodySmall      = base.bodySmall.at(12, 16),
        labelLarge     = base.labelLarge.at(14, 18),
        labelMedium    = base.labelMedium.at(12, 16),
        labelSmall     = base.labelSmall.at(11, 14),
    )
}

/** Dieselbe Rechnung wie in [PSMobileTheme] - siehe [ScaledOverlay]. */
@Composable
private fun gestauchteDichte(): Density {
    val configuration = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = uiScaleFor(configuration.screenWidthDp, configuration.screenHeightDp)
    return Density(
        density = base.density * scale,
        fontScale = base.fontScale * (1f - (1f - scale) * FONT_FOLLOW),
    )
}

@Composable
fun PSMobileTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides gestauchteDichte()) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = remember { psTypography() },
            content = content,
        )
    }
}

/**
 * Fuer Inhalte in einem eigenen Fenster - ModalBottomSheet, Dialog,
 * Popup. Die tragen [PSMobileTheme]s gestauchte Dichte nicht automatisch
 * weiter, weil sie ausserhalb des normalen Kompositionspfads haengen:
 * ohne dies zeichnet Material seine Telefon-Vorgabegroessen, und das
 * betroffene Blatt wirkt neben dem Rest der App aufgeblasen. Um jede
 * Fundstelle einzeln dieselbe Rechnung nachzutragen, statt sie zu
 * kopieren, einmal hier.
 */
@Composable
fun ScaledOverlay(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides gestauchteDichte(), content = content)
}

/**
 * Ein Mass fuer eine Zielflaeche - Gegenstueck zu `ps.touch()` auf iOS.
 *
 * [PSMobileTheme] staucht die Dichte global. Fuer Abstaende und Kaesten
 * ist das richtig, fuer Zielflaechen nicht: bei MIN_SCALE 0.7 wird aus
 * einem 48-dp-Knopf einer mit 33,6 dp, und der ist mit dem Finger nicht
 * mehr sicher zu treffen. iOS deckelt deshalb bei 44 pt:
 *
 *     func touch(_ v: CGFloat = 44) -> CGFloat { max(pt(v), 44) }
 *
 * Hier ist es dieselbe Regel, nur andersherum gerechnet: die Stauchung
 * wird fuer diesen einen Wert zurueckgenommen, sobald sie das Mass unter
 * die Untergrenze druecken wuerde. Wo es enger wird, muss der Bildschirm
 * Inhalt weglassen, statt weiter zu schrumpfen.
 */
@Composable
fun psTouch(dp: Int = 44): Dp {
    val configuration = LocalConfiguration.current
    val scale = uiScaleFor(configuration.screenWidthDp, configuration.screenHeightDp)
    if (scale >= 1f) return dp.dp
    // Das Theme multipliziert jeden dp-Wert spaeter mit scale. Damit
    // hinten mindestens MIN_TOUCH_DP herauskommen, muss hier durch
    // scale geteilt werden.
    val gewuenscht = maxOf(dp * scale, MIN_TOUCH_DP)
    return (gewuenscht / scale).dp
}

/** Apples Untergrenze, und dieselbe, die iOS hier verwendet. */
const val MIN_TOUCH_DP = 44f

/**
 * Ersatz fuer `androidx.compose.material3.AlertDialog` mit derselben
 * Signatur - nur der Name reicht an den Aufrufstellen, die Slots muessen
 * sich nicht aendern. Ohne das zeigte jeder Dialog ("Bett umbenennen"
 * und siebzehn weitere) Materials unskalierte Telefon-Vorgabegroessen,
 * waehrend der Rest der App gestaucht ist - aus demselben Grund wie bei
 * [ScaledOverlay].
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val dichte = gestauchteDichte()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { CompositionLocalProvider(LocalDensity provides dichte, content = confirmButton) },
        modifier = modifier,
        dismissButton = dismissButton?.let { { CompositionLocalProvider(LocalDensity provides dichte, content = it) } },
        icon = icon?.let { { CompositionLocalProvider(LocalDensity provides dichte, content = it) } },
        title = title?.let { { CompositionLocalProvider(LocalDensity provides dichte, content = it) } },
        text = text?.let { { CompositionLocalProvider(LocalDensity provides dichte, content = it) } },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
