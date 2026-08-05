import SwiftUI
import PSMShared

/// Die gemeinsame Huelle fuer Entscheidungen, die den aktuellen
/// Arbeitskontext nicht verlassen duerfen.
struct SchwebenderDialog<Inhalt: View>: View {

    let kennung: String
    let maximaleBreite: CGFloat
    let inhalt: Inhalt

    @Environment(\.psScale) private var ps

    init(kennung: String,
         maximaleBreite: CGFloat,
         @ViewBuilder inhalt: () -> Inhalt) {
        self.kennung = kennung
        self.maximaleBreite = maximaleBreite
        self.inhalt = inhalt()
    }

    private var rand: CGFloat { max(ps.pt(12), 12) }
    private var breite: CGFloat { max(0, ps.windowSize.width - 2 * rand) }
    private var hoehe: CGFloat { max(0, ps.windowSize.height - 2 * rand) }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()

            inhalt
                .frame(maxWidth: min(maximaleBreite, breite), maxHeight: hoehe)
                .background(PrusaColors.panel)
                .background {
                    Color.clear
                        .accessibilityElement()
                        .accessibilityIdentifier(kennung)
                        .allowsHitTesting(false)
                }
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
                .overlay(RoundedRectangle(cornerRadius: ps.pt(8))
                    .stroke(PrusaColors.divider, lineWidth: 1))
                .shadow(radius: 20)
                .padding(rand)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Was mit geänderten Profilwerten passiert, wenn man in den Einfachen
/// Modus wechselt.
///
/// Der Einfache Modus arbeitet auf dem Profil und ändert nur, was dort
/// auch wählbar ist. Wer im Expertenmodus an der Schichthöhe gedreht
/// hat, würde diese Änderung beim Wechsel stillschweigend verlieren —
/// und genau das ist der Fehler, den man erst drei Drucke später
/// bemerkt.
///
/// Deshalb dasselbe Vorgehen wie am Desktop: die geänderten Werte
/// auflisten, mit Vorher und Jetzt, und die Entscheidung dem Nutzer
/// lassen. PrusaSlicers Dialog heißt „Switching Presets: Unsaved
/// Changes"; unsere Knöpfe sind andere, weil unsere Frage eine andere
/// ist.
///
/// Fünf Wege, jeder als eigene Zeile mit einem Satz Erklärung. Auf
/// einem Tablet sind fünf kleine Knöpfe nebeneinander unbedienbar, und
/// eine Entscheidung ohne Erklärung trifft man einmal falsch und
/// danach gar nicht mehr.
struct ProfilWechselDialog: View {

    let aenderungen: [SlicerModel.Profilaenderung]
    var onVerwerfen: () -> Void
    var onNeuesProfil: (String) -> Void
    var onUeberschreiben: () -> Void
    var onInsProjekt: () -> Void
    var onAbbrechen: () -> Void

    @Environment(\.psScale) private var ps
    @State private var zeigeNamensfeld = false
    @State private var name = ""

    private var niedrigeHoehe: Bool { ps.windowSize.height < 600 }

    var body: some View {
        SchwebenderDialog(kennung: "dialog.profilwechsel", maximaleBreite: ps.pt(680)) {
            if niedrigeHoehe {
                ScrollView { dialogInhalt }
            } else {
                dialogInhalt
            }
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "profilwechsel") }
    }

    private var dialogInhalt: some View {
        VStack(alignment: .leading, spacing: ps.pt(14)) {
            kopf
            tabelle
            if zeigeNamensfeld { namensfeld } else { wege }
        }
        .padding(ps.pt(20))
    }

