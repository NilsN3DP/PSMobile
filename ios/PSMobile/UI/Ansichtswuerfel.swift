import UIKit
import SwiftUI
import simd

/// Der Ansichtswuerfel - Gegenstueck zu `ui/Ansichtswuerfel.kt`.
///
/// Ein kleiner Wuerfel unten links im Viewport, der sich mit der Szene
/// dreht: seine Flaechen heissen Oben, Vorn, Hinten, Links, Rechts, Unten.
/// Eine Flaeche antippen setzt die feste Blickrichtung, Ziehen dreht die
/// Kamera wie ein Zug im Viewport. Er ersetzt seit dem 14.09.2026 die fuenf
/// Richtungsknoepfe der unteren Leiste (Nils: "wie am PC mit so nem
/// Wuerfel, nicht mit festen Feldern").
///
/// Ein UIView neben dem GL-View statt SwiftUI: er lebt im selben Takt wie
/// die Gizmo-Marker (PSMGLView.zeichne) und braucht keinen Umweg ueber
/// einen Zustand, der jedes Bild neu durch SwiftUI liefe.
///
/// Die Projektion rechnet mit derselben Kamera wie psm_viewport.cpp:
/// Auge bei (cosP*sinY, -cosP*cosY, sinP), Oben ist +Z.
final class AnsichtswuerfelView: UIView {

    var onAnsicht: ((PsmViewport.ViewPreset) -> Void)?
    var onOrbit: ((Float, Float) -> Void)?

    private var yaw: Float = -0.6
    private var pitch: Float = 0.55
    private var letzterPunkt: CGPoint = .zero
    private var gezogen = false

    private struct Flaeche {
        let normale: SIMD3<Float>
        let label: String
        let ansicht: PsmViewport.ViewPreset
        let kennung: String
        let ecken: [SIMD3<Float>]
    }

    private static func flaechen() -> [Flaeche] {
        func e(_ x: Float, _ y: Float, _ z: Float) -> SIMD3<Float> { SIMD3(x, y, z) }
        return [
            Flaeche(normale: e(0, 0, 1), label: st("Top", "Oben"), ansicht: .top, kennung: "wuerfel.oben",
                    ecken: [e(-1, -1, 1), e(1, -1, 1), e(1, 1, 1), e(-1, 1, 1)]),
            Flaeche(normale: e(0, 0, -1), label: st("Bottom", "Unten"), ansicht: .bottom, kennung: "wuerfel.unten",
                    ecken: [e(-1, 1, -1), e(1, 1, -1), e(1, -1, -1), e(-1, -1, -1)]),
            Flaeche(normale: e(0, -1, 0), label: st("Front", "Vorn"), ansicht: .front, kennung: "wuerfel.vorn",
                    ecken: [e(-1, -1, -1), e(1, -1, -1), e(1, -1, 1), e(-1, -1, 1)]),
            Flaeche(normale: e(0, 1, 0), label: st("Back", "Hinten"), ansicht: .back, kennung: "wuerfel.hinten",
                    ecken: [e(1, 1, -1), e(-1, 1, -1), e(-1, 1, 1), e(1, 1, 1)]),
            Flaeche(normale: e(-1, 0, 0), label: st("Left", "Links"), ansicht: .left, kennung: "wuerfel.links",
                    ecken: [e(-1, 1, -1), e(-1, -1, -1), e(-1, -1, 1), e(-1, 1, 1)]),
            Flaeche(normale: e(1, 0, 0), label: st("Right", "Rechts"), ansicht: .right, kennung: "wuerfel.rechts",
                    ecken: [e(1, -1, -1), e(1, 1, -1), e(1, 1, 1), e(1, -1, 1)]),
        ]
    }

