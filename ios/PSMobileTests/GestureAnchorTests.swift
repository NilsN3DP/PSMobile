import CoreGraphics
import XCTest

final class GestureAnchorTests: XCTestCase {
    func testPinchToOneFingerReanchorsWithoutResidualSpan() {
        let state = touchGestureAnchor(points: [CGPoint(x: 100, y: 240)])

        XCTAssertEqual(CGPoint(x: 100, y: 240), state?.point)
        XCTAssertEqual(0, state?.span)
    }
}
