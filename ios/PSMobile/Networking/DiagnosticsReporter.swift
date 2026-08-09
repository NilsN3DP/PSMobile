import Foundation
import UIKit
import PSMShared

/// Freiwilliger Testbericht nach dem Slicen - geht an denselben Server
/// wie das Remote Slicing (docker/remote-slice/server.py, `/diagnostics`),
/// derselbe Host, derselbe Token. Zwei getrennte Schalter in den
/// Einstellungen (siehe AppSettings.kt):
///
///   KEY_DIAG_AUTO_UPLOAD  - fuer Testversionen: Geraet, Zeiten UND ein
///                           Screenshot bei jedem Slice.
///   KEY_TELEMETRY_ANON    - fuer spaetere Nutzer: dieselben Zahlen,
///                           ausdruecklich ohne Bild und ohne Projektnamen.
///
/// Beide sind standardmaessig aus. Ein Fehlschlag beim Senden darf nie
/// auffallen - das hier ist Beiwerk, kein Teil des Slice-Vorgangs.
enum DiagnosticsReporter {

    struct Ergebnis {
        var erfolgreich: Bool
        var sekunden: Double
        var dreiecke: Int
        var weg: String   // "local" oder "remote"
        var fehler: String?
    }

    /// Wie nachSlice(), aber fuer einen Selbsttest-Lauf ohne Slice
    /// davor oder danach - siehe Selbsttest.starten(). Dieselben zwei
    /// Schalter, dieselbe "aus heisst aus"-Regel; nur die Felder sind
    /// andere (kein "weg"/"dreiecke", dafuer der Bericht direkt statt
    /// nachtraeglich von der Platte gelesen).
    @MainActor static func nachSelbsttest(bericht: String, fehlerZahl: Int, warnungZahl: Int) {
        let autoTest = UserDefaults.standard.bool(forKey: AppSettings.shared.KEY_DIAG_AUTO_UPLOAD)
        let anonym = UserDefaults.standard.bool(forKey: AppSettings.shared.KEY_TELEMETRY_ANON)
        guard autoTest || anonym else { return }
        let sendeAnonym = anonym && !autoTest

        Task.detached(priority: .background) {
            var eintrag: [String: Any] = [
                "app_version": Self.appVersion(),
                "os_version": UIDevice.current.systemVersion,
                "geraet": Self.geraetekennung(),
                "ram_bytes": ProcessInfo.processInfo.physicalMemory,
                "erfolgreich": fehlerZahl == 0,
                "fehler_anzahl": fehlerZahl,
                "warnung_anzahl": warnungZahl,
                "weg": "selbsttest",
                "anonym": sendeAnonym,
            ]
            if !sendeAnonym {
                eintrag["selbsttest"] = bericht
                let log = PsmLog.recent
                if !log.isEmpty { eintrag["protokoll"] = log }
            }
            await Self.sendenRoh(eintrag)
        }
    }

    @MainActor static func nachSlice(_ ergebnis: Ergebnis, projektname: String?) {
        let autoTest = UserDefaults.standard.bool(forKey: AppSettings.shared.KEY_DIAG_AUTO_UPLOAD)
        let anonym = UserDefaults.standard.bool(forKey: AppSettings.shared.KEY_TELEMETRY_ANON)
        guard autoTest || anonym else { return }

        // Screenshot nur im Testmodus, nie bei der anonymen Telemetrie -
        // das ist der ganze Sinn der Trennung.
        let bild: UIImage? = autoTest ? Self.bildschirmfoto() : nil

        Task.detached(priority: .background) {
            await Self.senden(ergebnis, projektname: projektname,
                              anonym: anonym && !autoTest, bild: bild)
        }
    }

    @MainActor
    private static func bildschirmfoto() -> UIImage? {
        guard let fenster = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
            .first else { return nil }
        let renderer = UIGraphicsImageRenderer(bounds: fenster.bounds)
        return renderer.image { _ in
            fenster.drawHierarchy(in: fenster.bounds, afterScreenUpdates: false)
        }
    }

