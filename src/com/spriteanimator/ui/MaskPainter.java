package com.spriteanimator.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import com.spriteanimator.model.AppState;
import com.spriteanimator.model.Tile;

/**
 * Zoomable mask-painting canvas with:
 * - Left-drag  = paint active tile
 * - Right-drag = erase  (also active when Tool.ERASER is selected)
 * - Scroll     = zoom in/out
 * - Image drop = drop PNG/GIF/JPG directly onto canvas to load
 */
public class MaskPainter extends JPanel {

    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 16;

    private final AppState state;
    private int   zoom = 4;
    private float maskOpacity = 0.55f;
    private Point lastPaint;

    public MaskPainter(AppState state) {
        this.state = state;
        setBackground(new Color(40, 40, 40));

        // ── Mouse painting ────────────────────────────────────────────────────
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) { handlePaint(e); }
            @Override public void mousePressed(MouseEvent e) { lastPaint = null; handlePaint(e); }
            @Override public void mouseReleased(MouseEvent e) { lastPaint = null; state.fireChanged(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // ── Zoom on scroll ────────────────────────────────────────────────────
        addMouseWheelListener(e -> {
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom - e.getWheelRotation()));
            revalidate();
            repaint();
        });

        // ── Image drop ────────────────────────────────────────────────────────
        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport s) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) return false;
                    BufferedImage img = ImageIO.read(files.get(0));
                    if (img == null) return false;
                    BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(),
                                                           BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = argb.createGraphics();
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                    state.setSpriteImage(argb);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });

        new DropTarget(this, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                dtde.dropComplete(getTransferHandler().importData(
                    new TransferHandler.TransferSupport(MaskPainter.this, dtde.getTransferable())));
            }
        });

        state.addListener(this::repaint);
    }

    // ── Paint handler ─────────────────────────────────────────────────────────

    private void handlePaint(MouseEvent e) {
        BufferedImage mask   = state.getMaskImage();
        BufferedImage sprite = state.getSpriteImage();
        if (mask == null || sprite == null) return;

        Point origin = getSpriteOrigin();
        int sx = (e.getX() - origin.x) / zoom;
        int sy = (e.getY() - origin.y) / zoom;

        boolean erase = SwingUtilities.isRightMouseButton(e)
                     || state.getActiveTool() == AppState.Tool.ERASER;
        Tile active = state.getActiveTile();
        if (!erase && active == null) return;

        int brush = state.getBrushSize();
        Graphics2D mg = mask.createGraphics();
        mg.setComposite(erase
            ? AlphaComposite.getInstance(AlphaComposite.CLEAR)
            : AlphaComposite.getInstance(AlphaComposite.SRC));
        mg.setColor(erase ? new Color(0, 0, 0, 0) : active.getMaskColor());

        if (lastPaint != null) {
            drawBrushLine(mg, lastPaint.x, lastPaint.y, sx, sy, brush);
        } else {
            int h = brush / 2;
            mg.fillRect(sx - h, sy - h, brush, brush);
        }
        mg.dispose();
        lastPaint = new Point(sx, sy);
        repaint();
    }

    private void drawBrushLine(Graphics2D g, int x0, int y0, int x1, int y1, int brush) {
        int half  = brush / 2;
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) { g.fillRect(x0 - half, y0 - half, brush, brush); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            g.fillRect(x - half, y - half, brush, brush);
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        BufferedImage sprite = state.getSpriteImage();
        if (sprite == null) {
            g.setColor(new Color(100, 100, 100));
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("Sprite hier ablegen oder Strg+O zum Öffnen", 20, getHeight() / 2);
            // draw dashed drop-zone border
            g.setColor(new Color(100, 100, 100, 120));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                        10, new float[]{ 8, 6 }, 0));
            g.drawRect(20, 20, getWidth() - 40, getHeight() - 40);
            return;
        }

        Point origin = getSpriteOrigin();
        int dw = sprite.getWidth()  * zoom;
        int dh = sprite.getHeight() * zoom;

        drawCheckerboard(g, origin.x, origin.y, dw, dh);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(sprite, origin.x, origin.y, dw, dh, null);

        BufferedImage mask = state.getMaskImage();
        if (mask != null && maskOpacity > 0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, maskOpacity));
            g.drawImage(mask, origin.x, origin.y, dw, dh, null);
            g.setComposite(AlphaComposite.SrcOver);
        }

        drawBrushCursor(g);
    }

    private void drawCheckerboard(Graphics2D g, int x, int y, int w, int h) {
        int t = 8;
        for (int ty = 0; ty < h; ty += t)
            for (int tx = 0; tx < w; tx += t) {
                boolean light = ((tx / t + ty / t) % 2 == 0);
                g.setColor(light ? new Color(180, 180, 180) : new Color(120, 120, 120));
                g.fillRect(x + tx, y + ty, Math.min(t, w - tx), Math.min(t, h - ty));
            }
    }

    private void drawBrushCursor(Graphics2D g) {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        Point mp = pi.getLocation();
        SwingUtilities.convertPointFromScreen(mp, this);
        int px = state.getBrushSize() * zoom;
        int h  = px / 2;
        boolean erasing = state.getActiveTool() == AppState.Tool.ERASER;
        g.setColor(erasing ? new Color(255, 80, 80, 200) : new Color(255, 255, 0, 180));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(mp.x - h, mp.y - h, px, px);
        if (erasing) {
            g.drawLine(mp.x - h + 2, mp.y - h + 2, mp.x + h - 2, mp.y + h - 2);
            g.drawLine(mp.x + h - 2, mp.y - h + 2, mp.x - h + 2, mp.y + h - 2);
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private Point getSpriteOrigin() {
        BufferedImage s = state.getSpriteImage();
        if (s == null) return new Point(0, 0);
        return new Point(
            Math.max(0, (getWidth()  - s.getWidth()  * zoom) / 2),
            Math.max(0, (getHeight() - s.getHeight() * zoom) / 2));
    }

    @Override
    public Dimension getPreferredSize() {
        BufferedImage s = state.getSpriteImage();
        if (s == null) return new Dimension(512, 512);
        return new Dimension(s.getWidth() * zoom + 40, s.getHeight() * zoom + 40);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public float getMaskOpacity()        { return maskOpacity; }
    public void  setMaskOpacity(float v) { maskOpacity = v; repaint(); }
    public int   getZoom()               { return zoom; }
    public void  setZoom(int z)          { zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z)); revalidate(); repaint(); }

    public void clearMask() {
        BufferedImage mask = state.getMaskImage();
        if (mask == null) return;
        Graphics2D mg = mask.createGraphics();
        mg.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
        mg.fillRect(0, 0, mask.getWidth(), mask.getHeight());
        mg.dispose();
        state.fireChanged();
        repaint();
    }
}
