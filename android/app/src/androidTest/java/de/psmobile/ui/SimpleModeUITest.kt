package de.psmobile.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SimpleModeUITests.swift`.
 *
 * Prueft den Simple Mode - den Weg, den die meisten nehmen werden.
 *
 * Interessant ist hier nicht das Aussehen, sondern ob die Bedienung
 * wirklich beim Kern ankommt: eine Stuetzenwahl muss vier Parameter
 * setzen, und die Karte darueber muss danach den neuen Zustand zeigen.
 * Ein Panel, das sich oeffnet, aber nichts bewirkt, sieht auf einem
 * Bildschirmfoto genauso richtig aus wie eines, das funktioniert.
 */
@RunWith(AndroidJUnit4::class)
class SimpleModeUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-simple")
        warteAuf("simple.arbeitsbereich")
    }

    @Test
    fun werkzeugleisteOeffnetUndSchliesstEinPanel() {
        tippe("simple.werkzeug.Settings")
        assertTrue(
            "Das Einstellen-Panel hat keine Karten",
            existiert("simple.karte.SUPPORTS"),
        )

        // Ein zweites Tippen auf dasselbe Werkzeug schliesst wieder.
        tippe("simple.werkzeug.Settings")
        assertFalse(
            "Das Panel bleibt beim zweiten Tippen offen",
            existiert("simple.karte.SUPPORTS"),
        )
    }

    @Test
    fun zurueckFuehrtVonDerUnterseiteInsEinstellenPanel() {
        tippe("simple.werkzeug.Settings")
        tippe("simple.karte.ADHESION")
        warteAuf("simple.haftung.automatisch", 5)

        // backDestination() sagt: von einer Unterseite geht es zurueck
        // ins Einstellen-Panel, nicht in den Arbeitsbereich.
        tippe("simple.zurueck")
        warteAuf("simple.karte.ADHESION", 5)
    }

    @Test
    fun stuetzenwahlKommtImKernAn() {
        tippe("simple.werkzeug.Settings")
        tippe("simple.karte.SUPPORTS")
        tippe("simple.stuetzen.ORGANIC_EVERYWHERE")

        // Die Karte im Einstellen-Panel liest ihren Text aus der
        // Konfiguration. Steht dort "Organic"/"Organisch" (seit dem 16.09.2026
        // lesbar statt "organic"), ist der Wert wirklich im Kern gelandet
        // und nicht nur im Bildschirmzustand.
        tippe("simple.zurueck")
        warteAuf("simple.karte.SUPPORTS", 5)
        assertTrue(
            "Die Karte zeigt die neue Stuetzenart nicht - der Wert kam nicht im Kern an",
            zeigtText("simple.karte.SUPPORTS", "Organic") || zeigtText("simple.karte.SUPPORTS", "Organisch"),
        )
    }

    @Test
    fun appEinstellungenSindAusDemSimpleModeErreichbar() {
        tippe("simple.werkzeug.Settings")
        tippe("simple.appeinstellungen")
        warteAuf("appeinstellungen.startmodus", 10)

        tippe("appeinstellungen.zurueck")
        warteAuf("simple.arbeitsbereich", 10)
    }

    @Test
    fun wechselInDenAdvancedMode() {
        tippe("simple.werkzeug.Settings")
        tippe("simple.advanced")
        warteAuf("arbeitsbereich", 15)

        // Und zurueck: die beiden Modi muessen in beide Richtungen
        // erreichbar sein, sonst sitzt man im falschen fest.
        tippe("simple.oeffnen")
        warteAuf("simple.arbeitsbereich", 10)
    }
}
