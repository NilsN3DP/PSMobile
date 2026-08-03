import XCTest

/// Prueft den Weg vom Modell zum G-Code.
///
/// Der eigentliche Zweck der App. Bis hierhin war er der einzige, der
/// nirgends geprueft wurde: der Kern rechnete, aber ob die Oberflaeche
/// den Fortschritt zeigt, die Zahlen richtig aufbereitet und die Datei
/// herausgibt, stand nirgends belegt.
final class SliceUITests: XCTestCase {

    private var app: XCUIApplication!

    private func starte(_ argumente: [String]) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = argumente
        app.launch()
    }

    func testOhneModellNenntDieAppDenGrund() {
        starte(["-psm-preset-printer", "-psm-start-simple"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        app.buttons["simple.werkzeug.G-Code"].tap()

        // Ein ausgegrauter Knopf sagt nur, dass es nicht geht. Er sagt
        // nicht, was fehlt.
        XCTAssertTrue(app.otherElements["slice.hinderungsgrund"].waitForExistence(timeout: 5),
                      "Ohne Modell kommt keine Begruendung")
        XCTAssertTrue(app.staticTexts.allElementsBoundByIndex.contains {
            $0.label.contains("Nothing on the bed")
        }, "Der genannte Grund passt nicht")

        app.buttons["slice.hinderungsgrund.schliessen"].tap()
        XCTAssertFalse(app.otherElements["slice.hinderungsgrund"].exists)
    }

    func testMitModellEntstehtEinGCode() {
        starte(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        app.buttons["simple.werkzeug.G-Code"].tap()

        // Das Blatt muss sofort da sein - ein Slice dauert, und eine App,
        // die waehrenddessen unveraendert aussieht, wirkt abgestuerzt.
        XCTAssertTrue(app.otherElements["slice.blatt"].waitForExistence(timeout: 5),
                      "Waehrend des Schneidens ist nichts zu sehen")

        // Ein 20-mm-Wuerfel ist in Sekunden geschnitten; die Grenze ist
        // grosszuegig, weil der Simulator langsamer rechnet als ein Geraet.
        let sichern = app.buttons["slice.sichern"]
        XCTAssertTrue(sichern.waitForExistence(timeout: 180),
                      "Es kam kein Ergebnis - oder der G-Code liess sich nicht schreiben")

        // Die Zusammenfassung nennt Druckzeit und Verbrauch. Steht dort
        // ein Strich, hat der Kern keine Zahlen geliefert.
        let texte = app.staticTexts.allElementsBoundByIndex.map(\.label)
        XCTAssertTrue(texte.contains { $0.hasSuffix("m") && $0 != "–" },
                      "Keine Druckzeit in der Zusammenfassung")
        XCTAssertTrue(texte.contains { $0.hasSuffix(" g") },
                      "Kein Materialverbrauch in der Zusammenfassung")

        app.buttons["slice.schliessen"].tap()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 10))
    }

    func testDasModellLiegtAufDemBett() {
        // Ohne diesen Nachweis koennte der Wuerfel auch stillschweigend
        // nicht geladen worden sein - und der Slice-Test liefe dann gegen
        // ein leeres Bett.
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        XCTAssertTrue(app.staticTexts.allElementsBoundByIndex.contains {
            $0.label.contains("20.0 x 20.0 x 20.0 mm")
        }, "Der Testwuerfel steht nicht in der Modellliste")
    }
}
