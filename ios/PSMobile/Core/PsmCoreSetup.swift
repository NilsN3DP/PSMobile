import Foundation

/// Ersteinrichtung, Profile und Einstellungen.
///
/// Zweiter Teil der Bruecke zu psmobile_core - der erste Teil in
/// PsmCore.swift kuemmert sich um Modelle und das Slicen. Getrennt, weil
/// die Datei sonst auf tausend Zeilen anwaechst wie ihr Gegenstueck
/// PsmCore.kt auf Android.
///
/// Alle Zeichenketten kommen ueber feste Puffer aus dem C-ABI. Die
/// Groessen stehen im Header und sind hier gespiegelt statt geraten:
/// zu klein waere abgeschnitten, zu gross nur Verschwendung.
extension PsmCore {

    // MARK: - Puffer

    /// Holt eine Zeichenkette aus einer Funktion, die in einen Puffer
    /// schreibt. Das Muster wiederholt sich zwei Dutzend Mal.
    func string(_ capacity: Int = 256,
                        _ call: (UnsafeMutablePointer<CChar>, Int) -> psm_result)
        -> String? {
        var buf = [CChar](repeating: 0, count: capacity)
        let r = buf.withUnsafeMutableBufferPointer { p in
            call(p.baseAddress!, capacity)
        }
        return r == PSM_OK ? String(cString: buf) : nil
    }

    /// C-Zeichenfelder kommen in Swift als Tupel an - der Umweg ueber
    /// withUnsafeBytes macht daraus wieder eine Zeichenkette.
    /// Nicht privat: PsmCoreObjects liest ebenfalls Namensfelder aus
    /// C-Strukturen, und zwei Fassungen derselben vier Zeilen waeren eine
    /// zu viel.
    static func text<T>(_ feld: T) -> String {
        withUnsafeBytes(of: feld) { buf in
            let bytes = buf.bindMemory(to: CChar.self)
            return String(cString: bytes.baseAddress!)
        }
    }

    // MARK: - Ersteinrichtung

    struct PrinterModel {
        let key: String          // "vendor:model"
        let name: String
        let family: String
        let isSla: Bool
        let variants: [String]
    }

    /// Sucht die verfuegbaren Druckermodelle. Muss vor `printerModels`
    /// laufen - der Kern liest dafuer PrusaSlicers Vendor-Dateien.
    @discardableResult
    func scanPrinterModels() throws -> Int {
        var count = 0
        try check(psm_printer_models_scan(raw, &count), "Druckermodelle suchen")
        return count
    }

    func printerModels() -> [PrinterModel] {
        guard let count = try? scanPrinterModels() else { return [] }
        return (0 ..< count).compactMap { index in
            var m = psm_printer_model()
            guard psm_printer_model_at(raw, index, &m) == PSM_OK else { return nil }

            let varianten = (0 ..< Int(m.variant_count)).compactMap { v in
                string(64) { psm_printer_variant_at(self.raw, index, v, $0, $1) }
            }
            let vendor = Self.text(m.vendor_id)
            let model = Self.text(m.model_id)
            return PrinterModel(
                key: "\(vendor):\(model)",
                name: Self.text(m.name),
                family: Self.text(m.family),
                isSla: m.technology == 1,
                variants: varianten,
            )
        }
    }

    /// Installiert genau diese Modelle. Eine leere Liste bedeutet: alles.
    func installPrinters(_ keys: [String]) throws {
        let zeiger = keys.map { strdup($0) }
        defer { zeiger.forEach { free($0) } }
        var c = zeiger.map { UnsafePointer($0) }
        try check(psm_presets_install(raw, &c, c.count), "Drucker einrichten")
    }

    // MARK: - Profile

    func presetCount(_ type: PresetType) -> Int {
        psm_preset_count(raw, cType(type))
    }

    func presetNames(_ type: PresetType) -> [String] {
        (0 ..< presetCount(type)).compactMap { i in
            string { psm_preset_name_at(self.raw, self.cType(type), i, $0, $1) }
        }
    }

