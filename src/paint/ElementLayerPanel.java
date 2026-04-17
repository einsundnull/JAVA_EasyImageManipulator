package paint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Sidebar panel showing the non-destructive layers of the current image.
 * Each tile represents one Layer, with a thumbnail preview and a small red (×)
 * delete button in the top-right corner. Tiles are ordered highest-Z first
 * (most recently added at the top), mirroring typical layer-panel conventions.
 *
 * Visibility is controlled externally (shown/hidden together with Canvas mode).
 * The panel's own header close-button notifies the host via onCloseRequested().
 */
public class ElementLayerPanel extends BaseSidebarPanel {

    // ── DnD flavor for dragging a Layer between panels / onto a canvas ────────
    public static final DataFlavor LAYER_FLAVOR = new DataFlavor(Layer.class, "Layer");

    // ── Callback interface ────────────────────────────────────────────────────
    public interface Callbacks {
        List<Layer> getActiveElements();
        List<Layer> getSelectedElements();
        void setSelectedElement(Layer el);
        void toggleElementSelection(Layer el);
        void deleteElement(Layer el);
        /** Burn (merge) the layer permanently into the canvas image. */
        void burnElement(Layer el);
        /** Export layer as an image file in the same directory as the source image. */
        void exportElementAsImage(Layer el);
        void repaintCanvas();
        void onCloseRequested();
        /** Called when the mouse enters/leaves a layer tile. id=-1 = no tile. */
        void onLayerPanelElementHover(int elementId);
        /** Double-click on an ImageLayer tile: open it in the other canvas for pixel editing. */
        void openElementInOtherCanvas(Layer el);
        /** Double-click on a TextLayer tile: enter text-edit mode on the owning canvas. */
        void openTextLayerForEditing(Layer el);
        /** Insert a copy of {@code layer} at visual index {@code visualIdx} (0 = top). */
        void insertLayerCopyAt(Layer layer, int visualIdx);
        /** Insert the image at {@code file} as a new layer at visual index {@code visualIdx}. */
        void insertFileAsLayerAt(File file, int visualIdx);
        /** Reset the rotation angle of an ImageLayer to 0. */
        void resetElementRotation(Layer el);
        /** Export a TextLayer as a translation map. */
        void exportElementAsMap(Layer el);
        /** Toggle visibility (hidden state) of a layer. */
        void toggleElementVisibility(Layer el);
    }

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static  int TILE_W  = 140;
    private static  int PADDING = 18;  // 9px left + 9px right
    private static  int SCROLLBAR = 16;
    public  static  int PANEL_W = TILE_W + PADDING + SCROLLBAR;  // 174
    private static  int TILE_H  = 106;
    private static  int THUMB_H =  74;

    private final Callbacks      cb;
    private final JPanel         tilesContainer;
    private boolean              showAllOutlines = false;
    private boolean              showAll = false;
    private Layer                linkedElement = null;
    /** Id of the layer whose tile is currently hovered in this panel, or -1. */
    private int                  hoveredElementId = -1;
    /** Y-coordinate of the drop indicator line in tilesContainer, or -1 if none. */
    private int                  dropIndicatorY   = -1;

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
        // Build header manually since we need both outline button + close button
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

