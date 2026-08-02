package de.psmobile.slicing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.psmobile.MainActivity
import de.psmobile.BuildConfig
import de.psmobile.R
import de.psmobile.core.PsmCore
import de.psmobile.slicing.profileupdate.ProfilePackageStore
import de.psmobile.slicing.profileupdate.ProfileUpdateRepository
import de.psmobile.slicing.profileupdate.ProfileUpdateState
import de.psmobile.slicing.profileupdate.ProfileVersion
import de.psmobile.slicing.profileupdate.HttpUrlConnectionProfileUpdateHttp
import de.psmobile.slicing.colormix.ColorMixCodec
import de.psmobile.slicing.colormix.ColorMixRecipe
import de.psmobile.ui.BedLockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Haelt die PsmCore-Session und faehrt den Slice-Job.
 *
 * Warum ein Service und keine Coroutine in der Activity: Ein Slice kann
 * auf dem Handy mehrere Minuten dauern. Als Foreground-Service laeuft er
 * weiter, wenn der Bildschirm ausgeht, und der Nutzer sieht Fortschritt
 * in der Benachrichtigung.
 *
 * Die Session lebt bewusst hier und nicht in der Activity - so ueberlebt
 * sie Drehungen und Konfigurationswechsel, und der spaetere Umzug in
 * einen eigenen Prozess (siehe AndroidManifest) beruehrt nur diese Klasse.
 */
class SlicerService : Service() {

    companion object {
        private const val TAG = "SlicerService"
        private const val CHANNEL_ID = "psm_slicing"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_SLICE = "de.psmobile.action.START_SLICE"
        const val ACTION_CANCEL_SLICE = "de.psmobile.action.CANCEL_SLICE"

        // Feste Stufen statt freier Eingabe - siehe QuickKey.
        val LAYER_HEIGHTS = listOf("0.1" to "0,1", "0.15" to "0,15", "0.2" to "0,2", "0.3" to "0,3")
        val FILL_DENSITIES = listOf("0%" to "0 %", "10%" to "10 %", "15%" to "15 %", "25%" to "25 %", "50%" to "50 %")
        val SUPPORT_MODES = listOf("0" to "aus", "1" to "an")
        val BRIM_MODES = listOf("0" to "aus", "5" to "5 mm")
    }

    sealed interface Progress {
        data object Idle : Progress
        data class Running(val percent: Int, val stage: String) : Progress
        data class Done(val stats: PsmCore.SliceStats?, val seconds: Double) : Progress
        data class Failed(val message: String) : Progress
        data object Cancelled : Progress
        data object Stale : Progress
    }

