package de.psmobile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.psmobile.ui.theme.PSMobileTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Startseite muss zu allem fuehren, was man von dort erwartet.
 *
 * Anlass: die Druckerverwaltung mit der QR-Kopplung war zwar gebaut, aber
 * von der Startseite aus nicht erreichbar - wer sie suchte, landete im
 * Einrichtungsassistenten. Der Weg dorthin ist nichts, was ein reiner
 * Logiktest bemerken koennte; er ist eine Frage der Oberflaeche.
 */
@RunWith(AndroidJUnit4::class)
class StartseiteTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun englischEinstellen() {
        PsUi.load(InstrumentationRegistry.getInstrumentation().targetContext, "en")
    }

    @Test
    fun fuehrtZurDruckerverwaltung() {
        var gerufen = false
        compose.setContent {
            PSMobileTheme {
                WorkflowStartScreen(
                    onSimple = {},
                    onAdvanced = {},
                    onAdvancedWizard = {},
                    onAppSettings = {},
                    onLanguageChange = {},
                    onManagePrinters = { gerufen = true },
                )
            }
        }
        compose.onNodeWithText("Manage printers").performScrollTo().performClick()
        assertTrue("Der Knopf auf der Startseite ruft die Druckerverwaltung nicht auf", gerufen)
    }

    @Test
    fun fuehrtZuDenAppEinstellungen() {
        var gerufen = false
        compose.setContent {
            PSMobileTheme {
                WorkflowStartScreen(
                    onSimple = {},
                    onAdvanced = {},
                    onAdvancedWizard = {},
                    onAppSettings = { gerufen = true },
                    onLanguageChange = {},
                )
            }
        }
        compose.onNodeWithText("App settings").performScrollTo().performClick()
        assertTrue("Der Knopf auf der Startseite ruft die App-Einstellungen nicht auf", gerufen)
    }

    @Test
    fun fuehrtZurErsteinrichtung() {
        var gerufen = false
        compose.setContent {
            PSMobileTheme {
                WorkflowStartScreen(
                    onSimple = {},
                    onAdvanced = {},
                    onAdvancedWizard = { gerufen = true },
                    onAppSettings = {},
                    onLanguageChange = {},
                )
            }
        }
        compose.onNodeWithText("Printer setup").performScrollTo().performClick()
        assertTrue("Der Knopf auf der Startseite ruft die Ersteinrichtung nicht auf", gerufen)
    }
}
