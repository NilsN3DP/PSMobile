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

    func testDerObjektbereichHatKeineDoppelteUeberschrift() {
        // Der Bereichstitel ist bereits der Einstieg selbst. Eine zweite
        // "Objekte (n)"-Ueberschrift darunter verdoppelt ihn nur und
        // schiebt die eigentliche Liste ohne Informationsgewinn nach unten.
        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(objekteReiter.waitForExistence(timeout: 10),
                      "Der Bereich Objekte fehlt")
        objekteReiter.tap()

        XCTAssertTrue(objekteReiter.isHittable,
                      "Die einzige Ueberschrift fuer Objekte ist nicht sichtbar")
        XCTAssertFalse(app.staticTexts.matching(
            NSPredicate(format: "%K BEGINSWITH[c] %@ OR %K BEGINSWITH[c] %@",
                        "label", "OBJECTS (", "label", "OBJEKTE (")
        ).firstMatch.exists,
        "Der Objektbereich zeigt neben seinem Bereichstitel eine zweite Ueberschrift")
    }

    func testDerObjektbaumErscheintMitDerAuswahl() {
        XCTAssertFalse(app.otherElements["advanced.objectTree"].exists,
                       "Der Inspektor steht ohne Auswahl da")

        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()

        // Der Inspektor zeigt Zahlen zum Objekt - daran erkennt man ihn,
        // seit die Griffe oben in der Werkzeugleiste stehen und dort
        // auch ohne Auswahl vorhanden (nur ausgegraut) sind.
        XCTAssertTrue(app.textFields["advanced.scale.prozent"].waitForExistence(timeout: 5),
                      "Der Inspektor erscheint nicht")
        // Und die Griffe lassen sich umschalten, jetzt von oben.
        app.buttons["advanced.gizmo.rotate"].tap()
        XCTAssertTrue(app.buttons["advanced.gizmo.scale"].isEnabled,
                      "Der Griff zum Skalieren ist mit Auswahl nicht bedienbar")
    }

    func testEineVierteldrehungKommtAmModellAn() {
        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
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
        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
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

    func testEinTeilLaesstSichAnlegenUndWiederEntfernen() {
        // Aussparung, Modifier, Stuetzenwunsch: das, wofuer man sonst
        // das Programm wechselt. Auf Android laengst da, auf iOS bis
        // hierher nicht.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()

        let knopf = app.buttons["advanced.teilHinzufuegen"]
        XCTAssertTrue(knopf.waitForExistence(timeout: 10),
                      "Kein Weg zu einem neuen Teil")
        // Vorher hat der Wuerfel genau einen Koerper - und der bleibt
        // ohne Entfernen-Knopf, sonst bliebe ein Objekt ohne Geometrie.
        XCTAssertFalse(app.buttons["advanced.teil.0.entfernen"].exists,
                       "Der Modellkoerper darf nicht entfernbar sein")

        knopf.tap()
        XCTAssertTrue(app.buttons["teil.hinzufuegen"].waitForExistence(timeout: 5),
                      "Das Blatt fehlt")
        app.buttons["teil.art.1"].tap()          // Aussparung
        app.buttons["teil.form.1"].tap()         // Zylinder
        app.buttons["teil.hinzufuegen"].tap()

        // Der Beweis ist die Liste, nicht das geschlossene Blatt.
        let entfernen = app.buttons["advanced.teil.1.entfernen"]
        XCTAssertTrue(entfernen.waitForExistence(timeout: 10),
                      "Das neue Teil steht nicht in der Liste")

        entfernen.tap()
        XCTAssertTrue(warte(bis: { !app.buttons["advanced.teil.1.entfernen"].exists }),
                      "Das Teil liess sich nicht wieder entfernen")
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
