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
        private const val CHANNEL_ID = "psm_slicing"
        private const val NOTIFICATION_ID = 1
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

    /** Legt die Session an, falls noch nicht geschehen. Blockierend, kurz. */
    fun ensureCore(): PsmCore {
        core?.let { return it }
        val resDir = ResourceInstaller.ensureInstalled(this)
        val dataDir = File(filesDir, "psmdata").apply { mkdirs() }
        val c = PsmCore.create(dataDir.absolutePath, resDir.absolutePath)
        runCatching { c.loadBundledPresets() }
        core = c
        return c
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
