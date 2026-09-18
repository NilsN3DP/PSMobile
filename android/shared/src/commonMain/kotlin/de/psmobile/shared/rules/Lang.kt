package de.psmobile.shared.rules

/**
 * Die eingestellte Sprache.
 *
 * Ein einzelner Wert, den jede Plattform beim Start setzt - Android aus
 * PsUi, iOS aus dem Dienst. Die Regeln hier lesen ihn nur.
 *
 * Bewusst kein Nachschlagewerk: die Beschriftungen selbst kommen aus
 * PrusaSlicers eigenem Katalog (E-12) und werden je Plattform geladen.
 * Geteilt wird nur die Frage "welche Sprache gilt gerade", weil sonst
 * jede Seite ihre eigene Antwort haette.
 */
/**
 * Ein Text in beiden Sprachen.
 *
 * Bewusst eine eigene Klasse und kein Pair: Kotlins Typparameter
 * ueberleben die Bruecke nach Swift nicht. Aus `Pair<String, String>`
 * wird dort ein untypisiertes Objekt, aus dem sich die beiden Haelften
 * nicht mehr herausholen lassen.
 */
data class Bilingual(val english: String, val german: String) {
    val text: String get() = if (Lang.isGerman) german else english
}

object Lang {
    /** ISO-Kuerzel, "en" oder "de". Voreinstellung ist Englisch (E-12). */
    var current: String = "en"

    val isGerman: Boolean get() = current == "de"
}
