# EasyPrint Responsive Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Easy Print becomes a dark EasyPrint-style workflow that uses distinct phone and tablet layouts without changing the Advanced mode or creating a second slicer session.

**Architecture:** A small pure layout classifier maps available width to Compact, Medium and Expanded. `EasyModeScreen` owns only the selected Easy panel and delegates profile filtering/readiness to `EasyModeState` and all mutations to the existing `SlicerService`. Compact uses a focused overview plus modal selection surfaces; Medium/Expanded uses a horizontal toolbar, central preview workspace and contextual selector panel.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose window constraints, Android Emulator, JUnit, Gradle.

## Global Constraints

- Android only; do not redesign Advanced mode in this plan.
- The complete Easy root and all Easy surfaces use `PrusaColors.Background`.
- Compact is `< 600.dp`, Medium is `600.dp..839.dp`, Expanded is `>= 840.dp`.
- Easy uses the existing `SlicerService`, profile flows, import callback and slice callback.
- Printer is selected as a model; nozzle remains out of the Easy printer picker.
- ZIP import, new network calls and native slicing changes are out of scope.
- Primary touch targets have a minimum height of 48 dp.

---

### Task 1: Deterministic layout classification

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/EasyModeLayout.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/EasyModeLayoutTest.kt`

**Interfaces:**
- Produces `enum class EasyWidthClass { COMPACT, MEDIUM, EXPANDED }`.
- Produces `object EasyModeLayout` with `fun widthClass(widthDp: Int): EasyWidthClass`.

- [ ] **Step 1: Write the failing width boundary tests.**

```kotlin
@Test fun compactEndsBefore600() {
    assertEquals(EasyWidthClass.COMPACT, EasyModeLayout.widthClass(599))
}
@Test fun mediumStartsAt600AndEndsBefore840() {
    assertEquals(EasyWidthClass.MEDIUM, EasyModeLayout.widthClass(600))
    assertEquals(EasyWidthClass.MEDIUM, EasyModeLayout.widthClass(839))
}
@Test fun expandedStartsAt840() {
    assertEquals(EasyWidthClass.EXPANDED, EasyModeLayout.widthClass(840))
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the type is absent.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeLayoutTest'`

- [ ] **Step 3: Implement the pure classifier.**

```kotlin
enum class EasyWidthClass { COMPACT, MEDIUM, EXPANDED }

object EasyModeLayout {
    fun widthClass(widthDp: Int) = when {
        widthDp < 600 -> EasyWidthClass.COMPACT
        widthDp < 840 -> EasyWidthClass.MEDIUM
        else -> EasyWidthClass.EXPANDED
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeLayoutTest'`

### Task 2: Dark EasyPrint application shell

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/EasyModeLayoutTest.kt`

**Interfaces:**
- Consumes `EasyModeLayout.widthClass` and existing `EasyReadiness`.
- Produces `@Composable fun EasyModeScreen(...)` with a painted background and one current `EasyPanel`.

- [ ] **Step 1: Extend the layout test with a no-white-shell invariant.**

```kotlin
@Test fun everyWidthClassHasAnExplicitDarkRoot() {
    EasyWidthClass.entries.forEach { widthClass ->
        assertTrue(EasyModeLayout.rootUsesDarkBackground(widthClass))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails because the invariant is absent.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeLayoutTest'`

- [ ] **Step 3: Add `EasyPanel` and the full-screen background.**

```kotlin
enum class EasyPanel { HOME, PROJECTS, PRINTER, FILAMENT, SUPPORTS, ADHESION, PRINT_SETTINGS }

Box(Modifier.fillMaxSize().background(PrusaColors.Background)) {
    // Width-dependent Easy content
}
```

Set system bars dark while `EasyModeScreen` is composed using the existing
activity window. Restore the dark slicer default when the composable leaves.
Do not change Advanced navigation or theme colors.

- [ ] **Step 4: Replace the long vertical card stack with a compact home surface.**

Show only a project/model summary, selected printer, selected filament,
supports, adhesion and print-settings summaries. Each summary opens its
`EasyPanel`; it does not expand a profile list on the home surface.

- [ ] **Step 5: Run unit tests and compile.**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

### Task 3: Phone workflow and selection sheets

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeState.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/EasyModeStateTest.kt`

**Interfaces:**
- Consumes `EasyPanel`, `EasyModeState.filterPresets`, selected presets and quick settings.
- Produces `fun EasyModeState.panelTitle(panel: EasyPanel): String` and focused compact selection surfaces.

- [ ] **Step 1: Write failing panel-title and filtered-empty-state tests.**

```kotlin
@Test fun printerPanelHasClearTitle() {
    assertEquals("Druckermodell", EasyModeState.panelTitle(EasyPanel.PRINTER))
}
@Test fun emptyFilamentFilterShowsNoProfilesMessage() {
    assertTrue(EasyModeState.emptySearchMessage(EasyPanel.FILAMENT).contains("Keine"))
}
```

- [ ] **Step 2: Run the focused state test and verify it fails.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeStateTest'`

- [ ] **Step 3: Implement the state helpers and compact selector surfaces.**

Use `ModalBottomSheet` for printer, filament and print-settings search;
use a full-height selection surface when screen height is under 560 dp.
Keep query text in `rememberSaveable(panel)` so rotation retains it.
The profile-empty action calls the existing printer setup or Advanced
callback exactly as the previous Easy screen did.

- [ ] **Step 4: Implement focused support and adhesion choice surfaces.**

Use two labeled 48-dp choice rows per setting. Selecting a row calls
`service.setConfig("support_material", "1" | "0")` or
`service.setConfig("brim_width", "5" | "0")`, closes the panel and updates
the home summary.

- [ ] **Step 5: Add the compact fixed preview/print dock.**

The dock displays the first missing `EasyReadiness.missing` label when not
ready. When ready it calls the existing `onStartSlice` callback. It remains
visible while the home surface scrolls.

- [ ] **Step 6: Run state tests and compile.**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

### Task 4: Tablet toolbar, workspace and contextual panel

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/EasyModeLayoutTest.kt`

**Interfaces:**
- Consumes `EasyWidthClass.MEDIUM` and `EasyWidthClass.EXPANDED`.
- Produces `EasyToolbar`, `EasyWorkspace` and `EasyContextPanel` composables.

- [ ] **Step 1: Add failing layout-state tests for modal versus persistent context.**

```kotlin
@Test fun mediumUsesModalContextPanel() {
    assertFalse(EasyModeLayout.contextPanelIsPersistent(EasyWidthClass.MEDIUM))
}
@Test fun expandedUsesPersistentContextPanel() {
    assertTrue(EasyModeLayout.contextPanelIsPersistent(EasyWidthClass.EXPANDED))
}
```

- [ ] **Step 2: Run the focused test and verify it fails.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeLayoutTest'`

- [ ] **Step 3: Implement the toolbar and workspace.**

The toolbar contains the exact EasyPrint workflow actions: Projects,
active printer, Material, Supports, Print Settings, Preview and Print.
The workspace has a dark scene surface, model-count status and large model
import action. It is an Easy-mode status surface; it does not duplicate the
Advanced viewport or add a second renderer.

- [ ] **Step 4: Implement the width-dependent context panel.**

At Medium, selected toolbar actions open a modal sheet. At Expanded, show a
right-side 420-dp panel when an action is selected; deselecting closes it.
Use the same picker and choice composables from the phone path.

- [ ] **Step 5: Run unit tests, lint and a debug build.**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

### Task 5: Visual regression smoke tests and parity handoff

**Files:**
- Modify: `docs/feature-matrix.json`
- Modify: `docs/10-funktionsvergleich.md`
- Test artifact: `C:/Users/Nils/AppData/Local/Temp/psmobile-easy-{phone,tablet}.png`

**Interfaces:**
- Consumes the final APK and Android Emulator.
- Produces documented emulator evidence for the EasyPrint shell only.

- [ ] **Step 1: Build a fresh debug APK with the staged native libraries.**

Run from the local Android build mirror:
`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --rerun-tasks`

- [ ] **Step 2: Install it and test Compact portrait.**

Verify: no white root, Easy header, model import, printer search, material
search, supports, adhesion, fixed Print dock and no fatal-exception log.

- [ ] **Step 3: Test Compact landscape and state retention.**

Open a picker, enter a query, rotate, and verify the selected panel/query
remain available with no crash.

- [ ] **Step 4: Test Medium and Expanded tablet layouts.**

Verify toolbar actions, modal context at 600–839 dp, persistent right panel
at 840+ dp, and visible Preview/Print action.

- [ ] **Step 5: Update evidence and final test status.**

Record the tested screen classes and remaining intentional differences from
the web reference: no copied Prusa branding, no cloud spool inventory and
no separate Easy 3D renderer.

- [ ] **Step 6: Commit the focused EasyPrint changes.**

Run:
`git add android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt android/app/src/main/java/de/psmobile/ui/EasyModeLayout.kt android/app/src/main/java/de/psmobile/ui/EasyModeState.kt android/app/src/test/java/de/psmobile/ui/EasyModeLayoutTest.kt android/app/src/test/java/de/psmobile/ui/EasyModeStateTest.kt docs/feature-matrix.json docs/10-funktionsvergleich.md`

Then:
`git commit -m "feat: add responsive EasyPrint shell"`

### Task 6: Query retention across responsive layout changes

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeState.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/EasyModeStateTest.kt`
- Test artifact: `C:/Users/Nils/AppData/Local/Temp/psmobile-easy-phone-landscape-query-fixed.xml`

**Interfaces:**
- Produces `fun EasyModeState.profileQueryAfterChange(
  queries: Map<String, String>, panel: EasyPanel, value: String
): Map<String, String>`.
- `EasyModeScreen` owns one `rememberSaveable` `Map<String, String>` keyed by
  `EasyPanel.name` and passes query/value callbacks into all profile selectors.

- [ ] **Step 1: Write the failing query-retention reducer test.**

```kotlin
@Test fun printerQuerySurvivesAResponsiveBranchChange() {
    val entered = EasyModeState.profileQueryAfterChange(emptyMap(), EasyPanel.PRINTER, "core")
    assertEquals("core", entered[EasyPanel.PRINTER.name])
    assertEquals("core", EasyModeState.profileQueryFor(entered, EasyPanel.PRINTER))
}
```

- [ ] **Step 2: Run the focused state test and verify it fails because the
  helper is absent.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeStateTest'`

- [ ] **Step 3: Lift query ownership to the Easy root.**

Use `rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }`
in `EasyModeScreen`. Pass `query = queries[panel.name].orEmpty()` and an
immutable map-update callback through Compact and Tablet selectors. Remove
the selector-local `rememberSaveable(panel)` query.

- [ ] **Step 4: Run the focused state test and full Android gate.**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

- [ ] **Step 5: Repeat the exact emulator reproduction.**

Enter `core` in the Compact printer sheet, rotate into Medium, and verify
the still-open printer sheet retains `core` in its EditText. Capture a new
UI dump and check the log buffer for no fatal exception.

### Task 7: Final EasyPrint usability corrections

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeState.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/EasyModeStateTest.kt`
- Modify: `android/app/src/test/java/de/psmobile/ui/EasyModeLayoutTest.kt`

**Interfaces:**
- Produces `fun EasyModeState.printerModelChoices(rawPresets: List<String>): List<EasyPrinterChoice>` where each visible label has no nozzle suffix and maps to one existing raw preset.
- Produces `fun EasyModeLayout.compactDockReservedHeightDp(): Int` with a value at least 168.

- [ ] **Step 1: Write failing model-choice and dock-clearance tests.**

```kotlin
@Test fun printerModelChoicesGroupNozzleVariants() {
    val choices = EasyModeState.printerModelChoices(listOf(
        "Prusa CORE One 0.4 nozzle", "Prusa CORE One 0.6 nozzle"
    ))
    assertEquals(listOf("Prusa CORE One"), choices.map { it.label })
}
@Test fun compactDockReservesEnoughScrollClearance() {
    assertTrue(EasyModeLayout.compactDockReservedHeightDp() >= 168)
}
```

- [ ] **Step 2: Verify the focused tests fail for the missing helpers.**

Run: `./gradlew :app:testDebugUnitTest --tests '*EasyModeStateTest' --tests '*EasyModeLayoutTest'`

- [ ] **Step 3: Make printer selection model-level.**

Use grouped visible model labels in the Easy printer picker. Preserve the
existing selected raw preset internally; selecting a model maps to its
stable representative preset. Do not show or select a nozzle from Easy.

- [ ] **Step 4: Complete all responsive navigation paths.**

Use a fixed Compact header with Projects and active printer actions outside
the scroll content. Keep the tablet toolbar exactly unchanged. Add adhesion
choices beneath Supports in the shared Tablet supports context so adhesion
is reachable at Medium and Expanded.

- [ ] **Step 5: Correct the compact dock.**

Render distinct Preview and Print affordances using the existing slice
callback until a separate print dispatch callback exists. Reserve
`compactDockReservedHeightDp()` plus system navigation inset in the home
scroll content, so no summary can sit under the dock.

- [ ] **Step 6: Run the full Android gate and targeted emulator smoke.**

Verify a printer picker shows model-only labels, Medium reaches adhesion via
Supports, Compact header remains visible while scrolling, and the dock does
not cover the lowest summary. Run `:app:testDebugUnitTest :app:lintDebug
:app:assembleDebug` before installing the fresh APK.