        // All/Only toggle button using link-alt icon
        ImageIcon linkIcon = loadIcon("link-alt.png", 14);
        JLabel toggleBtn = new JLabel(linkIcon);
        toggleBtn.setForeground(AppColors.TEXT_MUTED);
        toggleBtn.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
        toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleBtn.setToolTipText("Only (linked) / All (unlinked) Toggle");
        toggleBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                showAll = !showAll;
                toggleBtn.setForeground(showAll ? Color.WHITE : AppColors.TEXT_MUTED);
                tilesContainer.repaint();
            }
            @Override public void mouseEntered(MouseEvent e) { toggleBtn.setForeground(Color.WHITE); }
            @Override public void mouseExited (MouseEvent e) { toggleBtn.setForeground(showAll ? Color.WHITE : AppColors.TEXT_MUTED); }
        });
        eastBtns.add(toggleBtn);

        eastBtns.add(outlineBtn);

        // Refresh button – reload elements from disk
        JLabel refreshBtn = new JLabel("⟳");
        refreshBtn.setForeground(AppColors.TEXT_MUTED);
        refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        refreshBtn.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { refresh(); }
            @Override public void mouseEntered(MouseEvent e) { refreshBtn.setForeground(Color.WHITE); }
            @Override public void mouseExited (MouseEvent e) { refreshBtn.setForeground(AppColors.TEXT_MUTED); }
        });
        eastBtns.add(refreshBtn);

        eastBtns.add(closeBtn);
        header.add(eastBtns, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // ── Tiles container ───────────────────────────────────────────────────
        // Override getPreferredSize so JViewport sizes this panel to the viewport width,
        // which causes BoxLayout Y_AXIS to resize tiles accordingly.
        tilesContainer = new JPanel() {
            @Override public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension d = super.getPreferredSize();
                if (getParent() != null && getParent().getWidth() > 0)
                    d.width = getParent().getWidth();
                return d;
            }
            @Override public void paint(Graphics g) {
                super.paint(g);
                if (dropIndicatorY >= 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(AppColors.ACCENT);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(0, dropIndicatorY, getWidth(), dropIndicatorY);
                    g2.dispose();
                }
            }
        };
        tilesContainer.setLayout(new BoxLayout(tilesContainer, BoxLayout.Y_AXIS));
        tilesContainer.setBackground(new Color(36, 36, 36));
        tilesContainer.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));

        // ── Drop target: accept Layer and image-file-as-element drags ─────────
        new DropTarget(tilesContainer, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void dragEnter(DropTargetDragEvent dtde) { dtde.acceptDrag(DnDConstants.ACTION_COPY); }
            @Override public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
                dropIndicatorY = computeDropIndicatorY(dtde.getLocation().y);
                tilesContainer.repaint();
            }
            @Override public void dragExit(DropTargetEvent dte) {
                dropIndicatorY = -1;
                tilesContainer.repaint();
            }
            @Override public void drop(DropTargetDropEvent dtde) {
                dropIndicatorY = -1;
                tilesContainer.repaint();
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                int vIdx = computeVisualDropIndex(dtde.getLocation().y);
                try {
                    Transferable t = dtde.getTransferable();
                    if (t.isDataFlavorSupported(LAYER_FLAVOR)) {
                        Layer layer = (Layer) t.getTransferData(LAYER_FLAVOR);
                        cb.insertLayerCopyAt(layer, vIdx);
                        dtde.dropComplete(true);
                    } else if (t.isDataFlavorSupported(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR)) {
                        TileGalleryPanel.FileForElement ffe = (TileGalleryPanel.FileForElement)
                                t.getTransferData(TileGalleryPanel.FILE_AS_ELEMENT_FLAVOR);
                        cb.insertFileAsLayerAt(ffe.file, vIdx);
                        dtde.dropComplete(true);
                    } else {
                        dtde.dropComplete(false);
                    }
                } catch (Exception ex) { dtde.dropComplete(false); }
            }
        }, true);

        // Use base class builder for dark-styled scrollpane
        JScrollPane scroll = buildSidebarScrollPane(tilesContainer);
        add(scroll, BorderLayout.CENTER);
    }

    // =========================================================================
    // Public API
    // =========================================================================
    public boolean isShowAllOutlines() { return showAllOutlines; }

    /**
     * Set the linked element for the All/Only toggle.
     */
    public void setLinkedElement(Layer layer) {
        this.linkedElement = layer;
        tilesContainer.repaint();
    }

    @Override
    public void refresh() {
        /* ElementLayerPanel uses refresh(List<Layer>) for updates, not generic refresh(). */
    }

    // ── Drop-indicator helpers ────────────────────────────────────────────────

    /** Returns the visual insert index (0 = top) for a drop at pixel y in tilesContainer. */
    private int computeVisualDropIndex(int y) {
        int idx = 0;
        for (Component c : tilesContainer.getComponents()) {
            if (c instanceof LayerTile) {
                Rectangle b = c.getBounds();
                if (y < b.y + b.height / 2) return idx;
                idx++;
            }
        }
        return idx;
    }

    /** Returns the y-pixel of the drop indicator line for a drag at pixel y. */
    private int computeDropIndicatorY(int y) {
        for (Component c : tilesContainer.getComponents()) {
            if (c instanceof LayerTile) {
                Rectangle b = c.getBounds();
                if (y < b.y + b.height / 2) return b.y;
            }
        }
        return tilesContainer.getHeight() - tilesContainer.getInsets().bottom;
    }

    /**
     * Called by the canvas when the mouse moves over/off a layer.
     * Highlights the matching tile in this panel without repainting the canvas.
     */
    public void setHoveredElement(int elementId) {
        if (hoveredElementId == elementId) return;
        hoveredElementId = elementId;
        tilesContainer.repaint();
    }

    /**
     * Rebuilds the tile list from the given layers.
     * The topmost layer (highest Z = last in list) is shown first.
     */
    public void refresh(List<Layer> layers) {
        tilesContainer.removeAll();
        List<Layer> selectedEls = cb.getSelectedElements();

        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            boolean sel = selectedEls.stream().anyMatch(s -> s.id() == layer.id());
            tilesContainer.add(new LayerTile(layer, sel, showAll, linkedElement));
            tilesContainer.add(Box.createVerticalStrut(5));
        }

        // "Keine Ebenen" placeholder
        if (layers.isEmpty()) {
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
    // LayerTile inner class
    // =========================================================================
    class LayerTile extends JPanel {

        private final Layer   layer;
        private final boolean selected;
        private final boolean isShowAll;
        private final Layer linkedLayerRef;
        private JLabel toImage, burn, del, resetRot, mapBtn, vis, lbl;

        LayerTile(Layer layer, boolean selected, boolean showAll, Layer linkedElement) {
            this.layer    = layer;
            this.selected = selected;
            this.isShowAll = showAll;
            this.linkedLayerRef = linkedElement;
            setLayout(null);
            setPreferredSize(new Dimension(TILE_W, TILE_H));
            // Don't set MaximumSize/MinimumSize — let tiles scale with panel width
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // ── Blue "zu image" (📥) button – export as image file ───────────────
            toImage = new JLabel("↓", JLabel.CENTER);
            toImage.setForeground(new Color(60, 140, 220));
            toImage.setFont(new Font("SansSerif", Font.BOLD, 11));
            toImage.setBounds(TILE_W - 55, 4, 16, 16);
            toImage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toImage.setOpaque(true);
            toImage.setBackground(new Color(50, 50, 50));
            toImage.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            toImage.setToolTipText("Als Bild exportieren");
            toImage.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) return;  // Let double-clicks through
                    e.consume();
                    cb.exportElementAsImage(layer);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    toImage.setBackground(new Color(40, 100, 160));
                    toImage.setForeground(Color.WHITE);
                    toImage.setBorder(BorderFactory.createLineBorder(new Color(60, 140, 220), 1));
                }
                @Override public void mouseExited(MouseEvent e) {
                    toImage.setBackground(new Color(50, 50, 50));
                    toImage.setForeground(new Color(60, 140, 220));
                    toImage.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(toImage);

            // ── Orange burn (⊕) button – second from top-right ───────────────
            burn = new JLabel("⊕", JLabel.CENTER);
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
                    if (e.getClickCount() > 1) return;  // Let double-clicks through
                    e.consume();
                    cb.burnElement(layer);
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
            del = new JLabel("✕", JLabel.CENTER);
            del.setForeground(new Color(220, 60, 60));
            del.setFont(new Font("SansSerif", Font.BOLD, 10));
            del.setBounds(TILE_W - 19, 4, 16, 16);
            del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            del.setOpaque(true);
            del.setBackground(new Color(50, 50, 50));
            del.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            del.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) return;  // Let double-clicks through
                    e.consume();
                    cb.deleteElement(layer);
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

            // ── Green reset rotation button (↺) – only for rotated ImageLayers ─
            if (layer instanceof ImageLayer il && Math.abs(il.rotationAngle()) > 0.001) {
                resetRot = new JLabel("↺", JLabel.CENTER);
                resetRot.setForeground(new Color(100, 180, 100));
                resetRot.setFont(new Font("SansSerif", Font.BOLD, 11));
                resetRot.setBounds(TILE_W - 73, 4, 16, 16);
                resetRot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                resetRot.setOpaque(true);
                resetRot.setBackground(new Color(50, 50, 50));
                resetRot.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                resetRot.setToolTipText("Rotation zurücksetzen");
                resetRot.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() > 1) return;  // Let double-clicks through
                        e.consume();
                        cb.resetElementRotation(layer);
                    }
                    @Override public void mouseEntered(MouseEvent e) {
                        resetRot.setBackground(new Color(80, 140, 80));
                        resetRot.setForeground(Color.WHITE);
                        resetRot.setBorder(BorderFactory.createLineBorder(new Color(100, 180, 100), 1));
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        resetRot.setBackground(new Color(50, 50, 50));
                        resetRot.setForeground(new Color(100, 180, 100));
                        resetRot.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                    }
                });
                add(resetRot);
            }

            // ── Cyan visibility button (👁) – for all layers ────────────────────
            boolean hidden = false;
            if (layer instanceof ImageLayer il) hidden = il.isHidden();
            else if (layer instanceof TextLayer tl) hidden = tl.isHidden();
            else if (layer instanceof PathLayer pl) hidden = pl.isHidden();

            vis = new JLabel(hidden ? "🚫" : "👁", JLabel.CENTER);
            vis.setForeground(hidden ? new Color(180, 60, 60) : new Color(60, 180, 180));
            vis.setFont(new Font("SansSerif", Font.BOLD, 10));
            vis.setBounds(TILE_W - 109, 4, 16, 16);
            vis.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            vis.setOpaque(true);
            vis.setBackground(new Color(50, 50, 50));
            vis.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
            vis.setToolTipText(hidden ? "Sichtbar machen" : "Ausblenden");
            vis.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) return;  // Let double-clicks through
                    e.consume();
                    cb.toggleElementVisibility(layer);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    boolean h = layer instanceof ImageLayer il && il.isHidden() ||
                               layer instanceof TextLayer tl && tl.isHidden() ||
                               layer instanceof PathLayer pl && pl.isHidden();
                    vis.setBackground(h ? new Color(140, 40, 40) : new Color(40, 140, 140));
                    vis.setForeground(Color.WHITE);
                    vis.setBorder(BorderFactory.createLineBorder(
                        h ? new Color(180, 60, 60) : new Color(60, 180, 180), 1));
                }
                @Override public void mouseExited(MouseEvent e) {
                    boolean h = layer instanceof ImageLayer il && il.isHidden() ||
                               layer instanceof TextLayer tl && tl.isHidden() ||
                               layer instanceof PathLayer pl && pl.isHidden();
                    vis.setBackground(new Color(50, 50, 50));
                    vis.setForeground(h ? new Color(180, 60, 60) : new Color(60, 180, 180));
                    vis.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                }
            });
            add(vis);

            // ── Purple map button (🗺) – only for TextLayers ────────────────────
            if (layer instanceof TextLayer) {
                mapBtn = new JLabel("🗺", JLabel.CENTER);
                mapBtn.setForeground(new Color(180, 100, 200));
                mapBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
                mapBtn.setBounds(TILE_W - 91, 4, 16, 16);
                mapBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                mapBtn.setOpaque(true);
                mapBtn.setBackground(new Color(50, 50, 50));
                mapBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                mapBtn.setToolTipText("Als Translation Map exportieren");
                mapBtn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() > 1) return;  // Let double-clicks through
                        e.consume();
                        cb.exportElementAsMap(layer);
                    }
                    @Override public void mouseEntered(MouseEvent e) {
                        mapBtn.setBackground(new Color(140, 80, 160));
                        mapBtn.setForeground(Color.WHITE);
                        mapBtn.setBorder(BorderFactory.createLineBorder(new Color(180, 100, 200), 1));
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        mapBtn.setBackground(new Color(50, 50, 50));
                        mapBtn.setForeground(new Color(180, 100, 200));
                        mapBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
                    }
                });
                add(mapBtn);
            }

            // ── Layer label at bottom ──────────────────────────────────────────
            String displayName = layer.displayName();
            if (layer instanceof ImageLayer il) {
                // Show opacity for ImageLayers (never 100% transparent so we can see them)
                displayName = displayName + " " + Math.max(1, il.opacity()) + "%";
            }
            lbl = new JLabel(displayName, JLabel.CENTER);
            lbl.setForeground(AppColors.TEXT_MUTED);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lbl.setBounds(0, TILE_H - 18, TILE_W, 16);
            add(lbl);

            // Initial layout at TILE_W so children have valid bounds immediately
            doLayout();

            // Tile click: select layer on canvas
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && layer instanceof PathLayer pl) {
                        // Double-click on PathLayer: open path editor dialog
                        openPathEditorDialog(pl);
                    } else if (e.getClickCount() == 2 && layer instanceof ImageLayer) {
                        // Double-click on ImageLayer: open in other canvas for pixel editing
                        cb.openElementInOtherCanvas(layer);
                    } else if (e.getClickCount() == 2 && layer instanceof TextLayer) {
                        // Double-click on TextLayer: enter text-edit mode on the owning canvas
                        cb.openTextLayerForEditing(layer);
                    } else if (e.isShiftDown()) {
                        cb.toggleElementSelection(layer);
                    } else {
                        cb.setSelectedElement(layer);
                    }
                    cb.repaintCanvas();
                }
                @Override public void mouseEntered(MouseEvent e) {
                    hoveredElementId = layer.id();
                    cb.onLayerPanelElementHover(layer.id());
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hoveredElementId = -1;
                    cb.onLayerPanelElementHover(-1);
                    repaint();
                }
            });

            // ── Drag source: left-drag exports this layer as LAYER_FLAVOR ─────
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                this, DnDConstants.ACTION_COPY,
                dge -> {
                    java.awt.event.InputEvent trigger = dge.getTriggerEvent();
                    if (!(trigger instanceof MouseEvent me) || !SwingUtilities.isLeftMouseButton(me)) return;
                    int iw = Math.max(1, getWidth()), ih = Math.max(1, getHeight());
                    BufferedImage dragImg = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D dg = dragImg.createGraphics();
                    dg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
                    paint(dg);
                    dg.dispose();
                    Transferable t = new Transferable() {
                        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{LAYER_FLAVOR}; }
                        @Override public boolean isDataFlavorSupported(DataFlavor f) { return LAYER_FLAVOR.equals(f); }
                        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                            if (!LAYER_FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
                            return layer;
                        }
                    };
                    try {
                        dge.startDrag(DragSource.DefaultCopyDrop, dragImg,
                                new Point(iw / 2, ih / 2), t, null);
                    } catch (java.awt.dnd.InvalidDnDOperationException ignored) {}
                }
            );
        }

        @Override
        public void doLayout() {
            int w = getWidth() > 0 ? getWidth() : TILE_W;
            if (mapBtn != null) {
                // TextLayer: mapBtn at w-91, toImage at w-55, burn at w-37, del at w-19
                mapBtn .setBounds(w - 91, 4, 16, 16);
                toImage.setBounds(w - 55, 4, 16, 16);
                burn   .setBounds(w - 37, 4, 16, 16);
                del    .setBounds(w - 19, 4, 16, 16);
            } else if (resetRot != null) {
                // Rotated ImageLayer: resetRot at w-73, toImage at w-55, burn at w-37, del at w-19
                resetRot.setBounds(w - 73, 4, 16, 16);
                toImage.setBounds(w - 55, 4, 16, 16);
                burn   .setBounds(w - 37, 4, 16, 16);
                del    .setBounds(w - 19, 4, 16, 16);
            } else {
                // ImageLayer without rotation: toImage at w-55, burn at w-37, del at w-19
                toImage.setBounds(w - 55, 4, 16, 16);
                burn   .setBounds(w - 37, 4, 16, 16);
                del    .setBounds(w - 19, 4, 16, 16);
            }
            lbl    .setBounds(0, TILE_H - 18, w, 16);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();

            // Hover = mouse over THIS tile OR canvas hover matches this layer
            boolean hovered = hoveredElementId == layer.id();

            // Tile background – slightly lighter on hover
            g2.setColor(hovered ? new Color(58, 54, 44) : new Color(44, 44, 44));
            g2.fillRoundRect(0, 0, w, TILE_H, 6, 6);

            // Checkerboard for transparency indication
            int tx = 5, ty = 4, tw = w - 10, th = THUMB_H;
            paintCheckerboard(g2, tx, ty, tw, th);

            // Layer thumbnail (aspect-fit, with rotation + opacity)
            if (layer instanceof ImageLayer il) {
                BufferedImage img = il.image();
                if (img != null) {
                    double s = Math.min((double) tw / img.getWidth(), (double) th / img.getHeight());
                    int iw = Math.max(1, (int)(img.getWidth()  * s));
                    int ih = Math.max(1, (int)(img.getHeight() * s));
                    int ix = tx + (tw - iw) / 2;
                    int iy = ty + (th - ih) / 2;
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setClip(tx, ty, tw, th);
                    float alpha = il.opacity() / 100.0f;
                    java.awt.Composite origComp = g2.getComposite();
                    if (alpha < 1.0f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    if (Math.abs(il.rotationAngle()) > 0.001) {
                        java.awt.geom.AffineTransform savedTx = g2.getTransform();
                        g2.rotate(Math.toRadians(il.rotationAngle()), ix + iw / 2.0, iy + ih / 2.0);
                        g2.drawImage(img, ix, iy, iw, ih, null);
                        g2.setTransform(savedTx);
                    } else {
                        g2.drawImage(img, ix, iy, iw, ih, null);
                    }
                    g2.setComposite(origComp);
                    g2.setClip(null);
                }
            } else if (layer instanceof TextLayer tl) {
                // TEXT_LAYER: render a small text preview in the tile
                int style = (tl.fontBold() ? java.awt.Font.BOLD : 0)
                          | (tl.fontItalic() ? java.awt.Font.ITALIC : 0);
                java.awt.Font pf = new java.awt.Font(
                        tl.fontName(), style,
                        Math.min(14, Math.max(8, tl.fontSize() / 3)));
                g2.setFont(pf);
                g2.setColor(tl.fontColor());
                g2.setClip(tx, ty, tw, th);
                String[] lines = tl.text().split("\n", -1);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                for (int i = 0; i < lines.length && i < 4; i++) {
                    g2.drawString(lines[i], tx + 2, ty + fm.getAscent() + fm.getHeight() * i + 2);
                }
                g2.setClip(null);
            } else if (layer instanceof PathLayer pl) {
                // PATH_LAYER: live preview
                java.util.List<Point3D> points = pl.points();
                if (!points.isEmpty()) {
                    // Step 1: find min/max over all points
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (Point3D p : points) {
                        if (p.x < minX) minX = p.x;
                        if (p.x > maxX) maxX = p.x;
                        if (p.y < minY) minY = p.y;
                        if (p.y > maxY) maxY = p.y;
                    }

                    // Step 2: frame edges — 8px outside each extreme point
                    double fX1 = minX - 8;
                    double fY1 = minY - 8;
                    double fX2 = maxX + 8;
                    double fY2 = maxY + 8;
                    double fW  = Math.max(1, fX2 - fX1);
                    double fH  = Math.max(1, fY2 - fY1);

                    // Step 3: scale frame to fit thumbnail, centered
                    double scale   = Math.min(tw / fW, th / fH);
                    double originX = tx + (tw - fW * scale) / 2.0;
                    double originY = ty + (th - fH * scale) / 2.0;

                    // Helper: map a path-space x/y to tile pixels
                    // px = originX + (p.x - fX1) * scale

                    // Step 4: draw frame rectangle
                    g2.setColor(new java.awt.Color(90, 90, 90, 180));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
                    g2.drawRect((int) originX, (int) originY,
                                (int)(fW * scale), (int)(fH * scale));

                    // Step 5: path lines
                    g2.setColor(new java.awt.Color(0, 200, 255, 180));
                    g2.setStroke(new BasicStroke(1f));
                    if (pl.isClosed() && points.size() >= 2) {
                        Point3D p1 = points.get(points.size() - 1);
                        Point3D p2 = points.get(0);
                        int x1 = (int)(originX + (p1.x - fX1) * scale);
                        int y1 = (int)(originY + (p1.y - fY1) * scale);
                        int x2 = (int)(originX + (p2.x - fX1) * scale);
                        int y2 = (int)(originY + (p2.y - fY1) * scale);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                    for (int i = 0; i < points.size() - 1; i++) {
                        Point3D p1 = points.get(i);
                        Point3D p2 = points.get(i + 1);
                        int x1 = (int)(originX + (p1.x - fX1) * scale);
                        int y1 = (int)(originY + (p1.y - fY1) * scale);
                        int x2 = (int)(originX + (p2.x - fX1) * scale);
                        int y2 = (int)(originY + (p2.y - fY1) * scale);
                        g2.drawLine(x1, y1, x2, y2);
                    }

                    // Step 6: point circles
                    for (Point3D p : points) {
                        int px = (int)(originX + (p.x - fX1) * scale);
                        int py = (int)(originY + (p.y - fY1) * scale);
                        g2.setColor(java.awt.Color.WHITE);
                        g2.fillOval(px - 2, py - 2, 4, 4);
                        g2.setColor(new java.awt.Color(0, 150, 200));
                        g2.drawOval(px - 2, py - 2, 4, 4);
                    }
                }
            }

            // Selection border / hover border / normal border
            if (selected) {
                g2.setColor(AppColors.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 6, 6);
            } else if (hovered) {
                // Warm amber hover border — mirrors the canvas hover outline colour
                g2.setColor(new Color(255, 200, 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 6, 6);
            } else {
                g2.setColor(new Color(62, 62, 62));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, TILE_H - 1, 6, 6);
            }

            // Draw red border for unlinked layers when showAll is true
            if (isShowAll && linkedLayerRef != null && layer.id() != linkedLayerRef.id()) {
                g2.setColor(AppColors.DANGER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w - 1, TILE_H - 1, 6, 6);
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

    // ── Path Editor Dialog (double-click handler) ──────────────────────────────
    private void openPathEditorDialog(PathLayer pathLayer) {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Path Editor", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(400, 500);
        dlg.setLocationRelativeTo(this);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title
        JLabel titleLbl = new JLabel("Path Points: " + pathLayer.displayName());
        titleLbl.setFont(titleLbl.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        mainPanel.add(titleLbl, BorderLayout.NORTH);

        // Points list
        JPanel listPanel = new JPanel(new BorderLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < pathLayer.pointCount(); i++) {
            Point3D p = pathLayer.getPoint(i);
            if (p != null) {
                listModel.addElement(String.format("Point %d: (%.1f, %.1f, %.1f)", i, p.x, p.y, p.z));
            }
        }
        JList<String> pointsList = new JList<>(listModel);
        pointsList.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(pointsList);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(listPanel, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        btnPanel.add(closeBtn);

        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        dlg.setContentPane(mainPanel);
        dlg.setVisible(true);
    }
}
