package de.psmobile.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.shared.rules.AppSettings
import de.psmobile.shared.rules.EasyModeState
import de.psmobile.shared.rules.PreviewRange
import de.psmobile.shared.rules.SliceSummary
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Der Arbeitsbereich im Advanced Mode - Gegenstueck zu
 * `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`.
 *
 * Aufbau: das Bett fuellt die Flaeche, die Werkzeuge liegen oben, und
 * rechts steht der Inspektor - der Objektbaum, wenn etwas ausgewaehlt
 * ist, sonst die Liste. Auf schmalen Geraeten klappt die Seite weg,
 * sonst bliebe vom Bett nichts uebrig.
 */
@Composable
fun AdvancedWorkspaceView(
    onHome: () -> Unit = {},
    onOpenSimple: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onPrinters: () -> Unit = {},
    onSendToPrinter: (File) -> Unit = {},
    onPrinterSetup: () -> Unit = {},
    onSettings: (String) -> Unit = {},
    onRemoteSettings: () -> Unit = {},
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val z = remember(model) { AdvancedZustand(model) }

    // @AppStorage(AppSettings.KEY_MULTI_BED_RENDER)
    val multiBedRenderGespeichert = rememberAppSetting(AppSettings.KEY_MULTI_BED_RENDER, true)
    val remoteSlicePluginAn = rememberAppSetting(AppSettings.KEY_PLUGIN_REMOTE_SLICE, true)
    val leisteUntenExperimentell = rememberAppSetting(AppSettings.KEY_PORTRAIT_BOTTOM_BAR, false)

    // Ein iPad bekommt nie die schmale, ueberlagernde Behandlung.
    val schmal = ps.windowSize.width < ps.pt(760)
    val leisteUnten = leisteUntenExperimentell && ps.windowSize.height > ps.windowSize.width
    val seitenleistenbreite: Dp = minOf(ps.pt(340), ps.windowSize.width * 0.42f)
    z.schmal = schmal
    z.leisteUnten = leisteUnten


    // .fileImporter(isPresented: $zeigeImporter, allowsMultipleSelection: true)
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        for (uri in uris) {
            // ZIPs sind in beiden Zwecken willkommen - eine ZIP ist nie
            // selbst ein Projekt, immer eine Modellquelle.
            if (dateiEndung(context, uri) == "zip") {
                model.loadZip(uri)
                continue
            }
            when (z.zweck) {
                AdvancedZweck.MODELL -> model.load(uri)
                AdvancedZweck.PROJEKT -> model.loadProject(uri)
            }
        }
    }
    // .fileImporter(isPresented: $zeigeReparatur)
    val reparaturWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) z.werkzeugErgebnis = model.repairSTL(uri)
    }
    // .fileImporter(isPresented: $zeigeWandeln)
    val wandelnWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Die Richtung sagt der Dateiname: wer eine .gcode waehlt, will
        // binaer; wer eine .bgcode waehlt, will Text.
        val istBinaer = dateiEndung(context, uri) == "bgcode"
        z.werkzeugErgebnis = model.convertGcode(uri, toBinary = !istBinaer)
    }
    z.oeffneImporter = { importer.launch(arrayOf("*/*")) }
    /*
     * Vor dem Verlassen fragen, wenn es etwas zu verlieren gibt - ein
     * zweites Tippen auf "Advanced" von der Startseite legt sonst
     * stillschweigend ein neues, leeres Projekt an.
     */
    z.nachHauseGehen = {
        if (model.hasUnsavedChanges) z.zeigeVerlassenNachfrage = true else onHome()
    }
    // Die Zurueck-Geste des Systems (nur Android): erst die ueberlagernde
    // Seite zu, dann die Vorschau, dann nach Hause - statt die App zu
    // beenden. Befund vom Galaxy S23 FE, 14.09.2026.
    BackHandler {
        when {
            z.seiteOffen && z.schmal -> z.seiteOffen = false
            z.vorschau -> z.vorschauUmschalten()
            else -> z.nachHauseGehen()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(PrusaColors.background))
        Row(Modifier.fillMaxSize()) {
            // Links die Werkzeuge am Objekt, wie in PrusaSlicers eigener
            // Leiste.
            WerkzeugSchiene(
                model = model,
                kopiert = z.kopiert,
                onKopiertChange = { z.kopiert = it },
                onEinfuegen = { z.zweck = AdvancedZweck.MODELL; z.oeffneImporter() },
                onSettings = { onSettings("print") },
                onArrange = { z.zeigeArrange = true },
                onMalwerkzeug = { z.malwerkzeugUmschalten(it) },
                onPrinters = onPrinters,
                onAppSettings = onAppSettings,
            )
            senkrechterTrenner()
            // Die Mitte bekommt den Rest zugeteilt, nicht zugestanden.
            // Genau deshalb tritt hier nicht auf, was auf iOS bis zum
            // 11.09.2026 auftrat: dort erfragte der HStack die
            // Eigenbreite der Mitte, die waagerechte ScrollView der
            // Werkzeugleiste meldete ihre volle Inhaltsbreite, und die
            // Seitenleiste wurde ueber den Fensterrand geschoben.
            // Drueben steht jetzt dieselbe Rechnung als `mittenbreite`.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                werkzeugleiste(z, model, onOpenSimple)
                if (schmal) {
                    // Der echte Selector liegt auf schmalen Fenstern
                    // oberhalb der ueberlagernden Seitenleiste.
                    Spacer(Modifier.fillMaxWidth().height(ps.touch(52)))
                } else {
                    BedSelector(
                        model = model,
                        onArrange = { z.zeigeArrange = true },
                        onOpenSelection = { z.zeigeBettwahl = true },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    arbeitsflaeche(z, model, multiBedRenderGespeichert)
                }
                ansichtsleiste(z, model)
            }
            if (z.seiteOffen && !schmal && !leisteUnten) {
                senkrechterTrenner()
                // Hoechstens zwei Fuenftel der Breite: darunter bleibt vom
                // Bett nichts uebrig, und darum geht es hier.
                Box(Modifier.width(seitenleistenbreite).fillMaxHeight()) {
                    seitenleiste(z, model, remoteSlicePluginAn, onSendToPrinter, onSettings, onRemoteSettings)
                }
            }
        }
        if (z.seiteOffen && schmal && !leisteUnten) {
            schmaleSeite(z, model, remoteSlicePluginAn, onSendToPrinter, onSettings, onRemoteSettings)
        }
        if (z.seiteOffen && leisteUnten) {
            unteneSeite(z, model, remoteSlicePluginAn, onSendToPrinter, onSettings, onRemoteSettings)
        }
        if (schmal) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.padding(start = ps.pt(74), top = z.werkzeugleisteHoehe)) {
                    BedSelector(
                        model = model,
                        onArrange = { z.zeigeArrange = true },
                        onOpenSelection = { z.zeigeBettwahl = true },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        if (z.hinderungsgruende.isNotEmpty()) {
            SliceBlockerSheet(gruende = z.hinderungsgruende) { z.hinderungsgruende = emptyList() }
        }
        if (z.zeigeBettwahl) {
            BedSelectionOverlay(
                model = model,
                isPresented = z.zeigeBettwahl,
                onIsPresentedChange = { z.zeigeBettwahl = it },
            )
        }
        // Die Einstellungen schweben ueber der Platte statt sie zu
        // ersetzen: mit einem Rand ringsherum sieht man, dass es weiter
        // um dieses Projekt geht.
        val tab = z.einstellungenTab
        if (tab != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { z.einstellungenTab = null },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(ps.pt(if (schmal) 10 else 28))
                    .clip(RoundedCornerShape(ps.pt(10)))
                    .background(PrusaColors.background)
                    .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(10))),
            ) {
                SettingsView(
                    model = model,
                    startTab = tab,
                    onClose = { z.einstellungenTab = null },
                )
            }
        }
        model.pendingPresetSwitch?.let { pending -> presetSwitchDialog(pending, model) }
        PSMarke(name = "arbeitsbereich")
    }

    // Ohne ausgewaehltes Objekt gibt es nichts zu bemalen.
    LaunchedEffect(model.selectedId) {
        if (model.selectedId == null) z.maloptionen = z.maloptionen.copy(tool = null)
    }
    // Was sich auf dem Bett aendert, macht eine ausgegebene Platte hinfaellig.
    LaunchedEffect(model.sceneRevision) {
        z.platte = null
        if (z.vorschau && model.previewSnapshot() == null) {
            z.vorschauSchliessen()
        }
    }
    // Wer auf "Vorschau" tippt und dafuer warten musste, will danach die
    // Wege sehen - nicht die Zusammenfassung.
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

    if (z.zeigeDrucker) {
        auswahlblatt(
            titel = PsUiCatalog.tr("Printer"),
            onFertig = {
                z.zeigeDrucker = false
                z.zeigeMaterial = false
                z.materialZiel = null
            },
        ) {
            DruckerAuswahlView(
                model = model,
                onSetup = {
                    z.zeigeDrucker = false
                    onPrinterSetup()
                },
                onWahl = { model.selectPreset(PsmCore.PresetType.PRINTER, it) },
            )
        }
    }
    if (z.zeigeMaterial) {
        auswahlblatt(
            titel = PsUiCatalog.tr("Filament"),
            onFertig = {
                z.zeigeDrucker = false
                z.zeigeMaterial = false
                z.materialZiel = null
            },
        ) {
            MaterialAuswahlView(
                model = model,
                gewaehlt = z.materialZiel?.let { model.extruderFilament(it) }
                    ?: model.selectedPreset("filament") ?: "",
                onWahl = { name ->
                    val index = z.materialZiel
                    if (index != null) {
                        model.setExtruderFilament(index, name)
                    } else {
                        model.selectPreset(PsmCore.PresetType.FILAMENT, name)
                    }
                },
            )
        }
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
    if (z.zeigeGcodeMarken) {
        Dialog(
            onDismissRequest = { z.zeigeGcodeMarken = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize().background(PrusaColors.background)) {
                    CustomGcodeView(model = model) { z.zeigeGcodeMarken = false }
                }
            }
        }
    }
    if (z.zeigeReparatur) {
        LaunchedEffect(Unit) {
            z.zeigeReparatur = false
            reparaturWaehler.launch(arrayOf("*/*"))
        }
    }
    if (z.zeigeWandeln) {
        LaunchedEffect(Unit) {
            z.zeigeWandeln = false
            wandelnWaehler.launch(arrayOf("*/*"))
        }
    }
    if (z.zeigeAlleProjekte) {
        Dialog(
            onDismissRequest = { z.zeigeAlleProjekte = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize().background(PrusaColors.background)) {
                    AllProjectsSheet(
                        onOpen = { url ->
                            z.zeigeAlleProjekte = false
                            model.loadProject(url)
                        },
                        onClose = { z.zeigeAlleProjekte = false },
                        model = model,
                    )
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
                Text(
                    st(
                        "A second tap on Advanced would discard this project.",
                        "Ein erneutes Tippen auf Advanced würde dieses Projekt verwerfen.",
                    ),
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { z.zeigeVerlassenNachfrage = false },
                    modifier = Modifier.testTag("verlassen.abbrechen"),
                ) {
                    Text(st("Cancel", "Abbrechen"))
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            z.zeigeVerlassenNachfrage = false
                            onHome()
                        },
                        modifier = Modifier.testTag("verlassen.verwerfen"),
                    ) { Text(st("Discard", "Verwerfen"), color = PrusaColors.danger) }
                    TextButton(
                        onClick = {
                            z.zeigeVerlassenNachfrage = false
                            z.speichernName = model.proposedProjectName
                            model.saveProject(name = z.speichernName)
                            onHome()
                        },
                        modifier = Modifier.testTag("verlassen.sichern"),
                    ) { Text(st("Save", "Sichern")) }
                }
            },
        )
    }
}

