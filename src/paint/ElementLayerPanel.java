package paint;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Sidebar panel showing the non-destructive Element layers of the current image.
 * Each tile represents one Element, with a thumbnail preview and a small red (×)
 * delete button in the top-right corner. Tiles are ordered highest-Z first
 * (most recently added at the top), mirroring typical layer-panel conventions.
 *
 * Visibility is controlled externally (shown/hidden together with Canvas mode).
 * The panel's own header close-button notifies the host via onCloseRequested().
 */
public class ElementLayerPanel extends JPanel {

    // ── Callback interface ────────────────────────────────────────────────────
    public interface Callbacks {
        List<Element> getActiveElements();
        List<Element> getSelectedElements();
        void setSelectedElement(Element el);
        void toggleElementSelection(Element el);
        void deleteElement(Element el);
        /** Burn (merge) the element permanently into the canvas image. */
        void burnElement(Element el);
        void repaintCanvas();
        void onCloseRequested();
        /** Called when the mouse enters/leaves a layer tile. id=-1 = no tile. */
        void onLayerPanelElementHover(int elementId);
    }

    // ── Dimensions ────────────────────────────────────────────────────────────
    public  static final int PANEL_W = 160;
    private static final int TILE_W  = 140;
    private static final int TILE_H  = 106;
    private static final int THUMB_H =  74;

    private final Callbacks      cb;
    private final JPanel         tilesContainer;
    private boolean              showAllOutlines = false;
    /** Id of the element whose tile is currently hovered in this panel, or -1. */
    private int                  hoveredElementId = -1;

