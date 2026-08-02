package de.psmobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleForTest {

    @Test
    fun `grosses Tablet bleibt unveraendert`() {
        // Ab der Referenzgroesse wird nicht mehr hochskaliert - die Masse
        // sind fuer diesen Fall geschrieben.
        assertEquals(1f, uiScaleFor(1000, 720), 0.001f)
        assertEquals(1f, uiScaleFor(1280, 800), 0.001f)
    }

    @Test
    fun `die knappere Kante entscheidet`() {
        // 900x648: Breite ergaebe 0.90, Hoehe 0.90 - gleich.
        // 900x576: Breite 0.90, Hoehe 0.80 - die Hoehe gewinnt.
        assertEquals(0.8f, uiScaleFor(900, 576), 0.001f)
        // Und andersherum: 750x720 -> Breite 0.75 gewinnt.
        assertEquals(0.75f, uiScaleFor(750, 720), 0.001f)
    }

    @Test
    fun `das gemeldete Fenster liegt auf der Untergrenze`() {
        // Der Fall aus der Fehlermeldung: 600x400 dp. Rechnerisch waeren
        // das 0.556 - die Untergrenze faengt es bei 0.7 ab, damit
        // Zielflaechen treffbar bleiben. Die restliche Enge muessen die
        // Bildschirme selbst aufloesen, indem sie Inhalt weglassen.
        assertEquals(MIN_SCALE, uiScaleFor(600, 400), 0.001f)
    }

    @Test
    fun `es wird nie unter die Untergrenze skaliert`() {
        // Sonst waeren Zielflaechen physisch nicht mehr zu treffen.
        assertEquals(MIN_SCALE, uiScaleFor(200, 200), 0.001f)
        assertEquals(MIN_SCALE, uiScaleFor(1, 1), 0.001f)
    }

    @Test
    fun `Schrift schrumpft schwaecher als die Kaesten`() {
        val scale = uiScaleFor(600, 400)
        val fontScale = 1f - (1f - scale) * FONT_FOLLOW
        assertTrue("Schrift darf nicht staerker schrumpfen als Kaesten",
                   fontScale > scale)
        assertTrue("Schrift muss aber mitgehen", fontScale < 1f)
    }
}
