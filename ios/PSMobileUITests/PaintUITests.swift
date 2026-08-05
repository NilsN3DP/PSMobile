import XCTest

/// Prueft das Bemalen.
///
/// Ein Pinselstrich ist schwer zu pruefen, weil man nicht weiss, wo auf
/// dem Schirm das Modell gerade liegt. Was sich pruefen laesst, ist der
/// Weg dorthin und die Wirkung: markiert der Strich Facetten, und
/// verschwinden sie beim Loeschen wieder. Die Zahl kommt aus dem Kern.
final class PaintUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        // Und die Malwerkzeuge liegen im Reiter daneben.
        let werkzeugeReiter = app.buttons["inspektor.werkzeuge"]
        if werkzeugeReiter.waitForExistence(timeout: 5), werkzeugeReiter.isEnabled {
            werkzeugeReiter.tap()
        }
    }

    func testDieWerkzeugeErscheinenMitDerAuswahl() {
        XCTAssertTrue(app.buttons["malen.stuetzen"].waitForExistence(timeout: 5),
                      "Die Malwerkzeuge fehlen")
        // Ohne gewaehltes Werkzeug gibt es keinen Pinsel und keinen
        // Zustand - sie waeren Zierde.
        XCTAssertFalse(app.sliders["malen.radius"].exists)

        app.buttons["malen.stuetzen"].tap()
        XCTAssertTrue(app.sliders["malen.radius"].waitForExistence(timeout: 5),
                      "Der Pinselregler fehlt")
        XCTAssertTrue(app.buttons["malen.zustand.2"].exists,
                      "Stuetzen sperren fehlt")

        app.buttons["malen.aus"].tap()
        XCTAssertFalse(app.sliders["malen.radius"].exists,
                       "Das Werkzeug laesst sich nicht abstellen")
    }

    func testEinStrichMarkiertUndLoeschenRaeumtAuf() {
        app.buttons["malen.stuetzen"].tap()
        XCTAssertTrue(app.staticTexts["malen.anzahl"].waitForExistence(timeout: 5))
        let vorher = app.staticTexts["malen.anzahl"].label
        XCTAssertTrue(vorher.contains("0"), "Am Anfang darf nichts markiert sein: " + vorher)

        // In die Mitte der Flaeche tippen - dort liegt der Wuerfel.
        app.otherElements["viewport"].coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        let anzahl = app.staticTexts["malen.anzahl"]
        XCTAssertTrue(warte(bis: { !anzahl.label.contains(" 0") && anzahl.label != vorher }),
                      "Der Strich hat nichts markiert: " + anzahl.label)

        app.buttons["malen.loeschen"].tap()
        XCTAssertTrue(warte(bis: { anzahl.label.contains("0") }),
                      "Loeschen raeumt nicht auf: " + anzahl.label)
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
