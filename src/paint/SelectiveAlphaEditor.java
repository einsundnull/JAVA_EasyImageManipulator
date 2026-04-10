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
import java.awt.event.MouseWheelEvent;
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
import javax.swing.JScrollBar;
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
public class SelectiveAlphaEditor extends JFrame {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
    private static final int    MAX_UNDO  = 50;
    private static final double ZOOM_MIN  = 0.05;
    private static final double ZOOM_MAX  = 32.0;
    private static final double ZOOM_STEP = 0.10;

    private static final int GRID_CELL    = 16;   // image-space pixels per grid cell
    private static final int RULER_THICK  = 20;   // pixels wide/tall for ruler strip
    private static final double SCREEN_DPI = 96.0;
    private static final double ZOOM_WHEEL = 0.06; // per notch for mouse-wheel zoom

    private static final int TOPBAR_BTN_W      = 50;
    private static final int TOPBAR_BTN_H      = 50;
    private static final int TOPBAR_ZOOM_BTN_W = 50;
    private static final int TOPBAR_ZOOM_BTN_H = 50;

    // ── Ruler unit ────────────────────────────────────────────────────────────
    private enum RulerUnit {
        PX, MM, CM, INCH;
        /** How many image pixels equal one unit (at given image DPI). */
        double pxPerUnit() {
            return switch (this) {
                case PX   -> 1.0;
                case MM   -> SCREEN_DPI / 25.4;
                case CM   -> SCREEN_DPI / 2.54;
                case INCH -> SCREEN_DPI;
            };
        }
        String label() { return switch (this) { case PX -> "px"; case MM -> "mm"; case CM -> "cm"; case INCH -> "in"; }; }
    }

    // ── Per-file canvas state (cached while switching images) ─────────────────
    private static final class CanvasState {
        BufferedImage               image;
        final ArrayDeque<BufferedImage> undoStack = new ArrayDeque<>();
        final ArrayDeque<BufferedImage> redoStack = new ArrayDeque<>();
        final List<Element>         elements  = new ArrayList<>();
        CanvasState(BufferedImage img) { this.image = img; }
    }

    // ── Application mode ──────────────────────────────────────────────────────
    public enum AppMode { ALPHA_EDITOR, PAINT }

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

