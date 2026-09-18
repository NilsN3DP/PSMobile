package de.psmobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import de.psmobile.diagnose.Selbsttest
import de.psmobile.slicing.SlicerService
import de.psmobile.slicing.profileupdate.ProfileUpdateState
import de.psmobile.ui.AppSettingsStore
import de.psmobile.ui.st
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.PSMobileTheme
import de.psmobile.ui.theme.PrusaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Die Aktivitaet ist auf Android das, was auf iOS `@main struct
 * PSMobileApp` ist - nur dass sie zusaetzlich den Dienst bindet, der den
 * Kern haelt, und eingehende Dateien (Teilen/Oeffnen) entgegennimmt.
 *
 * Die Oberflaeche selbst steht in [PSMobileApp] - dem Port von
 * `ios/PSMobile/PSMobileApp.swift`. Hier bleibt nur, was Android
 * verlangt: Service-Bindung, Intents, Fenster-Farben, Profil-Update-
 * Dialoge (Android-eigener Kanal ohne iOS-Gegenstueck).
 */
class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<SlicerService?>(null)
    private var model by mutableStateOf<SlicerModel?>(null)
    /** Eine per Teilen/Oeffnen hereingekommene Datei - Gegenstueck zu `.onOpenURL`. */
    private var externalOpen by mutableStateOf<Uri?>(null)
    private val einstellungen by lazy { AppSettingsStore(this, startargumente) }
    /** Die Schalter dieses Starts - siehe [Startargumente]. */
    private lateinit var startargumente: Startargumente
    /** Haelt den Selbsttest aus -psm-selbsttest-auto am Leben. */
    private var autoSelbsttest: Selbsttest? = null

    /** Fragt einmal nach `POST_NOTIFICATIONS` - siehe SlicerModel.vorDemSlicen. */
    private val benachrichtigungsrecht =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun benachrichtigungsrechtErfragen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val recht = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(recht) == PackageManager.PERMISSION_GRANTED) return
        if (shouldShowRequestPermissionRationale(recht)) return // schon einmal abgelehnt: nicht nerven
        benachrichtigungsrecht.launch(recht)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as SlicerService.LocalBinder).service
            service = svc
            // Session im Hintergrund anlegen - das Entpacken der Profile
            // dauert beim ersten Start ein paar hundert Millisekunden.
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { svc.ensureCore(startargumente) }
                    .onFailure { android.util.Log.e("MainActivity", "Core-Initialisierung fehlgeschlagen", it) }
                withContext(Dispatchers.Main) {
                    if (model == null) model = SlicerModel(this@MainActivity, svc, startargumente).also {
                        it.vorDemSlicen = { benachrichtigungsrechtErfragen() }
                    }
                    handleIncomingIntent(intent)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vor dem Binden: der Dienst bekommt sie beim Anlegen des Kerns,
        // und das passiert direkt nach onServiceConnected.
        startargumente = Startargumente.aus(intent)
        enableEdgeToEdge()
        // Der Startbildschirm darf nicht kurz als helle Android-Flaeche
        // aufblitzen, bevor die Oberflaeche ihren Inhalt zeichnet.
        @Suppress("DEPRECATION")
        window.statusBarColor = PrusaColors.Background.value.toInt()
        @Suppress("DEPRECATION")
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

        // Selbsttest ohne Geraet in der Hand - Gegenstueck zu
        // -psm-selbsttest-auto in PSMobileApp.swift. Er schreibt seinen
        // Bericht wie sonst auch; nur der Startknopf entfaellt.
        if (startargumente.contains("-psm-selbsttest-auto")) {
            val test = Selbsttest(this, startargumente)
            autoSelbsttest = test
            lifecycleScope.launch(Dispatchers.IO) { test.durchlauf() }
        }

        setContent {
            // Macht jedes `Modifier.testTag("x")` fuer uiautomator und adb
            // als resource-id sichtbar - das Gegenstueck zu dem, was
            // `accessibilityIdentifier` auf iOS ohnehin tut.
            //
            // Erst dadurch laesst sich derselbe Bildschirmfoto-Rundgang
            // auf beiden Plattformen ueber dieselben Kennungen fahren.
            // Ohne das kennt Android seine Kennungen nur innerhalb der
            // Compose-Tests, und ein Vergleich muesste ueber Koordinaten
            // laufen - also ueber genau das, was sich zwischen zwei
            // Layouts unterscheidet.
            @OptIn(ExperimentalComposeUiApi::class)
            //
            // Safe Area wie auf iOS: dort bleibt der Inhalt von selbst unter
            // Statusleiste und Kamera-Aussparung heraus, nur der Hintergrund
            // reicht per `.ignoresSafeArea()` bis an den Rand. Android macht
            // mit enableEdgeToEdge() das Gegenteil - alles reicht bis an den
            // Rand, und wer nichts aussparrt, zeichnet die Kopfzeile unter
            // die Uhr (so auf dem Telefon am 13.09.2026 gesehen). Deshalb:
            // Hintergrund randlos, Inhalt mit safeDrawing-Abstand.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PrusaColors.Background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // Und die Tastatur: ohne imePadding deckte sie Knoepfe und
                    // Felder am unteren Rand ab (Schichtprofil, Druckerformular
                    // auf dem S23 FE, 16.09.2026). iOS schiebt von selbst.
                    .imePadding()
                    .semantics { testTagsAsResourceId = true },
            ) {
            PSMobileTheme {
                val m = model
                if (m == null) {
                    // Bis der Dienst gebunden ist, gibt es nichts zu zeigen -
                    // wie der Startmoment auf iOS, nur sichtbar gemacht.
                    Box(
                        Modifier.fillMaxSize().background(PrusaColors.Background),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = PrusaColors.Orange) }
                } else {
                    PSMobileApp(
                        model = m,
                        einstellungen = einstellungen,
                        startRoute = teststartRoute(),
                        externalOpen = externalOpen,
                        onExternalOpenHandled = { externalOpen = null },
                    )
                    ProfilUpdateDialoge(m)
                }
            }
            }
        }
    }

    /**
     * Der Startmodus aus den Startargumenten - Gegenstueck zu den beiden
     * ersten Zeilen von `startRoute()` in `ios/PSMobile/PSMobileApp.swift`.
     * Ohne Schalter entscheiden die App-Einstellungen, wie drueben.
     */
    private fun teststartRoute(): Route? = when {
        startargumente.contains("-psm-start-simple") -> Route.Simple
        startargumente.contains("-psm-start-advanced") -> Route.Advanced
        else -> null
    }

    /**
     * Der Profil-Update-Kanal ist Android-eigen (iOS hat ihn noch nicht,
     * siehe Lastenheft IOS-02). Seine Rueckfragen bleiben deshalb hier
     * als Systemdialoge, ohne eigene Bedienelemente in der Oberflaeche.
     */
    @Composable
    private fun ProfilUpdateDialoge(m: SlicerModel) {
        val svc = m.service
        var showProfileUpdateSaveWarning by remember { mutableStateOf(false) }
        val profileUpdate by svc.profileUpdates.collectAsState()
        when (val update = profileUpdate) {
            is ProfileUpdateState.Offer ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(st("New printer and material profiles available", "Neue Drucker- und Materialprofile verfügbar")) },
                    text = { Text(update.manifest.releaseNotes.joinToString("\n• ", prefix = "• ")) },
                    confirmButton = { TextButton(onClick = { svc.downloadProfileUpdate() }) { Text(st("Update now", "Jetzt aktualisieren")) } },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { svc.deferProfileUpdate() }) { Text(st("Later", "Später")) }
                            TextButton(onClick = { svc.skipProfileUpdate() }) { Text(st("Ask again at the next update", "Erst beim nächsten Update fragen")) }
                        }
                    },
                )
            is ProfileUpdateState.ReadyToApply ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(st("Profiles updated", "Profile aktualisiert")) },
                    text = { Text(st("The new profiles are checked and can be used now or at the next restart.", "Die neuen Profile sind geprüft und können jetzt oder beim nächsten Neustart verwendet werden.")) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (svc.profileUpdateNeedsSave()) showProfileUpdateSaveWarning = true
                            else svc.applyStagedProfileUpdate()
                        }) { Text(st("Use now", "Jetzt verwenden")) }
                    },
                    dismissButton = { TextButton(onClick = { svc.deferProfileUpdate() }) { Text(st("At restart", "Beim Neustart")) } },
                )
            else -> Unit
        }

        if (showProfileUpdateSaveWarning) {
            AlertDialog(
                onDismissRequest = { showProfileUpdateSaveWarning = false },
                title = { Text(st("Save project before switching profiles?", "Projekt vor Profilwechsel speichern?")) },
                text = { Text(st("A model is loaded or there are unsaved profile changes. Save the 3MF project before the slicer session restarts with the new profiles.", "Es ist ein Modell geladen oder es gibt ungespeicherte Profiländerungen. Speichere das 3MF-Projekt, bevor die Slicer-Sitzung mit den neuen Profilen neu startet.")) },
                confirmButton = {
                    TextButton(onClick = {
                        showProfileUpdateSaveWarning = false
                        m.saveProject()
                        svc.applyStagedProfileUpdate()
                    }) { Text(st("Save project & update", "Projekt speichern & aktualisieren")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showProfileUpdateSaveWarning = false
                        svc.deferProfileUpdate()
                    }) { Text(st("At restart", "Beim Neustart")) }
                },
            )
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
     * hier gesichert - wie auf iOS beim Wechsel in den Hintergrund
     * (`scenePhase == .background`).
     */
    override fun onStop() {
        runCatching { service?.autosave() }
        super.onStop()
    }

    override fun onDestroy() {
        model?.dispose()
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    /** Verhindert, dass derselbe Intent zweimal ausgewertet wird. */
    private var handledIntent: Intent? = null

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent === handledIntent) return
        if (model == null) return   // wird nach dem Binden erneut aufgerufen
        handledIntent = intent

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> sharedStream(intent)
            else -> null
        }
        if (uri != null) externalOpen = uri
    }

    private fun sharedStream(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
