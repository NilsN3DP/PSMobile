import SwiftUI
import UIKit
import OpenGLES
import QuartzCore

/// Der 3D-Arbeitsbereich.
///
/// Gegenstueck zu `SceneView.kt` auf Android. Dort haelt ein
/// `GLSurfaceView` den Kontext und Compose bettet es per `AndroidView`
/// nur ein; hier uebernimmt `PSMGLView` dieselbe Rolle und SwiftUI
/// bettet es per `UIViewRepresentable` ein. Der gezeichnete Inhalt ist
/// in beiden Faellen derselbe C++-Bestand.
///
/// iOS bringt kein `GLSurfaceView` mit: Kontext, Puffer und die
/// Zeichenschleife muessen von Hand aufgesetzt werden. Genau das steht
/// unten.
struct ViewportView: UIViewRepresentable {

    let session: OpaquePointer
    let shaderDir: String
    var selectedId: Int32
    var selectedIds: [Int32]
    /// Aendert sich, wenn das Modell angefasst wurde. Ohne diesen Wert
    /// zeichnet der Viewport weiter den alten Stand.
    var invalidateKey: Int
    var inputEnabled: Bool = true
    /// Welches Gizmo am ausgewaehlten Objekt haengt. Der Viewport kennt
    /// die Betriebsart schon lange; sie war nur nicht einstellbar.
    var gizmo: PsmViewport.Gizmo = .move
    /// Bett oder G-Code-Vorschau. Die Werkzeugwege werden beim ersten
    /// Umschalten geladen - sie sind zu gross, um sie vorsorglich
    /// vorzuhalten - und die Zahl der Schichten kommt zurueck.
    var viewportMode: PsmViewport.Mode = .editor
    /// Sichtbarer Schichtbereich in der Vorschau. Nil heisst: alles.
    var layerRange: ClosedRange<Int32>?
    /// Steigt, wenn die Ansicht zurueckgesetzt werden soll. Ein Ereignis
    /// laesst sich in SwiftUI nicht als Zustand ausdruecken - ein
    /// Zaehler schon.
    var resetViewKey: Int = 0
    var onPreviewLoaded: ((Int32) -> Void)?
    var onSelect: (Int32) -> Void
    var onSurfaceTap: ((PsmViewport.SurfaceHit) -> Void)?
    var onBlockedInput: (() -> Void)?

    func makeUIView(context: Context) -> PSMGLView {
        let v = PSMGLView(session: session, shaderDir: shaderDir)
        v.onSelect = onSelect
        return v
    }

    func updateUIView(_ v: PSMGLView, context: Context) {
        v.selectedId = selectedId
        v.onSelect = onSelect
        v.onSurfaceTap = onSurfaceTap
        v.onBlockedInput = onBlockedInput
        v.inputEnabled = inputEnabled
        let zuruecksetzen = v.letzterResetKey != resetViewKey
        v.letzterResetKey = resetViewKey
        v.perform { vp in
            vp.setSelections(selectedIds, primary: selectedId)
            if vp.gizmo != gizmo { vp.gizmo = gizmo }
            if viewportMode == .preview && !v.vorschauGeladen {
                v.vorschauGeladen = true
                let schichten = vp.loadPreview() ? vp.layerCount : 0
                DispatchQueue.main.async { onPreviewLoaded?(schichten) }
            }
            if vp.mode != viewportMode { vp.mode = viewportMode }
            if let bereich = layerRange {
                vp.setLayerRange(first: bereich.lowerBound, last: bereich.upperBound)
            }
            if zuruecksetzen { vp.resetView() }
            vp.invalidate()
        }
        v.requestRender()
    }

    static func dismantleUIView(_ v: PSMGLView, coordinator: ()) {
        v.release()
    }
}

/// Haelt den GL-Kontext und die Puffer.
///
/// Gezeichnet wird nur auf Anforderung, nicht in einer Dauerschleife.
/// Ein Slicer steht die meiste Zeit still; 60 Bilder je Sekunde in ein
/// unveraendertes Bett zu rechnen kostet nur Akku.
final class PSMGLView: UIView {

