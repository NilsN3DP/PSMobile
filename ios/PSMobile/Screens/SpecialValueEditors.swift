import SwiftUI
import PSMShared

/// Die Werte, die als Zeichenkette nicht zu bedienen sind.
///
/// PrusaSlicer haelt intern alles als Text, auch eine Bettform
/// ("0x0,250x0,250x210,0x210") und die Reinigungsmengen einer MMU (eine
/// Matrix mit bis zu 25 Zahlen). Im allgemeinen Renderer landen sie in
/// einem einzeiligen Feld - technisch richtig, praktisch unbrauchbar:
/// ein Tippfehler in der Mitte macht das Bett kaputt, und die Matrix
/// laesst sich so gar nicht lesen.
///
/// Gerechnet wird in `SpecialValueCodec` im gemeinsamen Modul. Hier
/// steht nur, wie es aussieht.
enum SpecialValueEditors {

    /// Welche Schluessel einen eigenen Bearbeiter haben.
    static func hasEditor(_ key: String) -> Bool {
        key == "bed_shape" || key == "wiping_volumes_matrix"
    }
}

/// Die Bettform.
///
/// Der haeufige Fall ist ein Rechteck - dafuer drei Zahlen statt einer
/// Punktliste. Wer eine andere Form hat (Delta, abgeschraegte Ecken),
/// sieht die Punkte einzeln und kann sie aendern; erfinden kann die
/// Oberflaeche sie nicht.
struct BedShapeEditor: View {

