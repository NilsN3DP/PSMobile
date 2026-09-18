package de.psmobile.core

/** Eindeutige Winkelkonvertierung an der Android-/C-Grenze. */
internal object Angles {
    fun radiansToDegrees(value: Float): Float =
        Math.toDegrees(value.toDouble()).toFloat()

    fun degreesToRadians(value: Float): Float =
        Math.toRadians(value.toDouble()).toFloat()

    fun normalizeDegrees(value: Float): Float =
        ((value % 360f) + 360f) % 360f
}
