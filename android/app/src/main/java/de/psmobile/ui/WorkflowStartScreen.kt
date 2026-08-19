package de.psmobile.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.net.RecentProjectsStore

@Composable
fun WorkflowStartScreen(
    onSimple: () -> Unit,
    onAdvanced: () -> Unit,
    onAdvancedWizard: () -> Unit,
    onAppSettings: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onRemote: () -> Unit = {},
    onManagePrinters: () -> Unit = {},
    onOpenRecent: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val remoteSlicePluginAn = context
        .getSharedPreferences("psmobile", android.content.Context.MODE_PRIVATE)
        .getBoolean(de.psmobile.shared.rules.AppSettings.KEY_PLUGIN_REMOTE_SLICE, true)
    // Auf niedrigen Fenstern - Querformat auf einem Telefon, geteilter
    // Bildschirm - passte bisher nur die erste Karte auf den Schirm; die
    // zweite Wahlmoeglichkeit war unsichtbar. Deshalb dieselbe
    // Dichteentscheidung wie ueberall sonst.
    val configuration = LocalConfiguration.current
    val tight = UiScale.density(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    ) == UiDensity.TIGHT
    val breit = configuration.screenWidthDp >= 760
    var languageMenu by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(PsUi.language) }
    var zuletztRevision by remember { mutableStateOf(0) }
    var gewaehltesProjekt by remember { mutableStateOf<RecentProjectsStore.Eintrag?>(null) }
    val zuletzt = remember(zuletztRevision) { RecentProjectsStore.alle(context) }
    // Das Sichern eines Projekts navigiert nach Hause, bevor der
    // asynchrone Schreibvorgang (und damit RecentProjectsStore.hinzufuegen)
    // fertig ist (siehe SimpleModeScreen.kt: "onSaveProject(); onHome()"
    // direkt hintereinander). Ein einmaliges Einlesen beim Aufbau dieses
    // Bildschirms wuerde den neuen Eintrag deshalb verpassen - stattdessen
    // auf Aenderungen an der Liste selbst hoeren.
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(
            RecentProjectsStore.PREFS, android.content.Context.MODE_PRIVATE,
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RecentProjectsStore.KEY) zuletztRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box(
        Modifier.fillMaxSize().background(PrusaColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                // Die Activity zeichnet bewusst edge-to-edge. Der Einstieg
                // darf trotzdem nie unter Uhr, Kameraausschnitt oder
                // Benachrichtigungen beginnen.
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (tight) 12.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (tight) 14.dp else 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(if (tight) 34.dp else 40.dp)
                        .background(PrusaColors.Orange, RoundedCornerShape(Corners.FIELD.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("S", color = PrusaColors.Background, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("PSMobile", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        SimpleModeState.text("3D PRINT WORKSPACE", "3D-DRUCK-ARBEITSPLATZ"),
                        color = PrusaColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                OutlinedButton(onClick = { languageMenu = true }) { Text(language.uppercase() + " ▾") }
                DropdownMenu(
                    expanded = languageMenu,
                    onDismissRequest = { languageMenu = false },
                ) {
                    ScaledOverlay {
                    PsUi.availableLanguages.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code.uppercase()) },
                            onClick = {
                                language = code
                                languageMenu = false
                                PsUi.setLanguage(context, code)
                                onLanguageChange(code)
                            },
                        )
                    }
                    }
                }
            }

            // Bis zu vier zuletzt gesicherte Projekte - fehlt ganz, wenn
            // noch nichts gesichert wurde, eine leere Zeile waere nur eine
            // Frage ohne Antwort. Gegenstueck zu `zuletztBereich` in
            // WorkflowStartView.swift (iOS). Die Kacheln teilen sich die
            // Zeilenbreite (wie iOS' HStack aus flexiblen Kacheln), statt
            // in einer horizontal scrollbaren Leiste mit fester Breite zu
            // stehen.
            if (zuletzt.isNotEmpty()) {
                var alleZeigen by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleModeState.text("RECENT", "ZULETZT"),
                            color = PrusaColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (zuletzt.size > 4) {
                            Text(
                                if (alleZeigen) SimpleModeState.text("Show less", "Weniger zeigen")
                                else SimpleModeState.text("Show all", "Alle anzeigen"),
                                color = PrusaColors.Orange,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { alleZeigen = !alleZeigen },
                            )
                        }
                    }
                    val sichtbar = if (alleZeigen) zuletzt else zuletzt.take(4)
                    sichtbar.chunked(4).forEach { zeile ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            zeile.forEach { eintrag ->
                                Box(Modifier.weight(1f)) {
                                    ProjektKachel(
                                        eintrag = eintrag,
                                        onOpen = { gewaehltesProjekt = eintrag },
                                        onDelete = {
                                            RecentProjectsStore.entfernen(context, eintrag.uri)
                                            zuletztRevision++
                                        },
                                    )
                                }
                            }
                            repeat(4 - zeile.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            @Composable fun simpleKarte() {
                ModusKarte(
                    nummer = "01",
                    icon = Icons.Filled.Bolt,
                    titel = "Simple Mode",
                    detail = SimpleModeState.text(
                        "Get to print quickly with a few clear decisions.",
                        "Schnell zum Druck mit wenigen, klaren Entscheidungen.",
                    ),
                    breit = breit,
                    onClick = onSimple,
                )
            }
            @Composable fun advancedKarte() {
                ModusKarte(
                    nummer = "02",
                    icon = Icons.Filled.Tune,
                    titel = "Advanced Mode",
                    detail = SimpleModeState.text(
                        "Every setting PrusaSlicer knows, on all pages.",
                        "Alle Einstellungen, die PrusaSlicer kennt, auf allen Seiten.",
                    ),
                    breit = breit,
                    onClick = onAdvanced,
                )
            }
            if (breit) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1f)) { simpleKarte() }
                    Box(Modifier.weight(1f)) { advancedKarte() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    simpleKarte()
                    advancedKarte()
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FusszeilenKnopf(SimpleModeState.text("App settings", "App-Einstellungen"), Icons.Filled.Settings, onAppSettings)
                FusszeilenKnopf(SimpleModeState.text("Printer setup", "Ersteinrichtung"), Icons.Filled.Print, onAdvancedWizard)
                FusszeilenKnopf(SimpleModeState.text("Manage printers", "Drucker verwalten"), Icons.Filled.Wifi, onManagePrinters)
                if (remoteSlicePluginAn) {
                    FusszeilenKnopf(SimpleModeState.text("Remote Slicing", "Remote Slicing"), Icons.Filled.Cloud, onRemote)
                }
            }
        }
    }

    gewaehltesProjekt?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { gewaehltesProjekt = null },
            title = { Text(eintrag.name) },
            text = {
                Text(
                    SimpleModeState.text(
                        "Open this project in Simple or Advanced mode.",
                        "Dieses Projekt in Simple oder Advanced Mode öffnen.",
                    ),
                    color = PrusaColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = eintrag.uri
                    gewaehltesProjekt = null
                    onOpenRecent(u, true)
                }) { Text(SimpleModeState.text("Open in Advanced mode", "In Advanced Mode öffnen")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val u = eintrag.uri
                        gewaehltesProjekt = null
                        onOpenRecent(u, false)
                    }) { Text(SimpleModeState.text("Open in Simple mode", "In Simple Mode öffnen")) }
                    TextButton(onClick = { gewaehltesProjekt = null }) {
                        Text(SimpleModeState.text("Cancel", "Abbrechen"))
                    }
                }
            },
        )
    }
}

