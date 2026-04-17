package paint;

import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Callbacks required by HRulerPanel and VRulerPanel to interact with SelectiveAlphaEditor.
 * Rulers use these to access image dimensions, zoom, scroll position, and ruler unit.
 */
public interface RulerCallbacks {
    BufferedImage getWorkingImage();
    JScrollPane getScrollPane();
    JPanel getCanvasPanel();
    RulerUnit getRulerUnit();
    double getZoom();

    /** Returns the current page layout (margins etc.) or null if not in book mode. */
    PageLayout getPageLayout();

    /** Returns true when book mode is active. */
    boolean isBookMode();

    /**
     * Called when the user drags a margin handle in the ruler.
     * @param isHorizontal true = left/right margins (H ruler), false = top/bottom (V ruler)
     * @param isFirst      true = left/top margin, false = right/bottom margin
     * @param newMm        new margin value in mm
     */
    void onMarginDragged(boolean isHorizontal, boolean isFirst, int newMm);
}
