package de.psmobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Der 3D-Viewport als Compose-Baustein.
 *
 * Gestenregeln nach docs/05-ui-konzept-touch-stift.md, Stand M4:
 *   1 Finger tippen  -> Objekt auswaehlen
 *   1 Finger ziehen  -> Kamera drehen
 *   2 Finger ziehen  -> Kamera schieben
 *   2 Finger spreizen-> Zoom
 *   2 Finger doppelt -> Ansicht zuruecksetzen
 *
 * Objekte mit dem Finger verschieben und die Gizmos kommen in M5 - dafuer
 * braucht es erst den Strahltest gegen Dreiecke statt gegen Huellquader.
 */
/**
 * Griff auf den Viewport von aussen, etwa fuer die Ansichtsknoepfe.
 * Alle GL-Aufrufe werden auf den GL-Thread eingereiht.
 */
/*
 * Trefferradius fuer die Griffe am Objekt, in Bildpunkten.
 *
 * Der Desktop kommt mit fuenf Pixeln aus, weil dort ein Mauszeiger
 * zielt. Ein Finger deckt gut neun Millimeter ab; bei rund 300 dpi sind
 * das etwa 110 Punkte, davon nehmen wir die Haelfte als Radius.
 */
private const val HANDLE_RADIUS_PX = 56f

class SceneController {
    internal var view: GLSurfaceView? = null
    internal var holder: ViewportHolder? = null

    private fun run(block: (PsmViewport) -> Unit) {
        val v = view ?: return
        v.queueEvent { holder?.viewport?.let(block) }
        v.requestRender()
    }

    fun setView(preset: PsmViewport.View) = run { it.setView(preset) }
    fun resetView() = run { it.resetView() }

    /** true solange die Werkzeugwege gezeigt werden statt der Modelle. */
    var inPreview: Boolean = false
        private set

    /**
     * Schaltet auf die G-Code-Vorschau um. Das Laden passiert auf dem
     * GL-Thread, weil libvgcode dabei Puffer und Shader anlegt - deshalb
     * kommt die Schichtzahl erst per Rueckruf zurueck.
     *
     * @param onReady Schichtzahl, oder 0 wenn nichts geladen werden konnte
     */
    fun enterPreview(onReady: (Int) -> Unit) {
        val v = view ?: return
        v.queueEvent {
            val vp = holder?.viewport
            val layers = if (vp != null && vp.loadPreview()) {
                vp.mode = PsmViewport.Mode.PREVIEW
                vp.layerCount()
            } else 0
            v.post { inPreview = layers > 0; onReady(layers) }
        }
        v.requestRender()
    }

    fun enterEditor() {
        inPreview = false
        run { it.mode = PsmViewport.Mode.EDITOR }
    }

    fun setLayerRange(lo: Int, hi: Int) = run { it.setLayerRange(lo, hi) }

    /**
     * Solange das Skalieren-Werkzeug aktiv ist, greift die Spreizgeste
     * das ausgewaehlte Objekt statt der Kamera.
     *
     * Das Umschalten liegt hier und nicht im Viewport, weil es eine
     * Frage der Bedienung ist, nicht der Darstellung - der Viewport
     * kennt nur "skaliere um diesen Faktor".
     */
    var scaleTool: Boolean = false

    /** Wird bei jeder Skalierung gerufen, damit die Anzeige nachzieht. */
    var onScaled: (() -> Unit)? = null

    /**
     * Welche Griffe am Objekt gezeigt werden. Das Umschalten geht ueber
     * den GL-Thread, weil der Viewport die Geometrie dabei neu baut.
     */
    fun setGizmo(g: PsmViewport.Gizmo) = run { it.setGizmo(g) }
}

