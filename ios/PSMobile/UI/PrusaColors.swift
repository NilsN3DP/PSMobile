import SwiftUI

/// Die Farben aus PrusaSlicer.
///
/// Werte identisch zu `PrusaColors` auf Android. Absichtlich nicht
/// geteilt: es sind dreizehn Konstanten, und ein gemeinsames Modul
/// muesste sie erst in einen plattformfreien Typ verpacken, den beide
/// Seiten wieder auspacken. Der Aufwand waere groesser als der Nutzen -
/// anders als bei den Regeln, wo eine Abweichung im Verhalten nicht
/// auffaellt. Eine falsche Farbe sieht man sofort.
enum PrusaColors {

    /// Prusa-Akzent.
    static let orange     = Color(hex: 0xED6B21)
    static let orangeDim  = Color(hex: 0xC2551A)

    /// Fensterhintergrund.
    static let background = Color(hex: 0x232426)
    /// Seitenleisten, Werkzeugleiste.
    static let panel      = Color(hex: 0x2B2D30)
    /// Eingabefelder, Karten.
    static let panelRaised = Color(hex: 0x35373B)
    static let divider    = Color(hex: 0x3F4246)

    static let textPrimary = Color(hex: 0xE6E6E6)
    static let textMuted   = Color(hex: 0x9BA0A6)
    static let danger      = Color(hex: 0xE05252)
    static let ok          = Color(hex: 0x5BB75B)

    /// Verlauf der 3D-Flaeche, wie im Slicer hinter dem Druckbett.
    static let sceneGradient = LinearGradient(
        colors: [Color(hex: 0x3C4045), Color(hex: 0x212327)],
        startPoint: .top, endPoint: .bottom,
    )
}

extension Color {
    /// Aus "#RRGGBB", wie PrusaSlicer Filamentfarben notiert. Nil, wenn
    /// nichts oder etwas anderes dasteht - dann zeigt die Oberflaeche
    /// lieber ein neutrales Feld als eine erfundene Farbe.
    init?(hexString: String) {
        var text = hexString.trimmingCharacters(in: .whitespaces)
        if text.hasPrefix("#") { text.removeFirst() }
        guard text.count == 6, let wert = UInt32(text, radix: 16) else { return nil }
        self.init(hex: wert)
    }

    /// 0xRRGGBB, wie die Werte in PrusaSlicer und auf Android notiert sind.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue:  Double(hex & 0xFF) / 255,
            opacity: 1,
        )
    }
}
