package de.psmobile.shared.rules

/**
 * Erkennt einen angeschlossenen Wechselspeicher, damit der G-Code direkt
 * dorthin geschrieben werden kann - so wie man am PC auf die SD-Karte
 * exportiert.
 *
 * Android laesst kein direktes Schreiben auf ein OTG-Volume zu. Der Weg
 * fuehrt immer ueber den Dokumentenanbieter; was hier gewonnen wird, ist
 * dass der Speichern-Dialog gleich **auf dem Stick** aufgeht, statt im
 * zuletzt benutzten Ordner. Bei einem Drucker, der vom Stick liest, ist
 * das der Unterschied zwischen zwei Tipps und einer Suche durch den
 * Dateibaum.
 *
 * Die Entscheidung, welches Volume gemeint ist, steht hier getrennt von
 * der Android-Abfrage - dadurch ist sie ohne Geraet pruefbar.
 */
object RemovableStorage {

    /**
     * Ein Speicherort, wie ihn der StorageManager meldet.
     *
     * @param description Anzeigename, etwa "USB-Laufwerk" oder "SD-Karte"
     * @param isRemovable ob er entnommen werden kann
     * @param isPrimary   der eingebaute Hauptspeicher
     * @param isMounted   eingehaengt und beschreibbar
     */
    data class Volume(
        val description: String,
        val isRemovable: Boolean,
        val isPrimary: Boolean,
        val isMounted: Boolean,
    )

    /**
     * Der Speicherort, auf den exportiert werden soll - oder null, wenn
     * keiner angeschlossen ist. Dann bleibt der Knopf weg, statt einen
     * Speicherort anzubieten, den es nicht gibt.
     *
     * Der eingebaute Speicher wird ausdruecklich ausgeschlossen, auch
     * wenn Android ihn bei manchen Geraeten als entnehmbar meldet: dafuer
     * gibt es den gewoehnlichen Speichern-Weg.
     */
    fun target(volumes: List<Volume>): Volume? =
        volumes.firstOrNull { it.isRemovable && !it.isPrimary && it.isMounted }

    /** Beschriftung des Knopfes, mit dem Namen des Speicherorts. */
    fun label(volume: Volume): String {
        val name = volume.description.trim().ifBlank {
            SimpleModeState.text("USB drive", "USB-Laufwerk")
        }
        return SimpleModeState.text("Save to $name", "Auf $name speichern")
    }
}
