package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Main application window.
 *
 * Modes:
 *  - Alpha Editor  (Selective Selection + Floodfill)
 *  - Paint Mode    (full MS-Paint-style toolbar via PaintToolbar)
 *
 * Navigation:
 *  - CTRL + Mouse Wheel  → Zoom (toward cursor)
 *  - Mouse Wheel         → Vertical scroll
 *  - SHIFT + Wheel       → Horizontal scroll
 *  - Middle Mouse Drag   → Pan
 *  - CTRL + Left Drag    → Pan
 *
 * Ruler:
 *  - Drawn OUTSIDE the image in dedicated panels (H top, V left)
 *  - Configurable unit: px / mm / cm / inch
 */
public class SelectiveAlphaEditor2 extends JFrame implements CanvasCallbacks, RulerCallbacks {

    private static final long serialVersionUID = 1L;
	// ── Constants ─────────────────────────────────────────────────────────────
    private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
    private static final int    MAX_UNDO  = 50;

    // Zoom settings (non-final for runtime adjustment)
    private double ZOOM_MIN  = 0.05;
    private double ZOOM_MAX  = 16.0;        // max: 16x16 pixels
    private double ZOOM_STEP = 0.10;
    private double ZOOM_FACTOR = 1.08;      // progressive zoom: 8% per notch

//    private static final int GRID_CELL    = 16;   // image-space pixels per grid cell
    private static final int RULER_THICK  = 20;   // pixels wide/tall for ruler strip
//    private static final double SCREEN_DPI = 96.0;

    private static final int TOPBAR_BTN_W      = 36;
    private static final int TOPBAR_BTN_H      = 36;
    private static final int TOPBAR_ZOOM_BTN_W = 36;
    private static final int TOPBAR_ZOOM_BTN_H = 36;

    // ── Ruler unit ────────────────────────────────────────────────────────────
    // RulerUnit is now defined in RulerUnit.java (extracted as separate enum)

    // ── Per-file canvas state (cached while switching images) ─────────────────
    private static final class CanvasState {
        BufferedImage               image;
        final ArrayDeque<BufferedImage> undoStack = new ArrayDeque<>();
        final ArrayDeque<BufferedImage> redoStack = new ArrayDeque<>();
        final List<Layer>           elements  = new ArrayList<>();
        CanvasState(BufferedImage img) { this.image = img; }
    }

    // ── Application mode ──────────────────────────────────────────────────────
    // AppMode is now defined in AppMode.java (extracted as separate enum)

    // ── State ─────────────────────────────────────────────────────────────────
    private BufferedImage originalImage;
    private BufferedImage workingImage;
    private BufferedImage clipboard;
    private Point         pasteOffset;
    private File          sourceFile;

    private AppMode  appMode           = AppMode.ALPHA_EDITOR;
    private boolean  floodfillMode     = false;
    private boolean  hasUnsavedChanges = false;
    private boolean  showGrid          = false;
    private boolean  showRuler         = false;
    private RulerUnit rulerUnit        = RulerUnit.PX;

    private double zoom = 1.0;
    private boolean userHasManuallyZoomed = false; // true after any user-driven zoom change

    // Smooth zoom animation
//    private double       zoomTarget   = 1.0;  // animation target zoom level
//    private Point2D      zoomImgPt    = null; // image-space pixel to keep fixed during animation
//    private Point        zoomVpMouse  = null; // mouse position in viewport coords (stays fixed)
//    private javax.swing.Timer zoomTimer = null; // timer driving the animation

    // Alpha-editor selection (image-space)
    private boolean          isSelecting    = false;
    private Point            selectionStart = null;
    private Point            selectionEnd   = null;
    private List<Rectangle>  selectedAreas  = new ArrayList<>();

    // Paint-mode stroke tracking (image-space)
    private Point lastPaintPoint  = null;
    private Point shapeStartPoint = null;
    private BufferedImage paintSnapshot = null;

    // Directory browsing
    private List<File> directoryImages   = new ArrayList<>();
    private int        currentImageIndex = -1;
    private File       lastIndexedDir    = null;
    private List<File> selectedImages    = new ArrayList<>();

    // Undo / Redo (stack front = most recent)
    private final ArrayDeque<BufferedImage> undoStack = new ArrayDeque<>();
    private final ArrayDeque<BufferedImage> redoStack = new ArrayDeque<>();

    // ── File cache (images stay alive while navigating, dirty until saved) ────
    /** LRU-ish cache: key = canonical file, value = per-file canvas state.     */
    private final Map<File, CanvasState> fileStateCache = new LinkedHashMap<>();
    /** Files with unsaved changes (shown red in gallery).                        */
    private final Set<File> dirtyFiles = new HashSet<>();

    // ── Project Management ────────────────────────────────────────────────────
    private ProjectManager projectManager = new ProjectManager();

    // ── NEW: State Managers (Modularization) ───────────────────────────────────
    private ZoomState              zoomState              = new ZoomState();
    private FloatSelectionState    floatSelectionState    = new FloatSelectionState();
    private ElementLayerState      elementLayerState      = new ElementLayerState();
    private FileStateCache         fileCacheManager       = new FileStateCache();
    private UndoRedoManager        undoRedoManager        = new UndoRedoManager(MAX_UNDO);
    private ClipboardOperationHandler clipboardHandler    = new ClipboardOperationHandler();
    private ImageTransformHandler  transformHandler       = new ImageTransformHandler();

    // ── Element layers ────────────────────────────────────────────────────────
    /** Layers attached to the *current* image (alias into fileStateCache).       */
    private List<Layer>  activeElements  = new ArrayList<>();
    private int          nextElementId   = 1;
    /** Currently selected layers (index 0 = primary for handles/resize).         */
    private List<Layer>  selectedElements = new ArrayList<>();
    private boolean        draggingElement = false;
    private Point          elemDragAnchor  = null;
    private int            elemActiveHandle = -1;
    private Rectangle      elemScaleBase    = null;
    private Point          elemScaleStart   = null;
    /** True while "Einfügen als Element"-mode is active.                        */
    private boolean        insertAsElement  = false;

    // ── Canvas background ─────────────────────────────────────────────────────
    private Color canvasBg1 = new Color(200, 200, 200);
    private Color canvasBg2 = new Color(160, 160, 160);

    // ── Floating selection (SELECT tool – move & scale) ───────────────────────
    private BufferedImage floatingImg     = null;
    private Rectangle     floatRect       = null;
    private boolean       isDraggingFloat = false;
    private Point         floatDragAnchor = null;
    private int           activeHandle    = -1;
    private Rectangle     scaleBaseRect   = null;
    private Point         scaleDragStart  = null;

    // ── Filmstrip sidebar ─────────────────────────────────────────────────────
    private JPanel        galleryWrapper;
    private JToggleButton filmstripBtn;

    // ── Element layer panel (right sidebar, shown in Canvas mode) ─────────────
    private ElementLayerPanel elementLayerPanel;

    // ── UI references ─────────────────────────────────────────────────────────
    private CanvasPanel       canvasPanel;
    private TileGalleryPanel  tileGallery;
    private JPanel        canvasWrapper;   // null-layout, centres canvasPanel
    private JScrollPane   scrollPane;
    private HRulerPanel   hRuler;
    private VRulerPanel   vRuler;
    private JPanel        rulerCorner;
    private JPanel        rulerNorthBar;   // container for rulerCorner + hRuler
    private JPanel        viewportPanel;   // BorderLayout: ruler + scrollPane
    private JPanel        dropHintPanel;
    private JLayeredPane  layeredPane;
    private JPanel        actionPanel;    // Holds apply/clear/reset/save buttons

    private JLabel  statusLabel;
    private JLabel  modeLabel;
    private JLabel  zoomLabel;
    private JButton applyButton;
    private JButton clearSelectionsButton;
    private JButton prevNavButton;
    private JButton nextNavButton;
    private JToggleButton paintModeBtn;
    private JToggleButton canvasModeBtn;

