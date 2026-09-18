import SwiftUI
import PSMShared

/// Das Modelle-Blatt am unteren Rand - Gegenstueck zu
/// `SimpleModelSheet.kt`.
///
/// Es loest den einzelnen Knopf "Modell hinzufuegen" ab, sobald etwas
/// auf dem Bett liegt. Damit ist im Simple Mode erreichbar, was bisher
/// auf iOS gar nicht ging: anordnen, klonen, entfernen und auf ein
/// anderes Bett schieben.
///
/// Welche Aktion wann etwas bewirkt, entscheidet `SimpleModelSheetState`
/// im gemeinsamen Modul - dieselbe Regel wie auf Android.
struct SimpleModelSheetView: View {

    @ObservedObject var model: SlicerModel
    var onPickFile: () -> Void
    var onArrange: () -> Void = {}
    /// Die Vorschau laesst sich abschalten - sie kostet Platz, und auf
    /// einem schmalen Geraet ist die Zeile ohnehin eng.
    @StateObject private var einstellungen = AppSettingsStore()

    @Environment(\.psScale) private var ps
    @AppStorage(AppSettings.shared.KEY_UNITS_IMPERIAL) private var zollEinheiten = false
    @State private var ausgeklappt = true
    @State private var gewaehlt: [Int32] = []
    @State private var zeigeBettwahl = false

    /// Wie auf Android: drei Zeilen sind sichtbar, der Rest wird
    /// gescrollt. Frei mitwachsend schoebe sich das Blatt mit jeder
    /// weiteren Datei weiter ueber das Bett.
    private static let sichtbareZeilen = 3

    private var ids: [Int32] { model.objects.map(\.id) }

