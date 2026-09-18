# Testing

Four levels, inside out. Before any claim "the two sides are equal", whatever
supports the claim gets run.

## 1. Parity guard (before every commit)

```bash
git config core.hooksPath tools/hooks     # once
python3 tools/paritaet_pruefen.py         # by hand
```

Checks the source: every iOS screen file has a Kotlin twin, identifiers and
text pairs (`st("English", "Deutsch")`) match on both sides, self-test steps
and version numbers are equal. Rules in [twin-conventions.md](twin-conventions.md).
Bypass only with `git commit --no-verify` and a good reason (pure
documentation commits).

## 2. Rules in the shared module

```bash
cd android && ./gradlew :shared:testDebugUnitTest
```

Unit tests for everything both apps compute: settings catalogue, defaults,
layer range, extruder presentation, preview statistics.

## 3. UI tests (both platforms, the same cases)

| | Android | iOS |
| --- | --- | --- |
| Location | `android/app/src/androidTest/java/de/psmobile/ui/` | `ios/PSMobileUITests/` |
| Tooling | Compose test + uiautomator | XCUITest |
| Size | ~95 cases in 24 classes | ~104 cases in 30 classes |
| Run | `./gradlew :app:connectedProductionDebugAndroidTest` (emulator or device) | `xcodebuild test -scheme PSMobile -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)'` |

Both suites drive the app through the same identifiers and start it with the
same launch arguments (`-psm-reset-settings`, `-psm-preset-printer`,
`-psm-start-simple`, `-psm-load-cube`, …), so both sides test from the same
initial state. Single classes:

```bash
./gradlew :app:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.psmobile.ui.SimpleModeUITest
xcodebuild test ... -only-testing:PSMobileUITests/SimpleModeUITests
```

Notes from practice:

- The Android emulator with software GPU (`-gpu swiftshader_indirect`) renders
  the layer range of the preview wrongly – judge the preview only with the host
  GPU or on a device.
- On a real iPad, XCUITest needs "Developer → UI Automation" and an unlocked
  device; a hanging app process from an aborted run makes the next one fail
  with "Timed out while enabling automation mode"
  (`xcrun devicectl device process terminate`).

## 4. Screenshot tour and self-test

- `tools/rundgang-android.sh` and `ScreenshotTourUITests` (iOS) photograph the
  same screens through the same identifiers – for side-by-side comparison.
- The **built-in self-test** (App settings → Diagnostics) checks on the
  device: start core, set up printer, choose filament, load model, slice,
  write G-code, save and reload project, undo/redo, parts, painting,
  multi-colour, load test. Without a device in hand: `-psm-selbsttest-auto`
  as launch argument, report as Markdown in the cache (`cache/selbsttest/`).

## 5. Core

`core/test/psm_contract_tests.cpp` checks the C ABI (session, presets,
multi-bed 3MF, toggles); run via `build/scripts/run-core-tests.sh <abi>`.
