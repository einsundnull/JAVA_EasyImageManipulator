package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Szenen-Browser Panel für TransparencyTool und GameII Szenen.
 * Ähnlich wie TileGalleryPanel, aber für Szenen-Management.
 *
 * Features:
 * - Zwei Tabs: "Tool Scenes" (eigene Projekte) + "Game Scenes" (GameII)
 * - Resizable mit Splitter (wie TileGalleryPanel)
 * - Szenen als anklickbare Tiles anzeigen
 * - Doppelklick zum Laden
 * - Rechtsklick-Menü für Aktionen
 * - Dark UI Styling
 */
public class ScenesPanel extends BaseSidebarPanel {

    private Callbacks callbacks;
    private JTabbedPane tabbedPane;
    private SceneListPanel toolScenesPanel;
    private SceneListPanel gameScenesPanel;
    private boolean showAll = false;
    private File linkedScene = null;

    public interface Callbacks {
        /** Lädt eine Szene in den aktuellen Canvas. */
        void loadScene(File sceneFile);
        /** Kopiert eine Szene zu einem anderen Projekt/Game. */
        void copyScene(File source, String targetProject);
        /** Löscht eine Szene. */
        void deleteScene(File sceneFile);
        /** Wird aufgerufen wenn Szenen aktualisiert werden sollen. */
        void refreshScenes();
        /** Erstellt eine neue Szene mit einem Hintergrundbild und Elementen. */
        void createNewScene(File backgroundImage, List<Layer> elements, String sceneName);
    }

    public ScenesPanel(Callbacks cb, Runnable onClose) {
        this.callbacks = cb;
        initUI(onClose);
    }

