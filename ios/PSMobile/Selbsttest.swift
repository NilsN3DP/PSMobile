import Foundation
import UIKit
import PSMShared

/// Der Selbsttest: alles, was echtes Gerät braucht, auf einen Knopf.
///
/// Der Simulator beantwortet die wichtigsten Fragen nicht. Er hat kein
/// Jetsam-Limit, keine Wärmegrenze, eine andere GPU und ein
/// Dateisystem, das sich anders verhält als das des Geräts — genau
/// daran ist heute eine Stunde draufgegangen. Was auf dem iPad gilt,
/// weiß nur das iPad.
///
/// Deshalb läuft die Prüfung *in* der App: laden, schneiden, sichern,
/// wieder laden, bemalen, mehrfarbig rechnen, und zum Schluss unter
/// Last. Am Ende steht ein Bericht, den man weitergeben kann.
///
/// Die Prüfung bekommt eine **eigene Sitzung** in einem eigenen Ordner.
/// Ein Lasttest mit zwei Dutzend Körpern darf nicht das kaputtmachen,
/// woran gerade jemand arbeitet.
@MainActor
final class Selbsttest: ObservableObject {

    enum Ausgang: String {
        case ok, fehler, warnung, angabe

        var zeichen: String {
            switch self {
            case .ok:      return "✓"
            case .fehler:  return "✗"
            case .warnung: return "!"
            case .angabe:  return "·"
            }
        }
    }

    struct Schritt: Identifiable {
        let id = UUID()
        let name: String
        let ausgang: Ausgang
        let detail: String
        let sekunden: Double
    }

    @Published private(set) var schritte: [Schritt] = []
    @Published private(set) var laeuft = false
    @Published private(set) var aktuell = ""
    @Published private(set) var berichtURL: URL?

    private var aufgabe: Task<Void, Never>?

    /// Wie viele Koerper der Lasttest schneidet.
    ///
    /// 25 sind die Frage an ein Geraet: packst du das? Im Simulator ist
    /// es nur Wartezeit - dort soll der Test wissen, ob der Ablauf
    /// steht, nicht wie schnell die Maschine ist.
    private nonisolated static var lastKoerper: Int {
        ProcessInfo.processInfo.arguments.contains("-psm-selbsttest-kurz") ? 6 : 25
    }

    /// Die Datei, in die waehrend des Laufs mitgeschrieben wird.
    ///
    /// Nicht erst am Ende: stuerzt die App mitten in einem Schritt ab,
    /// gibt es kein Ende. Dann steht der Anfang des Schritts ohne
    /// Ergebnis in der Datei, und genau das ist die gesuchte Stelle.
    private nonisolated static func berichtsdatei() -> URL {
        let basis = FileManager.default.urls(for: .documentDirectory,
                                             in: .userDomainMask)[0]
        let ordner = basis.appendingPathComponent("Selbsttest", isDirectory: true)
        try? FileManager.default.createDirectory(at: ordner,
                                                 withIntermediateDirectories: true)
        let formatierer = DateFormatter()
        formatierer.dateFormat = "yyyy-MM-dd-HHmm"
        return ordner.appendingPathComponent(
            "psmobile-selbsttest-" + formatierer.string(from: Date()) + ".md")
    }

    /// Eine Zeile anhaengen und sofort schliessen.
    ///
    /// Geschlossen wird nach jeder Zeile, damit sie auch dann auf der
    /// Platte steht, wenn der naechste Schritt das Programm beendet.
    private nonisolated static func anhaengen(_ datei: URL, _ zeile: String) {
        let daten = Data((zeile + "\n").utf8)
        if let griff = try? FileHandle(forWritingTo: datei) {
            defer { try? griff.close() }
            griff.seekToEndOfFile()
            griff.write(daten)
        } else {
            try? daten.write(to: datei)
        }
    }

    var fehlerZahl: Int { schritte.filter { $0.ausgang == .fehler }.count }
    var warnungZahl: Int { schritte.filter { $0.ausgang == .warnung }.count }

