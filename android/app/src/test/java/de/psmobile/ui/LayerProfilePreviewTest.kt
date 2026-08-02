package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LayerProfilePreviewTest {
    @Test
    fun `profile points become contiguous preview segments`() {
        assertEquals(
            listOf(
                LayerPreviewSegment(0.0, 10.0, 0.12),
                LayerPreviewSegment(10.0, 20.0, 0.28),
            ),
            layerProfilePreviewSegments(20.0, listOf(0.0 to 0.12, 10.0 to 0.28)),
        )
    }

    @Test
    fun `new point is inserted before the top profile point`() {
        assertEquals(
            listOf("0.0" to "0.2", "10.0" to "0.2", "20.0" to "0.2"),
            insertLayerProfilePoint(listOf("0.0" to "0.2", "20.0" to "0.2"), 20.0),
        )
    }

    @Test
    fun `tiny model does not create an invalid layer point`() {
        val rows = listOf("0.0" to "0.2", "0.01" to "0.2")
        assertEquals(rows, insertLayerProfilePoint(rows, 0.01))
    }
}
