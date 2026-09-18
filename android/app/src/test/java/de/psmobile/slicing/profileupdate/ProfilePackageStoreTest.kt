package de.psmobile.slicing.profileupdate

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePackageStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var fallback: File
    private lateinit var store: ProfilePackageStore

    @Before fun setUp() {
        fallback = temp.newFolder("fallback")
        mandatoryEntries.forEach { entry ->
            File(fallback, entry).apply { parentFile!!.mkdirs(); writeText("fallback") }
        }
        store = ProfilePackageStore(temp.root, fallback)
    }

    @Test fun `valid package stages without replacing active resources`() {
        val zip = resourcesZip("valid.zip")
        val before = store.activeRoot().canonicalPath

        val staged = store.stage(zip, manifestFor(zip)).getOrThrow()

        assertTrue(File(staged, "profiles/PrusaResearch.ini").isFile)
        assertEquals(before, store.activeRoot().canonicalPath)
    }

    @Test fun `valid package with directory entries stages successfully`() {
        val zip = temp.newFile("directories.zip")
        ZipOutputStream(FileOutputStream(zip)).use { output ->
            listOf("profiles/", "shaders/", "shaders/ES/").forEach { directory ->
                output.putNextEntry(ZipEntry(directory))
                output.closeEntry()
            }
            mandatoryEntries.forEach { path ->
                output.putNextEntry(ZipEntry(path))
                output.write("updated".toByteArray())
                output.closeEntry()
            }
        }

        assertTrue(store.stage(zip, manifestFor(zip)).isSuccess)
    }

    @Test fun `path traversal package does not create staged resources`() {
        val zip = zip("traversal.zip", mapOf("../escaped.txt" to "nope"))

        assertTrue(store.stage(zip, manifestFor(zip)).isFailure)
        assertFalse(File(temp.root, "staged").exists())
        assertFalse(File(temp.root, "escaped.txt").exists())
    }

    @Test fun `activation and rollback restore the former active resources`() {
        val zip = resourcesZip("valid.zip")
        store.stage(zip, manifestFor(zip)).getOrThrow()

        store.activateStaged().getOrThrow()
        assertEquals("updated", File(store.activeRoot(), "profiles/PrusaResearch.ini").readText())

        store.rollback().getOrThrow()
        assertEquals("fallback", File(store.activeRoot(), "profiles/PrusaResearch.ini").readText())
    }

    private fun resourcesZip(name: String): File = zip(name, mandatoryEntries.associateWith { "updated" })

    private fun zip(name: String, entries: Map<String, String>): File = temp.newFile(name).also { file ->
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (path, contents) ->
                output.putNextEntry(ZipEntry(path))
                output.write(contents.toByteArray())
                output.closeEntry()
            }
        }
    }

    private fun manifestFor(zip: File) = ProfileManifest(
        version = ProfileVersion.parse("2.5.5")!!,
        packageUrl = java.net.URI("https://updates.example.test/2.5.5.zip"),
        sha256 = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) },
        minSlic3rVersion = ProfileVersion.parse("2.9.6")!!,
        releaseNotes = listOf("test"),
    )

    private companion object {
        val mandatoryEntries = listOf(
            "profiles/PrusaResearch.ini",
            "profiles/PrusaResearch.idx",
            "shaders/ES/test.glsl",
        )
    }
}
