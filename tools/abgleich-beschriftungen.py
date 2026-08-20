#!/usr/bin/env python3
"""Beschriftungen je Bildschirm auf beiden Seiten vergleichen.

Findet, was auf einer Seite steht und auf der anderen fehlt. Sagt
nichts ueber die Reihenfolge - das muss man sich ansehen -, aber es
zeigt zuverlaessig fehlende und zusaetzliche Bedienelemente.
"""
import io
import re
import sys

PAARE = [
    ("PrintersView.swift", "PrintersScreen.kt"),
    ("SetupView.swift", "SetupScreen.kt"),
    ("MaterialAuswahlView.swift", "MaterialAuswahl.kt"),
    ("SettingsView.swift", "SettingsScreen.kt"),
    ("SliceSheet.swift", "SimpleSliceSheet.kt"),
    ("RemoteSliceView.swift", "RemoteSliceScreen.kt"),
    ("CustomGcodeView.swift", "CustomGcode.kt"),
    ("ColorMixView.swift", "ColorMixScreen.kt"),
    ("DruckerAuswahlView.swift", "DruckerAuswahl.kt"),
    ("WorkflowStartView.swift", "WorkflowStartScreen.kt"),
    ("AppSettingsView.swift", "AppSettingsScreen.kt"),
    ("SimpleObjectBarView.swift", "SimpleObjectBar.kt"),
    ("WerkzeugSchiene.swift", "SlicerScreen.kt"),
]

SWIFT = re.compile(r'st\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)')
KOTLIN = re.compile(
    r'(?:appText|advancedText|SimpleModeState\.text|\bt|\bst)\(\s*'
    r'"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)')


def lies(pfad, muster):
    try:
        text = io.open(pfad, encoding="utf-8").read()
    except OSError:
        return None
    return {m.group(1) for m in muster.finditer(text)}


def finde(basis, name):
    import os
    for wurzel, _, dateien in os.walk(basis):
        if name in dateien:
            return os.path.join(wurzel, name)
    return None


for swift_name, kt_name in PAARE:
    swift_pfad = finde("ios/PSMobile", swift_name)
    kt_pfad = finde("android/app/src/main/java/de/psmobile", kt_name)
    if not swift_pfad or not kt_pfad:
        print("-- %-28s uebersprungen (%s)" % (
            swift_name, "iOS fehlt" if not swift_pfad else "Android fehlt"))
        continue
    ios = lies(swift_pfad, SWIFT) or set()
    android = lies(kt_pfad, KOTLIN) or set()
    nur_ios = sorted(ios - android)
    nur_android = sorted(android - ios)
    if not nur_ios and not nur_android:
        print("== %-28s gleich (%d Beschriftungen)" % (swift_name, len(ios)))
        continue
    print("== %s  <->  %s" % (swift_name, kt_name))
    for s in nur_ios:
        print("   nur iOS      : %s" % s)
    for s in nur_android:
        print("   nur Android  : %s" % s)
