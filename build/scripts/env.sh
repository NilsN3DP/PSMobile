#!/usr/bin/env bash
# Gemeinsame Konfiguration fuer alle PSMobile-Build-Skripte.
# Wird von den anderen Skripten per `source` eingebunden.

set -euo pipefail

# --- Projektwurzel (funktioniert auch bei Leerzeichen im Pfad) -------------
PSM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PSM_ROOT

# --- PrusaSlicer-Basis ----------------------------------------------------
export PS_REPO="https://github.com/prusa3d/PrusaSlicer.git"
export PS_TAG="version_2.9.6"
export PS_SRC="${PSM_ROOT}/external/PrusaSlicer"

# --- Android-Ziel ---------------------------------------------------------
# arm64-v8a ist das Hauptziel. x86_64 nur fuer den Emulator.
export ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
export ANDROID_API="${ANDROID_API:-26}"        # Android 8.0, deckt 2026 praktisch alles ab
export ANDROID_STL="c++_static"

# --- Ausgabepfade ---------------------------------------------------------
export DEPS_BUILD="${PSM_ROOT}/build-out/deps-${ANDROID_ABI}"
export DEPS_PREFIX="${PSM_ROOT}/build-out/destdir-${ANDROID_ABI}/usr/local"
export CORE_BUILD="${PSM_ROOT}/build-out/core-${ANDROID_ABI}"
export DL_CACHE="${PSM_ROOT}/build-out/dl-cache"

# --- Docker ---------------------------------------------------------------
export PSM_IMAGE="${PSM_IMAGE:-psmobile-ndk:1}"

# --- Pakete, die fuer den mobilen FDM-Zuschnitt entfallen -----------------
# Begruendung siehe docs/entscheidungen.md, E-08.
#   wxWidgets  - keine Desktop-GUI
#   GLEW       - GLES nutzt die nativen Header
#   OCCT       - STEP-Import ist fuer v1 aus
#   OpenCSG    - nur Desktop-Rendering
#   Catch2     - Unit-Tests werden auf dem Host gefahren
#   CURL/OpenSSL - libslic3r hat 0 curl-Referenzen; Netzwerk macht die
#                  native Schicht (OkHttp / URLSession). Siehe E-09.
#
# OpenVDB/OpenEXR/Blosc bleiben DRIN, obwohl v1 kein SLA kann:
# SLA/Hollowing.cpp ist fest an OpenVDBUtils gekoppelt, das Herauspatchen
# waere invasiver als das Mitbauen. Siehe E-10.
export DEP_EXCLUDES='wxWidgets|GLEW|OCCT|OpenCSG|Catch2|CURL|OpenSSL'

# --- Parallelitaet --------------------------------------------------------
export NPROC="${NPROC:-$(nproc)}"

psm_log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
psm_warn() { printf '\n\033[1;33m!!  %s\033[0m\n' "$*"; }
psm_err() { printf '\n\033[1;31mXX  %s\033[0m\n' "$*" >&2; }
