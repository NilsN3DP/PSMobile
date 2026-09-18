import XCTest

/// Prueft, dass die Oberflaeche auch auf schmalen Geraeten bedienbar
/// bleibt.
///
/// Die Skalierung rechnet aus der Fenstergroesse, aber eine Zahl allein
/// beweist nichts: ein Knopf kann rechnerisch passen und trotzdem unter
/// der Systemleiste liegen oder aus dem Bild ragen. Diese Tests laufen
/// auf demselben Bestand wie die anderen, nur auf einem iPhone.
///
/// Geprueft wird zweierlei, und beides ist genau das, was auf einem
/// grossen Bildschirm nie auffaellt:
///   - liegt das Element ganz im Fenster?
///   - laesst es sich treffen, oder verdeckt es etwas anderes?
final class ResponsiveLayoutUITests: XCTestCase {

    private var app: XCUIApplication!

    private func starte(_ argumente: [String]) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.sauberStarten(argumente)
    }

    /// Ganz im Fenster und treffbar.
    private func pruefe(_ element: XCUIElement, _ name: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 30), name + " fehlt")
        let fenster = app.windows.firstMatch.frame
        let rahmen = element.frame
        XCTAssertTrue(fenster.contains(rahmen),
                      "\(name) ragt aus dem Fenster: \(rahmen) in \(fenster)")
        XCTAssertTrue(element.isHittable, "\(name) ist nicht treffbar")
    }

    func testSimpleModeBleibtBedienbar() {
        starte(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))

        // Die Werkzeugleiste ist der engste Fall: sechs Knoepfe in einer
        // Zeile, und rechts muss G-Code noch hineinpassen.
        pruefe(app.buttons["simple.werkzeug.Projects"], "Projekte")
        pruefe(app.buttons["simple.werkzeug.Settings"], "Einstellen")
        pruefe(app.buttons["simple.werkzeug.G-Code"], "G-Code")

        // Das Modelle-Blatt darf das Bett nicht ganz verdecken und muss
        // unten im Bild bleiben.
        pruefe(app.buttons["blatt.mehr"], "Modelle-Blatt")

        // Und das Panel: auf einem schmalen Geraet ist es das erste, was
        // aus dem Bild laeuft.
        app.buttons["simple.werkzeug.Settings"].tap()
        pruefe(app.buttons["simple.karte.SUPPORTS"], "Stuetzen-Karte")
        pruefe(app.buttons["simple.appeinstellungen"], "App-Einstellungen")
    }

    /// Telefon quer (Fund 33/34 vom 16.09.2026): die Werkzeugspalte lag
    /// mittig auf dem Slice-Knopf und auf "Einklappen" des Modelle-Blatts,
    /// die Vorschau-Karte (nach Geraet statt Fenster) auf 393 pt Hoehe als
    /// 360-pt-Blatt. Auf einem iPad ist das Fenster auch quer hoch genug -
    /// dann prueft der Test nur, dass nichts uebereinanderliegt.
    func testSimpleModeQuerBleibtBedienbar() {
        starte(["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        sleep(1)
        // Ein Objekt waehlen: erst dann steht die Spalte mit Verschieben.
        let zeile = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).firstMatch
        XCTAssertTrue(zeile.waitForExistence(timeout: 30), "Modelle-Zeile fehlt")
        zeile.tap()
        pruefe(app.buttons["werkzeug.verschieben"], "Verschieben")
        pruefe(app.buttons["werkzeug.ansicht"], "Ansicht")
        pruefe(app.buttons["simple.werkzeug.G-Code"], "Slice")
        pruefe(app.buttons["blatt.klappen"], "Einklappen")
        getrennt(app.buttons["werkzeug.verschieben"], app.buttons["simple.werkzeug.G-Code"])
        getrennt(app.buttons["werkzeug.verschieben"], app.buttons["simple.fern.umschalten"])
        getrennt(app.buttons["werkzeug.ansicht"], app.buttons["blatt.klappen"])
        getrennt(app.buttons["werkzeug.verschieben"], app.buttons["blatt.klappen"])
    }

    /// Zwei Elemente duerfen sich nicht ueberlappen.
    private func getrennt(_ a: XCUIElement, _ b: XCUIElement) {
        guard a.exists, b.exists else { return }
        XCTAssertFalse(a.frame.intersects(b.frame),
                       "\(a.identifier) (\(a.frame)) liegt auf \(b.identifier) (\(b.frame))")
    }

    func testAdvancedModeBleibtBedienbar() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        pruefe(app.buttons["advanced.printSettings"], "Druckeinstellungen")
        pruefe(app.buttons["slicen"], "Slicen")

        // Auf schmalen Fenstern liegt der Inspektor ueber dem Bett. Er
        // muss trotzdem ganz sichtbar sein.
        // Die Objektliste liegt hinter ihrem Reiter, wie auf Android.
        let objekteReiter = app.buttons["inspektor.objekte"]
        if objekteReiter.waitForExistence(timeout: 10), objekteReiter.isEnabled {
            objekteReiter.tap()
        }
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()
        pruefe(app.buttons["advanced.gizmo.move"], "Gizmo Verschieben")
        pruefe(app.buttons["advanced.einpassen"], "Einpassen")
    }

    func testAdvancedObjektleisteBleibtVorDerSeitenleiste() {
        starte(["-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"])
        XCTAssertTrue(app.otherElements["arbeitsbereich"].waitForExistence(timeout: 60))

        let objekteReiter = app.buttons["inspektor.objekte"]
        XCTAssertTrue(objekteReiter.waitForExistence(timeout: 10),
                      "Der Bereich Objekte fehlt")
        objekteReiter.tap()
        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch.tap()

        let entfernen = app.buttons["objekt.entfernen"]
        XCTAssertTrue(entfernen.waitForExistence(timeout: 10),
                      "Die Objektleiste erscheint nicht")
        XCTAssertFalse(app.buttons["objekt.zurueck"].exists,
                       "Die Advanced-Objektleiste darf keine Simple-Zurueck-Aktion anbieten")

        // Auf breiten Geraeten bleibt die Leiste vollstaendig links von
        // der offenen Seitenleiste. Auf schmalen liegt die Seitenleiste
        // ueber dem Bett und begrenzt den Viewport deshalb nicht.
        //
        // Gemessen wird die Leiste, nicht ihr letzter Knopf: die Knoepfe
        // liegen in einer waagerechten ScrollView, und XCUITest meldet
        // auch fuer weggescrollte einen Rahmen. Bis 12.09.2026 stand hier
        // `entfernen.frame.maxX` - der letzte Knopf, bei 779 - und der
        // Fall war neun Laeufe lang rot, obwohl die Leiste bei 690 endete.
        // .any, nicht otherElements: die Leiste ist eine ScrollView, und
        // XCUITest fuehrt sie als solche.
        let leiste = app.descendants(matching: .any)["objektleiste"]
        XCTAssertTrue(leiste.waitForExistence(timeout: 5), "Die Objektleiste hat keine Kennung")
        let profil = app.buttons["inspektor.profile"]
        if profil.exists, profil.frame.minX > app.windows.firstMatch.frame.midX {
            XCTAssertLessThanOrEqual(leiste.frame.maxX, profil.frame.minX,
                                     "Die Objektleiste ragt in die Seitenleiste")
        }
    }

    func testDieErsteinrichtungPasstAufsSchmaleGeraet() {
        // Der erste Bildschirm ueberhaupt - wenn der nicht passt, kommt
        // niemand weiter.
        starte(["-psm-reset-setup"])
        let suche = app.textFields.firstMatch
        XCTAssertTrue(suche.waitForExistence(timeout: 90), "Die Ersteinrichtung fehlt")
        pruefe(suche, "Suchfeld der Ersteinrichtung")
    }
}
