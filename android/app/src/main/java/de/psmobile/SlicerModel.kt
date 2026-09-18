package de.psmobile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import de.psmobile.core.PsmCore
import de.psmobile.net.SecretStore
import de.psmobile.core.PsmViewport
import de.psmobile.shared.rules.AdhesionAdvice
import de.psmobile.shared.rules.Bilingual
import de.psmobile.shared.rules.ColorMixCodec
import de.psmobile.shared.rules.CoreLabels
import de.psmobile.shared.rules.ColorMixRecipe
import de.psmobile.shared.rules.FilamentCatalog
import de.psmobile.shared.rules.LayerProfile
import de.psmobile.shared.rules.SliceSummary
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.PrinterReadyPolicy
import de.psmobile.ui.PsUiCatalog
import de.psmobile.ui.st
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Zustandshalter der Oberflaeche - die 1:1-Fassade zu
 * `ios/PSMobile/SlicerModel.swift`.
 *
 * Auf iOS haelt SlicerModel den Kern selbst; auf Android liegt der Kern
 * im [SlicerService] (Foreground-Service, damit ein Slice weiterlaeuft,
 * wenn der Bildschirm ausgeht). Diese Klasse bietet den portierten
 * Bildschirmen dieselben Namen wie das Swift-Original und reicht an den
 * Dienst durch. Wer die iOS-Oberflaeche liest, findet hier jede Zeile
 * unter demselben Namen wieder.
 *
 * Beobachtung: Die veroeffentlichten Werte sind Compose-`State`; die
 * abgeleiteten Abfragen (`selectedPreset`, `config`, ...) lesen zuerst
 * [tick], das bei jeder Aenderung im Dienst steigt - so zeichnet sich
 * ein Bildschirm neu, sobald sich irgendetwas geaendert hat, wie
 * SwiftUI es bei `@Published` tut.
 */
