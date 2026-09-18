package de.psmobile.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/MultiBedArrangeUITests.swift`.
 *
 * Der gemeinsame Mehrbett-Vertrag: die Bettauswahl gehoert zum
 * Expertenmodus, eine gesperrte Platte blockiert das Anordnen, und das
 * Anordnen-Panel ordnet das ausdruecklich gewaehlte Zielbett.
 */
@RunWith(AndroidJUnit4::class)
class MultiBedArrangeUITest : PsmUiTest() {

    private fun starteModus(modus: String, mitWuerfel: Boolean = true) {
        val schalter = mutableListOf(
            "-psm-preset-printer",
            if (modus == "advanced") "-psm-start-advanced" else "-psm-start-simple",
        )
        if (mitWuerfel) schalter += "-psm-load-cube"
        starte(*schalter.toTypedArray())
        warteAuf(if (modus == "advanced") "arbeitsbereich" else "simple.arbeitsbereich")
        // Der Easy Mode arbeitet bewusst auf einem Bett - die Bettauswahl
        // gehoert nur zum Expertenmodus.
        if (modus == "advanced") warteAuf("bed.selector", 15)
    }

    @Test
    fun bettauswahlFehltImSimpleMode() {
        starteModus("simple")
        assertFalse(
            "Der Easy Mode zeigt weiterhin eine Bettauswahl",
            existiert("bed.selector"),
        )
        assertFalse(
            "Der Easy Mode erlaubt weiterhin, ein Bett hinzuzufuegen",
            existiert("bed.add"),
        )
    }

    @Test
    fun lockKommtAusDemKernUndBlockiertArrange() {
        starteModus("advanced")
        tippe("bed.lock.0", 5)

        // Seit der Umstellung auf Tipp = alle anordnen / Halten = Panel
        // oeffnet nur noch ein langer Druck das Panel.
        langerDruck("schiene.arrange")
        warteAuf("arrange.panel", 5)
        tippe("arrange.run")

        warteAuf("arrange.result", 10)
        val text = textVon("arrange.result").lowercase()
        assertTrue(
            "Der Kernfehler fuer ein gesperrtes Bett wird nicht erklaert: $text",
            text.contains("gesperrt") || text.contains("locked"),
        )
    }

    @Test
    fun arrangePanelOrdnetDasExpliziteZielbett() {
        starteModus("advanced")
        tippe("bed.add")

        // Seit der Umstellung auf Tipp = alle anordnen / Halten = Panel
        // oeffnet nur noch ein langer Druck das Panel - auch auf dem
        // frisch angelegten, leeren Bett, wo der Tipp ausgegraut ist.
        langerDruck("schiene.arrange")
        warteAuf("arrange.panel", 10)

        tippe("arrange.target.0")
        tippe("arrange.run")

        warteAuf("arrange.result", 20)
        val text = textVon("arrange.result")
        assertTrue(
            "Das Ergebnis nennt das explizit angeordnete Zielbett nicht: $text",
            text.contains("1"),
        )
    }

    private fun langerDruck(kennung: String) {
        warteAuf(kennung, 10)
        // Erst ueber die Compose-Regel; wenn das Panel ausbleibt, ueber
        // echte Eingabe an den von uiautomator gemeldeten Bounds. Auf dem
        // Galaxy S23 FE kam der longClick() der Regel nach dem Bettblatt
        // nicht als langer Druck an (Emulator und Tablet: immer).
        compose.onNodeWithTag(kennung, useUnmergedTree = true)
            .performTouchInput { longClick(durationMillis = 1_200) }
        compose.waitForIdle()
        if (runCatching { compose.waitUntil(3_000) { existiert("arrange.panel") } }.isSuccess) return
        val geraet = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val r = geraet.findObject(By.res(kennung))?.visibleBounds ?: return
        geraet.swipe(r.centerX(), r.centerY(), r.centerX(), r.centerY(), 300)
        compose.waitForIdle()
    }

    private fun textVon(kennung: String): String =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { knoten ->
                knoten.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
            }
            .joinToString(" ")
}
