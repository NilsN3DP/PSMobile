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
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()

        let dokumente = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        ordner = dokumente.appendingPathComponent("Tour", isDirectory: true)
        try? FileManager.default.removeItem(at: ordner)
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
}
