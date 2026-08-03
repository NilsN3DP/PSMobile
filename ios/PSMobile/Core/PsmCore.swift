import Foundation
import os

/// Swift-Seite der Bruecke zu psmobile_core.
///
/// Gegenstueck zu `de.psmobile.core.PsmCore` auf Android - bewusst mit
/// derselben Struktur, damit sich Verhalten zwischen beiden Plattformen
/// vergleichen laesst.
///
/// Der 3D-Viewport laeuft NICHT ueber diese Klasse. Siehe
/// docs/entscheidungen.md, E-03.
final class PsmCore {

    enum PsmError: Error, LocalizedError {
        case createFailed(String)
        case call(String, Int32, String)

        var errorDescription: String? {
            switch self {
            case .createFailed(let m):        return "Session liess sich nicht anlegen: \(m)"
            case .call(let what, let c, let m): return "\(what) fehlgeschlagen (\(c)): \(m)"
            }
        }
    }

    enum PresetType: Int32 { case print = 0, filament = 1, printer = 2 }

    enum SliceState { case idle, running, done, failed, cancelled }

    struct ObjectInfo {
        let id: Int32
        let name: String
        var position: SIMD3<Float>
        var rotation: SIMD3<Float>
        var scale: SIMD3<Float>
        let sizeMm: SIMD3<Float>
        let triangles: Int
        let instances: Int
        let outsideBed: Bool
    }

    struct SliceStats {
        let printTimeSeconds: Double
        let filamentMm: Double
        let filamentGrams: Double
        let cost: Double
        let objects: Int
    }

    private static let log = Logger(subsystem: "de.psmobile", category: "core")

    private var handle: OpaquePointer?

    /// Kotlin nutzt einen Listener, Swift eine Closure. Sie muss ueber
    /// einen unmanaged Zeiger durch das C-ABI getragen werden, weil
    /// C-Funktionszeiger nichts einfangen koennen.
    private final class ProgressBox {
        let callback: (Int, String) -> Bool
        init(_ cb: @escaping (Int, String) -> Bool) { self.callback = cb }
    }
    private var progressBox: ProgressBox?

    static var coreVersion: String { String(cString: psm_core_version()) }

    init(dataDir: String, resourceDir: String) throws {
        let abi = psm_abi_version()
        guard abi == Int32(PSM_ABI_VERSION) else {
            throw PsmError.createFailed("ABI-Bruch: Bibliothek \(abi), App \(PSM_ABI_VERSION)")
        }
        guard let h = psm_session_create(dataDir, resourceDir) else {
            throw PsmError.createFailed(String(cString: psm_last_error(nil)))
        }
        // psm_session_create liefert bereits einen OpaquePointer.
        handle = h
        Self.log.info("Kern \(Self.coreVersion) bereit")
    }

    deinit {
        if let h = handle {
            psm_slice_cancel(h)
            psm_session_destroy(h)
        }
    }

    /// Swift bildet unvollstaendige C-Typen wie psm_session als
    /// OpaquePointer ab - einen UnsafeMutablePointer darauf gibt es nicht.
    /// Genau so ist das ABI auch gemeint: der Zeiger wird durchgereicht,
    /// nie dereferenziert.
    /// Der rohe Sitzungszeiger fuer den Viewport. Der laeuft bewusst
    /// nicht ueber diese Klasse, sondern liest das Modell direkt aus der
    /// Session - siehe docs/entscheidungen.md, E-03. Dafuer braucht er
    /// den Zeiger.
    var sessionHandle: OpaquePointer? { handle }

    private var raw: OpaquePointer {
        handle!
    }

    var lastError: String {
        // psm_last_error nimmt void*, nicht psm_session* - es soll auch
        // ohne Session aufrufbar sein, wenn das Anlegen fehlgeschlagen ist.
        handle == nil ? "" : String(cString: psm_last_error(UnsafeMutableRawPointer(handle!)))
    }

    private func check(_ code: psm_result, _ what: String) throws {
        guard code == PSM_OK else { throw PsmError.call(what, code.rawValue, lastError) }
    }

    // MARK: - Modelle

    @discardableResult
    func loadModel(path: String) throws -> [Int32] {
        var ids = [psm_object_id](repeating: 0, count: 256)
        var count = 0
        let r = ids.withUnsafeMutableBufferPointer { buf in
            psm_model_load(raw, path, buf.baseAddress, 256, &count)
        }
        try check(r, "Modell laden")
        return Array(ids.prefix(min(count, 256)))
    }

    func removeObject(_ id: Int32) throws {
        try check(psm_model_remove(raw, id), "Objekt entfernen")
    }

    func listObjects() -> [Int32] {
        var ids = [psm_object_id](repeating: 0, count: 256)
        var count = 0
        let r = ids.withUnsafeMutableBufferPointer { buf in
            psm_model_list(raw, buf.baseAddress, 256, &count)
        }
        guard r == PSM_OK else { return [] }
        return Array(ids.prefix(min(count, 256)))
    }

    func objectInfo(_ id: Int32) -> ObjectInfo? {
        var info = psm_object_info()
        guard psm_model_info(raw, id, &info) == PSM_OK else { return nil }

        let name = withUnsafeBytes(of: &info.name) { buf -> String in
            let p = buf.bindMemory(to: CChar.self)
            return String(cString: p.baseAddress!)
        }
        let mn = SIMD3<Float>(info.bbox_min.0, info.bbox_min.1, info.bbox_min.2)
        let mx = SIMD3<Float>(info.bbox_max.0, info.bbox_max.1, info.bbox_max.2)

        return ObjectInfo(
            id: id,
            name: name,
            position: SIMD3(info.position.0, info.position.1, info.position.2),
            rotation: SIMD3(info.rotation.0, info.rotation.1, info.rotation.2),
            scale:    SIMD3(info.scale.0, info.scale.1, info.scale.2),
            sizeMm:   mx - mn,
            triangles: Int(info.triangle_count),
            instances: Int(info.instance_count),
            outsideBed: info.outside_bed != 0
        )
    }

