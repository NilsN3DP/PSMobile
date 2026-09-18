package de.psmobile.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.shared.rules.AdhesionAdvice
import de.psmobile.shared.rules.AppSettings
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.PreviewRange
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.SimplePanel
import de.psmobile.shared.rules.SimpleSupportChoice
import de.psmobile.shared.rules.TabsCatalog
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Der Simple Mode - Gegenstueck zu `ios/PSMobile/Screens/SimpleModeView.swift`.
 *
 * Der Arbeitsbereich fuellt den Bildschirm, Kopfzeile und Werkzeugleiste
 * liegen darueber, und ein angetipptes Werkzeug oeffnet ein an der
 * Leiste verankertes Panel statt eines vollflaechigen Blatts. So bleibt
 * das Druckbett sichtbar, waehrend man etwas einstellt.
 *
 * Beschriftungen, Panelfolge sowie Stuetzen- und Haftungslogik kommen
 * aus dem gemeinsamen Modul. Hier steht nur die Anordnung.
 */
@Composable
fun SimpleModeView(
    onHome: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenPrinterSetup: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onRemoteSettings: () -> Unit = {},
    /** Den fertigen G-Code an einen Drucker schicken - aus der Zusammenfassung heraus. */
    onSendToPrinter: (File) -> Unit = {},
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val remoteSlicePluginAn = rememberAppSetting(AppSettings.KEY_PLUGIN_REMOTE_SLICE, true)
    val z = remember(model) { SimpleModeZustand(model) }
    val kompakt = istKompakt()

    // Der Dateiwaehler. Eine 3MF kann beides sein - ein Modell, das
    // dazukommt, oder ein Projekt, das alles ersetzt. Das kann die
    // Datei nicht entscheiden, nur der Nutzer (siehe Zweck).
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (dateiEndung(context, uri) == "zip") {
            model.loadZip(uri)
            return@rememberLauncherForActivityResult
        }
        when (z.zweck) {
            Zweck.MODELL -> model.load(uri)
            Zweck.PROJEKT -> model.loadProject(uri)
        }
    }
    z.oeffneImporter = { importer.launch(arrayOf("*/*")) }
    // Zurueck-Geste des Systems (nur Android): nach Hause mit derselben
    // Nachfrage wie der Start-Knopf, statt die App zu beenden.
    BackHandler {
        // Erst die Vorschau zu, dann nach Hause - sonst fragte die App
        // aus der Vorschau heraus nach dem Verwerfen (Emulator, 15.09.2026).
        if (z.vorschau) z.vorschauSchliessen() else z.nachHauseGehen(onHome)
    }

    val dichteWurzel = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background)
            .onGloballyPositioned { z.kurz = with(dichteWurzel) { it.size.height.toDp() } < 520.dp },
    ) {
        arbeitsbereich(z, model)
        // Kopf und Werkzeugleiste messen statt raten: mit festen 116/148 pt
        // lag die Objektleiste auf dem Telefon in der Slice-Zeile (S23 FE,
        // 16.09.2026). Gegenstueck: KopfHoehe in SimpleModeView.swift.
        val dichte = LocalDensity.current
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { z.kopfHoehe = with(dichte) { it.size.height.toDp() } },
            ) {
                kopfzeile(z, model, onHome, onOpenAdvanced)
                werkzeugleiste(z, model, remoteSlicePluginAn, onRemoteSettings)
            }
            Spacer(Modifier.weight(1f))
        }
        if (z.panel == SimplePanel.WORKSPACE) {
            val id = model.selectedId
            val objekt = model.objects.firstOrNull { it.id == id }
            if (id != null && objekt != null) {
                Column(
                    Modifier.fillMaxSize().padding(top = z.kopfHoehe + ps.pt(6)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SimpleObjectBarView(
                        model = model,
                        objekt = objekt,
                        zeigtZurueck = true,
                        onClearSelection = {
                            z.aufFlaeche = false
                            model.select(null)
                        },
                        onFlaechenwahl = { z.aufFlaeche = it },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (z.panel != SimplePanel.WORKSPACE) {
            overlay(z, model, onOpenAdvanced, onOpenPrinterSetup, onAppSettings)
        }
        if (z.panel == SimplePanel.WORKSPACE && !z.vorschau) modellKnopf(z, model)
        if (z.panel == SimplePanel.WORKSPACE && !z.vorschau) schrittleiste(model)
        if (z.panel == SimplePanel.WORKSPACE) werkzeugspalte(z, model)
        val snapshot = z.finalPreview
        if (z.vorschau && snapshot != null) {
            FinalPreviewOverlay(
                snapshot = snapshot,
                range = z.previewRange,
                onRangeChange = { z.previewRange = it },
                view = z.previewView,
                onViewChange = { z.previewView = it },
                hiddenRoles = z.hiddenPreviewRoles,
                onHiddenRolesChange = { z.hiddenPreviewRoles = it },
                hiddenExtruders = z.hiddenPreviewExtruders,
                onHiddenExtrudersChange = { z.hiddenPreviewExtruders = it },
                onEditor = { z.vorschauSchliessen() },
                topInset = z.kopfHoehe + ps.pt(6),
            )
        }
        model.projectNotice?.let { hinweis -> ProjektHinweis(hinweis, model, abstandUnten = ps.pt(80)) }
        if (model.progress != SlicerModel.Progress.Idle) {
            SliceSheet(model = model, onSendToPrinter = onSendToPrinter) { model.dismissProgress() }
        }
        if (z.hinderungsgruende.isNotEmpty()) {
            SliceBlockerSheet(gruende = z.hinderungsgruende) { z.hinderungsgruende = emptyList() }
        }
        model.pendingPresetSwitch?.let { pending -> presetSwitchDialog(pending, model) }
        PSMarke(name = "simple.arbeitsbereich")
    }

    // Wer auf "Vorschau" tippt und dafuer warten musste, will danach
    // die Wege sehen - nicht die Zusammenfassung und dann noch einmal
    // tippen.
    LaunchedEffect(model.progress) {
        if (!z.nachDemSchnittZeigen) return@LaunchedEffect
        when (model.progress) {
            is SlicerModel.Progress.Done -> {
                z.nachDemSchnittZeigen = false
                model.dismissProgress()
                z.vorschauUmschalten()
            }
            is SlicerModel.Progress.Failed -> z.nachDemSchnittZeigen = false
            SlicerModel.Progress.Cancelled -> z.nachDemSchnittZeigen = false
            else -> Unit
        }
    }

    LaunchedEffect(model.sceneRevision) {
        if (z.vorschau && model.previewSnapshot() == null) {
            z.vorschauSchliessen()
        }
    }

    if (z.zeigeColorMix) {
        Dialog(
            onDismissRequest = { z.zeigeColorMix = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize().background(PrusaColors.background)) {
                    ColorMixView(onClose = { z.zeigeColorMix = false })
                }
            }
        }
    }

    if (z.zeigeSpeichernName) {
        AlertDialog(
            onDismissRequest = { z.zeigeSpeichernName = false },
            title = { Text(st("Save project", "Projekt sichern")) },
            text = {
                textfeld(
                    wert = z.speichernName,
                    onWert = { z.speichernName = it },
                    platzhalter = st("Name", "Name"),
                    kennung = "projekt.sichern.name",
                )
            },
            dismissButton = {
                TextButton(onClick = { z.zeigeSpeichernName = false }) {
                    Text(st("Cancel", "Abbrechen"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        z.zeigeSpeichernName = false
                        val name = z.speichernName.trim()
                        model.saveProject(name = if (name.isEmpty()) "PSMobile" else name)
                    },
                    modifier = Modifier.testTag("projekt.sichern.ok"),
                ) { Text(st("Save", "Sichern")) }
            },
        )
    }

    if (z.zeigeVerlassenNachfrage) {
        AlertDialog(
            onDismissRequest = { z.zeigeVerlassenNachfrage = false },
            title = { Text(st("Unsaved changes", "Ungesicherte Änderungen")) },
            text = {
                Text(st("A second tap on a mode would discard this project.",
                    "Ein erneutes Tippen auf einen Modus würde dieses Projekt verwerfen."))
            },
            dismissButton = {
                TextButton(onClick = { z.zeigeVerlassenNachfrage = false }) {
                    Text(st("Cancel", "Abbrechen"))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        z.zeigeVerlassenNachfrage = false
                        onHome()
                    }) { Text(st("Discard", "Verwerfen"), color = PrusaColors.danger) }
                    TextButton(onClick = {
                        z.zeigeVerlassenNachfrage = false
                        model.saveProject(name = model.proposedProjectName)
                        onHome()
                    }) { Text(st("Save", "Sichern")) }
                }
            },
        )
    }

    if (z.zeigeArrange) {
        Dialog(
            onDismissRequest = { z.zeigeArrange = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(
                    Modifier
                        .padding(ps.pt(16))
                        .widthIn(max = ps.pt(420))
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(PrusaColors.panel),
                ) {
                    ArrangePanel(
                        model = model,
                        isPresented = z.zeigeArrange,
                        onIsPresentedChange = { z.zeigeArrange = it },
                    )
                }
            }
        }
    }
}

// MARK: - Zustand

/** Wofuer der Dateiwaehler offen ist. */
private enum class Zweck { MODELL, PROJEKT }

/** Die `@State`-Werte des Swift-Structs, gebuendelt fuer die Teil-Composables. */
private class SimpleModeZustand(val model: SlicerModel) {
    var zeigeVerlassenNachfrage by mutableStateOf(false)
    var zeigeSpeichernName by mutableStateOf(false)
    var speichernName by mutableStateOf("")
    var panel by mutableStateOf(SimplePanel.WORKSPACE)
    var oeffneImporter: () -> Unit = {}
    var zeigeColorMix by mutableStateOf(false)
    var zeigeArrange by mutableStateOf(false)
    var hinderungsgruende by mutableStateOf<List<String>>(emptyList())
    var zweck by mutableStateOf(Zweck.MODELL)

    /** Welches Gizmo am ausgewaehlten Objekt haengt. */
    var gizmo by mutableStateOf(PsmViewport.Gizmo.MOVE)
    /** Ob das Werkzeug "Auf Flaeche legen" an ist. */
    var aufFlaeche by mutableStateOf(false)
    /** Bett oder G-Code-Vorschau. Die Vorschau gibt es erst nach einem Schnitt. */
    var vorschau by mutableStateOf(false)
    /** Gemessene Hoehe von Kopfzeile + Werkzeugleiste - darunter beginnt die Objektleiste. */
    var kopfHoehe by mutableStateOf(140.dp)
    /**
     * Ein kurzes Fenster (Telefon quer): die Werkzeugspalte passt nicht
     * mehr mittig zwischen Werkzeugleiste und Modelle-Blatt. Bis zum
     * 16.09.2026 lag "Verschieben" dann auf dem Slice-Knopf und "Ansicht"
     * auf "Einklappen" (S23 FE quer). Gegenstueck: `kurz` drueben.
     */
    var kurz by mutableStateOf(false)
    /** Ob nach dem laufenden Schnitt die Vorschau aufgehen soll. */
    var nachDemSchnittZeigen by mutableStateOf(false)
    var finalPreview by mutableStateOf<PsmCore.PreviewSnapshot?>(null)
    var previewRange by mutableStateOf(PreviewRange(layerCount = 0))
    var previewView by mutableStateOf(PsmViewport.PreviewView.FEATURE)
    var hiddenPreviewRoles by mutableStateOf<Set<Int>>(emptySet())
    var hiddenPreviewExtruders by mutableStateOf<Set<Int>>(emptySet())
    /** Untere und obere Schichthoehe der Schichthoehen-Darstellung. */
    var schichthoehenDarstellung by mutableStateOf<Pair<Double, Double>?>(null)
    var ansichtZuruecksetzen by mutableIntStateOf(0)

    /**
     * Dieselbe Nachfrage wie im Advanced Mode: ein zweites Tippen auf
     * einen Modus verwirft sonst stillschweigend das offene Projekt.
     */
    fun nachHauseGehen(onHome: () -> Unit) {
        if (model.hasUnsavedChanges) {
            zeigeVerlassenNachfrage = true
        } else {
            onHome()
        }
    }

    /**
     * Statt eines ausgegrauten Knopfes, der nur sagt "geht nicht":
     * erst die Gruende zeigen, dann schneiden.
     */
    fun schneiden() {
        val gruende = model.sliceBlockers
        if (gruende.isEmpty()) {
            panel = SimplePanel.WORKSPACE
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
    }

    /**
     * Vorschau oeffnen - und nur dann rechnen, wenn es sein muss. Der
     * Kern weiss, ob sein Ergebnis noch zur Szene passt. Passt es,
     * kostet das Hinsehen nichts; sonst wird geschnitten, und die
     * Vorschau geht danach von selbst auf.
     */
    fun vorschauZeigen() {
        if (vorschau) { vorschau = false; return }
        if (model.sliceResultIsCurrent || model.lastSliceWasRemote) {
            vorschauUmschalten()
            return
        }
        val gruende = model.sliceBlockers
        if (gruende.isEmpty()) {
            panel = SimplePanel.WORKSPACE
            nachDemSchnittZeigen = true
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
    }

    fun vorschauUmschalten() {
        if (vorschau) {
            vorschauSchliessen()
            return
        }
        val snapshot = model.previewSnapshot()
        if (snapshot == null || snapshot.layerCount == 0) {
            vorschauSchliessen()
            return
        }
        // In der Vorschau gibt es keine Objekte zum Anfassen.
        model.select(null)
        finalPreview = snapshot
        previewRange = PreviewRange(layerCount = snapshot.layerCount)
        previewView = PsmViewport.PreviewView.FEATURE
        hiddenPreviewRoles = emptySet()
        hiddenPreviewExtruders = emptySet()
        vorschau = true
    }

    fun vorschauSchliessen() {
        vorschau = false
        finalPreview = null
        previewRange = PreviewRange(layerCount = 0)
    }
}

/**
 * Auf schmalen Geraeten ruecken Kopfzeile und Leiste zusammen - aus
 * der Skalierung abgeleitet.
 */
@Composable
private fun istKompakt(): Boolean = LocalPsScale.current.factor <= 0.8f

/** Die Endung einer gewaehlten Datei, klein geschrieben - `pathExtension` auf iOS. */
private fun dateiEndung(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: ""
    return name.substringAfterLast('.', "").lowercase()
}

// MARK: - Arbeitsbereich

@Composable
private fun arbeitsbereich(z: SimpleModeZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val core = model.core ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val snapshot = z.finalPreview
        val rollen = remember(snapshot, core) {
            snapshot?.let { s -> (0 until s.roleCount).mapNotNull { runCatching { core.previewRole(it)?.role }.getOrNull() } }
                ?: emptyList()
        }
        val extruder = remember(snapshot, core) {
            snapshot?.let { s -> (0 until s.extruderCount).mapNotNull { runCatching { core.previewExtruder(it)?.extruder }.getOrNull() } }
                ?: emptyList()
        }
        val selectedId = model.selectedId
        ViewportView(
            core = core,
            shaderDir = model.shaderDir,
            selectedId = selectedId ?: -1,
            // Oberhalb der Leiste mit Import/Zurueck unten links.
            wuerfelHoch = ps.pt(70),
            selectedIds = model.selectedIds.toList(),
            invalidateKey = model.sceneRevision,
            // Bei offenem Panel darf der Viewport die Beruehrung nicht
            // schlucken: ein Tippen daneben soll das Panel schliessen,
            // nicht die Kamera drehen.
            inputEnabled = z.panel == SimplePanel.WORKSPACE,
            gizmo = z.gizmo,
            viewportMode = if (z.vorschau) PsmViewport.Mode.PREVIEW else PsmViewport.Mode.EDITOR,
            // Mehrbett ist absichtlich ein Advanced-Werkzeug: Simple
            // arbeitet immer auf dem aktuellen Bett.
            multiBedRender = false,
            focusBedIndex = model.activeBedIndex,
            focusBedKey = model.focusBedKey,
            layerRange = if (z.vorschau && !z.previewRange.isEmpty) z.previewRange.lower..z.previewRange.upper else null,
            previewView = z.previewView,
            previewRoles = rollen,
            hiddenPreviewRoles = z.hiddenPreviewRoles,
            previewExtruders = extruder,
            hiddenPreviewExtruders = z.hiddenPreviewExtruders,
            resetViewKey = z.ansichtZuruecksetzen,
            onPreviewLoaded = { anzahl ->
                val s = z.finalPreview
                if (s == null || anzahl != s.layerCount || anzahl <= 0) {
                    z.vorschauSchliessen()
                }
            },
            onSelect = { model.select(if (it < 0) null else it) },
            onObjectChanged = { model.refresh() },
            // Nur solange das Werkzeug an ist. Sonst gehoert jede
            // Beruehrung der Kamera, und ein Tippen ist eine Auswahl.
            onSurfaceTap = if (z.aufFlaeche && selectedId != null) {
                { treffer: PsmViewport.SurfaceHit ->
                    val id = model.selectedId
                    if (id != null) {
                        model.layOnFace(
                            id,
                            instance = treffer.instanceIndex,
                            volume = treffer.volumeIndex,
                            facet = treffer.facetIndex,
                        )
                    }
                    // Das Werkzeug bleibt an. Aus geht es ueber
                    // denselben Knopf.
                }
            } else null,
            onBlockedInput = { z.panel = SimplePanel.WORKSPACE },
            onLayerVisualizationChanged = { grenzen ->
                z.schichthoehenDarstellung = grenzen?.let {
                    it.first.toDouble() to it.second.toDouble()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val grenzen = z.schichthoehenDarstellung
        if (!z.vorschau && grenzen != null) {
            Box(Modifier.padding(ps.pt(12))) {
                LayerProfileViewportLegend(minHeight = grenzen.first, maxHeight = grenzen.second)
            }
        }
    }
}

// MARK: - Kopfzeile

@Composable
private fun kopfzeile(
    z: SimpleModeZustand,
    model: SlicerModel,
    onHome: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    Row(
        Modifier
            .fillMaxWidth()
            .background(PrusaColors.background)
            .pointerInput(Unit) {}
            .padding(horizontal = ps.pt(if (kompakt) 16 else 20), vertical = ps.pt(if (kompakt) 6 else 14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .testTag("kopf.start")
                .size(ps.touch(44))
                .clickable { z.nachHauseGehen(onHome) },
            contentAlignment = Alignment.Center,
        ) {
            SfSymbol(
                "house",
                Modifier.size(ps.font(if (kompakt) 22 else 26).value.dp),
                tint = PrusaColors.textMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(8)), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(ps.pt(if (kompakt) 23 else 28))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.orange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "S",
                    fontSize = ps.font(if (kompakt) 13 else 16),
                    fontWeight = FontWeight.Bold,
                    color = PrusaColors.background,
                )
            }
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    SimpleModeState.visibleBrand(),
                    fontSize = ps.font(if (kompakt) 20 else 23),
                    color = PrusaColors.textPrimary,
                )
                val drucker = SimpleModeState.printerLabel(rawPreset = model.selectedPreset("printer") ?: "")
                if (drucker.isNotEmpty()) {
                    Text(drucker, fontSize = ps.font(11), color = PrusaColors.textMuted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .testTag("kopf.advanced")
                .clickable(onClick = onOpenAdvanced)
                .border(ps.pt(1), PrusaColors.orange, RoundedCornerShape(ps.pt(6)))
                .heightIn(min = ps.touch(44))
                .padding(horizontal = ps.pt(if (kompakt) 12 else 16)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                st("Advanced", "Advanced"),
                fontSize = ps.font(if (kompakt) 12 else 14),
                fontWeight = FontWeight.Medium,
                color = PrusaColors.orange,
            )
        }
    }
}

// MARK: - Werkzeugleiste

/**
 * Die vier Werkzeuge, die ein Panel oeffnen. Reihenfolge und
 * Beschriftung stammen aus `toolbarLabels()`; welches Werkzeug welches
 * Panel zeigt, gehoert zur Anordnung und steht hier.
 */
private val panelZiele: List<SimplePanel> =
    listOf(SimplePanel.PROJECTS, SimplePanel.PRINTER, SimplePanel.MATERIAL, SimplePanel.SETTINGS)

@Composable
private fun werkzeugleiste(
    z: SimpleModeZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    val labels = SimpleModeState.toolbarLabels()
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {}
            .padding(horizontal = ps.pt(12), vertical = ps.pt(if (kompakt) 3 else 6)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(3)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        panelZiele.forEachIndexed { index, ziel ->
            werkzeug(model, labels[index], gewaehlt = z.panel == ziel, breite = ps.pt(if (kompakt) 62 else 74)) {
                // Ein zweites Tippen auf dasselbe Werkzeug schliesst
                // das Panel wieder.
                z.panel = if (z.panel == ziel) SimplePanel.WORKSPACE else ziel
            }
        }
        Spacer(Modifier.weight(1f))
        // "Vorschau" zeigt die Werkzeugwege, "G-Code" schneidet und
        // gibt die Datei aus.
        werkzeug(model, labels[4], gewaehlt = z.vorschau, breite = ps.pt(if (kompakt) 62 else 74)) {
            z.vorschauZeigen()
        }
        werkzeug(model, labels[5], gewaehlt = true, breite = ps.pt(if (kompakt) 84 else 100)) {
            z.schneiden()
        }
        if (remoteSlicePluginAn) {
            fernSchnittUmschalter(model, onRemoteSettings)
        }
    }
}

/**
 * Lokal/entfernt umschalten, wie im Advanced Mode. Ohne eingerichteten
 * Server fuehrt das Tippen erst zur Einrichtung statt stumm auf einen
 * leeren Host umzuschalten.
 */
@Composable
private fun fernSchnittUmschalter(model: SlicerModel, onRemoteSettings: () -> Unit) {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    val an = model.remoteSliceEnabled
    val beschreibung = if (an) st("Remote slicing on", "Remote Slicing an") else st("Remote slicing off", "Remote Slicing aus")
    Box(
        Modifier
            .testTag("simple.fern.umschalten")
            .semantics { contentDescription = beschreibung }
            .size(ps.pt(if (kompakt) 40 else 46), ps.pt(if (kompakt) 46 else 54))
            .background(if (an) PrusaColors.orange else PrusaColors.panel)
            .border(1.dp, if (an) PrusaColors.orange else PrusaColors.divider, RoundedCornerShape(ps.pt(1)))
            .clickable {
                val host = model.service.remoteSliceHost
                if (host.isEmpty()) {
                    onRemoteSettings()
                } else {
                    model.remoteSliceEnabled = !model.remoteSliceEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SfSymbol(
            if (an) "cloud.fill" else "cloud",
            Modifier.size(ps.font(16).value.dp),
            tint = if (an) PrusaColors.background else PrusaColors.textPrimary,
        )
    }
}

@Composable
private fun werkzeug(
    model: SlicerModel,
    label: String,
    gewaehlt: Boolean,
    breite: Dp,
    aktiv: Boolean = true,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    Box(
        Modifier
            .testTag("simple.werkzeug.$label")
            .clickable(enabled = aktiv, onClick = aktion)
            .alpha(if (aktiv) 1f else 0.45f)
            .size(breite, ps.pt(if (kompakt) 46 else 54))
            .background(if (gewaehlt) PrusaColors.orange else PrusaColors.panel)
            .border(1.dp, if (gewaehlt) PrusaColors.orange else PrusaColors.divider, RoundedCornerShape(ps.pt(1))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            werkzeugBeschriftung(model, label),
            fontSize = ps.font(if (kompakt) 9 else 10),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (gewaehlt) PrusaColors.background else PrusaColors.textPrimary,
        )
    }
}

/** Die Glyphen stehen bewusst nicht in der gemeinsamen Liste: dort steht, welche Werkzeuge es gibt, nicht wie sie aussehen. */
private fun werkzeugBeschriftung(model: SlicerModel, label: String): String = when (label) {
    "Projects" -> "▣\n" + st("Projects", "Projekte")
    "Printer" -> "▤\n" + st("Printer", "Drucker")
    "Material" -> "◎\n" + st("Material", "Material")
    "Settings" -> "☷\n" + st("Settings", "Einstell.")
    "Preview" -> "▱\n" + st("Preview", "Vorschau")
    "G-Code" -> "➤  " + (if (model.remoteSliceEnabled) st("Slice on server", "Auf Server") else st("Slice", "Slicen"))
    else -> label
}

@Composable
private fun presetSwitchDialog(pending: SlicerModel.PendingPresetSwitch, model: SlicerModel) {
    ProfilWechselDialog(
        aenderungen = pending.aenderungen,
        grund = st(
            "Switching the profile would drop these changes:",
            "Beim Wechsel des Profils gingen diese Änderungen verloren:",
        ),
        onVerwerfen = { model.pendingPresetSwitchVerwerfenUndWechseln() },
        onNeuesProfil = { model.pendingPresetSwitchAlsNeuesProfilSichernUndWechseln(it) },
        onUeberschreiben = { model.pendingPresetSwitchUeberschreibenUndWechseln() },
        onInsProjekt = { model.pendingPresetSwitchInsProjektUebernehmen() },
        onAbbrechen = { model.pendingPresetSwitchAbbrechen() },
    )
}

// MARK: - Modell hinzufuegen

/**
 * Auf leerem Bett ein einzelner Knopf, sonst das Modelle-Blatt: solange
 * nichts da ist, gibt es nichts anzuordnen, zu klonen oder zu entfernen.
 */
@Composable
private fun modellKnopf(z: SimpleModeZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    // Im kurzen Fenster steht die Werkzeugspalte rechts daneben (58 + 10 pt).
    Box(
        Modifier.fillMaxSize().padding(ps.pt(12)).padding(end = if (z.kurz) ps.pt(68) else 0.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (model.objects.isEmpty()) {
            Box(
                Modifier
                    .testTag("simple.modell")
                    .height(ps.touch(56))
                    .clip(RoundedCornerShape(ps.pt(2)))
                    .background(PrusaColors.orange)
                    .clickable { z.oeffneImporter() }
                    .padding(horizontal = ps.pt(16)),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋ " + st("Add model", "Modell hinzufügen"), fontSize = ps.font(14), color = Color.White)
            }
        } else {
            SimpleModelSheetView(
                model = model,
                onPickFile = { z.oeffneImporter() },
                onArrange = { z.zeigeArrange = true },
            )
        }
    }
}

// MARK: - Werkzeuge am rechten Rand

/**
 * Verschieben, Drehen, Skalieren und die Ansicht zuruecksetzen. Rechts
 * und nicht unten, weil unten schon die Schrittleiste und das
 * Modelle-Blatt liegen.
 */
@Composable
private fun werkzeugspalte(z: SimpleModeZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    // Mittig, solange die Hoehe reicht; im kurzen Fenster oben unter der
    // Werkzeugleiste, das Modelle-Blatt weicht nach links aus (modellKnopf).
    Box(
        Modifier.fillMaxSize().padding(top = if (z.kurz) z.kopfHoehe + ps.pt(6) else 0.dp),
        contentAlignment = if (z.kurz) Alignment.TopEnd else Alignment.CenterEnd,
    ) {
        Column(
            Modifier.padding(end = ps.pt(10)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (model.selectedId != null && !z.vorschau) {
                werkzeugKnopf("↔", st("Move", "Verschieben"),
                    aktiv = z.gizmo == PsmViewport.Gizmo.MOVE, kennung = "werkzeug.verschieben") {
                    z.gizmo = PsmViewport.Gizmo.MOVE
                }
                werkzeugKnopf("⟳", st("Rotate", "Drehen"),
                    aktiv = z.gizmo == PsmViewport.Gizmo.ROTATE, kennung = "werkzeug.drehen") {
                    z.gizmo = PsmViewport.Gizmo.ROTATE
                }
                werkzeugKnopf("⤢", st("Scale", "Skalieren"),
                    aktiv = z.gizmo == PsmViewport.Gizmo.SCALE, kennung = "werkzeug.skalieren") {
                    z.gizmo = PsmViewport.Gizmo.SCALE
                }
            }
            werkzeugKnopf("⌂", st("View", "Ansicht"), aktiv = false, kennung = "werkzeug.ansicht") {
                z.ansichtZuruecksetzen += 1
            }
            if (model.gcodeURL != null) {
                werkzeugKnopf(
                    if (z.vorschau) "▣" else "▱",
                    if (z.vorschau) st("Bed", "Bett") else st("Preview", "Vorschau"),
                    aktiv = z.vorschau, kennung = "werkzeug.vorschau",
                ) {
                    z.vorschauUmschalten()
                }
            }
        }
    }
}

@Composable
private fun werkzeugKnopf(
    glyph: String,
    label: String,
    aktiv: Boolean,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (aktiv) PrusaColors.background else PrusaColors.textPrimary
    Column(
        Modifier
            .testTag(kennung)
            .size(ps.pt(58), ps.touch(48))
            .clip(RoundedCornerShape(ps.pt(3)))
            .background(if (aktiv) PrusaColors.orange else PrusaColors.panel.copy(alpha = 0.92f))
            .clickable(onClick = aktion),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = ps.font(16), color = farbe)
        Text(label, fontSize = ps.font(8), color = farbe, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - Zurueck und wiederholen

/** Unten links. Beide Knoepfe nennen, was sie tun wuerden - "Zurueck" allein sagt nicht, was verloren geht. */
@Composable
private fun schrittleiste(model: SlicerModel) {
    val ps = LocalPsScale.current
    Box(Modifier.fillMaxSize().padding(ps.pt(12)), contentAlignment = Alignment.BottomStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            schritt("↶", model.undoLabel,
                standard = st("Undo", "Rückgängig"),
                kennung = "simple.zurueckschritt") { model.undo() }
            schritt("↷", model.redoLabel,
                standard = st("Redo", "Wiederholen"),
                kennung = "simple.wiederholen") { model.redo() }
        }
    }
}

@Composable
private fun schritt(
    glyph: String,
    beschriftung: String,
    standard: String,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (beschriftung.isEmpty()) PrusaColors.textMuted.copy(alpha = 0.4f) else PrusaColors.textPrimary
    Column(
        Modifier
            .testTag(kennung)
            .size(ps.pt(76), ps.touch(48))
            .background(PrusaColors.panel.copy(alpha = 0.9f))
            .clickable(enabled = beschriftung.isNotEmpty(), onClick = aktion),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = ps.font(15), color = farbe)
        // Zwei Zeilen: die Beschriftungen kommen aus dem Kern
        // ("Objekte importiert") und passen sonst nicht in eine.
        Text(
            if (beschriftung.isEmpty()) standard else beschriftung,
            fontSize = ps.font(8),
            color = farbe,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Was beim Oeffnen eines Projekts anders lief als darin stand. */
// MARK: - Overlay

@Composable
private fun overlay(
    z: SimpleModeZustand,
    model: SlicerModel,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    // Der abgedunkelte Arbeitsbereich ist zugleich die grosse,
    // touchfreundliche Schliessflaeche. Die Abdunklung beginnt unter der
    // Werkzeugleiste, nicht darueber: sonst verschwindet die
    // Beschriftung des gewaehlten Werkzeugs im Schleier.
    Box(
        Modifier.fillMaxSize().padding(top = ps.pt(if (kompakt) 108 else 136)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(PrusaColors.background.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { z.panel = SimplePanel.WORKSPACE },
        )

        Column(
            Modifier
                .padding(horizontal = ps.pt(12))
                .widthIn(max = ps.pt(if (kompakt) 420 else 620))
                .fillMaxWidth()
                .clip(RoundedCornerShape(ps.pt(3)))
                .background(PrusaColors.panel)
                .pointerInput(Unit) {}
                .padding(ps.pt(16)),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                Modifier
                    .testTag("simple.zurueck")
                    .height(ps.touch(44))
                    .clickable { z.panel = SimpleModeState.backDestination(panel = z.panel) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("← " + st("Back", "Zurück"), fontSize = ps.font(14), color = PrusaColors.orange)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = panelHoehe())
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
            ) {
                inhalt(z, model, onOpenAdvanced, onOpenPrinterSetup, onAppSettings)
            }
        }
    }
}

/** Was unterhalb der Werkzeugleiste uebrig bleibt, abzueglich des Zurueck-Knopfes und der Raender. */
@Composable
private fun panelHoehe(): Dp {
    val ps = LocalPsScale.current
    val kompakt = istKompakt()
    val oben = ps.pt(if (kompakt) 108 else 136)
    val raender = ps.touch(44) + ps.pt(64)
    return maxOf(ps.pt(220), ps.windowSize.height - oben - raender)
}

@Composable
private fun inhalt(
    z: SimpleModeZustand,
    model: SlicerModel,
    onOpenAdvanced: () -> Unit,
    onOpenPrinterSetup: () -> Unit,
    onAppSettings: () -> Unit,
) {
    when (z.panel) {
        SimplePanel.PROJECTS -> projektePanel(z, model)
        SimplePanel.PRINTER -> druckerPanel(model, onOpenPrinterSetup)
        SimplePanel.MATERIAL -> materialPanel(z, model, onOpenAdvanced)
        SimplePanel.SETTINGS -> einstellungenPanel(z, model, onOpenAdvanced, onAppSettings)
        SimplePanel.SUPPORTS -> stuetzenPanel(model)
        SimplePanel.ADHESION -> haftungPanel(model)
        SimplePanel.PRINT_SETTINGS -> druckprofilPanel(model, onOpenAdvanced)
        else -> Unit
    }
}

@Composable
private fun titel(text: String) {
    val ps = LocalPsScale.current
    Text(text, fontSize = ps.font(20), color = PrusaColors.textPrimary)
}

@Composable
private fun hinweis(text: String) {
    val ps = LocalPsScale.current
    Text(
        text,
        fontSize = ps.font(12),
        color = PrusaColors.textMuted,
        modifier = Modifier.padding(top = ps.pt(4), bottom = ps.pt(10)),
    )
}

// MARK: - Projekte

@Composable
private fun projektePanel(z: SimpleModeZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val text = SimpleModeState.projectSummaryCopy()
    val drucker = model.selectedPreset("printer") ?: ""
    val material = model.selectedPreset("filament") ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8)), horizontalAlignment = Alignment.Start) {
        titel(st("PROJECTS", "PROJEKTE"))
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(12)), verticalAlignment = Alignment.CenterVertically) {
            panelAktion(st("New project", "Neues Projekt"), kennung = "projekt.neu") {
                model.newProject()
            }
            panelAktion(st("Open model", "Modell öffnen"), kennung = "projekt.modell") {
                z.zweck = Zweck.MODELL
                z.oeffneImporter()
            }
            panelAktion(st("Open project", "Projekt öffnen"), kennung = "projekt.oeffnen") {
                z.zweck = Zweck.PROJEKT
                z.oeffneImporter()
            }
        }
        // Sichern schreibt erst die Datei, dann geht sie ueber den
        // Teilen-Dialog weiter.
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(12)), verticalAlignment = Alignment.CenterVertically) {
            panelAktion(st("Save project", "Projekt sichern"), kennung = "projekt.sichern") {
                // Erst den Namen erfragen, wie im Advanced: ohne ihn hiess
                // jedes Projekt "PSMobile" und ueberschrieb das vorige.
                z.speichernName = model.proposedProjectName
                z.zeigeSpeichernName = true
            }
            model.projectURL?.let { url ->
                Box(
                    Modifier
                        .testTag("projekt.weitergeben")
                        .height(ps.touch(44))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(PrusaColors.orange)
                        .clickable { teilen(context, model, url) }
                        .padding(horizontal = ps.pt(14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Share", "Weitergeben"), fontSize = ps.font(13), color = Color.White)
                }
            }
        }
        zuletzt(z, model)
        projektZeile(
            titel = text.title,
            drucker = if (drucker.isEmpty()) text.noPrinter else EasyModeState.profileDisplayLabel(rawPreset = drucker),
            material = if (material.isEmpty()) st("No material selected", "Material nicht gewählt")
            else EasyModeState.profileDisplayLabel(rawPreset = material),
            detail = text.session,
        )
    }
}

/** Die gesicherten Projekte, neueste oben. Acht Zeilen reichen. */
@Composable
private fun zuletzt(z: SimpleModeZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val dateien = model.recentProjects()
    if (dateien.isNotEmpty()) {
        Text(
            st("Recent", "Zuletzt"),
            fontSize = ps.font(11),
            color = PrusaColors.textMuted,
            modifier = Modifier.padding(top = ps.pt(4)),
        )
        dateien.forEachIndexed { index, url ->
            Row(
                Modifier
                    .testTag("projekt.zuletzt.$index")
                    .fillMaxWidth()
                    .height(ps.touch(44))
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(PrusaColors.panelRaised)
                    .clickable {
                        z.zweck = Zweck.PROJEKT
                        // Als Projekt, nicht als Modellimport - siehe
                        // WorkflowStartView.swift.
                        model.loadProject(url)
                    }
                    .padding(horizontal = ps.pt(10)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▣", fontSize = ps.font(13), color = PrusaColors.orange)
                Text(
                    url.nameWithoutExtension,
                    fontSize = ps.font(13),
                    color = PrusaColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(datum(url), fontSize = ps.font(10), color = PrusaColors.textMuted)
            }
        }
    }
}

private fun datum(url: File): String {
    val wert = url.lastModified()
    if (wert <= 0L) return ""
    // In der Sprache der Oberflaeche, nicht des Systems: bis zum 16.09.2026
    // stand "9/15/26 8:03 PM" in der deutschen Projektliste (Emulator en-US).
    val sprache = java.util.Locale.forLanguageTag(Sprache.aktuell)
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, sprache).format(Date(wert))
}

@Composable
private fun panelAktion(label: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .testTag(kennung)
            .height(ps.touch(44))
            .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(3)))
            .clickable(onClick = aktion)
            .padding(horizontal = ps.pt(14)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = ps.font(13), color = PrusaColors.orange)
    }
}

@Composable
private fun projektZeile(titel: String, drucker: String, material: String, detail: String) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth().background(PrusaColors.panelRaised).padding(ps.pt(12)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(ps.pt(70)).background(PrusaColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("▧", fontSize = ps.font(30), color = PrusaColors.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(3)), horizontalAlignment = Alignment.Start) {
            Text(titel, fontSize = ps.font(15), color = PrusaColors.textPrimary)
            listOf("▤  $drucker", "◎  $material", "□  $detail").forEach { zeile ->
                Text(zeile, fontSize = ps.font(11), color = PrusaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Drucker

@Composable
private fun druckerPanel(model: SlicerModel, onOpenPrinterSetup: () -> Unit) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10)), horizontalAlignment = Alignment.Start) {
        titel(st("PRINTER", "DRUCKER"))
        hinweis(st("Choose a printer model · select the nozzle in the slice summary",
            "Druckermodell wählen · die Düse wird in der Slice-Übersicht festgelegt"))
        DruckerAuswahlView(model = model, onSetup = onOpenPrinterSetup, onWahl = {
            model.selectPreset(PsmCore.PresetType.PRINTER, it)
        })
    }
}

@Suppress("unused")
@Composable
private fun druckerKarte(modell: String, duese: String, gewaehlt: Boolean, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (gewaehlt) PrusaColors.panelRaised else PrusaColors.background)
            .border(1.dp, if (gewaehlt) PrusaColors.orange else PrusaColors.divider, RoundedCornerShape(ps.pt(4)))
            .clickable(onClick = aktion)
            .padding(ps.pt(12)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(5)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ps.pt(52), ps.pt(60)).background(PrusaColors.panel), contentAlignment = Alignment.Center) {
                Text("▤", fontSize = ps.font(26), color = PrusaColors.textMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
                Text(modell, fontSize = ps.font(14), color = PrusaColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (gewaehlt) st("SELECTED · OFFLINE", "AUSGEWÄHLT · OFFLINE") else "OFFLINE",
                    fontSize = ps.font(10),
                    color = if (gewaehlt) PrusaColors.orange else PrusaColors.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = PrusaColors.divider)
        Text(st("Nozzle", "Düse") + "  " + duese, fontSize = ps.font(12), color = PrusaColors.textPrimary)
    }
}

// MARK: - Material

@Composable
private fun materialPanel(z: SimpleModeZustand, model: SlicerModel, onOpenAdvanced: () -> Unit) {
    val ps = LocalPsScale.current
    val gewaehlt = model.selectedPreset("filament") ?: ""
    // Nur die ersten Profile, nicht alle 189 auf einmal - die
    // vollstaendige Auswahl mit Hersteller und Farbe ist der Advanced Mode.
    val filamente = model.presetNames(PsmCore.PresetType.FILAMENT).take(30)
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8)), horizontalAlignment = Alignment.Start) {
        titel(st("MATERIAL", "MATERIAL"))
        hinweis(st("The material for this print.", "Das Material für diesen Druck."))
        // Mit mehreren Extrudern ist "das Material" keine Frage mehr,
        // sondern eine je Position.
        if (model.extruderCount > 1) {
            extruderListe(model, filamente)
            Button(
                onClick = { z.zeigeColorMix = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = ps.touch(44)).testTag("simple.colormix"),
                shape = RoundedCornerShape(ps.pt(6)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.orange.copy(alpha = 0.18f),
                    contentColor = PrusaColors.orange,
                ),
            ) {
                SfSymbol("circle.lefthalf.filled", Modifier.size(ps.font(14).value.dp), tint = PrusaColors.orange)
                Spacer(Modifier.width(ps.pt(8)))
                Text(st("ColorMix", "ColorMix"))
            }
        }
        if (filamente.isEmpty()) {
            leeresPanel(st("No material available", "Kein Material vorhanden"),
                st("Open Advanced Mode", "Advanced Mode öffnen"),
                onOpenAdvanced)
        } else {
            MaterialAuswahlView(model = model, gewaehlt = gewaehlt, onWahl = {
                model.selectPreset(PsmCore.PresetType.FILAMENT, it)
            })
        }
    }
}

/** Ein Eintrag je Extruder: Farbe, gewaehltes Material, Auswahl. */
@Composable
private fun extruderListe(model: SlicerModel, filamente: List<String>) {
    val ps = LocalPsScale.current
    Column(
        Modifier.padding(bottom = ps.pt(8)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Per extruder", "Je Extruder").uppercase(),
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        for (index in 0 until model.extruderCount) {
            var offen by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().testTag("extruder.$index"),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Die Farbe ist die einzige Auskunft, die man auf einen
                // Blick braucht: welcher Strang liegt auf welcher Position.
                Box(
                    Modifier
                        .size(ps.pt(26))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(colorFromHex(model.extruderColor(index)) ?: PrusaColors.panelRaised)
                        .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(3))),
                )
                Text("${index + 1}", fontSize = ps.font(12), color = PrusaColors.textMuted)
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .testTag("extruder.material.$index")
                            .fillMaxWidth()
                            .height(ps.touch(44))
                            .clip(RoundedCornerShape(ps.pt(3)))
                            .background(PrusaColors.panelRaised)
                            .clickable { offen = true }
                            .padding(horizontal = ps.pt(10)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            EasyModeState.profileDisplayLabel(rawPreset = model.extruderFilament(index)),
                            fontSize = ps.font(13),
                            color = PrusaColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        SfSymbol("chevron.up.chevron.down", Modifier.size(ps.font(10).value.dp), tint = PrusaColors.textMuted)
                    }
                    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                        ScaledOverlay {
                            filamente.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(EasyModeState.profileDisplayLabel(rawPreset = name)) },
                                    onClick = {
                                        model.setExtruderFilament(index, name)
                                        offen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Einstellen

@Composable
private fun einstellungenPanel(
    z: SimpleModeZustand,
    model: SlicerModel,
    onOpenAdvanced: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    val stuetzenAn = (model.config("support_material") ?: "0") == "1"
    // Lesbar statt roh: bis zum 16.09.2026 stand "Überall · organic" auf der
    // Karte - der Katalogwert, waehrend die Wahl darunter "Organisch" heisst.
    val stil = when (model.config("support_material_style") ?: "") {
        "", "snug" -> st("Snug", "Anliegend")
        "organic" -> st("Organic", "Organisch")
        "grid" -> st("Grid", "Gitter")
        else -> model.config("support_material_style") ?: ""
    }
    val brim = model.config("brim_width") ?: "0"
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(10)), horizontalAlignment = Alignment.Start) {
        titel(st("SETTINGS", "EINSTELLEN"))
        hinweis(st("The most important choices for this print.",
            "Die wichtigsten Entscheidungen für diesen Druck."))
        einstellKarte(
            z,
            titel = st("Supports", "Stützen"),
            detail = if (stuetzenAn) {
                (if ((model.config("support_material_buildplate_only") ?: "0") == "1")
                    st("Build plate only", "Nur Druckbett")
                else st("Everywhere", "Überall")) +
                    " · " + stil
            } else st("No supports", "Keine Stützen"),
            zeichen = "⌂", an = stuetzenAn, ziel = SimplePanel.SUPPORTS,
        )
        einstellKarte(
            z,
            titel = st("Adhesion", "Haftung"),
            detail = if (brim == "0") st("No additional bed adhesion", "Keine zusätzliche Haftung")
            else st("Outline around the model", "Rand um das Modell"),
            zeichen = "▱", an = brim != "0", ziel = SimplePanel.ADHESION,
        )
        einstellKarte(
            z,
            // Uebersetzt wie die Karten daneben - vorher blieb nur diese englisch.
            titel = st("Print Settings", "Druckeinstellungen"),
            detail = st("Quality, infill and shell thickness", "Qualität, Infill und Wandstärke"),
            zeichen = "☷", an = true, ziel = SimplePanel.PRINT_SETTINGS,
        )

        textKnopf(st("Open Advanced Mode", "Advanced Mode öffnen"),
            kennung = "simple.advanced", aktion = onOpenAdvanced)
        // Programm statt Werkstueck: Sprache, Startmodus, Vorschau.
        textKnopf(st("App settings", "App-Einstellungen"),
            kennung = "simple.appeinstellungen", aktion = onAppSettings)
    }
}

@Composable
private fun einstellKarte(
    z: SimpleModeZustand,
    titel: String,
    detail: String,
    zeichen: String,
    an: Boolean,
    ziel: SimplePanel,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .testTag("simple.karte.${ziel.name}")
            .fillMaxWidth()
            .background(PrusaColors.panelRaised)
            .border(1.dp, if (an) PrusaColors.orange.copy(alpha = 0.65f) else PrusaColors.divider, RoundedCornerShape(ps.pt(4)))
            .clickable { z.panel = ziel }
            .padding(ps.pt(14)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ps.pt(46)).background(PrusaColors.panel), contentAlignment = Alignment.Center) {
                Text(zeichen, fontSize = ps.font(24), color = if (an) PrusaColors.orange else PrusaColors.textMuted)
            }
            Text(titel, fontSize = ps.font(14), color = PrusaColors.textPrimary)
            Spacer(Modifier.weight(1f))
        }
        Text(detail, fontSize = ps.font(12), color = PrusaColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(st("Open", "Öffnen") + "  ›", fontSize = ps.font(13), color = PrusaColors.orange)
    }
}

