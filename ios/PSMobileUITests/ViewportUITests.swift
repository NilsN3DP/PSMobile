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
        app.launchArguments = ["-psm-preset-printer", "-psm-start-advanced",
                               "-psm-load-cube"]
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

        // Eine freie Ecke: Orbit darf nicht davon abhaengen, ob ein
        // Objekt auf der Platte liegt.
        let leer = flaeche.coordinate(withNormalizedOffset: CGVector(dx: 0.12, dy: 0.18))
        let ziel = flaeche.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.18))
        leer.press(forDuration: 0.1, thenDragTo: ziel)
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

    func testDirekterFingerzugVerschiebtDasObjektOhneMoveGizmo() {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60))
        sleep(3)

        objektWaehlen()
        XCTAssertTrue(app.buttons["advanced.gizmo.none"].waitForExistence(timeout: 10))
        app.buttons["advanced.gizmo.none"].tap()

        let start = flaeche.coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let ziel = flaeche.coordinate(
            withNormalizedOffset: CGVector(dx: 0.68, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: ziel)
        sleep(1)

        // Der alte Mittelpunkt ist nun leer und hebt die Auswahl auf.
        start.tap()
        XCTAssertTrue(warte(bis: {
            !self.app.buttons["advanced.gizmo.none"].isEnabled
        }), "Der direkte Zug liess das Objekt am alten Ort")

        // Am Ziel liegt das Objekt und kann wieder gewaehlt werden.
        ziel.tap()
        XCTAssertTrue(warte(bis: {
            self.app.buttons["advanced.gizmo.none"].isEnabled
        }), "Das direkt gezogene Objekt ist am Ziel nicht treffbar")
    }

    func testMoveGizmoZiehtNurEntlangDerGepicktenAchse() {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60))
        sleep(3)
        objektWaehlen()
        app.buttons["advanced.gizmo.move"].tap()

        let ursprung = app.otherElements["viewport.gizmo.origin"]
        let xAchse = app.otherElements["viewport.gizmo.axis.0"]
        XCTAssertTrue(ursprung.waitForExistence(timeout: 10),
                      "Der Viewport meldet keinen Gizmo-Ursprung")
        XCTAssertTrue(xAchse.waitForExistence(timeout: 10),
                      "Der Viewport meldet keinen X-Achsgriff")

        let vorher = mitte(ursprung.frame)
        let griff = mitte(xAchse.frame)
        let dx = griff.x - vorher.x
        let dy = griff.y - vorher.y
        let laenge = max(hypot(dx, dy), 1)
        let einheit = CGVector(dx: dx / laenge, dy: dy / laenge)
        let ziel = CGPoint(x: griff.x + einheit.dx * 60,
                           y: griff.y + einheit.dy * 60)

        bildschirmKoordinate(griff).press(
            forDuration: 0.1, thenDragTo: bildschirmKoordinate(ziel))

        XCTAssertTrue(warte(bis: {
            hypot(self.mitte(ursprung.frame).x - vorher.x,
                  self.mitte(ursprung.frame).y - vorher.y) > 5
        }), "Der gepickte X-Achsgriff hat das Objekt nicht bewegt")

        let nachher = mitte(ursprung.frame)
        let bewegung = CGVector(dx: nachher.x - vorher.x,
                               dy: nachher.y - vorher.y)
        let entlang = bewegung.dx * einheit.dx + bewegung.dy * einheit.dy
        let quer = abs(bewegung.dx * -einheit.dy + bewegung.dy * einheit.dx)
        XCTAssertGreaterThan(entlang, 5,
                             "Der X-Achsgriff bewegt nicht in Achsrichtung")
        XCTAssertLessThan(quer, 8,
                          "Der X-Achsgriff laesst unerlaubte Querbewegung zu")
    }

    func testRotateGizmoMachtObjektflaecheNichtZumDirektzug() {
        pruefeGizmoOhneDirektzug("advanced.gizmo.rotate")
    }

    func testScaleGizmoMachtObjektflaecheNichtZumDirektzug() {
        pruefeGizmoOhneDirektzug("advanced.gizmo.scale")
    }

    func testAufFlaecheLegtGezieltDieGetroffeneZweiteInstanz() {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60))
        sleep(3)
        objektWaehlen()

        let mehr = app.buttons["advanced.kopien.mehr"]
        XCTAssertTrue(mehr.waitForExistence(timeout: 10),
                      "Der Inspector bietet keine zweite Instanz an")
        mehr.tap()
        let anzahl = app.staticTexts["advanced.kopien.anzahl"]
        XCTAssertTrue(warte(bis: { anzahl.label == "2" }),
                      "Die zweite Instanz wurde nicht angelegt")

        let drehfelder = ["X", "Y", "Z"].map {
            app.textFields["advanced.rotate." + $0]
        }
        XCTAssertTrue(drehfelder.allSatisfy { $0.waitForExistence(timeout: 5) })
        XCTAssertTrue(drehfelder.allSatisfy { ($0.value as? String) == "0" },
                      "Instanz 0 startet nicht unveraendert")

        let werkzeug = app.buttons["objekt.aufflaeche"]
        XCTAssertTrue(werkzeug.waitForExistence(timeout: 10),
                      "Das Flaechenwerkzeug ist nicht erreichbar")
        werkzeug.tap()

        /*
         * Die zweite Kopie liegt rechts neben der ersten. Mehrere Punkte
         * machen den Test unabhaengig davon, welche sichtbare Dreiecks-
         * haelfte die aktuelle Projektion an dieser Stelle zeigt.
         *
         * Nach einem Treffer nimmt das erste Undo nur das Hinlegen
         * zurueck und beide Kopien bleiben da. Bei einem Fehltipp nimmt
         * es stattdessen das Anlegen der zweiten Kopie zurueck; dann
         * stellt Redo sie fuer den naechsten Punkt wieder her.
         */
        let punkte = [
            CGVector(dx: 0.58, dy: 0.48),
            CGVector(dx: 0.62, dy: 0.50),
            CGVector(dx: 0.58, dy: 0.54),
        ]
        var zweiteGetroffen = false
        for punkt in punkte {
            flaeche.coordinate(withNormalizedOffset: punkt).tap()
            usleep(500_000)
            XCTAssertTrue(drehfelder.allSatisfy {
                ($0.value as? String) == "0"
            }, "Der Treffer auf Kopie 1 hat faelschlich Kopie 0 gedreht")

            app.buttons["advanced.zurueck"].tap()
            objektzeileWaehlen()
            if warte(bis: { anzahl.label == "2" }, timeout: 3) {
                zweiteGetroffen = true
                break
            }
            XCTAssertTrue(warte(bis: { anzahl.label == "1" }, timeout: 3),
                          "Undo zeigt weder Flaechentreffer noch Fehltipp")
            app.buttons["advanced.wiederholen"].tap()
            objektzeileWaehlen()
            XCTAssertTrue(warte(bis: { anzahl.label == "2" }, timeout: 5),
                          "Die zweite Kopie liess sich nicht wiederherstellen")
        }
        XCTAssertTrue(zweiteGetroffen,
                      "Keiner der rechten Punkte traf die zweite Instanz")
    }

    private func pruefeGizmoOhneDirektzug(_ kennung: String) {
        let flaeche = app.otherElements["viewport"]
        XCTAssertTrue(flaeche.waitForExistence(timeout: 60))
        sleep(3)
        objektWaehlen()
        app.buttons[kennung].tap()

        let start = flaeche.coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let ziel = flaeche.coordinate(
            withNormalizedOffset: CGVector(dx: 0.68, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: ziel)
        sleep(1)

        // Orbit behaelt den Wuerfel im Blickzentrum. Ein verbotener
        // Direktzug wuerde ihn dagegen an die Zielposition verschieben.
        start.tap()
        XCTAssertTrue(app.buttons[kennung].isEnabled,
                      "\(kennung) hat die Objektflaeche direkt verschoben")
    }

    @discardableResult
    private func objektWaehlen() -> XCUIElement {
        // Ueber die Objektliste auswaehlen, damit die Geste selbst nicht
        // erst Auswahl und Bewegung miteinander vermischt.
        let objekte = app.buttons["inspektor.objekte"]
        XCTAssertTrue(objekte.waitForExistence(timeout: 10))
        objekte.tap()
        return objektzeileWaehlen()
    }

    @discardableResult
    private func objektzeileWaehlen() -> XCUIElement {
        let zeile = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'advanced.objekt.'")).firstMatch
        XCTAssertTrue(zeile.waitForExistence(timeout: 10))
        zeile.tap()
        return zeile
    }

    private func mitte(_ rahmen: CGRect) -> CGPoint {
        CGPoint(x: rahmen.midX, y: rahmen.midY)
    }

    private func bildschirmKoordinate(_ punkt: CGPoint) -> XCUICoordinate {
        let rahmen = app.frame
        return app.coordinate(withNormalizedOffset: CGVector(
            dx: (punkt.x - rahmen.minX) / max(rahmen.width, 1),
            dy: (punkt.y - rahmen.minY) / max(rahmen.height, 1)))
    }

    private func warte(bis bedingung: () -> Bool,
                       timeout: TimeInterval = 10) -> Bool {
        let ende = Date().addingTimeInterval(timeout)
        while Date() < ende {
            if bedingung() { return true }
            usleep(300_000)
        }
        return bedingung()
    }
}
