import SwiftUI
import UniformTypeIdentifiers
import PSMShared

@main
struct PSMobileApp: App {

    /// Die Schalter, die *gespeicherten* Zustand loeschen, wirken hier -
    /// vor dem ersten Bild.
    ///
    /// Vorher standen sie in `SlicerModel.start()`, und das laeuft im
    /// `onAppear` der Wurzel. SwiftUI ruft das `onAppear` der Kinder
    /// aber frueher: `SetupView` hatte die alte Druckerwahl schon in
    /// seinen `@State` uebernommen, bevor `-psm-reset-setup` sie leerte.
    /// Der Abschluss war damit ohne Auswahl bedienbar, und SetupUITests
    /// meldete das seit dem ersten Lauf. Nachziehen half nicht:
    /// `storedPrinters` ist eine statische UserDefaults-Eigenschaft, an
    /// der kein `onChange` haengt.
    ///
    /// Auf Android wirken dieselben Schalter in `ensureCore`, und die
    /// Oberflaeche entsteht erst danach - dort stellte sich die Frage
    /// nie.
    init() {
        let argumente = ProcessInfo.processInfo.arguments
        if argumente.contains("-psm-reset-setup") {
            SlicerModel.storedPrinters = []
        }
        if argumente.contains("-psm-preset-printer"), SlicerModel.storedPrinters.isEmpty {
            SlicerModel.storedPrinters = ["PrusaResearch:MK4S:0.4"]
        }
    }

    @StateObject private var model = SlicerModel()
    @StateObject private var einstellungen = AppSettingsStore()
    @StateObject private var drucker = PrinterStore()

    /// Welcher Bildschirm gerade oben liegt.
    ///
    /// Bewusst ein Aufzaehlungstyp und keine Sammlung von Bool-Flaggen:
    /// mit vier unabhaengigen Schaltern gibt es sechzehn Zustaende, von
    /// denen fuenf sinnvoll sind - und irgendwann steht man in einem der
    /// anderen elf.
    enum Route {
        case start, simple, advanced, druckEinstellungen, appEinstellungen, remote
        /// Drucker einrichten - oder, mit einer Datei, den G-Code
        /// hinschicken. Derselbe Bildschirm, zwei Anlaesse.
        case drucker(URL?)
    }

    @State private var route: Route = .start
    /// Wohin die App-Einstellungen zurueckfuehren. Sie sind aus beiden
    /// Modi und vom Start aus erreichbar.
    @State private var zurueckVon: Route = .start
    /// Welcher Reiter der Einstellungen aufgeht. Der Advanced Mode hat
    /// fuer Druck, Filament und Drucker je einen eigenen Einstieg -
    /// dreimal derselbe Bildschirm waere die schlechtere Loesung.
    @State private var einstellungsReiter = "print"
    /// Wie viele Modelle aus einer geteilten ZIP entpackt wurden - > 0
    /// haelt die Nachfrage Easy/Advanced offen, bis eine Wahl faellt.
    @State private var zipAnzahl: Int?
    /// Ob die Frage nach dem Modus von einer ZIP kommt oder von einem
    /// einzelnen Modell, das vom Startbildschirm aus geoeffnet wurde.
    @State private var modusfrageAusZip = true
    /// Eine per Oeffnen-mit hereingekommene Datei, die auf das Ende der
    /// Einrichtung wartet - Gegenstueck zu externalOpen drueben.
    @State private var wartendeURL: URL?

    @Environment(\.scenePhase) private var scenePhase
    /// Nur fuer -psm-selbsttest-auto (siehe onAppear unten) - haelt den
    /// Selbsttest am Leben, waehrend sein Task.detached im Hintergrund
    /// laeuft. Ohne diese Referenz waere er sofort wieder weg und der
    /// [weak self] darin liefe ins Leere.
    @State private var autoSelbsttest: Selbsttest?

