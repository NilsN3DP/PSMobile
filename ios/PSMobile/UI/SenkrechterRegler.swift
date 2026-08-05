import SwiftUI

/// Ein senkrechter Regler, von Hand gebaut.
///
/// SwiftUIs `Slider` laesst sich drehen, aber dann stimmen seine
/// Trefferflaechen nicht mehr mit dem ueberein, was man sieht: weder von
/// Hand noch im Test liess er sich bewegen. Hier ist es andersherum -
/// eine Spur, ein Griff, ein Ziehen, und der Finger trifft, was er
/// sieht.
///
/// Oben ist der groesste Wert. Bei Schichten ist das die einzige
/// Richtung, die niemand erklaeren muss.
struct SenkrechterRegler: View {

    @Binding var wert: Double
    let maximum: Double
    let beschriftung: String

    @Environment(\.psScale) private var ps

    var body: some View {
        VStack(spacing: ps.pt(6)) {
            Text(beschriftung)
                .font(.system(size: ps.font(10)))
                .foregroundStyle(PrusaColors.textPrimary)
                .lineLimit(1)

            GeometryReader { geo in
                let hoehe = geo.size.height
                let anteil = maximum > 0 ? wert / maximum : 0
                ZStack(alignment: .bottom) {
                    // Spur
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .fill(PrusaColors.panelRaised)
                        .frame(width: ps.pt(6))
                        .frame(maxWidth: .infinity)
                    // Gefuellter Teil - so sieht man den Stand auch ohne
                    // auf die Zahl zu schauen.
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .fill(PrusaColors.orange)
                        .frame(width: ps.pt(6), height: hoehe * anteil)
                        .frame(maxWidth: .infinity)
                    // Griff
                    Circle()
                        .fill(PrusaColors.orange)
                        .frame(width: ps.pt(22), height: ps.pt(22))
                        .overlay(Circle().stroke(.white.opacity(0.9), lineWidth: 2))
                        .offset(y: -(hoehe - ps.pt(22)) * anteil)
                        .frame(maxWidth: .infinity)
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in
                            // Oben ist gross: der Bildschirm zaehlt nach
                            // unten, die Schichten nach oben.
                            let a = 1 - min(max(g.location.y / hoehe, 0), 1)
                            wert = (a * maximum).rounded()
                        }
                )
            }
        }
        // Fuer VoiceOver und fuer den Test ist ein selbstgezeichneter
        // Regler sonst ein Bild ohne Wert. Der Stellvertreter macht ihn
        // wieder zu dem, was er ist.
        .accessibilityRepresentation {
            Slider(value: $wert, in: 0...Swift.max(maximum, 0.001))
        }
    }
}
