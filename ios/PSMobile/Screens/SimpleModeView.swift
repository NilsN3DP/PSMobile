import SwiftUI
import UniformTypeIdentifiers
import PSMShared

/// Der Simple Mode - Gegenstueck zu `SimpleModeScreen.kt`.
///
/// Aufbau wie dort: der Arbeitsbereich fuellt den Bildschirm, Kopfzeile
/// und Werkzeugleiste liegen darueber, und ein angetipptes Werkzeug
/// oeffnet ein an der Leiste verankertes Panel statt eines vollflaechigen
/// Blatts. So bleibt das Druckbett sichtbar, waehrend man etwas
/// einstellt - darum geht es bei einem Slicer auf einem Tablet.
///
/// Beschriftungen, Panelfolge sowie Stuetzen- und Haftungslogik kommen
/// aus dem gemeinsamen Modul (E-13). Hier steht nur die Anordnung.
/// Eine unsichtbare Marke fuer die UI-Tests.
///
/// Der Bezeichner darf nicht am umgebenden Stapel haengen: SwiftUI
/// vererbt ihn an jedes Kind und ueberschreibt deren eigene Bezeichner.
/// Danach heisst jeder Knopf wie der Bildschirm, und kein Test findet
/// mehr etwas - aufgefallen ist das erst, als die Werkzeugleiste
/// unauffindbar war, obwohl sie auf dem Bild klar zu sehen ist.
struct PSMarke: View {
    let name: String
    var body: some View {
        Color.clear
            .frame(width: 1, height: 1)
            .accessibilityElement()
            .accessibilityIdentifier(name)
    }
}

struct SimpleModeView: View {

    @EnvironmentObject private var model: SlicerModel
    var onOpenAdvanced: () -> Void = {}
    var onOpenPrinterSetup: () -> Void = {}
    var onAppSettings: () -> Void = {}

    @Environment(\.psScale) private var ps
    @State private var panel: SimplePanel = .workspace
    @State private var zeigeImporter = false

    /// Auf schmalen Geraeten ruecken Kopfzeile und Leiste zusammen -
    /// dasselbe `compact` wie auf Android, nur aus der Skalierung
    /// abgeleitet statt aus screenWidthDp.
    private var kompakt: Bool { ps.factor <= 0.8 }

    var body: some View {
        ZStack(alignment: .top) {
            PrusaColors.background.ignoresSafeArea()
            arbeitsbereich
            VStack(spacing: 0) {
                kopfzeile
                werkzeugleiste
                Spacer()
            }
            if panel != .workspace { overlay }
            if panel == .workspace { modellKnopf }
            PSMarke(name: "simple.arbeitsbereich")
        }
        .fileImporter(isPresented: $zeigeImporter,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: false) { ergebnis in
            if case .success(let urls) = ergebnis, let u = urls.first { model.load(url: u) }
        }
    }

    // MARK: - Arbeitsbereich

    @ViewBuilder private var arbeitsbereich: some View {
        if let session = model.sessionHandle {
            ViewportView(
                session: session,
                shaderDir: model.shaderDir,
                selectedId: model.selectedId ?? -1,
                selectedIds: model.selectedId.map { [$0] } ?? [],
                invalidateKey: model.sceneRevision,
                // Bei offenem Panel darf der Viewport die Beruehrung nicht
                // schlucken: ein Tippen daneben soll das Panel schliessen,
                // nicht die Kamera drehen.
                inputEnabled: panel == .workspace,
                onSelect: { model.select($0 < 0 ? nil : $0) },
                onBlockedInput: { panel = .workspace }
            )
            .ignoresSafeArea()
        }
    }

    // MARK: - Kopfzeile

