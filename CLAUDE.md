# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ⚠ Verbindliche Guidelines — ZUERST LESEN

Vor jeder Aufgabe in dieser Reihenfolge:

1. **`../JAVA_GUIDELINES_UNIVERSAL.md`** — projektneutrale Standards für **alle**
   Java-Projekte im Workspace (§0–§15).
   *Randnotiz:* liegt seit 2026-07-31 auch als **byte-identische Lesekopie** in
   `doc/JAVA_GUIDELINES_UNIVERSAL.md` (ebenso
   `doc/PROMPT_Schwachstellen-Audit.md`). **Maßgeblich ist das Original im
   Workspace-Root** — es gilt gleichlautend für GameLoop2, SpriteAnimator,
   PathAnimator und JavaDemos. Ändern nur dort, danach neu kopieren;
   Verfahren in `doc/README.md`, Abschnitt 1.
2. **`doc/GUIDELINES.md`** — Projekt-Steckbrief (welche TT-Klasse welche Rolle
   besetzt) + projektspezifische §20–§33.
3. **`doc/Prompt_Handling.txt`** — Task-Workflow: `[BT]`/`[ST]`, PD-Datei,
   Risikoklassen `[A]`/`[B]`/`[C]`, Abschluss-Block.
4. **`doc/WEITERMACHEN_PROMPT.txt`** — aktueller Stand und offene Schritte.
   Einstiegspunkt nach `/clear`.

Wegweiser durch alle Dokumente: **`doc/README.md`**.
Belege und Ist-Analyse: `doc/GUIDELINES_VORSCHLAG_2026-07-30.md` (Befunde S1–S13).

**Leitprinzip:** Die Regeln gelten für **neuen und berührten** Code. Ein reines
Refactoring laufender Logik zur Regelkonformität ist ausdrücklich unerwünscht.

---

## Build & Run

**Kompilieren (aus dem Projekt-Root):**
```bash
javac -encoding UTF-8 -sourcepath src -d bin src/paint/*.java src/module-info.java
```
`-encoding UTF-8` ist **Pflicht** — die Quelldateien enthalten Unicode (Pfeile,
Symbole, Umlaute). Ohne die Option: „unmappable character".
**Erwartet: exit 0, 328 `.class`** (Stand 2026-07-30).

**Starten:**
```bash
java -cp bin --module-path bin -m TransparencyTool/paint.SelectiveAlphaEditor
```
Oder in Eclipse: Run As → Java Application auf `SelectiveAlphaEditor`.

**Java:** JavaSE-17 (`.classpath`), `module-info.java` deklariert Modul
`TransparencyTool` mit `requires java.desktop`.
Kein Build-Tool, keine Tests, keine externen Abhängigkeiten. Eclipse-Projekt:
Quellen → `src/` und `resources/`, Ausgabe → `bin/`.

**Deploy:** `bash src/paint/JAVA_EasyImageManipulator-Push.sh` →
`https://github.com/einsundnull/JAVA_EasyImageManipulator.git`

> **Quellbaum:** nur noch `paint` (103 Dateien) + `book` (3) +
> `module-info.java`. Am 2026-07-30 ausgelagert, weil ohne Bezug zu `paint`:
> `com.spriteanimator` → `../SpriteAnimator/`, `PathAnimator` →
> `../PathAnimator/`, die startbaren Prototypen (`Demo`, `DemoWorksheetEditor{,2}`,
> `AutoGrowingPillFieldDemo` + `SmartLabel`) → `../JavaDemos/`.
> Ersatzlos gelöscht: `AutoGrowingPillFieldDemo2`, `BookListPanelLegacy`,
> `BookPagesPanelLegacy`, `PageLayoutToolbarLegacy` und der veraltete
> `paint/`-Ordner im Projekt-Root.

> **KnowledgeMap:** `graphify-out/` ist eingecheckt — `GRAPH_REPORT.md` für den
> Überblick, `graph.html` im Browser, `graph.json` für Abfragen. Code-only
> (Scan-Root `src/`). **Vorsicht:** externe Typen wie `JPanel` liegen als viele
> Knoten vor; der Graph taugt für „wie hängt es zusammen", **nicht** für
> „ist das tot" — dafür Textsuche.

