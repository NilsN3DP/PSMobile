import XCTest

/// Prueft den G-Code-Vorschau-Weg im Advanced Mode nach dem Umbau: kein
/// schwebendes "Final G-code"-Blatt mehr, sondern Randregler direkt am
/// Viewport plus Statistik/Legende im rechten Menueband. Und nach dem
/// Schneiden kein Popup mehr, das erst weggetippt werden muss - "Slice
/// now" wird direkt durch Export/Senden ersetzt.
final class GcodePreviewUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten([ "-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube" ])
    }

    private func schuss(_ name: String) {
        let bild = XCUIScreen.main.screenshot()
        let att = XCTAttachment(screenshot: bild)
        att.name = name
        att.lifetime = .keepAlways
        add(att)
    }

    /// Schneidet und wartet auf den Export-Knopf - kein Blatt mehr, das
    /// dafuer erst weggetippt werden muesste.
    private func schneidenUndWarten() {
        let slicenButton = app.buttons["slicen"]
        XCTAssertTrue(slicenButton.waitForExistence(timeout: 5))
        slicenButton.tap()
        let export = app.buttons["slice.sichern"]
        XCTAssertTrue(export.waitForExistence(timeout: 180),
                      "Nach dem Schneiden erscheint kein Export-Knopf")
    }

    func testVorschauZeigtRandreglerStattSchwebenderKarte() {
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 180))

        schneidenUndWarten()

        app.buttons["werkzeug.vorschau"].tap()
        XCTAssertTrue(app.otherElements["vorschau.panel"].waitForExistence(timeout: 10),
                      "Die Statistik/Legende im Menueband erscheint nicht nach dem Umschalten")
        Thread.sleep(forTimeInterval: 1.5)
        schuss("01-vorschau-menueband")

        // Kein schwebendes Blatt mehr - die alten Marken der Karte
        // duerfen nicht mehr existieren.
        XCTAssertFalse(app.otherElements["vorschau.seitenkarte"].exists,
                       "Die alte schwebende Vorschau-Karte ist noch da")
        XCTAssertFalse(app.otherElements["vorschau.bottomsheet"].exists,
                       "Das alte Vorschau-Bottomsheet ist noch da")

        // Die beiden Randregler: senkrecht links fuer den Schichtbereich,
        // waagerecht unten fuer den Werkzeugweg innerhalb der Schicht.
        XCTAssertTrue(app.otherElements["vorschau.schicht.unten"].waitForExistence(timeout: 5),
                      "Der senkrechte Schichtregler fehlt am Viewport-Rand")
        XCTAssertTrue(app.otherElements["vorschau.schicht.oben"].exists)

        schuss("02-randregler-sichtbar")
    }
}
