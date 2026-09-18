# Slicer Mobile – iOS

iOS is the reference UI; Android is its twin (see `../docs/twin-conventions.md`).

## What is here

| File | Purpose |
| --- | --- |
| `../cmake/toolchains/ios.cmake` | CMake toolchain, uses CMake's built-in iOS support |
| `../build/scripts/build-ios.sh` | deps and core build, counterpart of the Android scripts |
| `PSMobile/Core/PsmCore.swift` | Swift wrapper around the C ABI |
| `PSMobile/SlicerModel.swift` | state holder, counterpart of `SlicerService`/`SlicerModel` on Android |
| `PSMobile/PSMobileApp.swift` | SwiftUI entry point and routing |
| `PSMobile/Support/PSMobile-Bridging-Header.h` | makes `psmobile_core.h` visible to Swift |
| `project.yml` | XcodeGen spec – `xcodegen generate` creates the Xcode project |
| `PSMobile/Screens/` | Simple Mode, first-run setup, settings, projects, slice summary, … |
| `PSMobile/Viewport/` | OpenGL ES viewport, selection, camera, G-code layer range |
| `PSMobileUITests/` | simulator/device regressions that drive the UI |

## First steps on a Mac

```bash
# 1. tools
xcode-select --install
brew install cmake ninja xcodegen

# 2. sources (fresh clone)
build/scripts/bootstrap.sh          # fetches PrusaSlicer only; the Docker part fails on a Mac - that is fine

# 3. patches
git -C external/PrusaSlicer apply ../../patches/*.patch

# 4. dependencies and core (long, like on Android)
PSM_IOS_PLATFORM=OS64 build/scripts/build-ios.sh all

# 5. resources into the bundle
build/scripts/stage-resources.sh ios

# 6. regenerate the Xcode project after changes to project.yml, then test
cd ios && xcodegen generate
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile \
  -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)' test
```

## What tends to need attention

- **GMP/MPFR** build via autotools. The toolchain sets `TOOLCHAIN_PREFIX`,
  but the tools are named differently on macOS than on Linux. If `configure`
  stumbles, look at `build/scripts/mk-autotools-wrappers.sh` first.
- **`-fno-aligned-allocation`** is set by libslic3r for Apple targets. With
  deployment target 15.0 it is no longer necessary and can get in the way.
- **Simulator vs. device**: deps have to be built separately per platform
  (`OS64` and `SIMULATORARM64`). The simulator proves handling and the core
  contract, not memory or printer behaviour on an iPad.

## Before every release

PrusaSlicer is AGPL-3.0. Distribution through the iOS App Store is legally
unclear – see `../docs/decisions.md`, E-06. That is why iOS goes to testers via
TestFlight, not to the store.
