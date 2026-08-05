import Foundation

/// Swift-Seite des Viewports - Gegenstueck zu
/// `de.psmobile.core.PsmViewport` auf Android, mit denselben Namen.
///
/// Der Viewport laeuft bewusst nicht ueber `PsmCore`: er liest das Modell
/// direkt aus der Session, ohne Umweg ueber das ABI. Siehe
/// docs/entscheidungen.md, E-03.
///
/// **Alle Aufrufe gehoeren in den GL-Thread.** Das ist keine Empfehlung -
/// die Funktionen fassen GL-Objekte an, und ein Kontext gehoert immer
/// genau einem Thread. Auf Android sorgt `queueEvent` dafuer, hier der
/// Aufrufer in `PSMGLView`.
final class PsmViewport {

    enum ViewPreset: Int32 { case iso = 0, top, front, back, left, right }
    enum Mode: Int32 { case editor = 0, preview = 1 }
    enum Gizmo: Int32 { case none = 0, move, rotate, scale }

    struct SurfaceHit {
        let objectId: Int32
        let volumeIndex: Int32
        let facetIndex: Int32
        let instanceIndex: Int32
        /// Ort und Normale des getroffenen Dreiecks, in Millimetern.
        let position: (Float, Float, Float)
        let normal: (Float, Float, Float)
    }

    private let handle: OpaquePointer

    /// Legt den Viewport an. Muss im GL-Thread mit gueltigem Kontext
    /// geschehen - er uebersetzt dabei die Shader.
    init?(session: OpaquePointer, shaderDir: String) {
        guard let h = psm_viewport_create(session, shaderDir) else { return nil }
        self.handle = h
    }

    deinit { psm_viewport_destroy(handle) }

    var lastError: String {
        String(cString: psm_viewport_last_error(handle))
    }

    func resize(width: Int32, height: Int32) {
        psm_viewport_resize(handle, width, height)
    }

    func render() { psm_viewport_render(handle) }

    /// Sagt dem Viewport, dass sich das Modell geaendert hat. Ohne das
    /// zeichnet er weiter den alten Stand - ein skaliertes Objekt bliebe
    /// in seiner alten Groesse stehen.
    func invalidate() { psm_viewport_invalidate(handle) }

    func orbit(dx: Float, dy: Float) { psm_viewport_orbit(handle, dx, dy) }
    func pan(dx: Float, dy: Float)   { psm_viewport_pan(handle, dx, dy) }
    func zoom(_ factor: Float)       { psm_viewport_zoom(handle, factor) }
    func resetView()                 { psm_viewport_reset_view(handle) }

    func setView(_ v: ViewPreset) { psm_viewport_view_preset(handle, v.rawValue) }

    func pick(x: Float, y: Float) -> Int32 { psm_viewport_pick(handle, x, y) }

    func setSelection(_ id: Int32) { psm_viewport_set_selection(handle, id) }

    func setSelections(_ ids: [Int32], primary: Int32) {
        var buf = ids
        psm_viewport_set_selections(handle, &buf, buf.count, primary)
    }

    func surfacePick(x: Float, y: Float) -> SurfaceHit? {
        var hit = psm_surface_hit()
        guard psm_viewport_pick_surface(handle, x, y, &hit) != 0 else { return nil }
        // C-Felder kommen in Swift als Tupel an, nicht als Array.
        return SurfaceHit(objectId: hit.object_id,
                          volumeIndex: hit.volume_index,
                          facetIndex: hit.facet_index,
                          instanceIndex: hit.instance_index,
                          position: hit.position,
                          normal: hit.normal)
    }

    @discardableResult
    func dragSelected(fromX: Float, fromY: Float, toX: Float, toY: Float) -> Bool {
        psm_viewport_drag_selected(handle, fromX, fromY, toX, toY) != 0
    }

    /// Meldet den Anfang einer Geste - danach setzt der Kern genau
    /// einen Wiederherstellungspunkt.
    func gestureBegin() { psm_viewport_gesture_begin(handle) }

    @discardableResult
    func scaleSelected(_ factor: Float) -> Bool {
        psm_viewport_scale_selected(handle, factor) != 0
    }

    // C-Enums kommen in Swift als Struct mit UInt32 an, unsere eigenen
    // rechnen in Int32. Deshalb an beiden Enden ausdruecklich umwandeln
    // statt sich auf eine stillschweigende Anpassung zu verlassen.
    var mode: Mode {
        get { Mode(rawValue: Int32(psm_viewport_get_mode(handle).rawValue)) ?? .editor }
        set { psm_viewport_set_mode(handle, psm_view_mode(rawValue: UInt32(newValue.rawValue))) }
    }

    var gizmo: Gizmo {
        get { Gizmo(rawValue: Int32(psm_viewport_get_gizmo(handle).rawValue)) ?? .none }
        set { psm_viewport_set_gizmo(handle, psm_gizmo_mode(rawValue: UInt32(newValue.rawValue))) }
    }

    /// Welcher Griff unter dem Finger liegt, oder -1.
    func gizmoPick(x: Float, y: Float, radius: Float) -> Int32 {
        psm_viewport_gizmo_pick(handle, x, y, radius)
    }

    @discardableResult
    /// - Parameter snap: auf sinnvolle Schritte rasten, 15 Grad und 1 mm.
    ///   Auf dem Tablet standardmaessig an: freihaendig genau zu treffen
    ///   ist mit dem Finger nicht zu machen.
    func gizmoDrag(axis: Int32, fromX: Float, fromY: Float,
                   toX: Float, toY: Float, snap: Bool = true) -> Bool {
        psm_viewport_gizmo_drag(handle, axis, fromX, fromY, toX, toY,
                                snap ? 1 : 0) != 0
    }

    @discardableResult
    func loadPreview() -> Bool { psm_viewport_load_preview(handle) != 0 }

    var layerCount: Int32 { psm_viewport_layer_count(handle) }

    func setLayerRange(first: Int32, last: Int32) {
        psm_viewport_set_layer_range(handle, first, last)
    }
}
