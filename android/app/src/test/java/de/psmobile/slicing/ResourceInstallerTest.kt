package de.psmobile.slicing

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceInstallerTest {
    @Test
    fun `INDX bundle exposes its shared bed assets in the vendor-relative path`() {
        val root = createTempDirectory("psmobile-indx-assets").toFile()
        try {
            val source = File(root, "profiles/PrusaResearch").apply { mkdirs() }
            File(source, "coreone_indx.stl").writeText("mesh")
            File(source, "coreone_indx.svg").writeText("texture")

            ensureIndxBedAssets(root)

            val target = File(root, "profiles/PSMobileINDX/PrusaResearch")
            assertTrue(File(target, "coreone_indx.stl").isFile)
            assertTrue(File(target, "coreone_indx.svg").isFile)
            assertEquals("mesh", File(target, "coreone_indx.stl").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
