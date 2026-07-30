# TextLayer Implementation Documentation

## Overview

`TextLayer` is a **non-destructive, live-text layer** system in the TransparencyTool Paint Editor. Unlike rasterized `ImageLayer` objects (which store pixel data), `TextLayer` stores only the text content and font settings, and renders glyphs on-the-fly using Java AWT font rendering.

---

## Architecture & Design

### Class Hierarchy
```
Layer (abstract base class)
├── ImageLayer    (rasterized pixels)
└── TextLayer     (live text from font settings)
```

### TextLayer Structure

**Immutable value-object semantics:** All mutations return NEW instances (no in-place modification).

**Fields:**
| Field | Type | Purpose |
|---|---|---|
| `id` | int | Unique layer identifier (inherited from `Layer`) |
| `x`, `y` | int | Image-space position (top-left corner) |
| `width`, `height` | int | Rendered bounding box size (includes 4px padding) |
| `text` | String | Content (may contain `\n` for multiline) |
| `fontName` | String | Font family (e.g., "SansSerif", "Arial") |
| `fontSize` | int | Point size (clamped to ≥ 6) |
| `fontBold` | boolean | Bold style flag |
| `fontItalic` | boolean | Italic style flag |
| `fontColor` | Color | Text color (default BLACK) |

**Constants:**
- `TEXT_PADDING = 4` — extra space (pixels on each side) added around text bounding box

---

## Creation Flow

### Factory Method: `TextLayer.of()`

Located in: `TextLayer.java:62-79`

**Signature:**
```java
public static TextLayer of(int id, String text, String fontName, int fontSize,
                           boolean bold, boolean italic, Color color, int x, int y)
```

**Steps:**
1. **Normalize inputs:**
   - `fontName` defaults to `"SansSerif"` if null
   - `fontSize` clamped to ≥ 6
   - `text` defaults to `""` if null
   - `color` defaults to `Color.BLACK` if null

2. **Measure text dimensions:**
   - Create a dummy `BufferedImage(1, 1)` to get a `Graphics2D` context
   - Create a `Font` from the normalized settings
   - Get `FontMetrics` from that font
   - Split text by `\n` to handle multiline
   - Width = longest line width (checked via `fm.stringWidth()`)
   - Height = font height × number of lines

3. **Add padding:**
   - Final width = measured_width + `TEXT_PADDING * 2` (left + right)
   - Final height = measured_height + `TEXT_PADDING * 2` (top + bottom)

4. **Return new TextLayer** with computed dimensions

### Creation in Editor: `commitTextLayer()`

Located in: `SelectiveAlphaEditor.java:2328-2340`

**Triggered by:**
- User finishes typing in the text input area (after pressing Enter or clicking elsewhere)
- Called from `CanvasPanel.commitText()`

**Logic:**
```java
@Override 
public void commitTextLayer(int updateId, String text, String fontName, int fontSize,
                            boolean bold, boolean italic, Color color, int x, int y) {
    if (text == null || text.isEmpty() || appMode != AppMode.PAINT) return;
    
    if (updateId >= 0) {
        // EDIT MODE: Replace existing layer (preserves its id)
        Layer updated = TextLayer.of(updateId, text, fontName, fontSize, bold, italic, color, x, y);
        updateElement(updateId, updated);
    } else {
        // NEW LAYER: Create with fresh id
        TextLayer newLayer = TextLayer.of(nextElementId(), text, fontName, fontSize, bold, italic, color, x, y);
        addElement(newLayer);
        nextElementId++;
    }
}
```

**Key Points:**
- Only works in `PAINT` mode
- Ignores empty/null text
- Preserves layer id when editing (prevents UI flickering in layer panel)
- Calls `addElement()` / `updateElement()` to add/modify in the layer stack

---

## Rendering Pipeline

### 1. **Main Canvas Rendering** 
Located in: `CanvasPanel.java:1320-1367`

**When:** Called during every `paintComponent()` cycle

**For TextLayer:**
```java
} else if (el instanceof TextLayer tl) {
    // (1) Calculate screen font size
    int tstyle = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
    int screenFontSz = Math.max(1, (int) Math.round(tl.fontSize() * callbacks.getZoom()));
    Font tfont = new Font(tl.fontName(), tstyle, screenFontSz);
    
    // (2) Set graphics state
    g2.setFont(tfont);
    g2.setColor(tl.fontColor());
    java.awt.FontMetrics tfm = g2.getFontMetrics();
    
    // (3) Render each line with proper spacing
    String[] tLines = tl.text().split("\n", -1);
    int tpx = sr.x + (int)(TEXT_PADDING * callbacks.getZoom());
    int tpy = sr.y + (int)(TEXT_PADDING * callbacks.getZoom());
    for (int li = 0; li < tLines.length; li++) {
        g2.drawString(tLines[li], tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
    }
}
```

