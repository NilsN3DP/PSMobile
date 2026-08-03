import XCTest

/// Nimmt Bildschirmfotos der wichtigsten Ansichten auf.
///
/// Kein Test im eigentlichen Sinn - er prueft nichts. Er ist dazu da,
/// den Stand ansehen zu koennen, ohne ein Geraet in der Hand zu haben,
/// und wird nach Gebrauch wieder entfernt.
final class ShotUITests: XCTestCase {

    private func bild(_ app: XCUIApplication, _ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: "/tmp/shot-" + name + ".png"))
    }

    func testAlleAnsichten() {
        let app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple",
                               "-psm-load-cube", "-psm-reset-printers"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
        sleep(3)
        bild(app, "01-simple")

        app.buttons["simple.werkzeug.Material"].tap()
        sleep(1)
        bild(app, "02-material")

        app.buttons["simple.werkzeug.Settings"].tap()
        sleep(1)
        bild(app, "03-einstellen")

        app.buttons["simple.karte.PRINT_SETTINGS"].tap()
        sleep(1)
        bild(app, "04-druckeinstellungen")

        app.buttons["simple.zurueck"].tap()
        app.buttons["simple.karte.SUPPORTS"].tap()
        sleep(1)
        bild(app, "05-stuetzen")

        // Objektleiste am gewaehlten Modell
        app.buttons["simple.zurueck"].tap()
        app.buttons["simple.zurueck"].tap()
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).firstMatch.tap()
        sleep(1)
        bild(app, "06-objektleiste")

        // Schneiden bis zum Ergebnis
        app.buttons["simple.werkzeug.G-Code"].tap()
        _ = app.buttons["slice.schliessen"].waitForExistence(timeout: 180)
        sleep(1)
        bild(app, "07-zusammenfassung")
        app.buttons["slice.schliessen"].tap()

        // Vorschau
        if app.buttons["werkzeug.vorschau"].waitForExistence(timeout: 10) {
            app.buttons["werkzeug.vorschau"].tap()
            sleep(4)
            bild(app, "08-vorschau")
            app.buttons["werkzeug.vorschau"].tap()
        }

        // Advanced
        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.advanced"].tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 20))
        sleep(2)
        bild(app, "09-advanced")

        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        sleep(1)
        bild(app, "10-objektbaum")

        app.buttons["advanced.printerSettings"].tap()
        _ = app.buttons["seite.0"].waitForExistence(timeout: 20)
        sleep(1)
        bild(app, "11-einstellungen")
        app.buttons["seite.sonderwerte"].tap()
        sleep(1)
        bild(app, "12-sonderwerte")
        app.buttons["einstellungen.zurueck"].tap()

        app.buttons["drucker.oeffnen"].tap()
        sleep(2)
        bild(app, "13-drucker")
    }
}
