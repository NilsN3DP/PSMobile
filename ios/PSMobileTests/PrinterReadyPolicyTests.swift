import XCTest

final class PrinterReadyPolicyTests: XCTestCase {
    func testEmptySelectionIsNotReady() {
        XCTAssertFalse(PrinterReadyPolicy.isReady(selectedPrinter: nil, installedPrinters: ["PrusaResearch:MK4S:0.4"]))
        XCTAssertFalse(PrinterReadyPolicy.isReady(selectedPrinter: "", installedPrinters: ["PrusaResearch:MK4S:0.4"]))
    }

    func testUnknownSelectionIsNotReady() {
        XCTAssertFalse(PrinterReadyPolicy.isReady(selectedPrinter: "PrusaResearch:COREONE:0.4", installedPrinters: ["PrusaResearch:MK4S:0.4"]))
    }

    func testInstalledSelectionIsReady() {
        XCTAssertTrue(PrinterReadyPolicy.isReady(selectedPrinter: "PrusaResearch:MK4S:0.4", installedPrinters: ["PrusaResearch:MK4S:0.4"]))
    }
}
