# Android Beta Closure Audit

Stand: 2026-07-31

## Intern geschlossene Beta-Anforderungen

| Anforderung aus der gemeinsamen Liste | Nachweis |
| --- | --- |
| Android zuerst, iOS später | iOS bleibt in der Matrix bewusst nach dem Android-Gerätegate. |
| 3MF: Objekte oder Projekt wählen | `MainActivity`-Importdialog und `android.project_3mf_import`. |
| Projektimport übernimmt Drucker/Profil | `psm_project_load_3mf`-Vertrag prüft angeforderten und aktivierten Drucker. |
| Mehrbett ohne Durchscrollen | getrennte mobile Betten und direkte Bettchips; 3MF-Roundtrip-Vertrag prüft beide Betten. |
| Bett sperren | `BedLockPolicy`, `SlicerService` und `BedLockTest`; Sperren verhindern Mutationen. |
| Advanced: Wizard erneut öffnen, Profilbereiche und Suche | `AdvancedWizardScreen`, `SettingsScreen`, `ProfileSearch` und zugehörige JVM-Tests. |
| Drucker als Modell, Düse erst beim Slice | `EasyModeState.printerModelChoices`, `SimpleModeState.printerLabel` und Slice-Übersicht. |
| Simple Mode nach Referenz | direkte `SceneView`, dunkle Werkzeugleiste, Projekt-/Drucker-/Material-/Settings-Overlays, keine Dashboard-Kacheln. |
| Supports/Haftung im Simple Mode | Supports schreiben jetzt `support_material`, Auto/Build-plate und Stil (`snug`/`organic`) konsistent. |
| Desktop-nahe Modellwerkzeuge | Matrixeinträge für Mehrfachauswahl, Geometrie, Paint, Measure/Emboss und Spezialdialoge mit Core/JNI-Nachweisen. |
| Absturzschutz und Slice-Abbruch | Snapshot/Stale-Schutz, Foreground-Abbruch, 20-Slice- und 500-Touch-Stresstest im Release-Gate dokumentiert. |

## Nicht als erledigt behauptbare externe Abnahmen

Diese Punkte sind keine fehlende Implementierung, benötigen aber Ressourcen,
die in der Arbeitsumgebung nicht vorhanden sind:

1. Zwei reale ARM-Android-Geräte (4–6 GB und mindestens 8 GB RAM) für
   Low-Memory, Rotation während Slice und Screen-off.
2. Ein realer PrusaLink-Drucker je API-Key- und Digest-Authentifizierung
   sowie eine ausdrücklich freigegebene HTTP-Verbindung.
3. Produkt-/Release-Entscheidungen: Lizenztexte, Third-Party-Notices,
   Quellcodeangebot, Datenschutz und Store-/AGPL-Entscheidung.
4. iOS-Portierung, die ausdrücklich erst nach dem Android-Gate beginnt.

ZIP-Import bleibt ein bewusster Ausschluss und ist kein Beta-Fehler.
