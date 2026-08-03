import XCTest

/// Prueft die schwebende Leiste am ausgewaehlten Objekt.
///
/// Schneiden, Teilen und Klonen waren im Simple Mode auf iOS gar nicht
/// erreichbar - man haette in den Advanced Mode wechseln muessen, nur um
/// ein Objekt zu halbieren.
final class ObjectBarUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        // Ueber die Zeile im Modelle-Blatt auswaehlen und nicht durch
        // Tippen ins Bild: wo der Wuerfel auf dem Schirm liegt, haengt an
        // der Kamera, und ein Test soll nicht daran scheitern.
        let zeile = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).firstMatch
        XCTAssertTrue(zeile.waitForExistence(timeout: 10))
        zeile.tap()
    }

    func testDieLeisteErscheintMitDerAuswahl() {
        XCTAssertTrue(app.buttons["objekt.klonen"].waitForExistence(timeout: 5),
                      "Die Objektleiste erscheint nicht")

        app.buttons["objekt.zurueck"].tap()
        XCTAssertFalse(app.buttons["objekt.klonen"].exists,
                       "Die Leiste bleibt nach dem Abwaehlen stehen")
    }

    func testKlonenAusDerLeiste() {
        XCTAssertTrue(app.buttons["objekt.klonen"].waitForExistence(timeout: 5))
        app.buttons["objekt.klonen"].tap()

        let zeilen = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'"))
        XCTAssertTrue(warte(bis: { zeilen.count == 2 }),
                      "Nach dem Klonen liegen nicht zwei Objekte auf dem Bett")
    }

    func testSchneidenTeiltDenWuerfelInZwei() {
        XCTAssertTrue(app.buttons["objekt.schneiden"].waitForExistence(timeout: 5))
        app.buttons["objekt.schneiden"].tap()

        // Die Voreinstellung liegt auf halber Hoehe - genau das, was man
        // bei einem Wuerfel will, ohne etwas einzustellen.
        let ausfuehren = app.buttons["objekt.schnitt.ausfuehren"]
        XCTAssertTrue(ausfuehren.waitForExistence(timeout: 5),
                      "Das Schnittblatt ist nicht erschienen")
        ausfuehren.tap()

        let zeilen = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'"))
        XCTAssertTrue(warte(bis: { zeilen.count == 2 }, timeout: 30),
                      "Aus dem Schnitt sind keine zwei Objekte geworden")
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
