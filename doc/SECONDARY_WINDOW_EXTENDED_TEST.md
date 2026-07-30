# Secondary Canvas Window – Extended Test Plan (F1-F5)

## Feature: Secondary Canvas Window (F1/F2/F3/F4/F5)

Undecorated floating window with full control: preview modes, fullscreen, window management, drag, and resize.

---

## Controls

### Preview & Content (F1-F3)
- **F1**: Toggle window open/close
- **F2**: Cycle preview modes (SNAPSHOT → LIVE_ALL → LIVE_ALL_EDIT → SNAPSHOT)
- **F3**: In SNAPSHOT mode, take a snapshot of current active canvas

### Window Management (F4-F5)
- **F4**: Toggle fullscreen (maximized ↔ restored size)
- **F5**: Cycle window stacking (Always on Top → Normal → Behind Main → Always on Top)

### Mouse Interaction
- **Drag window**: Click anywhere on canvas area and drag to move window
- **Resize window**: Drag edges/corners to resize
  - **Edges**: Top (↕), Bottom (↕), Left (↔), Right (↔)
  - **Corners**: Top-Left (↖), Top-Right (↗), Bottom-Left (↙), Bottom-Right (↘)
- **Minimum size**: 200×150 pixels

---

## Preview Modes (F2)

| Mode | Display | Updates | Elements | Borders | PathLayer |
|------|---------|---------|----------|---------|-----------|
| **SNAPSHOT** | Static image of active canvas | Manual (F3) | No | No | No |
| **LIVE_ALL** | Live other canvas | 30 FPS | Yes (ImageLayer + PathLayer) | No | No |
| **LIVE_ALL_EDIT** | Live other canvas | 30 FPS | Yes | Dashed cyan | Cyan lines + points |

---

## Window Modes (F5)

| Mode | Behavior | Stacking |
|------|----------|----------|
| **Always on Top** | Window stays above all other windows | Topmost |
| **Normal** | Window respects normal stacking order | Follows OS window manager |
| **Behind Main** | Main app window forced to front | Secondary behind main app |

---

## Test Scenarios

### Scenario 1: Basic Toggle (F1)
1. Press F1 → secondary window appears (undecorated, 640×480, centered)
2. Press F1 again → window closes
3. Expected: Clean open/close, no errors

---

### Scenario 2: Mode Cycling (F2)
1. Open secondary window (F1)
2. Press F2 three times
3. Expected: Toast messages cycle through all modes:
   - "Preview: LIVE_ALL"
   - "Preview: LIVE_ALL_EDIT"
   - "Preview: SNAPSHOT"

---

### Scenario 3: Snapshot (F3)
1. Load image A into Canvas 0
2. Open secondary window, ensure in SNAPSHOT mode (press F2 twice from initial)
3. Press F3 → Toast "Snapshot updated"
4. Expected: Secondary window shows Canvas 0's workingImage
5. Switch to Canvas 1, add element to Canvas 0 (in background)
6. Canvas 0 not selected → F3 doesn't update
7. Click Canvas 0, press F3 → new snapshot with element included

---

### Scenario 4: Fullscreen Toggle (F4)
1. Open secondary window (F1)
2. Window shows 640×480, undecorated, in corner
3. Press F4 → window maximizes
   - Toast: "Fullscreen: ON"
   - Window fills entire screen
4. Press F4 again → window restores to original 640×480, original position
   - Toast: "Fullscreen: OFF"
5. Expected: Smooth transition, no content loss, preview still renders

---

### Scenario 5: Window Stacking (F5) – Always on Top
1. Open secondary window (F1)
2. Press F5 → Toast: "Window: Always on Top"
3. Expected: Secondary window now stays above all other windows
4. Click on main app → secondary window remains visible on top
5. Click elsewhere (desktop/other apps) → secondary window remains on top

---

### Scenario 6: Window Stacking (F5) – Normal
1. From "Always on Top" state, press F5 → Toast: "Window: Normal"
2. Expected: Window no longer forces itself to top
3. Click main app → secondary window may go behind (depends on OS focus)
4. Click secondary window → it comes to front (normal window behavior)

---

### Scenario 7: Window Stacking (F5) – Behind Main
1. From "Normal" state, press F5 → Toast: "Window: Behind Main"
2. Expected: Main app window is brought to front, secondary goes behind
3. Move main app window → secondary window now visible behind it
4. Click secondary window → it doesn't come to front (main app stays on top when clicking secondary)
5. Note: This is a "soft" behind behavior (main app is set to top, but not enforced with alwaysOnTop=true)

---

### Scenario 8: Mouse Drag (Move)
1. Open secondary window (F1)
2. Click anywhere on the content area and drag → window moves with mouse
3. Expected: Window follows cursor smoothly
4. Release → window stays at new position
5. Works in all window modes (normal, fullscreen exit restores position)

---

### Scenario 9: Resize from Edges
1. Open secondary window (F1), position it on screen
2. Move mouse to **top edge** → cursor changes to ↕ (vertical resize)
3. Drag up/down → window height changes, top-left stays fixed
4. Move to **right edge** → cursor changes to ↔ (horizontal resize)
5. Drag left/right → window width changes, top-left stays fixed
6. Expected: Smooth resizing, minimum enforced (200×150)

---

