# Experimental Local PrusaLink QR Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in, fail-closed QR/manual pairing flow for local physical PrusaLink printers on Android and iOS without changing firmware or enabling cloud fallback.

**Architecture:** A shared Kotlin model/parser owns strict payload validation and capability filtering. Android and iOS adapters own camera/manual input, secure token storage, local probing, and printer-store integration. Existing PrusaLink status/upload paths are reused; no undocumented command endpoint is introduced.

**Tech Stack:** Kotlin Multiplatform shared module, Android Kotlin/Jetpack UI, SwiftUI, Android Keystore-backed storage, iOS Keychain, native camera/QR APIs where available, XCTest/XCUITest and Gradle tests.

## Global Constraints

- Feature label is exactly `Experimental` and default is disabled.
- Only `type=prusalink-local`, `version=1`, `transport=http` payloads are accepted.
- Default endpoint is `http://192.168.4.1`; public/cloud endpoints and automatic discovery/fallback are rejected.
- WLAN passwords, tokens, and other credentials are never logged or placed in user-facing errors.
- Tokens are stored only in Android Keystore-backed or iOS Keychain-backed storage.
- Firmware/CFW is not changed in this plan; printer-side expiry/revocation is only claimed when the payload/API provides it.

---

### Task 1: Shared QR payload and validation contract

**Files:**
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/LocalPrusaLinkPairing.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/net/LocalPrusaLinkPairingTest.kt`

**Interfaces:**
- `data class LocalPrusaLinkQrPayload(type: String, version: Int, model: String, host: String, port: Int, transport: String, pairingToken: String, capabilities: Set<String>, expiresAtEpochSeconds: Long? = null)`
- `sealed interface LocalPairingValidation { data class Valid(val payload: LocalPrusaLinkQrPayload, val endpoint: String): LocalPairingValidation; data class Invalid(val reason: Reason): LocalPairingValidation }`
- `object LocalPrusaLinkPairing { fun parse(json: String, nowEpochSeconds: Long): LocalPairingValidation; fun validate(payload: LocalPrusaLinkQrPayload, nowEpochSeconds: Long): LocalPairingValidation; fun manual(host: String, port: Int, token: String, model: String, capabilities: Set<String> = emptySet()): LocalPairingValidation }`

- [ ] Write tests for valid payload/default endpoint, wrong type/version/transport, unsupported model, malformed/public/cloud host, invalid ports, missing token, expired `expires_at`, and capability allowlisting.
- [ ] Run `:shared:testDebugUnitTest --tests de.psmobile.shared.net.LocalPrusaLinkPairingTest`; verify RED.
- [ ] Implement strict JSON decoding, normalized endpoint generation, no token in reasons, and no public-address/cloud fallback.
- [ ] Run shared tests; verify GREEN and existing `:shared:allTests` remains green.
- [ ] Commit `feat(shared): validate experimental local pairing payloads`.

### Task 2: Secure token store and local printer persistence

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/net/PrinterStore.kt`
- Create: `android/app/src/main/java/de/psmobile/net/LocalPairingTokenStore.kt`
- Test: `android/app/src/test/java/de/psmobile/net/LocalPairingStoreTest.kt`
- Modify: `ios/PSMobile/Networking/PrinterStore.swift`
- Create: `ios/PSMobile/Networking/LocalPairingTokenStore.swift`
- Test: `ios/PSMobileTests/LocalPairingStoreTests.swift`

**Interfaces:**
- Android `interface LocalPairingTokenStore { fun read(printerId: String): String?; fun write(printerId: String, token: String); fun clear(printerId: String) }`.
- Swift `protocol LocalPairingTokenStore { func read(printerID: String) -> String?; func write(printerID: String, token: String); func clear(printerID: String) }`.
- Printer metadata carries `isLocalExperimental`, `localHost`, `localPort`, `localModel`, and `localCapabilities`; token is never Codable in the printer record.

- [ ] Write RED tests proving metadata persists while token storage is separate, reset clears token/record, and legacy records default to disabled/non-local.
- [ ] Implement Android Keystore-backed storage and iOS Keychain storage behind injectable test doubles.
- [ ] Add local-printer conversion to existing stores without changing cloud/OctoPrint records.
- [ ] Run focused Android tests and iOS unit tests on the Mac; verify GREEN.
- [ ] Commit `feat(pairing): store local printer metadata and tokens securely`.

