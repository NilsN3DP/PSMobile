# PSMobile iOS Reference and Android Parity Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete and approve the polished iOS reference, then bring Android to functional and visual parity without merging into the main branch before explicit acceptance.

**Architecture:** Work proceeds through five separately reviewable plans. Shared project and object state is implemented at the C ABI and common-rule boundaries first; iOS then becomes the approved visual reference, printer connectivity is integrated through one host-neutral model, Android consumes the same behavior contract, and device gates close the program.

**Tech Stack:** C++17/C ABI and PrusaSlicer 2.9.6, Swift 5.9/SwiftUI/XCTest/XCUITest, Kotlin 2.x/Jetpack Compose/Kotlin Multiplatform/JUnit, OpenGL ES viewport, Xcode 26.3, Android SDK 35.

## Global Constraints

- Work only on `codex/ios-android-parity-sidebuild`; do not merge, push, or alter the dirty NAS worktree without explicit approval.
- Treat the current dirty NAS source as read-only input and import only reviewed source/test files through the baseline plan.
- iOS is the visual and interaction reference; Android deviations require a documented OS-specific reason.
- Simple and Advanced use one project, selection, bed, printer, favorites, and configuration state.
- Physical printer hosts are PrusaLink, Moonraker, and OctoPrint; automatic discovery and manual address entry are both mandatory.
- Printer entry points exist on Start, Simple, Advanced, Add Printer, and Send screens.
- Advanced landscape uses a right inspector; portrait moves that inspector below the viewer.
- Simple includes the shared multi-bed slider and arrange logic.
- Touch targets are at least 44 pt on iOS and 48 dp on Android.
- Secrets stay in Keychain/Keystore and never enter logs, screenshots, diagnostics, fixtures, or commits.
- Append evidence to `docs/arbeitsjournal.md` after every accepted task.
- Every behavior change follows red-green-refactor and ends in a focused commit.

---

## Execution Order

1. [`2026-08-09-baseline-core-state-plan.md`](2026-08-09-baseline-core-state-plan.md)
2. [`2026-08-09-ios-reference-ui-plan.md`](2026-08-09-ios-reference-ui-plan.md)
3. [`2026-08-09-printer-connectivity-plan.md`](2026-08-09-printer-connectivity-plan.md)
4. [`2026-08-09-android-parity-plan.md`](2026-08-09-android-parity-plan.md)
5. [`2026-08-09-device-release-validation-plan.md`](2026-08-09-device-release-validation-plan.md)

Each plan must finish with a clean worktree, passing tests for its touched layers, an updated work journal, and a reviewer checkpoint. A failed checkpoint stops later plans; it does not authorize changes to the main branch.

## Requirement Traceability

| Lastenheft requirements | Implemented and verified by |
| --- | --- |
| ISO-01, ISO-04 | Baseline Tasks 1 and 7; Validation Tasks 1 and 7 |
| ISO-02, ISO-03 | Baseline Task 2; iOS/Android preview install checks in Validation Tasks 3-6 |
| ISO-05 | Validation Task 7 and this master plan Task 5 |
| UI-01, UI-06 | iOS Task 1; Android Task 1; screenshot gates |
| UI-02 | iOS Task 1 and Task 7; Printer Task 5; Android Task 1 and Task 7 |
| UI-03, UI-04, UI-05 | iOS Tasks 6-7; Android Tasks 5-7; parity matrix |
| CTX-01, CTX-02, CTX-03 | Baseline Tasks 3-4; iOS Task 3; Android Task 3 |
| FAV-01, FAV-02, FAV-03, FAV-04 | Baseline Tasks 3-4; iOS Tasks 2-3; Android Tasks 2-3 |
| CAM-01, CAM-02, CAM-05 | iOS Task 5; Android Task 5 |
| CAM-03, CAM-04 | iOS Tasks 5 and 7; Android Tasks 5 and 7; Validation Tasks 5-6 |
| BED-01 through BED-05 | Baseline Task 5; iOS Task 4; Android Task 4; device rotation gates |
| PRN-01 through PRN-10 | Printer Tasks 1-7; device PrusaLink gates |
| IOS-01, IOS-02, IOS-03 | iOS Task 6 |
| IOS-04, IOS-05, IOS-06 | iOS Tasks 6-7; Validation Tasks 3, 5, and 7 |
| AND-01, AND-03 | Android Tasks 1-5 and 7; parity matrix |
| AND-02 | Android Task 6; Validation Tasks 4 and 6 |
| AND-04 | Android Tasks 5-6; Validation Tasks 4 and 6 |
| ERR-01, ERR-03 | Baseline Task 6; Printer Task 6; device repeated-cycle gates |
| ERR-02 | Printer Tasks 1-6 |
| ERR-04 | iOS Task 6; Android Task 6; Validation Tasks 1-7 |

