import XCTest

/// INDX/MMU hat mehr als nur einen Druckerwechsel: Materialpositionen,
/// Mischrezept und Modellzuweisung muessen denselben Kernzustand sehen.
final class ExtruderAndColorMixUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = [
            "-psm-reset-setup",
            "-psm-preset-printer",
            "-psm-test-eight-extruders",
            "-psm-test-colormix-colors",
            "-psm-start-simple",
        ]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testEightMaterialPositionsAreOneBased() {
        let material = app.buttons["simple.werkzeug.Material"]
        XCTAssertTrue(material.waitForExistence(timeout: 10))
        material.tap()

        for index in 0..<8 {
            XCTAssertTrue(app.buttons["extruder.\(index)"].waitForExistence(timeout: 5),
                          "Materialposition \(index + 1) fehlt")
            XCTAssertTrue(app.staticTexts["\(index + 1)"].exists,
                          "Position muss einsbasiert beschriftet sein")
        }
        XCTAssertFalse(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Kopf'")).firstMatch.exists)
    }

    func testColorMixPreviewsAndSavesWithoutChangingFilament() {
        app.buttons["simple.werkzeug.Material"].tap()
        let materialBefore = app.staticTexts["extruder.material.0"].label

        let colorMix = app.buttons["simple.colormix"]
        XCTAssertTrue(colorMix.waitForExistence(timeout: 5), "ColorMix-Einstieg fehlt")
        colorMix.tap()

        let preview = app.otherElements["colormix.preview"]
        XCTAssertTrue(preview.waitForExistence(timeout: 5), "Mischvorschau fehlt")
        XCTAssertEqual(preview.value as? String, "#800080")

        app.buttons["colormix.save"].tap()
        XCTAssertTrue(app.buttons["colormix.done"].waitForExistence(timeout: 5),
                      "Das gespeicherte Rezept bleibt nicht sichtbar")
        app.buttons["colormix.done"].tap()

        XCTAssertTrue(app.staticTexts["extruder.material.0"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["extruder.material.0"].label, materialBefore,
                       "ColorMix darf das Filament einer physischen Position nicht ersetzen")
    }
}
