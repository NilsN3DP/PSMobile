package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.psTouch

private fun text(english: String, german: String) = SimpleModeState.text(english, german)

/**
 * Drucker waehlen - als Karten, nicht als Klappliste.
 *
 * Gegenstueck zu `DruckerAuswahlView` auf iOS, und wie dort **eine**
 * Ansicht fuer beide Modi. Der Advanced Mode hatte an dieser Stelle ein
 * Aufklappmenue mit rohen Profilnamen; die Begruendung steht drueben im
 * Quelltext:
 *
 * > Ein Menue mit dreissig Zeilen ist auf einem Bildschirm, den man in
 * > der Hand haelt, kein Menue, sondern eine Zumutung.
 *
 * Welche Profile zu einem Modell gehoeren und welche Duesen es dazu
 * gibt, entscheidet [EasyModeState] im gemeinsamen Modul - dieselbe
 * Regel wie drueben.
 *
 * **Nur der Drucker.** Filament und Druckeinstellungen bleiben
 * Suchlisten: dort sind es hunderte Eintraege, und iOS macht es genauso.
 */
@Composable
internal fun DruckerAuswahl(
    printers: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onSetup: () -> Unit,
) {
    val modelle = remember(printers) { EasyModeState.printerModelsWithNozzles(printers) }
    if (modelle.isEmpty()) {
        EmptySimplePanel(
            text("No printer configured", "Noch kein Drucker eingerichtet"),
            text("Set up printer", "Drucker einrichten"),
            onSetup,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        modelle.forEach { modell ->
            modell.variants.forEach { wahl ->
                SimplePrinterCard(
                    model = modell.label,
                    nozzle = wahl.label,
                    selected = wahl.rawPreset == selected,
                    onClick = { onSelect(wahl.rawPreset) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Der Weg zu einem weiteren Drucker, als Kachel unter den
        // anderen: gleiche Breite, gleicher Rahmen. Sie gehoert in die
        // Reihe und ist keine Fussnote - wer hier steht und einen
        // zweiten anlegen will, sucht genau an dieser Stelle.
        Row(
            Modifier
                .fillMaxWidth()
                .background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.FIELD.dp))
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
                .clickable(onClick = onSetup)
                .heightIn(min = psTouch(72))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("+", color = PrusaColors.Orange, fontSize = 28.sp)
            Column {
                Text(
                    text("Add printer", "Drucker hinzufügen"),
                    color = PrusaColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text("Another model or nozzle", "Weiteres Modell oder andere Düse"),
                    color = PrusaColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
