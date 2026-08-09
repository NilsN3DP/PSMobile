import Foundation
import PSMShared

/// Optionale, lokale Testinjektion. In produktiven Builds gibt es weder einen
/// vorkonfigurierten Server noch Zugangsdaten: beide Werte müssen zur Laufzeit
/// gesetzt werden (z. B. als Xcode-Scheme-Umgebungsvariablen) oder über die UI.
enum RemoteSliceDefaults {
    private static let hostEnvironmentKey = "PSM_REMOTE_SLICE_HOST"
    private static let tokenEnvironmentKey = "PSM_REMOTE_SLICE_TOKEN"

    static func seedIfNeeded() {
        let environment = ProcessInfo.processInfo.environment

        if let token = environment[tokenEnvironmentKey], !token.isEmpty {
            try? RemoteSliceCredentialStore().save(token)
        }

        guard let host = environment[hostEnvironmentKey], !host.isEmpty,
              (UserDefaults.standard.string(forKey: SlicerModel.remoteSliceHostKey) ?? "").isEmpty
        else { return }
        UserDefaults.standard.set(host, forKey: SlicerModel.remoteSliceHostKey)
    }
}
