package de.psmobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.psmobile.shared.rules.Lang
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.shared.rules.TabsCatalog
import org.json.JSONObject

/**
 * Der Kurzweg zu einem uebersetzten Text - Gegenstueck zu
 * `ios/PSMobile/Core/Texte.swift`. Die Sprache entscheidet das
 * gemeinsame Modul (`Lang.current`), damit beide Apps dasselbe sagen.
 */
fun st(english: String, german: String): String {
    // Die Sprache als Compose-Zustand mitlesen: sonst blieb der Bildschirm,
    // auf dem man die Sprache umstellt, bis zum naechsten Oeffnen in der
    // alten (Emulator, 15.09.2026). Lang.current selbst ist ein schlichtes
    // Feld im gemeinsamen Modul und loest keine Neuzeichnung aus.
    Sprache.aktuell
    return SimpleModeState.text(english, german)
}

/** Spiegel von `Lang.current` als Compose-Zustand - gesetzt in PsUiCatalog.load. */
object Sprache {
    var aktuell by mutableStateOf("en")
}

/**
 * Zugriff auf die aus PrusaSlicer uebernommene Oberflaechen-Definition -
 * Gegenstueck zu `ios/PSMobile/UI/PsUiCatalog.swift`.
 *
 * Die Dateien unter `assets/psui` liest [PsUi]; die Seitenstruktur wird
 * wie auf iOS vom gemeinsamen Modul (`TabsCatalog`) ausgewertet, damit
 * beide Apps dieselben Entscheidungen treffen.
 */
object PsUiCatalog {

    var tabs: Map<String, List<TabsCatalog.Page>> = emptyMap()
        private set
    var language: String = "en"
        private set

    private var strings: Map<String, String> = emptyMap()
    private var geladenFuer: String? = null

    fun load(context: Context, language: String = "en") {
        this.language = language
        Lang.current = language
        Sprache.aktuell = language
        PsUi.load(context, language)
        if (geladenFuer == null) {
            tabs = runCatching {
                TabsCatalog.parse(
                    context.assets.open("psui/tabs.json").bufferedReader().use { it.readText() },
                )
            }.getOrDefault(emptyMap())
        }
        strings = if (language == "en") emptyMap() else runCatching {
            val o = JSONObject(
                context.assets.open("psui/lang_$language.json").bufferedReader().use { it.readText() },
            )
            buildMap(o.length()) {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, o.getString(k))
                }
            }
        }.getOrDefault(emptyMap())
        geladenFuer = language
    }

    /** Uebersetzt eine Beschriftung aus PrusaSlicers Katalog. */
    fun tr(english: String): String = strings[english] ?: english

    /** Seiten eines Bereichs, bereits auf die Duesenzahl vervielfacht. */
    fun pages(tab: String, extruderCount: Int): List<TabsCatalog.Page> {
        val seiten = tabs[tab] ?: return emptyList()
        return TabsCatalog.expand(seiten, extruderCount)
    }
}

/**
 * Unsichtbare Marke fuer Bedienungshilfen und Oberflaechentests -
 * Gegenstueck zu `PSMarke` in `SimpleModeView.swift`.
 */
@Composable
fun PSMarke(name: String) {
    Box(Modifier.size(1.dp).testTag(name))
}

/**
 * Aus "#RRGGBB", wie PrusaSlicer Filamentfarben notiert. Null, wenn
 * nichts oder etwas anderes dasteht - Gegenstueck zu
 * `Color(hexString:)` in `PrusaColors.swift`.
 */
fun colorFromHex(hexString: String): Color? {
    var text = hexString.trim()
    if (text.startsWith("#")) text = text.substring(1)
    if (text.length != 6) return null
    val wert = text.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or wert)
}
