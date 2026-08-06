import XCTest

/// Nur fuer Untersuchungen in dieser Nachtsitzung - kein dauerhafter
/// Vertragstest, kein Anspruch auf Vollstaendigkeit.
final class KegelUntersuchungTests: XCTestCase {

    func testArrangePanelAnsehen() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        app.buttons["bed.add"].tap()
        sleep(1)
        app.buttons["schiene.arrange"].press(forDuration: 0.6)
        sleep(2)
        halte(app, "arrange-panel")
    }


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

        let oben = app.sliders["vorschau.layer.oben"]
        if oben.waitForExistence(timeout: 5) {
            oben.adjust(toNormalizedSliderPosition: 1.0)
            sleep(2)
            halte(app, "4-vorschau-volles-modell")
        }

        if app.buttons["Top"].waitForExistence(timeout: 3) {
            app.buttons["Top"].tap()
            sleep(1)
            halte(app, "5-vorschau-von-oben")
        }
    }

    func testSpuleAnsehen() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
        sleep(1)

        let material = app.buttons["simple.werkzeug.Material"]
        XCTAssertTrue(material.waitForExistence(timeout: 10), "Material-Knopf fehlt")
        material.tap()
        sleep(2)
        halte(app, "spule")
    }

    private func halte(_ app: XCUIApplication, _ name: String) {
        let daten = XCUIScreen.main.screenshot().pngRepresentation
        try? daten.write(to: URL(fileURLWithPath: "/tmp/kegel/\(name).png"))
    }
}
