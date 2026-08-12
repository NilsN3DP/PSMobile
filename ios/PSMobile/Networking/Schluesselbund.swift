import Foundation
import Security
import PSMShared

/// Der Umgang mit dem Schluesselbund an einer Stelle.
///
/// Es gibt zwei Ablagen fuer Geheimnisse - eine je Drucker
/// (`PrinterCredentialStore`, Konto aus Host und Auth-Modus) und eine
/// fuer den Remote-Slice-Server (`RemoteSliceCredentialStore`, ein festes
/// Konto). Wie sie ihre Konten schneiden, ist verschieden und soll es
/// bleiben. Wie sie mit der Security-API reden, war dagegen zweimal
/// dasselbe: SecItemUpdate, bei errSecItemNotFound SecItemAdd, dazu
/// derselbe Statuspruefer.
///
/// Vierzig Zeilen doppelt sind an dieser Stelle mehr als Kosmetik: hier
/// sass der Fehler, bei dem ein Passwort scheinbar gespeichert wurde und
/// die App danach "credentials incomplete" meldete. Ein Ort, an dem so
/// etwas passieren kann, ist besser als zwei.
struct Schluesselbund {
    let dienst: String

    private func basis(_ konto: String) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: dienst,
            kSecAttrAccount: konto,
        ]
    }

    func schreiben(_ wert: String, konto: String) throws {
        let daten = Data(wert.utf8)
        let frage = basis(konto)
        let status = SecItemUpdate(frage as CFDictionary,
                                   [kSecValueData: daten] as CFDictionary)
        if status == errSecItemNotFound {
            var neu = frage
            neu[kSecValueData] = daten
            // Nur auf diesem Geraet und erst nach dem ersten Entsperren.
            // Ein Backup traegt das Geheimnis damit nicht weiter.
            neu[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            try pruefen(SecItemAdd(neu as CFDictionary, nil))
        } else {
            try pruefen(status)
        }
    }

    func lesen(konto: String) throws -> String? {
        var frage = basis(konto)
        frage[kSecReturnData] = true
        frage[kSecMatchLimit] = kSecMatchLimitOne
        var ergebnis: CFTypeRef?
        let status = SecItemCopyMatching(frage as CFDictionary, &ergebnis)
        if status == errSecItemNotFound { return nil }
        try pruefen(status)
        guard let daten = ergebnis as? Data,
              let wert = String(data: daten, encoding: .utf8) else {
            throw CredentialStoreError.invalidData
        }
        return wert
    }

    func loeschen(konto: String) throws {
        let status = SecItemDelete(basis(konto) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw CredentialStoreError.status(status)
        }
    }

    private func pruefen(_ status: OSStatus) throws {
        guard status == errSecSuccess else { throw CredentialStoreError.status(status) }
    }
}

enum CredentialStoreError: LocalizedError {
    case status(OSStatus)
    case invalidData

    var errorDescription: String? {
        switch self {
        case .status(let status):
            return SecCopyErrorMessageString(status, nil) as String? ?? "Keychain error \(status)"
        case .invalidData:
            return st("Invalid credentials in the keychain",
                      "Ungültige Zugangsdaten im Keychain")
        }
    }
}
