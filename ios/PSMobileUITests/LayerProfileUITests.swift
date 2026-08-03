import XCTest

/// Prueft die variablen Schichthoehen.
///
/// Der interessante Teil ist nicht das Eingeben, sondern die Grenze:
/// ein Profil mit zwei gleichen Z-Werten lehnt der Kern ab. Wenn die
/// Oberflaeche das nicht vorher sieht, tippt jemand auf Uebernehmen und
/// es passiert nichts - der schlechteste aller Zustaende.
final class LayerProfileUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        XCTAssertTrue(app.buttons["advanced.schichten"].waitForExistence(timeout: 10))
        app.buttons["advanced.schichten"].tap()
        XCTAssertTrue(app.otherElements["schichten"].waitForExistence(timeout: 10),
                      "Der Schichteditor ist nicht erschienen")
    }

    func testZweiStuetzstellenSindDerAnfang() {
        // Voreingestellt sind Boden und Modelloberkante - ein Profil mit
        // weniger gibt es nicht.
        XCTAssertTrue(app.textFields["schichten.z.0"].exists)
        XCTAssertTrue(app.textFields["schichten.z.1"].exists)
        XCTAssertFalse(app.textFields["schichten.z.2"].exists)

        // Und die letzte Stuetzstelle steht auf der Modellhoehe, nicht
        // auf einer erfundenen Zahl.
        XCTAssertEqual(app.textFields["schichten.z.1"].value as? String, "20.0")
    }

    func testEineStuetzstelleLandetImModell() {
        app.buttons["schichten.neu"].tap()
        let neue = app.textFields["schichten.z.1"]
        XCTAssertTrue(neue.waitForExistence(timeout: 5), "Keine neue Stuetzstelle")

        // Zwischen null und der Modellhoehe - wer oben anfuegt, bekommt
        // eine Stelle, die nie erreicht wird.
        let z = Double((neue.value as? String) ?? "") ?? -1
        XCTAssertTrue(z > 0 && z < 20, "Die Stuetzstelle liegt bei \(z)")
    }

    func testEinUngueltigesProfilLaesstSichNichtUebernehmen() {
        let uebernehmen = app.buttons["schichten.uebernehmen"]
        XCTAssertTrue(uebernehmen.isEnabled, "Das Standardprofil sollte gueltig sein")

        // Beide Stellen auf dieselbe Hoehe: der Kern verlangt streng
        // steigende Z-Werte.
        let zweite = app.textFields["schichten.z.1"]
        zweite.tap()
        zweite.press(forDuration: 1.0)
        if app.menuItems["Select All"].waitForExistence(timeout: 2) {
            app.menuItems["Select All"].tap()
        }
        zweite.typeText("0.0")

        XCTAssertTrue(warte(bis: { !uebernehmen.isEnabled }),
                      "Ein ungueltiges Profil liesse sich uebernehmen")
    }

    private func warte(bis bedingung: () -> Bool, timeout: TimeInterval = 10) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }
}
