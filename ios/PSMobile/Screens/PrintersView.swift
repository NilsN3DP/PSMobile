import SwiftUI
import PSMShared

/// Drucker einrichten und den G-Code hinschicken.
///
/// Gegenstueck zu `PrintersScreen.kt`. Bis hierhin war auf iOS gar kein
/// Weg zum Drucker da - der fertige G-Code liess sich nur teilen.
struct PrintersView: View {

    @ObservedObject var store: PrinterStore
    /// Wenn gesetzt, wird nach dem Auswaehlen gesendet statt nur
    /// geprueft. So dient derselbe Bildschirm zum Einrichten und zum
    /// Senden.
    var senden: URL?
    var dateiname: String = "psmobile.gcode"
    /// Der Druckerprofilname des Projekts, aus dem der G-Code kommt -
    /// nur damit lassen sich passende von unpassenden Geraeten
    /// unterscheiden. Ohne Angabe (z.B. beim reinen Verwalten der
    /// Drucker) stehen alle gleichrangig da.
    var passendesProfil: String? = nil
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var bearbeitet: PrusaLinkClient.Printer?
    @State private var meldung: String?
    @State private var laeuft = false
    @State private var nachDemSendenDrucken = false
    /// Aus wegen Unuebersichtlichkeit, wie schon bei den Filamenten:
    /// wer mehrere Drucker eingerichtet hat, sieht beim Senden zuerst
    /// nur die, die zum geschnittenen Profil passen.
    @State private var zeigeAlleDrucker = false
    /// Ob ein eingerichteter Drucker gerade erreichbar ist - automatisch
    /// geprueft beim Oeffnen, nicht erst auf Tippen. nil heisst "wird
    /// noch geprueft", nicht "unbekannt fuer immer".
    @State private var erreichbarkeit: [String: Bool] = [:]

    private var passende: [PrusaLinkClient.Printer] {
        guard senden != nil, let profil = passendesProfil, !profil.isEmpty else {
            return store.printers
        }
        return store.printers.filter { $0.presetName == profil }
    }

    private var unpassendeAnzahl: Int {
        guard senden != nil, let profil = passendesProfil, !profil.isEmpty else { return 0 }
        return store.printers.count - passende.count
    }

    private var angezeigt: [PrusaLinkClient.Printer] {
        zeigeAlleDrucker || passende.isEmpty ? store.printers : passende
    }

    private let client = PrusaLinkClient()
    private let octoClient = OctoPrintClient()

    /// Ergebnis unabhaengig vom tatsaechlichen Client - beide melden
    /// dieselbe Zweiheit (ok/fehler), nur mit eigenem Typ, weil
    /// OctoPrintClient nichts von PrusaLinkClient wissen muss.
    private func pruefen(_ drucker: PrusaLinkClient.Printer,
                         secret: PrusaLinkClient.Secret) async -> PrusaLinkClient.Ergebnis {
        switch drucker.hostType {
        case .prusaLink:
            return await client.probe(drucker, secret: secret)
        case .octoprint:
            switch await octoClient.probe(drucker, apiKey: secret.apiKey) {
            case .ok(let t):     return .ok(t)
            case .fehler(let t): return .fehler(t)
            }
        }
    }

