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
    @State private var tab = "print"
    @State private var pageIndex = 0

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
                    inhalt
                }
            }
        }
        .onAppear {
            // Nur beim Erscheinen: waehrend jemand blaettert, soll der
            // Einstieg von aussen nichts mehr umstellen.
            if tabs.contains(startTab) { tab = startTab }
        }
    }

    // MARK: - Kopf und Reiter

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

            Spacer()

            // Das gewaehlte Profil gehoert in den Kopf: ohne es weiss
            // niemand, was hier gerade geaendert wird.
            if let profil = model.selectedPreset(for: tab) {
                Text(profil)
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(48))
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
            }
        }
        .frame(width: ps.pt(210))
        .background(PrusaColors.panel)
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
                if let name = zeile.title, zeile.options.count > 1 {
                    VStack(alignment: .leading, spacing: ps.pt(4)) {
                        Text(PsUiCatalog.tr(name))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        HStack(spacing: ps.pt(8)) {
                            ForEach(zeile.options, id: \.key) { option in
                                SettingField(model: model, option: option, kompakt: true)
                            }
                        }
                    }
                } else {
                    ForEach(zeile.options, id: \.key) { option in
                        SettingField(model: model, option: option, kompakt: false)
                    }
                }
            }
        }
    }
}
