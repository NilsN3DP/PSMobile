import SwiftUI
import UIKit
import PSMShared

/// Gemeinsame Preview-Karte. Nur ihre Anordnung unterscheidet sich:
/// iPad rechts kompakt, iPhone unten als Sheet.
struct FinalPreviewOverlay: View {
    let snapshot: PsmCore.PreviewSnapshot
    @Binding var range: PreviewRange
    @Binding var view: PsmViewport.PreviewView
    @Binding var hiddenRoles: Set<PsmCore.PreviewFeatureRole>
    @Binding var hiddenExtruders: Set<Int32>
    let onEditor: () -> Void

    @Environment(\.psScale) private var ps
    private var isPad: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    var body: some View {
        ZStack(alignment: isPad ? .trailing : .bottom) {
            Color.clear
            Group {
                // Auf dem iPhone ist die feste Bogenhoehe zu knapp fuer
                // Kopf, Statistik, beide Schichtregler, Farbmodus und die
                // Rollen-Chips zusammen - der untere Regler fiel dadurch
                // aus dem sichtbaren Bereich. Ein Scrollcontainer haelt
                // ihn erreichbar, statt ihn abzuschneiden.
                if isPad {
                    panel
                } else {
                    ScrollView(showsIndicators: false) { panel }
                }
            }
            .frame(width: isPad
                   ? min(ps.pt(330), ps.windowSize.width * 0.36)
                   : nil)
            .frame(maxHeight: isPad
                   ? ps.windowSize.height * 0.72
                   : ps.pt(360))
            .padding(isPad ? ps.pt(14) : 0)
            PSMarke(name: "vorschau.panel")
            PSMarke(name: isPad
                    ? "vorschau.seitenkarte"
                    : "vorschau.bottomsheet")
        }
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: ps.pt(9)) {
            HStack {
                Text(st("Final G-code", "Finaler G-Code"))
                    .font(.system(size: ps.font(15), weight: .bold))
                Spacer()
                Button(action: onEditor) {
                    Label(st("Editor", "Editor"), systemImage: "cube")
                        .font(.system(size: ps.font(12), weight: .semibold))
                        .frame(minHeight: ps.touch(44))
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("vorschau.editor")
            }

            statistik
            layerBereich

            Picker("", selection: $view) {
                Text(st("Features", "Merkmale"))
                    .tag(PsmViewport.PreviewView.feature)
                Text(st("Extruders", "Extruder"))
                    .tag(PsmViewport.PreviewView.extruder)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .accessibilityIdentifier("vorschau.farbmodus")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: ps.pt(7)) {
                    if view == .feature {
                        ForEach(snapshot.roles) { item in
                            rollenKnopf(item)
                        }
                    } else {
                        ForEach(snapshot.extruders) { item in
                            extruderKnopf(item)
                        }
                    }
                }
            }
        }
        .padding(ps.pt(12))
        .background(PrusaColors.panel.opacity(0.97))
        .clipShape(RoundedRectangle(
            cornerRadius: isPad ? ps.pt(8) : ps.pt(14),
            style: .continuous))
        .overlay(
            RoundedRectangle(
                cornerRadius: isPad ? ps.pt(8) : ps.pt(14),
                style: .continuous)
                .stroke(PrusaColors.divider, lineWidth: 1))
        .shadow(color: .black.opacity(0.28), radius: 12, y: 4)
    }

    private var statistik: some View {
        let values = range.stats(in: snapshot.layers.map {
            PreviewLayerMetrics(
                zLower: $0.zLower,
                zUpper: $0.zUpper,
                timeSeconds: $0.timeSeconds,
                filamentMm: $0.filamentMm,
                filamentGrams: $0.filamentGrams)
        })
        return HStack(spacing: ps.pt(12)) {
            stat("clock", duration(values.timeSeconds))
            stat("scribble.variable",
                 String(format: "%.2f m", values.filamentMm / 1000))
            stat("scalemass",
                 String(format: "%.1f g", values.filamentGrams))
            Spacer(minLength: 0)
            Text(String(format: "%.2f–%.2f mm",
                        values.zLower, values.zUpper))
                .font(.system(size: ps.font(11), design: .monospaced))
                .foregroundStyle(PrusaColors.textMuted)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("vorschau.statistik")
    }

    private func stat(_ symbol: String, _ value: String) -> some View {
        Label(value, systemImage: symbol)
            .font(.system(size: ps.font(11), weight: .medium))
            .foregroundStyle(PrusaColors.textPrimary)
    }

    private var layerBereich: some View {
        VStack(spacing: ps.pt(2)) {
            HStack {
                Text(st("Layer range", "Schichtbereich"))
                    .font(.system(size: ps.font(11), weight: .semibold))
                Spacer()
                Text("\(range.lower + 1)–\(range.upper + 1)/\(range.layerCount)")
                    .font(.system(size: ps.font(11), design: .monospaced))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            Slider(
                value: Binding(
                    get: { Double(range.lower) },
                    set: { range.setLower(Int($0.rounded())) }),
                in: 0...Double(max(range.layerCount - 1, 1)),
                step: 1)
                .tint(PrusaColors.orange)
                .disabled(range.layerCount < 2)
                .accessibilityLabel(st("Lower layer", "Untere Schicht"))
                .accessibilityIdentifier("vorschau.layer.unten")
            Slider(
                value: Binding(
                    get: { Double(range.upper) },
                    set: { range.setUpper(Int($0.rounded())) }),
                in: 0...Double(max(range.layerCount - 1, 1)),
                step: 1)
                .tint(PrusaColors.orange)
                .disabled(range.layerCount < 2)
                .accessibilityLabel(st("Upper layer", "Obere Schicht"))
                .accessibilityIdentifier("vorschau.layer.oben")
        }
    }

    private func rollenKnopf(_ item: PsmCore.PreviewRole) -> some View {
        let hidden = hiddenRoles.contains(item.role)
        return Button {
            if hidden {
                hiddenRoles.remove(item.role)
            } else {
                hiddenRoles.insert(item.role)
            }
        } label: {
            HStack(spacing: ps.pt(5)) {
                Circle()
                    .fill(color(item.colorRGBA))
                    .frame(width: ps.pt(10), height: ps.pt(10))
                Text(roleName(item.role))
                    .font(.system(size: ps.font(11), weight: .medium))
            }
            .foregroundStyle(
                hidden ? PrusaColors.textMuted : PrusaColors.textPrimary)
            .padding(.horizontal, ps.pt(9))
            .frame(minHeight: ps.touch(44))
            .background(
                hidden
                    ? PrusaColors.background.opacity(0.65)
                    : PrusaColors.orange.opacity(0.18))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(
            "vorschau.rolle.\(item.role.rawValue)")
        .accessibilityValue(hidden ? st("Hidden", "Ausgeblendet")
                                   : st("Visible", "Sichtbar"))
    }

    private func extruderKnopf(
        _ item: PsmCore.PreviewExtruder
    ) -> some View {
        let hidden = hiddenExtruders.contains(item.extruder)
        return Button {
            if hidden {
                hiddenExtruders.remove(item.extruder)
            } else {
                hiddenExtruders.insert(item.extruder)
            }
        } label: {
            HStack(spacing: ps.pt(5)) {
                Circle()
                    .fill(color(item.colorRGBA))
                    .frame(width: ps.pt(10), height: ps.pt(10))
                Text("E\(item.extruder + 1)")
                    .font(.system(size: ps.font(11), weight: .semibold))
            }
            .foregroundStyle(
                hidden ? PrusaColors.textMuted : PrusaColors.textPrimary)
            .padding(.horizontal, ps.pt(10))
            .frame(minHeight: ps.touch(44))
            .background(
                hidden
                    ? PrusaColors.background.opacity(0.65)
                    : PrusaColors.orange.opacity(0.18))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(
            "vorschau.extruder.\(item.extruder)")
        .accessibilityValue(hidden ? st("Hidden", "Ausgeblendet")
                                   : st("Visible", "Sichtbar"))
    }

    private func color(_ rgba: UInt32) -> Color {
        guard rgba != 0 else { return .clear }
        return Color(
            red: Double((rgba >> 24) & 0xff) / 255,
            green: Double((rgba >> 16) & 0xff) / 255,
            blue: Double((rgba >> 8) & 0xff) / 255,
            opacity: Double(rgba & 0xff) / 255)
    }

    private func roleName(_ role: PsmCore.PreviewFeatureRole) -> String {
        switch role {
        case .perimeter: return st("Perimeter", "Kontur")
        case .externalPerimeter: return st("External", "Außenkontur")
        case .overhangPerimeter: return st("Overhang", "Überhang")
        case .internalInfill: return st("Infill", "Füllung")
        case .solidInfill: return st("Solid infill", "Volle Füllung")
        case .topSolidInfill: return st("Top surface", "Deckfläche")
        case .ironing: return st("Ironing", "Glätten")
        case .bridgeInfill: return st("Bridge", "Brücke")
        case .gapFill: return st("Gap fill", "Lückenfüllung")
        case .skirt: return st("Skirt/Brim", "Schürze/Rand")
        case .supportMaterial: return st("Support", "Stütze")
        case .supportMaterialInterface:
            return st("Support interface", "Stütz-Kontakt")
        case .wipeTower: return st("Wipe tower", "Reinigungsturm")
        case .custom: return st("Custom", "Benutzerdefiniert")
        case .none: return ""
        }
    }

    private func duration(_ seconds: Double) -> String {
        let total = max(Int(seconds.rounded()), 0)
        return String(format: "%d:%02d", total / 3600,
                      (total % 3600) / 60)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
