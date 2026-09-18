package de.psmobile.ui

import de.psmobile.shared.rules.ArrangeAvailability
import de.psmobile.shared.rules.BedInput
import de.psmobile.shared.rules.BedStripContract
import de.psmobile.shared.rules.BedStripItem
import de.psmobile.shared.rules.BedStripState
import de.psmobile.shared.rules.SimpleModeState

data class AndroidBedSnapshot(
    val index: Int,
    val name: String,
    val locked: Boolean,
    val objectCount: Int,
    val instanceCount: Int,
    val active: Boolean,
)

data class ArrangeDecision(val execute: Boolean, val message: String? = null)

data class BedCapsulePresentation(
    val objectCountLabel: String?,
    val showLock: Boolean,
) {
    companion object {
        fun from(item: BedStripItem) = BedCapsulePresentation(
            objectCountLabel = item.objectCount.takeIf { it > 0 }?.toString(),
            showLock = item.locked,
        )
    }
}

object AndroidBedStripAdapter {
    fun state(beds: List<AndroidBedSnapshot>, fallback: String): BedStripState =
        BedStripContract.state(
            beds.map { bed ->
                BedInput(
                    bed.index,
                    bed.name.trim().ifEmpty { "$fallback ${bed.index + 1}" },
                    bed.locked,
                    bed.objectCount,
                    bed.instanceCount,
                )
            },
            beds.indexOfFirst { it.active },
        )

    /**
     * Verfuegbarkeit fuer ein beliebiges Zielbett, nicht nur das aktive.
     *
     * Der Vertrag beantwortet die Frage immer fuer das aktive Bett -
     * fuer ein anderes Ziel fragt man ihn deshalb mit diesem Bett als
     * aktivem. Genauso auf iOS (`BedSelector.swift`,
     * `arrangeAvailability(for:)`), damit die Antwort nicht zweimal
     * entsteht.
     */
    fun arrangeFuer(
        beds: List<AndroidBedSnapshot>,
        fallback: String,
        index: Int,
    ): ArrangeAvailability {
        val gedreht = beds.map { it.copy(active = it.index == index) }
        return state(gedreht, fallback).arrange
    }

    fun arrange(beds: List<AndroidBedSnapshot>, fallback: String): ArrangeDecision {
        val state = state(beds, fallback)
        val name = state.items[state.activeIndex].name
        return when (state.arrange) {
            ArrangeAvailability.AVAILABLE -> ArrangeDecision(true)
            ArrangeAvailability.LOCKED -> ArrangeDecision(false, SimpleModeState.text("$name is locked – arranging not possible", "$name ist gesperrt – Anordnen nicht möglich"))
            ArrangeAvailability.EMPTY -> ArrangeDecision(false, SimpleModeState.text("$name is empty. There is nothing to arrange.", "$name ist leer. Es gibt nichts anzuordnen."))
        }
    }
}

interface AndroidBedPort {
    fun beds(): List<AndroidBedSnapshot>
    fun add()
    fun select(index: Int)
    fun setMetadata(index: Int, name: String, locked: Boolean)
    fun remove(index: Int)
    fun arrange()
}

/** Real action boundary shared by Simple and Advanced; Core remains the port. */
class AndroidBedStripActions(
    private val port: AndroidBedPort,
    private val fallback: String,
    private val showMessage: (String) -> Unit,
) {
    fun add() = port.add()
    fun select(index: Int) = port.select(index)

    fun rename(index: Int, name: String) {
        val bed = port.beds().firstOrNull { it.index == index } ?: return
        port.setMetadata(index, name.trim(), bed.locked)
    }

    fun toggleLock(index: Int) {
        val bed = port.beds().firstOrNull { it.index == index } ?: return
        port.setMetadata(index, bed.name, !bed.locked)
    }

    fun remove(index: Int): Boolean {
        val state = AndroidBedStripAdapter.state(port.beds(), fallback)
        val item = state.items.firstOrNull { it.id == index } ?: return false
        if (item.locked) {
            showMessage(SimpleModeState.text("${item.name} is locked – removing not possible", "${item.name} ist gesperrt – Entfernen nicht möglich"))
            return false
        }
        if (!item.canRemove) {
            showMessage(if (state.items.size == 1)
                SimpleModeState.text("The only print bed cannot be removed.", "Das einzige Druckbett kann nicht entfernt werden.")
            else SimpleModeState.text("${item.name} is not empty and cannot be removed.", "${item.name} ist nicht leer und kann nicht entfernt werden."))
            return false
        }
        port.remove(index)
        return true
    }

    fun arrange(): Boolean {
        val decision = AndroidBedStripAdapter.arrange(port.beds(), fallback)
        decision.message?.let(showMessage)
        if (decision.execute) port.arrange()
        return decision.execute
    }
}
