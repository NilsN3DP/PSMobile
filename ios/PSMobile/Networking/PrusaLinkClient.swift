import Foundation
import PSMShared

/// PrusaLink-Anbindung fuer iOS.
///
/// Gegenstueck zu `PrusaLink.kt`. Hier steht nur die Verbindung selbst;
/// Adressen zusammensetzen, Dateinamen entschaerfen und Antwortcodes
/// deuten liegt in `PrusaLinkRules` im gemeinsamen Modul - sonst
/// beantwortet iOS dieselbe Frage anders, ohne dass es auffaellt.
///
/// Zwei Anmeldeverfahren, genau wie PrusaSlicer sie in OctoPrint.cpp
/// unterscheidet:
///   - aeltere Firmware: Kopfzeile X-Api-Key
///   - ab PrusaLink 0.7: HTTP-Digest mit Benutzername und Passwort
/// Digest, nicht Basic - Basic wird abgelehnt.
actor PrusaLinkClient {

    /// Womit der Drucker spricht. PrusaLink bleibt die Vorgabe - jeder
    /// vor diesem Feld gespeicherte Drucker dekodiert ohne den Schluessel
    /// zu diesem Fall, siehe die Codable-Vorgabe darunter.
    enum HostType: String, Codable, CaseIterable {
        case prusaLink, octoprint
    }

    /// Herkunft des Eintrags fuer die experimentelle Beleuchtung. Alte
    /// Eintraege bleiben unbekannt, damit sie bis zu einer bewussten
    /// Neueinrichtung keine Lighting-Capability erhalten.
    enum LightingProfile: String, Codable {
        case manualPhysical, cloud, demo, simulated, unknown
    }

    struct Printer: Codable, Identifiable, Equatable {
        var id: String = UUID().uuidString
        var name: String = ""
        /// URL; ohne Schema gilt HTTPS.
        var host: String = ""
        var hostType: HostType = .prusaLink
        var usesApiKey: Bool = false
        var username: String = PrusaLinkRules.shared.DEFAULT_USER
        var storage: String = "usb"
        var allowInsecureHttp: Bool = false
        /// Nicht geheime, pro Drucker getrennte Experimental-Einwilligung.
        var lightingOptIn: Bool = false
        var lightingProfile: LightingProfile = .manualPhysical
        /// Experimental local-hotspot pairing metadata; token remains in Keychain.
        var localExperimental: Bool = false
        var localHosts: [String] = []
        var localModel: String = ""
        var localCapabilities: Set<String> = []
        var localNozzleDiameter: Double? = nil
        var localNozzleMaterial: String = "unknown"
        /// Profilname in PSMobile, damit Profil und Geraet zusammenfinden.
        var presetName: String = ""

        var auth: PrusaLinkRules.Auth {
            usesApiKey ? PrusaLinkRules.Auth.apiKey : PrusaLinkRules.Auth.userPassword
        }

        var baseUrl: String { PrusaLinkRules.shared.baseUrl(host: host) }

        var transportError: String? {
            PrusaLinkRules.shared.transportError(host: host,
                                                 allowInsecureHttp: allowInsecureHttp)
        }

        func isComplete(secret: Secret) -> Bool {
            PrusaLinkRules.shared.isComplete(
                host: host,
                allowInsecureHttp: allowInsecureHttp,
                auth: auth,
                apiKey: secret.apiKey,
                username: username,
                password: secret.password
            )
        }
    }

    /// Was nie in den normalen Einstellungen landen darf.
    struct Secret {
        var apiKey: String = ""
        var password: String = ""
    }

    enum Ergebnis {
        case ok(String)
        case fehler(String)
    }

    struct LocalPairingPayload: Codable {
        var type: String
        var version: Int
        var model: String
        var host: String
        var port: Int
        var transport: String
        var pairingToken: String
        var capabilities: Set<String>
        var nozzle: Nozzle

        struct Nozzle: Codable {
            var diameter: Double
            var material: String?

            /// `true`/`false` fuer gehaertet - so sendet es die
            /// CFW-Gegenstelle (`build_prusalink_payload` in
            /// local_prusalink_qr.cpp, pfw653-farm-mini). `material`
            /// bleibt fuer manuelle Eingabe/Tests erhalten, ist bei
            /// echten Firmware-QR-Codes aber nie gesetzt.
            var hardened: Bool?

            var materialOrDerived: String {
                if let material { return material }
                if let hardened { return hardened ? "hardened" : "brass" }
                return "unknown"
            }
        }

        enum CodingKeys: String, CodingKey {
            case type, version, model, host, port, transport, capabilities, nozzle
            case pairingToken = "pairing_token"
        }
    }

    /// Die CFW-Gegenstelle (`lib/WUI/link_content/local_pairing.cpp` in
    /// pfw653-farm-mini) antwortet auf `/api/pair` ausschliesslich mit
    /// `{"username":"...","password":"..."}` - Modell, Duese, Host und
    /// Port kannte der Drucker bereits aus dem eigenen QR-Code, den diese
    /// App gerade gescannt und validiert hat. Die zusaetzlichen Felder
    /// hier zu verlangen hiess: jede echte Kopplung schlug als
    /// "invalidResponse" fehl, obwohl der Token-Austausch selbst erfolgreich war.
    private struct LocalPairResponse: Codable {
        var username: String
        var password: String
    }

    struct LocalPairResult {
        var printer: Printer
        var secret: Secret
        var nozzleDiameter: Double
        var nozzleMaterial: String
    }

    enum LocalPairError: Error {
        case invalidPayload
        case unauthorized
        case invalidResponse
    }

    /// Exchanges the QR token with the CFW-only local pairing endpoint.
    /// The token is sent once and is never included in errors or logs.
    func pairLocal(_ payload: LocalPairingPayload) async throws -> LocalPairResult {
        guard payload.type == "prusalink-local", payload.version == 1,
              payload.transport.lowercased() == "http", !payload.model.isEmpty,
              payload.pairingToken.isEmpty == false,
              Self.isLocalHost(payload.host), (1...65535).contains(payload.port)
        else { throw LocalPairError.invalidPayload }
        guard let url = URL(string: "http://\(payload.host):\(payload.port)/api/pair") else {
            throw LocalPairError.invalidPayload
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(["pairing_token": payload.pairingToken])
        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard code != 401 else { throw LocalPairError.unauthorized }
        guard (200...299).contains(code), let result = try? JSONDecoder().decode(LocalPairResponse.self, from: data),
              !result.username.isEmpty, !result.password.isEmpty else { throw LocalPairError.invalidResponse }
        let material = payload.nozzle.materialOrDerived
        let printer = Printer(
            name: payload.model, host: "http://\(payload.host):\(payload.port)",
            username: result.username, allowInsecureHttp: true,
            localExperimental: true, localHosts: [payload.host],
            localModel: payload.model, localCapabilities: payload.capabilities,
            localNozzleDiameter: payload.nozzle.diameter, localNozzleMaterial: material,
        )
        return LocalPairResult(printer: printer,
                               secret: Secret(password: result.password),
                               nozzleDiameter: payload.nozzle.diameter,
                               nozzleMaterial: material)
    }

    private static func isLocalHost(_ host: String) -> Bool {
        let parts = host.split(separator: ".").compactMap { Int($0) }
        guard parts.count == 4, parts.allSatisfy({ (0...255).contains($0) }) else { return false }
        return parts[0] == 10 || (parts[0] == 172 && (16...31).contains(parts[1])) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    /// Digest-Herausforderungen je Drucker merken.
    ///
    /// Wichtig fuers Hochladen: Wer erst sendet und dann eine 401
    /// bekommt, hat die Datei umsonst uebertragen. Die nonce kommt
    /// deshalb aus einer billigen Anfrage und wird fuer den PUT
    /// wiederverwendet - bei qop=auth ist das mit hochgezaehltem nc
    /// ausdruecklich erlaubt.
    private var challenges: [String: DigestAuth.Challenge] = [:]

    private let session: URLSession

    init() {
        let konfig = URLSessionConfiguration.ephemeral
        konfig.timeoutIntervalForRequest =
            TimeInterval(PrusaLinkRules.shared.TIMEOUT_MS) / 1000
        konfig.waitsForConnectivity = false
        session = URLSession(configuration: konfig)
    }

    /// Zustand abfragen. Dient zugleich als Test der Anmeldedaten.
    func probe(_ p: Printer, secret: Secret) async -> Ergebnis {
        if let fehler = p.transportError { return .fehler(fehler) }
        do {
            let (code, body) = try await anfrage(p, secret: secret,
                                                 pfad: PrusaLinkRules.shared.STATUS_PATH,
                                                 methode: "GET")
            if let fehler = PrusaLinkRules.shared.probeError(code: Int32(code), auth: p.auth) {
                return .fehler(fehler)
            }
            return .ok(PrusaLinkRules.shared.describeStatus(body: body))
        } catch {
            return .fehler(error.localizedDescription)
        }
    }

    /// Datei hochladen.
    func upload(_ p: Printer,
                secret: Secret,
                datei: URL,
                name: String,
                printAfter: Bool) async -> Ergebnis {
        if let fehler = p.transportError { return .fehler(fehler) }
        let pfad = PrusaLinkRules.shared.uploadPath(storage: p.storage, fileName: name)

        do {
            // Bei Digest zuerst eine billige Anfrage, um die nonce zu
            // holen - sonst ginge die Datei beim ersten Versuch ins Leere.
            if !p.usesApiKey && challenges[p.id] == nil {
                _ = try? await anfrage(p, secret: secret,
                                       pfad: PrusaLinkRules.shared.STATUS_PATH, methode: "GET")
            }

            var (code, body) = try await put(p, secret: secret, pfad: pfad,
                                             datei: datei, printAfter: printAfter)

            // Abgelaufene nonce: einmal neu holen und wiederholen.
            if code == 401 && !p.usesApiKey {
                challenges[p.id] = nil
                _ = try? await anfrage(p, secret: secret,
                                       pfad: PrusaLinkRules.shared.STATUS_PATH, methode: "GET")
                let zweiter = try await put(p, secret: secret, pfad: pfad,
                                            datei: datei, printAfter: printAfter)
                code = zweiter.0
                body = zweiter.1
            }

            if let fehler = PrusaLinkRules.shared.uploadError(code: Int32(code)) {
                return .fehler(code >= 500 ? fehler + ": " + body.prefix(200) : fehler)
            }
            return .ok(PrusaLinkRules.shared.uploadOk(printAfter: printAfter))
        } catch {
            return .fehler(error.localizedDescription)
        }
    }

    // MARK: - Innereien

    private func anfrage(_ p: Printer,
                         secret: Secret,
                         pfad: String,
                         methode: String) async throws -> (Int, String) {
        var (code, body, kopf) = try await sende(p, secret: secret, pfad: pfad, methode: methode)

        // Erste Digest-Herausforderung einsammeln und wiederholen.
        if code == 401 && !p.usesApiKey {
            guard let c = DigestAuth.shared.parseChallenge(header: kopf) else {
                return (401, body)
            }
            challenges[p.id] = c
            (code, body, kopf) = try await sende(p, secret: secret, pfad: pfad, methode: methode)
        }
        return (code, body)
    }

    private func sende(_ p: Printer,
                       secret: Secret,
                       pfad: String,
                       methode: String) async throws -> (Int, String, String?) {
        guard let url = URL(string: p.baseUrl + pfad) else {
            throw URLError(.badURL)
        }
        var anfrage = URLRequest(url: url)
        anfrage.httpMethod = methode
        anfrage.setValue("application/json", forHTTPHeaderField: "Accept")
        setzeAnmeldung(&anfrage, p: p, secret: secret, methode: methode, pfad: pfad)

        let (daten, antwort) = try await session.data(for: anfrage)
        let http = antwort as? HTTPURLResponse
        return (http?.statusCode ?? 0,
                String(data: daten, encoding: .utf8) ?? "",
                http?.value(forHTTPHeaderField: "WWW-Authenticate"))
    }

    private func put(_ p: Printer,
                     secret: Secret,
                     pfad: String,
                     datei: URL,
                     printAfter: Bool) async throws -> (Int, String) {
        guard let url = URL(string: p.baseUrl + pfad) else { throw URLError(.badURL) }
        var anfrage = URLRequest(url: url)
        anfrage.httpMethod = "PUT"
        anfrage.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        anfrage.setValue(PrusaLinkRules.shared.printAfterHeader(printAfter: printAfter),
                         forHTTPHeaderField: "Print-After-Upload")
        anfrage.setValue("?1", forHTTPHeaderField: "Overwrite")
        setzeAnmeldung(&anfrage, p: p, secret: secret, methode: "PUT", pfad: pfad)

        // Aus der Datei streamen statt sie in den Speicher zu laden: ein
        // G-Code kann dreistellige Megabyte haben, und iOS beendet Apps,
        // die sich das leisten.
        let (daten, antwort) = try await session.upload(for: anfrage, fromFile: datei)
        let http = antwort as? HTTPURLResponse
        return (http?.statusCode ?? 0, String(data: daten, encoding: .utf8) ?? "")
    }

    private func setzeAnmeldung(_ anfrage: inout URLRequest,
                                p: Printer,
                                secret: Secret,
                                methode: String,
                                pfad: String) {
        if p.usesApiKey {
            anfrage.setValue(secret.apiKey, forHTTPHeaderField: "X-Api-Key")
        } else if let c = challenges[p.id] {
            anfrage.setValue(
                DigestAuth.shared.authorization(c: c,
                                                username: p.username,
                                                password: secret.password,
                                                method: methode,
                                                uri: pfad),
                forHTTPHeaderField: "Authorization")
        }
    }
}
extension PrusaLinkClient.Printer {
    private enum CodingKeys: String, CodingKey {
        case id, name, host, hostType, usesApiKey, username, storage,
             allowInsecureHttp, presetName, lightingOptIn, lightingProfile,
             localExperimental, localHosts, localModel, localCapabilities,
             localNozzleDiameter, localNozzleMaterial
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        host = try c.decode(String.self, forKey: .host)
        hostType = try c.decodeIfPresent(PrusaLinkClient.HostType.self, forKey: .hostType)
            ?? .prusaLink
        usesApiKey = try c.decode(Bool.self, forKey: .usesApiKey)
        username = try c.decode(String.self, forKey: .username)
        storage = try c.decode(String.self, forKey: .storage)
        allowInsecureHttp = try c.decode(Bool.self, forKey: .allowInsecureHttp)
        presetName = try c.decode(String.self, forKey: .presetName)
        lightingOptIn = try c.decodeIfPresent(Bool.self, forKey: .lightingOptIn) ?? false
        lightingProfile = try c.decodeIfPresent(PrusaLinkClient.LightingProfile.self,
                                                forKey: .lightingProfile) ?? .unknown
        localExperimental = try c.decodeIfPresent(Bool.self, forKey: .localExperimental) ?? false
        localHosts = try c.decodeIfPresent([String].self, forKey: .localHosts) ?? []
        localModel = try c.decodeIfPresent(String.self, forKey: .localModel) ?? ""
        localCapabilities = try c.decodeIfPresent(Set<String>.self, forKey: .localCapabilities) ?? []
        localNozzleDiameter = try c.decodeIfPresent(Double.self, forKey: .localNozzleDiameter)
        localNozzleMaterial = try c.decodeIfPresent(String.self, forKey: .localNozzleMaterial) ?? "unknown"
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(host, forKey: .host)
        try c.encode(hostType, forKey: .hostType)
        try c.encode(usesApiKey, forKey: .usesApiKey)
        try c.encode(username, forKey: .username)
        try c.encode(storage, forKey: .storage)
        try c.encode(allowInsecureHttp, forKey: .allowInsecureHttp)
        try c.encode(presetName, forKey: .presetName)
        try c.encode(lightingOptIn, forKey: .lightingOptIn)
        try c.encode(lightingProfile, forKey: .lightingProfile)
        try c.encode(localExperimental, forKey: .localExperimental)
        try c.encode(localHosts, forKey: .localHosts)
        try c.encode(localModel, forKey: .localModel)
        try c.encode(localCapabilities, forKey: .localCapabilities)
        try c.encodeIfPresent(localNozzleDiameter, forKey: .localNozzleDiameter)
        try c.encode(localNozzleMaterial, forKey: .localNozzleMaterial)
    }
}
