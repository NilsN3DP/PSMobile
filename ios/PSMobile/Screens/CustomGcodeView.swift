import SwiftUI
import PSMShared

/// Was auf welcher Höhe passiert — Gegenstück zu PrusaSlicers Marken am
/// Schichtregler.
///
/// Der wichtigste Fall ist der Farbwechsel: man druckt bis 4 mm, der
/// Drucker hält an, man wechselt die Rolle von Hand, es geht weiter. Das
/// ist der einzige Weg, auf einem einfarbigen Drucker zweifarbig zu
/// drucken — und dafür kauft niemand einen zweiten Drucker.
///
/// Daneben die Pause (eine Mutter versenken, ein Magnet einlegen), der
/// Werkzeugwechsel bei mehreren Extrudern, und beliebiger eigener Code.
///
/// Die Höhe steht in Millimetern, nicht in Schichten. PrusaSlicer führt
/// sie so, und eine Schichtnummer wäre nach jeder Änderung der
/// Schichthöhe eine andere Stelle im Modell.
struct CustomGcodeView: View {

    @ObservedObject var model: SlicerModel
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var eintraege: [PsmCore.CustomGcode] = []
    @State private var neueHoehe = ""
    @State private var neuerTyp: PsmCore.CustomGcode.Kind = .colorChange
    @State private var neueFarbe = "#FF8000"
    @State private var eigenerCode = ""

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(14)) {
            HStack {
                Text(PsUiCatalog.tr("Custom G-code"))
                    .font(.system(size: ps.font(19), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Spacer()
                Button(st("Done", "Fertig"), action: onClose)
                    .foregroundStyle(PrusaColors.orange)
                    .frame(minHeight: ps.touch(44))
                    .accessibilityIdentifier("gcode.fertig")
            }
            Text(st("Height in millimetres, as PrusaSlicer counts it — a layer number would move with every change of layer height.",
                    "Höhe in Millimetern, wie PrusaSlicer sie führt — eine Schichtnummer wäre nach jeder Änderung der Schichthöhe eine andere Stelle."))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)

            ScrollView {
                VStack(alignment: .leading, spacing: ps.pt(10)) {
                    liste
                    Divider().overlay(PrusaColors.divider)
                    neuerEintrag
                }
            }
            Spacer(minLength: 0)
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "customgcode") }
        .onAppear { eintraege = model.customGcodeList() }
    }

    @ViewBuilder private var liste: some View {
        if eintraege.isEmpty {
            Text(st("Nothing set yet", "Noch nichts eingetragen"))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.vertical, ps.pt(10))
        } else {
            ForEach(Array(eintraege.enumerated()), id: \.offset) { index, e in
                HStack(spacing: ps.pt(10)) {
                    if e.type == .colorChange {
                        RoundedRectangle(cornerRadius: ps.pt(3))
                            .fill(Color(hexString: e.color) ?? PrusaColors.panelRaised)
                            .frame(width: ps.pt(22), height: ps.pt(22))
                            .overlay(RoundedRectangle(cornerRadius: ps.pt(3))
                                .stroke(PrusaColors.divider, lineWidth: 1))
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(String(format: "%.2f mm", e.printZ))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Text(name(e.type) + (e.extra.isEmpty ? "" : " · " + kurz(e.extra)))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button {
                        model.removeCustomGcode(index)
                        eintraege = model.customGcodeList()
                    } label: {
                        Text("✖")
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .frame(width: ps.touch(40), height: ps.touch(40))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("gcode.weg.\(index)")
                }
                .padding(.horizontal, ps.pt(10))
                .frame(minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            }
        }
    }

    private var neuerEintrag: some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(st("Add", "Hinzufügen").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)

            HStack(spacing: ps.pt(8)) {
                TextField("0.0", text: $neueHoehe)
                    .keyboardType(.decimalPad)
                    .font(.system(size: ps.font(14)))
                    .padding(.horizontal, ps.pt(10))
                    .frame(width: ps.pt(110), height: ps.touch(48))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .accessibilityIdentifier("gcode.hoehe")
                Text("mm")
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
            }

            // Vier Arten, nicht fünf: die Vorlage (TEMPLATE) ist ein
            // Sonderfall der Profile und gehört nicht in eine Liste, die
            // man je Projekt pflegt.
            HStack(spacing: ps.pt(6)) {
                typKnopf(st("Colour change", "Farbwechsel"), .colorChange)
                typKnopf(st("Pause", "Pause"), .pause)
                if model.extruderCount > 1 {
                    typKnopf(st("Tool", "Werkzeug"), .toolChange)
                }
                typKnopf(st("Code", "Code"), .code)
            }

            if neuerTyp == .colorChange {
                HStack(spacing: ps.pt(8)) {
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .fill(Color(hexString: neueFarbe) ?? PrusaColors.panelRaised)
                        .frame(width: ps.pt(26), height: ps.pt(26))
                        .overlay(RoundedRectangle(cornerRadius: ps.pt(3))
                            .stroke(PrusaColors.divider, lineWidth: 1))
                    TextField("#FF8000", text: $neueFarbe)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.characters)
                        .font(.system(size: ps.font(13)))
                        .padding(.horizontal, ps.pt(10))
                        .frame(height: ps.touch(48))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                        .accessibilityIdentifier("gcode.farbe")
                }
            }
            if neuerTyp == .code {
                TextField("M117 …", text: $eigenerCode, axis: .vertical)
                    .lineLimit(2...5)
                    .autocorrectionDisabled()
                    .font(.system(size: ps.font(13), design: .monospaced))
                    .padding(ps.pt(10))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .accessibilityIdentifier("gcode.eigener")
            }

            Button { hinzufuegen() } label: {
                Text(st("Add", "Hinzufügen"))
                    .font(.system(size: ps.font(14), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: ps.touch(50))
                    .background(hoeheGueltig ? PrusaColors.orange : PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!hoeheGueltig)
            .accessibilityIdentifier("gcode.hinzufuegen")
        }
    }

    /// Eine Höhe muss eine Zahl über null sein. Null wäre die erste
    /// Schicht, und dort einen Farbwechsel zu setzen heißt: gar keinen.
    private var hoeheGueltig: Bool {
        guard let z = Double(neueHoehe.replacingOccurrences(of: ",", with: ".")) else {
            return false
        }
        return z > 0
    }

    private func typKnopf(_ label: String, _ art: PsmCore.CustomGcode.Kind) -> some View {
        let aktiv = neuerTyp == art
        return Button { neuerTyp = art } label: {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(12))
                .frame(minHeight: ps.touch(42))
                .background(aktiv ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("gcode.typ.\(art.rawValue)")
    }

    private func hinzufuegen() {
        guard let z = Double(neueHoehe.replacingOccurrences(of: ",", with: ".")) else { return }
        model.addCustomGcode(PsmCore.CustomGcode(
            printZ: z,
            type: neuerTyp,
            extruder: 0,
            color: neuerTyp == .colorChange ? neueFarbe : "",
            extra: neuerTyp == .code ? eigenerCode : ""))
        eintraege = model.customGcodeList()
        neueHoehe = ""
        eigenerCode = ""
    }

    private func name(_ art: PsmCore.CustomGcode.Kind) -> String {
        switch art {
        case .colorChange: return st("Colour change", "Farbwechsel")
        case .pause:       return st("Pause", "Pause")
        case .toolChange:  return st("Tool change", "Werkzeugwechsel")
        case .template:    return st("Template", "Vorlage")
        case .code:        return st("Custom code", "Eigener Code")
        }
    }

    /// Mehrzeiliger Code in einer Zeile: die erste Zeile sagt genug,
    /// um ihn wiederzuerkennen.
    private func kurz(_ text: String) -> String {
        let erste = text.split(separator: "\n").first.map(String.init) ?? text
        return erste.count > 28 ? String(erste.prefix(27)) + "…" : erste
    }
}
