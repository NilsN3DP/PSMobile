# PSMobile Android Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Android to functional and visual parity with the approved iOS reference while retaining only necessary Android system integrations.

**Architecture:** Compose receives the same shell regions and common contracts as SwiftUI. `SlicerService` remains the single state owner; composables render state and dispatch actions. Screenshot comparison is reference-driven, and each intentional platform difference is entered in the parity matrix.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose Material 3, Kotlin Coroutines/Flow, Kotlin Multiplatform rules, Android SDK 35, JUnit and instrumentation tests.

## Global Constraints

- Target device is Samsung Tab S5e in portrait and landscape; x86_64 emulator is the fast regression target.
- Do not copy SwiftUI structure mechanically when Compose lifecycle or Android navigation requires a different implementation.
- Match visible hierarchy, spacing, sizing, color roles, information density, and interaction order to the frozen iOS screenshots.
- One `SlicerService` owns project, selection, bed, favorites, printer, slice, and preview state.
- Simple includes the full shared bed strip and arrange behavior.
- Advanced uses a trailing inspector in landscape and a bottom inspector in portrait.
- Every touch target is at least 48 dp and supports keyboard/focus navigation.
- A parity row is complete only with an automated test and screenshot or a documented OS exception.

---

### Task 1: Create the Android adaptive workspace shell

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/WorkspaceShell.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/WorkspaceLayout.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/WorkspaceLayoutTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`

**Interfaces:**
- Consumes: window width/height, mode, header/tools/viewport/inspector/bed-strip slots.
- Produces `InspectorPlacement.TRAILING|BOTTOM` and one shell composable.

- [ ] **Step 1: Write failing layout tests matching the iOS reference breakpoints.**

```kotlin
@Test fun tabletLandscapeUsesTrailingInspector() = assertEquals(
    InspectorPlacement.TRAILING,
    WorkspaceLayout.inspectorPlacement(widthDp = 1280, heightDp = 800, isTablet = true),
)

@Test fun portraitUsesBottomInspector() = assertEquals(
    InspectorPlacement.BOTTOM,
    WorkspaceLayout.inspectorPlacement(widthDp = 800, heightDp = 1280, isTablet = true),
)
```

- [ ] **Step 2: Run `./android/gradlew -p android :app:testDebugUnitTest --tests de.psmobile.ui.WorkspaceLayoutTest` and confirm failure.**
- [ ] **Step 3: Implement the pure layout rule and `WorkspaceShell` slots.**
- [ ] **Step 4: Move Simple and Advanced outer structure into the shell; remove duplicate global navigation from inner panels.**
- [ ] **Step 5: Run layout, Simple mode, and settings layout tests.**
- [ ] **Step 6: Commit.**

```bash
git add android/app/src/main/java/de/psmobile/ui/WorkspaceShell.kt android/app/src/main/java/de/psmobile/ui/WorkspaceLayout.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/test/java/de/psmobile/ui
git commit -m "feat(android): add adaptive workspace shell"
```

### Task 2: Move favorites into shared application state

**Files:**
- Create: `android/app/src/main/java/de/psmobile/settings/FavoriteSettingsRepository.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt`
- Test: `android/app/src/test/java/de/psmobile/settings/FavoriteSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: common `FavoriteSettingRules` and settings catalog keys.
- Produces `StateFlow<Set<String>> favorites`, `toggleFavorite(key)`, and sanitized catalog order.

- [ ] **Step 1: Write repository tests for persistence, stable order, and deleted keys.**
- [ ] **Step 2: Run the focused test and confirm the repository is missing.**
- [ ] **Step 3: Implement the repository on SharedPreferences and inject it into `SlicerService`.**
- [ ] **Step 4: Replace composable-local favorites state with collected service state; keep star controls and Favorites page behavior unchanged.**
- [ ] **Step 5: Run `FavoriteSettingsTest`, repository tests, and `SettingsLayoutTest`.**
- [ ] **Step 6: Commit.**

```bash
git add android/app/src/main/java/de/psmobile/settings android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt android/app/src/test
git commit -m "refactor(android): centralize favorite settings state"
```

### Task 3: Implement the contextual inspector and object overrides

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/jni/psm_jni.cpp`
- Create: `android/app/src/main/java/de/psmobile/ui/ContextInspector.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/ObjectOverrideField.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/ContextInspectorStateTest.kt`

**Interfaces:**
- Consumes: shared `InspectorContract`, selected object ID, favorites flow, C ABI overrides.
- Produces JNI/Kotlin functions `objectConfig`, `setObjectConfig`, `resetObjectConfig`, `isObjectConfigOverridden`.

- [ ] **Step 1: Write failing state tests for project scope, object scope, inherited value, overridden value, and reset.**
- [ ] **Step 2: Run focused JVM tests and confirm missing APIs.**
- [ ] **Step 3: Add JNI bindings that translate `PSM_ERR_NOT_FOUND` and `PSM_ERR_INVALID_ARG` through the existing `PsmCore` error mechanism.**

```kotlin
external fun nativeObjectConfig(handle: Long, objectId: Int, key: String): String
external fun nativeSetObjectConfig(handle: Long, objectId: Int, key: String, value: String)
external fun nativeResetObjectConfig(handle: Long, objectId: Int, key: String)
external fun nativeIsObjectConfigOverridden(handle: Long, objectId: Int, key: String): Boolean
```

- [ ] **Step 4: Implement `ContextInspector`: no selection renders project favorites; selection renders transform tools and favorite overrides.**
- [ ] **Step 5: Mount it in the shell trailing/bottom slot and preserve state across rotation and mode changes.**
- [ ] **Step 6: Run core contract, JVM, and Compose instrumentation tests.**
- [ ] **Step 7: Commit.**

```bash
git add android/jni/psm_jni.cpp android/app/src/main/java/de/psmobile/core/PsmCore.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui/ContextInspector.kt android/app/src/main/java/de/psmobile/ui/ObjectOverrideField.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/test
git commit -m "feat(android): add contextual object inspector"
```

### Task 4: Put the shared bed strip and arrange logic in Simple

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/BedStrip.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/BedStripStateTest.kt`
- Test: `android/app/src/androidTest/java/de/psmobile/SharedBedStripTest.kt`

