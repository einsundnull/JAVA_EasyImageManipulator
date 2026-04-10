package paint;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Main application window.
 *
 * Modes:
 *  - Alpha Editor  (Selective Selection + Floodfill)
 *  - Paint Mode    (full MS-Paint-style toolbar via PaintToolbar)
 *
 * Toolbar buttons in TopBar (right cluster):
 *  [🖼 Canvas]  [🖌 Paint]  |  [−] [%] [+] [1:1] [Fit]
 */
public class SelectiveAlphaEditor extends JFrame {
    // console.log("### SelectiveAlphaEditor.java ###");

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
    private static final double ZOOM_MIN  = 0.05;
    private static final double ZOOM_MAX  = 16.0;
    private static final double ZOOM_STEP = 0.10;

    // Grid
    private static final int GRID_CELL = 16; // image-space pixels

    // ── TopBar button sizes (change here to resize Canvas / Paint / Zoom buttons) ───
    // ── All button sizes ── change here to resize every button globally ────────
    private static final int TOPBAR_BTN_W      = 50;  // Canvas / Paint mode toggle buttons
    private static final int TOPBAR_BTN_H      = 50;  // Canvas / Paint mode toggle buttons
    private static final int TOPBAR_ZOOM_BTN_W = 50;  // Zoom buttons (−, +, 1:1, Fit)
    private static final int TOPBAR_ZOOM_BTN_H = 50;  // Zoom buttons

    // ── Application mode ──────────────────────────────────────────────────────
    public enum AppMode { ALPHA_EDITOR, PAINT }

    // ── State ─────────────────────────────────────────────────────────────────
    private BufferedImage originalImage;
    private BufferedImage workingImage;
    private BufferedImage clipboard;        // internal paint clipboard
    private Point         pasteOffset;     // where pasted content sits (image-space)
    private File          sourceFile;

    private AppMode  appMode           = AppMode.ALPHA_EDITOR;
    private boolean  floodfillMode     = false;  // within ALPHA_EDITOR
    private boolean  hasUnsavedChanges = false;
    private boolean  showGrid          = false;
    private boolean  showRuler         = false;

    // Zoom
    private double zoom = 1.0;

    // Alpha-editor selection (image-space)
    private boolean          isSelecting    = false;
    private Point            selectionStart = null;
    private Point            selectionEnd   = null;
    private List<Rectangle>  selectedAreas  = new ArrayList<>();

    // Paint-mode stroke tracking (image-space)
    private Point lastPaintPoint = null;
    private Point shapeStartPoint = null;   // for line/circle/rect preview
    private BufferedImage paintSnapshot = null; // snapshot before shape preview

    // Directory browsing
    private List<File> directoryImages   = new ArrayList<>();
    private int        currentImageIndex = -1;