    private void initUI(Runnable onClose) {
        setLayout(new BorderLayout());
        setBackground(AppColors.BG_PANEL);
        setOpaque(true);

        // Header with All/Only toggle, refresh and close buttons
        JPanel header = buildSidebarHeader("Szenen", this::refreshScenes, isAll -> {
            showAll = isAll;
            toolScenesPanel.updateShowAll(showAll);
            gameScenesPanel.updateShowAll(showAll);
        }, onClose);
        add(header, BorderLayout.NORTH);

        // Tabbed Pane für Tool/Game Scenes
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(AppColors.BG_PANEL);
        tabbedPane.setForeground(AppColors.TEXT);
        tabbedPane.setOpaque(true);

        // Tab 1: Tool Scenes
        toolScenesPanel = new SceneListPanel(
            "Tool Scenes",
            () -> SceneLocator.getToolProjects(),
            (project) -> SceneLocator.getToolScenes(project),
            callbacks,
            this
        );
        tabbedPane.addTab("Tool Scenes", toolScenesPanel);

        // Tab 2: Game Scenes
        gameScenesPanel = new SceneListPanel(
            "Game Scenes",
            () -> SceneLocator.getAvailableGames(),
            (gameName) -> SceneLocator.getGameScenes(gameName),
            callbacks,
            this
        );
        tabbedPane.addTab("Game Scenes", gameScenesPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Aktualisiert die Szenen-Listen.
     */
    @Override
    public void refresh() {
        toolScenesPanel.refresh();
        gameScenesPanel.refresh();
    }

    /**
     * Refresh Szenen-Liste vom Filesystem (called by refresh button in header).
     */
    public void refreshScenes() {
        refresh();
    }

    /**
     * Set the linked scene directory for the All/Only toggle.
     */
    public void setLinkedScene(File sceneDir) {
        this.linkedScene = sceneDir;
        toolScenesPanel.setLinkedScene(sceneDir);
        gameScenesPanel.setLinkedScene(sceneDir);
    }

    // =====================================================
    // Inner Class: SceneListPanel
    // =====================================================

    /**
     * Einzelnes Tab für eine Szenen-Quelle (Tool oder Game).
     */
    private static class SceneListPanel extends JPanel {

        private String source;
        private java.util.function.Supplier<List<String>> projectsSupplier;
        private java.util.function.Function<String, List<File>> scenesSupplier;
        private Callbacks callbacks;
        private boolean showAll = false;
        private File linkedScene = null;

        private JComboBox<String> projectCombo;
        private JPanel scenesContainer;
        private JScrollPane scrollPane;
        private java.util.List<SceneItem> items = new ArrayList<>();
        private ScenesPanel parentPanel;  // Reference to parent for dialogs

        SceneListPanel(String source,
                      java.util.function.Supplier<List<String>> projects,
                      java.util.function.Function<String, List<File>> scenes,
                      Callbacks cb,
                      ScenesPanel parent) {
            this.source = source;
            this.projectsSupplier = projects;
            this.scenesSupplier = scenes;
            this.callbacks = cb;
            this.parentPanel = parent;

            initUI();
        }

        private void initUI() {
            setLayout(new BorderLayout(4, 4));
            setBackground(AppColors.BG_PANEL);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

            // Project/Game Selector Combo
            JLabel label = new JLabel(source.split(" ")[0] + ":");
            label.setForeground(AppColors.TEXT);

            projectCombo = new JComboBox<>();
            projectCombo.setBackground(AppColors.BTN_BG);
            projectCombo.setForeground(AppColors.TEXT);
            projectCombo.addActionListener(e -> refreshScenesList());

            JPanel topPanel = new JPanel(new BorderLayout(4, 0));
            topPanel.setOpaque(false);
            topPanel.add(label, BorderLayout.WEST);
            topPanel.add(projectCombo, BorderLayout.CENTER);

            // Scenes Container (scrollable)
            scenesContainer = new JPanel();
            scenesContainer.setLayout(new BoxLayout(scenesContainer, BoxLayout.Y_AXIS));
            scenesContainer.setBackground(AppColors.BG_PANEL);
            scenesContainer.setOpaque(true);

            scrollPane = new JScrollPane(scenesContainer);
            scrollPane.setBackground(AppColors.BG_PANEL);
            scrollPane.getViewport().setBackground(AppColors.BG_PANEL);
            TileGalleryPanel.applyDarkScrollBar(scrollPane.getVerticalScrollBar());
            TileGalleryPanel.applyDarkScrollBar(scrollPane.getHorizontalScrollBar());

            add(topPanel, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);

            // ── Drop support: accept images and layers to create new scenes ────
            new DropTarget(scenesContainer, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    System.out.println("[DEBUG] Drop detected in SceneListPanel");
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    try {
                        Transferable t = dtde.getTransferable();
                        System.out.println("[DEBUG] Transferable received");

                        // Handle IMAGE drop (Java File List Flavor from TileGalleryPanel)
                        if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                            System.out.println("[DEBUG] javaFileListFlavor detected");
                            @SuppressWarnings("unchecked")
                            List<File> files = (List<File>) t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                            if (!files.isEmpty()) {
                                File imageFile = files.get(0);
                                System.out.println("[DEBUG] File: " + imageFile.getAbsolutePath());
                                showCreateSceneDialog(imageFile, null);  // null elements = use current
                                dtde.dropComplete(true);
                            } else {
                                dtde.dropComplete(false);
                            }
                        }
                        // Handle LAYER drop (LAYER_FLAVOR)
                        else if (t.isDataFlavorSupported(ElementLayerPanel.LAYER_FLAVOR)) {
                            System.out.println("[DEBUG] LAYER_FLAVOR detected");
                            Layer layer = (Layer) t.getTransferData(ElementLayerPanel.LAYER_FLAVOR);
                            List<Layer> elements = new ArrayList<>();
                            elements.add(layer);
                            showCreateSceneDialog(null, elements);  // null image = use current
                            dtde.dropComplete(true);
                        }
                        else {
                            System.out.println("[DEBUG] No supported flavors detected");
                            System.out.println("[DEBUG] Available flavors:");
                            for (DataFlavor df : t.getTransferDataFlavors()) {
                                System.out.println("  - " + df.toString());
                            }
                            dtde.dropComplete(false);
                        }
                    } catch (Exception ex) {
                        System.err.println("[ERROR] Drop failed: " + ex.getMessage());
                        ex.printStackTrace();
                        dtde.dropComplete(false);
                    }
                }
            }, true);

            refresh();
        }

