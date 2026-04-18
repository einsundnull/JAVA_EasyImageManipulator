package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/**
 * Abstract base class for all sidebar panels (TileGalleryPanel, ElementLayerPanel,
 * ScenesPanel, MapsPanel). Provides common utilities for:
 * - Standard dark header with title + optional close button
 * - JScrollPane with dark-styled scrollbars
 * - Right-click drag-to-copy mechanism
 * - File copying with unique name generation
 * - Subclasses implement abstract refresh() method
 */
public abstract class BaseSidebarPanel extends JPanel {

    public static final int DEFAULT_PANEL_W = 174;
    private static final int DRAG_THRESHOLD = 6;

    // ── DragToCopy: DataFlavor für File-Kopier-Drags (Rechtsklick) ───────────
    /**
     * Wrapper-Klasse damit FILE_COPY_FLAVOR eindeutig von javaFileListFlavor unterscheidbar ist.
     */
    public static final class FileForCopy {
        public final File file;
        public FileForCopy(File f) { file = f; }
    }

    /**
     * DataFlavor für Rechtsklick-Drag-to-Copy in allen Sidebar-Panels.
     * Unterscheidbar von normalen File-Drags (javaFileListFlavor).
     */
    public static final DataFlavor FILE_COPY_FLAVOR =
        new DataFlavor(FileForCopy.class, "FileForCopy");

    // ── Abstract method: Subclasses must implement ────────────────────────────
    /**
     * Refresh the panel's content. Called when underlying data changes.
     * Implementation varies by subclass (setFiles, refresh(List), etc.).
     */
    public abstract void refresh();

