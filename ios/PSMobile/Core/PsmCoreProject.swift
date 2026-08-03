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
