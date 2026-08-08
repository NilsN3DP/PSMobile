# PSMobile Device and Release Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the side-build on automated targets, iPad Pro 2020, Samsung Tab S5e, and real PrusaLink hardware, then produce a no-surprises integration recommendation.

**Architecture:** Automated gates run before hardware gates. Every run records commit, environment, fixture, command, result, and artifact hash. Hardware statements require hardware evidence; simulator/emulator results are never promoted to device claims.

**Tech Stack:** CMake/CTest, Gradle/JUnit/instrumentation, adb, xcodebuild/XCTest/XCUITest, Instruments/Xcode device logs, SHA-256 manifests, Markdown evidence.

## Global Constraints

- Required physical devices: iPad Pro 2020 and Samsung Tab S5e.
- Required real printer: available PrusaLink device; Moonraker and OctoPrint remain mock-only unless hardware is supplied.
- Reference preview target includes approximately 1.37 million movements.
- Run 20 repeated slice cycles without process restart.
- Zero crashes, ANRs, silent process kills, stale exports/sends, secret leaks, or unexplained screenshot differences.
- No merge, push, tag, or release publication occurs during validation.
- Final integration is a user decision after Gate 7.

---

### Task 1: Create the reproducible validation harness

**Files:**
- Create: `build/scripts/run-sidebuild-validation.ps1`
- Create: `build/scripts/validate-evidence.py`
- Create: `docs/qa/evidence-schema.json`
- Test: `build/scripts/tests/test_validate_evidence.py`

**Interfaces:**
- Consumes: platform test commands and artifact paths.
- Produces `docs/qa/runs/<timestamp>/run.json`, logs, screenshots, and SHA-256 manifest.

- [ ] **Step 1: Write failing schema tests for missing commit, dirty tree, device ID, command, exit code, or artifact hash.**
- [ ] **Step 2: Run `python -m unittest build.scripts.tests.test_validate_evidence` and confirm failure.**
- [ ] **Step 3: Implement the harness with immutable run directories and redaction of values matching password/API-key/token headers.**

```json
{
  "commit": "40-hex-sha",
  "clean": true,
  "target": "ios-simulator|android-emulator|ipad-pro-2020|samsung-tab-s5e|prusalink",
  "commands": [{"argv": [], "exit_code": 0, "log_sha256": "64-hex"}],
  "artifacts": [{"path": "relative/path", "sha256": "64-hex"}]
}
```

- [ ] **Step 4: Run the validator against one valid and one deliberately incomplete fixture.**
- [ ] **Step 5: Commit.**

```bash
git add build/scripts/run-sidebuild-validation.ps1 build/scripts/validate-evidence.py build/scripts/tests/test_validate_evidence.py docs/qa/evidence-schema.json
git commit -m "test: add side-build evidence harness"
```

### Task 2: Run host, Android JVM, and static security gates

**Files:**
- Create: `docs/qa/gate-1-automated.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: clean side-build commit.
- Produces: core/shared/JVM/security evidence.

- [ ] **Step 1: Run core and build-script tests.**

```powershell
cmake --build build-out/host --target psm_contract_tests
& build-out/host/core/test/psm_contract_tests
python -m unittest discover -s build/scripts/tests -p 'test_*.py'
python build/scripts/feature-report.py --check
```

- [ ] **Step 2: Run shared and Android JVM tests.**

Run: `./android/gradlew -p android :shared:allTests :app:testDebugUnitTest`

- [ ] **Step 3: Scan tracked content for secret patterns and reject any match outside explicit redacted test fixtures.**

```powershell
git grep -n -I -E '(X-Api-Key: [^<]|Authorization: (Basic|Digest|Bearer) [^<]|password["'']?\s*[:=]\s*["''][^<])'
```

Expected: no output.

- [ ] **Step 4: Record commands, test counts, duration, and hashes in `docs/qa/gate-1-automated.md`.**
- [ ] **Step 5: Commit evidence.**

```bash
git add docs/qa/gate-1-automated.md docs/arbeitsjournal.md
git commit -m "test: record automated validation gate"
```

### Task 3: Run iOS simulator reference and restart gates

**Files:**
- Create: `docs/qa/gate-2-ios-simulator.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: Mac side-build worktree at `/Volumes/Macintosh_HD/Users/user289137/psmobile-ios-android-parity-sidebuild` and frozen screenshot tests.
- Produces: full XCTest/XCUITest and screenshot evidence.

- [ ] **Step 1: Transfer a git bundle of the clean local branch to the Mac and create/update only the named remote side-build worktree.**

```powershell
git bundle create psmobile-sidebuild.bundle codex/ios-android-parity-sidebuild
scp -i "$env:USERPROFILE/.ssh/macincloud2" psmobile-sidebuild.bundle user289137@FF738.macincloud.com:/Volumes/Macintosh_HD/Users/user289137/
```

- [ ] **Step 2: On the Mac, fetch the bundle, regenerate Xcode project, and build.**

```bash
git -C /Volumes/Macintosh_HD/Users/user289137/psmobile fetch ../psmobile-sidebuild.bundle codex/ios-android-parity-sidebuild
git -C /Volumes/Macintosh_HD/Users/user289137/psmobile worktree add --force /Volumes/Macintosh_HD/Users/user289137/psmobile-ios-android-parity-sidebuild FETCH_HEAD
cd /Volumes/Macintosh_HD/Users/user289137/psmobile-ios-android-parity-sidebuild/ios
xcodegen generate
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' build
```