// MARK: - Stuetzen

@Composable
private fun stuetzenPanel(model: SlicerModel) {
    val ps = LocalPsScale.current
    val gewaehlt = SimpleModeState.selectedSupportChoice(
        supports = model.config("support_material") ?: "0",
        automatic = model.config("support_material_auto") ?: "0",
        buildPlateOnly = model.config("support_material_buildplate_only") ?: "0",
        style = model.config("support_material_style") ?: "",
    )
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
        titel(st("SUPPORTS", "STÜTZEN"))
        stuetzenWahl(model, SimpleSupportChoice.DISABLED, st("Disabled", "Aus"),
            st("No supports", "Keine Stützen"), gewaehlt)
        gruppe(st("Everywhere", "Überall"))
        stuetzenWahl(model, SimpleSupportChoice.SNUG_EVERYWHERE, st("Snug", "Anliegend"),
            st("Straight supports close to the model.", "Gerade Stützen dicht am Modell."), gewaehlt)
        stuetzenWahl(model, SimpleSupportChoice.ORGANIC_EVERYWHERE, st("Organic", "Organisch"),
            st("Tree-shaped supports, easy to remove.", "Baumförmige Stützen, leicht zu entfernen."), gewaehlt)
        gruppe(st("Build plate only", "Nur vom Druckbett"))
        stuetzenWahl(model, SimpleSupportChoice.SNUG_BUILD_PLATE, st("Snug", "Anliegend"),
            st("Build plate only", "Nur vom Druckbett"), gewaehlt)
        stuetzenWahl(model, SimpleSupportChoice.ORGANIC_BUILD_PLATE, st("Organic", "Organisch"),
            st("Build plate only", "Nur vom Druckbett"), gewaehlt)
    }
}

