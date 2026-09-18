import Foundation
import PSMShared

/// Zugriff auf die aus PrusaSlicer uebernommene Oberflaechen-Definition.
///
/// Gegenstueck zu `PsUi` auf Android. Die Daten unter `Resources/psui`
/// erzeugt `build/scripts/extract-ui.py` aus dem Original - Seiten aus
/// `Tab.cpp`, Beschriftungen aus den `.po`-Dateien. Nichts davon ist
/// abgetippt (E-12).
///
/// Ausgewertet wird die Seitenstruktur vom gemeinsamen Modul
/// (`TabsCatalog`), damit beide Apps dieselben Entscheidungen treffen.
/// Hier steht nur, wie die Dateien gefunden und gelesen werden - und das
/// ist auf jeder Plattform anders.
enum PsUiCatalog {

    private(set) static var tabs: [String: [TabsCatalog.Page]] = [:]
    private(set) static var language = "en"

    /// Beschriftungen aus PrusaSlicers eigenem Katalog, englisch als
    /// Schluessel. Fehlt eine Uebersetzung, bleibt das Englische stehen -
    /// besser als eine leere Zeile.
    private static var strings: [String: String] = [:]

    private static var root: URL? {
        Bundle.main.resourceURL?.appendingPathComponent("psui")
    }

    static func load(language lang: String = "en") {
        language = lang
        Lang.shared.current = lang

        guard let root else {
            NSLog("PsUiCatalog: psui fehlt im Bundle")
            return
        }

        if let text = try? String(contentsOf: root.appendingPathComponent("tabs.json"),
                                  encoding: .utf8) {
            tabs = TabsCatalog.shared.parse(text: text)
        } else {
            NSLog("PsUiCatalog: tabs.json nicht lesbar")
        }

        // Englisch ist PrusaSlicers Quellsprache - dafuer gibt es keine
        // Datei, und es braucht auch keine.
        strings = [:]
        if lang != "en",
           let daten = try? Data(contentsOf: root.appendingPathComponent("lang_\(lang).json")),
           let roh = try? JSONSerialization.jsonObject(with: daten) as? [String: String] {
            strings = roh
        }
    }

    /// Uebersetzt eine Beschriftung aus PrusaSlicers Katalog.
    static func tr(_ english: String) -> String {
        strings[english] ?? english
    }

    /// Seiten eines Bereichs, bereits auf die Duesenzahl vervielfacht.
    static func pages(_ tab: String, extruderCount: Int) -> [TabsCatalog.Page] {
        guard let seiten = tabs[tab] else { return [] }
        return TabsCatalog.shared.expand(pages: seiten,
                                         extruderCount: Int32(extruderCount))
    }
}
