# Experimental: lokale PrusaLink-Kopplung per QR-Code

Die Funktion ist standardmäßig deaktiviert und nur für lokale physische Drucker gedacht. Cloud-, Demo- und simulierte Drucker werden nicht automatisch umgestellt.

## Ablauf

PS Mobile validiert den QR-Inhalt (Typ, Version, HTTP-Transport, lokales IPv4-Netz, Port, Modell, Token und optionales Ablaufdatum). Danach sendet die App einmalig:

```http
POST http://192.168.4.1/api/pair
Content-Type: application/json

{"pairing_token":"<Token aus dem QR-Code>"}
```

Bei `200` werden `username`, `password`, `model`, `nozzle`, `host` und `port` geprüft. Host und Port müssen dem validierten QR-Ziel entsprechen. `401 Unauthorized` wird als ungültiger, abgelaufener oder widerrufener Token angezeigt. Anschließend verwendet die App die normalen PrusaLink-Endpunkte für Status, Dateien, Upload und Pause.

## Sicherheit

- Die Kopplung bleibt hinter einem separaten Experimental-Schalter und ist pro Drucker markiert.
- Der Token liegt nicht im Drucker-Metadaten-JSON. Android speichert ihn im verschlüsselten SecretStore, iOS im Keychain.
- Benutzername und Passwort aus `/api/pair` werden ebenfalls nur im SecretStore/Keychain gespeichert.
- Token, Passwörter und WLAN-Zugangsdaten werden nicht geloggt und nicht in Fehlermeldungen aufgenommen.
- Es gibt keinen Cloud-Fallback und keine automatische Drucker-Suche.

## Aktueller Side-Build-Stand

Die gemeinsame Validierung, sichere Persistenz, Android-Austausch und iOS-Clientgrenze sind implementiert. Android bietet zusätzlich eine manuelle JSON-Fallback-Eingabe, falls noch kein Kamera-Scanner verfügbar ist. Die native QR-Kamera-Integration und die CFW-seitige Implementierung bleiben separate nächste Schritte; ohne den CFW-Endpunkt kann keine reale Geräte-Kopplung bestätigt werden.