@Composable
private fun gruppe(text: String) {
    val ps = LocalPsScale.current
    Text(text, fontSize = ps.font(15), color = PrusaColors.textPrimary, modifier = Modifier.padding(top = ps.pt(10)))
}

@Composable
private fun stuetzenWahl(
    model: SlicerModel,
    wahl: SimpleSupportChoice,
    titel: String,
    detail: String,
    gewaehlt: SimpleSupportChoice,
) {
    referenzWahl(
        titel = titel,
        detail = detail,
        gewaehlt = wahl == gewaehlt,
        modifier = Modifier.testTag("simple.stuetzen.${wahl.name}"),
    ) {
        // Welche Parameter zu welcher Wahl gehoeren, entscheidet das
        // gemeinsame Modul.
        for ((schluessel, wert) in SimpleModeState.supportConfig(choice = wahl)) {
            model.setConfig(schluessel, wert)
        }
    }
}

// MARK: - Haftung

@Composable
private fun haftungPanel(model: SlicerModel) {
    val ps = LocalPsScale.current
    // "Automatisch entscheiden" ist bewusst kein dritter Zustand,
    // sondern eine Entscheidungshilfe.
    val rat = AdhesionAdvice.advise(objects = model.footprints)
    val brim = model.config("brim_width") ?: "0"
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
        titel(st("INCREASE ADHESION", "HAFTUNG VERBESSERN"))
        referenzWahl(
            titel = st("Decide automatically", "Automatisch entscheiden"),
            detail = AdhesionAdvice.explain(advice = rat),
            gewaehlt = false,
            modifier = Modifier.testTag("simple.haftung.automatisch"),
        ) {
            model.setConfig("brim_width", rat.brimWidthMm.toString())
        }
        referenzWahl(
            titel = st("Disabled", "Aus"),
            detail = st("No additional bed adhesion", "Keine zusätzliche Haftung"),
            gewaehlt = brim == "0",
        ) {
            model.setConfig("brim_width", "0")
        }
        referenzWahl(
            titel = st("Outline around the model", "Rand um das Modell"),
            detail = st("A brim helps hold edges down while printing.",
                "Ein Rand hält die Kanten während des Drucks unten."),
            gewaehlt = brim != "0",
        ) {
            model.setConfig("brim_width", AdhesionAdvice.SUGGESTED_BRIM_MM.toString())
        }
    }
}

