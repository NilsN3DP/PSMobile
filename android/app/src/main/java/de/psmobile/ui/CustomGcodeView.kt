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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale

/**
 * Was auf welcher Hoehe passiert - Port von
 * `ios/PSMobile/Screens/CustomGcodeView.swift`, Gegenstueck zu
 * PrusaSlicers Marken am Schichtregler.
 *
 * Der wichtigste Fall ist der Farbwechsel: man druckt bis 4 mm, der
 * Drucker haelt an, man wechselt die Rolle von Hand, es geht weiter.
 * Daneben die Pause, der Werkzeugwechsel bei mehreren Extrudern, und
 * beliebiger eigener Code.
 *
 * Die Hoehe steht in Millimetern, nicht in Schichten. PrusaSlicer fuehrt
 * sie so, und eine Schichtnummer waere nach jeder Aenderung der
 * Schichthoehe eine andere Stelle im Modell.
 */
@Composable
fun CustomGcodeView(
    model: SlicerModel = LocalSlicerModel.current,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    var eintraege by remember { mutableStateOf<List<PsmCore.CustomGcode>>(emptyList()) }
    var neueHoehe by remember { mutableStateOf("") }
    var neuerTyp by remember { mutableStateOf(PsmCore.CustomGcodeType.COLOR_CHANGE) }
    var neueFarbe by remember { mutableStateOf("#FF8000") }
    var eigenerCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { eintraege = model.customGcodeList() }

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    PsUiCatalog.tr("Custom G-code"),
                    color = PrusaColors.textPrimary,
                    fontSize = ps.font(19),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = ps.touch(44))
                        .clickable(onClick = onClose)
                        .testTag("gcode.fertig"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Done", "Fertig"), color = PrusaColors.orange)
                }
            }
            Text(
                st("Height in millimetres, as PrusaSlicer counts it — a layer number would move with every change of layer height.",
                    "Höhe in Millimetern, wie PrusaSlicer sie führt — eine Schichtnummer wäre nach jeder Änderung der Schichthöhe eine andere Stelle."),
                color = PrusaColors.textMuted,
                fontSize = ps.font(11),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
                horizontalAlignment = Alignment.Start,
            ) {
                Liste(
                    eintraege = eintraege,
                    onEntfernen = { index ->
                        model.removeCustomGcode(index)
                        eintraege = model.customGcodeList()
                    },
                )
                HorizontalDivider(color = PrusaColors.divider)
                NeuerEintrag(
                    model = model,
                    neueHoehe = neueHoehe,
                    onNeueHoeheChange = { neueHoehe = it },
                    neuerTyp = neuerTyp,
                    onNeuerTypChange = { neuerTyp = it },
                    neueFarbe = neueFarbe,
                    onNeueFarbeChange = { neueFarbe = it },
                    eigenerCode = eigenerCode,
                    onEigenerCodeChange = { eigenerCode = it },
                    onHinzufuegen = {
                        val z = neueHoehe.replace(',', '.').toDoubleOrNull() ?: return@NeuerEintrag
                        model.addCustomGcode(
                            PsmCore.CustomGcode(
                                printZ = z,
                                type = neuerTyp,
                                extruder = 0,
                                colour = if (neuerTyp == PsmCore.CustomGcodeType.COLOR_CHANGE) neueFarbe else "",
                                extra = if (neuerTyp == PsmCore.CustomGcodeType.CUSTOM) eigenerCode else "",
                            ),
                        )
                        eintraege = model.customGcodeList()
                        neueHoehe = ""
                        eigenerCode = ""
                    },
                )
            }
        }
        PSMarke(name = "customgcode")
    }
}

