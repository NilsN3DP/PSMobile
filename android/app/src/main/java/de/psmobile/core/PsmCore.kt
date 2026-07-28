package de.psmobile.core

import android.util.Log
import java.io.Closeable

/**
 * Kotlin-Seite der Bruecke zu libpsmobile_core.so.
 *
 * Diese Klasse ist die *einzige* Stelle in der App, die native Aufrufe
 * macht. Alles darueber arbeitet mit gewoehnlichen Kotlin-Typen.
 *
 * Der 3D-Viewport laeuft bewusst nicht ueber diese Bruecke - Pro-Frame-
 * Aufrufe ueber JNI waeren ein Performancefehler. Siehe
 * docs/entscheidungen.md, E-03.
 */
class PsmCore private constructor(private var handle: Long) : Closeable {

    companion object {
        private const val TAG = "PsmCore"
        const val ABI_VERSION = 1

        init {
            System.loadLibrary("psmobile_core")
        }

        /**
         * @param dataDir beschreibbar, z. B. context.filesDir
         * @param resDir  entpackte PrusaSlicer-Ressourcen (profiles/, shaders/)
         */
        fun create(dataDir: String, resDir: String): PsmCore {
            val abi = nativeAbiVersion()
            require(abi == ABI_VERSION) {
                "ABI-Bruch: native Bibliothek meldet $abi, App erwartet $ABI_VERSION"
            }
            val h = nativeCreate(dataDir, resDir)
            check(h != 0L) { "Session liess sich nicht anlegen: ${nativeLastError(0L)}" }
            Log.i(TAG, "Kern ${nativeCoreVersion()} bereit")
            return PsmCore(h)
        }

        fun coreVersion(): String = nativeCoreVersion()

        @JvmStatic private external fun nativeAbiVersion(): Int
        @JvmStatic private external fun nativeCoreVersion(): String
        @JvmStatic private external fun nativeCreate(dataDir: String, resDir: String): Long
        @JvmStatic private external fun nativeDestroy(h: Long)
        @JvmStatic private external fun nativeLastError(h: Long): String
        @JvmStatic private external fun nativeClear(h: Long): Int
        @JvmStatic private external fun nativeLoadPresets(h: Long): Int
        @JvmStatic private external fun nativeLoadModel(h: Long, path: String): IntArray?
        @JvmStatic private external fun nativeRemoveModel(h: Long, id: Int): Int
        @JvmStatic private external fun nativeListObjects(h: Long): IntArray?
        @JvmStatic private external fun nativeObjectInfo(h: Long, id: Int): FloatArray?
        @JvmStatic private external fun nativeObjectName(h: Long, id: Int): String
        @JvmStatic private external fun nativeSetPosition(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeSetRotation(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeSetScale(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeDropToBed(h: Long, id: Int): Int
        @JvmStatic private external fun nativeDuplicate(h: Long, id: Int): Int
        @JvmStatic private external fun nativeArrange(h: Long, gapMm: Float): Int
        @JvmStatic private external fun nativeScaleToFit(h: Long, id: Int, sizeMm: Float): Int
        @JvmStatic private external fun nativePresetCount(h: Long, type: Int): Int
        @JvmStatic private external fun nativePresetNameAt(h: Long, type: Int, index: Int): String
        @JvmStatic private external fun nativePresetSelect(h: Long, type: Int, name: String): Int
        @JvmStatic private external fun nativePresetSelected(h: Long, type: Int): String
        @JvmStatic private external fun nativeConfigGet(h: Long, key: String): String?
        @JvmStatic private external fun nativeConfigSet(h: Long, key: String, value: String): Int
        @JvmStatic private external fun nativeSliceStart(h: Long, listener: ProgressListener?): Long
        @JvmStatic private external fun nativeSliceReleaseBridge(bridge: Long)
        @JvmStatic private external fun nativeSliceCancel(h: Long)
        @JvmStatic private external fun nativeSliceState(h: Long): Int
        @JvmStatic private external fun nativeSliceWait(h: Long, timeoutMs: Int): Int
        @JvmStatic private external fun nativeSliceStats(h: Long): DoubleArray?
        @JvmStatic private external fun nativeGcodeExport(h: Long, path: String): Int
        @JvmStatic private external fun nativeEstimateMemory(h: Long): Long
    }

    /** Wird aus dem Slice-Thread gerufen, nicht aus dem UI-Thread. */
    fun interface ProgressListener {
        /** @return true, um den Job abzubrechen. */
        fun onProgress(percent: Int, stage: String): Boolean
    }

    enum class PresetType(val raw: Int) { PRINT(0), FILAMENT(1), PRINTER(2) }

    enum class SliceState { IDLE, RUNNING, DONE, FAILED, CANCELLED }

    data class ObjectInfo(
        val id: Int,
        val name: String,
        val position: Triple<Float, Float, Float>,
        val rotation: Triple<Float, Float, Float>,
        val scale: Triple<Float, Float, Float>,
        val sizeMm: Triple<Float, Float, Float>,
        val triangles: Int,
        val instances: Int,
        val outsideBed: Boolean,
    )

    data class SliceStats(
        val printTimeSeconds: Double,
        val filamentMm: Double,
        val filamentGrams: Double,
        val cost: Double,
        val layers: Int,
        val objects: Int,
    )

    class PsmException(message: String) : RuntimeException(message)

    private var sliceBridge = 0L

    private fun requireHandle(): Long {
        check(handle != 0L) { "Session ist bereits geschlossen" }
        return handle
    }

    private fun check(code: Int, what: String) {
        if (code != 0) throw PsmException("$what fehlgeschlagen (${code}): ${lastError()}")
    }

    fun lastError(): String = if (handle == 0L) "" else nativeLastError(handle)

    fun clear() = check(nativeClear(requireHandle()), "Zuruecksetzen")

    fun loadBundledPresets() = check(nativeLoadPresets(requireHandle()), "Profile laden")

    /** @return IDs der neu erzeugten Objekte */
    fun loadModel(path: String): IntArray =
        nativeLoadModel(requireHandle(), path)
            ?: throw PsmException("Laden fehlgeschlagen: ${lastError()}")

    fun removeModel(id: Int) = check(nativeRemoveModel(requireHandle(), id), "Entfernen")

    fun listObjects(): IntArray = nativeListObjects(requireHandle()) ?: IntArray(0)

    fun objectInfo(id: Int): ObjectInfo? {
        val v = nativeObjectInfo(requireHandle(), id) ?: return null
        return ObjectInfo(
            id = id,
            name = nativeObjectName(handle, id),
            position = Triple(v[0], v[1], v[2]),
            rotation = Triple(v[3], v[4], v[5]),
            scale = Triple(v[6], v[7], v[8]),
            sizeMm = Triple(v[12] - v[9], v[13] - v[10], v[14] - v[11]),
            triangles = v[15].toInt(),
            instances = v[16].toInt(),
            outsideBed = v[17] != 0f,
        )
    }

    fun setPosition(id: Int, x: Float, y: Float, z: Float) =
        check(nativeSetPosition(requireHandle(), id, x, y, z), "Verschieben")

    fun setRotation(id: Int, x: Float, y: Float, z: Float) =
        check(nativeSetRotation(requireHandle(), id, x, y, z), "Drehen")

    fun setScale(id: Int, x: Float, y: Float, z: Float) =
        check(nativeSetScale(requireHandle(), id, x, y, z), "Skalieren")

    fun dropToBed(id: Int) = check(nativeDropToBed(requireHandle(), id), "Aufs Bett legen")

    fun duplicate(id: Int): Int = nativeDuplicate(requireHandle(), id)

    /** @param gapMm 0 = Abstand aus der Druckerkonfiguration übernehmen */
    fun arrange(gapMm: Float = 0f) = check(nativeArrange(requireHandle(), gapMm), "Anordnen")

    fun scaleToFit(id: Int, sizeMm: Float) =
        check(nativeScaleToFit(requireHandle(), id, sizeMm), "Auf Größe skalieren")

    fun presetNames(type: PresetType): List<String> {
        val h = requireHandle()
        val n = nativePresetCount(h, type.raw)
        return (0 until n).map { nativePresetNameAt(h, type.raw, it) }
    }

    fun selectPreset(type: PresetType, name: String) =
        check(nativePresetSelect(requireHandle(), type.raw, name), "Preset waehlen")

    fun selectedPreset(type: PresetType): String = nativePresetSelected(requireHandle(), type.raw)

    operator fun get(key: String): String? = nativeConfigGet(requireHandle(), key)

    operator fun set(key: String, value: String) =
        check(nativeConfigSet(requireHandle(), key, value), "Parameter $key setzen")

    fun startSlice(listener: ProgressListener?) {
        releaseBridge()
        sliceBridge = nativeSliceStart(requireHandle(), listener)
        if (sliceBridge == 0L)
            throw PsmException("Slice-Start fehlgeschlagen: ${lastError()}")
    }

    fun cancelSlice() {
        if (handle != 0L) nativeSliceCancel(handle)
    }

    fun sliceState(): SliceState = when (nativeSliceState(requireHandle())) {
        1 -> SliceState.RUNNING
        2 -> SliceState.DONE
        3 -> SliceState.FAILED
        4 -> SliceState.CANCELLED
        else -> SliceState.IDLE
    }

    /** Blockiert. Nie aus dem UI-Thread aufrufen. */
    fun awaitSlice(): SliceState {
        nativeSliceWait(requireHandle(), -1)
        releaseBridge()
        return sliceState()
    }

    fun sliceStats(): SliceStats? {
        val v = nativeSliceStats(requireHandle()) ?: return null
        return SliceStats(v[0], v[1], v[2], v[3], v[4].toInt(), v[6].toInt())
    }

    fun exportGcode(path: String) = check(nativeGcodeExport(requireHandle(), path), "G-Code-Export")

    /**
     * Geschaetzter Spitzenspeicher in Bytes. Die App vergleicht das gegen
     * das Geraetebudget und warnt vorher, statt vom Low-Memory-Killer
     * erwischt zu werden.
     */
    fun estimatedSliceMemory(): Long = nativeEstimateMemory(requireHandle())

    private fun releaseBridge() {
        if (sliceBridge != 0L) {
            nativeSliceReleaseBridge(sliceBridge)
            sliceBridge = 0L
        }
    }

    override fun close() {
        if (handle != 0L) {
            cancelSlice()
            nativeDestroy(handle)
            handle = 0L
        }
        releaseBridge()
    }
}
