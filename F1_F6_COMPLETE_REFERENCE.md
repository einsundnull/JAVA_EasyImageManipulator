# Complete Secondary Canvas Window Reference (F1-F6)

## Quick Summary

| Feature | Keyboard | Purpose |
|---------|----------|---------|
| **Toggle Window** | F1 | Open/close secondary preview window |
| **Cycle Modes** | F2 | Switch between SNAPSHOT, LIVE_ALL, LIVE_ALL_EDIT |
| **Snapshot** | F3 | Capture current canvas to snapshot |
| **Fullscreen** | F4 | Toggle fullscreen maximized view |
| **Window Stacking** | F5 | Cycle: Always on Top → Normal → Behind Main |
| **Apply to Canvas II** | F6 | Copy secondary window image to Canvas II |

---

## All Features Overview

### Window Control (F1)
- **Toggle**: F1 opens/closes secondary window
- **Size**: 640×480 (default)
- **Position**: Centered to main app
- **Decoration**: Undecorated (no title bar)

### Preview Modes (F2)
- **SNAPSHOT**: Static image of active canvas (updated via F3)
- **LIVE_ALL**: Live streaming of other canvas
- **LIVE_ALL_EDIT**: Live with element borders + PathLayer overlays

### Snapshot Management (F3)
- Captures current active canvas (workingImage + ImageLayer elements flattened)
- Used by SNAPSHOT mode
- Manual trigger (not automatic)

### Fullscreen (F4)
- **Toggle**: F4 maximizes/restores window
- **State saved**: Original position and size restored exactly
- **Drag/Resize**: Disabled in fullscreen

### Window Stacking (F5)
- **Always on Top**: Window stays above all others
- **Normal**: Standard window behavior
- **Behind Main**: Main app forced to front

### Canvas II Import (F6)
- **Copy image**: From secondary window to Canvas II (index 1)
- **Based on mode**: SNAPSHOT or LIVE_ALL source
- **Clear state**: Canvas II elements/undo/zoom reset
- **Switch view**: Automatically shows Canvas II after import

---

## Mouse Controls

### Drag to Move
- Click + drag on window content → moves window
- Disabled in fullscreen
- Smooth real-time tracking

### Resize by Edge
- **Top/Bottom**: Vertical resize (↕ cursor)
- **Left/Right**: Horizontal resize (↔ cursor)
- Minimum: 200×150 pixels

### Resize by Corner
- **4 corners**: Two-axis resize (↖ ↗ ↙ ↘ cursors)
- **Top-left**: Resize + move window up/left
- **Top-right**: Resize + move window up/right
- **Bottom-left**: Resize + move window left
- **Bottom-right**: Resize + move window (no move)

---

## State Management

### Fullscreen State
- Original position/size saved before entering fullscreen
- Restored exactly when exiting fullscreen
- Saved in: `secOldX`, `secOldY`, `secOldW`, `secOldH`

### Always-on-Top State
- Cycles through 3 modes via enum `AlwaysOnTopMode`
- Applied via `JFrame.setAlwaysOnTop()`
- "Behind Main" is soft (one-time toFront() on main app)

### Preview Mode
- Stored in `secMode` enum
- Cycles: SNAPSHOT → LIVE_ALL → LIVE_ALL_EDIT → SNAPSHOT
- Affects:
  - What F3 does (only meaningful in SNAPSHOT)
  - What F6 copies (image source)
  - What is rendered (element borders only in EDIT mode)

### Snapshot
- Stored in `secSnapshot` BufferedImage
- Updated via F3
- Flattens workingImage + all ImageLayer elements
- Used as fallback in SNAPSHOT mode if workingImage unavailable

---

## Workflow Examples

### Example 1: Compare Two Canvas States
1. **Setup**: Load image on Canvas I, open secondary (F1)
2. **Capture**: Press F3 to snapshot Canvas I state
3. **Swap**: Press F6 to apply snapshot to Canvas II
4. **Result**: Canvas II has snapshot of Canvas I, both visible side-by-side for comparison

### Example 2: Live Monitor Other Canvas
1. **Load**: Image A on Canvas I, Image B on Canvas II
2. **Monitor**: Open secondary (F1), press F2 to enter LIVE_ALL
3. **Secondary shows**: Canvas I (the "other" one)
4. **Edit**: Make changes on Canvas I, see them live in secondary
5. **Track**: Press F2 again to enter LIVE_ALL_EDIT, see element outlines

### Example 3: Always-on-Top Reference
1. **Reference**: Open secondary in LIVE_ALL mode (F1, F2)
2. **Always visible**: Press F5 to enter "Always on Top"
3. **Work**: Click main app, edit Canvas II, secondary stays visible
4. **Reference**: See Canvas I changes in real-time while editing Canvas II
5. **Copy when ready**: Press F6 to import Canvas I to Canvas II

### Example 4: Fullscreen Presentation
1. **Prepare**: Load image on Canvas I
2. **Display**: Open secondary (F1), enter LIVE_ALL mode (F2)
3. **Maximize**: Press F4 for fullscreen
4. **Show**: Present large view of Canvas I to others
5. **Normal**: Press F4 to exit fullscreen when done

### Example 5: Window Customization
1. **Resize**: Drag edges to make window large (e.g., 1000×750)
2. **Position**: Drag window to specific location on screen
3. **Persist**: Position/size maintained during session
4. **Note**: Not saved on app exit (resets each launch)

---

## Technical Implementation

### Classes & Methods Added

