package de.psmobile

// 11.09.2026: `PSMobileApp.swift` sichert beim Wechsel in den
// Hintergrund den Arbeitsstand, bevor es die Sitzung als sauber beendet
// vermerkt (`onChange(of: scenePhase)`). Auf Android steht dieselbe
// Reihenfolge in `MainActivity.onStop()` - autosave(), dann
// sitzungSauberBeendet(). Der Lebenszyklus haengt hier an der Aktivitaet,
// nicht an der Oberflaeche.

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.psmobile.shared.rules.AppSettings
import de.psmobile.ui.AdvancedWorkspaceView
import de.psmobile.ui.AppSettingsStore
import de.psmobile.ui.AppSettingsView
import de.psmobile.ui.LocalAppSettingsStore
import de.psmobile.ui.PSScaleRoot
import de.psmobile.ui.PrintersView
import de.psmobile.ui.ProfilWechselDialog
import de.psmobile.ui.RemoteSliceView
import de.psmobile.ui.SettingsView
import de.psmobile.ui.SetupView
import de.psmobile.ui.SimpleModeView
import de.psmobile.ui.WorkflowStartView
import de.psmobile.ui.ZipModusDialog
import de.psmobile.ui.st
import java.io.File

/// Welcher Bildschirm gerade oben liegt.
///
/// Bewusst ein Aufzaehlungstyp und keine Sammlung von Bool-Flaggen:
/// mit vier unabhaengigen Schaltern gibt es sechzehn Zustaende, von
/// denen fuenf sinnvoll sind - und irgendwann steht man in einem der
/// anderen elf.
sealed class Route {
    data object Start : Route()
    data object Simple : Route()
    data object Advanced : Route()
    data object DruckEinstellungen : Route()
    data object AppEinstellungen : Route()
    data object Remote : Route()

    /// Drucker einrichten - oder, mit einer Datei, den G-Code
    /// hinschicken. Derselbe Bildschirm, zwei Anlaesse.
    data class Drucker(val datei: File?) : Route()
}

