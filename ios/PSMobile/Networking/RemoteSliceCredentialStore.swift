import Foundation
import Security

/// Das Zugangs-Token fuer den eigenen Remote-Slice-Server - im
/// Schluesselbund, nie in UserDefaults. Anders als bei PrusaLink gibt es
/// hier nur einen Server (siehe docs/remote-slicing.md, "eine
/// Warteschlange, ein Server"), deshalb ein fester Account statt einer
/// Aufteilung nach Host wie in `PrinterCredentialStore`.
struct RemoteSliceCredentialStore {
    private let service = "de.psmobile.remote-slice"
    private let account = "token"

    func save(_ token: String) throws {
        let data = Data(token.utf8)
        let query = baseQuery()
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

    func load() throws -> String? {
        var query = baseQuery()
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        try check(status)
        guard let data = result as? Data, let token = String(data: data, encoding: .utf8) else {
            throw CredentialStoreError.invalidData
        }
        return token
    }

    func remove() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { try check(status); return }
    }

    private func baseQuery() -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
        ]
    }

    private func check(_ status: OSStatus) throws {
        guard status == errSecSuccess else { throw CredentialStoreError.status(status) }
    }
}
