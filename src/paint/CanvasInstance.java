package paint;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.Timer;

/**
 * Holds all state specific to a single canvas (image editor window).
 * Multiple CanvasInstances can exist in an array, with one being active at a time.
 * This enables independent undo/redo stacks, image state, and layer hierarchies per canvas.
 */
public class CanvasInstance {

    // ── Image state ───────────────────────────────────────────────────────────
    public BufferedImage  workingImage;
    public BufferedImage  originalImage;
    public File           sourceFile;
    public boolean        hasUnsavedChanges = false;

    // ── Undo/Redo ─────────────────────────────────────────────────────────────
    public final ArrayDeque<BufferedImage>   undoStack  = new ArrayDeque<>();
    public final ArrayDeque<BufferedImage>   redoStack  = new ArrayDeque<>();
    public final Map<File, CanvasFileState>  fileCache  = new LinkedHashMap<>();

    // ── Layers / Elements ─────────────────────────────────────────────────────
    public List<Layer>    activeElements   = new ArrayList<>();
    public List<Layer>    selectedElements = new ArrayList<>();
    public int            nextElementId    = 1;
    public boolean        draggingElement  = false;
    public Point          elemDragAnchor;
    public int            elemActiveHandle = -1;
    public Rectangle      elemScaleBase;
    public Point          elemScaleStart;
    public boolean        insertAsElement  = false;

    // ── Floating selection ────────────────────────────────────────────────────
    public BufferedImage  floatingImg;
    public Rectangle      floatRect;
    public boolean        isDraggingFloat  = false;
    public Point          floatDragAnchor;
    public int            activeHandle     = -1;
    public Rectangle      scaleBaseRect;
    public Point          scaleDragStart;

    // ── Alpha-editor selection ────────────────────────────────────────────────
    public boolean        isSelecting      = false;
    public Point          selectionStart;
    public Point          selectionEnd;
    public List<Rectangle> selectedAreas   = new ArrayList<>();

    // ── Paint mode state ──────────────────────────────────────────────────────
    public Point          lastPaintPoint;
    public Point          shapeStartPoint;
    public BufferedImage  paintSnapshot;

    // ── Zoom ──────────────────────────────────────────────────────────────────
    public double         zoom             = 1.0;
    public boolean        userHasManuallyZoomed = false;
    public double         zoomTarget       = 1.0;
    public Point2D        zoomImgPt;
    public Point          zoomVpMouse;
    public Timer          zoomTimer;

    // ── Directory navigation ──────────────────────────────────────────────────
    public List<File>     directoryImages  = new ArrayList<>();
    public int            currentImageIndex = -1;
    public File           lastIndexedDir;

    // ── UI Components ─────────────────────────────────────────────────────────
    public CanvasPanel      canvasPanel;
    public JPanel           canvasWrapper;   // null-layout, centres canvasPanel
    public JScrollPane      scrollPane;
    public JPanel           viewportPanel;   // BorderLayout: ruler + scrollPane
    public JLayeredPane     layeredPane;
    public JPanel           dropHintPanel;
    public TileGalleryPanel tileGallery;
    public JButton          prevNavButton;
    public JButton          nextNavButton;
    public JPanel           elementEditBar;  // floating action bar shown when editing a layer

    // ── Inner file-cache entry ────────────────────────────────────────────────
    public static class CanvasFileState {
        public BufferedImage image;
        public final ArrayDeque<BufferedImage> undoStack = new ArrayDeque<>();
        public final ArrayDeque<BufferedImage> redoStack = new ArrayDeque<>();
        public final List<Layer> elements = new ArrayList<>();

        public CanvasFileState(BufferedImage img) {
            this.image = img;
        }
    }
}
