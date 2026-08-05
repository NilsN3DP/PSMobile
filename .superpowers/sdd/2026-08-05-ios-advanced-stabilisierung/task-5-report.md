# Task 5 – Schwebende Einrichtung und App-Einstellungen

## Umsetzung

- Die vorhandene Profil-Dialogoptik wurde als `SchwebenderDialog` herausgezogen; Profilwechsel, Einrichtung und App-Einstellungen verwenden dieselbe gedimmte, begrenzte Huelle.
- `PSMobileApp` zeichnet beim erneuten Oeffnen der Einrichtung sowie bei App-Einstellungen den bestehenden Bildschirm als Hintergrund weiter und legt den Dialog darueber.
- Die Huelle begrenzt Breite und Hoehe gegen `PSScale.windowSize`. Die Inhaltsbereiche bleiben scrollend; der Rueckweg der erneut geoeffneten Einrichtung ist als Zurueck-Schaltflaeche erreichbar.
- `FloatingDialogUITests` prueft den regelmaessigen iPad-Fall sowie den kompakten iPhone-Destination-Fall mit geringer Hoehe, einschliesslich Scrollen bis zur Diagnose beziehungsweise dem erreichbaren Setup-Abschluss und den jeweiligen Rueckweg.
- Der Profilwechsel bleibt bei niedriger Fensterhoehe ebenfalls bedienbar: nur dann wird sein Gesamtkern scrollend, ohne die normale Dialoggeometrie zu aendern.

## Testnachweis

- Red/Green-Lauf der neuen UI-Tests lokal nicht moeglich: Der isolierte Windows-Worktree hat weder `xcodebuild` noch das Swift-Toolchain-Binary. Der Aufruf `xcodebuild -version` endet daher mit „not recognized“.
- `git diff --check` lief nach den Aenderungen ohne Befund.
- Der geforderte iPad-Simulatorlauf ist beim Controller vorgesehen.

## Review

- Statisches Read-only Review: zwei Hinweise (kompakte Testabdeckung/Scrollen sowie Profilwechsel bei niedriger Hoehe) umgesetzt. Es gibt keine offenen Critical-Befunde.

## Scope

Geaendert wurden nur Dialogpraesentation, deren Host-Zustand, der minimale Close-Pfad im `SlicerModel` und die fokussierten UI-Tests. Persistente App-Einstellungen und Setup-Auswahl bleiben unveraendert.
