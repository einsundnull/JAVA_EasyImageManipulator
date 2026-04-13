package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/**
 * Photoshop-style floating color picker popup.
 *
 * Layout:
 *   +----------------------------------+
 *   |  [SV-Square]  [Hue-Strip]        |
 *   |  Alpha ──────────────────── [  ] |
 *   |  Hex: #RRGGBBAA    [Preview]     |
 *   +----------------------------------+
 *
 * Owned by a parent JComponent; shown as undecorated JWindow near that component.
 */
public class ColorPickerPopup extends JWindow {
    // ── Constants ────────────────────────────────────────────────────────────
    private static final int SV_SIZE   = 180;
    private static final int HUE_W     = 18;
    private static final int HUE_H     = SV_SIZE;
    private static final int PADDING   = 10;
    private static final int PREVIEW_S = 28;

    // ── State ─────────────────────────────────────────────────────────────────
    private float hue = 0f, sat = 1f, val = 1f;
    private int   alpha = 255;

    // ── Cached gradient images ────────────────────────────────────────────────
    private BufferedImage svImage;    // SV square for current hue
    private BufferedImage hueImage;   // Hue strip
    private BufferedImage alphaImage; // Alpha strip (updated with current color)

    // ── Sub-panels ────────────────────────────────────────────────────────────
    private SVPanel    svPanel;
    private HuePanel   huePanel;
    private AlphaPanel alphaPanel;
    private JLabel     previewLabel;
    private JTextField hexField;
    private JLabel     alphaValLabel;

    // ── Callback ──────────────────────────────────────────────────────────────
    private ChangeListener changeListener;

    // ── Drag state (for dragging the popup itself) ────────────────────────────
    private Point  dragStart    = null;
    private boolean isDragging  = false;

    // =========================================================================
    public ColorPickerPopup(Window owner) {
        super(owner);
        // setAlwaysOnTop works for most cases; for fullscreen we also set
        // the WINDOW_ALWAYS_ON_TOP hint which is respected by more WMs.
        setAlwaysOnTop(true);
        try {
            Toolkit.getDefaultToolkit().setDynamicLayout(true);
        } catch (Exception ignored) {}
        buildUI();
        pack();
    }

