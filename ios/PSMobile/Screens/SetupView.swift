import SwiftUI
import PSMShared

/// Ersteinrichtung - Gegenstueck zum Konfigurationsassistenten des
/// Desktops und zu SetupScreen.kt auf Android.
///
/// Der Zweck ist nicht Kosmetik: Werden alle 37 Druckermodelle
/// installiert, dauert der Start 13,5 s und die Auswahllisten enthalten
/// 5762 Filamente. Mit einem einzelnen gewaehlten Drucker sind es 1,9 s
/// und 189 Filamente.
///
/// Die Gruppierung nach Familien kommt aus dem gemeinsamen Modul
/// (E-13) - dieselbe Reihenfolge wie auf Android, dieselbe Regel fuer
/// Altgeraete am Ende.
struct SetupView: View {

    let models: [PsmCore.PrinterModel]
    let busy: Bool
    /// Bisher installierte Modelle, Format "vendor:model:variant".
    let preselected: Set<String>
    let onConfirm: ([String]) -> Void
    let onLanguageChange: (String) -> Void

    @Environment(\.psScale) private var ps
    @State private var selected: Set<String> = []
    @State private var showSla = false
    @State private var query = ""
    @State private var expandedFamilies: Set<String> = []

    /// Ist es eng? Dann faellt Beiwerk weg, statt alles zu schrumpfen.
    /// Dieselbe Frage wie UiScale.density auf Android; die Schwelle
    /// stammt von dort.
    private var tight: Bool { ps.factor <= 0.8 }

    private var shown: [PsmCore.PrinterModel] {
        models.filter { m in
            (showSla || !m.isSla) &&
            (query.trimmingCharacters(in: .whitespaces).isEmpty ||
             m.name.localizedCaseInsensitiveContains(query))
        }
    }

    /// Eine Familie mit ihren Modellen.
    ///
    /// Die Regel im gemeinsamen Modul liefert Positionen statt Modelle -
    /// so ueberlebt sie die Bruecke, denn Kotlins Typparameter gehen nach
    /// Swift verloren. Hier zurueck zu den eigenen Objekten.
    private struct Familie: Identifiable {
        let name: String
        let isLegacy: Bool
        let modelle: [PsmCore.PrinterModel]
        var id: String { name }
    }

    private var groups: [Familie] {
        let liste = shown
        return PrinterGrouping.shared.group(families: liste.map { $0.family })
            .map { g in
                Familie(name: g.family,
                        isLegacy: g.isLegacy,
                        modelle: g.indices.map { liste[$0.intValue] })
            }
    }

    var body: some View {
        ZStack {
            PrusaColors.background.ignoresSafeArea()

            VStack(alignment: .leading, spacing: ps.pt(tight ? 6 : 14)) {
                kopfzeile
                suchfeld
                liste
                abschluss
            }
            .padding(ps.pt(tight ? 12 : 24))
            .frame(maxWidth: ps.pt(760))
        }
        .onAppear { selected = preselected }
    }

    // MARK: - Teile

    private var kopfzeile: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(SimpleModeState.shared.text(english: "Configuration Assistant",
                                                 german: "Ersteinrichtung"))
                    .font(.system(size: ps.font(tight ? 18 : 22), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)

                // Auf engen Schirmen faellt der Untertitel weg. Er
                // erklaert nichts, was die Liste darunter nicht selbst
                // zeigt, kostet aber eine Zeile, die dann der Liste
                // fehlt.
                if !tight {
                    Text(SimpleModeState.shared.text(
                            english: "Select all printers you want to use.",
                            german: "Waehle alle Drucker, die du nutzen willst."))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
            }
            Spacer()

            Toggle(isOn: $showSla) {
                Text(SimpleModeState.shared.text(english: "SLA", german: "SLA"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            .toggleStyle(.switch)
            .tint(PrusaColors.orange)
            .fixedSize()
        }
    }

    private var suchfeld: some View {
        // Der Platzhalter wird selbst gezeichnet. SwiftUI faerbt seinen
        // eigenen in einem Grau, das auf dem dunklen Feld praktisch
        // unsichtbar ist - das Feld sah aus wie ein leerer Kasten ohne
        // Hinweis, wozu er da ist.
        ZStack(alignment: .leading) {
            if query.isEmpty {
                Text(SimpleModeState.shared.text(english: "Search printer model",
                                                 german: "Druckermodell suchen"))
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .padding(.horizontal, ps.pt(12))
                    .allowsHitTesting(false)
            }
            TextField("", text: $query)
                .textFieldStyle(.plain)
                .font(.system(size: ps.font(15)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(ps.pt(12))
                .autocorrectionDisabled()
        }
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedCornerShape(ps.pt(6)))
    }

    private var liste: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(groups) { gruppe in
                    familienKopf(gruppe)
                    if expandedFamilies.contains(gruppe.name) || !query.isEmpty {
                        ForEach(gruppe.modelle, id: \.key) { modell in
                            modellZeile(modell)
                        }
                    }
                }
            }
        }
        .frame(maxHeight: .infinity)
    }

