# PSMobile - Wurzel-Buildskript
#
# Baut den PrusaSlicer-Kern (libslic3r) plus die C-ABI-Schicht.
# Die Desktop-GUI bleibt aussen vor: SLIC3R_GUI=OFF.
#
# Konfigurieren immer ueber build/scripts/build-core.sh, nicht von Hand -
# die Toolchain und die Dependency-Pfade sind dort gesetzt.

cmake_minimum_required(VERSION 3.22)
project(PSMobile CXX C)

# PSM_DEPS_ROOT kommt vom Bauskript. Auf CMAKE_PREFIX_PATH ist hier
# kein Verlass: PrusaSlicers eigenes CMakeLists raeumt sie beim
# Einbinden ab, danach sucht jedes find_library im Nichts.
if (NOT PSM_DEPS_ROOT)
    if (DEFINED ENV{PSM_DEPS_PREFIX} AND NOT "$ENV{PSM_DEPS_PREFIX}" STREQUAL "")
        # Der Weg, den beide Bauskripte ohnehin gehen - eindeutig und
        # unabhaengig davon, was CMAKE_PREFIX_PATH sonst noch aufsammelt.
        set(PSM_DEPS_ROOT "$ENV{PSM_DEPS_PREFIX}")
    elseif (CMAKE_PREFIX_PATH)
        # CMAKE_PREFIX_PATH ist eine Liste, keine Zeichenkette. Wer sie
        # unbesehen als einen Pfad weiterreicht, baut daraus "a;b/lib":
        # find_library sucht dann in a und in b/lib/lib, also nirgends.
        # Die Meldung lautet trotzdem nur "nicht gefunden".
        list(GET CMAKE_PREFIX_PATH 0 PSM_DEPS_ROOT)
    endif ()
endif ()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(PSM_BUILD_TESTCLI "Testbinary fuer den Machbarkeitsbeweis M2 bauen" ON)
option(PSM_BUILD_TESTS "C-ABI-Vertragstests bauen" ON)
option(PSM_BUILD_SLICE_CLI "Headlose CLI fuer den Remote-Slice-Server bauen" OFF)

# --- PrusaSlicer-Kern -----------------------------------------------------
# Der Zuschnitt: siehe docs/architecture.md (Abhaengigkeiten).
set(SLIC3R_GUI                OFF CACHE BOOL "" FORCE)  # wxWidgets faellt weg
set(SLIC3R_BUILD_TESTS        OFF CACHE BOOL "" FORCE)
set(SLIC3R_BUILD_SANDBOXES    OFF CACHE BOOL "" FORCE)
# STEP-Import genau dann, wenn OCCT im Abhaengigkeitsprefix liegt. Der
# Kern meldet das ueber psm_supports_step(), die Apps richten sich danach.
# Fest auf OFF stand es vom 25.08. bis 13.09.2026 - die Android-App nahm
# STEP-Dateien trotzdem an, und die Play-Version 0.1.7 hatte OCCT noch.
if (EXISTS "${PSM_DEPS_ROOT}/lib/libTKSTEP.a")
    set(PSM_HAS_STEP 1)
    message(STATUS "PSMobile: OCCT gefunden - STEP-Import an")
else ()
    set(PSM_HAS_STEP 0)
    message(STATUS "PSMobile: kein OCCT unter ${PSM_DEPS_ROOT}/lib - STEP-Import aus")
endif ()
set(SLIC3R_ENABLE_FORMAT_STEP ${PSM_HAS_STEP} CACHE BOOL "" FORCE)
set(SLIC3R_STATIC             ON  CACHE BOOL "" FORCE)
set(SLIC3R_PCH                OFF CACHE BOOL "" FORCE)  # PCH + NDK vertragen sich schlecht

