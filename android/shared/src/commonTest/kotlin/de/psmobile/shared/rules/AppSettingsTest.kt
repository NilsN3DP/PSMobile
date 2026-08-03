package de.psmobile.shared.rules

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class AppSettingsTest {

    @Test
    fun `jeder Schalter hat einen eigenen Schluessel`() {
        val keys = AppSettings.toggles.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `jeder Schalter sagt, wofuer er da ist`() {
        // Eine Einstellung ohne Begruendung zwingt zum Ausprobieren.
        AppSettings.toggles.forEach {
            assertTrue(it.why.first.isNotBlank() && it.why.second.isNotBlank(), it.key)
            assertTrue(it.title.first.isNotBlank() && it.title.second.isNotBlank(), it.key)
        }
    }

    @Test
    fun `der bestehende Schluessel fuer unpassende Profile bleibt erhalten`() {
        // Er wird schon benutzt - ein neuer Name wuerde die Einstellung
        // bestehender Installationen still zuruecksetzen.
        assertEquals("presets.show-incompatible", AppSettings.KEY_SHOW_INCOMPATIBLE)
    }

    @Test
    fun `Vorschau und Arbeitsstand sind an, unpassende Profile aus`() {
        fun byKey(k: String) = AppSettings.toggles.first { it.key == k }
        assertTrue(byKey(AppSettings.KEY_THUMBNAILS).default)
        assertTrue(byKey(AppSettings.KEY_AUTOSAVE).default)
        assertFalse(byKey(AppSettings.KEY_SHOW_INCOMPATIBLE).default)
    }

    @Test
    fun `jeder Schalter liegt in genau einer angezeigten Gruppe`() {
        val shown = AppSettings.groupsInOrder.flatMap { AppSettings.group(it) }
        assertEquals(AppSettings.toggles.size, shown.size)
        assertEquals(AppSettings.toggles.toSet(), shown.toSet())
    }

    @Test
    fun `jede Gruppe hat eine Ueberschrift`() {
        AppSettings.groupsInOrder.forEach {
            assertTrue(AppSettings.groupTitle(it).isNotBlank(), it.name)
        }
    }

    @Test
    fun `der Startmodus kennt drei Moeglichkeiten mit eigenen Beschriftungen`() {
        assertEquals(3, AppSettings.startModes.size)
        val labels = AppSettings.startModes.map(AppSettings::startModeLabel)
        assertEquals(3, labels.toSet().size)
        assertEquals(0, labels.count { it.isBlank() })
    }

    @Test
    fun `ein unbekannter Startmodus faellt auf Fragen zurueck`() {
        assertEquals(
            AppSettings.startModeLabel(AppSettings.START_ASK),
            AppSettings.startModeLabel("irgendwas-altes"),
        )
    }
}
