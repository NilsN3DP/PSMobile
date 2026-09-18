package de.psmobile.ui

// `AppSettingsView.swift` traegt drueben zwei Dinge: den Bildschirm und
// die Klasse `AppSettingsStore`. Auf Android steht der Store in einer
// eigenen Datei, `AppSettingsStore.kt` - deshalb landen Aenderungen am
// Store dort und nicht hier.
//
// 10.09.2026: `-psm-reset-settings` setzt die App-Einstellungen auf die
// Voreinstellung aus dem gemeinsamen Modul zurueck. Der Bildvergleich
// verglich sonst zwei Vorgeschichten statt zweier Oberflaechen. Die
// Android-Fassung steht im `init`-Block von `AppSettingsStore.kt`.
// 12.09.2026: dazu gehoert jetzt auch `psm.sichtbarkeit`, die Stufe
// Simple/Advanced/Expert der Einstellungsseiten - ebenfalls dort.

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.BuildConfig
import de.psmobile.core.LogExport
import de.psmobile.shared.rules.AppSettings
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

/// Einstellungen der App.
///
/// Bewusst getrennt von den Druckeinstellungen: dort geht es um das
/// Werkstueck, hier um das Programm. Welche Schalter es gibt, in
/// welcher Gruppe sie stehen und warum - das alles kommt aus
/// `AppSettings` im gemeinsamen Modul.
@Composable
fun AppSettingsView(
    einstellungen: AppSettingsStore,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    var zeigeSelbsttest by remember { mutableStateOf(false) }
    var zeigeOpenSource by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Box {
        SchwebenderDialog(kennung = "dialog.appeinstellungen", maximaleBreite = ps.pt(760)) {
            Column {
                Kopfzeile(onClose)
                HorizontalDivider(color = PrusaColors.divider)
                Column(
                    Modifier
                        // Die Liste traegt einen Namen, damit ein Test zur
                        // Diagnose ganz unten blaettern kann - siehe die
                        // gleichlautende Stelle in AppSettingsView.swift.
                        .testTag("appeinstellungen.liste")
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .padding(horizontal = ps.pt(16))
                        .padding(bottom = ps.pt(24)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    AppSettings.groupsInOrder.forEach { gruppe ->
                        Abschnitt(gruppe, einstellungen)
                    }
                    Diagnose(onSelbsttest = { zeigeSelbsttest = true })
                    Ueber(onOpenSource = { zeigeOpenSource = true })
                }
            }
        }
        PSMarke(name = "appeinstellungen")
    }

    if (zeigeOpenSource) {
        Dialog(
            onDismissRequest = { zeigeOpenSource = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize()) {
                    OpenSourceView(onClose = { zeigeOpenSource = false })
                }
            }
        }
    }

    if (zeigeSelbsttest) {
        Dialog(
            onDismissRequest = { zeigeSelbsttest = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize()) {
                    SelbsttestView(onClose = { zeigeSelbsttest = false })
                }
            }
        }
    }
}

@Composable
private fun Kopfzeile(onClose: () -> Unit) {
    val ps = LocalPsScale.current
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
                .testTag("appeinstellungen.zurueck"),
            fontSize = ps.font(15), color = PrusaColors.orange,
        )
        Text(st("App settings", "App-Einstellungen"), fontSize = ps.font(20), color = PrusaColors.textPrimary)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Abschnitt(gruppe: AppSettings.Group, einstellungen: AppSettingsStore) {
    val ps = LocalPsScale.current
    val schalter = AppSettings.group(gruppe)
    val istDarstellung = gruppe == AppSettings.Group.APPEARANCE
    if (schalter.isNotEmpty() || istDarstellung) {
        Text(
            AppSettings.groupTitle(gruppe).uppercase(),
            Modifier.padding(top = ps.pt(20), bottom = ps.pt(6)),
            fontSize = ps.font(11), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
        )

        // Sprache und Startmodus sind keine Ja/Nein-Fragen und stehen
        // deshalb als Auswahl in der Darstellung.
        if (istDarstellung) {
            AuswahlZeile(
                titel = st("Language", "Sprache"),
                warum = st(
                    "Labels come from PrusaSlicer's own catalog.",
                    "Die Beschriftungen stammen aus PrusaSlicers eigenem Katalog.",
                ),
                werte = listOf("en", "de"),
                gewaehlt = einstellungen.language,
                beschriftung = { if (it == "de") "Deutsch" else "English" },
                kennung = "appeinstellungen.sprache",
                waehlen = { einstellungen.language = it },
            )
            AuswahlZeile(
                titel = st("On start", "Beim Start"),
                warum = st(
                    "Skip the mode question if you always use the same one.",
                    "Die Modusfrage entfällt, wenn man ohnehin immer denselben nimmt.",
                ),
                werte = AppSettings.startModes,
                gewaehlt = einstellungen.startMode,
                beschriftung = { AppSettings.startModeLabel(it) },
                kennung = "appeinstellungen.startmodus",
                waehlen = { einstellungen.startMode = it },
            )
        }

        schalter.forEach { eintrag ->
            SchalterZeile(eintrag, einstellungen)
        }
    }
}

