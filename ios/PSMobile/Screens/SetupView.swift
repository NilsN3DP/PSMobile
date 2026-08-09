import SwiftUI
import PSMShared

/// Ersteinrichtung - Gegenstueck zum Konfigurationsassistenten des
/// Desktops und zu SetupScreen.kt auf Android.
///
/// Der Zweck ist nicht Kosmetik: Werden alle 37 Druckermodelle
/// installiert, dauert der Start 13,5 s und die Auswahllisten enthalten
/// 5762 Filamente. Mit einem einzelnen gewaehlten Drucker sind es 1,9 s
/// und 189 Filamente.
///
/// Die Gruppierung nach Familien kommt aus dem gemeinsamen Modul
/// (E-13) - dieselbe Reihenfolge wie auf Android, dieselbe Regel fuer
/// Altgeraete am Ende.
struct SetupView: View {

    let models: [PsmCore.PrinterModel]
    let busy: Bool
    /// Bisher installierte Modelle, Format "vendor:model:variant".
    let preselected: Set<String>
    let onConfirm: ([String]) -> Void
    let onLanguageChange: (String) -> Void
    var onClose: (() -> Void)? = nil

    @Environment(\.psScale) private var ps
    @State private var selected: Set<String> = []
    @State private var query = ""
    @State private var expandedVendors: Set<String> = []
    @State private var expandedFamilies: Set<String> = []

    /// Ist es eng? Dann faellt Beiwerk weg, statt alles zu schrumpfen.
    /// Dieselbe Frage wie UiScale.density auf Android; die Schwelle
    /// stammt von dort.
    private var tight: Bool { ps.factor <= 0.8 }

    private var shown: [PsmCore.PrinterModel] {
        models.filter { m in
            // Hart aus, nicht nur standardmaessig versteckt: PSMobile
            // schneidet nur FFF (E-08), und das mitgelieferte
            // PrusaResearchSLA-Buendel ist zudem kaputt (siehe
            // build/scripts/stage-resources.sh). Ein Schalter, der zu
            // einem funktionsunfaehigen Drucker fuehrt, ist schlimmer
            // als gar keiner.
            !m.isSla &&
            (query.trimmingCharacters(in: .whitespaces).isEmpty ||
             m.name.localizedCaseInsensitiveContains(query))
        }
    }

    /// Eine Familie mit ihren Modellen.
    ///
    /// Die Regel im gemeinsamen Modul liefert Positionen statt Modelle -
    /// so ueberlebt sie die Bruecke, denn Kotlins Typparameter gehen nach
    /// Swift verloren. Hier zurueck zu den eigenen Objekten.
    private struct Familie: Identifiable {
        let name: String
        let isLegacy: Bool
        let modelle: [PsmCore.PrinterModel]
        var id: String { name }
    }

    /// Ein Hersteller mit seinen Familien.
    private struct Hersteller: Identifiable {
        let name: String
        let familien: [Familie]
        var anzahl: Int { familien.reduce(0) { $0 + $1.modelle.count } }
        var id: String { name }
    }

    /// Zwei Ebenen: Hersteller, darunter Familien.
    ///
    /// Beide Einteilungen kommen aus dem gemeinsamen Modul und beide
    /// aus PrusaSlicers eigenen Daten - der Hersteller aus dem
    /// Schluessel, die Familie aus der Vendor-Datei.
    private var hersteller: [Hersteller] {
        let liste = shown
        return PrinterGrouping.shared.groupByVendor(keys: liste.map { $0.key })
            .map { v in
                let modelle = v.indices.map { liste[$0.intValue] }
                let familien = PrinterGrouping.shared
                    .group(families: modelle.map { $0.family })
                    .map { g in
                        Familie(name: g.family,
                                isLegacy: g.isLegacy,
                                modelle: g.indices.map { modelle[$0.intValue] })
                    }
                return Hersteller(name: v.family, familien: familien)
            }
    }

    var body: some View {
        SchwebenderDialog(kennung: "dialog.einrichtung", maximaleBreite: ps.pt(760)) {
            VStack(alignment: .leading, spacing: ps.pt(tight ? 6 : 14)) {
                kopfzeile
                suchfeld
                liste
                abschluss
            }
            .padding(ps.pt(tight ? 12 : 24))
            .frame(maxHeight: .infinity)
        }
        .onAppear { selected = preselected }
    }

    // MARK: - Teile

