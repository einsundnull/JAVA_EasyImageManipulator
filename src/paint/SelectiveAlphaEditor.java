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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Main application window.
 *
 * Modes: - Alpha Editor (Selective Selection + Floodfill) - Paint Mode (full
 * MS-Paint-style toolbar via PaintToolbar)
 *
 * Navigation: - CTRL + Mouse Wheel → Zoom (toward cursor) - Mouse Wheel →
 * Vertical scroll - SHIFT + Wheel → Horizontal scroll - Middle Mouse Drag → Pan
 * - CTRL + Left Drag → Pan
 *
 * Ruler: - Drawn OUTSIDE the image in dedicated panels (H top, V left) -
 * Configurable unit: px / mm / cm / inch
 */
public class SelectiveAlphaEditor extends JFrame implements RulerCallbacks {

	// ── Constants ─────────────────────────────────────────────────────────────
	private static final String[] SUPPORTED_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
	private static final int MAX_UNDO = 50;
	private boolean[] canvasWasVisible = new boolean[2]; // Track canvas visibility for transitions

	// Zoom settings (non-final for runtime adjustment)
	double ZOOM_MIN = 0.05;
	double ZOOM_MAX = 16.0; // max: 16x16 pixels
	double ZOOM_STEP = 0.10;
	double ZOOM_FACTOR = 1.08; // progressive zoom: 8% per notch

	private static final int GRID_CELL = 16; // image-space pixels per grid cell
	private static final int RULER_THICK = 20; // pixels wide/tall for ruler strip
	private static final double SCREEN_DPI = 96.0;

	private static final int TOPBAR_BTN_W = 36;
	private static final int TOPBAR_BTN_H = 36;
	private static final int TOPBAR_ZOOM_BTN_W = 36;
	private static final int TOPBAR_ZOOM_BTN_H = 36;

	// ── Gallery shrinking behavior ─────────────────────────────────────────────
	// true → galleries shrink when both canvases shown, tiles scale to fit
	// false → canvases shrink to preserve gallery widths
	private static final boolean SHRINK_GALLERY = true;

	// ── Ruler unit ────────────────────────────────────────────────────────────
	// RulerUnit is now defined in RulerUnit.java (extracted as separate enum)

	// ── Canvas array (multiple independent canvases) ────────────────────────────
	final CanvasInstance[] canvases = new CanvasInstance[2];
	int activeCanvasIndex = 0;

	// Convenience accessors
	CanvasInstance ci() {
		return canvases[activeCanvasIndex];
	}

	CanvasInstance ci(int idx) {
		return canvases[idx];
	}

	// ── Shared global state (not per-canvas) ───────────────────────────────────
	BufferedImage clipboard;
	List<Layer> clipboardLayers; // For copying/pasting layers between canvases
	Point pasteOffset;

	/** Default appMode applied to new canvases on first load (from settings). */
	AppMode defaultAppMode = AppMode.ALPHA_EDITOR;
	boolean floodfillMode = false;
	boolean alphaPaintMode = false; // true = Pinsel-basiertes Alpha-Malen
	// showGrid is now per-canvas: ci(idx).showGrid
	boolean showRuler = false;
	RulerUnit rulerUnit = RulerUnit.PX;

	// ── File cache (images stay alive while navigating, dirty until saved) ────
	/** Files with unsaved changes (shown red in gallery). */
	final Set<File> dirtyFiles = new HashSet<>();

	// ── Directory browsing (gallery multiselect) ──────────────────────────────
	List<File> selectedImages = new ArrayList<>();

	// ── Project Management ────────────────────────────────────────────────────
	ProjectManager projectManager = new ProjectManager();


	// ── Controllers (Modularization) ─────────────────────────────────────────
	private final ClipboardController clipboardController = new ClipboardController(this);
	private final SecondaryWindowController secWinController = new SecondaryWindowController(this);
	final FileLoadController fileLoader = new FileLoadController(this);
	private final ModeController modeController = new ModeController(this);
	private final TransformController transformController = new TransformController(this);
	final ScenesController scenesController = new ScenesController(this);
	private final NewFileController newFileController = new NewFileController(this);
	final ElementController elementController = new ElementController(this);
	private final PreloadController preloadController = new PreloadController(this);
	final DropController dropController = new DropController(this);
	private final SaveController saveController = new SaveController(this);
	final ElementEditController elementEditController = new ElementEditController(this);
	private final QuickOpenController quickOpenController = new QuickOpenController(this);
	final EditorDialogs editorDialogs = new EditorDialogs(this);
	final ZoomController zoomController = new ZoomController(this);

	// ── Secondary Canvas Window (F1/F2/F3/F4/F5/F7) ──────────────────────────────
	// PreviewMode, AlwaysOnTopMode, CanvasDisplayMode → eigene Dateien

	JFrame secWin;
	SecondaryPanel secPanel;
	PreviewMode secMode = PreviewMode.LIVE_ALL;
	CanvasDisplayMode secCanvasMode = CanvasDisplayMode.SHOW_CANVAS_I_ONLY;
	BufferedImage secSnapshot;
	javax.swing.Timer secTimer;
	boolean secFullscreen = true;
	AlwaysOnTopMode secAlwaysOnTop = AlwaysOnTopMode.TO_BACKGROUND;
	int secOldX, secOldY, secOldW, secOldH; // For fullscreen restoration

	// ── Element layers ────────────────────────────────────────────────────────
	// All per-canvas element state now in CanvasInstance

	// ── Canvas background ─────────────────────────────────────────────────────
	Color canvasBg1 = new Color(200, 200, 200);
	Color canvasBg2 = new Color(160, 160, 160);
	Color canvasBg1Backup = null; // for QuickBG toggle

	// ── Filmstrip sidebar + toggles ────────────────────────────────────────────
	JPanel galleryWrapper;
	private JToggleButton filmstripBtn;
	JToggleButton scenesBtn;
	JToggleButton secondCanvasBtn;
	JToggleButton secondGalleryBtn;
	JToggleButton secondScenesBtn;

	// ── Element layer panels (shown in Canvas mode) ──────────────────────────
	ElementLayerPanel elementLayerPanel; // For canvas 0
	ElementLayerPanel elementLayerPanel2; // For canvas 1
	JToggleButton firstElementsBtn; // Toggle for elementLayerPanel
	JToggleButton secondElementsBtn; // Toggle for elementLayerPanel2

	// ── Maps panel (toggle-able list view) ─────────────────────────────────────
	MapsPanel mapsPanel;
	private JToggleButton mapsBtn; // Toggle for mapsPanel

	// ── Quick open and drop zone toggle ────────────────────────────────────────
	private JButton quickOpenBtn; // Quick open recent projects
	private JToggleButton toggleDropZoneBtn; // Toggle drop zone visibility for canvas 2

	// ── Shared UI components ───────────────────────────────────────────────────
	HRulerPanel hRuler;
	VRulerPanel vRuler;
	private JPanel rulerCorner;
	private JPanel rulerNorthBar; // container for rulerCorner + hRuler
	private JPanel actionPanel; // Holds apply/clear/reset/save buttons
	JPanel rightDropZone; // drag-activation overlay
	JToggleButton firstCanvasBtn; // Toggle for ci(0).layeredPane visibility
	private JPanel mainDividerPanel; // Thin vertical separator between Canvas 1 and 2

	private JLabel statusLabel;
	JLabel modeLabel;
	JLabel zoomLabel;
	JButton applyButton;
	JButton clearSelectionsButton;
	private JButton prevNavButton;
	private JButton nextNavButton;
	JToggleButton paintModeBtn;
	JToggleButton canvasModeBtn;
	JToggleButton bookModeBtn;
	JToggleButton sceneModeBtn;

	PaintToolbar paintToolbar;

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

		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
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
			ci(0).showGrid = settings.isShowGrid(); ci(1).showGrid = settings.isShowGrid();
			showRuler = settings.isShowRuler();
			rulerUnit = RulerUnit.valueOf(settings.getRulerUnit());

			// Zoom-Einstellungen
			ZOOM_MIN = settings.getZoomMin();
			ZOOM_MAX = settings.getZoomMax();
			ZOOM_STEP = settings.getZoomStep();
			ZOOM_FACTOR = settings.getZoomFactor();

