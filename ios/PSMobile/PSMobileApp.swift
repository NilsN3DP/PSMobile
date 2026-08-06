import SwiftUI
import UniformTypeIdentifiers
import PSMShared

@main
struct PSMobileApp: App {
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
        case start, simple, advanced, druckEinstellungen, appEinstellungen
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

    var body: some Scene {
        WindowGroup {
            // Legt die Skalierung aus der Fenstergroesse fest. Muss ganz
            // aussen stehen: alles darunter rechnet damit.
            PSScaleRoot {
                inhalt
            }
                .environmentObject(model)
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
                            onAdvanced: { zipAnzahl = nil; route = .advanced })
                    }
                }
                .onAppear {
                    model.start()
                    route = startRoute()
                }
                // Modelle, die aus anderen Apps geteilt werden. Eine
                // ZIP - meist von Printables, mit STL und Beiwerk - wird
                // erst entpackt statt wie eine einzelne Datei geladen.
                .onOpenURL { url in
                    if url.pathExtension.lowercased() == "zip" {
                        if let anzahl = model.loadZip(url: url), anzahl > 0 {
                            zipAnzahl = anzahl
                        }
                    } else {
                        model.load(url: url)
                    }
                }
        }
    }

    /// Die offenen Profilaenderungen, solange die Frage danach steht.
    @State private var profilfrage: [SlicerModel.Profilaenderung] = []

    @ViewBuilder private var inhalt: some View {
        ZStack {
            basisInhalt

            if model.setupNeeded {
                SetupView(
                    models: model.printerModels,
                    busy: model.setupBusy,
                    preselected: model.installedPrinters,
                    onConfirm: { model.completeSetup($0) },
                    onLanguageChange: { einstellungen.language = $0 },
                    onClose: model.installedPrinters.isEmpty ? nil : { model.dismissSetup() }
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
                onPrinterSetup: { model.reopenSetup() }
            )
        case .start:
            WorkflowStartView(
                onSimple: { self.route = .simple },
                onAdvanced: { self.route = .advanced },
                onAppSettings: { zurueckVon = .start; self.route = .appEinstellungen },
                onPrinterSetup: { model.reopenSetup() }
            )
        case .simple:
            SimpleModeView(
                onHome: { self.route = .start },
                onOpenAdvanced: { self.route = .advanced },
                onOpenPrinterSetup: { model.reopenSetup() },
                onAppSettings: { zurueckVon = .simple; self.route = .appEinstellungen },
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
                }
            )
        case .druckEinstellungen:
            SettingsView(model: model, startTab: einstellungsReiter) { self.route = .advanced }
        case .drucker(let datei):
            PrintersView(
                store: drucker,
                senden: datei,
                dateiname: datei?.lastPathComponent ?? "psmobile.gcode"
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
