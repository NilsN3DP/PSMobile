import XCTest

final class BedModeStateTests: XCTestCase {
    func testSimpleAdvancedSimpleConsumesTheSameActiveCoreBed() {
        let coreBeds = [
            BedActivity(index: 0, active: false),
            BedActivity(index: 1, active: true),
        ]

        let simpleBefore = BedModeState.activeIndex(coreBeds)
        let advanced = BedModeState.activeIndex(coreBeds)
        let simpleAfter = BedModeState.activeIndex(coreBeds)

        XCTAssertEqual(simpleBefore, 1)
        XCTAssertEqual(advanced, simpleBefore)
        XCTAssertEqual(simpleAfter, simpleBefore)
    }
}