**Interfaces:**
- Consumes: common `BedStripContract` and existing service bed mutations.
- Produces one Compose `BedStrip` used by both modes.

- [ ] **Step 1: Write tests for add, select, rename, lock, arrange, switch modes, and rotate without state loss.**
- [ ] **Step 2: Run tests and record failure in Simple.**
- [ ] **Step 3: Extract the existing Advanced bed selector into `BedStrip.kt` and pass only service state/actions.**
- [ ] **Step 4: Add the same component to Simple with iOS-reference placement and 48 dp controls.**
- [ ] **Step 5: Use the same `arrangeBed(index,gap,allowRotation)` service path in both modes and surface `LOCKED`, `FULL`, and `EMPTY` results identically.**
- [ ] **Step 6: Run shared bed rule tests and Android UI tests.**
- [ ] **Step 7: Commit.**

```bash
git add android/app/src/main/java/de/psmobile/ui/BedStrip.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/test android/app/src/androidTest
git commit -m "feat(android): share multi-bed controls across modes"
```

### Task 5: Add camera centering and match tool hierarchy

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/CameraControlBar.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SceneView.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `android/app/src/androidTest/java/de/psmobile/ViewportControlsTest.kt`

**Interfaces:**
- Consumes: existing `SceneView.resetView()` and zoom methods.
- Produces semantics ID `camera.center` and reference-matched tool order.

- [ ] **Step 1: Write an instrumentation test that changes the camera, taps `camera.center`, and verifies the scene reset marker/revision.**
- [ ] **Step 2: Run the test and confirm the Center action is absent.**
- [ ] **Step 3: Implement the compact bar and call the existing reset path; do not introduce camera state in Compose.**
- [ ] **Step 4: Match order, icons, labels, and spacing to iOS while using Android back/focus semantics.**
- [ ] **Step 5: Run viewport and shell instrumentation tests.**
- [ ] **Step 6: Commit.**

```bash
git add android/app/src/main/java/de/psmobile/ui/CameraControlBar.kt android/app/src/main/java/de/psmobile/ui/SceneView.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/androidTest
git commit -m "feat(android): match reference camera controls"
```

### Task 6: Close Android functional gaps

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/SpecialSettingsDialogs.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SetupScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Create: `android/app/src/main/java/de/psmobile/diagnostics/SelfTestReport.kt`
- Test: `android/app/src/test/java/de/psmobile/diagnostics/SelfTestReportTest.kt`
- Test: `android/app/src/androidTest/java/de/psmobile/FunctionalGapTourTest.kt`

**Interfaces:**
- Consumes: existing ZIP import, QR utilities, special codecs, profile search/update, diagnostics reporter.
- Produces parity for QR pairing, ZIP import, profile search, special editors, arrange options, bed rename, selftest, and redacted diagnostics export.

- [ ] **Step 1: Add a table-driven failing test with one assertion for each named gap.**
- [ ] **Step 2: Run unit/instrumentation tests and retain one failure per missing feature.**
- [ ] **Step 3: Connect existing dormant utilities to visible flows; implement missing selftest report fields: OS, RAM class, model size, triangles, slice time, preview segments, and redacted failures.**
- [ ] **Step 4: Ensure QR content is parsed locally and secrets are written directly to Keystore without UI/log echo.**
- [ ] **Step 5: Run the functional-gap tour and secret scan.**
- [ ] **Step 6: Commit.**

```bash
git add android/app/src/main/java/de/psmobile android/app/src/test android/app/src/androidTest
git commit -m "feat(android): close reference feature gaps"
```

### Task 7: Create and satisfy the parity matrix

**Files:**
- Create: `docs/qa/parity-matrix.md`
- Create: `android/app/src/androidTest/java/de/psmobile/ReferenceScreenshotTourTest.kt`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: iOS reference manifest and Android screenshot tour.
- Produces one row per iOS reference screen with Android evidence and exception status.

- [ ] **Step 1: Create rows for every file in `docs/qa/ios-reference-manifest.json` with columns `flow`, `iOS image`, `Android image`, `functional test`, `visual status`, `exception`.**
- [ ] **Step 2: Implement screenshot tests using deterministic fixtures, locale, font scale 1.0, dark theme, and fixed tablet dimensions.**
- [ ] **Step 3: Run the tour on the x86_64 emulator in portrait and landscape.**
- [ ] **Step 4: Compare hierarchy, spacing, color roles, control order, empty/error states, and information density; fix unexplained differences.**
- [ ] **Step 5: Require every remaining exception to name the Android API/lifecycle reason and user-visible impact.**
- [ ] **Step 6: Run all Android tests and commit matrix/evidence.**

```bash
git add docs/qa/parity-matrix.md docs/arbeitsjournal.md android/app/src/androidTest/java/de/psmobile/ReferenceScreenshotTourTest.kt
git commit -m "test(android): complete iOS parity matrix"
```
