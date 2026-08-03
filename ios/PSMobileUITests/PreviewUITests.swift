import XCTest

/// Prueft die Werkzeugspalte und die G-Code-Vorschau.
///
/// Der Viewport kann beides seit langem - drei Gizmos und die
/// Werkzeugwege aus libvgcode -, auf iOS war nur nichts davon
/// erreichbar. Ein Objekt liess sich ziehen, aber nicht drehen, und die
/// Vorschau gab es gar nicht.
final class PreviewUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testGizmosErscheinenNurMitAuswahl() {
        // Ohne ausgewaehltes Objekt haengt kein Gizmo an irgendetwas -
        // die Knoepfe waeren dann Zierde.
        XCTAssertFalse(app.buttons["werkzeug.drehen"].exists,
                       "Die Gizmo-Knoepfe stehen ohne Auswahl da")

        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).firstMatch.tap()

        XCTAssertTrue(app.buttons["werkzeug.drehen"].waitForExistence(timeout: 5),
                      "Mit Auswahl fehlen die Gizmo-Knoepfe")
        app.buttons["werkzeug.drehen"].tap()
        // Umschalten darf die Auswahl nicht verlieren - sonst waere das
        // Gizmo im selben Moment wieder weg.
        XCTAssertTrue(app.buttons["werkzeug.skalieren"].exists)
    }

    func testDieVorschauErscheintErstNachDemSchneiden() {
        XCTAssertFalse(app.buttons["werkzeug.vorschau"].exists,
                       "Die Vorschau steht schon vor dem Schneiden bereit")

        app.buttons["simple.werkzeug.G-Code"].tap()
        XCTAssertTrue(app.buttons["slice.schliessen"].waitForExistence(timeout: 180),
                      "Der Schnitt kam zu keinem Ergebnis")
        app.buttons["slice.schliessen"].tap()

        let vorschau = app.buttons["werkzeug.vorschau"]
        XCTAssertTrue(vorschau.waitForExistence(timeout: 10),
                      "Nach dem Schneiden fehlt der Vorschau-Knopf")
        vorschau.tap()

        // Der Schichtregler erscheint nur, wenn libvgcode wirklich
        // Schichten geliefert hat.
        XCTAssertTrue(app.sliders["vorschau.schicht"].waitForExistence(timeout: 60),
                      "Die Vorschau zeigt keine Schichten")

        // Und sie muss auch etwas zeichnen. Der Schichtregler allein
        // beweist nur, dass libvgcode Schichten gemeldet hat - nicht,
        // dass davon etwas auf dem Schirm landet.
        sleep(2)
        let mitAllen = XCUIScreen.main.screenshot().pngRepresentation
        app.sliders["vorschau.schicht"].adjust(toNormalizedSliderPosition: 0.1)
        sleep(2)
        let mitWenigen = XCUIScreen.main.screenshot().pngRepresentation
        XCTAssertNotEqual(mitAllen, mitWenigen,
                          "Der Schichtregler aendert das Bild nicht")
        try? mitAllen.write(to: URL(fileURLWithPath: "/tmp/psm-vorschau.png"))
    }
}
