package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SelbsttestUITests.swift`.
 *
 * Prueft den Selbsttest - den Test, der sich selbst testet. Er ist fuer
 * das echte Geraet gebaut: der Emulator hat keine Speichergrenze, keine
 * Waermegrenze und eine andere GPU. Was hier gruen ist, sagt nur, dass
 * der Ablauf steht - aber das muss er, bevor jemand mit dem Geraet in
 * der Hand darauf tippt. Ein Selbsttest, der selbst abstuerzt, kostet
 * mehr Zeit als er spart.
 */
@RunWith(AndroidJUnit4::class)
class SelbsttestUITest : PsmUiTest() {

    @Before
    fun setUp() {
        // Der Lasttest laeuft hier mit sechs statt 25 Koerpern.
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-selbsttest-kurz")
        warteAuf("simple.arbeitsbereich")
    }

    @Test
    fun derSelbsttestLaeuftDurchUndSchreibtEinenBericht() {
        tippe("simple.werkzeug.Settings")
        tippe("simple.appeinstellungen")

        blaettereZu("appeinstellungen.liste", "appeinstellungen.selbsttest")
        tippe("appeinstellungen.selbsttest", 10)

        tippe("selbsttest.start", 5)

        // Der Lasttest schneidet mehrere Koerper - das dauert.
        warteAuf("selbsttest.ergebnis", 600)

        // Nicht nur fertig, sondern bestanden. Ein Durchlauf mit drei
        // Fehlschlaegen ist auch fertig.
        val ergebnis = textVon("selbsttest.ergebnis")
        assertFalse(
            "Der Selbsttest meldet Fehler: $ergebnis",
            ergebnis.contains("fehlgeschlagen") && !ergebnis.contains("0 fehlgeschlagen"),
        )

        // Und der Bericht muss entstanden sein - ohne ihn kann niemand
        // etwas weitergeben.
        assertTrue("Es ist kein Bericht entstanden", existiert("selbsttest.teilen"))
    }

    private fun textVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { knoten ->
                knoten.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
            }
            .joinToString(" ")
}