### Task 3: Local pairing workflow and PrusaLink probe

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/net/PrusaLink.kt`
- Create: `android/app/src/main/java/de/psmobile/net/LocalPairingWorkflow.kt`
- Modify: `ios/PSMobile/Networking/PrusaLinkClient.swift`
- Create: `ios/PSMobile/Networking/LocalPairingWorkflow.swift`
- Test: Android and iOS pairing workflow tests.

**Interfaces:**
- `suspend fun pairLocal(payloadJson: String): PairingResult` / `func pairLocal(payloadJSON: String) async -> PairingResult`.
- `sealed PairingResult { Success(LocalPrinter); Invalid(reason); Offline; Unsupported; Expired }`.
- Workflow sequence: parse → validate → local status probe → capabilities probe → secure token write → printer-store upsert.

- [ ] Write RED tests for valid local pairing, offline printer, unsupported model, expired token, reset/reconnect, and no cloud fallback.
- [ ] Implement bounded timeout/reconnect behavior using existing status transport and token headers only where the documented local API accepts them.
- [ ] Ensure error messages redact host credentials/tokens and remain non-modal for reconnect failures.
- [ ] Run Android and Mac iOS workflow tests; verify GREEN.
- [ ] Commit `feat(pairing): add fail-closed local pairing workflow`.

### Task 4: Android experimental add-printer UI

**Files:**
- Modify: existing Android physical-printer add screen discovered in `android/app/src/main/java/de/psmobile/...`
- Create/modify: local pairing screen/state and manual fallback components.
- Test: Android UI/state tests for scanner unavailable and manual entry.

- [ ] Add an `Experimental` section with `Experimental local connection` and `Scan QR code`.
- [ ] Add scanner adapter boundary; use the platform camera only when available and immediately pass text to the shared parser.
- [ ] Add manual host/port/token fallback and validation errors without token echoing.
- [ ] Show `Experimental`, `Local printer`, `Printer hotspot`, IP, connected/disconnected, pairing active/inactive, `Show QR code again`, and `Reset local pairing`.
- [ ] Disable unsupported actions and preserve local mode without cloud fallback.
- [ ] Run focused Android UI/unit tests and production/preview compile.
- [ ] Commit `feat(android): add experimental local pairing UI`.

### Task 5: iOS experimental add-printer UI and scanner adapter

**Files:**
- Modify: `ios/PSMobile/Screens/PrintersView.swift`
- Create: `ios/PSMobile/Screens/LocalPairingView.swift`
- Create: `ios/PSMobile/Networking/LocalQrScanner.swift`
- Test: `ios/PSMobileTests/LocalPairingTests.swift`, `ios/PSMobileUITests/LocalPairingUITests.swift`

- [ ] Add the same Experimental local-connection entry point and manual fallback.
- [ ] Implement AVFoundation/Vision scanner adapter with a testable text callback; scanner absence exposes manual fields.
- [ ] Add local detail/status/reset UI and accessibility identifiers for all states.
- [ ] Run iPhone/iPad simulator unit/UI tests, including reconnect and no-cloud-fallback assertions.
- [ ] Commit `feat(ios): add experimental local pairing UI`.

### Task 6: Documentation, full verification, and side-build report

**Files:**
- Create: `docs/experimental-local-prusalink-qr-pairing.md`
- Modify: `docs/arbeitsjournal.md`
- Modify: `.superpowers` ignored report for this task.

- [ ] Document payload schema, security guarantees, manual fallback, unsupported firmware behavior, and the no-CFW limitation for printer-side token revocation.
- [ ] Run shared allTests, Android production/preview unit tests and APK builds, iOS simulator builds/tests, credential scans, and `git diff --check`.
- [ ] Verify no token/password appears in source logs, test output, UI strings, or analytics.
- [ ] Record exact commands/results and leave changes unmerged on the side-build branch for acceptance.
- [ ] Commit `docs: document experimental local pairing`.
