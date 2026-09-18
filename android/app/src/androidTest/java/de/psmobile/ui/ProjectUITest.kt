package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/ProjectUITests.swift`.
 *
 * Prueft Projekte und die Schrittfolge zum Zuruecknehmen.
 *
 * Ohne Projekte ueberlebt nichts einen App-Start: man laedt seine
 * Modelle jedes Mal neu und stellt alles noch einmal ein. Und ohne
 * Zurueck ist jeder Fehlgriff endgueltig - auf einem Touchgeraet, wo man
 * sich leicht vertippt, waere das die haerteste Einschraenkung.
 */
@RunWith(AndroidJUnit4::class)
class ProjectUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)
    }

    private fun zeilen(): Int = mitPraefix("blatt.zeile.").fetchSemanticsNodes().size

    @Test
    fun zurueckNimmtDasKlonenZurueck() {
        tippe("blatt.alle")
        tippe("blatt.klonen")
        assertTrue("Das Klonen hat nicht gewirkt", warteBis { zeilen() == 2 })

        compose.onNodeWithTag("simple.zurueckschritt", useUnmergedTree = true).assertIsEnabled()
        tippe("simple.zurueckschritt")
        assertTrue(
            "Zurueck hat das Klonen nicht rueckgaengig gemacht",
            warteBis { zeilen() == 1 },
        )

        // Und wieder vor: sonst waere ein versehentliches Zurueck genauso
        // endgueltig wie der Fehler davor.
        tippe("simple.wiederholen")
        assertTrue("Wiederholen bringt den Klon nicht zurueck", warteBis { zeilen() == 2 })
    }

    @Test
    fun projektSichernErzeugtEineDatei() {
        tippe("simple.werkzeug.Projects")
        warteAuf("projekt.sichern", 5)

        // Vorher gibt es nichts weiterzugeben - der Knopf erscheint erst
        // mit der Datei.
        assertFalse(existiert("projekt.weitergeben"))

        tippe("projekt.sichern")
        // Seit dem 15.09.2026 fragt auch der Simple Mode nach dem Namen.
        warteAuf("projekt.sichern.ok", 5)
        // Und schlaegt seit dem 16.09.2026 das erste Objekt ohne Endung vor -
        // vorher stand "psm-testwuerfel.stl" im Feld (Fund 29).
        val vorschlag = wertVon("projekt.sichern.name")
        assertTrue("Vorschlag ohne Endung erwartet, war: $vorschlag", vorschlag == "psm-testwuerfel")
        tippe("projekt.sichern.ok")
        warteAuf("projekt.weitergeben", 30)
    }

    private fun wertVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
            .orEmpty()

    private fun warteBis(sekunden: Long = 15, bedingung: () -> Boolean): Boolean = try {
        compose.waitUntil(timeoutMillis = sekunden * 1000, condition = bedingung)
        true
    } catch (fehler: Throwable) {
        false
    }
}
