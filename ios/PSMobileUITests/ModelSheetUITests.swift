import XCTest

/// Prueft das Modelle-Blatt im Simple Mode.
///
/// Es ist die einzige Stelle, an der auf iOS mehrere Objekte verwaltet
/// werden koennen - klonen, entfernen, anordnen, auf ein anderes Bett
/// schieben. Bis hierhin gab es dort nur einen Knopf zum Hinzufuegen.
final class ModelSheetUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testDasBlattLoestDenEinzelnenKnopfAb() {
        // Solange etwas auf dem Bett liegt, tritt das Blatt an die Stelle
        // von "Modell hinzufuegen".
        XCTAssertTrue(app.buttons["blatt.mehr"].waitForExistence(timeout: 10),
                      "Das Modelle-Blatt fehlt")
        XCTAssertFalse(app.buttons["simple.modell"].exists,
                       "Der einzelne Knopf steht noch daneben")
    }

    func testKlonenUndEntfernen() {
        let zeile = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'"))
        XCTAssertEqual(zeile.count, 1, "Es liegt nicht genau ein Wuerfel auf dem Bett")

        // Ohne Auswahl gibt es nichts zu klonen - erst alles waehlen.
        app.buttons["blatt.alle"].tap()
        app.buttons["blatt.klonen"].tap()

        let zwei = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'"))
        XCTAssertTrue(warte(bis: { zwei.count == 2 }),
                      "Nach dem Klonen liegen nicht zwei Objekte auf dem Bett")

        // Anordnen wird erst mit zwei Objekten sinnvoll - die Regel dazu
        // steht im gemeinsamen Modul.
        XCTAssertTrue(app.buttons["blatt.anordnen"].isEnabled,
                      "Anordnen bleibt bei zwei Objekten gesperrt")

        // Nach dem Klonen ist der Ausgangswuerfel weiterhin angehakt -
        // die Kopfzeile zeigt also die Aktionen fuer die Auswahl, nicht
        // "Alle waehlen". Erst abwaehlen, dann beide nehmen.
        app.buttons["blatt.abbrechen"].tap()
        app.buttons["blatt.alle"].tap()
        app.buttons["blatt.entfernen"].tap()
        XCTAssertTrue(warte(bis: {
            app.buttons.matching(
                NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).count == 0
        }), "Nach dem Entfernen liegt noch etwas auf dem Bett")

        // Und auf leerem Bett kommt der einzelne Knopf zurueck.
        XCTAssertTrue(app.buttons["simple.modell"].waitForExistence(timeout: 5),
                      "Auf leerem Bett fehlt der Knopf zum Hinzufuegen")
    }

    func testOhneAuswahlBleibenDieAktionenGesperrt() {
        // Ein einzelnes Objekt laesst sich nicht anordnen, und ohne
        // Auswahl gibt es weder Klonen noch Entfernen.
        XCTAssertTrue(app.buttons["blatt.alle"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["blatt.anordnen"].isEnabled,
                       "Anordnen ist bei einem einzigen Objekt nicht gesperrt")
        XCTAssertFalse(app.buttons["blatt.klonen"].exists,
                       "Klonen erscheint ohne Auswahl")
    }

    private func warte(bis bedingung: () -> Bool, timeout: TimeInterval = 10) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }
}
