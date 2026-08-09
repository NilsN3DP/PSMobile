import SwiftUI
import PSMShared

/// Ein Tipp weiter als die Absturzfrage in PSMobileApp - hier steht der
/// eigentliche ShareLink. SwiftUI kann ein Teilen-Blatt nicht aus einem
/// Alert-Knopf heraus direkt oeffnen, deshalb dieser kleine
/// Zwischenschritt statt eines eigenen UIActivityViewController-Wrappers.
struct AbsturzProtokollSheet: View {

    @Environment(\.psScale) private var ps
    @Environment(\.dismiss) private var dismiss
    @State private var datei: URL = LogExport.exportFile()
        ?? FileManager.default.temporaryDirectory.appendingPathComponent("psmobile-protokoll.txt")

    var body: some View {
        VStack(spacing: ps.pt(16)) {
            Text(st("Send log", "Protokoll senden"))
                .font(.system(size: ps.font(18), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st("Choose where to send it - for example Mail to yourself.",
                    "Wähle, wohin es gehen soll - zum Beispiel Mail an dich selbst."))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            ShareLink(item: datei) {
                Text(st("Share log", "Protokoll teilen"))
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: ps.touch(48))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            }
            .accessibilityIdentifier("absturz.teilen")
            Button(st("Close", "Schließen")) { dismiss() }
                .buttonStyle(.plain)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
        }
        .padding(ps.pt(24))
        .background(PrusaColors.background)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
