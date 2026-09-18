import Foundation

/// Dependency-freie Zahlen eines final verarbeiteten Layers.
///
/// Die App bildet ihre C-ABI-Datensätze darauf ab; der Unit-Test kann
/// dieselbe Range-Rechnung prüfen, ohne den großen Slicer-Kern zu linken.
struct PreviewLayerMetrics: Equatable {
    let zLower: Double
    let zUpper: Double
    let timeSeconds: Double
    let filamentMm: Double
    let filamentGrams: Double
}

/// Beidseitiger Layerbereich der finalen Vorschau.
struct PreviewRange: Equatable {
    struct Stats: Equatable {
        let timeSeconds: Double
        let filamentMm: Double
        let filamentGrams: Double
        let zLower: Double
        let zUpper: Double
    }

    let layerCount: Int
    private(set) var lower: Int
    private(set) var upper: Int

    init(layerCount: Int) {
        self.layerCount = max(layerCount, 0)
        lower = 0
        upper = max(layerCount - 1, 0)
    }

    var closedRange: ClosedRange<Int> { lower...upper }
    var isEmpty: Bool { layerCount == 0 }

    mutating func setLower(_ value: Int) {
        guard layerCount > 0 else { return }
        lower = min(max(value, 0), upper)
    }

    mutating func setUpper(_ value: Int) {
        guard layerCount > 0 else { return }
        upper = max(min(value, layerCount - 1), lower)
    }

    func stats(in layers: [PreviewLayerMetrics]) -> Stats {
        guard !isEmpty, lower < layers.count, upper < layers.count else {
            return Stats(timeSeconds: 0, filamentMm: 0,
                         filamentGrams: 0, zLower: 0, zUpper: 0)
        }
        let visible = layers[lower...upper]
        return Stats(
            timeSeconds: visible.reduce(0) { $0 + $1.timeSeconds },
            filamentMm: visible.reduce(0) { $0 + $1.filamentMm },
            filamentGrams:
                visible.reduce(0) { $0 + $1.filamentGrams },
            zLower: visible.first?.zLower ?? 0,
            zUpper: visible.last?.zUpper ?? 0)
    }
}
