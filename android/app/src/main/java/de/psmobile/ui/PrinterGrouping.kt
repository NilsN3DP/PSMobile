package de.psmobile.ui

import de.psmobile.core.PsmCore

/**
 * Ordnet die Druckerliste der Ersteinrichtung nach Familien.
 *
 * Bisher war es eine flache Liste von rund vierzig Eintraegen, in der
 * die Familie nur klein als Untertitel stand. Wer seinen Drucker sucht,
 * liest dann vierzig Zeilen. Der Desktop gruppiert stattdessen, und
 * genau das passiert hier.
 *
 * Die Reihenfolge der Gruppen wird **nicht** erfunden: sie folgt der
 * Reihenfolge, in der die Familien in PrusaSlicers eigener Vendor-Datei
 * zuerst auftauchen. Das ist dieselbe Haltung wie bei den
 * Einstellungsseiten - uebernommene Information statt eigener Meinung.
 *
 * Einzige Ausnahme sind die Altgeraete. PrusaSlicer fuehrt sie unter
 * einer Familie, deren Name "Legacy" enthaelt, und die gehoert ans Ende:
 * wer heute einen Drucker einrichtet, meint fast nie ein Altprofil, und
 * oben stehen sie nur im Weg.
 */
object PrinterGrouping {

    data class Group(
        val family: String,
        val models: List<PsmCore.PrinterModel>,
        val isLegacy: Boolean,
    )

    fun isLegacy(family: String): Boolean = family.contains("legacy", ignoreCase = true)

    fun grouped(models: List<PsmCore.PrinterModel>): List<Group> {
        // LinkedHashMap haelt die Reihenfolge des ersten Auftretens fest.
        val byFamily = LinkedHashMap<String, MutableList<PsmCore.PrinterModel>>()
        for (m in models) {
            val family = m.family.ifBlank { OTHER }
            byFamily.getOrPut(family) { mutableListOf() }.add(m)
        }

        val groups = byFamily.map { (family, list) ->
            Group(family, list, isLegacy(family))
        }
        // sortedBy ist stabil - die Reihenfolge innerhalb der beiden
        // Bloecke bleibt also die aus der Vendor-Datei.
        return groups.sortedBy { if (it.isLegacy) 1 else 0 }
    }

    /** Familie fehlt in der Vendor-Datei - kommt vor, soll aber sichtbar sein. */
    const val OTHER = "Weitere"
}
