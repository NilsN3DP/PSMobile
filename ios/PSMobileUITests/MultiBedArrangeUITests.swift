import XCTest

/// Gemeinsamer Mehrbett-Vertrag fuer Simple und Advanced.
///
/// Derselbe Test laeuft auf iPad und iPhone. Auf breiten Fenstern sind
/// Bettkarten unmittelbar sichtbar, auf schmalen oeffnet der aktive
/// Bettknopf ein Blatt. Damit beweist der Lauf die echte responsive
/// Bedienung statt nur zwei verschiedene Bildschirmbreiten.
final class MultiBedArrangeUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private var istSchmal: Bool {
        app.windows.firstMatch.frame.width < 760
    }

    private func starte(_ modus: String, mitWuerfel: Bool = true) {
        app.launchArguments = [
            "-psm-preset-printer",
            modus == "advanced" ? "-psm-start-advanced" : "-psm-start-simple"
        ]
        if mitWuerfel { app.launchArguments.append("-psm-load-cube") }
        app.launch()
        let marke = modus == "advanced" ? "arbeitsbereich" : "simple.arbeitsbereich"
        XCTAssertTrue(app.otherElements[marke].waitForExistence(timeout: 60))
        XCTAssertTrue(app.otherElements["bed.selector"].waitForExistence(timeout: 15),
                      "Die gemeinsame Bettauswahl fehlt in \(modus)")
    }

    private func oeffneAuswahlWennNoetig() {
        if istSchmal {
            let aktiv = app.buttons["bed.selector.active"]
            XCTAssertTrue(aktiv.isHittable, "Der aktive Bettknopf ist nicht direkt erreichbar")
            aktiv.tap()
            // Der eigene Blatt-Marker ist auf einem NavigationStack je
            // nach Host nicht immer eine separate Accessibility-Entitaet.
            // Der nur im Blatt vorhandene Hinzufuegen-Knopf beweist die
            // sichtbare und bedienbare Praesentation unmittelbar.
            XCTAssertTrue(app.buttons["bed.add"].waitForExistence(timeout: 5),
                          "Die kompakte Bettauswahl oeffnet kein Blatt")
        }
    }

    func testSelectorBleibtImSimpleModeAufLeeremBettWechselbar() {
        starte("simple")
        oeffneAuswahlWennNoetig()

        let neu = app.buttons["bed.add"]
        XCTAssertTrue(neu.waitForExistence(timeout: 5), "Neues Bett fehlt")
        neu.tap()

        if istSchmal {
            XCTAssertTrue(app.buttons["bed.selector.active"]
                .waitForExistence(timeout: 5),
                "Die Auswahl verschwindet auf dem leeren neuen Bett")
            app.buttons["bed.selector.active"].tap()
            XCTAssertTrue(app.buttons["bed.card.0"].waitForExistence(timeout: 5),
                          "Vom leeren Bett fuehrt kein Weg zu Bett 1")
        } else {
            XCTAssertTrue(app.buttons["bed.card.0"].isHittable)
            XCTAssertTrue(app.buttons["bed.card.1"].isHittable,
                          "Das leere Bett bleibt nicht als Karte wechselbar")
        }
    }

    func testLockKommtAusDemKernUndBlockiertArrange() {
        starte("advanced")
        oeffneAuswahlWennNoetig()

        let sperre = app.buttons["bed.lock.0"]
        XCTAssertTrue(sperre.waitForExistence(timeout: 5), "Bettsperre fehlt")
        sperre.tap()

        if istSchmal {
            app.buttons["bed.selector.close"].tap()
        }
        app.buttons["arrange.open"].tap()
        XCTAssertTrue(app.otherElements["arrange.panel"].waitForExistence(timeout: 5))
        app.buttons["arrange.run"].tap()

        let ergebnis = app.staticTexts["arrange.result"]
        XCTAssertTrue(ergebnis.waitForExistence(timeout: 10))
        XCTAssertTrue(ergebnis.label.localizedCaseInsensitiveContains("gesperrt") ||
                      ergebnis.label.localizedCaseInsensitiveContains("locked"),
                      "Der Kernfehler fuer ein gesperrtes Bett wird nicht erklaert")
    }

    func testArrangePanelOrdnetDasExpliziteZielbett() {
        starte("advanced")
        oeffneAuswahlWennNoetig()
        app.buttons["bed.add"].tap()

        if istSchmal {
            XCTAssertTrue(app.buttons["bed.selector.active"].waitForExistence(timeout: 5))
        }
        app.buttons["arrange.open"].tap()
        XCTAssertTrue(app.otherElements["arrange.panel"].waitForExistence(timeout: 5))

        let ziel = app.buttons["arrange.target.0"]
        XCTAssertTrue(ziel.waitForExistence(timeout: 5),
                      "Bett 1 ist nicht als Arrange-Ziel waehlbar")
        ziel.tap()
        app.buttons["arrange.run"].tap()

        let ergebnis = app.staticTexts["arrange.result"]
        XCTAssertTrue(ergebnis.waitForExistence(timeout: 10))
        XCTAssertTrue(ergebnis.label.contains("1"),
                      "Das Ergebnis nennt das explizit angeordnete Zielbett nicht")
    }
}
