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
        app.sauberStarten(app.launchArguments)
        let marke = modus == "advanced" ? "arbeitsbereich" : "simple.arbeitsbereich"
        XCTAssertTrue(app.otherElements[marke].waitForExistence(timeout: 60))
        // Der Easy Mode arbeitet bewusst auf einem Bett - die Bettauswahl
        // gehoert seit dieser Sitzung nur noch zum Advanced Mode, sie
        // blockierte dort vorher die Oberflaeche ohne Nutzen.
        if modus == "advanced" {
            XCTAssertTrue(app.otherElements["bed.selector"].waitForExistence(timeout: 15),
                          "Die Bettauswahl fehlt im Advanced Mode")
        }
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
            XCTAssertTrue(app.bett("bed.add").waitForExistence(timeout: 5),
                          "Die kompakte Bettauswahl oeffnet kein Blatt")
        }
    }

    /// Der Easy Mode zeigt seit dieser Sitzung keine Bettauswahl mehr -
    /// Mehrbett ist bewusst ein Werkzeug des Advanced Mode. Dasselbe
    /// Verhalten (neues leeres Bett bleibt als Karte wechselbar) prueft
    /// testArrangePanelOrdnetDasExpliziteZielbett bereits fuer Advanced.
    func testBettauswahlFehltImSimpleMode() {
        starte("simple")
        XCTAssertFalse(app.otherElements["bed.selector"].exists,
                       "Der Easy Mode zeigt weiterhin eine Bettauswahl")
        XCTAssertFalse(app.bett("bed.add").exists,
                       "Der Easy Mode erlaubt weiterhin, ein Bett hinzuzufuegen")
    }

    func testLockKommtAusDemKernUndBlockiertArrange() {
        starte("advanced")
        oeffneAuswahlWennNoetig()

        let sperre = app.bett("bed.lock.0")
        XCTAssertTrue(sperre.waitForExistence(timeout: 5), "Bettsperre fehlt")
        sperre.tap()

        if istSchmal {
            app.buttons["bed.selector.close"].tap()
        }
        // Seit der Umstellung auf Tipp = alle anordnen / Halten = Panel
        // oeffnet nur noch ein langer Druck das Panel.
        app.buttons["schiene.arrange"].press(forDuration: 0.6)
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
        app.bett("bed.add").tap()

        if istSchmal {
            XCTAssertTrue(app.buttons["bed.selector.active"].waitForExistence(timeout: 5))
        }
        // Seit der Umstellung auf Tipp = alle anordnen / Halten = Panel
        // oeffnet nur noch ein langer Druck das Panel.
        app.buttons["schiene.arrange"].press(forDuration: 0.6)
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