    // ── UI references ─────────────────────────────────────────────────────────
    private CanvasPanel  canvasPanel;
    private JPanel       viewportPanel;
    private JPanel       dropHintPanel;
    private JLayeredPane layeredPane;

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
        // console.log("### SelectiveAlphaEditor.java main ###");
        SwingUtilities.invokeLater(SelectiveAlphaEditor::new);
    }

    // =========================================================================
    // Constructors
    // =========================================================================
    public SelectiveAlphaEditor() {
        // console.log("### SelectiveAlphaEditor.java constructor (no file) ###");
        initializeUI();
    }

    public SelectiveAlphaEditor(File imageFile, boolean floodfillMode) {
        // console.log("### SelectiveAlphaEditor.java constructor (file) ###");
        this.floodfillMode = floodfillMode;
        initializeUI();
        loadFile(imageFile);
    }

    // =========================================================================
    // UI construction
    // =========================================================================
    private void initializeUI() {
        // console.log("### SelectiveAlphaEditor.java initializeUI ###");
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

        // Global keyboard shortcuts (before visible)
        setupKeyBindings();

        // Show main window first so getLocationOnScreen() works in PaintToolbar
        setVisible(true);

        // PaintToolbar is a JPanel – already added to SOUTH in buildBottomBar()
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        // console.log("### SelectiveAlphaEditor.java buildTopBar ###");
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

        // ── Left: mode label + alpha toggle ──────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        left.setOpaque(false);
        modeLabel = new JLabel("Modus: Selective Alpha");
        modeLabel.setForeground(AppColors.TEXT_MUTED);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JButton toggleBtn = buildButton("Alpha-Modus wechseln", AppColors.BTN_BG, AppColors.BTN_HOVER);
        toggleBtn.addActionListener(e -> toggleAlphaMode());
        left.add(modeLabel);
        left.add(toggleBtn);
        bar.add(left, BorderLayout.WEST);

        // ── Right: Canvas btn + Paint btn + zoom controls ────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        right.setOpaque(false);

        // Canvas button (placeholder – function TBD)
        canvasModeBtn = buildModeToggleBtn("⬜", "Canvas (Funktion folgt)");
        canvasModeBtn.addActionListener(e -> {
            // Placeholder: will activate vector canvas mode later
            canvasModeBtn.setSelected(false); // keep unselected for now
            showInfoDialog("Canvas-Modus", "Diese Funktion wird in einer späteren Version implementiert.");
        });

        // Paint button
        paintModeBtn = buildModeToggleBtn("🖌", "Paint-Modus aktivieren / deaktivieren");
        paintModeBtn.addActionListener(e -> togglePaintMode());

        // Zoom
        JButton zoomOutBtn  = buildButton("−",   AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomInBtn   = buildButton("+",   AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomResetBtn= buildButton("1:1", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton zoomFitBtn  = buildButton("Fit", AppColors.BTN_BG, AppColors.BTN_HOVER);
        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(AppColors.TEXT_MUTED);
        zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        zoomLabel.setPreferredSize(new Dimension(46, 20));
        zoomLabel.setHorizontalAlignment(JLabel.RIGHT);

        zoomOutBtn  .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomInBtn   .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomResetBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomFitBtn  .setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
        zoomOutBtn  .addActionListener(e -> setZoom(zoom - ZOOM_STEP * 2));
        zoomInBtn   .addActionListener(e -> setZoom(zoom + ZOOM_STEP * 2));
        zoomResetBtn.addActionListener(e -> setZoom(1.0));
        zoomFitBtn  .addActionListener(e -> fitToViewport());

        right.add(canvasModeBtn);
        right.add(paintModeBtn);
        right.add(Box.createHorizontalStrut(8));
        right.add(zoomOutBtn);
        right.add(zoomLabel);
        right.add(zoomInBtn);
        right.add(zoomResetBtn);
        right.add(zoomFitBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── Center ────────────────────────────────────────────────────────────────
    private JPanel buildCenter() {
        // console.log("### SelectiveAlphaEditor.java buildCenter ###");
        dropHintPanel = buildDropHintPanel();
        setupDropTarget(dropHintPanel);

        canvasPanel = new CanvasPanel();

        viewportPanel = new JPanel(null) {
            @Override public void doLayout() {
                if (canvasPanel.getParent() == this) {
                    Dimension cs = canvasPanel.getPreferredSize();
                    int x = Math.max(0, (getWidth()  - cs.width)  / 2);
                    int y = Math.max(0, (getHeight() - cs.height) / 2);
                    canvasPanel.setBounds(x, y, cs.width, cs.height);
                }
            }
        };
        viewportPanel.setBackground(AppColors.BG_DARK);
        viewportPanel.setVisible(false);
        viewportPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                viewportPanel.doLayout(); viewportPanel.repaint();
            }
        });

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

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_DARK);
        wrapper.add(layeredPane, BorderLayout.CENTER);
        return wrapper;
    }

    private void repositionNavButtons() {
        // console.log("### SelectiveAlphaEditor.java repositionNavButtons ###");
        if (prevNavButton == null) return;
        int h = layeredPane.getHeight(), bh = 80, bw = 36;
        int y = Math.max(0, (h - bh) / 2);
        prevNavButton.setBounds(8, y, bw, bh);
        nextNavButton.setBounds(layeredPane.getWidth() - bw - 8, y, bw, bh);
    }

    // ── Bottom area: status bar + paint toolbar (stacked) ───────────────────
    private JPanel buildBottomBar() {
        // console.log("### SelectiveAlphaEditor.java buildBottomBar ###");

        // ── Paint toolbar (hidden until paint mode) ───────────────────────────
        paintToolbar = new PaintToolbar(this, buildPaintCallbacks());

        // ── Status / action bar ───────────────────────────────────────────────
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

        actionPanel.add(applyButton);
        actionPanel.add(clearSelectionsButton);
        actionPanel.add(Box.createHorizontalStrut(8));
        actionPanel.add(resetButton);
        actionPanel.add(saveButton);
        statusBar.add(actionPanel, BorderLayout.WEST);

        statusLabel = new JLabel("Keine Datei geladen");
        statusLabel.setForeground(AppColors.TEXT_MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        statusBar.add(statusLabel, BorderLayout.EAST);

        // ── Wrapper: paint toolbar on top, status bar below ───────────────────
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_DARK);
        wrapper.add(paintToolbar, BorderLayout.NORTH);
        wrapper.add(statusBar,    BorderLayout.SOUTH);
        return wrapper;
    }

    // ── Drop hint ─────────────────────────────────────────────────────────────
    private JPanel buildDropHintPanel() {
        // console.log("### SelectiveAlphaEditor.java buildDropHintPanel ###");
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
                String s = "PNG · JPG · BMP · GIF   |   STRG+Rad = Zoom";
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
        // console.log("### SelectiveAlphaEditor.java setupDropTarget ###");
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
        // console.log("### SelectiveAlphaEditor.java handleFileDropped ###");
        if (hasUnsavedChanges && showUnsavedChangesDialog() == 0) saveImage();
        else if (showUnsavedChangesDialog() == 2 && hasUnsavedChanges) return;
        loadFile(file);
    }

    // =========================================================================
    // File loading
    // =========================================================================
    private void loadFile(File file) {
        // console.log("### SelectiveAlphaEditor.java loadFile ###");
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) { showErrorDialog("Ladefehler", "Bild konnte nicht gelesen werden:\n" + file.getName()); return; }
            originalImage     = img;
            workingImage      = deepCopy(originalImage);
            sourceFile        = file;
            hasUnsavedChanges = false;
            selectedAreas.clear();
            isSelecting = false; selectionStart = null; selectionEnd = null;
            lastPaintPoint = null; shapeStartPoint = null; paintSnapshot = null;

            indexDirectory(file);
            swapToImageView();
            fitToViewport();
            updateNavigationButtons();
            updateTitle();
            updateStatus();
            setBottomButtonsEnabled(true);
        } catch (IOException e) {
            showErrorDialog("Ladefehler", "Fehler:\n" + e.getMessage());
        }
    }

    private void indexDirectory(File file) {
        // console.log("### SelectiveAlphaEditor.java indexDirectory ###");
        File dir = file.getParentFile();
        if (dir == null) return;
        File[] files = dir.listFiles(f -> f.isFile() && isSupportedFile(f));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        directoryImages   = new ArrayList<>(Arrays.asList(files));
        currentImageIndex = directoryImages.indexOf(file);
    }

    private void navigateImage(int dir) {
        // console.log("### SelectiveAlphaEditor.java navigateImage ###");
        if (directoryImages.isEmpty()) return;
        int ni = currentImageIndex + dir;
        if (ni < 0 || ni >= directoryImages.size()) return;
        if (hasUnsavedChanges) { int r = showUnsavedChangesDialog(); if (r == 0) saveImage(); else if (r == 2) return; }
        currentImageIndex = ni;
        loadFile(directoryImages.get(currentImageIndex));
    }

    // =========================================================================
    // Zoom
    // =========================================================================
    private void setZoom(double nz) {
        // console.log("### SelectiveAlphaEditor.java setZoom ###");
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));
        if (canvasPanel != null) { canvasPanel.revalidate(); canvasPanel.repaint(); }
        if (viewportPanel != null) { viewportPanel.doLayout(); viewportPanel.repaint(); }
        updateZoomLabel();
    }

    private void fitToViewport() {
        // console.log("### SelectiveAlphaEditor.java fitToViewport ###");
        if (workingImage == null || viewportPanel == null) return;
        int vw = viewportPanel.getWidth(), vh = viewportPanel.getHeight();
        if (vw <= 0 || vh <= 0) { SwingUtilities.invokeLater(this::fitToViewport); return; }
        setZoom(Math.min((double)(vw - 80) / workingImage.getWidth(),
                         (double)(vh - 20) / workingImage.getHeight()));
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    // =========================================================================
    // Coordinate transform
    // =========================================================================
    private Point screenToImage(Point sp) {
        // console.log("### SelectiveAlphaEditor.java screenToImage ###");
        int ix = (int) Math.floor(sp.x / zoom);
        int iy = (int) Math.floor(sp.y / zoom);
        if (workingImage != null) {
            ix = Math.max(0, Math.min(workingImage.getWidth()  - 1, ix));
            iy = Math.max(0, Math.min(workingImage.getHeight() - 1, iy));
        }
        return new Point(ix, iy);
    }

    // =========================================================================
    // Alpha-editor operations (unchanged from prior version)
    // =========================================================================
    private void performFloodfill(Point screenPt) {
        // console.log("### SelectiveAlphaEditor.java performFloodfill ###");
        Point ip = screenToImage(screenPt);
        int tc = workingImage.getRGB(ip.x, ip.y);
        if (((tc >> 24) & 0xFF) == 0) { showInfoDialog("Bereits transparent", "Klicke auf eine sichtbare Farbe."); return; }
        PaintEngine.floodFill(workingImage, ip.x, ip.y, new Color(0,0,0,0), 30);
        markDirty();
    }

    private void applySelectionsToAlpha() {
        // console.log("### SelectiveAlphaEditor.java applySelectionsToAlpha ###");
        if (selectedAreas.isEmpty()) { showInfoDialog("Keine Auswahl", "Noch keine Bereiche ausgewählt."); return; }
        for (Rectangle r : selectedAreas) PaintEngine.clearRegion(workingImage, r);
        selectedAreas.clear();
        markDirty();
        showInfoDialog("Erledigt", "Ausgewählte Bereiche wurden transparent gemacht.");
    }

    private void clearSelections() {
        selectedAreas.clear(); canvasPanel.repaint();
    }

    private void resetImage() {
        workingImage = deepCopy(originalImage);
        selectedAreas.clear();
        hasUnsavedChanges = false;
        updateTitle();
        canvasPanel.repaint();
    }

    private void saveImage() {
        // console.log("### SelectiveAlphaEditor.java saveImage ###");
        if (sourceFile == null) return;
        try {
            String suffix  = (appMode == AppMode.PAINT) ? "_painted"
                    : floodfillMode ? "_floodfill_alpha" : "_selective_alpha";
            String outPath = WhiteToAlphaConverter.getOutputPath(sourceFile, suffix);
            File   outFile = new File(outPath);
            ImageIO.write(workingImage, "PNG", outFile);
            hasUnsavedChanges = false;
            updateTitle();
            showInfoDialog("Gespeichert", "Gespeichert als:\n" + outFile.getName());
        } catch (IOException e) { showErrorDialog("Speicherfehler", e.getMessage()); }
    }

    private void markDirty() {
        hasUnsavedChanges = true;
        updateTitle();
        canvasPanel.repaint();
    }

    // =========================================================================
    // Mode toggles
    // =========================================================================
    private void toggleAlphaMode() {
        // console.log("### SelectiveAlphaEditor.java toggleAlphaMode ###");
        if (appMode != AppMode.ALPHA_EDITOR) return;
        floodfillMode = !floodfillMode;
        modeLabel.setText("Modus: " + (floodfillMode ? "Floodfill" : "Selective Alpha"));
        boolean sel = !floodfillMode;
        applyButton.setEnabled(sel && sourceFile != null);
        clearSelectionsButton.setEnabled(sel && sourceFile != null);
        selectedAreas.clear(); canvasPanel.repaint();
    }

    private void togglePaintMode() {
        // console.log("### SelectiveAlphaEditor.java togglePaintMode ###");
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
    // PaintToolbar callbacks
    // =========================================================================
    private PaintToolbar.Callbacks buildPaintCallbacks() {
        // console.log("### SelectiveAlphaEditor.java buildPaintCallbacks ###");
        return new PaintToolbar.Callbacks() {
            @Override public void onToolChanged(PaintEngine.Tool tool) { canvasPanel.repaint(); }
            @Override public void onColorChanged(Color primary, Color secondary) {}
            @Override public void onStrokeChanged(int w) {}
            @Override public void onFillModeChanged(PaintEngine.FillMode m) {}
            @Override public void onBrushShapeChanged(PaintEngine.BrushShape s) {}
            @Override public void onCut()   { doCut(); }
            @Override public void onCopy()  { doCopy(); }
            @Override public void onPaste() { doPaste(); }
            @Override public void onToggleGrid(boolean show)  { showGrid  = show; canvasPanel.repaint(); }
            @Override public void onToggleRuler(boolean show) { showRuler = show; canvasPanel.repaint(); }
            @Override public BufferedImage getWorkingImage()  { return workingImage; }
        };
    }

    // =========================================================================
    // Clipboard operations
    // =========================================================================
    private void doCut() {
        // console.log("### SelectiveAlphaEditor.java doCut ###");
        if (workingImage == null) return;
        Rectangle sel = getActiveSelection();
        if (sel != null) {
            clipboard = PaintEngine.cropRegion(workingImage, sel);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(workingImage, sel);
            markDirty();
        } else {
            // Cut whole image
            clipboard = deepCopy(workingImage);
            copyToSystemClipboard(clipboard);
            PaintEngine.clearRegion(workingImage, new Rectangle(0, 0, workingImage.getWidth(), workingImage.getHeight()));
            markDirty();
        }
    }

    private void doCopy() {
        // console.log("### SelectiveAlphaEditor.java doCopy ###");
        if (workingImage == null) return;
        Rectangle sel = getActiveSelection();
        clipboard = (sel != null) ? PaintEngine.cropRegion(workingImage, sel) : deepCopy(workingImage);
        copyToSystemClipboard(clipboard);
    }

    private void doPaste() {
        // console.log("### SelectiveAlphaEditor.java doPaste ###");
        // Try system clipboard first
        BufferedImage fromClip = pasteFromSystemClipboard();
        if (fromClip != null) clipboard = fromClip;
        if (clipboard != null && workingImage != null) {
            pasteOffset = new Point(0, 0);
            PaintEngine.pasteRegion(workingImage, clipboard, pasteOffset);
            markDirty();
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
        // console.log("### SelectiveAlphaEditor.java copyToSystemClipboard ###");
        if (img == null) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new TransferableImage(img), null);
        } catch (Exception ignored) {}
    }

    private BufferedImage pasteFromSystemClipboard() {
        // console.log("### SelectiveAlphaEditor.java pasteFromSystemClipboard ###");
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                bi.createGraphics().drawImage(img, 0, 0, null);
                return bi;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Transferable wrapper for BufferedImage. */
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
        // console.log("### SelectiveAlphaEditor.java setupKeyBindings ###");
        JPanel root = (JPanel) getContentPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");

        am.put("copy",  new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { doCopy(); } });
        am.put("cut",   new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { doCut(); } });
        am.put("paste", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { doPaste(); } });
    }

    // =========================================================================
    // CanvasPanel
    // =========================================================================
    private class CanvasPanel extends JPanel {

        CanvasPanel() {
            // console.log("### SelectiveAlphaEditor.java CanvasPanel constructor ###");
            setOpaque(false);
            MouseAdapter handler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || sourceFile == null) return;
                    Point imgPt = screenToImage(e.getPoint());

                    if (appMode == AppMode.PAINT) {
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        switch (tool) {
                            case PENCIL, ERASER -> {
                                lastPaintPoint = imgPt;
                                paintDot(imgPt);
                            }
                            case FLOODFILL -> {
                                PaintEngine.floodFill(workingImage, imgPt.x, imgPt.y,
                                        paintToolbar.getPrimaryColor(), 30);
                                markDirty();
                            }
                            case EYEDROPPER -> {
                                Color picked = PaintEngine.pickColor(workingImage, imgPt.x, imgPt.y);
                                // Update toolbar color
                                paintToolbar.setSelectedColor(picked);
                            }
                            case LINE, CIRCLE, RECT, SELECT -> {
                                shapeStartPoint = imgPt;
                                paintSnapshot   = deepCopy(workingImage);
                            }
                        }
                    } else {
                        // Alpha editor
                        if (floodfillMode) {
                            performFloodfill(e.getPoint());
                        } else {
                            isSelecting    = true;
                            selectionStart = imgPt;
                            selectionEnd   = imgPt;
                        }
                    }
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (sourceFile == null) return;
                    Point imgPt = screenToImage(e.getPoint());

                    if (appMode == AppMode.PAINT) {
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        switch (tool) {
                            case PENCIL -> {
                                if (lastPaintPoint != null)
                                    PaintEngine.drawPencil(workingImage, lastPaintPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth(),
                                            paintToolbar.getBrushShape());
                                lastPaintPoint = imgPt;
                                markDirty();
                            }
                            case ERASER -> {
                                if (lastPaintPoint != null)
                                    PaintEngine.drawEraser(workingImage, lastPaintPoint, imgPt,
                                            paintToolbar.getStrokeWidth());
                                lastPaintPoint = imgPt;
                                markDirty();
                            }
                            case LINE -> {
                                if (shapeStartPoint != null && paintSnapshot != null) {
                                    // Restore snapshot then draw preview
                                    copyInto(paintSnapshot, workingImage);
                                    PaintEngine.drawLine(workingImage, shapeStartPoint, imgPt,
                                            paintToolbar.getPrimaryColor(),
                                            paintToolbar.getStrokeWidth());
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
                                            paintToolbar.getSecondaryColor());
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
                                            paintToolbar.getSecondaryColor());
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

                @Override public void mouseReleased(MouseEvent e) {
                    if (sourceFile == null) return;
                    Point imgPt = screenToImage(e.getPoint());

                    if (appMode == AppMode.PAINT) {
                        PaintEngine.Tool tool = paintToolbar.getActiveTool();
                        if (tool == PaintEngine.Tool.SELECT && shapeStartPoint != null) {
                            int x = Math.min(shapeStartPoint.x, imgPt.x);
                            int y = Math.min(shapeStartPoint.y, imgPt.y);
                            int w = Math.abs(imgPt.x - shapeStartPoint.x);
                            int h = Math.abs(imgPt.y - shapeStartPoint.y);
                            if (w > 2 && h > 2) { selectedAreas.clear(); selectedAreas.add(new Rectangle(x,y,w,h)); }
                        }
                        lastPaintPoint  = null;
                        shapeStartPoint = null;
                        paintSnapshot   = null;
                        markDirty();
                    } else {
                        if (!floodfillMode && isSelecting && SwingUtilities.isLeftMouseButton(e)) {
                            isSelecting = false;
                            if (selectionStart != null && selectionEnd != null) {
                                int x = Math.min(selectionStart.x, selectionEnd.x);
                                int y = Math.min(selectionStart.y, selectionEnd.y);
                                int w = Math.abs(selectionEnd.x - selectionStart.x);
                                int h = Math.abs(selectionEnd.y - selectionStart.y);
                                if (w > 2 && h > 2) selectedAreas.add(new Rectangle(x,y,w,h));
                            }
                            selectionStart = null; selectionEnd = null;
                            repaint();
                        }
                    }
                }

                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        setZoom(zoom + (-e.getPreciseWheelRotation() * ZOOM_STEP));
                        e.consume();
                    }
                }
            };
            addMouseListener(handler);
            addMouseMotionListener(handler);
            addMouseWheelListener(handler);
        }

        private void paintDot(Point imgPt) {
            PaintEngine.drawPencil(workingImage, imgPt, imgPt,
                    paintToolbar.getPrimaryColor(),
                    paintToolbar.getStrokeWidth(),
                    paintToolbar.getBrushShape());
            markDirty();
        }

        @Override public Dimension getPreferredSize() {
            if (workingImage == null) return new Dimension(1, 1);
            return new Dimension(
                    (int) Math.ceil(workingImage.getWidth()  * zoom),
                    (int) Math.ceil(workingImage.getHeight() * zoom));
        }

        @Override protected void paintComponent(Graphics g) {
            // console.log("### SelectiveAlphaEditor.java CanvasPanel paintComponent ###");
            super.paintComponent(g);
            if (workingImage == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    zoom >= 2.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int cw = (int) Math.ceil(workingImage.getWidth()  * zoom);
            int ch = (int) Math.ceil(workingImage.getHeight() * zoom);

            // Checkerboard
            int cell = Math.max(4, (int)(10 * zoom));
            for (int row = 0; row < ch; row += cell)
                for (int col = 0; col < cw; col += cell) {
                    boolean even = ((row/cell)+(col/cell)) % 2 == 0;
                    g2.setColor(even ? new Color(200,200,200) : new Color(160,160,160));
                    g2.fillRect(col, row, Math.min(cell, cw-col), Math.min(cell, ch-row));
                }

            // Image
            g2.drawImage(workingImage, 0, 0, cw, ch, null);

            // ── Grid ─────────────────────────────────────────────────────────
            if (showGrid) {
                g2.setColor(new Color(100, 100, 255, 60));
                g2.setStroke(new BasicStroke(0.5f));
                int gx = (int)(GRID_CELL * zoom);
                for (int x = 0; x < cw; x += gx) g2.drawLine(x, 0, x, ch);
                for (int y = 0; y < ch; y += gx) g2.drawLine(0, y, cw, y);
            }

            // ── Ruler ─────────────────────────────────────────────────────────
            if (showRuler) paintRuler(g2, cw, ch);

            // ── Selections ───────────────────────────────────────────────────
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

        private void paintRuler(Graphics2D g2, int cw, int ch) {
            // console.log("### SelectiveAlphaEditor.java paintRuler ###");
            int rulerH = 18;
            g2.setColor(new Color(40, 40, 40, 200));
            g2.fillRect(0, 0, cw, rulerH);
            g2.fillRect(0, 0, rulerH, ch);
            g2.setColor(new Color(180, 180, 180, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
            int step = (zoom >= 2) ? 10 : (zoom >= 1) ? 20 : 50;
            int stepPx = (int)(step * zoom);
            if (stepPx < 10) return;
            for (int px = 0; px < cw; px += stepPx) {
                int val = (int)(px / zoom);
                g2.setColor(new Color(180,180,180,180));
                g2.drawLine(px, rulerH - 5, px, rulerH);
                g2.drawLine(px, 0, px, rulerH / 2);
                if (px > 0) g2.drawString(String.valueOf(val), px + 2, 9);
            }
            for (int py = rulerH; py < ch; py += stepPx) {
                int val = (int)((py - rulerH) / zoom);
                g2.drawLine(0, py, rulerH/2, py);
                g2.drawLine(rulerH - 5, py, rulerH, py);
                if (py > rulerH + 6) {
                    Graphics2D gr = (Graphics2D) g2.create();
                    gr.translate(9, py - 2);
                    gr.rotate(-Math.PI / 2);
                    gr.drawString(String.valueOf(val), 0, 0);
                    gr.dispose();
                }
            }
        }

        private int toSx(int ix) { return (int) Math.round(ix * zoom); }
        private int toSy(int iy) { return (int) Math.round(iy * zoom); }
        private int toSw(int iw) { return (int) Math.round(iw * zoom); }
    }

    // =========================================================================
    // UI state helpers
    // =========================================================================
    private void swapToImageView() {
        // console.log("### SelectiveAlphaEditor.java swapToImageView ###");
        if (dropHintPanel.getParent() == layeredPane) layeredPane.remove(dropHintPanel);
        if (viewportPanel.getParent() == null) {
            int w = layeredPane.getWidth()  > 0 ? layeredPane.getWidth()  : 860;
            int h = layeredPane.getHeight() > 0 ? layeredPane.getHeight() : 560;
            viewportPanel.setBounds(0, 0, w, h);
            viewportPanel.add(canvasPanel);
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
        // console.log("### SelectiveAlphaEditor.java showUnsavedChangesDialog ###");
        if (!hasUnsavedChanges) return 1; // nothing to save → treat as discard
        final int[] result = { 2 };
        JDialog dialog = createBaseDialog("Ungespeicherte Änderungen", 420, 210);
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
        // console.log("### SelectiveAlphaEditor.java showErrorDialog ###");
        JDialog dialog = createBaseDialog(title, 440, 215);
        JPanel content = centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = htmlLabel(message.replace("\n","<br>"), AppColors.TEXT, 12);
        msgLbl.getAccessibleContext().setAccessibleDescription("Fehlermeldung: " + message);
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
        // console.log("### SelectiveAlphaEditor.java showInfoDialog ###");
        JDialog dialog = createBaseDialog(title, 400, 200);
        JPanel content = centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = htmlLabel(message.replace("\n","<br>"), AppColors.TEXT, 13);
        msgLbl.getAccessibleContext().setAccessibleDescription("Info: " + message);
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

    /** Copies all pixels from src into dst (must be same dimensions). */
    private void copyInto(BufferedImage src, BufferedImage dst) {
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
    }
}
