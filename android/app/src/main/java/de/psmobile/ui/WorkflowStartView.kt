package de.psmobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.AppSettings
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.shared.rules.Defaults
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/// Der Einstieg.
///
/// Zwei Module statt drei: Remote Slicing ist kein dritter Arbeitsmodus
/// mehr, sondern ein Umschalter neben dem Slice-Knopf in
/// Simple/Advanced - hier steht dafuer nur noch ein kleiner
/// Fusszeilen-Eintrag.
///
/// Simple/Advanced starten immer ein neues, leeres Projekt - wer am
/// alten weiterarbeiten will, tut das ueber "Zuletzt" oben.
@Composable
fun WorkflowStartView(
    onSimple: () -> Unit,
    onAdvanced: () -> Unit,
    onAppSettings: () -> Unit,
    onPrinterSetup: () -> Unit = {},
    onRemote: () -> Unit = {},
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    var alleProjekteZeigen by remember { mutableStateOf(false) }
    var gewaehltesProjekt by remember { mutableStateOf<File?>(null) }
    val remoteSlicePluginAn = rememberAppSetting(AppSettings.KEY_PLUGIN_REMOTE_SLICE, true)

    val eng = ps.factor <= 0.8f
    val breit = ps.windowSize.width >= 760.dp

    fun neuesProjektStarten(weiter: () -> Unit) {
        model.newProject()
        weiter()
    }

    fun projektOeffnen(inAdvanced: Boolean) {
        val url = gewaehltesProjekt ?: return
        gewaehltesProjekt = null
        // Als Projekt, nicht als Modellimport - siehe WorkflowStartView.swift.
        model.loadProject(url)
        if (inAdvanced) onAdvanced() else onSimple()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = ps.windowSize.height),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(ps.pt(if (eng) 16 else 32)))
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = ps.pt(20))
                    .widthIn(max = ps.pt(if (breit) 880 else 480))
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ps.pt(if (eng) 20 else 28)),
                horizontalAlignment = Alignment.Start,
            ) {
                Kopf(eng)
                // Der wiederhergestellte Arbeitsstand: bis zum 16.09.2026 kam er
                // beim Start zurueck, und der erste Tipp auf einen Modus warf ihn
                // mit newProject() wieder weg (S23 FE, HOME + Prozess-Ende).
                WeiterarbeitenBereich(model = model, onSimple = onSimple, onAdvanced = onAdvanced)
                ZuletztBereich(
                    model = model,
                    onAlle = { alleProjekteZeigen = true },
                    onWahl = { gewaehltesProjekt = it },
                )
                ModusKarten(
                    breit = breit,
                    onSimple = { neuesProjektStarten(onSimple) },
                    onAdvanced = { neuesProjektStarten(onAdvanced) },
                )
                Fusszeile(remoteSlicePluginAn, onAppSettings, onPrinterSetup, onRemote)
            }
            Spacer(Modifier.height(ps.pt(24)))
        }
        PSMarke(name = "start")
        KaffeeLink(Modifier.align(Alignment.BottomEnd))
    }

    // confirmationDialog: der Projektname als Titel, die drei Knoepfe
    // untereinander.
    gewaehltesProjekt?.let { url ->
        AlertDialog(
            onDismissRequest = { gewaehltesProjekt = null },
            title = { Text(url.nameWithoutExtension) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { projektOeffnen(inAdvanced = false) },
                        modifier = Modifier.fillMaxWidth().testTag("projekt.oeffnen.simple"),
                    ) {
                        Text(st("Open in Simple mode", "In Simple Mode öffnen"), color = PrusaColors.orange)
                    }
                    TextButton(
                        onClick = { projektOeffnen(inAdvanced = true) },
                        modifier = Modifier.fillMaxWidth().testTag("projekt.oeffnen.advanced"),
                    ) {
                        Text(st("Open in Advanced mode", "In Advanced Mode öffnen"), color = PrusaColors.orange)
                    }
                    TextButton(
                        onClick = { gewaehltesProjekt = null },
                        modifier = Modifier.fillMaxWidth().testTag("projekt.oeffnen.abbrechen"),
                    ) {
                        Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
                    }
                }
            },
            confirmButton = {},
            containerColor = PrusaColors.panel,
            titleContentColor = PrusaColors.textPrimary,
            textContentColor = PrusaColors.textPrimary,
        )
    }

    if (alleProjekteZeigen) {
        Dialog(
            onDismissRequest = { alleProjekteZeigen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                val rand = max(ps.pt(12), 12.dp)
                val form = RoundedCornerShape(ps.pt(8))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .padding(rand)
                            .widthIn(max = min(ps.pt(640), ps.windowSize.width - rand * 2))
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(form)
                            .background(PrusaColors.background)
                            .border(1.dp, PrusaColors.divider, form),
                    ) {
                        AllProjectsSheet(
                            onOpen = { url ->
                                alleProjekteZeigen = false
                                gewaehltesProjekt = url
                            },
                            onClose = { alleProjekteZeigen = false },
                            model = model,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Kopf(eng: Boolean) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ps.pt(if (eng) 40 else 48))
                .clip(RoundedCornerShape(ps.pt(5)))
                .background(PrusaColors.orange),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", fontSize = ps.font(22), fontWeight = FontWeight.Bold, color = PrusaColors.background)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.Start) {
            Text("Slicer Mobile", fontSize = ps.font(24), fontWeight = FontWeight.SemiBold, color = PrusaColors.textPrimary)
            Text(
                st("3D PRINT WORKSPACE", "3D-DRUCK-ARBEITSPLATZ"),
                fontSize = ps.font(11), fontWeight = FontWeight.Medium, color = PrusaColors.textMuted,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/// Der beim Start wiederhergestellte Arbeitsstand - nur zu sehen, wenn
/// etwas auf dem Bett liegt. Die Knoepfe fuehren in den Modus, ohne das
/// Projekt zu leeren (Gegenstueck: weiterarbeitenBereich drueben).
@Composable
private fun WeiterarbeitenBereich(model: SlicerModel, onSimple: () -> Unit, onAdvanced: () -> Unit) {
    val ps = LocalPsScale.current
    val anzahl = model.objects.size
    if (anzahl == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10)), horizontalAlignment = Alignment.Start) {
        Text(
            st("Continue where you left off", "Weiterarbeiten, wo du aufgehört hast"),
            fontSize = ps.font(12), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(8)))
                .background(PrusaColors.panel)
                .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (anzahl == 1) st("1 object on the bed", "1 Objekt auf dem Bett")
                else st("$anzahl objects on the bed", "$anzahl Objekte auf dem Bett"),
                fontSize = ps.font(13), color = PrusaColors.textPrimary, modifier = Modifier.weight(1f),
            )
            Text(
                st("Simple", "Simple"),
                Modifier.clickable(onClick = onSimple).testTag("start.weiter.simple").padding(ps.pt(6)),
                fontSize = ps.font(13), color = PrusaColors.orange,
            )
            Text(
                st("Advanced", "Advanced"),
                Modifier.clickable(onClick = onAdvanced).testTag("start.weiter.advanced").padding(ps.pt(6)),
                fontSize = ps.font(13), color = PrusaColors.orange,
            )
        }
    }
}

