import SwiftUI

/// Ein Regler mit zwei Griffen fuer einen Bereich - waagerecht oder
/// senkrecht, je nach Anlegestelle am Viewport-Rand.
///
/// SwiftUI hat kein eingebautes Gegenstueck (ein einzelner `Slider`
/// kennt nur einen Wert). Der Desktop hat aus demselben Grund einen
/// eigenen ImGui-Regler gebaut (`DoubleSlider::ImGuiControl`) - hier
/// dieselbe Idee, nur mit SwiftUI-Gesten statt ImGui.
struct DualHandleSlider: View {

    enum Achse { case waagerecht, senkrecht }

    @Binding var untererWert: Int32
    @Binding var obererWert: Int32
    let bereich: ClosedRange<Int32>
    let achse: Achse
    /// Feuert waehrend des Ziehens, nicht erst beim Loslassen - der
    /// Viewport soll live mitlaufen, wie am Desktop auch.
    var onChange: () -> Void = {}

    @State private var ziehGriff: Griff?

    private enum Griff { case unten, oben }

    private let griffDurchmesser: CGFloat = 22
    private let trackDicke: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            let laenge = achse == .waagerecht ? geo.size.width : geo.size.height
            let nutzbar = max(laenge - griffDurchmesser, 1)
            let spanne = max(Double(bereich.upperBound - bereich.lowerBound), 1)

            let unterePosition = position(fuer: untererWert, spanne: spanne, nutzbar: nutzbar)
            let oberePosition = position(fuer: obererWert, spanne: spanne, nutzbar: nutzbar)

            ZStack {
                // Track: der volle Bereich schwach, die Auswahl kraeftig.
                trackForm(geo: geo)
                    .fill(PrusaColors.panel.opacity(0.9))
                auswahlForm(geo: geo, von: unterePosition, bis: oberePosition)
                    .fill(PrusaColors.orange)

                griffAnsicht(aktiv: ziehGriff == .unten)
                    .position(punkt(fuer: unterePosition, geo: geo))
                    .gesture(ziehGeste(.unten, geo: geo, nutzbar: nutzbar, spanne: spanne))
                    .accessibilityIdentifier(achse == .senkrecht
                                             ? "vorschau.schicht.unten"
                                             : "vorschau.move.unten")

                griffAnsicht(aktiv: ziehGriff == .oben)
                    .position(punkt(fuer: oberePosition, geo: geo))
                    .gesture(ziehGeste(.oben, geo: geo, nutzbar: nutzbar, spanne: spanne))
                    .accessibilityIdentifier(achse == .senkrecht
                                             ? "vorschau.schicht.oben"
                                             : "vorschau.move.oben")
            }
        }
        .frame(width: achse == .waagerecht ? nil : griffDurchmesser,
               height: achse == .waagerecht ? griffDurchmesser : nil)
    }

    private func trackForm(geo: GeometryProxy) -> Path {
        let halb = griffDurchmesser / 2
        let rect = achse == .waagerecht
            ? CGRect(x: halb, y: (geo.size.height - trackDicke) / 2,
                     width: max(geo.size.width - griffDurchmesser, 0), height: trackDicke)
            : CGRect(x: (geo.size.width - trackDicke) / 2, y: halb,
                     width: trackDicke, height: max(geo.size.height - griffDurchmesser, 0))
        return RoundedRectangle(cornerRadius: trackDicke / 2).path(in: rect)
    }

    private func auswahlForm(geo: GeometryProxy, von: CGFloat, bis: CGFloat) -> Path {
        let halb = griffDurchmesser / 2
        if achse == .waagerecht {
            let rect = CGRect(x: halb + von, y: (geo.size.height - trackDicke) / 2,
                              width: max(bis - von, 0), height: trackDicke)
            return RoundedRectangle(cornerRadius: trackDicke / 2).path(in: rect)
        }
        // Dieselbe Spiegelung wie in punkt(fuer:geo:) - sonst zeigt der
        // kraeftige Auswahlbalken den falschen Abschnitt des Tracks an.
        let nutzbar = max(geo.size.height - griffDurchmesser, 1)
        let oben = nutzbar - bis
        let unten = nutzbar - von
        let rect = CGRect(x: (geo.size.width - trackDicke) / 2, y: halb + oben,
                          width: trackDicke, height: max(unten - oben, 0))
        return RoundedRectangle(cornerRadius: trackDicke / 2).path(in: rect)
    }

    private func griffAnsicht(aktiv: Bool) -> some View {
        Circle()
            .fill(PrusaColors.orange)
            .overlay(Circle().stroke(PrusaColors.background, lineWidth: 2))
            .frame(width: griffDurchmesser, height: griffDurchmesser)
            .scaleEffect(aktiv ? 1.25 : 1)
            .shadow(radius: aktiv ? 3 : 0)
            .animation(.interactiveSpring(response: 0.2), value: aktiv)
            // Die sichtbare Kugel bleibt klein - die Trefferflaeche
            // drumherum erreicht Apples 44pt-Mindestmass fuer
            // Touch-Ziele. Ohne das brauchte es mehrere Anlaeufe, bis
            // ein Finger den Griff ueberhaupt traf.
            .frame(width: 44, height: 44)
            .contentShape(Circle())
    }

    private func punkt(fuer position: CGFloat, geo: GeometryProxy) -> CGPoint {
        let halb = griffDurchmesser / 2
        if achse == .waagerecht {
            return CGPoint(x: halb + position, y: geo.size.height / 2)
        }
        // Senkrecht: Wert 0 (unterste Schicht) gehoert an den UNTEREN
        // Bildrand, wie am Bett - die Bildschirm-Y-Achse laeuft aber
        // von oben nach unten, deshalb hier gespiegelt.
        let nutzbar = max(geo.size.height - griffDurchmesser, 1)
        return CGPoint(x: geo.size.width / 2, y: halb + (nutzbar - position))
    }

    private func position(fuer wert: Int32, spanne: Double, nutzbar: CGFloat) -> CGFloat {
        let anteil = Double(wert - bereich.lowerBound) / spanne
        return CGFloat(anteil.clamped(to: 0...1)) * nutzbar
    }

    private func ziehGeste(_ griff: Griff, geo: GeometryProxy,
                           nutzbar: CGFloat, spanne: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { wert in
                ziehGriff = griff
                let roh = achse == .waagerecht
                    ? wert.location.x - griffDurchmesser / 2
                    // Gespiegelt wie punkt(fuer:geo:): ein Finger nahe
                    // am oberen Rand muss den hohen Wert (oberste
                    // Schicht) treffen, nicht den niedrigen.
                    : (nutzbar - (wert.location.y - griffDurchmesser / 2))
                let anteil = Double((roh / max(nutzbar, 1)).clamped(to: 0...1))
                let neu = bereich.lowerBound +
                    Int32((anteil * spanne).rounded())
                switch griff {
                case .unten:
                    untererWert = min(max(neu, bereich.lowerBound), obererWert)
                case .oben:
                    obererWert = max(min(neu, bereich.upperBound), untererWert)
                }
                onChange()
            }
            .onEnded { _ in ziehGriff = nil }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