    private var aktionen: [SimpleModelSheetState.Action] {
        SimpleModelSheetState.shared.enabledActions(
            objectsOnBed: Int32(model.objects.count),
            selected: gewaehlt.map { KotlinInt(int: $0) },
            bedCount: Int32(max(model.beds.count, 1)),
            maxBeds: Int32(SlicerModel.maxBeds)
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            kopfzeile
            if ausgeklappt {
                Divider().background(PrusaColors.divider)
                liste
                if model.beds.count > 1 {
                    Divider().background(PrusaColors.divider)
                    bettleiste
                }
            }
        }
        .frame(maxWidth: ps.pt(380))
        .background(PrusaColors.panel)
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(2))
                .stroke(PrusaColors.divider, lineWidth: 1)
        )
        // Geloeschte oder verschobene Objekte duerfen nicht als Geister
        // in der Auswahl bleiben - sonst nennt die Kopfzeile eine Zahl,
        // zu der es keine Zeilen mehr gibt.
        .onChange(of: model.objects.count) { _ in
            gewaehlt = gewaehlt.filter { ids.contains($0) }
        }
        .sheet(isPresented: $zeigeBettwahl) { bettwahl }
    }

    // MARK: - Kopfzeile

    private var kopfzeile: some View {
        HStack(spacing: ps.pt(2)) {
            // Eine Zeile: mit sechs Knoepfen daneben brach "1 SELECTED" auf dem
            // Telefon in drei Zeilen um (Zwilling: SimpleModelSheetView.kt, 16.09.2026).
            Text(SimpleModelSheetState.shared.headline(
                selected: gewaehlt.map { KotlinInt(int: $0) }))
                .font(.system(size: ps.font(12), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, ps.pt(10))

            if gewaehlt.isEmpty {
                aktion("▣", st("Select all", "Alle wählen"),
                       an: enthalten(.selectAll), kennung: "blatt.alle") {
                    gewaehlt = ids
                }
                aktion("＋", st("More", "Weitere"), an: true, kennung: "blatt.mehr",
                       aktion: onPickFile)
                aktion("▤", st("Arrange", "Anordnen"),
                       an: enthalten(.arrange), kennung: "blatt.anordnen") {
                    onArrange()
                }
            } else {
                aktion("✕", st("Cancel", "Abbrechen"), an: true, kennung: "blatt.abbrechen") {
                    gewaehlt = []
                }
                aktion("▤", st("Arrange", "Anordnen"),
                       an: enthalten(.arrange), kennung: "blatt.anordnen") {
                    onArrange()
                }
                aktion("➜", st("Move to", "Ziehen zu"),
                       an: enthalten(.moveToBed), kennung: "blatt.bett") {
                    zeigeBettwahl = true
                }
                aktion("⧉", st("Clone", "Klonen"),
                       an: enthalten(.clone), kennung: "blatt.klonen") {
                    model.duplicate(gewaehlt)
                }
                aktion("✖", st("Remove", "Entfernen"),
                       an: enthalten(.remove), kennung: "blatt.entfernen") {
                    model.removeObjects(gewaehlt)
                    gewaehlt = []
                }
            }
            aktion(ausgeklappt ? "⌄" : "⌃", st("Collapse", "Einklappen"),
                   an: true, kennung: "blatt.klappen") {
                ausgeklappt.toggle()
            }
        }
        .frame(height: ps.touch(46))
    }

    private func enthalten(_ a: SimpleModelSheetState.Action) -> Bool {
        aktionen.contains(a)
    }

    // MARK: - Liste

    private var liste: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(model.objects, id: \.id) { objekt in
                    zeile(objekt)
                }
            }
        }
        .frame(height: ps.pt(58) * CGFloat(min(max(model.objects.count, 1),
                                               Self.sichtbareZeilen)))
    }

    private func zeile(_ objekt: PsmCore.ObjectInfo) -> some View {
        let angehakt = gewaehlt.contains(objekt.id)
        let hervor = objekt.id == model.selectedId && gewaehlt.isEmpty
        return Button {
            // Sobald etwas angehakt ist, hakt ein Tippen an und ab.
            // Ohne Auswahl waehlt es das Objekt im Viewport aus.
            if gewaehlt.isEmpty {
                model.select(objekt.id)
            } else {
                gewaehlt = SimpleModelSheetState.shared.toggle(
                    selected: gewaehlt.map { KotlinInt(int: $0) },
                    id: Int32(objekt.id)
                ).map { $0.int32Value }
            }
        } label: {
            HStack(spacing: ps.pt(10)) {
                if einstellungen.thumbnails { ObjektMasse(objekt: objekt) }
                Button {
                    gewaehlt = SimpleModelSheetState.shared.toggle(
                        selected: gewaehlt.map { KotlinInt(int: $0) },
                        id: Int32(objekt.id)
                    ).map { $0.int32Value }
                } label: {
                    Image(systemName: angehakt ? "checkmark.square.fill" : "square")
                        .font(.system(size: ps.font(16)))
                        .foregroundStyle(angehakt ? PrusaColors.orange : PrusaColors.textMuted)
                        .frame(width: ps.touch(40), height: ps.touch(40))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: ps.pt(2)) {
                    Text(objekt.name.isEmpty ? "Objekt \(objekt.id)" : objekt.name)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .lineLimit(1)
                    Text(Masse.text(objekt.sizeMm.x, objekt.sizeMm.y, objekt.sizeMm.z, zoll: zollEinheiten))
                        .font(.system(size: ps.font(10)))
                        .foregroundStyle(objekt.outsideBed
                                         ? PrusaColors.danger : PrusaColors.textMuted)
                }
                Spacer()
            }
            .padding(.horizontal, ps.pt(8))
            .frame(height: ps.pt(58))
            .background(hervor ? PrusaColors.panelRaised : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("blatt.zeile.\(objekt.id)")
    }

    // MARK: - Betten

    private var bettleiste: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(6)) {
                ForEach(model.beds, id: \.index) { bett in
                    Button { model.selectBed(bett.index) } label: {
                        Text(st("Bed ", "Bett ") + "\(bett.index + 1) · \(bett.objectCount)")
                            .font(.system(size: ps.font(10)))
                            .foregroundStyle(bett.active
                                             ? PrusaColors.background : PrusaColors.textPrimary)
                            .padding(.horizontal, ps.pt(12))
                            .frame(minHeight: ps.pt(36))
                            .background(bett.active
                                        ? PrusaColors.orange : PrusaColors.panelRaised)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(2)))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("blatt.bett.\(bett.index)")
                }
            }
            .padding(.horizontal, ps.pt(8))
            .padding(.vertical, ps.pt(6))
        }
    }

    /// Zielbetten fuer "auf Bett ziehen". Das letzte Ziel legt bei Bedarf
    /// ein neues Bett an - ohne diese Moeglichkeit waere ein volles Bett
    /// eine Sackgasse.
    private var bettwahl: some View {
        let ziele = SimpleModelSheetState.shared.moveTargets(
            bedCount: Int32(max(model.beds.count, 1)),
            activeBed: Int32(model.beds.firstIndex { $0.active } ?? 0),
            maxBeds: Int32(SlicerModel.maxBeds)
        )
        return VStack(alignment: .leading, spacing: ps.pt(10)) {
            Text(st("Move to bed", "Auf Bett ziehen"))
                .font(.system(size: ps.font(18)))
                .foregroundStyle(PrusaColors.textPrimary)
            ForEach(ziele, id: \.self) { ziel in
                let index = Int(truncating: ziel)
                Button {
                    model.moveToBed(gewaehlt, target: index)
                    gewaehlt = []
                    zeigeBettwahl = false
                } label: {
                    Text(index < model.beds.count
                         ? st("Bed ", "Bett ") + "\(index + 1)"
                         : st("New bed", "Neues Bett"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                        .background(PrusaColors.panelRaised)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("bettwahl.\(index)")
            }
            Spacer()
        }
        .padding(ps.pt(20))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PrusaColors.background)
    }

    // MARK: - Bausteine

    private func aktion(_ glyph: String,
                        _ label: String,
                        an: Bool,
                        kennung: String,
                        aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            VStack(spacing: 0) {
                Text(glyph)
                    .font(.system(size: ps.font(15)))
                Text(label)
                    .font(.system(size: ps.font(8)))
                    .lineLimit(1)
            }
            .foregroundStyle(an ? PrusaColors.textPrimary : PrusaColors.textMuted.opacity(0.4))
            .frame(width: ps.pt(52), height: ps.touch(44))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!an)
        .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Das Groessenverhaeltnis eines Objekts als Kaestchen.
///
/// Gegenstueck zu `ObjectProportionThumb` auf Android, mit denselben
/// Massen. Bewusst kein gerendertes Bild: das kostet je Objekt einen
/// Durchgang durch den Viewport, und bei zwanzig Teilen in der Liste
/// merkt man das. Um zwei Objekte zu unterscheiden, reichen die
/// Proportionen - hoch und schmal oder flach und breit.
struct ObjektMasse: View {

    let objekt: PsmCore.ObjectInfo
    @Environment(\.psScale) private var ps

    var body: some View {
        let b = max(objekt.sizeMm.x, 0.1)
        let t = max(objekt.sizeMm.y, 0.1)
        let h = max(objekt.sizeMm.z, 0.1)
        let laengste = max(b, max(t, h))
        let breite = max(CGFloat(26 * (max(b, t) / laengste)), 4)
        let hoehe = max(CGFloat(26 * (h / laengste)), 4)
        return ZStack {
            RoundedRectangle(cornerRadius: ps.pt(2))
                .fill(PrusaColors.background)
            RoundedRectangle(cornerRadius: ps.pt(1))
                .fill(PrusaColors.orange)
                .frame(width: ps.pt(breite), height: ps.pt(hoehe))
        }
        .frame(width: ps.pt(36), height: ps.pt(36))
        .accessibilityIdentifier("blatt.masse.\(objekt.id)")
    }
}
