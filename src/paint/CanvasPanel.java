package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Canvas panel for drawing and image manipulation. Handles all mouse input,
 * painting, and selection/floating selection logic. Extracted from
 * SelectiveAlphaEditor as a standalone component.
 */
public class CanvasPanel extends JPanel {
	private final CanvasCallbacks callbacks;

	// Pan tracking
	private Point panStart = null;
	private Point panViewPos = null;
	public int mouseWheelSensitivityInPx = 16;

	// Key state tracking for modifier combinations
	private boolean[] keyState = new boolean[256];

	// Element multi-select: rubber-band state (screen coords)
	private Point elemBandStart = null;
	private Point elemBandEnd   = null;
	private boolean isElemBanding = false;
	// Delta-based multi-drag: last image-space drag point
	private Point elemLastImgPt = null;

	// ── Element rotation state ────────────────────────────────────────────────
	private boolean isDraggingRotation = false;
	private double  rotDragStartAngle  = 0.0;  // atan2 angle at press
	private double  rotDragBaseAngle   = 0.0;  // element.rotationAngle at press

	// ── Deferred undo ─────────────────────────────────────────────────────────
	/**
	 * True while a drag-start was registered (snap-drag, rotation) but no actual
	 * mouse movement has happened yet. The undo snapshot is pushed on the first
	 * real move, avoiding stack pollution when the user only clicks without dragging.
	 */
	private boolean pendingUndo = false;
	private void commitPendingUndo() {
		if (pendingUndo) { callbacks.pushUndo(); pendingUndo = false; }
	}

	// ── Right-click snap-drag state ───────────────────────────────────────────
	private boolean isSnapDragging = false;
	private static final int SNAP_DIST = 12;  // image-space pixels
	// True when the current stroke was started with right-click (paint with secondary color)
	private boolean rightClickStroke = false;

	// ── Right-mouse-drag zoom state (Google Earth style) ─────────────────────
	/** Viewport-relative position of the press point. Stable across zoom changes. */
	private Point   rightZoomVpStart    = null;
	/** Zoom level at right-button press, base for the exponential ramp. */
	private double  rightZoomStartZoom  = 1.0;
	/** True once the right-drag has crossed the threshold and taken over as a zoom gesture. */
	private boolean isRightZooming      = false;
	private static final int    RIGHT_ZOOM_THRESHOLD_PX  = 5;
	private static final double RIGHT_ZOOM_FACTOR_PER_PX = 1.01;  // 1 % per pixel of vertical drag

	// ── Text tool state ───────────────────────────────────────────────────────
	private static final int TEXT_PADDING = 4;

	/**
	 * Image-space bounding box of the active text input session.
	 * null = no active text input.  Expands automatically as text grows.
	 */
	private Rectangle textBoundingBox = null;
	/** True while the user is rubber-banding the initial text frame (mouse still held). */
	private boolean   textDrawingBox  = false;
	/** Drag-start image-space point for the rubber-band frame. */
	private Point     textDragStart   = null;
	/** The user's drawn frame (minimum size; textBoundingBox may be larger). */
	private Rectangle textMinBox      = null;

	private StringBuilder textBuffer = new StringBuilder();

	// Text-chooser settings (persisted; bidirectionally synced with dialog)
	private String  textFontName  = "SansSerif";
	private int     textFontSize  = 24;
	private boolean textBold      = false;
	private boolean textItalic    = false;
	private Color   textColor     = Color.BLACK;

	/** If >= 0, we are editing an existing TEXT_LAYER with this id. */
	private int  editingTextElementId = -1;
	/**
	 * When entering edit mode for an existing TEXT_LAYER, the original element is
	 * removed from activeElements and stored here; it is restored if the user cancels.
	 */
	private Layer editingOriginalElement = null;
	/** True when the element being edited is a wrapping TextLayer (book page text frame). */
	private boolean editingWrappingLayer = false;
	/** Per-keystroke text snapshots for scoped undo while editing the wrapping frame. */
	private final java.util.ArrayDeque<String> textUndoStack = new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<String> textRedoStack = new java.util.ArrayDeque<>();

	// ── Text caret + selection ─────────────────────────────────────────────────
	/** Character index of the insertion caret (0 = before first char). */
	private int textCaretPos       = 0;
	/** Anchor for Shift-extend selection. -1 = no active selection. */
	private int textSelAnchor      = -1;
	/** Blink timer for the caret. */
	private javax.swing.Timer textCaretTimer  = null;
	private boolean            textCaretVisible = true;
	/** True while user is drag-selecting text with the mouse. */
	private boolean textMouseDragSel = false;
	/** Cached visual line layout from last paint (used for mouse hit-testing). */
	private java.util.List<TLine> lastTextLines = null;
	private int lastTextSx = 0, lastTextSy = 0, lastTextLineH = 0;

	/** One visual line in the wrapped/unwrapped text layout. */
	private record TLine(int start, int end, String text) {}


	// ── Free-path drawing state ──────────────────────────────────────────────────
	/** Raw image-space points collected during FREE_PATH drag. null = not drawing. */
	private java.util.List<java.awt.Point> freePathPoints = null;
	/** Screen-space points for live preview during FREE_PATH drag. */
	private java.util.List<java.awt.Point> freePathScreenPoints = null;
	/** Snap threshold in image pixels: connect to existing path endpoint within this distance. */
	private static final int FREE_PATH_SNAP = 12;

	// ── Path editor state ────────────────────────────────────────────────────────
	/** Index of the currently hovered path point (for PathLayer), or -1. */
	private int hoveredPathPointIndex = -1;
	/** Index of the currently selected path point (for PathLayer), or -1. */
	private int selectedPathPointIndex = -1;

	// ── Hover highlight (bidirectional with ElementLayerPanel) ───────────────
	/** Id of the element the mouse is currently over, or -1. Used for bidirectional hover. */
	private int hoveredElementId = -1;
	/** Index (0-7) of the scale handle the mouse is over on the primary element, or -1. */
	private int hoveredHandleIndex = -1;

	// ── Brush-Preview Cursor ─────────────────────────────────────────────────
	/** Screen-space position for the dashed brush-shape preview, or null when outside canvas. */
	private Point brushPreviewPt = null;
	/** Invisible cursor used while PENCIL/ERASER is active so the dashed preview acts as cursor. */
	private static final java.awt.Cursor BLANK_CURSOR = java.awt.Toolkit.getDefaultToolkit()
			.createCustomCursor(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
					new Point(0, 0), "blank");

	// ── Font cache (avoids per-frame Font allocation in paintComponent) ──────
	private final java.util.Map<String, Font> fontCache = new java.util.HashMap<>();
	private Font cachedFont(String name, int style, int size) {
		String key = name + '|' + style + '|' + size;
		Font f = fontCache.get(key);
		if (f == null) {
			f = new Font(name, style, size);
			if (fontCache.size() > 64) fontCache.clear();  // bound cache
			fontCache.put(key, f);
		}
		return f;
	}

	// ── Checkerboard cache ───────────────────────────────────────────────────
	/** Cached 2×2-cell tile used to fill the canvas checkerboard in a single fillRect. */
	private java.awt.TexturePaint cachedCheckerPaint = null;
	private Color cachedCheckerBg1 = null;
	private Color cachedCheckerBg2 = null;

