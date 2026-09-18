package de.psmobile.ui

import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/ResponsiveLayoutUITests.swift`.
 *
 * Rechnet nach, ob jedes Bedienelement ganz im Fenster liegt. Genau
 * dieser Test hat auf iOS am 11.09.2026 gefunden, dass "Aufs Bett
 * einpassen" elf Punkte ueber den rechten Rand ragte - die Mitte des
 * Expertenmodus setzte ihre Eigenbreite durch und schob die
 * Seitenleiste hinaus.
 */
@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutUITest : PsmUiTest() {

    @Test
    fun simpleModeBleibtBedienbar() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)

        // Die Werkzeugleiste ist der engste Fall: sechs Knoepfe in einer
        // Zeile, und rechts muss G-Code noch hineinpassen.
        pruefe("simple.werkzeug.Projects", "Projekte")
        pruefe("simple.werkzeug.Settings", "Einstellen")
        pruefe("simple.werkzeug.G-Code", "G-Code")

        // Das Modelle-Blatt darf das Bett nicht ganz verdecken und muss
        // unten im Bild bleiben.
        pruefe("blatt.mehr", "Modelle-Blatt")

        // Und das Panel: auf einem schmalen Geraet ist es das erste, was
        // aus dem Bild laeuft.
        tippe("simple.werkzeug.Settings")
        pruefe("simple.karte.SUPPORTS", "Stuetzen-Karte")
        pruefe("simple.appeinstellungen", "App-Einstellungen")
    }

    /**
     * Telefon quer (Fund 33/34 vom 16.09.2026): die Werkzeugspalte lag
     * mittig auf dem Slice-Knopf und auf "Einklappen" des Modelle-Blatts,
     * die Vorschau-Karte auf Slice/Vorschau/Wolke. Auf einem Tablet ist
     * das Fenster auch quer hoch genug - dann prueft der Test nur, dass
     * nichts uebereinanderliegt.
     */
    @Test
    fun simpleModeQuerBleibtBedienbar() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)
        val geraet = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            geraet.setOrientationLeft()
            compose.waitForIdle()
            // Ein Objekt waehlen: erst dann steht die Spalte mit Verschieben.
            tippeErstesMit("blatt.zeile.")
            warteAuf("werkzeug.verschieben", 15)
            pruefe("werkzeug.verschieben", "Verschieben")
            pruefe("werkzeug.ansicht", "Ansicht")
            pruefe("simple.werkzeug.G-Code", "Slice")
            pruefe("blatt.klappen", "Einklappen")
            getrennt("werkzeug.verschieben", "simple.werkzeug.G-Code")
            getrennt("werkzeug.verschieben", "simple.fern.umschalten")
            getrennt("werkzeug.ansicht", "blatt.klappen")
            getrennt("werkzeug.verschieben", "blatt.klappen")
        } finally {
            geraet.setOrientationNatural()
        }
    }

    @Test
    fun advancedModeBleibtBedienbar() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")

        pruefe("schiene.add", "Importieren")
        pruefe("schiene.arrange", "Anordnen")
        pruefe("advanced.printSettings", "Druckeinstellungen")
        pruefe("slicen", "Schneiden")
    }

    @Test
    fun advancedObjektleisteBleibtVorDerSeitenleiste() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")

        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")

        warteAuf("objekt.entfernen", 10)
        assertFalse(
            "Die Advanced-Objektleiste darf keine Simple-Zurueck-Aktion anbieten",
            existiert("objekt.zurueck"),
        )

        // Auf breiten Geraeten bleibt die Leiste vollstaendig links von
        // der offenen Seitenleiste. Auf schmalen liegt die Seitenleiste
        // ueber dem Bett und begrenzt den Viewport deshalb nicht.
        //
        // Gemessen wird die Leiste, nicht ihr letzter Knopf: der liegt
        // in einer waagerechten Bildlaufzeile und hat auch weggescrollt
        // einen Rahmen.
        val leiste = rahmen("objektleiste")
        if (existiert("inspektor.profile")) {
            val profil = rahmen("inspektor.profile")
            val metrik = InstrumentationRegistry.getInstrumentation()
                .targetContext.resources.displayMetrics
            if (profil.left > metrik.widthPixels / 2f) {
                assertTrue(
                    "Die Objektleiste ragt in die Seitenleiste: $leiste vor $profil",
                    leiste.right <= profil.left,
                )
            }
        }
    }

    @Test
    fun dieErsteinrichtungPasstAufsSchmaleGeraet() {
        // Der erste Bildschirm ueberhaupt - wenn der nicht passt, kommt
        // niemand weiter.
        starte("-psm-reset-setup")
        warteAuf("einrichtung.suche", 90)
        pruefe("einrichtung.suche", "Suchfeld der Ersteinrichtung")
    }

    /** Zwei Elemente duerfen sich nicht ueberlappen. */
    private fun getrennt(a: String, b: String) {
        if (!existiert(a) || !existiert(b)) return
        val ra = rahmen(a)
        val rb = rahmen(b)
        val ueberlappt = ra.left < rb.right && rb.left < ra.right &&
            ra.top < rb.bottom && rb.top < ra.bottom
        assertFalse("$a ($ra) liegt auf $b ($rb)", ueberlappt)
    }

    private fun rahmen(kennung: String) =
        compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot

    /** Ganz im Fenster und mit Groesse. */
    private fun pruefe(kennung: String, name: String) {
        warteAuf(kennung, 30)
        val rahmen = compose.onAllNodes(hasTestTag(kennung), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        val metrik = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics
        assertTrue("$name hat keine Groesse: $rahmen", rahmen.width > 0f && rahmen.height > 0f)
        assertTrue(
            "$name ragt aus dem Fenster: $rahmen in ${metrik.widthPixels}x${metrik.heightPixels}",
            rahmen.left >= 0f && rahmen.top >= 0f &&
                rahmen.right <= metrik.widthPixels.toFloat() + 1f &&
                rahmen.bottom <= metrik.heightPixels.toFloat() + 1f,
        )
    }
}
