package de.psmobile.shared.rules

/**
 * QR-Kopplung fuer Remote Slicing.
 *
 * Ein Geraet zeigt seinen eingerichteten Server als QR-Code, ein zweites
 * scannt ihn und uebernimmt Adresse und Token, ohne beides abzutippen -
 * gerade das Token ist zum Abtippen zu lang, um es sich fehlerfrei
 * zuzutrauen.
 *
 * Das Format stand bisher nur in `ios/PSMobile/UI/QRPairing.swift`. Es
 * steht jetzt hier, weil es kein Oberflaechendetail ist, sondern eine
 * Verabredung zwischen zwei Geraeten - und die darf zwischen den
 * Plattformen nicht auseinanderlaufen. Ein iPhone muss den Code eines
 * Android-Tablets lesen koennen und umgekehrt.
 *
 * Bewusst eine eigene URL statt JSON: kompakt, robust zu zerlegen, und
 * Sonderzeichen im Token sind ueber die Prozentkodierung geregelt.
 *
 *   psmobile-remote://pair?host=<Adresse>&token=<Token>
 */
object RemotePairing {

    private const val SCHEMA = "psmobile-remote"

    data class Kopplung(val host: String, val token: String)

    /** Der Text, der in den QR-Code geht. */
    fun url(host: String, token: String): String = buildString {
        append(SCHEMA).append("://pair?host=").append(kodieren(host))
        if (token.isNotEmpty()) append("&token=").append(kodieren(token))
    }

    /**
     * Zerlegt einen gescannten Code.
     *
     * @return null, wenn es kein PSMobile-Kopplungscode ist. Ein Scanner
     *   liest schliesslich jeden QR-Code, den man ihm hinhaelt - eine
     *   WLAN-Karte im Cafe darf nicht als Serveradresse enden.
     */
    fun parse(text: String): Kopplung? {
        val roh = text.trim()
        val vorsilbe = "$SCHEMA://pair?"
        if (!roh.startsWith(vorsilbe, ignoreCase = true)) return null

        val felder = roh.removePrefix(roh.take(vorsilbe.length))
            .split("&")
            .mapNotNull { teil ->
                val i = teil.indexOf('=')
                if (i <= 0) null else teil.substring(0, i) to dekodieren(teil.substring(i + 1))
            }
            .toMap()

        val host = felder["host"].orEmpty()
        if (host.isEmpty()) return null
        return Kopplung(host, felder["token"].orEmpty())
    }

    // --- Prozentkodierung -------------------------------------------------
    // commonMain hat keine URL-Klasse; die paar Zeilen sind billiger als
    // eine Abhaengigkeit, und sie muessen auf beiden Plattformen dasselbe
    // tun. Ungeschuetzt bleibt nur, was RFC 3986 als "unreserved" fuehrt.

    private const val UNRESERVIERT =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private fun kodieren(text: String): String = buildString {
        text.encodeToByteArray().forEach { b ->
            val c = b.toInt().toChar()
            if (c in UNRESERVIERT) append(c)
            else append('%').append(hex(b.toInt() and 0xFF))
        }
    }

    private fun dekodieren(text: String): String {
        val bytes = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '%' && i + 2 < text.length -> {
                    val wert = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (wert == null) { bytes.add(c.code.toByte()); i++ }
                    else { bytes.add(wert.toByte()); i += 3 }
                }
                // Ein Pluszeichen ist in einer Abfrage historisch ein
                // Leerzeichen. Wir kodieren selbst nie so, aber fremde
                // Erzeuger tun es.
                c == '+' -> { bytes.add(' '.code.toByte()); i++ }
                else -> { bytes.add(c.code.toByte()); i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hex(wert: Int): String {
        val ziffern = "0123456789ABCDEF"
        return "${ziffern[(wert shr 4) and 0xF]}${ziffern[wert and 0xF]}"
    }
}