    /// Ein Wert aus einem benannten Preset, ohne es auszuwaehlen.
    ///
    /// Fuer Uebersichten: die Materialauswahl braucht von jedem
    /// Filamentprofil Typ und Farbe. Ueber die Auswahl zu gehen hiesse,
    /// fuer jede Zeile die ganze Konfiguration umzubauen.
    func presetOption(_ type: PresetType, _ name: String, _ key: String) -> String? {
        string { psm_preset_option_at(self.raw, self.cType(type), name, key, $0, $1) }
    }

    func selectedPreset(_ type: PresetType) -> String? {
        string { psm_preset_selected(self.raw, self.cType(type), $0, $1) }
    }

    func selectPreset(_ type: PresetType, _ name: String) throws {
        try check(psm_preset_select(raw, cType(type), name), "Profil waehlen")
    }

    /// Ob ein Profil zum eingerichteten Drucker passt.
    ///
    /// Unpassende werden nicht versteckt, sondern gekennzeichnet - wer
    /// ein Material sucht, das der Kern fuer unpassend haelt, soll sehen,
    /// dass es existiert, statt es fuer verschwunden zu halten.
    func presetCompatible(_ type: PresetType, at index: Int) -> Bool {
        var flag: Int32 = 0
        guard psm_preset_compatible_at(raw, cType(type), index, &flag) == PSM_OK
        else { return true }
        return flag != 0
    }

    var showsIncompatiblePresets: Bool {
        get { psm_preset_shows_incompatible(raw) != 0 }
        set { psm_preset_show_incompatible(raw, newValue ? 1 : 0) }
    }

    /// Eine ungespeicherte Aenderung im gewaehlten Profil.
    ///
    /// Mit altem *und* neuem Wert: der Hinweis soll nicht nur sagen, dass
    /// sich etwas geaendert hat, sondern was. PrusaSlicer zeigt es auf
    /// dem Desktop genauso.
    struct DirtyValue {
        let key: String
        let oldValue: String
        let newValue: String
    }

    func dirtyValues(_ type: PresetType) -> [DirtyValue] {
        let n = psm_preset_dirty_count(raw, cType(type))
        return (0 ..< n).compactMap { i in
            var key = [CChar](repeating: 0, count: 64)
            var alt = [CChar](repeating: 0, count: 512)
            var neu = [CChar](repeating: 0, count: 512)
            let r = psm_preset_dirty_at(raw, cType(type), i,
                                        &key, 64, &alt, 512, &neu, 512)
            guard r == PSM_OK else { return nil }
            return DirtyValue(key: String(cString: key),
                              oldValue: String(cString: alt),
                              newValue: String(cString: neu))
        }
    }

    func discardChanges(_ type: PresetType) throws {
        try check(psm_preset_discard(raw, cType(type)), "Aenderungen verwerfen")
    }

    func savePreset(_ type: PresetType, as name: String) throws {
        try check(psm_preset_save_as(raw, cType(type), name), "Profil speichern")
    }

    // MARK: - Einstellungen

    enum ConfigType: Int32 { case bool = 0, int, float, string, enumeration, points, percent }
    enum ConfigMode: Int32 { case simple = 0, advanced, expert }

    struct ConfigMeta {
        let key: String
        let label: String
        let category: String
        let tooltip: String
        let unit: String
        let type: ConfigType
        let mode: ConfigMode
        let min: Float?
        let max: Float?
        let enumCount: Int
    }

    func configMeta(for key: String) -> ConfigMeta? {
        var m = psm_config_meta()
        guard psm_config_meta_for(raw, key, &m) == PSM_OK else { return nil }
        return ConfigMeta(
            key: Self.text(m.key),
            label: Self.text(m.label),
            category: Self.text(m.category),
            tooltip: Self.text(m.tooltip),
            unit: Self.text(m.unit),
            type: ConfigType(rawValue: Int32(m.type.rawValue)) ?? .string,
            mode: ConfigMode(rawValue: Int32(m.mode.rawValue)) ?? .expert,
            min: m.has_min != 0 ? m.min : nil,
            max: m.has_max != 0 ? m.max : nil,
            enumCount: Int(m.enum_count),
        )
    }

