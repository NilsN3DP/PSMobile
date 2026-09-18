# Decisions (ADR)

Short format: decision, reasoning, consequence. Append changes as a new
entry, do not overwrite old ones.

---

## E-01 – Scope: a new client, not a port of the application

**Decision**: Slicer Mobile is a new touch client around the PrusaSlicer core,
not a port of the PrusaSlicer application.

**Reasoning**: `src/libslic3r` (190k LOC) has zero wxWidgets includes and is
portable C++17. `src/slic3r` (166k LOC, 404 files) is completely bound to
wxWidgets; wxWidgets has no Android port and `wxOSX/iPhone` is a stub. The
GUI layer therefore cannot come along technically.

**Consequence**: The desktop GUI is reference and template, not the porting
target. Logic that wrongly sits in the GUI layer (e.g. networking, profile
management) has to be identified and pulled down.

---

## E-02 – UI stack: native per platform over a shared C++ viewport

**Decision**: Kotlin/Jetpack Compose (Android) and SwiftUI (iOS) for the whole
chrome UI. The 3D viewport is a shared C++ code base on OpenGL ES, embedded via
`GLSurfaceView` and `CAEAGLLayer`.

**Reasoning**: The declared purpose of the project is good touch and pen
handling. Apple Pencil (pressure, tilt, hover, double tap) and S-Pen are only
fully reachable through the native input APIs. Flutter inserts a platform-view
layer exactly in this path and maps pen pressure more weakly; a pure ImGui UI
would be quick to build but feels foreign on a tablet and has no system
keyboard, no native scrolling and no accessibility.

**Consequence**: UI work happens twice. That is accepted deliberately.
Countermeasure: everything that is not presentation lives below the C ABI and
is built only once.

---

## E-03 – Bridge: a narrow C ABI, the viewport stays out of it

**Decision**: A stable C interface `psmobile_core.h` as the only boundary
between app and core. The viewport runs entirely in C++ and does **not** go
through the ABI per frame.

**Reasoning**: C++ symbols carry cleanly neither over JNI nor over Swift.
Per-frame calls over JNI would be a performance mistake.

**Consequence**: The UI only sends commands down and gets state and progress
back. Rendering and input handling in the viewport stay native.

---

## E-04 – Both platforms in parallel

**Decision**: Android and iOS are developed in parallel.

**Consequence**: iOS targets, the CMake iOS toolchain and the Swift bridge
are created from the start. Android is the leading platform for debugging
because only Android can be built on the Windows workstation. Since
September 2026 iOS is the UI reference and Android its twin (E-13,
[twin-conventions.md](twin-conventions.md)).

---

## E-05 – Build environment: Docker on a Linux host

**Decision**: The Android cross build runs in a reproducible Docker image on a
Linux build host (Docker, 20 cores, 31 GB RAM).

**Reasoning**: Boost b2 and the autotools-based dependencies cross-compile
far more painlessly on Linux than on Windows, and the server has more cores
than the workstation.

**Consequence**: All build steps are scripts in the image, not manual steps.
The build workspace belongs on a data volume, not inside the image.

---

## E-06 – Licence: AGPL-3.0, distribution via TestFlight / closed test

**Decision**: Slicer Mobile is licensed like PrusaSlicer (AGPL-3.0), the source
is public, and the app lists its components under "Open source & licenses" in
the app settings.

**Situation**: Apple's App Store terms are widely considered incompatible
with GPLv3/AGPLv3 (the VLC precedent). Therefore iOS goes to testers via
TestFlight, Android via the closed test on Google Play. Permanent store
distribution needs clarification with the rights holders.

---

## E-07 – Base is upstream 2.9.6, not a private fork

**Decision**: Slicer Mobile builds on `prusa3d/PrusaSlicer` tag `version_2.9.6`.

**Reasoning**: A clean, rebasable base. Private fork changes are GUI-near and
irrelevant for the mobile scope.

**Consequence**: Everything that has to change in PrusaSlicer is a patch
(E-11).

---

## E-08 – v1 scope: the FDM core

**Decision**: v1 covers import (STL/3MF/OBJ/STEP), placing/transforming,
auto-arrange, Prusa FDM profiles, slicing with progress and cancel, G-code
preview and export including PrusaLink.

**Deliberately out**: SLA/resin, a full profile editor.

**Consequence for the build**: `SLIC3R_GUI=OFF`. That removes wxWidgets,
GLEW, OpenCSG and Catch2 from the cross build. OpenVDB/OpenEXR/Blosc stay
(E-10), OCCT stays since September 2026 (STEP import), z3 stays because
`libseqarrange` depends on it and is linked publicly by `libslic3r`.

---

## E-09 – Networking stays in the native layer, no libcurl