    // ── Element layers ────────────────────────────────────────────────────────
    /** Elements attached to the *current* image (alias into fileStateCache).    */
    private List<Element>  activeElements  = new ArrayList<>();
    private int            nextElementId   = 1;
    /** Selected element for move / resize interaction.                           */
    private Element        selectedElement = null;
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
        SwingUtilities.invokeLater(SelectiveAlphaEditor::new);
    }

    // =========================================================================
    // Constructors
    // =========================================================================
    public SelectiveAlphaEditor() { initializeUI(); }

    public SelectiveAlphaEditor(File imageFile, boolean floodfillMode) {
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
        setLocationRelativeTo(null);

        setupKeyBindings();
        setVisible(true);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        left.setOpaque(false);
        modeLabel = new JLabel("Modus: Selective Alpha");
        modeLabel.setForeground(AppColors.TEXT_MUTED);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JButton toggleBtn = buildButton("Alpha-Modus wechseln", AppColors.BTN_BG, AppColors.BTN_HOVER);
        toggleBtn.addActionListener(e -> toggleAlphaMode());

        filmstripBtn = buildModeToggleBtn("\uD83C\uDEDE", "Filmstreifen ein-/ausblenden");
        filmstripBtn.setSelected(true);
        filmstripBtn.addActionListener(e -> {
            tileGallery.setVisible(filmstripBtn.isSelected());
            if (galleryWrapper != null) {
                galleryWrapper.revalidate();
                galleryWrapper.repaint();
            }
            // centerCanvas() already defers via invokeLater, so the layout triggered
            // by revalidate() will have settled before the view position is set.
            centerCanvas();
        });

        left.add(filmstripBtn);
        left.add(modeLabel);
//        left.add(toggleBtn);
        bar.add(left, BorderLayout.WEST);
 
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        right.setOpaque(false);

        canvasModeBtn = buildModeToggleBtn("⬜", "Canvas (Funktion folgt)");
        canvasModeBtn.addActionListener(e -> {
            canvasModeBtn.setSelected(false);
            showInfoDialog("Canvas-Modus", "Diese Funktion wird in einer späteren Version implementiert.");
        });

        paintModeBtn = buildModeToggleBtn("🖌", "Paint-Modus aktivieren / deaktivieren");
        paintModeBtn.addActionListener(e -> togglePaintMode());

        JButton zoomOutBtn   = buildButton("−",   AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomInBtn    = buildButton("+",   AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomResetBtn = buildButton("1:1", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomFitBtn   = buildButton("Fit", AppColors.BTN_BG, AppColors.BTN_HOVER);
        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(AppColors.TEXT_MUTED);
        zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        zoomLabel.setPreferredSize(new Dimension(52, 20));
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

        JButton newBitmapBtn = buildButton("Neu", AppColors.BTN_BG, AppColors.BTN_HOVER);
        newBitmapBtn.setToolTipText("Neue leere Bitmap erstellen");
        newBitmapBtn.addActionListener(e -> doNewBitmap());
        newBitmapBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));

        JButton bgColorBtn = buildButton("BG", AppColors.BTN_BG, AppColors.BTN_HOVER);
        bgColorBtn.setToolTipText("Canvas-Hintergrundfarbe einstellen");
        bgColorBtn.addActionListener(e -> showCanvasBgDialog());
        bgColorBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));

        right.add(canvasModeBtn);
        right.add(paintModeBtn);
        right.add(Box.createHorizontalStrut(8));
        right.add(newBitmapBtn);
        right.add(bgColorBtn);
        right.add(Box.createHorizontalStrut(8));
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
        canvasPanel = new CanvasPanel();

        // Wrapper that centres canvasPanel when image < viewport
        canvasWrapper = new JPanel(null) {
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
        hRuler     = new HRulerPanel();
        vRuler     = new VRulerPanel();
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

        prevNavButton = buildNavButton("‹");
        nextNavButton = buildNavButton("›");
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

        galleryWrapper = new JPanel(new BorderLayout());
        galleryWrapper.setBackground(AppColors.BG_DARK);
        galleryWrapper.add(tileGallery, BorderLayout.WEST);
        galleryWrapper.add(layeredPane, BorderLayout.CENTER);
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

    // ── Bottom bar ────────────────────────────────────────────────────────────
    private JPanel buildBottomBar() {
        paintToolbar = new PaintToolbar(this, buildPaintCallbacks());

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(AppColors.BG_PANEL);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionPanel.setOpaque(false);
        actionPanel.setName("actionPanel");

        applyButton = buildButton("Auswahl anwenden", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        applyButton.setForeground(Color.WHITE);
        applyButton.addActionListener(e -> applySelectionsToAlpha());
        applyButton.setEnabled(false);

        clearSelectionsButton = buildButton("Auswahl löschen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        clearSelectionsButton.addActionListener(e -> clearSelections());
        clearSelectionsButton.setEnabled(false);

        JButton resetButton = buildButton("Zurücksetzen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        resetButton.setName("resetButton");
        resetButton.addActionListener(e -> resetImage());
        resetButton.setEnabled(false);

        JButton saveButton = buildButton("Speichern", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
        saveButton.setName("saveButton");
        saveButton.setForeground(Color.WHITE);
        saveButton.addActionListener(e -> saveImage());
        saveButton.setEnabled(false);

        JButton insertElemBtn = buildButton("Als Element einfügen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        insertElemBtn.setToolTipText("Auswahl als nicht-destruktiven Layer einfügen (ENTER=zusammenführen, DEL=löschen)");
        insertElemBtn.addActionListener(e -> insertSelectionAsElement());

        JButton mergeElemBtn = buildButton("Element zusammenführen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        mergeElemBtn.setToolTipText("Ausgewähltes Element auf Canvas rendern (ENTER)");
        mergeElemBtn.addActionListener(e -> { if (selectedElement != null) mergeElementToCanvas(selectedElement); });

        actionPanel.add(applyButton);
        actionPanel.add(clearSelectionsButton);
        actionPanel.add(Box.createHorizontalStrut(8));
        actionPanel.add(insertElemBtn);
        actionPanel.add(mergeElemBtn);
        actionPanel.add(Box.createHorizontalStrut(8));
        actionPanel.add(resetButton);
        actionPanel.add(saveButton);
        statusBar.add(actionPanel, BorderLayout.WEST);

        statusLabel = new JLabel("Keine Datei geladen");
        statusLabel.setForeground(AppColors.TEXT_MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        statusBar.add(statusLabel, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_DARK);
        wrapper.add(paintToolbar, BorderLayout.NORTH);
        wrapper.add(statusBar,    BorderLayout.SOUTH);
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
        floatingImg = null; floatRect = null;
        isDraggingFloat = false; floatDragAnchor = null;
        activeHandle = -1; scaleBaseRect = null; scaleDragStart = null;
        selectedElement = null; draggingElement = false; elemDragAnchor = null;
        elemActiveHandle = -1; elemScaleBase = null; elemScaleStart = null;

        indexDirectory(file);
        swapToImageView();
        SwingUtilities.invokeLater(() -> { fitToViewport(); centerCanvas(); });
        updateNavigationButtons();
        updateTitle();
        updateStatus();
        setBottomButtonsEnabled(true);
    }

    /** Saves the current workingImage + stacks + elements back into the file cache. */
    private void saveCurrentState() {
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
        File dir = file.getParentFile();
        if (dir == null) return;
        boolean sameDir = dir.equals(lastIndexedDir);
        if (!sameDir) {
            File[] files = dir.listFiles(f -> f.isFile() && isSupportedFile(f));
            if (files == null) return;
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            directoryImages = new ArrayList<>(Arrays.asList(files));
            lastIndexedDir  = dir;
            tileGallery.setFiles(directoryImages, file);
        } else {
            tileGallery.setActiveFile(file);
        }
        currentImageIndex = directoryImages.indexOf(file);
    }

    private void navigateImage(int dir) {
        if (directoryImages.isEmpty()) return;
        int ni = currentImageIndex + dir;
        if (ni < 0 || ni >= directoryImages.size()) return;
        // No unsaved-changes dialog – state is kept in cache (shown as red border)
        currentImageIndex = ni;
        loadFile(directoryImages.get(currentImageIndex));
        // Gallery does not auto-scroll on setActiveFile; scroll explicitly here
        // so the nav-button target tile becomes visible in the filmstrip.
        tileGallery.scrollToActive();
    }

    // =========================================================================
    // Zoom
    // =========================================================================

    /**
     * Set zoom level. If anchorCanvas != null, keep that canvas point fixed
     * on screen (zoom toward cursor).
     */
    private void setZoom(double nz, Point anchorCanvas) {
        double oldZoom = zoom;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));

        if (canvasWrapper != null) {
            canvasWrapper.revalidate();
            canvasWrapper.repaint();
        }

        // Zoom toward anchor point (keep image pixel under cursor steady)
        if (anchorCanvas != null && scrollPane != null && Math.abs(zoom - oldZoom) > 1e-9) {
            final Point anchor = anchorCanvas;
            final double oz    = oldZoom;
            SwingUtilities.invokeLater(() -> {
                JViewport vp   = scrollPane.getViewport();
                Dimension vs   = vp.getViewSize();
                Dimension vpSz = vp.getSize();
                // canvas offset inside wrapper
                int cx = canvasPanel.getX();
                int cy = canvasPanel.getY();
                // image coord under anchor
                int imgX = (int)(anchor.x / oz);
                int imgY = (int)(anchor.y / oz);
                // where that image coord is in the wrapper after zoom
                int newCanvasX = (int)(imgX * zoom);
                int newCanvasY = (int)(imgY * zoom);
                // viewport position so anchor screen pos stays same
                Point vpMouse = SwingUtilities.convertPoint(canvasPanel, anchor, vp);
                int vx = cx + newCanvasX - vpMouse.x;
                int vy = cy + newCanvasY - vpMouse.y;
                vx = Math.max(0, Math.min(vx, vs.width  - vpSz.width));
                vy = Math.max(0, Math.min(vy, vs.height - vpSz.height));
                vp.setViewPosition(new Point(vx, vy));
            });
        }

        updateZoomLabel();
    }

    private void fitToViewport() {
        if (workingImage == null || scrollPane == null) return;
        Dimension vd = scrollPane.getViewport().getSize();
        if (vd.width <= 0 || vd.height <= 0) { SwingUtilities.invokeLater(this::fitToViewport); return; }
        setZoom(Math.min((double) vd.width  / workingImage.getWidth(),
                         (double) vd.height / workingImage.getHeight()), null);
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    // =========================================================================
    // Coordinate transform
    // =========================================================================
    /** Convert a point in canvasPanel-local coordinates to image-space. */
    private Point screenToImage(Point sp) {
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
    private void performFloodfill(Point screenPt) {
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

    private void resetImage() {
        pushUndo();
        workingImage = deepCopy(originalImage);
        selectedAreas.clear();
        floatingImg = null; floatRect = null;
        isDraggingFloat = false; floatDragAnchor = null;
        activeHandle = -1; scaleBaseRect = null; scaleDragStart = null;
        hasUnsavedChanges = false;
        updateTitle();
        canvasPanel.repaint();
    }

    private void saveImage() {
        if (sourceFile == null) return;
        try {
            String suffix  = (appMode == AppMode.PAINT) ? "_painted"
                    : floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
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
    private void pushUndo() {
        if (workingImage == null) return;
        undoStack.push(deepCopy(workingImage));
        if (undoStack.size() > MAX_UNDO) undoStack.pollLast();
        redoStack.clear();
    }

    private void doUndo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(deepCopy(workingImage));
        workingImage = undoStack.pop();
        hasUnsavedChanges = true;
        updateTitle();
        canvasWrapper.revalidate();
        canvasPanel.repaint();
        if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
    }

    private void doRedo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(deepCopy(workingImage));
        workingImage = redoStack.pop();
        hasUnsavedChanges = true;
        updateTitle();
        canvasWrapper.revalidate();
        canvasPanel.repaint();
        if (showRuler) { hRuler.repaint(); vRuler.repaint(); }
    }

    /** CTRL+S: save silently without any confirmation dialog. */
    private void saveImageSilent() {
        if (sourceFile == null) return;
        try {
            String suffix  = (appMode == AppMode.PAINT) ? "_painted"
                    : floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
            String outPath = WhiteToAlphaConverter.getOutputPath(sourceFile, suffix);
            ImageIO.write(workingImage, "PNG", new File(outPath));
            hasUnsavedChanges = false;
            dirtyFiles.remove(sourceFile);
            updateTitle();
            updateDirtyUI();
            ToastNotification.show(this, "Gespeichert");
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    private void markDirty() {
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
    private void toggleAlphaMode() {
        if (appMode != AppMode.ALPHA_EDITOR) return;
        floodfillMode = !floodfillMode;
        modeLabel.setText("Modus: " + (floodfillMode ? "Floodfill" : "Selective Alpha"));
        boolean sel = !floodfillMode;
        applyButton.setEnabled(sel && sourceFile != null);
        clearSelectionsButton.setEnabled(sel && sourceFile != null);
        selectedAreas.clear(); canvasPanel.repaint();
    }

    private void togglePaintMode() {
        boolean entering = paintModeBtn.isSelected();
        appMode = entering ? AppMode.PAINT : AppMode.ALPHA_EDITOR;
        modeLabel.setText("Modus: " + (entering ? "Paint" : (floodfillMode ? "Floodfill" : "Selective Alpha")));
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
        paintSnapshot   = null;
        canvasPanel.setCursor(entering
                ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                : Cursor.getDefaultCursor());
        canvasPanel.repaint();
    }

    // =========================================================================
    // Transformations
    // =========================================================================
    private void doFlipH() {
        if (workingImage == null) return;
        pushUndo();
        workingImage = PaintEngine.flipHorizontal(workingImage);
        markDirty();
    }

    private void doFlipV() {
        if (workingImage == null) return;
        pushUndo();
        workingImage = PaintEngine.flipVertical(workingImage);
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
        JButton ok  = buildButton("OK",       AppColors.ACCENT,  AppColors.ACCENT_HOVER);
        JButton can = buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                double deg = Double.parseDouble(angleField.getText().trim());
                pushUndo();
                workingImage = PaintEngine.rotate(workingImage, deg);
                markDirty();
                canvasWrapper.revalidate();
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
        int origW = workingImage.getWidth();
        int origH = workingImage.getHeight();

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
        JButton ok  = buildButton("OK",        AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton can = buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Integer.parseInt(wField.getText().trim());
                int nh = Integer.parseInt(hField.getText().trim());
                pushUndo();
                workingImage = PaintEngine.scale(workingImage, nw, nh);
                markDirty();
                canvasWrapper.revalidate();
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

    /** Paste the floating image at its current (possibly scaled) rect and clear float state. */
    private void commitFloat() {
        if (floatingImg == null || floatRect == null) return;
        BufferedImage scaled = PaintEngine.scale(floatingImg,
                Math.max(1, floatRect.width), Math.max(1, floatRect.height));
        PaintEngine.pasteRegion(workingImage, scaled, new Point(floatRect.x, floatRect.y));
        floatingImg = null; floatRect = null;
        isDraggingFloat = false; floatDragAnchor = null;
        activeHandle = -1;  scaleBaseRect = null; scaleDragStart = null;
        selectedAreas.clear();
        markDirty();
    }

    /** Discard the float and undo to the state before it was lifted. */
    private void cancelFloat() {
        floatingImg = null; floatRect = null;
        isDraggingFloat = false; floatDragAnchor = null;
        activeHandle = -1;  scaleBaseRect = null; scaleDragStart = null;
        selectedAreas.clear();
        doUndo();
    }

    /** Convert floatRect (image-space) to canvasPanel screen-space. */
    private Rectangle floatRectScreen() {
        if (floatRect == null) return new Rectangle(0, 0, 0, 0);
        return new Rectangle(
            (int) Math.round(floatRect.x      * zoom),
            (int) Math.round(floatRect.y      * zoom),
            (int) Math.round(floatRect.width  * zoom),
            (int) Math.round(floatRect.height * zoom));
    }

    /**
     * 8 handle hit-rects around {@code sr} (screen-space).
     * Order: TL=0, TC=1, TR=2, ML=3, MR=4, BL=5, BC=6, BR=7
     */
    private Rectangle[] handleRects(Rectangle sr) {
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

    /** Returns 0-7 if {@code pt} (canvasPanel coords) hits a handle, else -1. */
    private int hitHandle(Point pt) {
        if (floatRect == null) return -1;
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
        double dx = (current.x - origin.x) / zoom;
        double dy = (current.y - origin.y) / zoom;
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
    private void doCut() {
        if (workingImage == null) return;
        pushUndo();
        Rectangle sel = getActiveSelection();
        if (sel != null) {
            clipboard = PaintEngine.cropRegion(workingImage, sel);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(workingImage, sel);
            markDirty();
        } else {
            clipboard = deepCopy(workingImage);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(workingImage,
                    new Rectangle(0, 0, workingImage.getWidth(), workingImage.getHeight()));
            markDirty();
        }
    }

    private void doCopy() {
        if (workingImage == null) return;
        Rectangle sel = getActiveSelection();
        clipboard = (sel != null) ? PaintEngine.cropRegion(workingImage, sel) : deepCopy(workingImage);
        copyToSystemClipboard(clipboard);
    }

    private void doPaste() {
        BufferedImage fromClip = pasteFromSystemClipboard();
        if (fromClip != null) clipboard = fromClip;
        if (clipboard != null && workingImage != null) {
            pushUndo();
            // Create floating selection immediately — handles appear right away.
            // Content is merged to the canvas only when commitFloat() is called.
            floatingImg = deepCopy(clipboard);
            floatRect   = new Rectangle(0, 0,
                    Math.min(clipboard.getWidth(),  workingImage.getWidth()),
                    Math.min(clipboard.getHeight(), workingImage.getHeight()));
            isDraggingFloat = false; floatDragAnchor = null;
            activeHandle = -1; scaleBaseRect = null; scaleDragStart = null;
            selectedAreas.clear();
            hasUnsavedChanges = true;
            updateTitle();
            canvasPanel.repaint();
        }
    }

    private Rectangle getActiveSelection() {
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
    // Keyboard shortcuts
    // =========================================================================
    private void setupKeyBindings() {
        JPanel root = (JPanel) getContentPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteElement");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,  0), "mergeElement");

        am.put("copy",   new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCopy(); } });
        am.put("cut",    new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doCut(); } });
        am.put("paste",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doPaste(); } });
        am.put("undo",   new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            // If a paste-float is still open, Ctrl+Z cancels it (cancelFloat calls doUndo internally).
            // Otherwise do a normal undo.
            if (floatingImg != null) cancelFloat(); else doUndo();
        } });
        am.put("redo",   new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { doRedo(); } });
        am.put("save",   new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { saveImageSilent(); } });
        am.put("escape", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (floatingImg != null) { cancelFloat(); }
            else if (selectedElement != null) { selectedElement = null; canvasPanel.repaint(); }
            else { selectedAreas.clear(); isSelecting = false; selectionStart = null; selectionEnd = null; canvasPanel.repaint(); }
        }});
        am.put("deleteElement", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (selectedElement != null) {
                deleteSelectedElement();
            } else if (!selectedAreas.isEmpty() && workingImage != null) {
                // DEL clears the pixel content of the current selection
                pushUndo();
                for (Rectangle r : selectedAreas) PaintEngine.clearRegion(workingImage, r);
                selectedAreas.clear();
                isSelecting = false; selectionStart = null; selectionEnd = null;
                markDirty();
            }
        }});
        am.put("mergeElement", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            if (selectedElement != null) mergeElementToCanvas(selectedElement);
        }});
    }

    // =========================================================================
    // New helper methods
    // =========================================================================

    /** Centers the viewport over the canvas (called after zoom or sidebar toggle). */
    private void centerCanvas() {
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
        JButton ok  = buildButton("Erstellen", AppColors.ACCENT,  AppColors.ACCENT_HOVER);
        JButton can = buildButton("Abbrechen", AppColors.BTN_BG,  AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Math.max(1, Integer.parseInt(wField.getText().trim()));
                int nh = Math.max(1, Integer.parseInt(hField.getText().trim()));
                if (sourceFile != null) saveCurrentState();
                workingImage      = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                originalImage     = deepCopy(workingImage);
                sourceFile        = null;
                hasUnsavedChanges = false;
                activeElements    = new ArrayList<>();
                undoStack.clear(); redoStack.clear();
                selectedAreas.clear();
                floatingImg = null; floatRect = null;
                swapToImageView();
                SwingUtilities.invokeLater(() -> { fitToViewport(); centerCanvas(); });
                updateTitle();
                updateStatus();
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

        JButton btn1 = buildButton("Farbe 1", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton btn2 = buildButton("Farbe 2", AppColors.BTN_BG, AppColors.BTN_HOVER);
        btn1.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe 1", canvasBg1);
            if (c != null) { canvasBg1 = c; preview.repaint(); canvasPanel.repaint(); }
        });
        btn2.addActionListener(e -> {
            Color c = javax.swing.JColorChooser.showDialog(this, "Hintergrundfarbe 2", canvasBg2);
            if (c != null) { canvasBg2 = c; preview.repaint(); canvasPanel.repaint(); }
        });

        JDialog dialog = createBaseDialog("Canvas-Hintergrund", 320, 240);
        JPanel content = centeredColumnPanel(16, 20, 12);
        content.add(preview);
        content.add(Box.createVerticalStrut(12));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.add(btn1); row.add(btn2);
        content.add(row);
        content.add(Box.createVerticalStrut(12));
        JButton closeBtn = buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        closeBtn.setAlignmentX(CENTER_ALIGNMENT);
        closeBtn.addActionListener(e -> dialog.dispose());
        content.add(closeBtn);
        dialog.add(content);
        dialog.setVisible(true);
    }

    // =========================================================================
    // CanvasPanel
    // =========================================================================
    private class CanvasPanel extends JPanel {

        // Pan tracking
        private Point panStart   = null;   // mouse position in viewport coords at drag start
        private Point panViewPos = null;   // viewport position at drag start

        CanvasPanel() {
            setOpaque(false);
            MouseAdapter handler = new MouseAdapter() {

                // ── Press ────────────────────────────────────────────────────
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();

                    // ── Pan: middle mouse or CTRL+left ────────────────────
                    boolean isMiddle  = (e.getButton() == MouseEvent.BUTTON2);
                    boolean isCtrlDrg = SwingUtilities.isLeftMouseButton(e)
                                     && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                    if (isMiddle || isCtrlDrg) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        panStart   = SwingUtilities.convertPoint(canvasPanel, e.getPoint(),
                                         scrollPane.getViewport());
                        panViewPos = scrollPane.getViewport().getViewPosition();
                        return;
                    }

                    if (!SwingUtilities.isLeftMouseButton(e) || workingImage == null) return;

                    Point imgPt = screenToImage(e.getPoint());

                    // ── Element layer interaction (any mode, checked first) ───
                    if (!activeElements.isEmpty() && floatingImg == null) {
                        // Check handles of selected element first
                        if (selectedElement != null) {
                            Rectangle selScr = elemRectScreen(selectedElement);
                            Rectangle[] handles = handleRects(selScr);
                            for (int hi = 0; hi < handles.length; hi++) {
                                if (handles[hi].contains(e.getPoint())) {
                                    elemActiveHandle = hi;
                                    elemScaleBase    = new Rectangle(
                                        selectedElement.x(), selectedElement.y(),
                                        selectedElement.width(), selectedElement.height());
                                    elemScaleStart   = e.getPoint();
                                    return;
                                }
                            }
                            if (selScr.contains(e.getPoint())) {
                                draggingElement = true;
                                elemDragAnchor  = new Point(imgPt.x - selectedElement.x(),
                                                             imgPt.y - selectedElement.y());
                                return;
                            }
                        }
                        // Hit-test unselected elements (top = last in list)
                        Element hit = null;
                        for (int i = activeElements.size() - 1; i >= 0; i--) {
                            if (elemRectScreen(activeElements.get(i)).contains(e.getPoint())) {
                                hit = activeElements.get(i); break;
                            }
                        }
                        if (hit != null) {
                            selectedElement = hit;
                            draggingElement = true;
                            elemDragAnchor  = new Point(imgPt.x - hit.x(), imgPt.y - hit.y());
                            canvasPanel.repaint();
                            return;
                        } else {
                            // Click outside all elements → deselect
                            selectedElement = null;
                            canvasPanel.repaint();
                        }
                    }

                    // ── Floating selection: handle/move/commit (any mode) ─────
                    if (floatingImg != null) {
                        int h = hitHandle(e.getPoint());
                        if (h >= 0) {
                            activeHandle   = h;
                            scaleBaseRect  = new Rectangle(floatRect);
                            scaleDragStart = e.getPoint();
                            return;
                        } else if (floatRectScreen().contains(e.getPoint())) {
                            isDraggingFloat = true;
                            floatDragAnchor = new Point(imgPt.x - floatRect.x, imgPt.y - floatRect.y);
                            return;
                        } else {
                            commitFloat();
                            // fall through to normal handling with fresh state
                        }
                    }

                    if (appMode == AppMode.PAINT) {
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        switch (tool) {
                            case PENCIL, ERASER -> {
                                pushUndo();
                                lastPaintPoint = imgPt;
                                paintDot(imgPt);
                            }
                            case FLOODFILL -> {
                                pushUndo();
                                PaintEngine.floodFill(workingImage, imgPt.x, imgPt.y,
                                        paintToolbar.getPrimaryColor(), 30);
                                markDirty();
                            }
                            case EYEDROPPER -> {
                                Color picked = PaintEngine.pickColor(workingImage, imgPt.x, imgPt.y);
                                paintToolbar.setSelectedColor(picked);
                            }
                            case LINE, CIRCLE, RECT -> {
                                pushUndo();
                                shapeStartPoint = imgPt;
                                paintSnapshot   = deepCopy(workingImage);
                            }
                            case SELECT -> {
                                if (!selectedAreas.isEmpty()) {
                                    Rectangle sel = selectedAreas.get(selectedAreas.size() - 1);
                                    if (sel.contains(imgPt)) {
                                        // Lift selection into floating image
                                        pushUndo();
                                        floatingImg     = PaintEngine.cropRegion(workingImage, sel);
                                        floatRect       = new Rectangle(sel);
                                        PaintEngine.clearRegion(workingImage, sel);
                                        selectedAreas.clear();
                                        isDraggingFloat = true;
                                        floatDragAnchor = new Point(imgPt.x - sel.x, imgPt.y - sel.y);
                                        canvasPanel.repaint();
                                    } else {
                                        shapeStartPoint = imgPt;
                                    }
                                } else {
                                    shapeStartPoint = imgPt;
                                }
                            }
                        }
                    } else {
                        if (floodfillMode) {
                            pushUndo();
                            performFloodfill(e.getPoint());
                        } else {
                            // SHIFT = add to multi-selection; plain = start new
                            if (!e.isShiftDown()) {
                                isSelecting    = true;
                                selectionStart = imgPt;
                                selectionEnd   = imgPt;
                            } else {
                                isSelecting    = true;
                                selectionStart = imgPt;
                                selectionEnd   = imgPt;
                                // existing selections preserved (SHIFT adds)
                            }
                        }
                    }
                }

                // ── Drag ─────────────────────────────────────────────────────
                @Override public void mouseDragged(MouseEvent e) {
                    // Pan
                    if (panStart != null) {
                        Point now = SwingUtilities.convertPoint(canvasPanel, e.getPoint(),
                                        scrollPane.getViewport());
                        int dx = now.x - panStart.x;
                        int dy = now.y - panStart.y;
                        JViewport vp = scrollPane.getViewport();
                        Dimension vs = vp.getViewSize();
                        Dimension vd = vp.getSize();
                        int nx = Math.max(0, Math.min(panViewPos.x - dx, vs.width  - vd.width));
                        int ny = Math.max(0, Math.min(panViewPos.y - dy, vs.height - vd.height));
                        vp.setViewPosition(new Point(nx, ny));
                        return;
                    }

                    if (workingImage == null) return;
                    Point imgPt = screenToImage(e.getPoint());

                    // ── Element drag/resize (any mode) ───────────────────────
                    if (selectedElement != null && elemActiveHandle >= 0
                            && elemScaleBase != null && elemScaleStart != null) {
                        Rectangle nr = computeNewFloatRect(elemActiveHandle, elemScaleBase,
                                elemScaleStart, e.getPoint());
                        int idx = activeElements.indexOf(selectedElement);
                        if (idx >= 0) {
                            selectedElement = selectedElement.withBounds(nr.x, nr.y, nr.width, nr.height);
                            activeElements.set(idx, selectedElement);
                        }
                        canvasPanel.repaint();
                        return;
                    }
                    if (draggingElement && selectedElement != null && elemDragAnchor != null) {
                        int idx = activeElements.indexOf(selectedElement);
                        if (idx >= 0) {
                            selectedElement = selectedElement.withPosition(
                                imgPt.x - elemDragAnchor.x, imgPt.y - elemDragAnchor.y);
                            activeElements.set(idx, selectedElement);
                        }
                        canvasPanel.repaint();
                        return;
                    }

                    // ── Floating selection: scale/move (any mode) ─────────────
                    if (activeHandle >= 0 && scaleBaseRect != null && scaleDragStart != null) {
                        floatRect = computeNewFloatRect(activeHandle, scaleBaseRect,
                                scaleDragStart, e.getPoint());
                        canvasPanel.repaint();
                        return;
                    }
                    if (isDraggingFloat && floatDragAnchor != null) {
                        floatRect = new Rectangle(
                            imgPt.x - floatDragAnchor.x,
                            imgPt.y - floatDragAnchor.y,
                            floatRect.width, floatRect.height);
                        canvasPanel.repaint();
                        return;
                    }

                    if (appMode == AppMode.PAINT) {
                        boolean aa = paintToolbar.isAntialiasing();
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        switch (tool) {
                            case PENCIL -> {
                                if (lastPaintPoint != null)
                                    PaintEngine.drawPencil(workingImage, lastPaintPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth(),
                                            paintToolbar.getBrushShape(), aa);
                                lastPaintPoint = imgPt;
                                markDirty();
                            }
                            case ERASER -> {
                                if (lastPaintPoint != null)
                                    PaintEngine.drawEraser(workingImage, lastPaintPoint, imgPt,
                                            paintToolbar.getStrokeWidth(), aa);
                                lastPaintPoint = imgPt;
                                markDirty();
                            }
                            case LINE -> {
                                if (shapeStartPoint != null && paintSnapshot != null) {
                                    copyInto(paintSnapshot, workingImage);
                                    PaintEngine.drawLine(workingImage, shapeStartPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth(), aa);
                                    canvasPanel.repaint();
                                }
                            }
                            case CIRCLE -> {
                                if (shapeStartPoint != null && paintSnapshot != null) {
                                    copyInto(paintSnapshot, workingImage);
                                    PaintEngine.drawCircle(workingImage, shapeStartPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth(),
                                            paintToolbar.getFillMode(),
                                            paintToolbar.getSecondaryColor(), aa);
                                    canvasPanel.repaint();
                                }
                            }
                            case RECT -> {
                                if (shapeStartPoint != null && paintSnapshot != null) {
                                    copyInto(paintSnapshot, workingImage);
                                    PaintEngine.drawRect(workingImage, shapeStartPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth(),
                                            paintToolbar.getFillMode(),
                                            paintToolbar.getSecondaryColor(), aa);
                                    canvasPanel.repaint();
                                }
                            }
                            case SELECT -> {
                                if (shapeStartPoint != null) {
                                    selectionStart = shapeStartPoint;
                                    selectionEnd   = imgPt;
                                    isSelecting    = true;
                                    canvasPanel.repaint();
                                }
                            }
                            default -> {}
                        }
                    } else {
                        if (!floodfillMode && isSelecting) {
                            selectionEnd = imgPt;
                            repaint();
                        }
                    }
                }

                // ── Release ───────────────────────────────────────────────────
                @Override public void mouseReleased(MouseEvent e) {
                    if (panStart != null) {
                        panStart   = null;
                        panViewPos = null;
                        setCursor(appMode == AppMode.PAINT
                                ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                                : Cursor.getDefaultCursor());
                        return;
                    }
                    if (workingImage == null) return;
                    Point imgPt = screenToImage(e.getPoint());

                    // ── Element finish drag/resize ────────────────────────────
                    if (draggingElement || elemActiveHandle >= 0) {
                        draggingElement  = false;
                        elemDragAnchor   = null;
                        elemActiveHandle = -1;
                        elemScaleBase    = null;
                        elemScaleStart   = null;
                        markDirty();
                        return;
                    }

                    // ── Floating selection: finish drag/scale (any mode) ──────
                    if (activeHandle >= 0 || isDraggingFloat) {
                        activeHandle    = -1;
                        isDraggingFloat = false;
                        floatDragAnchor = null;
                        scaleDragStart  = null;
                        scaleBaseRect   = null;
                        canvasPanel.repaint();
                        return;
                    }

                    if (appMode == AppMode.PAINT) {
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        if (tool == PaintEngine.Tool.SELECT) {
                            if (shapeStartPoint != null) {
                                // Finished rubber-band selection
                                int x = Math.min(shapeStartPoint.x, imgPt.x);
                                int y = Math.min(shapeStartPoint.y, imgPt.y);
                                int w = Math.abs(imgPt.x - shapeStartPoint.x);
                                int h = Math.abs(imgPt.y - shapeStartPoint.y);
                                if (w > 2 && h > 2) {
                                    selectedAreas.clear();
                                    selectedAreas.add(new Rectangle(x, y, w, h));
                                }
                                shapeStartPoint = null;
                                isSelecting     = false;
                                selectionStart  = null;
                                selectionEnd    = null;
                            }
                            canvasPanel.repaint();
                        } else {
                            lastPaintPoint  = null;
                            shapeStartPoint = null;
                            paintSnapshot   = null;
                            markDirty();
                        }
                    } else {
                        if (!floodfillMode && isSelecting && SwingUtilities.isLeftMouseButton(e)) {
                            isSelecting = false;
                            if (selectionStart != null && selectionEnd != null) {
                                int x = Math.min(selectionStart.x, selectionEnd.x);
                                int y = Math.min(selectionStart.y, selectionEnd.y);
                                int w = Math.abs(selectionEnd.x - selectionStart.x);
                                int h = Math.abs(selectionEnd.y - selectionStart.y);
                                if (w > 2 && h > 2) {
                                    // SHIFT = add; no SHIFT = replace
                                    if (!e.isShiftDown()) selectedAreas.clear();
                                    selectedAreas.add(new Rectangle(x, y, w, h));
                                }
                            }
                            selectionStart = null; selectionEnd = null;
                            repaint();
                        }
                    }
                }

                // ── Mouse moved: cursor shape over handles / float ────────────
                @Override public void mouseMoved(MouseEvent e) {
                    if (floatingImg != null) {
                        int h = hitHandle(e.getPoint());
                        if (h >= 0) {
                            setCursor(getResizeCursor(h));
                        } else if (floatRectScreen().contains(e.getPoint())) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        } else {
                            setCursor(appMode == AppMode.PAINT
                                    ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                                    : Cursor.getDefaultCursor());
                        }
                        return;
                    }
                    if (appMode == AppMode.PAINT && paintToolbar != null
                            && paintToolbar.getActiveTool() == PaintEngine.Tool.SELECT) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    }
                }

                // ── Wheel: CTRL→zoom, else→scroll ────────────────────────────
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                        Point anchor = e.getPoint(); // canvas-local
                        setZoom(zoom + (-e.getPreciseWheelRotation() * ZOOM_WHEEL), anchor);
                        e.consume();
                    } else if (e.isShiftDown()) {
                        JScrollBar bar = scrollPane.getHorizontalScrollBar();
                        bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
                        e.consume();
                    } else {
                        JScrollBar bar = scrollPane.getVerticalScrollBar();
                        bar.setValue(bar.getValue() + e.getUnitsToScroll() * bar.getUnitIncrement());
                        e.consume();
                    }
                }
            };

            addMouseListener(handler);
            addMouseMotionListener(handler);
            addMouseWheelListener(handler);
        }

        private void paintDot(Point imgPt) {
            boolean aa = paintToolbar != null && paintToolbar.isAntialiasing();
            PaintEngine.drawPencil(workingImage, imgPt, imgPt,
                    paintToolbar.getPrimaryColor(),
                    paintToolbar.getStrokeWidth(),
                    paintToolbar.getBrushShape(), aa);
            markDirty();
        }

        @Override public Dimension getPreferredSize() {
            if (workingImage == null) return new Dimension(1, 1);
            return new Dimension(
                    (int) Math.ceil(workingImage.getWidth()  * zoom),
                    (int) Math.ceil(workingImage.getHeight() * zoom));
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (workingImage == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    zoom >= 2.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int cw = (int) Math.ceil(workingImage.getWidth()  * zoom);
            int ch = (int) Math.ceil(workingImage.getHeight() * zoom);

            // Checkerboard (colors configurable via showCanvasBgDialog)
            int cell = Math.max(4, (int)(10 * zoom));
            for (int row = 0; row < ch; row += cell)
                for (int col = 0; col < cw; col += cell) {
                    boolean even = ((row/cell)+(col/cell)) % 2 == 0;
                    g2.setColor(even ? canvasBg1 : canvasBg2);
                    g2.fillRect(col, row, Math.min(cell, cw-col), Math.min(cell, ch-row));
                }

            // Image
            g2.drawImage(workingImage, 0, 0, cw, ch, null);

            // ── Element layers (drawn above image, below float / grid) ────────
            for (Element el : activeElements) {
                int ex = (int) Math.round(el.x()      * zoom);
                int ey = (int) Math.round(el.y()      * zoom);
                int ew = (int) Math.round(el.width()  * zoom);
                int eh = (int) Math.round(el.height() * zoom);
                g2.drawImage(el.image(), ex, ey, ew, eh, null);
                boolean isSelected = el.equals(selectedElement);
                float[] dash = { 5f, 3f };
                g2.setColor(isSelected ? AppColors.ACCENT : new Color(255, 255, 0, 180));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 1f, dash, 0f));
                g2.drawRect(ex, ey, ew, eh);
                if (isSelected) {
                    g2.setStroke(new BasicStroke(1f));
                    for (Rectangle hr : handleRects(new Rectangle(ex, ey, ew, eh))) {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(hr.x, hr.y, hr.width, hr.height);
                        g2.setColor(AppColors.ACCENT);
                        g2.drawRect(hr.x, hr.y, hr.width, hr.height);
                    }
                }
            }

            // Floating selection (drawn above elements, below grid/selection overlays)
            if (floatingImg != null && floatRect != null) {
                int fx = (int) Math.round(floatRect.x     * zoom);
                int fy = (int) Math.round(floatRect.y     * zoom);
                int fw = (int) Math.round(floatRect.width  * zoom);
                int fh = (int) Math.round(floatRect.height * zoom);
                g2.drawImage(floatingImg, fx, fy, fw, fh, null);
                // Marching-ant dashed border
                float[] dash = { 6f, 4f };
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 1f, dash, 0f));
                g2.drawRect(fx, fy, fw, fh);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 1f, dash, 6f));
                g2.drawRect(fx, fy, fw, fh);
                // 8 scale handles
                g2.setStroke(new BasicStroke(1f));
                for (Rectangle hr : handleRects(new Rectangle(fx, fy, fw, fh))) {
                    g2.setColor(Color.WHITE);
                    g2.fillRect(hr.x, hr.y, hr.width, hr.height);
                    g2.setColor(AppColors.ACCENT);
                    g2.drawRect(hr.x, hr.y, hr.width, hr.height);
                }
            }

            // Grid
            if (showGrid) {
                g2.setColor(new Color(100, 100, 255, 60));
                g2.setStroke(new BasicStroke(0.5f));
                int gx = (int) Math.max(1, GRID_CELL * zoom);
                for (int x = 0; x < cw; x += gx) g2.drawLine(x, 0, x, ch);
                for (int y = 0; y < ch; y += gx) g2.drawLine(0, y, cw, y);
            }

            // Selections
            if (appMode == AppMode.ALPHA_EDITOR || paintToolbar == null
                    || paintToolbar.getActiveTool() == PaintEngine.Tool.SELECT) {
                g2.setColor(new Color(255, 0, 0, 70));
                for (Rectangle r : selectedAreas)
                    g2.fillRect(toSx(r.x), toSy(r.y), toSw(r.width), toSw(r.height));
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(1.2f));
                for (Rectangle r : selectedAreas)
                    g2.drawRect(toSx(r.x), toSy(r.y), toSw(r.width), toSw(r.height));
                if ((isSelecting || (appMode == AppMode.PAINT && shapeStartPoint != null))
                        && selectionStart != null && selectionEnd != null) {
                    int x = Math.min(selectionStart.x, selectionEnd.x);
                    int y = Math.min(selectionStart.y, selectionEnd.y);
                    int w = Math.abs(selectionEnd.x - selectionStart.x);
                    int h = Math.abs(selectionEnd.y - selectionStart.y);
                    g2.setColor(new Color(0, 200, 255, 60));
                    g2.fillRect(toSx(x), toSy(y), toSw(w), toSw(h));
                    g2.setColor(new Color(0, 200, 255));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRect(toSx(x), toSy(y), toSw(w), toSw(h));
                }
            }
        }

        private int toSx(int ix) { return (int) Math.round(ix * zoom); }
        private int toSy(int iy) { return (int) Math.round(iy * zoom); }
        private int toSw(int iw) { return (int) Math.round(iw * zoom); }
    }

    // =========================================================================
    // Ruler panels (drawn OUTSIDE the image, synchronized with scroll)
    // =========================================================================

    /** Horizontal ruler above the scroll pane. */
    private class HRulerPanel extends JPanel {
        HRulerPanel() {
            setPreferredSize(new Dimension(0, RULER_THICK));
            setBackground(new Color(50, 50, 50));
            setOpaque(true);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (workingImage == null || scrollPane == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            Point viewPos = scrollPane.getViewport().getViewPosition();
            int canvasOffX = canvasPanel.getX(); // centering offset in wrapper

            g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
            g2.setColor(new Color(180, 180, 180));

            double pxPerUnit   = rulerUnit.pxPerUnit();
            // How many image pixels between ruler ticks (auto-scale for readability)
            double imgPxPerTick = chooseTick(pxPerUnit, zoom);
            double screenPxPerTick = imgPxPerTick * zoom;
            if (screenPxPerTick < 4) return;

            // First tick at which image-coord
            double startImgX = (viewPos.x - canvasOffX) / zoom;
            double firstTick = Math.floor(startImgX / imgPxPerTick) * imgPxPerTick;

            for (double imgX = firstTick; imgX * zoom - (viewPos.x - canvasOffX) < w; imgX += imgPxPerTick) {
                int sx = (int)((imgX * zoom) - (viewPos.x - canvasOffX));
                if (sx < 0) continue;
                g2.drawLine(sx, RULER_THICK - 5, sx, RULER_THICK);
                if (screenPxPerTick > 20) {
                    double val = imgX / pxPerUnit;
                    String label = (val == (long) val) ? String.valueOf((long) val)
                                                       : String.format("%.1f", val);
                    g2.drawString(label + rulerUnit.label(), sx + 2, 9);
                }
            }
            // Bottom border line
            g2.setColor(AppColors.BORDER);
            g2.drawLine(0, RULER_THICK - 1, w, RULER_THICK - 1);
        }
    }

    /** Vertical ruler to the left of the scroll pane. */
    private class VRulerPanel extends JPanel {
        VRulerPanel() {
            setPreferredSize(new Dimension(RULER_THICK, 0));
            setBackground(new Color(50, 50, 50));
            setOpaque(true);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (workingImage == null || scrollPane == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            Point viewPos = scrollPane.getViewport().getViewPosition();
            int canvasOffY = canvasPanel.getY();

            g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
            g2.setColor(new Color(180, 180, 180));

            double pxPerUnit   = rulerUnit.pxPerUnit();
            double imgPxPerTick = chooseTick(pxPerUnit, zoom);
            double screenPxPerTick = imgPxPerTick * zoom;
            if (screenPxPerTick < 4) return;

            double startImgY = (viewPos.y - canvasOffY) / zoom;
            double firstTick = Math.floor(startImgY / imgPxPerTick) * imgPxPerTick;

            for (double imgY = firstTick; imgY * zoom - (viewPos.y - canvasOffY) < h; imgY += imgPxPerTick) {
                int sy = (int)((imgY * zoom) - (viewPos.y - canvasOffY));
                if (sy < 0) continue;
                g2.drawLine(RULER_THICK - 5, sy, RULER_THICK, sy);
                if (screenPxPerTick > 20) {
                    double val = imgY / pxPerUnit;
                    String label = (val == (long) val) ? String.valueOf((long) val)
                                                       : String.format("%.1f", val);
                    Graphics2D gr = (Graphics2D) g2.create();
                    gr.translate(9, sy - 2);
                    gr.rotate(-Math.PI / 2);
                    gr.drawString(label, 0, 0);
                    gr.dispose();
                }
            }
            // Right border line
            g2.setColor(AppColors.BORDER);
            g2.drawLine(RULER_THICK - 1, 0, RULER_THICK - 1, h);
        }
    }

    /**
     * Chooses a sensible tick interval in image-pixels so ticks are
     * not too crowded (min ~30 screen px apart) and snap to nice values.
     */
    private static double chooseTick(double pxPerUnit, double zoom) {
        // Target: ≥ 30 screen pixels between ticks
        double minScreenPx  = 30.0;
        double imgPxPerTick = minScreenPx / zoom;
        // Round up to a "nice" multiple of pxPerUnit
        double unitsPerTick = imgPxPerTick / pxPerUnit;
        double nice = Math.pow(10, Math.ceil(Math.log10(unitsPerTick)));
        if (unitsPerTick <= nice / 5) nice /= 5;
        else if (unitsPerTick <= nice / 2) nice /= 2;
        return nice * pxPerUnit;
    }

    // =========================================================================
    // UI state helpers
    // =========================================================================
    private void swapToImageView() {
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

    private void updateTitle() {
        if (sourceFile == null) { setTitle("Selective Alpha Editor"); return; }
        String dirty = hasUnsavedChanges ? " •" : "";
        String mode  = appMode == AppMode.PAINT ? "[Paint]"
                : floodfillMode ? "[Floodfill]" : "[Selective Alpha]";
        setTitle("Selective Alpha Editor  " + mode + "  –  " + sourceFile.getName() + dirty);
    }

    private void updateStatus() {
        if (sourceFile == null) { statusLabel.setText("Keine Datei geladen"); return; }
        statusLabel.setText(sourceFile.getName()
                + "   |   " + (currentImageIndex + 1) + " / " + directoryImages.size()
                + "   |   " + workingImage.getWidth() + " × " + workingImage.getHeight() + " px");
    }

    private void setBottomButtonsEnabled(boolean enabled) {
        boolean sel = !floodfillMode && appMode == AppMode.ALPHA_EDITOR;
        applyButton.setEnabled(enabled && sel);
        clearSelectionsButton.setEnabled(enabled && sel);
        JPanel ap = findActionPanel();
        if (ap == null) return;
        for (java.awt.Component c : ap.getComponents())
            if (c instanceof JButton btn && ("resetButton".equals(btn.getName()) || "saveButton".equals(btn.getName())))
                btn.setEnabled(enabled);
    }

    private JPanel findActionPanel() {
        java.awt.Container south = (java.awt.Container)
                ((BorderLayout) getContentPane().getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (south == null) return null;
        for (java.awt.Component c : south.getComponents())
            if (c instanceof JPanel p && "actionPanel".equals(p.getName())) return p;
        return null;
    }

    // =========================================================================
    // Custom Dialogs
    // =========================================================================
    private int showUnsavedChangesDialog() {
        if (!hasUnsavedChanges) return 1;
        final int[] result = { 2 };
        JDialog dialog = createBaseDialog("Ungespeicherte Änderungen", 420, 310);
        JPanel  content = centeredColumnPanel(20, 28, 16);
        content.add(styledLabel("⚠", 30, AppColors.WARNING, Font.PLAIN));
        content.add(Box.createVerticalStrut(10));
        content.add(htmlLabel("Das Bild hat ungespeicherte Änderungen.<br>Was möchtest du tun?", AppColors.TEXT, 13));
        content.add(Box.createVerticalStrut(18));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        JButton sBtn = buildButton("Speichern",  AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
        JButton dBtn = buildButton("Verwerfen",  AppColors.DANGER,  AppColors.DANGER_HOVER);
        JButton cBtn = buildButton("Abbrechen",  AppColors.BTN_BG,  AppColors.BTN_HOVER);
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

    private void showErrorDialog(String title, String message) {
        JDialog dialog = createBaseDialog(title, 440, 215);
        JPanel content = centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = htmlLabel(message.replace("\n","<br>"), AppColors.TEXT, 12);
        JButton ok = buildButton("OK", AppColors.DANGER, AppColors.DANGER_HOVER);
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
        JButton ok = buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
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

    // =========================================================================
    // Button factories
    // =========================================================================
    private JButton buildButton(String text, Color bg, Color hover) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? hover : (isEnabled() ? bg : AppColors.BTN_BG.darker()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setForeground(AppColors.TEXT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        btn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        btn.setMinimumSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        return btn;
    }

    private JToggleButton buildModeToggleBtn(String symbol, String tooltip) {
        JToggleButton btn = new JToggleButton(symbol) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected() ? AppColors.ACCENT_ACTIVE
                        : (getModel().isRollover() ? AppColors.BTN_HOVER : AppColors.BTN_BG);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isSelected()) {
                    g2.setColor(AppColors.ACCENT);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 8, 8);
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 16));
        btn.setForeground(AppColors.TEXT);
        btn.setFocusPainted(false); btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton buildNavButton(String symbol) {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(!isEnabled() ? new Color(0,0,0,30)
                        : getModel().isRollover() ? new Color(255,255,255,55) : new Color(0,0,0,110));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(isEnabled() ? AppColors.TEXT : AppColors.TEXT_MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 30));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(symbol)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(symbol, tx, ty);
            }
        };
        btn.setFocusPainted(false); btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // Element helpers
    // =========================================================================

    /** Screen-space rectangle for an element, accounting for current zoom. */
    private Rectangle elemRectScreen(Element el) {
        return new Rectangle(
            (int) Math.round(el.x()      * zoom),
            (int) Math.round(el.y()      * zoom),
            (int) Math.round(el.width()  * zoom),
            (int) Math.round(el.height() * zoom));
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
        Element el = new Element(nextElementId++, src, ex, ey, src.getWidth(), src.getHeight());
        activeElements.add(el);
        selectedElement = el;
        selectedAreas.clear();
        markDirty();
    }

    /** Merges the selected element onto the canvas and removes it from the layer list. */
    private void mergeElementToCanvas(Element el) {
        if (el == null || workingImage == null) return;
        pushUndo();
        BufferedImage scaled = PaintEngine.scale(el.image(), Math.max(1, el.width()), Math.max(1, el.height()));
        PaintEngine.pasteRegion(workingImage, scaled, new Point(el.x(), el.y()));
        activeElements.remove(el);
        if (selectedElement == el) selectedElement = null;
        markDirty();
    }

    /** Deletes the selected element without merging to canvas. */
    private void deleteSelectedElement() {
        if (selectedElement == null) return;
        activeElements.remove(selectedElement);
        selectedElement = null;
        markDirty();
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

    private BufferedImage deepCopy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        c.createGraphics().drawImage(src, 0, 0, null); return c;
    }

    private void copyInto(BufferedImage src, BufferedImage dst) {
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
    }
}
