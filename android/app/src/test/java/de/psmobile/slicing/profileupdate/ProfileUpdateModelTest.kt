package de.psmobile.slicing.profileupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUpdateModelTest {

    @Test
    fun `higher patch is offered unless it was skipped`() {
        val current = ProfileVersion.parse("2.5.4")!!
        val remote = ProfileVersion.parse("2.5.5")!!

        assertTrue(remote > current)
        assertFalse(UpdateOffer.shouldOffer(remote, skipped = remote, shownThisRun = false))
    }

    @Test
    fun `later only suppresses this launch while skip waits for a newer version`() {
        val skipped = ProfileVersion.parse("2.5.5")!!

        assertFalse(UpdateOffer.shouldOffer(skipped, skipped, shownThisRun = false))
        assertTrue(UpdateOffer.shouldOffer(ProfileVersion.parse("2.5.6")!!, skipped, shownThisRun = false))
    }
}
