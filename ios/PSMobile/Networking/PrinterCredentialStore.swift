import Foundation
import Security

enum PrinterAuthMode: String, Codable, CaseIterable {
    case apiKey
    case digest
}

struct PrinterCredential: Equatable {
    let host: String
    let mode: PrinterAuthMode
    let secret: String
}

/// Geheimnisse liegen ausschliesslich im Keychain. Die Druckerkonfiguration
/// kann Host und Auth-Modus in UserDefaults speichern, nie aber das Secret.
struct PrinterCredentialStore {
    private let service = "de.psmobile.printer-credential"

    func save(_ credential: PrinterCredential) throws {
        let data = Data(credential.secret.utf8)
        let query = baseQuery(host: credential.host, mode: credential.mode)
        let update: [CFString: Any] = [kSecValueData: data]
        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = query
            add[kSecValueData] = data
            add[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            try check(SecItemAdd(add as CFDictionary, nil))
        } else {
            try check(status)
        }
    }

    func load(host: String, mode: PrinterAuthMode) throws -> PrinterCredential? {
        var query = baseQuery(host: host, mode: mode)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        try check(status)
        guard let data = result as? Data, let secret = String(data: data, encoding: .utf8) else {
            throw CredentialStoreError.invalidData
        }
        return PrinterCredential(host: host, mode: mode, secret: secret)
    }

    func remove(host: String, mode: PrinterAuthMode) throws {
        let status = SecItemDelete(baseQuery(host: host, mode: mode) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { try check(status); return }
    }

    private func baseQuery(host: String, mode: PrinterAuthMode) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "\(host.lowercased())|\(mode.rawValue)",
        ]
    }

    private func check(_ status: OSStatus) throws {
        guard status == errSecSuccess else { throw CredentialStoreError.status(status) }
    }
}

// `CredentialStoreError` stand bis zum 10.09.2026 hier ein zweites Mal,
// zeichengleich zur Fassung in Schluesselbund.swift - nur mit fest
// verdrahtetem deutschem Text statt `st(...)`. Swift laesst dieselbe
// Deklaration nicht zweimal im selben Modul zu: die App liess sich
// dadurch nicht uebersetzen ("invalid redeclaration").
//
// Geblieben ist die Fassung in Schluesselbund.swift, weil sie ihre
// Meldung zweisprachig fuehrt. Sie gilt auch fuer diese Datei - beide
// liegen im selben Modul.
