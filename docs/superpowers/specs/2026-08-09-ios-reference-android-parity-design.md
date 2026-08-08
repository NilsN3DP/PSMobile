# Lastenheft: iOS-Referenz und Android-Paritaet fuer PSMobile

**Stand:** 9. August 2026
**Status:** Zur Abnahme
**Arbeitsmodell:** Isolierter Side-Build, kein Merge ohne ausdrueckliche Freigabe
**Zielplattformen:** iOS/iPadOS 16+, Android; Referenzgeraete iPad Pro 2020 und Samsung Tab S5e

## 1. Zweck

PSMobile soll auf iOS und Android denselben wahrnehmbaren Produktstand erreichen. Die iOS-App wird zuerst vervollstaendigt, gestalterisch vereinheitlicht und als verbindliche Referenz abgenommen. Danach wird Android funktional und visuell moeglichst 1:1 an diese Referenz angeglichen.

Die Arbeit erfolgt vollstaendig in einem parallel installierbaren Side-Build. Der vorhandene Hauptstand bleibt bis zur Abnahme unveraendert. Ein Merge oder selektives Cherry-Picking erfolgt nur nach ausdruecklicher Freigabe des Auftraggebers.

## 2. Ausgangslage und gepruefte Evidenz

- Der aktuelle iOS-Arbeitsstand baut auf dem Mac mit Xcode 26.3 erfolgreich fuer den iPad-Simulator.
- Acht iOS-Unit-Tests wurden am 8. August 2026 ohne Fehler ausgefuehrt.
- Ein aktueller Screenshot-Lauf mit vier UI-Tests wurde ohne Testfehler ausgefuehrt und erzeugte Ansichten fuer Start, Simple, Advanced, Einstellungen sowie Hoch- und Querformat.
- Der Quellstand enthaelt 87 Swift-Dateien, 80 deklarierte UI-Tests und acht Unit-Tests.
- iOS besitzt bereits PrusaLink- und OctoPrint-Clients in einer separaten Druckerverwaltung. Diese Verwaltung ist jedoch nicht als zentraler physischer Druckerablauf in Startseite, Simple Mode und Advanced Mode integriert.
- Klipper/Moonraker fehlt auf iOS als Print-Host.
- Die iOS-Einstellungen besitzen Sondereditoren fuer Bettform und Reinigungsmengen. Ramming-Parameter und G-Code-Ersetzungen fehlen als gleichwertige Spezialeditoren.
- Android besitzt bereits Favoritenlogik in den Einstellungen. iOS besitzt noch keine gleichwertige Favoritenverwaltung und der Core keine generische Schnittstelle fuer objektspezifische Einstellungs-Overrides.
- Der Core besitzt bereits eine Kamera-Reset-Funktion. In der sichtbaren Kamerasteuerung fehlt eine eindeutige Aktion zum Zentrieren beziehungsweise Einrahmen.
- Der Mehrbettzustand ist im Simple Mode teilweise ueber das Modelle-Blatt erreichbar, nutzt aber nicht durchgaengig denselben Bett-Slider und dieselbe Anordnungslogik wie Advanced.
- Der aktuelle UI-Test provoziert nach regulaeren Test-Neustarts den Hinweis, die App sei zuvor nicht normal beendet worden. Diese Erkennung ist nicht belastbar genug.
- Offene reale iOS-Geraetethemen sind grosse G-Code-Vorschauen, Speicherdruck, Schichtfilterung, Bettbeschriftung sowie die echte PrusaLink-Abnahme.

## 3. Leitprinzipien

### 3.1 iOS als Referenz

iOS definiert nach seiner Abnahme Navigation, Informationshierarchie, Komponentenstil, Bezeichnungen, Zustaende und Bedienablaeufe. Android uebernimmt diese Referenz moeglichst 1:1.

Abweichungen sind nur zulaessig, wenn das Betriebssystem sie technisch erzwingt, insbesondere bei Dateiauswahl, Berechtigungen, System-Zurueck-Navigation, Benachrichtigungen und sicherer Schluesselablage. Jede Abweichung muss dokumentiert werden und darf den Ablauf nicht verschlechtern.

### 3.2 Eine Sitzung, zwei Modi

