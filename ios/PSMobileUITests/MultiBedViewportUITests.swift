import XCTest

/// Werfeinweg-Test fuer die manuelle Sichtpruefung der raeumlichen
/// Mehrbett-Darstellung. Nimmt Screenshots vor/nach dem Umschalten -
/// die eigentliche Pruefung ist visuell, nicht automatisiert.
final class MultiBedViewportUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    func testMehrbettAnsichtSichtpruefung() {
        app.sauberStarten([ "-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube" ])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        XCTAssertTrue(app.otherElements["bed.selector"].waitForExistence(timeout: 15))

        let anhaengen = XCTAttachment.self

        func schuss(_ name: String) {
            let bild = XCUIScreen.main.screenshot()
            let att = anhaengen.init(screenshot: bild)
            att.name = name
            att.lifetime = .keepAlways
            add(att)
        }

        // Zweites Bett anlegen, damit ueberhaupt etwas zu versetzen ist.
        let hinzufuegen = app.bett("bed.add")
        if hinzufuegen.waitForExistence(timeout: 5) {
            hinzufuegen.tap()
        } else if app.buttons["bed.selector.active"].waitForExistence(timeout: 5) {
            app.buttons["bed.selector.active"].tap()
            XCTAssertTrue(app.bett("bed.add").waitForExistence(timeout: 5))
            app.bett("bed.add").tap()
            if app.buttons["bed.selector.close"].exists {
                app.buttons["bed.selector.close"].tap()
            }
        }

        schuss("01-vor-einstellung-einzelbett")

        // Zur App-Einstellung, Schalter suchen und an.
        XCTAssertTrue(app.buttons["appeinstellungen.oeffnen"].waitForExistence(timeout: 5))
        app.buttons["appeinstellungen.oeffnen"].tap()

        let schalter = app.switches["appeinstellungen.viewport.multi-bed-render"]
        var versuche = 0
        while !schalter.exists && versuche < 10 {
            app.swipeUp()
            versuche += 1
        }
        XCTAssertTrue(schalter.waitForExistence(timeout: 5), "Der Mehrbett-Schalter fehlt in den Einstellungen")
        schalter.tap()

        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 10))

        // Kurz warten, bis der Viewport neu aufgebaut hat.
        Thread.sleep(forTimeInterval: 1.5)
        schuss("02a-nach-einstellung-mehrbett-an-vor-reset")

        // Diagnose: erzwingt einen Kamera-Refit unabhaengig vom
        // automatischen camera_unset-Pfad, um einzugrenzen, ob das
        // Problem dort oder in der Geometrie selbst liegt.
        if app.buttons["ansicht.3D"].waitForExistence(timeout: 5) {
            app.buttons["ansicht.3D"].tap()
            Thread.sleep(forTimeInterval: 1.0)
        }
        schuss("02-nach-einstellung-mehrbett-an")

        // Wieder aus - die alte Einzelbett-Darstellung muss zurueckkommen.
        app.buttons["appeinstellungen.oeffnen"].tap()
        XCTAssertTrue(schalter.waitForExistence(timeout: 5))
        schalter.tap()
        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 10))
        Thread.sleep(forTimeInterval: 1.5)
        schuss("03-nach-einstellung-mehrbett-aus")
    }
}