    override class var layerClass: AnyClass { CAEAGLLayer.self }

    private let context: EAGLContext
    private var viewport: PsmViewport?
    private let session: OpaquePointer
    private let shaderDir: String

    private var framebuffer: GLuint = 0
    private var colorbuffer: GLuint = 0
    private var depthbuffer: GLuint = 0
    private var bufferWidth: GLint = 0
    private var bufferHeight: GLint = 0

    var selectedId: Int32 = -1
    var inputEnabled = true
    /// Zuletzt ausgefuehrtes Zuruecksetzen. Ohne diesen Merker liefe es
    /// bei jeder Neuzeichnung erneut.
    var letzterResetKey = 0
    /// Ob die Werkzeugwege schon geladen sind. Sie noch einmal zu laden
    /// kostet Sekunden und aendert nichts.
    var vorschauGeladen = false
    var onSelect: ((Int32) -> Void)?
    var onSurfaceTap: ((PsmViewport.SurfaceHit) -> Void)?
    var onBlockedInput: (() -> Void)?

    // Zustand der laufenden Geste - dieselbe Aufteilung wie auf Android.
    private var lastPoint: CGPoint = .zero
    private var lastSpan: CGFloat = 0
    private var moved = false
    private var dragObject = false
    /// Welcher Griff angefasst wurde. Bleibt fuer die Dauer des Zuges
    /// fest - laesst man ihn beim Ziehen los, spraenge das Objekt sonst
    /// auf eine andere Achse, sobald der Finger einem anderen Griff
    /// naeher kommt.
    private var gizmoAxis: Int32 = -1

    private static let handleRadiusPx: Float = 44

    init(session: OpaquePointer, shaderDir: String) {
        // GLES 2.0: genau dafuer sind die Shader aus PrusaSlicer
        // geschrieben. Sie sind GLSL ES 1.00.
        // GLES 3, wenn moeglich. Das Bett zeichnet auch unter GLES 2,
        // aber libvgcode - die G-Code-Vorschau - bringt Shader mit, die
        // sich dort nicht uebersetzen lassen; der Fehler kam erst beim
        // Umschalten auf die Vorschau und lautete nur "Unable to compile
        // vertex shader". Ein GLES-3-Kontext nimmt die alten Shader
        // weiterhin an, also kostet der Vorzug nichts.
        guard let ctx = EAGLContext(api: .openGLES3) ?? EAGLContext(api: .openGLES2) else {
            fatalError("Kein GLES2-Kontext - iOS ohne OpenGLES gibt es nicht")
        }
        self.context = ctx
        self.session = session
        self.shaderDir = shaderDir
        super.init(frame: .zero)

        // Undurchsichtig: der Viewport fuellt seine Flaeche vollstaendig,
        // und eine durchsichtige GL-Ebene kostet jeden Frame eine
        // zusaetzliche Mischung.
        isOpaque = true
        let eagl = layer as! CAEAGLLayer
        eagl.isOpaque = true
        eagl.drawableProperties = [
            kEAGLDrawablePropertyRetainedBacking: false,
            kEAGLDrawablePropertyColorFormat: kEAGLColorFormatRGBA8,
        ]
        isMultipleTouchEnabled = true
    }

    required init?(coder: NSCoder) { fatalError("nicht aus dem Storyboard") }

    /// Fuehrt etwas im GL-Kontext aus. Gegenstueck zu `queueEvent` auf
    /// Android - nur laeuft es hier gleich, weil gezeichnet wird, wenn
    /// UIKit es verlangt, und nicht auf einem eigenen Thread.
    func perform(_ block: (PsmViewport) -> Void) {
        guard let vp = viewport else { return }
        EAGLContext.setCurrent(context)
        block(vp)
    }

