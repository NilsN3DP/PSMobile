package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Wahl des Standardfilaments.
 *
 * Der Fall, der den Anlass gab: bei einem MK4S stand "Ultrafuse PET" im
 * Feld, weil es alphabetisch vorne liegt. Genau das darf nicht mehr
 * passieren, solange irgendein PLA in der Liste steht.
 */
class DefaultsTest {

    @Test
    fun exaktesPrusamentGewinnt() {
        val liste = listOf("Ultrafuse PET", "Prusament PLA", "Generic PLA")
        assertEquals("Prusament PLA", Defaults.preferredFilament(liste))
    }

    @Test
    fun prusamentMitZusatzGewinntVorFremdemPLA() {
        val liste = listOf("Generic PLA", "Prusament PLA Blend @MK4S")
        assertEquals("Prusament PLA Blend @MK4S", Defaults.preferredFilament(liste))
    }

    @Test
    fun ohnePrusamentIrgendeinPLA() {
        val liste = listOf("Ultrafuse PET", "Generic PLA @MK4S", "Generic PETG")
        assertEquals("Generic PLA @MK4S", Defaults.preferredFilament(liste))
    }

    @Test
    fun ohnePLAKeineMeinung() {
        // Dann bleibt es bei dem, was der Kern gewaehlt hat - eine
        // erfundene Wahl waere schlechter als gar keine.
        assertNull(Defaults.preferredFilament(listOf("Ultrafuse PET", "Generic PETG")))
    }

    @Test
    fun leereListeIstKeinFehler() {
        assertNull(Defaults.preferredFilament(emptyList()))
    }
}
