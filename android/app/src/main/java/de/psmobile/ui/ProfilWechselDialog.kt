package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors

/// Die gemeinsame Huelle fuer Entscheidungen, die den aktuellen
/// Arbeitskontext nicht verlassen duerfen.
@Composable
fun SchwebenderDialog(
    kennung: String,
    maximaleBreite: Dp,
    inhalt: @Composable () -> Unit,
) {
    val ps = LocalPsScale.current
    val rand = max(ps.pt(12), 12.dp)
    val breite = max(0.dp, ps.windowSize.width - rand * 2)
    val hoehe = max(0.dp, ps.windowSize.height - rand * 2)
    val form = RoundedCornerShape(ps.pt(8))

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Der Schleier faengt Tipper ab, wie Color.black auf iOS.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(rand)
                .widthIn(max = min(maximaleBreite, breite))
                .heightIn(max = hoehe)
                .shadow(20.dp, form)
                .background(PrusaColors.panel, form)
                .border(1.dp, PrusaColors.divider, form)
                .clip(form)
                .testTag(kennung),
        ) {
            inhalt()
        }
    }
}

/// Was mit geänderten Profilwerten passiert, wenn man in den Einfachen
/// Modus wechselt.
///
/// Der Einfache Modus arbeitet auf dem Profil und ändert nur, was dort
/// auch wählbar ist. Wer im Expertenmodus an der Schichthöhe gedreht
/// hat, würde diese Änderung beim Wechsel stillschweigend verlieren —
/// deshalb dasselbe Vorgehen wie am Desktop: die geänderten Werte
/// auflisten, mit Vorher und Jetzt, und die Entscheidung dem Nutzer
/// lassen. Fünf Wege, jeder als eigene Zeile mit einem Satz Erklärung.
@Composable
fun ProfilWechselDialog(
    aenderungen: List<SlicerModel.Profilaenderung>,
    /**
     * Warum gefragt wird. Voreingestellt der Weg in den Simple Mode; ein
     * Profilwechsel nennt seinen eigenen Grund - bis zum 16.09.2026 stand
     * auch dort "Der Einfache Modus arbeitet mit den Werten des Profils".
     */
    grund: String = st(
        "The simple mode works with the values from the profile. These changes are not adjustable there:",
        "Der Einfache Modus arbeitet mit den Werten des Profils. Diese Änderungen lassen sich dort nicht einstellen:",
    ),
    onVerwerfen: () -> Unit,
    onNeuesProfil: (String) -> Unit,
    onUeberschreiben: () -> Unit,
    onInsProjekt: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val ps = LocalPsScale.current
    var zeigeNamensfeld by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    val niedrigeHoehe = ps.windowSize.height < 600.dp

    Box {
        SchwebenderDialog(kennung = "dialog.profilwechsel", maximaleBreite = ps.pt(680)) {
            if (niedrigeHoehe) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DialogInhalt(
                        aenderungen, grund, zeigeNamensfeld, name,
                        onName = { name = it },
                        onZeigeNamensfeld = { zeigeNamensfeld = it },
                        onVerwerfen, onNeuesProfil, onUeberschreiben, onInsProjekt, onAbbrechen,
                    )
                }
            } else {
                DialogInhalt(
                    aenderungen, grund, zeigeNamensfeld, name,
                    onName = { name = it },
                    onZeigeNamensfeld = { zeigeNamensfeld = it },
                    onVerwerfen, onNeuesProfil, onUeberschreiben, onInsProjekt, onAbbrechen,
                )
            }
        }
        PSMarke(name = "profilwechsel")
    }
}

@Composable
private fun DialogInhalt(
    aenderungen: List<SlicerModel.Profilaenderung>,
    grund: String,
    zeigeNamensfeld: Boolean,
    name: String,
    onName: (String) -> Unit,
    onZeigeNamensfeld: (Boolean) -> Unit,
    onVerwerfen: () -> Unit,
    onNeuesProfil: (String) -> Unit,
    onUeberschreiben: () -> Unit,
    onInsProjekt: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier.padding(ps.pt(20)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
        horizontalAlignment = Alignment.Start,
    ) {
        Kopf(grund)
        Tabelle(aenderungen)
        if (zeigeNamensfeld) {
            Namensfeld(
                name = name,
                onName = onName,
                onZurueck = { onZeigeNamensfeld(false) },
                onSichern = { onNeuesProfil(name) },
            )
        } else {
            Wege(
                onNeu = { onName(""); onZeigeNamensfeld(true) },
                onUeberschreiben = onUeberschreiben,
                onInsProjekt = onInsProjekt,
                onVerwerfen = onVerwerfen,
                onAbbrechen = onAbbrechen,
            )
        }
    }
}

@Composable
private fun Kopf(grund: String) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6)), horizontalAlignment = Alignment.Start) {
        Text(
            st("Unsaved profile changes", "Ungespeicherte Profiländerungen"),
            fontSize = ps.font(19), fontWeight = FontWeight.SemiBold, color = PrusaColors.textPrimary,
        )
        Text(grund, fontSize = ps.font(12), color = PrusaColors.textMuted)
    }
}

