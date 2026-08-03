package de.psmobile.shared.rules

/**
 * Was nach einem Slice dasteht - und wie es dasteht.
 *
 * Bisher hatte keine der beiden Apps eine Zusammenfassung: man tippte
 * auf "G-Code", und sichtbar geschah nichts. Die Zahlen lagen zwar vor
 * (Druckzeit, Verbrauch, Kosten), aber nur im Advanced Mode und dort in
 * einer Zeile Text.
 *
 * Die Aufbereitung gehoert hierher, nicht in die Oberflaeche: eine
 * Druckzeit, die auf Android "2 h 14 min" heisst und auf iOS "134 min",
 * ist derselbe Wert in zwei Sprachen - und der Unterschied faellt
 * niemandem auf, der nur ein Geraet benutzt.
 */
object SliceSummary {

    /**
     * Druckzeit in der Schreibweise von PrusaSlicer: Tage, Stunden und
     * Minuten, und nur die Einheiten, die auch vorkommen. "0 h 7 min"
     * liest sich schlechter als "7 min".
     */
    fun duration(seconds: Double): String {
        if (seconds <= 0) return "–"
        val total = seconds.toLong()
        val days = total / 86_400
        val hours = (total % 86_400) / 3_600
        val minutes = (total % 3_600) / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (days > 0 || hours > 0) append("${hours}h ")
            append("${minutes}m")
        }.trim()
    }

    /** Verbrauch in Gramm, eine Nachkommastelle. */
    fun grams(value: Double): String =
        if (value <= 0) "–" else format1(value) + " g"

    /**
     * Filamentlaenge. Unter einem Meter in Millimetern - bei einem
     * Kalibrierwuerfel ist "0,4 m" keine Auskunft.
     */
    fun length(millimetres: Double): String = when {
        millimetres <= 0 -> "–"
        millimetres < 1_000 -> "${millimetres.toLong()} mm"
        else -> format1(millimetres / 1_000) + " m"
    }

    /**
     * Kosten. Ohne hinterlegten Preis steht in den Profilen eine Null -
     * dann ist die Zeile eine Behauptung und bleibt besser weg.
     */
    fun cost(value: Double): String? =
        if (value <= 0) null else format2(value)

    /** Die Zeilen der Zusammenfassung, in der Reihenfolge der Anzeige. */
    data class Row(val label: String, val value: String)

    fun rows(
        seconds: Double,
        grams: Double,
        millimetres: Double,
        cost: Double,
        objects: Int,
    ): List<Row> = buildList {
        add(Row(SimpleModeState.text("Print time", "Druckzeit"), duration(seconds)))
        add(Row(SimpleModeState.text("Material", "Material"), grams(grams)))
        add(Row(SimpleModeState.text("Filament", "Filament"), length(millimetres)))
        cost(cost)?.let { add(Row(SimpleModeState.text("Cost", "Kosten"), it)) }
        add(Row(SimpleModeState.text("Objects", "Objekte"), objects.toString()))
    }

    /**
     * Ein Dateiname aus dem Projektnamen. Leerzeichen und alles, was in
     * Dateisystemen Aerger macht, faellt weg - der Name kommt vom
     * Modell und damit letztlich von irgendeinem Downloadportal.
     */
    fun fileName(project: String): String {
        val sauber = project.trim()
            .removeSuffix(".3mf").removeSuffix(".stl").removeSuffix(".obj")
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
        return (if (sauber.isBlank()) "psmobile" else sauber) + ".gcode"
    }

    /**
     * Warum noch nicht geschnitten werden kann. Leer heisst: es kann
     * losgehen. Ein ausgegrauter Knopf ohne Begruendung laesst raten.
     */
    fun blockers(
        objects: Int,
        printer: String,
        filament: String,
        print: String,
    ): List<String> = buildList {
        if (objects == 0) add(SimpleModeState.text("Nothing on the bed", "Nichts auf dem Bett"))
        if (printer.isBlank()) add(SimpleModeState.text("No printer selected", "Drucker nicht gewählt"))
        if (filament.isBlank()) add(SimpleModeState.text("No material selected", "Material nicht gewählt"))
        if (print.isBlank()) {
            add(SimpleModeState.text("No print settings selected", "Druckeinstellungen nicht gewählt"))
        }
    }

    private fun format1(v: Double): String {
        val gerundet = kotlin.math.round(v * 10) / 10
        val ganz = gerundet.toLong()
        val zehntel = kotlin.math.round((gerundet - ganz) * 10).toLong()
        return "$ganz.$zehntel"
    }

    private fun format2(v: Double): String {
        val gerundet = kotlin.math.round(v * 100) / 100
        val ganz = gerundet.toLong()
        val rest = kotlin.math.round((gerundet - ganz) * 100).toLong()
        return "$ganz." + rest.toString().padStart(2, '0')
    }
}
