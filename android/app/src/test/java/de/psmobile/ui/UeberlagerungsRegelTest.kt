package de.psmobile.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jedes Popup muss seinen Inhalt in `ScaledOverlay` einpacken.
 *
 * Der Hintergrund ist ein Fehler, der die ganze App durchzog:
 * `ModalBottomSheet`, `Dialog`, `DropdownMenu` und `Popup` zeichnen in
 * einem eigenen Fenster. Was `PSMobileTheme` per CompositionLocalProvider
 * fuer `LocalDensity` setzt, kommt dort nicht an - die Popups blieben bei
 * den Vorgabegroessen des Geraets, waehrend die App darunter gestaucht
 * war. Sichtbar als "sieht komisch aus", schwer zu benennen, und an
 * zwoelf Stellen gleichzeitig.
 *
 * Warum das ein Test am Quelltext ist und keiner am Bildschirm: der
 * Stauchfaktor ist auf einem Tablet genau 1,0 (siehe `WindowScale`), ein
 * Dichtevergleich waere dort also immer gruen - auch wenn die Umhuellung
 * fehlt. Der Fehler zeigt sich erst auf kleinen Fenstern. Die Regel
 * dagegen laesst sich immer pruefen, und sie ist das, was wirklich
 * eingehalten werden muss.
 *
 * Wer ein neues Popup einbaut und die Umhuellung vergisst, faellt hier
 * auf - vor dem Einchecken, nicht nach dem Feedback.
 */
class UeberlagerungsRegelTest {

    private val oeffner = Regex("""\b(ModalBottomSheet|DropdownMenu|Dialog|Popup)\s*\(""")

    /** Wie weit hinter dem Aufruf die Umhuellung noch zaehlt. */
    private val sichtweite = 14

    private fun quellen(): List<File> {
        // Unit-Tests laufen im Modulverzeichnis (android/app), aus der IDE
        // heraus manchmal eine Ebene hoeher.
        val kandidaten = listOf(
            File("src/main/java/de/psmobile"),
            File("app/src/main/java/de/psmobile"),
            File("android/app/src/main/java/de/psmobile"),
        )
        val wurzel = kandidaten.firstOrNull { it.isDirectory }
            ?: error("Quellverzeichnis nicht gefunden, gesucht in: $kandidaten")
        return wurzel.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test
    fun jedesPopupIstUmhuellt() {
        val offen = mutableListOf<String>()
        var gefunden = 0

        for (datei in quellen()) {
            val zeilen = datei.readLines()
            zeilen.forEachIndexed { i, zeile ->
                val roh = zeile.trim()
                if (roh.startsWith("//") || roh.startsWith("*")) return@forEachIndexed
                if (!oeffner.containsMatchIn(zeile)) return@forEachIndexed
                // Die Definition eines eigenen Dialogs ist kein Aufruf.
                if (zeile.contains("fun ")) return@forEachIndexed
                gefunden++
                val fenster = zeilen.subList(i, minOf(i + sichtweite, zeilen.size))
                if (fenster.none { it.contains("ScaledOverlay") }) {
                    offen += "${datei.name}:${i + 1}  ${roh.take(70)}"
                }
            }
        }

        assertTrue(
            "Es muessen Popups gefunden werden, sonst prueft der Test nichts",
            gefunden >= 10,
        )
        assertTrue(
            "Popup ohne ScaledOverlay - der Inhalt zeichnet dann in der Dichte " +
                "des Geraets statt in der der App:\n" + offen.joinToString("\n"),
            offen.isEmpty(),
        )
    }
}
