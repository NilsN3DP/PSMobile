import XCTest

/// Prueft den Weg zum Drucker.
///
/// Was sich ohne echten Drucker pruefen laesst, ist genau das, was man
/// sonst erst im Fehlerfall merkt: dass ein Passwort nicht in den
/// normalen Einstellungen landet, und dass eine Klartextadresse ohne
/// ausdrueckliche Freigabe gar nicht erst gesendet wird.
final class PrintersUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced",
                               "-psm-reset-printers"]
        app.launch()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        app.buttons["drucker.oeffnen"].tap()
        XCTAssertTrue(app.otherElements["drucker"].waitForExistence(timeout: 10),
                      "Der Druckerschirm ist nicht erschienen")
    }

    private func lege(adresse: String, passwort: String = "geheim") {
        app.buttons["drucker.neu"].tap()
        let name = app.textFields["drucker.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5), "Das Formular fehlt")
        name.tap()
        name.typeText("Werkstatt")
        let feld = app.textFields["drucker.adresse"]
        feld.tap()
        feld.typeText(adresse)
        let pw = app.secureTextFields["drucker.passwort"]
        pw.tap()
        pw.typeText(passwort)
        app.buttons["drucker.sichern"].tap()
    }

    func testEinDruckerLaesstSichAnlegenUndErscheintInDerListe() {
        lege(adresse: "192.168.1.50")

        XCTAssertTrue(app.staticTexts["Werkstatt"].waitForExistence(timeout: 5),
                      "Der neue Drucker steht nicht in der Liste")
        // Ohne Schema gilt HTTPS - das entscheidet die gemeinsame Regel,
        // nicht die Oberflaeche.
        XCTAssertTrue(app.staticTexts["https://192.168.1.50"].exists,
                      "Die Adresse wurde nicht als HTTPS ergaenzt")
    }

    func testKlartextWirdOhneFreigabeNichtGesendet() {
        lege(adresse: "http://192.168.1.50")

        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'drucker.aktion.'")).firstMatch.tap()

        // Es darf gar kein Versuch stattfinden: die Regel sagt vorher,
        // dass die Zugangsdaten sonst im Klartext ueber das Netz gingen.
        let hinweis = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] 'HTTP'")).firstMatch
        XCTAssertTrue(hinweis.waitForExistence(timeout: 10),
                      "Klartext wird ohne Freigabe stillschweigend versucht")
    }
}
