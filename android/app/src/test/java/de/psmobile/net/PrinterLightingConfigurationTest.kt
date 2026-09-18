package de.psmobile.net

import de.psmobile.shared.net.LightingPrinterProfile
import de.psmobile.shared.net.LightingCapability
import de.psmobile.shared.net.LightingCommandGate
import de.psmobile.shared.net.LightingConnectionStatus
import de.psmobile.shared.net.LightingEndpointResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrinterLightingConfigurationTest {

    @Test
    fun `a newly manually configured printer keeps lighting opt in disabled`() {
        val printer = PrusaLink.Printer(id = "printer-1", name = "Local", host = "printer.local")

        assertFalse(printer.lightingOptIn)
        assertEquals(LightingPrinterProfile.MANUAL_PHYSICAL, printer.lightingProfile)
    }

    @Test
    fun `client blocks the lighting adapter before any endpoint dispatch when opt in is disabled`() {
        val printer = PrusaLink.Printer(id = "printer-1", name = "Local", host = "printer.local")

        assertEquals(
            LightingEndpointResult.BlockedBySafetyGate(LightingCommandGate.DISABLED),
            PrusaLink.lightingEndpointResult(
                printer,
                LightingCapability.SUPPORTED,
                LightingConnectionStatus.ONLINE,
            ),
        )
    }
}