### Scenario 10: Resize from Corners
1. Open secondary window, position on screen
2. Move mouse to **bottom-right corner** → cursor changes to ↘ (NE resize)
3. Drag toward bottom-right → both width and height increase
4. Move to **top-left corner** → cursor changes to ↖ (NW resize)
5. Drag toward top-left → both width and height decrease, window moves up/left
6. Expected: Proportional corner resizing, minimum enforced

---

### Scenario 11: Fullscreen Drag/Resize
1. Open secondary window, press F4 (fullscreen)
2. Try to drag → should not move (window is maximized)
3. Try to resize by dragging edges → should not resize
4. Press F4 again (exit fullscreen) → drag/resize works again
5. Expected: Fullscreen mode disables drag/resize, exits normally

---

### Scenario 12: Live Mode with Drag
1. Load different images on Canvas 0 and Canvas 1
2. Open secondary window (F1)
3. Press F2 to enter LIVE_ALL mode
4. Drag secondary window to different position
5. While dragging: Paint on Canvas 1 (the canvas being previewed)
6. Expected:
   - Window moves smoothly
   - Preview updates live even during drag (or after drag stops)
   - No stutter or lag

---

### Scenario 13: Fullscreen + Always on Top
1. Open secondary window
2. Press F4 → fullscreen
3. Press F5 → "Window: Always on Top"
4. Press F4 → exit fullscreen (restore size/position)
5. Expected: Window returns to saved position, remains on top

---

### Scenario 14: Consecutive Resize
1. Open secondary window
2. Drag right edge → resize to 800×480
3. Drag bottom edge → resize to 800×600
4. Drag top-left corner → resize and move simultaneously
5. Expected: All resizes work correctly, new size maintained

---

### Scenario 15: Cursor Changes
1. Open secondary window
2. Move mouse around window:
   - Center area → default cursor (↑)
   - Top edge → vertical resize cursor (↕)
   - Left edge → horizontal resize cursor (↔)
   - Top-left corner → NW resize cursor (↖)
   - Bottom-right corner → SE resize cursor (↘)
3. Expected: Cursor changes smoothly, reflects resize capability

---

## Edge Cases

### No Image Loaded
- If no canvas image exists, SNAPSHOT mode shows empty checkerboard
- Drag/resize still work

### Minimum Size Enforcement
- Try resizing below 200×150
- Window stops at minimum size, doesn't get smaller

### Rapid F5 Cycling
- Press F5 rapidly multiple times
- Window stacking cycles correctly, no stuck state

### Drag to Off-Screen
- Drag window completely off-screen
- Window can still be dragged back (no clipping)

### Fullscreen on Small Monitor
- If using multiple monitors, F4 maximizes on current screen

---

## Verification Checklist

### Compilation & Startup
- [ ] No compilation errors
- [ ] Application starts without exceptions
- [ ] Secondary window initializes (but hidden until F1)

### F1 – Toggle
- [ ] F1 opens secondary window
- [ ] F1 closes secondary window
- [ ] Window appears centered to main app

### F2 – Mode Cycle
- [ ] F2 cycles through SNAPSHOT → LIVE_ALL → LIVE_ALL_EDIT
- [ ] Toast messages display correctly
- [ ] LIVE_ALL shows other canvas live
- [ ] LIVE_ALL_EDIT shows overlays
- [ ] SNAPSHOT shows static image

### F3 – Snapshot
- [ ] F3 captures current canvas into snapshot
- [ ] Toast "Snapshot updated" appears
- [ ] Snapshot reflects canvas state at time of F3 press

### F4 – Fullscreen
- [ ] F4 maximizes window (fullscreen)
- [ ] Toast "Fullscreen: ON" appears
- [ ] F4 restores to previous size/position
- [ ] Toast "Fullscreen: OFF" appears
- [ ] Drag/resize disabled in fullscreen mode

### F5 – Window Stacking
- [ ] F5 cycles through: Always on Top → Normal → Behind Main
- [ ] Toast messages show current state
- [ ] Always on Top: window stays above all
- [ ] Normal: window behaves normally
- [ ] Behind Main: main app comes to front

### Mouse – Drag
- [ ] Click and drag on content area → window moves
- [ ] Release → window stays at new position
- [ ] Works with any preview mode active
- [ ] Smooth and responsive

### Mouse – Resize Edges
- [ ] Top edge: resize height (↕ cursor)
- [ ] Bottom edge: resize height (↕ cursor)
- [ ] Left edge: resize width (↔ cursor)
- [ ] Right edge: resize width (↔ cursor)
- [ ] All edges enforce minimum 200×150

### Mouse – Resize Corners
- [ ] Top-left: resize width & height, window moves (↖ cursor)
- [ ] Top-right: resize width & height (↗ cursor)
- [ ] Bottom-left: resize width & height (↙ cursor)
- [ ] Bottom-right: resize width & height (↘ cursor)

### Integration
- [ ] Fullscreen + Always on Top: both features work together
- [ ] Drag + Live mode: preview updates while dragging
- [ ] Timer cleanup on app close: no hanging processes

---

## Summary of Key Bindings

| Key | Action | Visual Feedback |
|-----|--------|-----------------|
| F1 | Toggle open/close | Window appears/disappears |
| F2 | Cycle modes | Toast: "Preview: [MODE]" |
| F3 | Snapshot | Toast: "Snapshot updated" |
| F4 | Fullscreen | Toast: "Fullscreen: [ON/OFF]" |
| F5 | Window stacking | Toast: "Window: [MODE]" |
| **Mouse** | **Drag** | **Moves window** |
| **Mouse** | **Drag edges/corners** | **Resizes window** |

