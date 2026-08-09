import SwiftUI
import UIKit
import PSMShared

/// Der Einstieg - Gegenstueck zu `WorkflowStartScreen.kt`.
///
/// Zwei Module statt drei: Remote Slicing ist seit dem Umbau kein
/// dritter Arbeitsmodus mehr, sondern ein Umschalter neben dem
/// Slice-Knopf in Simple/Advanced (siehe docs/remote-slicing.md) - hier
/// steht dafuer nur noch ein kleiner Fusszeilen-Eintrag.
///
/// Simple/Advanced starten immer ein neues, leeres Projekt - wer am
/// alten weiterarbeiten will, tut das ueber "Zuletzt" oben, nicht ueber
/// den Modus-Einstieg. Zwei verschiedene Absichten verdienen zwei
/// verschiedene Wege, nicht denselben Knopf mit unsichtbarem
/// Nebeneffekt.
struct WorkflowStartView: View {

    @EnvironmentObject private var model: SlicerModel
    var onSimple: () -> Void
    var onAdvanced: () -> Void
    var onAppSettings: () -> Void
    var onPrinterSetup: () -> Void = {}
    var onRemote: () -> Void = {}

    @Environment(\.psScale) private var ps
    @State private var alleProjekteZeigen = false
    @State private var gewaehltesProjekt: URL?
    @AppStorage(AppSettings.shared.KEY_PLUGIN_REMOTE_SLICE)
    private var remoteSlicePluginAn = true

