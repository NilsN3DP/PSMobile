package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import de.psmobile.ui.theme.PrusaColors

/// Nach dem Teilen einer ZIP: wohin mit den entpackten Modellen.
///
/// Die Modelle sind zu diesem Zeitpunkt schon geladen (siehe
/// SlicerModel.loadZip); hier wird nur entschieden, auf welchem
/// Bildschirm man sie zuerst sieht.
@Composable
fun ZipModusDialog(
    anzahl: Int,
    onSimple: () -> Unit,
    onAdvanced: () -> Unit,
    /** false: ein einzelnes Modell per Oeffnen-mit, nicht aus einer ZIP. */
    ausZip: Boolean = true,
) {
    val ps = LocalPsScale.current
    Box {
        SchwebenderDialog(kennung = "dialog.zip", maximaleBreite = ps.pt(420)) {
            Column(
                Modifier.padding(ps.pt(20)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(16)),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    if (ausZip) st("Models from ZIP", "Modelle aus der ZIP") else st("Model opened", "Modell geöffnet"),
                    fontSize = ps.font(18), fontWeight = FontWeight.SemiBold, color = PrusaColors.textPrimary,
                )
                Text(
                    if (ausZip) st(
                        "$anzahl model file(s) were unpacked and added to the bed. Which mode?",
                        "$anzahl Modelldatei(en) wurden entpackt und aufs Bett gelegt. In welchem Modus weiter?",
                    ) else st(
                        "The model was added to the bed. Which mode?",
                        "Das Modell liegt auf dem Bett. In welchem Modus weiter?",
                    ),
                    fontSize = ps.font(13), color = PrusaColors.textMuted,
                )

                Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10))) {
                    // .borderedProminent: gefuellter Systemknopf in Orange.
                    Button(
                        onClick = onSimple,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ps.touch(48))
                            .testTag("zip.simple"),
                        shape = RoundedCornerShape(ps.pt(8)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrusaColors.orange,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(st("Simple Mode", "Simple Mode"))
                    }

                    // .bordered: getoenter Systemknopf.
                    Button(
                        onClick = onAdvanced,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ps.touch(48))
                            .testTag("zip.advanced"),
                        shape = RoundedCornerShape(ps.pt(8)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrusaColors.orange.copy(alpha = 0.15f),
                            contentColor = PrusaColors.orange,
                        ),
                    ) {
                        Text(st("Advanced Mode", "Advanced Mode"))
                    }
                }
            }
        }
        PSMarke(name = "zipwahl")
    }
}
