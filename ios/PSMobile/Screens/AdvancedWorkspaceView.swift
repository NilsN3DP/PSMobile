import SwiftUI
import UniformTypeIdentifiers
import UIKit
import PSMShared

/// Die tatsächlich sichtbare Fläche des scrollbaren Seitenleistenteils.
private struct AdvancedSeitenleistenRahmenPreferenceKey: PreferenceKey {
    static var defaultValue: CGRect = .null

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        let neu = nextValue()
        if !neu.isNull { value = neu }
    }
}

/// Der Arbeitsbereich im Advanced Mode.
///
/// Gegenstueck zu `SlicerScreen.kt`. Bis hierhin war das auf iOS eine
/// Notloesung: Viewport, eine Liste und ein Knopf zum Slicen. Der Simple
/// Mode konnte inzwischen mehr als der Modus, der eigentlich fuer
/// diejenigen da ist, die alles sehen wollen.
///
/// Aufbau: das Bett fuellt die Flaeche, die Werkzeuge liegen oben, und
/// rechts steht der Inspektor - der Objektbaum, wenn etwas ausgewaehlt
/// ist, sonst die Liste. Auf schmalen Geraeten klappt die Seite weg,
/// sonst bliebe vom Bett nichts uebrig.
struct AdvancedWorkspaceView: View {

    @EnvironmentObject private var model: SlicerModel
    var onHome: () -> Void = {}
    var onOpenSimple: () -> Void = {}
    var onAppSettings: () -> Void = {}
    var onPrinters: () -> Void = {}
    /// Fehlte bisher hier komplett: nach dem Schneiden gab es im
    /// Advanced Mode nur "G-Code sichern", nie den direkten Weg zu
    /// PrusaLink, den der Simple Mode schon hatte.
    var onSendToPrinter: (URL) -> Void = { _ in }
    /// Zurueck in die Ersteinrichtung - der Weg zu einem weiteren Drucker.
    var onPrinterSetup: () -> Void = {}
    var onSettings: (String) -> Void = { _ in }
    /// Erklaerung/Einrichtung von Remote Slicing - siehe
    /// docs/remote-slicing.md und den Umschalter in schneidenKnopf.
    var onRemoteSettings: () -> Void = {}

    @Environment(\.psScale) private var ps
    @AppStorage(AppSettings.shared.KEY_UNITS_IMPERIAL) private var zollEinheiten = false
    @State private var zeigeImporter = false
    @State private var hinderungsgruende: [String] = []
    @State private var gizmo: PsmViewport.Gizmo = .move
    /// @AppStorage statt der eigenen AppSettingsStore-Instanz: das
    /// erzwingt zuverlaessig ein Redraw dieser Ansicht, sobald sich der
    /// Wert in UserDefaults aendert - auch wenn diese Ansicht waehrend
    /// des Besuchs der App-Einstellungen weiter im Hintergrund lebt und
    /// aus eigenem Antrieb sonst nicht neu zeichnet.
    @AppStorage(AppSettings.shared.KEY_MULTI_BED_RENDER)
    private var multiBedRenderGespeichert: Bool = true
    @State private var seiteOffen = true
    /// Gemessene Hoehe der Werkzeugleiste - im schmalen Fenster bricht sie um.
    @State private var werkzeugleisteHoehe: CGFloat = 52
    @State private var zeigeDrucker = false
    @State private var zeigeMaterial = false
    /// Welcher Kopf die Materialauswahl geoeffnet hat - nil heisst die
    /// allgemeine Filamentzeile. Ohne das landete jede Auswahl aus der
    /// Extruderbank immer im allgemeinen Preset statt am angetippten
    /// Werkzeug, und die Filamentuebersicht zeigte nie, was man fuer
    /// T2-T8 gewaehlt hatte.
    @State private var materialZiel: Int?
    @State private var zweck: Zweck = .modell
    /// Die Zwischenablage der Schiene. Sie gehoert hierher und nicht in
    /// die Schiene selbst: kopiert wird einmal und eingefuegt spaeter,
    /// vielleicht auf einem anderen Bett.
    @State private var kopiert: Int32?
    /// Die zuletzt ausgegebene Platte, solange sie noch weitergegeben
    /// werden kann.
    @State private var platte: URL?
    /// Das Ergebnis des zuletzt benutzten Dateiwerkzeugs, solange es
    /// noch weitergegeben werden kann.
    @State private var werkzeugErgebnis: URL?
    @State private var zeigeReparatur = false
    @State private var zeigeWandeln = false
    @State private var zeigeGcodeMarken = false
    @State private var zeigeArrange = false
    @State private var zeigeBettwahl = false
    @State private var reiter: InspektorReiter = .profile
    /// Welche Bereiche der Seitenleiste offen sind. Profile immer, der
    /// Rest auf Wunsch - sonst ist die Leiste beim Start eine Wand.
    @State private var offeneBereiche: Set<String> = ["profile"]
    /// Solange gesetzt, wartet die Seitenleiste auf die echten Rahmen
    /// von Scrollfläche und Bearbeitenblock.
    @State private var seitenleistenZiel: Int32?
    /// Null: zuerst die Auswahlzeile erhalten. Eins: falls der danach
    /// neu gemessene Block noch abgeschnitten ist, direkt nachkorrigieren.
    @State private var seitenleistenFokusSchritt = 0
    @State private var seitenleistenRahmen: CGRect = .null
    @State private var bearbeitenRahmen: CGRect = .null
    /// Zaehlt hoch, wenn die Seitenleiste zum Werkzeuge-Bereich blaettern soll.
    @State private var werkzeugeZiel = 0
    /// Welche Einstellungsseite als schwebendes Fenster offen ist.
    @State private var einstellungenTab: String?
    @State private var objektSuche = ""

    /// Die vier Abschnitte des Inspektors — dieselben wie auf Android.
    private enum InspektorReiter: CaseIterable {
        case profile, objekte, bearbeiten, werkzeuge
    }
    @State private var ansicht: PsmViewport.ViewPreset?
    @State private var ansichtZaehler = 0
    @State private var vorschau = false
    /// Ist das Flaechenwerkzeug an, gehoert die Beruehrung der Flaeche.
    @State private var aufFlaeche = false
    @State private var finalPreview: PsmCore.PreviewSnapshot?
    @State private var previewRange = PreviewRange(layerCount: 0)
    /// Grenzen des Werkzeugweg-Bereichs der aktuell sichtbaren
    /// Schicht(en) - kommt vom Kern (`onMoveRangeBounds`), nicht vom
    /// Nutzer gesetzt. Aendert sich mit jeder Aenderung des
    /// Schichtbereichs, weil dann eine andere Anzahl Werkzeugwege
    /// sichtbar ist.
    @State private var moveRangeGrenzen: ClosedRange<Int32>?
    @State private var moveRangeUnten: Int32 = 0
    @State private var moveRangeOben: Int32 = 0
    @State private var previewView: PsmViewport.PreviewView = .feature
    @State private var hiddenPreviewRoles:
        Set<PsmCore.PreviewFeatureRole> = []
    @State private var hiddenPreviewExtruders: Set<Int32> = []
    @State private var schichthoehenDarstellung:
        PsmViewport.LayerVisualization?
    /// Ob nach dem laufenden Schnitt die Vorschau aufgehen soll.
    @State private var nachDemSchnittZeigen = false

    /// Wofuer der Dateiwaehler gerade offen ist.
    private enum Zweck { case modell, projekt }
    @State private var zeigeAlleProjekte = false
    @State private var zeigeSpeichernName = false
    @State private var speichernName = ""
    @State private var zeigeVerlassenNachfrage = false

    /// Vor dem Verlassen fragen, wenn es etwas zu verlieren gibt - ein
    /// zweites Tippen auf "Advanced" von der Startseite legt sonst
    /// stillschweigend ein neues, leeres Projekt an.
    private func nachHauseGehen() {
        if model.hasUnsavedChanges {
            zeigeVerlassenNachfrage = true
        } else {
            onHome()
        }
    }
    @State private var ansichtZuruecksetzen = 0

    /// Dieselben Optionen steuern Bedienung, Kern und Viewport.
    @State private var maloptionen = PsmCore.PaintOptions()

    /// Auf schmalen Fenstern liegt der Inspektor ueber dem Bett statt
    /// daneben - nebeneinander bliebe fuer beides zu wenig.
    /// Ein iPad bekommt nie die schmale, ueberlagernde Behandlung -
    /// auch nicht hochkant, wo die Breite (z. B. 744pt beim iPad mini)
    /// unter die reine Breitengrenze faellt. Die Seitenleiste hat dort
    /// genug Platz und soll nicht wegen einer Zahl verschwinden, die
    /// fuers iPhone gedacht war.
    private var schmal: Bool {
        UIDevice.current.userInterfaceIdiom != .pad && ps.windowSize.width < 760
    }

    /// Experimentelles Layout (siehe App-Einstellungen, Gruppe
    /// "Experimentell"): im Hochformat die Leiste unten andocken statt
    /// rechts, damit das Bett die volle Breite behaelt. Nur im
    /// Hochformat wirksam - im Querformat bleibt die rechte Leiste, dort
    /// gibt es die Enge nicht, die diesen Umbau motiviert.
    @AppStorage(AppSettings.shared.KEY_PLUGIN_REMOTE_SLICE)
    private var remoteSlicePluginAn = true

