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

    var body: some View {
        VStack(spacing: 0) {
            kopfzeile
            Divider().background(PrusaColors.divider)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(AppSettings.shared.groupsInOrder, id: \.self) { gruppe in
                        abschnitt(gruppe)
                    }
                }
                .padding(.horizontal, ps.pt(16))
                .padding(.bottom, ps.pt(24))
                .frame(maxWidth: ps.pt(760))
                .frame(maxWidth: .infinity)
            }
        }
        .background(PrusaColors.background)
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
}
