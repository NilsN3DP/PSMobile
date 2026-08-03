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

    /// Ob noch kein Drucker eingerichtet ist.
    ///
    /// Ohne Drucker gibt es keine Profile, ohne Profile kein Bett und
    /// nichts zu slicen. Deshalb kommt die Ersteinrichtung vor allem
    /// anderen - genauso wie auf Android.
    @Published private(set) var setupNeeded = false
    @Published private(set) var printerModels: [PsmCore.PrinterModel] = []
    @Published private(set) var setupBusy = false

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
            core = c
            coreVersion = PsmCore.coreVersion

            // Massgeblich ist die gemerkte Wahl, nicht was der Kern an
            // Profilen kennt: nach loadBundledPresets waeren immer welche
            // da, und die Ersteinrichtung erschiene nie.
            //
            // Und geladen wird nur das Gewaehlte. Alle 37 Modelle kosten
            // 13,5 s Start und 5762 Filamente in den Listen, ein einzelner
            // Drucker 1,9 s und 189.
            let gewaehlt = Self.storedPrinters
            if gewaehlt.isEmpty {
                setupNeeded = true
                printerModels = c.printerModels()
            } else {
                try? c.installPrinters(Array(gewaehlt))
                setupNeeded = false
            }
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

    /// Uebernimmt die Druckerwahl aus der Ersteinrichtung.
    ///
    /// Laeuft abgetrennt: das Installieren liest und schreibt Dutzende
    /// Profildateien und blockiert sonst die Oberflaeche.
    func completeSetup(_ keys: [String]) {
        guard let core, !setupBusy else { return }
        setupBusy = true
        Task {
            do {
                try core.installPrinters(keys)
                await MainActor.run {
                    // Erst merken, dann als erledigt melden - sonst steht
                    // beim naechsten Start wieder die Einrichtung da.
                    Self.storedPrinters = Set(keys)
                    setupNeeded = false
                    setupBusy = false
                    sceneRevision += 1
                }
            } catch {
                await MainActor.run {
                    progress = .failed(error.localizedDescription)
                    setupBusy = false
                }
            }
        }
    }

    /// Die Einrichtung noch einmal oeffnen, etwa um einen Drucker
    /// nachzutragen.
    func reopenSetup() {
        guard let core else { return }
        printerModels = core.printerModels()
        setupNeeded = true
    }

    /// Die gemerkte Druckerwahl. Gegenstueck zu den Preferences auf
    /// Android - dieselbe Rolle, dieselbe Bedeutung.
    private static let printersKey = "printers"

    static var storedPrinters: Set<String> {
        get { Set(UserDefaults.standard.stringArray(forKey: printersKey) ?? []) }
        set { UserDefaults.standard.set(Array(newValue), forKey: printersKey) }
    }

    /// Bereits eingerichtete Modelle, damit die Auswahl nicht bei null
    /// beginnt, wenn man nur eine Duesengroesse ergaenzen will.
    var installedPrinters: Set<String> { Self.storedPrinters }

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
        // Ohne Auskunft ueber den freien Speicher wird nicht geraten. Eine
        // Warnung, die auf einer erfundenen Zahl steht, ist schlimmer als
        // keine - man gewoehnt sich an sie und uebersieht die echte.
        guard let have = PsmCore.availableMemory else { memoryWarning = nil; return }
        let need = core.estimatedSliceMemory
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
