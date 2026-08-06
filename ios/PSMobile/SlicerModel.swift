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
    /// Alle ausgewaehlten Objekte. `selectedId` ist das Hauptobjekt
    /// darin - an ihm haengen die Griffe, und auf es beziehen sich die
    /// Zahlen im Inspektor.
    @Published private(set) var selectedIds: Set<Int32> = []
    /// Bis zu welcher Einstufung Parameter gezeigt werden.
    ///
    /// Gilt fuer die ganze App und ueberlebt den Start: wer sich einmal
    /// fuer Expert entschieden hat, will das nicht auf jeder Seite neu
    /// sagen.
    @Published var sichtbarkeit: PsmCore.ConfigMode = {
        let gemerkt = UserDefaults.standard.integer(forKey: "psm.sichtbarkeit")
        return PsmCore.ConfigMode(rawValue: Int32(gemerkt)) ?? .advanced
    }() {
        didSet {
            UserDefaults.standard.set(Int(sichtbarkeit.rawValue), forKey: "psm.sichtbarkeit")
        }
    }

    /// Siehe filamentCatalog() - einmal holen reicht.
    private var filamentCache: [FilamentCatalog.Entry]?

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
                if argumente.contains("-psm-load-cube") {
                    // Viele Würfel erzwingen im UI-Test eine lange
                    // Objektliste. So bleibt der Inspector-Fokus nicht
                    // nur für den bequemen Ein-Objekt-Fall geprüft.
                    let anzahl = argumente.contains("-psm-test-many-cubes") ? 12 : 1
                    for _ in 0..<anzahl { ladeTestWuerfel() }
                }
            }
        } catch {
            progress = .failed(error.localizedDescription)
        }
    }

    /// Laedt den Testwuerfel aus `Testkoerper`.
    ///
    /// Der Wuerfel selbst steht dort, weil ihn auch der Selbsttest
    /// braucht - zwei Fassungen desselben Koerpers waeren eine zu viel.
    private func ladeTestWuerfel() {
        do {
            let datei = try Testkoerper.wuerfelDatei()
            _ = try core?.loadModel(path: datei.path)
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

    func select(_ id: Int32?) {
        selectedId = id
        selectedIds = id.map { [$0] } ?? []
    }

    /// Ein Objekt zur Auswahl hinzunehmen oder herausnehmen.
    ///
    /// Das zuletzt Angetippte wird Hauptobjekt. Nimmt man das
    /// Hauptobjekt heraus, rueckt ein anderes nach - eine Auswahl ohne
    /// Hauptobjekt haette keine Griffe.
    func toggleSelection(_ id: Int32) {
        var menge = selectedIds
        if menge.contains(id) {
            menge.remove(id)
            selectedIds = menge
            if selectedId == id { selectedId = menge.first }
        } else {
            menge.insert(id)
            selectedIds = menge
            selectedId = id
        }
        sceneRevision += 1
    }

    func selectAll() {
        selectedIds = Set(objects.map { $0.id })
        if selectedId == nil { selectedId = objects.first?.id }
        sceneRevision += 1
    }

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

    /// Die Profilnamen eines Bereichs - "print", "filament", "printer".
    /// Gegenstueck zu `selectedPreset(for:)`, fuer die Profilsuche in den
    /// Einstellungen.
    func presetNames(for tab: String) -> [String] {
        switch tab {
        case "print":    return presetNames(.print)
        case "filament": return presetNames(.filament)
        case "printer":  return presetNames(.printer)
        default:         return []
        }
    }

    /// Ein Profil eines Bereichs waehlen, ueber denselben String wie
    /// `selectedPreset(for:)` statt ueber den Aufrufer selbst den Typ
    /// zuordnen zu lassen.
    func selectPreset(for tab: String, _ name: String) {
        switch tab {
        case "print":    selectPreset(.print, name)
        case "filament": selectPreset(.filament, name)
        case "printer":  selectPreset(.printer, name)
        default:         break
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

    /// Alle Filamentprofile mit Typ und Farbe.
    ///
    /// Gemerkt, solange sich die Profile nicht aendern: bei
    /// vierhundert Profilen sind das achthundert Abfragen an den Kern,
    /// und bei jedem Tastendruck im Suchfeld erneut waere das spuerbar.
    func filamentCatalog() -> [FilamentCatalog.Entry] {
        guard let core else { return [] }
        let namen = core.presetNames(.filament)
        if let gemerkt = filamentCache, gemerkt.count == namen.count,
           gemerkt.first?.rawPreset == namen.first {
            return gemerkt
        }
        let eintraege = namen.map { name in
            FilamentCatalog.shared.entry(
                rawPreset: name,
                type: core.presetOption(.filament, name, "filament_type") ?? "",
                colorHex: core.presetOption(.filament, name, "filament_colour") ?? "")
        }
        filamentCache = eintraege
        return eintraege
    }

    // MARK: - Ungespeicherte Profilaenderungen

    /// Eine Aenderung am Profil, mit lesbarem Namen.
    struct Profilaenderung: Identifiable {
        let art: PsmCore.PresetType
        let key: String
        let bezeichnung: String
        let vorher: String
        let jetzt: String
        var id: String { "\(art.rawValue).\(key)" }
    }

    /// Alles, was gegenueber den gewaehlten Profilen geaendert ist.
    ///
    /// Ueber alle drei Sammlungen: wer im Advanced Mode an der
    /// Schichthoehe und am Filament dreht, hat zwei geaenderte Profile,
    /// und beide gehoeren in denselben Dialog.
    ///
    /// Der lesbare Name kommt aus dem Kern, nicht aus einer Liste hier -
    /// "fill_pattern" sagt niemandem etwas, "Fuellmuster" schon.
    func profilaenderungen() -> [Profilaenderung] {
        guard let core else { return [] }
        var alle: [Profilaenderung] = []
        for art in [PsmCore.PresetType.print, .filament, .printer] {
            for wert in core.dirtyValues(art) {
                let meta = core.configMeta(for: wert.key)
                alle.append(Profilaenderung(
                    art: art,
                    key: wert.key,
                    bezeichnung: (meta?.label).flatMap { $0.isEmpty ? nil : $0 } ?? wert.key,
                    vorher: wert.oldValue,
                    jetzt: wert.newValue))
            }
        }
        return alle
    }

    /// Zurueck auf die Werte des Profils - in allen drei Sammlungen.
    func profilaenderungenVerwerfen() {
        guard let core else { return }
        for art in [PsmCore.PresetType.print, .filament, .printer] {
            try? core.discardChanges(art)
        }
        sceneRevision += 1
        refresh()
    }

    /// Den geaenderten Stand als eigenes Profil sichern.
    ///
    /// Nur die Sammlungen, in denen wirklich etwas geaendert ist: ein
    /// unveraendertes Filamentprofil unter neuem Namen zu duplizieren
    /// waere eine Karteileiche.
    func profilSichern(als name: String) {
        guard let core, !name.isEmpty else { return }
        for art in [PsmCore.PresetType.print, .filament, .printer]
        where !core.dirtyValues(art).isEmpty {
            try? core.savePreset(art, as: name)
        }
        refresh()
    }

    /// Das gewaehlte Profil mit dem geaenderten Stand ueberschreiben.
    func profilUeberschreiben() {
        guard let core else { return }
        for art in [PsmCore.PresetType.print, .filament, .printer]
        where !core.dirtyValues(art).isEmpty {
            let tab = art == .print ? "print" : art == .filament ? "filament" : "printer"
            if let name = selectedPreset(for: tab), !name.isEmpty {
                try? core.savePreset(art, as: name)
            }
        }
        refresh()
    }

    func selectPreset(_ type: PsmCore.PresetType, _ name: String) {
        try? core?.selectPreset(type, name)
        // Ein anderer Drucker heisst andere passende Filamente - der
        // gemerkte Katalog gilt dann nicht mehr.
        if type == .printer {
            filamentCache = nil
            // Und die bisherige Filamentwahl passt womoeglich nicht mehr.
            // Also unsere Standardwerte erneut anwenden - sie kennen die
            // neue Liste.
            standardwerteSetzen()
        }
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
                    self.standardwerteSetzen()
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

    /// Unsere Standardwerte setzen: Prusament PLA und Gyroid.
    ///
    /// Zwei bewusste Abweichungen von PrusaSlicers Voreinstellung, beide
    /// im gemeinsamen Modul begruendet. Sie greifen nach der
    /// Einrichtung und nach einem Druckerwechsel - dann wechselt auch
    /// die Liste der kompatiblen Filamente.
    ///
    /// Das Fuellmuster macht das Druckprofil damit "geaendert". Das ist
    /// der Preis dafuer, eine eigene Meinung zu haben, und er ist
    /// sichtbar statt versteckt.
    func standardwerteSetzen() {
        guard let core else { return }
        if let wunsch = Defaults.shared.preferredFilament(names: presetNames(.filament)) {
            try? core.selectPreset(.filament, wunsch)
        }
        setConfig("fill_pattern", Defaults.shared.FILL_PATTERN)
        refresh()
    }

    /// Die Einrichtung noch einmal oeffnen, etwa um einen Drucker
    /// nachzutragen.
    func reopenSetup() {
        guard let core else { return }
        printerModels = core.printerModels()
        setupNeeded = true
    }

    /// Eine laufende App darf die Einrichtung wieder verlassen; beim
    /// allerersten Start gibt es dagegen keinen nutzbaren Zielbildschirm.
    func dismissSetup() {
        guard !Self.storedPrinters.isEmpty, !setupBusy else { return }
        setupNeeded = false
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

    /// Wo Projekte liegen.
    ///
    /// Nicht das temporaere Verzeichnis: das raeumt iOS ohne Vorwarnung
    /// leer, und ein Projekt, das einen Neustart nicht ueberlebt, ist
    /// kein Projekt. Documents wird gesichert und ist in der
    /// Dateien-App sichtbar.
    var projectsDirectory: URL {
        let basis = FileManager.default.urls(for: .documentDirectory,
                                             in: .userDomainMask)[0]
        let ordner = basis.appendingPathComponent("Projects", isDirectory: true)
        try? FileManager.default.createDirectory(at: ordner,
                                                 withIntermediateDirectories: true)
        return ordner
    }

    /// Die gesicherten Projekte, neueste zuerst.
    ///
    /// Nach Aenderungsdatum und nicht nach Namen: wer ein Projekt sucht,
    /// sucht fast immer das zuletzt bearbeitete.
    func recentProjects(limit: Int = 8) -> [URL] {
        let inhalt = (try? FileManager.default.contentsOfDirectory(
            at: projectsDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles])) ?? []
        return inhalt
            .filter { $0.pathExtension.lowercased() == "3mf" }
            .sorted { a, b in
                let da = (try? a.resourceValues(forKeys: [.contentModificationDateKey]))?
                    .contentModificationDate ?? .distantPast
                let db = (try? b.resourceValues(forKeys: [.contentModificationDateKey]))?
                    .contentModificationDate ?? .distantPast
                return da > db
            }
            .prefix(limit)
            .map { $0 }
    }

    /// Leeres Bett. Der vorherige Stand bleibt zuruecknehmbar - ein
    /// versehentliches "Neu" darf keine Stunde Arbeit kosten.
    func newProject() {
        guard let core else { return }
        try? core.clearBed()
        projectURL = nil
        projectNotice = nil
        select(nil)
        refresh()
    }

    /// Was auf welcher Hoehe passiert - Farbwechsel, Pause, eigener Code.
    func customGcodeList() -> [PsmCore.CustomGcode] {
        core?.customGcodeList() ?? []
    }

    func addCustomGcode(_ e: PsmCore.CustomGcode) {
        try? core?.addCustomGcode(e)
        refresh()
    }

    func updateCustomGcode(_ index: Int, _ e: PsmCore.CustomGcode) {
        try? core?.updateCustomGcode(index, e)
        refresh()
    }

    func removeCustomGcode(_ index: Int) {
        try? core?.removeCustomGcode(index)
        refresh()
    }

    /// Repariert eine STL und legt das Ergebnis neben das Original.
    ///
    /// Nicht an derselben Stelle ueberschreiben: wenn die Reparatur
    /// etwas kaputtmacht, will man das Original noch haben.
    func repairSTL(_ url: URL) -> URL? {
        guard let core else { return nil }
        let ziel = projectsDirectory.appendingPathComponent(
            url.deletingPathExtension().lastPathComponent + "-repariert.stl")
        try? FileManager.default.removeItem(at: ziel)
        let offen = url.startAccessingSecurityScopedResource()
        defer { if offen { url.stopAccessingSecurityScopedResource() } }
        do {
            try core.repairSTL(input: url.path, output: ziel.path)
            return ziel
        } catch {
            projectNotice = error.localizedDescription
            return nil
        }
    }

    /// Wandelt eine G-Code-Datei zwischen ASCII und BGCode.
    func convertGcode(_ url: URL, toBinary: Bool) -> URL? {
        guard let core else { return nil }
        let endung = toBinary ? "bgcode" : "gcode"
        let ziel = projectsDirectory.appendingPathComponent(
            url.deletingPathExtension().lastPathComponent + "." + endung)
        try? FileManager.default.removeItem(at: ziel)
        let offen = url.startAccessingSecurityScopedResource()
        defer { if offen { url.stopAccessingSecurityScopedResource() } }
        do {
            try core.convertGcode(input: url.path, output: ziel.path, toBinary: toBinary)
            return ziel
        } catch {
            projectNotice = error.localizedDescription
            return nil
        }
    }

    /// Die Platte als eine einzige STL.
    ///
    /// Nicht dasselbe wie ein Projekt: hier geht die Anordnung mit, aber
    /// keine Profile und keine Bemalung. Gedacht zum Weitergeben an
    /// jemanden, der einen anderen Slicer benutzt.
    func exportPlate() -> URL? {
        guard let core else { return nil }
        let datei = SliceSummary.shared.fileName(project: "PSMobile")
            .replacingOccurrences(of: ".gcode", with: "-platte.stl")
        let url = projectsDirectory.appendingPathComponent(datei)
        try? FileManager.default.removeItem(at: url)
        do {
            try core.exportPlateSTL(path: url.path)
            return url
        } catch {
            projectNotice = error.localizedDescription
            return nil
        }
    }

    /// Sichert alle Betten als PrusaSlicer-taugliches 3MF.
    func saveProject(name: String = "PSMobile") {
        guard let core else { return }
        // Ein Projekt behaelt seinen Namen. Am PC heisst es nach dem
        // Projekt und wird beim Sichern ueberschrieben; wer bei jedem
        // Sichern einen neuen Namen bekommt, sammelt Karteileichen.
        //
        // Der sprechende Name aus output_filename_format gehoert an den
        // G-Code, nicht hierher - siehe writeGcode().
        //
        // Endung anhaengen statt ersetzen: der Kern lehnt jeden Pfad ab,
        // der nicht auf .3mf endet.
        let basis = (SliceSummary.shared.fileName(project: name) as NSString)
            .deletingPathExtension
        let datei = (basis.isEmpty ? "psmobile" : basis) + ".3mf"
        let url = projectsDirectory.appendingPathComponent(datei)
        try? FileManager.default.removeItem(at: url)
        do {
            try core.saveProject(path: url.path)
            projectURL = url
        } catch {
            projectURL = nil
            // Der Kern nennt nur die Tat, PrusaSlicer den Grund. Beides
            // gehoert zusammen - eine Meldung ohne Grund kann der
            // Nutzer nur wegklicken.
            let gruende = PsmLog.recent.suffix(3).joined(separator: "\n")
            projectNotice = gruende.isEmpty
                ? error.localizedDescription
                : error.localizedDescription + "\n" + gruende
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

    /// Der Panel-Pfad braucht Erfolg und Fehler als echten Rückgabewert.
    /// Ein `try?` würde gerade Locked/Full verschlucken.
    func arrange(target: Int, gapMm: Float) throws -> PsmCore.ArrangeResult {
        guard let core else {
            throw PsmCore.PsmError.createFailed("Core nicht bereit")
        }
        let ergebnis = try core.arrange(bed: target, gapMm: gapMm)
        refresh()
        return ergebnis
    }

    /// Kompatibler Einstieg älterer Werkzeugknöpfe. Die gemeinsame
    /// Arrange-Oberfläche verwendet immer die werfende Zielbett-Fassung.
    func arrange() {
        let aktiv = beds.first(where: { $0.active })?.index ?? 0
        do {
            _ = try arrange(target: aktiv, gapMm: 6)
        } catch {
            projectNotice = error.localizedDescription
        }
    }

    /// Alle nicht gesperrten Betten in einem Rutsch anordnen - der
    /// kurze Tipp auf den Arrange-Knopf. Wer nur ein Bett anordnen
    /// will, haelt den Knopf gedrueckt und waehlt es im Panel.
    func arrangeAll(gapMm: Float = 6) {
        for bett in beds where !bett.locked {
            _ = try? arrange(target: bett.index, gapMm: gapMm)
        }
    }

    func duplicate(_ ids: [Int32]) {
        for id in ids { try? core?.duplicate(id) }
        refresh()
    }

    func removeObjects(_ ids: [Int32]) {
        // Ein Befehl, ein Schritt. Ohne die Klammer muesste man zehnmal
        // zurueck, um ein Loeschen von zehn Objekten rueckgaengig zu
        // machen - und wuesste beim dritten Mal nicht mehr, wo man war.
        if ids.count > 1 { core?.beginHistory("Objekte entfernen") }
        defer { if ids.count > 1 { core?.endHistory() } }
        for id in ids { try? core?.removeObject(id) }
        if let gewaehlt = selectedId, ids.contains(gewaehlt) { selectedId = nil }
        refresh()
    }

    /// Wie ein Bett heisst - der eigene Name, sonst die Nummer.
    func bedLabel(_ index: Int) -> String {
        let name = beds.first(where: { $0.index == index })?
            .name.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return name.isEmpty
            ? SimpleModeState.shared.text(english: "Bed", german: "Bett") + " \(index + 1)"
            : name
    }

    func renameBed(_ index: Int, to name: String) {
        guard let core, let bed = beds.first(where: { $0.index == index }) else { return }
        do {
            try core.setBedMetadata(bed, name: name)
            refresh()
        } catch {
            projectNotice = error.localizedDescription
        }
    }

    func isBedLocked(_ index: Int) -> Bool {
        beds.first(where: { $0.index == index })?.locked ?? false
    }

    /// Ein gesperrtes Bett wird von Anordnen nicht angefasst.
    func toggleBedLock(_ index: Int) {
        guard let core, let bed = beds.first(where: { $0.index == index }) else { return }
        do {
            try core.setBedMetadata(bed, locked: !bed.locked)
            refresh()
        } catch {
            projectNotice = error.localizedDescription
        }
    }

    /// Ein weiteres Bett. Es wird gleich das aktive - wer eines
    /// anlegt, will darauf.
    func addBed() {
        guard let core else { return }
        if let neu = try? core.addBed() {
            try? core.selectBed(neu)
            selectedId = nil
        }
        sceneRevision += 1
        refresh()
    }

    func removeBed(_ index: Int) {
        try? core?.removeBed(index)
        selectedId = nil
        sceneRevision += 1
        refresh()
    }

    func selectBed(_ index: Int) {
        try? core?.selectBed(index)
        // Ein anderes Bett heisst andere Objekte und eine andere
        // Ansicht - die Auswahl von vorhin gibt es dort nicht.
        selectedId = nil
        sceneRevision += 1
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
               instance: Int,
               volume: Int,
               facet: Int,
               hit: (Float, Float, Float),
               previous: (Float, Float, Float)?,
               options: PsmCore.PaintOptions) {
        try? core?.paint(id, instance: instance,
                         volume: volume, facet: facet,
                         hit: hit, previous: previous,
                         options: options)
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

    func layOnFacet(_ id: Int32,
                    instance: Int = 0,
                    volume: Int,
                    facet: Int) {
        try? core?.layOnFacet(id, instance: instance,
                              volume: volume, facet: facet)
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

    /// Legt das Objekt auf seine groesste ebene Flaeche.
    func layFlat(_ id: Int32) {
        try? core?.layFlatAuto(id)
        sceneRevision += 1
        refresh()
    }

    /// Dreht die angetippte Flaeche nach unten.
    func layOnFace(_ id: Int32,
                   instance: Int = 0,
                   volume: Int,
                   facet: Int) {
        try? core?.layOnFacet(id, instance: instance,
                              volume: volume, facet: facet)
        sceneRevision += 1
        refresh()
    }

    /// Reduziert die Dreieckszahl. Gibt vorher und nachher zurueck.
    @discardableResult
    func simplify(_ id: Int32, ratio: Float) -> (before: Int, after: Int)? {
        guard let ergebnis = try? core?.simplify(id, ratio: ratio) else { return nil }
        sceneRevision += 1
        refresh()
        return ergebnis
    }

    /// Zerlegt getrennte Koerper in einzelne Volumen.
    @discardableResult
    func splitVolumes(_ id: Int32) -> Int {
        let anzahl = (try? core?.splitVolumes(id)) ?? 0
        sceneRevision += 1
        refresh()
        return anzahl ?? 0
    }

    /// Der Dateiname, den PrusaSlicer vergaebe.
    func suggestedGcodeName() -> String {
        core?.suggestedGcodeName() ?? "psmobile.gcode"
    }

    func volumeCount(_ id: Int32) -> Int { core?.volumeCount(id) ?? 0 }

    func volumeInfo(_ id: Int32, at index: Int) -> PsmCore.VolumeInfo? {
        core?.volumeInfo(id, at: index)
    }

    /// Legt einen Grundkoerper als Teil an. Falsch heisst: hat nicht
    /// geklappt - der Grund steht im Protokoll.
    @discardableResult
    func addPrimitiveVolume(_ id: Int32,
                            type: PsmCore.VolumeType,
                            shape: PsmCore.PrimitiveShape,
                            size: Float) -> Bool {
        guard let core else { return false }
        do {
            try core.addPrimitiveVolume(id, type: type, shape: shape,
                                        sizeX: size, sizeY: size, sizeZ: size)
        } catch {
            projectNotice = error.localizedDescription
            return false
        }
        sceneRevision += 1
        refresh()
        return true
    }

    func removeVolume(_ id: Int32, at index: Int) {
        try? core?.removeVolume(id, at: index)
        sceneRevision += 1
        refresh()
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

    /// Verbrauch je Extruder des letzten Ergebnisses.
    func extruderUsage() -> [PsmCore.ExtruderUsage] {
        core?.extruderUsage() ?? []
    }

    /// Ob ein Hinsehen ohne neues Rechnen genuegt.
    var sliceResultIsCurrent: Bool { core?.sliceResultIsCurrent ?? false }

    /// Keine zwischengespeicherte Swift-Kopie: so kann eine neue
    /// Designrevision nie versehentlich den alten Preview-Wert zeigen.
    func previewSnapshot() -> PsmCore.PreviewSnapshot? {
        guard sliceResultIsCurrent else { return nil }
        return core?.previewSnapshot()
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
        // Hier kann der Kern die Frage beantworten: das Ergebnis ist
        // gerade erst entstanden. Beim Projektsichern konnte er es
        // nicht - psm_gcode_suggested_name verlangt ein aktuelles
        // Schnittergebnis.
        let name = Self.dateiname(suggestedGcodeName())
            ?? SliceSummary.shared.fileName(project: objects.first?.name ?? "")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try? FileManager.default.removeItem(at: url)
        do {
            try core.exportGcode(to: url.path)
            gcodeURL = url
        } catch {
            gcodeURL = nil
        }
    }

    /// Ein Vorschlag des Kerns, auf einen Dateinamen gestutzt.
    ///
    /// Was aus `output_filename_format` kommt, ist eine Vorlage aus dem
    /// Druckprofil und darf alles enthalten, was ein Dateiname nicht
    /// vertraegt - Leerzeichen, Klammern, im schlimmsten Fall einen
    /// Schraegstrich. Die Endung bleibt aber stehen: ein Drucker
    /// erkennt an ihr, ob er ASCII oder BGCode bekommt.
    ///
    /// Nil, wenn nichts Brauchbares uebrig bleibt - dann greift der
    /// Name aus dem gemeinsamen Modul.
    private static func dateiname(_ vorschlag: String) -> String? {
        let endung = (vorschlag as NSString).pathExtension.lowercased()
        guard endung == "gcode" || endung == "bgcode" else { return nil }
        let stamm = String((vorschlag as NSString).deletingPathExtension.map {
            $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" || $0 == "." ? $0 : "_"
        })
        guard !stamm.isEmpty else { return nil }
        return stamm + "." + endung
    }

    /// Zurueck in den Ruhezustand - das Blatt ist weg, das Ergebnis
    /// bleibt es aber nicht: ein neuer Slice ueberschreibt es ohnehin.
    func dismissProgress() {
        progress = .idle
    }

    /// Was fehlt, bevor geschnitten werden kann. Die Beurteilung steht im
    /// gemeinsamen Modul, damit beide Apps dieselben Gruende nennen.
    /// Wo ein Objekt relativ zum Druckraum liegt.
    func bedState(_ id: Int32) -> PsmCore.BedState {
        core?.bedState(id) ?? .unknown
    }

    /// Die Objekte, die so nicht gedruckt werden koennen.
    ///
    /// "Outside" und "Below" zaehlen mit: PrusaSlicer laesst sie beim
    /// Drucken zwar stillschweigend weg, aber jemand, der ein Objekt
    /// neben das Bett gelegt hat, will es drucken - und bekaeme sonst
    /// einen G-Code, in dem es fehlt, ohne dass es jemand gesagt haette.
    var objectsOffBed: [PsmCore.ObjectInfo] {
        objects.filter { $0.outsideBed }
    }

    var sliceBlockers: [String] {
        var gruende = SliceSummary.shared.blockers(
            objects: Int32(objects.count),
            printer: selectedPreset(for: "printer") ?? "",
            filament: selectedPreset(for: "filament") ?? "",
            print: selectedPreset(for: "print") ?? ""
        )
        let daneben = objectsOffBed
        if !daneben.isEmpty {
            let namen = daneben.map { $0.name }.joined(separator: ", ")
            gruende.append(Bilingual(
                english: "Outside the print area: " + namen,
                german: "Ausserhalb des Druckbereichs: " + namen).text)
        }
        return gruende
    }

    private func finishSlice() {
        sliceTask = nil
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}
