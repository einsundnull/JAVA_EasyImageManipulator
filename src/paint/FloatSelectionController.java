package paint;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Manages floating-selection commit/cancel and the shared geometry helpers
 * (handle rects, hit-testing, rotation handle) used by CanvasPanel and painters.
 * Extracted from SelectiveAlphaEditor.
 */
class FloatSelectionController {

	private final SelectiveAlphaEditor ed;

	FloatSelectionController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Commit / cancel ───────────────────────────────────────────────────────

	/**
	 * Paste the floating image at its current (possibly scaled) rect and clear
	 * float state. In Paint mode: creates a non-destructive Element layer instead
	 * of writing to canvas.
	 */
	void commitFloat() {
		CanvasInstance c = ed.ci();
		if (c.floatingImg == null || c.floatRect == null)
			return;
		BufferedImage scaled = PaintEngine.scale(c.floatingImg, Math.max(1, c.floatRect.width),
				Math.max(1, c.floatRect.height));
		if (c.appMode == AppMode.PAINT) {
			Layer el = new ImageLayer(c.nextElementId++, scaled, c.floatRect.x, c.floatRect.y,
					c.floatRect.width, c.floatRect.height);
			c.activeElements.add(el);
			c.selectedElements.clear();
			c.selectedElements.add(el);
			ed.refreshElementPanel();
		} else {
			PaintEngine.pasteRegion(c.workingImage, scaled, new Point(c.floatRect.x, c.floatRect.y));
		}
		c.floatingImg   = null;
		c.floatRect     = null;
		c.isDraggingFloat = false;
		c.floatDragAnchor = null;
		c.activeHandle  = -1;
		c.scaleBaseRect = null;
		c.scaleDragStart = null;
		c.selectedAreas.clear();
		ed.markDirty();
		ed.refreshElementPanel();
	}

	/** Discard the float and undo to the state before it was lifted. */
	void cancelFloat() {
		CanvasInstance c = ed.ci();
		c.floatingImg   = null;
		c.floatRect     = null;
		c.isDraggingFloat = false;
		c.floatDragAnchor = null;
		c.activeHandle  = -1;
		c.scaleBaseRect = null;
		c.scaleDragStart = null;
		c.selectedAreas.clear();
		ed.doUndo();
	}

	// ── Geometry helpers ──────────────────────────────────────────────────────

	/** Convert floatRect (image-space) to canvasPanel screen-space. */
	Rectangle floatRectScreen() {
		CanvasInstance c = ed.ci();
		if (c.floatRect == null)
			return new Rectangle(0, 0, 0, 0);
		return new Rectangle(
				(int) Math.round(c.floatRect.x * c.zoom),
				(int) Math.round(c.floatRect.y * c.zoom),
				(int) Math.round(c.floatRect.width * c.zoom),
				(int) Math.round(c.floatRect.height * c.zoom));
	}

	/**
	 * 8 handle hit-rects around {@code sr} (screen-space).
	 * Order: TL=0, TC=1, TR=2, ML=3, MR=4, BL=5, BC=6, BR=7
	 */
	Rectangle[] handleRects(Rectangle sr) {
		int x = sr.x, y = sr.y, w = sr.width, h = sr.height;
		int mx = x + w / 2, my = y + h / 2, rx = x + w, by = y + h;
		int hs = 4;
		return new Rectangle[] {
			new Rectangle(x - hs,  y - hs,  hs * 2, hs * 2), // 0 TL
			new Rectangle(mx - hs, y - hs,  hs * 2, hs * 2), // 1 TC
			new Rectangle(rx - hs, y - hs,  hs * 2, hs * 2), // 2 TR
			new Rectangle(x - hs,  my - hs, hs * 2, hs * 2), // 3 ML
			new Rectangle(rx - hs, my - hs, hs * 2, hs * 2), // 4 MR
			new Rectangle(x - hs,  by - hs, hs * 2, hs * 2), // 5 BL
			new Rectangle(mx - hs, by - hs, hs * 2, hs * 2), // 6 BC
			new Rectangle(rx - hs, by - hs, hs * 2, hs * 2), // 7 BR
		};
	}

	/** Returns 0–7 if {@code pt} (canvasPanel coords) hits a float handle, else -1. */
	int hitHandle(Point pt) {
		CanvasInstance c = ed.ci();
		if (c.floatRect == null)
			return -1;
		Rectangle[] handles = handleRects(floatRectScreen());
		for (int i = 0; i < handles.length; i++)
			if (handles[i].contains(pt))
				return i;
		return -1;
	}

	/** Returns rotation handle position for a rect (30 px above center-top). */
	Point getRotationHandlePos(Rectangle sr) {
		return new Point(sr.x + sr.width / 2, sr.y - 30);
	}

	/** Returns 8×8 hit-rect around the rotation handle. */
	Rectangle getRotationHandleRect(Rectangle sr) {
		Point p = getRotationHandlePos(sr);
		int hs = 4;
		return new Rectangle(p.x - hs, p.y - hs, hs * 2, hs * 2);
	}
}