// MARK: - Zustand

/** Wofuer der Dateiwaehler gerade offen ist. */
private enum class AdvancedZweck { MODELL, PROJEKT }

/** Die vier Abschnitte des Inspektors. */
private enum class InspektorReiter { PROFILE, OBJEKTE, BEARBEITEN, WERKZEUGE }

/** Die `@State`-Werte des Swift-Structs, gebuendelt fuer die Teil-Composables. */
private class AdvancedZustand(val model: SlicerModel) {
    var hinderungsgruende by mutableStateOf<List<String>>(emptyList())
    var gizmo by mutableStateOf(PsmViewport.Gizmo.MOVE)
    var seiteOffen by mutableStateOf(true)
    /** Gemessene Hoehe der Werkzeugleiste - im schmalen Fenster bricht sie um. */
    var werkzeugleisteHoehe by mutableStateOf(52.dp)
    /** Layoutlage, damit die Arbeitsflaeche den Ansichtswuerfel neben die Seite legen kann. */
    var schmal by mutableStateOf(false)
    var leisteUnten by mutableStateOf(false)
    var zeigeDrucker by mutableStateOf(false)
    var zeigeMaterial by mutableStateOf(false)

    /**
     * Welcher Kopf die Materialauswahl geoeffnet hat - null heisst die
     * allgemeine Filamentzeile.
     */
    var materialZiel by mutableStateOf<Int?>(null)
    var zweck by mutableStateOf(AdvancedZweck.MODELL)

    /** Die Zwischenablage der Schiene. */
    var kopiert by mutableStateOf<Int?>(null)

    /** Die zuletzt ausgegebene Platte, solange sie noch weitergegeben werden kann. */
    var platte by mutableStateOf<File?>(null)

    /** Das Ergebnis des zuletzt benutzten Dateiwerkzeugs. */
    var werkzeugErgebnis by mutableStateOf<File?>(null)
    var zeigeReparatur by mutableStateOf(false)
    var zeigeWandeln by mutableStateOf(false)
    var zeigeGcodeMarken by mutableStateOf(false)
    var zeigeArrange by mutableStateOf(false)
    var zeigeBettwahl by mutableStateOf(false)
    var reiter by mutableStateOf(InspektorReiter.PROFILE)

    /** Welche Bereiche der Seitenleiste offen sind. */
    var offeneBereiche by mutableStateOf(setOf("profile"))

    /** Solange gesetzt, wartet die Seitenleiste auf die echten Rahmen. */
    var seitenleistenZiel by mutableStateOf<Int?>(null)
    var seitenleistenFokusSchritt by mutableIntStateOf(0)
    var seitenleistenRahmen by mutableStateOf<Rect?>(null)
    var bearbeitenRahmen by mutableStateOf<Rect?>(null)
    /** Wahr, bis die Seitenleiste zum Werkzeuge-Bereich geblaettert hat. */
    var werkzeugeZiel by mutableStateOf(false)

    /** Rahmen der Objektzeilen - Gegenstueck zu `.id(...)` + `scrollTo`. */
    val objektRahmen = mutableStateMapOf<String, Rect>()

    /** Welche Einstellungsseite als schwebendes Fenster offen ist. */
    var einstellungenTab by mutableStateOf<String?>(null)
    var objektSuche by mutableStateOf("")

    var ansicht by mutableStateOf<PsmViewport.View?>(null)
    var ansichtZaehler by mutableIntStateOf(0)
    var vorschau by mutableStateOf(false)

    /** Ist das Flaechenwerkzeug an, gehoert die Beruehrung der Flaeche. */
    var aufFlaeche by mutableStateOf(false)
    var finalPreview by mutableStateOf<PsmCore.PreviewSnapshot?>(null)
    var previewRange by mutableStateOf(PreviewRange(layerCount = 0))

    /** Grenzen des Werkzeugweg-Bereichs der aktuell sichtbaren Schicht(en). */
    var moveRangeGrenzen by mutableStateOf<IntRange?>(null)
    var moveRangeUnten by mutableIntStateOf(0)
    var moveRangeOben by mutableIntStateOf(0)
    var previewView by mutableStateOf(PsmViewport.PreviewView.FEATURE)
    var hiddenPreviewRoles by mutableStateOf<Set<Int>>(emptySet())
    var hiddenPreviewExtruders by mutableStateOf<Set<Int>>(emptySet())
    var schichthoehenDarstellung by mutableStateOf<Pair<Float, Float>?>(null)

    /** Ob nach dem laufenden Schnitt die Vorschau aufgehen soll. */
    var nachDemSchnittZeigen by mutableStateOf(false)

    var zeigeAlleProjekte by mutableStateOf(false)
    var zeigeSpeichernName by mutableStateOf(false)
    var speichernName by mutableStateOf("")
    var zeigeVerlassenNachfrage by mutableStateOf(false)
    var ansichtZuruecksetzen by mutableIntStateOf(0)

    /** Dieselben Optionen steuern Bedienung, Kern und Viewport. */
    var maloptionen by mutableStateOf(PsmCore.PaintOptions())

    var oeffneImporter: () -> Unit = {}
    var nachHauseGehen: () -> Unit = {}

