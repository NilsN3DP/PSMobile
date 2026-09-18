package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayerProfileTest {

    private fun zeilen(vararg paare: Pair<String, String>) =
        paare.map { LayerProfile.Row(it.first, it.second) }

    @Test
    fun `ein Profil braucht zwei steigende Punkte`() {
        // Der Kern lehnt alles andere ab - und der Nutzer saehe dann nur,
        // dass nichts passiert.
        assertTrue(LayerProfile.canApply(zeilen("0.0" to "0.2", "20.0" to "0.1")))
        assertFalse(LayerProfile.canApply(zeilen("0.0" to "0.2")))
        assertFalse(LayerProfile.canApply(zeilen("5.0" to "0.2", "5.0" to "0.1")))
        assertFalse(LayerProfile.canApply(zeilen("5.0" to "0.2", "1.0" to "0.1")))
        assertFalse(LayerProfile.canApply(zeilen("0.0" to "0.0", "5.0" to "0.2")))
        assertFalse(LayerProfile.canApply(zeilen("0.0" to "0.2", "abc" to "0.1")))
    }

    @Test
    fun `Komma und Punkt gelten gleich`() {
        // Je nach Tastatur kommt das eine oder das andere.
        assertTrue(LayerProfile.canApply(zeilen("0,0" to "0,2", "20,0" to "0,1")))
    }

    @Test
    fun `die Vorschau reicht bis zur Modellhoehe`() {
        val baender = LayerProfile.segments(
            20.0,
            listOf(LayerProfile.Point(0.0, 0.2), LayerProfile.Point(10.0, 0.1)),
        )
        assertEquals(2, baender.size)
        assertEquals(0.0, baender[0].fromZ)
        assertEquals(10.0, baender[0].toZ)
        // Das letzte Band endet oben am Modell, nicht am letzten Punkt.
        assertEquals(20.0, baender[1].toZ)
    }

    @Test
    fun `ein neuer Punkt landet im Modell, nicht darueber`() {
        // Wer oben anfuegt, bekommt eine Stuetzstelle, die nie erreicht
        // wird.
        val neu = LayerProfile.addPoint(zeilen("0.0" to "0.2", "20.0" to "0.2"), 20.0)
        assertEquals(3, neu.size)
        val mitte = neu[1].z.toDouble()
        assertTrue(mitte > 0.0 && mitte < 20.0, "Punkt liegt bei $mitte")
    }

    @Test
    fun `unter zwei Punkten wird nichts entfernt`() {
        val zwei = zeilen("0.0" to "0.2", "20.0" to "0.2")
        assertEquals(2, LayerProfile.removePoint(zwei, 0).size)
        val drei = LayerProfile.addPoint(zwei, 20.0)
        assertEquals(2, LayerProfile.removePoint(drei, 1).size)
    }

    @Test
    fun `doppelte Z-Werte aus dem Kern werden zusammengezogen`() {
        // psm_model_layer_profile_adaptive liefert eine Treppe: an der
        // Stufe stehen zwei Paare mit demselben z. Roh uebernommen
        // waeren die Z-Werte nicht mehr streng steigend und canApply
        // faende das Profil ungueltig - der Nutzer saehe nur einen
        // grauen Uebernehmen-Knopf.
        val punkte = listOf(
            LayerProfile.Point(0.0, 0.2),
            LayerProfile.Point(0.2, 0.2),
            LayerProfile.Point(0.2, 0.25),
            LayerProfile.Point(0.45, 0.25),
        )
        val zeilen = LayerProfile.fromPoints(20.0, punkte)
        assertEquals(listOf("0.0", "0.20", "0.45"), zeilen.map { it.z })
        // Ab der Stufe gilt die neue Dicke, nicht die alte.
        assertEquals("0.25", zeilen[1].height)
        assertTrue(LayerProfile.canApply(zeilen))
    }

    @Test
    fun `bleibt nach dem Zusammenziehen nur ein Punkt, greifen die Vorgaben`() {
        val punkte = listOf(
            LayerProfile.Point(0.0, 0.2),
            LayerProfile.Point(0.0, 0.3),
        )
        assertEquals(LayerProfile.defaults(20.0), LayerProfile.fromPoints(20.0, punkte))
    }
}
