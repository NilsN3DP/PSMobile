package de.psmobile.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.psmobile.slicing.ResourceInstaller
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the real packaged boundary: Kotlin bridge, bundled native core and
 * shipped vendor profiles must start together and expose usable presets.
 */
@RunWith(AndroidJUnit4::class)
class CoreCatalogStartupTest {

    @Test
    fun packagedCoreLoadsCompletePrinterAndFilamentCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = ResourceInstaller.ensureInstalled(context)
        val dataDir = context.cacheDir.resolve("core-catalog-contract").apply {
            deleteRecursively()
            mkdirs()
        }

        val core = PsmCore.create(dataDir.absolutePath, resources.absolutePath)
        try {
            val models = core.scanPrinterModels()
            assertTrue("Expected the complete shipped printer catalog, got ${models.size}", models.size >= 250)

            core.installPresets(listOf("PrusaResearch:MK4S:0.4"))
            val printers = core.presetNames(PsmCore.PresetType.PRINTER)
            val filaments = core.presetNames(PsmCore.PresetType.FILAMENT)
            assertTrue("MK4S printer presets were not installed", printers.any { "MK4S" in it })
            assertTrue("MK4S filament catalog is unexpectedly empty", filaments.size >= 8)
        } finally {
            core.close()
        }
    }
}
