package de.psmobile.shared.rules

/**
 * Variable Schichthoehen: dieselbe Rechnung fuer beide Apps.
 *
 * PrusaSlicer laesst am Desktop eine Kurve ueber die Modellhoehe malen.
 * Mit dem Finger ist das nicht zu treffen, deshalb auf beiden
 * Plattformen Stuetzstellen als Zahlenpaare - Hoehe und Schichtdicke ab
 * dort. Was dazwischen liegt, ergibt sich.
 *
 * Die Pruefung steht hier und nicht in der Oberflaeche: ob ein Profil
 * gueltig ist - mindestens zwei Punkte, steigende Z-Werte, positive
 * Hoehen - darf nicht davon abhaengen, welche App gerade fragt.
 */
object LayerProfile {

    /** Eine Stuetzstelle: ab Hoehe [z] gilt Schichtdicke [height]. */
    data class Point(val z: Double, val height: Double)

    /**
     * Dieselbe Stelle, wie sie in Eingabefeldern steht. Getrennt vom
     * Punkt, weil "0,2" waehrend des Tippens auch mal "0," ist.
     */
    data class Row(val z: String, val height: String)

    /** Ein Band der Vorschau: von [fromZ] bis [toZ] gilt [heightMm]. */
    data class Segment(val fromZ: Double, val toZ: Double, val heightMm: Double)

    fun defaults(objectHeight: Double): List<Row> = listOf(
        Row("0.0", "0.20"),
        Row(decimal(objectHeight.coerceAtLeast(0.01)), "0.20"),
    )

    /** Nur was sich als Zahl lesen laesst. */
    fun points(rows: List<Row>): List<Point> = rows.mapNotNull { zeile ->
        val z = zahl(zeile.z)
        val h = zahl(zeile.height)
        if (z == null || h == null) null else Point(z, h)
    }

    /**
     * Ob sich das Profil setzen laesst.
     *
     * Der Kern verlangt mindestens zwei Paare mit streng steigenden
     * Z-Werten. Ein Profil, das das nicht erfuellt, wuerde abgelehnt -
     * und der Nutzer saehe nur, dass nichts passiert.
     */
    fun canApply(rows: List<Row>): Boolean {
        val p = points(rows)
        if (p.size != rows.size || p.size < 2) return false
        if (p.any { it.z < 0.0 || it.height <= 0.0 }) return false
        return p.zipWithNext().all { (vorher, danach) -> danach.z > vorher.z }
    }

    /** Die farbigen Baender, die dem Nutzer gezeigt werden. */
    fun segments(objectHeight: Double, points: List<Point>): List<Segment> {
        val sortiert = points.sortedBy { it.z }
        return sortiert.mapIndexedNotNull { index, punkt ->
            val ende = sortiert.getOrNull(index + 1)?.z ?: objectHeight
            if (ende > punkt.z && punkt.height > 0.0) {
                Segment(punkt.z, ende, punkt.height)
            } else {
                null
            }
        }
    }

    fun preview(objectHeight: Double, rows: List<Row>): List<Segment> =
        if (canApply(rows)) segments(objectHeight, points(rows)) else emptyList()

    /**
     * Setzt einen Punkt in das letzte Segment statt oberhalb des
     * Modells. Wer oben anfuegt, bekommt eine Stuetzstelle, die nie
     * erreicht wird.
     */
    fun addPoint(rows: List<Row>, objectHeight: Double): List<Row> {
        if (rows.size < 2) return rows + Row("0.0", "0.2")
        val vorletzt = zahl(rows[rows.size - 2].z) ?: 0.0
        val letzt = zahl(rows.last().z) ?: objectHeight.coerceAtLeast(0.01)
        val obergrenze = minOf(letzt, objectHeight).coerceAtLeast(0.01)
        if (obergrenze <= 0.02) return rows
        val mitte = ((vorletzt + obergrenze) / 2.0).coerceIn(0.01, obergrenze - 0.01)
        if (mitte <= vorletzt) return rows
        val neu = rows.toMutableList()
        neu.add(neu.size - 1, Row(decimal(mitte), rows.last().height))
        return neu
    }

    /** Zwei Punkte sind das Minimum - darunter gibt es kein Profil. */
    fun removePoint(rows: List<Row>, index: Int): List<Row> =
        if (rows.size <= 2 || index !in rows.indices) {
            rows
        } else {
            rows.toMutableList().also { it.removeAt(index) }
        }

    fun updateRow(rows: List<Row>, index: Int, z: String, height: String): List<Row> =
        if (index !in rows.indices) {
            rows
        } else {
            rows.toMutableList().also { it[index] = Row(z, height) }
        }

    fun fromPoints(objectHeight: Double, points: List<Point>): List<Row> =
        if (points.isEmpty()) {
            defaults(objectHeight)
        } else {
            points.map { Row(decimal(it.z), decimal(it.height)) }
        }

    /** Komma wie Punkt annehmen - je nach Tastatur kommt beides. */
    private fun zahl(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    private fun decimal(v: Double): String {
        val gerundet = kotlin.math.round(v * 100) / 100
        val ganz = gerundet.toLong()
        val rest = kotlin.math.round((gerundet - ganz) * 100).toLong()
        return if (rest == 0L) "$ganz.0" else "$ganz." + rest.toString().padStart(2, '0')
    }
}
