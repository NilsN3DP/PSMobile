import SwiftUI
import UniformTypeIdentifiers

@main
struct PSMobileApp: App {
    @StateObject private var model = SlicerModel()

    var body: some Scene {
        WindowGroup {
            // Legt die Skalierung aus der Fenstergroesse fest. Muss ganz
            // aussen stehen: alles darunter rechnet damit.
            PSScaleRoot {
                // Ohne Drucker gibt es nichts zu zeigen - die
                // Ersteinrichtung kommt vor dem Arbeitsbereich.
                if model.setupNeeded {
                    SetupView(
                        models: model.printerModels,
                        busy: model.setupBusy,
                        preselected: model.installedPrinters,
                        onConfirm: { model.completeSetup($0) },
                        onLanguageChange: { _ in },
                    )
                } else {
                    SlicerView()
                        .accessibilityIdentifier("arbeitsbereich")
                }
            }
                .environmentObject(model)
                .onAppear { model.start() }
                // Modelle, die aus anderen Apps geteilt werden
                .onOpenURL { model.load(url: $0) }
        }
    }
}

struct SlicerView: View {
    @EnvironmentObject private var model: SlicerModel
    @Environment(\.psScale) private var ps
    @State private var showImporter = false

    var body: some View {
        NavigationStack {
            VStack(spacing: ps.pt(12)) {

                // Platzhalter fuer den 3D-Viewport (M4).
                // Layout bleibt spaeter so: Bett vollflaechig oben,
                // Objektliste darunter, Aktionen ganz unten in
                // Daumenreichweite - siehe docs/05-ui-konzept-touch-stift.md
// Der Arbeitsbereich. Solange der Kern nicht steht, bleibt die
                // Flaeche leer statt zu blinken - das Anlegen dauert einen
                // Wimpernschlag, und ein aufblitzender Platzhalter sieht aus
                // wie ein Fehler.
                Group {
                    if let session = model.sessionHandle {
                        ViewportView(
                            session: session,
                            shaderDir: model.shaderDir,
                            selectedId: model.selectedId ?? -1,
                            selectedIds: model.selectedId.map { [$0] } ?? [],
                            invalidateKey: model.sceneRevision,
                            onSelect: { model.select($0 < 0 ? nil : $0) }
                        )
                    } else {
                        RoundedRectangle(cornerRadius: ps.pt(12)).fill(.quaternary)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(12)))
                .frame(maxHeight: .infinity)

                if let warning = model.memoryWarning {
                    Label(warning, systemImage: "exclamationmark.triangle")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }

                if !model.objects.isEmpty {
                    List {
                        ForEach(model.objects, id: \.id) { obj in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(obj.name.isEmpty ? "Objekt \(obj.id)" : obj.name)
                                    .font(.subheadline)
                                Text(String(format: "%.1f x %.1f x %.1f mm  -  %d Dreiecke",
                                            obj.sizeMm.x, obj.sizeMm.y, obj.sizeMm.z, obj.triangles))
                                    .font(.caption).foregroundStyle(.secondary)
                                if obj.outsideBed {
                                    Text("ragt ueber das Bett hinaus")
                                        .font(.caption2).foregroundStyle(.red)
                                }
                            }
                        }
                        .onDelete { idx in
                            idx.map { model.objects[$0].id }.forEach(model.remove)
                        }
                    }
                    .frame(maxHeight: ps.pt(220))
                }

                progressView

                HStack {
                    Button("Slicen") { model.slice() }
                        .buttonStyle(.borderedProminent)
                        .disabled(model.objects.isEmpty || isRunning)
                    Button("Abbrechen") { model.cancel() }
                        .disabled(!isRunning)
                }
            }
            .padding(ps.pt(16))
            .navigationTitle("PSMobile")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showImporter = true } label: { Label("Modell", systemImage: "plus") }
                }
            }
            .fileImporter(isPresented: $showImporter,
                          allowedContentTypes: [.item],
                          allowsMultipleSelection: false) { result in
                if case .success(let urls) = result, let u = urls.first { model.load(url: u) }
            }
        }
    }

    private var isRunning: Bool {
        if case .running = model.progress { return true }
        return false
    }

    @ViewBuilder private var progressView: some View {
        switch model.progress {
        case .idle:
            EmptyView()
        case .running(let percent, let stage):
            VStack(alignment: .leading, spacing: 4) {
                // Phase mit anzeigen, nicht nur Prozent - das macht
                // mehrminutige Wartezeiten ertraeglich.
                Text("\(percent)%  -  \(stage)").font(.callout)
                ProgressView(value: Double(percent), total: 100)
            }
        case .done(let secs, let minutes, let grams):
            Text(String(format: "Fertig in %.1f s  -  Druckzeit %d min  -  %.1f g",
                        secs, minutes, grams))
                .font(.callout)
        case .failed(let msg):
            Text(msg).font(.callout).foregroundStyle(.red)
        case .cancelled:
            Text("abgebrochen").font(.callout)
        }
    }
}
