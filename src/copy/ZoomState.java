package paint.copy;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Manages zoom state and smooth zoom animation.
 * Encapsulates all zoom-related fields and operations.
 */
public class ZoomState {

    // Zoom state
    private double zoom = 1.0;
    private boolean userHasManuallyZoomed = false;

    // Smooth zoom animation
    private double zoomTarget = 1.0;
    private Point2D zoomImgPt = null;
    private Point zoomVpMouse = null;
    private Timer zoomTimer = null;

    // Configuration
    private double ZOOM_MIN = 0.05;
    private double ZOOM_MAX = 16.0;
    private double ZOOM_STEP = 0.10;
    private double ZOOM_FACTOR = 1.08;

    // UI references (weak, set by owner)
    private JPanel canvasWrapper;
    private JScrollPane scrollPane;
    private JLabel zoomLabel;
    private BufferedImage workingImage;

    public ZoomState() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Set zoom with smooth animation, keeping anchor point fixed.
     * @param newZoom Target zoom level
     * @param anchorCanvas Point in canvas coords to keep under mouse (null = no anchor)
     */
    public void setZoom(double newZoom, Point anchorCanvas) {
        userHasManuallyZoomed = true;
        zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));

        // Capture anchor point only if a new gesture is starting (no animation running)
        if (zoomTimer == null) {
            if (anchorCanvas != null && scrollPane != null) {
                JViewport vp = scrollPane.getViewport();
                // image coord under cursor (using CURRENT zoom, before animation starts)
                zoomImgPt = new Point2D.Double(anchorCanvas.x / zoom, anchorCanvas.y / zoom);
                // viewport-relative mouse position (stays fixed during animation)
                zoomVpMouse = SwingUtilities.convertPoint(canvasWrapper, anchorCanvas, vp);
            } else {
                zoomImgPt = null;
                zoomVpMouse = null;
            }
        }

        startZoomAnimation();
    }

    /**
     * Fit image to viewport, reset manual zoom flag.
     */
    public void fitToViewport() {
        if (workingImage == null || scrollPane == null) return;
        Dimension vd = scrollPane.getViewport().getSize();
        if (vd.width <= 0 || vd.height <= 0) {
            SwingUtilities.invokeLater(this::fitToViewport);
            return;
        }
        double nz = Math.min((double) vd.width / workingImage.getWidth(),
                             (double) vd.height / workingImage.getHeight() * 0.98);
        setZoomInstant(nz);
    }

    /**
     * Set zoom immediately without animation.
     * Used for image load/browse.
     */
    public void setZoomInstant(double nz) {
        userHasManuallyZoomed = false;
        if (zoomTimer != null) {
            zoomTimer.stop();
            zoomTimer = null;
        }
        zoom = zoomTarget = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, nz));
        zoomImgPt = null;
        zoomVpMouse = null;
        if (canvasWrapper != null) {
            canvasWrapper.revalidate();
            canvasWrapper.repaint();
        }
        updateZoomLabel();
        if (scrollPane != null) {
            SwingUtilities.invokeLater(() ->
                scrollPane.getViewport().setViewPosition(new Point(0, 0)));
        }
    }

    /**
     * Increment/decrement zoom by ZOOM_STEP.
     */
    public void adjustZoom(double delta) {
        setZoom(zoom + delta, null);
    }

    /**
     * Reset zoom to 1.0x.
     */
    public void reset() {
        setZoomInstant(1.0);
    }

    /**
     * Zoom in by one step (typically used by mouse wheel).
     */
    public void zoomIn() {
        adjustZoom(ZOOM_STEP);
    }

    /**
     * Zoom out by one step (typically used by mouse wheel).
     */
    public void zoomOut() {
        adjustZoom(-ZOOM_STEP);
    }

    /**
     * Apply current zoom to the UI (called every animation frame).
     * Updates layout and viewport position to keep anchor fixed.
     */
    public void applyZoomFrame() {
        if (canvasWrapper == null) return;

        // Synchronous layout pass
        canvasWrapper.revalidate();

        // Adjust viewport so the anchor image pixel stays under the mouse
        if (zoomImgPt != null && zoomVpMouse != null && scrollPane != null && workingImage != null) {
            JViewport vp = scrollPane.getViewport();
            Dimension vs = vp.getViewSize();
            Dimension vpSz = vp.getSize();
            int cx = canvasWrapper.getComponent(0).getX();  // canvasPanel's X within wrapper
            int cy = canvasWrapper.getComponent(0).getY();
            int newCanvasX = (int)(zoomImgPt.getX() * zoom);
            int newCanvasY = (int)(zoomImgPt.getY() * zoom);
            int vx = cx + newCanvasX - zoomVpMouse.x;
            int vy = cy + newCanvasY - zoomVpMouse.y;
            int maxVx = Math.max(0, vs.width - vpSz.width);
            int maxVy = Math.max(0, vs.height - vpSz.height);
            vp.setViewPosition(new Point(Math.max(0, Math.min(vx, maxVx)),
                                         Math.max(0, Math.min(vy, maxVy))));
        }

        canvasWrapper.repaint();
        updateZoomLabel();
    }

    // ─────────────────────────────────────────────────────────────
    // Private methods
    // ─────────────────────────────────────────────────────────────

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(Math.round(zoom * 100) + "%");
        }
    }

    /**
     * Start or restart the zoom animation timer.
     * Each tick, zoom approaches zoomTarget using exponential decay.
     */
    private void startZoomAnimation() {
        if (zoomTimer != null) {
            zoomTimer.stop();
            zoomTimer = null;
        }

        final int INTERVAL_MS = 16;  // ~60 FPS
        final double FACTOR = 0.30;  // 30% per tick

        zoomTimer = new Timer(INTERVAL_MS, e -> {
            double diff = zoomTarget - zoom;
            boolean done = Math.abs(diff) < 0.0005;
            if (done) {
                zoom = zoomTarget;
                if (zoomTimer != null) {
                    zoomTimer.stop();
                    zoomTimer = null;
                }
            } else {
                zoom += diff * FACTOR;
            }
            applyZoomFrame();
            // After animation ends with no anchor: reset viewport to (0,0)
            if (done && zoomImgPt == null && scrollPane != null) {
                SwingUtilities.invokeLater(() ->
                    scrollPane.getViewport().setViewPosition(new Point(0, 0)));
            }
        });
        zoomTimer.setInitialDelay(0);
        zoomTimer.start();
    }

    // ─────────────────────────────────────────────────────────────
    // Getters & Setters
    // ─────────────────────────────────────────────────────────────

    public double getZoom() {
        return zoom;
    }

    public double getZoomTarget() {
        return zoomTarget;
    }

    public boolean isUserManuallyZoomed() {
        return userHasManuallyZoomed;
    }

    public void setUserManuallyZoomed(boolean b) {
        userHasManuallyZoomed = b;
    }

    public double getZoomMin() {
        return ZOOM_MIN;
    }

    public void setZoomMin(double min) {
        ZOOM_MIN = min;
    }

    public double getZoomMax() {
        return ZOOM_MAX;
    }

    public void setZoomMax(double max) {
        ZOOM_MAX = max;
    }

    public double getZoomStep() {
        return ZOOM_STEP;
    }

    public void setZoomStep(double step) {
        ZOOM_STEP = step;
    }

    public double getZoomFactor() {
        return ZOOM_FACTOR;
    }

    public void setZoomFactor(double factor) {
        ZOOM_FACTOR = factor;
    }

    public void setCanvasWrapper(JPanel wrapper) {
        this.canvasWrapper = wrapper;
    }

    public void setScrollPane(JScrollPane pane) {
        this.scrollPane = pane;
    }

    public void setZoomLabel(JLabel label) {
        this.zoomLabel = label;
    }

    public void setWorkingImage(BufferedImage img) {
        this.workingImage = img;
    }

    public boolean isAnimating() {
        return zoomTimer != null;
    }

    public void stopAnimation() {
        if (zoomTimer != null) {
            zoomTimer.stop();
            zoomTimer = null;
        }
    }
}
