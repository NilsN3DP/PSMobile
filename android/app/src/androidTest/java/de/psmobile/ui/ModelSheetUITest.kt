package de.psmobile.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/ModelSheetUITests.swift`.
 *
 * Prueft das Modelle-Blatt im Simple Mode - die einzige Stelle, an der
 * mehrere Objekte verwaltet werden: klonen, entfernen, anordnen.
 */
@RunWith(AndroidJUnit4::class)
class ModelSheetUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)
    }

    private fun zeilen(): Int = mitPraefix("blatt.zeile.").fetchSemanticsNodes().size

    @Test
    fun dasBlattLoestDenEinzelnenKnopfAb() {
        // Solange etwas auf dem Bett liegt, tritt das Blatt an die Stelle
        // von "Modell hinzufuegen".
        warteAuf("blatt.mehr", 10)
        assertFalse("Der einzelne Knopf steht noch daneben", existiert("simple.modell"))
    }

    @Test
    fun klonenUndEntfernen() {
        // Ohne Auswahl gibt es nichts zu klonen - erst alles waehlen.
        tippe("blatt.alle")
        tippe("blatt.klonen")
        warteAufModelle(2)

        // Anordnen wird erst mit zwei Objekten sinnvoll - die Regel dazu
        // steht im gemeinsamen Modul.
        compose.onNodeWithTag("blatt.anordnen", useUnmergedTree = true).assertIsEnabled()

        // Nach dem Klonen ist der Ausgangswuerfel weiterhin angehakt -
        // die Kopfzeile zeigt also die Aktionen fuer die Auswahl, nicht
        // "Alle waehlen". Erst abwaehlen, dann beide nehmen.
        tippe("blatt.abbrechen")
        tippe("blatt.alle")
        tippe("blatt.entfernen")
        warteAufModelle(0)

        // Und auf leerem Bett kommt der einzelne Knopf zurueck.
        warteAuf("simple.modell", 5)
    }

    @Test
    fun ohneAuswahlBleibenDieAktionenGesperrt() {
        // Ein einzelnes Objekt laesst sich nicht anordnen, und ohne
        // Auswahl gibt es weder Klonen noch Entfernen.
        warteAuf("blatt.alle", 10)
        compose.onNodeWithTag("blatt.anordnen", useUnmergedTree = true).assertIsNotEnabled()
        assertFalse("Klonen erscheint ohne Auswahl", existiert("blatt.klonen"))
        assertTrue("Es liegt nicht genau ein Wuerfel auf dem Bett", zeilen() == 1)
    }
}
