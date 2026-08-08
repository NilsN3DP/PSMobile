# PSMobile Printer Connectivity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide one secure printer setup and send workflow for PrusaLink, Moonraker, and OctoPrint on iOS and Android.

**Architecture:** A common host-neutral contract defines types, normalized addresses, capabilities, discovery candidates, error classes, and upload intent. Each platform supplies secure storage, Bonjour/NSD discovery, and HTTP transport. UI entry points all open the same repository and setup coordinator.

**Tech Stack:** Kotlin Multiplatform rules, Swift URLSession/Network.framework/Keychain, Kotlin HttpURLConnection or platform HTTP/NSD/Keystore, XCTest/JUnit, local mock servers.

## Global Constraints

- Supported types are exactly PrusaLink, Moonraker, and OctoPrint for this program.
- Automatic discovery and manual address input are first-class paths.
- Default transport is HTTPS; insecure HTTP requires an explicit per-printer choice and warning.
- Credentials never appear in metadata JSON, logs, diagnostics, screenshots, test names, or accessibility values.
- A printer may be saved offline only after a typed warning; the UI preserves the exact failure class.
- Upload and optional print start are separate capabilities and actions.
- Every entry point uses one stored printer list and one setup coordinator.

---

### Task 1: Define the common printer host contract

**Files:**
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/PrinterHost.kt`
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/PrinterAddress.kt`
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/PrinterFailure.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/net/PrinterHostContractTest.kt`

**Interfaces:**
- Produces `PrinterHostType`, `PrinterAuth`, `PrinterCapabilities`, `PrinterEndpoint`, `PrinterFailure`, and address normalization.

- [ ] **Step 1: Write failing contract tests.**

```kotlin
@Test fun hostCapabilitiesAreExplicit() {
    assertEquals(PrinterCapabilities(upload = true, start = true), PrinterHostType.PRUSA_LINK.capabilities)
    assertEquals(PrinterCapabilities(upload = true, start = true), PrinterHostType.MOONRAKER.capabilities)
    assertEquals(PrinterCapabilities(upload = true, start = true), PrinterHostType.OCTO_PRINT.capabilities)
}

@Test fun bareHostDefaultsToHttps() {
    assertEquals("https://printer.local", PrinterAddress.normalize("printer.local", allowHttp = false))
}
```

- [ ] **Step 2: Run `./android/gradlew -p android :shared:allTests` and confirm missing-type failures.**
- [ ] **Step 3: Implement serializable metadata types with only `credentialRef`, never secret values.**

```kotlin
data class PrinterEndpoint(
    val id: String,
    val name: String,
    val type: PrinterHostType,
    val baseUrl: String,
    val auth: PrinterAuth,
    val credentialRef: String,
    val presetName: String?,
    val storage: String?,
    val allowInsecureHttp: Boolean,
)
```

- [ ] **Step 4: Add exact failure cases: `NotFound`, `Unreachable`, `TlsRejected`, `AuthenticationFailed`, `WrongHostType`, `InvalidResponse`, `UploadRejected`, and `StorageRejected`.**
- [ ] **Step 5: Run common tests and commit.**

```bash
git add android/shared/src/commonMain/kotlin/de/psmobile/shared/net android/shared/src/commonTest/kotlin/de/psmobile/shared/net
git commit -m "feat(shared): define printer host contract"
```

### Task 2: Add deterministic mock servers and protocol clients

**Files:**
- Create: `testdata/printers/mock_server.py`
- Create: `testdata/printers/fixtures/prusalink-status.json`
- Create: `testdata/printers/fixtures/moonraker-info.json`
- Create: `testdata/printers/fixtures/octoprint-version.json`
- Create: `ios/PSMobile/Networking/MoonrakerClient.swift`
- Create: `ios/PSMobile/Networking/OctoPrintClient.swift`
- Modify: `ios/PSMobile/Networking/PrusaLinkClient.swift`
- Create: `android/app/src/main/java/de/psmobile/net/MoonrakerClient.kt`
- Create: `android/app/src/main/java/de/psmobile/net/OctoPrintClient.kt`
- Modify: `android/app/src/main/java/de/psmobile/net/PrusaLink.kt`

**Interfaces:**
- Consumes: `PrinterEndpoint`, platform credential lookup, local file URL/path.
- Produces platform `probe(endpoint)`, `upload(endpoint,file,remoteName)`, and `start(endpoint,remoteName)` results mapped to `PrinterFailure`.

- [ ] **Step 1: Implement the mock server routes before clients.**

```text
PrusaLink: GET /api/v1/status; PUT /api/v1/files/{storage}/{name}
Moonraker: GET /server/info; POST /server/files/upload; POST /printer/print/start
OctoPrint: GET /api/version; POST /api/files/local with select/print flags
```

- [ ] **Step 2: Add a smoke test command.**

Run: `python testdata/printers/mock_server.py --self-test`

Expected: `9 protocol cases passed`, including 401, 404, 409, 500, and interrupted upload mappings.

- [ ] **Step 3: Write iOS and Android client tests against an injected base URL/transport; assert method, path, headers, multipart fields, and start behavior.**
- [ ] **Step 4: Run the new tests and confirm clients are missing.**
- [ ] **Step 5: Implement clients with bounded connect/read timeouts and a single retry only for a refreshed PrusaLink Digest nonce.**
- [ ] **Step 6: Run all protocol tests; inspect logs to confirm secrets are redacted.**
- [ ] **Step 7: Commit.**

```bash
git add testdata/printers ios/PSMobile/Networking android/app/src/main/java/de/psmobile/net android/app/src/test ios/PSMobileTests
git commit -m "feat: add three printer protocol clients"
```

### Task 3: Build discovery and host fingerprinting

**Files:**
- Create: `ios/PSMobile/Networking/PrinterDiscoveryService.swift`
- Create: `android/app/src/main/java/de/psmobile/net/PrinterDiscoveryService.kt`
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/DiscoveryRules.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/net/DiscoveryRulesTest.kt`
- Modify: `ios/PSMobile/Support/Info.plist`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes DNS-SD/mDNS candidates and protocol probe signatures.
- Produces `DiscoveryCandidate(id, displayName, addresses, advertisedTypes, detectedType, confidence)`.

