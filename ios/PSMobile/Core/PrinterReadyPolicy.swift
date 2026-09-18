import Foundation

/// Determines whether the workspace has a printer that can be used safely.
/// Kept independent from the slicer core so the rule can be tested without
/// loading the native engine.
enum PrinterReadyPolicy {
    static func isReady(selectedPrinter: String?, installedPrinters: Set<String>) -> Bool {
        guard let selectedPrinter,
              !selectedPrinter.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        return installedPrinters.contains(selectedPrinter)
    }
}
