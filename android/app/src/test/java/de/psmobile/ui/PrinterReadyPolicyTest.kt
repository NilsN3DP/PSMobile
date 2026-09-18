package de.psmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterReadyPolicyTest {
    @Test fun noSelectedPrinterBlocksWorkspace() {
        assertFalse(PrinterReadyPolicy.isReady("", listOf("Prusa CORE One")))
    }

    @Test fun selectedPrinterMustBeAnInstalledPreset() {
        assertFalse(PrinterReadyPolicy.isReady("Missing", listOf("Prusa CORE One")))
        assertTrue(PrinterReadyPolicy.isReady("Prusa CORE One", listOf("Prusa CORE One")))
    }
}
