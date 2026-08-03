# Patches gegen PrusaSlicer 2.9.6

AGPL-3.0 verlangt, dass Aenderungen offengelegt werden. Statt einen Fork
zu pflegen, liegen alle Eingriffe hier als lesbarer Patch - so ist auf
einen Blick sichtbar, was angefasst wurde, und ein Rebase auf eine neue
PrusaSlicer-Version bleibt moeglich.

Anwenden (nach `build/scripts/bootstrap.sh`):

```bash
git -C external/PrusaSlicer apply patches/*.patch
```

## 0001-android-cross-build.patch

Fuenf Aenderungen, alle noetig, um den Kern fuer Android zu bauen.
Keine davon aendert das Verhalten des Slicers.

| Datei | Aenderung | Grund |
| --- | --- | --- |
| `deps/+GMP/GMP.cmake` | eigene `_gmp_cxxflags` ohne `-std=gnu11` und `-Wmissing-prototypes` | Beides ist C-only. GCC verzeiht das in `CXXFLAGS`, clang++ des NDK bricht mit "C++ compiler not available" ab. |
| `deps/+MPFR/MPFR.cmake` | nutzt die neuen `_gmp_cxxflags` | dasselbe |
| `deps/+PNG/PNG.cmake` | baut libpng auch fuer `ANDROID` | Upstream baut es nur fuer MSVC/Apple und erwartet sonst die Systembibliothek - die es auf Android nicht gibt. |
| `CMakeLists.txt` | `find_package(CURL)` an `SLIC3R_GUI` gekoppelt | `libslic3r` enthaelt null curl-Referenzen; Netzwerk sitzt nur in der GUI. Siehe ADR E-09. |
| `CMakeLists.txt` | `find_package(OpenGL)` und `GLEW` an `SLIC3R_GUI` gekoppelt | Auf Android gibt es kein Desktop-OpenGL, nur GLES. |
| `CMakeLists.txt` | `IS_CROSS_COMPILE` auch bei `CMAKE_CROSSCOMPILING` | Upstream erkennt Cross-Compiling nur an der Apple-Multi-Arch-Konstellation. Sonst wird das fuer arm64 uebersetzte Hilfsprogramm `encoding-check` auf dem x86-Host ausgefuehrt: "Exec format error". |

Die letzten drei sind Kandidaten fuer einen Upstream-Beitrag - sie
verbessern die Cross-Compile-Faehigkeit ganz allgemein, nicht nur fuer
Android.

## 0002-libvgcode-gles-und-gui-freie-typen.patch

Vier Aenderungen, damit die G-Code-Vorschau ohne GUI und mit GLES baut.

| Datei | Aenderung | Grund |
| --- | --- | --- |
| `src/libvgcode/src/ViewerImpl.hpp` | `set_positions` und `set_heights_widths_angles` nehmen `std::array<float, 4>` statt `Vec3` | Der Aufrufer uebergibt seit SPE-2411 `Vec4`. Der Nicht-ES-Pfad wurde damals auf `GL_RGBA32F` umgestellt, der ES-Pfad nicht - er uebersetzt bei Upstream gar nicht. |
| `src/libvgcode/src/ViewerImpl.cpp` | `GL_RGB32F`/`GL_RGB` zu `GL_RGBA32F`/`GL_RGBA`, `sizeof(Vec3)` mit | Folge davon. Der Kommentar im selben Bestand sagt es bereits: manche Treiber koennen `GL_RGB32F` nicht als Texturformat, deshalb der zusaetzliche ungenutzte Float. |
| `src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.{hpp,cpp}` | `GUI_Preview.hpp` und die `OptionType`-Umwandlung hinter `PSM_NO_GUI_TYPES` | Der Wrapper ist ansonsten wx-frei und genau deshalb brauchbar. Nur diese eine Umwandlung zieht die gesamte GUI herein, und wir benutzen sie nicht. |

Die ES-Korrektur ist ein Upstream-Kandidat: sie repariert einen Pfad,
der bei PrusaSlicer selbst nicht uebersetzt.

**Dieser Patch ist nachtraeglich entstanden.** Die Aenderungen lagen
zuerst nur in der Arbeitskopie auf dem Build-Host und fehlten damit in
jedem frischen Checkout - aufgefallen ist es erst, als der Kern auf dem
Mac an genau diesen Stellen abbrach, waehrend Android laengst baute.

## 0003-ios-glad-occt-und-step.patch

Drei Aenderungen, damit derselbe Bestand auch fuer iOS baut. Android
bleibt in allen dreien unberuehrt.

| Datei | Aenderung | Grund |
| --- | --- | --- |
| `src/libvgcode/glad/src/gles2.c` | Apple-Zweig: EGL-Typen nachgebildet, `eglGetProcAddress` durch `dlsym(RTLD_DEFAULT, ...)` ersetzt, Framework-Pfad in die Bibliotheksnamen | iOS hat kein EGL und keine `libGLESv2.dylib`. Die GLES-Symbole kommen aus `OpenGLES.framework` und liegen nach dem Binden bereits im Prozess. Ohne das sucht glad einen Header, den es nicht gibt. |
| `src/libslic3r/CMakeLists.txt` | `OCCTWrapper` nur noch bei `SLIC3R_ENABLE_FORMAT_STEP`, dazu `SLIC3R_OCCT_WRAPPER_LINKED` | Upstream bindet den Wrapper bei Apple bedingungslos ein - auch wenn STEP abgeschaltet ist. Auf Android faellt das nie auf, weil der Zweig dort nicht greift. |
| `src/libslic3r/Format/STEP.cpp` | direkter Aufruf von `load_step_internal` an denselben Merker gebunden | Folge davon: ohne den Wrapper fehlt das Symbol beim Linken. Jetzt nimmt die Datei denselben Nachlade-Weg wie auf Linux, der bei abgeschaltetem STEP nie beschritten wird. |

Der OCCT-Zweig ist ein Upstream-Kandidat: die Option `SLIC3R_ENABLE_FORMAT_STEP`
soll den Formatimport abschalten, tut es auf Apple aber nur halb.
