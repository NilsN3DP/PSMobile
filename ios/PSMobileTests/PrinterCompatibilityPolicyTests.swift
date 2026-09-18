import XCTest

final class PrinterCompatibilityPolicyTests: XCTestCase {
    private struct LinkedPrinter { let name: String; let profile: String }

    private func printer(_ name: String, profile: String) -> LinkedPrinter {
        LinkedPrinter(name: name, profile: profile)
    }

    func testOnlyMatchingProfileIsReturned() {
        let result = PrinterCompatibilityPolicy.matching(
            printers: [printer("MK4", profile: "PrusaResearch:MK4:0.4"),
                       printer("XL", profile: "PrusaResearch:XL:0.4")],
            selectedProfile: "PrusaResearch:MK4:0.4",
            profileName: { $0.profile })
        XCTAssertEqual(result.map(\.name), ["MK4"])
    }

    func testNoMatchDoesNotFallBackToAllPrinters() {
        let result = PrinterCompatibilityPolicy.matching(
            printers: [printer("MK4", profile: "PrusaResearch:MK4:0.4")],
            selectedProfile: "PrusaResearch:COREONE:0.4",
            profileName: { $0.profile })
        XCTAssertTrue(result.isEmpty)
    }

    func testEmptyProfileReturnsNoTargets() {
        let result = PrinterCompatibilityPolicy.matching(
            printers: [printer("MK4", profile: "PrusaResearch:MK4:0.4")],
            selectedProfile: "",
            profileName: { $0.profile })
        XCTAssertTrue(result.isEmpty)
    }
}
