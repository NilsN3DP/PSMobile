package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/RandfaelleUITests.swift`.
 *
 * Randfaelle: was passiert, wenn jemand Unsinn eingibt, zu frueh
 * tippt oder das Letzte seiner Art entfernen will. Kein Fall hier
 * prueft ein Feature - jeder prueft, dass ein Feature einen Fehler
 * des Nutzers aushaelt, ohne abzustuerzen oder Unsinn zu speichern.
 * Angelegt am 12.09.2026 fuer die Fehlersuche vor der ersten
 * oeffentlichen Version.
 */
@RunWith(AndroidJUnit4::class)
class RandfaelleUITest : PsmUiTest() {

    // --- Einstellungsfelder ------------------------------------------------

    @Test
    fun buchstabenInEinemZahlenfeldWerdenNichtUebernommen() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printSettings")
        warteAuf("feld.layer_height", 10)
        val vorher = wertVon("feld.layer_height")
        assertTrue(vorher.isNotEmpty())

        eingabefeldIn("feld.layer_height").performTextClearance()
        eingabefeldIn("feld.layer_height").performTextInput("abc")
        eingabefeldIn("feld.layer_height").performImeAction()
        compose.waitForIdle()

        tippe("reiter.printer")
        tippe("reiter.print")
        warteAuf("feld.layer_height", 5)
        assertEquals("Buchstaben landeten als Schichthoehe im Kern", vorher, wertVon("feld.layer_height"))
    }

    @Test
    fun einLeeresZahlenfeldFaelltAufDenAltenWertZurueck() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printSettings")
        warteAuf("feld.layer_height", 10)
        val vorher = wertVon("feld.layer_height")

        eingabefeldIn("feld.layer_height").performTextClearance()
        eingabefeldIn("feld.layer_height").performImeAction()
        compose.waitForIdle()

        tippe("reiter.printer")
        tippe("reiter.print")
        warteAuf("feld.layer_height", 5)
        assertEquals("Ein leeres Feld hat den Wert geloescht", vorher, wertVon("feld.layer_height"))
    }

    @Test
    fun eineSchichthoeheVonNullWirdNichtUebernommen() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printSettings")
        warteAuf("feld.layer_height", 10)
        val vorher = wertVon("feld.layer_height")

        eingabefeldIn("feld.layer_height").performTextClearance()
        eingabefeldIn("feld.layer_height").performTextInput("0")
        eingabefeldIn("feld.layer_height").performImeAction()
        compose.waitForIdle()

        tippe("reiter.printer")
        tippe("reiter.print")
        warteAuf("feld.layer_height", 5)
        val nachher = wertVon("feld.layer_height")
        assertTrue("Schichthoehe 0 wurde angenommen: \"$nachher\"", (nachher.toDoubleOrNull() ?: 0.0) > 0.0)
    }

    // --- Objekt ---------------------------------------------------------------

    @Test
    fun skalierenAufNullLaesstDasObjektNichtVerschwinden() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.scale.prozent")
        warteAuf("advanced.scale.prozent", 10)

        compose.onNodeWithTag("advanced.scale.prozent", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("advanced.scale.prozent", useUnmergedTree = true).performTextInput("0")
        compose.onNodeWithTag("advanced.scale.prozent", useUnmergedTree = true).performImeAction()
        compose.waitForIdle()

        // Das Objekt ist noch da und hat eine Groesse.
        assertTrue("Das Objekt ist weg", existiertMitPraefix("advanced.objekt."))
        val text = compose.onNodeWithTag("advanced.scale.prozent", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertTrue("Skalierung 0 % wurde angenommen: $text", (text.toDoubleOrNull() ?: 0.0) > 0.0)
    }

    @Test
    fun rueckgaengigOhneVerlaufTutNichts() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        // Mehrfach Zurueck, auch wenn nichts (mehr) da ist - das darf
        // weder abstuerzen noch den Wuerfel wegnehmen, der beim Start
        // geladen wurde.
        repeat(5) { tippeFallsDa("advanced.zurueck", 2) }
        repeat(3) { tippeFallsDa("advanced.wiederholen", 2) }
        assertTrue("Der Startwuerfel ist nach Zurueck/Wiederholen weg", existiertMitPraefix("advanced.objekt."))
    }

    // --- Betten -------------------------------------------------------------

    @Test
    fun dasLetzteBettLaesstSichNichtEntfernen() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        warteAuf("bed.selector", 15)
        assertFalse("Das einzige Bett bietet Entfernen an", existiert("bed.remove.0"))

        tippe("bed.add")
        warteAuf("bed.card.1", 10)
        // Mit zwei Betten darf das zweite gehen ...
        assertTrue(existiert("bed.remove.1"))
        tippe("bed.remove.1")
        compose.waitUntil(timeoutMillis = 10_000) { !existiert("bed.card.1") }
        // ... und danach ist das letzte wieder unantastbar.
        assertFalse("Nach dem Entfernen bietet das letzte Bett Entfernen an", existiert("bed.remove.0"))
        assertTrue("Der Wuerfel hat den Bettwechsel nicht ueberlebt", existiert("bed.card.0"))
    }

    // --- Drucker ------------------------------------------------------------

    @Test
    fun einDruckerOhneAdresseWirdNichtGesichert() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers")
        warteAuf("arbeitsbereich")
        tippe("drucker.oeffnen")
        warteAuf("drucker", 10)
        tippe("drucker.neu")
        warteAuf("drucker.name", 5)
        compose.onNodeWithTag("drucker.name", useUnmergedTree = true).performTextInput("Ohne Adresse")
        tippe("drucker.sichern")
        compose.waitForIdle()

        // Entweder bleibt das Formular offen (mit Hinweis), oder es gibt
        // keinen Eintrag ohne Adresse - beides ist richtig. Falsch ist ein
        // Eintrag, dessen Aktionsknopf ins Leere zeigt.
        val formularOffen = existiert("drucker.name")
        val eintragDa = existiertMitPraefix("drucker.aktion.")
        assertTrue("Ein Drucker ohne Adresse wurde angelegt", formularOffen || !eintragDa)
    }

    // --- Schneiden ----------------------------------------------------------

    @Test
    fun abbrechenUndSofortWiederSchneiden() {
        // Zwoelf Wuerfel, damit der Schnitt lange genug dauert, um ihn
        // wirklich abzubrechen - ein einzelner ist schneller als der Test.
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube", "-psm-test-many-cubes")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        compose.waitUntil(timeoutMillis = 30_000) {
            mitPraefix("advanced.objekt.").fetchSemanticsNodes().size == 12
        }
        tippe("slicen")
        warteAuf("slice.abbrechen", 10)
        tippe("slice.abbrechen")
        // Abbrechen heisst: der Schneiden-Knopf kommt zurueck - kein
        // haengender Fortschritt, kein halbes Ergebnis.
        compose.waitUntil(timeoutMillis = 30_000) { !existiert("slice.abbrechen") }
        assertTrue("Nach dem Abbrechen fehlt der Schneiden-Knopf", existiert("slicen"))
        assertFalse("Nach dem Abbrechen steht ein Ergebnis da", existiert("slice.sichern"))
        // Und gleich noch einmal: der zweite Schnitt muss durchlaufen.
        tippe("slicen")
        warteAuf("slice.sichern", 300)
    }

    // --- Runde 2 (12.09.2026) ---------------------------------------------

    @Test
    fun sonderzeichenInDerEinrichtungssucheStuerzenNichtAb() {
        starte("-psm-reset-setup", "-psm-start-advanced")
        warteAuf("einrichtung.liste", 90)
        compose.onNodeWithTag("einrichtung.suche", useUnmergedTree = true)
            .performTextInput("(*+[\\\\ %")
        compose.waitForIdle()
        // Kein Treffer ist in Ordnung - ein Absturz oder eine
        // verschwundene Liste nicht.
        assertTrue("Die Einrichtung ist nach Sonderzeichen in der Suche weg", existiert("einrichtung.liste"))
        compose.onNodeWithTag("einrichtung.suche", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("einrichtung.suche", useUnmergedTree = true).performTextInput("MK4")
        warteAufPraefix("variante.", 10)
    }

    @Test
    fun sonderzeichenInDerProfilsucheStuerzenNichtAb() {
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("advanced.printSettings")
        tippe("einstellungen.profilsuche.oeffnen")
        warteAuf("profilsuche.suche", 10)
        compose.onNodeWithTag("profilsuche.suche", useUnmergedTree = true)
            .performTextInput("(*+[\\\\ %")
        compose.waitForIdle()
        assertTrue("Die Profilsuche ist nach Sonderzeichen weg", existiert("profilsuche.suche"))
        compose.onNodeWithTag("profilsuche.suche", useUnmergedTree = true).performTextClearance()
        // Gesucht wird in den Profilnamen ("0.20mm SPEED @MK4S 0.4").
        compose.onNodeWithTag("profilsuche.suche", useUnmergedTree = true).performTextInput("0.2")
        warteAufPraefix("profilsuche.eintrag.", 10)
    }

    @Test
    fun doppeltesTippenAufSchneidenStartetEinenSchnitt() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube", "-psm-test-many-cubes")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        compose.waitUntil(timeoutMillis = 30_000) {
            mitPraefix("advanced.objekt.").fetchSemanticsNodes().size == 12
        }
        tippe("slicen")
        // Sofort noch einmal an derselben Stelle - wie ein ungeduldiger
        // Daumen: oben, wo eben der Knopf war. Dort steht jetzt die
        // Fortschrittszeile (dieselbe Kennung); ein Tipp darauf darf
        // nichts tun - und vor allem nicht den Abbrechen-Knopf darunter
        // treffen.
        runCatching {
            compose.onNodeWithTag("slicen", useUnmergedTree = true)
                .performTouchInput { click(Offset(width / 2f, 8f)) }
        }
        warteAuf("slice.sichern", 300)
        // Danach steht genau ein Ergebnis, kein zweiter laufender Schnitt.
        assertFalse("Nach dem Ergebnis laeuft noch ein Schnitt", existiert("slice.abbrechen"))
    }

    @Test
    fun zehnBettenAnlegenUndWiederEntfernen() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        warteAuf("bed.selector", 15)
        repeat(9) {
            tippe("bed.add")
        }
        warteAuf("bed.card.9", 20)
        // Alle bis auf das erste wieder weg - von hinten, wie ein Mensch.
        for (i in 9 downTo 1) {
            tippe("bed.remove.$i", 10)
            compose.waitUntil(timeoutMillis = 10_000) { !existiert("bed.card.$i") }
        }
        assertTrue(existiert("bed.card.0"))
        assertFalse(existiert("bed.remove.0"))
        // Der Wuerfel von Bett 1 hat das alles ueberlebt.
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        assertTrue("Der Wuerfel ist nach dem Bettkarussell weg", existiertMitPraefix("advanced.objekt."))
    }

    @Test
    fun einBettMitObjektLaesstSichNichtEntfernen() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        warteAuf("bed.selector", 15)
        tippe("bed.add")
        warteAuf("bed.card.1", 10)
        // Bett 1 traegt den Wuerfel: Entfernen gibt es nur fuer leere
        // Betten - auf beiden Seiten dieselbe Regel (objectCount == 0).
        // Wer ein Bett mit Inhalt loswerden will, leert es erst.
        assertFalse("Ein Bett mit Objekt bietet Entfernen an", existiert("bed.remove.0"))
        assertTrue("Das leere zweite Bett bietet kein Entfernen an", existiert("bed.remove.1"))
    }

    @Test
    fun einUeberlangerDruckernameBleibtImFenster() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers")
        warteAuf("arbeitsbereich")
        tippe("drucker.oeffnen")
        warteAuf("drucker", 10)
        tippe("drucker.neu")
        warteAuf("drucker.name", 5)
        val lang = "Werkstatt ".repeat(12).trim()
        compose.onNodeWithTag("drucker.name", useUnmergedTree = true).performTextInput(lang)
        compose.onNodeWithTag("drucker.adresse", useUnmergedTree = true).performTextInput("192.168.1.70")
        tippe("drucker.sichern")
        warteAufPraefix("drucker.aktion.", 10)
        val metrik = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val aktion = mitPraefix("drucker.aktion.").fetchSemanticsNodes().first().boundsInRoot
        assertTrue(
            "Der Aktionsknopf liegt bei einem langen Namen ausserhalb: $aktion in ${metrik.widthPixels}",
            aktion.right <= metrik.widthPixels + 1f && aktion.width > 0f,
        )
    }

    // --- Runde 3 (12.09.2026) ---------------------------------------------

    @Test
    fun kopienWenigerBeiEinerKopieBleibtEine() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.kopien.weniger")
        repeat(3) { tippe("advanced.kopien.weniger") }
        val anzahl = compose.onNodeWithTag("advanced.kopien.anzahl", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }.orEmpty()
        assertEquals("Weniger als eine Kopie", "1", anzahl)
        assertTrue("Das Objekt ist weg", existiertMitPraefix("advanced.objekt."))
    }

    @Test
    fun eineDrehungMitBuchstabenBleibtBeiNull() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.rotate.Z")
        compose.onNodeWithTag("advanced.rotate.Z", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("advanced.rotate.Z", useUnmergedTree = true).performTextInput("abc")
        compose.onNodeWithTag("advanced.rotate.Z", useUnmergedTree = true).performImeAction()
        compose.waitForIdle()
        val text = compose.onNodeWithTag("advanced.rotate.Z", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertEquals("Buchstaben blieben im Drehfeld stehen", "0", text)
    }

    @Test
    fun einVerschwundenesProjektUnterZuletztStuerztNichtAb() {
        starte("-psm-preset-printer", "-psm-start-simple", "-psm-load-cube")
        warteAuf("simple.arbeitsbereich")
        warteAufModelle(1)
        tippe("simple.werkzeug.Projects")
        warteAuf("projekt.sichern", 5)
        tippe("projekt.sichern")
        warteAuf("projekt.sichern.ok", 5)
        tippe("projekt.sichern.ok")
        warteAuf("projekt.weitergeben", 30)

        // Die Datei hinter dem Ruecken der App loeschen - wie ein Nutzer
        // in der Dateien-App.
        val ordner = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "Projects",
        )
        val geloescht = (ordner.listFiles() ?: emptyArray()).filter { it.extension == "3mf" }
            .onEach { it.delete() }
        assertTrue("Kein gesichertes Projekt gefunden", geloescht.isNotEmpty())

        // Zurueck zum Start und den Eintrag antippen: die App muss das
        // aushalten - kein Absturz, und danach weiter bedienbar.
        tippeFallsDa("projekt.hinweis.schliessen", 2)
        tippe("kopf.start")
        warteAuf("start", 10)
        if (existiert("start.zuletzt.0")) {
            tippe("start.zuletzt.0")
            tippeFallsDa("projekt.oeffnen.simple", 5)
            compose.waitForIdle()
        }
        assertTrue(
            "Nach dem verschwundenen Projekt ist kein Bildschirm mehr da",
            existiert("start") || existiert("simple.arbeitsbereich") || existiert("arbeitsbereich"),
        )
    }

    @Test
    fun derArbeitsstandUeberlebtEinenNeustart() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        // Eine zweite Kopie ist eine Instanz desselben Objekts - eine
        // Zeile in der Liste, "2" im Kopienzaehler.
        blaettereZu("seitenleiste", "advanced.kopien.mehr")
        tippe("advanced.kopien.mehr")
        compose.waitUntil(timeoutMillis = 15_000) { kopienAnzahl() == "2" }

        // Neu starten, ohne einen Schalter, der einen frischen Zustand
        // verlangt: die zwei Wuerfel muessen wieder da sein - auf iOS
        // aus dem gesicherten Arbeitsstand, hier aus dem Dienst, der
        // die Aktivitaet ueberlebt. Fuer den Nutzer dasselbe.
        starte("-psm-start-advanced")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.kopien.anzahl")
        compose.waitUntil(timeoutMillis = 15_000) { kopienAnzahl() == "2" }
    }

    @Test
    fun einProjektMitZweiKopienKommtMitZweiKopienZurueck() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.kopien.mehr")
        tippe("advanced.kopien.mehr")
        compose.waitUntil(timeoutMillis = 15_000) { kopienAnzahl() == "2" }

        tippe("projekt.sichern")
        warteAuf("projekt.sichern.ok", 5)
        tippe("projekt.sichern.ok")
        compose.waitForIdle()

        // Frisch starten und das Projekt wieder oeffnen: dieselben zwei
        // Kopien auf demselben Bett - keine zweite Platte, kein
        // verlorenes Exemplar. (Die 3MF-Strecke ist dieselbe, ueber die
        // auf iOS der Arbeitsstand zurueckkommt.)
        starte("-psm-preset-printer", "-psm-start-advanced")
        warteAuf("arbeitsbereich")
        tippe("kopf.start")
        // Ein frisch gestarteter, leerer Arbeitsbereich hat nichts
        // Ungesichertes - die Rueckfrage darf hier nicht kommen.
        assertFalse("Leerer Arbeitsbereich fragt nach ungesicherten Aenderungen", existiert("verlassen.verwerfen"))
        warteAuf("start", 10)
        tippe("start.zuletzt.0")
        tippe("projekt.oeffnen.advanced", 10)
        warteAuf("arbeitsbereich", 30)
        tippeFallsDa("projekt.hinweis.schliessen", 2)
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.kopien.anzahl")
        compose.waitUntil(timeoutMillis = 15_000) { kopienAnzahl() == "2" }
        assertFalse("Aus einem Projekt mit einem Bett wurden zwei", existiert("bed.card.1"))
    }

    private fun kopienAnzahl(): String =
        compose.onAllNodesWithTag("advanced.kopien.anzahl", useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }.orEmpty()

    private fun eingabefeldIn(kennung: String) =
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(kennung)),
            useUnmergedTree = true,
        )

    private fun wertVon(kennung: String): String =
        eingabefeldIn(kennung).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)
            ?.text.orEmpty()

    // MARK: - STEP

    /**
     * Beweist, dass der Kern STEP lesen kann - nicht nur, dass die App es
     * behauptet. Bis zum 13.09.2026 stand `stepVerfuegbar = true` fest im
     * Code, waehrend der Kern seit dem 25.08. ohne OCCT gebaut war.
     * Dieselbe Probe (screw.step aus den OCCT-Beispielen) laedt der
     * iOS-Zwilling ueber das Testbundle.
     */
    @Test
    fun stepDateiLaedtWennDerKernEsKann() {
        val test = InstrumentationRegistry.getInstrumentation()
        val ziel = File(test.targetContext.cacheDir, "psm-step/screw.step")
        ziel.parentFile?.mkdirs()
        test.context.assets.open("screw.step").use { quelle ->
            ziel.outputStream().use { quelle.copyTo(it) }
        }
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-step", "-psm-step-pfad=${ziel.absolutePath}")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        assertTrue("STEP-Datei wurde nicht geladen (Kern ohne OCCT?)", existiertMitPraefix("advanced.objekt."))
    }

    // MARK: - Vorzeichen (nur Android)

    /**
     * Samsungs Tastatur hat im Dezimalfeld keine Minus-Taste (S23 FE,
     * 14.09.2026). Der ±-Knopf am Drehwinkel kippt das Vorzeichen und
     * uebernimmt es. Kein iOS-Zwilling: dort hat die Tastatur das Minus.
     */
    @Test
    fun vorzeichenKnopfMachtDenDrehwinkelNegativ() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.rotate.X")
        warteAuf("advanced.rotate.X", 10)
        compose.onNodeWithTag("advanced.rotate.X", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("advanced.rotate.X", useUnmergedTree = true).performTextInput("30")
        compose.onNodeWithTag("advanced.rotate.X", useUnmergedTree = true).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("advanced.rotate.X.vorzeichen", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val text = compose.onNodeWithTag("advanced.rotate.X", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertTrue("Drehwinkel ist nach ± nicht negativ: $text", (text.replace(',', '.').toDoubleOrNull() ?: 0.0) < 0.0)
    }

    /**
     * Ein getippter Drehwinkel muss stehen bleiben. Bis zum 14.09.2026 zeigte
     * das Feld nach "Fertig" den alten Wert und uebernahm den beim naechsten
     * Fokuswechsel erneut - aus 45 wurde wieder 0 (S23 FE).
     */
    @Test
    fun getippterDrehwinkelBleibtStehen() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube")
        warteAuf("arbeitsbereich")
        oeffneInspektorBereich("inspektor.objekte", "advanced.objekt.")
        tippeErstesMit("advanced.objekt.")
        blaettereZu("seitenleiste", "advanced.rotate.Y")
        warteAuf("advanced.rotate.Y", 10)
        compose.onNodeWithTag("advanced.rotate.Y", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("advanced.rotate.Y", useUnmergedTree = true).performTextInput("45")
        compose.onNodeWithTag("advanced.rotate.Y", useUnmergedTree = true).performImeAction()
        compose.waitForIdle()
        // Fokus woandershin - genau da ging der Wert frueher verloren.
        compose.onNodeWithTag("advanced.scale.mm", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("advanced.rotate.X", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val text = compose.onNodeWithTag("advanced.rotate.Y", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertTrue("Drehwinkel 45 ging verloren: $text", kotlin.math.abs((text.replace(',', '.').toDoubleOrNull() ?: 0.0) - 45.0) < 0.6)
    }
}
