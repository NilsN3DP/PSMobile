Exit code: 0
Wall time: 0.4 seconds
Output:
# iOS-Parität und Beta-RC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` task-by-task. Every production change starts with its focused failing test.

**Goal:** iOS wird ein belastbarer, zweisprachiger FDM-Slicer-Client mit dem gleichen Simple-/Advanced-Arbeitsablauf wie Android; INDX/MMU, ColorMix, PrusaLink und sichere Profilupdates funktionieren auf beiden Plattformen, bevor die Android- und iOS-Beta-RCs anhand echter Geräte geprüft werden.

**Architecture:** Der C++-Kern bleibt alleinige Quelle für Projekt, Slicing, Extruder und Einstellungen. Regeln, die keine UI oder Plattform-API brauchen, liegen in `android/shared` als Kotlin-Multiplatform-Modul und werden vom iOS-Framework `PSMShared` sowie von Android konsumiert. SwiftUI und Compose bleiben dünne Hosts für denselben Zustand; Netzwerkzugänge speichern Geheimnisse nur in Keychain beziehungsweise Android Keystore.

**Tech Stack:** C++20/CMake, Kotlin Multiplatform, Kotlin/JVM 17, Compose Material 3, Swift 5.10, SwiftUI, XCTest/XCUITest, URLSession, Android Keystore, iOS Keychain, Xcode 26.3 and Android API 26+.

## Global Constraints

- FDM ist der Umfang; ZIP, STEP und SLA sind weiterhin ausdrücklich ausgeschlossen.
- Ein INDX-/MMU-Drucker zeigt die Positionen **1…N**, nie „Kopf“, und erlaubt Material und Farbe je Position.
- ColorMix berechnet sichtbare Mischfarben aus den tatsächlich zugewiesenen Extruderfarben; er darf keine Filamentzuweisung verändern.
- Profilprüfungen sind asynchron, offline sicher und ohne freigegebenen HTTPS-Host vollständig deaktiviert.
- Vor sofortigem Profilwechsel fragt die App bei geladenem Projekt, ungespeicherten Preset-Änderungen oder laufendem Slice nach Speichern/Verwerfen/Abbrechen; ein laufender Slice wird nie unterbrochen.
- Deutscher und englischer Text müssen für jeden neu geänderten Screen vorhanden sein; die Auswahl aus der Ersteinrichtung gilt sofort.
- Compact Portrait, Tablet Portrait und Tablet Landscape müssen bedienbar sein; Inhalte dürfen weder Systemleisten überlagern noch abgeschnitten sein.
- Keine ungezielten `git add .`, keine Änderungen an fremden uncommitteten Dateien und kein Commit ohne frische, passende Tests.

---

### Task 1: Reproduzierbaren Integrationsstand herstellen

**Files:**
- Modify: `docs/feature-matrix.json`
- Modify: `docs/10-funktionsvergleich.md`
- Create: `docs/release/ios-integration-inventory-2026-08-03.md`
- Test: `build/scripts/feature-report.py --check`

**Interfaces:**
- Consumes: lokaler Git-Stand, `user289137@FF738.macincloud.com:~/psmobile`, iOS-XCTest-Result.
- Produces: einen dokumentierten Commit-Bereich mit iOS-Dateien, eine korrekte Feature-Matrix und eine Liste bewusst verbliebener Plattformunterschiede.

- [ ] **Step 1: Inventar ohne Mutation erstellen**

Run:

```powershell
git status --short
git log --oneline --decorate -20
ssh -i $env:USERPROFILE\.ssh\macincloud2 user289137@FF738.macincloud.com 'cd ~/psmobile && git status --short && git log --oneline -20'
```

Record every file existing only on the Mac and every dirty local file. Classify it as iOS port, shared networking, Android work, generated output, or unrelated user work.

- [ ] **Step 2: Verify the existing iOS test target before moving files**

Run on the Mac:

```bash
cd ~/psmobile/ios
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' test
```

Expected: XCTest and XCUITest finish with zero failures. Capture the test count and Xcode version in the inventory; a failed baseline is a stop-and-fix item, not an integration commit.

- [ ] **Step 3: Move only verified source and resource files into an integration branch**

Stage explicit paths: `ios/`, `android/shared/`, `build/scripts/build-shared-ios.sh`, build files that declare them, and their tests. Exclude `.git-commit-msg.tmp`, build output and unrelated Android work. Resolve duplicate histories before copying: preserve the version with the passing iOS test suite and use `git diff --no-index` to account for every difference.

- [ ] **Step 4: Correct the authoritative status documents**

