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

	// Element multi-select: rubber-band state (screen coords)
	private Point elemBandStart = null;
	private Point elemBandEnd   = null;
	private boolean isElemBanding = false;
	// Delta-based multi-drag: last image-space drag point
	private Point elemLastImgPt = null;

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

	// ── Modeless text-options dialog ──────────────────────────────────────────
	private javax.swing.JDialog           textChooserDlg    = null;
	private javax.swing.JTextArea         dlgTextArea       = null;
	private javax.swing.JComboBox<String> dlgFontBox        = null;
	private javax.swing.JSpinner          dlgSizeSpinner    = null;
	private javax.swing.JCheckBox         dlgBoldCb         = null;
	private javax.swing.JCheckBox         dlgItalicCb       = null;
	private javax.swing.JButton           dlgColorBtn       = null;
	/** True while syncing controls from an element (suppresses the listener → element update loop). */
	private boolean suppressChooserSync = false;

	// ── Path editor state ────────────────────────────────────────────────────────
	/** Index of the currently hovered path point (for PathLayer), or -1. */
	private int hoveredPathPointIndex = -1;
	/** Index of the currently selected path point (for PathLayer), or -1. */
	private int selectedPathPointIndex = -1;

	// ── Hover highlight (bidirectional with ElementLayerPanel) ───────────────
	/** Id of the element the mouse is currently over, or -1. Used for bidirectional hover. */
	private int hoveredElementId = -1;

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
		System.err.println("[DEBUG] CanvasPanel created, focusable=" + isFocusable());
		setupMouseHandling();
		setupKeyBindings();
	}

	/** Called by ElementLayerPanel when the mouse enters/leaves a tile. */
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
		textBuffer.setLength(0);
		editingTextElementId = -1; editingOriginalElement = null;
		// Reset path editor state
		hoveredPathPointIndex = -1; selectedPathPointIndex = -1;
		// Cancel canvas draw overlay
		canvasDrawOverlay = null; overlayLastPt = null; overlayShapeStart = null;
		// Hover state
		hoveredElementId = -1;
	}

	private void setupMouseHandling() {
		MouseAdapter handler = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				System.err.println("[DEBUG] mousePressed called! Button=" + e.getButton() + ", Point=" + e.getPoint());
				requestFocusInWindow();

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

				if (!SwingUtilities.isLeftMouseButton(e) || callbacks.getWorkingImage() == null)
					return;

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

				// ── PathLayer point selection ────────────────────────────────────
				Layer primaryEl = callbacks.getSelectedElement();
				if (primaryEl instanceof PathLayer pl) {
					Rectangle sr = callbacks.elemRectScreen(pl);
					Point screenPt = e.getPoint();
					// Convert screen-relative offset to image-space by dividing by zoom
					double zoom = callbacks.getZoom();
					double layerX = (screenPt.x - sr.x) / zoom;
					double layerY = (screenPt.y - sr.y) / zoom;

					java.util.List<Point3D> points = pl.points();
					int hitRadius = 12;
					int hitPoint = -1;
					for (int i = 0; i < points.size(); i++) {
						Point3D p = points.get(i);
						double dist = Math.sqrt(Math.pow(layerX - p.x, 2) + Math.pow(layerY - p.y, 2));
						if (dist <= hitRadius) {
							hitPoint = i;
							break;
						}
					}

					if (hitPoint >= 0) {
						selectedPathPointIndex = hitPoint;
						System.err.println("[DEBUG] Selected path point " + hitPoint);
						repaint();
						return;
					}

					// NEW: Click inside polygon area → select path + start drag
					if (isInsidePathPolygon(pl, e.getPoint())) {
						callbacks.setDraggingElement(true);
						elemLastImgPt = imgPt;
						repaint();
						return;
					}

					// No point hit, no polygon hit → deselect path
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

					// CHECK DOUBLE-CLICK ON TEXTLAYER FIRST (before any other element handling)
					Layer hit = hitElement(e.getPoint());
					if (hit instanceof TextLayer && e.getClickCount() == 2) {
						System.err.println("[DEBUG] Double-click on TextLayer detected!");
						enterTextEditMode(hit);
						return;
					}

					// Check resize handles on the primary selected element
					Layer primary = callbacks.getSelectedElement();
					if (primary != null) {
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
						// Click on empty area → deselect all
						if (!callbacks.getSelectedElements().isEmpty()) {
							callbacks.setSelectedElement(null);
							repaint();
						}
						// With TEXT tool: a bare click on empty space is NOT a rubber-band start;
						// only a real drag (handled in mouseDragged → mouseReleased) should create a frame.
						// Return here so we don't fall through to the TEXT case in the tool switch below.
						if (callbacks.getAppMode() == AppMode.PAINT
								&& callbacks.getPaintToolbar().getActiveTool() == PaintEngine.Tool.TEXT) {
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
					switch (tool) {
					case PENCIL, ERASER -> {
						if (callbacks.isCanvasSubMode() && tool == PaintEngine.Tool.PENCIL) {
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
							callbacks.pushUndo();
							callbacks.setLastPaintPoint(imgPt);
							callbacks.paintDot(imgPt);
						}
					}
					case FLOODFILL -> {
						callbacks.pushUndo();
						PaintEngine.floodFill(callbacks.getWorkingImage(), imgPt.x, imgPt.y,
								callbacks.getPaintToolbar().getPrimaryColor(), 30);
						callbacks.markDirty();
					}
					case EYEDROPPER -> {
						Color picked = PaintEngine.pickColor(callbacks.getWorkingImage(), imgPt.x, imgPt.y);
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

							System.err.println("[DEBUG] Created new path with triangle at " + imgPt);
						}
						repaint();
					}
					}
				} else {
					if (callbacks.isFloodfillMode()) {
						callbacks.pushUndo();
						callbacks.performFloodfill(e.getPoint());
					} else {
						// Start a new selection (Shift adds to existing)
						System.err.println("[DEBUG] Started selection at " + imgPt + ", appMode="
								+ callbacks.getAppMode() + ", floodfill=" + callbacks.isFloodfillMode());
						callbacks.setSelecting(true);
						callbacks.setSelectionStart(imgPt);
						callbacks.setSelectionEnd(imgPt);
					}
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				System.err.println("[DEBUG] mouseDragged called! Point=" + e.getPoint());
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

				if (callbacks.getWorkingImage() == null)
					return;
				Point imgPt = callbacks.screenToImage(e.getPoint());

				// ── PathLayer point drag ────────────────────────────────────────
				Layer primary = callbacks.getSelectedElement();
				if (primary instanceof PathLayer pl && selectedPathPointIndex >= 0) {
					PathLayer updated = pl.withMovedPoint(selectedPathPointIndex, imgPt.x - pl.x(), imgPt.y - pl.y());

					// ── DEBUG: print all point coords + frame bounds after move ──
					java.util.List<Point3D> dbgPts = updated.points();
					double dbgMinX = Double.MAX_VALUE, dbgMaxX = -Double.MAX_VALUE;
					double dbgMinY = Double.MAX_VALUE, dbgMaxY = -Double.MAX_VALUE;
					StringBuilder sb = new StringBuilder();
					sb.append("[PATH-DRAG] dragged=").append(selectedPathPointIndex).append("\n");
					for (int di = 0; di < dbgPts.size(); di++) {
						Point3D dp = dbgPts.get(di);
						if (dp.x < dbgMinX) dbgMinX = dp.x;
						if (dp.x > dbgMaxX) dbgMaxX = dp.x;
						if (dp.y < dbgMinY) dbgMinY = dp.y;
						if (dp.y > dbgMaxY) dbgMaxY = dp.y;
						sb.append("  point[").append(di).append("] x=")
						  .append(String.format("%.1f", dp.x))
						  .append(" y=").append(String.format("%.1f", dp.y));
						if (di == selectedPathPointIndex) sb.append(" <-- dragged");
						sb.append("\n");
					}
					sb.append("  frame: left=").append(String.format("%.1f", dbgMinX - 8))
					  .append(" right=").append(String.format("%.1f", dbgMaxX + 8))
					  .append(" top=").append(String.format("%.1f", dbgMinY - 8))
					  .append(" bottom=").append(String.format("%.1f", dbgMaxY + 8));
					System.err.println(sb);

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
					if (tool == PaintEngine.Tool.PENCIL || tool == PaintEngine.Tool.ERASER) {
						boolean aa = callbacks.getPaintToolbar().isAntialiasing();
						if (callbacks.isCanvasSubMode() && tool == PaintEngine.Tool.PENCIL
								&& canvasDrawOverlay != null) {
							// Canvas sub-mode: draw on overlay
							PaintEngine.drawPencil(canvasDrawOverlay,
									overlayLastPt != null ? overlayLastPt : imgPt, imgPt,
									callbacks.getPaintToolbar().getPrimaryColor(),
									callbacks.getPaintToolbar().getStrokeWidth(),
									callbacks.getPaintToolbar().getBrushShape(), aa);
							overlayLastPt = imgPt;
							repaint();
						} else if (tool == PaintEngine.Tool.PENCIL) {
							PaintEngine.drawPencil(callbacks.getWorkingImage(), callbacks.getLastPaintPoint(), imgPt,
									callbacks.getPaintToolbar().getPrimaryColor(),
									callbacks.getPaintToolbar().getStrokeWidth(),
									callbacks.getPaintToolbar().getBrushShape(), aa);
							callbacks.setLastPaintPoint(imgPt);
							callbacks.markDirty();
						} else {
							PaintEngine.drawEraser(callbacks.getWorkingImage(), callbacks.getLastPaintPoint(), imgPt,
									callbacks.getPaintToolbar().getStrokeWidth(), aa);
							callbacks.setLastPaintPoint(imgPt);
							callbacks.markDirty();
						}
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
						System.err.println("[DEBUG] Selection drag (PAINT mode): imgPt=" + imgPt);
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
					}
				} else {
					System.err.println("[DEBUG] Selection drag: imgPt=" + imgPt + ", start="
							+ callbacks.getSelectionStart() + ", end=" + callbacks.getSelectionEnd() + " -> " + imgPt);
					callbacks.setSelectionEnd(imgPt);
					callbacks.repaintCanvas();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				panStart = null;
				panViewPos = null;
				elemLastImgPt = null;

				if (callbacks.getWorkingImage() == null)
					return;

				// Finalize TEXT tool frame (if a rubber-band drag happened)
				if (textDragStart != null) {
					if (textDrawingBox && textBoundingBox != null
							&& textBoundingBox.width >= 10 && textBoundingBox.height >= 6) {
						// Real drag → enter text-input mode
						textDrawingBox = false;
						textMinBox = new Rectangle(textBoundingBox);
						ensureTextChooserVisible();
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

				callbacks.setDraggingElement(false);
				callbacks.setElemActiveHandle(-1);

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
				System.err.println("[DEBUG] mouseWheelMoved called! Rotation=" + e.getWheelRotation() + ", Ctrl="
						+ ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0));
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
						if (dlgSizeSpinner != null) dlgSizeSpinner.setValue(newSize);
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
					Rectangle sr = callbacks.elemRectScreen(pl);
					Point screenPt = e.getPoint();
					// Convert screen-relative offset to image-space by dividing by zoom
					double zoom = callbacks.getZoom();
					double layerX = (screenPt.x - sr.x) / zoom;
					double layerY = (screenPt.y - sr.y) / zoom;

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
					repaint();  // Redraw point highlights
				}

				Layer hit = hitElement(e.getPoint());
				int newId = hit != null ? hit.id() : -1;
				if (newId != hoveredElementId) {
					hoveredElementId = newId;
					callbacks.onCanvasElementHover(newId);
					repaint(); // redraw hover outline on canvas
				}
			}
		});
		System.err.println("[DEBUG] Mouse listeners registered! Focusable=" + isFocusable() + ", Enabled=" + isEnabled()
				+ ", Visible=" + isVisible());
	}

	private void setupKeyBindings() {
		// Text tool: all printable keys are captured via a KeyAdapter registered on the
		// panel so we don't need to enumerate individual KeyStrokes in the InputMap.
		addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent ke) {
				// ── PathLayer point editing ────────────────────────────────────
				Layer primary = callbacks.getSelectedElement();
				if (primary instanceof PathLayer pl) {
					int code = ke.getKeyCode();
					if (code == KeyEvent.VK_DELETE) {
						if (selectedPathPointIndex >= 0) {
							// DEL: remove selected point
							System.err.println("[DEBUG] DELETE point at index " + selectedPathPointIndex);
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
						System.err.println("[DEBUG] ADD point after index " + selectedPathPointIndex + ", keyCode=" + code);
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
						System.err.println("[DEBUG] Added point, new pointCount=" + updated.pointCount());
						ke.consume(); repaint(); return;
					}
				}

				// ── Text tool editing ──────────────────────────────────────────
				if (textBoundingBox == null || textDrawingBox) return;
				int code = ke.getKeyCode();
				if (code == KeyEvent.VK_ENTER) {
					// Shift+Enter = newline inside text; plain Enter = commit
					if (ke.isShiftDown()) {
						textBuffer.append('\n');
					} else {
						commitText();
					}
					ke.consume(); repaint(); return;
				}
				if (code == KeyEvent.VK_ESCAPE) {
					// Cancel edit: restore original element if we were editing one
					if (editingOriginalElement != null) {
						callbacks.getActiveElements().add(editingOriginalElement);
					}
					textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
					textBuffer.setLength(0);
					editingTextElementId = -1; editingOriginalElement = null;
					ke.consume(); repaint(); return;
				}
				if (code == KeyEvent.VK_BACK_SPACE) {
					if (textBuffer.length() > 0)
						textBuffer.deleteCharAt(textBuffer.length() - 1);
					ke.consume(); repaint(); return;
				}
			}
			@Override
			public void keyTyped(java.awt.event.KeyEvent ke) {
				if (textBoundingBox == null || textDrawingBox) return;
				char c = ke.getKeyChar();
				if (c == KeyEvent.CHAR_UNDEFINED) return;
				if (c == '\r' || c == '\n' || c == '\u001b' || c == '\b') return; // handled in keyPressed
				textBuffer.append(c);
				ke.consume(); repaint();
			}
		});

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (textBoundingBox != null) {
					if (editingOriginalElement != null) callbacks.getActiveElements().add(editingOriginalElement);
					textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
					textBuffer.setLength(0);
					editingTextElementId = -1; editingOriginalElement = null;
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

	private void drawShapePreview(BufferedImage img, PaintEngine.Tool tool, Point from, Point to) {
		Graphics2D g2 = img.createGraphics();
		g2.setColor(callbacks.getPaintToolbar().getPrimaryColor());
		g2.setStroke(new BasicStroke(callbacks.getPaintToolbar().getStrokeWidth()));

		int x = Math.min(from.x, to.x);
		int y = Math.min(from.y, to.y);
		int w = Math.abs(to.x - from.x);
		int h = Math.abs(to.y - from.y);

		if (tool == PaintEngine.Tool.LINE) {
			g2.drawLine(from.x, from.y, to.x, to.y);
		} else if (tool == PaintEngine.Tool.CIRCLE) {
			g2.drawOval(x, y, w, h);
		} else if (tool == PaintEngine.Tool.RECT) {
			g2.drawRect(x, y, w, h);
		}
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
	private void ensureTextChooserVisible() {
		if (textChooserDlg == null) buildTextChooserDialog();
		syncTextChooserFromFields();
		if (!textChooserDlg.isShowing()) textChooserDlg.setVisible(true);
		// Request focus IMMEDIATELY for canvas so key events (text typing) are received
		// Do NOT use invokeLater or toFront() as they interfere with keyboard focus
		requestFocusInWindow();
	}

	/** Builds the one-time modeless dialog and wires live-update listeners. */
	private void buildTextChooserDialog() {
		javax.swing.JFrame topFrame = (javax.swing.JFrame)
				javax.swing.SwingUtilities.getWindowAncestor(this);

		textChooserDlg = new javax.swing.JDialog(topFrame, "Textoptionen", false); // MODELESS
		textChooserDlg.setLayout(new java.awt.BorderLayout(8, 8));
		textChooserDlg.getContentPane().setBackground(new Color(36, 36, 36));
		textChooserDlg.setAlwaysOnTop(true);

		javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridBagLayout());
		form.setBackground(new Color(36, 36, 36));
		form.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 14, 6, 14));
		java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
		gbc.insets = new java.awt.Insets(3, 4, 3, 4);
		gbc.anchor = java.awt.GridBagConstraints.WEST;

		// Helper to add label + control row
		int[] rowIdx = {0};
		java.util.function.BiConsumer<String, java.awt.Component> addRow = (lbl, comp) -> {
			gbc.gridx = 0; gbc.gridy = rowIdx[0]; gbc.weightx = 0; gbc.fill = java.awt.GridBagConstraints.NONE;
			javax.swing.JLabel l = new javax.swing.JLabel(lbl);
			l.setForeground(AppColors.TEXT); l.setFont(new Font("SansSerif", Font.PLAIN, 11));
			form.add(l, gbc);
			gbc.gridx = 1; gbc.weightx = 1; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
			form.add(comp, gbc);
			rowIdx[0]++;
			gbc.fill = java.awt.GridBagConstraints.NONE;
		};

		// Text input field (for editing selected TextLayer content)
		dlgTextArea = new javax.swing.JTextArea(4, 30);
		dlgTextArea.setBackground(new Color(50, 50, 50));
		dlgTextArea.setForeground(AppColors.TEXT);
		dlgTextArea.setLineWrap(true);
		dlgTextArea.setWrapStyleWord(true);
		dlgTextArea.setFont(new Font("Monospace", Font.PLAIN, 10));
		dlgTextArea.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if ((e.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0
						&& e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
					// CTRL+Enter: apply and close dialog
					textBuffer = new StringBuilder(dlgTextArea.getText());
					applyTextChooserToSelected();
					commitText();
					textChooserDlg.setVisible(false);
					requestFocusInWindow();
				}
			}
		});
		dlgTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) { updateTextFromField(); }
			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) { updateTextFromField(); }
			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) { updateTextFromField(); }

			private void updateTextFromField() {
				textBuffer = new StringBuilder(dlgTextArea.getText());
				applyTextChooserToSelected();
			}
		});
		javax.swing.JScrollPane textScrollPane = new javax.swing.JScrollPane(dlgTextArea,
				javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		textScrollPane.setBackground(new Color(36, 36, 36));
		addRow.accept("Text:", textScrollPane);

		// Font family
		String[] sysfonts = java.awt.GraphicsEnvironment
				.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		dlgFontBox = new javax.swing.JComboBox<>(sysfonts);
		dlgFontBox.setBackground(new Color(50, 50, 50));
		dlgFontBox.setForeground(AppColors.TEXT);
		dlgFontBox.addActionListener(ev -> {
			if (suppressChooserSync) return;
			textFontName = (String) dlgFontBox.getSelectedItem();
			applyTextChooserToSelected();
		});
		addRow.accept("Schriftart:", dlgFontBox);

		// Size
		dlgSizeSpinner = new javax.swing.JSpinner(
				new javax.swing.SpinnerNumberModel(textFontSize, 6, 800, 1));
		((javax.swing.JSpinner.DefaultEditor) dlgSizeSpinner.getEditor())
				.getTextField().setForeground(AppColors.TEXT);
		((javax.swing.JSpinner.DefaultEditor) dlgSizeSpinner.getEditor())
				.getTextField().setBackground(new Color(50, 50, 50));
		dlgSizeSpinner.addChangeListener(ev -> {
			if (suppressChooserSync) return;
			textFontSize = (int) dlgSizeSpinner.getValue();
			applyTextChooserToSelected();
		});
		addRow.accept("Größe (px):", dlgSizeSpinner);

		// Bold + Italic
		dlgBoldCb   = new javax.swing.JCheckBox("Fett");
		dlgItalicCb = new javax.swing.JCheckBox("Kursiv");
		dlgBoldCb.setForeground(AppColors.TEXT);   dlgBoldCb.setBackground(new Color(36, 36, 36));
		dlgItalicCb.setForeground(AppColors.TEXT); dlgItalicCb.setBackground(new Color(36, 36, 36));
		dlgBoldCb.addActionListener(ev -> {
			if (suppressChooserSync) return;
			textBold = dlgBoldCb.isSelected(); applyTextChooserToSelected();
		});
		dlgItalicCb.addActionListener(ev -> {
			if (suppressChooserSync) return;
			textItalic = dlgItalicCb.isSelected(); applyTextChooserToSelected();
		});
		javax.swing.JPanel styleRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		styleRow.setBackground(new Color(36, 36, 36));
		styleRow.add(dlgBoldCb); styleRow.add(dlgItalicCb);
		addRow.accept("Stil:", styleRow);

		// Color
		dlgColorBtn = new javax.swing.JButton("   ");
		dlgColorBtn.setBackground(textColor);
		dlgColorBtn.setOpaque(true);
		dlgColorBtn.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(80, 80, 80)));
		dlgColorBtn.setToolTipText("Textfarbe wählen");
		dlgColorBtn.addActionListener(ev -> {
			Color c = javax.swing.JColorChooser.showDialog(textChooserDlg, "Textfarbe", textColor);
			if (c != null) { textColor = c; dlgColorBtn.setBackground(c); applyTextChooserToSelected(); }
			// Bring focus back to canvas after color chooser
			javax.swing.SwingUtilities.invokeLater(CanvasPanel.this::requestFocusInWindow);
		});
		addRow.accept("Farbe:", dlgColorBtn);

		textChooserDlg.add(form, java.awt.BorderLayout.CENTER);

		// Close button
		javax.swing.JPanel south = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 6));
		south.setBackground(new Color(36, 36, 36));
		javax.swing.JButton closeBtn = new javax.swing.JButton("Schließen");
		closeBtn.setBackground(new Color(55, 55, 55));
		closeBtn.setForeground(AppColors.TEXT);
		closeBtn.addActionListener(ev -> {
			textChooserDlg.setVisible(false);
			javax.swing.SwingUtilities.invokeLater(CanvasPanel.this::requestFocusInWindow);
		});
		south.add(closeBtn);
		textChooserDlg.add(south, java.awt.BorderLayout.SOUTH);

		textChooserDlg.pack();
		// Position dialog at top-right of main frame (don't overlap canvas)
		if (topFrame != null) {
			int dialogX = topFrame.getX() + topFrame.getWidth() - textChooserDlg.getWidth() - 10;
			int dialogY = topFrame.getY() + 30;
			textChooserDlg.setLocation(Math.max(0, dialogX), Math.max(0, dialogY));
		}
	}

	/** Pushes current text* field values into the dialog controls without firing listeners. */
	private void syncTextChooserFromFields() {
		if (textChooserDlg == null) return;
		suppressChooserSync = true;
		try {
			dlgTextArea.setText(textBuffer.toString());
			dlgFontBox.setSelectedItem(textFontName);
			dlgSizeSpinner.setValue(textFontSize);
			dlgBoldCb.setSelected(textBold);
			dlgItalicCb.setSelected(textItalic);
			dlgColorBtn.setBackground(textColor);
		} finally {
			suppressChooserSync = false;
		}
	}

	/**
	 * Loads a TEXT_LAYER element's settings into the text* fields and syncs the dialog.
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
		syncTextChooserFromFields();
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
	private void enterTextEditMode(Layer el) {
		commitText(); // flush any in-progress text first
		// Load font settings from element (el must be a TextLayer here)
		TextLayer tl = (TextLayer) el;
		textFontName = tl.fontName();
		textFontSize = tl.fontSize() > 0 ? tl.fontSize() : 24;
		textBold     = tl.fontBold();
		textItalic   = tl.fontItalic();
		textColor    = tl.fontColor();
		textBuffer   = new StringBuilder(tl.text());
		textBoundingBox = new Rectangle(el.x(), el.y(), el.width(), el.height());
		textMinBox      = new Rectangle(el.x(), el.y(), el.width(), el.height());
		textDrawingBox  = false;
		editingTextElementId   = el.id();
		editingOriginalElement = el;
		// Remove from active elements while editing (prevents duplication with the preview)
		callbacks.setSelectedElement(null);
		callbacks.getActiveElements().removeIf(e -> e.id() == el.id());
		syncTextChooserFromFields();
		ensureTextChooserVisible();
		// Set focus to text area so cursor is visible and user can type immediately
		if (dlgTextArea != null) {
			dlgTextArea.requestFocus();
			// Position caret at end of text (don't select all, to avoid accidental deletion)
			dlgTextArea.setCaretPosition(dlgTextArea.getText().length());
		}
		repaint();
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
		System.err.println("[DEBUG] commitText: textBoundingBox=" + textBoundingBox + ", textBuffer.length()=" + textBuffer.length());
		if (textBoundingBox == null) {
			System.err.println("[DEBUG] commitText: RETURNING (no textBoundingBox)");
			return;
		}
		if (textBuffer.length() == 0) {
			// Nothing typed: if editing, restore original; otherwise just clear
			System.err.println("[DEBUG] commitText: Empty text buffer, restoring original element if exists");
			if (editingOriginalElement != null) {
				callbacks.getActiveElements().add(editingOriginalElement);
			}
			textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
			textBuffer.setLength(0);
			editingTextElementId = -1; editingOriginalElement = null;
			repaint();
			return;
		}
		System.err.println("[DEBUG] commitText: Calling commitTextLayer with text='" + textBuffer.toString() + "'");
		callbacks.commitTextLayer(
				editingTextElementId,
				textBuffer.toString(),
				textFontName, textFontSize, textBold, textItalic, textColor,
				textBoundingBox.x, textBoundingBox.y);
		textBoundingBox = null; textDrawingBox = false; textDragStart = null; textMinBox = null;
		textBuffer.setLength(0);
		editingTextElementId = -1; editingOriginalElement = null;
		repaint();
	}

	/** Returns the topmost layer at screen point, or null. */
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
			if (callbacks.elemRectScreen(els.get(i)).contains(screenPt)) return els.get(i);
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
		// Bei hohem Zoom (Pixel-Level): NEAREST_NEIGHBOR für schnelleres Rendering
		// Bei normalem Zoom: BICUBIC für glattes Rendering
		if (zoom > 2.0) {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		} else {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		}
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		int cw = (int) Math.round(callbacks.getWorkingImage().getWidth() * zoom);
		int ch = (int) Math.round(callbacks.getWorkingImage().getHeight() * zoom);

		// ── Draw checkerboard background ──────────────────────────────────────
		int cellSize = 16;
		for (int y = 0; y < ch; y += cellSize) {
			for (int x = 0; x < cw; x += cellSize) {
				boolean even = ((x / cellSize) + (y / cellSize)) % 2 == 0;
				g2.setColor(even ? callbacks.getCanvasBg1() : callbacks.getCanvasBg2());
				g2.fillRect(x, y, Math.min(cellSize, cw - x), Math.min(cellSize, ch - y));
			}
		}

		g2.drawImage(callbacks.getWorkingImage(), 0, 0, cw, ch, null);

		// ── Canvas sub-mode draw overlay (in-progress stroke preview) ────────
		if (canvasDrawOverlay != null) {
			g2.drawImage(canvasDrawOverlay, 0, 0, cw, ch, null);
		}

		// ── Element layers (non-destructive, rendered above base canvas) ──────
		java.util.List<Layer> activeEls   = callbacks.getActiveElements();
		java.util.List<Layer> selectedEls = callbacks.getSelectedElements();
		Layer primaryEl   = callbacks.getSelectedElement();
		boolean showOutlines = callbacks.isShowAllLayerOutlines();
		float[] selDash = { 5f, 3f };
		float[] dimDash = { 3f, 3f };

		// Draw all non-primary elements first; primary draws last (on top)
		for (int pass = 0; pass < 2; pass++) {
			for (Layer el : activeEls) {
				boolean isPrimary = primaryEl != null && primaryEl.id() == el.id();
				if (pass == 0 && isPrimary) continue;  // skip primary on first pass
				if (pass == 1 && !isPrimary) continue; // skip others on second pass

				Rectangle sr = callbacks.elemRectScreen(el);

				// For PathLayer the frame must be derived from the actual point
				// extents, not from the stored pl.x/pl.y/pl.width/pl.height.
				// pl.x() is the layer origin; points can have negative coordinates
				// (to the left/above the origin), so elemRectScreen gives the wrong rect.
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
					g2.drawImage(il.image(), sr.x, sr.y, sr.width, sr.height, null);
				} else if (el instanceof TextLayer tl) {
					// TextLayer: render glyphs live — integer pt size for metric consistency with TextLayer.of()
					int tstyle = (tl.fontBold() ? Font.BOLD : 0) | (tl.fontItalic() ? Font.ITALIC : 0);
					int screenFontSz = Math.max(1, (int) Math.round(tl.fontSize() * callbacks.getZoom()));
					Font  tfont  = new Font(tl.fontName(), tstyle, screenFontSz);
					g2.setFont(tfont);
					g2.setColor(tl.fontColor());
					java.awt.FontMetrics tfm = g2.getFontMetrics();
					String[] tLines = tl.text().split("\n", -1);
					int tpx = sr.x + (int)(TEXT_PADDING * callbacks.getZoom());
					int tpy = sr.y + (int)(TEXT_PADDING * callbacks.getZoom());
					for (int li = 0; li < tLines.length; li++) {
						g2.drawString(tLines[li], tpx, tpy + tfm.getHeight() * li + tfm.getAscent());
					}
				} else if (el instanceof PathLayer pl) {
					// PathLayer: render path lines and control points
					renderPathLayer(g2, pl, sr, callbacks.getZoom(), isPrimary);
				}

				boolean isHov = el.id() == hoveredElementId;
				boolean isSel = selectedEls.stream().anyMatch(s -> s.id() == el.id());
				if (isSel) {
					g2.setColor(AppColors.ACCENT);
					g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, selDash, 0f));
					g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
					if (isPrimary) {
						g2.setStroke(new BasicStroke(1f));
						for (Rectangle hr : callbacks.handleRects(frameRect)) {
							g2.setColor(Color.WHITE);
							g2.fillRect(hr.x, hr.y, hr.width, hr.height);
							g2.setColor(AppColors.ACCENT);
							g2.drawRect(hr.x, hr.y, hr.width, hr.height);
						}
					}
				} else if (isHov) {
					// Hover outline: brighter, solid
					g2.setColor(new Color(255, 200, 80, 200));
					g2.setStroke(new BasicStroke(1.5f));
					g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
				} else if (showOutlines) {
					// Dim outline for unselected layers when toggle is on
					g2.setColor(new Color(160, 160, 160, 140));
					g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dimDash, 0f));
					g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height);
				}
			}
		}

		// ── Text tool bounding-box preview (rubber-band draw phase + typing phase) ─
		if (textBoundingBox != null) {
			double z = callbacks.getZoom();
			int    tstyle = (textBold ? Font.BOLD : 0) | (textItalic ? Font.ITALIC : 0);
			int    screenSz = Math.max(1, (int) Math.round(Math.max(6, textFontSize) * z));
			Font   tfont  = new Font(textFontName, tstyle, screenSz);
			g2.setFont(tfont);
			java.awt.FontMetrics fm = g2.getFontMetrics();
			int lineH = fm.getHeight();

			// Compute natural text size (in screen pixels)
			String[] tLines = textDrawingBox ? new String[]{""}
			                                 : (textBuffer.toString() + "|").split("\n", -1);
			int textW = TEXT_PADDING * 2;
			int textH = TEXT_PADDING * 2 + lineH * Math.max(1, tLines.length);
			for (String l : tLines) textW = Math.max(textW, fm.stringWidth(l) + TEXT_PADDING * 2);

			// Minimum frame is what the user drew (in screen pixels)
			int minW = textMinBox != null ? (int)(textMinBox.width  * z) : (int)(textBoundingBox.width  * z);
			int minH = textMinBox != null ? (int)(textMinBox.height * z) : (int)(textBoundingBox.height * z);

			// Box expands to fit text, never smaller than user-drawn frame
			int boxW = Math.max(minW, textW);
			int boxH = Math.max(minH, textH);

			int sx = (int)(textBoundingBox.x * z);
			int sy = (int)(textBoundingBox.y * z);

			// Update textBoundingBox image-space size so commitText() uses correct dimensions
			if (!textDrawingBox) {
				textBoundingBox.width  = (int)Math.ceil(boxW / z);
				textBoundingBox.height = (int)Math.ceil(boxH / z);
			}

			// Background
			g2.setColor(new Color(255, 255, 255, 20));
			g2.fillRect(sx, sy, boxW, boxH);
			// Dashed border (accent if typing, white if drawing the box)
			float[] boxDash = { 5f, 3f };
			g2.setColor(textDrawingBox ? new Color(180, 180, 255) : AppColors.ACCENT);
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, boxDash, 0f));
			g2.drawRect(sx, sy, boxW, boxH);
			g2.setStroke(new BasicStroke(1f));

			// Draw text (skip during rubber-band draw phase)
			if (!textDrawingBox) {
				g2.setColor(textColor);
				for (int i = 0; i < tLines.length; i++) {
					g2.drawString(tLines[i],
					              sx + TEXT_PADDING,
					              sy + TEXT_PADDING + lineH * i + fm.getAscent());
				}
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
			System.err.println("[DEBUG PAINT] Drawing selection: imgCoords=(" + x + "," + y + "," + w + "," + h
					+ "), zoom=" + callbacks.getZoom());

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

		// Render optional image (if present)
		if (pl.image() != null) {
			g2.drawImage(pl.image(), sr.x, sr.y, sr.width, sr.height, null);
		}

		int pointRadius = 8;  // Pixel radius for point rendering

		// ── Draw lines connecting points ──
		// sr is in screen space (image coords * zoom); point coords are image-space,
		// so multiply by zoom before adding to sr to get correct screen position.
		g2.setColor(new Color(0, 200, 255, 180));  // Cyan
		g2.setStroke(new BasicStroke(1.5f));
		for (int i = 0; i < points.size() - 1; i++) {
			Point3D p1 = points.get(i);
			Point3D p2 = points.get(i + 1);
			int x1 = sr.x + (int) Math.round(p1.x * zoom);
			int y1 = sr.y + (int) Math.round(p1.y * zoom);
			int x2 = sr.x + (int) Math.round(p2.x * zoom);
			int y2 = sr.y + (int) Math.round(p2.y * zoom);
			g2.drawLine(x1, y1, x2, y2);
		}

		// Close polygon if needed
		if (pl.isClosed() && points.size() >= 2) {
			Point3D p1 = points.get(points.size() - 1);
			Point3D p2 = points.get(0);
			int x1 = sr.x + (int) Math.round(p1.x * zoom);
			int y1 = sr.y + (int) Math.round(p1.y * zoom);
			int x2 = sr.x + (int) Math.round(p2.x * zoom);
			int y2 = sr.y + (int) Math.round(p2.y * zoom);
			g2.drawLine(x1, y1, x2, y2);
		}

		// ── Draw point markers ──
		for (int i = 0; i < points.size(); i++) {
			Point3D p = points.get(i);
			int px = sr.x + (int) Math.round(p.x * zoom);
			int py = sr.y + (int) Math.round(p.y * zoom);

			boolean isHovered = isPrimary && (i == hoveredPathPointIndex);
			boolean isSelected = isPrimary && (i == selectedPathPointIndex);

			// Point size varies based on state
			int drawRadius = pointRadius;
			if (isSelected) drawRadius = pointRadius + 4;
			else if (isHovered) drawRadius = pointRadius + 2;

			// Point fill color
			Color fillColor;
			if (isSelected) fillColor = new Color(255, 200, 0);      // Yellow = selected
			else if (isHovered) fillColor = new Color(255, 100, 200);// Pink = hovered
			else fillColor = Color.WHITE;                             // White = normal

			g2.setColor(fillColor);
			g2.fillOval(px - drawRadius, py - drawRadius, drawRadius * 2, drawRadius * 2);

			// Point border
			Color borderColor;
			if (isSelected) borderColor = new Color(255, 140, 0);    // Orange border = selected
			else if (isHovered) borderColor = new Color(255, 80, 150);// Darker pink = hovered
			else borderColor = new Color(0, 150, 200);               // Cyan border = normal

			g2.setColor(borderColor);
			g2.setStroke(isSelected || isHovered ? new BasicStroke(2f) : new BasicStroke(1.5f));
			g2.drawOval(px - drawRadius, py - drawRadius, drawRadius * 2, drawRadius * 2);

			// Point index label
			g2.setColor(Color.BLACK);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			g2.drawString(String.valueOf(i), px + drawRadius + 3, py - 2);
		}
	}

}
