package paint;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A non-destructive path layer storing a sequence of control points.
 *
 * A PathLayer can:
 *  - Store points (x, y, z) that define a path
 *  - Optionally carry a raster image (stretched along the path)
 *  - Be edited: points can be moved, added, removed, selected
 *  - Be rendered as lines connecting points + optional image
 *
 * All mutations return NEW instances (value-object semantics).
 *
 * {@code width}/{@code height} (inherited from {@link Layer}) are computed
 * from the bounding box of all points.
 */
public final class PathLayer extends Layer {

    private final List<Point3D> points;
    private final BufferedImage image;  // null if no image
    private final boolean closed;       // true = polygon (close path), false = open path

    // ── Private constructor – callers use the factory method ─────────────────

    private PathLayer(int id, List<Point3D> points, BufferedImage image, boolean closed,
                      int x, int y, int w, int h) {
        super(id, x, y, w, h);
        this.points = new ArrayList<>(points);
        this.image = image;
        this.closed = closed;
    }

    // ── Factory method ────────────────────────────────────────────────────────

    /**
     * Creates a PathLayer, computing {@code width}/{@code height} from point bounds.
     *
     * @param id       unique layer id
     * @param points   list of control points (must have at least 1 point)
     * @param image    optional raster image (may be null)
     * @param closed   true = polygon, false = open path
     * @param x        image-space X position of the bounding box top-left
     * @param y        image-space Y position of the bounding box top-left
     */
    public static PathLayer of(int id, List<Point3D> points, BufferedImage image, boolean closed,
                               int x, int y) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("PathLayer must have at least 1 point");
        }

        // Compute bounding box from points
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point3D p : points) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }

        int w = Math.max(1, (int) (maxX - minX) + 1);
        int h = Math.max(1, (int) (maxY - minY) + 1);

        return new PathLayer(id, points, image, closed, x, y, w, h);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<Point3D> points()     { return new ArrayList<>(points); }
    public Point3D getPoint(int idx)  { return idx >= 0 && idx < points.size() ? points.get(idx).copy() : null; }
    public int pointCount()           { return points.size(); }
    public BufferedImage image()      { return image; }
    public boolean isClosed()         { return closed; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    @Override
    public PathLayer withPosition(int nx, int ny) {
        return new PathLayer(id, points, image, closed, nx, ny, width, height);
    }

    /**
     * Returns a copy with updated bounds.
     * The points are scaled proportionally to fit the new bounds.
     */
    @Override
    public PathLayer withBounds(int nx, int ny, int nw, int nh) {
        double scaleX = (double) nw / Math.max(1, width);
        double scaleY = (double) nh / Math.max(1, height);
        double scale = Math.max(scaleX, scaleY);  // Preserve aspect ratio

        List<Point3D> scaledPoints = new ArrayList<>();
        for (Point3D p : points) {
            scaledPoints.add(new Point3D(p.x * scale, p.y * scale, p.z));
        }

        // Recompute bounds with scaled points
        return of(id, scaledPoints, image, closed, nx, ny);
    }

    /**
     * Returns a copy with a point moved to a new position.
     * The bounding box is recomputed.
     */
    public PathLayer withMovedPoint(int pointIndex, double newX, double newY) {
        if (pointIndex < 0 || pointIndex >= points.size()) {
            return this;
        }
        List<Point3D> newPoints = new ArrayList<>(points);
        newPoints.set(pointIndex, new Point3D(newX, newY, points.get(pointIndex).z));
        return of(id, newPoints, image, closed, x, y);
    }

    /**
     * Returns a copy with a point's Z coordinate updated.
     */
    public PathLayer withPointZ(int pointIndex, double newZ) {
        if (pointIndex < 0 || pointIndex >= points.size()) {
            return this;
        }
        List<Point3D> newPoints = new ArrayList<>(points);
        Point3D old = points.get(pointIndex);
        newPoints.set(pointIndex, new Point3D(old.x, old.y, newZ));
        return new PathLayer(id, newPoints, image, closed, x, y, width, height);
    }

    /**
     * Returns a copy with a point added at the specified index.
     * If index is -1, the point is appended to the end.
     */
    public PathLayer withAddedPoint(int index, Point3D newPoint) {
        List<Point3D> newPoints = new ArrayList<>(points);
        if (index < 0 || index > newPoints.size()) {
            newPoints.add(newPoint);
        } else {
            newPoints.add(index, newPoint);
        }
        return of(id, newPoints, image, closed, x, y);
    }

    /**
     * Returns a copy with a point removed.
     * The path must have at least 1 point remaining.
     */
    public PathLayer withRemovedPoint(int index) {
        if (index < 0 || index >= points.size() || points.size() <= 1) {
            return this;  // Can't remove the last point
        }
        List<Point3D> newPoints = new ArrayList<>(points);
        newPoints.remove(index);
        return of(id, newPoints, image, closed, x, y);
    }

    /**
     * Returns a copy with an optional image.
     */
    public PathLayer withImage(BufferedImage newImage) {
        return new PathLayer(id, points, newImage, closed, x, y, width, height);
    }

    /**
     * Returns a copy with the closed state toggled.
     */
    public PathLayer withClosed(boolean newClosed) {
        return new PathLayer(id, points, image, newClosed, x, y, width, height);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() {
        return "Path " + id;
    }

    /**
     * Checks if a screen-space point hits any path point within the given radius.
     * Returns the index of the hit point, or -1 if no hit.
     */
    public int hitTestPoint(int screenX, int screenY, int radius) {
        // This is a placeholder; actual implementation in CanvasPanel
        // will need to convert screen coords to image-space coords first
        for (int i = 0; i < points.size(); i++) {
            double dist = points.get(i).distanceTo(screenX, screenY);
            if (dist <= radius) {
                return i;
            }
        }
        return -1;
    }
}
