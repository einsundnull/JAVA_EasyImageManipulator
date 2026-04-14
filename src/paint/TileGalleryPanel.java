package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Left-sidebar gallery showing all images in the loaded directory as tiles.
 *
 * Tile interaction:
 *  - Single click      → open image (green border = active)
 *  - SHIFT + click     → toggle multiselect (orange border + checkbox)
 *  - Checkbox click    → same as SHIFT+click
 *
 * When at least one tile is SHIFT-selected:
 *  - Checkboxes appear on every tile
 *  - "Alle" / "Keine" buttons appear in the header
 */
public class TileGalleryPanel extends BaseSidebarPanel {

    // ── Dimensions ────────────────────────────────────────────────────────────
    public  static final int GALLERY_W = 198;
    private static final int TILE_W    = 150;
    private static final int TILE_H    = 118;
    private static final int THUMB_H   = 88;

    // ── DnD: wrapper class so FILE_AS_ELEMENT_FLAVOR is unambiguous ──────────
    static final class FileForElement {
        final File file;
        FileForElement(File f) { file = f; }
    }
    public static final DataFlavor FILE_AS_ELEMENT_FLAVOR =
            new DataFlavor(FileForElement.class, "FileAsElement");

    // ── Callback interface ────────────────────────────────────────────────────
    public interface Callbacks {
        void onTileOpened(File file);
        void onSelectionChanged(List<File> selectedFiles);
        default void onFilesAdded(List<File> files) {}
        default void onDragStarted(File file) {}
        default void onDragEnded() {}
        /** A LayerTile was dropped on this gallery – save it as a PNG image file. */
        default void onLayerDropped(Layer layer) {}
        /** A file was copied via right-drag in the gallery at a specific position. */
        default void onFileCopied(File copiedFile, int insertIndex) {}
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Callbacks       callbacks;
    private final List<TilePanel> tiles          = new ArrayList<>();
    private final List<File>      selectedImages = new ArrayList<>();
    private       boolean         multiSelectMode = false;
    private       File            activeFile      = null;
    private       java.util.Set<File> dirtyFiles = new java.util.HashSet<>();

    // ── UI refs ───────────────────────────────────────────────────────────────
    private final JPanel      tilesContainer;
    private final JScrollPane galleryScroll;
    private final JPanel      actionRow;

    // =========================================================================
    // Constructor
    // =========================================================================
    public TileGalleryPanel(Callbacks callbacks) {
        this.callbacks = callbacks;
        setLayout(new BorderLayout());
        setBackground(new Color(36, 36, 36));
        setPreferredSize(new Dimension(GALLERY_W, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, AppColors.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 16, 0)));

        // ── Header label ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(42, 42, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        JLabel lbl = new JLabel("  Bilder");
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        header.add(lbl, BorderLayout.CENTER);

        // ── Select-All / Deselect-All (initially hidden) ──────────────────────
        actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        actionRow.setBackground(new Color(42, 42, 42));
        actionRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        JButton selectAllBtn   = galleryBtn("Alle");
        JButton deselectAllBtn = galleryBtn("Keine");
        selectAllBtn  .addActionListener(e -> selectAll());
        deselectAllBtn.addActionListener(e -> deselectAll());
        actionRow.add(selectAllBtn);
        actionRow.add(deselectAllBtn);
        actionRow.setVisible(false);

        // ── North: header + action row ────────────────────────────────────────
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(new Color(42, 42, 42));
        north.add(header);
        north.add(actionRow);
        add(north, BorderLayout.NORTH);

