package de.psmobile.core

/**
 * Kotlin-Seite des 3D-Viewports.
 *
 * Alle Methoden ausser den Gesten muessen auf dem GL-Thread laufen.
 * Geometrie geht nie durch diese Bruecke - der Viewport liest das Modell
 * direkt aus der Session. Siehe docs/entscheidungen.md, E-03.
 */
class PsmViewport private constructor(private var handle: Long) {

    companion object {
        /** Nur auf dem GL-Thread mit gueltigem Kontext aufrufen. */
        fun create(session: Long, shaderDir: String): PsmViewport? {
            val h = nativeCreate(session, shaderDir)
            return if (h == 0L) null else PsmViewport(h)
        }

        @JvmStatic private external fun nativeCreate(session: Long, shaderDir: String): Long
        @JvmStatic private external fun nativeDestroy(h: Long)
        @JvmStatic private external fun nativeResize(h: Long, w: Int, height: Int)
        @JvmStatic private external fun nativeRender(h: Long)
        @JvmStatic private external fun nativeInvalidate(h: Long)
        @JvmStatic private external fun nativeOrbit(h: Long, dx: Float, dy: Float)
        @JvmStatic private external fun nativePan(h: Long, dx: Float, dy: Float)
        @JvmStatic private external fun nativeZoom(h: Long, factor: Float)
        @JvmStatic private external fun nativeResetView(h: Long)
        @JvmStatic private external fun nativeViewPreset(h: Long, which: Int)
        @JvmStatic private external fun nativePick(h: Long, x: Float, y: Float): Int
        @JvmStatic private external fun nativeSetSelection(h: Long, id: Int)
        @JvmStatic private external fun nativeDragSelected(
            h: Long, fx: Float, fy: Float, tx: Float, ty: Float): Int
        @JvmStatic private external fun nativeLastError(h: Long): String
        @JvmStatic private external fun nativeSetMode(h: Long, mode: Int)
        @JvmStatic private external fun nativeGetMode(h: Long): Int
        @JvmStatic private external fun nativeLoadPreview(h: Long): Int
        @JvmStatic private external fun nativeLayerCount(h: Long): Int
        @JvmStatic private external fun nativeSetLayerRange(h: Long, lo: Int, hi: Int)
        @JvmStatic private external fun nativeScaleSelected(h: Long, factor: Float): Int
        @JvmStatic private external fun nativeSetGizmo(h: Long, mode: Int)
        @JvmStatic private external fun nativeGizmoPick(
            h: Long, x: Float, y: Float, radius: Float): Int
        @JvmStatic private external fun nativeGizmoDrag(
            h: Long, axis: Int, fx: Float, fy: Float,
            tx: Float, ty: Float, snap: Int): Int
    }

    fun resize(w: Int, h: Int) = nativeResize(handle, w, h)
    fun render() = nativeRender(handle)
    fun invalidate() = nativeInvalidate(handle)

    fun orbit(dx: Float, dy: Float) = nativeOrbit(handle, dx, dy)
    fun pan(dx: Float, dy: Float) = nativePan(handle, dx, dy)
    fun zoom(factor: Float) = nativeZoom(handle, factor)
    fun resetView() = nativeResetView(handle)

    enum class View(val raw: Int) { ISO(0), TOP(1), FRONT(2), BACK(3), LEFT(4), RIGHT(5) }

    fun setView(v: View) = nativeViewPreset(handle, v.raw)

    fun pick(x: Float, y: Float): Int = nativePick(handle, x, y)
    fun setSelection(id: Int) = nativeSetSelection(handle, id)

    /** @return true wenn das ausgewaehlte Objekt bewegt wurde */
    fun dragSelected(fx: Float, fy: Float, tx: Float, ty: Float): Boolean =
        nativeDragSelected(handle, fx, fy, tx, ty) != 0

    /**
     * Skaliert das ausgewaehlte Objekt gleichmaessig und setzt es wieder
     * aufs Bett. Fuer die Spreizgeste im Skalieren-Werkzeug.
     */
    fun scaleSelected(factor: Float): Boolean = nativeScaleSelected(handle, factor) != 0

    /* --- Griffe am Objekt -------------------------------------------- */

    enum class Gizmo(val raw: Int) { NONE(0), MOVE(1), ROTATE(2), SCALE(3) }

    fun setGizmo(g: Gizmo) = nativeSetGizmo(handle, g.raw)

    /**
     * Welcher Griff liegt unter dem Finger?
     *
     * @return 0 = X, 1 = Y, 2 = Z, 3 = gleichmaessig, -1 = keiner
     */
    fun gizmoPick(x: Float, y: Float, radiusPx: Float): Int =
        nativeGizmoPick(handle, x, y, radiusPx)

    /** @return true wenn sich etwas geaendert hat */
    fun gizmoDrag(axis: Int, fx: Float, fy: Float, tx: Float, ty: Float,
                  snap: Boolean): Boolean =
        nativeGizmoDrag(handle, axis, fx, fy, tx, ty, if (snap) 1 else 0) != 0

    /** Vorbereiten zeigt die Modelle, Vorschau die Werkzeugwege des Slicers. */
    enum class Mode(val raw: Int) { EDITOR(0), PREVIEW(1) }

    var mode: Mode
        get() = if (nativeGetMode(handle) == 1) Mode.PREVIEW else Mode.EDITOR
        set(value) = nativeSetMode(handle, value.raw)

    /**
     * Uebernimmt das Ergebnis des letzten Slice-Laufs in die Vorschau.
     * Nur auf dem GL-Thread aufrufen - legt Puffer und Shader an.
     *
     * @return true wenn Daten geladen wurden
     */
    fun loadPreview(): Boolean = nativeLoadPreview(handle) != 0

    fun layerCount(): Int = nativeLayerCount(handle)

    /** Beide Grenzen einschliesslich, 0-basiert. */
    fun setLayerRange(lo: Int, hi: Int) = nativeSetLayerRange(handle, lo, hi)

    val lastError: String get() = if (handle == 0L) "" else nativeLastError(handle)

    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }
}