    private var kopf: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(st("Unsaved profile changes", "Ungespeicherte Profiländerungen"))
                .font(.system(size: ps.font(19), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st("The simple mode works with the values from the profile. These changes are not adjustable there:",
                    "Der Einfache Modus arbeitet mit den Werten des Profils. Diese Änderungen lassen sich dort nicht einstellen:"))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Drei Spalten wie am Desktop: was, vorher, jetzt. Ohne die beiden
    /// Werte ist die Liste nur eine Anklage ohne Beweis.
    private var tabelle: some View {
        VStack(spacing: 0) {
            HStack {
                Text(st("Setting", "Einstellung"))
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(st("Before", "Vorher"))
                    .frame(width: ps.pt(90), alignment: .trailing)
                Text(st("Now", "Jetzt"))
                    .frame(width: ps.pt(90), alignment: .trailing)
            }
            .font(.system(size: ps.font(10), weight: .semibold))
            .foregroundStyle(PrusaColors.textMuted)
            .padding(.horizontal, ps.pt(10))
            .padding(.vertical, ps.pt(6))

            Divider().overlay(PrusaColors.divider)

            ScrollView {
                VStack(spacing: 0) {
                    ForEach(aenderungen) { a in
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(a.bezeichnung)
                                    .font(.system(size: ps.font(12)))
                                    .foregroundStyle(PrusaColors.textPrimary)
                                    .lineLimit(1)
                                Text(bereich(a.art))
                                    .font(.system(size: ps.font(9)))
                                    .foregroundStyle(PrusaColors.textMuted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Text(kurz(a.vorher))
                                .font(.system(size: ps.font(11)))
                                .foregroundStyle(PrusaColors.textMuted)
                                .frame(width: ps.pt(90), alignment: .trailing)
                            Text(kurz(a.jetzt))
                                .font(.system(size: ps.font(11), weight: .semibold))
                                .foregroundStyle(PrusaColors.orange)
                                .frame(width: ps.pt(90), alignment: .trailing)
                        }
                        .padding(.horizontal, ps.pt(10))
                        .frame(minHeight: ps.touch(40))
                    }
                }
            }
            .frame(maxHeight: ps.pt(260))
        }
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
        .accessibilityIdentifier("profilwechsel.liste")
    }

    private var wege: some View {
        VStack(spacing: ps.pt(8)) {
            weg(st("Save as new profile", "Als neues Profil sichern"),
                st("The changes stay, under a name of your choosing.",
                   "Die Änderungen bleiben erhalten, unter einem Namen deiner Wahl."),
                "profilwechsel.neu", betont: true) {
                    name = ""
                    zeigeNamensfeld = true
                }
            weg(st("Overwrite the profile", "Profil überschreiben"),
                st("The selected profile takes over these values permanently.",
                   "Das gewählte Profil übernimmt diese Werte dauerhaft."),
                "profilwechsel.ueberschreiben", aktion: onUeberschreiben)
            weg(st("Keep in this project only", "Nur in diesem Projekt behalten"),
                st("The profile stays untouched; the values travel with the project file.",
                   "Das Profil bleibt unberührt, die Werte reisen mit der Projektdatei."),
                "profilwechsel.projekt", aktion: onInsProjekt)
            weg(st("Discard changes", "Änderungen verwerfen"),
                st("Back to the values of the profile.",
                   "Zurück auf die Werte des Profils."),
                "profilwechsel.verwerfen", gefahr: true, aktion: onVerwerfen)
            weg(st("Cancel", "Abbrechen"),
                st("Stay in the expert mode.", "Im Expertenmodus bleiben."),
                "profilwechsel.abbrechen", aktion: onAbbrechen)
        }
    }

    private var namensfeld: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            Text(st("Name of the new profile", "Name des neuen Profils"))
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
            TextField("", text: $name)
                .textFieldStyle(.plain)
                .font(.system(size: ps.font(15)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(ps.pt(12))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .autocorrectionDisabled()
                .accessibilityIdentifier("profilwechsel.name")
            HStack(spacing: ps.pt(10)) {
                Button(st("Back", "Zurück")) { zeigeNamensfeld = false }
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(minHeight: ps.touch(48))
                Spacer()
                Button { onNeuesProfil(name) } label: {
                    Text(st("Save", "Sichern"))
                        .font(.system(size: ps.font(14), weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(20))
                        .frame(height: ps.touch(48))
                        .background(name.isEmpty ? PrusaColors.panelRaised : PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(name.isEmpty)
                .accessibilityIdentifier("profilwechsel.sichern")
            }
        }
    }

    private func weg(_ titel: String,
                     _ erklaerung: String,
                     _ kennung: String,
                     betont: Bool = false,
                     gefahr: Bool = false,
                     aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(titel)
                    .font(.system(size: ps.font(14), weight: betont ? .semibold : .regular))
                    .foregroundStyle(gefahr ? PrusaColors.danger
                                     : betont ? PrusaColors.orange : PrusaColors.textPrimary)
                Text(erklaerung)
                    .font(.system(size: ps.font(10)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, ps.pt(14))
            .padding(.vertical, ps.pt(9))
            .frame(minHeight: ps.touch(56))
            .background(PrusaColors.panelRaised)
            .overlay(RoundedRectangle(cornerRadius: ps.pt(4))
                .stroke(betont ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func bereich(_ art: PsmCore.PresetType) -> String {
        switch art {
        case .print:    return PsUiCatalog.tr("Print Settings")
        case .filament: return PsUiCatalog.tr("Filament")
        case .printer:  return PsUiCatalog.tr("Printer")
        }
    }

    /// Lange Werte abschneiden. Ein eigener G-Code steht sonst als
    /// Romanabsatz in einer Tabellenzelle.
    private func kurz(_ text: String) -> String {
        let eine = text.split(separator: "\n").first.map(String.init) ?? text
        return eine.count > 12 ? String(eine.prefix(11)) + "…" : eine
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
