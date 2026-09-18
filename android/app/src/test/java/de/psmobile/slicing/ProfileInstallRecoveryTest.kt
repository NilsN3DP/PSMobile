package de.psmobile.slicing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInstallRecoveryTest {
    @Test
    fun failedInstallRecoversEvenWhenPersistedSelectionExists() {
        assertTrue(ProfileInstallRecovery.needsRecovery(listOf("PrusaResearch:MK4"), false, 0, 0, 0))
    }

    @Test
    fun emptyProfileCollectionsTriggerRecovery() {
        assertTrue(ProfileInstallRecovery.needsRecovery(listOf("PrusaResearch:MK4"), true, 0, 12, 40))
    }

    @Test
    fun validInstalledProfilesDoNotTriggerRecovery() {
        assertFalse(ProfileInstallRecovery.needsRecovery(listOf("PrusaResearch:MK4"), true, 1, 12, 40))
    }

    @Test
    fun firstSetupWithoutSelectionIsNotRecovery() {
        assertFalse(ProfileInstallRecovery.needsRecovery(emptyList(), true, 0, 0, 0))
    }
}
