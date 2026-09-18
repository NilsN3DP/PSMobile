package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.FilamentCatalog
import de.psmobile.ui.theme.PrusaColors

/**
 * Material waehlen, wie EasyPrint es zeigt - Port von
 * `ios/PSMobile/Screens/MaterialAuswahlView.swift`.
 *
 * Oben ein Suchfeld ueber Anbieter, Material und Farbe, darunter eine
 * Reihe Typ-Knoepfe und eine Reihe Farbpunkte, und darunter Karten mit
 * Hersteller, Typ und Farbe. Welche Eintraege zu einer Suche passen,
 * entscheidet `FilamentCatalog` im gemeinsamen Modul.
 */
@Composable
fun MaterialAuswahlView(
    model: SlicerModel,
    /** Was gerade gilt. Wird hervorgehoben. */
    gewaehlt: String,
    onWahl: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    var suche by remember { mutableStateOf("") }
    var typ by remember { mutableStateOf("") }
    var farbe by remember { mutableStateOf("") }
    // Aus wegen Unuebersichtlichkeit bei vielen Herstellern: ein
    // Umschalter zeigt die Warnung nur auf Wunsch.
    var zeigeInkompatible by remember { mutableStateOf(false) }

    val alle: List<FilamentCatalog.Entry> = model.filamentCatalog()
    // Einmal pro Aufruf gelesen statt je Karte.
    val kompatibel: Set<String> = model.compatibleFilamentNames()
    val gefiltert: List<FilamentCatalog.Entry> =
        FilamentCatalog.filter(entries = alle, query = suche, type = typ, colorHex = farbe)
    val treffer: List<FilamentCatalog.Entry> =
        if (zeigeInkompatible || kompatibel.isEmpty()) gefiltert
        else gefiltert.filter { kompatibel.contains(it.rawPreset) }
    val inkompatibleAnzahl: Int =
        if (kompatibel.isEmpty()) 0
        else gefiltert.count { !kompatibel.contains(it.rawPreset) }

    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        // MARK: - Suchen und filtern
        MaterialSuchfeld(
            suche = suche,
            onSucheChange = { suche = it },
        )
        MaterialTypknoepfe(alle = alle, typ = typ, onTypChange = { typ = it })
        MaterialFarbpunkte(alle = alle, farbe = farbe, onFarbeChange = { farbe = it })
        if (inkompatibleAnzahl > 0) {
            MaterialInkompatibelUmschalter(
                zeigeInkompatible = zeigeInkompatible,
                inkompatibleAnzahl = inkompatibleAnzahl,
                onToggle = { zeigeInkompatible = !zeigeInkompatible },
            )
        }
        if (treffer.isEmpty()) {
            Text(
                FilamentCatalog.emptyMessage().text,
                fontSize = ps.font(12),
                color = PrusaColors.textMuted,
                modifier = Modifier.padding(vertical = ps.pt(12)),
            )
        } else {
            MaterialKarten(
                treffer = treffer,
                gewaehlt = gewaehlt,
                kompatibel = kompatibel,
                onWahl = onWahl,
            )
        }
    }
}

@Composable
private fun MaterialSuchfeld(suche: String, onSucheChange: (String) -> Unit) {
    val ps = LocalPsScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ps.touch(48))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(12)),
    ) {
        BasicTextField(
            value = suche,
            onValueChange = onSucheChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = ps.font(13), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .weight(1f)
                .testTag("material.suche"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (suche.isEmpty()) {
                        Text(
                            FilamentCatalog.searchHint().text,
                            fontSize = ps.font(13),
                            color = PrusaColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (suche.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ps.touch(36))
                    .clickable { onSucheChange("") }
                    .testTag("material.suche.leeren"),
            ) {
                SfSymbol(
                    "xmark.circle.fill",
                    Modifier.size(ps.font(16).value.dp),
                    tint = PrusaColors.textMuted,
                )
            }
        }
        SfSymbol(
            "magnifyingglass",
            Modifier.size(ps.font(16).value.dp),
            tint = PrusaColors.textMuted,
        )
    }
}

/** Die gaengigen Typen zuerst - wer PLA sucht, soll nicht an ABS und ASA vorbei. */
@Composable
private fun MaterialTypknoepfe(
    alle: List<FilamentCatalog.Entry>,
    typ: String,
    onTypChange: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = ps.pt(2)),
    ) {
        FilamentCatalog.types(alle).forEach { name ->
            val aktiv = typ == name
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(ps.touch(40))
                    .clip(CircleShape)
                    .background(if (aktiv) PrusaColors.orange else Color.Transparent)
                    .border(1.dp, if (aktiv) PrusaColors.orange else PrusaColors.divider, CircleShape)
                    // Ein zweites Tippen hebt die Einschraenkung auf.
                    .clickable { onTypChange(if (typ == name) "" else name) }
                    .padding(horizontal = ps.pt(16))
                    .testTag("material.typ.$name"),
            ) {
                Text(
                    name,
                    fontSize = ps.font(13),
                    color = if (aktiv) PrusaColors.background else PrusaColors.textPrimary,
                )
            }
        }
    }
}

