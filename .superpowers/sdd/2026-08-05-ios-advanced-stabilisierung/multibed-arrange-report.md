# Mehrbett-Auswahl und zielgerichtetes Anordnen

Stand: 2026-08-05

## Ergebnis

Bettnamen und Bettsperren sind jetzt echte Sitzungsdaten des C++-Kerns.
Simple und Advanced verwenden dieselbe responsive Bettauswahl und dasselbe
Arrange-Panel. Das Panel wählt ein Zielbett ausdrücklich, stellt den Abstand
zwischen 0 und 50 mm ein und erklärt die fachlichen Ergebnisse `Empty`,
`Locked` und `Full`, statt sie als generischen Fehler zu verschlucken.

## Kernvertrag

- ABI-Version 5 ergänzt `psm_bed_metadata_get/set` mit UTF-8-Name und Sperre.
- Metadaten werden parallel zu den lokalen Bettmodellen angelegt, entfernt,
  beim Projektwechsel zurückgesetzt und in Undo/Redo-Snapshots mitgeführt.
- `psm_arrange_bed_ex` liefert `psm_arrange_info` mit Status sowie Objekt- und
  Instanzzahl.
- Gesperrte und nicht passend belegbare Zielbetten liefern zusätzlich die
  stabilen Fehlercodes `PSM_ERR_LOCKED` und `PSM_ERR_FULL`.
- Die bisherigen Aufrufe `psm_arrange` und `psm_arrange_bed` bleiben als
  kompatible Wrapper erhalten.
- Die Sperrprüfung liegt unmittelbar vor der Arrange-Mutation im Kern. Sie ist
  dadurch unabhängig davon, ob Simple, Advanced oder ein späterer Client
  anordnet.

## Swift- und UI-Projektion

- `PsmCore.Bed` enthält ausschließlich die vom Kern gelesenen Felder Name und
  Sperre; die früheren `UserDefaults`-Spiegel wurden entfernt.
- `SlicerModel` reicht Umbenennen, Sperren und zielgerichtetes Anordnen an den
  Kern weiter und aktualisiert danach seine Projektion.
- Der gemeinsame `BedSelector` zeigt auf regulärer Breite adaptive Karten ohne
  horizontales Verstecken. Auf kompakter Breite bleibt das aktive Bett als
  direkt treffbarer Knopf sichtbar und öffnet eine vollständige Bettliste.
- Die kompakte Auswahl wird als Root-Overlay präsentiert. Das vermeidet den
  beobachteten SwiftUI-Konflikt mit den mehreren vorhandenen Advanced-Sheets
  und hält den Selector oberhalb der kompakten Seitenleiste.
- `ArrangePanel` bietet Zielbett, 0–50-mm-Abstand in 0,5-mm-Schritten und
  konkrete Erfolgs-, Leer-, Sperr- und Vollmeldungen.

## Test-first-Nachweis

Der neue Contract-Test wurde vor den Produktionssymbolen geschrieben. Der
erste Syntaxlauf schlug erwartungsgemäß wegen fehlender
`psm_bed_metadata`-/`psm_arrange_info`-Typen, APIs und Fehlercodes fehl. Nach
der Implementierung war derselbe Lauf grün.

Die kompakten UI-Tests fanden außerdem einen echten Integrationsfehler:
Im Advanced Mode wurde der Selector zunächst von der kompakten Seitenleiste
überdeckt. Erst nach der Root-Overlay-Korrektur liefen dieselben Tests grün.

## Verifikation auf dem Mac

Alle Mac-Läufe erfolgten in der isolierten Quelle
`~/psmobile-multibed-agent`; der vorhandene, fremd geänderte Mac-Checkout
`~/psmobile` blieb unangetastet.

- C++ Header-/Test-Syntax: erfolgreich.
- Voller Simulator-Core-Link gegen PrusaSlicer: erfolgreich.
- `psm_contract_tests` auf dem iPad-Simulator: `PASS`.
  Abgedeckt sind Metadaten-Roundtrip, Session-Isolation, `Locked`, `Full`,
  explizites Zielbett, unverändertes anderes Bett und unveränderte aktive
  Auswahl.
- Generischer iOS-Simulator-App-Build: `** BUILD SUCCEEDED **`.
- `MultiBedArrangeUITests`, iPad: 3 Tests, 0 Fehler, 33,274 s.
- `MultiBedArrangeUITests`, iPhone: 3 Tests, 0 Fehler, 43,211 s.
- Bestehende `ResponsiveLayoutUITests`, iPhone: 4 Tests, 0 Fehler, 37,770 s.

Die beim Core-Bau sichtbaren `sprintf`-Deprecation-Warnungen stammen aus den
eingebundenen PrusaSlicer-Quellen und waren bereits vorhanden.

## Geänderter Scope

- Core-C-ABI, Sessionmodell, Arrange-Implementierung und Contract-Test
- Swift-Core-Projektion und `SlicerModel`
- gemeinsamer `BedSelector`, Bettlisten-Overlay und `ArrangePanel`
- Einbindung in Simple/Advanced sowie deren vorhandene Arrange-Einstiege
- fokussierte Mehrbett-XCUITests
