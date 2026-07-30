package de.psmobile.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DigestAuthTest {
    @Test
    fun parsesSupportedDigestAndRejectsUnknownAlgorithm() {
        val supported = DigestAuth.parseChallenge(
            """Digest realm="PrusaLink", nonce="abc", qop="auth", algorithm=MD5"""
        )
        assertEquals("PrusaLink", supported?.realm)
        assertEquals("MD5", supported?.algorithm)

        assertNull(
            DigestAuth.parseChallenge(
                """Digest realm="PrusaLink", nonce="abc", qop="auth", algorithm=SHA-999"""
            )
        )
        assertNull(
            DigestAuth.parseChallenge(
                """Digest realm="PrusaLink", nonce="abc", qop="auth-int", algorithm=MD5"""
            )
        )
    }

    @Test
    fun nonceCountsAreUniqueUnderParallelRequests() {
        val challenge = DigestAuth.parseChallenge(
            """Digest realm="PrusaLink", nonce="abc", qop="auth", algorithm=MD5"""
        )!!
        val pool = Executors.newFixedThreadPool(8)
        try {
            val headers = pool.invokeAll(
                (1..64).map {
                    Callable {
                        DigestAuth.authorization(
                            challenge,
                            "maker",
                            "secret",
                            "GET",
                            "/api/v1/status",
                        )
                    }
                }
            ).map { it.get() }
            val counts = headers.mapNotNull { header ->
                Regex("""(?:^|, )nc=([0-9a-f]{8})""")
                    .find(header)?.groupValues?.get(1)?.toInt(16)
            }
            assertEquals(64, counts.toSet().size)
            assertEquals((1..64).toSet(), counts.toSet())
            assertTrue(headers.all { it.contains("algorithm=MD5") })
        } finally {
            pool.shutdownNow()
        }
    }
}
