package de.psmobile.shared.rules

/**
 * Platform-neutral presentation rules for the active print-bed strip.
 *
 * The core remains the source of truth for actual mutations. These values make
 * the choices shown by Android Compose and SwiftUI deterministic and identical.
 */
data class BedInput(
    val id: Int,
    val name: String,
    val locked: Boolean,
    val objectCount: Int,
    val instanceCount: Int,
)

data class BedStripItem(
    val id: Int,
    val name: String,
    val active: Boolean,
    val locked: Boolean,
    val objectCount: Int,
    val instanceCount: Int,
    val canSelect: Boolean,
    val canToggleLock: Boolean,
    val canRename: Boolean,
    val canRemove: Boolean,
)

enum class ArrangeAvailability { AVAILABLE, EMPTY, LOCKED }

data class BedStripState(
    val items: List<BedStripItem>,
    val activeIndex: Int,
    val canAdd: Boolean,
    val arrange: ArrangeAvailability,
)

data class BedStripMutation(
    val beds: List<BedInput>,
    val activeIndex: Int,
    val state: BedStripState,
)

object BedStripContract {
    fun state(beds: List<BedInput>, activeIndex: Int): BedStripState {
        val normalizedBeds = normalizeBeds(beds)
        val normalizedActive = normalizeActiveIndex(normalizedBeds.size, activeIndex)
        val active = normalizedBeds[normalizedActive]
        return BedStripState(
            items = normalizedBeds.mapIndexed { index, bed ->
                BedStripItem(
                    id = bed.id,
                    name = bed.name,
                    active = index == normalizedActive,
                    locked = bed.locked,
                    objectCount = bed.objectCount,
                    instanceCount = bed.instanceCount,
                    canSelect = true,
                    canToggleLock = true,
                    canRename = true,
                    canRemove = normalizedBeds.size > 1 &&
                        bed.objectCount == 0 && bed.instanceCount == 0,
                )
            },
            activeIndex = normalizedActive,
            canAdd = true,
            arrange = when {
                active.locked -> ArrangeAvailability.LOCKED
                active.objectCount <= 0 || active.instanceCount <= 0 -> ArrangeAvailability.EMPTY
                else -> ArrangeAvailability.AVAILABLE
            },
        )
    }

    fun normalizeActiveIndex(bedCount: Int, activeIndex: Int): Int =
        if (bedCount <= 0) 0 else activeIndex.coerceIn(0, bedCount - 1)

    fun add(beds: List<BedInput>, activeIndex: Int): BedStripMutation {
        val current = normalizeBeds(beds)
        val nextId = (current.maxOfOrNull { it.id } ?: -1) + 1
        val next = current + BedInput(nextId, "Bed ${current.size + 1}", false, 0, 0)
        return mutation(next, next.lastIndex)
    }

    fun select(beds: List<BedInput>, activeIndex: Int, id: Int): BedStripMutation {
        val current = normalizeBeds(beds)
        val selected = current.indexOfFirst { it.id == id }
        return mutation(current, if (selected >= 0) selected else normalizeActiveIndex(current.size, activeIndex))
    }

    fun toggleLock(beds: List<BedInput>, activeIndex: Int, id: Int): BedStripMutation =
        mutate(beds, activeIndex, id) { it.copy(locked = !it.locked) }

    fun rename(beds: List<BedInput>, activeIndex: Int, id: Int, name: String): BedStripMutation =
        mutate(beds, activeIndex, id) { it.copy(name = name) }

    /** Returns null when this operation is not permitted by the strip contract. */
    fun remove(beds: List<BedInput>, activeIndex: Int, id: Int): BedStripMutation? {
        val current = normalizeBeds(beds)
        val removedIndex = current.indexOfFirst { it.id == id }
        if (removedIndex < 0 || current.size == 1 ||
            current[removedIndex].objectCount != 0 ||
            current[removedIndex].instanceCount != 0
        ) return null
        val next = current.filterIndexed { index, _ -> index != removedIndex }
        val nextActive = when {
            removedIndex < activeIndex -> activeIndex - 1
            removedIndex == activeIndex -> activeIndex.coerceAtMost(next.lastIndex)
            else -> activeIndex
        }
        return mutation(next, nextActive)
    }

    private fun mutate(
        beds: List<BedInput>,
        activeIndex: Int,
        id: Int,
        change: (BedInput) -> BedInput,
    ): BedStripMutation {
        val current = normalizeBeds(beds)
        return mutation(current.map { if (it.id == id) change(it) else it }, activeIndex)
    }

    private fun mutation(beds: List<BedInput>, activeIndex: Int): BedStripMutation {
        val normalized = normalizeBeds(beds)
        val normalizedActive = normalizeActiveIndex(normalized.size, activeIndex)
        return BedStripMutation(normalized, normalizedActive, state(normalized, normalizedActive))
    }

    private fun normalizeBeds(beds: List<BedInput>): List<BedInput> =
        if (beds.isEmpty()) listOf(BedInput(0, "Bed 1", false, 0, 0)) else beds
}
