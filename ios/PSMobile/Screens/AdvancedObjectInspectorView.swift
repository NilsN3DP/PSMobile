import SwiftUI
import PSMShared

/// Der Rahmen der unmittelbar benötigten Inspector-Aktionen.
///
/// Die Seitenleiste entscheidet damit erst nach dem echten Layout, ob
/// sie überhaupt scrollen muss.
struct AdvancedInspectorSichtbereichPreferenceKey: PreferenceKey {
    static var defaultValue: CGRect = .null

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        let neu = nextValue()
        if !neu.isNull { value = neu }
    }
}

/// Der Objektinspektor im Advanced Mode - Gegenstueck zu
/// `ObjectPanel.kt`.
///
/// Alles, was man mit einem einzelnen Objekt tut, an einer Stelle:
/// Griffe waehlen, Groesse und Drehung in Zahlen, Ablegen, Einpassen,
/// Spiegeln, Kopien. Im Simple Mode gibt es davon eine knappe Auswahl
/// als schwebende Leiste; hier steht das Ganze.
///
/// Die Zahlenfelder sind bewusst da, wo die Gizmos schon sind: mit dem
/// Finger stellt man ungefaehr ein, mit einer Zahl genau. Beides
/// braucht man, oft nacheinander.
struct AdvancedObjectInspectorView: View {

    @ObservedObject var model: SlicerModel
    let objekt: PsmCore.ObjectInfo
    @Binding var gizmo: PsmViewport.Gizmo

