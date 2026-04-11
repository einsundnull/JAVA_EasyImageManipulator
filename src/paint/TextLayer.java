package paint;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

/**
 * A non-destructive live-text layer.  Text is rendered on-the-fly from the
 * stored font settings; no pixel data is cached here.
 *
 * {@code width}/{@code height} (inherited from {@link Layer}) are computed
 * from font metrics at construction time and represent the natural image-space
 * bounding box of the rendered text.
 *
 * Scaling ({@link #withBounds}) adjusts {@code fontSize} proportionally and
 * then recomputes the bounding box, rather than stretching pixels.
 *
 * All mutations return new instances (value-object semantics).
 */
public final class TextLayer extends Layer {

    /** Extra padding (px each side) added around the text bounding box. */
    public static final int TEXT_PADDING = 4;

    private final String  text;
    private final String  fontName;
    private final int     fontSize;
    private final boolean fontBold;
    private final boolean fontItalic;
    private final Color   fontColor;

    // ── Private constructor – callers use the factory method ─────────────────

    private TextLayer(int id, String text, String fontName, int fontSize,
                      boolean fontBold, boolean fontItalic, Color fontColor,
                      int x, int y, int w, int h) {
        super(id, x, y, w, h);
        this.text      = text;
        this.fontName  = fontName;
        this.fontSize  = fontSize;
        this.fontBold  = fontBold;
        this.fontItalic = fontItalic;
        this.fontColor = fontColor;
    }

    // ── Factory method ────────────────────────────────────────────────────────

    /**
     * Creates a TextLayer, computing {@code width}/{@code height} from font metrics.
     *
     * @param id        unique layer id
     * @param text      content (may contain {@code \n})
     * @param fontName  font family name, or {@code null} for "SansSerif"
     * @param fontSize  point size (clamped to ≥ 6)
     * @param bold      bold style
     * @param italic    italic style
     * @param color     text colour, or {@code null} for {@link Color#BLACK}
     * @param x         image-space X position of the top-left corner
     * @param y         image-space Y position of the top-left corner
     */
    public static TextLayer of(int id, String text, String fontName, int fontSize,
                               boolean bold, boolean italic, Color color, int x, int y) {
        String  fn   = fontName != null ? fontName : "SansSerif";
        int     fs   = Math.max(6, fontSize);
        int     style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        Color   col  = color != null ? color : Color.BLACK;
        String  txt  = text  != null ? text  : "";

        Font font = new Font(fn, style, fs);
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = dummy.createGraphics().getFontMetrics(font);
        String[] lines = txt.split("\n", -1);
        int w = 1;
        for (String line : lines) w = Math.max(w, fm.stringWidth(line));
        int h = Math.max(1, fm.getHeight() * lines.length);
        // Add TEXT_PADDING on each side
        return new TextLayer(id, txt, fn, fs, bold, italic, col, x, y, w + TEXT_PADDING * 2, h + TEXT_PADDING * 2);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  text()      { return text;      }
    public String  fontName()  { return fontName;  }
    public int     fontSize()  { return fontSize;  }
    public boolean fontBold()  { return fontBold;  }
    public boolean fontItalic(){ return fontItalic; }
    public Color   fontColor() { return fontColor; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    @Override
    public TextLayer withPosition(int nx, int ny) {
        return new TextLayer(id, text, fontName, fontSize, fontBold, fontItalic, fontColor, nx, ny, width, height);
    }

    /**
     * Returns a copy with updated bounds.
     * The font size is scaled proportionally to the larger of the two scale factors,
     * then the bounding box is recomputed from the new font metrics.
     */
    @Override
    public TextLayer withBounds(int nx, int ny, int nw, int nh) {
        double scaleX = (double) nw / Math.max(1, width);
        double scaleY = (double) nh / Math.max(1, height);
        double scale  = Math.max(scaleX, scaleY);
        int newFontSize = Math.max(6, (int) Math.round(fontSize * scale));
        return of(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, nx, ny);
    }

    /**
     * Returns a copy with updated text content and/or font settings.
     * The bounding box is recomputed from the new font metrics.
     */
    public TextLayer withText(String newText, String newFontName, int newFontSize,
                              boolean newBold, boolean newItalic, Color newColor) {
        return of(id, newText, newFontName, newFontSize, newBold, newItalic, newColor, x, y);
    }

    /**
     * Returns a copy with only the font size changed.
     * The bounding box is recomputed from the new font metrics.
     */
    public TextLayer withFontSize(int newFontSize) {
        return of(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, x, y);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() { return "Text " + id; }
}
