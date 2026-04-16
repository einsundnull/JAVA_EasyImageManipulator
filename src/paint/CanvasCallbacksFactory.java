package paint;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Factory for CanvasCallbacks instances, one per canvas index. Extracted from
 * SelectiveAlphaEditor.buildCanvasCallbacks().
 */
class CanvasCallbacksFactory {

	static CanvasCallbacks build(SelectiveAlphaEditor ed, int idx) {
		return new CanvasCallbacks() {
			private CanvasInstance c() {
				return ed.ci(idx);
			}

			// ── Image & state ──
			@Override
			public BufferedImage getWorkingImage() {
				return c().workingImage;
			}

			@Override
			public AppMode getAppMode() {
				return c().appMode;
			}

			@Override
			public boolean isFloodfillMode() {
				return ed.floodfillMode;
			}

			@Override
			public boolean isAlphaPaintMode() {
				return ed.alphaPaintMode;
			}

			@Override
			public boolean isGridVisible() {
				return c().showGrid;
			}

			@Override
			public double getZoom() {
				return c().zoom;
			}

			@Override
			public void setZoom(double nz, Point anchor) {
				c().userHasManuallyZoomed = true;
				c().zoomTarget = Math.max(ed.ZOOM_MIN, Math.min(ed.ZOOM_MAX, nz));
				if (c().zoomTimer == null) {
					if (anchor != null && c().scrollPane != null) {
						javax.swing.JViewport vp = c().scrollPane.getViewport();
						c().zoomImgPt = new Point2D.Double(anchor.x / c().zoom, anchor.y / c().zoom);
						c().zoomVpMouse = SwingUtilities.convertPoint(c().canvasPanel, anchor, vp);
					} else {
						c().zoomImgPt = null;
						c().zoomVpMouse = null;
					}
				}
				ed.startZoomAnimation(idx);
			}

			@Override
			public JScrollPane getScrollPane() {
				return c().scrollPane;
			}

			@Override
			public Point screenToImage(Point screenPt) {
				int ix = (int) Math.floor(screenPt.x / c().zoom);
				int iy = (int) Math.floor(screenPt.y / c().zoom);
				if (c().workingImage != null) {
					ix = Math.max(0, Math.min(c().workingImage.getWidth() - 1, ix));
					iy = Math.max(0, Math.min(c().workingImage.getHeight() - 1, iy));
				}
				return new Point(ix, iy);
			}

			// ── Selection ──
			@Override
			public List<Rectangle> getSelectedAreas() {
				return c().selectedAreas;
			}

			@Override
			public boolean isSelecting() {
				return c().isSelecting;
			}

			@Override
			public void setSelecting(boolean s) {
				c().isSelecting = s;
			}

			@Override
			public Point getSelectionStart() {
				return c().selectionStart;
			}

			@Override
			public void setSelectionStart(Point p) {
				c().selectionStart = p;
			}

			@Override
			public Point getSelectionEnd() {
				return c().selectionEnd;
			}

			@Override
			public void setSelectionEnd(Point p) {
				c().selectionEnd = p;
			}

			// ── Layers ──
			@Override
			public List<Layer> getActiveElements() {
				return c().activeElements;
			}

			@Override
			public Layer getSelectedElement() {
				return c().selectedElements.isEmpty() ? null : c().selectedElements.iterator().next();
			}

			@Override
			public void setSelectedElement(Layer el) {
				c().selectedElements.clear();
				if (el != null)
					c().selectedElements.add(el);
			}

			@Override
			public List<Layer> getSelectedElements() {
				return new ArrayList<>(c().selectedElements);
			}

			@Override
			public void setSelectedElements(List<Layer> els) {
				c().selectedElements.clear();
				c().selectedElements.addAll(els);
			}

			@Override
			public void toggleElementSelection(Layer el) {
				if (c().selectedElements.contains(el))
					c().selectedElements.remove(el);
				else
					c().selectedElements.add(el);
			}

			@Override
			public void moveSelectedElements(int dx, int dy) {
				List<Layer> sel = c().selectedElements;
				List<Layer> active = c().activeElements;
				for (int i = 0; i < sel.size(); i++) {
					Layer oldLayer = sel.get(i);
					Layer newLayer = oldLayer.withPosition(oldLayer.x() + dx, oldLayer.y() + dy);
					sel.set(i, newLayer);
					for (int j = 0; j < active.size(); j++) {
						if (active.get(j).id() == oldLayer.id()) {
							active.set(j, newLayer);
							break;
						}
					}
				}
			}

			@Override
			public int getNextElementId() {
				return c().nextElementId++;
			}

			@Override
			public void addElement(Layer el) {
				c().activeElements.add(el);
			}

			// ── Float ──
			@Override
			public BufferedImage getFloatingImage() {
				return c().floatingImg;
			}

			@Override
			public Rectangle getFloatRect() {
				return c().floatRect;
			}

			@Override
			public boolean isDraggingFloat() {
				return c().isDraggingFloat;
			}

			@Override
			public void setDraggingFloat(boolean d) {
				c().isDraggingFloat = d;
			}

			@Override
			public Point getFloatDragAnchor() {
				return c().floatDragAnchor;
			}

			@Override
			public void setFloatDragAnchor(Point p) {
				c().floatDragAnchor = p;
			}

			@Override
			public int getActiveHandle() {
				return c().activeHandle;
			}

			@Override
			public void setActiveHandle(int h) {
				c().activeHandle = h;
			}

			@Override
			public Rectangle getScaleBaseRect() {
				return c().scaleBaseRect;
			}

			@Override
			public void setScaleBaseRect(Rectangle r) {
				c().scaleBaseRect = r;
			}

			@Override
			public Point getScaleDragStart() {
				return c().scaleDragStart;
			}

			@Override
			public void setScaleDragStart(Point p) {
				c().scaleDragStart = p;
			}

			// ── Paint ──
			@Override
			public Point getLastPaintPoint() {
				return c().lastPaintPoint;
			}

			@Override
			public void setLastPaintPoint(Point p) {
				c().lastPaintPoint = p;
			}

			@Override
			public Point getShapeStartPoint() {
				return c().shapeStartPoint;
			}

			@Override
			public void setShapeStartPoint(Point p) {
				c().shapeStartPoint = p;
			}

			@Override
			public BufferedImage getPaintSnapshot() {
				return c().paintSnapshot;
			}

			@Override
			public void setPaintSnapshot(BufferedImage img) {
				c().paintSnapshot = img;
			}

			// ── Layer elem ──
			@Override
			public int getElemActiveHandle() {
				return c().elemActiveHandle;
			}

			@Override
			public void setElemActiveHandle(int h) {
				c().elemActiveHandle = h;
			}

			@Override
			public Rectangle getElemScaleBase() {
				return c().elemScaleBase;
			}

			@Override
			public void setElemScaleBase(Rectangle r) {
				c().elemScaleBase = r;
			}

			@Override
			public Point getElemScaleStart() {
				return c().elemScaleStart;
			}

			@Override
			public void setElemScaleStart(Point p) {
				c().elemScaleStart = p;
			}

			@Override
			public boolean isDraggingElement() {
				return c().draggingElement;
			}

			@Override
			public void setDraggingElement(boolean d) {
				c().draggingElement = d;
			}

			@Override
			public Point getElemDragAnchor() {
				return c().elemDragAnchor;
			}

			@Override
			public void setElemDragAnchor(Point p) {
				c().elemDragAnchor = p;
			}

			// ── Toolbar ──
			@Override
			public PaintToolbar getPaintToolbar() {
				return ed.paintToolbar;
			}

			@Override
			public boolean isShowAllLayerOutlines() {
				return false;
			}

			@Override
			public void commitTextAsElement(BufferedImage img, int x, int y) {
				if (img == null)
					return;
				CanvasInstance ci = c();
				Layer el = new ImageLayer(ci.nextElementId++, ed.deepCopy(img), x, y, img.getWidth(),
						img.getHeight());
				ci.activeElements.add(el);
				ci.selectedElements.clear();
				ci.selectedElements.add(el);
				ed.markDirty();
				ed.refreshElementPanel();
				if (ci.canvasPanel != null)
					ci.canvasPanel.repaint();
			}

			@Override
			public void commitTextLayer(int id, String text, String font, int size, boolean bold,
					boolean italic, Color col, int x, int y) {
				CanvasInstance ci = c();
				TextLayer updated = (id >= 0) ? TextLayer.of(id, text, font, size, bold, italic, col, x, y)
						: TextLayer.of(ci.nextElementId++, text, font, size, bold, italic, col, x, y);
				boolean found = false;
				for (int i = 0; i < ci.activeElements.size(); i++) {
					if (ci.activeElements.get(i).id() == updated.id()) {
						ci.activeElements.set(i, updated);
						found = true;
						break;
					}
				}
				if (!found)
					ci.activeElements.add(updated);
				ci.selectedElements.clear();
				ci.selectedElements.add(updated);

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

				ed.markDirty();
				ed.refreshElementPanel();
				if (ci.canvasPanel != null)
					ci.canvasPanel.repaint();
			}

			// ── Actions ──
			@Override
			public void pushUndo() {
				if (c().workingImage != null)
					c().undoStack.push(ed.deepCopy(c().workingImage));
			}

			@Override
			public void markDirty() {
				ed.markDirty(idx);
			}

			@Override
			public void performFloodfill(Point screenPt) {
			}

			@Override
			public void paintDot(Point imagePt) {
			}

			@Override
			public void commitFloat() {
			}

			@Override
			public void repaintCanvas() {
				if (c().canvasPanel != null)
					c().canvasPanel.repaint();
			}

			@Override
			public void rotateSelectedElements(double angleDeg) {
				ed.doRotate(angleDeg);
			}

			@Override
			public void onCanvasElementHover(int id) {
			}

			@Override
			public void onElementTransformed() {
				ed.refreshGalleryThumbnail(idx);
			}

			@Override
			public void clearSelection() {
				c().selectedAreas.clear();
				c().isSelecting = false;
			}

			@Override
			public void liftSelectionToFloat() {
			}

			@Override
			public boolean isCanvasSubMode() {
				return ed.canvasModeBtn.isSelected();
			}

			@Override
			public void liftSelectionToElement(Rectangle sel) {
				CanvasInstance ci = c();
				if (sel == null || ci.workingImage == null)
					return;
				BufferedImage src = PaintEngine.cropRegion(ci.workingImage, sel);
				if (src == null)
					return;
				Layer el = new ImageLayer(ci.nextElementId++, src, sel.x, sel.y, src.getWidth(),
						src.getHeight());
				ci.activeElements.add(el);
				ci.selectedElements.clear();
				ci.selectedElements.add(el);
				ci.selectedAreas.clear();
				ci.isSelecting = false;
				ed.refreshElementPanel();
				ed.markDirty();
				if (ci.canvasPanel != null)
					ci.canvasPanel.repaint();
			}

			@Override
			public void deleteSelection() {
			}

			@Override
			public void updateSelectedElement(Layer el) {
				if (el == null)
					return;
				CanvasInstance ci = c();
				for (int i = 0; i < ci.selectedElements.size(); i++) {
					if (ci.selectedElements.get(i).id() == el.id()) {
						ci.selectedElements.set(i, el);
						break;
					}
				}
				for (int i = 0; i < ci.activeElements.size(); i++) {
					if (ci.activeElements.get(i).id() == el.id()) {
						ci.activeElements.set(i, el);
						break;
					}
				}
			}

			@Override
			public void openImageLayerForEditing(Layer el) {
				ed.doOpenImageLayerInOtherCanvas(idx, el);
			}

			// ── Utilities ──
			@Override
			public int hitHandle(Point screenPt) {
				return ed.hitHandle(screenPt);
			}

			@Override
			public Rectangle floatRectScreen() {
				return ed.floatRectScreen();
			}

			@Override
			public Rectangle elemRectScreen(Layer el) {
				return ed.elemRectScreen(el, ed.ci(idx).zoom);
			}

			@Override
			public Rectangle[] handleRects(Rectangle r) {
				return ed.handleRects(r);
			}

			@Override
			public Point getRotationHandlePos(Rectangle sr) {
				return ed.getRotationHandlePos(sr);
			}

			@Override
			public Rectangle getRotationHandleRect(Rectangle sr) {
				return ed.getRotationHandleRect(sr);
			}

			@Override
			public Rectangle getActiveSelection() {
				return ed.getActiveSelection();
			}

			@Override
			public BufferedImage deepCopy(BufferedImage src) {
				return ed.deepCopy(src);
			}

			// ── Colors ──
			@Override
			public Color getCanvasBg1() {
				return ed.canvasBg1;
			}

			@Override
			public Color getCanvasBg2() {
				return ed.canvasBg2;
			}
		};
	}
}