    private func familienKopf(_ gruppe: Familie) -> some View {
        Button {
            if expandedFamilies.contains(gruppe.name) {
                expandedFamilies.remove(gruppe.name)
            } else {
                expandedFamilies.insert(gruppe.name)
            }
        } label: {
            HStack {
                Text(gruppe.name.uppercased())
                    .font(.system(size: ps.font(12), weight: .semibold))
                    .foregroundStyle(gruppe.isLegacy
                                     ? PrusaColors.textMuted : PrusaColors.orange)
                Text("\(gruppe.modelle.count)")
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Image(systemName: expandedFamilies.contains(gruppe.name)
                      ? "chevron.down" : "chevron.right")
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            .padding(.vertical, ps.pt(8))
        }
        .buttonStyle(.plain)
    }

    private func modellZeile(_ modell: PsmCore.PrinterModel) -> some View {
        // Jede Duesengroesse ist eine eigene Wahl - PrusaSlicer fuehrt sie
        // als getrennte Profile, und wer eine 0.6er Duese hat, braucht
        // nicht die 0.4er Profile.
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(modell.name)
                .font(.system(size: ps.font(tight ? 14 : 15)))
                .foregroundStyle(PrusaColors.textPrimary)

            HStack(spacing: ps.pt(6)) {
                ForEach(modell.variants, id: \.self) { variante in
                    let key = "\(modell.key):\(variante)"
                    Button {
                        if selected.contains(key) { selected.remove(key) }
                        else { selected.insert(key) }
                    } label: {
                        Text(variante)
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(selected.contains(key)
                                             ? .white : PrusaColors.textMuted)
                            .padding(.horizontal, ps.pt(10))
                            .frame(height: ps.touch(tight ? 38 : 48))
                            .background(selected.contains(key)
                                        ? PrusaColors.orange : PrusaColors.panelRaised)
                            .clipShape(RoundedCornerShape(ps.pt(4)))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, ps.pt(tight ? 6 : 12))
    }

    private var abschluss: some View {
        Button {
            onConfirm(Array(selected))
        } label: {
            HStack {
                Spacer()
                if busy {
                    ProgressView().tint(.white)
                } else {
                    Text(SimpleModeState.shared.text(english: "Finish",
                                                     german: "Fertig"))
                        .font(.system(size: ps.font(16), weight: .semibold))
                }
                Spacer()
            }
            .frame(height: ps.touch(tight ? 46 : 58))
            .background(selected.isEmpty ? PrusaColors.panelRaised : PrusaColors.orange)
            .foregroundStyle(selected.isEmpty ? PrusaColors.textMuted : .white)
            .clipShape(RoundedCornerShape(ps.pt(6)))
        }
        .buttonStyle(.plain)
        .disabled(selected.isEmpty || busy)
    }
}

/// Abkuerzung, weil RoundedRectangle(cornerRadius:) sich hier zwanzigmal
/// wiederholt haette.
private func RoundedCornerShape(_ radius: CGFloat) -> RoundedRectangle {
    RoundedRectangle(cornerRadius: radius)
}
