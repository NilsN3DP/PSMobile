import XCTest

final class PrinterCredentialStoreTests: XCTestCase {
    private let host = "test-printer.psmobile.invalid"

    override func tearDownWithError() throws {
        try PrinterCredentialStore().remove(host: host, mode: .apiKey)
    }

    func testCredentialsUseKeychainNotUserDefaults() throws {
        let store = PrinterCredentialStore()
        try store.save(.init(host: host, mode: .apiKey, secret: "test-secret"))

        XCTAssertNil(UserDefaults.standard.string(forKey: "printer.apiKey"))
        XCTAssertEqual(try store.load(host: host, mode: .apiKey)?.secret, "test-secret")
    }
}