    var body: some Scene {
        WindowGroup {
            // Legt die Skalierung aus der Fenstergroesse fest. Muss ganz
            // aussen stehen: alles darunter rechnet damit.
            PSScaleRoot {
                inhalt
            }
                .environmentObject(model)
                // Dunkel wie Android (dort `darkColorScheme` erzwungen):
                // die eigenen Farben sind ohnehin fest dunkel, aber
                // System-Alerts, Sheets und Picker folgten bis zum
                // 13.09.2026 dem Systemmodus - auf einem hellen iPhone
                // standen helle Dialoge in einer dunklen App.
                .preferredColorScheme(.dark)
                // Ueber allem: die Frage nach den Profilaenderungen
                // gehoert vor den Wechsel, nicht daneben.
                .overlay {
                    if !profilfrage.isEmpty {
                        ProfilWechselDialog(
                            aenderungen: profilfrage,
                            onVerwerfen: {
                                model.profilaenderungenVerwerfen()
                                profilfrage = []
                                route = .simple
                            },
                            onNeuesProfil: { name in
                                model.profilSichern(als: name)
                                profilfrage = []
                                route = .simple
                            },
                            onUeberschreiben: {
                                model.profilUeberschreiben()
                                profilfrage = []
                                route = .simple
                            },
                            onInsProjekt: {
                                // Nichts tun heisst: die Aenderungen
                                // bleiben im bearbeiteten Profil stehen
                                // und wandern beim Sichern ins 3MF.
                                profilfrage = []
                                route = .simple
                            },
                            onAbbrechen: { profilfrage = [] })
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    if let result = model.credentialSelfTestResult {
                        Text(result)
                            .accessibilityIdentifier("credential.selftest")
                            .padding(1)
                            .opacity(0.01)
                    }
                }
                .overlay {
                    // Eine ZIP kennt keinen Modus - erst entpacken und
                    // laden (loadZip), dann fragen, wo es weitergeht.
                    if let anzahl = zipAnzahl {
                        ZipModusDialog(
                            anzahl: anzahl,
                            onSimple: { zipAnzahl = nil; route = .simple },
                            onAdvanced: { zipAnzahl = nil; route = .advanced },
                            ausZip: modusfrageAusZip)
                    }
                }
                .onAppear {
                    model.start()
                    route = startRoute()
                    // Headless-Selbsttest fuer die Fehlersuche ohne
                    // Geraet - per `xcrun simctl launch ... -psm-selbsttest-auto`
                    // gestartet, schreibt seinen Bericht wie gewohnt
                    // nach Documents/Selbsttest.
                    if ProcessInfo.processInfo.arguments.contains("-psm-selbsttest-auto") {
                        let test = Selbsttest()
                        autoSelbsttest = test
                        test.starten()
                    }
                }
                .onChange(of: scenePhase) { phase in
                    // Im Hintergrund sichern - Gegenstueck zu onStop()
                    // in MainActivity.kt. iOS raeumt eine App im
                    // Hintergrund genauso wortlos weg wie Android.
                    //
                    // Die Absturz-Rueckfrage ("Protokoll senden?"), die
                    // hier bis zum 12.09.2026 hing, ist weg: Abstuerze
                    // melden TestFlight und der Store selbst, und eine
                    // oeffentliche Version schickt keine Protokolle an
                    // einen privaten Server.
                    if phase == .background {
                        model.arbeitsstandSichern()
                    }
                }
                // Modelle, die aus anderen Apps geteilt werden. Eine
                // ZIP - meist von Printables, mit STL und Beiwerk - wird
                // erst entpackt statt wie eine einzelne Datei geladen.
                //
                // Solange die Einrichtung laeuft, wartet die Datei: ein
                // Import ohne Drucker landete sonst als "Slicing failed"
                // im ersten Modus, und die Datei war weg (S23 FE, erster
                // Start per Oeffnen-mit, 14.09.2026). Ein einzelnes Modell
                // vom Startbildschirm aus stellt dieselbe Frage wie die ZIP.
                .onOpenURL { url in
                    if model.setupNeeded || !model.printerIsReady { wartendeURL = url } else { oeffne(url) }
                }
                .onChange(of: model.setupNeeded) { _ in wartendeVerarbeiten() }
                .onChange(of: model.printerIsReady) { _ in wartendeVerarbeiten() }
        }
    }

    /// Die offenen Profilaenderungen, solange die Frage danach steht.
    @State private var profilfrage: [SlicerModel.Profilaenderung] = []

    private func wartendeVerarbeiten() {
        guard let url = wartendeURL, !model.setupNeeded, model.printerIsReady else { return }
        wartendeURL = nil
        oeffne(url)
    }

    private func oeffne(_ url: URL) {
        if url.pathExtension.lowercased() == "zip" {
            if let anzahl = model.loadZip(url: url), anzahl > 0 {
                modusfrageAusZip = true
                zipAnzahl = anzahl
            }
        } else {
            let vorher = model.objects.count
            // Eine 3MF auf ein leeres Bett ist ein Projekt - mit Drucker,
            // Filament und Druckprofil, wie am Desktop beim Oeffnen. Bis zum
            // 16.09.2026 kam nur die Geometrie an, die eingebettete
            // Konfiguration ging stillschweigend verloren (Zwilling: PSMobileApp.kt).
            if url.pathExtension.lowercased() == "3mf" && vorher == 0 {
                model.loadProject(url: url)
            } else {
                model.load(url: url)
            }
            if case .start = route {
                if model.objects.count > vorher {
                    modusfrageAusZip = false
                    zipAnzahl = 1
                } else if case .failed = model.progress {
                    // Gescheitert: die Meldung steht im Schnittblatt, und
                    // das gibt es nur in den Modi - sonst passiert sichtbar nichts.
                    route = Route.simple
                }
            }
        }
    }

    @ViewBuilder private var inhalt: some View {
        ZStack {
            basisInhalt

            if model.setupNeeded || !model.printerIsReady {
                SetupView(
                    models: model.printerModels,
                    busy: model.setupBusy,
                    preselected: model.installedPrinters,
                    onConfirm: { model.completeSetup($0) },
                    onLanguageChange: { einstellungen.language = $0 },
                    onClose: model.printerIsReady ? { model.dismissSetup() } : nil
                )
            } else if case .appEinstellungen = route {
                AppSettingsView(einstellungen: einstellungen) { route = zurueckVon }
            }
        }
    }

    @ViewBuilder private var basisInhalt: some View {
        if case .appEinstellungen = route {
            bildschirm(fuer: zurueckVon)
        } else {
            bildschirm(fuer: route)
        }
    }

    @ViewBuilder private func bildschirm(fuer route: Route) -> some View {
        switch route {
        case .appEinstellungen:
            WorkflowStartView(
                onSimple: { self.route = .simple },
                onAdvanced: { self.route = .advanced },
                onAppSettings: {},
                onPrinterSetup: { model.reopenSetup() },
                onRemote: { self.route = .remote }
            )
        case .start:
            WorkflowStartView(
                onSimple: { self.route = .simple },
                onAdvanced: { self.route = .advanced },
                onAppSettings: { zurueckVon = .start; self.route = .appEinstellungen },
                onPrinterSetup: { model.reopenSetup() },
                onRemote: { self.route = .remote }
            )
        case .simple:
            SimpleModeView(
                onHome: { self.route = .start },
                onOpenAdvanced: { self.route = .advanced },
                onOpenPrinterSetup: { model.reopenSetup() },
                onAppSettings: { zurueckVon = .simple; self.route = .appEinstellungen },
                onRemoteSettings: { zurueckVon = .simple; self.route = .remote },
                onSendToPrinter: { datei in
                    zurueckVon = .simple
                    self.route = .drucker(datei)
                }
            )
        case .advanced:
            AdvancedWorkspaceView(
                onHome: { self.route = .start },
                onOpenSimple: {
                    let offen = model.profilaenderungen()
                    if offen.isEmpty { self.route = .simple } else { profilfrage = offen }
                },
                onAppSettings: { zurueckVon = .advanced; self.route = .appEinstellungen },
                onPrinters: { zurueckVon = .advanced; self.route = .drucker(nil) },
                onSendToPrinter: { datei in
                    zurueckVon = .advanced
                    self.route = .drucker(datei)
                },
                onPrinterSetup: { model.reopenSetup() },
                onSettings: { reiter in
                    einstellungsReiter = reiter
                    self.route = .druckEinstellungen
                },
                onRemoteSettings: { zurueckVon = .advanced; self.route = .remote }
            )
        case .druckEinstellungen:
            SettingsView(model: model, startTab: einstellungsReiter) { self.route = .advanced }
        case .remote:
            RemoteSliceView(onHome: { self.route = zurueckVon })
        case .drucker(let datei):
            PrintersView(
                store: drucker,
                senden: datei,
                dateiname: datei?.lastPathComponent ?? "psmobile.gcode",
                passendesProfil: model.selectedPreset(for: "printer"),
                profile: model.presetNames(.printer)
            ) { self.route = zurueckVon }
        }
    }

    /// Der Startmodus aus den App-Einstellungen. Die UI-Tests koennen ihn
    /// per Startargument uebergehen - eine Einstellung dafuer in der App
    /// waere ein Schalter, den nur die Tests brauchen.
    private func startRoute() -> Route {
        let argumente = ProcessInfo.processInfo.arguments
        if argumente.contains("-psm-start-simple") { return .simple }
        if argumente.contains("-psm-start-advanced") { return .advanced }

        switch einstellungen.startMode {
        case AppSettings.shared.START_SIMPLE:   return .simple
        case AppSettings.shared.START_ADVANCED: return .advanced
        default:                                return .start
        }
    }
}