---

## Architektur

Swing-Werkzeug zum Bearbeiten von Bildern und Szenen. **Zwei unabhängige
Canvases** nebeneinander, vier Modi, ein Zweitfenster für die Vorschau.
Alles liegt im Package `paint` (103 Klassen, flach) plus `book` (3 Klassen).

### Das Hauptfenster ist ein Orchestrator, keine God-Klasse

`SelectiveAlphaEditor extends JFrame` hat **566 Zeilen** und enthält fast keine
Fachlogik. Es hält Felder, instanziiert **20 Controller** und delegiert in
Einzeilern:

```java
public void pushUndo()          { saveController.pushUndo(activeCanvasIndex); }
public void markDirty()         { layoutController.markDirty(activeCanvasIndex); }
void toggleSecondaryWindow()    { secWinController.toggleSecondaryWindow(); }
```

**Wer Fachlogik sucht, sucht sie im Controller — nicht hier.** Wer neue
Fachlogik schreibt, legt sie ebenfalls dort ab (`doc/GUIDELINES.md` §22).

### Zustand: pro Canvas, nicht global

`CanvasInstance` (`canvases[2]`, Zugriff über `ci()` / `ci(idx)`) hält **alles,
was es zweimal gibt**: `workingImage`, `originalImage`, `sourceFile`,
Undo-/Redo-Stacks, `activeElements`/`selectedElements`, Float-Zustand, Zoom,
Verzeichnisliste, `appMode`, `showGrid`, die eigenen UI-Komponenten
(`canvasPanel`, `scrollPane`, `viewportPanel`, `layeredPane`, `tileGallery`,
`scenesPanel`) sowie `fileCache`/`preloadCache` (beide `ConcurrentHashMap` —
Worker-Threads schreiben, der EDT liest).

Genuin global bleibt im Hauptfenster: Zwischenablage, `dirtyFiles`,
`showRuler`, `rulerUnit`, `projectManager`, Zweitfenster, Toolbars.

> **Ein neues Feld im Hauptfenster, das pro Canvas verschieden sein kann, ist
> ein Regelverstoß** (§30). Und: kein `canvases[0]` in Fachcode — eine feste `0`
> ist der Grund, warum Canvas II Funktionen verliert.

### Controller (Fachlogik)

