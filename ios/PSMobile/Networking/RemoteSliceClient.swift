import Foundation
import PSMShared

/// Anbindung an den selbstgehosteten Remote-Slice-Server (Docker, siehe
/// docs/remote-slicing.md).
///
/// Bewusst schmal: das Projekt (.3mf, mit eingebettetem Drucker-/
/// Filament-/Druckprofil) geht als roher Dateikoerper hoch, keine
/// Mehrteil-Kodierung - die serverseitige Handler-Schleife
/// (`docker/remote-slice/server.py`) liest einfach `Content-Length`
/// Bytes vom Sockel. Einfacher auf beiden Seiten als multipart/form-data
/// fuer einen einzigen Dateikoerper ohne weitere Formularfelder.
actor RemoteSliceClient {

    struct JobStats: Codable, Equatable {
        var seconds: Double?
        var layerCount: Int?
        var maxZ: Double?
        var printTimeSeconds: Double?
        var filamentMm: Double?
        var filamentG: Double?

        enum CodingKeys: String, CodingKey {
            case seconds
            case layerCount = "layer_count"
            case maxZ = "max_z"
            case printTimeSeconds = "print_time_seconds"
            case filamentMm = "filament_mm"
            case filamentG = "filament_g"
        }
    }

    struct JobState: Codable, Equatable {
        var id: String
        var status: String
        var percent: Int?
        var stage: String?
        var error: String?
        var stats: JobStats?
        var objectCount: Int?
        var bedCount: Int?
        var printer: String?

        enum CodingKeys: String, CodingKey {
            case id, status, percent, stage, error, stats
            case objectCount = "object_count"
            case bedCount = "bed_count"
            case printer
        }

        var isDone: Bool { status == "done" }
        var isFailed: Bool { status == "failed" }
    }

    enum RemoteSliceError: LocalizedError {
        case ungueltigeAdresse
        case serverAntwortet(Int)
        case unerwarteteAntwort

        var errorDescription: String? {
            switch self {
            case .ungueltigeAdresse:
                return SimpleModeState.shared.text(
                    english: "Invalid server address.",
                    german: "Ungültige Serveradresse.")
            case .serverAntwortet(let code):
                if code == 401 || code == 403 {
                    return SimpleModeState.shared.text(
                        english: "Server rejected the access token.",
                        german: "Server hat das Zugangs-Token abgelehnt.")
                }
                return SimpleModeState.shared.text(
                    english: "Server responded with \(code).",
                    german: "Server antwortete mit \(code).")
            case .unerwarteteAntwort:
                return SimpleModeState.shared.text(
                    english: "Unexpected server response.",
                    german: "Unerwartete Antwort vom Server.")
            }
        }
    }

    private let session: URLSession

    init() {
        let konfig = URLSessionConfiguration.ephemeral
        // 15s war fuer den Health-Check gedacht, traf aber auch Hoch-
        // und Herunterladen: ein groesseres Projekt oder ein groesserer
        // G-Code auf einer normalen (nicht Gigabit-)Verbindung braucht
        // oft laenger, ganz ohne dass der Server je langsam waere -
        // Nils meldete genau das ("Slice on server: Timeout").
        // timeoutIntervalForRequest bleibt fuer einzelne Datenpakete
        // (greift bei einer Verbindung, die komplett steht), waehrend
        // timeoutIntervalForResource die GESAMTE Uebertragung begrenzt -
        // das ist der Wert, der bei einem grossen Datei-Transfer zaehlt.
        konfig.timeoutIntervalForRequest = 30
        konfig.timeoutIntervalForResource = 180
        session = URLSession(configuration: konfig)
    }

    /// Aus einer Nutzereingabe wie "192.168.1.50:8420" oder
    /// "https://slicer.example.com" eine Basis-URL machen. Ohne Schema
    /// wird HTTP angenommen - fuers Heimnetz. Fuer eine Adresse im
    /// offenen Internet gehoert ausdruecklich https:// davor, siehe
    /// docs/remote-slicing.md.
    static func normalizedBaseURL(from eingabe: String) -> URL? {
        let getrimmt = eingabe.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !getrimmt.isEmpty else { return nil }
        let mitSchema = getrimmt.contains("://") ? getrimmt : "http://\(getrimmt)"
        guard var teile = URLComponents(string: mitSchema),
              teile.host != nil else { return nil }
        teile.path = ""
        return teile.url
    }

    private func angereichert(_ request: inout URLRequest, token: String?) {
        if let token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }

    /// Erreichbarkeit pruefen, bevor ein ganzes Projekt hochgeladen wird.
    func healthCheck(baseURL: URL, token: String?) async -> Bool {
        var request = URLRequest(url: baseURL.appendingPathComponent("health"))
        angereichert(&request, token: token)
        // Kurz: eine Erreichbarkeitspruefung darf nicht 30 s stumm bleiben.
        // Der lange Wert der Session bleibt fuer die eigentlichen Auftraege.
        request.timeoutInterval = 5
        do {
            let (_, antwort) = try await session.data(for: request)
            guard let http = antwort as? HTTPURLResponse else {
                PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice healthCheck: keine HTTP-Antwort von \(baseURL)")
                return false
            }
            if http.statusCode != 200 {
                let ohneToken = token == nil || token?.isEmpty == true
                PsmLog.notiz(PSM_LOG_WARN,
                            "RemoteSlice healthCheck: HTTP \(http.statusCode) von \(baseURL)"
                            + (ohneToken ? " (kein Token gesetzt)" : ""))
            }
            return http.statusCode == 200
        } catch {
            PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice healthCheck fehlgeschlagen (\(baseURL)): \(error)")
            return false
        }
    }

    /// Laedt das Projekt hoch und liefert die Job-Kennung.
    func submitJob(projectFileURL: URL, baseURL: URL, token: String?) async throws -> String {
        var request = URLRequest(url: baseURL.appendingPathComponent("jobs"))
        request.httpMethod = "POST"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        angereichert(&request, token: token)

        do {
            let (data, antwort) = try await session.upload(
                for: request, fromFile: projectFileURL)
            guard let http = antwort as? HTTPURLResponse else {
                PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice submitJob: keine HTTP-Antwort von \(baseURL)")
                throw RemoteSliceError.unerwarteteAntwort
            }
            guard http.statusCode == 202 else {
                let text = String(data: data, encoding: .utf8) ?? "keine Antwortdaten"
                PsmLog.notiz(PSM_LOG_WARN,
                            "RemoteSlice submitJob: HTTP \(http.statusCode) von \(baseURL) - \(text)")
                throw RemoteSliceError.serverAntwortet(http.statusCode)
            }
            struct Angelegt: Codable { let id: String }
            let angelegt = try JSONDecoder().decode(Angelegt.self, from: data)
            return angelegt.id
        } catch let fehler as RemoteSliceError {
            throw fehler
        } catch {
            PsmLog.notiz(PSM_LOG_ERROR, "RemoteSlice submitJob fehlgeschlagen (\(baseURL)): \(error)")
            throw error
        }
    }

    /// Einmaliger Statusabruf - der Aufrufer entscheidet ueber das
    /// Poll-Intervall (siehe SlicerModel.remotePollLoop), nicht der
    /// Client selbst.
    func fetchStatus(jobId: String, baseURL: URL, token: String?) async throws -> JobState {
        var request = URLRequest(
            url: baseURL.appendingPathComponent("jobs").appendingPathComponent(jobId))
        angereichert(&request, token: token)
        do {
            let (data, antwort) = try await session.data(for: request)
            guard let http = antwort as? HTTPURLResponse else {
                PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice fetchStatus(\(jobId)): keine HTTP-Antwort")
                throw RemoteSliceError.unerwarteteAntwort
            }
            guard http.statusCode == 200 else {
                PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice fetchStatus(\(jobId)): HTTP \(http.statusCode)")
                throw RemoteSliceError.serverAntwortet(http.statusCode)
            }
            return try JSONDecoder().decode(JobState.self, from: data)
        } catch let fehler as RemoteSliceError {
            throw fehler
        } catch {
            PsmLog.notiz(PSM_LOG_ERROR, "RemoteSlice fetchStatus(\(jobId)) fehlgeschlagen: \(error)")
            throw error
        }
    }

    /// Laedt den fertigen G-Code in eine lokale Datei.
    func downloadGcode(jobId: String, baseURL: URL, token: String?, to zielURL: URL) async throws {
        var request = URLRequest(
            url: baseURL.appendingPathComponent("jobs")
                .appendingPathComponent(jobId).appendingPathComponent("gcode"))
        angereichert(&request, token: token)
        do {
            let (temp, antwort) = try await session.download(for: request)
            guard let http = antwort as? HTTPURLResponse, http.statusCode == 200 else {
                let code = (antwort as? HTTPURLResponse)?.statusCode ?? -1
                PsmLog.notiz(PSM_LOG_WARN, "RemoteSlice downloadGcode(\(jobId)): HTTP \(code)")
                throw RemoteSliceError.serverAntwortet(code)
            }
            try? FileManager.default.removeItem(at: zielURL)
            try FileManager.default.moveItem(at: temp, to: zielURL)
        } catch let fehler as RemoteSliceError {
            throw fehler
        } catch {
            PsmLog.notiz(PSM_LOG_ERROR, "RemoteSlice downloadGcode(\(jobId)) fehlgeschlagen: \(error)")
            throw error
        }
    }
}
