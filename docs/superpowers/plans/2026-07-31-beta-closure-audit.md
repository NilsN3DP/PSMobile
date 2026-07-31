# Android Beta Closure Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every Android-internal requirement discussed for the beta and leave only clearly external hardware, legal, and iOS gates visible.

**Architecture:** The audit treats existing source code as unproven until a unit, native-contract, or emulator evidence path confirms it. It does not invent hardware results: PrusaLink and low-memory device claims remain external until a real device is supplied. Simple and Advanced continue to share one `SlicerService` session.

**Tech Stack:** Kotlin, Jetpack Compose, C++ Core/JNI, Gradle, Python report tests, Android x86_64 emulator.

## Global Constraints

- Android is the release target; iOS remains explicitly deferred.
- ZIP import remains excluded by decision.
- No status is promoted without a reproducible command or emulator trace.
- Existing user worktree changes are preserved; Git object-store permissions currently prevent commits.

---

### Task 1: Reconcile all prior requirements with executable evidence

**Files:**
- Modify: `docs/03-roadmap.md`
- Modify: `docs/09-fehlerliste.md`
- Modify: `docs/feature-matrix.json`
- Create: `docs/release/android-beta-closure-audit.md`
- Test: `build/scripts/tests/test_feature_reports.py`

- [ ] Inventory 3MF decision import, project printer selection, direct multi-bed selection, bed locks, Advanced wizard, profile search, nozzle selection, Simple workspace, responsive layout, export/PrusaLink, and stability.
- [ ] Separate internal verifications from hardware-only and release/legal gates.
- [ ] Make every remaining entry name its exact required evidence.
- [ ] Run `python -m pytest build/scripts/tests/test_feature_reports.py -q`.

### Task 2: Verify and correct Simple Mode state semantics

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt`

- [ ] Add failing state tests for the real support and adhesion mapping.
- [ ] Ensure each Simple setting maps only to the same configuration keys as Advanced quick settings.
- [ ] Run focused Simple tests and Android compilation.

### Task 3: Run complete Android and emulator release smoke paths

**Files:**
- Modify: `docs/release/android-v1-gate.md`
- Create: `.superpowers/sdd/2026-07-31-beta-closure-audit/emulator-report.md`

- [ ] Build `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` from the local Gradle mirror.
- [ ] Install the generated APK and run start choice, Simple workspace, material, printer, Supports, Advanced entry, 3MF import selection, multi-bed, slice/preview/export/cancel smoke paths where deterministic emulator fixtures exist.
- [ ] Scan logcat for Java and native crashes after the run.

### Task 4: Publish closure status without false claims

**Files:**
- Modify: `docs/feature-matrix.json`
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `docs/release/android-beta-closure-audit.md`

- [ ] Mark internal beta requirements as tested only with evidence from Tasks 1–3.
- [ ] List only unavoidable external gates: physical ARM memory tests, real PrusaLink API-key/Digest/HTTP behavior, release licensing, and intentionally deferred iOS.
- [ ] Run report tests and `git diff --check`.