| Controller | Rolle |
|---|---|
| `UIBuilder` | Baut Top-Bar, Center (Canvases, Ruler, Galerien), Bottom-Bar. Schreibt in `ed.feld` — die SAE-Felder bleiben maßgeblich |
| `AppLifecycleController` | Start (Settings laden) und Shutdown (Settings speichern) |
| `LayoutController` | Ruler-Layout, Nav-Buttons, Fokus-Rahmen, Panel-Sichtbarkeit, `markDirty`, `updateTitle/Status` |
| `FileLoadController` | Laden, Verzeichnis-Indizierung, Navigation, `fitToViewport`, `centerCanvas` |
| `SaveController` | Speichern, **Undo/Redo**, Alpha-Editor-Operationen, Floodfill |
| `ElementController` | Layer-Operationen: Rendern, Persistenz, Selektion, Element-Koordinaten |
| `ElementEditController` | Element-Edit-Modus: betreten, verlassen, übernehmen |
| `FloatSelectionController` | Schwebende Auswahl: commit/cancel, Handle-Rechtecke, Hit-Test, Rotations-Handle |
| `ClipboardController` | Copy/Cut/Paste, auch „außerhalb der Auswahl", System-Zwischenablage |
| `TransformController` | Flip H/V, Rotieren, Skalieren |
| `ZoomController` | Animierter Zoom, **`screenToImage()`** |
| `ModeController` | Alpha / Paint / Book / Scene umschalten, Element-Panel, Mode-Label |
| `ScenesController` | Szenen-Panel, Szenen laden (**inkl. GameII-Szenen**), Szene aus Drop erzeugen |
| `NewFileController` | Neue Bitmap / Buchseite, Canvas-Hintergrund-Dialog |
| `DropController` | Drag & Drop auf den Canvas, rechte Drop-Zone |
| `PreloadController` | Hover-Preload-Cache für flüssiges Blättern (SwingWorker) |
| `QuickOpenController` | Schnell-Öffnen zuletzt benutzter Projekte |
| `SecondaryWindowController` | Zweitfenster (F1–F7) |
| `BookController` | Buch-/Seiten-System, `%APPDATA%\TransparencyTool\books\` |
| `EditorDialogs` | Zentrale Dialog-Erzeugung |

**Panels kennen den Editor nicht.** Sie bekommen ein `Callbacks`-Interface
(`CanvasCallbacks`, `TileGalleryPanel.Callbacks`, `PaintToolbar.Callbacks`,
`ElementLayerPanel.Callbacks`, `TextToolbar.Callbacks`, `MapsPanel.Callbacks`,
`EditorDialogCallbacks`, `RulerCallbacks`), das eine `*CallbacksFactory`
verdrahtet. Neue Panels folgen dem — **kein `SelectiveAlphaEditor`-Feld in
einem Panel**.

### `CanvasPanel` ist die God-Klasse (3299 Zeilen)

`CanvasPanel extends JPanel` spricht ausschließlich über `CanvasCallbacks` mit
dem Rest und trägt: gesamte Maus-Eingabe, Hit-Testing, `paintComponent` samt
Overlays, Pan, Rechtsklick-Zoom, Gummiband-Multiselect, Element-Rotation,
Snap-Drag, aufgeschobenes Undo (`pendingUndo` — der Snapshot entsteht erst bei
echter Bewegung) **und einen vollständigen Text-Editor** (eigener Caret,
eigene Text-Undo-Stacks, Bounding-Box).

**Hier landet keine neue Fachlogik** (§22). Extraktion nur opportunistisch bei
Berührung, mit Landkarte aus dem Graphen.

### Modell: `Layer` ist unveränderlich

```
Layer (abstract)
├── ImageLayer   – gerasterter Pixelbereich
├── TextLayer    – Text, live aus Font-Einstellungen gerendert
├── PathLayer    – Pfad mit Kontrollpunkten (Point3D), optional mit Bild
└── SpriteLayer  – GameII-Sprite, hält rawLines() der Quelldatei
```

**Jede Mutation gibt eine neue Instanz zurück** (`withPosition`, `withBounds`,
`withHidden`, `withRotation`, … — 25 Methoden). Identität läuft über `id()`.

> Wer einen Layer ändert, muss ihn in **`activeElements` *und*
> `selectedElements`** ersetzen (Suche über `id()`). Nur eine Liste zu
> aktualisieren ist die typische Fehlerquelle — die Selektion zeigt dann auf
> eine veraltete Kopie. Dafür gibt es `replaceInLists(...)`.

### Undo/Redo

`ArrayDeque<BufferedImage>` pro Canvas, max. 50. **`pushUndo()` vor der
Änderung, `markDirty()` danach** — die Reihenfolge ist die Zusage (§29).
`PaintEngine` arbeitet destruktiv auf `workingImage`; deshalb ist `pushUndo()`
vor **jedem** `PaintEngine`-Aufruf Pflicht, es gibt keinen anderen Weg zurück.
Die schwebende Auswahl ist mitten im Zug **nicht** Teil des Bandes:
`cancelFloat()` ruft `doUndo()`.

### Zwei Koordinatensysteme

- **Image-Space** — Pixel in `workingImage`. Alles Persistente: `Layer`-Bounds,
  `selectedAreas`, `floatRect`, `PaintEngine`-Argumente, Szenendateien.
- **Canvas-/Screen-Space** — Image-Space × `zoom`; das liefert
  `MouseEvent.getPoint()`.

**`screenToImage()` ist der einzige Weg hinein, `toSx/toSy/toSw()` der einzige
hinaus.** Kein `* zoom` / `/ zoom` in Fachcode (§27). Ein drittes System —
**Prozent-Koordinaten** — gehört GameII und existiert nur in
`GameSceneReader/Writer`.

### `PaintEngine`

Stateless, nur statische Methoden auf einem `BufferedImage`, reiner
Image-Space: Stift, Radierer, Linie, Kreis, Rechteck, Floodfill, Pipette,
Crop/Paste, Flip, Rotate, Scale, `clearRegion`, `clearOutside`,
`clearPolygon`. Kein Zoom, kein `Component`, kein UI-Zustand — das hält sie
testbar.

### Schwebende Auswahl (MS-Paint-Stil)

Klick in eine Auswahl mit aktivem SELECT-Werkzeug **hebt** die Region in
`floatingImg`, löscht den Originalbereich und verfolgt sie in `floatRect`
(Image-Space). Acht Skalier-Handles plus Rotations-Handle; Ecken skalieren
proportional, Seiten eine Achse. Die Float-Interaktion wird **vor** der
Modus-/Werkzeug-Weiche geprüft und funktioniert daher in jedem Modus.
`commitFloat()` führt zusammen, `cancelFloat()` verwirft. CTRL+V erzeugt sofort
eine schwebende Auswahl — nichts landet auf dem Canvas, bis committet wird.

### Modi

`AppMode` (pro Canvas): `ALPHA_EDITOR` · `PAINT` · `BOOK` · `SCENE`.
Zweitfenster: `PreviewMode` (SNAPSHOT / LIVE_ALL / LIVE_ALL_EDIT),
`CanvasDisplayMode` (nur I / nur II / aktiver), `AlwaysOnTopMode`
(TO_FRONT / NORMAL / TO_BACKGROUND).

### Persistenz

| Klasse | Zuständig für |
|---|---|
| `SceneFileReader` / `SceneFileWriter` | TT-Szenen |
| `TextReader` / `TextWriter` | `TextLayer`-Konfiguration |
| `GameSceneReader` / `GameSceneWriter` | **GameII-Szenen** (Prozent-Koordinaten) |
| `ToolLegacySceneReader` | Altes GameII-Layout — **nur lesen** |
| `SceneSerializer`, `SceneLocator`, `ProjectManager` | Szenen-/Projektverwaltung |
| `AppSettings` | globale Einstellungen (Singleton) |
| `MapManager`, `CardListStore`, `book.JsonStorage`, `PageLayoutManifest` | Maps, Karten, Bücher, Seitenlayout |

**`AppPaths` ist die Pfad-Zentrale** (`%APPDATA%\TransparencyTool\`, Fallback
`user.home`) und liefert: `projects/`, `projects/<P>/scenes/`, `settings/`,
`settings/lastProjects/`, `assets/`, `maps/`. Neue Pfade gehören dorthin —
kein `%APPDATA%`-Lesen und kein Pfad-Zusammenbau woanders.

Ebenfalls über `AppPaths`: `getBooksDir()` (`books/`) und
`getCardFoldersDir()` (`settings/cardfolders/`). Beide waren bis 2026-07-30
Ausreißer — `BookController` baute den Pfad hartkodiert aus `user.home` und
las `%APPDATA%` nicht (brach bei umgeleitetem Profil), `CardListStore`
beschrieb im Javadoc einen Pfad ohne `settings/`.

> `books/` und `settings/cardfolders/` entstehen **erst beim ersten Zugriff**,
> nicht beim Start — alle anderen Verzeichnisse legt `AppPaths` sofort an.

> **Das Szenenformat ist ein Vertrag mit GameII** (§23), kein internes Format.
> `#Sektion:` / `-key: value`, UTF-8, Szene = Verzeichnis, erster
> `#Images:`-Eintrag ist der Hintergrund. Spezifikation:
> `doc/SCENE_FORMAT_READ_WRITE.md`. Wer es ändert, ändert zuerst das Dokument.
> `GameSceneWriter` übernimmt **unbekannte Abschnitte unverändert**
> (`SpriteLayer.rawLines()`) und aktualisiert nur `#INIT_POSITION`/`#SIZE` —
> ein Writer, der fremde Abschnitte wegwirft, zerstört Animationen und Links.

