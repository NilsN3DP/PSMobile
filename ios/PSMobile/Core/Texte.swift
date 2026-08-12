import Foundation
import PSMShared

/// Der Kurzweg zu einem uebersetzten Text.
///
/// Die Sprache haengt an `SimpleModeState` im gemeinsamen Modul, damit
/// beide Plattformen dieselbe Entscheidung treffen. Der Aufruf dorthin
/// ist aber lang, und er kommt in jeder Ansicht dutzendfach vor - also
/// stand in 29 Dateien derselbe private Zweizeiler, 36-mal insgesamt,
/// Zeichen fuer Zeichen gleich. Diese eine Fassung ersetzt sie alle.
///
/// Bewusst eine freie Funktion und keine Methode: sie soll aus jeder
/// Ansicht, jedem Modell und jedem Netzwerk-Client heraus erreichbar
/// sein, ohne dass die erst irgendwo erben oder etwas einbetten muss.
///
/// Auf Android heisst das Gegenstueck `SimpleModeState.text(...)` und
/// wird dort von `PsUi.appText` und den lokalen `t(...)` genutzt.
func st(_ english: String, _ german: String) -> String {
    SimpleModeState.shared.text(english: english, german: german)
}
