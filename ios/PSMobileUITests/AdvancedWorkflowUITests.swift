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
        app.sauberStarten(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
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

        erstesObjektWaehlen()

        // Der Inspektor zeigt Zahlen zum Objekt - daran erkennt man ihn,
        // seit die Griffe oben in der Werkzeugleiste stehen und dort
        // auch ohne Auswahl vorhanden (nur ausgegraut) sind.
        XCTAssertTrue(warteBisTreffbar(app.textFields["advanced.scale.prozent"]),
                      "Der Inspektor erscheint nicht")
        // Und die Griffe lassen sich umschalten, jetzt von oben.
        app.buttons["advanced.gizmo.rotate"].tap()
        XCTAssertTrue(app.buttons["advanced.gizmo.scale"].isEnabled,
                      "Der Griff zum Skalieren ist mit Auswahl nicht bedienbar")
    }

    func testObjektauswahlFuehrtInDenSichtbarenBearbeitenbereich() {
        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(warteBisTreffbar(objekteReiter),
                      "Der Bereich Objekte ist nicht erreichbar")
        objekteReiter.tap()

        let objektzeile = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        XCTAssertTrue(warteBisTreffbar(objektzeile),
                      "Die Auswahlzeile fehlt")
        objektzeile.tap()

        // Die Auswahl bleibt im aufklappbaren Objektbereich erhalten.
        // Gleichzeitig muss der Tap direkt zu den Zahlen führen, ohne
        // dass man den Bearbeitenbereich erst suchen und öffnen muss.
        let prozent = app.textFields["advanced.scale.prozent"]
        XCTAssertTrue(warteBisTreffbar(prozent),
                      "Der Bearbeitenbereich wurde nicht geöffnet")
        XCTAssertTrue(warteBisTreffbar(app.textFields["advanced.rotate.Z"]),
                      "Das Drehfeld ist nicht treffbar")
        XCTAssertTrue(warteBisTreffbar(app.buttons["advanced.einpassen"]),
                      "Einpassen ist nicht treffbar")
        XCTAssertTrue(warteBisTreffbar(app.buttons["advanced.schichten"]),
                      "Der Einstieg für Schichthöhen ist nicht treffbar")
        // Erst nach den Inspector-Feldern erneut prüfen: So kann ein
        // später Fokus-Sprung die Zeile nicht unbemerkt hinausschieben.
        XCTAssertTrue(warteBisTreffbar(objektzeile),
                      "Die Auswahlzeile ist nicht mehr erreichbar")
    }

    func testMehrereObjekteFuehrenAuchBeiKompakterHoeheZumInspector() {
        app.terminate()
        app.sauberStarten(["-psm-preset-printer", "-psm-start-advanced",
                               "-psm-load-cube", "-psm-test-many-cubes"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        XCUIDevice.shared.orientation = .landscapeLeft
        addTeardownBlock { XCUIDevice.shared.orientation = .portrait }
        // Wie in FloatingDialogUITests: erst die Drehung zu Ende kommen
        // lassen. Ein Tipp waehrend der Animation landet auf den alten
        // Koordinaten - im Lauf 12 am 12.09. blieb OBJECTS deshalb zu,
        // obwohl die zwoelf Wuerfel laengst auf dem Bett standen.
        sleep(1)

        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(warteBisTreffbar(objekteReiter),
                      "Der Objektbereich ist im Querformat nicht erreichbar")
        objekteReiter.tap()

        let objektzeilen = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'"))
        if !warte(bis: { objektzeilen.count > 0 }, timeout: 3) {
            // Der Bereich ist noch zu - einmal nachfassen.
            objekteReiter.tap()
        }
        XCTAssertTrue(warte(bis: { objektzeilen.count == 12 }, timeout: 30),
                      "Die Mehrfachobjekt-Szene wurde nicht geladen")
        let ersteZeile = objektzeilen.firstMatch
        XCTAssertTrue(warteBisTreffbar(ersteZeile),
                      "Die erste Objektzeile ist nicht erreichbar")
        ersteZeile.tap()

        XCTAssertTrue(warteBisTreffbar(app.textFields["advanced.scale.prozent"]),
                      "Das Größenfeld bleibt bei vielen Objekten abgeschnitten")
        XCTAssertTrue(warteBisTreffbar(app.textFields["advanced.rotate.Z"]),
                      "Das Drehfeld bleibt bei vielen Objekten abgeschnitten")
        XCTAssertTrue(warteBisTreffbar(app.buttons["advanced.einpassen"]),
                      "Einpassen bleibt bei vielen Objekten abgeschnitten")
        XCTAssertTrue(warteBisTreffbar(app.buttons["advanced.schichten"]),
                      "Schichthöhen bleiben bei vielen Objekten abgeschnitten")
    }

    func testEineVierteldrehungKommtAmModellAn() {
        erstesObjektWaehlen()
        let rechts = app.buttons["advanced.drehen.rechts"]
        XCTAssertTrue(warteBisTreffbar(rechts),
                      "Die Vierteldrehung ist nicht erreichbar")

        let feld = app.textFields["advanced.rotate.Z"]
        XCTAssertTrue(warteBisTreffbar(feld), "Das Drehfeld fehlt")
        XCTAssertEqual(feld.value as? String, "0")

        rechts.tap()

        // Der Wert kommt aus dem Kern zurueck, nicht aus dem Knopf: der
        // Kern rechnet in Radiant, die Anzeige in Grad.
        XCTAssertTrue(warte(bis: { (feld.value as? String) == "90" }),
                      "Die Vierteldrehung steht nicht im Feld")
    }

    func testEinpassenAendertDieGroesse() {
        erstesObjektWaehlen()
        let prozent = app.textFields["advanced.scale.prozent"]
        XCTAssertTrue(warteBisTreffbar(prozent),
                      "Das Größenfeld ist nicht erreichbar")
        XCTAssertEqual(prozent.value as? String, "100.0")

        let einpassen = app.buttons["advanced.einpassen"]
        XCTAssertTrue(warteBisTreffbar(einpassen),
                      "Einpassen ist nicht erreichbar")
        einpassen.tap()

        // Ein 20-mm-Wuerfel auf einem 250er Bett wird deutlich groesser.
        XCTAssertTrue(warte(bis: { (prozent.value as? String) != "100.0" }),
                      "Einpassen hat die Groesse nicht veraendert")
    }

    func testEinTeilLaesstSichAnlegenUndWiederEntfernen() {
        // Aussparung, Modifier, Stuetzenwunsch: das, wofuer man sonst
        // das Programm wechselt. Auf Android laengst da, auf iOS bis
        // hierher nicht.
        erstesObjektWaehlen()

        let knopf = app.buttons["advanced.teilHinzufuegen"]
        XCTAssertTrue(warteBisTreffbar(knopf),
                      "Kein erreichbarer Weg zu einem neuen Teil")
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

    @discardableResult
    private func erstesObjektWaehlen() -> XCUIElement {
        // Die Objektliste liegt hinter ihrem aufklappbaren Bereich.
        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(warteBisTreffbar(objekteReiter),
                      "Der Bereich Objekte ist nicht erreichbar")
        if objekteReiter.isEnabled { objekteReiter.tap() }

        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        XCTAssertTrue(warteBisTreffbar(objekt), "Keine erreichbare Objektzeile")
        objekt.tap()
        return objekt
    }

    private func warteBisTreffbar(_ element: XCUIElement,
                                  timeout: TimeInterval = 10) -> Bool {
        if warte(bis: { element.exists && element.isHittable }, timeout: timeout) { return true }
        // Noch nicht treffbar heisst meist: der Bereich liegt unter dem
        // Fensterrand. Die Seitenleiste scrollt - also hinblaettern,
        // statt zu melden, die App koenne es nicht.
        //
        // Am 11.09.2026 fielen sechs Faelle dieser Datei mit "Der
        // Bereich Objekte ist nicht erreichbar" durch, auf einem iPad
        // Pro 13 hochkant. Das sagte nichts ueber die App, sondern
        // ueber die Fensterhoehe.
        let leiste = app.otherElements["seitenleiste"]
        guard leiste.exists else { return false }
        for _ in 0..<4 {
            leiste.swipeUp()
            if warte(bis: { element.exists && element.isHittable }, timeout: 2) { return true }
        }
        for _ in 0..<6 {
            leiste.swipeDown()
            if warte(bis: { element.exists && element.isHittable }, timeout: 2) { return true }
        }
        return false
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
