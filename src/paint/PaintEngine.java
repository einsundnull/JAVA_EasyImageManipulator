package paint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;

/**
 * Stateless drawing engine.
 * All methods operate on the supplied BufferedImage in IMAGE-space.
 * The caller is responsible for zoom-to-image coordinate conversion
 * before passing points here.
 */
public class PaintEngine {

    // ── Tool enum ─────────────────────────────────────────────────────────────
    public enum Tool {
        PENCIL, FLOODFILL, LINE, CIRCLE, RECT, ERASER, ERASER_BG, ERASER_COLOR,
        EYEDROPPER, SELECT, TEXT, PATH,
        FREE_PATH, WAND_I, WAND_II, WAND_III, WAND_IV,
        WAND_REPLACE_OUTER, WAND_REPLACE_INNER,
        WAND_AA_OUTER, WAND_AA_INNER,
        CUT_COLOR, CUT_UNTIL_COLOR, CUT_SAME_COLOR,
        SMEAR
    }

    /** Which color the Replace / AA wands write. */
    public enum WandColorSource { SECONDARY, CLICKED, SURROUNDING }

    // ── Fill mode enum ────────────────────────────────────────────────────────
    public enum FillMode {
        SOLID, OUTLINE_ONLY, GRADIENT
    }

    // ── Brush shape ───────────────────────────────────────────────────────────
    public enum BrushShape {
        ROUND, SQUARE
    }

    // Draw Pencil stroke
    public static void drawPencil(BufferedImage img, Point from, Point to,
                                   Color color, int strokeWidth, BrushShape shape, boolean aa) {
        Graphics2D g2 = img.createGraphics();
        applyQuality(g2, aa);
        g2.setColor(color);
        int r = strokeWidth / 2;
        if (shape == BrushShape.SQUARE) {
            // Axis-aligned square stamped along the interpolated path (MS-Paint style).
            // AA must be off for crisp edges — the brush shape is a pixel-grid square.
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int dx = to.x - from.x, dy = to.y - from.y;
            int steps = Math.max(Math.abs(dx), Math.abs(dy));
            if (steps == 0) {
                g2.fillRect(from.x - r, from.y - r, strokeWidth, strokeWidth);
            } else {
                for (int i = 0; i <= steps; i++) {
                    int px = from.x + dx * i / steps;
                    int py = from.y + dy * i / steps;
                    g2.fillRect(px - r, py - r, strokeWidth, strokeWidth);
                }
            }
        } else {
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (from.equals(to)) {
                g2.fillOval(from.x - r, from.y - r, strokeWidth, strokeWidth);
            } else {
                g2.drawLine(from.x, from.y, to.x, to.y);
            }
        }
        g2.dispose();
    }

