package de.psmobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {
    @Test
    fun radiansAndDegreesRoundTrip() {
        assertEquals(180f, Angles.radiansToDegrees(Math.PI.toFloat()), 0.0001f)
        assertEquals(Math.PI.toFloat(), Angles.degreesToRadians(180f), 0.0001f)
    }

    @Test
    fun normalizesNegativeAndLargeAngles() {
        assertEquals(270f, Angles.normalizeDegrees(-90f), 0.0001f)
        assertEquals(90f, Angles.normalizeDegrees(450f), 0.0001f)
    }
}