@Composable
private fun Liste(
    eintraege: List<PsmCore.CustomGcode>,
    onEntfernen: (Int) -> Unit,
) {
    val ps = LocalPsScale.current
    if (eintraege.isEmpty()) {
        Text(
            st("Nothing set yet", "Noch nichts eingetragen"),
            modifier = Modifier.padding(vertical = ps.pt(10)),
            color = PrusaColors.textMuted,
            fontSize = ps.font(12),
        )
    } else {
        eintraege.forEachIndexed { index, e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.panelRaised)
                    .padding(horizontal = ps.pt(10)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (e.type == PsmCore.CustomGcodeType.COLOR_CHANGE) {
                    val form = RoundedCornerShape(ps.pt(3))
                    Box(
                        Modifier
                            .size(ps.pt(22), ps.pt(22))
                            .clip(form)
                            .background(colorFromHex(e.colour) ?: PrusaColors.panelRaised)
                            .border(1.dp, PrusaColors.divider, form),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        String.format(Locale.US, "%.2f mm", e.printZ),
                        color = PrusaColors.textPrimary,
                        fontSize = ps.font(13),
                    )
                    Text(
                        name(e.type) + (if (e.extra.isEmpty()) "" else " · " + kurz(e.extra)),
                        color = PrusaColors.textMuted,
                        fontSize = ps.font(10),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(ps.touch(40), ps.touch(40))
                        .clickable { onEntfernen(index) }
                        .testTag("gcode.weg.$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✖", color = PrusaColors.textMuted, fontSize = ps.font(12))
                }
            }
        }
    }
}

