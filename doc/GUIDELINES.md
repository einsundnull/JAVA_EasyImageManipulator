# TransparencyTool — Projekt-Guidelines (verbindlich)

> **Status: VERBINDLICH ab 2026-07-30.**
>
> **Diese Datei ist die zweite Hälfte der Regeln.** Die erste ist
> **`../JAVA_GUIDELINES_UNIVERSAL.md`** — sie gilt für alle Java-Projekte im
> Workspace und wird **zuerst gelesen**. Hier stehen nur: der
> Projekt-Steckbrief (welche Klasse welche universelle Rolle besetzt) und die
> Paragraphen **§20+**, die es nur in diesem Projekt gibt.
>
> **Leitprinzip (Univ. §0): Unnötige Schritte nicht ausführen.** Die Regeln
> gelten verpflichtend für **neuen und berührten** Code. Reines Refactoring
> laufender Logik allein zur Regelkonformität ist ein „unnötiger Schritt".
> Risikoklassen: **[A]** großes ROI · **[B]** 100 % sicher · **[C]** größeres
> Risiko (nur mit separater Freigabe + Audit im `doc/`).
>
> **Lesereihenfolge:** `../JAVA_GUIDELINES_UNIVERSAL.md` → diese Datei →
> `doc/Prompt_Handling.txt` → `doc/WEITERMACHEN_PROMPT.txt` → aktive `Task_*.txt`.
> Analyse und Belege zu jeder Regel:
> `doc/GUIDELINES_VORSCHLAG_2026-07-30.md` (Befunde S1–S13).
> Wegweiser durch alle Dokumente im Ordner: `doc/README.md`.

---

## Projekt-Steckbrief (Univ. §14)

