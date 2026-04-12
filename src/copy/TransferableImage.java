package paint.copy;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/**
 * Clipboard-transferable wrapper for BufferedImage.
 * Used for copy/paste of image selections.
 */
public class TransferableImage implements Transferable {
    private final BufferedImage image;

    public TransferableImage(BufferedImage img) {
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