    @AppStorage(AppSettings.shared.KEY_PORTRAIT_BOTTOM_BAR)
    private var leisteUntenExperimentell: Bool = false
    // War auf "!schmal" beschraenkt - also nie auf einem iPhone, das
    // fast immer schmal ist. Der Schalter in den Einstellungen wirkte
    // dort dann ueberhaupt nicht, obwohl er aktiviert war. Aufs Hochformat
    // (Bett-UI wichtiger als Breite) kommt es an, nicht auf schmal/breit.
    private var leisteUnten: Bool {
        leisteUntenExperimentell && ps.windowSize.height > ps.windowSize.width
    }

    /// Die Seitenleiste beansprucht auf breiten Geraeten diesen Teil der
    /// ZStack. Schwebende Elemente muessen denselben freien Rest nutzen.
    private var seitenleistenbreite: CGFloat {
        min(ps.pt(340), ps.windowSize.width * 0.42)
    }

    /// Was der Mitte bleibt: Fenster minus Werkzeugschiene, Trenner und
    /// Seitenleiste. Gegenstueck zu `Modifier.weight(1f)` auf Android.
    private var mittenbreite: CGFloat {
        let schiene = ps.pt(74)
        let trenner: CGFloat = seiteOffen && !schmal && !leisteUnten ? 2 : 1
        let leiste = seiteOffen && !schmal && !leisteUnten ? seitenleistenbreite : 0
        return max(ps.pt(200), ps.windowSize.width - schiene - trenner - leiste)
    }

