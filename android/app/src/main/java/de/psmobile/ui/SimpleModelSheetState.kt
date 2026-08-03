package de.psmobile.ui
import de.psmobile.shared.rules.SimpleModeState

/**
 * Zustand des Modelle-Blatts im Simple Mode, nach dem Vorbild von
 * EasyPrint.
 *
 * Das Blatt hat zwei Gesichter. Ohne Auswahl zeigt es die Liste mit den
 * Aktionen fuer alle - alles waehlen, weitere laden, anordnen. Sobald
 * mindestens ein Modell angehakt ist, wechselt die Kopfzeile auf
 * "N gewaehlt" und bietet die Aktionen fuer die Auswahl an: anordnen,
 * auf ein anderes Bett ziehen, klonen, entfernen.
 *
 * Die Logik steht hier getrennt von der Darstellung, damit sie ohne
 * Geraet pruefbar bleibt - besonders die Frage, welche Aktionen wann
 * ueberhaupt etwas bewirken koennen.
 */
object SimpleModelSheetState {

    enum class Action { ARRANGE, MOVE_TO_BED, CLONE, REMOVE, SELECT_ALL, ADD_MORE }

    fun toggle(selected: Set<Int>, id: Int): Set<Int> =
        if (id in selected) selected - id else selected + id

    /** Nach dem Loeschen oder Bettwechsel duerfen keine Geister bleiben. */
    fun pruned(selected: Set<Int>, existing: Collection<Int>): Set<Int> =
        selected.filter { it in existing }.toSet()

    fun selectAll(existing: Collection<Int>): Set<Int> = existing.toSet()

    /**
     * Welche Aktionen jetzt sinnvoll sind.
     *
     * Anordnen braucht mindestens zwei Objekte - bei einem einzigen gibt
     * es nichts anzuordnen, und ein Knopf, der sichtbar nichts tut, ist
     * schlimmer als keiner. Auf ein anderes Bett ziehen setzt voraus,
     * dass es ein anderes gibt oder angelegt werden darf.
     */
    fun enabledActions(
        objectsOnBed: Int,
        selected: Set<Int>,
        bedCount: Int,
        maxBeds: Int,
    ): Set<Action> {
        val actions = mutableSetOf<Action>()
        if (objectsOnBed >= 2) actions += Action.ARRANGE
        if (objectsOnBed >= 1) actions += Action.SELECT_ALL
        actions += Action.ADD_MORE
        if (selected.isNotEmpty()) {
            actions += Action.CLONE
            actions += Action.REMOVE
            if (bedCount > 1 || bedCount < maxBeds) actions += Action.MOVE_TO_BED
        }
        return actions
    }

    /** Kopfzeile: entweder der Titel oder die Zahl der Ausgewaehlten. */
    fun headline(selected: Set<Int>): String =
        if (selected.isEmpty()) SimpleModeState.text("MODELS", "MODELLE")
        else SimpleModeState.text(
            "${selected.size} SELECTED",
            "${selected.size} GEWÄHLT",
        )

    /** Zielbetten fuer "auf Bett ziehen", ohne das aktuelle. */
    fun moveTargets(bedCount: Int, activeBed: Int, maxBeds: Int): List<Int> {
        val existing = (0 until bedCount).filter { it != activeBed }
        return if (bedCount < maxBeds) existing + bedCount else existing
    }
}
