package paint.copy;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Manages the floating selection state (cut/paste operand that moves/scales on canvas).
 * Encapsulates all floating selection fields and operations.
 */
public class FloatSelectionState {

    // Floating selection state
    private BufferedImage floatingImg = null;
    private Rectangle floatRect = null;
    private boolean isDraggingFloat = false;
    private Point floatDragAnchor = null;
    private int activeHandle = -1;
    private Rectangle scaleBaseRect = null;
    private Point scaleDragStart = null;

    // References
    private double zoom = 1.0;
    private BufferedImage workingImage = null;

    public FloatSelectionState() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Set a floating selection with the given image and position.
     */
    public void setFloat(BufferedImage img, Rectangle rect) {
        floatingImg = img;
        floatRect = rect;
    }

    /**
     * Clear all floating selection state.
     * CONSOLIDATES REDUNDANT 7-FIELD RESET PATTERNS.
     */
    public void clear() {
        floatingImg = null;
        floatRect = null;
        isDraggingFloat = false;
        floatDragAnchor = null;
        activeHandle = -1;
        scaleBaseRect = null;
        scaleDragStart = null;
    }

    /**
     * @return true if a float is currently active
     */
    public boolean isActive() {
        return floatingImg != null;
    }

    /**
     * @return the floating image, or null
     */
    public BufferedImage getImage() {
        return floatingImg;
    }

    /**
     * @return the bounding rectangle in image-space, or null
     */
    public Rectangle getRect() {
        return floatRect;
    }

    /**
     * Convert floatRect (image-space) to screen-space (using current zoom).
     */
    public Rectangle getRectScreen() {
        if (floatRect == null) return new Rectangle(0, 0, 0, 0);
        return new Rectangle(
            (int) Math.round(floatRect.x * zoom),
            (int) Math.round(floatRect.y * zoom),
            (int) Math.round(floatRect.width * zoom),
            (int) Math.round(floatRect.height * zoom));
    }

    /**
     * 8 handle hit-rects around the float (screen-space).
     * Order: TL=0, TC=1, TR=2, ML=3, MR=4, BL=5, BC=6, BR=7
     */
    public Rectangle[] getHandleRects() {
        Rectangle sr = getRectScreen();
        int x = sr.x, y = sr.y, w = sr.width, h = sr.height;
        int mx = x + w / 2, my = y + h / 2, rx = x + w, by = y + h;
        int hs = 4; // half-size → each handle is 8×8 px
        return new Rectangle[]{
            new Rectangle(x - hs, y - hs, hs * 2, hs * 2),      // 0 TL
            new Rectangle(mx - hs, y - hs, hs * 2, hs * 2),     // 1 TC
            new Rectangle(rx - hs, y - hs, hs * 2, hs * 2),     // 2 TR
            new Rectangle(x - hs, my - hs, hs * 2, hs * 2),     // 3 ML
            new Rectangle(rx - hs, my - hs, hs * 2, hs * 2),    // 4 MR
            new Rectangle(x - hs, by - hs, hs * 2, hs * 2),     // 5 BL
            new Rectangle(mx - hs, by - hs, hs * 2, hs * 2),    // 6 BC
            new Rectangle(rx - hs, by - hs, hs * 2, hs * 2),    // 7 BR
        };
    }

    /**
     * Test if a screen point hits a handle.
     * @return handle index (0-7) or -1 if no hit
     */
    public int hitHandle(Point screenPt) {
        if (floatRect == null) return -1;
        Rectangle[] handles = getHandleRects();
        for (int i = 0; i < handles.length; i++) {
            if (handles[i].contains(screenPt)) return i;
        }
        return -1;
    }

    /**
     * Start a drag/scale operation at a handle.
     * @param handle Index (0-7), or -1 for center drag
     * @param screenPt Starting point in screen coords
     */
    public void beginDrag(int handle, Point screenPt) {
        isDraggingFloat = true;
        activeHandle = handle;
        floatDragAnchor = screenPt;
        if (floatRect != null) {
            scaleBaseRect = new Rectangle(floatRect);
        }
        scaleDragStart = screenPt;
    }

    /**
     * Update the float position/size during a drag.
     * @param handle Handle being dragged (0-7, -1 for move)
     * @param current Current point in screen coords
     */
    public void updateDrag(int handle, Point current) {
        if (floatRect == null || scaleBaseRect == null) return;

        double dx = (current.x - scaleDragStart.x) / zoom;
        double dy = (current.y - scaleDragStart.y) / zoom;

        // Clamp to image bounds
        int imgW = workingImage != null ? workingImage.getWidth() : 0;
        int imgH = workingImage != null ? workingImage.getHeight() : 0;

        if (handle == -1) {
            // Center drag: move the float
            floatRect.x = (int) Math.max(0, Math.min(imgW - floatRect.width,
                    scaleBaseRect.x + dx));
            floatRect.y = (int) Math.max(0, Math.min(imgH - floatRect.height,
                    scaleBaseRect.y + dy));
        } else {
            // Handle drag: scale
            applyHandleScale(handle, dx, dy, imgW, imgH);
        }
    }

    /**
     * End the current drag operation.
     */
    public void endDrag() {
        isDraggingFloat = false;
        floatDragAnchor = null;
        activeHandle = -1;
        scaleBaseRect = null;
        scaleDragStart = null;
    }

    /**
     * @return true if currently dragging the float
     */
    public boolean isDragging() {
        return isDraggingFloat;
    }

    /**
     * @return the active handle index, or -1
     */
    public int getActiveHandle() {
        return activeHandle;
    }

    /**
     * @return the drag anchor point, or null
     */
    public Point getDragAnchor() {
        return floatDragAnchor;
    }

    // ─────────────────────────────────────────────────────────────
    // Private methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Apply scale transformation based on which handle is being dragged.
     * Corners scale proportionally; sides scale a single axis.
     */
    private void applyHandleScale(int handle, double dx, double dy, int imgW, int imgH) {
        int x = scaleBaseRect.x, y = scaleBaseRect.y;
        int w = scaleBaseRect.width, h = scaleBaseRect.height;

        switch (handle) {
            case 0: // TL
                x = (int) (x + dx); y = (int) (y + dy); w -= (int) dx; h -= (int) dy;
                break;
            case 1: // TC
                y = (int) (y + dy); h -= (int) dy;
                break;
            case 2: // TR
                y = (int) (y + dy); w += (int) dx; h -= (int) dy;
                break;
            case 3: // ML
                x = (int) (x + dx); w -= (int) dx;
                break;
            case 4: // MR
                w += (int) dx;
                break;
            case 5: // BL
                x = (int) (x + dx); w -= (int) dx; h += (int) dy;
                break;
            case 6: // BC
                h += (int) dy;
                break;
            case 7: // BR
                w += (int) dx; h += (int) dy;
                break;
        }

        // Clamp to minimum size and image bounds
        w = Math.max(1, w);
        h = Math.max(1, h);
        x = Math.max(0, Math.min(imgW - w, x));
        y = Math.max(0, Math.min(imgH - h, y));

        floatRect.x = x;
        floatRect.y = y;
        floatRect.width = w;
        floatRect.height = h;
    }

    // ─────────────────────────────────────────────────────────────
    // Setters for context
    // ─────────────────────────────────────────────────────────────

    public void setZoom(double z) {
        this.zoom = z;
    }

    public void setWorkingImage(BufferedImage img) {
        this.workingImage = img;
    }
}
