package paint.copy;

/**
 * Ruler unit enumeration for measurements.
 * Supports: pixels, millimeters, centimeters, inches.
 */
public enum RulerUnit {
    PX, MM, CM, INCH;

    private static final double SCREEN_DPI = 96.0;

    /** How many image pixels equal one unit (at given image DPI). */
    public double pxPerUnit() {
        return switch (this) {
            case PX   -> 1.0;
            case MM   -> SCREEN_DPI / 25.4;
            case CM   -> SCREEN_DPI / 2.54;
            case INCH -> SCREEN_DPI;
        };
    }

    /** Returns the label for this unit. */
    public String label() {
        return switch (this) {
            case PX   -> "px";
            case MM   -> "mm";
            case CM   -> "cm";
            case INCH -> "in";
        };
    }
}
