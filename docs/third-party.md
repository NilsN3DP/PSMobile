# Third-party components

Slicer Mobile is licensed under AGPL-3.0 (see `LICENSE`). The app shows the same
list under *App settings → Open source & licenses*; the source of truth is
`android/shared/src/commonMain/kotlin/de/psmobile/shared/rules/OpenSource.kt`.

| Component | Licence | Purpose | Platform |
| --- | --- | --- | --- |
| [PrusaSlicer 2.9.6](https://github.com/prusa3d/PrusaSlicer) | AGPL-3.0 | Slicing engine, printer and filament profiles, G-code preview (libvgcode) | both |
| [libbgcode](https://github.com/prusa3d/libbgcode) | AGPL-3.0 | Binary G-code (.bgcode) | both |
| [heatshrink](https://github.com/atomicobject/heatshrink) | ISC | Compression inside .bgcode | both |
| [Open CASCADE Technology](https://dev.opencascade.org) | LGPL-2.1 with exception | STEP import | both |
| [Boost](https://www.boost.org) | BSL-1.0 | C++ foundation libraries | both |
| [CGAL](https://www.cgal.org) | GPL-3.0 / LGPL-3.0 | Mesh booleans and geometry | both |
| [GMP / MPFR](https://gmplib.org) | LGPL-3.0 | Exact arithmetic for CGAL | both |
| [Eigen](https://eigen.tuxfamily.org) | MPL-2.0 | Linear algebra | both |
| [oneTBB](https://github.com/uxlfoundation/oneTBB) | Apache-2.0 | Parallel slicing | both |
| [NLopt](https://github.com/stevengj/nlopt) | LGPL-2.1 / MIT | Optimisation (arrange, supports) | both |
| [OpenVDB, OpenEXR, c-blosc](https://www.openvdb.org) | MPL-2.0 / BSD-3-Clause | Volumetric operations | both |
| [Qhull](http://www.qhull.org) | Qhull License | Convex hulls | both |
| [cereal, nlohmann/json, NanoSVG, Expat, zlib, libpng, libjpeg](https://github.com/prusa3d/PrusaSlicer/tree/master/deps) | BSD-3-Clause / MIT / zlib / PNG / IJG | Serialisation, JSON, SVG, XML and image formats | both |
| [Kotlin Multiplatform, kotlinx](https://kotlinlang.org) | Apache-2.0 | Shared rules module of both apps | both |
| [Jetpack Compose, AndroidX, CameraX](https://developer.android.com/jetpack) | Apache-2.0 | User interface, camera for QR pairing | android |
| [Coil](https://github.com/coil-kt/coil) | Apache-2.0 | Image loading | android |
| [ZXing](https://github.com/zxing/zxing) | Apache-2.0 | QR codes | android |
| [Google ML Kit Barcode Scanning](https://developers.google.com/ml-kit/terms) | Google APIs Terms of Service | QR scanning | android |

PrusaSlicer itself bundles further libraries (Clipper, admesh, miniz, libnest2d,
semver, …); their licences are in the PrusaSlicer source tree.
