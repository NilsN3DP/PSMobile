# PSMobile Baseline and Shared State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a reproducible side-build baseline and add the shared object, bed, favorites, and inspector contracts required by both apps.

**Architecture:** The dirty NAS tree is read-only source material. Reviewed changes are copied into the local branch with a manifest before new work begins. Object-specific configuration lives in `ModelObject::config` behind the C ABI; pure presentation decisions live in Kotlin Multiplatform common rules; Swift and Compose remain adapters.

**Tech Stack:** Git, PowerShell, C++17, PrusaSlicer Model/DynamicPrintConfig, C ABI contract tests, Kotlin Multiplatform common tests.

## Global Constraints

- Use branch `codex/ios-android-parity-sidebuild` in `C:/Users/Nils/.codex/worktrees/psmobile-ios-android-parity-sidebuild-local`.
- Never stage from `//localunraid/n3dp/KI Projekte/PSMobile`; only read and copy explicitly listed files.
- Do not import generated profile trees, build products, credentials, logs, or device data.
- Preserve ABI compatibility by appending functions and bumping `psm_abi_version()` only when the new wrapper symbols are complete.
- Mutations create one history checkpoint and call `mark_design_changed()` exactly once.
- Append test evidence to `docs/arbeitsjournal.md` after every task.

---

### Task 1: Capture and import the reviewed dirty-source baseline

**Files:**
- Create: `docs/qa/sidebuild-baseline-manifest.txt`
- Create: `docs/qa/sidebuild-baseline-source.sha256`
- Modify: the source/test files explicitly listed by the manifest
- Test: `docs/qa/sidebuild-baseline-manifest.txt`

**Interfaces:**
- Consumes: read-only NAS worktree at `//localunraid/n3dp/KI Projekte/PSMobile`, base commit `61c1b47`.
- Produces: an attributable source snapshot in the local side-build; no asset-tree import.

- [ ] **Step 1: Generate the candidate manifest without copying files.**

```powershell
$source = '\\localunraid\n3dp\KI Projekte\PSMobile'
git -c safe.directory='//localunraid/n3dp/KI Projekte/PSMobile' -C $source status --porcelain=v1 --untracked-files=all |
  Where-Object { $_ -match '\.(swift|kt|kts|cpp|hpp|h|c|yml|plist)$' -and $_ -notmatch '/assets/' } |
  Set-Content docs/qa/sidebuild-baseline-manifest.txt
```

- [ ] **Step 2: Verify the manifest contains only `core`, `viewport`, `ios`, `android`, `docker`, or build metadata paths.**

```powershell
$bad = Get-Content docs/qa/sidebuild-baseline-manifest.txt | Where-Object {
  $_ -notmatch '^.. (core|viewport|ios|android|docker|CMakeLists\.txt|build/scripts)/'
}
if ($bad) { $bad; throw 'Unreviewed baseline path' }
```

- [ ] **Step 3: Copy each reviewed file while preserving relative paths, then record SHA-256 hashes.**

```powershell
$source = '\\localunraid\n3dp\KI Projekte\PSMobile'
$root = (Get-Location).Path
Get-Content docs/qa/sidebuild-baseline-manifest.txt | ForEach-Object {
  $relative = $_.Substring(3)
  $from = Join-Path $source $relative
  $to = Join-Path $root $relative
  New-Item -ItemType Directory -Force -Path (Split-Path $to) | Out-Null
  Copy-Item -LiteralPath $from -Destination $to
}
Get-Content docs/qa/sidebuild-baseline-manifest.txt | ForEach-Object {
  $relative = $_.Substring(3)
  '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 $relative).Hash,$relative
} | Set-Content docs/qa/sidebuild-baseline-source.sha256
```

- [ ] **Step 4: Run existing fast tests before accepting the import.**

Run: `./android/gradlew -p android :shared:allTests :app:testProductionDebugUnitTest`

Expected: all shared and JVM tests pass; failures attributable to the imported snapshot are fixed before commit.

- [ ] **Step 5: Build the core contract target.**

Run: `cmake --build build-out/host --target psm_contract_tests && build-out/host/core/test/psm_contract_tests`

Expected: exit code 0. If `build-out/host` is absent, run `cmake -S . -B build-out/host -DPSM_BUILD_TESTS=ON` first.

