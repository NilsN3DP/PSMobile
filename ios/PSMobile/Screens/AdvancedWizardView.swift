import SwiftUI
import PSMShared

/// Der Advanced-Assistent - Gegenstueck zu `AdvancedWizardScreen.kt`.
///
/// Bis hierher gingen die beiden Plattformen an dieser Stelle
/// auseinander: "Ersteinrichtung" auf der Startseite sprang auf iOS
/// direkt in den Einrichtungsassistenten, waehrend Android davor diesen
/// Bildschirm zeigt. Der Unterschied ist keiner der Optik - hier kann
/// man Drucker, Filament und Print Settings *direkt* waehlen, ohne die
/// Einrichtung noch einmal durchzugehen. Wer nur das Filament wechseln
/// will, musste auf iOS den Umweg ueber die Druckeinstellungen nehmen.
struct AdvancedWizardView: View {
    @ObservedObject var model: SlicerModel
    @Environment(\.psScale) private var ps
    let onClose: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(12)) {
                Text(st("Advanced wizard", "Advanced-Assistent"))
                    .font(.system(size: ps.font(24), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)

                Text(st("Load printers, filament and Print Settings, or choose them directly.",
                        "Drucker, Filament und Print Settings nachladen oder direkt auswählen."))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)

                Button { model.reopenSetup() } label: {
                    Text(st("Add or remove printer models",
                            "Druckermodelle hinzufügen oder entfernen"))
                        .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("assistent.einrichtung")

                auswahl(st("Printer", "Drucker"), .printer)
                auswahl(st("Filament", "Filament"), .filament)
                auswahl("Print Settings", .print)

                Button(action: onClose) {
                    Text(st("Open workspace", "Arbeitsfläche öffnen"))
                        .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("assistent.arbeitsflaeche")
            }
            .padding(ps.pt(20))
        }
        .background(PrusaColors.background)
    }

    private func auswahl(_ titel: String, _ art: PsmCore.PresetType) -> some View {
        AssistentAuswahl(
            titel: titel,
            gewaehlt: model.core?.selectedPreset(art) ?? "",
            moeglichkeiten: model.presetNames(art),
        ) { name in
            model.selectPreset(art, name)
        }
    }
}

/// Eine durchsuchbare Liste mit der aktuellen Wahl.
///
/// Hoechstens 24 Treffer auf einmal - bei ueber fuenftausend
/// Filamentprofilen ist eine vollstaendige Liste keine Auswahl mehr,
/// sondern eine Wand. Die Zahl steht unten, damit klar ist, dass da noch
/// mehr kommt, wenn man die Suche schaerft.
private struct AssistentAuswahl: View {
    let titel: String
    let gewaehlt: String
    let moeglichkeiten: [String]
    let onWahl: (String) -> Void

    @Environment(\.psScale) private var ps
    @State private var suche = ""

    private var treffer: [String] {
        let begriff = suche.trimmingCharacters(in: .whitespaces)
        guard !begriff.isEmpty else { return moeglichkeiten }
        return moeglichkeiten.filter { $0.localizedCaseInsensitiveContains(begriff) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(titel)
                .font(.system(size: ps.font(15), weight: .medium))
                .foregroundStyle(PrusaColors.textPrimary)

            TextField(st("Search", "Suchen"), text: $suche)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()

            ForEach(treffer.prefix(24), id: \.self) { name in
                Button { onWahl(name) } label: {
                    HStack {
                        Text(name)
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Spacer()
                        if name == gewaehlt {
                            Text("✓").foregroundStyle(PrusaColors.orange)
                        }
                    }
                    .padding(.horizontal, ps.pt(12))
                    .frame(minHeight: ps.touch(44))
                    .background(name == gewaehlt ? PrusaColors.panelRaised : PrusaColors.panel)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            if treffer.count > 24 {
                Text(st("\(treffer.count - 24) more matches – refine search.",
                        "\(treffer.count - 24) weitere Treffer – Suche verfeinern."))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
        }
        .padding(ps.pt(14))
        .background(PrusaColors.panel)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
    }
}
