import Foundation
import PSMShared

/// Masse eines Objekts in der eingestellten Einheit - Gegenstueck zu
/// `masseText` in `android/.../ui/Texte.kt`. Bis zum 15.09.2026 galt
/// "Laengen in Zoll anzeigen" nur fuer die Einstellungsseiten; Objektliste
/// und Inspektor blieben bei mm (Emulator, Bug-Bounty).
enum Masse {
    static func text(_ x: Float, _ y: Float, _ z: Float, zoll: Bool) -> String {
        if zoll {
            return String(format: "%.2f × %.2f × %.2f in", x / 25.4, y / 25.4, z / 25.4)
        }
        return String(format: "%.1f × %.1f × %.1f mm", x, y, z)
    }
}
