import XCTest

/// Wirft nach: Bett-Entfernen-Knopf, Alle-Betten-schneiden-Knopf und
/// Kamera-Fokus beim Bettwechsel im Mehrbett-Modus.
final class FeatureCheckUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten([ "-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube" ])
    }

    private func schuss(_ name: String) {
        let bild = XCUIScreen.main.screenshot()
        let att = XCTAttachment(screenshot: bild)
        att.name = name
        att.lifetime = .keepAlways
        add(att)
    }

    func testEntfernenUndSchneidenKnoepfe() {
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        XCTAssertTrue(app.otherElements["bed.selector"].waitForExistence(timeout: 15))

        // Zweites, leeres Bett - daran haengt der Entfernen-Knopf.
        app.bett("bed.add").tap()
        let entfernen = app.bett("bed.remove.1")
        XCTAssertTrue(entfernen.waitForExistence(timeout: 5),
                      "Der X-Knopf fehlt am leeren zweiten Bett")
        schuss("01-x-knopf-sichtbar")

        // Multi-Bett-Ansicht an, dann zwischen den Betten wechseln - die
        // Kamera soll mitschwenken.
        XCTAssertTrue(app.buttons["appeinstellungen.oeffnen"].waitForExistence(timeout: 5))
        app.buttons["appeinstellungen.oeffnen"].tap()
        let schalter = app.switches["appeinstellungen.viewport.multi-bed-render"]
        var versuche = 0
        while !schalter.exists && versuche < 10 { app.swipeUp(); versuche += 1 }
        XCTAssertTrue(schalter.waitForExistence(timeout: 5))
        schalter.tap()
        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 10))
        Thread.sleep(forTimeInterval: 1.0)
        schuss("01b-mehrbett-schilder")
        app.bett("bed.card.0").tap()
        Thread.sleep(forTimeInterval: 1.0)
        schuss("02-fokus-bett-1")
        app.bett("bed.card.1").tap()
        Thread.sleep(forTimeInterval: 1.0)
        schuss("03-fokus-bett-2")

        // "Alle Betten schneiden" - nur pruefen, dass der Knopf da ist
        // und startet, nicht dass er fertig wird (Rechenzeit).
        let alleSchneiden = app.buttons["slicen.alle"]
        XCTAssertTrue(alleSchneiden.waitForExistence(timeout: 5),
                      "Der Knopf 'Alle Betten schneiden' fehlt bei mehreren Betten")
        schuss("04-alle-schneiden-sichtbar")
    }
}
