package de.psmobile.shared.rules

/**
 * Was in Slicer Mobile steckt - die Liste hinter "Open Source & Lizenzen"
 * in den App-Einstellungen.
 *
 * Eine Stelle fuer beide Apps: PrusaSlicer steht unter AGPL-3.0, und die
 * verlangt, dass ein abgeleitetes Werk seinen Quellcode und die
 * verwendeten Bibliotheken nennt. Was hier fehlt, fehlt auf beiden
 * Seiten - und was hier steht, steht auf beiden gleich.
 */
object OpenSource {

    /** Wo der Quellcode von Slicer Mobile liegt. */
    const val REPO = "https://github.com/NilsN3DP/PSMobile"

    /** Die Lizenz von Slicer Mobile selbst - dieselbe wie PrusaSlicer. */
    const val LIZENZ = "AGPL-3.0"

    enum class Plattform { BEIDE, ANDROID, IOS }

    data class Komponente(
        val name: String,
        val lizenz: String,
        val url: String,
        /** Wofuer die Bibliothek gebraucht wird - englisch, deutsch. */
        val rolleEn: String,
        val rolleDe: String,
        val plattform: Plattform = Plattform.BEIDE,
    )

    val komponenten: List<Komponente> = listOf(
        Komponente("PrusaSlicer 2.9.6", "AGPL-3.0", "https://github.com/prusa3d/PrusaSlicer",
            "Slicing engine, printer and filament profiles, G-code preview (libvgcode)",
            "Slicing-Kern, Drucker- und Filamentprofile, G-Code-Vorschau (libvgcode)"),
        Komponente("libbgcode", "AGPL-3.0", "https://github.com/prusa3d/libbgcode",
            "Binary G-code (.bgcode)", "Binaerer G-Code (.bgcode)"),
        Komponente("heatshrink", "ISC", "https://github.com/atomicobject/heatshrink",
            "Compression inside .bgcode", "Kompression in .bgcode"),
        Komponente("Open CASCADE Technology", "LGPL-2.1 with exception", "https://dev.opencascade.org",
            "STEP import", "STEP-Import"),
        Komponente("Boost", "BSL-1.0", "https://www.boost.org", "C++ foundation libraries", "C++-Grundbibliotheken"),
        Komponente("CGAL", "GPL-3.0 / LGPL-3.0", "https://www.cgal.org",
            "Mesh booleans and geometry", "Boolesche Operationen und Geometrie"),
        Komponente("GMP / MPFR", "LGPL-3.0", "https://gmplib.org",
            "Exact arithmetic for CGAL", "Exakte Arithmetik fuer CGAL"),
        Komponente("Eigen", "MPL-2.0", "https://eigen.tuxfamily.org", "Linear algebra", "Lineare Algebra"),
        Komponente("oneTBB", "Apache-2.0", "https://github.com/uxlfoundation/oneTBB",
            "Parallel slicing", "Paralleles Slicen"),
        Komponente("NLopt", "LGPL-2.1 / MIT", "https://github.com/stevengj/nlopt",
            "Optimisation (arrange, supports)", "Optimierung (Anordnen, Stuetzen)"),
        Komponente("OpenVDB, OpenEXR, c-blosc", "MPL-2.0 / BSD-3-Clause", "https://www.openvdb.org",
            "Volumetric operations", "Volumen-Operationen"),
        Komponente("Qhull", "Qhull License", "http://www.qhull.org", "Convex hulls", "Konvexe Huellen"),
        Komponente("cereal, nlohmann/json, NanoSVG, Expat, zlib, libpng, libjpeg",
            "BSD-3-Clause / MIT / zlib / PNG / IJG", "https://github.com/prusa3d/PrusaSlicer/tree/master/deps",
            "Serialisation, JSON, SVG, XML and image formats", "Serialisierung, JSON, SVG, XML und Bildformate"),
        Komponente("Kotlin Multiplatform, kotlinx", "Apache-2.0", "https://kotlinlang.org",
            "Shared rules module of both apps", "Gemeinsames Regelmodul beider Apps"),
        Komponente("Jetpack Compose, AndroidX, CameraX", "Apache-2.0", "https://developer.android.com/jetpack",
            "User interface, camera for QR pairing", "Oberflaeche, Kamera fuer QR-Kopplung", Plattform.ANDROID),
        Komponente("Coil", "Apache-2.0", "https://github.com/coil-kt/coil", "Image loading", "Bilder laden", Plattform.ANDROID),
        Komponente("ZXing", "Apache-2.0", "https://github.com/zxing/zxing", "QR codes", "QR-Codes", Plattform.ANDROID),
        Komponente("Google ML Kit Barcode Scanning", "Google APIs Terms of Service",
            "https://developers.google.com/ml-kit/terms", "QR scanning", "QR-Scan", Plattform.ANDROID),
    )

    /** Die Komponenten einer Seite, in Anzeigereihenfolge. */
    fun fuer(plattform: Plattform): List<Komponente> =
        komponenten.filter { it.plattform == Plattform.BEIDE || it.plattform == plattform }
}
