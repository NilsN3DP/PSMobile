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
    /// Was laden() zuletzt angezeigt hat - unveraendert wird nichts geschrieben.
    @State private var angezeigt: String = ""
    @State private var meta: PsmCore.ConfigMeta?
    @State private var auswahl: [PsmCore.EnumValue] = []
    @State private var gesperrt: (enabled: Bool, reason: String) = (true, "")

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
                        // Einheiten laufen durch den Katalog: "layers" heisst auf
                        // Deutsch "Schichten", "mm or %" "mm oder %" (16.09.2026).
                        Text(istLaenge && zollEinheiten ? "in" : PsUiCatalog.tr(meta.unit))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                }
            }

            feld

            // Ist ein Feld ausgegraut, gehoert der Grund dazu. Ein totes
            // Feld ohne Erklaerung sieht aus wie ein Fehler - PrusaSlicer
            // nennt den Grund, und der kommt ueber das ABI mit.
            // Als Beschriftung des sperrenden Parameters, nicht als
            // Schluessel: bis zum 16.09.2026 stand "perimeters" unter
            // "Duenne Waende erkennen" (Zwilling: SettingField.kt).
            if !gesperrt.enabled, !gesperrt.reason.isEmpty {
                let sperrerLabel = model.core?.configMeta(for: gesperrt.reason)?.label ?? ""
                let sperrer = sperrerLabel.isEmpty ? gesperrt.reason : PsUiCatalog.tr(sperrerLabel)
                Text(st("Depends on: ", "Abhängig von: ") + sperrer)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
        }
        .opacity(gesperrt.enabled ? 1 : 0.55)
        // Jedes Feld traegt seinen Parameternamen. Die Tests griffen
        // bisher "das erste Textfeld der Seite" - das trifft, solange
        // niemand die Reihenfolge in tabs.json aendert, und schweigt,
        // wenn doch. Mit dem Namen prueft ein Test die Schichthoehe und
        // nicht die erste Zeile.
        .accessibilityIdentifier("feld." + option.key)
        .onAppear(perform: laden)
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
        // Der Kern liefert immer mm - fuers Anzeigen in Zoll umrechnen,
        // ohne den gespeicherten Wert anzufassen.
        if zollEinheiten, meta?.unit == "mm", let mm = Double(wert) {
            wert = Self.formatiert(mm / 25.4)
        }
        angezeigt = wert
        gesperrt = core.configEnabled(option.key)
        if let m = meta, m.type == .enumeration, m.enumCount > 0 {
            auswahl = core.configEnumValues(option.key, count: m.enumCount)
        }
    }

    private func schreiben(_ roh: String) {
        // Dezimalkomma wie Punkt: die deutsche Tastatur tippt "0,3", und der
        // Kern las daraus 0 - die erste Schicht stand danach auf 0 mm (S23 FE,
        // 16.09.2026). Nur bei Zahlenfeldern; ein Text darf sein Komma behalten.
        let zahlig = meta?.type == .float || meta?.type == .percent || meta?.type == .int
        let neu = zahlig ? roh.replacingOccurrences(of: ",", with: ".") : roh
        wert = neu
        // Unveraendert: nichts schreiben. In Zoll ist die Anzeige gerundet
        // (0.2 mm → 0.0079 in → 0.2007 mm), und bis zum 16.09.2026 galt das
        // Profil nach Antippen + Verlassen des Felds als geaendert.
        if neu == angezeigt { laden(); return }
        // Umgekehrt beim Schreiben: was auf dem Bildschirm Zoll ist,
        // geht als mm an den Kern - der kennt keine Zoll-Einstellung.
        if zollEinheiten, istLaenge, let zoll = Double(neu) {
            model.setConfig(option.key, Self.formatiert(zoll * 25.4))
        } else {
            model.setConfig(option.key, neu)
        }
        // Danach aus dem Kern zuruecklesen, nicht dem Bildschirm glauben:
        // ein abgelehnter Wert (Schichthoehe 0, Buchstaben) springt so
        // sichtbar auf den alten zurueck, und ein geaenderter kann andere
        // Felder sperren oder freigeben.
        laden()
    }
}
