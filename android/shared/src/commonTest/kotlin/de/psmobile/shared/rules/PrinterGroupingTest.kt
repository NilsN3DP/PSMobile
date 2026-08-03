package de.psmobile.shared.rules

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PrinterGroupingTest {

    private fun grouped(models: List<Model>) =
        PrinterGrouping.grouped(models) { it.family }


    // Ein eigener Miniaturtyp statt PsmCore.PrinterModel: der Test prueft
    // die Gruppierung, nicht die JNI-Bruecke.
    private data class Model(val name: String, val family: String)

    private fun model(name: String, family: String) = Model(name, family)

    @Test
    fun `die Reihenfolge der Familien kommt aus der Vendor-Datei, nicht aus dem Alphabet`() {
        val groups = grouped(
            listOf(
                model("Prusa CORE One", "CORE"),
                model("Original Prusa MK4S", "MK4"),
                model("Original Prusa MK3.9S", "MK3.9"),
                model("Prusa CORE One L", "CORE"),
            )
        )
        // Alphabetisch waere CORE, MK3.9, MK4 - so steht es aber nicht
        // in der Vendor-Datei.
        assertEquals(listOf("CORE", "MK4", "MK3.9"), groups.map { it.family })
        assertEquals(2, groups.first().models.size)
    }

    @Test
    fun `Altgeraete stehen am Ende, egal wo sie in der Quelle auftauchen`() {
        val groups = grouped(
            listOf(
                model("Original Prusa XL", "Legacy profiles"),
                model("Prusa CORE One", "CORE"),
                model("Original Prusa MK4S", "MK4"),
            )
        )
        assertEquals(listOf("CORE", "MK4", "Legacy profiles"), groups.map { it.family })
        assertTrue(groups.last().isLegacy)
        assertFalse(groups.first().isLegacy)
    }

    @Test
    fun `mehrere Altgeraete-Familien behalten untereinander ihre Reihenfolge`() {
        val groups = grouped(
            listOf(
                model("A", "Legacy profiles"),
                model("B", "MK4"),
                model("C", "Legacy SLA"),
            )
        )
        assertEquals(listOf("MK4", "Legacy profiles", "Legacy SLA"), groups.map { it.family })
    }

    @Test
    fun `ohne Familienangabe landet der Drucker sichtbar unter Weitere`() {
        val groups = grouped(
            listOf(model("Fremdgeraet", "")))
        assertEquals(listOf(PrinterGrouping.OTHER), groups.map { it.family })
        assertEquals(1, groups.first().models.size)
    }

    @Test
    fun `Legacy wird unabhaengig von Gross- und Kleinschreibung erkannt`() {
        assertTrue(PrinterGrouping.isLegacy("Legacy profiles"))
        assertTrue(PrinterGrouping.isLegacy("legacy"))
        assertFalse(PrinterGrouping.isLegacy("MK4"))
    }

    @Test
    fun `eine leere Liste ergibt keine Gruppen`() {
        assertTrue(grouped(emptyList()).isEmpty())
    }
}
