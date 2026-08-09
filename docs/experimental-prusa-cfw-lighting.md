# Experimentelle Prusa-CFW-Druckerbeleuchtung

## Hardware-Gate: aktiv blockiert

Diese Implementierung kennt **keinen** LED-Endpunkt. Im Projekt sind nur
`GET /api/v1/status` (Probe) und `PUT /api/v1/files/{storage}/{name}`
(Upload) als PrusaLink-Endpunkte belegt. Es wurde keine autoritative
Prusa-CFW-Dokumentation gefunden, die LED-Pfade, Methoden, Request-Body,
Antwortcodes und Authentifizierung festlegt.

Darum existiert lediglich `LightingEndpointAdapter` mit
`PendingDocumentedLightingEndpointAdapter`. Er führt niemals HTTP aus und
gibt `BlockedPendingDocumentedEndpoint` zurück. Ein späterer Adapter darf
erst ergänzt werden, wenn diese Dokumentation vorliegt und als Quelle hier
referenziert wird. Simulatoren und Mock-Server sind kein Hardware-Nachweis.

## Sicherheitsvertrag

- Opt-in ist für jeden Drucker getrennt gespeichert und standardmäßig `false`.
- Zugangsdaten bleiben unverändert im Android Keystore bzw. iOS Keychain;
  Lighting-Metadaten enthalten keine Geheimnisse.
- Nur `MANUAL_PHYSICAL`, Firmware `6.5.3` (inklusive `-`/`+`-Suffix) und
  tolerant erkannte `Core One Mini`-Modellnamen können als unterstützt gelten.
- Cloud-, Demo-, Simulations- und unbekannte Profile sowie unbekannte Firmware
  oder Modellnamen sind nicht unterstützt.
- Vor einem Adapter-Aufruf sperrt das Gate bei deaktiviertem Opt-in,
  unsupported Capability, offline oder fehlgeschlagener Probe. Die Verbindung
  wird als transienter Status modelliert; es gibt keine Dialog-Wiederholung.
- AUTO, MANUAL, ANIMATION und OFF sowie RGB, Helligkeit und Animation sind
  als gemeinsamer Vertrag verfügbar. AUTO mappt nur bekannte Zustände
  (Idle, Heating, Printing, Paused, Error, Completed); unbekannte Zustände
  lösen keinen Lichtwechsel aus.

## Vor einer Hardware-Freigabe erforderlich

1. Autoritative Prusa-CFW-LED-API mit exakten Endpunkten und Payloads prüfen.
2. Einen dokumentierten Plattformadapter mit begrenztem Timeout, einem
   idempotenten Reconnect und redigierten Fehlern ergänzen.
3. Gegen ein physisches, manuell eingerichtetes Core One Mini mit Firmware
   6.5.3 testen; dabei bleiben Probe und Upload regressionsgetestet.
