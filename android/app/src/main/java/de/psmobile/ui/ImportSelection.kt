package de.psmobile.ui
import de.psmobile.shared.rules.SimpleModeState

/**
 * Regeln fuer eine Dateiauswahl mit mehreren Eintraegen.
 *
 * Der Grenzfall ist die 3MF: einzeln gewaehlt fragt die App, ob sie als
 * Objekte oder als vollstaendiges Projekt hereinkommen soll. In einer
 * Sammelauswahl darf diese Frage nicht mehr gestellt werden, denn ein
 * Projekt ersetzt das Bett und wuerde die uebrigen Dateien derselben
 * Auswahl unbemerkt wieder wegwerfen. Der Nutzer muss erfahren, dass das
 * passiert ist - sonst wundert er sich, warum eine 3MF diesmal keine
 * Druckeinstellungen mitgebracht hat.
 */
object ImportSelection {

    /** Nur bei genau einer Datei bleibt die Projektrueckfrage sinnvoll. */
    fun askAboutProject(selectionSize: Int): Boolean = selectionSize == 1

    fun summary(loaded: Int, total: Int, projectsAsObjects: Int): String = buildString {
        append(
            SimpleModeState.text(
                "$loaded of $total files were loaded.",
                "$loaded von $total Dateien wurden geladen.",
            )
        )
        if (projectsAsObjects > 0) {
            append(" ")
            append(
                SimpleModeState.text(
                    "$projectsAsObjects of them are 3MF files brought in as objects only, " +
                        "because a project replaces the bed and would have displaced the " +
                        "other files. Select a 3MF on its own to open it as a project.",
                    "Davon $projectsAsObjects 3MF-Datei${if (projectsAsObjects == 1) "" else "en"} " +
                        "nur als Objekte, weil ein Projekt das Bett ersetzt und die übrigen " +
                        "Dateien verdrängt hätte. Zum Öffnen als Projekt die 3MF einzeln wählen.",
                )
            )
        }
    }
}
