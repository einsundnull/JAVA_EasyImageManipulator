# Guidelines-Vorschlag — TransparencyTool

> Stand 2026-07-30. **Dies ist die ANALYSE-Grundlage, keine Regel.** Die
> daraus abgeleiteten Regeln stehen in `doc/GUIDELINES.md`
> (projektspezifisch) und `../JAVA_GUIDELINES_UNIVERSAL.md` (projektneutral).
>
> Grundlage: Ist-Analyse von `src/` (129 `.java`, ca. 39 000 Zeilen) +
> Übertragung der GameLoop2-Guidelines
> (`GameLoop2/src/main/doc/GUIDELINES.md`, `Prompt_Handling.txt`).
>
> Leitgedanke aus `Prompt_Handling.txt`: **Unnötige Schritte nicht
> ausführen.** Alles unten ist mit Ist-Zustand + Risiko markiert:
> **[A]** großes ROI · **[B]** 100 % sicher · **[C]** größeres Risiko.

---

## 0. Ist-Zustand

### 0.1 Grunddaten

| | |
|---|---|
| UI-Toolkit | **Swing** (GameLoop2: AWT) — andere Basisklassen, EDT-Regeln, `paintComponent` |
| Packages | `paint` (110 Klassen, flach) · `com.spriteanimator{,.ui,.model,.engine,.export}` (7) · `PathAnimator` (1) · `book` (3) · `module-info.java` |
| Einstiegspunkt | `paint.SelectiveAlphaEditor` |
| Größte Dateien | **CanvasPanel 3299** · PathEditor 1819 · PaintEngine 1239 · TileGalleryPanel 1042 · PaintToolbar 931 · TranslationMapListPanel 927 · ElementLayerPanel 918 · UIBuilder 873 |
| Build | `javac -encoding UTF-8 -sourcepath src -d bin src/paint/*.java src/module-info.java` |
| Doku | 13 lose `.md` im Root, 11 lose `.txt` in `src/paint/` — **kein `doc/`** (bis heute) |
| Graphify | **kein `graphify-out/`** |

### 0.2 Vorhandene, gelebte Muster (das Fundament)

Diese Muster sind **stärker als in GameLoop2** und werden festgeschrieben,
nicht ersetzt:

1. **Controller-Zerlegung (18 `*Controller`).** `SelectiveAlphaEditor` ist von
   ehemals ~1900 Zeilen auf **566** geschrumpft und ist heute im Wesentlichen
   Orchestrator: 20 Controller-Felder + Delegations-Einzeiler
   (`pushUndo() { saveController.pushUndo(activeCanvasIndex); }`).
   **GameLoop2 hat dieses Ziel für `GameWindow` erst als Vorhaben** — hier ist
   es weitgehend erreicht. Das ist der Referenzfall.
2. **Callbacks-Interfaces + `*CallbacksFactory` (5).** Panels kennen den
   Editor nicht, sondern ein `Callbacks`-Interface
   (`CanvasCallbacks` 159 Z., `EditorDialogCallbacks`, `PaintToolbar.Callbacks`
   …); die Factory verdrahtet. Saubere Umkehr der Abhängigkeit.
3. **Basisklassen:** `BaseSidebarPanel` (abstract, **6 Unterklassen**,
   trägt Header, Dark-Scrollpane, Rechtsklick-Drag-to-Copy, `refresh()`) ·
   `CardListPanel` (abstract, 2 Unterklassen) · `Layer` (abstract).
4. **`Layer` als Wert-Objekt.** `ImageLayer`/`TextLayer`/`PathLayer`/
   `SpriteLayer`; **jede** Mutation gibt eine neue Instanz zurück
   (25 `with*`-Methoden). Identität über `id()`. Das ist die tragende
   Korrektheitszusage des Undo-Systems.
5. **Token-Quelle `AppColors`** (23 Farben, `final`, privater Ctor,
   **591 Verwendungen**).
6. **Persistenz-Paare** im GameLoop2-Format: `SceneFileReader/Writer` ·
   `TextReader/Writer` · `GameSceneReader/Writer` · `ToolLegacySceneReader`.
