import SwiftUI
import PSMShared

/// Eine Bettauswahl für beide Arbeitsmodi.
///
/// Auf dem iPad liegen die Karten in einem umbrechenden Raster. Auf dem
/// iPhone bleibt das aktive Bett direkt erreichbar; die vollständige
/// Liste öffnet sich als Blatt. Horizontal verborgenes Scrollen gibt es
/// in keiner Größenklasse.
struct BedSelector: View {

    @ObservedObject var model: SlicerModel
    var onArrange: () -> Void
    var onOpenSelection: () -> Void

    @Environment(\.psScale) private var ps
    @State private var umzubenennen: Int?
    @State private var name = ""

    private var schmal: Bool { ps.windowSize.width < 760 }
    /// `BedStripContract` is exported by the real PSMShared Kotlin/Native
    /// framework. The model/core still owns mutations; this contract owns the
    /// platform-neutral presentation capabilities.
    private var strip: BedStripState {
        BedStripContract.shared.state(
            beds: model.beds.map { bed in
                BedInput(id: Int32(bed.index), name: model.bedLabel(bed.index),
                         locked: bed.locked, objectCount: Int32(bed.objectCount),
                         instanceCount: Int32(bed.instanceCount))
            },
            activeIndex: Int32(BedModeState.activeIndex(
                model.beds.map { BedActivity(index: $0.index, active: $0.active) })))
    }
    private var aktiveItem: BedStripItem? { strip.items.first { $0.active } }
    private func item(for bed: PsmCore.Bed) -> BedStripItem? {
        strip.items.first { $0.id == Int32(bed.index) }
    }

    var body: some View {
        ZStack {
            if schmal {
                kompakteAuswahl
            } else {
                rasterAuswahl
            }
            PSMarke(name: "bed.selector")
        }
        .background(PrusaColors.background)
        .alert(st("Rename bed", "Bett umbenennen"),
               isPresented: Binding(
                get: { umzubenennen != nil },
                set: { if !$0 { umzubenennen = nil } })) {
            TextField(st("Name", "Name"), text: $name)
            Button(st("Cancel", "Abbrechen"), role: .cancel) {
                umzubenennen = nil
            }
            Button(st("Apply", "Übernehmen")) {
                if let index = umzubenennen {
                    model.renameBed(index, to: name)
                }
                umzubenennen = nil
            }
        } message: {
            Text(st("An empty name restores the bed number.",
                    "Ein leerer Name stellt die Bettnummer wieder her."))
        }
    }

    private var kompakteAuswahl: some View {
        HStack(spacing: ps.pt(8)) {
            Button(action: onOpenSelection) {
                HStack(spacing: ps.pt(7)) {
                    Image(systemName: aktiveItem?.locked == true
                          ? "lock.fill" : "square.stack.3d.up")
                    VStack(alignment: .leading, spacing: 1) {
                        Text(aktiveItem?.name ?? st("Bed", "Bett"))
                            .font(.system(size: ps.font(13), weight: .semibold))
                        Text(st("\(aktiveItem?.objectCount ?? 0) objects",
                                "\(aktiveItem?.objectCount ?? 0) Objekte"))
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down")
                }
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(12))
                .frame(maxWidth: .infinity, minHeight: ps.touch())
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(7)))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("bed.selector.active")
            if let activeBed = model.beds.first(where: \.active) {
                Button {
                    name = activeBed.name
                    umzubenennen = activeBed.index
                } label: {
                    Image(systemName: "pencil")
                        .frame(width: ps.touch(36), height: ps.touch())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bed.rename.\(activeBed.index)")
                Button { model.toggleBedLock(activeBed.index) } label: {
                    Image(systemName: activeBed.locked ? "lock.fill" : "lock.open")
                        .frame(width: ps.touch(36), height: ps.touch())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bed.lock.\(activeBed.index)")
            }
        }
        .padding(.horizontal, ps.pt(8))
        .padding(.vertical, ps.pt(4))
    }

    /// Eine waagerecht scrollende Reihe schmaler Kapseln, wie vor dem
    /// Umbau auf das Kartenraster. Das Raster nahm eine eigene Zeile mit
    /// Ueberschrift und 64pt hohen Karten - hier reicht eine Zeile in
    /// Werkzeugleistenhoehe, und bei einem Bett faellt sie ganz weg.
    private var rasterAuswahl: some View {
        HStack(spacing: ps.pt(6)) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: ps.pt(6)) {
                    ForEach(model.beds, id: \.index) { bett in
                        if let item = item(for: bett) {
                            bettKapsel(bett, item: item)
                        }
                    }
                    Button { model.addBed() } label: {
                        Image(systemName: "plus")
                            .font(.system(size: ps.font(14), weight: .semibold))
                            .foregroundStyle(PrusaColors.orange)
                            .frame(width: ps.touch(40), height: ps.touch(38))
                            .background(PrusaColors.panelRaised)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!strip.canAdd)
                    .accessibilityIdentifier("bed.add")
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, ps.pt(8))
        .padding(.vertical, ps.pt(4))
    }

