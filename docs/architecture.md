# Architecture

As of September 2026, version 0.2.2.

## Layers

```
+---------------------------+   +---------------------------+
|  android/app  Compose     |   |  ios/  SwiftUI            |   UI, twice (twins)
+------------+--------------+   +-------------+-------------+
             |  JNI (android/jni)             |  bridging header
             v                                v
+-------------------------------------------------------------+
|  android/shared   Kotlin Multiplatform                       |   rules, once
|  settings catalogue, defaults, visibility, texts             |   (JVM + iOS framework)
+-------------------------------------------------------------+
|  core/   psmobile_core.h  (pure C ABI)                       |   C++, once
|  session, model I/O, presets, slice job, export, preview     |
+-------------------------------------------------------------+
|  viewport/   OpenGL ES renderer                              |   C++, once
|  bed, models, gizmos, painting, G-code (libvgcode)           |
+-------------------------------------------------------------+
|  external/PrusaSlicer 2.9.6   libslic3r, libnest2d, libvgcode|   unchanged + patches/
+-------------------------------------------------------------+
|  cross-built dependencies (Boost, TBB, CGAL, OCCT, ...)      |
+-------------------------------------------------------------+
```

Two rules hold this together:

1. **Everything that is not presentation lives below the C ABI or in the
   shared module.** What sits above is written twice – so as little as
   possible.
2. **iOS is the reference, Android the 1:1 twin.** Every file under
   `ios/PSMobile/Screens` and `ios/PSMobile/UI` has a counterpart under
   `android/app/src/main/java/de/psmobile/ui`, every identifier
   (`accessibilityIdentifier` / `testTag`) and every text pair exists on both
   sides. `tools/paritaet_pruefen.py` checks this before every commit (see
   [twin-conventions.md](twin-conventions.md)).

## Directories

| Path | Content |
| --- | --- |
| `core/include/psmobile_core.h` | the only public boundary to the core |
| `core/src/` | C ABI over libslic3r: session, presets, extruders, toggles (dependency rules of the settings), ZIP/3MF |
| `core/test/` | contract tests and a command-line slicer for the host |
| `viewport/` | platform-neutral GLES renderer: camera, picking, gizmos, painting, preview |
| `android/shared/` | Kotlin Multiplatform: rules both apps need (`rules/`), window scaling (`ui/`) |
| `android/app/` | Compose app, services (slicer, PrusaLink, remote), JNI binding in `android/jni` |
| `ios/` | SwiftUI app; `project.yml` for XcodeGen |
| `build/scripts/`, `build/docker/` | cross build: Docker image with NDK, deps, core, staging |
| `cmake/toolchains/` | Android and iOS toolchains |
| `patches/` | every change to PrusaSlicer, as readable patches |
| `tools/` | parity guard, screenshot tour, iOS build/TestFlight scripts |
| `docker/remote-slice/` | compose file for the optional headless slicer |

## Data flow

- **Session** (`psm_session`): holds model, presets (PrusaSlicer's
  `PresetBundle`), beds and the slice state. Android owns it in the
  `SlicerService`, iOS in the `SlicerModel`. The viewport shows the same
  session, not a copy.
- **Settings**: the catalogue (pages, groups, keys, visibility
  Simple/Advanced/Expert) is extracted from PrusaSlicer
  (`build/scripts/extract-ui.py`) and ships as JSON in the app assets;
  dependencies (`psm_config_enabled`) are computed by the core like the
  desktop does.
- **Profiles**: the official PrusaSlicer profiles from `resources/profiles`
  ship with the app (`stage-resources.sh`) and are installed into the app's
  data folder on first start.
- **Slicing**: its own thread in the core, TBB parallelises inside;
  progress and cancel via callbacks. Result: G-code/bgcode in the cache plus
  preview data (`final_preview_data`) for statistics and the layer slider.
- **Sending**: PrusaLink (Digest/API key) and OctoPrint run in the native
  network layer (OkHttp / URLSession), not in the core (E-09).

## Threading

- UI thread: only Compose/SwiftUI, never block.
- GL thread: its own context; scene changes are queued as a snapshot
  (`ViewportView.kt` / `PSMGLView.perform`).
- Core: session mutex; slice job and preview build on their own threads.

## Gestures in the viewport

Order on touch-down: active paint tool → gizmo handles → selected object
(direct drag) → camera (orbit). Two fingers: zoom/pan; in the scale tool the
pinch scales the object. Both sides in `SceneView.kt` and `ViewportView.swift`,
commented identically.

## What is deliberately not ported

SLA, the wxWidgets UI, the desktop configuration wizard (replaced by the
first-run setup), Prusa account/Connect. The reasoning is in
[decisions.md](decisions.md).
