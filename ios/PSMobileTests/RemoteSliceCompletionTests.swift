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

    func testRejectedPreviewDoesNotPublishRemoteResult() {
        var published = false

        let didPublish = RemoteSliceCompletion.publishIfPreviewAccepted(
            acceptPreview: { throw TestError.staleResult },
            publish: { published = true })

        XCTAssertFalse(didPublish)
        XCTAssertFalse(published)
    }

    private enum TestError: Error {
        case staleResult
    }
}