    inner class LocalBinder : Binder() {
        val service: SlicerService get() = this@SlicerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _profileUpdates = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val profileUpdates: StateFlow<ProfileUpdateState> = _profileUpdates.asStateFlow()
    private var profileUpdateRepository: ProfileUpdateRepository? = null

    fun deferProfileUpdate() { profileUpdateRepository?.later() }
    fun skipProfileUpdate() { profileUpdateRepository?.skipUntilNewer() }
    fun downloadProfileUpdate() { profileUpdateRepository?.downloadOffered(scope) }

    /**
     * Bei einem sofortigen Wechsel wird die Sitzung aus dem geprüften
     * Profilpaket neu aufgebaut. Ein Slice darf dabei niemals abgewürgt
     * werden; in diesem Fall bleibt das Paket für den nächsten Start liegen.
     */
    fun applyStagedProfileUpdate() {
        val ready = _profileUpdates.value as? ProfileUpdateState.ReadyToApply ?: return
        if (_progress.value is Progress.Running) {
            _profileUpdates.value = ProfileUpdateState.Deferred(
                ready.manifest,
                "Der laufende Slice wird nicht unterbrochen. Das Update wird beim Neustart aktiv.",
            )
            return
        }
        scope.launch(Dispatchers.IO) {
            val store = ProfilePackageStore(
                File(filesDir, "profile-resources"),
                File(filesDir, "profile-resources/fallback"),
            )
            val result = runCatching {
                // 3MF hält Modell, Platten und die aktuelle Zuordnung
                // zusammen. Damit geht beim Session-Neustart nichts verloren.
                val sessionCopy = core?.let { oldCore ->
                    File(cacheDir, "profile-switch").apply { mkdirs() }
                        .let { dir -> File(dir, "session-before-update.3mf") }
                        .also { oldCore.saveProject(it.absolutePath) }
                }
                synchronized(this@SlicerService) {
                    core?.close()
                    core = null
                    store.activateStaged().getOrThrow()
                    try {
                        val refreshed = ensureCore()
                        sessionCopy?.takeIf(File::isFile)?.let { refreshed.loadProject(it.absolutePath) }
                    } catch (error: Throwable) {
                        core?.close()
                        core = null
                        store.rollback().getOrThrow()
                        ensureCore()
                        throw error
                    }
                    refreshBeds()
                    refreshHistory()
                    refreshObjects()
                    refreshPresets()
                    refreshQuickSettings()
                }
            }
            _profileUpdates.value = result.fold(
                onSuccess = { ProfileUpdateState.Idle },
                onFailure = { ProfileUpdateState.Failed(it.message ?: "Profile konnten nicht aktiviert werden") },
            )
        }
    }

    /** Ein echter Speicherdialog ist vor dem Sitzungswechsel erforderlich. */
    fun profileUpdateNeedsSave(): Boolean =
        _objects.value.isNotEmpty() ||
            _presets.value.printerChanges.isNotEmpty() ||
            _presets.value.printChanges.isNotEmpty() ||
            _presets.value.filamentChanges.isNotEmpty()

    private val _objects = MutableStateFlow<List<PsmCore.ObjectInfo>>(emptyList())
    val objects: StateFlow<List<PsmCore.ObjectInfo>> = _objects.asStateFlow()

    private val _beds = MutableStateFlow<List<PsmCore.Bed>>(emptyList())
    val beds: StateFlow<List<PsmCore.Bed>> = _beds.asStateFlow()

    /** Ein Extruder mit seinem Filament und seiner Farbe. */
    data class Extruder(
        val index: Int,
        val filament: String = "",
        /** "#RRGGBB", leer wenn die Farbe des Filaments gilt. */
        val color: String = "",
    )

    /** Auswaehlbare Profile je Typ, plus die aktuelle Auswahl. */
    data class Presets(
        val printers: List<String> = emptyList(),
        val prints: List<String> = emptyList(),
        val filaments: List<String> = emptyList(),
        val selectedPrinter: String = "",
        val selectedPrint: String = "",
        val selectedFilament: String = "",
        val printerChanges: List<PsmCore.Change> = emptyList(),
        val printChanges: List<PsmCore.Change> = emptyList(),
        val filamentChanges: List<PsmCore.Change> = emptyList(),
        /**
         * Ein Eintrag je Extruder. Bei einem Kopf genau einer, beim MMU3
         * und beim XL-5T fuenf - jeder mit eigenem Filament und eigener
         * Farbe.
         */
        val extruders: List<Extruder> = emptyList(),
        /**
         * Die Filamente, die zum gewaehlten Drucker nicht passen. Sie
         * stehen nur dann ueberhaupt in [filaments], wenn der Nutzer sie
         * ausdruecklich eingeblendet hat - dann aber gekennzeichnet,
         * statt ununterscheidbar dazwischen.
         */
        val incompatibleFilaments: Set<String> = emptySet(),
        val showIncompatible: Boolean = false,
    ) {
        fun changes(type: PsmCore.PresetType): List<PsmCore.Change> = when (type) {
            PsmCore.PresetType.PRINTER -> printerChanges
            PsmCore.PresetType.PRINT -> printChanges
            PsmCore.PresetType.FILAMENT -> filamentChanges
        }
    }

    private val _presets = MutableStateFlow(Presets())
    val presets: StateFlow<Presets> = _presets.asStateFlow()

    data class ColorMixState(
        val available: Boolean = false,
        val recipes: List<ColorMixRecipe> = emptyList(),
        val reason: String? = null,
    )

    private val _colorMix = MutableStateFlow(ColorMixState())
    val colorMix: StateFlow<ColorMixState> = _colorMix.asStateFlow()

    /*
     * Schnelleinstellungen: die fuenf Parameter, die den Alltag abdecken.
     * PrusaSlicer hat einige hundert - die vollstaendige Liste kommt
     * spaeter als eigener Bildschirm, generiert aus PrintConfig. Hier
     * bewusst als feste Stufen statt Zahleneingabe: mit dem Finger will
     * niemand "0.15" tippen.
     */
    enum class QuickKey(val configKey: String) {
        LAYER_HEIGHT("layer_height"),
        FILL_DENSITY("fill_density"),
        SUPPORTS("support_material"),
        BRIM("brim_width"),
    }

    data class QuickSettings(
        val layerHeight: String = "",
        val fillDensity: String = "",
        val supports: String = "",
        val supportAuto: String = "",
        val supportBuildPlateOnly: String = "",
        val supportStyle: String = "",
        val brim: String = "",
    )

    private val _quick = MutableStateFlow(QuickSettings())
    val quickSettings: StateFlow<QuickSettings> = _quick.asStateFlow()

    private val _history = MutableStateFlow(
        PsmCore.HistoryState(0, 0, "", "")
    )
    val history: StateFlow<PsmCore.HistoryState> = _history.asStateFlow()

    private val _volumes =
        MutableStateFlow<Map<Int, List<PsmCore.VolumeInfo>>>(emptyMap())
    val volumes: StateFlow<Map<Int, List<PsmCore.VolumeInfo>>> =
        _volumes.asStateFlow()

    private val _toolMessage = MutableStateFlow<String?>(null)
    val toolMessage: StateFlow<String?> = _toolMessage.asStateFlow()
    private val heavyMutationActive = AtomicBoolean(false)

    /** Persistent per-project bed locks. SAF URIs are stable across reopen. */
    private val projectKey = MutableStateFlow("current")
    private val _lockedBeds = MutableStateFlow<Set<Int>>(emptySet())
    val lockedBeds: StateFlow<Set<Int>> = _lockedBeds.asStateFlow()

    fun setProjectKey(key: String?) {
        val normalized = key?.takeIf { it.isNotBlank() } ?: "current"
        projectKey.value = normalized
        _lockedBeds.value = prefs.getStringSet("bedLocks:$normalized", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
    }

    fun isBedLocked(index: Int): Boolean = index in _lockedBeds.value

    fun toggleBedLock(index: Int) {
        val next = BedLockPolicy.toggle(_lockedBeds.value, index)
        _lockedBeds.value = next
        prefs.edit().putStringSet("bedLocks:${projectKey.value}", next.map(Int::toString).toSet()).apply()
        _toolMessage.value = if (index in next) "Bett ${index + 1} gesperrt" else "Bett ${index + 1} entsperrt"
    }

    private fun checkBedUnlocked(index: Int, action: String): Boolean {
        if (BedLockPolicy.allows(_lockedBeds.value, index)) return true
        _toolMessage.value = "Bett ${index + 1} ist gesperrt – $action nicht möglich"
        return false
    }

    private fun activeBedIndex(): Int = _beds.value.firstOrNull { it.active }?.index ?: 0

    fun clearToolMessage() {
        _toolMessage.value = null
    }

    private var core: PsmCore? = null
    @Volatile private var sliceCommandActive = false
    @Volatile private var cancelRequestedByCommand = false
    @Volatile private var latestCommandStartId = 0
    var lastGcode: File? = null
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /* Auch ein doppelter Start- oder Cancel-Intent erhoeht Androids
         * startId. Der Worker muss beim Abschluss die neueste ID
         * quittieren, sonst bleibt der Service unbemerkt gestartet. */
        latestCommandStartId = startId
        when (intent?.action) {
            ACTION_START_SLICE -> {
                if (sliceCommandActive) {
                    return START_NOT_STICKY
                }
                sliceCommandActive = true
                cancelRequestedByCommand = false
                /* Android verlangt die Notification unmittelbar nach
                 * startForegroundService; Preset-Laden und Slicen duerfen
                 * erst danach im Worker beginnen. */
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(0, getString(R.string.slice_starting)),
                )
                runSlice(startId)
            }
            ACTION_CANCEL_SLICE -> {
                cancelRequestedByCommand = true
                core?.cancelSlice()
                if (!sliceCommandActive)
                    stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startProfileUpdateCheck()
    }

    private fun startProfileUpdateCheck() {
        val manifest = runCatching { java.net.URI(BuildConfig.PROFILE_UPDATE_MANIFEST_URL) }.getOrNull()
            ?: return
        val hosts = BuildConfig.PROFILE_UPDATE_ALLOWED_HOSTS
            .split(',').map(String::trim).filter(String::isNotBlank).toSet()
        val coreVersion = ProfileVersion.parse(PsmCore.coreVersion()) ?: return
        val resourceRoot = File(filesDir, "profile-resources")
        val store = ProfilePackageStore(resourceRoot, File(resourceRoot, "fallback"))
        val repository = ProfileUpdateRepository(
            manifestUri = manifest,
            allowedHosts = hosts,
            coreVersion = coreVersion,
            activeVersion = store.activeVersion(),
            http = HttpUrlConnectionProfileUpdateHttp(),
            packageStore = store,
            skippedVersion = {
                prefs.getString("profiles.skipped-version", null)?.let(ProfileVersion::parse)
            },
            rememberSkippedVersion = { version ->
                prefs.edit().putString("profiles.skipped-version", version.toString()).apply()
            },
        )
        profileUpdateRepository = repository
        scope.launch {
            repository.state.collect { _profileUpdates.value = it }
        }
        repository.checkOnLaunch(scope)
    }

    override fun onDestroy() {
        scope.cancel()
        core?.close()
        core = null
        super.onDestroy()
    }

    /**
     * Legt die Session an, falls noch nicht geschehen.
     *
     * Blockierend (Profile entpacken und parsen dauert beim ersten Start
     * ein bis zwei Sekunden) und bewusst synchronisiert: Activity und
     * Import-Pfad koennen gleichzeitig hier hereinlaufen, und zwei
     * parallel erzeugte Sessions waeren ein stiller Speicherfresser.
     */
    @Synchronized
    fun ensureCore(): PsmCore {
        core?.let { return it }
        // Fallback zuerst bereitstellen; ein vollständig geprüftes staged
        // Profilpaket darf ausschließlich beim kontrollierten Sitzungsstart
        // aktiv werden.
        ResourceInstaller.ensureInstalled(this)
        val resourceRoot = File(filesDir, "profile-resources")
        val fallback = File(resourceRoot, "fallback")
        ProfilePackageStore(resourceRoot, fallback).activateStagedOnLaunch()
        val resDir = ResourceInstaller.ensureInstalled(this)
        val dataDir = File(filesDir, "psmdata").apply { mkdirs() }
        val c = PsmCore.create(dataDir.absolutePath, resDir.absolutePath)
        core = c
        refreshBeds()
        refreshHistory()

        // Nur die bei der Ersteinrichtung gewaehlten Drucker einrichten.
        // Alles zu laden kostet 13,5 s statt 1,9 s - siehe SetupScreen.
        val chosen = installedPrinters()
        if (chosen.isEmpty()) {
            _setupNeeded.value = true
            _printerModels.value = runCatching { c.scanPrinterModels() }.getOrDefault(emptyList())
        } else {
            runCatching { c.installPresets(chosen.toList()) }
                .onFailure { Log.w(TAG, "Profile nicht geladen: ${it.message}") }
            _setupNeeded.value = false
            runCatching {
                c.showIncompatiblePresets =
                    prefs.getBoolean("presets.show-incompatible", false)
            }
            refreshPresets()
            refreshQuickSettings()
            restoreAutosave(c)
        }
        return c
    }

    // --- Arbeitsstand ueber das App-Ende hinweg ---------------------------
    //
    // Android beendet einen Prozess im Hintergrund ohne Vorwarnung. Wer
    // ein halbes Bett aufgebaut hatte, fand vorher ein leeres vor. Deshalb
    // schreibt die App bei jedem Wechsel in den Hintergrund ein
    // vollstaendiges Projekt und liest es beim naechsten Start zurueck -
    // dieselbe 3MF-Maschinerie wie "Speichern unter", nur an einen festen
    // Ort. Ein ausdruecklich gespeichertes Projekt bleibt davon unberuehrt.

    private val autosaveFile: File
        get() = File(filesDir, "autosave").apply { mkdirs() }.resolve("session.3mf")

    /**
     * Schreibt den aktuellen Stand weg. Ein leeres Bett loescht die Datei,
     * sonst kaeme nach "Neues Projekt" beim naechsten Start der alte Stand
     * zurueck.
     */
    fun autosave() {
        val c = core ?: return
        if (_setupNeeded.value) return
        runCatching {
            if (_objects.value.isEmpty() && _beds.value.size <= 1) {
                autosaveFile.delete()
                return
            }
            // Erst daneben schreiben, dann umbenennen: ein abgebrochener
            // Schreibvorgang darf keine halbe Datei hinterlassen, die beim
            // naechsten Start als Arbeitsstand gilt. Die Endung muss dabei
            // .3mf bleiben - der Core prueft sie und lehnt sonst ab.
            val tmp = File(autosaveFile.parentFile, "session-part.3mf")
            c.saveProject(tmp.absolutePath)
            if (autosaveFile.exists()) autosaveFile.delete()
            tmp.renameTo(autosaveFile)
        }.onFailure { Log.w(TAG, "Arbeitsstand nicht gesichert", it) }
    }

    private fun restoreAutosave(c: PsmCore) {
        val file = autosaveFile
        if (! file.isFile || file.length() == 0L) return
        runCatching {
            c.loadProject(file.absolutePath)
        }.onSuccess {
            refreshBeds()
            refreshObjects()
            refreshPresets()
            refreshQuickSettings()
            Log.i(TAG, "Arbeitsstand wiederhergestellt")
        }.onFailure {
            // Ein unlesbarer Arbeitsstand darf den Start nicht blockieren.
            Log.w(TAG, "Arbeitsstand nicht lesbar, wird verworfen", it)
            file.delete()
        }
    }

    // --- Ersteinrichtung --------------------------------------------------

    private val prefs by lazy { getSharedPreferences("psmobile", Context.MODE_PRIVATE) }

    private val _setupNeeded = MutableStateFlow(false)
    val setupNeeded: StateFlow<Boolean> = _setupNeeded.asStateFlow()

    private val _printerModels = MutableStateFlow<List<PsmCore.PrinterModel>>(emptyList())
    val printerModels: StateFlow<List<PsmCore.PrinterModel>> = _printerModels.asStateFlow()

    private val _setupBusy = MutableStateFlow(false)
    val setupBusy: StateFlow<Boolean> = _setupBusy.asStateFlow()

    fun installedPrinters(): Set<String> =
        prefs.getStringSet("printers", emptySet()) ?: emptySet()

    /** Richtet die gewaehlten Drucker ein und merkt sich die Auswahl. */
    fun completeSetup(keys: List<String>) {
        val c = core ?: return
        scope.launch {
            _setupBusy.value = true
            try {
                c.installPresets(keys)
                prefs.edit().putStringSet("printers", keys.toSet()).apply()
                refreshPresets()
                notifyConfigChanged()
                _setupNeeded.value = false
            } catch (t: Throwable) {
                Log.e(TAG, "Ersteinrichtung fehlgeschlagen", t)
                _progress.value = Progress.Failed(t.message ?: "Einrichtung fehlgeschlagen")
            } finally {
                _setupBusy.value = false
            }
        }
    }

    /** Druckerauswahl erneut oeffnen. */
    fun reopenSetup() {
        // Easy Mode kann sichtbar werden, waehrend die native Session noch
        // im Hintergrund aufgebaut wird. Nicht still abbrechen: den Aufbau
        // abwarten und danach den Setup-Dialog auf dem Main-Thread anzeigen.
        scope.launch(Dispatchers.Default) {
            val c = runCatching { ensureCore() }.getOrElse {
                Log.e(TAG, "Drucker-Setup konnte nicht geoeffnet werden", it)
                return@launch
            }
            val models = runCatching { c.scanPrinterModels() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                _printerModels.value = models
                _setupNeeded.value = true
            }
        }
    }

    var uiLanguage: String
        get() = prefs.getString("lang", "en") ?: "en"
        set(v) { prefs.edit().putString("lang", v).apply() }

    /**
     * Legt den fertigen G-Code an einen teilbaren Ort und liefert eine
     * content://-URI dafuer.
     *
     * Ueber FileProvider und nicht als file://-URI: Android untersagt
     * seit Nougat, file://-URIs an andere Apps weiterzureichen.
     */
    fun shareableGcodeUri(): android.net.Uri? {
        val src = lastGcode ?: return null
        val outDir = File(cacheDir, "share").apply { mkdirs() }
        // Frueher hiess jede Datei "psmobile.gcode". Der Name kommt jetzt
        // aus output_filename_format des Druckprofils, wie am Desktop -
        // also mit Modell, Schichthoehe, Material, Drucker und Druckzeit.
        val dst = File(outDir, suggestedGcodeName())
        outDir.listFiles()?.forEach { if (it != dst) it.delete() }
        src.copyTo(dst, overwrite = true)
        return androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", dst
        )
    }

    /** Dateiname nach PrusaSlicers Vorgabe, mit Rueckfallebene. */
    fun suggestedGcodeName(): String {
        val raw = core?.let { runCatching { it.suggestedGcodeName() }.getOrNull() }
        val name = raw?.takeIf { it.isNotBlank() } ?: "print.gcode"
        // Der Name landet im Dateisystem und wird weitergereicht - alles,
        // was dort Aerger macht, ersetzen.
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    fun refreshPresets() {
        val c = core ?: return
        val selectedPrinter = c.selectedPreset(PsmCore.PresetType.PRINTER)
        val allPrinters = c.presetNames(PsmCore.PresetType.PRINTER)
        val printers = if (de.psmobile.net.PrinterStore.onlyLinked(this)) {
            val linked = de.psmobile.net.PrinterStore.all(this)
                .map { it.presetName }
                .filter { it.isNotBlank() }
                .toSet()
            // Die aktuelle Auswahl bleibt sichtbar. Andernfalls wuerde
            // das Kombifeld beim Einschalten des Filters leer erscheinen.
            allPrinters.filter { it == selectedPrinter || it in linked }
        } else {
            allPrinters
        }
        val filamentEntries = runCatching {
            c.presetEntries(PsmCore.PresetType.FILAMENT)
        }.getOrElse { emptyList() }
        _presets.value = Presets(
            printers = printers,
            prints = c.presetNames(PsmCore.PresetType.PRINT),
            filaments = filamentEntries.map { it.name },
            incompatibleFilaments = filamentEntries.filterNot { it.compatible }
                .map { it.name }.toSet(),
            showIncompatible = runCatching { c.showIncompatiblePresets }.getOrDefault(false),
            selectedPrinter = selectedPrinter,
            selectedPrint = c.selectedPreset(PsmCore.PresetType.PRINT),
            selectedFilament = c.selectedPreset(PsmCore.PresetType.FILAMENT),
            printerChanges = runCatching {
                c.changes(PsmCore.PresetType.PRINTER)
            }.getOrDefault(emptyList()),
            printChanges = runCatching {
                c.changes(PsmCore.PresetType.PRINT)
            }.getOrDefault(emptyList()),
            filamentChanges = runCatching {
                c.changes(PsmCore.PresetType.FILAMENT)
            }.getOrDefault(emptyList()),
            extruders = (0 until c.extruderCount()).map { i ->
                Extruder(
                    index = i,
                    filament = runCatching { c.extruderFilament(i) }.getOrDefault(""),
                    color = runCatching { c.extruderColor(i) }.getOrDefault(""),
                )
            },
        )
        refreshColorMix()
    }

    /** Liest die persistente 3MF-kompatible ColorMix-Konfiguration. */
    fun refreshColorMix() {
        val c = core ?: return
        val result = runCatching { c.colorMixJson() }
        _colorMix.value = result.fold(
            onSuccess = { json -> ColorMixState(available = true, recipes = ColorMixCodec.decode(json)) },
            // Alte ausgelieferte .so-Dateien kennen die neue ABI noch nicht.
            // Das ist ein klarer Zustand, kein stiller, wirkungsloser Dialog.
            onFailure = { ColorMixState(available = false, reason = "Slicer-Core ohne ColorMix-ABI") },
        )
    }

    fun saveColorMix(recipes: List<ColorMixRecipe>) {
        val c = core ?: return
        val physicalColors = _presets.value.extruders.map { it.color.ifBlank { "#808080" } }
        runCatching { c.setColorMixJson(ColorMixCodec.encode(physicalColors, recipes)) }
            .onFailure { _toolMessage.value = "ColorMix konnte nicht gespeichert werden: ${it.message}" }
            .onSuccess {
                refreshColorMix()
                invalidateSliceResult()
                _toolMessage.value = "ColorMix aktualisiert"
            }
    }

    /** Filament eines einzelnen Kopfes - fuer MMU und XL. */
    fun setExtruderFilament(index: Int, name: String) {
        val c = core ?: return
        runCatching { c.setExtruderFilament(index, name) }
            .onFailure { Log.w(TAG, "Filament fuer Extruder $index: ${it.message}") }
            .onSuccess {
                refreshPresets()
                notifyConfigChanged()
            }
    }

    fun setExtruderColor(index: Int, rgb: String) {
        val c = core ?: return
        runCatching { c.setExtruderColor(index, rgb) }
            .onFailure { Log.w(TAG, "Farbe fuer Extruder $index: ${it.message}") }
            .onSuccess {
                refreshPresets()
                notifyConfigChanged()
            }
    }

    /**
     * Profil waehlen.
     *
     * Die Reihenfolge ist nicht beliebig: Der Drucker bestimmt, welche
     * Druck- und Filamentprofile ueberhaupt kompatibel sind. Nach jeder
     * Auswahl muessen die Listen deshalb neu gelesen werden.
     */
    /**
     * Auch Profile zeigen, die zum gewaehlten Drucker nicht passen.
     *
     * Die Wahl gilt fuer die Sitzung und wird gemerkt: wer sie einmal
     * gebraucht hat, braucht sie meist wieder, und sie beim naechsten
     * Start still zurueckzusetzen waere die unangenehmere Ueberraschung.
     */
    fun setShowIncompatiblePresets(on: Boolean) {
        val c = core ?: return
        runCatching { c.showIncompatiblePresets = on }
            .onFailure { Log.w(TAG, "Unpassende Profile umschalten: ${it.message}") }
            .onSuccess {
                prefs.edit().putBoolean("presets.show-incompatible", on).apply()
                refreshPresets()
            }
    }

    fun selectPreset(type: PsmCore.PresetType, name: String) {
        val c = core ?: return
        runCatching { c.selectPreset(type, name) }
            .onFailure { Log.w(TAG, "Preset '$name' nicht waehlbar: ${it.message}") }
            .onSuccess {
                refreshPresets()
                refreshObjects()
                notifyConfigChanged()
            }
    }

    /** Ungespeicherte Werte verwerfen und danach ein anderes Profil waehlen. */
    fun discardPresetChangesAndSelect(type: PsmCore.PresetType, name: String) {
        val c = core ?: return
        runCatching {
            c.discardChanges(type)
            c.selectPreset(type, name)
        }
            .onFailure { Log.w(TAG, "Aenderungen verwerfen: ${it.message}") }
            .onSuccess {
                refreshPresets()
                refreshObjects()
                notifyConfigChanged()
            }
    }

    /** Ein Profil wechseln und die bearbeiteten Werte auf das Ziel uebertragen. */
    fun selectPresetKeeping(
        type: PsmCore.PresetType,
        name: String,
        changes: List<PsmCore.Change>,
    ) {
        val c = core ?: return
        runCatching { c.selectPresetKeeping(type, name, changes) }
            .onFailure { Log.w(TAG, "Aenderungen uebertragen: ${it.message}") }
            .onSuccess {
                refreshPresets()
                refreshObjects()
                notifyConfigChanged()
            }
    }

    /** Das aktuell bearbeitete Profil unter eigenem Namen dauerhaft speichern. */
    fun savePresetAs(type: PsmCore.PresetType, name: String) {
        val c = core ?: return
        runCatching { c.savePresetAs(type, name.trim()) }
            .onFailure { Log.w(TAG, "Profil speichern: ${it.message}") }
            .onSuccess {
                refreshPresets()
                refreshObjects()
                notifyConfigChanged()
            }
    }

    /** Fuer den Viewport, der direkt auf der Session arbeitet (E-03). */
    val coreOrNull: PsmCore? get() = core

    /*
     * Navigationszustand im Service, nicht in der Oberflaeche.
     *
     * Grund: Eine per Teilen hereinkommende Datei muss die Ansicht aufs
     * Bett zurueckholen koennen. Lag der Zustand in einem lokalen
     * `remember`, lud das Modell unsichtbar im Hintergrund, waehrend der
     * Nutzer weiter den Druckerbildschirm sah - Befund A1 in
     * docs/09-fehlerliste.md.
     */
    sealed interface Screen {
        data object Bed : Screen
        data class Settings(val tab: String) : Screen
        data object Printers : Screen
        data object Wizard : Screen
        data object ColorMix : Screen
    }

    private val _screen = MutableStateFlow<Screen>(Screen.Bed)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun showScreen(s: Screen) { _screen.value = s }

    fun showBed() { _screen.value = Screen.Bed }

    /** Verzeichnis mit den GLES-Shadern aus PrusaSlicer. */
    fun shaderDir(): String =
        File(ResourceInstaller.ensureInstalled(this), "shaders/ES").absolutePath

    /** Steigt bei jeder Modelaenderung - der Viewport baut dann neu auf. */
    private val _sceneRevision = MutableStateFlow(0)
    val sceneRevision: StateFlow<Int> = _sceneRevision.asStateFlow()

    private fun bumpScene() { _sceneRevision.value = _sceneRevision.value + 1 }

    /**
     * Steigt bei jeder Aenderung an der Konfiguration - Profilwechsel,
     * Neuinstallation, Einzelwert.
     *
     * Die Einstellungsseite haengt ihre zwischengespeicherten Werte daran
     * auf. Ohne das zeigte sie nach einem Profilwechsel weiter die alten
     * Werte und schrieb sie beim naechsten Antippen in die neue
     * Konfiguration zurueck - Befund A3 in docs/09-fehlerliste.md.
     */
    private val _configRevision = MutableStateFlow(0)
    val configRevision: StateFlow<Int> = _configRevision.asStateFlow()

    private fun bumpConfig() { _configRevision.value = _configRevision.value + 1 }

    /**
     * Entfernt alle Android-seitigen Verweise auf ein altes Ergebnis.
     * Der Core prueft dieselbe Invariante noch einmal ueber Revisionen;
     * diese Seite sorgt dafuer, dass die UI gar nicht erst Export oder
     * Versand fuer einen veralteten Job anbietet.
     */
    private fun invalidateSliceResult() {
        lastGcode = null
        _sendState.value = null
        if (_progress.value !is Progress.Running)
            _progress.value = Progress.Idle
    }

    /**
     * Rueckmeldung fuer Einstellungsseiten, die direkt ueber PsmCore
     * schreiben. Aktualisiert Abhaengigkeiten, Bett und Slice-Status.
     */
    fun notifyConfigChanged() {
        refreshPresets()
        refreshQuickSettings()
        bumpConfig()
        bumpScene()
        invalidateSliceResult()
    }

    /** Rueckmeldung nach einer direkten Manipulation im GL-Viewport. */
    fun notifyViewportChanged() {
        refreshObjects()
        invalidateSliceResult()
    }

    fun refreshQuickSettings() {
        val c = core ?: return
        _quick.value = QuickSettings(
            layerHeight = c[QuickKey.LAYER_HEIGHT.configKey].orEmpty(),
            fillDensity = c[QuickKey.FILL_DENSITY.configKey].orEmpty(),
            supports = c[QuickKey.SUPPORTS.configKey].orEmpty(),
            supportAuto = c["support_material_auto"].orEmpty(),
            supportBuildPlateOnly = c["support_material_buildplate_only"].orEmpty(),
            supportStyle = c["support_material_style"].orEmpty(),
            brim = c[QuickKey.BRIM.configKey].orEmpty(),
        )
    }

    fun setQuick(key: QuickKey, value: String) {
        val c = core ?: return
        runCatching { c[key.configKey] = value }
            .onFailure { Log.w(TAG, "${key.configKey}=$value abgelehnt: ${it.message}") }
            .onSuccess { notifyConfigChanged() }
    }

    // --- Werkzeuge --------------------------------------------------------

    fun clearBed() {
        if (!checkBedUnlocked(activeBedIndex(), "Leeren")) return
        runCatching { core?.clearBed() }.onFailure { Log.w(TAG, "Bett leeren", it) }
        refreshObjects()
        invalidateSliceResult()
    }

    /** Leeres Projekt mit einem Bett; der vorherige Stand bleibt undo-fähig. */
    fun newProject() {
        val c = core ?: return
        runCatching { c.clear() }
            .onFailure { Log.w(TAG, "Neues Projekt", it) }
            .onSuccess {
                setProjectKey(null)
                // Sonst holt der naechste Start den eben verworfenen
                // Stand ueber den Arbeitsstand wieder herein.
                runCatching { autosaveFile.delete() }
                refreshObjects()
                invalidateSliceResult()
                showBed()
            }
    }

    /** Exportiert atomar in eine neue Cache-Datei; Activity kopiert sie ins SAF-Ziel. */
    fun saveProjectFile(): File {
        val c = ensureCore()
        val dir = File(cacheDir, "projects").apply { mkdirs() }
        val file = File(dir, "project-${System.nanoTime()}.3mf")
        c.saveProject(file.absolutePath)
        dir.listFiles()?.forEach { if (it != file) it.delete() }
        return file
    }

    fun suggestedProjectName(): String {
        val stem = _objects.value.firstOrNull()?.name
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "PSMobile-Projekt"
        return stem.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".3mf"
    }

    fun exportPlateFile(format: PsmCore.PlateFormat): File {
        val c = ensureCore()
        val dir = File(cacheDir, "plate-export").apply { mkdirs() }
        val extension =
            if (format == PsmCore.PlateFormat.STL) "stl" else "obj"
        val file = File(dir, "druckbett-${System.nanoTime()}.$extension")
        c.exportPlate(file.absolutePath, format)
        dir.listFiles()?.forEach { if (it != file) it.delete() }
        return file
    }

    fun repairStlFile(input: File): File {
        val c = ensureCore()
        val dir = File(cacheDir, "stl-repair").apply { mkdirs() }
        val file = File(dir, "repariert-${System.nanoTime()}.stl")
        c.repairStl(input.absolutePath, file.absolutePath)
        dir.listFiles()?.forEach { if (it != file) it.delete() }
        return file
    }

    fun convertGcodeFile(input: File, toBinary: Boolean): File {
        val c = ensureCore()
        val dir = File(cacheDir, "gcode-convert").apply { mkdirs() }
        val extension = if (toBinary) "bgcode" else "gcode"
        val file = File(dir, "konvertiert-${System.nanoTime()}.$extension")
        c.convertGcode(input.absolutePath, file.absolutePath, toBinary)
        dir.listFiles()?.forEach { if (it != file) it.delete() }
        return file
    }

    fun undo() {
        val c = core ?: return
        runCatching { c.undo() }
            .onFailure { Log.w(TAG, "Rückgängig", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                showBed()
            }
    }

    fun redo() {
        val c = core ?: return
        runCatching { c.redo() }
            .onFailure { Log.w(TAG, "Wiederholen", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                showBed()
            }
    }

    fun selectBed(index: Int) {
        val c = core ?: return
        runCatching { c.selectBed(index) }
            .onFailure { Log.w(TAG, "Bett ${index + 1} waehlen", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
            }
    }

    fun addBed() {
        val c = core ?: return
        runCatching { c.addBed() }
            .onFailure { Log.w(TAG, "Bett anlegen", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
            }
    }

    fun removeBed(index: Int) {
        val c = core ?: return
        if (!checkBedUnlocked(index, "Entfernen")) return
        runCatching { c.removeBed(index) }
            .onFailure { Log.w(TAG, "Bett ${index + 1} entfernen", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
            }
    }

    fun moveObjectToBed(id: Int, target: Int) {
        val c = core ?: return
        if (!checkBedUnlocked(target, "Verschieben")) return
        if (!checkBedUnlocked(activeBedIndex(), "Verschieben")) return
        runCatching { c.moveObjectToBed(id, target) }
            .onFailure { Log.w(TAG, "Objekt auf Bett ${target + 1} verschieben", it) }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
            }
    }

    fun arrange() {
        if (!checkBedUnlocked(activeBedIndex(), "Anordnen")) return
        runCatching { core?.arrange() }
            .onFailure { Log.w(TAG, "Anordnen", it) }
            .onSuccess { refreshObjects(); invalidateSliceResult() }
    }

    fun dropToBed(id: Int) {
        if (!checkBedUnlocked(activeBedIndex(), "Aufs Bett legen")) return
        runCatching { core?.dropToBed(id) }
            .onFailure { Log.w(TAG, "Aufs Bett legen", it) }
            .onSuccess { refreshObjects(); invalidateSliceResult() }
    }

    fun duplicate(id: Int, targetBed: Int? = null): Boolean {
        val c = core ?: return false
        if (!checkBedUnlocked(activeBedIndex(), "Duplizieren")) return false
        if (targetBed != null && !checkBedUnlocked(targetBed, "Duplizieren")) return false
        return runCatching {
            val copy = c.duplicate(id)
            if (targetBed != null)
                c.moveObjectToBed(copy, targetBed)
        }
            .fold(
                onSuccess = {
                    refreshObjects()
                    invalidateSliceResult()
                    true
                },
                onFailure = {
                    Log.w(TAG, "Duplizieren", it)
                    false
                },
            )
    }

    // --- An einen Drucker senden ------------------------------------------

    private val _sendState = MutableStateFlow<String?>(null)
    val sendState: StateFlow<String?> = _sendState.asStateFlow()

    fun clearSendState() { _sendState.value = null }

    /**
     * Schickt den zuletzt erzeugten G-Code an einen PrusaLink-Drucker und
     * sichert ihn, falls ein Sicherungsordner eingerichtet ist.
     *
     * Die Sicherung laeuft auch dann, wenn der Upload scheitert - der
     * G-Code ist ja trotzdem entstanden, und ihn zu verlieren waere
     * aergerlicher als ein fehlgeschlagener Upload.
     */
    fun sendToPrinter(printer: de.psmobile.net.PrusaLink.Printer, printAfter: Boolean) {
        val gcode = lastGcode
        if (core?.sliceState() != PsmCore.SliceState.DONE ||
            gcode == null || !gcode.exists()) {
            _sendState.value = "Kein aktueller G-Code vorhanden – erneut slicen"
            return
        }

        scope.launch {
            _sendState.value = "Sende an ${printer.name}…"

            val remote = suggestedGcodeName()
            val r = withContext(Dispatchers.IO) {
                de.psmobile.net.PrusaLink.upload(
                    printer,
                    gcode,
                    remote,
                    printAfter,
                )
            }
            var msg = when (r) {
                is de.psmobile.net.PrusaLink.Result.Ok -> r.message
                is de.psmobile.net.PrusaLink.Result.Error -> "Fehler: ${r.message}"
            }

            if (de.psmobile.net.BackupStore.isConfigured(this@SlicerService)) {
                val b = withContext(Dispatchers.IO) {
                    de.psmobile.net.BackupStore.archive(
                        this@SlicerService,
                        gcode,
                        printer.name,
                    )
                }
                msg += if (b.ok) "  ·  gesichert" else "  ·  Sicherung: ${b.message}"
            }

            _sendState.value = msg
        }
    }

    /** Import-Fehler in die Oberflaeche durchreichen statt verschlucken. */
    fun reportImportError(t: Throwable) {
        Log.e(TAG, "Import fehlgeschlagen", t)
        _progress.value = Progress.Failed(t.message ?: "Import fehlgeschlagen")
    }

    /**
     * Einen einzelnen Parameter setzen - fuer die Schnellzugriffe in der
     * Seitenleiste. Derselbe Weg wie auf den Einstellungsseiten, also
     * landet der Wert im bearbeiteten Preset.
     */
    fun setConfig(key: String, value: String) {
        val c = core ?: return
        runCatching { c[key] = value }
            .onFailure { Log.w(TAG, "$key=$value abgelehnt: ${it.message}") }
            .onSuccess { notifyConfigChanged() }
    }

    /* --- Objekt bearbeiten ------------------------------------------- */
    /*
     * Alle Handgriffe gehen ueber denselben Weg: aendern, Objektliste neu
     * lesen, Szene fuer ungueltig erklaeren. refreshObjects erledigt das
     * Zweite und Dritte bereits.
     */

    private fun withObject(
        id: Int,
        what: String,
        clearMessage: Boolean = true,
        block: (PsmCore) -> Unit,
    ) {
        val c = core ?: return
        if (clearMessage)
            _toolMessage.value = null
        val undoBefore = c.historyState().undoCount
        runCatching {
            c.beginHistory(what)
            try {
                block(c)
            } finally {
                c.endHistory()
            }
        }
            .onFailure {
                runCatching {
                    if (c.historyState().undoCount > undoBefore)
                        c.undo()
                }
                refreshObjects()
                Log.w(TAG, "$what: ${it.message}")
                // PsmCore liefert bereits „<Werkzeug> fehlgeschlagen: …“.
                // Nicht noch einmal voranstellen, sonst wird die Meldung
                // auf dem schmalen sichtbaren Arbeitsbereich unnötig lang.
                val prefix = "$what fehlgeschlagen: "
                val reason = it.message.orEmpty().removePrefix(prefix)
                    .ifBlank { "Unbekannter Fehler" }
                _toolMessage.value = "$prefix$reason"
            }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                if (_toolMessage.value == null ||
                    _toolMessage.value == "$what läuft …")
                    _toolMessage.value = what
            }
    }

    /**
     * Mesh-Neuberechnungen dürfen den Compose-Hauptthread nicht anhalten.
     * Zugleich läuft höchstens eine schwere Mutation, damit zwei schnelle
     * Fingertipps nicht zwei Emboss-/Simplify-Jobs übereinander starten.
     */
    private fun withObjectAsync(
        id: Int,
        what: String,
        block: (PsmCore) -> Unit,
    ) {
        if (!heavyMutationActive.compareAndSet(false, true)) {
            _toolMessage.value = "Bitte warten – eine Geometrieoperation läuft bereits."
            return
        }
        _toolMessage.value = "$what läuft …"
        scope.launch {
            try {
                withObject(id, what, clearMessage = false, block = block)
            } finally {
                heavyMutationActive.set(false)
            }
        }
    }

    private fun withObjects(
        ids: Collection<Int>,
        what: String,
        block: (PsmCore, Int) -> Unit,
    ) {
        val c = core ?: return
        val stableIds = ids.distinct()
        if (stableIds.isEmpty()) return
        val undoBefore = c.historyState().undoCount
        runCatching {
            c.beginHistory(what)
            try {
                stableIds.forEach { id -> block(c, id) }
            } finally {
                c.endHistory()
            }
        }
            .onFailure {
                runCatching {
                    if (c.historyState().undoCount > undoBefore)
                        c.undo()
                }
                refreshObjects()
                Log.w(TAG, "$what: ${it.message}")
                _toolMessage.value = "$what fehlgeschlagen: ${it.message}"
            }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                _toolMessage.value = "$what · ${stableIds.size} Objekt" +
                    if (stableIds.size == 1) "" else "e"
            }
    }

    fun removeObjects(ids: Collection<Int>) =
        withObjects(ids, "Auswahl löschen") { c, id -> c.removeModel(id) }

    fun dropToBed(ids: Collection<Int>) =
        withObjects(ids, "Auswahl aufs Bett legen") { c, id -> c.dropToBed(id) }

    fun mirror(ids: Collection<Int>, axis: PsmCore.Axis) =
        withObjects(ids, "Auswahl spiegeln") { c, id -> c.mirror(id, axis) }

    fun duplicateObjects(ids: Collection<Int>, targetBed: Int? = null) =
        withObjects(ids, "Auswahl duplizieren") { c, id ->
            val copy = c.duplicate(id)
            if (targetBed != null)
                c.moveObjectToBed(copy, targetBed)
        }

    /** Gleichmaessig auf einen Faktor setzen (1.0 = Originalgroesse). */
    fun setUniformScale(id: Int, factor: Float) =
        withObject(id, "Skalieren") { it.setScale(id, factor, factor, factor); it.dropToBed(id) }

    /** Laengste Kante auf ein Mass bringen. */
    fun scaleToSize(id: Int, mm: Float) =
        withObject(id, "Auf Mass skalieren") { it.scaleToFit(id, mm); it.dropToBed(id) }

    /** So gross wie das Bett es zulaesst. */
    fun scaleToBed(id: Int) =
        withObject(id, "Aufs Bett einpassen") { it.fitToBed(id, 0.9f) }

    fun setRotationAxis(id: Int, axis: Int, degrees: Float) =
        withObject(id, "Drehen") { c ->
            val o = c.objectInfo(id) ?: return@withObject
            val r = o.rotation
            val x = if (axis == 0) degrees else r.first
            val y = if (axis == 1) degrees else r.second
            val z = if (axis == 2) degrees else r.third
            c.setRotation(id, x, y, z)
            c.dropToBed(id)
        }

    /** Um einen Betrag weiterdrehen, fuer die Vierteldrehungen. */
    fun rotateBy(id: Int, axis: Int, degrees: Float) =
        withObject(id, "Weiterdrehen") { c ->
            val o = c.objectInfo(id) ?: return@withObject
            val r = o.rotation
            val cur = when (axis) { 0 -> r.first; 1 -> r.second; else -> r.third }
            val next = ((cur + degrees) % 360f + 360f) % 360f
            val x = if (axis == 0) next else r.first
            val y = if (axis == 1) next else r.second
            val z = if (axis == 2) next else r.third
            c.setRotation(id, x, y, z)
            c.dropToBed(id)
        }

    fun mirror(id: Int, axis: PsmCore.Axis) = withObject(id, "Spiegeln") { it.mirror(id, axis) }

    fun setInstances(id: Int, count: Int) =
        withObject(id, "Kopien") { it.setInstances(id, count) }

    fun setObjectExtruder(id: Int, extruder: Int) =
        withObject(id, "Objekt-Extruder") {
            it.setObjectExtruder(id, extruder)
        }

    fun setVolumeExtruder(id: Int, volume: Int, extruder: Int) =
        withObject(id, "Volumen-Extruder") {
            it.setVolumeExtruder(id, volume, extruder)
        }

    fun splitIntoObjects(id: Int) =
        withObjectAsync(id, "In Objekte teilen") { it.splitObjects(id) }

    fun splitIntoVolumes(id: Int) =
        withObjectAsync(id, "In Volumen teilen") { it.splitVolumes(id) }

    fun cutObject(
        id: Int,
        zMm: Float,
        keepUpper: Boolean,
        keepLower: Boolean,
        keepAsParts: Boolean,
    ) = withObjectAsync(id, "Schneiden") {
        it.cutZ(id, zMm, keepUpper, keepLower, keepAsParts)
    }

    fun simplifyObject(id: Int, remainingRatio: Float) =
        withObjectAsync(id, "Vereinfachen") {
            val result = it.simplify(id, remainingRatio.coerceIn(0.01f, 1f))
            _toolMessage.value =
                "Vereinfacht: ${result.before} → ${result.after} Dreiecke"
        }

    fun addPrimitiveVolume(
        id: Int,
        type: PsmCore.VolumeType,
        shape: PsmCore.PrimitiveShape,
        x: Float,
        y: Float,
        z: Float,
    ) = withObject(id, "Volumen hinzufügen") {
        it.addPrimitiveVolume(id, type, shape, x, y, z)
    }

    fun removeVolume(id: Int, volume: Int) =
        withObject(id, "Volumen entfernen") { it.removeVolume(id, volume) }

    fun addTextVolume(
        id: Int,
        text: String,
        sizeMm: Float,
        depthMm: Float,
        type: PsmCore.VolumeType,
    ) = withObjectAsync(id, "Text prägen") {
        it.addTextVolume(
            id = id,
            text = text,
            fontPath = "/system/fonts/Roboto-Regular.ttf",
            sizeMm = sizeMm,
            depthMm = depthMm,
            type = type,
        )
    }

    fun addSvgVolume(
        id: Int,
        svg: File,
        depthMm: Float,
        type: PsmCore.VolumeType,
    ) = withObjectAsync(id, "SVG prägen") {
        it.addSvgVolume(id, svg.absolutePath, depthMm, type)
    }

    fun layOnFacet(hit: de.psmobile.core.PsmViewport.SurfaceHit) =
        withObject(hit.objectId, "Auf Fläche legen") {
            it.layOnFacet(hit.objectId, hit.volumeIndex, hit.facetIndex)
        }

    fun paintFacet(
        hit: de.psmobile.core.PsmViewport.SurfaceHit,
        tool: PsmCore.PaintTool,
        state: Int,
        radiusMm: Float,
    ) = withObjectAsync(hit.objectId, "Fläche bemalen") {
        it.paintFacet(
            hit.objectId, hit.volumeIndex, hit.facetIndex,
            tool, state, radiusMm,
        )
    }

    fun clearPaint(id: Int, tool: PsmCore.PaintTool) =
        withObject(id, "Bemalung löschen") { it.clearPaint(id, tool) }

    fun layerProfile(id: Int): List<Pair<Double, Double>> =
        core?.layerProfile(id).orEmpty()

    fun setLayerProfile(id: Int, values: List<Pair<Double, Double>>) =
        withObject(id, "Variable Schichthöhe") {
            it.setLayerProfile(id, values)
        }

    fun setObjectColour(id: Int, colour: String) =
        withObject(id, "Objektfarbe") { it.setObjectColour(id, colour) }

    fun setObjectWipe(id: Int, intoInfill: Boolean, intoObjects: Boolean) =
        withObject(id, "Wischoptionen") {
            it.setObjectWipe(id, intoInfill, intoObjects)
        }

    fun customGcodes(): List<PsmCore.CustomGcode> =
        core?.customGcodes().orEmpty()

    fun replaceCustomGcodes(values: List<PsmCore.CustomGcode>) {
        val c = core ?: return
        runCatching {
            c.beginHistory("Custom G-Code")
            try {
                c.clearCustomGcode()
                values.sortedBy { it.printZ }.forEach(c::addCustomGcode)
            } finally {
                c.endHistory()
            }
        }
            .onFailure {
                Log.w(TAG, "Custom G-Code: ${it.message}")
                _toolMessage.value = "Custom G-Code fehlgeschlagen: ${it.message}"
            }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                _toolMessage.value = "Custom G-Code gespeichert"
            }
    }

    fun wipeTower(): PsmCore.WipeTower =
        core?.wipeTower() ?: PsmCore.WipeTower(0f, 0f, 0f)

    fun setWipeTower(value: PsmCore.WipeTower) {
        val c = core ?: return
        runCatching { c.setWipeTower(value) }
            .onFailure {
                Log.w(TAG, "Wipe-Tower: ${it.message}")
                _toolMessage.value = "Wipe-Tower fehlgeschlagen: ${it.message}"
            }
            .onSuccess {
                refreshObjects()
                invalidateSliceResult()
                _toolMessage.value = "Wipe-Tower gespeichert"
            }
    }

    fun refreshObjects() {
        val c = core ?: return
        // IntArray kennt kein mapNotNull - erst in eine Liste ueberfuehren.
        val values = c.listObjects().toList().mapNotNull { c.objectInfo(it) }
        _objects.value = values
        _volumes.value = values.associate { it.id to c.volumes(it.id) }
        refreshBeds()
        refreshHistory()
        bumpScene()
    }

    private fun refreshBeds() {
        val c = core ?: return
        _beds.value = c.beds()
    }

    private fun refreshHistory() {
        val c = core ?: return
        _history.value = c.historyState()
    }

    enum class ImportMode { OBJECTS, PROJECT }

    /**
     * Importiert eine normale Modelldatei oder eine 3MF in dem vom Nutzer
     * gewaehlten Modus. Ein Projekt ersetzt das Bett und aktualisiert
     * unmittelbar alle Preset-Anzeigen auf die eingebettete Auswahl.
     */
    fun loadModel(path: String, mode: ImportMode = ImportMode.OBJECTS): PsmCore.ProjectImport? {
        val c = ensureCore()
        val project = when (mode) {
            ImportMode.OBJECTS -> {
                c.loadModel(path)
                null
            }
            ImportMode.PROJECT -> c.loadProject(path)
        }
        refreshObjects()
        if (project != null) {
            setProjectKey(path)
            refreshPresets()
            refreshQuickSettings()
            bumpConfig()
        }
        invalidateSliceResult()
        // Nach einem Import gehoert die Aufmerksamkeit aufs Bett - sonst
        // laedt das Modell unsichtbar hinter einem anderen Bildschirm.
        showBed()
        return project
    }

    fun removeObject(id: Int) {
        if (!checkBedUnlocked(activeBedIndex(), "Löschen")) return
        core?.removeModel(id)
        refreshObjects()
        invalidateSliceResult()
    }

    fun startSlice() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, SlicerService::class.java).setAction(ACTION_START_SLICE),
        )
    }

