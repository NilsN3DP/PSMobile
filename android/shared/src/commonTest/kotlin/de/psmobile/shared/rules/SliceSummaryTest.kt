package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SliceSummaryTest {

    @Test
    fun `kurze Drucke nennen keine Stunden`() {
        // "0h 7m" liest sich schlechter als "7m" - und niemand rechnet
        // beim Lesen die fuehrende Null weg.
        assertEquals("7m", SliceSummary.duration(7 * 60.0))
        assertEquals("2h 14m", SliceSummary.duration(2 * 3600.0 + 14 * 60))
        assertEquals("1d 3h 5m", SliceSummary.duration(86_400.0 + 3 * 3600 + 5 * 60))
    }

    @Test
    fun `ohne Ergebnis steht ein Strich`() {
        // Eine Null waere eine Aussage. Ein Strich sagt: dazu liegt
        // nichts vor.
        assertEquals("–", SliceSummary.duration(0.0))
        assertEquals("–", SliceSummary.grams(0.0))
        assertEquals("–", SliceSummary.length(0.0))
    }

    @Test
    fun `kurze Filamentlaengen bleiben in Millimetern`() {
        // Bei einem Kalibrierwuerfel ist "0.4 m" keine Auskunft.
        assertEquals("420 mm", SliceSummary.length(420.0))
        assertEquals("2.5 m", SliceSummary.length(2500.0))
    }

    @Test
    fun `ohne hinterlegten Preis faellt die Kostenzeile weg`() {
        // In den meisten Profilen steht als Filamentpreis eine Null.
        // Eine Zeile "0.00" waere eine Behauptung.
        assertNull(SliceSummary.cost(0.0))
        assertEquals("12.34", SliceSummary.cost(12.34))

        val zeilen = SliceSummary.rows(3600.0, 20.0, 6000.0, 0.0, 2)
        assertTrue(zeilen.none { it.label.contains("Cost") || it.label.contains("Kosten") })
    }

    @Test
    fun `der Dateiname ueberlebt einen unfreundlichen Modellnamen`() {
        assertEquals("Benchy.gcode", SliceSummary.fileName("Benchy.stl"))
        assertEquals("mein_Teil_v2.gcode", SliceSummary.fileName("mein Teil v2"))
        assertEquals("psmobile.gcode", SliceSummary.fileName("   "))
        // Punkte und Schraegstriche im Namen haben in einem Dateinamen
        // nichts verloren.
        assertEquals("a_b_c.gcode", SliceSummary.fileName("a/b.c"))
    }

    @Test
    fun `jeder Grund einzeln genannt`() {
        // Ein ausgegrauter Knopf ohne Begruendung laesst raten.
        val gruende = SliceSummary.blockers(0, "", "PLA", "0.20mm")
        assertEquals(2, gruende.size)
        assertTrue(SliceSummary.blockers(1, "MK4S", "PLA", "0.20mm").isEmpty())
    }
}
