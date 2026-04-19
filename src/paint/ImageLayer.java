package paint;

import java.awt.image.BufferedImage;

/**
 * A non-destructive pixel-region layer placed on the canvas.
 *
 * Carries a {@link BufferedImage} ({@code image}) that is rendered at the
 * position and size stored in the base class.  The pixel data is NOT resized
 * on the fly; {@code width}/{@code height} are the desired render dimensions
 * and may differ from {@code image.getWidth()/getHeight()} after the user
 * scales the layer.
 *
 * All mutations return new instances (value-object semantics).
 */
public class ImageLayer extends Layer {

    private final BufferedImage image;
    private final double rotationAngle;
    private final int opacity;  // 0-100, where 100 = fully opaque, 0 = fully transparent
    private final boolean hidden;          // true = invisible (doesn't render)
    private final boolean mouseTransparent; // true = invisible to mouse hit-testing

    // Legacy constructor (for backward compatibility)
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h) {
        this(id, image, x, y, w, h, 0.0, 100, false);
    }

    // Constructor with rotation angle
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h, double rotationAngle) {
        this(id, image, x, y, w, h, rotationAngle, 100, false);
    }

    // Constructor with rotation angle and opacity
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h, double rotationAngle, int opacity) {
        this(id, image, x, y, w, h, rotationAngle, opacity, false);
    }

    // Full constructor with rotation angle, opacity, and hidden flag
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h, double rotationAngle, int opacity, boolean hidden) {
        this(id, image, x, y, w, h, rotationAngle, opacity, hidden, false);
    }

    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h, double rotationAngle, int opacity, boolean hidden, boolean mouseTransparent) {
        super(id, x, y, Math.max(1, w), Math.max(1, h));
        this.image = image;
        this.rotationAngle = rotationAngle;
        this.opacity = Math.max(0, Math.min(100, opacity));
        this.hidden = hidden;
        this.mouseTransparent = mouseTransparent;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public BufferedImage image() { return image; }
    public double rotationAngle() { return rotationAngle; }
    public int opacity() { return opacity; }
    @Override public boolean isHidden() { return hidden; }
    @Override public boolean isMouseTransparent() { return mouseTransparent; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    @Override
    public ImageLayer withPosition(int nx, int ny) {
        return new ImageLayer(id, image, nx, ny, width, height, rotationAngle, opacity, hidden, mouseTransparent);
    }

    @Override
    public ImageLayer withBounds(int nx, int ny, int nw, int nh) {
        return new ImageLayer(id, image, nx, ny, nw, nh, rotationAngle, opacity, hidden, mouseTransparent);
    }

    public ImageLayer withRotation(double newAngle) {
        return new ImageLayer(id, image, x, y, width, height, newAngle, opacity, hidden, mouseTransparent);
    }

    public ImageLayer withOpacity(int newOpacity) {
        return new ImageLayer(id, image, x, y, width, height, rotationAngle, newOpacity, hidden, mouseTransparent);
    }

    public ImageLayer withHidden(boolean newHidden) {
        return new ImageLayer(id, image, x, y, width, height, rotationAngle, opacity, newHidden, mouseTransparent);
    }

    public ImageLayer withMouseTransparent(boolean mt) {
        return new ImageLayer(id, image, x, y, width, height, rotationAngle, opacity, hidden, mt);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() { return "Image " + id; }
}