Replace the obsolete iOS “scaffold/not started” entries in `docs/feature-matrix.json` with separate, evidence-backed entries for native core, 3MF project workflow, Simple Mode, Advanced shell, viewport/preview and remaining parity items. Update `docs/10-funktionsvergleich.md` to state that iOS is active and link the inventory rather than claiming iOS is deferred.

- [ ] **Step 5: Verify and commit the integration boundary**

Run:

```powershell
python build/scripts/feature-report.py --check
git diff --check
git status --short
```

Commit only the explicit integration set with `chore: establish reproducible iOS integration baseline`.

### Task 2: Shared INDX/MMU and ColorMix rules

**Files:**
- Modify: `android/shared/src/commonMain/kotlin/de/psmobile/shared/ColorMixCodec.kt` (or create it if absent)
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/ExtruderPresentation.kt`
- Create: `android/shared/src/commonTest/kotlin/de/psmobile/shared/ExtruderPresentationTest.kt`
- Modify: `android/shared/src/commonTest/kotlin/de/psmobile/shared/ColorMixCodecTest.kt`

**Interfaces:**
- Produces: `ExtruderPresentation.slotLabel(index: Int): String`, `ExtruderPresentation.slots(count: Int): List<ExtruderSlot>`, and `ColorMixCodec.mix(colors: List<String>, ratios: List<Float>): String?`.
- `slotLabel(0)` returns `"1"`; no UI constructs labels itself from “head”.
- `mix` accepts only `#RRGGBB`, equal-length positive ratios, returns uppercase `#RRGGBB`, and returns `null` for invalid input.

- [ ] **Step 1: Write failing common tests**

```kotlin
@Test fun eight_positions_are_one_based_and_complete() {
    assertEquals((1..8).map(Int::toString), ExtruderPresentation.slots(8).map { it.label })
}

@Test fun equal_red_and_blue_mix_to_visible_purple() {
    assertEquals("#800080", ColorMixCodec.mix(listOf("#FF0000", "#0000FF"), listOf(1f, 1f)))
}
```

- [ ] **Step 2: Verify RED**

```powershell
cd android
.\gradlew.bat :shared:allTests --tests de.psmobile.shared.ExtruderPresentationTest
```

Expected: failure because the presentation type is absent.

- [ ] **Step 3: Implement the pure rules**

`slots` returns an empty list for zero and clamps neither valid 4 nor valid 8. `mix` uses linear weighted RGB channels, rounds half-up, rejects blank colors, ratios `<= 0`, unequal vectors and a total ratio of zero. Keep JSON codec persistence separate from the mixing calculation.

- [ ] **Step 4: Verify GREEN and commit**

```powershell
cd android
.\gradlew.bat :shared:allTests
```

Commit: `feat: share INDX positions and ColorMix rules`.

### Task 3: iOS extruder material and ColorMix UI

**Files:**
- Modify: `ios/PSMobile/SlicerModel.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Create: `ios/PSMobile/Screens/ColorMixView.swift`
- Modify: `ios/PSMobile/Screens/SettingsView.swift`
- Create: `ios/PSMobileUITests/ExtruderAndColorMixUITests.swift`

**Interfaces:**
- Consumes: `PsmCore.extruderCount`, `extruderFilament`, `setExtruderFilament`, `extruderColor`, `setExtruderColor`; `ExtruderPresentation` and `ColorMixCodec` from `PSMShared`.
- Produces: `ColorMixView` reachable from Simple Material and Advanced Settings, with accessibility IDs `extruder.0`…`extruder.7`, `colormix.preview`, and `colormix.save`.

- [ ] **Step 1: Write failing XCUITests**

```swift
func testINDXShowsEightOneBasedMaterialPositions() {
    launchINDX8T()
    app.buttons["simple.material"].tap()
    for index in 0..<8 { XCTAssertTrue(app.otherElements["extruder.\(index)"].exists) }
    XCTAssertFalse(app.staticTexts["Kopf 1"].exists)
}