    var body: some View {
        ZStack {
            PrusaColors.background.ignoresSafeArea()
            HStack(spacing: 0) {
                // Links die Werkzeuge am Objekt, wie in PrusaSlicers
                // eigener Leiste und wie auf Android.
                WerkzeugSchiene(model: model,
                                kopiert: $kopiert,
                                onEinfuegen: { zweck = .modell; zeigeImporter = true },
                                onSettings: { onSettings("print") },
                                onArrange: { zeigeArrange = true },
                                onMalwerkzeug: { malwerkzeugUmschalten($0) },
                                onPrinters: onPrinters,
                                onAppSettings: onAppSettings)
                Divider().overlay(PrusaColors.divider)
                VStack(spacing: 0) {
                    werkzeugleiste
                    if schmal {
                        // Der echte Selector liegt auf schmalen Fenstern
                        // oberhalb der überlagernden Seitenleiste.
                        Color.clear.frame(height: ps.touch(52))
                    } else {
                        BedSelector(model: model,
                                    onArrange: { zeigeArrange = true },
                                    onOpenSelection: { zeigeBettwahl = true })
                    }
                    arbeitsflaeche
                    ansichtsleiste
                }
                // Ausgerechnet statt verhandelt.
                //
                // Vorher bekam die Mitte, was sie fuer ihre Eigenbreite
                // hielt, und schob die Seitenleiste ueber den
                // Fensterrand: auf 1032 Punkten begann die Leiste bei
                // 727 statt bei 692, "Aufs Bett einpassen" stand elf
                // Punkte draussen, und die Objektleiste ragte bis 779
                // hinein (ResponsiveLayoutUITests). Ein
                // `.frame(maxWidth: .infinity)` aenderte daran nichts -
                // die waagerechte ScrollView der Werkzeugleiste meldet
                // ihre volle Inhaltsbreite als Eigenbreite, und der
                // HStack richtet sich danach.
                //
                // Android hat das Problem nicht, weil `weight(1f)` dort
                // den Rest zuteilt statt ihn zu erfragen. Genau das
                // steht hier jetzt auch: Fensterbreite minus Schiene,
                // Trenner und Leiste.
                .frame(width: mittenbreite)
                if seiteOffen && !schmal && !leisteUnten {
                    Divider().overlay(PrusaColors.divider)
                    // Hoechstens zwei Fuenftel der Breite: darunter
                    // bleibt vom Bett nichts uebrig, und darum geht es
                    // hier.
                    //
                    // Geschichte (11./12.09.2026): auf 1032 Punkten
                    // begann die Leiste bei 727 statt 692 - das hat
                    // `mittenbreite` oben behoben. Die zweite Meldung,
                    // "die Objektleiste ragt bis 779 hinein", war keine
                    // Layoutfrage: der Test mass den letzten Knopf einer
                    // waagerechten ScrollView, der weggescrollt auch
                    // einen Rahmen hat. Seit dem 12.09. misst er die
                    // Leiste selbst (Kennung "objektleiste").
                    seitenleiste.frame(width: seitenleistenbreite)
                }
            }
            if seiteOffen && schmal && !leisteUnten { schmaleSeite }
            if seiteOffen && leisteUnten { unteneSeite }
            if schmal {
                VStack {
                    BedSelector(model: model,
                                onArrange: { zeigeArrange = true },
                                onOpenSelection: { zeigeBettwahl = true })
                        .padding(.leading, ps.pt(74))
                        .padding(.top, werkzeugleisteHoehe)
                    Spacer()
                }
                .zIndex(80)
            }
            if !hinderungsgruende.isEmpty {
                SliceBlockerSheet(gruende: hinderungsgruende) { hinderungsgruende = [] }
            }
            if zeigeBettwahl {
                BedSelectionOverlay(model: model, isPresented: $zeigeBettwahl)
            }
            // Die Einstellungen schweben ueber der Platte statt sie zu
            // ersetzen: mit einem Rand ringsherum sieht man, dass es
            // weiter um dieses Projekt geht.
            if let tab = einstellungenTab {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()
                    .onTapGesture { einstellungenTab = nil }
                SettingsView(model: model,
                             startTab: tab,
                             onClose: { einstellungenTab = nil })
                    .background(PrusaColors.background)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(10)))
                    .overlay(RoundedRectangle(cornerRadius: ps.pt(10))
                        .stroke(PrusaColors.divider, lineWidth: 1))
                    .shadow(radius: 24)
                    .padding(ps.pt(schmal ? 10 : 28))
            }
            if let pending = model.pendingPresetSwitch {
                presetSwitchDialog(pending)
            }
            PSMarke(name: "arbeitsbereich")
        }
        // Ohne ausgewaehltes Objekt gibt es nichts zu bemalen. Ein
        // aktives Werkzeug ohne Ziel waere ein Zustand, aus dem man nur
        // schwer wieder herausfindet: der Viewport reagiert dann auf
        // keine Geste mehr wie erwartet.
        .onChange(of: model.selectedId) { neu in
            if neu == nil { maloptionen.tool = nil }
        }
        // Was sich auf dem Bett ändert, macht eine ausgegebene Platte
        // hinfällig - sonst gäbe man eine Anordnung von vorhin weiter.
        .onChange(of: model.sceneRevision) { _ in
            platte = nil
            if vorschau && model.previewSnapshot() == nil {
                vorschauSchliessen()
            }
        }
        // Wer auf "Vorschau" tippt und dafuer warten musste, will danach
        // die Wege sehen - nicht die Zusammenfassung.
        .onChange(of: model.progress) { neu in
            guard nachDemSchnittZeigen else { return }
            switch neu {
            case .done:
                nachDemSchnittZeigen = false
                model.dismissProgress()
                vorschauUmschalten()
            case .failed, .cancelled:
                nachDemSchnittZeigen = false
            default:
                break
            }
        }
        .sheet(isPresented: $zeigeDrucker) {
            auswahlblatt(titel: PsUiCatalog.tr("Printer")) {
                DruckerAuswahlView(model: model, onSetup: {
                    zeigeDrucker = false
                    onPrinterSetup()
                }) {
                    model.selectPreset(.printer, $0)
                }
            }
        }
        .sheet(isPresented: $zeigeMaterial) {
            auswahlblatt(titel: PsUiCatalog.tr("Filament")) {
                MaterialAuswahlView(
                    model: model,
                    gewaehlt: materialZiel.map { model.extruderFilament($0) }
                        ?? model.selectedPreset(for: "filament") ?? ""
                ) { name in
                    if let index = materialZiel {
                        model.setExtruderFilament(index, name)
                    } else {
                        model.selectPreset(.filament, name)
                    }
                }
            }
        }
        .popover(isPresented: $zeigeArrange) {
            ArrangePanel(model: model, isPresented: $zeigeArrange)
        }
        .sheet(isPresented: $zeigeGcodeMarken) {
            CustomGcodeView(model: model) { zeigeGcodeMarken = false }
        }
        .fileImporter(isPresented: $zeigeReparatur,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: false) { ergebnis in
            guard case .success(let urls) = ergebnis, let u = urls.first else { return }
            werkzeugErgebnis = model.repairSTL(u)
        }
        .fileImporter(isPresented: $zeigeWandeln,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: false) { ergebnis in
            guard case .success(let urls) = ergebnis, let u = urls.first else { return }
            // Die Richtung sagt der Dateiname: wer eine .gcode waehlt,
            // will binaer; wer eine .bgcode waehlt, will Text. Eine
            // Auswahl, die man ableiten kann, ist eine zu viel.
            let istBinaer = u.pathExtension.lowercased() == "bgcode"
            werkzeugErgebnis = model.convertGcode(u, toBinary: !istBinaer)
        }
        .fileImporter(isPresented: $zeigeImporter,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: true) { ergebnis in
            guard case .success(let urls) = ergebnis else { return }
            for url in urls {
                // ZIPs (meist von Printables, mit STL und Beiwerk) sind
                // in beiden Zwecken willkommen - eine ZIP ist nie
                // selbst ein Projekt, immer eine Modellquelle.
                if url.pathExtension.lowercased() == "zip" {
                    _ = model.loadZip(url: url)
                    continue
                }
                switch zweck {
                case .modell:  model.load(url: url)
                case .projekt: model.loadProject(url: url)
                }
            }
        }
        .sheet(isPresented: $zeigeAlleProjekte) {
            AllProjectsSheet(onOpen: { url in
                zeigeAlleProjekte = false
                model.loadProject(url: url)
            }, onClose: { zeigeAlleProjekte = false })
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
                .accessibilityIdentifier("verlassen.abbrechen")
            Button(st("Discard", "Verwerfen"), role: .destructive) { onHome() }
                .accessibilityIdentifier("verlassen.verwerfen")
            Button(st("Save", "Sichern")) {
                speichernName = model.proposedProjectName
                model.saveProject(name: speichernName)
                onHome()
            }
            .accessibilityIdentifier("verlassen.sichern")
        } message: {
            Text(st("A second tap on Advanced would discard this project.",
                     "Ein erneutes Tippen auf Advanced würde dieses Projekt verwerfen."))
        }
    }

    /// Der Rahmen um eine Auswahl: Titel, Inhalt, Fertig.
    ///
    /// Das Blatt schliesst sich nicht beim Waehlen - man will
    /// vergleichen und mehrfach umstellen. Geschlossen wird
    /// ausdruecklich.
    @ViewBuilder private func auswahlblatt<Inhalt: View>(
        titel: String,
        @ViewBuilder inhalt: @escaping () -> Inhalt
    ) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(12)) {
            HStack {
                Text(titel.uppercased())
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Spacer()
                Button(st("Done", "Fertig")) {
                    zeigeDrucker = false
                    zeigeMaterial = false
                    materialZiel = nil
                }
                .foregroundStyle(PrusaColors.orange)
                .frame(minHeight: ps.touch(44))
                .accessibilityIdentifier("auswahl.fertig")
            }
            ScrollView { inhalt() }
        }
        .padding(ps.pt(16))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
    }

    // MARK: - Werkzeuge

    /// Einen Pinsel an- oder ausschalten.
    ///
    /// Dasselbe Werkzeug noch einmal antippen heisst aus - wie bei den
    /// Griffen. Und ein Pinsel schliesst das Flaechenwerkzeug aus: beide
    /// wollen dieselbe Beruehrung.
    private func malwerkzeugUmschalten(_ werkzeug: PsmCore.PaintTool) {
        if maloptionen.tool == werkzeug {
            maloptionen.tool = nil
        } else {
            maloptionen.tool = werkzeug
            maloptionen.state = 1
            maloptionen.normalizeForTool()
            aufFlaeche = false
            reiter = .werkzeuge
            // Seit dem Akkordeon oeffnet der Reiter allein nichts mehr: bis
            // zum 16.09.2026 ging die Seite auf, aber "Werkzeuge" mit Pinsel,
            // Zustand und Zaehler blieb zugeklappt (Zwilling: AdvancedWorkspaceView.kt).
            offeneBereiche.insert(kennung(.werkzeuge))
            werkzeugeZiel += 1
            seiteOffen = true
        }
    }

    private var werkzeugleiste: some View {
        // Umbrechen statt scrollen: auf dem Telefon war die Leiste
        // abgeschnitten und musste gewischt werden (Nils, 14.09.2026).
        // Die gemessene Hoehe wandert in den Zustand - Bettkarte und Seite
        // richten sich danach. Gegenstueck: FlowRow in AdvancedWorkspaceView.kt.
        Group {
            FlowLayout(spacing: ps.pt(4)) {
                werkzeug("house", st("Start", "Start"), kennung: "kopf.start", aktion: nachHauseGehen)
                trenner
                werkzeug("doc", st("New", "Neu"), kennung: "projekt.neu") {
                    model.newProject()
                }
                werkzeug("folder", st("Open", "Öffnen"), kennung: "projekt.oeffnen") {
                    zweck = .projekt
                    zeigeImporter = true
                }
                werkzeug("clock", st("Projects", "Projekte"), kennung: "projekt.alle") {
                    zeigeAlleProjekte = true
                }
                werkzeug("square.and.arrow.down", st("Save", "Sichern"), kennung: "projekt.sichern") {
                    speichernName = model.proposedProjectName
                    zeigeSpeichernName = true
                }
                // "View" (Kamera zuruecksetzen) ist hier entfernt worden - die
                // untere ansichtsleiste hat mit "3D" denselben Knopf, und zwei
                // Wege zum selben Ergebnis waren nur ein zweiter Punkt, an dem
                // man nachdenken musste.
                werkzeug(vorschau ? "cube.fill" : "square.stack.3d.up",
                         vorschau ? st("Bed", "Bett") : st("Preview", "Vorschau"),
                         kennung: "werkzeug.vorschau") { vorschauZeigen() }
                trenner
                // Printers und App Settings stehen jetzt unten links in
                // der Werkzeugschiene, siehe dort - hier blieb nur, was
                // sich nicht sinnvoll dorthin verschieben liess.
                werkzeug("square.righthalf.filled", "Simple", kennung: "simple.oeffnen", aktion: onOpenSimple)
                Spacer(minLength: 0)
                werkzeug(seiteOffen ? "sidebar.right" : "sidebar.left", st("Panel", "Leiste"),
                         kennung: "advanced.seite") { seiteOffen.toggle() }
            }
            .padding(.horizontal, ps.pt(8))
            .padding(.vertical, ps.pt(4))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PrusaColors.panel)
        .background(GeometryReader { g in
            Color.clear.preference(key: WerkzeugleisteHoehe.self, value: g.size.height)
        })
        .onPreferenceChange(WerkzeugleisteHoehe.self) { werkzeugleisteHoehe = $0 }
    }

    /// Feste Blickrichtungen.
    ///
    /// Mit dem Finger eine saubere Draufsicht zu drehen ist Gluecksache -
    /// und genau die braucht man beim Anordnen am haeufigsten.
    private var ansichtsleiste: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(3)) {
                // Zurueck und Vor stehen am Anfang der unteren Leiste:
                // sie sind das, was man am haeufigsten braucht, und
                // unten links liegt der Daumen ohnehin. In der linken
                // Spalte standen sie ganz unten - am weitesten weg von
                // allem, was man tut.
                zurueckKnopf(st("Undo", "Zurück"), "arrow.uturn.backward",
                             moeglich: !model.undoLabel.isEmpty,
                             kennung: "advanced.zurueck") { model.undo() }
                zurueckKnopf(st("Redo", "Vor"), "arrow.uturn.forward",
                             moeglich: !model.redoLabel.isEmpty,
                             kennung: "advanced.wiederholen") { model.redo() }
                Divider().frame(height: ps.pt(24)).overlay(PrusaColors.divider)
                    .padding(.horizontal, ps.pt(4))
                // Nicht "Iso": der Name ist in der CAD-Welt richtig und
                // sonst nirgends. Neben Oben/Vorn/Hinten steht damit ein
                // Wort, das als einziges keine Richtung nennt.
                // Nur noch "3D" (zurueck zur Schraegansicht): die festen
                // Richtungen liegen seit dem 14.09.2026 auf dem Ansichtswuerfel
                // oben rechts im Viewport (UI/Ansichtswuerfel.swift).
                blickwinkel(st("3D", "3D"), .iso)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ps.pt(8))
            .padding(.vertical, ps.pt(3))
        }
        .background(PrusaColors.background)
    }

    /// Ein Knopf der unteren Leiste, ausgegraut wenn es nichts zu tun gibt.
    private func zurueckKnopf(_ label: String,
                              _ symbol: String,
                              moeglich: Bool,
                              kennung: String,
                              aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            HStack(spacing: ps.pt(4)) {
                Image(systemName: symbol)
                    .font(.system(size: ps.font(14)))
                Text(label)
                    .font(.system(size: ps.font(11)))
            }
            .foregroundStyle(moeglich ? PrusaColors.textPrimary
                             : PrusaColors.textMuted.opacity(0.4))
            .padding(.horizontal, ps.pt(10))
            .frame(height: ps.touch(40))
            .background(PrusaColors.panelRaised)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!moeglich)
        .accessibilityIdentifier(kennung)
    }

    private func blickwinkel(_ label: String, _ preset: PsmViewport.ViewPreset) -> some View {
        Button {
            ansicht = preset
            ansichtZaehler += 1
        } label: {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(12))
                .frame(minHeight: ps.touch(38))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("ansicht." + label)
    }

    private func schritt(_ glyph: String,
                         _ label: String,
                         kennung: String,
                         aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            HStack(spacing: ps.pt(4)) {
                Text(glyph).font(.system(size: ps.font(14)))
                if !label.isEmpty {
                    // Gekuerzt: die ganze Beschriftung machte den Knopf
                    // dreimal so breit wie seinen Nachbarn, und was
                    // zurueckgenommen wird, erkennt man am Anfang.
                    Text(label.count > 14 ? String(label.prefix(13)) + "…" : label)
                        .font(.system(size: ps.font(10)))
                        .lineLimit(1)
                }
            }
            .foregroundStyle(label.isEmpty ? PrusaColors.textMuted : PrusaColors.textPrimary)
            .padding(.horizontal, ps.pt(10))
            .frame(minHeight: ps.touch(38))
            .background(PrusaColors.panelRaised)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(label.isEmpty)
        .accessibilityIdentifier(kennung)
    }

    private var trenner: some View {
        Rectangle()
            .fill(PrusaColors.divider)
            .frame(width: 1, height: ps.pt(30))
            .padding(.horizontal, ps.pt(4))
    }

    private func werkzeug(_ symbol: String,
                          _ label: String,
                          kennung: String,
                          aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: ps.pt(2)) {
                Image(systemName: symbol).font(.system(size: ps.font(16)))
                Text(label)
                    .font(.system(size: ps.font(8)))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(PrusaColors.textPrimary)
            .frame(width: ps.pt(60), height: ps.touch(46))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    // MARK: - Bett

    @ViewBuilder private var arbeitsflaeche: some View {
        if let session = model.sessionHandle {
            ZStack(alignment: .bottomLeading) {
                ViewportView(
                    session: session,
                    shaderDir: model.shaderDir,
                    selectedId: model.selectedId ?? -1,
                    selectedIds: Array(model.selectedIds),
                    invalidateKey: model.sceneRevision,
                    gizmo: maloptionen.tool == nil ? gizmo : .none,
                    paintOptions:
                        maloptionen.tool == nil ? nil : maloptionen,
                    viewportMode: vorschau ? .preview : .editor,
                    multiBedRender: multiBedRenderGespeichert,
                    focusBedIndex: model.activeBedIndex,
                    focusBedKey: model.focusBedKey,
                    bedNamen: multiBedRenderGespeichert
                        ? model.beds.sorted(by: { $0.index < $1.index })
                            .map { model.bedLabel($0.index) }
                        : [],
                    layerRange: vorschau && !previewRange.isEmpty
                        ? (Int32(previewRange.lower) ... Int32(previewRange.upper))
                        : nil,
                    moveRange: vorschau && moveRangeGrenzen != nil
                        ? (moveRangeUnten...moveRangeOben)
                        : nil,
                    previewView: previewView,
                    previewRoles:
                        finalPreview?.roles.map(\.role) ?? [],
                    hiddenPreviewRoles: hiddenPreviewRoles,
                    previewExtruders:
                        finalPreview?.extruders.map(\.extruder) ?? [],
                    hiddenPreviewExtruders: hiddenPreviewExtruders,
                    resetViewKey: ansichtZuruecksetzen,
                    viewPreset: ansicht,
                    viewPresetKey: ansichtZaehler,
                    onPreviewLoaded: { anzahl in
                        guard let snapshot = finalPreview,
                              anzahl == Int32(snapshot.layers.count),
                              anzahl > 0 else {
                            vorschauSchliessen()
                            return
                        }
                    },
                    onMoveRangeBounds: { grenzen in
                        guard let grenzen else {
                            moveRangeGrenzen = nil
                            return
                        }
                        // Nur bei tatsaechlich neuen Grenzen zuruecksetzen -
                        // sonst ueberschreibt jeder Bildaufbau (mehrmals
                        // pro Sekunde) eine laufende Ziehgeste.
                        if moveRangeGrenzen != grenzen {
                            moveRangeGrenzen = grenzen
                            moveRangeUnten = grenzen.lowerBound
                            moveRangeOben = grenzen.upperBound
                        }
                    },
                    onSelect: { model.select($0 < 0 ? nil : $0) },
                    onObjectChanged: { model.refresh() },
                    // Solange ein Malwerkzeug gewaehlt ist, geht jede
                    // Beruehrung an die Flaeche statt an die Kamera. Der
                    // Viewport unterscheidet das daran, ob hier jemand
                    // zuhoert.
                    // Zwei Werkzeuge teilen sich dieselbe Beruehrung: der
                    // Pinsel und das Hinlegen auf eine Flaeche. Beide
                    // brauchen ein getroffenes Dreieck, nur macht jedes
                    // etwas anderes damit.
                    onSurfaceTap: aufFlaeche ? { treffer in
                        model.layOnFace(
                            treffer.objectId,
                            instance: Int(treffer.instanceIndex),
                            volume: Int(treffer.volumeIndex),
                            facet: Int(treffer.facetIndex))
                    } : nil,
                    onSurfaceStroke:
                        maloptionen.tool == nil ? nil : {
                            treffer, vorher in
                            /*
                             * Eine Capsule verbindet nur Treffer desselben
                             * Instanz-Volumens. Beim Sprung auf eine andere
                             * Kopie oder ein anderes Volumen beginnt ein
                             * neuer Tupfer; deren Weltpunkt darf nicht mit
                             * der aktuellen Instanzmatrix zurückgerechnet
                             * werden.
                             */
                            let vorigePosition =
                                vorher.flatMap { alt
                                    -> (Float, Float, Float)? in
                                    guard alt.objectId == treffer.objectId,
                                          alt.instanceIndex ==
                                            treffer.instanceIndex,
                                          alt.volumeIndex ==
                                            treffer.volumeIndex
                                    else { return nil }
                                    return alt.position
                                }
                            model.paint(
                                treffer.objectId,
                                instance: Int(treffer.instanceIndex),
                                volume: Int(treffer.volumeIndex),
                                facet: Int(treffer.facetIndex),
                                hit: treffer.position,
                                previous: vorigePosition,
                                options: maloptionen)
                        },
                    onLayerVisualizationChanged: {
                        schichthoehenDarstellung = $0
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Eine eigene Kennung, damit Gesten im Test die Flaeche
                // treffen und nicht die Marke des Bildschirms. Am Viewport
                // ist das gefahrlos: er ist eine einzelne UIView ohne
                // SwiftUI-Kinder, die Kennung vererbt sich an niemanden.
                .accessibilityIdentifier("viewport")

                if !vorschau, let grenzen = schichthoehenDarstellung {
                    LayerProfileViewportLegend(
                        minHeight: Double(grenzen.minHeight),
                        maxHeight: Double(grenzen.maxHeight))
                        .padding(ps.pt(12))
                }

                // Der Projekt-Hinweis gehoert auch hierher: bis zum 16.09.2026 lief
                // projectNotice im Advanced ins Leere (Zwilling: AdvancedWorkspaceView.kt). 100 pt: ueber dem Ansichtswuerfel.
                if let hinweis = model.projectNotice {
                    ProjektHinweis(text: hinweis, model: model, abstandUnten: ps.pt(100))
                }

                if vorschau, previewRange.layerCount > 1 {
                    // Linker Rand: Schichtbereich, senkrecht - wie der
                    // Desktop-Regler links vom Bett.
                    HStack {
                        DualHandleSlider(
                            untererWert: previewSchichtUnten,
                            obererWert: previewSchichtOben,
                            bereich: 0...Int32(max(previewRange.layerCount - 1, 1)),
                            achse: .senkrecht)
                            // Unten 98 statt 48: seit der Ansichtswuerfel (84 pt + 6 pt)
                            // unten links liegt, sass der untere Griff auf seiner
                            // "Front"-Flaeche (Zwilling: AdvancedWorkspaceView.kt).
                            .padding(.leading, ps.pt(8))
                            .padding(.top, ps.pt(48))
                            .padding(.bottom, ps.pt(98))
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if vorschau, let grenzen = moveRangeGrenzen, grenzen.lowerBound < grenzen.upperBound {
                    // Unterer Rand: Werkzeugweg innerhalb der Schicht,
                    // waagerecht - wie der Desktop-Regler unter dem Bett.
                    VStack {
                        Spacer(minLength: 0)
                        DualHandleSlider(
                            untererWert: $moveRangeUnten,
                            obererWert: $moveRangeOben,
                            bereich: grenzen,
                            achse: .waagerecht)
                            // Links 98: rechts neben dem Ansichtswuerfel beginnen.
                            .padding(.leading, ps.pt(98))
                            .padding(.trailing, ps.pt(56))
                            .padding(.bottom, ps.pt(14))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                // Hochformat mit offener Seite: nur das Bett abdunkeln, ein
                // Tipp darauf schliesst die Seite. Schiene, Werkzeugleiste
                // und untere Leiste bleiben bedienbar - vorher lag die
                // Schliessflaeche ueber allem, und der erste Tipp auf
                // "Drucker" oder "Zurueck" schloss nur die Seite
                // (Galaxy S23 FE, 14.09.2026). Gegenstueck: arbeitsflaeche drueben.
                // Gilt auch fuer die untere Leiste: deren eigene Schliessflaeche
                // lag ueber Werkzeugleiste und Bettkarte (S23 FE, 16.09.2026).
                if seiteOffen && (schmal || leisteUnten) {
                    PrusaColors.background.opacity(0.6)
                        .contentShape(Rectangle())
                        .onTapGesture { seiteOffen = false }
                }

                // Dieselbe schwebende Leiste wie im Einfachen Modus: was man
                // am ausgewaehlten Objekt am haeufigsten tut, gehoert an das
                // Objekt und nicht in eine Spalte am Rand. Im Advanced Mode
                // fehlte sie - dort war jeder Handgriff ein Weg nach rechts.
                //
                // Sitzt bewusst HIER, innerhalb von arbeitsflaeche statt
                // in der aeusseren ZStack (wo sie vorher stand): dieser
                // ZStack ist bereits exakt der freie Viewport, ohne
                // Werkzeugleiste und Bettwaehler darueber. Der vorige Code
                // ratete stattdessen einen festen Abstand von 96/118pt von
                // ganz oben, um die Werkzeugleiste zu ueberspringen - auf
                // Nils' Geraet reichte das nicht (die Leiste ragte in die
                // Menuezeile hinein). Hier drin braucht es dafuer nur noch
                // einen kleinen Rand, kein Raten mehr. Aus demselben Grund
                // faellt auch die maxBreite-Berechnung fuer die Seitenleiste
                // weg: dieser ZStack endet schon vor der Seitenleiste.
                if let id = model.selectedId,
                   let objekt = model.objects.first(where: { $0.id == id }),
                   !vorschau {
                    VStack {
                        HStack {
                            Spacer(minLength: 0)
                            SimpleObjectBarView(
                                model: model,
                                objekt: objekt,
                                zeigtZurueck: false,
                                onClearSelection: { model.select(nil) },
                                onFlaechenwahl: { aufFlaeche = $0 },
                                gizmo: $gizmo,
                                maxBreite: nil)
                            // Kein `.fixedSize(horizontal:)` mehr: die
                            // Leiste ist innen eine waagerechte
                            // ScrollView, und unter fixedSize nimmt die
                            // ihre Inhaltsbreite statt zu scrollen. Ohne
                            // gilt wieder, was drinsteht - hoechstens 640
                            // breit, der Rest scrollt, wie auf Android.
                            //
                            // Geschichte (11./12.09.2026): vier Anlaeufe
                            // gegen "die Leiste ragt bis 779 in die
                            // Seitenleiste" - Rahmen um die Mitte, Deckel
                            // ueber maxBreite, Rahmen um diesen Behaelter,
                            // fixedSize weg - aenderten nichts, weil die
                            // Leiste nie hineinragte. Der Test mass
                            // objekt.entfernen, den letzten Knopf der
                            // ScrollView (Inhalt ~800 breit), und XCUITest
                            // meldet auch weggescrollte Knoepfe mit
                            // Rahmen: 74 + 705 = 779. Die Leiste endet bei
                            // 690. Seit dem 12.09. misst der Test die
                            // Leiste (Kennung "objektleiste").
                            Spacer(minLength: 0)
                        }
                        .padding(.top, ps.pt(10))
                        // Im Hochformat liegt die Seite ueber dem rechten
                        // Rand: die Leiste weicht ihr aus, sonst sind die
                        // Griffe unerreichbar. Gegenstueck: seiteRechts drueben.
                        .padding(.trailing, (schmal && seiteOffen && !leisteUnten) ? ps.pt(320) : 0)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        } else {
            PrusaColors.background.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// Was jede Rolle gekostet hat.
    ///
    /// Nur bei mehr als einem Extruder: bei einem einfarbigen Druck
    /// steht dieselbe Zahl zwei Zeilen darüber, und eine Wiederholung
    /// ist keine Auskunft.
    ///
    /// Der Reinigungsturm bekommt eine eigene Spalte. Bei einem
    /// Mehrfarbdruck ist er oft die Hälfte des Verbrauchs, und wer nur
    /// die Modellzahl sieht, wundert sich über die Rolle.
    @ViewBuilder private var verbrauchJeExtruder: some View {
        let verbrauch = model.extruderUsage()
        if verbrauch.count > 1 {
            Divider().overlay(PrusaColors.divider).padding(.vertical, ps.pt(2))
            HStack {
                Text(st("Per tool", "Je Werkzeug"))
                    .font(.system(size: ps.font(10), weight: .semibold))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                Text(st("Model", "Modell") + " · " + st("Tower", "Turm"))
                    .font(.system(size: ps.font(9)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            ForEach(Array(verbrauch.enumerated()), id: \.offset) { _, u in
                HStack(spacing: ps.pt(6)) {
                    RoundedRectangle(cornerRadius: ps.pt(2))
                        .fill(Color(hexString: model.extruderColor(Int(u.extruder)))
                              ?? PrusaColors.panelRaised)
                        .frame(width: ps.pt(10), height: ps.pt(10))
                        .overlay(RoundedRectangle(cornerRadius: ps.pt(2))
                            .stroke(PrusaColors.divider, lineWidth: 1))
                    Text("T\(u.extruder + 1)")
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                    Spacer()
                    Text(String(format: "%.1f", u.volumeMm3 / 1000.0) + " cm³")
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textPrimary)
                    if u.wipeTowerMm3 + u.flushMm3 > 0 {
                        Text("+ " + String(format: "%.1f",
                                           (u.wipeTowerMm3 + u.flushMm3) / 1000.0))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.orange)
                    }
                }
            }
            .accessibilityIdentifier("seite.verbrauch")
        }
    }

    /// PreviewRange rechnet in Int, der Kern-Regler in Int32 - hier nur
    /// die Bruecke dazwischen, mit demselben Clamping wie PreviewRange
    /// selbst (setLower/setUpper).
    private var previewSchichtUnten: Binding<Int32> {
        Binding(
            get: { Int32(previewRange.lower) },
            set: { previewRange.setLower(Int($0)) })
    }
    private var previewSchichtOben: Binding<Int32> {
        Binding(
            get: { Int32(previewRange.upper) },
            set: { previewRange.setUpper(Int($0)) })
    }

    /// Vorschau oeffnen - und nur dann rechnen, wenn es sein muss.
    private func vorschauZeigen() {
        if vorschau { vorschauSchliessen(); return }
        if model.sliceResultIsCurrent || model.lastSliceWasRemote {
            vorschauUmschalten()
            return
        }
        let gruende = model.sliceBlockers
        if gruende.isEmpty {
            nachDemSchnittZeigen = true
            model.slice()
        } else {
            hinderungsgruende = gruende
        }
    }

    private func vorschauUmschalten() {
        guard let snapshot = model.previewSnapshot(),
              !snapshot.layers.isEmpty else {
            vorschauSchliessen()
            return
        }
        // In der Vorschau gibt es keine Objekte zum Anfassen, und kein
        // Werkzeug, das auf sie zeigt.
        model.select(nil)
        maloptionen.tool = nil
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
        moveRangeGrenzen = nil
    }

    private func schneiden() {
        let gruende = model.sliceBlockers
        if gruende.isEmpty { model.slice() } else { hinderungsgruende = gruende }
    }

    /// Dieselben Hinderungsgruende wie beim einzelnen Schnitt - nur ohne
    /// Bett auf dem Bett ist "alle Betten schneiden" derselbe leere
    /// Auftrag.
    private func alleBettenSchneiden() {
        let gruende = model.sliceAllBlockers
        if gruende.isEmpty { model.sliceAll() } else { hinderungsgruende = gruende }
    }

    // MARK: - Seitenleiste

    private var seitenleiste: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                // Die Leiste traegt einen Namen, damit ein Test zu einem
                // Bereich blaettern kann. Ohne das melden die Tests
                // "Der Bereich Objekte ist nicht erreichbar", sobald er
                // unter dem Fensterrand liegt - und das sagt dann nichts
                // ueber die App, sondern ueber die Fensterhoehe.
                ScrollView {
                    VStack(alignment: .leading, spacing: ps.pt(6)) {
                        // Die drei Einstellungsseiten stehen oben im Band:
                        // sie wirken auf das Profil, und das Profil steht
                        // rechts. Oben in der Werkzeugleiste gehoert hin,
                        // was auf den Viewport wirkt.
                        einstellungsbereiche
                        Divider().overlay(PrusaColors.divider)
                        // Untereinander statt hinter Reitern: vier Reiter
                        // heissen, dass drei Viertel des Gesuchten unsichtbar
                        // sind. So sieht man alle Ueberschriften und klappt
                        // auf, was man braucht.
                        ForEach(Array(InspektorReiter.allCases.enumerated()),
                                id: \.offset) { _, r in
                            bereich(r)
                                .id(kennung(r))
                        }
                    }
                    .padding(ps.pt(12))
                }
                .background {
                    GeometryReader { geo in
                        Color.clear.preference(
                            key: AdvancedSeitenleistenRahmenPreferenceKey.self,
                            value: geo.frame(in: .global))
                    }
                }
                .onPreferenceChange(AdvancedSeitenleistenRahmenPreferenceKey.self) {
                    seitenleistenRahmen = $0
                    bearbeitenFokussierenWennNoetig(proxy, seitenleiste: $0)
                }
                .onPreferenceChange(AdvancedInspectorSichtbereichPreferenceKey.self) {
                    bearbeitenRahmen = $0
                    bearbeitenFokussierenWennNoetig(proxy, bearbeiten: $0)
                }
                .onChange(of: seitenleistenZiel) { _ in
                    bearbeitenFokussierenWennNoetig(proxy)
                }
                // Der Werkzeuge-Bereich liegt am Ende der Leiste: wer einen
                // Pinsel waehlt, soll ihn sehen und nicht erst suchen. Einen
                // Umlauf spaeter, damit die eben geoeffnete Leiste (schmales
                // Fenster) schon ausgelegt ist.
                .onChange(of: werkzeugeZiel) { _ in
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(kennung(.werkzeuge), anchor: .top) }
                    }
                }
                .accessibilityIdentifier("seitenleiste")
            }
            // Ausserhalb der Reiter: was ein Schnitt ergeben hat, ist
            // keine Frage des gerade offenen Reiters.
            abschluss
        }
        .background(PrusaColors.background)
    }

    /// Statistik und Legende der laufenden Vorschau - an derselben
    /// Stelle, an der vorher die schwebende "Final G-code"-Karte lag.
    /// Die beiden Schichtregler stehen jetzt am Viewport-Rand, nicht
    /// mehr hier drin.
    @ViewBuilder private var vorschauInhalt: some View {
        if vorschau, let snapshot = finalPreview {
            VStack(alignment: .leading, spacing: ps.pt(9)) {
                PSMarke(name: "vorschau.panel")
                HStack {
                    Text(st("G-code preview", "G-Code-Vorschau"))
                        .font(.system(size: ps.font(13), weight: .semibold))
                    Spacer()
                    Button(action: vorschauSchliessen) {
                        Label(st("Editor", "Editor"), systemImage: "cube")
                            .font(.system(size: ps.font(11), weight: .semibold))
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("vorschau.editor")
                }
                PreviewStatsRow(range: previewRange, snapshot: snapshot)
                PreviewLegendPicker(
                    snapshot: snapshot,
                    view: $previewView,
                    hiddenRoles: $hiddenPreviewRoles,
                    hiddenExtruders: $hiddenPreviewExtruders)
            }
            .padding(.bottom, ps.pt(4))
        }
    }

    /// Ersetzt den Knopfbereich je nach Stand des letzten Schnitts.
    ///
    /// Frueher oeffnete "Slice now" ein Blatt, das erst wieder
    /// weggetippt werden musste, bevor man exportieren oder senden
    /// konnte - ein Zwischenschritt, der bei jedem einzelnen Schnitt
    /// im Weg stand. Jetzt steht an genau der Stelle, an der vorher
    /// "Slice now" war, nach einem gueltigen Ergebnis direkt Export
    /// und Senden - und sobald sich das Projekt aendert (sliceResultIsCurrent
    /// wird falsch), steht dort wieder "Slice now".
    @ViewBuilder private var schneidenBereich: some View {
        switch model.progress {
        case .running(let prozent, let phase):
            VStack(spacing: ps.pt(6)) {
                HStack {
                    Text("\(prozent) %")
                        .font(.system(size: ps.font(13), weight: .semibold))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(phase)
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .lineLimit(1)
                    Spacer()
                }
                ProgressView(value: Double(prozent), total: 100)
                    .tint(PrusaColors.orange)
                Button { model.cancel() } label: {
                    Text(st("Cancel", "Abbrechen"))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(40))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("slice.abbrechen")
            }
            .accessibilityIdentifier("slicen")

        case .done(_, _, _) where model.sliceResultIsCurrent || model.lastSliceWasRemote:
            VStack(spacing: ps.pt(8)) {
                if model.gcodeURLs.count > 1 {
                    ShareLink(items: model.gcodeURLs) {
                        exportKnopf(st("Export all", "Alle exportieren"))
                    }
                    .accessibilityIdentifier("slice.sichern")
                } else if let url = model.gcodeURL {
                    ShareLink(item: url) {
                        exportKnopf(st("Export G-Code", "G-Code exportieren"))
                    }
                    .accessibilityIdentifier("slice.sichern")
                    Button { onSendToPrinter(url) } label: {
                            Text(st("Send to printer", "An Drucker senden"))
                                .font(.system(size: ps.font(13), weight: .medium))
                                .foregroundStyle(PrusaColors.orange)
                                .frame(maxWidth: .infinity, minHeight: ps.touch(40))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("slice.andrucker")
                }
            }

        case .failed(let meldung, _):
            VStack(alignment: .leading, spacing: ps.pt(6)) {
                Text(meldung)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.danger)
                    .fixedSize(horizontal: false, vertical: true)
                schneidenKnopf
            }

        default:
            schneidenKnopf
        }
    }

    private func exportKnopf(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(15), weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: ps.touch(52))
            .background(PrusaColors.orange)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
    }

    private var schneidenKnopf: some View {
        HStack(spacing: ps.pt(8)) {
            Button { schneiden() } label: {
                Text(model.remoteSliceEnabled
                     ? st("Slice on server", "Auf Server slicen")
                     : (model.beds.count > 1
                        ? st("Slice current bed", "Aktuelles Bett slicen")
                        : PsUiCatalog.tr("Slice now")))
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
                    .frame(height: ps.touch(52))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("slicen")

            if remoteSlicePluginAn {
                fernSchnittUmschalter
            }
        }
    }

    /// Lokal/entfernt umschalten - siehe docs/remote-slicing.md. Ohne
    /// eingerichteten Server fuehrt das Tippen erst zur Einrichtung statt
    /// stumm auf einen leeren Host umzuschalten. Schmal und neben dem
    /// Knopf statt darueber - der Schnitt-Knopf bleibt der groesste,
    /// meistgetroffene Ziel, der Umschalter ein Nebenknopf daneben.
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
                .font(.system(size: ps.font(18)))
                .foregroundStyle(model.remoteSliceEnabled
                                 ? PrusaColors.orange : PrusaColors.textMuted)
                .frame(width: ps.touch(52), height: ps.touch(52))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("slicen.fern.umschalten")
        .accessibilityLabel(model.remoteSliceEnabled
                            ? st("Remote slicing on", "Remote Slicing an")
                            : st("Remote slicing off", "Remote Slicing aus"))
    }

    /// Das untere Ende der Seitenleiste: was der letzte Schnitt ergeben
    /// hat, und der Knopf für den nächsten.
    ///
    /// Wie in PrusaSlicers Sidebar. Die Zahlen gehören neben die
    /// Profile, die sie erzeugt haben — wer das Druckprofil wechselt,
    /// sieht in derselben Spalte, was das an Zeit kostet.
    private var abschluss: some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Divider().overlay(PrusaColors.divider)
            if let s = model.stats, model.sliceResultIsCurrent || model.lastSliceWasRemote {
                let zeilen = SliceSummary.shared.rows(
                    seconds: s.printTimeSeconds,
                    grams: s.filamentGrams,
                    millimetres: s.filamentMm,
                    cost: s.cost,
                    objects: Int32(model.objects.count))
                VStack(spacing: ps.pt(3)) {
                    ForEach(Array(zeilen.enumerated()), id: \.offset) { _, zeile in
                        HStack {
                            Text(zeile.label)
                                .font(.system(size: ps.font(11)))
                                .foregroundStyle(PrusaColors.textMuted)
                            Spacer()
                            Text(zeile.value)
                                .font(.system(size: ps.font(12)))
                                .foregroundStyle(PrusaColors.textPrimary)
                        }
                    }
                }
                .accessibilityIdentifier("seite.zusammenfassung")
                verbrauchJeExtruder
            } else {
                Text(st("Not sliced yet", "Noch nicht gesliced"))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            if let warnung = model.memoryWarning {
                Text(warnung)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            vorschauInhalt
            schneidenBereich

            // Nur sinnvoll, wenn es ueberhaupt etwas zu verteilen gibt.
            if model.beds.count > 1 {
                Button { alleBettenSchneiden() } label: {
                    if let fortschritt = model.sliceAllProgress {
                        Text(st("Bed \(fortschritt.bed)/\(fortschritt.total)",
                                "Bett \(fortschritt.bed)/\(fortschritt.total)"))
                            .font(.system(size: ps.font(13), weight: .medium))
                            .foregroundStyle(PrusaColors.orange)
                            .frame(maxWidth: .infinity)
                            .frame(height: ps.touch(40))
                    } else {
                        Text(st("Slice all beds", "Alle Betten slicen"))
                            .font(.system(size: ps.font(13), weight: .medium))
                            .foregroundStyle(PrusaColors.orange)
                            .frame(maxWidth: .infinity)
                            .frame(height: ps.touch(40))
                    }
                }
                .buttonStyle(.plain)
                .disabled(model.sliceAllProgress != nil)
                .accessibilityIdentifier("slicen.alle")
            }
        }
        .padding(ps.pt(12))
        .background(PrusaColors.panel)
    }

    /// Profile │ Objekte │ Bearbeiten │ Werkzeuge.
    ///
    /// Die letzten beiden beziehen sich auf ein Objekt und bleiben ohne
    /// Auswahl gesperrt — ein Reiter, den man anwählen kann und der dann
    /// leer ist, ist eine Sackgasse.
    /// Ein aufklappbarer Bereich der Seitenleiste.
    ///
    /// Zu ist der Normalfall fuer alles ausser den Profilen: wer eine
    /// Ueberschrift sieht, weiss, dass es den Bereich gibt, und
    /// entscheidet selbst, ob er ihn braucht.
    @ViewBuilder private func bereich(_ r: InspektorReiter) -> some View {
        let hatAuswahl = model.selectedId != nil
        let moeglich = (r != .bearbeiten && r != .werkzeuge) || hatAuswahl
        let offen = offeneBereiche.contains(kennung(r)) && moeglich
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Button {
                if offeneBereiche.contains(kennung(r)) {
                    offeneBereiche.remove(kennung(r))
                } else {
                    offeneBereiche.insert(kennung(r))
                }
            } label: {
                HStack {
                    Text(reiterName(r).uppercased())
                        .font(.system(size: ps.font(11), weight: .semibold))
                        .foregroundStyle(moeglich ? PrusaColors.textMuted
                                         : PrusaColors.textMuted.opacity(0.4))
                    Spacer()
                    Image(systemName: offen ? "chevron.down" : "chevron.right")
                        .font(.system(size: ps.font(11)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .frame(minHeight: ps.touch(40))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!moeglich)
            .accessibilityIdentifier("inspektor." + kennung(r))

            if offen {
                switch r {
                case .profile:    profilblock
                case .objekte:    objektliste
                case .bearbeiten: bearbeitenBlock
                case .werkzeuge:  werkzeugeBlock
                }
            }
        }
    }

    /// Die drei Einstellungsseiten, als Zeilen im Band.
    private var einstellungsbereiche: some View {
        VStack(spacing: ps.pt(4)) {
            einstellungsZeile("list.bullet.rectangle",
                              PsUiCatalog.tr("Print Settings"),
                              "print", "advanced.printSettings")
            einstellungsZeile("circle.circle",
                              PsUiCatalog.tr("Filament Settings"),
                              "filament", "advanced.filamentSettings")
            einstellungsZeile("printer",
                              // "Printer Settings" fehlt im deutschen Katalog - eigener Text statt tr().
                              st("Printer Settings", "Druckereinstellungen"),
                              "printer", "advanced.printerSettings")
        }
    }

    private func einstellungsZeile(_ symbol: String,
                                   _ label: String,
                                   _ tab: String,
                                   _ kennung: String) -> some View {
        Button { einstellungenTab = tab } label: {
            HStack(spacing: ps.pt(10)) {
                Image(systemName: symbol)
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.orange)
                    .frame(width: ps.pt(22))
                Text(label)
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .lineLimit(1)
                Spacer()
                Text("›")
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            .padding(.horizontal, ps.pt(10))
            .frame(minHeight: ps.touch(44))
            .background(PrusaColors.panelRaised)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private var reiterleiste: some View {
        let hatAuswahl = model.selectedId != nil
        return HStack(spacing: ps.pt(4)) {
            ForEach(Array(InspektorReiter.allCases.enumerated()), id: \.offset) { _, r in
                let an = (r != .bearbeiten && r != .werkzeuge) || hatAuswahl
                let aktiv = r == reiter
                Button { reiter = r } label: {
                    Text(reiterName(r))
                        .font(.system(size: ps.font(12),
                                      weight: aktiv ? .semibold : .regular))
                        .foregroundStyle(aktiv ? .white
                                         : (an ? PrusaColors.textPrimary
                                              : PrusaColors.textMuted.opacity(0.45)))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity)
                        .frame(height: ps.touch(44))
                        .background(aktiv ? PrusaColors.orange : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(7)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!an || aktiv)
                .accessibilityIdentifier("inspektor." + kennung(r))
            }
        }
        .padding(ps.pt(4))
        .background(PrusaColors.panel)
    }

    private func reiterName(_ r: InspektorReiter) -> String {
        switch r {
        case .profile:    return st("Profiles", "Profile")
        case .objekte:    return st("Objects", "Objekte")
        case .bearbeiten: return st("Edit", "Bearbeiten")
        case .werkzeuge:  return st("Tools", "Werkzeuge")
        }
    }

    private func kennung(_ r: InspektorReiter) -> String {
        switch r {
        case .profile:    return "profile"
        case .objekte:    return "objekte"
        case .bearbeiten: return "bearbeiten"
        case .werkzeuge:  return "werkzeuge"
        }
    }

    /// Was am ausgewählten Objekt geändert wird.
    @ViewBuilder private var bearbeitenBlock: some View {
        if let id = model.selectedId,
           let objekt = model.objects.first(where: { $0.id == id }) {
            AdvancedObjectInspectorView(model: model, objekt: objekt, gizmo: $gizmo)
        }
    }

    /// Was mit dem Objekt gemacht wird, ohne es zu vermessen: bemalen,
    /// Schichthöhen — und was mit der ganzen Platte geht.
    @ViewBuilder private var werkzeugeBlock: some View {
        if let id = model.selectedId {
            PaintView(model: model,
                      objektId: id,
                      options: $maloptionen)
        }
        Divider().overlay(PrusaColors.divider)
        Text(st("Whole plate", "Ganze Platte").uppercased())
            .font(.system(size: ps.font(11), weight: .semibold))
            .foregroundStyle(PrusaColors.textMuted)
        // Die Anordnung ist die Arbeit — als STL geht sie in einem
        // Stück an jemanden weiter, der einen anderen Slicer benutzt.
        Button { platte = model.exportPlate() } label: {
            Text(st("Export plate as STL", "Platte als STL ausgeben"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(model.objects.isEmpty
                                 ? PrusaColors.textMuted : PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.objects.isEmpty)
        .accessibilityIdentifier("werkzeuge.platte")
        // Dateiwerkzeuge: sie beziehen sich nicht auf ein Objekt,
        // sondern auf eine Datei. Auswählen, das Werkzeug schreibt eine
        // neue daneben, die geht über das Teilen-Blatt weiter.
        Button { zeigeGcodeMarken = true } label: {
            Text(PsUiCatalog.tr("Custom G-code"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("werkzeuge.gcodemarken")
        Button { zeigeReparatur = true } label: {
            Text(st("Repair STL", "STL reparieren"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("werkzeuge.reparieren")
        Button { zeigeWandeln = true } label: {
            Text(st("Convert G-code", "G-Code wandeln"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("werkzeuge.wandeln")
        if let url = werkzeugErgebnis {
            ShareLink(item: url) {
                Text(st("Share", "Weitergeben") + " · " + url.lastPathComponent)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("werkzeuge.ergebnis.weitergeben")
        }
        if let url = platte {
            ShareLink(item: url) {
                Text(st("Share", "Weitergeben") + " · " + url.lastPathComponent)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("werkzeuge.platte.weitergeben")
        }
    }

    /// Auf schmalen Geraeten als Blatt ueber dem Bett. Die Schliessflaeche
    /// liegt in arbeitsflaeche - nur ueber dem Bett, nicht ueber den Leisten.
    private var schmaleSeite: some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            seitenleiste.frame(width: ps.pt(320))
        }
        // Erst unterhalb von Werkzeugleiste (52 pt) und Bettkarte (touch(52))
        // beginnen: vorher deckte die Bettkarte die erste Zeile der Seite
        // ("Print Settings") halb ab - auf dem iPhone wie auf dem S23 FE
        // (14.09.2026). Gegenstueck: schmaleSeite in AdvancedWorkspaceView.kt.
        .padding(.top, werkzeugleisteHoehe + ps.touch(52))
        .ignoresSafeArea(edges: .bottom)
    }

    /// Experimentelles Hochformat-Layout: dieselbe Seitenleiste, von
    /// unten angedockt statt von rechts - siehe leisteUntenExperimentell.
    ///
    /// Die linke Werkzeugschiene bleibt ausgespart (Breite wie dort:
    /// ps.pt(74)) - sonst liegt der Abdunklungs-Scrim ueber "Drucker"
    /// und "App" in deren Fusszeile, und ein Finger dort schliesst nur
    /// die Seite statt den Knopf zu treffen. Ohne diese beiden kommt
    /// man aus der offenen Seitenleiste nicht mehr zu Druckern oder den
    /// App-Einstellungen.
    private var unteneSeite: some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: ps.pt(74))
            VStack(spacing: 0) {
                // Keine eigene Schliessflaeche mehr - die liegt in arbeitsflaeche
                // nur ueber dem Bett, nicht ueber Werkzeugleiste und Bettkarte.
                Spacer(minLength: 0)
                seitenleiste.frame(height: min(ps.pt(420), ps.windowSize.height * 0.5))
            }
        }
        .ignoresSafeArea(edges: .bottom)
        .accessibilityIdentifier("advanced.leiste.unten")
    }

    /// Drucker, Filament, Druckprofil - die drei Angaben, mit denen
    /// gerechnet wird.
    private var profilblock: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            // Drucker und Filament als Blatt mit denselben Karten wie im
            // Simple Mode. Ein Aufklappmenue mit dreissig rohen
            // Profilnamen ist auf einem Geraet, das man in der Hand
            // haelt, kein Menue, sondern eine Zumutung.
            profilzeile(titel: PsUiCatalog.tr("Printer"), reiter: "printer",
                        kennung: "advanced.wahl.printer") { zeigeDrucker = true }
            profilzeile(titel: PsUiCatalog.tr("Filament"), reiter: "filament",
                        kennung: "advanced.wahl.filament") {
                            materialZiel = nil
                            zeigeMaterial = true
                        }
            // Druckprofile sind eine Handvoll und tragen ihre Auskunft
            // im Namen - dafuer genuegt ein Menue.
            profilwahl(titel: PsUiCatalog.tr("Print settings"),
                       typ: .print, reiter: "print", kennung: "advanced.wahl.print")
            // Ab zwei Extrudern ist "das Material" keine Frage mehr,
            // sondern eine je Position.
            if model.extruderCount > 1 {
                Divider().overlay(PrusaColors.divider)
                ExtruderBank(model: model) { kopf in
                    materialZiel = kopf
                    zeigeMaterial = true
                }
            }
        }
    }

    /// Eine Zeile, die ein Blatt oeffnet.
    private func profilzeile(titel: String,
                             reiter: String,
                             kennung: String,
                             aktion: @escaping () -> Void) -> some View {
        let gewaehlt = model.selectedPreset(for: reiter) ?? ""
        return VStack(alignment: .leading, spacing: ps.pt(2)) {
            Text(titel.uppercased())
                .font(.system(size: ps.font(10), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
            Button(action: aktion) {
                HStack(spacing: ps.pt(6)) {
                    Text(gewaehlt.isEmpty
                         ? st("Not selected", "Nicht gewählt")
                         : EasyModeState.shared.profileDisplayLabel(rawPreset: gewaehlt))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("›")
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(kennung)
        }
    }

    private func profilwahl(titel: String,
                            typ: PsmCore.PresetType,
                            reiter: String,
                            kennung: String) -> some View {
        let gewaehlt = model.selectedPreset(for: reiter) ?? ""
        // Dreissig reichen: mehr passt in kein Menue, und wer wirklich
        // sucht, ist auf der Einstellungsseite besser aufgehoben.
        let namen = Array(model.presetNames(typ).prefix(30))
        return VStack(alignment: .leading, spacing: ps.pt(2)) {
            Text(titel.uppercased())
                .font(.system(size: ps.font(10), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
            HStack(spacing: ps.pt(6)) {
                Menu {
                    ForEach(namen, id: \.self) { name in
                        Button {
                            model.selectPreset(typ, name)
                        } label: {
                            Text(EasyModeState.shared.profileDisplayLabel(rawPreset: name))
                        }
                    }
                } label: {
                    HStack(spacing: ps.pt(6)) {
                        Text(gewaehlt.isEmpty
                             ? st("Not selected", "Nicht gewählt")
                             : EasyModeState.shared.profileDisplayLabel(rawPreset: gewaehlt))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                        Text("▾")
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    .padding(.horizontal, ps.pt(10))
                    .frame(maxWidth: .infinity, minHeight: ps.touch(44))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
                }
                .accessibilityIdentifier(kennung)
                // Der Stift fuehrt dorthin, wo dieses Profil im Einzelnen
                // steht - das Menue waehlt nur aus.
                Button { onSettings(reiter) } label: {
                    Text("⚙")
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.touch(44), height: ps.touch(44))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(kennung + ".bearbeiten")
            }
        }
    }

    private var objektliste: some View {
        // Ohne Suchtext alle. Gesucht wird im Namen, und ohne
        // Rücksicht auf Groß- und Kleinschreibung: wer "würfel" tippt,
        // meint auch "Würfel".
        let sichtbar = objektSuche.trimmingCharacters(in: .whitespaces).isEmpty
            ? model.objects
            : model.objects.filter {
                $0.name.range(of: objektSuche, options: .caseInsensitive) != nil
              }
        return VStack(alignment: .leading, spacing: ps.pt(6)) {
            if model.objects.isEmpty {
                VStack(spacing: ps.pt(6)) {
                    Text(st("No objects yet", "Noch keine Objekte"))
                        .font(.system(size: ps.font(15), weight: .semibold))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(st("Import with + in the tool rail",
                            "Über + in der Werkzeugleiste importieren"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, ps.pt(28))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
            } else {
                // Erst ab einer Handvoll: bei drei Objekten ist ein
                // Suchfeld mehr Bedienung als Hilfe.
                if model.objects.count > 5 {
                    TextField(st("Search objects", "Objekte suchen"), text: $objektSuche)
                        .font(.system(size: ps.font(13)))
                        .padding(.horizontal, ps.pt(10))
                        .frame(height: ps.touch(44))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                        .accessibilityIdentifier("objekte.suche")
                }
                HStack(spacing: ps.pt(10)) {
                    Button(st("Select all", "Alle auswählen")) { model.selectAll() }
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(minHeight: ps.touch(40))
                        .accessibilityIdentifier("objekte.alle")
                    Button(st("Clear", "Aufheben")) { model.select(nil) }
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(model.selectedIds.isEmpty
                                         ? PrusaColors.textMuted : PrusaColors.orange)
                        .frame(minHeight: ps.touch(40))
                        .disabled(model.selectedIds.isEmpty)
                        .accessibilityIdentifier("objekte.auswahlaufheben")
                    Spacer(minLength: 0)
                    if model.selectedIds.count > 1 {
                        Text("\(model.selectedIds.count)")
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                }
            }
            ForEach(sichtbar, id: \.id) { objekt in
                Button {
                    model.select(objekt.id)
                    // Wer ein Objekt antippt, will damit etwas tun. Der
                    // frühere Reiterzustand öffnete im Akkordeon nichts.
                    offeneBereiche.insert(kennung(.bearbeiten))
                    seitenleistenFokusSchritt = 0
                    seitenleistenZiel = objekt.id
                } label: {
                    HStack {
                        // Das Kästchen nimmt hinzu oder heraus, die Zeile
                        // wählt einzeln aus. Zwei Gesten für zwei
                        // Absichten, an derselben Zeile.
                        Button { model.toggleSelection(objekt.id) } label: {
                            Image(systemName: model.selectedIds.contains(objekt.id)
                                  ? "checkmark.square.fill" : "square")
                                .font(.system(size: ps.font(15)))
                                .foregroundStyle(model.selectedIds.contains(objekt.id)
                                                 ? PrusaColors.orange : PrusaColors.textMuted)
                                .frame(width: ps.touch(40), height: ps.touch(40))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("objekte.haken.\(objekt.id)")
                        ObjektMasse(objekt: objekt)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(objekt.name.isEmpty ? "Objekt \(objekt.id)" : objekt.name)
                                .font(.system(size: ps.font(13)))
                                .foregroundStyle(PrusaColors.textPrimary)
                                .lineLimit(1)
                            Text(Masse.text(objekt.sizeMm.x, objekt.sizeMm.y, objekt.sizeMm.z, zoll: zollEinheiten))
                                .font(.system(size: ps.font(10)))
                                .foregroundStyle(objekt.outsideBed
                                                 ? PrusaColors.danger : PrusaColors.textMuted)
                        }
                        Spacer()
                        Button { model.removeObjects([objekt.id]) } label: {
                            Text("✖")
                                .font(.system(size: ps.font(12)))
                                .foregroundStyle(PrusaColors.textMuted)
                                .frame(width: ps.touch(40), height: ps.touch(40))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("advanced.entfernen.\(objekt.id)")
                    }
                    .padding(.horizontal, ps.pt(8))
                    .frame(minHeight: ps.touch(48))
                    .background(model.selectedIds.contains(objekt.id)
                                ? PrusaColors.panelRaised : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("advanced.objekt.\(objekt.id)")
                .id(scrollKennungFuerObjekt(objekt.id))
            }
        }
    }

    /// Scrollt nur, wenn der obere Aktionsblock nicht vollständig in der
    /// gemessenen Seitenleistenfläche liegt. Zuerst bleibt die gewählte
    /// Objektzeile am oberen Rand. Reicht das bei einer langen Liste nicht,
    /// folgt genau eine Nachkorrektur zum gemessenen Aktionsblock.
    private func bearbeitenFokussierenWennNoetig(
        _ proxy: ScrollViewProxy,
        seitenleiste neuerSeitenleistenRahmen: CGRect? = nil,
        bearbeiten neuerBearbeitenRahmen: CGRect? = nil
    ) {
        let seitenleiste = neuerSeitenleistenRahmen ?? seitenleistenRahmen
        let bearbeiten = neuerBearbeitenRahmen ?? bearbeitenRahmen
        guard let id = seitenleistenZiel,
              !seitenleiste.isNull,
              !bearbeiten.isNull else { return }
        let sichtbar = bearbeiten.minY >= seitenleiste.minY
            && bearbeiten.maxY <= seitenleiste.maxY
        if sichtbar {
            seitenleistenZiel = nil
            seitenleistenFokusSchritt = 0
        } else if seitenleistenFokusSchritt == 0 {
            seitenleistenFokusSchritt = 1
            proxy.scrollTo(scrollKennungFuerObjekt(id), anchor: .top)
        } else {
            // Dieser zweite und letzte Sprung ist absichtlich begrenzt:
            // Seine neue Preference-Messung löst keinen dritten aus.
            proxy.scrollTo("advanced.bearbeiten.aktionen", anchor: .top)
            seitenleistenZiel = nil
            seitenleistenFokusSchritt = 0
        }
    }

    private func scrollKennungFuerObjekt(_ id: Int32) -> String {
        "advanced.objekt.scroll.\(id)"
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
}

/// Hoehe der Werkzeugleiste, von der Leiste nach oben gemeldet.
private struct WerkzeugleisteHoehe: PreferenceKey {
    static var defaultValue: CGFloat = 52
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Zeilenumbruch fuer die Werkzeugleiste - Gegenstueck zu Composes FlowRow.
/// Legt die Kinder von links nach rechts und bricht um, wenn die Breite
/// nicht reicht. Zeilenhoehe ist die hoechste Ansicht der Zeile.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let breite = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, zeile: CGFloat = 0, maxX: CGFloat = 0
        for sub in subviews {
            let g = sub.sizeThatFits(.unspecified)
            if x > 0 && x + g.width > breite { x = 0; y += zeile + spacing; zeile = 0 }
            x += g.width + spacing
            zeile = max(zeile, g.height)
            maxX = max(maxX, x - spacing)
        }
        return CGSize(width: breite == .infinity ? maxX : breite, height: y + zeile)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = bounds.minX, y: CGFloat = bounds.minY, zeile: CGFloat = 0
        for sub in subviews {
            let g = sub.sizeThatFits(.unspecified)
            if x > bounds.minX && x + g.width > bounds.maxX { x = bounds.minX; y += zeile + spacing; zeile = 0 }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(g))
            x += g.width + spacing
            zeile = max(zeile, g.height)
        }
    }
}
