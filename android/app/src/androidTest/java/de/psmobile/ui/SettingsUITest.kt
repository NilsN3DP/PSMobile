package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SettingsUITests.swift`.
 *
 * Prueft den Einstellungsrenderer - den Bildschirm mit dem groessten
 * Hebel: 20 Seiten und 247 Parameter aus einer Vorlage. Und damit auch
 * den, bei dem ein Fehler am weitesten reicht: was hier nicht stimmt,
 * stimmt auf jeder Seite nicht.
 */
@RunWith(AndroidJUnit4::class)
class SettingsUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printSettings")
    }

    @Test
    fun seitenAusDerVorlageErscheinen() {
        // Die erste Seite von "print" heisst im Original "Layers and
        // perimeters" - sie kommt aus Tab.cpp, nicht aus einer Liste in
        // unserem Code.
        warteAuf("seite.0", 10)

        // Zehn Seiten hat der Druckbereich laut Vorlage. Weniger hiesse,
        // dass der Renderer etwas verschluckt.
        assertTrue("Es fehlen Seiten im Druckbereich", existiert("seite.9"))
    }

    @Test
    fun derReiterwechselWechseltDieSeiten() {
        warteAuf("seite.0", 10)

        // Der Druckerbereich hat andere Seiten als der Druckbereich - und
        // die Extruderseite wird zur Laufzeit vervielfacht.
        tippe("reiter.printer")
        warteAuf("seite.0", 5)

        tippe("reiter.filament")
        warteAuf("seite.0", 5)
    }

    @Test
    fun einWertLaesstSichAendernUndBleibt() {
        warteAuf("seite.0", 10)
        warteAuf("feld.layer_height", 5)

        val vorher = wertVon("feld.layer_height")
        assertTrue(
            "Das Feld ist leer - der Wert kam nicht aus dem Kern",
            vorher.isNotEmpty(),
        )

        eingabefeldIn("feld.layer_height").performTextClearance()
        eingabefeldIn("feld.layer_height").performTextInput("0.15")
        // Uebernommen wird erst mit "Fertig" - beide Plattformen halten
        // es so (`onSubmit` drueben, `KeyboardActions(onDone)` hier).
        // Drueben tippt der Test dafuer eine Zeilenschaltung mit.
        eingabefeldIn("feld.layer_height").performImeAction()
        compose.waitForIdle()

        // Zur Seite und zurueck: der Wert muss aus dem Kern kommen, nicht
        // aus dem Bildschirmzustand.
        tippe("reiter.printer")
        tippe("reiter.print")
        warteAuf("feld.layer_height", 5)

        assertEquals(
            "Der geaenderte Wert hat den Seitenwechsel nicht ueberlebt",
            "0.15",
            wertVon("feld.layer_height"),
        )
    }

    @Test
    fun schnelleinstellungenImSimpleMode() {
        // Die drei Bereiche der Referenz kamen bisher nur als
        // Ueberschriften vor. Wer die Fuelldichte aendern wollte, musste
        // in den Advanced Mode.
        schliesseApp()
        starte("-psm-preset-printer", "-psm-start-simple")
        warteAuf("simple.arbeitsbereich")

        tippe("simple.werkzeug.Settings")
        tippe("simple.karte.PRINT_SETTINGS")

        for (schluessel in listOf("schnell.layer_height", "schnell.fill_density",
                                  "schnell.fill_pattern", "schnell.perimeters")) {
            warteAuf(schluessel, 10)
        }
    }

    @Test
    fun zurueckFuehrtInDenArbeitsbereich() {
        warteAuf("seite.0", 10)
        tippe("einstellungen.zurueck")
        warteAuf("arbeitsbereich", 10)
    }

    /** Das Eingabefeld unterhalb einer Feldkennung. */
    private fun eingabefeldIn(kennung: String) =
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(kennung)),
            useUnmergedTree = true,
        )

    private fun wertVon(kennung: String): String =
        eingabefeldIn(kennung).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)
            ?.text.orEmpty()
}
