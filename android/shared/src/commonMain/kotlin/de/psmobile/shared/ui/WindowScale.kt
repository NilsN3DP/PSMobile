package de.psmobile.shared.ui

/**
 * Wie stark die Oberflaeche insgesamt verkleinert wird.
 *
 * Die Masse in den Bildschirmen sind fuer ein Tablet mit rund 1000 x 720
 * geschrieben. Faellt das Fenster deutlich kleiner aus - Telefon,
 * geteilter Bildschirm, Slide Over -, dann sind dieselben Masse im
 * Verhaeltnis zu gross: von einer Druckerliste blieben anderthalb Zeilen
 * uebrig, von zwei Auswahlkarten eine.
 *
 * Wie die Zahl angewandt wird, entscheidet jede Plattform selbst: Android
 * staucht die wirksame Dichte, iOS rechnet die Masse einzeln durch.
 * **Die Zahl selbst steht hier**, damit beide Apps dieselbe verwenden.
 * Vorher stand sie zweimal, und nichts haette gemerkt, wenn eine Seite
 * sich verschoben haette.
 */
object WindowScale {
    // Der Name sagt, worauf sich der Faktor bezieht: auf die Groesse des
    // Fensters. Nicht zu verwechseln mit UiScale nebenan, das eine andere
    // Frage beantwortet - wie eng es ist, nicht um wieviel verkleinert
    // wird. Beide zusammen in einem Namensraum brauchen klare Namen.

    /** Die Groesse, fuer die die Masse gedacht sind. */
    const val REFERENCE_WIDTH = 1000f
    const val REFERENCE_HEIGHT = 720f

    /**
     * Untergrenze. Darunter waeren Zielflaechen physisch zu klein zum
     * Treffen. Wo es enger wird, muss der Bildschirm selbst Inhalt
     * weglassen, statt weiter zu schrumpfen.
     */
    const val MIN_SCALE = 0.7f

    /**
     * Wie stark die Schrift dem Kastenmass folgt. Text darf nicht so
     * stark schrumpfen wie Kaesten, sonst wird er unleserlich, bevor der
     * Platz wirklich knapp ist.
     */
    const val FONT_FOLLOW = 0.6f

    /**
     * Die knappere Kante entscheidet. Hochskaliert wird nie: ab der
     * Referenzgroesse stimmen die Masse bereits.
     */
    fun forWindow(width: Float, height: Float): Float {
        val byWidth = width / REFERENCE_WIDTH
        val byHeight = height / REFERENCE_HEIGHT
        return minOf(byWidth, byHeight, 1f).coerceAtLeast(MIN_SCALE)
    }

    /** Gedaempfte Fassung fuer Schriftgroessen. */
    fun fontScale(scale: Float): Float = 1f - (1f - scale) * FONT_FOLLOW
}
