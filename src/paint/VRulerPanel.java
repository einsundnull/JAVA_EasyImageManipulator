package paint;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Vertical ruler panel displayed to the left of the canvas.
 * Shows measurements in configurable units (px, mm, cm, inch).
 * In book mode, draws draggable margin handles for top/bottom margins (MS-Word style).
 */
public class VRulerPanel extends JPanel {
    private static final int RULER_THICK  = 20;
    private static final int HANDLE_HALF  = 5;
    private static final double MM_PER_PX = 25.4 / 96.0;

    private final RulerCallbacks callbacks;

    // ── Margin drag state ─────────────────────────────────────────────────────
    private int dragMargin = 0;  // 0=none, 1=top, 2=bottom
    private int dragStartY = 0;
    private int dragOrigMm = 0;

    public VRulerPanel(RulerCallbacks callbacks) {
        this.callbacks = callbacks;
        setPreferredSize(new Dimension(RULER_THICK, 0));
        setBackground(new Color(50, 50, 50));
        setOpaque(true);
        setCursor(Cursor.getDefaultCursor());

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!callbacks.isBookMode()) return;
                PageLayout pl = callbacks.getPageLayout();
                if (pl == null) return;
                int[] handles = marginScreenY(pl);
                if (Math.abs(e.getY() - handles[0]) <= HANDLE_HALF + 2) {
                    dragMargin = 1; dragStartY = e.getY(); dragOrigMm = pl.marginTop;
                } else if (Math.abs(e.getY() - handles[1]) <= HANDLE_HALF + 2) {
                    dragMargin = 2; dragStartY = e.getY(); dragOrigMm = pl.marginBottom;
                }
            }
            @Override public void mouseReleased(MouseEvent e) { dragMargin = 0; setCursor(Cursor.getDefaultCursor()); }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (!callbacks.isBookMode()) { setCursor(Cursor.getDefaultCursor()); return; }
                PageLayout pl = callbacks.getPageLayout();
                if (pl == null) return;
                int[] handles = marginScreenY(pl);
                boolean near = Math.abs(e.getY() - handles[0]) <= HANDLE_HALF + 2
                            || Math.abs(e.getY() - handles[1]) <= HANDLE_HALF + 2;
                setCursor(near ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
                               : Cursor.getDefaultCursor());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragMargin == 0) return;
                int dy = e.getY() - dragStartY;
                double zoom = callbacks.getZoom();
                int deltaMm = (int) Math.round(dy * MM_PER_PX / zoom);
                int newMm   = dragOrigMm + (dragMargin == 1 ? deltaMm : -deltaMm);
                callbacks.onMarginDragged(false, dragMargin == 1, Math.max(0, newMm));
                repaint();
            }
        });
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

        // ── Book-mode margin handles ──────────────────────────────────────────
        if (callbacks.isBookMode()) {
            PageLayout pl = callbacks.getPageLayout();
            if (pl != null) {
                int[] hy = marginScreenY(pl);
                drawMarginHandle(g2, hy[0], false); // top
                drawMarginHandle(g2, hy[1], true);  // bottom (flipped)

                if (dragMargin != 0) {
                    int mm = dragMargin == 1 ? pl.marginTop : pl.marginBottom;
                    int hyDrag = dragMargin == 1 ? hy[0] : hy[1];
                    g2.setColor(new Color(255, 220, 80));
                    g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                    Graphics2D gr = (Graphics2D) g2.create();
                    gr.translate(2, hyDrag - 2);
                    gr.rotate(-Math.PI / 2);
                    gr.drawString(mm + "mm", 0, 0);
                    gr.dispose();
                }
            }
        }

        // Right border line
        g2.setColor(AppColors.BORDER);
        g2.drawLine(RULER_THICK - 1, 0, RULER_THICK - 1, h);
    }

    /** Returns screen-y positions for top and bottom margin handles. */
    private int[] marginScreenY(PageLayout pl) {
        JScrollPane sp = callbacks.getScrollPane();
        if (sp == null) return new int[]{0, 0};
        double zoom = callbacks.getZoom();
        int canvasOffY = callbacks.getCanvasPanel().getY();
        Point vp = sp.getViewport().getViewPosition();
        int sT = (int) Math.round(pl.marginTopPx()    * zoom) - (vp.y - canvasOffY);
        BufferedImage wi = callbacks.getWorkingImage();
        int imgH = wi != null ? wi.getHeight() : 0;
        int sB = (int) Math.round((imgH - pl.marginBottomPx()) * zoom) - (vp.y - canvasOffY);
        return new int[]{ sT, sB };
    }

    /** Draws a right-pointing triangle at {@code sy} in the ruler. */
    private static void drawMarginHandle(Graphics2D g2, int sy, boolean flip) {
        int x0 = RULER_THICK - 2, x1 = 0;
        int[] xs = { x0, x0, x1 };
        int[] ys = flip
            ? new int[]{ sy, sy + HANDLE_HALF, sy }
            : new int[]{ sy, sy - HANDLE_HALF, sy };
        g2.setColor(new Color(0, 140, 255, 220));
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(0, 80, 180));
        g2.drawPolygon(xs, ys, 3);
    }

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
