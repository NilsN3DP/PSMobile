# PSMobile Android-Toolchain
#
# Duenner Wrapper um die NDK-Toolchain. Zweck:
#   1. ABI/API aus der Umgebung ziehen, damit die Datei ohne weitere
#      -D-Argumente an ExternalProjects durchgereicht werden kann.
#      (deps/AddCMakeProject.cmake reicht nur CMAKE_TOOLCHAIN_FILE weiter,
#      nicht ANDROID_ABI - deshalb muss die Datei selbstgenuegsam sein.)
#   2. TOOLCHAIN_PREFIX setzen. Die autotools-basierten Rezepte
#      (GMP, MPFR, OpenSSL) erwarten diese Variable fuer --host bzw.
#      --cross-compile-prefix.

if (NOT DEFINED ANDROID_ABI OR ANDROID_ABI STREQUAL "")
    if (DEFINED ENV{PSM_ANDROID_ABI})
        set(ANDROID_ABI "$ENV{PSM_ANDROID_ABI}")
    else ()
        set(ANDROID_ABI "arm64-v8a")
    endif ()
endif ()

if (NOT DEFINED ANDROID_PLATFORM OR ANDROID_PLATFORM STREQUAL "")
    if (DEFINED ENV{PSM_ANDROID_API})
        set(ANDROID_PLATFORM "android-$ENV{PSM_ANDROID_API}")
    else ()
        set(ANDROID_PLATFORM "android-26")
    endif ()
endif ()

set(ANDROID_STL "c++_static" CACHE STRING "" FORCE)

# 16-KB-Seitengroesse ist ab Android 15 Pflicht fuer alle mitgelieferten .so
set(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES ON)

if (DEFINED ENV{ANDROID_NDK_HOME})
    set(_psm_ndk "$ENV{ANDROID_NDK_HOME}")
elseif (DEFINED ENV{ANDROID_NDK_ROOT})
    set(_psm_ndk "$ENV{ANDROID_NDK_ROOT}")
else ()
    message(FATAL_ERROR "ANDROID_NDK_HOME ist nicht gesetzt")
endif ()

include("${_psm_ndk}/build/cmake/android.toolchain.cmake")

# --- Auffinden der selbst gebauten Dependencies ---------------------------
# Die NDK-Toolchain setzt CMAKE_FIND_ROOT_PATH auf den Sysroot und die
# Suchmodi auf ONLY. Dadurch findet find_package() nichts, was ausserhalb
# des Sysroots installiert wurde - also genau unseren deps-Prefix nicht.
#
#   * Prefix als zusaetzliche Suchwurzel eintragen, damit find_library()
#     und find_path() dort die ueblichen lib/- und include/-Unterordner sehen.
#   * PACKAGE-Modus auf BOTH, damit die *Config.cmake-Dateien im Prefix
#     ueberhaupt gefunden werden - sie enthalten absolute Pfade und
#     brauchen deshalb keine Re-Rooting-Logik.
if (DEFINED ENV{PSM_DEPS_PREFIX} AND NOT "$ENV{PSM_DEPS_PREFIX}" STREQUAL "")
    list(APPEND CMAKE_FIND_ROOT_PATH "$ENV{PSM_DEPS_PREFIX}")
    list(APPEND CMAKE_PREFIX_PATH    "$ENV{PSM_DEPS_PREFIX}")
    set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
endif ()

# --- Fuer autotools-Rezepte -----------------------------------------------
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(TOOLCHAIN_PREFIX "aarch64-linux-android")
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    set(TOOLCHAIN_PREFIX "armv7a-linux-androideabi")
elseif (ANDROID_ABI STREQUAL "x86_64")
    set(TOOLCHAIN_PREFIX "x86_64-linux-android")
elseif (ANDROID_ABI STREQUAL "x86")
    set(TOOLCHAIN_PREFIX "i686-linux-android")
else ()
    message(FATAL_ERROR "Unbekanntes ANDROID_ABI: ${ANDROID_ABI}")
endif ()
set(TOOLCHAIN_PREFIX "${TOOLCHAIN_PREFIX}" CACHE STRING "autotools host triple" FORCE)
