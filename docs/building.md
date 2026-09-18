# Building

Three parts, three environments:

| Part | Where | Duration (first run) |
| --- | --- | --- |
| Core + dependencies for Android | Linux with Docker (the scripts start the container) | deps ~1–2 h per ABI, core ~15 min |
| Android app | Windows/Linux/Mac with JDK 21 and Android SDK | minutes |
| Core + app for iOS | Mac with Xcode 26, CMake, Ninja, XcodeGen, JDK 17 | deps ~1–2 h per target, app minutes |

All scripts report what they do and are repeatable: what is finished is
skipped.

## 1. Sources and build image

```bash
build/scripts/bootstrap.sh
```

Fetches PrusaSlicer `version_2.9.6` into `external/PrusaSlicer`, applies the
patches from `patches/` and builds the Docker image `psmobile-ndk:1` with the
Android NDK (`build/docker/`). Without Docker (e.g. on a Mac) only the image
part fails – that is fine for the iOS route.

The dependencies come from PrusaSlicer's own `deps/` tree; what is dropped for
mobile (wxWidgets, GLEW, OpenCSG, Catch2, CURL, OpenSSL) is listed in
`build/scripts/dep-excludes.sh`.

## 2. Android

```bash
export ANDROID_ABI=arm64-v8a          # or x86_64 for the emulator
build/scripts/build-deps.sh           # Boost, TBB, CGAL, OCCT, ... -> build-out/destdir-<abi>
build/scripts/build-core.sh           # libslic3r + libpsmobile_core.so    -> build-out/core-<abi>
build/scripts/stage-native.sh         # .so into android/app/src/main/jniLibsFixed/<abi>/
build/scripts/stage-resources.sh      # profiles, shaders, settings catalogue into app/src/main/assets
cd android
./gradlew :app:assembleProductionDebug
```

- Gradle needs **JDK 21**; on Windows for example
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.
- The native core is deliberately **not** built by Gradle but staged –
  `android/app/build.gradle.kts` explains why.
- Two flavours: `production` (package `de.upsm`) and `preview`
  (`de.upsm.preview`). `assembleDebug` builds both; install
  `app/build/outputs/apk/production/debug/app-production-<abi>-debug.apk`.
- Release: `./gradlew :app:assembleProductionRelease :app:bundleProductionRelease
  -PPSMOBILE_UPLOAD_STORE_FILE=<keystore.jks>` (passwords via
  `PSMOBILE_UPLOAD_STORE_PASSWORD`, `PSMOBILE_UPLOAD_KEY_ALIAS`,
  `PSMOBILE_UPLOAD_KEY_PASSWORD`).

## 3. iOS

```bash
build/scripts/macos-bootstrap.sh      # tools, sources, patches
build/scripts/build-ios.sh all        # deps + core for device (OS64) and simulator (SIMULATORARM64)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
build/scripts/build-shared-ios.sh all # Kotlin Multiplatform framework PSMShared
cd ios && xcodegen generate && open PSMobile.xcodeproj
```

- `project.yml` is the source of the Xcode project; run `xcodegen generate`
  after every new Swift file.
- Without an Apple developer account the app runs in the simulator; for a
  device set `DEVELOPMENT_TEAM` in `project.yml`.
- TestFlight: `tools/ios-testflight.sh` archives and exports the IPA to
  `build-out/testflight-<version>/` (signature "Apple Distribution", profile in
  the login keychain – hence in a logged-in Terminal session, not over SSH).
  More in `ios/README.md`.

## 4. Versions

`versionName`/`versionCode` in `android/app/build.gradle.kts` and
`CFBundleShortVersionString`/`CFBundleVersion` in
`ios/PSMobile/Support/Info.plist` must be equal – the parity guard checks
that. Every upload to Play or TestFlight needs a higher build number.

## 5. Core contract tests

```bash
build/scripts/run-core-tests.sh x86_64   # psm_contract_tests on emulator/device (adb), core from build-out/core-<abi>
```

They are built with the core (`PSM_BUILD_TESTS=ON`). `core/test/psm_slice_cli.c`
slices a file without the app – the basis of the remote-slice server
(`PSM_BUILD_SLICE_CLI=ON`).
