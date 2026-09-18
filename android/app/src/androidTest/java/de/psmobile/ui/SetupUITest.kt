package de.psmobile.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SetupUITests.swift`.
 *
 * Bedient die Ersteinrichtung wie ein Finger: ob eine Familie sich
 * aufklappt, eine Duesengroesse sich waehlen laesst und der Abschluss in
 * den Arbeitsbereich fuehrt.
 */
@RunWith(AndroidJUnit4::class)
class SetupUITest : PsmUiTest() {

    @Before
    fun setUp() {
        // Ohne Zuruecksetzen startet der zweite Durchlauf mit bereits
        // eingerichtetem Drucker - und prueft dann nichts mehr.
        starte("-psm-reset-setup", "-psm-start-advanced")
    }

    @Test
    fun durchlaufDerErsteinrichtung() {
        // Die Ersteinrichtung braucht einen Moment: sie liest
        // PrusaSlicers Vendor-Dateien.
        //
        // Oberste Ebene ist der Hersteller, darunter erst die Familien:
        // wer einen Voron sucht, soll sich nicht durch Prusa-Familien
        // lesen muessen.
        warteAuf("einrichtung.liste", 90)
        blaettereZu("einrichtung.liste", "hersteller.PrusaResearch")
        assertFalse(
            "Familien stehen schon vor dem Aufklappen des Herstellers da",
            existiert("familie.CORE"),
        )
        tippe("hersteller.PrusaResearch")

        warteAuf("familie.CORE", 10)
        // Zugeklappt darf keine Duesengroesse sichtbar sein - sonst waere
        // die Liste wieder so lang wie vorher.
        assertFalse(
            "Varianten sind schon vor dem Aufklappen sichtbar",
            existiertMitPraefix("variante."),
        )

        tippe("familie.CORE")
        warteAufPraefix("variante.", 5)

        // Ohne Auswahl bleibt der Abschluss gesperrt. Sonst richtet man
        // versehentlich nichts ein und landet in einer App ohne Profile.
        compose.onNodeWithTag("fertig", useUnmergedTree = true).assertIsNotEnabled()

        tippeErstesMit("variante.")
        compose.onNodeWithTag("fertig", useUnmergedTree = true).assertIsEnabled()

        tippe("fertig")

        // Das Einrichten laedt Profile - das dauert.
        warteAuf("arbeitsbereich", 90)
    }

    @Test
    fun sucheBlendetDieGliederungAus() {
        warteAuf("einrichtung.liste", 90)
        blaettereZu("einrichtung.liste", "hersteller.PrusaResearch")

        compose.onNodeWithTag("einrichtung.suche", useUnmergedTree = true)
            .performTextInput("MK4")
        compose.waitForIdle()

        // Wer tippt, will Treffer sehen und keine Zwischenueberschriften.
        assertFalse(
            "Waehrend der Suche steht die Gliederung noch da",
            existiert("familie.CORE"),
        )
        assertFalse(
            "Waehrend der Suche stehen fremde Hersteller noch da",
            existiert("hersteller.Voron"),
        )
        warteAufPraefix("variante.", 5)
    }
}