    private func hochladen(_ drucker: PrusaLinkClient.Printer,
                           secret: PrusaLinkClient.Secret,
                           datei: URL, name: String,
                           printAfter: Bool) async -> PrusaLinkClient.Ergebnis {
        switch drucker.hostType {
        case .prusaLink:
            return await client.upload(drucker, secret: secret, datei: datei,
                                       name: name, printAfter: printAfter)
        case .octoprint:
            switch await octoClient.upload(drucker, apiKey: secret.apiKey, datei: datei,
                                           name: name, printAfter: printAfter) {
            case .ok(let t):     return .ok(t)
            case .fehler(let t): return .fehler(t)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            kopfzeile
            Divider().background(PrusaColors.divider)
            ScrollView {
                VStack(alignment: .leading, spacing: ps.pt(10)) {
                    if let meldung {
                        Text(meldung)
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(PrusaColors.textPrimary)
                            .padding(ps.pt(12))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(PrusaColors.panelRaised)
                    }
                    if senden != nil {
                        Toggle(isOn: $nachDemSendenDrucken) {
                            Text(st("Start printing after upload",
                                    "Nach dem Senden drucken"))
                                .font(.system(size: ps.font(13)))
                                .foregroundStyle(PrusaColors.textPrimary)
                        }
                        .tint(PrusaColors.orange)
                        .accessibilityIdentifier("drucker.dann.drucken")
                    }
                    if store.printers.isEmpty {
                        Text(st("No printer set up yet", "Noch kein Drucker eingerichtet"))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textMuted)
                    }
                    if unpassendeAnzahl > 0 && !passende.isEmpty {
                        Button { zeigeAlleDrucker.toggle() } label: {
                            HStack(spacing: ps.pt(5)) {
                                Image(systemName: zeigeAlleDrucker ? "eye.fill" : "eye.slash")
                                Text(zeigeAlleDrucker
                                     ? st("Hide \(unpassendeAnzahl) other printers",
                                          "\(unpassendeAnzahl) andere Drucker ausblenden")
                                     : st("Show \(unpassendeAnzahl) other printers",
                                          "\(unpassendeAnzahl) andere Drucker anzeigen"))
                            }
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .padding(.horizontal, ps.pt(10))
                            .frame(height: ps.touch(30))
                            .background(PrusaColors.panelRaised)
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("drucker.andere.umschalten")
                    }
                    ForEach(angezeigt) { drucker in
                        zeile(drucker)
                    }
                    knopf(st("Add printer", "Drucker hinzufügen"),
                          kennung: "drucker.neu") {
                        bearbeitet = PrusaLinkClient.Printer()
                    }
                }
                .padding(ps.pt(16))
                .frame(maxWidth: ps.pt(760))
                .frame(maxWidth: .infinity)
            }
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "drucker") }
        .sheet(item: $bearbeitet) { drucker in
            PrinterEditView(store: store, printer: drucker) { bearbeitet = nil }
        }
        .task(id: store.printers.map(\.id)) { await alleAutomatischPruefen() }
    }

    /// Alle eingerichteten Drucker gleichzeitig anfragen - nur im
    /// eigenen Netz schnell genug, um beim Oeffnen nicht aufzufallen;
    /// ueber das offene Internet blockiert nichts, weil jede Anfrage
    /// ihr eigenes Ergebnis unabhaengig eintraegt.
    private func alleAutomatischPruefen() async {
        await withTaskGroup(of: (String, Bool).self) { gruppe in
            for drucker in store.printers {
                let secret = store.secret(for: drucker)
                guard drucker.isComplete(secret: secret) else { continue }
                gruppe.addTask {
                    let ergebnis = await pruefen(drucker, secret: secret)
                    if case .ok = ergebnis { return (drucker.id, true) }
                    return (drucker.id, false)
                }
            }
            for await (id, ok) in gruppe {
                erreichbarkeit[id] = ok
            }
        }
    }

