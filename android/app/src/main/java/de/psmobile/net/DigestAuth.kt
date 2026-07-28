package de.psmobile.net

import java.security.MessageDigest
import kotlin.random.Random

/**
 * HTTP-Digest-Authentifizierung nach RFC 7616.
 *
 * Warum von Hand: Androids HttpURLConnection beherrscht nur Basic.
 * PrusaLink ab 0.7 nutzt aber Digest mit Benutzername und Passwort -
 * PrusaSlicer macht es in OctoPrint.cpp genauso (`http.auth_digest`).
 * Basic waere hier schlicht falsch und wuerde abgelehnt.
 */
object DigestAuth {

    /** Die Angaben aus dem `WWW-Authenticate`-Kopf einer 401-Antwort. */
    data class Challenge(
        val realm: String,
        val nonce: String,
        val qop: String?,
        val opaque: String?,
        val algorithm: String,
    ) {
        /** Zaehler der Anfragen mit dieser nonce, wie es qop=auth verlangt. */
        var counter: Int = 0
    }

    /**
     * Zerlegt `WWW-Authenticate: Digest realm="...", nonce="...", ...`.
     * @return null, wenn der Kopf fehlt oder kein Digest-Verfahren nennt.
     */
    fun parseChallenge(header: String?): Challenge? {
        if (header == null || !header.trimStart().startsWith("Digest", ignoreCase = true))
            return null

        val params = mutableMapOf<String, String>()
        // Kommas innerhalb von Anfuehrungszeichen duerfen nicht trennen.
        Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]+))""")
            .findAll(header.substringAfter("Digest"))
            .forEach { m ->
                val key = m.groupValues[1].lowercase()
                params[key] = m.groupValues[2].ifEmpty { m.groupValues[3] }
            }

        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        return Challenge(
            realm = realm,
            nonce = nonce,
            qop = params["qop"]?.split(',')?.map { it.trim() }
                ?.firstOrNull { it == "auth" } ?: params["qop"],
            opaque = params["opaque"],
            algorithm = params["algorithm"] ?: "MD5",
        )
    }

    /**
     * Baut den Wert fuer den `Authorization`-Kopf.
     *
     * @param uri Pfad inklusive Abfrage, nicht die vollstaendige URL -
     *            genau dieser Wert geht in die Pruefsumme ein.
     */
    fun authorization(
        c: Challenge,
        username: String,
        password: String,
        method: String,
        uri: String,
    ): String {
        val cnonce = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
        c.counter += 1
        val nc = "%08x".format(c.counter)

        val ha1 = md5("$username:${c.realm}:$password")
        val ha2 = md5("$method:$uri")

        val response = if (c.qop == "auth")
            md5("$ha1:${c.nonce}:$nc:$cnonce:auth:$ha2")
        else
            md5("$ha1:${c.nonce}:$ha2")

        return buildString {
            append("Digest username=\"$username\"")
            append(", realm=\"${c.realm}\"")
            append(", nonce=\"${c.nonce}\"")
            append(", uri=\"$uri\"")
            append(", response=\"$response\"")
            append(", algorithm=${c.algorithm}")
            if (c.qop == "auth") {
                append(", qop=auth")
                append(", nc=$nc")
                append(", cnonce=\"$cnonce\"")
            }
            c.opaque?.let { append(", opaque=\"$it\"") }
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