    private PaintToolbar paintToolbar;

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SelectiveAlphaEditor2::new);
    }

    // =========================================================================
    // Constructors
    // =========================================================================
    public SelectiveAlphaEditor2() { initializeUI(); }

    public SelectiveAlphaEditor2(File imageFile, boolean floodfillMode) {
        this.floodfillMode = floodfillMode;
        initializeUI();
        loadFile(imageFile);
    }

    // =========================================================================
    // UI construction
    // =========================================================================
    private void initializeUI() {
        setTitle("Selective Alpha Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(AppColors.BG_DARK);
        setLayout(new BorderLayout());

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        setMinimumSize(new Dimension(900, 650));
        pack();

        // Initialize state managers with UI component references (all UI components now exist)
        initializeStateManagers();

        // Lade Settings und stelle Fensterposition wieder her
        try {
            AppSettings.load();
            AppSettings settings = AppSettings.getInstance();

            // Fensterposition
            setLocation(settings.getWindowX(), settings.getWindowY());
            setSize(settings.getWindowWidth(), settings.getWindowHeight());
            if (settings.isWindowMaximized()) {
                setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

            // Canvas-Farben
            canvasBg1 = new Color(settings.getBg1());
            canvasBg2 = new Color(settings.getBg2());

            // View-Optionen
            showGrid = settings.isShowGrid();
            showRuler = settings.isShowRuler();
            rulerUnit = RulerUnit.valueOf(settings.getRulerUnit());

            // Zoom-Einstellungen
            ZOOM_MIN = settings.getZoomMin();
            ZOOM_MAX = settings.getZoomMax();
            ZOOM_STEP = settings.getZoomStep();
            ZOOM_FACTOR = settings.getZoomFactor();

            // App-Modus
            try {
                appMode = AppMode.valueOf(settings.getAppMode());
            } catch (IllegalArgumentException e) {
                appMode = AppMode.ALPHA_EDITOR;
            }

            // PaintToolbar-Einstellungen
            if (paintToolbar != null) {
                paintToolbar.setPrimaryColor(new Color(settings.getPrimaryColor(), true));
                paintToolbar.setSecondaryColor(new Color(settings.getSecondaryColor(), true));
                paintToolbar.setStrokeWidth(settings.getStrokeWidth());
                paintToolbar.setAntialiasing(settings.isAntialias());
                try {
                    paintToolbar.setFillMode(settings.getFillMode());
                    paintToolbar.setBrushShape(settings.getBrushShape());
                    paintToolbar.setActiveTool(settings.getActiveTool());
                } catch (Exception e) {
                    System.err.println("[WARN] Fehler beim Restore von Paint-Einstellungen: " + e.getMessage());
                }
            }

            // Text-Tool-Einstellungen
            if (canvasPanel != null) {
                canvasPanel.setTextFontName(settings.getFontName());
                canvasPanel.setTextFontSize(settings.getFontSize());
                canvasPanel.setTextBold(settings.isTextBold());
                canvasPanel.setTextItalic(settings.isTextItalic());
                canvasPanel.setTextColor(new Color(settings.getFontColor(), true));
            }

        } catch (IOException e) {
            System.err.println("[WARN] Fehler beim Laden der Einstellungen: " + e.getMessage());
            setLocationRelativeTo(null); // Fallback
        }

        setupKeyBindings();

        // WindowListener für State-Change (Resize/Maximize) und Schließen
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                onApplicationClosing();
            }
        });

        addWindowStateListener((WindowEvent e) -> {
            boolean wasMax = (e.getOldState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
            boolean isMax  = (e.getNewState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
            if (wasMax != isMax && workingImage != null) {
                SwingUtilities.invokeLater(() -> {
                    if (!userHasManuallyZoomed) {
                        fitToViewport();
                    } else {
                        centerCanvasX();
                    }
                    if (canvasPanel != null) canvasPanel.repaint();
                });
            }
        });

        setVisible(true);
    }

    private void onApplicationClosing() {
        try {
            // Speichere aktuelle Szene
            if (sourceFile != null && workingImage != null) {
                projectManager.saveScene(sourceFile, activeElements, zoom, appMode);
            }

            // Speichere globale Einstellungen
            AppSettings settings = AppSettings.getInstance();
            settings.setBg1(canvasBg1.getRGB());
            settings.setBg2(canvasBg2.getRGB());
            settings.setShowGrid(showGrid);
            settings.setShowRuler(showRuler);
            settings.setRulerUnit(rulerUnit.toString());
            settings.setAppMode(appMode.toString());
            settings.setZoomMin(ZOOM_MIN);
            settings.setZoomMax(ZOOM_MAX);
            settings.setZoomStep(ZOOM_STEP);
            settings.setZoomFactor(ZOOM_FACTOR);

            // Fensterposition
            settings.setWindowX(getX());
            settings.setWindowY(getY());
            settings.setWindowWidth(getWidth());
            settings.setWindowHeight(getHeight());
            settings.setWindowMaximized((getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH);

            // PaintToolbar
            if (paintToolbar != null) {
                settings.setPrimaryColor(paintToolbar.getPrimaryColor().getRGB());
                settings.setSecondaryColor(paintToolbar.getSecondaryColor().getRGB());
                settings.setStrokeWidth(paintToolbar.getStrokeWidth());
                settings.setAntialias(paintToolbar.isAntialiasing());
                settings.setActiveTool(paintToolbar.getActiveTool().toString());
                settings.setFillMode(paintToolbar.getFillMode().toString());
                settings.setBrushShape(paintToolbar.getBrushShape().toString());
            }

            // Text-Tool
            if (canvasPanel != null) {
                settings.setFontName(canvasPanel.getTextFontName());
                settings.setFontSize(canvasPanel.getTextFontSize());
                settings.setTextBold(canvasPanel.isTextBold());
                settings.setTextItalic(canvasPanel.isTextItalic());
                settings.setFontColor(canvasPanel.getTextColor().getRGB());
            }

            // Speichern
            settings.save();
        } catch (IOException e) {
            System.err.println("[ERROR] Fehler beim Speichern der Einstellungen: " + e.getMessage());
        }

        // Normal beenden
        System.exit(0);
    }

    /**
     * Initialize state managers with UI component references.
     * Called after pack() when all UI components are created.
     */
    private void initializeStateManagers() {
        // ZoomState: provide UI component references it needs
        zoomState.setCanvasWrapper(canvasWrapper);
        zoomState.setScrollPane(scrollPane);
        zoomState.setZoomLabel(zoomLabel);
        zoomState.setWorkingImage(workingImage);

        // FloatSelectionState: provide coordinate transformation context
        floatSelectionState.setZoom(zoom);
        floatSelectionState.setWorkingImage(workingImage);

        // ElementLayerState: provide UI and callback hooks
        elementLayerState.setWorkingImage(workingImage);
        elementLayerState.setAppMode(appMode);
        elementLayerState.setElementLayerPanel(elementLayerPanel);
        elementLayerState.setCanvasPanel(canvasPanel);
        elementLayerState.setOnUndo(() -> pushUndo());
        elementLayerState.setOnMarkDirty(() -> markDirty());

        // FileStateCache: provide project manager reference
        fileCacheManager.setProjectManager(projectManager);
        fileCacheManager.setOnDirectoryIndexed(() -> updateNavigationButtons());

        // UndoRedoManager: set callback for dirty flag and UI updates
        undoRedoManager.setOnUndoRedoComplete(this::afterUndoRedo);

        // ClipboardOperationHandler: provide context references
        clipboardHandler.setWorkingImage(workingImage);

        // ImageTransformHandler: provide context references
        transformHandler.setWorkingImage(workingImage);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

        // ── LEFT: filmstrip toggle + mode + status labels ──────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        left.setOpaque(false);

        // \u2261 (≡) is in Basic Multilingual Plane — safe on all Windows systems
        filmstripBtn = UIComponentFactory.buildModeToggleBtn("\u2261", "Filmstreifen ein-/ausblenden");
        filmstripBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        filmstripBtn.setSelected(true);
        filmstripBtn.addActionListener(e -> {
            tileGallery.setVisible(filmstripBtn.isSelected());
            if (galleryWrapper != null) { galleryWrapper.revalidate(); galleryWrapper.repaint(); }
            centerCanvas();
        });

        modeLabel = new JLabel("Modus: Selective Alpha");
        modeLabel.setForeground(AppColors.TEXT_MUTED);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        statusLabel = new JLabel("Keine Datei geladen");
        statusLabel.setForeground(AppColors.TEXT_MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        left.add(filmstripBtn);
        left.add(modeLabel);
        left.add(statusLabel);
        bar.add(left, BorderLayout.WEST);

        // ── RIGHT: all action + layer + file + mode + zoom buttons ─────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 5));
        right.setOpaque(false);

        // — Selection/alpha actions (formerly statusBar) —
        // applyButton and clearSelectionsButton are fields accessed by setBottomButtonsEnabled()
        applyButton = UIComponentFactory.buildButton("\u2713", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        applyButton.setForeground(Color.WHITE);
        applyButton.setToolTipText("Auswahl auf Alpha anwenden");
        applyButton.addActionListener(e -> applySelectionsToAlpha());
        applyButton.setEnabled(false);
        applyButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        clearSelectionsButton = UIComponentFactory.buildButton("\u2715", AppColors.BTN_BG, AppColors.BTN_HOVER);
        clearSelectionsButton.setToolTipText("Auswahl löschen");
        clearSelectionsButton.addActionListener(e -> clearSelections());
        clearSelectionsButton.setEnabled(false);
        clearSelectionsButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        // — Non-destructive layer buttons (moved from statusBar) —
        // \u2295 (⊕) = circled plus  \u229e (⊞) = squared plus — both BMP, safe
        JButton insertElemBtn = UIComponentFactory.buildButton("\u2295", AppColors.BTN_BG, AppColors.BTN_HOVER);
        insertElemBtn.setToolTipText("Als nicht-destruktiven Layer einfügen (ENTER=zusammenführen, DEL=löschen)");
        insertElemBtn.addActionListener(e -> insertSelectionAsElement());
        insertElemBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        JButton mergeElemBtn = UIComponentFactory.buildButton("\u229e", AppColors.BTN_BG, AppColors.BTN_HOVER);
        mergeElemBtn.setToolTipText("Ausgewähltes Element auf Canvas rendern (ENTER)");
        mergeElemBtn.addActionListener(e -> { if (!selectedElements.isEmpty()) mergeSelectedElements(); });
        mergeElemBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        // — File/image operations (formerly statusBar) —
        // actionPanel is a field used by setBottomButtonsEnabled() to find resetButton/saveButton by name
        actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actionPanel.setOpaque(false);

        JButton newBitmapBtn = UIComponentFactory.buildButton("Neu", AppColors.BTN_BG, AppColors.BTN_HOVER);
        newBitmapBtn.setToolTipText("Neue leere Bitmap erstellen");
        newBitmapBtn.addActionListener(e -> doNewBitmap());
        newBitmapBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        JButton bgColorBtn = UIComponentFactory.buildButton("BG", AppColors.BTN_BG, AppColors.BTN_HOVER);
        bgColorBtn.setToolTipText("Canvas-Hintergrundfarbe einstellen");
        bgColorBtn.addActionListener(e -> showCanvasBgDialog());
        bgColorBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        // \u21ba (↺) = anticlockwise arrow — BMP, safe
        JButton resetButton = UIComponentFactory.buildButton("\u21ba", AppColors.BTN_BG, AppColors.BTN_HOVER);
        resetButton.setName("resetButton");
        resetButton.setToolTipText("Bild zurücksetzen");
        resetButton.addActionListener(e -> resetImage());
        resetButton.setEnabled(false);
        resetButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        JButton saveButton = UIComponentFactory.buildButton("Save", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
        saveButton.setName("saveButton");
        saveButton.setForeground(Color.WHITE);
        saveButton.setToolTipText("Bild speichern (STRG+S)");
        saveButton.addActionListener(e -> saveImage());
        saveButton.setEnabled(false);
        saveButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

        actionPanel.add(resetButton);
        actionPanel.add(saveButton);

        // — Mode toggle buttons —
        // \u25a1 (□) = white square  \u270f (✏) = pencil — both BMP, safe
        canvasModeBtn = UIComponentFactory.buildModeToggleBtn("\u25a6", "Canvas-Modus: Layer-Verwaltung – nur im Paint-Modus (STRG+A = Alle auswählen)");
        canvasModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        canvasModeBtn.setEnabled(false); // enabled only while Paint mode is active
        canvasModeBtn.addActionListener(e -> toggleCanvasMode());

        paintModeBtn = UIComponentFactory.buildModeToggleBtn("\u270f", "Paint-Modus aktivieren / deaktivieren");
        paintModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        paintModeBtn.addActionListener(e -> togglePaintMode());

        // — Zoom controls —
        JButton zoomOutBtn   = UIComponentFactory.buildButton("\u2212", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomInBtn    = UIComponentFactory.buildButton("+",      AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomResetBtn = UIComponentFactory.buildButton("1:1",   AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomFitBtn   = UIComponentFactory.buildButton("Fit",   AppColors.BTN_BG, AppColors.BTN_HOVER);

        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(AppColors.TEXT_MUTED);
        zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        zoomLabel.setPreferredSize(new Dimension(46, 20));
        zoomLabel.setHorizontalAlignment(JLabel.RIGHT);
        zoomLabel.setToolTipText("Doppelklick: Zoom direkt eingeben");
        zoomLabel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        zoomLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showZoomInput();
            }
        });

        zoomOutBtn  .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomInBtn   .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomResetBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomFitBtn  .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomOutBtn  .addActionListener(e -> setZoom(zoom - ZOOM_STEP, null));
        zoomInBtn   .addActionListener(e -> setZoom(zoom + ZOOM_STEP, null));
        zoomResetBtn.addActionListener(e -> setZoom(1.0, null));
        zoomFitBtn  .addActionListener(e -> fitToViewport());

        // — Assemble right panel (left-to-right order in FlowLayout.RIGHT = right-to-left visual) —
        right.add(applyButton);
        right.add(clearSelectionsButton);
        right.add(Box.createHorizontalStrut(4));
        right.add(insertElemBtn);
        right.add(mergeElemBtn);
        right.add(Box.createHorizontalStrut(4));
        right.add(newBitmapBtn);
        right.add(bgColorBtn);
        right.add(actionPanel);
        right.add(Box.createHorizontalStrut(4));
        right.add(canvasModeBtn);
        right.add(paintModeBtn);
        right.add(Box.createHorizontalStrut(4));
        right.add(zoomOutBtn);
        right.add(zoomLabel);
        right.add(zoomInBtn);
        right.add(zoomResetBtn);
        right.add(zoomFitBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── Center: ruler panels + scrollPane ────────────────────────────────────
    private JPanel buildCenter() {
        dropHintPanel = buildDropHintPanel();
        setupDropTarget(dropHintPanel);

        // Canvas panel (the actual drawing surface)
        canvasPanel = new CanvasPanel(this);

        // Wrapper that centres canvasPanel when image < viewport
        canvasWrapper = new JPanel(null) {
            private static final long serialVersionUID = 1L;
			@Override public Dimension getPreferredSize() {
                if (workingImage == null) return new Dimension(1, 1);
                int cw = (int) Math.ceil(workingImage.getWidth()  * zoom);
                int ch = (int) Math.ceil(workingImage.getHeight() * zoom);
                Dimension vd = scrollPane != null ? scrollPane.getViewport().getSize()
                                                  : new Dimension(cw, ch);
                return new Dimension(Math.max(cw, vd.width), Math.max(ch, vd.height));
            }
            @Override public void doLayout() {
                if (canvasPanel == null) return;
                Dimension cs = canvasPanel.getPreferredSize();
                Dimension ws = getSize();
                int x = Math.max(0, (ws.width  - cs.width)  / 2);
                int y = Math.max(0, (ws.height - cs.height) / 2);
                canvasPanel.setBounds(x, y, cs.width, cs.height);
            }
        };
        canvasWrapper.setBackground(AppColors.BG_DARK);
        canvasWrapper.setOpaque(true);
        canvasWrapper.add(canvasPanel);

        // ScrollPane wrapping the canvas wrapper
        scrollPane = new JScrollPane(canvasWrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppColors.BG_DARK);
        scrollPane.setBackground(AppColors.BG_DARK);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        TileGalleryPanel.applyDarkScrollBar(scrollPane.getVerticalScrollBar());
        TileGalleryPanel.applyDarkScrollBar(scrollPane.getHorizontalScrollBar());

        // Ruler panels (redrawn on scroll change)
        hRuler     = new HRulerPanel(this);
        vRuler     = new VRulerPanel(this);
        rulerCorner = new JPanel();
        rulerCorner.setBackground(new Color(50, 50, 50));
        rulerCorner.setPreferredSize(new Dimension(RULER_THICK, RULER_THICK));
        rulerCorner.setOpaque(true);

        rulerNorthBar = new JPanel(new BorderLayout());
        rulerNorthBar.setOpaque(false);
        rulerNorthBar.add(rulerCorner, BorderLayout.WEST);
        rulerNorthBar.add(hRuler,     BorderLayout.CENTER);

        scrollPane.getViewport().addChangeListener(e -> {
            if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
            canvasWrapper.revalidate();
        });

        // viewportPanel: BorderLayout container for ruler + scroll
        viewportPanel = new JPanel(new BorderLayout());
        viewportPanel.setBackground(AppColors.BG_DARK);
        viewportPanel.setVisible(false);
        // showRuler is false initially → rulers not added yet
        viewportPanel.add(scrollPane, BorderLayout.CENTER);

        // Spacing between H scrollbar and Paint toolbar
        JPanel scrollSpacer = new JPanel();
        scrollSpacer.setOpaque(true);
        scrollSpacer.setBackground(AppColors.BG_DARK);
        scrollSpacer.setPreferredSize(new Dimension(0, 16));
        viewportPanel.add(scrollSpacer, BorderLayout.SOUTH);

        // Layered pane for nav button overlay
        layeredPane = new JLayeredPane();
        layeredPane.setBackground(AppColors.BG_DARK);
        layeredPane.setOpaque(true);
        layeredPane.setPreferredSize(new Dimension(860, 560));

        dropHintPanel.setBounds(0, 0, 860, 560);
        layeredPane.add(dropHintPanel, JLayeredPane.DEFAULT_LAYER);

        prevNavButton = UIComponentFactory.buildNavButton("‹");
        nextNavButton = UIComponentFactory.buildNavButton("›");
        prevNavButton.setEnabled(false);
        nextNavButton.setEnabled(false);
        prevNavButton.addActionListener(e -> navigateImage(-1));
        nextNavButton.addActionListener(e -> navigateImage(+1));
        layeredPane.add(prevNavButton, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(nextNavButton, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = layeredPane.getWidth(), h = layeredPane.getHeight();
                dropHintPanel.setBounds(0, 0, w, h);
                if (viewportPanel.getParent() == layeredPane)
                    viewportPanel.setBounds(0, 0, w, h);
                repositionNavButtons();
            }
        });

        tileGallery = new TileGalleryPanel(buildGalleryCallbacks());

        elementLayerPanel = new ElementLayerPanel(buildElementLayerCallbacks());

        galleryWrapper = new JPanel(new BorderLayout());
        galleryWrapper.setBackground(AppColors.BG_DARK);
        galleryWrapper.add(tileGallery, BorderLayout.WEST);
        galleryWrapper.add(layeredPane, BorderLayout.CENTER);
        // elementLayerPanel is added to EAST only when Canvas mode is active
        return galleryWrapper;
    }

    /** Re-builds the ruler strip layout around the scrollPane. */
    private void buildRulerLayout() {
        // Remove the stable containers from viewportPanel
        viewportPanel.remove(rulerNorthBar);
        viewportPanel.remove(vRuler);

        if (showRuler) {
            viewportPanel.add(rulerNorthBar, BorderLayout.NORTH);
            viewportPanel.add(vRuler,        BorderLayout.WEST);
        }
        viewportPanel.revalidate();
        viewportPanel.repaint();
    }

    private void repositionNavButtons() {
        if (prevNavButton == null) return;
        int h = layeredPane.getHeight(), bh = 80, bw = 36;
        int y = Math.max(0, (h - bh) / 2);
        prevNavButton.setBounds(8, y, bw, bh);
        nextNavButton.setBounds(layeredPane.getWidth() - bw - 8, y, bw, bh);
    }

    // ── Bottom bar: only the Paint toolbar (status bar merged into top bar) ───
    private JPanel buildBottomBar() {
        paintToolbar = new PaintToolbar(this, buildPaintCallbacks());
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_DARK);
        wrapper.add(paintToolbar, BorderLayout.NORTH);
        return wrapper;
    }

    // ── Drop hint ─────────────────────────────────────────────────────────────
    private JPanel buildDropHintPanel() {
        return new JPanel() {
            private static final long serialVersionUID = 1L;
			@Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                float[] dash = { 10f, 6f };
                g2.setColor(AppColors.BORDER);
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
                g2.drawRoundRect(20, 20, w - 41, h - 41, 20, 20);
                g2.setStroke(new BasicStroke(3));
                int is = 64, ix = w / 2 - is / 2, iy = h / 2 - 70;
                g2.setColor(AppColors.ACCENT);
                g2.drawRoundRect(ix, iy, is, is, 10, 10);
                int ax = w / 2;
                g2.drawLine(ax, iy + 10, ax, iy + is - 10);
                g2.drawLine(ax - 12, iy + is - 24, ax, iy + is - 10);
                g2.drawLine(ax + 12, iy + is - 24, ax, iy + is - 10);
                g2.setStroke(new BasicStroke(1));
                g2.setColor(AppColors.TEXT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                String t = "Bilddatei hier ablegen";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, w / 2 - fm.stringWidth(t) / 2, iy + is + 36);
                g2.setColor(AppColors.TEXT_MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                String s = "PNG · JPG · BMP · GIF   |   STRG+Rad = Zoom · Mittelmaus = Pan";
                fm = g2.getFontMetrics();
                g2.drawString(s, w / 2 - fm.stringWidth(s) / 2, iy + is + 60);
            }
            { setBackground(AppColors.BG_DARK); }
        };
    }

    // =========================================================================
    // Drag & Drop
    // =========================================================================
    private void setupDropTarget(java.awt.Component target) {
        new java.awt.dnd.DropTarget(target, java.awt.dnd.DnDConstants.ACTION_COPY,
                new java.awt.dnd.DropTargetAdapter() {
            @Override public void drop(java.awt.dnd.DropTargetDropEvent ev) {
                try {
                    ev.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) ev.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File f = files.get(0);
                        if (isSupportedFile(f)) handleFileDropped(f);
                        else showErrorDialog("Format nicht unterstützt",
                                "Erlaubt: PNG, JPG, BMP, GIF\nDatei: " + f.getName());
                    }
                    ev.dropComplete(true);
                } catch (Exception ex) { ev.dropComplete(false); showErrorDialog("Drop-Fehler", ex.getMessage()); }
            }
        }, true);
    }

    private void handleFileDropped(File file) {
        // State is cached — just load the new file (dirty files keep their red border)
        loadFile(file);
    }

    // =========================================================================
    // File loading
    // =========================================================================
    private void loadFile(File file) {
        // Save current canvas state before switching
        if (sourceFile != null) saveCurrentState();

        // Check cache first
        CanvasState cached = fileStateCache.get(file);
        if (cached != null) {
            // Restore from cache
            workingImage = cached.image;
            clipboardHandler.setWorkingImage(workingImage);
            transformHandler.setWorkingImage(workingImage);
            undoStack.clear(); undoStack.addAll(cached.undoStack);
            redoStack.clear(); redoStack.addAll(cached.redoStack);
            activeElements = cached.elements;
        } else {
            // Load fresh from disk
            try {
                BufferedImage img = ImageIO.read(file);
                if (img == null) { showErrorDialog("Ladefehler", "Bild konnte nicht gelesen werden:\n" + file.getName()); return; }
                originalImage = img;
                workingImage  = normalizeImage(originalImage);
                clipboardHandler.setWorkingImage(workingImage);
                transformHandler.setWorkingImage(workingImage);
                undoStack.clear();
                redoStack.clear();
                activeElements = new ArrayList<>();
                CanvasState cs = new CanvasState(workingImage);
                fileStateCache.put(file, cs);
            } catch (IOException e) {
                showErrorDialog("Ladefehler", "Fehler:\n" + e.getMessage());
                return;
            }
        }

        sourceFile        = file;
        hasUnsavedChanges = dirtyFiles.contains(file);
        selectedAreas.clear();
        isSelecting = false; selectionStart = null; selectionEnd = null;
        lastPaintPoint = null; shapeStartPoint = null; paintSnapshot = null;
        floatSelectionState.clear();  // DELEGATED: clears floatingImg, floatRect, isDraggingFloat, etc.
        selectedElements.clear(); draggingElement = false; elemDragAnchor = null;
        elemActiveHandle = -1; elemScaleBase = null; elemScaleStart = null;
        if (canvasPanel != null) canvasPanel.resetInputState();

        // NEW: Lade gespeicherte Szene-Daten (Layer, Zoom, Mode)
        try {
            if (projectManager.getProjectName() != null) {
                List<Layer> savedLayers = projectManager.loadScene(file);
                if (savedLayers != null) {
                    activeElements = savedLayers;
                }

                double savedZoom = projectManager.loadSceneZoom(file);
                if (savedZoom > 0) {
                    zoom = savedZoom;
                    userHasManuallyZoomed = true;
                }

                AppMode savedMode = projectManager.loadSceneMode(file);
                if (savedMode != null) {
                    appMode = savedMode;
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Fehler beim Laden der Szenen-Daten: " + e.getMessage());
        }

        indexDirectory(file);
        swapToImageView();
        SwingUtilities.invokeLater(() -> {
            if (userHasManuallyZoomed) {
                // Halte den Zoom, aber zentriere
                centerCanvas();
            } else {
                fitToViewport();
            }
        });
        refreshElementPanel();
        updateNavigationButtons();
        updateTitle();
        updateStatus();
        setBottomButtonsEnabled(true);
    }

    /** Saves the current workingImage + stacks + elements back into the file cache. */
    public void saveCurrentState() {
        if (sourceFile == null || workingImage == null) return;
        CanvasState cs = fileStateCache.computeIfAbsent(sourceFile, k -> new CanvasState(workingImage));
        cs.image = workingImage;
        cs.undoStack.clear(); cs.undoStack.addAll(undoStack);
        cs.redoStack.clear(); cs.redoStack.addAll(redoStack);
        cs.elements.clear();  cs.elements.addAll(activeElements);
    }

    /**
     * Converts the image to TYPE_INT_ARGB and ensures it is always stored in
     * a clean ARGB format so paint operations work correctly on any source.
     */
    private BufferedImage normalizeImage(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return deepCopy(src);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    private void indexDirectory(File file) {
        // DELEGATED to FileStateCache manager
        fileCacheManager.indexDirectory(file);
        // Update gallery UI with indexed files
        List<File> files = fileCacheManager.getDirectoryImages();
        if (!files.isEmpty()) {
            tileGallery.setFiles(files, file);
        }
        // Sync legacy fields
        directoryImages = new ArrayList<>(files);
        currentImageIndex = fileCacheManager.getCurrentImageIndex();
    }

    private void navigateImage(int dir) {
        // DELEGATED to FileStateCache manager
        File nextFile = (dir < 0) ? fileCacheManager.navigatePrevious()
                                  : fileCacheManager.navigateNext();
        if (nextFile != null) {
            loadFile(nextFile);
            tileGallery.scrollToActive();
            // Sync legacy fields
            currentImageIndex = fileCacheManager.getCurrentImageIndex();
        }
    }

    // =========================================================================
    // Zoom
    // =========================================================================

    /**
     * Set zoom level with smooth animation.
     * DELEGATED to ZoomState manager.
     */
    @Override public void setZoom(double nz, Point anchorCanvas) {
        zoomState.setZoom(nz, anchorCanvas);
        // Sync legacy field for backward compat
        zoom = zoomState.getZoom();
    }

    public void fitToViewport() {
        // DELEGATED to ZoomState manager
        zoomState.setWorkingImage(workingImage);
        zoomState.fitToViewport();
        centerCanvas();
    }

//    /** Set zoom immediately without animation. Used for image load/browse. */
//    private void setZoomInstant(double nz) {
//        // DELEGATED to ZoomState manager
//        zoomState.setZoomInstant(nz);
//        zoom = zoomState.getZoom();
//    }
//
//    private void updateZoomLabel() {
//        // DELEGATED to ZoomState manager
//        // zoomState automatically updates the label during animation
//    }
//
//    /**
//     * Start or restart the zoom animation timer.
//     * DELEGATED to ZoomState manager (called from setZoom).
//     */
//    private void startZoomAnimation() {
//        // zoomState.startZoomAnimation() is called internally from setZoom()
//        // Sync legacy field
//        zoom = zoomState.getZoom();
//    }
//
//    /**
//     * Apply current zoom to the UI (called every animation frame).
//     * DELEGATED to ZoomState manager.
//     */
//    private void applyZoomFrame() {
//        // zoomState.applyZoomFrame() is called by the animation timer
//        // Sync legacy fields
//        zoom = zoomState.getZoom();
//    }

    // =========================================================================
    // Coordinate transform
    // =========================================================================
    /** Convert a point in canvasPanel-local coordinates to image-space. */
    @Override public Point screenToImage(Point sp) {
        int ix = (int) Math.floor(sp.x / zoom);
        int iy = (int) Math.floor(sp.y / zoom);
        if (workingImage != null) {
            ix = Math.max(0, Math.min(workingImage.getWidth()  - 1, ix));
            iy = Math.max(0, Math.min(workingImage.getHeight() - 1, iy));
        }
        return new Point(ix, iy);
    }

    // =========================================================================
    // Alpha-editor operations
    // =========================================================================
    @Override public void performFloodfill(Point screenPt) {
        Point ip = screenToImage(screenPt);
        int tc = workingImage.getRGB(ip.x, ip.y);
        if (((tc >> 24) & 0xFF) == 0) { showInfoDialog("Bereits transparent", "Klicke auf eine sichtbare Farbe."); return; }
        PaintEngine.floodFill(workingImage, ip.x, ip.y, new Color(0,0,0,0), 30);
        markDirty();
    }

    private void applySelectionsToAlpha() {
        if (selectedAreas.isEmpty()) { showInfoDialog("Keine Auswahl", "Noch keine Bereiche ausgewählt."); return; }
        pushUndo();
        for (Rectangle r : selectedAreas) PaintEngine.clearRegion(workingImage, r);
        selectedAreas.clear();
        markDirty();
        showInfoDialog("Erledigt", "Ausgewählte Bereiche wurden transparent gemacht.");
    }

    private void clearSelections() { selectedAreas.clear(); canvasPanel.repaint(); }

    private String getSaveSuffix() {
        return (appMode == AppMode.PAINT) ? "_painted"
             : floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
    }

    private void resetImage() {
        if (originalImage == null) return;

        workingImage = deepCopy(originalImage);
        clipboardHandler.setWorkingImage(workingImage);
        transformHandler.setWorkingImage(workingImage);
        undoStack.clear();
        redoStack.clear();
        undoRedoManager.clear();
        selectedAreas.clear();
        floatingImg = null;
        floatRect = null;
        activeElements.clear();
        selectedElements.clear();

        hasUnsavedChanges = false;
        if (sourceFile != null) dirtyFiles.remove(sourceFile);
        updateTitle();
        updateDirtyUI();
        elementLayerPanel.refresh(activeElements);
        canvasPanel.repaint();
    }

    private void saveImage() {
        if (sourceFile == null) return;
        try {
            String suffix  = getSaveSuffix();
            String outPath = WhiteToAlphaConverter.getOutputPath(sourceFile, suffix);
            File   outFile = new File(outPath);
            ImageIO.write(workingImage, "PNG", outFile);
            hasUnsavedChanges = false;
            dirtyFiles.remove(sourceFile);
            updateTitle();
            updateDirtyUI();
            showInfoDialog("Gespeichert", "Gespeichert als:\n" + outFile.getName());
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    /** Saves current workingImage to undo stack before a destructive operation. */
    public void pushUndo() {
        // DELEGATED to UndoRedoManager
        undoRedoManager.pushUndo(workingImage);
        // Sync legacy fields for backward compatibility
        undoStack.clear();
        undoStack.addAll(undoRedoManager.getUndoStack());
        redoStack.clear();
        redoStack.addAll(undoRedoManager.getRedoStack());
    }

    public void clearUndoRedo() {
        // DELEGATED to UndoRedoManager
        undoRedoManager.clear();
        // Sync legacy fields
        undoStack.clear();
        redoStack.clear();
    }

    private void doUndo() {
        // DELEGATED to UndoRedoManager
        BufferedImage restored = undoRedoManager.doUndo(workingImage);
        if (restored != null) {
            workingImage = restored;
            clipboardHandler.setWorkingImage(workingImage);
        }
        // Sync legacy fields
        undoStack.clear();
        undoStack.addAll(undoRedoManager.getUndoStack());
        redoStack.clear();
        redoStack.addAll(undoRedoManager.getRedoStack());
    }

    private void doRedo() {
        // DELEGATED to UndoRedoManager
        BufferedImage restored = undoRedoManager.doRedo(workingImage);
        if (restored != null) {
            workingImage = restored;
            clipboardHandler.setWorkingImage(workingImage);
        }
        // Sync legacy fields
        undoStack.clear();
        undoStack.addAll(undoRedoManager.getUndoStack());
        redoStack.clear();
        redoStack.addAll(undoRedoManager.getRedoStack());
    }

    private void afterUndoRedo() {
        // If undo stack is empty, we've undone all changes back to original state
        if (undoStack.isEmpty()) {
            hasUnsavedChanges = false;
            if (sourceFile != null) dirtyFiles.remove(sourceFile);
        } else {
            hasUnsavedChanges = true;
            if (sourceFile != null) dirtyFiles.add(sourceFile);
        }
        updateTitle();
        updateDirtyUI();
        canvasWrapper.revalidate();
        canvasPanel.repaint();
        if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
    }

    /** CTRL+S: save silently without any confirmation dialog. */
    private void saveImageSilent() {
        if (sourceFile == null) return;
        try {
            String suffix  = getSaveSuffix();
            String outPath = WhiteToAlphaConverter.getOutputPath(sourceFile, suffix);
            ImageIO.write(workingImage, "PNG", new File(outPath));
            hasUnsavedChanges = false;
            dirtyFiles.remove(sourceFile);
            updateTitle();
            updateDirtyUI();
            ToastNotification.show(this, "Gespeichert");
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    public void markDirty() {
        hasUnsavedChanges = true;
        if (sourceFile != null) dirtyFiles.add(sourceFile);
        updateTitle();
        updateDirtyUI();
        canvasPanel.repaint();
        if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
    }

    private void updateDirtyUI() {
        tileGallery.setDirtyFiles(dirtyFiles);
    }

    // =========================================================================
    // Mode toggles
    // =========================================================================
//    private void toggleAlphaMode() {
//        if (appMode != AppMode.ALPHA_EDITOR) return;
//        floodfillMode = !floodfillMode;
//        modeLabel.setText("Modus: " + (floodfillMode ? "Floodfill" : "Selective Alpha"));
//        boolean sel = !floodfillMode;
//        applyButton.setEnabled(sel && sourceFile != null);
//        clearSelectionsButton.setEnabled(sel && sourceFile != null);
//        selectedAreas.clear(); canvasPanel.repaint();
//    }

    private void togglePaintMode() {
        boolean entering = paintModeBtn.isSelected();
        if (entering) {
            appMode = AppMode.PAINT;
        } else {
            // Leaving Paint: also deactivate Canvas sub-mode
            if (canvasModeBtn.isSelected()) {
                canvasModeBtn.setSelected(false);
                setElementPanelVisible(false);
            }
            appMode = AppMode.ALPHA_EDITOR;
        }
        // Canvas sub-mode button is only meaningful inside Paint
        canvasModeBtn.setEnabled(entering);
        modeLabel.setText(entering
                ? (canvasModeBtn.isSelected() ? "Modus: Paint / Canvas" : "Modus: Paint")
                : (floodfillMode ? "Modus: Floodfill" : "Modus: Selective Alpha"));
        if (entering) {
            paintToolbar.showToolbar();
            applyButton.setEnabled(false);
            clearSelectionsButton.setEnabled(false);
        } else {
            paintToolbar.hideToolbar();
            boolean sel = !floodfillMode;
            applyButton.setEnabled(sel && sourceFile != null);
            clearSelectionsButton.setEnabled(sel && sourceFile != null);
        }
        selectedAreas.clear();
        lastPaintPoint = null;
        shapeStartPoint = null;
        // Refit or center after paint toolbar visibility changes.
        // Double-deferred so the toolbar layout pass completes before we
        // measure the viewport height for fitting.
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            if (!userHasManuallyZoomed) {
                fitToViewport();
            } else {
                centerCanvasX();
            }
            canvasPanel.repaint();
        }));
        paintSnapshot = null;
        canvasPanel.setCursor(entering
                ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                : Cursor.getDefaultCursor());
        canvasPanel.repaint();
    }

    /** Canvas is a sub-mode of Paint: layers panel shown, drawing stays non-destructive. */
    private void toggleCanvasMode() {
        boolean entering = canvasModeBtn.isSelected();
        // appMode stays PAINT in both cases — Canvas is just a sub-mode flag
        if (entering) {
            setElementPanelVisible(true);
        } else {
            setElementPanelVisible(false);
        }
        modeLabel.setText(entering ? "Modus: Paint / Canvas" : "Modus: Paint");
        canvasPanel.repaint();
    }

    /** Shows or hides the element layer panel on the right side of the canvas. */
    private void setElementPanelVisible(boolean visible) {
        if (visible) {
            if (elementLayerPanel.getParent() != galleryWrapper) {
                galleryWrapper.add(elementLayerPanel, BorderLayout.EAST);
            }
            refreshElementPanel();
        } else {
            galleryWrapper.remove(elementLayerPanel);
        }
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /** Rebuilds the element layer panel tiles from the current activeElements. */
    private void refreshElementPanel() {
        if (elementLayerPanel != null && elementLayerPanel.isShowing()) {
            elementLayerPanel.refresh(activeElements);
        }
    }

    /** Builds the callbacks for the ElementLayerPanel. */
    private ElementLayerPanel.Callbacks buildElementLayerCallbacks() {
        return new ElementLayerPanel.Callbacks() {
            @Override public List<Layer> getActiveElements()   { return activeElements; }
            @Override public List<Layer> getSelectedElements() { return selectedElements; }
            @Override public void setSelectedElement(Layer el) {
                selectedElements.clear();
                if (el != null) selectedElements.add(el);
                if (canvasPanel != null) canvasPanel.repaint();
            }
            @Override public void toggleElementSelection(Layer el) {
                doToggleElementSelection(el);
                if (canvasPanel != null) canvasPanel.repaint();
            }
            @Override public void deleteElement(Layer el) {
                activeElements.removeIf(e -> e.id() == el.id());
                selectedElements.removeIf(e -> e.id() == el.id());
                markDirty();
                refreshElementPanel();
                if (canvasPanel != null) canvasPanel.repaint();
            }
            @Override public void burnElement(Layer el) {
                if (workingImage == null) return;
                // Tile holds a snapshot — look up live layer so position/scale are current
                Layer live = activeElements.stream()
                        .filter(e -> e.id() == el.id()).findFirst().orElse(el);
                pushUndo();
                BufferedImage imgToBurn;
                if (live instanceof TextLayer tl) {
                    imgToBurn = renderTextLayerToImage(tl);
                } else {
                    ImageLayer il = (ImageLayer) live;
                    imgToBurn = PaintEngine.scale(
                            il.image(), Math.max(1, live.width()), Math.max(1, live.height()));
                }
                PaintEngine.pasteRegion(workingImage, imgToBurn, new java.awt.Point(live.x(), live.y()));
                activeElements.removeIf(e -> e.id() == el.id());
                selectedElements.removeIf(e -> e.id() == el.id());
                markDirty();
                refreshElementPanel();
                if (canvasPanel != null) canvasPanel.repaint();
            }

            @Override public void exportElementAsImage(Layer el) {
                if (workingImage == null || sourceFile == null) return;
                System.err.println("[DEBUG] exportElementAsImage called for layer id=" + el.id());

                // Get live layer
                Layer live = activeElements.stream()
                        .filter(e -> e.id() == el.id()).findFirst().orElse(el);

                // Render element to image
                BufferedImage imgToExport;
                if (live instanceof TextLayer tl) {
                    imgToExport = renderTextLayerToImage(tl);
                } else {
                    ImageLayer il = (ImageLayer) live;
                    imgToExport = PaintEngine.scale(
                            il.image(), Math.max(1, live.width()), Math.max(1, live.height()));
                }

                // Generate default filename: originalName + _layer_<id>
                String sourceName = sourceFile.getName();
                int lastDot = sourceName.lastIndexOf('.');
                String baseName = lastDot > 0 ? sourceName.substring(0, lastDot) : sourceName;
                String extension = lastDot > 0 ? sourceName.substring(lastDot) : ".png";
                String defaultName = baseName + "_layer_" + live.id() + extension;

                // Find unique filename if file already exists
                File exportDir = sourceFile.getParentFile();
                File targetFile = new File(exportDir, defaultName);
                int counter = 1;
                while (targetFile.exists()) {
                    String uniqueName = baseName + "_layer_" + live.id() + "_" + counter + extension;
                    targetFile = new File(exportDir, uniqueName);
                    counter++;
                    defaultName = uniqueName;
                }

                // Show dialog for filename confirmation
                final File exportDirFinal = exportDir;
//                final String defaultNameFinal = defaultName;
                javax.swing.JTextField fileNameField = new javax.swing.JTextField(defaultName);
                fileNameField.selectAll();

                String[] options = {"Speichern", "Abbrechen"};
                javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
                panel.add(new javax.swing.JLabel("Dateiname:"), java.awt.BorderLayout.WEST);
                panel.add(fileNameField, java.awt.BorderLayout.CENTER);

                // Allow Enter key to save immediately
                fileNameField.addActionListener(ev -> {
                    System.err.println("[DEBUG] Enter pressed in filename dialog");
                    String fileName = fileNameField.getText().trim();
                    if (!fileName.isEmpty()) {
                        saveElementAsImageFile(imgToExport, new File(exportDirFinal, fileName));
                    }
                });

                int result = javax.swing.JOptionPane.showOptionDialog(SelectiveAlphaEditor2.this, panel, "Exportieren als Bild",
                        javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                        options, options[0]);

                if (result == javax.swing.JOptionPane.OK_OPTION) {
                    String fileName = fileNameField.getText().trim();
                    if (!fileName.isEmpty()) {
                        saveElementAsImageFile(imgToExport, new File(exportDirFinal, fileName));
                    }
                }
            }

            private void saveElementAsImageFile(BufferedImage img, File file) {
                try {
                    javax.imageio.ImageIO.write(img, "PNG", file);
                    System.err.println("[DEBUG] Element exported to: " + file.getAbsolutePath());

                    // Add the new image to the tile gallery
                    if (tileGallery != null) {
                        java.util.List<File> newFiles = new java.util.ArrayList<>();
                        newFiles.add(file);
                        tileGallery.addFiles(newFiles);
                        System.err.println("[DEBUG] Added new image to gallery: " + file.getName());
                    }

                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor2.this, "Bild gespeichert:\n" + file.getName(),
                            "Erfolg", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    System.err.println("[ERROR] Failed to export element: " + ex.getMessage());
                    ex.printStackTrace();
                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor2.this, "Fehler beim Speichern:\n" + ex.getMessage(),
                            "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }

            @Override public void repaintCanvas() {
                if (canvasPanel != null) canvasPanel.repaint();
            }
            @Override public void onCloseRequested() {
                canvasModeBtn.setSelected(false);
                toggleCanvasMode();
            }
            @Override public void onLayerPanelElementHover(int elementId) {
                // Forward tile hover to canvas so it can highlight the matching element
                if (canvasPanel != null) canvasPanel.setHoveredElementId(elementId);
            }
        };
    }

    // =========================================================================
    // Transformations
    // =========================================================================
    private void doFlipH() {
        if (workingImage == null) return;
        Rectangle sel = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        pushUndo();
        // DELEGATED to ImageTransformHandler
        BufferedImage result = transformHandler.flipHorizontal(sel);
        if (result != null) {
            workingImage = result;
        }
        markDirty();
    }

    private void doFlipV() {
        if (workingImage == null) return;
        Rectangle sel = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        pushUndo();
        // DELEGATED to ImageTransformHandler
        BufferedImage result = transformHandler.flipVertical(sel);
        if (result != null) {
            workingImage = result;
        }
        markDirty();
    }

    private void doRotate() {
        if (workingImage == null) return;
        // Dialog: enter angle
        JTextField angleField = new JTextField("90", 6);
        angleField.setBackground(AppColors.BTN_BG);
        angleField.setForeground(AppColors.TEXT);
        angleField.setCaretColor(AppColors.TEXT);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBackground(AppColors.BG_PANEL);
        JLabel lbl = new JLabel("Winkel (°):");
        lbl.setForeground(AppColors.TEXT);
        panel.add(lbl);
        panel.add(angleField);

        JDialog dialog = createBaseDialog("Drehen", 280, 160);
        JPanel content = centeredColumnPanel(16, 24, 12);
        content.add(panel);
        content.add(Box.createVerticalStrut(12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok  = UIComponentFactory.buildButton("OK",       AppColors.ACCENT,  AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        final boolean rotateHasSel = (appMode == AppMode.PAINT && getActiveSelection() != null);
        final Rectangle rotateSel  = rotateHasSel ? getActiveSelection() : null;
        ok.addActionListener(e -> {
            try {
                double deg = Double.parseDouble(angleField.getText().trim());
                pushUndo();
                // DELEGATED to ImageTransformHandler
                BufferedImage result = transformHandler.rotate(deg, rotateSel);
                if (result != null) {
                    workingImage = result;
                    if (!rotateHasSel) {
                        canvasWrapper.revalidate();
                    }
                }
                markDirty();
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte eine Zahl eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok); row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    private void doScale() {
        if (workingImage == null) return;
        Rectangle scaleSel  = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        int origW = scaleSel != null ? scaleSel.width  : workingImage.getWidth();
        int origH = scaleSel != null ? scaleSel.height : workingImage.getHeight();

        JTextField wField = new JTextField(String.valueOf(origW), 5);
        JTextField hField = new JTextField(String.valueOf(origH), 5);
        JTextField pctField = new JTextField("100", 5);
        JCheckBox lockAR = new JCheckBox("Proportional", true);

        for (JTextField f : new JTextField[]{wField, hField, pctField}) {
            f.setBackground(AppColors.BTN_BG);
            f.setForeground(AppColors.TEXT);
            f.setCaretColor(AppColors.TEXT);
        }
        lockAR.setOpaque(false);
        lockAR.setForeground(AppColors.TEXT);

        // Keep fields in sync
        pctField.addActionListener(ev -> {
            try {
                double pct = Double.parseDouble(pctField.getText().trim()) / 100.0;
                wField.setText(String.valueOf((int)(origW * pct)));
                hField.setText(String.valueOf((int)(origH * pct)));
            } catch (NumberFormatException ignored) {}
        });
        wField.addActionListener(ev -> {
            if (lockAR.isSelected()) {
                try {
                    int nw = Integer.parseInt(wField.getText().trim());
                    hField.setText(String.valueOf((int)(origH * ((double) nw / origW))));
                    pctField.setText(String.format("%.1f", 100.0 * nw / origW));
                } catch (NumberFormatException ignored) {}
            }
        });

        JPanel grid = new JPanel(new GridLayout(4, 2, 6, 4));
        grid.setOpaque(false);
        for (String lbl : new String[]{"Breite (px):", "Höhe (px):", "Prozent:", ""}) {
            JLabel l = new JLabel(lbl);
            l.setForeground(AppColors.TEXT);
            grid.add(l);
        }
        // Replace last label with lockAR checkbox
        Component[] comps = grid.getComponents();
        grid.remove(comps[comps.length - 1]);
        grid.add(lockAR);
        // interleave fields (already added labels, now add fields interleaved)
        // simpler: rebuild
        grid.removeAll();
        String[] labels = {"Breite (px):", "Höhe (px):", "Prozent:", ""};
        JComponent[] fields = {wField, hField, pctField, lockAR};
        for (int i = 0; i < labels.length; i++) {
            JLabel l = new JLabel(labels[i]); l.setForeground(AppColors.TEXT);
            grid.add(l);
            grid.add(fields[i]);
        }

        JDialog dialog = createBaseDialog("Skalieren", 300, 230);
        JPanel content = centeredColumnPanel(16, 20, 12);
        content.add(grid);
        content.add(Box.createVerticalStrut(12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok  = UIComponentFactory.buildButton("OK",        AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Integer.parseInt(wField.getText().trim());
                int nh = Integer.parseInt(hField.getText().trim());
                pushUndo();
                // DELEGATED to ImageTransformHandler
                Rectangle newSel = transformHandler.scale(nw, nh, scaleSel);
                if (scaleSel != null && newSel != null) {
                    selectedAreas.clear();
                    selectedAreas.add(newSel);
                } else if (scaleSel == null) {
                    workingImage = transformHandler.getWorkingImage();
                    canvasWrapper.revalidate();
                }
                markDirty();
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte ganzzahlige Pixelwerte eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok); row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    // =========================================================================
    // Floating selection operations
    // =========================================================================

    /** Paste the floating image at its current (possibly scaled) rect and clear float state.
     *  In Paint mode: creates a non-destructive Element layer instead of writing to canvas. */
    @Override public void commitFloat() {
        // DELEGATED to FloatSelectionState manager for image handling
        if (!floatSelectionState.isActive()) return;

        BufferedImage floatImg = floatSelectionState.getImage();
        Rectangle floatRect = floatSelectionState.getRect();
        if (floatImg == null || floatRect == null) return;

        BufferedImage scaled = PaintEngine.scale(floatImg,
                Math.max(1, floatRect.width), Math.max(1, floatRect.height));
        if (appMode == AppMode.PAINT) {
            // Non-destructive: become an ImageLayer
            Layer el = new ImageLayer(nextElementId++, scaled,
                    floatRect.x, floatRect.y, floatRect.width, floatRect.height);
            activeElements.add(el);
            selectedElements.clear();
            selectedElements.add(el);
            refreshElementPanel();
        } else {
            PaintEngine.pasteRegion(workingImage, scaled, new Point(floatRect.x, floatRect.y));
        }

        // Clear float state via manager
        floatSelectionState.clear();
        selectedAreas.clear();
        markDirty();
    }

    /** Discard the float and undo to the state before it was lifted. */
    private void cancelFloat() {
        // DELEGATED to FloatSelectionState manager
        floatSelectionState.clear();
        selectedAreas.clear();
        doUndo();
    }

    /** Convert floatRect (image-space) to canvasPanel screen-space. */
    @Override public Rectangle floatRectScreen() {
        // DELEGATED to FloatSelectionState manager
        floatSelectionState.setZoom(zoom);  // Keep in sync
        return floatSelectionState.getRectScreen();
    }

    /**
     * 8 handle hit-rects around {@code sr} (screen-space).
     * DELEGATED to FloatSelectionState manager.
     * Order: TL=0, TC=1, TR=2, ML=3, MR=4, BL=5, BC=6, BR=7
     */
    @Override public Rectangle[] handleRects(Rectangle sr) {
        // FloatSelectionState.getHandleRects() already does this
        return floatSelectionState.getHandleRects();
    }

    /** Returns 0-7 if {@code pt} (canvasPanel coords) hits a handle, else -1. */
    @Override public int hitHandle(Point pt) {
        // DELEGATED to FloatSelectionState manager
        floatSelectionState.setZoom(zoom);  // Keep in sync
        return floatSelectionState.hitHandle(pt);
    }

    /**
     * Compute the new floatRect when handle {@code handle} is dragged from
     * {@code origin} to {@code current} (both in canvasPanel screen-space).
     * Uses double precision throughout to avoid integer-truncation drift at
     * high zoom levels. Corners scale proportionally; sides scale one axis.
     */
//    private Rectangle computeNewFloatRect(int handle, Rectangle base,
//                                           Point origin, Point current) {
//        // Delta in image-space (double, no truncation)
//        double dx = (current.x - origin.x) / zoom;
//        double dy = (current.y - origin.y) / zoom;
//        double bx = base.x, by = base.y, bw = base.width, bh = base.height;
//        final double MIN = 1.0;
//
//        double rx, ry, rw, rh;
//        switch (handle) {
//            case 0 -> { // TL – proportional, anchor BR
//                rw = Math.max(MIN, bw - dx);
//                rh = Math.max(MIN, bh * rw / bw);
//                rx = bx + bw - rw; ry = by + bh - rh;
//            }
//            case 1 -> { // TC – scale Y only, anchor bottom
//                rh = Math.max(MIN, bh - dy);
//                rx = bx; rw = bw; ry = by + bh - rh;
//            }
//            case 2 -> { // TR – proportional, anchor BL
//                rw = Math.max(MIN, bw + dx);
//                rh = Math.max(MIN, bh * rw / bw);
//                rx = bx; ry = by + bh - rh;
//            }
//            case 3 -> { // ML – scale X only, anchor right
//                rw = Math.max(MIN, bw - dx);
//                rx = bx + bw - rw; ry = by; rh = bh;
//            }
//            case 4 -> { // MR – scale X only, anchor left
//                rw = Math.max(MIN, bw + dx);
//                rx = bx; ry = by; rh = bh;
//            }
//            case 5 -> { // BL – proportional, anchor TR
//                rw = Math.max(MIN, bw - dx);
//                rh = Math.max(MIN, bh * rw / bw);
//                rx = bx + bw - rw; ry = by;
//            }
//            case 6 -> { // BC – scale Y only, anchor top
//                rh = Math.max(MIN, bh + dy);
//                rx = bx; rw = bw; ry = by;
//            }
//            default -> { // BR (7) – proportional, anchor TL
//                rw = Math.max(MIN, bw + dx);
//                rh = Math.max(MIN, bh * rw / bw);
//                rx = bx; ry = by;
//            }
//        }
//        return new Rectangle(
//            (int) Math.round(rx), (int) Math.round(ry),
//            (int) Math.round(rw), (int) Math.round(rh));
//    }

//    private Cursor getResizeCursor(int handle) {
//        return Cursor.getPredefinedCursor(switch (handle) {
//            case 0 -> Cursor.NW_RESIZE_CURSOR;
//            case 1 -> Cursor.N_RESIZE_CURSOR;
//            case 2 -> Cursor.NE_RESIZE_CURSOR;
//            case 3 -> Cursor.W_RESIZE_CURSOR;
//            case 4 -> Cursor.E_RESIZE_CURSOR;
//            case 5 -> Cursor.SW_RESIZE_CURSOR;
//            case 6 -> Cursor.S_RESIZE_CURSOR;
//            case 7 -> Cursor.SE_RESIZE_CURSOR;
//            default -> Cursor.DEFAULT_CURSOR;
//        });
//    }

    // =========================================================================
    // PaintToolbar callbacks
    // =========================================================================
    private PaintToolbar.Callbacks buildPaintCallbacks() {
        return new PaintToolbar.Callbacks() {
            @Override public void onToolChanged(PaintEngine.Tool tool)      { canvasPanel.repaint(); }
            @Override public void onColorChanged(Color p, Color s)          {}
            @Override public void onStrokeChanged(int w)                    {}
            @Override public void onFillModeChanged(PaintEngine.FillMode m) {}
            @Override public void onBrushShapeChanged(PaintEngine.BrushShape s) {}
            @Override public void onAntialiasingChanged(boolean aa)         { canvasPanel.repaint(); }
            @Override public void onCut()   { doCut(); }
            @Override public void onCopy()  { doCopy(); }
            @Override public void onPaste() { doPaste(); }
            @Override public void onToggleGrid(boolean show)  {
                showGrid = show; canvasPanel.repaint();
            }
            @Override public void onToggleRuler(boolean show) {
                showRuler = show;
                buildRulerLayout();
            }
            @Override public void onRulerUnitChanged(int idx) {
                rulerUnit = RulerUnit.values()[idx];
                if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
            }
            @Override public void onFlipHorizontal() { doFlipH(); }
            @Override public void onFlipVertical()   { doFlipV(); }
            @Override public void onRotate()         { doRotate(); }
            @Override public void onScale()          { doScale(); }
            @Override public void onUndo()           { doUndo(); }
            @Override public void onRedo()           { doRedo(); }
            @Override public BufferedImage getWorkingImage() { return workingImage; }
        };
    }

    // =========================================================================
    // TileGallery callbacks
    // =========================================================================
    private TileGalleryPanel.Callbacks buildGalleryCallbacks() {
        return new TileGalleryPanel.Callbacks() {
            @Override public void onTileOpened(File f) {
                // No unsaved-changes dialog: dirty state is kept in cache + red border
                loadFile(f);
            }
            public void onSelectionChanged(List<File> files) {
                selectedImages = files;
            }
        };
    }

    // =========================================================================
    // Clipboard operations
    // =========================================================================

    /** CTRL+C — copy INSIDE selection → Element layer (or full image if no selection). */
    private void doCopy() {
        if (workingImage == null) return;

        // NEW: PathLayer support
        if (!selectedElements.isEmpty() && selectedElements.get(0) instanceof PathLayer pl) {
            // DELEGATED to ClipboardOperationHandler
            clipboard = clipboardHandler.doCopyPolygon(pl.absXPoints(), pl.absYPoints(), false);
            return;
        }

        // DELEGATED to ClipboardOperationHandler
        Rectangle sel = getActiveSelection();
        BufferedImage result = clipboardHandler.doCopy(sel, false);
        clipboard = result;

        // Create element layer from clipboard (in PAINT mode only)
        if (sel != null && result != null) {
            addElementFromClipboard(result, sel.x, sel.y);
        }
    }

    /** CTRL+SHIFT+C — copy OUTSIDE selection → Element layer (full-size, inside punched out). */
    private void doCopyOutside() {
        if (workingImage == null) return;

        // NEW: PathLayer support
        if (!selectedElements.isEmpty() && selectedElements.get(0) instanceof PathLayer pl) {
            // DELEGATED to ClipboardOperationHandler
            clipboard = clipboardHandler.doCopyPolygon(pl.absXPoints(), pl.absYPoints(), true);
            return;
        }

        // DELEGATED to ClipboardOperationHandler
        Rectangle sel = getActiveSelection();
        BufferedImage result = clipboardHandler.doCopy(sel, true);
        clipboard = result;

        // Create element layer from clipboard (always for outside-copy)
        if (result != null) {
            addElementFromClipboard(result, sel != null ? 0 : 0, 0);
        }
    }

    /** CTRL+X — cut INSIDE selection → Element layer + clear canvas pixels. */
    private void doCut() {
        if (workingImage == null) return;

        // NEW: PathLayer support
        if (!selectedElements.isEmpty() && selectedElements.get(0) instanceof PathLayer pl) {
            pushUndo();
            // DELEGATED to ClipboardOperationHandler
            clipboard = clipboardHandler.doCutPolygon(pl.absXPoints(), pl.absYPoints(), false);
            markDirty();
            return;
        }

        pushUndo();
        // DELEGATED to ClipboardOperationHandler
        Rectangle sel = getActiveSelection();
        BufferedImage result = clipboardHandler.doCut(sel, false);
        clipboard = result;

        markDirty();

        // Create element layer from clipboard (in PAINT mode only)
        if (sel != null && result != null) {
            addElementFromClipboard(result, sel.x, sel.y);
        }
    }

    /** CTRL+SHIFT+X — cut OUTSIDE selection → Element layer (full-size) + clear canvas outside. */
    private void doCutOutside() {
        if (workingImage == null) return;

        // NEW: PathLayer support
        if (!selectedElements.isEmpty() && selectedElements.get(0) instanceof PathLayer pl) {
            pushUndo();
            // DELEGATED to ClipboardOperationHandler
            clipboard = clipboardHandler.doCutPolygon(pl.absXPoints(), pl.absYPoints(), true);
            markDirty();
            return;
        }

        Rectangle sel = getActiveSelection();
        if (sel != null) {
            pushUndo();
            // DELEGATED to ClipboardOperationHandler
            BufferedImage result = clipboardHandler.doCut(sel, true);
            clipboard = result;

            markDirty();

            // Create element layer from clipboard (always for outside-cut)
            if (result != null) {
                addElementFromClipboard(result, 0, 0);
            }
        } else {
            // No selection: same as normal cut
            doCut();
        }
    }

    /**
     * Creates a new ImageLayer from the given image and adds it to activeElements.
     * Called after any copy/cut so the content immediately appears as a non-destructive layer.
     */
    private void addElementFromClipboard(BufferedImage img, int x, int y) {
        if (img == null || appMode != AppMode.PAINT) return;
        Layer el = new ImageLayer(nextElementId++, deepCopy(img), x, y, img.getWidth(), img.getHeight());
        activeElements.add(el);
        selectedElements.clear();
        selectedElements.add(el);
        refreshElementPanel();
        if (canvasPanel != null) canvasPanel.repaint();
    }

    private void doPaste() {
        // DELEGATED to ClipboardOperationHandler
        BufferedImage pasted = clipboardHandler.doPaste();
        if (pasted != null && workingImage != null) {
            pushUndo();
            // Create floating selection immediately — handles appear right away.
            // Content is merged to the canvas only when commitFloat() is called.
            clipboard = pasted;
            floatingImg = deepCopy(pasted);
            floatRect   = new Rectangle(0, 0,
                    Math.min(pasted.getWidth(),  workingImage.getWidth()),
                    Math.min(pasted.getHeight(), workingImage.getHeight()));
            isDraggingFloat = false; floatDragAnchor = null;
            activeHandle = -1; scaleBaseRect = null; scaleDragStart = null;
            selectedAreas.clear();
            hasUnsavedChanges = true;
            updateTitle();
            canvasPanel.repaint();
        }
    }

    @Override public Rectangle getActiveSelection() {
        if (!selectedAreas.isEmpty()) return selectedAreas.get(selectedAreas.size() - 1);
        if (isSelecting && selectionStart != null && selectionEnd != null) {
            int x = Math.min(selectionStart.x, selectionEnd.x);
            int y = Math.min(selectionStart.y, selectionEnd.y);
            int w = Math.abs(selectionEnd.x - selectionStart.x);
            int h = Math.abs(selectionEnd.y - selectionStart.y);
            return (w > 0 && h > 0) ? new Rectangle(x, y, w, h) : null;
        }
        return null;
    }

    // =========================================================================
    // Keyboard shortcuts
    // =========================================================================
    private void setupKeyBindings() {
        JPanel root = (JPanel) getContentPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        int CTRL       = InputEvent.CTRL_DOWN_MASK;
        int CTRL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL),        "copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL_SHIFT),  "copyOutside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL),        "cut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL_SHIFT),  "cutOutside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, CTRL),        "paste");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, CTRL),        "selectAll");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL),        "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, CTRL),        "redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL),        "save");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,    0),   "escape");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,    0),   "deleteInside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),  "deleteOutside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,     0),   "mergeElement");

        am.put("copy",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCopy(); } });
        am.put("copyOutside", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCopyOutside(); } });
        am.put("cut",         new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCut(); } });
        am.put("cutOutside",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCutOutside(); } });
        am.put("paste",       new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doPaste(); } });
        am.put("selectAll",   new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (appMode == AppMode.PAINT) {
                selectedElements.clear();
                selectedElements.addAll(activeElements);
                if (canvasPanel != null) canvasPanel.repaint();
                refreshElementPanel();
            }
        }});
        am.put("undo",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (floatingImg != null) cancelFloat(); else doUndo();
        }});
        am.put("redo",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doRedo(); } });
        am.put("save",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveImageSilent(); } });
        am.put("escape",      new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (floatingImg != null) { cancelFloat(); }
            else if (!selectedElements.isEmpty()) { selectedElements.clear(); canvasPanel.repaint(); }
            else { selectedAreas.clear(); isSelecting = false; selectionStart = null; selectionEnd = null; canvasPanel.repaint(); }
        }});
        am.put("deleteInside", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            // NEW: PathLayer support
            if (!selectedElements.isEmpty() && selectedElements.get(0) instanceof PathLayer pl) {
                pushUndo();
                PaintEngine.clearPolygon(workingImage, pl.absXPoints(), pl.absYPoints());
                markDirty();
            } else if (!selectedElements.isEmpty()) {
                deleteSelectedElements();
            } else if (!selectedAreas.isEmpty() && workingImage != null) {
                pushUndo();
                for (Rectangle r : selectedAreas) PaintEngine.clearRegion(workingImage, r);
                selectedAreas.clear();
                isSelecting = false; selectionStart = null; selectionEnd = null;
                markDirty();
            }
        }});
        am.put("deleteOutside", new AbstractAction() { private static final long serialVersionUID = 1L;

		@Override public void actionPerformed(ActionEvent e) {
            if (workingImage == null) return;
            Rectangle sel = getActiveSelection();
            if (sel != null) {
                pushUndo();
                PaintEngine.clearOutside(workingImage, sel);
                markDirty();
            }
        }});
        am.put("mergeElement", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (!selectedElements.isEmpty()) mergeSelectedElements();
        }});
    }

    // =========================================================================
    // New helper methods
    // =========================================================================

    /** Centers the viewport over the canvas (called after zoom or sidebar toggle). */
    public void centerCanvas() {
        if (scrollPane == null || workingImage == null) return;
        // Schedule after the current layout pass so viewport size is up-to-date.
        SwingUtilities.invokeLater(() -> {
            // Force the wrapper to re-layout with the current viewport size.
            canvasWrapper.revalidate();
            canvasWrapper.validate();
            JViewport  vp    = scrollPane.getViewport();
            Dimension  vpSz  = vp.getSize();
            int cw = (int) Math.ceil(workingImage.getWidth()  * zoom);
            int ch = (int) Math.ceil(workingImage.getHeight() * zoom);
            // When image < viewport, doLayout already centres canvasPanel inside the
            // wrapper and the view position should stay at (0,0).
            // When image > viewport, scroll to the centre of the image.
            int viewX = Math.max(0, (cw - vpSz.width)  / 2);
            int viewY = Math.max(0, (ch - vpSz.height) / 2);
            vp.setViewPosition(new Point(viewX, viewY));
        });
    }

    /**
     * Centers the viewport horizontally only (X-axis).
     * Called when the user has a custom zoom and the toolbar or fullscreen state changes.
     */
    private void centerCanvasX() {
        if (scrollPane == null || workingImage == null) return;
        SwingUtilities.invokeLater(() -> {
            canvasWrapper.revalidate();
            canvasWrapper.validate();
            JViewport vp = scrollPane.getViewport();
            Dimension vpSz = vp.getSize();
            int cw = (int) Math.ceil(workingImage.getWidth() * zoom);
            int viewX = Math.max(0, (cw - vpSz.width) / 2);
            int viewY = vp.getViewPosition().y; // keep vertical position unchanged
            vp.setViewPosition(new Point(viewX, viewY));
        });
    }

    /**
     * Opens a tiny popup where the user can type a zoom percentage directly.
     * Activated by double-clicking the zoom label.
     */
    private void showZoomInput() {
        JTextField tf = new JTextField(String.valueOf(Math.round(zoom * 100)), 5);
        tf.setBackground(AppColors.BTN_BG);
        tf.setForeground(AppColors.TEXT);
        tf.setCaretColor(AppColors.TEXT);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tf.setHorizontalAlignment(JTextField.CENTER);
        tf.setBorder(BorderFactory.createLineBorder(AppColors.ACCENT));

        JDialog popup = new JDialog(this, false);
        popup.setUndecorated(true);
        popup.setSize(80, 28);
        popup.setLocationRelativeTo(zoomLabel);
        popup.add(tf);

        tf.selectAll();
        tf.addActionListener(ev -> {
            try {
                double pct = Double.parseDouble(tf.getText().trim().replace("%", ""));
                setZoom(pct / 100.0, null);
            } catch (NumberFormatException ignored) {}
            popup.dispose();
        });
        tf.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) popup.dispose();
            }
        });
        popup.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {}
            @Override public void windowLostFocus(java.awt.event.WindowEvent e)  { popup.dispose(); }
        });
        popup.setVisible(true);
        tf.requestFocusInWindow();
    }

    /** Creates a new blank ARGB bitmap after asking for dimensions. */
    private void doNewBitmap() {
        JTextField wField = new JTextField("1024", 5);
        JTextField hField = new JTextField("1024", 5);
        for (JTextField f : new JTextField[]{wField, hField}) {
            f.setBackground(AppColors.BTN_BG);
            f.setForeground(AppColors.TEXT);
            f.setCaretColor(AppColors.TEXT);
        }
        JPanel grid = new JPanel(new GridLayout(2, 2, 6, 4));
        grid.setOpaque(false);
        JLabel wl = new JLabel("Breite (px):"); wl.setForeground(AppColors.TEXT);
        JLabel hl = new JLabel("Höhe  (px):"); hl.setForeground(AppColors.TEXT);
        grid.add(wl); grid.add(wField);
        grid.add(hl); grid.add(hField);

        JDialog dialog = createBaseDialog("Neue Bitmap", 300, 200);
        JPanel content = centeredColumnPanel(16, 20, 12);
        content.add(grid);
        content.add(Box.createVerticalStrut(14));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok  = UIComponentFactory.buildButton("Erstellen", AppColors.ACCENT,  AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG,  AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Math.max(1, Integer.parseInt(wField.getText().trim()));
                int nh = Math.max(1, Integer.parseInt(hField.getText().trim()));
                if (sourceFile != null) saveCurrentState();
                workingImage      = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                originalImage     = deepCopy(workingImage);
                activeElements    = new ArrayList<>();
                selectedElements.clear();
                undoStack.clear(); redoStack.clear();
                selectedAreas.clear();
                floatSelectionState.clear();  // DELEGATED

                // Create and save new file
                File saveDir = lastIndexedDir != null ? lastIndexedDir : new File(System.getProperty("user.home"));
                int counter = 1;
                File newFile;
                do {
                    newFile = new File(saveDir, "Untitled_" + counter + ".png");
                    counter++;
                } while (newFile.exists());

                try {
                    ImageIO.write(workingImage, "PNG", newFile);
                    sourceFile = newFile;
                    hasUnsavedChanges = false;
                    dirtyFiles.remove(sourceFile);

                    // Update tile gallery
                    if (!directoryImages.contains(newFile)) {
                        directoryImages.add(newFile);
                        tileGallery.addFiles(Arrays.asList(newFile));
                    }
                    tileGallery.setActiveFile(newFile);
                    currentImageIndex = directoryImages.indexOf(newFile);
                } catch (IOException ex) {
                    showErrorDialog("Speicherfehler", "Neue Bitmap konnte nicht gespeichert werden:\n" + ex.getMessage());
                    return;
                }

                swapToImageView();
                SwingUtilities.invokeLater(this::fitToViewport);
                updateTitle();
                updateStatus();
                updateDirtyUI();
                setBottomButtonsEnabled(true);
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte ganzzahlige Pixelwerte eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok); row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    /** Lets the user choose one or both checkerboard background colors. */
    private void showCanvasBgDialog() {
        JPanel preview = new JPanel() {
            private static final long serialVersionUID = 1L;

			@Override protected void paintComponent(Graphics g) {
                int cell = 16;
                for (int r = 0; r < getHeight(); r += cell)
                    for (int c = 0; c < getWidth(); c += cell) {
                        boolean even = ((r / cell) + (c / cell)) % 2 == 0;
                        g.setColor(even ? canvasBg1 : canvasBg2);
                        g.fillRect(c, r, Math.min(cell, getWidth()-c), Math.min(cell, getHeight()-r));
                    }
            }
        };
        preview.setPreferredSize(new Dimension(120, 60));

        JButton btn1 = UIComponentFactory.buildButton("Farbe 1", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton btn2 = UIComponentFactory.buildButton("Farbe 2", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton btnBoth = UIComponentFactory.buildButton("Beide", AppColors.BTN_BG, AppColors.BTN_HOVER);

        btn1.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe 1", canvasBg1);
            if (c != null) { canvasBg1 = c; preview.repaint(); canvasPanel.repaint(); }
        });
        btn2.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe 2", canvasBg2);
            if (c != null) { canvasBg2 = c; preview.repaint(); canvasPanel.repaint(); }
        });
        btnBoth.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe", canvasBg1);
            if (c != null) { canvasBg1 = c; canvasBg2 = c; preview.repaint(); canvasPanel.repaint(); }
        });

        JDialog dialog = createBaseDialog("Canvas-Hintergrund", 380, 240);
        JPanel content = centeredColumnPanel(16, 20, 12);
        content.add(preview);
        content.add(Box.createVerticalStrut(12));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.add(btn1); row.add(btn2); row.add(btnBoth);
        content.add(row);
        content.add(Box.createVerticalStrut(12));
        JButton closeBtn = UIComponentFactory.buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        closeBtn.setAlignmentX(CENTER_ALIGNMENT);
        closeBtn.addActionListener(e -> dialog.dispose());
        content.add(closeBtn);
        dialog.add(content);
        dialog.setVisible(true);
    }

    // =========================================================================
    // UI state helpers
    // =========================================================================
    public void swapToImageView() {
        if (dropHintPanel.getParent() == layeredPane) layeredPane.remove(dropHintPanel);
        if (viewportPanel.getParent() == null) {
            int w = layeredPane.getWidth()  > 0 ? layeredPane.getWidth()  : 860;
            int h = layeredPane.getHeight() > 0 ? layeredPane.getHeight() : 560;
            viewportPanel.setBounds(0, 0, w, h);
            layeredPane.add(viewportPanel, JLayeredPane.DEFAULT_LAYER);
            setupDropTarget(viewportPanel);
            setupDropTarget(canvasPanel);
        }
        viewportPanel.setVisible(true);
        repositionNavButtons();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void updateNavigationButtons() {
        prevNavButton.setEnabled(currentImageIndex > 0);
        nextNavButton.setEnabled(currentImageIndex < directoryImages.size() - 1);
    }

    public void updateTitle() {
        if (sourceFile == null) { setTitle("Selective Alpha Editor"); return; }
        String dirty = hasUnsavedChanges ? " •" : "";
        String mode  = appMode == AppMode.PAINT ? "[Paint]"
                : floodfillMode ? "[Floodfill]" : "[Selective Alpha]";
        setTitle("Selective Alpha Editor  " + mode + "  –  " + sourceFile.getName() + dirty);
    }

    public void updateStatus() {
        if (sourceFile == null) { statusLabel.setText("Keine Datei geladen"); return; }
        statusLabel.setText(sourceFile.getName()
                + "   |   " + (currentImageIndex + 1) + " / " + directoryImages.size()
                + "   |   " + workingImage.getWidth() + " × " + workingImage.getHeight() + " px");
    }

    public void setBottomButtonsEnabled(boolean enabled) {
        boolean sel = !floodfillMode && appMode == AppMode.ALPHA_EDITOR;
        applyButton.setEnabled(enabled && sel);
        clearSelectionsButton.setEnabled(enabled && sel);
        if (actionPanel == null) return;
        for (java.awt.Component c : actionPanel.getComponents())
            if (c instanceof JButton btn && ("resetButton".equals(btn.getName()) || "saveButton".equals(btn.getName())))
                btn.setEnabled(enabled);
    }

    // =========================================================================
    // Custom Dialogs
    // =========================================================================
//    private int showUnsavedChangesDialog() {
//        if (!hasUnsavedChanges) return 1;
//        final int[] result = { 2 };
//        JDialog dialog = createBaseDialog("Ungespeicherte Änderungen", 420, 310);
//        JPanel  content = centeredColumnPanel(20, 28, 16);
//        content.add(styledLabel("⚠", 30, AppColors.WARNING, Font.PLAIN));
//        content.add(Box.createVerticalStrut(10));
//        content.add(htmlLabel("Das Bild hat ungespeicherte Änderungen.<br>Was möchtest du tun?", AppColors.TEXT, 13));
//        content.add(Box.createVerticalStrut(18));
//        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
//        row.setOpaque(false);
//        JButton sBtn = UIComponentFactory.buildButton("Speichern",  AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
//        JButton dBtn = UIComponentFactory.buildButton("Verwerfen",  AppColors.DANGER,  AppColors.DANGER_HOVER);
//        JButton cBtn = UIComponentFactory.buildButton("Abbrechen",  AppColors.BTN_BG,  AppColors.BTN_HOVER);
//        sBtn.setForeground(Color.WHITE); dBtn.setForeground(Color.WHITE);
//        sBtn.addActionListener(e -> { result[0] = 0; dialog.dispose(); });
//        dBtn.addActionListener(e -> { result[0] = 1; dialog.dispose(); });
//        cBtn.addActionListener(e -> { result[0] = 2; dialog.dispose(); });
//        row.add(sBtn); row.add(dBtn); row.add(cBtn);
//        content.add(row);
//        dialog.add(content);
//        dialog.setVisible(true);
//        return result[0];
//    }

    private void showErrorDialog(String title, String message) {
        JDialog dialog = createBaseDialog(title, 440, 215);
        JPanel content = centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = htmlLabel(message.replace("\n","<br>"), AppColors.TEXT, 12);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.DANGER, AppColors.DANGER_HOVER);
        ok.setForeground(Color.WHITE); ok.setAlignmentX(CENTER_ALIGNMENT);
        ok.addActionListener(e -> dialog.dispose());
        content.add(styledLabel("✕", 26, AppColors.DANGER, Font.BOLD));
        content.add(Box.createVerticalStrut(6));
        content.add(styledLabel(title, 13, AppColors.DANGER, Font.BOLD));
        content.add(Box.createVerticalStrut(8));
        content.add(msgLbl);
        content.add(Box.createVerticalStrut(16));
        content.add(ok);
        dialog.add(content); dialog.setVisible(true);
    }

    private void showInfoDialog(String title, String message) {
        JDialog dialog = createBaseDialog(title, 400, 200);
        JPanel content = centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = htmlLabel(message.replace("\n","<br>"), AppColors.TEXT, 13);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        ok.setForeground(Color.WHITE); ok.setAlignmentX(CENTER_ALIGNMENT);
        ok.addActionListener(e -> dialog.dispose());
        content.add(styledLabel("ℹ", 26, AppColors.ACCENT, Font.PLAIN));
        content.add(Box.createVerticalStrut(8));
        content.add(msgLbl);
        content.add(Box.createVerticalStrut(16));
        content.add(ok);
        dialog.add(content); dialog.setVisible(true);
    }

    private JDialog createBaseDialog(String title, int w, int h) {
        JDialog d = new JDialog(this, title, true);
        d.setSize(w, h); d.setLocationRelativeTo(this); d.setResizable(false);
        d.getContentPane().setBackground(AppColors.BG_PANEL);
        d.setLayout(new BorderLayout());
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleBar.setBackground(new Color(35,35,35));
        titleBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0, AppColors.BORDER));
        JLabel tl = new JLabel(title);
        tl.setForeground(AppColors.TEXT); tl.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleBar.add(tl); d.add(titleBar, BorderLayout.NORTH);
        return d;
    }

    private JPanel centeredColumnPanel(int vp, int hp, int bp) {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppColors.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(vp, hp, bp, hp)); return p;
    }

    private JLabel styledLabel(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size)); l.setForeground(color);
        l.setAlignmentX(CENTER_ALIGNMENT); return l;
    }

    private JLabel htmlLabel(String html, Color color, int size) {
        JLabel l = new JLabel("<html><center>" + html + "</center></html>");
        l.setForeground(color); l.setFont(new Font("SansSerif", Font.PLAIN, size));
        l.setAlignmentX(CENTER_ALIGNMENT); return l;
    }

    // Note: buildButton(), buildModeToggleBtn(), buildNavButton() are now in UIComponentFactory.java

    // =========================================================================
    // Element helpers
    // =========================================================================

    /** Screen-space rectangle for a layer, accounting for current zoom. */
    @Override public Rectangle elemRectScreen(Layer el) {
        return new Rectangle(
            (int) Math.round(el.x()      * zoom),
            (int) Math.round(el.y()      * zoom),
            (int) Math.round(el.width()  * zoom),
            (int) Math.round(el.height() * zoom));
    }

    /**
     * Renders a TextLayer to a pixel image at its natural (image-space) font size.
     * Used when burning a text layer into the working image.
     */
    private BufferedImage renderTextLayerToImage(TextLayer tl) {
        int style = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
        Font font = new Font(tl.fontName(), style, Math.max(6, tl.fontSize()));
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
        String[] lines = tl.text().split("\n", -1);
        int w = 1;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int h = Math.max(1, fm.getHeight() * lines.length);
        BufferedImage img = new BufferedImage(w + TextLayer.TEXT_PADDING * 2, h + TextLayer.TEXT_PADDING * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        g2.setColor(tl.fontColor());
        for (int i = 0; i < lines.length; i++) {
            g2.drawString(lines[i], TextLayer.TEXT_PADDING, TextLayer.TEXT_PADDING + fm.getHeight() * i + fm.getAscent());
        }
        g2.dispose();
        return img;
    }

    /**
     * Inserts the current clipboard / selection as a new Element (non-destructive layer).
     * The underlying canvas pixels are NOT modified until the element is merged
     * via mergeElementToCanvas().
     */
    private void insertSelectionAsElement() {
        BufferedImage src = null;
        Rectangle sel = getActiveSelection();
        if (sel != null && workingImage != null) {
            src = PaintEngine.cropRegion(workingImage, sel);
        } else if (clipboard != null) {
            src = deepCopy(clipboard);
        }
        if (src == null) { showInfoDialog("Kein Inhalt", "Nichts zum Einfügen als Element."); return; }

        int ex = sel != null ? sel.x : 0;
        int ey = sel != null ? sel.y : 0;
        Layer el = new ImageLayer(nextElementId++, src, ex, ey, src.getWidth(), src.getHeight());
        activeElements.add(el);
        selectedElements.clear();
        selectedElements.add(el);
        selectedAreas.clear();
        markDirty();
        refreshElementPanel();
    }

    /** Merges all selected layers onto the canvas and removes them from the layer list. */
    private void mergeSelectedElements() {
        // DELEGATED to ElementLayerState manager
        if (selectedElements.isEmpty() || workingImage == null) return;
        elementLayerState.setWorkingImage(workingImage);
        elementLayerState.mergeSelectedElements();
        // Sync legacy fields
        activeElements.clear();
        activeElements.addAll(elementLayerState.getActiveElements());
        selectedElements.clear();
    }

    /** Deletes all selected layers without merging to canvas. */
    private void deleteSelectedElements() {
        // DELEGATED to ElementLayerState manager
        if (selectedElements.isEmpty()) return;
        elementLayerState.deleteSelectedElements();
        // Sync legacy fields
        activeElements.clear();
        activeElements.addAll(elementLayerState.getActiveElements());
        selectedElements.clear();
    }

    /** Toggles a layer in/out of the multi-selection. New primary is put at index 0. */
    private void doToggleElementSelection(Layer el) {
        for (int i = 0; i < selectedElements.size(); i++) {
            if (selectedElements.get(i).id() == el.id()) {
                selectedElements.remove(i);
                refreshElementPanel();
                return;
            }
        }
        selectedElements.add(0, el);
        refreshElementPanel();
    }

    // =========================================================================
    // Utilities
    // =========================================================================
    private static boolean isSupportedFile(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase();
        for (String e : SUPPORTED_EXTENSIONS) if (n.endsWith("." + e)) return true;
        return false;
    }

    public BufferedImage deepCopy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = c.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return c;
    }

