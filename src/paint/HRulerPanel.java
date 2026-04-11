package paint;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Horizontal ruler panel displayed above the canvas.
 * Shows measurements in configurable units (px, mm, cm, inch).
 */
public class HRulerPanel extends JPanel {
    private static final int RULER_THICK = 20;
    private final RulerCallbacks callbacks;

    public HRulerPanel(RulerCallbacks callbacks) {
        this.callbacks = callbacks;
        setPreferredSize(new Dimension(0, RULER_THICK));
        setBackground(new Color(50, 50, 50));
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage workingImage = callbacks.getWorkingImage();
        JScrollPane scrollPane = callbacks.getScrollPane();
        if (workingImage == null || scrollPane == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        Point viewPos = scrollPane.getViewport().getViewPosition();
        int canvasOffX = callbacks.getCanvasPanel().getX();

        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g2.setColor(new Color(180, 180, 180));

        RulerUnit rulerUnit = callbacks.getRulerUnit();
        double zoom = callbacks.getZoom();
        double pxPerUnit = rulerUnit.pxPerUnit();
        double imgPxPerTick = chooseTick(pxPerUnit, zoom);
        double screenPxPerTick = imgPxPerTick * zoom;
        if (screenPxPerTick < 4) return;

        double startImgX = (viewPos.x - canvasOffX) / zoom;
        double firstTick = Math.floor(startImgX / imgPxPerTick) * imgPxPerTick;

        for (double imgX = firstTick; imgX * zoom - (viewPos.x - canvasOffX) < w; imgX += imgPxPerTick) {
            int sx = (int) ((imgX * zoom) - (viewPos.x - canvasOffX));
            if (sx < 0) continue;
            g2.drawLine(sx, RULER_THICK - 5, sx, RULER_THICK);
            if (screenPxPerTick > 20) {
                double val = imgX / pxPerUnit;
                String label = (val == (long) val) ? String.valueOf((long) val)
                                                   : String.format("%.1f", val);
                g2.drawString(label + rulerUnit.label(), sx + 2, 9);
            }
        }
        // Bottom border line
        g2.setColor(AppColors.BORDER);
        g2.drawLine(0, RULER_THICK - 1, w, RULER_THICK - 1);
    }

    /**
     * Chooses a sensible tick interval in image-pixels so ticks are
     * not too crowded (min ~30 screen px apart) and snap to nice values.
     */
    private static double chooseTick(double pxPerUnit, double zoom) {
        double minScreenPx = 30.0;
        double imgPxPerTick = minScreenPx / zoom;
        double unitsPerTick = imgPxPerTick / pxPerUnit;
        double nice = Math.pow(10, Math.ceil(Math.log10(unitsPerTick)));
        if (unitsPerTick <= nice / 5) nice /= 5;
        else if (unitsPerTick <= nice / 2) nice /= 2;
        return nice * pxPerUnit;
    }
}
