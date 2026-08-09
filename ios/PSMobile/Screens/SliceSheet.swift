import SwiftUI
import PSMShared

/// Was waehrend und nach dem Slicen zu sehen ist.
///
/// Vorher war das eine Luecke in beiden Apps: man tippte auf "G-Code",
/// und sichtbar geschah nichts. Der Kern rechnete, die Zahlen lagen
/// hinterher vor - nur sah sie niemand, und herausbekommen liess sich
/// die Datei auf iOS ueberhaupt nicht.
///
/// Die Aufbereitung der Zahlen steht in `SliceSummary` im gemeinsamen
/// Modul. Hier steht, wie es aussieht und wie die Datei das Geraet
/// verlaesst - und das ist auf iOS das Teilen-Blatt, nicht ein
/// Dateipfad.
struct SliceSheet: View {

    @ObservedObject var model: SlicerModel
    /// Wenn gesetzt, steht neben dem Sichern auch der Weg zum Drucker.
    var onSendToPrinter: ((URL) -> Void)?
    var onClose: () -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        ZStack {
            PrusaColors.background.opacity(0.82)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                // Waehrend gerechnet wird, schliesst ein Tippen daneben
                // nichts: sonst verschwindet der Fortschritt, und der
                // Slice laeuft unsichtbar weiter.
                .onTapGesture { if !laeuft { onClose() } }

            VStack(alignment: .leading, spacing: ps.pt(14)) {
                inhalt
            }
            .padding(ps.pt(20))
            .frame(maxWidth: ps.pt(420))
            .background(PrusaColors.panel)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .overlay(
                RoundedRectangle(cornerRadius: ps.pt(4))
                    .stroke(PrusaColors.divider, lineWidth: 1)
            )
            .padding(ps.pt(16))
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "slice.blatt") }
    }

    /// "3.2 s" unter einer Sekunde grob, sonst auf eine Nachkommastelle -
    /// niemand braucht Millisekunden, aber "0 s" bei einem schnellen
    /// lokalen Schnitt waere eine falsche Auskunft.
    private func dauerText(_ sekunden: Double) -> String {
        sekunden < 1 ? String(format: "%.2f s", sekunden) : String(format: "%.1f s", sekunden)
    }

    private var laeuft: Bool {
        if case .running = model.progress { return true }
        return false
    }

    @ViewBuilder private var inhalt: some View {
        switch model.progress {
        case .running(let prozent, let phase):
            titel(st("Slicing", "Wird gesliced"))
            // Die Phase mit anzeigen, nicht nur Prozent: das macht eine
            // mehrminuetige Wartezeit ertraeglich, weil man sieht, dass
            // sich etwas bewegt, auch wenn die Zahl stehen bleibt.
            Text("\(prozent) %  ·  \(phase)")
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.textMuted)
            ProgressView(value: Double(prozent), total: 100)
                .tint(PrusaColors.orange)
            knopf(st("Cancel", "Abbrechen"), kennung: "slice.abbrechen", betont: false) {
                model.cancel()
            }

        case .done(let sekunden, _, _):
            titel(st("Ready to print", "Fertig zum Drucken"))
            Text(st("Sliced in \(dauerText(sekunden))",
                    "Geslict in \(dauerText(sekunden))"))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
            if let werte = model.stats {
                zahlen(werte)
            }
            if model.gcodeURLs.count > 1 {
                // "Alle Betten schneiden": mehrere Dateien auf einmal.
                // ShareLink nimmt eine Sammlung genauso wie eine
                // einzelne Datei - AirDrop, Dateien-App und Drucker-Apps
                // zeigen dann alle Dateien zur Auswahl.
                Text(st("\(model.gcodeURLs.count) G-Code files",
                        "\(model.gcodeURLs.count) G-Code-Dateien"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                ShareLink(items: model.gcodeURLs) {
                    Text(st("Export all", "Alle exportieren"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(50))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("slice.sichern")

                // Alle Betten teilen sich heute noch ein Druckerprofil
                // (siehe Uebergabe zu Profilen je Bett) - PrusaLink
                // bekommt darum jede Datei einzeln vom selben Drucker
                // aus angeboten, statt eine eigene Mehrfachauswahl zu
                // bauen, die es beim aktuellen Stand nicht braucht.
                if let senden = onSendToPrinter {
                    VStack(spacing: ps.pt(6)) {
                        ForEach(model.gcodeURLs, id: \.self) { datei in
                            Button { senden(datei) } label: {
                                HStack {
                                    Text(datei.lastPathComponent)
                                        .font(.system(size: ps.font(12)))
                                        .foregroundStyle(PrusaColors.textPrimary)
                                        .lineLimit(1)
                                    Spacer()
                                    Text(st("Send", "Senden"))
                                        .font(.system(size: ps.font(12), weight: .semibold))
                                        .foregroundStyle(PrusaColors.orange)
                                }
                                .padding(.horizontal, ps.pt(10))
                                .frame(minHeight: ps.touch(40))
                                .background(PrusaColors.panelRaised)
                                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("slice.andrucker." + datei.lastPathComponent)
                        }
                    }
                }
            } else if let url = model.gcodeURL {
                // Auf iOS gibt es keinen Ordner, in den eine App einfach
                // schreibt. Das Teilen-Blatt deckt alles ab, was der
                // Nutzer damit vorhat: in Dateien sichern, per AirDrop
                // an den Rechner, in eine Druckerapp geben.
                ShareLink(item: url) {
                    Text(st("Export G-Code", "G-Code exportieren"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(50))
                        .background(PrusaColors.orange)
                        .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("slice.sichern")

                if let senden = onSendToPrinter {
                    knopf(st("Send to printer", "An Drucker senden"),
                          kennung: "slice.andrucker", betont: false) { senden(url) }
                }
            } else {
                hinweis(st("The G-Code could not be written.",
                           "Der G-Code liess sich nicht schreiben."))
            }
            knopf(st("Close", "Schließen"), kennung: "slice.schliessen", betont: false, aktion: onClose)

        case .failed(let meldung):
            titel(st("Slicing failed", "Slicen fehlgeschlagen"))
            // Die Meldung des Kerns woertlich. Sie nennt meist den
            // Grund - eine eigene, freundlichere Formulierung wuerde ihn
            // verstecken.
            Text(meldung)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.danger)
                .fixedSize(horizontal: false, vertical: true)
            knopf(st("Close", "Schließen"), kennung: "slice.schliessen", betont: false, aktion: onClose)

        case .cancelled:
            titel(st("Cancelled", "Abgebrochen"))
            knopf(st("Close", "Schließen"), kennung: "slice.schliessen", betont: false, aktion: onClose)

        case .idle:
            EmptyView()
        }
    }

    private func zahlen(_ werte: PsmCore.SliceStats) -> some View {
        let zeilen = SliceSummary.shared.rows(
            seconds: werte.printTimeSeconds,
            grams: werte.filamentGrams,
            millimetres: werte.filamentMm,
            cost: werte.cost,
            objects: Int32(werte.objects)
        )
        return VStack(spacing: 0) {
            ForEach(Array(zeilen.enumerated()), id: \.offset) { _, zeile in
                HStack {
                    Text(zeile.label)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                    Spacer()
                    Text(zeile.value)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textPrimary)
                }
                .padding(.vertical, ps.pt(7))
                Divider().background(PrusaColors.divider)
            }
        }
    }

    private func titel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(18)))
            .foregroundStyle(PrusaColors.textPrimary)
    }

    private func hinweis(_ text: String) -> some View {
        Text(text)
            .font(.system(size: ps.font(12)))
            .foregroundStyle(PrusaColors.danger)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func knopf(_ label: String,
                       kennung: String,
                       betont: Bool,
                       aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(14)))
                .foregroundStyle(betont ? .white : PrusaColors.textMuted)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .background(betont ? PrusaColors.orange : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Warum gerade nicht geschnitten werden kann - als Blatt, wenn jemand
/// den Knopf trotzdem trifft.
///
/// Ein ausgegrauter Knopf sagt nur, dass es nicht geht. Er sagt nicht,
/// dass das Material fehlt.
struct SliceBlockerSheet: View {

    let gruende: [String]
    var onClose: () -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        ZStack {
            PrusaColors.background.opacity(0.82)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onClose)

            VStack(alignment: .leading, spacing: ps.pt(12)) {
                Text(SimpleModeState.shared.text(english: "Not ready yet",
                                                 german: "Noch nicht bereit"))
                    .font(.system(size: ps.font(18)))
                    .foregroundStyle(PrusaColors.textPrimary)
                ForEach(gruende, id: \.self) { grund in
                    Text("·  " + grund)
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)
                }
                Button(action: onClose) {
                    Text(SimpleModeState.shared.text(english: "Close", german: "Schließen"))
                        .font(.system(size: ps.font(14)))
                        .foregroundStyle(PrusaColors.orange)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("slice.hinderungsgrund.schliessen")
            }
            .padding(ps.pt(20))
            .frame(maxWidth: ps.pt(380))
            .background(PrusaColors.panel)
            .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
            .padding(ps.pt(16))
        }
        .overlay(alignment: .topLeading) { PSMarke(name: "slice.hinderungsgrund") }
    }
}
