import Foundation
import SwiftUI
import UIKit   // beginBackgroundTask
import PSMShared

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

    /// Die Betten des Projekts. Mehrbett gibt es im Kern seit langem;
    /// auf der iOS-Seite war es nur nicht sichtbar.
    @Published private(set) var beds: [PsmCore.Bed] = []
    @Published private(set) var progress: Progress = .idle
    @Published private(set) var memoryWarning: String?
    @Published private(set) var coreVersion: String = "?"

    /// Die Kennzahlen des letzten Slice. Frueher standen nur Zeit und
    /// Gewicht im Fortschrittstext; fuer eine Zusammenfassung braucht es
    /// auch Laenge, Kosten und Objektzahl.
    @Published private(set) var stats: PsmCore.SliceStats?

    /// Der fertige G-Code als Datei im Zwischenspeicher.
    ///
    /// Auf iOS gibt es keinen Ordner, in den eine App einfach schreibt -
    /// die Datei muss erst existieren, bevor das Teilen-Blatt sie
    /// weiterreichen kann.
    @Published private(set) var gcodeURL: URL?

    /// Das gesicherte Projekt als Datei - dieselbe Ueberlegung wie beim
    /// G-Code: erst schreiben, dann teilen.
    @Published private(set) var projectURL: URL?

    /// Was beim Oeffnen eines Projekts anders lief als darin stand.
    /// Bleibt stehen, bis der Nutzer es weggeklickt hat.
    @Published var projectNotice: String?

    /// Beschriftung des naechsten Zurueck-Schritts, leer wenn keiner da
    /// ist. Ein Zurueck-Knopf, der nicht sagt, was er zuruecknimmt, wird
    /// nur zoegernd benutzt.
    @Published private(set) var undoLabel = ""
    @Published private(set) var redoLabel = ""

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

    /// Nicht privat: der Einstellungsrenderer liest Typ, Grenzen und
    /// Auswahlwerte direkt beim Kern. Sie hier alle durchzureichen waere
    /// eine zweite, immer veraltete Fassung des ABI.
    private(set) var core: PsmCore?
    private var sliceTask: Task<Void, Never>?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    @Published private(set) var credentialSelfTestResult: String?

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
            PsUiCatalog.load(language: PsUiCatalog.language)

            // Massgeblich ist die gemerkte Wahl, nicht was der Kern an
            // Profilen kennt: nach loadBundledPresets waeren immer welche
            // da, und die Ersteinrichtung erschiene nie.
            //
            // Und geladen wird nur das Gewaehlte. Alle 37 Modelle kosten
            // 13,5 s Start und 5762 Filamente in den Listen, ein einzelner
            // Drucker 1,9 s und 189.
            // Der UI-Test braucht einen unberuehrten Zustand, sonst
            // startet der zweite Durchlauf mit eingerichtetem Drucker
            // und prueft nichts mehr. Nur ueber ein Startargument - eine
            // Einstellung in der App waere ein Schalter, mit dem sich
            // versehentlich alles loeschen liesse.
            let argumente = ProcessInfo.processInfo.arguments
            if argumente.contains("-psm-credential-self-test") {
                credentialSelfTestResult = runCredentialSelfTest()
            }
            if argumente.contains("-psm-reset-setup") {
                Self.storedPrinters = []
            }
            // Fuer Tests, die den Arbeitsbereich brauchen und nicht die
            // Ersteinrichtung: einen gaengigen Drucker vorgeben, statt
            // sich jedes Mal durch die Auswahl zu klicken.
            if argumente.contains("-psm-preset-printer"), Self.storedPrinters.isEmpty {
                Self.storedPrinters = ["PrusaResearch:MK4S:0.4"]
            }

            let gewaehlt = Self.storedPrinters
            if gewaehlt.isEmpty {
                setupNeeded = true
                printerModels = c.printerModels()
            } else {
                try? c.installPrinters(Array(gewaehlt))
                // Acht Positionen und eindeutige Farben sind ausschliesslich
                // ein reproduzierbarer UI-Testzustand. Im normalen Start
                // bestimmt das installierte Druckerprofil die Extruderzahl.
                if argumente.contains("-psm-test-eight-extruders") {
                    try? c.setConfig("nozzle_diameter", Array(repeating: "0.4", count: 8).joined(separator: ","))
                }
                if argumente.contains("-psm-test-colormix-colors") {
                    let colors = ["#FF0000", "#0000FF"] + Array(repeating: "#808080", count: 6)
                    for (index, color) in colors.enumerated() {
                        try? c.setExtruderColor(index, color)
                    }
                }
                setupNeeded = false
                // Ein Wuerfel fuer die Tests, die etwas auf dem Bett
                // brauchen: Schneiden, Auswahl, Gizmos. Er kommt hinter
                // die Profile - ohne Drucker gibt es kein Bett, und ein
                // Modell ohne Bett landet irgendwo.
                if argumente.contains("-psm-load-cube") { ladeTestWuerfel() }
            }
        } catch {
            progress = .failed(error.localizedDescription)
        }
    }

    /// Erzeugt einen 20-mm-Wuerfel als STL und laedt ihn.
    ///
    /// Bewusst gerechnet statt mitgeliefert: eine Datei im Bundle waere
    /// in jeder ausgelieferten App dabei, nur damit ein Test etwas zum
    /// Anfassen hat.
    ///
    /// Jede Zahl wird einzeln angehaengt. Der erste Anlauf schrieb die
    /// zwoelf Fliesskommazahlen eines Dreiecks aus einem
    /// zusammengesetzten Array - dabei kamen nur sechs an, und die Datei
    /// war 396 statt 684 Bytes gross. libslic3r meldete daraufhin nur
    /// "Loading of a model file failed", ohne zu sagen, woran.
    private func ladeTestWuerfel() {
        let a: Float = 20
        let ecken: [SIMD3<Float>] = [
            [0, 0, 0], [a, 0, 0], [a, a, 0], [0, a, 0],
            [0, 0, a], [a, 0, a], [a, a, a], [0, a, a],
        ]
        // Zwoelf Dreiecke, von aussen gesehen gegen den Uhrzeigersinn.
        let flaechen: [(Int, Int, Int)] = [
            (0, 2, 1), (0, 3, 2),   // unten
            (4, 5, 6), (4, 6, 7),   // oben
            (0, 1, 5), (0, 5, 4),   // vorn
            (1, 2, 6), (1, 6, 5),   // rechts
            (2, 3, 7), (2, 7, 6),   // hinten
            (3, 0, 4), (3, 4, 7),   // links
        ]

        // 80 Byte Kopf, vier Byte Anzahl, dann je Dreieck 50 Byte.
        var daten = Data(count: 80)
        func zahl(_ wert: Float) {
            var f = wert
            withUnsafeBytes(of: &f) { daten.append(contentsOf: $0) }
        }
        var anzahl = UInt32(flaechen.count)
        withUnsafeBytes(of: &anzahl) { daten.append(contentsOf: $0) }

        for (i, j, k) in flaechen {
            // Die Normale darf null bleiben - libslic3r rechnet sie aus
            // der Reihenfolge der Ecken.
            zahl(0); zahl(0); zahl(0)
            for ecke in [ecken[i], ecken[j], ecken[k]] {
                zahl(ecke.x); zahl(ecke.y); zahl(ecke.z)
            }
            var attribut = UInt16(0)
            withUnsafeBytes(of: &attribut) { daten.append(contentsOf: $0) }
        }

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("psm-testwuerfel.stl")
        do {
            try daten.write(to: url)
            _ = try core?.loadModel(path: url.path)
        } catch {
            NSLog("Testwuerfel liess sich nicht laden: %@", String(describing: error))
        }
        refresh()
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

    var extruderCount: Int { core?.extruderCount ?? 1 }

    /// Das gewaehlte Profil eines Bereichs - "print", "filament",
    /// "printer".
    func selectedPreset(for tab: String) -> String? {
        guard let core else { return nil }
        switch tab {
        case "print":    return core.selectedPreset(.print)
        case "filament": return core.selectedPreset(.filament)
        case "printer":  return core.selectedPreset(.printer)
        default:         return nil
        }
    }

    func setConfig(_ key: String, _ value: String) {
        try? core?.setConfig(key, value)
        // Eine Aenderung an den Einstellungen macht ein vorhandenes
        // Slice-Ergebnis ungueltig und kann das Bett veraendern.
        sceneRevision += 1
    }

    func config(_ key: String) -> String? { core?.config(key) }

    /// Material und Farbe je Extruder.
    ///
    /// Bei einem Extruder ist das dasselbe wie das gewaehlte
    /// Filamentprofil; erst mit MMU oder Werkzeugwechsler wird es eine
    /// eigene Frage.
    func extruderFilament(_ index: Int) -> String {
        core?.extruderFilament(index) ?? ""
    }

    func setExtruderFilament(_ index: Int, _ name: String) {
        try? core?.setExtruderFilament(index, name)
        sceneRevision += 1
        objectWillChange.send()
    }

    func extruderColor(_ index: Int) -> String {
        core?.extruderColor(index) ?? ""
    }

    func setExtruderColor(_ index: Int, _ hex: String) {
        try? core?.setExtruderColor(index, hex)
        sceneRevision += 1
        objectWillChange.send()
    }

    private func runCredentialSelfTest() -> String {
        let host = "credential-test.psmobile.invalid"
        let store = PrinterCredentialStore()
        do {
            try store.remove(host: host, mode: .apiKey)
            try store.save(.init(host: host, mode: .apiKey, secret: "test-secret"))
            defer { try? store.remove(host: host, mode: .apiKey) }
            guard try store.load(host: host, mode: .apiKey)?.secret == "test-secret",
                  UserDefaults.standard.object(forKey: "printer.apiKey") == nil else { return "failed" }
            return "passed"
        } catch {
            return "failed"
        }
    }

    /// Virtuelle ColorMix-Positionen bleiben im Kernprojekt erhalten und
    /// veraendern niemals die Filamentwahl der physischen Positionen.
    func colorMixRecipes() -> [ColorMixRecipe] {
        guard let source = try? core?.colorMixJson() else { return [] }
        return ColorMixCodec.shared.decode(source: source)
    }

    @discardableResult
    func saveColorMix(_ recipes: [ColorMixRecipe]) -> Bool {
        guard let core else { return false }
        let colors = (0..<extruderCount).map { index in
            let color = extruderColor(index)
            return color.isEmpty ? "#808080" : color
        }
        do {
            try core.setColorMixJson(ColorMixCodec.shared.encode(physicalColors: colors, recipes: recipes))
            sceneRevision += 1
            objectWillChange.send()
            return true
        } catch {
            return false
        }
    }

    /// Der Extruder eines Objekts. 0 heisst: der Standard des Profils.
    func objectExtruder(_ id: Int32) -> Int32 { core?.objectExtruder(id) ?? 0 }

    func setObjectExtruder(_ id: Int32, _ extruder: Int32) {
        try? core?.setObjectExtruder(id, extruder)
        refresh()
    }

    func presetNames(_ type: PsmCore.PresetType) -> [String] {
        core?.presetNames(type) ?? []
    }

    func selectPreset(_ type: PsmCore.PresetType, _ name: String) {
        try? core?.selectPreset(type, name)
        // Ein anderer Drucker heisst ein anderes Bett, ein anderes Profil
        // andere Masse - beides muss der Viewport sehen.
        sceneRevision += 1
        refresh()
    }

    /// Die Grundflaechen der Objekte, wie sie die Haftungsberatung
    /// braucht. Die Beurteilung selbst steht im gemeinsamen Modul.
    var footprints: [AdhesionAdvice.Footprint] {
        objects.map {
            AdhesionAdvice.Footprint(widthMm: $0.sizeMm.x,
                                     depthMm: $0.sizeMm.y,
                                     heightMm: $0.sizeMm.z)
        }
    }

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
        beds = core.beds()
        undoLabel = core.undoCount > 0 ? core.undoLabel : ""
        redoLabel = core.redoCount > 0 ? core.redoLabel : ""
        sceneRevision += 1
    }

    func undo() {
        try? core?.undo()
        selectedId = nil
        refresh()
    }

    func redo() {
        try? core?.redo()
        selectedId = nil
        refresh()
    }

    /// Oeffnet eine 3MF als vollstaendiges Projekt: Positionen, Betten
    /// und Konfiguration. Anders als beim Laden eines Modells wird das
    /// aktuelle Bett dabei ersetzt.
    func loadProject(url: URL) {
        guard let core else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let ziel = FileManager.default.temporaryDirectory
                .appendingPathComponent(url.lastPathComponent)
            try? FileManager.default.removeItem(at: ziel)
            try FileManager.default.copyItem(at: url, to: ziel)

            let info = try core.loadProject(path: ziel.path)
            projectNotice = Self.hinweis(zu: info)
            refresh()
        } catch {
            progress = .failed(error.localizedDescription)
        }
    }

    /// Sichert alle Betten als PrusaSlicer-taugliches 3MF.
    func saveProject(name: String = "PSMobile") {
        guard let core else { return }
        let datei = SliceSummary.shared.fileName(project: name)
            .replacingOccurrences(of: ".gcode", with: ".3mf")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(datei)
        try? FileManager.default.removeItem(at: url)
        do {
            try core.saveProject(path: url.path)
            projectURL = url
        } catch {
            projectURL = nil
            projectNotice = error.localizedDescription
        }
    }

    /// Nur melden, was den Nutzer betrifft: ein anderes Profil als
    /// gespeichert, und entfernte Skripte. Alles andere waere eine
    /// Meldung, die man wegklickt, ohne sie zu lesen.
    private static func hinweis(zu info: PsmCore.ProjectImport) -> String? {
        var zeilen: [String] = []
        if info.printerChanged {
            zeilen.append(SimpleModeState.shared.text(
                english: "Printer profile differs: " + info.selectedPrinter,
                german: "Anderes Druckerprofil aktiv: " + info.selectedPrinter))
        }
        if info.printChanged {
            zeilen.append(SimpleModeState.shared.text(
                english: "Print profile differs: " + info.selectedPrint,
                german: "Anderes Druckprofil aktiv: " + info.selectedPrint))
        }
        if info.postProcessRemoved {
            zeilen.append(SimpleModeState.shared.text(
                english: "Embedded post-processing scripts were not loaded.",
                german: "Eingebettete Nachbearbeitungsskripte wurden nicht übernommen."))
        }
        return zeilen.isEmpty ? nil : zeilen.joined(separator: "  ·  ")
    }

    /// Obergrenze aus dem C-ABI (PSM_MAX_BEDS). Sie steht dort, damit
    /// beide Seiten dieselbe Zahl nennen.
    static let maxBeds = 36

    func arrange() {
        try? core?.arrange()
        refresh()
    }

    func duplicate(_ ids: [Int32]) {
        for id in ids { try? core?.duplicate(id) }
        refresh()
    }

    func removeObjects(_ ids: [Int32]) {
        for id in ids { try? core?.removeObject(id) }
        if let gewaehlt = selectedId, ids.contains(gewaehlt) { selectedId = nil }
        refresh()
    }

    func selectBed(_ index: Int) {
        try? core?.selectBed(index)
        // Ein anderes Bett heisst andere Objekte und eine andere
        // Ansicht - die Auswahl von vorhin gibt es dort nicht.
        selectedId = nil
        refresh()
    }

    /// Schiebt Objekte auf ein anderes Bett. Ein Index jenseits der
    /// vorhandenen Betten legt eines an - sonst waere ein volles Bett
    /// eine Sackgasse.
    func moveToBed(_ ids: [Int32], target: Int) {
        guard let core else { return }
        var ziel = target
        if ziel >= beds.count {
            guard let neu = try? core.addBed() else { return }
            ziel = neu
            // addBed waehlt das neue Bett aus; die Objekte liegen aber
            // noch auf dem alten.
            try? core.selectBed(beds.first(where: { $0.active })?.index ?? 0)
        }
        for id in ids { _ = try? core.moveToBed(id, target: ziel) }
        selectedId = nil
        refresh()
    }

    func split(_ id: Int32) {
        _ = try? core?.splitObject(id)
        selectedId = nil
        refresh()
    }

    func cut(_ id: Int32, zMm: Float) {
        _ = try? core?.cut(id, zMm: zMm)
        selectedId = nil
        refresh()
    }

    /// Gleichmaessig skalieren. Der Kern kennt drei Achsen; ungleiche
    /// Faktoren gibt es in der Oberflaeche bewusst nicht - wer ein
    /// Modell in einer Achse streckt, druckt selten das, was er wollte.
    func setUniformScale(_ id: Int32, _ faktor: Float) {
        try? core?.setScale(id, SIMD3(faktor, faktor, faktor))
        refresh()
    }

    /// Auf ein Zielmass der laengsten Kante bringen.
    func scaleToSize(_ id: Int32, _ mm: Float) {
        try? core?.scaleToFit(id, sizeMm: mm)
        refresh()
    }

    /// Dreht eine Achse auf einen festen Winkel. Gerechnet wird im Kern
    /// in Radiant, eingegeben in Grad.
    func setRotationAxis(_ id: Int32, _ achse: Int, grad: Float) {
        guard let objekt = objects.first(where: { $0.id == id }) else { return }
        var r = objekt.rotation
        r[achse] = grad * .pi / 180
        try? core?.setRotation(id, r)
        refresh()
    }

    /// Dreht um einen Betrag weiter - fuer die Vierteldrehungen.
    func rotateBy(_ id: Int32, achse: Int, grad: Float) {
        guard let objekt = objects.first(where: { $0.id == id }) else { return }
        var r = objekt.rotation
        r[achse] += grad * .pi / 180
        try? core?.setRotation(id, r)
        refresh()
    }

    func paint(_ id: Int32,
               volume: Int,
               facet: Int,
               tool: PsmCore.PaintTool,
               state: Int32,
               radiusMm: Float) {
        try? core?.paint(id, volume: volume, facet: facet,
                         tool: tool, state: state, radiusMm: radiusMm)
        // Nur neu zeichnen, nicht die Objektliste neu lesen: beim
        // Streichen kaeme sonst je Beruehrung ein voller Durchlauf.
        sceneRevision += 1
    }

    func clearPaint(_ id: Int32, tool: PsmCore.PaintTool) {
        try? core?.clearPaint(id, tool: tool)
        sceneRevision += 1
        objectWillChange.send()
    }

    func paintCount(_ id: Int32, tool: PsmCore.PaintTool) -> Int {
        core?.paintCount(id, tool: tool) ?? 0
    }

    func layOnFacet(_ id: Int32, volume: Int, facet: Int) {
        try? core?.layOnFacet(id, volume: volume, facet: facet)
        refresh()
    }

    func layerProfile(_ id: Int32) -> [LayerProfile.Point] {
        (core?.layerProfile(id) ?? []).map {
            LayerProfile.Point(z: $0.z, height: $0.height)
        }
    }

    func setLayerProfile(_ id: Int32, points: [LayerProfile.Point]) {
        try? core?.setLayerProfile(id, points: points.map { (z: $0.z, height: $0.height) })
        refresh()
    }

    func clearLayerProfile(_ id: Int32) {
        try? core?.setLayerProfile(id, points: [])
        refresh()
    }

    func volumeCount(_ id: Int32) -> Int { core?.volumeCount(id) ?? 0 }

    func volumeInfo(_ id: Int32, at index: Int) -> PsmCore.VolumeInfo? {
        core?.volumeInfo(id, at: index)
    }

    func setVolumeExtruder(_ id: Int32, at index: Int, _ extruder: Int32) {
        try? core?.setVolumeExtruder(id, at: index, extruder)
        refresh()
    }

    func fitToBed(_ id: Int32) {
        try? core?.fitToBed(id)
        refresh()
    }

    func mirror(_ id: Int32, axis: Int32) {
        try? core?.mirror(id, axis: axis)
        refresh()
    }

    func dropToBed(_ id: Int32) {
        try? core?.dropToBed(id)
        refresh()
    }

    func setInstances(_ id: Int32, count: Int32) {
        try? core?.setInstances(id, count: count)
        refresh()
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
        stats = nil
        // Der G-Code des vorigen Laufs gehoert zum vorigen Stand. Ihn
        // stehen zu lassen, waere die gefaehrlichere Variante: man
        // teilt eine Datei, die zu dem, was auf dem Bett liegt, nicht
        // mehr passt.
        gcodeURL = nil
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
                        self?.stats = st
                        self?.writeGcode()
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

    /// Legt den G-Code als Datei ab, damit das Teilen-Blatt sie
    /// weitergeben kann. Der Name kommt aus dem gemeinsamen Modul - dort
    /// steht auch, was aus einem Modellnamen mit Leerzeichen und
    /// Schraegstrichen wird.
    private func writeGcode() {
        guard let core else { return }
        let name = SliceSummary.shared.fileName(project: objects.first?.name ?? "")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try? FileManager.default.removeItem(at: url)
        do {
            try core.exportGcode(to: url.path)
            gcodeURL = url
        } catch {
            gcodeURL = nil
        }
    }

    /// Zurueck in den Ruhezustand - das Blatt ist weg, das Ergebnis
    /// bleibt es aber nicht: ein neuer Slice ueberschreibt es ohnehin.
    func dismissProgress() {
        progress = .idle
    }

    /// Was fehlt, bevor geschnitten werden kann. Die Beurteilung steht im
    /// gemeinsamen Modul, damit beide Apps dieselben Gruende nennen.
    var sliceBlockers: [String] {
        SliceSummary.shared.blockers(
            objects: Int32(objects.count),
            printer: selectedPreset(for: "printer") ?? "",
            filament: selectedPreset(for: "filament") ?? "",
            print: selectedPreset(for: "print") ?? ""
        )
    }

    private func finishSlice() {
        sliceTask = nil
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}