- [ ] **Step 6: Commit the baseline as its own auditable change.**

```bash
git add docs/qa/sidebuild-baseline-* core viewport ios android docker CMakeLists.txt build/scripts
git commit -m "chore: capture reviewed side-build baseline"
```

### Task 2: Add parallel-installable side-build identities

**Files:**
- Modify: `ios/project.yml`
- Modify: `ios/PSMobile/Support/Info.plist`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `build/scripts/tests/test_sidebuild_identity.py`

**Interfaces:**
- Consumes: normal production identifiers `de.psmobile`.
- Produces: iOS target/scheme `PSMobilePreview` with `de.psmobile.preview` and Android `previewDebug` with application ID `de.psmobile.preview`; both display `PSMobile Preview` and use their own default preferences, Keychain/Keystore namespaces, caches, and documents.

- [ ] **Step 1: Write a failing configuration test.**

```python
def test_preview_identities_are_parallel_and_isolated(self):
    ios = Path("ios/project.yml").read_text(encoding="utf-8")
    android = Path("android/app/build.gradle.kts").read_text(encoding="utf-8")
    self.assertIn("PSMobilePreview", ios)
    self.assertIn("de.psmobile.preview", ios)
    self.assertIn('create("preview")', android)
    self.assertIn('applicationIdSuffix = ".preview"', android)
```

- [ ] **Step 2: Run `python -m unittest build.scripts.tests.test_sidebuild_identity` and confirm failure.**
- [ ] **Step 3: Add `PSMobilePreview` to `ios/project.yml` using the same source/resource/link settings as `PSMobile`, with a preview bundle identifier and display name.**
- [ ] **Step 4: Add Android flavor dimension `distribution` and preview flavor with `.preview` ID suffix and `PSMobile Preview` resource value.**
- [ ] **Step 5: Assert production and preview identifiers differ and that neither build references a shared explicit preferences suite or credential access group.**
- [ ] **Step 6: Generate Xcode project and assemble Android preview.**

Run: `cd ios && xcodegen generate && cd .. && ./android/gradlew -p android :app:assemblePreviewDebug`

- [ ] **Step 7: Commit.**

```bash
git add ios/project.yml ios/PSMobile/Support/Info.plist android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml build/scripts/tests/test_sidebuild_identity.py
git commit -m "build: add isolated preview app identities"
```

### Task 3: Add generic object configuration overrides to the C ABI

**Files:**
- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/src/psmobile_session.hpp`
- Test: `core/test/psm_contract_tests.cpp`
- Test: `core/test/psm_testcli.c`

**Interfaces:**
- Consumes: `psm_object_id`, `psm_config_meta_for`, global `psm_config_get`, `ModelObject::config`.
- Produces:
  - `psm_object_config_get(psm_session *, psm_object_id, const char *, char *, size_t)`
  - `psm_object_config_set(psm_session *, psm_object_id, const char *, const char *)`
  - `psm_object_config_reset(psm_session *, psm_object_id, const char *)`
  - `psm_object_config_is_overridden(psm_session *, psm_object_id, const char *) -> int32_t`

- [ ] **Step 1: Write a failing round-trip contract test.**

```cpp
TEST_CASE("object config overrides inherit set and reset") {
    auto s = test_session_with_cube();
    const auto id = first_object_id(s.get());
    char value[64]{};
    REQUIRE(psm_object_config_is_overridden(s.get(), id, "fill_density") == 0);
    REQUIRE(psm_object_config_set(s.get(), id, "fill_density", "35%") == PSM_OK);
    REQUIRE(psm_object_config_get(s.get(), id, "fill_density", value, sizeof(value)) == PSM_OK);
    CHECK(std::string(value) == "35%");
    CHECK(psm_object_config_is_overridden(s.get(), id, "fill_density") == 1);
    REQUIRE(psm_object_config_reset(s.get(), id, "fill_density") == PSM_OK);
    CHECK(psm_object_config_is_overridden(s.get(), id, "fill_density") == 0);
}
```

- [ ] **Step 2: Run the contract test and confirm it fails at missing symbols.**

Run: `cmake --build build-out/host --target psm_contract_tests`

Expected: compile/link failure naming `psm_object_config_set`.

- [ ] **Step 3: Declare the four ABI functions after the global configuration functions.**

```c
PSM_API psm_result psm_object_config_get(psm_session *s, psm_object_id id,
    const char *key, char *out, size_t out_cap);
