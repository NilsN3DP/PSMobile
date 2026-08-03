package de.psmobile.shared.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrusaLinkRulesTest {

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
        assertTrue(text.startsWith("IDLE"), text)
        assertTrue(text.contains("24"), text)

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