        private void showCreateSceneDialog(File imageFile, List<Layer> layers) {
            // Generate auto scene name
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss");
            String sceneName = "Scene_" + sdf.format(new java.util.Date());

            // Create dialog
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentPanel);
            JDialog dialog = new JDialog((java.awt.Frame) window, "Neue Szene erstellen", true);
            dialog.setSize(350, 150);
            dialog.setLocationRelativeTo(window);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setResizable(false);

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            content.setBackground(AppColors.BG_PANEL);

            // Input panel
            JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
            inputPanel.setOpaque(false);

            JLabel label = new JLabel("Szenen-Name:");
            label.setForeground(AppColors.TEXT);

            JTextField field = new JTextField(sceneName, 30);
            field.setBackground(AppColors.BTN_BG);
            field.setForeground(AppColors.TEXT);
            field.setCaretColor(AppColors.ACCENT);
            field.selectAll();  // Select all text for easy editing

            inputPanel.add(label, BorderLayout.WEST);
            inputPanel.add(field, BorderLayout.CENTER);

            // Button panel
            JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 5, 0));
            buttons.setOpaque(false);

            javax.swing.JButton okBtn = new javax.swing.JButton("OK");
            okBtn.setBackground(AppColors.ACCENT);
            okBtn.setForeground(AppColors.TEXT);
            okBtn.addActionListener(e -> {
                String name = field.getText().trim();
                if (name.isEmpty()) {
                    name = sceneName;
                }
                dialog.dispose();
                callbacks.createNewScene(imageFile, layers, name);
            });

            javax.swing.JButton cancelBtn = new javax.swing.JButton("Abbrechen");
            cancelBtn.setBackground(AppColors.BTN_BG);
            cancelBtn.setForeground(AppColors.TEXT);
            cancelBtn.addActionListener(e -> dialog.dispose());

            buttons.add(okBtn);
            buttons.add(cancelBtn);

            content.add(inputPanel, BorderLayout.CENTER);
            content.add(buttons, BorderLayout.SOUTH);
            dialog.add(content);
            dialog.setVisible(true);
        }

        void refresh() {
            // Update projects/games
            List<String> projects = projectsSupplier.get();
            projectCombo.removeAllItems();
            for (String proj : projects) {
                projectCombo.addItem(proj);
            }

            refreshScenesList();
        }

        void updateShowAll(boolean newShowAll) {
            showAll = newShowAll;
            for (SceneItem item : items) {
                item.repaint();
            }
        }

        void setLinkedScene(File sceneDir) {
            linkedScene = sceneDir;
            for (SceneItem item : items) {
                item.repaint();
            }
        }

        private void refreshScenesList() {
            scenesContainer.removeAll();
            items.clear();

            String selected = (String) projectCombo.getSelectedItem();
            if (selected == null) {
                scenesContainer.add(new JLabel("No projects/games available"));
                revalidate();
                repaint();
                return;
            }

            List<File> scenes = scenesSupplier.apply(selected);
            if (scenes.isEmpty()) {
                JLabel emptyLabel = new JLabel("No scenes");
                emptyLabel.setForeground(AppColors.TEXT_MUTED);
                scenesContainer.add(emptyLabel);
            } else {
                for (File scene : scenes) {
                    SceneItem item = new SceneItem(scene, selected, callbacks, showAll, linkedScene);
                    items.add(item);
                    scenesContainer.add(item);
                    scenesContainer.add(Box.createVerticalStrut(2));
                }
            }

            scenesContainer.add(Box.createVerticalGlue());
            revalidate();
            repaint();
        }
    }

    // =====================================================
    // Inner Class: SceneItem
    // =====================================================

    /**
     * Ein einzelnes Szenen-Item/Tile.
     */
    private static class SceneItem extends JPanel {

        private File sceneFile;
        private String projectName;
        private Callbacks callbacks;
        private boolean showAll;
        private File linkedScene;

        SceneItem(File scene, String project, Callbacks cb, boolean showAll, File linkedScene) {
            this.sceneFile = scene;
            this.projectName = project;
            this.callbacks = cb;
            this.showAll = showAll;
            this.linkedScene = linkedScene;

            initUI();
        }

        private void initUI() {
            setLayout(new BorderLayout(4, 4));
            setBackground(AppColors.BTN_BG);
            setForeground(AppColors.TEXT);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(200, 40));

            // Scene Name
            String displayName = sceneFile.getName();
            int dot = displayName.lastIndexOf('.');
            if (dot > 0) displayName = displayName.substring(0, dot);

            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setForeground(AppColors.TEXT);
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

            // Format Badge
            SceneLocator.SceneFormat format = SceneLocator.getSceneFormat(sceneFile);
            JLabel formatLabel = new JLabel(format != null ? format.extension.toUpperCase() : "?");
            formatLabel.setForeground(AppColors.TEXT_MUTED);
            formatLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));

            add(nameLabel, BorderLayout.CENTER);
            add(formatLabel, BorderLayout.EAST);

            // Hover Effect
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    setBackground(AppColors.BTN_HOVER);
                    repaint();
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    setBackground(AppColors.BTN_BG);
                    repaint();
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        // Doppelklick: Szene laden
                        callbacks.loadScene(sceneFile);
                    } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        // Rechtsklick: Kontext-Menü
                        showContextMenu(e.getX(), e.getY());
                    }
                }
            });

            // ── DragToCopy: right-drag initiates file copy via FILE_COPY_FLAVOR ────
            BaseSidebarPanel.installDragSource(this,
                () -> new java.awt.datatransfer.Transferable() {
                    @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                        return new java.awt.datatransfer.DataFlavor[]{ BaseSidebarPanel.FILE_COPY_FLAVOR };
                    }
                    @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) {
                        return BaseSidebarPanel.FILE_COPY_FLAVOR.equals(f);
                    }
                    @Override public Object getTransferData(java.awt.datatransfer.DataFlavor f)
                            throws java.awt.datatransfer.UnsupportedFlavorException {
                        if (!BaseSidebarPanel.FILE_COPY_FLAVOR.equals(f))
                            throw new java.awt.datatransfer.UnsupportedFlavorException(f);
                        return new BaseSidebarPanel.FileForCopy(sceneFile);
                    }
                },
                null  // no drag-started callback
            );
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Draw red border for unlinked scenes when showAll is true
            if (showAll && linkedScene != null && !sceneFile.getParentFile().equals(linkedScene)) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(AppColors.DANGER);
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        }

        private void showContextMenu(int x, int y) {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(AppColors.BTN_BG);
            menu.setForeground(AppColors.TEXT);

            // Laden
            JMenuItem loadItem = new JMenuItem("Load");
            loadItem.setBackground(AppColors.BTN_BG);
            loadItem.setForeground(AppColors.TEXT);
            loadItem.addActionListener(e -> callbacks.loadScene(sceneFile));
            menu.add(loadItem);

            // Kopieren
            JMenuItem copyItem = new JMenuItem("Copy to...");
            copyItem.setBackground(AppColors.BTN_BG);
            copyItem.setForeground(AppColors.TEXT);
            copyItem.addActionListener(e -> {
                // TODO: Copy Dialog
                JOptionPane.showMessageDialog(this, "Copy Scene Feature - Coming Soon");
            });
            menu.add(copyItem);

            menu.addSeparator();

            // Löschen
            JMenuItem deleteItem = new JMenuItem("Delete");
            deleteItem.setBackground(AppColors.BTN_BG);
            deleteItem.setForeground(AppColors.TEXT);
            deleteItem.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete scene: " + sceneFile.getName() + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    if (SceneLocator.deleteScene(sceneFile)) {
                        callbacks.refreshScenes();
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to delete scene");
                    }
                }
            });
            menu.add(deleteItem);

            menu.show(this, x, y);
        }
    }
}
