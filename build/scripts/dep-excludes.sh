#!/usr/bin/env bash
# Pakete, die fuer den mobilen FDM-Zuschnitt entfallen.
#
# Diese Liste gilt fuer Android und iOS gleichermassen. Sie steht in einer
# eigenen Datei, weil beide Seiten sie brauchen, ihre Umgebungen sich aber
# nicht vertragen: env.sh setzt Android-NDK-Pfade und ruft `nproc` auf, das
# es auf macOS nicht gibt - build-ios.sh kann env.sh also nicht einbinden.
#
# Vorher stand die Liste deshalb wortgleich an zwei Stellen. Beim
# Einschalten von OCCT fuer den STEP-Import musste sie doppelt geaendert
# werden; wer nur eine Datei anfasst, baut fuer Android und iOS still
# verschiedene Abhaengigkeiten.
#
# Begruendung der einzelnen Posten (siehe auch docs/architecture.md):
#   wxWidgets  - keine Desktop-GUI
#   GLEW       - GLES nutzt die nativen Header
#   OpenCSG    - nur Desktop-Rendering
#   Catch2     - Unit-Tests werden auf dem Host gefahren
#   CURL/OpenSSL - libslic3r hat 0 curl-Referenzen; Netzwerk macht die
#                  native Schicht (OkHttp / URLSession). Siehe E-09.
#
# Was bewusst DRIN bleibt:
#   OpenVDB/OpenEXR/Blosc - SLA/Hollowing.cpp ist fest an OpenVDBUtils
#                  gekoppelt, das Herauspatchen waere invasiver als das
#                  Mitbauen. Siehe E-10.
#   OCCT       - ohne die Bibliothek laesst sich keine STEP-Datei lesen,
#                  und ein Nachladen zur Laufzeit gibt es auf iOS nicht
#                  (siehe patches/0003 und die dlopen-Fehlermeldung, die
#                  genau daher kam).
export DEP_EXCLUDES='wxWidgets|GLEW|OpenCSG|Catch2|CURL|OpenSSL'
