import Foundation
import simd

/// Ein Würfel als STL, gerechnet statt mitgeliefert.
///
/// Eine Datei im Bundle wäre in jeder ausgelieferten App dabei, nur
/// damit ein Test etwas zum Anfassen hat.
///
/// Jede Zahl wird einzeln angehängt. Der erste Anlauf schrieb die zwölf
/// Fließkommazahlen eines Dreiecks aus einem zusammengesetzten Array —
/// dabei kamen nur sechs an, und die Datei war 396 statt 684 Bytes groß.
/// libslic3r meldete daraufhin nur „Loading of a model file failed",
/// ohne zu sagen, woran.
enum Testkoerper {

    /// Binäres STL eines achsenparallelen Würfels mit der Kante `kante`.
    static func wuerfel(kante a: Float = 20) -> Data {
        let ecken: [SIMD3<Float>] = [
            [0, 0, 0], [a, 0, 0], [a, a, 0], [0, a, 0],
            [0, 0, a], [a, 0, a], [a, a, a], [0, a, a],
        ]
        // Zwoelf Dreiecke, von aussen gesehen gegen den Uhrzeigersinn.
        let flaechen: [(Int, Int, Int)] = [
            (0, 2, 1), (0, 3, 2),   // unten
            (4, 5, 6), (4, 6, 7),   // oben
            (0, 1, 5), (0, 5, 4),   // vorn
            (1, 2, 6), (1, 6, 5),   // rechts
            (2, 3, 7), (2, 7, 6),   // hinten
            (3, 0, 4), (3, 4, 7),   // links
        ]

        // 80 Byte Kopf, vier Byte Anzahl, dann je Dreieck 50 Byte.
        var daten = Data(count: 80)
        func zahl(_ wert: Float) {
            var f = wert
            withUnsafeBytes(of: &f) { daten.append(contentsOf: $0) }
        }
        var anzahl = UInt32(flaechen.count)
        withUnsafeBytes(of: &anzahl) { daten.append(contentsOf: $0) }

        for (i, j, k) in flaechen {
            // Die Normale darf null bleiben - libslic3r rechnet sie aus
            // der Reihenfolge der Ecken.
            zahl(0); zahl(0); zahl(0)
            for ecke in [ecken[i], ecken[j], ecken[k]] {
                zahl(ecke.x); zahl(ecke.y); zahl(ecke.z)
            }
            var attribut = UInt16(0)
            withUnsafeBytes(of: &attribut) { daten.append(contentsOf: $0) }
        }
        return daten
    }

    /// Schreibt den Würfel in eine Datei und gibt ihren Pfad zurück.
    static func wuerfelDatei(kante: Float = 20,
                             name: String = "psm-testwuerfel.stl") throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try wuerfel(kante: kante).write(to: url)
        return url
    }
}