/// Drei Spalten wie am Desktop: was, vorher, jetzt.
@Composable
private fun Tabelle(aenderungen: List<SlicerModel.Profilaenderung>) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .testTag("profilwechsel.liste"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ps.pt(10), vertical = ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                st("Setting", "Einstellung"),
                Modifier.weight(1f),
                fontSize = ps.font(10), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
            )
            Text(
                st("Before", "Vorher"),
                Modifier.width(ps.pt(90)),
                fontSize = ps.font(10), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
                textAlign = TextAlign.End,
            )
            Text(
                st("Now", "Jetzt"),
                Modifier.width(ps.pt(90)),
                fontSize = ps.font(10), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
                textAlign = TextAlign.End,
            )
        }

        HorizontalDivider(color = PrusaColors.divider)

        Column(
            Modifier
                .heightIn(max = ps.pt(260))
                .verticalScroll(rememberScrollState()),
        ) {
            aenderungen.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ps.pt(10))
                        .heightIn(min = ps.touch(40)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            PsUiCatalog.tr(a.bezeichnung),
                            fontSize = ps.font(12), color = PrusaColors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(bereich(a.art), fontSize = ps.font(9), color = PrusaColors.textMuted)
                    }
                    Text(
                        kurz(a.vorher),
                        Modifier.width(ps.pt(90)),
                        fontSize = ps.font(11), color = PrusaColors.textMuted, textAlign = TextAlign.End,
                    )
                    Text(
                        kurz(a.jetzt),
                        Modifier.width(ps.pt(90)),
                        fontSize = ps.font(11), fontWeight = FontWeight.SemiBold, color = PrusaColors.orange,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun Wege(
    onNeu: () -> Unit,
    onUeberschreiben: () -> Unit,
    onInsProjekt: () -> Unit,
    onVerwerfen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8))) {
        Weg(
            st("Save as new profile", "Als neues Profil sichern"),
            st(
                "The changes stay, under a name of your choosing.",
                "Die Änderungen bleiben erhalten, unter einem Namen deiner Wahl.",
            ),
            "profilwechsel.neu", betont = true, aktion = onNeu,
        )
        Weg(
            st("Overwrite the profile", "Profil überschreiben"),
            st(
                "The selected profile takes over these values permanently.",
                "Das gewählte Profil übernimmt diese Werte dauerhaft.",
            ),
            "profilwechsel.ueberschreiben", aktion = onUeberschreiben,
        )
        Weg(
            st("Keep in this project only", "Nur in diesem Projekt behalten"),
            st(
                "The profile stays untouched; the values travel with the project file.",
                "Das Profil bleibt unberührt, die Werte reisen mit der Projektdatei.",
            ),
            "profilwechsel.projekt", aktion = onInsProjekt,
        )
        Weg(
            st("Discard changes", "Änderungen verwerfen"),
            st("Back to the values of the profile.", "Zurück auf die Werte des Profils."),
            "profilwechsel.verwerfen", gefahr = true, aktion = onVerwerfen,
        )
        Weg(
            st("Cancel", "Abbrechen"),
            st("Stay here.", "Hier bleiben."),
            "profilwechsel.abbrechen", aktion = onAbbrechen,
        )
    }
}

@Composable
private fun Namensfeld(
    name: String,
    onName: (String) -> Unit,
    onZurueck: () -> Unit,
    onSichern: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10)), horizontalAlignment = Alignment.Start) {
        Text(
            st("Name of the new profile", "Name des neuen Profils"),
            fontSize = ps.font(12), color = PrusaColors.textMuted,
        )
        BasicTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profilwechsel.name")
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panelRaised)
                .padding(ps.pt(12)),
            textStyle = TextStyle(fontSize = ps.font(15), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onZurueck, modifier = Modifier.heightIn(min = ps.touch(48))) {
                Text(st("Back", "Zurück"), color = PrusaColors.textMuted)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .height(ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(if (name.isEmpty()) PrusaColors.panelRaised else PrusaColors.orange)
                    .clickable(enabled = name.isNotEmpty(), onClick = onSichern)
                    .testTag("profilwechsel.sichern")
                    .padding(horizontal = ps.pt(20)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st("Save", "Sichern"),
                    fontSize = ps.font(14), fontWeight = FontWeight.SemiBold, color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun Weg(
    titel: String,
    erklaerung: String,
    kennung: String,
    betont: Boolean = false,
    gefahr: Boolean = false,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val form = RoundedCornerShape(ps.pt(4))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(form)
            .background(PrusaColors.panelRaised)
            .border(1.dp, if (betont) PrusaColors.orange else PrusaColors.divider, form)
            .clickable(onClick = aktion)
            .testTag(kennung)
            .heightIn(min = ps.touch(56))
            .padding(horizontal = ps.pt(14), vertical = ps.pt(9)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(2), Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            titel,
            fontSize = ps.font(14),
            fontWeight = if (betont) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                gefahr -> PrusaColors.danger
                betont -> PrusaColors.orange
                else -> PrusaColors.textPrimary
            },
        )
        Text(erklaerung, fontSize = ps.font(10), color = PrusaColors.textMuted)
    }
}

private fun bereich(art: PsmCore.PresetType): String = when (art) {
    PsmCore.PresetType.PRINT -> PsUiCatalog.tr("Print Settings")
    PsmCore.PresetType.FILAMENT -> PsUiCatalog.tr("Filament")
    PsmCore.PresetType.PRINTER -> PsUiCatalog.tr("Printer")
}

/// Lange Werte abschneiden. Ein eigener G-Code steht sonst als
/// Romanabsatz in einer Tabellenzelle.
private fun kurz(text: String): String {
    val eine = text.split("\n").firstOrNull { it.isNotEmpty() } ?: text
    return if (eine.length > 12) eine.take(11) + "…" else eine
}
