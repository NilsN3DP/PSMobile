package de.psmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedLockTest {
    @Test fun lockToggleIsPerBedAndBlocksMutation() {
        val locked = BedLockPolicy.toggle(emptySet(), 2)
        assertTrue(2 in locked)
        assertFalse(BedLockPolicy.allows(locked, 2))
        assertTrue(BedLockPolicy.allows(locked, 1))
        assertTrue(BedLockPolicy.toggle(locked, 2).isEmpty())
    }
}
