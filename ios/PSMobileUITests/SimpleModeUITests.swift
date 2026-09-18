import XCTest

/// Prueft den Simple Mode - den Weg, den die meisten nehmen werden.
///
/// Interessant ist hier nicht das Aussehen, sondern ob die Bedienung
/// wirklich beim Kern ankommt: eine Stuetzenwahl muss vier Parameter
/// setzen, und die Karte darueber muss danach den neuen Zustand zeigen.
/// Ein Panel, das sich oeffnet, aber nichts bewirkt, sieht auf einem
/// Bildschirmfoto genauso richtig aus wie eines, das funktioniert.
final class SimpleModeUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten(["-psm-preset-printer", "-psm-start-simple"])

        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60),
                      "Der Simple Mode ist nicht erschienen")
    }

    func testWerkzeugleisteOeffnetUndSchliesstEinPanel() {
        let werkzeug = app.buttons["simple.werkzeug.Settings"]
        XCTAssertTrue(werkzeug.waitForExistence(timeout: 10),
                      "Die Werkzeugleiste fehlt")

        werkzeug.tap()
        XCTAssertTrue(app.buttons["simple.karte.SUPPORTS"].waitForExistence(timeout: 5),
                      "Das Einstellen-Panel hat keine Karten")

        // Ein zweites Tippen auf dasselbe Werkzeug schliesst wieder - wie
        // auf Android.
        werkzeug.tap()
        XCTAssertFalse(app.buttons["simple.karte.SUPPORTS"].exists,
                       "Das Panel bleibt beim zweiten Tippen offen")
    }

    func testZurueckFuehrtVonDerUnterseiteInsEinstellenPanel() {
        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.karte.ADHESION"].tap()

        XCTAssertTrue(app.buttons["simple.haftung.automatisch"].waitForExistence(timeout: 5),
                      "Das Haftungspanel ist nicht erschienen")

        // backDestination() sagt: von einer Unterseite geht es zurueck
        // ins Einstellen-Panel, nicht in den Arbeitsbereich.
        app.buttons["simple.zurueck"].tap()
        XCTAssertTrue(app.buttons["simple.karte.ADHESION"].waitForExistence(timeout: 5),
                      "Zurueck fuehrt nicht ins Einstellen-Panel")
    }

    func testStuetzenwahlKommtImKernAn() {
        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.karte.SUPPORTS"].tap()

        let organisch = app.buttons["simple.stuetzen.ORGANIC_EVERYWHERE"]
        XCTAssertTrue(organisch.waitForExistence(timeout: 5),
                      "Die Stuetzenwahl fehlt")
        organisch.tap()

        // Die Karte im Einstellen-Panel liest ihren Text aus der
        // Konfiguration. Steht dort "Organic"/"Organisch" (seit dem 16.09.2026
        // lesbar statt "organic"), ist der Wert wirklich im Kern gelandet
        // und nicht nur im Bildschirmzustand.
        app.buttons["simple.zurueck"].tap()
        let karte = app.buttons["simple.karte.SUPPORTS"]
        XCTAssertTrue(karte.waitForExistence(timeout: 5))
        XCTAssertTrue(karte.staticTexts.allElementsBoundByIndex.contains {
            $0.label.contains("Organic") || $0.label.contains("Organisch")
        }, "Die Karte zeigt die neue Stuetzenart nicht - der Wert kam nicht im Kern an")
    }

    func testAppEinstellungenSindAusDemSimpleModeErreichbar() {
        app.buttons["simple.werkzeug.Settings"].tap()

        let knopf = app.buttons["simple.appeinstellungen"]
        XCTAssertTrue(knopf.waitForExistence(timeout: 5),
                      "Die App-Einstellungen fehlen im Einstellen-Panel")
        knopf.tap()

        XCTAssertTrue(app.buttons["appeinstellungen.startmodus"].waitForExistence(timeout: 10),
                      "Die App-Einstellungen zeigen keinen Startmodus")

        app.buttons["appeinstellungen.zurueck"].tap()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 10),
                      "Zurueck fuehrt nicht in den Simple Mode")
    }

    func testWechselInDenAdvancedMode() {
        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.advanced"].tap()

        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 15),
                      "Der Advanced Mode ist nicht erschienen")

        // Und zurueck: die beiden Modi muessen in beide Richtungen
        // erreichbar sein, sonst sitzt man im falschen fest.
        app.buttons["simple.oeffnen"].tap()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 10),
                      "Aus dem Advanced Mode fuehrt kein Weg zurueck")
    }
}
