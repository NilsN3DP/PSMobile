package de.psmobile.slicing.profileupdate

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManifestCodecTest {

    @Test
    fun `codec accepts a valid HTTPS manifest from an approved host`() {
        val result = ProfileManifestCodec.decode(
            validManifest("https://updates.example.test/profiles/2.5.5.zip"),
            URI("https://updates.example.test/manifest.json"),
            setOf("updates.example.test"),
        )

        assertEquals(ProfileVersion.parse("2.5.5"), result.getOrThrow().version)
    }

    @Test
    fun `codec rejects non HTTPS foreign host malformed checksum and invalid version`() {
        assertTrue(
            ProfileManifestCodec.decode(
                validManifest("http://updates.example.test/profiles/2.5.5.zip"),
                URI("https://updates.example.test/manifest.json"),
                setOf("updates.example.test"),
            ).isFailure,
        )
        assertTrue(
            ProfileManifestCodec.decode(
                validManifest("https://other.example.test/profiles/2.5.5.zip"),
                URI("https://updates.example.test/manifest.json"),
                setOf("updates.example.test"),
            ).isFailure,
        )
        assertTrue(
            ProfileManifestCodec.decode(
                validManifest("https://updates.example.test/profiles/2.5.5.zip", sha256 = "abc"),
                URI("https://updates.example.test/manifest.json"),
                setOf("updates.example.test"),
            ).isFailure,
        )
        assertTrue(
            ProfileManifestCodec.decode(
                validManifest("https://updates.example.test/profiles/2.5.5.zip", version = "2.5"),
                URI("https://updates.example.test/manifest.json"),
                setOf("updates.example.test"),
            ).isFailure,
        )
    }

    private fun validManifest(
        packageUrl: String,
        version: String = "2.5.5",
        sha256: String = "a".repeat(64),
    ): String = """
        {
          "version": "$version",
          "package_url": "$packageUrl",
          "sha256": "$sha256",
          "min_slic3r_version": "2.9.6",
          "release_notes": ["CORE One INDX 4T", "Filamentprofile"]
        }
    """.trimIndent()
}
