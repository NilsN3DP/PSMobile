package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/AdvancedWorkflowUITests.swift`.
 *
 * Die Wege im Expertenmodus: von den drei Einstiegen in die
 * Einstellungen und zurueck, und von der Objektauswahl direkt zu den
 * Zahlen am Objekt - ohne den Bearbeitenbereich erst suchen zu muessen.
 */
@RunWith(AndroidJUnit4::class)
class AdvancedWorkflowUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
    }

    @Test
    fun dieDreiEinstiegeFuehrenInDieEinstellungenUndZurueck() {
        for ((knopf, reiter) in listOf(
            "advanced.printSettings" to "reiter.print",
            "advanced.filamentSettings" to "reiter.filament",
            "advanced.printerSettings" to "reiter.printer",
        )) {
            tippe(knopf)
            warteAuf("seite.0", 15)
            // Der gewaehlte Bereich muss oben auch markiert sein.
            assertTrue("Reiter fehlt: $reiter", existiert(reiter))
            tippe("einstellungen.zurueck")
            warteAuf("arbeitsbereich", 10)
        }
    }

    @Test
    fun derObjektbaumErscheintMitDerAuswahl() {
        assertFalse(
            "Der Inspektor steht ohne Auswahl da",
            existiert("advanced.objectTree"),
        )

        erstesObjektWaehlen()

        // Der Inspektor zeigt Zahlen zum Objekt - daran erkennt man ihn,
        // seit die Griffe oben in der Werkzeugleiste stehen und dort
        // auch ohne Auswahl vorhanden (nur ausgegraut) sind.
        warteAuf("advanced.scale.prozent", 10)
    }

    @Test
    fun objektauswahlFuehrtInDenSichtbarenBearbeitenbereich() {
        erstesObjektWaehlen()

        // Die Auswahl bleibt im aufklappbaren Objektbereich erhalten.
        // Gleichzeitig muss der Tipp direkt zu den Zahlen fuehren, ohne
        // dass man den Bearbeitenbereich erst suchen und oeffnen muss.
        warteAuf("advanced.scale.prozent", 10)
        warteAuf("advanced.einpassen", 10)
        warteAuf("advanced.schichten", 10)
    }

    @Test
    fun derObjektbereichHatKeineDoppelteUeberschrift() {
        // Der Bereichstitel ist bereits der Einstieg selbst. Eine zweite
        // "Objekte (n)"-Ueberschrift darunter verdoppelt ihn nur und
        // schiebt die eigentliche Liste ohne Informationsgewinn nach unten.
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        val doppelt = alleTexte().any {
            it.uppercase().startsWith("OBJECTS (") || it.uppercase().startsWith("OBJEKTE (")
        }
        assertFalse(
            "Der Objektbereich zeigt neben seinem Bereichstitel eine zweite Ueberschrift",
            doppelt,
        )
    }

    @Test
    fun mehrereObjekteFuehrenAuchBeiKompakterHoeheZumInspector() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube", "-psm-test-many-cubes")
        warteAuf("arbeitsbereich")
        querformat()

        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        compose.waitUntil(timeoutMillis = 30_000) {
            mitPraefix("advanced.objekt.").fetchSemanticsNodes().size == 12
        }
        tippeErstesMit("advanced.objekt.")

        for (kennung in listOf("advanced.scale.prozent", "advanced.rotate.Z",
                               "advanced.einpassen", "advanced.schichten")) {
            blaettereZu("seitenleiste", kennung)
            assertTrue("$kennung bleibt bei vielen Objekten abgeschnitten", existiert(kennung))
        }
    }

    @Test
    fun eineVierteldrehungKommtAmModellAn() {
        erstesObjektWaehlen()
        blaettereZu("seitenleiste", "advanced.drehen.rechts")
        warteAuf("advanced.rotate.Z", 10)
        assertEquals("0", wertVon("advanced.rotate.Z"))

        tippe("advanced.drehen.rechts")

        // Der Wert kommt aus dem Kern zurueck, nicht aus dem Knopf: der
        // Kern rechnet in Radiant, die Anzeige in Grad.
        compose.waitUntil(timeoutMillis = 15_000) { wertVon("advanced.rotate.Z") == "90" }
    }

    @Test
    fun einpassenAendertDieGroesse() {
        erstesObjektWaehlen()
        blaettereZu("seitenleiste", "advanced.einpassen")
        warteAuf("advanced.scale.prozent", 10)
        assertEquals("100.0", wertVon("advanced.scale.prozent"))

        tippe("advanced.einpassen")

        // Ein 20-mm-Wuerfel auf einem 250er Bett wird deutlich groesser.
        compose.waitUntil(timeoutMillis = 15_000) { wertVon("advanced.scale.prozent") != "100.0" }
    }

    @Test
    fun einTeilLaesstSichAnlegenUndWiederEntfernen() {
        // Aussparung, Modifier, Stuetzenwunsch: das, wofuer man sonst
        // das Programm wechselt.
        erstesObjektWaehlen()

        blaettereZu("seitenleiste", "advanced.teilHinzufuegen")
        warteAuf("advanced.teilHinzufuegen", 10)
        // Vorher hat der Wuerfel genau einen Koerper - und der bleibt
        // ohne Entfernen-Knopf, sonst bliebe ein Objekt ohne Geometrie.
        assertFalse(
            "Der Modellkoerper darf nicht entfernbar sein",
            existiert("advanced.teil.0.entfernen"),
        )

        tippe("advanced.teilHinzufuegen")
        warteAuf("teil.hinzufuegen", 5)
        tippe("teil.art.1")          // Aussparung
        tippe("teil.form.1")         // Zylinder
        tippe("teil.hinzufuegen")

        // Der Beweis ist die Liste, nicht das geschlossene Blatt.
        blaettereZu("seitenleiste", "advanced.teil.1.entfernen")
        warteAuf("advanced.teil.1.entfernen", 10)

        tippe("advanced.teil.1.entfernen")
        compose.waitUntil(timeoutMillis = 15_000) { !existiert("advanced.teil.1.entfernen") }
    }

    private fun wertVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
            .orEmpty()

    private fun erstesObjektWaehlen() {
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
    }

    private fun assertTrue(nachricht: String, bedingung: Boolean) =
        org.junit.Assert.assertTrue(nachricht, bedingung)
}
