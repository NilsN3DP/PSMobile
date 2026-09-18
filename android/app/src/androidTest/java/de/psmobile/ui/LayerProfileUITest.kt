package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/LayerProfileUITests.swift`.
 *
 * Prueft die variablen Schichthoehen. Der interessante Teil ist nicht
 * das Eingeben, sondern die Grenze: ein Profil mit zwei gleichen
 * Z-Werten lehnt der Kern ab. Sieht die Oberflaeche das nicht vorher,
 * tippt jemand auf Uebernehmen und es passiert nichts - der
 * schlechteste aller Zustaende.
 */
@RunWith(AndroidJUnit4::class)
class LayerProfileUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        tippe("advanced.schichten", 15)
        warteAuf("schichten", 10)
    }

    @Test
    fun zweiStuetzstellenSindDerAnfang() {
        // Voreingestellt sind Boden und Modelloberkante - ein Profil mit
        // weniger gibt es nicht.
        assertTrue(existiert("schichten.z.0"))
        assertTrue(existiert("schichten.z.1"))
        assertFalse(existiert("schichten.z.2"))

        // Und die letzte Stuetzstelle steht auf der Modellhoehe, nicht
        // auf einer erfundenen Zahl.
        assertEquals("20.0", wertVon("schichten.z.1"))
    }

    @Test
    fun eineStuetzstelleLandetImModell() {
        tippe("schichten.neu")
        warteAuf("schichten.z.1", 5)

        // Zwischen null und der Modellhoehe - wer oben anfuegt, bekommt
        // eine Stelle, die nie erreicht wird.
        val z = wertVon("schichten.z.1").toDoubleOrNull() ?: -1.0
        assertTrue("Die Stuetzstelle liegt bei $z", z > 0 && z < 20)
    }

    @Test
    fun einUngueltigesProfilLaesstSichNichtUebernehmen() {
        compose.onNodeWithTag("schichten.uebernehmen", useUnmergedTree = true).assertIsEnabled()

        // Beide Stellen auf dieselbe Hoehe: der Kern verlangt streng
        // steigende Z-Werte.
        feld("schichten.z.1").performTextClearance()
        feld("schichten.z.1").performTextInput("0.0")
        compose.waitForIdle()

        compose.onNodeWithTag("schichten.uebernehmen", useUnmergedTree = true)
            .assertIsNotEnabled()
    }

    /**
     * Laeuft nur auf Wunsch (`-e psm.schichtprofil 1`).
     *
     * Der Fall selbst ist in Ordnung - er besteht auf dem Emulator mit
     * `-gpu swiftshader_indirect`. Mit Host-GPU (auto, angle_indirect)
     * stirbt beim Anwenden des Profils aber nicht die App, sondern der
     * Emulator-Prozess (Exit 139), und mit ihm die ganze Suite. Dasselbe
     * C++ zeichnet auf dem iOS-Simulator ohne Befund; Textur und Puffer
     * sind geprueft (1024x1024, 5 Byte je Pixel fuer zwei Stufen). Ein
     * Geraet in der Hand wuerde die Frage klaeren - bis dahin steht der
     * Befund in docs/testing.md.
     */
    @Test
    fun anwendenMarkiertDasModellUndZuruecksetzenEntferntDieMarkierung() {
        org.junit.Assume.assumeTrue(
            "Nur mit -e psm.schichtprofil 1 (swiftshader-Emulator), siehe Kommentar",
            InstrumentationRegistry.getArguments().getString("psm.schichtprofil") == "1",
        )
        tippe("schichten.neu")
        warteAuf("schichten.h.1", 5)
        feld("schichten.h.1").performTextClearance()
        feld("schichten.h.1").performTextInput("0.10")
        compose.waitForIdle()

        tippe("schichten.uebernehmen")

        // Die Legende ueber dem Viewport: sie erscheint erst, wenn der
        // GL-Viewport das Profil wirklich zeichnet (Shader und Textur
        // aufgebaut) - kein CPU-Vorabtest.
        warteAuf("viewport.schichthoehen", 10)
        val wert = compose.onAllNodes(hasTestTag("viewport.schichthoehen"), useUnmergedTree = true)
            .fetchSemanticsNodes().first().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        assertTrue(
            "Die Viewport-Markierung beschreibt die feine Schichthoehe nicht: $wert",
            wert.contains("0.10"),
        )

        blaettereZu("seitenleiste", "advanced.schichten")
        tippe("advanced.schichten", 10)
        warteAuf("schichten.zuruecksetzen", 5)
        tippe("schichten.zuruecksetzen")
        // Zuruecksetzen loescht sofort. "Uebernehmen" wuerde die danach
        // angezeigten Standardwerte bewusst wieder als neues Profil
        // speichern; zum Pruefen des Loeschpfads schliessen wir daher ab.
        tippe("schichten.abbrechen")

        compose.waitUntil(timeoutMillis = 10_000) { !existiert("viewport.schichthoehen") }
    }

    private fun feld(kennung: String) =
        compose.onNode(hasTestTag(kennung), useUnmergedTree = true)

    private fun wertVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
            .orEmpty()
}