    private var eng: Bool { ps.factor <= 0.8 }
    private var breit: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || ps.windowSize.width >= 760
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer(minLength: ps.pt(eng ? 16 : 32))
                VStack(alignment: .leading, spacing: ps.pt(eng ? 20 : 28)) {
                    kopf
                    zuletztBereich
                    modusKarten
                    fusszeile
                }
                .frame(maxWidth: ps.pt(breit ? 880 : 480))
                .frame(maxWidth: .infinity)
                .padding(.horizontal, ps.pt(20))
                Spacer(minLength: ps.pt(24))
            }
            .frame(maxWidth: .infinity, minHeight: ps.windowSize.height)
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "start") }
        .confirmationDialog(
            gewaehltesProjekt?.deletingPathExtension().lastPathComponent ?? "",
            isPresented: Binding(
                get: { gewaehltesProjekt != nil },
                set: { if !$0 { gewaehltesProjekt = nil } })
        ) {
            Button(st("Open in Simple mode", "In Simple Mode öffnen")) { projektOeffnen(inAdvanced: false) }
            Button(st("Open in Advanced mode", "In Advanced Mode öffnen")) { projektOeffnen(inAdvanced: true) }
            Button(st("Cancel", "Abbrechen"), role: .cancel) { gewaehltesProjekt = nil }
        }
        .sheet(isPresented: $alleProjekteZeigen) {
            AllProjectsSheet(
                onOpen: { url in
                    alleProjekteZeigen = false
                    gewaehltesProjekt = url
                },
                onClose: { alleProjekteZeigen = false }
            )
            .environmentObject(model)
        }
    }

    private var kopf: some View {
        HStack(spacing: ps.pt(12)) {
            Text("S")
                .font(.system(size: ps.font(22), weight: .bold))
                .foregroundStyle(PrusaColors.background)
                .frame(width: ps.pt(eng ? 40 : 48), height: ps.pt(eng ? 40 : 48))
                .background(PrusaColors.orange)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(5)))
            VStack(alignment: .leading, spacing: 1) {
                Text("PSMobile")
                    .font(.system(size: ps.font(24), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(st("3D PRINT WORKSPACE", "3D-DRUCK-ARBEITSPLATZ"))
                    .font(.system(size: ps.font(11), weight: .medium))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            Spacer()
        }
    }

    /// Bis zu vier zuletzt gesicherte Projekte, plus "Alle anzeigen" bei
    /// mehr. Fehlt ganz, wenn noch nichts gesichert wurde - eine leere
    /// Zeile waere nur eine Frage ohne Antwort.
    @ViewBuilder private var zuletztBereich: some View {
        let dateien = model.recentProjects(limit: 4)
        if !dateien.isEmpty {
            VStack(alignment: .leading, spacing: ps.pt(10)) {
                HStack {
                    Text(st("Recent", "Zuletzt"))
                        .font(.system(size: ps.font(12), weight: .semibold))
                        .foregroundStyle(PrusaColors.textMuted)
                    Spacer()
                    if model.recentProjects(limit: 5).count > 4 {
                        Button(st("Show all", "Alle anzeigen")) { alleProjekteZeigen = true }
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(PrusaColors.orange)
                            .accessibilityIdentifier("start.alleProjekte")
                    }
                }
                HStack(spacing: ps.pt(10)) {
                    ForEach(Array(dateien.enumerated()), id: \.offset) { index, url in
                        projektKachel(url)
                            .accessibilityIdentifier("start.zuletzt.\(index)")
                    }
                }
            }
        }
    }

    /// Wegwischen zum Aufraeumen - dieselbe Geste wie in einer Liste,
    /// nur handgebaut: die Kacheln stehen in einem HStack, nicht in
    /// einer List, die swipeActions von Haus aus kann.
    private func projektKachel(_ url: URL) -> some View {
        ProjektKachel(url: url,
                     oeffnen: { gewaehltesProjekt = url },
                     loeschen: { model.deleteProject(url) })
    }

    private var modusKarten: some View {
        let layout = breit
            ? AnyLayout(HStackLayout(alignment: .top, spacing: ps.pt(14)))
            : AnyLayout(VStackLayout(spacing: ps.pt(10)))
        return layout {
            modusKarte(nummer: "01", symbol: "bolt.fill", titel: "Simple Mode",
                      detail: st("Get to print quickly with a few clear decisions.",
                                 "Schnell zum Druck mit wenigen, klaren Entscheidungen."),
                      kennung: "start.simple") { neuesProjektStarten(onSimple) }
            modusKarte(nummer: "02", symbol: "slider.horizontal.3", titel: "Advanced Mode",
                      detail: st("Every setting PrusaSlicer knows, on all pages.",
                                 "Alle Einstellungen, die PrusaSlicer kennt, auf allen Seiten."),
                      kennung: "start.advanced") { neuesProjektStarten(onAdvanced) }
        }
    }

    private func modusKarte(nummer: String, symbol: String, titel: String, detail: String,
                            kennung: String, handlung: @escaping () -> Void) -> some View {
        Button(action: handlung) {
            VStack(alignment: .leading, spacing: ps.pt(14)) {
                HStack {
                    Image(systemName: symbol)
                        .font(.system(size: ps.font(18)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(width: ps.pt(34), height: ps.pt(34))
                        .background(PrusaColors.orange.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                    Spacer()
                    Text(nummer)
                        .font(.system(size: ps.font(12), weight: .bold))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                VStack(alignment: .leading, spacing: ps.pt(4)) {
                    Text(titel)
                        .font(.system(size: ps.font(17), weight: .semibold))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(detail)
                        .font(.system(size: ps.font(12)))
                        .foregroundStyle(PrusaColors.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
                HStack(spacing: ps.pt(4)) {
                    Text(st("New project", "Neues Projekt"))
                    Image(systemName: "arrow.right")
                }
                .font(.system(size: ps.font(13), weight: .semibold))
                .foregroundStyle(PrusaColors.orange)
                .padding(.top, ps.pt(10))
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(ps.pt(18))
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: ps.pt(breit ? 190 : 150))
            .background(PrusaColors.panel)
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(6))
                    .stroke(PrusaColors.divider, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private var fusszeile: some View {
        HStack(spacing: ps.pt(10)) {
            fusszeilenKnopf(st("App settings", "App-Einstellungen"),
                           symbol: "gearshape", kennung: "start.appeinstellungen",
                           aktion: onAppSettings)
            fusszeilenKnopf(st("Printer setup", "Ersteinrichtung"),
                           symbol: "printer", kennung: "start.einrichtung",
                           aktion: onPrinterSetup)
            if remoteSlicePluginAn {
                fusszeilenKnopf(st("Remote Slicing", "Remote Slicing"),
                               symbol: "cloud", kennung: "start.remote",
                               aktion: onRemote)
            }
        }
    }

    private func fusszeilenKnopf(_ label: String, symbol: String, kennung: String,
                                 aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            HStack(spacing: ps.pt(6)) {
                Image(systemName: symbol)
                Text(label)
            }
            .font(.system(size: ps.font(13)))
            .foregroundStyle(PrusaColors.textMuted)
            .padding(.horizontal, ps.pt(14))
            .frame(minHeight: ps.touch(42))
            .background(PrusaColors.panelRaised)
            .clipShape(Capsule())
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    // MARK: - Ablauf

    private func neuesProjektStarten(_ weiter: @escaping () -> Void) {
        model.newProject()
        weiter()
    }

    private func projektOeffnen(inAdvanced: Bool) {
        guard let url = gewaehltesProjekt else { return }
        gewaehltesProjekt = nil
        model.load(url: url)
        if inAdvanced { onAdvanced() } else { onSimple() }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Alle gesicherten Projekte, nicht nur die vier auf der Startseite -
/// dieselbe Auswahl (Simple/Advanced) wie dort, nur mit vollstaendiger
/// Liste statt Kacheln. Nicht mehr privat: der Advanced Mode zeigt
/// denselben Bestand ueber denselben Weg, siehe AdvancedWorkspaceView.
struct AllProjectsSheet: View {

    @EnvironmentObject private var model: SlicerModel
    var onOpen: (URL) -> Void
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var revision = 0

    var body: some View {
        NavigationStack {
            List {
                ForEach(model.recentProjects(limit: 500), id: \.self) { url in
                    Button { onOpen(url) } label: {
                        HStack {
                            Image(systemName: "square.stack.3d.up")
                                .foregroundStyle(PrusaColors.orange)
                            Text(url.deletingPathExtension().lastPathComponent)
                                .foregroundStyle(PrusaColors.textPrimary)
                        }
                    }
                    .accessibilityIdentifier("projekte.alle.\(url.lastPathComponent)")
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            model.deleteProject(url)
                            revision += 1
                        } label: {
                            Label(st("Delete", "Löschen"), systemImage: "trash")
                        }
                        .accessibilityIdentifier("projekte.alle.loeschen.\(url.lastPathComponent)")
                    }
                }
            }
            .id(revision)
            .navigationTitle(st("All projects", "Alle Projekte"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(st("Done", "Fertig"), action: onClose)
                        .accessibilityIdentifier("projekte.alle.fertig")
                }
            }
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "projekte.alle") }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Eine Kachel im "Zuletzt"-Streifen mit Wegwisch-Geste zum Loeschen.
/// Eigener Typ statt einer Funktion, weil der Wischversatz eigenen
/// @State braucht - eine Funktion kann das nicht ohne ViewBuilder-Tricks.
private struct ProjektKachel: View {
    let url: URL
    var oeffnen: () -> Void
    var loeschen: () -> Void

    @Environment(\.psScale) private var ps
    @State private var versatz: CGFloat = 0
    @State private var entfernt = false

    var body: some View {
        Button(action: oeffnen) {
            VStack(alignment: .leading, spacing: ps.pt(6)) {
                ZStack {
                    RoundedRectangle(cornerRadius: ps.pt(6))
                        .fill(PrusaColors.panel)
                    Image(systemName: "square.stack.3d.up")
                        .font(.system(size: ps.font(18)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                .frame(height: ps.pt(56))
                Text(url.deletingPathExtension().lastPathComponent)
                    .font(.system(size: ps.font(11), weight: .medium))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .lineLimit(1)
            }
            .padding(ps.pt(8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(PrusaColors.panelRaised)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .offset(x: versatz)
        .opacity(entfernt ? 0 : 1)
        .frame(width: entfernt ? 0 : nil)
        .gesture(
            DragGesture(minimumDistance: 12)
                .onChanged { wert in
                    // Nur nach oben (negativ) mitnehmen - ein Wisch nach
                    // rechts waere sonst mit dem Scrollen des Streifens
                    // selbst verwechselbar.
                    guard wert.translation.width < 0 else { return }
                    versatz = wert.translation.width
                }
                .onEnded { wert in
                    if wert.translation.width < -ps.pt(70) {
                        withAnimation(.easeOut(duration: 0.18)) {
                            versatz = -ps.pt(300)
                            entfernt = true
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                            loeschen()
                        }
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                            versatz = 0
                        }
                    }
                }
        )
        .accessibilityIdentifier("start.zuletzt.loeschen." + url.lastPathComponent)
    }
}
