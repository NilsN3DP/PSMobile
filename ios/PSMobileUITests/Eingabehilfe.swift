import XCTest

/// Den Inhalt eines Textfelds ersetzen - auf dem Geraet wie im Simulator,
/// in jeder Sprache.
///
/// Vorher: langer Druck und `menuItems["Select All"]`. Auf einem deutschen
/// iPad heisst der Eintrag "Alles auswählen", der Tipp ging ins Leere, und
/// aus "180" wurde "210180" (iPad Pro 11", 15.09.2026). Deshalb ans Ende
/// des Felds tippen, den alten Inhalt mit der Loeschtaste entfernen und
/// dann schreiben - das braucht kein Menue.
extension XCUIElement {
    func ersetzeText(_ text: String) {
        coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        let alt = (value as? String ?? "").count
        if alt > 0 {
            typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: alt + 2))
        }
        typeText(text)
    }
}
