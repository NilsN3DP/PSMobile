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
        app.sauberStarten(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))
        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(warteBisTreffbar(objekteReiter),
                      "Der Objektbereich ist nicht erreichbar")
        if objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        XCTAssertTrue(warteBisTreffbar(objekt), "Die Objektzeile ist nicht erreichbar")
        objekt.tap()
        let schichten = app.buttons["advanced.schichten"]
        XCTAssertTrue(warteBisTreffbar(schichten),
                      "Der Schichthöhen-Einstieg ist nicht erreichbar")
        schichten.tap()
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
        zweite.ersetzeText("0.0")

        XCTAssertTrue(warte(bis: { !uebernehmen.isEnabled }),
                      "Ein ungueltiges Profil liesse sich uebernehmen")
    }

    func testAnwendenMarkiertDasModellUndZuruecksetzenEntferntDieMarkierung() {
        app.buttons["schichten.neu"].tap()

        let mittlereHoehe = app.textFields["schichten.h.1"]
        XCTAssertTrue(mittlereHoehe.waitForExistence(timeout: 5),
                      "Die neue Stützstelle ist nicht editierbar")
        ersetzeText(in: mittlereHoehe, durch: "0.10")

        app.buttons["schichten.uebernehmen"].tap()

        let markierung = app.otherElements["viewport.schichthoehen"]
        XCTAssertTrue(markierung.waitForExistence(timeout: 10),
                      "Das angewendete Profil wird am Modell nicht sichtbar erklärt")
        XCTAssertTrue((markierung.value as? String)?.contains("0.10") == true,
                      "Die Viewport-Markierung beschreibt die feine Schichthöhe nicht")

        let schichten = app.buttons["advanced.schichten"]
        XCTAssertTrue(warteBisTreffbar(schichten),
                      "Der Schichthöhen-Editor lässt sich nicht erneut öffnen")
        schichten.tap()
        XCTAssertTrue(app.buttons["schichten.zuruecksetzen"].waitForExistence(timeout: 5))
        app.buttons["schichten.zuruecksetzen"].tap()
        // Zurücksetzen löscht sofort. „Übernehmen“ würde die danach
        // angezeigten Standardwerte bewusst wieder als neues Profil
        // speichern; zum Prüfen des Löschpfads schließen wir daher ab.
        app.buttons["schichten.abbrechen"].tap()

        XCTAssertTrue(warte(bis: { !markierung.exists }),
                      "Ein gelöschtes Profil lässt seine Viewport-Markierung zurück")
    }

    private func ersetzeText(in feld: XCUIElement, durch text: String) {
        feld.ersetzeText(text)   // siehe Eingabehilfe.swift
    }

    private func warte(bis bedingung: () -> Bool, timeout: TimeInterval = 10) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }

    /// Wie in AdvancedWorkflowUITests: noch nicht treffbar heisst meist,
    /// der Bereich liegt unter dem Fensterrand. Die Seitenleiste
    /// scrollt - also hinblaettern, statt zu melden, die App koenne es
    /// nicht.
    private func warteBisTreffbar(_ element: XCUIElement,
                                  timeout: TimeInterval = 10) -> Bool {
        if warte(bis: { element.exists && element.isHittable }, timeout: timeout) { return true }
        let leiste = app.otherElements["seitenleiste"]
        guard leiste.exists else { return false }
        for _ in 0..<4 {
            leiste.swipeUp()
            if warte(bis: { element.exists && element.isHittable }, timeout: 2) { return true }
        }
        for _ in 0..<6 {
            leiste.swipeDown()
            if warte(bis: { element.exists && element.isHittable }, timeout: 2) { return true }
        }
        return false
    }
}
