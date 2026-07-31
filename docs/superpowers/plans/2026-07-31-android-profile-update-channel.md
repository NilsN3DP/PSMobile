Exit code: 0
Wall time: 1.4 seconds
Output:
# Android-Profilupdatekanal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Android-App prüft beim Start nebenläufig auf eine kompatible Profilbasis, lädt sie nur nach ausdrücklicher Zustimmung sicher herunter und kann sie sofort oder beim nächsten Neustart aktivieren.

**Architecture:** Die Paketverwaltung liegt unter `de.psmobile.slicing.profileupdate` und trennt Manifest, Entscheidungen, Netzwerk und Dateisystem. `ResourceInstaller` liefert immer eine komplette aktive Ressourcenbasis; nur `SlicerService` schließt und startet eine PsmCore-Sitzung für den sicheren Wechsel.

**Tech Stack:** Kotlin/JVM 17, Android API 26+, Kotlin Coroutines/Flow, Compose Material 3, `HttpURLConnection`, `java.nio.file.Files`, JUnit 4 und AGP 8.7.3.

## Global Constraints

- Der Start darf durch Netz, DNS, Timeout oder einen defekten Updatekanal nicht verzögert werden; offline bleibt die aktive oder APK-basierte Profilbasis vollständig nutzbar.
- Nur HTTPS und nur die in `BuildConfig.PROFILE_UPDATE_ALLOWED_HOSTS` hinterlegten Hosts dürfen Manifest oder Paket liefern.
- Jedes Paket braucht SHA-256, eine dreiteilige numerische Version und `min_slic3r_version`; das Minimum darf die eingebaute Core-Version nicht überschreiten.
- APK-Ressourcen bleiben unveränderlicher Fallback. `fallback`, `active`, `staged` und `previous` sind getrennt; Aktivierung ist rollbackfähig.
- Ein laufender Slice wird nie unterbrochen. Sofortiges Anwenden bleibt als staged Update erhalten, bis der Slice beendet ist.
- Bei geladenem Projekt oder ungespeicherten Drucker-, Print- oder Filament-Presets muss der Nutzer eine sichere Entscheidung treffen.
- Deutsche Entscheidungen: **Jetzt aktualisieren**, **Später**, **Erst beim nächsten Update fragen**, danach **Jetzt verwenden** und **Beim Neustart**.
- ColorMix, PrusaLink-Synchronisierung und INDX-Hardwaresteuerung liegen außerhalb dieses Plans. Eine Profilbasis darf aber INDX 4T/8T enthalten.
- Produktions-Builds setzen den vollständigen HTTPS-Wert von `-PprofileManifestUrl` sowie den passenden kommagetrennten Hostwert von `-PprofileUpdateAllowedHosts`. Ein leerer Manifest-URL-Wert deaktiviert den Check sicher; ohne freigegebenen Host darf kein angeblich funktionierender Onlinekanal ausgeliefert werden.

---

## File Structure

| Datei | Verantwortung |
|---|---|
| `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateModel.kt` | Version, Manifest, Zustände und reine Entscheidungslogik. |
| `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileManifestCodec.kt` | Striktes JSON-Decoding, Host- und Versionsprüfung. |
| `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateHttp.kt` | Injektierbare HTTPS-Transportgrenze. |
| `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfilePackageStore.kt` | SHA-256, ZIP-Prüfung, staging, Aktivierung und Rollback. |
| `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateRepository.kt` | Startcheck, Entscheidungen, Download und StateFlow. |
| `android/app/src/main/java/de/psmobile/slicing/ResourceInstaller.kt` | APK-Fallback und Auswahl der aktiven Ressourcenbasis. |
| `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt` | Sicherheitsprüfung und PsmCore-Neustart. |
| `android/app/src/main/java/de/psmobile/ui/ProfileUpdateDialogs.kt` | Gemeinsame Compose-Dialoge für Simple und Advanced. |
| `android/app/src/main/java/de/psmobile/MainActivity.kt` | Einmaliger Dialoghost und Speichern-Callback. |
| `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml` | JSON-Abhängigkeit sowie Updatekanal-BuildConfig. |
| `build/scripts/package-profile-update.ps1`, `docs/release/profile-update-channel.md` | Reproduzierbares Paket und Releaseablauf. |

