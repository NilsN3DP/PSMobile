package de.psmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.TabsCatalog
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Die Einstellungsseiten - alle 20 aus einem Renderer. Gegenstueck zu
 * `ios/PSMobile/Screens/SettingsView.swift`.
 *
 * Es gibt hier bewusst keinen handgebauten Bildschirm je Seite:
 * Struktur, Reihenfolge und Beschriftungen kommen aus PrusaSlicer, Typ,
 * Grenzen und Auswahlwerte aus dem Kern. Was gezeichnet wird,
 * entscheidet der Typ des Parameters.
 */
@Composable
fun SettingsView(
    model: SlicerModel,
    /** Womit der Bildschirm aufgeht - Druck, Filament oder Drucker. */
    startTab: String = "print",
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    var zeigeZuruecksetzen by remember { mutableStateOf(false) }
    var zeigeProfilsuche by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("print") }
    var pageIndex by remember { mutableIntStateOf(0) }
    /** Welcher Sonderbearbeiter offen ist, wenn ueberhaupt. */
    var bearbeiter by remember { mutableStateOf<String?>(null) }

    val tabs = remember { listOf("print", "filament", "printer") }
    val pages: List<TabsCatalog.Page> = PsUiCatalog.pages(tab, model.extruderCount)

    /** Was gegenueber den gespeicherten Profilen geaendert ist. */
    val geaenderte: List<SlicerModel.Profilaenderung> = model.profilaenderungen()

    /** Ob gerade die handgebaute Sonderseite offen ist. */
    val sonderseiteOffen = tab == "printer" && pageIndex == pages.size

    LaunchedEffect(Unit) {
        // Nur beim Erscheinen: waehrend jemand blaettert, soll der
        // Einstieg von aussen nichts mehr umstellen.
        if (startTab in tabs) tab = startTab
    }
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            // MARK: - Kopf und Reiter
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ps.pt(16))
                    .height(ps.touch(48)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clickable(onClick = onClose)
                        .fillMaxHeight()
                        .testTag("einstellungen.zurueck"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹  " + PsUiCatalog.tr("Back"), fontSize = ps.font(15), color = PrusaColors.orange)
                }

                // Simple, Advanced, Expert - wie in PrusaSlicer oben links.
                Row(
                    Modifier.padding(start = ps.pt(12)),
                    horizontalArrangement = Arrangement.spacedBy(ps.pt(2)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stufe(PsUiCatalog.tr("Simple"), PsmCore.Mode.SIMPLE, model)
                    Stufe(PsUiCatalog.tr("Advanced"), PsmCore.Mode.ADVANCED, model)
                    Stufe(PsUiCatalog.tr("Expert"), PsmCore.Mode.EXPERT, model)
                }

                Spacer(Modifier.weight(1f))

                // Das gewaehlte Profil gehoert in den Kopf; Antippen
                // oeffnet die Profilsuche.
                val profil = model.selectedPreset(tab)
                if (profil != null) {
                    Row(
                        Modifier
                            .clickable { zeigeProfilsuche = true }
                            .heightIn(min = ps.touch(36))
                            .testTag("einstellungen.profilsuche.oeffnen"),
                        horizontalArrangement = Arrangement.spacedBy(ps.pt(3)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            profil,
                            fontSize = ps.font(13),
                            color = PrusaColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        SfSymbol("magnifyingglass", Modifier.size(ps.pt(11)), tint = PrusaColors.textMuted)
                    }
                }

                // Zuruecksetzen erscheint erst, wenn es etwas
                // zurueckzusetzen gibt.
                if (geaenderte.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(ps.pt(4)))
                            .background(PrusaColors.panelRaised)
                            .clickable { zeigeZuruecksetzen = true }
                            .height(ps.touch(40))
                            .padding(horizontal = ps.pt(10))
                            .testTag("einstellungen.zuruecksetzen"),
                        horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SfSymbol("arrow.counterclockwise", Modifier.size(ps.pt(12)), tint = PrusaColors.orange)
                        Text("${geaenderte.size}", fontSize = ps.font(12), color = PrusaColors.orange)
                    }
                }
            }

            // reiter
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ps.pt(12))
                    .padding(bottom = ps.pt(6)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { name ->
                    val aktiv = tab == name
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(ps.pt(6)))
                            .background(if (aktiv) PrusaColors.panelRaised else Color.Transparent)
                            .clickable {
                                tab = name
                                pageIndex = 0
                            }
                            .height(ps.touch(40))
                            .padding(horizontal = ps.pt(14))
                            .testTag("reiter.$name"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            PsUiCatalog.tr(name.replaceFirstChar { it.uppercase() }),
                            fontSize = ps.font(14),
                            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (aktiv) PrusaColors.textPrimary else PrusaColors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            Trennlinie(Modifier.fillMaxWidth().height(ps.pt(1)))

            Row(Modifier.fillMaxSize()) {
                // MARK: - Seiten
                Seitenliste(
                    pages = pages,
                    tab = tab,
                    pageIndex = pageIndex,
                    sonderseiteOffen = sonderseiteOffen,
                    onSeite = { pageIndex = it },
                )
                Trennlinie(Modifier.fillMaxHeight().width(ps.pt(1)))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (sonderseiteOffen) {
                        Sonderseite(model = model, onBearbeiten = { bearbeiter = it })
                    } else {
                        Inhalt(model = model, pages = pages, pageIndex = pageIndex)
                    }
                }
            }
        }
    }

    // .confirmationDialog "Reset profile to its saved values?"
    if (zeigeZuruecksetzen) {
        AlertDialog(
            onDismissRequest = { zeigeZuruecksetzen = false },
            title = {
                Text(
                    st("Reset profile to its saved values?", "Profil auf seine gespeicherten Werte zurücksetzen?"),
                    fontSize = ps.font(15),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    zeigeZuruecksetzen = false
                    model.profilaenderungenVerwerfen()
                }) {
                    Text(
                        if (geaenderte.size == 1) st("Reset 1 value", "1 Wert zurücksetzen")
                        else st("Reset ${geaenderte.size} values", "${geaenderte.size} Werte zurücksetzen"),
                        color = PrusaColors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { zeigeZuruecksetzen = false }) {
                    Text(st("Cancel", "Abbrechen"))
                }
            },
            containerColor = PrusaColors.panel,
            titleContentColor = PrusaColors.textPrimary,
            textContentColor = PrusaColors.textPrimary,
        )
    }

    // .sheet(item: bearbeiter)
    val auswahl = bearbeiter
    if (auswahl != null) {
        Dialog(
            onDismissRequest = { bearbeiter = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(ps.pt(12)))
                        .background(PrusaColors.panel),
                ) {
                    if (auswahl == "bed_shape") {
                        BedShapeEditor(model = model, onClose = { bearbeiter = null })
                    } else {
                        WipingVolumesEditor(model = model, onClose = { bearbeiter = null })
                    }
                }
            }
        }
    }

    // .sheet(isPresented: zeigeProfilsuche)
    ProfileSearchSheet(
        model = model,
        tab = tab,
        titel = PsUiCatalog.tr(
            when (tab) {
                "print" -> "Print settings"
                "filament" -> "Filament"
                else -> "Printer"
            },
        ),
        isPresented = zeigeProfilsuche,
        onIsPresentedChange = { zeigeProfilsuche = it },
    )
}