Simple und Advanced sind zwei Darstellungen derselben Sitzung. Aktives Projekt, Betten, Auswahl, Profile, Favoriten, physische Drucker, Undo/Redo und Slice-Ergebnisse werden nicht je Modus dupliziert.

### 3.3 Eine gemeinsame Fachlogik

Mehrbett, Favoriten, Objekt-Overrides, Druckerzuordnung und Validierung besitzen jeweils genau eine fachliche Zustandsquelle. Plattformnative Adapter bleiben fuer UI, Netzwerkerkennung, HTTP, Keychain und Keystore zulaessig.

### 3.4 Schutz vor Datenverlust

Import, Profilwechsel, Slicing, Profilupdate und Druckerupload duerfen bei Fehlern weder das bestehende Projekt noch gespeicherte Zugangsdaten stillschweigend beschaedigen.

## 4. Umfang

### 4.1 Enthalten

- Vervollstaendigung und Vereinheitlichung der iOS-App
- zentrale physische Druckereinrichtung fuer PrusaLink, Klipper/Moonraker und OctoPrint
- automatische Netzwerkerkennung und manuelle Einrichtung
- kontextabhaengiger Inspektor mit globalen und objektspezifischen Einstellungen
- favorisierte Einstellungen und generische Objekt-Overrides
- einheitliche Mehrbettbedienung in Simple und Advanced
- vereinheitlichte Arbeitsflaeche fuer Hoch- und Querformat
- Profilupdate-Kanal, fehlende Spezialeditoren und robuste Absturzerkennung auf iOS
- Android-Funktions- und Designparitaet
- Selbsttest, Diagnose, QR-Pairing, lokale und entfernte Slicing-Ablaufe
- automatisierte Regressionen sowie Abnahme auf Simulator, Emulator und realen Geraeten
- PrusaLink-Hardwareabnahme auf beiden Plattformen
- Abschlussbericht, Abweichungsmatrix und Merge-Empfehlung

### 4.2 Nicht enthalten

- SLA-Slicing und SLA-spezifische Werkzeuge
- Desktop-Fensterverwaltung und andere reine Desktop-Befehle
- Prusa Connect mit neuer OAuth-Infrastruktur
- reale Klipper-/Moonraker- oder OctoPrint-Hardwareabnahme, solange keine entsprechende Hardware bereitsteht
- automatischer Merge, automatisches Ersetzen der produktiven App oder ungefragte Migration produktiver App-Daten

## 5. Isolierter Side-Build

### ISO-01 - Getrennter Quellzweig (MUSS)

Die Arbeit erfolgt in einem eigenen Git-Worktree und einem eigenen Branch. Der Hauptarbeitsbaum und seine uncommittierten Aenderungen duerfen nicht veraendert, geloescht oder ueberschrieben werden.

**Abnahme:** `git worktree list`, Branchname und `git status` belegen die Trennung.

### ISO-02 - Parallel installierbare Apps (MUSS)

iOS und Android erhalten fuer den Side-Build eigene Bundle- beziehungsweise Application-IDs und den sichtbaren Namen **PSMobile Preview**.

**Abnahme:** Produktiv- und Preview-App sind auf demselben Geraet gleichzeitig installierbar.

### ISO-03 - Getrennte Daten und Geheimnisse (MUSS)

Preview und produktive App verwenden getrennte Einstellungen, Projektverweise, Keychain-/Keystore-Eintraege und Druckerzugangsdaten.

**Abnahme:** Aenderungen in der Preview-App sind in der produktiven App nicht sichtbar und umgekehrt.

### ISO-04 - Nachvollziehbarer Build (MUSS)

Die App-Einstellungen zeigen Version, Buildnummer, Quellrevision und den eindeutigen Preview-Status.

### ISO-05 - Kontrollierte Integration (MUSS)

Es erfolgt kein Merge und kein Push in einen Hauptzweig ohne ausdrueckliche Freigabe. Nach der Abnahme wird ein Integrationsbericht mit Merge-, Cherry-Pick- und Rueckfalloptionen vorgelegt.

## 6. Einheitliches UI-System

### UI-01 - Gemeinsame Arbeitsflaeche (MUSS)

Simple und Advanced verwenden dasselbe Grundgeruest:

- globale Kopfzeile fuer Start, Projekt, Druckerstatus, Vorschau und Slicen
- kontextbezogene Werkzeugleiste links
- moeglichst grosse 3D-Arbeitsflaeche in der Mitte
- kontextabhaengiger Inspektor rechts beziehungsweise im Hochformat unten
- kompakte Kamerasteuerung am unteren Rand

