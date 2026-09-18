package de.psmobile.core

import android.util.Log
import java.io.Closeable
import de.psmobile.shared.rules.CoreLabels
import de.psmobile.shared.rules.ModelFormats

/**
 * Kotlin-Seite der Bruecke zu libpsmobile_core.so.
 *
 * Diese Klasse ist die *einzige* Stelle in der App, die native Aufrufe
 * macht. Alles darueber arbeitet mit gewoehnlichen Kotlin-Typen.
 *
 * Der 3D-Viewport laeuft bewusst nicht ueber diese Bruecke - Pro-Frame-
 * Aufrufe ueber JNI waeren ein Performancefehler. Siehe
 * docs/decisions.md, E-03.
 */
class PsmCore private constructor(private var handle: Long) : Closeable {

    companion object {
        private const val TAG = "PsmCore"
        const val ABI_VERSION = 9

        init {
            System.loadLibrary("psmobile_core")
            // Ob STEP-Dateien angenommen werden, sagt der Kern selbst
            // (psm_supports_step: OCCT eingebaut oder nicht). Ein fester
            // Wert stand hier bis zum 13.09.2026 - und war seit dem
            // 25.08. falsch, weil der Kern ohne OCCT gebaut wurde.
            ModelFormats.stepVerfuegbar = nativeSupportsStep()
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
        @JvmStatic private external fun nativeSupportsStep(): Boolean
        @JvmStatic private external fun nativeCreate(dataDir: String, resDir: String): Long
        @JvmStatic private external fun nativeDestroy(h: Long)
        @JvmStatic private external fun nativeLastError(h: Long): String
        @JvmStatic private external fun nativeClear(h: Long): Int
        @JvmStatic private external fun nativeLoadPresets(h: Long): Int
        @JvmStatic private external fun nativeScanPrinterModels(h: Long): Int
        @JvmStatic private external fun nativePrinterModelAt(h: Long, index: Int): String?
        @JvmStatic private external fun nativeInstallPresets(h: Long, keys: Array<String>): Int
        @JvmStatic private external fun nativeConfigMeta(h: Long, key: String): String?
        @JvmStatic private external fun nativeConfigEnumAt(h: Long, key: String, index: Int): String?
        @JvmStatic private external fun nativeLoadModel(h: Long, path: String): IntArray?
        @JvmStatic private external fun nativeLoadProject(h: Long, path: String): String?
        @JvmStatic private external fun nativeSaveProject(h: Long, path: String): Int
        @JvmStatic private external fun nativeHistoryBegin(h: Long, label: String): Int
        @JvmStatic private external fun nativeHistoryEnd(h: Long): Int
        @JvmStatic private external fun nativeUndoCount(h: Long): Int
        @JvmStatic private external fun nativeRedoCount(h: Long): Int
        @JvmStatic private external fun nativeUndoLabel(h: Long): String
        @JvmStatic private external fun nativeRedoLabel(h: Long): String
        @JvmStatic private external fun nativeUndo(h: Long): Int
        @JvmStatic private external fun nativeRedo(h: Long): Int
        @JvmStatic private external fun nativeHistoryClear(h: Long): Int
        @JvmStatic private external fun nativeBeds(h: Long): IntArray?
        @JvmStatic private external fun nativeBedName(h: Long, index: Int): String
        @JvmStatic private external fun nativeBedMetadataSet(h: Long, index: Int, name: String, locked: Boolean): Int
        @JvmStatic private external fun nativeBedSelect(h: Long, index: Int): Int
        @JvmStatic private external fun nativeBedAdd(h: Long): Int
        @JvmStatic private external fun nativeBedRemove(h: Long, index: Int): Int
        @JvmStatic private external fun nativeBedClear(h: Long): Int
        @JvmStatic private external fun nativeBedMoveObject(h: Long, id: Int, target: Int): Int
        @JvmStatic private external fun nativeRemoveModel(h: Long, id: Int): Int
        @JvmStatic private external fun nativeListObjects(h: Long): IntArray?
        @JvmStatic private external fun nativeObjectInfo(h: Long, id: Int): FloatArray?
        @JvmStatic private external fun nativeObjectName(h: Long, id: Int): String
        @JvmStatic private external fun nativeObjectExtruder(h: Long, id: Int): Int
        @JvmStatic private external fun nativeObjectExtruderSet(h: Long, id: Int, extruder: Int): Int
        @JvmStatic private external fun nativeVolumeCount(h: Long, id: Int): Int
        @JvmStatic private external fun nativeVolumeInfo(h: Long, id: Int, index: Int): String?
        @JvmStatic private external fun nativeVolumeExtruderSet(
            h: Long, id: Int, index: Int, extruder: Int): Int
        @JvmStatic private external fun nativeSetPosition(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeSetRotation(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeSetScale(h: Long, id: Int, x: Float, y: Float, z: Float): Int
        @JvmStatic private external fun nativeDropToBed(h: Long, id: Int): Int
        @JvmStatic private external fun nativeLayFlatAuto(h: Long, id: Int): Int
        @JvmStatic private external fun nativeDuplicate(h: Long, id: Int): Int
        @JvmStatic private external fun nativeArrange(h: Long, gapMm: Float): Int
        @JvmStatic private external fun nativeScaleToFit(h: Long, id: Int, sizeMm: Float): Int
        @JvmStatic private external fun nativeFitToBed(h: Long, id: Int, fillRatio: Float): Int
        @JvmStatic private external fun nativeSplitObjects(h: Long, id: Int): IntArray?
        @JvmStatic private external fun nativeSplitVolumes(h: Long, id: Int): Int
        @JvmStatic private external fun nativeCutZ(
            h: Long, id: Int, z: Float,
            upper: Boolean, lower: Boolean, parts: Boolean): IntArray?
        @JvmStatic private external fun nativeSimplify(h: Long, id: Int, ratio: Float): IntArray?
        @JvmStatic private external fun nativeAddPrimitiveVolume(
            h: Long, id: Int, type: Int, shape: Int,
            sx: Float, sy: Float, sz: Float): Int
        @JvmStatic private external fun nativeAddTextVolume(
            h: Long, id: Int, text: String, fontPath: String,
            size: Float, depth: Float, type: Int): Int
        @JvmStatic private external fun nativeAddSvgVolume(
            h: Long, id: Int, path: String,
            depth: Float, type: Int): Int
        @JvmStatic private external fun nativeRemoveVolume(h: Long, id: Int, index: Int): Int
        @JvmStatic private external fun nativeLayOnFacet(
            h: Long, id: Int, volume: Int, facet: Int): Int
        @JvmStatic private external fun nativePaintFacet(
            h: Long, id: Int, volume: Int, facet: Int,
            tool: Int, state: Int, radiusMm: Float): Int
        @JvmStatic private external fun nativePaintApply(
            h: Long, id: Int, instance: Int, volume: Int, facet: Int,
            tool: Int, state: Int,
            mode: Int, shape: Int, radiusMm: Float, fillAngleDeg: Float,
            splitTriangles: Int,
            hx: Float, hy: Float, hz: Float,
            hasPrevious: Int, px: Float, py: Float, pz: Float): Int
        @JvmStatic private external fun nativePaintCount(h: Long, id: Int, tool: Int): Int
        @JvmStatic private external fun nativePresetOptionAt(
            h: Long, type: Int, name: String, key: String): String
        @JvmStatic private external fun nativePreviewSnapshot(h: Long): String?
        @JvmStatic private external fun nativePreviewLayerAt(h: Long, index: Int): String?
        @JvmStatic private external fun nativePreviewExtruderAt(h: Long, index: Int): String?
        @JvmStatic private external fun nativePreviewRoleAt(h: Long, index: Int): String?
        @JvmStatic private external fun nativeSliceExtruderCount(h: Long): Int
        @JvmStatic private external fun nativeSliceExtruderAt(h: Long, index: Int): String?
        @JvmStatic private external fun nativeLoadGcodeForPreview(h: Long, path: String): Int
        @JvmStatic private external fun nativeArrangeBedEx(
            h: Long, bedIndex: Int, gapMm: Float, allowRotation: Int): String
        @JvmStatic private external fun nativeLayerProfileAdaptive(
            h: Long, id: Int, quality: Float): DoubleArray?
        @JvmStatic private external fun nativeZipExtractModels(
            zipPath: String, destDir: String): Int
        @JvmStatic private external fun nativeSliceResultIsCurrent(h: Long): Int
        @JvmStatic private external fun nativeAcceptRemoteGcode(
            h: Long, path: String, requestRevision: Long): Int
        @JvmStatic private external fun nativeDesignRevision(h: Long): Long
        @JvmStatic private external fun nativeBedStateOf(h: Long, id: Int): Int
        @JvmStatic private external fun nativeLayOnFacetInstance(
            h: Long, id: Int, instance: Int, volume: Int, facet: Int): Int
        @JvmStatic private external fun nativeClearPaint(h: Long, id: Int, tool: Int): Int
        @JvmStatic private external fun nativeLayerProfile(h: Long, id: Int): DoubleArray?
        @JvmStatic private external fun nativeLayerProfileSet(
            h: Long, id: Int, values: DoubleArray?): Int
        @JvmStatic private external fun nativeObjectColour(h: Long, id: Int): String
        @JvmStatic private external fun nativeObjectColourSet(
            h: Long, id: Int, value: String): Int
        @JvmStatic private external fun nativeObjectWipe(h: Long, id: Int): IntArray?
        @JvmStatic private external fun nativeObjectWipeSet(
            h: Long, id: Int, infill: Boolean, objects: Boolean): Int
        @JvmStatic private external fun nativeCustomGcodeCount(h: Long): Int
        @JvmStatic private external fun nativeCustomGcodeAt(h: Long, index: Int): Array<String>?
        @JvmStatic private external fun nativeCustomGcodeAdd(
            h: Long, z: Double, type: Int, extruder: Int,
            color: String, extra: String): Int
        @JvmStatic private external fun nativeCustomGcodeUpdate(
            h: Long, index: Int, z: Double, type: Int, extruder: Int,
            color: String, extra: String): Int
        @JvmStatic private external fun nativeCustomGcodeRemove(h: Long, index: Int): Int
        @JvmStatic private external fun nativeCustomGcodeClear(h: Long): Int
        @JvmStatic private external fun nativeWipeTower(h: Long): FloatArray?
        @JvmStatic private external fun nativeWipeTowerSet(
            h: Long, x: Float, y: Float, rotation: Float): Int
        @JvmStatic private external fun nativePresetCount(h: Long, type: Int): Int
        @JvmStatic private external fun nativePresetNameAt(h: Long, type: Int, index: Int): String
        @JvmStatic private external fun nativePresetSelect(h: Long, type: Int, name: String): Int
        @JvmStatic private external fun nativePresetSelected(h: Long, type: Int): String
        @JvmStatic private external fun nativePresetShowIncompatible(h: Long, on: Boolean): Int
        @JvmStatic private external fun nativePresetShowsIncompatible(h: Long): Boolean
        @JvmStatic private external fun nativePresetCompatibleAt(
            h: Long, type: Int, index: Int): Boolean
        @JvmStatic private external fun nativeConfigGet(h: Long, key: String): String?
        @JvmStatic private external fun nativeConfigSet(h: Long, key: String, value: String): Int
        @JvmStatic private external fun nativePresetConfigGet(
            h: Long, type: Int, key: String): String?
        @JvmStatic private external fun nativePresetConfigSet(
            h: Long, type: Int, key: String, value: String): Int
        @JvmStatic private external fun nativeSliceStart(h: Long, listener: ProgressListener?): Long
        @JvmStatic private external fun nativeSliceReleaseBridge(bridge: Long)
        @JvmStatic private external fun nativeSliceCancel(h: Long)
        @JvmStatic private external fun nativeSliceState(h: Long): Int
        @JvmStatic private external fun nativeSliceWait(h: Long, timeoutMs: Int): Int
        @JvmStatic private external fun nativeSliceStats(h: Long): DoubleArray?
        @JvmStatic private external fun nativeGcodeExport(h: Long, path: String): Int
        @JvmStatic private external fun nativePlateExport(h: Long, path: String, format: Int): Int
        @JvmStatic private external fun nativeRepairStl(
            h: Long, input: String, output: String): Int
        @JvmStatic private external fun nativeConvertGcode(
            h: Long, input: String, output: String, binary: Boolean): Int
        @JvmStatic private external fun nativeEstimateMemory(h: Long): Long
        @JvmStatic private external fun nativeMirror(h: Long, id: Int, axis: Int): Int
        @JvmStatic private external fun nativeSetInstances(h: Long, id: Int, n: Int): Int
        @JvmStatic private external fun nativeGcodeSuggestedName(h: Long): String
        @JvmStatic private external fun nativeConfigEnabled(h: Long, key: String): String
        @JvmStatic private external fun nativeConfigGetAt(h: Long, key: String, index: Int): String?
        @JvmStatic private external fun nativeConfigSetAt(h: Long, key: String, index: Int, value: String): Int
        @JvmStatic private external fun nativeExtruderCount(h: Long): Int
        @JvmStatic private external fun nativeExtruderFilament(h: Long, idx: Int): String
        @JvmStatic private external fun nativeExtruderFilamentSet(h: Long, idx: Int, name: String): Int
        @JvmStatic private external fun nativeExtruderColor(h: Long, idx: Int): String
        @JvmStatic private external fun nativeExtruderColorSet(h: Long, idx: Int, rgb: String): Int
        @JvmStatic private external fun nativeColorMixJson(h: Long): String
        @JvmStatic private external fun nativeColorMixSetJson(h: Long, json: String): Int
        @JvmStatic private external fun nativeFilamentVendors(h: Long): String
        @JvmStatic private external fun nativeFilamentVendorsSet(h: Long, names: Array<String>): Int
        @JvmStatic private external fun nativePresetDirty(h: Long, type: Int): String
        @JvmStatic private external fun nativePresetDiscard(h: Long, type: Int): Int
        @JvmStatic private external fun nativePresetSaveAs(h: Long, type: Int, name: String): Int
        @JvmStatic private external fun nativePresetSelectKeeping(
            h: Long, type: Int, name: String,
            keys: Array<String>, values: Array<String>): Int
    }

    /** Wird aus dem Slice-Thread gerufen, nicht aus dem UI-Thread. */
    fun interface ProgressListener {
        /** @return true, um den Job abzubrechen. */
        fun onProgress(percent: Int, stage: String): Boolean
    }

    enum class PresetType(val raw: Int) { PRINT(0), FILAMENT(1), PRINTER(2) }

    enum class SliceState { IDLE, RUNNING, DONE, FAILED, CANCELLED, STALE }

    data class ObjectInfo(
        val id: Int,
        val name: String,
        val position: Triple<Float, Float, Float>,
        /** Grad, obwohl das plattformneutrale C-ABI Radiant verwendet. */
        val rotation: Triple<Float, Float, Float>,
        val scale: Triple<Float, Float, Float>,
        val sizeMm: Triple<Float, Float, Float>,
        val triangles: Int,
        val instances: Int,
        val outsideBed: Boolean,
        val extruder: Int,
        val colour: String,
        val wipeIntoInfill: Boolean,
        val wipeIntoObjects: Boolean,
    )

    enum class VolumeType {
        MODEL_PART, NEGATIVE, MODIFIER, SUPPORT_BLOCKER, SUPPORT_ENFORCER, UNKNOWN
    }

    data class VolumeInfo(
        val index: Int,
        val name: String,
        val type: VolumeType,
        val triangles: Int,
        /** Effektiv verwendeter Extruder; 0 bedeutet Standard. */
        val extruder: Int,
        /** 0 bedeutet, dass die Auswahl vom Objekt geerbt wird. */
        val explicitExtruder: Int,
    )

    data class SliceStats(
        val printTimeSeconds: Double,
        val filamentMm: Double,
        val filamentGrams: Double,
        val cost: Double,
        val layers: Int,
        val objects: Int,
    )

    data class ProjectImport(
        val configLoaded: Boolean,
        val postProcessRemoved: Boolean,
        val objectCount: Int,
        val bedCount: Int,
        val requestedPrinter: String,
        val selectedPrinter: String,
        val requestedPrint: String,
        val selectedPrint: String,
    ) {
        /**
         * true bedeutet: Das unveraenderte, im Projekt genannte Profil
         * war installiert. false ist kein Verlust – dann wurde die
         * eingebettete Konfiguration als projektlokales Profil aktiviert.
         */
        val exactInstalledPrinter: Boolean
            get() = requestedPrinter.isNotBlank() &&
                requestedPrinter == selectedPrinter
    }

    class PsmException(message: String) : RuntimeException(message)

    private var sliceBridge = 0L

    private fun requireHandle(): Long {
        check(handle != 0L) { "Session ist bereits geschlossen" }
        return handle
    }

    private fun check(code: Int, what: String) {
        // Die Meldung landet ungefiltert in der Oberflaeche, also muss
        // sie der eingestellten Sprache folgen. Der deutsche Name bleibt
        // hier der Schluessel - siehe CoreLabels.
        if (code != 0) throw PsmException(CoreLabels.failed(what, code, lastError()))
    }

    fun lastError(): String = if (handle == 0L) "" else nativeLastError(handle)

    /**
     * Rohzeiger auf die Session - nur fuer den Viewport, der im selben
     * C++-Prozessraum lebt und das Modell direkt liest (E-03).
     * Kein Aufrufer ausserhalb von PsmViewport darf das benutzen.
     */
    internal val nativeHandle: Long get() = handle

    fun clear() = check(nativeClear(requireHandle()), "Zuruecksetzen")

    fun loadBundledPresets() = check(nativeLoadPresets(requireHandle()), "Profile laden")

    // --- Ersteinrichtung --------------------------------------------------

    data class PrinterModel(
        val key: String,        // "vendor:model"
        val name: String,
        val family: String,
        val isSla: Boolean,
        /** Duesengroessen, z. B. 0.25, 0.4, HF0.4 */
        val variants: List<String>,
    )

    data class Bed(
        val index: Int,
        val objectCount: Int,
        val instanceCount: Int,
        val active: Boolean,
        val name: String,
        val locked: Boolean,
    )

    /**
     * Sichtet die verfuegbaren Druckermodelle, ohne Profile zu laden.
     * Schnell (gemessen 0,05 s fuer 37 Modelle) - im Gegensatz zum
     * vollstaendigen Laden, das ueber 13 s braucht.
     */
    fun scanPrinterModels(): List<PrinterModel> {
        val h = requireHandle()
        val n = nativeScanPrinterModels(h)
        return (0 until n).mapNotNull { i ->
            nativePrinterModelAt(h, i)?.split('\t')?.takeIf { it.size >= 5 }?.let {
                PrinterModel(
                    key = it[0], name = it[1], family = it[2], isSla = it[3] == "1",
                    variants = it[4].split(',').filter { v -> v.isNotBlank() },
                )
            }
        }
    }

    /** Richtet genau die angegebenen Modelle ein. Leer = alle. */
    fun installPresets(keys: List<String>) =
        check(nativeInstallPresets(requireHandle(), keys.toTypedArray()), "Profile einrichten")

    // --- Parameter-Metadaten ---------------------------------------------

    enum class ConfigType { BOOL, INT, FLOAT, STRING, ENUM, PERCENT, POINT, OTHER }
    enum class Mode { SIMPLE, ADVANCED, EXPERT }

    data class ConfigMeta(
        val key: String,
        val type: ConfigType,
        val mode: Mode,
        val min: Float?,
        val max: Float?,
        val enumCount: Int,
        val label: String,
        val unit: String,
        val tooltip: String,
    )

    fun configMeta(key: String): ConfigMeta? {
        val p = nativeConfigMeta(requireHandle(), key)?.split('\t') ?: return null
        if (p.size < 10) return null
        val type = when (p[0].toIntOrNull()) {
            0 -> ConfigType.BOOL; 1 -> ConfigType.INT; 2 -> ConfigType.FLOAT
            3 -> ConfigType.STRING; 4 -> ConfigType.ENUM; 5 -> ConfigType.PERCENT
            6 -> ConfigType.POINT; else -> ConfigType.OTHER
        }
        val mode = when (p[1].toIntOrNull()) {
            0 -> Mode.SIMPLE; 1 -> Mode.ADVANCED; else -> Mode.EXPERT
        }
        return ConfigMeta(
            key = key,
            type = type,
            mode = mode,
            min = if (p[2] == "1") p[3].toFloatOrNull() else null,
            max = if (p[4] == "1") p[5].toFloatOrNull() else null,
            enumCount = p[6].toIntOrNull() ?: 0,
            label = p[7],
            unit = p[8],
            tooltip = p.drop(9).joinToString("\t"),
        )
    }

    /** @return Paare aus (Wert, Beschriftung) */
    fun configEnumValues(key: String, count: Int): List<Pair<String, String>> =
        (0 until count).mapNotNull { i ->
            nativeConfigEnumAt(requireHandle(), key, i)?.split('\t')
                ?.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
        }

    /** @return IDs der neu erzeugten Objekte */
    fun loadModel(path: String): IntArray =
        nativeLoadModel(requireHandle(), path)
            // Nur die Meldung des Kerns: den Titel ("Laden fehlgeschlagen")
            // setzt das Blatt, sonst stand er zweimal da - einmal deutsch,
            // einmal englisch.
            ?: throw PsmException(lastError())

    fun loadProject(path: String): ProjectImport {
        val fields = nativeLoadProject(requireHandle(), path)?.split('\t')
            ?: throw PsmException("Projektimport fehlgeschlagen: ${lastError()}")
        if (fields.size < 8)
            throw PsmException("Projektimport lieferte unvollstaendige Metadaten")
        return ProjectImport(
            configLoaded = fields[0] == "1",
            postProcessRemoved = fields[1] == "1",
            objectCount = fields[2].toIntOrNull() ?: 0,
            bedCount = fields[3].toIntOrNull() ?: 1,
            requestedPrinter = fields[4],
            selectedPrinter = fields[5],
            requestedPrint = fields[6],
            selectedPrint = fields[7],
        )
    }

    fun saveProject(path: String) =
        check(nativeSaveProject(requireHandle(), path), "Projekt speichern")

    data class HistoryState(
        val undoCount: Int,
        val redoCount: Int,
        val undoLabel: String,
        val redoLabel: String,
    ) {
        val canUndo: Boolean get() = undoCount > 0
        val canRedo: Boolean get() = redoCount > 0
    }

    fun historyState(): HistoryState = HistoryState(
        undoCount = nativeUndoCount(requireHandle()),
        redoCount = nativeRedoCount(requireHandle()),
        undoLabel = nativeUndoLabel(requireHandle()),
        redoLabel = nativeRedoLabel(requireHandle()),
    )

    fun beginHistory(label: String) =
        check(nativeHistoryBegin(requireHandle(), label), "Historie beginnen")

    fun endHistory() =
        check(nativeHistoryEnd(requireHandle()), "Historie beenden")

    fun undo() = check(nativeUndo(requireHandle()), "Rückgängig")

    fun redo() = check(nativeRedo(requireHandle()), "Wiederholen")

    fun clearHistory() =
        check(nativeHistoryClear(requireHandle()), "Historie leeren")

    fun beds(): List<Bed> {
        val values = nativeBeds(requireHandle()) ?: return emptyList()
        if (values.isEmpty()) return emptyList()
        val active = values[0]
        return values.drop(1).chunked(3).mapIndexed { index, fields ->
            Bed(index, fields[0], fields[1], index == active,
                nativeBedName(requireHandle(), index), fields[2] != 0)
        }
    }

    fun setBedMetadata(index: Int, name: String, locked: Boolean) =
        check(nativeBedMetadataSet(requireHandle(), index, name.trim(), locked), "Bett-Metadaten setzen")

    fun selectBed(index: Int) =
        check(nativeBedSelect(requireHandle(), index), "Druckbett waehlen")

    fun addBed(): Int {
        val index = nativeBedAdd(requireHandle())
        if (index < 0)
            throw PsmException("Druckbett anlegen fehlgeschlagen: ${lastError()}")
        return index
    }

    fun removeBed(index: Int) =
        check(nativeBedRemove(requireHandle(), index), "Druckbett entfernen")

    fun clearBed() =
        check(nativeBedClear(requireHandle()), "Druckbett leeren")

    fun moveObjectToBed(id: Int, target: Int): Int {
        val newId = nativeBedMoveObject(requireHandle(), id, target)
        if (newId < 0)
            throw PsmException("Objekt verschieben fehlgeschlagen: ${lastError()}")
        return newId
    }

    fun removeModel(id: Int) = check(nativeRemoveModel(requireHandle(), id), "Entfernen")

    fun listObjects(): IntArray = nativeListObjects(requireHandle()) ?: IntArray(0)

    fun objectInfo(id: Int): ObjectInfo? {
        val v = nativeObjectInfo(requireHandle(), id) ?: return null
        val wipe = nativeObjectWipe(handle, id) ?: IntArray(2)
        return ObjectInfo(
            id = id,
            name = nativeObjectName(handle, id),
            position = Triple(v[0], v[1], v[2]),
            rotation = Triple(
                Angles.radiansToDegrees(v[3]),
                Angles.radiansToDegrees(v[4]),
                Angles.radiansToDegrees(v[5]),
            ),
            scale = Triple(v[6], v[7], v[8]),
            sizeMm = Triple(v[12] - v[9], v[13] - v[10], v[14] - v[11]),
            triangles = v[15].toInt(),
            instances = v[16].toInt(),
            outsideBed = v[17] != 0f,
            extruder = nativeObjectExtruder(handle, id).coerceAtLeast(0),
            colour = nativeObjectColour(handle, id),
            wipeIntoInfill = wipe.getOrElse(0) { 0 } != 0,
            wipeIntoObjects = wipe.getOrElse(1) { 0 } != 0,
        )
    }

    fun volumes(id: Int): List<VolumeInfo> =
        (0 until nativeVolumeCount(requireHandle(), id)).mapNotNull { index ->
            val fields = nativeVolumeInfo(requireHandle(), id, index)?.split('\t')
                ?: return@mapNotNull null
            if (fields.size < 5) return@mapNotNull null
            val type = when (fields[0].toIntOrNull()) {
                0 -> VolumeType.MODEL_PART
                1 -> VolumeType.NEGATIVE
                2 -> VolumeType.MODIFIER
                3 -> VolumeType.SUPPORT_BLOCKER
                4 -> VolumeType.SUPPORT_ENFORCER
                else -> VolumeType.UNKNOWN
            }
            VolumeInfo(
                index = index,
                name = fields.drop(4).joinToString("\t"),
                type = type,
                triangles = fields[1].toIntOrNull() ?: 0,
                extruder = fields[2].toIntOrNull() ?: 0,
                explicitExtruder = fields[3].toIntOrNull() ?: 0,
            )
        }

    fun setObjectExtruder(id: Int, extruder: Int) =
        check(nativeObjectExtruderSet(requireHandle(), id, extruder),
              "Objekt-Extruder")

    fun setVolumeExtruder(id: Int, volumeIndex: Int, extruder: Int) =
        check(nativeVolumeExtruderSet(requireHandle(), id, volumeIndex, extruder),
              "Volumen-Extruder")

    fun setPosition(id: Int, x: Float, y: Float, z: Float) =
        check(nativeSetPosition(requireHandle(), id, x, y, z), "Verschieben")

    /** Android arbeitet konsequent in Grad; erst an der C-Grenze wird umgerechnet. */
    fun setRotation(id: Int, x: Float, y: Float, z: Float) =
        check(
            nativeSetRotation(
                requireHandle(), id,
                Angles.degreesToRadians(x),
                Angles.degreesToRadians(y),
                Angles.degreesToRadians(z),
            ),
            "Drehen",
        )

    fun setScale(id: Int, x: Float, y: Float, z: Float) =
        check(nativeSetScale(requireHandle(), id, x, y, z), "Skalieren")

    fun dropToBed(id: Int) = check(nativeDropToBed(requireHandle(), id), "Aufs Bett legen")

    /** Die groesste ebene Flaeche kommt nach unten. */
    fun layFlatAuto(id: Int) = check(nativeLayFlatAuto(requireHandle(), id), "Flach hinlegen")

    fun duplicate(id: Int): Int {
        val copy = nativeDuplicate(requireHandle(), id)
        if (copy < 0)
            throw PsmException("Duplizieren fehlgeschlagen: ${lastError()}")
        return copy
    }

    /** @param gapMm 0 = Abstand aus der Druckerkonfiguration übernehmen */
    fun arrange(gapMm: Float = 0f) = check(nativeArrange(requireHandle(), gapMm), "Anordnen")

    fun scaleToFit(id: Int, sizeMm: Float) =
        check(nativeScaleToFit(requireHandle(), id, sizeMm), "Auf Größe skalieren")

    fun fitToBed(id: Int, fillRatio: Float = 0.9f) =
        check(nativeFitToBed(requireHandle(), id, fillRatio), "Aufs Bett einpassen")

    data class SimplifyResult(val before: Int, val after: Int)

    enum class PrimitiveShape(val raw: Int) {
        BOX(0), CYLINDER(1), SPHERE(2)
    }

    enum class PaintTool(val raw: Int) {
        SUPPORT(0), SEAM(1), FUZZY(2), MMU(3)
    }

    /** Wie gemalt wird. Werte aus dem C-ABI. */
    enum class PaintMode(val raw: Int) { BRUSH(0), SMART_FILL(1), BUCKET_FILL(2) }

    /** Womit gemalt wird: Kreis auf der Oberfläche oder Kugel durchs Netz. */
    enum class PaintShape(val raw: Int) { CIRCLE(0), SPHERE(1) }

    /**
     * Ein einziger Optionszustand für Bedienung, Kernaufruf und Viewport
     * - genau wie auf iOS. Markierte Facetten werden nirgends gespiegelt
     * oder nachgerechnet.
     */
    data class PaintOptions(
        val tool: PaintTool? = null,
        val state: Int = 1,
        val mode: PaintMode = PaintMode.BRUSH,
        val shape: PaintShape = PaintShape.SPHERE,
        val radiusMm: Float = 5f,
        val fillAngleDeg: Float = 30f,
        val splitTriangles: Boolean = true,
    ) {
        /**
         * Nicht jedes Werkzeug kann jeden Modus: Naht und Fuzzy kennen
         * im Kern nur den Pinsel, Bucket Fill gibt es nur für MMU.
         */
        val supportedModes: List<PaintMode>
            get() = when (tool) {
                PaintTool.SUPPORT -> listOf(PaintMode.BRUSH, PaintMode.SMART_FILL)
                PaintTool.SEAM, PaintTool.FUZZY -> listOf(PaintMode.BRUSH)
                PaintTool.MMU ->
                    listOf(PaintMode.BRUSH, PaintMode.SMART_FILL, PaintMode.BUCKET_FILL)
                null -> emptyList()
            }

        /** Räumt Modus und Zustand auf, wenn das Werkzeug gewechselt hat. */
        fun normalizedForTool(): PaintOptions {
            var result = this
            if (tool != null && mode !in supportedModes)
                result = result.copy(mode = PaintMode.BRUSH)
            if (tool != PaintTool.MMU && result.state > 2)
                result = result.copy(state = 1)
            return result
        }
    }

    fun splitObjects(id: Int): IntArray =
        nativeSplitObjects(requireHandle(), id)
            ?: throw PsmException("In Objekte teilen fehlgeschlagen: ${lastError()}")

    fun splitVolumes(id: Int): Int {
        val count = nativeSplitVolumes(requireHandle(), id)
        if (count < 0)
            throw PsmException("In Volumen teilen fehlgeschlagen: ${lastError()}")
        return count
    }

    fun cutZ(
        id: Int,
        zMm: Float,
        keepUpper: Boolean = true,
        keepLower: Boolean = true,
        keepAsParts: Boolean = false,
    ): IntArray = nativeCutZ(
        requireHandle(), id, zMm, keepUpper, keepLower, keepAsParts
    ) ?: throw PsmException("Schneiden fehlgeschlagen: ${lastError()}")

    fun simplify(id: Int, ratio: Float): SimplifyResult {
        val values = nativeSimplify(requireHandle(), id, ratio)
            ?: throw PsmException("Vereinfachen fehlgeschlagen: ${lastError()}")
        return SimplifyResult(
            values.getOrElse(0) { 0 },
            values.getOrElse(1) { 0 },
        )
    }

    fun addPrimitiveVolume(
        id: Int,
        type: VolumeType,
        shape: PrimitiveShape,
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float,
    ): Int {
        val rawType = when (type) {
            VolumeType.NEGATIVE -> 1
            VolumeType.MODIFIER -> 2
            VolumeType.SUPPORT_BLOCKER -> 3
            VolumeType.SUPPORT_ENFORCER -> 4
            else -> throw IllegalArgumentException("Kein erzeugbarer Volumentyp: $type")
        }
        val index = nativeAddPrimitiveVolume(
            requireHandle(), id, rawType, shape.raw,
            sizeX, sizeY, sizeZ,
        )
        if (index < 0)
            throw PsmException("Volumen anlegen fehlgeschlagen: ${lastError()}")
        return index
    }

    fun addTextVolume(
        id: Int,
        text: String,
        fontPath: String,
        sizeMm: Float,
        depthMm: Float,
        type: VolumeType,
    ): Int {
        val index = nativeAddTextVolume(
            requireHandle(), id, text, fontPath,
            sizeMm, depthMm, volumeTypeRaw(type),
        )
        if (index < 0)
            throw PsmException(CoreLabels.failed("Text prägen", index, lastError()))
        return index
    }

    fun addSvgVolume(
        id: Int,
        path: String,
        depthMm: Float,
        type: VolumeType,
    ): Int {
        val index = nativeAddSvgVolume(
            requireHandle(), id, path, depthMm, volumeTypeRaw(type)
        )
        if (index < 0)
            throw PsmException(CoreLabels.failed("SVG prägen", index, lastError()))
        return index
    }

    private fun volumeTypeRaw(type: VolumeType): Int = when (type) {
        VolumeType.MODEL_PART -> 0
        VolumeType.NEGATIVE -> 1
        VolumeType.MODIFIER -> 2
        VolumeType.SUPPORT_BLOCKER -> 3
        VolumeType.SUPPORT_ENFORCER -> 4
        VolumeType.UNKNOWN ->
            throw IllegalArgumentException("Unbekannter Volumentyp")
    }

    fun removeVolume(id: Int, index: Int) =
        check(nativeRemoveVolume(requireHandle(), id, index), "Volumen entfernen")

    fun layOnFacet(id: Int, volume: Int, facet: Int) =
        check(nativeLayOnFacet(requireHandle(), id, volume, facet), "Auf Fläche legen")

    fun paintFacet(
        id: Int,
        volume: Int,
        facet: Int,
        tool: PaintTool,
        state: Int,
        radiusMm: Float = 3f,
    ) =
        check(
            nativePaintFacet(
                requireHandle(), id, volume, facet,
                tool.raw, state, radiusMm,
            ),
            "Fläche bemalen",
        )

    /**
     * Der volle Malweg. Liegt ein voriger Treffer desselben
     * Instanz-Volumens vor, malt der Kern die Kapsel dazwischen - erst
     * das macht aus einer Folge von Tupfern einen durchgehenden Strich.
     */
    fun paintApply(
        id: Int,
        instance: Int,
        volume: Int,
        facet: Int,
        hit: FloatArray,
        previous: FloatArray?,
        options: PaintOptions,
    ) {
        val tool = options.tool ?: return
        check(
            nativePaintApply(
                requireHandle(), id, instance, volume, facet,
                tool.raw, options.state,
                options.mode.raw, options.shape.raw,
                options.radiusMm, options.fillAngleDeg,
                if (options.splitTriangles) 1 else 0,
                hit[0], hit[1], hit[2],
                if (previous == null) 0 else 1,
                previous?.get(0) ?: 0f,
                previous?.get(1) ?: 0f,
                previous?.get(2) ?: 0f,
            ),
            "Fläche bemalen",
        )
    }

    /** Zahl der mit diesem Werkzeug markierten Facetten. */
    fun paintCount(id: Int, tool: PaintTool): Int =
        nativePaintCount(requireHandle(), id, tool.raw)

    /* --- Materialauswahl -------------------------------------------- */

    /**
     * Ein Wert aus einem benannten Profil, ohne es auszuwaehlen.
     *
     * Die Materialauswahl braucht von jedem Filamentprofil Typ und
     * Farbe, und zwar von allen gleichzeitig. Ueber die Auswahl zu
     * gehen hiesse, fuer jede Zeile die ganze Konfiguration umzubauen
     * und das Slice-Ergebnis zu verwerfen.
     */
    /**
     * Entpackt die Modelle aus einer ZIP - typisch eine Sammlung von
     * Printables. Braucht keine Sitzung: der Kern liest nur die Datei.
     *
     * @return Zahl der entpackten Modelle, oder null bei einem Fehler.
     */
    fun zipExtractModels(zipPath: String, destDir: String): Int? =
        nativeZipExtractModels(zipPath, destDir).takeIf { it >= 0 }

    fun presetOption(type: PresetType, name: String, key: String): String =
        nativePresetOptionAt(requireHandle(), type.raw, name, key)

    /* --- Vorschau ---------------------------------------------------- */

    /** Kopf des final verarbeiteten Vorschau-Datensatzes. */
    data class PreviewSnapshot(
        val moveCount: Int,
        val layerCount: Int,
        val extruderCount: Int,
        val roleCount: Int,
        val printTimeSeconds: Double,
        val filamentUsedMm: Double,
        val filamentUsedG: Double,
        val minZ: Float,
        val maxZ: Float,
    )

    /** Eine Schicht des Ergebnisses - Grundlage der Bereichsrechnung. */
    data class PreviewLayer(
        val index: Int,
        val sourceLayerId: Int,
        val zLower: Double,
        val zUpper: Double,
        val timeSeconds: Double,
        val filamentUsedMm: Double,
        val filamentUsedG: Double,
    )

    /** Ein Extruder im Ergebnis, mit seiner Farbe aus dem Profil. */
    data class PreviewExtruder(
        val extruder: Int,
        val colorRgba: Long,
        val moveCount: Long,
        val timeSeconds: Double,
        val filamentUsedMm: Double,
        val filamentUsedG: Double,
    )

    /** Eine Merkmalsrolle im Ergebnis, mit libvgcodes Farbe. */
    data class PreviewRole(
        val role: Int,
        val colorRgba: Long,
        val moveCount: Long,
        val timeSeconds: Double,
        val filamentUsedMm: Double,
        val filamentUsedG: Double,
    )

    /** Was eine Rolle gekostet hat - Modell, Turm und Spuelen getrennt. */
    data class ExtruderUsage(
        val extruder: Int,
        val volumeMm3: Double,
        val wipeTowerMm3: Double,
        val flushMm3: Double,
    )

    private fun felder(roh: String?, erwartet: Int): List<String>? {
        val f = roh?.split('\t') ?: return null
        return if (f.size == erwartet) f else null
    }

    fun previewSnapshot(): PreviewSnapshot? {
        val f = felder(nativePreviewSnapshot(requireHandle()), 9) ?: return null
        return PreviewSnapshot(
            moveCount = f[0].toIntOrNull() ?: return null,
            layerCount = f[1].toIntOrNull() ?: return null,
            extruderCount = f[2].toIntOrNull() ?: return null,
            roleCount = f[3].toIntOrNull() ?: return null,
            printTimeSeconds = f[4].toDoubleOrNull() ?: return null,
            filamentUsedMm = f[5].toDoubleOrNull() ?: return null,
            filamentUsedG = f[6].toDoubleOrNull() ?: return null,
            minZ = f[7].toFloatOrNull() ?: return null,
            maxZ = f[8].toFloatOrNull() ?: return null,
        )
    }

    fun previewLayer(index: Int): PreviewLayer? {
        val f = felder(nativePreviewLayerAt(requireHandle(), index), 7) ?: return null
        return PreviewLayer(
            index = f[0].toIntOrNull() ?: return null,
            sourceLayerId = f[1].toIntOrNull() ?: return null,
            zLower = f[2].toDoubleOrNull() ?: return null,
            zUpper = f[3].toDoubleOrNull() ?: return null,
            timeSeconds = f[4].toDoubleOrNull() ?: return null,
            filamentUsedMm = f[5].toDoubleOrNull() ?: return null,
            filamentUsedG = f[6].toDoubleOrNull() ?: return null,
        )
    }

    fun previewExtruder(index: Int): PreviewExtruder? {
        val f = felder(nativePreviewExtruderAt(requireHandle(), index), 6) ?: return null
        return PreviewExtruder(
            extruder = f[0].toIntOrNull() ?: return null,
            colorRgba = f[1].toLongOrNull() ?: return null,
            moveCount = f[2].toLongOrNull() ?: return null,
            timeSeconds = f[3].toDoubleOrNull() ?: return null,
            filamentUsedMm = f[4].toDoubleOrNull() ?: return null,
            filamentUsedG = f[5].toDoubleOrNull() ?: return null,
        )
    }

    fun previewRole(index: Int): PreviewRole? {
        val f = felder(nativePreviewRoleAt(requireHandle(), index), 6) ?: return null
        return PreviewRole(
            role = f[0].toIntOrNull() ?: return null,
            colorRgba = f[1].toLongOrNull() ?: return null,
            moveCount = f[2].toLongOrNull() ?: return null,
            timeSeconds = f[3].toDoubleOrNull() ?: return null,
            filamentUsedMm = f[4].toDoubleOrNull() ?: return null,
            filamentUsedG = f[5].toDoubleOrNull() ?: return null,
        )
    }

    /** Wie viele Extruder im letzten Ergebnis wirklich gedruckt haben. */
    fun sliceExtruderCount(): Int = nativeSliceExtruderCount(requireHandle())

    fun sliceExtruder(index: Int): ExtruderUsage? {
        val f = felder(nativeSliceExtruderAt(requireHandle(), index), 4) ?: return null
        return ExtruderUsage(
            extruder = f[0].toIntOrNull() ?: return null,
            volumeMm3 = f[1].toDoubleOrNull() ?: return null,
            wipeTowerMm3 = f[2].toDoubleOrNull() ?: return null,
            flushMm3 = f[3].toDoubleOrNull() ?: return null,
        )
    }

    fun loadGcodeForPreview(path: String) =
        check(nativeLoadGcodeForPreview(requireHandle(), path), "G-Code laden")

    /* --- Anordnen ---------------------------------------------------- */

    /** Wie PrusaSlicer den Ausgang einer Anordnung meldet. */
    enum class ArrangeStatus(val raw: Int) {
        ARRANGED(0), EMPTY(1), LOCKED(2), FULL(3), UNKNOWN(-1);

        companion object {
            fun of(raw: Int) = entries.firstOrNull { it.raw == raw } ?: UNKNOWN
        }
    }

    data class ArrangeResult(
        val status: ArrangeStatus,
        val objectCount: Int,
        val instanceCount: Int,
        val ok: Boolean,
    )

    /**
     * Anordnen mit Optionen und Auskunft.
     *
     * Die nackte Fassung [arrange] sagt nur, dass es nicht ging. Diese
     * hier meldet gesperrt und voll als eigenen Zustand und liefert
     * dazu Objekt- und Instanzzahl - damit die Oberflaeche einen Satz
     * schreiben kann statt eines Fehlercodes.
     */
    fun arrangeBed(bedIndex: Int, gapMm: Float, allowRotation: Boolean): ArrangeResult {
        val f = nativeArrangeBedEx(
            requireHandle(), bedIndex, gapMm, if (allowRotation) 1 else 0,
        ).split('\t')
        if (f.size != 4) return ArrangeResult(ArrangeStatus.UNKNOWN, 0, 0, false)
        return ArrangeResult(
            status = ArrangeStatus.of(f[0].toIntOrNull() ?: -1),
            objectCount = f[1].toIntOrNull() ?: 0,
            instanceCount = f[2].toIntOrNull() ?: 0,
            ok = (f[3].toIntOrNull() ?: -1) == 0,
        )
    }

    /* --- Adaptive Schichthoehe --------------------------------------- */

    /**
     * PrusaSlicers adaptive Schichthoehe aus der Objektgeometrie.
     *
     * @param quality 0 grob bis 1 fein, wie der Regler am Desktop.
     * @return Paare aus z und Hoehe, leer wenn nichts zu rechnen war.
     */
    fun adaptiveLayerProfile(id: Int, quality: Float): List<Pair<Double, Double>> {
        val werte = nativeLayerProfileAdaptive(requireHandle(), id, quality)
            ?: return emptyList()
        return werte.toList().chunked(2).mapNotNull {
            if (it.size == 2) it[0] to it[1] else null
        }
    }

    /* --- Sonstiges ---------------------------------------------------- */

    /** Ob das letzte Ergebnis noch zum aktuellen Stand des Betts passt. */
    fun sliceResultIsCurrent(): Boolean = nativeSliceResultIsCurrent(requireHandle()) != 0

    /**
     * Fertigen G-Code von aussen uebernehmen, statt lokal neu zu
     * rechnen - der Weg fuer das Fernslicen.
     *
     * [requestRevision] ist die Szenenrevision, die beim Hochladen galt
     * ([designRevision]). Hat sich das Bett seither geaendert, weist der
     * Kern die Datei ab - sonst laege ein Ergebnis von vorhin auf einer
     * Anordnung von jetzt.
     */
    fun acceptRemoteGcode(path: String, requestRevision: Long) =
        check(
            nativeAcceptRemoteGcode(requireHandle(), path, requestRevision),
            "G-Code übernehmen",
        )

    fun designRevision(): Long = nativeDesignRevision(requireHandle())

    /** Lage eines Objekts zum Druckraum, feiner als [ObjectInfo.outsideBed]. */
    enum class BedState(val raw: Int) {
        INSIDE(0), COLLIDING(1), OUTSIDE(2), BELOW(3), UNKNOWN(4);

        companion object {
            fun of(raw: Int) = entries.firstOrNull { it.raw == raw } ?: UNKNOWN
        }
    }

    fun bedStateOf(id: Int): BedState =
        BedState.of(nativeBedStateOf(requireHandle(), id))

    /** Auf Flaeche legen fuer genau diese Kopie statt immer Instanz 0. */
    fun layOnFacet(id: Int, instance: Int, volume: Int, facet: Int) =
        check(
            nativeLayOnFacetInstance(requireHandle(), id, instance, volume, facet),
            "Auf Fläche legen",
        )

    fun clearPaint(id: Int, tool: PaintTool) =
        check(nativeClearPaint(requireHandle(), id, tool.raw), "Bemalung löschen")

    fun layerProfile(id: Int): List<Pair<Double, Double>> {
        val values = nativeLayerProfile(requireHandle(), id) ?: return emptyList()
        return values.toList().chunked(2).mapNotNull {
            if (it.size == 2) it[0] to it[1] else null
        }
    }

    fun setLayerProfile(id: Int, values: List<Pair<Double, Double>>) =
        check(
            nativeLayerProfileSet(
                requireHandle(), id,
                values.takeIf { it.isNotEmpty() }?.flatMap { (z, h) ->
                    listOf(z, h)
                }?.toDoubleArray(),
            ),
            "Variable Schichthöhe",
        )

    fun setObjectColour(id: Int, colour: String) =
        check(nativeObjectColourSet(requireHandle(), id, colour), "Objektfarbe")

    fun setObjectWipe(id: Int, intoInfill: Boolean, intoObjects: Boolean) =
        check(
            nativeObjectWipeSet(
                requireHandle(), id, intoInfill, intoObjects
            ),
            "Wischoptionen",
        )

    enum class CustomGcodeType(val raw: Int) {
        COLOR_CHANGE(0), PAUSE(1), TOOL_CHANGE(2), TEMPLATE(3), CUSTOM(4);

        companion object {
            fun fromRaw(raw: Int): CustomGcodeType =
                entries.firstOrNull { it.raw == raw } ?: CUSTOM
        }
    }

    data class CustomGcode(
        val printZ: Double,
        val type: CustomGcodeType,
        val extruder: Int = 0,
        val colour: String = "",
        val extra: String = "",
    )

    fun customGcodes(): List<CustomGcode> =
        (0 until nativeCustomGcodeCount(requireHandle())).mapNotNull { index ->
            val fields = nativeCustomGcodeAt(handle, index) ?: return@mapNotNull null
            if (fields.size < 5) return@mapNotNull null
            CustomGcode(
                printZ = fields[0].toDoubleOrNull() ?: 0.0,
                type = CustomGcodeType.fromRaw(fields[1].toIntOrNull() ?: 4),
                extruder = fields[2].toIntOrNull() ?: 0,
                colour = fields[3],
                extra = fields[4],
            )
        }

    fun addCustomGcode(value: CustomGcode) =
        check(
            nativeCustomGcodeAdd(
                requireHandle(), value.printZ, value.type.raw,
                value.extruder, value.colour, value.extra,
            ),
            "Custom G-Code hinzufügen",
        )

    fun updateCustomGcode(index: Int, value: CustomGcode) =
        check(
            nativeCustomGcodeUpdate(
                requireHandle(), index, value.printZ, value.type.raw,
                value.extruder, value.colour, value.extra,
            ),
            "Custom G-Code ändern",
        )

    fun removeCustomGcode(index: Int) =
        check(nativeCustomGcodeRemove(requireHandle(), index), "Custom G-Code entfernen")

    fun clearCustomGcode() =
        check(nativeCustomGcodeClear(requireHandle()), "Custom G-Code leeren")

    data class WipeTower(
        val x: Float,
        val y: Float,
        val rotationDegrees: Float,
    )

    fun wipeTower(): WipeTower {
        val values = nativeWipeTower(requireHandle()) ?: FloatArray(3)
        return WipeTower(
            values.getOrElse(0) { 0f },
            values.getOrElse(1) { 0f },
            values.getOrElse(2) { 0f },
        )
    }

    fun setWipeTower(value: WipeTower) =
        check(
            nativeWipeTowerSet(
                requireHandle(), value.x, value.y, value.rotationDegrees
            ),
            "Wipe-Tower positionieren",
        )

    fun presetNames(type: PresetType): List<String> {
        val h = requireHandle()
        val n = nativePresetCount(h, type.raw)
        return (0 until n).map { nativePresetNameAt(h, type.raw, it) }
    }

    /** Name und ob der Eintrag zum gewaehlten Drucker passt. */
    data class PresetEntry(val name: String, val compatible: Boolean)

    fun presetEntries(type: PresetType): List<PresetEntry> {
        val h = requireHandle()
        val n = nativePresetCount(h, type.raw)
        return (0 until n).map {
            PresetEntry(
                nativePresetNameAt(h, type.raw, it),
                nativePresetCompatibleAt(h, type.raw, it),
            )
        }
    }

    /**
     * Auch Profile auflisten, die zum gewaehlten Drucker nicht passen.
     * Entspricht PrusaSlicers "Show incompatible print and filament
     * presets" und ist standardmaessig aus.
     */
    var showIncompatiblePresets: Boolean
        get() = nativePresetShowsIncompatible(requireHandle())
        set(value) {
            check(
                nativePresetShowIncompatible(requireHandle(), value),
                "Unpassende Profile ein-/ausblenden",
            )
        }

    fun selectPreset(type: PresetType, name: String) =
        check(nativePresetSelect(requireHandle(), type.raw, name), "Preset waehlen")

    fun selectedPreset(type: PresetType): String = nativePresetSelected(requireHandle(), type.raw)

    /**
     * Ob ein Parameter im aktuellen Zustand ueberhaupt wirkt, und was
     * ihn sperrt. Die Regeln stammen woertlich aus PrusaSlicers
     * ConfigManipulation - siehe build/scripts/extract-toggles.py.
     */
    data class Enablement(val enabled: Boolean, val blockedBy: String)

    fun enablement(key: String): Enablement {
        val f = nativeConfigEnabled(requireHandle(), key).split('\t')
        return Enablement(f.getOrNull(0) != "0", f.getOrNull(1).orEmpty())
    }

    enum class Axis(val raw: Int) { X(0), Y(1), Z(2) }

    fun mirror(id: Int, axis: Axis) =
        check(nativeMirror(requireHandle(), id, axis.raw), "Spiegeln")

    /** Zahl der Kopien auf dem Bett. */
    fun setInstances(id: Int, count: Int) =
        check(nativeSetInstances(requireHandle(), id, count), "Kopien")

    /**
     * Der Dateiname, den PrusaSlicer vergeben wuerde - aus
     * output_filename_format des Druckprofils, also mit Modellname,
     * Schichthoehe, Material, Drucker und Druckzeit. Leer, solange nichts
     * geslict wurde.
     */
    fun suggestedGcodeName(): String = nativeGcodeSuggestedName(requireHandle())

    /**
     * Ein einzelner Eintrag eines Parameters, der je Extruder einen hat -
     * retract_length, nozzle_diameter und die uebrige Extruderseite.
     * Bei Parametern ohne Vektor dasselbe wie get/set.
     */
    fun getAt(key: String, index: Int): String? = nativeConfigGetAt(requireHandle(), key, index)

    fun setAt(key: String, index: Int, value: String) =
        check(nativeConfigSetAt(requireHandle(), key, index, value), "Wert je Extruder")

    /* --- Extruder ---------------------------------------------------- */

    /** Zahl der Extruder. Ein MMU3 hat fuenf, ein XL-5T ebenso. */
    fun extruderCount(): Int = nativeExtruderCount(requireHandle()).coerceAtLeast(1)

    fun extruderFilament(index: Int): String = nativeExtruderFilament(requireHandle(), index)

    fun setExtruderFilament(index: Int, name: String) =
        check(nativeExtruderFilamentSet(requireHandle(), index, name), "Filament je Extruder")

    /** "#RRGGBB", oder leer wenn die Farbe des Filaments gelten soll. */
    fun extruderColor(index: Int): String = nativeExtruderColor(requireHandle(), index)

    fun setExtruderColor(index: Int, rgb: String) =
        check(nativeExtruderColorSet(requireHandle(), index, rgb), "Farbe je Extruder")

    /**
     * Vollständige, 3MF-kompatible ColorMix-Konfiguration. Virtuelle
     * Extruder haben IDs oberhalb der physischen Köpfe und werden vom
     * Slicer beim Slice-Lauf in deren Mischfolge aufgelöst.
     */
    fun colorMixJson(): String = nativeColorMixJson(requireHandle())

    fun setColorMixJson(json: String) =
        check(nativeColorMixSetJson(requireHandle(), json), "ColorMix-Konfiguration")

    /* --- Filamenthersteller ------------------------------------------ */

    data class FilamentVendor(val name: String, val filaments: Int, val enabled: Boolean)

    fun filamentVendors(): List<FilamentVendor> =
        nativeFilamentVendors(requireHandle())
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 3) null
                else FilamentVendor(f[0], f[1].toIntOrNull() ?: 0, f[2] == "1")
            }
            .toList()

    /** Leere Liste blendet wieder alle ein. */
    fun setFilamentVendors(names: List<String>) =
        check(nativeFilamentVendorsSet(requireHandle(), names.toTypedArray()),
              "Hersteller waehlen")

    /* --- Geaenderte Werte gegenueber dem Preset ----------------------- */

    data class Change(val key: String, val was: String, val now: String)

    /**
     * Was gegenueber dem gewaehlten Preset abweicht - genau die Liste, die
     * der Desktop im Dialog "Unsaved Changes" zeigt.
     */
    fun changes(type: PresetType): List<Change> =
        nativePresetDirty(requireHandle(), type.raw)
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 3) null else Change(f[0], f[1], f[2])
            }
            .toList()

    fun discardChanges(type: PresetType) =
        check(nativePresetDiscard(requireHandle(), type.raw), "Aenderungen verwerfen")

    fun savePresetAs(type: PresetType, name: String) =
        check(nativePresetSaveAs(requireHandle(), type.raw, name), "Preset speichern")

    /** Preset wechseln und die genannten Werte danach wieder eintragen. */
    fun selectPresetKeeping(type: PresetType, name: String, keep: List<Change>) =
        check(nativePresetSelectKeeping(requireHandle(), type.raw, name,
                                        keep.map { it.key }.toTypedArray(),
                                        keep.map { it.now }.toTypedArray()),
              "Preset wechseln")

    operator fun get(key: String): String? = nativeConfigGet(requireHandle(), key)

    operator fun set(key: String, value: String) =
        check(nativeConfigSet(requireHandle(), key, value), "Parameter $key setzen")

    fun presetValue(type: PresetType, key: String): String? =
        nativePresetConfigGet(requireHandle(), type.raw, key)

    fun setPresetValue(type: PresetType, key: String, value: String) =
        check(
            nativePresetConfigSet(requireHandle(), type.raw, key, value),
            "Parameter $key im ${type.name.lowercase()}-Profil setzen",
        )

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
        5 -> SliceState.STALE
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

    enum class PlateFormat(val raw: Int) { STL(0), OBJ(1) }

    fun exportPlate(path: String, format: PlateFormat) =
        check(nativePlateExport(requireHandle(), path, format.raw), "Platte exportieren")

    fun repairStl(input: String, output: String) =
        check(nativeRepairStl(requireHandle(), input, output), "STL reparieren")

    fun convertGcode(input: String, output: String, toBinary: Boolean) =
        check(
            nativeConvertGcode(requireHandle(), input, output, toBinary),
            "G-Code konvertieren",
        )

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
