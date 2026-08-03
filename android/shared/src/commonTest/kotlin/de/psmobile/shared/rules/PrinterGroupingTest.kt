package de.psmobile.shared.rules

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PrinterGroupingTest {

    // Ein eigener Miniaturtyp statt PsmCore.PrinterModel: der Test prueft
    // die Gruppierung, nicht die JNI-Bruecke.
    private data class Model(val name: String, val family: String)

    private fun model(name: String, family: String) = Model(name, family)

    /**
     * So, wie die Aufrufer es tun: Familien hinein, und ueber die
     * zurueckgegebenen Positionen wieder zu den eigenen Modellen.
     */
    private fun grouped(models: List<Model>) =
        PrinterGrouping.group(models.map { it.family })

    private fun models(gruppe: PrinterGrouping.Group, aus: List<Model>) =
        gruppe.indices.map { aus[it] }

    @Test
    fun `die Reihenfolge der Familien kommt aus der Vendor-Datei, nicht aus dem Alphabet`() {
        val liste = listOf(
            model("Prusa CORE One", "CORE"),
            model("Original Prusa MK4S", "MK4"),
            model("Original Prusa MK3.9S", "MK3.9"),
            model("Prusa CORE One L", "CORE"),
        )
        val groups = grouped(liste)
        // Alphabetisch waere CORE, MK3.9, MK4 - so steht es aber nicht
        // in der Vendor-Datei.
        assertEquals(listOf("CORE", "MK4", "MK3.9"), groups.map { it.family })
        assertEquals(2, models(groups.first(), liste).size)
        assertEquals("Prusa CORE One L", models(groups.first(), liste)[1].name)
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
        val liste = listOf(model("Fremdgeraet", ""))
        val groups = grouped(liste)
        assertEquals(listOf(PrinterGrouping.OTHER), groups.map { it.family })
        assertEquals(1, models(groups.first(), liste).size)
    }

    @Test
    fun `die Positionen zeigen auf die richtigen Modelle`() {
        // Der eigentliche Vertrag der neuen Form: wer die Positionen
        // zurueckbekommt, muss damit seine eigenen Objekte wiederfinden.
        val liste = listOf(
            model("A", "MK4"),
            model("B", "CORE"),
            model("C", "MK4"),
        )
        val mk4 = grouped(liste).first { it.family == "MK4" }
        assertEquals(listOf(0, 2), mk4.indices)
        assertEquals(listOf("A", "C"), models(mk4, liste).map { it.name })
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
