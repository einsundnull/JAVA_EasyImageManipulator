package paint;

import java.awt.AlphaComposite;
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
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import book.PaperFormat;

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
public class SelectiveAlphaEditor extends JFrame implements RulerCallbacks {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
    private static final int    MAX_UNDO  = 50;

    // Zoom settings (non-final for runtime adjustment)
    private double ZOOM_MIN  = 0.05;
    private double ZOOM_MAX  = 16.0;        // max: 16x16 pixels
    private double ZOOM_STEP = 0.10;
    private double ZOOM_FACTOR = 1.08;      // progressive zoom: 8% per notch

    private static final int GRID_CELL    = 16;   // image-space pixels per grid cell
    private static final int RULER_THICK  = 20;   // pixels wide/tall for ruler strip
    private static final double SCREEN_DPI = 96.0;

    private static final int TOPBAR_BTN_W      = 36;
    private static final int TOPBAR_BTN_H      = 36;
    private static final int TOPBAR_ZOOM_BTN_W = 36;
    private static final int TOPBAR_ZOOM_BTN_H = 36;

    // ── Gallery shrinking behavior ─────────────────────────────────────────────
    // true  → galleries shrink when both canvases shown, tiles scale to fit
    // false → canvases shrink to preserve gallery widths
    private static final boolean SHRINK_GALLERY = true;

    // ── Ruler unit ────────────────────────────────────────────────────────────
    // RulerUnit is now defined in RulerUnit.java (extracted as separate enum)

    // ── Canvas array (multiple independent canvases) ────────────────────────────
    private final CanvasInstance[] canvases = new CanvasInstance[2];
    private int activeCanvasIndex = 0;

    // Convenience accessors
    private CanvasInstance ci() { return canvases[activeCanvasIndex]; }
    private CanvasInstance ci(int idx) { return canvases[idx]; }

    // ── Shared global state (not per-canvas) ───────────────────────────────────
    private BufferedImage clipboard;
    private List<Layer> clipboardLayers;  // For copying/pasting layers between canvases
    private Point         pasteOffset;

    private AppMode  appMode           = AppMode.ALPHA_EDITOR;
    private boolean  floodfillMode     = false;
    private boolean  showGrid          = false;
    private boolean  showRuler         = false;
    private RulerUnit rulerUnit        = RulerUnit.PX;

    // ── File cache (images stay alive while navigating, dirty until saved) ────
    /** Files with unsaved changes (shown red in gallery).                        */
    private final Set<File> dirtyFiles = new HashSet<>();

    // ── Directory browsing (gallery multiselect) ──────────────────────────────
    private List<File> selectedImages    = new ArrayList<>();

    // ── Project Management ────────────────────────────────────────────────────
    private ProjectManager projectManager = new ProjectManager();

    // ── NEW: State Managers (Modularization) ───────────────────────────────────
    private ZoomState         zoomState              = new ZoomState();
    private FloatSelectionState floatSelectionState  = new FloatSelectionState();
    private FileStateCache    fileCacheManager       = new FileStateCache();

    // ── Secondary Canvas Window (F1/F2/F3/F4/F5/F7) ──────────────────────────────
    private enum PreviewMode { SNAPSHOT, LIVE_ALL, LIVE_ALL_EDIT }
    private enum AlwaysOnTopMode { TO_FRONT, NORMAL, TO_BACKGROUND }
    private enum CanvasDisplayMode { SHOW_CANVAS_I_ONLY, SHOW_CANVAS_II_ONLY, SHOW_ACTIVE_CANVAS }
    private JFrame        secWin;
    private SecondaryPanel secPanel;
    private PreviewMode   secMode    = PreviewMode.LIVE_ALL;
    private CanvasDisplayMode secCanvasMode = CanvasDisplayMode.SHOW_CANVAS_I_ONLY;
    private BufferedImage secSnapshot;
    private javax.swing.Timer secTimer;
    private boolean       secFullscreen = true;
    private AlwaysOnTopMode secAlwaysOnTop = AlwaysOnTopMode.TO_BACKGROUND;
    private int           secOldX, secOldY, secOldW, secOldH;  // For fullscreen restoration

    // ── Element layers ────────────────────────────────────────────────────────
    // All per-canvas element state now in CanvasInstance

    // ── Canvas background ─────────────────────────────────────────────────────
    private Color canvasBg1 = new Color(200, 200, 200);
    private Color canvasBg2 = new Color(160, 160, 160);
    private Color canvasBg1Backup = null;  // for QuickBG toggle

    // ── Filmstrip sidebar + toggles ────────────────────────────────────────────
    private JPanel        galleryWrapper;
    private JToggleButton filmstripBtn;
    private JToggleButton secondCanvasBtn;
    private JToggleButton secondGalleryBtn;

    // ── Element layer panels (shown in Canvas mode) ──────────────────────────
    private ElementLayerPanel elementLayerPanel;    // For canvas 0
    private ElementLayerPanel elementLayerPanel2;   // For canvas 1
    private JToggleButton firstElementsBtn;         // Toggle for elementLayerPanel
    private JToggleButton secondElementsBtn;        // Toggle for elementLayerPanel2

    // ── Maps panel (toggle-able list view) ─────────────────────────────────────
    private MapsPanel mapsPanel;
    private JToggleButton mapsBtn;                  // Toggle for mapsPanel

    // ── Quick open and drop zone toggle ────────────────────────────────────────
    private JButton quickOpenBtn;                   // Quick open recent projects
    private JToggleButton toggleDropZoneBtn;        // Toggle drop zone visibility for canvas 2

    // ── Element-edit mode (double-click on layer tile) ────────────────────────
    private Layer elementEditSourceLayer;   // the layer being edited
    private int   elementEditSourceIdx;     // canvas that owns the layer
    private int   elementEditTargetIdx;     // canvas where the temp image is loaded

    // ── Shared UI components ───────────────────────────────────────────────────
    private HRulerPanel   hRuler;
    private VRulerPanel   vRuler;
    private JPanel        rulerCorner;
    private JPanel        rulerNorthBar;   // container for rulerCorner + hRuler
    private JPanel        actionPanel;     // Holds apply/clear/reset/save buttons
    private JPanel        rightDropZone;   // drag-activation overlay
    private JToggleButton firstCanvasBtn;  // Toggle for ci(0).layeredPane visibility
    private JPanel        mainDividerPanel; // Thin vertical separator between Canvas 1 and 2

    private JLabel  statusLabel;
    private JLabel  modeLabel;
    private JLabel  zoomLabel;
    private JButton applyButton;
    private JButton clearSelectionsButton;
    private JButton prevNavButton;
    private JButton nextNavButton;
    private JToggleButton paintModeBtn;
    private JToggleButton canvasModeBtn;
    private JToggleButton bookModeBtn;
    private JToggleButton sceneModeBtn;