    @Environment(\.psScale) private var ps
    @State private var zeigeSchichten = false
    /// Was das letzte Vereinfachen oder Zerlegen ergeben hat.
    @State private var vereinfachtText = ""
    @State private var zeigeTeilBlatt = false

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            VStack(alignment: .leading, spacing: ps.pt(10)) {
                // Die Griffe stehen jetzt oben in der Werkzeugleiste, bei
                // Ansicht und Vorschau: sie bestimmen, was ein Finger im
                // Viewport tut, und das ist keine Zahleneinstellung.
                groesse
                Divider().background(PrusaColors.divider)
                drehung
                Divider().background(PrusaColors.divider)
                handgriffe
                kopien
                if model.beds.count > 1 { bettwechsel }
                Divider().background(PrusaColors.divider)
                schichtenKnopf
            }
            .background {
                GeometryReader { geo in
                    Color.clear.preference(
                        key: AdvancedInspectorSichtbereichPreferenceKey.self,
                        value: geo.frame(in: .global))
                }
            }
            .id("advanced.bearbeiten.aktionen")
            Divider().background(PrusaColors.divider)
            geometrie
            Divider().background(PrusaColors.divider)
            teile
            // Eine Marke, kein Bezeichner am Stapel: SwiftUI vererbt den
            // an jedes Kind und ueberschreibt deren eigene. Genau daran
            // sind heute schon zwei Bildschirme gescheitert.
            PSMarke(name: "advanced.objectTree")
        }
    }

    private var schichtenKnopf: some View {
        Button { zeigeSchichten = true } label: {
            Text(PsUiCatalog.tr("Variable layer height"))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("advanced.schichten")
        .sheet(isPresented: $zeigeSchichten) {
            LayerProfileView(model: model, objekt: objekt) { zeigeSchichten = false }
        }
    }

    // MARK: - Griffe

    /// Welche Griffe am Objekt haengen. Wie am Desktop die Gizmo-Leiste
    /// links, hier als Zeile - auf einem Tablet ist waagerecht billiger
    /// als eine zweite senkrechte Leiste.
    // MARK: - Groesse

    /// Prozent und Millimeter nebeneinander: am Modell denkt man in
    /// Prozent, beim Einpassen aufs Bett in Millimetern.
    private var groesse: some View {
        HStack(spacing: ps.pt(8)) {
            beschriftung(PsUiCatalog.tr("Scale"))
            zahlenfeld(wert: String(format: "%.1f", objekt.scale.x * 100),
                       einheit: "%", kennung: "advanced.scale.prozent") { text in
                if let p = Float(text.replacingOccurrences(of: ",", with: ".")), p > 0 {
                    model.setUniformScale(objekt.id, p / 100)
                }
            }
            zahlenfeld(wert: String(format: "%.1f", max(objekt.sizeMm.x,
                                                        max(objekt.sizeMm.y, objekt.sizeMm.z))),
                       einheit: "mm", kennung: "advanced.scale.mm") { text in
                if let mm = Float(text.replacingOccurrences(of: ",", with: ".")), mm > 0 {
                    model.scaleToSize(objekt.id, mm)
                }
            }
        }
    }

    // MARK: - Drehung

    private var drehung: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            HStack(spacing: ps.pt(6)) {
                beschriftung(PsUiCatalog.tr("Rotation"))
                ForEach(Array(["X", "Y", "Z"].enumerated()), id: \.offset) { achse, name in
                    // Der Kern rechnet in Radiant, gelesen wird in Grad -
                    // niemand dreht ein Modell um 1,5708.
                    let grad = objekt.rotation[achse] * 180 / .pi
                    zahlenfeld(wert: String(Int(grad.rounded())),
                               einheit: name,
                               kennung: "advanced.rotate." + name) { text in
                        if let g = Float(text.replacingOccurrences(of: ",", with: ".")) {
                            model.setRotationAxis(objekt.id, Int(achse), grad: g)
                        }
                    }
                }
            }
            // Vierteldrehungen sind der haeufigste Fall und mit dem Finger
            // sonst nicht genau zu treffen.
            HStack(spacing: ps.pt(6)) {
                Spacer().frame(width: ps.pt(64))
                knopf("↺ 90°", kennung: "advanced.drehen.links") {
                    model.rotateBy(objekt.id, achse: 2, grad: -90)
                }
                knopf("↻ 90°", kennung: "advanced.drehen.rechts") {
                    model.rotateBy(objekt.id, achse: 2, grad: 90)
                }
                knopf("180°", kennung: "advanced.drehen.halb") {
                    model.rotateBy(objekt.id, achse: 2, grad: 180)
                }
            }
        }
    }

    // MARK: - Handgriffe

    private var handgriffe: some View {
        VStack(spacing: ps.pt(6)) {
            HStack(spacing: ps.pt(6)) {
                knopf(PsUiCatalog.tr("Place on bed"), kennung: "advanced.ablegen") {
                    model.dropToBed(objekt.id)
                }
                knopf(st("Fit to bed", "Aufs Bett einpassen"), kennung: "advanced.einpassen") {
                    model.fitToBed(objekt.id)
                }
            }
            HStack(spacing: ps.pt(6)) {
                ForEach(Array(["X", "Y", "Z"].enumerated()), id: \.offset) { achse, name in
                    knopf(st("Mirror ", "Spiegeln ") + name,
                          kennung: "advanced.spiegeln." + name) {
                        model.mirror(objekt.id, axis: Int32(achse))
                    }
                }
            }
        }
    }

    private var kopien: some View {
        HStack(spacing: ps.pt(6)) {
            beschriftung(st("Copies", "Kopien"))
            knopf("−", kennung: "advanced.kopien.weniger") {
                if objekt.instances > 1 {
                    model.setInstances(objekt.id, count: Int32(objekt.instances - 1))
                }
            }
            Text("\(objekt.instances)")
                .font(.system(size: ps.font(14)))
                .foregroundStyle(PrusaColors.textPrimary)
                .frame(width: ps.pt(44))
                .accessibilityIdentifier("advanced.kopien.anzahl")
            knopf("+", kennung: "advanced.kopien.mehr") {
                model.setInstances(objekt.id, count: Int32(objekt.instances + 1))
            }
            Spacer()
        }
    }

    private var bettwechsel: some View {
        Menu {
            ForEach(model.beds.filter { !$0.active }, id: \.index) { bett in
                Button(st("Bed ", "Bett ") + "\(bett.index + 1) · \(bett.objectCount)") {
                    model.moveToBed([objekt.id], target: bett.index)
                }
            }
        } label: {
            Text(st("Move to another bed", "Auf anderes Bett verschieben"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textPrimary)
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        }
        .accessibilityIdentifier("advanced.bettwechsel")
    }

    // MARK: - Teile

    /// Der Objektbaum: aus wie vielen Koerpern besteht das Objekt, und
    /// welcher Extruder druckt welchen. Bei einem Extruder ist die
    /// Zuweisung sinnlos und bleibt weg.
    /// Was an der Geometrie selbst geaendert wird.
    ///
    /// Vereinfachen meldet zurueck, was es gebracht hat: ohne die beiden
    /// Zahlen tippt man darauf und weiss nicht, ob etwas passiert ist.
    private var geometrie: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            abschnitt(st("Geometry", "Geometrie"))
            if !vereinfachtText.isEmpty {
                Text(vereinfachtText)
                    .font(.system(size: ps.font(10)))
                    .foregroundStyle(PrusaColors.orange)
            }
            HStack(spacing: ps.pt(8)) {
                Button {
                    if let e = model.simplify(objekt.id, ratio: 0.5) {
                        vereinfachtText = "\(e.before) → \(e.after)"
                    }
                } label: {
                    Text(st("Simplify by half", "Auf die Hälfte"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("advanced.vereinfachen")
                Button {
                    let n = model.splitVolumes(objekt.id)
                    vereinfachtText = st("Parts", "Teile") + ": \(n)"
                } label: {
                    Text(st("Split into parts", "In Teile zerlegen"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("advanced.zerlegen")
            }
            // Aussparung, Modifier, Stuetzenblocker: das, wofuer man
            // sonst das Programm wechselt.
            Button { zeigeTeilBlatt = true } label: {
                Text(st("Add part", "Teil hinzufügen"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.orange)
                    .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("advanced.teilHinzufuegen")
            .sheet(isPresented: $zeigeTeilBlatt) {
                TeilHinzufuegenView(model: model, objektId: objekt.id) {
                    zeigeTeilBlatt = false
                }
            }
        }
    }

    private var teile: some View {
        let anzahl = model.volumeCount(objekt.id)
        return VStack(alignment: .leading, spacing: ps.pt(6)) {
            abschnitt(st("Parts", "Teile") + " (\(anzahl))")
            ForEach(0..<anzahl, id: \.self) { i in
                if let teil = model.volumeInfo(objekt.id, at: i) {
                    HStack(spacing: ps.pt(8)) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(teil.name.isEmpty ? "\(i + 1)" : teil.name)
                                .font(.system(size: ps.font(13)))
                                .foregroundStyle(PrusaColors.textPrimary)
                                .lineLimit(1)
                            Text(teilArt(teil.type) + " · \(teil.triangles)")
                                .font(.system(size: ps.font(10)))
                                .foregroundStyle(PrusaColors.textMuted)
                        }
                        Spacer()
                        if model.extruderCount > 1 {
                            Menu {
                                // Null heisst: der Extruder des Objekts.
                                Button(st("Inherited", "Geerbt")) {
                                    model.setVolumeExtruder(objekt.id, at: i, 0)
                                }
                                ForEach(1...model.extruderCount, id: \.self) { nummer in
                                    Button("\(nummer)") {
                                        model.setVolumeExtruder(objekt.id, at: i, Int32(nummer))
                                    }
                                }
                            } label: {
                                Text(teil.explicitExtruder == 0
                                     ? st("Inherited", "Geerbt")
                                     : "\(teil.explicitExtruder)")
                                    .font(.system(size: ps.font(12)))
                                    .foregroundStyle(PrusaColors.orange)
                                    .padding(.horizontal, ps.pt(10))
                                    .frame(height: ps.touch(40))
                                    .background(PrusaColors.panelRaised)
                                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                            }
                            .accessibilityIdentifier("advanced.teil.\(i).extruder")
                        }
                        // Der Modellkoerper bleibt: nimmt man ihn weg,
                        // bleibt ein Objekt ohne Geometrie zurueck.
                        // Alles andere ist ein Zusatz und darf wieder weg.
                        if teil.type != 0 {
                            Button {
                                model.removeVolume(objekt.id, at: i)
                            } label: {
                                Text(st("Remove", "Entfernen"))
                                    .font(.system(size: ps.font(12)))
                                    .foregroundStyle(PrusaColors.danger)
                                    .padding(.horizontal, ps.pt(10))
                                    .frame(height: ps.touch(40))
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("advanced.teil.\(i).entfernen")
                        }
                    }
                    .padding(.vertical, ps.pt(2))
                }
            }
        }
    }

    /// Die Arten aus dem C-ABI. Ein Modifikator sieht im Baum aus wie
    /// ein Teil, druckt aber nichts - das muss dranstehen.
    private func teilArt(_ typ: Int32) -> String {
        switch typ {
        case 0:  return st("Part", "Teil")
        case 1:  return st("Negative", "Aussparung")
        case 2:  return st("Modifier", "Modifikator")
        case 3:  return st("Support blocker", "Stützensperre")
        case 4:  return st("Support enforcer", "Stützenzwang")
        default: return "?"
        }
    }

    // MARK: - Bausteine

    private func abschnitt(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: ps.font(11), weight: .semibold))
            .foregroundStyle(PrusaColors.textMuted)
    }

    private func beschriftung(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(12)))
            .foregroundStyle(PrusaColors.textMuted)
            .frame(width: ps.pt(64), alignment: .leading)
    }

    private func wahl(_ label: String,
                      an: Bool,
                      kennung: String,
                      aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(13), weight: an ? .semibold : .regular))
                .foregroundStyle(an ? .white : PrusaColors.textPrimary)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(an ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(9)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func knopf(_ label: String,
                       kennung: String,
                       aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textPrimary)
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func zahlenfeld(wert: String,
                            einheit: String,
                            kennung: String,
                            uebernehmen: @escaping (String) -> Void) -> some View {
        ZahlenFeld(wert: wert, einheit: einheit, uebernehmen: uebernehmen)
            .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Ein Zahlenfeld, das erst beim Verlassen uebernimmt.
///
/// Bei jedem Tastendruck zu uebernehmen waere hier falsch: aus "12" wird
/// beim Tippen von "125" kurz die 12, und das Modell springt zweimal.
/// Ausserdem stuende nach dem Loeschen des letzten Zeichens eine Null da.
private struct ZahlenFeld: View {

    let wert: String
    let einheit: String
    let uebernehmen: (String) -> Void

    @Environment(\.psScale) private var ps
    @State private var text: String = ""
    @FocusState private var amZug: Bool

    var body: some View {
        HStack(spacing: ps.pt(4)) {
            TextField("", text: $text)
                .keyboardType(.numbersAndPunctuation)
                .focused($amZug)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textPrimary)
                .onSubmit { uebernehmen(text) }
            Text(einheit)
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
        }
        .padding(.horizontal, ps.pt(8))
        .frame(height: ps.touch(44))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        .onAppear { text = wert }
        .onChange(of: wert) { neu in
            // Waehrend jemand tippt, nicht dazwischenfunken.
            if !amZug { text = neu }
        }
        .onChange(of: amZug) { hatFokus in
            if !hatFokus { uebernehmen(text) }
        }
    }
}
