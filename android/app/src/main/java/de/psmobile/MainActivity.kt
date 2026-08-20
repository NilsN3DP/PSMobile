package de.psmobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.storage.StorageManager
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
import de.psmobile.shared.rules.AppSettings
import androidx.compose.ui.unit.dp
import de.psmobile.ui.AppSettingsScreen
import de.psmobile.ui.PsUi
import de.psmobile.shared.rules.ModelFormats
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.RemovableStorage
import de.psmobile.ui.SceneController
import de.psmobile.ui.SetupScreen
import androidx.lifecycle.lifecycleScope
import de.psmobile.slicing.SlicerService
import de.psmobile.core.PsmCore
import de.psmobile.ui.SlicerScreen
import de.psmobile.ui.AppMode
import de.psmobile.ui.SimpleModeScreen
import de.psmobile.ui.WorkflowStartScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.psmobile.ui.theme.PSMobileTheme
import de.psmobile.ui.theme.AlertDialog
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

/**
 * Android kennt fuer ".stl" keinen festen MIME-Typ - je nachdem, wie die
 * Datei ins Geraet kam, meldet der MediaProvider "model/stl",
 * "application/sla" ODER "application/vnd.ms-pki.stl" (so vergeben vom
 * Standard-MediaProvider fuer per adb/Download-Ordner abgelegte Dateien -
 * an dieser Falle ist eine Verifizierung in dieser Sitzung zuerst
 * gescheitert, siehe android-parity-plan.md). Ohne den dritten Typ zeigt
 * der Systemdateiwaehler manche echten STL-Dateien ausgegraut an.
 */
/**
 * Was der Kern wirklich lesen kann.
 *
 * Die Liste steht im gemeinsamen Modul, weil iOS dieselbe braucht -
 * vorher stand sie hier und noch einmal in
 * SlicerModel.unterstuetzteModellendungen.
 */
internal val MODEL_EXTENSIONS: Set<String> get() = ModelFormats.endungen()

