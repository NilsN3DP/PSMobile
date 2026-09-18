import Foundation

/// Der Stand des Designs zu dem Zeitpunkt, an dem der Remote-Job startet.
/// Der Wert darf sich nicht mit nachfolgenden Modell-Aenderungen mitbewegen.
struct RemoteSliceRequest: Sendable {
    let designRevision: UInt64

    static func capture(_ readRevision: () -> UInt64) -> RemoteSliceRequest {
        RemoteSliceRequest(designRevision: readRevision())
    }
}

/// Veröffentlicht einen Remote-Slice nur, nachdem der Kern ihn fuer den
/// beim Start festgehaltenen Designstand akzeptiert hat.
enum RemoteSliceCompletion {
    /// Die drei Werte, die SlicerModel bei einem abgeschlossenen Remote-Job
    /// veroeffentlicht. Ein abgelehnter Kern-Import darf keinen davon wie ein
    /// brauchbares Ergebnis aussehen lassen.
    struct Publication: Equatable {
        enum Status: Equatable {
            case done
            case failed
        }

        let gcodeURL: URL?
        let status: Status
        let lastSliceWasRemote: Bool
    }

    static func publication(
        acceptPreview: () throws -> Void,
        gcodeURL: URL
    ) -> Publication {
        do {
            try acceptPreview()
            return Publication(gcodeURL: gcodeURL, status: .done,
                               lastSliceWasRemote: true)
        } catch {
            return Publication(gcodeURL: nil, status: .failed,
                               lastSliceWasRemote: false)
        }
    }
}