@Composable
private fun NeuerEintrag(
    model: SlicerModel,
    neueHoehe: String,
    onNeueHoeheChange: (String) -> Unit,
    neuerTyp: PsmCore.CustomGcodeType,
    onNeuerTypChange: (PsmCore.CustomGcodeType) -> Unit,
    neueFarbe: String,
    onNeueFarbeChange: (String) -> Unit,
    eigenerCode: String,
    onEigenerCodeChange: (String) -> Unit,
    onHinzufuegen: () -> Unit,
) {
    val ps = LocalPsScale.current
    val hoeheGueltig = hoeheGueltig(neueHoehe)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Add", "Hinzufügen").uppercase(),
            color = PrusaColors.textMuted,
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eingabe(
                wert = neueHoehe,
                onWertChange = onNeueHoeheChange,
                platzhalter = "0.0",
                modifier = Modifier
                    .size(ps.pt(110), ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.panelRaised)
                    .padding(horizontal = ps.pt(10)),
                kennung = "gcode.hoehe",
                textStyle = TextStyle(color = PrusaColors.textPrimary, fontSize = ps.font(14)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            Text("mm", color = PrusaColors.textMuted, fontSize = ps.font(11))
            Spacer(Modifier.weight(1f))
        }

        // Vier Arten, nicht fuenf: die Vorlage (TEMPLATE) ist ein
        // Sonderfall der Profile und gehoert nicht in eine Liste, die
        // man je Projekt pflegt.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypKnopf(st("Colour change", "Farbwechsel"), PsmCore.CustomGcodeType.COLOR_CHANGE, neuerTyp, onNeuerTypChange)
            TypKnopf(st("Pause", "Pause"), PsmCore.CustomGcodeType.PAUSE, neuerTyp, onNeuerTypChange)
            if (model.extruderCount > 1) {
                TypKnopf(st("Tool", "Werkzeug"), PsmCore.CustomGcodeType.TOOL_CHANGE, neuerTyp, onNeuerTypChange)
            }
            TypKnopf(st("Code", "Code"), PsmCore.CustomGcodeType.CUSTOM, neuerTyp, onNeuerTypChange)
        }

        if (neuerTyp == PsmCore.CustomGcodeType.COLOR_CHANGE) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val form = RoundedCornerShape(ps.pt(3))
                Box(
                    Modifier
                        .size(ps.pt(26), ps.pt(26))
                        .clip(form)
                        .background(colorFromHex(neueFarbe) ?: PrusaColors.panelRaised)
                        .border(1.dp, PrusaColors.divider, form),
                )
                Eingabe(
                    wert = neueFarbe,
                    onWertChange = onNeueFarbeChange,
                    platzhalter = "#FF8000",
                    modifier = Modifier
                        .weight(1f)
                        .height(ps.touch(48))
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(PrusaColors.panelRaised)
                        .padding(horizontal = ps.pt(10)),
                    kennung = "gcode.farbe",
                    textStyle = TextStyle(color = PrusaColors.textPrimary, fontSize = ps.font(13)),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                )
            }
        }
        if (neuerTyp == PsmCore.CustomGcodeType.CUSTOM) {
            Eingabe(
                wert = eigenerCode,
                onWertChange = onEigenerCodeChange,
                platzhalter = "M117 …",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.panelRaised)
                    .padding(ps.pt(10)),
                kennung = "gcode.eigener",
                textStyle = TextStyle(
                    color = PrusaColors.textPrimary,
                    fontSize = ps.font(13),
                    fontFamily = FontFamily.Monospace,
                ),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = false,
                minLines = 2,
                maxLines = 5,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(ps.touch(50))
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(if (hoeheGueltig) PrusaColors.orange else PrusaColors.panelRaised)
                .clickable(enabled = hoeheGueltig, onClick = onHinzufuegen)
                .testTag("gcode.hinzufuegen"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                st("Add", "Hinzufügen"),
                color = Color.White,
                fontSize = ps.font(14),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Eine Hoehe muss eine Zahl ueber null sein. Null waere die erste
 * Schicht, und dort einen Farbwechsel zu setzen heisst: gar keinen.
 */
private fun hoeheGueltig(neueHoehe: String): Boolean {
    val z = neueHoehe.replace(',', '.').toDoubleOrNull() ?: return false
    return z > 0
}

@Composable
private fun TypKnopf(
    label: String,
    art: PsmCore.CustomGcodeType,
    neuerTyp: PsmCore.CustomGcodeType,
    onNeuerTypChange: (PsmCore.CustomGcodeType) -> Unit,
) {
    val ps = LocalPsScale.current
    val aktiv = neuerTyp == art
    Box(
        Modifier
            .heightIn(min = ps.touch(42))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(if (aktiv) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable { onNeuerTypChange(art) }
            .padding(horizontal = ps.pt(12))
            .testTag("gcode.typ.${art.raw}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (aktiv) PrusaColors.background else PrusaColors.textPrimary,
            fontSize = ps.font(12),
        )
    }
}

/** Ein `TextField` mit Platzhalter im iOS-Stil auf Panelhintergrund. */
@Composable
private fun Eingabe(
    wert: String,
    onWertChange: (String) -> Unit,
    platzhalter: String,
    modifier: Modifier,
    kennung: String,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    BasicTextField(
        value = wert,
        onValueChange = onWertChange,
        modifier = modifier.testTag(kennung),
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        cursorBrush = SolidColor(PrusaColors.orange),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (wert.isEmpty()) {
                    Text(
                        platzhalter,
                        color = PrusaColors.textMuted,
                        fontSize = textStyle.fontSize,
                        fontFamily = textStyle.fontFamily,
                    )
                }
                inner()
            }
        },
    )
}

private fun name(art: PsmCore.CustomGcodeType): String = when (art) {
    PsmCore.CustomGcodeType.COLOR_CHANGE -> st("Colour change", "Farbwechsel")
    PsmCore.CustomGcodeType.PAUSE -> st("Pause", "Pause")
    PsmCore.CustomGcodeType.TOOL_CHANGE -> st("Tool change", "Werkzeugwechsel")
    PsmCore.CustomGcodeType.TEMPLATE -> st("Template", "Vorlage")
    PsmCore.CustomGcodeType.CUSTOM -> st("Custom code", "Eigener Code")
}

/**
 * Mehrzeiliger Code in einer Zeile: die erste Zeile sagt genug, um ihn
 * wiederzuerkennen.
 */
private fun kurz(text: String): String {
    val erste = text.split("\n").firstOrNull() ?: text
    return if (erste.length > 28) erste.take(27) + "…" else erste
}
