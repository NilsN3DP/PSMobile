# PSMobile iOS Reference UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn iOS into the approved, clean reference UI for both orientations and both Simple and Advanced workflows.

**Architecture:** One workspace shell owns header, viewport, tool rail, inspector placement, bed strip, overlays, and navigation. Simple and Advanced supply mode-specific content but never duplicate project state. The right inspector derives its scope from selection and moves below the viewport in portrait.

**Tech Stack:** Swift 5.9, SwiftUI, C ABI wrapper, Kotlin/Native `PSMShared`, XCTest, XCUITest, XcodeGen/Xcode 26.3.

## Global Constraints

- Reference device is iPad Pro 2020; simulator destination is `platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1` when installed.
- The visible shell has one global header, one contextual tool region, one viewport, one inspector, and one bed strip.
- No selection means project settings; one selection means transform controls plus favorite object overrides.
- Camera controls include an explicit Center action wired to the existing viewport reset.
- Simple and Advanced share the same bed selector, arrange behavior, and selected-bed state.
- Portrait places the Advanced inspector below the viewport; landscape places it on the right.
- All visible copy exists in German and English and all interactive elements have accessibility identifiers.
- Each task ends with XCUITest evidence and an update to `docs/arbeitsjournal.md`.

---

### Task 1: Introduce a single adaptive workspace shell

**Files:**
- Create: `ios/PSMobile/Screens/WorkspaceShellView.swift`
- Create: `ios/PSMobile/UI/WorkspaceLayout.swift`
- Test: `ios/PSMobileTests/WorkspaceLayoutTests.swift`
- Modify: `ios/project.yml`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Modify: `ios/PSMobile/Screens/WorkflowStartView.swift`
- Test: `ios/PSMobileUITests/WorkflowStartUITests.swift`

**Interfaces:**
- Consumes: mode, orientation, horizontal size class, viewport content, tool content, inspector content, bed-strip content.
- Produces:
  - `enum WorkspaceInspectorPlacement { case trailing, bottom }`
  - `WorkspaceLayout.inspectorPlacement(width:height:isPad:)`
  - `WorkspaceShellView<Viewport,Tools,Inspector,Beds>`

- [ ] **Step 1: Write failing layout tests.**

```swift
func testIPadLandscapePlacesInspectorOnTrailingEdge() {
    XCTAssertEqual(WorkspaceLayout.inspectorPlacement(width: 1366, height: 1024, isPad: true), .trailing)
}

func testPortraitPlacesInspectorBelowViewport() {
    XCTAssertEqual(WorkspaceLayout.inspectorPlacement(width: 1024, height: 1366, isPad: true), .bottom)
}
```

- [ ] **Step 2: Add `WorkspaceLayout.swift` to the unit-test source list and run the focused test.**

Run on Mac: `xcodebuild -project ios/PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' -only-testing:PSMobileTests/WorkspaceLayoutTests test`

Expected: compile failure until the layout API exists.

- [ ] **Step 3: Implement the placement rule and generic shell.**

```swift
enum WorkspaceInspectorPlacement: Equatable { case trailing, bottom }

enum WorkspaceLayout {
    static func inspectorPlacement(width: CGFloat, height: CGFloat, isPad: Bool) -> WorkspaceInspectorPlacement {
        width > height && isPad ? .trailing : .bottom
    }
}
```

- [ ] **Step 4: Move existing header, viewport, tool rail, bed selector, and inspector containers into `WorkspaceShellView`; keep current content closures unchanged.**
- [ ] **Step 5: Replace the outer layout in Simple and Advanced with the shell and remove the experimental portrait-bottom feature flag.**
- [ ] **Step 6: Recompose `WorkflowStartView` as the clean entry surface for new/open project, recent projects, Simple/Advanced entry, physical printers, setup, and app settings; every action opens the existing state owner rather than a duplicate flow.**
- [ ] **Step 7: Run layout tests, `WorkflowStartUITests`, and the existing responsive UI suite.**

Run: `xcodebuild -project ios/PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' -only-testing:PSMobileTests/WorkspaceLayoutTests -only-testing:PSMobileUITests/ResponsiveLayoutUITests test`

- [ ] **Step 8: Commit.**