/// Bis zu vier zuletzt gesicherte Projekte, plus "Alle anzeigen" bei
/// mehr. Fehlt ganz, wenn noch nichts gesichert wurde.
@Composable
private fun ZuletztBereich(
    model: SlicerModel,
    onAlle: () -> Unit,
    onWahl: (File) -> Unit,
) {
    val ps = LocalPsScale.current
    val dateien = model.recentProjects(limit = 4)
    if (dateien.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10)), horizontalAlignment = Alignment.Start) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    st("Recent", "Zuletzt"),
                    fontSize = ps.font(12), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                if (model.recentProjects(limit = 5).size > 4) {
                    Text(
                        st("Show all", "Alle anzeigen"),
                        Modifier
                            .clickable(onClick = onAlle)
                            .testTag("start.alleProjekte"),
                        fontSize = ps.font(12), color = PrusaColors.orange,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ps.pt(10))) {
                dateien.forEachIndexed { index, url ->
                    key(url.absolutePath) {
                        Box(
                            Modifier
                                .weight(1f)
                                .testTag("start.zuletzt.$index"),
                        ) {
                            ProjektKachel(
                                url = url,
                                oeffnen = { onWahl(url) },
                                loeschen = { model.deleteProject(url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModusKarten(breit: Boolean, onSimple: () -> Unit, onAdvanced: () -> Unit) {
    val ps = LocalPsScale.current
    if (breit) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(14)),
            verticalAlignment = Alignment.Top,
        ) {
            ModusKarte(
                Modifier.weight(1f), breit,
                nummer = "01", symbol = "bolt.fill", titel = "Simple Mode",
                detail = st(
                    "Get to print quickly with a few clear decisions.",
                    "Schnell zum Druck mit wenigen, klaren Entscheidungen.",
                ),
                kennung = "start.simple", handlung = onSimple,
            )
            ModusKarte(
                Modifier.weight(1f), breit,
                nummer = "02", symbol = "slider.horizontal.3", titel = "Advanced Mode",
                detail = st(
                    "Every setting PrusaSlicer knows, on all pages.",
                    "Alle Einstellungen, die PrusaSlicer kennt, auf allen Seiten.",
                ),
                kennung = "start.advanced", handlung = onAdvanced,
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ps.pt(10))) {
            ModusKarte(
                Modifier.fillMaxWidth(), breit,
                nummer = "01", symbol = "bolt.fill", titel = "Simple Mode",
                detail = st(
                    "Get to print quickly with a few clear decisions.",
                    "Schnell zum Druck mit wenigen, klaren Entscheidungen.",
                ),
                kennung = "start.simple", handlung = onSimple,
            )
            ModusKarte(
                Modifier.fillMaxWidth(), breit,
                nummer = "02", symbol = "slider.horizontal.3", titel = "Advanced Mode",
                detail = st(
                    "Every setting PrusaSlicer knows, on all pages.",
                    "Alle Einstellungen, die PrusaSlicer kennt, auf allen Seiten.",
                ),
                kennung = "start.advanced", handlung = onAdvanced,
            )
        }
    }
}

@Composable
private fun ModusKarte(
    modifier: Modifier,
    breit: Boolean,
    nummer: String,
    symbol: String,
    titel: String,
    detail: String,
    kennung: String,
    handlung: () -> Unit,
) {
    val ps = LocalPsScale.current
    val form = RoundedCornerShape(ps.pt(6))
    Column(
        modifier
            .heightIn(min = ps.pt(if (breit) 190 else 150))
            .background(PrusaColors.panel, form)
            .border(1.dp, PrusaColors.divider, form)
            .clickable(onClick = handlung)
            .testTag(kennung)
            .padding(ps.pt(18)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(ps.pt(34))
                    .clip(RoundedCornerShape(ps.pt(6)))
                    .background(PrusaColors.orange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                SfSymbol(symbol, Modifier.size(ps.pt(18)), tint = PrusaColors.orange)
            }
            Spacer(Modifier.weight(1f))
            Text(nummer, fontSize = ps.font(12), fontWeight = FontWeight.Bold, color = PrusaColors.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4)), horizontalAlignment = Alignment.Start) {
            Text(titel, fontSize = ps.font(17), fontWeight = FontWeight.SemiBold, color = PrusaColors.textPrimary)
            Text(detail, fontSize = ps.font(12), color = PrusaColors.textMuted)
        }
        Row(
            Modifier.padding(top = ps.pt(10)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                st("New project", "Neues Projekt"),
                fontSize = ps.font(13), fontWeight = FontWeight.SemiBold, color = PrusaColors.orange,
            )
            SfSymbol("arrow.right", Modifier.size(ps.pt(13)), tint = PrusaColors.orange)
        }
    }
}

@Composable
private fun Fusszeile(
    remoteSlicePluginAn: Boolean,
    onAppSettings: () -> Unit,
    onPrinterSetup: () -> Unit,
    onRemote: () -> Unit,
) {
    val ps = LocalPsScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10)), verticalAlignment = Alignment.CenterVertically) {
        FusszeilenKnopf(
            st("App settings", "App-Einstellungen"),
            symbol = "gearshape", kennung = "start.appeinstellungen", aktion = onAppSettings,
        )
        FusszeilenKnopf(
            st("Printer setup", "Ersteinrichtung"),
            symbol = "printer", kennung = "start.einrichtung", aktion = onPrinterSetup,
        )
        if (remoteSlicePluginAn) {
            FusszeilenKnopf(
                st("Remote Slicing", "Remote Slicing"),
                symbol = "cloud", kennung = "start.remote", aktion = onRemote,
            )
        }
    }
}

