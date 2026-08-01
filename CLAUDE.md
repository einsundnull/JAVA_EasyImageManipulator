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
   besetzt) + projektspezifische §20–§34.
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
**Erwartet: exit 0, 357 `.class`** in einem **leeren** Zielverzeichnis
(nachgemessen 2026-08-01 — 335 vor `PaintIcons`, das mit seinen inneren Typen
neun mitbrachte; 344 vor den Werkzeug-Kürzeln, die drei mitbrachten: den
`record ToolKey` und zwei anonyme `AbstractAction`; 347 vor den
Kontextmenüs, deren vier neue Dateien mit ihren inneren Typen zehn
mitbrachten).
Ein von Eclipse mitgepflegtes `bin/` enthält
zwei mehr — Eclipse übersetzt alle drei `book`-Klassen, der `javac`-Befehl nur
die von `paint` aus erreichbare. Wer eine Zahl notiert, schreibt dazu,
**womit** gemessen wurde.

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

> **Quellbaum:** nur noch `paint` (111 Dateien) + `book` (3) +
> `module-info.java`. (105 → 106 am 2026-07-31: `TextToSpeech` gelöscht,
> `ImageFileWriter` angelegt; 106 → 107 am 2026-08-01: `PaintIcons`;
> 107 → 111 am 2026-08-01: `ContextMenu`, `FileActionsController`,
> `ConfirmDialog`, `TextInputDialog`.) Am 2026-07-30 ausgelagert, weil ohne Bezug zu `paint`:
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
Alles liegt im Package `paint` (107 Klassen, flach) plus `book` (3 Klassen).

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

> **Die Undo-Stacks hängen an der Datei, nicht am Canvas** — `FileLoadController`
> stellt sie beim Blättern aus `c.fileCache` (`CanvasFileState`) wieder her.
> Wer ein Sättigungs-Flag oder einen Speicher-Marker am `CanvasInstance`
> anbringt, desynchronisiert ihn beim Blättern.
>
> **Deshalb hält `FileLoadController.trimInactiveHistory(idx)` den Cache
> klein** (B01, 2026-07-31): nach jedem Laden behalten alle **nicht**
> angezeigten Dateien nur noch `INACTIVE_UNDO_KEEP = 5` Undo-Schritte und
> keinen Redo-Stack; ihr Bild bleibt. Die **aktive** Datei ist per Vergleich
> mit `c.sourceFile` ausgenommen und behält ihre vollen 50.
> Gemessen: ein Eintrag mit vollem Stack kostet beim 6-MB-Bild **606 MB**,
> 96 bearbeitete Dateien sprengten mit 5,57 GB den Heap.
> **Der Verlust ist gewollt** — wer zu einer Datei zurückkehrt, hat dort 5
> statt 50 Schritte. Wer das „verbessert", holt den `OutOfMemoryError`
> zurück. Und: `undoStack` ist ein Stack, `push()` legt **vorne** ab — der
> älteste Schritt ist `pollLast()`, nicht `pollFirst()`.
>
> **Undo/Redo *setzt* den Änderungsmarker, es löscht ihn nicht** (D03,
> 2026-07-31). Der Fehlschluss „Stack leer ⇒ gespeichert" ist raus. Die
> gewollte Kehrseite: Änderung + sofort `Strg+Z` behält den Marker, das
> Schließen fragt trotzdem nach. Eine Rückfrage zu viel ist der harmlose
> Fehler — die Alternative war stiller Datenverlust. **Nicht „korrigieren".**

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
| `ImageLoader` / `ImageFileWriter` | **Bilder** — laden bzw. schreiben |
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

> **Bilder werden ausschließlich über `ImageFileWriter.writePng(...)`
> geschrieben** (§34, seit 2026-07-31). Es schreibt **atomar** — erst in eine
> `.part`-Nachbardatei, dann `Files.move(ATOMIC_MOVE)` — und **prüft den
> Rückgabewert** von `ImageIO.write`, der bei fehlendem Writer `false` ist,
> ohne zu werfen. `ImageIO.write` steht nirgends sonst im Quellbaum, auch
> nicht für Temp-Dateien. Eine liegengebliebene `.part`-Datei bedeutet: dort
> ist ein Schreibvorgang abgebrochen, das Original ist heil.

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

