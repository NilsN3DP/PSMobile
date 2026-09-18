package de.psmobile.shared.rules

/**
 * Welche Modelldateien die App oeffnen kann.
 *
 * Die Liste stand vorher zweimal - in `MainActivity.MODEL_EXTENSIONS`
 * und in `SlicerModel.unterstuetzteModellendungen`. Eine Seite haette
 * sich verschieben koennen, ohne dass etwas gemeldet haette; genau das
 * ist bei den Uebersetzungshelfern schon passiert.
 *
 * Die Dateiauswahl beider Systeme laesst mehr durch, als der Kern lesen
 * kann (`.item` auf iOS, `application/octet-stream` auf Android). Ohne
 * eine Pruefung davor landet eine fremde Datei im Kern und kommt als
 * unverstaendlicher Fehler zurueck - auf einem Geraet sah das so aus:
 * "Cannot load OCCTWrapper.so: dlopen(...)". Deshalb wird die Endung
 * vor dem ersten Kernaufruf geprueft.
 */
object ModelFormats {

    /** Immer lesbar - dafuer braucht der Kern keine Zusatzbibliothek. */
    private val grundformate = setOf("stl", "obj", "3mf", "amf")

    /** Braucht OCCT im Kern. */
    private val stepFormate = setOf("step", "stp")

    /**
     * Ob dieser Build STEP lesen kann.
     *
     * Das ist keine Frage des Geschmacks, sondern des Kerns: ohne
     * eingebautes OCCT wirft libslic3r beim ersten STEP-Modell. Beide
     * Plattformen setzen den Wert beim Start auf das, was ihr eigener
     * Kern kann - siehe `PsmCore` bzw. `SlicerModel`.
     */
    var stepVerfuegbar: Boolean = false

    /** Die Endungen, die dieser Build wirklich oeffnen kann. */
    fun endungen(): Set<String> =
        if (stepVerfuegbar) grundformate + stepFormate else grundformate

    /** Ob eine Datei mit dieser Endung geladen werden darf. */
    fun erlaubt(endung: String): Boolean =
        endungen().contains(endung.trim().lowercase().removePrefix("."))

    /** Die Liste, wie sie in einer Meldung erscheint: "3MF, AMF, OBJ, STL". */
    fun anzeige(): String =
        endungen().sorted().joinToString(", ") { it.uppercase() }

    /** Die Meldung fuer eine Datei, die dieser Build nicht lesen kann. */
    fun nichtUnterstuetzt(endung: String): String {
        val e = endung.trim().uppercase().removePrefix(".")
        return SimpleModeState.text(
            "$e files are not supported. Use ${anzeige()}.",
            "$e-Dateien werden nicht unterstützt. Möglich sind ${anzeige()}.",
        )
    }
}
