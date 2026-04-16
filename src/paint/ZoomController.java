package paint;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Manages animated zoom and coordinate conversion for both canvases.
 * Extracted from SelectiveAlphaEditor.
 */
class ZoomController {

	private final SelectiveAlphaEditor ed;

	ZoomController(SelectiveAlphaEditor ed) {
		this.ed = ed;
	}

	// ── Zoom entry points ─────────────────────────────────────────────────────

	/**
	 * Set zoom level with smooth animation for the active canvas.
	 * If anchorCanvas != null, keeps that canvas point fixed under the mouse.
	 */
	void setZoom(double nz, Point anchorCanvas) {
		setZoom(nz, anchorCanvas, ed.activeCanvasIndex);
	}

	void setZoom(double nz, Point anchorCanvas, int idx) {
		CanvasInstance c = ed.ci(idx);
		c.userHasManuallyZoomed = true;
		c.zoomTarget = Math.max(ed.ZOOM_MIN, Math.min(ed.ZOOM_MAX, nz));

		if (c.zoomTimer == null) {
			if (anchorCanvas != null && c.scrollPane != null) {
				JViewport vp = c.scrollPane.getViewport();
				c.zoomImgPt = new Point2D.Double(anchorCanvas.x / c.zoom, anchorCanvas.y / c.zoom);
				c.zoomVpMouse = SwingUtilities.convertPoint(c.canvasPanel, anchorCanvas, vp);
			} else {
				c.zoomImgPt = null;
				c.zoomVpMouse = null;
			}
		}

		startZoomAnimation(idx);
	}

	// ── Animation ─────────────────────────────────────────────────────────────

	/**
	 * Start or restart the zoom animation timer for indexed canvas.
	 * Each tick, zoom approaches zoomTarget using exponential decay.
	 */
	void startZoomAnimation(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.zoomTimer != null) {
			c.zoomTimer.stop();
			c.zoomTimer = null;
		}

		final int INTERVAL_MS = 16;   // ~60 FPS
		final double FACTOR = 0.30;   // 30% per tick — snappy but not jarring

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
			if (done && c.zoomImgPt == null && c.scrollPane != null) {
				SwingUtilities.invokeLater(() -> c.scrollPane.getViewport().setViewPosition(new Point(0, 0)));
			}
		});
		c.zoomTimer.setInitialDelay(0);
		c.zoomTimer.start();
	}

	void startZoomAnimation() {
		startZoomAnimation(ed.activeCanvasIndex);
	}

	// ── Frame application ─────────────────────────────────────────────────────

	void applyZoomFrame(int idx) {
		CanvasInstance c = ed.ci(idx);
		if (c.canvasWrapper == null)
			return;

		c.canvasWrapper.revalidate();

		if (c.zoomImgPt != null && c.zoomVpMouse != null && c.scrollPane != null && c.workingImage != null) {
			JViewport vp = c.scrollPane.getViewport();
			Dimension vs = vp.getViewSize();
			Dimension vpSz = vp.getSize();
			int cx = c.canvasPanel.getX();
			int cy = c.canvasPanel.getY();
			int newCanvasX = (int) (c.zoomImgPt.getX() * c.zoom);
			int newCanvasY = (int) (c.zoomImgPt.getY() * c.zoom);
			int vx = cx + newCanvasX - c.zoomVpMouse.x;
			int vy = cy + newCanvasY - c.zoomVpMouse.y;
			int maxVx = Math.max(0, vs.width - vpSz.width);
			int maxVy = Math.max(0, vs.height - vpSz.height);
			vp.setViewPosition(new Point(Math.max(0, Math.min(vx, maxVx)), Math.max(0, Math.min(vy, maxVy))));
		}

		c.canvasWrapper.repaint();
	}

	// ── Zoom label ────────────────────────────────────────────────────────────

	void updateZoomLabel() {
		if (ed.zoomLabel != null)
			ed.zoomLabel.setText(Math.round(ed.ci().zoom * 100) + "%");
	}

	// ── Coordinate conversion ─────────────────────────────────────────────────

	/** Convert a point in canvasPanel-local coordinates to image-space. */
	Point screenToImage(Point sp) {
		CanvasInstance c = ed.ci();
		int ix = (int) Math.floor(sp.x / c.zoom);
		int iy = (int) Math.floor(sp.y / c.zoom);
		if (c.workingImage != null) {
			ix = Math.max(0, Math.min(c.workingImage.getWidth() - 1, ix));
			iy = Math.max(0, Math.min(c.workingImage.getHeight() - 1, iy));
		}
		return new Point(ix, iy);
	}
}
