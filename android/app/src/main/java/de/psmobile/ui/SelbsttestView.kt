package de.psmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.diagnose.Selbsttest
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale
import kotlin.concurrent.thread

/// Der Selbsttest als Bildschirm: ein Knopf, eine Liste, ein Bericht.
///
/// Gedacht für das echte Gerät. Was hier grün ist, ist auf diesem
/// Gerät grün — nicht im Emulator. Am Ende steht eine Markdown-Datei,
/// die sich direkt teilen laesst.
@Composable
fun SelbsttestView(onClose: () -> Unit) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val model = LocalSlicerModel.current
    val schalter = LocalSlicerModel.current.startargumente
    val test = remember { Selbsttest(context, schalter) }

    val schritte by test.schritte.collectAsState()
    val aktuell by test.aktuell.collectAsState()
    val laeuft by test.laeuft.collectAsState()
    val bericht by test.bericht.collectAsState()
    val fehlerZahl = schritte.count { it.ausgang == Selbsttest.Ausgang.FEHLER }

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
            // Kopfzeile
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(56))
                    .padding(horizontal = ps.pt(16)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹  " + st("Back", "Zurück"),
                    Modifier
                        .clickable(onClick = onClose)
                        .testTag("selbsttest.zurueck"),
                    fontSize = ps.font(15), color = PrusaColors.orange,
                )
                Text(st("Self-test", "Selbsttest"), fontSize = ps.font(20), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
                val url = bericht
                if (url != null) {
                    Box(
                        Modifier
                            .height(ps.touch(44))
                            .clip(RoundedCornerShape(ps.pt(3)))
                            .background(PrusaColors.orange)
                            .clickable { teilen(context, model, url, "text/markdown") }
                            .testTag("selbsttest.teilen")
                            .padding(horizontal = ps.pt(14)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(st("Share report", "Bericht teilen"), fontSize = ps.font(13), color = Color.White)
                    }
                }
            }
            HorizontalDivider(color = PrusaColors.divider)

            // Inhalt
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ps.pt(16)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    st(
                        "Runs everything that needs real hardware: loading, slicing, saving, reloading, painting, multi-material, and finally under load. The simulator answers none of these questions honestly.",
                        "Prüft alles, was echtes Gerät braucht: laden, slicen, sichern, wieder laden, bemalen, mehrfarbig, und zum Schluss unter Last. Der Simulator beantwortet keine dieser Fragen ehrlich.",
                    ),
                    fontSize = ps.font(12), color = PrusaColors.textMuted,
                )

                // Startknopf
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ps.touch(52))
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(if (laeuft) PrusaColors.panelRaised else PrusaColors.orange)
                        .clickable {
                            if (laeuft) {
                                test.abbrechen()
                            } else {
                                // Der Durchlauf blockiert - ein eigener
                                // Faden, der auch weiterlaeuft, wenn
                                // dieser Bildschirm zugeht.
                                thread(name = "Selbsttest") { test.durchlauf() }
                            }
                        }
                        .testTag("selbsttest.start"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (laeuft) st("Cancel", "Abbrechen") else st("Run all checks", "Alles prüfen"),
                        fontSize = ps.font(15), fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                }

                val schrittName = aktuell
                if (laeuft && !schrittName.isNullOrEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(ps.pt(18)), color = PrusaColors.orange, strokeWidth = 2.dp)
                        Text(schrittName, fontSize = ps.font(12), color = PrusaColors.textPrimary)
                    }
                }

                schritte.forEach { schritt ->
                    Zeile(schritt)
                }

                if (!laeuft && schritte.isNotEmpty()) {
                    Abschluss(fehlerZahl, bericht != null)
                }
            }
        }
        PSMarke(name = "selbsttest")
    }
}

@Composable
private fun Zeile(schritt: Selbsttest.Schritt) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .testTag("selbsttest.schritt")
            .padding(horizontal = ps.pt(10), vertical = ps.pt(8)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            schritt.ausgang.zeichen,
            Modifier.width(ps.pt(18)),
            fontSize = ps.font(15), fontWeight = FontWeight.Bold, color = farbe(schritt.ausgang),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(schritt.name, fontSize = ps.font(13), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.1f s", schritt.sekunden),
                    fontSize = ps.font(10), color = PrusaColors.textMuted,
                )
            }
            if (schritt.detail.isNotEmpty()) {
                Text(
                    schritt.detail,
                    fontSize = ps.font(11),
                    color = if (schritt.ausgang == Selbsttest.Ausgang.FEHLER) PrusaColors.danger else PrusaColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Abschluss(fehlerZahl: Int, berichtVorhanden: Boolean) {
    val ps = LocalPsScale.current
    Column(
        Modifier.padding(top = ps.pt(6)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (fehlerZahl == 0) st("All checks passed", "Alles bestanden")
            else "$fehlerZahl " + st("failed", "fehlgeschlagen"),
            Modifier.testTag("selbsttest.ergebnis"),
            fontSize = ps.font(15), fontWeight = FontWeight.SemiBold,
            color = if (fehlerZahl == 0) PrusaColors.orange else PrusaColors.danger,
        )
        if (berichtVorhanden) {
            Text(
                st(
                    "The report is in Files → PSMobile → Selbsttest.",
                    "Der Bericht liegt in Dateien → PSMobile → Selbsttest.",
                ),
                fontSize = ps.font(11), color = PrusaColors.textMuted,
            )
        }
    }
}

/// iOS kennt `warnung` ("!"), Android `UEBERSPRUNGEN` ("–") - beide
/// sind der dritte Ausgang und bekommen dieselbe Farbe.
private fun farbe(ausgang: Selbsttest.Ausgang): Color = when (ausgang) {
    Selbsttest.Ausgang.OK -> PrusaColors.orange
    Selbsttest.Ausgang.FEHLER -> PrusaColors.danger
    Selbsttest.Ausgang.UEBERSPRUNGEN -> PrusaColors.textPrimary
    Selbsttest.Ausgang.ANGABE -> PrusaColors.textMuted
}
