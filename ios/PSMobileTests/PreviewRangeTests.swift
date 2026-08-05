import XCTest

final class PreviewRangeTests: XCTestCase {

    func testBeideGrenzenBleibenImFinalenLayerbereich() {
        var range = PreviewRange(layerCount: 12)

        range.setLower(4)
        range.setUpper(9)

        XCTAssertEqual(range.closedRange, 4...9)
        range.setLower(20)
        XCTAssertEqual(range.closedRange, 9...9)
        range.setUpper(-3)
        XCTAssertEqual(range.closedRange, 9...9)
    }

    func testSichtbareStatistikSummiertNurFinaleLayer() {
        let layers = [
            PreviewLayerMetrics(zLower: 0, zUpper: 0.2,
                                timeSeconds: 2, filamentMm: 4,
                                filamentGrams: 0.01),
            PreviewLayerMetrics(zLower: 0.2, zUpper: 0.4,
                                timeSeconds: 3, filamentMm: 5,
                                filamentGrams: 0.02),
            PreviewLayerMetrics(zLower: 0.4, zUpper: 0.6,
                                timeSeconds: 7, filamentMm: 8,
                                filamentGrams: 0.03),
        ]
        var range = PreviewRange(layerCount: layers.count)
        range.setLower(1)
        range.setUpper(2)

        let stats = range.stats(in: layers)

        XCTAssertEqual(stats.timeSeconds, 10, accuracy: 0.0001)
        XCTAssertEqual(stats.filamentMm, 13, accuracy: 0.0001)
        XCTAssertEqual(stats.filamentGrams, 0.05, accuracy: 0.0001)
        XCTAssertEqual(stats.zLower, 0.2, accuracy: 0.0001)
        XCTAssertEqual(stats.zUpper, 0.6, accuracy: 0.0001)
    }
}
