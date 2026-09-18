import SwiftUI
import PSMShared

/// Erklaerung und Einrichtung von Remote Slicing (siehe
/// docs/remote-slicing.md).
///
/// Anders als beim ersten Entwurf ist das kein eigener Weg zum
/// Schneiden mehr - der laeuft ueber denselben "Slice now"-Knopf wie
/// immer, siehe SlicerModel.remoteSliceEnabled. Dieser Bildschirm
/// erklaert nur, was dahintersteckt, und nimmt Serveradresse und
/// Zugangs-Token entgegen.
struct RemoteSliceView: View {

    var onHome: () -> Void = {}

    @Environment(\.psScale) private var ps
    @AppStorage(SlicerModel.remoteSliceHostKey) private var serverAdresse: String = ""

    private enum Pruefung: Equatable {
        case unbekannt, prueft, erreichbar, fehler(String)
    }

    @State private var token: String = ""
    @State private var tokenGeladen = false
    @State private var pruefung: Pruefung = .unbekannt
    @State private var zeigeScanner = false
    @State private var zeigeEigenerCode = false

    private let client = RemoteSliceClient()
    private let credentials = RemoteSliceCredentialStore()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ps.pt(20)) {
                kopf
                erklaerung
                einrichtung
                if !serverAdresse.isEmpty {
                    pruefungsBereich
                }
                sicherheitshinweis
                Spacer(minLength: ps.pt(20))
            }
            .padding(ps.pt(20))
            .frame(maxWidth: ps.pt(560))
            .frame(maxWidth: .infinity)
        }
        .background(PrusaColors.background)
        .overlay(alignment: .topLeading) { PSMarke(name: "remote") }
        .onAppear(perform: tokenLaden)
    }

    private var kopf: some View {
        HStack {
            Button(action: onHome) {
                Image(systemName: "chevron.left")
                    .font(.system(size: ps.font(16), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .frame(width: ps.touch(40), height: ps.touch(40))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("remote.zurueck")
            VStack(alignment: .leading, spacing: 2) {
                Text(st("Remote Slicing", "Remote Slicing"))
                    .font(.system(size: ps.font(20), weight: .semibold))
                    .foregroundStyle(PrusaColors.textPrimary)
                Text(st("Slice on your own server instead of this device.",
                        "Auf dem eigenen Server statt auf diesem Gerät slicen."))
                    .font(.system(size: ps.font(12)))
                    .foregroundStyle(PrusaColors.textMuted)
            }
            Spacer()
        }
    }

    /// Was es ist und wie es ablaeuft - bevor jemand eine Adresse
    /// eintippt, sollte klar sein, wohin das Projekt dabei geht.
    private var erklaerung: some View {
        VStack(alignment: .leading, spacing: ps.pt(8)) {
            Text(st("How it works", "So funktioniert es"))
                .font(.system(size: ps.font(13), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            erklaerungsZeile(
                "1",
                st("Your project (models, printer, filament and print settings) is sent to a server you run yourself.",
                   "Dein Projekt (Modelle, Drucker-, Filament- und Druckeinstellungen) geht an einen Server, den du selbst betreibst."))
            erklaerungsZeile(
                "2",
                st("That server slices it - same core, same result as on this device, just with its own CPU instead of this device's.",
                   "Der Server schneidet es - derselbe Kern, dasselbe Ergebnis wie auf diesem Gerät, nur mit eigener Rechenleistung statt der dieses Geräts."))
            erklaerungsZeile(
                "3",
                st("The finished G-code comes back and can be exported or sent to a printer, exactly like a local slice.",
                   "Der fertige G-Code kommt zurück und lässt sich exportieren oder an einen Drucker senden - genau wie bei einem lokalen Schnitt."))
        }
        .padding(ps.pt(14))
        .background(PrusaColors.panel)
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
    }

    private func erklaerungsZeile(_ nummer: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: ps.pt(10)) {
            Text(nummer)
                .font(.system(size: ps.font(11), weight: .bold))
                .foregroundStyle(PrusaColors.orange)
                .frame(width: ps.pt(16))
            Text(text)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var einrichtung: some View {
        VStack(alignment: .leading, spacing: ps.pt(10)) {
            Text(st("Setup", "Einrichtung"))
                .font(.system(size: ps.font(13), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)

            VStack(alignment: .leading, spacing: ps.pt(6)) {
                Text(st("Server address", "Serveradresse"))
                    .font(.system(size: ps.font(11), weight: .semibold))
                    .foregroundStyle(PrusaColors.textMuted)
                TextField("192.168.1.50:8420", text: $serverAdresse)
                    .textFieldStyle(.plain)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.system(size: ps.font(14), design: .monospaced))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .padding(.horizontal, ps.pt(10))
                    .frame(minHeight: ps.touch(40))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(5)))
                    .accessibilityIdentifier("remote.adresse")
                    .onChange(of: serverAdresse) { _ in pruefung = .unbekannt }
            }

            VStack(alignment: .leading, spacing: ps.pt(6)) {
                Text(st("Access token (if the server requires one)",
                        "Zugangs-Token (falls der Server eins verlangt)"))
                    .font(.system(size: ps.font(11), weight: .semibold))
                    .foregroundStyle(PrusaColors.textMuted)
                SecureField(st("Optional for a home-network server",
                               "Optional bei einem Server nur im Heimnetz"),
                            text: $token)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.system(size: ps.font(14), design: .monospaced))
                    .foregroundStyle(PrusaColors.textPrimary)
                    .padding(.horizontal, ps.pt(10))
                    .frame(minHeight: ps.touch(40))
                    .background(PrusaColors.panelRaised)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(5)))
                    .accessibilityIdentifier("remote.token")
                    .onChange(of: token) { _ in
                        tokenSichern()
                        pruefung = .unbekannt
                    }
            }

            Button(st("Test connection", "Verbindung testen")) { verbindungPruefen() }
                .buttonStyle(.bordered)
                .disabled(serverAdresse.isEmpty || pruefung == .prueft)
                .accessibilityIdentifier("remote.verbinden")

            // Paaren per QR-Code, statt Adresse und - vor allem - das
            // lange Token abzutippen. Ein Geraet zeigt seinen Code
            // (unten), ein zweites scannt ihn hier.
            HStack(spacing: ps.pt(10)) {
                Button {
                    zeigeScanner = true
                } label: {
                    Label(st("Scan QR code", "QR-Code scannen"),
                          systemImage: "qrcode.viewfinder")
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("remote.qr.scannen")

                if !serverAdresse.isEmpty {
                    Button {
                        zeigeEigenerCode = true
                    } label: {
                        Label(st("Show my QR code", "Meinen QR-Code zeigen"),
                              systemImage: "qrcode")
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("remote.qr.zeigen")
                }
            }
        }
        .fullScreenCover(isPresented: $zeigeScanner) {
            QRScanSheet(onCode: { code in
                zeigeScanner = false
                guard let paar = RemotePairing.parse(code) else { return }
                serverAdresse = paar.host
                if !paar.token.isEmpty {
                    token = paar.token
                    tokenSichern()
                }
                pruefung = .unbekannt
            }, onCancel: { zeigeScanner = false })
        }
        .sheet(isPresented: $zeigeEigenerCode) {
            eigenerCodeBlatt
        }
    }

    /// Der eigene Server als QR-Code, fuer ein zweites Geraet.
    private var eigenerCodeBlatt: some View {
        VStack(spacing: ps.pt(16)) {
            Text(st("Scan this on your other device", "Auf dem anderen Gerät scannen"))
                .font(.system(size: ps.font(16), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            if let url = RemotePairing.url(host: serverAdresse, token: token) {
                QRCodeView(inhalt: url.absoluteString)
                    .frame(width: ps.pt(240), height: ps.pt(240))
                    .padding(ps.pt(16))
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
            }
            Text(st("Anyone who can scan this can submit slice jobs to your server.",
                    "Wer diesen Code scannen kann, kann Aufträge an deinen Server schicken."))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, ps.pt(24))
            Button(st("Done", "Fertig")) { zeigeEigenerCode = false }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("remote.qr.fertig")
        }
        .padding(ps.pt(24))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PrusaColors.background)
    }

    @ViewBuilder private var pruefungsBereich: some View {
        switch pruefung {
        case .unbekannt:
            EmptyView()
        case .prueft:
            HStack(spacing: ps.pt(8)) {
                ProgressView().controlSize(.small)
                Text(st("Connecting…", "Verbinde…"))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textPrimary)
            }
        case .erreichbar:
            HStack(spacing: ps.pt(8)) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(PrusaColors.orange)
                Text(st("Connected. \"Slice now\" now offers remote slicing.",
                        "Verbunden. „Slice now“ bietet jetzt Remote Slicing an."))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(PrusaColors.textPrimary)
            }
            .accessibilityIdentifier("remote.verbunden")
        case .fehler(let meldung):
            Text(meldung)
                .font(.system(size: ps.font(12)))
                .foregroundStyle(PrusaColors.danger)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("remote.fehler")
        }
    }

    private var sicherheitshinweis: some View {
        VStack(alignment: .leading, spacing: ps.pt(6)) {
            Text(st("Security", "Sicherheit"))
                .font(.system(size: ps.font(13), weight: .semibold))
                .foregroundStyle(PrusaColors.textPrimary)
            Text(st(
                "The address goes over the plain network unless it starts with https://. If the server is only reachable inside your home network, that's fine. If you expose it to the open internet, put it behind HTTPS (a reverse proxy or tunnel) and set an access token - otherwise anyone who finds the address can submit slice jobs.",
                "Die Adresse geht unverschlüsselt raus, außer sie beginnt mit https://. Läuft der Server nur im Heimnetz, ist das unproblematisch. Hängst du ihn ins offene Internet, gehört HTTPS davor (Reverse Proxy oder Tunnel) und ein Zugangs-Token gesetzt - sonst kann jeder, der die Adresse findet, Aufträge einreichen."))
                .font(.system(size: ps.font(11)))
                .foregroundStyle(PrusaColors.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(ps.pt(14))
        .background(PrusaColors.panel.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: ps.pt(8)))
    }

    // MARK: - Ablauf

    private func tokenLaden() {
        guard !tokenGeladen else { return }
        tokenGeladen = true
        token = (try? credentials.load()) ?? ""
    }

    private func tokenSichern() {
        if token.isEmpty {
            try? credentials.remove()
        } else {
            try? credentials.save(token)
        }
    }

    private func verbindungPruefen() {
        guard let basis = RemoteSliceClient.normalizedBaseURL(from: serverAdresse) else {
            pruefung = .fehler(st("Invalid server address.", "Ungültige Serveradresse."))
            return
        }
        pruefung = .prueft
        let aktuellesToken = token.isEmpty ? nil : token
        Task {
            let erreichbar = await client.healthCheck(baseURL: basis, token: aktuellesToken)
            pruefung = erreichbar
                ? .erreichbar
                : .fehler(st("Server not reachable.", "Server nicht erreichbar."))
        }
    }

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }
}
