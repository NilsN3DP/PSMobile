package de.psmobile.shared.rules

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

    // Der Modelltyp bleibt offen. Auf Android ist es PsmCore.PrinterModel,
    // auf iOS wird es ein Swift-Gegenstueck sein - die Regel interessiert
    // sich nur fuer die Familie. Sie hier hereinzuziehen haette bedeutet,
    // eine innere Klasse aus der JNI-Bruecke herauszuloesen, ohne dass die
    // Regel dadurch besser wuerde.
    data class Group<T>(
        val family: String,
        val models: List<T>,
        val isLegacy: Boolean,
    )

    fun isLegacy(family: String): Boolean = family.contains("legacy", ignoreCase = true)

    fun <T> grouped(models: List<T>, family: (T) -> String): List<Group<T>> {
        // LinkedHashMap haelt die Reihenfolge des ersten Auftretens fest.
        val byFamily = LinkedHashMap<String, MutableList<T>>()
        for (m in models) {
            val f = family(m).ifBlank { OTHER }
            byFamily.getOrPut(f) { mutableListOf() }.add(m)
        }

        val groups = byFamily.map { (f, list) ->
            Group(f, list, isLegacy(f))
        }
        // sortedBy ist stabil - die Reihenfolge innerhalb der beiden
        // Bloecke bleibt also die aus der Vendor-Datei.
        return groups.sortedBy { if (it.isLegacy) 1 else 0 }
    }

    /** Familie fehlt in der Vendor-Datei - kommt vor, soll aber sichtbar sein. */
    const val OTHER = "Weitere"
}