**Steps Explained:**
1. **Font style calculation:** Combine BOLD and ITALIC flags into a single integer constant
2. **Screen-space scaling:** Multiply stored font size by current zoom level
3. **Create Font object:** AWT `Font` with the computed style and screen-space size
4. **Set rendering state:** Font, color, get metrics for line height/ascent
5. **Split multiline text:** Handle `\n` characters
6. **Position calculation:**
   - Start x = layer screen rect x + padding
   - Start y = layer screen rect y + padding
   - Each line offset by `(fontMetrics.getHeight() * line_index + fontMetrics.getAscent())`
7. **Render:** `g2.drawString()` for each line

### 2. **Layer Panel Thumbnail Rendering**
Located in: `ElementLayerPanel.java:307-320`

Renders a small preview thumbnail of the TextLayer in the left-side layer panel:
- Measures text with font metrics
- Draws text at a reduced size (fit-to-thumbnail)
- Shows layer name/icon

---

## User Interaction

### Creating Text
**Entry point:** Double-click on the canvas (or select TEXT_LAYER tool and click-drag to create a bounding box)

**Flow:**
1. User starts text editing (either by double-clicking an existing TextLayer or creating a new one)
2. `enterTextEditMode()` is called (`CanvasPanel.java:1112`)
3. Modal text input appears with font chooser controls (font name, size, bold, italic, color)
4. User types text, presses Enter or clicks outside
5. `commitText()` → `commitTextLayer()` → layer is created/updated

### Editing Existing TextLayer
1. **Double-click** on a TextLayer to enter edit mode
2. Font settings from the layer are loaded into the text chooser
3. User can modify text and font properties
4. Pressing Enter commits changes
5. `callbacks.commitTextLayer()` is called with updated values
6. New TextLayer instance created via `TextLayer.of()` and replaces old one

### Font Size Adjustment
**Shortcut:** CTRL + Mouse Wheel on a selected TextLayer

Located in: `CanvasPanel.java:723-728`
```java
if (primary instanceof TextLayer tl) {
    int delta = -e.getWheelRotation(); // scroll up = larger
    int newSize = Math.max(6, tl.fontSize() + delta);
    Layer updated = tl.withFontSize(newSize);
    callbacks.updateSelectedElement(updated);
}
```

---

## Mutation Methods (Value-Object Pattern)

All methods return NEW instances; TextLayer itself is never modified.

### 1. `withPosition(int nx, int ny)`
Returns a copy at a new image-space position, keeping text/font unchanged.

### 2. `withBounds(int nx, int ny, int nw, int nh)`
**Resizes the text layer:**
- Calculates scale factor: `max(nw/width, nh/height)`
- Scales font size proportionally
- Recomputes bounding box via `TextLayer.of()` with new font size
- Called when user drags resize handles around a selected TextLayer

**Code:**
```java
double scaleX = (double) nw / Math.max(1, width);
double scaleY = (double) nh / Math.max(1, height);
double scale  = Math.max(scaleX, scaleY);  // Preserve aspect
int newFontSize = Math.max(6, (int) Math.round(fontSize * scale));
return of(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, nx, ny);
```

### 3. `withText(String newText, String newFontName, int newFontSize, boolean newBold, boolean newItalic, Color newColor)`
Updates text content and/or font settings. Recomputes dimensions via `TextLayer.of()`.

### 4. `withFontSize(int newFontSize)`
Convenience method to change font size only. Recomputes dimensions.

---

## Integration Points

