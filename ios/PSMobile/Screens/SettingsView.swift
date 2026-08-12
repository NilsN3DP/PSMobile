import SwiftUI
import PSMShared

/// Die Einstellungsseiten - alle 20 aus einem Renderer.
///
/// Gegenstueck zu SettingsScreen.kt. Es gibt hier bewusst keinen
/// handgebauten Bildschirm je Seite: Struktur, Reihenfolge und
/// Beschriftungen kommen aus PrusaSlicer (E-12), Typ, Grenzen und
/// Auswahlwerte aus dem Kern. Was gezeichnet wird, entscheidet der Typ
/// des Parameters - nicht eine Liste, die jemand pflegen muesste.
///
/// Dadurch traegt dieser eine Bildschirm 247 Parameter, und ein neuer
/// Parameter in PrusaSlicer erscheint nach dem naechsten Extraktionslauf
/// von selbst.
struct SettingsView: View {

    @ObservedObject var model: SlicerModel
    /// Womit der Bildschirm aufgeht. Der Advanced Mode hat fuer Druck,
    /// Filament und Drucker je einen eigenen Einstieg.
    var startTab: String = "print"
    let onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var zeigeZuruecksetzen = false
    @State private var zeigeProfilsuche = false
    @State private var tab = "print"
    @State private var pageIndex = 0
    /// Welcher Sonderbearbeiter offen ist, wenn ueberhaupt.
    @State private var bearbeiter: String?

    private let tabs = ["print", "filament", "printer"]

    private var pages: [TabsCatalog.Page] {
        PsUiCatalog.pages(tab, extruderCount: model.extruderCount)
    }