@Composable
private fun ModusKarte(
    nummer: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titel: String,
    detail: String,
    breit: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth()
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
            .clickable(onClick = onClick),
        color = PrusaColors.Panel,
        shape = RoundedCornerShape(Corners.FIELD.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp)
                        .background(PrusaColors.Orange.copy(alpha = 0.12f), RoundedCornerShape(Corners.FIELD.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = PrusaColors.Orange, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.weight(1f))
                Text(nummer, color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titel, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                SimpleModeState.text("New project", "Neues Projekt") + "  →",
                color = PrusaColors.Orange,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FusszeilenKnopf(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        Modifier.heightIn(min = psTouch(40)).clickable(onClick = onClick),
        color = PrusaColors.PanelRaised,
        shape = CircleShape,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = PrusaColors.TextMuted, modifier = Modifier.size(15.dp))
            Text(label, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Kachel im "Zuletzt"-Streifen mit Wegwisch-Geste zum Loeschen -
 * Gegenstueck zu ProjektKachel in WorkflowStartView.swift (iOS). */
@Composable
private fun ProjektKachel(
    eintrag: RecentProjectsStore.Eintrag,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var versatz by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .offset { androidx.compose.ui.unit.IntOffset(versatz.toInt(), 0) }
            .pointerInput(eintrag.uri) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (versatz < -70f) onDelete() else versatz = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Nur nach links (negativ) mitnehmen - ein Wisch
                        // nach rechts waere sonst mit dem Scrollen des
                        // Streifens selbst verwechselbar.
                        if (dragAmount < 0f || versatz < 0f) versatz += dragAmount
                    },
                )
            }
            .clickable(onClick = onOpen)
            .background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.FIELD.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val thumb = remember(eintrag.thumbPath) {
            eintrag.thumbPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
            }
        }
        Box(
            Modifier.fillMaxWidth().height(psTouch(56))
                .background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(psTouch(56)).clip(RoundedCornerShape(Corners.FIELD.dp)),
                )
            } else {
                Icon(Icons.Filled.ViewInAr, contentDescription = null, tint = PrusaColors.TextMuted)
            }
        }
        Text(
            eintrag.name,
            color = PrusaColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
