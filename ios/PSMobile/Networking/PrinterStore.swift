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

    func setSecret(_ secret: PrusaLinkClient.Secret, for p: PrusaLinkClient.Printer) {
        let modus: PrinterAuthMode = p.usesApiKey ? .apiKey : .digest
        let wert = p.usesApiKey ? secret.apiKey : secret.password
        try? geheim.save(PrinterCredential(host: p.host, mode: modus, secret: wert))
    }
}