func testColorMixPreviewChangesWithoutChangingAssignedFilament() {
    launchINDX4T(); let before = selectedFilament(0)
    app.buttons["simple.colormix"].tap(); choose("#FF0000", for: 0); choose("#0000FF", for: 1)
    XCTAssertEqual(app.otherElements["colormix.preview"].value as? String, "#800080")
    app.buttons["colormix.save"].tap(); XCTAssertEqual(selectedFilament(0), before)
}
```

- [ ] **Step 2: Verify RED on Mac**

```bash
cd ~/psmobile/ios
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' -only-testing:PSMobileUITests/ExtruderAndColorMixUITests test
```

Expected: test compile or assertion failure because the screen/identifiers are absent.

- [ ] **Step 3: Implement iOS composition**

Use `ExtruderPresentation.slots(count:)` for all Simple and Advanced labels. Reuse the existing searchable material-list component and its color chips; do not introduce a separate picker. `ColorMixView` edits temporary contributions, previews `ColorMixCodec.mix`, and saves only the named mixed color/preset metadata after confirmation. It must not overwrite `extruderFilament` unless the user explicitly chooses a material for that position.

- [ ] **Step 4: Verify tablet and compact presentation**

Run the focused XCUITest on an iPad simulator and an iPhone simulator. Add snapshot-content assertions that all eight positions are scrollable and 44-pt tappable. Run `xcodebuild ... test` for the entire scheme and commit `feat: add iOS INDX material and ColorMix workflow`.

### Task 4: Shared secure PrusaLink and profile-update policy

**Files:**
- Modify: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/DigestAuth.kt`
- Modify: `android/shared/src/commonMain/kotlin/de/psmobile/shared/net/PrusaLinkRules.kt`
- Create: `android/shared/src/commonMain/kotlin/de/psmobile/shared/profileupdate/ProfileUpdatePolicy.kt`
- Create: `android/shared/src/commonTest/kotlin/de/psmobile/shared/profileupdate/ProfileUpdatePolicyTest.kt`
- Create: `ios/PSMobile/Networking/PrusaLinkClient.swift`
- Create: `ios/PSMobile/Networking/PrinterCredentialStore.swift`
- Create: `ios/PSMobile/ProfileUpdate/ProfileUpdateRepository.swift`
- Create: `ios/PSMobile/ProfileUpdate/ProfileUpdateView.swift`
- Create: `ios/PSMobileTests/ProfileUpdatePolicyTests.swift`

**Interfaces:**
- `ProfileUpdatePolicy.offer(remote:current:skipped:)` distinguishes Now, Later and SkipUntilNewer.
- `PrusaLinkClient.probe(_:)`, `upload(gcode:to:)` and `PrinterCredentialStore` use URLSession and Keychain; no password is stored in UserDefaults.
- `ProfileUpdateRepository.checkOnLaunch()` leaves state `.idle` when URL is empty, offline, invalid, HTTP or incompatible.

- [ ] **Step 1: Write failing shared policy tests and iOS tests**

```kotlin
@Test fun skippedVersionIsHiddenButNewerVersionIsOffered() {
    assertFalse(ProfileUpdatePolicy.offer("2.5.5", "2.5.4", "2.5.5"))
    assertTrue(ProfileUpdatePolicy.offer("2.5.6", "2.5.4", "2.5.5"))
}
```

```swift
func testCredentialsAreNotPersistedInUserDefaults() {
    try store.save(.init(host: "printer.local", apiKey: "secret"))
    XCTAssertNil(UserDefaults.standard.string(forKey: "printer.apiKey"))
    XCTAssertEqual(try store.load(host: "printer.local")?.apiKey, "secret")
}
```

- [ ] **Step 2: Verify RED**

Run `:shared:allTests` on Windows and `-only-testing:PSMobileTests/ProfileUpdatePolicyTests test` on the Mac. Each test must fail because the feature is missing, not because a simulator cannot start.

- [ ] **Step 3: Implement secure policy and iOS adapters**

Port the existing Android manifest constraints exactly: HTTPS, explicit allowed host list, strict `major.minor.patch`, lowercase SHA-256, package-size cap, no redirects, 3 s connect/5 s read timeout and Core-version compatibility. Keychain keys are scoped to host plus auth mode. API key and Digest are both supported, but cleartext remains an explicit per-printer opt-in. Profile packages stage to Application Support as `fallback`, `active`, `staged`, `previous`; activation rolls back atomically on failure.

- [ ] **Step 4: Add UI and transition tests**

Create XCUITests for the exact choices **Update now / Later / Ask again with next update** and then **Use now / On restart**. With a loaded project, assert the Save / Discard / Cancel confirmation appears. With a running slice, assert immediate apply is disabled and `On restart` remains available.

- [ ] **Step 5: Verify and commit**

Run shared tests, iOS unit and UI suites; run an API-Key and Digest probe/upload against a controlled test printer or a local deterministic HTTP fixture. Commit `feat: add iOS PrusaLink and safe profile updates`.

### Task 5: iOS Advanced workflow, translations, and responsive behavior

**Files:**
- Modify: `ios/PSMobile/PSMobileApp.swift`
- Modify: `ios/PSMobile/Screens/SettingsView.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobile/Screens/SimpleObjectBarView.swift`
- Create: `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`
- Create: `ios/PSMobile/UI/LocalizedCopy.swift`
- Create: `ios/PSMobileUITests/AdvancedWorkflowUITests.swift`
- Create: `ios/PSMobileUITests/ResponsiveLayoutUITests.swift`

