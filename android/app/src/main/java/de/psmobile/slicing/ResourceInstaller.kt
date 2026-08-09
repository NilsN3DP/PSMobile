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

    internal fun activeResourceRoot(resourceRoot: File, fallback: File): File =
        ProfilePackageStore(resourceRoot, fallback).activeRoot()

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
            return activeResourceRoot(resourceRoot, fallback)
        }

        Log.i(TAG, "entpacke Fallback-Ressourcen nach $fallback")
        fallback.deleteRecursively()
        fallback.mkdirs()
        copyAssetDir(context, ASSET_ROOT, fallback)
        stampFile.writeText(version)
        return activeResourceRoot(resourceRoot, fallback)
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

    // ensureIndxBundle() ist raus: das app-eigene Notprofil ist ueberholt,
    // seit PrusaResearch.ini selbst (aus Prusas eigenem Live-Update-
    // Kanal, config_version 2.5.5) echte CORE-One-INDX-Profile mitbringt
    // - siehe stage-resources.sh. Zwei "Prusa CORE One INDX 4T"-Eintraege
    // (einer echt, einer von hier) waeren in der Liste verwirrend
    // doppelt gewesen.
}
