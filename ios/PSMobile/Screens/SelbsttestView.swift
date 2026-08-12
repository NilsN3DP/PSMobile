import SwiftUI
import PSMShared

/// Der Selbsttest als Bildschirm: ein Knopf, eine Liste, ein Bericht.
///
/// Gedacht für das echte Gerät. Was hier grün ist, ist auf diesem iPad
/// grün — nicht im Simulator, wo es keine Speichergrenze, keine
/// Wärmegrenze und eine andere GPU gibt.
///
/// Am Ende steht eine Markdown-Datei in „Dateien → PSMobile →
/// Selbsttest". Weitergeben geht direkt über das Teilen-Blatt.
struct SelbsttestView: View {

    @StateObject private var test = Selbsttest()
    var onClose: () -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            kopfzeile
            Divider().background(PrusaColors.divider)
            inhalt
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "selbsttest") }
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
            .accessibilityIdentifier("selbsttest.zurueck")

            Text(st("Self-test", "Selbsttest"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Spacer()
            if let url = test.berichtURL {
                ShareLink(item: url) {
                    Text(st("Share report", "Bericht teilen"))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(14))
                        .frame(height: ps.touch(44))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("selbsttest.teilen")
            }
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(56))
    }

    private var inhalt: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(10)) {
                Text(st("Runs everything that needs real hardware: loading, slicing, saving, reloading, painting, multi-material, and finally under load. The simulator answers none of these questions honestly.",
                        "Prüft alles, was echtes Gerät braucht: laden, slicen, sichern, wieder laden, bemalen, mehrfarbig, und zum Schluss unter Last. Der Simulator beantwortet keine dieser Fragen ehrlich."))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)

                startKnopf

                if test.laeuft, !test.aktuell.isEmpty {
                    HStack(spacing: ps.pt(8)) {
                        ProgressView()
                        Text(test.aktuell)
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(PrusaColors.textPrimary)
                    }
                }

                ForEach(test.schritte) { schritt in
                    zeile(schritt)
                }

                if !test.laeuft, !test.schritte.isEmpty {
                    abschluss
                }
            }
            .padding(ps.pt(16))
            .frame(maxWidth: ps.pt(760))
            .frame(maxWidth: .infinity)
        }
    }

    private var startKnopf: some View {
        Button {
            if test.laeuft { test.abbrechen() } else { test.starten() }
        } label: {
            Text(test.laeuft ? st("Cancel", "Abbrechen")
                             : st("Run all checks", "Alles prüfen"))
                .font(.system(size: ps.font(15), weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: ps.touch(52))
                .background(test.laeuft ? PrusaColors.panelRaised : PrusaColors.orange)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("selbsttest.start")
    }

    private func zeile(_ schritt: Selbsttest.Schritt) -> some View {
        HStack(alignment: .top, spacing: ps.pt(10)) {
            Text(schritt.ausgang.zeichen)
                .font(.system(size: ps.font(15), weight: .bold))
                .foregroundStyle(farbe(schritt.ausgang))
                .frame(width: ps.pt(18))
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                HStack {
                    Text(schritt.name)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                    Text(String(format: "%.1f s", schritt.sekunden))
                        .font(.system(size: ps.font(10)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                if !schritt.detail.isEmpty {
                    Text(schritt.detail)
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(schritt.ausgang == .fehler
                                         ? PrusaColors.danger : PrusaColors.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(.horizontal, ps.pt(10))
        .padding(.vertical, ps.pt(8))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
        .accessibilityIdentifier("selbsttest.schritt")
    }

    private var abschluss: some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(test.fehlerZahl == 0
                 ? st("All checks passed", "Alles bestanden")
                 : "\(test.fehlerZahl) " + st("failed", "fehlgeschlagen"))
                .font(.system(size: ps.font(15), weight: .semibold))
                .foregroundStyle(test.fehlerZahl == 0 ? PrusaColors.orange : PrusaColors.danger)
                .accessibilityIdentifier("selbsttest.ergebnis")
            if test.berichtURL != nil {
                Text(st("The report is in Files → PSMobile → Selbsttest.",
                        "Der Bericht liegt in Dateien → PSMobile → Selbsttest."))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
        }
        .padding(.top, ps.pt(6))
    }

    private func farbe(_ ausgang: Selbsttest.Ausgang) -> Color {
        switch ausgang {
        case .ok:      return PrusaColors.orange
        case .fehler:  return PrusaColors.danger
        case .warnung: return PrusaColors.textPrimary
        case .angabe:  return PrusaColors.textMuted
        }
    }
}
