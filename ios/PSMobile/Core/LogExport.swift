import Foundation
import UIKit

/// Ein Protokoll, das den Neustart nach einem Absturz uebersteht - und
/// eine Datei daraus, die sich teilen laesst.
///
/// `OSLogStore` waere die naheliegende Quelle, liest auf iOS ohne
/// besondere Berechtigung aber nur das laufende Verfahren
/// (`.currentProcessIdentifier`) - nach einem Absturz ist genau das
/// verschwunden, sobald der neue Prozess startet. Deshalb schreibt
/// `PsmLog` zusaetzlich in eine eigene Datei, die den Prozesswechsel
/// uebersteht.
enum LogExport {

    private static var logURL: URL {
        let verzeichnis = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(
            at: verzeichnis, withIntermediateDirectories: true)
        return verzeichnis.appendingPathComponent("psmobile.log")
    }

    /// Ab dieser Groesse faengt die Datei wieder von vorn an - ein
    /// Protokoll ist ein Blick zurueck, kein Archiv. 512 KB sind bei
    /// kurzen Kernzeilen mehrere Tausend Eintraege.
    private static let maxBytes = 512 * 1024

    private static let sperre = NSLock()

    /// Eine Zeile anhaengen - wird von PsmLog fuer jede Warnung/jeden
    /// Fehler aufgerufen, zusaetzlich zum Ringpuffer im Speicher.
    static func append(_ zeile: String) {
        sperre.lock(); defer { sperre.unlock() }
        let mitZeitstempel = "\(ISO8601DateFormatter().string(from: Date())) \(zeile)\n"
        guard let daten = mitZeitstempel.data(using: .utf8) else { return }

        let url = logURL
        if let groesse = try? FileManager.default.attributesOfItem(atPath: url.path)[.size]
            as? Int, groesse > maxBytes {
            try? FileManager.default.removeItem(at: url)
        }
        if let handle = try? FileHandle(forWritingTo: url) {
            defer { try? handle.close() }
            handle.seekToEndOfFile()
            handle.write(daten)
        } else {
            try? daten.write(to: url)
        }
    }

    /// Kopf mit App-Version und Geraeteangaben - keine Kontodaten, keine
    /// Netzwerkadressen, nichts, was eine Person identifiziert. Das
    /// macht "anonym senden" ohne weiteren Aufwand zutreffend.
    private static func kopf() -> String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"]
            as? String ?? "?"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return """
        PSMobile \(version) (\(build))
        \(UIDevice.current.systemName) \(UIDevice.current.systemVersion), \
        \(UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone")

        """
    }

    /// Eine teilbare Datei mit Kopf plus Protokoll - fuer den
    /// Export-Knopf in den App-Einstellungen und die Absturzfrage beim
    /// naechsten Start.
    static func exportFile() -> URL? {
        let inhalt = kopf() + "\n" + ((try? String(contentsOf: logURL, encoding: .utf8)) ?? "")
        let ziel = FileManager.default.temporaryDirectory
            .appendingPathComponent("psmobile-protokoll.txt")
        try? FileManager.default.removeItem(at: ziel)
        do {
            try inhalt.write(to: ziel, atomically: true, encoding: .utf8)
            return ziel
        } catch {
            return nil
        }
    }
}

/// Grobe Absturzerkennung ohne eigenes SDK: beim Start wird ein Merker
/// gesetzt, beim sauberen Wechsel in den Hintergrund wieder geloescht.
/// Steht er beim naechsten Start noch, ist die App zuletzt nicht ueber
/// den normalen Weg beendet worden - das schliesst auch ein hartes
/// Beenden per Wischgeste ein, nicht nur einen Absturz, aber die Frage
/// "Protokoll senden?" schadet in beiden Faellen nicht.
enum CrashHeuristic {
    private static let key = "diag.unclean-session"

    static var letzteSitzungUnsauberBeendet: Bool {
        UserDefaults.standard.bool(forKey: key)
    }

    static func sitzungBeginnt() {
        UserDefaults.standard.set(true, forKey: key)
    }

    static func sitzungSauberBeendet() {
        UserDefaults.standard.set(false, forKey: key)
    }
}
