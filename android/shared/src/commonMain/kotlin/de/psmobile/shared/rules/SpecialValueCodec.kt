package de.psmobile.shared.rules

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Verlustfreie Codecs für die strukturierten Werte, die PrusaSlicer in
 * normalen Konfigurationsfeldern serialisiert. Die UI arbeitet dadurch mit
 * Zeilen, Punkten und Matrizen; die Prusa-Syntax bleibt an einer Stelle.
 */
object SpecialValueCodec {
    data class BedPoint(val x: Double, val y: Double)

    data class Ramming(
        val lineWidthPercent: Double,
        val lineSpacingPercent: Double,
        val sampledSpeeds: List<Double>,
        val controlPoints: List<BedPoint>,
    )

    fun parseBedShape(value: String): List<BedPoint>? {
        if (value.isBlank()) return emptyList()
        return value.split(',').map { token ->
            val separator = token.indexOf('x', startIndex = 1)
            if (separator < 1) return null
            val x = token.substring(0, separator).trim().toDoubleOrNull() ?: return null
            val y = token.substring(separator + 1).trim().toDoubleOrNull() ?: return null
            BedPoint(x, y)
        }
    }

    fun encodeBedShape(points: List<BedPoint>): String =
        points.joinToString(",") { "${decimal(it.x)}x${decimal(it.y)}" }

    fun polygonArea(points: List<BedPoint>): Double {
        if (points.size < 3) return 0.0
        var twiceArea = 0.0
        points.indices.forEach { index ->
            val next = points[(index + 1) % points.size]
            twiceArea += points[index].x * next.y - next.x * points[index].y
        }
        return abs(twiceArea) / 2.0
    }

    fun parseFloats(value: String): List<Double>? {
        if (value.isBlank()) return emptyList()
        return value.split(',').map { it.trim().toDoubleOrNull() ?: return null }
    }

    fun encodeFloats(values: List<Double>): String =
        values.joinToString(",") { decimal(it) }

    /**
     * Gegenstück zu libslic3r::escape_strings_cstyle. Elemente sind durch
     * Semikolon getrennt; komplexe Werte stehen in Anführungszeichen.
     */
    fun encodeStrings(values: List<String>): String =
        values.joinToString(";") { value ->
            val quote = (values.size == 1 && value.isEmpty()) ||
                value.any { it == ' ' || it == ';' || it == '\t' || it == '\\' ||
                    it == '"' || it == '\r' || it == '\n' }
            if (!quote) value else buildString {
                append('"')
                value.forEach { char ->
                    when (char) {
                        '\\', '"' -> {
                            append('\\')
                            append(char)
                        }
                        '\r' -> append("\\r")
                        '\n' -> append("\\n")
                        else -> append(char)
                    }
                }
                append('"')
            }
        }