```bash
git add ios/PSMobile/Screens/WorkspaceShellView.swift ios/PSMobile/UI/WorkspaceLayout.swift ios/PSMobile/Screens/SimpleModeView.swift ios/PSMobile/Screens/AdvancedWorkspaceView.swift ios/PSMobile/Screens/WorkflowStartView.swift ios/PSMobileTests/WorkspaceLayoutTests.swift ios/PSMobileUITests/WorkflowStartUITests.swift ios/project.yml
git commit -m "feat(ios): add adaptive workspace shell"
```

### Task 2: Add iOS favorite settings storage and marking

**Files:**
- Create: `ios/PSMobile/Settings/FavoriteSettingsStore.swift`
- Test: `ios/PSMobileTests/FavoriteSettingsStoreTests.swift`
- Modify: `ios/PSMobile/Screens/SettingsView.swift`
- Modify: `ios/PSMobile/Screens/SettingField.swift`
- Modify: `ios/project.yml`

**Interfaces:**
- Consumes: `PSMShared.FavoriteSettingRules`, all keys in current settings catalog.
- Produces: `FavoriteSettingsStore.keys`, `toggle(_:)`, `ordered(availableKeys:)`.

- [ ] **Step 1: Write failing tests using an isolated `UserDefaults` suite.**

```swift
func testTogglePersistsAndSanitizesCatalogOrder() {
    let defaults = UserDefaults(suiteName: #function)!
    let store = FavoriteSettingsStore(defaults: defaults)
    store.toggle("fill_density")
    store.toggle("layer_height")
    XCTAssertEqual(store.ordered(availableKeys: ["layer_height", "fill_density"]),
                   ["layer_height", "fill_density"])
}
```

- [ ] **Step 2: Run the focused unit test and confirm the missing-type failure.**
- [ ] **Step 3: Implement `@MainActor final class FavoriteSettingsStore: ObservableObject` with one persisted string array and deterministic ordering.**
- [ ] **Step 4: Add a 44 pt star control to every eligible settings row and a Favorites page that uses the same `SettingField` editor.**
- [ ] **Step 5: Add accessibility identifiers `settings.favorite.<key>` and `settings.page.favorites`.**
- [ ] **Step 6: Run unit tests plus `SettingsUITests`.**
- [ ] **Step 7: Commit.**

```bash
git add ios/PSMobile/Settings ios/PSMobile/Screens/SettingsView.swift ios/PSMobile/Screens/SettingField.swift ios/PSMobileTests ios/project.yml
git commit -m "feat(ios): add favorite settings"
```

### Task 3: Build the contextual project/object inspector

**Files:**
- Create: `ios/PSMobile/Screens/ContextInspectorView.swift`
- Create: `ios/PSMobile/Screens/ObjectOverrideField.swift`
- Modify: `ios/PSMobile/Core/PsmCore.swift`
- Modify: `ios/PSMobile/SlicerModel.swift`
- Modify: `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Test: `ios/PSMobileUITests/ContextInspectorUITests.swift`

**Interfaces:**
- Consumes: shared `InspectorContract`, selected object ID, favorites store, C ABI object override functions.
- Produces:
  - `PsmCore.objectConfig(id:key:)`
  - `PsmCore.setObjectConfig(id:key:value:)`
  - `PsmCore.resetObjectConfig(id:key:)`
  - `SlicerModel.inspectorTarget`

- [ ] **Step 1: Write failing UI tests for both scopes.**

```swift
func testNoSelectionShowsProjectFavorites() {
    launchWithFixture("single-cube")
    app.buttons["selection.clear"].tap()
    XCTAssertTrue(app.otherElements["inspector.project"].exists)
    XCTAssertFalse(app.otherElements["inspector.object"].exists)
}

func testSelectionShowsTransformAndResettableOverride() {
    selectFixtureObject()
    XCTAssertTrue(app.otherElements["inspector.object"].exists)
    XCTAssertTrue(app.buttons["object.scale"].exists)
    XCTAssertTrue(app.buttons["override.fill_density.reset"].exists)
}

