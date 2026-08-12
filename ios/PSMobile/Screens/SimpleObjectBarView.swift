import SwiftUI
import PSMShared

/// Die schwebende Leiste am ausgewaehlten Objekt - Gegenstueck zu
/// `SimpleObjectBar.kt`.
///
/// Sie erscheint, sobald ein Objekt angetippt ist, und verschwindet mit
/// der Auswahl. Schneiden, Teilen und Klonen waren im Simple Mode auf
/// iOS bisher gar nicht erreichbar.
///
/// Bewusst oben und nicht unten: unten liegt bereits das Modelle-Blatt,
/// und das ausgewaehlte Objekt selbst soll sichtbar bleiben.
struct SimpleObjectBarView: View {

    @ObservedObject var model: SlicerModel
    let objekt: PsmCore.ObjectInfo
    /// Im Simple Mode setzt dieser Knopf die Auswahl zurueck. Im Advanced
    /// uebernimmt die Seitenleiste diese Orientierung bereits selbst.
    let zeigtZurueck: Bool
    var onClearSelection: () -> Void
    /// Meldet, ob das Flaechenwerkzeug an ist - dann muss der Viewport
    /// die Beruehrung an die Flaeche geben statt an die Kamera.
    var onFlaechenwahl: (Bool) -> Void = { _ in }
    /// Nur der Advanced Mode uebergibt das - im Simple Mode sitzt die
    /// Griffwahl schon im Werkzeug-Blatt, eine zweite waere doppelt.
    /// War vorher eine eigene schwebende Leiste ohne Bezug zu dieser
    /// hier - stand deshalb an wechselnden Stellen im Bild, je nachdem
    /// wie breit das Fenster gerade war.
    var gizmo: Binding<PsmViewport.Gizmo>? = nil
    /// Obergrenze in Punkten, statt der festen ps.pt(640) - Advanced
    /// Mode braucht das auf schmalen Fenstern, wo die Leiste sonst
    /// per .fixedSize() ueber ihre eigentliche Wunschbreite hinaus in
    /// die Seitenleiste ragt: .fixedSize() macht sie immun gegen jede
    /// von aussen zugewiesene Breite (z. B. per .padding), begrenzen
    /// laesst sie sich nur von innen, ueber dieses .frame(maxWidth:).
    var maxBreite: CGFloat? = nil