/** Die haeufigsten Farben im Bestand, nicht ein fester Farbkreis. */
@Composable
private fun MaterialFarbpunkte(
    alle: List<FilamentCatalog.Entry>,
    farbe: String,
    onFarbeChange: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = ps.pt(2)),
    ) {
        FilamentCatalog.colors(alle, limit = 12).forEach { hex ->
            val aktiv = farbe == hex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ps.touch(40))
                    .clip(CircleShape)
                    .clickable { onFarbeChange(if (farbe == hex) "" else hex) }
                    .testTag("material.farbe.$hex"),
            ) {
                Box(
                    Modifier
                        .size(ps.pt(26))
                        .clip(CircleShape)
                        .background(colorFromHex(hex) ?: PrusaColors.panelRaised)
                        .border(
                            if (aktiv) 3.dp else 1.dp,
                            if (aktiv) PrusaColors.orange else PrusaColors.divider,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

/**
 * Zeigt oder versteckt Profile, die der Kern als unpassend zum
 * eingerichteten Drucker meldet. Versteckt ist die Vorgabe.
 */
@Composable
private fun MaterialInkompatibelUmschalter(
    zeigeInkompatible: Boolean,
    inkompatibleAnzahl: Int,
    onToggle: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (zeigeInkompatible) PrusaColors.background else PrusaColors.textMuted
    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(5)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(ps.touch(30))
            .clip(CircleShape)
            .background(if (zeigeInkompatible) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable(onClick = onToggle)
            .padding(horizontal = ps.pt(10))
            .testTag("material.inkompatibel.umschalten"),
    ) {
        SfSymbol(
            if (zeigeInkompatible) "eye.fill" else "eye.slash",
            Modifier.size(ps.font(11).value.dp),
            tint = farbe,
        )
        Text(
            if (zeigeInkompatible)
                st("Hide $inkompatibleAnzahl incompatible", "$inkompatibleAnzahl inkompatible ausblenden")
            else
                st("Show $inkompatibleAnzahl incompatible", "$inkompatibleAnzahl inkompatible anzeigen"),
            fontSize = ps.font(11),
            color = farbe,
        )
    }
}

// MARK: - Die Karten

/** Zwei Spalten wie `LazyVGrid` - ohne Lazy, weil der Aufrufer selbst scrollt. */
@Composable
private fun MaterialKarten(
    treffer: List<FilamentCatalog.Entry>,
    gewaehlt: String,
    kompatibel: Set<String>,
    onWahl: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10))) {
        treffer.chunked(2).forEach { zeile ->
            Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10))) {
                zeile.forEach { eintrag ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(ps.pt(6)))
                            .clickable { onWahl(eintrag.rawPreset) }
                            .testTag("material.karte.${eintrag.rawPreset}"),
                    ) {
                        MaterialKarte(eintrag, gewaehlt, kompatibel)
                    }
                }
                if (zeile.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MaterialKarte(
    eintrag: FilamentCatalog.Entry,
    gewaehlt: String,
    kompatibel: Set<String>,
) {
    val ps = LocalPsScale.current
    val aktiv = eintrag.rawPreset == gewaehlt
    val passt = kompatibel.isEmpty() || kompatibel.contains(eintrag.rawPreset)
    Column(
        verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(132))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(if (aktiv) PrusaColors.panelRaised else PrusaColors.panel)
            .alpha(if (passt) 1f else 0.6f)
            .border(
                if (aktiv) 2.dp else 1.dp,
                if (aktiv) PrusaColors.orange else PrusaColors.divider,
                RoundedCornerShape(ps.pt(6)),
            )
            .padding(ps.pt(10)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                eintrag.vendor,
                fontSize = ps.font(14),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!passt) {
                // Nicht versteckt, nur gekennzeichnet.
                SfSymbol(
                    "exclamationmark.triangle.fill",
                    Modifier.size(ps.font(10).value.dp),
                    tint = PrusaColors.orange,
                )
            }
        }
        Text(
            eintrag.type.ifEmpty { " " },
            fontSize = ps.font(12),
            color = PrusaColors.textMuted,
        )
        MaterialSpule(eintrag.colorHex)
        Text(
            eintrag.rawPreset,
            fontSize = ps.font(9),
            color = PrusaColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!passt) {
            Text(
                st("Not compatible with this printer", "Passt nicht zu diesem Drucker"),
                fontSize = ps.font(8),
                color = PrusaColors.orange,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Eine Rolle statt eines Farbklecks: aeusserer Flansch, aufgewickeltes
 * Filament als Ring, Nabe und Kernloch.
 */
@Composable
private fun MaterialSpule(hex: String) {
    val ps = LocalPsScale.current
    val farbe = colorFromHex(hex) ?: PrusaColors.panelRaised
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(ps.pt(44)),
    ) {
        Box(
            Modifier
                .size(ps.pt(44))
                .clip(CircleShape)
                .background(PrusaColors.panelRaised)
                .border(1.dp, PrusaColors.divider, CircleShape),
        )
        Box(
            Modifier
                .size(ps.pt(34))
                .clip(CircleShape)
                .background(farbe),
        )
        Box(
            Modifier
                .size(ps.pt(18))
                .clip(CircleShape)
                .background(PrusaColors.background)
                .border(1.dp, PrusaColors.divider, CircleShape),
        )
        Box(
            Modifier
                .size(ps.pt(7))
                .clip(CircleShape)
                .background(PrusaColors.panel),
        )
    }
}
