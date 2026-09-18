package de.psmobile.shared.net

/**
 * MD5 nach RFC 1321.
 *
 * Warum von Hand: HTTP-Digest schreibt MD5 vor, und die Bruecke braucht
 * es auf beiden Plattformen. Android haette MessageDigest, iOS
 * CommonCrypto - zwei Wege zum selben Ergebnis, beide nur ueber
 * plattformabhaengigen Code erreichbar. Die knapp hundert Zeilen hier
 * gelten fuer beide und lassen sich gegen die Testvektoren aus dem RFC
 * pruefen.
 *
 * Nicht fuer Neues verwenden: MD5 ist als Hashverfahren gebrochen. Es
 * steht hier, weil PrusaLink es im Digest-Verfahren verlangt, und dort
 * traegt die Sicherheit die nonce, nicht der Hash.
 */
internal object Md5 {

    /** Die 64 Konstanten aus RFC 1321, ausgeschrieben statt gerechnet. */
    private val K = intArrayOf(
        0xd76aa478u.toInt(), 0xe8c7b756u.toInt(), 0x242070dbu.toInt(), 0xc1bdceeeu.toInt(),
        0xf57c0fafu.toInt(), 0x4787c62au.toInt(), 0xa8304613u.toInt(), 0xfd469501u.toInt(),
        0x698098d8u.toInt(), 0x8b44f7afu.toInt(), 0xffff5bb1u.toInt(), 0x895cd7beu.toInt(),
        0x6b901122u.toInt(), 0xfd987193u.toInt(), 0xa679438eu.toInt(), 0x49b40821u.toInt(),
        0xf61e2562u.toInt(), 0xc040b340u.toInt(), 0x265e5a51u.toInt(), 0xe9b6c7aau.toInt(),
        0xd62f105du.toInt(), 0x02441453u.toInt(), 0xd8a1e681u.toInt(), 0xe7d3fbc8u.toInt(),
        0x21e1cde6u.toInt(), 0xc33707d6u.toInt(), 0xf4d50d87u.toInt(), 0x455a14edu.toInt(),
        0xa9e3e905u.toInt(), 0xfcefa3f8u.toInt(), 0x676f02d9u.toInt(), 0x8d2a4c8au.toInt(),
        0xfffa3942u.toInt(), 0x8771f681u.toInt(), 0x6d9d6122u.toInt(), 0xfde5380cu.toInt(),
        0xa4beea44u.toInt(), 0x4bdecfa9u.toInt(), 0xf6bb4b60u.toInt(), 0xbebfbc70u.toInt(),
        0x289b7ec6u.toInt(), 0xeaa127fau.toInt(), 0xd4ef3085u.toInt(), 0x04881d05u.toInt(),
        0xd9d4d039u.toInt(), 0xe6db99e5u.toInt(), 0x1fa27cf8u.toInt(), 0xc4ac5665u.toInt(),
        0xf4292244u.toInt(), 0x432aff97u.toInt(), 0xab9423a7u.toInt(), 0xfc93a039u.toInt(),
        0x655b59c3u.toInt(), 0x8f0ccc92u.toInt(), 0xffeff47du.toInt(), 0x85845dd1u.toInt(),
        0x6fa87e4fu.toInt(), 0xfe2ce6e0u.toInt(), 0xa3014314u.toInt(), 0x4e0811a1u.toInt(),
        0xf7537e82u.toInt(), 0xbd3af235u.toInt(), 0x2ad7d2bbu.toInt(), 0xeb86d391u.toInt(),
    )

    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    fun hex(text: String): String = hex(text.encodeToByteArray())

    fun hex(input: ByteArray): String {
        var a0 = 0x67452301
        var b0 = 0xefcdab89u.toInt()
        var c0 = 0x98badcfeu.toInt()
        var d0 = 0x10325476

        // Auffuellen: eine Eins, dann Nullen, dann die Laenge in Bits als
        // 64-Bit-Zahl - alles in umgekehrter Bytefolge.
        val laenge = ((input.size + 8) / 64 + 1) * 64
        val block = ByteArray(laenge)
        input.copyInto(block)
        block[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        for (i in 0 until 8) {
            block[laenge - 8 + i] = ((bits ushr (8 * i)) and 0xFF).toByte()
        }

        val m = IntArray(16)
        var offset = 0
        while (offset < laenge) {
            for (j in 0 until 16) {
                val p = offset + j * 4
                m[j] = (block[p].toInt() and 0xFF) or
                    ((block[p + 1].toInt() and 0xFF) shl 8) or
                    ((block[p + 2].toInt() and 0xFF) shl 16) or
                    ((block[p + 3].toInt() and 0xFF) shl 24)
            }

            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                    i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val summe = a + f + K[i] + m[g]
                a = d
                d = c
                c = b
                b += rotl(summe, S[i])
            }

            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }

        return leHex(a0) + leHex(b0) + leHex(c0) + leHex(d0)
    }

    private fun rotl(v: Int, n: Int): Int = (v shl n) or (v ushr (32 - n))

    /** Ein Wort in umgekehrter Bytefolge als Hex - so gibt MD5 aus. */
    private fun leHex(v: Int): String {
        val ziffern = "0123456789abcdef"
        val sb = StringBuilder(8)
        for (i in 0 until 4) {
            val b = (v ushr (8 * i)) and 0xFF
            sb.append(ziffern[b shr 4]).append(ziffern[b and 0x0F])
        }
        return sb.toString()
    }
}
