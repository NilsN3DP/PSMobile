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
        let name: String
        let locked: Bool
    }

    enum ArrangeStatus {
        case arranged
        case empty
    }

    struct ArrangeResult {
        let status: ArrangeStatus
        let objectCount: Int
        let instanceCount: Int
    }

    /// Die Betten des Projekts. Mindestens eines - ein Projekt ohne
    /// Druckbett gibt es nicht.
    func beds() -> [Bed] {
        let anzahl = psm_bed_count(raw)
        guard anzahl > 0 else { return [] }
        let aktiv = psm_bed_active(raw)
        return (0..<anzahl).map { i in
            var metadata = psm_bed_metadata()
            let hatMetadaten = psm_bed_metadata_get(raw, i, &metadata) == PSM_OK
            let name = hatMetadaten
                ? withUnsafeBytes(of: &metadata.name) { puffer -> String in
                    let zeichen = puffer.bindMemory(to: CChar.self)
                    return String(cString: zeichen.baseAddress!)
                }
                : ""
            return Bed(index: Int(i),
                       objectCount: Int(psm_bed_object_count(raw, i)),
                       active: i == aktiv,
                       name: name,
                       locked: hatMetadaten && metadata.locked != 0)
        }
    }

    func setBedMetadata(_ bed: Bed, name: String? = nil,
                        locked: Bool? = nil) throws {
        var metadata = psm_bed_metadata()
        let sauber = (name ?? bed.name).trimmingCharacters(in: .whitespacesAndNewlines)
        withUnsafeMutableBytes(of: &metadata.name) { puffer in
            puffer.initializeMemory(as: UInt8.self, repeating: 0)
            let bytes = Array(sauber.utf8.prefix(puffer.count - 1))
            puffer.copyBytes(from: bytes)
        }
        metadata.locked = (locked ?? bed.locked) ? 1 : 0
        try check(psm_bed_metadata_set(raw, size_t(bed.index), &metadata),
                  "Bett-Metadaten setzen")
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

    /// Ordnet genau das angegebene Bett an und reicht fachliche Fehler
    /// (gesperrt, voll) unverändert aus dem Kern weiter.
    func arrange(bed index: Int, gapMm: Float = 6,
                allowRotation: Bool = false) throws -> ArrangeResult {
        var info = psm_arrange_info()
        let code = psm_arrange_bed_ex(raw, size_t(index), gapMm,
                                      allowRotation ? 1 : 0, &info)
        try check(code, "Anordnen")
        let status: ArrangeStatus =
            info.status == PSM_ARRANGE_EMPTY ? .empty : .arranged
        return ArrangeResult(status: status,
                             objectCount: Int(info.object_count),
                             instanceCount: Int(info.instance_count))
    }

    /// Bestehende Diagnosepfade ordnen weiterhin das aktive Bett an.
    /// Die sichtbare Oberfläche verwendet die explizite Zielbett-Fassung.
    func arrange(gapMm: Float = 6) throws {
        _ = try arrange(bed: Int(psm_bed_active(raw)), gapMm: gapMm)
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

    /// Berechnet Stuetzstellen aus der Objektgeometrie, PrusaSlicers
    /// eigener SlicingAdaptive-Algorithmus. Setzt noch nichts - der
    /// Aufrufer zeigt das Ergebnis erst in der Vorschau, wie beim
    /// manuellen Profil auch.
    func layerProfileAdaptive(_ id: Int32,
                              qualityFactor: Float) throws -> [(z: Double, height: Double)] {
        var anzahl: size_t = 0
        try check(psm_model_layer_profile_adaptive(raw, id, qualityFactor, nil, 0, &anzahl),
                  "Adaptives Schichtprofil berechnen")
        guard anzahl > 0 else { return [] }
        var werte = [Double](repeating: 0, count: Int(anzahl) * 2)
        try werte.withUnsafeMutableBufferPointer { puffer in
            try check(psm_model_layer_profile_adaptive(
                raw, id, qualityFactor, puffer.baseAddress, anzahl, &anzahl),
                "Adaptives Schichtprofil lesen")
        }
        return stride(from: 0, to: werte.count, by: 2).map { (werte[$0], werte[$0 + 1]) }
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

    enum PaintMode: Int32, CaseIterable {
        case brush = 0, smartFill = 1, bucketFill = 2
    }

    enum PaintShape: Int32, CaseIterable {
        case circle = 0, sphere = 1
    }

    /// Einziger Optionszustand fuer Bedienung, Kernaufruf und Viewport.
    struct PaintOptions: Equatable {
        var tool: PaintTool?
        var state: Int32 = 1
        var mode: PaintMode = .brush
        var shape: PaintShape = .sphere
        var radiusMm: Float = 5
        var fillAngleDeg: Float = 30
        var splitTriangles = true

        var supportedModes: [PaintMode] {
            switch tool {
            case .support: return [.brush, .smartFill]
            case .seam, .fuzzy: return [.brush]
            case .mmu: return [.brush, .smartFill, .bucketFill]
            case nil: return []
            }
        }

        mutating func normalizeForTool() {
            if !supportedModes.contains(mode) {
                mode = .brush
            }
            if tool != .mmu && state > 2 {
                state = 1
            }
        }

        func cOptions(hit: (Float, Float, Float),
                      previous: (Float, Float, Float)? = nil)
            -> psm_paint_options {
            var result = psm_paint_options()
            result.version = UInt32(PSM_PAINT_OPTIONS_VERSION_1)
            result.mode = psm_paint_mode(
                rawValue: UInt32(mode.rawValue))
            result.shape = psm_paint_shape(
                rawValue: UInt32(shape.rawValue))
            result.radius_mm = radiusMm
            result.fill_angle_deg = fillAngleDeg
            result.split_triangles = splitTriangles ? 1 : 0
            result.has_previous_position = previous == nil ? 0 : 1
            result.hit_position = hit
            result.previous_position = previous ?? (0, 0, 0)
            return result
        }
    }

    /// Treffer und Instanz werden unveraendert an TriangleSelector gereicht.
    /// Persistiert bleibt nur die gemeinsame Annotation des Volumens.
    func paint(_ id: Int32,
               instance: Int,
               volume: Int,
               facet: Int,
               hit: (Float, Float, Float),
               previous: (Float, Float, Float)?,
               options: PaintOptions) throws {
        guard let tool = options.tool else { return }
        var cOptions = options.cOptions(hit: hit, previous: previous)
        try check(psm_model_paint_apply(
                    raw, id, size_t(instance), size_t(volume), size_t(facet),
                    psm_paint_tool(rawValue: UInt32(tool.rawValue)),
                    options.state, &cOptions),
                  "Bemalen")
    }

    /// Bestehende Diagnose-Aufrufer bleiben ABI-kompatibel; die
    /// Bedienoberflaeche verwendet ausschliesslich den Optionsweg oben.
    func paint(_ id: Int32,
               volume: Int,
               facet: Int,
               tool: PaintTool,
               state: Int32,
               radiusMm: Float) throws {
        try check(psm_model_paint_brush(
                    raw, id, size_t(volume), size_t(facet),
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