// MARK: - Druckeinstellungen

@Composable
private fun druckprofilPanel(model: SlicerModel, onOpenAdvanced: () -> Unit) {
    val ps = LocalPsScale.current
    val gewaehlt = model.selectedPreset("print") ?: ""
    val profile = model.presetNames(PsmCore.PresetType.PRINT).take(10)
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8)), horizontalAlignment = Alignment.Start) {
        titel(st("PRINT SETTINGS", "DRUCKEINSTELLUNGEN"))

        // Die drei Bereiche der Referenz, mit den Werten dahinter -
        // gezeichnet mit demselben SettingField wie die Parameter der
        // Einstellungsseiten.
        schnellBereich(model, st("Print Settings", "Druckeinstellungen"),
            schluessel = listOf("layer_height"))
        schnellBereich(model, st("Infill", "Füllung"),
            schluessel = listOf("fill_density", "fill_pattern"))
        schnellBereich(model, st("Shell Thickness", "Wandstärke"),
            schluessel = listOf("perimeters", "top_solid_layers", "bottom_solid_layers"))

        Text(
            st("Profiles", "Profile").uppercase(),
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
            modifier = Modifier.padding(top = ps.pt(10)),
        )
        if (profile.isEmpty()) {
            leeresPanel(st("No print settings available", "Keine Druckeinstellungen vorhanden"),
                st("Set up print settings", "Druckeinstellungen einrichten"),
                onOpenAdvanced)
        }
        profile.forEach { name ->
            referenzWahl(
                titel = EasyModeState.profileDisplayLabel(rawPreset = name),
                detail = name,
                gewaehlt = name == gewaehlt,
            ) {
                model.selectPreset(PsmCore.PresetType.PRINT, name)
            }
        }
    }
}