**Decision**: CURL and OpenSSL are **not** built for Android/iOS. PrusaLink
and OctoPrint access is done by the native layer – OkHttp on Android,
URLSession on iOS.

**Reasoning**: Measured on the source: `libslic3r` contains **zero**
references to `curl/curl.h`. All network code sits in `src/slic3r/Utils/*`,
i.e. in the desktop GUI layer that is dropped anyway. Two big dependencies
less, and the native stacks can do more: system certificates, proxy
configuration, background transfers, Wi-Fi changes.

**Consequence**: PrusaSlicer's root `CMakeLists.txt` requires CURL
unconditionally. A patch couples the lookup to `SLIC3R_GUI` (`patches/`).

---

## E-10 – OpenVDB is built after all

**Decision**: OpenVDB, OpenEXR and Blosc are cross-built although v1 has no
SLA.

**Reasoning**: While building it turned out that `SLA/Hollowing.cpp` is
hard-coupled to `OpenVDBUtils.hpp` and is always compiled. Only the
`OpenVDBUtils*` sources are optional in `libslic3r/CMakeLists.txt`, not the
rest of the SLA layer. Patching OpenVDB out would have been an invasive
change to the slicer source; building it along cost one run without a single
error.

**Consequence**: A bigger library than necessary, but no fork ballast, and
SLA is available later without further build work. If size becomes a
problem, the right lever is `--gc-sections` plus LTO, not cutting source files.

---

## E-11 – Patches instead of a fork

**Decision**: All necessary changes to PrusaSlicer live as readable patches
in `patches/` and are applied after cloning. `external/PrusaSlicer` is not
checked in.

**Reasoning**: AGPL requires disclosure of changes. A patch stack shows at a
glance what was touched and can be rebased onto a new PrusaSlicer version. A
private fork would obscure that.

Details per patch: [../patches/README.md](../patches/README.md).

---

## E-12 – Take over instead of rebuilding (ground rule)

**Decision**: For every piece of UI the question is: can it be taken over as
data or logic? If yes, it is **extracted and never typed off**. Only real wx
widgets are rebuilt – and then as close to the original as possible.

**Trigger**: The first UI version had hand-picked settings, invented German
labels and Material icons. That was a rebuild although PrusaSlicer ships all
of it.

**What is taken over** (`build/scripts/extract-ui.py`):

| Source | Result |
| --- | --- |
| `GUI/Tab.cpp` | 20 settings pages, groups, 247 parameters in original order |
| `GUI/GLCanvas3D.cpp` | 15 tools with order, icon file and tooltip |
| `localization/*/PrusaSlicer_*.po` | the translations, verbatim |
| `resources/icons/*.svg` | the original icons |
| `libslic3r/PrintConfig` | type, limits, unit, enum values and **mode** per parameter |

The mode (`comSimple` / `comAdvanced` / `comExpert`) is already attached to
every option. Which setting appears at which level is therefore not a design
decision but inherited information.

**What cannot be taken over**: the wxWidgets widgets themselves. Drawing is
done with Compose/SwiftUI – but along the extracted structures, not to a
design of our own.

**Consequence**: The extractor explicitly reports what it could not resolve
(places where PrusaSlicer inserts options via variables). Those gaps are
visible, not silently lost.

---

## E-13 – Native UI per platform, shared rules

**Decision**: The UI is built on each platform with its own toolkit – Compose
on Android, SwiftUI on iOS. Whatever lies below and is not UI moves into a
shared Kotlin Multiplatform module and is written only once.

**Trigger**: Once the core ran on iOS, the question was how the UI gets
there. Measured: 17,930 lines of Kotlin, 12,904 of them UI. Writing that twice
is expensive; *maintaining* it twice is more expensive.

**Rejected: Compose Multiplatform.** It would have shared the 12,904 lines
and been roughly half as expensive to write. The argument against it was not
performance – slicing is C++, and the viewport hangs in the frame as a native
GL surface either way – but feel. The app uses Material building blocks in
many places: switches, dialogs, sliders, text fields. A Material switch looks
wrong on an iPhone, exactly where people touch most often. Plus the things you
do not see but feel: scroll physics, rubber-banding at list ends, the
magnifier in text selection, swipe back.

**What is shared**: everything without screen reference. Rules such as the
adhesion analysis, printer grouping, scaling thresholds, dependencies between
settings, the state handling around the core.

**What is not shared**: layout. Each platform arranges for itself.

**Consequence**: Double maintenance shrinks to layout. Whoever changes a rule
– when a brim is suggested, which setting is greyed out when – changes it
once and both apps follow. Only arrangements can drift, and that is noticed
by looking. The guard ([keeping-in-sync.md](keeping-in-sync.md)) catches the
rest.
