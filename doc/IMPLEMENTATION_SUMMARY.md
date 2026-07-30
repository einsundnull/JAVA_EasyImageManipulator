# Implementation Summary: Secondary Canvas Window (F1/F2/F3)

## Overview
Implemented a floating, undecorated secondary window that displays canvas content in three preview modes, controlled via F1/F2/F3 keyboard shortcuts.

---

## Changes Made

### File: `src/paint/SelectiveAlphaEditor.java`

#### 1. **New Enum & Fields** (after line 147)
```java
private enum PreviewMode { SNAPSHOT, LIVE_ALL, LIVE_ALL_EDIT }
private JFrame        secWin;
private SecondaryPanel secPanel;
private PreviewMode   secMode    = PreviewMode.SNAPSHOT;
private BufferedImage secSnapshot;
private javax.swing.Timer secTimer;
```

#### 2. **Import Addition** (line 65)
```java
import java.awt.KeyboardFocusManager;
```

#### 3. **SecondaryPanel Inner Class** (~lines 3240–3315)
Custom JPanel that renders:
- **Checkerboard background** (16px cells)
- **Main image** scaled to fit window (aspect ratio preserved)
- **Elements** (ImageLayer + PathLayer) in LIVE_ALL/LIVE_ALL_EDIT modes
- **Element outlines** (dashed cyan borders) in LIVE_ALL_EDIT mode
- **PathLayer overlays** (cyan lines + white control points) in LIVE_ALL_EDIT mode

#### 4. **Helper Method: renderPathLayerPreview()** (~lines 3316–3339)
Renders PathLayer preview with:
- Cyan lines connecting Point3D points
- White circles at control points
- Proper scaling and offset for the preview window

#### 5. **Control Methods** (~lines 3343–3399)
- **initSecondaryWindow()**: Creates and initializes the secondary JFrame
  - Undecorated, 640×480, centered to main window
  - 33ms timer for ~30 FPS updates (LIVE_ALL/LIVE_ALL_EDIT modes)
  
- **toggleSecondaryWindow()**: Opens/closes the secondary window (F1)
  - Starts timer for live modes, stops for SNAPSHOT
  
- **cyclePreviewMode()**: Cycles through preview modes (F2)
  - SNAPSHOT → LIVE_ALL → LIVE_ALL_EDIT → SNAPSHOT
  - Shows Toast notification with current mode
  
- **refreshSnapshot()**: Takes a snapshot of active canvas (F3)
  - Flattens workingImage + all ImageLayer elements into secSnapshot
  - Shows Toast: "Snapshot updated"

#### 6. **Keyboard Setup** (in setupKeyBindings(), ~lines 3478–3488)
Global KeyEventDispatcher for F1/F2/F3:
```java
KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
    switch (e.getKeyCode()) {
        case KeyEvent.VK_F1 -> { toggleSecondaryWindow(); return true; }
        case KeyEvent.VK_F2 -> { cyclePreviewMode();      return true; }
        case KeyEvent.VK_F3 -> { refreshSnapshot();       return true; }
    }
    return false;
});
```

#### 7. **Initialization** (in initializeUI(), line 338)
```java
initSecondaryWindow();
```
Called after setupKeyBindings() in the constructor.

#### 8. **Cleanup** (in onApplicationClosing(), ~lines 395–399)
```java
if (secTimer != null && secTimer.isRunning()) {
    secTimer.stop();
}
if (secWin != null) {
    secWin.dispose();
}
```

---

## Preview Modes Explained

| Mode | Display | Updates | Elements | Borders | PathLayer |
|------|---------|---------|----------|---------|-----------|
| **SNAPSHOT** | Static image of active canvas | Manual (F3) | No | No | No |
| **LIVE_ALL** | Live other canvas | 30 FPS | Yes (ImageLayer + PathLayer) | No | No |
| **LIVE_ALL_EDIT** | Live other canvas | 30 FPS | Yes | Dashed cyan | Cyan lines + points |

---

## Key Features

1. **Undecorated Window**: No title bar, no system chrome
2. **Aspect Ratio Preservation**: Image scales to fit window while maintaining proportions
3. **Checkerboard Background**: Shows transparency/empty areas clearly
4. **Live Updates**: LIVE_ALL/LIVE_ALL_EDIT modes update at 30 FPS via Timer
5. **Global Shortcuts**: F1/F2/F3 work even when secondary window has focus (via KeyEventDispatcher)
6. **Toast Notifications**: User feedback for mode changes and snapshots
7. **Clean Shutdown**: Timer stopped, window disposed on app close

---

## Testing

See `SECONDARY_WINDOW_TEST.md` for comprehensive test scenarios covering:
- Toggle functionality (F1)
- Mode cycling (F2)
- Snapshot capture (F3)
- Live mode rendering
- Element visualization
- Window properties
- Edge cases

---

## Technical Details

### Why KeyEventDispatcher?
The secondary window might have focus. Using InputMap with `WHEN_IN_FOCUSED_WINDOW` on the main frame won't work when a different window has focus. KeyEventDispatcher is global and receives all key events regardless of focus.

### Why 33ms Timer?
- 33ms = ~30 FPS (sufficient for visual smoothness)
- Avoids excessive CPU usage
- Repaints only when timer fires (not on every canvas change)

### Point3D Support
PathLayer uses Point3D (with x, y, z coordinates). The preview renders x, y only, ignoring z (2D preview).

### Performance Considerations
- Snapshot mode: no timer running (memory/CPU efficient)
- LIVE_ALL modes: 30 FPS cap, scales image to window size (prevents massive rendering)
- Element iteration only over activeElements (not all layers)

---

## Compilation & Verification

✅ **Compilation**: `javac.exe -encoding UTF-8 -sourcepath src -d bin src/paint/*.java src/module-info.java` — Success

✅ **No Errors**: All imports resolved, no type mismatches

✅ **Ready for Testing**: Application can start and be tested manually