    /**
     * Einen Pinsel an- oder ausschalten. Dasselbe Werkzeug noch einmal
     * antippen heisst aus - und ein Pinsel schliesst das Flaechenwerkzeug
     * aus: beide wollen dieselbe Beruehrung.
     */
    fun malwerkzeugUmschalten(werkzeug: PsmCore.PaintTool) {
        if (maloptionen.tool == werkzeug) {
            maloptionen = maloptionen.copy(tool = null)
        } else {
            maloptionen = maloptionen.copy(tool = werkzeug, state = 1).normalizedForTool()
            aufFlaeche = false
            reiter = InspektorReiter.WERKZEUGE
            // Seit dem Akkordeon oeffnet der Reiter allein nichts mehr: bis
            // zum 16.09.2026 ging die Seite auf, aber "Werkzeuge" mit Pinsel,
            // Zustand und Zaehler blieb zugeklappt (S23 FE, Bug-Bounty).
            offeneBereiche = offeneBereiche + kennung(InspektorReiter.WERKZEUGE)
            werkzeugeZiel = true
            seiteOffen = true
        }
    }

    /** Vorschau oeffnen - und nur dann rechnen, wenn es sein muss. */
    fun vorschauZeigen() {
        if (vorschau) { vorschauSchliessen(); return }
        if (model.sliceResultIsCurrent || model.lastSliceWasRemote) {
            vorschauUmschalten()
            return
        }
        val gruende = model.sliceBlockers
        if (gruende.isEmpty()) {
            nachDemSchnittZeigen = true
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
    }

    fun vorschauUmschalten() {
        val snapshot = model.previewSnapshot()
        if (snapshot == null || snapshot.layerCount == 0) {
            vorschauSchliessen()
            return
        }
        // In der Vorschau gibt es keine Objekte zum Anfassen, und kein
        // Werkzeug, das auf sie zeigt.
        model.select(null)
        maloptionen = maloptionen.copy(tool = null)
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
        moveRangeGrenzen = null
    }

    fun schneiden() {
        val gruende = model.sliceBlockers
        if (gruende.isEmpty()) model.slice() else hinderungsgruende = gruende
    }

    /** Dieselben Hinderungsgruende wie beim einzelnen Schnitt. */
    fun alleBettenSchneiden() {
        val gruende = model.sliceAllBlockers
        if (gruende.isEmpty()) model.sliceAll() else hinderungsgruende = gruende
    }
}

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

private fun kennung(r: InspektorReiter): String = when (r) {
    InspektorReiter.PROFILE -> "profile"
    InspektorReiter.OBJEKTE -> "objekte"
    InspektorReiter.BEARBEITEN -> "bearbeiten"
    InspektorReiter.WERKZEUGE -> "werkzeuge"
}

private fun scrollKennungFuerObjekt(id: Int): String = "advanced.objekt.scroll.$id"
private fun bereichKennung(r: InspektorReiter): String = "advanced.bereich." + kennung(r)

// MARK: - Auswahlblatt

/**
 * Der Rahmen um eine Auswahl: Titel, Inhalt, Fertig.
 *
 * Das Blatt schliesst sich nicht beim Waehlen - man will vergleichen und
 * mehrfach umstellen. Geschlossen wird ausdruecklich.
 */
@Composable
private fun auswahlblatt(
    titel: String,
    onFertig: () -> Unit,
    inhalt: @Composable () -> Unit,
) {
    val ps = LocalPsScale.current
    Dialog(
        onDismissRequest = onFertig,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ScaledOverlay {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(PrusaColors.background)
                    .padding(ps.pt(16)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(12)),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titel.uppercase(),
                        fontSize = ps.font(15),
                        fontWeight = FontWeight.SemiBold,
                        color = PrusaColors.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onFertig,
                        modifier = Modifier
                            .defaultMinSize(minHeight = ps.touch(44))
                            .testTag("auswahl.fertig"),
                    ) {
                        Text(st("Done", "Fertig"), color = PrusaColors.orange)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { inhalt() }
            }
        }
    }
}

// MARK: - Werkzeuge

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun werkzeugleiste(z: AdvancedZustand, model: SlicerModel, onOpenSimple: () -> Unit) {
    val ps = LocalPsScale.current
    val dichte = LocalDensity.current
    // Umbrechen statt scrollen: auf dem Telefon war die Leiste abgeschnitten
    // und musste gewischt werden (Nils, 14.09.2026). Die gemessene Hoehe
    // wandert in den Zustand - Bettkarte und Seite richten sich danach.
    // Gegenstueck: FlowLayout in AdvancedWorkspaceView.swift.
    Box(
        Modifier
            .fillMaxWidth()
            .background(PrusaColors.panel)
            .onGloballyPositioned { z.werkzeugleisteHoehe = with(dichte) { it.size.height.toDp() } },
    ) {
        FlowRow(
            Modifier.padding(horizontal = ps.pt(8), vertical = ps.pt(4)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
        ) {
            werkzeug("house", st("Start", "Start"), "kopf.start") { z.nachHauseGehen() }
            trenner()
            werkzeug("doc", st("New", "Neu"), "projekt.neu") { model.newProject() }
            werkzeug("folder", st("Open", "Öffnen"), "projekt.oeffnen") {
                z.zweck = AdvancedZweck.PROJEKT
                z.oeffneImporter()
            }
            werkzeug("clock", st("Projects", "Projekte"), "projekt.alle") {
                z.zeigeAlleProjekte = true
            }
            werkzeug("square.and.arrow.down", st("Save", "Sichern"), "projekt.sichern") {
                z.speichernName = model.proposedProjectName
                z.zeigeSpeichernName = true
            }
            werkzeug(
                if (z.vorschau) "cube.fill" else "square.stack.3d.up",
                if (z.vorschau) st("Bed", "Bett") else st("Preview", "Vorschau"),
                "werkzeug.vorschau",
            ) { z.vorschauZeigen() }
            trenner()
            werkzeug("square.righthalf.filled", "Simple", "simple.oeffnen", onOpenSimple)
            Spacer(Modifier.width(ps.pt(0)))
            werkzeug(
                if (z.seiteOffen) "sidebar.right" else "sidebar.left",
                st("Panel", "Leiste"),
                "advanced.seite",
            ) { z.seiteOffen = !z.seiteOffen }
        }
    }
}

/**
 * Feste Blickrichtungen. Mit dem Finger eine saubere Draufsicht zu drehen
 * ist Gluecksache - und genau die braucht man beim Anordnen am haeufigsten.
 */
@Composable
private fun ansichtsleiste(z: AdvancedZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    Box(Modifier.fillMaxWidth().background(PrusaColors.background)) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ps.pt(8), vertical = ps.pt(3)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(3)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zurueck und Vor stehen am Anfang der unteren Leiste: sie sind
            // das, was man am haeufigsten braucht.
            zurueckKnopf(
                st("Undo", "Zurück"), "arrow.uturn.backward",
                moeglich = model.undoLabel.isNotEmpty(),
                kennung = "advanced.zurueck",
            ) { model.undo() }
            zurueckKnopf(
                st("Redo", "Vor"), "arrow.uturn.forward",
                moeglich = model.redoLabel.isNotEmpty(),
                kennung = "advanced.wiederholen",
            ) { model.redo() }
            Box(
                Modifier
                    .padding(horizontal = ps.pt(4))
                    .width(1.dp)
                    .height(ps.pt(24))
                    .background(PrusaColors.divider),
            )
            // Nicht "Iso": der Name ist in der CAD-Welt richtig und sonst
            // nirgends.
            // Nur noch "3D" (zurueck zur Schraegansicht): die festen
            // Richtungen liegen seit dem 14.09.2026 auf dem Ansichtswuerfel
            // oben rechts im Viewport (ui/Ansichtswuerfel.kt).
            blickwinkel(z, st("3D", "3D"), PsmViewport.View.ISO)
        }
    }
}

/** Ein Knopf der unteren Leiste, ausgegraut wenn es nichts zu tun gibt. */
@Composable
private fun zurueckKnopf(
    label: String,
    symbol: String,
    moeglich: Boolean,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (moeglich) PrusaColors.textPrimary else PrusaColors.textMuted.copy(alpha = 0.4f)
    Row(
        Modifier
            .height(ps.touch(40))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .clickable(enabled = moeglich, onClick = aktion)
            .padding(horizontal = ps.pt(10))
            .testTag(kennung),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(14).value.dp), tint = farbe)
        Text(label, fontSize = ps.font(11), color = farbe)
    }
}

@Composable
private fun blickwinkel(z: AdvancedZustand, label: String, preset: PsmViewport.View) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .heightIn(min = ps.touch(38))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .clickable {
                z.ansicht = preset
                z.ansichtZaehler += 1
            }
            .padding(horizontal = ps.pt(12))
            .testTag("ansicht.$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = ps.font(12), color = PrusaColors.textPrimary)
    }
}

@Composable
private fun trenner() {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .padding(horizontal = ps.pt(4))
            .width(1.dp)
            .height(ps.pt(30))
            .background(PrusaColors.divider),
    )
}

/** Ein senkrechter Strich zwischen den Spalten - `Divider()` im HStack. */
@Composable
private fun senkrechterTrenner() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(PrusaColors.divider))
}

