package de.psmobile.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/ObjectBarUITests.swift`.
 *
 * Prueft die schwebende Leiste am ausgewaehlten Objekt: Schneiden,
 * Teilen und Klonen, ohne dafuer in den Expertenmodus wechseln zu
 * muessen.
 */
@RunWith(AndroidJUnit4::class)
class ObjectBarUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)

        // Ueber die Zeile im Modelle-Blatt auswaehlen und nicht durch
        // Tippen ins Bild: wo der Wuerfel auf dem Schirm liegt, haengt an
        // der Kamera, und ein Test soll nicht daran scheitern.
        tippeErstesMit("blatt.zeile.")
    }

    @Test
    fun dieLeisteErscheintMitDerAuswahl() {
        warteAuf("objekt.klonen", 5)

        tippe("objekt.zurueck")
        assertFalse(
            "Die Leiste bleibt nach dem Abwaehlen stehen",
            existiert("objekt.klonen"),
        )
    }

    @Test
    fun klonenAusDerLeiste() {
        tippe("objekt.klonen")
        warteAufModelle(2)
    }

    @Test
    fun schneidenTeiltDenWuerfelInZwei() {
        tippe("objekt.schneiden")

        // Die Voreinstellung liegt auf halber Hoehe - genau das, was man
        // bei einem Wuerfel will, ohne etwas einzustellen.
        tippe("objekt.schnitt.ausfuehren")
        warteAufModelle(2, sekunden = 30)
    }
}
