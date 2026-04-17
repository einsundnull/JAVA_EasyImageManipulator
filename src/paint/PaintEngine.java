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
import java.util.Arrays;
import java.util.Stack;

/**
 * Stateless drawing engine.
 * All methods operate on the supplied BufferedImage in IMAGE-space.
 * The caller is responsible for zoom-to-image coordinate conversion
 * before passing points here.
 */
public class PaintEngine {

    // ── Tool enum ─────────────────────────────────────────────────────────────
    public enum Tool {
        PENCIL, FLOODFILL, LINE, CIRCLE, RECT, ERASER, EYEDROPPER, SELECT, TEXT, PATH,
        FREE_PATH, WAND_I, WAND_II, WAND_III, WAND_IV
    }

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
        g2.setStroke(new BasicStroke(strokeWidth,
                shape == BrushShape.ROUND ? BasicStroke.CAP_ROUND : BasicStroke.CAP_SQUARE,
                shape == BrushShape.ROUND ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_MITER));
        if (from.equals(to)) {
            int r = strokeWidth / 2;
            if (shape == BrushShape.ROUND) {
                g2.fillOval(from.x - r, from.y - r, strokeWidth, strokeWidth);
            } else {
                g2.fillRect(from.x - r, from.y - r, strokeWidth, strokeWidth);
            }
        } else {
            g2.drawLine(from.x, from.y, to.x, to.y);
        }
        g2.dispose();
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
        if (x < 0 || x >= img.getWidth() || y < 0 || y >= img.getHeight()) return;
        int targetARGB = img.getRGB(x, y);
        int fillARGB   = fillColor.getRGB();
        if (targetARGB == fillARGB) return;

