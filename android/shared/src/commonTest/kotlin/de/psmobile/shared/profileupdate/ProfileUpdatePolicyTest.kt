package de.psmobile.shared.profileupdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileUpdatePolicyTest {
    @Test fun skipped_version_is_hidden_but_newer_version_is_offered() {
        assertFalse(ProfileUpdatePolicy.shouldOffer(remote = "2.5.5", current = "2.5.4", skipped = "2.5.5"))
        assertTrue(ProfileUpdatePolicy.shouldOffer(remote = "2.5.6", current = "2.5.4", skipped = "2.5.5"))
    }

    @Test fun only_strict_newer_semantic_versions_are_offered() {
        assertFalse(ProfileUpdatePolicy.shouldOffer(remote = "2.5.4", current = "2.5.4", skipped = null))
        assertFalse(ProfileUpdatePolicy.shouldOffer(remote = "2.5", current = "2.5.4", skipped = null))
        assertFalse(ProfileUpdatePolicy.shouldOffer(remote = "v2.5.5", current = "2.5.4", skipped = null))
        assertTrue(ProfileUpdatePolicy.shouldOffer(remote = "3.0.0", current = "2.5.4", skipped = null))
    }

    @Test fun decision_persists_only_skip_until_newer() {
        assertEquals(null, ProfileUpdatePolicy.skippedVersionAfter(ProfileUpdateDecision.NOW, "2.5.5"))
        assertEquals(null, ProfileUpdatePolicy.skippedVersionAfter(ProfileUpdateDecision.LATER, "2.5.5"))
        assertEquals("2.5.5", ProfileUpdatePolicy.skippedVersionAfter(ProfileUpdateDecision.SKIP_UNTIL_NEWER, "2.5.5"))
    }
}
