import XCTest

/// Prueft die Gesten im 3D-Arbeitsbereich.
///
/// Bis hierhin waren sie geschrieben, aber unbelegt: ein Screenshot
/// zeigt ein Bett, nicht ob sich die Kamera drehen laesst. Genau hier
/// steckt der Unterschied zwischen "uebersetzt" und "funktioniert".
///
/// Der Vergleich laeuft ueber Bilder: dieselbe Ansicht vor und nach der
/// Geste. Aendert sich nichts, hat die Geste nichts bewirkt.
final class ViewportUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // Mit eingerichtetem Drucker starten - die Ersteinrichtung ist
        // hier nicht das Thema.
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced"]
        app.launch()
    }

    private func bildDaten() -> Data {
        XCUIScreen.main.screenshot().pngRepresentation
    }

    func testDrehenAendertDieAnsicht() {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60),
                      "Der Arbeitsbereich ist nicht erschienen")
        // Das Bett wird beim ersten Bild noch aufgebaut.
        sleep(3)

        let vorher = bildDaten()

        // Ein Finger quer ueber die Mitte: das dreht die Kamera.
        let mitte = flaeche.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45))
        let ziel = flaeche.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.45))
        mitte.press(forDuration: 0.1, thenDragTo: ziel)
        sleep(1)

        XCTAssertNotEqual(vorher, bildDaten(),
                          "Nach dem Drehen sieht die Ansicht unveraendert aus")
    }

    func testZweiFingerZoomen() {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60))
        sleep(3)

        let vorher = bildDaten()
        flaeche.pinch(withScale: 2.0, velocity: 1.0)
        sleep(1)

        XCTAssertNotEqual(vorher, bildDaten(),
                          "Nach dem Spreizen sieht die Ansicht unveraendert aus")
    }
}
