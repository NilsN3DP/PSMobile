#!/usr/bin/env bash
# Erzeugt die von autoconf erwarteten Werkzeugnamen fuer die NDK-Toolchain.
#
# Hintergrund: GMP, MPFR und OpenSSL werden per configure gebaut und suchen
# Werkzeuge unter <host-triple>-gcc, -ar, -ranlib usw. Moderne NDKs liefern
# nur noch <triple><api>-clang und llvm-*. Diese Bruecke schliesst die Luecke.
#
# Aufruf im Container:  source build/scripts/mk-autotools-wrappers.sh
# Danach liegt $PSM_XBIN vorne im PATH.

set -euo pipefail

: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME fehlt}"
: "${PSM_ANDROID_API:?PSM_ANDROID_API fehlt}"
: "${PSM_ANDROID_ABI:?PSM_ANDROID_ABI fehlt}"

NDK_BIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin"
PSM_XBIN="${PSM_XBIN:-/tmp/psm-xbin}"

case "${PSM_ANDROID_ABI}" in
    arm64-v8a)    TRIPLE=aarch64-linux-android      ; CLANG_TRIPLE=aarch64-linux-android ;;
    armeabi-v7a)  TRIPLE=armv7a-linux-androideabi   ; CLANG_TRIPLE=armv7a-linux-androideabi ;;
    x86_64)       TRIPLE=x86_64-linux-android       ; CLANG_TRIPLE=x86_64-linux-android ;;
    x86)          TRIPLE=i686-linux-android         ; CLANG_TRIPLE=i686-linux-android ;;
    *) echo "Unbekanntes ABI: ${PSM_ANDROID_ABI}" >&2; exit 1 ;;
esac

mkdir -p "${PSM_XBIN}"

# Compiler: <triple>-gcc/-cc/-g++/-c++  ->  <clang-triple><api>-clang(++)
for name in gcc cc clang; do
    cat > "${PSM_XBIN}/${TRIPLE}-${name}" <<EOF
#!/bin/sh
exec "${NDK_BIN}/${CLANG_TRIPLE}${PSM_ANDROID_API}-clang" "\$@"
EOF
    chmod +x "${PSM_XBIN}/${TRIPLE}-${name}"
done

for name in g++ c++ clang++; do
    cat > "${PSM_XBIN}/${TRIPLE}-${name}" <<EOF
#!/bin/sh
exec "${NDK_BIN}/${CLANG_TRIPLE}${PSM_ANDROID_API}-clang++" "\$@"
EOF
    chmod +x "${PSM_XBIN}/${TRIPLE}-${name}"
done

# Binutils-Ersatz aus dem LLVM-Werkzeugkasten
link_llvm() {  # $1 = autoconf-Name, $2 = llvm-Werkzeug
    if [ -x "${NDK_BIN}/${TRIPLE}-$1" ]; then
        ln -sf "${NDK_BIN}/${TRIPLE}-$1" "${PSM_XBIN}/${TRIPLE}-$1"
    elif [ -x "${NDK_BIN}/$2" ]; then
        ln -sf "${NDK_BIN}/$2" "${PSM_XBIN}/${TRIPLE}-$1"
    fi
}
link_llvm ar       llvm-ar
link_llvm ranlib   llvm-ranlib
link_llvm strip    llvm-strip
link_llvm nm       llvm-nm
link_llvm objcopy  llvm-objcopy
link_llvm objdump  llvm-objdump
link_llvm readelf  llvm-readelf
link_llvm as       llvm-as
link_llvm ld       ld.lld
link_llvm strings  llvm-strings

export PATH="${PSM_XBIN}:${PATH}"
export PSM_XBIN

echo "autotools-Wrapper bereit in ${PSM_XBIN} fuer ${TRIPLE} (API ${PSM_ANDROID_API})"
