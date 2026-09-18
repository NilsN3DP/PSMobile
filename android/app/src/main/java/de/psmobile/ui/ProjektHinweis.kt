package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.psmobile.SlicerModel
import de.psmobile.ui.theme.PrusaColors

/**
 * Der Projekt-Hinweis: was beim Oeffnen anders kam (Profile), was beim
 * Sichern schiefging, was ein Werkzeug ablehnte. Gegenstueck zu
 * `ProjektHinweis.swift`.
 *
 * Bis zum 16.09.2026 zeigte ihn nur der Simple Mode; im Advanced lief
 * `projectNotice` ins Leere - ein Projekt mit fremdem Drucker sagte dort
 * nichts, ein gescheitertes Sichern auch nicht (Emulator).
 *
 * @param abstandUnten Platz ueber dem unteren Rand - im Simple Mode fuer
 *   Schrittleiste und Modelle-Blatt, im Advanced fuer die Ansichtsleiste.
 */
@Composable
fun ProjektHinweis(text: String, model: SlicerModel, abstandUnten: Dp) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = ps.pt(12))
            .testTag("projekt.hinweis"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .widthIn(max = ps.pt(520))
                    .fillMaxWidth()
                    .background(PrusaColors.panelRaised)
                    .border(1.dp, PrusaColors.orange, RoundedCornerShape(ps.pt(3)))
                    .pointerInput(Unit) {}
                    .padding(horizontal = ps.pt(14), vertical = ps.pt(8)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
                verticalAlignment = Alignment.Top,
            ) {
                Text(text, fontSize = ps.font(12), color = PrusaColors.textPrimary, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .testTag("projekt.hinweis.schliessen")
                        .size(ps.touch(44))
                        .clickable { model.projectNotice = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = ps.font(14), color = PrusaColors.textMuted)
                }
            }
            Spacer(Modifier.height(abstandUnten))
        }
    }
}
