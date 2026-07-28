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
}

@Composable
fun SceneView(
    core: PsmCore?,
    shaderDir: String,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    invalidateKey: Any,
    controller: SceneController,
    modifier: Modifier = Modifier,
) {
    if (core == null) return

    val holder = remember { ViewportHolder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SceneGLView(ctx, core, shaderDir, holder, onSelect).also {
                controller.view = it
                controller.holder = holder
            }
        },
        update = { view ->
            // Modell hat sich geaendert oder Auswahl gewechselt: neu zeichnen.
            view.queueEvent {
                holder.viewport?.setSelection(selectedId ?: -1)
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
) : GLSurfaceView(context) {

    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var pointers = 0
    private var moved = false
    private var downTime = 0L

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
        val vp = holder.viewport ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                pointers = 1; moved = false
                downTime = System.currentTimeMillis()
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
                        if (abs(f - 1f) > 0.002f) queueEvent { vp.zoom(f) }
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
                        queueEvent { vp.orbit(dx, dy) }
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
                        val id = vp.pick(x, y)
                        vp.setSelection(id)
                        post { onSelect(id) }
                    }
                    requestRender()
                }
                pointers = 0
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pointers = event.pointerCount - 1
                lastSpan = 0f
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
