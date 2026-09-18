package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/PaintUITests.swift`.
 *
 * Ein Pinselstrich ist schwer zu pruefen, weil man nicht weiss, wo auf
 * dem Schirm das Modell gerade liegt. Was sich pruefen laesst, ist der
 * Weg dorthin und die Wirkung: markiert der Strich Facetten, und
 * verschwinden sie beim Loeschen wieder. Die Zahl kommt aus dem Kern.
 */
@RunWith(AndroidJUnit4::class)
class PaintUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-test-eight-extruders",
               "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        // Die Objektliste liegt hinter ihrem Reiter, wie drueben.
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        // Und die Malwerkzeuge liegen im Reiter daneben.
        oeffneInspektorBereich("inspektor.werkzeuge", "malen.")
    }

    @Test
    fun dieWerkzeugeErscheinenMitDerAuswahl() {
        warteAuf("malen.stuetzen", 5)
        // Ohne gewaehltes Werkzeug gibt es keinen Pinsel und keinen
        // Zustand - sie waeren Zierde.
        assertFalse(existiert("malen.radius"))

        tippeWerkzeug("malen.stuetzen")
        warteAuf("malen.radius", 5)
        assertTrue("Stuetzen sperren fehlt", existiert("malen.zustand.2"))
        assertTrue(existiert("malen.modus.pinsel"))
        assertTrue(existiert("malen.modus.smart"))
        assertFalse("Support darf keinen erfundenen Bucket Fill anbieten",
                    existiert("malen.modus.eimer"))
        assertTrue(existiert("malen.form.kreis"))
        assertTrue(existiert("malen.form.kugel"))
        val scope = textVon("malen.scope")
        assertTrue(
            "Der volumebasierte Scope muss in der aktiven Sprache klar benannt sein: $scope",
            scope.contains("alle Kopien") || scope.contains("all copies"),
        )

        tippeWerkzeug("malen.naht")
        assertTrue(existiert("malen.modus.pinsel"))
        assertFalse("Naht folgt Prusa und hat keinen Smart Fill", existiert("malen.modus.smart"))
        assertFalse(existiert("malen.modus.eimer"))

        tippeWerkzeug("malen.mmu")
        assertTrue(existiert("malen.modus.smart"))
        assertTrue(existiert("malen.modus.eimer"))

        tippeWerkzeug("malen.aus")
        assertFalse("Das Werkzeug laesst sich nicht abstellen", existiert("malen.radius"))
    }

    @Test
    fun smartBucketCursorUndLoeschenSindSichtbarUndWirksam() {
        tippeWerkzeug("malen.stuetzen")
        tippeWerkzeug("malen.modus.smart")
        zeige("malen.winkel")
        tippeViewport()
        warteAuf("viewport.malmarkierung", 5)
        warteBis("Smart Fill markiert nichts") { !textVon("malen.anzahl").contains(" 0") }
        tippeWerkzeug("malen.loeschen")
        warteBis("Clear entfernt Smart Fill nicht") { textVon("malen.anzahl").contains("0") }

        tippeWerkzeug("malen.mmu")
        tippeWerkzeug("malen.modus.eimer")
        tippeViewport()
        warteBis("Bucket Fill markiert nichts") { !textVon("malen.anzahl").contains(" 0") }
    }

    @Test
    fun einStrichMarkiertUndLoeschenRaeumtAuf() {
        tippeWerkzeug("malen.stuetzen")
        warteAuf("malen.anzahl", 5)
        val vorher = textVon("malen.anzahl")
        assertTrue("Am Anfang darf nichts markiert sein: $vorher", vorher.contains("0"))

        // In die Mitte der Flaeche tippen - dort liegt der Wuerfel.
        tippeViewport()

        warteBis("Der Strich hat nichts markiert: ${textVon("malen.anzahl")}") {
            val anzahl = textVon("malen.anzahl")
            !anzahl.contains(" 0") && anzahl != vorher
        }

        tippeWerkzeug("malen.loeschen")
        warteBis("Loeschen raeumt nicht auf: ${textVon("malen.anzahl")}") {
            textVon("malen.anzahl").contains("0")
        }
    }

    /**
     * Die Malwerkzeuge liegen in der blaetternden Seitenleiste. Ein
     * Knoten ausserhalb des Bildes bekommt von performClick keinen
     * Tipp, der ankommt - also erst hinblaettern.
     */
    private fun tippeWerkzeug(kennung: String) {
        seiteAufAufDemTelefon()
        warteAuf(kennung, 10)
        blaettereZu("seitenleiste", kennung)
        tippe(kennung)
    }

    /** Hinblaettern und warten - der Wert liegt auf dem Telefon unter dem Rand. */
    private fun zeige(kennung: String) {
        runCatching { blaettereZu("seitenleiste", kennung) }
        warteAuf(kennung, 5)
    }

    /**
     * Auf dem Telefon liegt die Seite ueber dem Bett - erst zu, dann
     * tippen, wie ein Finger auch. Das Werkzeug bleibt dabei an.
     */
    private fun tippeViewport() {
        seiteZuAufDemTelefon()
        compose.onNodeWithTag("viewport", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun textVon(kennung: String): String {
        seiteAufAufDemTelefon()
        return compose.onNodeWithTag(kennung, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text } ?: ""
    }

    private fun warteBis(nachricht: String, bedingung: () -> Boolean) {
        val fehler = runCatching {
            compose.waitUntil(timeoutMillis = 15_000) { bedingung() }
        }.exceptionOrNull()
        if (fehler != null) throw AssertionError(nachricht, fehler)
    }
}
