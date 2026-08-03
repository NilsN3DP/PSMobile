#!/usr/bin/env bash
# Legt die PrusaSlicer-Ressourcen dorthin, wo die App sie erwartet.
#
#   stage-resources.sh android   -> android/app/src/main/assets/psresources
#   stage-resources.sh ios       -> ios/Resources/psresources
#   stage-resources.sh both
#
# Mitgenommen wird bewusst nur, was der mobile Zuschnitt braucht:
#   profiles/     Vendor-Profile (Prusa u. a.)
#   shaders/ES/   die GLES-Shader, die Prusa fuer Raspberry Pi gebaut hat
#                 und die wir fuer den Viewport in M4 erben
# Icons, Uebersetzungen und Web-Ressourcen der Desktop-GUI bleiben drau
# ssen - das spart mehrere hundert MB APK-Groesse.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

SRC="${PS_SRC}/resources"
if [ ! -d "${SRC}" ]; then
    psm_err "Ressourcen fehlen unter ${SRC}. Erst bootstrap.sh laufen lassen."
    exit 1
fi

# Welche Vendor-Bundles mitgeliefert werden.
#   PSM_VENDORS=prusa  (Vorgabe) - nur Prusa, rund 6 MB
#   PSM_VENDORS=all               - alle 36 Hersteller, rund 41 MB
# 41 MB nur an Profilen waeren ein Drittel der APK-Groesse fuer Drucker,
# die v1 gar nicht adressiert. Weitere Hersteller kann die App spaeter
# nachladen.
VENDORS="${PSM_VENDORS:-prusa}"

stage_into() {
    local dest="$1"
    psm_log "Ressourcen nach ${dest} (Vendors: ${VENDORS})"
    rm -rf "${dest}"
    mkdir -p "${dest}/profiles" "${dest}/shaders"

    if [ "${VENDORS}" = "all" ]; then
        cp -r "${SRC}/profiles/." "${dest}/profiles/"
    else
        # .ini plus zugehoeriger .idx und Unterordner mit Bettmodellen
        for f in "${SRC}"/profiles/Prusa*.ini "${SRC}"/profiles/Prusa*.idx; do
            [ -e "$f" ] && cp "$f" "${dest}/profiles/"
        done
        # Der abschliessende Schraegstrich muss weg, bevor kopiert wird.
        #
        # GNU cp legt bei "cp -r quelle/ ziel/" den Ordner quelle unter
        # ziel an, BSD cp schuettet seinen Inhalt hinein. Auf Linux
        # entstand also profiles/PrusaResearch/coreone_bed.stl, auf dem
        # Mac lagen dieselben Dateien flach in profiles/. PrusaSlicer
        # sucht das Bettmodell aber unter dem Herstellernamen - auf iOS
        # fand es keines und zeichnete nur das flache Vieleck.
        #
        # Gefunden auf einem echten iPad, nachdem es im Simulator
        # monatelang genauso falsch war und niemandem auffiel.
        for d in "${SRC}"/profiles/Prusa*/; do
            [ -d "$d" ] && cp -r "${d%/}" "${dest}/profiles/"
        done
    fi

    cp -r "${SRC}/shaders/ES" "${dest}/shaders/ES"

    # Nur die Drucker-Vorschaubilder wegwerfen. Bettmodelle (.stl) und
    # Betttexturen (.svg) bleiben - der Viewport braucht beide, sie
    # stehen als bed_model und bed_texture in den Profilen.
    find "${dest}/profiles" -type f -name '*_thumbnail.png' -delete 2>/dev/null || true

    sync 2>/dev/null || true
    local n_ini n_sh
    n_ini=$(find "${dest}/profiles" -name '*.ini' | wc -l)
    n_sh=$(find "${dest}/shaders" -type f | wc -l)
    echo "  ${n_ini} Vendor-Bundles, ${n_sh} Shader, $(du -sh "${dest}" | cut -f1) gesamt"
}

# Die aus PrusaSlicer uebernommene Oberflaechen-Definition - Seiten,
# Werkzeuge, Beschriftungen, Icons. Erzeugt von extract-ui.py, liegt im
# Android-Baum und wird von dort gespiegelt.
#
# Beide Apps lesen dieselbe Datei und werten sie mit derselben Regel aus
# (TabsCatalog, E-13). Zwei Kopien der Datei sind unschoen, aber jede
# Plattform legt Ressourcen nun einmal woanders ab; die Alternative waere
# ein Auspacken zur Laufzeit gewesen.
UI_SRC="${PSM_ROOT}/android/app/src/main/assets/psui"

stage_ui_into() {
    local dest="$1"
    [ -d "${UI_SRC}" ] || { psm_warn "psui fehlt - erst extract-ui.py laufen lassen"; return; }
    psm_log "Oberflaechen-Definition nach ${dest}"
    rm -rf "${dest}"
    mkdir -p "$(dirname "${dest}")"
    cp -r "${UI_SRC}" "${dest}"
    echo "  $(find "${dest}" -type f | wc -l) Dateien, $(du -sh "${dest}" | cut -f1)"
}

case "${1:-both}" in
    android) stage_into "${PSM_ROOT}/android/app/src/main/assets/psresources" ;;
    ios)     stage_into "${PSM_ROOT}/ios/Resources/psresources"
             stage_ui_into "${PSM_ROOT}/ios/Resources/psui" ;;
    both)
        stage_into "${PSM_ROOT}/android/app/src/main/assets/psresources"
        stage_into "${PSM_ROOT}/ios/Resources/psresources"
        stage_ui_into "${PSM_ROOT}/ios/Resources/psui"
        ;;
    *) echo "Nutzung: $0 [android|ios|both]" >&2; exit 2 ;;
esac
