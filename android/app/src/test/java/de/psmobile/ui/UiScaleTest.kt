package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {

    @Test
    fun `schmales Fenster wird eng bemessen`() {
        // Genau der Fall, der die Ersteinrichtung unbrauchbar machte:
        // 400x600 dp, hochkant.
        assertEquals(UiDensity.TIGHT, UiScale.density(widthDp = 400, heightDp = 600))
    }

    @Test
    fun `flaches Querformat wird eng bemessen`() {
        // Breit genug, aber zu niedrig fuer eine Liste mit Kopf und Fuss.
        assertEquals(UiDensity.TIGHT, UiScale.density(widthDp = 1280, heightDp = 560))
    }

    @Test
    fun `grosses Tablet bekommt die regulaeren Masse`() {
        assertEquals(UiDensity.REGULAR, UiScale.density(widthDp = 1280, heightDp = 800))
        assertEquals(UiDensity.REGULAR, UiScale.density(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun `die Breitenklasse entscheidet mit`() {
        // Unter 600 dp Breite ist es eng, auch wenn die Hoehe reicht.
        assertEquals(UiDensity.TIGHT, UiScale.density(widthDp = 599, heightDp = 2000))
        assertEquals(UiDensity.REGULAR, UiScale.density(widthDp = 600, heightDp = 2000))
    }

    @Test
    fun `enge Bildschirme bekommen kleinere Abstaende`() {
        val tight = UiScale.spacingFactor(UiDensity.TIGHT)
        val regular = UiScale.spacingFactor(UiDensity.REGULAR)
        assertEquals(1f, regular)
        assert(tight < regular) { "eng muss kleinere Abstaende ergeben als regulaer" }
    }
}
