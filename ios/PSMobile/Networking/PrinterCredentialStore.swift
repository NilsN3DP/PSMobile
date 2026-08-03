import Foundation
import Security

/// Die eingerichteten PrusaLink-Drucker.
///
/// Gegenstueck zu `PrinterStore.kt`. Dieselbe Trennung wie dort: was
/// nicht geheim ist, liegt in den normalen Einstellungen; Passwoerter
/// und API-Schluessel gehoeren in den Schluesselbund.
///
/// Bewusst nicht ins Datenverzeichnis von libslic3r: das sind Geraete
/// des Nutzers, keine Slicer-Profile. Ein Profilabgleich duerfte sie
/// sonst mitnehmen.
@MainActor
final class PrinterCredentialStore: ObservableObject {

    @Published private(set) var printers: [PrusaLinkClient.Printer] = []

    private static let key = "prusalink.printers"

    init() {
        load()
    }

    private func load() {
        guard let daten = UserDefaults.standard.data(forKey: Self.key),
              let liste = try? JSONDecoder().decode([PrusaLinkClient.Printer].self, from: daten)
        else {
            printers = []
            return
        }
        printers = liste
    }

    private func save() {
        guard let daten = try? JSONEncoder().encode(printers) else { return }
        UserDefaults.standard.set(daten, forKey: Self.key)
    }

    func upsert(_ p: PrusaLinkClient.Printer) {
        if let i = printers.firstIndex(where: { $0.id == p.id }) {
            printers[i] = p
        } else {
            printers.append(p)
        }
        save()
    }

    func remove(_ id: String) {
        printers.removeAll { $0.id == id }
        save()
        // Die Zugangsdaten muessen mit weg. Ein Passwort im
        // Schluesselbund, zu dem es kein Geraet mehr gibt, ist nur noch
        // Altlast.
        Keychain.delete(konto: id)
    }

    func secret(for id: String) -> PrusaLinkClient.Secret {
        guard let daten = Keychain.read(konto: id),
              let werte = try? JSONDecoder().decode([String: String].self, from: daten)
        else {
            return PrusaLinkClient.Secret()
        }
        return PrusaLinkClient.Secret(apiKey: werte["apiKey"] ?? "",
                                      password: werte["password"] ?? "")
    }

    func setSecret(_ secret: PrusaLinkClient.Secret, for id: String) {
        let werte = ["apiKey": secret.apiKey, "password": secret.password]
        guard let daten = try? JSONEncoder().encode(werte) else { return }
        Keychain.write(daten, konto: id)
    }
}

/// Der Schluesselbund, so knapp wie es geht.
///
/// Nur drei Vorgaenge, und alle mit `kSecAttrAccessibleWhenUnlocked`:
/// die Zugangsdaten werden gebraucht, waehrend jemand vor dem Geraet
/// sitzt, nicht im Hintergrund. Ein Backup soll sie ausserdem nicht auf
/// ein anderes Geraet tragen - daher `ThisDeviceOnly`.
enum Keychain {

    private static let dienst = "de.psmobile.prusalink"

    static func write(_ daten: Data, konto: String) {
        let suche: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: dienst,
            kSecAttrAccount as String: konto,
        ]
        SecItemDelete(suche as CFDictionary)

        var neu = suche
        neu[kSecValueData as String] = daten
        neu[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(neu as CFDictionary, nil)
    }

    static func read(konto: String) -> Data? {
        let suche: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: dienst,
            kSecAttrAccount as String: konto,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var ergebnis: CFTypeRef?
        guard SecItemCopyMatching(suche as CFDictionary, &ergebnis) == errSecSuccess else {
            return nil
        }
        return ergebnis as? Data
    }

    static func delete(konto: String) {
        let suche: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: dienst,
            kSecAttrAccount as String: konto,
        ]
        SecItemDelete(suche as CFDictionary)
    }
}