## Update-Paketvertrag

~~~json
{
  "version": "2.5.5",
  "package_url": "https://updates.example.invalid/psmobile/profiles/2.5.5.zip",
  "sha256": "64 lowercase hexadecimal characters",
  "min_slic3r_version": "2.9.6",
  "release_notes": ["CORE One INDX 4T und 8T", "Aktualisierte Toolchange- und Filamentprofile"]
}
~~~

Das ZIP enthält `profiles/PrusaResearch.ini`, `profiles/PrusaResearch.idx`, `profiles/PrusaResearchSLA.ini`, `profiles/PrusaResearchSLA.idx` und `shaders/ES/`. Es enthält keine absoluten Pfade, keine `..`-Segmente und keine Symlinks.

### Task 1: Reine Version-, Manifest- und Angebotslogik

**Files:**
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateModel.kt`
- Create: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileUpdateModelTest.kt`

**Interfaces:**
- Produces: `ProfileVersion.parse(raw: String): ProfileVersion?`, `ProfileManifest`, `OfferDecision`, `ProfileUpdateState`, `UpdateOffer.shouldOffer(remote, skipped, shownThisRun)`.
- Consumes: keine Android-Klassen.

- [ ] **Step 1: Write the failing decision tests**

~~~kotlin
@Test fun `higher patch is offered unless it was skipped`() {
    val current = ProfileVersion.parse("2.5.4")!!
    val remote = ProfileVersion.parse("2.5.5")!!
    assertTrue(remote > current)
    assertFalse(UpdateOffer.shouldOffer(remote, skipped = remote, shownThisRun = false))
}

@Test fun `later only suppresses this launch while skip waits for a newer version`() {
    val skipped = ProfileVersion.parse("2.5.5")!!
    assertFalse(UpdateOffer.shouldOffer(skipped, skipped, shownThisRun = false))
    assertTrue(UpdateOffer.shouldOffer(ProfileVersion.parse("2.5.6")!!, skipped, shownThisRun = false))
}
~~~

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileUpdateModelTest`

Expected: FAIL because the package and types do not exist.

- [ ] **Step 3: Implement the minimal complete model**

Define strict numeric `ProfileVersion(major: Int, minor: Int, patch: Int)` and reject `2.5`, `v2.5.5`, negative values and suffixes. Define:

~~~kotlin
data class ProfileManifest(
    val version: ProfileVersion,
    val packageUrl: URI,
    val sha256: String,
    val minSlic3rVersion: ProfileVersion,
    val releaseNotes: List<String>,
)
enum class OfferDecision { UpdateNow, Later, SkipUntilNewer }
sealed interface ProfileUpdateState {
    data object Idle : ProfileUpdateState
    data object Checking : ProfileUpdateState
    data class Offer(val manifest: ProfileManifest) : ProfileUpdateState
    data class Downloading(val manifest: ProfileManifest, val percent: Int?) : ProfileUpdateState
    data class ReadyToApply(val manifest: ProfileManifest) : ProfileUpdateState
    data class Deferred(val manifest: ProfileManifest, val reason: String) : ProfileUpdateState
    data class Failed(val message: String) : ProfileUpdateState
}
~~~

`Later` remains process-local; persisting a skipped version belongs to Task 4.

- [ ] **Step 4: Run the focused test and commit**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileUpdateModelTest`

Expected: PASS.

~~~powershell
git add android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateModel.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileUpdateModelTest.kt
git commit -m "feat: add profile update decision model"
~~~

### Task 2: Manifest codec and explicit release configuration

**Files:**
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileManifestCodec.kt`
- Create: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileManifestCodecTest.kt`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Consumes: `ProfileManifest`, `ProfileVersion` from Task 1.
- Produces: `ProfileManifestCodec.decode(json: String, manifestUri: URI, allowedHosts: Set<String>): Result<ProfileManifest>`.

- [ ] **Step 1: Write failing JSON validation tests**

~~~kotlin
@Test fun `codec accepts a valid HTTPS manifest from an approved host`() {
    val result = ProfileManifestCodec.decode(
        validManifestJson("2.5.5", "2.9.6"),
        URI("https://updates.example.test/manifest.json"),
        setOf("updates.example.test"),
    )
    assertEquals(ProfileVersion.parse("2.5.5"), result.getOrThrow().version)
}