/// Der Selbsttest und der Protokoll-Export, ganz unten. Das sind
/// Werkzeuge, keine Vorlieben.
@Composable
private fun Diagnose(onSelbsttest: () -> Unit) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8)), horizontalAlignment = Alignment.Start) {
        Text(
            st("Diagnostics", "Diagnose").uppercase(),
            Modifier.padding(top = ps.pt(20), bottom = ps.pt(6)),
            fontSize = ps.font(11), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(10)))
                .background(PrusaColors.panelRaised)
                .clickable(onClick = onSelbsttest)
                .testTag("appeinstellungen.selbsttest")
                .heightIn(min = ps.touch(56))
                .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
                Text(st("Self-test", "Selbsttest"), fontSize = ps.font(14), color = PrusaColors.textPrimary)
                Text(
                    st(
                        "Runs loading, slicing, saving, painting and a load test on this device, and writes a report.",
                        "Prüft Laden, Schneiden, Sichern, Bemalen und eine Volllast auf diesem Gerät und schreibt einen Bericht.",
                    ),
                    fontSize = ps.font(11), color = PrusaColors.textMuted,
                )
            }
            Text("›", fontSize = ps.font(18), color = PrusaColors.orange)
        }

        // Frisch geschrieben beim Antippen, nicht einmal beim ersten
        // Aufbau - sonst zeigt der Export einen alten Stand.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(10)))
                .background(PrusaColors.panelRaised)
                .clickable {
                    val uri = runCatching { LogExport.uri(context) }.getOrNull() ?: return@clickable
                    teilen(context, uri, "text/plain", "psmobile-protokoll.txt")
                }
                .testTag("appeinstellungen.protokoll")
                .heightIn(min = ps.touch(56))
                .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
                Text(st("Export log", "Protokoll exportieren"), fontSize = ps.font(14), color = PrusaColors.textPrimary)
                Text(
                    st(
                        "The last warnings and errors from the core, as a text file to share.",
                        "Die letzten Warnungen und Fehler aus dem Kern, als teilbare Textdatei.",
                    ),
                    fontSize = ps.font(11), color = PrusaColors.textMuted,
                )
            }
            SfSymbol("square.and.arrow.up", Modifier.size(ps.pt(16)), tint = PrusaColors.orange)
        }

    }
}

/// Woraus die App besteht und wo ihr Quellcode liegt - PrusaSlicers
/// AGPL verlangt beides sichtbar in der App. Ganz unten, unter der
/// Diagnose, mit der Versionszeile.
@Composable
private fun Ueber(onOpenSource: () -> Unit) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8)), horizontalAlignment = Alignment.Start) {
        Text(
            st("About", "Über").uppercase(),
            Modifier.padding(top = ps.pt(20), bottom = ps.pt(6)),
            fontSize = ps.font(11), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(10)))
                .background(PrusaColors.panelRaised)
                .clickable(onClick = onOpenSource)
                .testTag("appeinstellungen.opensource")
                .heightIn(min = ps.touch(56))
                .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
                Text(st("Open source & licenses", "Open Source & Lizenzen"), fontSize = ps.font(14), color = PrusaColors.textPrimary)
                Text(
                    st(
                        "Based on PrusaSlicer (AGPL-3.0). Source code on GitHub and the list of third-party components.",
                        "Basiert auf PrusaSlicer (AGPL-3.0). Quellcode auf GitHub und die Liste der verwendeten Komponenten.",
                    ),
                    fontSize = ps.font(11), color = PrusaColors.textMuted,
                )
            }
            Text("›", fontSize = ps.font(18), color = PrusaColors.orange)
        }
        Versionszeile()
    }
}

@Composable
private fun Versionszeile() {
    val ps = LocalPsScale.current
    Text(
        "Slicer Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        Modifier
            .padding(top = ps.pt(16))
            .fillMaxWidth()
            .testTag("appeinstellungen.version"),
        fontSize = ps.font(11), color = PrusaColors.textMuted, textAlign = TextAlign.Center,
    )
}

@Composable
private fun SchalterZeile(schalter: AppSettings.Toggle, einstellungen: AppSettingsStore) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = ps.pt(6))
            .clip(RoundedCornerShape(ps.pt(10)))
            .background(PrusaColors.panelRaised)
            .heightIn(min = ps.touch(56))
            .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ps.pt(2))) {
            Text(schalter.title.text, fontSize = ps.font(14), color = PrusaColors.textPrimary)
            Text(schalter.why.text, fontSize = ps.font(11), color = PrusaColors.textMuted)
        }
        Switch(
            checked = einstellungen.bool(schalter.key, schalter.standard),
            onCheckedChange = { einstellungen.set(schalter.key, it) },
            modifier = Modifier.testTag("appeinstellungen.${schalter.key}"),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrusaColors.orange,
            ),
        )
    }
}

@Composable
private fun AuswahlZeile(
    titel: String,
    warum: String,
    werte: List<String>,
    gewaehlt: String,
    beschriftung: (String) -> String,
    kennung: String,
    waehlen: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    var offen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = ps.pt(6))
            .clip(RoundedCornerShape(ps.pt(10)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
            Text(titel, fontSize = ps.font(14), color = PrusaColors.textPrimary)
            Text(warum, fontSize = ps.font(11), color = PrusaColors.textMuted)
        }
        // Ein Menue statt einer Reihe Knoepfe: die Beschriftungen des
        // Startmodus sind zu lang fuer nebeneinander.
        Box {
            val form = RoundedCornerShape(ps.pt(6))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(44))
                    .background(PrusaColors.background, form)
                    .border(1.dp, PrusaColors.divider, form)
                    .clickable { offen = true }
                    .testTag(kennung)
                    .padding(horizontal = ps.pt(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(beschriftung(gewaehlt), fontSize = ps.font(13), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
                SfSymbol("chevron.up.chevron.down", Modifier.size(ps.pt(10)), tint = PrusaColors.textMuted)
            }
            DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                ScaledOverlay {
                    Column {
                        werte.forEach { wert ->
                            DropdownMenuItem(
                                text = { Text(beschriftung(wert)) },
                                onClick = {
                                    offen = false
                                    waehlen(wert)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
