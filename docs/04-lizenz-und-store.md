# Lizenz und Store-Auslieferung

Stand: 2026-07-28

Dies ist das groesste **nicht-technische** Risiko des Projekts. Es muss
geklaert sein, bevor iOS-Auslieferungsaufwand entsteht - nicht danach.

> Hinweis: Das hier ist eine technische Zusammenfassung der bekannten
> Sachlage, keine Rechtsberatung. Fuer eine verbindliche Aussage ist ein
> Fachanwalt oder eine schriftliche Zusage von Prusa Research noetig.

## Ausgangslage

- **PrusaSlicer steht unter AGPL-3.0.** Copyright liegt ueberwiegend bei
  Prusa Research, mit Anteilen aus Slic3r (Alessandro Ranellucci) und
  weiteren Beitragenden.
- AGPL fordert: Quelloffenlegung aller abgeleiteten Werke, gleiche Lizenz,
  keine zusaetzlichen Nutzungsbeschraenkungen - und die Offenlegung greift
  zusaetzlich bei Nutzung ueber ein Netzwerk.

## Android

**Weitgehend unproblematisch.**

- Google Play: GPL-Apps sind dort ueblich und akzeptiert.
- F-Droid: der natuerliche Verteilweg fuer eine AGPL-App.
- Direkte APK-Verteilung: jederzeit moeglich.

Pflicht bleibt in allen Faellen: vollstaendiger Quellcode von PSMobile
oeffentlich, unter AGPL-3.0, mit sichtbarem Hinweis in der App.

## iOS - der Konflikt

Apples App-Store-Nutzungsbedingungen beschraenken die Nutzung einer
geladenen App auf Geraete, die der jeweiligen Apple-ID zugeordnet sind.
Das ist nach verbreiteter Auslegung - und ausdruecklich nach Auffassung
der FSF - eine **zusaetzliche Beschraenkung**, die GPLv3/AGPLv3 § 7
untersagt. Bekanntester Fall: die Entfernung von VLC aus dem App Store
nach Beschwerde eines Rechteinhabers.

Entscheidend ist: Der Konflikt entsteht nicht durch Apple allein, sondern
sobald **ein einziger Rechteinhaber** widerspricht. Bei PrusaSlicer gibt es
viele Beitragende.

### Optionen

| Weg | Aufwand | Risiko | Bewertung |
| --- | --- | --- | --- |
| **A - Schriftliche Zustimmung von Prusa Research** | Anfrage, Wartezeit | mittel: deckt nur Prusas eigenen Anteil ab, nicht alle Beitragenden | sauberster Weg, sollte in jedem Fall versucht werden |
| **B - TestFlight** | gering | gering, aber limitiert (max. 10.000 Tester, Builds laufen nach 90 Tagen ab) | guter Weg fuer Beta und Eigennutzung |
| **C - Alternative EU-Marktplaetze / Web Distribution** | mittel | gering, aber nur EU und Apple-Developer-Programm noetig | realistischer Dauerbetrieb in der EU |
| **D - AltStore / Sideloading** | gering | gering, aber Nutzer muessen alle 7 Tage neu signieren (ohne Dev-Account) | Nischenweg |
| **E - iOS App Store trotzdem** | gering | **hoch**: jederzeit Takedown moeglich | nicht empfohlen |

### Empfehlung

Parallel: **A anfragen** (kostet nur eine E-Mail und ist die einzige
Loesung, die den Store dauerhaft oeffnen koennte) und in der Zwischenzeit
**B fuer Beta**, **C als Ziel fuer EU-Nutzer** einplanen.
iOS wird bis dahin gebaut und getestet, aber nicht veroeffentlicht (E-06).

## Was wir in jedem Fall tun muessen

- [ ] PSMobile-Repository oeffentlich, Lizenz AGPL-3.0
- [ ] `LICENSE` und `THIRD-PARTY-NOTICES` vollstaendig pflegen
- [ ] In-App-Bildschirm "Open Source" mit Lizenztexten und Quell-Link
- [ ] Alle Aenderungen an PrusaSlicer als lesbare Patches in `patches/`
- [ ] Klar kommunizieren, dass PSMobile **nicht** von Prusa Research stammt
      (Markenrecht: Name und Logo "PrusaSlicer"/"Prusa" duerfen nicht so
      verwendet werden, dass eine offizielle Herkunft suggeriert wird)

## Namensfrage

"PSMobile" ist als Arbeitstitel in Ordnung. Fuer eine Veroeffentlichung
sollte ein Name ohne "Prusa"-Bezug gewaehlt und im Untertitel sachlich
angegeben werden, worauf die App aufbaut - etwa
"basiert auf PrusaSlicer (AGPL-3.0)".

## Offene Punkte

- [ ] Anfrage an Prusa Research formulieren und abschicken
- [ ] Pruefen, ob Apple-Developer-Programm-Mitgliedschaft vorhanden ist
- [ ] Entscheiden, ob EU-Web-Distribution als Hauptweg fuer iOS gesetzt wird
