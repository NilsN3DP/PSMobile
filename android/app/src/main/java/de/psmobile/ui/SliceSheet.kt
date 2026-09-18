package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.SliceSummary
import de.psmobile.ui.theme.PrusaColors
import java.io.File

/**
 * Was waehrend und nach dem Slicen zu sehen ist - Gegenstueck zu
 * `ios/PSMobile/Screens/SliceSheet.swift`.
 *
 * Die Aufbereitung der Zahlen steht in `SliceSummary` im gemeinsamen
 * Modul. Hier steht, wie es aussieht und wie die Datei das Geraet
 * verlaesst - ueber den Teilen-Dialog, nicht ueber einen Dateipfad.
 */
@Composable
fun SliceSheet(
    model: SlicerModel,
    /** Wenn gesetzt, steht neben dem Sichern auch der Weg zum Drucker. */
    onSendToPrinter: ((File) -> Unit)?,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    val laeuft = model.progress is SlicerModel.Progress.Running
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Waehrend gerechnet wird, schliesst ein Tippen daneben
        // nichts: sonst verschwindet der Fortschritt, und der
        // Slice laeuft unsichtbar weiter.
        Box(
            Modifier
                .fillMaxSize()
                .background(PrusaColors.background.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (!laeuft) onClose() },
        )
        Column(
            Modifier
                .padding(ps.pt(16))
                .widthIn(max = ps.pt(420))
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panel)
                .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(4)))
                .padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
            horizontalAlignment = Alignment.Start,
        ) {
            sliceInhalt(model, onSendToPrinter, onClose)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            PSMarke(name = "slice.blatt")
        }
    }
}

/// "3.2 s" unter einer Sekunde grob, sonst auf eine Nachkommastelle -
/// niemand braucht Millisekunden, aber "0 s" bei einem schnellen
/// lokalen Schnitt waere eine falsche Auskunft.
private fun dauerText(sekunden: Double): String =
    // Locale.US wie ueberall: auf einem deutschen Geraet stand sonst "0,21 s"
    // neben "1.2 m" und "3.6 g" (S23 FE, 16.09.2026); iOS formatiert ohne Locale.
    if (sekunden < 1) "%.2f s".format(Locale.US, sekunden) else "%.1f s".format(Locale.US, sekunden)

@Composable
private fun sliceInhalt(
    model: SlicerModel,
    onSendToPrinter: ((File) -> Unit)?,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    when (val fortschritt = model.progress) {
        is SlicerModel.Progress.Running -> {
            sliceTitel(st("Slicing", "Wird gesliced"))
            // Die Phase mit anzeigen, nicht nur Prozent: das macht eine
            // mehrminuetige Wartezeit ertraeglich, weil man sieht, dass
            // sich etwas bewegt, auch wenn die Zahl stehen bleibt.
            Text(
                "${fortschritt.percent} %  ·  ${fortschritt.stage}",
                fontSize = ps.font(13),
                color = PrusaColors.textMuted,
            )
            LinearProgressIndicator(
                progress = { fortschritt.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = PrusaColors.orange,
                trackColor = PrusaColors.panelRaised,
            )
            sliceKnopf(st("Cancel", "Abbrechen"), kennung = "slice.abbrechen", betont = false) {
                model.cancel()
            }
        }

        is SlicerModel.Progress.Done -> {
            sliceTitel(st("Ready to print", "Fertig zum Drucken"))
            Text(
                st("Sliced in ${dauerText(fortschritt.seconds)}", "Gesliced in ${dauerText(fortschritt.seconds)}"),
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
            )
            model.stats?.let { werte -> zahlen(werte) }
            val url = model.gcodeURL
            if (model.gcodeURLs.size > 1) {
                // "Alle Betten schneiden": mehrere Dateien auf einmal -
                // der Teilen-Dialog nimmt eine Sammlung genauso wie eine
                // einzelne Datei.
                Text(
                    st("${model.gcodeURLs.size} G-Code files", "${model.gcodeURLs.size} G-Code-Dateien"),
                    fontSize = ps.font(12),
                    color = PrusaColors.textMuted,
                )
                Box(
                    Modifier
                        .testTag("slice.sichern")
                        .fillMaxWidth()
                        .heightIn(min = ps.touch(50))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(PrusaColors.orange)
                        .clickable { teilen(context, model, model.gcodeURLs) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Export all", "Alle exportieren"), fontSize = ps.font(14), color = Color.White)
                }

                // Alle Betten teilen sich heute noch ein Druckerprofil -
                // PrusaLink bekommt darum jede Datei einzeln vom selben
                // Drucker aus angeboten.
                if (onSendToPrinter != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6))) {
                        model.gcodeURLs.forEach { datei ->
                            Row(
                                Modifier
                                    .testTag("slice.andrucker." + datei.name)
                                    .fillMaxWidth()
                                    .heightIn(min = ps.touch(40))
                                    .clip(RoundedCornerShape(ps.pt(3)))
                                    .background(PrusaColors.panelRaised)
                                    .clickable { onSendToPrinter(datei) }
                                    .padding(horizontal = ps.pt(10)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    datei.name,
                                    fontSize = ps.font(12),
                                    color = PrusaColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    st("Send", "Senden"),
                                    fontSize = ps.font(12),
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrusaColors.orange,
                                )
                            }
                        }
                    }
                }
            } else if (url != null) {
                // Der Teilen-Dialog deckt alles ab, was der Nutzer mit
                // der Datei vorhat: sichern, an den Rechner, in eine
                // Druckerapp geben.
                Box(
                    Modifier
                        .testTag("slice.sichern")
                        .fillMaxWidth()
                        .heightIn(min = ps.touch(50))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(PrusaColors.orange)
                        .clickable { teilen(context, model, url) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Export G-Code", "G-Code exportieren"), fontSize = ps.font(14), color = Color.White)
                }

                if (onSendToPrinter != null) {
                    sliceKnopf(st("Send to printer", "An Drucker senden"), kennung = "slice.andrucker", betont = false) {
                        onSendToPrinter(url)
                    }
                }
            } else {
                sliceHinweis(st("The G-Code could not be written.", "Der G-Code liess sich nicht schreiben."))
            }
            sliceKnopf(st("Close", "Schließen"), kennung = "slice.schliessen", betont = false, aktion = onClose)
        }

        is SlicerModel.Progress.Failed -> {
            // Ein gescheiterter Import ist kein gescheiterter Schnitt - auf
            // dem S23 FE stand "Slicing failed" ueber einer kaputten STL.
            sliceTitel(
                if (fortschritt.laden) st("Loading failed", "Laden fehlgeschlagen")
                else st("Slicing failed", "Slicen fehlgeschlagen"),
            )
            // Die Meldung des Kerns woertlich. Sie nennt meist den
            // Grund - eine eigene, freundlichere Formulierung wuerde ihn
            // verstecken.
            Text(fortschritt.message, fontSize = ps.font(12), color = PrusaColors.danger)
            sliceKnopf(st("Close", "Schließen"), kennung = "slice.schliessen", betont = false, aktion = onClose)
        }

        SlicerModel.Progress.Cancelled -> {
            sliceTitel(st("Cancelled", "Abgebrochen"))
            sliceKnopf(st("Close", "Schließen"), kennung = "slice.schliessen", betont = false, aktion = onClose)
        }

        SlicerModel.Progress.Idle -> Unit
    }
}

