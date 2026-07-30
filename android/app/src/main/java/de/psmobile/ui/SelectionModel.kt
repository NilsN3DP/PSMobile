package de.psmobile.ui

/**
 * Reiner Auswahlzustand, damit Suche, Mehrfachauswahl und asynchrone
 * Objektmutationen dieselben Regeln verwenden.
 */
internal data class SelectionModel(
    val ids: LinkedHashSet<Int> = linkedSetOf(),
    val primaryId: Int? = null,
) {
    fun reconcile(validIds: Collection<Int>): SelectionModel {
        val valid = validIds.toHashSet()
        val kept = ids.filterTo(linkedSetOf()) { it in valid }
        return SelectionModel(
            kept,
            primaryId?.takeIf { it in kept } ?: kept.firstOrNull(),
        )
    }

    fun toggle(id: Int): SelectionModel {
        val next = LinkedHashSet(ids)
        if (!next.add(id)) next.remove(id)
        return SelectionModel(
            next,
            if (id in next) id else primaryId?.takeIf { it in next }
                ?: next.firstOrNull(),
        )
    }

    companion object {
        fun single(id: Int?) =
            SelectionModel(id?.let { linkedSetOf(it) } ?: linkedSetOf(), id)

        fun all(ids: Collection<Int>): SelectionModel {
            val selected = ids.toCollection(linkedSetOf())
            return SelectionModel(selected, selected.firstOrNull())
        }
    }
}
