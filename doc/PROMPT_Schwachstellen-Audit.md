# Wiederverwendbarer Prompt: Schwachstellen-Audit

> **Verwendung:** Alles ab „--- PROMPT ANFANG ---" kopieren, `<PROJEKT>` und
> `<SCOPE>` ersetzen, einfügen. Funktioniert für jedes Java-Projekt im
> Workspace, weil er sich auf die zweistufigen Guidelines stützt.
>
> **Vorher `/clear`** ist sinnvoll: das Audit braucht keinen Verlauf, nur die
> Dokumente — und es liest viel Code.

---

--- PROMPT ANFANG ---

Führe ein **Schwachstellen-Audit** für `<PROJEKT>` durch.
Scope: `<SCOPE>` (z. B. `src/paint/**`, oder „alles").

## Was das ist — und was nicht

**Du sammelst und bewertest. Du reparierst nichts.** Jede Reparatur ist danach
ein eigener, freigabepflichtiger Schritt. Wenn du beim Lesen einen
Einzeiler-Fix siehst: **nicht anfassen**, in die Tabelle schreiben.

Ergebnis ist **eine Tabelle** in `doc/Audit_Schwachstellen_<Datum>.md`, nach
Schwere sortiert, plus ein kurzer Fließtext für die Befunde, die eine
Erklärung brauchen.

## Schritt 0 — Grundlage lesen

1. `../JAVA_GUIDELINES_UNIVERSAL.md` (§0–§15)
2. `doc/GUIDELINES.md` (Projekt-Steckbrief + projektspezifische §)
3. `doc/WEITERMACHEN_PROMPT.txt` — **enthält oft schon belegte Befunde**
   (dort teils mit `[!]` markiert). Die gehören **ohne erneute Suche** in die
   Tabelle; doppelte Arbeit ist verschwendet.
4. `doc/README.md` als Wegweiser, falls vorhanden.

Der Steckbrief sagt dir, welches UI-Toolkit, welche Basisklassen, welche
God-Klasse und welche Format-Verträge es gibt. **Ohne ihn suchst du an den
falschen Stellen.**

## Schritt 1 — Graphify: ja, aber gezielt

**Lohnt sich, wenn `graphify-out/` fehlt oder älter ist als die letzte
Strukturänderung.** Sonst überspringen.

- Code-only-Korpus kostet **0 LLM-Tokens** (reine AST-Extraktion).
- Bei gefilterter Scope **niemals `graphify update .`** — das re-extrahiert
  alles (Scope-Falle). Gezielt: `graphify <datei>.java --update`.

**Wofür der Graph taugt:**
- **Priorisierung.** Ein Fehler in einem Knoten mit 150 Kanten wiegt schwerer
  als derselbe Fehler in einem Blatt. Die God-Node-Liste in
  `GRAPH_REPORT.md` ist deine Reihenfolge.
- **Vollständigkeit.** „Wer ruft das auf?" — für die Frage, ob ein Fehler
  erreichbar ist.
- **Zyklen.** Import-Zyklen stehen im Report.

**Wofür er NICHT taugt — wichtig:**
- Er **findet keine Fehler**. Keine Races, keine Lecks, keine Off-by-ones.
  Das ist Lesearbeit.
- Er beantwortet **nicht** „ist das tot". Externe Typen (`JPanel`, `ArrayList`)
  liegen als viele Knoten vor, je einer pro erwähnender Datei; ein Rollup auf
  Dateiebene erfindet Referenzen. **Erreichbarkeit immer per Textsuche belegen.**
- Die Kantenliste in `graph.json` heißt `links`, nicht `edges`.

## Schritt 2 — Kategorien systematisch durchgehen

Gehe **jede** Kategorie einzeln durch. Notiere ausdrücklich, wenn eine
Kategorie **nicht zutrifft** (z. B. „kein Netz" → keine Netz-Fehlerfälle) —
das unterscheidet „geprüft und sauber" von „vergessen".

**A. Nebenläufigkeit**
- UI-Thread-Verstöße: Oberfläche außerhalb des EDT verändert (Swing/AWT)
- Lange Arbeit **auf** dem UI-Thread → eingefrorene Anwendung
- Geteilter Zustand ohne Synchronisation/`volatile`; Sichtbarkeit zwischen Threads
- `ConcurrentModificationException`: Sammlung ändern während Iteration
- Verklemmungen, verschachtelte Sperren, Selbstaufruf (Reentrance)
- Rückrufe, die in einem anderen Thread landen als erwartet
- Zeitgeber/Timer, die sich überlappen oder nie gestoppt werden

**B. Lebensdauer & Lecks**
- Zuhörer/Listener registriert, nie entfernt (klassisches Swing-Leck)
- Statische Sammlungen, die nur wachsen; Caches **ohne** Verdrängung
- Ungeschlossene Ströme, Dateien, `Graphics2D` ohne `dispose()`
- Fenster/Dialoge, die nicht `dispose()`d werden
- Bilder/Puffer, die pro Aktion neu entstehen und gehalten werden

**C. Kontrollfluss**
- Endlosschleifen; Abbruchbedingung, die nie eintritt (auch: Fließkomma als
  Schleifenzähler)
- Unbegrenzte Rekursion
- Nebenläufige Rekursion durch Rückrufe (`repaint` → `paint` → `repaint`)
- Schleifen über wachsende Sammlungen

**D. Korrektheit**
- Off-by-one, Index- und Bereichsfehler
- `null`-Rückgaben ohne Prüfung; `Optional` ignoriert
- Ganzzahlüberlauf, Division durch null, Modulo mit negativen Zahlen
- Fließkomma-Vergleich mit `==`; Rundungsfehler in Koordinaten
- Falsche Reihenfolge zusammengehöriger Aufrufe (z. B. Undo-Schnappschuss
  **nach** der Änderung)
- Zustandsmaschinen mit unerreichbaren oder klemmenden Zuständen
- Kopie vs. Referenz: geteiltes Objekt, das versehentlich verändert wird
- `equals`/`hashCode` inkonsistent; veränderliche Schlüssel in Maps

**E. Fehlerbehandlung**
- Leerer `catch` — Fehler verschwindet lautlos
- `catch (Exception)` über zu großem Block
- Fehler nur auf die Konsole, Benutzer sieht nichts
- Teilweise ausgeführte Operation ohne Rücknahme
- Ausnahmen in Rückrufen, die den auslösenden Thread töten

**F. Persistenz & Datenverlust**
- Kein atomares Schreiben: Absturz oder voller Datenträger mitten im
  Speichern zerstört die Datei (Muster: in Temp schreiben, dann umbenennen)
- Kein Rückfall, wenn die Datei beschädigt ist
- Formatänderung ohne Rückwärtskompatibilität; fehlender Key ≠ Default
- Überschreiben ohne Rückfrage; kein Schutz gegen Datenverlust beim Beenden
- Zwei Programme schreiben dieselbe Datei (Format-Verträge, Univ. §7)

**G. Kodierung, Sprache, Zeit**
- Datei-Ein-/Ausgabe **ohne** Kodierungsangabe → plattformabhängig
- `toLowerCase()`/`format()` ohne `Locale` (Türkisch-I-Problem)
- Zeitzonen und Sommerzeit; lokale Zeit persistiert
- Zeilenenden, BOM
- Zeichen außerhalb der BMP (Emoji) bei Index-/Längenrechnung

**H. Pfade & Umgebung**
- Hartkodierte Pfade; Annahmen über Ordnernamen des Betriebssystems
- Trennzeichen fest verdrahtet
- Fehlende Prüfung, ob ein Verzeichnis existiert/schreibbar ist
- Groß-/Kleinschreibung von Dateinamen

**I. Einschleusung & Vertrauen**
- Externe Programme mit zusammengesetzter Befehlszeile aufgerufen
  (`Runtime.exec`, PowerShell, Shell) — kann Benutzertext ausbrechen?
- Pfad-Traversal aus Benutzereingaben (`../`)
- Deserialisierung fremder Daten
- Temporäre Dateien mit vorhersagbarem Namen

**J. Ressourcen & Leistung**
- Allokation im Zeichen- oder Schleifenpfad
- Quadratische Algorithmen auf wachsenden Daten
- Wiederholtes Lesen derselben Datei
- Fehlende oder falsch invalidierte Caches
- Unnötige Vollkopien großer Puffer/Bilder

**K. Oberfläche**
- Tastenkürzel doppelt belegt oder in Textfeldern störend
- Nicht rückgängig machbare Aktion ohne Rückfrage
- Fortschritt/Blockierung ohne Rückmeldung
- Zustand, der nach Neustart verloren geht, obwohl er es nicht sollte
- Bedienelement ohne erreichbaren Weg (nur Shortcut, nirgends sichtbar)

**L. Guideline-Verstöße mit Fehlerpotenzial**
Nur solche, aus denen ein **Fehler** folgen kann — reine Stilfragen gehören
nicht ins Audit. Beispiele: Persistenz am `*Reader`/`*Writer` vorbei;
Rendering schreibt ins Modell; neue Fachlogik in der God-Klasse.

**M. Prüfbarkeit**
- Gibt es Tests? Wenn nein: welche Stellen wären ohne Test nicht sicher
  änderbar? (Das ist selbst ein Befund.)

## Schritt 3 — Belegen, nicht vermuten

Jeder Eintrag braucht:
- **`Datei:Zeile`** — ohne Fundstelle kein Eintrag
- ein **konkretes Fehlerszenario**: welche Eingabe/Reihenfolge/Zeitpunkt führt
  zu welchem falschen Ergebnis? „Könnte problematisch sein" ist kein Befund
- eine **Sicherheitsstufe**:
  - `BELEGT` — im Code nachvollzogen, Szenario schlüssig
  - `PLAUSIBEL` — Muster erkannt, Auslöser nicht vollständig geprüft
  - `VERMUTUNG` — auffällig, braucht Nachforschung

**Ehrlichkeit vor Menge.** Zehn belegte Befunde sind mehr wert als vierzig
Verdachtsmomente. Wenn du eine Kategorie nicht ernsthaft prüfen konntest,
schreib das hin, statt sie leer zu lassen.

## Schritt 4 — Die Tabelle

`doc/Audit_Schwachstellen_<Datum>.md`, sortiert nach Schwere:

| ID | Kat. | Datei:Zeile | Befund | Fehlerszenario | Schwere | Sicherheit | Aufwand |
|---|---|---|---|---|---|---|---|
| A01 | Nebenläufigkeit | `Foo.java:214` | … | … | **S1** | BELEGT | M |

**Schwere:**
- **S1** — Datenverlust, Absturz, Beschädigung gespeicherter Dateien,
  Sicherheitslücke
- **S2** — falsches Ergebnis oder verlorene Benutzerarbeit, ohne Absturz
- **S3** — Beeinträchtigung: Hänger, Leck, das erst nach langer Laufzeit wirkt
- **S4** — Wartbarkeit/Robustheit: heute harmlos, morgen die Ursache

**Aufwand:** S (unter 1 h) · M (überschaubar) · L (eigener Task mit PD)

**ID-Schema:** Kategorie-Buchstabe + laufende Nummer (A01, F03 …), damit man
später auf einen Befund verweisen kann.

Danach zwei kurze Abschnitte:
- **„Die fünf, die ich zuerst angehen würde"** — mit Begründung der
  Reihenfolge (nicht stur nach Schwere: ein S2 mit Aufwand S schlägt ein S1
  mit Aufwand L, wenn es täglich weh tut)
- **„Geprüft und sauber"** — welche Kategorien du durchgesehen hast, ohne
  etwas zu finden. Das ist Teil des Ergebnisses.

## Schritt 5 — Abschluss

- `doc/progress_<DateTime>_schwachstellen-audit.txt` nach `Prompt_Handling.txt`
- `doc/WEITERMACHEN_PROMPT.txt` aktualisieren: Verweis auf die Tabelle, die
  Top-5, und der Hinweis, dass Reparaturen **einzeln freigegeben** werden
- Abschluss-Block (PD/progress/WEITERMACHEN aktualisiert? nächster Schritt?)

**Kein Code geändert. Build unberührt.** Falls du doch etwas anfassen musstest
(z. B. um einen Verdacht zu prüfen): rückgängig machen und es sagen.

--- PROMPT ENDE ---

---

## Hinweise zur Wiederverwendung

- **Zuschneiden lohnt sich.** Bei einem kleinen Projekt (< 20 Dateien) die
  Kategorien A–M in einem Durchgang; bei einer großen Codebasis besser zwei
  Läufe: erst A–F (Laufzeitfehler), dann G–M.
- **Der Steckbrief steuert die Auswahl.** Ohne Netz entfallen die
  Netz-Fehlerfälle; ohne Threads schrumpft A; ohne Persistenz entfällt F.
  Das soll im Ergebnis stehen, nicht stillschweigend passieren.
- **Nach dem Audit** ist die Tabelle die Vorlage für einzelne `[ST]`-Schritte
  oder eine `Task_*.txt` (PD), wenn mehrere Befunde zusammenhängen.
- **Nicht mit einer Aufräumaktion vermischen.** Ein Audit, das nebenbei
  repariert, hinterlässt einen Commit, den niemand mehr prüfen kann.
