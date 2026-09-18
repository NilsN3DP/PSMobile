package de.psmobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedSnapshotDecoderTest {
    @Test
    fun `legacy count array produces one complete bed instead of treating the count as metadata`() {
        val beds = BedSnapshotDecoder.decode(
            values = intArrayOf(0, 3),
            metadata = mapOf(0 to BedSnapshotDecoder.Metadata("Prototype", true)),
        )

        assertEquals(1, beds.size)
        assertEquals(0, beds.single().index)
        assertEquals(3, beds.single().objectCount)
        assertEquals(3, beds.single().instanceCount)
        assertEquals("Prototype", beds.single().name)
        assertTrue(beds.single().locked)
        assertTrue(beds.single().active)
    }

    @Test
    fun `legacy count array supplies stable defaults when metadata was never edited`() {
        val beds = BedSnapshotDecoder.decode(
            values = intArrayOf(1, 0, 2),
            metadata = emptyMap(),
        )

        assertEquals(listOf("Bed 1", "Bed 2"), beds.map { it.name })
        assertEquals(listOf(0, 2), beds.map { it.objectCount })
        assertFalse(beds[0].active)
        assertTrue(beds[1].active)
        assertFalse(beds.any { it.locked })
    }
}