class SlicerModel(
    context: Context,
    val service: SlicerService,
    /** Die Schalter dieses Starts - siehe [Startargumente]. */
    val startargumente: Startargumente = Startargumente.LEER,
) {
    private val context: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    sealed interface Progress {
        data object Idle : Progress
        data class Running(val percent: Int, val stage: String) : Progress
        data class Done(val seconds: Double, val printMinutes: Int, val grams: Double) : Progress
        /** [laden]: ein Import ist gescheitert, kein Schnitt - das Blatt titelt dann anders. */
        data class Failed(val message: String, val laden: Boolean = false) : Progress
        data object Cancelled : Progress
    }

    /** Steigt bei jeder Aenderung im Dienst - siehe Klassenkommentar. */
    private var tick by mutableStateOf(0)
    private fun bump() { tick++ }

    var objects: List<PsmCore.ObjectInfo> by mutableStateOf(emptyList()); private set
    var beds: List<PsmCore.Bed> by mutableStateOf(emptyList()); private set
    var progress: Progress by mutableStateOf(Progress.Idle); private set
    var memoryWarning: String? by mutableStateOf(null); private set
    var coreVersion: String by mutableStateOf("?"); private set
    var stats: PsmCore.SliceStats? by mutableStateOf(null); private set

    /** Der fertige G-Code als Datei, mit dem Namen aus dem Druckprofil. */
    var gcodeURL: File? by mutableStateOf(null); private set
    var lastSliceWasRemote: Boolean by mutableStateOf(false); private set
    /** Bei "Alle Betten schneiden": eine Datei je Bett mit Objekten. */
    var gcodeURLs: List<File> by mutableStateOf(emptyList()); private set
    var sliceAllProgress: Pair<Int, Int>? by mutableStateOf(null); private set
    /** Steigt bei jedem Bettwechsel - der Viewport schwenkt dann dorthin. */
    var focusBedKey: Int by mutableStateOf(0); private set

    var projectURL: File? by mutableStateOf(null); private set
    val hasUnsavedChanges: Boolean get() { tick; return service.hasUnsavedChanges }

    var selectedIds: Set<Int> by mutableStateOf(emptySet()); private set
    var selectedId: Int? by mutableStateOf(null); private set

    /** Bis zu welcher Einstufung Parameter gezeigt werden - ueberlebt den Start. */
    // Mit ausdruecklicher Vorgabe lesen. Drueben las
    // `integer(forKey:)` ohne Vorgabe und bekam 0 = Simple, wenn nichts
    // gespeichert war - seit dem 12.09.2026 fragt iOS erst, ob der
    // Schluessel existiert. Hier war es von Anfang an richtig.
    private val _sichtbarkeit = mutableStateOf(
        PsmCore.Mode.entries.getOrNull(prefs().getInt("psm.sichtbarkeit", PsmCore.Mode.ADVANCED.ordinal))
            ?: PsmCore.Mode.ADVANCED,
    )
    var sichtbarkeit: PsmCore.Mode
        get() = _sichtbarkeit.value
        set(value) {
            _sichtbarkeit.value = value
            prefs().edit().putInt("psm.sichtbarkeit", value.ordinal).apply()
        }

    var pendingPresetSwitch: PendingPresetSwitch? by mutableStateOf(null); private set
    /** Was beim Oeffnen anders lief - bleibt stehen, bis es weggeklickt wird. */
    var projectNotice: String? by mutableStateOf(null)
    var undoLabel: String by mutableStateOf(""); private set
    var redoLabel: String by mutableStateOf(""); private set

    var setupNeeded: Boolean by mutableStateOf(false); private set
    var printerModels: List<PsmCore.PrinterModel> by mutableStateOf(emptyList()); private set
    var setupBusy: Boolean by mutableStateOf(false); private set

    /** Nur mit installiertem, gewaehltem Druckerprofil ist der Arbeitsbereich nutzbar. */
    val printerIsReady: Boolean
        get() {
            tick
            val c = core ?: return false
            return PrinterReadyPolicy.isReady(
                selectedPrinter = runCatching { c.selectedPreset(PsmCore.PresetType.PRINTER) }.getOrDefault(""),
                installedPrinters = runCatching { c.presetNames(PsmCore.PresetType.PRINTER) }.getOrDefault(emptyList()),
            ) && installedPrinters.isNotEmpty()
        }

    var sceneRevision: Int by mutableStateOf(0); private set

    val core: PsmCore? get() { tick; return service.coreOrNull }
    val shaderDir: String get() = service.shaderDir()

    /** Ob "Slice now" auf den eigenen Server statt lokal zielt. */
    private val _remoteSliceEnabled = mutableStateOf(service.remoteSliceEnabled)
    var remoteSliceEnabled: Boolean
        get() = _remoteSliceEnabled.value
        set(value) {
            _remoteSliceEnabled.value = value
            service.remoteSliceEnabled = value
        }
    // 11.09.2026: `-psm-reset-setup` und `-psm-preset-printer` sind
    // drueben aus `SlicerModel.start()` nach `PSMobileApp.init()`
    // gewandert - sie muessen wirken, bevor das erste Bild entsteht.
    // Hier stehen sie seit jeher im Dienst (`SlicerService.ensureCore`),
    // und die Oberflaeche entsteht erst danach: der Unterschied war die
    // Reihenfolge, nicht die Regel.
    //
    // 11.09.2026: `SlicerModel.swift` hat den Arbeitsstand ueber das
    // App-Ende hinweg bekommen (`arbeitsstandSichern`,
    // `arbeitsstandZurueckholen`). Auf Android gibt es ihn seit jeher -
    // er steht im Dienst, `SlicerService.autosave()` und
    // `restoreAutosave()`, weil dort der Kern haengt. Die Aenderung ist
    // also drueben nachgezogen worden, nicht hier entstanden.
    //
    // Dazu gehoert die Regel, wann er *nicht* zurueckgeholt wird:
    // sobald ein Testschalter einen Ausgangszustand vorgibt. Sie steht
    // hier in `Startargumente.verlangtFrischenAusgangszustand` und
    // drueben in `SlicerModel.verlangtFrischenAusgangszustand` -
    // dieselbe Liste, und sie muss dieselbe bleiben.

    /**
     * Gegenstueck zu `runCredentialSelfTest` in `SlicerModel.swift`.
     *
     * Bis zum 10.09.2026 stand hier fest `null`: der Schalter
     * `-psm-credential-self-test` prueft auf iOS, dass ein Geheimnis den
     * Weg in den Schluesselspeicher und zurueck findet und **nicht** in
     * den gewoehnlichen Einstellungen landet. Auf Android pruefte er
     * nichts - der Test lief durch, weil er nie lief.
     */
    val credentialSelfTestResult: String? by lazy {
        if (startargumente.contains("-psm-credential-self-test")) selbsttestZugangsdaten() else null
    }

    private fun selbsttestZugangsdaten(): String {
        val ref = "credential-test.psmobile.invalid|apiKey"
        return runCatching {
            SecretStore.remove(context, ref)
            SecretStore.put(context, ref, "test-secret")
            try {
                val gelesen = SecretStore.get(context, ref)
                // Der zweite Teil ist der eigentliche Punkt: das Geheimnis
                // darf nirgends im Klartext liegen. Auf iOS prueft das
                // `UserDefaults.standard.object(forKey: "printer.apiKey")`.
                val klartext = prefs().getString("printer.apiKey", null)
                if (gelesen == "test-secret" && klartext == null) "passed" else "failed"
            } finally {
                SecretStore.remove(context, ref)
            }
        }.getOrDefault("failed")
    }

    val extruderCount: Int get() { tick; return core?.let { runCatching { it.extruderCount() }.getOrNull() } ?: 1 }
    val installedPrinters: Set<String> get() { tick; return service.installedPrinters() }
    val activeBedIndex: Int get() = beds.firstOrNull { it.active }?.index ?: 0
    val footprints: List<AdhesionAdvice.Footprint>
        get() = objects.map { AdhesionAdvice.Footprint(it.sizeMm.first, it.sizeMm.second, it.sizeMm.third) }

    private var remoteWarAktiv = false
    private var filamentCache: List<FilamentCatalog.Entry>? = null
    private var compatibleFilamentCache: Set<String>? = null

    init {
        fun <T> Flow<T>.beobachte(block: (T) -> Unit) {
            scope.launch { collect { block(it); bump() } }
        }
        service.objects.beobachte { objects = it; pruneSelection() }
        service.beds.beobachte { beds = it }
        service.progress.beobachte { uebernehmeFortschritt(it) }
        service.gcodeDateien.beobachte { gcodeURLs = benenneBettDateien(it) }
        service.bettFortschritt.beobachte { sliceAllProgress = it }
        service.history.beobachte {
            // Durch CoreLabels: der Kern benennt seine Schritte deutsch
            // (`history_checkpoint("Objekte importieren")`), und das
            // stand bis zum 11.09.2026 so unter dem Zurueck-Pfeil - auch
            // in der englischen Oberflaeche. Dieselbe Tabelle uebersetzt
            // drueben, siehe SlicerModel.swift.
            undoLabel = if (it.canUndo) CoreLabels.label(it.undoLabel) else ""
            redoLabel = if (it.canRedo) CoreLabels.label(it.redoLabel) else ""
        }
        service.setupNeeded.beobachte { setupNeeded = it }
        service.printerModels.beobachte { printerModels = it }
        service.setupBusy.beobachte { setupBusy = it }
        service.sceneRevision.beobachte { sceneRevision = it }
        service.configRevision.beobachte { }
        service.presets.beobachte { filamentCache = null; compatibleFilamentCache = null }
        service.colorMix.beobachte { }
        service.paintRevision.beobachte { }
        service.toolMessage.beobachte { meldung ->
            // Der Dienst meldet auch Erfolge ("Skalieren"). iOS zeigt in
            // projectNotice nur, was schiefging.
            if (meldung != null && istFehler(meldung)) projectNotice = meldung
        }
    }

    fun dispose() { scope.cancel() }

    private fun prefs() = context.getSharedPreferences("psmobile", Context.MODE_PRIVATE)

    private fun istFehler(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("fehlgeschlagen") || t.contains("failed") || t.contains("fehler") ||
            t.contains("error") || t.contains("nicht möglich") || t.contains("not possible") ||
            t.contains("gesperrt") || t.contains("locked")
    }

    private fun pruneSelection() {
        val vorhanden = objects.map { it.id }.toSet()
        val bereinigt = selectedIds.filter { it in vorhanden }.toSet()
        if (bereinigt != selectedIds) selectedIds = bereinigt
        if (selectedId != null && selectedId !in vorhanden) selectedId = bereinigt.firstOrNull()
    }

    private fun uebernehmeFortschritt(p: SlicerService.Progress) {
        progress = when (p) {
            SlicerService.Progress.Idle -> Progress.Idle
            is SlicerService.Progress.Running -> Progress.Running(p.percent, p.stage)
            is SlicerService.Progress.Done -> {
                stats = p.stats
                gcodeURL = benannteKopie(service.lastGcode)
                lastSliceWasRemote = remoteWarAktiv
                Progress.Done(
                    seconds = p.seconds,
                    printMinutes = ((p.stats?.printTimeSeconds ?: 0.0) / 60).toInt(),
                    grams = p.stats?.filamentGrams ?: 0.0,
                )
            }
            is SlicerService.Progress.Failed -> Progress.Failed(p.message)
            SlicerService.Progress.Cancelled -> Progress.Cancelled
            SlicerService.Progress.Stale -> Progress.Idle
        }
        if (p !is SlicerService.Progress.Done && p !is SlicerService.Progress.Running) {
            if (p !is SlicerService.Progress.Idle) gcodeURL = null
        }
        if (p is SlicerService.Progress.Running) { gcodeURL = null; stats = null }
    }

    /**
     * Der G-Code des Dienstes heisst intern `last.gcode`; nach aussen
     * traegt er den Namen aus dem Druckprofil, wie auf iOS (`writeGcode`).
     */
    private fun benannteKopie(quelle: File?): File? {
        val src = quelle ?: return null
        if (!src.isFile) return null
        return runCatching {
            val ordner = File(context.cacheDir, "gcode-out").apply { mkdirs() }
            val name = dateiname(service.suggestedGcodeName())
                ?: SliceSummary.fileName(objects.firstOrNull()?.name.orEmpty())
            val ziel = File(ordner, name)
            ordner.listFiles()?.forEach { if (it != ziel) it.delete() }
            src.copyTo(ziel, overwrite = true)
        }.getOrNull()
    }

    private fun benenneBettDateien(dateien: List<File>): List<File> {
        if (dateien.isEmpty()) return emptyList()
        val basis = dateiname(service.suggestedGcodeName())
            ?: SliceSummary.fileName(objects.firstOrNull()?.name.orEmpty())
        val endung = basis.substringAfterLast('.', "gcode")
        val stamm = basis.substringBeforeLast('.')
        val ordner = File(context.cacheDir, "gcode-out").apply { mkdirs() }
        return dateien.mapNotNull { datei ->
            val index = datei.nameWithoutExtension.substringAfter("bett-", "").toIntOrNull()?.minus(1)
            val bettname = (index?.let { bedLabel(it) } ?: datei.nameWithoutExtension).replace(' ', '-')
            runCatching { datei.copyTo(File(ordner, "$stamm-$bettname.$endung"), overwrite = true) }.getOrNull()
        }
    }

    /** Ein Vorschlag des Kerns, auf einen Dateinamen gestutzt. */
    private fun dateiname(vorschlag: String): String? {
        val endung = vorschlag.substringAfterLast('.', "").lowercase()
        if (endung != "gcode" && endung != "bgcode") return null
        val stamm = vorschlag.substringBeforeLast('.')
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }
            .joinToString("")
        if (stamm.isEmpty()) return null
        return "$stamm.$endung"
    }

    // MARK: - Start

    fun start() {
        PsUiCatalog.load(context, service.uiLanguage)
        coreVersion = runCatching { PsmCore.coreVersion() }.getOrDefault("?")
        if (service.coreOrNull != null) refresh()
    }

    /** Eine Datei aus der Systemauswahl in den App-Zwischenspeicher holen. */
    fun copyToCache(uri: Uri, ordner: String = "import"): File? = runCatching {
        val name = anzeigename(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "modell.stl"
        // Der Zeitstempel gehoert in den Ordnernamen, nicht in den
        // Dateinamen. Der Kern uebernimmt den Dateinamen als Objektnamen,
        // und der steht in der Modell-Liste: mit Praefix stand dort
        // "2136589326300-psm-testwuerfel.stl", waehrend iOS
        // "psm-testwuerfel.stl" zeigt. Zwei Namen fuer dieselbe Datei.
        val dir = File(File(context.cacheDir, ordner), System.nanoTime().toString())
            .apply { mkdirs() }
        val ziel = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { input ->
            ziel.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        ziel
    }.onFailure { Log.w(TAG, "Datei nicht lesbar: $uri", it) }.getOrNull()

    private fun anzeigename(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    fun load(uri: Uri) {
        val datei = copyToCache(uri) ?: run {
            progress = Progress.Failed(laden = true, message = st("The file could not be read.", "Die Datei liess sich nicht lesen."))
            return
        }
        load(datei)
    }

    fun load(url: File) {
        if (!printerIsReady || service.coreOrNull == null) {
            reopenSetup()
            progress = Progress.Failed(st("Select a printer before importing an object", "Wähle zuerst einen Drucker, dann lässt sich ein Objekt importieren"), laden = true)
            return
        }
        ladefehlerVergessen()
        runCatching { service.loadModel(url.absolutePath, SlicerService.ImportMode.OBJECTS) }
            .onFailure { progress = Progress.Failed(it.message ?: st("Import failed", "Import fehlgeschlagen"), laden = true) }
        refresh()
        checkMemory()
    }

    /**
     * Eine alte Lademeldung raeumen, bevor der naechste Import beginnt: bis
     * zum 16.09.2026 stand "Die Datei liess sich nicht lesen." weiter auf
     * dem Schirm, obwohl die naechste Datei laengst geladen war (Emulator).
     */
    private fun ladefehlerVergessen() {
        val p = progress
        if (p is Progress.Failed && p.laden) progress = Progress.Idle
    }

    /**
     * Nach dem Teilen einer ZIP: entpackt alle Modelldateien und fuegt
     * sie wie einzelne Importe hinzu. null heisst Fehler beim Entpacken,
     * 0 eine ZIP ohne erkennbares Modell.
     */
    fun loadZip(uri: Uri): Int? {
        val datei = copyToCache(uri) ?: return null
        return loadZip(datei)
    }

    fun loadZip(url: File): Int? {
        if (!printerIsReady || service.coreOrNull == null) {
            reopenSetup()
            progress = Progress.Failed(st("Select a printer before importing an object", "Wähle zuerst einen Drucker, dann lässt sich ein Objekt importieren"), laden = true)
            return null
        }
        ladefehlerVergessen()
        val anzahl = runCatching { service.loadZip(url.absolutePath) }
            .onFailure { progress = Progress.Failed(it.message ?: st("Import failed", "Import fehlgeschlagen"), laden = true) }
            .getOrNull()
        // Eine ZIP ohne Modell blieb stumm (S23 FE, 16.09.2026) - sagen, warum nichts kam.
        if (anzahl == 0) progress = Progress.Failed(st("The ZIP contains no model files.", "Die ZIP enthält keine Modelldateien."), laden = true)
        refresh()
        checkMemory()
        return anzahl
    }

    // MARK: - Auswahl

    fun select(id: Int?) {
        selectedId = id
        selectedIds = if (id == null) emptySet() else setOf(id)
    }

    fun toggleSelection(id: Int) {
        val menge = selectedIds.toMutableSet()
        if (menge.contains(id)) {
            menge.remove(id)
            selectedIds = menge
            if (selectedId == id) selectedId = menge.firstOrNull()
        } else {
            menge.add(id)
            selectedIds = menge
            selectedId = id
        }
        // Kein sceneRevision += 1: die Auswahl aendert das Projekt nicht.
        // Bis zum 16.09.2026 warf ein Haekchen in der Objektliste die
        // ausgegebene Platte (z.platte) weg; der Viewport liest selectedIds
        // ohnehin selbst (Bug-Bounty, wie drueben).
    }

    fun selectAll() {
        selectedIds = objects.map { it.id }.toSet()
        if (selectedId == null) selectedId = objects.firstOrNull()?.id
    }

    // MARK: - Profile und Konfiguration

    private fun typ(tab: String): PsmCore.PresetType? = when (tab) {
        "print" -> PsmCore.PresetType.PRINT
        "filament" -> PsmCore.PresetType.FILAMENT
        "printer" -> PsmCore.PresetType.PRINTER
        else -> null
    }

    private fun tab(type: PsmCore.PresetType): String = when (type) {
        PsmCore.PresetType.PRINT -> "print"
        PsmCore.PresetType.FILAMENT -> "filament"
        PsmCore.PresetType.PRINTER -> "printer"
    }

    /** Das gewaehlte Profil eines Bereichs - "print", "filament", "printer". */
    fun selectedPreset(tab: String): String? {
        tick
        val t = typ(tab) ?: return null
        return core?.let { runCatching { it.selectedPreset(t) }.getOrNull() }?.takeIf { it.isNotBlank() }
    }

    fun presetNames(tab: String): List<String> = typ(tab)?.let { presetNames(it) } ?: emptyList()

    fun presetNames(type: PsmCore.PresetType): List<String> {
        tick
        return core?.let { runCatching { it.presetNames(type) }.getOrNull() } ?: emptyList()
    }

    fun selectPreset(tab: String, name: String) { typ(tab)?.let { selectPreset(it, name) } }

    fun selectPreset(type: PsmCore.PresetType, name: String) = requestPresetSwitch(type, name)

    fun setConfig(key: String, value: String) {
        service.setConfig(key, value)
        sceneRevision += 1
    }

    fun config(key: String): String? { tick; return core?.let { runCatching { it[key] }.getOrNull() } }

    fun extruderFilament(index: Int): String {
        tick
        return core?.let { runCatching { it.extruderFilament(index) }.getOrNull() } ?: ""
    }

    fun setExtruderFilament(index: Int, name: String) {
        service.setExtruderFilament(index, name)
        sceneRevision += 1
    }

    fun extruderColor(index: Int): String {
        tick
        return core?.let { runCatching { it.extruderColor(index) }.getOrNull() } ?: ""
    }

    fun setExtruderColor(index: Int, hex: String) {
        service.setExtruderColor(index, hex)
        sceneRevision += 1
    }

    /** Position und Drehung des Reinigungsturms - (x, y, rotationDeg). */
    fun wipeTower(): Triple<Float, Float, Float>? {
        tick
        val w = runCatching { service.wipeTower() }.getOrNull() ?: return null
        return Triple(w.x, w.y, w.rotationDegrees)
    }

    fun setWipeTower(x: Float, y: Float, rotationDeg: Float) {
        service.setWipeTower(PsmCore.WipeTower(x, y, rotationDeg))
        sceneRevision += 1
    }

    fun colorMixRecipes(): List<ColorMixRecipe> {
        tick
        val json = core?.let { runCatching { it.colorMixJson() }.getOrNull() } ?: return emptyList()
        return ColorMixCodec.decode(json)
    }

    fun saveColorMix(recipes: List<ColorMixRecipe>): Boolean {
        val c = core ?: return false
        val colors = (0 until extruderCount).map { index ->
            extruderColor(index).ifEmpty { "#808080" }
        }
        return runCatching { c.setColorMixJson(ColorMixCodec.encode(colors, recipes)) }
            .onSuccess {
                service.refreshColorMix()
                service.notifyViewportChanged()
                sceneRevision += 1
            }
            .isSuccess
    }

    /** Der Extruder eines Objekts. 0 heisst: der Standard des Profils. */
    fun objectExtruder(id: Int): Int = objects.firstOrNull { it.id == id }?.extruder ?: 0

    fun setObjectExtruder(id: Int, extruder: Int) {
        service.setObjectExtruder(id, extruder)
        refresh()
    }

    /** Welche Filamentprofile zum eingerichteten Drucker passen. */
    fun compatibleFilamentNames(): Set<String> {
        tick
        compatibleFilamentCache?.let { return it }
        val c = core ?: return emptySet()
        val ergebnis = runCatching { c.presetEntries(PsmCore.PresetType.FILAMENT) }
            .getOrDefault(emptyList())
            .filter { it.compatible }
            .map { it.name }
            .toSet()
        compatibleFilamentCache = ergebnis
        return ergebnis
    }

    /** Alle Filamentprofile mit Typ und Farbe. */
    fun filamentCatalog(): List<FilamentCatalog.Entry> {
        tick
        val c = core ?: return emptyList()
        val namen = runCatching { c.presetNames(PsmCore.PresetType.FILAMENT) }.getOrDefault(emptyList())
        filamentCache?.let { gemerkt ->
            if (gemerkt.size == namen.size && gemerkt.firstOrNull()?.rawPreset == namen.firstOrNull()) return gemerkt
        }
        val eintraege = namen.map { name ->
            FilamentCatalog.entry(
                rawPreset = name,
                type = runCatching { c.presetOption(PsmCore.PresetType.FILAMENT, name, "filament_type") }.getOrDefault(""),
                colorHex = runCatching { c.presetOption(PsmCore.PresetType.FILAMENT, name, "filament_colour") }.getOrDefault(""),
            )
        }
        filamentCache = eintraege
        return eintraege
    }

    // MARK: - Ungespeicherte Profilaenderungen

    data class Profilaenderung(
        val art: PsmCore.PresetType,
        val key: String,
        val bezeichnung: String,
        val vorher: String,
        val jetzt: String,
    ) {
        val id: String get() = "${art.raw}.$key"
    }

    data class PendingPresetSwitch(
        val art: PsmCore.PresetType,
        val ziel: String,
        val aenderungen: List<Profilaenderung>,
    ) {
        val id: String get() = "${art.raw}:$ziel"
    }

    private fun aenderungen(type: PsmCore.PresetType): List<Profilaenderung> {
        val c = core ?: return emptyList()
        return runCatching { c.changes(type) }.getOrDefault(emptyList()).map { wert ->
            val meta = runCatching { c.configMeta(wert.key) }.getOrNull()
            Profilaenderung(
                art = type,
                key = wert.key,
                bezeichnung = meta?.label?.takeIf { it.isNotBlank() } ?: wert.key,
                vorher = wert.was,
                jetzt = wert.now,
            )
        }
    }

    /** Alles, was gegenueber den gewaehlten Profilen geaendert ist. */
    fun profilaenderungen(): List<Profilaenderung> {
        tick
        return listOf(PsmCore.PresetType.PRINT, PsmCore.PresetType.FILAMENT, PsmCore.PresetType.PRINTER)
            .flatMap { aenderungen(it) }
    }

    fun profilaenderungenVerwerfen() {
        service.profilaenderungenVerwerfen()
        sceneRevision += 1
        refresh()
    }

    fun profilSichern(als: String) {
        if (als.isEmpty()) return
        listOf(PsmCore.PresetType.PRINT, PsmCore.PresetType.FILAMENT, PsmCore.PresetType.PRINTER)
            .filter { aenderungen(it).isNotEmpty() }
            .forEach { service.savePresetAs(it, als) }
        refresh()
    }

    /** Wahr, wenn alles geschrieben wurde; ein Systemprofil lehnt der Kern ab. */
    fun profilUeberschreiben(): Boolean {
        var alles = true
        listOf(PsmCore.PresetType.PRINT, PsmCore.PresetType.FILAMENT, PsmCore.PresetType.PRINTER)
            .filter { aenderungen(it).isNotEmpty() }
            .forEach { art ->
                val name = selectedPreset(tab(art))
                if (!name.isNullOrEmpty() && !service.savePresetAs(art, name)) alles = false
            }
        refresh()
        if (!alles) {
            projectNotice = st(
                "System profiles cannot be overwritten – the changes stay in this project.",
                "Systemprofile lassen sich nicht überschreiben – die Änderungen bleiben im Projekt.",
            )
        }
        return alles
    }

    fun requestPresetSwitch(type: PsmCore.PresetType, name: String) {
        if (selectedPreset(tab(type)) == name) return
        val dirty = aenderungen(type)
        if (dirty.isEmpty()) {
            performPresetSwitch(type, name)
            return
        }
        pendingPresetSwitch = PendingPresetSwitch(type, name, dirty)
    }

    fun pendingPresetSwitchAbbrechen() { pendingPresetSwitch = null }

    fun pendingPresetSwitchVerwerfenUndWechseln() {
        val pending = pendingPresetSwitch ?: return
        core?.let { runCatching { it.discardChanges(pending.art) } }
        pendingPresetSwitch = null
        performPresetSwitch(pending.art, pending.ziel)
    }

    fun pendingPresetSwitchAlsNeuesProfilSichernUndWechseln(name: String) {
        val pending = pendingPresetSwitch ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        // Der Name eines Systemprofils wird abgelehnt - dann bleibt der
        // Dialog offen, statt die Aenderungen mit dem Wechsel wegzuwerfen.
        if (!service.savePresetAs(pending.art, trimmed)) {
            projectNotice = st(
                "This name belongs to a system profile – choose another one.",
                "Dieser Name gehört einem Systemprofil – bitte einen anderen wählen.",
            )
            return
        }
        pendingPresetSwitch = null
        performPresetSwitch(pending.art, pending.ziel)
    }

    fun pendingPresetSwitchUeberschreibenUndWechseln() {
        val pending = pendingPresetSwitch ?: return
        val current = selectedPreset(tab(pending.art))
        // Ein Systemprofil laesst sich nicht ueberschreiben - dann bleibt der
        // Dialog offen, statt die Aenderungen mit dem Wechsel wegzuwerfen.
        if (!current.isNullOrEmpty() && !service.savePresetAs(pending.art, current)) {
            projectNotice = st(
                "System profiles cannot be overwritten – save as a new profile instead.",
                "Systemprofile lassen sich nicht überschreiben – als neues Profil sichern.",
            )
            return
        }
        pendingPresetSwitch = null
        performPresetSwitch(pending.art, pending.ziel)
    }

    fun pendingPresetSwitchInsProjektUebernehmen() {
        val pending = pendingPresetSwitch ?: return
        val transfer = pending.aenderungen.map { PsmCore.Change(it.key, it.vorher, it.jetzt) }
        service.selectPresetKeeping(pending.art, pending.ziel, transfer)
        if (pending.art == PsmCore.PresetType.FILAMENT) {
            service.setExtruderFilament(0, pending.ziel)
            transfer.forEach { service.setConfig(it.key, it.now) }
        }
        pendingPresetSwitch = null
        finishPresetSwitch(pending.art)
    }

    private fun performPresetSwitch(type: PsmCore.PresetType, name: String) {
        if (type == PsmCore.PresetType.FILAMENT) {
            // Wie auf iOS: ueber den Extruder waehlen, sonst macht
            // update_compatible() die Wahl still rueckgaengig.
            service.setExtruderFilament(0, name)
            finishPresetSwitch(type)
            return
        }
        service.selectPreset(type, name)
        finishPresetSwitch(type)
    }

    private fun finishPresetSwitch(type: PsmCore.PresetType) {
        if (type == PsmCore.PresetType.PRINTER) {
            filamentCache = null
            compatibleFilamentCache = null
        }
        sceneRevision += 1
        refresh()
    }

    // MARK: - Ersteinrichtung

    fun completeSetup(keys: List<String>) {
        if (setupBusy) return
        service.completeSetup(keys)
    }

    fun standardwerteSetzen() {
        service.standardwerteSetzen()
        refresh()
    }

    fun reopenSetup() { service.reopenSetup() }

    fun dismissSetup() {
        if (!printerIsReady || setupBusy) return
        service.dismissSetup()
    }

    // MARK: - Objekte

    fun remove(id: Int) {
        service.removeObject(id)
        refresh()
    }

    fun refresh() {
        service.refreshObjects()
        sceneRevision += 1
        bump()
    }

    fun undo() {
        service.undo()
        select(null)
        refresh()
    }

    fun redo() {
        service.redo()
        select(null)
        refresh()
    }

    // MARK: - Projekte

    fun loadProject(uri: Uri) {
        val datei = copyToCache(uri, "project") ?: run {
            progress = Progress.Failed(laden = true, message = st("The file could not be read.", "Die Datei liess sich nicht lesen."))
            return
        }
        loadProject(datei)
    }

    fun loadProject(url: File) {
        ladefehlerVergessen()
        runCatching { service.loadModel(url.absolutePath, SlicerService.ImportMode.PROJECT) }
            .onSuccess { info ->
                projectNotice = info?.let { hinweis(it) }
                projectURL = url.takeIf { it.parentFile == projectsDirectory }
                refresh()
            }
            .onFailure { progress = Progress.Failed(it.message ?: st("Import failed", "Import fehlgeschlagen"), laden = true) }
    }

    /** Wo Projekte liegen - der Dateien-App zugaenglich, nicht der Zwischenspeicher. */
    val projectsDirectory: File
        get() {
            val basis = context.getExternalFilesDir(null) ?: context.filesDir
            return File(basis, "Projects").apply { mkdirs() }
        }

    /** Die gesicherten Projekte, neueste zuerst. */
    fun recentProjects(limit: Int = 8): List<File> {
        tick
        return (projectsDirectory.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.equals("3mf", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .take(limit)
    }

    fun deleteProject(url: File) {
        runCatching { url.delete() }
        de.psmobile.net.ProjektSpiegel.entfernen(context, url.name)
        bump()
    }

    fun newProject() {
        service.newProject()
        projectURL = null
        projectNotice = null
        select(null)
        refresh()
    }

    fun customGcodeList(): List<PsmCore.CustomGcode> { tick; return service.customGcodes() }

    fun addCustomGcode(e: PsmCore.CustomGcode) {
        core?.let { runCatching { it.addCustomGcode(e) } }
        service.notifyViewportChanged()
        refresh()
    }

    fun updateCustomGcode(index: Int, e: PsmCore.CustomGcode) {
        core?.let { runCatching { it.updateCustomGcode(index, e) } }
        service.notifyViewportChanged()
        refresh()
    }

    fun removeCustomGcode(index: Int) {
        core?.let { runCatching { it.removeCustomGcode(index) } }
        service.notifyViewportChanged()
        refresh()
    }

    /** Repariert eine STL und legt das Ergebnis in den Projektordner. */
    fun repairSTL(uri: Uri): File? {
        val quelle = copyToCache(uri, "repair-input") ?: return null
        return repairSTL(quelle)
    }

    fun repairSTL(url: File): File? = runCatching {
        val ergebnis = service.repairStlFile(url)
        val ziel = File(projectsDirectory, url.nameWithoutExtension.substringAfter('-', url.nameWithoutExtension) + "-repariert.stl")
        ergebnis.copyTo(ziel, overwrite = true)
    }.onFailure { projectNotice = it.message }.getOrNull()

    /** Wandelt eine G-Code-Datei zwischen ASCII und BGCode. */
    fun convertGcode(uri: Uri, toBinary: Boolean): File? {
        val quelle = copyToCache(uri, "gcode-input") ?: return null
        return convertGcode(quelle, toBinary)
    }

    fun convertGcode(url: File, toBinary: Boolean): File? = runCatching {
        val ergebnis = service.convertGcodeFile(url, toBinary)
        val endung = if (toBinary) "bgcode" else "gcode"
        val ziel = File(projectsDirectory, url.nameWithoutExtension.substringAfter('-', url.nameWithoutExtension) + "." + endung)
        ergebnis.copyTo(ziel, overwrite = true)
    }.onFailure { projectNotice = it.message }.getOrNull()

    /** Die Platte als eine einzige STL. */
    fun exportPlate(): File? = runCatching {
        val datei = SliceSummary.fileName("PSMobile").replace(".gcode", "-platte.stl")
        val ergebnis = service.exportPlateFile(PsmCore.PlateFormat.STL)
        ergebnis.copyTo(File(projectsDirectory, datei), overwrite = true)
    }.onFailure { projectNotice = it.message }.getOrNull()

    /**
     * Der Name, den der Sichern-Dialog vorschlaegt: das gesicherte Projekt,
     * sonst das erste Objekt - ohne dessen Dateiendung. Bis zum 16.09.2026
     * stand "wuerfel20.stl" im Feld, und aus "screw.step" wurde beim
     * Sichern "screw_step.3mf" (S23 FE, Bug-Bounty). Gegenstueck:
     * `proposedProjectName` drueben.
     */
    val proposedProjectName: String
        get() = projectURL?.nameWithoutExtension
            ?: objects.firstOrNull()?.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "PSMobile"

    /** Sichert alle Betten als PrusaSlicer-taugliches 3MF. */
    fun saveProject(name: String = "PSMobile") {
        val basis = SliceSummary.fileName(name).substringBeforeLast('.')
        val datei = (basis.ifEmpty { "psmobile" }) + ".3mf"
        val ziel = File(projectsDirectory, datei)
        runCatching {
            val quelle = service.saveProjectFile()
            quelle.copyTo(ziel, overwrite = true)
            // Sichtbare Kopie fuer die Dateien-App - wie Documents/Projects auf iOS.
            de.psmobile.net.ProjektSpiegel.spiegeln(context, ziel)
        }.onSuccess {
            projectURL = ziel
            bump()
        }.onFailure {
            projectURL = null
            projectNotice = it.message ?: st("Saving failed", "Speichern fehlgeschlagen")
        }
    }

    private fun hinweis(info: PsmCore.ProjectImport): String? {
        val zeilen = mutableListOf<String>()
        if (info.requestedPrinter.isNotBlank() && info.requestedPrinter != info.selectedPrinter) {
            zeilen += st("Printer profile differs: ", "Anderes Druckerprofil aktiv: ") + info.selectedPrinter
        }
        if (info.requestedPrint.isNotBlank() && info.requestedPrint != info.selectedPrint) {
            zeilen += st("Print profile differs: ", "Anderes Druckprofil aktiv: ") + info.selectedPrint
        }
        if (info.postProcessRemoved) {
            zeilen += st(
                "Embedded post-processing scripts were not loaded.",
                "Eingebettete Nachbearbeitungsskripte wurden nicht übernommen.",
            )
        }
        // Das Filament kennt die Importauskunft nicht - aber wenn danach
        // "- default -" aktiv ist, fehlt das Profil des Projekts, und ohne
        // Hinweis fiele das erst am Druck auf (MK3S-Projekt am Emulator, 16.09.2026).
        val filament = selectedPreset("filament").orEmpty()
        if (info.configLoaded && (filament.isBlank() || filament.contains("default", ignoreCase = true))) {
            zeilen += st("No matching filament profile – choose one", "Kein passendes Filamentprofil – bitte wählen")
        }
        return if (zeilen.isEmpty()) null else zeilen.joinToString("  ·  ")
    }

    // MARK: - Betten und Anordnen

    /** Obergrenze aus dem C-ABI (PSM_MAX_BEDS). */
    companion object {
        private const val TAG = "SlicerModel"
        const val maxBeds = 36
        const val remoteSliceEnabledKey = SlicerService.KEY_REMOTE_SLICE_ENABLED
        const val remoteSliceHostKey = SlicerService.KEY_REMOTE_SLICE_HOST
    }

    /**
     * Anordnen mit Rueckgabe. Wirft bei gesperrtem oder vollem Bett -
     * der Panel-Pfad braucht Erfolg und Fehler als echten Wert.
     */
    @Throws(PsmCore.PsmException::class)
    fun arrange(target: Int, gapMm: Float, allowRotation: Boolean = false): PsmCore.ArrangeResult {
        val c = service.coreOrNull ?: throw PsmCore.PsmException(st("Core not ready", "Kern nicht bereit"))
        val ergebnis = c.arrangeBed(target, gapMm, allowRotation)
        if (!ergebnis.ok) {
            val grund = when (ergebnis.status) {
                PsmCore.ArrangeStatus.LOCKED -> st("${bedLabel(target)} is locked", "${bedLabel(target)} ist gesperrt")
                PsmCore.ArrangeStatus.FULL -> st("${bedLabel(target)} is full", "${bedLabel(target)} ist voll")
                else -> st("Arrange failed", "Anordnen fehlgeschlagen")
            }
            throw PsmCore.PsmException(grund)
        }
        service.notifyViewportChanged()
        refresh()
        return ergebnis
    }

    fun arrange() {
        val aktiv = activeBedIndex
        runCatching { arrange(aktiv, 6f) }.onFailure { projectNotice = it.message }
    }

    /** Alle nicht gesperrten Betten in einem Rutsch anordnen. */
    fun arrangeAll(gapMm: Float = 6f, allowRotation: Boolean = false) {
        beds.filter { !it.locked }.forEach { bett ->
            runCatching { arrange(bett.index, gapMm, allowRotation) }
        }
    }

    fun duplicate(ids: List<Int>) {
        service.duplicateObjects(ids)
        refresh()
    }

    fun removeObjects(ids: List<Int>) {
        service.removeObjects(ids)
        if (selectedId in ids) selectedId = null
        selectedIds = selectedIds - ids.toSet()
        refresh()
    }

    /** Wie ein Bett heisst - der eigene Name, sonst die Nummer. */
    fun bedLabel(index: Int): String {
        val name = beds.firstOrNull { it.index == index }?.name?.trim().orEmpty()
        return if (name.isEmpty()) st("Bed", "Bett") + " ${index + 1}" else name
    }

    fun renameBed(index: Int, to: String) {
        service.renameBed(index, to)
        refresh()
    }

    fun isBedLocked(index: Int): Boolean = beds.firstOrNull { it.index == index }?.locked ?: false

    fun toggleBedLock(index: Int) {
        service.toggleBedLock(index)
        refresh()
    }

    fun addBed() {
        service.addBed()
        selectedId = null
        sceneRevision += 1
        refresh()
    }

    fun removeBed(index: Int) {
        service.removeBed(index)
        selectedId = null
        sceneRevision += 1
        refresh()
    }

    fun selectBed(index: Int) {
        service.selectBed(index)
        selectedId = null
        selectedIds = emptySet()
        sceneRevision += 1
        focusBedKey += 1
        refresh()
    }

    /** Schiebt Objekte auf ein anderes Bett; jenseits der vorhandenen legt es eines an. */
    fun moveToBed(ids: List<Int>, target: Int) {
        val c = service.coreOrNull ?: return
        var ziel = target
        if (ziel >= beds.size) {
            val aktiv = activeBedIndex
            ziel = runCatching { c.addBed() }.getOrNull() ?: return
            runCatching { c.selectBed(aktiv) }
        }
        ids.forEach { service.moveObjectToBed(it, ziel) }
        select(null)
        refresh()
    }

    fun split(id: Int) {
        service.splitIntoObjects(id)
        select(null)
        refresh()
    }

    fun cut(id: Int, zMm: Float) {
        service.cutObject(id, zMm, keepUpper = true, keepLower = true, keepAsParts = false)
        select(null)
        refresh()
    }

    fun setUniformScale(id: Int, faktor: Float) { service.setUniformScale(id, faktor); refresh() }

    fun scaleToSize(id: Int, mm: Float) { service.scaleToSize(id, mm); refresh() }

    /** Dreht eine Achse auf einen festen Winkel - in Grad. */
    fun setRotationAxis(id: Int, achse: Int, grad: Float) { service.setRotationAxis(id, achse, grad); refresh() }

    /** Dreht um einen Betrag weiter - fuer die Vierteldrehungen. */
    fun rotateBy(id: Int, achse: Int, grad: Float) { service.rotateBy(id, achse, grad); refresh() }

    // MARK: - Bemalen

    fun paint(hit: PsmViewport.SurfaceHit, previous: PsmViewport.SurfaceHit?, options: PsmCore.PaintOptions) {
        // Der Dienst zaehlt den Strich selbst (bumpScene) - sonst weiss
        // hasUnsavedChanges nichts davon.
        service.paintStroke(hit, previous, options)
    }

    fun paint(
        id: Int,
        instance: Int,
        volume: Int,
        facet: Int,
        hit: Triple<Float, Float, Float>,
        previous: Triple<Float, Float, Float>?,
        options: PsmCore.PaintOptions,
    ) {
        val treffer = PsmViewport.SurfaceHit(id, volume, facet, instance, hit.first, hit.second, hit.third, 0f, 0f, 1f)
        val voriger = previous?.let {
            PsmViewport.SurfaceHit(id, volume, facet, instance, it.first, it.second, it.third, 0f, 0f, 1f)
        }
        paint(treffer, voriger, options)
    }

    fun clearPaint(id: Int, tool: PsmCore.PaintTool) {
        service.clearPaint(id, tool)
        sceneRevision += 1
        bump()
    }

    fun paintCount(id: Int, tool: PsmCore.PaintTool): Int { tick; return service.paintCount(id, tool) }

    fun layOnFacet(id: Int, instance: Int = 0, volume: Int, facet: Int) {
        core?.let { runCatching { it.layOnFacet(id, instance, volume, facet) } }
        service.notifyViewportChanged()
        refresh()
    }

    fun layOnFacet(hit: PsmViewport.SurfaceHit) {
        service.layOnFacet(hit)
        refresh()
    }

    /** Dreht die angetippte Flaeche nach unten. */
    fun layOnFace(id: Int, instance: Int = 0, volume: Int, facet: Int) {
        layOnFacet(id, instance, volume, facet)
        sceneRevision += 1
    }

    fun layerProfile(id: Int): List<LayerProfile.Point> {
        tick
        return service.layerProfile(id).map { LayerProfile.Point(it.first, it.second) }
    }

    fun setLayerProfile(id: Int, points: List<LayerProfile.Point>) {
        service.setLayerProfile(id, points.map { it.z to it.height })
        refresh()
    }

    fun clearLayerProfile(id: Int) {
        service.setLayerProfile(id, emptyList())
        refresh()
    }

    fun layerProfileAdaptive(id: Int, qualityFactor: Float): List<LayerProfile.Point> =
        runCatching { service.adaptivesLayerProfile(id, qualityFactor) }
            .onFailure { projectNotice = it.message }
            .getOrDefault(emptyList())
            .map { LayerProfile.Point(it.first, it.second) }

    /** Legt das Objekt auf seine groesste ebene Flaeche. */
    fun layFlat(id: Int) {
        service.layFlatAuto(id)
        sceneRevision += 1
        refresh()
    }

    /** Reduziert die Dreieckszahl. Gibt vorher und nachher zurueck. */
    fun simplify(id: Int, ratio: Float): Pair<Int, Int>? {
        val c = service.coreOrNull ?: return null
        val ergebnis = runCatching { c.simplify(id, ratio.coerceIn(0.01f, 1f)) }
            .onFailure { projectNotice = it.message }
            .getOrNull() ?: return null
        service.notifyViewportChanged()
        sceneRevision += 1
        refresh()
        return ergebnis.before to ergebnis.after
    }

    /** Zerlegt getrennte Koerper in einzelne Volumen. */
    fun splitVolumes(id: Int): Int {
        val c = service.coreOrNull ?: return 0
        val anzahl = runCatching { c.splitVolumes(id) }.getOrDefault(0)
        service.notifyViewportChanged()
        sceneRevision += 1
        refresh()
        return anzahl
    }

    fun suggestedGcodeName(): String = service.suggestedGcodeName()

    fun volumeCount(id: Int): Int { tick; return core?.let { runCatching { it.volumes(id).size }.getOrNull() } ?: 0 }

    fun volumeInfo(id: Int, at: Int): PsmCore.VolumeInfo? {
        tick
        return core?.let { runCatching { it.volumes(id).getOrNull(at) }.getOrNull() }
    }

    fun addPrimitiveVolume(id: Int, type: PsmCore.VolumeType, shape: PsmCore.PrimitiveShape, size: Float): Boolean {
        service.addPrimitiveVolume(id, type, shape, size, size, size)
        sceneRevision += 1
        refresh()
        return true
    }

    fun removeVolume(id: Int, at: Int) {
        service.removeVolume(id, at)
        sceneRevision += 1
        refresh()
    }

    fun setVolumeExtruder(id: Int, at: Int, extruder: Int) {
        service.setVolumeExtruder(id, at, extruder)
        refresh()
    }

    fun fitToBed(id: Int) { service.fitToBed(id); refresh() }

    /** Spiegeln an einer Achse: 0 = X, 1 = Y, 2 = Z. */
    fun mirror(id: Int, axis: Int) {
        val a = PsmCore.Axis.entries.getOrNull(axis) ?: return
        service.mirror(id, a)
        refresh()
    }

    fun dropToBed(id: Int) { service.dropToBed(id); refresh() }

    fun setInstances(id: Int, count: Int) { service.setInstances(id, count); refresh() }

    private fun checkMemory() { memoryWarning = runCatching { service.memoryWarning() }.getOrNull() }

    // MARK: - Schneiden und Ergebnis

    /** Verbrauch je Extruder des letzten Ergebnisses. */
    fun extruderUsage(): List<PsmCore.ExtruderUsage> {
        tick
        val c = core ?: return emptyList()
        return runCatching {
            (0 until c.sliceExtruderCount()).mapNotNull { c.sliceExtruder(it) }
        }.getOrDefault(emptyList())
    }

    /** Ob ein Hinsehen ohne neues Rechnen genuegt. */
    val sliceResultIsCurrent: Boolean get() { tick; return service.sliceResultIsCurrent() }

    fun previewSnapshot(): PsmCore.PreviewSnapshot? {
        if (!sliceResultIsCurrent && !lastSliceWasRemote) return null
        return core?.let { runCatching { it.previewSnapshot() }.getOrNull() }
    }

    /**
     * Wird vor jedem Slice gerufen - MainActivity haengt hier die Frage
     * nach dem Benachrichtigungsrecht ein (Android 13+). Ohne das Recht
     * laeuft der Vordergrunddienst zwar, aber seine Fortschritts-
     * Benachrichtigung bleibt unsichtbar. iOS kennt die Frage nicht;
     * dort fragt das System stattdessen beim ersten Druckerkontakt nach
     * dem lokalen Netzwerk.
     */
    var vorDemSlicen: (() -> Unit)? = null

    fun slice() {
        vorDemSlicen?.invoke()
        remoteWarAktiv = remoteSliceEnabled
        stats = null
        gcodeURL = null
        service.startSlice()
    }

    fun sliceAll() {
        vorDemSlicen?.invoke()
        remoteWarAktiv = false
        stats = null
        gcodeURL = null
        gcodeURLs = emptyList()
        service.startSliceAlleBetten()
    }

    fun cancel() { service.cancelSlice() }

    @Throws(PsmCore.PsmException::class)
    fun exportGcode(to: File) {
        val c = service.coreOrNull ?: throw PsmCore.PsmException(st("Core not ready", "Kern nicht bereit"))
        c.exportGcode(to.absolutePath)
    }

    /** Zurueck in den Ruhezustand - das Ergebnis bleibt. */
    fun dismissProgress() {
        service.dismissProgress()
        progress = Progress.Idle
    }

    /** Wo ein Objekt relativ zum Druckraum liegt. */
    fun bedState(id: Int): PsmCore.BedState {
        tick
        return core?.let { runCatching { it.bedStateOf(id) }.getOrNull() } ?: PsmCore.BedState.UNKNOWN
    }

    val objectsOffBed: List<PsmCore.ObjectInfo> get() = objects.filter { it.outsideBed }

    val sliceBlockers: List<String> get() = hinderungsgruende(objects.size)

    /**
     * Fuer "Alle Betten slicen": leer ist erst, wenn kein Bett etwas traegt.
     * Bis zum 16.09.2026 blockte ein leeres aktives Bett den Lauf mit
     * "Nichts auf dem Bett", obwohl Bett 2 belegt war (Emulator).
     * Gegenstueck: `sliceAllBlockers` drueben.
     */
    val sliceAllBlockers: List<String> get() = hinderungsgruende(beds.sumOf { it.objectCount })

    private fun hinderungsgruende(objektzahl: Int): List<String> {
            val gruende = SliceSummary.blockers(
                objects = objektzahl,
                printer = selectedPreset("printer").orEmpty(),
                filament = selectedPreset("filament").orEmpty(),
                print = selectedPreset("print").orEmpty(),
            ).toMutableList()
            val daneben = objectsOffBed
            if (daneben.isNotEmpty()) {
                val namen = daneben.joinToString(", ") { it.name }
                gruende += Bilingual(
                    english = "Outside the print area: $namen",
                    german = "Außerhalb des Druckbereichs: $namen",
                ).text
            }
            return gruende
    }

    // MARK: - Teilen (Android-Infrastruktur)

    /**
     * Eine Datei fuer den Teilen-Dialog freigeben. Ueber den
     * FileProvider und nur aus dem Freigabeordner - dieselbe Regel wie
     * im Dienst.
     */
    fun shareableUri(file: File): Uri? = runCatching {
        val outDir = File(context.cacheDir, "share").apply { mkdirs() }
        val dst = File(outDir, file.name)
        if (file.canonicalPath != dst.canonicalPath) file.copyTo(dst, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dst)
    }.onFailure { Log.w(TAG, "Datei nicht teilbar: ${file.name}", it) }.getOrNull()
}

/** Das Modell fuer alle Bildschirme - Gegenstueck zu `.environmentObject(model)`. */
val LocalSlicerModel = compositionLocalOf<SlicerModel> {
    error("SlicerModel fehlt - PSMobileApp stellt es bereit")
}
