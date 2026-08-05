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
    /// Feste Blickrichtung. Wirkt, sobald sich der Zaehler aendert -
    /// so laesst sich dieselbe Richtung zweimal hintereinander anfahren.
    var viewPreset: PsmViewport.ViewPreset?
    var viewPresetKey: Int = 0
    var onPreviewLoaded: ((Int32) -> Void)?
    var onSelect: (Int32) -> Void
    /// Ruft zurueck, sobald eine Geste das Objekt im Kern veraendert
    /// hat - Groesse, Lage, Drehung. Ohne das zeigt die Objektleiste
    /// waehrend des Ziehens noch die Werte von vorher.
    var onObjectChanged: (() -> Void)?
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
        v.onObjectChanged = onObjectChanged
        v.onSurfaceTap = onSurfaceTap
        v.onBlockedInput = onBlockedInput
        v.inputEnabled = inputEnabled
        let zuruecksetzen = v.letzterResetKey != resetViewKey
        v.letzterResetKey = resetViewKey
        let blickwinkel = v.letzterViewKey != viewPresetKey ? viewPreset : nil
        v.letzterViewKey = viewPresetKey
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
            if let blick = blickwinkel { vp.setView(blick) }
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
    var letzterViewKey = 0
    /// Ob die Werkzeugwege schon geladen sind. Sie noch einmal zu laden
    /// kostet Sekunden und aendert nichts.
    var vorschauGeladen = false
    var onSelect: ((Int32) -> Void)?
    var onObjectChanged: (() -> Void)?
    var onSurfaceTap: ((PsmViewport.SurfaceHit) -> Void)?
    var onBlockedInput: (() -> Void)?

    // Zustand der laufenden Geste - dieselbe Aufteilung wie auf Android.
    private var lastPoint: CGPoint = .zero
    private var lastSpan: CGFloat = 0
    private var moved = false
    private var dragObject = false
    /// Der Takt des Bildschirms, und ob fuers naechste Bild etwas zu
    /// tun ist.
    private var takt: CADisplayLink?
    private var brauchtBild = false
    private var letzteMeldung: CFTimeInterval = 0
    /// Ob dieser Zug ein Malstrich ist. Dann dreht sich die Kamera
    /// nicht mit - sonst malte man auf ein wanderndes Ziel.
    private var malstrich = false
    /// Wo zuletzt ein Tupfer sass, in Pixeln.
    private var letzterTupfer = CGPoint(x: -1000, y: -1000)
    /// Abstand zwischen zwei Tupfern. Enger waere Rechenzeit ohne Bild:
    /// der Pinsel ist ein Vielfaches davon breit.
    private static let tupferAbstandPx: CGFloat = 6
    /// Welcher Griff angefasst wurde. Bleibt fuer die Dauer des Zuges
    /// fest - laesst man ihn beim Ziehen los, spraenge das Objekt sonst
    /// auf eine andere Achse, sobald der Finger einem anderen Griff
    /// naeher kommt.
    private var gizmoAxis: Int32 = -1

    private static let handleRadiusPx: Float = 44
    private var gizmoMarkers: [UIView] = []

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

        /*
         * OpenGL-Inhalt taucht nicht von selbst im Accessibility-Baum auf.
         * Diese nicht interaktiven Marker geben VoiceOver und XCUITest die
         * echten, vom Viewport projizierten Positionen. Sie bleiben optisch
         * durchsichtig und nehmen dem GL-View keine Beruehrungen weg.
         */
        gizmoMarkers = (0...3).map { index in
            let marker = UIView(frame: .zero)
            marker.backgroundColor = .clear
            marker.isUserInteractionEnabled = false
            marker.isAccessibilityElement = true
            marker.accessibilityIdentifier = index == 0
                ? "viewport.gizmo.origin"
                : "viewport.gizmo.axis.\(index - 1)"
            marker.accessibilityLabel = index == 0
                ? "Ursprung des Verschiebewerkzeugs"
                : ["X-Achse", "Y-Achse", "Z-Achse"][index - 1]
            marker.isHidden = true
            addSubview(marker)
            return marker
        }
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
    /// Bescheid sagen, dass sich etwas geaendert hat.
    ///
    /// Zeichnet nicht selbst: gezeichnet wird im Takt des Bildschirms.
    /// Zehn Aufrufe zwischen zwei Bildern kosten so ein Bild, nicht
    /// zehn.
    func requestRender() {
        brauchtBild = true
        if takt == nil { starteTakt() }
    }

    private func starteTakt() {
        let link = CADisplayLink(target: self, selector: #selector(taktschlag))
        link.add(to: .main, forMode: .common)
        takt = link
    }

    @objc private func taktschlag() {
        guard brauchtBild else { return }
        brauchtBild = false
        zeichne()
    }

    /// Sofort zeichnen. Nur fuer den Fall, dass es kein spaeter gibt -
    /// beim Aufbau und beim Groessenwechsel.
    func zeichne() {
        guard let vp = viewport, framebuffer != 0 else { return }
        EAGLContext.setCurrent(context)
        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), framebuffer)
        vp.render()
        glBindRenderbuffer(GLenum(GL_RENDERBUFFER), colorbuffer)
        context.presentRenderbuffer(Int(GL_RENDERBUFFER))
        aktualisiereGizmoMarker(vp)
    }

    private func aktualisiereGizmoMarker(_ vp: PsmViewport) {
        gizmoMarkers.forEach { $0.isHidden = true }
        guard vp.mode == .editor, selectedId >= 0, vp.gizmo == .move,
              contentScaleFactor > 0 else { return }

        let punktgroesse: CGFloat = 12
        var ursprung: CGPoint?
        for axis in 0..<3 {
            guard let screen = vp.gizmoScreenAxis(Int32(axis)) else { continue }
            let from = CGPoint(x: screen.from.x / contentScaleFactor,
                               y: screen.from.y / contentScaleFactor)
            let to = CGPoint(x: screen.to.x / contentScaleFactor,
                             y: screen.to.y / contentScaleFactor)
            ursprung = ursprung ?? from
            gizmoMarkers[axis + 1].frame = CGRect(
                x: to.x - punktgroesse / 2, y: to.y - punktgroesse / 2,
                width: punktgroesse, height: punktgroesse)
            gizmoMarkers[axis + 1].isHidden = false
        }
        if let punkt = ursprung {
            gizmoMarkers[0].frame = CGRect(
                x: punkt.x - punktgroesse / 2, y: punkt.y - punktgroesse / 2,
                width: punktgroesse, height: punktgroesse)
            gizmoMarkers[0].isHidden = false
        }
    }

    deinit {
        takt?.invalidate()
    }

    func release() {
        EAGLContext.setCurrent(context)
        takt?.invalidate()
        takt = nil
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
        zeichne()
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

    /// Sagt der Oberflaeche Bescheid, aber nicht oefter als noetig.
    ///
    /// Bei 120 Bildern in der Sekunde jedes Mal die ganze Objektliste
    /// aus dem Kern zu holen waere Verschwendung; zwoelf Mal in der
    /// Sekunde sieht der Mensch als fluessig an.
    private func meldeAenderung() {
        let jetzt = CACurrentMediaTime()
        guard jetzt - letzteMeldung > 0.08 else { return }
        letzteMeldung = jetzt
        onObjectChanged?()
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
            vp.gestureBegin()
            return
        }

        lastPoint = all.first?.location(in: self) ?? .zero
        moved = false
        // Ein Zug ist ein Schritt: der Kern setzt danach genau einen
        // Wiederherstellungspunkt, nicht einen je Ereignis.
        vp.gestureBegin()
        let (x, y) = px(lastPoint)

        EAGLContext.setCurrent(context)

        // Ein aktives Malwerkzeug hat Vorrang vor allem anderen: keine
        // Griffe, kein Verschieben, keine Kameradrehung. Wer malt, will
        // malen.
        if let tap = onSurfaceTap {
            gizmoAxis = -1
            dragObject = false
            malstrich = true
            letzterTupfer = CGPoint(x: CGFloat(x), y: CGFloat(y))
            if let hit = vp.surfacePick(x: x, y: y) {
                tap(hit)
                requestRender()
            }
            return
        }
        malstrich = false

        // Zuerst die Griffe: sie liegen ueber dem Objekt und haben Vorrang
        // vor Auswahl und Kameradrehung.
        gizmoAxis = (vp.mode == .editor && selectedId >= 0)
            ? vp.gizmoPick(x: x, y: y, radius: PSMGLView.handleRadiusPx)
            : -1

        /*
         * Das Move-Gizmo ist absichtlich streng: entweder wurde seine
         * viewportseitig gemessene Achse getroffen, oder die Geste bleibt
         * Orbit. Ohne Move-Gizmo darf dagegen das Objekt selbst direkt
         * gezogen werden; freie Flaeche bleibt weiterhin Orbit.
         */
        dragObject = vp.gizmo == .none
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
                        meldeAenderung()
                    } else {
                        vp.zoom(f)
                    }
                }
                lastSpan = s
            }
            let m = mid(all)
            // In Pixeln, nicht in Punkten. Der Viewport rechnet Wege
            // gegen seine Puffergroesse, und die ist auf einem
            // Retina-Schirm doppelt so gross wie die Punktflaeche - in
            // Punkten geschoben fuehlt sich alles halb so weit an.
            vp.pan(dx: Float((m.x - lastPoint.x) * contentScaleFactor),
                   dy: Float((m.y - lastPoint.y) * contentScaleFactor))
            lastPoint = m
            moved = true
            requestRender()
            return
        }

        guard let p = all.first?.location(in: self) else { return }
        let (fx, fy) = px(lastPoint)
        let (tx, ty) = px(p)

        if malstrich, let tap = onSurfaceTap {
            let weit = hypot(CGFloat(tx) - letzterTupfer.x, CGFloat(ty) - letzterTupfer.y)
            if weit >= PSMGLView.tupferAbstandPx {
                letzterTupfer = CGPoint(x: CGFloat(tx), y: CGFloat(ty))
                if let hit = vp.surfacePick(x: tx, y: ty) { tap(hit) }
            }
            lastPoint = p
            moved = true
            requestRender()
            return
        }

        if gizmoAxis >= 0 {
            vp.gizmoDrag(axis: gizmoAxis, fromX: fx, fromY: fy, toX: tx, toY: ty)
            meldeAenderung()
        } else if dragObject {
            vp.dragSelected(fromX: fx, fromY: fy, toX: tx, toY: ty)
            meldeAenderung()
        } else {
            vp.orbit(dx: Float((p.x - lastPoint.x) * contentScaleFactor),
                     dy: Float((p.y - lastPoint.y) * contentScaleFactor))
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
        defer {
            // Der letzte Wert muss stimmen, auch wenn die Drossel oben
            // die letzte Meldung geschluckt hat.
            if gizmoAxis >= 0 || dragObject || lastSpan > 0 {
                letzteMeldung = 0
                meldeAenderung()
            }
            gizmoAxis = -1; dragObject = false; lastSpan = 0; malstrich = false
        }
        // Beim Malen ist schon alles gemalt - ein Tupfer zum Abschied
        // saesse dort, wo der Finger abhebt, und das ist selten gewollt.
        if malstrich { requestRender(); return }
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
        malstrich = false
        lastSpan = 0
    }
}
