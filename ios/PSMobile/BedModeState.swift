import Foundation

struct BedActivity: Equatable {
    let index: Int
    let active: Bool
}

/// The mode-independent projection of Core's active-bed flags.
/// Both Simple and Advanced render `BedSelector`, which consumes this exact
/// projection instead of owning a mode-local selected index.
enum BedModeState {
    static func activeIndex(_ beds: [BedActivity]) -> Int {
        guard !beds.isEmpty else { return 0 }
        return beds.firstIndex(where: \.active) ?? 0
    }
}
