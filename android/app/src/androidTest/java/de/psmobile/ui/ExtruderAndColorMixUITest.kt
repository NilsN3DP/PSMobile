package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/ExtruderAndColorMixUITests.swift`.
 *
 * INDX/MMU hat mehr als nur einen Druckerwechsel: Materialpositionen,
 * Mischrezept und Modellzuweisung muessen denselben Kernzustand sehen.
 */
@RunWith(AndroidJUnit4::class)
class ExtruderAndColorMixUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte(
            "-psm-reset-setup",
            "-psm-preset-printer",
            "-psm-test-eight-extruders",
            "-psm-test-colormix-colors",
            "-psm-start-simple",
        )
        warteAuf("simple.arbeitsbereich")
    }

    @Test
    fun achtMaterialpositionenSindEinsbasiert() {
        tippe("simple.werkzeug.Material", 10)

        for (index in 0 until 8) {
            warteAuf("extruder.$index", 5)
        }
        assertTrue(
            "Position muss einsbasiert beschriftet sein",
            alleTexte().any { it.trim() == "1" } && alleTexte().any { it.trim() == "8" },
        )
    }

    @Test
    fun colorMixSpeichertOhneDasFilamentZuErsetzen() {
        tippe("simple.werkzeug.Material")
        val materialVorher = textVon("extruder.material.0")

        tippe("simple.colormix", 5)
        warteAuf("colormix.preview", 5)

        tippe("colormix.save")
        warteAuf("colormix.recipe.9", 5)
        tippe("colormix.done", 5)

        warteAuf("extruder.material.0", 5)
        assertEquals(
            "ColorMix darf das Filament einer physischen Position nicht ersetzen",
            materialVorher,
            textVon("extruder.material.0"),
        )
    }

    private fun textVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { knoten ->
                knoten.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
            }
            .joinToString(" ")
}

/**
 * Zwilling zu `PrinterCredentialStoreUITests` in derselben Swift-Datei.
 *
 * Prueft den Zugangsdaten-Selbsttest aus der App heraus: ein Geheimnis
 * muss den Weg in den Schluesselspeicher und zurueck finden und darf
 * nirgends im Klartext liegen. Auf Android war
 * `credentialSelfTestResult` bis zum 11.09.2026 fest `null` - der Test
 * lief durch, weil er nie lief.
 */
@RunWith(AndroidJUnit4::class)
class PrinterCredentialStoreUITest : PsmUiTest() {

    @Test
    fun appLegtZugangsdatenInDenSchluesselspeicherUndNichtInDieEinstellungen() {
        starte(
            "-psm-reset-setup", "-psm-preset-printer",
            "-psm-start-simple", "-psm-credential-self-test",
        )
        warteAuf("credential.selftest")
        assertEquals("passed", textVon("credential.selftest"))
    }

    private fun textVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { knoten ->
                knoten.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
            }
            .joinToString(" ")
}