PSM_API psm_result psm_object_config_set(psm_session *s, psm_object_id id,
    const char *key, const char *value);
PSM_API psm_result psm_object_config_reset(psm_session *s, psm_object_id id,
    const char *key);
PSM_API int32_t psm_object_config_is_overridden(psm_session *s,
    psm_object_id id, const char *key);
```

- [ ] **Step 4: Implement inherited reads and validated object writes.**

```cpp
// Read: object->config first, otherwise s->config.
// Write: clone the global option into object->config when absent, then
// set_deserialize_nothrow(key, value, substitutions).
// Reset: object->config.erase(key).
// Every successful write/reset: history_checkpoint, ++config_revision,
// mark_design_changed exactly once.
```

- [ ] **Step 5: Add negative tests for missing object, unknown key, invalid value, and reset of an inherited key.**

Run: `cmake --build build-out/host --target psm_contract_tests && build-out/host/core/test/psm_contract_tests`

Expected: all contract cases pass and the project revision changes only after successful mutations.

- [ ] **Step 6: Commit the ABI contract.**

```bash
git add core/include/psmobile_core.h core/src/psmobile_core.cpp core/src/psmobile_session.hpp core/test
git commit -m "feat(core): add object setting overrides"
```

### Task 4: Add shared inspector and favorites rules

**Files:**
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/rules/InspectorContract.kt`
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/rules/FavoriteSettingRules.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/rules/InspectorContractTest.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/rules/FavoriteSettingRulesTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/FavoriteSettings.kt`

**Interfaces:**
- Consumes: selected object ID, known configuration keys, persisted favorite keys.
- Produces:
  - `enum class InspectorScope { PROJECT, OBJECT }`
  - `data class InspectorTarget(val scope: InspectorScope, val objectId: Int?)`
  - `InspectorContract.target(selectedObjectId: Int?): InspectorTarget`
  - `FavoriteSettingRules.sanitize(favorites, availableKeys): List<String>`

- [ ] **Step 1: Write failing common tests for no selection, object selection, stable order, and removed keys.**

```kotlin
@Test fun selectionChoosesObjectScope() {
    assertEquals(InspectorTarget(InspectorScope.OBJECT, 42), InspectorContract.target(42))
    assertEquals(InspectorTarget(InspectorScope.PROJECT, null), InspectorContract.target(null))
}

