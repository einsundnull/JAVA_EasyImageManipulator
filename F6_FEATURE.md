# F6 Feature: Apply Secondary Window to Canvas II

## Overview
Press **F6** to load the current image from the Secondary Canvas Window into Canvas II (Canvas 1).

---

## What It Does

When F6 is pressed:
1. **Checks Secondary Window Status**
   - If window is not open/visible: Shows toast "Secondary window not open"
   - If window is open, continues...

2. **Gets Image Based on Current Preview Mode**
   - **SNAPSHOT mode**: Uses the saved snapshot from the current active canvas
   - **LIVE_ALL mode**: Uses the workingImage from the other canvas
   - **LIVE_ALL_EDIT mode**: Uses the workingImage from the other canvas

3. **Applies to Canvas II (Canvas 1)**
   - Replaces Canvas II's workingImage with a copy of the source image
   - Clears all undo/redo stacks for Canvas II
   - Clears all elements (activeElements becomes empty)
   - Resets zoom to 1.0 (100%)
   - Clears selected elements

4. **Updates UI**
   - Switches active canvas to Canvas II
   - Makes Canvas II visible (enables secondCanvasBtn)
   - Refreshes element panel
   - Centers view on Canvas II
   - Shows toast: "Image applied to Canvas II"

---

## Use Cases

### Scenario 1: Copy Canvas I to Canvas II
1. Load image A on Canvas I
2. Open Secondary Window (F1)
3. Press F2 to enter LIVE_ALL mode
4. Secondary window shows Canvas I (other canvas)
5. Press F6 → Image from secondary window applied to Canvas II
6. Result: Canvas II now has the same image as Canvas I

### Scenario 2: Apply Snapshot to Canvas II
1. Load image A on Canvas I
2. Paint/edit on Canvas I
3. Open Secondary Window (F1)
4. Press F2 twice to enter SNAPSHOT mode
5. Press F3 to take snapshot of current Canvas I state
6. Press F6 → Snapshot applied to Canvas II
7. Result: Canvas II has the snapshot of Canvas I's workingImage

### Scenario 3: Transfer Work Between Canvases
1. Load image A on Canvas I, Image B on Canvas II
2. Make some changes on Canvas I
3. Want to move Canvas I's version to Canvas II
4. Open Secondary Window, select LIVE_ALL mode
5. Secondary shows Canvas I
6. Press F6 → Canvas I's image replaces Canvas II's image
7. Result: Canvas II now has Canvas I's image, ready for further editing

### Scenario 4: Quick Copy from Other Canvas (LIVE_ALL_EDIT)
1. Canvas I and Canvas II have different images
2. Open Secondary Window in LIVE_ALL_EDIT mode
3. See both canvas with element outlines
4. Press F6 → Copy the other canvas's image to Canvas II
5. Now can compare/edit both versions side-by-side

---

## Behavior Details

### Image Normalization
- Applied image is normalized to TYPE_INT_ARGB (supports transparency)
- Original image type preserved in source canvas

### Undo/Redo
- Canvas II's undo/redo stacks cleared (fresh canvas)
- Previous Canvas II state cannot be undone
- Original Canvas I state unchanged

### Elements
- All elements on Canvas II are removed
- Canvas II starts fresh with no layers/paths
- Canvas I's elements remain untouched (not copied)

### Zoom Level
- Canvas II zoom reset to 1.0 (100%)
- View centered on new image
- User-manual-zoom flag reset

### Active Canvas
- Canvas II becomes the active canvas after F6
- Canvas I remains visible in secondary window
- Both canvases displayed if layout is split

---

## Toast Messages

| Situation | Message |
|-----------|---------|
| Secondary window not open | "Secondary window not open" |
| No snapshot available | "No snapshot available" |
| Source canvas empty | "Source canvas has no image" |
| Success | "Image applied to Canvas II" |

---

## Keyboard Shortcut

- **F6**: Apply secondary window image to Canvas II

---

## Integration with Other Features

### With F1 (Toggle Window)
- F6 requires secondary window to be open (F1 first if needed)

### With F2 (Mode Cycle)
- F6 behavior depends on current preview mode
- Different modes source the image from different places

### With F3 (Snapshot)
- F3 updates snapshot
- F6 applies whatever is shown (including new snapshot)

### With F4 (Fullscreen)
- F6 works regardless of fullscreen state
- Fullscreen doesn't affect F6 functionality

### With F5 (Window Stacking)
- Window stacking mode doesn't affect F6
- F6 still applies image to Canvas II

### With Canvas Navigation
- Switching active canvas doesn't affect F6
- F6 always applies to Canvas II (index 1)
- Secondary window shows "other" canvas (always 1 - activeCanvasIndex)

---

## Technical Implementation

### Method: `applySecondaryWindowToCanvas()`

1. **Validation**
   - Check if secWin exists and is visible
   - Based on secMode, determine source image and validate

2. **Image Acquisition**
   - SNAPSHOT: Copy secSnapshot
   - LIVE_ALL/LIVE_ALL_EDIT: Copy other canvas's workingImage
   - Use deepCopy() to create independent copy
   - Normalize to TYPE_INT_ARGB

3. **Canvas II Update**
   - Replace ci(1).workingImage
   - Clear stacks and elements
   - Reset zoom and manual-zoom flag
   - Refresh canvasPanel

4. **UI Update**
   - Set activeCanvasIndex = 1
   - Enable and select secondCanvasBtn
   - Call updateLayoutVisibility() to show both canvases
   - Call centerCanvas(1) to focus on Canvas II
   - Show toast notification

---

## Edge Cases

### Empty Canvas
- If secondary source canvas is empty:
  - SNAPSHOT mode: Toast "No snapshot available"
  - LIVE_ALL mode: Toast "Source canvas has no image"

### No Secondary Window
- If window not open: Toast "Secondary window not open"
- User can press F1 first to open window

### Canvas II Hidden
- F6 also enables Canvas II visibility
- secondCanvasBtn is enabled and selected
- Both canvases shown after F6

---

## Comparison with Manual Copy

| Method | Time | Effort | Result |
|--------|------|--------|--------|
| **Manual Drag** | Slow | High | Image copied, may distort |
| **F6 (Direct Copy)** | Instant | 1 key | Clean image copy, exact |

F6 is faster and more precise than manual dragging.

---

## Workflow Suggestion

**Best Practice Workflow:**

1. Work on Canvas I
2. Open Secondary (F1) to monitor work
3. Press F3 to snapshot current state
4. Continue editing Canvas I
5. Want to save previous state to Canvas II?
   - F3 again to refresh snapshot to earlier state (can't go back, so optional)
   - OR switch to SNAPSHOT mode (F2)
6. Press F6 → Canvas II has the snapshot
7. Now can compare Canvas I (current) with Canvas II (previous state)

