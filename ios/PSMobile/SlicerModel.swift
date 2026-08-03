import Foundation
import SwiftUI
import UIKit   // beginBackgroundTask

/// Zustandshalter der App - Gegenstueck zu `SlicerService` auf Android.
///
/// Auf iOS gibt es keinen zweiten Prozess und keinen Foreground-Service.
/// Der Slice-Job laeuft deshalb als abgetrennter Task, und die App muss
/// beim Wechsel in den Hintergrund selbst dafuer sorgen, dass sie
/// Rechenzeit behaelt (`beginBackgroundTask`).
@MainActor
final class SlicerModel: ObservableObject {

    enum Progress: Equatable {
        case idle
        case running(percent: Int, stage: String)
        case done(seconds: Double, printMinutes: Int, grams: Double)
        case failed(String)
        case cancelled
    }

    @Published private(set) var objects: [PsmCore.ObjectInfo] = []
    @Published private(set) var progress: Progress = .idle
    @Published private(set) var memoryWarning: String?
    @Published private(set) var coreVersion: String = "?"

    /// Welches Objekt gerade angefasst ist, oder nichts.
    @Published private(set) var selectedId: Int32?

    /// Steigt bei jeder Modelaenderung - der Viewport zeichnet dann neu.
    /// Gegenstueck zu sceneRevision in SlicerService auf Android.
    @Published private(set) var sceneRevision: Int = 0

    var sessionHandle: OpaquePointer? { core?.sessionHandle }

    /// Verzeichnis mit den GLES-Shadern aus PrusaSlicer. Sie liegen im
    /// App-Bundle, nicht im Datenverzeichnis - anders als auf Android,
    /// wo sie beim ersten Start ausgepackt werden.
    var shaderDir: String {
        Bundle.main.resourceURL?
            .appendingPathComponent("psresources/shaders/ES").path ?? ""
    }

    private var core: PsmCore?
    private var sliceTask: Task<Void, Never>?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    func start() {
        guard core == nil else { return }
        do {
            let support = FileManager.default.urls(for: .applicationSupportDirectory,
                                                   in: .userDomainMask)[0]
            let dataDir = support.appendingPathComponent("psmdata")
            try? FileManager.default.createDirectory(at: dataDir, withIntermediateDirectories: true)

            guard let resDir = Bundle.main.resourceURL?.appendingPathComponent("psresources") else {
                progress = .failed("Ressourcen fehlen im App-Bundle")
                return
            }

            let c = try PsmCore(dataDir: dataDir.path, resourceDir: resDir.path)
            try? c.loadBundledPresets()
            core = c
            coreVersion = PsmCore.coreVersion
        } catch {
            progress = .failed(error.localizedDescription)
        }
    }

    func load(url: URL) {
        guard let core else { return }
        // Aus der Files-App kommen sicherheitsbeschraenkte URLs.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        do {
            // libslic3r arbeitet mit Dateipfaden - deshalb erst in den
            // App-Container kopieren.
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(url.lastPathComponent)
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.copyItem(at: url, to: dest)

            try core.loadModel(path: dest.path)
            refresh()
            checkMemory()
        } catch {
            progress = .failed(error.localizedDescription)
        }
    }

    func select(_ id: Int32?) { selectedId = id }

    func remove(_ id: Int32) {
        try? core?.removeObject(id)
        refresh()
    }

    func refresh() {
        guard let core else { return }
        objects = core.listObjects().compactMap { core.objectInfo($0) }
        sceneRevision += 1
    }

    private func checkMemory() {
        guard let core else { return }
        let need = core.estimatedSliceMemory
        let have = PsmCore.availableMemory
        memoryWarning = need > UInt64(Double(have) * 0.8)
            ? "Dieses Modell braucht geschaetzt \(need / 1_048_576) MB, verfuegbar sind etwa \(have / 1_048_576) MB. Das kann fehlschlagen."
            : nil
    }

    func slice() {
        guard let core, sliceTask == nil else { return }

        // Ohne diesen Antrag beendet iOS die Rechenarbeit, sobald die App
        // in den Hintergrund geht - ein Slice dauert aber Minuten.
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "psm-slice") { [weak self] in
            self?.cancel()
        }

        progress = .running(percent: 0, stage: "wird vorbereitet")
        let t0 = Date()

        sliceTask = Task.detached(priority: .userInitiated) { [weak self] in
            do {
                try core.startSlice { percent, stage in
                    Task { @MainActor in
                        self?.progress = .running(percent: percent, stage: stage)
                    }
                    return false
                }
                let state = core.awaitSlice()
                let secs = Date().timeIntervalSince(t0)

                await MainActor.run {
                    switch state {
                    case .done:
                        let st = core.sliceStats()
                        self?.progress = .done(
                            seconds: secs,
                            printMinutes: Int((st?.printTimeSeconds ?? 0) / 60),
                            grams: st?.filamentGrams ?? 0
                        )
                    case .cancelled: self?.progress = .cancelled
                    default:         self?.progress = .failed(core.lastError)
                    }
                    self?.finishSlice()
                }
            } catch {
                await MainActor.run {
                    self?.progress = .failed(error.localizedDescription)
                    self?.finishSlice()
                }
            }
        }
    }

    func cancel() {
        core?.cancelSlice()
    }

    func exportGcode(to url: URL) throws {
        try core?.exportGcode(to: url.path)
    }

    private func finishSlice() {
        sliceTask = nil
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}
