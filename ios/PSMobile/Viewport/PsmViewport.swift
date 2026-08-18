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
    enum PreviewView: Int32 { case feature = 0, extruder = 1 }
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

    struct LayerVisualization: Equatable {
        let minHeight: Float
        let maxHeight: Float
    }

    struct PaintVisualization: Equatable {
        let cursorVisible: Bool
        let annotationVisible: Bool
        let annotationFacets: Int
        let mode: PsmCore.PaintMode
        let shape: PsmCore.PaintShape
        let radiusMm: Float
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

    /// Erst nach dem Rendern vorhanden: damit kann die Oberfläche nicht
    /// versehentlich ein Profil behaupten, dessen Shader oder GL-Textur
    /// gar nicht aufgebaut wurde.
    var activeLayerVisualization: LayerVisualization? {
        var info = psm_layer_visualization_info()
        guard psm_viewport_active_layer_visualization(handle, &info) != 0 else {
            return nil
        }
        return LayerVisualization(
            minHeight: info.min_layer_height,
            maxHeight: info.max_layer_height)
    }

    var activePaintVisualization: PaintVisualization? {
        var info = psm_paint_visualization_info()
        guard psm_viewport_active_paint_visualization(
                handle, &info) != 0,
              let mode = PsmCore.PaintMode(rawValue: info.mode),
              let shape = PsmCore.PaintShape(rawValue: info.shape)
        else { return nil }
        return PaintVisualization(
            cursorVisible: info.cursor_visible != 0,
            annotationVisible: info.annotation_visible != 0,
            annotationFacets: Int(info.annotation_facets),
            mode: mode, shape: shape, radiusMm: info.radius_mm)
    }

    func setPaintOptions(_ options: PsmCore.PaintOptions?) {
        guard let options, let tool = options.tool else {
            psm_viewport_set_paint_options(
                handle, 0, psm_paint_tool(rawValue: 0), nil)
            return
        }
        var cOptions = options.cOptions(hit: (0, 0, 0))
        psm_viewport_set_paint_options(
            handle, 1,
            psm_paint_tool(rawValue: UInt32(tool.rawValue)),
            &cOptions)
    }

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

    /// Schliesst ein Ziehen ab. Liegt das Objekt jetzt ueber einem
    /// anderen Bett, gehoert es danach auch dorthin - und behaelt dabei
    /// die Stelle, an der der Finger es abgesetzt hat.
    /// - Returns: die neue Objektkennung, wenn das Bett gewechselt hat.
    func dropSelected() -> Int32? {
        var neu: psm_object_id = PSM_INVALID_ID
        guard psm_viewport_drop_selected(handle, &neu) != 0 else { return nil }
        return Int32(neu)
    }

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

    struct GizmoScreenAxis {
        let from: CGPoint
        let to: CGPoint
    }

    /// Dieselbe projizierte Achse, die der Viewport beim Greifen benutzt.
    /// UIKit braucht sie, damit VoiceOver und XCUITest den unsichtbaren
    /// GL-Inhalt an seiner wirklichen Bildschirmposition beschreiben.
    func gizmoScreenAxis(_ axis: Int32) -> GizmoScreenAxis? {
        var screen = psm_gizmo_screen_axis()
        guard psm_viewport_gizmo_axis_screen(handle, axis, &screen) != 0 else {
            return nil
        }
        return GizmoScreenAxis(
            from: CGPoint(x: CGFloat(screen.from_x), y: CGFloat(screen.from_y)),
            to: CGPoint(x: CGFloat(screen.to_x), y: CGFloat(screen.to_y)))
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

    /// Die Grenzen des Werkzeugweg-Bereichs innerhalb der gerade
    /// sichtbaren Schicht(en) - haengen vom Schichtbereich ab, also
    /// nach jeder Aenderung von setLayerRange neu abfragen.
    func moveRangeBounds() -> ClosedRange<Int32>? {
        var minWert: Int32 = 0
        var maxWert: Int32 = 0
        guard psm_viewport_move_range_bounds(handle, &minWert, &maxWert) != 0,
              minWert <= maxWert else { return nil }
        return minWert...maxWert
    }

    func setMoveRange(first: Int32, last: Int32) {
        psm_viewport_set_move_range(handle, first, last)
    }

    func setPreviewView(_ view: PreviewView) {
        psm_viewport_set_preview_view(
            handle,
            psm_preview_view(rawValue: UInt32(view.rawValue)))
    }

    func setRole(_ role: PsmCore.PreviewFeatureRole, visible: Bool) {
        psm_viewport_set_role_visible(
            handle,
            psm_preview_feature_role(rawValue: UInt32(role.rawValue)),
            visible ? 1 : 0)
    }

    func setExtruder(_ extruder: Int32, visible: Bool) {
        psm_viewport_set_extruder_visible(
            handle, extruder, visible ? 1 : 0)
    }

    /// Alle Betten raeumlich versetzt zeigen statt nur das aktive. Der
    /// Kern verwirft einen gleichlautenden Wert selbst, ein Aufruf bei
    /// jedem Redraw kostet also nichts.
    var multiBedRender: Bool = false {
        didSet {
            psm_viewport_set_multi_bed_render(handle, multiBedRender ? 1 : 0)
        }
    }

    /// Schwenkt zum Mittelpunkt des angegebenen Betts, ohne den Zoom zu
    /// aendern.
    func focusBed(_ index: Int32) {
        psm_viewport_focus_bed(handle, index)
    }

    /// Bildschirmposition fuer ein Namensschild am jeweiligen Bett in
    /// der raeumlichen Mehrbett-Darstellung - nil ausserhalb des
    /// Mehrbett-Modus oder wenn das Bett gerade hinter der Kamera liegt.
    func bedLabelAnchor(position: Int32) -> CGPoint? {
        var x: Float = 0
        var y: Float = 0
        guard psm_viewport_bed_label_anchor(handle, position, &x, &y) != 0
        else { return nil }
        return CGPoint(x: CGFloat(x), y: CGFloat(y))
    }
}
