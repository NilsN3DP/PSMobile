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
        @JvmStatic private external fun nativeLastError(h: Long): String
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

    val lastError: String get() = if (handle == 0L) "" else nativeLastError(handle)

    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }
}