internal val MODEL_MIME_TYPES = arrayOf(
    "model/3mf",
    "model/stl",
    "application/sla",
    "application/vnd.ms-pki.stl",
    // Sammlungen von Printables kommen als ZIP. Der Kern sucht die
    // Modelle darin selbst heraus - siehe SlicerService.loadZip.
    "application/zip",
    "application/octet-stream",
)

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<SlicerService?>(null)
    private var pending3mf by mutableStateOf<File?>(null)

    /** Wie viele Modelle die zuletzt entpackte ZIP mitbrachte. */
    private var zipModelle: Int = 0
    private var pending3mfUri: Uri? = null
    private var importNotice by mutableStateOf<String?>(null)
    private var noticeTitle by mutableStateOf(de.psmobile.ui.PsUi.appText("Project imported", "Projekt importiert"))
    private var currentProjectUri by mutableStateOf<Uri?>(null)
    private var pendingFileOutput: File? = null
    private var pendingFileName: String = "PSMobile-Datei"
    private var pendingFileDescription: String = "Datei"
    private var applyProfileUpdateWhenProjectSaved = false
    private var appMode by mutableStateOf<AppMode?>(null)
    private var showAppSettings by mutableStateOf(false)
    private var showRemoteSlice by mutableStateOf(false)
    private var showPrinters by mutableStateOf(false)
    // Fuer die Momentaufnahmen der "Zuletzt"-Kacheln - siehe
    // merkeAlsZuletzt(). Reine Plain-Felder statt mutableStateOf: eine
    // neue Ansicht traegt sich per LaunchedEffect selbst ein, niemand
    // liest das aus der Composition zurueck.
    private var simpleSceneController: SceneController? = null
    private var advancedSceneController: SceneController? = null

    private val appPrefs by lazy {
        getSharedPreferences("psmobile", Context.MODE_PRIVATE)
    }

    /**
     * Einen Schalter wirksam machen.
     *
     * Der Wert steht schon in den Preferences - hier geht es nur um die
     * Stellen, die ihn nicht selbst nachlesen, sondern im Kern oder im
     * Dienst gesetzt werden muessen.
     */
    private fun applyAppSetting(key: String, on: Boolean) {
        when (key) {
            AppSettings.KEY_SHOW_INCOMPATIBLE -> service?.setShowIncompatiblePresets(on)
            // Modellvorschau und Arbeitsstand liest die jeweilige Stelle
            // selbst aus den Preferences.
        }
    }
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

    /**
     * Wie fileCreator, aber der Dialog geht gleich auf dem Wechselspeicher
     * auf.
     *
     * Android laesst kein direktes Schreiben auf ein OTG-Volume zu - der
     * Weg fuehrt immer ueber den Dokumentenanbieter. Was hier gewonnen
     * wird, ist der Startordner: statt im zuletzt benutzten Verzeichnis
     * steht man auf dem Stick.
     */
    private var pendingInitialUri: Uri? = null

    private val fileCreatorOnVolume = registerForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent =
                super.createIntent(context, input).apply {
                    pendingInitialUri?.let {
                        putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it)
                    }
                }
        }
    ) { uri ->
        pendingInitialUri = null
        if (uri != null)
            writePendingFile(uri)
        else
            pendingFileOutput = null
    }

    /**
     * Der angeschlossene Wechselspeicher, oder null.
     *
     * Wird bei jedem Aufruf neu gefragt: ein Stick kann jederzeit
     * angesteckt oder abgezogen werden, und ein gemerkter Wert waere dann
     * eine Luege.
     */
    private fun removableTarget(): Pair<RemovableStorage.Volume, Uri>? {
        val manager = getSystemService(StorageManager::class.java) ?: return null
        val volumes = manager.storageVolumes
        val described = volumes.map {
            RemovableStorage.Volume(
                description = it.getDescription(this).orEmpty(),
                isRemovable = it.isRemovable,
                isPrimary = it.isPrimary,
                isMounted = it.state == android.os.Environment.MEDIA_MOUNTED,
            )
        }
        val chosen = RemovableStorage.target(described) ?: return null
        val volume = volumes.getOrNull(described.indexOf(chosen)) ?: return null
        // createOpenDocumentTreeIntent liefert den Wurzel-URI des Volumes;
        // mehr als dessen EXTRA_INITIAL_URI wird hier nicht gebraucht.
        val root = volume.createOpenDocumentTreeIntent()
            .getParcelableExtra<Uri>(android.provider.DocumentsContract.EXTRA_INITIAL_URI)
            ?: return null
        return chosen to root
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
        // Wer den Modus immer gleich waehlt, soll nicht jedes Mal gefragt
        // werden. Zurueck zur Frage kommt man ueber die App-Einstellungen.
        appMode = when (appPrefs.getString(AppSettings.KEY_START_MODE, AppSettings.START_ASK)) {
            AppSettings.START_SIMPLE -> AppMode.SIMPLE
            AppSettings.START_ADVANCED -> AppMode.ADVANCED
            else -> null
        }
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

                if (showRemoteSlice && svc != null) {
                    de.psmobile.ui.RemoteSliceScreen(
                        service = svc,
                        onHome = { showRemoteSlice = false },
                    )
                } else if (appMode == null) {
                    WorkflowStartScreen(
                        onSimple = { appMode = AppMode.SIMPLE },
                        onAdvanced = { appMode = AppMode.ADVANCED },
                        // Direkt in die Einrichtung, wie auf iOS. Der
                        // Assistent dazwischen war eine dritte Stelle zum
                        // Waehlen von Drucker, Filament und Print Settings -
                        // beide Modi koennen das laengst mit Suche.
                        onAdvancedWizard = { svc?.reopenSetup() },
                        onAppSettings = { showAppSettings = true },
                        onLanguageChange = { svc?.uiLanguage = it },
                        onRemote = { showRemoteSlice = true },
                        onManagePrinters = { showPrinters = true },
                        onOpenRecent = { uri, advanced -> openRecentProject(uri, advanced) },
                    )
                } else if (appMode == AppMode.SIMPLE && svc == null) {
                    // Bei festem Startmodus zeichnet Simple schon im ersten
                    // Bild, der Dienst ist dann noch nicht gebunden. Vorher
                    // deckte die Startseite diese Luecke ab.
                    Box(
                        Modifier.fillMaxSize().background(PrusaColors.Background),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = PrusaColors.Orange) }
                } else if (appMode == AppMode.SIMPLE) {
                    SimpleModeScreen(
                        service = svc!!,
                        window = this@MainActivity.window,
                        onPickFile = { modelPicker.launch(MODEL_MIME_TYPES) },
                        onHome = { appMode = null },
                        onOpenAdvanced = { appMode = AppMode.ADVANCED },
                        onOpenPrinterSetup = { svc.reopenSetup() },
                        onAppSettings = { showAppSettings = true },
                        onStartSlice = { svc.startSlice() },
                        // Der Knopf im Slice-Blatt war nie verdrahtet:
                        // onShareGcode hatte eine leere Vorgabe, und
                        // niemand hat sie ueberschrieben. Im Simple Mode
                        // fuehrte "G-Code sichern" damit ins Leere.
                        onShareGcode = {
                            svc.shareableGcodeUri()?.let { uri -> shareGcode(uri) }
                        },
                        onShareAllGcode = { shareGcodes(svc.shareableGcodeUris()) },
                        onSaveProject = { saveProject(saveAs = false) },
                        onRemoteSettings = { showRemoteSlice = true },
                        onControllerReady = { simpleSceneController = it },
                    )
                } else {
                    SlicerScreen(
                        service = svc,
                        onOpenSimple = { appMode = AppMode.SIMPLE },
                        onHome = { appMode = null },
                        onAppSettings = { showAppSettings = true },
                        onPickFile = { uris -> importUris(uris) },
                        onShare = { uri -> shareGcode(uri) },
                        onShareAll = { uris -> shareGcodes(uris) },
                        // Bei jeder Neuzeichnung neu gefragt: ein Stick
                        // kann jederzeit angesteckt oder abgezogen
                        // werden.
                        usbTarget = removableTarget()?.let {
                            RemovableStorage.label(it.first)
                        },
                        onExportToUsb = { exportGcodeToVolume() },
                        onPickBackupFolder = { backupPicker.launch(null) },
                        onNewProject = {
                            svc?.newProject()
                            currentProjectUri = null
                            noticeTitle = de.psmobile.ui.PsUi.appText("New project", "Neues Projekt")
                            importNotice = de.psmobile.ui.PsUi.appText("An empty project with one bed was created.", "Ein leeres Projekt mit einem Druckbett wurde angelegt.")
                        },
                        onSaveProject = { saveProject(saveAs = false) },
                        onSaveProjectAs = { saveProject(saveAs = true) },
                        canReloadProject = currentProjectUri != null,
                        onReloadProject = ::reloadCurrentProject,
                        onExportPlate = { exportPlate(it) },
                        onRepairStl = {
                            repairStlPicker.launch(MODEL_MIME_TYPES)
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
                        onOpenRecent = { uri -> openRecentProject(uri, alsAdvanced = true) },
                        onControllerReady = { advancedSceneController = it },
                    )
                }

                // AP-06: Diese drei ersetzten bisher den ganzen
                // Bildschirm. Jetzt liegen sie als Karte darueber - was
                // darunter war, bleibt sichtbar, und man findet sich beim
                // Schliessen sofort wieder zurecht.
                if (svc != null && setupNeeded) {
                    val models by svc.printerModels.collectAsState()
                    val busy by svc.setupBusy.collectAsState()
                    // Beim allerersten Start gibt es keinen Weg hinaus -
                    // dann darf auch ein Tipp daneben nicht schliessen.
                    // Dahinter liegt dort ohnehin nichts; die Karte
                    // traegt erst beim spaeteren Wiederoeffnen aus dem
                    // Arbeitsbereich heraus.
                    val schliessbar = svc.installedPrinters().isNotEmpty()
                    de.psmobile.ui.SchwebenderDialog(
                        kennung = "dialog.ersteinrichtung",
                        onClose = { svc.dismissSetup() },
                        maxBreite = 900.dp,
                        abbrechbar = schliessbar,
                    ) {
                        SetupScreen(
                            models = models,
                            busy = busy,
                            onConfirm = { svc.completeSetup(it) },
                            onLanguageChange = { svc.uiLanguage = it },
                            preselected = svc.installedPrinters(),
                            onClose = if (schliessbar) {
                                { svc.dismissSetup() }
                            } else null,
                        )
                    }
                }

                if (showAppSettings) {
                    de.psmobile.ui.SchwebenderDialog(
                        kennung = "dialog.app-einstellungen",
                        onClose = { showAppSettings = false },
                        maxBreite = 900.dp,
                    ) {
                        AppSettingsScreen(
                            prefs = appPrefs,
                            language = svc?.uiLanguage ?: "en",
                            onLanguageChange = { svc?.uiLanguage = it },
                            onToggleChanged = { key, on -> applyAppSetting(key, on) },
                            onClose = { showAppSettings = false },
                        )
                    }
                }

                // Von der Startseite aus, ohne erst ein Projekt im
                // Advanced Mode oeffnen zu muessen - vorher war die
                // Druckerverwaltung (und damit die QR-Kopplung) nur ueber
                // dessen Seitenleiste erreichbar. Schwebt wie ihr
                // Gegenstueck im Advanced Mode; zwei verschiedene Rahmen
                // fuer denselben Bildschirm waeren eine Zumutung.
                if (showPrinters && svc != null) {
                    val presets by svc.presets.collectAsState()
                    de.psmobile.ui.SchwebenderDialog(
                        kennung = "dialog.drucker",
                        onClose = { showPrinters = false },
                        maxBreite = 900.dp,
                    ) {
                        de.psmobile.ui.PrintersScreen(
                            presetNames = presets.printers,
                            onClose = { showPrinters = false },
                            onPickBackupFolder = { backupPicker.launch(null) },
                            onReopenSetup = { svc.reopenSetup() },
                        )
                    }
                }

                var showProfileUpdateSaveWarning by remember { mutableStateOf(false) }
                val profileUpdate by (svc?.profileUpdates?.collectAsState()
                    ?: remember { mutableStateOf<de.psmobile.slicing.profileupdate.ProfileUpdateState>(de.psmobile.slicing.profileupdate.ProfileUpdateState.Idle) })
                when (val update = profileUpdate) {
                    is de.psmobile.slicing.profileupdate.ProfileUpdateState.Offer ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("New printer and material profiles available", "Neue Drucker- und Materialprofile verfügbar")) },
                            text = { androidx.compose.material3.Text(update.manifest.releaseNotes.joinToString("\n• ", prefix = "• ")) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = { svc?.downloadProfileUpdate() }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Update now", "Jetzt aktualisieren")) } },
                            dismissButton = { androidx.compose.foundation.layout.Row {
                                androidx.compose.material3.TextButton(onClick = { svc?.deferProfileUpdate() }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Later", "Später")) }
                                androidx.compose.material3.TextButton(onClick = { svc?.skipProfileUpdate() }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Ask again at the next update", "Erst beim nächsten Update fragen")) }
                            } },
                        )
                    is de.psmobile.slicing.profileupdate.ProfileUpdateState.ReadyToApply ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Profiles updated", "Profile aktualisiert")) },
                            text = { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("The new profiles are checked and can be used now or at the next restart.", "Die neuen Profile sind geprüft und können jetzt oder beim nächsten Neustart verwendet werden.")) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                                if (svc?.profileUpdateNeedsSave() == true) showProfileUpdateSaveWarning = true
                                else svc?.applyStagedProfileUpdate()
                            }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Use now", "Jetzt verwenden")) } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { svc?.deferProfileUpdate() }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("At restart", "Beim Neustart")) } },
                        )
                    else -> Unit
                }

                if (showProfileUpdateSaveWarning) {
                    AlertDialog(
                        onDismissRequest = { showProfileUpdateSaveWarning = false },
                        title = { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Save project before switching profiles?", "Projekt vor Profilwechsel speichern?")) },
                        text = { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("A model is loaded or there are unsaved profile changes. Save the 3MF project before the slicer session restarts with the new profiles.", "Es ist ein Modell geladen oder es gibt ungespeicherte Profiländerungen. Speichere das 3MF-Projekt, bevor die Slicer-Sitzung mit den neuen Profilen neu startet.")) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            showProfileUpdateSaveWarning = false
                            applyProfileUpdateWhenProjectSaved = true
                            saveProject(saveAs = false)
                        }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Save project & update", "Projekt speichern & aktualisieren")) } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = {
                            showProfileUpdateSaveWarning = false
                            svc?.deferProfileUpdate()
                        }) { androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("At restart", "Beim Neustart")) } },
                    )
                }

                pending3mf?.let { file ->
                    AlertDialog(
                        onDismissRequest = { pending3mf = null },
                        title = {
                            androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("Import 3MF", "3MF importieren"))
                        },
                        text = {
                            androidx.compose.material3.Text(
                                de.psmobile.ui.PsUi.appText(
                                    "Should „${file.name.substringAfter('-', file.name)}“ add only its " +
                                        "3D objects to the current bed, or open as a full project " +
                                        "with positions and print profile?",
                                    "Soll „${file.name.substringAfter('-', file.name)}“ nur seine " +
                                        "3D-Objekte zum aktuellen Bett hinzufügen oder als vollständiges " +
                                        "Projekt mit Positionen und Druckprofil geöffnet werden?",
                                )
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    import3mf(file, SlicerService.ImportMode.PROJECT)
                                },
                            ) {
                                androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("As project", "Als Projekt"))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    import3mf(file, SlicerService.ImportMode.OBJECTS)
                                },
                            ) {
                                androidx.compose.material3.Text(de.psmobile.ui.PsUi.appText("3D objects only", "Nur 3D-Objekte"))
                            }
                        },
                    )
                }

                importNotice?.let { message ->
                    AlertDialog(
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
            var ausZip = 0
            var leereZips = 0
            for (uri in uris) {
                when (importOne(uri, askAboutProject = ask)) {
                    ImportOutcome.FAILED -> Unit
                    ImportOutcome.LOADED -> loaded++
                    ImportOutcome.AWAITING_DECISION -> Unit
                    ImportOutcome.LOADED_PROJECT_AS_OBJECTS -> {
                        loaded++
                        projectsAsObjects++
                    }
                    // Eine ZIP zaehlt als eine Auswahl, bringt aber
                    // mehrere Modelle mit - beide Zahlen gehoeren in
                    // den Hinweis, sonst steht dort "1 Datei geladen"
                    // und auf dem Bett liegen zwoelf.
                    ImportOutcome.LOADED_ZIP -> {
                        loaded++
                        ausZip += zipModelle
                    }
                    ImportOutcome.EMPTY_ZIP -> leereZips++
                }
            }
            // Bei genau einer Datei bleibt der Hinweis sonst aus - die
            // Rueckfrage zur 3MF sagt dort selbst, was passiert ist. Eine
            // ZIP stellt keine Rueckfrage, bringt aber mehrere Modelle
            // mit: dass aus einer Auswahl zwoelf Teile wurden, gehoert
            // gesagt.
            if (!ask || ausZip > 0 || leereZips > 0) {
                noticeTitle = de.psmobile.ui.PsUi.appText("Files loaded", "Dateien geladen")
                importNotice = buildString {
                    if (!ask) {
                        append(
                            de.psmobile.ui.ImportSelection.summary(
                                loaded = loaded,
                                total = uris.size,
                                projectsAsObjects = projectsAsObjects,
                            )
                        )
                    }
                    if (ausZip > 0) {
                        if (isNotEmpty()) append(" ")
                        append(
                            de.psmobile.ui.PsUi.appText(
                                "$ausZip models came from a ZIP archive.",
                                "$ausZip Modelle kamen aus einem ZIP-Archiv.",
                            )
                        )
                    }
                    if (leereZips > 0) {
                        if (isNotEmpty()) append(" ")
                        append(
                            de.psmobile.ui.PsUi.appText(
                                "A ZIP archive contained no readable model.",
                                "Ein ZIP-Archiv enthielt kein lesbares Modell.",
                            )
                        )
                    }
                }
            }
        }
    }

    private enum class ImportOutcome {
        FAILED,
        LOADED,
        LOADED_PROJECT_AS_OBJECTS,

        /** Eine ZIP hat mehrere Modelle mitgebracht. */
        LOADED_ZIP,

        /** Die ZIP enthielt kein Modell, das der Kern lesen kann. */
        EMPTY_ZIP,

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
                } ?: error(de.psmobile.ui.PsUi.appText("File not readable: $uri", "Datei nicht lesbar: $uri"))
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
        // Der Waehler laesst application/octet-stream zu, weil eine
        // engere Liste echte STL-Dateien ausgraut (siehe
        // MODEL_MIME_TYPES) - damit kommt hier aber auch alles andere an.
        // Was der Kern nicht lesen kann, gehoert mit klarer Ansage
        // abgewiesen. Die Dateiauswahl laesst deutlich mehr durch, als
        // libslic3r versteht; ohne diese Pruefung kam die Absage aus dem
        // Kern und war unlesbar.
        // Eine ZIP ist keine Modelldatei, sondern eine Tuete voll
        // davon - sie geht deshalb an der Endungspruefung vorbei und in
        // den Entpacker. Genau wie drueben, wo `loadZip` vor dem
        // Formatpfad steht (`PSMobileApp.swift:168`).
        if (file.extension.equals("zip", ignoreCase = true)) {
            val anzahl = withContext(Dispatchers.IO) {
                runCatching { svc.loadZip(file.absolutePath) }
            }.onFailure { svc.reportImportError(it) }.getOrNull()
            zipModelle = anzahl ?: 0
            return when {
                anzahl == null -> ImportOutcome.FAILED
                anzahl <= 0 -> ImportOutcome.EMPTY_ZIP
                else -> ImportOutcome.LOADED_ZIP
            }
        }
        if (!ModelFormats.erlaubt(file.extension)) {
            svc.reportImportError(
                IllegalArgumentException(ModelFormats.nichtUnterstuetzt(file.extension)))
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
                            !project.configLoaded -> SimpleModeState.text(
                                "The 3MF contained no project configuration. The objects were " +
                                    "taken over with their stored arrangement.",
                                "Die 3MF enthielt keine Projektkonfiguration. Die Objekte wurden " +
                                    "mit ihrer gespeicherten Anordnung übernommen.")
                            project.exactInstalledPrinter -> SimpleModeState.text(
                                "The matching printer profile „$profileLabel“ was selected " +
                                    "automatically.",
                                "Das passende Druckerprofil „$profileLabel“ wurde automatisch ausgewählt.")
                            else -> SimpleModeState.text(
                                "The embedded printer configuration was activated as the " +
                                    "project-local profile „$profileLabel“.",
                                "Die eingebettete Druckerkonfiguration wurde als projektlokales " +
                                    "Profil „$profileLabel“ aktiviert.")
                        }
                        val beds = if (project.bedCount > 1) {
                            "\n\n" + SimpleModeState.text(
                                "${project.bedCount} print beds were taken over and can be " +
                                    "selected directly at the top.",
                                "${project.bedCount} Druckbetten wurden übernommen und können " +
                                    "oben direkt ausgewählt werden.")
                        } else {
                            ""
                        }
                        importNotice = if (project.postProcessRemoved) {
                            "$details$beds\n\n" + SimpleModeState.text(
                                "An embedded post-processing script was not taken over, for " +
                                    "safety reasons.",
                                "Ein eingebettetes Post-Processing-Skript wurde aus " +
                                    "Sicherheitsgründen nicht übernommen.")
                        } else {
                            details + beds
                        }
                        noticeTitle = SimpleModeState.text("Project imported", "Projekt importiert")
                        currentProjectUri = sourceUri
                        if (sourceUri != null) {
                            merkeAlsZuletzt(sourceUri, queryDisplayName(sourceUri) ?: file.nameWithoutExtension)
                        }
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
            result.onFailure { showFileError(de.psmobile.ui.PsUi.appText("Reload project", "Projekt neu laden"), it) }
                .onSuccess { project ->
                    svc.setProjectKey(uri.toString())
                    noticeTitle = de.psmobile.ui.PsUi.appText("Project reloaded", "Projekt neu geladen")
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
                    } ?: error(de.psmobile.ui.PsUi.appText("Project target is not writable: $uri", "Projektziel ist nicht beschreibbar: $uri"))
                    source.length()
                }
            }
            result
                .onFailure { svc.reportImportError(it) }
                .onSuccess { bytes ->
                    currentProjectUri = uri
                    svc.setProjectKey(uri.toString())
                    noticeTitle = de.psmobile.ui.PsUi.appText("Project saved", "Projekt gespeichert")
                    val name = queryDisplayName(uri) ?: svc.suggestedProjectName()
                    importNotice = "„$name“ wurde als vollständiges 3MF-Projekt " +
                        "mit allen belegten Druckbetten gespeichert " +
                        "(${bytes / 1024} KiB)."
                    if (applyProfileUpdateWhenProjectSaved) {
                        applyProfileUpdateWhenProjectSaved = false
                        svc.applyStagedProfileUpdate()
                    }
                    merkeAlsZuletzt(uri, name)
                }
        }
    }

    /**
     * Fuer die Startseite ("Zuletzt", Gegenstueck zu iOS'
     * recentProjects()) - siehe RecentProjectsStore.kt fuer die
     * Begruendung, warum das auf Android eine explizite Liste braucht
     * statt eines Verzeichnis-Listings. Die dauerhafte Leseberechtigung
     * ist noetig, weil eine SAF-Zugriffsgewaehrung sonst spaetestens
     * beim naechsten App-Start wieder erlischt.
     */
    private fun merkeAlsZuletzt(uri: Uri, name: String) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        de.psmobile.net.RecentProjectsStore.hinzufuegen(this, uri.toString(), name)
        erzeugeKachelBild(uri)
    }

    /**
     * Momentaufnahme des Bettinhalts fuer die "Zuletzt"-Kachel - vom
     * gerade sichtbaren Viewport (Simple oder Advanced, je nachdem, wo
     * gesichert wurde), nicht neu gerendert. Laeuft nach dem Eintragen
     * selbst nach: das Rendern braucht einen GL-Umlauf und soll den
     * "Projekt gespeichert"-Erfolg nicht aufhalten.
     */
    private fun erzeugeKachelBild(uri: Uri) {
        val controller = if (appMode == AppMode.ADVANCED) advancedSceneController else simpleSceneController
        controller?.captureThumbnail { bitmap ->
            if (bitmap == null) return@captureThumbnail
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val seite = minOf(bitmap.width, bitmap.height)
                    val quadrat = android.graphics.Bitmap.createBitmap(
                        bitmap, (bitmap.width - seite) / 2, (bitmap.height - seite) / 2, seite, seite,
                    )
                    val klein = android.graphics.Bitmap.createScaledBitmap(quadrat, 240, 240, true)
                    val ordner = File(cacheDir, "recent-thumbs").apply { mkdirs() }
                    val datei = File(ordner, "${uri.toString().hashCode()}.png")
                    datei.outputStream().use { out ->
                        klein.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                    }
                    de.psmobile.net.RecentProjectsStore.setzeThumbnail(
                        this@MainActivity, uri.toString(), datei.absolutePath,
                    )
                }
            }
        }
    }

    /** Ein Eintrag aus der "Zuletzt"-Liste auf der Startseite antippen. */
    private fun openRecentProject(uriString: String, alsAdvanced: Boolean) {
        val svc = service ?: return
        val uri = Uri.parse(uriString)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = copyDocumentToCache(uri, "project-recent")
                    svc.loadModel(source.absolutePath, SlicerService.ImportMode.PROJECT)
                }
            }
            result.onFailure {
                // Nicht mehr lesbar (geloescht, Berechtigung entzogen,
                // externer Speicher getrennt) - dann gehoert der Eintrag
                // auch nicht mehr in die Liste. Selbstheilend statt bei
                // jedem Start erneut zu scheitern.
                svc.reportImportError(it)
                de.psmobile.net.RecentProjectsStore.entfernen(this@MainActivity, uriString)
            }.onSuccess {
                currentProjectUri = uri
                svc.setProjectKey(uriString)
                merkeAlsZuletzt(uri, queryDisplayName(uri) ?: "Projekt")
                appMode = if (alsAdvanced) AppMode.ADVANCED else AppMode.SIMPLE
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
                .onFailure { showFileError(de.psmobile.ui.PsUi.appText("Bed export", "Bettexport"), it) }
                .onSuccess { file ->
                    pendingFileOutput = file
                    pendingFileName =
                        if (format == PsmCore.PlateFormat.STL)
                            "PSMobile-Druckbett.stl"
                        else "PSMobile-Druckbett.obj"
                    pendingFileDescription =
                        if (format == PsmCore.PlateFormat.STL)
                            de.psmobile.ui.PsUi.appText("STL bed export", "STL-Bettexport")
                        else de.psmobile.ui.PsUi.appText("OBJ bed export", "OBJ-Bettexport")
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
                        if (toBinary) de.psmobile.ui.PsUi.appText("binary BGCode", "binärer BGCode") else "ASCII-G-Code"
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
                .onFailure { showFileError(de.psmobile.ui.PsUi.appText("Emboss SVG", "SVG prägen"), it) }
                .onSuccess {
                    noticeTitle = de.psmobile.ui.PsUi.appText("SVG added", "SVG hinzugefügt")
                    importNotice =
                        de.psmobile.ui.PsUi.appText("The SVG outline was inserted as a volume into the selected object.", "Die SVG-Kontur wurde als Volumen in das ausgewählte Objekt eingefügt.")
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
        } ?: error(de.psmobile.ui.PsUi.appText("File not readable: $uri", "Datei nicht lesbar: $uri"))
        return output
    }

    private fun writePendingFile(uri: Uri) {
        val source = pendingFileOutput ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error(de.psmobile.ui.PsUi.appText("File target is not writable: $uri", "Dateiziel ist nicht beschreibbar: $uri"))
                    source.length()
                }
            }
            result
                .onFailure { showFileError(pendingFileDescription, it) }
                .onSuccess { bytes ->
                    noticeTitle = de.psmobile.ui.PsUi.appText("File saved", "Datei gespeichert")
                    importNotice =
                        "$pendingFileDescription wurde gespeichert (${bytes / 1024} KiB)."
                    // Gespeichert ist nicht weitergegeben: der Weg in
                    // eine andere App fuehrt ueber das Teilen-Blatt, und
                    // dafuer muss die Datei erreichbar bleiben.
                    service?.merkeAusgabe(source, pendingFileName)
                }
            pendingFileOutput = null
        }
    }

    private fun showFileError(action: String, error: Throwable) {
        noticeTitle = de.psmobile.ui.PsUi.appText("$action failed", "$action fehlgeschlagen")
        importNotice = error.message ?: de.psmobile.ui.PsUi.appText("Unknown file error", "Unbekannter Dateifehler")
    }

    /** G-Code an Files, Drive, PrusaLink-Apps o. ae. weiterreichen. */
    /**
     * G-Code auf den angeschlossenen Stick schreiben.
     *
     * Der Dateiname kommt aus output_filename_format des Druckprofils -
     * derselbe wie beim Teilen. Wer den G-Code auf einen Stick zieht,
     * steckt ihn gleich in den Drucker; dort ist der sprechende Name
     * mehr wert als irgendwo sonst.
     */
    private fun exportGcodeToVolume() {
        val svc = service ?: return
        val (volume, root) = removableTarget() ?: run {
            showFileError(
                "USB-Export",
                IllegalStateException(de.psmobile.ui.PsUi.appText("No removable storage connected.", "Kein Wechselspeicher angeschlossen.")),
            )
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { svc.gcodeFileForExport() }
            }
            result
                .onFailure { showFileError("USB-Export", it) }
                .onSuccess { file ->
                    pendingFileOutput = file
                    pendingFileName = file.name
                    pendingFileDescription = "G-Code auf ${volume.description}"
                    pendingInitialUri = root
                    fileCreatorOnVolume.launch(file.name)
                }
        }
    }

    private fun shareGcode(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"          // G-Code hat keinen eigenen MIME-Typ
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, de.psmobile.ui.PsUi.appText("Share G-code", "G-Code teilen")))
    }

    /**
     * Mehrere G-Code-Dateien auf einmal weitergeben.
     *
     * Nach "alle Betten schneiden" liegen mehrere vor; einzeln zu teilen
     * hiesse, den Waehler fuenfmal zu durchlaufen. Gegenstueck zum
     * ShareLink mit Sammlung auf iOS (SliceSheet.swift:88ff).
     */
    private fun shareGcodes(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            shareGcode(uris.first())
            return
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(
            send, de.psmobile.ui.PsUi.appText("Share G-code", "G-Code teilen")))
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}
