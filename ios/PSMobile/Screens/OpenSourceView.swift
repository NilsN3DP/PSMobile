import SwiftUI
import PSMShared

/// "Open Source & Lizenzen": woraus Slicer Mobile besteht und wo der
/// Quellcode liegt. Gegenstueck zu `OpenSourceView.kt`.
///
/// PrusaSlicer steht unter AGPL-3.0 - ein abgeleitetes Werk muss seinen
/// Quellcode zugaenglich machen und die verwendeten Bibliotheken nennen.
/// Die Liste selbst kommt aus `OpenSource` im gemeinsamen Modul.
struct OpenSourceView: View {

    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @Environment(\.openURL) private var openURL

    private var deutsch: Bool { Lang.shared.current == "de" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            kopfzeile
            Divider().background(PrusaColors.divider)
            ScrollView {
                VStack(alignment: .leading, spacing: ps.pt(10)) {
                    Text(st(
                        "Slicer Mobile is an unofficial app built on PrusaSlicer. It is not affiliated with or endorsed by Prusa Research. " +
                            "Like PrusaSlicer it is released under the \(OpenSource.shared.LIZENZ) license – the complete source code is public.",
                        "Slicer Mobile ist eine inoffizielle App auf Basis von PrusaSlicer. Sie ist nicht mit Prusa Research verbunden und nicht von Prusa Research freigegeben. " +
                            "Wie PrusaSlicer steht sie unter der Lizenz \(OpenSource.shared.LIZENZ) – der vollständige Quellcode ist öffentlich."))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)

                    Button {
                        if let url = URL(string: OpenSource.shared.REPO) { openURL(url) }
                    } label: {
                        Text(st("Source code on GitHub", "Quellcode auf GitHub"))
                            .font(.system(size: ps.font(14), weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: ps.touch(48))
                            .background(PrusaColors.orange)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("opensource.github")
                    Text(OpenSource.shared.REPO)
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)

                    Text(st("Third-party components", "Verwendete Komponenten").uppercased())
                        .font(.system(size: ps.font(11), weight: .semibold))
                        .foregroundStyle(PrusaColors.textMuted)
                        .padding(.top, ps.pt(12))
                    let komponenten = OpenSource.shared.fuer(plattform: OpenSource.Plattform.ios)
                    ForEach(Array(komponenten.enumerated()), id: \.offset) { index, k in
                        Button {
                            if let url = URL(string: k.url) { openURL(url) }
                        } label: {
                            VStack(alignment: .leading, spacing: ps.pt(2)) {
                                HStack {
                                    Text(k.name)
                                        .font(.system(size: ps.font(14)))
                                        .foregroundStyle(PrusaColors.textPrimary)
                                    Spacer()
                                    Text(k.lizenz)
                                        .font(.system(size: ps.font(11)))
                                        .foregroundStyle(PrusaColors.orange)
                                }
                                Text(deutsch ? k.rolleDe : k.rolleEn)
                                    .font(.system(size: ps.font(11)))
                                    .foregroundStyle(PrusaColors.textMuted)
                                Text(k.url)
                                    .font(.system(size: ps.font(10)))
                                    .foregroundStyle(PrusaColors.textMuted)
                            }
                            .padding(.horizontal, ps.pt(14))
                            .padding(.vertical, ps.pt(10))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(PrusaColors.panelRaised)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("opensource.komponente.\(index)")
                    }
                    Spacer().frame(height: ps.pt(8))
                }
                .padding(ps.pt(16))
            }
            .accessibilityIdentifier("opensource.liste")
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "opensource") }
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
            .accessibilityIdentifier("opensource.zurueck")

            Text(st("Open source & licenses", "Open Source & Lizenzen"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Spacer()
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(56))
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
