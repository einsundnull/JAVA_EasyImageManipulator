package paint;

import java.awt.image.BufferedImage;

/**
 * An inserted image fragment placed as a non-destructive layer on the canvas.
 *
 * Elements are stored per-image in the directory and remain fully interactive
 * (movable, resizable, deletable) until explicitly merged to the canvas.
 *
 * Stored in:  Map&lt;File, List&lt;Element&gt;&gt;  inside CanvasSlot,
 * conceptually matching:
 *   List I  → which image from the directory  (Map key)
 *   List II → elements on that image          (Map value)
 *
 * width / height hold the current rendered size in image-space pixels.
 * Scaling is implicit: original image dimensions vs width/height.
 */
public record Element(
    int           id,
    BufferedImage image,
    int           x,
    int           y,
    int           width,
    int           height
) {
    /** Returns a copy of this element with an updated position. */
    public Element withPosition(int nx, int ny) {
        return new Element(id, image, nx, ny, width, height);
    }

    /** Returns a copy of this element with updated size and position (for resize). */
    public Element withBounds(int nx, int ny, int nw, int nh) {
        return new Element(id, image, nx, ny, Math.max(1, nw), Math.max(1, nh));
    }
}