    // =========================================================================
    // Constructor
    // =========================================================================
    public ElementLayerPanel(Callbacks cb) {
        this.cb = cb;
        setLayout(new BorderLayout());
        setBackground(new Color(36, 36, 36));
        setPreferredSize(new Dimension(PANEL_W, 0));
        // Left border separates this panel from the canvas
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, AppColors.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 16, 0)));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(42, 42, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        header.setPreferredSize(new Dimension(PANEL_W, 28));

        JLabel title = new JLabel("  Ebenen");
        title.setForeground(AppColors.TEXT_MUTED);
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        header.add(title, BorderLayout.CENTER);

        // Close button (×) – hides the panel via onCloseRequested()
        JLabel closeBtn = new JLabel("×");
        closeBtn.setForeground(AppColors.TEXT_MUTED);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { cb.onCloseRequested(); }
            @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(Color.WHITE); }
            @Override public void mouseExited (MouseEvent e) { closeBtn.setForeground(AppColors.TEXT_MUTED); }
        });
        // "Show all outlines" toggle – small square icon button
        JToggleButton outlineBtn = new JToggleButton("▣") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? AppColors.ACCENT : new Color(55, 55, 55));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        outlineBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        outlineBtn.setForeground(AppColors.TEXT);
        outlineBtn.setFocusPainted(false);
        outlineBtn.setBorderPainted(false);
        outlineBtn.setContentAreaFilled(false);
        outlineBtn.setPreferredSize(new Dimension(22, 22));
        outlineBtn.setToolTipText("Alle Layer-Rahmen immer anzeigen");
        outlineBtn.addActionListener(e -> {
            showAllOutlines = outlineBtn.isSelected();
            cb.repaintCanvas();
        });

        JPanel eastBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 3));
        eastBtns.setOpaque(false);
        eastBtns.add(outlineBtn);
        eastBtns.add(closeBtn);
        header.add(eastBtns, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Tiles container ───────────────────────────────────────────────────
        tilesContainer = new JPanel();
        tilesContainer.setLayout(new BoxLayout(tilesContainer, BoxLayout.Y_AXIS));
        tilesContainer.setBackground(new Color(36, 36, 36));
        tilesContainer.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));

        JScrollPane scroll = new JScrollPane(tilesContainer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setBackground(new Color(36, 36, 36));
        scroll.getViewport().setBackground(new Color(36, 36, 36));
        TileGalleryPanel.applyDarkScrollBar(scroll.getVerticalScrollBar());
        add(scroll, BorderLayout.CENTER);
    }

    // =========================================================================
    // Public API
    // =========================================================================
    public boolean isShowAllOutlines() { return showAllOutlines; }

    /**
     * Called by the canvas when the mouse moves over/off an element.
     * Highlights the matching tile in this panel without repainting the canvas.
     */
    public void setHoveredElement(int elementId) {
        if (hoveredElementId == elementId) return;
        hoveredElementId = elementId;
        tilesContainer.repaint();
    }

    /**
     * Rebuilds the tile list from the given elements.
     * The topmost element (highest Z = last in list) is shown first.
     */
    public void refresh(List<Element> elements) {
        tilesContainer.removeAll();
        List<Element> selectedEls = cb.getSelectedElements();

        for (int i = elements.size() - 1; i >= 0; i--) {
            Element el = elements.get(i);
            boolean sel = selectedEls.stream().anyMatch(s -> s.id() == el.id());
            tilesContainer.add(new ElementTile(el, sel));
            tilesContainer.add(Box.createVerticalStrut(4));
        }

        // "Keine Ebenen" placeholder
        if (elements.isEmpty()) {
            JLabel empty = new JLabel("<html><center>Keine<br>Ebenen</center></html>", JLabel.CENTER);
            empty.setForeground(AppColors.TEXT_MUTED);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 11));
            empty.setAlignmentX(CENTER_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
            tilesContainer.add(empty);
        }

        tilesContainer.revalidate();
        tilesContainer.repaint();
    }

    // =========================================================================
    // ElementTile inner class
    // =========================================================================
    class ElementTile extends JPanel {

        private final Element element;
        private final boolean selected;

        ElementTile(Element el, boolean selected) {
            this.element  = el;
            this.selected = selected;
            setLayout(null);
            setPreferredSize(new Dimension(TILE_W, TILE_H));
            setMaximumSize (new Dimension(TILE_W, TILE_H));
            setMinimumSize (new Dimension(TILE_W, TILE_H));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // ── Orange burn (⊞) button – second from top-right ───────────────
            JLabel burn = new JLabel("⊕", JLabel.CENTER);
            burn.setForeground(new Color(220, 140, 30));
            burn.setFont(new Font("SansSerif", Font.BOLD, 11));
            burn.setBounds(TILE_W - 37, 4, 16, 16);
            burn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            burn.setOpaque(true);
            burn.setBackground(new Color(50, 50, 50));
            burn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            burn.setToolTipText("Layer einbrennen (auf Canvas anwenden)");
            burn.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    e.consume();
                    cb.burnElement(element);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    burn.setBackground(new Color(160, 90, 10));
                    burn.setForeground(Color.WHITE);
                    burn.setBorder(BorderFactory.createLineBorder(new Color(220, 140, 30), 1));
                }
                @Override public void mouseExited(MouseEvent e) {
                    burn.setBackground(new Color(50, 50, 50));
                    burn.setForeground(new Color(220, 140, 30));
                    burn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(burn);

            // ── Red (×) delete button – top-right corner ──────────────────────
            JLabel del = new JLabel("✕", JLabel.CENTER);
            del.setForeground(new Color(220, 60, 60));
            del.setFont(new Font("SansSerif", Font.BOLD, 10));
            del.setBounds(TILE_W - 19, 4, 16, 16);
            del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            del.setOpaque(true);
            del.setBackground(new Color(50, 50, 50));
            del.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            del.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    e.consume();
                    cb.deleteElement(element);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    del.setBackground(new Color(180, 40, 40));
                    del.setForeground(Color.WHITE);
                    del.setBorder(BorderFactory.createLineBorder(new Color(220, 60, 60), 1));
                }
                @Override public void mouseExited(MouseEvent e) {
                    del.setBackground(new Color(50, 50, 50));
                    del.setForeground(new Color(220, 60, 60));
                    del.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(del);

            // ── Layer label at bottom ──────────────────────────────────────────
            JLabel lbl = new JLabel("Layer " + el.id(), JLabel.CENTER);
            lbl.setForeground(AppColors.TEXT_MUTED);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lbl.setBounds(0, TILE_H - 18, TILE_W, 16);
            add(lbl);

            // Tile click: select element on canvas
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.isShiftDown()) {
                        cb.toggleElementSelection(element);
                    } else {
                        cb.setSelectedElement(element);
                    }
                    cb.repaintCanvas();
                }
                @Override public void mouseEntered(MouseEvent e) {
                    hoveredElementId = element.id();
                    cb.onLayerPanelElementHover(element.id());
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hoveredElementId = -1;
                    cb.onLayerPanelElementHover(-1);
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Hover = mouse over THIS tile OR canvas hover matches this element
            boolean hovered = hoveredElementId == element.id();

            // Tile background – slightly lighter on hover
            g2.setColor(hovered ? new Color(58, 54, 44) : new Color(44, 44, 44));
            g2.fillRoundRect(0, 0, TILE_W, TILE_H, 6, 6);

            // Checkerboard for transparency indication
            int tx = 5, ty = 4, tw = TILE_W - 10, th = THUMB_H;
            paintCheckerboard(g2, tx, ty, tw, th);

            // Element thumbnail (aspect-fit) — for TEXT_LAYER show a text preview
            BufferedImage img = element.image();
            if (img != null) {
                double s = Math.min((double) tw / img.getWidth(), (double) th / img.getHeight());
                int iw = Math.max(1, (int)(img.getWidth()  * s));
                int ih = Math.max(1, (int)(img.getHeight() * s));
                int ix = tx + (tw - iw) / 2;
                int iy = ty + (th - ih) / 2;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, ix, iy, iw, ih, null);
            } else if (element.type() == ElementType.TEXT_LAYER) {
                // TEXT_LAYER: render a small text preview in the tile
                int style = (element.fontBold() ? java.awt.Font.BOLD : 0)
                          | (element.fontItalic() ? java.awt.Font.ITALIC : 0);
                java.awt.Font pf = new java.awt.Font(
                        element.fontName() != null ? element.fontName() : "SansSerif",
                        style, Math.min(14, Math.max(8, element.fontSize() / 3)));
                g2.setFont(pf);
                g2.setColor(element.fontColor() != null ? element.fontColor() : java.awt.Color.WHITE);
                g2.setClip(tx, ty, tw, th);
                String[] lines = element.text() != null ? element.text().split("\n", -1) : new String[]{""};
                java.awt.FontMetrics fm = g2.getFontMetrics();
                for (int i = 0; i < lines.length && i < 4; i++) {
                    g2.drawString(lines[i], tx + 2, ty + fm.getAscent() + fm.getHeight() * i + 2);
                }
                g2.setClip(null);
            }

            // Selection border / hover border / normal border
            if (selected) {
                g2.setColor(AppColors.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, TILE_W - 3, TILE_H - 3, 6, 6);
            } else if (hovered) {
                // Warm amber hover border — mirrors the canvas hover outline colour
                g2.setColor(new Color(255, 200, 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, TILE_W - 3, TILE_H - 3, 6, 6);
            } else {
                g2.setColor(new Color(62, 62, 62));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, TILE_W - 1, TILE_H - 1, 6, 6);
            }

            g2.dispose();
        }

        private void paintCheckerboard(Graphics2D g2, int x, int y, int w, int h) {
            int cs = 6;
            for (int row = 0; row * cs < h; row++) {
                for (int col = 0; col * cs < w; col++) {
                    g2.setColor((row + col) % 2 == 0 ? new Color(78, 78, 78) : new Color(62, 62, 62));
                    int cx = x + col * cs, cy = y + row * cs;
                    int cw = Math.min(cs, x + w - cx);
                    int ch = Math.min(cs, y + h - cy);
                    if (cw > 0 && ch > 0) g2.fillRect(cx, cy, cw, ch);
                }
            }
        }
    }
}