    // ── Icon Helper ───────────────────────────────────────────────────────────
    /**
     * Loads an icon from resources/icons and scales it to the specified size.
     */
    protected static ImageIcon loadIcon(String iconName, int size) {
        try {
            String iconPath = "resources/icons/" + iconName;
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                new java.io.File(iconPath));
            if (img == null) return null;

            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();

            return new ImageIcon(scaled);
        } catch (Exception e) {
            System.err.println("[BaseSidebarPanel] Failed to load icon: " + iconName);
            return null;
        }
    }

    // ── Header-Builder ────────────────────────────────────────────────────────
    /**
     * Creates a standard dark header panel with title and optional close button.
     *
     * @param title     Display text for the header
     * @param onClose   Runnable to call when close button clicked.
     *                  If null, no close button is shown.
     * @return JPanel configured as a header (dark BG, border, layout)
     */
    protected JPanel buildSidebarHeader(String title, Runnable onClose) {
        return buildSidebarHeader(title, null, null, onClose);
    }

    /**
     * Creates a dark header panel with title, refresh, and close buttons.
     */
    protected JPanel buildSidebarHeader(String title, Runnable onRefresh, Runnable onClose) {
        return buildSidebarHeader(title, onRefresh, null, onClose);
    }

    /**
     * Creates a dark header panel with title and optional refresh, all/only toggle, and close buttons.
     *
     * @param title         Display text for the header
     * @param onRefresh     Runnable to call when refresh button is clicked.
     *                      If null, no refresh button is shown.
     * @param onAllOnlyToggle  Callback when All/Only button is toggled.
     *                      If null, no toggle button is shown.
     *                      Boolean parameter: true = All, false = Only
     * @param onClose       Runnable to call when close button is clicked.
     *                      If null, no close button is shown.
     * @return JPanel configured as a header (dark BG, border, layout)
     */
    protected JPanel buildSidebarHeader(String title, Runnable onRefresh,
                                        java.util.function.Consumer<Boolean> onAllOnlyToggle,
                                        Runnable onClose) {
        return buildSidebarHeader(title, onRefresh, onAllOnlyToggle, onClose, null);
    }

    protected JPanel buildSidebarHeader(String title, Runnable onRefresh,
                                        java.util.function.Consumer<Boolean> onAllOnlyToggle,
                                        Runnable onClose, Runnable onTitleClick) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(42, 42, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        header.setPreferredSize(new Dimension(DEFAULT_PANEL_W, 28));

        JLabel titleLbl = new JLabel("  " + title);
        titleLbl.setForeground(AppColors.TEXT_MUTED);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLbl.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        if (onTitleClick != null) {
            titleLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            titleLbl.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onTitleClick.run(); }
                @Override public void mouseEntered(MouseEvent e) { titleLbl.setForeground(AppColors.TEXT); }
                @Override public void mouseExited (MouseEvent e) { titleLbl.setForeground(AppColors.TEXT_MUTED); }
            });
        }
        header.add(titleLbl, BorderLayout.CENTER);

        // ── East side: all/only toggle (optional) + refresh button (optional) + close button (optional) ─
        if (onRefresh != null || onAllOnlyToggle != null || onClose != null) {
            JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            eastPanel.setBackground(new Color(42, 42, 42));
            eastPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

            // All/Only toggle button using link-alt icon
            if (onAllOnlyToggle != null) {
                final boolean[] isAll = {false};  // Default: Only
                ImageIcon icon = loadIcon("link-alt.png", 14);
                JLabel toggleBtn = new JLabel(icon);
                toggleBtn.setForeground(AppColors.TEXT_MUTED);
                toggleBtn.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
                toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                toggleBtn.setToolTipText("Only (linked) / All (unlinked) Toggle");
                toggleBtn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        isAll[0] = !isAll[0];
                        onAllOnlyToggle.accept(isAll[0]);
                        // Update icon opacity to show state
                        toggleBtn.setForeground(isAll[0] ? Color.WHITE : AppColors.TEXT_MUTED);
                    }
                    @Override public void mouseEntered(MouseEvent e) { toggleBtn.setForeground(Color.WHITE); }
                    @Override public void mouseExited (MouseEvent e) { toggleBtn.setForeground(isAll[0] ? Color.WHITE : AppColors.TEXT_MUTED); }
                });
                eastPanel.add(toggleBtn);
            }

            if (onRefresh != null) {
                JLabel refreshBtn = new JLabel("⟳");
                refreshBtn.setForeground(AppColors.TEXT_MUTED);
                refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
                refreshBtn.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
                refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                refreshBtn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onRefresh.run(); }
                    @Override public void mouseEntered(MouseEvent e) { refreshBtn.setForeground(Color.WHITE); }
                    @Override public void mouseExited (MouseEvent e) { refreshBtn.setForeground(AppColors.TEXT_MUTED); }
                });
                eastPanel.add(refreshBtn);
            }

            if (onClose != null) {
                JLabel closeBtn = new JLabel("×");
                closeBtn.setForeground(AppColors.TEXT_MUTED);
                closeBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
                closeBtn.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 0));
                closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                closeBtn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onClose.run(); }
                    @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(Color.WHITE); }
                    @Override public void mouseExited (MouseEvent e) { closeBtn.setForeground(AppColors.TEXT_MUTED); }
                });
                eastPanel.add(closeBtn);
            }

            header.add(eastPanel, BorderLayout.EAST);
            header.putClientProperty("eastPanel", eastPanel);
        } else {
            // Always provide an east panel so addAddButton() can insert into it later
            JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            eastPanel.setBackground(new Color(42, 42, 42));
            eastPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
            header.add(eastPanel, BorderLayout.EAST);
            header.putClientProperty("eastPanel", eastPanel);
        }
        return header;
    }

    /**
     * Adds a "+" icon button to the east panel of an existing header JPanel.
     * Must be called after buildSidebarHeader() — relies on the "eastPanel" client property.
     */
    protected static void addAddButton(JPanel header, Runnable onAdd) {
        Object prop = header.getClientProperty("eastPanel");
        if (!(prop instanceof JPanel)) return;
        JPanel east = (JPanel) prop;
        JLabel addBtn = new JLabel("+");
        addBtn.setForeground(AppColors.TEXT_MUTED);
        addBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        addBtn.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.setToolTipText("Neu erstellen");
        addBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onAdd.run(); }
            @Override public void mouseEntered(MouseEvent e) { addBtn.setForeground(java.awt.Color.WHITE); }
            @Override public void mouseExited (MouseEvent e) { addBtn.setForeground(AppColors.TEXT_MUTED); }
        });
        east.add(addBtn, 0);
        east.revalidate();
        east.repaint();
    }

    // ── ScrollPane-Builder ─────────────────────────────────────────────────────
    /**
     * Creates a JScrollPane with dark-styled scrollbars (matching TransparencyTool theme).
     */
    protected JScrollPane buildSidebarScrollPane(JPanel container) {
        JScrollPane sp = new JScrollPane(container,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(new Color(36, 36, 36));
        sp.setBackground(new Color(36, 36, 36));
        sp.getVerticalScrollBar().setUnitIncrement(14);
        TileGalleryPanel.applyDarkScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    // ── DragToCopy-Utilities ───────────────────────────────────────────────────

    /**
     * Installs right-click-drag support on a JComponent to create a Transferable for copying.
     * When dragging with the right mouse button exceeds DRAG_THRESHOLD, a drag gesture is started.
     *
     * @param item                  Component to install drag source on
     * @param transferableFactory   Supplier that creates the Transferable (called during drag)
     * @param onDragStarted         Optional Runnable called when drag begins (can be null)
     */
    protected static void installDragSource(JComponent item,
                                            Supplier<Transferable> transferableFactory,
                                            Runnable onDragStarted) {
        Point[] dragStart = {null};
        boolean[] rightDown = {false};
        boolean[] rightDrag = {false};

        item.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) {
                // Only export transferable if this is a right-drag
                if (rightDrag[0]) return transferableFactory.get();
                return null;
            }
        });

        item.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragStart[0]  = e.getPoint();
                rightDown[0]  = SwingUtilities.isRightMouseButton(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragStart[0] = null;
                rightDown[0] = false;
            }
        });

        item.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart[0] == null) return;
                int dx = Math.abs(e.getX() - dragStart[0].x);
                int dy = Math.abs(e.getY() - dragStart[0].y);
                if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                    boolean wasRight = rightDown[0];
                    dragStart[0] = null;
                    rightDown[0] = false;
                    if (onDragStarted != null) onDragStarted.run();
                    rightDrag[0] = wasRight;
                    item.getTransferHandler().exportAsDrag(item, e, TransferHandler.COPY);
                    rightDrag[0] = false;
                }
            }
        });
    }

    /**
     * Copies a file to a target directory, appending "_copy_N" suffix if the destination already exists.
     * Uses Java NIO for the actual file copy operation.
     *
     * @param source    Source file to copy
     * @param targetDir Destination directory
     * @return The destination file if successful, null if copy failed
     */
    public static File copyFileWithUniqueName(File source, File targetDir) {
        try {
            String name = source.getName();
            int lastDot = name.lastIndexOf('.');
            String base = lastDot > 0 ? name.substring(0, lastDot) : name;
            String ext  = lastDot > 0 ? name.substring(lastDot)    : "";
            File dest = new File(targetDir, name);
            for (int i = 1; dest.exists(); i++) {
                dest = new File(targetDir, base + "_copy_" + i + ext);
            }
            java.nio.file.Files.copy(source.toPath(), dest.toPath());
            return dest;
        } catch (IOException ex) {
            System.err.println("[BaseSidebarPanel] copyFileWithUniqueName failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Computes the visual insert index in a BoxLayout Y_AXIS container based on drop Y position.
     * Used for calculating where to insert a newly dropped item in the list.
     *
     * @param container JPanel with BoxLayout Y_AXIS containing items
     * @param dropY     Drop Y-coordinate in container space
     * @return Insert index (0 = insert before first item, etc.)
     */
    protected static int computeDropIndex(JPanel container, int dropY) {
        int idx = 0;
        for (Component c : container.getComponents()) {
            // Count only visual items (JPanel, JLabel), skip struts
            if (c instanceof JPanel || (c instanceof JLabel && !(c instanceof Box.Filler))) {
                Rectangle b = c.getBounds();
                if (dropY < b.y + b.height / 2) return idx;
                idx++;
            }
        }
        return idx;
    }

    // ── Preload-on-Hover Utilities ─────────────────────────────────────────────

    /**
     * Callback interface for preload operations.
     */
    public interface PreloadCallback {
        /**
         * Called when user hovers over an item.
         * Implementation should start preloading asynchronously.
         * @return A handle to cancel the preload if user moves away
         */
        PreloadHandle onHoverStart();
    }

    /**
     * Handle for controlling an active preload operation.
     */
    public interface PreloadHandle {
        /**
         * Cancel the preload operation.
         */
        void cancel();

        /**
         * Check if preload is still active.
         */
        boolean isActive();
    }

    /**
     * Installs hover-based preloading on a JComponent.
     * When user hovers over the component, a preload callback is triggered.
     * When user leaves, the preload is cancelled.
     *
     * @param item              Component to monitor for hover
     * @param preloadCallback   Callback that starts preloading
     */
    protected static void installPreloadOnHover(JComponent item, PreloadCallback preloadCallback) {
        PreloadHandle[] activeHandle = {null};

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // Cancel any existing preload
                if (activeHandle[0] != null && activeHandle[0].isActive()) {
                    activeHandle[0].cancel();
                }
                // Start new preload
                activeHandle[0] = preloadCallback.onHoverStart();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Cancel preload when leaving
                if (activeHandle[0] != null && activeHandle[0].isActive()) {
                    activeHandle[0].cancel();
                    activeHandle[0] = null;
                }
            }
        });
    }
}
