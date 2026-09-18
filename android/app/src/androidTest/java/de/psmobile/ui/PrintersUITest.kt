package de.psmobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zwilling zu `ios/PSMobileUITests/PrintersUITests.swift`.
 *
 * Prueft den Weg zum Drucker. Was sich ohne echten Drucker pruefen
 * laesst, ist genau das, was man sonst erst im Fehlerfall merkt: dass
 * ein Passwort nicht in den normalen Einstellungen landet, und dass eine
 * Klartextadresse ohne ausdrueckliche Freigabe gar nicht erst gesendet
 * wird.
 */
@RunWith(AndroidJUnit4::class)
class PrintersUITest : PsmUiTest() {

    @Before
    fun setUp() {
        starte("-psm-preset-printer", "-psm-start-advanced", "-psm-reset-printers")
        warteAuf("arbeitsbereich")
        tippe("drucker.oeffnen")
        warteAuf("drucker", 10)
    }

    private fun lege(adresse: String, passwort: String = "geheim") {
        tippe("drucker.neu")
        warteAuf("drucker.name", 5)
        eingabe("drucker.name", "Werkstatt")
        eingabe("drucker.adresse", adresse)
        eingabe("drucker.passwort", passwort)
        tippe("drucker.sichern")
    }

    private fun eingabe(kennung: String, text: String) {
        compose.onNodeWithTag(kennung, useUnmergedTree = true).performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun einDruckerLaesstSichAnlegenUndErscheintInDerListe() {
        lege(adresse = "192.168.1.50")

        compose.waitUntil(timeoutMillis = 5_000) {
            alleTexte().any { it.contains("Werkstatt") }
        }
        // Ohne Schema gilt HTTPS - das entscheidet die gemeinsame Regel,
        // nicht die Oberflaeche.
        assertTrue(
            "Die Adresse wurde nicht als HTTPS ergaenzt",
            alleTexte().any { it.contains("https://192.168.1.50") },
        )
    }

    @Test
    fun dasPasswortUeberlebtImSchluesselbund() {
        // Drueben: der Schluesselbund; hier: der verschluesselte Speicher
        // hinter PrinterStoreModel.setSecret. Gemeinsam ist der Weg: nach
        // dem Sichern muss das Formular beim Bearbeiten das Passwort
        // wieder zeigen - leer hiesse, der Speicher hat nichts
        // zurueckgegeben.
        lege(adresse = "192.168.1.60", passwort = "streng-geheim-42")
        compose.waitUntil(timeoutMillis = 5_000) { alleTexte().any { it.contains("Werkstatt") } }

        // Erst wenn das Formular weg ist - wie drueben.
        compose.waitUntil(timeoutMillis = 10_000) { !existiert("drucker.name") }
        tippeErstesMit("drucker.bearbeiten.")

        warteAuf("drucker.passwort", 5)
        val wert = compose.onAllNodes(hasTestTag("drucker.passwort"), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
            .orEmpty()
        // Das Passwortfeld gibt seinen Inhalt nicht heraus - auch hier
        // nur Punkte, wie SecureField drueben. Genau das reicht: leer
        // hiesse, der Speicher hat nichts zurueckgegeben.
        assertEquals("Das Passwort kam nicht aus dem Speicher zurueck",
                     "•".repeat("streng-geheim-42".length), wert)
    }

    @Test
    fun klartextWirdOhneFreigabeNichtGesendet() {
        lege(adresse = "http://192.168.1.50")
        compose.waitUntil(timeoutMillis = 10_000) { !existiert("drucker.name") }

        tippeErstesMit("drucker.aktion.")

        // Es darf gar kein Versuch stattfinden: die Regel sagt vorher,
        // dass die Zugangsdaten sonst im Klartext ueber das Netz gingen.
        compose.waitUntil(timeoutMillis = 10_000) {
            alleTexte().any { it.contains("HTTP", ignoreCase = true) && !it.startsWith("http://") }
        }
    }
}
