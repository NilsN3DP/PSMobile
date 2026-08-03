import XCTest
@testable import PSMobile

/// Prueft, wo die Zugangsdaten eines Druckers landen.
///
/// Die Druckerliste geht als JSON in die normalen Einstellungen - das
/// ist richtig so, es sind keine Geheimnisse. Passwort und
/// API-Schluessel duerfen dort aber unter keinen Umstaenden auftauchen:
/// UserDefaults liegt unverschluesselt im App-Container und wandert in
/// jedes Backup.
@MainActor
final class PrinterStoreTests: XCTestCase {

    func testGeheimnisseStehenNichtInDenEinstellungen() {
        let store = PrinterStore()
        var drucker = PrusaLinkClient.Printer()
        drucker.name = "Werkstatt"
        drucker.host = "192.168.1.50"
        drucker.username = "maker"

        store.upsert(drucker)
        store.setSecret(PrusaLinkClient.Secret(apiKey: "", password: "streng-geheim-42"),
                        for: drucker)

        let roh = UserDefaults.standard.data(forKey: "prusalink.printers")
        let text = roh.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        XCTAssertTrue(text.contains("192.168.1.50"),
                      "Der Drucker wurde gar nicht gesichert")
        XCTAssertFalse(text.contains("streng-geheim-42"),
                       "Das Passwort steht in den Einstellungen")

        // Und es kommt aus dem Schluesselbund wieder heraus - sonst
        // waere es zwar sicher, aber nutzlos.
        XCTAssertEqual(store.secret(for: drucker).password, "streng-geheim-42")

        store.remove(drucker)
    }

    func testEntfernenNimmtDieZugangsdatenMit() {
        // Ein Passwort im Schluesselbund, zu dem es kein Geraet mehr
        // gibt, ist Altlast - und beim naechsten Drucker unter derselben
        // Adresse ploetzlich wieder da.
        let store = PrinterStore()
        var drucker = PrusaLinkClient.Printer()
        drucker.host = "192.168.1.51"
        store.upsert(drucker)
        store.setSecret(PrusaLinkClient.Secret(apiKey: "", password: "weg-damit"),
                        for: drucker)
        XCTAssertEqual(store.secret(for: drucker).password, "weg-damit")

        store.remove(drucker)
        XCTAssertEqual(store.secret(for: drucker).password, "")
    }
}