    /// Zeichnet sofort.
    ///
    /// Bewusst nicht ueber setNeedsDisplay und draw(_:): eine GL-Ebene
    /// haengt nicht am Zeichenzyklus von UIKit, sie bekommt ihren Inhalt
    /// vom Renderbuffer. Der Umweg wuerde nur einen Frame Verzoegerung
    /// einbauen, und beim Ziehen mit dem Finger merkt man das.
    func requestRender() {
        guard let vp = viewport, framebuffer != 0 else { return }
        EAGLContext.setCurrent(context)
        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), framebuffer)
        vp.render()
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), colorbuffer)
        context.presentRenderbuffer(Int(GL_RENDERBUFFER))
    }

    func release() {
        EAGLContext.setCurrent(context)
        viewport = nil
        deleteBuffers()
        EAGLContext.setCurrent(nil)
    }

    // MARK: - Puffer

    override func layoutSubviews() {
        super.layoutSubviews()
        // Der Bildschirmmassstab gehoert dazu: die Ebene misst in Punkten,
        // GL rechnet in Pixeln. Ohne das zeichnet der Viewport auf einem
        // Retina-Geraet in ein Viertel der Flaeche.
        contentScaleFactor = window?.screen.scale ?? UIScreen.main.scale
        EAGLContext.setCurrent(context)
        deleteBuffers()
        createBuffers()

        if viewport == nil {
            viewport = PsmViewport(session: session, shaderDir: shaderDir)
            if viewport == nil {
                NSLog("PSMGLView: Viewport liess sich nicht anlegen")
            } else if let e = viewport?.lastError, !e.isEmpty {
                NSLog("PSMGLView: Shaderfehler: %@", e)
            }
        }
        viewport?.resize(width: Int32(bufferWidth), height: Int32(bufferHeight))
        requestRender()
    }

    private func createBuffers() {
        glGenFramebuffers(1, &framebuffer)
        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), framebuffer)

        glGenRenderbuffers(1, &colorbuffer)
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), colorbuffer)
        context.renderbufferStorage(Int(GL_RENDERBUFFER), from: layer as? CAEAGLLayer)
        glFramebufferRenderbuffer(GLenum(GL_FRAMEBUFFER), GLenum(GL_COLOR_ATTACHMENT0),
                                  GLenum(GL_RENDERBUFFER), colorbuffer)

        glGetRenderbufferParameteriv(GLenum(GL_RENDERBUFFER),
                                     GLenum(GL_RENDERBUFFER_WIDTH), &bufferWidth)
        glGetRenderbufferParameteriv(GLenum(GL_RENDERBUFFER),
                                     GLenum(GL_RENDERBUFFER_HEIGHT), &bufferHeight)

        // Ohne Tiefenpuffer zeichnet ein Modell sich selbst durch.
        glGenRenderbuffers(1, &depthbuffer)
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), depthbuffer)
        glRenderbufferStorage(GLenum(GL_RENDERBUFFER), GLenum(GL_DEPTH_COMPONENT16),
                              bufferWidth, bufferHeight)
        glFramebufferRenderbuffer(GLenum(GL_FRAMEBUFFER), GLenum(GL_DEPTH_ATTACHMENT),
                                  GLenum(GL_RENDERBUFFER), depthbuffer)
    }

    private func deleteBuffers() {
        if framebuffer != 0 { glDeleteFramebuffers(1, &framebuffer); framebuffer = 0 }
        if colorbuffer != 0 { glDeleteRenderbuffers(1, &colorbuffer); colorbuffer = 0 }
        if depthbuffer != 0 { glDeleteRenderbuffers(1, &depthbuffer); depthbuffer = 0 }
    }

    // MARK: - Gesten
    //
    // Bewusst rohe Beruehrungen statt UIGestureRecognizer: die Reihenfolge
    // - erst Griff, dann Objekt, dann Kamera - laesst sich damit genauso
    // ausdruecken wie in SceneView.kt. Mit mehreren Erkennern muesste man
    // dieselbe Entscheidung ueber Vorrangregeln nachbilden.

    /// Rechnet einen Punkt von Punkten in Pixel um. Der Viewport rechnet
    /// in Pixeln, UIKit liefert Punkte.
    private func px(_ p: CGPoint) -> (Float, Float) {
        (Float(p.x * contentScaleFactor), Float(p.y * contentScaleFactor))
    }

    private func span(_ touches: Set<UITouch>) -> CGFloat {
        let pts = touches.map { $0.location(in: self) }
        guard pts.count >= 2 else { return 0 }
        return hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
    }

    private func mid(_ touches: Set<UITouch>) -> CGPoint {
        let pts = touches.map { $0.location(in: self) }
        guard !pts.isEmpty else { return .zero }
        let sx = pts.reduce(0) { $0 + $1.x }, sy = pts.reduce(0) { $0 + $1.y }
        return CGPoint(x: sx / CGFloat(pts.count), y: sy / CGFloat(pts.count))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard inputEnabled else { return }
        guard let vp = viewport else { return }
        let all = event?.touches(for: self) ?? touches

        if all.count >= 2 {
            lastSpan = span(all)
            lastPoint = mid(all)
            moved = true    // ab zwei Fingern ist es keine Auswahl mehr
            return
        }

        lastPoint = all.first?.location(in: self) ?? .zero
        moved = false
        let (x, y) = px(lastPoint)

        EAGLContext.setCurrent(context)
        // Zuerst die Griffe: sie liegen ueber dem Objekt und haben Vorrang
        // vor Auswahl und Kameradrehung.
        gizmoAxis = (vp.mode == .editor && selectedId >= 0)
            ? vp.gizmoPick(x: x, y: y, radius: PSMGLView.handleRadiusPx)
            : -1

        // In der Vorschau gibt es nichts anzufassen - dort dreht jede
        // Fingerbewegung nur die Kamera.
        dragObject = onSurfaceTap == nil
            && gizmoAxis < 0
            && vp.mode == .editor
            && selectedId >= 0
            && vp.pick(x: x, y: y) == selectedId
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard inputEnabled, let vp = viewport else { return }
        let all = event?.touches(for: self) ?? touches
        EAGLContext.setCurrent(context)

        if all.count >= 2 {
            let s = span(all)
            if lastSpan > 0, s > 0 {
                let f = Float(s / lastSpan)
                if abs(f - 1) > 0.002 {
                    // Im Skalieren-Werkzeug greift das Spreizen das Objekt,
                    // sonst die Kamera.
                    if vp.gizmo == .scale && selectedId >= 0 {
                        vp.scaleSelected(f)
                    } else {
                        vp.zoom(f)
                    }
                }
                lastSpan = s
            }
            let m = mid(all)
            vp.pan(dx: Float(m.x - lastPoint.x), dy: Float(m.y - lastPoint.y))
            lastPoint = m
            moved = true
            requestRender()
            return
        }

        guard let p = all.first?.location(in: self) else { return }
        let (fx, fy) = px(lastPoint)
        let (tx, ty) = px(p)

        if gizmoAxis >= 0 {
            vp.gizmoDrag(axis: gizmoAxis, fromX: fx, fromY: fy, toX: tx, toY: ty)
        } else if dragObject {
            vp.dragSelected(fromX: fx, fromY: fy, toX: tx, toY: ty)
        } else {
            vp.orbit(dx: Float(p.x - lastPoint.x), dy: Float(p.y - lastPoint.y))
        }

        lastPoint = p
        moved = true
        requestRender()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard inputEnabled else {
            // Der Viewport liegt technisch unter dem SwiftUI-Aufbau, kann
            // Beruehrungen aber trotzdem zuerst erhalten. Ist die Eingabe
            // gesperrt, ist ein Tippen semantisch "Menue schliessen".
            onBlockedInput?()
            return
        }
        defer { gizmoAxis = -1; dragObject = false; lastSpan = 0 }
        guard let vp = viewport, !moved,
              let p = touches.first?.location(in: self) else { return }

        // Nicht bewegt: das war eine Auswahl.
        EAGLContext.setCurrent(context)
        let (x, y) = px(p)
        if let tap = onSurfaceTap {
            if let hit = vp.surfacePick(x: x, y: y) { tap(hit) }
        } else {
            onSelect?(vp.pick(x: x, y: y))
        }
        requestRender()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        gizmoAxis = -1
        dragObject = false
        lastSpan = 0
    }
}