@Composable
fun SceneView(
    core: PsmCore?,
    shaderDir: String,
    selectedId: Int?,
    selectedIds: Set<Int> = selectedId?.let { setOf(it) } ?: emptySet(),
    onSelect: (Int) -> Unit,
    onSurfaceTap: ((PsmViewport.SurfaceHit) -> Unit)? = null,
    invalidateKey: Any,
    controller: SceneController,
    inputEnabled: Boolean = true,
    onBlockedInput: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (core == null) return

    val holder = remember { ViewportHolder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SceneGLView(ctx, core, shaderDir, holder, onSelect, controller).also {
                controller.view = it
                controller.holder = holder
            }
        },
        update = { view ->
            // Modell hat sich geaendert oder Auswahl gewechselt: neu zeichnen.
            //
            // invalidateKey muss hier gelesen werden. Compose entscheidet
            // anhand der von der Lambda erfassten Werte, ob update erneut
            // laeuft - ein Parameter, der nur in der Signatur steht, aendert
            // daran nichts. Vorher zeichnete der Viewport nur neu, wenn sich
            // die Auswahl aenderte: ein skaliertes oder gedrehtes Objekt
            // blieb auf dem Bett in seiner alten Groesse stehen.
            @Suppress("UNUSED_EXPRESSION") invalidateKey

            view.selectedId = selectedId ?: -1
            view.surfaceTap = onSurfaceTap
            // Ein Compose-Overlay liegt visuell ueber GLSurfaceView, die
            // native View kann Beruehrungen aber trotzdem zuerst erhalten.
            // In diesem Zustand darf sie sie nicht konsumieren.
            view.inputEnabled = inputEnabled
            view.onBlockedInput = onBlockedInput
            view.queueEvent {
                holder.viewport?.setSelections(selectedIds, selectedId)
                holder.viewport?.invalidate()
            }
            view.requestRender()
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.view = null
            controller.holder = null
            holder.release()
        }
    }
}

internal class ViewportHolder {
    @Volatile var viewport: PsmViewport? = null
    fun release() {
        viewport?.destroy()
        viewport = null
    }
}

