import SwiftUI
import PSMShared

/// Material wählen, wie EasyPrint es zeigt.
///
/// Vorlage sind die Bildschirmfotos der EasyPrint-App: oben ein
/// Suchfeld über Anbieter, Material und Farbe, darunter eine Reihe
/// Typ-Knöpfe und eine Reihe Farbpunkte, und darunter Karten mit
/// Hersteller, Typ und Farbe.
///
/// Das ist mehr als Zierde. Eine Liste mit vierhundert Filamentprofilen
/// ist mit dem Finger nicht zu durchsuchen, und niemand kennt den
/// genauen Namen seines Profils — man weiß, welche Rolle im Schrank
/// liegt: "die orange Prusament-PLA". Genau danach lässt sich hier
/// suchen.
///
/// Welche Einträge zu einer Suche passen, entscheidet `FilamentCatalog`
/// im gemeinsamen Modul — dieselbe Antwort auf beiden Plattformen.
struct MaterialAuswahlView: View {

    @ObservedObject var model: SlicerModel
    /// Was gerade gilt. Wird hervorgehoben, damit man sich nicht fragt,
    /// warum man hier ist.
    let gewaehlt: String
    var onWahl: (String) -> Void

    @Environment(\.psScale) private var ps
    @State private var suche = ""
    @State private var typ = ""
    @State private var farbe = ""

    private var alle: [FilamentCatalog.Entry] { model.filamentCatalog() }
    private var treffer: [FilamentCatalog.Entry] {
        FilamentCatalog.shared.filter(entries: alle, query: suche, type: typ, colorHex: farbe)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            suchfeld
            typknoepfe
            farbpunkte
            if treffer.isEmpty {
                Text(FilamentCatalog.shared.emptyMessage().text)
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .padding(.vertical, ps.pt(12))
            } else {
                karten
            }
        }
    }

    // MARK: - Suchen und filtern

    private var suchfeld: some View {
        HStack(spacing: ps.pt(6)) {
            TextField(FilamentCatalog.shared.searchHint().text, text: $suche)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textPrimary)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier("material.suche")
            if !suche.isEmpty {
                Button { suche = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(PrusaColors.textMuted)
                        .frame(width: ps.touch(36), height: ps.touch(36))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("material.suche.leeren")
            }
            Image(systemName: "magnifyingglass")
                .foregroundStyle(PrusaColors.textMuted)
        }
        .padding(.horizontal, ps.pt(12))
        .frame(height: ps.touch(48))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
    }

    /// Die gängigen Typen zuerst — wer PLA sucht, soll nicht an ABS
    /// und ASA vorbei.
    private var typknoepfe: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(8)) {
                ForEach(FilamentCatalog.shared.types(entries: alle), id: \.self) { name in
                    Button {
                        // Ein zweites Tippen hebt die Einschränkung auf.
                        typ = (typ == name) ? "" : name
                    } label: {
                        Text(name)
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(typ == name
                                             ? PrusaColors.background : PrusaColors.textPrimary)
                            .padding(.horizontal, ps.pt(16))
                            .frame(height: ps.touch(40))
                            .background(typ == name ? PrusaColors.orange : Color.clear)
                            .overlay(
                                Capsule().stroke(typ == name
                                                 ? PrusaColors.orange : PrusaColors.divider,
                                                 lineWidth: 1)
                            )
                            .clipShape(Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("material.typ." + name)
                }
            }
            .padding(.vertical, ps.pt(2))
        }
    }

    /// Die häufigsten Farben im Bestand, nicht ein fester Farbkreis:
    /// die Punkte sollen zeigen, was wirklich da ist.
    private var farbpunkte: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ps.pt(10)) {
                ForEach(FilamentCatalog.shared.colors(entries: alle, limit: 12), id: \.self) { hex in
                    Button {
                        farbe = (farbe == hex) ? "" : hex
                    } label: {
                        Circle()
                            .fill(Color(hexString: hex) ?? PrusaColors.panelRaised)
                            .frame(width: ps.pt(26), height: ps.pt(26))
                            .overlay(
                                Circle().stroke(farbe == hex
                                                ? PrusaColors.orange : PrusaColors.divider,
                                                lineWidth: farbe == hex ? 3 : 1)
                            )
                            .frame(width: ps.touch(40), height: ps.touch(40))
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("material.farbe." + hex)
                }
            }
            .padding(.vertical, ps.pt(2))
        }
    }

    // MARK: - Die Karten

    private var karten: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: ps.pt(10)),
                            GridItem(.flexible(), spacing: ps.pt(10))],
                  spacing: ps.pt(10)) {
            ForEach(treffer, id: \.rawPreset) { eintrag in
                Button { onWahl(eintrag.rawPreset) } label: {
                    karte(eintrag)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("material.karte." + eintrag.rawPreset)
            }
        }
    }

    private func karte(_ eintrag: FilamentCatalog.Entry) -> some View {
        let aktiv = eintrag.rawPreset == gewaehlt
        return VStack(spacing: ps.pt(4)) {
            Text(eintrag.vendor)
                .font(.system(size: ps.font(14), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
                .lineLimit(1)
            Text(eintrag.type.isEmpty ? " " : eintrag.type)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
            spule(eintrag.colorHex)
            Text(eintrag.rawPreset)
                .font(.system(size: ps.font(9)))
                .foregroundStyle(PrusaColors.textMuted)
                .lineLimit(1)
        }
        .padding(ps.pt(10))
        .frame(maxWidth: .infinity, minHeight: ps.touch(132))
        .background(aktiv ? PrusaColors.panelRaised : PrusaColors.panel)
        .overlay(
            RoundedRectangle(cornerRadius: ps.pt(6))
                .stroke(aktiv ? PrusaColors.orange : PrusaColors.divider, lineWidth: aktiv ? 2 : 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
        .contentShape(Rectangle())
    }

    /// Eine Rolle statt eines Farbklecks.
    ///
    /// In EasyPrint steht auf jeder Karte ein Foto der Spule. Ein Foto
    /// je Filament hätten wir nicht, aber die Form allein trägt schon
    /// die Auskunft: das hier ist eine Rolle in dieser Farbe.
    private func spule(_ hex: String) -> some View {
        let farbe = Color(hexString: hex) ?? PrusaColors.panelRaised
        return ZStack {
            Capsule()
                .fill(farbe)
                .frame(width: ps.pt(56), height: ps.pt(22))
            Capsule()
                .fill(PrusaColors.background)
                .frame(width: ps.pt(18), height: ps.pt(22))
            Capsule()
                .stroke(PrusaColors.divider, lineWidth: 1)
                .frame(width: ps.pt(56), height: ps.pt(22))
        }
        .frame(height: ps.pt(28))
    }
}