func testMultipleSelectionShowsOnlySharedActionsAndCommonOverrides() {
    selectTwoFixtureObjects()
    XCTAssertTrue(app.otherElements["inspector.multiple"].exists)
    XCTAssertFalse(app.buttons["object.rename"].exists)
    XCTAssertTrue(app.buttons["override.fill_density.applyToSelection"].exists)
}
```

- [ ] **Step 2: Run the focused UI suite and confirm the identifiers do not exist.**
- [ ] **Step 3: Add Swift C ABI wrappers with `throws` behavior matching existing global configuration wrappers.**
- [ ] **Step 4: Implement `ContextInspectorView`: project scope edits global values; one-object scope shows transform controls followed by favorite overrides; multi-selection shows only shared transforms and favorite keys valid for every selected object.**
- [ ] **Step 5: Show inherited/overridden state and a `Auf globalen Wert zurücksetzen` action per object field.**
- [ ] **Step 6: Mount the same inspector content in Simple and Advanced shell placements.**
- [ ] **Step 7: Run `ContextInspectorUITests`, `ObjectBarUITests`, and `SettingsUITests`.**
- [ ] **Step 8: Commit.**

```bash
git add ios/PSMobile/Core/PsmCore.swift ios/PSMobile/SlicerModel.swift ios/PSMobile/Screens/ContextInspectorView.swift ios/PSMobile/Screens/ObjectOverrideField.swift ios/PSMobile/Screens/AdvancedObjectInspectorView.swift ios/PSMobile/Screens/SimpleModeView.swift ios/PSMobile/Screens/AdvancedWorkspaceView.swift ios/PSMobileUITests/ContextInspectorUITests.swift
git commit -m "feat(ios): add contextual inspector overrides"
```

### Task 4: Share multi-bed controls between Simple and Advanced

**Files:**
- Modify: `ios/PSMobile/Screens/BedSelector.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Modify: `ios/PSMobile/SlicerModel.swift`
- Test: `ios/PSMobileUITests/SharedBedStripUITests.swift`

**Interfaces:**
- Consumes: `SlicerModel.beds`, active bed, `arrangeBed`, lock and rename mutations.
- Produces: one `BedSelector` instance API used by both modes and preserved through mode/orientation changes.

- [ ] **Step 1: Write failing tests that add, rename, lock, arrange, switch mode twice, rotate, and assert the same active bed and object counts.**
- [ ] **Step 2: Run the focused UI suite and record the first state-loss assertion.**
- [ ] **Step 3: Extract any remaining mode-local bed state into `SlicerModel`; make `BedSelector` accept only bindings/actions from that model.**
- [ ] **Step 4: Mount the full bed slider in Simple, including add, rename, lock, select, and arrange controls.**
- [ ] **Step 5: Ensure arrange uses `psm_arrange_bed_ex` and displays locked/full/empty results identically in both modes.**
- [ ] **Step 6: Run `SharedBedStripUITests`, `MultiBedArrangeUITests`, and `MultiBedViewportUITests`.**
- [ ] **Step 7: Commit.**

```bash
git add ios/PSMobile/Screens/BedSelector.swift ios/PSMobile/Screens/SimpleModeView.swift ios/PSMobile/Screens/AdvancedWorkspaceView.swift ios/PSMobile/SlicerModel.swift ios/PSMobileUITests/SharedBedStripUITests.swift
git commit -m "feat(ios): share multi-bed controls across modes"
```

### Task 5: Add a visible camera Center action

