import SwiftUI
import PSMShared

/// Die Extruderbank — Gegenstück zu `ExtruderBank` auf Android.
///
/// Bei einem Extruder ist „das Material" eine Frage. Ab zwei ist es eine
/// je Position, und dann braucht man zwei Dinge auf einen Blick: welche
/// Farbe auf welchem Kopf liegt, und welches Material.
///
/// T1–T8 nebeneinander, weil PrusaSlicer sie so nummeriert. Darunter die
/// Wahl für den angetippten Kopf — nicht acht Auswahlen untereinander,
/// das wäre eine Wand aus Feldern, von denen sieben gerade niemanden
/// interessieren.
struct ExtruderBank: View {

    @ObservedObject var model: SlicerModel
    /// Öffnet die Materialauswahl für diesen Kopf.
    var onMaterial: (Int) -> Void

    @Environment(\.psScale) private var ps
    @State private var gewaehlt = 0
    @State private var zeigeFarbe = false
    @State private var farbtext = ""

    private var anzahl: Int { model.extruderCount }

    @State private var turmX: Float = 0
    @State private var turmY: Float = 0
    @State private var turmDrehung: Float = 0
    @State private var turmGeladen = false

    var body: some View {
        if anzahl > 1 {
            VStack(alignment: .leading, spacing: ps.pt(8)) {
                Text(st("Choose T1–T8 · colour and material per tool",
                        "T1–T8 auswählen · Farbe und Material je Werkzeug"))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                koepfe
                wahl
                reinigungsturm
            }
            .sheet(isPresented: $zeigeFarbe) { farbblatt }
            .onAppear {
                guard !turmGeladen, let turm = model.wipeTower() else { return }
                turmGeladen = true
                turmX = turm.x
                turmY = turm.y
                turmDrehung = turm.rotationDeg
            }
        }
    }

