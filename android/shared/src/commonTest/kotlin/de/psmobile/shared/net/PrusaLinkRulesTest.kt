package de.psmobile.shared.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrusaLinkRulesTest {

    @Test
    fun `lighting recognizes an explicitly manual Core One Mini with firmware 6 5 3`() {
        assertEquals(
            LightingCapability.SUPPORTED,
            PrusaLinkLighting.capability(
                LightingProbe(
                    profile = LightingPrinterProfile.MANUAL_PHYSICAL,
                    firmware = "6.5.3+1234",
                    model = "Prusa CORE-One Mini",
                ),
            ),
        )
    }

    @Test
    fun `lighting rejects non physical and unknown printer profiles`() {
        val profiles = listOf(
            LightingPrinterProfile.CLOUD,
            LightingPrinterProfile.DEMO,
            LightingPrinterProfile.SIMULATED,
            LightingPrinterProfile.UNKNOWN,
        )

        profiles.forEach { profile ->
            assertEquals(
                LightingCapability.UNSUPPORTED,
                PrusaLinkLighting.capability(
                    LightingProbe(profile, "6.5.3", "CORE One Mini"),
                ),
                profile.name,
            )
        }
    }

    @Test
    fun `lighting rejects unknown firmware models and firmware boundaries`() {
        val physical = LightingPrinterProfile.MANUAL_PHYSICAL
        assertEquals(
            LightingCapability.UNSUPPORTED,
            PrusaLinkLighting.capability(LightingProbe(physical, null, "CORE One Mini")),
        )
        assertEquals(
            LightingCapability.UNSUPPORTED,
            PrusaLinkLighting.capability(LightingProbe(physical, "6.5.2", "CORE One Mini")),
        )
        assertEquals(
            LightingCapability.UNSUPPORTED,
            PrusaLinkLighting.capability(LightingProbe(physical, "6.5.4", "CORE One Mini")),
        )
        assertEquals(
            LightingCapability.UNSUPPORTED,
            PrusaLinkLighting.capability(LightingProbe(physical, "6.5.3", "Unknown printer")),
        )
    }

    @Test
    fun `lighting command is blocked until opt in capability and successful online probe`() {
        val settings = LightingSettings(optIn = false)
        assertEquals(
            LightingCommandGate.DISABLED,
            PrusaLinkLighting.commandGate(
                settings,
                LightingCapability.SUPPORTED,
                LightingConnectionStatus.ONLINE,
            ),
        )
        assertEquals(
            LightingCommandGate.UNSUPPORTED,
            PrusaLinkLighting.commandGate(
                settings.copy(optIn = true),
                LightingCapability.UNSUPPORTED,
                LightingConnectionStatus.ONLINE,
            ),
        )
        assertEquals(
            LightingCommandGate.OFFLINE,
            PrusaLinkLighting.commandGate(
                settings.copy(optIn = true),
                LightingCapability.SUPPORTED,
                LightingConnectionStatus.OFFLINE,
            ),
        )
        assertEquals(
            LightingCommandGate.PROBE_FAILED,
            PrusaLinkLighting.commandGate(
                settings.copy(optIn = true),
                LightingCapability.SUPPORTED,
                LightingConnectionStatus.PROBE_FAILED,
            ),
        )
        assertEquals(
            LightingCommandGate.ALLOWED,
            PrusaLinkLighting.commandGate(
                settings.copy(optIn = true),
                LightingCapability.SUPPORTED,
                LightingConnectionStatus.ONLINE,
            ),
        )
    }

    @Test
    fun `lighting keeps all modes color brightness and animation in the common contract`() {
        assertEquals(
            setOf(LightingMode.AUTO, LightingMode.MANUAL, LightingMode.ANIMATION, LightingMode.OFF),
            LightingMode.entries.toSet(),
        )
        val settings = LightingSettings(
            optIn = true,
            mode = LightingMode.ANIMATION,
            brightness = 72,
            color = LightingColor(12, 34, 56),
            animation = "pulse",
        )
        assertEquals(72, settings.brightness)
        assertEquals(LightingColor(12, 34, 56), settings.color)
        assertEquals("pulse", settings.animation)
        assertEquals(LightingMode.AUTO, PrusaLinkLighting.resetToAuto(settings).mode)
    }

    @Test
    fun `lighting auto maps known printer states and leaves unknown state untouched`() {
        assertEquals(
            LightingColor(255, 64, 0),
            PrusaLinkLighting.automatic(LightingPrinterState.HEATING)?.color,
        )
        assertEquals(
            LightingColor(0, 180, 255),
            PrusaLinkLighting.automatic(LightingPrinterState.PRINTING)?.color,
        )
        assertEquals(
            LightingColor(255, 0, 0),
            PrusaLinkLighting.automatic(LightingPrinterState.ERROR)?.color,
        )
        assertNull(PrusaLinkLighting.automatic(LightingPrinterState.UNKNOWN))
    }

    @Test
    fun `ohne Schema gilt HTTPS`() {
        // Ein Drucker im eigenen Netz wird gern als nackte IP
        // eingetragen. Daraus http zu machen waere bequemer und falsch -
        // die Zugangsdaten gingen im Klartext ueber das Netz.
        assertEquals("https://192.168.1.20", PrusaLinkRules.baseUrl("192.168.1.20"))
        assertEquals("https://drucker.local", PrusaLinkRules.baseUrl("drucker.local/"))
        assertEquals("http://192.168.1.20", PrusaLinkRules.baseUrl("http://192.168.1.20"))
        assertEquals("https://a.b", PrusaLinkRules.baseUrl("  https://a.b/  "))
    }

    @Test
    fun `Klartext muss ausdruecklich freigegeben werden`() {
        assertNotNull(PrusaLinkRules.transportError("http://1.2.3.4", false))
        assertNull(PrusaLinkRules.transportError("http://1.2.3.4", true))
        assertNull(PrusaLinkRules.transportError("1.2.3.4", false))
        assertNotNull(PrusaLinkRules.transportError("", false))
    }

    @Test
    fun `unvollstaendige Anmeldedaten fallen auf`() {
        assertTrue(
            PrusaLinkRules.isComplete(
                "1.2.3.4", false, PrusaLinkRules.Auth.USER_PASSWORD, "", "maker", "geheim",
            ),
        )
        assertTrue(
            !PrusaLinkRules.isComplete(
                "1.2.3.4", false, PrusaLinkRules.Auth.USER_PASSWORD, "", "maker", "",
            ),
        )
        assertTrue(
            PrusaLinkRules.isComplete(
                "1.2.3.4", false, PrusaLinkRules.Auth.API_KEY, "abc", "", "",
            ),
        )
        assertTrue(
            !PrusaLinkRules.isComplete(
                "1.2.3.4", false, PrusaLinkRules.Auth.API_KEY, "", "", "",
            ),
        )
    }

    @Test
    fun `der Dateiname wird entschaerft`() {
        // Ein Schraegstrich im Namen waere ein anderer Pfad auf dem
        // Drucker - und der Name kommt aus einer heruntergeladenen Datei.
        assertEquals(
            "/api/v1/files/usb/mein_Teil.gcode",
            PrusaLinkRules.uploadPath("usb", "mein Teil.gcode"),
        )
        assertEquals(
            "/api/v1/files/usb/.._.._etc_passwd",
            PrusaLinkRules.uploadPath("usb", "../../etc/passwd"),
        )
        // Umlaute umschreiben statt roh in die URL (16.09.2026).
        assertEquals(
            "/api/v1/files/usb/Wuerfel_gross.bgcode",
            PrusaLinkRules.uploadPath("usb", "Würfel groß.bgcode"),
        )
    }

    @Test
    fun `jeder Antwortcode bekommt einen eigenen Satz`() {
        // "HTTP 409" sagt dem Nutzer nichts. Dass die Datei schon da ist,
        // sagt ihm, was er tun kann.
        assertNull(PrusaLinkRules.uploadError(204))
        assertNotNull(PrusaLinkRules.uploadError(409))
        assertNotNull(PrusaLinkRules.uploadError(413))
        assertTrue(PrusaLinkRules.uploadError(500)!!.contains("500"))

        assertNull(PrusaLinkRules.probeError(200, PrusaLinkRules.Auth.API_KEY))
        assertTrue(
            PrusaLinkRules.probeError(401, PrusaLinkRules.Auth.API_KEY)!! !=
                PrusaLinkRules.probeError(401, PrusaLinkRules.Auth.USER_PASSWORD)!!,
        )
    }

    @Test
    fun `aus der Statusantwort wird ein Satz`() {
        val body = """{"printer":{"state":"IDLE","temp_nozzle":24.7,"temp_bed":23.0}}"""
        val text = PrusaLinkRules.describeStatus(body)
        // Lesbar statt roh: "Idle"/"Bereit", nicht "IDLE".
        assertTrue(text.startsWith("Idle") || text.startsWith("Bereit"), text)
        assertTrue(!text.startsWith("IDLE"), text)
        assertTrue(text.contains("24"), text)
        assertTrue(PrusaLinkRules.describeStatus("""{"printer":{"state":"PRINTING"}}""").let { it.startsWith("Printing") || it.startsWith("Druckt") })
        // Unbekanntes bleibt, wie es kommt.
        assertTrue(PrusaLinkRules.describeStatus("""{"printer":{"state":"WARP"}}""").startsWith("WARP"))

        // Faellt ein Feld aus, steht da eben nur "verbunden" - und das
        // stimmt dann auch.
        assertTrue(PrusaLinkRules.describeStatus("{}").isNotBlank())
        assertTrue(PrusaLinkRules.describeStatus("kein JSON").isNotBlank())
    }

    @Test
    fun `die Kopfzeile fuers Drucken nach dem Hochladen`() {
        // PrusaLink erwartet ?1 und ?0, nicht true und false.
        assertEquals("?1", PrusaLinkRules.printAfterHeader(true))
        assertEquals("?0", PrusaLinkRules.printAfterHeader(false))
    }
}