/**
 * Ganz klein unten in der Ecke: der Link auf Nils' Buy-me-a-coffee-Seite
 * (Defaults.KAFFEE_LINK). Kein Knopf im Sinne der Fusszeile - eine
 * Textzeile in Gedaempft, die den Browser oeffnet.
 */
@Composable
private fun KaffeeLink(modifier: Modifier = Modifier) {
    if (Defaults.KAFFEE_LINK.isEmpty()) return
    val ps = LocalPsScale.current
    val browser = LocalUriHandler.current
    Text(
        "☕ Buy me a coffee",
        fontSize = ps.font(10), color = PrusaColors.textMuted,
        modifier = modifier
            .testTag("start.kaffee")
            .clickable { runCatching { browser.openUri(Defaults.KAFFEE_LINK) } }
            .padding(horizontal = ps.pt(10), vertical = ps.pt(8)),
    )
}

@Composable
private fun FusszeilenKnopf(label: String, symbol: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PrusaColors.panelRaised)
            .clickable(onClick = aktion)
            .testTag(kennung)
            .heightIn(min = ps.touch(42))
            .padding(horizontal = ps.pt(14)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfSymbol(symbol, Modifier.size(ps.pt(13)), tint = PrusaColors.textMuted)
        Text(label, fontSize = ps.font(13), color = PrusaColors.textMuted)
    }
}

