import XCTest

/// Die beiden Verwaltungsansichten bleiben ueber dem Arbeitsbereich,
/// damit ein iPad nicht in eine Vollbild-Navigation wechselt.
final class FloatingDialogUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        addTeardownBlock { XCUIDevice.shared.orientation = .portrait }
    }

    private func starte(_ argumente: [String]) {
        app.sauberStarten(argumente)
    }

    private func pruefeDialog(_ dialog: XCUIElement, _ name: String) {
        XCTAssertTrue(dialog.waitForExistence(timeout: 60), "\(name) fehlt")
        let fenster = app.windows.firstMatch.frame
        XCTAssertTrue(fenster.contains(dialog.frame),
                      "\(name) ragt aus dem Fenster: \(dialog.frame) in \(fenster)")
        XCTAssertLessThan(dialog.frame.width, fenster.width,
                          "\(name) darf nicht den ganzen Bildschirm bedecken")
    }

    /// Dieser Fall wird auf dem iPad-Destination-Lauf ausgefuehrt.
    func testAufRegulaererBreiteBleibenAppEinstellungenDialogUeberDemSimpleMode() {
        starte(["-psm-preset-printer", "-psm-start-simple"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.appeinstellungen"].tap()

        pruefeDialog(app.otherElements["dialog.appeinstellungen"], "App-Einstellungen-Dialog")
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].exists,
                      "Der Simple Mode darf hinter dem Dialog nicht verschwinden")

        app.otherElements["dialog.appeinstellungen"].swipeUp()
        XCTAssertTrue(app.buttons["appeinstellungen.selbsttest"].waitForExistence(timeout: 10),
                      "Die App-Einstellungen lassen sich nicht bis zur Diagnose scrollen")

        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 10),
                      "Der Rueckweg aus den App-Einstellungen fehlt")
    }

    /// Dieser Fall wird auf dem iPhone-Destination-Lauf ausgefuehrt.
    func testAufKompakterBreiteBleibenBeideDialogeErreichbar() {
        starte(["-psm-preset-printer"])
        XCTAssertTrue(app.otherElements["start"].waitForExistence(timeout: 60))

        app.buttons["start.einrichtung"].tap()
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(1)

        let dialog = app.otherElements["dialog.einrichtung"]
        pruefeDialog(dialog, "Einrichtungsdialog")
        dialog.swipeUp()
        XCTAssertTrue(app.buttons["fertig"].isHittable,
                      "Der Abschluss der Einrichtung bleibt bei geringer Hoehe nicht erreichbar")
        let schliessen = app.buttons["einrichtung.schliessen"]
        XCTAssertTrue(schliessen.waitForExistence(timeout: 10),
                      "Der Rueckweg aus der erneut geoeffneten Einrichtung fehlt")
        XCTAssertTrue(schliessen.isHittable,
                      "Der Rueckweg bleibt bei geringer Hoehe nicht erreichbar")

        schliessen.tap()
        XCTAssertTrue(app.otherElements["start"].waitForExistence(timeout: 10),
                      "Schliessen der Einrichtung kehrt nicht zum Ausgangsbildschirm zurueck")

        app.buttons["start.appeinstellungen"].tap()
        let einstellungen = app.otherElements["dialog.appeinstellungen"]
        pruefeDialog(einstellungen, "App-Einstellungen-Dialog auf kompakter Breite")
        einstellungen.swipeUp()
        XCTAssertTrue(app.buttons["appeinstellungen.selbsttest"].waitForExistence(timeout: 10),
                      "App-Einstellungen bleiben auf kompakter Breite nicht scrollend")
        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["start"].waitForExistence(timeout: 10),
                      "Der Rueckweg aus den kompakten App-Einstellungen fehlt")
    }
}