    @ObservedObject var model: SlicerModel
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var punkte: [SpecialValueCodec.BedPoint] = []
    @State private var breite = ""
    @State private var tiefe = ""

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(14)) {
            Text(PsUiCatalog.tr("Bed shape"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)

            if istRechteck {
                Text(st("Rectangular bed", "Rechteckiges Bett"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                HStack(spacing: ps.pt(10)) {
                    zahl(st("Width", "Breite"), $breite, kennung: "bett.breite")
                    zahl(st("Depth", "Tiefe"), $tiefe, kennung: "bett.tiefe")
                }
            } else {
                // Keine erfundene Vereinfachung: was nicht rechteckig ist,
                // wird als das gezeigt, was es ist.
                Text(st("Custom shape with \(punkte.count) points",
                        "Freie Form mit \(punkte.count) Punkten"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                ScrollView {
                    VStack(spacing: ps.pt(4)) {
                        ForEach(Array(punkte.enumerated()), id: \.offset) { i, punkt in
                            HStack(spacing: ps.pt(8)) {
                                Text("\(i + 1)")
                                    .font(.system(size: ps.font(11)))
                                    .foregroundStyle(PrusaColors.textMuted)
                                    .frame(width: ps.pt(24))
                                Text(String(format: "%.1f  ×  %.1f mm", punkt.x, punkt.y))
                                    .font(.system(size: ps.font(13)))
                                    .foregroundStyle(PrusaColors.textPrimary)
                                Spacer()
                            }
                        }
                    }
                }
                .frame(maxHeight: ps.pt(240))
            }

            if let flaeche = flaecheQcm {
                Text(st("Area", "Fläche") + String(format: ": %.0f cm²", flaeche))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .accessibilityIdentifier("bett.flaeche")
            }

            HStack(spacing: ps.pt(12)) {
                Button(st("Cancel", "Abbrechen"), action: onClose)
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                if istRechteck {
                    Button {
                        uebernehmen()
                    } label: {
                        Text(st("Apply", "Übernehmen"))
                            .foregroundStyle(.white)
                            .padding(.horizontal, ps.pt(20))
                            .frame(height: ps.touch(48))
                            .background(PrusaColors.orange)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("bett.uebernehmen")
                }
            }
            Spacer()
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "bett") }
        .onAppear(perform: laden)
    }

    /// Vier Punkte, an den Achsen ausgerichtet, mit Ursprung im
    /// Nullpunkt - dann reichen zwei Zahlen.
    private var istRechteck: Bool {
        guard punkte.count == 4 else { return false }
        let xs = Set(punkte.map { round($0.x * 10) })
        let ys = Set(punkte.map { round($0.y * 10) })
        return xs.count == 2 && ys.count == 2
    }

    private var flaecheQcm: Double? {
        guard punkte.count >= 3 else { return nil }
        return SpecialValueCodec.shared.polygonArea(points: punkte) / 100
    }

    private func laden() {
        let roh = model.config("bed_shape") ?? ""
        punkte = SpecialValueCodec.shared.parseBedShape(value: roh) ?? []
        breite = String(format: "%.0f", (punkte.map(\.x).max() ?? 0) - (punkte.map(\.x).min() ?? 0))
        tiefe = String(format: "%.0f", (punkte.map(\.y).max() ?? 0) - (punkte.map(\.y).min() ?? 0))
    }

    private func uebernehmen() {
        guard let b = Double(breite.replacingOccurrences(of: ",", with: ".")),
              let t = Double(tiefe.replacingOccurrences(of: ",", with: ".")),
              b > 0, t > 0 else { return }
        let neu = [
            SpecialValueCodec.BedPoint(x: 0, y: 0),
            SpecialValueCodec.BedPoint(x: b, y: 0),
            SpecialValueCodec.BedPoint(x: b, y: t),
            SpecialValueCodec.BedPoint(x: 0, y: t),
        ]
        model.setConfig("bed_shape", SpecialValueCodec.shared.encodeBedShape(points: neu))
        onClose()
    }

    private func zahl(_ label: String,
                      _ text: Binding<String>,
                      kennung: String) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(label)
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
            TextField("", text: text)
                .keyboardType(.numbersAndPunctuation)
                .font(.system(size: ps.font(14)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .accessibilityIdentifier(kennung)
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Die Reinigungsmengen einer MMU.
///
/// Eine Matrix: wie viel Material beim Wechsel von Position i nach j
/// verworfen wird. Als eine Zeile Zahlen ist sie nicht zu lesen - als
/// Gitter mit Zeilen- und Spaltenkoepfen schon.
struct WipingVolumesEditor: View {

    @ObservedObject var model: SlicerModel
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var werte: [Double] = []

    private var n: Int { Int(Double(werte.count).squareRoot().rounded()) }

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(12)) {
            Text(PsUiCatalog.tr("Wipe tower"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st("Millimetres purged when changing from the row to the column.",
                    "Millimeter, die beim Wechsel von der Zeile zur Spalte verworfen werden."))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)

            if n < 2 {
                Text(st("Only one extruder - nothing to purge.",
                        "Nur ein Extruder - nichts zu verwerfen."))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
            } else {
                ScrollView([.horizontal, .vertical]) {
                    Grid(horizontalSpacing: ps.pt(4), verticalSpacing: ps.pt(4)) {
                        GridRow {
                            Text("").frame(width: ps.pt(28))
                            ForEach(0..<n, id: \.self) { spalte in
                                kopf("\(spalte + 1)")
                            }
                        }
                        ForEach(0..<n, id: \.self) { zeile in
                            GridRow {
                                kopf("\(zeile + 1)")
                                ForEach(0..<n, id: \.self) { spalte in
                                    zelle(zeile: zeile, spalte: spalte)
                                }
                            }
                        }
                    }
                    .padding(ps.pt(4))
                }
                .frame(maxHeight: ps.pt(320))
            }

            HStack {
                Button(st("Cancel", "Abbrechen"), action: onClose)
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Button { uebernehmen() } label: {
                    Text(st("Apply", "Übernehmen"))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(20))
                        .frame(height: ps.touch(48))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("reinigung.uebernehmen")
            }
            Spacer()
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "reinigung") }
        .onAppear(perform: laden)
    }

    private func kopf(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(11)))
            .foregroundStyle(PrusaColors.textMuted)
            .frame(width: ps.pt(56), height: ps.pt(28))
    }

    /// Die Diagonale bleibt leer: von einer Position auf sich selbst
    /// wechselt niemand.
    @ViewBuilder private func zelle(zeile: Int, spalte: Int) -> some View {
        let index = zeile * n + spalte
        if zeile == spalte {
            Text("—")
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .frame(width: ps.pt(56), height: ps.touch(40))
        } else {
            TextField("", text: Binding(
                get: { index < werte.count ? String(format: "%.0f", werte[index]) : "" },
                set: { neu in
                    if index < werte.count,
                       let z = Double(neu.replacingOccurrences(of: ",", with: ".")) {
                        werte[index] = z
                    }
                }))
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textPrimary)
                .frame(width: ps.pt(56), height: ps.touch(40))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                .accessibilityIdentifier("reinigung.\(zeile).\(spalte)")
        }
    }

    private func laden() {
        let roh = model.config("wiping_volumes_matrix") ?? ""
        werte = (SpecialValueCodec.shared.parseFloats(value: roh) ?? []).map { $0.doubleValue }
    }

    private func uebernehmen() {
        let text = SpecialValueCodec.shared.encodeFloats(
            values: werte.map { KotlinDouble(double: $0) })
        model.setConfig("wiping_volumes_matrix", text)
        onClose()
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
