import SwiftUI

/// Der Projekt-Hinweis: was beim Oeffnen anders kam (Profile), was beim
/// Sichern schiefging, was ein Werkzeug ablehnte. Gegenstueck zu
/// `ProjektHinweis.kt`.
///
/// Bis zum 16.09.2026 zeigte ihn nur der Simple Mode; im Advanced lief
/// `projectNotice` ins Leere - ein Projekt mit fremdem Drucker sagte dort
/// nichts, ein gescheitertes Sichern auch nicht.
struct ProjektHinweis: View {
    let text: String
    @ObservedObject var model: SlicerModel
    /// Platz ueber dem unteren Rand - im Simple Mode fuer Schrittleiste und
    /// Modelle-Blatt, im Advanced fuer die Ansichtsleiste.
    let abstandUnten: CGFloat

    @Environment(\.psScale) private var ps

    var body: some View {
        VStack {
            Spacer()
            HStack(alignment: .top, spacing: ps.pt(10)) {
                Text(text)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button { model.projectNotice = nil } label: {
                    Text("✕")
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.touch(44), height: ps.touch(44))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("projekt.hinweis.schliessen")
            }
            .padding(.horizontal, ps.pt(14))
            .padding(.vertical, ps.pt(8))
            .frame(maxWidth: ps.pt(520))
            .background(PrusaColors.panelRaised)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(3))
                    .stroke(PrusaColors.orange, lineWidth: 1)
            )
            Spacer().frame(height: abstandUnten)
        }
        .padding(.horizontal, ps.pt(12))
        .accessibilityIdentifier("projekt.hinweis")
    }
}
