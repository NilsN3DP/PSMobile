# Patches against PrusaSlicer 2.9.6

AGPL-3.0 requires changes to be disclosed. Instead of maintaining a fork, every
change lives here as a readable patch – what was touched is visible at a
glance, and rebasing onto a new PrusaSlicer version stays possible.

Apply (after `build/scripts/bootstrap.sh`):

```bash
git -C external/PrusaSlicer apply patches/*.patch
```

## 0001-android-cross-build.patch

Five changes, all needed to build the core for Android. None of them changes
the slicer's behaviour.

| File | Change | Reason |
| --- | --- | --- |
| `deps/+GMP/GMP.cmake` | own `_gmp_cxxflags` without `-std=gnu11` and `-Wmissing-prototypes` | Both are C-only. GCC tolerates them in `CXXFLAGS`, the NDK's clang++ aborts with "C++ compiler not available". |
| `deps/+MPFR/MPFR.cmake` | uses the new `_gmp_cxxflags` | same |
| `deps/+PNG/PNG.cmake` | builds libpng for `ANDROID` too | Upstream builds it only for MSVC/Apple and otherwise expects the system library – which Android does not have. |
| `CMakeLists.txt` | `find_package(CURL)` coupled to `SLIC3R_GUI` | `libslic3r` contains zero curl references; networking lives only in the GUI. See ADR E-09. |
| `CMakeLists.txt` | `find_package(OpenGL)` and `GLEW` coupled to `SLIC3R_GUI` | There is no desktop OpenGL on Android, only GLES. |
| `CMakeLists.txt` | `IS_CROSS_COMPILE` also on `CMAKE_CROSSCOMPILING` | Upstream detects cross-compiling only in the Apple multi-arch constellation. Otherwise the `encoding-check` helper built for arm64 is executed on the x86 host: "Exec format error". |

The last three are candidates for an upstream contribution – they improve
cross-compiling in general, not only for Android.

## 0002-libvgcode-gles-und-gui-freie-typen.patch

Four changes so the G-code preview builds without the GUI and with GLES.

| File | Change | Reason |
| --- | --- | --- |
| `src/libvgcode/src/ViewerImpl.hpp` | `set_positions` and `set_heights_widths_angles` take `std::array<float, 4>` instead of `Vec3` | The caller passes `Vec4` since SPE-2411. The non-ES path was switched to `GL_RGBA32F` back then, the ES path was not – it does not even compile upstream. |
| `src/libvgcode/src/ViewerImpl.cpp` | `GL_RGB32F`/`GL_RGB` to `GL_RGBA32F`/`GL_RGBA`, `sizeof(Vec3)` along with it | Consequence of the above. The comment in the same code already says it: some drivers cannot use `GL_RGB32F` as a texture format, hence the extra unused float. |
| `src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.{hpp,cpp}` | `GUI_Preview.hpp` and the `OptionType` conversion behind `PSM_NO_GUI_TYPES` | The wrapper is otherwise wx-free and exactly therefore usable. Only this one conversion pulls in the whole GUI, and we do not use it. |

The ES fix is an upstream candidate: it repairs a path that does not compile
in PrusaSlicer itself.

## 0003-ios-glad-occt-und-step.patch

Three changes so the same code base also builds for iOS. The OCCT parts apply
to Android as well since September 2026: the wrapper is linked statically on
both phones (`APPLE OR ANDROID`) because there is no loadable module next to
an app. The app asks the core (`psm_supports_step`) whether STEP is available.
The glad part is iOS-only.

| File | Change | Reason |
| --- | --- | --- |
| `src/libvgcode/glad/src/gles2.c` | Apple branch: EGL types emulated, `eglGetProcAddress` replaced by `dlsym(RTLD_DEFAULT, ...)`, framework path in the library names | iOS has no EGL and no `libGLESv2.dylib`. The GLES symbols come from `OpenGLES.framework` and are already in the process after linking. Without this glad looks for a header that does not exist. |
| `src/libslic3r/CMakeLists.txt` | `OCCTWrapper` only with `SLIC3R_ENABLE_FORMAT_STEP`, plus `SLIC3R_OCCT_WRAPPER_LINKED` | Upstream links the wrapper unconditionally on Apple – even when STEP is disabled. On Android this never shows because that branch does not apply there. |
| `src/libslic3r/Format/STEP.cpp` | direct call of `load_step_internal` bound to the same flag | Consequence: without the wrapper the symbol is missing at link time. Now the file takes the same lazy-load path as on Linux, which is never taken when STEP is disabled. |

The OCCT branch is an upstream candidate: the option `SLIC3R_ENABLE_FORMAT_STEP`
is meant to disable the format import but only half does so on Apple.

## 0004-linux-headless-build.patch

One change so the core builds natively for Linux (remote-slice server, see
`docs/remote-slicing.md`).

| File | Change | Reason |
| --- | --- | --- |
| `CMakeLists.txt` | `find_package(DBus1 REQUIRED)` coupled to `SLIC3R_GUI` | DBus is only needed for the GUI's desktop notifications (see `src/slic3r/CMakeLists.txt`). Without the GUI a slim Docker image has no DBus development package with a CMake config installed, and needs none. |

An upstream candidate: the same case as CURL/OpenGL in 0001 – a library only
the GUI needs, but which is searched for unconditionally.

## 0005-occt-cmake4.patch

One change so OCCT configures with CMake 4.

| File | Change | Reason |
| --- | --- | --- |
| `deps/+OCCT/OCCT.cmake` | passes `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` to OCCT | OCCT 7.6.1 declares `cmake_minimum_required(VERSION 3.0)`, which CMake 4 rejects. Putting the policy into the recipe keeps it out of every caller's command line. |
