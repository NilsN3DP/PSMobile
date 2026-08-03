import SwiftUI
import PSMShared

/// Der Einstieg - Gegenstueck zu `WorkflowStartScreen.kt`.
///
/// Beide Modi arbeiten auf denselben Druckern und Profilen; die Frage
/// ist nur, wie viel Oberflaeche man sehen will. Wer sich festgelegt
/// hat, stellt den Startmodus in den App-Einstellungen fest ein und
/// sieht diesen Schirm nicht mehr.
struct WorkflowStartView: View {

    var onSimple: () -> Void
    var onAdvanced: () -> Void
    var onAppSettings: () -> Void

    @Environment(\.psScale) private var ps
    private var eng: Bool { ps.factor <= 0.8 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(eng ? 8 : 14)) {
                kopf
                modusKarte
                textKnopf(st("App settings", "App-Einstellungen"),
                          kennung: "start.appeinstellungen",
                          aktion: onAppSettings)
            }
            .frame(maxWidth: ps.pt(760))
            .frame(maxWidth: .infinity)
            .padding(.horizontal, ps.pt(16))
            .padding(.vertical, ps.pt(eng ? 12 : 28))
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "start") }
    }

    private var kopf: some View {
        HStack(spacing: ps.pt(10)) {
            Text("S")
                .font(.system(size: ps.font(16), weight: .bold))
                .foregroundStyle(PrusaColors.background)
                .frame(width: ps.pt(eng ? 26 : 32), height: ps.pt(eng ? 26 : 32))
                .background(PrusaColors.orange)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
            VStack(alignment: .leading, spacing: 0) {
                Text("PSMobile")
                    .font(.system(size: ps.font(16)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text("3D PRINT WORKSPACE")
                    .font(.system(size: ps.font(10)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            Spacer()
        }
    }

    private var modusKarte: some View {
        VStack(alignment: .leading, spacing: ps.pt(eng ? 8 : 12)) {
            Text(st("CHOOSE MODE", "MODUS WÄHLEN"))
                .font(.system(size: ps.font(13), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st("Both modes use your configured printers and profiles.",
                    "Beide Modi verwenden deine eingerichteten Drucker und Profile."))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)

            modusZeile(nummer: "01",
                       titel: "Simple Mode",
                       detail: st("Get to print quickly with a few clear decisions.",
                                  "Schnell zum Druck mit wenigen, klaren Entscheidungen."),
                       aktion: st("Start", "Starten"),
                       kennung: "start.simple",
                       handlung: onSimple)
            modusZeile(nummer: "02",
                       titel: "Advanced Mode",
                       detail: st("Every setting PrusaSlicer knows, on all pages.",
                                  "Alle Einstellungen, die PrusaSlicer kennt, auf allen Seiten."),
                       aktion: st("Start", "Starten"),
                       kennung: "start.advanced",
                       handlung: onAdvanced)
        }
        .padding(ps.pt(eng ? 12 : 18))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PrusaColors.panel)
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(4))
                .stroke(PrusaColors.divider, lineWidth: 1)
        )
    }

    private func modusZeile(nummer: String,
                            titel: String,
                            detail: String,
                            aktion: String,
                            kennung: String,
                            handlung: @escaping () -> Void) -> some View {
        Button(action: handlung) {
            HStack(spacing: ps.pt(12)) {
                Text(nummer)
                    .font(.system(size: ps.font(15), weight: .bold))
                    .foregroundStyle(PrusaColors.orange)
                VStack(alignment: .leading, spacing: ps.pt(2)) {
                    Text(titel)
                        .font(.system(size: ps.font(15)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(detail)
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Text(aktion + "  ›")
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.orange)
            }
            .padding(ps.pt(12))
            .frame(maxWidth: .infinity, minHeight: ps.touch(64), alignment: .leading)
            .background(PrusaColors.panelRaised)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(3))
                    .stroke(PrusaColors.divider, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func textKnopf(_ label: String,
                           kennung: String,
                           aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
