package paint;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Manages image transformation operations: flip, rotate, scale.
 * Encapsulates all transform logic and operations.
 */
public class ImageTransformHandler {

    // Context references
    private BufferedImage workingImage = null;

    // Callback for state propagation
    private Runnable onTransformComplete = null;

    public ImageTransformHandler() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Flip Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Flip horizontally.
     * @param region The selection rectangle, or null for full image
     * @return The transformed image (or modified working image)
     */
    public BufferedImage flipHorizontal(Rectangle region) {
        if (workingImage == null) return null;

        if (region != null) {
            PaintEngine.flipHorizontalInRegion(workingImage, region);
        } else {
            workingImage = PaintEngine.flipHorizontal(workingImage);
        }

        if (onTransformComplete != null) {
            onTransformComplete.run();
        }
        return workingImage;
    }

    /**
     * Flip vertically.
     * @param region The selection rectangle, or null for full image
     * @return The transformed image (or modified working image)
     */
    public BufferedImage flipVertical(Rectangle region) {
        if (workingImage == null) return null;

        if (region != null) {
            PaintEngine.flipVerticalInRegion(workingImage, region);
        } else {
            workingImage = PaintEngine.flipVertical(workingImage);
        }

        if (onTransformComplete != null) {
            onTransformComplete.run();
        }
        return workingImage;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Rotate Operation
    // ─────────────────────────────────────────────────────────────

    /**
     * Rotate the image or selection.
     * @param angle Rotation angle in degrees
     * @param region The selection rectangle, or null for full image
     * @return The transformed image (or modified working image)
     * @throws NumberFormatException if angle is not a valid number
     */
    public BufferedImage rotate(double angle, Rectangle region) throws NumberFormatException {
        if (workingImage == null) return null;

        if (region != null) {
            PaintEngine.rotateInRegion(workingImage, region, angle);
        } else {
            workingImage = PaintEngine.rotate(workingImage, angle);
        }

        if (onTransformComplete != null) {
            onTransformComplete.run();
        }
        return workingImage;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Scale Operation
    // ─────────────────────────────────────────────────────────────

    /**
     * Scale the image or selection to new dimensions.
     * @param newWidth Target width in pixels
     * @param newHeight Target height in pixels
     * @param region The selection rectangle, or null for full image
     * @return The new selection rectangle (for region scaling) or null
     * @throws NumberFormatException if dimensions are not valid numbers
     */
    public Rectangle scale(int newWidth, int newHeight, Rectangle region) throws NumberFormatException {
        if (workingImage == null) return null;

        Rectangle result = null;
        if (region != null) {
            result = PaintEngine.scaleInRegion(workingImage, region, newWidth, newHeight);
        } else {
            workingImage = PaintEngine.scale(workingImage, newWidth, newHeight);
        }

        if (onTransformComplete != null) {
            onTransformComplete.run();
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Setters for Context
    // ─────────────────────────────────────────────────────────────

    /**
     * Set the working image reference.
     */
    public void setWorkingImage(BufferedImage img) {
        this.workingImage = img;
    }

    /**
     * Set a callback for when transform completes.
     */
    public void setOnTransformComplete(Runnable r) {
        this.onTransformComplete = r;
    }

    /**
     * Get the current working image.
     */
    public BufferedImage getWorkingImage() {
        return workingImage;
    }
}