    /// Ein Wert einer Auswahlliste.
    ///
    /// Wert und Beschriftung sind verschieden: in die Konfiguration geht
    /// "rectilinear", auf den Bildschirm "Rectilinear" beziehungsweise
    /// dessen Uebersetzung. Wer beides verwechselt, schreibt unlesbare
    /// Werte in die Profile.
    struct EnumValue {
        let value: String
        let label: String
    }

    func configEnumValues(_ key: String, count: Int) -> [EnumValue] {
        (0 ..< count).compactMap { i in
            var wert = [CChar](repeating: 0, count: 128)
            var text = [CChar](repeating: 0, count: 256)
            let r = psm_config_enum_value_at(raw, key, i, &wert, 128, &text, 256)
            guard r == PSM_OK else { return nil }
            return EnumValue(value: String(cString: wert),
                             label: String(cString: text))
        }
    }

    func config(_ key: String) -> String? {
        string(1024) { psm_config_get(self.raw, key, $0, $1) }
    }

    func setConfig(_ key: String, _ value: String) throws {
        try check(psm_config_set(raw, key, value), "Einstellung setzen")
    }

    /// Ob eine Einstellung gerade wirksam ist - und wenn nicht, warum.
    ///
    /// PrusaSlicer graut Felder aus, die von anderen Einstellungen
    /// abhaengen. Der Grund kommt mit, damit die Oberflaeche ihn zeigen
    /// kann statt nur ein totes Feld.
    func configEnabled(_ key: String) -> (enabled: Bool, reason: String) {
        var buf = [CChar](repeating: 0, count: 256)
        let on = buf.withUnsafeMutableBufferPointer { p in
            psm_config_enabled(raw, key, p.baseAddress!, 256)
        }
        return (on != 0, String(cString: buf))
    }

    // MARK: - Extruder und Filament

    var extruderCount: Int { Int(psm_extruder_count(raw)) }

    func extruderFilament(_ extruder: Int) -> String? {
        string { psm_extruder_filament_get(self.raw, Int32(extruder), $0, $1) }
    }

    func setExtruderFilament(_ extruder: Int, _ name: String) throws {
        try check(psm_extruder_filament_set(raw, Int32(extruder), name),
                  "Filament setzen")
    }

    func extruderColor(_ extruder: Int) -> String? {
        string(32) { psm_extruder_color_get(self.raw, Int32(extruder), $0, $1) }
    }

    func setExtruderColor(_ extruder: Int, _ hex: String) throws {
        try check(psm_extruder_color_set(raw, Int32(extruder), hex), "Farbe setzen")
    }

    /// Position und Drehung des Reinigungsturms - nur bei mehreren
    /// Extrudern von Belang, siehe ExtruderBank.
    func wipeTower() -> (x: Float, y: Float, rotationDeg: Float)? {
        var x: Float = 0, y: Float = 0, rot: Float = 0
        guard psm_wipe_tower_get(raw, &x, &y, &rot) == PSM_OK else { return nil }
        return (x, y, rot)
    }

    func setWipeTower(x: Float, y: Float, rotationDeg: Float) throws {
        try check(psm_wipe_tower_set(raw, x, y, rotationDeg), "Reinigungsturm setzen")
    }

    struct FilamentVendor {
        let name: String
        let filamentCount: Int
        let enabled: Bool
    }

    func filamentVendors() -> [FilamentVendor] {
        let n = psm_filament_vendor_count(raw)
        return (0 ..< n).compactMap { i in
            var buf = [CChar](repeating: 0, count: 128)
            var count: Int32 = 0
            var enabled: Int32 = 0
            let r = buf.withUnsafeMutableBufferPointer { p in
                psm_filament_vendor_at(raw, i, p.baseAddress!, 128, &count, &enabled)
            }
            guard r == PSM_OK else { return nil }
            return FilamentVendor(name: String(cString: buf),
                                  filamentCount: Int(count),
                                  enabled: enabled != 0)
        }
    }

    func setFilamentVendors(_ names: [String]) throws {
        let zeiger = names.map { strdup($0) }
        defer { zeiger.forEach { free($0) } }
        var c = zeiger.map { UnsafePointer($0) }
        try check(psm_filament_vendors_set(raw, &c, c.count), "Hersteller waehlen")
    }
}
