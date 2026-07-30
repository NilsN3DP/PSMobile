package de.psmobile.slicing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliceMemoryPolicyTest {
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun comfortableJobIsAllowedWithoutWarning() {
        val decision = SliceMemoryPolicy.evaluate(
            estimatedPeakBytes = gib,
            totalDeviceBytes = 8 * gib,
            availableDeviceBytes = 5 * gib,
            currentProcessBytes = gib / 2,
            systemLowMemory = false,
        )

        assertFalse(decision.blocked)
        assertFalse(decision.warning)
    }

    @Test
    fun jobNearSafeBudgetWarns() {
        val decision = SliceMemoryPolicy.evaluate(
            estimatedPeakBytes = 3_500L * 1024L * 1024L,
            totalDeviceBytes = 8 * gib,
            availableDeviceBytes = 5 * gib,
            currentProcessBytes = gib / 2,
            systemLowMemory = false,
        )

        assertFalse(decision.blocked)
        assertTrue(decision.warning)
    }

    @Test
    fun twoGigabytePeakIsBlockedOnFourGigabyteDevice() {
        val decision = SliceMemoryPolicy.evaluate(
            estimatedPeakBytes = 2_100L * 1024L * 1024L,
            totalDeviceBytes = 4 * gib,
            availableDeviceBytes = 2 * gib,
            currentProcessBytes = 500L * 1024L * 1024L,
            systemLowMemory = false,
        )

        assertTrue(decision.blocked)
    }

    @Test
    fun androidLowMemorySignalAlwaysBlocks() {
        val decision = SliceMemoryPolicy.evaluate(
            estimatedPeakBytes = 256L * 1024L * 1024L,
            totalDeviceBytes = 8 * gib,
            availableDeviceBytes = 4 * gib,
            currentProcessBytes = 256L * 1024L * 1024L,
            systemLowMemory = true,
        )

        assertTrue(decision.blocked)
    }
}
