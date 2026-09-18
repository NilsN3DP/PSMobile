import XCTest

/// Randfaelle: was passiert, wenn jemand Unsinn eingibt, zu frueh
/// tippt oder das Letzte seiner Art entfernen will. Kein Fall hier
/// prueft ein Feature - jeder prueft, dass ein Feature einen Fehler
/// des Nutzers aushaelt, ohne abzustuerzen oder Unsinn zu speichern.
/// Angelegt am 12.09.2026 fuer die Fehlersuche vor der ersten
/// oeffentlichen Version. Zwilling: RandfaelleUITest.kt.
final class RandfaelleUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func starte(_ argumente: [String], warteAuf kennung: String = "arbeitsbereich") {
        app.sauberStarten(argumente)
        // .any statt otherElements: eine Liste fuehrt XCUITest als Table
        // oder CollectionView, eine Marke als otherElement.
        XCTAssertTrue(app.descendants(matching: .any)[kennung].waitForExistence(timeout: 90),
                      "Der Bildschirm \(kennung) ist nicht erschienen")
    }

    /// Den Objektbereich aufklappen und auf die Zeilen warten. Ein Tipp
    /// direkt nach dem Start geht gelegentlich ins Leere (Lauf 12,
    /// testMehrereObjekte) - dann einmal nachfassen.
    private func objekteAufklappen(erwartet: Int) {
        let objekte = app.buttons["inspektor.objekte"]
        XCTAssertTrue(objekte.waitForExistence(timeout: 10))
        objekte.tap()
        let zeilen = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'"))
        if !warte(bis: { zeilen.count > 0 }, timeout: 3) { objekte.tap() }
        XCTAssertTrue(warte(bis: { zeilen.count == erwartet }, timeout: 30),
                      "Erwartet \(erwartet) Objekte, da sind \(zeilen.count)")
    }

    // MARK: - Einstellungsfelder

    func testBuchstabenInEinemZahlenfeldWerdenNichtUebernommen() {
        starte(["-psm-preset-printer", "-psm-start-advanced"])
        app.buttons["advanced.printSettings"].tap()
        let feld = app.textFields["feld.layer_height"]
        XCTAssertTrue(feld.waitForExistence(timeout: 10))
        let vorher = feld.value as? String ?? ""
        XCTAssertFalse(vorher.isEmpty)

        ersetzeText(in: feld, durch: "abc\n")

        app.buttons["reiter.printer"].tap()
        app.buttons["reiter.print"].tap()
        let wieder = app.textFields["feld.layer_height"]
        XCTAssertTrue(wieder.waitForExistence(timeout: 5))
        XCTAssertEqual(wieder.value as? String, vorher,
                       "Buchstaben landeten als Schichthoehe im Kern")
    }

    func testEinLeeresZahlenfeldFaelltAufDenAltenWertZurueck() {
        starte(["-psm-preset-printer", "-psm-start-advanced"])
        app.buttons["advanced.printSettings"].tap()
        let feld = app.textFields["feld.layer_height"]
        XCTAssertTrue(feld.waitForExistence(timeout: 10))
        let vorher = feld.value as? String ?? ""

        ersetzeText(in: feld, durch: "\n")

        app.buttons["reiter.printer"].tap()
        app.buttons["reiter.print"].tap()
        let wieder = app.textFields["feld.layer_height"]
        XCTAssertTrue(wieder.waitForExistence(timeout: 5))
        XCTAssertEqual(wieder.value as? String, vorher,
                       "Ein leeres Feld hat den Wert geloescht")
    }

    func testEineSchichthoeheVonNullWirdNichtUebernommen() {
        starte(["-psm-preset-printer", "-psm-start-advanced"])
        app.buttons["advanced.printSettings"].tap()
        let feld = app.textFields["feld.layer_height"]
        XCTAssertTrue(feld.waitForExistence(timeout: 10))

        ersetzeText(in: feld, durch: "0\n")

        app.buttons["reiter.printer"].tap()
        app.buttons["reiter.print"].tap()
        let wieder = app.textFields["feld.layer_height"]
        XCTAssertTrue(wieder.waitForExistence(timeout: 5))
        let nachher = Double(wieder.value as? String ?? "") ?? 0
        XCTAssertGreaterThan(nachher, 0, "Schichthoehe 0 wurde angenommen: \(wieder.value ?? "")")
    }

    // MARK: - Objekt

    func testSkalierenAufNullLaesstDasObjektNichtVerschwinden() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        erstesObjektWaehlen()
        let prozent = app.textFields["advanced.scale.prozent"]
        XCTAssertTrue(warteBisTreffbar(prozent), "Das Groessenfeld ist nicht erreichbar")

        ersetzeText(in: prozent, durch: "0\n")

        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.exists,
            "Das Objekt ist weg")
        let wert = Double(app.textFields["advanced.scale.prozent"].value as? String ?? "") ?? 0
        XCTAssertGreaterThan(wert, 0, "Skalierung 0 % wurde angenommen")
    }

    func testRueckgaengigOhneVerlaufTutNichts() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        let objekte = app.buttons["inspektor.objekte"]
        XCTAssertTrue(objekte.waitForExistence(timeout: 10))
        objekte.tap()
        // Mehrfach Zurueck, auch wenn nichts (mehr) da ist - das darf
        // weder abstuerzen noch den Wuerfel wegnehmen, der beim Start
        // geladen wurde.
        for _ in 0..<5 where app.buttons["advanced.zurueck"].isEnabled {
            app.buttons["advanced.zurueck"].tap()
        }
        for _ in 0..<3 where app.buttons["advanced.wiederholen"].isEnabled {
            app.buttons["advanced.wiederholen"].tap()
        }
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
            .waitForExistence(timeout: 5),
            "Der Startwuerfel ist nach Zurueck/Wiederholen weg")
    }

    // MARK: - Betten

    func testDasLetzteBettLaesstSichNichtEntfernen() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["bed.selector"].waitForExistence(timeout: 15)
                      || app.buttons["bed.selector"].waitForExistence(timeout: 1))
        XCTAssertFalse(app.bett("bed.remove.0").exists, "Das einzige Bett bietet Entfernen an")

        app.bett("bed.add").tap()
        XCTAssertTrue(app.bett("bed.card.1").waitForExistence(timeout: 10))
        XCTAssertTrue(app.bett("bed.remove.1").exists)
        app.bett("bed.remove.1").tap()
        XCTAssertTrue(warte(bis: { !self.app.bett("bed.card.1").exists }))
        XCTAssertFalse(app.bett("bed.remove.0").exists,
                       "Nach dem Entfernen bietet das letzte Bett Entfernen an")
        XCTAssertTrue(app.bett("bed.card.0").exists, "Das erste Bett ist weg")
    }

    // MARK: - Drucker

    func testEinDruckerOhneAdresseWirdNichtGesichert() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers"])
        app.buttons["drucker.oeffnen"].tap()
        XCTAssertTrue(app.otherElements["drucker"].waitForExistence(timeout: 10))
        app.buttons["drucker.neu"].tap()
        let name = app.textFields["drucker.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("Ohne Adresse")
        let sichern = app.buttons["drucker.sichern"]
        if sichern.isEnabled { sichern.tap() }

        let formularOffen = app.textFields["drucker.name"].exists
        let eintragDa = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'drucker.aktion.'")).firstMatch.exists
        XCTAssertTrue(formularOffen || !eintragDa, "Ein Drucker ohne Adresse wurde angelegt")
    }

    // MARK: - Schneiden

    func testAbbrechenUndSofortWiederSchneiden() {
        // Zwoelf Wuerfel, damit der Schnitt lange genug dauert, um ihn
        // wirklich abzubrechen - ein einzelner ist schneller als der Test.
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube", "-psm-test-many-cubes"])
        objekteAufklappen(erwartet: 12)

        app.buttons["slicen"].tap()
        let abbrechen = app.buttons["slice.abbrechen"]
        // Auf dem Simulator eines M5 sind zwoelf Wuerfel schneller
        // geschnitten, als XCUITest hinsieht. Erwischt der Test den Lauf
        // noch, prueft er das Abbrechen; sonst steht schon das Ergebnis
        // da, und der Fall prueft nur noch den zweiten Schnitt.
        if abbrechen.waitForExistence(timeout: 3) {
            abbrechen.tap()
            // Abbrechen heisst: der Schneiden-Knopf kommt zurueck - kein
            // haengender Fortschritt, kein halbes Ergebnis.
            XCTAssertTrue(warte(bis: { !abbrechen.exists }, timeout: 30))
            XCTAssertTrue(app.buttons["slicen"].exists, "Nach dem Abbrechen fehlt der Schneiden-Knopf")
            XCTAssertFalse(app.buttons["slice.sichern"].exists, "Nach dem Abbrechen steht ein Ergebnis da")
            app.buttons["slicen"].tap()
        } else {
            XCTAssertTrue(app.buttons["slice.sichern"].waitForExistence(timeout: 300))
            // Etwas aendern, damit der zweite Schnitt einer ist. Die
            // Objektliste ist schon offen - nur die Zeile waehlen, nicht
            // den Bereich erneut antippen (das klappte ihn wieder zu).
            let zeile = app.buttons.matching(
                NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
            XCTAssertTrue(warteBisTreffbar(zeile), "Keine erreichbare Objektzeile")
            zeile.tap()
            let rechts = app.buttons["advanced.drehen.rechts"]
            XCTAssertTrue(warteBisTreffbar(rechts))
            rechts.tap()
            XCTAssertTrue(app.buttons["slicen"].waitForExistence(timeout: 10))
            app.buttons["slicen"].tap()
        }
        XCTAssertTrue(app.buttons["slice.sichern"].waitForExistence(timeout: 300))
    }

    // MARK: - Runde 2 (12.09.2026)

    func testSonderzeichenInDerEinrichtungssucheStuerzenNichtAb() {
        starte(["-psm-reset-setup", "-psm-start-advanced"], warteAuf: "einrichtung.liste")
        let suche = app.textFields["einrichtung.suche"]
        XCTAssertTrue(suche.waitForExistence(timeout: 90))
        suche.tap()
        suche.typeText("(*+[\\ %")
        // Kein Treffer ist in Ordnung - ein Absturz oder eine
        // verschwundene Liste nicht.
        XCTAssertTrue(app.descendants(matching: .any)["einrichtung.liste"].exists,
                      "Die Einrichtung ist nach Sonderzeichen in der Suche weg")
        ersetzeText(in: suche, durch: "MK4")
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'variante.'")).firstMatch
            .waitForExistence(timeout: 10))
    }

    func testSonderzeichenInDerProfilsucheStuerzenNichtAb() {
        starte(["-psm-preset-printer", "-psm-start-advanced"])
        app.buttons["advanced.printSettings"].tap()
        let oeffnen = app.buttons["einstellungen.profilsuche.oeffnen"]
        XCTAssertTrue(oeffnen.waitForExistence(timeout: 10))
        oeffnen.tap()
        let suche = app.textFields["profilsuche.suche"]
        XCTAssertTrue(suche.waitForExistence(timeout: 10))
        suche.tap()
        suche.typeText("(*+[\\ %")
        XCTAssertTrue(app.textFields["profilsuche.suche"].exists,
                      "Die Profilsuche ist nach Sonderzeichen weg")
        // Gesucht wird in den Profilnamen ("0.20mm SPEED @MK4S 0.4").
        ersetzeText(in: suche, durch: "0.2")
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'profilsuche.eintrag.'")).firstMatch
            .waitForExistence(timeout: 10))
    }

    func testDoppeltesTippenAufSchneidenStartetEinenSchnitt() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube", "-psm-test-many-cubes"])
        objekteAufklappen(erwartet: 12)

        // Ein echter Doppeltipp, keine zwei Taps mit Denkpause: auf dem
        // schnellen Simulator stuende nach der Pause schon das Ergebnis
        // da, und der zweite Tipp traefe "Exportieren" - das prueft
        // dann etwas anderes. Der zweite Tipp landet oben, wo eben der
        // Knopf war: die Fortschrittszeile, nicht "Abbrechen".
        app.buttons["slicen"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15)).doubleTap()
        XCTAssertTrue(app.buttons["slice.sichern"].waitForExistence(timeout: 300),
                      "Nach dem Doppeltipp kommt kein Ergebnis")
        XCTAssertFalse(app.buttons["slice.abbrechen"].exists, "Nach dem Ergebnis laeuft noch ein Schnitt")
    }

    func testZehnBettenAnlegenUndWiederEntfernen() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.bett("bed.add").waitForExistence(timeout: 15))
        for _ in 0..<9 {
            tippeInDerLeiste("bed.add")
        }
        XCTAssertTrue(app.bett("bed.card.9").waitForExistence(timeout: 20))
        // Alle bis auf das erste wieder weg - von hinten, wie ein Mensch.
        for i in stride(from: 9, through: 1, by: -1) {
            tippeInDerLeiste("bed.remove.\(i)")
            XCTAssertTrue(warte(bis: { !self.app.bett("bed.card.\(i)").exists }))
        }
        XCTAssertTrue(app.bett("bed.card.0").exists)
        XCTAssertFalse(app.bett("bed.remove.0").exists)
        let objekte = app.buttons["inspektor.objekte"]
        if objekte.exists, objekte.isEnabled { objekte.tap() }
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
            .waitForExistence(timeout: 5),
            "Der Wuerfel ist nach dem Bettkarussell weg")
    }

    func testEinBettMitObjektLaesstSichNichtEntfernen() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.bett("bed.add").waitForExistence(timeout: 15))
        app.bett("bed.add").tap()
        XCTAssertTrue(app.bett("bed.card.1").waitForExistence(timeout: 10))
        // Bett 1 traegt den Wuerfel: Entfernen gibt es nur fuer leere
        // Betten - auf beiden Seiten dieselbe Regel (objectCount == 0).
        XCTAssertFalse(app.bett("bed.remove.0").exists, "Ein Bett mit Objekt bietet Entfernen an")
        XCTAssertTrue(app.bett("bed.remove.1").exists, "Das leere zweite Bett bietet kein Entfernen an")
    }

    func testEinUeberlangerDruckernameBleibtImFenster() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers"])
        app.buttons["drucker.oeffnen"].tap()
        XCTAssertTrue(app.otherElements["drucker"].waitForExistence(timeout: 10))
        app.buttons["drucker.neu"].tap()
        let name = app.textFields["drucker.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText(String(repeating: "Werkstatt ", count: 12).trimmingCharacters(in: .whitespaces))
        let adresse = app.textFields["drucker.adresse"]
        adresse.tap()
        adresse.typeText("192.168.1.70")
        app.buttons["drucker.sichern"].tap()

        let aktion = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'drucker.aktion.'")).firstMatch
        XCTAssertTrue(aktion.waitForExistence(timeout: 10))
        let fenster = app.windows.firstMatch.frame
        XCTAssertGreaterThan(aktion.frame.width, 0, "Der Aktionsknopf hat keine Breite")
        XCTAssertLessThanOrEqual(aktion.frame.maxX, fenster.maxX + 1,
                                 "Der Aktionsknopf liegt bei einem langen Namen ausserhalb")
    }

    /// Ein Knopf in der waagerecht blaetternden Bettleiste kann rechts
    /// ausserhalb liegen - dann erst hinblaettern.
    private func tippeInDerLeiste(_ kennung: String) {
        let knopf = app.buttons[kennung]
        XCTAssertTrue(knopf.waitForExistence(timeout: 10), "Nicht gefunden: \(kennung)")
        if !knopf.isHittable {
            // Die Leiste ist die ScrollView, in der "bed.add" steht.
            app.scrollViews.containing(.button, identifier: "bed.add").firstMatch.swipeLeft()
        }
        knopf.tap()
    }

    // MARK: - Runde 3 (12.09.2026)

    func testKopienWenigerBeiEinerKopieBleibtEine() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        erstesObjektWaehlen()
        let weniger = app.buttons["advanced.kopien.weniger"]
        XCTAssertTrue(warteBisTreffbar(weniger), "Kopien-Minus ist nicht erreichbar")
        for _ in 0..<3 { weniger.tap() }
        XCTAssertEqual(app.staticTexts["advanced.kopien.anzahl"].label, "1", "Weniger als eine Kopie")
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.exists,
            "Das Objekt ist weg")
    }

    func testEineDrehungMitBuchstabenBleibtBeiNull() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        erstesObjektWaehlen()
        let feld = app.textFields["advanced.rotate.Z"]
        XCTAssertTrue(warteBisTreffbar(feld), "Das Drehfeld ist nicht erreichbar")
        ersetzeText(in: feld, durch: "abc\n")
        XCTAssertEqual(app.textFields["advanced.rotate.Z"].value as? String, "0",
                       "Buchstaben blieben im Drehfeld stehen")
    }

    // Kein Zwilling zu "einVerschwundenesProjektUnterZuletztStuerztNichtAb":
    // XCUITest laeuft als eigener Prozess und kommt an Documents der App
    // nicht heran, um die Datei hinter ihrem Ruecken zu loeschen. Der
    // Weg im Code ist auf beiden Seiten derselbe (loadProject faengt den
    // Fehler und meldet ihn als Progress.failed).

    func testDerArbeitsstandUeberlebtEinenNeustart() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        erstesObjektWaehlen()
        let mehr = app.buttons["advanced.kopien.mehr"]
        XCTAssertTrue(warteBisTreffbar(mehr))
        mehr.tap()
        // Eine zweite Kopie ist eine Instanz desselben Objekts - eine
        // Zeile in der Liste, "2" im Kopienzaehler.
        let anzahl = app.staticTexts["advanced.kopien.anzahl"]
        XCTAssertTrue(warte(bis: { anzahl.label == "2" }, timeout: 15))

        // Neu starten, ohne einen Schalter, der einen frischen Zustand
        // verlangt: die zwei Wuerfel muessen aus dem gesicherten
        // Arbeitsstand zurueckkommen. sauberStarten geht ueber Home, also
        // durch scenePhase == .background - dort wird gesichert.
        starte(["-psm-start-advanced"])
        erstesObjektWaehlen()
        XCTAssertTrue(warteBisTreffbar(app.staticTexts["advanced.kopien.anzahl"]))
        XCTAssertTrue(warte(bis: { self.app.staticTexts["advanced.kopien.anzahl"].label == "2" }, timeout: 15),
                      "Die zweite Kopie hat den Neustart nicht ueberlebt")
    }

    func testEinProjektMitZweiKopienKommtMitZweiKopienZurueck() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        erstesObjektWaehlen()
        let mehr = app.buttons["advanced.kopien.mehr"]
        XCTAssertTrue(warteBisTreffbar(mehr))
        mehr.tap()
        let anzahl = app.staticTexts["advanced.kopien.anzahl"]
        XCTAssertTrue(warte(bis: { anzahl.label == "2" }, timeout: 15))

        app.buttons["projekt.sichern"].tap()
        // Die Knoepfe eines .alert tragen ihre Kennung nicht verlaesslich;
        // zur Not ueber die Beschriftung.
        // Nur im Alert suchen: die Werkzeugleiste hat ebenfalls einen
        // Knopf "Save", und der oeffnete den Dialog nur noch einmal.
        let ok = app.alerts.buttons.matching(NSPredicate(
            format: "identifier == 'projekt.sichern.ok' OR label == 'Save' OR label == 'Sichern'")).firstMatch
        XCTAssertTrue(ok.waitForExistence(timeout: 5), "Der Sichern-Dialog fehlt")
        ok.tap()
        XCTAssertTrue(warte(bis: { self.app.alerts.count == 0 }, timeout: 5), "Der Sichern-Dialog bleibt offen")

        // Frisch starten und das Projekt wieder oeffnen: dieselben zwei
        // Kopien auf demselben Bett - keine zweite Platte, kein
        // verlorenes Exemplar.
        starte(["-psm-preset-printer", "-psm-start-advanced"])
        app.buttons["kopf.start"].tap()
        XCTAssertFalse(app.buttons["verlassen.verwerfen"].exists,
                       "Leerer Arbeitsbereich fragt nach ungesicherten Aenderungen")
        XCTAssertTrue(app.otherElements["start"].waitForExistence(timeout: 10))
        app.buttons["start.zuletzt.0"].tap()
        let advanced = app.buttons.matching(NSPredicate(
            format: "identifier == 'projekt.oeffnen.advanced' OR label BEGINSWITH 'Open in Advanced' OR label BEGINSWITH 'In Advanced'")).firstMatch
        XCTAssertTrue(advanced.waitForExistence(timeout: 10))
        advanced.tap()
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 30))
        if app.buttons["projekt.hinweis.schliessen"].waitForExistence(timeout: 2) {
            app.buttons["projekt.hinweis.schliessen"].tap()
        }
        erstesObjektWaehlen()
        XCTAssertTrue(warteBisTreffbar(app.staticTexts["advanced.kopien.anzahl"]))
        XCTAssertTrue(warte(bis: { self.app.staticTexts["advanced.kopien.anzahl"].label == "2" }, timeout: 15),
                      "Die zweite Kopie hat die 3MF-Rundreise nicht ueberlebt")
        XCTAssertFalse(app.bett("bed.card.1").exists, "Aus einem Projekt mit einem Bett wurden zwei")
    }

    // MARK: - Helfer

    private func erstesObjektWaehlen() {
        let objekte = app.buttons["inspektor.objekte"]
        XCTAssertTrue(warteBisTreffbar(objekte), "Der Bereich Objekte ist nicht erreichbar")
        if objekte.isEnabled { objekte.tap() }
        let objekt = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        // Ein Tipp direkt nach dem Start geht gelegentlich ins Leere -
        // dann einmal nachfassen (siehe objekteAufklappen).
        if !warte(bis: { objekt.exists }, timeout: 3), objekte.isEnabled { objekte.tap() }
        XCTAssertTrue(warteBisTreffbar(objekt), "Keine erreichbare Objektzeile")
        objekt.tap()
    }

    private func ersetzeText(in feld: XCUIElement, durch text: String) {
        feld.ersetzeText(text)   // siehe Eingabehilfe.swift
    }

    private func warteBisTreffbar(_ element: XCUIElement, timeout: TimeInterval = 10) -> Bool {
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

    private func warte(bis bedingung: () -> Bool, timeout: TimeInterval = 10) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }

    // MARK: - STEP

    /// Beweist, dass der Kern STEP lesen kann - nicht nur, dass die App es
    /// behauptet. iOS setzte das Flag bis zum 13.09.2026 nie, obwohl der
    /// Geraete-Kern OCCT enthielt; Android behauptete es ohne OCCT.
    /// Dieselbe Probe (screw.step aus den OCCT-Beispielen) laedt der
    /// Android-Zwilling aus androidTest/assets.
    func testStepDateiLaedtWennDerKernEsKann() throws {
        let pfad = try XCTUnwrap(Bundle(for: Self.self).path(forResource: "screw", ofType: "step"),
                                 "screw.step fehlt im Testbundle")
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-step", "-psm-step-pfad=\(pfad)"])
        objekteAufklappen(erwartet: 1)
    }

    /// Ein getippter Drehwinkel muss stehen bleiben - Zwilling von
    /// getippterDrehwinkelBleibtStehen (Android, S23 FE 14.09.2026).
    func testGetippterDrehwinkelBleibtStehen() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        objekteAufklappen(erwartet: 1)
        let zeile = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        XCTAssertTrue(warteBisTreffbar(zeile))
        zeile.tap()
        let feld = app.textFields["advanced.rotate.Y"]
        XCTAssertTrue(warteBisTreffbar(feld), "Drehwinkel-Feld Y fehlt")
        feld.tap()
        feld.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 4))
        feld.typeText("45\n")
        // Fokus woandershin - genau da ging der Wert frueher verloren.
        let anderes = app.textFields["advanced.scale.mm"]
        XCTAssertTrue(warteBisTreffbar(anderes))
        anderes.tap()
        app.textFields["advanced.rotate.X"].tap()
        Thread.sleep(forTimeInterval: 1)
        let text = (feld.value as? String) ?? ""
        XCTAssertTrue(abs((Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0) - 45) < 0.6,
                      "Drehwinkel 45 ging verloren: \(text)")
    }
}
