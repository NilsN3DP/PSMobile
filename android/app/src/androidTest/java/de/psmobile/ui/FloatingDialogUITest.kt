package de.psmobile.ui

import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zum iPad-Fall in
 * `ios/PSMobileUITests/FloatingDialogUITests.swift`.
 *
 * Die Verwaltungsansichten bleiben ueber dem Arbeitsbereich stehen,
 * statt in eine Vollbild-Navigation zu wechseln. Auf einem Tablet ist
 * das der Unterschied zwischen "kurz etwas nachsehen" und "den
 * Arbeitsplatz verlassen".
 */
@RunWith(AndroidJUnit4::class)
class FloatingDialogUITest : PsmUiTest() {

    @Test
    fun appEinstellungenBleibenDialogUeberDemSimpleMode() {
        starte("-psm-preset-printer", "-psm-start-simple")
        warteAuf("simple.arbeitsbereich")

        tippe("simple.werkzeug.Settings")
        tippe("simple.appeinstellungen")

        warteAuf("dialog.appeinstellungen", 10)
        pruefeDialog("dialog.appeinstellungen")
        assertTrue(
            "Der Simple Mode darf hinter dem Dialog nicht verschwinden",
            existiert("simple.arbeitsbereich"),
        )

        // Bis zur Diagnose ganz unten muss sich blaettern lassen.
        warteAuf("appeinstellungen.selbsttest", 10)

        tippe("appeinstellungen.zurueck")
        warteAuf("simple.arbeitsbereich", 10)
    }

    /**
     * Drueben laeuft dieser Fall auf dem iPhone-Ziel. Hier gibt es nur
     * das Tablet - also wird das Fenster fuer die Dauer des Falls auf
     * Telefonmasse gestellt (`wm size`), und am Ende zurueck. Dieselbe
     * Regel (`ps.windowSize`) entscheidet dann auf beiden Seiten, dass
     * die Dialoge kompakt werden.
     */
    @Test
    fun aufKompakterBreiteBleibenBeideDialogeErreichbar() {
        val geraet = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val vorher = geraet.executeShellCommand("wm size")
        val uebersteuert = Regex("""Override size: (\d+x\d+)""").find(vorher)?.groupValues?.get(1)
        // Querformat-Telefon: 2400x1080 bei 320 dpi = 750x337 dp.
        geraet.executeShellCommand("wm size 2400x1080")
        try {
            starte("-psm-preset-printer")
            warteAuf("start")

            tippe("start.einrichtung")
            warteAuf("dialog.einrichtung", 10)
            pruefeDialog("dialog.einrichtung")
            // "Fertig" ist der feste Fuss des Dialogs, nicht Teil der
            // Liste - er muss bei geringer Hoehe im Fenster stehen.
            warteAuf("fertig", 10)
            val fertig = compose.onAllNodes(hasTestTag("fertig"), useUnmergedTree = true)
                .fetchSemanticsNodes().first().boundsInRoot
            val metrik = InstrumentationRegistry.getInstrumentation()
                .targetContext.resources.displayMetrics
            assertTrue(
                "Der Abschluss der Einrichtung bleibt bei geringer Hoehe nicht erreichbar: $fertig",
                fertig.height > 0f && fertig.bottom <= metrik.heightPixels + 1f,
            )
            warteAuf("einrichtung.schliessen", 10)
            tippe("einrichtung.schliessen")
            warteAuf("start", 10)

            tippe("start.appeinstellungen")
            warteAuf("dialog.appeinstellungen", 10)
            pruefeDialog("dialog.appeinstellungen")
            blaettereZu("appeinstellungen.liste", "appeinstellungen.selbsttest")
            assertTrue(
                "App-Einstellungen bleiben auf kompakter Breite nicht scrollend",
                existiert("appeinstellungen.selbsttest"),
            )
            tippe("appeinstellungen.zurueck")
            warteAuf("start", 10)
        } finally {
            schliesseApp()
            geraet.executeShellCommand(
                if (uebersteuert != null) "wm size $uebersteuert" else "wm size reset",
            )
        }
    }

    /**
     * Ganz im Fenster, und nicht der ganze Bildschirm.
     *
     * Drueben vergleicht der Test gegen `app.windows.firstMatch.frame`;
     * hier ist das Gegenstueck die Groesse des Anzeigebereichs.
     */
    private fun pruefeDialog(kennung: String) {
        val dialog = compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        val metrik = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics
        assertTrue(
            "Der Dialog hat keine Groesse: $dialog",
            dialog.width > 0f && dialog.height > 0f,
        )
        assertTrue(
            "Der Dialog ragt aus dem Fenster: $dialog in ${metrik.widthPixels}x${metrik.heightPixels}",
            dialog.left >= 0f && dialog.top >= 0f &&
                dialog.right <= metrik.widthPixels.toFloat() + 1f,
        )
        assertTrue(
            "Der Dialog darf nicht den ganzen Bildschirm bedecken: $dialog",
            dialog.width < metrik.widthPixels.toFloat(),
        )
    }
}
