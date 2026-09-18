package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BedStripContractTest {

    private fun bed(
        id: Int,
        name: String = "Bed $id",
        locked: Boolean = false,
        objects: Int = 0,
        instances: Int = objects,
    ) = BedInput(id, name, locked, objects, instances)

    @Test
    fun stateCreatesOneFallbackBedAndNormalizesAnEmptyActiveIndex() {
        val state = BedStripContract.state(emptyList(), activeIndex = -4)

        assertEquals(0, state.activeIndex)
        assertEquals(listOf(BedStripItem(0, "Bed 1", true, false, 0, 0, true, true, true, false)), state.items)
        assertEquals(ArrangeAvailability.EMPTY, state.arrange)
        assertTrue(state.canAdd)
    }

    @Test
    fun stateClampsAnOutOfRangeActiveIndexAndKeepsItemOrderNamesAndIds() {
        val state = BedStripContract.state(
            listOf(bed(7, "First", objects = 1), bed(42, "Second", locked = true, objects = 2)),
            activeIndex = 99,
        )

        assertEquals(1, state.activeIndex)
        assertEquals(listOf(7, 42), state.items.map { it.id })
        assertEquals(listOf("First", "Second"), state.items.map { it.name })
        assertEquals(listOf(false, true), state.items.map { it.active })
        assertEquals(ArrangeAvailability.LOCKED, state.arrange)
    }

    @Test
    fun onlyBedCanBeManagedButCannotBeRemoved() {
        val item = BedStripContract.state(listOf(bed(5, objects = 1)), 0).items.single()

        assertTrue(item.canSelect)
        assertTrue(item.canToggleLock)
        assertTrue(item.canRename)
        assertFalse(item.canRemove)
        assertNull(BedStripContract.remove(listOf(bed(5)), activeIndex = 0, id = 5))
    }

    @Test
    fun anInstancedBedCannotBeRemovedEvenWhenItsObjectCountIsStale() {
        val input = listOf(bed(7, objects = 1), bed(42, objects = 0, instances = 3))
        val second = BedStripContract.state(input, activeIndex = 1).items[1]

        assertFalse(second.canRemove)
        assertNull(BedStripContract.remove(input, activeIndex = 1, id = 42))
    }

    @Test
    fun addAppendsAStableNewItemAndMakesItActive() {
        val result = BedStripContract.add(listOf(bed(7, "Kept"), bed(42, "Also kept")), activeIndex = 0)

        assertEquals(2, result.activeIndex)
        assertEquals(listOf(7, 42, 43), result.beds.map { it.id })
        assertEquals(listOf("Kept", "Also kept", "Bed 3"), result.beds.map { it.name })
        assertEquals(listOf(false, false, true), result.state.items.map { it.active })
    }

    @Test
    fun selectionLockAndRenamePreserveStableItemIdentityAndOrder() {
        val input = listOf(bed(7, "First"), bed(42, "Second", objects = 3, instances = 6))
        val selected = BedStripContract.select(input, activeIndex = 0, id = 42)
        val locked = BedStripContract.toggleLock(selected.beds, selected.activeIndex, id = 42)
        val renamed = BedStripContract.rename(locked.beds, locked.activeIndex, id = 42, name = "Production")

        assertEquals(1, renamed.activeIndex)
        assertEquals(listOf(7, 42), renamed.beds.map { it.id })
        assertEquals(listOf("First", "Production"), renamed.beds.map { it.name })
        assertEquals(listOf(false, true), renamed.beds.map { it.locked })
        assertEquals(listOf(false, true), renamed.state.items.map { it.active })
    }

    @Test
    fun removeDropsOnlyAnEmptyNonfinalBedAndNormalizesTheActiveIndex() {
        val removed = BedStripContract.remove(
            listOf(bed(7, objects = 2), bed(42), bed(99, objects = 1)),
            activeIndex = 2,
            id = 42,
        )

        requireNotNull(removed)
        assertEquals(listOf(7, 99), removed.beds.map { it.id })
        assertEquals(1, removed.activeIndex)
        assertEquals(listOf(false, true), removed.state.items.map { it.active })
        assertNull(BedStripContract.remove(listOf(bed(7, objects = 1), bed(42)), 0, 7))
    }

    @Test
    fun arrangeAvailabilityUsesTheActiveBedLockObjectAndInstanceCounts() {
        assertEquals(ArrangeAvailability.LOCKED, BedStripContract.state(listOf(bed(7, locked = true, objects = 3, instances = 3)), 0).arrange)
        assertEquals(ArrangeAvailability.EMPTY, BedStripContract.state(listOf(bed(7, objects = 0, instances = 4)), 0).arrange)
        assertEquals(ArrangeAvailability.EMPTY, BedStripContract.state(listOf(bed(7, objects = 2, instances = 0)), 0).arrange)
        assertEquals(ArrangeAvailability.AVAILABLE, BedStripContract.state(listOf(bed(7, objects = 2, instances = 3)), 0).arrange)
        assertEquals(
            ArrangeAvailability.AVAILABLE,
            BedStripContract.state(listOf(bed(7, locked = true, objects = 1), bed(42, objects = 1)), 1).arrange,
        )
    }
}