        // ── Tiles container inside scroll pane ────────────────────────────────
        // Override getPreferredSize so JViewport sizes this panel to the viewport width,
        // which causes BoxLayout Y_AXIS to resize tiles accordingly.
        tilesContainer = new JPanel() {
            @Override public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension d = super.getPreferredSize();
                if (getParent() != null && getParent().getWidth() > 0)
                    d.width = getParent().getWidth();
                return d;
            }
        };
        tilesContainer.setLayout(new BoxLayout(tilesContainer, BoxLayout.Y_AXIS));
        tilesContainer.setBackground(new Color(36, 36, 36));
        tilesContainer.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));

        // DnD drop target on tilesContainer: accept file lists
        tilesContainer.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) {
                        callbacks.onFilesAdded(new ArrayList<>(files));
                    }
                    return true;
                } catch (UnsupportedFlavorException | IOException ex) {
                    return false;
                }
            }
        });

        // ── Drop target: accept Layer drags (Case 5: save layer as image) ────
        new DropTarget(tilesContainer, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    Transferable t = dtde.getTransferable();

                    // Handle right-drag within gallery: copy file
                    if (t.isDataFlavorSupported(FILE_AS_ELEMENT_FLAVOR)) {
                        FileForElement ffe = (FileForElement) t.getTransferData(FILE_AS_ELEMENT_FLAVOR);
                        File sourceFile = ffe.file;

                        // Copy file in same directory using BaseSidebarPanel utility
                        File destDir = sourceFile.getParentFile();
                        if (destDir != null) {
                            File destFile = BaseSidebarPanel.copyFileWithUniqueName(sourceFile, destDir);
                            if (destFile != null) {
                                // Calculate insertion index based on drop position
                                Point dropPt = dtde.getLocation();
                                int insertIndex = BaseSidebarPanel.computeDropIndex(tilesContainer, dropPt.y);
                                // Notify callback about the copied file with position
                                callbacks.onFileCopied(destFile, insertIndex);
                                dtde.dropComplete(true);
                                return;
                            }
                        }
                        dtde.dropComplete(false);
                    } else if (t.isDataFlavorSupported(ElementLayerPanel.LAYER_FLAVOR)) {
                        Layer layer = (Layer) t.getTransferData(ElementLayerPanel.LAYER_FLAVOR);
                        callbacks.onLayerDropped(layer);
                        dtde.dropComplete(true);
                    } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        // Handle file drops from external sources or other galleries
                        @SuppressWarnings("unchecked")
                        java.util.List<File> droppedFiles = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        if (droppedFiles != null && !droppedFiles.isEmpty()) {
                            Point dropPt = dtde.getLocation();
                            int insertIndex = BaseSidebarPanel.computeDropIndex(tilesContainer, dropPt.y);
                            // Copy files from external source to current gallery directory
                            for (File sourceFile : droppedFiles) {
                                if (sourceFile.isFile()) {
                                    try {
                                        File destDir = getTileGalleryDirectory();
                                        if (destDir == null) {
                                            dtde.dropComplete(false);
                                            return;
                                        }
                                        // Copy file to destination directory using BaseSidebarPanel utility
                                        File destFile = BaseSidebarPanel.copyFileWithUniqueName(sourceFile, destDir);
                                        if (destFile != null) {
                                            callbacks.onFileCopied(destFile, insertIndex);
                                        }
                                    } catch (Exception ex) {
                                        System.err.println("[ERROR] Failed to copy file: " + ex.getMessage());
                                    }
                                }
                            }
                            dtde.dropComplete(true);
                            return;
                        }
                        dtde.dropComplete(false);
                    } else {
                        dtde.dropComplete(false);
                    }
                } catch (Exception ex) { dtde.dropComplete(false); }
            }
        }, true);

        galleryScroll = new JScrollPane(tilesContainer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        galleryScroll.setBorder(null);
        galleryScroll.getViewport().setBackground(new Color(36, 36, 36));
        galleryScroll.getVerticalScrollBar().setUnitIncrement(14);
        applyDarkScrollBar(galleryScroll.getVerticalScrollBar());
        add(galleryScroll, BorderLayout.CENTER);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Full refresh – called when a new directory is loaded. */
    public void setFiles(List<File> files, File active) {
        activeFile = active;
        tiles.clear();
        tilesContainer.removeAll();
        selectedImages.clear();
        multiSelectMode = false;
        actionRow.setVisible(false);

        for (File f : files) addTile(f);
        tilesContainer.revalidate();
        tilesContainer.repaint();
        SwingUtilities.invokeLater(this::scrollToActiveImpl);
    }

    /** Light update – called when navigating within the same directory. */
    public void setActiveFile(File f) {
        activeFile = f;
        for (TilePanel t : tiles) {
            t.setActive(t.imageFile.equals(f));
            t.repaint();
        }
        // No auto-scroll: callers that need the gallery to follow (e.g. nav
        // buttons) must call scrollToActive() explicitly afterwards.
    }

    /** Scroll the gallery so the currently active tile is fully visible. */
    public void scrollToActive() {
        scrollToActiveImpl();
    }

    public List<File> getSelectedImages() { return new ArrayList<>(selectedImages); }

    /**
     * Mark which files have unsaved changes.
     * These tiles get a red border instead of the normal green/orange one.
     */
    public void setDirtyFiles(java.util.Set<File> dirty) {
        this.dirtyFiles = dirty == null ? new java.util.HashSet<>() : new java.util.HashSet<>(dirty);
        for (TilePanel t : tiles) t.repaint();
    }

    @Override
    public void refresh() {
        /* TileGalleryPanel uses setFiles() and setActiveFile() for updates, not a generic refresh(). */
    }

    /**
     * Aktualisiert das Thumbnail für eine Datei mit dem übergebenen Bild direkt
     * (ohne Disk-Read). Skaliert wie loadThumbAsync().
     */
    public void refreshThumbnailFor(File f, BufferedImage liveImage) {
        if (liveImage == null) return;
        for (TilePanel t : tiles) {
            if (t.imageFile.equals(f)) {
                t.setLiveThumbnail(liveImage);
                break;
            }
        }
    }

    /**
     * Add new files to the gallery without clearing existing ones.
     * Only adds files not already present.
     */
    public void addFiles(List<File> newFiles) {
        boolean changed = false;
        for (File f : newFiles) {
            boolean alreadyPresent = tiles.stream().anyMatch(t -> t.imageFile.equals(f));
            if (!alreadyPresent) {
                addTile(f);
                changed = true;
            }
        }
        if (changed) {
            tilesContainer.revalidate();
            tilesContainer.repaint();
        }
    }

    /**
     * Add a file at a specific index position in the gallery.
     * Used for maintaining drop position when copying files.
     */
    public void addFileAtIndex(File f, int index) {
        boolean alreadyPresent = tiles.stream().anyMatch(t -> t.imageFile.equals(f));
        if (!alreadyPresent) {
            addTileAtIndex(f, index);
            tilesContainer.revalidate();
            tilesContainer.repaint();
        }
    }

    private void addTile(File f) {
        TilePanel tp = new TilePanel(f);
        tp.setActive(f.equals(activeFile));
        tiles.add(tp);
        tilesContainer.add(tp);
        tilesContainer.add(Box.createVerticalStrut(5));
    }

    /** Insert a tile at a specific index (for drop positioning). */
    private void addTileAtIndex(File f, int tileIndex) {
        TilePanel tp = new TilePanel(f);
        tp.setActive(f.equals(activeFile));
        // Clamp index to valid range
        int idx = Math.max(0, Math.min(tileIndex, tiles.size()));
        tiles.add(idx, tp);
        // Each tile has: TilePanel + 5px gap, so position in tilesContainer is 2*idx
        int containerIdx = 2 * idx;
        tilesContainer.add(tp, containerIdx);
        tilesContainer.add(Box.createVerticalStrut(5), containerIdx + 1);
    }

    // =========================================================================
    // Internal actions
    // =========================================================================
    private void selectAll() {
        selectedImages.clear();
        for (TilePanel t : tiles) {
            t.setSelected(true);
            selectedImages.add(t.imageFile);
            t.repaint();
        }
        callbacks.onSelectionChanged(getSelectedImages());
    }

    private void deselectAll() {
        selectedImages.clear();
        multiSelectMode = false;
        actionRow.setVisible(false);
        for (TilePanel t : tiles) {
            t.setSelected(false);
            t.showCheckbox(false);
            t.repaint();
        }
        callbacks.onSelectionChanged(getSelectedImages());
    }

    private void enterMultiSelectMode() {
        multiSelectMode = true;
        actionRow.setVisible(true);
        for (TilePanel t : tiles) t.showCheckbox(true);
    }

    void onTileClicked(TilePanel tile, boolean shift) {
        if (shift) {
            if (!multiSelectMode) enterMultiSelectMode();
            boolean nowSel = !tile.isInSelection;
            tile.setSelected(nowSel);
            if (nowSel) {
                if (!selectedImages.contains(tile.imageFile)) selectedImages.add(tile.imageFile);
            } else {
                selectedImages.remove(tile.imageFile);
                if (selectedImages.isEmpty()) deselectAll();
            }
            tile.repaint();
            callbacks.onSelectionChanged(getSelectedImages());
        } else {
            // Single click → open image
            setActiveFile(tile.imageFile);
            callbacks.onTileOpened(tile.imageFile);
        }
    }

    private void scrollToActiveImpl() {
        for (TilePanel t : tiles) {
            if (t.isActive) {
                Rectangle r = SwingUtilities.convertRectangle(t, new Rectangle(t.getSize()), tilesContainer);
                // scrollRectToVisible on the viewport only scrolls downward reliably;
                // set the view position directly so upward navigation also works.
                int viewH  = galleryScroll.getViewport().getHeight();
                int contH  = tilesContainer.getHeight();
                int targetY = r.y + r.height / 2 - viewH / 2;
                targetY = Math.max(0, Math.min(targetY, Math.max(0, contH - viewH)));
                galleryScroll.getViewport().setViewPosition(new Point(0, targetY));
                break;
            }
        }
    }

    /**
     * Get the current gallery directory from the tiles.
     */
    private File getTileGalleryDirectory() {
        if (activeFile != null) {
            return activeFile.getParentFile();
        }
        if (!tiles.isEmpty()) {
            return tiles.get(0).imageFile.getParentFile();
        }
        return null;
    }

    // =========================================================================
    // TilePanel (inner)
    // =========================================================================
    class TilePanel extends JPanel {

        final File imageFile;
        private BufferedImage thumbnail;
        boolean isActive      = false;
        boolean isInSelection = false;
        boolean isClicked     = false;
        private final JCheckBox checkbox;

        // For drag detection
        private Point   dragStart       = null;
        private boolean rightButtonDown = false;
        private boolean rightDrag       = false; // set before exportAsDrag to pick correct flavor
        private static final int DRAG_THRESHOLD = 6;

        TilePanel(File f) {
            this.imageFile = f;
            setLayout(null);
            setPreferredSize(new Dimension(TILE_W, TILE_H));
            // Don't set MaximumSize/MinimumSize — let tiles scale with gallery width
            setOpaque(false);
            setFocusable(true);

            // Checkbox – top-right corner
            checkbox = new JCheckBox();
            checkbox.setOpaque(false);
            checkbox.setVisible(false);
            checkbox.setFocusable(false);
            checkbox.setBounds(TILE_W - 22, 3, 18, 18);
            checkbox.addActionListener(e -> onTileClicked(this, true));
            add(checkbox);

            // DnD: left-drag → javaFileListFlavor; right-drag → FILE_AS_ELEMENT_FLAVOR
            setTransferHandler(new TransferHandler() {
                @Override public int getSourceActions(JComponent c) { return COPY; }
                @Override protected Transferable createTransferable(JComponent c) {
                    if (rightDrag) {
                        FileForElement ffe = new FileForElement(imageFile);
                        return new Transferable() {
                            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{FILE_AS_ELEMENT_FLAVOR}; }
                            @Override public boolean isDataFlavorSupported(DataFlavor f) { return FILE_AS_ELEMENT_FLAVOR.equals(f); }
                            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                                if (!FILE_AS_ELEMENT_FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
                                return ffe;
                            }
                        };
                    } else {
                        return new Transferable() {
                            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
                            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.javaFileListFlavor.equals(f); }
                            @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                                if (!DataFlavor.javaFileListFlavor.equals(f)) throw new UnsupportedFlavorException(f);
                                List<File> list = new ArrayList<>();
                                list.add(imageFile);
                                return list;
                            }
                        };
                    }
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragStart       = e.getPoint();
                    rightButtonDown = SwingUtilities.isRightMouseButton(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    dragStart       = null;
                    rightButtonDown = false;
                    callbacks.onDragEnded();
                }
                @Override public void mouseClicked(MouseEvent e) {
                    // Clear clicked state from other tiles and set this one
                    for (TilePanel t : tiles) {
                        if (t != TilePanel.this) {
                            t.isClicked = false;
                        }
                    }
                    TilePanel.this.isClicked = true;
                    TilePanel.this.requestFocus();
                    onTileClicked(TilePanel.this, e.isShiftDown());
                    repaint();
                }
                @Override public void mouseEntered(MouseEvent e) { repaint(); }
                @Override public void mouseExited (MouseEvent e) { repaint(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        int dx = Math.abs(e.getX() - dragStart.x);
                        int dy = Math.abs(e.getY() - dragStart.y);
                        if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                            boolean wasRight = rightButtonDown;
                            dragStart = null; rightButtonDown = false;
                            callbacks.onDragStarted(imageFile);
                            rightDrag = wasRight;
                            getTransferHandler().exportAsDrag(TilePanel.this, e, TransferHandler.COPY);
                            rightDrag = false;
                        }
                    }
                }
            });

            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                        deleteTile();
                    }
                }
            });

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            loadThumbAsync();
        }

        @Override
        public void doLayout() {
            int w = getWidth() > 0 ? getWidth() : TILE_W;
            checkbox.setBounds(w - 22, 3, 18, 18);
        }

        void setActive(boolean a)        { isActive = a; }
        void setSelected(boolean s)      { isInSelection = s; checkbox.setSelected(s); }
        void showCheckbox(boolean show)  { checkbox.setVisible(show); }

        private void deleteTile() {
            int result = JOptionPane.showConfirmDialog(
                    TileGalleryPanel.this,
                    "Möchten Sie dieses Element wirklich löschen?",
                    "Bestätigung",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                // Find and remove this tile
                int tileIndex = tiles.indexOf(this);
                if (tileIndex >= 0) {
                    tiles.remove(tileIndex);
                    // Remove tile and its strut from container
                    int compIndex = tilesContainer.getComponentZOrder(this);
                    if (compIndex >= 0) {
                        tilesContainer.remove(compIndex);
                        // Also remove the strut after it if present
                        if (compIndex < tilesContainer.getComponentCount()) {
                            Component nextComp = tilesContainer.getComponent(compIndex);
                            if (nextComp instanceof Box.Filler) {
                                tilesContainer.remove(compIndex);
                            }
                        }
                    }
                    tilesContainer.revalidate();
                    tilesContainer.repaint();
                }
            }
        }

        private void loadThumbAsync() {
            new SwingWorker<BufferedImage, Void>() {
                @Override protected BufferedImage doInBackground() throws Exception {
                    BufferedImage img = ImageIO.read(imageFile);
                    if (img == null) return null;
                    int maxW = TILE_W - 10;
                    int maxH = THUMB_H;
                    double s = Math.min((double) maxW / img.getWidth(),
                                        (double) maxH / img.getHeight());
                    int w = Math.max(1, (int)(img.getWidth()  * s));
                    int h = Math.max(1, (int)(img.getHeight() * s));
                    BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = t.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(img, 0, 0, w, h, null);
                    g2.dispose();
                    return t;
                }
                @Override protected void done() {
                    try { thumbnail = get(); repaint(); }
                    catch (InterruptedException | ExecutionException ignored) {}
                }
            }.execute();
        }

        void setLiveThumbnail(BufferedImage src) {
            if (src == null) return;
            int maxW = TILE_W - 10;
            int maxH = THUMB_H;
            double s = Math.min((double) maxW / src.getWidth(),
                                (double) maxH / src.getHeight());
            int w = Math.max(1, (int)(src.getWidth()  * s));
            int h = Math.max(1, (int)(src.getHeight() * s));
            BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = t.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, w, h, null);
            g2.dispose();
            thumbnail = t;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();

            boolean hover = getMousePosition() != null;

            // ── Background ───────────────────────────────────────────────────
            Color bg = isActive ? AppColors.TILE_ACTIVE_BG
                     : hover   ? AppColors.TILE_HOVER_BG
                     : AppColors.TILE_DEFAULT_BG;
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w - 1, TILE_H - 1, 8, 8);

            // ── Thumbnail ────────────────────────────────────────────────────
            int thumbTop = 4;
            if (thumbnail != null) {
                int tx = (w - thumbnail.getWidth())  / 2;
                int ty = thumbTop + (THUMB_H - thumbnail.getHeight()) / 2;
                Shape clip = new java.awt.geom.RoundRectangle2D.Float(
                        3, thumbTop, w - 6, THUMB_H, 4, 4);
                g2.clip(clip);
                g2.drawImage(thumbnail, tx, ty, null);
                g2.setClip(null);
            } else {
                // Placeholder while loading
                g2.setColor(AppColors.TILE_PLACEHOLDER);
                g2.fillRoundRect(3, thumbTop, w - 6, THUMB_H, 4, 4);
                g2.setColor(AppColors.TEXT_MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                String ph = "Lädt…";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(ph, (w - fm.stringWidth(ph)) / 2,
                        thumbTop + THUMB_H / 2 + fm.getAscent() / 2);
            }

            // ── Filename label ────────────────────────────────────────────────
            String name = imageFile.getName();
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(hover ? AppColors.TEXT : AppColors.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            // Truncate
            while (name.length() > 4 && fm.stringWidth(name) > w - 10)
                name = name.substring(0, name.length() - 5) + "…";
            g2.drawString(name, (w - fm.stringWidth(name)) / 2,
                    thumbTop + THUMB_H + 15);

            // ── Border ───────────────────────────────────────────────────────
            boolean isDirty = dirtyFiles.contains(imageFile);
            g2.setStroke(new BasicStroke(2f));
            if (isActive && isDirty) {
                g2.setColor(AppColors.DANGER);
                g2.drawRoundRect(0, 0, w - 2, TILE_H - 2, 8, 8);
                g2.setColor(AppColors.SUCCESS);
                g2.drawRoundRect(2, 2, w - 6, TILE_H - 6, 6, 6);
            } else if (isDirty) {
                g2.setColor(AppColors.DANGER);            // red – ungespeicherte Änderungen
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 8, 8);
            } else if (isClicked) {
                g2.setColor(new Color(255, 255, 0));      // yellow – clicked state
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 8, 8);
            } else if (isActive) {
                g2.setColor(AppColors.SUCCESS);           // green
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 8, 8);
            } else if (isInSelection) {
                g2.setColor(AppColors.SELECTION);
                g2.drawRoundRect(1, 1, w - 3, TILE_H - 3, 8, 8);
            } else if (hover) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(AppColors.BORDER);
                g2.drawRoundRect(0, 0, w - 2, TILE_H - 2, 8, 8);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // Dark scrollbar – also used by SelectiveAlphaEditor for the main viewport
    // =========================================================================
    public static void applyDarkScrollBar(JScrollBar bar) {
        bar.setBackground(new Color(30, 30, 30));
        bar.setUI(new DarkScrollBarUI());
    }

    public static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor            = new Color(75, 75, 75);
            thumbDarkShadowColor  = new Color(30, 30, 30);
            thumbHighlightColor   = new Color(90, 90, 90);
            thumbLightShadowColor = new Color(55, 55, 55);
            trackColor            = new Color(30, 30, 30);
            trackHighlightColor   = new Color(40, 40, 40);
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
        private static JButton zeroBtn() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize (new Dimension(0, 0));
            b.setMaximumSize (new Dimension(0, 0));
            return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(105, 105, 105) : new Color(70, 70, 70));
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(new Color(30, 30, 30));
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    // ── Small button factory ──────────────────────────────────────────────────
    private static JButton galleryBtn(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? AppColors.BTN_HOVER : AppColors.BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("SansSerif", Font.PLAIN, 10));
        b.setForeground(AppColors.TEXT_MUTED);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(44, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
