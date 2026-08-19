package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Ein Bildschirm, der ueber der Platte schwebt statt sie zu ersetzen.
 *
 * Gegenstueck zu `SchwebenderDialog` in
 * `ios/PSMobile/Screens/ProfilWechselDialog.swift`. Die Begruendung von
 * dort gilt hier genauso:
 *
 * > Die Einstellungen schweben ueber der Platte statt sie zu ersetzen:
 * > mit einem Rand ringsherum sieht man, dass es weiter um dieses
 * > Projekt geht.
 *
 * Android hatte an diesen Stellen Vollbildseiten. Der Unterschied ist
 * nicht Geschmack: wer aus den Einstellungen zurueckkam, musste bisher
 * erst wieder herausfinden, wo er war.
 *
 * **Der Inhalt liegt in [ScaledOverlay].** Ein Dialog zeichnet in einem
 * eigenen Fenster und erbt `LocalDensity` nicht - ohne die Umhuellung
 * steht der Inhalt in der Dichte des Geraets statt in der der App. Ein
 * Regeltest wacht darueber (`UeberlagerungsRegelTest`).
 *
 * **Warum die Karte ihre Groesse selbst rechnet und nicht `fillMaxSize`
 * nimmt:** das Dialogfenster ist mit `usePlatformDefaultWidth = false`
 * so hoch wie der ganze Bildschirm, sitzt aber unter der Statusleiste -
 * es ragt also genau um deren Hoehe unten heraus. Gemessen waren es
 * 104 px Rand oben und 8 px unten; die Karte klebte an der Unterkante.
 * Weder `windowInsetsPadding` noch `FLAG_LAYOUT_NO_LIMITS` haben daran
 * etwas geaendert. Die sichtbare Flaeche kennt dagegen die Ansicht der
 * Aktivitaet - sie wird hier vor dem Dialog gemessen, und die Karte
 * haengt daran, oben angeschlagen.
 *
 * @param abbrechbar Ob ein Tipp daneben und die Zurueck-Geste schliessen.
 *        Die Ersteinrichtung beim allerersten Start hat keinen Weg
 *        hinaus - dort bleibt nur der Weg durch.
 * @param kennung Name fuer die Bedienungshilfen und die Oberflaechentests.
 * @param maxBreite Obergrenze der Breite. Auf einem breiten Tablet soll
 *        eine Einstellungsseite nicht ueber die ganzen 2560 px laufen.
 */
@Composable
internal fun SchwebenderDialog(
    kennung: String,
    onClose: () -> Unit,
    maxBreite: Dp = 1100.dp,
    abbrechbar: Boolean = true,
    inhalt: @Composable () -> Unit,
) {
    // Auf schmalen Geraeten kostet ein breiter Rand den Platz, den der
    // Inhalt braucht; auf breiten macht erst er sichtbar, dass etwas
    // darunter liegt. Dieselben Werte wie auf iOS.
    val schmal = SettingsLayout.usesCompactNavigation(
        LocalConfiguration.current.screenWidthDp,
    )
    val rand = if (schmal) 10.dp else 28.dp

    // Vor dem Dialog gemessen: hier ist die Ansicht die der Aktivitaet,
    // und ihre Groesse ist die Flaeche, die die App wirklich hat.
    val ansicht = LocalView.current
    val dichte = LocalDensity.current
    val sichtbareBreite = with(dichte) { ansicht.width.toDp() }
    val sichtbareHoehe = with(dichte) { ansicht.height.toDp() }
    // Die App zeichnet von Kante zu Kante - die Ansicht ist also so hoch
    // wie der Bildschirm, und Status- und Gestenleiste liegen darin. Sie
    // gehoeren abgezogen, sonst reicht die Karte unter beide.
    val leisten = WindowInsets.safeDrawing.asPaddingValues()
    val kartenBreite = maxBreite.coerceAtMost(sichtbareBreite - rand * 2)
    val kartenHoehe = sichtbareHoehe - rand * 2 -
        leisten.calculateTopPadding() - leisten.calculateBottomPadding()

    Dialog(
        onDismissRequest = { if (abbrechbar) onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = abbrechbar,
            dismissOnClickOutside = abbrechbar,
        ),
    ) {
        ScaledOverlay {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    // Tipp daneben schliesst. Bewusst ohne Wellenschlag:
                    // der Hintergrund ist kein Knopf, er ist der Weg
                    // hinaus.
                    .pointerInput(abbrechbar, onClose) {
                        detectTapGestures { if (abbrechbar) onClose() }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = rand)
                        .width(kartenBreite)
                        .height(kartenHoehe)
                        .clip(RoundedCornerShape(Corners.SHEET.dp))
                        .background(PrusaColors.Panel)
                        .border(
                            1.dp,
                            PrusaColors.Divider,
                            RoundedCornerShape(Corners.SHEET.dp),
                        )
                        // Ein Tipp in der Karte darf den Dialog nicht
                        // schliessen - ohne das faengt der Hintergrund
                        // alles ab, was neben einem Bedienelement landet.
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    inhalt()
                }
            }
        }
    }
}
