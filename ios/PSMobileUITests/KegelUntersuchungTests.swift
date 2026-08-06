import XCTest

/// Nur fuer die Untersuchung der G-Code-Vorschau in dieser Nachtsitzung -
/// kein dauerhafter Vertragstest, kein Anspruch auf Vollstaendigkeit.
final class KegelUntersuchungTests: XCTestCase {

    func testKegelVorschau() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-kegel"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        let slicen = app.buttons["slicen"]
        XCTAssertTrue(slicen.waitForExistence(timeout: 10))
        slicen.tap()

        let schliessen = app.buttons["slice.schliessen"]
        XCTAssertTrue(schliessen.waitForExistence(timeout: 60), "Schnitt kam nicht durch")
        halte(app, "2a-fertig-vor-schliessen")
        schliessen.tap()
        sleep(1)

        let vorschauKnopf = app.buttons["werkzeug.vorschau"]
        XCTAssertTrue(vorschauKnopf.waitForExistence(timeout: 10))
        vorschauKnopf.tap()
        sleep(3)
        halte(app, "3-vorschau-oben")

        // Schichtregler ganz nach unten ziehen, um zu sehen, ob dann das
        // volle Ergebnis (alle Schichten) kommt.
        let oben = app.sliders["vorschau.layer.oben"]
        if oben.waitForExistence(timeout: 5) {
            oben.adjust(toNormalizedSliderPosition: 1.0)
            sleep(2)
            halte(app, "4-vorschau-volles-modell")
        }

        // Von oben drauf schauen, um die Schichten von der Seite/oben zu
        // sehen statt schraeg.
        if app.buttons["Top"].waitForExistence(timeout: 3) {
            app.buttons["Top"].tap()
            sleep(1)
            halte(app, "5-vorschau-von-oben")
        }
    }

    private func halte(_ app: XCUIApplication, _ name: String) {
        let daten = XCUIScreen.main.screenshot().pngRepresentation
        try? daten.write(to: URL(fileURLWithPath: "/tmp/kegel/\(name).png"))
    }
}
