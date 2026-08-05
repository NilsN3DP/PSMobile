import XCTest

/// Bildschirmfotos der wichtigsten Ansichten.
///
/// Kein Prüftest: er behauptet nichts und schlägt nur fehl, wenn eine
/// Ansicht gar nicht erscheint. Sein Zweck ist, den Stand zeigen zu
/// können, ohne ein Gerät in der Hand zu haben — und zwar immer
/// denselben Stand, aus demselben Ablauf.
///
/// Die Bilder landen unter /tmp/psm-*.png. Ein Anhang an das Testergebnis
/// wäre der übliche Weg, aber der versteckt sie im .xcresult; von dort
/// wieder herauszukommen ist mehr Arbeit als eine Datei zu lesen.
final class BildschirmfotoTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func halte(_ name: String) {
        let daten = XCUIScreen.main.screenshot().pngRepresentation
        try? daten.write(to: URL(fileURLWithPath: "/tmp/psm-\(name).png"))
    }

    func testAlleAnsichten() {
        // --- Startseite -------------------------------------------------
        app.launchArguments = ["-psm-preset-printer"]
        app.launch()
        XCTAssertTrue(app.otherElements["start"].waitForExistence(timeout: 60),
                      "Die Startseite fehlt")
        sleep(1)
        halte("start")

        // --- Simple Mode mit einem Objekt -------------------------------
        app.terminate()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60),
                      "Der Simple Mode fehlt")
        sleep(3)
        halte("simple")

        // Das Material-Blatt — Suchfeld, Typ-Knöpfe, Farbpunkte, Karten.
        if app.buttons["werkzeug.1"].exists { app.buttons["werkzeug.1"].tap() }
        let materialKnopf = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'werkzeug.'")).element(boundBy: 2)
        if materialKnopf.exists {
            materialKnopf.tap()
            sleep(2)
            halte("material")
        }

        // --- Advanced ---------------------------------------------------
        app.terminate()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60),
                      "Der Arbeitsbereich fehlt")
        sleep(3)
        halte("advanced")

        // Objekt auswählen: dann zeigt der Inspektor, was er kann.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        if objekt.waitForExistence(timeout: 5) {
            objekt.tap()
            sleep(2)
            halte("advanced-objekt")
        }

        // --- Einstellungen mit der Einstufung ---------------------------
        if app.buttons["advanced.printSettings"].exists {
            app.buttons["advanced.printSettings"].tap()
            sleep(3)
            halte("einstellungen")
        }
    }

    /// Dieselben Ansichten quer.
    ///
    /// Ein Tablet hält man quer, und quer ist das Seitenverhältnis ein
    /// anderes: die Seitenleiste nimmt weniger Anteil, das Bett bekommt
    /// mehr Breite als Höhe. Was hochkant passt, muss deshalb nicht quer
    /// passen.
    ///
    /// Je ein Test je Bild: die App im Querformat zu beenden und neu zu
    /// starten bringt den Testläufer aus dem Tritt — er meldet "Busy"
    /// und fällt aus, und in einer Kette fehlen dann auch die Bilder,
    /// die schon gegangen wären.
    private func quer(_ argumente: [String], marke: String, name: String) {
        app.launchArguments = argumente
        app.launch()
        XCTAssertTrue(app.otherElements[marke].waitForExistence(timeout: 60),
                      "Die Ansicht fehlt: " + marke)
        XCUIDevice.shared.orientation = .landscapeLeft
        // Der Viewport bekommt die neue Größe erst nach der Drehung, und
        // die Kamera passt danach neu ein.
        sleep(4)
        halte(name)
        XCUIDevice.shared.orientation = .portrait
        sleep(1)
    }

    func testQuerAdvanced() {
        quer(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"],
             marke: "arbeitsbereich", name: "quer-advanced")
    }

    func testQuerSimple() {
        quer(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"],
             marke: "simple.arbeitsbereich", name: "quer-simple")
    }

    func testQuerAdvancedMitObjekt() {
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        if objekt.waitForExistence(timeout: 10) { objekt.tap() }
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(4)
        halte("quer-advanced-objekt")
        XCUIDevice.shared.orientation = .portrait
        sleep(1)
    }
}
