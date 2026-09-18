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
        app.sauberStarten(["-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers"])
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

    func testDasPasswortUeberlebtImSchluesselbund() {
        // Der Schluesselbund laesst sich nur aus der App heraus pruefen:
        // ein Testbuendel ohne Host bekommt bei jedem Aufruf -34018,
        // errSecMissingEntitlement. Mit Host wiederum liegt das
        // Kotlin-Framework doppelt im Prozess. Also hier, in der
        // Bedienung - wo es ohnehin darauf ankommt.
        lege(adresse: "192.168.1.60", passwort: "streng-geheim-42")
        XCTAssertTrue(app.staticTexts["Werkstatt"].waitForExistence(timeout: 5))

        // Ueber die Kennung, nicht ueber die Beschriftung: die App
        // startet auf Englisch, und ein Test, der an einer Uebersetzung
        // haengt, faellt beim naechsten Sprachwechsel um.
        let bearbeiten = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'drucker.bearbeiten.'")).firstMatch
        XCTAssertTrue(bearbeiten.waitForExistence(timeout: 5),
                      "Der Bearbeiten-Knopf fehlt")
        // Erst wenn die Liste wieder oben liegt. Solange das Blatt zum
        // Sichern noch nach unten faehrt, geht ein Tippen mal an das
        // Ziel und mal daneben - genau daran ist dieser Test zweimal
        // gescheitert, ohne dass sich etwas am Code geaendert haette.
        //
        // Gefragt wird, ob das Formular verschwunden ist, und nicht, ob
        // irgendetwas anfassbar ist: nach dem Tippen ins Passwortfeld
        // steht die Tastatur noch und deckt den halben Schirm ab -
        // anfassbar ist dann fast nichts, obwohl alles in Ordnung ist.
        expectation(for: NSPredicate(format: "exists == false"),
                    evaluatedWith: app.textFields["drucker.name"])
        waitForExpectations(timeout: 10)
        bearbeiten.tap()

        let pw = app.secureTextFields["drucker.passwort"]
        XCTAssertTrue(pw.waitForExistence(timeout: 5), "Das Formular fehlt")
        // SecureField gibt seinen Inhalt nicht heraus - es meldet nur,
        // dass etwas darin steht. Genau das reicht: leer hiesse, der
        // Schluesselbund hat nichts zurueckgegeben.
        XCTAssertEqual(pw.value as? String, "••••••••••••••••",
                       "Das Passwort kam nicht aus dem Schluesselbund zurueck")
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
