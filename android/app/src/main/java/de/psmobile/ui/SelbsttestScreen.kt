package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.ui.Corners
import de.psmobile.diagnose.Selbsttest
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.shared.rules.SimpleModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private fun st(english: String, german: String) = SimpleModeState.text(english, german)

/**
 * Der Selbsttest - Gegenstueck zu `SelbsttestView.swift`.
 *
 * Jeder Schritt steht einzeln mit Ergebnis und Dauer da, waehrend er
 * laeuft. Ein Balken, der nur "Test laeuft" sagt, hilft niemandem, wenn
 * es bei Schritt elf haengt.
 */
@Composable
fun SelbsttestScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val test = remember { Selbsttest(context) }
    val schritte by test.schritte.collectAsState()
    val aktuell by test.aktuell.collectAsState()
    val laeuft by test.laeuft.collectAsState()
    val bericht by test.bericht.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier.fillMaxSize().background(PrusaColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(psTouch(56)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹  " + st("Back", "Zurück"),
                    color = PrusaColors.Orange,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onClose).padding(end = 16.dp),
                )
                Text(
                    st("Self-test", "Selbsttest"),
                    color = PrusaColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(color = PrusaColors.Divider)

            Text(
                st(
                    "Runs loading, slicing, saving, painting and a load test on this device, " +
                        "and writes a report.",
                    "Prüft Laden, Schneiden, Sichern, Bemalen und eine Volllast auf diesem " +
                        "Gerät und schreibt einen Bericht.",
                ),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Der Kern rechnet, das darf nicht auf dem
                        // Zeichenstrang laufen - sonst steht die Anzeige
                        // genau dann still, wenn sie am meisten zu sagen
                        // haette.
                        scope.launch(Dispatchers.Default) { test.durchlauf() }
                    },
                    enabled = !laeuft,
                    shape = RoundedCornerShape(Corners.FIELD.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
                ) {
                    Text(if (laeuft) st("Running…", "Läuft…") else st("Start", "Starten"))
                }
                bericht?.let { datei ->
                    if (!laeuft) {
                        Button(
                            onClick = {
                                runCatching {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", datei,
                                    )
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                            st("Share report", "Bericht teilen"),
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(Corners.FIELD.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Panel),
                        ) {
                            Text(st("Share report", "Bericht teilen"))
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                schritte.forEach { s -> SchrittZeile(s) }
                if (laeuft) {
                    aktuell?.let {
                        Text(
                            "…  $it",
                            color = PrusaColors.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchrittZeile(s: Selbsttest.Schritt) {
    val farbe = when (s.ausgang) {
        Selbsttest.Ausgang.OK -> PrusaColors.TextPrimary
        Selbsttest.Ausgang.FEHLER -> PrusaColors.Orange
        else -> PrusaColors.TextMuted
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(s.ausgang.zeichen, color = farbe, fontSize = 14.sp,
            modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = farbe, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (s.detail.isNotBlank()) {
                Text(s.detail, color = PrusaColors.TextMuted, fontSize = 11.sp)
            }
        }
        Text(String.format(java.util.Locale.US, "%.1f s", s.sekunden),
            color = PrusaColors.TextMuted, fontSize = 11.sp)
    }
}
