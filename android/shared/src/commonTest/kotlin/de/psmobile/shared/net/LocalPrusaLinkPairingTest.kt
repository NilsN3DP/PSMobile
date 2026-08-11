package de.psmobile.shared.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LocalPrusaLinkPairingTest {

    private val pairedPayload = LocalPrusaLinkQrPayload(
        type = "prusalink-local", version = 1, model = "COREONE",
        host = "192.168.4.1", port = 80, transport = "http",
        pairingToken = "secret-token", capabilities = setOf("status", "files", "upload", "pause"),
        nozzle = LocalNozzle(0.4, NozzleMaterial.HARDENED),
    )

    @Test
    fun `pair response accepts the CFW's minimal username-password reply`() {
        // lib/WUI/link_content/local_pairing.cpp (pfw653-farm-mini) antwortet
        // ausschliesslich mit username+password - Modell, Duese, Host und
        // Port stammen aus dem bereits validierten QR-Payload.
        val result = LocalPairingExchange.parseResponse(
            """{"username":"maker","password":"secret"}""",
            pairedPayload,
        )
        assertTrue(result is LocalPairingExchangeResult.Success)
        val credentials = (result as LocalPairingExchangeResult.Success).credentials
        assertEquals("maker", credentials.username)
        assertEquals("COREONE", credentials.model)
        assertEquals("192.168.4.1", credentials.host)
        assertEquals(0.4, credentials.nozzleDiameter)
        assertTrue(credentials.nozzleHardened)
    }

    @Test
    fun `pair response rejects incomplete secrets`() {
        val result = LocalPairingExchange.parseResponse(
            """{"username":"maker","password":""}""",
            pairedPayload,
        )
        assertTrue(result is LocalPairingExchangeResult.Malformed)
    }

    @Test
    fun `capabilities are allowlisted by the payload`() {
        assertTrue(LocalPrusaLinkCapabilities.supports(setOf("status", "upload"), "upload"))
        assertFalse(LocalPrusaLinkCapabilities.supports(setOf("status"), "pause"))
    }
    private val valid = """
        {"type":"prusalink-local","version":1,"model":"COREONE","host":"192.168.4.1","port":80,
         "transport":"http","pairing_token":"secret-token","capabilities":["status","files","upload","pause"],
         "nozzle":{"diameter":0.4,"material":"brass"}}
    """.trimIndent()

    @Test
    fun `parses valid local payload including nozzle and capabilities`() {
        val result = LocalPrusaLinkPairing.parse(valid, nowEpochSeconds = 100)
        val accepted = assertIs<LocalPairingValidation.Valid>(result)
        assertEquals("http://192.168.4.1:80", accepted.endpoint)
        assertEquals(0.4, accepted.payload.nozzle.diameter)
        assertEquals(NozzleMaterial.BRASS, accepted.payload.nozzle.material)
        assertEquals(setOf("status", "files", "upload", "pause"), accepted.payload.capabilities)
    }

    @Test
    fun `rejects wrong type version transport and non local host`() {
        val cases = listOf(
            valid.replace("prusalink-local", "cloud"),
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"transport\":\"http\"", "\"transport\":\"https\""),
            valid.replace("192.168.4.1", "printer.example.com"),
        )
        cases.forEach { json ->
            assertIs<LocalPairingValidation.Invalid>(LocalPrusaLinkPairing.parse(json, 100))
        }
    }

    @Test
    fun `rejects missing token invalid port and expired payload`() {
        assertIs<LocalPairingValidation.Invalid>(
            LocalPrusaLinkPairing.parse(valid.replace("secret-token", ""), 100),
        )
        assertIs<LocalPairingValidation.Invalid>(
            LocalPrusaLinkPairing.parse(valid.replace("\"port\":80", "\"port\":0"), 100),
        )
        assertIs<LocalPairingValidation.Invalid>(
            LocalPrusaLinkPairing.parse(valid.replace("\"capabilities\"", "\"expires_at\":99,\"capabilities\""), 100),
        )
    }

    @Test
    fun `manual pairing accepts private host and rejects public host`() {
        assertIs<LocalPairingValidation.Valid>(
            LocalPrusaLinkPairing.manual("192.168.1.44", 80, "token", "MK4"),
        )
        assertIs<LocalPairingValidation.Invalid>(
            LocalPrusaLinkPairing.manual("8.8.8.8", 80, "token", "MK4"),
        )
    }

    @Test
    fun `known device id is reused while reachable hosts are merged`() {
        val existing = LocalPrinterIdentity("device-1", "COREONE", listOf("192.168.4.1"))
        val merged = LocalPrinterIdentity.merge(existing, "192.168.1.44")
        assertEquals("device-1", merged.id)
        assertEquals(listOf("192.168.4.1", "192.168.1.44"), merged.hosts)
        assertEquals(merged, LocalPrinterIdentity.merge(merged, "192.168.1.44"))
    }

    @Test
    fun `local pairing is disabled by default and requires explicit experimental opt in`() {
        assertEquals(false, ExperimentalFeatureFlags.localPrusaLinkPairingDefault)
        assertEquals(false, ExperimentalFeatureFlags.localPrusaLinkPairingEnabled(false))
        assertEquals(true, ExperimentalFeatureFlags.localPrusaLinkPairingEnabled(true))
    }
}
