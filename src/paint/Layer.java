package paint;

/**
 * Abstract base class for all non-destructive canvas layers.
 *
 * Common state: id, position (x/y) and rendered size (width/height) in image-space.
 * Subclasses carry the type-specific payload (pixels or text).
 *
 * All mutations return NEW instances (value-object semantics).
 *
 * Concrete subclasses:
 *   {@link ImageLayer} – a rasterised pixel region
 *   {@link TextLayer}  – live text rendered on-the-fly from font settings
 */
public abstract class Layer {

    protected final int id;
    protected final int x;
    protected final int y;
    protected final int width;
    protected final int height;

    protected Layer(int id, int x, int y, int width, int height) {
        this.id     = id;
        this.x      = x;
        this.y      = y;
        this.width  = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public int id()     { return id; }
    public int x()      { return x; }
    public int y()      { return y; }
    public int width()  { return width; }
    public int height() { return height; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    /** Returns a copy moved to a new image-space position. */
    public abstract Layer withPosition(int nx, int ny);

    /**
     * Returns a copy with updated position and rendered size.
     * For {@link TextLayer} the font size is adjusted proportionally.
     */
    public abstract Layer withBounds(int nx, int ny, int nw, int nh);

    // ── Convenience ───────────────────────────────────────────────────────────

    /** Human-readable display name for the layer panel. */
    public abstract String displayName();
}
