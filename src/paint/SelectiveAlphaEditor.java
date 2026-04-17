package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
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
	boolean[] canvasWasVisible = new boolean[2]; // Track canvas visibility for transitions

	// Zoom settings (non-final for runtime adjustment)
	double ZOOM_MIN = 0.05;
	double ZOOM_MAX = 16.0; // max: 16x16 pixels
	double ZOOM_STEP = 0.10;
	double ZOOM_FACTOR = 1.08; // progressive zoom: 8% per notch

	static final int RULER_THICK = 20; // pixels wide/tall for ruler strip

	static final int TOPBAR_BTN_W = 36;
	static final int TOPBAR_BTN_H = 36;
	static final int TOPBAR_ZOOM_BTN_W = 36;
	static final int TOPBAR_ZOOM_BTN_H = 36;

	// ── Gallery shrinking behavior ─────────────────────────────────────────────
	// true → galleries shrink when both canvases shown, tiles scale to fit
	// false → canvases shrink to preserve gallery widths
	static final boolean SHRINK_GALLERY = true;

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
	boolean showRuler = true;
	RulerUnit rulerUnit = RulerUnit.PX;

	// ── File cache (images stay alive while navigating, dirty until saved) ────
	/** Files with unsaved changes (shown red in gallery). */
	final Set<File> dirtyFiles = new HashSet<>();

	// ── Directory browsing (gallery multiselect) ──────────────────────────────
	List<File> selectedImages = new ArrayList<>();

	// ── Project Management ────────────────────────────────────────────────────
	ProjectManager projectManager = new ProjectManager();


	// ── Controllers (Modularization) ─────────────────────────────────────────
	private final UIBuilder uiBuilder = new UIBuilder(this);
	private final FloatSelectionController floatController = new FloatSelectionController(this);
	private final AppLifecycleController lifecycleController = new AppLifecycleController(this);
	final LayoutController layoutController = new LayoutController(this);
	private final ClipboardController clipboardController = new ClipboardController(this);
	final SecondaryWindowController secWinController = new SecondaryWindowController(this);
	final FileLoadController fileLoader = new FileLoadController(this);
	private final ModeController modeController = new ModeController(this);
	private final TransformController transformController = new TransformController(this);
	final ScenesController scenesController = new ScenesController(this);
	final NewFileController newFileController = new NewFileController(this);
	final ElementController elementController = new ElementController(this);
	private final PreloadController preloadController = new PreloadController(this);
	final DropController dropController = new DropController(this);
	final SaveController saveController = new SaveController(this);
	final ElementEditController elementEditController = new ElementEditController(this);
	private final QuickOpenController quickOpenController = new QuickOpenController(this);
	final EditorDialogs editorDialogs = new EditorDialogs(this);
	final BookController bookController = new BookController(this);
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
	JToggleButton filmstripBtn;
	JToggleButton filmstripBtn2;       // Toggle for Canvas I second gallery
	JToggleButton scenesBtn;
	JToggleButton secondCanvasBtn;
	JToggleButton secondGalleryBtn;
	JToggleButton secondGalleryBtn2;   // Toggle for Canvas II second gallery
	JToggleButton secondScenesBtn;

	// ── Element layer panels (shown in Canvas mode) ──────────────────────────
	ElementLayerPanel elementLayerPanel; // For canvas 0
	ElementLayerPanel elementLayerPanel2; // For canvas 1
	JToggleButton firstElementsBtn; // Toggle for elementLayerPanel
	JToggleButton secondElementsBtn; // Toggle for elementLayerPanel2

	// ── Maps panel (toggle-able list view) ─────────────────────────────────────
	MapsPanel mapsPanel;
	JToggleButton mapsBtn; // Toggle for mapsPanel

	// ── Quick open and drop zone toggle ────────────────────────────────────────
	JButton quickOpenBtn; // Quick open recent projects
	JToggleButton toggleDropZoneBtn; // Toggle drop zone visibility for canvas 2

	// ── Shared UI components ───────────────────────────────────────────────────
	HRulerPanel hRuler;
	VRulerPanel vRuler;
	JPanel rulerCorner;
	JPanel rulerNorthBar; // container for rulerCorner + hRuler
	JPanel actionPanel; // Holds apply/clear/reset/save buttons
	JPanel rightDropZone; // drag-activation overlay
	JToggleButton firstCanvasBtn; // Toggle for ci(0).layeredPane visibility
	JPanel mainDividerPanel; // Thin vertical separator between Canvas 1 and 2

	JLabel statusLabel;
	JLabel modeLabel;
	JLabel zoomLabel;
	JButton applyButton;
	JButton clearSelectionsButton;
	JToggleButton paintModeBtn;
	JToggleButton canvasModeBtn;
	JToggleButton bookModeBtn;
	JToggleButton sceneModeBtn;

	// Book-mode context buttons (shown only when bookModeBtn is active)
	JToggleButton bookListIBtn;   // BI  – Books ListView Canvas I
	JToggleButton bookPagesIBtn;  // PI  – Pages of Book I
	JToggleButton bookListIIBtn;  // BII – Books ListView Canvas II
	JToggleButton bookPagesIIBtn; // PII – Pages of Book II
	JPanel        topBarLeft;     // left panel of the top bar (for revalidation)
	JPanel        topBarRight;    // right panel of the top bar (for revalidation)

	// Book panels
	BookListPanel  bookListPanel;
	BookPagesPanel bookPagesPanel;
	BookListPanel  bookListPanel2;
	BookPagesPanel bookPagesPanel2;

	// Page-layout toolbar (horizontal, above PaintToolbar)
	PageLayoutToolbar pageLayoutToolbar;
	JToggleButton     pageLayoutBtn;   // "SL" toggle in top bar

	PaintToolbar paintToolbar;
	TextToolbar  textToolbar;

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
		add(uiBuilder.buildTopBar(), BorderLayout.NORTH);
		add(uiBuilder.buildCenter(), BorderLayout.CENTER);
		add(uiBuilder.buildBottomBar(), BorderLayout.SOUTH);
		setMinimumSize(new Dimension(900, 650));
		pack();
		lifecycleController.loadSettings();
		setupKeyBindings();
		lifecycleController.setupWindowBehavior();
		setVisible(true);
	}


	// ── Layout utilities → LayoutController ─────────────────────────────────
	void buildRulerLayout()              { layoutController.buildRulerLayout(); }
	void repositionNavButtons(int idx)   { layoutController.repositionNavButtons(idx); }
	void updateCanvasFocusBorder()       { layoutController.updateCanvasFocusBorder(); }
	void updateLayoutVisibility()        { layoutController.updateLayoutVisibility(); }

	// ── Drag & Drop → DropController ─────────────────────────────────────────
	void setupDropTarget(java.awt.Component target, int idx) { dropController.setupDropTarget(target, idx); }

	// ── Layer utilities → ElementController ──────────────────────────────────
	Layer copyLayerWithNewId(Layer src, int newId)                        { return elementController.copyLayerWithNewId(src, newId); }
	static int visualToInsertIndex(int visualIdx, int listSize)           { return ElementController.visualToInsertIndex(visualIdx, listSize); }
	static int[] fitElementSize(int iW, int iH, int cW, int cH)          { return ElementController.fitElementSize(iW, iH, cW, cH); }

	private JPanel buildRightDropZone()    { return dropController.buildRightDropZone(); }
	void repositionRightDropZone()         { dropController.repositionRightDropZone(); }

	// ── File loading → FileLoadController ───────────────────────────────────
	void loadFile(File file)                               { fileLoader.loadFile(file); }
	void loadFile(File file, int idx)                      { fileLoader.loadFile(file, idx); }
	void loadSceneBackground(File file, int idx)           { fileLoader.loadSceneBackground(file, idx); }
	public void saveCurrentState()                         { fileLoader.saveCurrentState(); }
	public void saveCurrentState(int idx)                  { fileLoader.saveCurrentState(idx); }
	void reloadCurrentImage(int idx)                       { fileLoader.reloadCurrentImage(idx); }
	void indexDirectory(File file)                         { fileLoader.indexDirectory(file); }
	void indexDirectory(File file, int idx)                { fileLoader.indexDirectory(file, idx); }
	void indexDirectory(File file, int idx, String cat)    { fileLoader.indexDirectory(file, idx, cat); }
	void navigateImage(int dir)                            { fileLoader.navigateImage(dir); }
	void navigateImage(int dir, int idx)                   { fileLoader.navigateImage(dir, idx); }
	void setScenesPanelVisible(int idx, boolean visible)   { fileLoader.setScenesPanelVisible(idx, visible); }
	public void swapToImageView(int idx)                   { fileLoader.swapToImageView(idx); }
	public void fitToViewport(int idx)                     { fileLoader.fitToViewport(idx); }
	public void centerCanvas(int idx)                      { fileLoader.centerCanvas(idx); }
	void setZoomInstant(double nz, int idx)                { fileLoader.setZoomInstant(nz, idx); }

	// ── Zoom → ZoomController ────────────────────────────────────────────────
	public void setZoom(double nz, Point anchorCanvas) { zoomController.setZoom(nz, anchorCanvas); }
	public void fitToViewport()                         { fitToViewport(activeCanvasIndex); }
	void startZoomAnimation(int idx)                    { zoomController.startZoomAnimation(idx); }
	void startZoomAnimation()                           { zoomController.startZoomAnimation(); }
	public Point screenToImage(Point sp)                { return zoomController.screenToImage(sp); }

	// ── Alpha-editor operations → SaveController ────────────────────────────
	public void performFloodfill(Point screenPt)   { saveController.performFloodfill(screenPt); }
	private void applySelectionsToAlpha()           { saveController.applySelectionsToAlpha(); }
	private void clearSelections()                  { saveController.clearSelections(); }
	private void resetImage()                       { saveController.resetImage(); }
	private void saveImage()                        { saveController.saveImage(); }
	public void pushUndo()                          { saveController.pushUndo(activeCanvasIndex); }
	public void pushUndo(int idx)                   { saveController.pushUndo(idx); }
	public void clearUndoRedo()                     { saveController.clearUndoRedo(); }
	void doUndo()                                   { saveController.doUndo(activeCanvasIndex); }
	void doRedo()                                   { saveController.doRedo(activeCanvasIndex); }
	void saveImageToOriginal()                      { saveController.saveImageToOriginal(); }
	void saveImageSilent()                          { saveController.saveImageSilent(); }
	void saveBurnedElementsCopy()                   { saveController.saveBurnedElementsCopy(); }
	void saveBurnedElementsOriginal()               { saveController.saveBurnedElementsOriginal(); }
	void ensureElementEditBarVisible()              { elementEditController.ensureElementEditBarVisible(); }

	// ── Dirty/refresh → LayoutController ─────────────────────────────────────
	public void markDirty()                         { layoutController.markDirty(activeCanvasIndex); }
	public void markDirty(int idx)                  { layoutController.markDirty(idx); }
	void updateDirtyUI()                            { layoutController.updateDirtyUI(); }
	void refreshElementPanel()                      { layoutController.refreshElementPanel(); }

	// ── Mode toggles → ModeController ────────────────────────────────────────
	void toggleAlphaMode()                        { modeController.toggleAlphaMode(); }
	void togglePaintMode()                        { modeController.togglePaintMode(); }
	void toggleCanvasMode()                       { modeController.toggleCanvasMode(); }
	void toggleBookMode()                         { modeController.toggleBookMode(); }
	void toggleSceneMode()                        { modeController.toggleSceneMode(); }
	void setElementPanelVisible(boolean visible)  { modeController.setElementPanelVisible(visible); }
	void updateModeLabel()                        { modeController.updateModeLabel(); }

	ElementLayerPanel.Callbacks buildElementLayerCallbacks(int idx)  { return ElementLayerCallbacksFactory.build(this, idx); }

	// ── Element helpers → ElementController ──────────────────────────────────
	void replaceInLists(CanvasInstance c, Layer updated)    { elementController.replaceInLists(c, updated); }
	BufferedImage renderCompositeForThumbnail(CanvasInstance c) { return elementController.renderCompositeForThumbnail(c); }
	void refreshGalleryThumbnail()                          { elementController.refreshGalleryThumbnail(); }
	void refreshGalleryThumbnail(int idx)                   { elementController.refreshGalleryThumbnail(idx); }
	void doOpenImageLayerInOtherCanvas(int sourceIdx, Layer el) { elementController.doOpenImageLayerInOtherCanvas(sourceIdx, el); }

	// ── Transformations → TransformController ────────────────────────────────
	void doFlipH()               { transformController.doFlipH(); }
	void doFlipV()               { transformController.doFlipV(); }
	void doRotate(double deg)    { transformController.doRotate(deg); }
	void doRotate()              { transformController.doRotate(); }
	void doScale()               { transformController.doScale(); }

	// ── Floating selection → FloatSelectionController ────────────────────────
	public void commitFloat()                              { floatController.commitFloat(); }
	void cancelFloat()                                     { floatController.cancelFloat(); }
	public Rectangle floatRectScreen()                     { return floatController.floatRectScreen(); }
	public Rectangle[] handleRects(Rectangle sr)           { return floatController.handleRects(sr); }
	public int hitHandle(Point pt)                         { return floatController.hitHandle(pt); }
	public Point getRotationHandlePos(Rectangle sr)        { return floatController.getRotationHandlePos(sr); }
	public Rectangle getRotationHandleRect(Rectangle sr)   { return floatController.getRotationHandleRect(sr); }

	// ── Callback factories ────────────────────────────────────────────────────
	PaintToolbar.Callbacks buildPaintCallbacks()                    { return PaintCallbacksFactory.build(this); }
	TextToolbar.Callbacks  buildTextToolbarCallbacks()              { return TextToolbarCallbacksFactory.build(this); }
	CanvasCallbacks buildCanvasCallbacks(int idx)                   { return CanvasCallbacksFactory.build(this, idx); }
	TileGalleryPanel.Callbacks buildGalleryCallbacks(int idx)       { return GalleryCallbacksFactory.build(this, idx); }
	TileGalleryPanel.FilePreloadCallback buildGalleryPreloadCallback(int idx) { return GalleryCallbacksFactory.buildPreloadCallback(this, idx); }
	TileGalleryPanel.Callbacks buildScenesCallbacks(int idx)        { return scenesController.buildScenesCallbacks(idx); }
	void refreshSceneFiles(int idx)                                 { scenesController.refreshSceneFiles(idx); }
	void createSceneFromDrop(java.util.List<java.io.File> files, int idx) { scenesController.createSceneFromDrop(files, idx); }
	void loadGameIISceneDir(java.io.File sceneDir, int idx)         { scenesController.loadGameIISceneDir(sceneDir, idx); }

	// ── Clipboard → ClipboardController ──────────────────────────────────────
	void doCopy()        { clipboardController.doCopy(); }
	void doCopyOutside() { clipboardController.doCopyOutside(); }
	void doCut()         { clipboardController.doCut(); }
	void doCutOutside()  { clipboardController.doCutOutside(); }
	void doPaste()       { clipboardController.doPaste(); }

	public Rectangle getActiveSelection() { return clipboardController.getActiveSelection(); }

	// ── Secondary window → SecondaryWindowController ─────────────────────────
	void toggleSecondaryWindow()       { secWinController.toggleSecondaryWindow(); }
	void cyclePreviewMode()            { secWinController.cyclePreviewMode(); }
	void refreshSnapshot()             { secWinController.refreshSnapshot(); }
	void toggleSecondaryFullscreen()   { secWinController.toggleSecondaryFullscreen(); }
	void cycleAlwaysOnTop()            { secWinController.cycleAlwaysOnTop(); }
	void cycleCanvasDisplayMode()      { secWinController.cycleCanvasDisplayMode(); }
	void applySecondaryWindowToCanvas(){ secWinController.applySecondaryWindowToCanvas(); }

	private void setupKeyBindings()        { new KeyboardShortcutManager(this).setup(); }

	public void centerCanvas()             { centerCanvas(activeCanvasIndex); }
	void centerCanvasX(int idx)            { fileLoader.centerCanvasX(idx); }
	void centerCanvasX()                   { fileLoader.centerCanvasX(activeCanvasIndex); }
	public void swapToImageView()          { swapToImageView(activeCanvasIndex); }
	private void showZoomInput()           { editorDialogs.showZoomInput(); }
	void doNewBitmap()                     { newFileController.doNewBitmap(); }
	void doNewBitmapForCanvas(int idx)     { newFileController.doNewBitmapForCanvas(idx); }
	void openSecondGalleryDir(int idx)     { fileLoader.openSecondGalleryDir(idx); }
	void loadFileIntoGallery2(File f, int idx) { fileLoader.loadFileIntoGallery2(f, idx); }
	TileGalleryPanel.Callbacks buildGallery2Callbacks(int idx) { return GalleryCallbacksFactory.buildGallery2(this, idx); }
	private void showCanvasBgDialog()      { newFileController.showCanvasBgDialog(); }
	private void toggleQuickBG()           { newFileController.toggleQuickBG(); }

	// ── UI state → LayoutController ──────────────────────────────────────────
	void updateNavigationButtons()         { layoutController.updateNavigationButtons(); }
	public void updateTitle()              { layoutController.updateTitle(); }
	public void updateStatus()             { layoutController.updateStatus(); }
	public void setBottomButtonsEnabled(boolean enabled) { layoutController.setBottomButtonsEnabled(enabled); }

	// ── Quick-open dialog → QuickOpenController ──────────────────────────────
	void showQuickOpenDialog()                                     { quickOpenController.show(); }
	void showQuickOpenDialog(int canvasIdx)                        { quickOpenController.show(canvasIdx); }
	void showQuickOpenDialog(int canvasIdx, String initialCategory){ quickOpenController.show(canvasIdx, initialCategory); }

	// ── Dialogs → EditorDialogs / UIComponentFactory ──────────────────────────
	void showErrorDialog(String title, String message)             { editorDialogs.showErrorDialog(title, message); }
	void showInfoDialog(String title, String message)              { editorDialogs.showInfoDialog(title, message); }
	JDialog createBaseDialog(String title, int w, int h)           { return UIComponentFactory.createBaseDialog(this, title, w, h); }
	JPanel centeredColumnPanel(int vp, int hp, int bp)             { return UIComponentFactory.centeredColumnPanel(vp, hp, bp); }

	// ── Element helpers → ElementController ─────────────────────────────────
	public Rectangle elemRectScreen(Layer el)              { return elementController.elemRectScreen(el); }
	public Rectangle elemRectScreen(Layer el, double zoom) { return elementController.elemRectScreen(el, zoom); }
	BufferedImage renderTextLayerToImage(TextLayer tl)     { return elementController.renderTextLayerToImage(tl); }
	private void insertSelectionAsElement()                { elementController.insertSelectionAsElement(); }
	void mergeSelectedElements()                           { elementController.mergeSelectedElements(); }
	void deleteSelectedElements()                          { elementController.deleteSelectedElements(); }
	void persistSceneIfActive(int idx)                     { elementController.persistSceneIfActive(idx); }
	void doToggleElementSelection(Layer el)                { elementController.doToggleElementSelection(el); }

	// ── Utilities ────────────────────────────────────────────────────────────
	static boolean isSupportedFile(File f)      { return FileLoadController.isSupportedFile(f); }
	BufferedImage normalizeImage(BufferedImage src) { return fileLoader.normalizeImage(src); }

	public BufferedImage deepCopy(BufferedImage src) {
		BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = c.createGraphics();
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
		return c;
	}

	// ── RulerCallbacks ────────────────────────────────────────────────────────
	@Override public BufferedImage getWorkingImage() { return ci().workingImage; }
	@Override public JScrollPane getScrollPane()     { return ci().scrollPane; }
	@Override public JPanel getCanvasPanel()         { return ci().canvasPanel; }
	@Override public double getZoom()                { return ci().zoom; }
	@Override public RulerUnit getRulerUnit()        { return rulerUnit; }
	@Override public PageLayout getPageLayout()      { return pageLayoutToolbar != null ? pageLayoutToolbar.getPageLayout() : null; }
	@Override public boolean isBookMode()            { return bookModeBtn != null && bookModeBtn.isSelected(); }
	@Override public void onMarginDragged(boolean isHorizontal, boolean isFirst, int newMm) {
		if (pageLayoutToolbar == null) return;
		pageLayoutToolbar.setMarginFromRuler(isHorizontal, isFirst, newMm);
	}

	// ── Scene helpers → ScenesController ─────────────────────────────────────
	void copySceneDirectory(File sceneFile, int idx) { scenesController.copySceneDirectory(sceneFile, idx); }

	// ── Layer export helpers → ElementController ──────────────────────────────
	BufferedImage renderLayerToImage(Layer live)                        { return elementController.renderLayerToImage(live); }
	File uniqueLayerExportFile(File sourceFile, int layerId)            { return elementController.uniqueLayerExportFile(sourceFile, layerId); }
	void saveLayerAsImageFile(BufferedImage img, File file, int idx)    { elementController.saveLayerAsImageFile(img, file, idx); }

	// ── Public API (used by dialogs and controllers) ──────────────────────────
	public void setWorkingImage(BufferedImage img)  { ci().workingImage = img; }
	public BufferedImage getOriginalImage()         { return ci().originalImage; }
	public void setOriginalImage(BufferedImage img) { ci().originalImage = img; }
	public File getSourceFile()                     { return ci().sourceFile; }
	public void setSourceFile(File f)               { ci().sourceFile = f; }
	public JPanel getCanvasWrapper()                { return ci().canvasWrapper; }
	public JLabel getZoomLabel()                    { return zoomLabel; }
	public Color getCanvasBg1()                     { return canvasBg1; }
	public Color getCanvasBg2()                     { return canvasBg2; }
	public void setCanvasBg1(Color c)               { canvasBg1 = c; }
	public void setCanvasBg2(Color c)               { canvasBg2 = c; }

	// ── Preload Cache ─────────────────────────────────────────────────────────
	void preloadFileAsync(File file, int idx)       { preloadController.preloadFileAsync(file, idx); }
	void preloadNextImages(int idx)                 { preloadController.preloadNextImages(idx); }
}
