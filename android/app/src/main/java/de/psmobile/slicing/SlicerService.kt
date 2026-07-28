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
import de.psmobile.MainActivity
import de.psmobile.R
import de.psmobile.core.PsmCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
    }

    inner class LocalBinder : Binder() {
        val service: SlicerService get() = this@SlicerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _objects = MutableStateFlow<List<PsmCore.ObjectInfo>>(emptyList())
    val objects: StateFlow<List<PsmCore.ObjectInfo>> = _objects.asStateFlow()

    /** Auswaehlbare Profile je Typ, plus die aktuelle Auswahl. */
    data class Presets(
        val printers: List<String> = emptyList(),
        val prints: List<String> = emptyList(),
        val filaments: List<String> = emptyList(),
        val selectedPrinter: String = "",
        val selectedPrint: String = "",
        val selectedFilament: String = "",
    )

    private val _presets = MutableStateFlow(Presets())
    val presets: StateFlow<Presets> = _presets.asStateFlow()

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
        val brim: String = "",
    )

    private val _quick = MutableStateFlow(QuickSettings())
    val quickSettings: StateFlow<QuickSettings> = _quick.asStateFlow()

    private var core: PsmCore? = null
    var lastGcode: File? = null
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
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
        val resDir = ResourceInstaller.ensureInstalled(this)
        val dataDir = File(filesDir, "psmdata").apply { mkdirs() }
        val c = PsmCore.create(dataDir.absolutePath, resDir.absolutePath)
        runCatching { c.loadBundledPresets() }
            .onFailure { Log.w(TAG, "Profile nicht geladen: ${it.message}") }
        core = c
        refreshPresets()
        refreshQuickSettings()
        return c
    }

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
        val dst = File(outDir, "psmobile.gcode")
        src.copyTo(dst, overwrite = true)
        return androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", dst
        )
    }

    fun refreshPresets() {
        val c = core ?: return
        _presets.value = Presets(
            printers = c.presetNames(PsmCore.PresetType.PRINTER),
            prints = c.presetNames(PsmCore.PresetType.PRINT),
            filaments = c.presetNames(PsmCore.PresetType.FILAMENT),
            selectedPrinter = c.selectedPreset(PsmCore.PresetType.PRINTER),
            selectedPrint = c.selectedPreset(PsmCore.PresetType.PRINT),
            selectedFilament = c.selectedPreset(PsmCore.PresetType.FILAMENT),
        )
    }

    /**
     * Profil waehlen.
     *
     * Die Reihenfolge ist nicht beliebig: Der Drucker bestimmt, welche
     * Druck- und Filamentprofile ueberhaupt kompatibel sind. Nach jeder
     * Auswahl muessen die Listen deshalb neu gelesen werden.
     */
    fun selectPreset(type: PsmCore.PresetType, name: String) {
        val c = core ?: return
        runCatching { c.selectPreset(type, name) }
            .onFailure { Log.w(TAG, "Preset '$name' nicht waehlbar: ${it.message}") }
        refreshPresets()
        refreshQuickSettings()   // ein anderes Profil bringt andere Werte mit
        refreshObjects()
    }

    fun refreshQuickSettings() {
        val c = core ?: return
        _quick.value = QuickSettings(
            layerHeight = c[QuickKey.LAYER_HEIGHT.configKey].orEmpty(),
            fillDensity = c[QuickKey.FILL_DENSITY.configKey].orEmpty(),
            supports = c[QuickKey.SUPPORTS.configKey].orEmpty(),
            brim = c[QuickKey.BRIM.configKey].orEmpty(),
        )
    }

    fun setQuick(key: QuickKey, value: String) {
        val c = core ?: return
        runCatching { c[key.configKey] = value }
            .onFailure { Log.w(TAG, "${key.configKey}=$value abgelehnt: ${it.message}") }
        refreshQuickSettings()
    }

    // --- Werkzeuge --------------------------------------------------------

    fun clearBed() {
        runCatching { core?.clear() }.onFailure { Log.w(TAG, "Bett leeren", it) }
        refreshObjects()
        _progress.value = Progress.Idle
    }

    fun arrange() {
        runCatching { core?.arrange() }.onFailure { Log.w(TAG, "Anordnen", it) }
        refreshObjects()
    }

    fun dropToBed(id: Int) {
        runCatching { core?.dropToBed(id) }.onFailure { Log.w(TAG, "Aufs Bett legen", it) }
        refreshObjects()
    }

    fun duplicate(id: Int) {
        runCatching { core?.duplicate(id) }.onFailure { Log.w(TAG, "Duplizieren", it) }
        refreshObjects()
    }

    /** Import-Fehler in die Oberflaeche durchreichen statt verschlucken. */
    fun reportImportError(t: Throwable) {
        Log.e(TAG, "Import fehlgeschlagen", t)
        _progress.value = Progress.Failed(t.message ?: "Import fehlgeschlagen")
    }

    fun refreshObjects() {
        val c = core ?: return
        // IntArray kennt kein mapNotNull - erst in eine Liste ueberfuehren.
        _objects.value = c.listObjects().toList().mapNotNull { c.objectInfo(it) }
    }

    fun loadModel(path: String) {
        val c = ensureCore()
        c.loadModel(path)
        refreshObjects()
    }

    fun removeObject(id: Int) {
        core?.removeModel(id)
        refreshObjects()
    }

    fun startSlice() {
        val c = ensureCore()
        if (_progress.value is Progress.Running) return

        startForeground(NOTIFICATION_ID, buildNotification(0, getString(R.string.slice_starting)))
        val t0 = System.nanoTime()

        scope.launch {
            try {
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
                    else -> _progress.value = Progress.Failed(c.lastError())
                }
            } catch (t: Throwable) {
                _progress.value = Progress.Failed(t.message ?: "unbekannter Fehler")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun cancelSlice() {
        core?.cancelSlice()
    }

    /** Geschaetzter Spitzenspeicher gegen das Budget dieses Geraets. */
    fun memoryWarning(): String? {
        val c = core ?: return null
        val need = c.estimatedSliceMemory()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val budgetMb = am.largeMemoryClass.toLong()
        val needMb = need / (1024 * 1024)
        return if (needMb > budgetMb * 0.8)
            getString(R.string.memory_warning, needMb, budgetMb)
        else null
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.slicing))
            .setContentText(stage)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    private fun updateNotification(percent: Int, stage: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(percent, stage))
    }
}
