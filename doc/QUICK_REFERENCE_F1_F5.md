# Quick Reference: Secondary Canvas Window (F1-F6)

## Keyboard Shortcuts

| Key | Action | Visual Feedback |
|-----|--------|-----------------|
| **F1** | Toggle window open/close | Window appears/disappears |
| **F2** | Cycle preview mode | Toast: "Preview: [SNAPSHOT/LIVE_ALL/LIVE_ALL_EDIT]" |
| **F3** | Snapshot active canvas | Toast: "Snapshot updated" |
| **F4** | Toggle fullscreen | Toast: "Fullscreen: [ON/OFF]" |
| **F5** | Cycle window stacking | Toast: "Window: [Always on Top/Normal/Behind Main]" |
| **F6** | Apply window image to Canvas II | Toast: "Image applied to Canvas II" |

---

## Mouse Controls

### Drag to Move
- Click on window content area and drag → moves window
- Works in normal and restored-from-fullscreen modes
- Disabled in fullscreen mode

### Resize Edges
- **Top/Bottom edge**: Drag to resize height
  - Cursor: ↕ (vertical double-arrow)
- **Left/Right edge**: Drag to resize width
  - Cursor: ↔ (horizontal double-arrow)

### Resize Corners
- **Top-Left corner**: Drag to resize and move window
  - Cursor: ↖ (NW diagonal)
- **Top-Right corner**: Drag to resize width, move window up
  - Cursor: ↗ (NE diagonal)
- **Bottom-Left corner**: Drag to resize height, move window left
  - Cursor: ↙ (SW diagonal)
- **Bottom-Right corner**: Drag to resize width and height
  - Cursor: ↘ (SE diagonal)

### Minimum Size
- Window cannot be resized below **200 × 150 pixels**

---

## Preview Modes (F2)

### SNAPSHOT
- Static image of **currently active** canvas
- Updated manually via F3
- No elements shown
- No live updates

### LIVE_ALL
- Shows **other** canvas (the one not active)
- Updates in real-time at ~30 FPS
- Displays all elements (ImageLayer + PathLayer)
- No element borders or overlays

### LIVE_ALL_EDIT
- Shows **other** canvas (the one not active)
- Updates in real-time at ~30 FPS
- Displays all elements (ImageLayer + PathLayer)
- **Cyan dashed borders** around all elements
- **Cyan lines + white circles** showing PathLayer control points

---

## Window Stacking Modes (F5)

### Always on Top
- Window stays above all other windows
- Clicking other windows doesn't hide secondary window
- Main app still usable behind secondary window

### Normal
- Standard window behavior
- Respects OS focus and stacking order
- Can go behind other windows
- Click secondary window to bring it to front

### Behind Main
- Main app window is brought to front
- Secondary window goes behind main app
- Soft behavior (not permanently locked to back)
- Clicking secondary doesn't force it to front

---

## Feature Interactions

### Fullscreen + Resize
- Drag/resize disabled in fullscreen mode
- Exit fullscreen (F4) to re-enable drag/resize
- Original position and size restored exactly

### Fullscreen + Stacking (F5)
- F5 works in fullscreen
- Stacking mode persists after exiting fullscreen

### LIVE_ALL + Drag/Resize
- Preview updates live even while dragging
- Smooth interaction without lag or stutter

### Mode Change + Window State
- F2 (mode change) works in all window states
- Window position/size unaffected by mode changes

---

## Tips & Tricks

1. **Inspect Details**: Use LIVE_ALL_EDIT mode to see element outlines while editing on the other canvas
2. **Quick Snapshot**: Press F3 repeatedly to capture multiple snapshots at different canvas states
3. **Always on Top for Reference**: Use "Always on Top" mode to keep secondary window visible while working in main app
4. **Large Window for Detail Work**: Resize secondary window large, enter LIVE_ALL_EDIT mode, double-click element on Canvas 0 to edit in Canvas 1 — see changes live in secondary window
5. **Fullscreen for Presentation**: F4 to fullscreen secondary for showing work to others

---

## Troubleshooting

### Window Stuck Off-Screen
- Press F1 to close window
- Press F1 to reopen (creates at default centered position)
- Or manually drag window back with mouse

### Resize Not Working
- Check if window is in fullscreen mode (F4 to exit)
- Ensure mouse is near edge/corner (8px detection zone)
- Watch cursor for resize indicator

### Live Mode Not Updating
- Ensure LIVE_ALL or LIVE_ALL_EDIT mode is active (check via F2)
- Check that other canvas has an image loaded
- Try clicking on secondary window to refresh

### Always on Top Not Working
- Click secondary window to ensure it's active
- OS might override always-on-top in some cases (e.g., Alt+Tab dialogs)
- Try toggling F5 twice to cycle back to Always on Top

---

## Default Behavior

When first opened (F1):
- **Size**: 640 × 480 pixels
- **Position**: Centered to main application window
- **Preview Mode**: SNAPSHOT
- **Window State**: Normal (not always on top, not behind main)
- **Decoration**: Undecorated (no title bar, no system chrome)

---

## Keyboard Binding Summary

```
F1 ── toggleSecondaryWindow()
F2 ── cyclePreviewMode()
F3 ── refreshSnapshot()
F4 ── toggleSecondaryFullscreen()
F5 ── cycleAlwaysOnTop()
F6 ── applySecondaryWindowToCanvas()
```

Global dispatcher (works even when secondary window has focus).

---

## F6 – Apply Window Image to Canvas II

Press **F6** to load the current image from the secondary window into Canvas II.

- Copies image based on current preview mode (SNAPSHOT or LIVE_ALL/LIVE_ALL_EDIT)
- Clears Canvas II's elements and resets zoom
- Switches to Canvas II to show the result
- Toast: "Image applied to Canvas II"

See `F6_FEATURE.md` for detailed documentation.

