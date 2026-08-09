package de.psmobile.net

import org.junit.Test
import org.junit.Assert.assertEquals

class LocalPrinterMergeTest {
    @Test
    fun `existing local device keeps id and merges reachable host`() {
        val existing = PrusaLink.Printer(
            id = "device-1",
            name = "CORE One",
            host = "192.168.4.1",
            localExperimental = true,
            localHosts = listOf("192.168.4.1"),
        )
        val merged = LocalPrinterMerge.withHost(existing, "192.168.1.44")
        assertEquals("device-1", merged.id)
        assertEquals(listOf("192.168.4.1", "192.168.1.44"), merged.localHosts)
    }
}
