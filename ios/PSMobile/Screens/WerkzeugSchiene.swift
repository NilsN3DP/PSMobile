import SwiftUI
import PSMShared

/// Die senkrechte Werkzeugschiene links — Gegenstück zu `ToolStrip` auf
/// Android.
///
/// Der Nutzer hat beide Fassungen nebeneinandergelegt und gesagt, was
/// ihm fehlt: genau diese Schiene. Auf iOS lag alles waagerecht in einer
/// Zeile oben — Projektbefehle, Einstellungsseiten und Objektwerkzeuge
/// gemischt —, und die Objektwerkzeuge fehlten größtenteils ganz.
///
/// Reihenfolge und Namen kommen aus `toolbar.json`, das aus
/// PrusaSlicers eigener Werkzeugleiste stammt (GLCanvas3D). Auch welche
/// Werkzeuge ohne Auswahl sinnlos sind, ist von dort übernommen — das
/// sind die `enabling_callbacks` des Originals.
///
/// Die Zeichen sind SF Symbols und nicht Prusas SVGs. Die SVGs liegen
/// zwar in den Ressourcen, aber SwiftUI zeichnet kein SVG; ein eigener
/// Rasterer dafür wäre viel Arbeit für ein Ergebnis, das auf einem
/// iPad schlechter aussähe als das native Symbol.
struct WerkzeugSchiene: View {

    @ObservedObject var model: SlicerModel
    /// Was kopiert wurde. Liegt beim Aufrufer, weil die Zwischenablage
    /// den Bildschirm überlebt.
    @Binding var kopiert: Int32?
    var onEinfuegen: () -> Void
    var onSettings: () -> Void
    var onArrange: () -> Void = {}
    /// Einen Pinsel an- oder ausschalten. Liegt beim Aufrufer, weil dort
    /// auch der Zustand des Werkzeugs sitzt.
    var onMalwerkzeug: (PsmCore.PaintTool) -> Void = { _ in }
    /// Drucker und App-Einstellungen - standen bisher oben in der
    /// waagerechten Werkzeugleiste, zusammen mit "Print Settings" dort
    /// aber doppelt gemoppelt. Unten in dieser Spalte sind sie weiterhin
    /// immer erreichbar, konkurrieren aber nicht mehr mit den
    /// Objektwerkzeugen um den obersten Platz.
    var onPrinters: () -> Void = {}
    var onAppSettings: () -> Void = {}

    @Environment(\.psScale) private var ps
    /// Nur fuer das Arrange-Popover - die anderen Werkzeuge oeffnen
    /// nichts Eigenes, das lohnt keinen eigenen Zustand je Knopf.
    @State private var zeigeArrangePanel = false

    /// Ein Werkzeug: Kennung wie in toolbar.json, Zeichen, kurzer Text.
    private struct Werkzeug {
        let name: String
        let symbol: String
        let label: String
    }

    private var auswahl: Int32? { model.selectedId }

    private var werkzeuge: [Werkzeug] {
        [
            Werkzeug(name: "add", symbol: "plus",
                     label: st("Import", "Import")),
            Werkzeug(name: "delete", symbol: "trash",
                     label: st("Delete", "Löschen")),
            Werkzeug(name: "deleteall", symbol: "trash.slash",
                     label: st("Clear", "Leeren")),
            Werkzeug(name: "arrange", symbol: "square.grid.2x2",
                     label: PsUiCatalog.tr("Arrange")),
            Werkzeug(name: "copy", symbol: "doc.on.doc",
                     label: st("Copy", "Kopieren")),
            Werkzeug(name: "paste", symbol: "doc.on.clipboard",
                     label: st("Paste", "Einfügen")),
            Werkzeug(name: "more", symbol: "plus.square.on.square",
                     label: st("+ copy", "+ Kopie")),
            Werkzeug(name: "fewer", symbol: "minus.square",
                     label: st("− copy", "− Kopie")),
            // Ein Knopf statt zwei: am Desktop ist das ein Rechtsklick-
            // Menue mit "Split to objects" / "Split to parts", keine
            // zwei getrennten Befehle. "Volumes" hiess hier vorher
            // "Objekte"-Zwilling und war fuer sich kaum verstaendlich.
            Werkzeug(name: "split", symbol: "square.split.2x1",
                     label: st("Separate", "Trennen")),
            // Die Pinsel gehoeren zu den Gizmos, nicht in einen Reiter
            // am rechten Rand: beide bestimmen, was ein Finger auf dem
            // Modell tut. Am Desktop stehen sie aus demselben Grund in
            // derselben Spalte.
            Werkzeug(name: "paintsupport", symbol: "paintbrush.pointed",
                     label: st("Supports", "Stützen")),
            Werkzeug(name: "paintseam", symbol: "scribble",
                     label: st("Seam", "Naht")),
            Werkzeug(name: "paintmmu", symbol: "paintpalette",
                     label: "MMU"),
        ]
    }

