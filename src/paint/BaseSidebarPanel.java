package paint;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import javax.swing.*;

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
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(42, 42, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));
        header.setPreferredSize(new Dimension(DEFAULT_PANEL_W, 28));

        JLabel titleLbl = new JLabel("  " + title);
        titleLbl.setForeground(AppColors.TEXT_MUTED);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLbl.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        header.add(titleLbl, BorderLayout.CENTER);

        if (onClose != null) {
            JLabel closeBtn = new JLabel("×");
            closeBtn.setForeground(AppColors.TEXT_MUTED);
            closeBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
            closeBtn.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onClose.run(); }
                @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(Color.WHITE); }
                @Override public void mouseExited (MouseEvent e) { closeBtn.setForeground(AppColors.TEXT_MUTED); }
            });
            header.add(closeBtn, BorderLayout.EAST);
        }
        return header;
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