### SelectiveAlphaEditor (Main Frame)
- **`nextElementId()`**: Manages unique layer IDs
- **`addElement(Layer)`**: Adds new layer to `layers` stack
- **`updateElement(int id, Layer)`**: Replaces existing layer
- **`commitTextLayer()`**: Callback invoked when text editing is complete
- **`getSelectedElement()`**: Returns currently selected layer (may be TextLayer)
- **`updateSelectedElement(Layer)`**: Updates selected layer (if it's a TextLayer)

### CanvasPanel (Drawing Surface)
- **`enterTextEditMode(Layer)`**: Loads TextLayer into text input mode
- **`commitText()`**: Flushes typed text, triggers `commitTextLayer()`
- **`syncTextChooserFromElement(Layer)`**: Syncs UI controls to match TextLayer's font settings
- **`paintComponent()`**: Renders all visible TextLayers
- **`elemRectScreen(Layer)`**: Gets screen-space rectangle of a layer
- **Text editing state:** `textBuffer`, `textBoundingBox`, `editingTextElementId`

### CanvasCallbacks Interface
Defines the contract between CanvasPanel and SelectiveAlphaEditor:
- `getSelectedElement()`, `updateSelectedElement()`
- `commitTextLayer()`, `updateSelectedElement()`
- `getZoom()` — needed for screen-space font scaling
- `elemRectScreen()` — layer position/size in screen coordinates

---

## Known Issues / Why Text Might Not Display

### 1. **Layer Not Created**
- `commitTextLayer()` returns early if `text.isEmpty()` or `appMode != PAINT`
- **Check:** Is app in PAINT mode? Is text non-empty?

### 2. **Layer Not Added to Stack**
- TextLayer instance is created but `addElement()` fails silently
- **Check:** Is `nextElementId()` incrementing? Are layers stored in a list/deque?

### 3. **Layer Not Rendered in paintComponent()**
- Instance is in the layer stack but isn't being painted
- **Likely cause:** `instanceof TextLayer` check passes, but rendering code has an exception or invalid state
- **Check:** Verify font creation succeeds, Graphics2D state is correct

### 4. **Layer Not Visible in ElementLayerPanel**
- TextLayer exists but thumbnail panel doesn't show it
- **Check:** Is `ElementLayerPanel` iterating over the layers list and refreshing?

### 5. **Font Metrics Incorrect**
- Text width/height computed wrong, so bounding box is too small → text is clipped
- **Check:** Does `TextLayer.of()` correctly measure multiline text?

### 6. **Zoom / Screen Scaling Issue**
- Text displays at wrong size or position
- **Check:** Is `callbacks.getZoom()` correct? Is `elemRectScreen()` calculating screen position correctly?

---

## Data Flow Summary

```
User Types Text
    ↓
[CanvasPanel: textBuffer accumulates chars]
    ↓
User Presses Enter
    ↓
commitText()
    ↓
callbacks.commitTextLayer(id, text, fontName, fontSize, bold, italic, color, x, y)
    ↓
[SelectiveAlphaEditor]
    ↓
TextLayer.of(id, text, fontName, fontSize, bold, italic, color, x, y)
    ├─ Normalize inputs
    ├─ Measure text dimensions (FontMetrics)
    ├─ Add padding
    └─ Return new TextLayer instance
    ↓
addElement(newLayer) OR updateElement(id, newLayer)
    ↓
[Layer added to layer stack]
    ↓
canvasPanel.repaint()
    ↓
[CanvasPanel.paintComponent()]
    ├─ Iterate layers
    ├─ For each TextLayer:
    │  ├─ Calculate screen font size (fontSize × zoom)
    │  ├─ Create Font, set on Graphics2D
    │  ├─ Split text by \n
    │  └─ drawString() each line
    └─ Repaint complete
    ↓
Text Visible on Canvas
```

---

## Testing Checklist

To verify TextLayer is working:

- [ ] **Create a new text layer** (double-click canvas, type text, press Enter)
- [ ] **Verify layer appears** in ElementLayerPanel (left sidebar)
- [ ] **Text is visible** on the main canvas
- [ ] **Layer can be selected** and has a bounding box with resize handles
- [ ] **Text is editable** (double-click to re-enter edit mode)
- [ ] **Zoom works** (text scales with zoom level)
- [ ] **Font properties work** (bold, italic, color, size all apply)
- [ ] **Multiline text works** (type text with \n, verify each line renders)
- [ ] **Layer is renderable** (export/save includes rendered text)
- [ ] **Undo/redo works** (add text, undo, text layer is removed)

---

## Code References

| File | Lines | Purpose |
|---|---|---|
| `TextLayer.java` | 1-132 | TextLayer class definition |
| `Layer.java` | 1-54 | Abstract base class |
| `CanvasPanel.java` | 1324-1337 | TextLayer rendering (main canvas) |
| `ElementLayerPanel.java` | 307-320 | TextLayer thumbnail rendering |
| `SelectiveAlphaEditor.java` | 2328-2340 | commitTextLayer() callback |
| `CanvasPanel.java` | 1112-1127 | enterTextEditMode() |
| `CanvasPanel.java` | 723-728 | CTRL+wheel font size adjustment |
| `CanvasPanel.java` | 1070-1090 | syncTextChooserFromElement() |