    fun parseStrings(value: String): List<String>? {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && (value[index] == ' ' || value[index] == '\t')) {
                index++
            }
            if (index >= value.length) break
            val output = StringBuilder()
            if (value[index] == '"') {
                index++
                var closed = false
                while (index < value.length) {
                    val char = value[index++]
                    when {
                        char == '"' -> {
                            closed = true
                            break
                        }
                        char == '\\' -> {
                            if (index >= value.length) return null
                            output.append(
                                when (val escaped = value[index++]) {
                                    'r' -> '\r'
                                    'n' -> '\n'
                                    else -> escaped
                                }
                            )
                        }
                        else -> output.append(char)
                    }
                }
                if (!closed) return null
                while (index < value.length && (value[index] == ' ' || value[index] == '\t')) {
                    index++
                }
                if (index < value.length && value[index] != ';') return null
            } else {
                while (index < value.length && value[index] != ';') {
                    output.append(value[index++])
                }
                while (output.isNotEmpty() &&
                    (output.last() == ' ' || output.last() == '\t')) {
                    // deleteCharAt ist auf Kotlin/Native missbilligt - deleteAt heisst
                    // dasselbe und gilt auf beiden Plattformen.
                    output.deleteAt(output.lastIndex)
                }
            }
            result += output.toString()
            if (index < value.length) {
                if (value[index] != ';') return null
                index++
                if (index == value.length) result += ""
            }
        }
        return result
    }

    fun parseRamming(serializedVector: String): Ramming? {
        val parameter = parseStrings(serializedVector)?.firstOrNull() ?: return null
        val halves = parameter.split('|', limit = 2)
        if (halves.size != 2) return null
        val left = halves[0].trim().split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .map { it.toDoubleOrNull() ?: return null }
        if (left.size < 2) return null
        val right = halves[1].trim().split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .map { it.toDoubleOrNull() ?: return null }
        if (right.size % 2 != 0) return null
        return Ramming(
            lineWidthPercent = left[0],
            lineSpacingPercent = left[1],
            sampledSpeeds = left.drop(2),
            controlPoints = right.chunked(2).map { BedPoint(it[0], it[1]) },
        )
    }

    /**
     * Die Desktop-Kurve wird in 0,25-s-Schritten gespeichert. Wenn nur
     * Linienbreite/-abstand geändert wurden, bleiben die Originalsamples
     * exakt erhalten. Bei geänderten Punkten erzeugt die UI lineare,
     * robuste Samples; die Kontrollpunkte werden zusätzlich gespeichert.
     */
    fun encodeRamming(value: Ramming, rebuildSamples: Boolean = false): String {
        val samples = if (rebuildSamples) sampleRamming(value.controlPoints) else value.sampledSpeeds
        val parameter = buildString {
            append(decimal(value.lineWidthPercent))
            append(' ')
            append(decimal(value.lineSpacingPercent))
            samples.forEach {
                append(' ')
                append(decimal(it))
            }
            append('|')
            value.controlPoints.forEach {
                append(' ')
                append(decimal(it.x))
                append(' ')
                append(decimal(it.y))
            }
        }
        return encodeStrings(listOf(parameter))
    }

    private fun sampleRamming(points: List<BedPoint>): List<Double> {
        val sorted = points.sortedBy { it.x }
        val duration = sorted.lastOrNull()?.x ?: return emptyList()
        val count = ceil(duration / 0.25).toInt().coerceAtLeast(1)
        return List(count) { index ->
            val x = (index + 0.5) * 0.25
            interpolate(sorted, x).coerceAtLeast(0.0)
        }
    }

    private fun interpolate(points: List<BedPoint>, x: Double): Double {
        if (points.isEmpty()) return 0.0
        if (x <= points.first().x) return points.first().y
        if (x >= points.last().x) return points.last().y
        val right = points.indexOfFirst { it.x >= x }.coerceAtLeast(1)
        val a = points[right - 1]
        val b = points[right]
        if (b.x == a.x) return b.y
        return a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x))
    }

    /**
     * Sechs Nachkommastellen, immer mit Punkt, ohne nachlaufende Nullen.
     *
     * Von Hand gerechnet statt mit String.format: das gibt es nur auf der
     * JVM. Auf die Landeseinstellung darf es hier ohnehin nicht ankommen -
     * die Werte gehen in PrusaSlicers Konfiguration, und die erwartet
     * einen Punkt. Ein deutsches Komma haette dort eine unlesbare Datei
     * ergeben.
     *
     * Gerundet wird von der Null weg, wie es %.6f auch tut. Die sechste
     * Nachkommastelle entscheidet bei Bettkoordinaten ueber ein
     * Tausendstel Millimeter - der Unterschied ist rechnerisch da und
     * praktisch bedeutungslos.
     */
    private fun decimal(value: Double): String {
        if (!value.isFinite()) return "0"

        val negativ = value < 0
        val skaliert = kotlin.math.round(kotlin.math.abs(value) * 1_000_000.0).toLong()
        val ganz = skaliert / 1_000_000
        val bruch = (skaliert % 1_000_000).toString().padStart(6, '0')

        val text = ((if (negativ) "-" else "") + ganz + "." + bruch)
            .trimEnd('0')
            .trimEnd('.')
        return if (text == "-0" || text.isEmpty()) "0" else text
    }
}