    var body: some View {
        ZStack {
            PrusaColors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                kopfzeile
                reiter
                Divider().overlay(PrusaColors.divider)
                HStack(spacing: 0) {
                    seitenliste
                    Divider().overlay(PrusaColors.divider)
                    if sonderseiteOffen { sonderseite } else { inhalt }
                }
            }
        }
        .sheet(item: Binding(get: { bearbeiter.map(Schluessel.init) },
                             set: { bearbeiter = $0?.wert })) { auswahl in
            if auswahl.wert == "bed_shape" {
                BedShapeEditor(model: model) { bearbeiter = nil }
            } else {
                WipingVolumesEditor(model: model) { bearbeiter = nil }
            }
        }
        .sheet(isPresented: $zeigeProfilsuche) {
            ProfileSearchSheet(model: model, tab: tab,
                                titel: PsUiCatalog.tr(tab == "print" ? "Print settings"
                                                       : tab == "filament" ? "Filament"
                                                       : "Printer"),
                                isPresented: $zeigeProfilsuche)
        }
        .onAppear {
            // Nur beim Erscheinen: waehrend jemand blaettert, soll der
            // Einstieg von aussen nichts mehr umstellen.
            if tabs.contains(startTab) { tab = startTab }
        }
    }

    // MARK: - Kopf und Reiter

    /// Was gegenueber den gespeicherten Profilen geaendert ist.
    private var geaenderte: [SlicerModel.Profilaenderung] {
        model.profilaenderungen()
    }

    private var kopfzeile: some View {
        HStack {
            Button(action: onClose) {
                Text("‹  " + PsUiCatalog.tr("Back"))
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.orange)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("einstellungen.zurueck")

            einstufung

            Spacer()

            // Das gewaehlte Profil gehoert in den Kopf: ohne es weiss
            // niemand, was hier gerade geaendert wird. Antippen oeffnet
            // die Profilsuche - vorher fuehrte von hier kein Weg zum
            // Wechseln, man musste den Bildschirm verlassen.
            if let profil = model.selectedPreset(for: tab) {
                Button { zeigeProfilsuche = true } label: {
                    HStack(spacing: ps.pt(3)) {
                        Text(profil)
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .lineLimit(1)
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    .frame(minHeight: ps.touch(36))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("einstellungen.profilsuche.oeffnen")
            }

            // Zuruecksetzen gehoert hierher und nicht nur in den Dialog
            // beim Moduswechsel: man will es auch dann, wenn man gerade
            // nirgendwohin wechselt. Es erscheint erst, wenn es etwas
            // zurueckzusetzen gibt - ein Knopf ohne Wirkung ist eine
            // Frage, die man sich stellt und nicht beantwortet bekommt.
            if !geaenderte.isEmpty {
                Button { zeigeZuruecksetzen = true } label: {
                    HStack(spacing: ps.pt(4)) {
                        Image(systemName: "arrow.counterclockwise")
                        Text("\(geaenderte.count)")
                    }
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.orange)
                    .padding(.horizontal, ps.pt(10))
                    .frame(height: ps.touch(40))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("einstellungen.zuruecksetzen")
                .confirmationDialog(
                    st("Reset profile to its saved values?",
                       "Profil auf seine gespeicherten Werte zurücksetzen?"),
                    isPresented: $zeigeZuruecksetzen, titleVisibility: .visible) {
                        Button(st("Reset \(geaenderte.count) values",
                                  "\(geaenderte.count) Werte zurücksetzen"),
                               role: .destructive) {
                            model.profilaenderungenVerwerfen()
                        }
                        Button(st("Cancel", "Abbrechen"), role: .cancel) {}
                    }
            }
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(48))
    }

    /// Simple, Advanced, Expert - wie in PrusaSlicer oben links.
    ///
    /// Nicht als Menue, sondern als drei Knoepfe: es sind drei, sie sind
    /// kurz, und man wechselt oft genug zwischen ihnen, dass ein
    /// zusaetzliches Antippen zum Aufklappen stoert.
    private var einstufung: some View {
        HStack(spacing: ps.pt(2)) {
            stufe(PsUiCatalog.tr("Simple"), .simple)
            stufe(PsUiCatalog.tr("Advanced"), .advanced)
            stufe(PsUiCatalog.tr("Expert"), .expert)
        }
        .padding(.leading, ps.pt(12))
    }

    private func stufe(_ label: String, _ wert: PsmCore.ConfigMode) -> some View {
        let aktiv = model.sichtbarkeit == wert
        return Button { model.sichtbarkeit = wert } label: {
            Text(label)
                .font(.system(size: ps.font(11)))
                .foregroundStyle(aktiv ? PrusaColors.background : PrusaColors.textMuted)
                .padding(.horizontal, ps.pt(10))
                .frame(minHeight: ps.touch(36))
                .background(aktiv ? PrusaColors.orange : PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("einstellungen.stufe." + String(wert.rawValue))
    }

    private var reiter: some View {
        HStack(spacing: ps.pt(4)) {
            ForEach(tabs, id: \.self) { name in
                Button {
                    tab = name
                    pageIndex = 0
                } label: {
                    Text(PsUiCatalog.tr(name.capitalized))
                        .font(.system(size: ps.font(14),
                                      weight: tab == name ? .semibold : .regular))
                        .foregroundStyle(tab == name
                                         ? PrusaColors.textPrimary : PrusaColors.textMuted)
                        .padding(.horizontal, ps.pt(14))
                        .frame(height: ps.touch(40))
                        .background(tab == name ? PrusaColors.panelRaised : .clear)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("reiter.\(name)")
            }
            Spacer()
        }
        .padding(.horizontal, ps.pt(12))
        .padding(.bottom, ps.pt(6))
    }

    // MARK: - Seiten

    /// Ob gerade die handgebaute Sonderseite offen ist.
    private var sonderseiteOffen: Bool {
        tab == "printer" && pageIndex == pages.count
    }

    private var seitenliste: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(Array(pages.enumerated()), id: \.offset) { index, seite in
                    Button {
                        pageIndex = index
                    } label: {
                        Text(PsUiCatalog.tr(seite.title))
                            .font(.system(size: ps.font(13),
                                          weight: pageIndex == index ? .semibold : .regular))
                            .foregroundStyle(pageIndex == index
                                             ? PrusaColors.orange : PrusaColors.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, ps.pt(12))
                            .frame(height: ps.touch(40))
                            .background(pageIndex == index
                                        ? PrusaColors.panelRaised : .clear)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("seite.\(index)")
                }

                // Die einzige Seite, die nicht aus der Vorlage kommt.
                //
                // bed_shape und wiping_volumes_matrix stehen nicht in
                // tabs.json: PrusaSlicer baut sie am Desktop mit eigenen
                // Widgets statt als Zeile im Parameterbaum. Als
                // Zeichenkette waeren sie unbedienbar - eine Bettform ist
                // "0x0,250x0,250x210,0x210", und ein Tippfehler in der
                // Mitte macht sie kaputt.
                if tab == "printer" {
                    Button { pageIndex = pages.count } label: {
                        Text(SimpleModeState.shared.text(english: "Bed and purging",
                                                         german: "Bett und Reinigung"))
                            .font(.system(size: ps.font(13),
                                          weight: sonderseiteOffen ? .semibold : .regular))
                            .foregroundStyle(sonderseiteOffen
                                             ? PrusaColors.orange : PrusaColors.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, ps.pt(12))
                            .frame(height: ps.touch(40))
                            .background(sonderseiteOffen ? PrusaColors.panelRaised : .clear)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("seite.sonderwerte")
                }
            }
        }
        .frame(width: ps.pt(210))
        .background(PrusaColors.panel)
    }

    /// Bettform und Reinigungsmengen, jede mit ihrem eigenen Bearbeiter.
    private var sonderseite: some View {
        VStack(alignment: .leading, spacing: ps.pt(14)) {
            sonderwert(PsUiCatalog.tr("Bed shape"), schluessel: "bed_shape")
            sonderwert(PsUiCatalog.tr("Wipe tower"), schluessel: "wiping_volumes_matrix")
            Spacer()
        }
        .padding(ps.pt(16))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func sonderwert(_ titel: String, schluessel: String) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(titel)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textPrimary)
            Button { bearbeiter = schluessel } label: {
                HStack {
                    Text(model.config(schluessel).map { $0.isEmpty ? "—" : $0 } ?? "—")
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .lineLimit(1)
                    Spacer()
                    Text(SimpleModeState.shared.text(english: "Edit", german: "Bearbeiten"))
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.orange)
                }
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("sonderwert." + schluessel)
        }
    }

    private var inhalt: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: ps.pt(18)) {
                if pageIndex < pages.count {
                    ForEach(Array(pages[pageIndex].groups.enumerated()), id: \.offset) { _, gruppe in
                        gruppenBlock(gruppe)
                    }
                }
            }
            .padding(ps.pt(16))
        }
        .frame(maxWidth: .infinity)
    }

    /// Ob ein Parameter auf der gewaehlten Stufe gezeigt wird.
    ///
    /// Kennt der Kern die Einstufung nicht, wird gezeigt. Etwas
    /// wegzulassen, weil man es nicht einordnen kann, waere die
    /// schlechtere Richtung: ein fehlender Parameter faellt niemandem
    /// auf, bis der Druck misslingt.
    private func sichtbarAufStufe(_ key: String) -> Bool {
        guard let meta = model.core?.configMeta(for: key) else { return true }
        return meta.mode.rawValue <= model.sichtbarkeit.rawValue
    }

    private func gruppenBlock(_ gruppe: TabsCatalog.Group) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            if !gruppe.title.isEmpty {
                Text(PsUiCatalog.tr(gruppe.title).uppercased())
                    .font(.system(size: ps.font(11), weight: .semibold))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            // Zusammengehoerende Parameter stehen nebeneinander - so wie
            // PrusaSlicer "Solid layers" mit oben und unten in einer
            // Zeile zeigt. Die Zuordnung kommt aus dem gemeinsamen Modul.
            ForEach(Array(TabsCatalog.shared.lines(group: gruppe).enumerated()),
                    id: \.offset) { _, zeile in
                let sichtbar = zeile.options.filter { sichtbarAufStufe($0.key) }
                if sichtbar.isEmpty {
                    EmptyView()
                } else if let name = zeile.title, sichtbar.count > 1 {
                    VStack(alignment: .leading, spacing: ps.pt(4)) {
                        Text(PsUiCatalog.tr(name))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        HStack(spacing: ps.pt(8)) {
                            ForEach(sichtbar, id: \.key) { option in
                                SettingField(model: model, option: option, kompakt: true)
                            }
                        }
                    }
                } else {
                    ForEach(sichtbar, id: \.key) { option in
                        SettingField(model: model, option: option, kompakt: false)
                    }
                }
            }
        }
    }
}

/// Ein Schluessel als identifizierbarer Wert, damit `sheet(item:)` ihn
/// annimmt. Ein blosser String ist nicht Identifiable, und ein zweites
/// Bool je Bearbeiter waere die schlechtere Loesung.
private struct Schluessel: Identifiable {
    let wert: String
    var id: String { wert }
    init(_ wert: String) { self.wert = wert }
}
