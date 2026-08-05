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
    /// Einen Pinsel an- oder ausschalten. Liegt beim Aufrufer, weil dort
    /// auch der Zustand des Werkzeugs sitzt.
    var onMalwerkzeug: (PsmCore.PaintTool) -> Void = { _ in }

    @Environment(\.psScale) private var ps

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
            Werkzeug(name: "splitobjects", symbol: "square.split.2x1",
                     label: st("Objects", "Objekte")),
            Werkzeug(name: "splitvolumes", symbol: "square.split.1x2",
                     label: st("Volumes", "Volumen")),
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
        "delete", "copy", "more", "fewer", "splitobjects", "splitvolumes",
        // Ein Pinsel ohne Objekt hat nichts zu bemalen.
        "paintsupport", "paintseam", "paintmmu",
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: ps.pt(6)) {
                ForEach(werkzeuge, id: \.name) { w in
                    knopf(w)
                }
            }
            .padding(.vertical, ps.pt(10))
            .padding(.horizontal, ps.pt(6))
        }
        .frame(width: ps.pt(74))
        .background(PrusaColors.panel)
    }

    private func knopf(_ w: Werkzeug) -> some View {
        let an = erlaubt(w.name)
        return Button { ausfuehren(w.name) } label: {
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
        .buttonStyle(.plain)
        .disabled(!an)
        .accessibilityIdentifier("schiene." + w.name)
        .accessibilityLabel(w.label)
    }

    private func erlaubt(_ name: String) -> Bool {
        if Self.brauchtAuswahl.contains(name) { return auswahl != nil }
        switch name {
        case "paste":     return kopiert != nil
        case "undo":      return !model.undoLabel.isEmpty
        case "redo":      return !model.redoLabel.isEmpty
        case "deleteall": return !model.objects.isEmpty
        case "arrange":   return !model.objects.isEmpty
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
        case "arrange":   model.arrange()
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
        case "splitobjects", "splitvolumes":
            if let id = auswahl { model.split(id) }
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
