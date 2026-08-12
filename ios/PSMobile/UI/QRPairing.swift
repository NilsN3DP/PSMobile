import AVFoundation
import CoreImage.CIFilterBuiltins
import PSMShared

/// Der Typ aus dem gemeinsamen Modul heisst genauso wie die Huelle
/// hier - ohne diesen zweiten Namen verdeckt die eine die andere.
private typealias SharedRemotePairing = PSMShared.RemotePairing
import SwiftUI

/// QR-Pairing fuer Remote Slicing.
///
/// Ein Geraet zeigt seinen eingerichteten Server als QR-Code
/// (QRCodeView), ein zweites scannt ihn (QRScannerView) und
/// uebernimmt Adresse und Token, ohne beides abzutippen - gerade das
/// Token ist zum Abtippen zu lang, um es sich fehlerfrei zuzutrauen.
///
/// Format bewusst eine eigene URL statt JSON: eine URL laesst sich mit
/// URLComponents robust parsen (Kodierung von Sonderzeichen im Token
/// eingeschlossen) und ist genauso kompakt.
///   psmobile-remote://pair?host=<Adresse>&token=<Token>
/// Beides reicht nur noch an das gemeinsame Modul weiter.
///
/// Das Format stand frueher hier - in Swift, mit URLComponents - und
/// seit der Android-Seite ein zweites Mal in Kotlin. Zwei Fassungen
/// derselben Verabredung zwischen zwei Geraeten sind eine zu viel: laeuft
/// eine davon weg, merkt es niemand, bis jemand vor dem falschen Geraet
/// steht und der Code nicht angenommen wird.
enum RemotePairing {
    static func url(host: String, token: String) -> URL? {
        URL(string: SharedRemotePairing.shared.url(host: host, token: token))
    }

    /// - Returns: (Serveradresse, Token oder leer) oder nil, wenn der
    ///   gescannte Code kein PSMobile-Pairing-Code ist - ein QR-Scanner
    ///   liest schliesslich jeden QR-Code, nicht nur eigene.
    static func parse(_ text: String) -> (host: String, token: String)? {
        guard let paar = SharedRemotePairing.shared.parse(text: text) else { return nil }
        return (paar.host, paar.token)
    }
}

/// Zeigt den eigenen Server als QR-Code - fuer ein zweites Geraet zum
/// Abscannen. CIFilter statt einer dritten Bibliothek: iOS bringt einen
/// QR-Generator schon mit, fuer eine kurze URL reicht die Standardgroesse.
struct QRCodeView: View {
    let inhalt: String

    private var bild: UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(inhalt.utf8)
        filter.correctionLevel = "M"
        guard let ausgabe = filter.outputImage else { return nil }
        // Ohne Hochskalieren bleibt der Code nur ein paar Dutzend Pixel
        // gross und wirkt auf einem Bildschirm verwaschen statt scharf.
        let skaliert = ausgabe.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let context = CIContext()
        guard let cg = context.createCGImage(skaliert, from: skaliert.extent) else { return nil }
        return UIImage(cgImage: cg)
    }

    var body: some View {
        if let bild {
            Image(uiImage: bild)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .accessibilityIdentifier("remote.qr.anzeige")
        } else {
            Text(SimpleModeState.shared.text(english: "Cannot create QR code",
                                             german: "QR-Code lässt sich nicht erzeugen"))
                .foregroundStyle(.secondary)
        }
    }
}

/// Kamera-Scanner fuer einen einzelnen QR-Code.
///
/// Bewusst AVCaptureMetadataOutput statt Vision/VNDetectBarcodesRequest:
/// das ist die klassische, seit iOS 7 stabile Route fuer genau diesen
/// einen Zweck, ohne dass ein zweites Bildverarbeitungs-Framework noetig
/// waere.
struct QRScannerView: UIViewControllerRepresentable {
    var onFound: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerViewController {
        let vc = ScannerViewController()
        vc.onFound = onFound
        return vc
    }

    func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {}

    final class ScannerViewController: UIViewController,
        AVCaptureMetadataOutputObjectsDelegate {
        var onFound: ((String) -> Void)?
        private let session = AVCaptureSession()
        private var gemeldet = false

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black

            guard let geraet = AVCaptureDevice.default(for: .video),
                  let eingang = try? AVCaptureDeviceInput(device: geraet),
                  session.canAddInput(eingang)
            else { return }
            session.addInput(eingang)

            let ausgang = AVCaptureMetadataOutput()
            guard session.canAddOutput(ausgang) else { return }
            session.addOutput(ausgang)
            ausgang.setMetadataObjectsDelegate(self, queue: .main)
            ausgang.metadataObjectTypes = [.qr]

            let vorschau = AVCaptureVideoPreviewLayer(session: session)
            vorschau.frame = view.bounds
            vorschau.videoGravity = .resizeAspectFill
            view.layer.addSublayer(vorschau)
            self.vorschauEbene = vorschau
        }

        private var vorschauEbene: AVCaptureVideoPreviewLayer?

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            vorschauEbene?.frame = view.bounds
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            gemeldet = false
            if !session.isRunning {
                DispatchQueue.global(qos: .userInitiated).async { [session] in
                    session.startRunning()
                }
            }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if session.isRunning {
                session.stopRunning()
            }
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput,
                             didOutput metadataObjects: [AVMetadataObject],
                             from connection: AVCaptureConnection) {
            guard !gemeldet,
                  let code = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  code.type == .qr,
                  let text = code.stringValue
            else { return }
            gemeldet = true
            onFound?(text)
        }
    }
}

/// Das Scanner-Blatt mit Rahmen und Abbrechen-Knopf - die reine Kamera-
/// vorschau allein wirkt ohne jede Fuehrung wie ein Programmierfehler.
struct QRScanSheet: View {
    var onCode: (String) -> Void
    var onCancel: () -> Void

    @Environment(\.psScale) private var ps

    var body: some View {
        ZStack {
            QRScannerView { code in
                onCode(code)
            }
            .ignoresSafeArea()

            VStack {
                Spacer()
                RoundedRectangle(cornerRadius: ps.pt(16))
                    .stroke(Color.white, lineWidth: 2)
                    .frame(width: ps.pt(220), height: ps.pt(220))
                Spacer()
                Text(SimpleModeState.shared.text(
                    english: "Point the camera at the QR code shown on the other device.",
                    german: "Kamera auf den QR-Code des anderen Geräts halten."))
                    .font(.system(size: ps.font(13)))
                    .foregroundStyle(.white)
                    .padding(.horizontal, ps.pt(24))
                    .padding(.bottom, ps.pt(12))
                Button(SimpleModeState.shared.text(english: "Cancel", german: "Abbrechen"),
                       action: onCancel)
                    .buttonStyle(.borderedProminent)
                    .tint(.white.opacity(0.25))
                    .foregroundStyle(.white)
                    .padding(.bottom, ps.pt(24))
                    .accessibilityIdentifier("remote.qr.abbrechen")
            }
        }
        .background(Color.black)
    }
}