Eine zusaetzliche Werkzeugleiste quer ueber dem Modell ist nicht Teil des Zielbilds.

### UI-02 - Startseite (MUSS)

Die Startseite bietet klar getrennte Einstiege fuer:

- Simple Mode
- Advanced Mode
- Druckerprofile
- physische Drucker
- App-Einstellungen
- Remote Slicing

Zuletzt verwendete Projekte erscheinen nur, wenn Eintraege vorhanden sind. Die Seite nutzt den verfuegbaren Raum mit einer klaren vertikalen Hierarchie und vermeidet ungenutzte Grossflaechen.

### UI-03 - Einheitliche Gestaltung (MUSS)

Abstaende, Radien, Schriftgroessen, Touchziele, Karten, Dialoge, aktive Zustaende, Warnungen und Fehler verwenden ein dokumentiertes Designsystem. Pro Bereich gibt es hoechstens eine orange Hauptaktion.

### UI-04 - Verstaendliche Begriffe (MUSS)

Die App unterscheidet sichtbar zwischen **Druckerprofilen** und **physischen Druckern**. Der mehrdeutige Begriff „Printer setup“ wird nicht fuer beide Aufgaben verwendet.

### UI-05 - Erklaerbare Symbole (MUSS)

Keine wesentliche Funktion ist ausschliesslich durch ein nicht selbsterklaerendes Symbol erreichbar. Beschriftung, Accessibility-Label oder kontextbezogene Hilfe erklaeren die Aktion.

### UI-06 - Responsive Inspektorposition (MUSS)

Im Querformat liegt der Inspektor rechts. Im Hochformat wandert derselbe Inspektor automatisch als einklappbare Leiste nach unten. Die untere Leiste belegt hoechstens 45 Prozent der nutzbaren Hoehe, damit der Viewer ausreichend Platz behaelt. Das Verhalten ist Standard und kein experimenteller Schalter.

## 7. Kontextabhaengiger Inspektor und Favoriten

### CTX-01 - Projektkontext ohne Auswahl (MUSS)

Wenn kein Objekt ausgewaehlt ist, zeigt der Inspektor:

- Projekt- und Profiluebersicht
- globale favorisierte Einstellungen, zum Beispiel Infill, Supports und Brim
- Bett- und Slice-Status
- physischen Zieldrucker

Aenderungen gelten global fuer das Projekt beziehungsweise alle Objekte, soweit der jeweilige Parameter dies fachlich vorsieht.

### CTX-02 - Objektkontext bei Auswahl (MUSS)

Wenn genau ein Objekt ausgewaehlt ist, zeigt der Inspektor:

- Position, Rotation, Skalierung und Instanzen
- Extruder-, Teil- und Geometriewerkzeuge
- favorisierte, fuer Objekt-Overrides zulaessige Einstellungen
- den Status **global geerbt** oder **am Objekt ueberschrieben**
- die Aktion **Auf globalen Wert zuruecksetzen**

### CTX-03 - Mehrfachauswahl (MUSS)

Bei Mehrfachauswahl zeigt der Inspektor gemeinsame Werte. Unterschiedliche Werte erscheinen als **Gemischt**. Eine Aenderung wird atomar auf alle ausgewaehlten Objekte angewendet und bildet einen gemeinsamen Undo-Schritt.

### FAV-01 - Favoriten markieren (MUSS)

Jede geeignete Einstellung kann in den vollstaendigen Einstellungen mit einem Stern als Favorit markiert oder entfernt werden.

### FAV-02 - Kontextfilter (MUSS)

Im Inspektor erscheinen nur Favoriten, die im aktuellen Kontext fachlich gueltig sind. Veraltete oder durch Profilupdates entfernte Schluessel werden nicht als tote Eintraege angezeigt.

### FAV-03 - Stabile Reihenfolge (MUSS)

Favoriten folgen der fachlichen Reihenfolge der Einstellungsseiten und nicht dem Zeitpunkt des Markierens.

### FAV-04 - Generische Objekt-Overrides (MUSS)

