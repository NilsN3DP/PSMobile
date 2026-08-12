package de.psmobile.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.psmobile.ui.theme.PSMobileTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zeichnet die Bildschirme auf Englisch und sucht nach deutschem Text.
 *
 * Das ist die Regressionsprobe zu einem Fehler, der die App lange still
 * begleitet hat: rund fuenfhundert deutsche Zeichenketten standen fest
 * im Code, obwohl Englisch die Standardsprache ist. Aufgefallen ist das
 * erst, als jemand die App auf Englisch benutzt hat - kein Test hat je
 * einen Android-Bildschirm gezeichnet.
 *
 * Die Probe ist absichtlich grob: Umlaute und Eszett kommen in
 * englischem Text nicht vor. Sie findet damit nicht jedes deutsche Wort
 * ("Save" gegen "Speichern" entgeht ihr), aber sie findet den Grossteil
 * und kostet nichts. Zusaetzlich stehen unten die haeufigsten deutschen
 * Woerter ohne Umlaut.
 */
@RunWith(AndroidJUnit4::class)
class EnglischeOberflaecheTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context

    /** Deutsche Woerter ohne Umlaut, die in der Oberflaeche vorkamen. */
    private val deutscheWoerter = listOf(
        "Abbrechen", "Schliessen", "Speichern", "Drucker", "Einstellungen",
        "Sprache", "Hinzufuegen", "Loeschen", "Bearbeiten", "Fehler",
        "Verbunden", "Nicht verbunden", "Suchen", "Weiter", "Zurueck",
    )

    @Before
    fun englischEinstellen() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        PsUi.load(context, "en")
    }

    private fun pruefe(baum: String) {
        val umlaute = Regex("[äöüÄÖÜß]")
        val treffer = baum.lineSequence()
            .filter { it.contains("Text = ") }
            .filter { zeile ->
                umlaute.containsMatchIn(zeile) ||
                    deutscheWoerter.any { zeile.contains(it) }
            }
            .toList()
        assertTrue(
            "Deutscher Text im englischen Modus:\n" + treffer.joinToString("\n"),
            treffer.isEmpty(),
        )
    }

    @Test
    fun startseiteIstEnglisch() {
        compose.setContent {
            PSMobileTheme {
                WorkflowStartScreen(
                    onSimple = {},
                    onAdvanced = {},
                    onAdvancedWizard = {},
                    onAppSettings = {},
                    onLanguageChange = {},
                )
            }
        }
        pruefe(compose.onRoot().printToString(maxDepth = Int.MAX_VALUE))
    }

    @Test
    fun druckerverwaltungIstEnglisch() {
        compose.setContent {
            PSMobileTheme {
                PrintersScreen(
                    presetNames = listOf("Original Prusa MK4 0.4 nozzle"),
                    onClose = {},
                    onPickBackupFolder = {},
                    onReopenSetup = {},
                )
            }
        }
        pruefe(compose.onRoot().printToString(maxDepth = Int.MAX_VALUE))
    }

    @Test
    fun appEinstellungenSindEnglisch() {
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        compose.setContent {
            PSMobileTheme {
                AppSettingsScreen(
                    prefs = prefs,
                    language = "en",
                    onLanguageChange = {},
                    onToggleChanged = { _, _ -> },
                    onClose = {},
                )
            }
        }
        pruefe(compose.onRoot().printToString(maxDepth = Int.MAX_VALUE))
    }
}