@Test fun favoritesFollowCatalogOrderAndDropUnknownKeys() {
    assertEquals(listOf("layer_height", "fill_density"),
        FavoriteSettingRules.sanitize(
            setOf("fill_density", "gone", "layer_height"),
            listOf("layer_height", "fill_density", "brim_width")))
}
```

- [ ] **Step 2: Run common tests and confirm missing-type failures.**

Run: `./android/gradlew -p android :shared:allTests`

- [ ] **Step 3: Implement the two pure rule objects and make Android `FavoriteSettings` delegate to them.**

```kotlin
object InspectorContract {
    fun target(selectedObjectId: Int?) = selectedObjectId?.let {
        InspectorTarget(InspectorScope.OBJECT, it)
    } ?: InspectorTarget(InspectorScope.PROJECT, null)
}
```

- [ ] **Step 4: Run shared and Android favorite tests.**

Run: `./android/gradlew -p android :shared:allTests :app:testProductionDebugUnitTest --tests de.psmobile.ui.FavoriteSettingsTest`

Expected: all tests pass.

- [ ] **Step 5: Commit the shared presentation contracts.**

```bash
git add android/shared/src/commonMain android/shared/src/commonTest android/app/src/main/java/de/psmobile/ui/FavoriteSettings.kt android/app/src/test/java/de/psmobile/ui/FavoriteSettingsTest.kt
git commit -m "feat(shared): define inspector and favorite rules"
```

### Task 5: Unify multi-bed presentation rules

**Files:**
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/rules/BedStripContract.kt`
- Test: `android/shared/src/commonTest/kotlin/de/psmobile/shared/rules/BedStripContractTest.kt`
- Modify: `ios/PSMobile/Screens/BedSelector.swift`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`

**Interfaces:**
- Consumes: bed count, active index, locked flags, object/instance counts.
- Produces: `BedStripItem`, `ArrangeAvailability`, and identical add/select/lock/rename rules for Simple and Advanced.

- [ ] **Step 1: Write failing tests for active bed, new-bed item, locked arrange, and removal of the final bed.**

```kotlin
@Test fun oneBedStillOffersAddButNotRemove() {
    val state = BedStripContract.state(listOf(BedInput("Bett 1", false, 1)), 0)
    assertTrue(state.canAdd)
    assertFalse(state.items.single().canRemove)
}
```

- [ ] **Step 2: Run `:shared:allTests` and confirm failure.**
- [ ] **Step 3: Implement `BedStripContract` without Compose or SwiftUI dependencies.**
- [ ] **Step 4: Make both platform selectors consume the contract values and retain one selected-bed source.**
- [ ] **Step 5: Run core bed tests plus shared tests.**

Run: `cmake --build build-out/host --target psm_contract_tests && build-out/host/core/test/psm_contract_tests && ./android/gradlew -p android :shared:allTests`

- [ ] **Step 6: Commit.**

```bash
git add android/shared/src/commonMain android/shared/src/commonTest ios/PSMobile/Screens/BedSelector.swift android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt
git commit -m "feat(shared): unify multi-bed presentation rules"
```

### Task 6: Make project mutations transactional and stale-safe

**Files:**
- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/src/psmobile_session.hpp`
- Test: `core/test/psm_contract_tests.cpp`
- Modify: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfilePackageStore.kt`
- Test: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfilePackageStoreTest.kt`

**Interfaces:**
- Consumes: project/model import, preset selection, profile-package activation, project revision, slice revision.
- Produces: failed import/load/profile operations restore the prior valid state; stale slice results cannot export or send.

- [ ] **Step 1: Add contract tests that serialize the current project/config before invalid model import, truncated 3MF load, invalid preset switch, and failed object override; compare revision and serialized state afterward.**
- [ ] **Step 2: Add Android package tests that inject failure before activation, during swap, and after activation health check; assert the prior profile directory remains active.**
- [ ] **Step 3: Run focused tests and confirm at least the injected mid-operation failures expose partial state.**
- [ ] **Step 4: Wrap core mutations in temporary model/config values and swap only on success; preserve the existing history checkpoint semantics.**
- [ ] **Step 5: Keep profile updates in staging, validate required files/checksum, atomically swap active/previous directories, and restore previous on failed health check.**
- [ ] **Step 6: Add a stale-result assertion before every `psm_gcode_export` and printer send adapter using the captured project/config revision.**
- [ ] **Step 7: Run core contract and Android profile-update tests.**
- [ ] **Step 8: Commit.**

```bash
git add core/src/psmobile_core.cpp core/src/psmobile_session.hpp core/test/psm_contract_tests.cpp android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfilePackageStore.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfilePackageStoreTest.kt
git commit -m "fix: keep project mutations transactional"
```

### Task 7: Close the baseline gate

**Files:**
- Modify: `docs/arbeitsjournal.md`
- Create: `docs/qa/gate-0-baseline.md`

**Interfaces:**
- Consumes: Tasks 1-4 test output and commit IDs.
- Produces: Gate 0 evidence used by every later plan.

- [ ] **Step 1: Run the complete fast suite from a clean worktree.**

```powershell
cmake --build build-out/host --target psm_contract_tests
& build-out/host/core/test/psm_contract_tests
./android/gradlew -p android :shared:allTests :app:testProductionDebugUnitTest
git diff --check
git status --short
```

Expected: all commands return 0 and `git status --short` is empty before documenting evidence.

- [ ] **Step 2: Record base commit, imported hashes, test counts, ABI version, and residual baseline risks in `docs/qa/gate-0-baseline.md`.**
- [ ] **Step 3: Append the Gate 0 result to `docs/arbeitsjournal.md`.**
- [ ] **Step 4: Commit the gate evidence.**

```bash
git add docs/qa/gate-0-baseline.md docs/arbeitsjournal.md
git commit -m "docs: record side-build baseline gate"
```
