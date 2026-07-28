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