Der gemeinsame Core erhaelt eine generische, typgepruefte Schnittstelle zum Lesen, Schreiben, Loeschen und Auflisten objektspezifischer Einstellungs-Overrides. Die Schnittstelle respektiert vorhandene Metadaten, Abhaengigkeiten, Wertebereiche, Undo/Redo und Projektserialisierung.

## 8. Kamera und Viewer

### CAM-01 - Zentrieren (MUSS)

Die Kamerasteuerung enthaelt eine sichtbare Aktion **Zentrieren**. Bei vorhandener Auswahl wird die Auswahl eingerahmt. Ohne Auswahl wird das aktive Bett beziehungsweise bei sichtbarer Mehrbettansicht die sichtbare Bettgruppe eingerahmt.

### CAM-02 - Konsistente Ansichten (MUSS)

3D, Oben, Vorne, Hinten, Links, Rechts und Zentrieren stehen in Simple und Advanced ueber dieselbe Viewer-Komponente zur Verfuegung.

### CAM-03 - Grosse Vorschauen (MUSS)

Die Referenzvorschau mit etwa 1,37 Millionen Bewegungen muss auf dem iPad Pro 2020 ohne Speicherabbruch geladen und bedienbar bleiben. Falls erforderlich werden Daten gestaffelt, begrenzt oder geometrisch reduziert, ohne falsche Schichtinformationen anzuzeigen.

### CAM-04 - Schichtfilterung (MUSS)

Oberer und unterer Schichtregler filtern exakt den gewaehlten Bereich. Die erste Schicht liegt am unteren Ende der vertikalen Skala. Alle Griffe besitzen mindestens 44 pt beziehungsweise 48 dp Trefferflaeche.

### CAM-05 - Bettkontext (MUSS)

Bettgeometrie, Bettname und aktueller Bettbezug bleiben in Modell- und G-Code-Vorschau korrekt sichtbar.

## 9. Mehrbett in Simple und Advanced

### BED-01 - Gemeinsamer Bett-Slider (MUSS)

Simple und Advanced verwenden dieselbe Bett-Slider-Komponente und denselben zentralen Bettzustand.

### BED-02 - Vollstaendige Bettaktionen (MUSS)

Beide Modi unterstuetzen Auswahl, Hinzufuegen, Entfernen leerer Betten, Umbenennen, Sperren, Reihenfolge, Objektanzahl und Verschieben von Objekten auf ein anderes Bett.

### BED-03 - Gemeinsame Arrange-Logik (MUSS)

Anordnen nutzt in beiden Modi dieselbe Logik und dieselben Optionen:

- aktuelles Bett oder alle Betten
- Rotation erlauben oder verbieten
- einheitlicher Abstand
- respektierte Bettsperren

### BED-04 - Moduswechsel ohne Zustandsverlust (MUSS)

Beim Wechsel Simple -> Advanced -> Simple bleiben aktives Bett, Namen, Reihenfolge, Sperren, Auswahl, Objektzuordnungen und Undo/Redo unveraendert.

### BED-05 - Orientierungswechsel ohne Zustandsverlust (MUSS)

Hoch-/Querformatwechsel veraendern weder Bettzustand noch Auswahl oder geoeffneten fachlichen Inspektorkontext.

## 10. Physische Drucker

### PRN-01 - Zentrale Druckerverwaltung (MUSS)

Alle Einstiege oeffnen denselben Druckerbestand und denselben Hinzufuegen-Assistenten:

- Startseite
- Simple Mode
- Advanced Mode
- Druckerbereich beziehungsweise „Drucker hinzufuegen“
- Senden-Dialog

### PRN-02 - Automatische und manuelle Einrichtung (MUSS)

Der Assistent bietet lokale Netzwerksuche ueber mDNS/DNS-SD mit anschliessender Protokollerkennung sowie eine manuelle Adresseingabe.

### PRN-03 - Unterstuetzte Hosttypen (MUSS)

- PrusaLink
- Klipper ueber Moonraker
- OctoPrint

### PRN-04 - Gefuehrter Ablauf (MUSS)

Der Ablauf umfasst Erkennung beziehungsweise Typwahl, Name, Adresse, Zugangsdaten, Verbindungstest, Zuordnung zu Druckerprofilen, Speichern und optionale Festlegung als Standarddrucker.

### PRN-05 - PrusaLink (MUSS)

PrusaLink unterstuetzt API-Key sowie Benutzername/Passwort mit Digest, Speicherziel, Upload und optionalen Druckstart.

