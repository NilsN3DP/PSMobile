package de.psmobile.shared.rules

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

    /*
     * Ausgewaehlte Kennungen stehen als Liste, nicht als Menge: Kotlins
     * Set<Int> kommt in Swift als Set<KotlinInt> an, und damit laesst
     * sich dort nicht arbeiten. Die Reihenfolge spielt keine Rolle,
     * doppelte Eintraege entstehen durch toggle() nicht.
     */


    enum class Action { ARRANGE, MOVE_TO_BED, CLONE, REMOVE, SELECT_ALL, ADD_MORE }

    fun toggle(selected: List<Int>, id: Int): List<Int> =
        if (id in selected) selected - id else selected + id

    /** Nach dem Loeschen oder Bettwechsel duerfen keine Geister bleiben. */
    fun pruned(selected: List<Int>, existing: List<Int>): List<Int> =
        selected.filter { it in existing }

    fun selectAll(existing: List<Int>): List<Int> = existing.distinct()

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
        selected: List<Int>,
        bedCount: Int,
        maxBeds: Int,
    ): List<Action> {
        val actions = mutableListOf<Action>()
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
    fun headline(selected: List<Int>): String =
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
