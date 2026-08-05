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
        app.launchArguments = [
            "-psm-preset-printer", "-psm-test-eight-extruders",
            "-psm-start-advanced", "-psm-load-cube"
        ]
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
        XCTAssertTrue(app.buttons["malen.modus.pinsel"].exists)
        XCTAssertTrue(app.buttons["malen.modus.smart"].exists)
        XCTAssertFalse(app.buttons["malen.modus.eimer"].exists,
                       "Support darf keinen erfundenen Bucket Fill anbieten")
        XCTAssertTrue(app.buttons["malen.form.kreis"].exists)
        XCTAssertTrue(app.buttons["malen.form.kugel"].exists)
        let scopeLabel = app.staticTexts["malen.scope"].label
        XCTAssertTrue(
            scopeLabel.contains("alle Kopien") || scopeLabel.contains("all copies"),
            "Der volumebasierte Scope muss in der aktiven Sprache klar benannt sein: \(scopeLabel)"
        )

        app.buttons["malen.naht"].tap()
        XCTAssertTrue(app.buttons["malen.modus.pinsel"].exists)
        XCTAssertFalse(app.buttons["malen.modus.smart"].exists,
                       "Naht folgt Prusa und hat keinen Smart Fill")
        XCTAssertFalse(app.buttons["malen.modus.eimer"].exists)

        app.buttons["malen.mmu"].tap()
        XCTAssertTrue(app.buttons["malen.modus.smart"].exists)
        XCTAssertTrue(app.buttons["malen.modus.eimer"].exists)

        app.buttons["malen.aus"].tap()
        XCTAssertFalse(app.sliders["malen.radius"].exists,
                       "Das Werkzeug laesst sich nicht abstellen")
    }

    func testSmartBucketCursorUndLoeschenSindSichtbarUndWirksam() {
        app.buttons["malen.stuetzen"].tap()
        app.buttons["malen.modus.smart"].tap()
        XCTAssertTrue(app.sliders["malen.winkel"].waitForExistence(timeout: 5))
        tippeViewport()
        XCTAssertTrue(app.otherElements["viewport.malmarkierung"]
            .waitForExistence(timeout: 5),
            "Der echte Viewport-Pass muss als sichtbare Markierung gemeldet werden")
        XCTAssertTrue(warte(bis: {
            !self.app.staticTexts["malen.anzahl"].label.contains(" 0")
        }), "Smart Fill markiert nichts")
        app.buttons["malen.loeschen"].tap()
        XCTAssertTrue(warte(bis: {
            self.app.staticTexts["malen.anzahl"].label.contains("0")
        }), "Clear entfernt Smart Fill nicht")

        app.buttons["malen.mmu"].tap()
        app.buttons["malen.modus.eimer"].tap()
        tippeViewport()
        XCTAssertTrue(warte(bis: {
            !self.app.staticTexts["malen.anzahl"].label.contains(" 0")
        }), "Bucket Fill markiert nichts")
    }

    func testEinStrichMarkiertUndLoeschenRaeumtAuf() {
        app.buttons["malen.stuetzen"].tap()
        XCTAssertTrue(app.staticTexts["malen.anzahl"].waitForExistence(timeout: 5))
        let vorher = app.staticTexts["malen.anzahl"].label
        XCTAssertTrue(vorher.contains("0"), "Am Anfang darf nichts markiert sein: " + vorher)

        // In die Mitte der Flaeche tippen - dort liegt der Wuerfel.
        tippeViewport()

        let anzahl = app.staticTexts["malen.anzahl"]
        XCTAssertTrue(warte(bis: { !anzahl.label.contains(" 0") && anzahl.label != vorher }),
                      "Der Strich hat nichts markiert: " + anzahl.label)

        app.buttons["malen.loeschen"].tap()
        XCTAssertTrue(warte(bis: { anzahl.label.contains("0") }),
                      "Loeschen raeumt nicht auf: " + anzahl.label)
    }

    private func tippeViewport() {
        app.otherElements["viewport"].coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
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
