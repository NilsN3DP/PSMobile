import SwiftUI
import PSMShared

/// Ein Teil ins Objekt legen — Gegenstück zu `AddVolumeDialog` auf
/// Android.
///
/// Drei Entscheidungen, mehr braucht es nicht: wofür der Körper da ist,
/// welche Form er hat, wie groß er ist. Er entsteht in der Mitte des
/// Objekts; an seinen Platz schiebt man ihn danach mit den Griffen —
/// genau wie am Desktop.
///
/// Der Modellkörper selbst fehlt in der Auswahl. Einen Vollkörper aus
/// einem Grundkörper zu bauen ist ein Modellierschritt; die vier
/// anderen sind das, wofür man sonst das Programm wechselt.
struct TeilHinzufuegenView: View {

    @ObservedObject var model: SlicerModel
    let objektId: Int32
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var art: PsmCore.VolumeType = .negative
    @State private var form: PsmCore.PrimitiveShape = .box
    @State private var groesse: Double = 10

    // Nur Typ und Beschriftung. Der Erklaertext stand hier frueher als
    // vierter Wert mit - auf Deutsch und ohne englische Fassung -, wurde
    // aber nie gelesen: gezeichnet wird erklaerung(), und die uebersetzt.
    private let arten: [(PsmCore.VolumeType, String, String)] = [
        (.negative, "Negative volume", "Aussparung"),
        (.modifier, "Modifier", "Modifier"),
        (.supportBlocker, "Support blocker", "Stützen verhindern"),
        (.supportEnforcer, "Support enforcer", "Stützen erzwingen"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(14)) {
            HStack {
                Text(st("Add part", "Teil hinzufügen"))
                    .font(.system(size: ps.font(19), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Spacer()
                Button(st("Cancel", "Abbrechen"), action: onClose)
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(minHeight: ps.touch(44))
                    .accessibilityIdentifier("teil.abbrechen")
            }

            ScrollView {
                VStack(alignment: .leading, spacing: ps.pt(14)) {
                    artwahl
                    formwahl
                    groessenwahl
                }
            }

            Button { hinzufuegen() } label: {
                Text(st("Add", "Hinzufügen"))
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: ps.touch(52))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("teil.hinzufuegen")
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "teilhinzufuegen") }
    }

    /// Jede Art mit einem Satz dazu. Vier Wörter ohne Erklärung wären
    /// vier Fragen — „Modifier" sagt niemandem etwas, der es nicht
    /// schon weiß.
    private var artwahl: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            ueberschrift(st("Purpose", "Wofür"))
            ForEach(arten, id: \.0) { eintrag in
                let aktiv = art == eintrag.0
                Button { art = eintrag.0 } label: {
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(st(eintrag.1, eintrag.2))
                            .font(.system(size: ps.font(13), weight: aktiv ? .semibold : .regular))
                            .foregroundStyle(aktiv ? PrusaColors.orange : PrusaColors.textPrimary)
                        Text(erklaerung(eintrag.0))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, ps.pt(12))
                    .padding(.vertical, ps.pt(8))
                    .frame(minHeight: ps.touch(52))
                    .background(aktiv ? PrusaColors.panelRaised : PrusaColors.panel)
                    .overlay(RoundedRectangle(cornerRadius: ps.pt(4))
                        .stroke(aktiv ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("teil.art.\(eintrag.0.rawValue)")
            }
        }
    }

    private var formwahl: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            ueberschrift(st("Shape", "Form"))
            HStack(spacing: ps.pt(8)) {
                formKnopf(st("Box", "Quader"), .box)
                formKnopf(st("Cylinder", "Zylinder"), .cylinder)
                formKnopf(st("Sphere", "Kugel"), .sphere)
            }
        }
    }

    private var groessenwahl: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            ueberschrift(st("Size", "Größe") + " · \(Int(groesse)) mm")
            Slider(value: $groesse, in: 2...60, step: 1)
                .tint(PrusaColors.orange)
                .frame(minHeight: ps.touch(44))
                .accessibilityIdentifier("teil.groesse")
            Text(st("The part appears at the centre of the object. Move it into place with the handles.",
                    "Das Teil entsteht in der Mitte des Objekts. An seinen Platz schiebt man es mit den Griffen."))
                .font(.system(size: ps.font(10)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func formKnopf(_ label: String, _ wert: PsmCore.PrimitiveShape) -> some View {
        let aktiv = form == wert
        return Button { form = wert } label: {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(minHeight: ps.touch(44))
                .background(aktiv ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("teil.form.\(wert.rawValue)")
    }

    private func ueberschrift(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: ps.font(11), weight: .semibold))
            .foregroundStyle(PrusaColors.textMuted)
    }

    private func erklaerung(_ wert: PsmCore.VolumeType) -> String {
        switch wert {
        case .negative:
            return st("A hole in the model — the part is subtracted.",
                      "Ein Loch im Modell — der Körper wird abgezogen.")
        case .modifier:
            return st("Different settings apply inside this region.",
                      "In diesem Bereich gelten andere Einstellungen.")
        case .supportBlocker:
            return st("No supports here, whatever the automatic says.",
                      "Hier setzt der Slicer keine Stützen, auch wenn die Automatik es will.")
        case .supportEnforcer:
            return st("Supports here, even without an overhang.",
                      "Hier setzt er Stützen, auch ohne Überhang.")
        case .modelPart:
            return ""
        }
    }

    private func hinzufuegen() {
        if model.addPrimitiveVolume(objektId, type: art, shape: form,
                                    size: Float(groesse)) {
            onClose()
        }
    }
}
