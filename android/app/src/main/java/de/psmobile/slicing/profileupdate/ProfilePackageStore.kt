package de.psmobile.slicing.profileupdate

import de.psmobile.shared.rules.SimpleModeState
import java.io.File
import java.io.FileInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** App-private, rollback-capable storage for a complete psresources package. */
class ProfilePackageStore(
    private val root: File,
    private val fallbackRoot: File,
) {
    private val active = File(root, "active")
    private val staged = File(root, "staged")
    private val previous = File(root, "previous")

    fun activeRoot(): File = active.takeIf(File::isDirectory) ?: fallbackRoot

    /** Version des tatsächlich aktiven Pakets, nicht die App-Version. */
    fun activeVersion(): ProfileVersion =
        File(activeRoot(), VERSION_FILE).takeIf(File::isFile)
            ?.readText()?.trim()
            ?.let(ProfileVersion::parse)
            ?: ProfileVersion(0, 0, 0)

    fun stage(zip: File, manifest: ProfileManifest): Result<File> = runCatching {
        require(zip.isFile) { "Profilpaket fehlt" }
        require(sha256(zip) == manifest.sha256) { SimpleModeState.text(
            "Profile package checksum does not match",
            "Profilpaket-Prüfsumme stimmt nicht") }
        root.mkdirs()
        val incoming = File(root, "incoming-${System.nanoTime()}")
        try {
            extract(zip, incoming)
            requireComplete(incoming)
            File(incoming, VERSION_FILE).writeText(manifest.version.toString())
            staged.takeIf(File::exists)?.deleteRecursively()
            move(incoming, staged)
            staged
        } catch (error: Throwable) {
            incoming.deleteRecursively()
            throw error
        }
    }

    fun activateStaged(): Result<Unit> = runCatching {
        require(staged.isDirectory) { "Kein vorgemerktes Profilupdate" }
        previous.takeIf(File::exists)?.deleteRecursively()
        if (active.isDirectory) move(active, previous) else copyTree(fallbackRoot, previous)
        try {
            move(staged, active)
        } catch (error: Throwable) {
            previous.takeIf(File::exists)?.let { move(it, active) }
            throw error
        }
    }

    fun activateStagedOnLaunch(): Result<Boolean> = runCatching {
        if (!staged.isDirectory) false else {
            activateStaged().getOrThrow()
            true
        }
    }

    fun rollback(): Result<Unit> = runCatching {
        require(previous.isDirectory) { SimpleModeState.text(
            "No previous profile state available",
            "Kein vorheriger Profilstand verfügbar") }
        active.takeIf(File::exists)?.deleteRecursively()
        move(previous, active)
    }

    private fun extract(zip: File, destination: File) {
        val rootPath = destination.canonicalFile.toPath()
        ZipInputStream(FileInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                require(!entry.name.startsWith('/') && !entry.name.startsWith('\\')) {
                    "Absoluter ZIP-Pfad ist nicht erlaubt"
                }
                val output = File(destination, entry.name).canonicalFile
                require(output.toPath().startsWith(rootPath)) { SimpleModeState.text("ZIP path leaves the package", "ZIP-Pfad verlässt das Paket") }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { input.copyTo(it) }
                }
                input.closeEntry()
            }
        }
    }

    private fun requireComplete(directory: File) {
        requiredFiles.forEach { path ->
            require(File(directory, path).isFile) { SimpleModeState.text(
            "Profile package does not contain $path",
            "Profilpaket enthält $path nicht") }
        }
        require(File(directory, "shaders/ES").isDirectory) { SimpleModeState.text(
            "Profile package contains no shaders", "Profilpaket enthält keine Shader") }
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun copyTree(source: File, destination: File) {
        require(source.isDirectory) { "Fallback-Ressourcen fehlen" }
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source)
            val target = File(destination, relative.path)
            if (file.isDirectory) target.mkdirs() else {
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val VERSION_FILE = ".psmobile-profile-version"
        val requiredFiles = listOf(
            "profiles/PrusaResearch.ini",
            "profiles/PrusaResearch.idx",
            "profiles/PrusaResearchSLA.ini",
            "profiles/PrusaResearchSLA.idx",
        )
    }
}
