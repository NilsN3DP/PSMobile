# PSMobile - Wurzel-Buildskript
#
# Baut den PrusaSlicer-Kern (libslic3r) plus die C-ABI-Schicht.
# Die Desktop-GUI bleibt aussen vor: SLIC3R_GUI=OFF.
#
# Konfigurieren immer ueber build/scripts/build-core.sh, nicht von Hand -
# die Toolchain und die Dependency-Pfade sind dort gesetzt.

cmake_minimum_required(VERSION 3.22)
project(PSMobile CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(PSM_BUILD_TESTCLI "Testbinary fuer den Machbarkeitsbeweis M2 bauen" ON)

# --- PrusaSlicer-Kern -----------------------------------------------------
# Der Zuschnitt entspricht docs/entscheidungen.md, E-08.
set(SLIC3R_GUI                OFF CACHE BOOL "" FORCE)  # wxWidgets faellt weg
set(SLIC3R_BUILD_TESTS        OFF CACHE BOOL "" FORCE)
set(SLIC3R_BUILD_SANDBOXES    OFF CACHE BOOL "" FORCE)
set(SLIC3R_ENABLE_FORMAT_STEP OFF CACHE BOOL "" FORCE)  # kein OCCT
set(SLIC3R_STATIC             ON  CACHE BOOL "" FORCE)
set(SLIC3R_PCH                OFF CACHE BOOL "" FORCE)  # PCH + NDK vertragen sich schlecht

set(PSM_PS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/external/PrusaSlicer")
if (NOT EXISTS "${PSM_PS_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "PrusaSlicer-Quellen fehlen unter ${PSM_PS_DIR}.\n"
        "Erst build/scripts/bootstrap.sh laufen lassen.")
endif ()

add_subdirectory("${PSM_PS_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/prusaslicer" EXCLUDE_FROM_ALL)

# --- C-ABI ----------------------------------------------------------------
set(PSM_CORE_SOURCES
    core/src/psmobile_core.cpp
    core/src/psmobile_presets.cpp
)

# Die JNI-Bruecke wird mit einkompiliert statt als eigene .so gebaut.
# So liefert die App genau eine Bibliothek aus und Gradle braucht kein NDK.
if (ANDROID)
    list(APPEND PSM_CORE_SOURCES android/jni/psm_jni.cpp)
endif ()

add_library(psmobile_core SHARED ${PSM_CORE_SOURCES})

target_include_directories(psmobile_core
    PUBLIC  ${CMAKE_CURRENT_SOURCE_DIR}/core/include
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/core/src
)

target_link_libraries(psmobile_core PRIVATE libslic3r)

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
if (NOT MSVC)
    target_link_options(psmobile_core PRIVATE "-Wl,--exclude-libs,ALL")
endif ()

if (ANDROID)
    target_link_libraries(psmobile_core PRIVATE log)
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
