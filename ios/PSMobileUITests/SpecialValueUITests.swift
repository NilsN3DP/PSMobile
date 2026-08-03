import XCTest

/// Prueft die beiden Sonderbearbeiter.
///
/// Beide Werte sind im allgemeinen Renderer eine Zeile Text. Was hier
/// geprueft wird, ist genau der Unterschied: dass der eigene Bearbeiter
/// aufgeht, den vorhandenen Wert liest und ihn richtig zurueckschreibt.
final class SpecialValueUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        app.buttons["advanced.printerSettings"].tap()
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 15))
    }

    func testDieBettformOeffnetSichAlsBreiteUndTiefe() {
        // Die Bettform steht auf der ersten Druckerseite.
        let knopf = app.buttons["sonderwert.bed_shape"]
        XCTAssertTrue(knopf.waitForExistence(timeout: 10),
                      "Fuer die Bettform gibt es keinen eigenen Bearbeiter")
        knopf.tap()

        let breite = app.textFields["bett.breite"]
        XCTAssertTrue(breite.waitForExistence(timeout: 5), "Der Bearbeiter fehlt")

        // Der MK4S hat ein 250er Bett - der Wert kommt aus dem Profil,
        // nicht aus einer Voreinstellung hier.
        XCTAssertEqual(breite.value as? String, "250")
        XCTAssertEqual(app.textFields["bett.tiefe"].value as? String, "210")

        // Und die Flaeche wird ausgerechnet, nicht abgetippt.
        XCTAssertTrue(app.staticTexts["bett.flaeche"].exists,
                      "Die Flaeche fehlt")
    }

    func testEineGeaenderteBettformKommtAn() {
        app.buttons["sonderwert.bed_shape"].tap()
        let tiefe = app.textFields["bett.tiefe"]
        XCTAssertTrue(tiefe.waitForExistence(timeout: 5))

        tiefe.tap()
        tiefe.press(forDuration: 1.0)
        if app.menuItems["Select All"].waitForExistence(timeout: 2) {
            app.menuItems["Select All"].tap()
        }
        tiefe.typeText("180")
        app.buttons["bett.uebernehmen"].tap()

        // Zurueck im Bearbeiter muss der neue Wert stehen - er kommt dann
        // aus dem Kern, nicht aus dem Textfeld von vorhin.
        XCTAssertTrue(app.buttons["sonderwert.bed_shape"].waitForExistence(timeout: 10))
        app.buttons["sonderwert.bed_shape"].tap()
        let wieder = app.textFields["bett.tiefe"]
        XCTAssertTrue(wieder.waitForExistence(timeout: 5))
        XCTAssertEqual(wieder.value as? String, "180",
                       "Die geaenderte Bettform hat den Weg durch den Kern nicht ueberlebt")
    }
}