### Vorlesen (TTS)

**`CardTtsPlayer` ist der einzige Weg** — genutzt von `CardListPanel`,
`TranslationMapListPanel` und (seit 2026-07-31) `MapsPanel`. Der Text wird in
eine temporäre UTF-8-`.ps1` geschrieben und dort in einem einfach gequoteten
Here-String (`@'…'@`) übergeben; er ist **nie Teil der Kommandozeile**. Genau
das war Befund **I01**: die gelöschte Klasse `TextToSpeech` interpolierte
Kartentext in eine doppelt gequotete PowerShell-Zeichenkette, in der `$(…)`
ausgewertet wird. **Keine zweite Vorlese-Implementierung anlegen.**

### Farben, Fonts, Maße

Zwei Klassen, **überschneidungsfrei**: `AppColors` = Farben (704
Verwendungen), `AppTheme` = Fonts, Abstände, Radien, Stroke-Breiten,
Standardgrößen, plus `alpha(Color,int)` und `pad(...)` (154 Verwendungen in
15 Dateien). Eine Farbe gehört nie in `AppTheme`, ein Font nie in
`AppColors`.

**Zwölf Dateien sind migriert** (Stand 2026-07-31, Schritt [6] inhaltlich
abgeschlossen): `UIComponentFactory` · `SelectiveAlphaEditor` · `UIBuilder` ·
`TextToolbar` · `BaseSidebarPanel` · `NewImageDialog` · `TileGalleryPanel` ·
`PageLayoutToolbar` · `MapsPanel` · `TranslationMapListPanel` · `CanvasPanel` ·
`ElementLayerPanel`. Im Bestand stehen außerhalb der Token-Klassen noch
**125 `new Color(`** (vorher 259), **62 `new Font(`** (135) und
**34 `new BasicStroke(`** (68) — nachgemessen, jeweils rund die Hälfte weniger.

### Werkzeug-Symbole: `PaintIcons` (seit 2026-08-01)

**Die Symbole der Paint-Leiste werden gezeichnet, nicht getippt.**
`PaintIcons` liefert `javax.swing.Icon`-Objekte, die ihre Form mit
`Graphics2D` auf einem 24×24-Entwurfsraster beschreiben; `PaintToolbar` und
`WandPanel` setzen sie per `setIcon(...)`, die **Beschriftung steht darunter**.

> **Warum gezeichnet:** gemessen mit `Font.canDisplayUpTo()` über 59
> Kandidaten in sechs Familien — `SansSerif`/`Dialog` zeigen 58 davon, aber
> **🪣 U+1FAA3 (Fülleimer) in keiner einzigen**, auch nicht in „Segoe UI
> Emoji". Ein Glyph-Tausch konnte das namentlich gewünschte Symbol nicht
> liefern. Und **„Segoe UI" kennt von den 59 Zeichen nur sechs** — die
> Knopfschrift darf nie auf die UI-Schrift umgestellt werden.

Die **elf Zauberstab-/Schere-Varianten sind eine Familie**: gemeinsame
Grundform links oben, Varianten-Abzeichen rechts unten (`badgeZone`, eigenes
Koordinatenfeld 0…10). Vorher zeigten elf Knöpfe im `WandPanel`-Raster
zusammen nur **vier** Zeichen — sechsmal „⚡", dreimal „✂", je einmal „◠"/„◡".

> **Nicht „vereinheitlichen":** Ring-außen/innen sind **eckig**, AA-außen/innen
> **rund**. Im ersten Entwurf waren alle vier eckig und sahen zu viert wie
> dasselbe blaue Quadrat aus. Es braucht **zwei Umrisse**, nicht zwei kleine
> Unterschiede. Ebenso bleibt die dunkle Scheibe unter dem Abzeichen — ohne
> sie verschwimmt es auf dem blauen Hintergrund des *ausgewählten* Knopfes.

**Zeichen behalten** (Univ. §0, sie sind eindeutig): ↩ ↪ ↔ ↕ ↺ ↻ ⊞ ✂. Damit
sie trotzdem eine Beschriftung tragen können, gibt es `PaintIcons.glyph(...)`
— ein Knopf kann nur *einen* Text haben, also muss auch ein Zeichen als Icon
vorliegen.

