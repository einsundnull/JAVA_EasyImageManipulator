# Schwachstellen-Audit TransparencyTool — 2026-07-31

> **Dieses Audit repariert nichts.** Es sammelt und bewertet. Jede Reparatur ist
> danach ein eigener, freigabepflichtiger Schritt.
> **Kein Code geändert. Build unberührt.**
>
> Scope: `src/paint/**` (106 Klassen) + `src/book/**` (3), rund 32 000 Zeilen.
> Grundlage: `JAVA_GUIDELINES_UNIVERSAL.md` §0–§15 · `doc/GUIDELINES.md` §20–§33 ·
> `doc/WEITERMACHEN_PROMPT.txt` (die dort mit `[!]` geführten Befunde sind
> übernommen und teils **verschärft**).
> Verfahren: `doc/PROMPT_Schwachstellen-Audit.md`.

**Schwere:** S1 Datenverlust/Absturz/Beschädigung/Sicherheitslücke · S2 falsches
Ergebnis oder verlorene Arbeit · S3 Beeinträchtigung · S4 Wartbarkeit
**Sicherheit:** BELEGT (im Code nachvollzogen) · PLAUSIBEL · VERMUTUNG
**Aufwand:** S (< 1 h) · M (überschaubar) · L (eigener Task mit PD)

---

## Stand der Reparaturen

| Befund | Stand |
|---|---|
| **F01** | ✔ **erledigt am 2026-07-31** → `progress_2026-07-31_F01-schliessen-schutz.txt` |
| **I01** | ✔ **erledigt am 2026-07-31** → `progress_2026-07-31_I01-tts-injektion.txt` |
| alle übrigen | offen |

> **Was F01 gelehrt hat:** der Dialog
> `EditorDialogs.showUnsavedChangesDialog()` **existierte bereits** — fertig
> gebaut, über `createBaseDialog` gestylt, mit drei Optionen — und hatte im
> gesamten Quellbaum **null Aufrufer**. Die Reparatur war „anschließen", nicht
> „bauen". Damit entfielen Mockup-Pflicht (§9), die `JOptionPane`-Frage (§20)
> und das Vorziehen von Schritt [9] **ersatzlos**.
> **Vor jeder weiteren Reparatur aus dieser Tabelle lohnt dieselbe Frage:
> gibt es die Lösung schon?** Bei **I01** lautet die Antwort nachweislich
> ebenfalls ja (`CardTtsPlayer`).
>
> **Neu aufgenommen:** **F01b** — „Alles speichern" über beide Canvases und
> über nicht geöffnete `dirtyFiles`. Bewusst nicht mitgemacht: das erfindet
> eine Speicher-Politik (In-Place vs. Suffix-Kopie), und In-Place-Schreiben ist
> laut **F02** derzeit nicht absturzsicher. Reihenfolge also: F02 vor F01b.

---

## Die Tabelle