### PRN-06 - Moonraker (MUSS)

Moonraker unterstuetzt Verbindungstest, API-Schluessel beziehungsweise vertrauenswuerdiges lokales Netz, Upload und Druckstart.

### PRN-07 - OctoPrint (MUSS)

OctoPrint unterstuetzt API-Key, Verbindungstest, Upload und Druckstart.

### PRN-08 - Offline speichern (MUSS)

Ein nicht erreichbarer Drucker darf nach einer konkreten Warnung bewusst gespeichert werden. Die Warnung unterscheidet DNS-, Netzwerk-, Authentifizierungs-, Hosttyp- und Protokollfehler.

### PRN-09 - Profilzuordnung (MUSS)

Beim Senden wird das aktive Druckerprofil mit den zugeordneten Profilen des Zielgeraets verglichen. Eine Abweichung nennt beide Profile, warnt deutlich und erlaubt eine bewusste Fortsetzung.

### PRN-10 - Sichere Geheimnisse (MUSS)

Zugangsdaten liegen in iOS Keychain beziehungsweise Android Keystore. Sie erscheinen niemals in UI-Dumps, Protokollen, Screenshots oder Diagnoseberichten.

## 11. Weitere iOS-Pflichtpunkte

### IOS-01 - Fehlende Spezialeditoren (MUSS)

iOS erhaelt gleichwertige Editoroberflaechen fuer Filament-Ramming-Parameter und G-Code-Ersetzungen. Eingaben werden vor dem Schreiben validiert und koennen verlustfrei erneut geoeffnet werden.

### IOS-02 - Sicherer Profil-Update-Kanal (MUSS)

iOS prueft Updates ausschliesslich ueber erlaubte HTTPS-Hosts, verifiziert Manifest, Version, Kompatibilitaet und Paketpruefsumme, stellt das Paket separat bereit und wechselt erst danach atomar. Bei Fehlern bleibt die vorherige Profilbasis aktiv.

### IOS-03 - Robuste Absturzerkennung (MUSS)

Der Absturzdialog erscheint nur bei belastbarer Absturzevidenz. Regulaere Test-Neustarts, normales Beenden und bewusstes Beenden der App loesen keinen Absturzdialog aus.

### IOS-04 - Sprache und Zugaenglichkeit (MUSS)

Alle Nutzertexte sind mindestens auf Deutsch und Englisch vorhanden. VoiceOver, Tastatur, Stift, Touchziele, Fokusreihenfolge, Kontrast und dynamische Textgroessen werden systematisch geprueft.

### IOS-05 - Selbsttest und Diagnose (MUSS)

Der Selbsttest laeuft auf dem iPad Pro 2020 und erzeugt einen exportierbaren Bericht mit Geraet, Betriebssystem, RAM, Modellgroesse, Dreiecken, Slice-Zeit, Vorschauumfang und Fehlern. Personenbezogene Daten und Geheimnisse sind ausgeschlossen.

### IOS-06 - Release-Unterlagen (MUSS vor oeffentlicher Freigabe)

Lizenz, Third-Party-Notices, Quellcodeangebot, Datenschutzinformation und die dokumentierte AGPL-/Store-Entscheidung liegen vor.

## 12. Android-Paritaet

### AND-01 - Referenzgebundene Umsetzung (MUSS)

Android wird erst nach Abnahme der iOS-Referenz angeglichen. Jede sichtbare iOS-Ansicht und jeder Referenzablauf besitzt ein Android-Gegenstueck oder eine dokumentierte, technisch notwendige Abweichung.

### AND-02 - Funktionsluecken (MUSS)

Der Arbeitslauf schliesst mindestens die bereits identifizierten Luecken:

- Selbsttest und Diagnose-Upload
- QR-Pairing
- ZIP-Import
- Profilsuche
- gleichwertige Spezialeditoren
- vollstaendige Arrange-Optionen
- Bett umbenennen
- zentrale physische Druckerverwaltung inklusive Moonraker und OctoPrint
- gemeinsamer Bett-Slider im Simple Mode
- kontextabhaengiger Inspektor und Favoriten-Overrides

### AND-03 - Visuelle Paritaet (MUSS)

