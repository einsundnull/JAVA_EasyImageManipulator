# Secondary Canvas Window – Test Plan

## Feature: Secondary Canvas Window (F1/F2/F3)

Undecorated floating window showing canvas content in three preview modes.

---

## Controls

- **F1**: Toggle window open/close
- **F2**: Cycle preview modes (SNAPSHOT → LIVE_ALL → LIVE_ALL_EDIT → SNAPSHOT)
- **F3**: In SNAPSHOT mode, take a snapshot of the current active canvas

---

## Preview Modes

### SNAPSHOT
- Static image of the currently active canvas
- Updated manually via F3
- No elements shown (only the base image)
- Toast shows: "Snapshot updated"

### LIVE_ALL
- Live repaint of the **other** canvas (1 - activeCanvasIndex)
- Repaints every ~33ms (30 FPS)
- Shows all elements (ImageLayer, PathLayer)
- Aspect ratio preserved, centered in window
- Toast shows: "Preview: LIVE_ALL"

### LIVE_ALL_EDIT
- Live repaint of the **other** canvas
- Shows all elements AND their outlines (dashed cyan borders)
- Shows PathLayer points (cyan lines) and control points (white circles)
- Toast shows: "Preview: LIVE_ALL_EDIT"

---

## Test Scenarios

### Scenario 1: Basic Toggle (F1)
1. Press F1 → secondary window appears (undecorated, 640x480, centered to main app)
2. Press F1 again → window closes
3. Expected: Clean open/close without errors

### Scenario 2: Mode Cycling (F2)
1. Open secondary window (F1)
2. Press F2 three times
3. Expected: Toast messages show:
   - "Preview: LIVE_ALL"
   - "Preview: LIVE_ALL_EDIT"
   - "Preview: SNAPSHOT"

### Scenario 3: SNAPSHOT Mode (F3)
1. Load image A into Canvas 0 (main)
2. Open secondary window (F1)
3. Press F2 → should be in SNAPSHOT mode after 2 presses
4. Press F3 → Toast shows "Snapshot updated", window shows Canvas 0's image
5. Switch to Canvas 1
6. Add an element to Canvas 0
7. Click F3 again → window still shows old snapshot (unchanged)
8. Switch back to Canvas 0, press F3 → new snapshot taken with element

### Scenario 4: LIVE_ALL Mode
1. Load Image A into Canvas 0, Image B into Canvas 1
2. Canvas 0 is active
3. Open secondary window (F1)
4. Press F2 → cycle to LIVE_ALL mode
5. Expected: Secondary window shows Canvas 1's content live
6. Switch to Canvas 1 in main app
7. Expected: Secondary window now shows Canvas 0's content (the "other" canvas)
8. Draw something on Canvas 0 in the background → secondary window updates in real-time

### Scenario 5: LIVE_ALL_EDIT Mode
1. Load image with elements into Canvas 1
2. Canvas 0 active, Canvas 1 has ImageLayer elements + PathLayer
3. Open secondary window (F1)
4. Press F2 twice → LIVE_ALL_EDIT mode
5. Expected:
   - Secondary window shows Canvas 1's content
   - Cyan dashed outlines around all elements
   - PathLayer rendered with cyan lines connecting points
   - White circles at control points
6. Modify an element on Canvas 1 (scale/move) → secondary updates live
7. Expected: Outlines follow the element movements

### Scenario 6: Window Properties
1. Open secondary window (F1)
2. Expected:
   - Window is undecorated (no title bar, no close/min/max buttons)
   - Cannot be resized via mouse drag (system chrome disabled)
   - Window size is 640x480
   - Centered to main application window
   - Closes when pressing F1 or Escape (optional, via HIDE_ON_CLOSE)

### Scenario 7: Aspect Ratio Preservation
1. Load a 800×600 image
2. Open secondary window in LIVE_ALL mode (F1, F2)
3. Expected: Image appears in window with correct aspect ratio, centered with checkerboard background visible
4. Manually resize secondary window (if possible) → image adapts to fit inside

### Scenario 8: Concurrent Operations
1. Open secondary window (F1, F2 to LIVE_ALL)
2. While secondary is updating live:
   - Draw on Canvas 1 (other canvas)
   - Zoom in/out on Canvas 1
   - Add/remove elements
3. Expected: Secondary window updates smoothly without blocking main app

---

## Edge Cases

### Empty Canvas
- If Canvas 1 (other) is empty, secondary window shows checkerboard only

### No Image Loaded
- If main canvas has no image, SNAPSHOT mode shows nothing (returns early)

### Window Closed While Painting
- Close secondary window (F1) mid-paint on the other canvas
- Reopen (F1) → shows current state
- Expected: No crashes, state is clean

---

## Verification Checklist

- [ ] Compilation succeeds with no errors
- [ ] Application starts without exceptions
- [ ] F1 opens and closes secondary window
- [ ] F2 cycles modes with Toast messages visible
- [ ] F3 updates snapshot with Toast message
- [ ] LIVE_ALL repaints the other canvas in real-time
- [ ] LIVE_ALL_EDIT shows element outlines + path overlays
- [ ] Secondary window is undecorated
- [ ] Element updates on other canvas appear instantly in secondary window
- [ ] Closing main app doesn't crash (cleanly closes secondary)