    private var kopfzeile: some View {
        HStack(alignment: .center) {
            if let onClose {
                Button(action: onClose) {
                    Text("‹  " + SimpleModeState.shared.text(english: "Back", german: "Zurück"))
                        .font(.system(size: ps.font(15)))
                        .foregroundStyle(PrusaColors.orange)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("einrichtung.schliessen")
            }
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(SimpleModeState.shared.text(english: "Configuration Assistant",
                                                 german: "Ersteinrichtung"))
                    .font(.system(size: ps.font(tight ? 18 : 22), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)

                // Auf engen Schirmen faellt der Untertitel weg. Er
                // erklaert nichts, was die Liste darunter nicht selbst
                // zeigt, kostet aber eine Zeile, die dann der Liste
                // fehlt.
                if !tight {
                    Text(SimpleModeState.shared.text(
                            english: "Select all printers you want to use.",
                            german: "Waehle alle Drucker, die du nutzen willst."))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
            }
            Spacer()
        }
    }

    private var suchfeld: some View {
        // Der Platzhalter wird selbst gezeichnet. SwiftUI faerbt seinen
        // eigenen in einem Grau, das auf dem dunklen Feld praktisch
        // unsichtbar ist - das Feld sah aus wie ein leerer Kasten ohne
        // Hinweis, wozu er da ist.
        ZStack(alignment: .leading) {
            if query.isEmpty {
                Text(SimpleModeState.shared.text(english: "Search printer model",
                                                 german: "Druckermodell suchen"))
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .padding(.horizontal, ps.pt(12))
                    .allowsHitTesting(false)
            }
            TextField("", text: $query)
                .textFieldStyle(.plain)
                .font(.system(size: ps.font(15)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(ps.pt(12))
                .autocorrectionDisabled()
        }
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedCornerShape(ps.pt(6)))
    }

    private var liste: some View {
        let alle = hersteller
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(alle) { marke in
                    // Bei nur einem Hersteller waere die oberste Ebene
                    // eine Zeile, die man immer erst aufklappen muss,
                    // ohne dass sie etwas unterscheidet.
                    let offen = alle.count == 1
                        || expandedVendors.contains(marke.name)
                        || !query.isEmpty
                    if alle.count > 1 {
                        herstellerKopf(marke, offen: offen)
                    }
                    if offen {
                        ForEach(marke.familien) { gruppe in
                            familienKopf(gruppe)
                            if expandedFamilies.contains(gruppe.name) || !query.isEmpty {
                                ForEach(gruppe.modelle, id: \.key) { modell in
                                    modellZeile(modell)
                                }
                            }
                        }
                    }
                }
            }
        }
        .frame(maxHeight: .infinity)
    }

    private func herstellerKopf(_ marke: Hersteller, offen: Bool) -> some View {
        Button {
            if expandedVendors.contains(marke.name) {
                expandedVendors.remove(marke.name)
            } else {
                expandedVendors.insert(marke.name)
            }
        } label: {
            HStack {
                Text(marke.name)
                    .font(.system(size: ps.font(17), weight: .bold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text("\(marke.anzahl)")
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Image(systemName: offen ? "chevron.down" : "chevron.right")
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            .padding(.top, ps.pt(8))
            .frame(minHeight: ps.touch(52))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("hersteller.\(marke.name)")
    }

    private func familienKopf(_ gruppe: Familie) -> some View {
        Button {
            if expandedFamilies.contains(gruppe.name) {
                expandedFamilies.remove(gruppe.name)
            } else {
                expandedFamilies.insert(gruppe.name)
            }
        } label: {
            HStack {
                Text(gruppe.name.uppercased())
                    .font(.system(size: ps.font(14), weight: .semibold))
                    .padding(.leading, ps.pt(12))
                    .foregroundStyle(gruppe.isLegacy
                                     ? PrusaColors.textMuted : PrusaColors.orange)
                Text("\(gruppe.modelle.count)")
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Image(systemName: expandedFamilies.contains(gruppe.name)
                      ? "chevron.down" : "chevron.right")
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            .frame(minHeight: ps.touch(52))
            // Ohne das ist nur der Text antippbar, nicht die Zeile: der
            // Spacer dazwischen ist leerer Raum, und leeren Raum nimmt
            // SwiftUI von der Trefferpruefung aus. Wer auf die Mitte der
            // Zeile tippt, greift ins Leere - der UI-Test hat es gefunden,
            // ein Finger haette dasselbe erlebt.
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Kennungen, damit der UI-Test die Zeilen findet. Der sichtbare
        // Text taugt dafuer nicht: er ist uebersetzt und geht bei der
        // naechsten Sprachaenderung kaputt.
        .accessibilityIdentifier("familie.\(gruppe.name)")
    }

    private func modellZeile(_ modell: PsmCore.PrinterModel) -> some View {
        // Jede Duesengroesse ist eine eigene Wahl - PrusaSlicer fuehrt sie
        // als getrennte Profile, und wer eine 0.6er Duese hat, braucht
        // nicht die 0.4er Profile.
        HStack(alignment: .top, spacing: ps.pt(10)) {
            druckerBild(modell)

            VStack(alignment: .leading, spacing: ps.pt(4)) {
                Text(modell.name)
                    .font(.system(size: ps.font(tight ? 16 : 17)))
                    .foregroundStyle(PrusaColors.textPrimary)

                HStack(spacing: ps.pt(6)) {
                    ForEach(modell.variants, id: \.self) { variante in
                        let key = "\(modell.key):\(variante)"
                        Button {
                            if selected.contains(key) { selected.remove(key) }
                            else { selected.insert(key) }
                        } label: {
                            Text(variante)
                                .font(.system(size: ps.font(15)))
                                .foregroundStyle(selected.contains(key)
                                                 ? .white : PrusaColors.textMuted)
                                .padding(.horizontal, ps.pt(16))
                                .frame(minWidth: ps.touch(64),
                                       minHeight: ps.touch(tight ? 48 : 56))
                                .background(selected.contains(key)
                                            ? PrusaColors.orange : PrusaColors.panelRaised)
                                .clipShape(RoundedCornerShape(ps.pt(4)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("variante.\(key)")
                    }
                }
            }
        }
        .padding(.vertical, ps.pt(tight ? 10 : 14))
    }

    /// Vorschaubild je Druckermodell, wie in der Desktop-App - liegt als
    /// loses PNG in den gebuendelten Ressourcen (profiles/<vendor>/
    /// <model_id>_thumbnail.png), nicht im Asset-Katalog, deshalb ueber
    /// den Dateipfad statt per Image(_:)-Name geladen. Fehlt die Datei
    /// (z.B. bei einem Vendor ohne Vorschaubilder), bleibt ein
    /// Platzhalter-Symbol statt einer leeren Luecke.
    private func druckerBild(_ modell: PsmCore.PrinterModel) -> some View {
        let seite = ps.pt(tight ? 40 : 48)
        return Group {
            if let pfad = thumbnailPfad(modell), let bild = UIImage(contentsOfFile: pfad) {
                Image(uiImage: bild)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            } else {
                Image(systemName: "printer")
                    .font(.system(size: ps.font(18)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
        }
        .frame(width: seite, height: seite)
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedCornerShape(ps.pt(4)))
    }

    private func thumbnailPfad(_ modell: PsmCore.PrinterModel) -> String? {
        // modell.key hat die Form "vendor:model_id" - der Dateiname der
        // Vorschau folgt bei den meisten Herstellern genau der model_id
        // (dem [printer_model:XXX]-Abschnitt in der Vendor-INI). Voron
        // ist die Ausnahme: dort heisst die Datei z.B.
        // "Voron_v2_250_thumbnail.png" statt "V2_250_thumbnail.png" -
        // Vendor-Praefix und andere Gross-/Kleinschreibung. Deshalb erst
        // den genauen Namen versuchen, sonst im Vendor-Ordner nach einer
        // "*_thumbnail.png" suchen, die die model_id enthaelt.
        let teile = modell.key.split(separator: ":", maxSplits: 1)
        guard teile.count == 2 else { return nil }
        guard let resDir = Bundle.main.resourceURL?.appendingPathComponent("psresources") else {
            return nil
        }
        let vendorDir = resDir
            .appendingPathComponent("profiles")
            .appendingPathComponent(String(teile[0]))
        let genau = vendorDir.appendingPathComponent("\(teile[1])_thumbnail.png")
        if FileManager.default.fileExists(atPath: genau.path) {
            return genau.path
        }
        let modelId = String(teile[1]).lowercased()
        guard let dateien = try? FileManager.default.contentsOfDirectory(
            at: vendorDir, includingPropertiesForKeys: nil) else { return nil }
        return dateien.first {
            let n = $0.lastPathComponent.lowercased()
            return n.hasSuffix("_thumbnail.png") && n.contains(modelId)
        }?.path
    }

    private var abschluss: some View {
        Button {
            onConfirm(Array(selected))
        } label: {
            HStack {
                Spacer()
                if busy {
                    ProgressView().tint(.white)
                } else {
                    Text(SimpleModeState.shared.text(english: "Finish",
                                                     german: "Fertig"))
                        .font(.system(size: ps.font(16), weight: .semibold))
                }
                Spacer()
            }
            .frame(height: ps.touch(tight ? 46 : 58))
            .background(selected.isEmpty ? PrusaColors.panelRaised : PrusaColors.orange)
            .foregroundStyle(selected.isEmpty ? PrusaColors.textMuted : .white)
            .clipShape(RoundedCornerShape(ps.pt(6)))
        }
        .buttonStyle(.plain)
        .disabled(selected.isEmpty || busy)
        .accessibilityIdentifier("fertig")
    }
}

/// Abkuerzung, weil RoundedRectangle(cornerRadius:) sich hier zwanzigmal
/// wiederholt haette.
private func RoundedCornerShape(_ radius: CGFloat) -> RoundedRectangle {
    RoundedRectangle(cornerRadius: radius)
}
