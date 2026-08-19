# Experimentelle Prusa-CFW-Druckerbeleuchtung

## Ziel

Eine opt-in Lichtsteuerung für physische PrusaLink-Drucker mit Prusa Firmware 6.5.3, einschließlich Core One Mini. Die Funktion bleibt standardmäßig deaktiviert, ist pro Drucker getrennt gespeichert und darf bei unbekannter oder nicht unterstützter Capability keine LED-Befehle senden.

## Sicherheits- und Produktregeln

- Unterstützt werden nur explizit eingerichtete physische Drucker; Cloud-, Demo- und Simulationsprofile erhalten keine Lighting-Capability.
- Firmware-/Capability-Prüfung erfolgt vor jeder Aktivierung. Unbekannt bedeutet „Nicht unterstützt“.
- Keine Tokens, API-Keys, Passwörter oder Authorization-Header in Logs, Fehlertexten oder Tests.
- Bestehende PrusaLink-Probe, Upload- und Druckabläufe bleiben unverändert.
- Verbindungsverlust führt zu einem transienten UI-Status, nicht zu wiederkehrenden Fehlerdialogen.

## Umsetzungsschritte

### Task 1: Gemeinsames Lighting-Modell und Capability-Regeln

Erzeuge gemeinsame Regeln für `LightingCapability`, `LightingMode` (AUTO, MANUAL, ANIMATION, OFF), Helligkeit, RGB-Farbe, Animation und Statusabhängigkeit. Prüfe Firmware 6.5.3 sowie Core-One-Mini-Modellnamen tolerant, aber falle bei unbekannten Werten sicher auf unsupported zurück. Schreibe zuerst RED-Tests für physisch/Cloud/Demo/Simulation, Firmwaregrenzen, Statusmapping und Befehls-Sperre.

### Task 2: Dokumentierte PrusaLink-Licht-API kapseln

Erweitere die bestehenden Plattform-Clients ausschließlich über dokumentierte Drucker-/PrusaLink-Endpunkte. Capability-Probe, Statusabfrage, Licht ein/aus, Helligkeit, RGB, Animation, Stop, Auto/Manual/Reset benötigen begrenzte Timeouts, eine Reconnect-Strategie und idempotente Fehlerbehandlung. Keine direkte Hardwaresteuerung.

### Task 3: Sicheren per-Drucker-Zustand speichern

Erweitere Android `PrinterStore`/`SecretStore` und iOS `PrinterStore` um nicht geheime Lighting-Einstellungen pro Drucker. Opt-in bleibt false. Credentials bleiben ausschließlich in Keystore/Keychain. Cloud-/Demo-/Simulationsprofile werden beim Laden und vor jedem Senden blockiert.

### Task 4: Experimental-UI in allen relevanten Druckerpfaden

Baue eine klar als „Experimental“ markierte Lichtkarte in Drucker-Detail/Setup und verlinke sie aus Startseite, Simple, Advanced und „Drucker hinzufügen“, sobald ein physischer Drucker ausgewählt ist. Zeige unterstützt/nicht unterstützt, verbunden/nicht verbunden, Modus, Helligkeit und Farbe. Deaktiviere Bedienelemente bei Offline/unsupported; zeige Verbindungsverlust inline und nicht als dauerhafte Dialogschleife.

### Task 5: Automatik und manuelle Steuerung

Implementiere AUTO-Mapping für Idle, Heating, Printing, Paused, Error und Completed. MANUAL, ANIMATION und OFF müssen explizit auswählbar sein. „Auf Automatik zurücksetzen“ löscht nur den manuellen Override des gewählten Druckers.

### Task 6: Integration, Regression und Dokumentation

Prüfe Android JVM/Instrumented-Tests, iOS Unit/UI-Tests, bestehende PrusaLink-Tests und alle Opt-in-Gates. Ergänze eine kurze technische Dokumentation mit unterstützten Firmware-/Modellantworten, Endpunkten, Fehlerverhalten und Hardware-Gate. Markiere reale PrusaLink-Hardwareprüfung separat; Simulatoren und Mock-Server gelten nicht als Hardware-Abnahme.