    /// Position und Drehung des Reinigungsturms - nur bei mehreren
    /// Extrudern ueberhaupt relevant, deshalb hier statt in den
    /// allgemeinen Druckeinstellungen.
    private var reinigungsturm: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(st("Wipe tower", "Reinigungsturm"))
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.top, ps.pt(6))
            turmZeile(st("X position", "X-Position"), wert: $turmX)
            turmZeile(st("Y position", "Y-Position"), wert: $turmY)
            turmZeile(st("Rotation", "Drehung"), wert: $turmDrehung, einheit: "°")
        }
    }

    private func turmZeile(_ titel: String, wert: Binding<Float>,
                           einheit: String = "mm") -> some View {
        Stepper(value: Binding(
            get: { Double(wert.wrappedValue) },
            set: { neu in
                wert.wrappedValue = Float(neu)
                model.setWipeTower(x: turmX, y: turmY, rotationDeg: turmDrehung)
            }
        ), in: einheit == "°" ? -360...360 : -1000...1000, step: einheit == "°" ? 5 : 1) {
            HStack {
                Text(titel)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Spacer()
                Text(String(format: "%.0f", wert.wrappedValue) + " " + einheit)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .monospacedDigit()
            }
        }
        .accessibilityIdentifier("extruder.turm." + titel)
    }

    /// Die Köpfe, höchstens acht je Zeile — dieselbe Aufteilung wie auf
    /// Android.
    private var koepfe: some View {
        let spalten = min(anzahl, 8)
        return LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: ps.pt(6)),
                           count: spalten),
            spacing: ps.pt(6)
        ) {
            ForEach(0..<anzahl, id: \.self) { index in
                let aktiv = index == gewaehlt
                Button { gewaehlt = index } label: {
                    VStack(spacing: ps.pt(3)) {
                        Text("T\(index + 1)")
                            .font(.system(size: ps.font(11), weight: .semibold))
                            .foregroundStyle(aktiv ? PrusaColors.orange
                                                   : PrusaColors.textPrimary)
                        RoundedRectangle(cornerRadius: ps.pt(2))
                            .fill(Color(hexString: model.extruderColor(index))
                                  ?? PrusaColors.panelRaised)
                            .frame(height: ps.pt(12))
                            .overlay(
                                RoundedRectangle(cornerRadius: ps.pt(2))
                                    .stroke(PrusaColors.divider, lineWidth: 1)
                            )
                    }
                    .padding(.horizontal, ps.pt(5))
                    .frame(height: ps.touch(52))
                    .frame(maxWidth: .infinity)
                    .background(aktiv ? PrusaColors.panelRaised : PrusaColors.panel)
                    .overlay(
                        RoundedRectangle(cornerRadius: ps.pt(8))
                            .stroke(aktiv ? PrusaColors.orange : PrusaColors.divider,
                                    lineWidth: aktiv ? 2 : 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("extruder.kopf.\(index)")
            }
        }
    }

    /// Material und Farbe für den angetippten Kopf.
    private var wahl: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Button { onMaterial(gewaehlt) } label: {
                HStack(spacing: ps.pt(8)) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(EasyModeState.shared.profileDisplayLabel(
                            rawPreset: model.extruderFilament(gewaehlt)))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .lineLimit(1)
                        Text(st("Choose spool & material", "Spule & Material wählen"))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    Spacer(minLength: 0)
                    Text("›")
                        .font(.system(size: ps.font(16)))
                        .foregroundStyle(PrusaColors.orange)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(minHeight: ps.touch(52))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("extruder.material.\(gewaehlt)")

            Button {
                farbtext = model.extruderColor(gewaehlt)
                zeigeFarbe = true
            } label: {
                HStack(spacing: ps.pt(8)) {
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .fill(Color(hexString: model.extruderColor(gewaehlt))
                              ?? PrusaColors.panelRaised)
                        .frame(width: ps.pt(24), height: ps.pt(24))
                        .overlay(
                            RoundedRectangle(cornerRadius: ps.pt(3))
                                .stroke(PrusaColors.divider, lineWidth: 1)
                        )
                    Text(st("Colour", "Farbe"))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer(minLength: 0)
                    Text(model.extruderColor(gewaehlt))
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("extruder.farbe.\(gewaehlt)")
        }
    }

    /// Farbe wählen: eine Reihe gängiger Farben und ein Feld für den
    /// eigenen Wert.
    ///
    /// Kein Farbkreis: der Wert landet als Hex im Profil, und wer eine
    /// bestimmte Rolle abbilden will, hat ihren Hex-Wert meist zur Hand.
    /// Die Reihe deckt den Rest ab.
    private var farbblatt: some View {
        let gaengig = ["#FF8000", "#E53935", "#FDD835", "#43A047", "#1E88E5",
                       "#8E24AA", "#795548", "#000000", "#FFFFFF", "#9E9E9E"]
        return VStack(alignment: .leading, spacing: ps.pt(14)) {
            Text(st("Colour", "Farbe") + " · T\(gewaehlt + 1)")
                .font(.system(size: ps.font(17), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: ps.pt(8)),
                                     count: 5),
                      spacing: ps.pt(8)) {
                ForEach(gaengig, id: \.self) { hex in
                    Button { farbtext = hex } label: {
                        RoundedRectangle(cornerRadius: ps.pt(4))
                            .fill(Color(hexString: hex) ?? PrusaColors.panelRaised)
                            .frame(height: ps.touch(48))
                            .overlay(
                                RoundedRectangle(cornerRadius: ps.pt(4))
                                    .stroke(farbtext.uppercased() == hex
                                            ? PrusaColors.orange : PrusaColors.divider,
                                            lineWidth: farbtext.uppercased() == hex ? 3 : 1)
                            )
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("farbe." + hex)
                }
            }

            TextField(st("Custom value, e.g. #3399FF", "Eigener Wert, z. B. #3399FF"),
                      text: $farbtext)
                .font(.system(size: ps.font(14)))
                .autocorrectionDisabled()
                .textInputAutocapitalization(.characters)
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .accessibilityIdentifier("farbe.eigener")

            HStack(spacing: ps.pt(12)) {
                Button(st("Cancel", "Abbrechen")) { zeigeFarbe = false }
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(minHeight: ps.touch(48))
                Spacer()
                Button {
                    // Nur was der gemeinsame Katalog als Farbe erkennt -
                    // sonst stünde im Profil ein Wert, den kein Slicer
                    // lesen kann.
                    let sauber = FilamentCatalog.shared.normalizeColor(value: farbtext)
                    if !sauber.isEmpty { model.setExtruderColor(gewaehlt, sauber) }
                    zeigeFarbe = false
                } label: {
                    Text(st("Apply", "Übernehmen"))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(20))
                        .frame(height: ps.touch(48))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("farbe.uebernehmen")
            }
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
    }
}