`AppSettings` liegt in `settings/default.txt` und enthält **JSON** trotz
`.txt`-Endung (bewusst, damit bestehende Installationen ihre Einstellungen
behalten). Der Parser ist zeilenbasiert: **ein Key pro Zeile**. Format:
`doc/Schema_AppSettings.txt`.

### Threading

Alle UI-Änderungen auf dem EDT. Lange Arbeit (Bilder laden, Verzeichnisse
scannen, Thumbnails, Startdialog) läuft in `SwingWorker` — so machen es
`TileGalleryPanel`, `PreloadController`, `ScenesController`, `StartupDialog`,
`ElementController`, `GalleryCallbacksFactory`. `invokeLater` (84 Stellen) ist
für „nach dem aktuellen Event", **nicht** für Nebenläufigkeit. Zeitgesteuertes
über `javax.swing.Timer`, nicht `java.util.Timer`.

### Farben

### Farben, Fonts, Maße

Zwei Klassen, **überschneidungsfrei**: `AppColors` = Farben (591 Verwendungen),
`AppTheme` = Fonts, Abstände, Radien, Stroke-Breiten, Standardgrößen, plus
`alpha(Color,int)` und `pad(...)`. Eine Farbe gehört nie in `AppTheme`, ein
Font nie in `AppColors`.

