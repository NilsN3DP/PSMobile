package de.psmobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.psmobile.ui.PsUi
import de.psmobile.ui.SetupScreen
import androidx.lifecycle.lifecycleScope
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.SlicerScreen
import de.psmobile.ui.theme.PSMobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<SlicerService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SlicerService.LocalBinder).service
            // Session im Hintergrund anlegen - das Entpacken der Profile
            // dauert beim ersten Start ein paar hundert Millisekunden.
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { service?.ensureCore() }
            }
            handleIncomingIntent(intent)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bindService(
            Intent(this, SlicerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        setContent {
            PSMobileTheme {
                val svc = service
                // Uebernommene Oberflaechen-Daten laden; Englisch ist
                // Standard, siehe E-12.
                LaunchedEffect(svc) {
                    PsUi.load(this@MainActivity, svc?.uiLanguage ?: "en")
                }

                val setupNeeded by (svc?.setupNeeded?.collectAsState()
                    ?: remember { mutableStateOf(false) })

                if (svc != null && setupNeeded) {
                    val models by svc.printerModels.collectAsState()
                    val busy by svc.setupBusy.collectAsState()
                    SetupScreen(
                        models = models,
                        busy = busy,
                        onConfirm = { svc.completeSetup(it) },
                    )
                } else {
                    SlicerScreen(
                        service = svc,
                        onPickFile = { uri -> importUri(uri) },
                        onShare = { uri -> shareGcode(uri) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let { importUri(it) }
    }

    /**
     * Kopiert das Modell aus dem Content-Provider in den App-Speicher.
     *
     * Der Umweg ist noetig, weil libslic3r mit Dateipfaden arbeitet und
     * ein content://-URI keinen hat. Bei grossen Modellen ist das eine
     * spuerbare Kopie - deshalb im IO-Dispatcher, nicht im UI-Thread.
     */
    private fun importUri(uri: Uri) {
        val svc = service ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "modell.stl"
                    val dest = File(cacheDir, "import").apply { mkdirs() }.resolve(name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: error("Datei nicht lesbar: $uri")
                    svc.loadModel(dest.absolutePath)
                }
            }
            // Fehler muessen sichtbar werden. Vorher verschluckte ein
            // blankes runCatching sie, und in der UI passierte wortlos
            // nichts - der schlimmste Fehlerzustand ueberhaupt.
            result.onFailure { svc.reportImportError(it) }
        }
    }

    /** G-Code an Files, Drive, PrusaLink-Apps o. ae. weiterreichen. */
    private fun shareGcode(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"          // G-Code hat keinen eigenen MIME-Typ
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "G-Code teilen"))
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}