| Rolle | Besetzung |
|---|---|
| UI-Toolkit | **Swing** (nicht AWT — GameLoop2-Rendering-Regeln gelten hier **nicht**, siehe §24) |
| Einstiegspunkt | `paint.SelectiveAlphaEditor` |
| Run | `java -cp bin --module-path bin -m TransparencyTool/paint.SelectiveAlphaEditor` |
| Build | `javac -encoding UTF-8 -sourcepath src -d bin src/paint/*.java src/module-info.java` — **`-encoding UTF-8` ist Pflicht** (Unicode-Symbole im Quelltext). Erwartet: **exit 0, 328 `.class`** (Stand 2026-07-30, nach Auslagerung + Altlast-Löschung §28) |
| Quellbaum | **nur** `paint` (103) + `book` (3) + `module-info.java`. Seit 2026-07-30 eigene Projekte: `../SpriteAnimator/`, `../PathAnimator/`, `../JavaDemos/` |
| Token-Quelle (Univ. §3) | **`AppColors`** (Farben) **+ `AppTheme`** (Fonts, Abstände, Radien, Strokes, Größen) — zwei Klassen, überschneidungsfrei → §21 |
| Fenster-Basisklasse (Univ. §2) | **fehlt noch** → §20. Bis dahin `UIComponentFactory.createBaseDialog(...)` als Minimum |
| Zeichenflächen-Basisklasse | **keine** (Swing puffert selbst) → Regeln in §24 |
| Panel-Basisklassen | **`BaseSidebarPanel`** (6 Unterklassen) · **`CardListPanel`** (2) |
| Domänen-Basisklasse | **`Layer`** (Wert-Objekt, unveränderlich) → §29 |
| Bestätigungs-Dialog | **fehlt noch** → §20. `JOptionPane` ist Altlast, kein Vorbild |
| Grafik-/Widget-Fabrik | `UIComponentFactory` · `UIBuilder` · `PanelToggleButton` (`SmartLabel` ist am 2026-07-30 nach `../JavaDemos/` gezogen — es war nur vom Demo benutzt) |
| Pfad-Zentrale | **`AppPaths`** — **alle** Pfade unter `%APPDATA%\TransparencyTool\`. Nie `new File("...")` mit hartem Pfad |
| Zustands-Träger | **`CanvasInstance`** (Zustand **pro Canvas**), `canvases[2]`, `ci()`/`ci(idx)` → §30 |
| Fachlogik | **18 `*Controller`** + 5 `*CallbacksFactory` + `Callbacks`-Interfaces → §22 |
| God-Klasse (Univ. §5) | **`CanvasPanel` (3299 Z.)** — *nicht* das Hauptfenster (566 Z., bereits Orchestrator) |
| Persistenz-Paare (Univ. §6) | `SceneFileReader/Writer` · `TextReader/Writer` · `GameSceneReader/Writer` · `ToolLegacySceneReader` (nur lesen) · `SceneSerializer` · `PageLayoutManifest` |
| Format-Verträge (Univ. §7) | **Szenen-Format ↔ GameII** → §23 |
| Settings (Univ. §12) | **`AppSettings`** (Singleton) → `%APPDATA%\TransparencyTool\settings\default.txt` (Inhalt: JSON, siehe §31) |
| Shortcut-Registry (Univ. §11) | **fehlt noch** → §25. Aufbau-Ort ist `KeyboardShortcutManager` |
| Mess-Anzeige (Univ. §13) | **keine** — bewusst, siehe §26 |
| Graphify-Scope (Univ. §1) | Scan-Root **`src/`** (schließt `bin/`, `doc/`, `resources/` ohne Filter aus), code-only. Graph ist eingecheckt: 2479 Knoten / 6369 Kanten / 123 Communities, 0 Tokens |

---

## §20 Fenster & Bestätigungen  [A / Umsetzung C]

**Anlass:** 12 Klassen erben direkt von `JFrame`/`JDialog`/`JWindow`, jede
baut Titelbar, Größe, ESC-Verhalten und Farben selbst. `JOptionPane` steht in
16 Dateien (~100 Aufrufe) und bringt den hellgrauen System-Look in eine
dunkle App (Befunde S1/S2).

- **Ab jetzt [B]:** Ein **neues** Fenster wird **nicht** roh als `JDialog`/
  `JFrame`/`JWindow` gebaut. Bis `BaseDialog` existiert, ist
  `UIComponentFactory.createBaseDialog(owner, title, w, h)` der einzige
  erlaubte Weg — und die dort gesetzten Werte werden nicht lokal überschrieben.
- **Ab jetzt [B]:** **Keine neuen `JOptionPane`-Aufrufe.** Bestehende sind
  dokumentierte Altlast (Univ. §0), kein Vorbild und keine Rechtfertigung.
- **`BaseDialog` ist fällig [C], Reihenfolge Pflicht** (Univ. §2):
  1. `doc/Audit Redundanz Dialoge <Datum>.txt` über alle 12 Fenster —
     was ist wirklich gemeinsam (Titelbar, ESC, Resize, Farben, Positionierung)?
  2. `doc/Schema_BaseDialog.txt` als ASCII-Mockup → **Freigabe**.
  3. Extraktion, danach Migration **eine Datei pro Schritt** mit Sichtprüfung.
- **Eine Factory ist keine Basisklasse.** `createBaseDialog` teilt das
  Aussehen, nicht das Verhalten. Genau deshalb hat jedes der 12 Fenster ein
  eigenes ESC-/Schließ-/Resize-Verhalten. Wer das Verhalten zum dritten Mal
  kopiert, baut stattdessen die Basisklasse.
- `ConfirmDialog` (Ja/Nein) und `MessageDialog` (Info/Fehler) sind Teil
  desselben Schritts — sonst bleibt `JOptionPane` mangels Alternative stehen.

---

## §21 Token-Quelle `AppColors` erweitern, nicht umgehen  [A / Bereinigung C]

**Anlass:** 454 `new Color(`, 162 `new Font(`, 110 `new BasicStroke(` —
davon 7 `new Font` in `UIComponentFactory` selbst, dem StyleGuide-Anker.
Ursache ist keine Disziplinlosigkeit, sondern eine **Lücke**: `AppColors`
kennt nur Farben (Befund S3).

- **Ab jetzt [B]:** In berührtem Code **kein** `new Color(...)` /
  `new Font(...)` / `new BasicStroke(...)`. Fehlt ein Token, wird es
  **angelegt** — nicht das Literal getippt.
- **Erledigt 2026-07-30 [A], additiv: `AppTheme` existiert.** Zwei Klassen,
  **überschneidungsfrei** — `AppColors` = Farben, sonst nichts; `AppTheme` =
  `FONT_*` (12 abgezählte Fonts) · `STROKE_*` · `RADIUS_*` · `PAD_*`/`GAP_*` ·
  `BTN_W`/`BTN_H` · `alpha(Color,int)` · `pad(...)`. **Eine Farbe gehört nie
  in `AppTheme`, ein Font nie in `AppColors`.**
  Der Name folgt der Projektkonvention (`AppColors`/`AppPaths`/`AppSettings`).
  **Die Werte sind abgezählt, nicht erfunden:** die Font-Skala deckt die
  gemessene Verteilung ab (PLAIN 11 = 27×, PLAIN 12 = 18×, BOLD 11 = 13×,
  PLAIN 10 = 11× …), die Strichstärken 1/1.5/2/3 px ebenso (19×/9×/7×/1×).
  **Kein bestehender Wert wurde geändert; die Klasse hatte beim Anlegen null
  Aufrufer.**
- **Bereinigung [C] — noch offen, braucht Freigabe.** Inkrementell **pro
  Datei mit Sichtprüfung**, nie in einem Rutsch.
  **Sinnvoller Anfang: `UIComponentFactory`** — es ist der StyleGuide-Anker
  und tippt selbst 7× `new Font`; dort steht auch `BUTTON_WIDTH/HEIGHT` (36),
  das `SelectiveAlphaEditor` als `TOPBAR_BTN_W/H` ein zweites Mal führt.
  Danach nach Dichte: `ElementLayerPanel` (64 Farben) ·
  `TranslationMapListPanel` (18 Fonts) · `PageLayoutToolbar` (12) ·
  `CanvasPanel` (24 Farben) · `MapsPanel` (20).
  **Jede Migration ist werterhaltend** — sie ersetzt ein Literal durch das
  Token mit demselben Wert. Weicht ein Fundstellen-Wert ab (z. B. `Font` 14,
  Radius 16), wird er **nicht** auf das nächste Token gerundet: entweder neues
  Token oder Literal mit Begründung stehen lassen.
- **Alpha-Varianten werden abgeleitet**, nie als zweites RGB-Tripel getippt.
  Für `new Color(255,255,255,55)`-Muster gehört ein `alpha(Color,int)`-Helfer
  in die Token-Quelle.
- **Zwei Token mit gleichem RGB sind nicht automatisch dasselbe Token.** Vor
  dem Zusammenlegen prüfen, ob die *Bedeutung* dieselbe ist, und die
  Entscheidung als Kommentar festhalten.
- Farben, die zur **Laufzeit** vom User gewählt werden (Primär-/Sekundärfarbe,
  Canvas-Schachbrett `bg1`/`bg2`, Kartenfarben) sind **keine** Tokens. Sie
  gehören in `AppSettings` (§31) — das ist die dokumentierte Ausnahme.

---

## §22 `CanvasPanel` ist die God-Klasse  [A-Regel / Extraktion C]

**Anlass:** Die Controller-Zerlegung hat `SelectiveAlphaEditor` von ~1900 auf
566 Zeilen gebracht — ein Erfolg, der als Muster gilt. Sie hat den **Canvas
nicht entlastet**: `CanvasPanel` trägt mit 3299 Zeilen Maus-Input,
Hit-Testing, `paintComponent`, Overlays, Skalierhandles, Float-Interaktion
und Pfad-Bearbeitung (Befund S4).

- **Verbindlich [A/B]: Neue Fachlogik landet nicht in `CanvasPanel`.**
  Zielbild: `CanvasPanel` = Eingabe-Weiterleitung + Zeichnen. Alles andere
  gehört in einen `*Controller` (Muster: die bestehenden 18) oder hinter ein
  `Callbacks`-Interface.
- **Das bestehende Muster ist verbindlich [B]:** Ein Panel kennt den Editor
  **nicht** direkt, sondern ein `Callbacks`-Interface; die Verdrahtung macht
  eine `*CallbacksFactory`. Neue Panels folgen dem — kein
  `SelectiveAlphaEditor`-Feld in einem Panel.
- **Extraktion ist [C]**, nur opportunistisch bei Berührung, jede mit
  Verhaltens-Audit im `doc/`. Vorher **Extraktions-Landkarte aus dem
  Graphen** (Univ. §1/§5). Erwartbare Ziele: `HitTester` ·
  `CanvasOverlayRenderer` · `HandleController` (8 Skalierhandles) ·
  `PathEditController`. **Keine Big-Bang-Zerlegung.**
- **Trefferradien und Handle-Geometrie sind Daten, keine Literale [B].**
  Die 8 Skalierhandles um Float und Layer sind **eine** Deklaration
  (Position, Größe, Cursor, Wirkung); `handle-hit`-Tests nie erneut als
  Literal in Maus-Handlern. (Übertragung von GameLoop2 §12 auf den einzigen
  Fall, den es hier gibt.)
- **Hit-Test-Reihenfolge ist Handle → Punkt/Layer → Kante/Fläche [B]**
  (GameLoop2 §16). Ein Punkt gewinnt gegen seine Kante, sonst wird er
  unklickbar. Die Reihenfolge im Code ist die Reihenfolge, die im
  Hilfe-Dialog unter „Maus" dokumentiert wird (§25).

---

## §23 Szenen-Format ist ein Vertrag mit GameII  [A/B]

**Anlass:** Das Szenen-Format ist **kein internes Format** — GameII/GameLoop2
liest es, und `GameSceneReader`/`GameSceneWriter`/`ToolLegacySceneReader`
bedienen die Gegenrichtung. Eine stille Änderung bricht ein anderes Programm.

- **`doc/SCENE_FORMAT_READ_WRITE.md` ist der Vertrag**,
  nicht nur eine Beschreibung. Wer das Format ändert, ändert **zuerst** dieses
  Dokument und trägt es in die `WEITERMACHEN_PROMPT.txt` **beider** Projekte
  ein (Univ. §7).
- **Formatfamilie ist gemeinsam mit GameLoop2:** `#Sektion:` → darunter
  `-key: value` bzw. `-wert`; UTF-8 ohne BOM; Leerzeilen ignoriert;
  abschnittbasiert (Reihenfolge beliebig). Ein neues Feld ist **optional mit
  Default** — alte Szenen müssen unverändert laden.
- **Struktur ist Teil des Vertrags:** Szene = **Verzeichnis**
  (`<Name>/<Name>.txt` + `images/` + `texts/` + `paths/`),
  Verzeichnisname = Dateiname, Bilder immer PNG, **erster `#Images:`-Eintrag
  = Hintergrund** (ohne Metadaten).
- **Legacy wird gelesen, nicht geschrieben [B]:** GameII-Szenen mit der
  `.txt` direkt im `scenes/`-Ordner sind Legacy — `ToolLegacySceneReader`
  ist nur lesend, und das bleibt so.
- **Nicht implementierte Abschnitte werden als solche dokumentiert.**
  `#Paths:` wird referenziert, aber nicht gelesen; `GameSceneWriter` schreibt
  PathLayer nicht. Solche Lücken stehen im Schema — ein „TODO" nur im Code
  ist für das lesende Programm unsichtbar.
- **Beim Zurückschreiben fremder Dateien gilt: unbekannte Abschnitte
  unverändert übernehmen [A].** `GameSceneWriter` macht das über
  `SpriteLayer.rawLines()` und aktualisiert nur `#INIT_POSITION`/`#SIZE`.
  Dieses Verhalten ist verbindlich — ein Writer, der fremde Abschnitte
  wegwirft, zerstört Animationen, Physik und Links.
- **Die Koordinaten-Umrechnung TT ↔ GameII steht an einer Stelle** (heute im
  Javadoc von `GameSceneWriter`: Anker = Unterkante-Mitte, x/y in Prozent).
  Sie gehört ins Schema und wird nicht in Aufrufern nachgerechnet.

---

## §24 Swing-Regeln (ersetzt GameLoop2 §6)  [B]

GameLoop2 §6 (Double Buffering über `DoubleBufferedCanvas`, `GfxUtil.aa`,
60-FPS-GameLoop) ist **nicht übertragbar**: Swing puffert selbst, und es gibt
keinen GameLoop. An seine Stelle tritt:

- **`paintComponent(Graphics g)`** ist die einzige Zeichenmethode;
  `super.paintComponent(g)` zuerst. Nie `paint()` überschreiben, nie
  `getGraphics()` außerhalb des Zeichenpfads.
- **`paintComponent` zeichnet und rechnet — es ändert nichts.** Kein
  Modell-Schreiben, kein `setState`, kein Datei-/Netzzugriff, kein
  `repaint()`-Aufruf aus dem Zeichenpfad (Univ. §4: „render liest, schreibt
  nicht zurück"). Was pro Zeichnung berechnet werden muss, wird vorher in
  `CanvasInstance` gelegt.
- **`Graphics2D`-Zustand wird zurückgesetzt**: wer `setTransform`,
  `setClip`, `setComposite` oder `setStroke` setzt, stellt den Vorzustand
  wieder her (oder arbeitet auf `g.create()`).
- **Antialiasing** immer über **eine** Stelle (Helfer in der Token-Quelle),
  nicht als wiederholter `RenderingHints`-Block. Heute steht der Block
  mehrfach in `UIComponentFactory` — das ist die Fundstelle für den Helfer.
- **EDT-Disziplin [B]:** Alle UI-Änderungen auf dem Event Dispatch Thread.
  Lange Arbeit (Bilder laden, Verzeichnisse scannen, Thumbnails, TTS) gehört
  in einen **`SwingWorker`** (Muster: `TileGalleryPanel`, `PreloadController`,
  `StartupDialog`), nicht in einen rohen `Thread`. `invokeLater` ist für
  „nach dem aktuellen Event", nicht für Nebenläufigkeit.
- **Geteilter Zustand zwischen Worker und EDT ist explizit
  threadsicher.** `CanvasInstance.fileCache`/`preloadCache` sind
  `ConcurrentHashMap` — das ist Absicht und wird nicht „vereinfacht".
- **`javax.swing.Timer` (EDT), nicht `java.util.Timer`** für alles, was UI
  berührt (Zoom-Animation, Toasts).

---

## §25 Tastatur- & Maus-Register  [B]

**Anlass:** Die Beschreibung jeder Taste steckt nur im Code;
`doc/Shortcut Table.txt` ist handgepflegt und **schon veraltet** — es fehlen
F1–F7, ALT+T, ALT+P, R/SHIFT+R, CTRL+ALT+S, CTRL+SHIFT+S, SHIFT+ALT+A.
Es gibt **keinen** Hilfe-Dialog (Befund S6).

- **Eine Registry [B]:** `paint.KeyBindings.ALL` als statische Liste
  `KeyBinding{combo, scope, description}`. **Neue Tastenfunktion → zuerst
  Eintrag, dann Handler.** Kein stiller Shortcut.
  `KeyboardShortcutManager` **verdrahtet** die Registry (InputMap/ActionMap +
  globaler `KeyEventDispatcher`), er ist nicht die Quelle der Beschreibung.
- **Scopes:** `GLOBAL` (`KeyEventDispatcher`, alle Fenster — heute F1–F7,
  ALT+T, ALT+P) · `WINDOW` (`WHEN_IN_FOCUSED_WINDOW`-InputMap des
  Hauptfensters) · `DIALOG` (ESC/ENTER je Dialog) · `MOUSE` (Canvas-Gesten).
- **Maus-Gesten werden wie Tasten registriert**, in der
  **Trefferreihenfolge des Codes** (§22): CTRL+Rad = Zoom · Rad = vertikal ·
  SHIFT+Rad = horizontal · Mitteltaste-Drag = Pan · CTRL+Links-Drag = Pan ·
  SHIFT+Drag = Gummiband-Multiselect · SHIFT+Klick = Selektion
  hinzufügen/entfernen · Rechtsklick-Drag in Sidebars = Drag-to-Copy.
- **Hilfe-Taste ist `SHIFT+F1` — ENTSCHIEDEN (Q3, 2026-07-30).**
  **Nicht F1**: F1 ist mit `toggleSecondaryWindow` belegt, F1–F7 sind
  vollständig vergeben (Befund S7). F1 wird **nicht** freigeräumt — die
  Zweitfenster-Belegung ist eingeübt. Bewusste Abweichung von GameLoop2 §11;
  wer sie „angleicht", nimmt dem Zweitfenster seine Taste.
- Der Dialog rendert **nur** die Registry, gruppiert nach Scope, plus
  `KeyBindings.GUIDE` (kurze Abläufe in Klick-Reihenfolge). **Neue Funktion =
  ein GUIDE-Absatz**, sonst ist sie in der UI nicht auffindbar.
  Beschreibungen werden **umgebrochen, nie abgeschnitten**.
- **Mockup-Pflicht (Univ. §9):** `doc/Schema_KeyBindings_Dialog.txt` zuerst.
- **`Shortcut Table.txt` wird aus der Registry erzeugt oder gelöscht.** Eine
  zweite, handgepflegte Liste ist die Ursache des Problems, nicht die Lösung.
- **Konflikte dokumentieren:** ALT+P (Floating PaintBar) und die
  Alpha-Editor-Belegungen von `R`/`V` ohne Modifier sind mit Vermerk in die
  Registry aufzunehmen, bis geprüft ist, ob sie in Textfeldern störn.

---

## §26 Messen statt vermuten (abgeschwächtes Render-Budget)  [B]

TransparencyTool hat **keinen GameLoop** — die Frame-Budget-Pflicht aus
GameLoop2 §17 gilt hier **nicht**, und eine Mess-Anzeige wird **bewusst
nicht** gefordert (das wäre ein „unnötiger Schritt", Univ. §0).

Was bleibt:

- **Kein Optimierungsschritt ohne vorherige Messung.** Erfahrungswert aus
  GameLoop2: von drei aus dem Code hergeleiteten Befunden traf genau einer
  zu. Gemessen wird notfalls per `System.nanoTime` um den verdächtigen Pass,
  und der Messwert wird mit **Zustandsvermerk** (Zoom, Bildgröße,
  Layer-/Datei-Anzahl) in die progress-Datei übernommen.
- **Zwei Lastfälle sind bekannt und relevant:** Thumbnail-Skalierung in der
  Galerie (`getScaledInstance(SCALE_SMOOTH)` pro Zeile pro Repaint war in
  GameLoop2 der teuerste Einzelposten) und `paintComponent` bei großem Zoom.
  Skalierte Bilder werden **gecacht** (Univ. §13); Caches für Anzeigegröße
  und Thumbnail bleiben **getrennt**.
- **Cache-Invalidierung hängt an der Bild-Referenz.** Wer ein
  `BufferedImage` *in place* bemalt statt die Referenz zu ersetzen, umgeht
  jeden Cache. `PaintEngine` arbeitet bewusst in-place auf `workingImage` —
  dieses Bild wird deshalb **nicht** gecacht.

---

## §27 Zwei Koordinatensysteme, definierte Übergänge  [A/B]

Häufigste Fehlerquelle der Codebase.

- **Image-Space** = Pixel in `CanvasInstance.workingImage`. Alles Persistente
  ist Image-Space: `Layer`-Bounds, `selectedAreas`, `floatRect`,
  `PaintEngine`-Argumente, Szenen-Dateien.
- **Canvas-/Screen-Space** = Image-Space × `zoom`. Das liefert
  `MouseEvent.getPoint()`.
- **`screenToImage()` ist der einzige Weg hinein**, `toSx/toSy/toSw()` der
  einzige Weg hinaus. **Kein `* zoom` / `/ zoom` in Fachcode** — wer eine
  Umrechnung selbst tippt, baut den nächsten Off-by-Zoom-Fehler.
- **`PaintEngine` bleibt Image-Space und stateless.** Statische Methoden auf
  einem `BufferedImage`; kein Zoom, kein `Component`, kein UI-Zustand. Das
  ist die Zusage, die sie testbar hält.
- **Prozent-Koordinaten sind ein drittes System und gehören GameII** (§23).
  Sie existieren nur in `GameSceneReader/Writer`, nie im Editor-Zustand.

---

## §28 Dokumentation: alles nach `doc/`  [B]

- **`doc/` ist der einzige Ort für Projektdokumente.** Bis 2026-07-30 lagen
  13 `.md` im Root und 11 `.txt` in `src/paint/` (Quellbaum). Der Quellbaum
  enthält Code.
- **Erledigt 2026-07-30:** 13 `.md` aus dem Projekt-Root und 11 `.txt` aus
  `src/paint/` liegen jetzt in `doc/`. **`CLAUDE.md` ist das einzige Dokument
  außerhalb** — sie ist der Einstiegspunkt und wird im Root erwartet.
- **`doc/` bleibt flach, mit `doc/README.md` als Wegweiser.** Die älteren
  Dokumente verweisen mit **nacktem Dateinamen** aufeinander
  (`siehe F6_FEATURE.md`); ein `archive/`-Unterordner hätte jeden dieser
  Verweise gebrochen. Die Sortierung nach *verbindlich / Vertrag / Analyse /
  Feature / historisch* leistet `README.md`, nicht die Ordnerstruktur.
- **Ausnahme, bewusst liegengelassen:** `src/paint/JAVA_EasyImageManipulator-Push.sh`
  bleibt im Quellbaum. Es ist kein Dokument, sondern das Deploy-Skript — und
  es wird vermutlich von außen per Pfad aufgerufen (die regelmäßigen
  „WhiteBoard Update"-Commits). Ein Verschieben bräche diese Automatik
  lautlos. Verlegen nur zusammen mit dem Aufrufer.
- **Zusammenfassungs-Dokumente einer abgeschlossenen Aufgabe** werden zu
  `progress_<DateTime>_<Name>.txt` (Univ. §10) — nicht zu einer weiteren
  Architekturbeschreibung, die veraltet.
- **`CLAUDE.md` war überholt und ist am 2026-07-30 neu geschrieben** (Befund
  S9: beschrieb das Hauptfenster als 1900-Zeilen-God-Klasse, `CanvasPanel`
  und die Ruler als innere Klassen, kannte die 18 Controller nicht).
  **Univ. §9 gilt ab jetzt: Wer die Struktur ändert, korrigiert `CLAUDE.md`
  im selben Schritt.** Eine falsche Architekturbeschreibung ist schlimmer als
  keine — sie wird geglaubt.
- **Altlasten sind beseitigt (2026-07-30, Befund S10):** gelöscht wurden
  `AutoGrowingPillFieldDemo2`, `BookListPanelLegacy`, `BookPagesPanelLegacy`,
  `PageLayoutToolbarLegacy` (892 Z.) und der veraltete `paint/`-Ordner mit
  `.class`-Dateien im Projekt-Root. **`ToolLegacySceneReader` ist keine
  Altlast** — Legacy-Lesen ist laut §23 gewollt; `book/` auch nicht, es wird
  produktiv genutzt.
- **Eine Klasse mit `main()` ist auch ohne Referenz nicht tot [B].** Vier der
  acht Löschkandidaten waren eigenständig startbar und wurden deshalb nach
  `../JavaDemos/` **ausgelagert statt gelöscht** (`Demo`,
  `DemoWorksheetEditor{,2}`, `AutoGrowingPillFieldDemo`). `SmartLabel` kam
  mit: es sah wie Produktivcode aus, wurde aber von nichts außer `Demo`
  benutzt — ohne den Umzug wäre eine Dublette nötig gewesen (Univ. §3).
  **Merksatz: „referenzfrei" ≠ „unbenutzt".**
- **`com.spriteanimator` und `PathAnimator` sind AUSGELAGERT** (Q1,
  2026-07-30) in die eigenständigen Workspace-Projekte
  `../SpriteAnimator/` und `../PathAnimator/`. Beide hatten **null**
  Querreferenzen zu `paint` (in beide Richtungen) und wurden vom Build-Befehl
  ohnehin nicht erfasst — die Auslagerung war deshalb ein reiner
  Verschiebe-Schritt [B]. Sie unterliegen ab jetzt derselben universellen
  Regelbasis mit eigenem Steckbrief.
  **`src/` enthält damit nur noch `paint` + `book` + `module-info.java`.**
  Ein Programm mit eigener `main`-Methode gehört in ein eigenes Projekt —
  nicht in den Quellbaum eines anderen.

---

## §29 `Layer` ist unveränderlich — Undo hängt daran  [A/B]

- **Jede Mutation eines `Layer` gibt eine neue Instanz zurück**
  (`with*`-Methoden, 25 Stück). **Kein Setter, kein nicht-`final`-Feld.**
  Identität läuft über `id()`, nie über Objektgleichheit.
- Wer einen Layer ändert, **ersetzt ihn in `activeElements` *und*
  `selectedElements`** (Suche über `id()`). Nur eine der beiden Listen zu
  aktualisieren ist die typische Fehlerquelle: die Selektion zeigt dann auf
  eine veraltete Kopie.
- **`pushUndo()` **vor** der Änderung, `markDirty()` danach.** Die Reihenfolge
  ist die Zusage; `markDirty()` allein macht die Änderung nicht
  rückgängig-fähig, `pushUndo()` allein nicht speicherbar. Beide sind
  Delegationen (`SaveController` / `LayoutController`) — nie an den Stacks
  vorbei arbeiten.
- **Ein `pushUndo()` pro Benutzeraktion**, nicht pro Element. Eine Aktion auf
  n Layern ist **ein** Undo-Schritt (die Schleife in `toggleVis`, die pro
  Element `pushUndo()` ruft, ist eine dokumentierte Altlast).
- **Die schwebende Auswahl (`floatingImg`/`floatRect`) ist nicht Teil des
  Undo-Bands**, solange sie schwebt. `cancelFloat()` stellt über `doUndo()`
  den Zustand vor dem Anheben wieder her — deshalb muss vor dem Anheben ein
  `pushUndo()` stehen.
- **`PaintEngine` arbeitet destruktiv auf `workingImage`** (in-place, Absicht,
  §26). Genau darum ist `pushUndo()` vor **jedem** `PaintEngine`-Aufruf
  Pflicht — es gibt keinen anderen Weg zurück.

---

## §30 Zustand gehört zum Canvas, nicht zum Editor  [B]

- **Alles, was es zweimal gibt, steht in `CanvasInstance`** — Bild, Datei,
  Undo-Stacks, Layer, Selektion, Float, Zoom, Verzeichnisliste, `appMode`,
  `showGrid`. **Ein neues Feld im Hauptfenster, das pro Canvas verschieden
  sein kann, ist ein Regelverstoß.**
- **Genuin globaler Zustand bleibt im Hauptfenster** und ist als solcher
  erkennbar: Zwischenablage, `dirtyFiles`, `showRuler`, `rulerUnit`,
  `projectManager`.
- **Zugriff nur über `ci()` / `ci(idx)`.** Kein `canvases[0]` in Fachcode —
  eine feste `0` ist der Grund, warum ein zweiter Canvas Funktionen verliert.
- Wer eine Aktion schreibt, entscheidet **explizit**, ob sie auf dem aktiven
  Canvas (`ci()`) oder auf einem benannten (`ci(idx)`) arbeitet. Die
  Delegationen im Hauptfenster zeigen das Muster: `pushUndo()` →
  `pushUndo(activeCanvasIndex)`.

---

## §31 `AppSettings` — ein Format, ein Name  [B]

- **`AppSettings` ist die einzige Quelle globaler Einstellungen**, gelesen
  **genau einmal** beim Start (Univ. §12). Neuer globaler Schalter →
  **zuerst** Feld in `AppSettings`, dann Handler, dann `save()` im
  Umschalter.
- **Fehlender Key = bisheriger Laufzeit-Default.** Eine fehlende Datei muss
  exakt das alte Startverhalten ergeben.
- **Werte beim Speichern aus dem Live-Zustand ableiten**, nicht in
  Schattenfeldern mitführen — sonst desynchronisiert der zweite Bedienweg
  (Taste vs. Dialog) die Datei.
- **Der Dateiname lügt absichtlich — ENTSCHIEDEN (Q4, 2026-07-30):** Die
  Datei heißt `settings/default.txt` und enthält **JSON**. Der Name **bleibt**,
  damit bestehende Installationen ihre Einstellungen behalten; korrigiert
  wurden stattdessen das Javadoc (nannte eine nicht existierende
  `settings.json`) und die Formatbeschreibung → **`doc/Schema_AppSettings.txt`**.
  `AppPaths` ist die Wahrheit über den Pfad.
- **Der Parser ist zeilenbasiert, kein JSON-Parser:** ein Key pro Zeile.
  Wer die Ausgabe umformatiert, macht die Datei unlesbar. Details im Schema.
- **Encoding-Altlast [C]:** Gelesen/geschrieben wird mit `FileReader`/
  `FileWriter` **ohne Encoding**, also plattformabhängig statt UTF-8
  (Univ. §6). Betrifft Pfade mit Umlauten in `recentFiles`/`recentProjects`.
  Umstellung ist [C], weil bereits geschriebene Dateien danach anders
  gelesen würden.
- **Handgeschriebenes JSON ohne Bibliothek ist Bestand, nicht Vorbild.**
  Fünf Klassen serialisieren JSON selbst (`AppSettings`, `ProjectManager`,
  `MapManager`, `CardListStore`, `book.JsonStorage`). Ein **neuer** Typ
  bekommt keinen sechsten Serializer, sondern nutzt einen bestehenden Weg.
  Ob eine Bibliothek dazukommt, wird mit dem DB/API-Anbieter entschieden
  (§33) — nicht nebenbei.
- **Alle Pfade über `AppPaths`.** Kein harter Pfad, kein `%APPDATA%`-Lesen
  außerhalb dieser Klasse, kein `src/`→`bin/`-Pfad-`replace` in der
  Persistenz (`TextWriter` tut das heute — dokumentierte Altlast, Befund S11).
- **Zwei bekannte Verstöße gegen diese Regel (Befund S13, 2026-07-30):**
  `BookController.BOOKS_ROOT` baut den Pfad **hartkodiert** aus
  `user.home + "AppData/Roaming/TransparencyTool/books"` — es liest
  `%APPDATA%` nicht und umgeht damit auch den `AppPaths`-Fallback; bei
  umgeleitetem oder nicht-englischem Profil zeigt der Pfad ins Leere.
  `CardListStore` legt `cardfolders/` unter **`settings/`** ab, während sein
  Javadoc `%APPDATA%/TransparencyTool/cardfolders/` behauptet.
  **Beide gehören nach `AppPaths` [B]** — bei `books/` ist es zusätzlich eine
  Fehlerbehebung, nicht nur Regelkonformität. Achtung: ein Umzug ändert den
  Ablageort bestehender Daten, ein reines Verlagern der Pfadbildung nicht.

---

## §33 Vorbereitung auf DB/API: Karten & Übersetzungs-Maps isolieren  [A/B]

**Anlass:** Antwort auf Q2 (2026-07-30). Eine Anbindung **ist geplant**, die
betroffenen Daten stehen fest — **Karten + Übersetzungs-Maps** —, der
**Anbieter ist ausdrücklich noch offen.**

Daraus folgt die einzige sinnvolle Regel: **nicht auf einen Anbieter
entwerfen, sondern die betroffenen Stellen jetzt so isolieren, dass später
genau eine Schicht ausgetauscht wird.** Eine konkrete DB-Architektur ohne
Anbieterentscheidung wäre ein „unnötiger Schritt" (Univ. §0) — und mit hoher
Wahrscheinlichkeit die falsche.

- **Betroffener Datenbestand (und nur dieser):**
  `%APPDATA%\TransparencyTool\maps\<language>.json` (`MapManager`,
  `TranslationMap`, `MapsPanel`, `MapCreateDialog`, `MapEditDialog`,
  `TranslationMapListPanel`) und die Kartenlisten (`CardListStore`,
  `CardEntry`, `CardListPanel`, `CardFolderDialog`).
  **Nicht betroffen:** Projekte, Szenen, Bilder, `AppSettings`. Insbesondere
  bleibt der **GameII-Format-Vertrag (§23) unberührt** — Szenen bleiben
  dateibasiert.
- **Ab jetzt [B]: kein direkter Datei-Zugriff auf Karten-/Listendaten aus der
  UI.** `MapsPanel`, `TranslationMapListPanel`, `CardListPanel` &Co. gehen
  **ausschließlich** über `MapManager` bzw. `CardListStore`. Wer heute in
  einem Panel eine Map-Datei liest oder schreibt, verlegt es dorthin —
  **das ist der ganze Migrationsaufwand**, den die spätere Umstellung spart.
- **Zwei Zugriffspunkte, nicht mehr [A]:** `MapManager` und `CardListStore`
  sind die **einzigen** Klassen, die wissen, *wo* diese Daten liegen. Sie
  bekommen (wenn die Anbindung kommt) je ein Interface mit zwei
  Implementierungen — lokal und remote. Kein Aufrufer ändert sich.
- **Signaturen jetzt schon aufruf-tauglich halten [B]:** Rückgaben sind
  vollständige Objekte oder Listen, **keine** `File`-Handles und keine
  Streams nach außen. Ein `getMapFile(...)`, das ein `File` an die UI gibt,
  ist die Stelle, die eine Remote-Quelle später unmöglich macht.
- **Was ausdrücklich noch NICHT gebaut wird [C], bis der Anbieter feststeht:**
  Netz-Schicht, Auth, Caching-/Offline-Strategie, Konfliktauflösung,
  Synchronisations-Zeitpunkte, asynchrone Signaturen. Die Entscheidung
  „lokal-first mit Sync" gegen „remote-first mit Cache" ist eine
  Produktentscheidung, keine Aufräumarbeit.
- **Wenn der Anbieter feststeht**, gilt Univ. §15: die Architektur bekommt
  eine **`service`-Schicht zwischen `core` und `io`**, und §33 wird durch die
  konkreten Regeln ersetzt. Erwartbar dann: Netzzugriff nie auf dem EDT
  (§24, `SwingWorker`), Fehler-/Offline-Fall sichtbar in der UI, Zugangsdaten
  nie im Quelltext und nie in `AppSettings`.
- **Bis dahin bleibt alles lokal und funktionsfähig.** Diese Regel verlangt
  **keine** Umstellung — nur, dass keine *neue* Direktzugriffs-Stelle
  entsteht.

---

## §32 Nicht anwendbar aus den GameLoop2-Guidelines

| GameLoop2 | Grund |
|---|---|
| §6 Double Buffering / `DoubleBufferedCanvas` / `GfxUtil.aa` | Swing puffert selbst → ersetzt durch §24 |
| §12 Socket-/Plug-Regeln | Keine Plugin-Sockets. Übertragbarer Kern („eine Deklaration pro Anschluss, Trefferradius nie als Literal") → §22 (Skalierhandles) |
| §13 Bewegungs-Geschwindigkeit / `stepSpeed()` | Kein GameLoop, keine Schrittweite |
| §15 Z-Feld / drei Modi (RADIAL/EDGE_BAND/MESH) | GameLoop2-Fachlogik. `PathLayer`/`Point3D` tragen zwar ein Z, aber nur als Datenfeld für den Export (§23) |
| §17 Render-Budget (Frame-Pflicht, FrameStats, Bedarfs-Redraw, F7/F8) | Kein GameLoop, keine Frame-Zeit → abgeschwächt zu §26 |
| §11 „F1 öffnet die Hilfe" | F1 ist belegt → `SHIFT+F1` (§25) |
| Web-Regeln (i18n, WCAG, DSGVO, No-Build, HTML/CSS/JS) | Univ. §15 |

---

## Anhang — Belege

Alle Zahlen und Befunde S1–S12 mit Fundstellen und Messmethode:
**`doc/GUIDELINES_VORSCHLAG_2026-07-30.md`**, Abschnitt 0.
Reihenfolge der Umsetzung mit Risiko- und Model-Empfehlung: dort Abschnitt 2.
Offene Fragen an den User (Q1–Q4): dort Abschnitt 3.
