#!/usr/bin/env bash
# Einrichtung auf einem frisch gemieteten Mac - von null bis zum
# lauffaehigen Xcode-Projekt.
#
# Gedacht fuer genau den Fall, dass die Maschine nach Stunden abgerechnet
# wird: jeder Schritt meldet sich, jeder Schritt ist wiederholbar, und
# was schon fertig ist, wird uebersprungen. Bricht etwas ab, kann das
# Skript nach dem Beheben einfach erneut laufen.
#
#   ./macos-bootstrap.sh              alles der Reihe nach
#   ./macos-bootstrap.sh tools        nur Werkzeuge pruefen und holen
#   ./macos-bootstrap.sh sources      nur Quellen und Patches
#   ./macos-bootstrap.sh deps         nur Dependencies (der lange Teil)
#   ./macos-bootstrap.sh core         nur libslic3r und psmobile_core
#   ./macos-bootstrap.sh project      Ressourcen stagen, Xcode-Projekt erzeugen
#
# Plattform ueber PSM_IOS_PLATFORM:
#   SIMULATORARM64  Simulator auf Apple Silicon - fuer einen Prototyp
#   OS64            echtes Geraet, braucht ein Signierprofil
#
# Der Simulator ist die guenstigere Wahl: kein Apple-Entwicklerkonto,
# keine Provisionierung, und zum Ansehen der Oberflaeche reicht er.

set -euo pipefail

# Ueber SSH kommt keine Locale mit: LANG ist leer, LC_CTYPE steht auf C.
# CMake packt Boost dann nicht aus - im Archiv stecken Dateinamen mit
# Sonderzeichen, und ohne UTF-8 bricht es mit "Pathname cannot be
# converted from UTF-8 to current locale" ab. Am Bildschirm faellt das
# nie auf, weil Terminal.app eine Locale setzt.
export LANG="${LANG:-en_US.UTF-8}"
export LC_ALL="${LC_ALL:-en_US.UTF-8}"

PSM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${PSM_IOS_PLATFORM:-SIMULATORARM64}"
PS_REPO="https://github.com/prusa3d/PrusaSlicer.git"
PS_TAG="version_2.9.6"
PS_SRC="${PSM_ROOT}/external/PrusaSlicer"

# Werkzeuge, die ohne Adminrechte beschafft werden, landen hier - im
# Projektbaum statt in /usr/local, wo ohne sudo nichts hinkommt.
PSM_TOOLS="${PSM_ROOT}/build-out/mac-tools"

# Was ein frueherer Lauf schon eingerichtet hat, gilt auch jetzt.
[ -f "${PSM_TOOLS}/env.sh" ] && source "${PSM_TOOLS}/env.sh"

# env.sh sammelt, was die spaeteren Schritte brauchen: die ohne
# Adminrechte geholten Werkzeuge und - falls vorhanden - das eigene
# Homebrew. Beide Wege werden unabhaengig voneinander eingerichtet,
# deshalb wird die Datei jedes Mal aus dem tatsaechlichen Zustand neu
# geschrieben. Wer sie nur ueberschreibt, verliert den jeweils anderen
# Weg: erst hat der homebrew-Schritt seine Zeile hineingeschrieben, dann
# hat der tools-Schritt sie geloescht, und autoreconf war weg.
write_env() {
    mkdir -p "${PSM_TOOLS}"
    {
        printf 'export LANG="${LANG:-en_US.UTF-8}"\n'
        printf 'export LC_ALL="${LC_ALL:-en_US.UTF-8}"\n'
        if [ -x "${HOME}/homebrew/bin/brew" ]; then
            printf 'eval "$(%s/bin/brew shellenv)"\n' "${HOME}/homebrew"
        fi
        printf 'export PATH="%s/bin:$PATH"\n' "${PSM_TOOLS}"
    } > "${PSM_TOOLS}/env.sh"
}

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!!  %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mXX  %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(uname -s)" = "Darwin" ] || die "Dieses Skript laeuft nur auf macOS."