@Composable
private fun schnellBereich(model: SlicerModel, titelText: String, schluessel: List<String>) {
    val ps = LocalPsScale.current
    Column(
        Modifier.padding(top = ps.pt(10)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            titelText.uppercase(),
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        schluessel.forEach { key ->
            Box(Modifier.testTag("schnell.$key")) {
                SettingField(
                    model = model,
                    option = TabsCatalog.Option(key = key, code = false, line = null),
                    kompakt = false,
                )
            }
        }
    }
}

/** Die drei Spaltenkoepfe stehen in der gemeinsamen Liste bewusst auf Englisch; die Uebersetzung gehoert hierher. */
@Suppress("unused")
private fun spaltenNameDeutsch(english: String): String = when (english) {
    "Print Settings" -> "Druckeinstellungen"
    "Infill" -> "Füllung"
    "Shell Thickness" -> "Wandstärke"
    else -> english
}

// MARK: - Bausteine

@Composable
private fun referenzWahl(
    titel: String,
    detail: String,
    gewaehlt: Boolean,
    modifier: Modifier = Modifier,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        modifier
            .padding(top = ps.pt(10))
            .fillMaxWidth()
            .heightIn(min = ps.touch(48))
            .background(if (gewaehlt) PrusaColors.panelRaised else PrusaColors.background)
            .border(1.dp, if (gewaehlt) PrusaColors.orange else PrusaColors.divider, RoundedCornerShape(ps.pt(2)))
            .clickable(onClick = aktion)
            .padding(ps.pt(12)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(titel, fontSize = ps.font(14), color = PrusaColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty() && detail != titel) {
                Text(
                    detail,
                    fontSize = ps.font(11),
                    color = PrusaColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
        if (gewaehlt) {
            SfSymbol("checkmark", Modifier.size(ps.font(12).value.dp), tint = PrusaColors.orange)
        }
    }
}

@Composable
private fun leeresPanel(nachricht: String, aktion: String, handlung: () -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth().background(PrusaColors.panelRaised).padding(ps.pt(18)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(nachricht, fontSize = ps.font(13), color = PrusaColors.textMuted)
        Box(
            Modifier
                .height(ps.touch(44))
                .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(3)))
                .clickable(onClick = handlung)
                .padding(horizontal = ps.pt(14)),
            contentAlignment = Alignment.Center,
        ) {
            Text(aktion, fontSize = ps.font(13), color = PrusaColors.orange)
        }
    }
}

@Composable
private fun textKnopf(label: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .testTag(kennung)
            .fillMaxWidth()
            .heightIn(min = ps.touch(48))
            .clickable(onClick = aktion),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = ps.font(13), color = PrusaColors.textMuted)
    }
}
