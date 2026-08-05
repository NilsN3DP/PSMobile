import SwiftUI
import PSMShared

/// Mobile Bedienung der Prusa-TriangleSelector-Werkzeuge.
///
/// Die Oberfläche besitzt genau einen Optionswert. Derselbe Wert geht an
/// den Kern und an den Viewport; markierte Facetten werden hier nie
/// gespiegelt oder nachgerechnet.
struct PaintView: View {

    @ObservedObject var model: SlicerModel
    let objektId: Int32
    @Binding var options: PsmCore.PaintOptions

    @Environment(\.psScale) private var ps

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            Text(st("Paint", "Bemalen").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)

            HStack(spacing: ps.pt(6)) {
                wahl(st("Off", "Aus"), an: options.tool == nil,
                     kennung: "malen.aus") {
                    options.tool = nil
                }
                werkzeug(PsUiCatalog.tr("Supports"), .support,
                         kennung: "malen.stuetzen")
                werkzeug(PsUiCatalog.tr("Seam"), .seam,
                         kennung: "malen.naht")
                if model.extruderCount > 1 {
                    werkzeug("MMU", .mmu, kennung: "malen.mmu")
                }
            }

            if let aktiv = options.tool {
                zustandsWahl(aktiv)
                modusWahl

                if options.mode == .brush {
                    HStack(spacing: ps.pt(6)) {
                        wahl(st("Circle", "Kreis"),
                             an: options.shape == .circle,
                             kennung: "malen.form.kreis") {
                            options.shape = .circle
                        }
                        wahl(st("Sphere", "Kugel"),
                             an: options.shape == .sphere,
                             kennung: "malen.form.kugel") {
                            options.shape = .sphere
                        }
                    }
                    regler(
                        titel: st("Size", "Größe"),
                        wert: Binding(
                            get: { Double(options.radiusMm) },
                            set: { options.radiusMm = Float($0) }),
                        bereich: 1...20,
                        kennung: "malen.radius",
                        ausgabe: String(
                            format: "%.0f mm", options.radiusMm))
                } else {
                    regler(
                        titel: st("Angle", "Winkel"),
                        wert: Binding(
                            get: { Double(options.fillAngleDeg) },
                            set: { options.fillAngleDeg = Float($0) }),
                        bereich: 0...90,
                        kennung: "malen.winkel",
                        ausgabe: String(
                            format: "%.0f°", options.fillAngleDeg))
                }

                Text(st("Object (all copies)", "Objekt (alle Kopien)"))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .accessibilityIdentifier("malen.scope")

                HStack {
                    Text(st("Marked", "Markiert") +
                         ": \(model.paintCount(objektId, tool: aktiv))")
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .accessibilityIdentifier("malen.anzahl")
                    Spacer()
                    Button { model.clearPaint(objektId, tool: aktiv) } label: {
                        Text(st("Clear", "Löschen"))
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(PrusaColors.danger)
                            .frame(minHeight: ps.touch(40))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("malen.loeschen")
                }
            }
        }
    }

    private var modusWahl: some View {
        HStack(spacing: ps.pt(6)) {
            ForEach(options.supportedModes, id: \.rawValue) { mode in
                wahl(modusName(mode), an: options.mode == mode,
                     kennung: modusKennung(mode)) {
                    options.mode = mode
                }
            }
        }
    }

    private func werkzeug(_ label: String,
                           _ tool: PsmCore.PaintTool,
                           kennung: String) -> some View {
        wahl(label, an: options.tool == tool, kennung: kennung) {
            options.tool = tool
            options.state = 1
            options.normalizeForTool()
        }
    }

    @ViewBuilder private func zustandsWahl(
        _ aktiv: PsmCore.PaintTool
    ) -> some View {
        if aktiv == .mmu {
            HStack(spacing: ps.pt(6)) {
                wahl(st("Erase", "Radieren"),
                     an: options.state == 0,
                     kennung: "malen.zustand.0") {
                    options.state = 0
                }
                ForEach(1...model.extruderCount, id: \.self) { nummer in
                    wahl("\(nummer)",
                         an: options.state == Int32(nummer),
                         kennung: "malen.zustand.\(nummer)") {
                        options.state = Int32(nummer)
                    }
                }
            }
        } else {
            HStack(spacing: ps.pt(6)) {
                wahl(PsUiCatalog.tr("Enforce"),
                     an: options.state == 1,
                     kennung: "malen.zustand.1") {
                    options.state = 1
                }
                wahl(PsUiCatalog.tr("Block"),
                     an: options.state == 2,
                     kennung: "malen.zustand.2") {
                    options.state = 2
                }
                wahl(st("Erase", "Radieren"),
                     an: options.state == 0,
                     kennung: "malen.zustand.0") {
                    options.state = 0
                }
            }
        }
    }

    private func regler(titel: String,
                        wert: Binding<Double>,
                        bereich: ClosedRange<Double>,
                        kennung: String,
                        ausgabe: String) -> some View {
        HStack(spacing: ps.pt(8)) {
            Text(titel)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .frame(width: ps.pt(56), alignment: .leading)
            Slider(value: wert, in: bereich)
                .tint(PrusaColors.orange)
                .accessibilityIdentifier(kennung)
            Text(ausgabe)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textPrimary)
                .frame(width: ps.pt(52), alignment: .trailing)
        }
    }

    private func modusName(_ mode: PsmCore.PaintMode) -> String {
        switch mode {
        case .brush: return st("Brush", "Pinsel")
        case .smartFill: return "Smart Fill"
        case .bucketFill: return st("Bucket", "Eimer")
        }
    }

    private func modusKennung(_ mode: PsmCore.PaintMode) -> String {
        switch mode {
        case .brush: return "malen.modus.pinsel"
        case .smartFill: return "malen.modus.smart"
        case .bucketFill: return "malen.modus.eimer"
        }
    }

    private func wahl(_ label: String,
                      an: Bool,
                      kennung: String,
                      aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(
                    size: ps.font(12),
                    weight: an ? .semibold : .regular))
                .foregroundStyle(an ? .white : PrusaColors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(
                    an ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
        .accessibilityValue(an ? st("Selected", "Ausgewählt") : "")
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