step_tools() {
    log "Werkzeuge"

    if ! xcode-select -p >/dev/null 2>&1; then
        die "Xcode-Kommandozeilenwerkzeuge fehlen. 'xcode-select --install' ausfuehren und danach erneut starten."
    fi
    printf '    Xcode:    %s\n' "$(xcodebuild -version 2>/dev/null | head -1 || echo unbekannt)"

    # Ohne die iOS-Plattform kennt xcodebuild kein iphonesimulator-SDK.
    if ! xcodebuild -showsdks 2>/dev/null | grep -q iphone; then
        die "Kein iOS-SDK gefunden. In Xcode unter Settings > Components die iOS-Plattform laden."
    fi

    # Auf einem gemieteten Managed-Server gibt es kein sudo. Homebrew
    # kann dort trotzdem vorhanden sein - die Xcode-Vorlagen bringen es
    # meist mit -, aber verlassen darf man sich nicht darauf. Deshalb
    # erst feststellen was da ist, bevor irgendetwas installiert wird:
    # ein fehlendes autoreconf faellt sonst erst nach Stunden auf, mitten
    # im MPFR-Bau, und sieht dort wie ein Fehler im Bauskript aus.
    local can_sudo=nein
    sudo -n true 2>/dev/null && can_sudo=ja
    printf '    sudo:     %s\n' "${can_sudo}"

    local have_brew=nein
    if command -v brew >/dev/null 2>&1; then
        # Vorhanden heisst nicht benutzbar: auf geteilten Servern gehoert
        # das Praefix oft root.
        if [ -w "$(brew --prefix 2>/dev/null)/bin" ] 2>/dev/null; then
            have_brew=ja
        else
            have_brew="vorhanden, aber nicht beschreibbar"
        fi
    fi
    printf '    Homebrew: %s\n' "${have_brew}"

    mkdir -p "${PSM_TOOLS}/bin"
    export PATH="${PSM_TOOLS}/bin:${PATH}"

    local missing=()
    local tool
    for tool in cmake ninja xcodegen automake autoconf libtool pkg-config; do
        command -v "${tool}" >/dev/null 2>&1 || missing+=("${tool}")
    done

    if [ ${#missing[@]} -eq 0 ]; then
        printf '    Alle Werkzeuge vorhanden.\n'
    elif [ "${have_brew}" = "ja" ]; then
        log "Installiere ueber Homebrew: ${missing[*]}"
        brew install "${missing[@]}"
    else
        warn "Kein nutzbares Homebrew. Hole was ohne Adminrechte geht."
        for tool in "${missing[@]}"; do
            case "${tool}" in
                cmake)    fetch_cmake ;;
                ninja)    fetch_ninja ;;
                xcodegen) fetch_xcodegen ;;
                *)        printf '    %s: nicht ohne Homebrew zu beschaffen\n' "${tool}" ;;
            esac
        done
    fi

    # Erst festhalten, was da ist, dann bemaengeln, was fehlt. Andersherum
    # war es eine Falle: der Abbruch kam vor dem Schreiben, also blieb ein
    # frueher eingerichtetes Homebrew unvermerkt, und der naechste Lauf
    # fand die autotools wieder nicht.
    write_env
    source "${PSM_TOOLS}/env.sh"

    # Nach den Notbeschaffungen erneut zaehlen. Was jetzt noch fehlt,
    # laesst sich nicht umgehen - GMP und MPFR bauen per autotools, ohne
    # autoreconf kommen sie nicht durch.
    local still=()
    for tool in cmake ninja xcodegen automake autoconf libtool pkg-config; do
        command -v "${tool}" >/dev/null 2>&1 || still+=("${tool}")
    done

    printf '\n    cmake:    %s\n' "$(cmake --version 2>/dev/null | head -1 || echo FEHLT)"
    printf '    ninja:    %s\n' "$(ninja --version 2>/dev/null || echo FEHLT)"
    printf '    xcodegen: %s\n' "$(xcodegen --version 2>/dev/null || echo FEHLT)"
    printf '    autoreconf: %s\n' "$(command -v autoreconf >/dev/null 2>&1 && echo vorhanden || echo FEHLT)"

    if [ ${#still[@]} -gt 0 ]; then
        cat >&2 <<EOF

$(printf '\033[1;31mXX  Es fehlen: %s\033[0m' "${still[*]}")

    Das sind die autotools. GMP und MPFR bauen damit - MPFRs Bauskript
    ruft autoreconf auf -, und beide sind ueber CGAL unverzichtbar.
    macOS bringt sie nicht mit, Apple hat sie aus Xcode entfernt.

    Ohne Adminrechte hilft ein eigenes Homebrew im Benutzerverzeichnis:

        $0 homebrew

    Das braucht kein sudo, baut die Pakete aber aus dem Quelltext und
    laeuft entsprechend lange. Danach dieses Skript erneut starten.

    Der andere Weg ist ein Server mit Adminrechten - bei MacinCloud
    heisst das "Dedicated".
EOF
        exit 1
    fi

    printf '\n    Werkzeugpfad gemerkt in %s/env.sh\n' "${PSM_TOOLS}"
}

# --- Notbeschaffung ohne Adminrechte -----------------------------------
#
# CMake und Ninja liefern fertige macOS-Binaries aus, die sich einfach
# irgendwohin auspacken lassen. Das deckt die beiden wichtigsten
# Werkzeuge ab, wenn kein Homebrew zur Verfuegung steht.

fetch_cmake() {
    log "CMake ohne Adminrechte holen"
    local url="https://github.com/Kitware/CMake/releases/download/v3.31.6/cmake-3.31.6-macos-universal.tar.gz"
    curl -fsSL "${url}" -o "${PSM_TOOLS}/cmake.tar.gz"
    tar -xzf "${PSM_TOOLS}/cmake.tar.gz" -C "${PSM_TOOLS}"
    local app
    app="$(find "${PSM_TOOLS}" -maxdepth 2 -name 'CMake.app' -type d | head -1)"
    ln -sf "${app}/Contents/bin/cmake"  "${PSM_TOOLS}/bin/cmake"
    ln -sf "${app}/Contents/bin/ctest"  "${PSM_TOOLS}/bin/ctest"
    ln -sf "${app}/Contents/bin/cpack"  "${PSM_TOOLS}/bin/cpack"
}

fetch_ninja() {
    log "Ninja ohne Adminrechte holen"
    curl -fsSL "https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-mac.zip" \
        -o "${PSM_TOOLS}/ninja.zip"
    unzip -oq "${PSM_TOOLS}/ninja.zip" -d "${PSM_TOOLS}/bin"
    chmod +x "${PSM_TOOLS}/bin/ninja"
}

# Homebrew ins Benutzerverzeichnis, wenn es keine Adminrechte gibt.
#
# Offiziell unterstuetzt, aber mit einem Preis: fuer ein Praefix
# ausserhalb von /opt/homebrew gibt es keine fertigen Pakete, alles wird
# aus dem Quelltext gebaut. Fuer die vier autotools ist das vertretbar,
# fuer eine ganze Werkzeugkette waere es das nicht.
step_homebrew() {
    local prefix="${HOME}/homebrew"
    log "Homebrew nach ${prefix}"

    if [ ! -x "${prefix}/bin/brew" ]; then
        git clone --depth 1 https://github.com/Homebrew/brew "${prefix}"
    else
        printf '    Vorhanden.\n'
    fi

    eval "$("${prefix}/bin/brew" shellenv)"
    warn "Baut aus dem Quelltext - das dauert. Nicht abbrechen."
    # texinfo sieht nach Beiwerk aus, ist aber Pflicht: MPFR wird vor dem
    # Bauen durch autoreconf geschickt, und danach will make auch die
    # Dokumentation erzeugen. Ohne makeinfo bricht es mit Error 127 ab -
    # einer Meldung, die nicht verraet, welches Programm fehlt.
    brew install autoconf automake libtool pkg-config texinfo

    # Die spaeteren Schritte laufen in eigenen Shells und finden brew
    # sonst nicht wieder.
    write_env

    log "Fertig"
    cat <<EOF

    In dieser Sitzung aktivieren:

      source ${PSM_TOOLS}/env.sh

    Dauerhaft, damit auch neue Anmeldungen es finden:

      echo 'source ${PSM_TOOLS}/env.sh' >> ~/.zprofile

EOF
}

fetch_xcodegen() {
    log "XcodeGen ohne Adminrechte holen"
    curl -fsSL "https://github.com/yonaskolb/XcodeGen/releases/download/2.42.0/xcodegen.zip" \
        -o "${PSM_TOOLS}/xcodegen.zip"
    unzip -oq "${PSM_TOOLS}/xcodegen.zip" -d "${PSM_TOOLS}/xcodegen"
    local bin
    bin="$(find "${PSM_TOOLS}/xcodegen" -name xcodegen -type f -perm +111 | head -1)"
    ln -sf "${bin}" "${PSM_TOOLS}/bin/xcodegen"
}

step_sources() {
    log "PrusaSlicer-Quellen"

    if [ -d "${PS_SRC}/.git" ]; then
        printf '    Vorhanden: %s\n' "$(git -C "${PS_SRC}" describe --tags 2>/dev/null || echo unbekannt)"
    else
        mkdir -p "$(dirname "${PS_SRC}")"
        git clone --depth 1 --branch "${PS_TAG}" "${PS_REPO}" "${PS_SRC}"
    fi

    log "Patches"
    # Der Patch heisst nach Android, ist fuer Apple aber unschaedlich: die
    # GMP/MPFR-Aenderungen trennen nur C- von C++-Flags, und die
    # PNG-Zeile fuegt lediglich ein ANDROID zur Bedingung hinzu.
    local patch
    for patch in "${PSM_ROOT}"/patches/*.patch; do
        [ -f "${patch}" ] || continue
        if git -C "${PS_SRC}" apply --reverse --check "${patch}" >/dev/null 2>&1; then
            printf '    schon angewendet: %s\n' "$(basename "${patch}")"
        else
            git -C "${PS_SRC}" apply "${patch}"
            printf '    angewendet: %s\n' "$(basename "${patch}")"
        fi
    done
}

step_deps() {
    log "Dependencies fuer ${PLATFORM} - das ist der lange Teil"
    warn "Erfahrungswert von Android: eine bis mehrere Stunden. Nicht abbrechen, wenn es lange still ist."
    PSM_IOS_PLATFORM="${PLATFORM}" "${PSM_ROOT}/build/scripts/build-ios.sh" deps
}

step_core() {
    log "libslic3r und psmobile_core fuer ${PLATFORM}"
    PSM_IOS_PLATFORM="${PLATFORM}" "${PSM_ROOT}/build/scripts/build-ios.sh" core
}

step_project() {
    log "Ressourcen ins Bundle"
    "${PSM_ROOT}/build/scripts/stage-resources.sh" ios

    log "Xcode-Projekt erzeugen"
    ( cd "${PSM_ROOT}/ios" && PSM_IOS_PLATFORM="${PLATFORM}" xcodegen generate )

    log "Fertig"
    cat <<EOF

    Naechster Schritt:

      open "${PSM_ROOT}/ios/PSMobile.xcodeproj"

    Im Simulator starten:

      xcodebuild -project "${PSM_ROOT}/ios/PSMobile.xcodeproj" \\
                 -scheme PSMobile \\
                 -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M4)' \\
                 build

EOF
}

case "${1:-all}" in
    tools)    step_tools ;;
    homebrew) step_homebrew ;;
    sources)  step_sources ;;
    deps)     step_deps ;;
    core)     step_core ;;
    project)  step_project ;;
    all)      step_tools; step_sources; step_deps; step_core; step_project ;;
    *) echo "Nutzung: $0 [tools|homebrew|sources|deps|core|project|all]" >&2; exit 2 ;;
esac