**New Enums:**
- `PreviewMode`: SNAPSHOT, LIVE_ALL, LIVE_ALL_EDIT
- `AlwaysOnTopMode`: TO_FRONT, NORMAL, TO_BACKGROUND

**New Fields:**
```java
private JFrame        secWin;
private SecondaryPanel secPanel;
private PreviewMode   secMode;
private BufferedImage secSnapshot;
private javax.swing.Timer secTimer;
private boolean       secFullscreen;
private AlwaysOnTopMode secAlwaysOnTop;
private int           secOldX, secOldY, secOldW, secOldH;
```

**New Inner Class:**
- `SecondaryPanel extends JPanel` (rendering + mouse handling)
  - Handles paintComponent (rendering checkerboard, image, elements)
  - Handles mouse drag (window movement)
  - Handles mouse resize (edge/corner detection)

**New Methods:**
- `initSecondaryWindow()` — Initialize window and panel
- `toggleSecondaryWindow()` — F1 handler
- `cyclePreviewMode()` — F2 handler
- `refreshSnapshot()` — F3 handler
- `toggleSecondaryFullscreen()` — F4 handler
- `cycleAlwaysOnTop()` — F5 handler
- `applySecondaryWindowToCanvas()` — F6 handler
- `renderPathLayerPreview()` — Helper for PathLayer rendering
- `getResizeEdgeAt()` — Helper for resize detection
- `updateCursor()` — Helper for cursor feedback

**Global Dispatcher:**
- One `KeyEventDispatcher` handles F1-F6 globally
- Works even when secondary window has focus

---

## Performance

### Timer Management
- 30 FPS repaint timer for LIVE_ALL modes
- Timer stopped in SNAPSHOT mode
- Timer stopped on window close
- Clean shutdown on app exit

### Memory
- Snapshot stored once (BufferedImage)
- Deep copies used to prevent reference corruption
- Old position/size stored only during fullscreen

### Rendering
- Image scaled to fit window (aspect ratio preserved)
- Checkerboard background (16px cells)
- Elements rendered only in LIVE modes

---

## Troubleshooting

### Window Off-Screen
- **Fix**: F1 to close, F1 to reopen (resets to center)

### Resize Not Working
- **Check**: Not in fullscreen? (F4 to exit)
- **Check**: Mouse near edge? (8px detection zone)
- **Check**: Cursor changed? (Visual indicator)

### Snapshot Not Available
- **Cause**: Never pressed F3, or in LIVE mode
- **Fix**: Switch to SNAPSHOT mode (F2), press F3

### Can't Move Window in Fullscreen
- **Expected**: Drag disabled in fullscreen
- **Fix**: Press F4 to exit fullscreen, then drag

### Always on Top Not Working
- **OS Override**: Some systems override alwaysOnTop
- **Try**: Toggle F5 to cycle through modes

---

## Integration Points

### With Main Canvas
- Secondary shows "other" canvas (1 - activeCanvasIndex)
- F6 imports to Canvas II (always index 1)
- Does not affect Canvas I state (except indirect via UI)

### With Elements Layer
- Element borders shown only in LIVE_ALL_EDIT
- PathLayer overlays (cyan lines + white points) shown in EDIT mode
- Elements not copied with F6 (Canvas II starts empty)

### With Undo/Redo
- Undo/redo independent per canvas
- F6 clears Canvas II's undo/redo stacks

### With File Operations
- Secondary window position/size not saved
- Window settings not persisted
- Resets to default on app restart

---

## Keyboard Binding Table

```
┌─────────────────────────────────────────────────────┐
│ F-Key Binding Reference                             │
├───────────────────────────────────────────────────────┤
│ F1 ── toggleSecondaryWindow()      (open/close)      │
│ F2 ── cyclePreviewMode()           (mode cycle)      │
│ F3 ── refreshSnapshot()            (capture)         │
│ F4 ── toggleSecondaryFullscreen()  (fullscreen)      │
│ F5 ── cycleAlwaysOnTop()           (stacking)        │
│ F6 ── applySecondaryWindowToCanvas() (import)        │
└───────────────────────────────────────────────────────┘
```

All via global `KeyboardFocusManager.addKeyEventDispatcher()`.

---

## Feature Checklist

- ✅ Window toggle (F1)
- ✅ Preview mode cycling (F2)
- ✅ Snapshot capture (F3)
- ✅ Fullscreen toggle (F4)
- ✅ Window stacking control (F5)
- ✅ Canvas II import (F6)
- ✅ Mouse drag (move window)
- ✅ Mouse resize (edges + corners)
- ✅ Cursor feedback
- ✅ Toast notifications
- ✅ Clean shutdown
- ✅ Comprehensive documentation

---

## Files Generated

1. `QUICK_REFERENCE_F1_F5.md` — One-page cheat sheet
2. `F6_FEATURE.md` — Detailed F6 documentation
3. `EXTENDED_IMPLEMENTATION_SUMMARY.md` — Technical details F1-F5
4. `F1_F6_COMPLETE_REFERENCE.md` — This file (complete overview)

---

## Next Steps / Future Enhancements

Possible future additions:
- [ ] Save window position/size to settings
- [ ] Snapshot preview in secondary window title
- [ ] Copy elements from other canvas (F7?)
- [ ] Paste Canvas II back to Canvas I (F8?)
- [ ] Borderless fullscreen mode
- [ ] Custom window sizes / snap-to-grid
- [ ] Window transparency control
- [ ] Freeze mode (pause live updates)

