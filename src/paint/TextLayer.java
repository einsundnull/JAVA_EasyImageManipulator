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
    private final boolean hidden;   // true = invisible (doesn't render)
    private final boolean wrapping; // true = word-wrap within bounds, font size fixed

    // ── Private constructor – callers use the factory method ─────────────────

    private TextLayer(int id, String text, String fontName, int fontSize,
                      boolean fontBold, boolean fontItalic, Color fontColor,
                      int x, int y, int w, int h, boolean hidden, boolean wrapping) {
        super(id, x, y, w, h);
        this.text       = text;
        this.fontName   = fontName;
        this.fontSize   = fontSize;
        this.fontBold   = fontBold;
        this.fontItalic = fontItalic;
        this.fontColor  = fontColor;
        this.hidden     = hidden;
        this.wrapping   = wrapping;
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
        return of(id, text, fontName, fontSize, bold, italic, color, x, y, false);
    }

    public static TextLayer of(int id, String text, String fontName, int fontSize,
                               boolean bold, boolean italic, Color color, int x, int y, boolean hidden) {
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
        return new TextLayer(id, txt, fn, fs, bold, italic, col, x, y, w + TEXT_PADDING * 2, h + TEXT_PADDING * 2, hidden, false);
    }

    /**
     * Creates a wrapping TextLayer with explicit bounds (content area).
     * Font size is fixed — text word-wraps within the given width.
     * {@link #withBounds} on a wrapping layer resizes the frame WITHOUT scaling the font.
     */
    public static TextLayer wrappingOf(int id, String text, String fontName, int fontSize,
                                       boolean bold, boolean italic, Color color,
                                       int x, int y, int w, int h) {
        String fn  = fontName != null ? fontName : "SansSerif";
        int    fs  = Math.max(6, fontSize);
        Color  col = color != null ? color : Color.BLACK;
        String txt = text  != null ? text  : "";
        return new TextLayer(id, txt, fn, fs, bold, italic, col, x, y,
                Math.max(TEXT_PADDING * 2 + 1, w),
                Math.max(TEXT_PADDING * 2 + 1, h),
                false, true);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  text()       { return text;      }
    public String  fontName()   { return fontName;  }
    public int     fontSize()   { return fontSize;  }
    public boolean fontBold()   { return fontBold;  }
    public boolean fontItalic() { return fontItalic; }
    public Color   fontColor()  { return fontColor; }
    public boolean isHidden()   { return hidden;    }
    public boolean isWrapping() { return wrapping;  }

    // ── Mutations (return new instances) ──────────────────────────────────────

    @Override
    public TextLayer withPosition(int nx, int ny) {
        return new TextLayer(id, text, fontName, fontSize, fontBold, fontItalic, fontColor, nx, ny, width, height, hidden, wrapping);
    }

    /**
     * Returns a copy with updated bounds.
     * <ul>
     *   <li>Normal layers: font size scales proportionally, bounding box recomputed.</li>
     *   <li>Wrapping layers: bounds resize, font size stays fixed (text reflows).</li>
     * </ul>
     */
    @Override
    public TextLayer withBounds(int nx, int ny, int nw, int nh) {
        if (wrapping) {
            return new TextLayer(id, text, fontName, fontSize, fontBold, fontItalic, fontColor,
                    nx, ny,
                    Math.max(TEXT_PADDING * 2 + 1, nw),
                    Math.max(TEXT_PADDING * 2 + 1, nh),
                    hidden, true);
        }
        double scaleX = (double) nw / Math.max(1, width);
        double scaleY = (double) nh / Math.max(1, height);
        double scale  = Math.max(scaleX, scaleY);
        int newFontSize = Math.max(6, (int) Math.round(fontSize * scale));
        return of(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, nx, ny, hidden);
    }

    /**
     * Returns a copy with updated text content and/or font settings.
     * For wrapping layers the bounding box is preserved; for normal layers it is recomputed.
     */
    public TextLayer withText(String newText, String newFontName, int newFontSize,
                              boolean newBold, boolean newItalic, Color newColor) {
        if (wrapping) {
            Color col = newColor != null ? newColor : Color.BLACK;
            String fn = newFontName != null ? newFontName : "SansSerif";
            return new TextLayer(id, newText != null ? newText : "", fn,
                    Math.max(6, newFontSize), newBold, newItalic, col,
                    x, y, width, height, hidden, true);
        }
        return of(id, newText, newFontName, newFontSize, newBold, newItalic, newColor, x, y, hidden);
    }

    /**
     * Returns a copy with only the font size changed.
     * For wrapping layers the bounding box is preserved; for normal layers it is recomputed.
     */
    public TextLayer withFontSize(int newFontSize) {
        if (wrapping) {
            return new TextLayer(id, text, fontName, Math.max(6, newFontSize),
                    fontBold, fontItalic, fontColor, x, y, width, height, hidden, true);
        }
        return of(id, text, fontName, newFontSize, fontBold, fontItalic, fontColor, x, y, hidden);
    }

    public TextLayer withHidden(boolean newHidden) {
        if (wrapping) {
            return new TextLayer(id, text, fontName, fontSize, fontBold, fontItalic, fontColor,
                    x, y, width, height, newHidden, true);
        }
        return of(id, text, fontName, fontSize, fontBold, fontItalic, fontColor, x, y, newHidden);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() { return "Text " + id; }
}