    private static func senden(_ ergebnis: Ergebnis, projektname: String?,
                               anonym: Bool, bild: UIImage?) async {
        // Kein Server eingetragen: gar nicht erst den (teuren, mit
        // Screenshot und Protokoll gefuellten) Eintrag zusammenbauen.
        guard RemoteSliceClient.normalizedBaseURL(
            from: UserDefaults.standard.string(forKey: SlicerModel.remoteSliceHostKey) ?? "") != nil
        else { return }

        var eintrag: [String: Any] = [
            "app_version": Self.appVersion(),
            "os_version": UIDevice.current.systemVersion,
            "geraet": Self.geraetekennung(),
            "ram_bytes": ProcessInfo.processInfo.physicalMemory,
            "erfolgreich": ergebnis.erfolgreich,
            "sekunden": ergebnis.sekunden,
            "dreiecke": ergebnis.dreiecke,
            "weg": ergebnis.weg,
            "anonym": anonym,
        ]
        if !anonym {
            if let projektname { eintrag["projekt"] = projektname }
            if let fehler = ergebnis.fehler { eintrag["fehler"] = fehler }
            if let bild, let png = bild.pngData() {
                eintrag["screenshot_png_base64"] = png.base64EncodedString()
            }
            // Mehr Daten heisst hier: nicht nur die eine Fehlermeldung,
            // sondern der Weg dahin. Der Ringpuffer traegt, was PsmLog
            // seit Sitzungsbeginn an Warnungen/Fehlern gesammelt hat -
            // dieselbe Quelle wie beim Protokoll-Export in den
            // Einstellungen, hier nur automatisch statt manuell.
            let log = PsmLog.recent
            if !log.isEmpty { eintrag["protokoll"] = log }
            // Der letzte Selbsttest laeuft nicht bei jedem Slice mit -
            // das waere Minuten pro Schnitt -, aber falls schon einer
            // auf der Platte liegt (manuell gestartet, oder per
            // -psm-selbsttest-auto), kostet das Anhaengen nichts.
            if let bericht = Self.letzterSelbsttest() {
                eintrag["selbsttest"] = bericht
            }
        }

        await Self.sendenRoh(eintrag)
    }

    // Hier verschluckte bis heute Nacht ein "try?" jeden Fehlschlag
    // spurlos - ein 401 wegen fehlendem Token sah von aussen genauso
    // aus wie "kein Server eingetragen, gar nicht erst versucht". Jede
    // Stufe jetzt einzeln geloggt, damit ein kuenftiger Fehlschlag im
    // naechsten Protokoll-Export sofort auffaellt statt erst nach einer
    // eigens eingebauten Debug-Runde.
    private static func sendenRoh(_ eintrag: [String: Any]) async {
        let hostEinstellung = UserDefaults.standard.string(forKey: SlicerModel.remoteSliceHostKey) ?? ""
        guard let basis = RemoteSliceClient.normalizedBaseURL(from: hostEinstellung) else {
            PsmLog.notiz(PSM_LOG_WARN,
                        "Diagnose-Upload uebersprungen: keine gueltige Serveradresse (\"\(hostEinstellung)\")")
            return
        }
        let token = try? RemoteSliceCredentialStore().load()
        guard let koerper = try? JSONSerialization.data(withJSONObject: eintrag) else {
            PsmLog.notiz(PSM_LOG_WARN, "Diagnose-Upload uebersprungen: Eintrag liess sich nicht kodieren")
            return
        }
        var request = URLRequest(url: basis.appendingPathComponent("diagnostics"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            PsmLog.notiz(PSM_LOG_WARN, "Diagnose-Upload an \(basis) ohne Token - Server lehnt das vermutlich ab")
        }
        request.httpBody = koerper
        do {
            let (daten, antwort) = try await URLSession.shared.data(for: request)
            let code = (antwort as? HTTPURLResponse)?.statusCode ?? -1
            if code < 200 || code >= 300 {
                let text = String(data: daten, encoding: .utf8) ?? "keine Antwortdaten"
                PsmLog.notiz(PSM_LOG_WARN, "Diagnose-Upload an \(basis): HTTP \(code) - \(text)")
            }
        } catch {
            PsmLog.notiz(PSM_LOG_ERROR, "Diagnose-Upload an \(basis) fehlgeschlagen: \(error)")
        }
    }

    private static func appVersion() -> String {
        let kurz = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(kurz) (\(build))"
    }

    /// Neuester Bericht unter Documents/Selbsttest, falls vorhanden -
    /// nach Dateiname sortiert, der traegt den Zeitstempel schon im
    /// Namen (siehe Selbsttest.berichtsdatei()).
    private static func letzterSelbsttest() -> String? {
        let ordner = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Selbsttest", isDirectory: true)
        guard let dateien = try? FileManager.default.contentsOfDirectory(
            at: ordner, includingPropertiesForKeys: nil) else { return nil }
        guard let neueste = dateien.filter({ $0.pathExtension == "md" })
            .sorted(by: { $0.lastPathComponent > $1.lastPathComponent }).first else { return nil }
        return try? String(contentsOf: neueste, encoding: .utf8)
    }

    private static func geraetekennung() -> String {
        var groesse = 0
        sysctlbyname("hw.machine", nil, &groesse, nil, 0)
        var zeichen = [CChar](repeating: 0, count: groesse)
        sysctlbyname("hw.machine", &zeichen, &groesse, nil, 0)
        return String(cString: zeichen)
    }
}
