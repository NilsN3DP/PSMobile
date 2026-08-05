import SwiftUI
import PSMShared

/// Variable Schichthöhen.
///
/// PrusaSlicer lässt am Desktop eine Kurve über die Modellhöhe malen.
/// Mit dem Finger ist das nicht zu treffen, deshalb Stützstellen als
/// Zahlenpaare: ab welcher Höhe gilt welche Schichtdicke. Was dazwischen
/// liegt, ergibt sich.
///
/// Ob ein Profil gültig ist — mindestens zwei Punkte, steigende Z-Werte,
/// positive Höhen — entscheidet `LayerProfile` im gemeinsamen Modul.
/// Hier steht die Anzeige und ein Balken, der zeigt, was dabei
/// herauskommt.
struct LayerProfileView: View {

    @ObservedObject var model: SlicerModel
    let objekt: PsmCore.ObjectInfo
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var zeilen: [LayerProfile.Row] = []

    private var hoehe: Double { Double(objekt.sizeMm.z) }
    private var anwendbar: Bool { LayerProfile.shared.canApply(rows: zeilen) }
    private var baender: [LayerProfile.Segment] {
        LayerProfile.shared.preview(objectHeight: hoehe, rows: zeilen)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(12)) {
            Text(PsUiCatalog.tr("Variable layer height"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st("From this height, the given layer thickness applies.",
                    "Ab dieser Höhe gilt die angegebene Schichtdicke."))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)

            HStack(alignment: .top, spacing: ps.pt(16)) {
                vorschau
                stellen
            }

            HStack(spacing: ps.pt(12)) {
                Button(st("Reset", "Zurücksetzen")) {
                    model.clearLayerProfile(objekt.id)
                    zeilen = LayerProfile.shared.defaults(objectHeight: hoehe)
                }
                .foregroundStyle(PrusaColors.textMuted)
                .accessibilityIdentifier("schichten.zuruecksetzen")
                Spacer()
                Button(st("Cancel", "Abbrechen"), action: onClose)
                    .foregroundStyle(PrusaColors.textMuted)
                    .accessibilityIdentifier("schichten.abbrechen")
                Button {
                    model.setLayerProfile(objekt.id,
                                          points: LayerProfile.shared.points(rows: zeilen))
                    onClose()
                } label: {
                    Text(st("Apply", "Übernehmen"))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(20))
                        .frame(height: ps.touch(48))
                        .background(anwendbar ? PrusaColors.orange : PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                }
                .buttonStyle(.plain)
                .disabled(!anwendbar)
                .accessibilityIdentifier("schichten.uebernehmen")
            }
            Spacer()
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "schichten") }
        .onAppear(perform: laden)
    }

    /// Ein Balken über die Modellhöhe. Dünne Schichten dunkler, dicke
    /// heller - so sieht man auf einen Blick, wo fein gedruckt wird,
    /// ohne die Zahlen zu lesen.
    private var vorschau: some View {
        VStack(spacing: 0) {
            ForEach(Array(baender.reversed().enumerated()), id: \.offset) { _, band in
                let anteil = hoehe > 0 ? (band.toZ - band.fromZ) / hoehe : 0
                Rectangle()
                    .fill(farbe(band.heightMm))
                    .frame(height: max(ps.pt(2), ps.pt(260) * anteil))
                    .overlay(alignment: .leading) {
                        Text(String(format: "%.2f", band.heightMm))
                            .font(.system(size: ps.font(9)))
                            .foregroundStyle(PrusaColors.background)
                            .padding(.leading, ps.pt(4))
                    }
            }
            if baender.isEmpty {
                Rectangle()
                    .fill(PrusaColors.panelRaised)
                    .frame(height: ps.pt(260))
                    .overlay(
                        Text(st("Not valid", "Nicht gültig"))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    )
            }
        }
        .frame(width: ps.pt(72))
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
        .accessibilityIdentifier("schichten.vorschau")
    }

    /// Dünn ist dunkel, dick ist hell - dieselbe Richtung wie in
    /// PrusaSlicers eigener Darstellung.
    private func farbe(_ hoeheMm: Double) -> Color {
        let anteil = min(max((hoeheMm - 0.05) / 0.30, 0), 1)
        return Color(hue: 0.08, saturation: 0.75, brightness: 0.35 + 0.5 * anteil)
    }

    private var stellen: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            ForEach(Array(zeilen.enumerated()), id: \.offset) { i, zeile in
                HStack(spacing: ps.pt(6)) {
                    feld(zeile.z, einheit: "mm", kennung: "schichten.z.\(i)") { neu in
                        zeilen = LayerProfile.shared.updateRow(rows: zeilen, index: Int32(i),
                                                               z: neu, height: zeile.height)
                    }
                    feld(zeile.height, einheit: st("Layer", "Schicht"),
                         kennung: "schichten.h.\(i)") { neu in
                        zeilen = LayerProfile.shared.updateRow(rows: zeilen, index: Int32(i),
                                                               z: zeile.z, height: neu)
                    }
                    Button {
                        zeilen = LayerProfile.shared.removePoint(rows: zeilen, index: Int32(i))
                    } label: {
                        Text("✖")
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .frame(width: ps.touch(40), height: ps.touch(40))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(zeilen.count <= 2)
                    .accessibilityIdentifier("schichten.weg.\(i)")
                }
            }
            Button {
                zeilen = LayerProfile.shared.addPoint(rows: zeilen, objectHeight: hoehe)
            } label: {
                Text("＋ " + st("Point", "Stützstelle"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.orange)
                    .frame(minHeight: ps.touch(44))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("schichten.neu")
        }
    }

    private func feld(_ wert: String,
                      einheit: String,
                      kennung: String,
                      uebernehmen: @escaping (String) -> Void) -> some View {
        HStack(spacing: ps.pt(3)) {
            TextField("", text: Binding(get: { wert }, set: uebernehmen))
                .keyboardType(.numbersAndPunctuation)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textPrimary)
                .accessibilityIdentifier(kennung)
            Text(einheit)
                .font(.system(size: ps.font(9)))
                .foregroundStyle(PrusaColors.textMuted)
        }
        .padding(.horizontal, ps.pt(8))
        .frame(width: ps.pt(104), height: ps.touch(44))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
    }

    private func laden() {
        let vorhanden = model.layerProfile(objekt.id)
        zeilen = LayerProfile.shared.fromPoints(objectHeight: hoehe, points: vorhanden)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
