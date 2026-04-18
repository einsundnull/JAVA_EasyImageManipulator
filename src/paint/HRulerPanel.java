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
 * Horizontal ruler panel displayed above the canvas.
 * Shows measurements in configurable units (px, mm, cm, inch).
 * In book mode, draws draggable margin handles for left/right margins (MS-Word style).
 */
public class HRulerPanel extends JPanel {
    private static final int RULER_THICK  = 20;
    private static final int HANDLE_HALF  = 5; // half-width of the margin triangle handle
    private static final double MM_PER_PX = 25.4 / 96.0;

    private final RulerCallbacks callbacks;

    // ── Margin drag state ─────────────────────────────────────────────────────
    private int  dragMargin  = 0;  // 0=none, 1=left, 2=right
    private int  dragStartX  = 0;
    private int  dragOrigMm  = 0;

    public HRulerPanel(RulerCallbacks callbacks) {
        this.callbacks = callbacks;
        setPreferredSize(new Dimension(0, RULER_THICK));
        setBackground(new Color(50, 50, 50));
        setOpaque(true);
        setCursor(Cursor.getDefaultCursor());

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!callbacks.isBookMode()) return;
                PageLayout pl = callbacks.getPageLayout();
                if (pl == null) return;
                int[] handles = marginScreenX(pl);
                if (Math.abs(e.getX() - handles[0]) <= HANDLE_HALF + 2) {
                    dragMargin = 1; dragStartX = e.getX(); dragOrigMm = pl.marginLeft;
                } else if (Math.abs(e.getX() - handles[1]) <= HANDLE_HALF + 2) {
                    dragMargin = 2; dragStartX = e.getX(); dragOrigMm = pl.marginRight;
                }
            }
            @Override public void mouseReleased(MouseEvent e) { dragMargin = 0; setCursor(Cursor.getDefaultCursor()); }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (!callbacks.isBookMode()) { setCursor(Cursor.getDefaultCursor()); return; }
                PageLayout pl = callbacks.getPageLayout();
                if (pl == null) return;
                int[] handles = marginScreenX(pl);
                boolean near = Math.abs(e.getX() - handles[0]) <= HANDLE_HALF + 2
                            || Math.abs(e.getX() - handles[1]) <= HANDLE_HALF + 2;
                setCursor(near ? Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
                               : Cursor.getDefaultCursor());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragMargin == 0) return;
                int dx = e.getX() - dragStartX;
                double zoom = callbacks.getZoom();
                int deltaMm = (int) Math.round(dx * MM_PER_PX / zoom);
                int newMm   = dragOrigMm + (dragMargin == 1 ? deltaMm : -deltaMm);
                callbacks.onMarginDragged(true, dragMargin == 1, Math.max(0, newMm));
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

        // ── Book-mode margin handles ──────────────────────────────────────────
        if (callbacks.isBookMode()) {
            PageLayout pl = callbacks.getPageLayout();
            if (pl != null) {
                int[] hx = marginScreenX(pl);
                drawMarginHandle(g2, hx[0], false); // left
                drawMarginHandle(g2, hx[1], true);  // right (flipped)

                // Tooltip-style value during drag
                if (dragMargin != 0) {
                    int mm = dragMargin == 1 ? pl.marginLeft : pl.marginRight;
                    String txt = mm + "mm";
                    int hxDrag = dragMargin == 1 ? hx[0] : hx[1];
                    g2.setColor(new Color(255, 220, 80));
                    g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                    g2.drawString(txt, hxDrag + 4, 9);
                }
            }
        }

        // Bottom border line
        g2.setColor(AppColors.BORDER);
        g2.drawLine(0, RULER_THICK - 1, w, RULER_THICK - 1);
    }

    /** Returns screen-x positions for left and right margin handles. */
    private int[] marginScreenX(PageLayout pl) {
        JScrollPane sp = callbacks.getScrollPane();
        if (sp == null) return new int[]{0, 0};
        double zoom = callbacks.getZoom();
        int canvasOffX = callbacks.getCanvasPanel().getX();
        Point vp = sp.getViewport().getViewPosition();
        int sL = (int) Math.round(pl.marginLeftPx()  * zoom) - (vp.x - canvasOffX);
        BufferedImage wi = callbacks.getWorkingImage();
        int imgW = wi != null ? wi.getWidth() : 0;
        int sR = (int) Math.round((imgW - pl.marginRightPx()) * zoom) - (vp.x - canvasOffX);
        return new int[]{ sL, sR };
    }

    /** Draws a downward-pointing triangle at {@code sx} in the ruler. */
    private static void drawMarginHandle(Graphics2D g2, int sx, boolean flip) {
        int y0 = 0, y1 = RULER_THICK - 2;
        int[] xs = flip
            ? new int[]{ sx, sx - HANDLE_HALF, sx }
            : new int[]{ sx, sx + HANDLE_HALF, sx };
        int[] ys = { y0, y0, y1 };
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
