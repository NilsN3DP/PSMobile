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
    @AppStorage(AppSettings.shared.KEY_UNITS_IMPERIAL) private var zollEinheiten = false
    @State private var wert: String = ""
    @State private var meta: PsmCore.ConfigMeta?
    @State private var auswahl: [PsmCore.EnumValue] = []
    @State private var gesperrt: (enabled: Bool, reason: String) = (true, "")
    /// Schreiben beim Verlassen des Feldes.
    ///
    /// Vorher hing das ausschliesslich an `.onSubmit`. Bei einem
    /// `TextEditor` gibt es aber gar kein Submit - Return setzt dort eine
    /// neue Zeile -, weshalb sich kein einziges der als `code` markierten
    /// Felder (Start-/End-G-Code, Schichtwechsel, Werkzeugwechsel, ...)
    /// je speichern liess: der Text blieb im @State stehen und sah
    /// uebernommen aus, kam aber nie im Kern an. Und auch beim
    /// einzeiligen Feld ging eine Eingabe verloren, sobald jemand
    /// weitertippte statt Return zu druecken. Android schreibt an dieser
    /// Stelle beim Fokusverlust (SettingsScreen.kt).
    @FocusState private var imFeld: Bool

    /// Nur echte Laengen (Einheit "mm") werden umgerechnet - eine
    /// Prozentzahl oder ein Grad-Wert hat mit Zoll nichts zu tun, und
    /// eine falsche Umrechnung dort waere schlimmer als keine Anzeige.
    private var istLaenge: Bool { meta?.unit == "mm" }

    private static func formatiert(_ wert: Double) -> String {
        // Bis zu vier Nachkommastellen, ueberfluessige Nullen weg -
        // 0.4 mm werden sonst zu "0.0157480000..." Zoll.
        let s = String(format: "%.4f", wert)
        var t = s
        while t.hasSuffix("0") { t.removeLast() }
        if t.hasSuffix(".") { t.removeLast() }
        return t
    }

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            if !kompakt, let meta {
                HStack(spacing: ps.pt(6)) {
                    Text(PsUiCatalog.tr(meta.label))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(gesperrt.enabled
                                         ? PrusaColors.textPrimary : PrusaColors.textMuted)
                    if !meta.unit.isEmpty {
                        Text(istLaenge && zollEinheiten ? "in" : meta.unit)
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
        .onChange(of: zollEinheiten) { _ in einheitGewechselt() }
    }

    @ViewBuilder private var feld: some View {
        gewoehnlichesFeld
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
                    .focused($imFeld)
                    .onChange(of: imFeld) { drin in if !drin { schreiben(wert) } }
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
                    .focused($imFeld)
                    .onChange(of: imFeld) { drin in if !drin { schreiben(wert) } }
                    .onSubmit { schreiben(wert) }
                    .disabled(!gesperrt.enabled)
            }
        }
    }

    /// Wechselt die Zoll-Einstellung, waehrend dieses Feld schon steht,
    /// muss der angezeigte Wert mit umgerechnet werden. Die Einheit
    /// daneben folgte sofort (sie haengt direkt an @AppStorage), die Zahl
    /// aber kam nur aus `laden()` in `.onAppear` - bei einem Feld, das
    /// mounted bleibt (App-Einstellungen liegen als Overlay ueber dem
    /// Simple Mode), stand danach ein Millimeterwert unter dem Etikett
    /// "in", und ein anschliessendes Speichern haette ihn nochmals mit
    /// 25,4 multipliziert.
    private func einheitGewechselt() {
        guard let core = model.core, meta?.unit == "mm" else { return }
        let mm = core.config(option.key) ?? ""
        guard let roh = Double(mm) else { return }
        wert = zollEinheiten ? Self.formatiert(roh / 25.4) : Self.formatiert(roh)
    }

    private func beschriftung(fuer wert: String) -> String {
        auswahl.first { $0.value == wert }.map { PsUiCatalog.tr($0.label) } ?? wert
    }

    private func laden() {
        guard let core = model.core else { return }
        meta = core.configMeta(for: option.key)
        wert = core.config(option.key) ?? ""
        // Der Kern liefert immer mm - fuers Anzeigen in Zoll umrechnen,
        // ohne den gespeicherten Wert anzufassen.
        if zollEinheiten, meta?.unit == "mm", let mm = Double(wert) {
            wert = Self.formatiert(mm / 25.4)
        }
        gesperrt = core.configEnabled(option.key)
        if let m = meta, m.type == .enumeration, m.enumCount > 0 {
            auswahl = core.configEnumValues(option.key, count: m.enumCount)
        }
    }

    private func schreiben(_ neu: String) {
        wert = neu
        // Umgekehrt beim Schreiben: was auf dem Bildschirm Zoll ist,
        // geht als mm an den Kern - der kennt keine Zoll-Einstellung.
        if zollEinheiten, istLaenge, let zoll = Double(neu) {
            model.setConfig(option.key, Self.formatiert(zoll * 25.4))
        } else {
            model.setConfig(option.key, neu)
        }
        // Ein geaenderter Wert kann andere Felder sperren oder freigeben -
        // deshalb gleich neu befragen, statt es dem Zufall zu ueberlassen.
        if let core = model.core {
            gesperrt = core.configEnabled(option.key)
        }
    }
}