- [ ] **Step 1: Write failing pure tests for candidate deduplication by normalized host/port, type precedence, and ambiguous results.**
- [ ] **Step 2: Run common tests and confirm failure.**
- [ ] **Step 3: Implement rules for `_prusalink._tcp`, `_octoprint._tcp`, `_http._tcp`, and `_https._tcp`; generic HTTP candidates require fingerprint probes before a type is selected.**
- [ ] **Step 4: Implement iOS discovery with `NWBrowser` and Android discovery with `NsdManager`; emit updates through `AsyncStream` and `Flow`.**
- [ ] **Step 5: Add iOS local-network usage text and Android network permissions without requesting location.**
- [ ] **Step 6: Add fixture-driven tests for duplicate IPv4/IPv6 records and disappearing services.**
- [ ] **Step 7: Commit.**

```bash
git add ios/PSMobile/Networking/PrinterDiscoveryService.swift ios/PSMobile/Support/Info.plist android/app/src/main/java/de/psmobile/net/PrinterDiscoveryService.kt android/app/src/main/AndroidManifest.xml android/shared/src/commonMain/kotlin/de/psmobile/shared/net/DiscoveryRules.kt android/shared/src/commonTest/kotlin/de/psmobile/shared/net/DiscoveryRulesTest.kt
git commit -m "feat: discover local printer hosts"
```

### Task 4: Migrate secure storage to the host-neutral model

**Files:**
- Modify: `ios/PSMobile/Networking/PrinterStore.swift`
- Modify: `ios/PSMobile/Networking/PrinterCredentialStore.swift`
- Modify: `android/app/src/main/java/de/psmobile/net/PrinterStore.kt`
- Modify: `android/app/src/main/java/de/psmobile/net/SecretStore.kt`
- Test: `ios/PSMobileTests/PrinterStoreTests.swift`
- Test: `android/app/src/test/java/de/psmobile/net/PrinterStoreTest.kt`

**Interfaces:**
- Consumes: old PrusaLink-only records.
- Produces: versioned `PrinterEndpoint` metadata plus platform-secure credentials referenced by `credentialRef`.

- [ ] **Step 1: Write migration tests using old API-key and Digest records.**
- [ ] **Step 2: Assert serialized metadata does not contain `apiKey`, `password`, `Authorization`, or secret fixture values.**
- [ ] **Step 3: Implement a one-time v1-to-v2 migration: save secret first, commit metadata second, remove legacy plaintext only after both succeed.**
- [ ] **Step 4: Add default-printer ID and preset association fields; deleting an endpoint deletes its secret.**
- [ ] **Step 5: Run store tests twice to prove migration idempotence.**
- [ ] **Step 6: Commit.**

```bash
git add ios/PSMobile/Networking/PrinterStore.swift ios/PSMobile/Networking/PrinterCredentialStore.swift ios/PSMobileTests/PrinterStoreTests.swift android/app/src/main/java/de/psmobile/net/PrinterStore.kt android/app/src/main/java/de/psmobile/net/SecretStore.kt android/app/src/test/java/de/psmobile/net/PrinterStoreTest.kt
git commit -m "feat: store printer hosts securely"
```

