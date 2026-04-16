package paint;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

/**
 * Holds all state specific to a single canvas (image editor window).
 * Multiple CanvasInstances can exist in an array, with one being active at a time.
 * This enables independent undo/redo stacks, image state, and layer hierarchies per canvas.
 */
public class CanvasInstance {

    // ── Per-canvas mode & display state ──────────────────────────────────────
    public AppMode        appMode           = AppMode.ALPHA_EDITOR;
    public boolean        showGrid          = false;

    // ── Image state ───────────────────────────────────────────────────────────
    public BufferedImage  workingImage;
    public BufferedImage  originalImage;
    public File           sourceFile;
    public boolean        hasUnsavedChanges = false;

    // ── Undo/Redo ─────────────────────────────────────────────────────────────
    public final ArrayDeque<BufferedImage>   undoStack  = new ArrayDeque<>();
    public final ArrayDeque<BufferedImage>   redoStack  = new ArrayDeque<>();
    public final Map<File, CanvasFileState>  fileCache  = new LinkedHashMap<>();

    // ── Preload Cache (for hover/browsing optimization) ───────────────────────
    public final Map<File, PreloadedFileState> preloadCache = new HashMap<>();

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

    // ── Active scene (set when a scene is loaded, null otherwise) ────────────
    public File             activeSceneFile;   // the .txt file of the currently loaded scene

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
    public TileGalleryPanel scenesPanel;     // Scene browser for Tool/Game scenes
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

    // ── Preloaded file state (for hover/browsing optimization) ────────────────
    /**
     * Preloaded and normalized image ready for instant display.
     * Image is already normalized (TYPE_INT_ARGB) and ready to be fitted to viewport.
     * When this exists in cache, fitToViewport() will use the preloaded image
     * instead of reading from disk, making the load instant without jumping.
     */
    public static class PreloadedFileState {
        public BufferedImage image;
        public long timestamp; // For cache cleanup

        public PreloadedFileState(BufferedImage img) {
            this.image = img;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isStale(long maxAgeMs) {
            return (System.currentTimeMillis() - timestamp) > maxAgeMs;
        }
    }
}