### Task 1: Establish the side-build baseline and shared contracts

**Files:**
- Execute: `docs/superpowers/plans/2026-08-09-baseline-core-state-plan.md`
- Verify: `core/test/psm_contract_tests.cpp`
- Verify: `android/shared/src/commonTest/kotlin/de/psmobile/shared/rules/WorkspaceContractTest.kt`

**Interfaces:**
- Consumes: committed base `61c1b47` plus the reviewed dirty-source manifest.
- Produces: object override C ABI, common inspector/bed/favorites rules, and reproducible Windows/Mac baselines.

- [ ] **Step 1: Execute every checkbox in the baseline/core plan in order.**
- [ ] **Step 2: Confirm its final commit and test evidence in `docs/arbeitsjournal.md`.**
- [ ] **Step 3: Stop for review if any dirty-source file cannot be attributed or tested.**

### Task 2: Finish and approve the iOS reference shell

**Files:**
- Execute: `docs/superpowers/plans/2026-08-09-ios-reference-ui-plan.md`
- Verify: `ios/PSMobileUITests/ReferenceWorkspaceUITests.swift`

**Interfaces:**
- Consumes: shared contracts from Task 1.
- Produces: accepted iOS landscape/portrait screenshots and stable navigation/state behavior.

- [ ] **Step 1: Execute every checkbox in the iOS plan.**
- [ ] **Step 2: Review the screenshot tour on iPad Pro simulator and iPad Pro 2020.**
- [ ] **Step 3: Record Gate 2 as accepted before beginning Android visual parity.**

### Task 3: Complete the host-neutral printer workflow

**Files:**
- Execute: `docs/superpowers/plans/2026-08-09-printer-connectivity-plan.md`
- Verify: `android/shared/src/commonTest/kotlin/de/psmobile/shared/net/PrinterHostContractTest.kt`

**Interfaces:**
- Consumes: the approved iOS shell and shared state contracts.
- Produces: PrusaLink, Moonraker, and OctoPrint discovery, setup, storage, probe, upload, and optional start behavior.

- [ ] **Step 1: Execute the printer plan through mock-server acceptance.**
- [ ] **Step 2: Run the real PrusaLink gate on iPad Pro 2020.**
- [ ] **Step 3: Keep Moonraker and OctoPrint labeled mock-verified until real hardware evidence exists.**

### Task 4: Implement Android parity against the frozen iOS reference

**Files:**
- Execute: `docs/superpowers/plans/2026-08-09-android-parity-plan.md`
- Verify: `docs/qa/parity-matrix.md`

**Interfaces:**
- Consumes: approved iOS screenshots, shared contracts, and printer host model.
- Produces: Android screens and flows with one-to-one reference evidence or documented platform exceptions.

- [ ] **Step 1: Execute every checkbox in the Android plan.**
- [ ] **Step 2: Fill every parity-matrix row with test and screenshot evidence.**
- [ ] **Step 3: Stop if an Android-only state fork is introduced without a contract test.**

### Task 5: Run device, performance, security, and release gates

**Files:**
- Execute: `docs/superpowers/plans/2026-08-09-device-release-validation-plan.md`
- Produce: `docs/qa/final-acceptance-report.md`

**Interfaces:**
- Consumes: iOS and Android preview builds plus all automated evidence.
- Produces: Gate 0-7 result, residual-risk list, and merge/cherry-pick recommendation.

- [ ] **Step 1: Execute all simulator, emulator, iPad, Samsung, and PrusaLink gates.**
- [ ] **Step 2: Verify the final branch is clean and contains no secrets or generated device data.**
- [ ] **Step 3: Present the acceptance report; do not merge until the user explicitly selects an integration option.**
