package de.psmobile.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/SliceUITests.swift`.
 *
 * Prueft den Weg vom Modell zum G-Code - den eigentlichen Zweck der App.
 * Der Kern rechnet; ob die Oberflaeche den Fortschritt zeigt, die Zahlen
 * richtig aufbereitet und die Datei herausgibt, steht erst mit diesen
 * Faellen belegt.
 */
@RunWith(AndroidJUnit4::class)
class SliceUITest : PsmUiTest() {

    @Test
    fun ohneModellNenntDieAppDenGrund() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)

        // Das leere Bett stellt der Test selbst her, statt sich darauf zu
        // verlassen, dass der vorige Fall keines hinterlassen hat. Auf
        // iOS reicht dafuer der Startschalter - dort ist jeder Start ein
        // eigener Prozess. Hier teilen sich alle Faelle einen, und ein
        // Test, dessen Ausgangszustand vom Nachbarn abhaengt, sagt am
        // Ende nur etwas ueber die Reihenfolge aus.
        tippe("simple.werkzeug.Projects")
        tippe("projekt.neu")
        warteAufModelle(0)
        tippe("simple.werkzeug.Projects")

        tippe("simple.werkzeug.G-Code")

        // Ein ausgegrauter Knopf sagt nur, dass es nicht geht. Er sagt
        // nicht, was fehlt.
        warteAuf("slice.hinderungsgrund", 20)
        assertTrue(
            "Der genannte Grund passt nicht",
            alleTexte().any { it.contains("Nothing on the bed") },
        )

        tippe("slice.hinderungsgrund.schliessen")
        assertFalse(existiert("slice.hinderungsgrund"))
    }

    @Test
    fun mitModellEntstehtEinGCode() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)

        tippe("simple.werkzeug.G-Code")

        // Das Blatt muss sofort da sein - ein Slice dauert, und eine App,
        // die waehrenddessen unveraendert aussieht, wirkt abgestuerzt.
        warteAuf("slice.blatt", 5)

        // Ein 20-mm-Wuerfel ist in Sekunden geschnitten; die Grenze ist
        // grosszuegig, weil der Emulator langsamer rechnet als ein Geraet.
        warteAuf("slice.sichern", 180)

        // Die Zusammenfassung nennt Druckzeit und Verbrauch. Steht dort
        // ein Strich, hat der Kern keine Zahlen geliefert.
        val texte = alleTexte()
        assertTrue(
            "Keine Druckzeit in der Zusammenfassung",
            texte.any { it.endsWith("m") && it != "–" },
        )
        assertTrue(
            "Kein Materialverbrauch in der Zusammenfassung",
            texte.any { it.endsWith(" g") },
        )

        tippe("slice.schliessen")
        warteAuf("simple.arbeitsbereich", 10)
    }

    @Test
    fun dasModellLiegtAufDemBett() {
        // Ohne diesen Nachweis koennte der Wuerfel auch stillschweigend
        // nicht geladen worden sein - und der Slice-Test liefe dann gegen
        // ein leeres Bett.
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")

        // Die Objektliste liegt hinter ihrem Reiter.
        tippe("inspektor.objekte")

        assertTrue(
            "Der Testwuerfel steht nicht in der Modellliste",
            alleTexte().any { it.contains("20.0 × 20.0 × 20.0 mm") },
        )
    }
}
