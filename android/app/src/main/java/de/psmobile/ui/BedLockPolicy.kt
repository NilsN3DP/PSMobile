package de.psmobile.ui

object BedLockPolicy {
    fun toggle(locked: Set<Int>, index: Int): Set<Int> = locked.toMutableSet().apply {
        if (!add(index)) remove(index)
    }
    fun allows(locked: Set<Int>, index: Int): Boolean = index !in locked
}
