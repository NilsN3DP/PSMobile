package de.psmobile.diagnose

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Der Wuerfel, mit dem der Selbsttest arbeitet.
 *
 * Erzeugt und nicht mitgeliefert: eine Datei in den Assets waere in
 * jedem APK, obwohl sie nur beim Selbsttest gebraucht wird, und ein
 * Modell aus dem Dateisystem des Nutzers waere kein Test, sondern ein
 * Zufall. Zwoelf Dreiecke sind schnell geschrieben und immer gleich -
 * genau das macht die Messwerte zwischen zwei Geraeten vergleichbar.
 *
 * Binaeres STL: 80 Byte Kopf, 4 Byte Anzahl, dann je 50 Byte pro
 * Dreieck (Normale und drei Ecken als float32, dazu zwei ungenutzte
 * Byte). Little Endian, so will es das Format.
 */
object Testkoerper {

    /** Kantenlaenge in Millimetern. Gross genug fuer echte Schichten. */
    private const val KANTE = 20f

    fun wuerfelDatei(context: Context): File {
        val ordner = File(context.cacheDir, "selbsttest").also { it.mkdirs() }
        return File(ordner, "wuerfel.stl").also { if (!it.exists()) it.writeBytes(wuerfel()) }
    }

    private fun wuerfel(): ByteArray {
        val k = KANTE
        // Acht Ecken, Ursprung in einer unteren Kante.
        val e = arrayOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(k, 0f, 0f),
            floatArrayOf(k, k, 0f), floatArrayOf(0f, k, 0f),
            floatArrayOf(0f, 0f, k), floatArrayOf(k, 0f, k),
            floatArrayOf(k, k, k), floatArrayOf(0f, k, k),
        )
        // Zwoelf Dreiecke, jede Flaeche zweimal geteilt. Die Reihenfolge
        // der Ecken bestimmt, wohin die Normale zeigt - nach aussen, sonst
        // haelt der Slicer den Koerper fuer umgestuelpt.
        val dreiecke = listOf(
            intArrayOf(0, 3, 2), intArrayOf(0, 2, 1),   // unten
            intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),   // oben
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4),   // vorn
            intArrayOf(2, 3, 7), intArrayOf(2, 7, 6),   // hinten
            intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),   // rechts
            intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),   // links
        )

        val puffer = ByteBuffer.allocate(84 + dreiecke.size * 50)
            .order(ByteOrder.LITTLE_ENDIAN)
        puffer.put(ByteArray(80))                 // Kopf, Inhalt egal
        puffer.putInt(dreiecke.size)

        dreiecke.forEach { d ->
            val a = e[d[0]]; val b = e[d[1]]; val c = e[d[2]]
            normale(a, b, c).forEach { puffer.putFloat(it) }
            listOf(a, b, c).forEach { p -> p.forEach { puffer.putFloat(it) } }
            puffer.putShort(0)                    // Attributbytes, ungenutzt
        }
        return puffer.array()
    }

    /** Kreuzprodukt der beiden Kanten, auf Laenge eins gebracht. */
    private fun normale(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
        val u = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val v = floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2])
        val n = floatArrayOf(
            u[1] * v[2] - u[2] * v[1],
            u[2] * v[0] - u[0] * v[2],
            u[0] * v[1] - u[1] * v[0],
        )
        val laenge = kotlin.math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
        return if (laenge == 0f) n else floatArrayOf(n[0] / laenge, n[1] / laenge, n[2] / laenge)
    }
}
