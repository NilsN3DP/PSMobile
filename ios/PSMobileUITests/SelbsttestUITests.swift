import XCTest

/// Prueft den Selbsttest - den Test, der sich selbst testet.
///
/// Er ist fuer das echte Geraet gebaut: der Simulator hat keine
/// Speichergrenze, keine Waermegrenze und eine andere GPU. Was hier
/// grün ist, sagt nur, dass der Ablauf steht - nicht, dass ein iPad ihn
/// packt. Genau deshalb muss der Ablauf aber stehen, bevor jemand mit
/// dem Geraet in der Hand darauf tippt: ein Selbsttest, der selbst
/// abstuerzt, kostet den Nutzer mehr Zeit als er spart.
final class SelbsttestUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // Der Lasttest laeuft hier mit sechs statt 25 Koerpern: ob ein
        // Geraet die volle Last packt, kann nur ein Geraet beantworten.
        // Hier geht es darum, dass der Ablauf ueberhaupt steht.
        app.sauberStarten(["-psm-preset-printer", "-psm-start-simple", "-psm-selbsttest-kurz"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testDerSelbsttestLaeuftDurchUndSchreibtEinenBericht() {
        app.buttons["simple.werkzeug.Settings"].tap()
        app.buttons["simple.appeinstellungen"].tap()

        let einstieg = app.buttons["appeinstellungen.selbsttest"]
        XCTAssertTrue(einstieg.waitForExistence(timeout: 10),
                      "Kein Einstieg in den Selbsttest")
        // Die Diagnose steht ganz unten; auf kurzen Fenstern liegt sie
        // unter dem Rand. Die Liste heisst seit dem 11.09.2026 auf
        // beiden Seiten "appeinstellungen.liste".
        if !einstieg.isHittable { app.otherElements["appeinstellungen.liste"].swipeUp() }
        einstieg.tap()

        let start = app.buttons["selbsttest.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 5), "Der Startknopf fehlt")
        start.tap()

        // Der Lasttest schneidet 25 Koerper - im Simulator dauert das.
        let ergebnis = app.staticTexts["selbsttest.ergebnis"]
        XCTAssertTrue(ergebnis.waitForExistence(timeout: 600),
                      "Der Selbsttest ist nicht fertig geworden")

        // Nicht nur fertig, sondern bestanden. Ein Durchlauf mit drei
        // Fehlschlaegen ist auch fertig.
        XCTAssertFalse((ergebnis.label).contains("fehlgeschlagen"),
                       "Der Selbsttest meldet Fehler: " + ergebnis.label)

        // Und der Bericht muss entstanden sein - ohne ihn kann niemand
        // etwas weitergeben.
        XCTAssertTrue(app.buttons["selbsttest.teilen"].exists,
                      "Es ist kein Bericht entstanden")
    }
}
