package de.psmobile.slicing

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResourceInstallerTest {
    @Test
    fun `active resource root does not materialize obsolete INDX fallback`() {
        val root = createTempDirectory("psmobile-resources").toFile()
        try {
            val fallback = File(root, "fallback").apply { mkdirs() }

            val active = ResourceInstaller.activeResourceRoot(root, fallback)

            assertEquals(fallback, active)
            assertFalse(File(fallback, "profiles/PSMobileINDX.ini").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