    private var kopfzeile: some View {
        HStack {
            Image(systemName: "person.crop.circle")
                .font(.system(size: ps.font(kompakt ? 24 : 28)))
                .foregroundStyle(PrusaColors.textMuted)
            Spacer()
            HStack(spacing: ps.pt(8)) {
                Text("S")
                    .font(.system(size: ps.font(kompakt ? 13 : 16), weight: .bold))
                    .foregroundStyle(PrusaColors.background)
                    .frame(width: ps.pt(kompakt ? 23 : 28), height: ps.pt(kompakt ? 23 : 28))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                VStack(alignment: .leading, spacing: 0) {
                    Text(SimpleModeState.shared.visibleBrand())
                        .font(.system(size: ps.font(kompakt ? 20 : 23)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    let drucker = SimpleModeState.shared.printerLabel(
                        rawPreset: model.selectedPreset(for: "printer") ?? "")
                    if !drucker.isEmpty {
                        Text(drucker)
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                }
            }
            Spacer()
            Image(systemName: "bell")
                .font(.system(size: ps.font(kompakt ? 24 : 28)))
                .foregroundStyle(PrusaColors.orange)
        }
        .padding(.horizontal, ps.pt(kompakt ? 16 : 20))
        .padding(.vertical, ps.pt(kompakt ? 6 : 14))
        .background(PrusaColors.background)
    }

    // MARK: - Werkzeugleiste

    /// Die vier Werkzeuge, die ein Panel oeffnen. Reihenfolge und
    /// Beschriftung stammen aus `toolbarLabels()`; welches Werkzeug
    /// welches Panel zeigt, gehoert zur Anordnung und steht hier.
    private static let panelZiele: [SimplePanel] = [.projects, .printer, .material, .settings]

    private var werkzeugleiste: some View {
        let labels = SimpleModeState.shared.toolbarLabels()
        return HStack(spacing: ps.pt(3)) {
            ForEach(Array(Self.panelZiele.enumerated()), id: \.offset) { index, ziel in
                werkzeug(labels[index], gewaehlt: panel == ziel, breite: ps.pt(kompakt ? 62 : 74)) {
                    // Ein zweites Tippen auf dasselbe Werkzeug schliesst
                    // das Panel wieder.
                    panel = (panel == ziel) ? .workspace : ziel
                }
            }
            Spacer(minLength: 0)
            werkzeug(labels[4], gewaehlt: false,
                     breite: ps.pt(kompakt ? 62 : 74), aktiv: druckbereit) { model.slice() }
            werkzeug(labels[5], gewaehlt: true,
                     breite: ps.pt(kompakt ? 84 : 100), aktiv: druckbereit) { model.slice() }
        }
        .padding(.horizontal, ps.pt(12))
        .padding(.vertical, ps.pt(kompakt ? 3 : 6))
    }

    private var druckbereit: Bool {
        !model.objects.isEmpty
            && !(model.selectedPreset(for: "printer") ?? "").isEmpty
            && !(model.selectedPreset(for: "filament") ?? "").isEmpty
    }

    private func werkzeug(_ label: String,
                          gewaehlt: Bool,
                          breite: CGFloat,
                          aktiv: Bool = true,
                          aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(werkzeugBeschriftung(label))
                .font(.system(size: ps.font(kompakt ? 9 : 10)))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .foregroundStyle(gewaehlt ? PrusaColors.background : PrusaColors.textPrimary)
                .frame(width: breite, height: ps.pt(kompakt ? 46 : 54))
                .background(gewaehlt ? PrusaColors.orange : PrusaColors.panel)
                .overlay(
                    RoundedRectangle(cornerRadius: ps.pt(1))
                        .stroke(gewaehlt ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1)
                )
                .opacity(aktiv ? 1 : 0.45)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!aktiv)
        .accessibilityIdentifier("simple.werkzeug.\(label)")
    }

    /// Dieselben Zeichen wie auf Android. Die Glyphen stehen bewusst
    /// nicht in der gemeinsamen Liste: dort steht, welche Werkzeuge es
    /// gibt, nicht wie sie aussehen.
    private func werkzeugBeschriftung(_ label: String) -> String {
        switch label {
        case "Projects": return "▣\n" + st("Projects", "Projekte")
        case "Printer":  return "▤\n" + st("Printer", "Drucker")
        case "Material": return "◎\n" + st("Material", "Material")
        case "Settings": return "☷\n" + st("Settings", "Einstell.")
        case "Preview":  return "▱\n" + st("Preview", "Vorschau")
        case "G-Code":   return "➤  G-Code"
        default:         return label
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }

    // MARK: - Modell hinzufuegen

    private var modellKnopf: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button { zeigeImporter = true } label: {
                    Text("＋ " + st("Add model", "Modell hinzufügen"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(.white)
                        .padding(.horizontal, ps.pt(16))
                        .frame(height: ps.touch(56))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(2)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("simple.modell")
            }
        }
        .padding(ps.pt(12))
    }

    // MARK: - Overlay

    private var overlay: some View {
        // Der abgedunkelte Arbeitsbereich ist zugleich die grosse,
        // touchfreundliche Schliessflaeche.
        // Die Abdunklung beginnt unter der Werkzeugleiste, nicht darueber:
        // sonst verschwindet die Beschriftung des gewaehlten Werkzeugs im
        // Schleier, und die orange Flaeche sieht aus wie ein leerer Kasten.
        ZStack(alignment: .top) {
            PrusaColors.background.opacity(0.72)
                .contentShape(Rectangle())
                .onTapGesture { panel = .workspace }

            VStack(alignment: .leading, spacing: 0) {
                Button {
                    panel = SimpleModeState.shared.backDestination(panel: panel)
                } label: {
                    Text("← " + st("Back", "Zurück"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(height: ps.touch(44), alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("simple.zurueck")

                ScrollView { inhalt.frame(maxWidth: .infinity, alignment: .leading) }
                    .frame(maxHeight: panelHoehe)
            }
            .padding(ps.pt(16))
            .frame(maxWidth: ps.pt(kompakt ? 420 : 620))
            .fixedSize(horizontal: false, vertical: true)
            .background(PrusaColors.panel)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
            .padding(.horizontal, ps.pt(12))
        }
        .padding(.top, ps.pt(kompakt ? 108 : 136))
        .ignoresSafeArea(edges: .bottom)
    }

    /// Was unterhalb der Werkzeugleiste uebrig bleibt, abzueglich des
    /// Zurueck-Knopfes und der Raender.
    private var panelHoehe: CGFloat {
        let oben = ps.pt(kompakt ? 108 : 136)
        let raender = ps.touch(44) + ps.pt(64)
        return max(ps.pt(220), ps.windowSize.height - oben - raender)
    }

    @ViewBuilder private var inhalt: some View {
        switch panel {
        case .projects:      projektePanel
        case .printer:       druckerPanel
        case .material:      materialPanel
        case .settings:      einstellungenPanel
        case .supports:      stuetzenPanel
        case .adhesion:      haftungPanel
        case .printSettings: druckprofilPanel
        default:             EmptyView()
        }
    }

    private func titel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(20)))
            .foregroundStyle(PrusaColors.textPrimary)
    }

    private func hinweis(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(12)))
            .foregroundStyle(PrusaColors.textMuted)
            .padding(.top, ps.pt(4))
            .padding(.bottom, ps.pt(10))
    }

    // MARK: - Projekte

    private var projektePanel: some View {
        let text = SimpleModeState.shared.projectSummaryCopy()
        let drucker = model.selectedPreset(for: "printer") ?? ""
        let material = model.selectedPreset(for: "filament") ?? ""
        return VStack(alignment: .leading, spacing: ps.pt(8)) {
            HStack {
                titel(st("PROJECTS", "PROJEKTE"))
                Spacer()
                Button { zeigeImporter = true } label: {
                    Text(st("Open model", "Modell öffnen"))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.orange)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            projektZeile(
                titel: text.title,
                drucker: drucker.isEmpty
                    ? text.noPrinter
                    : EasyModeState.shared.profileDisplayLabel(rawPreset: drucker),
                material: material.isEmpty
                    ? st("No material selected", "Material nicht gewählt")
                    : EasyModeState.shared.profileDisplayLabel(rawPreset: material),
                detail: text.session
            )
        }
    }

    private func projektZeile(titel: String,
                              drucker: String,
                              material: String,
                              detail: String) -> some View {
        HStack(alignment: .top, spacing: ps.pt(12)) {
            Text("▧")
                .font(.system(size: ps.font(30)))
                .foregroundStyle(PrusaColors.textMuted)
                .frame(width: ps.pt(70), height: ps.pt(70))
                .background(PrusaColors.background)
            VStack(alignment: .leading, spacing: ps.pt(3)) {
                Text(titel)
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.textPrimary)
                ForEach(["▤  " + drucker, "◎  " + material, "□  " + detail], id: \.self) { zeile in
                    Text(zeile)
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .lineLimit(1)
                }
            }
            Spacer()
        }
        .padding(ps.pt(12))
        .background(PrusaColors.panelRaised)
    }

    // MARK: - Drucker

    private var druckerPanel: some View {
        let modelle = EasyModeState.shared.printerModelsWithNozzles(
            rawPresets: model.presetNames(.printer))
        let gewaehlt = model.selectedPreset(for: "printer") ?? ""
        return VStack(alignment: .leading, spacing: ps.pt(10)) {
            titel(st("PRINTER", "DRUCKER"))
            hinweis(st("Choose a printer model · select the nozzle in the slice summary",
                       "Druckermodell wählen · die Düse wird in der Slice-Übersicht festgelegt"))
            if modelle.isEmpty {
                leeresPanel(st("No printer configured", "Noch kein Drucker eingerichtet"),
                            st("Set up printer", "Drucker einrichten"),
                            onOpenPrinterSetup)
            } else {
                ForEach(Array(modelle.enumerated()), id: \.offset) { _, modell in
                    ForEach(Array(modell.variants.enumerated()), id: \.offset) { _, wahl in
                        druckerKarte(modell: modell.label,
                                     duese: wahl.label,
                                     gewaehlt: wahl.rawPreset == gewaehlt) {
                            model.selectPreset(.printer, wahl.rawPreset)
                            panel = .workspace
                        }
                    }
                }
            }
        }
    }

    private func druckerKarte(modell: String,
                              duese: String,
                              gewaehlt: Bool,
                              aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(alignment: .leading, spacing: ps.pt(5)) {
                HStack(spacing: ps.pt(10)) {
                    Text("▤")
                        .font(.system(size: ps.font(26)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.pt(52), height: ps.pt(60))
                        .background(PrusaColors.panel)
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(modell)
                            .font(.system(size: ps.font(14)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .lineLimit(2)
                        Text(gewaehlt
                             ? st("SELECTED · OFFLINE", "AUSGEWÄHLT · OFFLINE")
                             : "OFFLINE")
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(gewaehlt ? PrusaColors.orange : PrusaColors.textMuted)
                    }
                    Spacer()
                }
                Divider().background(PrusaColors.divider)
                Text(st("Nozzle", "Düse") + "  " + duese)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textPrimary)
            }
            .padding(ps.pt(12))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(gewaehlt ? PrusaColors.panelRaised : PrusaColors.background)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(4))
                    .stroke(gewaehlt ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Material

    private var materialPanel: some View {
        let gewaehlt = model.selectedPreset(for: "filament") ?? ""
        // Nur die ersten Profile, nicht alle 189 auf einmal - die
        // vollstaendige Auswahl mit Hersteller und Farbe ist der
        // Advanced Mode.
        let filamente = Array(model.presetNames(.filament).prefix(30))
        return VStack(alignment: .leading, spacing: ps.pt(8)) {
            titel(st("MATERIAL", "MATERIAL"))
            hinweis(st("The material for this print.", "Das Material für diesen Druck."))
            if filamente.isEmpty {
                leeresPanel(st("No material available", "Kein Material vorhanden"),
                            st("Open Advanced Mode", "Advanced Mode öffnen"),
                            onOpenAdvanced)
            }
            ForEach(filamente, id: \.self) { name in
                referenzWahl(titel: EasyModeState.shared.profileDisplayLabel(rawPreset: name),
                             detail: name,
                             gewaehlt: name == gewaehlt) {
                    model.selectPreset(.filament, name)
                    panel = .workspace
                }
            }
        }
    }

    // MARK: - Einstellen

    private var einstellungenPanel: some View {
        let stuetzenAn = (model.config("support_material") ?? "0") == "1"
        let stil = model.config("support_material_style") ?? ""
        let brim = model.config("brim_width") ?? "0"
        return VStack(alignment: .leading, spacing: ps.pt(10)) {
            titel(st("SETTINGS", "EINSTELLEN"))
            hinweis(st("The most important choices for this print.",
                       "Die wichtigsten Entscheidungen für diesen Druck."))
            einstellKarte(
                titel: st("Supports", "Stützen"),
                detail: stuetzenAn
                    ? ((model.config("support_material_buildplate_only") ?? "0") == "1"
                       ? st("Build plate only", "Nur Druckbett")
                       : st("Everywhere", "Überall"))
                      + " · " + (stil.isEmpty ? "Snug" : stil)
                    : st("No supports", "Keine Stützen"),
                zeichen: "⌂", an: stuetzenAn, ziel: .supports)
            einstellKarte(
                titel: st("Adhesion", "Haftung"),
                detail: brim == "0"
                    ? st("No additional bed adhesion", "Keine zusätzliche Haftung")
                    : st("Outline around the model", "Rand um das Modell"),
                zeichen: "▱", an: brim != "0", ziel: .adhesion)
            einstellKarte(
                titel: "Print Settings",
                detail: st("Quality, infill and shell thickness",
                           "Qualität, Infill und Wandstärke"),
                zeichen: "☷", an: true, ziel: .printSettings)

            textKnopf(st("Open Advanced Mode", "Advanced Mode öffnen"),
                      kennung: "simple.advanced", aktion: onOpenAdvanced)
            // Programm statt Werkstueck: Sprache, Startmodus, Vorschau.
            // Steht hier, weil man die Startseite nicht mehr sieht, wenn
            // der Modus fest eingestellt ist.
            textKnopf(st("App settings", "App-Einstellungen"),
                      kennung: "simple.appeinstellungen", aktion: onAppSettings)
        }
    }

    private func einstellKarte(titel: String,
                               detail: String,
                               zeichen: String,
                               an: Bool,
                               ziel: SimplePanel) -> some View {
        Button { panel = ziel } label: {
            VStack(alignment: .leading, spacing: ps.pt(8)) {
                HStack(spacing: ps.pt(10)) {
                    Text(zeichen)
                        .font(.system(size: ps.font(24)))
                        .foregroundStyle(an ? PrusaColors.orange : PrusaColors.textMuted)
                        .frame(width: ps.pt(46), height: ps.pt(46))
                        .background(PrusaColors.panel)
                    Text(titel)
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                }
                Text(detail)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .lineLimit(2)
                Text(st("Open", "Öffnen") + "  ›")
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.orange)
            }
            .padding(ps.pt(14))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(PrusaColors.panelRaised)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(4))
                    .stroke(an ? PrusaColors.orange.opacity(0.65) : PrusaColors.divider,
                            lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("simple.karte.\(ziel.name)")
    }

    // MARK: - Stuetzen

    private var stuetzenPanel: some View {
        let gewaehlt = SimpleModeState.shared.selectedSupportChoice(
            supports: model.config("support_material") ?? "0",
            automatic: model.config("support_material_auto") ?? "0",
            buildPlateOnly: model.config("support_material_buildplate_only") ?? "0",
            style: model.config("support_material_style") ?? ""
        )
        return VStack(alignment: .leading, spacing: ps.pt(2)) {
            titel(st("SUPPORTS", "STÜTZEN"))
            stuetzenWahl(.disabled, st("Disabled", "Aus"),
                         st("No supports", "Keine Stützen"), gewaehlt)
            gruppe(st("Everywhere", "Überall"))
            stuetzenWahl(.snugEverywhere, st("Snug", "Anliegend"),
                         st("Straight supports close to the model.",
                            "Gerade Stützen dicht am Modell."), gewaehlt)
            stuetzenWahl(.organicEverywhere, st("Organic", "Organisch"),
                         st("Tree-shaped supports, easy to remove.",
                            "Baumförmige Stützen, leicht zu entfernen."), gewaehlt)
            gruppe(st("Build plate only", "Nur vom Druckbett"))
            stuetzenWahl(.snugBuildPlate, st("Snug", "Anliegend"),
                         st("Build plate only", "Nur vom Druckbett"), gewaehlt)
            stuetzenWahl(.organicBuildPlate, st("Organic", "Organisch"),
                         st("Build plate only", "Nur vom Druckbett"), gewaehlt)
        }
    }

    private func gruppe(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(15)))
            .foregroundStyle(PrusaColors.textPrimary)
            .padding(.top, ps.pt(10))
    }

    private func stuetzenWahl(_ wahl: SimpleSupportChoice,
                              _ titel: String,
                              _ detail: String,
                              _ gewaehlt: SimpleSupportChoice) -> some View {
        referenzWahl(titel: titel, detail: detail, gewaehlt: wahl == gewaehlt) {
            // Welche Parameter zu welcher Wahl gehoeren, entscheidet das
            // gemeinsame Modul - sonst driften Android und iOS
            // auseinander, ohne dass es jemand bemerkt.
            for (schluessel, wert) in SimpleModeState.shared.supportConfig(choice: wahl) {
                model.setConfig(schluessel, wert)
            }
        }
        .accessibilityIdentifier("simple.stuetzen.\(wahl.name)")
    }

    // MARK: - Haftung

    private var haftungPanel: some View {
        // "Automatisch entscheiden" ist bewusst kein dritter Zustand,
        // sondern eine Entscheidungshilfe: sie beurteilt die Objekte auf
        // dem Bett und setzt danach eine der beiden echten Einstellungen.
        let rat = AdhesionAdvice.shared.advise(objects: model.footprints)
        let brim = model.config("brim_width") ?? "0"
        return VStack(alignment: .leading, spacing: ps.pt(2)) {
            titel(st("INCREASE ADHESION", "HAFTUNG VERBESSERN"))
            referenzWahl(titel: st("Disabled", "Aus"),
                         detail: st("No additional bed adhesion",
                                    "Keine zusätzliche Haftung"),
                         gewaehlt: brim == "0") {
                model.setConfig("brim_width", "0")
            }
            referenzWahl(titel: st("Decide automatically", "Automatisch entscheiden"),
                         detail: AdhesionAdvice.shared.explain(advice: rat),
                         gewaehlt: false) {
                model.setConfig("brim_width", String(rat.brimWidthMm))
            }
            .accessibilityIdentifier("simple.haftung.automatisch")
            referenzWahl(titel: st("Outline around the model", "Rand um das Modell"),
                         detail: st("A brim helps hold edges down while printing.",
                                    "Ein Rand hält die Kanten während des Drucks unten."),
                         gewaehlt: brim != "0") {
                model.setConfig("brim_width", String(AdhesionAdvice.shared.SUGGESTED_BRIM_MM))
            }
        }
    }

    // MARK: - Druckeinstellungen

    private var druckprofilPanel: some View {
        let gewaehlt = model.selectedPreset(for: "print") ?? ""
        let profile = Array(model.presetNames(.print).prefix(10))
        return VStack(alignment: .leading, spacing: ps.pt(8)) {
            titel(st("PRINT SETTINGS", "DRUCKEINSTELLUNGEN"))
            HStack(spacing: ps.pt(8)) {
                ForEach(SimpleModeState.shared.printSettingsColumns(), id: \.self) { spalte in
                    Text(st(spalte, spaltenNameDeutsch(spalte)))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .padding(ps.pt(10))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(PrusaColors.panelRaised)
                }
            }
            if profile.isEmpty {
                leeresPanel(st("No print settings available",
                               "Keine Druckeinstellungen vorhanden"),
                            st("Set up print settings", "Druckeinstellungen einrichten"),
                            onOpenAdvanced)
            }
            ForEach(profile, id: \.self) { name in
                referenzWahl(titel: EasyModeState.shared.profileDisplayLabel(rawPreset: name),
                             detail: name,
                             gewaehlt: name == gewaehlt) {
                    model.selectPreset(.print, name)
                    panel = .workspace
                }
            }
        }
    }

    /// Die drei Spaltenkoepfe stehen in der gemeinsamen Liste bewusst auf
    /// Englisch - sie benennen die Bereiche der Referenz. Die
    /// Uebersetzung gehoert deshalb hierher.
    private func spaltenNameDeutsch(_ english: String) -> String {
        switch english {
        case "Print Settings":  return "Druckeinstellungen"
        case "Infill":          return "Füllung"
        case "Shell Thickness": return "Wandstärke"
        default:                return english
        }
    }

    // MARK: - Bausteine

    private func referenzWahl(titel: String,
                              detail: String,
                              gewaehlt: Bool,
                              aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            HStack(spacing: ps.pt(10)) {
                VStack(alignment: .leading, spacing: ps.pt(2)) {
                    Text(titel)
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .lineLimit(1)
                    if !detail.isEmpty, detail != titel {
                        Text(detail)
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer()
                if gewaehlt {
                    Image(systemName: "checkmark")
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                }
            }
            .padding(ps.pt(12))
            .frame(maxWidth: .infinity, minHeight: ps.touch(48), alignment: .leading)
            .background(gewaehlt ? PrusaColors.panelRaised : PrusaColors.background)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(2))
                    .stroke(gewaehlt ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.top, ps.pt(10))
    }

    private func leeresPanel(_ nachricht: String,
                             _ aktion: String,
                             _ handlung: @escaping () -> Void) -> some View {
        VStack(spacing: ps.pt(10)) {
            Text(nachricht)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
            Button(action: handlung) {
                Text(aktion)
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.orange)
                    .padding(.horizontal, ps.pt(14))
                    .frame(height: ps.touch(44))
                    .overlay(
                        RoundedRectangle(cornerRadius: ps.pt(3))
                            .stroke(PrusaColors.divider, lineWidth: 1)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(ps.pt(18))
        .background(PrusaColors.panelRaised)
    }

    private func textKnopf(_ label: String,
                           kennung: String,
                           aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }
}
