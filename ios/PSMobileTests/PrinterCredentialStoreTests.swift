import XCTest
import Security

/// Prueft den Schluesselbund-Zugang.
///
/// Die Datei wird mitkompiliert statt ueber "@testable import" aus der
/// App geholt. Das ist hier keine Sparsamkeit, sondern der einzige Weg:
/// das Testbuendel laeuft im Prozess der App, und ein zweites statisches
/// PSMShared wuerde Kotlin/Native-Klassen doppelt laden. Zum Glueck
/// braucht dieser Teil nur Foundation und Security.
final class PrinterCredentialStoreTests: XCTestCase {

    private let store = PrinterCredentialStore()
    private let host = "test-drucker.local"

    override func tearDown() {
        try? store.remove(host: host, mode: .digest)
        try? store.remove(host: host, mode: .apiKey)
    }

    func testGeheimnisKommtWiederHeraus() throws {
        try store.save(PrinterCredential(host: host, mode: .digest, secret: "streng-geheim-42"))
        XCTAssertEqual(try store.load(host: host, mode: .digest)?.secret, "streng-geheim-42")
    }

    func testBeideVerfahrenHabenEigenePlaetze() throws {
        // Wer von Passwort auf API-Schluessel wechselt und zurueck, soll
        // nicht beide Male neu tippen muessen.
        try store.save(PrinterCredential(host: host, mode: .digest, secret: "passwort"))
        try store.save(PrinterCredential(host: host, mode: .apiKey, secret: "schluessel"))

        XCTAssertEqual(try store.load(host: host, mode: .digest)?.secret, "passwort")
        XCTAssertEqual(try store.load(host: host, mode: .apiKey)?.secret, "schluessel")
    }

    func testUeberschreibenStattZweitemEintrag() throws {
        // Ein zweiter Eintrag zur selben Adresse waere nicht sichtbar,
        // aber der Schluesselbund liefert dann irgendeinen von beiden -
        // und damit vielleicht das alte Passwort.
        try store.save(PrinterCredential(host: host, mode: .digest, secret: "alt"))
        try store.save(PrinterCredential(host: host, mode: .digest, secret: "neu"))
        XCTAssertEqual(try store.load(host: host, mode: .digest)?.secret, "neu")
    }

    func testEntferntesGeheimnisIstWeg() throws {
        try store.save(PrinterCredential(host: host, mode: .digest, secret: "weg-damit"))
        try store.remove(host: host, mode: .digest)
        XCTAssertNil(try store.load(host: host, mode: .digest))
    }

    func testUnbekannteAdresseLiefertNichts() throws {
        // Nicht wirft, sondern nil: ein Drucker ohne hinterlegte Daten
        // ist ein gewoehnlicher Zustand, kein Fehler.
        XCTAssertNil(try store.load(host: "gibt-es-nicht.local", mode: .digest))
    }
}