@SuppressLint("ViewConstructor")
private class SceneGLView(
    context: Context,
    private val core: PsmCore,
    private val shaderDir: String,
    private val holder: ViewportHolder,
    private val onSelect: (Int) -> Unit,
    private val controller: SceneController,
) : GLSurfaceView(context) {

    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var pointers = 0
    private var moved = false
    private var downTime = 0L
    @Volatile private var dragObject = false
    @Volatile var selectedId = -1
    @Volatile var surfaceTap: ((PsmViewport.SurfaceHit) -> Unit)? = null
    @Volatile var inputEnabled = true
    @Volatile var onBlockedInput: (() -> Unit)? = null
    /*
     * Welcher Griff angefasst wurde. Bleibt fuer die Dauer des Zuges
     * fest - laesst man ihn beim Ziehen los, springt das Objekt sonst
     * auf eine andere Achse, sobald der Finger einem anderen Griff
     * naeher kommt.
     */
    @Volatile private var gizmoAxis = -1

    init {
        // GLES 2.0: genau dafuer sind die Shader aus PrusaSlicer geschrieben.
        setEGLContextClientVersion(2)
        setRenderer(SceneRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private inner class SceneRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // Der Kontext kann neu erzeugt werden (App im Hintergrund) -
            // dann muss auch der Viewport samt Puffern neu entstehen.
            holder.release()
            val vp = PsmViewport.create(core.nativeHandle, shaderDir)
            if (vp == null) {
                Log.e("SceneView", "Viewport liess sich nicht anlegen")
            } else if (vp.lastError.isNotEmpty()) {
                Log.e("SceneView", "Shaderfehler: ${vp.lastError}")
            }
            holder.viewport = vp
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            holder.viewport?.resize(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            holder.viewport?.render()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) {
            // GLSurfaceView liegt technisch ueber Compose. Deshalb erreicht
            // ein Tap auf den abgedunkelten Hintergrund nicht dessen
            // clickable. Er ist hier semantisch ein "Menue schliessen".
            if (event.actionMasked == MotionEvent.ACTION_UP) onBlockedInput?.invoke()
            return true
        }
        val vp = holder.viewport ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                pointers = 1; moved = false
                downTime = System.currentTimeMillis()
                val x = event.x; val y = event.y
                queueEvent {
                    // Alle MOVE-Ereignisse bis ACTION_UP sind genau ein
                    // Undo-Schritt. Eine reine Kamerageste erzeugt keinen
                    // Snapshot, weil der Core erst bei einer Modelländerung
                    // tatsächlich einen Checkpoint anlegt.
                    core.beginHistory("Touch-Geste")
                    // Zuerst die Griffe: sie liegen ueber dem Objekt und
                    // haben Vorrang vor Auswahl und Kameradrehung.
                    gizmoAxis =
                        if (vp.mode == PsmViewport.Mode.EDITOR && selectedId >= 0)
                            vp.gizmoPick(x, y, HANDLE_RADIUS_PX)
                        else -1

                    // In der Vorschau gibt es nichts anzufassen - dort dreht
                    // jede Fingerbewegung nur die Kamera.
                    dragObject = surfaceTap == null &&
                        gizmoAxis < 0 &&
                        vp.mode == PsmViewport.Mode.EDITOR &&
                        selectedId >= 0 && vp.pick(x, y) == selectedId
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointers = event.pointerCount
                lastSpan = span(event)
                lastX = midX(event); lastY = midY(event)
                moved = true    // ab zwei Fingern ist es keine Auswahl mehr
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val s = span(event)
                    if (lastSpan > 0f && s > 0f) {
                        val f = s / lastSpan
                        if (abs(f - 1f) > 0.002f) {
                            // Im Skalieren-Werkzeug greift das Spreizen das
                            // Objekt, sonst die Kamera. Das Schieben mit
                            // zwei Fingern bleibt in beiden Faellen gleich.
                            if (controller.scaleTool && selectedId >= 0) {
                                queueEvent {
                                    if (vp.scaleSelected(f)) post { controller.onScaled?.invoke() }
                                }
                            } else {
                                queueEvent { vp.zoom(f) }
                            }
                        }
                    }
                    lastSpan = s

                    val mx = midX(event); val my = midY(event)
                    val dx = mx - lastX; val dy = my - lastY
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) queueEvent { vp.pan(dx, dy) }
                    lastX = mx; lastY = my
                    requestRender()
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) > 1f || abs(dy) > 1f) {
                        moved = true
                        val fx = lastX; val fy = lastY
                        val tx = event.x; val ty = event.y
                        queueEvent {
                            // Reihenfolge: Griff, dann Objekt, dann Kamera.
                            // Genau die Regel aus dem Gestenkonzept, um den
                            // Griff erweitert.
                            val handled = when {
                                gizmoAxis >= 0 ->
                                    vp.gizmoDrag(gizmoAxis, fx, fy, tx, ty, snap = true)
                                dragObject -> vp.dragSelected(fx, fy, tx, ty)
                                else -> false
                            }
                            if (handled) post { controller.onScaled?.invoke() }
                            // Der native Viewport zieht dx bereits von
                            // seinem Kamerawinkel ab. Das Touchdelta darf
                            // daher nicht ein zweites Mal gespiegelt werden:
                            // Wischen nach rechts folgt unmittelbar der
                            // sichtbaren Drehbewegung des Druckbetts.
                            else vp.orbit(dx, dy)
                        }
                        requestRender()
                    }
                    lastX = event.x; lastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                val quick = System.currentTimeMillis() - downTime < 250
                if (!moved && quick) {
                    val x = event.x; val y = event.y
                    queueEvent {
                        if (vp.mode == PsmViewport.Mode.EDITOR) {
                            val callback = surfaceTap
                            if (callback != null) {
                                vp.surfacePick(x, y)?.let { hit ->
                                    post { callback(hit) }
                                }
                            } else {
                                val id = vp.pick(x, y)
                                vp.setSelection(id)
                                selectedId = id
                                post { onSelect(id) }
                            }
                        }
                    }
                    requestRender()
                }
                pointers = 0
                gizmoAxis = -1
                queueEvent { core.endHistory() }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pointers = event.pointerCount - 1
                lastSpan = 0f
            }

            MotionEvent.ACTION_CANCEL -> {
                pointers = 0
                gizmoAxis = -1
                queueEvent { core.endHistory() }
            }
        }
        return true
    }

    private fun span(e: MotionEvent): Float =
        if (e.pointerCount < 2) 0f
        else hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun midX(e: MotionEvent): Float =
        if (e.pointerCount < 2) e.x else (e.getX(0) + e.getX(1)) * 0.5f

    private fun midY(e: MotionEvent): Float =
        if (e.pointerCount < 2) e.y else (e.getY(0) + e.getY(1)) * 0.5f
}
