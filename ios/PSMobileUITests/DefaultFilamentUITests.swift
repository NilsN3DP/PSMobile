import XCTest

/// Wirft nach: welches Filament steht nach der echten Ersteinrichtung
/// (nicht dem Testschalter -psm-preset-printer, der komplettSetup und
/// damit standardwerteSetzen ueberspringt) fuer einen frisch
/// eingerichteten MK4S 0.4 im Feld.
final class DefaultFilamentUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-reset-setup", "-psm-start-advanced"]
        app.launch()
    }

    func testStandardfilamentNachEchterEinrichtung() {
        let marke = app.buttons["hersteller.PrusaResearch"]
        XCTAssertTrue(marke.waitForExistence(timeout: 30))

        let feld = app.textFields.firstMatch
        feld.tap()
        feld.typeText("MK4S")

        let variante = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'variante.'")).firstMatch
        XCTAssertTrue(variante.waitForExistence(timeout: 10),
                      "Kein Treffer fuer MK4S")
        variante.tap()

        let fertig = app.buttons["fertig"]
        XCTAssertTrue(fertig.isEnabled)
        fertig.tap()

        let arbeitsbereich = app.otherElements["arbeitsbereich"]
        XCTAssertTrue(arbeitsbereich.waitForExistence(timeout: 90))

        let bild = XCUIScreen.main.screenshot()
        let att = XCTAttachment(screenshot: bild)
        att.name = "nach-echter-einrichtung"
        att.lifetime = .keepAlways
        add(att)
    }
}