@Test fun `codec rejects HTTP foreign hosts malformed checksum and invalid version`() {
    assertTrue(ProfileManifestCodec.decode(httpJson(), URI("https://updates.example.test/m.json"), setOf("updates.example.test")).isFailure)
    assertTrue(ProfileManifestCodec.decode(foreignHostJson(), URI("https://updates.example.test/m.json"), setOf("updates.example.test")).isFailure)
    assertTrue(ProfileManifestCodec.decode(badShaJson(), URI("https://updates.example.test/m.json"), setOf("updates.example.test")).isFailure)
}
~~~

- [ ] **Step 2: Run to verify failure**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileManifestCodecTest`

Expected: FAIL because `ProfileManifestCodec` is undefined.

- [ ] **Step 3: Add JSON and BuildConfig support, then implement the codec**

Add `kotlinx-serialization-json` version `1.7.3` to the catalog and `implementation(libs.kotlinx.serialization.json)`. Enable `buildFeatures.buildConfig = true` and define:

~~~kotlin
val manifestUrl = providers.gradleProperty("profileManifestUrl").orNull.orEmpty()
val allowedHosts = providers.gradleProperty("profileUpdateAllowedHosts").orNull.orEmpty()
buildConfigField("String", "PROFILE_UPDATE_MANIFEST_URL", "\"$manifestUrl\"")
buildConfigField("String", "PROFILE_UPDATE_ALLOWED_HOSTS", "\"$allowedHosts\"")
~~~

Decode only the five contract keys. Require a nonempty notes array, a lowercase 64-hex SHA-256, strict versions, HTTPS, and an allowed manifest/package host.

- [ ] **Step 4: Verify and commit**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileManifestCodecTest`

Expected: PASS.

~~~powershell
git add android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileManifestCodec.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileManifestCodecTest.kt
git commit -m "feat: validate profile update manifests"
~~~

### Task 3: Transactional package store and fallback-preserving installer

**Files:**
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfilePackageStore.kt`
- Create: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfilePackageStoreTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/ResourceInstaller.kt`

**Interfaces:**
- Consumes: `ProfileManifest` from Task 1.
- Produces: `stage(zip: File, manifest: ProfileManifest): Result<File>`, `activateStaged(): Result<Unit>`, `activateStagedOnLaunch(): Result<Boolean>`, `rollback(): Result<Unit>`, `activeRoot(): File`.

- [ ] **Step 1: Write failing staging and rollback tests**

Use JUnit `TemporaryFolder` and `ZipOutputStream` fixtures.

~~~kotlin
@Test fun `valid package stages without replacing active resources`() {
    val before = store.activeRoot().canonicalPath
    val staged = store.stage(validResourcesZip(), manifest()).getOrThrow()
    assertTrue(File(staged, "profiles/PrusaResearch.ini").isFile)
    assertEquals(before, store.activeRoot().canonicalPath)
}

@Test fun `checksum failure and path traversal do not change active resources`() {
    assertTrue(store.stage(pathTraversalZip(), manifest(sha256 = "0".repeat(64))).isFailure)
    assertEquals(fallbackRoot.canonicalPath, store.activeRoot().canonicalPath)
    assertFalse(File(temp.root, "staged").exists())
}
~~~

- [ ] **Step 2: Run to verify failure**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfilePackageStoreTest`

Expected: FAIL because `ProfilePackageStore` is undefined.

- [ ] **Step 3: Implement verified same-volume transactions**

Under `filesDir/profile-resources`, use exactly `fallback/`, `active/`, `staged/`, `previous/` and `download.part`. Stream SHA-256 before extraction. Extract into a unique sibling directory, reject absolute/canonical escapes, then verify every mandatory path. Use `Files.move(..., ATOMIC_MOVE)` into `staged`; if unsupported, rename only after full validation. Activation renames `active` to `previous`, then `staged` to `active`; if the second move fails, restore `previous` immediately.

Refactor `ResourceInstaller.ensureInstalled(context)` to copy APK assets only into `fallback` and return `active` when it exists. It must never erase online `active` because `versionName` changes.

- [ ] **Step 4: Add activation/rollback tests, verify and commit**

Add assertions that a staged version becomes active only on activation, rollback restores the exact prior root, and an incomplete ZIP never appears as staged.

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfilePackageStoreTest`