    private let alle = AnsichtswuerfelView.flaechen()
    /// Sichtbare Flaechen des letzten Bildes mit Bildpunkten und Tiefe -
    /// fuer Zeichnung, Treffersuche und Bedienungshilfen gleichermassen.
    private var sichtbar: [(Flaeche, [CGPoint], Float)] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isAccessibilityElement = false
        accessibilityIdentifier = "wuerfel"
        accessibilityLabel = st("View cube", "Ansichtswürfel")
    }

    required init?(coder: NSCoder) { fatalError("nicht aus einem Storyboard") }

    /// Vom Zeichentakt gerufen; zeichnet nur bei Aenderung neu.
    func setzeWinkel(yaw neuYaw: Float, pitch neuPitch: Float) {
        guard abs(neuYaw - yaw) > 1e-4 || abs(neuPitch - pitch) > 1e-4 else { return }
        yaw = neuYaw
        pitch = neuPitch
        setNeedsDisplay()
    }

    // MARK: - Projektion

    private func basis() -> (zurKamera: SIMD3<Float>, rechts: SIMD3<Float>, oben: SIMD3<Float>) {
        let cp = cos(pitch)
        let zurKamera = simd_normalize(SIMD3<Float>(cp * sin(yaw), -cp * cos(yaw), sin(pitch)))
        let vor = -zurKamera
        let rechts = simd_normalize(simd_cross(vor, SIMD3<Float>(0, 0, 1)))
        let oben = simd_normalize(simd_cross(rechts, vor))
        return (zurKamera, rechts, oben)
    }

    private func berechneSichtbar() {
        let b = basis()
        let mitte = CGPoint(x: bounds.midX, y: bounds.midY)
        let radius = Float(min(bounds.width, bounds.height)) * 0.27
        sichtbar = alle.compactMap { f in
            let tiefe = simd_dot(f.normale, b.zurKamera)
            guard tiefe > 0.02 else { return nil }
            let ecken = f.ecken.map { p -> CGPoint in
                CGPoint(x: mitte.x + CGFloat(simd_dot(p, b.rechts) * radius),
                        y: mitte.y - CGFloat(simd_dot(p, b.oben) * radius))
            }
            return (f, ecken, tiefe)
        }.sorted { $0.2 < $1.2 }
        aktualisiereBedienungshilfen()
    }

    override func draw(_ rect: CGRect) {
        berechneSichtbar()
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        for (f, ecken, tiefe) in sichtbar {
            let pfad = UIBezierPath()
            pfad.move(to: ecken[0])
            for e in ecken.dropFirst() { pfad.addLine(to: e) }
            pfad.close()
            // Heller, je frontaler die Flaeche zur Kamera steht.
            let h = CGFloat(0.28 + 0.30 * tiefe)
            ctx.setFillColor(UIColor(red: h, green: h, blue: h + 0.02, alpha: 0.92).cgColor)
            ctx.addPath(pfad.cgPath)
            ctx.fillPath()
            ctx.setStrokeColor(UIColor(PrusaColors.orange).withAlphaComponent(0.9).cgColor)
            ctx.setLineWidth(1.5)
            ctx.addPath(pfad.cgPath)
            ctx.strokePath()
            guard tiefe > 0.35 else { continue }
            let schwer = ecken.reduce(CGPoint.zero) { CGPoint(x: $0.x + $1.x, y: $0.y + $1.y) }
            let mitte = CGPoint(x: schwer.x / CGFloat(ecken.count), y: schwer.y / CGFloat(ecken.count))
            let attr: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: tiefe > 0.6 ? 9 : 7),
                .foregroundColor: UIColor(PrusaColors.textPrimary),
            ]
            let text = NSAttributedString(string: f.label, attributes: attr)
            let groesse = text.size()
            text.draw(at: CGPoint(x: mitte.x - groesse.width / 2, y: mitte.y - groesse.height / 2))
        }
    }

    // MARK: - Beruehrung

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        letzterPunkt = t.location(in: self)
        gezogen = false
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        let dx = Float(p.x - letzterPunkt.x), dy = Float(p.y - letzterPunkt.y)
        if abs(dx) + abs(dy) > 2 { gezogen = true }
        letzterPunkt = p
        if gezogen { onOrbit?(dx * Float(contentScaleFactor), dy * Float(contentScaleFactor)) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !gezogen, let t = touches.first else { return }
        let p = t.location(in: self)
        if let treffer = sichtbar.last(where: { trifft($0.1, p) }) {
            onAnsicht?(treffer.0.ansicht)
        }
    }

    /// Punkt-in-Viereck (Halbebenentest).
    private func trifft(_ punkte: [CGPoint], _ p: CGPoint) -> Bool {
        var vorzeichen = 0
        for i in punkte.indices {
            let a = punkte[i], b = punkte[(i + 1) % punkte.count]
            let k = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            let s = k > 0 ? 1 : (k < 0 ? -1 : 0)
            if s == 0 { continue }
            if vorzeichen == 0 { vorzeichen = s } else if s != vorzeichen { return false }
        }
        return vorzeichen != 0
    }

    // MARK: - Bedienungshilfen / Tests

    /// Je sichtbarer Flaeche ein Accessibility-Element an ihrem Schwerpunkt
    /// mit derselben Kennung wie drueben (wuerfel.oben, ...). XCUITest tippt
    /// dessen Rahmen an und landet damit in touchesEnded.
    private func aktualisiereBedienungshilfen() {
        accessibilityElements = sichtbar.map { f, ecken, _ in
            let schwer = ecken.reduce(CGPoint.zero) { CGPoint(x: $0.x + $1.x, y: $0.y + $1.y) }
            let mitte = CGPoint(x: schwer.x / CGFloat(ecken.count), y: schwer.y / CGFloat(ecken.count))
            let element = UIAccessibilityElement(accessibilityContainer: self)
            element.accessibilityFrameInContainerSpace = CGRect(x: mitte.x - 9, y: mitte.y - 9, width: 18, height: 18)
            element.accessibilityIdentifier = f.kennung
            element.accessibilityLabel = f.label
            element.accessibilityTraits = .button
            return element
        }
    }
}
