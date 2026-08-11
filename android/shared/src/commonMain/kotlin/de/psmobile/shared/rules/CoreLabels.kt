package de.psmobile.shared.rules

/**
 * Uebersetzung der Operationsnamen, die der Kern in seine Fehlermeldungen
 * einsetzt.
 *
 * Beide Plattformen bauen ihre Kernfehler nach demselben Muster zusammen -
 * "<Operation> fehlgeschlagen (<Code>): <Detail>" (PsmCore.check auf
 * Android, PsmError.errorDescription auf iOS). Der Operationsname stand
 * dabei an rund 120 Aufrufstellen fest auf Deutsch, und diese Meldungen
 * landen ungefiltert in der Oberflaeche: im Fortschrittsbanner, im
 * Werkzeug-Hinweis und im Import-Dialog.
 *
 * Statt 120 Aufrufstellen anzufassen, uebersetzt diese Tabelle an der
 * einen Stelle, an der die Meldung entsteht. Der deutsche Name bleibt
 * damit der Schluessel - er wird nirgends gespeichert oder verglichen,
 * sondern nur formatiert.
 *
 * Unbekannte Namen kommen unveraendert durch. Das ist Absicht: eine neue
 * Operation soll keine Ausnahme ausloesen, sondern hoechstens
 * unuebersetzt erscheinen, bis sie hier nachgetragen wird.
 */
object CoreLabels {

    private val englisch: Map<String, String> = mapOf(
        "Adaptives Schichtprofil berechnen" to "Compute adaptive layer profile",
        "Adaptives Schichtprofil lesen" to "Read adaptive layer profile",
        "Anordnen" to "Arrange",
        "Auf Groesse bringen" to "Scale to size",
        "Auf das Bett bringen" to "Move onto the bed",
        "Aufs Bett legen" to "Drop to bed",
        "Bett anlegen" to "Add bed",
        "Bett leeren" to "Clear bed",
        "ColorMix lesen" to "Read ColorMix",
        "ColorMix speichern" to "Save ColorMix",
        "Custom G-Code leeren" to "Clear custom G-code",
        "Custom G-code hinzufuegen" to "Add custom G-code",
        "Custom G-code leeren" to "Clear custom G-code",
        "Drehen" to "Rotate",
        "Druckbett leeren" to "Clear print bed",
        "Drucker einrichten" to "Set up printer",
        "Druckermodelle suchen" to "Scan printer models",
        "Einstellung setzen" to "Set option",
        "Extruder setzen" to "Set extruder",
        "Flach hinlegen" to "Lay flat",
        "G-Code fuer Vorschau laden" to "Load G-code for preview",
        "G-Code wandeln" to "Convert G-code",
        "G-Code-Export" to "G-code export",
        "Hersteller waehlen" to "Choose vendor",
        "Historie beenden" to "End history",
        "Historie leeren" to "Clear history",
        "In Volumen teilen" to "Split into volumes",
        "Klonen" to "Clone",
        "Kopien setzen" to "Set copies",
        "Modell laden" to "Load model",
        "Modell vereinfachen" to "Simplify model",
        "Objekt entfernen" to "Remove object",
        "Platte als STL" to "Plate as STL",
        "Profile laden" to "Load profiles",
        "Projekt oeffnen" to "Open project",
        "Projekt sichern" to "Save project",
        "Reinigungsturm setzen" to "Set wipe tower",
        "Remote-G-Code fuer Vorschau annehmen" to "Accept remote G-code for preview",
        "Rückgängig" to "Undo",
        "STL reparieren" to "Repair STL",
        "Skalieren" to "Scale",
        "Slice-Start" to "Slice start",
        "Spiegeln" to "Mirror",
        "Verschieben" to "Move",
        "Wiederholen" to "Redo",
        "Zurueck" to "Back",
        "Zuruecksetzen" to "Reset",
    )

    /** Der Operationsname in der eingestellten Sprache. */
    fun label(german: String): String =
        if (Lang.isGerman) german else (englisch[german] ?: german)

    /** Die vollstaendige Fehlerzeile, wie sie der Nutzer sieht. */
    fun failed(german: String, code: Int, detail: String): String {
        val was = label(german)
        return if (Lang.isGerman) "$was fehlgeschlagen ($code): $detail"
        else "$was failed ($code): $detail"
    }
}
