package de.psmobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
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
import de.psmobile.ui.AppMode
import de.psmobile.ui.SimpleModeScreen
import de.psmobile.ui.WorkflowStartScreen
import de.psmobile.ui.theme.PSMobileTheme
import de.psmobile.ui.theme.PrusaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Native projects may refer to their temporary cache copy as the active
 * printer profile. Cache paths and nanosecond prefixes are implementation
 * details, not useful information in an import confirmation.
 */
internal fun importDisplayName(value: String): String =
    value.substringBefore(" (")
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("^\\d+-"), "")
        .ifBlank { "Projektprofil" }

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<SlicerService?>(null)
    private var pending3mf by mutableStateOf<File?>(null)
    private var pending3mfUri: Uri? = null
    private var importNotice by mutableStateOf<String?>(null)
    private var noticeTitle by mutableStateOf("Projekt importiert")
    private var currentProjectUri by mutableStateOf<Uri?>(null)
    private var pendingFileOutput: File? = null
    private var pendingFileName: String = "PSMobile-Datei"
    private var pendingFileDescription: String = "Datei"
    private var applyProfileUpdateWhenProjectSaved = false
    private var appMode by mutableStateOf<AppMode?>(null)
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

    private val modelPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> importUris(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Der Startbildschirm darf nicht kurz als helle Android-Fläche
        // aufblitzen, bevor Simple oder Advanced ihren Inhalt zeichnet.
        window.statusBarColor = PrusaColors.Background.value.toInt()
        window.navigationBarColor = PrusaColors.Background.value.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

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
                } else if (appMode == null) {
                    WorkflowStartScreen(
                        onSimple = { appMode = AppMode.SIMPLE },
                        onAdvanced = { appMode = AppMode.ADVANCED },
                        onAdvancedWizard = {
                            appMode = AppMode.ADVANCED
                            svc?.showScreen(SlicerService.Screen.Wizard)
                        },
                        onLanguageChange = { svc?.uiLanguage = it },
                    )
                } else if (appMode == AppMode.SIMPLE) {
                    SimpleModeScreen(
                        service = svc!!,
                        window = this@MainActivity.window,
                        onPickFile = { modelPicker.launch(arrayOf("model/3mf", "model/stl", "application/octet-stream")) },
                        onOpenAdvanced = { appMode = AppMode.ADVANCED },
                        onOpenPrinterSetup = { svc.reopenSetup() },
                        onStartSlice = { svc.startSlice() },
                    )
                } else {
                    SlicerScreen(
                        service = svc,
                        onOpenSimple = { appMode = AppMode.SIMPLE },
                        onPickFile = { uris -> importUris(uris) },
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
                        canReloadProject = currentProjectUri != null,
                        onReloadProject = ::reloadCurrentProject,
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

                var showProfileUpdateSaveWarning by remember { mutableStateOf(false) }
                val profileUpdate by (svc?.profileUpdates?.collectAsState()
                    ?: remember { mutableStateOf<de.psmobile.slicing.profileupdate.ProfileUpdateState>(de.psmobile.slicing.profileupdate.ProfileUpdateState.Idle) })
                when (val update = profileUpdate) {
                    is de.psmobile.slicing.profileupdate.ProfileUpdateState.Offer ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text("Neue Drucker- und Materialprofile verfügbar") },
                            text = { androidx.compose.material3.Text(update.manifest.releaseNotes.joinToString("\n• ", prefix = "• ")) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = { svc?.downloadProfileUpdate() }) { androidx.compose.material3.Text("Jetzt aktualisieren") } },
                            dismissButton = { androidx.compose.foundation.layout.Row {
                                androidx.compose.material3.TextButton(onClick = { svc?.deferProfileUpdate() }) { androidx.compose.material3.Text("Später") }
                                androidx.compose.material3.TextButton(onClick = { svc?.skipProfileUpdate() }) { androidx.compose.material3.Text("Erst beim nächsten Update fragen") }
                            } },
                        )
                    is de.psmobile.slicing.profileupdate.ProfileUpdateState.ReadyToApply ->
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text("Profile aktualisiert") },
                            text = { androidx.compose.material3.Text("Die neuen Profile sind geprüft und können jetzt oder beim nächsten Neustart verwendet werden.") },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                                if (svc?.profileUpdateNeedsSave() == true) showProfileUpdateSaveWarning = true
                                else svc?.applyStagedProfileUpdate()
                            }) { androidx.compose.material3.Text("Jetzt verwenden") } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { svc?.deferProfileUpdate() }) { androidx.compose.material3.Text("Beim Neustart") } },
                        )
                    else -> Unit
                }

                if (showProfileUpdateSaveWarning) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showProfileUpdateSaveWarning = false },
                        title = { androidx.compose.material3.Text("Projekt vor Profilwechsel speichern?") },
                        text = { androidx.compose.material3.Text("Es ist ein Modell geladen oder es gibt ungespeicherte Profiländerungen. Speichere das 3MF-Projekt, bevor die Slicer-Sitzung mit den neuen Profilen neu startet.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            showProfileUpdateSaveWarning = false
                            applyProfileUpdateWhenProjectSaved = true
                            saveProject(saveAs = false)
                        }) { androidx.compose.material3.Text("Projekt speichern & aktualisieren") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = {
                            showProfileUpdateSaveWarning = false
                            svc?.deferProfileUpdate()
                        }) { androidx.compose.material3.Text("Beim Neustart") } },
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

    /**
     * Android beendet einen Prozess im Hintergrund ohne Vorwarnung, und
     * onDestroy laeuft dann nicht mehr. onStop ist der letzte Zeitpunkt,
     * auf den man sich verlassen kann - deshalb wird der Arbeitsstand
     * hier gesichert, nicht erst beim Beenden.
     */
    override fun onStop() {
        runCatching { service?.autosave() }
        super.onStop()
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
    private fun importUri(uri: Uri) = importUris(listOf(uri))

    /**
     * Importiert die gesamte Auswahl der Reihe nach.
     *
     * Eine Baugruppe besteht selten aus genau einem Teil, deshalb darf
     * der Dateiwaehler mehrere Dateien liefern. Sie muessen aber
     * nacheinander durch den Core, nicht nebenlaeufig - sonst
     * ueberholen sich Kopiervorgang und Modellaufbau gegenseitig.
     *
     * Bei genau einer 3MF bleibt die gewohnte Rueckfrage "Nur Objekte
     * oder als Projekt". In einer Sammelauswahl entfaellt sie: ein
     * Projekt ersetzt das Bett und wuerde die uebrigen Dateien derselben
     * Auswahl wieder wegwerfen. Dort kommen 3MF-Dateien daher nur als
     * Objekte herein, und der Abschlusshinweis sagt das ausdruecklich.
     */
    private fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ask = de.psmobile.ui.ImportSelection.askAboutProject(uris.size)
        lifecycleScope.launch {
            var loaded = 0
            var projectsAsObjects = 0
            for (uri in uris) {
                when (importOne(uri, askAboutProject = ask)) {
                    ImportOutcome.FAILED -> Unit
                    ImportOutcome.LOADED -> loaded++
                    ImportOutcome.AWAITING_DECISION -> Unit
                    ImportOutcome.LOADED_PROJECT_AS_OBJECTS -> {
                        loaded++
                        projectsAsObjects++
                    }
                }
            }
            if (!ask) {
                noticeTitle = de.psmobile.ui.PsUi.appText("Files loaded", "Dateien geladen")
                importNotice = de.psmobile.ui.ImportSelection.summary(
                    loaded = loaded,
                    total = uris.size,
                    projectsAsObjects = projectsAsObjects,
                )
            }
        }
    }

    private enum class ImportOutcome {
        FAILED,
        LOADED,
        LOADED_PROJECT_AS_OBJECTS,

        /** 3MF wartet auf die Rueckfrage; der Hinweis kommt von dort. */
        AWAITING_DECISION,
    }

    private suspend fun importOne(uri: Uri, askAboutProject: Boolean): ImportOutcome {
        val svc = service ?: return ImportOutcome.FAILED
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "modell.stl"
                val safeName = name
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .ifBlank { "modell.stl" }
                // Die Eindeutigkeit gehoert in den Ordner, nicht in den
                // Dateinamen: libslic3r uebernimmt den Dateinamen als
                // Objektnamen, und im Objektbaum stand dadurch
                // "57617737825603-teil_01.stl" statt "teil_01.stl".
                val dest = File(cacheDir, "import/${System.nanoTime()}")
                    .apply { mkdirs() }
                    .resolve(safeName)
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
            return ImportOutcome.FAILED
        }
        val mime = contentResolver.getType(uri).orEmpty()
        val is3mf = file.extension.equals("3mf", ignoreCase = true) ||
            mime.contains("3mf", ignoreCase = true) ||
            mime.contains("3dmanufacturing", ignoreCase = true)
        if (is3mf && askAboutProject) {
            pending3mf = file
            pending3mfUri = uri
            return ImportOutcome.AWAITING_DECISION
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching { svc.loadModel(file.absolutePath, SlicerService.ImportMode.OBJECTS) }
        }.onFailure { svc.reportImportError(it) }.isSuccess
        return when {
            !ok -> ImportOutcome.FAILED
            is3mf -> ImportOutcome.LOADED_PROJECT_AS_OBJECTS
            else -> ImportOutcome.LOADED
        }
    }

    private fun import3mf(file: File, mode: SlicerService.ImportMode) {
        val svc = service ?: return
        val sourceUri = pending3mfUri
        pending3mf = null
        pending3mfUri = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { svc.loadModel(file.absolutePath, mode) }
            }
            result
                .onFailure { svc.reportImportError(it) }
                .onSuccess { project ->
                    if (mode == SlicerService.ImportMode.PROJECT) {
                        svc.setProjectKey(sourceUri?.toString() ?: file.absolutePath)
                    }
                    if (project != null) {
                        val profile = project.selectedPrinter.ifBlank {
                            project.requestedPrinter
                        }
                        val profileLabel = importDisplayName(profile)
                        val details = when {
                            !project.configLoaded ->
                                "Die 3MF enthielt keine Projektkonfiguration. Die Objekte wurden " +
                                    "mit ihrer gespeicherten Anordnung übernommen."
                            project.exactInstalledPrinter ->
                                "Das passende Druckerprofil „$profileLabel“ wurde automatisch ausgewählt."
                            else ->
                                "Die eingebettete Druckerkonfiguration wurde als projektlokales " +
                                    "Profil „$profileLabel“ aktiviert."
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
                        currentProjectUri = sourceUri
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

    /** Lädt die zuletzt gespeicherte bzw. als Projekt geöffnete 3MF erneut. */
    private fun reloadCurrentProject() {
        val svc = service ?: return
        val uri = currentProjectUri ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = copyDocumentToCache(uri, "project-reload")
                    svc.loadModel(source.absolutePath, SlicerService.ImportMode.PROJECT)
                }
            }
            result.onFailure { showFileError("Projekt neu laden", it) }
                .onSuccess { project ->
                    svc.setProjectKey(uri.toString())
                    noticeTitle = "Projekt neu geladen"
                    val name = queryDisplayName(uri) ?: "Projekt"
                    importNotice = "„$name“ wurde erneut vom Datenträger geladen" +
                        if (project?.bedCount ?: 1 > 1) {
                            " (${project?.bedCount} Druckbetten)."
                        } else "."
                }
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
                    svc.setProjectKey(uri.toString())
                    noticeTitle = "Projekt gespeichert"
                    val name = queryDisplayName(uri) ?: svc.suggestedProjectName()
                    importNotice = "„$name“ wurde als vollständiges 3MF-Projekt " +
                        "mit allen belegten Druckbetten gespeichert " +
                        "(${bytes / 1024} KiB)."
                    if (applyProfileUpdateWhenProjectSaved) {
                        applyProfileUpdateWhenProjectSaved = false
                        svc.applyStagedProfileUpdate()
                    }
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
