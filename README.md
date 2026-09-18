# Slicer Mobile

The real PrusaSlicer 2.9.6 engine on iPad, iPhone and Android – with a touch UI
built for tablets and phones. No cloud: models are sliced on the device and
sent straight to a PrusaLink/OctoPrint printer or exported as `.gcode`/`.bgcode`.

> **Unofficial.** Slicer Mobile is based on [PrusaSlicer](https://github.com/prusa3d/PrusaSlicer)
> (AGPL-3.0) and is released under the same licence. It is not affiliated with or
> endorsed by Prusa Research. Most of the code was written with AI coding agents –
> the project is too large for one person.

**Status:** beta. iOS via TestFlight, Android via a closed test on Google Play.
Want to test? Open an issue or send a message – iOS gets a TestFlight link,
Android needs the Google account e-mail used with Play.

## What it does

- Import STL / 3MF / OBJ / STEP (OCCT is compiled in), place, move, rotate,
  scale, arrange, multiple beds
- **Simple Mode** – three cards (supports, adhesion, print settings) and a
  slice button
- **Advanced Mode** – every PrusaSlicer print/filament/printer setting, on the
  same pages as the desktop, with the same dependency rules
- G-code preview with layer range, feature/extruder colours, time/filament/cost
- Paint-on supports, seam painting, MMU colour painting
- Send to PrusaLink / OctoPrint, export G-code / binary G-code
- The official PrusaSlicer printer and filament profiles – the output is the
  desktop's output, because it is the desktop engine
- Optional remote slicing on your own server (work in progress, see
  [docs/remote-slicing.md](docs/remote-slicing.md))

Tested with Original Prusa Mini, MK4S, CORE One, CORE One L and briefly an XL.
Klipper printers: sending over the network is untested; USB stick works.

## How it is built

| Layer | Where | Language |
| --- | --- | --- |
| Slicing engine | `external/PrusaSlicer` (fetched by `bootstrap.sh`, patched from `patches/`) | C++ |
| Mobile core: C API around libslic3r, profiles, preview data | `core/` | C++ |
| OpenGL ES viewport: bed, models, gizmos, G-code (libvgcode) | `viewport/` | C++ |
| Shared rules for both apps (settings catalogue, defaults, validation) | `android/shared/` (Kotlin Multiplatform) | Kotlin |
| Android app | `android/app/` (Jetpack Compose) | Kotlin |
| iOS app | `ios/` (SwiftUI, XcodeGen) | Swift |

iOS is the reference; Android is a 1:1 twin. Every screen, identifier and text
exists on both sides, and `tools/paritaet_pruefen.py` (pre-commit hook) refuses a
commit that lets them drift. Details: [docs/architecture.md](docs/architecture.md).

## Building

Short version – the full walk-through is in [docs/building.md](docs/building.md).

```bash
# 1. PrusaSlicer sources + Android NDK build image (Docker)
build/scripts/bootstrap.sh

# 2. Android: dependencies, core, stage into the app, APK
build/scripts/build-deps.sh && build/scripts/build-core.sh
build/scripts/stage-native.sh && build/scripts/stage-resources.sh
cd android && ./gradlew :app:assembleProductionDebug

# 3. iOS (on a Mac): see ios/README.md
build/scripts/macos-bootstrap.sh && build/scripts/build-ios.sh
build/scripts/build-shared-ios.sh all && cd ios && xcodegen && open PSMobile.xcodeproj
```

Tests: [docs/testing.md](docs/testing.md) – ~200 UI tests that run on both
platforms, core contract tests, and the built-in self-test in the app.

## Documentation

| File | Content |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | layers, data flow, threading, why native UI twice |
| [docs/building.md](docs/building.md) | building core, Android and iOS; release builds |
| [docs/testing.md](docs/testing.md) | test suites, parity guard, self-test |
| [docs/twin-conventions.md](docs/twin-conventions.md) | the twin rules (file pairs, identifiers, texts) |
| [docs/keeping-in-sync.md](docs/keeping-in-sync.md) | how to change something on both sides |
| [docs/decisions.md](docs/decisions.md) | design decisions E-01 … E-13 |
| [docs/remote-slicing.md](docs/remote-slicing.md) | self-hosted headless slicer (WIP) |
| [docs/third-party.md](docs/third-party.md) | third-party components and licences |
| [CHANGELOG.md](CHANGELOG.md) | what changed per version |
| [patches/README.md](patches/README.md) | the patches applied to PrusaSlicer |

The documentation is in English; code comments are in German (the language the
project was built in). The UI itself is English and German.

## Licence

AGPL-3.0 – see [LICENSE](LICENSE). PrusaSlicer is © Prusa Research and
contributors; the complete list of third-party components is in
[docs/third-party.md](docs/third-party.md) and in the app under
*App settings → Open source & licenses*.