    func starten() {
        guard !laeuft else { return }
        schritte = []
        berichtURL = nil
        laeuft = true
        PsmLog.clear()
        aufgabe = Task.detached(priority: .userInitiated) { [weak self] in
            await self?.durchlauf()
        }
    }

    func abbrechen() {
        aufgabe?.cancel()
        aufgabe = nil
        laeuft = false
    }

    // MARK: - Der Durchlauf

    private nonisolated func durchlauf() async {
        var kern: PsmCore?
        var wuerfelId: Int32 = 0
        var projektPfad = ""

        let datei = Self.berichtsdatei()
        Self.anhaengen(datei, "# PSMobile Selbsttest")
        Self.anhaengen(datei, "")
        Self.anhaengen(datei, Self.umgebung())
        Self.anhaengen(datei, "")

        /// Einen Schritt ausfuehren, messen und melden.
        ///
        /// Wirft der Block, wird daraus ein Fehlschlag mit dem Grund -
        /// und mit der letzten Zeile aus dem Kernprotokoll. Seit heute
        /// steht dort auch, was PrusaSlicer selbst zu sagen hatte.
        @Sendable func schritt(_ name: String,
                               _ block: () async throws -> (Ausgang, String)) async {
            await MainActor.run { self.aktuell = name }
            // Vor dem Schritt, nicht danach: was hier steht und kein
            // Ergebnis bekommt, ist die Stelle, an der es geknallt hat.
            Self.anhaengen(datei, "→ " + name)
            let t0 = Date()
            var ausgang = Ausgang.ok
            var detail = ""
            do {
                (ausgang, detail) = try await block()
            } catch {
                ausgang = .fehler
                detail = error.localizedDescription
                if let letzte = await MainActor.run(body: { PsmLog.recent.last }) {
                    detail += " — " + letzte
                }
            }
            let dauer = Date().timeIntervalSince(t0)
            Self.anhaengen(datei, String(format: "%@ %@ (%.1f s) %@",
                                         ausgang.zeichen, name, dauer, detail))
            await MainActor.run {
                self.schritte.append(Schritt(name: name, ausgang: ausgang,
                                             detail: detail, sekunden: dauer))
            }
        }

        await schritt("Gerät und App") { (.angabe, Self.umgebung()) }

        await schritt("Groß- und Kleinschreibung im Dateisystem") {
            (.angabe, try Self.schreibweisePruefen())
        }

        await schritt("Kern starten") {
            let ordner = FileManager.default.temporaryDirectory
                .appendingPathComponent("psm-selbsttest", isDirectory: true)
            try? FileManager.default.removeItem(at: ordner)
            try FileManager.default.createDirectory(at: ordner, withIntermediateDirectories: true)
            guard let res = Bundle.main.resourceURL?.appendingPathComponent("psresources") else {
                throw Fehler.text("Ressourcen fehlen im App-Bundle")
            }
            kern = try PsmCore(dataDir: ordner.path, resourceDir: res.path)
            return (.ok, "Kern " + PsmCore.coreVersion)
        }

        await schritt("Druckerprofil einrichten") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            try k.installPrinters(["PrusaResearch:MK4S:0.4"])
            let drucker = k.presetCount(.printer)
            let druck = k.presetCount(.print)
            let filament = k.presetCount(.filament)
            guard drucker > 0, druck > 0, filament > 0 else {
                throw Fehler.text("Profile leer: \(drucker)/\(druck)/\(filament)")
            }
            return (.ok, "\(drucker) Drucker · \(druck) Druckprofile · \(filament) Filamente")
        }

