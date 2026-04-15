package paint;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Hilfsmethode zum Laden von Bildern als BufferedImage.
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
}
