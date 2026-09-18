package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/PreviewUITests.swift`.
 *
 * Prueft die Werkzeugspalte und die G-Code-Vorschau: die Gizmos gibt es
 * nur mit Auswahl, und die Vorschau erst nach dem Schneiden.
 *
 * Die Bildvergleiche der Vorlage (Ausschnitt vor und nach dem Ziehen des
 * Schichtreglers) fehlen hier: sie messen, ob sich das Bild geaendert
 * hat, und dafuer braucht es einen stabilen Bildausschnitt. Der Viewport
 * zeichnet auf dem Emulator weiter, waehrend der Test misst.
 */
@RunWith(AndroidJUnit4::class)
class PreviewUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)
    }

    @Test
    fun gizmosErscheinenNurMitAuswahl() {
        // Ohne ausgewaehltes Objekt haengt kein Gizmo an irgendetwas -
        // die Knoepfe waeren dann Zierde.
        assertFalse(
            "Die Gizmo-Knoepfe stehen ohne Auswahl da",
            existiert("werkzeug.drehen"),
        )

        tippeErstesMit("blatt.zeile.")

        warteAuf("werkzeug.drehen", 5)
        tippe("werkzeug.drehen")
        // Umschalten darf die Auswahl nicht verlieren - sonst waere das
        // Gizmo im selben Moment wieder weg.
        assertTrue(existiert("werkzeug.skalieren"))
    }

    @Test
    fun dieVorschauErscheintErstNachDemSchneiden() {
        assertFalse(
            "Die Vorschau steht schon vor dem Schneiden bereit",
            existiert("werkzeug.vorschau"),
        )

        tippe("simple.werkzeug.G-Code")
        warteAuf("slice.schliessen", 180)
        tippe("slice.schliessen")

        warteAuf("werkzeug.vorschau", 10)
        tippe("werkzeug.vorschau")

        // Der Schichtregler erscheint nur, wenn libvgcode wirklich
        // Schichten geliefert hat.
        warteAuf("vorschau.layer.oben", 60)
        assertTrue(existiert("vorschau.layer.unten"))
    }

    /**
     * Drueben zwei Faelle, je einer pro Geraeteklasse (XCTSkip auf der
     * anderen). Hier entscheidet dieselbe Regel - `isPad` aus der
     * Fensterbreite -, welche Marke stehen muss; beide Faelle in einem.
     */
    @Test
    fun tabletVerwendetSeitenkarteUndTelefonBottomSheet() {
        tippe("simple.werkzeug.G-Code")
        warteAuf("slice.schliessen", 180)
        tippe("slice.schliessen")
        tippe("werkzeug.vorschau")

        compose.waitUntil(timeoutMillis = 60_000) {
            existiert("vorschau.seitenkarte") || existiert("vorschau.bottomsheet")
        }
        val metrik = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics
        val breiteDp = metrik.widthPixels / metrik.density
        if (breiteDp >= 600) {
            assertTrue("Das Tablet zeigt keine Seitenkarte", existiert("vorschau.seitenkarte"))
        } else {
            assertTrue("Das Telefon zeigt kein Bottom-Sheet", existiert("vorschau.bottomsheet"))
        }
        assertTrue(existiert("vorschau.layer.unten"))
        assertTrue(existiert("vorschau.layer.oben"))
    }

    /**
     * Der Feature-Filter in der Legende.
     *
     * Steht hier als Gegenstueck zu der Zusicherung in
     * `PreviewUITests.testDieVorschauErscheintErstNachDemSchneiden`, die
     * auf iOS am 11.09.2026 durchfiel ("Feature-Filter reagiert nicht
     * auf Beruehrung"): ein Tipp auf die Rolle liess sie sichtbar.
     * Laeuft der Fall hier durch, liegt es nicht an der gemeinsamen
     * Regel.
     */
    @Test
    fun derFeatureFilterSchaltetEineRolleAus() {
        tippe("simple.werkzeug.G-Code")
        warteAuf("slice.schliessen", 180)
        tippe("slice.schliessen")
        tippe("werkzeug.vorschau", 10)
        warteAufPraefix("vorschau.rolle.", 60)

        val rolle = mitPraefix("vorschau.rolle.").fetchSemanticsNodes().first()
            .config.getOrNull(SemanticsProperties.TestTag)!!
        assertEquals("Visible", zustandVon(rolle))
        tippe(rolle)
        assertEquals(
            "Feature-Filter reagiert nicht auf Beruehrung",
            "Hidden",
            zustandVon(rolle),
        )
    }

    private fun zustandVon(kennung: String): String =
        compose.onNodeWithTag(kennung, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
}
