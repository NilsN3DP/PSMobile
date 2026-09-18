package de.psmobile.slicing

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test
import de.psmobile.slicing.profileupdate.ProfilePackageStore

class ResourceInstallerTest {
    @Test
    fun `active resource root falls back to the bundled resource directory`() {
        val root = createTempDirectory("psmobile-resources").toFile()
        try {
            val fallback = root.resolve("fallback").apply { mkdirs() }

            val active = ProfilePackageStore(root, fallback).activeRoot()

            assertEquals(fallback, active)
        } finally {
            root.deleteRecursively()
        }
    }
}
