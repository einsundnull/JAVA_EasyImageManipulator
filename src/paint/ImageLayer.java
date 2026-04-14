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
public final class ImageLayer extends Layer {

    private final BufferedImage image;
    private final double rotationAngle;

    // Legacy constructor (for backward compatibility, rotationAngle defaults to 0)
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h) {
        this(id, image, x, y, w, h, 0.0);
    }

    // Full constructor with rotation angle
    public ImageLayer(int id, BufferedImage image, int x, int y, int w, int h, double rotationAngle) {
        super(id, x, y, Math.max(1, w), Math.max(1, h));
        this.image = image;
        this.rotationAngle = rotationAngle;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public BufferedImage image() { return image; }
    public double rotationAngle() { return rotationAngle; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    @Override
    public ImageLayer withPosition(int nx, int ny) {
        return new ImageLayer(id, image, nx, ny, width, height, rotationAngle);
    }

    @Override
    public ImageLayer withBounds(int nx, int ny, int nw, int nh) {
        return new ImageLayer(id, image, nx, ny, nw, nh, rotationAngle);
    }

    public ImageLayer withRotation(double newAngle) {
        return new ImageLayer(id, image, x, y, width, height, newAngle);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() { return "Image " + id; }
}
