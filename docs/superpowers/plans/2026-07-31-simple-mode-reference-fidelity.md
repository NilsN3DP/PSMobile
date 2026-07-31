# Simple Mode Reference-Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current Easy card dashboard into a reference-aligned Simple Mode that launches directly into the real slicer workspace and presents every Simple submenu as a compact dark overlay.

**Architecture:** Keep `SlicerService` as the sole owner of project, preset, viewport, and slice state. Simple Mode embeds the existing real `SceneView` and a focused `SceneController` directly, then composes a dedicated Simple chrome layer and reference-aligned overlays around it. This deliberately avoids refactoring Advanced Mode during the Simple Mode pass. Pure state/layout decisions remain in `SimpleModeState`/`SimpleModeLayout` so the visual routing has fast unit coverage; actual behavior is proven with emulator smoke tests.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing GLES `SceneView`/`SceneController`, `SlicerService`, Android unit tests, Android Emulator.

## Global Constraints

- The visible product label is exactly `Simple Mode`; no user-facing `EasyPrint` string or logo remains.
- Simple Mode must closely follow the supplied Prusa reference hierarchy and visual density without changing Advanced Mode’s information architecture.
- Simple Mode’s default is the real 3D build-plate workspace, never a card/tile dashboard.
- Preserve one shared `SlicerService` session; do not copy projects, profiles, or slice state.
- Printer choice is model-level; nozzle variants stay internal until slice overview.
- Keep dark root/system bars, use orange only for selected/primary controls, and avoid large rounded dashboard cards.
- Support phone portrait, phone landscape, Medium tablet (600–839dp), and Expanded tablet (840dp+).
- Do not add ZIP/cloud/iOS work or a new printer-dispatch protocol in this plan.

---

## File structure

- `android/app/src/main/java/de/psmobile/ui/AppMode.kt` — rename the user-facing workflow enum from EASY to SIMPLE.
- `android/app/src/main/java/de/psmobile/MainActivity.kt` — route the existing setup/start picker into `SimpleModeScreen` without creating a second service.
- `android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt` — pure labels, panel routes, and reference submenu metadata.
- `android/app/src/main/java/de/psmobile/ui/SimpleModeLayout.kt` — pure compact/landscape/tablet chrome sizing and panel placement decisions.
- `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt` — Simple header, floating toolbar, workspace overlays, model FAB, bottom navigation, and all reference-aligned submenus.
- `android/app/src/test/java/de/psmobile/ui/AppModeTest.kt` — workflow routing/name tests updated to SIMPLE.
- `android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt` — reference copy, toolbar, selector, and panel-route tests.
- `android/app/src/test/java/de/psmobile/ui/SimpleModeLayoutTest.kt` — phone/tablet/landscape layout invariants.
- `docs/10-funktionsvergleich.md`, `docs/feature-matrix.json` — mark Simple Mode reference workspace/submenus as tested only after emulator evidence.

## Task 1: Establish the Simple Mode domain and remove the Easy name

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/AppMode.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/WorkflowStartScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/AppModeTest.kt`

**Interfaces:**
- Produces `enum class SimplePanel { WORKSPACE, PROJECTS, PRINTER, MATERIAL, SUPPORTS, ADHESION, PRINT_SETTINGS }`.
- Produces `SimpleModeState.toolbarLabels(printerModel: String): List<String>` and `SimpleModeState.visibleBrand(): String`.
- Produces `AppMode.SIMPLE`; callers must not retain `AppMode.EASY`.

- [ ] **Step 1: Write the failing name and toolbar tests.**

```kotlin
@Test fun simpleModeUsesOnlyTheApprovedVisibleName() {
    assertEquals("Simple Mode", SimpleModeState.visibleBrand())
    assertFalse(SimpleModeState.toolbarLabels("CORE One").any { it.contains("EasyPrint") })
}

