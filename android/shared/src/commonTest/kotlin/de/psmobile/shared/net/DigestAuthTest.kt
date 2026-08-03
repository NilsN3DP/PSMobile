package de.psmobile.shared.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Md5Test {

    @Test
    fun `die Testvektoren aus RFC 1321`() {
        // Ohne sie waere die eigene Fassung eine Behauptung. Genau hier
        // faellt ein Vorzeichenfehler in der Bitschieberei auf, und sonst
        // nirgends - eine falsche Pruefsumme sieht aus wie eine richtige.
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.hex(""))
        assertEquals("0cc175b9c0f1b6a831c399e269772661", Md5.hex("a"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.hex("abc"))
        assertEquals("f96b697d7cb7938d525a2f31aaf161d0", Md5.hex("message digest"))
        assertEquals(
            "c3fcd3d76192e4007dfb496cca67e13b",
            Md5.hex("abcdefghijklmnopqrstuvwxyz"),
        )
        assertEquals(
            "d174ab98d277d9f5a5611c2c9f419d9f",
            Md5.hex(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
            ),
        )
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            Md5.hex("1234567890".repeat(8)),
        )
    }

    @Test
    fun `die Blockgrenze wird richtig aufgefuellt`() {
        // 55, 56 und 64 Byte sind die Faelle, an denen die Auffuellung
        // umspringt: bei 56 passt die Laengenangabe nicht mehr in denselben
        // Block. Wer das falsch macht, merkt es bei kurzen Eingaben nie.
        // Sollwerte aus einer unabhaengigen Fassung (Pythons hashlib),
        // nicht aus dieser hier - sonst prueft der Test sich selbst.
        assertEquals("ef1772b6dff9a122358552954ad0df65", Md5.hex("a".repeat(55)))
        assertEquals("3b0c8ac703f828b04c6c197006d17218", Md5.hex("a".repeat(56)))
        assertEquals("b06521f39153d618550606be297466d5", Md5.hex("a".repeat(63)))
        assertEquals("014842d480b571495a4a0363793f7367", Md5.hex("a".repeat(64)))
        assertEquals("c743a45e0d2e6a95cb859adae0248435", Md5.hex("a".repeat(65)))
        assertEquals("cabe45dcc9ae5b66ba86600cca6b8ba8", Md5.hex("a".repeat(1000)))
    }
}

class DigestAuthTest {

    private val header =
        """Digest realm="Printer API", qop="auth", nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", """ +
            """opaque="5ccc069c403ebaf9f0171e9517f40e41""""

    @Test
    fun `der Kopf wird zerlegt`() {
        val c = assertNotNull(DigestAuth.parseChallenge(header))
        assertEquals("Printer API", c.realm)
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", c.nonce)
        assertEquals("auth", c.qop)
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", c.opaque)
        assertEquals("MD5", c.algorithm)
    }

    @Test
    fun `Basic und fehlende Angaben werden abgelehnt`() {
        // Lieber nichts als eine halbe Challenge: mit fehlender nonce
        // liesse sich zwar ein Kopf bauen, der Server wuerde ihn aber
        // ablehnen - und der Fehler stuende dann an der falschen Stelle.
        assertNull(DigestAuth.parseChallenge(null))
        assertNull(DigestAuth.parseChallenge("""Basic realm="x""""))
        assertNull(DigestAuth.parseChallenge("""Digest realm="x""""))
        assertNull(DigestAuth.parseChallenge("""Digest realm="x", nonce="y", qop="auth-int""""))
        assertNull(
            DigestAuth.parseChallenge("""Digest realm="x", nonce="y", algorithm=SHA-256"""),
        )
    }

    @Test
    fun `das Beispiel aus RFC 2617 kommt richtig heraus`() {
        // Die Rechnung selbst - mit den Werten des RFC und fester cnonce,
        // sonst waere das Ergebnis jedes Mal ein anderes.
        val c = assertNotNull(
            DigestAuth.parseChallenge(
                """Digest realm="testrealm@host.com", qop="auth,auth-int", """ +
                    """nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", """ +
                    """opaque="5ccc069c403ebaf9f0171e9517f40e41"""",
            ),
        )
        val kopf = DigestAuth.authorization(
            c, "Mufasa", "Circle Of Life", "GET", "/dir/index.html", "0a4f113b",
        )
        assertTrue(
            kopf.contains("""response="6629fae49393a05397450978507c4ef1""""),
            kopf,
        )
        assertTrue(kopf.contains("nc=00000001"), kopf)
        assertTrue(kopf.contains("""cnonce="0a4f113b""""), kopf)
    }

    @Test
    fun `der Zaehler laeuft mit jeder Anfrage weiter`() {
        // Ein stehender Zaehler waere fuer den Server ein
        // Wiedereinspielversuch - er darf dieselbe Kombination aus nonce
        // und nc nur einmal sehen.
        val c = assertNotNull(DigestAuth.parseChallenge(header))
        assertTrue(DigestAuth.authorization(c, "u", "p", "GET", "/", "aa").contains("nc=00000001"))
        assertTrue(DigestAuth.authorization(c, "u", "p", "GET", "/", "aa").contains("nc=00000002"))
    }

    @Test
    fun `ohne qop faellt der Zusatz weg`() {
        val c = assertNotNull(
            DigestAuth.parseChallenge("""Digest realm="r", nonce="n""""),
        )
        val kopf = DigestAuth.authorization(c, "u", "p", "PUT", "/api/files", "aa")
        assertTrue(!kopf.contains("qop="), kopf)
        assertTrue(!kopf.contains("cnonce="), kopf)
    }

    @Test
    fun `die cnonce ist nicht jedes Mal dieselbe`() {
        val c = assertNotNull(DigestAuth.parseChallenge(header))
        val a = DigestAuth.authorization(c, "u", "p", "GET", "/")
        val b = DigestAuth.authorization(c, "u", "p", "GET", "/")
        assertTrue(a != b, "Zwei Anfragen mit derselben cnonce")
    }
}
