# -*- coding: utf-8 -*-
"""Der Prüfplan als PDF zum Abhaken.

Erster Anlauf ging über HTML: `cupsfilter` kann kein HTML→PDF, und
Chrome headless bricht auf dem gemieteten Mac ab, weil er ohne Display
keinen CVDisplayLink bekommt. Also direkt gesetzt - das ist hier
ohnehin ehrlicher, weil ein Prüfplan feste Spaltenbreiten braucht und
kein Umbruch nach Browserlaune.

Aufbau je Zeile: Nummer, Schritt, Erwartung, Kästchen, Notizfeld. Die
Notizspalte ist absichtlich breit: "geht nicht" hilft niemandem, "nach
dem zweiten Tippen springt die Ansicht zurück" schon.
"""
import os
from fpdf import FPDF

R = "./"

ORANGE = (232, 121, 43)
GRAU = (110, 110, 110)
HELL = (200, 200, 200)


class Plan(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Helvetica", "", 7.5)
        self.set_text_color(*GRAU)
        self.cell(0, 4, "PSMobile - Prüfplan für echte Hardware", align="R")
        self.ln(6)

    def footer(self):
        self.set_y(-12)
        self.set_font("Helvetica", "", 7.5)
        self.set_text_color(*GRAU)
        self.cell(0, 4, "Seite %d" % self.page_no(), align="C")


pdf = Plan(orientation="P", unit="mm", format="A4")
pdf.set_auto_page_break(auto=True, margin=16)
pdf.set_margins(12, 12, 12)
pdf.add_page()

# --- Kopf ----------------------------------------------------------------
pdf.set_font("Helvetica", "B", 17)
pdf.set_text_color(20, 20, 20)
pdf.cell(0, 9, "PSMobile - Prüfplan für echte Hardware", new_x="LMARGIN", new_y="NEXT")
pdf.set_font("Helvetica", "", 9)
pdf.set_text_color(*GRAU)
pdf.multi_cell(0, 5,
               "Gerät: ______________________   iOS: __________   "
               "App-Stand (.ipa): ______________________\n"
               "Datum: __________   Drucker im Profil: ______________________   "
               "Filament: ______________________")
pdf.ln(2)

pdf.set_font("Helvetica", "B", 9)
pdf.set_text_color(20, 20, 20)
pdf.cell(0, 5, "Testkörper", new_x="LMARGIN", new_y="NEXT")
pdf.set_font("Helvetica", "", 8.5)
pdf.set_text_color(*GRAU)
pdf.multi_cell(0, 4.4,
               "Liegen neben dieser Datei im Ordner testkoerper/. Bitte genau diese "
               "benutzen - sonst sind zwei Durchläufe nicht vergleichbar.\n"
               "wuerfel20.stl  20 mm Würfel - Bezugskörper für alles mit Zahlen.\n"
               "ecke.stl  L-förmig, 40×40×20 - sein Hüllkasten hat eine leere Ecke.\n"
               "turm.stl  15×15×60 - hoch für Schichthöhen, schmal für Stützen.\n"
               "schraege.stl  Keil mit Schrägfläche - liegt falsch herum auf dem Bett.\n\n"
               "OK-Spalte: Haken wenn es tut was danebensteht. Wenn nicht: bitte in die "
               "letzte Spalte, was stattdessen passiert - was getippt, was passiert.")
pdf.ln(3)

# --- Spalten -------------------------------------------------------------
W_NR, W_WAS, W_ERW, W_OK, W_NOTIZ = 10, 56, 52, 10, 58


def abschnitt(titel):
    if pdf.get_y() > 240:
        pdf.add_page()
    pdf.ln(2)
    pdf.set_font("Helvetica", "B", 11)
    pdf.set_text_color(20, 20, 20)
    pdf.cell(0, 6, titel, new_x="LMARGIN", new_y="NEXT")
    pdf.set_draw_color(*ORANGE)
    pdf.set_line_width(0.4)
    y = pdf.get_y()
    pdf.line(12, y, 198, y)
    pdf.ln(1.5)
    pdf.set_font("Helvetica", "B", 7.5)
    pdf.set_text_color(*GRAU)
    for breite, text in ((W_NR, "Nr"), (W_WAS, "Schritt"), (W_ERW, "Erwartet"),
                         (W_OK, "OK"), (W_NOTIZ, "Was passiert stattdessen")):
        pdf.cell(breite, 4.5, text)
    pdf.ln(5)


def zeile(nr, was, erw):
    """Eine Prüfzeile. Höhe richtet sich nach der längeren der beiden Spalten."""
    pdf.set_font("Helvetica", "", 8.5)
    hoehe_was = len(pdf.multi_cell(W_WAS, 4, was, dry_run=True, output="LINES"))
    hoehe_erw = len(pdf.multi_cell(W_ERW, 4, erw, dry_run=True, output="LINES"))
    zeilen = max(hoehe_was, hoehe_erw, 2)
    h = zeilen * 4 + 2.5

    if pdf.get_y() + h > 275:
        pdf.add_page()

    y0 = pdf.get_y()
    x0 = pdf.get_x()

    pdf.set_text_color(*GRAU)
    pdf.set_font("Helvetica", "", 7.5)
    pdf.set_xy(x0, y0 + 1)
    pdf.cell(W_NR, 4, nr)

    pdf.set_text_color(20, 20, 20)
    pdf.set_font("Helvetica", "", 8.5)
    pdf.set_xy(x0 + W_NR, y0 + 1)
    pdf.multi_cell(W_WAS, 4, was)

    pdf.set_text_color(70, 70, 70)
    pdf.set_xy(x0 + W_NR + W_WAS, y0 + 1)
    pdf.multi_cell(W_ERW, 4, erw)

    # Kästchen
    pdf.set_draw_color(90, 90, 90)
    pdf.set_line_width(0.25)
    pdf.rect(x0 + W_NR + W_WAS + W_ERW + 2.5, y0 + 1.5, 4, 4)

    # Notizlinien
    pdf.set_draw_color(*HELL)
    nx = x0 + W_NR + W_WAS + W_ERW + W_OK
    for i in range(zeilen):
        ly = y0 + 4.5 + i * 4
        pdf.line(nx, ly, nx + W_NOTIZ - 2, ly)

    pdf.set_draw_color(225, 225, 225)
    pdf.line(12, y0 + h - 0.5, 198, y0 + h - 0.5)
    pdf.set_xy(x0, y0 + h)


PLAN = [
    ("1 - Start und Einrichtung", [
        ("1.1", "App starten",
         "Startseite mit Einfach, Experte, App-Einstellungen und Ersteinrichtung"),
        ("1.2", "Ersteinrichtung öffnen, Familie aufklappen, Modell und Düse wählen",
         "Jede Zeile trifft beim ersten Antippen, nicht erst beim zweiten"),
        ("1.3", "Ersteinrichtung ein zweites Mal öffnen",
         "Von der Startseite aus erreichbar, die vorherige Wahl steht noch drin"),
    ]),
    ("2 - Simple Mode", [
        ("2.1", "Haus oben links antippen", "Zurück auf die Startseite"),
        ("2.2", "Menü oben rechts antippen",
         "Startseite, App-Einstellungen, Expertenmodus - alle drei tun etwas"),
        ("2.3", "Projekte, Modell öffnen, wuerfel20.stl",
         "Würfel liegt mittig, Modelle-Blatt zeigt 20,0 x 20,0 x 20,0 mm"),
        ("2.4", "Material antippen, zwei Filamente nacheinander wählen",
         "Blatt bleibt offen, die Auswahl wechselt sichtbar mit"),
        ("2.5", "Im Material-Blatt \"prusament\" ins Suchfeld tippen",
         "Nur Prusament-Karten bleiben stehen"),
        ("2.6", "Typ-Knopf PETG antippen, dann noch einmal",
         "Erst nur PETG, dann wieder alle"),
        ("2.7", "Einen Farbpunkt antippen",
         "Nur Filamente dieser Farbe bleiben stehen"),
        ("2.8", "Projekte, Neues Projekt", "Das Bett ist leer"),
        ("2.9", "Würfel laden, sichern, Neues Projekt, dann in \"Zuletzt\" antippen",
         "Der Würfel ist wieder da"),
    ]),
    ("3 - Bedienung am Bett", [
        ("3.1", "Ein Finger über das Bett ziehen",
         "Dreht flüssig und in die Richtung des Fingers, kein Ruckeln"),
        ("3.2", "Zwei Finger spreizen und zusammenziehen",
         "Zoomt, ohne dass die Ansicht springt"),
        ("3.3", "ecke.stl laden, auswählen, in die leere Ecke des L tippen",
         "Die Auswahl wird gelöst - dort ist kein Objekt"),
        ("3.4", "Ein Objekt auswählen", "Ein dünner Kasten um das Teil ist zu sehen"),
        ("3.5", "Nach dem Wegtippen: hat sich der Zoom geändert?",
         "Nein, die Ansicht bleibt wo sie war"),
        ("3.6", "Objekt auswählen und auf dem Objekt ziehen",
         "Die Ansicht dreht sich, das Teil bleibt liegen"),
        ("3.7", "Am roten oder grünen Pfeil ziehen",
         "Das Teil verschiebt sich entlang dieser Achse"),
        ("3.8", "Auf Skalieren stellen, am Eckgriff ziehen, dabei den Inspektor ansehen",
         "Prozent und Millimeter zählen live mit"),
        ("3.9", "Nach dem Skalieren einmal Zurück antippen",
         "Die ganze Skalierung ist weg, nicht ein Prozent davon"),
    ]),
    ("4 - Hinlegen, Bemalen, Schichten", [
        ("4.1", "schraege.stl laden, auswählen, Objektleiste \"Hinlegen\"",
         "Der Keil dreht sich, die Schräge liegt unten und flach auf"),
        ("4.2", "\"Auf Fläche\" antippen (wird orange), dann eine Fläche des Keils antippen",
         "Genau diese Fläche kommt nach unten, das Werkzeug schaltet sich ab"),
        ("4.3", "Experte, turm.stl wählen, Reiter Werkzeuge, Stützen malen, Strich ziehen",
         "Der Strich folgt dem Finger, das Teil verschiebt sich nicht"),
        ("4.4", "Trifft die Farbe dort, wo der Finger ist?", "Ja, ohne Versatz"),
        ("4.5", "Reiter Bearbeiten, Variable Schichthöhe, Stützstelle hinzufügen, Übernehmen",
         "Der Balken zeigt zwei Bänder, Übernehmen ist möglich"),
    ]),
    ("5 - Expertenmodus", [
        ("5.1", "Werkzeugschiene links durchgehen",
         "Ohne Auswahl sind Löschen, Kopieren, Kopie plus und minus, Objekte, Volumen grau"),
        ("5.2", "Zwei Würfel laden, Reiter Objekte, beide ankreuzen, Löschen",
         "Beide verschwinden"),
        ("5.3", "Ein Objekt wählen, Kopieren, dann Einfügen",
         "Eine Kopie liegt daneben, nicht im Original"),
        ("5.4", "Blickrichtungen unten: Oben, Vorn, Links",
         "Jede zeigt wirklich diese Seite, die Bettaufschrift steht richtig herum"),
        ("5.5", "Bettleiste: Plus antippen, zwischen Bett 1 und 2 wechseln",
         "Jedes Bett zeigt seine eigenen Objekte"),
        ("5.6", "Reiter Profile, Drucker antippen",
         "Karten mit Modell und Düse, das Blatt schließt beim Wählen nicht"),
        ("5.7", "Einstellungen öffnen, oben links Simple / Advanced / Expert wechseln",
         "Die Zahl der sichtbaren Einstellungen ändert sich deutlich"),
        ("5.8", "Gerät quer und hochkant drehen",
         "Das Bett bleibt vollständig sichtbar, nichts wird abgeschnitten"),
    ]),
    ("6 - Schneiden und Ausgabe", [
        ("6.1", "Würfel auf dem Bett, Slice now",
         "Läuft durch, danach stehen Druckzeit, Material und Filament in der Seitenleiste"),
        ("6.2", "Druckzeit mit PrusaSlicer am PC vergleichen (gleicher Drucker und Profil)",
         "Weicht um weniger als 5 % ab.   App: ________   PC: ________"),
        ("6.3", "Vorschau antippen",
         "Werkzeugwege erscheinen, ohne dass neu gerechnet wird"),
        ("6.4", "Schichtregler rechts nach unten ziehen",
         "Weniger Schichten werden gezeigt, der Griff folgt dem Finger"),
        ("6.5", "Ein Objekt halb neben das Bett schieben, dann Slice now",
         "Wird abgelehnt mit \"Außerhalb des Druckbereichs\" und dem Namen"),
        ("6.6", "Nach dem Schneiden an einen PrusaLink-Drucker senden",
         "Kommt an und ist auf dem Drucker sichtbar"),
        ("6.7", "Der eigentliche Test: diesen G-Code wirklich drucken",
         "Der Würfel kommt heraus und misst 20 mm in jeder Richtung"),
    ]),
    ("7 - Belastung und Ausdauer", [
        ("7.1", "Ein großes Modell laden, über 500 000 Dreiecke",
         "Lädt, die App stürzt nicht ab.   Modell: __________   Dauer: ______ s"),
        ("7.2", "Damit drehen und zoomen",
         "Bleibt bedienbar, kein Ruckeln über eine Sekunde"),
        ("7.3", "Zehn Würfel aufs Bett, Anordnen",
         "Alle liegen nebeneinander, keiner überlappt"),
        ("7.4", "App in den Hintergrund, fünf Minuten warten, zurückholen",
         "Der Arbeitsstand ist noch da"),
        ("7.5", "App beenden und neu starten",
         "Drucker und Profile stehen noch, das Bett ist wie verlassen"),
    ]),
]

for titel, zeilen_ in PLAN:
    abschnitt(titel)
    for nr, was, erw in zeilen_:
        zeile(nr, was, erw)

# --- Freies Feld ---------------------------------------------------------
if pdf.get_y() > 210:
    pdf.add_page()
pdf.ln(3)
pdf.set_font("Helvetica", "B", 11)
pdf.set_text_color(20, 20, 20)
pdf.cell(0, 6, "8 - Was sonst noch aufgefallen ist", new_x="LMARGIN", new_y="NEXT")
pdf.set_draw_color(*ORANGE)
y = pdf.get_y()
pdf.line(12, y, 198, y)
pdf.ln(2)
pdf.set_font("Helvetica", "", 8.5)
pdf.set_text_color(*GRAU)
pdf.multi_cell(0, 4.4,
               "Alles, wofür oben keine Zeile steht: Darstellung, Formulierungen, "
               "Stellen die sich falsch anfühlen, Vergleiche mit Android oder dem PC.")
pdf.ln(1)
pdf.set_draw_color(*HELL)
y = pdf.get_y()
for i in range(14):
    pdf.line(12, y + i * 6, 198, y + i * 6)

os.makedirs(R, exist_ok=True)
pdf.output(R + "PSMobile-Pruefplan.pdf")
print("geschrieben:", R + "PSMobile-Pruefplan.pdf",
      os.path.getsize(R + "PSMobile-Pruefplan.pdf"), "Bytes")
