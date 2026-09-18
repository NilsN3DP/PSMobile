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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.FilamentCatalog
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import java.util.Locale

/**
 * Die Extruderbank - Port von `ios/PSMobile/Screens/ExtruderBank.swift`.
 *
 * T1-T8 nebeneinander, weil PrusaSlicer sie so nummeriert. Darunter die
 * Wahl fuer den angetippten Kopf - nicht acht Auswahlen untereinander.
 */
@Composable
fun ExtruderBank(
    model: SlicerModel,
    /** Oeffnet die Materialauswahl fuer diesen Kopf. */
    onMaterial: (Int) -> Unit,
) {
    val ps = LocalPsScale.current
    var gewaehlt by remember { mutableStateOf(0) }
    var zeigeFarbe by remember { mutableStateOf(false) }
    var farbtext by remember { mutableStateOf("") }

    val anzahl: Int = model.extruderCount

    var turmX by remember { mutableStateOf(0f) }
    var turmY by remember { mutableStateOf(0f) }
    var turmDrehung by remember { mutableStateOf(0f) }
    var turmGeladen by remember { mutableStateOf(false) }

    if (anzahl > 1) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                st(
                    "Choose T1–T8 · colour and material per tool",
                    "T1–T8 auswählen · Farbe und Material je Werkzeug",
                ),
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
            )
            ExtruderBankKoepfe(model = model, anzahl = anzahl, gewaehlt = gewaehlt, onGewaehlt = { gewaehlt = it })
            ExtruderBankWahl(
                model = model,
                gewaehlt = gewaehlt,
                onMaterial = onMaterial,
                onFarbe = {
                    farbtext = model.extruderColor(gewaehlt)
                    zeigeFarbe = true
                },
            )
            ExtruderBankReinigungsturm(
                turmX = turmX, onTurmX = { turmX = it },
                turmY = turmY, onTurmY = { turmY = it },
                turmDrehung = turmDrehung, onTurmDrehung = { turmDrehung = it },
                onUebernehmen = { model.setWipeTower(turmX, turmY, turmDrehung) },
            )
        }
        if (zeigeFarbe) {
            Dialog(
                onDismissRequest = { zeigeFarbe = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ScaledOverlay {
                    ExtruderBankFarbblatt(
                        gewaehlt = gewaehlt,
                        farbtext = farbtext,
                        onFarbtextChange = { farbtext = it },
                        onAbbrechen = { zeigeFarbe = false },
                        onUebernehmen = {
                            // Nur was der gemeinsame Katalog als Farbe erkennt.
                            val sauber = FilamentCatalog.normalizeColor(farbtext)
                            if (sauber.isNotEmpty()) model.setExtruderColor(gewaehlt, sauber)
                            zeigeFarbe = false
                        },
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            if (turmGeladen) return@LaunchedEffect
            val turm = model.wipeTower() ?: return@LaunchedEffect
            turmGeladen = true
            turmX = turm.first
            turmY = turm.second
            turmDrehung = turm.third
        }
    }
}

/**
 * Position und Drehung des Reinigungsturms - nur bei mehreren
 * Extrudern ueberhaupt relevant.
 */
@Composable
private fun ExtruderBankReinigungsturm(
    turmX: Float, onTurmX: (Float) -> Unit,
    turmY: Float, onTurmY: (Float) -> Unit,
    turmDrehung: Float, onTurmDrehung: (Float) -> Unit,
    onUebernehmen: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Wipe tower", "Reinigungsturm"),
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
            modifier = Modifier.padding(top = ps.pt(6)),
        )
        ExtruderBankTurmZeile(st("X position", "X-Position"), turmX, { onTurmX(it); onUebernehmen() })
        ExtruderBankTurmZeile(st("Y position", "Y-Position"), turmY, { onTurmY(it); onUebernehmen() })
        ExtruderBankTurmZeile(st("Rotation", "Drehung"), turmDrehung, { onTurmDrehung(it); onUebernehmen() }, einheit = "°")
    }
}

/** Gegenstueck zu SwiftUIs `Stepper`: Beschriftung, Wert, Minus und Plus. */
@Composable
private fun ExtruderBankTurmZeile(
    titel: String,
    wert: Float,
    onWert: (Float) -> Unit,
    einheit: String = "mm",
) {
    val ps = LocalPsScale.current
    val untergrenze = if (einheit == "°") -360f else -1000f
    val obergrenze = if (einheit == "°") 360f else 1000f
    val schritt = if (einheit == "°") 5f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("extruder.turm.$titel"),
    ) {
        Text(
            titel,
            fontSize = ps.font(12),
            color = PrusaColors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            String.format(Locale.US, "%.0f", wert) + " " + einheit,
            fontSize = ps.font(12),
            color = PrusaColors.textMuted,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(ps.pt(8)))
        ExtruderBankStepperKnopf("−", enabled = wert > untergrenze) {
            onWert((wert - schritt).coerceAtLeast(untergrenze))
        }
        Spacer(Modifier.width(ps.pt(2)))
        ExtruderBankStepperKnopf("+", enabled = wert < obergrenze) {
            onWert((wert + schritt).coerceAtMost(obergrenze))
        }
    }
}

@Composable
private fun ExtruderBankStepperKnopf(zeichen: String, enabled: Boolean, onClick: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ps.touch(36))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            zeichen,
            fontSize = ps.font(16),
            color = if (enabled) PrusaColors.textPrimary else PrusaColors.textMuted,
        )
    }
}

