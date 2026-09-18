package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Das Format ist eine Verabredung zwischen zwei Geraeten, kein
 * Oberflaechendetail - deshalb wird es geprueft und nicht nur benutzt.
 */
class RemotePairingTest {

    @Test
    fun `Adresse und Token ueberleben den Umweg ueber den Code`() {
        val url = RemotePairing.url("http://192.168.1.50:8080", "abc123XYZ")
        val zurueck = RemotePairing.parse(url)
        assertEquals("http://192.168.1.50:8080", zurueck?.host)
        assertEquals("abc123XYZ", zurueck?.token)
    }

    @Test
    fun `Sonderzeichen im Token gehen nicht verloren`() {
        // Genau dafuer gibt es die Prozentkodierung: ein Token mit & oder
        // = wuerde den Code sonst mitten entzweischneiden.
        val token = "a+b&c=d/e f%g"
        val zurueck = RemotePairing.parse(RemotePairing.url("http://x", token))
        assertEquals(token, zurueck?.token)
    }

    @Test
    fun `Umlaute ueberstehen den Weg`() {
        val zurueck = RemotePairing.parse(RemotePairing.url("http://drücker.local", "grüß"))
        assertEquals("http://drücker.local", zurueck?.host)
        assertEquals("grüß", zurueck?.token)
    }

    @Test
    fun `ohne Token bleibt das Feld leer statt zu fehlen`() {
        val zurueck = RemotePairing.parse(RemotePairing.url("http://x", ""))
        assertEquals("http://x", zurueck?.host)
        assertEquals("", zurueck?.token)
    }

    @Test
    fun `fremde Codes werden abgewiesen`() {
        // Ein Scanner liest jeden Code, den man ihm hinhaelt. Eine
        // WLAN-Karte im Cafe darf nicht als Serveradresse enden.
        assertNull(RemotePairing.parse("WIFI:S=Gastnetz;T=WPA;P=geheim;;"))
        assertNull(RemotePairing.parse("https://example.com"))
        assertNull(RemotePairing.parse(""))
        assertNull(RemotePairing.parse("psmobile-remote://pair?token=nurToken"))
    }

    @Test
    fun `der Kopplungscode der lokalen PrusaLink-Kopplung ist ein anderer`() {
        // Beide sind QR-Codes und beide gehoeren zu dieser App - deshalb
        // muss der eine Leser den anderen ausdruecklich ablehnen, statt
        // ihn halb zu verstehen.
        val prusalink = """{"type":"prusalink-local","version":1,"host":"192.168.4.1"}"""
        assertNull(RemotePairing.parse(prusalink))
    }
}
