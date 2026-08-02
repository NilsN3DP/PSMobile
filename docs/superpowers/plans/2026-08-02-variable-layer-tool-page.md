# Variable Layer Tool Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make variable layer heights an Advanced-tool workflow that remains fully usable on phone and tablet layouts.

**Architecture:** Keep the existing `SlicerService.layerProfile` data contract and preview overlay. Replace the modal editor with an inspector-owned state that renders a dedicated, vertically scrollable tool page; its primary actions remain outside the editable list. This avoids a second navigation stack and leaves the native slicing profile unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing `SlicerScreen` inspector, JVM unit tests, Android x86_64 emulator.

## Global Constraints

- Android is the implementation target; iOS is not changed.
- Advanced remains touch-first: primary targets are at least 48 dp.
- Keep the existing dark panel / orange selection visual language.
- Do not remove existing layer-profile points, validation, preview segments, or model overlay.

---

### Task 1: Extract layer-profile validation into a testable state model

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/LayerProfileEditorState.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/LayerProfileEditorStateTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/GeometryTools.kt`

**Interfaces:**
- Produces: `LayerProfileEditorState(objectHeight: Double, rows: List<Pair<String, String>>)` with `validPoints`, `previewSegments`, `addPoint()`, and `removePoint(index)`.
- Consumes: `NumberCodec`, `insertLayerProfilePoint`, and `layerProfilePreviewSegments`.

- [x] **Step 1: Write the failing tests**

```kotlin
@Test fun `editor accepts strictly increasing positive points`() {
    val state = LayerProfileEditorState(20.0, listOf("0" to "0.12", "20" to "0.28"))
    assertEquals(listOf(0.0 to 0.12, 20.0 to 0.28), state.validPoints)
    assertTrue(state.canApply)
}

@Test fun `editor refuses repeated Z points`() {
    val state = LayerProfileEditorState(20.0, listOf("0" to "0.2", "0" to "0.2"))
    assertFalse(state.canApply)
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests de.psmobile.ui.LayerProfileEditorStateTest`

Expected: compilation fails because `LayerProfileEditorState` does not exist.

- [x] **Step 3: Implement the minimal immutable state model**

```kotlin
internal data class LayerProfileEditorState(
    val objectHeight: Double,
    val rows: List<Pair<String, String>>,
) {
    val validPoints: List<Pair<Double, Double>> = rows.mapNotNull { /* NumberCodec */ }
    val canApply: Boolean = validPoints.size == rows.size && /* strict Z validation */
}
```

- [x] **Step 4: Run focused and full JVM tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: all tests pass.

### Task 2: Render an inspector-owned layer-height editor

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/GeometryTools.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/LayerProfileEditorStateTest.kt`

**Interfaces:**
- Consumes: `LayerProfileEditorState` from Task 1 and `SlicerService.setLayerProfile(objectId, points)`.
- Produces: `GeometryDialog.LAYERS` as an inspector state with a back action rather than a modal dialog.

- [ ] **Step 1: Add the editor-navigation state and a failing state transition test**

```kotlin
@Test fun `layer editor returns to tools after apply`() {
    assertEquals(GeometryToolDestination.TOOLS,
        GeometryToolDestination.LAYERS.afterApply())
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests de.psmobile.ui.LayerProfileEditorStateTest`

Expected: compilation fails because `GeometryToolDestination` is absent.

- [x] **Step 3: Implement the dedicated editor composition**

```kotlin
Column(Modifier.fillMaxSize()) {
    LayerToolHeader(onBack = onBack)
    LayerProfilePreview(state.previewSegments)
    LazyColumn(Modifier.weight(1f)) { /* editable Z / height rows */ }
    LayerToolActions(onApply = { onApply(state.validPoints) }, onReset = onReset)
}
```

Use 48-dp rows and keep `Übernehmen`, `Zurücksetzen`, and `Abbrechen` visible outside the list.

- [x] **Step 4: Run JVM tests and build the debug APK**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: build succeeds.

### Task 3: Emulator acceptance test

**Files:**
- Modify: `docs/release/android-v1-gate.md`

**Interfaces:**
- Consumes: APK from Task 2 and an imported `wuerfel20.stl`.
- Produces: a dated record of the tested variable-layer workflow.

- [x] **Step 1: Install the fresh APK and open Advanced tools**

Run: `adb -s emulator-5554 install -r app-debug.apk`

- [x] **Step 2: Verify visible initial controls and actions**

Import a cube, select it, open Tools → Variable layer heights. Confirm that the first Z/height row and `Übernehmen` are visible without scrolling.

- [x] **Step 3: Verify edit, apply, model feedback and reset**

Add a midpoint, change it to `0.12 mm`, apply, and confirm the model-associated orange/grey preview overlay. Reopen, reset, and confirm the overlay disappears.

- [x] **Step 4: Verify narrow/portrait layout**

Set the emulator to `800x1200`, repeat Steps 2–3, then restore `1200x800`. Record screenshot paths and outcome in the release gate.