    @Environment(\.psScale) private var ps
    @State private var zeigeSchnitt = false
    @State private var flaechenwahl = false
    @State private var schnittHoehe: Float = 0

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                if let gizmo {
                    griffKnopf("arrow.up.and.down.and.arrow.left.and.right",
                               st("Move", "Verschieben"), .move, gizmo)
                    griffKnopf("arrow.triangle.2.circlepath",
                               st("Rotate", "Drehen"), .rotate, gizmo)
                    griffKnopf("arrow.up.left.and.arrow.down.right",
                               st("Scale", "Skalieren"), .scale, gizmo)
                    griffKnopf("hand.point.up.left",
                               st("None", "Kein"), PsmViewport.Gizmo.none, gizmo)
                    Divider()
                        .frame(height: ps.pt(28))
                        .padding(.horizontal, ps.pt(2))
                }
                aktion("✂", st("Cut", "Schneiden"), kennung: "objekt.schneiden") {
                    schnittHoehe = objekt.sizeMm.z / 2
                    zeigeSchnitt = true
                }
                aktion("⧅", st("Split", "Teilen"), kennung: "objekt.teilen") {
                    model.split(objekt.id)
                }
                aktion("⧉", st("Clone", "Klonen"), kennung: "objekt.klonen") {
                    model.duplicate([objekt.id])
                }
                aktion("⭳", st("Drop", "Ablegen"), kennung: "objekt.ablegen") {
                    model.dropToBed(objekt.id)
                }
                // Ein Tippen, kein Nachdenken: die groesste ebene
                // Flaeche kommt nach unten.
                aktion("⬓", st("Lay flat", "Hinlegen"), kennung: "objekt.hinlegen") {
                    model.layFlat(objekt.id)
                }
                // Und fuer die Faelle, in denen die groesste Flaeche
                // nicht die gemeinte ist: eine antippen.
                aktion("◈", st("On face", "Auf Fläche"), kennung: "objekt.aufflaeche",
                       aktiv: flaechenwahl) {
                    flaechenwahl.toggle()
                    onFlaechenwahl(flaechenwahl)
                }
                aktion("⇔", st("Fit", "Einpassen"), kennung: "objekt.einpassen") {
                    model.fitToBed(objekt.id)
                }
                aktion("✖", st("Remove", "Entfernen"), kennung: "objekt.entfernen") {
                    model.removeObjects([objekt.id])
                    onClearSelection()
                }
                if zeigtZurueck {
                    aktion("←", st("Back", "Zurück"), kennung: "objekt.zurueck",
                           aktion: onClearSelection)
                }
            }
            .padding(.horizontal, ps.pt(4))
        }
        .frame(maxWidth: maxBreite ?? ps.pt(640))
        .background(PrusaColors.panel.opacity(0.95))
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(2))
                .stroke(PrusaColors.divider, lineWidth: 1)
        )
        .fixedSize(horizontal: false, vertical: true)
        .sheet(isPresented: $zeigeSchnitt) { schnittBlatt }
    }

    /// Schnitthoehe waehlen.
    ///
    /// Ein Zahlenfeld waere hier die schlechtere Wahl: man weiss vorher
    /// selten, bei welchem Millimeterwert man schneiden will, sondern
    /// ungefaehr wo. Der Regler zeigt den Wert zusaetzlich an.
    private var schnittBlatt: some View {
        VStack(alignment: .leading, spacing: ps.pt(16)) {
            Text(st("Cut", "Schneiden"))
                .font(.system(size: ps.font(18)))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(String(format: "%.1f mm", schnittHoehe))
                .font(.system(size: ps.font(14)))
                .foregroundStyle(PrusaColors.textMuted)
            Slider(value: Binding(get: { Double(schnittHoehe) },
                                  set: { schnittHoehe = Float($0) }),
                   in: 0...Double(max(objekt.sizeMm.z, 1)))
                .tint(PrusaColors.orange)
                .accessibilityIdentifier("objekt.schnitthoehe")
            HStack(spacing: ps.pt(12)) {
                Button(st("Cancel", "Abbrechen")) { zeigeSchnitt = false }
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Button {
                    // Beide Haelften behalten und als eigene Objekte,
                    // nicht als Teile eines gemeinsamen: im Simple Mode
                    // gibt es keinen Objektbaum, in dem man Teile
                    // wiederfaende.
                    model.cut(objekt.id, zMm: schnittHoehe)
                    zeigeSchnitt = false
                    onClearSelection()
                } label: {
                    Text(st("Cut", "Schneiden"))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(20))
                        .frame(height: ps.touch(48))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("objekt.schnitt.ausfuehren")
            }
            Spacer()
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
    }

    /// Dieselbe Kennung wie zuvor die schwebende Leiste - vorhandene
    /// Tests bleiben gueltig.
    private func griffKnopf(_ symbol: String,
                            _ label: String,
                            _ wert: PsmViewport.Gizmo,
                            _ binding: Binding<PsmViewport.Gizmo>) -> some View {
        let aktiv = binding.wrappedValue == wert
        return Button {
            binding.wrappedValue = wert
        } label: {
            VStack(spacing: 0) {
                Image(systemName: symbol).font(.system(size: ps.font(15)))
                Text(label).font(.system(size: ps.font(8))).lineLimit(1)
            }
            .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
            .frame(width: ps.pt(58), height: ps.touch(46))
            .background(aktiv ? PrusaColors.orange : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("advanced.gizmo." + kennungFuer(wert))
    }

    private func kennungFuer(_ wert: PsmViewport.Gizmo) -> String {
        switch wert {
        case .move:   return "move"
        case .rotate: return "rotate"
        case .scale:  return "scale"
        default:      return "none"
        }
    }

    private func aktion(_ glyph: String,
                        _ label: String,
                        kennung: String,
                        aktiv: Bool = false,
                        aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: 0) {
                Text(glyph).font(.system(size: ps.font(15)))
                Text(label).font(.system(size: ps.font(8))).lineLimit(1)
            }
            .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
            .frame(width: ps.pt(58), height: ps.touch(46))
            .background(aktiv ? PrusaColors.orange : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }
}
