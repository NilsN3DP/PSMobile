package de.psmobile.slicing

import android.content.Context
import android.util.Log
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
        val target = File(context.filesDir, "resources")
        val stampFile = File(target, STAMP)
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) {
            "0"
        }

        if (stampFile.exists() && stampFile.readText().trim() == version) {
            return target
        }

        Log.i(TAG, "entpacke Ressourcen nach $target")
        target.deleteRecursively()
        target.mkdirs()
        copyAssetDir(context, ASSET_ROOT, target)
        stampFile.writeText(version)
        return target
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
}
