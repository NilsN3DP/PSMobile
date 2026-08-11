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

    /// Experimentelle lokale PrusaLink-Kopplung: standardmaessig aus,
    /// genau wie unter Android (PrinterStore.localPairingOptIn), damit
    /// niemand versehentlich in einen fremden Drucker-Hotspot koppelt.
    private static let localPairingOptInKey = "prusalink.localPairingOptIn"
    @State private var localPairingOptIn = UserDefaults.standard.bool(forKey: PrintersView.localPairingOptInKey)

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
                    if senden == nil {
                        lokaleKopplungOptIn
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
                    if senden == nil && localPairingOptIn {
                        knopf("Experimental: QR koppeln",
                              kennung: "drucker.lokal.neu") {
                            var neu = PrusaLinkClient.Printer()
                            neu.host = "http://192.168.4.1"
                            neu.localExperimental = true
                            neu.allowInsecureHttp = true
                            bearbeitet = neu
                        }
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

    /// Standardmaessig aus. Akzeptiert im Kopplungsschritt nur lokale
    /// Drucker-Hotspots (siehe PrusaLinkClient.isLocalHost); kein
    /// automatisches Entdecken fremder Geraete.
    private var lokaleKopplungOptIn: some View {
        HStack(alignment: .top, spacing: ps.pt(10)) {
            VStack(alignment: .leading, spacing: ps.pt(2)) {
                Text(st("Experimental local PrusaLink pairing",
                        "Experimentelle lokale PrusaLink-Kopplung"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(st("Off by default. Only accepts local printer hotspots; QR scan or manual JSON entry.",
                        "Standardmäßig aus. Akzeptiert nur lokale Drucker-Hotspots; QR-Scan oder manuelle JSON-Eingabe."))
                    .font(.system(size: ps.font(11)))
                    .foregroundStyle(PrusaColors.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { localPairingOptIn },
                set: { neu in
                    localPairingOptIn = neu
                    UserDefaults.standard.set(neu, forKey: Self.localPairingOptInKey)
                }
            ))
            .labelsHidden()
            .tint(PrusaColors.orange)
            .accessibilityIdentifier("drucker.lokal.optin")
        }
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
    @State private var pairingJson = ""
    @State private var pairingLaeuft = false
    @State private var pairingMeldung: String?
    @State private var pairingFehler = false
    @State private var zeigeScanner = false

    private let localClient = PrusaLinkClient()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(14)) {
                Text(st("Printer", "Drucker"))
                    .font(.system(size: ps.font(20)))
                    .foregroundStyle(PrusaColors.textPrimary)

                if printer.localExperimental {
                    lokaleKopplung
                }

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

                Toggle(isOn: $printer.lightingOptIn) {
                    VStack(alignment: .leading, spacing: ps.pt(2)) {
                        Text(st("Experimental lighting", "Experimentelle Beleuchtung"))
                            .font(.system(size: ps.font(13)))
                            .foregroundStyle(PrusaColors.textPrimary)
                        Text(st("Off per printer. It sends no command until a documented CFW hardware API is available.",
                                "Pro Drucker deaktiviert. Es wird kein Befehl gesendet, bis eine dokumentierte CFW-Hardware-API vorliegt."))
                            .font(.system(size: ps.font(11)))
                            .foregroundStyle(PrusaColors.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .tint(PrusaColors.orange)
                .disabled(printer.hostType != .prusaLink)
                .onChange(of: printer.hostType) { neu in
                    if neu != .prusaLink { printer.lightingOptIn = false }
                }

                HStack(spacing: ps.pt(12)) {
                    Button(st("Delete", "Löschen")) {
                        store.remove(printer)
                        onClose()
                    }
                    .foregroundStyle(PrusaColors.danger)
                    .accessibilityIdentifier("drucker.loeschen")
                    if printer.localExperimental {
                        Button(st("Reset local pairing", "Lokale Kopplung zurücksetzen")) {
                            store.setLocalPairingToken(nil, for: printer)
                            printer.localExperimental = false
                            printer.localHosts = []
                            printer.localCapabilities = []
                            printer.localModel = ""
                            printer.localNozzleDiameter = nil
                            printer.localNozzleMaterial = "unknown"
                            store.upsert(printer)
                            onClose()
                        }
                        .foregroundStyle(PrusaColors.textMuted)
                        .accessibilityIdentifier("drucker.lokal.zuruecksetzen")
                    }
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

    /// Scannen, koppeln, Ergebnis anzeigen - Gegenstueck zum onPair-Callback
    /// in PrintersScreen.kt. Speichert bei Erfolg sofort und schliesst den
    /// Editor, statt auf den separaten Sichern-Knopf zu warten: das
    /// Kopplungsergebnis (Benutzername/Passwort vom Drucker) waere sonst
    /// verloren, wenn jemand den Editor stattdessen abbricht.
    private func kopple(_ text: String) {
        pairingFehler = false
        guard let daten = text.data(using: .utf8),
              let payload = try? JSONDecoder().decode(PrusaLinkClient.LocalPairingPayload.self, from: daten)
        else {
            pairingFehler = true
            pairingMeldung = st("Invalid QR code", "QR-Code ungültig")
            return
        }
        pairingLaeuft = true
        pairingMeldung = nil
        Task {
            do {
                let ergebnis = try await localClient.pairLocal(payload)
                await MainActor.run {
                    pairingLaeuft = false
                    var gekoppelt = ergebnis.printer
                    gekoppelt.id = printer.id
                    gekoppelt.presetName = printer.presetName
                    gekoppelt.lightingOptIn = printer.lightingOptIn
                    store.upsert(gekoppelt)
                    let gesichert = store.setSecret(ergebnis.secret, for: gekoppelt)
                        && store.setLocalPairingToken(payload.pairingToken, for: gekoppelt)
                    guard gesichert else {
                        // Ohne das wirkte die Kopplung erfolgreich - der
                        // Drucker stand in der Liste -, aber ein spaeterer
                        // Verbindungsversuch scheiterte unerklaerlich an
                        // "Anmeldedaten unvollstaendig", weil das Passwort
                        // nie im Schluesselbund ankam.
                        pairingFehler = true
                        pairingMeldung = st("Could not store the password securely. Please try again.",
                                             "Das Passwort konnte nicht sicher gespeichert werden. Bitte erneut versuchen.")
                        return
                    }
                    onClose()
                }
            } catch {
                await MainActor.run {
                    pairingLaeuft = false
                    pairingFehler = true
                    switch error as? PrusaLinkClient.LocalPairError {
                    case .invalidPayload: pairingMeldung = st("Invalid QR code", "QR-Code ungültig")
                    case .unauthorized: pairingMeldung = st("Pairing token rejected", "Kopplungs-Token abgelehnt")
                    case .invalidResponse: pairingMeldung = st("Printer response invalid", "Antwort des Druckers ungültig")
                    case nil: pairingMeldung = st("Pairing failed", "Kopplung fehlgeschlagen")
                    }
                }
            }
        }
    }

    private var lokaleKopplung: some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(st("Experimental · Local printer", "Experimental · Lokaler Drucker"))
                .font(.system(size: ps.font(13)))
                .foregroundStyle(PrusaColors.orange)
            Text(st("Scan the printer's QR code, or paste the shown JSON manually.",
                    "QR-Code des Druckers scannen, oder den angezeigten JSON-Inhalt manuell einfügen."))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)

            Button { zeigeScanner = true } label: {
                Text(st("Scan QR code", "QR-Code scannen"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                    .background(PrusaColors.orange)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(3)))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("drucker.lokal.scan")

            feld(st("QR payload (manual fallback)", "QR-Payload (manuelle Fallback-Eingabe)"),
                 text: $pairingJson, kennung: "drucker.lokal.json")

            Button { kopple(pairingJson) } label: {
                Text(pairingLaeuft
                     ? st("Pairing…", "Kopplung läuft…")
                     : st("Pair QR payload", "QR-Payload koppeln"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.orange)
                    .frame(maxWidth: .infinity, minHeight: ps.touch(48))
                    .overlay(RoundedRectangle(cornerRadius: ps.pt(3))
                        .stroke(PrusaColors.divider, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(pairingJson.isEmpty || pairingLaeuft)
            .accessibilityIdentifier("drucker.lokal.koppeln")

            if let pairingMeldung {
                Text(pairingMeldung)
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(pairingFehler ? PrusaColors.danger : PrusaColors.ok)
            }

            if !printer.localModel.isEmpty || !printer.localHosts.isEmpty {
                lokalesDetail
            }
        }
        .padding(ps.pt(12))
        .background(PrusaColors.panelRaised)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
        .fullScreenCover(isPresented: $zeigeScanner) {
            QRScanSheet(
                onCode: { code in
                    zeigeScanner = false
                    pairingJson = code
                    kopple(code)
                },
                onCancel: { zeigeScanner = false }
            )
        }
    }

    private var lokalesDetail: some View {
        VStack(alignment: .leading, spacing: ps.pt(4)) {
            Text(st("Paired printer", "Gekoppelter Drucker"))
                .font(.system(size: ps.font(12)))
                .fontWeight(.semibold)
                .foregroundStyle(PrusaColors.textPrimary)
            detailZeile(st("Model", "Modell"), printer.localModel.isEmpty ? "–" : printer.localModel)
            detailZeile(st("IP address", "IP-Adresse"), printer.localHosts.first ?? "–")
            detailZeile(st("Nozzle", "Düse"),
                        printer.localNozzleDiameter.map { "\($0) mm · \(printer.localNozzleMaterial)" } ?? "–")
            detailZeile(st("Capabilities", "Fähigkeiten"),
                        printer.localCapabilities.isEmpty
                        ? "–" : printer.localCapabilities.sorted().joined(separator: ", "))
        }
    }

    private func detailZeile(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.system(size: ps.font(11))).foregroundStyle(PrusaColors.textMuted)
            Spacer()
            Text(value).font(.system(size: ps.font(11))).foregroundStyle(PrusaColors.textPrimary)
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
