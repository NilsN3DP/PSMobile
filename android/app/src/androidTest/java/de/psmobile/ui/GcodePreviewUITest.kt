package de.psmobile.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/GcodePreviewUITests.swift`.
 *
 * Prueft den G-Code-Vorschau-Weg im Expertenmodus: Randregler direkt am
 * Viewport plus Statistik und Legende im rechten Menueband - kein
 * schwebendes Blatt mehr, das erst weggetippt werden muss.
 */
@RunWith(AndroidJUnit4::class)
class GcodePreviewUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich", 180)
        // Kein warteAufModelle: "blatt.zeile." ist der Simple Mode. Im
        // Expertenmodus liegt die Objektliste hinter ihrem Reiter, und
        // was auf dem Bett liegt, sagt der Startschalter.
    }

    /**
     * Schneidet und wartet auf den Export-Knopf - kein Blatt mehr, das
     * dafuer erst weggetippt werden muesste.
     */
    private fun schneidenUndWarten() {
        tippe("slicen", 5)
        warteAuf("slice.sichern", 180)
    }

    @Test
    fun vorschauZeigtRandreglerStattSchwebenderKarte() {
        schneidenUndWarten()

        tippe("werkzeug.vorschau")
        warteAuf("vorschau.panel", 10)

        // Kein schwebendes Blatt mehr - die alten Marken der Karte
        // duerfen nicht mehr existieren.
        assertFalse(
            "Die alte schwebende Vorschau-Karte ist noch da",
            existiert("vorschau.seitenkarte"),
        )
        assertFalse(
            "Das alte Vorschau-Bottomsheet ist noch da",
            existiert("vorschau.bottomsheet"),
        )

        // Die beiden Randregler: senkrecht links fuer den Schichtbereich,
        // waagerecht unten fuer den Werkzeugweg innerhalb der Schicht.
        warteAuf("vorschau.schicht.unten", 5)
        assertTrue(existiert("vorschau.schicht.oben"))
    }
}
