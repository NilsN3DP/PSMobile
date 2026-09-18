package de.psmobile.slicing.profileupdate

import java.net.URI
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUpdateRepositoryTest {
    @Test fun `newer compatible manifest becomes an offer`() = runTest {
        val repository = ProfileUpdateRepository(
            manifestUri = URI("https://updates.example.test/manifest.json"),
            allowedHosts = setOf("updates.example.test"),
            coreVersion = ProfileVersion.parse("2.9.6")!!,
            activeVersion = ProfileVersion.parse("2.5.4")!!,
            http = { validManifest().toByteArray() },
        )

        repository.checkOnLaunch(this)
        advanceUntilIdle()

        assertTrue(repository.state.value is ProfileUpdateState.Offer)
    }

    @Test fun `offline check leaves the app idle`() = runTest {
        val repository = ProfileUpdateRepository(
            manifestUri = URI("https://updates.example.test/manifest.json"),
            allowedHosts = setOf("updates.example.test"),
            coreVersion = ProfileVersion.parse("2.9.6")!!,
            activeVersion = ProfileVersion.parse("2.5.4")!!,
            http = { error("offline") },
        )

        repository.checkOnLaunch(this)
        advanceUntilIdle()

        assertEquals(ProfileUpdateState.Idle, repository.state.value)
    }

    private fun validManifest() = """{"version":"2.5.5","package_url":"https://updates.example.test/2.5.5.zip","sha256":"${"a".repeat(64)}","min_slic3r_version":"2.9.6","release_notes":["INDX"]}"""
}
