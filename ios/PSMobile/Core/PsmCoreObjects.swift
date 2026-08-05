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

    /// Schneidet an einer waagerechten Ebene in Bettkoordinaten.
    ///
    /// keepAsParts legt beide Haelften als Volumen eines Objekts an
    /// statt als getrennte Objekte. Im Simple Mode ist das die
    /// schlechtere Wahl - dort gibt es keinen Objektbaum, in dem man
    /// Teile wiederfaende.
    @discardableResult
    func cut(_ id: Int32,
             zMm: Float,
             keepUpper: Bool = true,
             keepLower: Bool = true,
             keepAsParts: Bool = false) throws -> [Int32] {
        var ids = [psm_object_id](repeating: 0, count: 64)
        var anzahl = size_t(0)
        try check(psm_model_cut_z(raw, id, zMm,
                                  keepUpper ? 1 : 0, keepLower ? 1 : 0,
                                  keepAsParts ? 1 : 0,
                                  &ids, size_t(ids.count), &anzahl),
                  "Schneiden")
        return Array(ids.prefix(Int(anzahl)))
    }

    // MARK: - Variable Schichthoehen

    /// Die Stuetzstellen des Objekts, als Paare aus Hoehe und
    /// Schichtdicke.
    func layerProfile(_ id: Int32) -> [(z: Double, height: Double)] {
        let anzahl = Int(psm_model_layer_profile_count(raw, id))
        guard anzahl > 0 else { return [] }
        return (0..<anzahl).compactMap { i in
            var z = 0.0
            var h = 0.0
            guard psm_model_layer_profile_at(raw, id, size_t(i), &z, &h) == PSM_OK else {
                return nil
            }
            return (z, h)
        }
    }

    /// Setzt die Stuetzstellen. Eine leere Liste raeumt das Profil ab -
    /// das ist die einzige Art, zur festen Schichthoehe zurueckzukommen.
    func setLayerProfile(_ id: Int32, points: [(z: Double, height: Double)]) throws {
        var werte: [Double] = []
        for punkt in points {
            werte.append(punkt.z)
            werte.append(punkt.height)
        }
        try check(psm_model_layer_profile_set(raw, id, werte, size_t(points.count)),
                  "Schichtprofil setzen")
    }

    // MARK: - Mehrere Aufrufe, ein Schritt

    /// Fasst alles bis zum passenden `endHistory` zu einem Undo-Schritt
    /// zusammen.
    ///
    /// Ohne das ist jeder Kern-Aufruf ein eigener Schritt: wer zehn
    /// Objekte auswaehlt und loescht, muss zehnmal zurueck - und weiss
    /// beim dritten Mal nicht mehr, wo er war.
    func beginHistory(_ label: String) {
        _ = psm_history_begin(raw, label)
    }

    func endHistory() {
        _ = psm_history_end(raw)
    }

    // MARK: - Vereinfachen und zerlegen

    /// Reduziert die Dreieckszahl mit PrusaSlicers Quadric-Edge-Collapse.
    ///
    /// Gibt vorher und nachher zurueck - ohne die beiden Zahlen raet
    /// man, ob es etwas gebracht hat.
    @discardableResult
    func simplify(_ id: Int32, ratio: Float) throws -> (before: Int, after: Int) {
        var vorher = UInt32(0)
        var nachher = UInt32(0)
        try check(psm_model_simplify(raw, id, ratio, &vorher, &nachher),
                  "Modell vereinfachen")
        return (Int(vorher), Int(nachher))
    }

    /// Zerlegt getrennte Koerper in einzelne Volumen.
    ///
    /// Eine STL mit mehreren Koerpern ist ein Objekt mit losen Teilen.
    /// Zerlegt bekommt jedes seinen eigenen Extruder.
    @discardableResult
    func splitVolumes(_ id: Int32) throws -> Int {
        var anzahl = size_t(0)
        try check(psm_model_split_volumes(raw, id, &anzahl), "In Volumen teilen")
        return Int(anzahl)
    }

    // MARK: - Hinlegen

    /// Legt das Objekt auf seine groesste ebene Flaeche.
    func layFlatAuto(_ id: Int32) throws {
        try check(psm_model_lay_flat_auto(raw, id), "Flach hinlegen")
    }

    // MARK: - Lage zum Druckraum

    /// Wo ein Objekt relativ zum Druckraum liegt.
    ///
    /// Die Reihenfolge ist die von PrusaSlicers BuildVolume::ObjectState:
    /// je hoeher, desto weniger druckbar.
    enum BedState: UInt32 {
        case inside = 0, colliding = 1, outside = 2, below = 3, unknown = 4
    }

    func bedState(_ id: Int32) -> BedState {
        BedState(rawValue: psm_model_bed_state(raw, id).rawValue) ?? .unknown
    }

    // MARK: - Bemalen

    /// Welches Werkzeug malt. Die Werte kommen aus dem C-ABI.
    enum PaintTool: Int32 {
        case support = 0, seam = 1, fuzzy = 2, mmu = 3
    }

    /// Ein Pinselstrich um ein getroffenes Dreieck.
    ///
    /// Markiert werden nur kantenverbundene, aehnlich ausgerichtete
    /// Facetten innerhalb des Radius - sonst faerbt ein Tippen auf eine
    /// Kante die Rueckseite gleich mit.
    ///
    /// `state`: 0 loescht. Stuetzen und Naht kennen 1 (erzwingen) und
    /// 2 (sperren), MMU die einsbasierte Extrudernummer.
    func paint(_ id: Int32,
               volume: Int,
               facet: Int,
               tool: PaintTool,
               state: Int32,
               radiusMm: Float) throws {
        try check(psm_model_paint_brush(raw, id, size_t(volume), size_t(facet),
                                        psm_paint_tool(rawValue: UInt32(tool.rawValue)),
                                        state, radiusMm),
                  "Bemalen")
    }

    func clearPaint(_ id: Int32, tool: PaintTool) throws {
        try check(psm_model_clear_paint(raw, id,
                                        psm_paint_tool(rawValue: UInt32(tool.rawValue))),
                  "Bemalung loeschen")
    }

    /// Wie viele Facetten dieses Werkzeug markiert hat. Ohne diese Zahl
    /// waere nicht zu sehen, ob ein Strich etwas bewirkt hat.
    func paintCount(_ id: Int32, tool: PaintTool) -> Int {
        Int(psm_model_paint_count(raw, id,
                                  psm_paint_tool(rawValue: UInt32(tool.rawValue))))
    }

    /// Legt die angetippte Flaeche dieser Kopie nach unten und auf Z=0.
    func layOnFacet(_ id: Int32,
                    instance: Int = 0,
                    volume: Int,
                    facet: Int) throws {
        try check(psm_model_lay_on_facet_instance(
                    raw, id, size_t(instance), size_t(volume), size_t(facet)),
                  "Flaeche nach unten legen")
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

    /// Wofuer ein Teil da ist. Die Zahlen sind die der ABI.
    enum VolumeType: UInt32 {
        case modelPart = 0
        case negative = 1
        case modifier = 2
        case supportBlocker = 3
        case supportEnforcer = 4
    }

    enum PrimitiveShape: UInt32 {
        case box = 0
        case cylinder = 1
        case sphere = 2
    }

    /// Legt einen Grundkoerper in die Mitte des Objekts.
    ///
    /// In der Mitte und nicht dort, wo man getippt hat: der Kern kennt
    /// die Tippstelle nicht, und ein Koerper, der halb im Modell steckt,
    /// ist der Anfang, den man mit den Griffen weiterschiebt - so macht
    /// es der Desktop auch.
    @discardableResult
    func addPrimitiveVolume(_ id: Int32,
                            type: VolumeType,
                            shape: PrimitiveShape,
                            sizeX: Float,
                            sizeY: Float,
                            sizeZ: Float) throws -> Int {
        var index = size_t(0)
        try check(psm_model_add_primitive_volume(
                      raw, id,
                      psm_volume_type(rawValue: type.rawValue),
                      psm_primitive_shape(rawValue: shape.rawValue),
                      sizeX, sizeY, sizeZ, &index),
                  "Teil anlegen")
        return Int(index)
    }

    func removeVolume(_ id: Int32, at index: Int) throws {
        try check(psm_model_remove_volume(raw, id, size_t(index)), "Teil entfernen")
    }
}
