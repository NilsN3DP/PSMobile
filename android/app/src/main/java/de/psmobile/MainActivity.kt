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
import androidx.activity.result.contract.ActivityResultContracts
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
import de.psmobile.core.PsmCore
import de.psmobile.ui.SlicerScreen
import de.psmobile.ui.theme.PSMobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<SlicerService?>(null)
    private var pending3mf by mutableStateOf<File?>(null)
    private var importNotice by mutableStateOf<String?>(null)
    private var noticeTitle by mutableStateOf("Projekt importiert")
    private var currentProjectUri by mutableStateOf<Uri?>(null)
    private var pendingFileOutput: File? = null
    private var pendingFileName: String = "PSMobile-Datei"
    private var pendingFileDescription: String = "Datei"
    private var pendingSvgTarget:
        Triple<Int, Float, PsmCore.VolumeType>? = null

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

    /**
     * Zielordner fuer die Sicherung. Ueber das Storage Access Framework,
     * damit auch eingebundene Netzlaufwerke funktionieren - ohne
     * Speicherberechtigung und ohne eigenes SMB im Code.
     */
    private val backupPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            de.psmobile.net.PrinterStore.setBackupTree(this, uri.toString())
        }
    }

    private val projectCreator = registerForActivityResult(
        ActivityResultContracts.CreateDocument("model/3mf")
    ) { uri ->
        if (uri != null)
            writeProject(uri)
    }

    private val fileCreator = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null)
            writePendingFile(uri)
        else
            pendingFileOutput = null
    }

    private val repairStlPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(::repairStl)
    }

    private val convertGcodePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(::convertGcode)
    }

    private val svgPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(::addSvgToObject)
            ?: run { pendingSvgTarget = null }
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
                        onLanguageChange = { svc.uiLanguage = it },
                        preselected = svc.installedPrinters(),
                    )
                } else {
                    SlicerScreen(
                        service = svc,
                        onPickFile = { uri -> importUri(uri) },
                        onShare = { uri -> shareGcode(uri) },
                        onPickBackupFolder = { backupPicker.launch(null) },
                        onNewProject = {
                            svc?.newProject()
                            currentProjectUri = null
                            noticeTitle = "Neues Projekt"
                            importNotice = "Ein leeres Projekt mit einem Druckbett wurde angelegt."
                        },
                        onSaveProject = { saveProject(saveAs = false) },
                        onSaveProjectAs = { saveProject(saveAs = true) },
                        onExportPlate = { exportPlate(it) },
                        onRepairStl = {
                            repairStlPicker.launch(
                                arrayOf(
                                    "model/stl",
                                    "application/sla",
                                    "application/octet-stream",
                                )
                            )
                        },
                        onConvertGcode = {
                            convertGcodePicker.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/octet-stream",
                                )
                            )
                        },
                        onAddSvg = { objectId, depth, type ->
                            pendingSvgTarget = Triple(objectId, depth, type)
                            svgPicker.launch(
                                arrayOf("image/svg+xml", "text/xml")
                            )
                        },
                    )
                }

                pending3mf?.let { file ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pending3mf = null },
                        title = {
                            androidx.compose.material3.Text("3MF importieren")
                        },
                        text = {
                            androidx.compose.material3.Text(
                                "Soll „${file.name.substringAfter('-', file.name)}“ nur seine " +
                                    "3D-Objekte zum aktuellen Bett hinzufügen oder als vollständiges " +
                                    "Projekt mit Positionen und Druckprofil geöffnet werden?"
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    import3mf(file, SlicerService.ImportMode.PROJECT)
                                },
                            ) {
                                androidx.compose.material3.Text("Als Projekt")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    import3mf(file, SlicerService.ImportMode.OBJECTS)
                                },
                            ) {
                                androidx.compose.material3.Text("Nur 3D-Objekte")
                            }
                        },
                    )
                }

                importNotice?.let { message ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { importNotice = null },
                        title = {
                            androidx.compose.material3.Text(noticeTitle)
                        },
                        text = {
                            androidx.compose.material3.Text(message)
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { importNotice = null },
                            ) {
                                androidx.compose.material3.Text("OK")
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    /** Verhindert, dass derselbe Intent zweimal ausgewertet wird. */
    private var handledIntent: Intent? = null

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent === handledIntent) return
        handledIntent = intent

        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> sharedStream(intent)
            else -> null
        }
        uri?.let { importUri(it) }
    }

    private fun sharedStream(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
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
                    val safeName = name
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .ifBlank { "modell.stl" }
                    val dest = File(cacheDir, "import").apply { mkdirs() }
                        .resolve("${System.nanoTime()}-$safeName")
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: error("Datei nicht lesbar: $uri")
                    dest
                }
            }
            // Fehler muessen sichtbar werden. Vorher verschluckte ein
            // blankes runCatching sie, und in der UI passierte wortlos
            // nichts - der schlimmste Fehlerzustand ueberhaupt.
            val file = result.getOrElse {
                svc.reportImportError(it)
                return@launch
            }
            val mime = contentResolver.getType(uri).orEmpty()
            val is3mf = file.extension.equals("3mf", ignoreCase = true) ||
                mime.contains("3mf", ignoreCase = true) ||
                mime.contains("3dmanufacturing", ignoreCase = true)
            if (is3mf) {
                pending3mf = file
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { svc.loadModel(file.absolutePath) }
                }.onFailure { svc.reportImportError(it) }
            }
        }
    }

    private fun import3mf(file: File, mode: SlicerService.ImportMode) {
        val svc = service ?: return
        pending3mf = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { svc.loadModel(file.absolutePath, mode) }
            }
            result
                .onFailure { svc.reportImportError(it) }
                .onSuccess { project ->
                    if (project != null) {
                        val profile = project.selectedPrinter.ifBlank {
                            project.requestedPrinter
                        }
                        val details = when {
                            !project.configLoaded ->
                                "Die 3MF enthielt keine Projektkonfiguration. Die Objekte wurden " +
                                    "mit ihrer gespeicherten Anordnung übernommen."
                            project.exactInstalledPrinter ->
                                "Das passende Druckerprofil „$profile“ wurde automatisch ausgewählt."
                            else ->
                                "Die eingebettete Druckerkonfiguration wurde als projektlokales " +
                                    "Profil „$profile“ aktiviert."
                        }
                        val beds = if (project.bedCount > 1) {
                            "\n\n${project.bedCount} Druckbetten wurden übernommen und können " +
                                "oben direkt ausgewählt werden."
                        } else {
                            ""
                        }
                        importNotice = if (project.postProcessRemoved) {
                            "$details$beds\n\nEin eingebettetes Post-Processing-Skript wurde aus " +
                                "Sicherheitsgründen nicht übernommen."
                        } else {
                            details + beds
                        }
                        noticeTitle = "Projekt importiert"
                        currentProjectUri = null
                    }
                }
        }
    }

    private fun saveProject(saveAs: Boolean) {
        val svc = service ?: return
        val current = currentProjectUri
        if (saveAs || current == null) {
            projectCreator.launch(svc.suggestedProjectName())
        } else {
            writeProject(current)
        }
    }

    /**
     * Der Core schreibt auf einen normalen Dateipfad; das Storage Access
     * Framework kann dagegen Drive, SMB oder einen Dokumentanbieter
     * liefern. Deshalb entsteht zuerst eine sichere Cache-3MF und wird
     * anschließend in das gewählte Ziel gestreamt.
     */
    private fun writeProject(uri: Uri) {
        val svc = service ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = svc.saveProjectFile()
                    val output = runCatching {
                        contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull() ?: contentResolver.openOutputStream(uri, "w")
                    output?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    } ?: error("Projektziel ist nicht beschreibbar: $uri")
                    source.length()
                }
            }
            result
                .onFailure { svc.reportImportError(it) }
                .onSuccess { bytes ->
                    currentProjectUri = uri
                    noticeTitle = "Projekt gespeichert"
                    val name = queryDisplayName(uri) ?: svc.suggestedProjectName()
                    importNotice = "„$name“ wurde als vollständiges 3MF-Projekt " +
                        "mit allen belegten Druckbetten gespeichert " +
                        "(${bytes / 1024} KiB)."
                }
        }
    }

    private fun exportPlate(format: PsmCore.PlateFormat) {
        val svc = service ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { svc.exportPlateFile(format) }
            }
            result
                .onFailure { showFileError("Bettexport", it) }
                .onSuccess { file ->
                    pendingFileOutput = file
                    pendingFileName =
                        if (format == PsmCore.PlateFormat.STL)
                            "PSMobile-Druckbett.stl"
                        else "PSMobile-Druckbett.obj"
                    pendingFileDescription =
                        if (format == PsmCore.PlateFormat.STL)
                            "STL-Bettexport"
                        else "OBJ-Bettexport"
                    fileCreator.launch(pendingFileName)
                }
        }
    }

    private fun repairStl(uri: Uri) {
        val svc = service ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = copyDocumentToCache(uri, "repair-input")
                    svc.repairStlFile(source)
                }
            }
            result
                .onFailure { showFileError("STL-Reparatur", it) }
                .onSuccess { file ->
                    val sourceName =
                        queryDisplayName(uri)?.substringBeforeLast('.')
                            ?.takeIf { it.isNotBlank() }
                            ?: "Modell"
                    pendingFileOutput = file
                    pendingFileName = "$sourceName-repariert.stl"
                    pendingFileDescription = "reparierte STL"
                    fileCreator.launch(pendingFileName)
                }
        }
    }

    private fun convertGcode(uri: Uri) {
        val svc = service ?: return
        lifecycleScope.launch {
            val sourceName =
                queryDisplayName(uri) ?: uri.lastPathSegment ?: "print.gcode"
            val toBinary = !sourceName.endsWith(".bgcode", ignoreCase = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = copyDocumentToCache(uri, "gcode-input")
                    svc.convertGcodeFile(source, toBinary)
                }
            }
            result
                .onFailure { showFileError("G-Code-Konvertierung", it) }
                .onSuccess { file ->
                    pendingFileOutput = file
                    val stem = sourceName.substringBeforeLast('.')
                        .ifBlank { "print" }
                    pendingFileName =
                        "$stem.${if (toBinary) "bgcode" else "gcode"}"
                    pendingFileDescription =
                        if (toBinary) "binärer BGCode" else "ASCII-G-Code"
                    fileCreator.launch(pendingFileName)
                }
        }
    }

    private fun addSvgToObject(uri: Uri) {
        val svc = service ?: return
        val target = pendingSvgTarget ?: return
        pendingSvgTarget = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = copyDocumentToCache(uri, "svg-input")
                    svc.addSvgVolume(
                        id = target.first,
                        svg = source,
                        depthMm = target.second,
                        type = target.third,
                    )
                }
            }
            result
                .onFailure { showFileError("SVG prägen", it) }
                .onSuccess {
                    noticeTitle = "SVG hinzugefügt"
                    importNotice =
                        "Die SVG-Kontur wurde als Volumen in das ausgewählte Objekt eingefügt."
                }
        }
    }

    private fun copyDocumentToCache(uri: Uri, folder: String): File {
        val name = queryDisplayName(uri)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.ifBlank { null }
            ?: "eingabe.dat"
        val dir = File(cacheDir, folder).apply { mkdirs() }
        val output = File(dir, "${System.nanoTime()}-$name")
        contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { out -> input.copyTo(out) }
        } ?: error("Datei nicht lesbar: $uri")
        return output
    }

    private fun writePendingFile(uri: Uri) {
        val source = pendingFileOutput ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("Dateiziel ist nicht beschreibbar: $uri")
                    source.length()
                }
            }
            result
                .onFailure { showFileError(pendingFileDescription, it) }
                .onSuccess { bytes ->
                    noticeTitle = "Datei gespeichert"
                    importNotice =
                        "$pendingFileDescription wurde gespeichert (${bytes / 1024} KiB)."
                }
            pendingFileOutput = null
        }
    }

    private fun showFileError(action: String, error: Throwable) {
        noticeTitle = "$action fehlgeschlagen"
        importNotice = error.message ?: "Unbekannter Dateifehler"
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
