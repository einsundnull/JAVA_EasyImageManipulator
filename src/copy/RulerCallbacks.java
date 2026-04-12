package paint.copy;

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
}