@Composable
private fun werkzeug(
    symbol: String,
    label: String,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .size(ps.pt(60), ps.touch(46))
            .clickable(onClick = aktion)
            .testTag(kennung),
        verticalArrangement = Arrangement.spacedBy(ps.pt(2), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(16).value.dp), tint = PrusaColors.textPrimary)
        Text(
            label,
            fontSize = ps.font(8),
            color = PrusaColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Bett

@Composable
private fun arbeitsflaeche(
    z: AdvancedZustand,
    model: SlicerModel,
    multiBedRenderGespeichert: Boolean,
) {
    val ps = LocalPsScale.current
    val core = model.core
    if (core == null) {
        Box(Modifier.fillMaxSize().background(PrusaColors.background))
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val snapshot = z.finalPreview
        val rollen = remember(snapshot, core) {
            snapshot?.let { s ->
                (0 until s.roleCount).mapNotNull {
                    runCatching { core.previewRole(it)?.role }.getOrNull()
                }
            } ?: emptyList()
        }
        val extruder = remember(snapshot, core) {
            snapshot?.let { s ->
                (0 until s.extruderCount).mapNotNull {
                    runCatching { core.previewExtruder(it)?.extruder }.getOrNull()
                }
            } ?: emptyList()
        }
        ViewportView(
            core = core,
            shaderDir = model.shaderDir,
            selectedId = model.selectedId ?: -1,
            selectedIds = model.selectedIds.toList(),
            invalidateKey = model.sceneRevision,
            modifier = Modifier.fillMaxSize().testTag("viewport"),
            gizmo = if (z.maloptionen.tool == null) z.gizmo else PsmViewport.Gizmo.NONE,
            paintOptions = if (z.maloptionen.tool == null) null else z.maloptionen,
            viewportMode = if (z.vorschau) PsmViewport.Mode.PREVIEW else PsmViewport.Mode.EDITOR,
            multiBedRender = multiBedRenderGespeichert,
            focusBedIndex = model.activeBedIndex,
            focusBedKey = model.focusBedKey,
            bedNamen = if (multiBedRenderGespeichert) {
                model.beds.sortedBy { it.index }.map { model.bedLabel(it.index) }
            } else {
                emptyList()
            },
            layerRange = if (z.vorschau && !z.previewRange.isEmpty) {
                z.previewRange.lower..z.previewRange.upper
            } else {
                null
            },
            moveRange = if (z.vorschau && z.moveRangeGrenzen != null) {
                z.moveRangeUnten..z.moveRangeOben
            } else {
                null
            },
            previewView = z.previewView,
            previewRoles = rollen,
            hiddenPreviewRoles = z.hiddenPreviewRoles,
            previewExtruders = extruder,
            hiddenPreviewExtruders = z.hiddenPreviewExtruders,
            resetViewKey = z.ansichtZuruecksetzen,
            viewPreset = z.ansicht,
            viewPresetKey = z.ansichtZaehler,
            onPreviewLoaded = { anzahl ->
                val s = z.finalPreview
                if (s == null || anzahl != s.layerCount || anzahl <= 0) {
                    z.vorschauSchliessen()
                }
            },
            onMoveRangeBounds = { grenzen ->
                if (grenzen == null) {
                    z.moveRangeGrenzen = null
                } else if (z.moveRangeGrenzen != grenzen) {
                    // Nur bei tatsaechlich neuen Grenzen zuruecksetzen -
                    // sonst ueberschreibt jeder Bildaufbau eine laufende
                    // Ziehgeste.
                    z.moveRangeGrenzen = grenzen
                    z.moveRangeUnten = grenzen.first
                    z.moveRangeOben = grenzen.last
                }
            },
            onSelect = { model.select(if (it < 0) null else it) },
            onObjectChanged = { model.refresh() },
            // Zwei Werkzeuge teilen sich dieselbe Beruehrung: der Pinsel
            // und das Hinlegen auf eine Flaeche.
            onSurfaceTap = if (z.aufFlaeche) {
                { treffer: PsmViewport.SurfaceHit ->
                    model.layOnFace(
                        treffer.objectId,
                        instance = treffer.instanceIndex,
                        volume = treffer.volumeIndex,
                        facet = treffer.facetIndex,
                    )
                }
            } else {
                null
            },
            onSurfaceStroke = if (z.maloptionen.tool == null) {
                null
            } else {
                { treffer: PsmViewport.SurfaceHit, vorher: PsmViewport.SurfaceHit? ->
                    // Eine Capsule verbindet nur Treffer desselben
                    // Instanz-Volumens. Beim Sprung auf eine andere Kopie
                    // oder ein anderes Volumen beginnt ein neuer Tupfer.
                    val vorigePosition = vorher?.let { alt ->
                        if (alt.objectId == treffer.objectId &&
                            alt.instanceIndex == treffer.instanceIndex &&
                            alt.volumeIndex == treffer.volumeIndex
                        ) {
                            Triple(alt.x, alt.y, alt.z)
                        } else {
                            null
                        }
                    }
                    model.paint(
                        treffer.objectId,
                        instance = treffer.instanceIndex,
                        volume = treffer.volumeIndex,
                        facet = treffer.facetIndex,
                        hit = Triple(treffer.x, treffer.y, treffer.z),
                        previous = vorigePosition,
                        options = z.maloptionen,
                    )
                }
            },
            onLayerVisualizationChanged = { z.schichthoehenDarstellung = it },
        )

        val grenzen = z.schichthoehenDarstellung
        if (!z.vorschau && grenzen != null) {
            LayerProfileViewportLegend(
                minHeight = grenzen.first.toDouble(),
                maxHeight = grenzen.second.toDouble(),
                modifier = Modifier.padding(ps.pt(12)),
            )
        }

        // Der Projekt-Hinweis gehoert auch hierher: bis zum 16.09.2026 lief
        // projectNotice im Advanced ins Leere (Profile eines fremden Projekts,
        // Sichern fehlgeschlagen). 100 pt Abstand: ueber dem Ansichtswuerfel.
        model.projectNotice?.let { hinweis -> ProjektHinweis(hinweis, model, abstandUnten = ps.pt(100)) }

        if (z.vorschau && z.previewRange.layerCount > 1) {
            // Linker Rand: Schichtbereich, senkrecht - wie der
            // Desktop-Regler links vom Bett.
            Row(Modifier.fillMaxSize()) {
                DualHandleSlider(
                    untererWert = z.previewRange.lower,
                    onUntererWertChange = { z.previewRange = z.previewRange.withLower(it) },
                    obererWert = z.previewRange.upper,
                    onObererWertChange = { z.previewRange = z.previewRange.withUpper(it) },
                    bereich = 0..maxOf(z.previewRange.layerCount - 1, 1),
                    achse = DualHandleSlider.Achse.senkrecht,
                    // Unten 98 statt 48: seit der Ansichtswuerfel (84 pt + 6 pt)
                    // unten links liegt, sass der untere Griff auf seiner
                    // "Front"-Flaeche (S23 FE, 16.09.2026). Zwilling: AdvancedWorkspaceView.swift.
                    modifier = Modifier
                        .padding(start = ps.pt(8))
                        .padding(top = ps.pt(48), bottom = ps.pt(98)),
                )
                Spacer(Modifier.weight(1f))
            }
        }
        val moveGrenzen = z.moveRangeGrenzen
        if (z.vorschau && moveGrenzen != null && moveGrenzen.first < moveGrenzen.last) {
            // Unterer Rand: Werkzeugweg innerhalb der Schicht, waagerecht.
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                DualHandleSlider(
                    untererWert = z.moveRangeUnten,
                    onUntererWertChange = { z.moveRangeUnten = it },
                    obererWert = z.moveRangeOben,
                    onObererWertChange = { z.moveRangeOben = it },
                    bereich = moveGrenzen,
                    achse = DualHandleSlider.Achse.waagerecht,
                    // Links 98: rechts neben dem Ansichtswuerfel beginnen.
                    modifier = Modifier
                        .padding(start = ps.pt(98), end = ps.pt(56))
                        .padding(bottom = ps.pt(14)),
                )
            }
        }

        // Hochformat mit offener Seite: nur das Bett abdunkeln, ein Tipp
        // darauf schliesst die Seite. Schiene, Werkzeugleiste und untere
        // Leiste bleiben bedienbar - vorher lag die Schliessflaeche ueber
        // allem, und der erste Tipp auf "Drucker" oder "Zurueck" schloss
        // nur die Seite (Galaxy S23 FE, 14.09.2026).
        // Gilt auch fuer die untere Leiste: deren eigene Schliessflaeche lag
        // ueber Werkzeugleiste und Bettkarte (S23 FE, 16.09.2026).
        if (z.seiteOffen && (z.schmal || z.leisteUnten)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PrusaColors.background.copy(alpha = 0.6f))
                    .clickable { z.seiteOffen = false },
            )
        }

        // Dieselbe schwebende Leiste wie im Einfachen Modus: was man am
        // ausgewaehlten Objekt am haeufigsten tut, gehoert an das Objekt
        // und nicht in eine Spalte am Rand.
        val id = model.selectedId
        val objekt = model.objects.firstOrNull { it.id == id }
        if (id != null && objekt != null && !z.vorschau) {
            // Im Hochformat liegt die Seite ueber dem rechten Rand: die
            // Leiste weicht ihr aus, sonst sind die Griffe unerreichbar
            // (Galaxy S23 FE, 14.09.2026).
            val seiteRechts = if (z.schmal && z.seiteOffen && !z.leisteUnten) ps.pt(320) else 0.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(top = ps.pt(10), end = seiteRechts)) {
                    Spacer(Modifier.weight(1f))
                    SimpleObjectBarView(
                        model = model,
                        objekt = objekt,
                        zeigtZurueck = false,
                        onClearSelection = { model.select(null) },
                        onFlaechenwahl = { z.aufFlaeche = it },
                        gizmo = z.gizmo,
                        onGizmoChange = { z.gizmo = it },
                        // Kein eigener Deckel: hier klemmt die
                        // uebergeordnete Breite, und was nicht
                        // hineinpasst, scrollt (horizontalScroll in
                        // SimpleObjectBarView).
                        //
                        // Drueben ragt die Leiste in die Seitenleiste -
                        // nach vier Anlaeufen ein offener Befund, siehe
                        // die Stelle in AdvancedWorkspaceView.swift. Die
                        // Zahlen sagen: ihr Behaelter reicht dort unter
                        // die Seitenleiste. Hier nicht - der Zwilling von
                        // ResponsiveLayoutUITests laeuft durch.
                        maxBreite = null,
                    )
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Was jede Rolle gekostet hat. Nur bei mehr als einem Extruder: bei einem
 * einfarbigen Druck steht dieselbe Zahl zwei Zeilen darüber.
 */
@Composable
private fun verbrauchJeExtruder(model: SlicerModel) {
    val ps = LocalPsScale.current
    val verbrauch = model.extruderUsage()
    if (verbrauch.size <= 1) return
    HorizontalDivider(Modifier.padding(vertical = ps.pt(2)), color = PrusaColors.divider)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            st("Per tool", "Je Werkzeug"),
            fontSize = ps.font(10),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            st("Model", "Modell") + " · " + st("Tower", "Turm"),
            fontSize = ps.font(9),
            color = PrusaColors.textMuted,
        )
    }
    Column(Modifier.testTag("seite.verbrauch")) {
        verbrauch.forEach { u ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ps.pt(10), ps.pt(10))
                        .clip(RoundedCornerShape(ps.pt(2)))
                        .background(
                            colorFromHex(model.extruderColor(u.extruder)) ?: PrusaColors.panelRaised,
                        )
                        .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(2))),
                )
                Text("T${u.extruder + 1}", fontSize = ps.font(11), color = PrusaColors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.1f", u.volumeMm3 / 1000.0) + " cm³",
                    fontSize = ps.font(11),
                    color = PrusaColors.textPrimary,
                )
                if (u.wipeTowerMm3 + u.flushMm3 > 0) {
                    Text(
                        "+ " + String.format(
                            Locale.US, "%.1f", (u.wipeTowerMm3 + u.flushMm3) / 1000.0,
                        ),
                        fontSize = ps.font(10),
                        color = PrusaColors.orange,
                    )
                }
            }
        }
    }
}

