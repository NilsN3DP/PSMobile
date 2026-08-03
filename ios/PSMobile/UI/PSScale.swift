import SwiftUI
import PSMShared

/// Wie stark die Oberflaeche insgesamt verkleinert wird.
///
/// Die Zahlen kommen aus dem gemeinsamen Modul (E-13) - es sind
/// buchstaeblich dieselben wie auf Android, nicht nachgehaltene Kopien. Die Masse in den Bildschirmen sind
/// fuer ein Tablet mit rund 1000 x 720 Punkt geschrieben. Faellt das
/// Fenster kleiner aus - iPhone, Slide Over, Split View, Stage Manager -,
/// dann sind dieselben Masse im Verhaeltnis zu gross.
///
/// Auf Android wird dafuer die wirksame Dichte gestaucht. SwiftUI hat
/// kein Gegenstueck dazu, das global wirkt, deshalb rechnen die
/// Bildschirme ihre Masse hier durch: `ps.pt(16)` statt `16`. Das ist
/// mehr Schreibarbeit, aber es ist dieselbe Regel - und sie ist an einer
/// Stelle nachzulesen statt in fuenfzehn Ansichten verstreut.
///
/// Wer eine Zahl direkt hinschreibt, bekommt sie auf dem iPhone zu gross
/// zurueck. Genau davor schuetzt diese Datei.
struct PSScale: Equatable {

    /// Die Groesse, fuer die die Masse gedacht sind.
    static let referenceWidth = CGFloat(WindowScale.shared.REFERENCE_WIDTH)
    static let referenceHeight = CGFloat(WindowScale.shared.REFERENCE_HEIGHT)

    /// Untergrenze. Darunter waeren Zielflaechen physisch zu klein zum
    /// Treffen. Wo es enger wird, muss der Bildschirm selbst Inhalt
    /// weglassen, statt weiter zu schrumpfen.
    static let minScale = CGFloat(WindowScale.shared.MIN_SCALE)

    /// Wie stark die Schrift dem Kastenmass folgt. Text darf nicht so
    /// stark schrumpfen wie Kaesten, sonst wird er unleserlich, bevor der
    /// Platz wirklich knapp ist.
    static let fontFollow = CGFloat(WindowScale.shared.FONT_FOLLOW)

    let factor: CGFloat

    init(width: CGFloat, height: CGFloat) {
        self.factor = PSScale.scaleFor(width: width, height: height)
    }

    /// Die knappere Kante entscheidet. Hochskaliert wird nie: ab der
    /// Referenzgroesse stimmen die Masse bereits.
    static func scaleFor(width: CGFloat, height: CGFloat) -> CGFloat {
        CGFloat(WindowScale.shared.forWindow(width: Float(width), height: Float(height)))
    }

    /// Gedaempfte Fassung fuer Schriftgroessen.
    static func fontScaleFor(_ scale: CGFloat) -> CGFloat {
        CGFloat(WindowScale.shared.fontScale(scale: Float(scale)))
    }

    /// Ein Kastenmass: Abstand, Breite, Hoehe, Eckenradius.
    func pt(_ value: CGFloat) -> CGFloat { value * factor }

    /// Eine Schriftgroesse - schrumpft schwaecher als die Kaesten.
    func font(_ value: CGFloat) -> CGFloat {
        value * PSScale.fontScaleFor(factor)
    }

    /// Zielflaeche fuer Finger und Stift. Bewusst nicht kleiner als
    /// Apples Untergrenze von 44 Punkt, egal was die Skalierung sagt.
    func touch(_ value: CGFloat = 44) -> CGFloat { max(pt(value), 44) }
}

private struct PSScaleKey: EnvironmentKey {
    /// Falls jemand die Umgebung vergisst: die Referenzgroesse, also
    /// unveraendert. Lieber zu gross auf einem grossen Geraet als
    /// willkuerlich verkleinert.
    static let defaultValue = PSScale(width: PSScale.referenceWidth,
                                      height: PSScale.referenceHeight)
}

extension EnvironmentValues {
    var psScale: PSScale {
        get { self[PSScaleKey.self] }
        set { self[PSScaleKey.self] = newValue }
    }
}

/// Legt die Skalierung aus der tatsaechlichen Fenstergroesse fest.
///
/// Bewusst die Fenstergroesse und nicht die Bildschirmgroesse: Auf dem
/// iPad kann die App in Slide Over oder Stage Manager in einem schmalen
/// Fenster stehen, waehrend der Bildschirm gross bleibt. Wer den
/// Bildschirm misst, baut dort eine Oberflaeche, die nicht hineinpasst.
struct PSScaleRoot<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        GeometryReader { geo in
            content()
                .environment(\.psScale,
                             PSScale(width: geo.size.width, height: geo.size.height))
        }
    }
}