Expected: PASS.

~~~powershell
git add android/app/src/main/java/de/psmobile/slicing/ResourceInstaller.kt android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfilePackageStore.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfilePackageStoreTest.kt
git commit -m "feat: stage profile resources atomically"
~~~

### Task 4: Background repository, download and remembered decisions

**Files:**
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateHttp.kt`
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateRepository.kt`
- Create: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileUpdateRepositoryTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: `state: StateFlow<ProfileUpdateState>`, `checkOnLaunch()`, `downloadOffered()`, `later()`, `skipUntilNewer()`, `deferToRestart()`.
- Defines: `interface ProfileUpdateHttp { suspend fun get(uri: URI): ByteArray }` and `interface UpdateDecisionStore` with skipped, active, staged and activate-on-launch values.

- [ ] **Step 1: Write failing fake-HTTP repository tests**

~~~kotlin
@Test fun `launch check emits offer for a newer compatible manifest`() = runTest {
    val repository = repository(http = FakeHttp.success(validManifestJson("2.5.5", "2.9.6")), active = "2.5.4")
    repository.checkOnLaunch()
    advanceUntilIdle()
    assertTrue(repository.state.value is ProfileUpdateState.Offer)
}

@Test fun `offline leaves active profile usable and the state idle`() = runTest {
    val repository = repository(http = FakeHttp.failure(IOException("offline")), active = "2.5.4")
    repository.checkOnLaunch()
    advanceUntilIdle()
    assertEquals(ProfileUpdateState.Idle, repository.state.value)
    assertEquals("2.5.4", repository.activeVersion().toString())
}
~~~

- [ ] **Step 2: Run to verify failure**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileUpdateRepositoryTest`

Expected: FAIL because the transport and repository do not exist.

- [ ] **Step 3: Implement transport and repository**

Add `kotlinx-coroutines-test` version `1.9.0` to the catalog and `testImplementation(libs.kotlinx.coroutines.test)` before using `runTest` and `advanceUntilIdle`. `HttpUrlConnectionProfileUpdateHttp` must require HTTPS before opening, disable redirects, use 3,000 ms connect and 5,000 ms read timeouts, accept only status 200 and cap packages at 20 MiB. Compare `manifest.minSlic3rVersion` with `ProfileVersion.parse(PsmCore.coreVersion())`; an unparsable core version is incompatible. Persist `profileUpdate.skippedVersion`, `profileUpdate.activeVersion`, `profileUpdate.stagedVersion` and `profileUpdate.activateOnNextLaunch` in existing `psmobile` preferences.

`checkOnLaunch()` returns immediately and works on `Dispatchers.IO`. Disabled configuration, offline, timeout, malformed, checksum-invalid and incompatible manifests resolve to `Idle`, keep active files intact and only log diagnostics. Equal/older/skipped versions do not offer. `Later` is process-local; `SkipUntilNewer` persists; `downloadOffered()` reaches `ReadyToApply` only after Task 3 stages a valid full package.

- [ ] **Step 4: Add all decision cases, verify and commit**

Add equal version, incompatible `min_slic3r_version`, checksum mismatch, Later, persisted Skip and successful staging cases.

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileUpdateRepositoryTest`

Expected: PASS.

~~~powershell
git add android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateHttp.kt android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileUpdateRepository.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileUpdateRepositoryTest.kt
git commit -m "feat: check profile updates in background"
~~~

### Task 5: Safe session activation in SlicerService

**Files:**
- Create: `android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileActivationPolicy.kt`
- Create: `android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileActivationPolicyTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`

**Interfaces:**
- Consumes: `ProfileUpdateRepository`, `ProfilePackageStore`, `PsmCore.Change`.
- Produces: `SlicerService.profileUpdates: StateFlow<ProfileUpdateState>`, `requestApplyProfileUpdate(): ActivationRequirement`, `applyStagedProfileUpdate(keep: Map<PsmCore.PresetType, List<PsmCore.Change>>): Result<Unit>`, `activateStagedOnServiceStart(): Result<Boolean>`.
- Defines: `sealed interface ActivationRequirement { data object Ready; data object SliceRunning; data class ConfirmUnsaved(val projectLoaded: Boolean, val changes: Map<PsmCore.PresetType, List<PsmCore.Change>>) }`.

