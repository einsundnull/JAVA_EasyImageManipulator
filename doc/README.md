# doc/ — Wegweiser

Alle Projektdokumente liegen hier (Univ. §9 / §28). Der Ordner ist **flach**:
die älteren Dokumente verweisen mit **nacktem Dateinamen** aufeinander
(`siehe F6_FEATURE.md`) — ein `archive/`-Unterordner hätte jeden dieser
Verweise gebrochen. Statt Ordnern sortiert **diese Liste**.

---

## 1. Verbindlich — vor jeder Aufgabe lesen

| Datei | Inhalt |
|---|---|
| [`JAVA_GUIDELINES_UNIVERSAL.md`](JAVA_GUIDELINES_UNIVERSAL.md) | **Zuerst.** Projektneutrale Standards für alle Java-Projekte (§0–§15). **KOPIE** — Original: `../../JAVA_GUIDELINES_UNIVERSAL.md` |
| [`GUIDELINES.md`](GUIDELINES.md) | Projekt-Steckbrief + TransparencyTool-Regeln §20–§33 |
| [`Prompt_Handling.txt`](Prompt_Handling.txt) | Task-Workflow: `[BT]`/`[ST]`, Risikoklassen, Abschluss-Block |
| [`PROMPT_Schwachstellen-Audit.md`](PROMPT_Schwachstellen-Audit.md) | **Wiederverwendbarer Audit-Prompt** (alle Java-Projekte): Schwachstellen finden, bewerten, tabellieren. **KOPIE** — Original: `../../PROMPT_Schwachstellen-Audit.md` |
| [`WEITERMACHEN_PROMPT.txt`](WEITERMACHEN_PROMPT.txt) | **Aktueller Stand + offene Schritte.** Einstieg nach `/clear` |