// MARK: - Seitenleiste

@Composable
private fun seitenleiste(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onSendToPrinter: (File) -> Unit,
    onSettings: (String) -> Unit,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(PrusaColors.background)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { z.seitenleistenRahmen = it.boundsInWindow() }
                // Die Leiste traegt einen Namen, damit ein Test zu einem
                // Bereich blaettern kann - siehe die gleichlautende
                // Stelle in AdvancedWorkspaceView.swift.
                .testTag("seitenleiste")
                .verticalScroll(scrollState)
                .padding(ps.pt(12)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
            horizontalAlignment = Alignment.Start,
        ) {
            // Die drei Einstellungsseiten stehen oben im Band: sie wirken
            // auf das Profil, und das Profil steht rechts.
            einstellungsbereiche(z)
            HorizontalDivider(color = PrusaColors.divider)
            // Untereinander statt hinter Reitern: vier Reiter heissen, dass
            // drei Viertel des Gesuchten unsichtbar sind.
            InspektorReiter.entries.forEach { r ->
                bereich(z, model, r, onSettings)
            }
        }
        // Ausserhalb der Reiter: was ein Schnitt ergeben hat, ist keine
        // Frage des gerade offenen Reiters.
        abschluss(z, model, remoteSlicePluginAn, onSendToPrinter, onRemoteSettings)
    }

    /*
     * Scrollt nur, wenn der obere Aktionsblock nicht vollstaendig in der
     * gemessenen Seitenleistenflaeche liegt. Zuerst bleibt die gewaehlte
     * Objektzeile am oberen Rand. Reicht das bei einer langen Liste nicht,
     * folgt genau eine Nachkorrektur zum gemessenen Aktionsblock.
     */
    // Der Werkzeuge-Bereich liegt am Ende der Leiste: wer einen Pinsel
    // waehlt, soll ihn sehen und nicht erst suchen. Gemessen wird der
    // Bereichskopf; solange er nicht im Baum ist (Telefon, Leiste zu),
    // wartet der Effekt auf die Messung.
    LaunchedEffect(z.werkzeugeZiel, z.objektRahmen[bereichKennung(InspektorReiter.WERKZEUGE)], z.seitenleistenRahmen) {
        if (!z.werkzeugeZiel) return@LaunchedEffect
        // Eine leere Messung zaehlt nicht.
        val kopf = z.objektRahmen[bereichKennung(InspektorReiter.WERKZEUGE)]
            ?.takeIf { it.height > 0f } ?: return@LaunchedEffect
        val sl = z.seitenleistenRahmen?.takeIf { it.height > 0f } ?: return@LaunchedEffect
        z.werkzeugeZiel = false
        scope.launch {
            scrollState.animateScrollTo(
                (scrollState.value + (kopf.top - sl.top)).toInt().coerceAtLeast(0),
            )
        }
    }

    LaunchedEffect(z.seitenleistenZiel, z.seitenleistenRahmen, z.bearbeitenRahmen) {
        val id = z.seitenleistenZiel ?: return@LaunchedEffect
        val sl = z.seitenleistenRahmen ?: return@LaunchedEffect
        val be = z.bearbeitenRahmen ?: return@LaunchedEffect
        val sichtbar = be.top >= sl.top && be.bottom <= sl.bottom
        if (sichtbar) {
            z.seitenleistenZiel = null
            z.seitenleistenFokusSchritt = 0
        } else if (z.seitenleistenFokusSchritt == 0) {
            z.seitenleistenFokusSchritt = 1
            val zeile = z.objektRahmen[scrollKennungFuerObjekt(id)] ?: return@LaunchedEffect
            scope.launch {
                scrollState.scrollTo(
                    (scrollState.value + (zeile.top - sl.top)).toInt().coerceAtLeast(0),
                )
            }
        } else {
            // Dieser zweite und letzte Sprung ist absichtlich begrenzt.
            scope.launch {
                scrollState.scrollTo(
                    (scrollState.value + (be.top - sl.top)).toInt().coerceAtLeast(0),
                )
            }
            z.seitenleistenZiel = null
            z.seitenleistenFokusSchritt = 0
        }
    }
}

/**
 * Statistik und Legende der laufenden Vorschau - an derselben Stelle, an
 * der vorher die schwebende "Final G-code"-Karte lag.
 */
@Composable
private fun ColumnScope.vorschauInhalt(z: AdvancedZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val snapshot = z.finalPreview
    if (!z.vorschau || snapshot == null) return
    Column(
        Modifier.padding(bottom = ps.pt(4)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(9)),
        horizontalAlignment = Alignment.Start,
    ) {
        PSMarke(name = "vorschau.panel")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                st("G-code preview", "G-Code-Vorschau"),
                fontSize = ps.font(13),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { z.vorschauSchliessen() },
                modifier = Modifier.testTag("vorschau.editor"),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfSymbol("cube", Modifier.size(ps.font(11).value.dp), tint = PrusaColors.orange)
                    Text(
                        st("Editor", "Editor"),
                        fontSize = ps.font(11),
                        fontWeight = FontWeight.SemiBold,
                        color = PrusaColors.orange,
                    )
                }
            }
        }
        PreviewStatsRow(range = z.previewRange, snapshot = snapshot, model = model)
        PreviewLegendPicker(
            snapshot = snapshot,
            view = z.previewView,
            onViewChange = { z.previewView = it },
            hiddenRoles = z.hiddenPreviewRoles,
            onHiddenRolesChange = { z.hiddenPreviewRoles = it },
            hiddenExtruders = z.hiddenPreviewExtruders,
            onHiddenExtrudersChange = { z.hiddenPreviewExtruders = it },
            model = model,
        )
    }
}