    private PaintToolbar paintToolbar;

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SelectiveAlphaEditor::new);
    }

    // =========================================================================
    // Constructors
    // =========================================================================
    public SelectiveAlphaEditor() {
        // Initialize canvas array
        canvases[0] = new CanvasInstance();
        canvases[1] = new CanvasInstance();
        activeCanvasIndex = 0;
        initializeUI();
    }

    public SelectiveAlphaEditor(File imageFile, boolean floodfillMode) {
        // Initialize canvas array
        canvases[0] = new CanvasInstance();
        canvases[1] = new CanvasInstance();
        activeCanvasIndex = 0;
        this.floodfillMode = floodfillMode;
        initializeUI();
        loadFile(imageFile, 0);
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
            if (ci(0).canvasPanel != null) {
                ci(0).canvasPanel.setTextFontName(settings.getFontName());
                ci(0).canvasPanel.setTextFontSize(settings.getFontSize());
                ci(0).canvasPanel.setTextBold(settings.isTextBold());
                ci(0).canvasPanel.setTextItalic(settings.isTextItalic());
                ci(0).canvasPanel.setTextColor(new Color(settings.getFontColor(), true));
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
            if (wasMax != isMax && ci(0).workingImage != null) {
                SwingUtilities.invokeLater(() -> {
                    if (!ci(0).userHasManuallyZoomed) {
                        fitToViewport(0);
                    } else {
                        centerCanvasX(0);
                    }
                    if (ci(0).canvasPanel != null) ci(0).canvasPanel.repaint();
                });
            }
        });

        initSecondaryWindow();

        // Show StartupDialog for recently used projects (always show)
        SwingUtilities.invokeLater(() -> {
            try {
                Map<String, List<String>> recent = LastProjectsManager.loadAll();
                StartupDialog dlg = new StartupDialog(SelectiveAlphaEditor.this, recent);
                dlg.setVisible(true);
                File chosen = dlg.getSelectedPath();
                if (chosen != null && chosen.isDirectory()) {
                    // Open directory in gallery - find and load first image
                    File[] images = chosen.listFiles(f -> f.isFile() && isSupportedFile(f));
                    if (images != null && images.length > 0) {
                        java.util.Arrays.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        indexDirectory(images[0], activeCanvasIndex);
                        // Show TileGalleryPanel when opening from StartDialog
                        filmstripBtn.setSelected(true);
                        ci(activeCanvasIndex).tileGallery.setVisible(true);
                        updateLayoutVisibility();
                    }
                }
            } catch (IOException e) {
                System.err.println("[WARN] Konnte lastProjects nicht laden: " + e.getMessage());
            }
        });

        setVisible(true);
    }

    private void onApplicationClosing() {
        try {
            // Speichere aktuelle Szene
            if (ci(0).sourceFile != null && ci(0).workingImage != null) {
                projectManager.saveScene(ci(0).sourceFile, ci(0).activeElements, ci(0).zoom, appMode);
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
                if (paintToolbar.getActiveTool() != null)
                    settings.setActiveTool(paintToolbar.getActiveTool().toString());
                settings.setFillMode(paintToolbar.getFillMode().toString());
                settings.setBrushShape(paintToolbar.getBrushShape().toString());
            }

            // Text-Tool
            if (ci(0).canvasPanel != null) {
                settings.setFontName(ci(0).canvasPanel.getTextFontName());
                settings.setFontSize(ci(0).canvasPanel.getTextFontSize());
                settings.setTextBold(ci(0).canvasPanel.isTextBold());
                settings.setTextItalic(ci(0).canvasPanel.isTextItalic());
                settings.setFontColor(ci(0).canvasPanel.getTextColor().getRGB());
            }

            // Speichern
            settings.save();
        } catch (IOException e) {
            System.err.println("[ERROR] Fehler beim Speichern der Einstellungen: " + e.getMessage());
        }

        // Stop secondary window timer
        if (secTimer != null && secTimer.isRunning()) {
            secTimer.stop();
        }
        if (secWin != null) {
            secWin.dispose();
        }

        // Normal beenden
        System.exit(0);
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
        filmstripBtn.setSelected(false);  // Start hidden, only drop zone visible
        filmstripBtn.addActionListener(e -> {
            ci(0).tileGallery.setVisible(filmstripBtn.isSelected());
            updateLayoutVisibility();
            centerCanvas(0);
        });

        firstCanvasBtn = UIComponentFactory.buildModeToggleBtn("1", "1. Canvas ein-/ausblenden");
        firstCanvasBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        firstCanvasBtn.setSelected(true);   // Canvas 1 starts visible
        firstCanvasBtn.setEnabled(false);   // Enabled once canvas 0 has content
        firstCanvasBtn.addActionListener(e -> updateLayoutVisibility());

        secondCanvasBtn = UIComponentFactory.buildModeToggleBtn("2", "2. Canvas ein-/ausblenden");
        secondCanvasBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        secondCanvasBtn.setSelected(false);
        secondCanvasBtn.setEnabled(false);
        secondCanvasBtn.addActionListener(e -> updateLayoutVisibility());

        secondGalleryBtn = UIComponentFactory.buildModeToggleBtn("B", "2. Filmstreifen ein-/ausblenden");
        secondGalleryBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        secondGalleryBtn.setSelected(false);
        secondGalleryBtn.setEnabled(false);
        secondGalleryBtn.addActionListener(e -> {
            if (ci(1).tileGallery != null) {
                ci(1).tileGallery.setVisible(secondGalleryBtn.isSelected());
            }
            updateLayoutVisibility();
        });

        firstElementsBtn = UIComponentFactory.buildModeToggleBtn("E1", "1. Ebenen ein-/ausblenden");
        firstElementsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        firstElementsBtn.setSelected(false);
        firstElementsBtn.setEnabled(false);
        firstElementsBtn.addActionListener(e -> {
            if (elementLayerPanel != null) {
                elementLayerPanel.setVisible(firstElementsBtn.isSelected());
            }
            updateLayoutVisibility();
        });

        secondElementsBtn = UIComponentFactory.buildModeToggleBtn("E2", "2. Ebenen ein-/ausblenden");
        secondElementsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        secondElementsBtn.setSelected(false);
        secondElementsBtn.setEnabled(false);
        secondElementsBtn.addActionListener(e -> {
            if (elementLayerPanel2 != null) {
                elementLayerPanel2.setVisible(secondElementsBtn.isSelected());
            }
            updateLayoutVisibility();
        });

        mapsBtn = UIComponentFactory.buildModeToggleBtn("🗺", "Translation Maps ein-/ausblenden");
        mapsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        mapsBtn.setSelected(false);
        mapsBtn.addActionListener(e -> {
            if (mapsPanel != null) {
                mapsPanel.setVisible(mapsBtn.isSelected());
            }
            updateLayoutVisibility();
        });

        quickOpenBtn = UIComponentFactory.buildButton("📂", AppColors.BTN_BG, AppColors.BTN_HOVER);
        quickOpenBtn.setToolTipText("Schnellauswahl: Recent Projekte");
        quickOpenBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        quickOpenBtn.setForeground(AppColors.TEXT);
        quickOpenBtn.addActionListener(e -> showQuickOpenDialog());

        toggleDropZoneBtn = UIComponentFactory.buildModeToggleBtn("⬇", "Drop-Feld für 2. Canvas");
        toggleDropZoneBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        toggleDropZoneBtn.setSelected(false);
        toggleDropZoneBtn.addActionListener(e -> {
            if (rightDropZone != null) {
                rightDropZone.setVisible(toggleDropZoneBtn.isSelected());
            }
        });

        modeLabel = new JLabel("Modus: Selective Alpha");
        modeLabel.setForeground(AppColors.TEXT_MUTED);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        statusLabel = new JLabel("Keine Datei geladen");
        statusLabel.setForeground(AppColors.TEXT_MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        left.add(filmstripBtn);
        left.add(firstCanvasBtn);
        left.add(firstElementsBtn);
        left.add(secondCanvasBtn);
        left.add(secondGalleryBtn);
        left.add(secondElementsBtn);
        left.add(mapsBtn);
        left.add(quickOpenBtn);
        left.add(toggleDropZoneBtn);
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
        mergeElemBtn.addActionListener(e -> { if (!ci().selectedElements.isEmpty()) mergeSelectedElements(); });
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

        // QuickBG toggle: temporarily hide/show BG Color 1
        JButton quickBgBtn = UIComponentFactory.buildButton("Q-BG", AppColors.BTN_BG, AppColors.BTN_HOVER);
        quickBgBtn.setToolTipText("BG Color 1 temporär aus-/einblenden");
        quickBgBtn.addActionListener(e -> toggleQuickBG());
        quickBgBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

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

        // Mode toggle buttons
        canvasModeBtn = UIComponentFactory.buildModeToggleBtn("\u25a6", "Canvas-Modus: Layer-Verwaltung – nur im Paint-Modus (STRG+A = Alle auswählen)");
        canvasModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        canvasModeBtn.setEnabled(false); // enabled only while Paint mode is active
        canvasModeBtn.addActionListener(e -> toggleCanvasMode());

        paintModeBtn = UIComponentFactory.buildModeToggleBtn("\u270f", "Paint-Modus aktivieren / deaktivieren");
        paintModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        paintModeBtn.addActionListener(e -> togglePaintMode());

        bookModeBtn = UIComponentFactory.buildModeToggleBtn("Book", "Buch-Modus aktivieren / deaktivieren");
        bookModeBtn.setPreferredSize(new Dimension(50, TOPBAR_BTN_H));
        bookModeBtn.addActionListener(e -> toggleBookMode());

        sceneModeBtn = UIComponentFactory.buildModeToggleBtn("Scene", "Szenen-Modus aktivieren / deaktivieren");
        sceneModeBtn.setPreferredSize(new Dimension(50, TOPBAR_BTN_H));
        sceneModeBtn.addActionListener(e -> toggleSceneMode());

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
        zoomOutBtn  .addActionListener(e -> setZoom(ci().zoom - ZOOM_STEP, null));
        zoomInBtn   .addActionListener(e -> setZoom(ci().zoom + ZOOM_STEP, null));
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
        right.add(quickBgBtn);
        right.add(actionPanel);
        right.add(Box.createHorizontalStrut(4));
        right.add(canvasModeBtn);
        right.add(paintModeBtn);
        right.add(bookModeBtn);
        right.add(sceneModeBtn);
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
        // Build both canvas areas (handles all UI setup for both canvases)
        buildCanvasArea(0);
        buildCanvasArea(1);

        // Set initial focus border for canvas 0
        updateCanvasFocusBorder();

        // Ruler panels (only for canvas 0) — redrawn on scroll change
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

        // Element layer panels (both initially hidden)
        elementLayerPanel = new ElementLayerPanel(buildElementLayerCallbacks(0));
        elementLayerPanel.setVisible(false);  // Hide initially, show only in Canvas mode
        elementLayerPanel2 = new ElementLayerPanel(buildElementLayerCallbacks(1));
        elementLayerPanel2.setVisible(false);  // Hide until canvas 2 has content

        mapsPanel = new MapsPanel(new MapsPanel.Callbacks() {
            @Override public void onMapSelected(TranslationMap map) {
                // TODO: implement map viewing
            }
            @Override public void onMapDeleted(String language, String mapId) {
                // Map is already deleted, just refresh
            }
            @Override public void onMapEdited(TranslationMap oldMap, TranslationMap newMap) {
                // Map is already saved, just refresh
            }
        });
        mapsPanel.setVisible(false);  // Hide initially
        mapsPanel.setPreferredSize(new Dimension(250, 400));
        mapsPanel.setMaximumSize(new Dimension(250, Integer.MAX_VALUE));

        // Canvas drawing areas: flexible width (take all available space)
        ci(0).layeredPane.setMinimumSize(new Dimension(0, 0));
        ci(0).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        ci(0).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

        ci(1).layeredPane.setMinimumSize(new Dimension(0, 0));
        ci(1).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        ci(1).layeredPane.setVisible(false);  // Hidden until canvas 2 is loaded
        ci(1).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

        // Gallery panels: fixed width (no growth beyond preferred)
        // NOTE: When SHRINK_GALLERY = false, remove setMaximumSize() calls below
        // so galleries keep full width and canvases shrink instead
        ci(0).tileGallery.setVisible(false);  // Start hidden, show on filmstrip toggle
        if (SHRINK_GALLERY) {
            ci(0).tileGallery.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
        }
        ci(0).tileGallery.setAlignmentY(Component.CENTER_ALIGNMENT);

        if (SHRINK_GALLERY) {
            elementLayerPanel.setMaximumSize(new Dimension(ElementLayerPanel.PANEL_W, Integer.MAX_VALUE));
        }
        elementLayerPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

        if (SHRINK_GALLERY) {
            elementLayerPanel2.setMaximumSize(new Dimension(ElementLayerPanel.PANEL_W, Integer.MAX_VALUE));
        }
        elementLayerPanel2.setAlignmentY(Component.CENTER_ALIGNMENT);

        ci(1).tileGallery.setVisible(false);  // Start hidden, show on gallery 2 toggle
        if (SHRINK_GALLERY) {
            ci(1).tileGallery.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
        }
        ci(1).tileGallery.setAlignmentY(Component.CENTER_ALIGNMENT);

        // Vertical divider between Canvas1 and Canvas2
        mainDividerPanel = new JPanel();
        mainDividerPanel.setBackground(AppColors.BORDER);
        mainDividerPanel.setPreferredSize(new Dimension(2, 0));
        mainDividerPanel.setMaximumSize(new Dimension(2, Integer.MAX_VALUE));
        mainDividerPanel.setMinimumSize(new Dimension(2, 0));
        mainDividerPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        mainDividerPanel.setVisible(false);  // Only visible when both canvases are shown

        // Right drop zone overlay (for dragging tiles to activate second canvas)
        rightDropZone = buildRightDropZone();
        ci(0).layeredPane.add(rightDropZone, JLayeredPane.PALETTE_LAYER);
        rightDropZone.setVisible(false);

        // Gallery wrapper: BoxLayout X_AXIS — invisible components take NO space
        galleryWrapper = new JPanel();
        galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.X_AXIS));
        galleryWrapper.setBackground(AppColors.BG_DARK);

        // Order: Gallery1 | Elements1 | Canvas1 | Divider | Canvas2 | Elements2 | Gallery2 | Maps
        galleryWrapper.add(ci(0).tileGallery);
        galleryWrapper.add(elementLayerPanel);
        galleryWrapper.add(ci(0).layeredPane);
        galleryWrapper.add(mainDividerPanel);
        galleryWrapper.add(ci(1).layeredPane);
        galleryWrapper.add(elementLayerPanel2);
        galleryWrapper.add(ci(1).tileGallery);
        galleryWrapper.add(mapsPanel);

        // Auto-fit canvas when layout changes (panels hidden, divider moved, etc.)
        galleryWrapper.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                // Re-fit the active canvas to the new available space
                CanvasInstance activeCanvas = ci();
                if (activeCanvas.workingImage != null && activeCanvas.viewportPanel.isVisible()
                        && !activeCanvas.userHasManuallyZoomed) {
                    SwingUtilities.invokeLater(() -> fitToViewport(activeCanvasIndex));
                }
            }
        });

        return galleryWrapper;
    }

    /** Re-builds the ruler strip layout around the scrollPane. */
    private void buildRulerLayout() {
        // Remove the stable containers from canvas 0 viewportPanel
        ci(0).viewportPanel.remove(rulerNorthBar);
        ci(0).viewportPanel.remove(vRuler);

        if (showRuler) {
            ci(0).viewportPanel.add(rulerNorthBar, BorderLayout.NORTH);
            ci(0).viewportPanel.add(vRuler,        BorderLayout.WEST);
        }
        ci(0).viewportPanel.revalidate();
        ci(0).viewportPanel.repaint();
    }

    private void repositionNavButtons(int idx) {
        CanvasInstance c = ci(idx);
        if (c.prevNavButton == null) return;
        int h = c.layeredPane.getHeight(), bh = 80, bw = 36;
        int y = Math.max(0, (h - bh) / 2);
        c.prevNavButton.setBounds(8, y, bw, bh);
        c.nextNavButton.setBounds(c.layeredPane.getWidth() - bw - 8, y, bw, bh);
    }

    /** Updates focus borders and element panel: active canvas gets green border only when second canvas is visible. */
    private void updateCanvasFocusBorder() {
        // Only show focus border if both canvas drawing areas are visible
        boolean showBorder = secondCanvasBtn.isEnabled()
                          && firstCanvasBtn.isSelected()
                          && secondCanvasBtn.isSelected();

        for (int i = 0; i < 2; i++) {
            CanvasInstance c = ci(i);
            if (c.layeredPane == null) continue;
            if (c.viewportPanel == null) continue;

            if (showBorder && i == activeCanvasIndex) {
                // Active canvas (with second canvas visible): green border on both viewport and layered pane
                javax.swing.border.Border greenBorder = new javax.swing.border.LineBorder(new Color(0, 220, 0), 3, false);
                c.viewportPanel.setBorder(greenBorder);
                c.layeredPane.setBorder(greenBorder);
            } else {
                // Inactive or no focus needed: no border
                c.viewportPanel.setBorder(null);
                c.layeredPane.setBorder(null);
            }
        }

        // Update element panels
        refreshElementPanel();
    }

    /** Updates visibility of all 7 layout elements independently. */
    private void updateLayoutVisibility() {
        // Canvas drawing areas follow their buttons
        if (ci(0).layeredPane != null)
            ci(0).layeredPane.setVisible(firstCanvasBtn.isSelected());
        if (ci(1).layeredPane != null)
            ci(1).layeredPane.setVisible(secondCanvasBtn.isSelected());

        // Divider only visible when both canvas drawing areas are shown
        if (mainDividerPanel != null)
            mainDividerPanel.setVisible(
                firstCanvasBtn.isSelected() && secondCanvasBtn.isSelected());

        updateCanvasFocusBorder();
        if (galleryWrapper != null) { galleryWrapper.revalidate(); galleryWrapper.repaint(); }
    }

    // ── Build canvas area (for index 0 or 1) ──────────────────────────────────
    private void buildCanvasArea(int idx) {
        CanvasInstance c = canvases[idx];

        // Canvas panel with callbacks
        c.canvasPanel = new CanvasPanel(buildCanvasCallbacks(idx));

        // Canvas wrapper (null-layout for absolute positioning) with centering
        c.canvasWrapper = new JPanel(null) {
            @Override public Dimension getPreferredSize() {
                if (c.workingImage == null) return new Dimension(1, 1);
                int cw = (int) Math.ceil(c.workingImage.getWidth()  * c.zoom);
                int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
                Dimension vd = c.scrollPane != null ? c.scrollPane.getViewport().getSize()
                                                      : new Dimension(cw, ch);
                return new Dimension(Math.max(cw, vd.width), Math.max(ch, vd.height));
            }
            @Override public void doLayout() {
                if (c.canvasPanel == null) return;
                Dimension cs = c.canvasPanel.getPreferredSize();
                Dimension ws = getSize();
                int x = Math.max(0, (ws.width  - cs.width)  / 2);
                int y = Math.max(0, (ws.height - cs.height) / 2);
                c.canvasPanel.setBounds(x, y, cs.width, cs.height);
            }
        };
        c.canvasWrapper.setBackground(AppColors.BG_DARK);
        c.canvasWrapper.setOpaque(true);
        c.canvasWrapper.add(c.canvasPanel);

        // Scroll pane
        c.scrollPane = new JScrollPane(c.canvasWrapper);
        c.scrollPane.setBorder(null);
        if (idx == 0) {
            c.scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            c.scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        }
        TileGalleryPanel.applyDarkScrollBar(c.scrollPane.getVerticalScrollBar());
        TileGalleryPanel.applyDarkScrollBar(c.scrollPane.getHorizontalScrollBar());
        c.scrollPane.getViewport().setBackground(AppColors.BG_DARK);
        c.scrollPane.setBackground(AppColors.BG_DARK);
        c.scrollPane.getViewport().addChangeListener(e -> {
            if (showRuler && idx == 0) { hRuler.repaint(); vRuler.repaint(); }
            c.canvasWrapper.revalidate();
        });

        // Viewport panel (for ruler layout)
        c.viewportPanel = new JPanel(new BorderLayout());
        c.viewportPanel.setBackground(AppColors.BG_DARK);
        c.viewportPanel.setVisible(false);
        c.viewportPanel.add(c.scrollPane, BorderLayout.CENTER);

        // Spacing below viewport
        JPanel scrollSpacer = new JPanel();
        scrollSpacer.setOpaque(true);
        scrollSpacer.setBackground(AppColors.BG_DARK);
        scrollSpacer.setPreferredSize(new Dimension(0, 16));
        c.viewportPanel.add(scrollSpacer, BorderLayout.SOUTH);

        // Listener to trigger fitToViewport when viewport size becomes known
        c.viewportPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    Dimension vd = c.scrollPane.getViewport().getSize();
                    if (vd.width > 0 && vd.height > 0 && c.workingImage != null && !c.userHasManuallyZoomed) {
                        fitToViewport(idx);
                    }
                });
            }
            @Override public void componentResized(ComponentEvent e) {
                if (c.workingImage != null && !c.userHasManuallyZoomed && c.viewportPanel.isVisible()) {
                    // Re-fit on resize for better responsiveness
                    SwingUtilities.invokeLater(() -> fitToViewport(idx));
                }
            }
        });

        // Layered pane for nav button overlay
        c.layeredPane = new JLayeredPane();
        c.layeredPane.setBackground(AppColors.BG_DARK);
        c.layeredPane.setOpaque(true);
        c.layeredPane.setPreferredSize(new Dimension(860, 560));

        // Drop hint panel
        c.dropHintPanel = buildDropHintPanel();
        c.dropHintPanel.setBounds(0, 0, 860, 560);
        c.layeredPane.add(c.dropHintPanel, JLayeredPane.DEFAULT_LAYER);

        // Nav buttons
        c.prevNavButton = UIComponentFactory.buildNavButton("‹");
        c.nextNavButton = UIComponentFactory.buildNavButton("›");
        c.prevNavButton.setEnabled(false);
        c.nextNavButton.setEnabled(false);
        c.prevNavButton.addActionListener(e -> navigateImage(-1, idx));
        c.nextNavButton.addActionListener(e -> navigateImage(+1, idx));
        c.layeredPane.add(c.prevNavButton, JLayeredPane.PALETTE_LAYER);
        c.layeredPane.add(c.nextNavButton, JLayeredPane.PALETTE_LAYER);

        // Element-edit action bar (hidden until a layer is opened for editing)
        c.elementEditBar = buildElementEditBar(idx);
        c.elementEditBar.setVisible(false);
        c.layeredPane.add(c.elementEditBar, JLayeredPane.MODAL_LAYER);

        // Component listener for resizing
        c.layeredPane.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = c.layeredPane.getWidth(), h = c.layeredPane.getHeight();
                c.dropHintPanel.setBounds(0, 0, w, h);
                if (c.viewportPanel.getParent() == c.layeredPane)
                    c.viewportPanel.setBounds(0, 0, w, h);
                repositionNavButtons(idx);
                repositionElementEditBar(idx);
                if (idx == 0) repositionRightDropZone();

                // Re-fit when canvas size changes (e.g., split pane divider moved)
                if (c.workingImage != null && !c.userHasManuallyZoomed && c.viewportPanel.isVisible()) {
                    SwingUtilities.invokeLater(() -> fitToViewport(idx));
                }
            }
        });

        // Focus listener: clicking activates this canvas
        c.canvasPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (activeCanvasIndex != idx) resetElementDragState(activeCanvasIndex);
                activeCanvasIndex = idx;
                updateCanvasFocusBorder();
            }
        });

        // Tile gallery for this canvas
        c.tileGallery = new TileGalleryPanel(buildGalleryCallbacks(idx));
        c.tileGallery.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (activeCanvasIndex != idx) resetElementDragState(activeCanvasIndex);
                activeCanvasIndex = idx;
                updateCanvasFocusBorder();
            }
        });

        // Set up drop targets
        setupDropTarget(c.dropHintPanel, idx);
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
    private void setupDropTarget(java.awt.Component target, int idx) {
        new java.awt.dnd.DropTarget(target, java.awt.dnd.DnDConstants.ACTION_COPY,
                new java.awt.dnd.DropTargetAdapter() {
            @Override public void drop(java.awt.dnd.DropTargetDropEvent ev) {
                try {
                    ev.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    java.awt.datatransfer.Transferable t = ev.getTransferable();
                    if (t.isDataFlavorSupported(ElementLayerPanel.LAYER_FLAVOR)) {
                        // Case 1: LayerTile dragged onto canvas → copy as element
                        Layer layer = (Layer) t.getTransferData(ElementLayerPanel.LAYER_FLAVOR);
                        insertLayerCopyToCanvas(layer, idx);
                        ev.dropComplete(true);
                    } else if (t.isDataFlavorSupported(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR)) {
                        // Case 3: right-drag from TileGallery onto canvas → insert as element
                        TileGalleryPanel.FileForElement ffe = (TileGalleryPanel.FileForElement)
                                t.getTransferData(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR);
                        insertFileAsElement(ffe.file, idx);
                        ev.dropComplete(true);
                    } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File f = files.get(0);
                            if (isSupportedFile(f)) loadFile(f, idx);
                            else showErrorDialog("Format nicht unterstützt",
                                    "Erlaubt: PNG, JPG, BMP, GIF\nDatei: " + f.getName());
                        }
                        ev.dropComplete(true);
                    } else {
                        ev.dropComplete(false);
                    }
                } catch (Exception ex) { ev.dropComplete(false); showErrorDialog("Drop-Fehler", ex.getMessage()); }
            }
        }, true);
    }

    /** Case 1: Copy a layer onto a canvas as a new element. */
    private void insertLayerCopyToCanvas(Layer source, int targetIdx) {
        CanvasInstance c = ci(targetIdx);
        if (c.workingImage == null) return;
        Layer copy = copyLayerWithNewId(source, c.nextElementId++);
        if (copy == null) return;
        c.activeElements.add(copy);
        c.selectedElements.clear();
        c.selectedElements.add(copy);
        markDirty(targetIdx);
        refreshElementPanel();
        if (c.canvasPanel != null) c.canvasPanel.repaint();
        ToastNotification.show(this, "Layer kopiert");
    }

    /** Case 3: Load an image file and insert it as a new element on the canvas. */
    private void insertFileAsElement(File f, int targetIdx) {
        CanvasInstance c = ci(targetIdx);
        if (c.workingImage == null) {
            if (isSupportedFile(f)) loadFile(f, targetIdx);
            return;
        }
        try {
            BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img == null) return;
            img = normalizeImage(img);
            int[] size = fitElementSize(img.getWidth(), img.getHeight(),
                                        c.workingImage.getWidth(), c.workingImage.getHeight());
            int cx = Math.max(0, (c.workingImage.getWidth()  - size[0]) / 2);
            int cy = Math.max(0, (c.workingImage.getHeight() - size[1]) / 2);
            ImageLayer layer = new ImageLayer(c.nextElementId++, img, cx, cy, size[0], size[1]);
            c.activeElements.add(layer);
            c.selectedElements.clear();
            c.selectedElements.add(layer);
            markDirty(targetIdx);
            refreshElementPanel();
            if (c.canvasPanel != null) c.canvasPanel.repaint();
            ToastNotification.show(this, "Bild als Element eingefügt");
        } catch (Exception ex) {
            showErrorDialog("Fehler", "Bild konnte nicht geladen werden: " + ex.getMessage());
        }
    }

    /** Creates a copy of {@code src} with a new ID. Returns null for unsupported types. */
    private Layer copyLayerWithNewId(Layer src, int newId) {
        if (src instanceof ImageLayer il) {
            BufferedImage normalized = normalizeImage(il.image());
            return new ImageLayer(newId, normalized, il.x(), il.y(), normalized.getWidth(), normalized.getHeight());
        } else if (src instanceof TextLayer tl) {
            return TextLayer.of(newId, tl.text(), tl.fontName(), tl.fontSize(),
                    tl.fontBold(), tl.fontItalic(), tl.fontColor(), tl.x(), tl.y());
        } else if (src instanceof PathLayer pl) {
            return PathLayer.of(newId, pl.points(), pl.image(), pl.isClosed(), pl.x(), pl.y());
        }
        return null;
    }

    /** Converts a visual drop index (0 = top) to a list insert index.
     *  Since display is reversed (top = last element), insertIdx = listSize - visualIdx. */
    private static int visualToInsertIndex(int visualIdx, int listSize) {
        return Math.max(0, Math.min(listSize, listSize - visualIdx));
    }

    /** Max fraction of the canvas dimensions an auto-inserted element may occupy. */
    private static final float MAX_ELEM_RATIO = 0.40f;

    /**
     * Returns {renderW, renderH} for a freshly dropped element layer.
     * Fits the image proportionally into MAX_ELEM_RATIO of the canvas size.
     * Never upscales (if image is already smaller, keeps original size).
     */
    private static int[] fitElementSize(int imgW, int imgH, int canvasW, int canvasH) {
        float maxW  = canvasW * MAX_ELEM_RATIO;
        float maxH  = canvasH * MAX_ELEM_RATIO;
        float scale = Math.min(1.0f, Math.min(maxW / imgW, maxH / imgH));
        return new int[]{ Math.max(1, Math.round(imgW * scale)),
                          Math.max(1, Math.round(imgH * scale)) };
    }

    // =========================================================================
    // Second Canvas / Split Screen
    // =========================================================================

    // buildSecondArea() removed — now using buildCanvasArea(1) for canvas 2

    private JPanel buildRightDropZone() {
        JPanel zone = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRoundRect(0, 0, w, h, 12, 12);
                float[] dash = { 8f, 5f };
                g2.setColor(AppColors.ACCENT);
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
                g2.drawRoundRect(4, 4, w - 9, h - 9, 10, 10);
                g2.setStroke(new BasicStroke(2));
                // Arrow right symbol
                int cx = w / 2, cy = h / 2;
                g2.drawLine(cx - 12, cy, cx + 12, cy);
                g2.drawLine(cx + 4, cy - 8, cx + 12, cy);
                g2.drawLine(cx + 4, cy + 8, cx + 12, cy);
                g2.setColor(AppColors.TEXT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                String t = "2. Canvas";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, cx - fm.stringWidth(t) / 2, cy + 28);
            }
        };
        zone.setOpaque(false);
        zone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Click handler: open quick select dialog for canvas 2
        zone.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                e.consume();
                showQuickOpenDialog(1);  // Load into canvas 2 (index 1)
            }
        });

        setupDropTarget(zone, 1);
        return zone;
    }

    private void repositionRightDropZone() {
        if (rightDropZone == null || ci(0).workingImage == null) return;
        int imgRightInCanvas = (int)(ci(0).canvasPanel.getX() + ci(0).workingImage.getWidth() * ci(0).zoom);
        int vpX = imgRightInCanvas - ci(0).scrollPane.getViewport().getViewPosition().x;
        int x = vpX + 16;
        int y = ci(0).layeredPane.getHeight() / 2 - 60;
        int w = 90, h = 120;
        if (x + w < ci(0).layeredPane.getWidth()) {
            rightDropZone.setBounds(x, y, w, h);
        } else {
            rightDropZone.setVisible(false);
        }
    }

    // =========================================================================
    // File loading
    // =========================================================================
    // Convenience: loads into active canvas
    private void loadFile(File file) {
        loadFile(file, activeCanvasIndex);
    }

    // Core version: loads into specified canvas
    private void loadFile(File file, int idx) {
        CanvasInstance c = ci(idx);

        // Save current canvas state before switching
        if (c.sourceFile != null) saveCurrentState(idx);

        // Check cache first
        CanvasInstance.CanvasFileState cached = c.fileCache.get(file);
        if (cached != null) {
            // Restore from cache
            c.workingImage = cached.image;
            c.undoStack.clear(); c.undoStack.addAll(cached.undoStack);
            c.redoStack.clear(); c.redoStack.addAll(cached.redoStack);
            c.activeElements = new ArrayList<>(cached.elements);
        } else {
            // Load fresh from disk
            try {
                BufferedImage img = ImageIO.read(file);
                if (img == null) { showErrorDialog("Ladefehler", "Bild konnte nicht gelesen werden:\n" + file.getName()); return; }
                c.originalImage = img;
                c.workingImage  = normalizeImage(c.originalImage);
                c.undoStack.clear();
                c.redoStack.clear();
                c.activeElements = new ArrayList<>();
                c.selectedElements.clear();
                CanvasInstance.CanvasFileState cs = new CanvasInstance.CanvasFileState(c.workingImage);
                c.fileCache.put(file, cs);
            } catch (IOException e) {
                showErrorDialog("Ladefehler", "Fehler:\n" + e.getMessage());
                return;
            }
        }

        c.sourceFile = file;
        c.hasUnsavedChanges = dirtyFiles.contains(file);
        c.selectedAreas.clear();
        c.isSelecting = false; c.selectionStart = null; c.selectionEnd = null;
        c.lastPaintPoint = null; c.shapeStartPoint = null; c.paintSnapshot = null;
        c.floatingImg = null; c.floatRect = null;
        c.isDraggingFloat = false; c.floatDragAnchor = null;
        c.activeHandle = -1; c.scaleBaseRect = null; c.scaleDragStart = null;
        c.selectedElements.clear(); c.draggingElement = false; c.elemDragAnchor = null;
        c.elemActiveHandle = -1; c.elemScaleBase = null; c.elemScaleStart = null;
        if (c.canvasPanel != null) c.canvasPanel.resetInputState();

        // Load saved scene data (Layer, Zoom, Mode)
        try {
            if (projectManager.getProjectName() != null) {
                List<Layer> savedLayers = projectManager.loadScene(file);
                if (savedLayers != null && !savedLayers.isEmpty()) {
                    c.activeElements = savedLayers;
                    c.selectedElements.clear();
                }

                double savedZoom = projectManager.loadSceneZoom(file);
                if (savedZoom > 0) {
                    c.zoom = savedZoom;
                    c.userHasManuallyZoomed = true;
                }

                AppMode savedMode = projectManager.loadSceneMode(file);
                if (savedMode != null) {
                    appMode = savedMode;
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Fehler beim Laden der Szenen-Daten: " + e.getMessage());
        }

        // Set this canvas as active when loading an image
        activeCanvasIndex = idx;
        updateCanvasFocusBorder();

        // Don't index temp files (element-edit mode) — would pollute nav with /tmp
        if (!file.getParentFile().equals(new java.io.File(System.getProperty("java.io.tmpdir"))))
            indexDirectory(file, idx);
        swapToImageView(idx);
        SwingUtilities.invokeLater(() -> fitToViewport(idx));
        refreshElementPanel();
        updateNavigationButtons();
        updateTitle();
        updateStatus();
        setBottomButtonsEnabled(true);
    }

    /** Saves the current canvas state back into the file cache. */
    public void saveCurrentState() {
        saveCurrentState(activeCanvasIndex);
    }

    public void saveCurrentState(int idx) {
        CanvasInstance c = ci(idx);
        if (c.sourceFile == null || c.workingImage == null) return;
        CanvasInstance.CanvasFileState cs = c.fileCache.computeIfAbsent(c.sourceFile, k -> new CanvasInstance.CanvasFileState(c.workingImage));
        cs.image = c.workingImage;
        cs.undoStack.clear(); cs.undoStack.addAll(c.undoStack);
        cs.redoStack.clear(); cs.redoStack.addAll(c.redoStack);
        cs.elements.clear();  cs.elements.addAll(c.activeElements);
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
        indexDirectory(file, activeCanvasIndex);
    }

    private void indexDirectory(File file, int idx) {
        CanvasInstance c = ci(idx);
        File dir = file.getParentFile();
        if (dir == null) return;
        boolean sameDir = dir.equals(c.lastIndexedDir);
        if (!sameDir) {
            File[] files = dir.listFiles(f -> f.isFile() && isSupportedFile(f));
            if (files == null) return;
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            c.directoryImages = new ArrayList<>(Arrays.asList(files));
            c.lastIndexedDir  = dir;
            c.tileGallery.setFiles(c.directoryImages, file);

            // Track last opened image directory
            try {
                LastProjectsManager.addRecent(LastProjectsManager.CAT_IMAGES, dir.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[WARN] Konnte lastProjects nicht speichern: " + e.getMessage());
            }
        } else {
            c.tileGallery.setActiveFile(file);
        }
        c.currentImageIndex = c.directoryImages.indexOf(file);
    }

    private void navigateImage(int dir) {
        navigateImage(dir, activeCanvasIndex);
    }

    private void navigateImage(int dir, int idx) {
        CanvasInstance c = ci(idx);
        if (c.directoryImages.isEmpty()) return;
        int ni = c.currentImageIndex + dir;
        if (ni < 0 || ni >= c.directoryImages.size()) return;
        c.currentImageIndex = ni;
        loadFile(c.directoryImages.get(c.currentImageIndex), idx);
        c.tileGallery.scrollToActive();
    }

    // ── Indexed canvas methods (array-based) ────────────────────────────────────
    public void swapToImageView(int idx) {
        CanvasInstance c = ci(idx);
        if (c.dropHintPanel.getParent() == c.layeredPane) c.layeredPane.remove(c.dropHintPanel);
        if (c.viewportPanel.getParent() == null) {
            int w = Math.max(c.layeredPane.getWidth(), 430);
            int h = Math.max(c.layeredPane.getHeight(), 560);
            c.viewportPanel.setBounds(0, 0, w, h);
            c.layeredPane.add(c.viewportPanel, JLayeredPane.DEFAULT_LAYER);
            setupDropTarget(c.viewportPanel, idx);
            setupDropTarget(c.canvasPanel, idx);
        }
        c.viewportPanel.setVisible(true);

        // Enable canvas-0 element and canvas buttons as soon as canvas 0 has content
        if (idx == 0) {
            firstElementsBtn.setEnabled(true);
            firstCanvasBtn.setEnabled(true);
        }

        // If loading into canvas 1, manage visibility via buttons
        if (idx == 1) {
            boolean firstActivation = !secondCanvasBtn.isEnabled();
            rightDropZone.setVisible(false);

            if (firstActivation) {
                // First load: auto-activate canvas button
                secondCanvasBtn.setSelected(true);
            } else if (!secondCanvasBtn.isSelected()) {
                // Canvas was explicitly hidden by user → loading new image re-activates it
                secondCanvasBtn.setSelected(true);
            }

            updateLayoutVisibility();
            updateCanvasFocusBorder();

            // Canvas II: only enable buttons — user decides visibility explicitly
            secondCanvasBtn.setEnabled(true);
            secondGalleryBtn.setEnabled(true);
            secondElementsBtn.setEnabled(true);
        }

        repositionNavButtons(idx);
        c.layeredPane.revalidate();
        c.layeredPane.repaint();
    }

    public void fitToViewport(int idx) {
        CanvasInstance c = ci(idx);
        if (c.workingImage == null || c.scrollPane == null) return;
        Dimension vd = c.scrollPane.getViewport().getSize();
        if (vd.width <= 0 || vd.height <= 0) {
            SwingUtilities.invokeLater(() -> fitToViewport(idx));
            return;
        }

        // Stop any running animation and set zoom synchronously
        if (c.zoomTimer != null) { c.zoomTimer.stop(); c.zoomTimer = null; }
        c.userHasManuallyZoomed = false;
        c.zoomImgPt   = null;
        c.zoomVpMouse = null;

        double nz = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX,
                Math.min((vd.width  - 80.0) / c.workingImage.getWidth(),
                         (vd.height - 80.0) / c.workingImage.getHeight()) * 0.98));
        c.zoom = c.zoomTarget = nz;
        c.canvasWrapper.revalidate();

        // Single invokeLater — layout has settled by the time this runs,
        // so viewport size is accurate and centering is correct.
        SwingUtilities.invokeLater(() -> {
            if (c.workingImage == null || c.scrollPane == null) return;
            c.canvasWrapper.revalidate();
            c.canvasWrapper.validate();
            JViewport   vp    = c.scrollPane.getViewport();
            Dimension   vpSz  = vp.getSize();
            int cw = (int) Math.ceil(c.workingImage.getWidth()  * c.zoom);
            int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
            vp.setViewPosition(new Point(
                    Math.max(0, (cw - vpSz.width)  / 2),
                    Math.max(0, (ch - vpSz.height) / 2)));
            c.canvasWrapper.repaint();
            if (zoomLabel != null) zoomLabel.setText(Math.round(c.zoom * 100) + "%");
        });
    }

    public void centerCanvas(int idx) {
        CanvasInstance c = ci(idx);
        if (c.scrollPane == null || c.workingImage == null) return;
        SwingUtilities.invokeLater(() -> {
            c.canvasWrapper.revalidate();
            c.canvasWrapper.validate();
            JViewport  vp    = c.scrollPane.getViewport();
            Dimension  vpSz  = vp.getSize();
            int cw = (int) Math.ceil(c.workingImage.getWidth()  * c.zoom);
            int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
            int viewX = Math.max(0, (cw - vpSz.width)  / 2);
            int viewY = Math.max(0, (ch - vpSz.height) / 2);
            vp.setViewPosition(new Point(viewX, viewY));
        });
    }

    private void setZoomInstant(double nz, int idx) {
        CanvasInstance c = ci(idx);
        c.userHasManuallyZoomed = false;
        if (c.zoomTimer != null) { c.zoomTimer.stop(); c.zoomTimer = null; }
        c.zoom = c.zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));
        c.zoomImgPt = null;
        c.zoomVpMouse = null;
        if (c.canvasWrapper != null) {
            c.canvasWrapper.revalidate();
            c.canvasWrapper.repaint();
        }
        SwingUtilities.invokeLater(() ->
            c.scrollPane.getViewport().setViewPosition(new Point(0, 0)));
    }

    // =========================================================================
    // Zoom
    // =========================================================================

    /**
     * Set zoom level with smooth animation.
     * If anchorCanvas != null, keep that canvas point fixed on screen (zoom toward cursor).
     */
    public void setZoom(double nz, Point anchorCanvas) {
        setZoom(nz, anchorCanvas, activeCanvasIndex);
    }

    // Internal setZoom that works with indexed canvas
    private void setZoom(double nz, Point anchorCanvas, int idx) {
        CanvasInstance c = ci(idx);
        c.userHasManuallyZoomed = true;
        c.zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));

        // Capture anchor point only if a new gesture is starting (no animation running)
        if (c.zoomTimer == null) {
            if (anchorCanvas != null && c.scrollPane != null) {
                JViewport vp = c.scrollPane.getViewport();
                // image coord under cursor (using CURRENT zoom, before animation starts)
                c.zoomImgPt = new Point2D.Double(anchorCanvas.x / c.zoom, anchorCanvas.y / c.zoom);
                // viewport-relative mouse position (stays fixed during animation)
                c.zoomVpMouse = SwingUtilities.convertPoint(c.canvasPanel, anchorCanvas, vp);
            } else {
                c.zoomImgPt  = null;
                c.zoomVpMouse = null;
            }
        }
        // If animation is already running, just update zoomTarget and continue

        startZoomAnimation(idx);
    }

    public void fitToViewport() {
        fitToViewport(activeCanvasIndex);
    }

    /** Set zoom immediately without animation. Used for image load/browse. */
    private void setZoomInstant(double nz) {
        setZoomInstant(nz, activeCanvasIndex);
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) zoomLabel.setText(Math.round(ci().zoom * 100) + "%");
    }

    /**
     * Start or restart the zoom animation timer for indexed canvas.
     * Each tick, zoom approaches zoomTarget using exponential decay.
     */
    private void startZoomAnimation(int idx) {
        CanvasInstance c = ci(idx);
        if (c.zoomTimer != null) {
            c.zoomTimer.stop();
            c.zoomTimer = null;
        }

        final int INTERVAL_MS = 16;  // ~60 FPS
        final double FACTOR = 0.30;  // 30% per tick — snappy but not jarring

        c.zoomTimer = new Timer(INTERVAL_MS, null);
        c.zoomTimer.addActionListener(e -> {
            double diff = c.zoomTarget - c.zoom;
            boolean done = Math.abs(diff) < 0.0005;
            if (done) {
                c.zoom = c.zoomTarget;
                c.zoomTimer.stop();
                c.zoomTimer = null;
            } else {
                c.zoom += diff * FACTOR;
            }
            applyZoomFrame(idx);
            // After animation ends with no anchor: reset viewport to (0,0)
            if (done && c.zoomImgPt == null && c.scrollPane != null) {
                SwingUtilities.invokeLater(() ->
                    c.scrollPane.getViewport().setViewPosition(new Point(0, 0)));
            }
        });
        c.zoomTimer.setInitialDelay(0);
        c.zoomTimer.start();
    }

    /**
     * Start or restart the zoom animation timer for active canvas.
     */
    private void startZoomAnimation() {
        startZoomAnimation(activeCanvasIndex);
    }

    /**
     * Apply current zoom to the UI for indexed canvas (called every animation frame).
     */
    private void applyZoomFrame(int idx) {
        CanvasInstance c = ci(idx);
        if (c.canvasWrapper == null) return;

        // Synchronous layout pass
        c.canvasWrapper.revalidate();

        // Adjust viewport so the anchor image pixel stays under the mouse.
        if (c.zoomImgPt != null && c.zoomVpMouse != null && c.scrollPane != null && c.workingImage != null) {
            JViewport vp   = c.scrollPane.getViewport();
            Dimension vs   = vp.getViewSize();
            Dimension vpSz = vp.getSize();
            int cx = c.canvasPanel.getX();
            int cy = c.canvasPanel.getY();
            int newCanvasX = (int)(c.zoomImgPt.getX() * c.zoom);
            int newCanvasY = (int)(c.zoomImgPt.getY() * c.zoom);
            int vx = cx + newCanvasX - c.zoomVpMouse.x;
            int vy = cy + newCanvasY - c.zoomVpMouse.y;
            int maxVx = Math.max(0, vs.width  - vpSz.width);
            int maxVy = Math.max(0, vs.height - vpSz.height);
            vp.setViewPosition(new Point(Math.max(0, Math.min(vx, maxVx)),
                                         Math.max(0, Math.min(vy, maxVy))));
        }

        c.canvasWrapper.repaint();
    }

    /**
     * Apply current zoom to the active canvas (called every animation frame).
     */
    private void applyZoomFrame() {
        applyZoomFrame(activeCanvasIndex);
    }

    // =========================================================================
    // Coordinate transform
    // =========================================================================
    /** Convert a point in canvasPanel-local coordinates to image-space. */
    public Point screenToImage(Point sp) {
        CanvasInstance c = ci();
        int ix = (int) Math.floor(sp.x / c.zoom);
        int iy = (int) Math.floor(sp.y / c.zoom);
        if (c.workingImage != null) {
            ix = Math.max(0, Math.min(c.workingImage.getWidth()  - 1, ix));
            iy = Math.max(0, Math.min(c.workingImage.getHeight() - 1, iy));
        }
        return new Point(ix, iy);
    }

    // =========================================================================
    // Alpha-editor operations
    // =========================================================================
    public void performFloodfill(Point screenPt) {
        CanvasInstance c = ci();
        Point ip = screenToImage(screenPt);
        int tc = c.workingImage.getRGB(ip.x, ip.y);
        if (((tc >> 24) & 0xFF) == 0) { showInfoDialog("Bereits transparent", "Klicke auf eine sichtbare Farbe."); return; }
        PaintEngine.floodFill(c.workingImage, ip.x, ip.y, new Color(0,0,0,0), 30);
        markDirty();
    }

    private void applySelectionsToAlpha() {
        CanvasInstance c = ci();
        if (c.selectedAreas.isEmpty()) { showInfoDialog("Keine Auswahl", "Noch keine Bereiche ausgewählt."); return; }
        pushUndo();
        for (Rectangle r : c.selectedAreas) PaintEngine.clearRegion(c.workingImage, r);
        c.selectedAreas.clear();
        markDirty();
        showInfoDialog("Erledigt", "Ausgewählte Bereiche wurden transparent gemacht.");
    }

    private void clearSelections() { ci().selectedAreas.clear(); ci().canvasPanel.repaint(); }

    private String getSaveSuffix() {
        return (appMode == AppMode.PAINT) ? "_painted"
             : floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
    }

    private void resetImage() {
        CanvasInstance c = ci();
        if (c.originalImage == null) return;

        c.workingImage = deepCopy(c.originalImage);
        c.undoStack.clear();
        c.redoStack.clear();
        c.selectedAreas.clear();
        c.floatingImg = null;
        c.floatRect = null;
        c.activeElements.clear();
        c.selectedElements.clear();

        c.hasUnsavedChanges = false;
        if (c.sourceFile != null) dirtyFiles.remove(c.sourceFile);
        updateTitle();
        updateDirtyUI();
        refreshElementPanel();
        c.canvasPanel.repaint();
    }

    private void saveImage() {
        CanvasInstance c = ci();
        if (c.sourceFile == null) return;
        try {
            String suffix  = getSaveSuffix();
            String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
            File   outFile = new File(outPath);
            ImageIO.write(c.workingImage, "PNG", outFile);
            c.hasUnsavedChanges = false;
            dirtyFiles.remove(c.sourceFile);
            updateTitle();
            updateDirtyUI();
            showInfoDialog("Gespeichert", "Gespeichert als:\n" + outFile.getName());
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    /** Saves current workingImage to undo stack before a destructive operation. */
    public void pushUndo() {
        pushUndo(activeCanvasIndex);
    }

    public void pushUndo(int idx) {
        CanvasInstance c = ci(idx);
        if (c.workingImage == null) return;
        c.undoStack.push(deepCopy(c.workingImage));
        if (c.undoStack.size() > MAX_UNDO) c.undoStack.pollLast();
        c.redoStack.clear();
    }

    public void clearUndoRedo() {
        ci().undoStack.clear();
        ci().redoStack.clear();
    }

    private void doUndo() {
        doUndo(activeCanvasIndex);
    }

    private void doUndo(int idx) {
        CanvasInstance c = ci(idx);
        if (c.undoStack.isEmpty()) return;
        c.redoStack.push(deepCopy(c.workingImage));
        c.workingImage = c.undoStack.pop();
        afterUndoRedo(idx);
    }

    private void doRedo() {
        doRedo(activeCanvasIndex);
    }

    private void doRedo(int idx) {
        CanvasInstance c = ci(idx);
        if (c.redoStack.isEmpty()) return;
        c.undoStack.push(deepCopy(c.workingImage));
        c.workingImage = c.redoStack.pop();
        afterUndoRedo(idx);
    }

    private void afterUndoRedo(int idx) {
        CanvasInstance c = ci(idx);
        // If undo stack is empty, we've undone all changes back to original state
        if (c.undoStack.isEmpty()) {
            c.hasUnsavedChanges = false;
            if (c.sourceFile != null) dirtyFiles.remove(c.sourceFile);
        } else {
            c.hasUnsavedChanges = true;
            if (c.sourceFile != null) dirtyFiles.add(c.sourceFile);
        }
        updateTitle();
        updateDirtyUI();
        c.canvasWrapper.revalidate();
        c.canvasPanel.repaint();
        if (showRuler && idx == 0) { hRuler.repaint(); vRuler.repaint(); }
    }

    /** CTRL+ALT+S: overwrite the original source file directly, no suffix, no dialog. */
    private void saveImageToOriginal() {
        CanvasInstance c = ci();
        if (c.sourceFile == null || c.workingImage == null) return;
        try {
            ImageIO.write(c.workingImage, "PNG", c.sourceFile);
            c.hasUnsavedChanges = false;
            dirtyFiles.remove(c.sourceFile);
            updateTitle();
            updateDirtyUI();
            refreshGalleryThumbnail();
            ToastNotification.show(this, "Gespeichert: " + c.sourceFile.getName());
            SwingUtilities.invokeLater(this::ensureElementEditBarVisible);
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    // =========================================================================
    // Element-edit mode (double-click on layer tile → open in other canvas)
    // =========================================================================

    /** Builds the floating action bar shown in a canvas when it is in element-edit mode. */
    private JPanel buildElementEditBar(int idx) {
        // Non-opaque panel with manual alpha-background paint — avoids JLayeredPane transparency glitch
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 6, 6)) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bar.setOpaque(false);
        bar.setBackground(new Color(30, 30, 30, 220));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

        JButton btnNewLayer  = UIComponentFactory.buildButton("Als neues Layer",           AppColors.ACCENT,   AppColors.ACCENT_HOVER);
        JButton btnThisLayer = UIComponentFactory.buildButton("In diesem Layer übernehmen", new Color(60,140,60), new Color(40,110,40));
        JButton btnNewImage  = UIComponentFactory.buildButton("Als neues Image",            AppColors.BTN_BG,   AppColors.BTN_HOVER);
        JButton btnAbort     = UIComponentFactory.buildButton("Abbrechen",                  new Color(160,50,50), new Color(130,30,30));
        JButton btnClose     = UIComponentFactory.buildButton("Close",                      AppColors.BTN_BG,   AppColors.BTN_HOVER);

        for (JButton b : new JButton[]{btnNewLayer, btnThisLayer, btnNewImage, btnAbort, btnClose})
            b.setForeground(Color.WHITE);

        btnNewLayer.addActionListener(e -> elementEditAsNewLayer(idx));
        btnThisLayer.addActionListener(e -> elementEditIntoSourceLayer(idx));
        btnNewImage.addActionListener(e -> elementEditAsNewImage(idx));
        btnAbort.addActionListener(e -> elementEditAbort(idx));
        btnClose.addActionListener(e -> elementEditClose(idx));

        bar.add(btnNewLayer);
        bar.add(btnThisLayer);
        bar.add(btnNewImage);
        bar.add(btnAbort);
        bar.add(btnClose);
        return bar;
    }

    private void repositionElementEditBar(int idx) {
        CanvasInstance c = ci(idx);
        if (c.elementEditBar == null) return;
        int w = c.layeredPane.getWidth();
        int h = c.layeredPane.getHeight();
        int bh = 44;
        c.elementEditBar.setBounds(0, h - bh, w, bh);
    }

    /** Enter element-edit mode: load the element as a temp image into the target canvas. */
    private void activateElementEditMode(int targetIdx, Layer sourceLayer, int sourceIdx) {
        // Guard: only allow one element edit at a time
        if (elementEditSourceLayer != null) {
            showErrorDialog("Bearbeitung aktiv",
                "Schließe zuerst die aktuelle Bearbeitung ab.");
            return;
        }
        elementEditSourceLayer = sourceLayer;
        elementEditSourceIdx   = sourceIdx;
        elementEditTargetIdx   = targetIdx;

        CanvasInstance c = ci(targetIdx);
        repositionElementEditBar(targetIdx);
        c.elementEditBar.setVisible(true);
        c.layeredPane.revalidate();
        c.layeredPane.repaint();
    }

    /** Exit element-edit mode without any transfer. */
    private void exitElementEditMode(int targetIdx) {
        elementEditSourceLayer = null;
        CanvasInstance c = ci(targetIdx);
        c.elementEditBar.setVisible(false);
        c.layeredPane.repaint();
    }

    /** Resets element drag/scale state for the given canvas when switching away. */
    private void resetElementDragState(int canvasIdx) {
        CanvasInstance c = ci(canvasIdx);
        c.draggingElement = false;
        c.elemDragAnchor  = null;
        c.elemActiveHandle = -1;
        c.elemScaleBase   = null;
        c.elemScaleStart  = null;
    }

    // ── Button actions ────────────────────────────────────────────────────────

    /** Add the current workingImage of the target canvas as a new layer on the source canvas. */
    private void elementEditAsNewLayer(int targetIdx) {
        if (elementEditSourceLayer == null) return;
        CanvasInstance src = ci(elementEditSourceIdx);
        CanvasInstance tgt = ci(targetIdx);
        if (tgt.workingImage == null) { exitElementEditMode(targetIdx); return; }

        BufferedImage img = deepCopy(tgt.workingImage);
        ImageLayer newLayer = new ImageLayer(src.nextElementId++, img,
                elementEditSourceLayer.x(), elementEditSourceLayer.y(),
                img.getWidth(), img.getHeight());
        src.activeElements.add(newLayer);
        src.selectedElements.clear();
        src.selectedElements.add(newLayer);
        src.hasUnsavedChanges = true;
        markDirty(elementEditSourceIdx);
        refreshElementPanel();
        if (src.canvasPanel != null) src.canvasPanel.repaint();
        exitElementEditMode(targetIdx);
        ToastNotification.show(this, "Als neues Layer eingefügt");
    }

    /** Replace the source layer's image with the current workingImage of the target canvas. */
    private void elementEditIntoSourceLayer(int targetIdx) {
        if (elementEditSourceLayer == null) return;
        CanvasInstance src = ci(elementEditSourceIdx);
        CanvasInstance tgt = ci(targetIdx);
        if (tgt.workingImage == null) { exitElementEditMode(targetIdx); return; }

        if (!(elementEditSourceLayer instanceof ImageLayer)) {
            showErrorDialog("Nicht möglich", "Nur ImageLayer können überschrieben werden.");
            return;
        }
        BufferedImage img = deepCopy(tgt.workingImage);
        // Replace layer in-place: find it by id and swap the image
        List<Layer> els = src.activeElements;
        for (int i = 0; i < els.size(); i++) {
            if (els.get(i).id() == elementEditSourceLayer.id()) {
                ImageLayer old = (ImageLayer) els.get(i);
                ImageLayer replacement = new ImageLayer(old.id(), img,
                        old.x(), old.y(), img.getWidth(), img.getHeight());
                els.set(i, replacement);
                // Keep selectedElements in sync with activeElements
                for (int j = 0; j < src.selectedElements.size(); j++) {
                    if (src.selectedElements.get(j).id() == old.id()) {
                        src.selectedElements.set(j, replacement);
                        break;
                    }
                }
                break;
            }
        }
        src.hasUnsavedChanges = true;
        markDirty(elementEditSourceIdx);
        refreshElementPanel();
        if (src.canvasPanel != null) src.canvasPanel.repaint();
        exitElementEditMode(targetIdx);
        ToastNotification.show(this, "Layer übernommen");
    }

    /** Keep the target canvas as a normal standalone image — just exit edit mode. */
    private void elementEditAsNewImage(int targetIdx) {
        exitElementEditMode(targetIdx);
        ToastNotification.show(this, "Als neues Image behalten");
    }

    /** Discard changes: clear the target canvas and exit edit mode. */
    private void elementEditAbort(int targetIdx) {
        CanvasInstance c = ci(targetIdx);
        c.workingImage     = null;
        c.originalImage    = null;
        c.sourceFile       = null;
        c.undoStack.clear();
        c.redoStack.clear();
        c.activeElements.clear();
        c.selectedElements.clear();
        c.hasUnsavedChanges = false;
        c.viewportPanel.setVisible(false);
        if (c.viewportPanel.getParent() != null) c.layeredPane.remove(c.viewportPanel);
        c.layeredPane.add(c.dropHintPanel, JLayeredPane.DEFAULT_LAYER);
        int w = c.layeredPane.getWidth(), h = c.layeredPane.getHeight();
        c.dropHintPanel.setBounds(0, 0, Math.max(w,1), Math.max(h,1));
        exitElementEditMode(targetIdx);
        refreshElementPanel();
        updateTitle();
        c.layeredPane.revalidate();
        c.layeredPane.repaint();
    }

    /** Close the edit bar without any action (keep the canvas as-is). */
    private void elementEditClose(int targetIdx) {
        exitElementEditMode(targetIdx);
    }

    /** CTRL+S: save silently without any confirmation dialog. */
    private void saveImageSilent() {
        CanvasInstance c = ci();
        if (c.sourceFile == null) return;
        try {
            String suffix  = getSaveSuffix();
            String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
            ImageIO.write(c.workingImage, "PNG", new File(outPath));
            c.hasUnsavedChanges = false;
            dirtyFiles.remove(c.sourceFile);
            updateTitle();
            updateDirtyUI();
            refreshGalleryThumbnail();
            ToastNotification.show(this, "Gespeichert");
            SwingUtilities.invokeLater(this::ensureElementEditBarVisible);
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    /** CTRL+SHIFT+S: burn visible elements, save as copy with new name. */
    private void saveBurnedElementsCopy() {
        CanvasInstance c = ci();
        if (c.sourceFile == null || c.workingImage == null) return;

        // Burn elements into canvas
        BufferedImage burned = burnVisibleElements();
        if (burned == null) return;

        // Show save dialog
        String suffix = "_burned";
        String outPath = WhiteToAlphaConverter.getOutputPath(c.sourceFile, suffix);
        File suggestedFile = new File(outPath);

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(suggestedFile);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));

        int result = chooser.showSaveDialog(this);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                File target = chooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".png")) {
                    target = new File(target.getAbsolutePath() + ".png");
                }
                ImageIO.write(burned, "PNG", target);
                ToastNotification.show(this, "Mit Elementen gespeichert: " + target.getName());
            } catch (IOException e) {
                showErrorDialog("Speicherfehler", e.getMessage());
            }
        }
    }

    /** CTRL+SHIFT+ALT+S: burn visible elements, save with same name (overwrite). */
    private void saveBurnedElementsOriginal() {
        CanvasInstance c = ci();
        if (c.sourceFile == null || c.workingImage == null) return;

        // Burn elements into canvas
        BufferedImage burned = burnVisibleElements();
        if (burned == null) return;

        // Confirm overwrite
        int result = javax.swing.JOptionPane.showConfirmDialog(this,
                "Elemente einbrennen und Originaldatei überschreiben?\n" + c.sourceFile.getName(),
                "Bestätigung", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);

        if (result == javax.swing.JOptionPane.OK_OPTION) {
            try {
                ImageIO.write(burned, "PNG", c.sourceFile);
                c.hasUnsavedChanges = false;
                dirtyFiles.remove(c.sourceFile);
                updateTitle();
                updateDirtyUI();
                ToastNotification.show(this, "Mit Elementen gespeichert: " + c.sourceFile.getName());
            } catch (IOException e) {
                showErrorDialog("Speicherfehler", e.getMessage());
            }
        }
    }

    /**
     * Burns all active elements into the canvas image.
     * Merges all layers (ImageLayer, TextLayer, PathLayer) into the working image.
     * Returns a new BufferedImage with burned elements, or null on error.
     */
    private BufferedImage burnVisibleElements() {
        CanvasInstance c = ci();
        if (c.workingImage == null || c.activeElements.isEmpty()) {
            return c.workingImage;  // No elements to burn
        }

        try {
            // Create a copy of the working image
            BufferedImage result = new BufferedImage(
                    c.workingImage.getWidth(), c.workingImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = result.createGraphics();

            // Draw the base canvas
            g2.drawImage(c.workingImage, 0, 0, null);

            // Draw all active elements on top
            for (Layer el : c.activeElements) {
                if (el instanceof ImageLayer il) {
                    // Get opacity as alpha
                    float alpha = il.opacity() / 100.0f;
                    java.awt.Composite origComposite = g2.getComposite();
                    if (alpha < 1.0f) {
                        g2.setComposite(java.awt.AlphaComposite.getInstance(
                                java.awt.AlphaComposite.SRC_OVER, alpha));
                    }

                    // Draw with rotation if needed
                    if (Math.abs(il.rotationAngle()) > 0.001) {
                        java.awt.geom.AffineTransform orig = g2.getTransform();
                        double cx = il.x() + il.width() / 2.0;
                        double cy = il.y() + il.height() / 2.0;
                        g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
                        g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
                        g2.setTransform(orig);
                    } else {
                        g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
                    }

                    if (alpha < 1.0f) {
                        g2.setComposite(origComposite);
                    }
                } else if (el instanceof TextLayer tl) {
                    // Render TextLayer
                    int style = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
                    Font font = new Font(tl.fontName(), style, tl.fontSize());
                    g2.setFont(font);
                    g2.setColor(tl.fontColor());
                    String[] lines = tl.text().split("\n", -1);
                    FontMetrics fm = g2.getFontMetrics();
                    for (int i = 0; i < lines.length; i++) {
                        g2.drawString(lines[i], tl.x() + 4, tl.y() + fm.getAscent() + i * fm.getHeight() + 4);
                    }
                } else if (el instanceof PathLayer pl) {
                    // Render PathLayer (simple polygon outline)
                    java.util.List<Point3D> points = pl.points();
                    if (!points.isEmpty()) {
                        int[] xs = new int[points.size()];
                        int[] ys = new int[points.size()];
                        for (int i = 0; i < points.size(); i++) {
                            xs[i] = (int) points.get(i).x;
                            ys[i] = (int) points.get(i).y;
                        }
                        g2.setColor(new Color(100, 150, 200));
                        g2.setStroke(new BasicStroke(2));
                        if (pl.isClosed()) {
                            g2.drawPolygon(xs, ys, points.size());
                        } else {
                            g2.drawPolyline(xs, ys, points.size());
                        }
                    }
                }
            }

            g2.dispose();
            return result;
        } catch (Exception ex) {
            System.err.println("[ERROR] Failed to burn elements: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    /** Re-asserts element-edit bar visibility after any operation that might disturb it. */
    private void ensureElementEditBarVisible() {
        if (elementEditSourceLayer == null) return;
        CanvasInstance tc = canvases[elementEditTargetIdx];
        if (tc.elementEditBar == null) return;
        repositionElementEditBar(elementEditTargetIdx);
        tc.elementEditBar.setVisible(true);
        tc.layeredPane.repaint();
    }

    public void markDirty() {
        markDirty(activeCanvasIndex);
    }

    public void markDirty(int idx) {
        CanvasInstance c = ci(idx);
        c.hasUnsavedChanges = true;
        if (c.sourceFile != null) dirtyFiles.add(c.sourceFile);
        updateTitle();
        updateDirtyUI();
        refreshElementPanel();
        refreshGalleryThumbnail();
        // Refresh all panels
        if (mapsPanel != null) {
            try {
                mapsPanel.refreshMapsList();
            } catch (Exception ex) {
                System.err.println("[WARN] Failed to refresh maps panel: " + ex.getMessage());
            }
        }
        c.canvasPanel.repaint();
        if (showRuler && idx == 0) { hRuler.repaint(); vRuler.repaint(); }
    }

    private void updateDirtyUI() {
        ci(0).tileGallery.setDirtyFiles(dirtyFiles);
        if (ci(1).tileGallery != null) {
            ci(1).tileGallery.setDirtyFiles(dirtyFiles);
        }
    }

    // =========================================================================
    // Mode toggles
    // =========================================================================
    private void toggleAlphaMode() {
        CanvasInstance c = ci();
        if (appMode != AppMode.ALPHA_EDITOR) return;
        floodfillMode = !floodfillMode;
        modeLabel.setText("Modus: " + (floodfillMode ? "Floodfill" : "Selective Alpha"));
        boolean sel = !floodfillMode;
        applyButton.setEnabled(sel && c.sourceFile != null);
        clearSelectionsButton.setEnabled(sel && c.sourceFile != null);
        c.selectedAreas.clear(); c.canvasPanel.repaint();
    }

    private void togglePaintMode() {
        CanvasInstance c = ci();
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
        updateModeLabel();
        if (entering) {
            paintToolbar.showToolbar();
            applyButton.setEnabled(false);
            clearSelectionsButton.setEnabled(false);
        } else {
            paintToolbar.hideToolbar();
            boolean sel = !floodfillMode;
            applyButton.setEnabled(sel && c.sourceFile != null);
            clearSelectionsButton.setEnabled(sel && c.sourceFile != null);
        }
        c.selectedAreas.clear();
        c.lastPaintPoint = null;
        c.shapeStartPoint = null;
        // Refit or center after paint toolbar visibility changes.
        // Double-deferred so the toolbar layout pass completes before we
        // measure the viewport height for fitting.
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            if (!c.userHasManuallyZoomed) {
                fitToViewport();
            } else {
                centerCanvasX();
            }
            c.canvasPanel.repaint();
        }));
        c.paintSnapshot = null;
        c.canvasPanel.setCursor(entering
                ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                : Cursor.getDefaultCursor());
        c.canvasPanel.repaint();
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
        updateModeLabel();
        ci().canvasPanel.repaint();
    }

    /** Book mode: toggle paper layout editor. Paint, Canvas, and Book can coexist. */
    private void toggleBookMode() {
        boolean entering = bookModeBtn.isSelected();
        if (entering) {
            // Scene and Book are mutually exclusive
            if (sceneModeBtn.isSelected()) {
                sceneModeBtn.setSelected(false);
            }
        }
        // appMode stays unchanged (PAINT or ALPHA_EDITOR)
        updateModeLabel();
        ci().canvasPanel.repaint();
    }

    /** Scene mode: toggle scene editor. Paint, Canvas, and Scene can coexist. */
    private void toggleSceneMode() {
        boolean entering = sceneModeBtn.isSelected();
        if (entering) {
            // Book and Scene are mutually exclusive
            if (bookModeBtn.isSelected()) {
                bookModeBtn.setSelected(false);
            }
        }
        // appMode stays unchanged (PAINT or ALPHA_EDITOR)
        updateModeLabel();
        ci().canvasPanel.repaint();
    }

    /** Shows or hides the element layer panels based on active canvas and mode. */
    private void setElementPanelVisible(boolean visible) {
        if (visible) {
            // Show appropriate element panel based on active canvas
            if (activeCanvasIndex == 0) {
                elementLayerPanel.setVisible(true);
                if (elementLayerPanel2 != null) elementLayerPanel2.setVisible(false);
            } else {
                elementLayerPanel.setVisible(false);
                if (elementLayerPanel2 != null) elementLayerPanel2.setVisible(true);
            }
            refreshElementPanel();
        } else {
            // Hide both panels
            elementLayerPanel.setVisible(false);
            if (elementLayerPanel2 != null) elementLayerPanel2.setVisible(false);
        }
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /** Updates modeLabel to show all active mode flags dynamically. */
    private void updateModeLabel() {
        if (appMode == AppMode.PAINT) {
            StringBuilder sb = new StringBuilder("Modus: Paint");
            if (canvasModeBtn.isSelected()) sb.append(" / Canvas");
            if (bookModeBtn.isSelected())   sb.append(" / Buch");
            if (sceneModeBtn.isSelected())  sb.append(" / Szene");
            modeLabel.setText(sb.toString());
        } else {
            // ALPHA_EDITOR base mode
            StringBuilder sb = new StringBuilder(
                "Modus: " + (floodfillMode ? "Floodfill" : "Selective Alpha"));
            if (bookModeBtn.isSelected())  sb.append(" / Buch");
            if (sceneModeBtn.isSelected()) sb.append(" / Szene");
            modeLabel.setText(sb.toString());
        }
    }

    /** Rebuilds the element layer panel tiles from the current activeElements. */
    private void refreshElementPanel() {
        if (elementLayerPanel  != null && elementLayerPanel.isShowing())
            elementLayerPanel.refresh(ci(0).activeElements);
        if (elementLayerPanel2 != null && elementLayerPanel2.isShowing())
            elementLayerPanel2.refresh(ci(1).activeElements);
    }

    /** Builds the callbacks for the ElementLayerPanel, bound to a specific canvas index.
     *  Panel operations (delete, burn, …) always affect that panel's own canvas.
     *  Paste/insert uses ci() separately and is unaffected by this binding. */
    /**
     * Helper: Update an ImageLayer in both activeElements and selectedElements lists by ID.
     * Used by flip/rotate/reset operations.
     */
    private void replaceInLists(CanvasInstance c, ImageLayer updated) {
        for (int i = 0; i < c.activeElements.size(); i++) {
            if (c.activeElements.get(i).id() == updated.id()) {
                c.activeElements.set(i, updated);
                break;
            }
        }
        for (int i = 0; i < c.selectedElements.size(); i++) {
            if (c.selectedElements.get(i).id() == updated.id()) {
                c.selectedElements.set(i, updated);
                break;
            }
        }
    }

    /**
     * Rendert Canvas + alle aktiven Elemente als Composite-Bild (für Gallery-Thumbnail).
     * Nur ImageLayers mit Rotation werden mit g2.rotate() gerendert.
     */
    private BufferedImage renderCompositeForThumbnail(CanvasInstance c) {
        if (c.workingImage == null) return null;
        int w = c.workingImage.getWidth(), h = c.workingImage.getHeight();
        BufferedImage comp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = comp.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Canvas background
        g2.drawImage(c.workingImage, 0, 0, null);
        // Aktive Elemente
        for (Layer el : c.activeElements) {
            if (el instanceof ImageLayer il) {
                if (Math.abs(il.rotationAngle()) > 0.001) {
                    // Rotated: apply transform
                    double cx = il.x() + il.width() / 2.0;
                    double cy = il.y() + il.height() / 2.0;
                    g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
                    g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
                    g2.rotate(-Math.toRadians(il.rotationAngle()), cx, cy);
                } else {
                    // Unrotated
                    g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
                }
            }
            // TextLayer und PathLayer: für Thumbnail vernachlässigt
        }
        g2.dispose();
        return comp;
    }

    /**
     * Aktualisiert das Gallery-Thumbnail für den aktuellen Canvas
     * mit dem Live-Composite aus Canvas + Elementen.
     */
    private void refreshGalleryThumbnail() {
        CanvasInstance c = ci();
        if (c.sourceFile == null || c.workingImage == null) return;
        BufferedImage thumb = renderCompositeForThumbnail(c);
        if (thumb != null) c.tileGallery.refreshThumbnailFor(c.sourceFile, thumb);
    }

    private ElementLayerPanel.Callbacks buildElementLayerCallbacks(int idx) {
        return new ElementLayerPanel.Callbacks() {
            private CanvasInstance c() { return canvases[idx]; }

            @Override public List<Layer> getActiveElements()   { return c().activeElements; }
            @Override public List<Layer> getSelectedElements() { return c().selectedElements; }
            @Override public void setSelectedElement(Layer el) {
                c().selectedElements.clear();
                if (el != null) c().selectedElements.add(el);
                if (c().canvasPanel != null) c().canvasPanel.repaint();
            }
            @Override public void toggleElementSelection(Layer el) {
                doToggleElementSelection(el);
                if (c().canvasPanel != null) c().canvasPanel.repaint();
            }
            @Override public void deleteElement(Layer el) {
                c().activeElements.removeIf(e -> e.id() == el.id());
                c().selectedElements.removeIf(e -> e.id() == el.id());
                markDirty();
                refreshElementPanel();
                if (c().canvasPanel != null) c().canvasPanel.repaint();
            }
            @Override public void burnElement(Layer el) {
                if (c().workingImage == null) return;
                // Tile holds a snapshot — look up live layer so position/scale are current
                Layer live = c().activeElements.stream()
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
                PaintEngine.pasteRegion(c().workingImage, imgToBurn, new java.awt.Point(live.x(), live.y()));
                c().activeElements.removeIf(e -> e.id() == el.id());
                c().selectedElements.removeIf(e -> e.id() == el.id());
                markDirty();
                refreshElementPanel();
                if (c().canvasPanel != null) c().canvasPanel.repaint();
            }

            @Override public void exportElementAsImage(Layer el) {
                if (c().workingImage == null || c().sourceFile == null) return;
                // Get live layer
                Layer live = c().activeElements.stream()
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
                String sourceName = c().sourceFile.getName();
                int lastDot = sourceName.lastIndexOf('.');
                String baseName = lastDot > 0 ? sourceName.substring(0, lastDot) : sourceName;
                String extension = lastDot > 0 ? sourceName.substring(lastDot) : ".png";
                String defaultName = baseName + "_layer_" + live.id() + extension;

                // Find unique filename if file already exists
                File exportDir = c().sourceFile.getParentFile();
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
                final String defaultNameFinal = defaultName;
                javax.swing.JTextField fileNameField = new javax.swing.JTextField(defaultName);
                fileNameField.selectAll();

                String[] options = {"Speichern", "Abbrechen"};
                javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
                panel.add(new javax.swing.JLabel("Dateiname:"), java.awt.BorderLayout.WEST);
                panel.add(fileNameField, java.awt.BorderLayout.CENTER);

                // Allow Enter key to save immediately
                fileNameField.addActionListener(ev -> {
                    String fileName = fileNameField.getText().trim();
                    if (!fileName.isEmpty()) {
                        saveElementAsImageFile(imgToExport, new File(exportDirFinal, fileName));
                    }
                });

                int result = javax.swing.JOptionPane.showOptionDialog(SelectiveAlphaEditor.this, panel, "Exportieren als Bild",
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

                    // Add the new image to the gallery of this canvas
                    if (c().tileGallery != null) {
                        java.util.List<File> newFiles = new java.util.ArrayList<>();
                        newFiles.add(file);
                        c().tileGallery.addFiles(newFiles);
                    }

                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor.this, "Bild gespeichert:\n" + file.getName(),
                            "Erfolg", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    System.err.println("[ERROR] Failed to export element: " + ex.getMessage());
                    ex.printStackTrace();
                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor.this, "Fehler beim Speichern:\n" + ex.getMessage(),
                            "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }

            @Override public void repaintCanvas() {
                if (c().canvasPanel != null) c().canvasPanel.repaint();
            }
            @Override public void onCloseRequested() {
                canvasModeBtn.setSelected(false);
                toggleCanvasMode();
            }
            @Override public void onLayerPanelElementHover(int elementId) {
                // Forward tile hover to canvas so it can highlight the matching element
                if (c().canvasPanel != null) c().canvasPanel.setHoveredElementId(elementId);
            }

            @Override public void openElementInOtherCanvas(Layer el) {
                doOpenImageLayerInOtherCanvas(idx, el);
            }
            @Override public void openTextLayerForEditing(Layer el) {
                // Focus the owning canvas and enter text-edit mode directly
                if (!(el instanceof TextLayer)) return;
                activeCanvasIndex = idx;
                updateCanvasFocusBorder();
                CanvasInstance ci = c();
                if (ci.canvasPanel != null) ci.canvasPanel.enterTextEditMode(el);
            }

            @Override public void resetElementRotation(Layer el) {
                CanvasInstance c = c();
                if (!(el instanceof ImageLayer il)) return;
                pushUndo();
                ImageLayer updated = il.withRotation(0.0);
                replaceInLists(c, updated);
                markDirty(idx);
                refreshElementPanel();
                if (c.canvasPanel != null) c.canvasPanel.repaint();
            }

            @Override public void exportElementAsMap(Layer el) {
                if (!(el instanceof TextLayer tl)) return;
                // Get live layer
                Layer live = c().activeElements.stream()
                        .filter(e -> e.id() == el.id()).findFirst().orElse(el);
                if (!(live instanceof TextLayer textLive)) return;

                // Get text content
                String textContent = textLive.text();
                if (textContent == null || textContent.isEmpty()) {
                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor.this,
                            "TextLayer hat keinen Inhalt.", "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Show dialog to select language and section
                MapCreateDialog dialog = new MapCreateDialog(SelectiveAlphaEditor.this, textContent);
                dialog.setVisible(true);

                if (!dialog.isAccepted()) return;

                try {
                    String mapId = MapManager.generateMapId();
                    TranslationMap newMap = new TranslationMap(mapId, dialog.getLanguage(),
                            dialog.getSection(), dialog.getTextI(), dialog.getTextII());
                    MapManager.addOrUpdateMap(newMap);

                    // Refresh maps panel if it exists
                    if (mapsPanel != null) {
                        mapsPanel.refreshMapsList();
                    }

                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor.this,
                            "Translation Map gespeichert:\nSprache: " + dialog.getLanguage() +
                            "\nBereich: " + dialog.getSection(),
                            "Erfolg", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    System.err.println("[ERROR] Failed to export map: " + ex.getMessage());
                    ex.printStackTrace();
                    javax.swing.JOptionPane.showMessageDialog(SelectiveAlphaEditor.this,
                            "Fehler beim Speichern:\n" + ex.getMessage(),
                            "Fehler", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }

            // ── Case 2: LayerTile dropped onto another ElementLayerPanel ─────
            @Override public void insertLayerCopyAt(Layer layer, int visualIdx) {
                CanvasInstance c = c();
                Layer copy = copyLayerWithNewId(layer, c.nextElementId++);
                if (copy == null) return;
                int insertIdx = visualToInsertIndex(visualIdx, c.activeElements.size());
                c.activeElements.add(insertIdx, copy);
                c.selectedElements.clear();
                c.selectedElements.add(copy);
                markDirty(idx);
                if (c.canvasPanel != null) c.canvasPanel.repaint();
                SwingUtilities.invokeLater(() -> refreshElementPanel());
            }

            // ── Case 4: TileGallery right-drag dropped onto ElementLayerPanel ─
            @Override public void insertFileAsLayerAt(File file, int visualIdx) {
                CanvasInstance c = c();
                if (c.workingImage == null) return;
                try {
                    BufferedImage img = javax.imageio.ImageIO.read(file);
                    if (img == null) return;
                    img = normalizeImage(img);
                    int[] size = fitElementSize(img.getWidth(), img.getHeight(),
                                                c.workingImage.getWidth(), c.workingImage.getHeight());
                    int cx = Math.max(0, (c.workingImage.getWidth()  - size[0]) / 2);
                    int cy = Math.max(0, (c.workingImage.getHeight() - size[1]) / 2);
                    ImageLayer layer = new ImageLayer(c.nextElementId++, img, cx, cy,
                                                     size[0], size[1]);
                    int insertIdx = visualToInsertIndex(visualIdx, c.activeElements.size());
                    c.activeElements.add(insertIdx, layer);
                    c.selectedElements.clear();
                    c.selectedElements.add(layer);
                    markDirty(idx);
                    if (c.canvasPanel != null) c.canvasPanel.repaint();
                    SwingUtilities.invokeLater(() -> refreshElementPanel());
                } catch (Exception ex) {
                    showErrorDialog("Fehler", "Bild konnte nicht geladen werden: " + ex.getMessage());
                }
            }
        };
    }

    /** Opens an ImageLayer (or any renderable layer) in the other canvas for full pixel editing. */
    private void doOpenImageLayerInOtherCanvas(int sourceIdx, Layer el) {
        BufferedImage img = null;
        if (el instanceof ImageLayer il) {
            img = il.image();
        } else if (el instanceof TextLayer tl) {
            img = renderTextLayerToImage(tl);
        }
        if (img == null) return;
        try {
            java.io.File tmp = java.io.File.createTempFile("element_" + el.id() + "_", ".png");
            tmp.deleteOnExit();
            ImageIO.write(img, "PNG", tmp);

            int targetIdx = 1 - sourceIdx;
            activeCanvasIndex = targetIdx;

            if (targetIdx == 1) {
                secondCanvasBtn.setEnabled(true);
                secondCanvasBtn.setSelected(true);
                updateLayoutVisibility();
                if (galleryWrapper != null) { galleryWrapper.revalidate(); galleryWrapper.repaint(); }
            }

            loadFile(tmp, targetIdx);
            activateElementEditMode(targetIdx, el, sourceIdx);
        } catch (IOException ex) {
            showErrorDialog("Fehler", "Element konnte nicht geöffnet werden:\n" + ex.getMessage());
        }
    }

    // =========================================================================
    // Transformations
    // =========================================================================
    private void doFlipH() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // Apply to selected elements first
        if (!c.selectedElements.isEmpty()) {
            pushUndo();
            for (int i = 0; i < c.selectedElements.size(); i++) {
                Layer el = c.selectedElements.get(i);
                if (el instanceof ImageLayer il) {
                    BufferedImage flipped = PaintEngine.flipHorizontal(il.image());
                    ImageLayer updated = new ImageLayer(il.id(), flipped, il.x(), il.y(), il.width(), il.height());
                    c.selectedElements.set(i, updated);
                    // Also update in activeElements
                    for (int j = 0; j < c.activeElements.size(); j++) {
                        if (c.activeElements.get(j).id() == updated.id()) {
                            c.activeElements.set(j, updated);
                            break;
                        }
                    }
                }
            }
            markDirty();
            refreshElementPanel();
            if (c.canvasPanel != null) c.canvasPanel.repaint();
            return;
        }

        // Apply to canvas
        Rectangle sel = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        pushUndo();
        if (sel != null) {
            PaintEngine.flipHorizontalInRegion(c.workingImage, sel);
        } else {
            c.workingImage = PaintEngine.flipHorizontal(c.workingImage);
        }
        markDirty();
    }

    private void doFlipV() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // Apply to selected elements first
        if (!c.selectedElements.isEmpty()) {
            pushUndo();
            for (int i = 0; i < c.selectedElements.size(); i++) {
                Layer el = c.selectedElements.get(i);
                if (el instanceof ImageLayer il) {
                    BufferedImage flipped = PaintEngine.flipVertical(il.image());
                    ImageLayer updated = new ImageLayer(il.id(), flipped, il.x(), il.y(), il.width(), il.height());
                    c.selectedElements.set(i, updated);
                    // Also update in activeElements
                    for (int j = 0; j < c.activeElements.size(); j++) {
                        if (c.activeElements.get(j).id() == updated.id()) {
                            c.activeElements.set(j, updated);
                            break;
                        }
                    }
                }
            }
            markDirty();
            refreshElementPanel();
            if (c.canvasPanel != null) c.canvasPanel.repaint();
            return;
        }

        // Apply to canvas
        Rectangle sel = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        pushUndo();
    }

    private void doRotate(double angleDeg) {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // Apply to selected elements first
        if (!c.selectedElements.isEmpty()) {
            pushUndo();
            for (int i = 0; i < c.selectedElements.size(); i++) {
                Layer el = c.selectedElements.get(i);
                if (el instanceof ImageLayer il) {
                    // Add angle to existing rotation (metadata, no pixel manipulation)
                    double newAngle = il.rotationAngle() + angleDeg;
                    ImageLayer updated = il.withRotation(newAngle);
                    c.selectedElements.set(i, updated);
                    // Also update in activeElements
                    for (int j = 0; j < c.activeElements.size(); j++) {
                        if (c.activeElements.get(j).id() == updated.id()) {
                            c.activeElements.set(j, updated);
                            break;
                        }
                    }
                }
            }
            markDirty();
            refreshElementPanel();
            if (c.canvasPanel != null) c.canvasPanel.repaint();
            return;
        }

        // Apply to canvas
        Rectangle sel = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        pushUndo();
        if (sel != null) {
            PaintEngine.flipVerticalInRegion(c.workingImage, sel);
        } else {
            c.workingImage = PaintEngine.flipVertical(c.workingImage);
        }
        markDirty();
    }

    private void doRotate() {
        if (ci().workingImage == null) return;
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
                CanvasInstance c = ci();
                double deg = Double.parseDouble(angleField.getText().trim());
                pushUndo();
                if (rotateHasSel) {
                    PaintEngine.rotateInRegion(c.workingImage, rotateSel, deg);
                } else {
                    c.workingImage = PaintEngine.rotate(c.workingImage, deg);
                    c.canvasWrapper.revalidate();
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
        CanvasInstance c = ci();
        if (c.workingImage == null) return;
        Rectangle scaleSel  = (appMode == AppMode.PAINT) ? getActiveSelection() : null;
        int origW = scaleSel != null ? scaleSel.width  : c.workingImage.getWidth();
        int origH = scaleSel != null ? scaleSel.height : c.workingImage.getHeight();

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
                if (scaleSel != null) {
                    Rectangle newSel = PaintEngine.scaleInRegion(c.workingImage, scaleSel, nw, nh);
                    c.selectedAreas.clear();
                    c.selectedAreas.add(newSel);
                } else {
                    c.workingImage = PaintEngine.scale(c.workingImage, nw, nh);
                    c.canvasWrapper.revalidate();
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
    public void commitFloat() {
        CanvasInstance c = ci();
        if (c.floatingImg == null || c.floatRect == null) return;
        BufferedImage scaled = PaintEngine.scale(c.floatingImg,
                Math.max(1, c.floatRect.width), Math.max(1, c.floatRect.height));
        if (appMode == AppMode.PAINT) {
            // Non-destructive: become an ImageLayer
            Layer el = new ImageLayer(c.nextElementId++, scaled,
                    c.floatRect.x, c.floatRect.y, c.floatRect.width, c.floatRect.height);
            c.activeElements.add(el);
            c.selectedElements.clear();
            c.selectedElements.add(el);
            refreshElementPanel();
        } else {
            PaintEngine.pasteRegion(c.workingImage, scaled, new Point(c.floatRect.x, c.floatRect.y));
        }
        c.floatingImg = null; c.floatRect = null;
        c.isDraggingFloat = false; c.floatDragAnchor = null;
        c.activeHandle = -1;  c.scaleBaseRect = null; c.scaleDragStart = null;
        c.selectedAreas.clear();
        markDirty();
    }

    /** Discard the float and undo to the state before it was lifted. */
    private void cancelFloat() {
        CanvasInstance c = ci();
        c.floatingImg = null; c.floatRect = null;
        c.isDraggingFloat = false; c.floatDragAnchor = null;
        c.activeHandle = -1;  c.scaleBaseRect = null; c.scaleDragStart = null;
        c.selectedAreas.clear();
        doUndo();
    }

    /** Convert floatRect (image-space) to canvasPanel screen-space. */
    public Rectangle floatRectScreen() {
        CanvasInstance c = ci();
        if (c.floatRect == null) return new Rectangle(0, 0, 0, 0);
        return new Rectangle(
            (int) Math.round(c.floatRect.x      * c.zoom),
            (int) Math.round(c.floatRect.y      * c.zoom),
            (int) Math.round(c.floatRect.width  * c.zoom),
            (int) Math.round(c.floatRect.height * c.zoom));
    }

    /**
     * 8 handle hit-rects around {@code sr} (screen-space).
     * Order: TL=0, TC=1, TR=2, ML=3, MR=4, BL=5, BC=6, BR=7
     */
    public Rectangle[] handleRects(Rectangle sr) {
        int x = sr.x, y = sr.y, w = sr.width, h = sr.height;
        int mx = x + w / 2, my = y + h / 2, rx = x + w, by = y + h;
        int hs = 4; // half-size → each handle square is 8×8 px
        return new Rectangle[]{
            new Rectangle(x  - hs, y  - hs, hs*2, hs*2), // 0 TL
            new Rectangle(mx - hs, y  - hs, hs*2, hs*2), // 1 TC
            new Rectangle(rx - hs, y  - hs, hs*2, hs*2), // 2 TR
            new Rectangle(x  - hs, my - hs, hs*2, hs*2), // 3 ML
            new Rectangle(rx - hs, my - hs, hs*2, hs*2), // 4 MR
            new Rectangle(x  - hs, by - hs, hs*2, hs*2), // 5 BL
            new Rectangle(mx - hs, by - hs, hs*2, hs*2), // 6 BC
            new Rectangle(rx - hs, by - hs, hs*2, hs*2), // 7 BR
        };
    }

    /** Returns rotation handle position for selected element (30px above center). */
    public Point getRotationHandlePos(Rectangle sr) {
        int mx = sr.x + sr.width / 2;
        int ty = sr.y - 30; // 30 pixels above top
        return new Point(mx, ty);
    }

    /** Returns rotation handle hit rect (8×8 around handle position). */
    public Rectangle getRotationHandleRect(Rectangle sr) {
        Point p = getRotationHandlePos(sr);
        int hs = 4;
        return new Rectangle(p.x - hs, p.y - hs, hs*2, hs*2);
    }

    /** Returns 0-7 if {@code pt} (canvasPanel coords) hits a handle, else -1. */
    public int hitHandle(Point pt) {
        CanvasInstance c = ci();
        if (c.floatRect == null) return -1;
        Rectangle[] handles = handleRects(floatRectScreen());
        for (int i = 0; i < handles.length; i++) if (handles[i].contains(pt)) return i;
        return -1;
    }

    /**
     * Compute the new floatRect when handle {@code handle} is dragged from
     * {@code origin} to {@code current} (both in canvasPanel screen-space).
     * Uses double precision throughout to avoid integer-truncation drift at
     * high zoom levels. Corners scale proportionally; sides scale one axis.
     */
    private Rectangle computeNewFloatRect(int handle, Rectangle base,
                                           Point origin, Point current) {
        // Delta in image-space (double, no truncation)
        double dx = (current.x - origin.x) / ci().zoom;
        double dy = (current.y - origin.y) / ci().zoom;
        double bx = base.x, by = base.y, bw = base.width, bh = base.height;
        final double MIN = 1.0;

        double rx, ry, rw, rh;
        switch (handle) {
            case 0 -> { // TL – proportional, anchor BR
                rw = Math.max(MIN, bw - dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx + bw - rw; ry = by + bh - rh;
            }
            case 1 -> { // TC – scale Y only, anchor bottom
                rh = Math.max(MIN, bh - dy);
                rx = bx; rw = bw; ry = by + bh - rh;
            }
            case 2 -> { // TR – proportional, anchor BL
                rw = Math.max(MIN, bw + dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx; ry = by + bh - rh;
            }
            case 3 -> { // ML – scale X only, anchor right
                rw = Math.max(MIN, bw - dx);
                rx = bx + bw - rw; ry = by; rh = bh;
            }
            case 4 -> { // MR – scale X only, anchor left
                rw = Math.max(MIN, bw + dx);
                rx = bx; ry = by; rh = bh;
            }
            case 5 -> { // BL – proportional, anchor TR
                rw = Math.max(MIN, bw - dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx + bw - rw; ry = by;
            }
            case 6 -> { // BC – scale Y only, anchor top
                rh = Math.max(MIN, bh + dy);
                rx = bx; rw = bw; ry = by;
            }
            default -> { // BR (7) – proportional, anchor TL
                rw = Math.max(MIN, bw + dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx; ry = by;
            }
        }
        return new Rectangle(
            (int) Math.round(rx), (int) Math.round(ry),
            (int) Math.round(rw), (int) Math.round(rh));
    }

    private Cursor getResizeCursor(int handle) {
        return Cursor.getPredefinedCursor(switch (handle) {
            case 0 -> Cursor.NW_RESIZE_CURSOR;
            case 1 -> Cursor.N_RESIZE_CURSOR;
            case 2 -> Cursor.NE_RESIZE_CURSOR;
            case 3 -> Cursor.W_RESIZE_CURSOR;
            case 4 -> Cursor.E_RESIZE_CURSOR;
            case 5 -> Cursor.SW_RESIZE_CURSOR;
            case 6 -> Cursor.S_RESIZE_CURSOR;
            case 7 -> Cursor.SE_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        });
    }

    // =========================================================================
    // PaintToolbar callbacks
    // =========================================================================
    private PaintToolbar.Callbacks buildPaintCallbacks() {
        return new PaintToolbar.Callbacks() {
            @Override public void onToolChanged(PaintEngine.Tool tool)      { ci().canvasPanel.repaint(); }
            @Override public void onColorChanged(Color p, Color s)          {}
            @Override public void onStrokeChanged(int w)                    {}
            @Override public void onFillModeChanged(PaintEngine.FillMode m) {}
            @Override public void onBrushShapeChanged(PaintEngine.BrushShape s) {}
            @Override public void onAntialiasingChanged(boolean aa)         { ci().canvasPanel.repaint(); }
            @Override public void onCut()   { doCut(); }
            @Override public void onCopy()  { doCopy(); }
            @Override public void onPaste() { doPaste(); }
            @Override public void onToggleGrid(boolean show)  {
                showGrid = show; ci().canvasPanel.repaint();
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
            @Override public BufferedImage getWorkingImage() { return ci().workingImage; }
        };
    }

    // =========================================================================
    // Canvas callbacks (indexed)
    // =========================================================================
    private CanvasCallbacks buildCanvasCallbacks(int idx) {
        return new CanvasCallbacks() {
            private CanvasInstance c() { return ci(idx); }

            // ── Image & state ──
            @Override public BufferedImage getWorkingImage() { return c().workingImage; }
            @Override public AppMode getAppMode() { return appMode; }
            @Override public boolean isFloodfillMode() { return floodfillMode; }
            @Override public double getZoom() { return c().zoom; }
            @Override public void setZoom(double nz, Point anchor) {
                c().userHasManuallyZoomed = true;
                c().zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));
                if (c().zoomTimer == null) {
                    if (anchor != null && c().scrollPane != null) {
                        JViewport vp = c().scrollPane.getViewport();
                        c().zoomImgPt = new Point2D.Double(anchor.x / c().zoom, anchor.y / c().zoom);
                        c().zoomVpMouse = SwingUtilities.convertPoint(c().canvasPanel, anchor, vp);
                    } else {
                        c().zoomImgPt = null;
                        c().zoomVpMouse = null;
                    }
                }
                startZoomAnimation(idx);
            }
            @Override public JScrollPane getScrollPane() { return c().scrollPane; }
            @Override public Point screenToImage(Point screenPt) {
                int ix = (int) Math.floor(screenPt.x / c().zoom);
                int iy = (int) Math.floor(screenPt.y / c().zoom);
                if (c().workingImage != null) {
                    ix = Math.max(0, Math.min(c().workingImage.getWidth()  - 1, ix));
                    iy = Math.max(0, Math.min(c().workingImage.getHeight() - 1, iy));
                }
                return new Point(ix, iy);
            }

            // ── Selection ──
            @Override public List<Rectangle> getSelectedAreas() { return c().selectedAreas; }
            @Override public boolean isSelecting() { return c().isSelecting; }
            @Override public void setSelecting(boolean s) { c().isSelecting = s; }
            @Override public Point getSelectionStart() { return c().selectionStart; }
            @Override public void setSelectionStart(Point p) { c().selectionStart = p; }
            @Override public Point getSelectionEnd() { return c().selectionEnd; }
            @Override public void setSelectionEnd(Point p) { c().selectionEnd = p; }

            // ── Layers ──
            @Override public List<Layer> getActiveElements() { return c().activeElements; }
            @Override public Layer getSelectedElement() { return c().selectedElements.isEmpty() ? null : c().selectedElements.iterator().next(); }
            @Override public void setSelectedElement(Layer el) { c().selectedElements.clear(); if (el != null) c().selectedElements.add(el); }
            @Override public List<Layer> getSelectedElements() { return new ArrayList<>(c().selectedElements); }
            @Override public void setSelectedElements(List<Layer> els) { c().selectedElements.clear(); c().selectedElements.addAll(els); }
            @Override public void toggleElementSelection(Layer el) { if (c().selectedElements.contains(el)) c().selectedElements.remove(el); else c().selectedElements.add(el); }
            @Override public void moveSelectedElements(int dx, int dy) {
                List<Layer> sel = c().selectedElements;
                List<Layer> active = c().activeElements;
                // Update positions for all selected layers
                for (int i = 0; i < sel.size(); i++) {
                    Layer oldLayer = sel.get(i);
                    Layer newLayer = oldLayer.withPosition(oldLayer.x() + dx, oldLayer.y() + dy);
                    sel.set(i, newLayer);
                    // Also update in activeElements
                    for (int j = 0; j < active.size(); j++) {
                        if (active.get(j).id() == oldLayer.id()) {
                            active.set(j, newLayer);
                            break;
                        }
                    }
                }
            }
            @Override public int getNextElementId() { return c().nextElementId++; }
            @Override public void addElement(Layer el) { c().activeElements.add(el); }

            // ── Float ──
            @Override public BufferedImage getFloatingImage() { return c().floatingImg; }
            @Override public Rectangle getFloatRect() { return c().floatRect; }
            @Override public boolean isDraggingFloat() { return c().isDraggingFloat; }
            @Override public void setDraggingFloat(boolean d) { c().isDraggingFloat = d; }
            @Override public Point getFloatDragAnchor() { return c().floatDragAnchor; }
            @Override public void setFloatDragAnchor(Point p) { c().floatDragAnchor = p; }
            @Override public int getActiveHandle() { return c().activeHandle; }
            @Override public void setActiveHandle(int h) { c().activeHandle = h; }
            @Override public Rectangle getScaleBaseRect() { return c().scaleBaseRect; }
            @Override public void setScaleBaseRect(Rectangle r) { c().scaleBaseRect = r; }
            @Override public Point getScaleDragStart() { return c().scaleDragStart; }
            @Override public void setScaleDragStart(Point p) { c().scaleDragStart = p; }

            // ── Paint ──
            @Override public Point getLastPaintPoint() { return c().lastPaintPoint; }
            @Override public void setLastPaintPoint(Point p) { c().lastPaintPoint = p; }
            @Override public Point getShapeStartPoint() { return c().shapeStartPoint; }
            @Override public void setShapeStartPoint(Point p) { c().shapeStartPoint = p; }
            @Override public BufferedImage getPaintSnapshot() { return c().paintSnapshot; }
            @Override public void setPaintSnapshot(BufferedImage img) { c().paintSnapshot = img; }

            // ── Layer elem ──
            @Override public int getElemActiveHandle() { return c().elemActiveHandle; }
            @Override public void setElemActiveHandle(int h) { c().elemActiveHandle = h; }
            @Override public Rectangle getElemScaleBase() { return c().elemScaleBase; }
            @Override public void setElemScaleBase(Rectangle r) { c().elemScaleBase = r; }
            @Override public Point getElemScaleStart() { return c().elemScaleStart; }
            @Override public void setElemScaleStart(Point p) { c().elemScaleStart = p; }
            @Override public boolean isDraggingElement() { return c().draggingElement; }
            @Override public void setDraggingElement(boolean d) { c().draggingElement = d; }
            @Override public Point getElemDragAnchor() { return c().elemDragAnchor; }
            @Override public void setElemDragAnchor(Point p) { c().elemDragAnchor = p; }

            // ── Toolbar ──
            @Override public PaintToolbar getPaintToolbar() { return paintToolbar; }

            // ── Layer panel ──
            @Override public boolean isShowAllLayerOutlines() { return false; }
            @Override public void commitTextAsElement(BufferedImage img, int x, int y) {
                if (img == null) return;
                CanvasInstance ci = c();
                Layer el = new ImageLayer(ci.nextElementId++, deepCopy(img), x, y, img.getWidth(), img.getHeight());
                ci.activeElements.add(el);
                ci.selectedElements.clear();
                ci.selectedElements.add(el);
                refreshElementPanel();
                if (ci.canvasPanel != null) ci.canvasPanel.repaint();
            }
            @Override public void commitTextLayer(int id, String text, String font, int size,
                                                  boolean bold, boolean italic, Color col, int x, int y) {
                CanvasInstance ci = c();
                TextLayer updated = (id >= 0)
                        ? TextLayer.of(id, text, font, size, bold, italic, col, x, y)
                        : TextLayer.of(ci.nextElementId++, text, font, size, bold, italic, col, x, y);
                // Replace in activeElements if present (element stays in list during editing); add if new
                boolean found = false;
                for (int i = 0; i < ci.activeElements.size(); i++) {
                    if (ci.activeElements.get(i).id() == updated.id()) {
                        ci.activeElements.set(i, updated); found = true; break;
                    }
                }
                if (!found) ci.activeElements.add(updated);
                ci.selectedElements.clear();
                ci.selectedElements.add(updated);

                // Persist text font settings
                AppSettings settings = AppSettings.getInstance();
                settings.setFontName(font);
                settings.setFontSize(size);
                settings.setTextBold(bold);
                settings.setTextItalic(italic);
                settings.setFontColor(col.getRGB());
                try {
                    settings.save();
                } catch (IOException e) {
                    System.err.println("[WARN] Konnte Schriftart-Einstellungen nicht speichern: " + e.getMessage());
                }

                refreshElementPanel();
                if (ci.canvasPanel != null) ci.canvasPanel.repaint();
            }

            // ── Actions ──
            @Override public void pushUndo() { if (c().workingImage != null) c().undoStack.push(deepCopy(c().workingImage)); }
            @Override public void markDirty() { SelectiveAlphaEditor.this.markDirty(idx); }
            @Override public void performFloodfill(Point screenPt) { }
            @Override public void paintDot(Point imagePt) { }
            @Override public void commitFloat() { }
            @Override public void repaintCanvas() { if (c().canvasPanel != null) c().canvasPanel.repaint(); }
            @Override public void rotateSelectedElements(double angleDeg) { doRotate(angleDeg); }
            @Override public void onCanvasElementHover(int id) { }
            @Override public void clearSelection() { c().selectedAreas.clear(); c().isSelecting = false; }
            @Override public void liftSelectionToFloat() { }
            @Override public boolean isCanvasSubMode() { return canvasModeBtn.isSelected(); }
            @Override public void liftSelectionToElement(Rectangle sel) {
                CanvasInstance ci = c();
                if (sel == null || ci.workingImage == null) return;
                BufferedImage src = PaintEngine.cropRegion(ci.workingImage, sel);
                if (src == null) return;
                Layer el = new ImageLayer(ci.nextElementId++, src, sel.x, sel.y, src.getWidth(), src.getHeight());
                ci.activeElements.add(el);
                ci.selectedElements.clear();
                ci.selectedElements.add(el);
                ci.selectedAreas.clear();
                ci.isSelecting = false;
                refreshElementPanel();
                if (ci.canvasPanel != null) ci.canvasPanel.repaint();
            }
            @Override public void deleteSelection() { }
            @Override public void updateSelectedElement(Layer el) {
                if (el == null) return;
                CanvasInstance ci = c();
                for (int i = 0; i < ci.selectedElements.size(); i++) {
                    if (ci.selectedElements.get(i).id() == el.id()) {
                        ci.selectedElements.set(i, el); break;
                    }
                }
                for (int i = 0; i < ci.activeElements.size(); i++) {
                    if (ci.activeElements.get(i).id() == el.id()) {
                        ci.activeElements.set(i, el); break;
                    }
                }
            }
            @Override public void openImageLayerForEditing(Layer el) {
                doOpenImageLayerInOtherCanvas(idx, el);
            }

            // ── Utilities ──
            @Override public int hitHandle(Point screenPt) { return SelectiveAlphaEditor.this.hitHandle(screenPt); }
            @Override public Rectangle floatRectScreen() { return SelectiveAlphaEditor.this.floatRectScreen(); }
            @Override public Rectangle elemRectScreen(Layer el) { return SelectiveAlphaEditor.this.elemRectScreen(el, ci(idx).zoom); }
            @Override public Rectangle[] handleRects(Rectangle r) { return SelectiveAlphaEditor.this.handleRects(r); }
            @Override public Point getRotationHandlePos(Rectangle sr) { return SelectiveAlphaEditor.this.getRotationHandlePos(sr); }
            @Override public Rectangle getRotationHandleRect(Rectangle sr) { return SelectiveAlphaEditor.this.getRotationHandleRect(sr); }
            @Override public Rectangle getActiveSelection() { return SelectiveAlphaEditor.this.getActiveSelection(); }
            @Override public BufferedImage deepCopy(BufferedImage src) { return SelectiveAlphaEditor.this.deepCopy(src); }

            // ── Colors ──
            @Override public Color getCanvasBg1() { return canvasBg1; }
            @Override public Color getCanvasBg2() { return canvasBg2; }
        };
    }

    // =========================================================================
    // TileGallery callbacks
    // =========================================================================
    private TileGalleryPanel.Callbacks buildGalleryCallbacks(int idx) {
        return new TileGalleryPanel.Callbacks() {
            @Override public void onTileOpened(File f) {
                // No unsaved-changes dialog: dirty state is kept in cache + red border
                loadFile(f, idx);
            }
            @Override public void onSelectionChanged(List<File> files) {
                selectedImages = files;
            }
            @Override public void onDragStarted(File file) {
                if (idx == 0 && !secondCanvasBtn.isSelected() && ci(0).workingImage != null) {
                    repositionRightDropZone();
                    rightDropZone.setVisible(true);
                    ci(0).layeredPane.repaint();
                }
            }
            @Override public void onDragEnded() {
                if (idx == 0) {
                    rightDropZone.setVisible(false);
                    ci(0).layeredPane.repaint();
                }
            }

            // ── Case 5: LayerTile dropped onto TileGallery → save as PNG ─────
            @Override public void onLayerDropped(Layer layer) {
                CanvasInstance c = canvases[idx];
                if (c.sourceFile == null) return;
                // Render layer to image
                BufferedImage img = null;
                Layer live = c.activeElements.stream()
                        .filter(e -> e.id() == layer.id()).findFirst().orElse(layer);
                if (live instanceof ImageLayer il) {
                    img = PaintEngine.scale(il.image(),
                            Math.max(1, live.width()), Math.max(1, live.height()));
                } else if (live instanceof TextLayer tl) {
                    img = renderTextLayerToImage(tl);
                } else {
                    return; // PathLayer not supported for image export
                }
                // Auto-generate unique filename (same logic as exportElementAsImage)
                String sourceName = c.sourceFile.getName();
                int lastDot = sourceName.lastIndexOf('.');
                String baseName  = lastDot > 0 ? sourceName.substring(0, lastDot) : sourceName;
                String extension = lastDot > 0 ? sourceName.substring(lastDot)    : ".png";
                String name = baseName + "_layer_" + live.id() + extension;
                File exportDir = c.sourceFile.getParentFile();
                File target = new File(exportDir, name);
                int counter = 1;
                while (target.exists()) {
                    target = new File(exportDir, baseName + "_layer_" + live.id() + "_" + counter + extension);
                    counter++;
                }
                final File finalTarget = target;
                final BufferedImage finalImg = img;
                // saveElementAsImageFile lives in buildElementLayerCallbacks – replicate inline
                try {
                    javax.imageio.ImageIO.write(finalImg, "PNG", finalTarget);
                    List<File> added = new java.util.ArrayList<>();
                    added.add(finalTarget);
                    canvases[idx].tileGallery.addFiles(added);
                    ToastNotification.show(SelectiveAlphaEditor.this,
                            "Gespeichert: " + finalTarget.getName());
                } catch (Exception ex) {
                    showErrorDialog("Fehler", "Speichern fehlgeschlagen: " + ex.getMessage());
                }
            }

            // ── File copied via right-drag in gallery ─────────────────────────
            @Override public void onFileCopied(File copiedFile, int insertIndex) {
                CanvasInstance c = canvases[idx];
                // Add the copied file at the drop position
                c.tileGallery.addFileAtIndex(copiedFile, insertIndex);
                // Set as active (auto-select in gallery)
                c.tileGallery.setActiveFile(copiedFile);
                ToastNotification.show(SelectiveAlphaEditor.this,
                        "Kopie erstellt: " + copiedFile.getName());
            }
        };
    }


    // =========================================================================
    // Clipboard operations
    // =========================================================================

    /** CTRL+C — copy INSIDE selection → Element layer (or full image if no selection). */
    private void doCopy() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // Copy selected layers (non-destructive, for pasting into other canvases)
        if (!c.selectedElements.isEmpty()) {
            clipboardLayers = new ArrayList<>(c.selectedElements);
            // Still copy image for system clipboard compatibility
            Layer first = c.selectedElements.get(0);
            if (first instanceof ImageLayer il) {
                clipboard = il.image();
            } else if (first instanceof PathLayer pl) {
                clipboard = PaintEngine.cropPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            } else if (first instanceof TextLayer tl) {
                clipboard = renderTextLayerToImage(tl);
            }
            if (clipboard != null) copyToSystemClipboard(clipboard);
            return;
        }

        // No selected layers: copy image region
        Rectangle sel = getActiveSelection();
        if (sel != null) {
            clipboard = PaintEngine.cropRegion(c.workingImage, sel);
            copyToSystemClipboard(clipboard);
            addElementFromClipboard(clipboard, sel.x, sel.y);
            clipboardLayers = new ArrayList<>(c.selectedElements);  // carry layer to other canvas
        } else {
            clipboard = deepCopy(c.workingImage);
            clipboardLayers = null;
            copyToSystemClipboard(clipboard);
        }
    }

    /** CTRL+SHIFT+C — copy OUTSIDE selection → Element layer (full-size, inside punched out). */
    private void doCopyOutside() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // NEW: PathLayer support
        if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
            clipboard = PaintEngine.cropOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            if (clipboard != null) copyToSystemClipboard(clipboard);
            return;
        }

        Rectangle sel = getActiveSelection();
        if (sel != null) {
            clipboard = PaintEngine.cropOutside(c.workingImage, sel);
            copyToSystemClipboard(clipboard);
            addElementFromClipboard(clipboard, 0, 0);
            clipboardLayers = new ArrayList<>(c.selectedElements);  // carry layer to other canvas
        } else {
            // No selection: same as normal copy
            clipboard = deepCopy(c.workingImage);
            clipboardLayers = null;
            copyToSystemClipboard(clipboard);
        }
    }

    /** CTRL+X — cut INSIDE selection → Element layer + clear canvas pixels. */
    private void doCut() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // Cut selected layers: copy them to clipboard and remove from canvas
        if (!c.selectedElements.isEmpty() && !(c.selectedElements.get(0) instanceof PathLayer)) {
            pushUndo();
            clipboardLayers = new ArrayList<>(c.selectedElements);

            // Copy image for system clipboard
            Layer first = c.selectedElements.get(0);
            if (first instanceof ImageLayer il) {
                clipboard = il.image();
            } else if (first instanceof TextLayer tl) {
                clipboard = renderTextLayerToImage(tl);
            }
            if (clipboard != null) copyToSystemClipboard(clipboard);

            // Remove these layers from canvas
            for (Layer el : c.selectedElements) {
                c.activeElements.removeIf(e -> e.id() == el.id());
            }
            c.selectedElements.clear();
            markDirty();
            refreshElementPanel();
            c.canvasPanel.repaint();
            return;
        }

        // PathLayer support
        if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
            pushUndo();
            clipboard = PaintEngine.cropPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            clipboardLayers = null;  // PathLayer uses image clipboard
            if (clipboard != null) copyToSystemClipboard(clipboard);
            PaintEngine.clearPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            markDirty();
            return;
        }

        Rectangle sel = getActiveSelection();
        if (sel != null) {
            pushUndo();
            clipboard = PaintEngine.cropRegion(c.workingImage, sel);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(c.workingImage, sel);
            markDirty();
            addElementFromClipboard(clipboard, sel.x, sel.y);
            clipboardLayers = new ArrayList<>(c.selectedElements);  // carry layer to other canvas
        } else {
            // No selection: cut entire image
            pushUndo();
            clipboard = deepCopy(c.workingImage);
            clipboardLayers = null;
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(c.workingImage,
                    new Rectangle(0, 0, c.workingImage.getWidth(), c.workingImage.getHeight()));
            markDirty();
        }
    }

    /** CTRL+SHIFT+X — cut OUTSIDE selection → Element layer (full-size) + clear canvas outside. */
    private void doCutOutside() {
        CanvasInstance c = ci();
        if (c.workingImage == null) return;

        // NEW: PathLayer support
        if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
            pushUndo();
            clipboard = PaintEngine.cropOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            if (clipboard != null) copyToSystemClipboard(clipboard);
            PaintEngine.clearOutsidePolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
            markDirty();
            return;
        }

        Rectangle sel = getActiveSelection();
        if (sel != null) {
            pushUndo();
            clipboard = PaintEngine.cropOutside(c.workingImage, sel);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearOutside(c.workingImage, sel);
            markDirty();
            addElementFromClipboard(clipboard, 0, 0);
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
        CanvasInstance c = ci();
        Layer el = new ImageLayer(c.nextElementId++, deepCopy(img), x, y, img.getWidth(), img.getHeight());
        c.activeElements.add(el);
        c.selectedElements.clear();
        c.selectedElements.add(el);
        refreshElementPanel();
        if (c.canvasPanel != null) c.canvasPanel.repaint();
    }

    private void doPaste() {
        CanvasInstance c = ci();
        BufferedImage fromClip = pasteFromSystemClipboard();
        if (fromClip != null) clipboard = fromClip;

        // Try to paste layers first (if copied from another canvas)
        if (clipboardLayers != null && !clipboardLayers.isEmpty() && c.workingImage != null) {
            pushUndo();
            c.selectedElements.clear();

            for (Layer original : clipboardLayers) {
                // Create a new layer with a fresh ID for this canvas
                Layer newLayer;
                if (original instanceof ImageLayer il) {
                    newLayer = new ImageLayer(c.nextElementId++,
                        deepCopy(il.image()),
                        original.x(), original.y(),
                        original.width(), original.height());
                } else if (original instanceof TextLayer tl) {
                    newLayer = TextLayer.of(c.nextElementId++,
                        tl.text(), tl.fontName(), tl.fontSize(),
                        tl.fontBold(), tl.fontItalic(), tl.fontColor(),
                        original.x(), original.y());
                } else if (original instanceof PathLayer pl) {
                    newLayer = PathLayer.of(c.nextElementId++,
                        new ArrayList<>(pl.points()),
                        null,  // image will be null, rendered on-the-fly
                        pl.isClosed(),
                        original.x(), original.y());
                } else {
                    continue;  // Unknown layer type
                }

                c.activeElements.add(newLayer);
                c.selectedElements.add(newLayer);
            }

            c.hasUnsavedChanges = true;
            markDirty();
            refreshElementPanel();
            c.canvasPanel.repaint();
            updateTitle();
            return;
        }

        // No layers in clipboard: paste as floating image
        if (clipboard != null && c.workingImage != null) {
            pushUndo();
            // Create floating selection immediately — handles appear right away.
            // Content is merged to the canvas only when commitFloat() is called.
            c.floatingImg = deepCopy(clipboard);
            c.floatRect   = new Rectangle(0, 0,
                    Math.min(clipboard.getWidth(),  c.workingImage.getWidth()),
                    Math.min(clipboard.getHeight(), c.workingImage.getHeight()));
            c.isDraggingFloat = false; c.floatDragAnchor = null;
            c.activeHandle = -1; c.scaleBaseRect = null; c.scaleDragStart = null;
            c.selectedAreas.clear();
            c.hasUnsavedChanges = true;
            updateTitle();
            c.canvasPanel.repaint();
        }
    }

    public Rectangle getActiveSelection() {
        CanvasInstance c = ci();
        if (!c.selectedAreas.isEmpty()) return c.selectedAreas.get(c.selectedAreas.size() - 1);
        if (c.isSelecting && c.selectionStart != null && c.selectionEnd != null) {
            int x = Math.min(c.selectionStart.x, c.selectionEnd.x);
            int y = Math.min(c.selectionStart.y, c.selectionEnd.y);
            int w = Math.abs(c.selectionEnd.x - c.selectionStart.x);
            int h = Math.abs(c.selectionEnd.y - c.selectionStart.y);
            return (w > 0 && h > 0) ? new Rectangle(x, y, w, h) : null;
        }
        return null;
    }

    private void copyToSystemClipboard(BufferedImage img) {
        if (img == null) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new TransferableImage(img), null);
        } catch (Exception ignored) {}
    }

    private BufferedImage pasteFromSystemClipboard() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null),
                        BufferedImage.TYPE_INT_ARGB);
                bi.createGraphics().drawImage(img, 0, 0, null);
                return bi;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static class TransferableImage implements Transferable {
        private final BufferedImage image;
        TransferableImage(BufferedImage img) { this.image = img; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{ DataFlavor.imageFlavor }; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
            return image;
        }
    }

    // =========================================================================
    // Secondary Canvas Window – SecondaryPanel inner class
    // =========================================================================
    private class SecondaryPanel extends JPanel {
        private static final int HANDLE_SIZE = 8;
        private int dragStartX, dragStartY;
        private int dragStartWinX, dragStartWinY, dragStartWinW, dragStartWinH;
        private String resizeEdge = null;  // "tl", "t", "tr", "l", "r", "bl", "b", "br", or null for drag

        SecondaryPanel() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStartX = e.getXOnScreen();
                    dragStartY = e.getYOnScreen();
                    dragStartWinX = secWin.getX();
                    dragStartWinY = secWin.getY();
                    dragStartWinW = secWin.getWidth();
                    dragStartWinH = secWin.getHeight();
                    resizeEdge = getResizeEdgeAt(e.getX(), e.getY());
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    String edge = getResizeEdgeAt(e.getX(), e.getY());
                    updateCursor(edge);
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (secWin == null || secFullscreen) return;
                    int dx = e.getXOnScreen() - dragStartX;
                    int dy = e.getYOnScreen() - dragStartY;

                    if (resizeEdge == null) {
                        // Window drag
                        secWin.setLocation(dragStartWinX + dx, dragStartWinY + dy);
                    } else {
                        // Window resize
                        int newX = dragStartWinX, newY = dragStartWinY;
                        int newW = dragStartWinW, newH = dragStartWinH;

                        if (resizeEdge.contains("l")) newX += dx;
                        if (resizeEdge.contains("r")) newW += dx;
                        if (resizeEdge.contains("t")) newY += dy;
                        if (resizeEdge.contains("b")) newH += dy;

                        newW = Math.max(200, newW);
                        newH = Math.max(150, newH);

                        secWin.setBounds(newX, newY, newW, newH);
                    }
                }
            });
        }

        private String getResizeEdgeAt(int x, int y) {
            int w = getWidth(), h = getHeight();
            boolean nearLeft   = x < HANDLE_SIZE;
            boolean nearRight  = x >= w - HANDLE_SIZE;
            boolean nearTop    = y < HANDLE_SIZE;
            boolean nearBottom = y >= h - HANDLE_SIZE;

            if (nearTop && nearLeft)   return "tl";
            if (nearTop && nearRight)  return "tr";
            if (nearBottom && nearLeft)  return "bl";
            if (nearBottom && nearRight) return "br";
            if (nearTop)    return "t";
            if (nearBottom) return "b";
            if (nearLeft)   return "l";
            if (nearRight)  return "r";
            return null;
        }

        private void updateCursor(String edge) {
            if (edge == null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            } else {
                int cursorType = switch (edge) {
                    case "tl", "br" -> Cursor.NW_RESIZE_CURSOR;
                    case "tr", "bl" -> Cursor.NE_RESIZE_CURSOR;
                    case "t", "b"   -> Cursor.N_RESIZE_CURSOR;
                    case "l", "r"   -> Cursor.W_RESIZE_CURSOR;
                    default -> Cursor.DEFAULT_CURSOR;
                };
                setCursor(Cursor.getPredefinedCursor(cursorType));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            // Determine source canvas based on display mode
            BufferedImage src;
            List<Layer> elements;
            int srcIdx = switch (SelectiveAlphaEditor.this.secCanvasMode) {
                case SHOW_CANVAS_I_ONLY -> 0;
                case SHOW_CANVAS_II_ONLY -> 1;
                case SHOW_ACTIVE_CANVAS -> SelectiveAlphaEditor.this.activeCanvasIndex;
            };
            CanvasInstance ci = canvases[srcIdx];
            if (ci.workingImage == null) return;
            src = ci.workingImage;

            if (secMode == PreviewMode.SNAPSHOT) {
                // In SNAPSHOT mode, render elements on top of snapshot base
                elements = ci.activeElements;
            } else {
                // In LIVE modes, render all elements
                elements = ci.activeElements;
            }

            // Scale-to-fit (maintain aspect ratio)
            int pw = getWidth(), ph = getHeight();
            int iw = src.getWidth(), ih = src.getHeight();
            double scale = Math.min((double) pw / iw, (double) ph / ih);
            int dw = (int)(iw * scale), dh = (int)(ih * scale);
            int ox = (pw - dw) / 2, oy = (ph - dh) / 2;

            // Checkerboard background - use same colors as main canvas
            int cell = 16;
            for (int cy = 0; cy < ph; cy += cell) {
                for (int cx = 0; cx < pw; cx += cell) {
                    g2.setColor(((cx/cell + cy/cell) % 2 == 0) ? canvasBg1 : canvasBg2);
                    g2.fillRect(cx, cy, cell, cell);
                }
            }

            // Draw main image
            g2.drawImage(src, ox, oy, dw, dh, null);

            // Draw elements (all modes with GameLoop rendering)
            for (Layer el : elements) {
                if (el instanceof ImageLayer il) {
                    int ex = ox + (int)Math.round(il.x() * scale);
                    int ey = oy + (int)Math.round(il.y() * scale);
                    int ew = (int)Math.round(il.width()  * scale);
                    int eh = (int)Math.round(il.height() * scale);
                    // Draw with rotation if needed
                    if (Math.abs(il.rotationAngle()) > 0.001) {
                        java.awt.geom.AffineTransform orig = g2.getTransform();
                        double cx = ex + ew / 2.0;
                        double cy = ey + eh / 2.0;
                        g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
                        g2.drawImage(il.image(), ex, ey, ew, eh, null);
                        g2.setTransform(orig);
                    } else {
                        g2.drawImage(il.image(), ex, ey, ew, eh, null);
                    }
                } else if (el instanceof TextLayer tl) {
                    // Render TextLayer with scaled font size
                    int tstyle = (tl.fontBold() ? java.awt.Font.BOLD : 0) | (tl.fontItalic() ? java.awt.Font.ITALIC : 0);
                    int scaledFontSize = Math.max(1, (int) Math.round(tl.fontSize() * scale));
                    java.awt.Font tfont = new java.awt.Font(tl.fontName(), tstyle, scaledFontSize);
                    g2.setFont(tfont);
                    g2.setColor(tl.fontColor());
                    java.awt.FontMetrics tfm = g2.getFontMetrics();
                    String[] tLines = tl.text().split("\n", -1);
                    int tpx = ox + (int)Math.round((tl.x() + TextLayer.TEXT_PADDING) * scale);
                    int tpy = oy + (int)Math.round((tl.y() + TextLayer.TEXT_PADDING) * scale);
                    for (int li = 0; li < tLines.length; li++) {
                        g2.drawString(tLines[li], tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
                    }
                } else if (el instanceof PathLayer pl && secMode == PreviewMode.LIVE_ALL_EDIT) {
                    // PathLayer only in LIVE_ALL_EDIT
                    renderPathLayerPreview(g2, pl, ox, oy, scale);
                }
            }

            // Element borders only in LIVE_ALL_EDIT
            if (secMode == PreviewMode.LIVE_ALL_EDIT) {
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.setColor(new Color(0, 180, 255));
                for (Layer el : elements) {
                    int ex = ox + (int)Math.round(el.x() * scale);
                    int ey = oy + (int)Math.round(el.y() * scale);
                    int ew = (int)Math.round(el.width()  * scale);
                    int eh = (int)Math.round(el.height() * scale);
                    g2.drawRect(ex, ey, ew, eh);
                }
            }
        }
    }

    private void renderPathLayerPreview(Graphics2D g2, PathLayer pl, int ox, int oy, double scale) {
        List<Point3D> pts = pl.points();
        if (pts.isEmpty()) return;
        g2.setColor(new Color(0, 200, 255));
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 1; i < pts.size(); i++) {
            int x1 = ox + (int)Math.round(pts.get(i-1).x * scale);
            int y1 = oy + (int)Math.round(pts.get(i-1).y * scale);
            int x2 = ox + (int)Math.round(pts.get(i).x   * scale);
            int y2 = oy + (int)Math.round(pts.get(i).y   * scale);
            g2.drawLine(x1, y1, x2, y2);
        }
        g2.setColor(Color.WHITE);
        for (Point3D p : pts) {
            int px = ox + (int)Math.round(p.x * scale) - 3;
            int py = oy + (int)Math.round(p.y * scale) - 3;
            g2.fillOval(px, py, 6, 6);
        }
    }

    // =========================================================================
    // Secondary Canvas Window – Control Methods
    // =========================================================================
    private void initSecondaryWindow() {
        secPanel = new SecondaryPanel();
        secPanel.setBackground(Color.BLACK);
        secWin = new JFrame("TransparencyTool - Canvas Preview");
        // Keep decorated so screen sharing / screen capture can detect the window
        secWin.setUndecorated(false);
        secWin.setResizable(true);
        secWin.setSize(640, 480);
        secWin.setLocationRelativeTo(this);
        secWin.getContentPane().add(secPanel);
        secWin.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        // GameLoop: 60 FPS (16ms per frame) for smooth rendering
        secTimer = new javax.swing.Timer(16, e -> secPanel.repaint());
    }

    private void toggleSecondaryWindow() {
        if (secWin == null) initSecondaryWindow();
        if (secWin.isVisible()) {
            secTimer.stop();
            secWin.setVisible(false);
        } else {
            if (secMode != PreviewMode.SNAPSHOT) secTimer.start();
            secWin.setVisible(true);
            secPanel.repaint();
        }
    }

    private void cyclePreviewMode() {
        if (secWin == null) initSecondaryWindow();
        secMode = switch (secMode) {
            case SNAPSHOT     -> PreviewMode.LIVE_ALL;
            case LIVE_ALL     -> PreviewMode.LIVE_ALL_EDIT;
            case LIVE_ALL_EDIT -> PreviewMode.SNAPSHOT;
        };
        if (secWin.isVisible()) {
            if (secMode == PreviewMode.SNAPSHOT) secTimer.stop();
            else                                 secTimer.start();
            secPanel.repaint();
        }
        ToastNotification.show(SelectiveAlphaEditor.this, "Preview: " + secMode.name());
    }

    private void refreshSnapshot() {
        if (secWin == null) initSecondaryWindow();
        // Composite: workingImage + all elements flattened
        BufferedImage src = ci().workingImage;
        if (src == null) return;
        secSnapshot = new BufferedImage(src.getWidth(), src.getHeight(),
                                        BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = secSnapshot.createGraphics();
        g2.drawImage(src, 0, 0, null);
        for (Layer el : ci().activeElements) {
            if (el instanceof ImageLayer il)
                g2.drawImage(il.image(), il.x(), il.y(), il.width(), il.height(), null);
        }
        g2.dispose();
        if (secWin != null && secWin.isVisible()) secPanel.repaint();
        ToastNotification.show(SelectiveAlphaEditor.this, "Snapshot updated");
    }

    private void toggleSecondaryFullscreen() {
        if (secWin == null) initSecondaryWindow();
        if (!secWin.isVisible()) {
            secWin.setVisible(true);
        }

        if (secFullscreen) {
            // Exit fullscreen
            secWin.setExtendedState(JFrame.NORMAL);
            secWin.setBounds(secOldX, secOldY, secOldW, secOldH);
            secFullscreen = false;
            ToastNotification.show(SelectiveAlphaEditor.this, "Fullscreen: OFF");
        } else {
            // Enter fullscreen
            secOldX = secWin.getX();
            secOldY = secWin.getY();
            secOldW = secWin.getWidth();
            secOldH = secWin.getHeight();
            secWin.setExtendedState(JFrame.MAXIMIZED_BOTH);
            secFullscreen = true;
            ToastNotification.show(SelectiveAlphaEditor.this, "Fullscreen: ON");
        }
    }

    private void cycleAlwaysOnTop() {
        if (secWin == null) initSecondaryWindow();
        if (!secWin.isVisible()) {
            secWin.setVisible(true);
        }

        secAlwaysOnTop = switch (secAlwaysOnTop) {
            case TO_FRONT -> AlwaysOnTopMode.NORMAL;
            case NORMAL -> AlwaysOnTopMode.TO_BACKGROUND;
            case TO_BACKGROUND -> AlwaysOnTopMode.TO_FRONT;
        };

        switch (secAlwaysOnTop) {
            case TO_FRONT:
                secWin.setAlwaysOnTop(true);
                ToastNotification.show(SelectiveAlphaEditor.this, "Window: Always on Top");
                break;
            case NORMAL:
                secWin.setAlwaysOnTop(false);
                ToastNotification.show(SelectiveAlphaEditor.this, "Window: Normal");
                break;
            case TO_BACKGROUND:
                secWin.setAlwaysOnTop(false);
                // Send to back by requesting focus for main window
                SelectiveAlphaEditor.this.toFront();
                SelectiveAlphaEditor.this.requestFocus();
                ToastNotification.show(SelectiveAlphaEditor.this, "Window: Behind Main");
                break;
        }
    }

    private void cycleCanvasDisplayMode() {
        if (secWin == null) initSecondaryWindow();
        if (!secWin.isVisible()) {
            secWin.setVisible(true);
        }

        secCanvasMode = switch (secCanvasMode) {
            case SHOW_CANVAS_I_ONLY -> CanvasDisplayMode.SHOW_CANVAS_II_ONLY;
            case SHOW_CANVAS_II_ONLY -> CanvasDisplayMode.SHOW_ACTIVE_CANVAS;
            case SHOW_ACTIVE_CANVAS -> CanvasDisplayMode.SHOW_CANVAS_I_ONLY;
        };

        String msg = switch (secCanvasMode) {
            case SHOW_CANVAS_I_ONLY -> "Display: Canvas I Only";
            case SHOW_CANVAS_II_ONLY -> "Display: Canvas II Only";
            case SHOW_ACTIVE_CANVAS -> "Display: Active Canvas";
        };
        ToastNotification.show(SelectiveAlphaEditor.this, msg);
        if (secPanel != null) secPanel.repaint();
    }

    private void applySecondaryWindowToCanvas() {
        if (secWin == null || !secWin.isVisible()) {
            ToastNotification.show(SelectiveAlphaEditor.this, "Secondary window not open");
            return;
        }

        // Get the image to apply (from the currently shown canvas)
        BufferedImage imageToApply = null;

        if (secMode == PreviewMode.SNAPSHOT) {
            if (secSnapshot == null) {
                ToastNotification.show(SelectiveAlphaEditor.this, "No snapshot available");
                return;
            }
            imageToApply = deepCopy(secSnapshot);
        } else {
            // LIVE_ALL or LIVE_ALL_EDIT: get the ACTIVE canvas's working image
            CanvasInstance srcCi = ci();  // Active canvas
            if (srcCi.workingImage == null) {
                ToastNotification.show(SelectiveAlphaEditor.this, "Active canvas has no image");
                return;
            }
            imageToApply = deepCopy(srcCi.workingImage);
        }

        // Apply to Canvas II (index 1)
        CanvasInstance targetCi = ci(1);
        targetCi.workingImage = normalizeImage(imageToApply);
        targetCi.undoStack.clear();
        targetCi.redoStack.clear();
        targetCi.activeElements = new ArrayList<>();
        targetCi.selectedElements.clear();
        targetCi.zoom = 1.0;
        targetCi.userHasManuallyZoomed = false;

        // Update Canvas II display
        if (targetCi.canvasPanel != null) {
            targetCi.canvasPanel.repaint();
        }
        if (elementLayerPanel2 != null) {
            refreshElementPanel();
        }

        // Switch to Canvas II to see the result
        activeCanvasIndex = 1;
        secondCanvasBtn.setSelected(true);
        secondCanvasBtn.setEnabled(true);
        updateLayoutVisibility();
        centerCanvas(1);

        ToastNotification.show(SelectiveAlphaEditor.this, "Image applied to Canvas II");
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
        int CTRL_ALT   = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL),        "copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL_SHIFT),  "copyOutside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL),        "cut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL_SHIFT),  "cutOutside");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, CTRL),        "paste");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, CTRL),                        "selectAll");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "selectAllElements");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL),                       "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, CTRL),        "redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL),                           "save");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_ALT),                      "saveOriginal");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL | InputEvent.SHIFT_DOWN_MASK),      "saveBurnedCopy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_ALT | InputEvent.SHIFT_DOWN_MASK), "saveBurnedOriginal");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0),           "rotateCW");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK), "rotateCCW");
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
                CanvasInstance c = ci();
                c.selectedElements.clear();
                c.selectedElements.addAll(c.activeElements);
                if (c.canvasPanel != null) c.canvasPanel.repaint();
                refreshElementPanel();
            }
        }});
        am.put("selectAllElements", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            CanvasInstance c = ci();
            c.selectedElements.clear();
            c.selectedElements.addAll(c.activeElements);
            if (c.canvasPanel != null) c.canvasPanel.repaint();
            refreshElementPanel();
        }});
        am.put("undo",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            CanvasInstance c = ci();
            if (c.floatingImg != null) cancelFloat(); else doUndo();
        }});
        am.put("redo",        new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doRedo(); } });
        am.put("save",         new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveImageSilent(); } });
        am.put("saveOriginal", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveImageToOriginal(); } });
        am.put("saveBurnedCopy",      new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveBurnedElementsCopy(); } });
        am.put("saveBurnedOriginal",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveBurnedElementsOriginal(); } });
        am.put("rotateCW",     new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doRotate(90.0); } });
        am.put("rotateCCW",    new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doRotate(-90.0); } });
        am.put("escape",      new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            CanvasInstance c = ci();
            if (c.floatingImg != null) { cancelFloat(); }
            else if (!c.selectedElements.isEmpty()) { c.selectedElements.clear(); c.canvasPanel.repaint(); }
            else { c.selectedAreas.clear(); c.isSelecting = false; c.selectionStart = null; c.selectionEnd = null; c.canvasPanel.repaint(); }
        }});
        am.put("deleteInside", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            CanvasInstance c = ci();
            // NEW: PathLayer support
            if (!c.selectedElements.isEmpty() && c.selectedElements.get(0) instanceof PathLayer pl) {
                pushUndo();
                PaintEngine.clearPolygon(c.workingImage, pl.absXPoints(), pl.absYPoints());
                markDirty();
            } else if (!c.selectedElements.isEmpty()) {
                deleteSelectedElements();
            } else if (!c.selectedAreas.isEmpty() && c.workingImage != null) {
                pushUndo();
                for (Rectangle r : c.selectedAreas) PaintEngine.clearRegion(c.workingImage, r);
                c.selectedAreas.clear();
                c.isSelecting = false; c.selectionStart = null; c.selectionEnd = null;
                markDirty();
            }
        }});
        am.put("deleteOutside", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            CanvasInstance c = ci();
            if (c.workingImage == null) return;
            Rectangle sel = getActiveSelection();
            if (sel != null) {
                pushUndo();
                PaintEngine.clearOutside(c.workingImage, sel);
                markDirty();
            }
        }});
        am.put("mergeElement", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (!ci().selectedElements.isEmpty()) mergeSelectedElements();
        }});

        // Global F1/F2/F3/F4/F5/F6 key dispatcher for secondary window control
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_F1 -> { toggleSecondaryWindow();      return true; }
                case KeyEvent.VK_F2 -> { cyclePreviewMode();           return true; }
                case KeyEvent.VK_F3 -> { refreshSnapshot();            return true; }
                case KeyEvent.VK_F4 -> { toggleSecondaryFullscreen();  return true; }
                case KeyEvent.VK_F5 -> { cycleAlwaysOnTop();           return true; }
                case KeyEvent.VK_F6 -> { applySecondaryWindowToCanvas(); return true; }
                case KeyEvent.VK_F7 -> { cycleCanvasDisplayMode();     return true; }
            }
            return false;
        });
    }

    // =========================================================================
    // New helper methods
    // =========================================================================

    /** Centers the viewport over the canvas (called after zoom or sidebar toggle). */
    public void centerCanvas() {
        centerCanvas(activeCanvasIndex);
    }

    /**
     * Centers the viewport horizontally only (X-axis) for indexed canvas.
     * Called when the user has a custom zoom and the toolbar or fullscreen state changes.
     */
    private void centerCanvasX(int idx) {
        CanvasInstance c = ci(idx);
        if (c.scrollPane == null || c.workingImage == null) return;
        SwingUtilities.invokeLater(() -> {
            c.canvasWrapper.revalidate();
            c.canvasWrapper.validate();
            JViewport vp = c.scrollPane.getViewport();
            Dimension vpSz = vp.getSize();
            int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
            int viewX = Math.max(0, (cw - vpSz.width) / 2);
            int viewY = vp.getViewPosition().y; // keep vertical position unchanged
            vp.setViewPosition(new Point(viewX, viewY));
        });
    }

    /**
     * Centers the viewport horizontally only (X-axis) for active canvas.
     */
    private void centerCanvasX() {
        centerCanvasX(activeCanvasIndex);
    }

    /**
     * Opens a tiny popup where the user can type a zoom percentage directly.
     * Activated by double-clicking the zoom label.
     */
    private void showZoomInput() {
        JTextField tf = new JTextField(String.valueOf(Math.round(ci().zoom * 100)), 5);
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
        if (bookModeBtn.isSelected()) { doNewBookSheet(); return; }

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
                CanvasInstance c = ci();
                if (c.sourceFile != null) saveCurrentState();
                c.workingImage      = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                c.originalImage     = deepCopy(c.workingImage);
                c.activeElements    = new ArrayList<>();
                c.selectedElements.clear();
                c.undoStack.clear(); c.redoStack.clear();
                c.selectedAreas.clear();
                c.floatingImg = null; c.floatRect = null;

                // Create and save new file
                File saveDir = c.lastIndexedDir != null ? c.lastIndexedDir : new File(System.getProperty("user.home"));
                int counter = 1;
                File newFile;
                do {
                    newFile = new File(saveDir, "Untitled_" + counter + ".png");
                    counter++;
                } while (newFile.exists());

                try {
                    ImageIO.write(c.workingImage, "PNG", newFile);
                    c.sourceFile = newFile;
                    c.hasUnsavedChanges = false;
                    dirtyFiles.remove(c.sourceFile);

                    // Update tile gallery
                    if (!c.directoryImages.contains(newFile)) {
                        c.directoryImages.add(newFile);
                        c.tileGallery.addFiles(Arrays.asList(newFile));
                    }
                    c.tileGallery.setActiveFile(newFile);
                    c.currentImageIndex = c.directoryImages.indexOf(newFile);
                } catch (IOException ex) {
                    showErrorDialog("Speicherfehler", "Neue Bitmap konnte nicht gespeichert werden:\n" + ex.getMessage());
                    return;
                }

                swapToImageView(activeCanvasIndex);
                SwingUtilities.invokeLater(() -> fitToViewport(activeCanvasIndex));
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

    /** Creates a new sheet with paper format, orientation, and margin guides. */
    private void doNewBookSheet() {
        // Show paper format dialog
        PaperFormat.Format[] formats = PaperFormat.Format.values();
        String[] formatLabels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            formatLabels[i] = formats[i].toString();
        }
        JComboBox<String> formatCombo = new JComboBox<>(formatLabels);
        formatCombo.setSelectedIndex(4); // A4 as default
        formatCombo.setBackground(AppColors.BTN_BG);
        formatCombo.setForeground(AppColors.TEXT);

        // Create orientation combobox
        String[] orientations = {"Hochformat", "Querformat"};
        JComboBox<String> orientCombo = new JComboBox<>(orientations);
        orientCombo.setSelectedIndex(0); // Portrait as default
        orientCombo.setBackground(AppColors.BTN_BG);
        orientCombo.setForeground(AppColors.TEXT);

        // Create margins checkbox
        JCheckBox marginsCheckBox = new JCheckBox("Mit Rändern", true);
        marginsCheckBox.setOpaque(false);
        marginsCheckBox.setForeground(AppColors.TEXT);

        // Create form grid
        JPanel grid = new JPanel(new GridLayout(3, 2, 6, 4));
        grid.setOpaque(false);

        JLabel formatLabel = new JLabel("Format:");
        formatLabel.setForeground(AppColors.TEXT);
        grid.add(formatLabel);
        grid.add(formatCombo);

        JLabel orientLabel = new JLabel("Ausrichtung:");
        orientLabel.setForeground(AppColors.TEXT);
        grid.add(orientLabel);
        grid.add(orientCombo);

        JLabel marginsLabel = new JLabel("");
        marginsLabel.setForeground(AppColors.TEXT);
        grid.add(marginsLabel);
        grid.add(marginsCheckBox);

        // Create dialog
        JDialog dialog = createBaseDialog("Neues Blatt", 320, 240);
        JPanel content = centeredColumnPanel(16, 20, 12);
        content.add(grid);
        content.add(Box.createVerticalStrut(12));

        // Create buttons
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("Erstellen", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        okBtn.setForeground(Color.WHITE);

        okBtn.addActionListener(e -> {
            PaperFormat.Format selectedFormat = formats[formatCombo.getSelectedIndex()];
            boolean landscape = orientCombo.getSelectedIndex() == 1;
            boolean withMargins = marginsCheckBox.isSelected();

            // Convert mm → pixels at 96 DPI (1 mm = 96/25.4 px ≈ 3.7795 px)
            final double PX_PER_MM = 96.0 / 25.4;
            int wPx = (int) Math.round((landscape ? selectedFormat.getWidthLandscape() : selectedFormat.getWidthPortrait()) * PX_PER_MM);
            int hPx = (int) Math.round((landscape ? selectedFormat.getHeightLandscape() : selectedFormat.getHeightPortrait()) * PX_PER_MM);

            // Build the Sheet image
            BufferedImage img = new BufferedImage(wPx, hPx, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, wPx, hPx);

            if (withMargins) {
                int mTop    = (int) Math.round(selectedFormat.getMarginTop()    * PX_PER_MM);
                int mBottom = (int) Math.round(selectedFormat.getMarginBottom() * PX_PER_MM);
                int mLeft   = (int) Math.round(selectedFormat.getMarginInner()  * PX_PER_MM);
                int mRight  = (int) Math.round(selectedFormat.getMarginOuter()  * PX_PER_MM);
                g2.setColor(new Color(0, 120, 220, 180)); // semi-transparent blue
                float[] dash = { 6f, 4f };
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
                g2.drawRect(mLeft, mTop, wPx - mLeft - mRight, hPx - mTop - mBottom);
            }
            g2.dispose();

            // Load into canvas (same pattern as doNewBitmap)
            CanvasInstance c = ci();
            c.workingImage  = img;
            c.originalImage = deepCopy(img);
            c.activeElements = new ArrayList<>();
            c.selectedElements.clear();
            c.undoStack.clear(); c.redoStack.clear();
            c.selectedAreas.clear();
            c.floatingImg = null; c.floatRect = null;

            swapToImageView(activeCanvasIndex);
            SwingUtilities.invokeLater(() -> fitToViewport(activeCanvasIndex));
            updateTitle(); updateStatus();
            setBottomButtonsEnabled(true);
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        row.add(okBtn);
        row.add(cancelBtn);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    /** Lets the user choose one or both checkerboard background colors. */
    private void showCanvasBgDialog() {
        JPanel preview = new JPanel() {
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
            if (c != null) { canvasBg1 = c; preview.repaint(); ci().canvasPanel.repaint(); }
        });
        btn2.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe 2", canvasBg2);
            if (c != null) { canvasBg2 = c; preview.repaint(); ci().canvasPanel.repaint(); }
        });
        btnBoth.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe", canvasBg1);
            if (c != null) { canvasBg1 = c; canvasBg2 = c; preview.repaint(); ci().canvasPanel.repaint(); }
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

    private void toggleQuickBG() {
        if (canvasBg1Backup == null) {
            // Hide BG Color 1 by setting it to match BG Color 2
            canvasBg1Backup = canvasBg1;
            canvasBg1 = canvasBg2;
        } else {
            // Restore BG Color 1
            canvasBg1 = canvasBg1Backup;
            canvasBg1Backup = null;
        }
        if (ci() != null && ci().canvasPanel != null) ci().canvasPanel.repaint();
        if (secPanel != null) secPanel.repaint();  // Also repaint secondary window if visible
    }

    // =========================================================================
    // UI state helpers
    // =========================================================================
    public void swapToImageView() {
        swapToImageView(activeCanvasIndex);
    }

    private void updateNavigationButtons() {
        CanvasInstance c = ci();
        c.prevNavButton.setEnabled(c.currentImageIndex > 0);
        c.nextNavButton.setEnabled(c.currentImageIndex < c.directoryImages.size() - 1);
    }

    public void updateTitle() {
        CanvasInstance c = ci();
        if (c.sourceFile == null) { setTitle("Selective Alpha Editor"); return; }
        String dirty = c.hasUnsavedChanges ? " •" : "";
        String fileName = c.sourceFile.getName();
        String size = c.workingImage != null ? c.workingImage.getWidth() + "x" + c.workingImage.getHeight() + "px" : "?x?";
        String imageCount = (c.currentImageIndex + 1) + "/" + c.directoryImages.size();
        setTitle("Selective Alpha Editor  |  " + fileName + "  |  " + size + "  |  " + imageCount + dirty);
    }

    public void updateStatus() {
        CanvasInstance c = ci();
        if (c.sourceFile == null) { statusLabel.setText("Keine Datei geladen"); return; }
        statusLabel.setText(c.sourceFile.getName()
                + "   |   " + (c.currentImageIndex + 1) + " / " + c.directoryImages.size()
                + "   |   " + c.workingImage.getWidth() + " × " + c.workingImage.getHeight() + " px");
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
    private int showUnsavedChangesDialog() {
        if (!ci().hasUnsavedChanges) return 1;
        final int[] result = { 2 };
        JDialog dialog = createBaseDialog("Ungespeicherte Änderungen", 420, 310);
        JPanel  content = centeredColumnPanel(20, 28, 16);
        content.add(styledLabel("⚠", 30, AppColors.WARNING, Font.PLAIN));
        content.add(Box.createVerticalStrut(10));
        content.add(htmlLabel("Das Bild hat ungespeicherte Änderungen.<br>Was möchtest du tun?", AppColors.TEXT, 13));
        content.add(Box.createVerticalStrut(18));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        JButton sBtn = UIComponentFactory.buildButton("Speichern",  AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
        JButton dBtn = UIComponentFactory.buildButton("Verwerfen",  AppColors.DANGER,  AppColors.DANGER_HOVER);
        JButton cBtn = UIComponentFactory.buildButton("Abbrechen",  AppColors.BTN_BG,  AppColors.BTN_HOVER);
        sBtn.setForeground(Color.WHITE); dBtn.setForeground(Color.WHITE);
        sBtn.addActionListener(e -> { result[0] = 0; dialog.dispose(); });
        dBtn.addActionListener(e -> { result[0] = 1; dialog.dispose(); });
        cBtn.addActionListener(e -> { result[0] = 2; dialog.dispose(); });
        row.add(sBtn); row.add(dBtn); row.add(cBtn);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
        return result[0];
    }

    /** Show quick open dialog with recent projects and browse option (loads into active canvas). */
    private void showQuickOpenDialog() {
        showQuickOpenDialog(activeCanvasIndex);
    }

    /** Show quick open dialog with recent projects and browse option. */
    private void showQuickOpenDialog(int canvasIdx) {
        try {
            java.util.Map<String, java.util.List<String>> recent = LastProjectsManager.loadAll();

            JDialog dialog = new JDialog(this, "Schnellauswahl", true);
            dialog.setSize(400, 350);
            dialog.setLocationRelativeTo(this);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            JPanel content = UIComponentFactory.centeredColumnPanel(12, 16, 8);

            JLabel titleLbl = new JLabel("Zuletzt verwendet:");
            titleLbl.setForeground(AppColors.TEXT);
            titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            content.add(titleLbl);

            DefaultListModel<String> listModel = new DefaultListModel<>();
            for (java.util.List<String> files : recent.values()) {
                for (String f : files) {
                    listModel.addElement(new File(f).getName() + " (" + f + ")");
                }
            }

            JList<String> list = new JList<>(listModel);
            list.setBackground(AppColors.BTN_BG);
            list.setForeground(AppColors.TEXT);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
            content.add(scroll);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            btnPanel.setOpaque(false);
            JButton openBtn = UIComponentFactory.buildButton("Öffnen", AppColors.ACCENT, AppColors.ACCENT_HOVER);
            JButton browseBtn = UIComponentFactory.buildButton("Durchsuchen", AppColors.BTN_BG, AppColors.BTN_HOVER);
            JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);

            openBtn.setForeground(Color.WHITE);
            browseBtn.setForeground(AppColors.TEXT);
            cancelBtn.setForeground(AppColors.TEXT);

            openBtn.addActionListener(e -> {
                int idx = list.getSelectedIndex();
                if (idx >= 0) {
                    String selected = listModel.getElementAt(idx);
                    // Extract full path from "name (path)"
                    int parenIdx = selected.lastIndexOf('(');
                    if (parenIdx > 0) {
                        String fullPath = selected.substring(parenIdx + 1, selected.length() - 1);
                        loadFile(new File(fullPath), canvasIdx);
                        dialog.dispose();
                    }
                }
            });

            browseBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Images", "png", "jpg", "jpeg", "gif", "bmp"));
                int result = chooser.showOpenDialog(dialog);
                if (result == JFileChooser.APPROVE_OPTION) {
                    loadFile(chooser.getSelectedFile(), canvasIdx);
                    dialog.dispose();
                }
            });

            cancelBtn.addActionListener(e -> dialog.dispose());

            btnPanel.add(openBtn);
            btnPanel.add(browseBtn);
            btnPanel.add(cancelBtn);
            content.add(btnPanel);

            dialog.add(content);
            dialog.setVisible(true);
        } catch (Exception ex) {
            showErrorDialog("Fehler", "Schnellauswahl konnte nicht geöffnet werden:\n" + ex.getMessage());
        }
    }

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

    /** Screen-space rectangle for a layer, accounting for current zoom (uses active canvas zoom). */
    public Rectangle elemRectScreen(Layer el) {
        return elemRectScreen(el, ci().zoom);
    }

    /** Screen-space rectangle for a layer, with explicit zoom value. */
    public Rectangle elemRectScreen(Layer el, double zoom) {
        double z = zoom;
        if (el instanceof PathLayer pl && !pl.points().isEmpty()) {
            // PathLayer: compute padded visual bounding box from actual points
            // (matches the frameRect drawn in CanvasPanel.paintComponent)
            double fMinX = Double.MAX_VALUE, fMaxX = -Double.MAX_VALUE;
            double fMinY = Double.MAX_VALUE, fMaxY = -Double.MAX_VALUE;
            for (Point3D p : pl.points()) {
                if (p.x < fMinX) fMinX = p.x;
                if (p.x > fMaxX) fMaxX = p.x;
                if (p.y < fMinY) fMinY = p.y;
                if (p.y > fMaxY) fMaxY = p.y;
            }
            int fx = (int) Math.round((pl.x() + fMinX - 8) * z);
            int fy = (int) Math.round((pl.y() + fMinY - 8) * z);
            int fw = (int) Math.round((fMaxX - fMinX + 16) * z);
            int fh = (int) Math.round((fMaxY - fMinY + 16) * z);
            return new Rectangle(fx, fy, Math.max(1, fw), Math.max(1, fh));
        }
        return new Rectangle(
            (int) Math.round(el.x()      * z),
            (int) Math.round(el.y()      * z),
            (int) Math.round(el.width()  * z),
            (int) Math.round(el.height() * z));
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
        // Set transparent background - preserve alpha channel
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setComposite(AlphaComposite.SrcOver);
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
        CanvasInstance c = ci();
        Rectangle sel = getActiveSelection();
        if (sel != null && c.workingImage != null) {
            src = PaintEngine.cropRegion(c.workingImage, sel);
        } else if (clipboard != null) {
            src = deepCopy(clipboard);
        }
        if (src == null) { showInfoDialog("Kein Inhalt", "Nichts zum Einfügen als Element."); return; }

        int ex = sel != null ? sel.x : 0;
        int ey = sel != null ? sel.y : 0;
        Layer el = new ImageLayer(c.nextElementId++, src, ex, ey, src.getWidth(), src.getHeight());
        c.activeElements.add(el);
        c.selectedElements.clear();
        c.selectedElements.add(el);
        c.selectedAreas.clear();
        markDirty();
        refreshElementPanel();
    }

    /** Merges all selected layers onto the canvas and removes them from the layer list. */
    private void mergeSelectedElements() {
        CanvasInstance c = ci();
        if (c.selectedElements.isEmpty() || c.workingImage == null) return;
        pushUndo();
        for (Layer el : new ArrayList<>(c.selectedElements)) {
            if (el instanceof ImageLayer il) {
                BufferedImage scaled = PaintEngine.scale(il.image(), Math.max(1, el.width()), Math.max(1, el.height()));
                PaintEngine.pasteRegion(c.workingImage, scaled, new Point(el.x(), el.y()));
            } else if (el instanceof TextLayer tl) {
                BufferedImage rendered = renderTextLayerToImage(tl);
                PaintEngine.pasteRegion(c.workingImage, rendered, new Point(el.x(), el.y()));
            }
            c.activeElements.removeIf(e -> e.id() == el.id());
        }
        c.selectedElements.clear();
        markDirty();
        refreshElementPanel();
    }

    /** Deletes all selected layers without merging to canvas. */
    private void deleteSelectedElements() {
        CanvasInstance c = ci();
        if (c.selectedElements.isEmpty()) return;
        for (Layer el : c.selectedElements) c.activeElements.removeIf(e -> e.id() == el.id());
        c.selectedElements.clear();
        markDirty();
        refreshElementPanel();
    }

    /** Toggles a layer in/out of the multi-selection. New primary is put at index 0. */
    private void doToggleElementSelection(Layer el) {
        CanvasInstance c = ci();
        for (int i = 0; i < c.selectedElements.size(); i++) {
            if (c.selectedElements.get(i).id() == el.id()) {
                c.selectedElements.remove(i);
                refreshElementPanel();
                return;
            }
        }
        c.selectedElements.add(0, el);
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

    private void copyInto(BufferedImage src, BufferedImage dst) {
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
    }


    // =========================================================================
    // RulerCallbacks Interface (required methods)
    // =========================================================================
    @Override public BufferedImage getWorkingImage() { return ci().workingImage; }
    @Override public JScrollPane getScrollPane() { return ci().scrollPane; }
    @Override public JPanel getCanvasPanel() { return ci().canvasPanel; }
    @Override public double getZoom() { return ci().zoom; }
    @Override public RulerUnit getRulerUnit() { return rulerUnit; }

    // =========================================================================
    // Public API for EditorDialogs
    // =========================================================================
    public void setWorkingImage(BufferedImage img) { ci().workingImage = img; }
    public BufferedImage getOriginalImage() { return ci().originalImage; }
    public void setOriginalImage(BufferedImage img) { ci().originalImage = img; }
    public File getSourceFile() { return ci().sourceFile; }
    public void setSourceFile(File f) { ci().sourceFile = f; }
    public JPanel getCanvasWrapper() { return ci().canvasWrapper; }
    public JLabel getZoomLabel() { return zoomLabel; }
    public Color getCanvasBg1() { return canvasBg1; }
    public Color getCanvasBg2() { return canvasBg2; }
    public void setCanvasBg1(Color c) { canvasBg1 = c; }
    public void setCanvasBg2(Color c) { canvasBg2 = c; }
}