Arbeitsflaeche, Hierarchie, Karten, Kapseln, Inspektor, Werkzeugleisten, Dialoge, Spule, Bett-Slider, Slice-Zusammenfassung und Fehlerzustaende entsprechen der iOS-Referenz. Material-Design-Abweichungen sind kein Selbstzweck.

### AND-04 - Android-Systemintegration (MUSS)

Dateiauswahl, Berechtigungen, System-Zurueck, Benachrichtigungen, Foreground-Service und Keystore folgen Android-Anforderungen, ohne den fachlichen Ablauf zu veraendern.

## 13. Fehler- und Sicherheitsverhalten

### ERR-01 - Transaktionale Operationen (MUSS)

Fehlgeschlagene Importe, Profilwechsel, Profilupdates und Projektladevorgaenge lassen den zuvor gueltigen Zustand unveraendert oder stellen ihn vollstaendig wieder her.

### ERR-02 - Verstaendliche Netzwerkfehler (MUSS)

Fehler unterscheiden mindestens: nicht gefunden, nicht erreichbar, Zertifikat beziehungsweise Transport abgelehnt, Anmeldung fehlgeschlagen, falscher Hosttyp, Serverantwort ungueltig, Upload abgebrochen und Speicherziel abgelehnt.

### ERR-03 - Stale-Schutz (MUSS)

Ein Slice- oder Vorschauergebnis, das nicht zur aktuellen Projekt- und Konfigurationsrevision gehoert, darf weder exportiert noch gesendet werden.

### ERR-04 - Diagnosefaehigkeit (MUSS)

Schwerwiegende Fehler bieten einen verstaendlichen naechsten Schritt und optional einen redigierten Diagnoseexport. Geheimnisse werden vor dem Schreiben entfernt.

## 14. Test- und Abnahmevertrag

### 14.1 Automatisierte Ebenen

- gemeinsame Regeltests fuer Favoriten, Objekt-Overrides, Mehrbettzustand und Druckerzuordnung
- Core-Vertragstests fuer globale und objektspezifische Einstellungen
- iOS-Unit- und XCUITests
- Android-JVM-, Compose- und Instrumentation-Tests
- lokale Mock-Server fuer PrusaLink, Moonraker und OctoPrint
- Projekt-Roundtrips mit einem und mehreren Betten
- Modus- und Orientierungswechsel waehrend Import, Bearbeitung, Slicing und Vorschau
- vollstaendige Screenshot-Tour in relevanten Groessenklassen

### 14.2 Gemeinsamer Referenzablauf

1. Druckerprofil und physischen Drucker einrichten.
2. Modell und Mehrbettprojekt importieren.
3. Betten wechseln, umbenennen, sperren und anordnen.
4. Objekte bearbeiten und zwischen Betten uebertragen.
5. Favoriten global und als Objekt-Override verwenden.
6. Simple und Advanced mehrfach wechseln.
7. Lokal und remote slicen.
8. Vorschau filtern und Kamera zentrieren.
9. G-Code exportieren und an PrusaLink senden.
10. Projekt speichern, App neu starten und Projekt wiederherstellen.

### 14.3 Reale Abnahmeziele

- **iPad Pro 2020:** Hochformat, Querformat, grosse Vorschau, Speicher, Stift und PrusaLink
- **Samsung Tab S5e:** Hochformat, Querformat, Performance, Speicher, Android-Lebenszyklus und PrusaLink
- **PrusaLink-Hardware:** Erkennung, API-Key/Digest soweit am vorhandenen Geraet verfuegbar, Upload, Dateiname und optionaler Druckstart
- **Moonraker und OctoPrint:** vollstaendige Mock-Abnahme; reale Hardware bleibt separat gekennzeichnet

### 14.4 Mindestqualitaet

- keine Abstuerze, ANRs oder Datenverluste
- keine Zustandsabweichung beim Modus- oder Orientierungswechsel
- mindestens 44 pt auf iOS und 48 dp auf Android fuer Touchziele
- Referenzvorschau mit etwa 1,37 Millionen Bewegungen ohne Speicherabbruch
- 20 wiederholte Slice-Zyklen ohne Prozessneustart
- keine ungeprueften Geheimnisse in Logs oder Diagnoseberichten
- alle internen Tests gruen; Hardwareaussagen nur mit realem Beleg
- jede unbegruendete Screenshot-Abweichung blockiert die Paritaetsabnahme