- [ ] **Step 3: Run unit tests, functional UI tests, and screenshot tour in separate invocations so restart/crash behavior is observable.**
- [ ] **Step 4: Confirm normal test relaunches never show the crash dialog and an injected abnormal marker shows it exactly once.**
- [ ] **Step 5: Record results and screenshot hashes in `docs/qa/gate-2-ios-simulator.md`.**
- [ ] **Step 6: Commit evidence after copying only logs/reports, not DerivedData.**

### Task 4: Run Android emulator regression and screenshot gates

**Files:**
- Create: `docs/qa/gate-4-android-emulator.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: x86_64 emulator and deterministic screenshot tour.
- Produces: instrumentation, lifecycle, and visual parity evidence.

- [ ] **Step 1: Assemble and install debug/test APKs.**

Run: `./android/gradlew -p android :app:assembleDebug :app:assembleDebugAndroidTest`

- [ ] **Step 2: Run instrumentation with animations disabled and fixed locale/font scale.**

Run: `./android/gradlew -p android :app:connectedDebugAndroidTest`

- [ ] **Step 3: Execute portrait/landscape rotation during import, selection, slicing, preview, printer setup, and mode switches.**
- [ ] **Step 4: Compare screenshot-tour hashes/visual reports against the parity matrix and reject unexplained differences.**
- [ ] **Step 5: Record results in `docs/qa/gate-4-android-emulator.md` and commit.**

### Task 5: Run iPad Pro 2020 memory and workflow acceptance

**Files:**
- Create: `docs/qa/gate-3-ipad-pro-2020.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: signed iOS preview build, 1.37M-movement fixture, real PrusaLink endpoint.
- Produces: device-only memory, lifecycle, Pencil, orientation, and printer evidence.

- [ ] **Step 1: Record device model identifier, iPadOS version, free storage, build commit, and fixture SHA-256.**
- [ ] **Step 2: Run the shared reference flow in portrait and landscape: setup, import, beds, object overrides, favorites, slice, preview, center camera, export, PrusaLink upload, save, relaunch, restore.**
- [ ] **Step 3: Load the 1.37M-movement preview and record peak resident memory, load time, interaction responsiveness, and any Jetsam entry.**
- [ ] **Step 4: Run 20 slice/preview cycles without process restart and verify stale results never export or send.**
- [ ] **Step 5: Exercise Apple Pencil hit targets and scrolling without relying on hover.**
- [ ] **Step 6: Record pass/fail and attach redacted device logs in `docs/qa/gate-3-ipad-pro-2020.md`.**
- [ ] **Step 7: Commit only the report and redacted artifacts.**

### Task 6: Run Samsung Tab S5e acceptance

**Files:**
- Create: `docs/qa/gate-5-samsung-tab-s5e.md`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: Android preview APK, same fixture hashes and reference flow.
- Produces: real-device performance, lifecycle, storage, and PrusaLink evidence.

- [ ] **Step 1: Record `adb shell getprop` device/OS data, RAM class, storage, build commit, and fixture hashes.**
- [ ] **Step 2: Run the reference flow in portrait and landscape, including background/foreground and process recreation after saved-state checkpoints.**
- [ ] **Step 3: Run the 1.37M-movement preview and 20 repeated slice cycles; collect `dumpsys meminfo`, frame timing, logcat, and ANR evidence.**
- [ ] **Step 4: Verify document picker, notification, foreground service, back navigation, Keystore persistence, and PrusaLink upload.**
- [ ] **Step 5: Confirm no touch target is below 48 dp and keyboard focus reaches every setup/settings control.**
- [ ] **Step 6: Record and commit `docs/qa/gate-5-samsung-tab-s5e.md`.**

### Task 7: Produce final Gate 0-7 acceptance report

**Files:**
- Create: `docs/qa/final-acceptance-report.md`
- Modify: `docs/feature-matrix.json`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: all gate reports, parity matrix, test logs, screenshot manifests, and clean branch history.
- Produces: factual integration options with residual risk and exact commit range.

- [ ] **Step 1: Validate every evidence run against `docs/qa/evidence-schema.json`.**
- [ ] **Step 2: Create a Gate 0-7 table with status `PASS`, `FAIL`, or `NOT RUN`; never infer hardware results.**
- [ ] **Step 3: List every remaining parity exception, mock-only printer claim, performance limit, license/store decision, and rollback point.**
- [ ] **Step 4: Update `docs/feature-matrix.json` only where evidence supports the new state.**
- [ ] **Step 5: Run final verification.**

```powershell
python build/scripts/feature-report.py --check
python build/scripts/validate-evidence.py docs/qa/runs
git diff --check
git status --short
git log --oneline --decorate 61c1b47..HEAD
```

Expected: validators pass; the worktree is clean after the report commit.

- [ ] **Step 6: Commit the final report.**

```bash
git add docs/qa/final-acceptance-report.md docs/feature-matrix.json docs/arbeitsjournal.md
git commit -m "docs: complete side-build acceptance report"
```

- [ ] **Step 7: Present three non-destructive choices to the user: keep branch, cherry-pick approved blocks, or merge the complete branch. Perform none without explicit selection.**