/**
 * Ersetzt den Knopfbereich je nach Stand des letzten Schnitts: nach einem
 * gueltigen Ergebnis steht an genau der Stelle, an der vorher "Slice now"
 * war, direkt Export und Senden.
 */
@Composable
private fun ColumnScope.schneidenBereich(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onSendToPrinter: (File) -> Unit,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val fortschritt = model.progress
    when {
        fortschritt is SlicerModel.Progress.Running -> {
            Column(
                Modifier.testTag("slicen"),
                verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${fortschritt.percent} %",
                        fontSize = ps.font(13),
                        fontWeight = FontWeight.SemiBold,
                        color = PrusaColors.textPrimary,
                    )
                    Text(
                        fortschritt.stage,
                        fontSize = ps.font(11),
                        color = PrusaColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                }
                LinearProgressIndicator(
                    progress = { fortschritt.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrusaColors.orange,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ps.touch(40))
                        .clickable { model.cancel() }
                        .testTag("slice.abbrechen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        st("Cancel", "Abbrechen"),
                        fontSize = ps.font(13),
                        color = PrusaColors.textMuted,
                    )
                }
            }
        }

        fortschritt is SlicerModel.Progress.Done &&
            (model.sliceResultIsCurrent || model.lastSliceWasRemote) -> {
            Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8))) {
                val url = model.gcodeURL
                if (model.gcodeURLs.size > 1) {
                    Box(
                        Modifier
                            .clickable { teilen(context, model, model.gcodeURLs) }
                            .testTag("slice.sichern"),
                    ) {
                        exportKnopf(st("Export all", "Alle exportieren"))
                    }
                } else if (url != null) {
                    Box(
                        Modifier
                            .clickable { teilen(context, model, url) }
                            .testTag("slice.sichern"),
                    ) {
                        exportKnopf(st("Export G-Code", "G-Code exportieren"))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ps.touch(40))
                            .clickable { onSendToPrinter(url) }
                            .testTag("slice.andrucker"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            st("Send to printer", "An Drucker senden"),
                            fontSize = ps.font(13),
                            fontWeight = FontWeight.Medium,
                            color = PrusaColors.orange,
                        )
                    }
                }
            }
        }

        // Drueben `case .failed(let meldung, _)` seit Progress.Failed.laden - hier aendert das nichts.
        fortschritt is SlicerModel.Progress.Failed -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(fortschritt.message, fontSize = ps.font(12), color = PrusaColors.danger)
                schneidenKnopf(z, model, remoteSlicePluginAn, onRemoteSettings)
            }
        }

        else -> schneidenKnopf(z, model, remoteSlicePluginAn, onRemoteSettings)
    }
}

@Composable
private fun exportKnopf(text: String) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(ps.touch(52))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.orange),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = ps.font(15), fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun schneidenKnopf(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(ps.touch(52))
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.orange)
                .clickable { z.schneiden() }
                .testTag("slicen"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (model.remoteSliceEnabled) {
                    st("Slice on server", "Auf Server slicen")
                } else if (model.beds.size > 1) {
                    st("Slice current bed", "Aktuelles Bett slicen")
                } else {
                    PsUiCatalog.tr("Slice now")
                },
                fontSize = ps.font(15),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (remoteSlicePluginAn) {
            fernSchnittUmschalter(model, onRemoteSettings)
        }
    }
}

/**
 * Lokal/entfernt umschalten. Ohne eingerichteten Server fuehrt das Tippen
 * erst zur Einrichtung statt stumm auf einen leeren Host umzuschalten.
 */
@Composable
private fun fernSchnittUmschalter(model: SlicerModel, onRemoteSettings: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .size(ps.touch(52), ps.touch(52))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .clickable {
                val host = model.service.remoteSliceHost
                if (host.isEmpty()) {
                    onRemoteSettings()
                } else {
                    model.remoteSliceEnabled = !model.remoteSliceEnabled
                }
            }
            .testTag("slicen.fern.umschalten"),
        contentAlignment = Alignment.Center,
    ) {
        SfSymbol(
            if (model.remoteSliceEnabled) "cloud.fill" else "cloud",
            Modifier.size(ps.font(18).value.dp),
            tint = if (model.remoteSliceEnabled) PrusaColors.orange else PrusaColors.textMuted,
            contentDescription = if (model.remoteSliceEnabled) {
                st("Remote slicing on", "Remote Slicing an")
            } else {
                st("Remote slicing off", "Remote Slicing aus")
            },
        )
    }
}

/**
 * Das untere Ende der Seitenleiste: was der letzte Schnitt ergeben hat,
 * und der Knopf für den nächsten.
 */
@Composable
private fun abschluss(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onSendToPrinter: (File) -> Unit,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth().background(PrusaColors.panel).padding(ps.pt(12)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        HorizontalDivider(color = PrusaColors.divider)
        val s = model.stats
        if (s != null && (model.sliceResultIsCurrent || model.lastSliceWasRemote)) {
            val zeilen = SliceSummary.rows(
                seconds = s.printTimeSeconds,
                grams = s.filamentGrams,
                millimetres = s.filamentMm,
                cost = s.cost,
                objects = model.objects.size,
            )
            Column(
                Modifier.fillMaxWidth().testTag("seite.zusammenfassung"),
                verticalArrangement = Arrangement.spacedBy(ps.pt(3)),
            ) {
                zeilen.forEach { zeile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(zeile.label, fontSize = ps.font(11), color = PrusaColors.textMuted)
                        Spacer(Modifier.weight(1f))
                        Text(zeile.value, fontSize = ps.font(12), color = PrusaColors.textPrimary)
                    }
                }
            }
            verbrauchJeExtruder(model)
        } else {
            Text(
                st("Not sliced yet", "Noch nicht gesliced"),
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
            )
        }
        model.memoryWarning?.let { warnung ->
            Text(warnung, fontSize = ps.font(11), color = PrusaColors.orange)
        }
        vorschauInhalt(z, model)
        schneidenBereich(z, model, remoteSlicePluginAn, onSendToPrinter, onRemoteSettings)

        // Nur sinnvoll, wenn es ueberhaupt etwas zu verteilen gibt.
        if (model.beds.size > 1) {
            val laeuft = model.sliceAllProgress
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(40))
                    .clickable(enabled = laeuft == null) { z.alleBettenSchneiden() }
                    .testTag("slicen.alle"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (laeuft != null) {
                        st("Bed ${laeuft.first}/${laeuft.second}", "Bett ${laeuft.first}/${laeuft.second}")
                    } else {
                        st("Slice all beds", "Alle Betten slicen")
                    },
                    fontSize = ps.font(13),
                    fontWeight = FontWeight.Medium,
                    color = PrusaColors.orange,
                )
            }
        }
    }
}

/**
 * Ein aufklappbarer Bereich der Seitenleiste. Zu ist der Normalfall fuer
 * alles ausser den Profilen.
 */
@Composable
private fun ColumnScope.bereich(
    z: AdvancedZustand,
    model: SlicerModel,
    r: InspektorReiter,
    onSettings: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    val hatAuswahl = model.selectedId != null
    val moeglich = (r != InspektorReiter.BEARBEITEN && r != InspektorReiter.WERKZEUGE) || hatAuswahl
    val offen = z.offeneBereiche.contains(kennung(r)) && moeglich
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(40))
                .clickable(enabled = moeglich) {
                    z.offeneBereiche = if (z.offeneBereiche.contains(kennung(r))) {
                        z.offeneBereiche - kennung(r)
                    } else {
                        z.offeneBereiche + kennung(r)
                    }
                }
                .testTag("inspektor." + kennung(r))
                // positionInWindow statt boundsInWindow: Letzteres ist auf das
                // Fenster beschnitten und meldet fuer einen Kopf unterhalb des
                // Bildschirmrands (0,0,0,0) - genau den will man anfahren.
                .onGloballyPositioned {
                    z.objektRahmen[bereichKennung(r)] = Rect(it.positionInWindow(), it.size.toSize())
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisposableEffect(r) {
                onDispose { z.objektRahmen.remove(bereichKennung(r)) }
            }
            Text(
                reiterName(r).uppercase(),
                fontSize = ps.font(11),
                fontWeight = FontWeight.SemiBold,
                color = if (moeglich) {
                    PrusaColors.textMuted
                } else {
                    PrusaColors.textMuted.copy(alpha = 0.4f)
                },
            )
            Spacer(Modifier.weight(1f))
            SfSymbol(
                if (offen) "chevron.down" else "chevron.right",
                Modifier.size(ps.font(11).value.dp),
                tint = PrusaColors.textMuted,
            )
        }

        if (offen) {
            when (r) {
                InspektorReiter.PROFILE -> profilblock(z, model, onSettings)
                InspektorReiter.OBJEKTE -> objektliste(z, model)
                InspektorReiter.BEARBEITEN -> bearbeitenBlock(z, model)
                InspektorReiter.WERKZEUGE -> werkzeugeBlock(z, model)
            }
        }
    }
}

