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
    @discardableResult
    static func publishIfPreviewAccepted(
        acceptPreview: () throws -> Void,
        publish: () -> Void
    ) -> Bool {
        do {
            try acceptPreview()
            publish()
            return true
        } catch {
            return false
        }
    }
}