- [ ] **Step 1: Write failing activation-policy tests**

~~~kotlin
@Test fun `slice always blocks immediate activation`() {
    assertEquals(ActivationRequirement.SliceRunning, ProfileActivationPolicy.requirement(true, true, dirtyChanges))
}

@Test fun `loaded project or dirty preset requires confirmation`() {
    assertTrue(ProfileActivationPolicy.requirement(false, true, emptyMap()) is ActivationRequirement.ConfirmUnsaved)
    assertTrue(ProfileActivationPolicy.requirement(false, false, dirtyChanges) is ActivationRequirement.ConfirmUnsaved)
}
~~~

- [ ] **Step 2: Run to verify failure**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileActivationPolicyTest`

Expected: FAIL because `ProfileActivationPolicy` is undefined.

- [ ] **Step 3: Implement the controlled transition**

Serialize core creation, slice start and profile switching with one `Mutex`. In `ensureCore()`, first run `activateStagedOnServiceStart()`, then resolve `ResourceInstaller.ensureInstalled(this)`.

For immediate activation: reject `Progress.Running`; export loaded work to `cacheDir/profile-switch/session-before-update.3mf`; capture dirty changes; close old core; activate staged resources; create a new core; reload cached 3MF; reinstall configured printer keys; reapply accepted changes with `selectPresetKeeping`; refresh beds, objects, presets, quick settings, history, scene/config revisions and invalidate G-Code. On any failure close the new core, rollback, reopen previous resources and reload the cached project. Do not call a core method after `close()`.

- [ ] **Step 4: Verify policy and Android build, then commit**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.slicing.profileupdate.ProfileActivationPolicyTest :app:assembleDebug`

Expected: PASS and `BUILD SUCCESSFUL`.

~~~powershell
git add android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/slicing/profileupdate/ProfileActivationPolicy.kt android/app/src/test/java/de/psmobile/slicing/profileupdate/ProfileActivationPolicyTest.kt
git commit -m "feat: switch staged profiles without losing a session"
~~~

### Task 6: One dialog host, release artifacts and regression acceptance

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/ProfileUpdateDialogs.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/ProfileUpdateDialogStateTest.kt`
- Create: `android/app/src/androidTest/java/de/psmobile/ProfileUpdateFlowTest.kt`
- Create: `build/scripts/package-profile-update.ps1`
- Create: `docs/release/profile-update-channel.md`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt`
- Modify: `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`, `docs/release/android-beta-closure-audit.md`

**Interfaces:**
- Consumes: `SlicerService.profileUpdates`, `requestApplyProfileUpdate()` and `ActivationRequirement`.
- Produces: `ProfileUpdateDialogs(...)` and a reproducible `manifest.json` plus versioned ZIP.

- [ ] **Step 1: Write failing dialog-state tests**

~~~kotlin
@Test fun `offer has exactly the three requested actions`() {
    assertEquals(
        listOf("Jetzt aktualisieren", "Später", "Erst beim nächsten Update fragen"),
        ProfileUpdateDialogState.offerActions(),
    )
}

@Test fun `ready state asks for immediate use or restart`() {
    assertEquals(listOf("Jetzt verwenden", "Beim Neustart"), ProfileUpdateDialogState.readyActions())
}
~~~

- [ ] **Step 2: Run to verify failure**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest --tests de.psmobile.ui.ProfileUpdateDialogStateTest`

Expected: FAIL because `ProfileUpdateDialogState` is undefined.

- [ ] **Step 3: Implement the application-level dark dialog flow**

Render `ProfileUpdateDialogs` exactly once in `MainActivity`, above both `SimpleModeScreen` and `SlicerScreen`. `Offer` shows title **Neue Drucker- und Materialprofile verfügbar**, notes and the three first decisions. `ReadyToApply` has **Jetzt verwenden** and **Beim Neustart**. `SliceRunning` says **Der Slice läuft weiter. Die neuen Profile werden beim nächsten Start verwendet.**

For `ConfirmUnsaved`, show loaded-project and dirty-preset counts. **Speichern unter…** calls existing `saveProject(saveAs = true)` and resumes only from its success callback; **Änderungen übertragen** uses captured change lists; **Verwerfen** passes empty lists; **Abbrechen** leaves the session and staged files untouched. A `Failed` state is dismissible. Use Material 3 with 48 dp targets and existing dark colors; no screen owns a second copy of this state.

- [ ] **Step 4: Add release packaging and device test**

Implement this deterministic script contract:

~~~powershell
pwsh -File build/scripts/package-profile-update.ps1 `
  -ResourceRoot android/app/src/main/assets/psresources `
  -Version 2.5.5 `
  -MinSlic3rVersion 2.9.6 `
  -PackageBaseUrl https://updates.example.test/psmobile/profiles `
  -ReleaseNote 'CORE One INDX 4T und 8T' `
  -ReleaseNote 'Aktualisierte Toolchange- und Filamentprofile' `
  -OutputDir build-out/profile-channel
