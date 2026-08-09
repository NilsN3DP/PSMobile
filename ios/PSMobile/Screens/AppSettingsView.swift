import SwiftUI
import PSMShared

/// Einstellungen der App - Gegenstueck zu `AppSettingsScreen.kt`.
///
/// Bewusst getrennt von den Druckeinstellungen: dort geht es um das
/// Werkstueck, hier um das Programm. Wer die Modellvorschau abschalten
/// will, sucht sie nicht zwischen Schichthoehe und Fuelldichte.
///
/// Welche Schalter es gibt, in welcher Gruppe sie stehen und warum -
/// das alles kommt aus `AppSettings` im gemeinsamen Modul. Hier steht
/// nur, wie es auf iOS aussieht und wo die Werte liegen.
struct AppSettingsView: View {

    @ObservedObject var einstellungen: AppSettingsStore
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var zeigeSelbsttest = false
    @State private var protokollDatei: URL = LogExport.exportFile()
        ?? FileManager.default.temporaryDirectory.appendingPathComponent("psmobile-protokoll.txt")

    var body: some View {
        SchwebenderDialog(kennung: "dialog.appeinstellungen", maximaleBreite: ps.pt(760)) {
            VStack(spacing: 0) {
                kopfzeile
                Divider().background(PrusaColors.divider)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(AppSettings.shared.groupsInOrder, id: \.self) { gruppe in
                            abschnitt(gruppe)
                        }
                        diagnose
                    }
                    .padding(.horizontal, ps.pt(16))
                    .padding(.bottom, ps.pt(24))
                    .frame(maxWidth: ps.pt(760))
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "appeinstellungen") }
    }

    private var kopfzeile: some View {
        HStack(spacing: ps.pt(16)) {
            Button(action: onClose) {
                Text("‹  " + st("Back", "Zurück"))
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.orange)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("appeinstellungen.zurueck")

            Text(st("App settings", "App-Einstellungen"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Spacer()
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(56))
    }

    @ViewBuilder private func abschnitt(_ gruppe: AppSettings.Group) -> some View {
        let schalter = AppSettings.shared.group(g: gruppe)
        let istDarstellung = gruppe == AppSettings.Group.appearance
        if !schalter.isEmpty || istDarstellung {
            Text(AppSettings.shared.groupTitle(g: gruppe).uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.top, ps.pt(20))
                .padding(.bottom, ps.pt(6))

            // Sprache und Startmodus sind keine Ja/Nein-Fragen und stehen
            // deshalb als Auswahl in der Darstellung.
            if istDarstellung {
                auswahlZeile(
                    titel: st("Language", "Sprache"),
                    warum: st("Labels come from PrusaSlicer's own catalog.",
                              "Die Beschriftungen stammen aus PrusaSlicers eigenem Katalog."),
                    werte: ["en", "de"],
                    gewaehlt: einstellungen.language,
                    beschriftung: { $0 == "de" ? "Deutsch" : "English" },
                    kennung: "appeinstellungen.sprache",
                    waehlen: { einstellungen.language = $0 })
                auswahlZeile(
                    titel: st("On start", "Beim Start"),
                    warum: st("Skip the mode question if you always use the same one.",
                              "Die Modusfrage entfällt, wenn man ohnehin immer denselben nimmt."),
                    werte: AppSettings.shared.startModes,
                    gewaehlt: einstellungen.startMode,
                    beschriftung: { AppSettings.shared.startModeLabel(value: $0) },
                    kennung: "appeinstellungen.startmodus",
                    waehlen: { einstellungen.startMode = $0 })
            }

            ForEach(schalter, id: \.key) { eintrag in
                schalterZeile(eintrag)
            }
        }
    }

    /// Der Selbsttest, ganz unten.
    ///
    /// Er gehoert nicht zwischen die Schalter: das sind Vorlieben, das
    /// hier ist ein Werkzeug. Und er gehoert in die App und nicht in
    /// einen Testlauf am Rechner - was auf diesem Geraet gilt, weiss
    /// nur dieses Geraet.
    private var diagnose: some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(st("Diagnostics", "Diagnose").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.top, ps.pt(20))
                .padding(.bottom, ps.pt(6))

            Button { zeigeSelbsttest = true } label: {
                HStack(spacing: ps.pt(12)) {
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(st("Self-test", "Selbsttest"))
                            .font(.system(size: ps.font(14)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Text(st("Runs loading, slicing, saving, painting and a load test on this device, and writes a report.",
                                "Prüft Laden, Schneiden, Sichern, Bemalen und eine Volllast auf diesem Gerät und schreibt einen Bericht."))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Text("›")
                        .font(.system(size: ps.font(18)))
                        .foregroundStyle(PrusaColors.orange)
                }
                .padding(.horizontal, ps.pt(14))
                .padding(.vertical, ps.pt(10))
                .frame(minHeight: ps.touch(56))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("appeinstellungen.selbsttest")
            .sheet(isPresented: $zeigeSelbsttest) {
                SelbsttestView { zeigeSelbsttest = false }
            }

            ShareLink(item: protokollDatei) {
                HStack(spacing: ps.pt(12)) {
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(st("Export log", "Protokoll exportieren"))
                            .font(.system(size: ps.font(14)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Text(st("The last warnings and errors from the core, as a text file to share.",
                                "Die letzten Warnungen und Fehler aus dem Kern, als teilbare Textdatei."))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: ps.font(16)))
                        .foregroundStyle(PrusaColors.orange)
                }
                .padding(.horizontal, ps.pt(14))
                .padding(.vertical, ps.pt(10))
                .frame(minHeight: ps.touch(56))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
                .contentShape(Rectangle())
            }
            // Bei jedem Erscheinen frisch schreiben statt einmal beim
            // ersten Aufbau der Ansicht - sonst zeigt der Export den
            // Stand von vor dem Oeffnen der Einstellungen, nicht den
            // aktuellen.
            .onAppear { protokollDatei = LogExport.exportFile() ?? protokollDatei }
            .accessibilityIdentifier("appeinstellungen.protokoll")

            versionszeile
        }
    }

    private var versionszeile: some View {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return Text("PSMobile \(version) (\(build))")
            .font(.system(size: ps.font(11)))
            .foregroundStyle(PrusaColors.textMuted)
            .padding(.top, ps.pt(16))
            .frame(maxWidth: .infinity, alignment: .center)
            .accessibilityIdentifier("appeinstellungen.version")
    }

    private func schalterZeile(_ schalter: AppSettings.Toggle) -> some View {
        HStack(spacing: ps.pt(12)) {
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(schalter.title.text)
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(schalter.why.text)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { einstellungen.bool(schalter.key, standard: schalter.standard) },
                set: { einstellungen.set(schalter.key, $0) }
            ))
            .labelsHidden()
            .tint(PrusaColors.orange)
            .accessibilityIdentifier("appeinstellungen.\(schalter.key)")
        }
        .padding(.horizontal, ps.pt(14))
        .padding(.vertical, ps.pt(10))
        .frame(minHeight: ps.touch(56))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
        .padding(.bottom, ps.pt(6))
    }

    private func auswahlZeile(titel: String,
                              warum: String,
                              werte: [String],
                              gewaehlt: String,
                              beschriftung: @escaping (String) -> String,
                              kennung: String,
                              waehlen: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(titel)
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(warum)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // Ein Menue statt einer Reihe Knoepfe: die Beschriftungen des
            // Startmodus sind zu lang fuer nebeneinander, und auf einem
            // iPhone im Hochformat bricht die Reihe sonst um.
            Menu {
                ForEach(werte, id: \.self) { wert in
                    Button(beschriftung(wert)) { waehlen(wert) }
                }
            } label: {
                HStack {
                    Text(beschriftung(gewaehlt))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: ps.font(10)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .padding(.horizontal, ps.pt(12))
                .frame(height: ps.touch(44))
                .background(PrusaColors.background)
                .overlay(
                    RoundedRectangle(cornerRadius: ps.pt(6))
                        .stroke(PrusaColors.divider, lineWidth: 1)
                )
            }
            .accessibilityIdentifier(kennung)
        }
        .padding(.horizontal, ps.pt(14))
        .padding(.vertical, ps.pt(10))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
        .padding(.bottom, ps.pt(6))
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Die Werte hinter den App-Einstellungen.
///
/// Gegenstueck zu den SharedPreferences auf Android: dieselben
/// Schluessel aus `AppSettings`, nur in `UserDefaults`. Die Schluessel
/// stehen im gemeinsamen Modul, damit eine spaeter ergaenzte Einstellung
/// nicht auf einer Seite vergessen wird.
@MainActor
final class AppSettingsStore: ObservableObject {

    @Published var language: String {
        didSet {
            UserDefaults.standard.set(language, forKey: Self.languageKey)
            // Der Katalog haelt sowohl die Beschriftungen als auch
            // Lang.current fuer das gemeinsame Modul.
            PsUiCatalog.load(language: language)
        }
    }

    @Published var startMode: String {
        didSet { UserDefaults.standard.set(startMode, forKey: AppSettings.shared.KEY_START_MODE) }
    }

    /// Nur damit die Oberflaeche sich neu zeichnet - die Wahrheit steht
    /// in UserDefaults.
    @Published private var revision = 0

    private static let languageKey = "ui.language"

    init() {
        language = UserDefaults.standard.string(forKey: Self.languageKey) ?? "en"
        startMode = UserDefaults.standard.string(forKey: AppSettings.shared.KEY_START_MODE)
            ?? AppSettings.shared.START_ASK
    }

    func bool(_ key: String, standard: Bool) -> Bool {
        _ = revision
        // `object(forKey:)` unterscheidet "nicht gesetzt" von "false" -
        // `bool(forKey:)` kann das nicht, und ein Schalter mit Standard
        // "an" waere beim ersten Start faelschlich aus.
        guard UserDefaults.standard.object(forKey: key) != nil else { return standard }
        return UserDefaults.standard.bool(forKey: key)
    }

    func set(_ key: String, _ value: Bool) {
        UserDefaults.standard.set(value, forKey: key)
        revision += 1
    }

    var thumbnails: Bool {
        bool(AppSettings.shared.KEY_THUMBNAILS, standard: true)
    }

    var showIncompatible: Bool {
        bool(AppSettings.shared.KEY_SHOW_INCOMPATIBLE, standard: false)
    }

    var multiBedRender: Bool {
        bool(AppSettings.shared.KEY_MULTI_BED_RENDER, standard: true)
    }
}