        await schritt("Modell laden") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            let datei = try Testkoerper.wuerfelDatei()
            let ids = try k.loadModel(path: datei.path)
            guard let erste = ids.first else { throw Fehler.text("Kein Objekt entstanden") }
            wuerfelId = erste
            let info = k.objectInfo(erste)
            return (.ok, "\(info?.triangles ?? 0) Dreiecke · "
                    + String(format: "%.0f×%.0f×%.0f mm",
                             info?.sizeMm.x ?? 0, info?.sizeMm.y ?? 0, info?.sizeMm.z ?? 0))
        }

        await schritt("Liegt auf dem Bett") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            let zustand = k.bedState(wuerfelId)
            guard zustand == .inside else {
                throw Fehler.text("Bettzustand: \(zustand)")
            }
            return (.ok, "vollständig innerhalb")
        }

        await schritt("Schneiden") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            let t0 = Date()
            try k.startSlice { _, _ in false }
            let stand = k.awaitSlice()
            guard stand == .done else { throw Fehler.text("Ergebnis: \(stand) — " + k.lastError) }
            let dauer = Date().timeIntervalSince(t0)
            guard let st = k.sliceStats(), st.printTimeSeconds > 0, st.filamentGrams > 0 else {
                throw Fehler.text("Statistik leer")
            }
            return (.ok, String(format: "%.1f s · Druckzeit %.0f min · %.1f g",
                                dauer, st.printTimeSeconds / 60, st.filamentGrams))
        }

        await schritt("G-Code schreiben") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("psm-selbsttest.gcode")
            try? FileManager.default.removeItem(at: url)
            try k.exportGcode(to: url.path)
            let groesse = (try FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
            guard groesse > 1024 else { throw Fehler.text("nur \(groesse) Bytes") }
            let anfang = try String(contentsOf: url, encoding: .utf8).prefix(4096)
            guard anfang.contains("G1") else { throw Fehler.text("keine Bewegungsbefehle im Kopf") }
            return (.ok, String(format: "%.1f MB", Double(groesse) / 1_048_576))
        }

        await schritt("Projekt sichern und wieder laden") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("psm-selbsttest.3mf")
            try? FileManager.default.removeItem(at: url)
            try k.saveProject(path: url.path)
            projektPfad = url.path
            let groesse = (try FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
            let info = try k.loadProject(path: url.path)
            guard info.objectCount == 1 else {
                throw Fehler.text("\(info.objectCount) Objekte statt einem")
            }
            wuerfelId = k.listObjects().first ?? wuerfelId
            return (.ok, String(format: "%.0f kB · 1 Objekt zurück", Double(groesse) / 1024))
        }

        await schritt("Zurücknehmen und Wiederholen") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            _ = try k.duplicate(wuerfelId)
            let nachKlon = k.listObjects().count
            try k.undo()
            let nachZurueck = k.listObjects().count
            try k.redo()
            let nachVor = k.listObjects().count
            guard nachKlon == 2, nachZurueck == 1, nachVor == 2 else {
                throw Fehler.text("Zählung \(nachKlon)/\(nachZurueck)/\(nachVor) statt 2/1/2")
            }
            // Den Klon wieder weg - die naechsten Schritte rechnen mit einem.
            if let zweiter = k.listObjects().last, zweiter != wuerfelId {
                try k.removeObject(zweiter)
            }
            return (.ok, "2 → 1 → 2")
        }

        await schritt("Teil anlegen und entfernen") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            _ = try k.addPrimitiveVolume(wuerfelId, type: .negative, shape: .cylinder,
                                         sizeX: 8, sizeY: 8, sizeZ: 30)
            let mit = k.volumeCount(wuerfelId)
            try k.removeVolume(wuerfelId, at: mit - 1)
            let ohne = k.volumeCount(wuerfelId)
            guard mit == 2, ohne == 1 else {
                throw Fehler.text("Teile \(mit)/\(ohne) statt 2/1")
            }
            return (.ok, "Aussparung angelegt und entfernt")
        }

        await schritt("Bemalen") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            try k.paint(wuerfelId, volume: 0, facet: 0, tool: .support,
                        state: 1, radiusMm: 5)
            let anzahl = k.paintCount(wuerfelId, tool: .support)
            guard anzahl > 0 else { throw Fehler.text("keine Facette markiert") }
            try k.clearPaint(wuerfelId, tool: .support)
            return (.ok, "\(anzahl) Facetten markiert")
        }

        await schritt("Mehrfarbig schneiden") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            // Fuenf Duesen wie bei der MMU, dann die halbe Oberflaeche
            // dem zweiten Extruder geben.
            try k.setConfig("nozzle_diameter",
                            Array(repeating: "0.4", count: 5).joined(separator: ","))
            try k.paint(wuerfelId, volume: 0, facet: 0, tool: .mmu, state: 2, radiusMm: 30)
            try k.startSlice { _, _ in false }
            let stand = k.awaitSlice()
            guard stand == .done else { throw Fehler.text("Ergebnis: \(stand) — " + k.lastError) }
            let verbrauch = k.extruderUsage().filter { $0.totalMm3 > 0 }
            guard verbrauch.count >= 2 else {
                throw Fehler.text("nur \(verbrauch.count) Extruder im Verbrauch")
            }
            let zeilen = verbrauch
                .map { String(format: "T%d %.0f mm³", $0.extruder + 1, $0.totalMm3) }
            return (.ok, zeilen.joined(separator: " · "))
        }

        await schritt("Lasttest: \(Self.lastKoerper) Körper") {
            guard let k = kern else { throw Fehler.text("Kein Kern") }
            try k.clearPaint(wuerfelId, tool: .mmu)
            try k.setConfig("nozzle_diameter", "0.4")
            // Nicht blind vervielfaeltigen. Das Geraet hat eine Grenze,
            // die der Simulator nicht kennt, und der Kern kann schaetzen,
            // was ein Schnitt braucht. Also nach jedem Koerper
            // nachrechnen und aufhoeren, bevor es eng wird - lieber ein
            // Lasttest mit acht Koerpern als eine App, die weggeht.
            let frei = PsmCore.availableMemory
            let obergrenze = frei.map { Double($0) * 0.5 }
            var abgebrochen = false
            for _ in 1..<Self.lastKoerper {
                _ = try k.duplicate(wuerfelId)
                if let grenze = obergrenze,
                   Double(k.estimatedSliceMemory) > grenze {
                    abgebrochen = true
                    break
                }
            }
            try k.arrange(gapMm: 4)
            let anzahl = k.listObjects().count
            let geschaetzt = k.estimatedSliceMemory

            let t0 = Date()
            try k.startSlice { _, _ in false }
            let stand = k.awaitSlice()
            let dauer = Date().timeIntervalSince(t0)
            guard stand == .done else { throw Fehler.text("Ergebnis: \(stand) — " + k.lastError) }

            var text = String(format: "%d Körper · %.1f s · geschätzt %.0f MB",
                              anzahl, dauer, Double(geschaetzt) / 1_048_576)
            if let frei { text += String(format: " · frei %.0f MB", Double(frei) / 1_048_576) }
            if abgebrochen { text += " · wegen Speichergrenze früher gestoppt" }
            // Zwei Minuten sind die Grenze, ab der niemand mehr wartet,
            // sondern die App wegwischt.
            return (dauer > 120 ? .warnung : .ok, text)
        }

        _ = projektPfad
        kern = nil

        let bericht = await MainActor.run { self.berichtText() }
        Self.anhaengen(datei, "")
        Self.anhaengen(datei, bericht)
        await MainActor.run {
            self.berichtURL = datei
            self.aktuell = ""
            self.laeuft = false
        }
    }

    // MARK: - Bericht

    /// Markdown, weil man es lesen kann, ohne es zu öffnen.
    func berichtText() -> String {
        var z: [String] = []
        z.append("# PSMobile Selbsttest")
        z.append("")
        let formatierer = DateFormatter()
        formatierer.dateFormat = "yyyy-MM-dd HH:mm"
        z.append(formatierer.string(from: Date()))
        z.append("")
        z.append("\(schritte.filter { $0.ausgang == .ok }.count) bestanden · "
                 + "\(fehlerZahl) fehlgeschlagen · \(warnungZahl) auffällig")
        z.append("")
        z.append("| | Prüfung | Dauer | Ergebnis |")
        z.append("|---|---|---|---|")
        for s in schritte {
            let detail = s.detail.replacingOccurrences(of: "\n", with: " · ")
                .replacingOccurrences(of: "|", with: "/")
            z.append("| \(s.ausgang.zeichen) | \(s.name) | "
                     + String(format: "%.1f s", s.sekunden) + " | \(detail) |")
        }
        z.append("")
        let protokoll = PsmLog.recent
        if !protokoll.isEmpty {
            z.append("## Warnungen und Fehler aus dem Kern")
            z.append("")
            z.append("```")
            z.append(contentsOf: protokoll.suffix(20))
            z.append("```")
        }
        return z.joined(separator: "\n") + "\n"
    }

    // MARK: - Angaben

    private nonisolated static func umgebung() -> String {
        var teile: [String] = []
        teile.append(geraetekennung())
        teile.append(UIDevice.current.systemName + " " + UIDevice.current.systemVersion)
        if let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
           let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String {
            teile.append("App \(v) (\(b))")
        }
        teile.append("ABI \(PSM_ABI_VERSION)")
        teile.append(String(format: "%.1f GB RAM",
                            Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824))
        if let frei = PsmCore.availableMemory {
            teile.append(String(format: "%.0f MB nutzbar", Double(frei) / 1_048_576))
        } else {
            teile.append("keine Speichergrenze (Simulator)")
        }
        teile.append("\(ProcessInfo.processInfo.processorCount) Kerne")
        return teile.joined(separator: " · ")
    }

    /// Das Kürzel, das ein iPad Pro von einem iPad Air unterscheidet -
    /// `UIDevice.model` sagt zu beiden nur „iPad".
    private nonisolated static func geraetekennung() -> String {
        var system = utsname()
        uname(&system)
        let kennung = withUnsafeBytes(of: &system.machine) { roh -> String in
            let bytes = roh.bindMemory(to: CChar.self)
            return String(cString: bytes.baseAddress!)
        }
        return kennung.isEmpty ? "unbekannt" : kennung
    }

    /// Unterscheidet das Dateisystem Groß- und Kleinschreibung?
    ///
    /// Genau daran ist heute eine Stunde draufgegangen: der Simulator
    /// liegt auf einem Dateisystem, das sie ignoriert, und lehnt deshalb
    /// zwei Dateien ab, die sich nur darin unterscheiden. Auf dem Gerät
    /// ist es andersherum. Wer Projekte benennt, muss das wissen.
    private nonisolated static func schreibweisePruefen() throws -> String {
        let ordner = FileManager.default.temporaryDirectory
            .appendingPathComponent("psm-schreibweise", isDirectory: true)
        try? FileManager.default.removeItem(at: ordner)
        try FileManager.default.createDirectory(at: ordner, withIntermediateDirectories: true)
        let gross = ordner.appendingPathComponent("PSMobileProbe.txt")
        try Data("x".utf8).write(to: gross)
        let klein = ordner.appendingPathComponent("psmobileprobe.txt")
        let gefunden = FileManager.default.fileExists(atPath: klein.path)
        let zweiteAngelegt = FileManager.default.createFile(atPath: klein.path, contents: Data())
        try? FileManager.default.removeItem(at: ordner)
        if gefunden {
            return "wird ignoriert — zwei Namen mit gleicher Schreibweise sind dieselbe Datei"
        }
        return zweiteAngelegt
            ? "wird unterschieden — Datei und DATEI sind zwei Dateien"
            : "wird unterschieden, eine zweite Schreibweise ließ sich aber nicht anlegen"
    }

    enum Fehler: LocalizedError {
        case text(String)
        var errorDescription: String? {
            if case .text(let t) = self { return t }
            return nil
        }
    }
}