/** Die Koepfe, hoechstens acht je Zeile. */
@Composable
private fun ExtruderBankKoepfe(
    model: SlicerModel,
    anzahl: Int,
    gewaehlt: Int,
    onGewaehlt: (Int) -> Unit,
) {
    val ps = LocalPsScale.current
    val spalten = minOf(anzahl, 8)
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6))) {
        (0 until anzahl).chunked(spalten).forEach { zeile ->
            Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(6))) {
                zeile.forEach { index ->
                    val aktiv = index == gewaehlt
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ps.pt(3), Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .height(ps.touch(52))
                            .clip(RoundedCornerShape(ps.pt(8)))
                            .background(if (aktiv) PrusaColors.panelRaised else PrusaColors.panel)
                            .border(
                                if (aktiv) 2.dp else 1.dp,
                                if (aktiv) PrusaColors.orange else PrusaColors.divider,
                                RoundedCornerShape(ps.pt(8)),
                            )
                            .clickable { onGewaehlt(index) }
                            .padding(horizontal = ps.pt(5))
                            .testTag("extruder.kopf.$index"),
                    ) {
                        Text(
                            "T${index + 1}",
                            fontSize = ps.font(11),
                            fontWeight = FontWeight.SemiBold,
                            color = if (aktiv) PrusaColors.orange else PrusaColors.textPrimary,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(ps.pt(12))
                                .clip(RoundedCornerShape(ps.pt(2)))
                                .background(colorFromHex(model.extruderColor(index)) ?: PrusaColors.panelRaised)
                                .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(2))),
                        )
                    }
                }
                repeat(spalten - zeile.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Material und Farbe fuer den angetippten Kopf. */
@Composable
private fun ExtruderBankWahl(
    model: SlicerModel,
    gewaehlt: Int,
    onMaterial: (Int) -> Unit,
    onFarbe: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(52))
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panelRaised)
                .clickable { onMaterial(gewaehlt) }
                .padding(horizontal = ps.pt(10))
                .testTag("extruder.material.$gewaehlt"),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    EasyModeState.profileDisplayLabel(model.extruderFilament(gewaehlt)),
                    fontSize = ps.font(13),
                    color = PrusaColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    st("Choose spool & material", "Spule & Material wählen"),
                    fontSize = ps.font(10),
                    color = PrusaColors.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "›",
                fontSize = ps.font(16),
                color = PrusaColors.orange,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(48))
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panelRaised)
                .clickable(onClick = onFarbe)
                .padding(horizontal = ps.pt(10))
                .testTag("extruder.farbe.$gewaehlt"),
        ) {
            Box(
                Modifier
                    .size(ps.pt(24))
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(colorFromHex(model.extruderColor(gewaehlt)) ?: PrusaColors.panelRaised)
                    .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(3))),
            )
            Text(
                st("Colour", "Farbe"),
                fontSize = ps.font(13),
                color = PrusaColors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                model.extruderColor(gewaehlt),
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
            )
        }
    }
}

/**
 * Farbe waehlen: eine Reihe gaengiger Farben und ein Feld fuer den
 * eigenen Wert. Kein Farbkreis: der Wert landet als Hex im Profil.
 */
@Composable
private fun ExtruderBankFarbblatt(
    gewaehlt: Int,
    farbtext: String,
    onFarbtextChange: (String) -> Unit,
    onAbbrechen: () -> Unit,
    onUebernehmen: () -> Unit,
) {
    val ps = LocalPsScale.current
    val gaengig = listOf(
        "#FF8000", "#E53935", "#FDD835", "#43A047", "#1E88E5",
        "#8E24AA", "#795548", "#000000", "#FFFFFF", "#9E9E9E",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .widthIn(max = ps.pt(560))
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(12)))
            .background(PrusaColors.background)
            .padding(ps.pt(20)),
    ) {
        Text(
            st("Colour", "Farbe") + " · T${gewaehlt + 1}",
            fontSize = ps.font(17),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8))) {
            gaengig.chunked(5).forEach { zeile ->
                Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(8))) {
                    zeile.forEach { hex ->
                        val aktiv = farbtext.uppercase() == hex
                        Box(
                            Modifier
                                .weight(1f)
                                .height(ps.touch(48))
                                .clip(RoundedCornerShape(ps.pt(4)))
                                .background(colorFromHex(hex) ?: PrusaColors.panelRaised)
                                .border(
                                    if (aktiv) 3.dp else 1.dp,
                                    if (aktiv) PrusaColors.orange else PrusaColors.divider,
                                    RoundedCornerShape(ps.pt(4)),
                                )
                                .clickable { onFarbtextChange(hex) }
                                .testTag("farbe.$hex"),
                        )
                    }
                    repeat(5 - zeile.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        BasicTextField(
            value = farbtext,
            onValueChange = onFarbtextChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = ps.font(14), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier
                .fillMaxWidth()
                .height(ps.touch(48))
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panelRaised)
                .padding(horizontal = ps.pt(10))
                .testTag("farbe.eigener"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (farbtext.isEmpty()) {
                        Text(
                            st("Custom value, e.g. #3399FF", "Eigener Wert, z. B. #3399FF"),
                            fontSize = ps.font(14),
                            color = PrusaColors.textMuted,
                        )
                    }
                    inner()
                }
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = onAbbrechen,
                modifier = Modifier.heightIn(min = ps.touch(48)),
            ) {
                Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
            }
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.orange)
                    .clickable(onClick = onUebernehmen)
                    .padding(horizontal = ps.pt(20))
                    .testTag("farbe.uebernehmen"),
            ) {
                Text(st("Apply", "Übernehmen"), color = Color.White)
            }
        }
    }
}