/// Alle gesicherten Projekte, nicht nur die vier auf der Startseite -
/// dieselbe Auswahl (Simple/Advanced) wie dort, nur mit vollstaendiger
/// Liste statt Kacheln. Nicht privat: der Advanced Mode zeigt denselben
/// Bestand ueber denselben Weg.
@Composable
fun AllProjectsSheet(
    onOpen: (File) -> Unit,
    onClose: () -> Unit,
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    var revision by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Navigationsleiste: Titel links, "Fertig" als Systemknopf rechts.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(56))
                    .padding(horizontal = ps.pt(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    st("All projects", "Alle Projekte"),
                    fontSize = ps.font(20), fontWeight = FontWeight.SemiBold, color = PrusaColors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose, modifier = Modifier.testTag("projekte.alle.fertig")) {
                    Text(st("Done", "Fertig"), color = PrusaColors.orange)
                }
            }
            HorizontalDivider(color = PrusaColors.divider)
            key(revision) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(model.recentProjects(limit = 500), key = { it.absolutePath }) { url ->
                        ProjektZeile(
                            url = url,
                            onOpen = { onOpen(url) },
                            onDelete = {
                                model.deleteProject(url)
                                revision += 1
                            },
                        )
                        HorizontalDivider(color = PrusaColors.divider)
                    }
                }
            }
        }
        PSMarke(name = "projekte.alle")
    }
}

