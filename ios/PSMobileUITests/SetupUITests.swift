import XCTest

/// Bedient die Ersteinrichtung wie ein Finger.
///
/// Der Grund fuer dieses Ziel: `simctl` kann installieren, starten und
/// ein Bild machen - aber nicht tippen. Bis hierhin liess sich jeder
/// Bildschirm nur ansehen. Ob eine Familie sich aufklappt, eine
/// Duesengroesse sich waehlen laesst und der Abschluss in den
/// Arbeitsbereich fuehrt, war damit unpruefbar.
///
/// Gegenstueck zu den Emulator-Durchlaeufen auf Android.
final class SetupUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // Ohne Zuruecksetzen startet der zweite Durchlauf mit bereits
        // eingerichtetem Drucker - und prueft dann nichts mehr.
        app.sauberStarten(["-psm-reset-setup", "-psm-start-advanced"])
    }

    func testDurchlaufDerErsteinrichtung() {
        // Die Ersteinrichtung braucht einen Moment: sie liest
        // PrusaSlicers Vendor-Dateien.
        //
        // Oberste Ebene ist der Hersteller, darunter erst die Familien:
        // wer einen Voron sucht, soll sich nicht durch Prusa-Familien
        // lesen muessen.
        let marke = app.buttons["hersteller.PrusaResearch"]
        XCTAssertTrue(marke.waitForExistence(timeout: 30),
                      "Die Herstellerliste ist nicht erschienen")
        XCTAssertFalse(app.buttons["familie.CORE"].exists,
                       "Familien stehen schon vor dem Aufklappen des Herstellers da")
        marke.tap()

        let core = app.buttons["familie.CORE"]
        XCTAssertTrue(core.waitForExistence(timeout: 10),
                      "Unter dem Hersteller fehlen die Familien")

        // Zugeklappt darf keine Duesengroesse sichtbar sein - sonst waere
        // die Liste wieder so lang wie vorher.
        XCTAssertFalse(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'variante.'")).firstMatch.exists,
            "Varianten sind schon vor dem Aufklappen sichtbar")

        core.tap()

        let variante = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'variante.'")).firstMatch
        XCTAssertTrue(variante.waitForExistence(timeout: 5),
                      "Nach dem Aufklappen fehlen die Duesengroessen")        // Ohne Auswahl bleibt der Abschluss gesperrt. Sonst richtet man
        // versehentlich nichts ein und landet in einer App ohne Profile.
        let fertig = app.buttons["fertig"]
        XCTAssertTrue(fertig.exists)
        XCTAssertFalse(fertig.isEnabled, "Abschluss ist ohne Auswahl bedienbar")

        variante.tap()
        XCTAssertTrue(fertig.isEnabled, "Abschluss bleibt nach der Auswahl gesperrt")

        fertig.tap()

        // Das Einrichten laedt Profile - das dauert.
        let arbeitsbereich = app.otherElements["arbeitsbereich"]
        XCTAssertTrue(arbeitsbereich.waitForExistence(timeout: 90),
                      "Nach dem Abschluss kommt der Arbeitsbereich nicht")
    }

    func testSucheBlendetDieGliederungAus() {
        XCTAssertTrue(app.buttons["hersteller.PrusaResearch"].waitForExistence(timeout: 30),
                      "Die Herstellerliste ist nicht erschienen")

        let feld = app.textFields["einrichtung.suche"]
        feld.tap()
        feld.typeText("MK4")

        // Wer tippt, will Treffer sehen und keine Zwischenueberschriften.
        // Dieselbe Regel wie auf Android.
        XCTAssertFalse(app.buttons["familie.CORE"].exists,
                       "Waehrend der Suche steht die Gliederung noch da")
        XCTAssertFalse(app.buttons["hersteller.Voron"].exists,
                       "Waehrend der Suche stehen fremde Hersteller noch da")
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'variante.'")).firstMatch
            .waitForExistence(timeout: 5),
            "Die Suche zeigt keine Treffer")
    }
}
