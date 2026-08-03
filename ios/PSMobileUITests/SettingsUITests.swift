import XCTest

/// Prueft den Einstellungsrenderer.
///
/// Er ist der Bildschirm mit dem groessten Hebel - 20 Seiten und 247
/// Parameter aus einer Vorlage. Und damit auch der, bei dem ein Fehler
/// am weitesten reicht: was hier nicht stimmt, stimmt auf jeder Seite
/// nicht.
final class SettingsUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced"]
        app.launch()

        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60),
                      "Der Arbeitsbereich ist nicht erschienen")
        app.buttons["einstellungen.oeffnen"].tap()
    }

    func testSeitenAusDerVorlageErscheinen() {
        // Die erste Seite von "print" heisst im Original "Layers and
        // perimeters" - sie kommt aus Tab.cpp, nicht aus einer Liste in
        // unserem Code.
        let erste = app.buttons["seite.0"]
        XCTAssertTrue(erste.waitForExistence(timeout: 10),
                      "Keine Seitenliste - tabs.json wurde nicht gelesen")

        // Zehn Seiten hat der Druckbereich laut Vorlage. Weniger hiesse,
        // dass der Renderer etwas verschluckt.
        XCTAssertTrue(app.buttons["seite.9"].exists,
                      "Es fehlen Seiten im Druckbereich")
    }

    func testDerReiterwechselWechseltDieSeiten() {
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 10))

        // Der Druckerbereich hat andere Seiten als der Druckbereich - und
        // die Extruderseite wird zur Laufzeit vervielfacht.
        app.buttons["reiter.printer"].tap()
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 5))

        app.buttons["reiter.filament"].tap()
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 5))
    }

    func testEinWertLaesstSichAendernUndBleibt() {
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 10))

        // Das erste Textfeld der ersten Seite ist die Schichthoehe.
        let feld = app.textFields.firstMatch
        XCTAssertTrue(feld.waitForExistence(timeout: 5),
                      "Auf der ersten Seite steht kein Eingabefeld")

        let vorher = feld.value as? String ?? ""
        XCTAssertFalse(vorher.isEmpty,
                       "Das Feld ist leer - der Wert kam nicht aus dem Kern")

        feld.tap()
        feld.press(forDuration: 1.0)
        if app.menuItems["Select All"].waitForExistence(timeout: 2) {
            app.menuItems["Select All"].tap()
        }
        feld.typeText("0.15\n")

        // Zur Seite und zurueck: der Wert muss aus dem Kern kommen, nicht
        // aus dem Bildschirmzustand.
        app.buttons["reiter.printer"].tap()
        app.buttons["reiter.print"].tap()

        let wieder = app.textFields.firstMatch
        XCTAssertTrue(wieder.waitForExistence(timeout: 5))
        XCTAssertEqual(wieder.value as? String, "0.15",
                       "Der geaenderte Wert hat den Seitenwechsel nicht ueberlebt")
    }

    func testSchnelleinstellungenImSimpleMode() {
        // Die drei Bereiche der Referenz kamen bisher nur als
        // Ueberschriften vor. Wer die Fuelldichte aendern wollte, musste
        // in den Advanced Mode.
        let simple = XCUIApplication()
        simple.launchArguments = ["-psm-preset-printer", "-psm-start-simple"]
        simple.launch()
        XCTAssertTrue(simple.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        simple.buttons["simple.werkzeug.Settings"].tap()
        simple.buttons["simple.karte.PRINT_SETTINGS"].tap()

        for schluessel in ["schnell.layer_height", "schnell.fill_density",
                           "schnell.fill_pattern", "schnell.perimeters"] {
            XCTAssertTrue(simple.descendants(matching: .any)[schluessel]
                            .waitForExistence(timeout: 10),
                          "Im Druckprofil-Panel fehlt " + schluessel)
        }
    }

    func testZurueckFuehrtInDenArbeitsbereich() {
        XCTAssertTrue(app.buttons["seite.0"].waitForExistence(timeout: 10))
        app.buttons["einstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 10),
                      "Zurueck fuehrt nicht in den Arbeitsbereich")
    }
}
