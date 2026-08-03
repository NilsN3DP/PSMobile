import Foundation

/// Objekte auf dem Bett, Betten und die Werkzeuge dazu.
///
/// Bisher konnte die iOS-Seite Modelle nur laden, ansehen und
/// entfernen. Alles, was man mit einem Objekt sonst tut - anordnen,
/// klonen, spiegeln, auf ein anderes Bett schieben, auf Bettgroesse
/// bringen - lag im C-ABI bereit und war nur nicht durchgereicht.
///
/// Wie im uebrigen Kern gilt: hier steht nichts, was der Kern nicht
/// selbst entscheidet. Die Funktionen reichen durch und wandeln
/// C-Typen in Swift-Typen, mehr nicht.
extension PsmCore {

    // MARK: - Betten

    struct Bed {
        let index: Int
        let objectCount: Int
        let active: Bool
    }

    /// Die Betten des Projekts. Mindestens eines - ein Projekt ohne
    /// Druckbett gibt es nicht.
    func beds() -> [Bed] {
        let anzahl = psm_bed_count(raw)
        guard anzahl > 0 else { return [] }
        let aktiv = psm_bed_active(raw)
        return (0..<anzahl).map { i in
            Bed(index: Int(i),
                objectCount: Int(psm_bed_object_count(raw, i)),
                active: i == aktiv)
        }
    }

    func selectBed(_ index: Int) throws {
        try check(psm_bed_select(raw, size_t(index)), "Bett waehlen")
    }

    @discardableResult
    func addBed() throws -> Int {
        var index = size_t(0)
        try check(psm_bed_add(raw, &index), "Bett anlegen")
        return Int(index)
    }

    func removeBed(_ index: Int) throws {
        try check(psm_bed_remove(raw, size_t(index)), "Bett entfernen")
    }

    func clearBed() throws {
        try check(psm_bed_clear(raw), "Bett leeren")
    }

    /// Schiebt ein Objekt auf ein anderes Bett. Es bekommt dort eine
    /// neue Kennung - dieselbe Geometrie, aber ein anderer Platz.
    @discardableResult
    func moveToBed(_ id: Int32, target: Int) throws -> Int32 {
        var neu: psm_object_id = 0
        try check(psm_bed_move_object(raw, id, size_t(target), &neu),
                  "Objekt auf anderes Bett schieben")
        return neu
    }

    // MARK: - Objektwerkzeuge

    /// Ordnet alles auf dem Bett neu an. Blockierend, aber typisch unter
    /// einer Sekunde - ein Fortschrittsbalken dafuer waere laenger zu
    /// sehen als der Vorgang dauert.
    func arrange(gapMm: Float = 6) throws {
        try check(psm_arrange(raw, gapMm), "Anordnen")
    }

    @discardableResult
    func duplicate(_ id: Int32) throws -> Int32 {
        var neu: psm_object_id = 0
        try check(psm_model_duplicate(raw, id, &neu), "Klonen")
        return neu
    }

    /// 0 = X, 1 = Y, 2 = Z.
    func mirror(_ id: Int32, axis: Int32) throws {
        try check(psm_model_mirror(raw, id, axis), "Spiegeln")
    }

    /// Zahl der Kopien. PrusaSlicer nennt das Instanzen: dieselbe
    /// Geometrie mehrfach platziert, ohne den Speicher zu vervielfachen.
    func setInstances(_ id: Int32, count: Int32) throws {
        try check(psm_model_set_instances(raw, id, count), "Kopien setzen")
    }

    /// Skaliert so, dass die groesste Kante genau `sizeMm` misst.
    func scaleToFit(_ id: Int32, sizeMm: Float) throws {
        try check(psm_model_scale_to_fit(raw, id, sizeMm), "Auf Groesse bringen")
    }

    /// Skaliert und zentriert auf das aktuelle Bett. `fillRatio` ist der
    /// genutzte Anteil von Breite und Tiefe.
    func fitToBed(_ id: Int32, fillRatio: Float = 0.9) throws {
        try check(psm_model_fit_to_bed(raw, id, fillRatio), "Auf das Bett bringen")
    }

    /// Zerlegt getrennte Koerper in eigene Objekte und liefert deren
    /// Kennungen. Der Puffer ist grosszuegig: ein zerlegtes Modell hat
    /// selten mehr als eine Handvoll Teile, und ein zu kleiner Puffer
    /// wuerde den Rest stillschweigend verlieren.
    @discardableResult
    func splitObject(_ id: Int32) throws -> [Int32] {
        var ids = [psm_object_id](repeating: 0, count: 256)
        var anzahl = size_t(0)
        try check(psm_model_split_objects(raw, id, &ids, size_t(ids.count), &anzahl),
                  "Zerlegen")
        return Array(ids.prefix(Int(anzahl)))
    }

    // MARK: - Extruder je Objekt

    /// Der Extruder eines Objekts. 0 heisst: der Standard des Profils.
    func objectExtruder(_ id: Int32) -> Int32 {
        psm_model_extruder_get(raw, id)
    }

    func setObjectExtruder(_ id: Int32, _ extruder: Int32) throws {
        try check(psm_model_extruder_set(raw, id, extruder), "Extruder setzen")
    }

    // MARK: - Teile eines Objekts

    struct VolumeInfo {
        let index: Int32
        let type: Int32
        let name: String
        let triangles: UInt32
        /// Wirksamer Extruder - 0 heisst: vom Objekt geerbt.
        let extruder: Int32
        let explicitExtruder: Int32
    }

    func volumeCount(_ id: Int32) -> Int {
        Int(psm_model_volume_count(raw, id))
    }

    func volumeInfo(_ id: Int32, at index: Int) -> VolumeInfo? {
        var info = psm_volume_info()
        guard psm_model_volume_info(raw, id, size_t(index), &info) == PSM_OK else { return nil }
        return VolumeInfo(
            index: info.index,
            type: Int32(info.type.rawValue),
            name: Self.text(info.name),
            triangles: info.triangle_count,
            extruder: info.extruder,
            explicitExtruder: info.explicit_extruder
        )
    }

    func setVolumeExtruder(_ id: Int32, at index: Int, _ extruder: Int32) throws {
        try check(psm_model_volume_extruder_set(raw, id, size_t(index), extruder),
                  "Extruder eines Teils setzen")
    }
}
