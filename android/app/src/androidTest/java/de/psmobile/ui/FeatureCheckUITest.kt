package de.psmobile.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/FeatureCheckUITests.swift`.
 *
 * Wirft nach, was im Mehrbett-Betrieb leicht untergeht: dass ein leeres
 * zweites Bett einen Entfernen-Knopf hat und dass "Alle Betten
 * schneiden" erscheint, sobald es mehr als eines gibt.
 *
 * Ueber uiautomator statt ueber die Compose-Regel: mit zwei Betten
 * zeichnet der Viewport beide, und die Regel wartet auf eine Ruhe, die
 * dann nicht mehr eintritt ("pending recompositions"). Die App bleibt
 * dabei bedienbar - das zeigt dieser Test.
 */
@RunWith(AndroidJUnit4::class)
class FeatureCheckUITest {

    private lateinit var geraet: UiDevice

    @Before
    fun setUp() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        context.startActivity(
            android.content.Intent(context, de.psmobile.MainActivity::class.java)
                .addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                )
                .putExtra(
                    de.psmobile.Startargumente.EXTRA,
                    arrayOf("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"),
                ),
        )
        geraet = UiDevice.getInstance(instr)
        assertTrue(
            "Die Bettauswahl fehlt im Expertenmodus",
            geraet.wait(Until.hasObject(By.res("bed.selector")), 90_000),
        )
    }

    @Test
    fun entfernenUndAlleSchneidenErscheinenMitZweitemBett() {
        // Zweites, leeres Bett - daran haengt der Entfernen-Knopf.
        tippe("bed.add")
        assertTrue("Der X-Knopf fehlt am leeren zweiten Bett", sichtbar("bed.remove.1"))

        // "Alle Betten schneiden" - nur pruefen, dass der Knopf da ist,
        // nicht dass er fertig wird (Rechenzeit).
        assertTrue("Der Knopf 'Alle Betten schneiden' fehlt bei mehreren Betten", sichtbar("slicen.alle"))
    }

    @Test
    fun zwischenDenBettenLaesstSichWechseln() {
        tippe("bed.add")
        assertTrue("Die zweite Bettkarte fehlt", sichtbar("bed.card.1"))

        // Kein Bildvergleich wie drueben: dass die Kamera mitschwenkt,
        // prueft ViewportUITest bereits ueber Bilder. Hier zaehlt, dass
        // beide Karten bedienbar bleiben.
        tippe("bed.card.0")
        tippe("bed.card.1")
        assertTrue("Die erste Bettkarte ist nach dem Wechsel nicht mehr da", sichtbar("bed.card.0"))
    }

    /**
     * Da, mit Blick auf das Telefon: Betten liegen dort in einem Blatt
     * hinter der Bettkarte, und das Blatt ist modal - was ausserhalb
     * liegt, ist erst nach dem Schliessen wieder zu sehen.
     */
    private fun sichtbar(kennung: String): Boolean {
        if (geraet.wait(Until.hasObject(By.res(kennung)), 3_000)) return true
        val knopf = if (kennung.startsWith("bed.")) "bed.selector.active" else "bed.selector.close"
        geraet.findObject(By.res(knopf))?.click()
        geraet.waitForIdle(1_000)
        return geraet.wait(Until.hasObject(By.res(kennung)), 10_000)
    }

    private fun tippe(kennung: String) {
        // Telefon: Betten liegen in einem Blatt hinter der Bettkarte,
        // siehe oeffneKompakteBettauswahl in PsmUiTest.
        if (kennung.startsWith("bed.") && !geraet.hasObject(By.res(kennung))) {
            geraet.findObject(By.res("bed.selector.active"))?.click()
            geraet.waitForIdle(2_000)
        }
        val knopf = geraet.wait(Until.findObject(By.res(kennung)), 15_000)
        assertTrue("Nicht gefunden: $kennung", knopf != null)
        knopf.click()
        geraet.waitForIdle(2_000)
    }
}
