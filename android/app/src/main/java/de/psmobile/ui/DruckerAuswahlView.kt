package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.ui.theme.PrusaColors

/// Drucker wählen — als Karten, nicht als Klappliste.
///
/// Welche Profile zu einem Modell gehören und welche Düsen es dazu gibt,
/// entscheidet `EasyModeState` im gemeinsamen Modul.
@Composable
fun DruckerAuswahlView(
    model: SlicerModel,
    onSetup: () -> Unit = {},
    onWahl: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    val modelle = EasyModeState.printerModelsWithNozzles(model.presetNames("printer"))
    val gewaehlt = model.selectedPreset("printer") ?: ""

    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        if (modelle.isEmpty()) {
            Text(
                st("No printer configured", "Noch kein Drucker eingerichtet"),
                fontSize = ps.font(13), color = PrusaColors.textMuted,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(48))
                    .clickable(onClick = onSetup)
                    .testTag("drucker.einrichten"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st("Set up printer", "Drucker einrichten"),
                    fontSize = ps.font(13), color = PrusaColors.orange,
                )
            }
        } else {
            modelle.forEach { modell ->
                modell.variants.forEach { wahl ->
                    Karte(
                        modell = modell.label,
                        duese = wahl.label,
                        gewaehlt = wahl.rawPreset == gewaehlt,
                    ) { onWahl(wahl.rawPreset) }
                }
            }
            PlusKachel(onSetup)
        }
    }
}

/// Der Weg zu einem weiteren Drucker, als Kachel unter den anderen.
@Composable
private fun PlusKachel(onSetup: () -> Unit) {
    val ps = LocalPsScale.current
    val form = RoundedCornerShape(ps.pt(4))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(form)
            .background(PrusaColors.panelRaised)
            .border(1.dp, PrusaColors.divider, form)
            .clickable(onClick = onSetup)
            .testTag("drucker.karte.hinzufuegen")
            .padding(ps.pt(12)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ps.pt(52), ps.pt(60))
                .background(PrusaColors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = ps.font(30), fontWeight = FontWeight.Light, color = PrusaColors.orange)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
            Text(
                st("Add printer", "Drucker hinzufügen"),
                fontSize = ps.font(14), color = PrusaColors.textPrimary,
            )
            Text(
                st("Another model or nozzle", "Weiteres Modell oder andere Düse"),
                fontSize = ps.font(10), color = PrusaColors.textMuted,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Karte(
    modell: String,
    duese: String,
    gewaehlt: Boolean,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val form = RoundedCornerShape(ps.pt(4))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(form)
            .background(PrusaColors.panelRaised)
            .border(
                if (gewaehlt) 2.dp else 1.dp,
                if (gewaehlt) PrusaColors.orange else PrusaColors.divider,
                form,
            )
            .clickable(onClick = aktion)
            .testTag("drucker.karte.$modell.$duese")
            .padding(ps.pt(12)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(5)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(ps.pt(52), ps.pt(60))
                    .background(PrusaColors.panel),
                contentAlignment = Alignment.Center,
            ) {
                Text("▤", fontSize = ps.font(26), color = PrusaColors.textMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
                Text(
                    modell,
                    fontSize = ps.font(14), color = PrusaColors.textPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (gewaehlt) st("SELECTED · OFFLINE", "AUSGEWÄHLT · OFFLINE") else "OFFLINE",
                    fontSize = ps.font(10),
                    color = if (gewaehlt) PrusaColors.orange else PrusaColors.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = PrusaColors.divider)
        Text(
            st("Nozzle", "Düse") + "  " + duese,
            fontSize = ps.font(12), color = PrusaColors.textPrimary,
        )
    }
}
