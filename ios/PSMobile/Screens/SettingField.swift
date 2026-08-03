import SwiftUI
import PSMShared

/// Ein einzelner Parameter.
///
/// Was gezeichnet wird, entscheidet der Typ aus dem Kern - Schalter,
/// Zahl, Auswahl oder Text. Keine Liste, die jemand pflegen muesste:
/// PrusaSlicer kennt zu jedem Parameter Typ, Grenzen, Einheit und
/// Auswahlwerte, und genau die kommen ueber das ABI herueber.
struct SettingField: View {

    @ObservedObject var model: SlicerModel
    let option: TabsCatalog.Option
    let kompakt: Bool

    @Environment(\.psScale) private var ps
    @State private var wert: String = ""
    @State private var meta: PsmCore.ConfigMeta?
    @State private var auswahl: [PsmCore.EnumValue] = []
    @State private var gesperrt: (enabled: Bool, reason: String) = (true, "")
    @State private var zeigeBearbeiter = false

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            if !kompakt, let meta {
                HStack(spacing: ps.pt(6)) {
                    Text(PsUiCatalog.tr(meta.label))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(gesperrt.enabled
                                         ? PrusaColors.textPrimary : PrusaColors.textMuted)
                    if !meta.unit.isEmpty {
                        Text(meta.unit)
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                }
            }

            feld

            // Ist ein Feld ausgegraut, gehoert der Grund dazu. Ein totes
            // Feld ohne Erklaerung sieht aus wie ein Fehler - PrusaSlicer
            // nennt den Grund, und der kommt ueber das ABI mit.
            if !gesperrt.enabled, !gesperrt.reason.isEmpty {
                Text(gesperrt.reason)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
        }
        .opacity(gesperrt.enabled ? 1 : 0.55)
        .onAppear(perform: laden)
    }

    @ViewBuilder private var feld: some View {
        // Zwei Werte sind als Zeichenkette nicht zu bedienen: die
        // Bettform und die Reinigungsmengen einer MMU. Fuer sie gibt es
        // einen eigenen Bearbeiter; der allgemeine Renderer wuerde sie
        // in ein einzeiliges Feld legen, in dem ein Tippfehler in der
        // Mitte das Bett kaputtmacht.
        if SpecialValueEditors.hasEditor(option.key) {
            Button { zeigeBearbeiter = true } label: {
                HStack {
                    Text(wert.isEmpty ? "—" : wert)
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .lineLimit(1)
                    Spacer()
                    Text(SimpleModeState.shared.text(english: "Edit", german: "Bearbeiten"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("sonderwert." + option.key)
            .sheet(isPresented: $zeigeBearbeiter, onDismiss: laden) {
                if option.key == "bed_shape" {
                    BedShapeEditor(model: model) { zeigeBearbeiter = false }
                } else {
                    WipingVolumesEditor(model: model) { zeigeBearbeiter = false }
                }
            }
        } else {
            gewoehnlichesFeld
        }
    }

    @ViewBuilder private var gewoehnlichesFeld: some View {
        switch meta?.type {
        case .bool:
            Toggle(isOn: Binding(
                get: { wert == "1" },
                set: { schreiben($0 ? "1" : "0") })) {
                if kompakt, let meta {
                    Text(PsUiCatalog.tr(meta.label))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textPrimary)
                }
            }
            .toggleStyle(.switch)
            .tint(PrusaColors.orange)
            .disabled(!gesperrt.enabled)

        case .enumeration:
            Menu {
                ForEach(auswahl, id: \.value) { eintrag in
                    Button(PsUiCatalog.tr(eintrag.label)) { schreiben(eintrag.value) }
                }
            } label: {
                HStack {
                    Text(beschriftung(fuer: wert))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: ps.font(10)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(40))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            }
            .disabled(!gesperrt.enabled)

        default:
            // Zahl und Text gehen denselben Weg: PrusaSlicer haelt intern
            // ohnehin alles als Zeichenkette, und der Kern prueft beim
            // Setzen. Mehrzeilig nur, wo es als G-code markiert ist.
            if option.code {
                TextEditor(text: Binding(get: { wert }, set: { wert = $0 }))
                    .font(.system(size: ps.font(12), design: .monospaced))
                    .frame(height: ps.pt(120))
                    .scrollContentBackground(.hidden)
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .onSubmit { schreiben(wert) }
                    .disabled(!gesperrt.enabled)
            } else {
                TextField("", text: Binding(get: { wert }, set: { wert = $0 }))
                    .textFieldStyle(.plain)
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .padding(.horizontal, ps.pt(10))
                    .frame(height: ps.touch(40))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .onSubmit { schreiben(wert) }
                    .disabled(!gesperrt.enabled)
            }
        }
    }

    private func beschriftung(fuer wert: String) -> String {
        auswahl.first { $0.value == wert }.map { PsUiCatalog.tr($0.label) } ?? wert
    }

    private func laden() {
        guard let core = model.core else { return }
        meta = core.configMeta(for: option.key)
        wert = core.config(option.key) ?? ""
        gesperrt = core.configEnabled(option.key)
        if let m = meta, m.type == .enumeration, m.enumCount > 0 {
            auswahl = core.configEnumValues(option.key, count: m.enumCount)
        }
    }

    private func schreiben(_ neu: String) {
        wert = neu
        model.setConfig(option.key, neu)
        // Ein geaenderter Wert kann andere Felder sperren oder freigeben -
        // deshalb gleich neu befragen, statt es dem Zufall zu ueberlassen.
        if let core = model.core {
            gesperrt = core.configEnabled(option.key)
        }
    }
}
