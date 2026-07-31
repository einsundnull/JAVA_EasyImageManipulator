# JAVA_GUIDELINES_UNIVERSAL — verbindliche Standards für ALLE Java-Projekte

> **Status: VERBINDLICH ab 2026-07-30** für jedes Java-Projekt in
> `C:\Users\pc\eclipse-workspace\`. Abgeleitet aus den GameLoop2-Guidelines
> (`GameLoop2/src/main/doc/GUIDELINES.md`) durch Herauslösen aller
> projektunabhängigen Regeln.
>
> **Diese Datei ist projektneutral.** Sie enthält keine Klassennamen eines
> einzelnen Projekts, sondern **Rollen** (Token-Quelle, Fenster-Basisklasse,
> Persistenz-Paar …). Jedes Projekt legt in seiner eigenen
> `doc/GUIDELINES.md` einen **Projekt-Steckbrief** (§14) an, der diese Rollen
> mit konkreten Klassen besetzt, und ergänzt projektspezifische Paragraphen.
>
> **Lesereihenfolge für jede Aufgabe:**
> 1. diese Datei · 2. `<Projekt>/doc/GUIDELINES.md` · 3. `doc/Prompt_Handling.txt`
> · 4. `doc/WEITERMACHEN_PROMPT.txt` · 5. die aktive `Task_*.txt` (PD)

---

## §0 Leitprinzip: unnötige Schritte nicht ausführen

Aus `Prompt_Handling.txt`. **Eine funktionierende Architektur wird NICHT nur
umgebaut, um diesen Regeln zu genügen.**

- Die Regeln gelten verpflichtend für **neuen und berührten** Code.
- Reines Refactoring bestehender, laufender Logik allein zur Regelkonformität
  ist ein „unnötiger Schritt" und wird als solcher markiert und begründet.
- Jede Regel trägt eine Risikoklasse:
  **[A]** großes ROI · **[B]** 100 % sicher · **[C]** größeres Risiko
  (nur mit separater Freigabe + Audit im `doc/`).
- Ein Regelverstoß im Bestand ist eine **dokumentierte Altlast**, kein Auftrag.
  Er wird im `doc/` und in der `WEITERMACHEN_PROMPT.txt` geführt, bis er
  bearbeitet wird.

---

## §1 Graphify-First (KnowledgeMap)  [A/B]

- Projektüberblick und Navigation **zuerst über den Graphen**, nicht über
  reihenweises Datei-Lesen: `graphify-out/GRAPH_REPORT.md` lesen, dann
  `graphify query "..."` / `explain "X"` / `path "A" "B"` gegen
  `graphify-out/graph.json`.
- Der Graph ist **code-only** (AST der `.java`-Dateien). Ausgeschlossen:
  `doc/`, `bin/`, `graphify-out/`, generierte und binäre Dateien.
- **Nach Struktur-/Funktionsänderungen auffrischen** — gezielt
  `graphify <datei>.java --update`. **Kein** Voll-`graphify update .` bei
  gefilterter Scope (Scope-Falle: re-extrahiert alles, auch das
  Ausgeschlossene). Voller Rebuild nur nach großen Umbauten.
- `graph.json` nie komplett in den Chat lesen — nur per CLI/Feld-Query.
  Das Format ist **node-link**: die Kantenliste heißt `links`, **nicht** `edges`.
- **Der Graph beantwortet „wie hängt es zusammen", nicht „ist das tot" [B].**
  Externe Bibliothekstypen (`JPanel`, `ArrayList`, `Callbacks`) liegen als
  **mehrere** Knoten vor — je einer pro Datei, die sie erwähnt, mit genau
  dieser `source_file`. Wer Kanten auf Dateiebene aufrollt, liest daraus
  „Datei A referenziert Datei B", obwohl beide nur dieselbe Oberklasse
  benutzen. **Eine Löschentscheidung wird deshalb nie allein aus dem Graphen
  begründet, sondern mit einer Textsuche über den Quellbaum belegt**
  (gemessen an TransparencyTool 2026-07-30: der Graph meldete für eine tote
  Klasse 28 Referenzen, tatsächlich waren es null).

---

## §2 Basisklassen-Pflicht (One Source of Truth für UI)  [A/B]

Jedes Projekt benennt in seinem Steckbrief (§14) seine Basisklassen-Familie.
Ab Benennung gilt: **kein UI-Element umgeht sie.**

| Rolle | Pflicht für |
|---|---|
| **Fenster-Basisklasse** | **Jedes** Fenster/Dialog. Nie roh `new Frame`/`new JFrame`/`new JDialog`/`new JWindow`. |
| **Zeichenflächen-Basisklasse** | **Jede** eigengezeichnete Fläche; flickerfrei über EINE Render-Methode. Die Roh-Zeichenmethode der Plattform (`paint()`/`paintComponent()`/`update()`) wird nicht in Fachklassen überschrieben. |
| **Panel-Basisklasse(n)** | Jede wiederkehrende zusammengesetzte UI-Einheit (Sidebar, Liste, Overlay, Toolbar). |
| **Bestätigungs-Dialog** | **Alle** Ja/Nein-/Warn-/Fehler-Abfragen. Nie eigene Ja/Nein-Gerüste, nie plattform-Standarddialoge (`JOptionPane` & Co.) in Fachcode. |
| **Grafik-Utility (statisch)** | Grafik-Helfer (Antialiasing, Distanz, Skalieren, Alpha-Ableitung) für Klassen **außerhalb** der Fenster-Hierarchie. |

- **Neue wiederkehrende UI-Typen** bekommen eine eigene `Base<Typ>`-Klasse,
  sobald sie **≥2×** auftreten. Reihenfolge Pflicht: **erst Redundanz-Audit
  im `doc/` (`Audit Redundanz <Thema> <Datum>.txt`), dann Extraktion.**
- Eine **Factory-Methode ist keine Basisklasse.** Wenn eine Factory ein
  Fenster/Widget nur konfiguriert zurückgibt, ist damit das Aussehen geteilt,
  aber nicht das Verhalten (Tastatur, Resize, Schließen, ESC/ENTER). Sobald
  das Verhalten zum zweiten Mal kopiert wird, ist die Basisklasse fällig.

---

## §3 Ein StyleGuide, eine Token-Quelle  [A / Erstbereinigung C]

- **Alle** Farben, Fonts, Abstände, Radien, Stroke-Breiten stammen aus
  **einer** projektweit sichtbaren Quelle (`Theme` / `Palette` / `AppColors`,
  `public static final`).
- **Kein** verstreutes `new Color(...)` / `new Font(...)` /
  `new BasicStroke(...)` in Dialogen, Panels, Renderern oder im Hauptfenster.
- Die Token-Quelle ist **nicht `protected` in einer Basisklasse.** Sonst
  können Klassen außerhalb der Hierarchie sie nicht nutzen und erfinden eigene
  Werte — der häufigste Weg, wie eine Palette zerfällt.
- Alpha-/Hell-/Dunkel-Varianten werden aus der Token-Farbe **abgeleitet**
  (`alpha(Color,int)`, `.darker()`), nie als zweites RGB-Tripel getippt.
- Konvention der Namen: `BG_*` (Flächen), `FG_*`/`TEXT_*` (Text),
  `FONT_*` (feste, abgezählte Font-Menge), fachliche Präfixe für
  Bedeutungsfarben. Eine Bedeutung = ein Token.
- **Erstbereinigung ist [C]:** inkrementell pro Datei mit Sichtprüfung, nie
  in einem Rutsch. Zwei Token mit gleichem RGB sind **nicht** automatisch
  dasselbe Token — vor dem Zusammenlegen prüfen, ob die Bedeutung dieselbe
  ist, und die Entscheidung als Kommentar festhalten.

---

## §4 Schichten-Trennung (Abhängigkeitsregel zuerst, Package-Split später)  [A / Split C]

Konzeptionelle Schichten — **als Aufruf-/Abhängigkeitsregel sofort
verbindlich**, unabhängig davon, ob die Packages physisch existieren:

```
core     Orchestrierung, Lebenszyklus, Verdrahtung, Hauptfenster
model    Domäne, reine Daten/Logik
io       Persistenz + Laden (Reader/Writer/Loader)
render   Zeichnen
ui       Fenster/Dialoge/Panels/Toolbars
theme    Tokens (§3)
```

**Regeln (gelten sofort, ohne Umzug):**
- `model` kennt kein Rendering und keine IO-Pfade.
- `io` kennt keine UI; Persistenz **ausschließlich** über die `*Reader`/
  `*Writer` — kein ad-hoc Datei-Schreiben/-Parsen irgendwo sonst (§6).
- `render` **liest** aus `model` und **schreibt nicht zurück.** Teure oder
  zustandsändernde Berechnungen gehören in den Update-/Controller-Pfad.
- **[C] Physischer Package-Split** erst nach separater Freigabe — er erzwingt
  viele Sichtbarkeits-/Import-Änderungen, wenn heute alles in einem Package
  liegt. Die „wer darf wen aufrufen"-Regel gilt schon vorher.

---

## §5 God-Klassen: nichts Neues hineinlegen  [A-Regel / Extraktion C]

Jedes Projekt benennt im Steckbrief (§14) seine größte Klasse.

- **Verbindliche Regel [A/B]:** Neue Fachlogik landet **nicht** weiter in der
  God-Klasse. Zielbild: dünner Orchestrator (Verdrahtung + Lebenszyklus).
- **Extraktion ist [C]** und passiert nur **opportunistisch bei Berührung**,
  jede mit Verhaltens-Audit im `doc/`. **Keine Big-Bang-Zerlegung.**
- Vor jeder Extraktion wird eine **Extraktions-Landkarte** aus dem Graphen
  (§1) erzeugt: Methoden nach Community/Verantwortung gruppiert, mit
  Zielklasse und Zeilennummer. Ohne Landkarte keine Extraktion.
- Beim Extrahieren wandert die Logik in die Schicht, in die sie gehört (§4) —
  nicht in eine neue Sammelklasse gleicher Art.

---

## §6 Persistenz-Format-Standard  [A/B]

- **Ein `*Reader` + ein `*Writer` pro Entitätstyp.** Kein Parsen/Schreiben
  des Formats anderswo — Single Source of Truth fürs Format.
- Das Dateiformat jedes Typs wird in **einem** `Schema_<Typ>.txt` im `doc/`
  beschrieben. Bei projektübergreifend gelesenen Formaten ist dieses Schema
  ein **Vertrag** (§7).
- **Pflicht:** UTF-8 · Zielverzeichnis vor dem Schreiben anlegen
  (`Files.createDirectories`) · Fehler über `System.err` (**kein stilles
  Verschlucken**, kein leerer `catch`) · Manifest-/Index-Dateien beim Laden
  überspringen.
- **Fehlender Key = dokumentierter Default.** Eine ältere Datei muss
  unverändert laden. Ein Feld ohne Bedeutung wird **nicht** geschrieben
  (optionale Werte nur bei Abweichung vom Default) — dann bleiben alte
  Dateien byte-identisch.
- **Ein Bildformat pro Zweck**, an einer Stelle geschrieben. Pixel-Export
  gehört in die `io`-Schicht, nicht in Controller und Panels.
- **Autosave-Konvention** wird pro Projekt festgelegt (z. B. bei
  `mouseReleased`, sofort nach Add/Delete) und gilt dann einheitlich.

---

## §7 Formate zwischen Projekten sind Verträge  [A/B]

Sobald **zwei** Programme dieselbe Datei lesen oder schreiben:

- Das Format gehört **einem** Projekt (dem Erzeuger). Das Schema im `doc/`
  des Erzeugers ist die Wahrheit; das lesende Projekt verweist darauf.
- **Rückwärtskompatibel erweitern**, nie umdeuten: neue Keys sind optional,
  bestehende Keys ändern ihre Bedeutung nicht.
- **Legacy-Formate werden gelesen, nicht geschrieben** — und im Schema als
  „nur lesen" markiert.
- Eine Formatänderung ist immer [C] und braucht einen Eintrag in **beiden**
  Projekten (`doc/` + `WEITERMACHEN_PROMPT.txt`).

---

## §8 Namens- & Datei-Konventionen  [B]

- Klassen `PascalCase`. Verbindliche Suffixe:
  `*Dialog` (Fenster) · `*Reader`/`*Writer` (Persistenz) · `*Renderer`
  (Zeichnen) · `*Panel` (zusammengesetzte UI) · `*Canvas` (gezeichnete
  Fläche) · `*Controller` (Fachlogik-Bündel) · `*Factory` (Erzeugung) ·
  `*Manager` (Lebenszyklus/Registry) · `Base*` (Basisklasse).
- Eine öffentliche Top-Level-Klasse pro Datei; Dateiname = Klassenname.
- Javadoc-Kopf mit der **Rolle** der Klasse (ein Satz: wofür, und was sie
  ausdrücklich nicht tut).
- Deutschsprachige Kommentare/Titel sind Konvention; Bezeichner
  englisch/gemischt wie gewachsen.
- **`*Legacy`/`*Demo`/`*Test`-Dateien im Produktivbaum sind Altlasten.**
  Sie werden im `doc/` gelistet mit Vermerk „tot / abhängig / zu löschen".
  Löschen ist [B] **nachdem** die Referenzfreiheit im Graphen (§1) belegt ist.

---

## §9 Dokumentations- & Mockup-Pflicht  [B]

- **Alle Projektdokumente liegen in `doc/`.** Kein loses `.md`/`.txt` im
  Projekt-Root oder im Quellbaum. Der Quellbaum enthält Code.
- **ASCII-Schema = Mockup-First.** Vor neuem/geändertem Dialog- oder
  Overlay-Layout zuerst ein `Schema_<Thema>.txt`-Mockup im `doc/` →
  **Freigabe** → Implementierung.
- **Redundanz-Audit vor Basisklassen-Extraktion** (`Audit Redundanz *.txt`).
- **Jede Ausnahme** von diesen Guidelines wird als Code-Kommentar **und** im
  `doc/` begründet.
- **Dokumentation, die dem Code widerspricht, ist ein Bug.** Wer eine
  Struktur ändert, korrigiert im selben Schritt `CLAUDE.md`/`doc/`. Eine
  veraltete Architekturbeschreibung ist schlimmer als keine — sie wird
  geglaubt.
- Jedes Projekt hat eine `CLAUDE.md` im Root mit: Build-Befehl, Run-Befehl,
  Einstiegspunkt, Verweis auf diese Datei und auf `doc/GUIDELINES.md`.

---

## §10 Task-Workflow für größere Änderungen  [B]

Aus `Prompt_Handling.txt` (liegt als Kopie in jedem `doc/`):

- Größere Aufgabe **[BT]** → `Task_<DateTime>_<Name>.txt` (PD) im `doc/`;
  Schritte `[m/n]` mit Risikoklasse `[A]/[B]/[C]`; Model-Empfehlung;
  „unnötige Schritte" markieren + begründen. Zwischenschritte werden als
  `[n+i.j/m]` eingefügt.
- Kleine Aufgabe **[ST]** → keine PD, aber `progress_<DateTime>_<Name>.txt`.
- `progress_<DateTime>_<Name>.txt` nach **jedem** Schritt: Prompt,
  Zusammenfassung, Vorschläge, tatsächliche Lösung, Runtime-Verify-Liste.
- `WEITERMACHEN_PROMPT.txt` zum Fortsetzen nach `/clear`; nennt immer die
  aktive PD. Wird sie zu lang: mit `<DATUM>`-Suffix archivieren und bereinigt
  neu anlegen (nur offene TODOs).
- **Abschluss-Block** als letzte Zeilen jeder Ausgabe:
  PD aktualisiert? · progress aktualisiert? · WEITERMACHEN aktualisiert? ·
  WEITERMACHEN bereinigt/archiviert? · Aufgabe vollständig? · nächster
  Schritt? · `/clear` + WEITERMACHEN empfohlen?
- **Runtime-Verify gehört dem User.** Was nur zur Laufzeit sichtbar ist, wird
  als benannte Checkliste (`A1-A9`) in der progress-Datei hinterlassen, nicht
  als „funktioniert" behauptet. „Build grün" ist kein Verify.

---

## §11 Tastatur & Maus: ein Register als Single Source of Truth  [B]

- **Jede** über Taste/Tastenkombination/Maus-Geste auslösbare Funktion steht
  in **einer** statischen Registry (`KeyBinding{combo, scope, description}`).
  Neue Funktion → **zuerst** Eintrag in der Registry, **dann** Handler-Code.
  Kein „stiller" Shortcut.
- Ein **Hilfe-Dialog** speist sich ausschließlich aus dieser Registry und
  enthält **keinen** eigenen Text. Er beantwortet drei Fragen:
  **Tasten** (Scopes Global/Canvas/Dialog) · **Maus** (Gesten in
  Trefferreihenfolge des Codes) · **Anleitung** (kurze Abläufe in
  Klick-Reihenfolge; **neue Funktion = ein Absatz**, sonst ist sie in der UI
  nicht auffindbar).
- **Welche Taste die Hilfe öffnet, legt das Projekt fest** (§14) — sie kann
  belegt sein. Nicht blind `F1` annehmen.
- **Konflikte dokumentieren:** Mehrfachbelegungen werden in der Registry mit
  Vermerk geführt, bis sie aufgelöst sind — nicht verschweigen.
- Beschreibungen werden im Dialog **umgebrochen, nie abgeschnitten.**
- Eine **handgepflegte Shortcut-Tabelle im `doc/` ist keine Registry.** Sie
  veraltet lautlos; sobald die Registry existiert, wird die Tabelle daraus
  erzeugt oder gelöscht.

---

## §12 Einstellungen sind persistent  [B]

- **Ein globaler Schalter, der eine Taste oder einen Kopf-Button hat,
  überlebt den Neustart.** Ablage über ein §6-Paar in einem
  Benutzerverzeichnis (`%APPDATA%/<Projekt>/…`), Format im `doc/`
  beschrieben. Neuer Schalter → **zuerst** Feld in der Settings-Klasse,
  dann Handler, dann Speicher-Aufruf im Umschalter.
- **Werte werden beim Speichern aus dem Live-Zustand abgeleitet**, nie in
  Schattenfeldern mitgeführt — sonst desynchronisiert ein zweiter Bedienweg
  (Taste vs. Dialog) die Datei.
- **Fehlender Key = bisheriger Laufzeit-Default.** Eine fehlende Datei muss
  exakt das alte Startverhalten ergeben.
- **Gelesen wird genau einmal** beim Start, an einer definierten Stelle im
  Lebenszyklus, und nur solange nicht zurückgeschrieben.
- **Per-Element-Zustand bleibt beim Element.** Ein globaler Wert darf
  Elemente nur ausblenden, nie pauschal einblenden — sonst überschreibt der
  Start die individuelle Sichtbarkeit.
- Der Dateiname sagt das Format (`.json` ⇒ JSON, `.txt` ⇒ Zeilenformat). Ein
  `.txt` mit JSON-Inhalt ist eine Falle für jedes lesende Werkzeug.

---

## §13 Mess-Budget: gemessen wird vor optimiert  [A/B]

- **Ein Optimierungsschritt ohne vorherige Messung ist ein „unnötiger
  Schritt".** Erfahrungswert aus GameLoop2: von drei aus dem Code
  hergeleiteten Performance-Befunden traf genau **einer** zu; der größte
  Lastfall stand nicht in der Planung.
- Das Projekt hat eine **Mess-Anzeige** mit Phasenliste (welcher Pass kostet
  die Zeit) und einen **Kopier-Shortcut**, der die Werte als Text mit
  Zeitstempel und **Zustandszeile** (Modus, Sichtbarkeiten, Elementzahlen)
  ablegt. Messungen werden **übernommen, nicht abgetippt** — ohne
  Zustandsvermerk ist ein Vorwert nicht reproduzierbar.
- **Zwei Takte, eine Bewegungsmathematik:** Logik-Takt fix und nachgeholt,
  Zeichen-Takt gedeckelt und bei Rückstand **verworfen**.
- **Keine Allokation im Zeichenpfad** (`new Color`/`new Font`/`new
  BasicStroke`/Streams/Comparatoren pro Frame) — Verschärfung von §3 auf die
  Renderer.
- **Skalierte Bilder werden gecacht, nicht pro Frame gerechnet.** Caches mit
  verschiedenen Zwecken (Dekodierung, Anzeigegröße, Thumbnail) werden
  **nicht zusammengelegt** — sie würden sich gegenseitig pro Frame verwerfen.
  Invalidierung hängt an der Bild-**Referenz**: wer ein Bild *in place*
  bemalt, umgeht alle Caches.
- **Bedarfs-Redraw wird festgestellt, nicht gemeldet [A/C].** Keine
  Meldepflicht für einzelne Handler (jede vergessene Stelle = stehendes
  Bild), sondern Quellen an der Wurzel + **Pflicht-Sicherheitsnetz**
  (spätestens jedes n-te fällige Bild wird unabhängig gezeichnet) +
  **Ausschalter** als Notausgang.
- **Eine Kennzahl, die ohne Erklärung wie ein Einbruch aussieht, gehört
  nicht ins Overlay** — sie erklärt sich selbst (z. B. „Ruhe: x % gespart").

---

## §14 Projekt-Steckbrief (jedes Projekt füllt das aus)

Die `doc/GUIDELINES.md` jedes Projekts beginnt mit dieser Tabelle. Sie
besetzt die Rollen dieser Datei und ist der Grund, warum es je Projekt keine
zweite Regelkopie braucht.

| Rolle | Projekt-Klasse/Wert |
|---|---|
| UI-Toolkit | AWT / Swing / … |
| Einstiegspunkt + Run-Befehl | |
| Build-Befehl + erwartete Artefaktzahl | |
| Token-Quelle (§3) | |
| Fenster-Basisklasse (§2) | |
| Zeichenflächen-Basisklasse (§2) | |
| Panel-Basisklassen (§2) | |
| Bestätigungs-Dialog (§2) | |
| Grafik-Utility (§2) | |
| Schichten-Ist: Packages (§4) | |
| God-Klasse(n) (§5) | |
| Persistenz-Paare (§6) | |
| Format-Verträge nach außen (§7) | |
| Settings-Klasse + Ablageort (§12) | |
| Shortcut-Registry + Hilfe-Taste (§11) | |
| Mess-Anzeige + Kopier-Shortcut (§13) | |
| Graphify-Scope (§1) | |

---

## §15 Nicht anwendbar (aus den Web-Guidelines)

i18n/Fallback-Ketten · Barrierefreiheit/WCAG · DSGVO/Fonts/Cookies/CSP ·
No-Build/ES5/IIFE · HTML/CSS/JS-Trennung.

Grund: interne, einsprachige Desktop-Werkzeuge ohne Web/Server/Netz. Die
inhaltlichen Prinzipien sind übersetzt: Mockup-Pflicht → §9 · Basisklassen
(`base_<type_subtype>`) → §2 · Design-Tokens → §3 · Schichtentrennung → §4.

**Ausnahme:** Sobald ein Java-Projekt eine DB/API anbindet, wird **vor**
Beginn geklärt, welche (Frage aus `Prompt_Handling.txt`), und die
Architektur-Regeln werden um eine `service`-Schicht zwischen `core` und `io`
ergänzt. Ohne diese Klärung wird keine Persistenz-Architektur entworfen.

---

## Anhang — Herkunft und Pflege

- Extrahiert am 2026-07-30 aus `GameLoop2/src/main/doc/GUIDELINES.md`
  (§0–§17) + `GUIDELINES_VORSCHLAG_2026-07-27.md` + `Prompt_Handling.txt`.
- **Erste Instanziierungen:** GameLoop2 (AWT, 2.5D-Engine mit Editor) ·
  TransparencyTool (Swing, Bild-/Szenen-Editor).
- **Pflege:** Eine Regel wandert hierher, sobald sie im **zweiten** Projekt
  gilt. Bis dahin bleibt sie im Projekt-Dokument. Eine hier geänderte Regel
  gilt sofort für alle Projekte — die Änderung braucht deshalb einen
  Eintrag in der `WEITERMACHEN_PROMPT.txt` **jedes** betroffenen Projekts.
- Projektspezifische Paragraphen werden ab **§20** numeriert, damit §0–§15
  projektübergreifend dieselbe Bedeutung behalten.
