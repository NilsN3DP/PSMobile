package de.psmobile.ui

import de.psmobile.shared.rules.ArrangeAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedStripAdapterTest {
    private fun bed(
        id: Int,
        name: String = "",
        locked: Boolean = false,
        objects: Int = 0,
        instances: Int = 0,
        active: Boolean = false,
    ) = AndroidBedSnapshot(id, name, locked, objects, instances, active)

    @Test fun `core metadata and true instance totals drive the strip`() {
        val state = AndroidBedStripAdapter.state(listOf(
            bed(0, "Kundenplatte", locked = true, objects = 2, instances = 5, active = true),
            bed(1, objects = 1, instances = 0),
        ), "Bett")

        assertEquals("Kundenplatte", state.items[0].name)
        assertTrue(state.items[0].locked)
        assertEquals(5, state.items[0].instanceCount)
        assertFalse(state.items[1].canRemove)
        assertEquals(ArrangeAvailability.LOCKED, state.arrange)
    }

    @Test fun `empty locked and populated arrange decisions are visible or executable`() {
        assertEquals("Bett 1 ist gesperrt – Anordnen nicht möglich",
            AndroidBedStripAdapter.arrange(listOf(bed(0, locked = true, active = true)), "Bett").message)
        assertEquals("Bett 1 ist leer. Es gibt nichts anzuordnen.",
            AndroidBedStripAdapter.arrange(listOf(bed(0, active = true)), "Bett").message)
        assertTrue(AndroidBedStripAdapter.arrange(
            listOf(bed(0, objects = 1, instances = 2, active = true)), "Bett").execute)
    }

    @Test fun `same core snapshot preserves selection across simple and advanced modes`() {
        val beds = listOf(bed(0), bed(1, name = "Prototyp", active = true))
        val simple = AndroidBedStripAdapter.state(beds, "Bett")
        val advanced = AndroidBedStripAdapter.state(beds, "Bett")
        assertEquals(1, simple.activeIndex)
        assertEquals(simple.activeIndex, advanced.activeIndex)
        assertEquals("Prototyp", advanced.items[1].name)
    }

    @Test fun `production actions roundtrip add select rename lock and removal rules`() {
        val port = FakeBedPort(mutableListOf(bed(0, active = true)))
        val messages = mutableListOf<String>()
        val actions = AndroidBedStripActions(port, "Bett", messages::add)

        actions.add()
        assertEquals(2, port.value.size)
        actions.select(0)
        actions.rename(0, "Serie")
        actions.toggleLock(0)
        assertEquals("Serie", port.value[0].name)
        assertTrue(port.value[0].locked)
        assertFalse(actions.remove(0))
        actions.toggleLock(0)
        assertTrue(actions.remove(0))
        assertEquals(1, port.value.size)
        assertFalse(actions.remove(1))
    }

    private class FakeBedPort(val value: MutableList<AndroidBedSnapshot>) : AndroidBedPort {
        override fun beds() = value.toList()
        override fun add() {
            value.replaceAll { it.copy(active = false) }
            value += AndroidBedSnapshot(value.size, "", false, 0, 0, true)
        }
        override fun select(index: Int) { value.replaceAll { it.copy(active = it.index == index) } }
        override fun setMetadata(index: Int, name: String, locked: Boolean) {
            value.replaceAll { if (it.index == index) it.copy(name = name, locked = locked) else it }
        }
        override fun remove(index: Int) { value.removeAll { it.index == index } }
        override fun arrange() = Unit
    }
}
