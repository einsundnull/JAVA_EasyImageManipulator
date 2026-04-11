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
 * Vertical ruler panel displayed to the left of the canvas.
 * Shows measurements in configurable units (px, mm, cm, inch).
 */
public class VRulerPanel extends JPanel {
    private static final int RULER_THICK = 20;
    private final RulerCallbacks callbacks;

    public VRulerPanel(RulerCallbacks callbacks) {
        this.callbacks = callbacks;
        setPreferredSize(new Dimension(RULER_THICK, 0));
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

        int h = getHeight();
        Point viewPos = scrollPane.getViewport().getViewPosition();
        int canvasOffY = callbacks.getCanvasPanel().getY();

        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g2.setColor(new Color(180, 180, 180));

        RulerUnit rulerUnit = callbacks.getRulerUnit();
        double zoom = callbacks.getZoom();
        double pxPerUnit = rulerUnit.pxPerUnit();
        double imgPxPerTick = chooseTick(pxPerUnit, zoom);
        double screenPxPerTick = imgPxPerTick * zoom;
        if (screenPxPerTick < 4) return;

        double startImgY = (viewPos.y - canvasOffY) / zoom;
        double firstTick = Math.floor(startImgY / imgPxPerTick) * imgPxPerTick;

        for (double imgY = firstTick; imgY * zoom - (viewPos.y - canvasOffY) < h; imgY += imgPxPerTick) {
            int sy = (int) ((imgY * zoom) - (viewPos.y - canvasOffY));
            if (sy < 0) continue;
            g2.drawLine(RULER_THICK - 5, sy, RULER_THICK, sy);
            if (screenPxPerTick > 20) {
                double val = imgY / pxPerUnit;
                String label = (val == (long) val) ? String.valueOf((long) val)
                                                   : String.format("%.1f", val);
                Graphics2D gr = (Graphics2D) g2.create();
                gr.translate(9, sy - 2);
                gr.rotate(-Math.PI / 2);
                gr.drawString(label, 0, 0);
                gr.dispose();
            }
        }
        // Right border line
        g2.setColor(AppColors.BORDER);
        g2.drawLine(RULER_THICK - 1, 0, RULER_THICK - 1, h);
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
