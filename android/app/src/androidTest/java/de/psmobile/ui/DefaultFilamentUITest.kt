package de.psmobile.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/DefaultFilamentUITests.swift`.
 *
 * Wirft nach: welches Filament nach der **echten** Ersteinrichtung im
 * Feld steht - nicht nach dem Testschalter `-psm-preset-printer`, der
 * `completeSetup` und damit `standardwerteSetzen` ueberspringt.
 *
 * Die Regel steht im gemeinsamen Modul (`Defaults.preferredFilament`):
 * Prusament PLA statt des alphabetisch ersten Filaments. Bis zum
 * 20.08.2026 wandte nur iOS sie an; auf Android stand nach der
 * Einrichtung, was der Kern gewaehlt hatte.
 */
@RunWith(AndroidJUnit4::class)
class DefaultFilamentUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-reset-setup", "-psm-start-advanced")
        warteAuf("einrichtung.liste", 90)
    }

    @Test
    fun standardfilamentNachEchterEinrichtung() {
        compose.onNodeWithTag("einrichtung.suche", useUnmergedTree = true)
            .performTextInput("MK4S")
        compose.waitForIdle()

        tippeErstesMit("variante.", 10)
        tippe("fertig")

        // Das Einrichten laedt Profile - das dauert.
        warteAuf("arbeitsbereich", 120)

        assertTrue(
            "Nach der Einrichtung steht nicht Prusament PLA im Feld: " + alleTexte(),
            alleTexte().any { it.contains("Prusament PLA") },
        )
    }
}
