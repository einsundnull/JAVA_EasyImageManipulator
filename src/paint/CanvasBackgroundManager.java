package paint;

import java.awt.Color;

/**
 * Manages canvas background colors and rendering.
 * Encapsulates checkerboard background configuration.
 */
public class CanvasBackgroundManager {

    private Color color1;
    private Color color2;

    // Callback for UI updates
    private Runnable onColorsChanged = null;

    /**
     * Create a background manager with initial colors.
     */
    public CanvasBackgroundManager(Color col1, Color col2) {
        this.color1 = col1;
        this.color2 = col2;
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Get the first checkerboard color.
     */
    public Color getColor1() {
        return color1;
    }

    /**
     * Set the first checkerboard color.
     */
    public void setColor1(Color c) {
        this.color1 = c;
        notifyChanged();
    }

    /**
     * Get the second checkerboard color.
     */
    public Color getColor2() {
        return color2;
    }

    /**
     * Set the second checkerboard color.
     */
    public void setColor2(Color c) {
        this.color2 = c;
        notifyChanged();
    }

    /**
     * Set both colors at once.
     */
    public void setColors(Color col1, Color col2) {
        this.color1 = col1;
        this.color2 = col2;
        notifyChanged();
    }

    /**
     * Get RGB value of first color (for settings persistence).
     */
    public int getColor1RGB() {
        return color1.getRGB();
    }

    /**
     * Get RGB value of second color (for settings persistence).
     */
    public int getColor2RGB() {
        return color2.getRGB();
    }

    // ─────────────────────────────────────────────────────────────
    // Callback
    // ─────────────────────────────────────────────────────────────

    /**
     * Set a callback for when colors change.
     */
    public void setOnColorsChanged(Runnable r) {
        this.onColorsChanged = r;
    }

    /**
     * Notify listeners that colors changed.
     */
    private void notifyChanged() {
        if (onColorsChanged != null) {
            onColorsChanged.run();
        }
    }
}
