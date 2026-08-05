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
 *
 * **Warum Positionen statt Modelle:** Die Regel bekommt nur die Familien
 * und gibt zurueck, welche Positionen in welche Gruppe gehoeren. Das
 * Modell selbst bleibt beim Aufrufer - auf Android eine innere Klasse
 * der JNI-Bruecke, auf iOS ein Swift-Typ. Ein generischer Zuschnitt
 * waere naheliegender gewesen, aber Kotlins Typparameter ueberleben die
 * Bruecke nach Swift nicht: dort kaeme nur ein untypisiertes Group an.
 */
object PrinterGrouping {

    data class Group(
        val family: String,
        val isLegacy: Boolean,
        /** Positionen in der uebergebenen Liste, in ihrer Reihenfolge. */
        val indices: List<Int>,
    )

    fun isLegacy(family: String): Boolean = family.contains("legacy", ignoreCase = true)

    /**
     * @param families Familie je Modell, in der Reihenfolge der Liste.
     */
    fun group(families: List<String>): List<Group> {
        // LinkedHashMap haelt die Reihenfolge des ersten Auftretens fest.
        val byFamily = LinkedHashMap<String, MutableList<Int>>()
        families.forEachIndexed { index, roh ->
            val family = roh.ifBlank { OTHER }
            byFamily.getOrPut(family) { mutableListOf() }.add(index)
        }

        val groups = byFamily.map { (family, indices) ->
            Group(family, isLegacy(family), indices)
        }
        // sortedBy ist stabil - die Reihenfolge innerhalb der beiden
        // Bloecke bleibt also die aus der Vendor-Datei.
        return groups.sortedBy { if (it.isLegacy) 1 else 0 }
    }

    /** Familie fehlt in der Vendor-Datei - kommt vor, soll aber sichtbar sein. */
    const val OTHER = "Weitere"

    /**
     * Der Hersteller aus dem Schluessel `Hersteller:Modell`.
     *
     * Nicht erfunden, sondern gelesen: PrusaSlicer fuehrt seine Modelle
     * so, und die Ersteinrichtung gruppiert oberste Ebene danach. Wer
     * einen Voron sucht, will sich nicht durch Prusa-Familien lesen.
     */
    fun vendorOf(key: String): String =
        key.substringBefore(':', UNKNOWN_VENDOR).ifBlank { UNKNOWN_VENDOR }

    const val UNKNOWN_VENDOR = "Weitere"

    /**
     * Die Hersteller in der Reihenfolge ihres ersten Auftretens, mit
     * den Positionen ihrer Modelle.
     */
    fun groupByVendor(keys: List<String>): List<Group> {
        val byVendor = LinkedHashMap<String, MutableList<Int>>()
        keys.forEachIndexed { index, key ->
            byVendor.getOrPut(vendorOf(key)) { mutableListOf() }.add(index)
        }
        return byVendor.map { (vendor, indices) -> Group(vendor, false, indices) }
    }
}
