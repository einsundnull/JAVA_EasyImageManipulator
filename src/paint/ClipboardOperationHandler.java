package paint;

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/**
 * Manages clipboard operations: copy, cut, paste, and system clipboard sync.
 * Encapsulates all clipboard-related logic and dependencies.
 */
public class ClipboardOperationHandler {

    // Clipboard state
    private BufferedImage clipboard = null;

    // Context references (set by owner)
    private BufferedImage workingImage = null;
    private PaintEngine.Tool activeTool = PaintEngine.Tool.PENCIL;

    // Callbacks for operations that need external coordination
    private Runnable onClipboardUpdated = null;

    public ClipboardOperationHandler() {
    }

    // ─────────────────────────────────────────────────────────────
    // Public API - Clipboard Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Copy region/selection to clipboard (internal + system).
     * @param region The selection rectangle, or null for full image
     * @param isOutside If true, copy area outside selection instead
     * @return The clipboard image
     */
    public BufferedImage doCopy(Rectangle region, boolean isOutside) {
        if (workingImage == null) return null;

        BufferedImage result = null;
        if (region != null) {
            if (isOutside) {
                result = PaintEngine.cropOutside(workingImage, region);
            } else {
                result = PaintEngine.cropRegion(workingImage, region);
            }
        } else {
            result = deepCopy(workingImage);
        }

        if (result != null) {
            clipboard = result;
            copyToSystemClipboard(result);
            if (onClipboardUpdated != null) onClipboardUpdated.run();
        }

        return result;
    }

    /**
     * Copy polygon to clipboard (PathLayer support).
     * @param xPoints X coordinates of polygon
     * @param yPoints Y coordinates of polygon
     * @param isOutside If true, copy area outside polygon instead
     * @return The clipboard image
     */
    public BufferedImage doCopyPolygon(int[] xPoints, int[] yPoints, boolean isOutside) {
        if (workingImage == null || xPoints == null || yPoints == null) return null;

        BufferedImage result = null;
        if (isOutside) {
            result = PaintEngine.cropOutsidePolygon(workingImage, xPoints, yPoints);
        } else {
            result = PaintEngine.cropPolygon(workingImage, xPoints, yPoints);
        }

        if (result != null) {
            clipboard = result;
            copyToSystemClipboard(result);
            if (onClipboardUpdated != null) onClipboardUpdated.run();
        }

        return result;
    }

    /**
     * Cut a region from the working image (destructive).
     * @param region The selection rectangle, or null for full image
     * @param isOutside If true, cut area outside selection instead
     * @return The cut image
     */
    public BufferedImage doCut(Rectangle region, boolean isOutside) {
        BufferedImage result = doCopy(region, isOutside);
        if (result != null && workingImage != null) {
            if (region != null) {
                if (isOutside) {
                    PaintEngine.clearOutside(workingImage, region);
                } else {
                    PaintEngine.clearRegion(workingImage, region);
                }
            } else {
                PaintEngine.clearRegion(workingImage,
                        new Rectangle(0, 0, workingImage.getWidth(), workingImage.getHeight()));
            }
        }
        return result;
    }

    /**
     * Cut a polygon from the working image (destructive, PathLayer support).
     * @param xPoints X coordinates of polygon
     * @param yPoints Y coordinates of polygon
     * @param isOutside If true, cut area outside polygon instead
     * @return The cut image
     */
    public BufferedImage doCutPolygon(int[] xPoints, int[] yPoints, boolean isOutside) {
        BufferedImage result = doCopyPolygon(xPoints, yPoints, isOutside);
        if (result != null && workingImage != null) {
            if (isOutside) {
                PaintEngine.clearOutsidePolygon(workingImage, xPoints, yPoints);
            } else {
                PaintEngine.clearPolygon(workingImage, xPoints, yPoints);
            }
        }
        return result;
    }

    /**
     * Paste from clipboard (internal or system).
     * @return The pasted image, or null if clipboard is empty
     */
    public BufferedImage doPaste() {
        BufferedImage fromSys = pasteFromSystemClipboard();
        if (fromSys != null) {
            clipboard = fromSys;
            if (onClipboardUpdated != null) onClipboardUpdated.run();
        }
        return clipboard;
    }

    /**
     * Get the current clipboard contents.
     */
    public BufferedImage getClipboard() {
        return clipboard;
    }

    /**
     * Set clipboard contents directly.
     */
    public void setClipboard(BufferedImage img) {
        clipboard = img;
        if (img != null) {
            copyToSystemClipboard(img);
        }
        if (onClipboardUpdated != null) onClipboardUpdated.run();
    }

    /**
     * Clear the clipboard.
     */
    public void clearClipboard() {
        clipboard = null;
    }

    /**
     * Check if clipboard has content.
     */
    public boolean hasClipboard() {
        return clipboard != null;
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Copy a BufferedImage to the system clipboard.
     */
    private void copyToSystemClipboard(BufferedImage img) {
        if (img == null) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new TransferableImage(img), null);
        } catch (Exception ignored) {}
    }

    /**
     * Paste a BufferedImage from the system clipboard.
     */
    private BufferedImage pasteFromSystemClipboard() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null),
                        BufferedImage.TYPE_INT_ARGB);
                bi.createGraphics().drawImage(img, 0, 0, null);
                return bi;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Deep copy a BufferedImage.
     */
    private BufferedImage deepCopy(BufferedImage src) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = dst.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dst;
    }

    // ─────────────────────────────────────────────────────────────
    // Transferable Adapter
    // ─────────────────────────────────────────────────────────────

    /**
     * Adapter to make BufferedImage transferable to system clipboard.
     */
    private static class TransferableImage implements Transferable {
        private final BufferedImage image;

        TransferableImage(BufferedImage img) {
            this.image = img;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{ DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor f) {
            return DataFlavor.imageFlavor.equals(f);
        }

        @Override
        public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
            return image;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Setters for Context
    // ─────────────────────────────────────────────────────────────

    /**
     * Set the working image reference (needed for crop operations).
     */
    public void setWorkingImage(BufferedImage img) {
        this.workingImage = img;
    }

    /**
     * Set the active tool (for future use, e.g., tool-specific paste behavior).
     */
    public void setActiveTool(PaintEngine.Tool tool) {
        this.activeTool = tool;
    }

    /**
     * Set a callback for when clipboard contents change.
     */
    public void setOnClipboardUpdated(Runnable r) {
        this.onClipboardUpdated = r;
    }
}