7. **Pfad-Zentrale `AppPaths`** — alle Anwendungspfade unter
   `%APPDATA%\TransparencyTool\`. Genau das Muster, das GameLoop2 §14 fordert.
8. **Per-Canvas-Zustand `CanvasInstance`** (ca. 40 Felder) statt globaler
   Editor-Felder; `canvases[2]` + `ci()`/`ci(idx)`.

### 0.3 Schwachstellen (was die Regeln adressieren müssen)

| # | Befund | Zahl | Risiko |
|---|---|---|---|
| S1 | **Keine Fenster-Basisklasse.** 12 Klassen erben direkt von `JFrame`/`JDialog`/`JWindow`; jede baut Titelbar/ESC/Größe selbst. `UIComponentFactory.createBaseDialog()` ist eine **Factory**, keine Basisklasse — sie teilt das Aussehen, nicht das Verhalten. | 12 | [A] |
| S2 | **`JOptionPane` in 16 Dateien** (~100 Aufrufe, Spitze: `ElementLayerCallbacksFactory` 11, `ToolBar` 11, `NewFolderDialog` 9). Hellgrauer System-Look mitten in einer dunklen App; kein einheitlicher Bestätigungsweg. | ~100 | [A/C] |
| S3 | **Token-Quelle unvollständig → 454 `new Color(`, 162 `new Font(`, 110 `new BasicStroke(`.** `AppColors` hat **keine Fonts, Abstände, Radien, Strokes**. Deshalb steht `new Font("SansSerif", Font.PLAIN, 12)` überall — allein 7× in `UIComponentFactory`, dem StyleGuide-Anker. Spitzen: `ElementLayerPanel` 64 Farben, `TranslationMapListPanel` 18 Fonts. | 726 | [A / Bereinigung C] |
| S4 | **`CanvasPanel` = neue God-Klasse, 3299 Zeilen** (Maus-Input + Hit-Test + `paintComponent` + Overlays + Handles + Float + Pfad-Bearbeitung). Die Controller-Zerlegung hat den Editor entlastet, **nicht den Canvas**. | 3299 | [A-Regel / [C] Extraktion |
| S5 | **`ImageIO.write` in 9 Dateien** (`SaveController` 6×, `GalleryCallbacksFactory` 3×, `ScenesController`, `NewFileController`, `ElementController`, `SceneImageAdapter`, `TileGalleryPanel`, `BookController`, `SceneSerializer`) → Bild-Persistenz ist **nicht** in der `io`-Schicht. Ebenso `new FileWriter` in `ProjectManager`, `MapManager`, `LastProjectsManager`, `CardListStore`, `AppSettings` — 5 handgeschriebene JSON-Serializer ohne gemeinsamen Writer. | 9 + 5 | [B/C] |
| S6 | **Shortcut-Register existiert nicht.** `KeyboardShortcutManager` (322 Z.) ist ein guter *Aufbau*-Ort (InputMap/ActionMap + globaler `KeyEventDispatcher`), aber **keine Registry**: die Beschreibung steckt nur im Code. `src/paint/Shortcut Table.txt` ist handgepflegt und **schon veraltet** — dort fehlen F1–F7, ALT+T, ALT+P, R/SHIFT+R, CTRL+ALT+S, CTRL+SHIFT+S, SHIFT+ALT+A. **Kein Hilfe-Dialog.** | — | [B] |
| S7 | **`F1` ist belegt** (`toggleSecondaryWindow`), F1–F7 komplett vergeben. Die GameLoop2-Regel „F1 = Hilfe" ist **nicht übertragbar**. | — | [B] |
| S8 | **`AppSettings`: Javadoc sagt `settings.json`, geschrieben wird `settings/default.txt`** — mit JSON-Inhalt. Format und Dateiname widersprechen sich, das Javadoc widerspricht beiden. | — | [B] |
| S9 | **`CLAUDE.md` ist überholt.** Beschreibt `SelectiveAlphaEditor` als „~1900 Zeilen, owns all state" (ist 566) und `CanvasPanel`/`HRulerPanel`/`VRulerPanel` als *inner classes* (sind eigene Dateien). Kein Wort über die 18 Controller, `CanvasInstance`, `Layer`, `BaseSidebarPanel`, Szenen, Books, Maps, Cards, TTS. Wer das liest, arbeitet am falschen Modell. | — | [A/B] |
| S10 | **Altlasten im Quellbaum:** `Demo.java`, `DemoWorksheetEditor{,2}.java`, `AutoGrowingPillFieldDemo{,2}.java`, `BookListPanelLegacy`, `BookPagesPanelLegacy`, `PageLayoutToolbarLegacy`, `ToolLegacySceneReader` (≈ 2100 Z.). Dazu ein **veralteter `paint/`-Ordner mit `.class`-Dateien im Projekt-Root** und ein Verzeichnis, das wörtlich `{model,ui,engine,export,i18n}` heißt (fehlgeschlagenes `mkdir` mit Brace-Expansion, leer). | ~2100 | [B] |
| S11 | **`TextWriter` schreibt jede Datei zweimal** — Original und eine `src/→bin/`-Kopie. Ein Pfad-`replace` in der Persistenz, der von der Eclipse-Ordnerstruktur abhängt. | — | [C] |
| S12 | **Drei Programme in einem Projekt.** `com.spriteanimator` (eigenes `MainWindow`, 7 Klassen) und `PathAnimator.PathEditor` (1819 Z., eigenes `JFrame`, 34 `new Color`) sind eigenständige Apps ohne Verbindung zu `paint` — und werden vom Build-Befehl (`src/paint/*.java`) **nicht kompiliert**. | 2 Apps | [B] |

### 0.4 Was TransparencyTool **besser** macht als GameLoop2

Damit die Übertragung nicht in die falsche Richtung läuft — diese Punkte
werden in die universelle Datei aufgenommen, nicht angeglichen:

- Controller-Zerlegung + Callbacks-Interfaces (0.2.1/0.2.2) sind hier
  vorbildlich; GameLoop2 §4 hat das erst als Vorhaben.
- `Layer` als unveränderliches Wert-Objekt ist stärker als GameLoop2s
  veränderliche `Entity`/`Sprite`.
- `AppPaths` als Pfad-Zentrale erfüllt GameLoop2 §14 bereits im Ansatz.
- `CanvasInstance` (Zustand pro Canvas, kein globales Feld) ist die Antwort
  auf ein Problem, das GameLoop2 noch nicht hat.

---

## 1. Übertragung der GameLoop2-Regeln

| GameLoop2 | TransparencyTool | Bewertung |
|---|---|---|
| §0 Graphify-First | **übernommen**, Graph muss erst gebaut werden (S: kein `graphify-out/`) | [A/B] → Univ. §1 |
| §1 Basisklassen (`BaseDialog`-Familie) | **übernommen, Rollen anders besetzt**: `BaseSidebarPanel`/`CardListPanel`/`Layer` existieren, **Fenster-Basisklasse fehlt** (S1/S2) | [A] → Univ. §2 + TT §20 |
| §2 Ein StyleGuide/Token-Quelle | **übernommen**, `AppColors` ist der Anker und muss um Fonts/Metrik wachsen (S3) | [A/C] → Univ. §3 + TT §21 |
| §3 Schichten-Trennung | **übernommen** als Abhängigkeitsregel; Package-Split **[C]**, hier zusätzlich durch `module-info.java` erschwert | [A/C] → Univ. §4 |
| §4 God-Klasse entflechten | **übernommen, Ziel verschoben**: nicht das Hauptfenster (erledigt), sondern `CanvasPanel` (S4) | [A] → Univ. §5 + TT §22 |
| §5 Persistenz-Standard | **übernommen**, Format ist dasselbe; Lücken bei Bild-/JSON-Schreiben (S5) | [A/B] → Univ. §6 + TT §23 |
| §6 Rendering-Konventionen | **angepasst**: AWT-Double-Buffering entfällt (Swing puffert selbst), an seine Stelle treten **EDT-Regeln** und `paintComponent`-Disziplin | [B] → TT §24 |
| §7 Namens-/Datei-Konventionen | **übernommen**, `*Controller`/`*CallbacksFactory`/`*Manager` ergänzt | [B] → Univ. §8 |
| §8 Doku-/Mockup-Pflicht | **übernommen**, verlangt hier zuerst das Einsammeln von 24 losen Dokumenten in `doc/` | [B] → Univ. §9 + TT §28 |
| §9 Task-Workflow | **1:1 übernommen** (`Prompt_Handling.txt` kopiert) | [B] → Univ. §10 |
| §11 Keyboard-Register + F1-Dialog | **übernommen mit anderer Taste** — F1 ist belegt (S7), Vorschlag **SHIFT+F1** | [B] → Univ. §11 + TT §25 |
| §12 Socket-/Plug-Regeln | **nicht anwendbar** — es gibt keine Plugin-Sockets. Der übertragbare Kern („eine Deklaration pro Anschluss, Trefferradius nie als Literal") gilt hier für die **8 Layer-Skalierhandles** in `CanvasPanel` | teilweise → TT §22 |
| §13 Bewegungs-Geschwindigkeit | **nicht anwendbar** — kein GameLoop, keine Schrittweite | — |
| §14 Editor-Einstellungen persistent | **übernommen**, `AppSettings` erfüllt es fast; Formatwiderspruch (S8) | [B] → Univ. §12 |
| §15 Z-Feld/drei Modi | **nicht anwendbar** (GameLoop2-Fachlogik). `PathLayer` hat aber `Point3D`-Z → beim Szenen-Export relevant | — |
| §16 Vertex vor Edge | **übernommen für `PathLayer`-Bearbeitung**: Hit-Test-Reihenfolge und „Punkt gewinnt gegen Kante" gilt genauso | [B] → TT §22 |
| §17 Render-Budget | **abgeschwächt übernommen**: kein GameLoop ⇒ keine Frame-Budget-Pflicht. Der Kern („gemessen wird vor optimiert") bleibt; relevant für Thumbnails/Zoom/`paintComponent` | [A/B] → Univ. §13 + TT §26 |
| §10 Nicht anwendbar (Web) | **übernommen** | → Univ. §15 |

### Neu, nur in TransparencyTool

| # | Anlass |
|---|---|
| **TT §23 Format-Vertrag mit GameII** | Das Szenen-Format ist **kein internes Format**: GameII liest es. `SCENE_FORMAT_READ_WRITE.md` ist ein Vertrag; `GameSceneReader/Writer` und `ToolLegacySceneReader` bedienen die Gegenrichtung. In GameLoop2 gibt es dafür keine Regel — sie fehlt dort ebenfalls. → Univ. §7 |
| **TT §24 Swing/EDT** | 84 `invokeLater`, 7 `SwingWorker`, 9 Timer, 2 rohe Threads. In GameLoop2 (AWT + eigener GameLoop) existiert dieses Thema nicht. |
| **TT §27 Zwei Koordinatensysteme** | Image-Space vs. Canvas-Space × Zoom; `screenToImage()`/`toSx/toSy/toSw` als einzige Übergänge. Häufigste Fehlerquelle der Codebase. |
| **TT §29 Undo + Wert-Objekt-Kopplung** | 46 `pushUndo()`, 62 `markDirty()`, Reihenfolge ist die Zusage. |

---

## 2. Empfohlene Reihenfolge (ROI-first, risikoarm zuerst)

| Schritt | Inhalt | Risiko | Model |
|---|---|---|---|
| 1 | **Regeln festschreiben** (diese Dateien) — reine Dokumentation, kein Code | [B] | erledigt 2026-07-30 |
| 2 | **`CLAUDE.md` korrigieren** (S9). Ohne das arbeitet jede Sitzung am falschen Architekturmodell — höchster Hebel pro Aufwand | [B] | Sonnet/Opus (verlangt Codelesen) |
| 3 | **Graphify-Graph bauen** (Univ. §1), Scope ohne `bin/`, `paint/`, `doc/`, `graphify-out/` | [A/B] | beliebig |
| 4 | **Doku einsammeln** → 13 Root-`.md` + 11 `src/paint/*.txt` nach `doc/` (S: Univ. §9). Reines Verschieben | [B] | **Haiku geeignet** |
| 5 | **Altlasten belegen und löschen** (S10) — Referenzfreiheit im Graphen prüfen, dann Root-`paint/`, `{model,ui,…}`-Verzeichnis, Demo-/Legacy-Dateien | [B] | Sonnet (Prüfung), Haiku (Löschen) |
| 6 | **`AppColors` → `Theme` erweitern** (S3): `FONT_*`, `PAD_*`, `RADIUS_*`, `STROKE_*` **additiv** anlegen; Bereinigung danach pro Datei | [A] dann [C] | Sonnet, Datei für Datei |
| 7 | **`KeyBindings`-Registry + Hilfe-Dialog** (S6/S7), Mockup zuerst | [B] | Sonnet |
| 8 | **`BaseDialog`** (S1) — **erst Redundanz-Audit** über die 12 Fenster, dann Extraktion; `JOptionPane`-Ablösung (S2) danach inkrementell | [C] | Opus, mit Freigabe |
| 9 | **`CanvasPanel` entflechten** (S4) — nur opportunistisch, Landkarte aus dem Graphen | [C] | Opus, mit Freigabe |

> **Anti-Overengineering (`Prompt_Handling.txt`):** Schritte 8 und 9 sind
> **nicht** „mach das jetzt komplett". Sie sind Leitplanken für neue/berührte
> Codeteile. Ein reiner Umbau bestehender, funktionierender Logik nur zur
> Regelkonformität ist ausdrücklich ein „unnötiger Schritt".

---

## 3. Fragen an den User — **ALLE BEANTWORTET am 2026-07-30**

- **Q1 [Architektur] → AUSGELAGERT.** `com.spriteanimator` und `PathAnimator`
  (S12) sind eigene Workspace-Projekte (`../SpriteAnimator/`,
  `../PathAnimator/`). Vorher geprüft: **null Querreferenzen** zu `paint` in
  beide Richtungen → reiner Verschiebe-Schritt **[B]**, nicht [C]. Beide
  bauen eigenständig (exit 0), TransparencyTool baut weiter grün
  (**370 `.class`**). Keine Zeile Code geändert. Das leere Verzeichnis
  `{model,ui,engine,export,i18n}` aus S10 ist dabei mit entfallen.
  → Regel in `GUIDELINES.md` §28.
- **Q2 [DB/API] → JA, aber Anbieter offen; Daten = Karten +
  Übersetzungs-Maps.** Daraus folgt **keine** Architektur auf Verdacht,
  sondern eine **Isolationsregel**: `MapManager` und `CardListStore` werden
  die einzigen Klassen, die wissen, *wo* diese Daten liegen; die UI greift
  nie direkt auf Dateien zu. Netz-Schicht, Auth, Caching, Offline-Verhalten
  und asynchrone Signaturen bleiben **ungebaut**, bis der Anbieter feststeht
  (sonst „unnötiger Schritt", Univ. §0). Szenen/Projekte bleiben
  dateibasiert — der GameII-Vertrag (§23) ist **nicht** betroffen.
  → neue Regel `GUIDELINES.md` **§33**.
- **Q3 [Hilfe-Taste] → SHIFT+F1.** F1 wird **nicht** freigeräumt; die
  Zweitfenster-Belegung bleibt. → `GUIDELINES.md` §25.
- **Q4 [Settings-Format] → DOKU KORRIGIEREN, Dateiname bleibt.**
  `settings/default.txt` enthält weiterhin JSON; bestehende Installationen
  behalten ihre Einstellungen. Korrigiert wurden das Javadoc von
  `AppSettings` (nannte eine nicht existierende `settings.json`) und die
  fehlende Formatbeschreibung → **`doc/Schema_AppSettings.txt`** (neu).
  → `GUIDELINES.md` §31.

### Beim Umsetzen von Q4 neu gefunden

**`AppSettings` liest und schreibt ohne Encoding-Angabe** (`FileReader`/
`FileWriter`, also Plattform-Kodierung statt UTF-8, Univ. §6). Betrifft Pfade
mit Umlauten in `recentFiles`/`recentProjects`. **Nicht behoben:** eine
Umstellung ist [C], weil bereits geschriebene Dateien danach anders gelesen
würden. Als Altlast geführt (§31, `Schema_AppSettings.txt`,
`WEITERMACHEN_PROMPT.txt`).
