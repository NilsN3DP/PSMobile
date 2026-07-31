package de.psmobile.slicing

import android.content.Context
import android.util.Log
import de.psmobile.slicing.profileupdate.ProfilePackageStore
import java.io.File

/**
 * Entpackt die PrusaSlicer-Ressourcen (Profile, Shader) aus den Assets
 * ins Dateisystem.
 *
 * Warum ueberhaupt entpacken: libslic3r arbeitet durchgehend mit
 * Dateipfaden, nicht mit Streams. Assets liegen im APK und haben keinen
 * Pfad. Einmal beim ersten Start entpacken ist der pragmatische Weg.
 *
 * Ein Versionsstempel verhindert, dass bei jedem Start neu kopiert wird.
 */
object ResourceInstaller {

    private const val TAG = "ResourceInstaller"
    private const val ASSET_ROOT = "psresources"
    private const val STAMP = ".installed-version"

    fun ensureInstalled(context: Context): File {
        val resourceRoot = File(context.filesDir, "profile-resources")
        val fallback = File(resourceRoot, "fallback")
        val stampFile = File(fallback, STAMP)
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }

        if (stampFile.exists() && stampFile.readText().trim() == version) {
            ensureIndxBundle(fallback)
            return ProfilePackageStore(resourceRoot, fallback).activeRoot().also(::ensureIndxBundle)
        }

        Log.i(TAG, "entpacke Fallback-Ressourcen nach $fallback")
        fallback.deleteRecursively()
        fallback.mkdirs()
        copyAssetDir(context, ASSET_ROOT, fallback)
        ensureIndxBundle(fallback)
        stampFile.writeText(version)
        return ProfilePackageStore(resourceRoot, fallback).activeRoot().also(::ensureIndxBundle)
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val entries = context.assets.list(assetPath) ?: return
        if (entries.isEmpty()) {
            // Blatt: es ist eine Datei
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            return
        }
        target.mkdirs()
        for (name in entries) {
            copyAssetDir(context, "$assetPath/$name", File(target, name))
        }
    }

    /** INDX is kept app-owned until it ships in the upstream vendor bundle. */
    private fun ensureIndxBundle(resources: File) {
        val file = File(resources, "profiles/PSMobileINDX.ini")
        if (file.isFile) return
        file.parentFile?.mkdirs()
        file.writeText(
            """
            [vendor]
            repo_id = psmobile-indx
            name = PSMobile INDX
            config_version = 1.0.0

            [printer_model:COREONE_INDX4T]
            name = Prusa CORE One INDX 4T
            variants = HF0.4
            technology = FFF
            family = CORE
            bed_model = PrusaResearch/coreone_indx.stl
            bed_texture = PrusaResearch/coreone_indx.svg

            [printer_model:COREONE_INDX8T]
            name = Prusa CORE One INDX 8T
            variants = HF0.4
            technology = FFF
            family = CORE
            bed_model = PrusaResearch/coreone_indx.stl
            bed_texture = PrusaResearch/coreone_indx.svg

            [printer:Prusa CORE One INDX 4T HF0.4 nozzle]
            printer_model = COREONE_INDX4T
            printer_variant = HF0.4
            bed_shape = 0x0,248x0,248x205,0x205
            max_print_height = 270
            gcode_flavor = marlin2
            nozzle_diameter = 0.4,0.4,0.4,0.4
            extruder_colour = #F58231;#1F77B4;#2CA02C;#D62728
            extruder_offset = 0x0,0x0,0x0,0x0
            start_gcode = M862.3 P "COREONEINDX"\nG90\nM83
            toolchange_gcode = T[next_extruder] S1 L0 D0
            default_filament_profile = PSMobile INDX PLA
            default_print_profile = 0.20mm Balanced @PSMobile INDX

            [printer:Prusa CORE One INDX 8T HF0.4 nozzle]
            printer_model = COREONE_INDX8T
            printer_variant = HF0.4
            bed_shape = 0x0,248x0,248x205,0x205
            max_print_height = 270
            gcode_flavor = marlin2
            nozzle_diameter = 0.4,0.4,0.4,0.4,0.4,0.4,0.4,0.4
            extruder_colour = #F58231;#1F77B4;#2CA02C;#D62728;#9467BD;#8C564B;#17BECF;#BCBD22
            extruder_offset = 0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0
            start_gcode = M862.3 P "COREONEINDX"\nG90\nM83
            toolchange_gcode = T[next_extruder] S1 L0 D0
            default_filament_profile = PSMobile INDX PLA
            default_print_profile = 0.20mm Balanced @PSMobile INDX

            [print:0.20mm Balanced @PSMobile INDX]
            layer_height = 0.2
            fill_density = 15%
            fill_pattern = grid
            perimeters = 3
            compatible_printers = Prusa CORE One INDX 4T HF0.4 nozzle;Prusa CORE One INDX 8T HF0.4 nozzle

            [filament:PSMobile INDX PLA]
            filament_type = PLA
            filament_colour = #F58231
            temperature = 215
            first_layer_temperature = 220
            bed_temperature = 60
            first_layer_bed_temperature = 60
            compatible_printers = Prusa CORE One INDX 4T HF0.4 nozzle;Prusa CORE One INDX 8T HF0.4 nozzle
            """.trimIndent(),
        )
    }
}
