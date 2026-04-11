package paint;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

/**
 * A non-destructive layer placed on the canvas.
 *
 * Two kinds are distinguished via {@link ElementType}:
 *
 *  IMAGE_LAYER – a rasterised pixel region.  {@code image} holds the pixel data.
 *                Text fields are null / zero / false.
 *
 *  TEXT_LAYER  – live text rendered on-the-fly.  {@code image} is null.
 *                Scaling adjusts {@code fontSize} (not pixels).
 *
 * {@code width} / {@code height} are the natural image-space size:
 *   • IMAGE_LAYER: current rendered size (may differ from image.getWidth/Height after resize)
 *   • TEXT_LAYER : size measured from font metrics at the current fontSize
 *
 * Use the static factory methods {@link #ofImage} and {@link #ofText} to construct instances.
 */
public record Element(
    int           id,
    ElementType   type,
    BufferedImage image,      // IMAGE_LAYER only; null for TEXT_LAYER
    int           x,
    int           y,
    int           width,
    int           height,
    // ── TEXT_LAYER fields (ignored / null for IMAGE_LAYER) ──────────────────
    String        text,
    String        fontName,
    int           fontSize,
    boolean       fontBold,
    boolean       fontItalic,
    Color         fontColor
) {

    // =========================================================================
    // Factory methods
    // =========================================================================

    /** Creates an IMAGE_LAYER element. */
    public static Element ofImage(int id, BufferedImage img, int x, int y, int w, int h) {
        return new Element(id, ElementType.IMAGE_LAYER, img, x, y,
                Math.max(1, w), Math.max(1, h),
                null, null, 0, false, false, null);
    }

    /**
     * Creates a TEXT_LAYER element.
     * {@code width} and {@code height} are computed from font metrics at the given {@code fontSize}.
     */
    public static Element ofText(int id, String text, String fontName, int fontSize,
                                  boolean bold, boolean italic, Color color, int x, int y) {
        int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        Font font = new Font(fontName != null ? fontName : "SansSerif", style, Math.max(6, fontSize));
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
        String[] lines = text != null ? text.split("\n", -1) : new String[]{""};
        int w = 1;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int h = Math.max(1, fm.getHeight() * lines.length);
        // Add TEXT_PADDING (4px each side)
        return new Element(id, ElementType.TEXT_LAYER, null, x, y,
                w + 8, h + 8,
                text != null ? text : "", fontName != null ? fontName : "SansSerif",
                Math.max(6, fontSize), bold, italic,
                color != null ? color : Color.BLACK);
    }

    // =========================================================================
    // Mutation helpers (return new instances)
    // =========================================================================

    /** Returns a copy of this element moved to a new position. */
    public Element withPosition(int nx, int ny) {
        return new Element(id, type, image, nx, ny, width, height,
                text, fontName, fontSize, fontBold, fontItalic, fontColor);
    }

    /**
     * Returns a copy with updated bounds.
     * <ul>
     *   <li>IMAGE_LAYER – changes pixel render size and position.</li>
     *   <li>TEXT_LAYER  – computes new {@code fontSize} proportionally to the scale change,
     *                      then recomputes {@code width}/{@code height} from font metrics.</li>
     * </ul>
     */
    public Element withBounds(int nx, int ny, int nw, int nh) {
        if (type == ElementType.TEXT_LAYER) {
            double scaleX = (double) nw / Math.max(1, width);
            double scaleY = (double) nh / Math.max(1, height);
            double scale  = Math.max(scaleX, scaleY);
            int newFontSize = Math.max(6, (int) Math.round(fontSize * scale));
            return ofText(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, nx, ny);
        }
        return ofImage(id, image, nx, ny, nw, nh);
    }

    /**
     * For TEXT_LAYER: returns a copy with updated text content and/or font settings.
     * Width/height are recomputed from the new font metrics.
     */
    public Element withText(String newText, String newFontName, int newFontSize,
                             boolean newBold, boolean newItalic, Color newColor) {
        if (type != ElementType.TEXT_LAYER) return this; // no-op for IMAGE_LAYER
        return ofText(id, newText, newFontName, newFontSize, newBold, newItalic, newColor, x, y);
    }

    /**
     * For TEXT_LAYER: returns a copy with only the font size changed.
     * Width/height are recomputed from the new font metrics.
     */
    public Element withFontSize(int newFontSize) {
        if (type != ElementType.TEXT_LAYER) return this;
        return ofText(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, x, y);
    }
}
