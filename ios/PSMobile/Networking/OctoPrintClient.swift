import Foundation
import PSMShared

/// OctoPrint-Anbindung fuer iOS.
///
/// Protokoll aus external/PrusaSlicer/src/slic3r/Utils/OctoPrint.cpp
/// abgelesen (dort wxWidgets/curl-gebunden, hier reines Swift):
///   - Test:    GET  api/version         mit X-Api-Key
///   - Hochladen: POST api/files/local   multipart/form-data:
///                "print"=true/false, "path"=<Zielordner>, "file"=<Datei>
///
/// Anders als PrusaLink (PUT + optional Digest-Auth) kennt OctoPrint nur
/// den API-Schluessel - einfacher, aber ein eigener Client statt eines
/// Zweigs im bestehenden, weil Methode (POST statt PUT) und Kodierung
/// (multipart statt octet-stream) sich unterscheiden, nicht nur die
/// Kopfzeile.
actor OctoPrintClient {

    enum Ergebnis {
        case ok(String)
        case fehler(String)
    }

    private let session: URLSession

    init() {
        let konfig = URLSessionConfiguration.ephemeral
        konfig.timeoutIntervalForRequest = 15
        session = URLSession(configuration: konfig)
    }

    private func baseUrl(_ p: PrusaLinkClient.Printer) -> String {
        let h = p.host.trimmingCharacters(in: .whitespacesAndNewlines)
        let mitSchema = h.contains("://") ? h
            : (p.allowInsecureHttp ? "http://" + h : "https://" + h)
        return mitSchema.hasSuffix("/") ? String(mitSchema.dropLast()) : mitSchema
    }

    func probe(_ p: PrusaLinkClient.Printer, apiKey: String) async -> Ergebnis {
        guard let url = URL(string: baseUrl(p) + "/api/version") else {
            return .fehler(st("Invalid address.", "Ungültige Adresse."))
        }
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "X-Api-Key")
        do {
            let (daten, antwort) = try await session.data(for: request)
            let code = (antwort as? HTTPURLResponse)?.statusCode ?? 0
            if code == 401 || code == 403 {
                return .fehler(st("API key rejected.", "API-Schlüssel abgelehnt."))
            }
            guard code == 200 else {
                return .fehler(st("Server answered with", "Server antwortete mit") + " \(code)")
            }
            let text = String(data: daten, encoding: .utf8) ?? ""
            return .ok(text.count > 120 ? String(text.prefix(120)) + "…" : text)
        } catch {
            // Wie PrusaLink: eine uebersetzte Zeile statt der Systemmeldung
            // (Zwilling: OctoPrintClient.kt, 16.09.2026); das Detail ins Protokoll.
            PsmLog.notiz(PSM_LOG_WARN, "OctoPrint probe fehlgeschlagen: \(error)")
            return .fehler(st("Printer not reachable", "Drucker nicht erreichbar"))
        }
    }

    func upload(_ p: PrusaLinkClient.Printer, apiKey: String,
                datei: URL, name: String, printAfter: Bool) async -> Ergebnis {
        guard let url = URL(string: baseUrl(p) + "/api/files/local") else {
            return .fehler(st("Invalid address.", "Ungültige Adresse."))
        }
        // Ganz im Speicher aufgebaut, anders als PrusaLinkClient.put(),
        // das direkt von der Platte streamt (siehe Kommentar dort zu
        // dreistelligen Megabytes). OctoPrints multipart-Rahmen um die
        // Datei herum macht reines Datei-Streaming unbequemer als beim
        // einfachen PUT - fuer v1 hingenommen, bei sehr grossen G-Codes
        // ein spaeterer Verbesserungspunkt (InputStream-Body).
        guard let inhalt = try? Data(contentsOf: datei) else {
            return .fehler(st("Could not read the file.", "Datei ließ sich nicht lesen."))
        }

        let grenze = "psmobile-" + UUID().uuidString
        var koerper = Data()
        func feld(_ name: String, _ wert: String) {
            koerper.append("--\(grenze)\r\n".data(using: .utf8)!)
            koerper.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n"
                .data(using: .utf8)!)
            koerper.append(wert.data(using: .utf8)!)
            koerper.append("\r\n".data(using: .utf8)!)
        }
        feld("print", printAfter ? "true" : "false")
        feld("path", "")
        koerper.append("--\(grenze)\r\n".data(using: .utf8)!)
        koerper.append(
            "Content-Disposition: form-data; name=\"file\"; filename=\"\(name)\"\r\n"
            .data(using: .utf8)!)
        koerper.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        koerper.append(inhalt)
        koerper.append("\r\n--\(grenze)--\r\n".data(using: .utf8)!)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "X-Api-Key")
        request.setValue("multipart/form-data; boundary=\(grenze)",
                         forHTTPHeaderField: "Content-Type")

        do {
            let (daten, antwort) = try await session.upload(for: request, from: koerper)
            let code = (antwort as? HTTPURLResponse)?.statusCode ?? 0
            if code == 401 || code == 403 {
                return .fehler(st("API key rejected.", "API-Schlüssel abgelehnt."))
            }
            guard code == 200 || code == 201 else {
                let body = String(data: daten, encoding: .utf8) ?? ""
                return .fehler(st("Server answered with", "Server antwortete mit")
                               + " \(code): " + body.prefix(200))
            }
            return .ok(printAfter
                       ? st("Uploaded, printing starts.", "Übertragen, Druck startet.")
                       : st("Uploaded.", "Übertragen."))
        } catch {
            PsmLog.notiz(PSM_LOG_WARN, "OctoPrint upload fehlgeschlagen: \(error)")
            return .fehler(st("Transfer failed", "Übertragung fehlgeschlagen"))
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