    /**
     * Smear: pushes pixels from {@code from} toward {@code to}.
     * For each pixel in the brush circle at {@code to}, blends the corresponding
     * source pixel from {@code from} with the existing destination pixel.
     * Strength falls off softly from centre to edge.
     *
     * @param strength  0..1, how strongly the source pixel dominates (0.65 is a good default)
     */
    /**
     * Returns the raw int[] pixel array for TYPE_INT_ARGB/TYPE_INT_RGB images,
     * or null if the image type doesn't support direct array access.
     * Writing into the returned array immediately updates the image — no setRGB needed.
     */
    private static int[] rawPixels(BufferedImage img) {
        if (img.getType() != BufferedImage.TYPE_INT_ARGB
                && img.getType() != BufferedImage.TYPE_INT_RGB
                && img.getType() != BufferedImage.TYPE_INT_ARGB_PRE) return null;
        try {
            return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void smear(BufferedImage img, Point from, Point to, int strokeWidth, float strength) {
        if (from == null || img == null) return;
        int w = img.getWidth(), h = img.getHeight();
        int r = Math.max(1, strokeWidth / 2);
        int dx = to.x - from.x, dy = to.y - from.y;
        int[] px = rawPixels(img);
        if (px == null) { smearFallback(img, from, to, strokeWidth, strength); return; }

        for (int oy = -r; oy <= r; oy++) {
            for (int ox = -r; ox <= r; ox++) {
                if (ox * ox + oy * oy > r * r) continue;
                int tx = to.x + ox, ty = to.y + oy;
                int fx = tx - dx,   fy = ty - dy;
                if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue;

                int srcARGB = px[fy * w + fx];
                int dstARGB = px[ty * w + tx];

                float dist  = (float) Math.sqrt(ox * ox + oy * oy) / r;
                float blend = strength * (1f - dist * 0.5f);
                float inv   = 1f - blend;

                int sA = (srcARGB >> 24) & 0xFF, sR = (srcARGB >> 16) & 0xFF,
                    sG = (srcARGB >>  8) & 0xFF, sB =  srcARGB        & 0xFF;
                int dA = (dstARGB >> 24) & 0xFF, dR = (dstARGB >> 16) & 0xFF,
                    dG = (dstARGB >>  8) & 0xFF, dB =  dstARGB        & 0xFF;

                int nA = Math.min(255, (int)(sA * blend + dA * inv));
                int nR = Math.min(255, (int)(sR * blend + dR * inv));
                int nG = Math.min(255, (int)(sG * blend + dG * inv));
                int nB = Math.min(255, (int)(sB * blend + dB * inv));

                px[ty * w + tx] = (nA << 24) | (nR << 16) | (nG << 8) | nB;
            }
        }
    }

    private static void smearFallback(BufferedImage img, Point from, Point to, int strokeWidth, float strength) {
        int w = img.getWidth(), h = img.getHeight();
        int r = Math.max(1, strokeWidth / 2);
        int dx = to.x - from.x, dy = to.y - from.y;
        for (int oy = -r; oy <= r; oy++) {
            for (int ox = -r; ox <= r; ox++) {
                if (ox * ox + oy * oy > r * r) continue;
                int tx = to.x + ox, ty = to.y + oy;
                int fx = tx - dx,   fy = ty - dy;
                if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue;
                int srcARGB = img.getRGB(fx, fy), dstARGB = img.getRGB(tx, ty);
                float dist = (float) Math.sqrt(ox * ox + oy * oy) / r;
                float blend = strength * (1f - dist * 0.5f), inv = 1f - blend;
                int sA = (srcARGB >> 24) & 0xFF, sR = (srcARGB >> 16) & 0xFF,
                    sG = (srcARGB >>  8) & 0xFF, sB =  srcARGB        & 0xFF;
                int dA = (dstARGB >> 24) & 0xFF, dR = (dstARGB >> 16) & 0xFF,
                    dG = (dstARGB >>  8) & 0xFF, dB =  dstARGB        & 0xFF;
                img.setRGB(tx, ty,
                    (Math.min(255,(int)(sA*blend+dA*inv)) << 24) |
                    (Math.min(255,(int)(sR*blend+dR*inv)) << 16) |
                    (Math.min(255,(int)(sG*blend+dG*inv)) <<  8) |
                     Math.min(255,(int)(sB*blend+dB*inv)));
            }
        }
    }

    // Draw Eraser stroke
    public static void drawEraser(BufferedImage img, Point from, Point to, int strokeWidth, boolean aa) {
        Graphics2D g2 = img.createGraphics();
        applyQuality(g2, aa);
        g2.setComposite(AlphaComposite.Clear);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (from.equals(to)) {
            int r = strokeWidth / 2;
            g2.fillOval(from.x - r, from.y - r, strokeWidth, strokeWidth);
        } else {
            g2.drawLine(from.x, from.y, to.x, to.y);
        }
        g2.dispose();
    }

    /**
     * Eraser BG: paints with {@code bgColor} (opaque solid), same brush shape as the normal eraser.
     * Used when "erase to secondary color" is selected.
     */
    public static void drawEraserBG(BufferedImage img, Point from, Point to,
                                     Color bgColor, int strokeWidth, boolean aa) {
        drawPencil(img, from, to, bgColor, strokeWidth, BrushShape.ROUND, aa);
    }

    /**
     * Color eraser (MS-Paint style): for every pixel under the round brush that matches
     * {@code targetColor} within {@code tolerance}, replace it with {@code replacement}.
     * Pixels that don't match are left untouched.
     */
    public static void drawColorEraser(BufferedImage img, Point from, Point to,
                                        Color targetColor, Color replacement,
                                        int strokeWidth, int tolerance, boolean aa) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = rawPixels(img);
        int targetARGB = targetColor.getRGB();
        int replARGB   = replacement.getRGB();
        int r = Math.max(1, strokeWidth / 2);

        // For each point along the segment, stamp a circle
        int dx = to.x - from.x, dy = to.y - from.y;
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        for (int s = 0; s <= steps; s++) {
            int cx = from.x + dx * s / steps;
            int cy = from.y + dy * s / steps;
            for (int y = cy - r; y <= cy + r; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (x < 0 || y < 0 || x >= w || y >= h) continue;
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r) continue;
                    int cur = px != null ? px[y * w + x] : img.getRGB(x, y);
                    if (colorsMatch(cur, targetARGB, tolerance * 255 / 100)) {
                        if (px != null) px[y * w + x] = replARGB;
                        else img.setRGB(x, y, replARGB);
                    }
                }
            }
        }
    }

    // Draw Line
    public static void drawLine(BufferedImage img, Point from, Point to,
                                 Color color, int strokeWidth, boolean aa) {
        Graphics2D g2 = img.createGraphics();
        applyQuality(g2, aa);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(from.x, from.y, to.x, to.y);
        g2.dispose();
    }

    // Draw Circle / Ellipse
    public static void drawCircle(BufferedImage img, Point from, Point to,
                                   Color color, int strokeWidth, FillMode fillMode,
                                   Color color2, boolean aa) {
        int[] bbox = boundingBox(from, to);
        if (bbox == null) return;
        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];

        Graphics2D g2 = img.createGraphics();
        applyQuality(g2, aa);
        Ellipse2D shape = new Ellipse2D.Float(x, y, w, h);

        switch (fillMode) {
            case SOLID -> { g2.setColor(color); g2.fill(shape); }
            case GRADIENT -> {
                GradientPaint gp = new GradientPaint(x, y, color, x + w, y + h,
                        color2 != null ? color2 : color.darker());
                g2.setPaint(gp);
                g2.fill(shape);
            }
            case OUTLINE_ONLY -> {}
        }
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.draw(shape);
        g2.dispose();
    }

    // Draw Rectangle
    public static void drawRect(BufferedImage img, Point from, Point to,
                                 Color color, int strokeWidth, FillMode fillMode,
                                 Color color2, boolean aa) {
        int[] bbox = boundingBox(from, to);
        if (bbox == null) return;
        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];

        Graphics2D g2 = img.createGraphics();
        applyQuality(g2, aa);

        switch (fillMode) {
            case SOLID -> { g2.setColor(color); g2.fillRect(x, y, w, h); }
            case GRADIENT -> {
                GradientPaint gp = new GradientPaint(x, y, color, x + w, y + h,
                        color2 != null ? color2 : color.darker());
                g2.setPaint(gp);
                g2.fillRect(x, y, w, h);
            }
            case OUTLINE_ONLY -> {}
        }
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.drawRect(x, y, w, h);
        g2.dispose();
    }

    // Floodfill
    public static void floodFill(BufferedImage img, int x, int y, Color fillColor, int tolerance) {
        int w = img.getWidth(), h = img.getHeight();
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        int[] px = rawPixels(img);
        int targetARGB = px != null ? px[y * w + x] : img.getRGB(x, y);
        int fillARGB   = fillColor.getRGB();
        if (targetARGB == fillARGB) return;

        boolean[] visited = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.push(y * w + x);
        while (!queue.isEmpty()) {
            int coord = queue.pop();
            if (visited[coord]) continue;
            int px_ = coord % w, py_ = coord / w;
            int cur = px != null ? px[coord] : img.getRGB(px_, py_);
            if (!colorsMatch(cur, targetARGB, tolerance)) continue;
            visited[coord] = true;
            if (px != null) px[coord] = fillARGB; else img.setRGB(px_, py_, fillARGB);
            if (px_ + 1 < w) queue.push(coord + 1);
            if (px_ - 1 >= 0) queue.push(coord - 1);
            if (py_ + 1 < h) queue.push(coord + w);
            if (py_ - 1 >= 0) queue.push(coord - w);
        }
    }

    // Eyedropper
    public static Color pickColor(BufferedImage img, int x, int y) {
        if (x < 0 || x >= img.getWidth() || y < 0 || y >= img.getHeight()) return Color.BLACK;
        return new Color(img.getRGB(x, y), true);
    }

    // Cut / Copy / Paste
    public static BufferedImage cropRegion(BufferedImage img, Rectangle r) {
        int x = Math.max(0, r.x),  y = Math.max(0, r.y);
        int w = Math.min(r.width,  img.getWidth()  - x);
        int h = Math.min(r.height, img.getHeight() - y);
        if (w <= 0 || h <= 0) return null;
        // Deep copy – getSubimage() shares the raster, so clearing the source
        // would also wipe the cropped image if we returned the raw subimage.
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        g2.drawImage(img.getSubimage(x, y, w, h), 0, 0, null);
        g2.dispose();
        return copy;
    }

    public static void clearRegion(BufferedImage img, Rectangle r) {
        Graphics2D g2 = img.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(r.x, r.y, r.width, r.height);
        g2.dispose();
    }

    public static void pasteRegion(BufferedImage dst, BufferedImage src, Point at) {
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.SrcOver);
        g2.drawImage(src, at.x, at.y, null);
        g2.dispose();
    }

    /**
     * Returns a full-canvas-size copy with the selection area punched out (transparent).
     * Used for "copy/cut outside selection" operations.
     */
    public static BufferedImage cropOutside(BufferedImage img, Rectangle sel) {
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.setComposite(AlphaComposite.Clear);
        int x = Math.max(0, sel.x), y = Math.max(0, sel.y);
        int w = Math.min(sel.width,  img.getWidth()  - x);
        int h = Math.min(sel.height, img.getHeight() - y);
        if (w > 0 && h > 0) g2.fillRect(x, y, w, h);
        g2.dispose();
        return result;
    }

    /**
     * Clears everything OUTSIDE the selection rectangle (makes it transparent).
     */
    public static void clearOutside(BufferedImage img, Rectangle sel) {
        Graphics2D g2 = img.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        int x = Math.max(0, sel.x), y = Math.max(0, sel.y);
        int w = Math.min(sel.width,  img.getWidth()  - x);
        int h = Math.min(sel.height, img.getHeight() - y);
        // Clear top strip
        if (y > 0)                        g2.fillRect(0, 0, img.getWidth(), y);
        // Clear bottom strip
        if (y + h < img.getHeight())      g2.fillRect(0, y + h, img.getWidth(), img.getHeight() - y - h);
        // Clear left strip (within sel row band)
        if (x > 0)                        g2.fillRect(0, y, x, h);
        // Clear right strip (within sel row band)
        if (x + w < img.getWidth())       g2.fillRect(x + w, y, img.getWidth() - x - w, h);
        g2.dispose();
    }

    // ── Region-space transforms (in-place) ───────────────────────────────────

    /** Flip the pixels inside rectangle r horizontally, in-place. */
    public static void flipHorizontalInRegion(BufferedImage img, Rectangle r) {
        BufferedImage region = cropRegion(img, r);
        BufferedImage flipped = flipHorizontal(region);
        clearRegion(img, r);
        pasteRegion(img, flipped, new Point(r.x, r.y));
    }

    /** Flip the pixels inside rectangle r vertically, in-place. */
    public static void flipVerticalInRegion(BufferedImage img, Rectangle r) {
        BufferedImage region = cropRegion(img, r);
        BufferedImage flipped = flipVertical(region);
        clearRegion(img, r);
        pasteRegion(img, flipped, new Point(r.x, r.y));
    }

    /** Rotate the pixels inside r by angleDeg degrees; result is scaled back to fit r. */
    public static void rotateInRegion(BufferedImage img, Rectangle r, double angleDeg) {
        BufferedImage region = cropRegion(img, r);
        BufferedImage rotated = rotate(region, angleDeg);
        BufferedImage scaled  = scale(rotated, Math.max(1, r.width), Math.max(1, r.height));
        clearRegion(img, r);
        pasteRegion(img, scaled, new Point(r.x, r.y));
    }

    /**
     * Scale the pixels inside r to nw×nh, clearing the original area first.
     * @return new selection rectangle (same origin, new size)
     */
    public static Rectangle scaleInRegion(BufferedImage img, Rectangle r, int nw, int nh) {
        nw = Math.max(1, nw); nh = Math.max(1, nh);
        BufferedImage region = cropRegion(img, r);
        BufferedImage scaled  = scale(region, nw, nh);
        clearRegion(img, r);
        pasteRegion(img, scaled, new Point(r.x, r.y));
        return new Rectangle(r.x, r.y, nw, nh);
    }

    // Transformations
    public static BufferedImage flipHorizontal(BufferedImage img) { return flip(img, true); }
    public static BufferedImage flipVertical(BufferedImage img)   { return flip(img, false); }

    private static BufferedImage flip(BufferedImage img, boolean horizontal) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage r = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = r.createGraphics();
        if (horizontal) g2.drawImage(img, w, 0, -w, h, null);
        else            g2.drawImage(img, 0, h,  w, -h, null);
        g2.dispose();
        return r;
    }

    /** Rotate by angleDeg degrees clockwise around centre. Canvas resizes to fit. */
    public static BufferedImage rotate(BufferedImage img, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double sin = Math.abs(Math.sin(rad)), cos = Math.abs(Math.cos(rad));
        int w = img.getWidth(), h = img.getHeight();
        int nw = (int) Math.ceil(w * cos + h * sin);
        int nh = (int) Math.ceil(h * cos + w * sin);
        BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.translate(nw / 2.0, nh / 2.0);
        g2.rotate(rad);
        g2.translate(-w / 2.0, -h / 2.0);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return result;
    }

    /** Scale to exact pixel dimensions. */
    public static BufferedImage scale(BufferedImage img, int newW, int newH) {
        if (newW <= 0 || newH <= 0) return img;
        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(img, 0, 0, newW, newH, null);
        g2.dispose();
        return result;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Polygon operations (for PathLayer fill/clear operations)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Clears all pixels inside the polygon (sets alpha to 0).
     * xs/ys are image-space coordinates.
     */
    public static void clearPolygon(BufferedImage img, int[] xs, int[] ys) {
        if (img == null || xs == null || ys == null || xs.length < 3) return;
        Graphics2D g2 = img.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillPolygon(xs, ys, xs.length);
        g2.dispose();
    }

    /**
     * Clears all pixels OUTSIDE the polygon (inside → opaque, outside → alpha 0).
     */
    public static void clearOutsidePolygon(BufferedImage img, int[] xs, int[] ys) {
        if (img == null || xs == null || ys == null || xs.length < 3) return;
        Graphics2D g2 = img.createGraphics();
        Area fullArea = new Area(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
        Area polyArea = new Area(new Polygon(xs, ys, xs.length));
        fullArea.subtract(polyArea);
        g2.setComposite(AlphaComposite.Clear);
        g2.fill(fullArea);
        g2.dispose();
    }

    /**
     * Returns a new ARGB BufferedImage containing pixels inside the polygon.
     * Bounding box is computed from min/max x,y values.
     */
    public static BufferedImage cropPolygon(BufferedImage img, int[] xs, int[] ys) {
        if (img == null || xs == null || ys == null || xs.length < 3) return null;

        int minX = xs[0], maxX = xs[0];
        int minY = ys[0], maxY = ys[0];
        for (int v : xs) { minX = Math.min(minX, v); maxX = Math.max(maxX, v); }
        for (int v : ys) { minY = Math.min(minY, v); maxY = Math.max(maxY, v); }

        int w = Math.max(1, maxX - minX + 1);
        int h = Math.max(1, maxY - minY + 1);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = out.createGraphics();
        int[] relXs = xs.clone();
        int[] relYs = ys.clone();
        for (int i = 0; i < relXs.length; i++) {
            relXs[i] -= minX;
            relYs[i] -= minY;
        }
        g2.setClip(new Polygon(relXs, relYs, relXs.length));
        g2.drawImage(img, -minX, -minY, null);
        g2.dispose();
        return out;
    }

    /**
     * Returns a new ARGB BufferedImage containing pixels OUTSIDE the polygon.
     */
    public static BufferedImage cropOutsidePolygon(BufferedImage img, int[] xs, int[] ys) {
        if (img == null || xs == null || ys == null || xs.length < 3) return null;

        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.setComposite(AlphaComposite.Clear);
        g2.fillPolygon(xs, ys, xs.length);
        g2.dispose();
        return out;
    }

    // ── Magic Wand helpers ────────────────────────────────────────────────────

    /**
     * Flood-fill region gathering (Wand I): collects all pixels reachable from
     * (x,y) whose color matches the start pixel within tolerance.
     * Returns a boolean[width][height] mask of the filled region.
     */
    public static boolean[][] floodFillRegion(BufferedImage img, int x, int y, int tolerance) {
        int w = img.getWidth(), h = img.getHeight();
        if (x < 0 || x >= w || y < 0 || y >= h) return new boolean[w][h];
        int[] px = rawPixels(img);
        int targetARGB = px != null ? px[y * w + x] : img.getRGB(x, y);
        boolean[] flat = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.push(y * w + x);
        while (!queue.isEmpty()) {
            int coord = queue.pop();
            if (flat[coord]) continue;
            int cx = coord % w, cy = coord / w;
            int cur = px != null ? px[coord] : img.getRGB(cx, cy);
            if (!colorsMatch(cur, targetARGB, tolerance)) continue;
            flat[coord] = true;
            if (cx + 1 < w) queue.push(coord + 1);
            if (cx - 1 >= 0) queue.push(coord - 1);
            if (cy + 1 < h) queue.push(coord + w);
            if (cy - 1 >= 0) queue.push(coord - w);
        }
        // Convert flat row-major mask to [x][y] for callers that expect it
        boolean[][] region = new boolean[w][h];
        for (int i = 0; i < flat.length; i++) {
            if (flat[i]) region[i % w][i / w] = true;
        }
        return region;
    }

    /**
     * Flood-fill region gathering (Wand II): collects all pixels reachable from
     * (x,y), stopping when a pixel matches stopColor within stopTolerance.
     * Returns a boolean[width][height] mask of the filled region.
     */
    public static boolean[][] floodFillRegionUntilColor(BufferedImage img, int x, int y,
            Color stopColor, int stopTolerance) {
        int w = img.getWidth(), h = img.getHeight();
        if (x < 0 || x >= w || y < 0 || y >= h) return new boolean[w][h];
        int[] px = rawPixels(img);
        int stopARGB  = stopColor.getRGB();
        int startARGB = px != null ? px[y * w + x] : img.getRGB(x, y);
        if (colorsMatch(startARGB, stopARGB, stopTolerance)) return new boolean[w][h];
        boolean[] flat = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.push(y * w + x);
        while (!queue.isEmpty()) {
            int coord = queue.pop();
            if (flat[coord]) continue;
            int cx = coord % w, cy = coord / w;
            int rgb = px != null ? px[coord] : img.getRGB(cx, cy);
            if (colorsMatch(rgb, stopARGB, stopTolerance)) continue;
            flat[coord] = true;
            if (cx + 1 < w) queue.push(coord + 1);
            if (cx - 1 >= 0) queue.push(coord - 1);
            if (cy + 1 < h) queue.push(coord + w);
            if (cy - 1 >= 0) queue.push(coord - w);
        }
        boolean[][] region = new boolean[w][h];
        for (int i = 0; i < flat.length; i++) {
            if (flat[i]) region[i % w][i / w] = true;
        }
        return region;
    }

    /**
     * Computes an N-pixel-wide band along the boundary of a region mask.
     *
     * <p>outer=true  → band = pixels OUTSIDE region, within {@code width} steps of the region.<br>
     * outer=false → band = pixels INSIDE region,  within {@code width} steps of the complement.</p>
     *
     * <p>closed=true  → 8-connected morphology (Chebyshev distance): watertight ring,
     *                    guaranteed to block 4-connected flood-fill on every side.<br>
     * closed=false → 4-connected morphology (Manhattan distance): may have diagonal
     *                    "overlap" gaps of {@code width} pixels.</p>
     */
    public static boolean[][] boundaryBand(boolean[][] region, int w, int h,
            int width, boolean outer, boolean closed) {
        boolean[][] band = new boolean[w][h];
        if (region == null || width <= 0) return band;
        int W = Math.max(1, width);

        int[][] dirs = closed
                ? new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}
                : new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        int[][] dist = new int[w][h];
        for (int[] r : dist) java.util.Arrays.fill(r, Integer.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        // Seed: pixels on the target side that touch the opposite side (distance 1).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean in = inRegion(region, x, y, w, h);
                boolean target = outer ? !in : in;
                if (!target) continue;
                boolean onBoundary = false;
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean nIn;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        // Image edge: for inner bands, the missing neighbor is treated
                        // as "outside", so region-pixels at the edge become boundary seeds.
                        nIn = false;
                    } else {
                        nIn = inRegion(region, nx, ny, w, h);
                    }
                    boolean nOpposite = outer ? nIn : !nIn;
                    if (nOpposite) { onBoundary = true; break; }
                }
                if (onBoundary) {
                    dist[x][y] = 1;
                    band[x][y] = true;
                    queue.add(new int[]{x, y});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];
            int d = dist[px][py];
            if (d >= W) continue;
            for (int[] dir : dirs) {
                int nx = px + dir[0], ny = py + dir[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                boolean nIn = inRegion(region, nx, ny, w, h);
                boolean nTarget = outer ? !nIn : nIn;
                if (!nTarget) continue;
                if (dist[nx][ny] <= d + 1) continue;
                dist[nx][ny] = d + 1;
                band[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return band;
    }

    /**
     * Computes the distance field for pixels in the N-pixel boundary band.
     * Returns int[w][h] where dist[x][y] in 1..width means "pixel is in the band,
     * d steps from the opposite side"; 0 means "not in band".
     */
    public static int[][] boundaryDistanceField(boolean[][] region, int w, int h,
            int width, boolean outer, boolean closed) {
        int[][] dist = new int[w][h];
        if (region == null || width <= 0) return dist;
        int W = Math.max(1, width);

        int[][] dirs = closed
                ? new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}
                : new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        int[][] work = new int[w][h];
        for (int[] r : work) java.util.Arrays.fill(r, Integer.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean in = inRegion(region, x, y, w, h);
                boolean target = outer ? !in : in;
                if (!target) continue;
                boolean onBoundary = false;
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean nIn;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) nIn = false;
                    else nIn = inRegion(region, nx, ny, w, h);
                    boolean nOpposite = outer ? nIn : !nIn;
                    if (nOpposite) { onBoundary = true; break; }
                }
                if (onBoundary) {
                    work[x][y] = 1;
                    dist[x][y] = 1;
                    queue.add(new int[]{x, y});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];
            int d = work[px][py];
            if (d >= W) continue;
            for (int[] dir : dirs) {
                int nx = px + dir[0], ny = py + dir[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                boolean nIn = inRegion(region, nx, ny, w, h);
                boolean nTarget = outer ? !nIn : nIn;
                if (!nTarget) continue;
                if (work[nx][ny] <= d + 1) continue;
                work[nx][ny] = d + 1;
                dist[nx][ny] = d + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /**
     * Wand REPLACE_OUTER: flood-fill region from click, then paint an N-pixel band
     * OUTSIDE the region (along the outer boundary) with {@code replacement}.
     */
    public static void replaceOuter(BufferedImage img, int x, int y, Color replacement,
            int tolerance, int bandWidth, boolean closed) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] region = floodFillRegion(img, x, y, tolerance);
        boolean[][] band   = boundaryBand(region, w, h, bandWidth, true, closed);
        fillMaskARGB(img, band, replacement.getRGB());
    }

    /**
     * Wand REPLACE_INNER: flood-fill region from click, then paint an N-pixel band
     * INSIDE the region (along the inner boundary) with {@code replacement}.
     */
    public static void replaceInner(BufferedImage img, int x, int y, Color replacement,
            int tolerance, int bandWidth, boolean closed) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] region = floodFillRegion(img, x, y, tolerance);
        boolean[][] band   = boundaryBand(region, w, h, bandWidth, false, closed);
        fillMaskARGB(img, band, replacement.getRGB());
    }

    /**
     * Wand AA_OUTER: flood-fill region, then blend an N-pixel AA band OUTSIDE the
     * region with the given color. Alpha fades linearly from opaque at distance=1
     * (touching the boundary) to transparent at distance=width+1.
     */
    public static void antiAliasOuter(BufferedImage img, int x, int y, Color blendColor,
            int tolerance, int bandWidth, boolean closed) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] region = floodFillRegion(img, x, y, tolerance);
        int[][] dist = boundaryDistanceField(region, w, h, bandWidth, true, closed);
        blendDistanceField(img, dist, bandWidth, blendColor);
    }

    /**
     * Wand AA_INNER: flood-fill region, then blend an N-pixel AA band INSIDE the
     * region with the given color. Alpha fades from opaque at the boundary to
     * transparent at depth=width+1.
     */
    public static void antiAliasInner(BufferedImage img, int x, int y, Color blendColor,
            int tolerance, int bandWidth, boolean closed) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] region = floodFillRegion(img, x, y, tolerance);
        int[][] dist = boundaryDistanceField(region, w, h, bandWidth, false, closed);
        blendDistanceField(img, dist, bandWidth, blendColor);
    }

    /**
     * Average color of all pixels in {@code mask}.  Returns {@code fallback} if mask is empty.
     */
    public static Color averageMaskColor(BufferedImage img, boolean[][] mask, Color fallback) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = rawPixels(img);
        long sumR = 0, sumG = 0, sumB = 0, n = 0;
        for (int x = 0; x < w && x < mask.length; x++) {
            int mh = Math.min(h, mask[x].length);
            for (int y = 0; y < mh; y++) {
                if (!mask[x][y]) continue;
                int rgb = px != null ? px[y * w + x] : img.getRGB(x, y);
                int a = (rgb >>> 24) & 0xFF;
                if (a == 0) continue;
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >>  8) & 0xFF;
                sumB +=  rgb        & 0xFF;
                n++;
            }
        }
        if (n == 0) return fallback;
        return new Color((int)(sumR / n), (int)(sumG / n), (int)(sumB / n));
    }

    /** Returns the RGB color of a single pixel, or {@code fallback} if out of bounds. */
    public static Color pixelColor(BufferedImage img, int x, int y, Color fallback) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return fallback;
        int rgb = img.getRGB(x, y);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static void blendDistanceField(BufferedImage img, int[][] dist, int width, Color blend) {
        int w = img.getWidth(), h = img.getHeight();
        int W = Math.max(1, width);
        int br = blend.getRed(), bg = blend.getGreen(), bb = blend.getBlue();
        int[] px = rawPixels(img);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w && x < dist.length; x++) {
                if (y >= dist[x].length) continue;
                int d = dist[x][y];
                if (d <= 0) continue;
                // alpha: d=1 → 1.0   d=W → 1/W-ish   d=W+1 → 0
                double a = Math.max(0.0, Math.min(1.0, (double)(W - d + 1) / (W + 1)));
                int src = px != null ? px[y * w + x] : img.getRGB(x, y);
                int sa = (src >>> 24) & 0xFF;
                int sr = (src >> 16) & 0xFF;
                int sg = (src >>  8) & 0xFF;
                int sb =  src        & 0xFF;
                int nr = (int) Math.round(sr * (1 - a) + br * a);
                int ng = (int) Math.round(sg * (1 - a) + bg * a);
                int nb = (int) Math.round(sb * (1 - a) + bb * a);
                int na = Math.min(255, (int) Math.round(sa + (255 - sa) * a));
                int out = (na << 24) | (nr << 16) | (ng << 8) | nb;
                if (px != null) px[y * w + x] = out;
                else img.setRGB(x, y, out);
            }
        }
    }

    private static void fillMaskARGB(BufferedImage img, boolean[][] mask, int argb) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = rawPixels(img);
        if (px != null) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w && x < mask.length; x++) {
                    if (y < mask[x].length && mask[x][y]) px[y * w + x] = argb;
                }
            }
        } else {
            for (int x = 0; x < w && x < mask.length; x++) {
                int rh = Math.min(h, mask[x].length);
                for (int y = 0; y < rh; y++) {
                    if (mask[x][y]) img.setRGB(x, y, argb);
                }
            }
        }
    }

    /**
     * Clears (alpha=0) all pixels where region[x][y] == true (Wand III).
     */
    public static void clearRegionMask(BufferedImage img, boolean[][] region) {
        int w = Math.min(img.getWidth(), region.length);
        int h = img.getHeight();
        int[] px = rawPixels(img);
        if (px != null) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (x < region.length && y < region[x].length && region[x][y])
                        px[y * img.getWidth() + x] = 0x00000000;
                }
            }
        } else {
            for (int x = 0; x < w; x++) {
                int rh = Math.min(h, region[x].length);
                for (int y = 0; y < rh; y++) {
                    if (region[x][y]) img.setRGB(x, y, 0x00000000);
                }
            }
        }
    }

    /**
     * CUT_COLOR: makes every pixel in {@code img} that matches {@code targetColor}
     * within {@code tolerancePct} (0-100) fully transparent.
     * Operates globally on the entire image — no flood fill, purely pixel-exact.
     */
    public static void cutByColor(BufferedImage img, Color targetColor, int tolerancePct) {
        int w = img.getWidth(), h = img.getHeight();
        int targetARGB = targetColor.getRGB();
        int tol = tolerancePct * 255 / 100;
        int[] px = rawPixels(img);
        if (px != null) {
            for (int i = 0; i < px.length; i++) {
                if (colorsMatch(px[i], targetARGB, tol)) px[i] = 0x00000000;
            }
        } else {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (colorsMatch(img.getRGB(x, y), targetARGB, tol)) img.setRGB(x, y, 0x00000000);
        }
    }

    /**
     * CUT_UNTIL_COLOR: flood-fills from (x,y), stopping wherever a pixel matches
     * {@code stopColor} within {@code tolerancePct}, then makes the filled region transparent.
     * Identical boundary logic to {@link #floodFillRegionUntilColor} but writes alpha=0.
     */
    public static void cutUntilColor(BufferedImage img, int x, int y,
                                      Color stopColor, int tolerancePct) {
        boolean[][] region = floodFillRegionUntilColor(img, x, y, stopColor, tolerancePct * 255 / 100);
        clearRegionMask(img, region);
    }

    /**
     * CUT_SAME_COLOR: flood-fills from (x,y) collecting only pixels whose color matches
     * the clicked pixel within {@code tolerancePct}, then makes that region transparent.
     * Stops at any pixel of a different color.
     */
    public static void cutSameColor(BufferedImage img, int x, int y, int tolerancePct) {
        boolean[][] region = floodFillRegion(img, x, y, tolerancePct * 255 / 100);
        clearRegionMask(img, region);
    }

    /**
     * Traces the outer boundary of a region mask using Moore-neighborhood tracing,
     * then simplifies with Douglas-Peucker.
     * Returns an array of [x, y] pairs as int[n][2], or null if region is empty.
     */
    /**
     * Traces the outer contour of a boolean region mask using Java2D {@link Area}.
     *
     * Scanline runs of content pixels are merged into an Area, whose PathIterator
     * produces a mathematically correct, closed, non-self-intersecting polygon —
     * no pixel-ordering hacks, no cut-through lines between first and last vertex.
     * The staircase outline is then simplified with closed-polygon D-P.
     *
     * Returns {@code null} if the region is empty.
     */
    public static int[][] traceContour(boolean[][] region, int imgW, int imgH) {
        // Build area from horizontal scanline runs
        java.awt.geom.GeneralPath path = new java.awt.geom.GeneralPath(
                java.awt.geom.GeneralPath.WIND_NON_ZERO);
        for (int y = 0; y < imgH; y++) {
            int runStart = -1;
            for (int x = 0; x <= imgW; x++) {
                boolean in = x < imgW && inRegion(region, x, y, imgW, imgH);
                if (in && runStart < 0) {
                    runStart = x;
                } else if (!in && runStart >= 0) {
                    path.moveTo(runStart, y);
                    path.lineTo(x,        y);
                    path.lineTo(x,        y + 1);
                    path.lineTo(runStart, y + 1);
                    path.closePath();
                    runStart = -1;
                }
            }
        }
        java.awt.geom.Area area = new java.awt.geom.Area(path);
        if (area.isEmpty()) return null;

        // Extract subpaths; keep the one with the most vertices (outer boundary)
        java.util.List<java.util.List<int[]>> subpaths = new java.util.ArrayList<>();
        java.util.List<int[]> cur = null;
        java.awt.geom.PathIterator pi = area.getPathIterator(null);
        float[] coords = new float[6];
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
                case java.awt.geom.PathIterator.SEG_MOVETO ->
                    cur = new java.util.ArrayList<>();
                case java.awt.geom.PathIterator.SEG_LINETO -> {
                    if (cur != null)
                        cur.add(new int[]{Math.round(coords[0]), Math.round(coords[1])});
                }
                case java.awt.geom.PathIterator.SEG_CLOSE -> {
                    if (cur != null && cur.size() >= 3) subpaths.add(cur);
                    cur = null;
                }
            }
            pi.next();
        }
        if (cur != null && cur.size() >= 3) subpaths.add(cur);
        if (subpaths.isEmpty()) return null;

        java.util.List<int[]> outer = subpaths.stream()
                .max(java.util.Comparator.comparingInt(java.util.List::size))
                .orElse(null);
        if (outer == null || outer.size() < 3) return null;

        return douglasPeuckerClosed(outer, 1.5);
    }

    private static boolean inRegion(boolean[][] region, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        if (x >= region.length || y >= region[x].length) return false;
        return region[x][y];
    }

    /**
     * Douglas-Peucker for a CLOSED polygon ring.
     * Splits the ring at its two approximate diameter endpoints and applies
     * open-polyline D-P independently to each half, then concatenates.
     * This avoids the degenerate base-line that occurs when first and last
     * are adjacent pixels (angle −π and +π from centroid).
     */
    private static int[][] douglasPeuckerClosed(java.util.List<int[]> pts, double epsilon) {
        int n = pts.size();
        if (n <= 4) return pts.stream().toArray(int[][]::new);

        // Find approximate diameter: scan a sample of pairs for max distance
        int p1 = 0, p2 = n / 2;
        double maxD = 0;
        int step = Math.max(1, n / 60);
        for (int i = 0; i < n; i += step) {
            for (int j = i + n / 4; j < i + 3 * n / 4; j++) {
                int jj = j % n;
                int[] a = pts.get(i), b = pts.get(jj);
                double d = Math.hypot(a[0] - b[0], a[1] - b[1]);
                if (d > maxD) { maxD = d; p1 = i; p2 = jj; }
            }
        }
        if (p1 > p2) { int t = p1; p1 = p2; p2 = t; }

        // Two arcs: [p1..p2] and [p2..n) + [0..p1]
        int[][] r1 = douglasPeucker(pts.subList(p1, p2 + 1), epsilon);

        java.util.List<int[]> arc2 = new java.util.ArrayList<>(pts.subList(p2, n));
        arc2.addAll(pts.subList(0, p1 + 1));
        int[][] r2 = douglasPeucker(arc2, epsilon);

        // Concatenate: r1 (all) + r2 (skip first, which == r1 last; skip last, which == r1 first)
        int total = r1.length + Math.max(0, r2.length - 2);
        int[][] result = new int[total][];
        System.arraycopy(r1, 0, result, 0, r1.length);
        for (int i = 1; i < r2.length - 1; i++) result[r1.length + i - 1] = r2[i];
        return result;
    }

    private static int[][] douglasPeucker(java.util.List<int[]> pts, double epsilon) {
        if (pts.size() <= 2) return pts.stream().toArray(int[][]::new);
        // Find point with max distance from line start-end
        int[] start = pts.get(0), end = pts.get(pts.size() - 1);
        double maxDist = 0;
        int maxIdx = 0;
        for (int i = 1; i < pts.size() - 1; i++) {
            double d = pointToLineDist(pts.get(i), start, end);
            if (d > maxDist) { maxDist = d; maxIdx = i; }
        }
        if (maxDist > epsilon) {
            int[][] left  = douglasPeucker(pts.subList(0, maxIdx + 1), epsilon);
            int[][] right = douglasPeucker(pts.subList(maxIdx, pts.size()), epsilon);
            int[][] result = new int[left.length + right.length - 1][];
            System.arraycopy(left,  0, result, 0,              left.length);
            System.arraycopy(right, 1, result, left.length,    right.length - 1);
            return result;
        } else {
            return new int[][]{start, end};
        }
    }

    private static double pointToLineDist(int[] p, int[] a, int[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return Math.hypot(p[0] - a[0], p[1] - a[1]);
        double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    }

    /**
     * Wand IV – pixel-level inwards collapse.
     *
     * Rasterizes the drawn polygon, then marks every pixel inside it that is
     * neither transparent nor matches {@code secondaryColor} as "content".
     * This spreads at pixel resolution from every boundary point simultaneously
     * (wavefront semantics) so there are no jumps.
     *
     * Returns a boolean[w][h] content mask suitable for {@link #traceContour},
     * or {@code null} if no content was found inside the polygon.
     *
     * @param polyXs       polygon X coordinates in image-space
     * @param polyYs       polygon Y coordinates in image-space
     * @param img          the canvas image
     * @param secondaryColor pixels matching this color are treated as pass-through
     * @param tolerancePct tolerance 0-100 % for secondary-color matching
     */
    public static boolean[][] collapseInward(int[] polyXs, int[] polyYs,
            java.awt.image.BufferedImage img,
            java.awt.Color secondaryColor, int tolerancePct) {

        int w   = img.getWidth(), h = img.getHeight();
        int secRGB = secondaryColor.getRGB();
        int tolAbs = tolerancePct * 255 / 100;

        boolean[][] inside  = rasterizePolygonMask(polyXs, polyYs, w, h);
        boolean[][] content = new boolean[w][h];
        boolean anyContent  = false;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!inside[x][y]) continue;
                int  rgb   = img.getRGB(x, y);
                int  alpha = (rgb >>> 24) & 0xFF;
                boolean passThrough = alpha == 0 || colorsMatch(rgb, secRGB, tolAbs);
                if (!passThrough) {
                    content[x][y] = true;
                    anyContent     = true;
                }
            }
        }
        return anyContent ? content : null;
    }

    /** Fills the given polygon into a boolean mask using Java2D. */
    private static boolean[][] rasterizePolygonMask(int[] xs, int[] ys, int w, int h) {
        java.awt.image.BufferedImage tmp =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = tmp.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillPolygon(xs, ys, xs.length);
        g2.dispose();
        boolean[][] mask = new boolean[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                mask[x][y] = (tmp.getRGB(x, y) & 0xFF) > 128;
        return mask;
    }

    /**
     * Densifies a path by inserting interpolated points so no consecutive gap
     * exceeds {@code maxGapPx} image pixels.  Used before inward collapse so the
     * result has enough vertices to trace fine content edges.
     */
    public static java.util.List<java.awt.Point> densifyPath(
            java.util.List<java.awt.Point> pts, int maxGapPx) {
        if (pts == null || pts.size() < 2) return pts;
        java.util.List<java.awt.Point> out = new java.util.ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            java.awt.Point a = pts.get(i);
            java.awt.Point b = pts.get((i + 1) % pts.size());
            out.add(a);
            double dist = Math.hypot(b.x - a.x, b.y - a.y);
            int steps = (int) Math.ceil(dist / maxGapPx);
            for (int s = 1; s < steps; s++) {
                double t = (double) s / steps;
                out.add(new java.awt.Point(
                        (int) Math.round(a.x + t * (b.x - a.x)),
                        (int) Math.round(a.y + t * (b.y - a.y))));
            }
        }
        return out;
    }

    /**
     * Simplifies a list of image-space points using Douglas-Peucker.
     * Used for freehand path simplification.
     */
    public static java.util.List<java.awt.Point> simplifyPath(
            java.util.List<java.awt.Point> pts, double epsilon) {
        if (pts.size() <= 2) return new java.util.ArrayList<>(pts);
        int[][] arr = pts.stream().map(p -> new int[]{p.x, p.y}).toArray(int[][]::new);
        java.util.List<int[]> list = new java.util.ArrayList<>(java.util.Arrays.asList(arr));
        int[][] simplified = douglasPeucker(list, epsilon);
        java.util.List<java.awt.Point> result = new java.util.ArrayList<>();
        for (int[] pt : simplified) result.add(new java.awt.Point(pt[0], pt[1]));
        return result;
    }

    // Helpers
    private static int[] boundingBox(Point from, Point to) {
        int x = Math.min(from.x, to.x);
        int y = Math.min(from.y, to.y);
        int w = Math.abs(to.x - from.x);
        int h = Math.abs(to.y - from.y);
        return (w < 1 || h < 1) ? null : new int[]{x, y, w, h};
    }

    private static void applyQuality(Graphics2D g2, boolean aa) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                aa ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                aa ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private static boolean colorsMatch(int c1, int c2, int tolerance) {
        return Math.abs(((c1 >> 16) & 0xFF) - ((c2 >> 16) & 0xFF)) <= tolerance
            && Math.abs(((c1 >>  8) & 0xFF) - ((c2 >>  8) & 0xFF)) <= tolerance
            && Math.abs( (c1        & 0xFF) - ( c2        & 0xFF)) <= tolerance
            && Math.abs(((c1 >> 24) & 0xFF) - ((c2 >> 24) & 0xFF)) <= tolerance;
    }
}