    private var kopfzeile: some View {
        HStack(spacing: ps.pt(16)) {
            Button(action: onClose) {
                Text("‹  " + st("Back", "Zurück"))
                    .font(.system(size: ps.font(15)))
                    .foregroundStyle(PrusaColors.orange)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("drucker.zurueck")
            Text(senden == nil
                 ? st("Printers", "Drucker")
                 : st("Send to printer", "An Drucker senden"))
                .font(.system(size: ps.font(20)))
                .foregroundStyle(PrusaColors.textPrimary)
            Spacer()
            if laeuft { ProgressView().tint(PrusaColors.orange) }
        }
        .padding(.horizontal, ps.pt(16))
        .frame(height: ps.touch(56))
    }

    private func zeile(_ drucker: PrusaLinkClient.Printer) -> some View {
        HStack(spacing: ps.pt(12)) {
            erreichbarkeitsPunkt(drucker)
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(drucker.name.isEmpty ? drucker.host : drucker.name)
                    .font(.system(size: ps.font(14)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(drucker.baseUrl)
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .lineLimit(1)
            }
            Spacer()
            Button { bearbeitet = drucker } label: {
                Text(st("Edit", "Bearbeiten"))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .frame(minHeight: ps.touch(44))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("drucker.bearbeiten." + drucker.id)
            Button { handle(drucker) } label: {
                Text(senden == nil ? st("Test", "Prüfen") : st("Send", "Senden"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(.white)
                    .padding(.horizontal, ps.pt(14))
                    .frame(height: ps.touch(44))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(laeuft)
            .accessibilityIdentifier("drucker.aktion." + drucker.id)
        }
        .padding(ps.pt(12))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(4)))
    }

    /// Gruen erreichbar, gedaempft nicht erreichbar, ein kleiner Kreis
    /// waehrend der ersten Pruefung noch laeuft - dieselbe Sprache wie
    /// die Fortschrittspunkte anderswo in der App.
    private func erreichbarkeitsPunkt(_ drucker: PrusaLinkClient.Printer) -> some View {
        let zustand = erreichbarkeit[drucker.id]
        return Circle()
            .fill(zustand == true ? PrusaColors.orange
                  : zustand == false ? PrusaColors.textMuted.opacity(0.3)
                  : PrusaColors.textMuted.opacity(0.15))
            .frame(width: ps.pt(9), height: ps.pt(9))
            .accessibilityIdentifier("drucker.erreichbar." + drucker.id)
            .accessibilityValue(zustand == true
                ? st("Reachable", "Erreichbar")
                : zustand == false
                    ? st("Not reachable", "Nicht erreichbar")
                    : st("Checking", "Wird geprüft"))
    }

    private func handle(_ drucker: PrusaLinkClient.Printer) {
        let secret = store.secret(for: drucker)
        guard drucker.isComplete(secret: secret) else {
            meldung = drucker.transportError
                ?? st("Credentials incomplete", "Anmeldedaten unvollständig")
            return
        }
        laeuft = true
        meldung = nil
        Task {
            let ergebnis: PrusaLinkClient.Ergebnis
            if let datei = senden {
                ergebnis = await hochladen(drucker, secret: secret, datei: datei,
                                           name: dateiname,
                                           printAfter: nachDemSendenDrucken)
            } else {
                ergebnis = await pruefen(drucker, secret: secret)
            }
            await MainActor.run {
                laeuft = false
                switch ergebnis {
                case .ok(let text):
                    meldung = text
                    if senden == nil { erreichbarkeit[drucker.id] = true }
                case .fehler(let text):
                    meldung = text
                    if senden == nil { erreichbarkeit[drucker.id] = false }
                }
            }
        }
    }

    private func knopf(_ label: String,
                       kennung: String,
                       aktion: @escaping () -> Void) -> some View {
        Button(action: aktion) {
            Text(label)
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.orange)
                .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                .overlay(
                    RoundedRectangle(cornerRadius: ps.pt(3))
                        .stroke(PrusaColors.divider, lineWidth: 1)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(kennung)
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}

/// Einen Drucker anlegen oder aendern.
///
/// Das Passwort steht in einem SecureField und geht direkt in den
/// Schluesselbund - es taucht weder in den Einstellungen noch in einem
/// Protokoll auf.
struct PrinterEditView: View {

    @ObservedObject var store: PrinterStore
    @State var printer: PrusaLinkClient.Printer
    var onClose: () -> Void

    @Environment(\.psScale) private var ps
    @State private var apiKey = ""
    @State private var password = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(14)) {
                Text(st("Printer", "Drucker"))
                    .font(.system(size: ps.font(20)))
                    .foregroundStyle(PrusaColors.textPrimary)

                feld(st("Name", "Name"), text: $printer.name, kennung: "drucker.name")

                // Der Host-Typ entscheidet, welcher Client ueberhaupt
                // spricht (siehe PrintersView.pruefen/hochladen) - vor
                // der Adresse, weil er auch bestimmt, welche Felder
                // danach ueberhaupt Sinn ergeben.
                Picker("", selection: $printer.hostType) {
                    Text("PrusaLink").tag(PrusaLinkClient.HostType.prusaLink)
                    Text("OctoPrint").tag(PrusaLinkClient.HostType.octoprint)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("drucker.hosttyp")
                .onChange(of: printer.hostType) { neu in
                    // OctoPrint kennt in dieser Anbindung nur den
                    // API-Schluessel, kein Digest-Verfahren.
                    if neu == .octoprint { printer.usesApiKey = true }
                }

                feld(st("Address", "Adresse"), text: $printer.host, kennung: "drucker.adresse")
                Text(st("Without a scheme, HTTPS applies.",
                        "Ohne Schema gilt HTTPS."))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)

                if printer.hostType == .prusaLink {
                    // PrusaLink ab 0.7 nutzt Benutzername und Passwort
                    // ueber HTTP-Digest; aeltere Firmware einen
                    // API-Schluessel.
                    Picker("", selection: $printer.usesApiKey) {
                        Text(st("User + password", "Benutzer + Passwort")).tag(false)
                        Text(st("API key", "API-Schlüssel")).tag(true)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("drucker.verfahren")
                }

                if printer.usesApiKey {
                    geheimFeld(st("API key", "API-Schlüssel"), text: $apiKey,
                               kennung: "drucker.apikey")
                } else {
                    feld(st("Username", "Benutzername"), text: $printer.username,
                         kennung: "drucker.benutzer")
                    geheimFeld(st("Password", "Passwort"), text: $password,
                               kennung: "drucker.passwort")
                }

                if printer.hostType == .prusaLink {
                    feld(st("Storage", "Speicher"), text: $printer.storage,
                         kennung: "drucker.speicher")
                }

                Toggle(isOn: $printer.allowInsecureHttp) {
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(st("Allow plain HTTP", "Klartext-HTTP erlauben"))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Text(st("Credentials travel unencrypted. Only in a network you trust.",
                                "Die Zugangsdaten gehen unverschlüsselt über das Netz. Nur in einem Netz, dem du traust."))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .tint(PrusaColors.orange)

                HStack(spacing: ps.pt(12)) {
                    Button(st("Delete", "Löschen")) {
                        store.remove(printer)
                        onClose()
                    }
                    .foregroundStyle(PrusaColors.danger)
                    .accessibilityIdentifier("drucker.loeschen")
                    Spacer()
                    Button(st("Cancel", "Abbrechen"), action: onClose)
                        .foregroundStyle(PrusaColors.textMuted)
                    Button {
                        store.upsert(printer)
                        store.setSecret(
                            PrusaLinkClient.Secret(apiKey: apiKey, password: password),
                            for: printer)
                        onClose()
                    } label: {
                        Text(st("Save", "Sichern"))
                            .foregroundStyle(.white)
                            .padding(.horizontal, ps.pt(20))
                            .frame(height: ps.touch(48))
                            .background(PrusaColors.orange)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("drucker.sichern")
                }
            }
            .padding(ps.pt(20))
            .frame(maxWidth: ps.pt(640))
            .frame(maxWidth: .infinity)
        }
        .background(PrusaColors.background)
        .onAppear {
            let vorhanden = store.secret(for: printer)
            apiKey = vorhanden.apiKey
            password = vorhanden.password
        }
    }

    private func feld(_ label: String,
                      text: Binding<String>,
                      kennung: String) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
            TextField("", text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: ps.font(14)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                .accessibilityIdentifier(kennung)
        }
    }

    private func geheimFeld(_ label: String,
                            text: Binding<String>,
                            kennung: String) -> some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(label)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
            SecureField("", text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: ps.font(14)))
                .foregroundStyle(PrusaColors.textPrimary)
                .padding(.horizontal, ps.pt(10))
                .frame(height: ps.touch(44))
                .background(PrusaColors.panelRaised)
                .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
                .accessibilityIdentifier(kennung)
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