**Files:**
- Create: `ios/PSMobile/Screens/CameraControlBar.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Test: `ios/PSMobileUITests/ViewportUITests.swift`

**Interfaces:**
- Consumes: existing `resetViewKey`/`PsmViewport.resetView()` path.
- Produces: `camera.center` action in both modes without a second camera state.

- [ ] **Step 1: Add failing UI tests that orbit then center the fixture, switch beds before centering, and move both lower/upper preview layer handles; assert the reset frames the active bed and the visible layer range is inclusive.**
- [ ] **Step 2: Run `ViewportUITests/testCenterButtonRestoresReferenceView` and confirm the button is missing.**
- [ ] **Step 3: Implement a compact camera bar with Center, zoom-in, and zoom-out actions; Center increments the existing reset key.**
- [ ] **Step 4: Add German/English labels and VoiceOver hints; keep every hit target at least 44 pt.**
- [ ] **Step 5: Run the full viewport UI suite.**
- [ ] **Step 6: Commit.**

```bash
git add ios/PSMobile/Screens/CameraControlBar.swift ios/PSMobile/Screens/SimpleModeView.swift ios/PSMobile/Screens/AdvancedWorkspaceView.swift ios/PSMobileUITests/ViewportUITests.swift
git commit -m "feat(ios): expose camera centering"
```

### Task 6: Complete iOS-only quality gaps

**Files:**
- Modify: `ios/PSMobile/Screens/SpecialValueEditors.swift`
- Modify: `ios/PSMobile/Screens/AppSettingsView.swift`
- Modify: `ios/PSMobile/PSMobileApp.swift`
- Modify: `ios/PSMobile/Core/PsmLog.swift`
- Modify: `ios/PSMobile/Selbsttest.swift`
- Modify: `ios/PSMobile/Screens/SelbsttestView.swift`
- Create: `ios/PSMobile/ProfileUpdate/ProfileUpdateService.swift`
- Create: `docs/qa/ios-release-checklist.md`
- Test: `ios/PSMobileTests/ProfileUpdatePolicyTests.swift`
- Test: `ios/PSMobileUITests/SpecialValueUITests.swift`
- Test: `ios/PSMobileUITests/CrashDetectionUITests.swift`

**Interfaces:**
- Consumes: common `SpecialValueCodec`, existing app lifecycle markers, allowed update hosts and SHA-256 manifest.
- Produces: round-trippable ramming/G-code editors, atomic profile update, crash dialog only after abnormal termination evidence, bilingual accessible UI, redacted selftest export, and release-document checklist.

- [ ] **Step 1: Add failing tests for special-value round trips, rejected HTTP/foreign-host manifests, checksum mismatch, rollback, normal restart, abnormal restart, German/English copy, Dynamic Type, VoiceOver identifiers, and selftest redaction.**
- [ ] **Step 2: Run focused tests and capture failures.**
- [ ] **Step 3: Wire the existing special editors to the settings rows and validate before calling `psm_config_set`.**
- [ ] **Step 4: Implement update staging as `download -> checksum -> unpack to staging -> validate mandatory files -> atomic directory swap -> rollback on launch failure`.**
- [ ] **Step 5: Replace the launch-only crash marker with `launchStarted`, `sceneBecameActive`, and `cleanShutdown` evidence; UI-test process kills are excluded by launch arguments.**
- [ ] **Step 6: Export selftest data for device/OS, RAM, model size, triangle count, slice time, preview movement count, and redacted failures; never include paths, credentials, or printer secrets.**
- [ ] **Step 7: Audit every new control at default and accessibility text sizes with VoiceOver, keyboard, and Pencil; add localized German and English strings.**
- [ ] **Step 8: Fill `docs/qa/ios-release-checklist.md` with AGPL source offer, third-party notices, privacy disclosure, and explicit App-Store/legal decision status.**
- [ ] **Step 9: Run the new tests plus `SetupUITests`, `SelbsttestUITests`, and `BildschirmfotoTests`.**
- [ ] **Step 10: Commit.**

```bash
git add ios/PSMobile ios/PSMobileTests ios/PSMobileUITests ios/project.yml docs/qa/ios-release-checklist.md
git commit -m "feat(ios): close reference quality gaps"
```

### Task 7: Freeze the iOS visual reference

**Files:**
- Create: `docs/qa/ios-reference-screenshots/README.md`
- Create: `docs/qa/ios-reference-manifest.json`
- Modify: `ios/PSMobileUITests/BildschirmfotoTests.swift`
- Modify: `docs/arbeitsjournal.md`

**Interfaces:**
- Consumes: completed iOS shell and deterministic fixtures.
- Produces: named portrait/landscape reference images and Gate 1/2 evidence for Android.

- [ ] **Step 1: Extend the screenshot tour to Start, setup, Simple empty/selected/multi-bed, Advanced landscape/portrait, Settings favorites, preview, and printer entry.**
- [ ] **Step 2: Run unit and UI suites on the Mac side-build copy.**

Run: `xcodebuild -project ios/PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' test`

Expected: zero failed tests.

- [ ] **Step 3: Copy only final screenshots into `docs/qa/ios-reference-screenshots` and record filename, orientation, pixel size, test method, and commit in the JSON manifest.**
- [ ] **Step 4: Inspect every image for clipping, duplicate navigation, false crash dialogs, inconsistent spacing, and stale state.**
- [ ] **Step 5: Append Gate 1/2 evidence to the work journal and commit.**

```bash
git add docs/qa/ios-reference-* ios/PSMobileUITests/BildschirmfotoTests.swift docs/arbeitsjournal.md
git commit -m "test(ios): freeze approved reference tour"
```