    func setPosition(_ id: Int32, _ p: SIMD3<Float>) throws {
        try check(psm_model_set_position(raw, id, p.x, p.y, p.z), "Verschieben")
    }

    func setRotation(_ id: Int32, _ r: SIMD3<Float>) throws {
        try check(psm_model_set_rotation(raw, id, r.x, r.y, r.z), "Drehen")
    }

    func setScale(_ id: Int32, _ s: SIMD3<Float>) throws {
        try check(psm_model_set_scale(raw, id, s.x, s.y, s.z), "Skalieren")
    }

    func dropToBed(_ id: Int32) throws {
        try check(psm_model_drop_to_bed(raw, id), "Aufs Bett legen")
    }

    // MARK: - Presets

    func loadBundledPresets() throws {
        try check(psm_presets_load_bundled(raw), "Profile laden")
    }

    func presetNames(_ type: PresetType) -> [String] {
        let n = psm_preset_count(raw, psm_preset_type(rawValue: UInt32(type.rawValue)))
        return (0..<n).map { i in
            var buf = [CChar](repeating: 0, count: 256)
            _ = psm_preset_name_at(raw, psm_preset_type(rawValue: UInt32(type.rawValue)),
                                   i, &buf, 256)
            return String(cString: buf)
        }
    }

    func selectPreset(_ type: PresetType, _ name: String) throws {
        try check(psm_preset_select(raw, psm_preset_type(rawValue: UInt32(type.rawValue)), name),
                  "Preset waehlen")
    }

    // MARK: - Konfiguration

    subscript(key: String) -> String? {
        get {
            var buf = [CChar](repeating: 0, count: 4096)
            guard psm_config_get(raw, key, &buf, 4096) == PSM_OK else { return nil }
            return String(cString: buf)
        }
        set {
            guard let v = newValue else { return }
            _ = psm_config_set(raw, key, v)
        }
    }

    // MARK: - Slicing

    /// - Parameter onProgress: laeuft auf dem Slice-Thread, nicht auf dem
    ///   Main-Actor. Rueckgabe `true` bricht ab.
    func startSlice(onProgress: @escaping (Int, String) -> Bool) throws {
        let box = ProgressBox(onProgress)
        progressBox = box

        let trampoline: @convention(c) (Int32, UnsafePointer<CChar>?, UnsafeMutableRawPointer?) -> Int32 = {
            percent, stage, user in
            guard let user else { return 0 }
            let b = Unmanaged<ProgressBox>.fromOpaque(user).takeUnretainedValue()
            let text = stage.map { String(cString: $0) } ?? ""
            return b.callback(Int(percent), text) ? 1 : 0
        }

        let r = psm_slice_start(raw, trampoline, Unmanaged.passUnretained(box).toOpaque())
        try check(r, "Slice-Start")
    }

    func cancelSlice() {
        guard handle != nil else { return }
        psm_slice_cancel(raw)
    }

    var sliceState: SliceState {
        switch psm_slice_state_get(raw) {
        case PSM_STATE_RUNNING:   return .running
        case PSM_STATE_DONE:      return .done
        case PSM_STATE_FAILED:    return .failed
        case PSM_STATE_CANCELLED: return .cancelled
        default:                  return .idle
        }
    }

    /// Blockiert. Nur aus einem Hintergrund-Task aufrufen.
    @discardableResult
    func awaitSlice() -> SliceState {
        _ = psm_slice_wait(raw, -1)
        progressBox = nil
        return sliceState
    }

    func sliceStats() -> SliceStats? {
        var st = psm_slice_stats()
        guard psm_slice_stats_get(raw, &st) == PSM_OK else { return nil }
        return SliceStats(
            printTimeSeconds: st.print_time_seconds,
            filamentMm: st.filament_used_mm,
            filamentGrams: st.filament_used_g,
            cost: st.filament_cost,
            objects: Int(st.object_count)
        )
    }

    func exportGcode(to path: String) throws {
        try check(psm_gcode_export(raw, path), "G-Code-Export")
    }

    /// Geschaetzter Spitzenspeicher in Bytes.
    ///
    /// Auf iOS besonders wichtig: es gibt keinen zweiten Prozess, in den
    /// man das Slicing auslagern koennte. Wenn Jetsam zuschlaegt, ist die
    /// ganze App weg. Deshalb vorher gegen `os_proc_available_memory()`
    /// pruefen und lieber warnen.
    var estimatedSliceMemory: UInt64 { psm_estimate_slice_memory(raw) }

    /// Wieviel Speicher dieser Prozess noch bekommen darf, oder nichts.
    ///
    /// os_proc_available_memory liefert 0, wenn es keine Auskunft gibt -
    /// im Simulator etwa, wo es keine Jetsam-Grenze gibt. Die Null heisst
    /// also "unbekannt" und nicht "kein Speicher". Wer sie als Zahl
    /// weiterreicht, warnt bei jedem Modell vor 0 MB.
    static var availableMemory: UInt64? {
        let v = os_proc_available_memory()
        return v > 0 ? UInt64(v) : nil
    }
}