    /// Name, Objektzahl und Schlossknopf in einer Kapsel. Umbenennen und
    /// Entfernen sind selten und liegen deshalb im Kontextmenue (langer
    /// Druck) statt als eigene, immer sichtbare Knoepfe - die haben zuvor
    /// die Karte breiter gemacht, als der Name Platz hatte.
    private func bettKapsel(_ bett: PsmCore.Bed, item: BedStripItem) -> some View {
        // Dezenter als das volle Orange der uebrigen Aktionsknoepfe: das
        // aktive Bett ist ein Zustand, den man staendig im Blick hat,
        // kein Befehl, den man antippt - er soll nicht um Aufmerksamkeit
        // mit Arrange und den Werkzeugen konkurrieren.
        HStack(spacing: ps.pt(2)) {
            Button {
                model.selectBed(bett.index)
            } label: {
                HStack(spacing: ps.pt(4)) {
                    Text(item.name)
                        .font(.system(size: ps.font(11), weight: .medium))
                        .lineLimit(1)
                    Text("\(item.objectCount)")
                        .font(.system(size: ps.font(9)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .foregroundStyle(item.active
                                 ? PrusaColors.orange : PrusaColors.textPrimary)
                .padding(.leading, ps.pt(10))
                .frame(minHeight: ps.touch(32))
            }
            .buttonStyle(.plain)
            .disabled(!item.canSelect)
            .accessibilityIdentifier("bed.card.\(bett.index)")
            .accessibilityValue(item.active ? "active" : "inactive")

            Button {
                model.toggleBedLock(bett.index)
            } label: {
                Image(systemName: item.locked ? "lock.fill" : "lock.open")
                    .font(.system(size: ps.font(9)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(width: ps.touch(26), height: ps.touch(32))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!item.canToggleLock)
            .accessibilityIdentifier("bed.lock.\(bett.index)")

            // Direkt sichtbar statt nur im Kontextmenue (langer Druck) -
            // auf dem iPad findet den kaum jemand von selbst. Nur bei
            // einem leeren Bett: ein volles darf nicht so verschwinden.
            if item.canRemove {
                Button {
                    model.removeBed(bett.index)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: ps.font(8), weight: .semibold))
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.touch(24), height: ps.touch(32))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.trailing, ps.pt(3))
                .accessibilityIdentifier("bed.remove.\(bett.index)")
            } else {
                Spacer().frame(width: ps.pt(3))
            }
        }
        .background(PrusaColors.panelRaised)
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(6))
                .stroke(item.active ? PrusaColors.orange.opacity(0.6) : Color.clear,
                        lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        .contextMenu {
            if item.canRename {
                Button {
                    name = item.name
                    umzubenennen = bett.index
                } label: {
                    Label(st("Rename", "Umbenennen"), systemImage: "pencil")
                }
            }
            if item.canRemove {
                Button(role: .destructive) {
                    model.removeBed(bett.index)
                } label: {
                    Label(st("Remove", "Entfernen"), systemImage: "trash")
                }
            }
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Das vom jeweiligen Arbeitsmodus präsentierte iPhone-Blatt.
///
/// Die Präsentation gehört dem Host, weil der Advanced Mode mehrere
/// eigene Datei- und Einstellungsblätter besitzt. Ein am Kind befestigtes
/// Sheet wurde dort von SwiftUI verworfen, obwohl derselbe Selector im
/// Simple Mode funktionierte.
struct BedSelectionSheet: View {

    @ObservedObject var model: SlicerModel
    @Binding var isPresented: Bool

    @Environment(\.psScale) private var ps
    @State private var umzubenennen: Int?
    @State private var name = ""

    private var strip: BedStripState {
        BedStripContract.shared.state(
            beds: model.beds.map { bed in
                BedInput(id: Int32(bed.index), name: model.bedLabel(bed.index),
                         locked: bed.locked, objectCount: Int32(bed.objectCount),
                         instanceCount: Int32(bed.instanceCount))
            },
            activeIndex: Int32(BedModeState.activeIndex(
                model.beds.map { BedActivity(index: $0.index, active: $0.active) })))
    }
    private func item(for bed: PsmCore.Bed) -> BedStripItem? {
        strip.items.first { $0.id == Int32(bed.index) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ps.pt(10)) {
                    ForEach(model.beds, id: \.index) { bett in
                        HStack(spacing: 0) {
                            Button {
                                model.selectBed(bett.index)
                                isPresented = false
                            } label: {
                                HStack {
                                    Image(systemName: bett.locked
                                          ? "lock.fill" : "square.stack.3d.up")
                                    VStack(alignment: .leading) {
                                        Text(model.bedLabel(bett.index))
                                            .fontWeight(.semibold)
                                        Text(st("\(bett.objectCount) objects",
                                                "\(bett.objectCount) Objekte"))
                                            .font(.caption)
                                            .foregroundStyle(PrusaColors.textMuted)
                                    }
                                    Spacer(minLength: ps.pt(90))
                                }
                                .padding(.horizontal, ps.pt(12))
                                .frame(maxWidth: .infinity,
                                       minHeight: ps.touch(64),
                                       alignment: .leading)
                                .background(bett.active
                                            ? PrusaColors.orange
                                            : PrusaColors.panelRaised)
                                .foregroundStyle(bett.active
                                                 ? PrusaColors.background
                                                 : PrusaColors.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("bed.card.\(bett.index)")
                            .accessibilityValue(item(for: bett)?.active == true ? "active" : "inactive")

                            HStack(spacing: 0) {
                                miniKnopf("pencil", id: "bed.rename.\(bett.index)") {
                                    name = bett.name
                                    umzubenennen = bett.index
                                }
                                miniKnopf(bett.locked ? "lock.open" : "lock",
                                          id: "bed.lock.\(bett.index)") {
                                    model.toggleBedLock(bett.index)
                                }
                                if item(for: bett)?.canRemove == true {
                                    miniKnopf("trash", id: "bed.remove.\(bett.index)") {
                                        model.removeBed(bett.index)
                                    }
                                }
                            }
                            .padding(ps.pt(4))
                            .fixedSize()
                        }
                    }

                    Button {
                        model.addBed()
                        isPresented = false
                    } label: {
                        Label(st("Add bed", "Bett hinzufügen"), systemImage: "plus")
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: ps.touch())
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(PrusaColors.orange)
                    .disabled(!strip.canAdd)
                    .accessibilityIdentifier("bed.add")
                }
                .padding()
            }
            .background(PrusaColors.background)
            .navigationTitle(st("Beds", "Betten"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(st("Done", "Fertig")) { isPresented = false }
                        .accessibilityIdentifier("bed.selector.close")
                }
            }
            .overlay { PSMarke(name: "bed.selector.sheet") }
        }
        .presentationDetents([.medium, .large])
        .alert(st("Rename bed", "Bett umbenennen"),
               isPresented: Binding(
                get: { umzubenennen != nil },
                set: { if !$0 { umzubenennen = nil } })) {
            TextField(st("Name", "Name"), text: $name)
            Button(st("Cancel", "Abbrechen"), role: .cancel) {
                umzubenennen = nil
            }
            Button(st("Apply", "Übernehmen")) {
                if let index = umzubenennen {
                    model.renameBed(index, to: name)
                }
                umzubenennen = nil
            }
        }
    }

    private func miniKnopf(_ symbol: String, id: String,
                           aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Image(systemName: symbol)
                .frame(width: ps.touch(34), height: ps.touch(34))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(PrusaColors.textPrimary)
        .accessibilityIdentifier(id)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Konfliktfreies Bottom-Sheet für Hosts mit mehreren Systemblättern.
struct BedSelectionOverlay: View {

    @ObservedObject var model: SlicerModel
    @Binding var isPresented: Bool
    @Environment(\.psScale) private var ps

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { isPresented = false }
            BedSelectionSheet(model: model, isPresented: $isPresented)
                .frame(maxHeight: min(ps.windowSize.height * 0.82, ps.pt(620)))
                .background(PrusaColors.background)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(14)))
                .shadow(radius: ps.pt(20))
        }
        .ignoresSafeArea(edges: .bottom)
        .zIndex(100)
    }
}

/// Ziel, Abstand und Ergebnis eines Arrange-Laufs.
struct ArrangePanel: View {

    @ObservedObject var model: SlicerModel
    @Binding var isPresented: Bool

    @Environment(\.psScale) private var ps
    @State private var ziel = 0
    @State private var abstand = 6.0
    @State private var ergebnis = ""
    /// Alle Betten statt eines einzelnen Zielbetts - der haeufige
    /// Wunsch bei mehreren bestueckten Platten, bisher nur ueber den
    /// Tipp/Halten-Umweg am Arrange-Knopf erreichbar (der ordnet immer
    /// alle an, ohne Wahl). Hier laesst sich beides gezielt waehlen.
    @State private var alleBetten = false
    /// Entspricht ArrangeSettings::set_rotation_enabled im Kern -
    /// Vorgabe aus, wie am Desktop.
    @State private var drehenErlauben = false

    private var inputs: [BedInput] {
        model.beds.map { bed in
            BedInput(id: Int32(bed.index), name: model.bedLabel(bed.index),
                     locked: bed.locked, objectCount: Int32(bed.objectCount),
                     instanceCount: Int32(bed.instanceCount))
        }
    }

    private func arrangeAvailability(for index: Int) -> ArrangeAvailability {
        let active = model.beds.firstIndex { $0.index == index } ?? 0
        return BedStripContract.shared.state(beds: inputs, activeIndex: Int32(active)).arrange
    }

    private var canArrange: Bool {
        if alleBetten {
            return model.beds.contains { arrangeAvailability(for: $0.index) == .available }
        }
        return arrangeAvailability(for: ziel) == .available
    }

    /// Ein kleines, am Knopf verankertes Popover statt eines
    /// Vollbild-Sheets - "Arrange" ist eine schnelle Randentscheidung,
    /// kein eigener Bildschirm. Eigene Kapseln statt Picker/Toggle:
    /// die System-Steuerelemente rechneten hier mit hellem Aussehen und
    /// waren auf dem dunklen Grund kaum zu lesen.
    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(12)) {
            HStack {
                Text(PsUiCatalog.tr("Arrange"))
                    .font(.system(size: ps.font(15), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Spacer()
                Button(st("Done", "Fertig")) { isPresented = false }
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.orange)
                    .accessibilityIdentifier("arrange.close")
            }

            HStack(spacing: ps.pt(6)) {
                zielKapsel(st("Current bed", "Aktuelles Bett"), aktiv: !alleBetten,
                          kennung: "arrange.zielmodus.aktuell") { alleBetten = false }
                zielKapsel(st("All beds", "Alle Betten"), aktiv: alleBetten,
                          kennung: "arrange.zielmodus.alle") { alleBetten = true }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("arrange.zielmodus")

            if !alleBetten {
                Text(st("Target bed", "Zielbett"))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                ScrollView {
                    VStack(spacing: ps.pt(6)) {
                        ForEach(model.beds, id: \.index) { bett in
                            Button {
                                ziel = bett.index
                                ergebnis = ""
                            } label: {
                                HStack {
                                    if bett.locked { Image(systemName: "lock.fill") }
                                    Text(model.bedLabel(bett.index))
                                    Spacer()
                                    Text("\(bett.objectCount)")
                                }
                                .padding(.horizontal, ps.pt(10))
                                .frame(maxWidth: .infinity, minHeight: ps.touch(38))
                                .background(ziel == bett.index
                                            ? PrusaColors.orange : PrusaColors.panelRaised)
                                .foregroundStyle(ziel == bett.index
                                                 ? PrusaColors.background
                                                 : PrusaColors.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("arrange.target.\(bett.index)")
                        }
                    }
                }
                .frame(maxHeight: ps.pt(140))
            }

            Stepper(value: $abstand, in: 0...50, step: 0.5) {
                HStack {
                    Text(st("Spacing", "Abstand"))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                    Text(abstand.formatted(.number.precision(.fractionLength(1))) + " mm")
                        .monospacedDigit()
                        .foregroundStyle(PrusaColors.textPrimary)
                }
            }
            .accessibilityIdentifier("arrange.gap")

            Button {
                drehenErlauben.toggle()
            } label: {
                HStack {
                    Text(st("Allow rotation", "Drehen erlauben"))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Spacer()
                    Image(systemName: drehenErlauben ? "checkmark.square.fill" : "square")
                        .foregroundStyle(drehenErlauben
                                         ? PrusaColors.orange : PrusaColors.textMuted)
                }
                .frame(minHeight: ps.touch(32))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("arrange.drehen")

            if !ergebnis.isEmpty {
                Text(ergebnis)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("arrange.result")
            }

            Button(action: anordnen) {
                Label(PsUiCatalog.tr("Arrange"), systemImage: "square.grid.2x2")
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: ps.touch(44))
            }
            .buttonStyle(.borderedProminent)
            .tint(PrusaColors.orange)
            .accessibilityIdentifier("arrange.run")
        }
        .padding(ps.pt(16))
        .frame(width: ps.pt(300))
        .background(PrusaColors.background)
        .overlay { PSMarke(name: "arrange.panel") }
        .onAppear {
            ziel = model.beds.first(where: \.active)?.index ?? 0
        }
    }

    private func zielKapsel(_ label: String, aktiv: Bool, kennung: String,
                            aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(12), weight: aktiv ? .semibold : .regular))
                .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textPrimary)
                .frame(maxWidth: .infinity, minHeight: ps.touch(34))
                .background(aktiv ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func anordnen() {
        if alleBetten {
            guard canArrange else {
                ergebnis = st("No unlocked bed contains instances to arrange.",
                              "Kein entsperrtes Bett enthält Instanzen zum Anordnen.")
                return
            }
            model.arrangeAll(gapMm: Float(abstand), allowRotation: drehenErlauben)
            ergebnis = st("All beds arranged.", "Alle Betten angeordnet.")
            return
        }
        switch arrangeAvailability(for: ziel) {
        case .locked:
            ergebnis = st(
                "Bed \(ziel + 1) is locked. Unlock it in the bed selector.",
                "Bett \(ziel + 1) ist gesperrt. Entsperre es in der Bettauswahl.")
            return
        case .empty:
            ergebnis = st(
                "Bed \(ziel + 1) is empty. There is nothing to arrange.",
                "Bett \(ziel + 1) ist leer. Es gibt nichts anzuordnen.")
            return
        case .available:
            break
        default:
            break
        }
        do {
            let info = try model.arrange(target: ziel, gapMm: Float(abstand),
                                         allowRotation: drehenErlauben)
            switch info.status {
            case .empty:
                ergebnis = st(
                    "Bed \(ziel + 1) is empty. There is nothing to arrange.",
                    "Bett \(ziel + 1) ist leer. Es gibt nichts anzuordnen.")
            case .arranged:
                ergebnis = st(
                    "Bed \(ziel + 1): \(info.instanceCount) instances arranged.",
                    "Bett \(ziel + 1): \(info.instanceCount) Instanzen angeordnet.")
            }
        } catch PsmCore.PsmError.call(_, let code, _) where
                    code == PSM_ERR_LOCKED.rawValue {
            ergebnis = st(
                "Bed \(ziel + 1) is locked. Unlock it in the bed selector.",
                "Bett \(ziel + 1) ist gesperrt. Entsperre es in der Bettauswahl.")
        } catch PsmCore.PsmError.call(_, let code, _) where
                    code == PSM_ERR_FULL.rawValue {
            ergebnis = st(
                "Bed \(ziel + 1) is full. Reduce copies or choose another bed.",
                "Bett \(ziel + 1) ist voll. Verringere die Kopien oder wähle ein anderes Bett.")
        } catch {
            ergebnis = error.localizedDescription
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
