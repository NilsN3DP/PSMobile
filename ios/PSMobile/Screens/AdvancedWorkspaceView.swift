import SwiftUI
import UniformTypeIdentifiers
import PSMShared

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
    var onOpenSimple: () -> Void = {}
    var onAppSettings: () -> Void = {}
    var onPrinters: () -> Void = {}
    var onSettings: (String) -> Void = { _ in }

    @Environment(\.psScale) private var ps
    @State private var zeigeImporter = false
    @State private var hinderungsgruende: [String] = []
    @State private var gizmo: PsmViewport.Gizmo = .move
    @State private var seiteOffen = true
    @State private var ansichtZuruecksetzen = 0

    /// Auf schmalen Fenstern liegt der Inspektor ueber dem Bett statt
    /// daneben - nebeneinander bliebe fuer beides zu wenig.
    private var schmal: Bool { ps.windowSize.width < 760 }

    var body: some View {
        ZStack {
            PrusaColors.background.ignoresSafeArea()
            HStack(spacing: 0) {
                VStack(spacing: 0) {
                    werkzeugleiste
                    arbeitsflaeche
                    fusszeile
                }
                if seiteOffen && !schmal {
                    Divider().overlay(PrusaColors.divider)
                    seitenleiste.frame(width: ps.pt(340))
                }
            }
            if seiteOffen && schmal { schmaleSeite }
            if model.progress != .idle {
                SliceSheet(model: model) { model.dismissProgress() }
            }
            if !hinderungsgruende.isEmpty {
                SliceBlockerSheet(gruende: hinderungsgruende) { hinderungsgruende = [] }
            }
            PSMarke(name: "arbeitsbereich")
        }
        .fileImporter(isPresented: $zeigeImporter,
                      allowedContentTypes: [.item],
                      allowsMultipleSelection: true) { ergebnis in
            if case .success(let urls) = ergebnis {
                urls.forEach { model.load(url: $0) }
            }
        }
    }

    // MARK: - Werkzeuge

    private var werkzeugleiste: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(4)) {
                werkzeug("＋", st("Model", "Modell"), kennung: "advanced.modell") {
                    zeigeImporter = true
                }
                werkzeug("▤", PsUiCatalog.tr("Arrange"), kennung: "advanced.arrange") {
                    model.arrange()
                }
                werkzeug("⌂", st("View", "Ansicht"), kennung: "advanced.ansicht") {
                    ansichtZuruecksetzen += 1
                }
                trenner
                werkzeug("☷", PsUiCatalog.tr("Print Settings"),
                         kennung: "advanced.printSettings") { onSettings("print") }
                werkzeug("◎", PsUiCatalog.tr("Filament Settings"),
                         kennung: "advanced.filamentSettings") { onSettings("filament") }
                werkzeug("▤", PsUiCatalog.tr("Printer Settings"),
                         kennung: "advanced.printerSettings") { onSettings("printer") }
                trenner
                werkzeug("➦", st("Printers", "Drucker"), kennung: "drucker.oeffnen",
                         aktion: onPrinters)
                werkzeug("⚙", st("App", "App"), kennung: "appeinstellungen.oeffnen",
                         aktion: onAppSettings)
                werkzeug("◱", "Simple", kennung: "simple.oeffnen", aktion: onOpenSimple)
                Spacer(minLength: 0)
                werkzeug(seiteOffen ? "▸" : "◂", st("Panel", "Leiste"),
                         kennung: "advanced.seite") { seiteOffen.toggle() }
            }
            .padding(.horizontal, ps.pt(8))
            .padding(.vertical, ps.pt(4))
        }
        .background(PrusaColors.panel)
    }

    private var trenner: some View {
        Rectangle()
            .fill(PrusaColors.divider)
            .frame(width: 1, height: ps.pt(30))
            .padding(.horizontal, ps.pt(4))
    }

    private func werkzeug(_ glyph: String,
                          _ label: String,
                          kennung: String,
                          aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: 0) {
                Text(glyph).font(.system(size: ps.font(15)))
                Text(label).font(.system(size: ps.font(8))).lineLimit(1)
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
            ViewportView(
                session: session,
                shaderDir: model.shaderDir,
                selectedId: model.selectedId ?? -1,
                selectedIds: model.selectedId.map { [$0] } ?? [],
                invalidateKey: model.sceneRevision,
                gizmo: gizmo,
                resetViewKey: ansichtZuruecksetzen,
                onSelect: { model.select($0 < 0 ? nil : $0) }
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            PrusaColors.background.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var fusszeile: some View {
        HStack(spacing: ps.pt(12)) {
            if let warnung = model.memoryWarning {
                Text(warnung)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.orange)
                    .lineLimit(2)
            }
            Spacer()
            Button { schneiden() } label: {
                Text(PsUiCatalog.tr("Slice now"))
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(.white)
                    .padding(.horizontal, ps.pt(24))
                    .frame(height: ps.touch(48))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("slicen")
        }
        .padding(.horizontal, ps.pt(12))
        .padding(.vertical, ps.pt(8))
        .background(PrusaColors.panel)
    }

    private func schneiden() {
        let gruende = model.sliceBlockers
        if gruende.isEmpty { model.slice() } else { hinderungsgruende = gruende }
    }

    // MARK: - Seitenleiste

    private var seitenleiste: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(12)) {
                objektliste
                if let id = model.selectedId,
                   let objekt = model.objects.first(where: { $0.id == id }) {
                    Divider().overlay(PrusaColors.divider)
                    AdvancedObjectInspectorView(model: model, objekt: objekt, gizmo: $gizmo)
                }
            }
            .padding(ps.pt(12))
        }
        .background(PrusaColors.background)
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

    private var objektliste: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(st("Objects", "Objekte").uppercased() + " (\(model.objects.count))")
                .font(.system(size: ps.font(11), weight: .semibold))
                .foregroundStyle(PrusaColors.textMuted)
            if model.objects.isEmpty {
                Text(st("Nothing on the bed yet", "Noch nichts auf dem Bett"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            ForEach(model.objects, id: \.id) { objekt in
                Button { model.select(objekt.id) } label: {
                    HStack {
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
                    .background(objekt.id == model.selectedId
                                ? PrusaColors.panelRaised : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("advanced.objekt.\(objekt.id)")
            }
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