/** Die drei Einstellungsseiten, als Zeilen im Band. */
@Composable
private fun einstellungsbereiche(z: AdvancedZustand) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
    ) {
        einstellungsZeile(
            z, "list.bullet.rectangle", PsUiCatalog.tr("Print Settings"),
            "print", "advanced.printSettings",
        )
        einstellungsZeile(
            z, "circle.circle", PsUiCatalog.tr("Filament Settings"),
            "filament", "advanced.filamentSettings",
        )
        einstellungsZeile(
            // "Printer Settings" fehlt im deutschen PrusaSlicer-Katalog (lang_de.json)
            // - deshalb eigener Text statt tr() (Emulator auf Deutsch, 15.09.2026).
            z, "printer", st("Printer Settings", "Druckereinstellungen"),
            "printer", "advanced.printerSettings",
        )
    }
}

@Composable
private fun einstellungsZeile(
    z: AdvancedZustand,
    symbol: String,
    label: String,
    tab: String,
    kennung: String,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .clickable { z.einstellungenTab = tab }
            .padding(horizontal = ps.pt(10))
            .testTag(kennung),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ps.pt(22)), contentAlignment = Alignment.Center) {
            SfSymbol(symbol, Modifier.size(ps.font(14).value.dp), tint = PrusaColors.orange)
        }
        Text(
            label,
            fontSize = ps.font(13),
            color = PrusaColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text("›", fontSize = ps.font(15), color = PrusaColors.textMuted)
    }
}

/**
 * Profile │ Objekte │ Bearbeiten │ Werkzeuge. Die letzten beiden beziehen
 * sich auf ein Objekt und bleiben ohne Auswahl gesperrt.
 */
@Suppress("unused")
@Composable
private fun reiterleiste(z: AdvancedZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val hatAuswahl = model.selectedId != null
    Row(
        Modifier.fillMaxWidth().background(PrusaColors.panel).padding(ps.pt(4)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspektorReiter.entries.forEach { r ->
            val an = (r != InspektorReiter.BEARBEITEN && r != InspektorReiter.WERKZEUGE) || hatAuswahl
            val aktiv = r == z.reiter
            Box(
                Modifier
                    .weight(1f)
                    .height(ps.touch(44))
                    .clip(RoundedCornerShape(ps.pt(7)))
                    .background(if (aktiv) PrusaColors.orange else Color.Transparent)
                    .clickable(enabled = an && !aktiv) { z.reiter = r }
                    .testTag("inspektor." + kennung(r)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    reiterName(r),
                    fontSize = ps.font(12),
                    fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        aktiv -> Color.White
                        an -> PrusaColors.textPrimary
                        else -> PrusaColors.textMuted.copy(alpha = 0.45f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun reiterName(r: InspektorReiter): String = when (r) {
    InspektorReiter.PROFILE -> st("Profiles", "Profile")
    InspektorReiter.OBJEKTE -> st("Objects", "Objekte")
    InspektorReiter.BEARBEITEN -> st("Edit", "Bearbeiten")
    InspektorReiter.WERKZEUGE -> st("Tools", "Werkzeuge")
}

/** Was am ausgewählten Objekt geändert wird. */
@Composable
private fun bearbeitenBlock(z: AdvancedZustand, model: SlicerModel) {
    val id = model.selectedId ?: return
    val objekt = model.objects.firstOrNull { it.id == id } ?: return
    AdvancedObjectInspectorView(
        model = model,
        objekt = objekt,
        gizmo = z.gizmo,
        onGizmoChange = { z.gizmo = it },
        onSichtbereichChange = { z.bearbeitenRahmen = it },
    )
}

/**
 * Was mit dem Objekt gemacht wird, ohne es zu vermessen: bemalen,
 * Schichthöhen — und was mit der ganzen Platte geht.
 */
@Composable
private fun werkzeugeBlock(z: AdvancedZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        model.selectedId?.let { id ->
            PaintView(
                model = model,
                objektId = id,
                options = z.maloptionen,
                onOptionsChange = { z.maloptionen = it },
            )
        }
        HorizontalDivider(color = PrusaColors.divider)
        Text(
            st("Whole plate", "Ganze Platte").uppercase(),
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        // Die Anordnung ist die Arbeit — als STL geht sie in einem Stück an
        // jemanden weiter, der einen anderen Slicer benutzt.
        werkzeugKnopf(
            st("Export plate as STL", "Platte als STL ausgeben"),
            farbe = if (model.objects.isEmpty()) PrusaColors.textMuted else PrusaColors.orange,
            moeglich = model.objects.isNotEmpty(),
            kennung = "werkzeuge.platte",
        ) { z.platte = model.exportPlate() }
        // Dateiwerkzeuge: sie beziehen sich nicht auf ein Objekt, sondern
        // auf eine Datei.
        werkzeugKnopf(PsUiCatalog.tr("Custom G-code"), kennung = "werkzeuge.gcodemarken") {
            z.zeigeGcodeMarken = true
        }
        werkzeugKnopf(st("Repair STL", "STL reparieren"), kennung = "werkzeuge.reparieren") {
            z.zeigeReparatur = true
        }
        werkzeugKnopf(st("Convert G-code", "G-Code wandeln"), kennung = "werkzeuge.wandeln") {
            z.zeigeWandeln = true
        }
        z.werkzeugErgebnis?.let { url ->
            teilenKnopf(url, "werkzeuge.ergebnis.weitergeben") { teilen(context, model, url) }
        }
        z.platte?.let { url ->
            teilenKnopf(url, "werkzeuge.platte.weitergeben") { teilen(context, model, url) }
        }
    }
}

@Composable
private fun werkzeugKnopf(
    text: String,
    farbe: Color = PrusaColors.orange,
    moeglich: Boolean = true,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(48))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .clickable(enabled = moeglich, onClick = aktion)
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = ps.font(13), color = farbe)
    }
}

@Composable
private fun teilenKnopf(url: File, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.orange)
            .clickable(onClick = aktion)
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            st("Share", "Weitergeben") + " · " + url.name,
            fontSize = ps.font(12),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Auf schmalen Geraeten als Blatt ueber dem Bett. Die Schliessflaeche
 * liegt in arbeitsflaeche - nur ueber dem Bett, nicht ueber den Leisten.
 */
@Composable
private fun schmaleSeite(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onSendToPrinter: (File) -> Unit,
    onSettings: (String) -> Unit,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    // Erst unterhalb von Werkzeugleiste (52 pt) und Bettkarte (touch(52))
    // beginnen: vorher deckte die Bettkarte die erste Zeile der Seite
    // ("Print Settings") halb ab - auf dem S23 FE am 14.09.2026 gesehen,
    // auf dem iPhone genauso. Gegenstueck: schmaleSeite in
    // AdvancedWorkspaceView.swift.
    Row(Modifier.fillMaxSize().padding(top = z.werkzeugleisteHoehe + ps.touch(52))) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(ps.pt(320)).fillMaxHeight()) {
            seitenleiste(z, model, remoteSlicePluginAn, onSendToPrinter, onSettings, onRemoteSettings)
        }
    }
}

/**
 * Experimentelles Hochformat-Layout: dieselbe Seitenleiste, von unten
 * angedockt statt von rechts. Die linke Werkzeugschiene bleibt ausgespart
 * (Breite wie dort: ps.pt(74)).
 */
@Composable
private fun unteneSeite(
    z: AdvancedZustand,
    model: SlicerModel,
    remoteSlicePluginAn: Boolean,
    onSendToPrinter: (File) -> Unit,
    onSettings: (String) -> Unit,
    onRemoteSettings: () -> Unit,
) {
    val ps = LocalPsScale.current
    Row(Modifier.fillMaxSize().testTag("advanced.leiste.unten")) {
        Spacer(Modifier.width(ps.pt(74)).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Keine eigene Schliessflaeche mehr - die liegt in arbeitsflaeche
            // nur ueber dem Bett, nicht ueber Werkzeugleiste und Bettkarte.
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(minOf(ps.pt(420), ps.windowSize.height * 0.5f)),
            ) {
                seitenleiste(
                    z, model, remoteSlicePluginAn, onSendToPrinter, onSettings, onRemoteSettings,
                )
            }
        }
    }
}

/** Drucker, Filament, Druckprofil - die drei Angaben, mit denen gerechnet wird. */
@Composable
private fun profilblock(z: AdvancedZustand, model: SlicerModel, onSettings: (String) -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        // Drucker und Filament als Blatt mit denselben Karten wie im Simple
        // Mode.
        profilzeile(
            model = model,
            titel = PsUiCatalog.tr("Printer"),
            reiter = "printer",
            kennung = "advanced.wahl.printer",
        ) { z.zeigeDrucker = true }
        profilzeile(
            model = model,
            titel = PsUiCatalog.tr("Filament"),
            reiter = "filament",
            kennung = "advanced.wahl.filament",
        ) {
            z.materialZiel = null
            z.zeigeMaterial = true
        }
        // Druckprofile sind eine Handvoll und tragen ihre Auskunft im Namen.
        profilwahl(
            model = model,
            titel = PsUiCatalog.tr("Print settings"),
            typ = PsmCore.PresetType.PRINT,
            reiter = "print",
            kennung = "advanced.wahl.print",
            onSettings = onSettings,
        )
        // Ab zwei Extrudern ist "das Material" keine Frage mehr, sondern
        // eine je Position.
        if (model.extruderCount > 1) {
            HorizontalDivider(color = PrusaColors.divider)
            ExtruderBank(model = model) { kopf ->
                z.materialZiel = kopf
                z.zeigeMaterial = true
            }
        }
    }
}

