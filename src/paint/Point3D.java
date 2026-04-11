package paint;

import java.io.Serializable;

/**
 * A 3D point with x, y, z coordinates.
 * Used by PathLayer to store control points.
 * Z-component defaults to 1.0 for future use (e.g., depth ordering, animation).
 */
public final class Point3D implements Serializable {
    private static final long serialVersionUID = 1L;

    public double x;
    public double y;
    public double z;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Point3D(double x, double y) {
        this(x, y, 1.0);
    }

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    // ── Mutations (return new instances) ──────────────────────────────────────

    /** Returns a copy of this point moved to a new position. */
    public Point3D withPosition(double nx, double ny) {
        return new Point3D(nx, ny, z);
    }

    /** Returns a copy of this point with updated Z coordinate. */
    public Point3D withZ(double nz) {
        return new Point3D(x, y, nz);
    }

    /** Returns a copy of this point with all coordinates updated. */
    public Point3D with(double nx, double ny, double nz) {
        return new Point3D(nx, ny, nz);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Calculates the Euclidean distance to another point (ignoring Z).
     * Used for hit testing (click detection on canvas).
     */
    public double distanceTo(double px, double py) {
        double dx = x - px;
        double dy = y - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Calculates 3D distance to another point (including Z).
     */
    public double distance3DTo(Point3D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns a copy of this point.
     */
    public Point3D copy() {
        return new Point3D(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("Point3D(%.1f, %.1f, %.1f)", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Point3D p)) return false;
        return x == p.x && y == p.y && z == p.z;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) ^ Double.hashCode(y) ^ Double.hashCode(z);
    }
}
