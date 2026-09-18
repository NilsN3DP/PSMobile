import XCTest

final class RemoteSliceCompletionTests: XCTestCase {

    func testRequestKeepsRevisionCapturedBeforeRemoteWorkStarts() {
        var currentRevision: UInt64 = 41

        let request = RemoteSliceRequest.capture {
            currentRevision
        }
        currentRevision = 42

        XCTAssertEqual(request.designRevision, 41)
    }

    func testAcceptedPreviewPublishesRemoteOutput() {
        let gcodeURL = URL(fileURLWithPath: "/tmp/remote-result.gcode")

        let publication = RemoteSliceCompletion.publication(
            acceptPreview: {}, gcodeURL: gcodeURL)

        XCTAssertEqual(publication.gcodeURL, gcodeURL)
        XCTAssertEqual(publication.status, .done)
        XCTAssertTrue(publication.lastSliceWasRemote)
    }

    func testRejectedPreviewPublishesFailedNonRemoteState() {
        let gcodeURL = URL(fileURLWithPath: "/tmp/stale-result.gcode")

        let publication = RemoteSliceCompletion.publication(
            acceptPreview: { throw TestError.staleResult }, gcodeURL: gcodeURL)

        XCTAssertNil(publication.gcodeURL)
        XCTAssertEqual(publication.status, .failed)
        XCTAssertFalse(publication.lastSliceWasRemote)
    }

    private enum TestError: Error {
        case staleResult
    }
}