/** Eine Zeile, die ein Blatt oeffnet. */
@Composable
private fun profilzeile(
    model: SlicerModel,
    titel: String,
    reiter: String,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val gewaehlt = model.selectedPreset(reiter) ?: ""
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            titel.uppercase(),
            fontSize = ps.font(10),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(44))
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panelRaised)
                .clickable(onClick = aktion)
                .padding(horizontal = ps.pt(10))
                .testTag(kennung),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (gewaehlt.isEmpty()) {
                    st("Not selected", "Nicht gewählt")
                } else {
                    EasyModeState.profileDisplayLabel(gewaehlt)
                },
                modifier = Modifier.weight(1f),
                fontSize = ps.font(13),
                color = PrusaColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("›", fontSize = ps.font(14), color = PrusaColors.textMuted)
        }
    }
}

@Composable
private fun profilwahl(
    model: SlicerModel,
    titel: String,
    typ: PsmCore.PresetType,
    reiter: String,
    kennung: String,
    onSettings: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    val gewaehlt = model.selectedPreset(reiter) ?: ""
    // Dreissig reichen: mehr passt in kein Menue.
    val namen = model.presetNames(typ).take(30)
    var offen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            titel.uppercase(),
            fontSize = ps.font(10),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textMuted,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ps.touch(44))
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(PrusaColors.panelRaised)
                        .clickable { offen = true }
                        .padding(horizontal = ps.pt(10))
                        .testTag(kennung),
                    horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (gewaehlt.isEmpty()) {
                            st("Not selected", "Nicht gewählt")
                        } else {
                            EasyModeState.profileDisplayLabel(gewaehlt)
                        },
                        fontSize = ps.font(13),
                        color = PrusaColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("▾", fontSize = ps.font(11), color = PrusaColors.textMuted)
                }
                DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                    ScaledOverlay {
                        Column {
                            namen.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(EasyModeState.profileDisplayLabel(name)) },
                                    onClick = {
                                        offen = false
                                        model.selectPreset(typ, name)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Der Stift fuehrt dorthin, wo dieses Profil im Einzelnen steht.
            Box(
                Modifier
                    .size(ps.touch(44), ps.touch(44))
                    .clickable { onSettings(reiter) }
                    .testTag("$kennung.bearbeiten"),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", fontSize = ps.font(14), color = PrusaColors.textMuted)
            }
        }
    }
}

@Composable
private fun objektliste(z: AdvancedZustand, model: SlicerModel) {
    val ps = LocalPsScale.current
    // Ohne Suchtext alle. Gesucht wird im Namen, und ohne Rücksicht auf
    // Groß- und Kleinschreibung.
    val sichtbar = if (z.objektSuche.trim().isEmpty()) {
        model.objects
    } else {
        model.objects.filter { it.name.contains(z.objektSuche, ignoreCase = true) }
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        if (model.objects.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ps.pt(8)))
                    .background(PrusaColors.panelRaised)
                    .padding(vertical = ps.pt(28)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    st("No objects yet", "Noch keine Objekte"),
                    fontSize = ps.font(15),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.textPrimary,
                )
                Text(
                    st(
                        "Import with + in the tool rail",
                        "Über + in der Werkzeugleiste importieren",
                    ),
                    fontSize = ps.font(12),
                    color = PrusaColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Erst ab einer Handvoll: bei drei Objekten ist ein Suchfeld
            // mehr Bedienung als Hilfe.
            if (model.objects.size > 5) {
                textfeld(
                    wert = z.objektSuche,
                    onWert = { z.objektSuche = it },
                    platzhalter = st("Search objects", "Objekte suchen"),
                    kennung = "objekte.suche",
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { model.selectAll() },
                    modifier = Modifier
                        .defaultMinSize(minHeight = ps.touch(40))
                        .testTag("objekte.alle"),
                ) {
                    Text(
                        st("Select all", "Alle auswählen"),
                        fontSize = ps.font(12),
                        color = PrusaColors.orange,
                    )
                }
                TextButton(
                    onClick = { model.select(null) },
                    enabled = model.selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .defaultMinSize(minHeight = ps.touch(40))
                        .testTag("objekte.auswahlaufheben"),
                ) {
                    Text(
                        st("Clear", "Aufheben"),
                        fontSize = ps.font(12),
                        color = if (model.selectedIds.isEmpty()) {
                            PrusaColors.textMuted
                        } else {
                            PrusaColors.orange
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (model.selectedIds.size > 1) {
                    Text(
                        "${model.selectedIds.size}",
                        fontSize = ps.font(11),
                        color = PrusaColors.textMuted,
                    )
                }
            }
        }
        sichtbar.forEach { objekt ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(
                        if (model.selectedIds.contains(objekt.id)) {
                            PrusaColors.panelRaised
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable {
                        model.select(objekt.id)
                        // Wer ein Objekt antippt, will damit etwas tun.
                        z.offeneBereiche = z.offeneBereiche + kennung(InspektorReiter.BEARBEITEN)
                        z.seitenleistenFokusSchritt = 0
                        z.seitenleistenZiel = objekt.id
                    }
                    .padding(horizontal = ps.pt(8))
                    .onGloballyPositioned {
                        z.objektRahmen[scrollKennungFuerObjekt(objekt.id)] = it.boundsInWindow()
                    }
                    .testTag("advanced.objekt.${objekt.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Das Kästchen nimmt hinzu oder heraus, die Zeile wählt
                // einzeln aus.
                Box(
                    Modifier
                        .size(ps.touch(40), ps.touch(40))
                        .clickable { model.toggleSelection(objekt.id) }
                        .testTag("objekte.haken.${objekt.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    SfSymbol(
                        if (model.selectedIds.contains(objekt.id)) {
                            "checkmark.square.fill"
                        } else {
                            "square"
                        },
                        Modifier.size(ps.font(15).value.dp),
                        tint = if (model.selectedIds.contains(objekt.id)) {
                            PrusaColors.orange
                        } else {
                            PrusaColors.textMuted
                        },
                    )
                }
                ObjektMasse(objekt = objekt)
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        if (objekt.name.isEmpty()) "Objekt ${objekt.id}" else objekt.name,
                        fontSize = ps.font(13),
                        color = PrusaColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        masseText(objekt.sizeMm.first, objekt.sizeMm.second, objekt.sizeMm.third),
                        fontSize = ps.font(10),
                        color = if (objekt.outsideBed) {
                            PrusaColors.danger
                        } else {
                            PrusaColors.textMuted
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(ps.touch(40), ps.touch(40))
                        .clickable { model.removeObjects(listOf(objekt.id)) }
                        .testTag("advanced.entfernen.${objekt.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✖", fontSize = ps.font(12), color = PrusaColors.textMuted)
                }
            }
        }
    }
}

/** Ein Textfeld im iOS-Stil: Panelhintergrund, keine Material-Umrandung. */
@Composable
internal fun textfeld(
    wert: String,
    onWert: (String) -> Unit,
    platzhalter: String,
    kennung: String,
) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(10)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (wert.isEmpty()) {
            Text(platzhalter, fontSize = ps.font(13), color = PrusaColors.textMuted)
        }
        BasicTextField(
            value = wert,
            onValueChange = onWert,
            modifier = Modifier.fillMaxWidth().testTag(kennung),
            singleLine = true,
            textStyle = TextStyle(fontSize = ps.font(13), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
        )
    }
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