~~~

It must stop on absent mandatory files, zip only relative paths, compute lowercase SHA-256 and create UTF-8 manifest JSON. Document upload of immutable ZIP plus atomically replaced manifest, checksum verification and server rollback by republishing the previous manifest.

Add catalog entries `androidx-test-ext-junit` version `1.2.1`, `androidx-test-espresso-core` version `3.6.1` and `androidx-ui-test-junit4`; add `androidTestImplementation(libs.androidx.test.ext.junit)`, `androidTestImplementation(libs.androidx.test.espresso.core)`, `androidTestImplementation(platform(libs.androidx.compose.bom))` and `androidTestImplementation(libs.androidx.ui.test.junit4)`. The device test injects a deterministic fake repository and proves: offer → download → **Beim Neustart** keeps the first session; a second launch displays active version `2.5.5`.

- [ ] **Step 5: Verify, manually stress the flow, and commit**

Run: `cd android; .\\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest --console=plain`

Expected: `BUILD SUCCESSFUL` and all connected tests passing.

On the emulator record these results in `docs/release/android-beta-closure-audit.md`: offline cold start; all three offer responses; corrupt checksum; incompatible minimum Core version; immediate activation with empty project; save/transfer/discard/abort with loaded 3MF and dirty printer, print, filament profiles; active long slice; INDX 4T/8T setup and multiextruder G-Code; portrait/landscape rotation while every dialog is open.

~~~powershell
git add android/app/src/main/java/de/psmobile/ui/ProfileUpdateDialogs.kt android/app/src/test/java/de/psmobile/ui/ProfileUpdateDialogStateTest.kt android/app/src/androidTest/java/de/psmobile/ProfileUpdateFlowTest.kt android/app/src/main/java/de/psmobile/MainActivity.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt android/app/build.gradle.kts android/gradle/libs.versions.toml build/scripts/package-profile-update.ps1 docs/release/profile-update-channel.md docs/release/android-beta-closure-audit.md
git commit -m "feat: ship profile update user flow"
~~~

## Self-Review

### Spec coverage

| Anforderung | Planaufgaben |
|---|---|
| Nebenläufiger Startcheck und Offline-Fallback | 3, 4, 6 |
| HTTPS, Host, Manifest, SHA-256, Core-Kompatibilität | 2, 3, 4 |
| fallback/active/staged/previous und Rollback | 3, 5 |
| Jetzt/Später/erst beim nächsten Update fragen | 1, 4, 6 |
| Jetzt verwenden/beim Neustart | 4, 5, 6 |
| Slice- und Arbeitsdatenschutz | 5, 6 |
| INDX-Presets in neuer Basis | 3, 6 |
| Unit-, Gerät- und Emulatorabnahme | 1–6 |

### Placeholder scan

Alle Arbeitsschritte enthalten konkrete Dateien, Typen, Befehle und erwartete Testergebnisse. Der externe Veröffentlichungs-Host ist bewusst ein verpflichtender Build-Parameter, weil im Repository aktuell kein freigegebener Host hinterlegt ist.

### Type consistency

`ProfileVersion`, `ProfileManifest` und `ProfileUpdateState` entstehen in Task 1. Task 3 besitzt ausschließlich Dateien und Transaktionen. Task 4 koordiniert nur Manifest und staging. Nur Task 5 darf die native Sitzung schließen oder Ressourcen aktivieren. Task 6 konsumiert ausschließlich Service-State und hält keine Dateisystemlogik.