/** `Divider().overlay(PrusaColors.divider)` */
@Composable
private fun Trennlinie(modifier: Modifier) {
    Box(modifier.background(PrusaColors.divider))
}

/** Ein Knopf der Einstufung: drei kurze Knoepfe statt eines Menues. */
@Composable
private fun Stufe(label: String, wert: PsmCore.Mode, model: SlicerModel) {
    val ps = LocalPsScale.current
    val aktiv = model.sichtbarkeit == wert
    Box(
        Modifier
            .clip(RoundedCornerShape(ps.pt(3)))
            .background(if (aktiv) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable { model.sichtbarkeit = wert }
            .heightIn(min = ps.touch(36))
            .padding(horizontal = ps.pt(10))
            .testTag("einstellungen.stufe." + wert.ordinal.toString()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = ps.font(11),
            color = if (aktiv) PrusaColors.background else PrusaColors.textMuted,
        )
    }
}

@Composable
private fun Seitenliste(
    pages: List<TabsCatalog.Page>,
    tab: String,
    pageIndex: Int,
    sonderseiteOffen: Boolean,
    onSeite: (Int) -> Unit,
) {
    val ps = LocalPsScale.current
    LazyColumn(
        Modifier
            .width(ps.pt(210))
            .fillMaxHeight()
            .background(PrusaColors.panel),
    ) {
        itemsIndexed(pages) { index, seite ->
            val aktiv = pageIndex == index
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (aktiv) PrusaColors.panelRaised else Color.Transparent)
                    .clickable { onSeite(index) }
                    .height(ps.touch(40))
                    .padding(horizontal = ps.pt(12))
                    .testTag("seite.$index"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    PsUiCatalog.tr(seite.title),
                    fontSize = ps.font(13),
                    fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (aktiv) PrusaColors.orange else PrusaColors.textPrimary,
                )
            }
        }

        // Die einzige Seite, die nicht aus der Vorlage kommt: bed_shape
        // und wiping_volumes_matrix stehen nicht in tabs.json.
        if (tab == "printer") {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(if (sonderseiteOffen) PrusaColors.panelRaised else Color.Transparent)
                        .clickable { onSeite(pages.size) }
                        .height(ps.touch(40))
                        .padding(horizontal = ps.pt(12))
                        .testTag("seite.sonderwerte"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        st("Bed and purging", "Bett und Reinigung"),
                        fontSize = ps.font(13),
                        fontWeight = if (sonderseiteOffen) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sonderseiteOffen) PrusaColors.orange else PrusaColors.textPrimary,
                    )
                }
            }
        }
    }
}