@Test fun compactToolbarHasReferenceActionOrder() {
    assertEquals(
        listOf("Projekte", "Drucker", "Material", "Einstellen", "Vorschau", "G-Code"),
        SimpleModeState.toolbarLabels("CORE One"),
    )
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest`

Expected: FAIL because `SimpleModeState` and `AppMode.SIMPLE` do not exist.

- [ ] **Step 3: Implement the minimal domain rename and state helper.**

```kotlin
enum class AppMode { SIMPLE, ADVANCED }

object SimpleModeState {
    fun visibleBrand() = "Simple Mode"
    fun toolbarLabels(printerModel: String) = listOf(
        "Projekte", "Drucker", "Material", "Einstellen", "Vorschau", "G-Code",
    )
}
```

Update all start-screen and MainActivity references from `EASY` to `SIMPLE` and
visible copy from Easy/EasyPrint to Simple Mode. Do not rename `SlicerService`
state or mutate profile data.

- [ ] **Step 4: Run focused and existing routing tests.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest --tests *AppModeTest`

Expected: PASS with zero failures.

- [ ] **Step 5: Commit the isolated task.**

```powershell
git add android/app/src/main/java/de/psmobile/ui/AppMode.kt android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt android/app/src/main/java/de/psmobile/ui/WorkflowStartScreen.kt android/app/src/main/java/de/psmobile/MainActivity.kt android/app/src/test/java/de/psmobile/ui/AppModeTest.kt android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt
git commit -m "feat: rename Easy workflow to Simple Mode"
```

## Task 2: Embed the real slicer workspace in Simple Mode

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/SimpleWorkspaceState.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/SimpleWorkspaceStateTest.kt`

**Interfaces:**
- Consumes existing `SceneView`, `SceneController`, `SlicerService.objects`, and `SlicerService.sceneRevision`.
- Produces `SimpleWorkspaceState.invalidateKey(sceneRevision: Long): Long` and a private Simple Mode workspace composable in `SimpleModeScreen`.
- Advanced retains ownership of its own inspector and uncommon surface tools; both modes use the same service/core/scene data.

- [ ] **Step 1: Write the failing workspace ownership test.**

```kotlin
@Test fun simpleWorkspaceUsesTheSameSceneRevisionAsAdvanced() {
    assertEquals(42, WorkspaceState.invalidateKey(sceneRevision = 42))
}

@Test fun emptyWorkspaceStillExposesModelImport() {
    assertTrue(WorkspaceState.showModelAddAction(modelCount = 0))
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleWorkspaceStateTest`

Expected: FAIL because `SimpleWorkspaceState` is missing.

- [ ] **Step 3: Embed a focused real workspace without changing Advanced.**

In the private Simple workspace section of `SimpleModeScreen`, create a
`SceneController`, wire `onScaled` to `service.notifyViewportChanged()`, and
render the existing `SceneView` with `core = service.coreOrNull`,
`shaderDir = service.shaderDir()`, `invalidateKey = sceneRevision`, and the
selected object ID. Add only the editor/preview and view controls required by
the Simple toolbar; keep Advanced-only inspector, paint, measure, and sidebar
code in `SlicerScreen` unchanged. Simple undo/redo calls the same service APIs.

```kotlin
data class SimpleWorkspaceState(val invalidateKey: Long, val showModelAdd: Boolean)
object SimpleWorkspaceState {
    fun invalidateKey(sceneRevision: Long) = sceneRevision
    fun showModelAddAction(modelCount: Int) = modelCount >= 0
}
```

- [ ] **Step 4: Run the focused test and compile the app.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleWorkspaceStateTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the extraction.**

```powershell
git add android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/ui/SimpleWorkspaceState.kt android/app/src/test/java/de/psmobile/ui/SimpleWorkspaceStateTest.kt
git commit -m "feat: embed real workspace in Simple Mode"
```

## Task 3: Build Simple Mode’s reference-style workspace chrome

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/SimpleModeLayout.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/SimpleModeLayoutTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`

**Interfaces:**
- Consumes the real Simple workspace, `SimplePanel.WORKSPACE`, `SlicerService`, `onPickFile`, and `onStartSlice`.
- Produces `@Composable fun SimpleModeScreen(...)` and `SimpleModeLayout.toolbarPlacement(widthDp: Int, heightDp: Int): ToolbarPlacement`.
- `MainActivity` routes `AppMode.SIMPLE` only to `SimpleModeScreen`.

- [ ] **Step 1: Write the failing placement tests.**

```kotlin
@Test fun portraitUsesFloatingReferenceToolbar() {
    assertEquals(ToolbarPlacement.FLOATING_TOP, SimpleModeLayout.toolbarPlacement(411, 891))
}
@Test fun landscapeKeepsWorkspaceAndUsesCompactRow() {
    assertEquals(ToolbarPlacement.COMPACT_HORIZONTAL, SimpleModeLayout.toolbarPlacement(891, 411))
}
@Test fun phoneNeverUsesDashboardCards() {
    assertFalse(SimpleModeLayout.usesDashboardCards(EasyWidthClass.COMPACT))
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeLayoutTest`

Expected: FAIL because `SimpleModeLayout` does not exist.

- [ ] **Step 3: Implement the workspace-first Simple shell.**

Compose, in this order: dark header (`Simple Mode`, account, notifications),
square compact toolbar, `SlicerWorkspace`, lower-left undo/redo, orange
`+ Modell hinzufügen` over the lower-right viewport, and dark bottom navigation.
Do not call `EasyHome`, `EasySummary`, `EasyPrintDock`, or any rounded dashboard
card. The toolbar opens `SimplePanel` overlays and Preview/G-Code uses the
existing `onStartSlice` callback.

```kotlin
enum class ToolbarPlacement { FLOATING_TOP, COMPACT_HORIZONTAL, TABLET_ROW }
object SimpleModeLayout {
    fun toolbarPlacement(widthDp: Int, heightDp: Int) = when {
        widthDp >= 600 -> ToolbarPlacement.TABLET_ROW
        widthDp > heightDp -> ToolbarPlacement.COMPACT_HORIZONTAL
        else -> ToolbarPlacement.FLOATING_TOP
    }
    fun usesDashboardCards(width: EasyWidthClass) = false
}
```

- [ ] **Step 4: Run the layout tests and build.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeLayoutTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the workspace shell.**

```powershell
git add android/app/src/main/java/de/psmobile/ui/SimpleModeLayout.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/main/java/de/psmobile/MainActivity.kt android/app/src/test/java/de/psmobile/ui/SimpleModeLayoutTest.kt
git commit -m "feat: add reference style Simple Mode workspace"
```

## Task 4: Implement projects, printer, and material reference overlays

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt`

**Interfaces:**
- Consumes `SimplePanel`, `SlicerService.Presets`, existing `EasyModeState.printerModelChoices`, and existing service `selectPreset`.
- Produces `enum class OverlayKind { FULL, SHEET, GRID }`, `SimpleModeState.projectOverlayTitle()`, `SimpleModeState.overlayKind(panel, widthClass)`, and material quick filters.

- [ ] **Step 1: Write the failing submenu metadata tests.**

```kotlin
@Test fun printerUsesModelsRatherThanNozzleVariants() {
    assertEquals("Prusa CORE One", SimpleModeState.printerLabel("Prusa CORE One 0.4 nozzle"))
}
@Test fun materialQuickFiltersMatchTheSimpleBrowser() {
    assertEquals(listOf("PLA", "PETG", "ASA", "ABS", "FLEX"), SimpleModeState.materialTypes())
}
@Test fun projectsAndPrinterOpenAsOverlays() {
    assertEquals(OverlayKind.FULL, SimpleModeState.overlayKind(SimplePanel.PROJECTS, EasyWidthClass.COMPACT))
    assertEquals(OverlayKind.GRID, SimpleModeState.overlayKind(SimplePanel.PRINTER, EasyWidthClass.EXPANDED))
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest`

Expected: FAIL because the material/overlay helpers do not exist.

- [ ] **Step 3: Implement dark overlays above the retained workspace.**

Projects: searchable scrollable entries with thumbnail placeholder, project
metadata, overflow target, `New Project`, and existing import action. Printer:
model-level list on phone and configured-printer grid on Expanded tablet;
dispatch the selected raw representative to `selectPreset`. Material: palette
slots, `Add new`, search, type/colour filters, spool entries, and a concise
basic-material section. Each overlay has a visible back/close target and does
not reset `SimplePanel.WORKSPACE` state behind it.

- [ ] **Step 4: Run focused tests and build.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the overlays.**

```powershell
git add android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt
git commit -m "feat: add Simple Mode project printer material overlays"
```

## Task 5: Implement settings, supports, adhesion, and Print Settings overlays

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt`

**Interfaces:**
- Consumes existing `service.setConfig("support_material", value)`, `service.setConfig("brim_width", value)`, and print preset selection.
- Produces `SimpleModeState.supportGroups()`, `SimpleModeState.adhesionChoices()`, and `SimpleModeState.printSettingsColumns()`.

- [ ] **Step 1: Write the failing setting-structure tests.**

```kotlin
@Test fun supportsPreserveReferenceGroups() {
    assertEquals(listOf("Disabled", "Everywhere", "Build plate only"), SimpleModeState.supportGroups())
}
@Test fun adhesionHasDisabledAutomaticAndBrim() {
    assertEquals(listOf("Disabled", "Automatic", "Outline around the model"), SimpleModeState.adhesionChoices())
}
@Test fun printSettingsUseTheThreeReferenceColumns() {
    assertEquals(listOf("Print Settings", "Infill", "Shell Thickness"), SimpleModeState.printSettingsColumns())
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest`

Expected: FAIL with missing support/adhesion/column helpers.

- [ ] **Step 3: Implement reference-aligned selection overlays.**

Supports render Disabled, Everywhere and Build plate only sections with Snug and
Organic cards, selection indicator, explanatory text, and existing config
mapping. Adhesion renders Disabled, Automatic, and brim/outline choices mapped
to the current brim setting. Print Settings render the three compact columns,
use current presets and safe density/shell controls, and retain a reset action.
The `Einstellen` toolbar action opens a compact settings chooser which routes to
these overlays and Print Settings; Advanced remains the path for uncommon
parameters.

- [ ] **Step 4: Run focused tests and assemble.**

Run: `gradlew.bat :app:testDebugUnitTest --tests *SimpleModeStateTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit settings overlays.**

```powershell
git add android/app/src/main/java/de/psmobile/ui/SimpleModeState.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/src/test/java/de/psmobile/ui/SimpleModeStateTest.kt
git commit -m "feat: add Simple Mode settings overlays"
```

## Task 6: Verify visual fidelity, responsiveness, and stability

**Files:**
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `docs/feature-matrix.json`
- Create: `.superpowers/sdd/2026-07-31-simple-mode-reference-fidelity/task-6-report.md`
- Create: `.superpowers/sdd/2026-07-31-simple-mode-reference-fidelity/artifacts/`

**Interfaces:**
- Consumes the debug APK from Tasks 1–5 and the supplied reference hierarchy.
- Produces reproducible PNG/XML/logcat evidence plus documented feature status.

- [ ] **Step 1: Add a failing documentation assertion for the Simple Mode feature row.**

```python
def test_simple_mode_reference_row_is_tested():
    row = feature_by_id(load_matrix(), "simple_mode_reference_workspace")
    assert row["status"] == "tested"
```

- [ ] **Step 2: Run the documentation test and verify it fails before smoke evidence.**

Run: `python -m pytest build/scripts/tests/test_feature_reports.py -q`

Expected: FAIL because the new row is not marked `tested` yet.

- [ ] **Step 3: Run the complete Android gate in a local mirror if the UNC build tree is locked.**

Run: `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` with no test failures or lint errors. Record the
APK path and mirror path in the report.

- [ ] **Step 4: Execute and capture the emulator smoke matrix.**

Install the fresh APK and cold-start it. Capture PNG and XML at: 411dp portrait
workspace; 411dp landscape toolbar; 700dp Medium workspace and each overlay;
900dp Expanded workspace/printer grid. Exercise model import selection, printer
model selection, material search/filter, Supports choice, Adhesion choice,
Print Settings choice, Preview/G-Code readiness, overlay dismissal, and rotation.
Save logcat and require zero matches for `FATAL EXCEPTION`, `Process:
de.psmobile`, or `de.psmobile.*has died`.

- [ ] **Step 5: Update evidence documentation and run its tests.**

Mark the feature matrix row as `tested` only after the smoke artifacts exist;
link the report from the comparison document.

Run: `python -m pytest build/scripts/tests/test_feature_reports.py -q`

Expected: PASS.

- [ ] **Step 6: Commit evidence and docs.**

```powershell
git add docs/10-funktionsvergleich.md docs/feature-matrix.json .superpowers/sdd/2026-07-31-simple-mode-reference-fidelity
git commit -m "test: verify Simple Mode reference workflow"
```

## Plan self-review

- **Spec coverage:** Tasks 1 and 3 cover name, dark chrome, direct workspace,
  responsive hierarchy, model add, toolbar and bottom navigation. Task 2 makes
  the workspace real rather than a visual placeholder without refactoring
  Advanced Mode. Tasks 4 and 5 cover all
  five reference submenu families. Task 6 covers required device sizes,
  import/slice readiness, fatal-process check, and docs.
- **Placeholder scan:** no `TODO`, `TBD`, deferred implementation, or unnamed
  test action remains. The specified separate printer-dispatch API is explicitly
  out of scope and existing wiring is preserved.
- **Type consistency:** `SimplePanel`, `SimpleModeState`, `SimpleModeLayout`,
  `SimpleWorkspaceState`, and `AppMode.SIMPLE` are defined before
  later tasks consume them. `SlicerService` remains the single data boundary.
