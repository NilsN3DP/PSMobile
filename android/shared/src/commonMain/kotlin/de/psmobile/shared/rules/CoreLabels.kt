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
        "SVG prägen" to "Emboss SVG",
        "STL reparieren" to "Repair STL",
        "Skalieren" to "Scale",
        "Slice-Start" to "Slice start",
        "Spiegeln" to "Mirror",
        "Text prägen" to "Emboss text",
        "Verschieben" to "Move",
        "Wiederholen" to "Redo",
        "Zurueck" to "Back",
        "Zuruecksetzen" to "Reset",

        // Die Bezeichnungen der Rueckgaengig-Schritte. Der Kern
        // setzt sie in `history_checkpoint(...)`, und sie stehen
        // unter dem Zurueck-Pfeil im Simple Mode und in der
        // Werkzeugleiste des Expertenmodus. Bis zum 11.09.2026
        // standen sie dort deutsch in einer englischen
        // Oberflaeche - auf beiden Plattformen, denn beide zeigen
        // denselben Kerntext.
        "Auf Flaeche legen" to "Place on face",
        "Bemalung loeschen" to "Clear painting",
        "Custom G-Code aendern" to "Edit custom G-code",
        "Custom G-Code entfernen" to "Remove custom G-code",
        "Custom G-Code hinzufuegen" to "Add custom G-code",
        "Druckbett entfernen" to "Remove print bed",
        "Druckbett hinzufügen" to "Add print bed",
        "Flaeche bemalen" to "Paint face",
        "In Objekte teilen" to "Split to objects",
        "In Volumen teilen" to "Split to parts",
        "Kopien ändern" to "Change copies",
        "Mesh vereinfachen" to "Simplify mesh",
        "Neues Projekt" to "New project",
        "Objekt auf anderes Bett verschieben" to "Move object to another bed",
        "Objekt aufs Bett einpassen" to "Fit object to bed",
        "Objekt aufs Bett legen" to "Drop object to bed",
        "Objekt drehen" to "Rotate object",
        "Objekt duplizieren" to "Duplicate object",
        "Objekt schneiden" to "Cut object",
        "Objekt skalieren" to "Scale object",
        "Objekt spiegeln" to "Mirror object",
        "Objekt verschieben" to "Move object",
        "Objekt-Einstellung zurücksetzen" to "Reset object setting",
        "Objekt-Einstellung ändern" to "Change object setting",
        "Objekt-Extruder ändern" to "Change object extruder",
        "Objekte anordnen" to "Arrange objects",
        "Objekte importieren" to "Import objects",
        "Objektfarbe" to "Object colour",
        "Profil auswaehlen" to "Select preset",
        "SVG praegen" to "Emboss SVG",
        "Text praegen" to "Emboss text",
        "Volumen entfernen" to "Remove part",
        "Volumen hinzufuegen" to "Add part",
        "Volumen-Extruder ändern" to "Change part extruder",
        "Wipe-Tower positionieren" to "Position wipe tower",
        "Wischoptionen" to "Wipe options",
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
