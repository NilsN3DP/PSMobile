package de.psmobile.shared.ui

/**
 * Eckenradien für beide Plattformen.
 *
 * Vorher gab es auf iOS zwölf verschiedene Radien (1 bis 16, am
 * häufigsten 3 und 4) und auf Android fünfzehn (1 bis 18, am häufigsten
 * 8 und 10). Beide Seiten waren in sich uneinheitlich und
 * untereinander verschieden — iOS wirkte kantig, Android runder.
 *
 * Aufgefallen ist es an der Filamentauswahl: die ist auf iOS ein
 * SwiftUI-`.sheet` und bekommt damit Apples eigene, großzügige Rundung,
 * während der Einstellungs-Panel daneben mit `cornerRadius: 3`
 * praktisch eckig ist. Nebeneinander sieht das aus wie zwei Apps.
 *
 * Deshalb dieselbe Lösung wie bei [WindowScale]: eine kleine Skala, an
 * einer Stelle, für beide Seiten. Vier Werte statt siebenundzwanzig.
 *
 * Die Zahlen sind Punkte beziehungsweise dp im Referenzmaßstab. Wer
 * skaliert, gibt sie durch `ps.pt()` (iOS) — auf Android staucht das
 * Theme die Dichte ohnehin global.
 */
object Corners {

    /**
     * Blätter und große Dialoge: Filamentauswahl, Einstellungen,
     * Ersteinrichtung, ZIP-Frage.
     *
     * Der Wert kommt von Apples Blattrundung auf iPadOS — das ist die
     * Anmutung, die als Vorbild gewählt wurde.
     */
    const val SHEET = 28f

    /**
     * Karten und Panels innerhalb eines Bildschirms: Materialkarte,
     * Bettkarte, Werkzeugkarte, die schwebende Objektleiste.
     */
    const val CARD = 14f

    /**
     * Felder, Knöpfe und Zeilen: Eingabefeld, Profilzeile, Knopf im
     * Formular.
     */
    const val FIELD = 8f

    /**
     * Kapseln: Typfilter, Bettkapseln, Auswahlchips. Der Wert ist
     * absichtlich größer als jede vorkommende Höhe — das ergibt eine
     * halbrunde Seite, unabhängig davon, wie hoch die Kapsel gerade
     * ist.
     */
    const val PILL = 999f
}
