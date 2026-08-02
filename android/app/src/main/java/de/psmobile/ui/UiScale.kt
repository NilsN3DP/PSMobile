package de.psmobile.ui

/**
 * Wie eng ein Bildschirm bemessen werden muss.
 *
 * TIGHT bedeutet nicht "Telefon", sondern "wenig Platz auf mindestens
 * einer Kante" - das trifft auch ein kleines Fenster auf einem Tablet
 * und den Splitscreen.
 */
enum class UiDensity { TIGHT, REGULAR }

/**
 * Eine gemeinsame Antwort auf die Frage, wie viel Luft die Oberflaeche
 * sich leisten kann.
 *
 * Es gab bereits zwei Bruchpunkte im Projekt - EasyModeLayout mit den
 * Material-Klassen 600/840 und SettingsLayout mit 720 fuer die
 * Navigationsform. Beide beantworten andere Fragen und bleiben. Was
 * fehlte, war die dritte: wie gross duerfen Abstaende und Schrift sein.
 * Die stand bisher in jedem Bildschirm einzeln, im Assistenten sogar
 * falsch herum.
 */
object UiScale {

    /**
     * Der Fehler, der das noetig gemacht hat: SetupScreen bestimmte
     *
     *     val compact = screenWidthDp > screenHeightDp
     *
     * also "compact" = Querformat. Auf einem 400x600 dp grossen Fenster
     * war das false, und der Assistent nahm die GROSSEN Masse - 24 dp
     * Rand, 58 dp hohe Knoepfe, 22 sp Titel. Von der Druckerliste blieben
     * anderthalb Zeilen sichtbar.
     *
     * Richtig ist: eng wird es, sobald eine der beiden Kanten knapp ist.
     * Die Breite entscheidet ueber die Material-Klasse, die Hoehe ueber
     * die Frage, ob eine Liste ueberhaupt noch Zeilen zeigt.
     */
    fun density(widthDp: Int, heightDp: Int): UiDensity =
        if (EasyModeLayout.widthClass(widthDp) == EasyWidthClass.COMPACT ||
            heightDp < MIN_COMFORTABLE_HEIGHT_DP
        ) {
            UiDensity.TIGHT
        } else {
            UiDensity.REGULAR
        }

    /**
     * Unterhalb dieser Hoehe bleibt von einer Liste mit Kopfzeile,
     * Suchfeld und Abschlussknopf zu wenig uebrig. Der Wert ist an der
     * Ersteinrichtung gemessen: Kopf und Fuss brauchen zusammen rund
     * 300 dp, darunter sollen noch mindestens vier Zeilen passen.
     */
    const val MIN_COMFORTABLE_HEIGHT_DP = 640

    /** Ein Faktor fuer Abstaende - Schrift skaliert eigenstaendig. */
    fun spacingFactor(density: UiDensity): Float = when (density) {
        UiDensity.TIGHT -> 0.65f
        UiDensity.REGULAR -> 1f
    }
}