    /// Welche Werkzeuge ohne Auswahl sinnlos sind — entspricht den
    /// enabling_callbacks im Original.
    private static let brauchtAuswahl: Set<String> = [
        "delete", "copy", "more", "fewer", "split",
        // Ein Pinsel ohne Objekt hat nichts zu bemalen.
        "paintsupport", "paintseam", "paintmmu",
    ]

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: ps.pt(6)) {
                    ForEach(werkzeuge, id: \.name) { w in
                        knopf(w)
                    }
                }
                .padding(.vertical, ps.pt(10))
                .padding(.horizontal, ps.pt(6))
            }
            Spacer(minLength: 0)
            fusszeile
        }
        .frame(width: ps.pt(74))
        .background(PrusaColors.panel)
    }

    /// Drucker und App-Einstellungen, unten links - siehe onPrinters.
    private var fusszeile: some View {
        VStack(spacing: ps.pt(6)) {
            Divider().overlay(PrusaColors.divider)
                .padding(.horizontal, ps.pt(8))
            fusszeilenKnopf("paperplane", st("Printers", "Drucker"),
                            kennung: "drucker.oeffnen", aktion: onPrinters)
            fusszeilenKnopf("gearshape", st("App", "App"),
                            kennung: "appeinstellungen.oeffnen", aktion: onAppSettings)
        }
        .padding(.vertical, ps.pt(8))
        .padding(.horizontal, ps.pt(6))
    }

    private func fusszeilenKnopf(_ symbol: String, _ label: String,
                                 kennung: String,
                                 aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: ps.pt(3)) {
                Image(systemName: symbol)
                    .font(.system(size: ps.font(17)))
                Text(label)
                    .font(.system(size: ps.font(9)))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(PrusaColors.textMuted)
            .frame(maxWidth: .infinity)
            .frame(height: ps.touch(50))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
        .accessibilityLabel(label)
    }

    @ViewBuilder
    private func knopf(_ w: Werkzeug) -> some View {
        let an = erlaubt(w.name)
        if w.name == "arrange" {
            // Wie zuvor in der Bettleiste, die diesen Knopf jetzt nicht
            // mehr doppelt zeigt: Tipp ordnet alle ungesperrten Betten
            // sofort an, Halten oeffnet ein Popover direkt am Knopf mit
            // Zielbett/Abstand - kein Vollbild-Sheet, das ist zu viel
            // Weg fuer eine schnelle Randentscheidung.
            Button { model.arrangeAll() } label: {
                knopfInhalt(w, an: an)
            }
            .buttonStyle(.plain)
            .disabled(!an)
            .accessibilityIdentifier("schiene." + w.name)
            .accessibilityLabel(w.label)
            .simultaneousGesture(
                LongPressGesture(minimumDuration: 0.5).onEnded { _ in
                    zeigeArrangePanel = true
                }
            )
            .popover(isPresented: $zeigeArrangePanel) {
                ArrangePanel(model: model, isPresented: $zeigeArrangePanel)
            }
        } else if w.name == "split" {
            Menu {
                Button(st("Split to objects", "Zu Objekten trennen")) {
                    ausfuehren("splitobjects")
                }
                Button(st("Split to parts", "Zu Volumen trennen")) {
                    ausfuehren("splitvolumes")
                }
            } label: {
                knopfInhalt(w, an: an)
            }
            .disabled(!an)
            .accessibilityIdentifier("schiene." + w.name)
            .accessibilityLabel(w.label)
        } else {
            Button { ausfuehren(w.name) } label: {
                knopfInhalt(w, an: an)
            }
            .buttonStyle(.plain)
            .disabled(!an)
            .accessibilityIdentifier("schiene." + w.name)
            .accessibilityLabel(w.label)
        }
    }

    private func knopfInhalt(_ w: Werkzeug, an: Bool) -> some View {
        VStack(spacing: ps.pt(3)) {
            Image(systemName: w.symbol)
                .font(.system(size: ps.font(19)))
            Text(w.label)
                .font(.system(size: ps.font(9)))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .foregroundStyle(an ? PrusaColors.textPrimary : PrusaColors.textMuted.opacity(0.35))
        .frame(maxWidth: .infinity)
        .frame(height: ps.touch(58))
        .background(an ? PrusaColors.panelRaised : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
        .contentShape(Rectangle())
    }

    private func erlaubt(_ name: String) -> Bool {
        if Self.brauchtAuswahl.contains(name) { return auswahl != nil }
        switch name {
        case "paste":     return kopiert != nil
        case "undo":      return !model.undoLabel.isEmpty
        case "redo":      return !model.redoLabel.isEmpty
        case "deleteall": return !model.objects.isEmpty
        // Auch leer bedienbar: das gemeinsame Arrange-Panel erklärt den
        // Zustand sichtbar, statt den Befehl kommentarlos zu deaktivieren.
        case "arrange":   return true
        default:          return true
        }
    }

    private func ausfuehren(_ name: String) {
        switch name {
        case "add":       onEinfuegen()
        case "undo":      model.undo()
        case "redo":      model.redo()
        // Loeschen loescht, was markiert ist - das ist der Punkt,
        // an dem eine Mehrfachauswahl etwas bringt.
        case "delete":    model.removeObjects(Array(model.selectedIds))
        case "deleteall": model.newProject()
        // "arrange" hat einen eigenen Zweig in knopf(_:) - Tipp und
        // Halten unterscheiden sich, das passt nicht in ein einzelnes
        // ausfuehren(_:).
        case "copy":      kopiert = auswahl
        case "paste":     if let id = kopiert { model.duplicate([id]) }
        case "more":
            if let id = auswahl, let o = model.objects.first(where: { $0.id == id }) {
                model.setInstances(id, count: Int32(o.instances + 1))
            }
        case "fewer":
            if let id = auswahl, let o = model.objects.first(where: { $0.id == id }) {
                model.setInstances(id, count: Int32(max(o.instances - 1, 1)))
            }
        case "splitobjects":
            if let id = auswahl { model.split(id) }
        case "splitvolumes":
            // Der schon vorhandene Wrapper aus dem Objekt-Inspektor -
            // hierher fehlte nur der Aufruf, nicht die Funktion selbst.
            if let id = auswahl { model.splitVolumes(id) }
        case "paintsupport": onMalwerkzeug(.support)
        case "paintseam":    onMalwerkzeug(.seam)
        case "paintmmu":     onMalwerkzeug(.mmu)
        case "settings":  onSettings()
        default:          break
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
