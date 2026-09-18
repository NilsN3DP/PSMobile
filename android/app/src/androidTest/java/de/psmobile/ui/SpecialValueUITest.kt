package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SpecialValueUITests.swift`.
 *
 * Prueft die beiden Sonderbearbeiter. Beide Werte sind im allgemeinen
 * Renderer eine Zeile Text; geprueft wird genau der Unterschied - dass
 * der eigene Bearbeiter aufgeht, den vorhandenen Wert liest und ihn
 * richtig zurueckschreibt.
 */
@RunWith(AndroidJUnit4::class)
class SpecialValueUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printerSettings")
        warteAuf("seite.0", 15)
        // Bettform und Reinigungsmengen stehen nicht in tabs.json -
        // PrusaSlicer baut sie am Desktop mit eigenen Widgets. Sie haben
        // deshalb eine eigene Seite am Ende der Druckerliste.
        tippe("seite.sonderwerte")
    }

    @Test
    fun dieBettformOeffnetSichAlsBreiteUndTiefe() {
        warteAuf("sonderwert.bed_shape", 10)
        tippe("sonderwert.bed_shape")
        warteAuf("bett.breite", 5)

        // Bewusst keine festen Masse: eine Aenderung am Profil ueberlebt
        // den App-Start - so haelt es PrusaSlicer mit veraenderten
        // Presets. Ein Test, der hier 250 erwartet, faellt um, sobald ein
        // frueherer Lauf das Bett angefasst hat, und zeigt dann auf die
        // falsche Stelle.
        val breite = wertVon("bett.breite").toDoubleOrNull()
        val tiefe = wertVon("bett.tiefe").toDoubleOrNull()
        assertTrue("Die Breite kam nicht aus dem Profil", breite != null)
        assertTrue("Die Tiefe kam nicht aus dem Profil", tiefe != null)
        assertTrue("Das Bett hat keine Groesse", (breite ?: 0.0) > 0 && (tiefe ?: 0.0) > 0)

        // Und die Flaeche wird ausgerechnet, nicht abgetippt.
        assertTrue("Die Flaeche fehlt", existiert("bett.flaeche"))
    }

    @Test
    fun eineGeaenderteBettformKommtAn() {
        tippe("sonderwert.bed_shape")
        warteAuf("bett.tiefe", 5)

        feld("bett.tiefe").performTextClearance()
        feld("bett.tiefe").performTextInput("180")
        // Kein IME-"Fertig" wie im Einstellungsrenderer: hier uebernimmt
        // der Knopf, und das Feld traegt darum keine IME-Aktion.
        tippe("bett.uebernehmen")

        // Zurueck im Bearbeiter muss der neue Wert stehen - er kommt dann
        // aus dem Kern, nicht aus dem Textfeld von vorhin.
        warteAuf("sonderwert.bed_shape", 10)
        tippe("sonderwert.bed_shape")
        warteAuf("bett.tiefe", 5)
        assertEquals(
            "Die geaenderte Bettform hat den Weg durch den Kern nicht ueberlebt",
            "180",
            wertVon("bett.tiefe"),
        )
    }

    /**
     * Das Eingabefeld zu einer Kennung.
     *
     * Mal sitzt die Kennung auf dem Feld selbst, mal auf dem Kasten
     * darum - das entscheidet der Bildschirm, und ein Test sollte daran
     * nicht scheitern.
     */
    private fun feld(kennung: String) =
        if (compose.onAllNodes(hasTestTag(kennung) and hasSetTextAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            compose.onNode(hasTestTag(kennung) and hasSetTextAction(), useUnmergedTree = true)
        } else {
            compose.onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag(kennung)),
                useUnmergedTree = true,
            )
        }

    /**
     * Der Wert eines Eingabefelds - erst am Feld selbst, sonst an einem
     * Kind. Ob die Kennung auf dem Feld sitzt oder auf dem Kasten
     * darum, entscheidet der Bildschirm.
     */
    private fun wertVon(kennung: String): String {
        val direkt = compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
        if (direkt != null) return direkt
        return feld(kennung).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    }
}
