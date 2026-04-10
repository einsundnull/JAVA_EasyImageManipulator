# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Compile (from the project root):**
```bash
javac.exe -encoding UTF-8 -sourcepath src -d bin src/paint/*.java src/module-info.java
```
Always use `-encoding UTF-8` — source files contain Unicode symbols (arrows, emoji, etc.) that cause "unmappable character" errors without it.

**Run:**
```bash
java.exe -cp bin --module-path bin -m TransparencyTool/paint.SelectiveAlphaEditor
```
Or simply via Eclipse: Run → Run As → Java Application on `SelectiveAlphaEditor`.

**Deploy to GitHub:**
```bash
bash src/paint/JAVA_EasyImageManipulator-Push.sh
```
Remote: `https://github.com/einsundnull/JAVA_EasyImageManipulator.git`

**Java target:** JavaSE-16 (module system, switch expressions with `yield` require ≥ 14)

No build tool (Maven/Gradle). No tests. Eclipse project: source → `src/`, output → `bin/`.

---

## Architecture

The app is a single-window Java Swing image editor with two modes: **Alpha Editor** (make areas transparent) and **Paint** (MS-Paint-style drawing). Everything lives in `package paint`.

### Core flow

```
SelectiveAlphaEditor (JFrame)
├── Top bar          – zoom buttons, mode toggles (Canvas/Paint), filmstrip toggle
├── Center
│   ├── TileGalleryPanel (WEST) – directory image browser, toggleable via filmstrip button
│   └── JLayeredPane (CENTER)
│       ├── dropHintPanel    – shown when no file is loaded
│       └── viewportPanel    – shown after file load
│           ├── rulerNorthBar (NORTH, optional) – HRulerPanel
│           ├── VRulerPanel   (WEST,  optional)
│           ├── JScrollPane → canvasWrapper → CanvasPanel (the drawing surface)
│           └── scrollSpacer  (SOUTH, 16 px gap before toolbar)
└── Bottom bar
    ├── PaintToolbar (NORTH) – tool strip, hidden in Alpha-Editor mode
    └── statusBar    (SOUTH) – action buttons, status label
```

### Key classes

| Class | Role |
|---|---|
| `SelectiveAlphaEditor` | Main frame (~1900 lines). Owns all state, coordinates between components. All modes, undo/redo, keyboard shortcuts, zoom, rulers, floating selection. |
| `CanvasPanel` | Inner class of `SelectiveAlphaEditor`. Handles all mouse input and `paintComponent`. |
| `PaintEngine` | Stateless static drawing methods: pencil, eraser, line, circle, rect, floodfill, eyedropper, crop/paste, flip, rotate, scale. All operate directly on a `BufferedImage` in image-space. |
| `PaintToolbar` | The horizontal tool strip shown in Paint mode. Communicates via a `Callbacks` interface back to `SelectiveAlphaEditor`. |
| `TileGalleryPanel` | Left sidebar showing directory images as tiles. Async thumbnail loading via `SwingWorker`. Contains `DarkScrollBarUI` (static, also used by the main scroll pane). |
| `ColorPickerPopup` | Floating HSV color picker (`JWindow`). Used by `PaintToolbar` for primary/secondary color selection. |
| `AppColors` | Single source of truth for all UI colors. |
| `ToastNotification` | Shows a timed, translucent top-right popup (used for CTRL+S save confirmation). |
| `WhiteToAlphaConverter` | Legacy utility: converts white pixels to transparent. Its `getOutputPath()` is reused for all save paths. |

### Coordinate systems

- **Image-space**: pixel coordinates in `workingImage`. Used by `PaintEngine` and stored in `selectedAreas`, `floatRect`.
- **Canvas (screen) space**: image-space × zoom. This is what `CanvasPanel.e.getPoint()` returns. `screenToImage()` converts between the two.
- `toSx/toSy/toSw()` in `CanvasPanel.paintComponent` convert image coords to screen coords for drawing overlays.

### Floating selection (MS Paint style)

When the SELECT tool is active and the user clicks inside a selection, the region is **lifted** into `floatingImg` (a `BufferedImage`), the original canvas area is cleared, and `floatRect` (image-space `Rectangle`) tracks its position/size. The float is drawn on top of the canvas during `paintComponent`. Eight scale handles are drawn around it; corners scale proportionally, sides scale a single axis. Float interaction (hit detection, drag, scale) is checked **before** the `appMode`/tool switch, so it works regardless of current mode. `commitFloat()` merges back; `cancelFloat()` calls `doUndo()`.

Paste (CTRL+V) creates a floating selection immediately — nothing is written to the canvas until `commitFloat()`.

### Undo/Redo

`ArrayDeque<BufferedImage>` stacks, max 50 entries. `pushUndo()` is called before any destructive canvas operation. `doUndo()` / `doRedo()` swap stacks. The floating selection is NOT part of the undo stack mid-drag; `cancelFloat()` calls `doUndo()` to restore the pre-lift state.

### Ruler panels

`HRulerPanel` and `VRulerPanel` are inner classes. They read `scrollPane.getViewport().getViewPosition()` and `canvasPanel.getX()/getY()` (centering offset inside `canvasWrapper`) on every repaint to stay synchronized with scroll position. `buildRulerLayout()` adds/removes `rulerNorthBar` and `vRuler` from `viewportPanel` when the ruler is toggled.

### Dark scrollbar

`TileGalleryPanel.DarkScrollBarUI` (public static inner class) is applied to the gallery's scroll pane and also to the main viewport's horizontal and vertical scroll bars via `TileGalleryPanel.applyDarkScrollBar(JScrollBar)`.

### Saving

Output is always PNG. The filename comes from `WhiteToAlphaConverter.getOutputPath(sourceFile, suffix)` which appends a mode suffix (`_painted`, `_floodfill_alpha`, `_selective_alpha`, `_white_to_alpha`) before the extension. CTRL+S saves silently and shows a toast; the "Speichern" button in the status bar shows a dialog with the saved filename.