@Composable
private fun zahlen(werte: PsmCore.SliceStats) {
    val ps = LocalPsScale.current
    val zeilen = SliceSummary.rows(
        seconds = werte.printTimeSeconds,
        grams = werte.filamentGrams,
        millimetres = werte.filamentMm,
        cost = werte.cost,
        objects = werte.objects,
    )
    Column(Modifier.fillMaxWidth()) {
        zeilen.forEach { zeile ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = ps.pt(7)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(zeile.label, fontSize = ps.font(13), color = PrusaColors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(zeile.value, fontSize = ps.font(13), color = PrusaColors.textPrimary)
            }
            HorizontalDivider(color = PrusaColors.divider)
        }
    }
}

@Composable
private fun sliceTitel(text: String) {
    val ps = LocalPsScale.current
    Text(text, fontSize = ps.font(18), color = PrusaColors.textPrimary)
}

@Composable
private fun sliceHinweis(text: String) {
    val ps = LocalPsScale.current
    Text(text, fontSize = ps.font(12), color = PrusaColors.danger)
}

@Composable
private fun sliceKnopf(
    label: String,
    kennung: String,
    betont: Boolean,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .testTag(kennung)
            .fillMaxWidth()
            .heightIn(min = ps.touch(48))
            .clip(RoundedCornerShape(ps.pt(3)))
            .background(if (betont) PrusaColors.orange else Color.Transparent)
            .clickable(onClick = aktion),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = ps.font(14),
            color = if (betont) Color.White else PrusaColors.textMuted,
        )
    }
}

/**
 * Warum gerade nicht geschnitten werden kann - als Blatt, wenn jemand
 * den Knopf trotzdem trifft. Ein ausgegrauter Knopf sagt nur, dass es
 * nicht geht. Er sagt nicht, dass das Material fehlt.
 */
@Composable
fun SliceBlockerSheet(
    gruende: List<String>,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(PrusaColors.background.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            Modifier
                .padding(ps.pt(16))
                .widthIn(max = ps.pt(380))
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panel)
                .padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(12)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                st("Not ready yet", "Noch nicht bereit"),
                fontSize = ps.font(18),
                color = PrusaColors.textPrimary,
            )
            gruende.forEach { grund ->
                Text("·  $grund", fontSize = ps.font(13), color = PrusaColors.textMuted)
            }
            Box(
                Modifier
                    .testTag("slice.hinderungsgrund.schliessen")
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(48))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(st("Close", "Schließen"), fontSize = ps.font(14), color = PrusaColors.orange)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            PSMarke(name = "slice.hinderungsgrund")
        }
    }
}