set(PSM_PS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/external/PrusaSlicer")
if (NOT EXISTS "${PSM_PS_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "PrusaSlicer-Quellen fehlen unter ${PSM_PS_DIR}.\n"
        "Erst build/scripts/bootstrap.sh laufen lassen.")
endif ()

add_subdirectory("${PSM_PS_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/prusaslicer" EXCLUDE_FROM_ALL)

# --- G-Code-Vorschau ------------------------------------------------------
# libvgcode ist der Renderer des Vorschau-Tabs aus PrusaSlicer. Es haengt an
# keiner GUI, wird von PrusaSlicer aber nur unter SLIC3R_GUI eingebunden -
# deshalb hier selbst hinzufuegen.
#
# Nur fuer ANDROID/IOS: der Viewport-Wrapper, der einzige Nutzer von
# libvgcode in diesem Baum, ist selbst schon an diese beiden gebunden
# (siehe PSM_CORE_SOURCES unten). Der Remote-Slice-Server (natives Linux,
# siehe docs/remote-slicing.md) schneidet nur, zeigt nichts an - ihm
# fehlt dafuer auch kein GLES/EGL, das auf einem Docker-Image ohnehin
# nicht vorhanden ist.
if (ANDROID OR IOS)
    # ENABLE_OPENGL_ES schaltet auf GLADs GLES2-Pfad. Prusa hat das fuer
    # Raspberry Pi und WebAssembly gebaut; wir erben es.
    # SLIC3R_OPENGL_ES entscheidet in libvgcode/CMakeLists.txt, welcher GLAD-
    # Loader uebersetzt wird - gl.c oder gles2.c. PrusaSlicer erzwingt die
    # Option auf OFF, weil sie an SLIC3R_GUI haengt. Als gewoehnliche Variable
    # ueberdeckt der Wert den Cache-Eintrag nur hier. Ohne das uebersetzt
    # libvgcode zwar mit ENABLE_OPENGL_ES, bringt aber den Desktop-Loader mit,
    # und gladLoaderLoadGLES2 fehlt beim Linken.
    set(SLIC3R_OPENGL_ES ON)
    add_subdirectory("${PSM_PS_DIR}/src/libvgcode"
                     "${CMAKE_CURRENT_BINARY_DIR}/libvgcode" EXCLUDE_FROM_ALL)
    unset(SLIC3R_OPENGL_ES)
    if (TARGET libvgcode)
        target_compile_definitions(libvgcode PUBLIC ENABLE_OPENGL_ES)
        if (ANDROID)
            # GLADs GLES2-Loader zieht sonst ein selbst erzeugtes glad/egl.h
            # herein, das in PrusaSlicers Baum nicht mitgeliefert ist. Das NDK
            # bringt EGL selbst mit, also den Systempfad nehmen.
            target_compile_definitions(libvgcode PRIVATE GLAD_GLES2_USE_SYSTEM_EGL=1)
            target_link_libraries(libvgcode PUBLIC EGL GLESv2)
        elseif (IOS)
            # iOS hat kein EGL. Die GLES-Symbole stecken im Framework und sind
            # nach dem Binden bereits im Prozess; glad findet sie per dlsym,
            # siehe patches/0003. Deshalb hier nur das Framework binden - und
            # GLAD_GLES2_USE_SYSTEM_EGL bewusst nicht setzen.
            target_link_libraries(libvgcode PUBLIC "-framework OpenGLES")
        endif ()
        target_include_directories(libvgcode PUBLIC
            "${PSM_PS_DIR}/src/libvgcode/include"
            "${PSM_PS_DIR}/src/libvgcode/glad/include")
    endif ()
endif ()

# --- C-ABI ----------------------------------------------------------------
set(PSM_CORE_SOURCES
    core/src/psmobile_core.cpp
    core/src/psmobile_presets.cpp
    core/src/psmobile_extruders.cpp
    core/src/psmobile_zip.cpp
    core/src/psmobile_toggles.cpp
    core/src/psmobile_toggles_generated.cpp
    # GL-unabhaengig: Vertragstest und Renderer muessen dieselben aus dem
    # gespeicherten Profil erzeugten Farbdaten sehen.
    viewport/src/psm_layer_profile_render.cpp
)

# Der Viewport liegt in derselben Bibliothek wie der Kern: er liest das
# Modell direkt aus der Session, ohne Umweg ueber das ABI (E-03).
#
# Er ist in C++ gegen GLES geschrieben und damit auf beiden Plattformen
# derselbe Bestand - Android und iOS unterscheiden sich nur darin, wie
# der Kontext entsteht (EGL beziehungsweise EAGL). Genau deshalb ist er
# nicht an ANDROID gebunden.
if (ANDROID OR IOS)
    list(APPEND PSM_CORE_SOURCES viewport/src/psm_viewport.cpp
                                 viewport/src/psm_gizmo.cpp
                                 viewport/src/psm_bed_geometry.cpp)
    # Wandelt ein Print-Ergebnis in die Eingabedaten von libvgcode um.
    # Liegt bei PrusaSlicer unter GUI/, ist aber wx- und GUI-frei.
    list(APPEND PSM_CORE_SOURCES
        "${PSM_PS_DIR}/src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.cpp")
endif ()

# Die JNI-Bruecke wird mit einkompiliert statt als eigene .so gebaut.
# So liefert die App genau eine Bibliothek aus und Gradle braucht kein NDK.
if (ANDROID)
    list(APPEND PSM_CORE_SOURCES android/jni/psm_jni.cpp)
endif ()

if (ANDROID AND ANDROID_ABI STREQUAL "x86_64")
    list(APPEND PSM_CORE_SOURCES core/src/psm_android_x86_fortify_compat.cpp)
endif ()

# Auf iOS statisch: die App bindet den Kern direkt ein. Eine eigene
# dynamische Bibliothek muesste mitsigniert und als Framework verpackt
# werden, ohne dass sie etwas dafuer zurueckgibt. Auf Android ist es
# umgekehrt - dort laedt die JVM eine .so.
if (IOS)
    add_library(psmobile_core STATIC ${PSM_CORE_SOURCES})
else ()
    add_library(psmobile_core SHARED ${PSM_CORE_SOURCES})
endif ()

target_include_directories(psmobile_core
    PUBLIC  ${CMAKE_CURRENT_SOURCE_DIR}/core/include
            ${CMAKE_CURRENT_SOURCE_DIR}/viewport/include
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/core/src
)

# slic3r-arrange-wrapper liegt neben libslic3r und wird von ihr nicht
# mitgezogen - Auto-Arrange braucht es aber.
target_link_libraries(psmobile_core PRIVATE libslic3r slic3r-arrange-wrapper)
if (TARGET libvgcode)
    target_link_libraries(psmobile_core PRIVATE libvgcode)
endif ()

# NanoSVG rastert die Bett-Textur. Die Bibliothek liegt bereits im
# Abhaengigkeitsbaum, weil PrusaSlicer sie selbst braucht - wir binden nur
# den Rasterer dazu.
#
# NO_CMAKE_FIND_ROOT_PATH ist noetig: das NDK-Toolchain setzt die
# Suchmodi auf ONLY und wuerde die angegebenen Pfade unter
# CMAKE_FIND_ROOT_PATH neu verwurzeln - dann sucht es im Nirgendwo.
# Derselbe Stolperstein wie beim Bauen der Abhaengigkeiten.
message(STATUS "PSM: Suche NanoSVG in [${PSM_DEPS_ROOT}/lib]")
find_library(PSM_NANOSVG     NAMES nanosvg
             PATHS "${PSM_DEPS_ROOT}/lib" NO_DEFAULT_PATH NO_CMAKE_FIND_ROOT_PATH)
find_library(PSM_NANOSVGRAST NAMES nanosvgrast
             PATHS "${PSM_DEPS_ROOT}/lib" NO_DEFAULT_PATH NO_CMAKE_FIND_ROOT_PATH)
if (PSM_NANOSVG AND PSM_NANOSVGRAST)
    target_link_libraries(psmobile_core PRIVATE "${PSM_NANOSVGRAST}" "${PSM_NANOSVG}")
else ()
    # Der Pfad gehoert in die Meldung. Ohne ihn sucht man an der falschen
    # Stelle: die Bibliothek liegt fast immer da, nur zeigt PSM_DEPS_ROOT
    # woandershin.
    message(FATAL_ERROR
        "NanoSVG nicht gefunden - ohne sie fehlt die Bett-Textur.\n"
        "  gesucht in: ${PSM_DEPS_ROOT}/lib\n"
        "  nanosvg:     ${PSM_NANOSVG}\n"
        "  nanosvgrast: ${PSM_NANOSVGRAST}")
endif ()

# LibVGCodeWrapper.cpp erwartet die Include-Wurzel von PrusaSlicer
target_include_directories(psmobile_core PRIVATE "${PSM_PS_DIR}/src")

# Der Wrapper enthaelt eine Enum-Umwandlung, die wx ueber GUI_Preview.hpp
# hereinzieht. Wir brauchen sie nicht - siehe patches/.
target_compile_definitions(psmobile_core PRIVATE PSM_NO_GUI_TYPES PSM_HAS_STEP=${PSM_HAS_STEP})

# Nur die psm_*-Symbole nach aussen. Haelt die .so klein und verhindert,
# dass Slic3r-Symbole mit denen anderer Bibliotheken kollidieren.
set_target_properties(psmobile_core PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# CXX_VISIBILITY_PRESET wirkt nur auf die eigenen Quellen. libslic3r wird
# als statische Bibliothek eingebunden und wuerde sonst ~8.800 Slic3r-
# Symbole in die dynamische Symboltabelle spuelen: unnoetig gross und eine
# Kollisionsquelle mit anderen .so im selben Prozess.
# --exclude-libs kennt nur die GNU-Linker. Apples ld lehnt es ab, und auf
# iOS ist es ohnehin gegenstandslos: dort ist der Kern eine statische
# Bibliothek, aus der gar keine Symboltabelle nach aussen geht.
if (NOT MSVC AND NOT APPLE)
    target_link_options(psmobile_core PRIVATE "-Wl,--exclude-libs,ALL")
endif ()

if (ANDROID)
    # GLESv2 statt v3: die Shader aus PrusaSlicer sind GLSL ES 1.00.
    target_link_libraries(psmobile_core PRIVATE log GLESv2 EGL)
    target_link_options(psmobile_core PRIVATE
        # 16-KB-Seitengroesse ist ab Android 15 Pflicht
        "-Wl,-z,max-page-size=16384"
        # Nicht erreichbaren Code entfernen - der Kern zieht viel mit,
        # was ein FDM-Slicer auf dem Handy nie anfasst.
        "-Wl,--gc-sections"
    )

    # Gestrippte Fassung fuer die App. Die ungestrippte bleibt fuer
    # Absturzanalysen liegen.
    add_custom_command(TARGET psmobile_core POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_BINARY_DIR}/stripped"
        COMMAND ${CMAKE_STRIP} --strip-unneeded
                $<TARGET_FILE:psmobile_core>
                -o "${CMAKE_BINARY_DIR}/stripped/libpsmobile_core.so"
        COMMENT "Gestrippte libpsmobile_core.so erzeugen"
    )
endif ()

# --- Testbinary fuer M2 ---------------------------------------------------
if (PSM_BUILD_TESTCLI)
    add_executable(psm_testcli core/test/psm_testcli.c)
    target_link_libraries(psm_testcli PRIVATE psmobile_core)
    target_include_directories(psm_testcli PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/core/include)
endif ()

if (PSM_BUILD_SLICE_CLI)
    add_executable(psm_slice_cli core/test/psm_slice_cli.c)
    target_link_libraries(psm_slice_cli PRIVATE psmobile_core)
    target_include_directories(psm_slice_cli PRIVATE \/core/include)
endif ()

if (PSM_BUILD_TESTS)
    add_executable(psm_contract_tests core/test/psm_contract_tests.cpp)
    target_link_libraries(psm_contract_tests PRIVATE psmobile_core)
    target_include_directories(psm_contract_tests PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/core/include)
    target_compile_features(psm_contract_tests PRIVATE cxx_std_17)
    if (IOS)
        # Auf iOS ist der Kern statisch gelinkt. Der Vertragstest sieht
        # deshalb denselben Prusa-Mehrbett-Singleton wie psm_arrange und
        # kann seine unerlaubte Mutation direkt nachweisen.
        target_compile_definitions(psm_contract_tests PRIVATE
            PSM_TEST_MULTIPLE_BEDS_STATE=1)
        target_include_directories(psm_contract_tests PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/core/src
            ${PSM_PS_DIR}/src)
        target_link_libraries(psm_contract_tests PRIVATE libslic3r)
    endif ()

    # Die Griff-Treffererkennung braucht weder GL-Kontext noch Modell.
    # Als eigener Test bleibt ihr Pixelvertrag deshalb auch auf dem Host
    # messbar, obwohl der eigentliche Viewport nur mobil gebaut wird.
    add_executable(psm_gizmo_tests
        viewport/test/psm_gizmo_tests.cpp
        viewport/src/psm_gizmo.cpp
        viewport/src/psm_bed_geometry.cpp)
    target_link_libraries(psm_gizmo_tests PRIVATE libslic3r)
    target_include_directories(psm_gizmo_tests PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/core/include
        ${CMAKE_CURRENT_SOURCE_DIR}/viewport/include
        ${CMAKE_CURRENT_SOURCE_DIR}/viewport/src)
    target_compile_features(psm_gizmo_tests PRIVATE cxx_std_17)
endif ()