Im Bestand stehen noch 454 `new Color(`, 162 `new Font(`, 110
`new BasicStroke(` — `AppTheme` ist erst seit 2026-07-30 da und **noch nirgends
benutzt**. In berührtem Code gilt ab jetzt: **kein neues Literal, fehlendes
Token anlegen** (§21).
Ausnahme: Farben, die der **User** wählt (Primär-/Sekundärfarbe,
Canvas-Schachbrett, Kartenfarben) sind keine Tokens — sie gehören in
`AppSettings`.

### Tastatur

`KeyboardShortcutManager` verdrahtet zwei Wege: die `InputMap`/`ActionMap` des
Hauptfensters (`WHEN_IN_FOCUSED_WINDOW`) und einen globalen
`KeyEventDispatcher` für F1–F7, ALT+T, ALT+P.

**F1–F7 sind vollständig vergeben** (F1 Zweitfenster, F2 Preview-Modus,
F3 Snapshot, F4 Vollbild, F5 Always-on-top, F6 auf Canvas anwenden,
F7 Canvas-Anzeigemodus). Eine Hilfe-Taste wäre deshalb **SHIFT+F1** (§25).

**`KeyBindings.ALL` ist die Registry** (53 Einträge, sechs Scopes) —
`KeyBindingsDialog` zeigt sie über **Umschalt+F1** oder den Knopf „?“ rechts
in der oberen Leiste. **Neue Taste oder Geste → zuerst Registry-Eintrag, dann
Handler** (§25); der Dialog enthält keinen eigenen Text.

> Zwei Gesten, die man nicht errät und die vorher nirgends standen:
> **`Strg`+Rad auf einem Text-Layer** ändert die Schriftgröße statt zu zoomen,
> **`Alt+T` halten + Rad** ändert die Deckkraft des Bild-Layers.
> Während der Textbearbeitung sind `Strg+A/C/X/V/Z`, `Esc`, `Enter` und `Entf`
> auf den Text umgelenkt (Scope `TEXT`).

---

## Altlasten (geführt, kein Auftrag)

Vollständige Liste mit Risiko: `doc/WEITERMACHEN_PROMPT.txt`.

- ~~Tote/veraltete Dateien~~ — **erledigt 2026-07-30** (2540 Zeilen raus,
  siehe Quellbaum-Hinweis oben). `ToolLegacySceneReader` ist **keine**
  Altlast — Legacy-Lesen ist gewollt (§23).
- **`ImageIO.write` in 9 Dateien** statt in der `io`-Schicht.
- **5 handgeschriebene JSON-Serializer** ohne gemeinsamen Writer.
- **`AppSettings` liest/schreibt ohne Encoding** (Plattform statt UTF-8) —
  betrifft Umlaute in `recentFiles`/`recentProjects`.
- **`TextWriter` schreibt jede Datei zweimal** (Original + `src/`→`bin/`-Kopie).
- **Keine Fenster-Basisklasse:** 12 Klassen erben direkt von
  `JFrame`/`JDialog`/`JWindow`; `JOptionPane` in 16 Dateien. `JOptionPane` ist
  Altlast, **kein Vorbild** — in neuem Code nicht verwenden (§20).
- **`toggleVis` ruft `pushUndo()` pro Element** statt pro Aktion.
- **`#Paths:`** wird in Szenen referenziert, aber nicht gelesen;
  `GameSceneWriter` schreibt `PathLayer` nicht.
