package de.psmobile.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the architectural integration: Simple must show the same contract
 * backed strip as Advanced rather than retain its own selected-bed chips.
 */
class SimpleBedContractIntegrationTest {

    private fun source(path: String): String = sequenceOf(
        File("src/main/java/de/psmobile/ui/$path"),
        File("android/app/src/main/java/de/psmobile/ui/$path"),
    ).first { it.isFile }.readText()

    @Test
    fun `simple routes visible bed selection through the shared selector`() {
        val screen = source("SimpleModeScreen.kt")
        val sheet = source("SimpleModelSheet.kt")

        assertTrue(screen.contains("BedSelector("))
        assertTrue(screen.contains("lockedBeds by service.lockedBeds.collectAsState()"))
        assertFalse(sheet.contains("beds.forEachIndexed { index, bed ->"))
    }
}
