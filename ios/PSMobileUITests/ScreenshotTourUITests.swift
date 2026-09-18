import XCTest

/// Eine Fernbedienung fuers iOS-Layout, Bildschirm fuer Bildschirm - das
/// Android-Gegenstueck zu "adb shell input tap" plus Screenshot, hier
/// ueber XCUITest, weil AppleScript/System Events auf dieser gemieteten
/// Mac-Instanz keine Accessibility-Rechte bekommt (kein aktives GUI-
/// Login), XCUITest aber ueber testmanagerd laeuft und das nicht
/// braucht.
///
/// Schreibt jeden Zwischenstand als PNG nach Documents/Tour/ - von dort
/// werden sie genauso abgeholt wie die Selbsttest-Berichte die ganze
/// Nacht schon (SSH + base64), keine neue Infrastruktur noetig.
final class ScreenshotTourUITests: XCTestCase {

    private var app: XCUIApplication!
    private var ordner: URL!

    override func setUp() {
        continueAfterFailure = true
        app = XCUIApplication()
        // -psm-reset-settings: der Simulator traegt Schalter aus frueheren
        // Testlaeufen mit sich. Ohne das Zuruecksetzen vergleicht der
        // Rundgang zwei Vorgeschichten statt zweier Oberflaechen.
        app.launchArguments = ["-psm-reset-settings", "-psm-preset-printer",
                               "-psm-start-simple", "-psm-load-cube"]
        // Wie unten in testTourAdvanced: ein Vorlauf, der die App im
        // Vordergrund abgeschossen hat, laesst den naechsten Start die
        // Absturzfrage zeigen - mitten im ersten Bild.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
        app.terminate()
        app.launch()

        let dokumente = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        ordner = dokumente.appendingPathComponent("Tour", isDirectory: true)
        // Nicht mehr leeren: setUp laeuft vor *jedem* Fall, und der
        // zweite (Expertenmodus) loeschte so die Bilder des ersten - oder
        // umgekehrt, je nach Reihenfolge. Am 11.09.2026 fehlten deshalb
        // 08-11 im Vergleichsordner. Gleiche Namen ueberschreiben sich
        // ohnehin.
        try? FileManager.default.createDirectory(at: ordner, withIntermediateDirectories: true)
    }

    private func foto(_ name: String) {
        let bild = XCUIScreen.main.screenshot().image
        guard let daten = bild.pngData() else { return }
        try? daten.write(to: ordner.appendingPathComponent("\(name).png"))
    }

    func testTourSimpleUndSettings() {
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
        foto("01-simple-workspace")

        app.buttons["simple.werkzeug.Settings"].tap()
        _ = app.buttons["simple.karte.SUPPORTS"].waitForExistence(timeout: 5)
        foto("02-simple-settings-panel")

        if app.buttons["simple.karte.SUPPORTS"].exists {
            app.buttons["simple.karte.SUPPORTS"].tap()
            _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
            foto("03-simple-supports")
            app.buttons["simple.werkzeug.Settings"].tap()
        }

        app.buttons["simple.werkzeug.Material"].tap()
        Thread.sleep(forTimeInterval: 0.6)
        foto("04-simple-material")

        app.buttons["simple.werkzeug.Printer"].tap()
        Thread.sleep(forTimeInterval: 0.6)
        foto("05-simple-printer")

        app.buttons["simple.werkzeug.Projects"].tap()
        Thread.sleep(forTimeInterval: 0.6)
        foto("06-simple-projects")

        // App-Einstellungen - Menue oben rechts, falls per Kennung erreichbar.
        if app.buttons["simple.werkzeug.Settings"].exists {
            app.buttons["simple.werkzeug.Settings"].tap()
        }
        if app.buttons["simple.appeinstellungen"].waitForExistence(timeout: 3) {
            app.buttons["simple.appeinstellungen"].tap()
            Thread.sleep(forTimeInterval: 0.6)
            foto("07-app-einstellungen")
        }
    }

    /// Derselbe Rundgang fuer den Expertenmodus.
    ///
    /// Der einfache Modus ist sieben Bildschirme; der Expertenmodus
    /// traegt den Rest der App. Ihn aussen vor zu lassen hiesse, ein
    /// Drittel zu vergleichen und "gleich" zu sagen.
    func testTourAdvanced() {
        // Erst in den Hintergrund, dann beenden. Ein terminate() aus dem
        // Vordergrund gilt der Absturzheuristik als unsauberes Ende, und
        // der naechste Start zeigt "The app didn't close normally last
        // time" - mitten im Bild.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
        app.terminate()
        app.sauberStarten(["-psm-reset-settings", "-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        Thread.sleep(forTimeInterval: 2)
        foto("08-advanced-workspace")

        // Filament und Drucker sind Reiter *innerhalb* der
        // Einstellungsflaeche. Die Zeilen advanced.filamentSettings und
        // advanced.printerSettings liegen dahinter und sind nicht mehr
        // erreichbar, sobald die Flaeche offen ist.
        for (kennung, name) in [("advanced.printSettings", "09-advanced-print-settings"),
                                ("reiter.filament",        "10-advanced-filament-settings"),
                                ("reiter.printer",         "11-advanced-printer-settings")] {
            guard app.buttons[kennung].waitForExistence(timeout: 8) else { continue }
            app.buttons[kennung].tap()
            Thread.sleep(forTimeInterval: 1.5)
            foto(name)
        }
    }

    /// Telefon im Hochformat - Bilder 12 und 13, Gegenstueck zum Block
    /// "Telefon-Hochformat" in tools/rundgang-android.sh. Laeuft nur auf
    /// einem iPhone-Simulator (tools/ios-bauen.sh mit PSM_TELEFON=1); auf
    /// dem iPad wuerde er iPad-Bilder unter Telefon-Namen ablegen.
    func testTourTelefon() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone,
                          "nur auf einem iPhone-Simulator")
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
        Thread.sleep(forTimeInterval: 2)
        foto("12-telefon-simple")
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
        app.terminate()
        app.sauberStarten(["-psm-reset-settings", "-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        Thread.sleep(forTimeInterval: 2)
        foto("13-telefon-advanced")
    }
}
