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
    var onHome: () -> Void = {}
    var onOpenAdvanced: () -> Void = {}
    var onOpenPrinterSetup: () -> Void = {}
    var onAppSettings: () -> Void = {}
    var onRemoteSettings: () -> Void = {}
    /// Den fertigen G-Code an einen Drucker schicken. Der Weg ueber die
    /// Zusammenfassung: dort liegt die Datei, dort ist die Frage faellig.
    var onSendToPrinter: (URL) -> Void = { _ in }

    @Environment(\.psScale) private var ps
    @AppStorage(AppSettings.shared.KEY_PLUGIN_REMOTE_SLICE)
    private var remoteSlicePluginAn = true
    @State private var zeigeVerlassenNachfrage = false
    @State private var zeigeSpeichernName = false
    @State private var speichernName = ""

    /// Dieselbe Nachfrage wie im Advanced Mode - siehe dort fuer die
    /// Begruendung (ein zweites Tippen auf einen Modus verwirft sonst
    /// stillschweigend das offene Projekt).
    private func nachHauseGehen() {
        if model.hasUnsavedChanges {
            zeigeVerlassenNachfrage = true
        } else {
            onHome()
        }
    }
    @State private var panel: SimplePanel = .workspace
    @State private var zeigeImporter = false
    @State private var zeigeColorMix = false
    @State private var zeigeArrange = false
    /// Gemessene Hoehe von Kopfzeile + Werkzeugleiste - darunter beginnt die Objektleiste.
    @State private var kopfHoehe: CGFloat = 140
    /// Ein kurzes Fenster (iPhone quer): die Werkzeugspalte passt nicht
    /// mehr mittig zwischen Werkzeugleiste und Modelle-Blatt. Bis zum
    /// 16.09.2026 lag "Verschieben" dann auf dem Slice-Knopf und "Ansicht"
    /// auf "Einklappen" (Zwilling: `kurz` in SimpleModeView.kt).
    @State private var kurz = false
    @State private var hinderungsgruende: [String] = []

    /// Wofuer der Dateiwaehler offen ist. Eine 3MF kann beides sein -
    /// ein Modell, das dazukommt, oder ein Projekt, das alles ersetzt.
    /// Das kann die Datei nicht entscheiden, nur der Nutzer.
    private enum Zweck { case modell, projekt }
    @State private var zweck: Zweck = .modell

    /// Welches Gizmo am ausgewaehlten Objekt haengt.
    @State private var gizmo: PsmViewport.Gizmo = .move
    /// Bett oder G-Code-Vorschau. Die Vorschau gibt es erst nach einem
    /// Schnitt - vorher ist nichts anzuzeigen.
    /// Ob das Werkzeug "Auf Flaeche legen" an ist.
    @State private var aufFlaeche = false
    @State private var vorschau = false
    /// Ob nach dem laufenden Schnitt die Vorschau aufgehen soll. Wer auf
    /// "Vorschau" tippt, will die Wege sehen - nicht die
    /// Zusammenfassung und dann noch einmal tippen.
    @State private var nachDemSchnittZeigen = false
    @State private var finalPreview: PsmCore.PreviewSnapshot?
    @State private var previewRange = PreviewRange(layerCount: 0)
    @State private var previewView: PsmViewport.PreviewView = .feature
    @State private var hiddenPreviewRoles:
        Set<PsmCore.PreviewFeatureRole> = []
    @State private var hiddenPreviewExtruders: Set<Int32> = []
    @State private var schichthoehenDarstellung:
        PsmViewport.LayerVisualization?
    @State private var ansichtZuruecksetzen = 0

    /// Auf schmalen Geraeten ruecken Kopfzeile und Leiste zusammen -
    /// dasselbe `compact` wie auf Android, nur aus der Skalierung
    /// abgeleitet statt aus screenWidthDp.
    private var kompakt: Bool { ps.factor <= 0.8 }

    var body: some View {
        ZStack(alignment: .top) {
            PrusaColors.background.ignoresSafeArea()
            GeometryReader { g in
                Color.clear.preference(key: FensterHoehe.self, value: g.size.height)
            }
            .onPreferenceChange(FensterHoehe.self) { kurz = $0 < 520 }
            .allowsHitTesting(false)
            arbeitsbereich
            // Kopf und Werkzeugleiste messen statt raten: mit festen 116/148 pt
            // lag die Objektleiste auf dem Telefon in der Slice-Zeile (S23 FE,
            // 16.09.2026). Gegenstueck: kopfHoehe in SimpleModeView.kt.
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    kopfzeile
                    werkzeugleiste
                }
                .background(GeometryReader { g in
                    Color.clear.preference(key: KopfHoehe.self, value: g.size.height)
                })
                .onPreferenceChange(KopfHoehe.self) { kopfHoehe = $0 }
                Spacer()
            }
            if panel == .workspace,
               let id = model.selectedId,
               let objekt = model.objects.first(where: { $0.id == id }) {
                VStack {
                    SimpleObjectBarView(
                        model: model,
                        objekt: objekt,
                        zeigtZurueck: true,
                        onClearSelection: {
                            aufFlaeche = false
                            model.select(nil)
                        },
                        onFlaechenwahl: { aufFlaeche = $0 })
                    .padding(.top, kopfHoehe + ps.pt(6))
                    Spacer()
                }
            }
            if panel != .workspace { overlay }
            if panel == .workspace && !vorschau { modellKnopf }
            if panel == .workspace && !vorschau { schrittleiste }
            if panel == .workspace { werkzeugspalte }
            if vorschau, let snapshot = finalPreview {
                FinalPreviewOverlay(
                    snapshot: snapshot,
                    range: $previewRange,
                    view: $previewView,
                    hiddenRoles: $hiddenPreviewRoles,
                    hiddenExtruders: $hiddenPreviewExtruders,
                    onEditor: vorschauSchliessen,
                    topInset: kopfHoehe + ps.pt(6))
            }
            if let hinweis = model.projectNotice { ProjektHinweis(text: hinweis, model: model, abstandUnten: ps.pt(80)) }
            if model.progress != .idle {
                SliceSheet(model: model,
                           onSendToPrinter: onSendToPrinter) { model.dismissProgress() }
            }
            if !hinderungsgruende.isEmpty {
                SliceBlockerSheet(gruende: hinderungsgruende) { hinderungsgruende = [] }
            }
            if let pending = model.pendingPresetSwitch {
                presetSwitchDialog(pending)
            }
            PSMarke(name: "simple.arbeitsbereich")
        }
        // Wer auf "Vorschau" tippt und dafuer warten musste, will
        // danach die Wege sehen - nicht die Zusammenfassung und dann
        // noch einmal tippen.
        .onChange(of: model.progress) { neu in
            guard nachDemSchnittZeigen else { return }
            if case .done = neu {
                nachDemSchnittZeigen = false
                model.dismissProgress()
                vorschauUmschalten()
            } else if case .failed = neu {
                nachDemSchnittZeigen = false
            } else if case .cancelled = neu {
                nachDemSchnittZeigen = false
            }
        }
        .fileImporter(isPresented: $zeigeImporter,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: false) { ergebnis in
            guard case .success(let urls) = ergebnis, let u = urls.first else { return }
            if u.pathExtension.lowercased() == "zip" {
                _ = model.loadZip(url: u)
                return
            }
            switch zweck {
            case .modell:  model.load(url: u)
            case .projekt: model.loadProject(url: u)
            }
        }
        .sheet(isPresented: $zeigeColorMix) {
            ColorMixView { zeigeColorMix = false }
                .environmentObject(model)
        }
        .alert(st("Save project", "Projekt sichern"), isPresented: $zeigeSpeichernName) {
            TextField(st("Name", "Name"), text: $speichernName)
                .accessibilityIdentifier("projekt.sichern.name")
            Button(st("Cancel", "Abbrechen"), role: .cancel) {}
            Button(st("Save", "Sichern")) {
                let name = speichernName.trimmingCharacters(in: .whitespacesAndNewlines)
                model.saveProject(name: name.isEmpty ? "PSMobile" : name)
            }
            .accessibilityIdentifier("projekt.sichern.ok")
        }
        .alert(st("Unsaved changes", "Ungesicherte Änderungen"),
               isPresented: $zeigeVerlassenNachfrage) {
            Button(st("Cancel", "Abbrechen"), role: .cancel) {}
            Button(st("Discard", "Verwerfen"), role: .destructive) { onHome() }
            Button(st("Save", "Sichern")) {
                model.saveProject(name: model.proposedProjectName)
                onHome()
            }
        } message: {
            Text(st("A second tap on a mode would discard this project.",
                     "Ein erneutes Tippen auf einen Modus würde dieses Projekt verwerfen."))
        }
        .onChange(of: model.sceneRevision) { _ in
            if vorschau && model.previewSnapshot() == nil {
                vorschauSchliessen()
            }
        }
        .popover(isPresented: $zeigeArrange) {
            ArrangePanel(model: model, isPresented: $zeigeArrange)
        }
    }

    // MARK: - Arbeitsbereich

    @ViewBuilder private var arbeitsbereich: some View {
        if let session = model.sessionHandle {
            ZStack(alignment: .bottomLeading) {
                ViewportView(
                    session: session,
                    shaderDir: model.shaderDir,
                    selectedId: model.selectedId ?? -1,
                    selectedIds: Array(model.selectedIds),
                    invalidateKey: model.sceneRevision,
                    // Bei offenem Panel darf der Viewport die Beruehrung nicht
                    // schlucken: ein Tippen daneben soll das Panel schliessen,
                    // nicht die Kamera drehen.
                    inputEnabled: panel == .workspace,
                    // Oberhalb der Leiste mit Import/Zurueck unten links.
                    wuerfelHoch: ps.pt(70),
                    gizmo: gizmo,
                    viewportMode: vorschau ? .preview : .editor,
                    // Mehrbett ist absichtlich ein Advanced-Werkzeug:
                    // Simple arbeitet immer auf dem aktuellen Bett, auch
                    // wenn die globale Viewport-Option aktiviert ist.
                    multiBedRender: false,
                    focusBedIndex: model.activeBedIndex,
                    focusBedKey: model.focusBedKey,
                    layerRange: vorschau && !previewRange.isEmpty
                        ? (Int32(previewRange.lower) ... Int32(previewRange.upper))
                        : nil,
                    previewView: previewView,
                    previewRoles:
                        finalPreview?.roles.map(\.role) ?? [],
                    hiddenPreviewRoles: hiddenPreviewRoles,
                    previewExtruders:
                        finalPreview?.extruders.map(\.extruder) ?? [],
                    hiddenPreviewExtruders: hiddenPreviewExtruders,
                    resetViewKey: ansichtZuruecksetzen,
                    onPreviewLoaded: { anzahl in
                        guard let snapshot = finalPreview,
                              anzahl == Int32(snapshot.layers.count),
                              anzahl > 0 else {
                            vorschauSchliessen()
                            return
                        }
                    },
                    onSelect: { model.select($0 < 0 ? nil : $0) },
                    onObjectChanged: { model.refresh() },
                    // Nur solange das Werkzeug an ist. Sonst gehoert jede
                    // Beruehrung der Kamera, und ein Tippen ist eine
                    // Auswahl.
                    onSurfaceTap: (aufFlaeche && model.selectedId != nil)
                        ? { (treffer: PsmViewport.SurfaceHit) in
                            guard let id = model.selectedId else { return }
                            model.layOnFace(id,
                                            instance: Int(treffer.instanceIndex),
                                            volume: Int(treffer.volumeIndex),
                                            facet: Int(treffer.facetIndex))
                            // Das Werkzeug bleibt an. Sich nach einem
                            // Tippen selbst abzuschalten war gut gemeint -
                            // beim ersten Versuch trifft man aber selten
                            // die gemeinte Flaeche, und dann steht man vor
                            // einem Werkzeug, das nicht mehr reagiert.
                            // Aus geht es ueber denselben Knopf.
                          }
                        : nil,
                    onBlockedInput: { panel = .workspace },
                    onLayerVisualizationChanged: {
                        schichthoehenDarstellung = $0
                    }
                )
                .ignoresSafeArea()

                if !vorschau, let grenzen = schichthoehenDarstellung {
                    LayerProfileViewportLegend(
                        minHeight: Double(grenzen.minHeight),
                        maxHeight: Double(grenzen.maxHeight))
                        .padding(ps.pt(12))
                }
            }
        }
    }

    // MARK: - Kopfzeile

    private var kopfzeile: some View {
        HStack {
            Button(action: nachHauseGehen) {
                Image(systemName: "house")
                    .font(.system(size: ps.font(kompakt ? 22 : 26)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(width: ps.touch(44), height: ps.touch(44))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("kopf.start")
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
            Button(st("Advanced", "Advanced"), action: onOpenAdvanced)
                .font(.system(size: ps.font(kompakt ? 12 : 14), weight: .medium))
                .foregroundStyle(PrusaColors.orange)
                .padding(.horizontal, ps.pt(kompakt ? 12 : 16))
                .frame(minHeight: ps.touch(44))
                .overlay(
                    RoundedRectangle(cornerRadius: ps.pt(6))
                        .stroke(PrusaColors.orange, lineWidth: ps.pt(1)))
                .accessibilityIdentifier("kopf.advanced")
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
            // "Vorschau" zeigt die Werkzeugwege, "G-Code" schneidet und
            // gibt die Datei aus. Frueher taten beide dasselbe, weil es
            // die Vorschau noch nicht gab.
            werkzeug(labels[4], gewaehlt: vorschau,
                     breite: ps.pt(kompakt ? 62 : 74)) { vorschauZeigen() }
            werkzeug(labels[5], gewaehlt: true,
                     breite: ps.pt(kompakt ? 84 : 100)) { schneiden() }
            if remoteSlicePluginAn {
                fernSchnittUmschalter
            }
        }
        .padding(.horizontal, ps.pt(12))
        .padding(.vertical, ps.pt(kompakt ? 3 : 6))
    }

    /// Lokal/entfernt umschalten, wie im Advanced Mode - siehe dort
    /// (AdvancedWorkspaceView.fernSchnittUmschalter) fuer die
    /// Begruendung. Ohne eingerichteten Server fuehrt das Tippen erst
    /// zur Einrichtung statt stumm auf einen leeren Host umzuschalten.
    private var fernSchnittUmschalter: some View {
        Button {
            let host = UserDefaults.standard.string(
                forKey: SlicerModel.remoteSliceHostKey) ?? ""
            if host.isEmpty {
                onRemoteSettings()
            } else {
                model.remoteSliceEnabled.toggle()
            }
        } label: {
            Image(systemName: model.remoteSliceEnabled ? "cloud.fill" : "cloud")
                .font(.system(size: ps.font(16)))
                .foregroundStyle(model.remoteSliceEnabled
                                 ? PrusaColors.background : PrusaColors.textPrimary)
                .frame(width: ps.pt(kompakt ? 40 : 46), height: ps.pt(kompakt ? 46 : 54))
                .background(model.remoteSliceEnabled ? PrusaColors.orange : PrusaColors.panel)
                .overlay(
                    RoundedRectangle(cornerRadius: ps.pt(1))
                        .stroke(model.remoteSliceEnabled
                                ? PrusaColors.orange : PrusaColors.divider, lineWidth: 1)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("simple.fern.umschalten")
        .accessibilityLabel(model.remoteSliceEnabled
                            ? st("Remote slicing on", "Remote Slicing an")
                            : st("Remote slicing off", "Remote Slicing aus"))
    }

    /// Statt eines ausgegrauten Knopfes, der nur sagt "geht nicht":
    /// erst die Gruende zeigen, dann schneiden. Ein Knopf, der nichts
    /// tut und nichts sagt, ist die schlechtere Auskunft.
    private func schneiden() {
        let gruende = model.sliceBlockers
        if gruende.isEmpty {
            panel = .workspace
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
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
        case "G-Code":
            return "➤  " + (model.remoteSliceEnabled
                                 ? st("Slice on server", "Auf Server")
                                 : st("Slice", "Slicen"))
        default:         return label
        }
    }

    private func presetSwitchDialog(_ pending: SlicerModel.PendingPresetSwitch) -> some View {
        ProfilWechselDialog(
            aenderungen: pending.aenderungen,
            grund: SimpleModeState.shared.text(
                english: "Switching the profile would drop these changes:",
                german: "Beim Wechsel des Profils gingen diese Änderungen verloren:"),
            onVerwerfen: { model.pendingPresetSwitchVerwerfenUndWechseln() },
            onNeuesProfil: { model.pendingPresetSwitchAlsNeuesProfilSichernUndWechseln($0) },
            onUeberschreiben: { model.pendingPresetSwitchUeberschreibenUndWechseln() },
            onInsProjekt: { model.pendingPresetSwitchInsProjektUebernehmen() },
            onAbbrechen: { model.pendingPresetSwitchAbbrechen() })
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }

    // MARK: - Modell hinzufuegen

    /// Auf leerem Bett ein einzelner Knopf, sonst das Modelle-Blatt:
    /// solange nichts da ist, gibt es nichts anzuordnen, zu klonen oder
    /// zu entfernen - eine Liste mit lauter toten Knoepfen waere die
    /// schlechtere Auskunft.
    @ViewBuilder private var modellKnopf: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                if model.objects.isEmpty {
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
                } else {
                    SimpleModelSheetView(
                        model: model,
                        onPickFile: { zeigeImporter = true },
                        onArrange: { zeigeArrange = true })
                }
            }
        }
        .padding(ps.pt(12))
        // Im kurzen Fenster steht die Werkzeugspalte rechts daneben (58 + 10 pt).
        .padding(.trailing, kurz ? ps.pt(68) : 0)
    }

    // MARK: - Werkzeuge am rechten Rand

    /// Verschieben, Drehen, Skalieren und die Ansicht zuruecksetzen.
    ///
    /// Der Viewport kennt die drei Gizmos seit langem, sie waren auf iOS
    /// nur nicht umschaltbar - man konnte ein Objekt also nur ziehen,
    /// nicht drehen. Rechts und nicht unten, weil unten schon die
    /// Schrittleiste und das Modelle-Blatt liegen.
    private var werkzeugspalte: some View {
        HStack {
            Spacer()
            VStack(spacing: ps.pt(6)) {
                // Mittig, solange die Hoehe reicht; im kurzen Fenster oben
                // unter der Werkzeugleiste, das Modelle-Blatt weicht nach
                // links aus (modellKnopf).
                if kurz {
                    Color.clear.frame(height: kopfHoehe + ps.pt(6))
                } else {
                    Spacer()
                }
                if model.selectedId != nil && !vorschau {
                    werkzeugKnopf("↔", st("Move", "Verschieben"),
                                  aktiv: gizmo == .move, kennung: "werkzeug.verschieben") {
                        gizmo = .move
                    }
                    werkzeugKnopf("⟳", st("Rotate", "Drehen"),
                                  aktiv: gizmo == .rotate, kennung: "werkzeug.drehen") {
                        gizmo = .rotate
                    }
                    werkzeugKnopf("⤢", st("Scale", "Skalieren"),
                                  aktiv: gizmo == .scale, kennung: "werkzeug.skalieren") {
                        gizmo = .scale
                    }
                }
                werkzeugKnopf("⌂", st("View", "Ansicht"),
                              aktiv: false, kennung: "werkzeug.ansicht") {
                    ansichtZuruecksetzen += 1
                }
                if model.gcodeURL != nil {
                    werkzeugKnopf(vorschau ? "▣" : "▱",
                                  vorschau ? st("Bed", "Bett") : st("Preview", "Vorschau"),
                                  aktiv: vorschau, kennung: "werkzeug.vorschau") {
                        vorschauUmschalten()
                    }
                }
                Spacer()
            }
            .padding(.trailing, ps.pt(10))
        }
    }

    /// Die Vorschau wird beim ersten Hinsehen geladen - die Werkzeugwege
    /// eines Drucks sind zu gross, um sie vorsorglich vorzuhalten.
    /// Vorschau oeffnen - und nur dann rechnen, wenn es sein muss.
    ///
    /// Der Kern weiss, ob sein Ergebnis noch zur Szene passt. Passt es,
    /// kostet das Hinsehen nichts; sonst wird geschnitten, und die
    /// Vorschau geht danach von selbst auf.
    private func vorschauZeigen() {
        if vorschau { vorschau = false; return }
        if model.sliceResultIsCurrent || model.lastSliceWasRemote {
            vorschauUmschalten()
            return
        }
        let gruende = model.sliceBlockers
        if gruende.isEmpty {
            panel = .workspace
            nachDemSchnittZeigen = true
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
    }

    private func vorschauUmschalten() {
        if vorschau {
            vorschauSchliessen()
            return
        }
        guard let snapshot = model.previewSnapshot(),
              !snapshot.layers.isEmpty else {
            vorschauSchliessen()
            return
        }
        // In der Vorschau gibt es keine Objekte zum Anfassen.
        model.select(nil)
        finalPreview = snapshot
        previewRange = PreviewRange(layerCount: snapshot.layers.count)
        previewView = .feature
        hiddenPreviewRoles.removeAll()
        hiddenPreviewExtruders.removeAll()
        vorschau = true
    }

    private func vorschauSchliessen() {
        vorschau = false
        finalPreview = nil
        previewRange = PreviewRange(layerCount: 0)
    }

    private func werkzeugKnopf(_ glyph: String,
                               _ label: String,
                               aktiv: Bool,
                               kennung: String,
                               aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: 0) {
                Text(glyph).font(.system(size: ps.font(16)))
                Text(label).font(.system(size: ps.font(8))).lineLimit(1)
            }
            .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
            .frame(width: ps.pt(58), height: ps.touch(48))
            .background(aktiv ? PrusaColors.orange : PrusaColors.panel.opacity(0.92))
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    // MARK: - Zurueck und wiederholen

    /// Unten links, wie auf Android. Beide Knoepfe nennen, was sie tun
    /// wuerden - "Zurueck" allein sagt nicht, was verloren geht.
    private var schrittleiste: some View {
        VStack {
            Spacer()
            HStack(spacing: 0) {
                schritt("↶", model.undoLabel,
                        standard: st("Undo", "Rückgängig"),
                        kennung: "simple.zurueckschritt") { model.undo() }
                schritt("↷", model.redoLabel,
                        standard: st("Redo", "Wiederholen"),
                        kennung: "simple.wiederholen") { model.redo() }
                Spacer()
            }
        }
        .padding(ps.pt(12))
    }

    private func schritt(_ glyph: String,
                         _ beschriftung: String,
                         standard: String,
                         kennung: String,
                         aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: 0) {
                Text(glyph).font(.system(size: ps.font(15)))
                Text(beschriftung.isEmpty ? standard : beschriftung)
                    .font(.system(size: ps.font(8)))
                    // Zwei Zeilen: die Beschriftungen kommen aus dem Kern
                    // ("Objekte importiert") und passen auf einem iPhone
                    // sonst nicht in eine.
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(beschriftung.isEmpty
                             ? PrusaColors.textMuted.opacity(0.4) : PrusaColors.textPrimary)
            .frame(width: ps.pt(76), height: ps.touch(48))
            .background(PrusaColors.panel.opacity(0.9))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(beschriftung.isEmpty)
        .accessibilityIdentifier(kennung)
    }

    /// Was beim Oeffnen eines Projekts anders lief als darin stand.
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
            titel(st("PROJECTS", "PROJEKTE"))
            HStack(spacing: ps.pt(12)) {
                panelAktion(st("New project", "Neues Projekt"), kennung: "projekt.neu") {
                    model.newProject()
                }
                panelAktion(st("Open model", "Modell öffnen"), kennung: "projekt.modell") {
                    zweck = .modell
                    zeigeImporter = true
                }
                panelAktion(st("Open project", "Projekt öffnen"), kennung: "projekt.oeffnen") {
                    zweck = .projekt
                    zeigeImporter = true
                }
            }
            // Sichern schreibt erst die Datei, dann geht sie ueber das
            // Teilen-Blatt weiter - auf iOS gibt es keinen Ordner, in den
            // eine App einfach schreibt.
            HStack(spacing: ps.pt(12)) {
                panelAktion(st("Save project", "Projekt sichern"),
                            kennung: "projekt.sichern") {
                    // Erst den Namen erfragen, wie im Advanced: ohne ihn
                    // hiess jedes Projekt "PSMobile" und ueberschrieb das vorige.
                    speichernName = model.proposedProjectName
                    zeigeSpeichernName = true
                }
                if let url = model.projectURL {
                    ShareLink(item: url) {
                        Text(st("Share", "Weitergeben"))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(.white)
                            .padding(.horizontal, ps.pt(14))
                            .frame(height: ps.touch(44))
                            .background(PrusaColors.orange)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                            .contentShape(Rectangle())
                    }
                    .accessibilityIdentifier("projekt.weitergeben")
                }
            }
            zuletzt
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

    /// Die gesicherten Projekte, neueste oben.
    ///
    /// Acht Zeilen reichen: wer weiter zurueck will, nimmt "Projekt
    /// oeffnen" und die Dateien-App - dort liegt derselbe Ordner.
    @ViewBuilder private var zuletzt: some View {
        let dateien = model.recentProjects()
        if !dateien.isEmpty {
            Text(st("Recent", "Zuletzt"))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.top, ps.pt(4))
            ForEach(Array(dateien.enumerated()), id: \.offset) { index, url in
                Button {
                    zweck = .projekt
                    // Als Projekt, nicht als Modellimport - siehe
                    // WorkflowStartView.swift.
                    model.loadProject(url: url)
                } label: {
                    HStack(spacing: ps.pt(8)) {
                        Text("▣")
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.orange)
                        Text(url.deletingPathExtension().lastPathComponent)
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .lineLimit(1)
                        Spacer()
                        Text(datum(url))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    .padding(.horizontal, ps.pt(10))
                    .frame(height: ps.touch(44))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("projekt.zuletzt.\(index)")
            }
        }
    }

    private func datum(_ url: URL) -> String {
        let wert = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?
            .contentModificationDate
        guard let wert else { return "" }
        let f = DateFormatter()
        // In der Sprache der Oberflaeche, nicht des Systems (Zwilling:
        // datum() in SimpleModeView.kt).
        f.locale = Locale(identifier: Lang.shared.current == "de" ? "de_DE" : "en_US")
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: wert)
    }

    private func panelAktion(_ label: String,
                             kennung: String,
                             aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
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
        .accessibilityIdentifier(kennung)
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
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            titel(st("PRINTER", "DRUCKER"))
            hinweis(st("Choose a printer model · select the nozzle in the slice summary",
                       "Druckermodell wählen · die Düse wird in der Slice-Übersicht festgelegt"))
            DruckerAuswahlView(model: model, onSetup: onOpenPrinterSetup) {
                model.selectPreset(.printer, $0)
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
            // Mit mehreren Extrudern ist "das Material" keine Frage
            // mehr, sondern eine je Position. Bei einem Extruder waere
            // die Liste eine Zeile, die nichts sagt.
            if model.extruderCount > 1 {
                extruderListe(filamente)
                Button {
                    zeigeColorMix = true
                } label: {
                    Label(st("ColorMix", "ColorMix"), systemImage: "circle.lefthalf.filled")
                        .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                }
                .buttonStyle(.bordered)
                .tint(PrusaColors.orange)
                .accessibilityIdentifier("simple.colormix")
            }
            if filamente.isEmpty {
                leeresPanel(st("No material available", "Kein Material vorhanden"),
                            st("Open Advanced Mode", "Advanced Mode öffnen"),
                            onOpenAdvanced)
            } else {
                MaterialAuswahlView(model: model, gewaehlt: gewaehlt) {
                    model.selectPreset(.filament, $0)
                }
            }
        }
    }

    /// Ein Eintrag je Extruder: Farbe, gewaehltes Material, Auswahl.
    private func extruderListe(_ filamente: [String]) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(st("Per extruder", "Je Extruder").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
            ForEach(0..<model.extruderCount, id: \.self) { index in
                HStack(spacing: ps.pt(10)) {
                    // Die Farbe ist die einzige Auskunft, die man auf
                    // einen Blick braucht: welcher Strang liegt auf
                    // welcher Position.
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .fill(Color(hexString: model.extruderColor(index))
                              ?? PrusaColors.panelRaised)
                        .frame(width: ps.pt(26), height: ps.pt(26))
                        .overlay(
                            RoundedRectangle(cornerRadius: ps.pt(3))
                                .stroke(PrusaColors.divider, lineWidth: 1)
                        )
                    Text("\(index + 1)")
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                    Menu {
                        ForEach(filamente, id: \.self) { name in
                            Button(EasyModeState.shared.profileDisplayLabel(rawPreset: name)) {
                                model.setExtruderFilament(index, name)
                            }
                        }
                    } label: {
                        HStack {
                            Text(EasyModeState.shared.profileDisplayLabel(
                                rawPreset: model.extruderFilament(index)))
                                .font(.system(size: ps.font(13)))
                                .foregroundStyle(PrusaColors.textPrimary)
                                .lineLimit(1)
                            Spacer()
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.system(size: ps.font(10)))
                                .foregroundStyle(PrusaColors.textMuted)
                        }
                        .padding(.horizontal, ps.pt(10))
                        .frame(height: ps.touch(44))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                        .accessibilityIdentifier("extruder.material.\(index)")
                    }
                    .accessibilityIdentifier("extruder.\(index)")
                }
            }
        }
        .padding(.bottom, ps.pt(8))
    }

    // MARK: - Einstellen

    private var einstellungenPanel: some View {
        let stuetzenAn = (model.config("support_material") ?? "0") == "1"
        // Lesbar statt roh: bis zum 16.09.2026 stand "Überall · organic" auf
        // der Karte - der Katalogwert, waehrend die Wahl darunter "Organisch" heisst.
        let rohStil = model.config("support_material_style") ?? ""
        let stil: String
        switch rohStil {
        case "", "snug": stil = st("Snug", "Anliegend")
        case "organic": stil = st("Organic", "Organisch")
        case "grid": stil = st("Grid", "Gitter")
        default: stil = rohStil
        }
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
                      + " · " + stil
                    : st("No supports", "Keine Stützen"),
                zeichen: "⌂", an: stuetzenAn, ziel: .supports)
            einstellKarte(
                titel: st("Adhesion", "Haftung"),
                detail: brim == "0"
                    ? st("No additional bed adhesion", "Keine zusätzliche Haftung")
                    : st("Outline around the model", "Rand um das Modell"),
                zeichen: "▱", an: brim != "0", ziel: .adhesion)
            einstellKarte(
                // Uebersetzt wie die Karten daneben - vorher blieb nur diese englisch.
                titel: st("Print Settings", "Druckeinstellungen"),
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
            referenzWahl(titel: st("Decide automatically", "Automatisch entscheiden"),
                         detail: AdhesionAdvice.shared.explain(advice: rat),
                         gewaehlt: false) {
                model.setConfig("brim_width", String(rat.brimWidthMm))
            }
            .accessibilityIdentifier("simple.haftung.automatisch")
            referenzWahl(titel: st("Disabled", "Aus"),
                         detail: st("No additional bed adhesion",
                                    "Keine zusätzliche Haftung"),
                         gewaehlt: brim == "0") {
                model.setConfig("brim_width", "0")
            }
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

            // Die drei Bereiche der Referenz, jetzt mit den Werten
            // dahinter. Sie kamen bisher nur als Ueberschriften vor -
            // wer die Fuelldichte aendern wollte, musste in den
            // Advanced Mode.
            //
            // Gezeichnet wird mit demselben SettingField wie die 247
            // Parameter der Einstellungsseiten: Typ, Grenzen,
            // Auswahlwerte und der Grund fuers Ausgrauen kommen aus dem
            // Kern, nicht aus einer Liste hier.
            schnellBereich(st("Print Settings", "Druckeinstellungen"),
                           schluessel: ["layer_height"])
            schnellBereich(st("Infill", "Füllung"),
                           schluessel: ["fill_density", "fill_pattern"])
            schnellBereich(st("Shell Thickness", "Wandstärke"),
                           schluessel: ["perimeters", "top_solid_layers",
                                        "bottom_solid_layers"])

            Text(st("Profiles", "Profile").uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
                .padding(.top, ps.pt(10))
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
                }
            }
        }
    }

    private func schnellBereich(_ titelText: String,
                                schluessel: [String]) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(titelText.uppercased())
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
            ForEach(schluessel, id: \.self) { key in
                SettingField(model: model,
                             option: TabsCatalog.Option(key: key, code: false, line: nil),
                             kompakt: false)
                    .accessibilityIdentifier("schnell." + key)
            }
        }
        .padding(.top, ps.pt(10))
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

/// Hoehe von Kopfzeile + Werkzeugleiste (siehe body).
private struct FensterHoehe: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private struct KopfHoehe: PreferenceKey {
    static var defaultValue: CGFloat = 140
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}