| ID | Kat. | Datei:Zeile | Befund | Fehlerszenario | Schwere | Sicherheit | Aufwand |
|---|---|---|---|---|---|---|---|
| ~~**F01**~~ ✔ | Persistenz | `SelectiveAlphaEditor.java:254` + `AppLifecycleController.java:28,147` | **ERLEDIGT 2026-07-31.** **Kein Schutz beim Schließen.** `EXIT_ON_CLOSE`; `windowClosing` ruft `saveOnClose()`, das **Szene-Metadaten und Einstellungen** sichert — aber `hasUnsavedChanges`/`dirtyFiles` **nie abfragt** und `workingImage` **nie schreibt**. | Bild bearbeiten, nicht speichern, Fenster-X klicken → JVM endet sofort, **alle Pixeländerungen weg, ohne jede Rückfrage**. Die App *kennt* den Zustand (Titelmarker, `dirtyFiles`) und benutzt ihn beim Beenden nicht. | **S1** | BELEGT | S |
| ~~**I01**~~ ✔ | Einschleusung | `TextToSpeech.java:52-67` (Datei am 2026-07-31 **gelöscht**) | **ERLEDIGT 2026-07-31.** **PowerShell-Injektion.** Kartentext wird in eine **doppelt gequotete** PS-Zeichenkette interpoliert. `escapedText` ersetzt `"` durch `\"` — **Backslash ist in PowerShell kein Escape-Zeichen**, und `$(...)` wird in doppelten Anführungszeichen **ausgewertet**. | Karte mit dem Text `$(Remove-Item C:\… -Recurse)` oder `"; <befehl>; "`, Knopf „Vorlesen" in `MapsPanel:232` → der Befehl **läuft**. Maps liegen als `maps/<lang>.json` und sind austauschbar/teilbar. | **S1** | BELEGT | S |
| ~~**F02**~~ ✔ | Persistenz | `SaveController.java:142,199,283` (+ 21 `ImageIO.write` in 12 Dateien) | **ERLEDIGT 2026-07-31** → `ImageFileWriter`, §34. **Kein atomares Schreiben, direkt auf die Quelldatei.** `ImageIO.write(workingImage, "PNG", c.sourceFile)` trunkiert das Original **beim Öffnen** und füllt es dann. Kein Temp+Rename, kein Backup. | Absturz, voller Datenträger oder Stromausfall mitten im Schreiben eines großen PNG → **Originaldatei ist zerstört**, weder alte noch neue Fassung existiert. Betrifft „In Original speichern" und `Strg+S` auf Buchseiten. | **S1** | BELEGT | M |
| **G01** | Kodierung | `GameSceneReader.java:139,176,251,291` ⇄ `GameSceneWriter.java:64` | **Round-Trip zerstört Umlaute in einer Vertragsdatei.** Gelesen wird mit `ISO_8859_1`, zurückgeschrieben mit `Files.write(path, lines)` = **UTF-8**. §23 sagt „UTF-8 ohne BOM". | GameII-Szene mit `ä` (UTF-8, 2 Byte) → als 2 Latin-1-Zeichen `Ã¤` gelesen → als 4 UTF-8-Byte geschrieben. Ein Verschieben des Sprites im TT **beschädigt die Datei für GameII** (§7/§23-Vertragsbruch), obwohl `rawLines()` „unverändert übernehmen" verspricht. | **S1** | BELEGT | S |
| **D01** | Korrektheit | `SaveController.java:53-79` | **Das Undo-Band speichert ausschließlich `workingImage`.** `doUndo` stellt nur Pixel wieder her. Element-Operationen (verstecken, verschieben, drehen, löschen, Text ändern) rufen `pushUndo()`, legen aber einen **inhaltsgleichen** Pixel-Schnappschuss ab. | Layer verschieben → `Strg+Z` → das Bild ist identisch, **der Layer bleibt verschoben**; gleichzeitig ist ein echter Pixel-Schritt aus dem 50er-Band verdrängt und der Redo-Stack gelöscht. Undo wirkt für den Benutzer „kaputt". | **S2** | BELEGT | L |
| ~~**D02**~~ ✔ | Korrektheit | `KeyboardShortcutManager.java:172-176` | **ERLEDIGT 2026-07-31.** Gegenprobe über alle 48 `pushUndo`-Vorkommen: es war die **einzige** solche Stelle. **`toggleVis` ruft `pushUndo()` in der Schleife**, einmal pro Element (bekannte Altlast — **hier verschärft**): kombiniert mit D01 sind es n **wirkungslose** Schnappschüsse. | 50 Layer selektieren, `Umschalt+V` → 50 Pushes → `MAX_UNDO = 50` → **die gesamte echte Pixel-Historie ist verdrängt**, und kein einziger Schritt macht das Verstecken rückgängig. | **S2** | BELEGT | S |
| ~~**D03**~~ ✔ | Korrektheit | `SaveController.java:81-91` | **ERLEDIGT 2026-07-31.** Der Fehlschluss ist entfernt; Undo/Redo setzt den Marker nur noch, es löscht ihn nicht. Das im Vorschlag genannte Sättigungs-Flag wurde **verworfen** — die Undo-Stacks sind **pro Datei** (`CanvasFileState`), ein Flag am Canvas hätte beim Blättern desynchronisiert. **Leerer Undo-Stack wird als „gespeichert" gedeutet.** `afterUndoRedo` setzt bei leerem Stack `hasUnsavedChanges = false` und entfernt aus `dirtyFiles`. Nach mehr als `MAX_UNDO` Änderungen ist der leere Stack aber **nicht** der Ursprungszustand. | 60 Pinselstriche, dann 50× `Strg+Z` → Stack leer → Titel meldet „keine ungespeicherten Änderungen", Datei weicht um 10 Striche ab. Zusammen mit **F01** geht das beim Schließen **stumm** verloren. | **S2** | BELEGT | S |
| **G02** | Kodierung | `AppSettings.java:149,290` · `SceneFileWriter.java:96` · `SceneFileReader.java:55` · `TextWriter.java:31` · `TextReader.java:32` · `MapManager.java:34,78` · `ProjectManager.java:75,105,259` · `CardListStore.java:89,105` · `LastProjectsManager.java:41,93` | **Neun Klassen lesen/schreiben ohne Kodierungsangabe** (`FileReader`/`FileWriter` → Plattform-Kodierung). Bisher nur für `AppSettings` dokumentiert; **`SceneFileWriter`/`TextWriter` betreffen den §23-Vertrag**, der UTF-8 zusagt. | Textlayer „Größe" wird als cp1252 geschrieben; GameII oder ein anderes System liest UTF-8 → Mojibake. Pfade mit Umlauten in `recentFiles` überleben einen Rechnerwechsel nicht. `CLAUDE.md`/§23 behaupten UTF-8 → **Doku widerspricht Code** (Univ. §9). | **S2** | BELEGT | M |
| **B01** | Lecks | `CanvasInstance.java:42` · `FileLoadController.java:99,265,299` · `PreloadController.java:44` | **`fileCache` wächst unbegrenzt.** `Map<File, CanvasFileState>` hält **Vollbilder**; es gibt kein `remove`, keine Größengrenze, keine Verdrängung. (Der Nachbar `preloadCache` **hat** eine: `PreloadController:114`.) | Ordner mit 300 Fotos durchblättern → 300 Vollbilder im Speicher → `OutOfMemoryError` mitten in der Arbeit; wegen **F01** ist die ungespeicherte Arbeit dann weg. | **S2** | BELEGT | M |
| **B02** | Lecks | `SaveController.java:57-59,67,76` | **Undo-Speicher wird in Schritten gezählt, nicht in Bytes** — und `doRedo` (Z. 76) pusht **ohne** Größenprüfung auf den Undo-Stack; der Redo-Stack ist **gar nicht** gedeckelt. | 4000×3000-Bild = 48 MB je Kopie. 50 Schritte × 2 Canvases ≈ **4,8 GB**. Lange Redo/Undo-Ketten heben den `MAX_UNDO`-Deckel zusätzlich auf. | **S2** | BELEGT | M |
| **A01** | Nebenläufigkeit | `CardTtsPlayer.java:28-55` | **Wettlauf um den Wiedergabe-Zustand** und **Callback außerhalb des EDT.** Der `finally`-Block des alten Threads nullt `currentId`/`currentOnDone` **ohne zu prüfen, ob sie inzwischen der neuen Wiedergabe gehören**, und ruft `cb.run()` auf dem `CardTTS`-Thread. | Karte A vorlesen, auf Karte B klicken: `play(B)` setzt die Felder, danach läuft A's `finally` → **B's Knopf springt auf „▶" zurück, während B noch spricht**; `isPlaying(B)` ist falsch. `stop()` ruft `cb` zusätzlich ein zweites Mal. | **S2** | BELEGT | S |
| ~~**F03**~~ ✔ | Persistenz | alle 21 `ImageIO.write`-Stellen | **ERLEDIGT 2026-07-31** → `ImageFileWriter`, §34. **Rückgabewert nie geprüft.** `ImageIO.write` wirft nicht, wenn kein Writer gefunden wird — es liefert `false`. | Liefert der Aufruf `false`, meldet der Code trotzdem „Gespeichert", setzt `hasUnsavedChanges = false` und entfernt aus `dirtyFiles` → der Benutzer schließt beruhigt, **die Datei ist leer/trunkiert**. | **S2** | PLAUSIBEL | S |
| **A02** | Nebenläufigkeit | `ZoomController.java:107-114` | **Timer-Callback greift auf das Feld `c.zoomTimer` statt auf die Ereignisquelle.** `javax.swing.Timer` kann ein bereits eingereihtes Ereignis **nach** `stop()` zustellen. | Zoom läuft (T1), Ereignis liegt in der EDT-Schlange; neuer Zoom ersetzt das Feld durch T2 → das alte Ereignis stoppt **T2** und nullt das Feld → Zoom bleibt auf halbem Weg stehen. Wurde stattdessen von `FileLoadController:486` genullt: **NPE auf dem EDT**. | **S3** | PLAUSIBEL | S |
| **I02** | Einschleusung/Korrektheit | `CardTtsPlayer.java:76` | **`text.replace("'", "''")` verdoppelt Apostrophe im Nutztext.** In einem **einfach gequoteten Here-String** (`@'…'@`) findet **keine** Ersetzung statt — die Verdopplung bleibt stehen. (Sie *verhindert* zugleich, dass eine Zeile `'@` den String beendet — das ist der Grund, warum hier **keine** Injektion wie in I01 vorliegt. Die Schutzwirkung ist unkommentiert und würde bei einer „Aufräumung" verloren gehen.) | Karte „don't stop" wird als `don''t stop` an SAPI übergeben. Harmlos hörbar, aber der Schutzmechanismus ist **zufällig aussehend** und damit gefährdet. | **S3** | BELEGT | S |
| **L01** | Persistenz/Pfade | `TextWriter.java:19-22` | **Jede Textdatei wird zweimal geschrieben**, die zweite über einen Pfad-`replace` `src/texts/`→`bin/texts/` (bekannte Altlast). Hängt an der Eclipse-Ordnerstruktur. | Außerhalb von Eclipse (oder nach einem Umzug) zeigt der zweite Pfad ins Leere oder erzeugt Fremdverzeichnisse; die zwei Kopien können auseinanderlaufen. | **S3** | BELEGT (dok.) | M |
| **F04** | Persistenz | `AppLifecycleController.java:150-153` | **`saveOnClose` sichert nur die Szene von Canvas I** (`ci(0)`), übernimmt Einstellungen aber vom **aktiven** Canvas (`ci()`, Z. 159/162). | Mit Canvas II gearbeitet und geschlossen → Elementstand von Canvas II fällt weg, gespeichert werden Raster/Modus von II mit der Szene von I: gemischter Zustand beim nächsten Start. | **S3** | PLAUSIBEL | S |
| **L02** | Wartbarkeit | `ZoomState.java` (298) · `FileStateCache.java` (347) · `ElementLayerState.java` (429) · `FloatSelectionState.java` (258) | **1332 Zeilen referenzfreie Parallel-Implementierungen** lebender Logik. Textsuche über den gesamten Quellbaum: **0 Referenzen**, **kein `main()`** (also nicht „startbar" wie die 2026-07-30 ausgelagerten Fälle). `ZoomState` dupliziert die Zoom-Animation aus `ZoomController` **inklusive** des Timer-Fehlers A02. | Der nächste Bearbeiter repariert A02 in `ZoomState` — der toten Kopie — und wundert sich, dass nichts passiert. Klassische Doppelpflege-Falle. | **S4** | BELEGT | S |
| **E01** | Fehlerbehandlung | 20 Fundstellen, u. a. `SceneLocator.java:227,241` · `ClipboardController.java:289,300` · `QuickOpenController.java:91` · `SecondaryWindowController.java:200` | **Stille `catch`-Blöcke** — teils `catch (Exception ignored) {}` über mehreren Anweisungen. Univ. §6: „kein stilles Verschlucken". | `SecondaryWindowController:200` verschluckt einen fehlgeschlagenen `AppSettings.save()` komplett: Einstellungen sind weg, niemand erfährt es. Bei `SceneLocator` bleibt eine nicht gefundene Szene unerklärt. | **S4** | BELEGT | M |
| **K01** | Oberfläche | `KeyboardShortcutManager.java:274-297` | **Globaler `KeyEventDispatcher` greift vor jedem Fokus.** `Alt+T`, `Alt+P`, `F1`–`F7` wirken auch, während in einem Dialog-Textfeld oder im Canvas-Texteditor getippt wird. | `Alt+P` beim Tippen öffnet/schließt die schwebende Malleiste. In der Registry als `GLOBAL` geführt (also bewusst) — die **Wechselwirkung mit Scope `TEXT`** ist dort aber nicht vermerkt. | **S4** | PLAUSIBEL | S |
| **M01** | Prüfbarkeit | ganzes Projekt | **Keine Tests, kein Testverzeichnis, kein Build-Tool.** `src/` enthält nur `paint`, `book`, `module-info.java`. | Ohne Test sind **`PaintEngine`** (1239 Z. reine Pixelmathematik, stateless — der am leichtesten testbare Teil der Codebase) und die Koordinatenumrechnung (§27) nicht sicher änderbar. Jede D01-Reparatur am Undo-Band wäre heute ein Blindflug. | **S4** | BELEGT | L |

---

## Die fünf, die ich zuerst angehen würde

Nicht stur nach Schwere — Reihenfolge nach **Schaden × Häufigkeit ÷ Aufwand**.

1. **F01 — Warndialog beim Schließen** *(S1, Aufwand S)*
   Der billigste S1 im ganzen Audit: `DO_NOTHING_ON_CLOSE` + Abfrage von
   `dirtyFiles` in `windowClosing`. Der Zustand wird bereits geführt, er wird
   nur nicht benutzt. **Ein Klick daneben kostet heute die ganze Sitzung.**
   Trifft jeden Benutzer, jeden Tag. *(Braucht `ConfirmDialog` aus §20 — bis
   dahin ist eine Ausnahme mit Begründung nötig, oder Schritt [9] zieht vor.)*

2. **I01 — TTS-Injektion** *(S1, Aufwand S)*
   Die Lösung existiert bereits im eigenen Haus: `CardTtsPlayer` macht es mit
   Here-String und Temp-Datei richtig. `MapsPanel` auf `CardTtsPlayer`
   umstellen und `TextToSpeech` löschen — **entfernt gleichzeitig eine
   Dublette** (§3). Bei der Gelegenheit I02 kommentieren.

3. **F02 + F03 — atomares Speichern an einer Stelle** *(S1, Aufwand M)* — **✔ ERLEDIGT 2026-07-31**
   *Nachtrag zur Einschätzung unten: „21 Fundstellen, eine Änderung" stimmt
   als Zahl, aber nur **neun** davon konnten überhaupt eine vorhandene Datei
   zerstören. Sieben erzeugten garantiert neue Namen, vier Wegwerf-Temps,
   eine schrieb gar keine Datei. Einteilung in
   `progress_2026-07-31_F02-F03-atomares-speichern.txt`.*
   Ein `ImageIO`-Helfer in der `io`-Schicht: in Temp schreiben, Rückgabewert
   prüfen, dann `Files.move(ATOMIC_MOVE)`. **21 Fundstellen, eine Änderung** —
   und es ist die Vorarbeit für Befund S5 („`ImageIO.write` in 9 Dateien"),
   der ohnehin geführt wird.

4. **D02 + D03 — die zwei Undo-Einzeiler** *(S2, Aufwand S)* — **✔ ERLEDIGT 2026-07-31**
   *Nachtrag: „den Sättigungsfall merken" ging nicht — die Undo-Stacks
   hängen an der **Datei** (`CanvasFileState` in `fileCache`), nicht am
   Canvas. Gelöst wurde stattdessen, indem die falsche Richtung ganz
   entfällt.*
   `pushUndo()` aus der `toggleVis`-Schleife heraus, und das
   „Stack leer ⇒ gespeichert" durch einen echten Vergleich ersetzen (oder den
   Sättigungsfall merken). Beide sind isoliert, beide sind [B].
   **D01 selbst ist ausdrücklich NICHT hier** — ein Undo-Band, das auch
   Elementzustände trägt, ist ein eigener Task mit PD und braucht M01.

5. **B01 — `fileCache` deckeln** *(S2, Aufwand M)*
   Die Verdrängung aus `PreloadController:114` ist die Vorlage; sie muss nur
   auf den zweiten Cache angewandt werden. §26 verlangt vorher eine **Messung**
   (ab wann wird es eng?) — die gehört in den Schritt.

**Bewusst nicht in den Top 5:** G01/G02 (Kodierung). Fachlich sind sie S1/S2,
aber jede Umstellung ändert, wie **bereits geschriebene** Dateien gelesen
werden — das ist [C] und braucht eine Migrationsentscheidung des Users, keinen
schnellen Fix. G01 (GameII) sollte trotzdem als **erstes der beiden** kommen,
weil es einen Vertrag nach außen bricht.

---

## Geprüft und sauber

Diese Kategorien wurden durchgesehen, **ohne** Befund — das ist Teil des
Ergebnisses:

- **§24 Zeichenpfad.** Skriptgestützt über **alle** `paintComponent`-Blöcke:
  **kein** `repaint()`, kein `markDirty()`, kein `pushUndo()`, kein
  `setState`/`save()` im Zeichenpfad. Univ. §4 („render schreibt nicht
  zurück") ist eingehalten. Damit auch **keine** `repaint`→`paint`→`repaint`-
  Rekursion (Kategorie C).
- **§30 Canvas-Zugriff.** **Null** Fundstellen für `canvases[0]`/`canvases[1]`
  außerhalb des Hauptfensters. Der Zugriff läuft durchgehend über
  `ci()`/`ci(idx)`.
- **§29 Undo-Reihenfolge am `PaintEngine`.** Stichprobe Floodfill:
  `CanvasPanel:821` ruft `pushUndo()` **vor** `performFloodfill` — korrekt.
  (Die Lücke liegt nicht in der Reihenfolge, sondern im **Inhalt** des
  Schnappschusses → D01.)
- **Kontrollfluss (C).** Keine `while(true)`, keine `for(;;)`, keine
  unbegrenzte Rekursion gefunden.
- **`ConcurrentModificationException`.** Kein „Sammlung ändern während
  `for-each`". `toggleVis` sieht danach aus, benutzt aber `List.set()` —
  das bricht den Iterator nicht.
- **Fließkomma-Vergleiche.** Kein `==`/`!=` auf `zoom`/`scale`/Koordinaten.
- **Verdrängung im `preloadCache`.** `PreloadController:114-129` deckelt
  korrekt nach `MAX_CACHE_SIZE` mit ältestem-zuerst.
- **Deserialisierung fremder Daten.** Kein `ObjectInputStream`, keine
  Java-Serialisierung — der gesamte Kategorie-I-Teil „Deserialisierung"
  entfällt.
- **Netz, DB, Auth, Nebenläufigkeit über Prozessgrenzen.** Existiert nicht
  (§33: Anbieter offen, nichts gebaut). Alle Netz-Fehlerfälle **entfallen** —
  das ist eine bewusste Feststellung, keine Lücke.
- **Verklemmungen.** Kein `synchronized`, keine Sperren im Quellbaum; die
  einzigen geteilten Strukturen sind zwei `ConcurrentHashMap` und drei
  `volatile`-Felder. Deadlocks sind damit ausgeschlossen (Wettläufe nicht,
  siehe A01).

## Nicht ernsthaft geprüft — ehrlich benannt

- **`CanvasPanel.java` (3332 Z.)** wurde **überflogen, nicht durchgearbeitet.**
  Die Maus-/Hit-Test-Logik, die 8 Skalierhandles und der eingebaute
  Texteditor (eigener Caret, eigene Undo-Stacks) sind die wahrscheinlichste
  Fundstelle für Off-by-one- und Reihenfolgefehler (Kategorie D). Das braucht
  einen **eigenen Durchgang** und ist der Grund, warum diese Tabelle keinen
  einzigen Caret-Befund enthält.
- **`PaintEngine.java` (1239 Z.)** — Pixelmathematik, Zeile für Zeile
  ungeprüft. Bereichsgrenzen bei Crop/Scale/Rotate sind hier zu erwarten.
- **`book/` (3 Klassen)** — nur gestreift.
- **Kategorie J (Leistung)** wurde **nicht gemessen**, nur strukturell
  betrachtet (B01/B02). §26 verbietet Optimierung ohne Messung; ein
  Leistungsbefund ohne `System.nanoTime`-Beleg wäre hier ein Verstoß gegen
  die eigenen Regeln.

---

## Anschluss

Die Tabelle ist die Vorlage für einzelne `[ST]`-Schritte. Zusammenhängend und
damit `Task_*.txt`-würdig (PD) sind nur zwei Gruppen:
**F02+F03** (ein Speicher-Helfer, 21 Fundstellen) und
**D01+M01** (Undo-Band tragfähig machen — ohne Tests nicht verantwortbar).

Alles andere ist einzeln und klein. **Reparaturen werden einzeln freigegeben.**
