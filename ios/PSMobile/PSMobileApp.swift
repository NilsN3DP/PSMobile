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
                .onAppear {
                    model.start()
                    route = startRoute()
                }
                // Modelle, die aus anderen Apps geteilt werden
                .onOpenURL { model.load(url: $0) }
        }
    }

    /// Die offenen Profilaenderungen, solange die Frage danach steht.
    @State private var profilfrage: [SlicerModel.Profilaenderung] = []

    @ViewBuilder private var inhalt: some View {
        // Ohne Drucker gibt es nichts zu zeigen - die Ersteinrichtung
        // kommt vor allem anderen.
        if model.setupNeeded {
            SetupView(
                models: model.printerModels,
                busy: model.setupBusy,
                preselected: model.installedPrinters,
                onConfirm: { model.completeSetup($0) },
                onLanguageChange: { einstellungen.language = $0 }
            )
        } else {
            switch route {
            case .start:
                WorkflowStartView(
                    onSimple: { route = .simple },
                    onAdvanced: { route = .advanced },
                    onAppSettings: { zurueckVon = .start; route = .appEinstellungen },
                    onPrinterSetup: { model.reopenSetup() }
                )
            case .simple:
                SimpleModeView(
                    onHome: { route = .start },
                    onOpenAdvanced: { route = .advanced },
                    onOpenPrinterSetup: { model.reopenSetup() },
                    onAppSettings: { zurueckVon = .simple; route = .appEinstellungen },
                    onSendToPrinter: { datei in
                        zurueckVon = .simple
                        route = .drucker(datei)
                    }
                )
            case .advanced:
                AdvancedWorkspaceView(
                    onHome: { route = .start },
                    onOpenSimple: {
                        // Der Einfache Modus arbeitet auf dem Profil.
                        // Sind Werte geaendert, die es dort nicht gibt,
                        // faellt die Entscheidung darueber vor dem
                        // Wechsel - nicht stillschweigend danach.
                        let offen = model.profilaenderungen()
                        if offen.isEmpty { route = .simple } else { profilfrage = offen }
                    },
                    onAppSettings: { zurueckVon = .advanced; route = .appEinstellungen },
                    onPrinters: { zurueckVon = .advanced; route = .drucker(nil) },
                    onPrinterSetup: { model.reopenSetup() },
                    onSettings: { reiter in
                        einstellungsReiter = reiter
                        route = .druckEinstellungen
                    }
                )
            case .druckEinstellungen:
                SettingsView(model: model, startTab: einstellungsReiter) { route = .advanced }
            case .appEinstellungen:
                AppSettingsView(einstellungen: einstellungen) { route = zurueckVon }
            case .drucker(let datei):
                PrintersView(
                    store: drucker,
                    senden: datei,
                    dateiname: datei?.lastPathComponent ?? "psmobile.gcode"
                ) { route = zurueckVon }
            }
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
