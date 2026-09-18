import XCTest

/// Prueft Projekte und die Schrittfolge zum Zuruecknehmen.
///
/// Ohne Projekte ueberlebt nichts einen App-Start: man laedt seine
/// Modelle jedes Mal neu und stellt alles noch einmal ein. Und ohne
/// Zurueck ist jeder Fehlgriff endgueltig - auf einem Touchgeraet, wo
/// man sich leicht vertippt, waere das die haerteste Einschraenkung.
final class ProjectUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    private var zeilen: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'"))
    }

    func testZurueckNimmtDasKlonenZurueck() {
        app.buttons["blatt.alle"].tap()
        app.buttons["blatt.klonen"].tap()
        XCTAssertTrue(warte(bis: { self.zeilen.count == 2 }),
                      "Das Klonen hat nicht gewirkt")

        let zurueck = app.buttons["simple.zurueckschritt"]
        XCTAssertTrue(zurueck.isEnabled,
                      "Nach dem Klonen ist kein Schritt zum Zuruecknehmen da")
        zurueck.tap()

        XCTAssertTrue(warte(bis: { self.zeilen.count == 1 }),
                      "Zurueck hat das Klonen nicht rueckgaengig gemacht")

        // Und wieder vor: sonst waere ein versehentliches Zurueck genauso
        // endgueltig wie der Fehler davor.
        app.buttons["simple.wiederholen"].tap()
        XCTAssertTrue(warte(bis: { self.zeilen.count == 2 }),
                      "Wiederholen bringt den Klon nicht zurueck")
    }

    func testProjektSichernErzeugtEineDatei() {
        app.buttons["simple.werkzeug.Projects"].tap()

        let sichern = app.buttons["projekt.sichern"]
        XCTAssertTrue(sichern.waitForExistence(timeout: 5),
                      "Im Projekte-Panel fehlt das Sichern")
        // Vorher gibt es nichts weiterzugeben - der Knopf erscheint erst
        // mit der Datei.
        XCTAssertFalse(app.buttons["projekt.weitergeben"].exists)

        sichern.tap()
        // Seit dem 15.09.2026 fragt auch der Simple Mode nach dem Namen.
        let ok = app.buttons["projekt.sichern.ok"].firstMatch
        XCTAssertTrue(ok.waitForExistence(timeout: 5), "Die Namensabfrage fehlt")
        // Und schlaegt seit dem 16.09.2026 das erste Objekt ohne Endung vor -
        // vorher stand "psm-testwuerfel.stl" im Feld (Fund 29).
        // Im Alert traegt das Feld auf dem iPhone-Simulator keine Kennung -
        // dann das erste Textfeld des Alerts.
        let benannt = app.textFields["projekt.sichern.name"].firstMatch
        let feld = benannt.exists ? benannt : app.alerts.firstMatch.textFields.firstMatch
        XCTAssertTrue(feld.waitForExistence(timeout: 5), "Das Namensfeld fehlt")
        let vorschlag = feld.value as? String ?? ""
        XCTAssertEqual(vorschlag, "psm-testwuerfel", "Vorschlag ohne Endung erwartet")
        ok.tap()
        XCTAssertTrue(app.buttons["projekt.weitergeben"].waitForExistence(timeout: 30),
                      "Es ist keine Projektdatei entstanden")
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
