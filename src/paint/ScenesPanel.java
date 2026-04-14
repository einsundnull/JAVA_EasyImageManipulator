package paint;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;

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

    public interface Callbacks {
        /** Lädt eine Szene in den aktuellen Canvas. */
        void loadScene(File sceneFile);
        /** Kopiert eine Szene zu einem anderen Projekt/Game. */
        void copyScene(File source, String targetProject);
        /** Löscht eine Szene. */
        void deleteScene(File sceneFile);
        /** Wird aufgerufen wenn Szenen aktualisiert werden sollen. */
        void refreshScenes();
    }

    public ScenesPanel(Callbacks cb, Runnable onClose) {
        this.callbacks = cb;
        initUI(onClose);
    }

    private void initUI(Runnable onClose) {
        setLayout(new BorderLayout());
        setBackground(AppColors.BG_PANEL);
        setOpaque(true);

        // Header with close button
        JPanel header = buildSidebarHeader("Szenen", onClose);
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
            callbacks
        );
        tabbedPane.addTab("Tool Scenes", toolScenesPanel);

        // Tab 2: Game Scenes
        gameScenesPanel = new SceneListPanel(
            "Game Scenes",
            () -> SceneLocator.getAvailableGames(),
            (gameName) -> SceneLocator.getGameScenes(gameName),
            callbacks
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

        private JComboBox<String> projectCombo;
        private JPanel scenesContainer;
        private JScrollPane scrollPane;

        SceneListPanel(String source,
                      java.util.function.Supplier<List<String>> projects,
                      java.util.function.Function<String, List<File>> scenes,
                      Callbacks cb) {
            this.source = source;
            this.projectsSupplier = projects;
            this.scenesSupplier = scenes;
            this.callbacks = cb;

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

            refresh();
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

        private void refreshScenesList() {
            scenesContainer.removeAll();

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
                    SceneItem item = new SceneItem(scene, selected, callbacks);
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

        SceneItem(File scene, String project, Callbacks cb) {
            this.sceneFile = scene;
            this.projectName = project;
            this.callbacks = cb;

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