## 15. Abnahme-Gates

### Gate 0 - Baseline gesichert

Quellstand, Buildumgebung, Testfixtures, aktuelle Screenshots und bekannte offene Punkte sind reproduzierbar dokumentiert.

### Gate 1 - iOS intern

iOS Preview baut; Unit-, Core- und UI-Tests bestehen; keine offenen internen Pflichtanforderungen.

### Gate 2 - iOS visuell

Die vollstaendige Screenshot-Tour ist freigegeben. Hoch- und Querformat entsprechen dem vereinbarten UI-System.

### Gate 3 - iPad und PrusaLink

Der Referenzablauf besteht auf dem iPad Pro 2020 und der echten PrusaLink-Hardware.

### Gate 4 - Android intern

Android Preview baut; JVM-, Core-, Compose- und Instrumentation-Tests bestehen; die Paritaetsmatrix ist vollstaendig.

### Gate 5 - Samsung Tab S5e

Der Referenzablauf besteht in Hoch- und Querformat ohne ANR, Speicherabbruch oder Zustandsverlust.

### Gate 6 - Plattformvergleich

iOS und Android bestehen denselben Referenzablauf. Unterschiede sind entweder behoben oder technisch begruendet und freigegeben.

### Gate 7 - Integrationsentscheidung

Ein Abschlussbericht nennt bestandene Gates, Restabweichungen, Risiken, Buildartefakte und Integrationsoptionen. Erst danach entscheidet der Auftraggeber ueber Merge oder Cherry-Picking.

## 16. Liefergegenstaende

- iOS Preview-Build und reproduzierbare Buildanleitung
- Android Preview-Build und reproduzierbare Buildanleitung
- versionierte iOS-Referenzscreenshots
- iOS-/Android-Paritaetsmatrix
- automatisierte Testprotokolle
- iPad-Pro-, Samsung-Tab-S5e- und PrusaLink-Abnahmeprotokolle
- Sicherheits- und Datenschutzpruefung der gespeicherten Geheimnisse
- aktualisierte Feature-Matrix und Release-Gates
- Abschlussbericht mit Merge-, Cherry-Pick- und Rueckfalloptionen

## 17. Risiken und Gegenmassnahmen

### R1 - Ausgangsstaende sind uncommitted

**Risiko:** Aktuelle iOS-, Core- und Android-Aenderungen liegen in mehreren Arbeitsbaeumen.
**Gegenmassnahme:** Vor Implementierung wird ein nach Pfaden und Revisionen dokumentierter Side-Build-Baseline-Snapshot erstellt. Es wird nichts aus einem schmutzigen Arbeitsbaum ueberschrieben.

### R2 - Grosse Vorschau auf realem iPad

**Risiko:** Simulatoren besitzen kein realistisches Jetsam-Limit.
**Gegenmassnahme:** Fruehes iPad-Speichergate mit der vorhandenen 1,37-Millionen-Bewegungen-Fixture; danach erst UI-Politur des Vorschaupfads.

### R3 - Netzwerkdienste unterscheiden sich

**Risiko:** mDNS-Ankuendigungen und Authentifizierung variieren nach Firmware.
**Gegenmassnahme:** Erkennung plus HTTP-Fingerprinting, manuelle Einrichtung als vollwertiger Fallback und protokollspezifische Mock-Server.

### R4 - Paritaet fuehrt zu doppelter Logik

**Risiko:** Separate Simple-/Advanced- oder Plattformzustaende erzeugen neue Umschaltfehler.
**Gegenmassnahme:** Gemeinsame Fachzustaende und Komponenten; Moduswechsel ist Bestandteil jedes Regressionlaufs.

### R5 - Umfang gefaehrdet den Hauptstand

**Risiko:** Ein grosser Umbau koennte funktionierende Pfade regressieren.
**Gegenmassnahme:** Side-Build, kleine pruefbare Integrationsbloecke, Gates und kein Merge ohne reale Abnahme.

## 18. Freigaberegel

Dieses Lastenheft beschreibt das Zielbild. Die nachfolgende technische Umsetzungsplanung darf erst beginnen, nachdem der Auftraggeber diese schriftliche Fassung geprueft und freigegeben hat. Die Umsetzung selbst bleibt bis zur abschliessenden Geraeteabnahme im isolierten Side-Build.
