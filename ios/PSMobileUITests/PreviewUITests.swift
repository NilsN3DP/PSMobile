import XCTest
import UIKit

/// Prueft die Werkzeugspalte und die G-Code-Vorschau.
///
/// Der Viewport kann beides seit langem - drei Gizmos und die
/// Werkzeugwege aus libvgcode -, auf iOS war nur nichts davon
/// erreichbar. Ein Objekt liess sich ziehen, aber nicht drehen, und die
/// Vorschau gab es gar nicht.
final class PreviewUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-psm-preset-printer", "-psm-start-simple", "-psm-load-cube"]
        app.launch()
        XCTAssertTrue(app.otherElements["simple.arbeitsbereich"].waitForExistence(timeout: 60))
    }

    func testGizmosErscheinenNurMitAuswahl() {
        // Ohne ausgewaehltes Objekt haengt kein Gizmo an irgendetwas -
        // die Knoepfe waeren dann Zierde.
        XCTAssertFalse(app.buttons["werkzeug.drehen"].exists,
                       "Die Gizmo-Knoepfe stehen ohne Auswahl da")

        app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'blatt.zeile.'")).firstMatch.tap()

        XCTAssertTrue(app.buttons["werkzeug.drehen"].waitForExistence(timeout: 5),
                      "Mit Auswahl fehlen die Gizmo-Knoepfe")
        app.buttons["werkzeug.drehen"].tap()
        // Umschalten darf die Auswahl nicht verlieren - sonst waere das
        // Gizmo im selben Moment wieder weg.
        XCTAssertTrue(app.buttons["werkzeug.skalieren"].exists)
    }

    /// Die Bildmitte als PNG, ohne Raender und ohne den Regler.
    private func ausschnitt() throws -> Data {
        let bild = app.screenshot().image
        let voll = CGRect(origin: .zero, size: bild.size)
        let mitte = voll.insetBy(dx: voll.width * 0.25, dy: voll.height * 0.25)
        guard let cg = bild.cgImage?.cropping(
                to: CGRect(x: mitte.minX * bild.scale, y: mitte.minY * bild.scale,
                           width: mitte.width * bild.scale,
                           height: mitte.height * bild.scale)),
              let daten = UIImage(cgImage: cg).pngData()
        else { throw XCTSkip("Bildausschnitt nicht moeglich") }
        return daten
    }


    func testDieVorschauErscheintErstNachDemSchneiden() {
        XCTAssertFalse(app.buttons["werkzeug.vorschau"].exists,
                       "Die Vorschau steht schon vor dem Schneiden bereit")

        app.buttons["simple.werkzeug.G-Code"].tap()
        XCTAssertTrue(app.buttons["slice.schliessen"].waitForExistence(timeout: 180),
                      "Der Schnitt kam zu keinem Ergebnis")
        app.buttons["slice.schliessen"].tap()

        let vorschau = app.buttons["werkzeug.vorschau"]
        XCTAssertTrue(vorschau.waitForExistence(timeout: 10),
                      "Nach dem Schneiden fehlt der Vorschau-Knopf")
        vorschau.tap()

        // Der Schichtregler erscheint nur, wenn libvgcode wirklich
        // Schichten geliefert hat.
        // Als Slider gemeldet, weil er einer ist - gezogen wird er
        // trotzdem ueber Koordinaten, wie mit dem Finger.
        let regler = app.sliders["vorschau.layer.oben"]
        XCTAssertTrue(regler.waitForExistence(timeout: 60),
                      "Die Vorschau zeigt keine Schichten")

        // Und sie muss auch etwas zeichnen. Der Schichtregler allein
        // beweist nur, dass libvgcode Schichten gemeldet hat - nicht,
        // dass davon etwas auf dem Schirm landet.
        sleep(2)
        // Verglichen wird die Bildmitte, nicht der ganze Schirm: dort
        // liegt das Modell. Im ganzen Bild steht auch die Beschriftung
        // des Reglers, und die aendert sich bei jedem Zug - ein
        // Vergleich darueber bestuende auch dann, wenn im Viewport
        // nichts passiert. Genau daran ist der Fehler lange
        // vorbeigelaufen.
        let mitAllen = (try? ausschnitt()) ?? Data()
        XCTAssertFalse(mitAllen.isEmpty, "Kein Bildausschnitt zu bekommen")
        // Der obere Grenzregler startet rechts; nach links zeigt weniger.
        regler.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
            .press(forDuration: 0.1,
                   thenDragTo: regler.coordinate(
                       withNormalizedOffset: CGVector(dx: 0.25, dy: 0.5)))
        sleep(2)
        let mitWenigen = (try? ausschnitt()) ?? Data()
        XCTAssertNotEqual(mitAllen, mitWenigen,
                          "Die Vorschau zeigt bei wenigen Schichten dasselbe Bild "
                          + "wie bei allen - die Schichtgrenze wirkt nicht")

        XCTAssertTrue(app.descendants(matching: .any)[
            "vorschau.statistik"].exists,
                      "Finale Zeit-/Filamentstatistik fehlt")
        let ersteRolle = app.buttons.matching(
            NSPredicate(format:
                "identifier BEGINSWITH 'vorschau.rolle.'")).firstMatch
        XCTAssertTrue(ersteRolle.exists,
            "Finale Feature-Rollen fehlen")
        ersteRolle.tap()
        XCTAssertEqual(ersteRolle.value as? String, "Hidden",
                       "Feature-Filter reagiert nicht auf Berührung")
        app.buttons["Extruders"].tap()
        let ersterExtruder = app.buttons.matching(
            NSPredicate(format:
                "identifier BEGINSWITH 'vorschau.extruder.'")).firstMatch
        XCTAssertTrue(ersterExtruder.exists,
            "Finale Extruder fehlen")
        ersterExtruder.tap()
        XCTAssertEqual(ersterExtruder.value as? String, "Hidden",
                       "Extruder-Filter reagiert nicht auf Berührung")

        app.buttons["vorschau.editor"].tap()
        XCTAssertFalse(app.descendants(matching: .any)[
            "vorschau.panel"].waitForExistence(
            timeout: 1),
            "Editor-Rückweg lässt die Vorschau offen")
        XCTAssertTrue(app.buttons["werkzeug.vorschau"].exists,
                      "Nach dem Rückweg ist der Editor nicht bedienbar")
    }

    func testPhoneVerwendetBottomSheetMitBeidseitigemRange() throws {
        guard UIDevice.current.userInterfaceIdiom == .phone else {
            throw XCTSkip("Dieser Vertrag gehört zum iPhone-Layout")
        }
        app.buttons["simple.werkzeug.G-Code"].tap()
        XCTAssertTrue(app.buttons["slice.schliessen"].waitForExistence(
            timeout: 180))
        app.buttons["slice.schliessen"].tap()
        app.buttons["werkzeug.vorschau"].tap()

        XCTAssertTrue(app.descendants(matching: .any)["vorschau.bottomsheet"]
            .waitForExistence(timeout: 60))
        XCTAssertTrue(app.sliders["vorschau.layer.unten"].exists)
        XCTAssertTrue(app.sliders["vorschau.layer.oben"].exists)
    }

    func testIPadVerwendetKompakteSeitenkarte() throws {
        guard UIDevice.current.userInterfaceIdiom == .pad else {
            throw XCTSkip("Dieser Vertrag gehört zum iPad-Layout")
        }
        app.buttons["simple.werkzeug.G-Code"].tap()
        XCTAssertTrue(app.buttons["slice.schliessen"].waitForExistence(
            timeout: 180))
        app.buttons["slice.schliessen"].tap()
        app.buttons["werkzeug.vorschau"].tap()

        XCTAssertTrue(app.descendants(matching: .any)["vorschau.seitenkarte"]
            .waitForExistence(timeout: 60))
        XCTAssertTrue(app.sliders["vorschau.layer.unten"].exists)
        XCTAssertTrue(app.sliders["vorschau.layer.oben"].exists)
    }
}
