package de.psmobile.shared.net

/**
 * HTTP-Digest-Authentifizierung nach RFC 7616.
 *
 * Warum von Hand: Androids HttpURLConnection beherrscht nur Basic, und
 * Apples URLSession nimmt Digest zwar entgegen, aber nur ueber einen
 * Delegaten, dem man die Zugangsdaten synchron unterschieben muesste.
 * PrusaLink ab 0.7 verlangt Digest mit Benutzername und Passwort -
 * PrusaSlicer macht es in OctoPrint.cpp genauso (http.auth_digest).
 *
 * Steht im gemeinsamen Modul, weil eine zweite Fassung genau die Art
 * Fehler waere, die man nicht sieht: sie schlaegt nicht fehl, sie
 * authentifiziert nur anders.
 */
object DigestAuth {

    /** Die Angaben aus dem WWW-Authenticate-Kopf einer 401-Antwort. */
    class Challenge(
        val realm: String,
        val nonce: String,
        val qop: String?,
        val opaque: String?,
        val algorithm: String,
    ) {
        /**
         * Zaehler der Anfragen mit dieser nonce, wie es qop=auth
         * verlangt. Nicht nebenlaeufig geschuetzt: eine Challenge
         * gehoert zu einer Verbindung, und die wird der Reihe nach
         * benutzt.
         */
        private var zaehler = 0
        internal fun naechsteZahl(): Int = ++zaehler
    }

    /**
     * Zerlegt `WWW-Authenticate: Digest realm="...", nonce="...", ...`.
     *
     * @return null, wenn der Kopf fehlt oder kein Digest-Verfahren nennt.
     */
    fun parseChallenge(header: String?): Challenge? {
        if (header == null || !header.trimStart().startsWith("Digest", ignoreCase = true)) {
            return null
        }

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
        val qop = params["qop"]?.split(',')?.map { it.trim().lowercase() }
            ?.firstOrNull { it == "auth" }
        if (params["qop"] != null && qop == null) return null
        val algorithm = (params["algorithm"] ?: "MD5").uppercase()
        if (algorithm != "MD5") return null

        return Challenge(
            realm = realm,
            nonce = nonce,
            qop = qop,
            opaque = params["opaque"],
            algorithm = algorithm,
        )
    }

    /**
     * Baut den Wert fuer den Authorization-Kopf.
     *
     * @param uri Pfad einschliesslich Abfrage, nicht die vollstaendige
     *            URL - genau dieser Wert geht in die Pruefsumme ein.
     */
    fun authorization(
        c: Challenge,
        username: String,
        password: String,
        method: String,
        uri: String,
    ): String = authorization(c, username, password, method, uri, hex(secureRandomBytes(8)))

    /**
     * Dieselbe Rechnung mit vorgegebener cnonce. Nur fuer Tests - ohne
     * feste cnonce laesst sich das Ergebnis nicht gegen die Beispiele
     * aus dem RFC pruefen.
     */
    internal fun authorization(
        c: Challenge,
        username: String,
        password: String,
        method: String,
        uri: String,
        cnonce: String,
    ): String {
        require(c.algorithm == "MD5") { "Nicht unterstützter Digest-Algorithmus: ${c.algorithm}" }
        val nc = nc(c.naechsteZahl())

        val ha1 = Md5.hex("$username:${c.realm}:$password")
        val ha2 = Md5.hex("$method:$uri")

        val response = if (c.qop == "auth") {
            Md5.hex("$ha1:${c.nonce}:$nc:$cnonce:auth:$ha2")
        } else {
            Md5.hex("$ha1:${c.nonce}:$ha2")
        }

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

    private fun hex(bytes: ByteArray): String {
        val ziffern = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(ziffern[v shr 4]).append(ziffern[v and 0x0F])
        }
        return sb.toString()
    }

    /** Achtstellig mit fuehrenden Nullen, wie es RFC 7616 verlangt. */
    private fun nc(wert: Int): String {
        val hex = wert.toString(16)
        return "0".repeat(8 - hex.length) + hex
    }
}

/**
 * Zufall fuer die cnonce.
 *
 * Bewusst der kryptografische Zufall der Plattform und nicht
 * kotlin.random: die cnonce soll nicht vorhersagbar sein, sonst laesst
 * sich das Verfahren mit selbst gewaehlten Werten angreifen.
 */
internal expect fun secureRandomBytes(count: Int): ByteArray