    private fun runSlice(startId: Int) {
        if (_progress.value is Progress.Running) return

        invalidateSliceResult()
        val t0 = System.nanoTime()

        scope.launch {
            try {
                val c = ensureCore()
                val memory = sliceMemoryDecision(c)
                if (memory.blocked) {
                    val needMb = memory.estimatedPeakBytes / (1024 * 1024)
                    val budgetMb = memory.safeBudgetBytes / (1024 * 1024)
                    _progress.value = Progress.Failed(
                        getString(R.string.memory_blocked, needMb, budgetMb)
                    )
                    return@launch
                }
                if (cancelRequestedByCommand) {
                    _progress.value = Progress.Cancelled
                    return@launch
                }
                c.startSlice { percent, stage ->
                    _progress.value = Progress.Running(percent, stage)
                    updateNotification(percent, stage)
                    false
                }
                when (c.awaitSlice()) {
                    PsmCore.SliceState.DONE -> {
                        val out = File(filesDir, "last.gcode")
                        c.exportGcode(out.absolutePath)
                        lastGcode = out
                        val secs = (System.nanoTime() - t0) / 1_000_000_000.0
                        _progress.value = Progress.Done(c.sliceStats(), secs)
                    }
                    PsmCore.SliceState.CANCELLED -> _progress.value = Progress.Cancelled
                    PsmCore.SliceState.STALE -> {
                        lastGcode = null
                        _progress.value = Progress.Stale
                    }
                    else -> _progress.value = Progress.Failed(c.lastError())
                }
            } catch (t: Throwable) {
                _progress.value = Progress.Failed(t.message ?: "unbekannter Fehler")
            } finally {
                sliceCommandActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(latestCommandStartId.coerceAtLeast(startId))
            }
        }
    }

