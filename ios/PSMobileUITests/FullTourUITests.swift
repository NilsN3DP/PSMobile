import XCTest

/// Vollstaendiger Rundgang durch so viele erreichbare Bildschirme wie
/// moeglich - Advanced Mode, Simple Mode, Drucker, Einrichtung. Jeder
/// Schritt schreibt sein eigenes PNG nach Documents/FullTour/, damit
/// ein Fehlschlag mitten drin nicht die vorherigen Bilder kostet.
final class FullTourUITests: XCTestCase {

    private var app: XCUIApplication!
    private var ordner: URL!
    private var zaehler = 0

    override func setUp() {
        continueAfterFailure = true
    }

    private func neueApp(_ argumente: [String]) {
        app = XCUIApplication()
        app.launchArguments = argumente
        app.launch()
    }

    private func foto(_ name: String) {
        zaehler += 1
        let nummeriert = String(format: "%02d-%@", zaehler, name)
        let bild = XCUIScreen.main.screenshot().image
        guard let daten = bild.pngData() else { return }
        try? daten.write(to: ordner.appendingPathComponent("\(nummeriert).png"))
    }

    private func tippeWennDa(_ el: XCUIElement, timeout: TimeInterval = 3) -> Bool {
        guard el.waitForExistence(timeout: timeout) else { return false }
        el.tap()
        return true
    }

    func testVollstaendigerRundgang() {
        let dokumente = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        ordner = dokumente.appendingPathComponent("FullTour", isDirectory: true)
        try? FileManager.default.removeItem(at: ordner)
        try? FileManager.default.createDirectory(at: ordner, withIntermediateDirectories: true)

        // --- Ersteinrichtung ---
        neueApp(["-psm-reset-setup"])
        if app.textFields.firstMatch.waitForExistence(timeout: 60) {
            foto("ersteinrichtung")
        }
        app.terminate()

        // --- Startbildschirm ---
        neueApp(["-psm-preset-printer"])
        if app.buttons["start.einrichtung"].waitForExistence(timeout: 30) ||
            app.staticTexts.firstMatch.waitForExistence(timeout: 5) {
            foto("start")
        }
        app.terminate()

        // --- Advanced Mode: voller Rundgang ---
        neueApp(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        foto("advanced-workspace")

        // Objektbaum / Inspektor-Reiter
        if tippeWennDa(app.buttons["inspektor.objekte"]) {
            foto("advanced-inspektor-objekte")
        }
        if tippeWennDa(app.buttons["inspektor.profile"]) {
            foto("advanced-inspektor-profile")
        }
        if tippeWennDa(app.buttons["inspektor.werkzeuge"]) {
            foto("advanced-inspektor-werkzeuge")
        }

        // Ein Objekt auswaehlen -> Objektleiste
        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        if objekt.waitForExistence(timeout: 5) {
            objekt.tap()
            foto("advanced-objekt-ausgewaehlt")
        }

        // Druckeinstellungen
        if tippeWennDa(app.buttons["advanced.printSettings"]) {
            foto("advanced-printsettings")
            app.buttons.matching(identifier: "einstellungen.zurueck").firstMatch.tap()
        }
        // Druckereinstellungen
        if tippeWennDa(app.buttons["advanced.printerSettings"]) {
            foto("advanced-printersettings")
            app.buttons.matching(identifier: "einstellungen.zurueck").firstMatch.tap()
        }
        // Filamenteinstellungen
        if tippeWennDa(app.buttons["advanced.filamentSettings"]) {
            foto("advanced-filamentsettings")
            app.buttons.matching(identifier: "einstellungen.zurueck").firstMatch.tap()
        }

        // Bettauswahl
        if tippeWennDa(app.buttons["bed.selector"]) {
            foto("advanced-bettauswahl")
            if app.buttons["bed.selector.close"].exists { app.buttons["bed.selector.close"].tap() }
        }

        // Anordnen
        if tippeWennDa(app.buttons["schiene.arrange"]) {
            foto("advanced-anordnen")
        }

        app.terminate()

        // --- Simple Mode: ColorMix, Modelle-Blatt ---
        neueApp(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube",
                 "-psm-test-eight-extruders"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        if tippeWennDa(app.buttons["blatt.mehr"]) {
            foto("simple-modelle-blatt")
        }
        if tippeWennDa(app.buttons["simple.colormix"]) {
            foto("simple-colormix")
            if app.buttons["colormix.done"].exists { app.buttons["colormix.done"].tap() }
        }
        app.terminate()

        // --- Drucker-Verwaltung (PrusaLink/OctoPrint-Hosts) ---
        neueApp(["-psm-preset-printer", "-psm-start-simple"])
        _ = app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60)
        if tippeWennDa(app.buttons["drucker.neu"], timeout: 5) {
            foto("drucker-neu")
        }
    }
}
