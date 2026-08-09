package de.psmobile.shared.rules

/**
 * Einstellungen der App selbst - nicht des Drucks.
 *
 * Bisher lagen sie verstreut: die Sprache als Klappmenue auf dem
 * Startbildschirm, "unpassende Materialien zeigen" als Haken mitten in
 * der Materialwahl, der Startmodus gar nicht einstellbar. Wer etwas
 * davon aendern wollte, musste wissen, an welcher Stelle im Ablauf es
 * versteckt ist.
 *
 * Hier stehen sie an einem Ort, mit Standardwerten und Begruendungen.
 * Die Werte selbst liegen in denselben SharedPreferences wie bisher -
 * bestehende Einstellungen bleiben also erhalten.
 */
object AppSettings {

    enum class Group { APPEARANCE, PERFORMANCE, PROFILES, WORK, PLUGINS }

    /**
     * Ein Schalter.
     *
     * @param key      Schluessel in den SharedPreferences
     * @param standard Wert, solange nichts gesetzt wurde
     * @param why      warum es ihn gibt - erscheint unter dem Schalter.
     *                 Eine Einstellung ohne Begruendung zwingt zum
     *                 Ausprobieren.
     */
    data class Toggle(
        val key: String,
        val group: Group,
        val title: Bilingual,
        val why: Bilingual,
        val standard: Boolean,
    )

    /** Modellvorschau in Listen - der Leistungsschalter. */
    const val KEY_THUMBNAILS = "ui.thumbnails"

    /** Auch Profile zeigen, die zum Drucker nicht passen. */
    const val KEY_SHOW_INCOMPATIBLE = "presets.show-incompatible"

    /** Arbeitsstand beim Verlassen sichern und beim Start zurueckholen. */
    const val KEY_AUTOSAVE = "work.autosave"

    /** Beim Start immer denselben Modus oeffnen statt zu fragen. */
    const val KEY_START_MODE = "ui.start-mode"

    /** Alle Betten raeumlich versetzt im Viewport zeigen statt nur das aktive. */
    const val KEY_MULTI_BED_RENDER = "viewport.multi-bed-render"

    /** Laengenfelder (mm-Einheit) in Zoll anzeigen und eingeben - der
     *  Kern rechnet immer in mm, das ist reine Anzeige/Eingabe-
     *  Umrechnung in SettingField. */
    const val KEY_UNITS_IMPERIAL = "ui.units-imperial"

    /** Im Hochformat die Werkzeugleiste unten statt rechts anlegen. */
    const val KEY_PORTRAIT_BOTTOM_BAR = "experimental.portrait-bottom-bar"

    /** Remote Slicing ueberhaupt anbieten - Umschalter am Slice-Knopf,
     *  Eintrag auf der Startseite. Aus macht die Oberflaeche wieder so
     *  aufgeraeumt wie ohne das Plugin. */
    const val KEY_PLUGIN_REMOTE_SLICE = "plugins.remote-slice"

    /** Nach jedem Slice automatisch einen Testbericht (Geraet, RAM,
     *  Dreieckszahl, Slice-Zeit, Screenshot) an den eingerichteten
     *  Slice-Server schicken - fuer Testversionen, nicht fuer die
     *  allgemeine Nutzung gedacht. */
    const val KEY_DIAG_AUTO_UPLOAD = "diagnostics.auto-upload"

    /** Anonyme Nutzungsdaten (Geraet, RAM, Dreieckszahl, Slice-Zeit -
     *  ausdruecklich ohne Screenshot und ohne Projektnamen) teilen. */
    const val KEY_TELEMETRY_ANON = "diagnostics.telemetry-anonymous"

    /** Werte fuer KEY_START_MODE. */
    const val START_ASK = "ask"
    const val START_SIMPLE = "simple"
    const val START_ADVANCED = "advanced"

