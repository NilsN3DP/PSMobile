import Foundation

/// Die eingerichteten PrusaLink-Drucker.
///
/// Gegenstueck zu `PrinterStore.kt`. Dieselbe Trennung wie dort: was
/// nicht geheim ist, liegt in den normalen Einstellungen; Passwoerter
/// und API-Schluessel holt und legt [PrinterCredentialStore] im
/// Schluesselbund ab.
///
/// Bewusst nicht ins Datenverzeichnis von libslic3r: das sind Geraete
/// des Nutzers, keine Slicer-Profile. Ein Profilabgleich duerfte sie
/// sonst mitnehmen.
@MainActor
final class PrinterStore: ObservableObject {

    @Published private(set) var printers: [PrusaLinkClient.Printer] = []

    private static let key = "prusalink.printers"
    private let geheim = PrinterCredentialStore()

    init() {
        // Die UI-Tests brauchen eine leere Liste, sonst faengt der
        // zweite Durchlauf mit den Druckern des ersten an und prueft
        // nichts mehr. Nur ueber ein Startargument - eine Einstellung
        // dafuer waere ein Schalter, mit dem sich versehentlich alles
        // loeschen liesse.
        if ProcessInfo.processInfo.arguments.contains("-psm-reset-printers") {
            UserDefaults.standard.removeObject(forKey: Self.key)
        }
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

    func remove(_ p: PrusaLinkClient.Printer) {
        printers.removeAll { $0.id == p.id }
        save()
        // Die Zugangsdaten muessen mit weg. Ein Passwort im
        // Schluesselbund, zu dem es kein Geraet mehr gibt, ist Altlast.
        try? geheim.remove(host: p.host, mode: .apiKey)
        try? geheim.remove(host: p.host, mode: .digest)
    }

    /// Das Geheimnis zum gewaehlten Verfahren. Beide Verfahren haben
    /// einen eigenen Platz im Schluesselbund - wer von Passwort auf
    /// API-Schluessel wechselt, verliert das andere nicht.
    func secret(for p: PrusaLinkClient.Printer) -> PrusaLinkClient.Secret {
        let modus: PrinterAuthMode = p.usesApiKey ? .apiKey : .digest
        let wert = (try? geheim.load(host: p.host, mode: modus))??.secret ?? ""
        return p.usesApiKey
            ? PrusaLinkClient.Secret(apiKey: wert, password: "")
            : PrusaLinkClient.Secret(apiKey: "", password: wert)
    }

    /// Liefert `false`, wenn das Schreiben in den Schluesselbund
    /// fehlschlug - vorher verschluckte `try?` das stillschweigend, und
    /// ein Aufrufer wie die QR-Kopplung meldete Erfolg, obwohl das
    /// Passwort nie ankam.
    @discardableResult
    func setSecret(_ secret: PrusaLinkClient.Secret, for p: PrusaLinkClient.Printer) -> Bool {
        let modus: PrinterAuthMode = p.usesApiKey ? .apiKey : .digest
        let wert = p.usesApiKey ? secret.apiKey : secret.password
        do {
            try geheim.save(PrinterCredential(host: p.host, mode: modus, secret: wert))
            return true
        } catch {
            print("PrinterStore.setSecret: Schluesselbund-Schreibfehler fuer \(p.host): \(error)")
            return false
        }
    }

    func localPairingToken(for p: PrusaLinkClient.Printer) -> String? {
        try? geheim.load(host: p.id, mode: .localPairing)?.secret
    }

    /// Liefert wie [setSecret] `false`, wenn der Schluesselbund den
    /// Token nicht aufnehmen konnte - sonst meldete die Kopplung Erfolg,
    /// waehrend der Token nie ankam.
    @discardableResult
    func setLocalPairingToken(_ token: String?, for p: PrusaLinkClient.Printer) -> Bool {
        do {
            if let token, !token.isEmpty {
                try geheim.save(PrinterCredential(host: p.id, mode: .localPairing, secret: token))
            } else {
                try geheim.remove(host: p.id, mode: .localPairing)
            }
            return true
        } catch {
            print("PrinterStore.setLocalPairingToken: Schluesselbund-Fehler fuer \(p.id): \(error)")
            return false
        }
    }
}
