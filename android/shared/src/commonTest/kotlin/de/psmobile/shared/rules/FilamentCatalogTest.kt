package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilamentCatalogTest {

    private val bestand = listOf(
        FilamentCatalog.entry("Prusament PLA", "PLA", "#FF8000"),
        FilamentCatalog.entry("Prusament PETG", "PETG", "ff8000"),
        FilamentCatalog.entry("Generic PLA", "PLA", "#000000"),
        FilamentCatalog.entry("Generic ABS", "abs", "#FFFFFF"),
        FilamentCatalog.entry("Filatech FilaFlex40", "FLEX", ""),
    )

    @Test
    fun derHerstellerStecktImNamen() {
        assertEquals("Prusament", FilamentCatalog.vendorOf("Prusament PLA Blend"))
        // Ein einzelnes Wort ist selbst der Hersteller und nicht leer.
        assertEquals("Eigenbau", FilamentCatalog.vendorOf("Eigenbau"))
    }

    @Test
    fun farbenWerdenVergleichbar() {
        // Mit und ohne Raute, gross und klein, ist dieselbe Farbe -
        // sonst faende der Farbpunkt die Haelfte seiner Rollen nicht.
        assertEquals(bestand[0].colorHex, bestand[1].colorHex)
        // Was keine Farbe ist, wird auch keine.
        assertEquals("", FilamentCatalog.normalizeColor("orange"))
        assertEquals("", FilamentCatalog.normalizeColor("#GGGGGG"))
    }

    @Test
    fun gaengigeTypenStehenVorn() {
        val typen = FilamentCatalog.types(bestand)
        assertEquals(listOf("PLA", "PETG", "ABS", "FLEX"), typen)
    }

    @Test
    fun sucheGehtWortweiseUndOhneReihenfolge() {
        val treffer = FilamentCatalog.filter(bestand, query = "pla prusament")
        assertEquals(listOf("Prusament PLA"), treffer.map { it.rawPreset })
    }

    @Test
    fun typUndFarbeSchraenkenGemeinsamEin() {
        val treffer = FilamentCatalog.filter(bestand, type = "PLA", colorHex = "ff8000")
        assertEquals(listOf("Prusament PLA"), treffer.map { it.rawPreset })
    }

    @Test
    fun ohneAngabeWirdNichtGefiltert() {
        assertEquals(bestand.size, FilamentCatalog.filter(bestand).size)
    }

    @Test
    fun haeufigsteFarbeZuerst() {
        val farben = FilamentCatalog.colors(bestand)
        assertEquals("#FF8000", farben.first())
        // Die leere Farbe des Flex-Profils ist keine Farbe.
        assertTrue(farben.none { it.isBlank() })
    }
}
