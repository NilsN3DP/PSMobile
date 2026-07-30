package de.psmobile.ui

import java.util.Locale

/**
 * Zahlen fuer native Konfigurationen bleiben immer punktgetrennt.
 * Eingaben akzeptieren Punkt und Komma, damit die deutsche Tastatur
 * nicht gegen den PrusaSlicer-Parser arbeitet.
 */
internal object NumberCodec {
    fun oneDecimal(value: Float): String =
        String.format(Locale.ROOT, "%.1f", value)

    fun twoDecimals(value: Float): String =
        String.format(Locale.ROOT, "%.2f", value)

    fun parseFloat(value: String): Float? =
        value.trim().replace(',', '.').toFloatOrNull()

    fun parseDouble(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()
}
