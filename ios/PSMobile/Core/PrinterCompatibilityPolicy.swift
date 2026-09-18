/// Targets only printers explicitly linked to the active slicer profile.
enum PrinterCompatibilityPolicy {
    /// Kept generic so the rule can be compiled in the lightweight unit-test
    /// target without pulling the complete PrusaLink networking client in.
    static func matching<T>(
        printers: [T],
        selectedProfile: String?,
        profileName: (T) -> String,
    ) -> [T] {
        guard let selectedProfile,
              !selectedProfile.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return []
        }
        return printers.filter {
            let name = profileName($0).trimmingCharacters(in: .whitespacesAndNewlines)
            return !name.isEmpty && name == selectedProfile
        }
    }
}