/** Bettform und Reinigungsmengen, jede mit ihrem eigenen Bearbeiter. */
@Composable
private fun Sonderseite(model: SlicerModel, onBearbeiten: (String) -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(ps.pt(16)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
        horizontalAlignment = Alignment.Start,
    ) {
        Sonderwert(PsUiCatalog.tr("Bed shape"), schluessel = "bed_shape", model = model, onBearbeiten = onBearbeiten)
        Sonderwert(PsUiCatalog.tr("Wipe tower"), schluessel = "wiping_volumes_matrix", model = model, onBearbeiten = onBearbeiten)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Sonderwert(titel: String, schluessel: String, model: SlicerModel, onBearbeiten: (String) -> Unit) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6)), horizontalAlignment = Alignment.Start) {
        Text(titel, fontSize = ps.font(13), color = PrusaColors.textPrimary)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panelRaised)
                .clickable { onBearbeiten(schluessel) }
                .height(ps.touch(44))
                .padding(horizontal = ps.pt(10))
                .testTag("sonderwert.$schluessel"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                model.config(schluessel)?.let { if (it.isEmpty()) "—" else it } ?: "—",
                fontSize = ps.font(12),
                color = PrusaColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(st("Edit", "Bearbeiten"), fontSize = ps.font(12), color = PrusaColors.orange)
        }
    }
}

@Composable
private fun Inhalt(model: SlicerModel, pages: List<TabsCatalog.Page>, pageIndex: Int) {
    val ps = LocalPsScale.current
    LazyColumn(
        Modifier
            .fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(ps.pt(16)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(18)),
    ) {
        if (pageIndex < pages.size) {
            itemsIndexed(pages[pageIndex].groups) { _, gruppe ->
                GruppenBlock(model, gruppe)
            }
        }
    }
}

/**
 * Ob ein Parameter auf der gewaehlten Stufe gezeigt wird. Kennt der
 * Kern die Einstufung nicht, wird gezeigt.
 */
private fun sichtbarAufStufe(model: SlicerModel, key: String): Boolean {
    val meta = model.core?.let { runCatching { it.configMeta(key) }.getOrNull() } ?: return true
    return meta.mode.ordinal <= model.sichtbarkeit.ordinal
}

@Composable
private fun GruppenBlock(model: SlicerModel, gruppe: TabsCatalog.Group) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        if (gruppe.title.isNotEmpty()) {
            Text(
                PsUiCatalog.tr(gruppe.title).uppercase(),
                fontSize = ps.font(11),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textMuted,
            )
        }
        // Zusammengehoerende Parameter stehen nebeneinander - die
        // Zuordnung kommt aus dem gemeinsamen Modul.
        TabsCatalog.lines(gruppe).forEach { zeile ->
            val sichtbar = zeile.options.filter { sichtbarAufStufe(model, it.key) }
            val name = zeile.title
            if (sichtbar.isEmpty()) {
                // EmptyView
            } else if (name != null && sichtbar.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4)), horizontalAlignment = Alignment.Start) {
                    Text(PsUiCatalog.tr(name), fontSize = ps.font(13), color = PrusaColors.textPrimary)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                        verticalAlignment = Alignment.Top,
                    ) {
                        sichtbar.forEach { option ->
                            Box(Modifier.weight(1f)) {
                                SettingField(model = model, option = option, kompakt = true)
                            }
                        }
                    }
                }
            } else {
                sichtbar.forEach { option ->
                    SettingField(model = model, option = option, kompakt = false)
                }
            }
        }
    }
}