			// App-Modus
			try {
				defaultAppMode = AppMode.valueOf(settings.getAppMode());
			} catch (IllegalArgumentException e) {
				defaultAppMode = AppMode.ALPHA_EDITOR;
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
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				onApplicationClosing();
			}
		});

		addWindowStateListener((WindowEvent e) -> {
			boolean wasMax = (e.getOldState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
			boolean isMax = (e.getNewState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
			if (wasMax != isMax && ci(0).workingImage != null) {
				SwingUtilities.invokeLater(() -> {
					if (!ci(0).userHasManuallyZoomed) {
						fitToViewport(0);
					} else {
						centerCanvasX(0);
					}
					if (ci(0).canvasPanel != null)
						ci(0).canvasPanel.repaint();
				});
			}
		});

		secWinController.initSecondaryWindow();

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
						// Show TileGalleryPanel when opening from StartDialog (not ScenesPanel)
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
				projectManager.saveScene(ci(0).sourceFile, ci(0).activeElements, ci(0).zoom, ci(0).appMode,
						ci(0).workingImage.getWidth(), ci(0).workingImage.getHeight());
			}

			// Speichere globale Einstellungen
			AppSettings settings = AppSettings.getInstance();
			settings.setBg1(canvasBg1.getRGB());
			settings.setBg2(canvasBg2.getRGB());
			settings.setShowGrid(ci().showGrid);
			settings.setShowRuler(showRuler);
			settings.setRulerUnit(rulerUnit.toString());
			settings.setAppMode(ci().appMode.toString());
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

		// ── Construct all field-buttons (needed by other methods) ──────────────
		// These are assigned to fields but only some are added to the bar.

		// SI – Scenes I
		scenesBtn = UIComponentFactory.buildModeToggleBtn("SI", "Szenen I ein-/ausblenden");
		scenesBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		scenesBtn.setSelected(false);
		scenesBtn.addActionListener(e -> setScenesPanelVisible(0, scenesBtn.isSelected()));

		// II – Images I (filmstrip)
		filmstripBtn = UIComponentFactory.buildModeToggleBtn("II", "Bilder I ein-/ausblenden");
		filmstripBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		filmstripBtn.setSelected(true);
		filmstripBtn.addActionListener(e -> {
			ci(0).tileGallery.setVisible(filmstripBtn.isSelected());
			updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(0)));
		});

		// EI – Elements I
		firstElementsBtn = UIComponentFactory.buildModeToggleBtn("EI", "Ebenen I ein-/ausblenden");
		firstElementsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		firstElementsBtn.setSelected(false);
		firstElementsBtn.setEnabled(false);
		firstElementsBtn.addActionListener(e -> {
			if (elementLayerPanel != null) elementLayerPanel.setVisible(firstElementsBtn.isSelected());
			updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(activeCanvasIndex)));
		});

		// FI – Open folder → Canvas I
		quickOpenBtn = UIComponentFactory.buildButton("\uD83D\uDCC2 I", AppColors.BTN_BG, AppColors.BTN_HOVER);
		quickOpenBtn.setToolTipText("Ordner öffnen / Recent Projekte (Canvas I)");
		quickOpenBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		quickOpenBtn.setForeground(AppColors.TEXT);
		quickOpenBtn.addActionListener(e -> showQuickOpenDialog(0));

		// Canvas buttons (fields required by other code, not shown in bar)
		firstCanvasBtn = UIComponentFactory.buildModeToggleBtn("1", "1. Canvas ein-/ausblenden");
		firstCanvasBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		firstCanvasBtn.setSelected(true);
		firstCanvasBtn.setEnabled(false);
		firstCanvasBtn.addActionListener(e -> updateLayoutVisibility());

		secondCanvasBtn = UIComponentFactory.buildModeToggleBtn("2", "2. Canvas ein-/ausblenden");
		secondCanvasBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		secondCanvasBtn.setSelected(false);
		secondCanvasBtn.setEnabled(false);
		secondCanvasBtn.addActionListener(e -> updateLayoutVisibility());

		// III – Images II
		secondGalleryBtn = UIComponentFactory.buildModeToggleBtn("III", "Bilder II ein-/ausblenden");
		secondGalleryBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		secondGalleryBtn.setSelected(false);
		secondGalleryBtn.setEnabled(false);
		secondGalleryBtn.addActionListener(e -> {
			if (ci(1).tileGallery != null) ci(1).tileGallery.setVisible(secondGalleryBtn.isSelected());
			updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(1)));
		});

		// SII – Scenes II
		secondScenesBtn = UIComponentFactory.buildModeToggleBtn("SII", "Szenen II ein-/ausblenden");
		secondScenesBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		secondScenesBtn.setSelected(false);
		secondScenesBtn.setEnabled(true);
		secondScenesBtn.addActionListener(e -> setScenesPanelVisible(1, secondScenesBtn.isSelected()));

		// EII – Elements II
		secondElementsBtn = UIComponentFactory.buildModeToggleBtn("EII", "Ebenen II ein-/ausblenden");
		secondElementsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		secondElementsBtn.setSelected(false);
		secondElementsBtn.setEnabled(false);
		secondElementsBtn.addActionListener(e -> {
			if (elementLayerPanel2 != null) elementLayerPanel2.setVisible(secondElementsBtn.isSelected());
			updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(activeCanvasIndex)));
		});

		// mapsBtn – not shown, keep as field for other code
		mapsBtn = UIComponentFactory.buildModeToggleBtn("M", "Maps ein-/ausblenden");
		mapsBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		mapsBtn.setSelected(false);
		mapsBtn.addActionListener(e -> {
			if (mapsPanel != null) mapsPanel.setVisible(mapsBtn.isSelected());
			updateLayoutVisibility();
			SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> reloadCurrentImage(activeCanvasIndex)));
		});

		// toggleDropZoneBtn – not shown, keep as field
		toggleDropZoneBtn = UIComponentFactory.buildModeToggleBtn("\u2193", "Drop-Feld");
		toggleDropZoneBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		toggleDropZoneBtn.setSelected(false);
		toggleDropZoneBtn.addActionListener(e -> {
			if (rightDropZone != null) rightDropZone.setVisible(toggleDropZoneBtn.isSelected());
		});

		// applyButton / clearSelectionsButton – fields required by setBottomButtonsEnabled()
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

		// actionPanel – field required by setBottomButtonsEnabled()
		actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actionPanel.setOpaque(false);

		// RL – Reload (resetButton field)
		JButton resetButton = UIComponentFactory.buildButton("RL", AppColors.BTN_BG, AppColors.BTN_HOVER);
		resetButton.setName("resetButton");
		resetButton.setToolTipText("Bild neu laden / zurücksetzen");
		resetButton.addActionListener(e -> resetImage());
		resetButton.setEnabled(false);
		resetButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

		// SV – Save (saveButton field)
		JButton saveButton = UIComponentFactory.buildButton("SV", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
		saveButton.setName("saveButton");
		saveButton.setForeground(Color.WHITE);
		saveButton.setToolTipText("Bild speichern (STRG+S)");
		saveButton.addActionListener(e -> saveImage());
		saveButton.setEnabled(false);
		saveButton.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

		actionPanel.add(resetButton);
		actionPanel.add(saveButton);

		// CM – Canvas Mode
		canvasModeBtn = UIComponentFactory.buildModeToggleBtn("CM",
				"Canvas-Modus: Layer-Verwaltung (STRG+A = Alle auswählen)");
		canvasModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		canvasModeBtn.setEnabled(false);
		canvasModeBtn.addActionListener(e -> toggleCanvasMode());

		// PT – Paint toolbar
		paintModeBtn = UIComponentFactory.buildModeToggleBtn("PT", "Paint-Leiste ein-/ausblenden");
		paintModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		paintModeBtn.addActionListener(e -> togglePaintMode());

		// BK – Book mode
		bookModeBtn = UIComponentFactory.buildModeToggleBtn("BK", "Buch-Modus");
		bookModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		bookModeBtn.addActionListener(e -> toggleBookMode());

		// sceneModeBtn – not shown, keep as field
		sceneModeBtn = UIComponentFactory.buildModeToggleBtn("SC", "Szenen-Modus");
		sceneModeBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		sceneModeBtn.addActionListener(e -> toggleSceneMode());

		// modeLabel / statusLabel – fields used elsewhere, not shown in bar
		modeLabel = new JLabel("");
		modeLabel.setForeground(AppColors.TEXT_MUTED);
		modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		statusLabel = new JLabel("");
		statusLabel.setForeground(AppColors.TEXT_MUTED);
		statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

		// ── Zoom controls ──────────────────────────────────────────────────────
		JButton zoomInBtn  = UIComponentFactory.buildButton("+",      AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton zoomOutBtn = UIComponentFactory.buildButton("\u2212", AppColors.BTN_BG, AppColors.BTN_HOVER);
		JButton zoomFitBtn = UIComponentFactory.buildButton("Fit",    AppColors.BTN_BG, AppColors.BTN_HOVER);
		// 1:1 button exists but is not shown per spec
		JButton zoomResetBtn = UIComponentFactory.buildButton("1:1",  AppColors.BTN_BG, AppColors.BTN_HOVER);

		zoomLabel = new JLabel("100%");
		zoomLabel.setForeground(AppColors.TEXT_MUTED);
		zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		zoomLabel.setPreferredSize(new Dimension(46, 20));
		zoomLabel.setHorizontalAlignment(JLabel.CENTER);
		zoomLabel.setToolTipText("Doppelklick: Zoom eingeben");
		zoomLabel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		zoomLabel.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) showZoomInput();
			}
		});

		zoomInBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
		zoomOutBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
		zoomFitBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
		zoomResetBtn.setPreferredSize(new Dimension(TOPBAR_ZOOM_BTN_W, TOPBAR_ZOOM_BTN_H));
		zoomInBtn.addActionListener(e  -> setZoom(ci().zoom + ZOOM_STEP, null));
		zoomOutBtn.addActionListener(e -> setZoom(ci().zoom - ZOOM_STEP, null));
		zoomResetBtn.addActionListener(e -> setZoom(1.0, null));
		zoomFitBtn.addActionListener(e  -> fitToViewport());

		// BG – Background color
		JButton bgColorBtn = UIComponentFactory.buildButton("BG", AppColors.BTN_BG, AppColors.BTN_HOVER);
		bgColorBtn.setToolTipText("Canvas-Hintergrundfarbe");
		bgColorBtn.addActionListener(e -> showCanvasBgDialog());
		bgColorBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

		// Qu – Quick BG toggle
		JButton quickBgBtn = UIComponentFactory.buildButton("Qu", AppColors.BTN_BG, AppColors.BTN_HOVER);
		quickBgBtn.setToolTipText("BG Color temporär aus-/einblenden");
		quickBgBtn.addActionListener(e -> toggleQuickBG());
		quickBgBtn.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));

		// FII – Open folder → Canvas II
		JButton openFolderII = UIComponentFactory.buildButton("\uD83D\uDCC2 II", AppColors.BTN_BG, AppColors.BTN_HOVER);
		openFolderII.setToolTipText("Ordner öffnen (Canvas II)");
		openFolderII.setForeground(AppColors.TEXT);
		openFolderII.setPreferredSize(new Dimension(TOPBAR_BTN_W, TOPBAR_BTN_H));
		openFolderII.addActionListener(e -> showQuickOpenDialog(1));

		// ── Assemble bar ───────────────────────────────────────────────────────
		// LEFT group: SI | II | EI | FI | [gap]
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 5));
		left.setOpaque(false);
		left.add(scenesBtn);
		left.add(filmstripBtn);
		left.add(firstElementsBtn);
		left.add(quickOpenBtn);
		left.add(Box.createHorizontalStrut(12));
		bar.add(left, BorderLayout.WEST);

		// CENTER group: + | 66% | - | Fit | BG | Qu | RL | SV | CM | PT | BK
		JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 5));
		center.setOpaque(false);
		center.add(zoomInBtn);
		center.add(zoomLabel);
		center.add(zoomOutBtn);
		center.add(zoomFitBtn);
		center.add(Box.createHorizontalStrut(6));
		center.add(bgColorBtn);
		center.add(quickBgBtn);
		center.add(Box.createHorizontalStrut(6));
		center.add(resetButton);
		center.add(saveButton);
		center.add(Box.createHorizontalStrut(6));
		center.add(canvasModeBtn);
		center.add(paintModeBtn);
		center.add(bookModeBtn);
		bar.add(center, BorderLayout.CENTER);

		// RIGHT group: [gap] | FII | EII | III | SII
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 5));
		right.setOpaque(false);
		right.add(Box.createHorizontalStrut(12));
		right.add(openFolderII);
		right.add(secondElementsBtn);
		right.add(secondGalleryBtn);
		right.add(secondScenesBtn);
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
		hRuler = new HRulerPanel(this);
		vRuler = new VRulerPanel(this);
		rulerCorner = new JPanel();
		rulerCorner.setBackground(new Color(50, 50, 50));
		rulerCorner.setPreferredSize(new Dimension(RULER_THICK, RULER_THICK));
		rulerCorner.setOpaque(true);

		rulerNorthBar = new JPanel(new BorderLayout());
		rulerNorthBar.setOpaque(false);
		rulerNorthBar.add(rulerCorner, BorderLayout.WEST);
		rulerNorthBar.add(hRuler, BorderLayout.CENTER);

		// Element layer panels (both initially hidden)
		elementLayerPanel = new ElementLayerPanel(buildElementLayerCallbacks(0));
		elementLayerPanel.setVisible(false); // Hide initially, show only in Canvas mode
		elementLayerPanel2 = new ElementLayerPanel(buildElementLayerCallbacks(1));
		elementLayerPanel2.setVisible(false); // Hide until canvas 2 has content

		mapsPanel = new MapsPanel(new MapsPanel.Callbacks() {
			@Override
			public void onMapSelected(TranslationMap map) {
				// TODO: implement map viewing
			}

			@Override
			public void onMapDeleted(String language, String mapId) {
				// Map is already deleted, just refresh
			}

			@Override
			public void onMapEdited(TranslationMap oldMap, TranslationMap newMap) {
				// Map is already saved, just refresh
			}
		});
		mapsPanel.setVisible(false); // Hide initially
		mapsPanel.setPreferredSize(new Dimension(250, 400));
		mapsPanel.setMaximumSize(new Dimension(250, Integer.MAX_VALUE));

		// Canvas drawing areas: flexible width (take all available space)
		ci(0).layeredPane.setMinimumSize(new Dimension(0, 0));
		ci(0).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		ci(0).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		ci(1).layeredPane.setMinimumSize(new Dimension(0, 0));
		ci(1).layeredPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		ci(1).layeredPane.setVisible(false); // Hidden until canvas 2 is loaded
		ci(1).layeredPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		// Gallery panels: fixed width (no growth beyond preferred)
		// NOTE: When SHRINK_GALLERY = false, remove setMaximumSize() calls below
		// so galleries keep full width and canvases shrink instead
		ci(0).scenesPanel.setVisible(false); // Start HIDDEN - only open by user action or Recent dialog
		if (SHRINK_GALLERY) {
			ci(0).scenesPanel.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		}
		ci(0).scenesPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

		ci(0).tileGallery.setVisible(true); // Start VISIBLE
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

		ci(1).scenesPanel.setVisible(false); // Start hidden until Canvas 2 is activated
		if (SHRINK_GALLERY) {
			ci(1).scenesPanel.setMaximumSize(new Dimension(TileGalleryPanel.GALLERY_W, Integer.MAX_VALUE));
		}
		ci(1).scenesPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

		ci(1).tileGallery.setVisible(false); // Start hidden until Canvas 2 is activated
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
		mainDividerPanel.setVisible(false); // Only visible when both canvases are shown

		// Right drop zone overlay (for dragging tiles to activate second canvas)
		rightDropZone = buildRightDropZone();
		ci(0).layeredPane.add(rightDropZone, JLayeredPane.PALETTE_LAYER);
		rightDropZone.setVisible(false);

		// Gallery wrapper: BoxLayout X_AXIS — invisible components take NO space
		galleryWrapper = new JPanel();
		galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.X_AXIS));
		galleryWrapper.setBackground(AppColors.BG_DARK);

		// Order: Scenes1 | Gallery1 | Elements1 | Canvas1 | Divider | Canvas2 |
		// Elements2 | Gallery2 | Scenes2 | Maps
		galleryWrapper.add(ci(0).scenesPanel);
		galleryWrapper.add(ci(0).tileGallery);
		galleryWrapper.add(elementLayerPanel);
		galleryWrapper.add(ci(0).layeredPane);
		galleryWrapper.add(mainDividerPanel);
		galleryWrapper.add(ci(1).layeredPane);
		galleryWrapper.add(elementLayerPanel2);
		galleryWrapper.add(ci(1).tileGallery);
		galleryWrapper.add(ci(1).scenesPanel);
		galleryWrapper.add(mapsPanel);

		// Synchronize initial visibility with button states
		updateLayoutVisibility();

		// Auto-fit canvas when layout changes (panels hidden, divider moved, etc.)
		galleryWrapper.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
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
	void buildRulerLayout() {
		// Remove the stable containers from canvas 0 viewportPanel
		ci(0).viewportPanel.remove(rulerNorthBar);
		ci(0).viewportPanel.remove(vRuler);

		if (showRuler) {
			ci(0).viewportPanel.add(rulerNorthBar, BorderLayout.NORTH);
			ci(0).viewportPanel.add(vRuler, BorderLayout.WEST);
		}
		ci(0).viewportPanel.revalidate();
		ci(0).viewportPanel.repaint();
	}

	void repositionNavButtons(int idx) {
		CanvasInstance c = ci(idx);
		if (c.prevNavButton == null)
			return;
		int h = c.layeredPane.getHeight(), bh = 80, bw = 36;
		int y = Math.max(0, (h - bh) / 2);
		c.prevNavButton.setBounds(8, y, bw, bh);
		c.nextNavButton.setBounds(c.layeredPane.getWidth() - bw - 8, y, bw, bh);
	}

	/**
	 * Updates focus borders and element panel: active canvas gets green border only
	 * when second canvas is visible.
	 */
	void updateCanvasFocusBorder() {
		// Only show focus border if both canvas drawing areas are visible
		boolean showBorder = secondCanvasBtn.isEnabled() && firstCanvasBtn.isSelected() && secondCanvasBtn.isSelected();

		for (int i = 0; i < 2; i++) {
			CanvasInstance c = ci(i);
			if (c.layeredPane == null)
				continue;
			if (c.viewportPanel == null)
				continue;

			if (showBorder && i == activeCanvasIndex) {
				// Active canvas (with second canvas visible): green border on both viewport and
				// layered pane
				javax.swing.border.Border greenBorder = new javax.swing.border.LineBorder(new Color(0, 220, 0), 3,
						false);
				c.viewportPanel.setBorder(greenBorder);
				c.layeredPane.setBorder(greenBorder);
			} else {
				// Inactive or no focus needed: no border
				c.viewportPanel.setBorder(null);
				c.layeredPane.setBorder(null);
			}
		}

		// Sync toolbar buttons to reflect the newly active canvas's mode
		boolean isPaint = ci().appMode == AppMode.PAINT;
		paintModeBtn.setSelected(isPaint);
		canvasModeBtn.setEnabled(isPaint);
		updateModeLabel();

		// Update element panels
		refreshElementPanel();
	}

	/** Updates visibility of all 7 layout elements independently. */
	void updateLayoutVisibility() {
		// Canvas drawing areas follow their buttons
		if (ci(0).layeredPane != null) {
			boolean show0 = firstCanvasBtn.isSelected();
			ci(0).layeredPane.setVisible(show0);
			// Only sync viewportPanel visibility if it has content
			if (ci(0).viewportPanel != null && ci(0).workingImage != null) {
				ci(0).viewportPanel.setVisible(show0);
				// Only reload when transitioning from hidden to visible
				if (show0 && !canvasWasVisible[0] && ci(0).sourceFile != null) {
					SwingUtilities.invokeLater(() -> loadFile(ci(0).sourceFile, 0));
				}
			}
			canvasWasVisible[0] = show0;
		}
		if (ci(1).layeredPane != null) {
			boolean show1 = secondCanvasBtn.isSelected();
			ci(1).layeredPane.setVisible(show1);
			// Only sync viewportPanel visibility if it has content
			if (ci(1).viewportPanel != null && ci(1).workingImage != null) {
				ci(1).viewportPanel.setVisible(show1);
				// Only reload when transitioning from hidden to visible
				if (show1 && !canvasWasVisible[1] && ci(1).sourceFile != null) {
					SwingUtilities.invokeLater(() -> loadFile(ci(1).sourceFile, 1));
				}
			}
			canvasWasVisible[1] = show1;
		}

		// Divider only visible when both canvas drawing areas are shown
		if (mainDividerPanel != null)
			mainDividerPanel.setVisible(firstCanvasBtn.isSelected() && secondCanvasBtn.isSelected());

		updateCanvasFocusBorder();
		if (galleryWrapper != null) {
			galleryWrapper.revalidate();
			galleryWrapper.repaint();
		}
	}

	// ── Build canvas area (for index 0 or 1) ──────────────────────────────────
	private void buildCanvasArea(int idx) {
		CanvasInstance c = canvases[idx];

		// Canvas panel with callbacks
		c.canvasPanel = new CanvasPanel(buildCanvasCallbacks(idx));

		// Canvas wrapper (null-layout for absolute positioning) with centering
		c.canvasWrapper = new JPanel(null) {
			@Override
			public Dimension getPreferredSize() {
				if (c.workingImage == null)
					return new Dimension(1, 1);
				int cw = (int) Math.ceil(c.workingImage.getWidth() * c.zoom);
				int ch = (int) Math.ceil(c.workingImage.getHeight() * c.zoom);
				Dimension vd = c.scrollPane != null ? c.scrollPane.getViewport().getSize() : new Dimension(cw, ch);
				return new Dimension(Math.max(cw, vd.width), Math.max(ch, vd.height));
			}

			@Override
			public void doLayout() {
				if (c.canvasPanel == null)
					return;
				Dimension cs = c.canvasPanel.getPreferredSize();
				Dimension ws = getSize();
				int x = Math.max(0, (ws.width - cs.width) / 2);
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
			if (showRuler && idx == 0) {
				hRuler.repaint();
				vRuler.repaint();
			}
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
			@Override
			public void componentShown(ComponentEvent e) {
				SwingUtilities.invokeLater(() -> {
					Dimension vd = c.scrollPane.getViewport().getSize();
					if (vd.width > 0 && vd.height > 0 && c.workingImage != null && !c.userHasManuallyZoomed) {
						fitToViewport(idx);
					}
				});
			}

			@Override
			public void componentResized(ComponentEvent e) {
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
		c.elementEditBar = elementEditController.buildElementEditBar(idx);
		c.elementEditBar.setVisible(false);
		c.layeredPane.add(c.elementEditBar, JLayeredPane.MODAL_LAYER);

		// Component listener for resizing
		c.layeredPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				int w = c.layeredPane.getWidth(), h = c.layeredPane.getHeight();
				c.dropHintPanel.setBounds(0, 0, w, h);
				if (c.viewportPanel.getParent() == c.layeredPane)
					c.viewportPanel.setBounds(0, 0, w, h);
				repositionNavButtons(idx);
				elementEditController.repositionElementEditBar(idx);
				if (idx == 0)
					repositionRightDropZone();

				// Re-fit when canvas size changes (e.g., split pane divider moved)
				if (c.workingImage != null && !c.userHasManuallyZoomed && c.viewportPanel.isVisible()) {
					SwingUtilities.invokeLater(() -> fitToViewport(idx));
				}
			}
		});

		// Focus listener: clicking activates this canvas
		c.canvasPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (activeCanvasIndex != idx)
					elementEditController.resetElementDragState(activeCanvasIndex);
				activeCanvasIndex = idx;
				updateCanvasFocusBorder();
			}
		});

		// Tile gallery for this canvas
		c.tileGallery = new TileGalleryPanel(buildGalleryCallbacks(idx), buildGalleryPreloadCallback(idx));
		c.tileGallery.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (activeCanvasIndex != idx)
					elementEditController.resetElementDragState(activeCanvasIndex);
				activeCanvasIndex = idx;
				updateCanvasFocusBorder();
			}
		});

		// Load last image directory
		try {
			List<String> recentImages = LastProjectsManager.load(LastProjectsManager.CAT_IMAGES);
			if (!recentImages.isEmpty()) {
				File lastDir = new File(recentImages.get(0));
				if (lastDir.exists() && lastDir.isDirectory()) {
					File[] files = lastDir.listFiles(f -> f.isFile() && isSupportedFile(f));
					if (files != null && files.length > 0) {
						java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
						c.directoryImages = new java.util.ArrayList<>(java.util.Arrays.asList(files));
						c.lastIndexedDir = lastDir;
						c.tileGallery.setFiles(c.directoryImages, files[0]);
					}
				}
			}
		} catch (IOException ex) {
			System.out.println("[INFO] Keine letzten Bilder gefunden: " + ex.getMessage());
		}

		// Scenes panel for this canvas (exact TileGalleryPanel instance, populated from SceneLocator)
		c.scenesPanel = new TileGalleryPanel(buildScenesCallbacks(idx), null, "Szenen",
				() -> setScenesPanelVisible(idx, false),
				() -> refreshSceneFiles(idx));
		c.scenesPanel.setFileDropOverride(files -> createSceneFromDrop(files, idx));
		c.scenesPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (activeCanvasIndex != idx)
					elementEditController.resetElementDragState(activeCanvasIndex);
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
			@Override
			protected void paintComponent(Graphics g) {
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

			{
				setBackground(AppColors.BG_DARK);
			}
		};
	}

	// =========================================================================
	// Drag & Drop
	// =========================================================================
	void setupDropTarget(java.awt.Component target, int idx) {
		dropController.setupDropTarget(target, idx);
	}

	/**
	 * Creates a copy of {@code src} with a new ID. Returns null for unsupported
	 * types.
	 */
	Layer copyLayerWithNewId(Layer src, int newId) {
		if (src instanceof ImageLayer il) {
			BufferedImage normalized = normalizeImage(il.image());
			return new ImageLayer(newId, normalized, il.x(), il.y(), normalized.getWidth(), normalized.getHeight());
		} else if (src instanceof TextLayer tl) {
			return TextLayer.of(newId, tl.text(), tl.fontName(), tl.fontSize(), tl.fontBold(), tl.fontItalic(),
					tl.fontColor(), tl.x(), tl.y());
		} else if (src instanceof PathLayer pl) {
			return PathLayer.of(newId, pl.points(), pl.image(), pl.isClosed(), pl.x(), pl.y());
		}
		return null;
	}

	/**
	 * Converts a visual drop index (0 = top) to a list insert index. Since display
	 * is reversed (top = last element), insertIdx = listSize - visualIdx.
	 */
	static int visualToInsertIndex(int visualIdx, int listSize) {
		return Math.max(0, Math.min(listSize, listSize - visualIdx));
	}

	/**
	 * Max fraction of the canvas dimensions an auto-inserted element may occupy.
	 */
	private static final float MAX_ELEM_RATIO = 0.40f;

	/**
	 * Returns {renderW, renderH} for a freshly dropped element layer. Fits the
	 * image proportionally into MAX_ELEM_RATIO of the canvas size. Never upscales
	 * (if image is already smaller, keeps original size).
	 */
	static int[] fitElementSize(int imgW, int imgH, int canvasW, int canvasH) {
		float maxW = canvasW * MAX_ELEM_RATIO;
		float maxH = canvasH * MAX_ELEM_RATIO;
		float scale = Math.min(1.0f, Math.min(maxW / imgW, maxH / imgH));
		return new int[] { Math.max(1, Math.round(imgW * scale)), Math.max(1, Math.round(imgH * scale)) };
	}

	// =========================================================================
	// Second Canvas / Split Screen
	// =========================================================================

	// buildSecondArea() removed — now using buildCanvasArea(1) for canvas 2

	private JPanel buildRightDropZone() {
		return dropController.buildRightDropZone();
	}

	void repositionRightDropZone() {
		dropController.repositionRightDropZone();
	}

	// =========================================================================
	// File loading
	// =========================================================================
	// Convenience: loads into active canvas
	void loadFile(File file) {
		fileLoader.loadFile(file);
	}

	// Core version: loads into specified canvas
	void loadFile(File file, int idx) {
		fileLoader.loadFile(file, idx);
	}

	/**
	 * Loads a scene background image onto the canvas WITHOUT touching the image
	 * TileGalleryPanel — no indexDirectory(), no tileGallery.setFiles().
	 * Used exclusively when clicking a scene tile.
	 */
	void loadSceneBackground(File file, int idx) {
		fileLoader.loadSceneBackground(file, idx);
	}

	/** Saves the current canvas state back into the file cache. */
	public void saveCurrentState() {
		fileLoader.saveCurrentState();
	}

	public void saveCurrentState(int idx) {
		fileLoader.saveCurrentState(idx);
	}

	/**
	 * Recalculates zoom and centering for the canvas after layout changes.
	 * Does NOT change the active canvas - just fixes the display of the current canvas.
	 */
	void reloadCurrentImage(int idx) {
		fileLoader.reloadCurrentImage(idx);
	}

	/**
	 * Converts the image to TYPE_INT_ARGB and ensures it is always stored in a
	 * clean ARGB format so paint operations work correctly on any source.
	 */
	BufferedImage normalizeImage(BufferedImage src) {
		if (src.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB)
			return deepCopy(src);
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
		return out;
	}

	void indexDirectory(File file) {
		fileLoader.indexDirectory(file);
	}

	void indexDirectory(File file, int idx) {
		fileLoader.indexDirectory(file, idx);
	}

	void indexDirectory(File file, int idx, String category) {
		fileLoader.indexDirectory(file, idx, category);
	}

	void navigateImage(int dir) {
		fileLoader.navigateImage(dir);
	}

	void navigateImage(int dir, int idx) {
		fileLoader.navigateImage(dir, idx);
	}

	/**
	 * Synchronize ScenesPanel visibility with button state. Single source of truth
	 * to prevent button/panel mismatches.
	 */
	void setScenesPanelVisible(int idx, boolean visible) {
		fileLoader.setScenesPanelVisible(idx, visible);
	}

	// ── Indexed canvas methods (array-based) ────────────────────────────────────
	public void swapToImageView(int idx) {
		fileLoader.swapToImageView(idx);
	}

	public void fitToViewport(int idx) {
		fileLoader.fitToViewport(idx);
	}

	public void centerCanvas(int idx) {
		fileLoader.centerCanvas(idx);
	}

	void setZoomInstant(double nz, int idx) {
		fileLoader.setZoomInstant(nz, idx);
	}

	// ── Zoom → ZoomController ────────────────────────────────────────────────

	public void setZoom(double nz, Point anchorCanvas) {
		zoomController.setZoom(nz, anchorCanvas);
	}

	public void fitToViewport() {
		fitToViewport(activeCanvasIndex);
	}

	void startZoomAnimation(int idx) {
		zoomController.startZoomAnimation(idx);
	}

	void startZoomAnimation() {
		zoomController.startZoomAnimation();
	}

	// ── Coordinate transform ──────────────────────────────────────────────────

	/** Convert a point in canvasPanel-local coordinates to image-space. */
	public Point screenToImage(Point sp) {
		return zoomController.screenToImage(sp);
	}

	// =========================================================================
	// Alpha-editor operations
	// =========================================================================
	public void performFloodfill(Point screenPt) {
		CanvasInstance c = ci();
		Point ip = screenToImage(screenPt);
		int tc = c.workingImage.getRGB(ip.x, ip.y);
		if (((tc >> 24) & 0xFF) == 0) {
			showInfoDialog("Bereits transparent", "Klicke auf eine sichtbare Farbe.");
			return;
		}
		PaintEngine.floodFill(c.workingImage, ip.x, ip.y, new Color(0, 0, 0, 0), 30);
		markDirty();
	}

	private void applySelectionsToAlpha() {
		saveController.applySelectionsToAlpha();
	}

	private void clearSelections() {
		saveController.clearSelections();
	}

	private void resetImage() {
		saveController.resetImage();
	}

	private void saveImage() {
		saveController.saveImage();
	}

	/** Saves current workingImage to undo stack before a destructive operation. */
	public void pushUndo() {
		pushUndo(activeCanvasIndex);
	}

	public void pushUndo(int idx) {
		CanvasInstance c = ci(idx);
		if (c.workingImage == null)
			return;
		c.undoStack.push(deepCopy(c.workingImage));
		if (c.undoStack.size() > MAX_UNDO)
			c.undoStack.pollLast();
		c.redoStack.clear();
	}

	public void clearUndoRedo() {
		ci().undoStack.clear();
		ci().redoStack.clear();
	}

	void doUndo() {
		doUndo(activeCanvasIndex);
	}

	private void doUndo(int idx) {
		saveController.doUndo(idx);
	}

	void doRedo() {
		doRedo(activeCanvasIndex);
	}

	private void doRedo(int idx) {
		saveController.doRedo(idx);
	}

	/**
	 * CTRL+ALT+S: overwrite the original source file directly, no suffix, no
	 * dialog.
	 */
	void saveImageToOriginal() {
		saveController.saveImageToOriginal();
	}

	// ── Element-edit mode → ElementEditController ────────────────────────────

	// ── Save / burn → SaveController ─────────────────────────────────────────

	void saveImageSilent() {
		saveController.saveImageSilent();
	}

	void saveBurnedElementsCopy() {
		saveController.saveBurnedElementsCopy();
	}

	void saveBurnedElementsOriginal() {
		saveController.saveBurnedElementsOriginal();
	}

	// ── Element-edit visibility ───────────────────────────────────────────────

	void ensureElementEditBarVisible() {
		elementEditController.ensureElementEditBarVisible();
	}

	public void markDirty() {
		markDirty(activeCanvasIndex);
	}

	public void markDirty(int idx) {
		CanvasInstance c = ci(idx);
		c.hasUnsavedChanges = true;
		if (c.sourceFile != null)
			dirtyFiles.add(c.sourceFile);
		updateTitle();
		updateDirtyUI();
		refreshElementPanel();
		refreshGalleryThumbnail(idx);
		// Refresh all panels
		if (mapsPanel != null) {
			try {
				mapsPanel.refreshMapsList();
			} catch (Exception ex) {
				System.err.println("[WARN] Failed to refresh maps panel: " + ex.getMessage());
			}
		}
		c.canvasPanel.repaint();
		if (showRuler && idx == 0) {
			hRuler.repaint();
			vRuler.repaint();
		}
	}

	void updateDirtyUI() {
		ci(0).tileGallery.setDirtyFiles(dirtyFiles);
		if (ci(1).tileGallery != null) {
			ci(1).tileGallery.setDirtyFiles(dirtyFiles);
		}
	}

	// =========================================================================
	// Mode toggles
	// =========================================================================
	void toggleAlphaMode() {
		modeController.toggleAlphaMode();
	}

	void togglePaintMode() {
		modeController.togglePaintMode();
	}

	/**
	 * Canvas is a sub-mode of Paint: layers panel shown, drawing stays
	 * non-destructive.
	 */
	void toggleCanvasMode() {
		modeController.toggleCanvasMode();
	}

	/**
	 * Book mode: toggle paper layout editor. Paint, Canvas, and Book can coexist.
	 */
	void toggleBookMode() {
		modeController.toggleBookMode();
	}

	/** Scene mode: toggle scene editor. Paint, Canvas, and Scene can coexist. */
	void toggleSceneMode() {
		modeController.toggleSceneMode();
	}

	/** Shows or hides the element layer panels based on active canvas and mode. */
	void setElementPanelVisible(boolean visible) {
		modeController.setElementPanelVisible(visible);
	}

	/** Updates modeLabel to show all active mode flags dynamically. */
	void updateModeLabel() {
		modeController.updateModeLabel();
	}

	/** Rebuilds the element layer panel tiles from the current activeElements. */
	void refreshElementPanel() {
		if (elementLayerPanel != null && elementLayerPanel.isShowing())
			elementLayerPanel.refresh(ci(0).activeElements);
		if (elementLayerPanel2 != null && elementLayerPanel2.isShowing())
			elementLayerPanel2.refresh(ci(1).activeElements);
	}

	/**
	 * Builds the callbacks for the ElementLayerPanel, bound to a specific canvas
	 * index. Panel operations (delete, burn, …) always affect that panel's own
	 * canvas. Paste/insert uses ci() separately and is unaffected by this binding.
	 */
	/**
	 * Helper: Update an ImageLayer in both activeElements and selectedElements
	 * lists by ID. Used by flip/rotate/reset operations.
	 */
	void replaceInLists(CanvasInstance c, Layer updated) {
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
	 * Rendert Canvas + alle aktiven Elemente als Composite-Bild (für
	 * Gallery-Thumbnail). Nur ImageLayers mit Rotation werden mit g2.rotate()
	 * gerendert.
	 */
	BufferedImage renderCompositeForThumbnail(CanvasInstance c) {
		if (c.workingImage == null)
			return null;
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
	 * Aktualisiert das Gallery-Thumbnail für den aktuellen Canvas mit dem
	 * Live-Composite aus Canvas + Elementen.
	 */
	void refreshGalleryThumbnail() {
		refreshGalleryThumbnail(activeCanvasIndex);
	}

	void refreshGalleryThumbnail(int idx) {
		CanvasInstance c = ci(idx);
		if (c.sourceFile == null || c.workingImage == null) return;
		BufferedImage thumb = renderCompositeForThumbnail(c);
		if (thumb == null) return;
		// Update image gallery tile
		c.tileGallery.refreshThumbnailFor(c.sourceFile, thumb);
		// Update scene gallery tile if a scene is loaded
		if (c.activeSceneFile != null)
			c.scenesPanel.refreshThumbnailFor(c.activeSceneFile, thumb);
	}

	ElementLayerPanel.Callbacks buildElementLayerCallbacks(int idx) {
		return ElementLayerCallbacksFactory.build(this, idx);
	}

	/**
	 * Opens an ImageLayer (or any renderable layer) in the other canvas for full
	 * pixel editing.
	 */
	void doOpenImageLayerInOtherCanvas(int sourceIdx, Layer el) {
		BufferedImage img = null;
		if (el instanceof ImageLayer il) {
			img = il.image();
		} else if (el instanceof TextLayer tl) {
			img = renderTextLayerToImage(tl);
		}
		if (img == null)
			return;
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
				if (galleryWrapper != null) {
					galleryWrapper.revalidate();
					galleryWrapper.repaint();
				}
			}

			loadFile(tmp, targetIdx);
			elementEditController.activateElementEditMode(targetIdx, el, sourceIdx);
		} catch (IOException ex) {
			showErrorDialog("Fehler", "Element konnte nicht geöffnet werden:\n" + ex.getMessage());
		}
	}

	// =========================================================================
	// Transformations
	// =========================================================================
	void doFlipH() {
		transformController.doFlipH();
	}

	void doFlipV() {
		transformController.doFlipV();
	}

	void doRotate(double angleDeg) {
		transformController.doRotate(angleDeg);
	}

	void doRotate() {
		transformController.doRotate();
	}

	void doScale() {
		transformController.doScale();
	}

	// =========================================================================
	// Floating selection operations
	// =========================================================================

	/**
	 * Paste the floating image at its current (possibly scaled) rect and clear
	 * float state. In Paint mode: creates a non-destructive Element layer instead
	 * of writing to canvas.
	 */
	public void commitFloat() {
		CanvasInstance c = ci();
		if (c.floatingImg == null || c.floatRect == null)
			return;
		BufferedImage scaled = PaintEngine.scale(c.floatingImg, Math.max(1, c.floatRect.width),
				Math.max(1, c.floatRect.height));
		if (ci().appMode == AppMode.PAINT) {
			// Non-destructive: become an ImageLayer
			Layer el = new ImageLayer(c.nextElementId++, scaled, c.floatRect.x, c.floatRect.y, c.floatRect.width,
					c.floatRect.height);
			c.activeElements.add(el);
			c.selectedElements.clear();
			c.selectedElements.add(el);
			refreshElementPanel();
		} else {
			PaintEngine.pasteRegion(c.workingImage, scaled, new Point(c.floatRect.x, c.floatRect.y));
		}
		c.floatingImg = null;
		c.floatRect = null;
		c.isDraggingFloat = false;
		c.floatDragAnchor = null;
		c.activeHandle = -1;
		c.scaleBaseRect = null;
		c.scaleDragStart = null;
		c.selectedAreas.clear();
		markDirty();
		refreshElementPanel(); // Ensure ListView is updated in all modes
	}

	/** Discard the float and undo to the state before it was lifted. */
	void cancelFloat() {
		CanvasInstance c = ci();
		c.floatingImg = null;
		c.floatRect = null;
		c.isDraggingFloat = false;
		c.floatDragAnchor = null;
		c.activeHandle = -1;
		c.scaleBaseRect = null;
		c.scaleDragStart = null;
		c.selectedAreas.clear();
		doUndo();
	}

	/** Convert floatRect (image-space) to canvasPanel screen-space. */
	public Rectangle floatRectScreen() {
		CanvasInstance c = ci();
		if (c.floatRect == null)
			return new Rectangle(0, 0, 0, 0);
		return new Rectangle((int) Math.round(c.floatRect.x * c.zoom), (int) Math.round(c.floatRect.y * c.zoom),
				(int) Math.round(c.floatRect.width * c.zoom), (int) Math.round(c.floatRect.height * c.zoom));
	}

	/**
	 * 8 handle hit-rects around {@code sr} (screen-space). Order: TL=0, TC=1, TR=2,
	 * ML=3, MR=4, BL=5, BC=6, BR=7
	 */
	public Rectangle[] handleRects(Rectangle sr) {
		int x = sr.x, y = sr.y, w = sr.width, h = sr.height;
		int mx = x + w / 2, my = y + h / 2, rx = x + w, by = y + h;
		int hs = 4; // half-size → each handle square is 8×8 px
		return new Rectangle[] { new Rectangle(x - hs, y - hs, hs * 2, hs * 2), // 0 TL
				new Rectangle(mx - hs, y - hs, hs * 2, hs * 2), // 1 TC
				new Rectangle(rx - hs, y - hs, hs * 2, hs * 2), // 2 TR
				new Rectangle(x - hs, my - hs, hs * 2, hs * 2), // 3 ML
				new Rectangle(rx - hs, my - hs, hs * 2, hs * 2), // 4 MR
				new Rectangle(x - hs, by - hs, hs * 2, hs * 2), // 5 BL
				new Rectangle(mx - hs, by - hs, hs * 2, hs * 2), // 6 BC
				new Rectangle(rx - hs, by - hs, hs * 2, hs * 2), // 7 BR
		};
	}

	/**
	 * Returns rotation handle position for selected element (30px above center).
	 */
	public Point getRotationHandlePos(Rectangle sr) {
		int mx = sr.x + sr.width / 2;
		int ty = sr.y - 30; // 30 pixels above top
		return new Point(mx, ty);
	}

	/** Returns rotation handle hit rect (8×8 around handle position). */
	public Rectangle getRotationHandleRect(Rectangle sr) {
		Point p = getRotationHandlePos(sr);
		int hs = 4;
		return new Rectangle(p.x - hs, p.y - hs, hs * 2, hs * 2);
	}

	/** Returns 0-7 if {@code pt} (canvasPanel coords) hits a handle, else -1. */
	public int hitHandle(Point pt) {
		CanvasInstance c = ci();
		if (c.floatRect == null)
			return -1;
		Rectangle[] handles = handleRects(floatRectScreen());
		for (int i = 0; i < handles.length; i++)
			if (handles[i].contains(pt))
				return i;
		return -1;
	}

	/**
	 * Compute the new floatRect when handle {@code handle} is dragged from
	 * {@code origin} to {@code current} (both in canvasPanel screen-space). Uses
	 * double precision throughout to avoid integer-truncation drift at high zoom
	 * levels. Corners scale proportionally; sides scale one axis.
	 */
	private Rectangle computeNewFloatRect(int handle, Rectangle base, Point origin, Point current) {
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
			rx = bx + bw - rw;
			ry = by + bh - rh;
		}
		case 1 -> { // TC – scale Y only, anchor bottom
			rh = Math.max(MIN, bh - dy);
			rx = bx;
			rw = bw;
			ry = by + bh - rh;
		}
		case 2 -> { // TR – proportional, anchor BL
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by + bh - rh;
		}
		case 3 -> { // ML – scale X only, anchor right
			rw = Math.max(MIN, bw - dx);
			rx = bx + bw - rw;
			ry = by;
			rh = bh;
		}
		case 4 -> { // MR – scale X only, anchor left
			rw = Math.max(MIN, bw + dx);
			rx = bx;
			ry = by;
			rh = bh;
		}
		case 5 -> { // BL – proportional, anchor TR
			rw = Math.max(MIN, bw - dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx + bw - rw;
			ry = by;
		}
		case 6 -> { // BC – scale Y only, anchor top
			rh = Math.max(MIN, bh + dy);
			rx = bx;
			rw = bw;
			ry = by;
		}
		default -> { // BR (7) – proportional, anchor TL
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by;
		}
		}
		return new Rectangle((int) Math.round(rx), (int) Math.round(ry), (int) Math.round(rw), (int) Math.round(rh));
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
	PaintToolbar.Callbacks buildPaintCallbacks() {
		return PaintCallbacksFactory.build(this);
	}

	// =========================================================================
	// Canvas callbacks (indexed)
	// =========================================================================
	CanvasCallbacks buildCanvasCallbacks(int idx) {
		return CanvasCallbacksFactory.build(this, idx);
	}

	// =========================================================================
	// TileGallery callbacks
	// =========================================================================
	TileGalleryPanel.Callbacks buildGalleryCallbacks(int idx) {
		return GalleryCallbacksFactory.build(this, idx);
	}

	private TileGalleryPanel.FilePreloadCallback buildGalleryPreloadCallback(int idx) {
		return GalleryCallbacksFactory.buildPreloadCallback(this, idx);
	}

	// =========================================================================
	// Scenes Panel Callbacks
	// =========================================================================

	private TileGalleryPanel.Callbacks buildScenesCallbacks(int idx) {
		return scenesController.buildScenesCallbacks(idx);
	}

	void refreshSceneFiles(int idx) {
		scenesController.refreshSceneFiles(idx);
	}

	void createSceneFromDrop(java.util.List<java.io.File> files, int idx) {
		scenesController.createSceneFromDrop(files, idx);
	}

	void loadGameIISceneDir(java.io.File sceneDir, int idx) {
		scenesController.loadGameIISceneDir(sceneDir, idx);
	}

	// =========================================================================
	// Clipboard operations
	// =========================================================================

	/**
	 * CTRL+C — copy INSIDE selection → Element layer (or full image if no
	 * selection).
	 */
	void doCopy() {
		clipboardController.doCopy();
	}

	/**
	 * CTRL+SHIFT+C — copy OUTSIDE selection → Element layer (full-size, inside
	 * punched out).
	 */
	void doCopyOutside() {
		clipboardController.doCopyOutside();
	}

	/** CTRL+X — cut INSIDE selection → Element layer + clear canvas pixels. */
	void doCut() {
		clipboardController.doCut();
	}

	/**
	 * CTRL+SHIFT+X — cut OUTSIDE selection → Element layer (full-size) + clear
	 * canvas outside.
	 */
	void doCutOutside() {
		clipboardController.doCutOutside();
	}

	/**
	 * Creates a new ImageLayer from the given image and adds it to activeElements.
	 * Called after any copy/cut so the content immediately appears as a
	 * non-destructive layer.
	 */

	void doPaste() {
		clipboardController.doPaste();
	}

	public Rectangle getActiveSelection() {
		CanvasInstance c = ci();
		if (!c.selectedAreas.isEmpty())
			return c.selectedAreas.get(c.selectedAreas.size() - 1);
		if (c.isSelecting && c.selectionStart != null && c.selectionEnd != null) {
			int x = Math.min(c.selectionStart.x, c.selectionEnd.x);
			int y = Math.min(c.selectionStart.y, c.selectionEnd.y);
			int w = Math.abs(c.selectionEnd.x - c.selectionStart.x);
			int h = Math.abs(c.selectionEnd.y - c.selectionStart.y);
			return (w > 0 && h > 0) ? new Rectangle(x, y, w, h) : null;
		}
		return null;
	}

	// =========================================================================
	// Secondary Canvas Window – Control Methods
	// =========================================================================

	void toggleSecondaryWindow() {
		secWinController.toggleSecondaryWindow();
	}

	void cyclePreviewMode() {
		secWinController.cyclePreviewMode();
	}

	void refreshSnapshot() {
		secWinController.refreshSnapshot();
	}

	void toggleSecondaryFullscreen() {
		secWinController.toggleSecondaryFullscreen();
	}

	void cycleAlwaysOnTop() {
		secWinController.cycleAlwaysOnTop();
	}

	void cycleCanvasDisplayMode() {
		secWinController.cycleCanvasDisplayMode();
	}

	void applySecondaryWindowToCanvas() {
		secWinController.applySecondaryWindowToCanvas();
	}

	// =========================================================================
	// Keyboard shortcuts
	// =========================================================================
	private void setupKeyBindings() {
		new KeyboardShortcutManager(this).setup();
	}

	// =========================================================================
	// New helper methods
	// =========================================================================

	/**
	 * Centers the viewport over the canvas (called after zoom or sidebar toggle).
	 */
	public void centerCanvas() {
		centerCanvas(activeCanvasIndex);
	}

	/**
	 * Centers the viewport horizontally only (X-axis) for indexed canvas. Called
	 * when the user has a custom zoom and the toolbar or fullscreen state changes.
	 */
	void centerCanvasX(int idx) {
		CanvasInstance c = ci(idx);
		if (c.scrollPane == null || c.workingImage == null)
			return;
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
	void centerCanvasX() {
		centerCanvasX(activeCanvasIndex);
	}

	private void showZoomInput() {
		editorDialogs.showZoomInput();
	}

	/** Creates a new blank ARGB bitmap after asking for dimensions. */
	private void doNewBitmap() {
		newFileController.doNewBitmap();
	}

	/** Lets the user choose one or both checkerboard background colors. */
	private void showCanvasBgDialog() {
		newFileController.showCanvasBgDialog();
	}

	private void toggleQuickBG() {
		newFileController.toggleQuickBG();
	}

	// =========================================================================
	// UI state helpers
	// =========================================================================
	public void swapToImageView() {
		swapToImageView(activeCanvasIndex);
	}

	void updateNavigationButtons() {
		CanvasInstance c = ci();
		c.prevNavButton.setEnabled(c.currentImageIndex > 0);
		c.nextNavButton.setEnabled(c.currentImageIndex < c.directoryImages.size() - 1);
	}

	public void updateTitle() {
		CanvasInstance c = ci();
		if (c.sourceFile == null) {
			setTitle("Selective Alpha Editor");
			return;
		}
		String dirty = c.hasUnsavedChanges ? " •" : "";
		String fileName = c.sourceFile.getName();
		String size = c.workingImage != null ? c.workingImage.getWidth() + "x" + c.workingImage.getHeight() + "px"
				: "?x?";
		String imageCount = (c.currentImageIndex + 1) + "/" + c.directoryImages.size();
		setTitle("Selective Alpha Editor  |  " + fileName + "  |  " + size + "  |  " + imageCount + dirty);
	}

	public void updateStatus() {
		CanvasInstance c = ci();
		if (c.sourceFile == null) {
			statusLabel.setText("Keine Datei geladen");
			return;
		}
		statusLabel.setText(
				c.sourceFile.getName() + "   |   " + (c.currentImageIndex + 1) + " / " + c.directoryImages.size()
						+ "   |   " + c.workingImage.getWidth() + " × " + c.workingImage.getHeight() + " px");
	}

	public void setBottomButtonsEnabled(boolean enabled) {
		boolean sel = !floodfillMode && ci().appMode == AppMode.ALPHA_EDITOR;
		applyButton.setEnabled(enabled && sel);
		clearSelectionsButton.setEnabled(enabled && sel);
		if (actionPanel == null)
			return;
		for (java.awt.Component c : actionPanel.getComponents())
			if (c instanceof JButton btn && ("resetButton".equals(btn.getName()) || "saveButton".equals(btn.getName())))
				btn.setEnabled(enabled);
	}

	// ── Quick-open dialog → QuickOpenController ──────────────────────────────

	void showQuickOpenDialog() {
		quickOpenController.show();
	}

	void showQuickOpenDialog(int canvasIdx) {
		quickOpenController.show(canvasIdx);
	}

	void showQuickOpenDialog(int canvasIdx, String initialCategory) {
		quickOpenController.show(canvasIdx, initialCategory);
	}

	// ── Dialogs → EditorDialogs / UIComponentFactory ──────────────────────────

	void showErrorDialog(String title, String message) {
		editorDialogs.showErrorDialog(title, message);
	}

	void showInfoDialog(String title, String message) {
		editorDialogs.showInfoDialog(title, message);
	}

	JDialog createBaseDialog(String title, int w, int h) {
		return UIComponentFactory.createBaseDialog(this, title, w, h);
	}

	JPanel centeredColumnPanel(int vp, int hp, int bp) {
		return UIComponentFactory.centeredColumnPanel(vp, hp, bp);
	}

	// =========================================================================
	// Element helpers
	// =========================================================================

	public Rectangle elemRectScreen(Layer el) {
		return elementController.elemRectScreen(el);
	}

	public Rectangle elemRectScreen(Layer el, double zoom) {
		return elementController.elemRectScreen(el, zoom);
	}

	BufferedImage renderTextLayerToImage(TextLayer tl) {
		return elementController.renderTextLayerToImage(tl);
	}

	private void insertSelectionAsElement() {
		elementController.insertSelectionAsElement();
	}

	void mergeSelectedElements() {
		elementController.mergeSelectedElements();
	}

	void deleteSelectedElements() {
		elementController.deleteSelectedElements();
	}

	void persistSceneIfActive(int idx) {
		elementController.persistSceneIfActive(idx);
	}

	void doToggleElementSelection(Layer el) {
		elementController.doToggleElementSelection(el);
	}

	// =========================================================================
	// Utilities
	// =========================================================================
	static boolean isSupportedFile(File f) {
		if (f == null || !f.isFile())
			return false;
		String n = f.getName().toLowerCase();
		for (String e : SUPPORTED_EXTENSIONS)
			if (n.endsWith("." + e))
				return true;
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
	@Override
	public BufferedImage getWorkingImage() {
		return ci().workingImage;
	}

	@Override
	public JScrollPane getScrollPane() {
		return ci().scrollPane;
	}

	@Override
	public JPanel getCanvasPanel() {
		return ci().canvasPanel;
	}

	@Override
	public double getZoom() {
		return ci().zoom;
	}

	@Override
	public RulerUnit getRulerUnit() {
		return rulerUnit;
	}

	// =========================================================================
	// Scene helpers
	// =========================================================================

	/**
	 * Copies an entire scene directory (sceneFile's parent) to a new name in the
	 * same scenes root, then refreshes the scene panel.
	 */
	void copySceneDirectory(File sceneFile, int idx) {
		File srcDir = sceneFile.getParentFile();
		File scenesRoot = srcDir.getParentFile();
		String baseName = srcDir.getName();
		File destDir = new File(scenesRoot, baseName + "_copy");
		int n = 2;
		while (destDir.exists()) destDir = new File(scenesRoot, baseName + "_copy" + n++);
		File finalDest = destDir;
		new javax.swing.SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception {
				copyDirRecursive(srcDir, finalDest);
				// Rename the .txt inside to match the new directory name
				File oldTxt = new File(finalDest, baseName + ".txt");
				File newTxt = new File(finalDest, finalDest.getName() + ".txt");
				if (oldTxt.exists()) oldTxt.renameTo(newTxt);
				return null;
			}
			@Override protected void done() {
				try { get(); } catch (Exception ex) { showErrorDialog("Fehler", ex.getMessage()); return; }
				refreshSceneFiles(idx);
				ToastNotification.show(SelectiveAlphaEditor.this, "Scene kopiert: " + finalDest.getName());
			}
		}.execute();
	}

	private static void copyDirRecursive(File src, File dest) throws java.io.IOException {
		dest.mkdirs();
		for (File f : src.listFiles()) {
			File d = new File(dest, f.getName());
			if (f.isDirectory()) copyDirRecursive(f, d);
			else java.nio.file.Files.copy(f.toPath(), d.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// =========================================================================
	// Shared layer-export helpers
	// =========================================================================

	/** Renders a layer to a BufferedImage. Returns null for PathLayer (not supported). */
	BufferedImage renderLayerToImage(Layer live) {
		if (live instanceof TextLayer tl) {
			return renderTextLayerToImage(tl);
		} else if (live instanceof ImageLayer il) {
			return PaintEngine.scale(il.image(), Math.max(1, live.width()), Math.max(1, live.height()));
		}
		return null; // PathLayer not supported for pixel export
	}

	/** Returns a unique File in sourceFile's directory for exporting a layer. */
	File uniqueLayerExportFile(File sourceFile, int layerId) {
		String sourceName = sourceFile.getName();
		int dot = sourceName.lastIndexOf('.');
		String base = dot > 0 ? sourceName.substring(0, dot) : sourceName;
		String ext  = dot > 0 ? sourceName.substring(dot)    : ".png";
		File dir    = sourceFile.getParentFile();
		File target = new File(dir, base + "_layer_" + layerId + ext);
		int counter = 1;
		while (target.exists()) {
			target = new File(dir, base + "_layer_" + layerId + "_" + counter + ext);
			counter++;
		}
		return target;
	}

	/** Writes img to file, adds file to the gallery of canvas idx, shows error on failure. */
	void saveLayerAsImageFile(BufferedImage img, File file, int idx) {
		try {
			javax.imageio.ImageIO.write(img, "PNG", file);
			if (ci(idx).tileGallery != null)
				ci(idx).tileGallery.addFiles(java.util.Arrays.asList(file));
		} catch (Exception ex) {
			System.err.println("[ERROR] Failed to export layer: " + ex.getMessage());
			showErrorDialog("Fehler", "Speichern fehlgeschlagen:\n" + ex.getMessage());
		}
	}

	// =========================================================================
	// Public API for EditorDialogs
	// =========================================================================
	public void setWorkingImage(BufferedImage img) {
		ci().workingImage = img;
	}

	public BufferedImage getOriginalImage() {
		return ci().originalImage;
	}

	public void setOriginalImage(BufferedImage img) {
		ci().originalImage = img;
	}

	public File getSourceFile() {
		return ci().sourceFile;
	}

	public void setSourceFile(File f) {
		ci().sourceFile = f;
	}

	public JPanel getCanvasWrapper() {
		return ci().canvasWrapper;
	}

	public JLabel getZoomLabel() {
		return zoomLabel;
	}

	public Color getCanvasBg1() {
		return canvasBg1;
	}

	public Color getCanvasBg2() {
		return canvasBg2;
	}

	public void setCanvasBg1(Color c) {
		canvasBg1 = c;
	}

	public void setCanvasBg2(Color c) {
		canvasBg2 = c;
	}

	// =========================================================================
	// Preload Cache (for hover/browsing optimization)
	// =========================================================================

	void preloadFileAsync(File file, int idx) {
		preloadController.preloadFileAsync(file, idx);
	}

	void preloadNextImages(int idx) {
		preloadController.preloadNextImages(idx);
	}
}