/// Der Compose-Inhalt der App - Port von `ios/PSMobile/PSMobileApp.swift`.
///
/// Was dort `@main struct PSMobileApp: App` ist, teilt sich auf Android
/// in [MainActivity] (Dienst, Intents, Fenster) und dieses Composable
/// (alles, was die Oberflaeche betrifft).
///
/// @param externalOpen  Eine per Teilen/Oeffnen hereingekommene Datei -
///                      Gegenstueck zu `.onOpenURL`.
/// @param startRoute    Ueberstimmt den Startmodus aus den Einstellungen,
///                      wie die Startargumente der UI-Tests auf iOS.
@Composable
fun PSMobileApp(
    model: SlicerModel,
    einstellungen: AppSettingsStore,
    externalOpen: Uri? = null,
    onExternalOpenHandled: () -> Unit = {},
    startRoute: Route? = null,
) {
    val context = LocalContext.current

    var route by remember { mutableStateOf<Route>(Route.Start) }
    /// Wohin die App-Einstellungen zurueckfuehren. Sie sind aus beiden
    /// Modi und vom Start aus erreichbar.
    var zurueckVon by remember { mutableStateOf<Route>(Route.Start) }
    /// Welcher Reiter der Einstellungen aufgeht.
    var einstellungsReiter by remember { mutableStateOf("print") }
    /// Wie viele Modelle aus einer geteilten ZIP entpackt wurden - > 0
    /// haelt die Nachfrage Easy/Advanced offen, bis eine Wahl faellt.
    var zipAnzahl by remember { mutableStateOf<Int?>(null) }
    /// Ob die Frage nach dem Modus von einer ZIP kommt oder von einem
    /// einzelnen Modell, das vom Startbildschirm aus geoeffnet wurde.
    var modusfrageAusZip by remember { mutableStateOf(true) }
    /// Die offenen Profilaenderungen, solange die Frage danach steht.
    var profilfrage by remember { mutableStateOf<List<SlicerModel.Profilaenderung>>(emptyList()) }

    // onAppear
    LaunchedEffect(Unit) {
        model.start()
        route = startRoute ?: startRouteAusEinstellungen(einstellungen)
    }

    // onOpenURL: Modelle, die aus anderen Apps geteilt werden. Eine ZIP
    // wird erst entpackt statt wie eine einzelne Datei geladen.
    //
    // Solange die Einrichtung laeuft, wartet die Datei: ein Import ohne
    // Drucker landete sonst als "Slicing failed" im ersten Modus, und die
    // Datei war weg (S23 FE, erster Start per Oeffnen-mit, 14.09.2026).
    // Ein einzelnes Modell vom Startbildschirm aus stellt dieselbe Frage
    // wie die ZIP - sonst bleibt der Nutzer auf dem Start stehen und
    // sieht nicht, dass etwas geladen wurde.
    LaunchedEffect(externalOpen, model.setupNeeded, model.printerIsReady) {
        val uri = externalOpen ?: return@LaunchedEffect
        if (model.setupNeeded || !model.printerIsReady) return@LaunchedEffect
        if (anzeigename(context, uri).lowercase().endsWith(".zip")) {
            val anzahl = model.loadZip(uri)
            if (anzahl != null && anzahl > 0) { modusfrageAusZip = true; zipAnzahl = anzahl }
        } else {
            val vorher = model.objects.size
            // Eine 3MF auf ein leeres Bett ist ein Projekt - mit Drucker,
            // Filament und Druckprofil, wie am Desktop beim Oeffnen. Bis zum
            // 16.09.2026 kam nur die Geometrie an, die eingebettete
            // Konfiguration ging stillschweigend verloren. Liegt schon etwas
            // auf dem Bett, kommt die Datei als Modell dazu.
            val alsProjekt = anzeigename(context, uri).lowercase().endsWith(".3mf") && vorher == 0
            if (alsProjekt) model.loadProject(uri) else model.load(uri)
            if (route == Route.Start) {
                if (model.objects.size > vorher) { modusfrageAusZip = false; zipAnzahl = 1 }
                // Gescheitert: die Meldung steht im Schnittblatt, und das
                // gibt es nur in den Modi - sonst passiert sichtbar nichts.
                else if (model.progress is SlicerModel.Progress.Failed) route = Route.Simple
            }
        }
        onExternalOpenHandled()
    }

    // Zurueck-Geste des Systems auf den Unterseiten (nur Android): zurueck
    // dorthin, wo man herkam - nicht aus der App. Start, Simple, Advanced
    // und die App-Einstellungen regeln das selbst.
    BackHandler(enabled = route is Route.Drucker || route == Route.Remote || route == Route.DruckEinstellungen) {
        route = if (route == Route.DruckEinstellungen) Route.Advanced else zurueckVon
    }

    @Composable
    fun bildschirm(fuer: Route) {
        when (fuer) {
            Route.AppEinstellungen -> WorkflowStartView(
                onSimple = { route = Route.Simple },
                onAdvanced = { route = Route.Advanced },
                onAppSettings = {},
                onPrinterSetup = { model.reopenSetup() },
                onRemote = { route = Route.Remote },
            )
            Route.Start -> WorkflowStartView(
                onSimple = { route = Route.Simple },
                onAdvanced = { route = Route.Advanced },
                onAppSettings = { zurueckVon = Route.Start; route = Route.AppEinstellungen },
                onPrinterSetup = { model.reopenSetup() },
                onRemote = { route = Route.Remote },
            )
            Route.Simple -> SimpleModeView(
                onHome = { route = Route.Start },
                onOpenAdvanced = { route = Route.Advanced },
                onOpenPrinterSetup = { model.reopenSetup() },
                onAppSettings = { zurueckVon = Route.Simple; route = Route.AppEinstellungen },
                onRemoteSettings = { zurueckVon = Route.Simple; route = Route.Remote },
                onSendToPrinter = { datei ->
                    zurueckVon = Route.Simple
                    route = Route.Drucker(datei)
                },
            )
            Route.Advanced -> AdvancedWorkspaceView(
                onHome = { route = Route.Start },
                onOpenSimple = {
                    val offen = model.profilaenderungen()
                    if (offen.isEmpty()) route = Route.Simple else profilfrage = offen
                },
                onAppSettings = { zurueckVon = Route.Advanced; route = Route.AppEinstellungen },
                onPrinters = { zurueckVon = Route.Advanced; route = Route.Drucker(null) },
                onSendToPrinter = { datei ->
                    zurueckVon = Route.Advanced
                    route = Route.Drucker(datei)
                },
                onPrinterSetup = { model.reopenSetup() },
                onSettings = { reiter ->
                    einstellungsReiter = reiter
                    route = Route.DruckEinstellungen
                },
                onRemoteSettings = { zurueckVon = Route.Advanced; route = Route.Remote },
            )
            Route.DruckEinstellungen -> SettingsView(
                model = model,
                startTab = einstellungsReiter,
                onClose = { route = Route.Advanced },
            )
            Route.Remote -> RemoteSliceView(onHome = { route = zurueckVon })
            is Route.Drucker -> PrintersView(
                senden = fuer.datei,
                dateiname = fuer.datei?.name ?: "psmobile.gcode",
                passendesProfil = model.selectedPreset("printer"),
                onClose = { route = zurueckVon },
            )
        }
    }

    @Composable
    fun basisInhalt() {
        if (route == Route.AppEinstellungen) {
            bildschirm(zurueckVon)
        } else {
            bildschirm(route)
        }
    }

    @Composable
    fun inhalt() {
        Box(Modifier.fillMaxSize()) {
            basisInhalt()

            if (model.setupNeeded || !model.printerIsReady) {
                SetupView(
                    models = model.printerModels,
                    busy = model.setupBusy,
                    preselected = model.installedPrinters,
                    onConfirm = { model.completeSetup(it) },
                    onLanguageChange = { einstellungen.language = it },
                    onClose = if (model.printerIsReady) ({ model.dismissSetup() }) else null,
                )
            } else if (route == Route.AppEinstellungen) {
                AppSettingsView(einstellungen = einstellungen, onClose = { route = zurueckVon })
            }
        }
    }

    CompositionLocalProvider(
        LocalSlicerModel provides model,
        LocalAppSettingsStore provides einstellungen,
    ) {
        // Legt die Skalierung aus der Fenstergroesse fest. Muss ganz
        // aussen stehen: alles darunter rechnet damit.
        PSScaleRoot {
            Box(Modifier.fillMaxSize()) {
                inhalt()

                // Ueber allem: die Frage nach den Profilaenderungen
                // gehoert vor den Wechsel, nicht daneben.
                if (profilfrage.isNotEmpty()) {
                    ProfilWechselDialog(
                        aenderungen = profilfrage,
                        onVerwerfen = {
                            model.profilaenderungenVerwerfen()
                            profilfrage = emptyList()
                            route = Route.Simple
                        },
                        onNeuesProfil = { name ->
                            model.profilSichern(als = name)
                            profilfrage = emptyList()
                            route = Route.Simple
                        },
                        onUeberschreiben = {
                            model.profilUeberschreiben()
                            profilfrage = emptyList()
                            route = Route.Simple
                        },
                        onInsProjekt = {
                            // Nichts tun heisst: die Aenderungen bleiben
                            // im bearbeiteten Profil stehen und wandern
                            // beim Sichern ins 3MF.
                            profilfrage = emptyList()
                            route = Route.Simple
                        },
                        onAbbrechen = { profilfrage = emptyList() },
                    )
                }

                val result = model.credentialSelfTestResult
                if (result != null) {
                    Text(
                        result,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .testTag("credential.selftest")
                            .padding(1.dp)
                            .alpha(0.01f),
                    )
                }

                // Eine ZIP kennt keinen Modus - erst entpacken und laden
                // (loadZip), dann fragen, wo es weitergeht.
                val anzahl = zipAnzahl
                if (anzahl != null) {
                    ZipModusDialog(
                        anzahl = anzahl,
                        onSimple = { zipAnzahl = null; route = Route.Simple },
                        onAdvanced = { zipAnzahl = null; route = Route.Advanced },
                        ausZip = modusfrageAusZip,
                    )
                }
            }

            // Die Absturz-Rueckfrage ("Protokoll senden?") stand bis zum
            // 12.09.2026 hier. Abstuerze melden Play Console und
            // TestFlight selbst; eine oeffentliche Version schickt keine
            // Protokolle an einen privaten Server.
        }
    }
}

/// Der Startmodus aus den App-Einstellungen.
private fun startRouteAusEinstellungen(einstellungen: AppSettingsStore): Route =
    when (einstellungen.startMode) {
        AppSettings.START_SIMPLE -> Route.Simple
        AppSettings.START_ADVANCED -> Route.Advanced
        else -> Route.Start
    }

/// Der Dateiname hinter einer content://-Adresse - nur der laesst die
/// Endung erkennen, der Pfad einer Content-URI sagt darueber nichts.
private fun anzeigename(context: Context, uri: Uri): String {
    val angezeigt = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()
    return angezeigt ?: uri.lastPathSegment ?: ""
}