**Interfaces:**
- Advanced has direct entry points for Print Settings, Filament Settings, Printer Settings, model tree, transform, arrange, layer/preview and slice.
- `LocalizedCopy.text(english:german:)` is the only new textual copy helper; `AppSettingsStore.language` selects the language immediately.
- Accessibility IDs: `advanced.objectTree`, `advanced.arrange`, `advanced.transform`, `advanced.printSettings`, `advanced.filamentSettings`, `advanced.printerSettings`.

- [ ] **Step 1: Write failing workflow tests**

```swift
func testAdvancedExposesEssentialSettingsWithoutOpeningABlurOfTiles() {
    launchAdvanced()
    for id in ["advanced.printSettings", "advanced.filamentSettings", "advanced.printerSettings", "advanced.objectTree"] {
        XCTAssertTrue(app.buttons[id].waitForExistence(timeout: 10))
    }
}

func testEnglishChangesSimpleAndAdvancedVisibleCopy() {
    setLanguage("en"); launchSimple()
    XCTAssertTrue(app.staticTexts["Print Settings"].exists)
    openAdvanced(); XCTAssertTrue(app.staticTexts["Printer Settings"].exists)
}
```

- [ ] **Step 2: Verify RED then implement focused controls**

Build a compact toolbar with one overflow menu, a persistent Advanced inspector on regular-width iPads, and sheets on compact widths. Reuse `ViewportView`, `SimpleModelSheetView` operations and `SettingField`; do not duplicate native core operations. The Simple Mode back action must navigate to its prior subpanel; tapping a blocked viewport area minimizes the current menu to workspace rather than closing the app route.

- [ ] **Step 3: Add orientation and safe-area tests**

For iPhone portrait, iPad portrait and iPad landscape assert: no element frame intersects the status/home indicator safe areas; all primary targets are at least 44×44 pt; the model viewport remains nonzero; opening/closing every Simple panel restores workspace. Test a left/right rotation gesture with a visible model and assert the yaw sign matches the Android convention.

- [ ] **Step 4: Verify full scheme and commit**

Run every `PSMobileTests` and `PSMobileUITests` case on both device classes. Commit `feat: complete responsive iOS advanced workflow`.

### Task 6: Android hardware gates and cross-platform regression audit

**Files:**
- Modify: `docs/release/android-beta-closure-audit.md`
- Modify: `docs/release/ios-beta-closure-audit.md`
- Modify: `docs/feature-matrix.json`
- Test: existing Android unit/instrumentation suites, iOS full scheme, physical-printer test records.

**Interfaces:**
- Produces signed-off evidence for every `not_started`, `coded` or device-gated entry in `docs/feature-matrix.json`; entries without evidence remain open.

- [ ] **Step 1: Establish test fixtures before manual testing**

Create/locate fixtures for an exact installed-printer 3MF, two-bed project, complex custom-G-code/Wipe-Tower project, INDX 4T/8T, 41-MB 3MF and API-Key/Digest test printers. Document source, expected object/bed/profile counts and SHA-256 in both beta audit documents.

- [ ] **Step 2: Run Android automated and device gates**

```powershell
cd android
.\gradlew.bat :shared:allTests :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest --console=plain
```

On real 4–6 GB and 8+ GB ARM devices: repeat a 41-MB project, twenty slices, screen-off background slicing, notification cancel, rotation during slice, all Simple/Advanced menus, multi-bed lock, 3MF object/project choice, save/reopen and PrusaLink API-Key/Digest upload. Record observed memory and failures; fix each reproducible failure with a red test before repeating that case.

- [ ] **Step 3: Run iOS simulator and physical-device gates**

```bash
cd ~/psmobile/ios
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.3.1' test
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,name=iPhone 16 Pro' test
```

On iPad: repeat import, multi-bed, INDX eight positions, ColorMix, profile-update decisions, simple and advanced navigation, slice/preview/share, rotation and real PrusaLink upload. Capture screenshots and crash logs in the iOS audit.

- [ ] **Step 4: Final documentation and release decision**

Set feature-matrix statuses only when matching evidence exists. Keep any environment-dependent gap explicit. Add license/third-party/privacy/store decision status rather than presenting the app as publicly releasable without it. Commit `docs: record Android and iOS beta RC evidence`.

## Self-Review

| Requirement | Tasks |
| --- | --- |
| Securely preserve and reconcile current iOS work | 1 |
| INDX/MMU positions, material and colors | 2, 3 |
| ColorMix | 2, 3 |
| PrusaLink and safe profile update | 4 |
| Advanced and Simple UI, translations, responsive behavior | 5 |
| Android stability, real-hardware and complete regression proof | 6 |
| Release / legal transparency | 6 |

The plan deliberately does not turn ZIP, STEP or SLA into scope. It does not treat a simulator build as proof of physical printer or memory behavior.