Fülleimer, Pipette und die beiden Farbradierer zeigen die **aktuelle
Malfarbe**. Sie lesen den Live-Zustand der Leiste; deshalb laufen alle
Farbänderungen über **einen** Trichter, `PaintToolbar.fireColorChanged()`
(vorher stand `cb.onColorChanged(...)` an sechs Stellen).

> **Die Tooltips versprachen einmal Tastenkürzel, die es nicht gab** —
> „Stift (P)", „Fülleimer (F)" usw., ohne Registry und ohne Handler, während
> `R` in Wahrheit *90° drehen* auslöste. Die Klammerangaben wurden am
> 2026-08-01 entfernt und **noch am selben Tag durch echte Kürzel ersetzt**
> (Abschnitt „Tastatur"). Das Kürzel im Tooltip stammt seither aus
> `KeyBindings.comboFor(tool)` — **abgeleitet, nicht getippt**.

Spezifikation und Befunde: `doc/Schema_PaintToolbar_Icons.txt`,
Umsetzung `doc/progress_2026-08-01_paint-icons-umsetzung.txt`.

Der Rest ist bewusst **nicht** migriert: `PaintToolbar`, `ColorPickerPopup`,
`PaintEngine` und die Kartenfarben führen Farbe als **Inhalt**, nicht als
Oberfläche; der Rest ist Streubesitz mit 1–5 Fundstellen je Datei. In
berührtem Code gilt trotzdem: **kein neues Literal, fehlendes Token anlegen**
(§21).
Ausnahme: Farben, die der **User** wählt (Primär-/Sekundärfarbe,
Canvas-Schachbrett, Kartenfarben) sind keine Tokens — sie gehören in
`AppSettings`.

### Kontextmenüs: `ContextMenu` (seit 2026-08-01)

**Rechtsklick auf eine Kachel öffnet ein Menü** — in beiden Bildlisten (12
Einträge), im Layer-Panel (11) sowie in Szenen-, Seiten- und Bücherliste (3).
`ContextMenu` ist der Baukasten: Aussehen, Auslöser, Häkchen. **Die
Fachlogik steht in `FileActionsController`** bzw. hinter den vorhandenen
`ElementLayerPanel.Callbacks` — das Panel kennt keine Dateilogik (§22).

> **Das Panel entscheidet nicht, was im Menü steht.** `TileGalleryPanel`
> reicht ein leeres Menü an `Callbacks.onContextMenu(file, menu)`; **was
> darin landet, füllt der Verdrahter.** Genau deshalb können Bildliste,
> Szenenliste und Seitenliste *dasselbe* Panel sein und trotzdem
> verschiedene Menüs zeigen — ohne ein „welche Art Liste bin ich"-Feld.

> **Das Menü wird bei jedem Rechtsklick neu gebaut.** Nur so stammen „grau"
> und die Häkchen zwangsläufig aus dem Live-Zustand und können nicht
> desynchronisieren (Univ. §12). Ein zwischengespeichertes `JPopupMenu` wäre
> der nächste Kandidat für ein lügendes Häkchen.

> **Der Auslöser prüft `isPopupTrigger()` in `mousePressed` *und*
> `mouseReleased`** — unter Windows meldet erst das Loslassen den Trigger.
> Und er hat einen **Zieh-Wächter** (5 px): Rechts-Ziehen in den
> Seitenleisten ist die registrierte Geste „Datei in eine andere Liste
> kopieren" (§25); nach einer Kopier-Bewegung darf kein Menü aufgehen.

> **Es gibt keine Kürzel-Spalte, und das ist eine Entscheidung.** Der erste
> Entwurf sah rechts „Strg+C", „Entf", „F2" vor — diese Tasten bedeuten im
> Hauptfenster aber etwas anderes (Bildauswahl als Layer kopieren;
> Auswahl/Layer löschen; Vorschau-Modus). Ein Kürzel neben „Datei kopieren"
> hätte eine Taste versprochen, die etwas anderes tut — **derselbe Fehler,
> der am selben Tag den Werkzeug-Kürzel-Task ausgelöst hat.** Die *Geste*
> steht in `KeyBindings` (Scope `MOUSE_UI`), das Kürzel nirgends.

> **Einträge werden deaktiviert, nicht versteckt** — ein Menü, das je nach
> Lage die Höhe wechselt, macht die übrigen Einträge unauffindbar. Ausnahme:
> was es *gar nicht gibt*, fehlt ganz. Deshalb trägt das Layer-Menü **kein**
> „Umbenennen": `Layer.displayName()` ist abgeleitet, es gibt kein
> Namensfeld. Ein echter Layer-Name berührt das Modell (§29) **und** den
> GameII-Vertrag (§23) und ist ein eigener Task.

> **Umbenennen und Löschen sind bei ungespeicherten Änderungen grau.**
> Umbenennen zwänge zum Neuladen von der Platte, Löschen nähme die Vorlage
> weg. **„Speichern unter …" schreibt den bearbeiteten Stand**, wenn die
> Datei gerade offen ist (über `ImageFileWriter`, §34) — sonst kopiert es
> byteweise, damit ein JPG ein JPG bleibt. **Gelöscht wird in den
> Papierkorb**, mit Rückfrage: für Dateien gibt es kein Undo, und für Layer
> ebenso wenig (Befund D01).

**`ConfirmDialog` (Ja/Nein) und `TextInputDialog` (Texteingabe)** sind im
selben Schritt entstanden — ohne sie hätte „Umbenennen" wieder ein
`JOptionPane` gebraucht. Genau davor warnt §20. Die drei *bestehenden*
`JOptionPane`-Umbenennungen (Seite, Buch, Szene) bleiben unangetastet: sie
funktionieren, ein Umbau wäre ein unnötiger Schritt.

> **Kein Kontextmenü auf dem Canvas** (Entscheidung des Users, 2026-08-01).
> Dort ist die rechte Taste vergeben: `Strg`+Rechts zoomt, Rechts malt mit
> der Sekundärfarbe bzw. zieht mit Einrasten (`CanvasPanel:336/368`).
> `CanvasPanel` ist von dieser Aufgabe **nicht** angefasst worden.

Spezifikation und Befunde: `doc/Schema_Kontextmenue.txt` (Abschnitt 8: was
die Umsetzung am Entwurf geändert hat), Umsetzung
`doc/progress_2026-08-01_kontextmenues-umsetzung.txt`.

### Tastatur

`KeyboardShortcutManager` verdrahtet zwei Wege: die `InputMap`/`ActionMap` des
Hauptfensters (`WHEN_IN_FOCUSED_WINDOW`) und einen globalen
`KeyEventDispatcher` für F1–F7, ALT+T, ALT+P.

**F1–F7 sind vollständig vergeben** (F1 Zweitfenster, F2 Preview-Modus,
F3 Snapshot, F4 Vollbild, F5 Always-on-top, F6 auf Canvas anwenden,
F7 Canvas-Anzeigemodus). Die Hilfe-Taste ist deshalb **Umschalt+F1** (§25) —
F1 wird **nicht** freigeräumt, die Zweitfenster-Belegung ist eingeübt.
Die Umschalt+F1-Abfrage steht im Dispatcher **vor** der F1-Abfrage; wer sie
verschiebt, macht die Hilfe unerreichbar.

**`KeyBindings.ALL` ist die Registry** (81 Einträge, sieben Scopes) —
`KeyBindingsDialog` zeigt sie über **Umschalt+F1** oder den Knopf „?“ rechts
in der oberen Leiste. **Neue Taste oder Geste → zuerst Registry-Eintrag, dann
Handler** (§25); der Dialog enthält keinen eigenen Text.

**Werkzeug-Kürzel (seit 2026-08-01):** P Stift · F Füllen · L Linie ·
E Ellipse · V Rechteck · G Radierer · I Pipette · A Auswahl · T Text ·
B Pfad · W Wischen; `Umschalt` macht die Variante (`Umschalt+G` radiert mit
der Sekundärfarbe, `Umschalt+B` ist der Freihand-Pfad, `Strg+G` der
Farbtausch). **Z** blendet das Zauberstab-Raster ein, dessen elf Werkzeuge
auf **1–9** liegen (`Umschalt+5`/`Umschalt+6` für die Innen-Varianten) — in
genau der Reihenfolge, in der die Knöpfe im Raster stehen.

> **Die Belegung steht vollständig in `KeyBindings.TOOL_KEYS`** — einer
> Tabelle mit drei Verbrauchern: den Zeilen des Hilfe-Dialogs, der
> Verdrahtung in `KeyboardShortcutManager.setupToolKeys` und dem Kürzel im
> Tooltip (`KeyBindings.comboFor`). **Nirgends wird eine Taste ein zweites
> Mal getippt.** Genau das war der Fehler davor: handgepflegte
> Klammer-Kürzel im Tooltip, die kein Handler bediente.
>
> **`R` ist kein Werkzeug-Kürzel** — es dreht das Bild. Deshalb trägt das
> Rechteck `V` („Viereck"). Wer das angleicht, nimmt dem Drehen seine Taste.
>
> **Die Taste wählt, sie schaltet nicht ab.** Der Knopf schaltet beim zweiten
> Klick auf „kein Werkzeug"; bei einer Taste sähe dasselbe wie „nichts
> passiert" aus.

> **Einfache Buchstaben und die Textbearbeitung beißen sich — das war ein
> vorhandener Fehler.** Belegungen der `InputMap WHEN_IN_FOCUSED_WINDOW`
> feuern auf `KEY_PRESSED`, die Texteingabe des `CanvasPanel` nimmt Zeichen
> aber erst in `keyTyped()` entgegen. Ein „r" im Text drehte deshalb zugleich
> das Bild um 90 Grad (am 2026-08-01 mit einem Wegwerf-Programm
> nachgewiesen). Der Wachposten `KeyboardShortcutManager.toolKeysActive()`
> fragt jetzt **an einer Stelle** `CanvasPanel.isEditingText()` und die
> Sichtbarkeit der Mal-Leiste ab — keine Meldepflicht je Handler (Univ. §13).
> `R`, `Umschalt+R` und `Umschalt+V` hängen mit drin.

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
- ~~`ImageIO.write` in 9 Dateien~~ — **erledigt 2026-07-31** als F02+F03:
  alle 21 Aufrufe laufen über `ImageFileWriter` (§34).
- **5 handgeschriebene JSON-Serializer** ohne gemeinsamen Writer.
- **`AppSettings` liest/schreibt ohne Encoding** (Plattform statt UTF-8) —
  betrifft Umlaute in `recentFiles`/`recentProjects`.
- **`TextWriter` schreibt jede Datei zweimal** (Original + `src/`→`bin/`-Kopie).
- **Keine Fenster-Basisklasse:** 12 Klassen erben direkt von
  `JFrame`/`JDialog`/`JWindow`; `JOptionPane` in 16 Dateien. `JOptionPane` ist
  Altlast, **kein Vorbild** — in neuem Code nicht verwenden (§20).
- ~~`toggleVis` ruft `pushUndo()` pro Element~~ — **erledigt 2026-07-31** als
  D02. Gegenprobe über alle 48 `pushUndo`-Vorkommen: es war die **einzige**
  solche Stelle, die Altlast ist damit geschlossen.
- **Text-Persistenz schreibt nicht atomar:** `AppSettings`, `ProjectManager`,
  `MapManager`, `CardListStore`, `book.JsonStorage`, `SceneFileWriter`,
  `TextWriter`. Bilder sind seit F02 versorgt (§34), Text nicht — ein
  `TextFileWriter` nach demselben Vorbild wäre der nächste logische Schritt,
  ist aber **nicht beauftragt** und gehört mit der Kodierungsfrage zusammen
  geplant, sonst wird dieselbe Datei zweimal angefasst.
- **`GameSceneReader` liest ISO-8859-1, `GameSceneWriter` schreibt UTF-8**
  (Befund G01) — der Round-Trip beschädigt Umlaute in einer **Vertragsdatei**
  (§23). `[C]`: eine Umstellung ändert, wie bereits geschriebene Dateien
  gelesen werden.
- **`#Paths:`** wird in Szenen referenziert, aber nicht gelesen;
  `GameSceneWriter` schreibt `PathLayer` nicht.
