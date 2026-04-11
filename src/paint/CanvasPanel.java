package paint;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.Dimension;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Canvas panel for drawing and image manipulation.
 * Handles all mouse input, painting, and selection/floating selection logic.
 * Extracted from SelectiveAlphaEditor as a standalone component.
 */
public class CanvasPanel extends JPanel {
    private final CanvasCallbacks callbacks;

    // Pan tracking
    private Point panStart = null;
    private Point panViewPos = null;

    public CanvasPanel(CanvasCallbacks callbacks) {
        this.callbacks = callbacks;
        setOpaque(false);
        setupMouseHandling();
        setupKeyBindings();
    }

    private void setupMouseHandling() {
        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                boolean isMiddle = (e.getButton() == MouseEvent.BUTTON2);
                boolean isCtrlDrg = SwingUtilities.isLeftMouseButton(e)
                        && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                if (isMiddle || isCtrlDrg) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    panStart = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
                            callbacks.getScrollPane().getViewport());
                    panViewPos = callbacks.getScrollPane().getViewport().getViewPosition();
                    return;
                }

                if (!SwingUtilities.isLeftMouseButton(e) || callbacks.getWorkingImage() == null) return;

                Point imgPt = callbacks.screenToImage(e.getPoint());

                if (!callbacks.getActiveElements().isEmpty() && callbacks.getFloatingImage() == null) {
                    if (callbacks.getSelectedElement() != null) {
                        Rectangle selScr = callbacks.elemRectScreen(callbacks.getSelectedElement());
                        Rectangle[] handles = callbacks.handleRects(selScr);
                        for (int hi = 0; hi < handles.length; hi++) {
                            if (handles[hi].contains(e.getPoint())) {
                                callbacks.setElemActiveHandle(hi);
                                callbacks.setElemScaleBase(new Rectangle(
                                        callbacks.getSelectedElement().x(), callbacks.getSelectedElement().y(),
                                        callbacks.getSelectedElement().width(), callbacks.getSelectedElement().height()));
                                callbacks.setElemScaleStart(e.getPoint());
                                return;
                            }
                        }
                        if (selScr.contains(e.getPoint())) {
                            callbacks.setDraggingElement(true);
                            callbacks.setElemDragAnchor(new Point(imgPt.x - callbacks.getSelectedElement().x(),
                                    imgPt.y - callbacks.getSelectedElement().y()));
                            return;
                        }
                    }

                    Element hit = null;
                    for (int i = callbacks.getActiveElements().size() - 1; i >= 0; i--) {
                        if (callbacks.elemRectScreen(callbacks.getActiveElements().get(i)).contains(e.getPoint())) {
                            hit = callbacks.getActiveElements().get(i);
                            break;
                        }
                    }
                    if (hit != null) {
                        callbacks.setSelectedElement(hit);
                        callbacks.setDraggingElement(true);
                        callbacks.setElemDragAnchor(new Point(imgPt.x - hit.x(), imgPt.y - hit.y()));
                        repaint();
                        return;
                    } else {
                        callbacks.setSelectedElement(null);
                        repaint();
                    }
                }

                if (callbacks.getFloatingImage() != null) {
                    int h = callbacks.hitHandle(e.getPoint());
                    if (h >= 0) {
                        callbacks.setActiveHandle(h);
                        callbacks.setScaleBaseRect(new Rectangle(callbacks.getFloatRect()));
                        callbacks.setScaleDragStart(e.getPoint());
                        return;
                    } else if (callbacks.floatRectScreen().contains(e.getPoint())) {
                        callbacks.setDraggingFloat(true);
                        callbacks.setFloatDragAnchor(new Point(imgPt.x - callbacks.getFloatRect().x,
                                imgPt.y - callbacks.getFloatRect().y));
                        return;
                    } else {
                        callbacks.commitFloat();
                    }
                }

                if (callbacks.getAppMode() == AppMode.PAINT) {
                    PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
                    switch (tool) {
                        case PENCIL, ERASER -> {
                            callbacks.pushUndo();
                            callbacks.setLastPaintPoint(imgPt);
                            callbacks.paintDot(imgPt);
                        }
                        case FLOODFILL -> {
                            callbacks.pushUndo();
                            PaintEngine.floodFill(callbacks.getWorkingImage(), imgPt.x, imgPt.y,
                                    callbacks.getPaintToolbar().getPrimaryColor(), 30);
                            callbacks.markDirty();
                        }
                        case EYEDROPPER -> {
                            Color picked = PaintEngine.pickColor(callbacks.getWorkingImage(), imgPt.x, imgPt.y);
                            callbacks.getPaintToolbar().setSelectedColor(picked);
                        }
                        case LINE, CIRCLE, RECT -> {
                            callbacks.pushUndo();
                            callbacks.setShapeStartPoint(imgPt);
                            callbacks.setPaintSnapshot(callbacks.deepCopy(callbacks.getWorkingImage()));
                        }
                        case SELECT -> {
                            if (!callbacks.getSelectedAreas().isEmpty()) {
                                Rectangle sel = callbacks.getSelectedAreas().get(callbacks.getSelectedAreas().size() - 1);
                                if (sel.contains(imgPt)) {
                                    callbacks.pushUndo();
                                    callbacks.commitFloat();
                                } else {
                                    callbacks.setShapeStartPoint(imgPt);
                                }
                            } else {
                                callbacks.setShapeStartPoint(imgPt);
                            }
                        }
                    }
                } else {
                    if (callbacks.isFloodfillMode()) {
                        callbacks.pushUndo();
                        callbacks.performFloodfill(e.getPoint());
                    } else {
                        // Start a new selection (Shift adds to existing)
                        callbacks.setSelecting(true);
                        callbacks.setSelectionStart(imgPt);
                        callbacks.setSelectionEnd(imgPt);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panStart != null) {
                    Point curVp = SwingUtilities.convertPoint(CanvasPanel.this, e.getPoint(),
                            callbacks.getScrollPane().getViewport());
                    int dx = panStart.x - curVp.x;
                    int dy = panStart.y - curVp.y;
                    int newX = Math.max(0, panViewPos.x + dx);
                    int newY = Math.max(0, panViewPos.y + dy);
                    callbacks.getScrollPane().getViewport().setViewPosition(new Point(newX, newY));
                    return;
                }

                if (callbacks.getWorkingImage() == null) return;
                Point imgPt = callbacks.screenToImage(e.getPoint());

                if (callbacks.isDraggingElement()) {
                    Element el = callbacks.getSelectedElement();
                    if (el != null) {
                        Element updated = el.withPosition(
                                imgPt.x - callbacks.getElemDragAnchor().x,
                                imgPt.y - callbacks.getElemDragAnchor().y);
                        callbacks.updateSelectedElement(updated);
                        repaint();
                    }
                    return;
                }

                if (callbacks.getElemActiveHandle() >= 0) {
                    Rectangle r = callbacks.getElemScaleBase();
                    double origW = r.width, origH = r.height;
                    double origX = r.x, origY = r.y;
                    double dx = (e.getPoint().x - callbacks.getElemScaleStart().x) / callbacks.getZoom();
                    double dy = (e.getPoint().y - callbacks.getElemScaleStart().y) / callbacks.getZoom();

                    Element el = callbacks.getSelectedElement();
                    int handle = callbacks.getElemActiveHandle();

                    double nw, nh, nx, ny;
                    if (handle == 0) {
                        nw = Math.max(1, origW - dx);
                        nh = Math.max(1, origH * nw / origW);
                        nx = origX + origW - nw;
                        ny = origY + origH - nh;
                    } else if (handle == 2) {
                        nw = Math.max(1, origW + dx);
                        nh = Math.max(1, origH * nw / origW);
                        nx = origX;
                        ny = origY + origH - nh;
                    } else if (handle == 5) {
                        nw = Math.max(1, origW - dx);
                        nh = Math.max(1, origH * nw / origW);
                        nx = origX + origW - nw;
                        ny = origY;
                    } else if (handle == 7) {
                        nw = Math.max(1, origW + dx);
                        nh = Math.max(1, origH * nw / origW);
                        nx = origX;
                        ny = origY;
                    } else if (handle == 1 || handle == 6) {
                        nh = handle == 1 ? Math.max(1, origH - dy) : Math.max(1, origH + dy);
                        nw = origW;
                        nx = origX;
                        ny = handle == 1 ? origY + origH - nh : origY;
                    } else if (handle == 3 || handle == 4) {
                        nw = handle == 3 ? Math.max(1, origW - dx) : Math.max(1, origW + dx);
                        nh = origH;
                        nx = handle == 3 ? origX + origW - nw : origX;
                        ny = origY;
                    } else {
                        return;
                    }

                    Element updated = el.withBounds((int) nx, (int) ny, (int) nw, (int) nh);
                    callbacks.updateSelectedElement(updated);
                    repaint();
                    return;
                }

                if (callbacks.isDraggingFloat()) {
                    Rectangle fr = callbacks.getFloatRect();
                    fr.x = (int) (imgPt.x - callbacks.getFloatDragAnchor().x);
                    fr.y = (int) (imgPt.y - callbacks.getFloatDragAnchor().y);
                    callbacks.repaintCanvas();
                    return;
                }

                if (callbacks.getActiveHandle() >= 0) {
                    Rectangle newR = computeNewFloatRect(callbacks.getActiveHandle(), callbacks.getScaleBaseRect(),
                            callbacks.getScaleDragStart(), e.getPoint());
                    if (newR != null) {
                        Rectangle fr = callbacks.getFloatRect();
                        fr.x = newR.x;
                        fr.y = newR.y;
                        fr.width = newR.width;
                        fr.height = newR.height;
                        callbacks.repaintCanvas();
                    }
                    return;
                }

                if (callbacks.getAppMode() == AppMode.PAINT) {
                    PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
                    if (tool == PaintEngine.Tool.PENCIL || tool == PaintEngine.Tool.ERASER) {
                        boolean aa = callbacks.getPaintToolbar().isAntialiasing();
                        if (tool == PaintEngine.Tool.PENCIL) {
                            PaintEngine.drawPencil(callbacks.getWorkingImage(),
                                    callbacks.getLastPaintPoint(), imgPt,
                                    callbacks.getPaintToolbar().getPrimaryColor(),
                                    callbacks.getPaintToolbar().getStrokeWidth(),
                                    callbacks.getPaintToolbar().getBrushShape(), aa);
                        } else {
                            PaintEngine.drawEraser(callbacks.getWorkingImage(),
                                    callbacks.getLastPaintPoint(), imgPt,
                                    callbacks.getPaintToolbar().getStrokeWidth(), aa);
                        }
                        callbacks.setLastPaintPoint(imgPt);
                        callbacks.markDirty();
                    } else if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE || tool == PaintEngine.Tool.RECT) {
                        if (callbacks.getPaintSnapshot() != null && callbacks.getWorkingImage() != null) {
                            // Restore original image, then draw preview
                            copyInto(callbacks.getPaintSnapshot(), callbacks.getWorkingImage());
                            drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
                            callbacks.markDirty();
                        }
                        callbacks.repaintCanvas();
                    }
                } else {
                    callbacks.setSelectionEnd(imgPt);
                    callbacks.repaintCanvas();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panStart = null;
                panViewPos = null;

                if (callbacks.getWorkingImage() == null) return;

                callbacks.setDraggingElement(false);
                callbacks.setElemActiveHandle(-1);

                if (callbacks.isDraggingFloat()) {
                    callbacks.setDraggingFloat(false);
                    callbacks.repaintCanvas();
                    return;
                }

                if (callbacks.getActiveHandle() >= 0) {
                    callbacks.setActiveHandle(-1);
                    callbacks.setScaleBaseRect(null);
                    callbacks.setScaleDragStart(null);
                    callbacks.repaintCanvas();
                    return;
                }

                if (callbacks.getAppMode() == AppMode.PAINT) {
                    PaintEngine.Tool tool = callbacks.getPaintToolbar().getActiveTool();
                    if (tool == PaintEngine.Tool.LINE || tool == PaintEngine.Tool.CIRCLE || tool == PaintEngine.Tool.RECT) {
                        Point imgPt = callbacks.screenToImage(e.getPoint());
                        drawShape(tool, callbacks.getShapeStartPoint(), imgPt);
                        callbacks.setShapeStartPoint(null);
                        callbacks.setPaintSnapshot(null);
                        callbacks.markDirty();
                    }
                } else {
                    if (callbacks.isSelecting()) {
                        callbacks.setSelecting(false);
                        Point start = callbacks.getSelectionStart();
                        Point end = callbacks.getSelectionEnd();
                        if (start != null && end != null) {
                            int x = Math.min(start.x, end.x);
                            int y = Math.min(start.y, end.y);
                            int w = Math.abs(end.x - start.x);
                            int h = Math.abs(end.y - start.y);
                            if (w > 0 && h > 0) {
                                Rectangle sel = new Rectangle(x, y, w, h);
                                callbacks.getSelectedAreas().add(sel);
                                callbacks.repaintCanvas();
                            }
                        }
                    }
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    double zoomStep = 0.06;
                    double newZoom = callbacks.getZoom() - (e.getWheelRotation() * zoomStep);
                    callbacks.repaintCanvas();
                } else if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                    callbacks.getScrollPane().getHorizontalScrollBar()
                            .setValue(callbacks.getScrollPane().getHorizontalScrollBar().getValue() +
                                    e.getWheelRotation() * 16);
                } else {
                    callbacks.getScrollPane().getVerticalScrollBar()
                            .setValue(callbacks.getScrollPane().getVerticalScrollBar().getValue() +
                                    e.getWheelRotation() * 16);
                }
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    private void setupKeyBindings() {
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (callbacks.getFloatingImage() != null) {
                    callbacks.commitFloat();
                } else if (callbacks.isSelecting()) {
                    callbacks.setSelecting(false);
                    callbacks.repaintCanvas();
                }
            }
        });

        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commit");
        getActionMap().put("commit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (callbacks.getFloatingImage() != null) {
                    callbacks.commitFloat();
                }
            }
        });

        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (callbacks.getFloatingImage() != null) {
                    // cancelFloat() would need to be exposed
                }
            }
        });
    }

    private void copyInto(BufferedImage src, BufferedImage dst) {
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(java.awt.AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
    }

    private void drawShapePreview(BufferedImage img, PaintEngine.Tool tool, Point from, Point to) {
        Graphics2D g2 = img.createGraphics();
        g2.setColor(callbacks.getPaintToolbar().getPrimaryColor());
        g2.setStroke(new BasicStroke(callbacks.getPaintToolbar().getStrokeWidth()));

        int x = Math.min(from.x, to.x);
        int y = Math.min(from.y, to.y);
        int w = Math.abs(to.x - from.x);
        int h = Math.abs(to.y - from.y);

        if (tool == PaintEngine.Tool.LINE) {
            g2.drawLine(from.x, from.y, to.x, to.y);
        } else if (tool == PaintEngine.Tool.CIRCLE) {
            g2.drawOval(x, y, w, h);
        } else if (tool == PaintEngine.Tool.RECT) {
            g2.drawRect(x, y, w, h);
        }
        g2.dispose();
    }

    private void drawShape(PaintEngine.Tool tool, Point from, Point to) {
        if (callbacks.getWorkingImage() == null) return;

        int x = Math.min(from.x, to.x);
        int y = Math.min(from.y, to.y);
        int w = Math.abs(to.x - from.x);
        int h = Math.abs(to.y - from.y);

        boolean aa = callbacks.getPaintToolbar().isAntialiasing();
        if (tool == PaintEngine.Tool.LINE) {
            PaintEngine.drawLine(callbacks.getWorkingImage(), from, to,
                    callbacks.getPaintToolbar().getPrimaryColor(),
                    callbacks.getPaintToolbar().getStrokeWidth(), aa);
        } else if (tool == PaintEngine.Tool.CIRCLE) {
            PaintEngine.drawCircle(callbacks.getWorkingImage(), from, to,
                    callbacks.getPaintToolbar().getPrimaryColor(),
                    callbacks.getPaintToolbar().getStrokeWidth(),
                    callbacks.getPaintToolbar().getFillMode(),
                    callbacks.getPaintToolbar().getSecondaryColor(), aa);
        } else if (tool == PaintEngine.Tool.RECT) {
            PaintEngine.drawRect(callbacks.getWorkingImage(), from, to,
                    callbacks.getPaintToolbar().getPrimaryColor(),
                    callbacks.getPaintToolbar().getStrokeWidth(),
                    callbacks.getPaintToolbar().getFillMode(),
                    callbacks.getPaintToolbar().getSecondaryColor(), aa);
        }
    }

    private Rectangle computeNewFloatRect(int handle, Rectangle base, Point origin, Point current) {
        double dx = (current.x - origin.x) / callbacks.getZoom();
        double dy = (current.y - origin.y) / callbacks.getZoom();
        double bx = base.x, by = base.y, bw = base.width, bh = base.height;
        final double MIN = 1.0;

        double rx, ry, rw, rh;
        switch (handle) {
            case 0 -> {
                rw = Math.max(MIN, bw - dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx + bw - rw;
                ry = by + bh - rh;
            }
            case 1 -> {
                rh = Math.max(MIN, bh - dy);
                rx = bx;
                rw = bw;
                ry = by + bh - rh;
            }
            case 2 -> {
                rw = Math.max(MIN, bw + dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx;
                ry = by + bh - rh;
            }
            case 3 -> {
                rw = Math.max(MIN, bw - dx);
                rx = bx + bw - rw;
                ry = by;
                rh = bh;
            }
            case 4 -> {
                rw = Math.max(MIN, bw + dx);
                rx = bx;
                ry = by;
                rh = bh;
            }
            case 5 -> {
                rw = Math.max(MIN, bw - dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx + bw - rw;
                ry = by;
            }
            case 6 -> {
                rh = Math.max(MIN, bh + dy);
                rx = bx;
                rw = bw;
                ry = by;
            }
            default -> {
                rw = Math.max(MIN, bw + dx);
                rh = Math.max(MIN, bh * rw / bw);
                rx = bx;
                ry = by;
            }
        }

        return new Rectangle((int) Math.round(rx), (int) Math.round(ry),
                (int) Math.round(rw), (int) Math.round(rh));
    }

    @Override
    public Dimension getPreferredSize() {
        if (callbacks.getWorkingImage() == null) return new Dimension(1, 1);
        return new Dimension(
                (int) Math.ceil(callbacks.getWorkingImage().getWidth()  * callbacks.getZoom()),
                (int) Math.ceil(callbacks.getWorkingImage().getHeight() * callbacks.getZoom()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (callbacks.getWorkingImage() == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cw = (int) Math.round(callbacks.getWorkingImage().getWidth() * callbacks.getZoom());
        int ch = (int) Math.round(callbacks.getWorkingImage().getHeight() * callbacks.getZoom());
        g2.drawImage(callbacks.getWorkingImage(), 0, 0, cw, ch, null);

        // Selections overlay
        g2.setColor(new Color(255, 0, 0, 70));
        for (Rectangle r : callbacks.getSelectedAreas()) {
            int sx = (int) Math.round(r.x * callbacks.getZoom());
            int sy = (int) Math.round(r.y * callbacks.getZoom());
            int sw = (int) Math.round(r.width * callbacks.getZoom());
            int sh = (int) Math.round(r.height * callbacks.getZoom());
            g2.fillRect(sx, sy, sw, sh);
        }

        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1.2f));
        for (Rectangle r : callbacks.getSelectedAreas()) {
            int sx = (int) Math.round(r.x * callbacks.getZoom());
            int sy = (int) Math.round(r.y * callbacks.getZoom());
            int sw = (int) Math.round(r.width * callbacks.getZoom());
            int sh = (int) Math.round(r.height * callbacks.getZoom());
            g2.drawRect(sx, sy, sw, sh);
        }

        // Active selection being drawn (during drag)
        if (callbacks.isSelecting() && callbacks.getSelectionStart() != null && callbacks.getSelectionEnd() != null) {
            int x = Math.min(callbacks.getSelectionStart().x, callbacks.getSelectionEnd().x);
            int y = Math.min(callbacks.getSelectionStart().y, callbacks.getSelectionEnd().y);
            int w = Math.abs(callbacks.getSelectionEnd().x - callbacks.getSelectionStart().x);
            int h = Math.abs(callbacks.getSelectionEnd().y - callbacks.getSelectionStart().y);

            g2.setColor(new Color(0, 200, 255, 60));
            g2.fillRect((int) Math.round(x * callbacks.getZoom()),
                       (int) Math.round(y * callbacks.getZoom()),
                       (int) Math.round(w * callbacks.getZoom()),
                       (int) Math.round(h * callbacks.getZoom()));
            g2.setColor(new Color(0, 200, 255));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect((int) Math.round(x * callbacks.getZoom()),
                       (int) Math.round(y * callbacks.getZoom()),
                       (int) Math.round(w * callbacks.getZoom()),
                       (int) Math.round(h * callbacks.getZoom()));
        }

        // Floating selection with handles
        if (callbacks.getFloatingImage() != null && callbacks.getFloatRect() != null) {
            Rectangle fr = callbacks.getFloatRect();
            int fx = (int) Math.round(fr.x * callbacks.getZoom());
            int fy = (int) Math.round(fr.y * callbacks.getZoom());
            int fw = (int) Math.round(fr.width * callbacks.getZoom());
            int fh = (int) Math.round(fr.height * callbacks.getZoom());
            g2.drawImage(callbacks.getFloatingImage(), fx, fy, fw, fh, null);

            float[] dash = { 6f, 4f };
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
            g2.drawRect(fx, fy, fw, fh);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 6f));
            g2.drawRect(fx, fy, fw, fh);

            g2.setStroke(new BasicStroke(1f));
            for (Rectangle hr : callbacks.handleRects(new Rectangle(fx, fy, fw, fh))) {
                g2.setColor(Color.WHITE);
                g2.fillRect(hr.x, hr.y, hr.width, hr.height);
                g2.setColor(AppColors.ACCENT);
                g2.drawRect(hr.x, hr.y, hr.width, hr.height);
            }
        }
    }
}