    fun cancelSlice() {
        startService(
            Intent(this, SlicerService::class.java).setAction(ACTION_CANCEL_SLICE)
        )
    }

    private fun sliceMemoryDecision(c: PsmCore): SliceMemoryPolicy.Decision {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val currentPssBytes = android.os.Debug.getPss().toLong() * 1024L
        return SliceMemoryPolicy.evaluate(
            estimatedPeakBytes = c.estimatedSliceMemory(),
            totalDeviceBytes = info.totalMem,
            availableDeviceBytes = info.availMem,
            currentProcessBytes = currentPssBytes,
            systemLowMemory = info.lowMemory,
        )
    }

    /** Geschaetzter Spitzenspeicher gegen das reale native Geraetebudget. */
    fun memoryWarning(): String? {
        val c = core ?: return null
        val memory = sliceMemoryDecision(c)
        if (memory.blocked || memory.warning) {
            val needMb = memory.estimatedPeakBytes / (1024 * 1024)
            val budgetMb = memory.safeBudgetBytes / (1024 * 1024)
            return if (memory.blocked)
                getString(R.string.memory_blocked, needMb, budgetMb)
            else
                getString(R.string.memory_warning, needMb, budgetMb)
        }
        return null
    }

    // --- Benachrichtigung -------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_slicing),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(percent: Int, stage: String): Notification {
        val tap = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = android.app.PendingIntent.getService(
            this, 1,
            Intent(this, SlicerService::class.java).setAction(ACTION_CANCEL_SLICE),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.slicing))
            .setContentText(stage)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setContentIntent(tap)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel_slice),
                cancel,
            )
            .build()
    }

    private fun updateNotification(percent: Int, stage: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(percent, stage))
    }
}
