package paint;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Hilfsmethoden fürs Laden und Konvertieren von Bildern.
 */
public class ImageLoader {
    public static BufferedImage loadImage(File file) {
        try {
            return javax.imageio.ImageIO.read(file);
        } catch (Exception e) {
            System.err.println("[ImageLoader] Fehler beim Laden: " + file.getAbsolutePath());
            return null;
        }
    }

    /**
     * Converts an arbitrary {@link Image} (e.g. from clipboard or drag-and-drop)
     * into a {@link BufferedImage} with TYPE_INT_ARGB. Handles the Graphics2D
     * disposal so callers don't leak native resources.
     * Returns null if the image has no usable dimensions.
     */
    public static BufferedImage toBuffered(Image img) {
        if (img == null) return null;
        if (img instanceof BufferedImage bi) return bi;
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
        return bi;
    }
}