//    private void copyInto(BufferedImage src, BufferedImage dst) {
//        Graphics2D g2 = dst.createGraphics();
//        g2.setComposite(AlphaComposite.Src);
//        g2.drawImage(src, 0, 0, null);
//        g2.dispose();
//    }

    // =========================================================================
    // CanvasCallbacks Implementation (for CanvasPanel)
    // =========================================================================
    @Override public BufferedImage getWorkingImage() { return workingImage; }
    @Override public AppMode getAppMode() { return appMode; }
    @Override public boolean isFloodfillMode() { return floodfillMode; }
    @Override public double getZoom() { return zoom; }
    @Override public JScrollPane getScrollPane() { return scrollPane; }

    @Override public List<Rectangle> getSelectedAreas() { return selectedAreas; }
    @Override public boolean isSelecting() { return isSelecting; }
    @Override public void setSelecting(boolean selecting) { isSelecting = selecting; }
    @Override public Point getSelectionStart() { return selectionStart; }
    @Override public void setSelectionStart(Point p) { selectionStart = p; }
    @Override public Point getSelectionEnd() { return selectionEnd; }
    @Override public void setSelectionEnd(Point p) { selectionEnd = p; }

    @Override public List<Layer> getActiveElements() { return activeElements; }
    @Override public Layer getSelectedElement() {
        return selectedElements.isEmpty() ? null : selectedElements.get(0);
    }
    @Override public void setSelectedElement(Layer el) {
        selectedElements.clear();
        if (el != null) selectedElements.add(el);
        refreshElementPanel();
    }
    @Override public List<Layer> getSelectedElements() { return selectedElements; }
    @Override public void setSelectedElements(List<Layer> els) {
        selectedElements.clear();
        selectedElements.addAll(els);
        refreshElementPanel();
        if (canvasPanel != null) canvasPanel.repaint();
    }
    @Override public void toggleElementSelection(Layer el) {
        doToggleElementSelection(el);
        refreshElementPanel();
        if (canvasPanel != null) canvasPanel.repaint();
    }
    @Override public void moveSelectedElements(int dx, int dy) {
        // DELEGATED to ElementLayerState manager
        if (dx == 0 && dy == 0) return;
        elementLayerState.moveSelectedElements(dx, dy);
        // Sync legacy fields
        activeElements.clear();
        activeElements.addAll(elementLayerState.getActiveElements());
        selectedElements.clear();
        for (Layer el : elementLayerState.getSelectedLayers()) {
            selectedElements.add(el);
        }
    }
    @Override public int getNextElementId() {
        return nextElementId++;
    }
    @Override public void addElement(Layer el) {
        if (el != null) {
            activeElements.add(el);
            refreshElementPanel();
            markDirty();
            if (canvasPanel != null) canvasPanel.repaint();
        }
    }

    @Override public BufferedImage getFloatingImage() { return floatingImg; }
    @Override public Rectangle getFloatRect() { return floatRect; }
    @Override public boolean isDraggingFloat() { return isDraggingFloat; }
    @Override public void setDraggingFloat(boolean dragging) { isDraggingFloat = dragging; }
    @Override public Point getFloatDragAnchor() { return floatDragAnchor; }
    @Override public void setFloatDragAnchor(Point p) { floatDragAnchor = p; }
    @Override public int getActiveHandle() { return activeHandle; }
    @Override public void setActiveHandle(int handle) { activeHandle = handle; }
    @Override public Rectangle getScaleBaseRect() { return scaleBaseRect; }
    @Override public void setScaleBaseRect(Rectangle r) { scaleBaseRect = r; }
    @Override public Point getScaleDragStart() { return scaleDragStart; }
    @Override public void setScaleDragStart(Point p) { scaleDragStart = p; }

    @Override public Point getLastPaintPoint() { return lastPaintPoint; }
    @Override public void setLastPaintPoint(Point p) { lastPaintPoint = p; }
    @Override public Point getShapeStartPoint() { return shapeStartPoint; }
    @Override public void setShapeStartPoint(Point p) { shapeStartPoint = p; }
    @Override public BufferedImage getPaintSnapshot() { return paintSnapshot; }
    @Override public void setPaintSnapshot(BufferedImage img) { paintSnapshot = img; }

    @Override public int getElemActiveHandle() { return elemActiveHandle; }
    @Override public void setElemActiveHandle(int handle) { elemActiveHandle = handle; }
    @Override public Rectangle getElemScaleBase() { return elemScaleBase; }
    @Override public void setElemScaleBase(Rectangle r) { elemScaleBase = r; }
    @Override public Point getElemScaleStart() { return elemScaleStart; }
    @Override public void setElemScaleStart(Point p) { elemScaleStart = p; }
    @Override public boolean isDraggingElement() { return draggingElement; }
    @Override public void setDraggingElement(boolean dragging) { draggingElement = dragging; }
    @Override public Point getElemDragAnchor() { return elemDragAnchor; }
    @Override public void setElemDragAnchor(Point p) { elemDragAnchor = p; }

    @Override public PaintToolbar getPaintToolbar() { return paintToolbar; }

    @Override public boolean isShowAllLayerOutlines() {
        return elementLayerPanel != null && elementLayerPanel.isShowing()
                && elementLayerPanel.isShowAllOutlines();
    }

    @Override public void commitTextAsElement(BufferedImage textImg, int x, int y) {
        if (textImg == null || appMode != AppMode.PAINT) return;
        Layer el = new ImageLayer(nextElementId++, textImg, x, y, textImg.getWidth(), textImg.getHeight());
        activeElements.add(el);
        selectedElements.clear();
        selectedElements.add(el);
        refreshElementPanel();
        markDirty();
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public void commitTextLayer(int updateId, String text, String fontName, int fontSize,
                                          boolean bold, boolean italic, java.awt.Color color, int x, int y) {
        // DELEGATED to ElementLayerState manager
        elementLayerState.setAppMode(appMode);
        elementLayerState.setWorkingImage(workingImage);
        elementLayerState.commitTextLayer(updateId, text, fontName, fontSize, bold, italic, color, x, y);

        // Sync legacy fields
        activeElements.clear();
        activeElements.addAll(elementLayerState.getActiveElements());
        selectedElements.clear();
        Layer sel = elementLayerState.getSelectedLayer();
        if (sel != null) selectedElements.add(sel);
        refreshElementPanel();
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public void repaintCanvas() { canvasPanel.repaint(); }

    @Override public void onCanvasElementHover(int elementId) {
        if (elementLayerPanel != null) elementLayerPanel.setHoveredElement(elementId);
    }

    @Override public void clearSelection() {
        selectedAreas.clear();
        isSelecting = false; selectionStart = null; selectionEnd = null;
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public void liftSelectionToFloat() {
        Rectangle sel = getActiveSelection();
        if (sel == null || workingImage == null) return;
        int x = Math.max(0, sel.x),  y = Math.max(0, sel.y);
        int w = Math.min(sel.width,  workingImage.getWidth()  - x);
        int h = Math.min(sel.height, workingImage.getHeight() - y);
        if (w <= 0 || h <= 0) return;
        Rectangle clipped = new Rectangle(x, y, w, h);
        pushUndo();
        floatingImg  = PaintEngine.cropRegion(workingImage, clipped);
        floatRect    = new Rectangle(clipped);
        PaintEngine.clearRegion(workingImage, clipped);
        selectedAreas.clear();
        isSelecting = false; selectionStart = null; selectionEnd = null;
        isDraggingFloat = false; floatDragAnchor = null;
        activeHandle = -1; scaleBaseRect = null; scaleDragStart = null;
        markDirty();
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public boolean isCanvasSubMode() {
        return appMode == AppMode.PAINT && canvasModeBtn != null && canvasModeBtn.isSelected();
    }

    @Override public void liftSelectionToElement(Rectangle sel) {
        if (sel == null || workingImage == null) return;
        int x = Math.max(0, sel.x), y = Math.max(0, sel.y);
        int w = Math.min(sel.width,  workingImage.getWidth()  - x);
        int h = Math.min(sel.height, workingImage.getHeight() - y);
        if (w <= 0 || h <= 0) return;
        Rectangle clipped = new Rectangle(x, y, w, h);
        pushUndo();
        BufferedImage crop = PaintEngine.cropRegion(workingImage, clipped);
        PaintEngine.clearRegion(workingImage, clipped);
        Layer el = new ImageLayer(nextElementId++, crop, clipped.x, clipped.y, clipped.width, clipped.height);
        activeElements.add(el);
        selectedElements.clear();
        selectedElements.add(el);
        selectedAreas.clear();
        isSelecting = false; selectionStart = null; selectionEnd = null;
        markDirty();
        refreshElementPanel();
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public void deleteSelection() {
        if (floatingImg != null) {
            // Discard float pixels (region already cleared when lifted)
            floatSelectionState.clear();  // DELEGATED: clears all 7 fields
            markDirty();
        } else if (!selectedAreas.isEmpty() && workingImage != null) {
            pushUndo();
            for (Rectangle r : selectedAreas) PaintEngine.clearRegion(workingImage, r);
            selectedAreas.clear();
            isSelecting = false; selectionStart = null; selectionEnd = null;
            markDirty();
        }
        if (canvasPanel != null) canvasPanel.repaint();
    }

    @Override public void paintDot(Point imagePt) {
        boolean aa = paintToolbar != null && paintToolbar.isAntialiasing();
        PaintEngine.drawPencil(workingImage, imagePt, imagePt,
                paintToolbar.getPrimaryColor(),
                paintToolbar.getStrokeWidth(),
                paintToolbar.getBrushShape(), aa);
        markDirty();
    }

    @Override public void updateSelectedElement(Layer el) {
        if (el != null && !selectedElements.isEmpty()) {
            Layer primary = selectedElements.get(0);
            for (int j = 0; j < activeElements.size(); j++) {
                if (activeElements.get(j).id() == primary.id()) { activeElements.set(j, el); break; }
            }
            selectedElements.set(0, el);
            markDirty();
            refreshElementPanel();
        }
    }

    // =========================================================================
    // Public API for EditorDialogs
    // =========================================================================
    // Note: getWorkingImage() is in CanvasCallbacks Implementation section
    public void setWorkingImage(BufferedImage img) { workingImage = img; }
    public BufferedImage getOriginalImage() { return originalImage; }
    public void setOriginalImage(BufferedImage img) { originalImage = img; }
    public File getSourceFile() { return sourceFile; }
    public void setSourceFile(File f) { sourceFile = f; }
    public JPanel getCanvasWrapper() { return canvasWrapper; }
    public JPanel getCanvasPanel() { return canvasPanel; }
    public JLabel getZoomLabel() { return zoomLabel; }
    public Color getCanvasBg1() { return canvasBg1; }
    public Color getCanvasBg2() { return canvasBg2; }
    public void setCanvasBg1(Color c) { canvasBg1 = c; }
    public void setCanvasBg2(Color c) { canvasBg2 = c; }
    public RulerUnit getRulerUnit() { return rulerUnit; }
}
