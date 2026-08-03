import SwiftUI
import PSMShared

/// Bemalen: Stützen erzwingen oder sperren, Naht setzen, MMU-Farben.
///
/// Der Kern kann das seit langem - Facette treffen, Pinsel mit Radius,
/// zählen, löschen. Auf iOS war nichts davon erreichbar: der Viewport
/// lieferte Treffer, aber niemand hörte zu.
///
/// Am Desktop hängt das an einer Gizmo-Leiste mit Mausrad für den
/// Radius. Auf einem Tablet gibt es kein Mausrad, also steht der Radius
/// als Regler da - und der Finger ist ohnehin gröber als ein Zeiger.
struct PaintView: View {

    @ObservedObject var model: SlicerModel
    let objektId: Int32
    @Binding var werkzeug: PsmCore.PaintTool?
    @Binding var zustand: Int32
    @Binding var radius: Float

    @Environment(\.psScale) private var ps

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            Text(st("Paint", "Bemalen").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)

            // Welches Werkzeug. Aus ist ein eigener Zustand, kein
            // Nebeneffekt: solange gemalt wird, dreht ein Wischen die
            // Kamera nicht mehr, und das muss man abstellen koennen.
            HStack(spacing: ps.pt(6)) {
                wahl(st("Off", "Aus"), an: werkzeug == nil, kennung: "malen.aus") {
                    werkzeug = nil
                }
                wahl(PsUiCatalog.tr("Supports"), an: werkzeug == .support,
                     kennung: "malen.stuetzen") {
                    werkzeug = .support
                    zustand = 1
                }
                wahl(PsUiCatalog.tr("Seam"), an: werkzeug == .seam, kennung: "malen.naht") {
                    werkzeug = .seam
                    zustand = 1
                }
                if model.extruderCount > 1 {
                    wahl("MMU", an: werkzeug == .mmu, kennung: "malen.mmu") {
                        werkzeug = .mmu
                        zustand = 1
                    }
                }
            }

            if let aktiv = werkzeug {
                zustandsWahl(aktiv)

                HStack(spacing: ps.pt(8)) {
                    Text(st("Brush", "Pinsel"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.pt(56), alignment: .leading)
                    Slider(value: Binding(get: { Double(radius) },
                                          set: { radius = Float($0) }),
                           in: 1...20)
                        .tint(PrusaColors.orange)
                        .accessibilityIdentifier("malen.radius")
                    Text(String(format: "%.0f mm", radius))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .frame(width: ps.pt(52), alignment: .trailing)
                }

                // Die Zahl der markierten Facetten. Ohne sie waere nicht
                // zu sehen, ob ein Strich etwas bewirkt hat - auf einer
                // dunklen Flaeche sind ein paar gefaerbte Dreiecke leicht
                // zu uebersehen.
                HStack {
                    Text(st("Marked", "Markiert") + ": \(model.paintCount(objektId, tool: aktiv))")
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

    /// Erzwingen, sperren oder radieren - bei MMU stattdessen die
    /// Extrudernummer.
    @ViewBuilder private func zustandsWahl(_ aktiv: PsmCore.PaintTool) -> some View {
        if aktiv == .mmu {
            HStack(spacing: ps.pt(6)) {
                wahl(st("Erase", "Radieren"), an: zustand == 0, kennung: "malen.zustand.0") {
                    zustand = 0
                }
                ForEach(1...model.extruderCount, id: \.self) { nummer in
                    wahl("\(nummer)", an: zustand == Int32(nummer),
                         kennung: "malen.zustand.\(nummer)") {
                        zustand = Int32(nummer)
                    }
                }
            }
        } else {
            HStack(spacing: ps.pt(6)) {
                wahl(PsUiCatalog.tr("Enforce"), an: zustand == 1, kennung: "malen.zustand.1") {
                    zustand = 1
                }
                wahl(PsUiCatalog.tr("Block"), an: zustand == 2, kennung: "malen.zustand.2") {
                    zustand = 2
                }
                wahl(st("Erase", "Radieren"), an: zustand == 0, kennung: "malen.zustand.0") {
                    zustand = 0
                }
            }
        }
    }

    private func wahl(_ label: String,
                      an: Bool,
                      kennung: String,
                      aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(12), weight: an ? .semibold : .regular))
                .foregroundStyle(an ? .white : PrusaColors.textPrimary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(an ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
