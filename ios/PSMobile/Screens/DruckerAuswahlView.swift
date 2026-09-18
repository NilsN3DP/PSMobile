import SwiftUI
import PSMShared

/// Drucker wählen — als Karten, nicht als Klappliste.
///
/// Stand vorher nur im Simple Mode. Der Expertenmodus hatte an dieser
/// Stelle ein Aufklappmenü mit rohen Profilnamen, und der Nutzer hat
/// gesagt, was daran nicht stimmt: die Karten sind auf einem Gerät
/// einfach besser zu bedienen. Ein Menü mit dreißig Zeilen ist auf einem
/// Bildschirm, den man in der Hand hält, kein Menü, sondern eine
/// Zumutung.
///
/// Welche Profile zu einem Modell gehören und welche Düsen es dazu gibt,
/// entscheidet `EasyModeState` im gemeinsamen Modul.
struct DruckerAuswahlView: View {

    @ObservedObject var model: SlicerModel
    var onSetup: () -> Void = {}
    var onWahl: (String) -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        let modelle = EasyModeState.shared.printerModelsWithNozzles(
            rawPresets: model.presetNames(.printer))
        let gewaehlt = model.selectedPreset(for: "printer") ?? ""
        return VStack(alignment: .leading, spacing: ps.pt(10)) {
            if modelle.isEmpty {
                Text(SimpleModeState.shared.text(english: "No printer configured",
                                                 german: "Noch kein Drucker eingerichtet"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                Button(action: onSetup) {
                    Text(SimpleModeState.shared.text(english: "Set up printer",
                                                     german: "Drucker einrichten"))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("drucker.einrichten")
            } else {
                ForEach(Array(modelle.enumerated()), id: \.offset) { _, modell in
                    ForEach(Array(modell.variants.enumerated()), id: \.offset) { _, wahl in
                        karte(modell: modell.label,
                              duese: wahl.label,
                              gewaehlt: wahl.rawPreset == gewaehlt) {
                            onWahl(wahl.rawPreset)
                        }
                    }
                }
                plusKachel
            }
        }
    }

    /// Der Weg zu einem weiteren Drucker, als Kachel unter den anderen.
    ///
    /// Gleiche Groesse, gleicher Rahmen: sie gehoert in die Reihe und
    /// ist keine Fussnote. Wer im Druckerreiter steht und einen zweiten
    /// anlegen will, sucht genau hier - und fand bisher nichts.
    private var plusKachel: some View {
        Button(action: onSetup) {
            HStack(spacing: ps.pt(10)) {
                Text("+")
                    .font(.system(size: ps.font(30), weight: .light))
                    .foregroundStyle(PrusaColors.orange)
                    .frame(width: ps.pt(52), height: ps.pt(60))
                    .background(PrusaColors.panel)
                VStack(alignment: .leading, spacing: ps.pt(2)) {
                    Text(SimpleModeState.shared.text(english: "Add printer",
                                                     german: "Drucker hinzufügen"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(SimpleModeState.shared.text(english: "Another model or nozzle",
                                                     german: "Weiteres Modell oder andere Düse"))
                        .font(.system(size: ps.font(10)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                Spacer()
            }
            .padding(ps.pt(12))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(PrusaColors.panelRaised)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(4))
                    .stroke(PrusaColors.divider, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("drucker.karte.hinzufuegen")
    }

    private func karte(modell: String,
                       duese: String,
                       gewaehlt: Bool,
                       aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(alignment: .leading, spacing: ps.pt(5)) {
                HStack(spacing: ps.pt(10)) {
                    Text("▤")
                        .font(.system(size: ps.font(26)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.pt(52), height: ps.pt(60))
                        .background(PrusaColors.panel)
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(modell)
                            .font(.system(size: ps.font(14)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .lineLimit(2)
                        Text(gewaehlt
                             ? SimpleModeState.shared.text(english: "SELECTED · OFFLINE",
                                                           german: "AUSGEWÄHLT · OFFLINE")
                             : "OFFLINE")
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(gewaehlt ? PrusaColors.orange : PrusaColors.textMuted)
                    }
                    Spacer()
                }
                Divider().background(PrusaColors.divider)
                Text(SimpleModeState.shared.text(english: "Nozzle", german: "Düse") + "  " + duese)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textPrimary)
            }
            .padding(ps.pt(12))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(PrusaColors.panelRaised)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(4))
                    .stroke(gewaehlt ? PrusaColors.orange : PrusaColors.divider,
                            lineWidth: gewaehlt ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("drucker.karte." + modell + "." + duese)
    }
}
