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
    private var aktiv: PsmCore.Bed? { model.beds.first(where: \.active) }

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
                    Image(systemName: aktiv?.locked == true
                          ? "lock.fill" : "square.stack.3d.up")
                    VStack(alignment: .leading, spacing: 1) {
                        Text(aktiv.map { model.bedLabel($0.index) } ?? st("Bed", "Bett"))
                            .font(.system(size: ps.font(13), weight: .semibold))
                        Text(st("\(aktiv?.objectCount ?? 0) objects",
                                "\(aktiv?.objectCount ?? 0) Objekte"))
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

            arrangeKnopf
        }
        .padding(.horizontal, ps.pt(8))
        .padding(.vertical, ps.pt(4))
    }

    private var rasterAuswahl: some View {
        VStack(spacing: ps.pt(4)) {
            HStack {
                Text(st("Beds", "Betten"))
                    .font(.system(size: ps.font(12), weight: .semibold))
                    .foregroundStyle(PrusaColors.textMuted)
                Spacer()
                arrangeKnopf
                Button { model.addBed() } label: {
                    Label(st("Add bed", "Bett hinzufügen"), systemImage: "plus")
                        .frame(minHeight: ps.touch())
                }
                .buttonStyle(.bordered)
                .tint(PrusaColors.orange)
                .accessibilityIdentifier("bed.add")
            }

            ScrollView(.vertical, showsIndicators: model.beds.count > 4) {
                LazyVGrid(columns: [
                    GridItem(.adaptive(minimum: ps.pt(170)), spacing: ps.pt(8))
                ], spacing: ps.pt(8)) {
                    ForEach(model.beds, id: \.index) { bett in
                        bettKarte(bett)
                    }
                }
            }
            .frame(maxHeight: ps.pt(132))
        }
        .padding(.horizontal, ps.pt(8))
        .padding(.vertical, ps.pt(4))
    }

    /// Kurzer Tipp ordnet alle Betten sofort an - das ist der haeufige
    /// Fall. Das Panel mit Zielbett und Abstand oeffnet sich erst beim
    /// Halten, damit der Alltagsgriff nicht durch ein Blatt fuehrt.
    private var arrangeKnopf: some View {
        Button(action: { model.arrangeAll() }) {
            Label(PsUiCatalog.tr("Arrange"), systemImage: "square.grid.2x2")
                .frame(minHeight: ps.touch())
        }
        .buttonStyle(.borderedProminent)
        .tint(PrusaColors.orange)
        .accessibilityIdentifier("arrange.open")
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.5).onEnded { _ in
                onArrange()
            }
        )
    }

    private func bettKarte(_ bett: PsmCore.Bed) -> some View {
        ZStack(alignment: .bottomTrailing) {
            Button {
                model.selectBed(bett.index)
            } label: {
                HStack(spacing: ps.pt(10)) {
                    Image(systemName: bett.locked ? "lock.fill" : "square.stack.3d.up")
                        .foregroundStyle(bett.active
                                         ? PrusaColors.background : PrusaColors.orange)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(model.bedLabel(bett.index))
                            .font(.system(size: ps.font(13), weight: .semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                        // Die Einheit steht schon in der Ueberschrift "Beds" -
                        // auf der Karte reicht die Zahl, sonst bricht der Text um.
                        Text("\(bett.objectCount)")
                            .font(.system(size: ps.font(10)))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .foregroundStyle(bett.active
                                             ? PrusaColors.background.opacity(0.75)
                                             : PrusaColors.textMuted)
                    }
                    Spacer(minLength: ps.pt(40))
                }
                .foregroundStyle(bett.active
                                 ? PrusaColors.background : PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(12))
                .frame(maxWidth: .infinity, minHeight: ps.touch(64),
                       alignment: .leading)
                .background(bett.active ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("bed.card.\(bett.index)")

            HStack(spacing: 0) {
                miniKnopf("pencil", id: "bed.rename.\(bett.index)") {
                    name = bett.name
                    umzubenennen = bett.index
                }
                miniKnopf(bett.locked ? "lock.open" : "lock",
                          id: "bed.lock.\(bett.index)") {
                    model.toggleBedLock(bett.index)
                }
                if model.beds.count > 1 && bett.objectCount == 0 {
                    miniKnopf("trash", id: "bed.remove.\(bett.index)") {
                        model.removeBed(bett.index)
                    }
                }
            }
            .padding(.trailing, ps.pt(4))
            .padding(.bottom, ps.pt(4))
        }
    }

    private func miniKnopf(_ symbol: String, id: String,
                           aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Image(systemName: symbol)
                .font(.system(size: ps.font(11)))
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

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ps.pt(10)) {
                    ForEach(model.beds, id: \.index) { bett in
                        ZStack(alignment: .bottomTrailing) {
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

                            HStack(spacing: 0) {
                                miniKnopf("pencil", id: "bed.rename.\(bett.index)") {
                                    name = bett.name
                                    umzubenennen = bett.index
                                }
                                miniKnopf(bett.locked ? "lock.open" : "lock",
                                          id: "bed.lock.\(bett.index)") {
                                    model.toggleBedLock(bett.index)
                                }
                                if model.beds.count > 1 && bett.objectCount == 0 {
                                    miniKnopf("trash", id: "bed.remove.\(bett.index)") {
                                        model.removeBed(bett.index)
                                    }
                                }
                            }
                            .padding(ps.pt(4))
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

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: ps.pt(14)) {
                Text(st("Target bed", "Zielbett"))
                    .font(.system(size: ps.font(13), weight: .semibold))
                ScrollView {
                    LazyVGrid(columns: [
                        GridItem(.adaptive(minimum: ps.pt(130)), spacing: ps.pt(8))
                    ], spacing: ps.pt(8)) {
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
                                .frame(maxWidth: .infinity, minHeight: ps.touch())
                                .background(ziel == bett.index
                                            ? PrusaColors.orange : PrusaColors.panelRaised)
                                .foregroundStyle(ziel == bett.index
                                                 ? PrusaColors.background
                                                 : PrusaColors.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: ps.pt(7)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("arrange.target.\(bett.index)")
                        }
                    }
                }
                .frame(maxHeight: ps.pt(180))

                Stepper(value: $abstand, in: 0...50, step: 0.5) {
                    HStack {
                        Text(st("Spacing", "Abstand"))
                        Spacer()
                        Text(abstand.formatted(.number.precision(.fractionLength(1))) + " mm")
                            .monospacedDigit()
                    }
                }
                .accessibilityIdentifier("arrange.gap")

                if !ergebnis.isEmpty {
                    Text(ergebnis)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .accessibilityIdentifier("arrange.result")
                }

                Button(action: anordnen) {
                    Label(PsUiCatalog.tr("Arrange"), systemImage: "square.grid.2x2")
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: ps.touch(50))
                }
                .buttonStyle(.borderedProminent)
                .tint(PrusaColors.orange)
                .accessibilityIdentifier("arrange.run")
            }
            .padding()
            .background(PrusaColors.background)
            .navigationTitle(PsUiCatalog.tr("Arrange"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(st("Done", "Fertig")) { isPresented = false }
                        .accessibilityIdentifier("arrange.close")
                }
            }
            .overlay { PSMarke(name: "arrange.panel") }
        }
        .presentationDetents([.medium, .large])
        .onAppear {
            ziel = model.beds.first(where: \.active)?.index ?? 0
        }
    }

    private func anordnen() {
        do {
            let info = try model.arrange(target: ziel, gapMm: Float(abstand))
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
