# PSMobile iOS-Toolchain
#
# Nutzt CMakes eingebaute iOS-Unterstuetzung (ab 3.14) statt einer
# fremden ios.toolchain.cmake - weniger bewegliche Teile, und
# CMAKE_TOOLCHAIN_FILE wird vom deps-Baum ohnehin weitergereicht.
#
# Bauen geht nur auf einem Mac mit Xcode. Siehe ios/README.md.
#
# Aufruf:
#   cmake -S . -B build-ios \
#         -DCMAKE_TOOLCHAIN_FILE=cmake/toolchains/ios.cmake \
#         -DPSM_IOS_PLATFORM=OS64        # oder SIMULATORARM64

set(CMAKE_SYSTEM_NAME iOS)

if (NOT DEFINED PSM_IOS_PLATFORM OR PSM_IOS_PLATFORM STREQUAL "")
    if (DEFINED ENV{PSM_IOS_PLATFORM})
        set(PSM_IOS_PLATFORM "$ENV{PSM_IOS_PLATFORM}")
    else ()
        set(PSM_IOS_PLATFORM "OS64")
    endif ()
endif ()

# iOS 15 als Untergrenze: deckt praktisch alle Geraete ab, die genug
# Arbeitsspeicher fuers Slicen haben, und bringt die SwiftUI-Bausteine mit,
# auf die die Oberflaeche aufbaut.
set(CMAKE_OSX_DEPLOYMENT_TARGET "15.0" CACHE STRING "" FORCE)

if (PSM_IOS_PLATFORM STREQUAL "OS64")
    set(CMAKE_OSX_SYSROOT "iphoneos" CACHE STRING "" FORCE)
    set(CMAKE_OSX_ARCHITECTURES "arm64" CACHE STRING "" FORCE)
    set(TOOLCHAIN_PREFIX "aarch64-apple-darwin")
elseif (PSM_IOS_PLATFORM STREQUAL "SIMULATORARM64")
    set(CMAKE_OSX_SYSROOT "iphonesimulator" CACHE STRING "" FORCE)
    set(CMAKE_OSX_ARCHITECTURES "arm64" CACHE STRING "" FORCE)
    set(TOOLCHAIN_PREFIX "aarch64-apple-darwin")
elseif (PSM_IOS_PLATFORM STREQUAL "SIMULATOR64")
    set(CMAKE_OSX_SYSROOT "iphonesimulator" CACHE STRING "" FORCE)
    set(CMAKE_OSX_ARCHITECTURES "x86_64" CACHE STRING "" FORCE)
    set(TOOLCHAIN_PREFIX "x86_64-apple-darwin")
else ()
    message(FATAL_ERROR "Unbekanntes PSM_IOS_PLATFORM: ${PSM_IOS_PLATFORM}")
endif ()

set(TOOLCHAIN_PREFIX "${TOOLCHAIN_PREFIX}" CACHE STRING "autotools host triple" FORCE)

# Beim Cross-Build leitet CMake CMAKE_SYSTEM_PROCESSOR nicht ab, es bleibt
# leer. Die meisten Projekte stoert das nicht, libjpeg-turbo schon: es
# ruft string(TOLOWER ${CMAKE_SYSTEM_PROCESSOR} ...) unquotiert auf, das
# leere Argument verschwindet, und CMake meldet "string no output variable
# specified" - eine Meldung, die auf alles Moegliche hindeutet, nur nicht
# auf die eigentliche Ursache.
set(CMAKE_SYSTEM_PROCESSOR "${CMAKE_OSX_ARCHITECTURES}")

# Bitcode ist seit Xcode 14 abgeschafft.
set(CMAKE_XCODE_ATTRIBUTE_ENABLE_BITCODE "NO" CACHE STRING "" FORCE)

# Fuer CMAKE_SYSTEM_NAME iOS steht MACOSX_BUNDLE von sich aus auf AN. Fuer
# eine App ist das richtig, fuer die Hilfsprogramme in den Dependencies
# nicht: heatshrink baut ein Kommandozeilenwerkzeug und installiert es mit
# install(TARGETS), und CMake bricht dann mit "given no BUNDLE DESTINATION
# for MACOSX_BUNDLE executable" ab. Diese Programme laufen ohnehin nie auf
# einem Geraet - sie sind reine Bauhilfen. Das eigentliche App-Bundle
# entsteht in Xcode, nicht hier.
set(CMAKE_MACOSX_BUNDLE OFF CACHE BOOL "" FORCE)

# Wie bei Android: die selbst gebauten Dependencies liegen ausserhalb des
# SDK-Sysroots und muessen als zusaetzliche Suchwurzel bekannt sein.
if (DEFINED ENV{PSM_DEPS_PREFIX} AND NOT "$ENV{PSM_DEPS_PREFIX}" STREQUAL "")
    list(APPEND CMAKE_FIND_ROOT_PATH "$ENV{PSM_DEPS_PREFIX}")
    list(APPEND CMAKE_PREFIX_PATH    "$ENV{PSM_DEPS_PREFIX}")
    set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
endif ()

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM BEFORE)
