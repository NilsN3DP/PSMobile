import SwiftUI
import PSMShared

/// Profilsuche fuer Druck-, Filament- und Druckereinstellungen.
///
/// Bis hierher gab es in den Einstellungsseiten nur den Namen des
/// geltenden Profils zu lesen - wer wechseln wollte, musste den
/// Bildschirm verlassen und ueber den Advanced-Kopf gehen. Filamentlisten
/// haben je nach Drucker mehrere hundert Eintraege; ein Suchfeld ist hier
/// keine Zierde, sondern der einzige zumutbare Weg zum eigenen Profil.
struct ProfileSearchSheet: View {

    @ObservedObject var model: SlicerModel
    /// "print", "filament" oder "printer" - derselbe String wie
    /// `SettingsView.tab`.
    let tab: String
    let titel: String
    @Binding var isPresented: Bool

    @Environment(\.psScale) private var ps
    @State private var suche = ""

    private var aktuell: String? { model.selectedPreset(for: tab) }
    private var alle: [String] { model.presetNames(for: tab) }
    private var treffer: [String] {
        guard !suche.isEmpty else { return alle }
        return alle.filter { $0.localizedCaseInsensitiveContains(suche) }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                suchfeld
                if treffer.isEmpty {
                    Text(st("No profile matches the search.",
                            "Kein Profil passt zur Suche."))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .padding(.top, ps.pt(24))
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: ps.pt(2)) {
                            ForEach(treffer, id: \.self) { name in
                                zeile(name)
                            }
                        }
                        .padding(.vertical, ps.pt(6))
                    }
                }
            }
            .background(PrusaColors.background)
            .navigationTitle(titel)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(st("Done", "Fertig")) { isPresented = false }
                        .accessibilityIdentifier("profilsuche.fertig")
                }
            }
            .overlay { PSMarke(name: "profilsuche") }
        }
        .presentationDetents([.medium, .large])
    }

    private var suchfeld: some View {
        HStack(spacing: ps.pt(6)) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(PrusaColors.textMuted)
            TextField(st("Search profiles", "Profile durchsuchen"), text: $suche)
                .font(.system(size: ps.font(14)))
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier("profilsuche.suche")
            if !suche.isEmpty {
                Button { suche = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.touch(32), height: ps.touch(32))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("profilsuche.leeren")
            }
        }
        .padding(.horizontal, ps.pt(12))
        .frame(height: ps.touch(46))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        .padding(ps.pt(12))
    }

    private func zeile(_ name: String) -> some View {
        let gewaehlt = name == aktuell
        return Button {
            model.selectPreset(for: tab, name)
            isPresented = false
        } label: {
            HStack(spacing: ps.pt(8)) {
                Text(name)
                    .font(.system(size: ps.font(14),
                                  weight: gewaehlt ? .semibold : .regular))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: ps.pt(8))
                if gewaehlt {
                    Image(systemName: "checkmark")
                        .foregroundStyle(PrusaColors.orange)
                }
            }
            .padding(.horizontal, ps.pt(16))
            .frame(minHeight: ps.touch(46))
            .background(gewaehlt ? PrusaColors.panelRaised : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("profilsuche.eintrag." + name)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