    val toggles: List<Toggle> = listOf(
        Toggle(
            key = KEY_UNITS_IMPERIAL,
            group = Group.APPEARANCE,
            title = Bilingual(
                "Show lengths in inches",
                "Längen in Zoll anzeigen",
            ),
            why = Bilingual(
                "Only display and entry change - the core still slices in millimetres.",
                "Nur Anzeige und Eingabe ändern sich - geschnitten wird weiterhin in Millimetern.",
            ),
            standard = false,
        ),
        Toggle(
            key = KEY_THUMBNAILS,
            group = Group.PERFORMANCE,
            title = Bilingual("Model previews in lists", "Modellvorschau in Listen"),
            why = Bilingual(
                "Costs a little time per object. Turn it off if long lists feel sluggish.",
                "Kostet je Objekt etwas Rechenzeit. Bei langen Listen abschaltbar.",
            ),
            standard = true,
        ),
        Toggle(
            key = KEY_SHOW_INCOMPATIBLE,
            group = Group.PROFILES,
            title = Bilingual(
                "Show materials for other printers",
                "Materialien anderer Drucker zeigen",
            ),
            why = Bilingual(
                "Unsuitable ones stay marked. Off by default because the list is long.",
                "Unpassende bleiben gekennzeichnet. Standardmäßig aus, weil die Liste lang ist.",
            ),
            standard = false,
        ),
        Toggle(
            key = KEY_AUTOSAVE,
            group = Group.WORK,
            title = Bilingual(
                "Keep work when leaving the app",
                "Arbeitsstand beim Verlassen behalten",
            ),
            why = Bilingual(
                "The system may end the app in the background without warning.",
                "Das System beendet die App im Hintergrund ohne Vorwarnung.",
            ),
            standard = true,
        ),
        Toggle(
            key = KEY_MULTI_BED_RENDER,
            group = Group.PERFORMANCE,
            title = Bilingual(
                "Show all beds at once in the viewport",
                "Alle Betten gleichzeitig im Viewport zeigen",
            ),
            why = Bilingual(
                "Shows the name of every plate, like other slicers do. Turn off on weaker devices if it feels sluggish.",
                "Zeigt den Namen jeder Druckplatte, wie andere Slicer es tun. Bei schwächeren Geräten bei Bedarf abschaltbar.",
            ),
            standard = true,
        ),
        Toggle(
            key = KEY_PLUGIN_REMOTE_SLICE,
            group = Group.PLUGINS,
            title = Bilingual(
                "Remote Slicing",
                "Remote Slicing",
            ),
            why = Bilingual(
                "Slice on your own server instead of this device. Adds a switch next to Slice now.",
                "Auf dem eigenen Server statt auf diesem Gerät slicen. Fügt einen Schalter neben Slice now hinzu.",
            ),
            standard = true,
        ),
        Toggle(
            key = KEY_PORTRAIT_BOTTOM_BAR,
            group = Group.PLUGINS,
            title = Bilingual(
                "Tool panel at the bottom in portrait",
                "Werkzeugleiste im Hochformat unten",
            ),
            why = Bilingual(
                "Trades a narrow bed for a docked bottom panel. Still rough - try it and tell us what breaks.",
                "Tauscht ein schmales Bett gegen eine angedockte Leiste unten. Noch nicht ausgereift - ausprobieren und Rückmeldung geben.",
            ),
            standard = false,
        ),
        Toggle(
            key = KEY_DIAG_AUTO_UPLOAD,
            group = Group.PLUGINS,
            title = Bilingual(
                "Automatic test report",
                "Automatischer Testbericht",
            ),
            why = Bilingual(
                "Sends device info, timing and a screenshot to the configured slice server after every slice - for testing, not everyday use.",
                "Schickt Geräteinfo, Zeiten und einen Screenshot nach jedem Slice an den eingerichteten Slice-Server - für Tests, nicht für den Alltag.",
            ),
            standard = false,
        ),
        Toggle(
            key = KEY_TELEMETRY_ANON,
            group = Group.PLUGINS,
            title = Bilingual(
                "Share anonymous usage data",
                "Anonyme Nutzungsdaten teilen",
            ),
            why = Bilingual(
                "Device, RAM, triangle count and slice time, without a screenshot or project name.",
                "Gerät, RAM, Dreieckszahl und Slice-Zeit, ohne Screenshot oder Projektnamen.",
            ),
            standard = false,
        ),
    )

    fun group(g: Group): List<Toggle> = toggles.filter { it.group == g }

    fun groupTitle(g: Group): String = when (g) {
        Group.APPEARANCE -> SimpleModeState.text("Appearance", "Darstellung")
        Group.PERFORMANCE -> SimpleModeState.text("Performance", "Leistung")
        Group.PROFILES -> SimpleModeState.text("Profiles", "Profile")
        Group.WORK -> SimpleModeState.text("Work in progress", "Arbeitsstand")
        Group.PLUGINS -> SimpleModeState.text("Plugins", "Plugins")
    }

    /** Die Gruppen in der Reihenfolge, in der sie angezeigt werden. */
    val groupsInOrder: List<Group> = listOf(
        Group.APPEARANCE, Group.PERFORMANCE, Group.PROFILES, Group.WORK, Group.PLUGINS,
    )

    fun startModeLabel(value: String): String = when (value) {
        START_SIMPLE -> SimpleModeState.text("Always Simple Mode", "Immer Simple Mode")
        START_ADVANCED -> SimpleModeState.text("Always Advanced Mode", "Immer Advanced Mode")
        else -> SimpleModeState.text("Ask every time", "Jedes Mal fragen")
    }

    val startModes: List<String> = listOf(START_ASK, START_SIMPLE, START_ADVANCED)
}
