import SwiftUI
import UniformTypeIdentifiers
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

    @Environment(\.psScale) private var ps
    @State private var zeigeImporter = false
    @State private var hinderungsgruende: [String] = []
    @State private var gizmo: PsmViewport.Gizmo = .move
    @State private var seiteOffen = true
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
    @State private var ansichtZuruecksetzen = 0

    /// Dieselben Optionen steuern Bedienung, Kern und Viewport.
    @State private var maloptionen = PsmCore.PaintOptions()

    /// Auf schmalen Fenstern liegt der Inspektor ueber dem Bett statt
    /// daneben - nebeneinander bliebe fuer beides zu wenig.
    private var schmal: Bool { ps.windowSize.width < 760 }

    /// Die Seitenleiste beansprucht auf breiten Geraeten diesen Teil der
    /// ZStack. Schwebende Elemente muessen denselben freien Rest nutzen.
    private var seitenleistenbreite: CGFloat {
        min(ps.pt(340), ps.windowSize.width * 0.42)
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
                if seiteOffen && !schmal {
                    Divider().overlay(PrusaColors.divider)
                    // Hoechstens zwei Fuenftel der Breite: darunter
                    // bleibt vom Bett nichts uebrig, und darum geht es
                    // hier.
                    seitenleiste.frame(width: seitenleistenbreite)
                }
            }
            if vorschau, let snapshot = finalPreview {
                FinalPreviewOverlay(
                    snapshot: snapshot,
                    range: $previewRange,
                    view: $previewView,
                    hiddenRoles: $hiddenPreviewRoles,
                    hiddenExtruders: $hiddenPreviewExtruders,
                    onEditor: vorschauSchliessen)
            }
            // Dieselbe schwebende Leiste wie im Einfachen Modus: was man
            // am ausgewaehlten Objekt am haeufigsten tut, gehoert an das
            // Objekt und nicht in eine Spalte am Rand. Im Advanced Mode
            // fehlte sie - dort war jeder Handgriff ein Weg nach rechts.
            if let id = model.selectedId,
               let objekt = model.objects.first(where: { $0.id == id }),
               !vorschau {
                VStack {
                    // Die Leiste gehoert zum freien Viewport, nicht zur
                    // gesamten ZStack: sonst liegt ihre rechte Haelfte
                    // ueber der offenen Seitenleiste.
                    HStack {
                        Spacer(minLength: 0)
                        SimpleObjectBarView(
                            model: model,
                            objekt: objekt,
                            zeigtZurueck: false,
                            onClearSelection: { model.select(nil) },
                            onFlaechenwahl: { aufFlaeche = $0 })
                            .fixedSize(horizontal: true, vertical: false)
                        Spacer(minLength: 0)
                    }
                    .padding(.trailing, seiteOffen && !schmal
                             ? seitenleistenbreite : 0)
                    .padding(.top, ps.pt(schmal ? 96 : 118))
                    Spacer()
                }
            }
            // Verschoben aus der permanenten oberen Leiste: die Griffe
            // sind ein Objektwerkzeug, kein Projektbefehl, und brauchten
            // dort staendig Platz, auch ohne Auswahl. Bleiben bewusst
            // immer vorhanden (nur deaktiviert ohne Auswahl) statt ganz
            // zu verschwinden - dieselbe Kennung, dasselbe Verhalten,
            // nur kleiner und naeher am Bett als an der Kopfzeile.
            VStack {
                HStack {
                    kompakteGriffe
                    Spacer(minLength: 0)
                }
                .padding(.leading, ps.pt(schmal ? 82 : 8))
                .padding(.top, ps.pt(schmal ? 96 : 118))
                Spacer()
            }
            if seiteOffen && schmal { schmaleSeite }
            if schmal {
                VStack {
                    BedSelector(model: model,
                                onArrange: { zeigeArrange = true },
                                onOpenSelection: { zeigeBettwahl = true })
                        .padding(.leading, ps.pt(74))
                        .padding(.top, ps.pt(52))
                    Spacer()
                }
                .zIndex(80)
            }
            if model.progress != .idle {
                SliceSheet(model: model,
                           onSendToPrinter: onSendToPrinter) { model.dismissProgress() }
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
            switch zweck {
            case .modell:  urls.forEach { model.load(url: $0) }
            case .projekt: if let erste = urls.first { model.loadProject(url: erste) }
            }
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

    /// Welche Griffe der Finger im Viewport bedient.
    ///
    /// Hier oben und nicht mehr rechts im Inspektor: die Wahl gehoert
    /// zum Viewport, nicht zu den Zahlen, und man trifft sie oft
    /// hintereinander.
    ///
    /// Ohne Auswahl ausgegraut - ein Griff ohne Objekt ist keine
    /// Einstellung, sondern eine Enttaeuschung.
    private var kompakteGriffe: some View {
        let hatAuswahl = model.selectedId != nil
        return HStack(spacing: ps.pt(2)) {
            griffKnopf("arrow.up.and.down.and.arrow.left.and.right", PsUiCatalog.tr("Move"), .move, hatAuswahl)
            griffKnopf("arrow.triangle.2.circlepath", PsUiCatalog.tr("Rotate"), .rotate, hatAuswahl)
            griffKnopf("arrow.up.left.and.arrow.down.right", PsUiCatalog.tr("Scale"), .scale, hatAuswahl)
            griffKnopf("hand.point.up.left", PsUiCatalog.tr("None"), PsmViewport.Gizmo.none, hatAuswahl)
        }
        .padding(ps.pt(3))
        .background(PrusaColors.panel.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(6))
                .stroke(PrusaColors.divider, lineWidth: 1)
        )
    }

    private func griffKnopf(_ symbol: String,
                            _ name: String,
                            _ wert: PsmViewport.Gizmo,
                            _ moeglich: Bool) -> some View {
        let an = gizmo == wert && moeglich
        return Button {
            gizmo = wert
        } label: {
            Image(systemName: symbol)
                .font(.system(size: ps.font(14)))
                .foregroundStyle(!moeglich ? PrusaColors.textMuted.opacity(0.4)
                                 : an ? PrusaColors.orange : PrusaColors.textPrimary)
                .frame(width: ps.touch(34), height: ps.touch(34))
                .background(an ? PrusaColors.panelRaised : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!moeglich)
        .accessibilityIdentifier("advanced.gizmo." + kennungFuer(wert))
        .accessibilityLabel(name)
    }

    private func kennungFuer(_ wert: PsmViewport.Gizmo) -> String {
        switch wert {
        case .move:   return "move"
        case .rotate: return "rotate"
        case .scale:  return "scale"
        default:      return "none"
        }
    }

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
            seiteOffen = true
        }
    }

    private var werkzeugleiste: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(4)) {
                werkzeug("house", st("Start", "Start"), kennung: "kopf.start", aktion: onHome)
                trenner
                werkzeug("doc", st("New", "Neu"), kennung: "projekt.neu") {
                    model.newProject()
                }
                werkzeug("folder", st("Open", "Öffnen"), kennung: "projekt.oeffnen") {
                    zweck = .projekt
                    zeigeImporter = true
                }
                werkzeug("square.and.arrow.down", st("Save", "Sichern"), kennung: "projekt.sichern") {
                    model.saveProject()
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
        .background(PrusaColors.panel)
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
                blickwinkel(st("3D", "3D"), .iso)
                blickwinkel(st("Top", "Oben"), .top)
                blickwinkel(st("Front", "Vorn"), .front)
                blickwinkel(st("Back", "Hinten"), .back)
                blickwinkel(st("Left", "Links"), .left)
                blickwinkel(st("Right", "Rechts"), .right)
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

    /// Vorschau oeffnen - und nur dann rechnen, wenn es sein muss.
    private func vorschauZeigen() {
        if vorschau { vorschauSchliessen(); return }
        if model.sliceResultIsCurrent {
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
    }

    private func schneiden() {
        let gruende = model.sliceBlockers
        if gruende.isEmpty { model.slice() } else { hinderungsgruende = gruende }
    }

    // MARK: - Seitenleiste

    private var seitenleiste: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
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
            }
            // Ausserhalb der Reiter: was ein Schnitt ergeben hat, ist
            // keine Frage des gerade offenen Reiters.
            abschluss
        }
        .background(PrusaColors.background)
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
            if let s = model.stats, model.sliceResultIsCurrent {
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
                Text(st("Not sliced yet", "Noch nicht geschnitten"))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            if let warnung = model.memoryWarning {
                Text(warnung)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button { schneiden() } label: {
                Text(PsUiCatalog.tr("Slice now"))
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: ps.touch(52))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("slicen")
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
                              PsUiCatalog.tr("Printer Settings"),
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

    /// Auf schmalen Geraeten als Blatt ueber dem Bett, mit einer
    /// Schliessflaeche daneben.
    private var schmaleSeite: some View {
        HStack(spacing: 0) {
            PrusaColors.background.opacity(0.6)
                .contentShape(Rectangle())
                .onTapGesture { seiteOffen = false }
            seitenleiste.frame(width: ps.pt(320))
        }
        .ignoresSafeArea(edges: .bottom)
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
                            Text(String(format: "%.1f × %.1f × %.1f mm",
                                        objekt.sizeMm.x, objekt.sizeMm.y, objekt.sizeMm.z))
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

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
