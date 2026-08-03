import XCTest
// Die Datei wird mitkompiliert, siehe project.yml - kein Import noetig.

/// Gegenstueck zu `UiScaleForTest` auf Android - dieselben Faelle,
/// dieselben Erwartungen. Weicht eine der beiden Seiten ab, faellt es
/// hier auf und nicht erst auf einem Geraet.
final class PSScaleTests: XCTestCase {

    func testGrossesTabletBleibtUnveraendert() {
        // Ab der Referenzgroesse wird nicht mehr hochskaliert - die Masse
        // sind fuer diesen Fall geschrieben.
        XCTAssertEqual(PSScale.scaleFor(width: 1000, height: 720), 1, accuracy: 0.001)
        XCTAssertEqual(PSScale.scaleFor(width: 1280, height: 800), 1, accuracy: 0.001)
    }

    func testDieKnappereKanteEntscheidet() {
        // 900x576: Breite 0.90, Hoehe 0.80 - die Hoehe gewinnt.
        XCTAssertEqual(PSScale.scaleFor(width: 900, height: 576), 0.8, accuracy: 0.001)
        // Und andersherum: 750x720 -> Breite 0.75 gewinnt.
        XCTAssertEqual(PSScale.scaleFor(width: 750, height: 720), 0.75, accuracy: 0.001)
    }

    func testUntergrenzeGreift() {
        // Rechnerisch waeren das 0.556 - die Untergrenze faengt es ab,
        // damit Zielflaechen treffbar bleiben.
        XCTAssertEqual(PSScale.scaleFor(width: 600, height: 400),
                       PSScale.minScale, accuracy: 0.001)
        XCTAssertEqual(PSScale.scaleFor(width: 200, height: 200),
                       PSScale.minScale, accuracy: 0.001)
        XCTAssertEqual(PSScale.scaleFor(width: 1, height: 1),
                       PSScale.minScale, accuracy: 0.001)
    }

    func testSchriftSchrumpftSchwaecherAlsKaesten() {
        let scale = PSScale.scaleFor(width: 600, height: 400)
        let fontScale = PSScale.fontScaleFor(scale)
        XCTAssertGreaterThan(fontScale, scale,
                             "Schrift darf nicht staerker schrumpfen als Kaesten")
        XCTAssertLessThan(fontScale, 1, "Schrift muss aber mitgehen")
    }

    /// Die Geraete, auf denen die App laufen soll - vom kleinsten iPhone
    /// bis zum groessten iPad, quer und hoch. Kein Fall darf unter die
    /// Untergrenze fallen oder ueber 1 hinausgehen.
    func testAlleGeraeteliegenImRahmen() {
        let groessen: [(String, CGFloat, CGFloat)] = [
            ("iPhone SE hoch",        375, 667),
            ("iPhone SE quer",        667, 375),
            ("iPhone 15 hoch",        393, 852),
            ("iPhone 15 Pro Max quer",932, 430),
            ("iPad mini hoch",        744, 1133),
            ("iPad 11 quer",          1210, 834),
            ("iPad Pro 13 quer",      1376, 1032),
            ("Slide Over schmal",     320, 1024),
        ]
        for (name, w, h) in groessen {
            let s = PSScale.scaleFor(width: w, height: h)
            XCTAssertGreaterThanOrEqual(s, PSScale.minScale, "\(name) faellt unter die Untergrenze")
            XCTAssertLessThanOrEqual(s, 1, "\(name) wird hochskaliert")
        }
    }

    func testZielflaecheBleibtTreffbar() {
        // Auch im engsten Fenster darf eine Zielflaeche nicht unter
        // Apples Untergrenze von 44 Punkt rutschen.
        let eng = PSScale(width: 320, height: 480)
        XCTAssertGreaterThanOrEqual(eng.touch(), 44)
    }
}
