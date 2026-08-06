import SwiftUI
import PSMShared

/// Nach dem Teilen einer ZIP: wohin mit den entpackten Modellen.
///
/// Die ZIP selbst kennt keinen Modus - das ist eine reine
/// Container-Datei, meist von Printables, mit STL und Beiwerk drin.
/// Die Modelle sind zu diesem Zeitpunkt schon geladen (siehe
/// SlicerModel.loadZip); hier wird nur entschieden, auf welchem
/// Bildschirm man sie zuerst sieht.
struct ZipModusDialog: View {

    let anzahl: Int
    var onSimple: () -> Void
    var onAdvanced: () -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        SchwebenderDialog(kennung: "dialog.zip", maximaleBreite: ps.pt(420)) {
            VStack(alignment: .leading, spacing: ps.pt(16)) {
                Text(st("Models from ZIP", "Modelle aus der ZIP"))
                    .font(.system(size: ps.font(18), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(st(
                    "\(anzahl) model file(s) were unpacked and added to the bed. Which mode?",
                    "\(anzahl) Modelldatei(en) wurden entpackt und aufs Bett gelegt. In welchem Modus weiter?"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: ps.pt(10)) {
                    Button(action: onSimple) {
                        Text(st("Simple Mode", "Simple Mode"))
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: ps.touch(48))
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(PrusaColors.orange)
                    .accessibilityIdentifier("zip.simple")

                    Button(action: onAdvanced) {
                        Text(st("Advanced Mode", "Advanced Mode"))
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: ps.touch(48))
                    }
                    .buttonStyle(.bordered)
                    .tint(PrusaColors.orange)
                    .accessibilityIdentifier("zip.advanced")
                }
            }
            .padding(ps.pt(20))
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "zipwahl") }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
