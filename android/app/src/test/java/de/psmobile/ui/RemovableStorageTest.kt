package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovableStorageTest {

    private fun vol(
        name: String,
        removable: Boolean = true,
        primary: Boolean = false,
        mounted: Boolean = true,
    ) = RemovableStorage.Volume(name, removable, primary, mounted)

    @Test
    fun `ohne angeschlossenen Speicher gibt es kein Ziel`() {
        assertNull(RemovableStorage.target(emptyList()))
    }

    @Test
    fun `der eingebaute Speicher zaehlt nicht, auch wenn er entnehmbar heisst`() {
        // Manche Geraete melden den internen Speicher als removable.
        // Dafuer gibt es den gewoehnlichen Speichern-Weg.
        assertNull(
            RemovableStorage.target(
                listOf(vol("Interner Speicher", removable = true, primary = true))
            )
        )
    }

    @Test
    fun `ein nicht eingehaengter Stick ist kein Ziel`() {
        assertNull(RemovableStorage.target(listOf(vol("USB-Laufwerk", mounted = false))))
    }

    @Test
    fun `ein angeschlossener Stick wird gefunden`() {
        val found = RemovableStorage.target(
            listOf(vol("Interner Speicher", removable = false, primary = true), vol("USB-Laufwerk"))
        )
        assertEquals("USB-Laufwerk", found?.description)
    }

    @Test
    fun `bei mehreren nimmt es den ersten - mehr als einen Stick hat kaum jemand`() {
        val found = RemovableStorage.target(listOf(vol("SD-Karte"), vol("USB-Laufwerk")))
        assertEquals("SD-Karte", found?.description)
    }

    @Test
    fun `die Beschriftung nennt den Speicherort beim Namen`() {
        assertTrue(RemovableStorage.label(vol("SD-Karte")).contains("SD-Karte"))
    }

    @Test
    fun `ohne Namen bleibt die Beschriftung trotzdem lesbar`() {
        val text = RemovableStorage.label(vol("   "))
        assertTrue(text, text.isNotBlank())
        assertTrue(text, text.contains("USB"))
    }
}
