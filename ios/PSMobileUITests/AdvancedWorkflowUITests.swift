import XCTest

/// Prueft den Advanced Mode.
///
/// Er war lange die schwaechere Haelfte: Viewport, Liste, ein Knopf. Wer
/// alles sehen wollte, sah weniger als im Simple Mode. Diese Tests
/// halten fest, dass die Wege jetzt da sind - und dass die Zahlenfelder
/// wirklich am Modell ankommen, nicht nur im Bildschirmzustand.
final class AdvancedWorkflowUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testDieDreiEinstellungsbereicheHabenEigeneEinstiege() {
        // Am Desktop sind Druck, Filament und Drucker drei Reiter. Dreimal
        // denselben Bildschirm zu oeffnen und dann umzuschalten waere ein
        // Klick zu viel bei jedem Wechsel.
        for (knopf, reiter) in [("advanced.printSettings", "reiter.print"),
                                ("advanced.filamentSettings", "reiter.filament"),
                                ("advanced.printerSettings", "reiter.printer")] {
            app.buttons[knopf].tap()
            let seite = app.buttons["seite.0"]
            XCTAssertTrue(seite.waitForExistence(timeout: 15),
                          "Kein Einstieg ueber " + knopf)
            // Der gewaehlte Bereich muss oben auch markiert sein.
            XCTAssertTrue(app.buttons[reiter].exists, "Reiter fehlt: " + reiter)
            app.buttons["einstellungen.zurueck"].tap()
            XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 10))
        }
    }

    func testDerObjektbaumErscheintMitDerAuswahl() {
        XCTAssertFalse(app.otherElements["advanced.objectTree"].exists,
                       "Der Inspektor steht ohne Auswahl da")

        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()

        XCTAssertTrue(app.buttons["advanced.gizmo.rotate"].waitForExistence(timeout: 5),
                      "Der Inspektor erscheint nicht")
        // Die Griffe lassen sich umschalten - der Viewport kennt sie
        // laengst, erreichbar waren sie im Advanced Mode bisher nicht.
        app.buttons["advanced.gizmo.rotate"].tap()
        XCTAssertTrue(app.buttons["advanced.gizmo.scale"].exists)
    }

    func testEineVierteldrehungKommtAmModellAn() {
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        XCTAssertTrue(app.buttons["advanced.drehen.rechts"].waitForExistence(timeout: 5))

        let feld = app.textFields["advanced.rotate.Z"]
        XCTAssertTrue(feld.exists, "Das Drehfeld fehlt")
        XCTAssertEqual(feld.value as? String, "0")

        app.buttons["advanced.drehen.rechts"].tap()

        // Der Wert kommt aus dem Kern zurueck, nicht aus dem Knopf: der
        // Kern rechnet in Radiant, die Anzeige in Grad.
        XCTAssertTrue(warte(bis: { (feld.value as? String) == "90" }),
                      "Die Vierteldrehung steht nicht im Feld")
    }

    func testEinpassenAendertDieGroesse() {
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        let prozent = app.textFields["advanced.scale.prozent"]
        XCTAssertTrue(prozent.waitForExistence(timeout: 5))
        XCTAssertEqual(prozent.value as? String, "100.0")

        app.buttons["advanced.einpassen"].tap()

        // Ein 20-mm-Wuerfel auf einem 250er Bett wird deutlich groesser.
        XCTAssertTrue(warte(bis: { (prozent.value as? String) != "100.0" }),
                      "Einpassen hat die Groesse nicht veraendert")
    }

    private func warte(bis bedingung: () -> Bool, timeout: TimeInterval = 15) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }
}