    // =========================================================================
    // Public API
    // =========================================================================
    public Color getSelectedColor() {
        Color rgb = Color.getHSBColor(hue, sat, val);
        return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), alpha);
    }

    public void setSelectedColor(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue   = hsb[0];
        sat   = hsb[1];
        val   = hsb[2];
        alpha = c.getAlpha();
        svImage = null; // invalidate cache
        repaintAll();
        syncHexField();
    }

    public void setChangeListener(ChangeListener l) {
        this.changeListener = l;
    }

    /** Show the popup anchored below (or above) the given screen point. */
    public void showAt(int screenX, int screenY) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = getWidth(), h = getHeight();
        int x = Math.min(screenX, screen.width  - w - 4);
        int y = screenY;
        if (y + h > screen.height - 40) y = screenY - h - 4;
        setLocation(x, y);
        setVisible(true);
    }

    // =========================================================================
    // UI construction
    // =========================================================================
    private void buildUI() {
        JPanel root = new JPanel(null);
        root.setBackground(AppColors.BG_PANEL);
        root.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));

        int totalW = PADDING + SV_SIZE + PADDING/2 + HUE_W + PADDING;
        int rowY   = PADDING + SV_SIZE + PADDING;
        int HANDLE_H_CONST = 20;
        int totalH = HANDLE_H_CONST + rowY + 20 + PADDING + 28 + PADDING;

        // ── SV square ────────────────────────────────────────────────────────
        svPanel = new SVPanel();
        int HANDLE_H = 20;
        svPanel.setBounds(PADDING, HANDLE_H + PADDING, SV_SIZE, SV_SIZE);
        root.add(svPanel);

        // ── Hue strip ─────────────────────────────────────────────────────────
        huePanel = new HuePanel();
        huePanel.setBounds(PADDING + SV_SIZE + PADDING/2, HANDLE_H + PADDING, HUE_W, HUE_H);
        root.add(huePanel);

        // ── Alpha label + strip ───────────────────────────────────────────────
        JLabel alphaLbl = makeLabel("A:");
        alphaLbl.setBounds(PADDING, HANDLE_H + rowY, 18, 18);
        root.add(alphaLbl);

        alphaPanel = new AlphaPanel();
        int alphaStripW = totalW - PADDING - 18 - 4 - 30 - PADDING;
        alphaPanel.setBounds(PADDING + 20, HANDLE_H + rowY, alphaStripW, 18);
        root.add(alphaPanel);

        alphaValLabel = makeLabel("255");
        alphaValLabel.setBounds(PADDING + 20 + alphaStripW + 4, HANDLE_H + rowY, 28, 18);
        root.add(alphaValLabel);

        // ── Preview + hex ─────────────────────────────────────────────────────
        int previewY = HANDLE_H + rowY + 20 + PADDING/2;
        previewLabel = new JLabel();
        previewLabel.setOpaque(true);
        previewLabel.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        previewLabel.setBounds(PADDING, previewY, PREVIEW_S, PREVIEW_S);
        root.add(previewLabel);

        hexField = new JTextField("#FFFFFFFF");
        hexField.setBackground(AppColors.BTN_BG);
        hexField.setForeground(AppColors.TEXT);
        hexField.setCaretColor(AppColors.TEXT);
        hexField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        hexField.setFont(new Font("Monospaced", Font.PLAIN, 11));
        hexField.setBounds(PADDING + PREVIEW_S + 6, previewY, 90, PREVIEW_S);
        hexField.addActionListener(e -> parseHexField());
        root.add(hexField);

        // ── Drag handle bar at top ────────────────────────────────────────────
        int handleH = 20;
        JPanel handleBar = new JPanel(null);
        handleBar.setBackground(AppColors.HANDLE_BAR_TOP);
        handleBar.setBounds(0, 0, totalW, handleH);
        handleBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLbl = new JLabel("Farbe wählen");
        titleLbl.setForeground(AppColors.TEXT_MUTED);
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        titleLbl.setBounds(6, 3, totalW - 24, 14);
        handleBar.add(titleLbl);

        JButton closeBtn = new JButton("×");
        closeBtn.setForeground(AppColors.TEXT_MUTED);
        closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setBounds(totalW - 18, 2, 16, 16);
        closeBtn.addActionListener(e -> setVisible(false));
        handleBar.add(closeBtn);
        root.add(handleBar);

        // Drag via handle bar (the primary drag surface)
        MouseAdapter handleDragMA = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStart  = SwingUtilities.convertPoint(handleBar, e.getPoint(), null);
                dragStart  = new Point(handleBar.getLocationOnScreen().x + e.getX(),
                                       handleBar.getLocationOnScreen().y + e.getY());
                isDragging = true;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                Point cur = new Point(handleBar.getLocationOnScreen().x + e.getX(),
                                      handleBar.getLocationOnScreen().y + e.getY());
                Point loc = getLocation();
                setLocation(loc.x + cur.x - dragStart.x, loc.y + cur.y - dragStart.y);
                dragStart = cur;
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragStart  = null;
                isDragging = false;
                toFront();
            }
        };
        handleBar.addMouseListener(handleDragMA);
        handleBar.addMouseMotionListener(handleDragMA);

        root.setPreferredSize(new Dimension(totalW, totalH));
        setContentPane(root);

        // Hide when clicking outside – but NOT while dragging the popup itself
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) {
                if (!isDragging) setVisible(false);
            }
        });

        // ── Drag: click anywhere on root panel to move the popup ──────────────
        MouseAdapter dragMA = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStart  = e.getLocationOnScreen();
                isDragging = true;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                Point cur = e.getLocationOnScreen();
                Point loc = getLocation();
                setLocation(loc.x + cur.x - dragStart.x,
                            loc.y + cur.y - dragStart.y);
                dragStart = cur;
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragStart  = null;
                isDragging = false;
                toFront();
            }
        };
        // Attach drag only to the root panel (not the sub-panels — they consume their own events)
        root.addMouseListener(dragMA);
        root.addMouseMotionListener(dragMA);

        updateAlphaValLabel();
        syncPreview();
        syncHexField();
    }

    // =========================================================================
    // Inner panels
    // =========================================================================

    // ── SV (Saturation / Value) square ────────────────────────────────────────
    private class SVPanel extends JPanel {
        SVPanel() {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { pick(e); }
                @Override public void mouseDragged(MouseEvent e)  { pick(e); }
                void pick(MouseEvent e) {
                    sat = Math.max(0f, Math.min(1f, (float) e.getX() / (getWidth()  - 1)));
                    val = Math.max(0f, Math.min(1f, 1f - (float) e.getY() / (getHeight() - 1)));
                    svImage = null;
                    repaintAll(); syncHexField(); fireChange();
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();
            if (svImage == null || svImage.getWidth() != w || svImage.getHeight() != h) {
                svImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        float s = (float) x / (w - 1);
                        float v = 1f - (float) y / (h - 1);
                        svImage.setRGB(x, y, Color.HSBtoRGB(hue, s, v));
                    }
                }
            }
            g.drawImage(svImage, 0, 0, null);
            // Crosshair
            int cx = Math.round(sat * (w - 1));
            int cy = Math.round((1f - val) * (h - 1));
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.draw(new Ellipse2D.Float(cx - 5, cy - 5, 10, 10));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            g2.draw(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
        }
    }

    // ── Hue strip ─────────────────────────────────────────────────────────────
    private class HuePanel extends JPanel {
        HuePanel() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            buildHueImage();
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { pick(e); }
                @Override public void mouseDragged(MouseEvent e)  { pick(e); }
                void pick(MouseEvent e) {
                    hue = Math.max(0f, Math.min(1f, (float) e.getY() / (getHeight() - 1)));
                    svImage = null;
                    repaintAll(); syncHexField(); fireChange();
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }

        private void buildHueImage() {
            hueImage = new BufferedImage(HUE_W, HUE_H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < HUE_H; y++) {
                int rgb = Color.HSBtoRGB((float) y / (HUE_H - 1), 1f, 1f);
                for (int x = 0; x < HUE_W; x++) hueImage.setRGB(x, y, rgb);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(hueImage, 0, 0, getWidth(), getHeight(), null);
            int y = Math.round(hue * (getHeight() - 1));
            g.setColor(Color.WHITE);
            g.fillRect(0, y - 1, getWidth(), 3);
            g.setColor(Color.BLACK);
            g.drawRect(0, y - 2, getWidth() - 1, 4);
        }
    }

    // ── Alpha strip ───────────────────────────────────────────────────────────
    private class AlphaPanel extends JPanel {
        AlphaPanel() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { pick(e); }
                @Override public void mouseDragged(MouseEvent e)  { pick(e); }
                void pick(MouseEvent e) {
                    alpha = Math.max(0, Math.min(255,
                            (int) Math.round((double) e.getX() / (getWidth() - 1) * 255)));
                    alphaImage = null;
                    repaintAll(); syncHexField(); fireChange();
                    updateAlphaValLabel();
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();
            // Checkerboard
            int cs = 6;
            for (int x = 0; x < w; x += cs)
                for (int y = 0; y < h; y += cs) {
                    boolean even = ((x / cs) + (y / cs)) % 2 == 0;
                    g.setColor(even ? new Color(180,180,180) : new Color(120,120,120));
                    g.fillRect(x, y, Math.min(cs, w - x), Math.min(cs, h - y));
                }
            // Alpha gradient
            Color rgb = Color.getHSBColor(hue, sat, val);
            GradientPaint gp = new GradientPaint(0, 0,
                    new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 0),
                    w, 0,
                    new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255));
            ((Graphics2D) g).setPaint(gp);
            g.fillRect(0, 0, w, h);
            // Thumb
            int x = (int) Math.round((double) alpha / 255 * (w - 1));
            g.setColor(Color.WHITE);
            g.fillRect(x - 1, 0, 3, h);
            g.setColor(Color.BLACK);
            g.drawRect(x - 2, 0, 4, h - 1);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void repaintAll() {
        svPanel.repaint();
        huePanel.repaint();
        alphaPanel.repaint();
        syncPreview();
        updateAlphaValLabel();
    }

    private void syncPreview() {
        previewLabel.setBackground(getSelectedColor());
    }

    private void syncHexField() {
        Color c = getSelectedColor();
        hexField.setText(String.format("#%02X%02X%02X%02X",
                c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
    }

    private void parseHexField() {
        try {
            String s = hexField.getText().trim().replaceAll("^#", "");
            if (s.length() == 6)  s = s + "FF";
            if (s.length() != 8) return;
            int r = Integer.parseInt(s.substring(0,2),16);
            int g = Integer.parseInt(s.substring(2,4),16);
            int b = Integer.parseInt(s.substring(4,6),16);
            int a = Integer.parseInt(s.substring(6,8),16);
            setSelectedColor(new Color(r, g, b, a));
            fireChange();
        } catch (NumberFormatException ignored) {}
    }

    private void updateAlphaValLabel() {
        if (alphaValLabel != null)
            alphaValLabel.setText(String.valueOf(alpha));
    }

    private void fireChange() {
        if (changeListener != null)
            changeListener.stateChanged(new javax.swing.event.ChangeEvent(this));
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }
}
