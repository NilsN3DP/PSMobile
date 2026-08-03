package de.psmobile.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Faelle aus UiScaleForTest auf Android und PSScaleTests auf iOS -
 * jetzt an einer Stelle. Vorher gab es sie zweimal, und nichts haette
 * gemerkt, wenn eine der beiden Seiten sich verschoben haette.
 */
class WindowScaleTest {

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue(kotlin.math.abs(expected - actual) < 0.001f,
                   "erwartet $expected, war $actual")

    @Test
    fun grossesTabletBleibtUnveraendert() {
        // Ab der Referenzgroesse wird nicht mehr hochskaliert - die Masse
        // sind fuer diesen Fall geschrieben.
        assertClose(1f, WindowScale.forWindow(1000f, 720f))
        assertClose(1f, WindowScale.forWindow(1280f, 800f))
    }

    @Test
    fun dieKnappereKanteEntscheidet() {
        // 900x576: Breite 0.90, Hoehe 0.80 - die Hoehe gewinnt.
        assertClose(0.8f, WindowScale.forWindow(900f, 576f))
        // Und andersherum: 750x720 -> Breite 0.75 gewinnt.
        assertClose(0.75f, WindowScale.forWindow(750f, 720f))
    }

    @Test
    fun untergrenzeGreift() {
        // Rechnerisch waeren das 0.556 - die Untergrenze faengt es ab,
        // damit Zielflaechen treffbar bleiben.
        assertClose(WindowScale.MIN_SCALE, WindowScale.forWindow(600f, 400f))
        assertClose(WindowScale.MIN_SCALE, WindowScale.forWindow(200f, 200f))
        assertClose(WindowScale.MIN_SCALE, WindowScale.forWindow(1f, 1f))
    }

    @Test
    fun schriftSchrumpftSchwaecherAlsKaesten() {
        val scale = WindowScale.forWindow(600f, 400f)
        val fontScale = WindowScale.fontScale(scale)
        assertTrue(fontScale > scale, "Schrift darf nicht staerker schrumpfen als Kaesten")
        assertTrue(fontScale < 1f, "Schrift muss aber mitgehen")
    }

    @Test
    fun alleGeraeteliegenImRahmen() {
        // Vom kleinsten iPhone bis zum groessten iPad, hoch und quer.
        val groessen = listOf(
            Triple("iPhone SE hoch", 375f, 667f),
            Triple("iPhone SE quer", 667f, 375f),
            Triple("iPhone 15 hoch", 393f, 852f),
            Triple("iPhone 15 Pro Max quer", 932f, 430f),
            Triple("iPad mini hoch", 744f, 1133f),
            Triple("iPad 11 quer", 1210f, 834f),
            Triple("iPad Pro 13 quer", 1376f, 1032f),
            Triple("Slide Over schmal", 320f, 1024f),
            // Und die Android-Seite, die den Anlass gab.
            Triple("gemeldetes Fenster", 600f, 400f),
        )
        for ((name, w, h) in groessen) {
            val s = WindowScale.forWindow(w, h)
            assertTrue(s >= WindowScale.MIN_SCALE, "$name faellt unter die Untergrenze")
            assertTrue(s <= 1f, "$name wird hochskaliert")
        }
    }
}
