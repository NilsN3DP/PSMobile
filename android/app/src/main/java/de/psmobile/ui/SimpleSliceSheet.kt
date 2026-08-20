package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.ui.Corners
import de.psmobile.core.PsmCore
import de.psmobile.shared.net.PrusaLinkRules
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.SliceSummary
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.PrusaColors

/**
 * Was waehrend und nach dem Slicen im Simple Mode zu sehen ist.
 *
 * Bis hierhin war das eine Luecke: der G-Code-Knopf startete den
 * Schnitt, und sichtbar geschah nichts. Die Zahlen lagen hinterher vor,
 * aber nur im Advanced Mode und dort in einer Zeile Text.
 *
 * Die Aufbereitung steht in [SliceSummary] im gemeinsamen Modul - iOS
 * zeigt dieselben Zeilen mit denselben Regeln. Eine Druckzeit, die hier
 * "2 h 14 min" heisst und drueben "134 min", waere derselbe Wert in zwei
 * Sprachen, und der Unterschied faellt niemandem auf, der nur ein Geraet
 * benutzt.
 */
@Composable
internal fun SimpleSliceSheet(
    progress: SlicerService.Progress,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    /** Wie viele G-Code-Dateien der letzte Lauf ergeben hat. */
    dateien: Int = 0,
    onShareAll: () -> Unit = {},
) {
    val laeuft = progress is SlicerService.Progress.Running

    Box(
        Modifier.fillMaxSize()
            .background(PrusaColors.Background.copy(alpha = 0.82f))
            // Waehrend gerechnet wird, schliesst ein Tippen daneben
            // nichts: sonst verschwindet der Fortschritt, und der Schnitt
            // laeuft unsichtbar weiter.
            .clickable(enabled = !laeuft, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp)
                .background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (progress) {
                is SlicerService.Progress.Running -> {
                    Titel(st("Slicing", "Wird geschnitten"))
                    // Die Phase mit anzeigen, nicht nur Prozent: das macht
                    // eine mehrminuetige Wartezeit ertraeglich, weil man
                    // sieht, dass sich etwas bewegt.
                    Text(
                        "${progress.percent} %  ·  ${progress.stage}",
                        color = PrusaColors.TextMuted,
                        fontSize = 13.sp,
                    )
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        color = PrusaColors.Orange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(st("Cancel", "Abbrechen"), color = PrusaColors.TextMuted)
                    }
                }

                is SlicerService.Progress.Done -> {
                    Titel(st("Ready to print", "Fertig zum Drucken"))
                    // Wie lange es gedauert hat - dieselbe Zeile wie auf
                    // iOS. Ohne sie ist "fertig" eine Behauptung ohne
                    // Mass.
                    Text(
                        st(
                            "Sliced in ${SliceSummary.duration(progress.seconds)}",
                            "Geslict in ${SliceSummary.duration(progress.seconds)}",
                        ),
                        color = PrusaColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    progress.stats?.let { Zahlen(it) }
                    if (dateien > 1) {
                        Text(
                            st("$dateien G-code files", "$dateien G-Code-Dateien"),
                            color = PrusaColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Button(
                        onClick = if (dateien > 1) onShareAll else onShare,
                        shape = RoundedCornerShape(Corners.FIELD.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrusaColors.Orange,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = psTouch(50)),
                    ) {
                        // "Exportieren", nicht "Sichern": die Datei
                        // verlaesst die Anwendung. Wortwahl von iOS.
                        Text(
                            if (dateien > 1) st("Export all", "Alle exportieren")
                            else st("Export G-code", "G-Code exportieren")
                        )
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(st("Close", "Schließen"), color = PrusaColors.TextMuted)
                    }
                }

                is SlicerService.Progress.Failed -> {
                    Titel(st("Slicing failed", "Schneiden fehlgeschlagen"))
                    // Die Meldung des Kerns woertlich. Sie nennt meist den
                    // Grund; eine eigene, freundlichere Formulierung wuerde
                    // ihn verstecken.
                    Text(progress.message, color = PrusaColors.Danger, fontSize = 12.sp)
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(st("Close", "Schließen"), color = PrusaColors.TextMuted)
                    }
                }

                SlicerService.Progress.Cancelled -> {
                    Titel(st("Cancelled", "Abgebrochen"))
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(st("Close", "Schließen"), color = PrusaColors.TextMuted)
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun Titel(text: String) {
    Text(text, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Zahlen(stats: PsmCore.SliceStats) {
    val zeilen = SliceSummary.rows(
        seconds = stats.printTimeSeconds,
        grams = stats.filamentGrams,
        millimetres = stats.filamentMm,
        cost = stats.cost,
        objects = stats.objects,
    )
    Column(Modifier.fillMaxWidth()) {
        zeilen.forEach { zeile ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(zeile.label, color = PrusaColors.TextMuted, fontSize = 13.sp)
                Text(zeile.value, color = PrusaColors.TextPrimary, fontSize = 13.sp)
            }
            HorizontalDivider(color = PrusaColors.Divider)
        }
    }
}

/**
 * Warum gerade nicht geschnitten werden kann.
 *
 * Ein ausgegrauter Knopf sagt nur, dass es nicht geht. Er sagt nicht,
 * dass das Material fehlt.
 */
@Composable
internal fun SimpleSliceBlockers(gruende: List<String>, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(PrusaColors.Background.copy(alpha = 0.82f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp)
                .background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Titel(st("Not ready yet", "Noch nicht bereit"))
            gruende.forEach {
                Text("·  $it", color = PrusaColors.TextMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(st("Close", "Schließen"), color = PrusaColors.Orange)
            }
        }
    }
}

private fun st(english: String, german: String) = SimpleModeState.text(english, german)