/// Eine Listenzeile mit swipeActions: nach links wischen legt den roten
/// Loeschen-Knopf frei.
@Composable
private fun ProjektZeile(url: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ps = LocalPsScale.current
    val scope = rememberCoroutineScope()
    val versatz = remember { Animatable(0f) }
    val knopfBreite = ps.pt(88)

    Box(Modifier.fillMaxWidth().heightIn(min = ps.touch(48))) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(knopfBreite)
                .fillMaxHeight()
                .background(PrusaColors.danger)
                .clickable(onClick = onDelete)
                .testTag("projekte.alle.loeschen.${url.name}"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SfSymbol("trash", Modifier.size(ps.pt(18)), tint = Color.White)
                Text(st("Delete", "Löschen"), fontSize = ps.font(12), color = Color.White)
            }
        }
        Row(
            Modifier
                .offset { IntOffset(versatz.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(PrusaColors.background)
                .pointerInput(Unit) {
                    val breitePx = knopfBreite.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                versatz.animateTo(if (versatz.value < -breitePx / 2) -breitePx else 0f)
                            }
                        },
                        onDragCancel = { scope.launch { versatz.animateTo(0f) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { versatz.snapTo((versatz.value + delta).coerceIn(-breitePx, 0f)) }
                    }
                }
                .clickable(onClick = onOpen)
                .testTag("projekte.alle.${url.name}")
                .heightIn(min = ps.touch(48))
                .padding(horizontal = ps.pt(16)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SfSymbol("square.stack.3d.up", Modifier.size(ps.pt(18)), tint = PrusaColors.orange)
            Text(url.nameWithoutExtension, fontSize = ps.font(15), color = PrusaColors.textPrimary)
        }
    }
}

/// Eine Kachel im "Zuletzt"-Streifen mit Wegwisch-Geste zum Loeschen.
@Composable
private fun ProjektKachel(
    url: File,
    oeffnen: () -> Unit,
    loeschen: () -> Unit,
) {
    val ps = LocalPsScale.current
    val scope = rememberCoroutineScope()
    val versatz = remember { Animatable(0f) }
    var entfernt by remember { mutableStateOf(false) }

    Column(
        Modifier
            .then(if (entfernt) Modifier.width(0.dp) else Modifier.fillMaxWidth())
            .offset { IntOffset(versatz.value.roundToInt(), 0) }
            .alpha(if (entfernt) 0f else 1f)
            .pointerInput(Unit) {
                var gesamt = 0f
                detectHorizontalDragGestures(
                    onDragStart = { gesamt = 0f },
                    onDragEnd = {
                        if (versatz.value < -ps.pt(70).toPx()) {
                            val ziel = -ps.pt(300).toPx()
                            scope.launch {
                                launch { versatz.animateTo(ziel, tween(180)) }
                                entfernt = true
                                delay(180)
                                loeschen()
                            }
                        } else {
                            scope.launch {
                                versatz.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { versatz.animateTo(0f) } },
                ) { change, delta ->
                    gesamt += delta
                    // Nur nach links (negativ) mitnehmen - ein Wisch nach
                    // rechts waere sonst mit dem Scrollen verwechselbar.
                    if (gesamt < 0f) {
                        change.consume()
                        scope.launch { versatz.snapTo(gesamt) }
                    }
                }
            }
            .clip(RoundedCornerShape(ps.pt(8)))
            .background(PrusaColors.panelRaised)
            .clickable(onClick = oeffnen)
            .testTag("start.zuletzt.loeschen." + url.name)
            .padding(ps.pt(8)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ps.pt(56))
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panel),
            contentAlignment = Alignment.Center,
        ) {
            SfSymbol("square.stack.3d.up", Modifier.size(ps.pt(18)), tint = PrusaColors.textMuted)
        }
        Text(
            url.nameWithoutExtension,
            fontSize = ps.font(11), fontWeight = FontWeight.Medium, color = PrusaColors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
