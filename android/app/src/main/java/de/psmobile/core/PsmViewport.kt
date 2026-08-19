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
        @JvmStatic private external fun nativeDropSelected(h: Long): Int
        @JvmStatic private external fun nativeSetMultiBedRender(h: Long, enabled: Int)
        @JvmStatic private external fun nativeFocusBed(h: Long, index: Int)
        @JvmStatic private external fun nativeBedLabelAnchor(h: Long, position: Int): String?
        @JvmStatic private external fun nativeSetPreviewView(h: Long, view: Int)
        @JvmStatic private external fun nativeSetRoleVisible(h: Long, role: Int, visible: Int)
        @JvmStatic private external fun nativeSetExtruderVisible(h: Long, extruder: Int, visible: Int)
        @JvmStatic private external fun nativeMoveRangeBounds(h: Long): String?
        @JvmStatic private external fun nativeSetMoveRange(h: Long, first: Int, last: Int)
        @JvmStatic private external fun nativeGestureBegin(h: Long)
        @JvmStatic private external fun nativeGetGizmo(h: Long): Int
        @JvmStatic private external fun nativeGizmoAxisScreen(h: Long, axis: Int): String?
        @JvmStatic private external fun nativeSetSelections(
            h: Long, ids: IntArray, primary: Int)
        @JvmStatic private external fun nativeSetPaintOptions(
            h: Long, enabled: Int, tool: Int, mode: Int, shape: Int,
            radiusMm: Float, fillAngleDeg: Float, splitTriangles: Int,
        )

        @JvmStatic private external fun nativeSurfacePick(
            h: Long, x: Float, y: Float): String?
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

    /**
     * Markiert mehrere Objekte. Nur [primary] traegt Gizmos und ist das
     * Ziel numerischer Einzelwerkzeuge.
     */
    fun setSelections(ids: Collection<Int>, primary: Int?) =
        nativeSetSelections(handle, ids.toIntArray(), primary ?: -1)

    data class SurfaceHit(
        val objectId: Int,
        val volumeIndex: Int,
        val facetIndex: Int,
        val instanceIndex: Int,
        val x: Float,
        val y: Float,
        val z: Float,
        val nx: Float,
        val ny: Float,
        val nz: Float,
    )

    /** Exakter Dreieckstreffer in Weltkoordinaten. */

    /**
     * Schaltet die Maldarstellung ein oder aus.
     *
     * Der Viewport zeichnet die bemalten Dreiecke und den Pinselzeiger
     * nur, solange das gesetzt ist. Ohne den Aufruf landet die Bemalung
     * zwar im Modell, ist aber nicht zu sehen - und dann sieht es aus,
     * als taete das Werkzeug nichts.
     *
     * Es ist derselbe Optionswert, der auch an den Kern geht: der Zeiger
     * auf dem Modell hat damit garantiert die Groesse und Form, mit der
     * anschliessend gemalt wird.
     *
     * @param options options.tool == null schaltet ab.
     */
    fun setPaintOptions(options: PsmCore.PaintOptions) {
        val tool = options.tool
        if (tool == null) {
            nativeSetPaintOptions(handle, 0, 0, 0, 0, 0f, 0f, 0)
            return
        }
        nativeSetPaintOptions(
            handle, 1, tool.raw, options.mode.raw, options.shape.raw,
            options.radiusMm, options.fillAngleDeg,
            if (options.splitTriangles) 1 else 0,
        )
    }

    fun surfacePick(x: Float, y: Float): SurfaceHit? {
        val fields = nativeSurfacePick(handle, x, y)?.split('\t') ?: return null
        if (fields.size != 10) return null
        val numbers = fields.map { it.toFloatOrNull() ?: return null }
        return SurfaceHit(
            objectId = numbers[0].toInt(),
            volumeIndex = numbers[1].toInt(),
            facetIndex = numbers[2].toInt(),
            instanceIndex = numbers[3].toInt(),
            x = numbers[4],
            y = numbers[5],
            z = numbers[6],
            nx = numbers[7],
            ny = numbers[8],
            nz = numbers[9],
        )
    }

    /** @return true wenn das ausgewaehlte Objekt bewegt wurde */
    fun dragSelected(fx: Float, fy: Float, tx: Float, ty: Float): Boolean =
        nativeDragSelected(handle, fx, fy, tx, ty) != 0

    /**
     * Schliesst ein Ziehen ab. Liegt das Objekt jetzt ueber einem
     * anderen Bett, gehoert es danach auch dorthin - und bleibt dabei
     * an der Stelle, an der der Finger es abgesetzt hat.
     *
     * @return die neue Objektkennung, oder null wenn nichts wechselte.
     */
    fun dropSelected(): Int? = nativeDropSelected(handle).takeIf { it >= 0 }

    /**
     * Alle Betten raeumlich versetzt zeigen statt nur das aktive.
     * Kostet mehr Geometrie und Strahltests pro Bild - deshalb ein
     * Schalter und kein Standardverhalten.
     */
    fun setMultiBedRender(enabled: Boolean) =
        nativeSetMultiBedRender(handle, if (enabled) 1 else 0)

    /** Schwenkt die Kamera auf genau ein Bett und passt es ins Bild. */
    fun focusBed(index: Int) = nativeFocusBed(handle, index)

    /* --- Vorschau-Filter --------------------------------------------- */

    /** Wonach die Werkzeugwege eingefaerbt werden. */
    enum class PreviewView(val raw: Int) { FEATURE(0), EXTRUDER(1) }

    fun setPreviewView(view: PreviewView) = nativeSetPreviewView(handle, view.raw)

    /** Eine Merkmalsrolle aus- oder einblenden. */
    fun setRoleVisible(role: Int, visible: Boolean) =
        nativeSetRoleVisible(handle, role, if (visible) 1 else 0)

    /** Einen Extruder aus- oder einblenden. */
    fun setExtruderVisible(extruder: Int, visible: Boolean) =
        nativeSetExtruderVisible(handle, extruder, if (visible) 1 else 0)

    /* --- Werkzeugweg innerhalb der Schicht ---------------------------- */

    /**
     * Grenzen des unteren Reglers. Er scrubt innerhalb einer Schicht,
     * statt zwischen Schichten zu wechseln. Sie aendern sich mit dem
     * Schichtbereich - also vor jedem Aufbau des Reglers neu abfragen.
     */
    fun moveRangeBounds(): IntRange? {
        val f = nativeMoveRangeBounds(handle)?.split('\t') ?: return null
        if (f.size != 2) return null
        val min = f[0].toIntOrNull() ?: return null
        val max = f[1].toIntOrNull() ?: return null
        return min..max
    }

    fun setMoveRange(first: Int, last: Int) = nativeSetMoveRange(handle, first, last)

    /**
     * Meldet den Anfang einer Geste - danach setzt der Kern genau einen
     * Wiederherstellungspunkt.
     */
    fun gestureBegin() = nativeGestureBegin(handle)

    fun currentGizmo(): Gizmo =
        Gizmo.entries.firstOrNull { it.raw == nativeGetGizmo(handle) } ?: Gizmo.NONE

    /**
     * Bildschirmsegment einer Move-Gizmo-Achse in Renderpixeln, oder
     * null wenn sie gerade nicht sichtbar ist.
     */
    fun gizmoAxisScreen(axis: Int): FloatArray? {
        val f = nativeGizmoAxisScreen(handle, axis)?.split('\t') ?: return null
        if (f.size != 4) return null
        val werte = f.map { it.toFloatOrNull() ?: return null }
        return werte.toFloatArray()
    }

    /**
     * Bildschirmpunkt fuer das Namensschild eines Betts, oder null
     * ausserhalb der Mehrbett-Darstellung.
     */
    fun bedLabelAnchor(position: Int): Pair<Float, Float>? {
        val fields = nativeBedLabelAnchor(handle, position)?.split('	') ?: return null
        if (fields.size != 2) return null
        val x = fields[0].toFloatOrNull() ?: return null
        val y = fields[1].toFloatOrNull() ?: return null
        return x to y
    }

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