        int w = img.getWidth(), h = img.getHeight();
        Stack<Point>  stack   = new Stack<>();
        boolean[][]   visited = new boolean[w][h];
        stack.push(new Point(x, y));
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h || visited[p.x][p.y]) continue;
            if (!colorsMatch(img.getRGB(p.x, p.y), targetARGB, tolerance)) continue;
            visited[p.x][p.y] = true;
            img.setRGB(p.x, p.y, fillARGB);
            stack.push(new Point(p.x + 1, p.y));
            stack.push(new Point(p.x - 1, p.y));
            stack.push(new Point(p.x, p.y + 1));
            stack.push(new Point(p.x, p.y - 1));
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
        int targetARGB = img.getRGB(x, y);
        boolean[][] region = new boolean[w][h];
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(x, y));
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h || region[p.x][p.y]) continue;
            if (!colorsMatch(img.getRGB(p.x, p.y), targetARGB, tolerance)) continue;
            region[p.x][p.y] = true;
            stack.push(new Point(p.x + 1, p.y));
            stack.push(new Point(p.x - 1, p.y));
            stack.push(new Point(p.x, p.y + 1));
            stack.push(new Point(p.x, p.y - 1));
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
        int stopARGB = stopColor.getRGB();
        int startARGB = img.getRGB(x, y);
        // If the starting pixel itself is the stop color, return empty region
        if (colorsMatch(startARGB, stopARGB, stopTolerance)) return new boolean[w][h];
        boolean[][] region = new boolean[w][h];
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(x, y));
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h || region[p.x][p.y]) continue;
            int rgb = img.getRGB(p.x, p.y);
            if (colorsMatch(rgb, stopARGB, stopTolerance)) continue;  // boundary
            region[p.x][p.y] = true;
            stack.push(new Point(p.x + 1, p.y));
            stack.push(new Point(p.x - 1, p.y));
            stack.push(new Point(p.x, p.y + 1));
            stack.push(new Point(p.x, p.y - 1));
        }
        return region;
    }

    /**
     * Clears (alpha=0) all pixels where region[x][y] == true (Wand III).
     */
    public static void clearRegionMask(BufferedImage img, boolean[][] region) {
        int w = Math.min(img.getWidth(), region.length);
        for (int x = 0; x < w; x++) {
            int h = Math.min(img.getHeight(), region[x].length);
            for (int y = 0; y < h; y++) {
                if (region[x][y]) img.setRGB(x, y, 0x00000000);
            }
        }
    }

    /**
     * Traces the outer boundary of a region mask using Moore-neighborhood tracing,
     * then simplifies with Douglas-Peucker.
     * Returns an array of [x, y] pairs as int[n][2], or null if region is empty.
     */
    public static int[][] traceContour(boolean[][] region, int imgW, int imgH) {
        // Find any boundary pixel (leftmost of topmost row)
        int startX = -1, startY = -1;
        outer:
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                if (x < region.length && y < region[x].length && region[x][y]) {
                    startX = x; startY = y;
                    break outer;
                }
            }
        }
        if (startX < 0) return null;

        // Simple boundary trace: walk around using 8-connectivity
        java.util.List<int[]> boundary = new java.util.ArrayList<>();
        int[] dx = {1, 1, 0, -1, -1, -1, 0, 1};
        int[] dy = {0, 1, 1, 1, 0, -1, -1, -1};
        // For each boundary pixel, emit centroid of that pixel
        // Use a simple scan-based contour instead of Moore tracing to avoid complexity
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                if (!inRegion(region, x, y, imgW, imgH)) continue;
                // Boundary pixel = has at least one 4-neighbor outside region
                if (!inRegion(region, x-1, y, imgW, imgH) || !inRegion(region, x+1, y, imgW, imgH)
                 || !inRegion(region, x, y-1, imgW, imgH) || !inRegion(region, x, y+1, imgW, imgH)) {
                    boundary.add(new int[]{x, y});
                }
            }
        }
        if (boundary.isEmpty()) return null;

        // Sort boundary pixels into a connected chain using nearest-neighbor
        java.util.List<int[]> ordered = orderBoundary(boundary);

        // Douglas-Peucker simplification
        return douglasPeucker(ordered, 1.5);
    }

    private static boolean inRegion(boolean[][] region, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        if (x >= region.length || y >= region[x].length) return false;
        return region[x][y];
    }

    private static java.util.List<int[]> orderBoundary(java.util.List<int[]> pts) {
        if (pts.size() <= 2) return pts;
        java.util.List<int[]> ordered = new java.util.ArrayList<>();
        boolean[] used = new boolean[pts.size()];
        int cur = 0;
        used[0] = true;
        ordered.add(pts.get(0));
        for (int i = 1; i < pts.size(); i++) {
            int bestIdx = -1;
            double bestDist = Double.MAX_VALUE;
            int[] c = ordered.get(ordered.size() - 1);
            for (int j = 0; j < pts.size(); j++) {
                if (used[j]) continue;
                int[] p = pts.get(j);
                double d = Math.hypot(c[0] - p[0], c[1] - p[1]);
                if (d < bestDist) { bestDist = d; bestIdx = j; }
            }
            if (bestIdx < 0) break;
            used[bestIdx] = true;
            ordered.add(pts.get(bestIdx));
        }
        return ordered;
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
     * Wand IV – inwards collapse.
     * Takes a closed freehand polygon and collapses each vertex inward (toward
     * centroid, 1 px per step) until the pixel under the vertex is neither
     * transparent nor matches {@code secondaryColor} within the given tolerance.
     *
     * @param pts          polygon vertices in image-space
     * @param img          the canvas image
     * @param secondaryColor the "pass-through" color (treated like transparency)
     * @param tolerancePct tolerance 0-100 % (how closely a pixel must match
     *                     secondaryColor to be treated as "pass-through")
     * @return new list of collapsed image-space vertices
     */
    public static java.util.List<java.awt.Point> collapsePathInward(
            java.util.List<java.awt.Point> pts, java.awt.image.BufferedImage img,
            java.awt.Color secondaryColor, int tolerancePct) {

        if (pts == null || pts.size() < 3) return pts;
        int w = img.getWidth(), h = img.getHeight();
        int secRGB   = secondaryColor.getRGB();
        int tolAbs   = tolerancePct * 255 / 100;

        // Centroid used as "inward" direction reference
        double cx = 0, cy = 0;
        for (java.awt.Point p : pts) { cx += p.x; cy += p.y; }
        cx /= pts.size(); cy /= pts.size();

        java.util.List<java.awt.Point> result = new java.util.ArrayList<>();
        for (java.awt.Point p : pts) {
            double dx = cx - p.x, dy = cy - p.y;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.5) { result.add(p); continue; }
            dx /= len; dy /= len;  // unit vector toward centroid

            double nx = p.x, ny = p.y;
            java.awt.Point best = p;   // last "pass-through" position
            while (true) {
                nx += dx; ny += dy;
                int ix = (int) Math.round(nx), iy = (int) Math.round(ny);
                // Stop at image boundary or centroid overshoot
                if (ix < 0 || ix >= w || iy < 0 || iy >= h) break;
                if (Math.hypot(ix - cx, iy - cy) < 1.0) break; // reached centroid
                int rgb   = img.getRGB(ix, iy);
                int alpha = (rgb >>> 24) & 0xFF;
                boolean isTransparent   = alpha == 0;
                boolean isSecondaryLike = alpha > 0 && colorsMatch(rgb, secRGB, tolAbs);
                if (isTransparent || isSecondaryLike) {
                    best = new java.awt.Point(ix, iy);  // still pass-through, keep moving
                } else {
                    best = new java.awt.Point(ix, iy);  // stop at the first blocking pixel
                    break;
                }
            }
            result.add(best);
        }
        return result;
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
