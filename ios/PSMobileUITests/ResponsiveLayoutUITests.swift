import XCTest

/// Prueft, dass die Oberflaeche auch auf schmalen Geraeten bedienbar
/// bleibt.
///
/// Die Skalierung rechnet aus der Fenstergroesse, aber eine Zahl allein
/// beweist nichts: ein Knopf kann rechnerisch passen und trotzdem unter
/// der Systemleiste liegen oder aus dem Bild ragen. Diese Tests laufen
/// auf demselben Bestand wie die anderen, nur auf einem iPhone.
///
/// Geprueft wird zweierlei, und beides ist genau das, was auf einem
/// grossen Bildschirm nie auffaellt:
///   - liegt das Element ganz im Fenster?
///   - laesst es sich treffen, oder verdeckt es etwas anderes?
final class ResponsiveLayoutUITests: XCTestCase {

    private var app: XCUIApplication!

    private func starte(_ argumente: [String]) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = argumente
        app.launch()
    }

    /// Ganz im Fenster und treffbar.
    private func pruefe(_ element: XCUIElement, _ name: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 30), name + " fehlt")
        let fenster = app.windows.firstMatch.frame
        let rahmen = element.frame
        XCTAssertTrue(fenster.contains(rahmen),
                      "\(name) ragt aus dem Fenster: \(rahmen) in \(fenster)")
        XCTAssertTrue(element.isHittable, "\(name) ist nicht treffbar")
    }

    func testSimpleModeBleibtBedienbar() {
        starte(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        // Die Werkzeugleiste ist der engste Fall: sechs Knoepfe in einer
        // Zeile, und rechts muss G-Code noch hineinpassen.
        pruefe(app.buttons["simple.werkzeug.Projects"], "Projekte")
        pruefe(app.buttons["simple.werkzeug.Settings"], "Einstellen")
        pruefe(app.buttons["simple.werkzeug.G-Code"], "G-Code")

        // Das Modelle-Blatt darf das Bett nicht ganz verdecken und muss
        // unten im Bild bleiben.
        pruefe(app.buttons["blatt.mehr"], "Modelle-Blatt")

        // Und das Panel: auf einem schmalen Geraet ist es das erste, was
        // aus dem Bild laeuft.
        app.buttons["simple.werkzeug.Settings"].tap()
        pruefe(app.buttons["simple.karte.SUPPORTS"], "Stuetzen-Karte")
        pruefe(app.buttons["simple.appeinstellungen"], "App-Einstellungen")
    }

    func testAdvancedModeBleibtBedienbar() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        pruefe(app.buttons["advanced.printSettings"], "Druckeinstellungen")
        pruefe(app.buttons["slicen"], "Slicen")

        // Auf schmalen Fenstern liegt der Inspektor ueber dem Bett. Er
        // muss trotzdem ganz sichtbar sein.
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        pruefe(app.buttons["advanced.gizmo.move"], "Gizmo Verschieben")
        pruefe(app.buttons["advanced.einpassen"], "Einpassen")
    }

    func testDieErsteinrichtungPasstAufsSchmaleGeraet() {
        // Der erste Bildschirm ueberhaupt - wenn der nicht passt, kommt
        // niemand weiter.
        starte(["-psm-reset-setup"])
        let suche = app.textFields.firstMatch
        XCTAssertTrue(suche.waitForExistence(timeout: 90), "Die Ersteinrichtung fehlt")
        pruefe(suche, "Suchfeld der Ersteinrichtung")
    }
}
