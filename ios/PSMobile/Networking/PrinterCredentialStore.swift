import Foundation
import PSMShared

enum PrinterAuthMode: String, Codable, CaseIterable {
    case apiKey
    case digest
    case localPairing
}

struct PrinterCredential: Equatable {
    let host: String
    let mode: PrinterAuthMode
    let secret: String
}

/// Geheimnisse liegen ausschliesslich im Keychain. Die Druckerkonfiguration
/// kann Host und Auth-Modus in UserDefaults speichern, nie aber das Secret.
///
/// Ein Konto je Drucker und Verfahren: derselbe Drucker kann einen
/// API-Schluessel und ein Digest-Passwort haben, ohne dass eines das
/// andere ueberschreibt. Den Umgang mit der Security-API selbst
/// uebernimmt `Schluesselbund`.
struct PrinterCredentialStore {
    private let bund = Schluesselbund(dienst: "de.psmobile.printer-credential")

    private func konto(_ host: String, _ mode: PrinterAuthMode) -> String {
        "\(host.lowercased())|\(mode.rawValue)"
    }

    func save(_ credential: PrinterCredential) throws {
        try bund.schreiben(credential.secret, konto: konto(credential.host, credential.mode))
    }

    func load(host: String, mode: PrinterAuthMode) throws -> PrinterCredential? {
        guard let secret = try bund.lesen(konto: konto(host, mode)) else { return nil }
        return PrinterCredential(host: host, mode: mode, secret: secret)
    }

    func remove(host: String, mode: PrinterAuthMode) throws {
        try bund.loeschen(konto: konto(host, mode))
    }
}
