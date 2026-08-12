package de.psmobile.ui

import de.psmobile.shared.rules.Lang
import de.psmobile.shared.rules.SimpleModeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Es gibt genau eine Stelle, an der entschieden wird, welche Sprache
 * gilt: `SimpleModeState.text` im gemeinsamen Modul, das `Lang.current`
 * liest. `PsUi.appText` reicht nur weiter.
 *
 * Vorher gab es zwei Fassungen dieser Entscheidung, und sie waren nicht
 * einmal gleich: die eine pruefte `startsWith("de")`, die andere
 * `== "de"`. Bei einem Sprachcode wie "de_DE" haette die eine Haelfte der
 * App deutsch gesprochen und die andere englisch.
 */
class UiTextTest {

    @After
    fun zuruecksetzen() {
        Lang.current = "en"
    }

    @Test
    fun `english uses the English application copy`() {
        Lang.current = "en"
        assertEquals("Workspace", SimpleModeState.text("Workspace", "Arbeitsbereich"))
    }

    @Test
    fun `german uses the German application copy`() {
        Lang.current = "de"
        assertEquals("Arbeitsbereich", SimpleModeState.text("Workspace", "Arbeitsbereich"))
    }

    @Test
    fun `unbekannter Code faellt auf Englisch zurueck`() {
        Lang.current = "fr"
        assertEquals("Workspace", SimpleModeState.text("Workspace", "Arbeitsbereich"))
    }
}