	// ── Cached paint primitives (avoid per-frame allocation in paintComponent) ──
	private static final float[] SEL_DASH = { 5f, 3f };
	private static final float[] DIM_DASH = { 3f, 3f };
	private static final BasicStroke STROKE_1        = new BasicStroke(1f);
	private static final BasicStroke STROKE_1_5      = new BasicStroke(1.5f);
	private static final BasicStroke STROKE_SEL_DASH = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, SEL_DASH, 0f);
	private static final BasicStroke STROKE_DIM_DASH = new BasicStroke(1f,   BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, DIM_DASH, 0f);
	private static final BasicStroke STROKE_FREEPATH = new BasicStroke(2f);
	private static final Color COLOR_HANDLE_HOV_FILL   = new Color(255, 220, 60);
	private static final Color COLOR_HANDLE_HOV_BORDER = new Color(200, 140, 0);
	private static final Color COLOR_ROT_HOV           = new Color(255, 180, 0);
	private static final Color COLOR_ROT               = new Color(100, 180, 255);
	private static final Color COLOR_HOVER_OUTLINE     = new Color(255, 200, 80, 200);
	private static final Color COLOR_DIM_OUTLINE       = new Color(160, 160, 160, 140);
	private static final Color COLOR_FREEPATH_LINE     = new Color(80, 160, 255, 200);
	private static final Color COLOR_FREEPATH_START    = new Color(255, 200, 60);

	// ── Canvas-submode draw overlay ───────────────────────────────────────────
	/** Transparent scratch image used when drawing in Canvas sub-mode (becomes an Element on release). */
	private BufferedImage canvasDrawOverlay = null;
	/** Last image-space point for pencil strokes on the overlay. */
	private Point   overlayLastPt = null;
	/** Start point for shape tools drawing on the overlay. */
	private Point   overlayShapeStart = null;

	public CanvasPanel(CanvasCallbacks callbacks) {
		this.callbacks = callbacks;
		setOpaque(false);
		setFocusable(true);
		setupMouseHandling();
		setupKeyBindings();
	}

	/** Called by ElementLayerPanel when the mouse enters/leaves a tile. */
	// ──────────────────────────────────────────────────────────────
	// Text-Tool Getter/Setter für Settings-Persistierung
	// ──────────────────────────────────────────────────────────────

	public String getTextFontName() { return textFontName; }
	public void setTextFontName(String name) { textFontName = name; }

	public int getTextFontSize() { return textFontSize; }
	public void setTextFontSize(int size) { textFontSize = Math.max(6, size); }

	public boolean isTextBold() { return textBold; }
	public void setTextBold(boolean b) { textBold = b; }

	public boolean isTextItalic() { return textItalic; }
	public void setTextItalic(boolean i) { textItalic = i; }

	public Color getTextColor() { return textColor; }
	public void setTextColor(Color c) { textColor = c; }

	public void setHoveredElementId(int id) {
		if (hoveredElementId == id) return;
		hoveredElementId = id;
		repaint();
	}

	/**
	 * Resets all transient input state (text, overlay, pan, element drag, …).
	 * Called by SelectiveAlphaEditor when switching to a different image so no
	 * stale state from the previous image carries over.
	 */
	public void resetInputState() {
		panStart = null; panViewPos = null;
		elemBandStart = null; elemBandEnd = null; isElemBanding = false;
		elemLastImgPt = null;
		// Cancel any in-progress text (without committing)
		if (editingOriginalElement != null) {
			// restore original — but activeElements belongs to the other image now, so just discard
		}
		textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
		textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
		editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
		stopCaretBlink(); callbacks.hideTextToolbar();
		// Reset path editor state
		hoveredPathPointIndex = -1; selectedPathPointIndex = -1;
		// Cancel canvas draw overlay
		canvasDrawOverlay = null; overlayLastPt = null; overlayShapeStart = null;
		// Hover state
		hoveredElementId = -1;
		hoveredHandleIndex = -1;
		// Discard any pending undo carried over from a previous image
		pendingUndo = false;
	}

	private void setupMouseHandling() {
		MouseAdapter handler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();

				// Arm right-mouse-drag zoom (Google-Earth style). The zoom only
				// engages once the drag exceeds the threshold; until then the
				// existing right-click logic (snap-drag, secondary-color paint,
				// etc.) still runs below. Store the press point in VIEWPORT
				// coordinates — that frame doesn't drift when the canvas resizes.
				boolean isCtrlRight = SwingUtilities.isRightMouseButton(e)
						&& (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
				if (isCtrlRight) {
					rightZoomVpStart   = SwingUtilities.convertPoint(
							CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					rightZoomStartZoom = callbacks.getZoom();
					isRightZooming     = false;
					return; // suppress all paint/snap-drag logic for CTRL+right
				}

				boolean isMiddle = (e.getButton() == MouseEvent.BUTTON2);
				boolean isCtrlDrg = SwingUtilities.isLeftMouseButton(e)
						&& (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
				if (isMiddle || isCtrlDrg) {
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					panStart = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					panViewPos = callbacks.getScrollPane().getViewport().getViewPosition();
					return;
				}

				boolean isLeft = SwingUtilities.isLeftMouseButton(e);
				boolean isRight = SwingUtilities.isRightMouseButton(e);
				if ((!isLeft && !isRight) || callbacks.getWorkingImage() == null)
					return;

				// ── Right-click: snap-drag OR secondary-color paint ──────────────────────
				if (isRight) {
					boolean isPaintDrawTool = false;
					if (callbacks.getAppMode() == AppMode.PAINT && callbacks.getPaintToolbar() != null) {
						PaintEngine.Tool t = callbacks.getPaintToolbar().getActiveTool();
						isPaintDrawTool = t == PaintEngine.Tool.PENCIL
								|| t == PaintEngine.Tool.ERASER
								|| t == PaintEngine.Tool.ERASER_BG
								|| t == PaintEngine.Tool.ERASER_COLOR
								|| t == PaintEngine.Tool.SMEAR
								|| t == PaintEngine.Tool.FLOODFILL
								|| t == PaintEngine.Tool.EYEDROPPER;
					}
					if (!isPaintDrawTool) {
						// Snap-drag mode (non-paint tools)
						Point imgPt = callbacks.screenToImage(e.getPoint());
						Layer hit = hitElement(e.getPoint());
						if (hit != null) {
							if (!callbacks.getSelectedElements().contains(hit)) {
								callbacks.setSelectedElement(hit);
							}
							isSnapDragging = true;
							elemLastImgPt = imgPt;
							pendingUndo = true;  // commit on first real move
						}
						return;
					}
					// else: fall through to the PAINT switch with rightClickStroke = true
					rightClickStroke = true;
				} else {
					rightClickStroke = false;
				}

				// ── In-canvas caret positioning (click/double-click/drag inside active text box) ─
				if (textBoundingBox != null && !textDrawingBox && lastTextLines != null) {
					double z = callbacks.getZoom();
					int tbSx = (int)(textBoundingBox.x * z);
					int tbSy = (int)(textBoundingBox.y * z);
					int tbW  = editingWrappingLayer ? (int)(textBoundingBox.width  * z) : 99999;
					int tbH  = editingWrappingLayer ? (int)(textBoundingBox.height * z) : 99999;
					Rectangle tbRect = new Rectangle(tbSx - 2, tbSy - 2, tbW + 4, tbH + 4);
					if (tbRect.contains(e.getPoint())) {
						int style = (textBold ? Font.BOLD : 0) | (textItalic ? Font.ITALIC : 0);
						int scSz  = Math.max(1, (int) Math.round(Math.max(6, textFontSize) * z));
						java.awt.FontMetrics fm = getFontMetrics(new Font(textFontName, style, scSz));
						int clickedPos = screenToCaretPos(e.getPoint(), lastTextLines, fm, lastTextSx, lastTextSy, lastTextLineH);
						if (e.getClickCount() == 1) {
							if (!e.isShiftDown()) { textCaretPos = clickedPos; clearTextSel(); }
							else { if (textSelAnchor < 0) textSelAnchor = textCaretPos; textCaretPos = clickedPos; }
							textMouseDragSel = true;
						} else if (e.getClickCount() == 2) {
							int ws = wordStart(clickedPos), we = wordEnd(clickedPos);
							textSelAnchor = ws; textCaretPos = we;
						} else if (e.getClickCount() >= 3) {
							int li = findLineForCaret(clickedPos, lastTextLines);
							textSelAnchor = lastTextLines.get(li).start();
							textCaretPos  = lastTextLines.get(li).end();
						}
						textCaretVisible = true; repaint(); return;
					}
				}

				// Commit any open text session when clicking elsewhere
				if (textBoundingBox != null && !textDrawingBox) {
					PaintEngine.Tool curTool = callbacks.getAppMode() == AppMode.PAINT
					        ? callbacks.getPaintToolbar().getActiveTool() : null;
					if (curTool != PaintEngine.Tool.TEXT) {
						commitText();
					}
					// TEXT tool click-away → commitText() first, then start new frame below
				}

				Point imgPt = callbacks.screenToImage(e.getPoint());

				// ── Rotation handle for selected element (highest priority) ─────────
				Layer primaryEl = callbacks.getSelectedElement();
				if (primaryEl != null && primaryEl instanceof ImageLayer il) {
					Rectangle elemRectSc = callbacks.elemRectScreen(primaryEl);
					Rectangle rotHandleRect = callbacks.getRotationHandleRect(elemRectSc);
					if (rotHandleRect.contains(e.getPoint())) {
						// Start free-rotation drag
						isDraggingRotation = true;
						double cx = elemRectSc.x + elemRectSc.width / 2.0;
						double cy = elemRectSc.y + elemRectSc.height / 2.0;
						rotDragStartAngle = Math.toDegrees(Math.atan2(e.getY() - cy, e.getX() - cx));
						rotDragBaseAngle = il.rotationAngle();
						pendingUndo = true;  // commit on first real move
						return;
					}
				}

				// ── PathLayer interaction ────────────────────────────────────────
				primaryEl = callbacks.getSelectedElement();
				if (primaryEl instanceof PathLayer pl) {
					// 1. Scale handles (outermost priority — must not be swallowed by deselect)
					Rectangle[] plHandles = callbacks.handleRects(callbacks.elemRectScreen(pl));
					for (int hi = 0; hi < plHandles.length; hi++) {
						if (plHandles[hi].contains(e.getPoint())) {
							callbacks.setElemActiveHandle(hi);
							callbacks.setElemScaleBase(new Rectangle(pl.x(), pl.y(), pl.width(), pl.height()));
							callbacks.setElemScaleStart(e.getPoint());
							return;
						}
					}

					// 2. Control-point hit (for individual point editing)
					// Use layer origin directly — elemRectScreen has padding that would offset coords.
					double zoom = callbacks.getZoom();
					double layerX = e.getPoint().x / zoom - pl.x();
					double layerY = e.getPoint().y / zoom - pl.y();

					java.util.List<Point3D> points = pl.points();
					int hitRadius = 12;
					int hitPoint = -1;
					for (int i = 0; i < points.size(); i++) {
						Point3D p = points.get(i);
						double dist = Math.sqrt(Math.pow(layerX - p.x, 2) + Math.pow(layerY - p.y, 2));
						if (dist <= hitRadius) { hitPoint = i; break; }
					}
					if (hitPoint >= 0) {
						selectedPathPointIndex = hitPoint;
						repaint();
						return;
					}

					// 3. Click inside polygon → drag whole path
					if (isInsidePathPolygon(pl, e.getPoint())) {
						callbacks.setDraggingElement(true);
						elemLastImgPt = imgPt;
						repaint();
						return;
					}

					// 4. Click outside everything → deselect
					callbacks.setSelectedElement(null);
					selectedPathPointIndex = -1;
				}

				if (!callbacks.getActiveElements().isEmpty() && callbacks.getFloatingImage() == null) {
					boolean shiftDown = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;

					if (shiftDown) {
						// Shift+click → toggle element in/out of selection
						// Shift+drag (not on element) → start rubber-band multi-select
						Layer hit = hitElement(e.getPoint());
						if (hit != null) {
							callbacks.toggleElementSelection(hit);
						} else {
							isElemBanding = true;
							elemBandStart = e.getPoint();
							elemBandEnd   = e.getPoint();
						}
						repaint();
						return;
					}

					// Double-click dispatch by layer type (all tools)
					Layer hit = hitElement(e.getPoint());
					if (e.getClickCount() == 2 && hit != null) {
						if (hit instanceof TextLayer) {
							enterTextEditMode(hit);
						} else if (hit instanceof ImageLayer) {
							callbacks.openImageLayerForEditing(hit);
						}
						// PathLayer: ignore double-click here (handled via path editor dialog in panel)
						return;
					}
					// In book mode, double-click anywhere (even in margins) opens the wrapping TextLayer
					if (e.getClickCount() == 2 && hit == null && callbacks.isBookMode()) {
						for (Layer el : callbacks.getActiveElements()) {
							if (el instanceof TextLayer tl && tl.isWrapping()) {
								enterTextEditMode(el);
								return;
							}
						}
					}

					// Check resize handles on the primary selected element
					Layer primary = callbacks.getSelectedElement();
					if (primary != null && !primary.isMouseTransparent()
							&& !(primary instanceof TextLayer ptl && ptl.isWrapping())) {
						Rectangle[] handles = callbacks.handleRects(callbacks.elemRectScreen(primary));
						for (int hi = 0; hi < handles.length; hi++) {
							if (handles[hi].contains(e.getPoint())) {
								callbacks.setElemActiveHandle(hi);
								callbacks.setElemScaleBase(new Rectangle(
										primary.x(), primary.y(), primary.width(), primary.height()));
								callbacks.setElemScaleStart(e.getPoint());
								return;
							}
						}
					}

					// Click on any currently selected element → drag ALL selected
					for (Layer sel : callbacks.getSelectedElements()) {
						if (sel.isMouseTransparent()) continue;
						if (sel instanceof TextLayer stl && stl.isWrapping()) continue;
						if (callbacks.elemRectScreen(sel).contains(e.getPoint())) {
							callbacks.setDraggingElement(true);
							elemLastImgPt = imgPt;
							return;
						}
					}

					// Click on an unselected element → single-select + drag
					if (hit != null) {
						callbacks.setSelectedElement(hit);
						callbacks.setDraggingElement(true);
						elemLastImgPt = imgPt;
						repaint();
						return;
					} else {
						// Click on empty area → deselect all, then fall through to tool switch
						// (TEXT tool: TEXT case sets textDragStart; other tools: handle their own logic)
						if (!callbacks.getSelectedElements().isEmpty()) {
							callbacks.setSelectedElement(null);
							repaint();
						}
					}
				}

				// DOUBLE-CLICK on an active selection → instantly lift selection to element
				if (e.getClickCount() >= 2 && callbacks.getFloatingImage() == null) {
					Rectangle dblSel = callbacks.getActiveSelection();
					if (dblSel != null && dblSel.width > 0 && dblSel.height > 0) {
						double z = callbacks.getZoom();
						Rectangle dblScr = new Rectangle(
								(int) Math.round(dblSel.x * z),
								(int) Math.round(dblSel.y * z),
								(int) Math.round(dblSel.width  * z),
								(int) Math.round(dblSel.height * z));
						if (dblScr.contains(e.getPoint())) {
							callbacks.liftSelectionToElement(dblSel);
							return;
						}
					}
				}

				if (callbacks.getFloatingImage() != null) {
					int h = callbacks.hitHandle(e.getPoint());
					if (h >= 0) {
						callbacks.setActiveHandle(h);
						callbacks.setScaleBaseRect(new Rectangle(callbacks.getFloatRect()));
						callbacks.setScaleDragStart(e.getPoint());
						return;
					} else if (callbacks.floatRectScreen().contains(e.getPoint())) {
						callbacks.setDraggingFloat(true);
						callbacks.setFloatDragAnchor(
								new Point(imgPt.x - callbacks.getFloatRect().x, imgPt.y - callbacks.getFloatRect().y));
						return;
					} else {
						callbacks.commitFloat();
					}
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					if (tool == null) return;
					switch (tool) {
					case ERASER_BG -> {
						callbacks.pushUndo();
						callbacks.setLastPaintPoint(imgPt);
						int sw = callbacks.getPaintToolbar().getStrokeWidth();
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						PaintEngine.drawEraserBG(callbacks.getWorkingImage(), imgPt, imgPt,
								callbacks.getPaintToolbar().getSecondaryColor(), sw, aa);
						callbacks.markDirty();
						callbacks.repaintCanvas();
					}
					case ERASER_COLOR -> {
						callbacks.pushUndo();
						callbacks.setLastPaintPoint(imgPt);
						int sw = callbacks.getPaintToolbar().getStrokeWidth();
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						PaintEngine.drawColorEraser(callbacks.getWorkingImage(), imgPt, imgPt,
								callbacks.getPaintToolbar().getPrimaryColor(),
								callbacks.getPaintToolbar().getSecondaryColor(),
								sw, callbacks.getPaintToolbar().getWandTolerance(), aa);
						callbacks.markDirty();
						callbacks.repaintCanvas();
					}
					case PENCIL, ERASER -> {
						if (callbacks.isCanvasSubMode() && tool == PaintEngine.Tool.PENCIL && !rightClickStroke) {
							// Canvas sub-mode: draw on a transparent overlay → becomes Element on release
							canvasDrawOverlay = newOverlay();
							overlayLastPt = imgPt;
							if (canvasDrawOverlay != null) {
								PaintEngine.drawPencil(canvasDrawOverlay, imgPt, imgPt,
										callbacks.getPaintToolbar().getPrimaryColor(),
										callbacks.getPaintToolbar().getStrokeWidth(),
										callbacks.getPaintToolbar().getBrushShape(),
										callbacks.getPaintToolbar().isAntialiasing());
							}
							repaint();
						} else {
							Color strokeColor = rightClickStroke
									? callbacks.getPaintToolbar().getSecondaryColor()
									: callbacks.getPaintToolbar().getPrimaryColor();
							callbacks.pushUndo();
							callbacks.setLastPaintPoint(imgPt);
							int sw = callbacks.getPaintToolbar().getStrokeWidth();
							boolean aa = callbacks.getPaintToolbar().isAntialiasing();
							Color secondary = callbacks.getPaintToolbar().getSecondaryColor();
							if (tool == PaintEngine.Tool.ERASER) {
								if (rightClickStroke)
									PaintEngine.drawEraserBG(callbacks.getWorkingImage(), imgPt, imgPt, secondary, sw, aa);
								else
									PaintEngine.drawEraser(callbacks.getWorkingImage(), imgPt, imgPt, sw, aa);
							} else if (strokeColor.getAlpha() == 0) {
								PaintEngine.drawEraser(callbacks.getWorkingImage(), imgPt, imgPt, sw, aa);
							} else {
								PaintEngine.drawPencil(callbacks.getWorkingImage(), imgPt, imgPt,
										strokeColor, sw, callbacks.getPaintToolbar().getBrushShape(), aa);
							}
							callbacks.markDirty();
							callbacks.repaintCanvas();
						}
					}
					case SMEAR -> {
						callbacks.pushUndo();
						callbacks.setLastPaintPoint(imgPt);
					}
					case FLOODFILL -> {
						callbacks.pushUndo();
						Color fillColor = rightClickStroke
								? callbacks.getPaintToolbar().getSecondaryColor()
								: callbacks.getPaintToolbar().getPrimaryColor();
						PaintEngine.floodFill(callbacks.getWorkingImage(), imgPt.x, imgPt.y, fillColor, 30);
						callbacks.markDirty();
					}
					case EYEDROPPER -> {
						Color picked = PaintEngine.pickColor(callbacks.getWorkingImage(), imgPt.x, imgPt.y);
						if (rightClickStroke)
							callbacks.getPaintToolbar().setSecondaryColor(picked);
						else
							callbacks.getPaintToolbar().setSelectedColor(picked);
					}
					case LINE, CIRCLE, RECT -> {
						if (callbacks.isCanvasSubMode()) {
							// Canvas sub-mode: draw on overlay
							canvasDrawOverlay = newOverlay();
							overlayShapeStart = imgPt;
							} else {
							callbacks.pushUndo();
							callbacks.setShapeStartPoint(imgPt);
							callbacks.setPaintSnapshot(callbacks.deepCopy(callbacks.getWorkingImage()));
						}
					}
					case SELECT -> {
						Rectangle existingSel = callbacks.getActiveSelection();
						if (existingSel != null) {
							// Selection exists – check handles, inside, outside
							int sx = (int) Math.round(existingSel.x * callbacks.getZoom());
							int sy = (int) Math.round(existingSel.y * callbacks.getZoom());
							int sw = (int) Math.round(existingSel.width  * callbacks.getZoom());
							int sh = (int) Math.round(existingSel.height * callbacks.getZoom());
							Rectangle selScr = new Rectangle(sx, sy, sw, sh);
							Rectangle[] handles = callbacks.handleRects(selScr);
							boolean hitHandle = false;
							for (int hi = 0; hi < handles.length; hi++) {
								if (handles[hi].contains(e.getPoint())) {
									if (callbacks.isCanvasSubMode()) {
										// Canvas sub-mode: lift selection into Element, then scale
										callbacks.liftSelectionToElement(existingSel);
									} else {
										// Handle hit → lift pixels, then start float scale-resize
										callbacks.liftSelectionToFloat();
										Rectangle fr = callbacks.getFloatRect();
										if (fr != null) {
											callbacks.setActiveHandle(hi);
											callbacks.setScaleBaseRect(new Rectangle(fr));
											callbacks.setScaleDragStart(e.getPoint());
										}
									}
									hitHandle = true;
									break;
								}
							}
							if (!hitHandle) {
								if (selScr.contains(e.getPoint())) {
									if (callbacks.isCanvasSubMode()) {
										// Canvas sub-mode: lift selection into Element layer + start drag
										callbacks.liftSelectionToElement(existingSel);
										// Find the newly created element (last in list) and start dragging
										java.util.List<Layer> els = callbacks.getActiveElements();
										if (!els.isEmpty()) {
											Layer newEl = els.get(els.size() - 1);
											callbacks.setSelectedElement(newEl);
											callbacks.setDraggingElement(true);
											elemLastImgPt = imgPt;
										}
									} else {
										// Normal mode: lift pixels, then start float drag
										callbacks.liftSelectionToFloat();
										Rectangle fr = callbacks.getFloatRect();
										if (fr != null) {
											callbacks.setDraggingFloat(true);
											callbacks.setFloatDragAnchor(
													new Point(imgPt.x - fr.x, imgPt.y - fr.y));
										}
									}
								} else {
									// Click outside → clear selection, start new
									callbacks.clearSelection();
									callbacks.setSelecting(true);
									callbacks.setSelectionStart(imgPt);
									callbacks.setSelectionEnd(imgPt);
								}
							}
						} else {
							callbacks.setSelecting(true);
							callbacks.setSelectionStart(imgPt);
							callbacks.setSelectionEnd(imgPt);
						}
					}
					case TEXT -> {
						// Rubber-band frame starts only on mouseDragged (not on bare press).
						// mousePressed just commits any open text and records the drag start.
						// The frame is NOT created here — it is created once the user actually drags
						// (see mouseDragged TEXT case) and finalised in mouseReleased.
						commitText();
						editingTextElementId   = -1;
						editingOriginalElement = null;
						textBuffer.setLength(0);
						textDragStart  = imgPt;
						textDrawingBox = false;   // not active yet — starts in mouseDragged
						textBoundingBox = null;
						textMinBox      = null;
					}
					case PATH -> {
						// PATH tool: first click creates new path with 3 initial points in triangle shape
						// Otherwise, do nothing (user edits by dragging points)
						Layer primary = callbacks.getSelectedElement();
						if (!(primary instanceof PathLayer)) {
							// Create new path with 3 starter points in triangle arrangement
							// Points are positioned within the bounding box (10-90 on a 100x100 box)
							java.util.List<Point3D> points = new java.util.ArrayList<>();
							points.add(new Point3D(50, 10));     // Top
							points.add(new Point3D(10, 90));     // Bottom-left
							points.add(new Point3D(90, 90));     // Bottom-right

							PathLayer newPath = PathLayer.of(callbacks.getNextElementId(), points, null, true, imgPt.x, imgPt.y);
							callbacks.addElement(newPath);
							callbacks.setSelectedElement(newPath);

							// Deselect PATH tool after creating path (switch to SELECT to prevent accidental path creation)
							callbacks.getPaintToolbar().setActiveTool(PaintEngine.Tool.SELECT);

						}
						repaint();
					}
					case FREE_PATH, WAND_IV -> {
						// Begin freehand path drawing; first point may snap to existing path endpoint
						freePathPoints = new java.util.ArrayList<>();
						freePathScreenPoints = new java.util.ArrayList<>();
						Point snapped = snapToPathEndpoint(imgPt);
						freePathPoints.add(snapped);
						freePathScreenPoints.add(e.getPoint());
						repaint();
					}
					case WAND_I -> handleWandI(imgPt);
					case WAND_II -> handleWandII(imgPt);
					case WAND_III -> handleWandIII(imgPt);
					case WAND_REPLACE_OUTER -> handleWandReplace(imgPt, true);
					case WAND_REPLACE_INNER -> handleWandReplace(imgPt, false);
					case WAND_AA_OUTER -> handleWandAA(imgPt, true);
					case WAND_AA_INNER -> handleWandAA(imgPt, false);
					}
				} else {
					if (callbacks.isFloodfillMode()) {
						callbacks.pushUndo();
						callbacks.performFloodfill(e.getPoint());
					} else {
						// Start a new selection (Shift adds to existing)
						callbacks.setSelecting(true);
						callbacks.setSelectionStart(imgPt);
						callbacks.setSelectionEnd(imgPt);
					}
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				// ── Right-mouse-drag zoom (intercepts everything once engaged) ──
				if (SwingUtilities.isRightMouseButton(e) && rightZoomVpStart != null
						&& (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					// Drag delta in VIEWPORT coords — independent of canvas resize.
					Point curVp = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					int dy = rightZoomVpStart.y - curVp.y;  // drag up = positive = zoom in
					int dx = curVp.x - rightZoomVpStart.x;
					if (!isRightZooming
							&& Math.max(Math.abs(dx), Math.abs(dy)) < RIGHT_ZOOM_THRESHOLD_PX) {
						// Below threshold: let normal right-click behavior keep running.
					} else {
						if (!isRightZooming) {
							isRightZooming = true;
							// Cancel any conflicting press-time state so release doesn't finalize it.
							isSnapDragging     = false;
							rightClickStroke   = false;
							panStart           = null;
							pendingUndo        = false;
							setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
							// Pin the image pixel under the ORIGINAL press point.
							// Pass the press-time canvas-space anchor once; subsequent
							// setZoomLive() calls reuse the pinned pixel.
							Point anchorCanvas = SwingUtilities.convertPoint(
									callbacks.getScrollPane().getViewport(),
									rightZoomVpStart, CanvasPanel.this);
							callbacks.beginZoomLive(anchorCanvas);
						}
						double newZoom = rightZoomStartZoom
								* Math.pow(RIGHT_ZOOM_FACTOR_PER_PX, dy);
						callbacks.setZoomLive(newZoom);
						return;
					}
				}

				if (panStart != null) {
					Point curVp = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
							callbacks.getScrollPane().getViewport());
					int dx = panStart.x - curVp.x;
					int dy = panStart.y - curVp.y;
					int newX = Math.max(0, panViewPos.x + dx);
					int newY = Math.max(0, panViewPos.y + dy);
					callbacks.getScrollPane().getViewport().setViewPosition(new Point(newX, newY));
					return;
				}

				// ── Text drag-selection ────────────────────────────────────────────
				if (textMouseDragSel && textBoundingBox != null && !textDrawingBox && lastTextLines != null) {
					double z = callbacks.getZoom();
					int style = (textBold ? Font.BOLD : 0) | (textItalic ? Font.ITALIC : 0);
					int scSz  = Math.max(1, (int) Math.round(Math.max(6, textFontSize) * z));
					java.awt.FontMetrics fm = getFontMetrics(new Font(textFontName, style, scSz));
					int dragPos = screenToCaretPos(e.getPoint(), lastTextLines, fm, lastTextSx, lastTextSy, lastTextLineH);
					if (textSelAnchor < 0) textSelAnchor = textCaretPos;
					textCaretPos = dragPos;
					textCaretVisible = true; repaint(); return;
				}

				// ── Rotation drag ──────────────────────────────────────────────────
				if (isDraggingRotation) {
					Layer el = callbacks.getSelectedElement();
					if (el instanceof ImageLayer il) {
						Rectangle sr = callbacks.elemRectScreen(el);
						double cx = sr.x + sr.width / 2.0;
						double cy = sr.y + sr.height / 2.0;
						double currentAngle = Math.toDegrees(Math.atan2(e.getY() - cy, e.getX() - cx));
						double delta = currentAngle - rotDragStartAngle;
						if (delta != 0) {
							commitPendingUndo();
							callbacks.updateSelectedElement(il.withRotation(rotDragBaseAngle + delta));
							callbacks.onElementTransformed();
							repaint();
						}
					}
					return;
				}

				if (callbacks.getWorkingImage() == null)
					return;
				Point imgPt = callbacks.screenToImage(e.getPoint());

				// ── Snap-drag ──────────────────────────────────────────────────────
				if (isSnapDragging) {
					if (elemLastImgPt != null) {
						int dx = imgPt.x - elemLastImgPt.x;
						int dy = imgPt.y - elemLastImgPt.y;
						if (dx != 0 || dy != 0) {
							commitPendingUndo();
							callbacks.moveSelectedElements(dx, dy);
							elemLastImgPt = imgPt;
							// Get the primary element and apply snap
							Layer primary = callbacks.getSelectedElement();
							if (primary != null) {
								applySnapToElement(primary);
							}
						}
					}
					repaint();
					return;
				}

				// ── PathLayer point drag ────────────────────────────────────────
				Layer primary = callbacks.getSelectedElement();
				if (primary instanceof PathLayer pl && selectedPathPointIndex >= 0) {
					PathLayer updated = pl.withMovedPoint(selectedPathPointIndex, imgPt.x - pl.x(), imgPt.y - pl.y());
					callbacks.updateSelectedElement(updated);
					repaint();
					return;
				}

				if (callbacks.isDraggingElement()) {
					if (elemLastImgPt != null) {
						int dx = imgPt.x - elemLastImgPt.x;
						int dy = imgPt.y - elemLastImgPt.y;
						if (dx != 0 || dy != 0) {
							callbacks.moveSelectedElements(dx, dy);
							elemLastImgPt = imgPt;
							callbacks.onElementTransformed();
						}
					}
					repaint();
					return;
				}

				if (isElemBanding) {
					elemBandEnd = e.getPoint();
					repaint();
					return;
				}

				if (callbacks.getElemActiveHandle() >= 0) {
					Rectangle r = callbacks.getElemScaleBase();
					double origW = r.width, origH = r.height;
					double origX = r.x, origY = r.y;
					double dx = (e.getPoint().x - callbacks.getElemScaleStart().x) / callbacks.getZoom();
					double dy = (e.getPoint().y - callbacks.getElemScaleStart().y) / callbacks.getZoom();

					Layer el = callbacks.getSelectedElement();
					int handle = callbacks.getElemActiveHandle();

					double nw, nh, nx, ny;
					if (handle == 0) {
						nw = Math.max(1, origW - dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX + origW - nw;
						ny = origY + origH - nh;
					} else if (handle == 2) {
						nw = Math.max(1, origW + dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX;
						ny = origY + origH - nh;
					} else if (handle == 5) {
						nw = Math.max(1, origW - dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX + origW - nw;
						ny = origY;
					} else if (handle == 7) {
						nw = Math.max(1, origW + dx);
						nh = Math.max(1, origH * nw / origW);
						nx = origX;
						ny = origY;
					} else if (handle == 1 || handle == 6) {
						nh = handle == 1 ? Math.max(1, origH - dy) : Math.max(1, origH + dy);
						nw = origW;
						nx = origX;
						ny = handle == 1 ? origY + origH - nh : origY;
					} else if (handle == 3 || handle == 4) {
						nw = handle == 3 ? Math.max(1, origW - dx) : Math.max(1, origW + dx);
						nh = origH;
						nx = handle == 3 ? origX + origW - nw : origX;
						ny = origY;
					} else {
						return;
					}

					Layer updated = el.withBounds((int) nx, (int) ny, (int) nw, (int) nh);
					callbacks.updateSelectedElement(updated);
					callbacks.onElementTransformed();
					repaint();
					return;
				}

				if (callbacks.isDraggingFloat()) {
					Rectangle fr = callbacks.getFloatRect();
					fr.x = (int) (imgPt.x - callbacks.getFloatDragAnchor().x);
					fr.y = (int) (imgPt.y - callbacks.getFloatDragAnchor().y);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getActiveHandle() >= 0) {
					Rectangle newR = computeNewFloatRect(callbacks.getActiveHandle(), callbacks.getScaleBaseRect(),
							callbacks.getScaleDragStart(), e.getPoint());
					if (newR != null) {
						Rectangle fr = callbacks.getFloatRect();
						fr.x = newR.x;
						fr.y = newR.y;
						fr.width = newR.width;
						fr.height = newR.height;
						callbacks.repaintCanvas();
					}
					return;
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					if (tool == PaintEngine.Tool.ERASER_BG) {
						int sw = callbacks.getPaintToolbar().getStrokeWidth();
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						Point prevPt = callbacks.getLastPaintPoint();
						if (prevPt == null) prevPt = imgPt;
						PaintEngine.drawEraserBG(callbacks.getWorkingImage(), prevPt, imgPt,
								callbacks.getPaintToolbar().getSecondaryColor(), sw, aa);
						callbacks.setLastPaintPoint(imgPt);
						callbacks.markDirty();
						repaintStrokeSegment(prevPt, imgPt, sw);
					} else if (tool == PaintEngine.Tool.ERASER_COLOR) {
						int sw = callbacks.getPaintToolbar().getStrokeWidth();
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						Point prevPt = callbacks.getLastPaintPoint();
						if (prevPt == null) prevPt = imgPt;
						PaintEngine.drawColorEraser(callbacks.getWorkingImage(), prevPt, imgPt,
								callbacks.getPaintToolbar().getPrimaryColor(),
								callbacks.getPaintToolbar().getSecondaryColor(),
								sw, callbacks.getPaintToolbar().getWandTolerance(), aa);
						callbacks.setLastPaintPoint(imgPt);
						callbacks.markDirty();
						repaintStrokeSegment(prevPt, imgPt, sw);
					} else if (tool == PaintEngine.Tool.PENCIL || tool == PaintEngine.Tool.ERASER) {
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						int strokeW = callbacks.getPaintToolbar().getStrokeWidth();
						if (callbacks.isCanvasSubMode() && tool == PaintEngine.Tool.PENCIL
								&& canvasDrawOverlay != null && !rightClickStroke) {
							// Canvas sub-mode: draw on overlay
							java.awt.Color primaryColor = callbacks.getPaintToolbar().getPrimaryColor();
							Point prevPt = overlayLastPt != null ? overlayLastPt : imgPt;
							if (primaryColor.getAlpha() == 0) {
								PaintEngine.drawEraser(canvasDrawOverlay, prevPt, imgPt, strokeW, aa);
							} else {
								PaintEngine.drawPencil(canvasDrawOverlay, prevPt, imgPt,
										primaryColor, strokeW,
										callbacks.getPaintToolbar().getBrushShape(), aa);
							}
							overlayLastPt = imgPt;
							repaintStrokeSegment(prevPt, imgPt, strokeW);
						} else if (tool == PaintEngine.Tool.PENCIL) {
							java.awt.Color strokeColor = rightClickStroke
									? callbacks.getPaintToolbar().getSecondaryColor()
									: callbacks.getPaintToolbar().getPrimaryColor();
							Point prevPt = callbacks.getLastPaintPoint();
							if (strokeColor.getAlpha() == 0) {
								PaintEngine.drawEraser(callbacks.getWorkingImage(), prevPt, imgPt, strokeW, aa);
							} else {
								PaintEngine.drawPencil(callbacks.getWorkingImage(), prevPt, imgPt,
										strokeColor, strokeW,
										callbacks.getPaintToolbar().getBrushShape(), aa);
							}
							callbacks.setLastPaintPoint(imgPt);
							callbacks.markDirty();
							repaintStrokeSegment(prevPt, imgPt, strokeW);
						} else {
							Point prevPt = callbacks.getLastPaintPoint();
							PaintEngine.drawEraser(callbacks.getWorkingImage(), prevPt, imgPt, strokeW, aa);
							callbacks.setLastPaintPoint(imgPt);
							callbacks.markDirty();
							repaintStrokeSegment(prevPt, imgPt, strokeW);
						}
					} else if (tool == PaintEngine.Tool.SMEAR) {
						Point last = callbacks.getLastPaintPoint();
						if (last == null) last = imgPt;
						int strokeW = callbacks.getPaintToolbar().getStrokeWidth();
						PaintEngine.smear(callbacks.getWorkingImage(), last, imgPt, strokeW, 0.65f);
						callbacks.setLastPaintPoint(imgPt);
						callbacks.markDirty();
						repaintStrokeSegment(last, imgPt, strokeW);
					} else if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE
							|| tool == PaintEngine.Tool.RECT) {
						if (callbacks.isCanvasSubMode() && canvasDrawOverlay != null
								&& overlayShapeStart != null) {
							// Canvas sub-mode: preview shape on fresh overlay copy each tick
							BufferedImage wi = callbacks.getWorkingImage();
							if (wi != null) {
								// Re-create overlay each tick so preview is clean
								canvasDrawOverlay = newOverlay();
								drawShapeOnOverlay(tool, overlayShapeStart, imgPt);
								repaint();
							}
						} else if (callbacks.getPaintSnapshot() != null && callbacks.getWorkingImage() != null) {
							// Normal mode: restore snapshot, draw preview
							copyInto(callbacks.getPaintSnapshot(), callbacks.getWorkingImage());
							drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
							callbacks.markDirty();
							callbacks.repaintCanvas();
						}
					} else if (tool == PaintEngine.Tool.SELECT) {
						// SELECT tool in PAINT mode: draw selection frame
						callbacks.setSelectionEnd(imgPt);
						callbacks.repaintCanvas();
					} else if (tool == PaintEngine.Tool.TEXT && textDragStart != null) {
						// TEXT tool: rubber-band frame (activate on first drag move)
						textDrawingBox = true;
						int bx = Math.min(textDragStart.x, imgPt.x);
						int by = Math.min(textDragStart.y, imgPt.y);
						int bw = Math.max(2, Math.abs(imgPt.x - textDragStart.x));
						int bh = Math.max(2, Math.abs(imgPt.y - textDragStart.y));
						textBoundingBox = new Rectangle(bx, by, bw, bh);
						repaint();
					} else if ((tool == PaintEngine.Tool.FREE_PATH || tool == PaintEngine.Tool.WAND_IV)
							&& freePathPoints != null) {
						// Collect raw image-space and screen-space points for live preview
						Point last = freePathPoints.get(freePathPoints.size() - 1);
						if (Math.abs(imgPt.x - last.x) > 1 || Math.abs(imgPt.y - last.y) > 1) {
							freePathPoints.add(imgPt);
							freePathScreenPoints.add(e.getPoint());
							repaint();
						}
					}
				} else if (callbacks.isAlphaPaintMode()) {
					// Alpha Paint mode: draw transparent strokes with eraser-like tool
					if (callbacks.getWorkingImage() != null) {
						Point lastPt = callbacks.getLastPaintPoint();
						if (lastPt == null) lastPt = imgPt;
						PaintEngine.drawEraser(callbacks.getWorkingImage(), lastPt, imgPt,
								20, true);  // Fixed stroke width of 20, antialiasing on
						callbacks.setLastPaintPoint(imgPt);
						callbacks.markDirty();
					}
				} else {
					callbacks.setSelectionEnd(imgPt);
					callbacks.repaintCanvas();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				// Right-mouse-drag zoom: if we engaged, consume the release.
				if (isRightZooming) {
					isRightZooming   = false;
					rightZoomVpStart = null;
					callbacks.endZoomLive();
					setCursor(Cursor.getDefaultCursor());
					return;
				}
				rightZoomVpStart = null;

				panStart = null;
				panViewPos = null;
				elemLastImgPt = null;
				textMouseDragSel = false;
				rightClickStroke = false;
				// Discard any pending undo that was never committed (click without drag)
				pendingUndo = false;

				// Finalize rotation drag
				if (isDraggingRotation) {
					isDraggingRotation = false;
					callbacks.markDirty();
					return;
				}

				// Finalize snap-drag
				if (isSnapDragging) {
					isSnapDragging = false;
					callbacks.markDirty();
					return;
				}

				if (callbacks.getWorkingImage() == null)
					return;

				// Finalize TEXT tool frame (if a rubber-band drag happened)
				if (textDragStart != null) {
					if (textDrawingBox && textBoundingBox != null
							&& textBoundingBox.width >= 10 && textBoundingBox.height >= 6) {
						// Real drag → enter text-input mode
						textDrawingBox = false;
						textMinBox = new Rectangle(textBoundingBox);
						requestFocusInWindow();
						repaint();
					} else {
						// Bare click (no drag) → discard, nothing happens
						textDrawingBox  = false;
						textBoundingBox = null;
						textMinBox      = null;
						repaint();
					}
					textDragStart = null;
					return;
				}

				// Finalize rubber-band element multi-select
				if (isElemBanding) {
					isElemBanding = false;
					if (elemBandStart != null && elemBandEnd != null) {
						int bx = Math.min(elemBandStart.x, elemBandEnd.x);
						int by = Math.min(elemBandStart.y, elemBandEnd.y);
						int bw = Math.abs(elemBandEnd.x - elemBandStart.x);
						int bh = Math.abs(elemBandEnd.y - elemBandStart.y);
						if (bw > 2 || bh > 2) {
							Rectangle band = new Rectangle(bx, by, bw, bh);
							java.util.List<Layer> newSel = new java.util.ArrayList<>();
							for (Layer el : callbacks.getActiveElements()) {
								if (callbacks.elemRectScreen(el).intersects(band)) newSel.add(el);
							}
							callbacks.setSelectedElements(newSel);
						}
					}
					elemBandStart = null;
					elemBandEnd   = null;
					repaint();
					return;
				}

				boolean wasScaling = callbacks.getElemActiveHandle() >= 0;
				callbacks.setDraggingElement(false);
				callbacks.setElemActiveHandle(-1);
				if (wasScaling) { callbacks.markDirty(); repaint(); return; }

				if (callbacks.isDraggingFloat()) {
					callbacks.setDraggingFloat(false);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getActiveHandle() >= 0) {
					callbacks.setActiveHandle(-1);
					callbacks.setScaleBaseRect(null);
					callbacks.setScaleDragStart(null);
					callbacks.repaintCanvas();
					return;
				}

				if (callbacks.getAppMode() == AppMode.PAINT) {
					PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
					if (tool == PaintEngine.Tool.PENCIL && callbacks.isCanvasSubMode()) {
						// Canvas sub-mode pencil → commit overlay as Element
						commitOverlayAsElement();
						callbacks.repaintCanvas();
					} else if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE
							|| tool == PaintEngine.Tool.RECT) {
						if (callbacks.isCanvasSubMode() && overlayShapeStart != null) {
							// Canvas sub-mode: draw final shape on overlay and commit as Element
							Point imgPt = callbacks.screenToImage(e.getPoint());
							if (canvasDrawOverlay == null) canvasDrawOverlay = newOverlay();
							drawShapeOnOverlay(tool, overlayShapeStart, imgPt);
							commitOverlayAsElement();
							callbacks.repaintCanvas();
						} else {
							Point imgPt = callbacks.screenToImage(e.getPoint());
							drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
							callbacks.setShapeStartPoint(null);
							callbacks.setPaintSnapshot(null);
							callbacks.markDirty();
						}
					} else if (tool == PaintEngine.Tool.SELECT) {
						// SELECT tool: finalize selection
						if (callbacks.isSelecting()) {
							callbacks.setSelecting(false);
							Point start = callbacks.getSelectionStart();
							Point end   = callbacks.getSelectionEnd();
							if (start != null && end != null) {
								int x = Math.min(start.x, end.x);
								int y = Math.min(start.y, end.y);
								int w = Math.abs(end.x - start.x);
								int h = Math.abs(end.y - start.y);
								if (w > 0 && h > 0) {
									Rectangle sel = new Rectangle(x, y, w, h);
									// Always keep the selection rect first — Canvas sub-mode
									// lifts pixels only on the first action the user takes
									// (move-drag → liftSelectionToElement, CTRL+X, DEL, etc.)
									callbacks.getSelectedAreas().clear();
									callbacks.getSelectedAreas().add(sel);
									callbacks.repaintCanvas();
								}
							}
						}
					} else if (tool == PaintEngine.Tool.FREE_PATH && freePathPoints != null) {
						commitFreePath(e.getPoint());
					} else if (tool == PaintEngine.Tool.WAND_IV && freePathPoints != null) {
						commitWandIV();
					}
				} else {
					if (callbacks.isSelecting()) {
						callbacks.setSelecting(false);
						Point start = callbacks.getSelectionStart();
						Point end = callbacks.getSelectionEnd();
						if (start != null && end != null) {
							int x = Math.min(start.x, end.x);
							int y = Math.min(start.y, end.y);
							int w = Math.abs(end.x - start.x);
							int h = Math.abs(end.y - start.y);
							if (w > 0 && h > 0) {
								Rectangle sel = new Rectangle(x, y, w, h);
								callbacks.getSelectedAreas().add(sel);
								callbacks.repaintCanvas();
							}
						}
					}
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				// Check if ALT+T is held (for opacity control)
				boolean altPressed = (e.getModifiersEx() & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK)) != 0;
				boolean tPressed = keyState != null && keyState[KeyEvent.VK_T];

				if (altPressed && tPressed) {
					// ALT+T + wheel → adjust opacity of selected ImageLayer
					Layer primary = callbacks.getSelectedElement();
					if (primary instanceof ImageLayer il) {
						e.consume();
						int delta = e.getWheelRotation(); // up = more opaque, down = more transparent
						int newOpacity = Math.max(1, Math.min(100, il.opacity() + delta * 2));
						ImageLayer updated = il.withOpacity(newOpacity);
						callbacks.updateSelectedElement(updated);
						repaint();
						return;
					}
				}

				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					// CTRL+wheel on a selected TextLayer → adjust font size instead of global zoom
					Layer primary = callbacks.getSelectedElement();
					if (primary instanceof TextLayer tl) {
						int delta = -e.getWheelRotation(); // scroll up = larger
						int newSize = Math.max(6, tl.fontSize() + delta);
						Layer updated = tl.withFontSize(newSize);
						callbacks.updateSelectedElement(updated);
						// Keep chooser spinner in sync
						textFontSize = newSize;
						callbacks.syncTextToolbar(textFontName, textFontSize, textBold, textItalic, textColor);
						repaint();
						return;
					}
					// Progressive zoom: factor-based instead of step-based
					// Wheel up = zoom in, wheel down = zoom out
					// This makes zooming to pixel level much faster
					double currentZoom = callbacks.getZoom();
					double zoomFactor = 1.08;  // 8% per notch (configurable)
					double newZoom = currentZoom * Math.pow(zoomFactor, -e.getWheelRotation());
					callbacks.setZoom(newZoom, e.getPoint());
				} else if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
					callbacks.getScrollPane().getHorizontalScrollBar()
							.setValue(callbacks.getScrollPane().getHorizontalScrollBar().getValue()
									+ e.getWheelRotation() * mouseWheelSensitivityInPx);
				} else {
					callbacks.getScrollPane().getVerticalScrollBar()
							.setValue(callbacks.getScrollPane().getVerticalScrollBar().getValue()
									+ e.getWheelRotation() * mouseWheelSensitivityInPx);
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (hoveredHandleIndex >= 0) {
					hoveredHandleIndex = -1;
					setCursor(Cursor.getDefaultCursor());
				}
				if (brushPreviewPt != null) {
					brushPreviewPt = null;
					// Also restore the cursor — otherwise BLANK_CURSOR persists and the
					// user sees no cursor at all when they re-enter with a non-brush tool.
					setCursor(Cursor.getDefaultCursor());
					repaint();
				}
			}
		};

		addMouseListener(handler);
		addMouseMotionListener(handler);
		addMouseWheelListener(handler);

		// Track which element the mouse is over (for bidirectional hover highlight)
		addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override public void mouseMoved(java.awt.event.MouseEvent e) {
				if (callbacks.getActiveElements().isEmpty()) return;

				// Check PathLayer point hover (primary element only)
				Layer primary = callbacks.getSelectedElement();
				int newPathPointHover = -1;
				if (primary instanceof PathLayer pl) {
					// Use layer origin directly — elemRectScreen has padding that would offset coords.
					double zoom = callbacks.getZoom();
					double layerX = e.getPoint().x / zoom - pl.x();
					double layerY = e.getPoint().y / zoom - pl.y();

					java.util.List<Point3D> points = pl.points();
					int hitRadius = 12;  // Larger hit area for points
					for (int i = 0; i < points.size(); i++) {
						Point3D p = points.get(i);
						double dist = Math.sqrt(Math.pow(layerX - p.x, 2) + Math.pow(layerY - p.y, 2));
						if (dist <= hitRadius) {
							newPathPointHover = i;
							break;  // Hit the first (topmost) point
						}
					}
				}

				if (newPathPointHover != hoveredPathPointIndex) {
					hoveredPathPointIndex = newPathPointHover;
					repaint();
				}

				// Check rotation-handle hover first (for ImageLayer only)
				int newHoveredHandle = -1;
				if (primary != null && primary instanceof ImageLayer) {
					Rectangle elemRectSc = callbacks.elemRectScreen(primary);
					Rectangle rotHandleRect = callbacks.getRotationHandleRect(elemRectSc);
					if (rotHandleRect.contains(e.getPoint())) {
						newHoveredHandle = -2;  // Special code for rotation handle
					}
				}

				// If not over rotation handle, check scale-handle hover on the primary selected element
				if (newHoveredHandle == -1 && primary != null) {
					Rectangle[] hrs = callbacks.handleRects(callbacks.elemRectScreen(primary));
					for (int hi = 0; hi < hrs.length; hi++) {
						if (hrs[hi].contains(e.getPoint())) { newHoveredHandle = hi; break; }
					}
				}
				if (newHoveredHandle != hoveredHandleIndex) {
					hoveredHandleIndex = newHoveredHandle;
					// Update cursor
					if (newHoveredHandle == -2) {
						// Rotation handle: rotation cursor
						setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					} else if (newHoveredHandle >= 0) {
						int cur = switch (newHoveredHandle) {
							case 0 -> Cursor.NW_RESIZE_CURSOR;
							case 1 -> Cursor.N_RESIZE_CURSOR;
							case 2 -> Cursor.NE_RESIZE_CURSOR;
							case 3 -> Cursor.W_RESIZE_CURSOR;
							case 4 -> Cursor.E_RESIZE_CURSOR;
							case 5 -> Cursor.SW_RESIZE_CURSOR;
							case 6 -> Cursor.S_RESIZE_CURSOR;
							case 7 -> Cursor.SE_RESIZE_CURSOR;
							default -> Cursor.DEFAULT_CURSOR;
						};
						setCursor(Cursor.getPredefinedCursor(cur));
					} else {
						setCursor(Cursor.getDefaultCursor());
					}
					repaint();
				}

				Layer hit = hitElement(e.getPoint());
				int newId = hit != null ? hit.id() : -1;
				if (newId != hoveredElementId) {
					hoveredElementId = newId;
					callbacks.onCanvasElementHover(newId);
					repaint();
				}
			}
		});

		// Brush-preview: eigener Listener – immer aktiv, unabhängig von Canvas-Modus
		addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override public void mouseMoved(java.awt.event.MouseEvent e)   { updateBrushPreview(e.getPoint()); }
			@Override public void mouseDragged(java.awt.event.MouseEvent e) { updateBrushPreview(e.getPoint()); }
		});
		addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseExited(java.awt.event.MouseEvent e) {
				if (brushPreviewPt != null) {
					brushPreviewPt = null;
					setCursor(Cursor.getDefaultCursor());
					repaint();
				}
			}
		});
	}

	/** Updates the brush-preview position and cursor; triggers a clip-rect repaint at old+new positions. */
	private void updateBrushPreview(Point screenPt) {
		if (callbacks.getAppMode() != AppMode.PAINT) {
			if (brushPreviewPt != null) { Point old = brushPreviewPt; brushPreviewPt = null; repaintBrushPreview(old, null); }
			return;
		}
		PaintEngine.Tool tool = callbacks.getPaintToolbar() != null ? callbacks.getPaintToolbar().getActiveTool() : null;
		boolean isBrushTool = tool == PaintEngine.Tool.PENCIL || tool == PaintEngine.Tool.ERASER
				|| tool == PaintEngine.Tool.ERASER_BG || tool == PaintEngine.Tool.ERASER_COLOR
				|| tool == PaintEngine.Tool.SMEAR;
		if (!isBrushTool) {
			if (brushPreviewPt != null) {
				Point old = brushPreviewPt; brushPreviewPt = null;
				setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
				repaintBrushPreview(old, null);
			}
			return;
		}
		Point old = brushPreviewPt;
		brushPreviewPt = screenPt;
		setCursor(BLANK_CURSOR);
		repaintBrushPreview(old, screenPt);
	}

	/** Repaints only the rectangles around the old and new brush-preview positions. */
	private void repaintBrushPreview(Point oldPt, Point newPt) {
		int rScreen = brushPreviewRadiusScreen() + 2; // +2 for stroke width/padding
		if (oldPt != null) repaint(oldPt.x - rScreen, oldPt.y - rScreen, rScreen * 2, rScreen * 2);
		if (newPt != null) repaint(newPt.x - rScreen, newPt.y - rScreen, rScreen * 2, rScreen * 2);
	}

	/** Brush-preview radius in screen pixels (image-space strokeWidth × zoom). */
	private int brushPreviewRadiusScreen() {
		if (callbacks.getPaintToolbar() == null) return 8;
		int sw = Math.max(1, callbacks.getPaintToolbar().getStrokeWidth());
		return Math.max(4, (int) Math.ceil(sw * callbacks.getZoom() / 2.0));
	}

	/** Repaints the screen region covered by a pencil/eraser stroke segment from a→b (image space). */
	private void repaintStrokeSegment(Point aImg, Point bImg, int strokeWidthImg) {
		if (aImg == null || bImg == null) return;
		double z = callbacks.getZoom();
		int pad = (int) Math.ceil(strokeWidthImg * z / 2.0) + 2;
		int sx1 = (int) Math.floor(Math.min(aImg.x, bImg.x) * z) - pad;
		int sy1 = (int) Math.floor(Math.min(aImg.y, bImg.y) * z) - pad;
		int sx2 = (int) Math.ceil (Math.max(aImg.x, bImg.x) * z) + pad;
		int sy2 = (int) Math.ceil (Math.max(aImg.y, bImg.y) * z) + pad;
		repaint(sx1, sy1, sx2 - sx1, sy2 - sy1);
	}

	private void setupKeyBindings() {
		// Text tool: all printable keys are captured via a KeyAdapter registered on the
		// panel so we don't need to enumerate individual KeyStrokes in the InputMap.
		addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent ke) {
				// Track key state for modifier combinations (e.g., ALT+T)
				int keyCode = ke.getKeyCode();
				if (keyCode >= 0 && keyCode < keyState.length) {
					keyState[keyCode] = true;
				}

				// ── PathLayer point editing ────────────────────────────────────
				Layer primary = callbacks.getSelectedElement();
				if (primary instanceof PathLayer pl) {
					int code = ke.getKeyCode();
					if (code == KeyEvent.VK_DELETE) {
						if (selectedPathPointIndex >= 0) {
							// DEL: remove selected point
							PathLayer updated = pl.withRemovedPoint(selectedPathPointIndex);
							callbacks.updateSelectedElement(updated);
							selectedPathPointIndex = -1;
							ke.consume(); repaint(); return;
						} else {
							// NEW: DEL without point selection → clear pixels inside polygon
							callbacks.pushUndo();
							PaintEngine.clearPolygon(callbacks.getWorkingImage(),
									pl.absXPoints(), pl.absYPoints());
							callbacks.markDirty();
							ke.consume(); repaint(); return;
						}
					}
					// Check for PLUS key (VK_PLUS or VK_ADD for numpad, or character '=' with shift)
					boolean isPlusKey = (code == KeyEvent.VK_PLUS || code == KeyEvent.VK_ADD ||
					                    (code == KeyEvent.VK_EQUALS && ke.isShiftDown()));
					if (isPlusKey && selectedPathPointIndex >= 0) {
						// PLUS: add new point after selected point
						Point3D current = pl.getPoint(selectedPathPointIndex);
						Point3D next;
						// For closed paths, wrap around to first point; otherwise use next point or offset
						if (pl.isClosed() && selectedPathPointIndex == pl.pointCount() - 1) {
							next = pl.getPoint(0);
						} else {
							next = pl.getPoint(selectedPathPointIndex + 1);
						}
						// Midpoint between current and next (or offset if at end)
						double newX = next != null ? (current.x + next.x) / 2 : current.x + 20;
						double newY = next != null ? (current.y + next.y) / 2 : current.y + 20;
						PathLayer updated = pl.withAddedPoint(selectedPathPointIndex + 1, new Point3D(newX, newY));
						callbacks.updateSelectedElement(updated);
						selectedPathPointIndex++;  // Auto-select new point
						ke.consume(); repaint(); return;
					}
				}

				// ── Text tool editing ──────────────────────────────────────────
				if (textBoundingBox == null || textDrawingBox) return;
				int code = ke.getKeyCode();
				boolean ctrl  = ke.isControlDown();
				boolean shift = ke.isShiftDown();

				if (code == KeyEvent.VK_ESCAPE) {
					stopCaretBlink(); callbacks.hideTextToolbar();
					textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
					textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
					editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
					ke.consume(); repaint(); return;
				}
				if (code == KeyEvent.VK_ENTER) {
					if (editingWrappingLayer || shift) {
						insertAtCaret("\n");
					} else {
						commitText();
					}
					textCaretVisible = true; syncDialogText();
					ke.consume(); repaintIfEditing(); return;
				}
				if (code == KeyEvent.VK_BACK_SPACE) {
					if (ctrl && !hasTextSel()) { int ws = wordStart(textCaretPos); textBuffer.delete(ws, textCaretPos); textCaretPos = ws; }
					else deleteBeforeCaret();
					textCaretVisible = true; syncDialogText();
					ke.consume(); repaintIfEditing(); return;
				}
				if (code == KeyEvent.VK_DELETE) {
					if (ctrl && !hasTextSel()) { int we = wordEnd(textCaretPos); textBuffer.delete(textCaretPos, we); }
					else deleteAfterCaret();
					textCaretVisible = true; syncDialogText();
					ke.consume(); repaintIfEditing(); return;
				}
				// Clipboard
				if (ctrl && code == KeyEvent.VK_A) { textSelAnchor = 0; textCaretPos = textBuffer.length(); ke.consume(); repaintIfEditing(); return; }
				if (ctrl && code == KeyEvent.VK_C) { copyToClipboard(); ke.consume(); return; }
				if (ctrl && code == KeyEvent.VK_X) { copyToClipboard(); pushTextUndo(); deleteSel(); textCaretVisible = true; syncDialogText(); ke.consume(); repaintIfEditing(); return; }
				if (ctrl && code == KeyEvent.VK_V) { pasteFromClipboard(); textCaretVisible = true; syncDialogText(); ke.consume(); repaintIfEditing(); return; }
				// Scoped text undo/redo (only while editing the wrapping frame layer)
				if (editingWrappingLayer && ctrl && code == KeyEvent.VK_Z) {
					if (!textUndoStack.isEmpty()) {
						textRedoStack.push(textBuffer.toString());
						String prev = textUndoStack.pop();
						textBuffer = new StringBuilder(prev);
						textCaretPos = Math.min(textCaretPos, textBuffer.length());
						clearTextSel(); lastTextLines = null;
						textCaretVisible = true; syncDialogText();
					}
					ke.consume(); repaintIfEditing(); return;
				}
				if (editingWrappingLayer && ctrl && code == KeyEvent.VK_Y) {
					if (!textRedoStack.isEmpty()) {
						textUndoStack.push(textBuffer.toString());
						String next = textRedoStack.pop();
						textBuffer = new StringBuilder(next);
						textCaretPos = Math.min(textCaretPos, textBuffer.length());
						clearTextSel(); lastTextLines = null;
						textCaretVisible = true; syncDialogText();
					}
					ke.consume(); repaintIfEditing(); return;
				}
				// Arrow navigation
				if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT ||
					code == KeyEvent.VK_UP   || code == KeyEvent.VK_DOWN  ||
					code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
					int oldCaret = textCaretPos;
					java.util.List<TLine> ll = getCurrentTextLines();
					if (code == KeyEvent.VK_LEFT) {
						if (hasTextSel() && !shift) textCaretPos = selMin();
						else if (ctrl) textCaretPos = wordStart(textCaretPos);
						else textCaretPos = Math.max(0, textCaretPos - 1);
					} else if (code == KeyEvent.VK_RIGHT) {
						if (hasTextSel() && !shift) textCaretPos = selMax();
						else if (ctrl) textCaretPos = wordEnd(textCaretPos);
						else textCaretPos = Math.min(textBuffer.length(), textCaretPos + 1);
					} else if (code == KeyEvent.VK_HOME) {
						textCaretPos = ctrl ? 0 : lineStartPos(textCaretPos, ll);
					} else if (code == KeyEvent.VK_END) {
						textCaretPos = ctrl ? textBuffer.length() : lineEndPos(textCaretPos, ll);
					} else if (code == KeyEvent.VK_UP) {
						int li = findLineForCaret(textCaretPos, ll);
						if (li > 0) { int off = textCaretPos - ll.get(li).start(); TLine prev = ll.get(li - 1); textCaretPos = Math.min(prev.start() + off, prev.end()); }
						else textCaretPos = 0;
					} else if (code == KeyEvent.VK_DOWN) {
						int li = findLineForCaret(textCaretPos, ll);
						if (li < ll.size() - 1) { int off = textCaretPos - ll.get(li).start(); TLine next = ll.get(li + 1); textCaretPos = Math.min(next.start() + off, next.end()); }
						else textCaretPos = textBuffer.length();
					}
					if (shift) { if (textSelAnchor < 0) textSelAnchor = oldCaret; }
					else clearTextSel();
					textCaretVisible = true;
					ke.consume(); repaintIfEditing(); return;
				}
			}
			@Override
			public void keyTyped(java.awt.event.KeyEvent ke) {
				if (textBoundingBox == null || textDrawingBox) return;
				char c = ke.getKeyChar();
				if (c == KeyEvent.CHAR_UNDEFINED) return;
				if (c == '\r' || c == '\n' || c == '\u001b' || c == '\b') return;
				if (ke.isControlDown()) return; // handled in keyPressed
				insertAtCaret(String.valueOf(c));
				textCaretVisible = true;
				syncDialogText();
				ke.consume(); repaint();
			}

			@Override
			public void keyReleased(java.awt.event.KeyEvent ke) {
				// Track key state for modifier combinations
				int keyCode = ke.getKeyCode();
				if (keyCode >= 0 && keyCode < keyState.length) {
					keyState[keyCode] = false;
				}
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (textBoundingBox != null) {
					stopCaretBlink(); callbacks.hideTextToolbar();
					textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
					textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
					editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
					repaint(); return;
				}
				if (callbacks.getFloatingImage() != null) {
					callbacks.commitFloat();
				} else if (!callbacks.getSelectedElements().isEmpty()) {
					callbacks.setSelectedElement(null); // clears all
					callbacks.repaintCanvas();
				} else if (callbacks.isSelecting()) {
					callbacks.setSelecting(false);
					callbacks.repaintCanvas();
				} else {
					callbacks.clearSelection();
				}
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commit");
		getActionMap().put("commit", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (textBoundingBox != null && !textDrawingBox) { commitText(); return; }
				if (callbacks.getFloatingImage() != null) {
					callbacks.commitFloat();
				}
			}
		});

		// DEL and Backspace are handled by the parent window's WHEN_IN_FOCUSED_WINDOW
		// binding in SelectiveAlphaEditor.setupKeyBindings() — do not shadow them here.
	}

	private void copyInto(BufferedImage src, BufferedImage dst) {
		Graphics2D g2 = dst.createGraphics();
		g2.setComposite(java.awt.AlphaComposite.Src);
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
	}

	/** Same as drawShape but targets {@code canvasDrawOverlay} (Canvas sub-mode). */
	private void drawShapeOnOverlay(PaintEngine.Tool tool, Point from, Point to) {
		if (canvasDrawOverlay == null) return;
		boolean aa = callbacks.getPaintToolbar().isAntialiasing();
		Color primary   = callbacks.getPaintToolbar().getPrimaryColor();
		Color secondary = callbacks.getPaintToolbar().getSecondaryColor();
		int   sw        = callbacks.getPaintToolbar().getStrokeWidth();
		PaintEngine.FillMode fill = callbacks.getPaintToolbar().getFillMode();
		if (tool == PaintEngine.Tool.LINE) {
			PaintEngine.drawLine(canvasDrawOverlay, from, to, primary, sw, aa);
		} else if (tool == PaintEngine.Tool.CIRCLE) {
			PaintEngine.drawCircle(canvasDrawOverlay, from, to, primary, sw, fill, secondary, aa);
		} else if (tool == PaintEngine.Tool.RECT) {
			PaintEngine.drawRect(canvasDrawOverlay, from, to, primary, sw, fill, secondary, aa);
		}
	}

	private void drawShape(PaintEngine.Tool tool, Point from, Point to) {
		if (callbacks.getWorkingImage() == null)
			return;

		int x = Math.min(from.x, to.x);
		int y = Math.min(from.y, to.y);
		int w = Math.abs(to.x - from.x);
		int h = Math.abs(to.y - from.y);

		boolean aa = callbacks.getPaintToolbar().isAntialiasing();
		if (tool == PaintEngine.Tool.LINE) {
			PaintEngine.drawLine(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), aa);
		} else if (tool == PaintEngine.Tool.CIRCLE) {
			PaintEngine.drawCircle(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), callbacks.getPaintToolbar().getFillMode(),
					callbacks.getPaintToolbar().getSecondaryColor(), aa);
		} else if (tool == PaintEngine.Tool.RECT) {
			PaintEngine.drawRect(callbacks.getWorkingImage(), from, to, callbacks.getPaintToolbar().getPrimaryColor(),
					callbacks.getPaintToolbar().getStrokeWidth(), callbacks.getPaintToolbar().getFillMode(),
					callbacks.getPaintToolbar().getSecondaryColor(), aa);
		}
	}

	// =========================================================================
	// Text-chooser (modeless, persistent)
	// =========================================================================

	/**
	 * Ensures the modeless text-options dialog is visible.
	 * Creates it once on first call; subsequent calls just bring it to front and
	 * synchronise the controls to the current text* field values.
	 * Returns immediately — does NOT block the canvas.
	 */

	/** Builds the one-time modeless dialog and wires live-update listeners. */
	/**
	 * Loads a TEXT_LAYER element's settings into the text* fields.
	 * Called when the user single-clicks a TEXT_LAYER to select it.
	 */
	public void syncTextChooserFromElement(Layer el) {
		if (!(el instanceof TextLayer tl)) return;
		textBuffer   = new StringBuilder(tl.text());
		textFontName = tl.fontName();
		textFontSize = tl.fontSize() > 0 ? tl.fontSize() : 24;
		textBold     = tl.fontBold();
		textItalic   = tl.fontItalic();
		textColor    = tl.fontColor();
		callbacks.syncTextToolbar(textFontName, textFontSize, textBold, textItalic, textColor);
	}

	/**
	 * Applies the current text* fields to whichever TEXT_LAYER is selected (or the
	 * active text input if editing).  Called by every dialog control listener.
	 */
	private void applyTextChooserToSelected() {
		// Live-update a selected, committed TextLayer
		Layer primary = callbacks.getSelectedElement();
		if (primary instanceof TextLayer tl && textBoundingBox == null) {
			// Use textBuffer (current text from dialog) instead of tl.text() (old text)
			Layer updated = tl.withText(
					textBuffer.toString(), textFontName, textFontSize, textBold, textItalic, textColor);
			callbacks.updateSelectedElement(updated);
		}
		// Repaint if text input is active (refreshes the preview box)
		if (textBoundingBox != null) repaint();
		else callbacks.repaintCanvas();
	}

	/** Repaints only when there is an active text edit or draw phase. */
	private void repaintIfEditing() {
		if (textBoundingBox != null) repaint();
	}

	// =========================================================================
	// Enter / exit text edit mode
	// =========================================================================

	/**
	 * Enters edit mode for an existing TEXT_LAYER:
	 *  – removes the element from activeElements (so it doesn't duplicate the preview)
	 *  – populates textBuffer + textOrigin + font settings
	 *  – records the original element so cancel can restore it
	 */
	public void enterTextEditMode(Layer el) {
		// Guard against re-entry while already editing this element
		if (editingTextElementId >= 0 && editingTextElementId == el.id()) return;
		commitText(); // flush any in-progress text first
		// Load font settings from element (el must be a TextLayer here)
		TextLayer tl = (TextLayer) el;
		textFontName = tl.fontName();
		textFontSize = tl.fontSize() > 0 ? tl.fontSize() : 24;
		textBold     = tl.fontBold();
		textItalic   = tl.fontItalic();
		textColor    = tl.fontColor();
		textBuffer   = new StringBuilder(tl.text());
		textBoundingBox    = new Rectangle(el.x(), el.y(), el.width(), el.height());
		textMinBox         = new Rectangle(el.x(), el.y(), el.width(), el.height());
		textDrawingBox     = false;
		editingTextElementId   = el.id();
		editingOriginalElement = el;
		editingWrappingLayer   = tl.isWrapping();
		textUndoStack.clear(); textRedoStack.clear();
		textCaretPos   = textBuffer.length();
		textSelAnchor  = -1;
		lastTextLines  = null;
		callbacks.setSelectedElement(null);
		callbacks.showTextToolbar(textFontName, textFontSize, textBold, textItalic, textColor);
		requestFocusInWindow();
		startCaretBlink();
		repaint();
	}

	/**
	 * Applies snap logic based on the active snap mode:
	 * <ul>
	 *   <li>SNAP_TO_LAYER  – aligns to edges/corners of other elements.</li>
	 *   <li>SNAP_TO_MARGIN – aligns to page margin edges.</li>
	 * </ul>
	 */
	private void applySnapToElement(Layer dragged) {
		if (dragged == null) return;
		PageLayout pl = callbacks.getPageLayout();

		// Determine snap mode (default SNAP_TO_LAYER when no page layout)
		PageLayout.SnapMode mode = (pl != null) ? pl.snapMode : PageLayout.SnapMode.SNAP_TO_LAYER;

		Rectangle dr = new Rectangle(dragged.x(), dragged.y(), dragged.width(), dragged.height());
		int snapX = Integer.MAX_VALUE, snapDx = 0;
		int snapY = Integer.MAX_VALUE, snapDy = 0;

		if (mode == PageLayout.SnapMode.SNAP_TO_MARGIN && pl != null
				&& callbacks.getWorkingImage() != null) {
			// Snap to the four margin lines (image-space pixels)
			int imgW = callbacks.getWorkingImage().getWidth();
			int imgH = callbacks.getWorkingImage().getHeight();
			int mL = pl.marginLeftPx(), mR = imgW - pl.marginRightPx();
			int mT = pl.marginTopPx(),  mB = imgH - pl.marginBottomPx();

			int[][] xPairs = {
				{ dr.x,            mL }, { dr.x,            mR },
				{ dr.x + dr.width, mL }, { dr.x + dr.width, mR },
			};
			int[][] yPairs = {
				{ dr.y,             mT }, { dr.y,             mB },
				{ dr.y + dr.height, mT }, { dr.y + dr.height, mB },
			};
			for (int[] p : xPairs) {
				int d = Math.abs(p[0] - p[1]);
				if (d < SNAP_DIST && d < snapX) { snapX = d; snapDx = p[1] - p[0]; }
			}
			for (int[] p : yPairs) {
				int d = Math.abs(p[0] - p[1]);
				if (d < SNAP_DIST && d < snapY) { snapY = d; snapDy = p[1] - p[0]; }
			}
		} else if (mode != PageLayout.SnapMode.NONE) {
			// SNAP_TO_LAYER (default)
			for (Layer other : callbacks.getActiveElements()) {
				if (other.id() == dragged.id()) continue;
				Rectangle or = new Rectangle(other.x(), other.y(), other.width(), other.height());
				int[][] xPairs = {
					{ dr.x,               or.x              },
					{ dr.x,               or.x + or.width   },
					{ dr.x + dr.width,    or.x              },
					{ dr.x + dr.width,    or.x + or.width   },
					{ dr.x + dr.width/2,  or.x + or.width/2 }
				};
				int[][] yPairs = {
					{ dr.y,               or.y              },
					{ dr.y,               or.y + or.height  },
					{ dr.y + dr.height,   or.y              },
					{ dr.y + dr.height,   or.y + or.height  },
					{ dr.y + dr.height/2, or.y + or.height/2 }
				};
				for (int[] p : xPairs) {
					int d = Math.abs(p[0] - p[1]);
					if (d < SNAP_DIST && d < snapX) { snapX = d; snapDx = p[1] - p[0]; }
				}
				for (int[] p : yPairs) {
					int d = Math.abs(p[0] - p[1]);
					if (d < SNAP_DIST && d < snapY) { snapY = d; snapDy = p[1] - p[0]; }
				}
			}
		}

		if (snapX < SNAP_DIST && snapDx != 0) callbacks.moveSelectedElements(snapDx, 0);
		if (snapY < SNAP_DIST && snapDy != 0) callbacks.moveSelectedElements(0, snapDy);
	}

	/**
	 * Word-wraps {@code text} (which may contain {@code \n}) into lines that fit
	 * within {@code maxWidth} screen pixels using {@code fm}.
	 * Explicit newlines always start a new line.
	 */
	// =========================================================================
	// Text layout helpers
	// =========================================================================

	/**
	 * Builds visual line layout tracking char offsets in the original string.
	 * For wordWrap=true, lines are broken at word boundaries to fit maxWidth.
	 * For wordWrap=false, lines are split only on '\n'.
	 */
	static java.util.List<TLine> buildTextLines(String text, java.awt.FontMetrics fm, int maxW, boolean wordWrap) {
		java.util.List<TLine> lines = new java.util.ArrayList<>();
		if (text == null) text = "";
		int n = text.length();
		int i = 0;
		while (i <= n) {
			int pEnd = text.indexOf('\n', i);
			if (pEnd < 0) pEnd = n;
			// No wrap needed if: wrapping disabled, empty para, or whole para fits
			if (!wordWrap || pEnd == i || fm.stringWidth(text.substring(i, pEnd)) <= maxW) {
				lines.add(new TLine(i, pEnd, text.substring(i, pEnd)));
				i = pEnd + 1;
				continue;
			}
			// Word-wrap this paragraph
			int lStart = i, lEnd = i;
			int wStart = i;
			while (wStart <= pEnd) {
				// Find end of current word
				int wEnd = wStart;
				while (wEnd < pEnd && text.charAt(wEnd) != ' ') wEnd++;
				String candidate = text.substring(lStart, wEnd);
				if (fm.stringWidth(candidate) <= maxW || lStart == wStart) {
					// Fits (or first word on line — emit even if too long)
					lEnd = wEnd;
					wStart = wEnd + 1;
					if (wStart > pEnd) {
						lines.add(new TLine(lStart, lEnd, text.substring(lStart, lEnd)));
						break;
					}
				} else {
					// Emit current line, start new one
					int nextStart = lEnd;
					if (nextStart < pEnd && text.charAt(nextStart) == ' ') nextStart++;
					lines.add(new TLine(lStart, lEnd, text.substring(lStart, lEnd)));
					lStart = nextStart; lEnd = nextStart;
				}
			}
			// Catch leftover (handles edge case where loop exited without emitting)
			if (lines.isEmpty() || lines.get(lines.size() - 1).end() < lStart)
				lines.add(new TLine(lStart, Math.max(lStart, pEnd), text.substring(lStart, Math.max(lStart, pEnd))));
			i = pEnd + 1;
		}
		if (lines.isEmpty()) lines.add(new TLine(0, 0, ""));
		return lines;
	}

	/** Backward-compat wrapper used by ElementController. */
	static java.util.List<String> wrapText(String text, java.awt.FontMetrics fm, int maxWidth) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (TLine l : buildTextLines(text, fm, maxWidth, true)) out.add(l.text());
		return out;
	}

	// Find last TLine whose start <= caretPos
	private static int findLineForCaret(int caretPos, java.util.List<TLine> lines) {
		int best = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).start() <= caretPos) best = i;
			else break;
		}
		return best;
	}

	// Screen X of the caret within the text box
	private static int caretScreenX(int caretPos, java.util.List<TLine> lines, java.awt.FontMetrics fm, int sx) {
		if (lines.isEmpty()) return sx + TEXT_PADDING;
		int li = findLineForCaret(caretPos, lines);
		TLine l = lines.get(li);
		int offset = Math.min(caretPos - l.start(), l.text().length());
		return sx + TEXT_PADDING + fm.stringWidth(l.text().substring(0, offset));
	}

	// Convert screen point to nearest caret position
	private static int screenToCaretPos(java.awt.Point screenPt, java.util.List<TLine> lines,
			java.awt.FontMetrics fm, int sx, int sy, int lineH) {
		if (lines.isEmpty()) return 0;
		int relY = screenPt.y - sy - TEXT_PADDING;
		int li = Math.max(0, Math.min(lines.size() - 1, lineH > 0 ? relY / lineH : 0));
		TLine line = lines.get(li);
		int relX = screenPt.x - sx - TEXT_PADDING;
		if (relX <= 0) return line.start();
		String t = line.text();
		for (int i = 0; i <= t.length(); i++) {
			int cx   = fm.stringWidth(t.substring(0, i));
			int next = i < t.length() ? fm.stringWidth(t.substring(0, i + 1)) : cx + 9999;
			if (relX < (cx + next) / 2) return line.start() + i;
		}
		return line.end();
	}

	// ── Caret / selection state helpers ────────────────────────────────────────

	private int  selMin()     { return textSelAnchor < 0 ? textCaretPos : Math.min(textCaretPos, textSelAnchor); }
	private int  selMax()     { return textSelAnchor < 0 ? textCaretPos : Math.max(textCaretPos, textSelAnchor); }
	private boolean hasTextSel() { return textSelAnchor >= 0 && textSelAnchor != textCaretPos; }
	private void clearTextSel()  { textSelAnchor = -1; }

	private void deleteSel() {
		if (!hasTextSel()) return;
		int a = selMin(), b = selMax();
		textBuffer.delete(a, b);
		textCaretPos = a;
		clearTextSel();
	}

	private void pushTextUndo() {
		if (!editingWrappingLayer) return;
		textUndoStack.push(textBuffer.toString());
		if (textUndoStack.size() > 200) textUndoStack.pollLast();
		textRedoStack.clear();
	}

	private void insertAtCaret(String s) {
		if (hasTextSel()) deleteSel();
		pushTextUndo();
		textBuffer.insert(textCaretPos, s);
		textCaretPos += s.length();
	}

	private void deleteBeforeCaret() {
		if (hasTextSel()) { pushTextUndo(); deleteSel(); return; }
		if (textCaretPos > 0) { pushTextUndo(); textBuffer.deleteCharAt(textCaretPos - 1); textCaretPos--; }
	}

	private void deleteAfterCaret() {
		if (hasTextSel()) { pushTextUndo(); deleteSel(); return; }
		if (textCaretPos < textBuffer.length()) { pushTextUndo(); textBuffer.deleteCharAt(textCaretPos); }
	}

	private int wordStart(int pos) {
		String s = textBuffer.toString(); int i = pos;
		while (i > 0 && !Character.isWhitespace(s.charAt(i - 1))) i--;
		return i;
	}
	private int wordEnd(int pos) {
		String s = textBuffer.toString(); int n = s.length(); int i = pos;
		while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
		return i;
	}
	private int lineStartPos(int pos, java.util.List<TLine> lines) {
		if (lines.isEmpty()) return 0;
		return lines.get(findLineForCaret(pos, lines)).start();
	}
	private int lineEndPos(int pos, java.util.List<TLine> lines) {
		if (lines.isEmpty()) return textBuffer.length();
		return lines.get(findLineForCaret(pos, lines)).end();
	}

	private void copyToClipboard() {
		if (!hasTextSel()) return;
		java.awt.datatransfer.StringSelection ss =
				new java.awt.datatransfer.StringSelection(textBuffer.substring(selMin(), selMax()));
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	private void pasteFromClipboard() {
		try {
			java.awt.datatransfer.Transferable t =
					java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (t != null && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
				String s = (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
				insertAtCaret(s.replace("\r\n", "\n").replace("\r", "\n"));
			}
		} catch (Exception ex) { System.err.println("[WARN] Paste: " + ex.getMessage()); }
	}

	private void startCaretBlink() {
		stopCaretBlink();
		textCaretVisible = true;
		textCaretTimer = new javax.swing.Timer(530, ev -> {
			if (textBoundingBox != null) { textCaretVisible = !textCaretVisible; repaint(); }
			else stopCaretBlink();
		});
		textCaretTimer.start();
	}

	private void stopCaretBlink() {
		if (textCaretTimer != null) { textCaretTimer.stop(); textCaretTimer = null; }
		textCaretVisible = true;
	}

	/** Rebuilds line layout for the current text state (uses zoom-scaled font). */
	private java.util.List<TLine> getCurrentTextLines() {
		if (textBoundingBox == null) return java.util.Collections.emptyList();
		double z = callbacks.getZoom();
		int style = (textBold ? Font.BOLD : 0) | (textItalic ? Font.ITALIC : 0);
		int screenSz = Math.max(1, (int) Math.round(Math.max(6, textFontSize) * z));
		java.awt.FontMetrics fm = getFontMetrics(new Font(textFontName, style, screenSz));
		int maxW = editingWrappingLayer ? (int)(textBoundingBox.width * z) - TEXT_PADDING * 2 : Integer.MAX_VALUE;
		return buildTextLines(textBuffer.toString(), fm, Math.max(1, maxW), editingWrappingLayer);
	}

	/** Keeps the dialog's text area in sync after canvas edits (no-op: dialog removed). */
	private void syncDialogText() {}

	/**
	 * Called by PaintCallbacksFactory when the user changes text properties in
	 * the PaintToolbar text section. Updates internal fields and live-previews
	 * the selected/editing TextLayer.
	 */
	public void applyTextPropsFromToolbar(String font, int size, boolean bold, boolean italic, Color color) {
		textFontName = font;
		textFontSize = size;
		textBold     = bold;
		textItalic   = italic;
		textColor    = color;
		if (textBoundingBox != null) {
			// In text-edit mode: live-preview updates automatically via repaint
			repaint();
		} else {
			// Outside text-edit mode: directly update the wrapping TextLayer or selected TextLayer
			applyTextChooserToSelected();
			// Also update wrapping TextLayer if in book mode and none is selected
			if (callbacks.isBookMode()) {
				java.util.List<Layer> els = callbacks.getActiveElements();
				for (int i = 0; i < els.size(); i++) {
					if (els.get(i) instanceof TextLayer tl && tl.isWrapping()) {
						callbacks.updateSelectedElement(tl.withText(tl.text(), font, size, bold, italic, color));
						break;
					}
				}
			}
		}
		// Restore focus to canvas so keyboard input keeps working
		javax.swing.SwingUtilities.invokeLater(this::requestFocusInWindow);
	}


	/**
	 * Returns a cached TexturePaint containing a 2×2-cell checker tile.
	 * Rebuilt only when the background colors change — a single fillRect then
	 * paints the whole canvas instead of thousands of per-cell fillRects.
	 */
	private java.awt.TexturePaint getCheckerPaint(Color bg1, Color bg2) {
		if (cachedCheckerPaint != null
				&& bg1.equals(cachedCheckerBg1)
				&& bg2.equals(cachedCheckerBg2)) {
			return cachedCheckerPaint;
		}
		int cell = 16;
		int size = cell * 2;
		BufferedImage tile = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = tile.createGraphics();
		g.setColor(bg1); g.fillRect(0, 0, cell, cell);      g.fillRect(cell, cell, cell, cell);
		g.setColor(bg2); g.fillRect(cell, 0, cell, cell);   g.fillRect(0, cell, cell, cell);
		g.dispose();
		cachedCheckerPaint = new java.awt.TexturePaint(tile, new Rectangle(0, 0, size, size));
		cachedCheckerBg1 = bg1;
		cachedCheckerBg2 = bg2;
		return cachedCheckerPaint;
	}

	/**
	 * Creates a transparent overlay image (same size as workingImage) used for
	 * drawing in Canvas sub-mode so the stroke becomes a separate Element layer.
	 */
	private BufferedImage newOverlay() {
		BufferedImage wi = callbacks.getWorkingImage();
		if (wi == null) return null;
		BufferedImage ov = new BufferedImage(wi.getWidth(), wi.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		return ov;
	}

	/** Commits {@code canvasDrawOverlay} as a new Element (cropped to painted bounds). Returns true if committed. */
	private boolean commitOverlayAsElement() {
		BufferedImage ov = canvasDrawOverlay;
		// Reset state immediately (even if nothing was drawn)
		canvasDrawOverlay = null;
		overlayLastPt     = null;
		overlayShapeStart = null;
		if (ov == null) return false;

		// Find bounding box of non-transparent pixels
		int minX = ov.getWidth(), minY = ov.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < ov.getHeight(); y++) {
			for (int x = 0; x < ov.getWidth(); x++) {
				if ((ov.getRGB(x, y) >>> 24) != 0) {
					if (x < minX) minX = x; if (y < minY) minY = y;
					if (x > maxX) maxX = x; if (y > maxY) maxY = y;
				}
			}
		}
		if (maxX < 0) return false; // nothing was drawn

		int w = maxX - minX + 1, h = maxY - minY + 1;
		BufferedImage crop = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D cg = crop.createGraphics();
		cg.drawImage(ov, -minX, -minY, null);
		cg.dispose();

		callbacks.commitTextAsElement(crop, minX, minY); // reuses the "add element" path
		return true;
	}

	/**
	 * Commits the active text session as a TEXT_LAYER Element.
	 * If editing an existing element ({@code editingTextElementId >= 0}), that element
	 * is updated; otherwise a new element is added.
	 * Clears text state afterwards.
	 */
	private void commitText() {
		if (textBoundingBox == null) return;
		stopCaretBlink();
		callbacks.hideTextToolbar();
		if (textBuffer.length() == 0) {
			textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
			textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
			editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
			repaint();
			return;
		}
		boolean wasWrapping = editingWrappingLayer;
		callbacks.commitTextLayer(
				editingTextElementId,
				textBuffer.toString(),
				textFontName, textFontSize, textBold, textItalic, textColor,
				textBoundingBox.x, textBoundingBox.y);
		textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
		textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
		editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
		// Wrapping layer stays on page → keep TextToolbar visible with current props
		if (wasWrapping) callbacks.showTextToolbar(textFontName, textFontSize, textBold, textItalic, textColor);
		repaint();
	}

	/** Called by TextToolbarCallbacksFactory commit button. */
	public void commitTextFromToolbar() { commitText(); requestFocusInWindow(); }

	/** Called by TextToolbarCallbacksFactory cancel button. */
	public void cancelTextFromToolbar() {
		stopCaretBlink();
		callbacks.hideTextToolbar();
		textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
		textBuffer.setLength(0); textCaretPos = 0; clearTextSel(); lastTextLines = null;
		editingTextElementId = -1; editingOriginalElement = null; editingWrappingLayer = false;
		repaint();
		requestFocusInWindow();
	}

	/** Returns the topmost layer at screen point, or null. */
	// ── Free-path helpers ─────────────────────────────────────────────────────

	/**
	 * Snaps an image-space point to the nearest endpoint of any existing PathLayer,
	 * if within FREE_PATH_SNAP pixels. Returns snapped point or original.
	 */
	private Point snapToPathEndpoint(Point imgPt) {
		for (Layer el : callbacks.getActiveElements()) {
			if (!(el instanceof PathLayer pl)) continue;
			java.util.List<Point3D> pts = pl.points();
			if (pts.isEmpty()) continue;
			// Check first and last point (endpoints)
			for (int idx : new int[]{0, pts.size() - 1}) {
				Point3D ep = pts.get(idx);
				int ax = (int) Math.round(pl.x() + ep.x);
				int ay = (int) Math.round(pl.y() + ep.y);
				if (Math.abs(ax - imgPt.x) <= FREE_PATH_SNAP && Math.abs(ay - imgPt.y) <= FREE_PATH_SNAP)
					return new Point(ax, ay);
			}
		}
		return imgPt;
	}

	/**
	 * Finalises a FREE_PATH drag: simplifies collected points, optionally snaps the
	 * last point to an existing path endpoint, creates a PathLayer, and switches to
	 * SELECT so the user can edit it immediately.
	 */
	private void commitFreePath(Point screenRelease) {
		java.util.List<java.awt.Point> raw = freePathPoints;
		freePathPoints       = null;
		freePathScreenPoints = null;
		if (raw == null || raw.size() < 2) { repaint(); return; }

		// Snap last point to nearby endpoint
		Point lastImg = raw.get(raw.size() - 1);
		Point snappedLast = snapToPathEndpoint(lastImg);
		if (!snappedLast.equals(lastImg))
			raw.set(raw.size() - 1, snappedLast);

		// Douglas-Peucker simplification (epsilon=2 image px)
		java.util.List<java.awt.Point> simplified = PaintEngine.simplifyPath(raw, 2.0);
		if (simplified.size() < 2) { repaint(); return; }

		// Find bounding box of all points
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (java.awt.Point p : simplified) {
			minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
			maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
		}

		// Convert to relative PathLayer coords (origin = minX, minY)
		java.util.List<Point3D> pts = new java.util.ArrayList<>();
		for (java.awt.Point p : simplified)
			pts.add(new Point3D(p.x - minX, p.y - minY));

		// Closed = first and last snap to same point
		boolean closed = simplified.get(0).equals(simplified.get(simplified.size() - 1));

		PathLayer newPath = PathLayer.of(callbacks.getNextElementId(), pts, null, closed, minX, minY);
		callbacks.addElement(newPath);
		callbacks.setSelectedElement(newPath);
		callbacks.getPaintToolbar().setActiveTool(PaintEngine.Tool.SELECT);
		callbacks.markDirty();
		repaint();
	}

	/**
	 * Wand IV – inwards collapse: simplify the drawn path, close it, then collapse
	 * each vertex inward until it hits a pixel that is neither transparent nor the
	 * secondary color (within the toolbar's wand-tolerance setting).
	 */
	private void commitWandIV() {
		java.util.List<java.awt.Point> raw = freePathPoints;
		freePathPoints       = null;
		freePathScreenPoints = null;
		if (raw == null || raw.size() < 3) { repaint(); return; }

		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) { repaint(); return; }

		// Build polygon arrays from raw drawn points
		int[] xs = new int[raw.size()];
		int[] ys = new int[raw.size()];
		for (int i = 0; i < raw.size(); i++) { xs[i] = raw.get(i).x; ys[i] = raw.get(i).y; }

		// Pixel-level inward collapse: rasterize polygon, find all non-pass-through
		// pixels inside it. Every pixel is checked independently at pixel resolution,
		// spreading "circularly" with no jumps.
		java.awt.Color secColor = callbacks.getPaintToolbar().getSecondaryColor();
		int            tolPct   = callbacks.getPaintToolbar().getWandTolerance();
		boolean[][] content = PaintEngine.collapseInward(xs, ys, img, secColor, tolPct);
		if (content == null) {
			ToastNotification.show(null, "Kein Inhalt im Bereich gefunden");
			repaint();
			return;
		}

		// Trace the boundary of the content mask and create a PathLayer
		createPathFromRegion(content, img.getWidth(), img.getHeight());
	}

	// ── Magic Wand helpers ─────────────────────────────────────────────────────

	private static final int WAND_TOLERANCE = 30;

	/**
	 * Wand I: flood-fill from click stopping at color boundaries, creates PathLayer outline.
	 */
	private void handleWandI(Point imgPt) {
		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		boolean[][] region = PaintEngine.floodFillRegion(img, imgPt.x, imgPt.y, WAND_TOLERANCE);
		createPathFromRegion(region, img.getWidth(), img.getHeight());
	}

	/**
	 * Wand II: flood-fill from click stopping when hitting secondary color, creates PathLayer outline.
	 */
	private void handleWandII(Point imgPt) {
		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		java.awt.Color stopColor = callbacks.getPaintToolbar().getSecondaryColor();
		boolean[][] region = PaintEngine.floodFillRegionUntilColor(img, imgPt.x, imgPt.y, stopColor, WAND_TOLERANCE);
		createPathFromRegion(region, img.getWidth(), img.getHeight());
	}

	/**
	 * Wand III: flood-fill from click (same boundary logic as Wand I), directly clears region pixels.
	 */
	private void handleWandIII(Point imgPt) {
		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		callbacks.pushUndo();
		boolean[][] region = PaintEngine.floodFillRegion(img, imgPt.x, imgPt.y, WAND_TOLERANCE);
		PaintEngine.clearRegionMask(img, region);
		callbacks.markDirty();
		repaint();
	}

	/**
	 * Replace (outer or inner): solid fill of the boundary band with the color
	 * resolved from the current WandColorSource (secondary / clicked / surrounding).
	 */
	private void handleWandReplace(Point imgPt, boolean outer) {
		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		PaintToolbar tb = callbacks.getPaintToolbar();
		int tol = tb.getWandTolerance();
		int bw  = tb.getReplaceBandWidth();
		boolean closed = tb.isReplaceBandClosed();
		java.awt.Color col = resolveWandColor(img, imgPt, tb.getWandColorSource(),
				tb.getSecondaryColor(), tol, bw, closed, outer);
		callbacks.pushUndo();
		if (outer) PaintEngine.replaceOuter(img, imgPt.x, imgPt.y, col, tol, bw, closed);
		else       PaintEngine.replaceInner(img, imgPt.x, imgPt.y, col, tol, bw, closed);
		callbacks.markDirty();
		repaint();
	}

	/**
	 * Anti-alias (outer or inner): distance-weighted blend of the boundary band
	 * with the color resolved from the current WandColorSource.
	 */
	private void handleWandAA(Point imgPt, boolean outer) {
		BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		PaintToolbar tb = callbacks.getPaintToolbar();
		int tol = tb.getWandTolerance();
		int bw  = tb.getReplaceBandWidth();
		boolean closed = tb.isReplaceBandClosed();
		java.awt.Color col = resolveWandColor(img, imgPt, tb.getWandColorSource(),
				tb.getSecondaryColor(), tol, bw, closed, outer);
		callbacks.pushUndo();
		if (outer) PaintEngine.antiAliasOuter(img, imgPt.x, imgPt.y, col, tol, bw, closed);
		else       PaintEngine.antiAliasInner(img, imgPt.x, imgPt.y, col, tol, bw, closed);
		callbacks.markDirty();
		repaint();
	}

	private java.awt.Color resolveWandColor(BufferedImage img, Point imgPt,
			PaintEngine.WandColorSource src, java.awt.Color secondary,
			int tol, int bandWidth, boolean closed, boolean outer) {
		return switch (src) {
			case SECONDARY -> secondary;
			case CLICKED   -> PaintEngine.pixelColor(img, imgPt.x, imgPt.y, secondary);
			case SURROUNDING -> {
				// "Surrounding" = average on the OPPOSITE side of the boundary from the band.
				// For outer bands: sample inside the clicked region.
				// For inner bands: sample outside the clicked region.
				boolean[][] region = PaintEngine.floodFillRegion(img, imgPt.x, imgPt.y, tol);
				boolean[][] opp = PaintEngine.boundaryBand(region,
						img.getWidth(), img.getHeight(),
						Math.max(3, bandWidth),
						/*outer=*/!outer, closed);
				yield PaintEngine.averageMaskColor(img, opp, secondary);
			}
		};
	}

	/** Shared: trace contour of region mask and create a PathLayer element. */
	private void createPathFromRegion(boolean[][] region, int imgW, int imgH) {
		int[][] contour = PaintEngine.traceContour(region, imgW, imgH);
		if (contour == null || contour.length < 2) {
			ToastNotification.show(null, "Kein Bereich gefunden");
			return;
		}

		// Find bounding box
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		for (int[] p : contour) { minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); }

		java.util.List<Point3D> pts = new java.util.ArrayList<>();
		for (int[] p : contour) pts.add(new Point3D(p[0] - minX, p[1] - minY));

		PathLayer newPath = PathLayer.of(callbacks.getNextElementId(), pts, null, true, minX, minY);
		callbacks.addElement(newPath);
		callbacks.setSelectedElement(newPath);
		callbacks.getPaintToolbar().setActiveTool(PaintEngine.Tool.SELECT);
		callbacks.markDirty();
		repaint();
	}

	/**
	 * Checks if a screen-space point is inside the polygon of the given PathLayer.
	 * Used to detect click/drag inside the path area (not just on control points).
	 */
	private boolean isInsidePathPolygon(PathLayer pl, Point screenPt) {
		if (pl == null) return false;
		double zoom = callbacks.getZoom();
		int[] xs = pl.absXPoints();
		int[] ys = pl.absYPoints();
		int[] sxs = new int[xs.length];
		int[] sys = new int[ys.length];
		for (int i = 0; i < xs.length; i++) {
			sxs[i] = (int) Math.round(xs[i] * zoom);
			sys[i] = (int) Math.round(ys[i] * zoom);
		}
		return new java.awt.Polygon(sxs, sys, sxs.length).contains(screenPt);
	}

	private Layer hitElement(Point screenPt) {
		java.util.List<Layer> els = callbacks.getActiveElements();
		for (int i = els.size() - 1; i >= 0; i--) {
			Layer el = els.get(i);
			if (editingTextElementId >= 0 && el.id() == editingTextElementId) continue;
			if (el.isMouseTransparent()) continue;
			if (el instanceof TextLayer tl && tl.isWrapping()) continue; // bounds set by margins, never draggable
			if (callbacks.elemRectScreen(el).contains(screenPt)) return el;
		}
		return null;
	}

	private Rectangle computeNewFloatRect(int handle, Rectangle base, Point origin, Point current) {
		double dx = (current.x - origin.x) / callbacks.getZoom();
		double dy = (current.y - origin.y) / callbacks.getZoom();
		double bx = base.x, by = base.y, bw = base.width, bh = base.height;
		final double MIN = 1.0;

		double rx, ry, rw, rh;
		switch (handle) {
		case 0 -> {
			rw = Math.max(MIN, bw - dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx + bw - rw;
			ry = by + bh - rh;
		}
		case 1 -> {
			rh = Math.max(MIN, bh - dy);
			rx = bx;
			rw = bw;
			ry = by + bh - rh;
		}
		case 2 -> {
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by + bh - rh;
		}
		case 3 -> {
			rw = Math.max(MIN, bw - dx);
			rx = bx + bw - rw;
			ry = by;
			rh = bh;
		}
		case 4 -> {
			rw = Math.max(MIN, bw + dx);
			rx = bx;
			ry = by;
			rh = bh;
		}
		case 5 -> {
			rw = Math.max(MIN, bw - dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx + bw - rw;
			ry = by;
		}
		case 6 -> {
			rh = Math.max(MIN, bh + dy);
			rx = bx;
			rw = bw;
			ry = by;
		}
		default -> {
			rw = Math.max(MIN, bw + dx);
			rh = Math.max(MIN, bh * rw / bw);
			rx = bx;
			ry = by;
		}
		}

		return new Rectangle((int) Math.round(rx), (int) Math.round(ry), (int) Math.round(rw), (int) Math.round(rh));
	}

	@Override
	public Dimension getPreferredSize() {
		if (callbacks.getWorkingImage() == null)
			return new Dimension(1, 1);
		return new Dimension((int) Math.ceil(callbacks.getWorkingImage().getWidth() * callbacks.getZoom()),
				(int) Math.ceil(callbacks.getWorkingImage().getHeight() * callbacks.getZoom()));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (callbacks.getWorkingImage() == null)
			return;

		Graphics2D g2 = (Graphics2D) g;
		double zoom = callbacks.getZoom();
		// Zoom >= 1: NEAREST (pixelgenau, MS-Paint-artig, schnell).
		// Zoom  < 1: BILINEAR (glatt beim Runterskalieren, deutlich schneller als BICUBIC).
		if (zoom >= 1.0) {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		} else {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		int cw = (int) Math.round(callbacks.getWorkingImage().getWidth() * zoom);
		int ch = (int) Math.round(callbacks.getWorkingImage().getHeight() * zoom);

		// ── Draw checkerboard background (one fillRect with cached TexturePaint) ──
		java.awt.Paint savedPaint = g2.getPaint();
		g2.setPaint(getCheckerPaint(callbacks.getCanvasBg1(), callbacks.getCanvasBg2()));
		g2.fillRect(0, 0, cw, ch);
		g2.setPaint(savedPaint);

		g2.drawImage(callbacks.getWorkingImage(), 0, 0, cw, ch, null);

		// ── Canvas sub-mode draw overlay (in-progress stroke preview) ────────
		if (canvasDrawOverlay != null) {
			g2.drawImage(canvasDrawOverlay, 0, 0, cw, ch, null);
		}

		// ── Free-path live preview ────────────────────────────────────────────
		if (freePathScreenPoints != null && freePathScreenPoints.size() >= 2) {
			java.awt.Stroke savedStroke = g2.getStroke();
			java.awt.Color savedColor   = g2.getColor();
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
					java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(STROKE_FREEPATH);
			g2.setColor(COLOR_FREEPATH_LINE);
			java.util.List<java.awt.Point> sp = freePathScreenPoints;
			for (int i = 1; i < sp.size(); i++)
				g2.drawLine(sp.get(i-1).x, sp.get(i-1).y, sp.get(i).x, sp.get(i).y);
			// Draw first point marker
			java.awt.Point fp = sp.get(0);
			g2.setColor(COLOR_FREEPATH_START);
			g2.fillOval(fp.x - 4, fp.y - 4, 8, 8);
			g2.setStroke(savedStroke);
			g2.setColor(savedColor);
		}

		// ── Element layers (non-destructive, rendered above base canvas) ──────
		java.util.List<Layer> activeEls   = callbacks.getActiveElements();
		java.util.List<Layer> selectedEls = callbacks.getSelectedElements();
		Layer primaryEl   = callbacks.getSelectedElement();
		boolean showOutlines = callbacks.isShowAllLayerOutlines();
		// Show all outlines during snap-drag so user can see snap targets
		boolean showOutlinesDuringSnap = showOutlines || isSnapDragging;

		// Single pass for non-primary; primary drawn separately on top.
		int primaryId = primaryEl != null ? primaryEl.id() : Integer.MIN_VALUE;
		boolean primaryRenderable = false;
		for (Layer el : activeEls) {
			if (el.id() == primaryId) { primaryRenderable = true; continue; }
			renderElement(g2, el, false, selectedEls, showOutlinesDuringSnap, zoom);
		}
		if (primaryRenderable) {
			renderElement(g2, primaryEl, true, selectedEls, showOutlinesDuringSnap, zoom);
		}

		// ── Text tool bounding-box preview (rubber-band draw phase + typing phase) ─
		if (textBoundingBox != null) {
			double z = callbacks.getZoom();
			int    tstyle   = (textBold ? Font.BOLD : 0) | (textItalic ? Font.ITALIC : 0);
			int    screenSz = Math.max(1, (int) Math.round(Math.max(6, textFontSize) * z));
			Font   tfont    = cachedFont(textFontName, tstyle, screenSz);
			g2.setFont(tfont);
			java.awt.FontMetrics fm = g2.getFontMetrics();
			int lineH = fm.getHeight();
			int sx = (int)(textBoundingBox.x * z);
			int sy = (int)(textBoundingBox.y * z);
			int boxW, boxH;
			float[] boxDash = { 5f, 3f };

			if (editingWrappingLayer) {
				// ── Wrapping mode: fixed bounds, word-wrap ──────────────────────────
				boxW = (int)(textBoundingBox.width  * z);
				boxH = (int)(textBoundingBox.height * z);
				g2.setColor(new Color(255, 255, 255, 15));
				g2.fillRect(sx, sy, boxW, boxH);
				g2.setColor(AppColors.ACCENT);
				g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, boxDash, 0f));
				g2.drawRect(sx, sy, boxW, boxH);
				g2.setStroke(new BasicStroke(1f));

				if (!textDrawingBox) {
					int maxW = Math.max(1, boxW - TEXT_PADDING * 2);
					java.util.List<TLine> lines = buildTextLines(textBuffer.toString(), fm, maxW, true);
					lastTextLines = lines; lastTextSx = sx; lastTextSy = sy; lastTextLineH = lineH;
					String text = textBuffer.toString();
					// Selection highlight
					if (hasTextSel()) {
						int sMin = selMin(), sMax = selMax();
						g2.setColor(new Color(66, 135, 245, 120));
						for (int li = 0; li < lines.size(); li++) {
							TLine ln = lines.get(li);
							int lSS = Math.max(sMin, ln.start()), lSE = Math.min(sMax, ln.end());
							if (lSS < lSE) {
								int x1 = sx + TEXT_PADDING + fm.stringWidth(text.substring(ln.start(), lSS));
								int x2 = sx + TEXT_PADDING + fm.stringWidth(text.substring(ln.start(), lSE));
								g2.fillRect(x1, sy + TEXT_PADDING + li * lineH, x2 - x1, lineH);
							}
						}
					}
					// Text
					g2.setColor(textColor);
					for (int li = 0; li < lines.size(); li++) {
						int drawY = sy + TEXT_PADDING + li * lineH + fm.getAscent();
						if (drawY > sy + boxH + fm.getDescent()) break;
						g2.drawString(lines.get(li).text(), sx + TEXT_PADDING, drawY);
					}
					// Caret
					if (textCaretVisible) {
						int li = findLineForCaret(textCaretPos, lines);
						TLine ln = lines.get(li);
						int off = Math.min(textCaretPos - ln.start(), ln.text().length());
						int cx = sx + TEXT_PADDING + fm.stringWidth(ln.text().substring(0, off));
						int cy = sy + TEXT_PADDING + li * lineH;
						g2.setColor(textColor);
						g2.setStroke(new BasicStroke(1.5f));
						g2.drawLine(cx, cy, cx, cy + lineH - 1);
						g2.setStroke(new BasicStroke(1f));
					}
				}
			} else {
				// ── Normal mode: auto-expand, no word-wrap ──────────────────────────
				java.util.List<TLine> lines = textDrawingBox
						? java.util.List.of(new TLine(0, 0, ""))
						: buildTextLines(textBuffer.toString(), fm, Integer.MAX_VALUE, false);
				int textW = TEXT_PADDING * 2;
				int textH = TEXT_PADDING * 2 + lineH * Math.max(1, lines.size());
				for (TLine l : lines) textW = Math.max(textW, fm.stringWidth(l.text()) + TEXT_PADDING * 2);
				int minW = textMinBox != null ? (int)(textMinBox.width  * z) : (int)(textBoundingBox.width  * z);
				int minH = textMinBox != null ? (int)(textMinBox.height * z) : (int)(textBoundingBox.height * z);
				boxW = Math.max(minW, textW);
				boxH = Math.max(minH, textH);
				if (!textDrawingBox) {
					textBoundingBox.width  = (int)Math.ceil(boxW / z);
					textBoundingBox.height = (int)Math.ceil(boxH / z);
				}
				g2.setColor(new Color(255, 255, 255, 20));
				g2.fillRect(sx, sy, boxW, boxH);
				g2.setColor(textDrawingBox ? new Color(180, 180, 255) : AppColors.ACCENT);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, boxDash, 0f));
				g2.drawRect(sx, sy, boxW, boxH);
				g2.setStroke(new BasicStroke(1f));

				if (!textDrawingBox) {
					lastTextLines = lines; lastTextSx = sx; lastTextSy = sy; lastTextLineH = lineH;
					String text = textBuffer.toString();
					// Selection highlight
					if (hasTextSel()) {
						int sMin = selMin(), sMax = selMax();
						g2.setColor(new Color(66, 135, 245, 120));
						for (int li = 0; li < lines.size(); li++) {
							TLine ln = lines.get(li);
							int lSS = Math.max(sMin, ln.start()), lSE = Math.min(sMax, ln.end());
							if (lSS < lSE) {
								int x1 = sx + TEXT_PADDING + fm.stringWidth(text.substring(ln.start(), lSS));
								int x2 = sx + TEXT_PADDING + fm.stringWidth(text.substring(ln.start(), lSE));
								g2.fillRect(x1, sy + TEXT_PADDING + li * lineH, x2 - x1, lineH);
							}
						}
					}
					// Text
					g2.setColor(textColor);
					for (int li = 0; li < lines.size(); li++) {
						g2.drawString(lines.get(li).text(), sx + TEXT_PADDING,
								sy + TEXT_PADDING + li * lineH + fm.getAscent());
					}
					// Caret
					if (textCaretVisible) {
						int li = findLineForCaret(textCaretPos, lines);
						TLine ln = lines.get(li);
						int off = Math.min(textCaretPos - ln.start(), ln.text().length());
						int cx = sx + TEXT_PADDING + fm.stringWidth(ln.text().substring(0, off));
						int cy = sy + TEXT_PADDING + li * lineH;
						g2.setColor(textColor);
						g2.setStroke(new BasicStroke(1.5f));
						g2.drawLine(cx, cy, cx, cy + lineH - 1);
						g2.setStroke(new BasicStroke(1f));
					}
				}
			}
		}

		// ── Book mode: live margin overlay (non-destructive) ─────────────────────
		if (callbacks.isBookMode()) {
			PageLayout pl = callbacks.getPageLayout();
			if (pl != null && callbacks.getWorkingImage() != null) {
				int imgW = callbacks.getWorkingImage().getWidth();
				int imgH = callbacks.getWorkingImage().getHeight();
				int mL = pl.marginLeftPx(), mR = pl.marginRightPx();
				int mT = pl.marginTopPx(),  mB = pl.marginBottomPx();
				int sL = (int) Math.round(mL * zoom);
				int sT = (int) Math.round(mT * zoom);
				int sR = (int) Math.round((imgW - mR) * zoom);
				int sB = (int) Math.round((imgH - mB) * zoom);
				float[] dash = { 6f, 4f };
				g2.setColor(new Color(0, 120, 220, 200));
				g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
				g2.drawRect(sL, sT, Math.max(0, sR - sL), Math.max(0, sB - sT));
				g2.setStroke(new BasicStroke(1f));
				// Semi-transparent margin bands
				g2.setColor(new Color(0, 120, 220, 18));
				g2.fillRect(0,   0,   cw, sT);           // top band
				g2.fillRect(0,   sB,  cw, ch - sB);      // bottom band
				g2.fillRect(0,   sT,  sL, sB - sT);      // left band
				g2.fillRect(sR,  sT,  cw - sR, sB - sT); // right band
			}
		}

		// Rubber-band multi-select rect (screen coords, drawn while shift-dragging)
		if (isElemBanding && elemBandStart != null && elemBandEnd != null) {
			int bx = Math.min(elemBandStart.x, elemBandEnd.x);
			int by = Math.min(elemBandStart.y, elemBandEnd.y);
			int bw = Math.abs(elemBandEnd.x - elemBandStart.x);
			int bh = Math.abs(elemBandEnd.y - elemBandStart.y);
			g2.setColor(new Color(100, 150, 255, 40));
			g2.fillRect(bx, by, bw, bh);
			float[] dash = { 5f, 3f };
			g2.setColor(new Color(100, 150, 255));
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
			g2.drawRect(bx, by, bw, bh);
		}

		// Selections overlay
		g2.setColor(new Color(255, 0, 0, 70));
		for (Rectangle r : callbacks.getSelectedAreas()) {
			int sx = (int) Math.round(r.x * callbacks.getZoom());
			int sy = (int) Math.round(r.y * callbacks.getZoom());
			int sw = (int) Math.round(r.width * callbacks.getZoom());
			int sh = (int) Math.round(r.height * callbacks.getZoom());
			g2.fillRect(sx, sy, sw, sh);
		}

		g2.setColor(Color.RED);
		g2.setStroke(new BasicStroke(1.2f));
		for (Rectangle r : callbacks.getSelectedAreas()) {
			int sx = (int) Math.round(r.x * callbacks.getZoom());
			int sy = (int) Math.round(r.y * callbacks.getZoom());
			int sw = (int) Math.round(r.width * callbacks.getZoom());
			int sh = (int) Math.round(r.height * callbacks.getZoom());
			g2.drawRect(sx, sy, sw, sh);
		}

		// Drag handles on the active selection (PAINT mode SELECT tool only)
		if (callbacks.getAppMode() == AppMode.PAINT && !callbacks.isSelecting()) {
			Rectangle aSel = callbacks.getActiveSelection();
			if (aSel != null) {
				int sx = (int) Math.round(aSel.x * callbacks.getZoom());
				int sy = (int) Math.round(aSel.y * callbacks.getZoom());
				int sw = (int) Math.round(aSel.width  * callbacks.getZoom());
				int sh = (int) Math.round(aSel.height * callbacks.getZoom());
				g2.setStroke(new BasicStroke(1f));
				for (Rectangle hr : callbacks.handleRects(new Rectangle(sx, sy, sw, sh))) {
					g2.setColor(Color.WHITE);
					g2.fillRect(hr.x, hr.y, hr.width, hr.height);
					g2.setColor(AppColors.ACCENT);
					g2.drawRect(hr.x, hr.y, hr.width, hr.height);
				}
			}
		}

		// Active selection being drawn (during drag)
		if (callbacks.isSelecting() && callbacks.getSelectionStart() != null && callbacks.getSelectionEnd() != null) {
			int x = Math.min(callbacks.getSelectionStart().x, callbacks.getSelectionEnd().x);
			int y = Math.min(callbacks.getSelectionStart().y, callbacks.getSelectionEnd().y);
			int w = Math.abs(callbacks.getSelectionEnd().x - callbacks.getSelectionStart().x);
			int h = Math.abs(callbacks.getSelectionEnd().y - callbacks.getSelectionStart().y);
			g2.setColor(new Color(0, 200, 255, 60));
			g2.fillRect((int) Math.round(x * callbacks.getZoom()), (int) Math.round(y * callbacks.getZoom()),
					(int) Math.round(w * callbacks.getZoom()), (int) Math.round(h * callbacks.getZoom()));
			g2.setColor(new Color(0, 200, 255));
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawRect((int) Math.round(x * callbacks.getZoom()), (int) Math.round(y * callbacks.getZoom()),
					(int) Math.round(w * callbacks.getZoom()), (int) Math.round(h * callbacks.getZoom()));
		}

		// Floating selection with handles
		if (callbacks.getFloatingImage() != null && callbacks.getFloatRect() != null) {
			Rectangle fr = callbacks.getFloatRect();
			int fx = (int) Math.round(fr.x * callbacks.getZoom());
			int fy = (int) Math.round(fr.y * callbacks.getZoom());
			int fw = (int) Math.round(fr.width * callbacks.getZoom());
			int fh = (int) Math.round(fr.height * callbacks.getZoom());
			g2.drawImage(callbacks.getFloatingImage(), fx, fy, fw, fh, null);

			float[] dash = { 6f, 4f };
			g2.setColor(Color.WHITE);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
			g2.drawRect(fx, fy, fw, fh);
			g2.setColor(Color.BLACK);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 6f));
			g2.drawRect(fx, fy, fw, fh);

			g2.setStroke(new BasicStroke(1f));
			for (Rectangle hr : callbacks.handleRects(new Rectangle(fx, fy, fw, fh))) {
				g2.setColor(Color.WHITE);
				g2.fillRect(hr.x, hr.y, hr.width, hr.height);
				g2.setColor(AppColors.ACCENT);
				g2.drawRect(hr.x, hr.y, hr.width, hr.height);
			}
		}

		// ── Grid overlay ──────────────────────────────────────────────────────
		if (callbacks.isGridVisible()) {
			drawGrid(g2, callbacks.getZoom());
		}

		// ── Brush-shape preview cursor ────────────────────────────────────────
		if (brushPreviewPt != null && callbacks.getPaintToolbar() != null) {
			drawBrushPreview(g2, brushPreviewPt);
		}
	}

	/**
	 * Draws a dashed outline representing the current brush shape and stroke width
	 * at the given screen position. Renders two overlapping dashes (white + black)
	 * for contrast on any background — identical style to the floating-selection outline.
	 */
	private void drawBrushPreview(Graphics2D g2, Point screenPt) {
		PaintEngine.Tool         tool  = callbacks.getPaintToolbar().getActiveTool();
		PaintEngine.BrushShape   shape = callbacks.getPaintToolbar().getBrushShape();
		int strokePx = callbacks.getPaintToolbar().getStrokeWidth();
		double zoom  = callbacks.getZoom();

		// Diameter in screen pixels; at least 2 px so the outline is always visible
		int d = Math.max(2, (int) Math.round(strokePx * zoom));
		int cx = screenPt.x, cy = screenPt.y;
		int x  = cx - d / 2, y  = cy - d / 2;

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		float[] dash = { 4f, 4f };
		java.awt.Stroke dashWhite = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f);
		java.awt.Stroke dashBlack = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 4f);

		boolean round = (shape == PaintEngine.BrushShape.ROUND)
				|| tool == PaintEngine.Tool.ERASER
				|| tool == PaintEngine.Tool.ERASER_BG
				|| tool == PaintEngine.Tool.ERASER_COLOR;

		g2.setStroke(dashWhite); g2.setColor(Color.WHITE);
		if (round) g2.drawOval(x, y, d, d); else g2.drawRect(x, y, d, d);

		g2.setStroke(dashBlack); g2.setColor(Color.BLACK);
		if (round) g2.drawOval(x, y, d, d); else g2.drawRect(x, y, d, d);

		// Reset to avoid leaking stroke/AA settings
		g2.setStroke(new BasicStroke(1f));
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	private static final int GRID_PX = 32; // grid cell size in image pixels

	private void drawGrid(Graphics2D g2, double zoom) {
		java.awt.image.BufferedImage img = callbacks.getWorkingImage();
		if (img == null) return;
		int W = img.getWidth(), H = img.getHeight();
		int step = Math.max(1, (int) Math.round(GRID_PX * zoom));
		int screenW = (int) Math.round(W * zoom);
		int screenH = (int) Math.round(H * zoom);
		g2.setStroke(new BasicStroke(0.5f));
		// Minor lines (every cell)
		g2.setColor(new java.awt.Color(255, 255, 255, 30));
		for (int x = 0; x <= screenW; x += step) g2.drawLine(x, 0, x, screenH);
		for (int y = 0; y <= screenH; y += step) g2.drawLine(0, y, screenW, y);
		// Major lines (every 4 cells)
		g2.setColor(new java.awt.Color(255, 255, 255, 70));
		for (int x = 0; x <= screenW; x += step * 4) g2.drawLine(x, 0, x, screenH);
		for (int y = 0; y <= screenH; y += step * 4) g2.drawLine(0, y, screenW, y);
	}

	// =========================================================================
	// Element Rendering (single-pass helper)
	// =========================================================================

	/**
	 * Renders a single layer element (ImageLayer / TextLayer / PathLayer) plus its
	 * selection / hover / dim outline. Extracted from the old two-pass loop; the
	 * caller decides the draw order (non-primary first, primary last).
	 */
	private void renderElement(Graphics2D g2, Layer el, boolean isPrimary,
			java.util.List<Layer> selectedEls, boolean showOutlinesDuringSnap, double zoom) {
		// Skip the element currently being text-edited — the live preview replaces it
		if (editingTextElementId >= 0 && el.id() == editingTextElementId) return;
		if (el.isHidden()) return;

		Rectangle sr = callbacks.elemRectScreen(el);

		// For PathLayer the frame must be derived from the actual point
		// extents, not from the stored pl.x/pl.y/pl.width/pl.height.
		Rectangle frameRect = sr;
		if (el instanceof PathLayer pl) {
			double fMinX = Double.MAX_VALUE, fMaxX = -Double.MAX_VALUE;
			double fMinY = Double.MAX_VALUE, fMaxY = -Double.MAX_VALUE;
			for (Point3D p : pl.points()) {
				if (p.x < fMinX) fMinX = p.x;
				if (p.x > fMaxX) fMaxX = p.x;
				if (p.y < fMinY) fMinY = p.y;
				if (p.y > fMaxY) fMaxY = p.y;
			}
			int fx = (int) Math.round((pl.x() + fMinX - 8) * zoom);
			int fy = (int) Math.round((pl.y() + fMinY - 8) * zoom);
			int fw = (int) Math.round((fMaxX - fMinX + 16) * zoom);
			int fh = (int) Math.round((fMaxY - fMinY + 16) * zoom);
			frameRect = new Rectangle(fx, fy, Math.max(1, fw), Math.max(1, fh));
		}

		if (el instanceof ImageLayer il) {
			java.awt.Composite origComposite = g2.getComposite();
			float alpha = il.opacity() / 100.0f;
			if (alpha < 1.0f) {
				g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
			}

			if (Math.abs(il.rotationAngle()) > 0.001) {
				java.awt.geom.AffineTransform orig = g2.getTransform();
				double cx = sr.x + sr.width / 2.0;
				double cy = sr.y + sr.height / 2.0;
				g2.rotate(Math.toRadians(il.rotationAngle()), cx, cy);
				g2.drawImage(il.image(), sr.x, sr.y, sr.width, sr.height, null);
				g2.setTransform(orig);
			} else {
				g2.drawImage(il.image(), sr.x, sr.y, sr.width, sr.height, null);
			}

			if (alpha < 1.0f) {
				g2.setComposite(origComposite);
			}
		} else if (el instanceof TextLayer tl) {
			int tstyle = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
			int screenFontSz = Math.max(1, (int) Math.round(tl.fontSize() * zoom));
			Font tfont = cachedFont(tl.fontName(), tstyle, screenFontSz);
			g2.setFont(tfont);
			g2.setColor(tl.fontColor());
			java.awt.FontMetrics tfm = g2.getFontMetrics();
			int tpx = sr.x + (int)(TEXT_PADDING * zoom);
			int tpy = sr.y + (int)(TEXT_PADDING * zoom);
			if (tl.isWrapping()) {
				int maxW = sr.width - (int)(TEXT_PADDING * 2 * zoom);
				if (maxW < 1) maxW = 1;
				java.util.List<String> wrapped = wrapText(tl.text(), tfm, maxW);
				for (int li = 0; li < wrapped.size(); li++) {
					g2.drawString(wrapped.get(li), tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
				}
			} else {
				String[] tLines = tl.text().split("\n", -1);
				for (int li = 0; li < tLines.length; li++) {
					g2.drawString(tLines[li], tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
				}
			}
		} else if (el instanceof PathLayer pl) {
			renderPathLayer(g2, pl, sr, zoom, isPrimary);
		}

		boolean isHov = el.id() == hoveredElementId;
		boolean isSel = false;
		for (Layer s : selectedEls) {
			if (s.id() == el.id()) { isSel = true; break; }
		}
		if (isSel) {
			g2.setColor(AppColors.ACCENT);
			g2.setStroke(STROKE_SEL_DASH);
			g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
			if (isPrimary) {
				g2.setStroke(STROKE_1);
				Rectangle[] hrs = callbacks.handleRects(frameRect);
				for (int hi = 0; hi < hrs.length; hi++) {
					Rectangle hr = hrs[hi];
					boolean hov = (hi == hoveredHandleIndex);
					g2.setColor(hov ? COLOR_HANDLE_HOV_FILL : Color.WHITE);
					g2.fillRect(hr.x, hr.y, hr.width, hr.height);
					g2.setColor(hov ? COLOR_HANDLE_HOV_BORDER : AppColors.ACCENT);
					g2.setStroke(hov ? STROKE_1_5 : STROKE_1);
					g2.drawRect(hr.x, hr.y, hr.width, hr.height);
				}
			}
			Point rotPos = callbacks.getRotationHandlePos(frameRect);
			g2.setColor(hoveredHandleIndex == -2 ? COLOR_ROT_HOV : COLOR_ROT);
			g2.setStroke(STROKE_1_5);
			g2.drawOval(rotPos.x - 6, rotPos.y - 6, 12, 12);
			g2.fillOval(rotPos.x - 3, rotPos.y - 3, 6, 6);
		} else if (isHov) {
			g2.setColor(COLOR_HOVER_OUTLINE);
			g2.setStroke(STROKE_1_5);
			g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
		} else if (showOutlinesDuringSnap) {
			g2.setColor(COLOR_DIM_OUTLINE);
			g2.setStroke(STROKE_DIM_DASH);
			g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
		}
	}

	// =========================================================================
	// PathLayer Rendering
	// =========================================================================

	/**
	 * Renders a PathLayer: lines connecting points + point markers + hover/select effects.
	 */
	private void renderPathLayer(Graphics2D g2, PathLayer pl, Rectangle sr, double zoom, boolean isPrimary) {
		java.util.List<Point3D> points = pl.points();
		if (points.isEmpty()) return;

		// Screen origin of the layer (independent of the padded elemRectScreen / sr).
		// All point screen coords are computed as (pl.x() + p.x) * zoom.
		final int ox = (int) Math.round(pl.x() * zoom);
		final int oy = (int) Math.round(pl.y() * zoom);

		// Render optional image (if present)
		if (pl.image() != null) {
			g2.drawImage(pl.image(), sr.x, sr.y, sr.width, sr.height, null);
		}

		int pointRadius = 8;  // Pixel radius for point rendering

		// ── Draw lines connecting points ──
		g2.setColor(new Color(0, 200, 255, 180));  // Cyan
		g2.setStroke(new BasicStroke(1.5f));
		for (int i = 0; i < points.size() - 1; i++) {
			Point3D p1 = points.get(i);
			Point3D p2 = points.get(i + 1);
			int x1 = ox + (int) Math.round(p1.x * zoom);
			int y1 = oy + (int) Math.round(p1.y * zoom);
			int x2 = ox + (int) Math.round(p2.x * zoom);
			int y2 = oy + (int) Math.round(p2.y * zoom);
			g2.drawLine(x1, y1, x2, y2);
		}

		// Close polygon if needed
		if (pl.isClosed() && points.size() >= 2) {
			Point3D p1 = points.get(points.size() - 1);
			Point3D p2 = points.get(0);
			int x1 = ox + (int) Math.round(p1.x * zoom);
			int y1 = oy + (int) Math.round(p1.y * zoom);
			int x2 = ox + (int) Math.round(p2.x * zoom);
			int y2 = oy + (int) Math.round(p2.y * zoom);
			g2.drawLine(x1, y1, x2, y2);
		}

		// ── Draw point markers ──
		for (int i = 0; i < points.size(); i++) {
			Point3D p = points.get(i);
			int px = ox + (int) Math.round(p.x * zoom);
			int py = oy + (int) Math.round(p.y * zoom);

			boolean isHovered = isPrimary && (i == hoveredPathPointIndex);
			boolean isSelected = isPrimary && (i == selectedPathPointIndex);

			// Point size varies based on state (hover keeps same size so line intersection stays visible)
			int drawRadius = pointRadius;
			if (isSelected) drawRadius = pointRadius + 4;

			if (isHovered && !isSelected) {
				// Hover: only a thin dashed outline, no fill
				g2.setColor(new Color(255, 255, 255, 200));
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
						10f, new float[]{3f, 3f}, 0f));
				g2.drawOval(px - drawRadius, py - drawRadius, drawRadius * 2, drawRadius * 2);
			} else {
				// Normal / selected: filled circle with border
				Color fillColor  = isSelected ? new Color(255, 200, 0) : Color.WHITE;
				Color borderColor = isSelected ? new Color(255, 140, 0) : new Color(0, 150, 200);

				g2.setColor(fillColor);
				g2.fillOval(px - drawRadius, py - drawRadius, drawRadius * 2, drawRadius * 2);

				g2.setColor(borderColor);
				g2.setStroke(isSelected ? new BasicStroke(2f) : new BasicStroke(1.5f));
				g2.drawOval(px - drawRadius, py - drawRadius, drawRadius * 2, drawRadius * 2);
			}

			// Point index label
			g2.setColor(Color.BLACK);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			g2.drawString(String.valueOf(i), px + drawRadius + 3, py - 2);
		}
	}

}
