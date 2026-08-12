import Foundation

/// Das Zugangs-Token fuer den eigenen Remote-Slice-Server - im
/// Schluesselbund, nie in UserDefaults. Anders als bei PrusaLink gibt es
/// hier nur einen Server (siehe docs/remote-slicing.md, "eine
/// Warteschlange, ein Server"), deshalb ein festes Konto statt einer
/// Aufteilung nach Host wie in `PrinterCredentialStore`.
struct RemoteSliceCredentialStore {
    private let bund = Schluesselbund(dienst: "de.psmobile.remote-slice")
    private let konto = "token"

    func save(_ token: String) throws {
        try bund.schreiben(token, konto: konto)
    }

    func load() throws -> String? {
        try bund.lesen(konto: konto)
    }

    func remove() throws {
        try bund.loeschen(konto: konto)
    }
}