> **Randnotiz: die zwei universellen Prompts liegen doppelt — absichtlich.**
> `JAVA_GUIDELINES_UNIVERSAL.md` und `PROMPT_Schwachstellen-Audit.md` gehören
> dem **Workspace**, nicht diesem Projekt. Die maßgebliche Fassung liegt in
> `C:\Users\pc\eclipse-workspace\` und gilt gleichlautend für GameLoop2,
> SpriteAnimator, PathAnimator und JavaDemos.
> Die Dateien in `doc/` sind **byte-identische Lesekopien**, damit ein Einstieg
> nach `/clear` alle Pflichtdokumente an einem Ort findet.
> - **Geändert wird nur das Original.** Eine Änderung an der Kopie ist ein Bug —
>   sie gilt für die vier Nachbarprojekte nicht mit.
> - Danach neu kopieren:
>   `cp ../../JAVA_GUIDELINES_UNIVERSAL.md ../../PROMPT_Schwachstellen-Audit.md .`
> - Gleichstand prüfen: `md5sum` beider Paare (Stand 2026-07-31: identisch).
> - **Relative Pfade *innerhalb* der Kopien zählen ab dem Workspace-Root**
>   (`../JAVA_GUIDELINES_UNIVERSAL.md` im Audit-Prompt meint das Original,
>   nicht `TransparencyTool/`). Die Kopie ist eine Lesehilfe, keine Pfadbasis.

## 2. Verträge & Formate — ändern heißt: erst hier ändern

| Datei | Inhalt |
|---|---|
| [`SCENE_FORMAT_READ_WRITE.md`](SCENE_FORMAT_READ_WRITE.md) | **Format-Vertrag mit GameII** (§23). Kein internes Format — ein anderes Programm liest mit |
| [`Schema_AppSettings.txt`](Schema_AppSettings.txt) | Feldtabelle + Parser-Vertrag der Einstellungen (§31) |
| [`Scene_read_write_comparison mit Game.txt`](<Scene_read_write_comparison mit Game.txt>) | Gegenüberstellung TT- ↔ GameII-Szenen |
| [`ReaderWriterScenesImplementPlan 15_04_2026_17_04.txt`](<ReaderWriterScenesImplementPlan 15_04_2026_17_04.txt>) | Planung der Reader/Writer-Paare |

## 3. Analyse & Begründung der Regeln

| Datei | Inhalt |
|---|---|
| [`Audit_Schwachstellen_2026-07-31.md`](Audit_Schwachstellen_2026-07-31.md) | **Schwachstellen-Tabelle**, 20 Befunde nach Schwere (4× S1), je mit `Datei:Zeile` und Fehlerszenario · Top-5-Reihenfolge · „geprüft und sauber" |
| [`GUIDELINES_VORSCHLAG_2026-07-30.md`](GUIDELINES_VORSCHLAG_2026-07-30.md) | Ist-Analyse mit Befunden **S1–S13**, Übertragungstabelle aus GameLoop2, Reihenfolge-Empfehlung |
| [`Dialog-Vergleich.md`](Dialog-Vergleich.md) | **Vorarbeit für das `BaseDialog`-Audit** (§20) |
| [`ANALYSIS_ARRAY_ISSUES.md`](ANALYSIS_ARRAY_ISSUES.md) | Analyse zur Canvas-Array-Umstellung |

## 4. Feature-Dokumentation (Stand April 2026 — inhaltlich noch gültig)

| Datei | Inhalt |
|---|---|
| [`PATH_TOOL_DOCUMENTATION.md`](PATH_TOOL_DOCUMENTATION.md) | Pfad-Werkzeug |
| [`TextLayer_Implementation_Documentation.md`](TextLayer_Implementation_Documentation.md) | TextLayer |
| [`F6_FEATURE.md`](F6_FEATURE.md) | F6 „auf Canvas anwenden" |
| [`Schema_ButtonLeiste_Top_with_Books.txt`](Schema_ButtonLeiste_Top_with_Books.txt) | ASCII-Mockup der oberen Leiste (Buch-Modus) |
| [`Schema_PaintToolbar_Icons.txt`](Schema_PaintToolbar_Icons.txt) | **Symbole der Paint-Leiste** (2026-08-01, freigegeben und umgesetzt) — Befund, Vorschlagstabelle, Zauberstab-Familie · **Abschnitt 8: was die Sichtprüfung am Entwurf geändert hat** |
| [`HowImagesForScenesAreLoaded.txt`](HowImagesForScenesAreLoaded.txt) · [`Preload.txt`](Preload.txt) | Bild-/Preload-Pfade |
| [`SplitCanvas.txt`](SplitCanvas.txt) · [`SplitCanvas II.txt`](<SplitCanvas II.txt>) | Konzept der zwei Canvases |

## 5. Tastatur (§25)

> **Die Wahrheit steht seit 2026-07-30 im Code: `paint.KeyBindings.ALL`.**
> 79 Einträge in sieben Scopes, sichtbar im Programm über **Umschalt+F1**
> oder den Knopf „?“. Neue Taste oder Geste → **zuerst** Registry-Eintrag,
> dann Handler.
>
> **Die Werkzeug-Kürzel liegen in `KeyBindings.TOOL_KEYS`** (seit
> 2026-08-01) — einer Tabelle, aus der sich Dialogzeilen, Verdrahtung *und*
> Tooltip speisen. Eine Taste wird nirgends ein zweites Mal getippt; genau
> die zweite, handgepflegte Liste war der Fehler davor.

| Datei | Rolle |
|---|---|
| [`Schema_KeyBindings_Dialog.txt`](Schema_KeyBindings_Dialog.txt) | Mockup + Entscheidungen zum Hilfe-Dialog (umgesetzt) |
| [`F1_F6_COMPLETE_REFERENCE.md`](F1_F6_COMPLETE_REFERENCE.md) · [`QUICK_REFERENCE_F1_F5.md`](QUICK_REFERENCE_F1_F5.md) | Historische F-Tasten-Beschreibung (April 2026) — bei Widerspruch gewinnt die Registry |

> `Shortcut Table.txt` wurde am 2026-07-30 **gelöscht**: handgepflegt, kannte
> 13 von 53 Belegungen. Eine zweite Liste neben der Registry ist die Ursache
> des Problems, nicht die Lösung (§25). Inhalt steckt in `KeyBindings.ALL`,
> die Historie in Git.

## 6. Verlaufsprotokolle (`progress_*`) — was wann warum passiert ist

| Datei | Thema |
|---|---|
| [`progress_2026-07-30_guidelines-einfuehrung.txt`](progress_2026-07-30_guidelines-einfuehrung.txt) | Zweistufige Guidelines eingeführt |
| [`progress_2026-07-30_Q1-Q4-umsetzung.txt`](progress_2026-07-30_Q1-Q4-umsetzung.txt) | Auslagerungen, DB/API-Regel §33, Settings-Doku · **Nachtrag:** `CLAUDE.md` neu geschrieben |
| [`progress_2026-07-30_graphify-graph.txt`](progress_2026-07-30_graphify-graph.txt) | KnowledgeMap gebaut · **Nachtrag:** Altlasten beseitigt |
| [`progress_2026-07-30_doku-einsammeln.txt`](progress_2026-07-30_doku-einsammeln.txt) | Diese Aufräumaktion |
| [`progress_2026-07-30_apptheme.txt`](progress_2026-07-30_apptheme.txt) | `AppTheme` angelegt (Tokens für Fonts/Maße) |
| [`progress_2026-07-30_apppaths-books.txt`](progress_2026-07-30_apppaths-books.txt) | `books/` + `cardfolders/` über `AppPaths` |
| [`progress_2026-07-30_keybindings.txt`](progress_2026-07-30_keybindings.txt) | Tasten-Registry + Hilfe-Dialog |
| [`progress_2026-07-31_apptheme-migration-01.txt`](progress_2026-07-31_apptheme-migration-01.txt) | Token-Bereinigung, **Datei 1**: `UIComponentFactory` |
| [`progress_2026-07-31_apptheme-migration-02.txt`](progress_2026-07-31_apptheme-migration-02.txt) | Token-Bereinigung, **Dateien 2+3**: `SelectiveAlphaEditor` + `UIBuilder` |
| [`progress_2026-07-31_apptheme-migration-03.txt`](progress_2026-07-31_apptheme-migration-03.txt) | Token-Bereinigung, **Datei 4**: `TextToolbar` (erste ohne Restliteral) |
| [`progress_2026-07-31_apptheme-migration-04.txt`](progress_2026-07-31_apptheme-migration-04.txt) | Token-Bereinigung, **Datei 5**: `BaseSidebarPanel` (wirkt auf 4 Panels) |
| [`progress_2026-07-31_apptheme-migration-05.txt`](progress_2026-07-31_apptheme-migration-05.txt) | Token-Bereinigung, **Datei 6**: `NewImageDialog` · abgeleitete Alpha-Farbe · Tippfehler-Befund |
| [`progress_2026-07-31_apptheme-migration-06.txt`](progress_2026-07-31_apptheme-migration-06.txt) | Token-Bereinigung, **Datei 7**: `TileGalleryPanel` · Unterklasse tippte Basisklassen-Farben nach |
| [`progress_2026-07-31_apptheme-migration-07.txt`](progress_2026-07-31_apptheme-migration-07.txt) | Token-Bereinigung, **Datei 8**: `PageLayoutToolbar` · ein Grundton, drei Alphas · zwei Dateien als „Farbe = Inhalt" zurückgestellt |
| [`progress_2026-07-31_apptheme-migration-08.txt`](progress_2026-07-31_apptheme-migration-08.txt) | Token-Bereinigung, **Datei 9**: `MapsPanel` · zwei Knopf-Gruppen · dritte RGB-Kollision |
| [`progress_2026-07-31_apptheme-migration-09.txt`](progress_2026-07-31_apptheme-migration-09.txt) | Token-Bereinigung, **Datei 10**: `TranslationMapListPanel` · nur Schriften, alle Farben sind Benutzerdaten |
| [`progress_2026-07-31_apptheme-migration-10.txt`](progress_2026-07-31_apptheme-migration-10.txt) | Token-Bereinigung, **Datei 11**: `CanvasPanel` · file-lokale Konstanten statt Palette · ⏳ §21-Ergänzung zur Freigabe |
| [`progress_2026-07-31_apptheme-migration-11.txt`](progress_2026-07-31_apptheme-migration-11.txt) | Token-Bereinigung, **Datei 12**: `ElementLayerPanel` · Knopf-Familie in die Palette · **Schritt [6] inhaltlich abgeschlossen** |
| [`progress_2026-07-31_universelle-prompts-in-doc.txt`](progress_2026-07-31_universelle-prompts-in-doc.txt) | Universelle Prompts als Lesekopie in `doc/` verankert · Randnotiz in vier Wegweisern |
| [`progress_2026-07-31_schwachstellen-audit.txt`](progress_2026-07-31_schwachstellen-audit.txt) | Verlauf des Schwachstellen-Audits · was geprüft wurde und was **nicht** |
| [`progress_2026-07-31_F01-schliessen-schutz.txt`](progress_2026-07-31_F01-schliessen-schutz.txt) | **F01 repariert:** Rückfrage beim Schließen · der Dialog existierte bereits ohne Aufrufer · Verify **X1–X13** |
| [`progress_2026-07-31_I01-tts-injektion.txt`](progress_2026-07-31_I01-tts-injektion.txt) | **I01 repariert:** `MapsPanel` nutzt `CardTtsPlayer`, `TextToSpeech` gelöscht · Text nie mehr in der Kommandozeile · Verify **T1–T9** |
| [`Task_2026-07-31_2100_F02-F03-Atomares-Speichern.txt`](Task_2026-07-31_2100_F02-F03-Atomares-Speichern.txt) | **PD** zu F02+F03 · Klassifikation der 21 Fundstellen in vier Gruppen · abgeschlossen |
| [`progress_2026-07-31_F02-F03-atomares-speichern.txt`](progress_2026-07-31_F02-F03-atomares-speichern.txt) | **F02+F03 repariert:** `ImageFileWriter` (atomar + Rückgabewert geprüft), alle 21 Stellen migriert · §34 · Verify **S1–S12** |
| [`progress_2026-07-31_D02-D03-undo-einzeiler.txt`](progress_2026-07-31_D02-D03-undo-einzeiler.txt) | **D02+D03 repariert:** ein `pushUndo` pro Aktion · „Stack leer ⇒ gespeichert" entfällt · **Befund: die Undo-Stacks hängen an der Datei, nicht am Canvas** · Verify **D1–D6** |
| [`Task_2026-07-31_2300_B01-fileCache-deckeln.txt`](Task_2026-07-31_2300_B01-fileCache-deckeln.txt) | **PD** zu B01 · Messung, Politik-Entscheidung, Umsetzung · abgeschlossen |
| [`progress_2026-07-31_B01-filecache-deckeln.txt`](progress_2026-07-31_B01-filecache-deckeln.txt) | **B01 repariert:** Historie inaktiver Dateien wird gekürzt · **die Messung hat die Lösung geändert** — nicht die Eintragszahl war das Problem, sondern die Undo-Stacks (606 MB je Eintrag) · Verify **B1–B7** |
| [`progress_2026-07-31_claudemd-korrektur.txt`](progress_2026-07-31_claudemd-korrektur.txt) | **`CLAUDE.md` gegen den gemessenen Ist-Stand korrigiert** (Univ. §9) · sechs Widersprüche, jede Zahl einzeln nachgemessen · gröbster: „`AppTheme` noch nirgends benutzt" bei 154 Verwendungen |
| [`Task_2026-08-01_0900_PaintToolbar-Icons.txt`](Task_2026-08-01_0900_PaintToolbar-Icons.txt) | **PD** zu den Paint-Leisten-Symbolen · 6 Schritte + 3 Zwischenschritte · abgeschlossen |
| [`progress_2026-08-01_paint-icons-vorschlag.txt`](progress_2026-08-01_paint-icons-vorschlag.txt) | **Befund:** elf Knöpfe zeigen vier Zeichen · 🪣 ist in **keiner** Schriftfamilie darstellbar (gemessen) · **die Tooltips versprachen Tastenkürzel, die es nicht gibt** |
| [`Task_2026-08-01_0957_Werkzeug-Kuerzel.txt`](Task_2026-08-01_0957_Werkzeug-Kuerzel.txt) | **PD** zu den Werkzeug-Kürzeln · 6 Schritte · abgeschlossen |
| [`progress_2026-08-01_werkzeug-kuerzel.txt`](progress_2026-08-01_werkzeug-kuerzel.txt) | **Werkzeug-Kürzel nachgerüstet** · `TOOL_KEYS` als einzige Quelle für Dialog, Verdrahtung und Tooltip · **Befund: ein „r" im Text drehte zugleich das Bild** — einfache Buchstaben feuern vor der Texteingabe · Verify **W1–W9** |
| [`progress_2026-08-01_paint-icons-umsetzung.txt`](progress_2026-08-01_paint-icons-umsetzung.txt) | **`PaintIcons` gebaut und verdrahtet** · Zauberstab-Familie aus Grundform + Abzeichen · **wie ohne Programmstart geprüft wurde — und welche drei Fehler das fand** · Verify **P1–P9** |
| [`WEITERMACHEN_PROMPT_2026-07-30_archiv.txt`](WEITERMACHEN_PROMPT_2026-07-30_archiv.txt) | Vorgänger-Fassung mit dem vollen Verlauf der Schritte [1]–[4] |

## 7. Historisch — abgeschlossene Arbeiten (nicht als Ist-Stand lesen)

Diese Dokumente beschreiben **Zustände von April 2026**. Sie sind
Verlaufsdokumentation, keine Architekturbeschreibung. Bei Widerspruch zum
Code gewinnt der Code; bei Widerspruch zu `CLAUDE.md` gewinnt `CLAUDE.md`.

- [`IMPLEMENTATION_SUMMARY.md`](IMPLEMENTATION_SUMMARY.md) · [`EXTENDED_IMPLEMENTATION_SUMMARY.md`](EXTENDED_IMPLEMENTATION_SUMMARY.md) · [`F6_IMPLEMENTATION_SUMMARY.md`](F6_IMPLEMENTATION_SUMMARY.md) — Abschlussberichte Zweitfenster/F-Tasten
- [`SECONDARY_WINDOW_TEST.md`](SECONDARY_WINDOW_TEST.md) · [`SECONDARY_WINDOW_EXTENDED_TEST.md`](SECONDARY_WINDOW_EXTENDED_TEST.md) — Testszenarien von damals
- [`12_04_2026_13_20 Refactor Canvas Array.txt`](<12_04_2026_13_20 Refactor Canvas Array.txt>) · [`Canvas Visibility Bug 15_04_2026_10_57.txt`](<Canvas Visibility Bug 15_04_2026_10_57.txt>) · [`Streamlining Code 16_04_2026_17_19.txt`](<Streamlining Code 16_04_2026_17_19.txt>) — datierte Arbeitsnotizen

---

## Neue Dokumente anlegen

- **Abschluss einer Aufgabe** → `progress_<Datum>_<Name>.txt` (Univ. §10),
  **nicht** ein weiteres `*_SUMMARY.md`. Genau daraus ist Abschnitt 7
  entstanden: Berichte, die wie Architekturbeschreibungen aussehen und
  stillschweigend veralten.
- **UI-Layout** → zuerst `Schema_<Thema>.txt` als ASCII-Mockup, dann
  Freigabe, dann Code (Univ. §9).
- **Vor einer Basisklassen-Extraktion** → `Audit Redundanz <Thema> <Datum>.txt`.
- **Dateiformat** → `Schema_<Typ>.txt` (Univ. §6).

> **`CLAUDE.md` bleibt im Projekt-Root** — sie ist der Einstiegspunkt und
> wird dort erwartet. Sie ist das einzige Dokument außerhalb von `doc/`.