### Task 5: Implement one setup coordinator and all entry points

**Files:**
- Create: `ios/PSMobile/Printers/PrinterSetupCoordinator.swift`
- Modify: `ios/PSMobile/Screens/PrintersView.swift`
- Modify: `ios/PSMobile/Screens/WorkflowStartView.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Create: `android/app/src/main/java/de/psmobile/net/PrinterSetupCoordinator.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/PrintersScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/WorkflowStartScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`

**Interfaces:**
- Consumes: discovery candidates, manual entry, clients, secure repository, available printer profiles.
- Produces one state machine: `choose path -> choose/detect type -> address -> credentials -> probe -> profile mapping -> save/default`.

- [ ] **Step 1: Write state-machine tests for discovered PrusaLink, manually entered Moonraker, wrong OctoPrint fingerprint, offline save, cancel, and retry.**
- [ ] **Step 2: Run tests and confirm the coordinator is missing.**
- [ ] **Step 3: Implement platform coordinators with equivalent states and shared validation rules.**
- [ ] **Step 4: Replace platform-specific add forms with coordinator-driven screens; show discovery and manual entry on the first page.**
- [ ] **Step 5: Wire Start, Simple, Advanced, Add Printer, and Send entry points to the same repository/coordinator.**
- [ ] **Step 6: Add accessibility IDs `printer.entry.start`, `.simple`, `.advanced`, `.list`, `.send`; add UI tests that assert all five reach the same saved list.**
- [ ] **Step 7: Run iOS `PrintersUITests` and Android printer tests.**
- [ ] **Step 8: Commit.**

```bash
git add ios/PSMobile/Printers ios/PSMobile/Screens ios/PSMobileUITests/PrintersUITests.swift android/app/src/main/java/de/psmobile/net/PrinterSetupCoordinator.kt android/app/src/main/java/de/psmobile/ui android/app/src/test
git commit -m "feat: unify printer setup entry points"
```

### Task 6: Integrate send, progress, cancellation, and profile checks

**Files:**
- Modify: `ios/PSMobile/SlicerModel.swift`
- Modify: `ios/PSMobile/Screens/SliceSheet.swift`
- Modify: `ios/PSMobile/Screens/FinalPreviewPanel.swift`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleSliceSheet.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `ios/PSMobileUITests/PrinterSendUITests.swift`
- Test: `android/app/src/test/java/de/psmobile/net/PrinterSendPolicyTest.kt`

**Interfaces:**
- Consumes: current slice revision, exported G-code, selected endpoint, active printer preset.
- Produces upload progress/state, cancellation, profile mismatch warning, and optional start request.

- [ ] **Step 1: Write tests proving stale slice results cannot send and mismatched profiles require explicit continuation.**
- [ ] **Step 2: Write mock-server tests for upload-only, upload-and-start, cancellation, and rejected storage.**
- [ ] **Step 3: Implement a host-neutral send intent with `endpointId`, `remoteName`, `startAfterUpload`, and captured slice revision.**
- [ ] **Step 4: Update both send UIs to show target, profile match, progress, cancel, and final remote path.**
- [ ] **Step 5: Run printer protocol, send policy, and UI suites.**
- [ ] **Step 6: Commit.**

```bash
git add ios/PSMobile/SlicerModel.swift ios/PSMobile/Screens ios/PSMobileUITests/PrinterSendUITests.swift android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui android/app/src/test/java/de/psmobile/net
git commit -m "feat: send slices to configured printer hosts"
```

### Task 7: Close printer gates

**Files:**
- Create: `docs/qa/printer-mock-report.md`
- Create: `docs/qa/prusalink-device-report.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: automated protocol results and real PrusaLink hardware run.
- Produces: truthful host-by-host acceptance status.

- [ ] **Step 1: Run the complete mock matrix for all three hosts on iOS and Android.**
- [ ] **Step 2: On real PrusaLink hardware, execute discovery, API-key or Digest authentication, upload, filename verification, and optional start only after user confirmation.**
- [ ] **Step 3: Record firmware, auth method, storage target, timestamps, app commit, and redacted outcomes.**
- [ ] **Step 4: Label Moonraker/OctoPrint as mock-verified and PrusaLink as hardware-verified only if evidence exists.**
- [ ] **Step 5: Commit reports and journal update.**

```bash
git add docs/qa/printer-mock-report.md docs/qa/prusalink-device-report.md docs/arbeitsjournal.md
git commit -m "test: record printer connectivity gates"
```
