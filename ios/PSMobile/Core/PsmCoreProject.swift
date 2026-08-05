import Foundation

/// Projekte als 3MF und die Schrittfolge zum Zuruecknehmen.
///
/// Beides lag im C-ABI bereit und war auf der iOS-Seite nicht
/// durchgereicht. Ohne Projekte gibt es nichts, was einen App-Start
/// ueberlebt - man laedt seine Modelle jedes Mal neu und stellt alles
/// noch einmal ein.
extension PsmCore {

    // MARK: - Projekte

    /// Was beim Oeffnen eines Projekts geschehen ist.
    ///
    /// Wichtig sind die Faelle, in denen der Kern etwas anderes
    /// aktiviert hat als im Projekt stand - dann druckt man sonst mit
    /// einem Profil, das man nicht gewaehlt hat.
    struct ProjectImport {
        let configLoaded: Bool
        /// Eingebettete Post-Processing-Skripte werden aus
        /// Sicherheitsgruenden nie uebernommen. Der Nutzer soll trotzdem
        /// erfahren, dass welche drin waren.
        let postProcessRemoved: Bool
        let objectCount: Int
        let bedCount: Int
        let requestedPrinter: String
        let selectedPrinter: String
        let requestedPrint: String
        let selectedPrint: String

        var printerChanged: Bool {
            !requestedPrinter.isEmpty && requestedPrinter != selectedPrinter
        }
        var printChanged: Bool {
            !requestedPrint.isEmpty && requestedPrint != selectedPrint
        }
    }

    @discardableResult
    func loadProject(path: String) throws -> ProjectImport {
        var info = psm_project_import_info()
        try check(psm_project_load_3mf(raw, path, &info), "Projekt oeffnen")
        return ProjectImport(
            configLoaded: info.config_loaded != 0,
            postProcessRemoved: info.post_process_removed != 0,
            objectCount: Int(info.object_count),
            bedCount: Int(info.bed_count),
            requestedPrinter: Self.text(info.requested_printer),
            selectedPrinter: Self.text(info.selected_printer),
            requestedPrint: Self.text(info.requested_print),
            selectedPrint: Self.text(info.selected_print)
        )
    }

    func saveProject(path: String) throws {
        try check(psm_project_save_3mf(raw, path), "Projekt sichern")
    }

    /// Der Dateiname, den PrusaSlicer fuer dieses Ergebnis vergaebe.
    ///
    /// Nicht selbst zusammengesetzt: `output_filename_format` steht im
    /// Druckprofil und enthaelt Modellname, Schichthoehe, Material,
    /// Drucker und Druckzeit. Bisher hiess hier jede Datei
    /// psmobile.gcode - wer drei Varianten auf den Stick legt, findet
    /// keine wieder.
    func suggestedGcodeName() -> String {
        string { psm_gcode_suggested_name(self.raw, $0, $1) } ?? "psmobile.gcode"
    }

    // MARK: - Custom G-code

    /// Ein Eintrag: auf dieser Hoehe passiert etwas.
    struct CustomGcode {
        /// In Millimetern, nicht in Schichten. PrusaSlicer fuehrt es so,
        /// und eine Schichtnummer waere nach jeder Aenderung der
        /// Schichthoehe eine andere Stelle.
        var printZ: Double
        var type: Kind
        var extruder: Int32
        var color: String
        var extra: String

        enum Kind: UInt32 {
            case colorChange = 0, pause = 1, toolChange = 2, template = 3, code = 4
        }
    }

    func customGcodeList() -> [CustomGcode] {
        let anzahl = Int(psm_custom_gcode_count(raw))
        guard anzahl > 0 else { return [] }
        return (0..<anzahl).compactMap { i in
            var c = psm_custom_gcode()
            guard psm_custom_gcode_at(raw, size_t(i), &c) == PSM_OK else { return nil }
            return CustomGcode(printZ: c.print_z,
                               type: CustomGcode.Kind(rawValue: c.type.rawValue) ?? .code,
                               extruder: c.extruder,
                               color: Self.text(c.color),
                               extra: Self.text(c.extra))
        }
    }

    private func roh(_ e: CustomGcode) -> psm_custom_gcode {
        var c = psm_custom_gcode()
        c.print_z = e.printZ
        c.type = psm_custom_gcode_type(rawValue: e.type.rawValue)
        c.extruder = e.extruder
        withUnsafeMutableBytes(of: &c.color) { ziel in
            let bytes = Array(e.color.utf8.prefix(31))
            ziel.copyBytes(from: bytes)
        }
        withUnsafeMutableBytes(of: &c.extra) { ziel in
            let bytes = Array(e.extra.utf8.prefix(1023))
            ziel.copyBytes(from: bytes)
        }
        return c
    }

    func addCustomGcode(_ e: CustomGcode) throws {
        var c = roh(e)
        try check(psm_custom_gcode_add(raw, &c), "Custom G-code hinzufuegen")
    }

    func updateCustomGcode(_ index: Int, _ e: CustomGcode) throws {
        var c = roh(e)
        try check(psm_custom_gcode_update(raw, size_t(index), &c), "Custom G-code aendern")
    }

    func removeCustomGcode(_ index: Int) throws {
        try check(psm_custom_gcode_remove(raw, size_t(index)), "Custom G-code entfernen")
    }

    func clearCustomGcode() throws {
        try check(psm_custom_gcode_clear(raw), "Custom G-code leeren")
    }

    /// Repariert eine STL und schreibt sie neu.
    ///
    /// libslic3r/admesh flickt offene Kanten und verdrehte Normalen
    /// beim Einlesen - aber nur, wenn jemand danach fragt.
    func repairSTL(input: String, output: String) throws {
        try check(psm_stl_repair(raw, input, output), "STL reparieren")
    }

    /// Wandelt G-Code zwischen ASCII und Prusas binaerem BGCode.
    ///
    /// Neuere Drucker lesen binaer, aeltere nur ASCII. Wer eine Datei
    /// fuer den falschen hat, dreht sie um statt neu zu schneiden.
    func convertGcode(input: String, output: String, toBinary: Bool) throws {
        try check(psm_gcode_convert(raw, input, output, toBinary ? 1 : 0),
                  "G-Code wandeln")
    }

    func exportPlateSTL(path: String) throws {
        try check(psm_plate_export_stl(raw, path), "Platte als STL")
    }

    // MARK: - Zuruecknehmen und wiederholen

    var undoCount: Int { Int(psm_history_undo_count(raw)) }
    var redoCount: Int { Int(psm_history_redo_count(raw)) }

    /// Die Beschriftung des naechsten Schritts - "Objekt entfernt",
    /// "Skaliert". Ein Zurueck-Knopf, der nicht sagt, was er zuruecknimmt,
    /// wird nur zoegernd benutzt.
    var undoLabel: String {
        var puffer = [CChar](repeating: 0, count: 256)
        guard psm_history_undo_label(raw, &puffer, 256) == PSM_OK else { return "" }
        return String(cString: puffer)
    }

    var redoLabel: String {
        var puffer = [CChar](repeating: 0, count: 256)
        guard psm_history_redo_label(raw, &puffer, 256) == PSM_OK else { return "" }
        return String(cString: puffer)
    }

    func undo() throws { try check(psm_history_undo(raw), "Zurueck") }
    func redo() throws { try check(psm_history_redo(raw), "Wiederholen") }
}
