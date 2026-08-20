package de.psmobile.ui

import de.psmobile.shared.rules.LayerProfile

/**
 * Pure editor state for variable layer heights. Keeping validation outside
 * Compose makes every layout (dialog, inspector or full page) behave alike.
 */
internal data class LayerProfileEditorState(
    val objectHeight: Double,
    val rows: List<Pair<String, String>>,
) {
    val validPoints: List<Pair<Double, Double>> = rows.mapNotNull { (z, height) ->
        val parsedZ = NumberCodec.parseDouble(z)
        val parsedHeight = NumberCodec.parseDouble(height)
        if (parsedZ == null || parsedHeight == null) null else parsedZ to parsedHeight
    }

    val canApply: Boolean = validPoints.size == rows.size &&
        validPoints.size >= 2 &&
        validPoints.all { (z, height) -> z >= 0.0 && height > 0.0 } &&
        validPoints.zipWithNext().all { (previous, next) -> next.first > previous.first }

    val previewSegments: List<LayerPreviewSegment> =
        if (canApply) layerProfilePreviewSegments(objectHeight, validPoints) else emptyList()

    fun updateRow(index: Int, z: String = rows[index].first, height: String = rows[index].second) =
        copy(rows = rows.toMutableList().also { it[index] = z to height })

    fun addPoint() = copy(rows = insertLayerProfilePoint(rows, objectHeight))

    fun removePoint(index: Int) =
        if (rows.size <= 2 || index !in rows.indices) this
        else copy(rows = rows.toMutableList().also { it.removeAt(index) })

    fun reset() = copy(rows = defaults(objectHeight))

    companion object {
        fun defaults(objectHeight: Double) = listOf(
            "0.0" to "0.20",
            objectHeight.coerceAtLeast(0.01).toString() to "0.20",
        )

        /**
         * Aus Kernpunkten. Die Umrechnung steht in der gemeinsamen
         * Regel, nicht hier: der Kern liefert Treppenstufen mit
         * doppelten Z-Werten, und wie man die zusammenzieht, darf nicht
         * davon abhaengen, welche App gerade fragt.
         */
        fun fromProfile(objectHeight: Double, points: List<Pair<Double, Double>>) =
            LayerProfileEditorState(
                objectHeight = objectHeight,
                rows = LayerProfile.fromPoints(
                    objectHeight,
                    points.map { (z, height) -> LayerProfile.Point(z, height) },
                ).map { it.z to it.height },
            )
    }
}
