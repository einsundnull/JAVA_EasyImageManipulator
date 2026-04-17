package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Startup-Dialog: "Zuletzt verwendet" - zeigt Projekte pro Kategorie in Tabs.
 * Styled mit AppColors (dunkles Theme).
 * Kann auch als Schnellauswahl-Dialog (Mode.QUICK_OPEN) verwendet werden.
 */
public class StartupDialog extends JDialog {

    public enum Mode { STARTUP, QUICK_OPEN }

    private File selectedPath = null;
    private String selectedCategory = LastProjectsManager.CAT_IMAGES;
    /** 0 = primary gallery, 1 = first extra gallery (tileGallery2), etc. */
    private int selectedGallerySlot = 0;
    private final Map<String, List<String>> recentByCategory;
    private final Mode mode;
    private final int canvasIdx;
    private final String initialCategory;

    private static final int NUM_BASE_TABS = 6;
    private int extraTabCount = 0;
    private boolean addingTab = false;

    // Alt-Konstruktor (Startup-Modus, rückwärtskompatibel)
    public StartupDialog(JFrame owner, Map<String, List<String>> recentByCategory) {
        this(owner, recentByCategory, Mode.STARTUP, 0, LastProjectsManager.CAT_IMAGES);
    }

    // Neuer Hauptkonstruktor (für beide Modi)
    public StartupDialog(JFrame owner, Map<String, List<String>> recentByCategory, Mode mode, int canvasIdx) {
        this(owner, recentByCategory, mode, canvasIdx, LastProjectsManager.CAT_IMAGES);
    }

    public StartupDialog(JFrame owner, Map<String, List<String>> recentByCategory, Mode mode, int canvasIdx, String initialCategory) {
        super(owner, mode == Mode.STARTUP ? "Zuletzt verwendet" : "Schnellauswahl", true);
        this.recentByCategory = recentByCategory;
        this.mode = mode;
        this.canvasIdx = canvasIdx;
        this.initialCategory = initialCategory;
        this.selectedCategory = initialCategory;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(600, 450);
        setLocationRelativeTo(getOwner());

        // Main panel with dark background
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(AppColors.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Title (mode-abhängig)
        String titleText = mode == Mode.STARTUP ? "Zuletzt geöffnete Projekte" : "Schnellauswahl – Zuletzt verwendet";
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(AppColors.TEXT);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Tabs with custom styling
        JTabbedPane tabPane = createStyledTabbedPane();

        String[] categories = {
            LastProjectsManager.CAT_SCENES,
            LastProjectsManager.CAT_IMAGES,
            LastProjectsManager.CAT_BOOKS,
            LastProjectsManager.CAT_GAMES,
            LastProjectsManager.CAT_TEACHING,
            LastProjectsManager.CAT_MAPS
        };

        int initialTabIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            String cat = categories[i];
            List<String> paths = new ArrayList<>(recentByCategory.getOrDefault(cat, new ArrayList<>()));

            if (cat.equals(LastProjectsManager.CAT_GAMES)) {
                File gamesDir = SceneLocator.getAppDataGamesDir();
                if (gamesDir.exists()) {
                    File[] gameDirs = gamesDir.listFiles(File::isDirectory);
                    if (gameDirs != null) {
                        for (File gameDir : gameDirs) {
                            String absPath = gameDir.getAbsolutePath();
                            if (!paths.contains(absPath)) paths.add(absPath);
                        }
                    }
                }
            }

            JPanel tabPanel = (cat.equals(LastProjectsManager.CAT_IMAGES))
                    ? createImagesPanel(paths, 0)
                    : createCategoryTab(paths, cat);
            tabPane.addTab(capitalize(cat), tabPanel);
            if (cat.equals(initialCategory)) initialTabIndex = i;
        }

        // "+" pseudo-tab — always the last tab
        tabPane.addTab("+", new JPanel());

        tabPane.setSelectedIndex(initialTabIndex);
        tabPane.addChangeListener(e -> {
            if (addingTab) return;
            int sel = tabPane.getSelectedIndex();
            int plusIdx = tabPane.getTabCount() - 1;
            if (sel == plusIdx) {
                addingTab = true;
                try {
                    addExtraGalleryTab(tabPane);
                } finally {
                    addingTab = false;
                }
                return;
            }
            if (sel >= NUM_BASE_TABS) {
                // Extra gallery tab: slot = 1-based index among extras
                selectedGallerySlot = sel - NUM_BASE_TABS + 1;
                selectedCategory = LastProjectsManager.CAT_IMAGES;
            } else if (sel >= 0 && sel < categories.length) {
                selectedGallerySlot = 0;
                selectedCategory = categories[sel];
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(AppColors.BG_DARK);
        centerPanel.add(tabPane, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Buttons (mode-abhängig)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(AppColors.BG_DARK);

        if (mode == Mode.STARTUP) {
            // Startup-Mode: Neues Projekt + Überspringen
            JButton newProjectBtn = UIComponentFactory.buildButton("Neues Projekt", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
            JButton skipBtn = UIComponentFactory.buildButton("Überspringen", AppColors.BTN_BG, AppColors.BTN_HOVER);

            newProjectBtn.addActionListener(e -> {
                // TODO: Open new project dialog
                dispose();
            });

            skipBtn.addActionListener(e -> {
                selectedPath = null;
                dispose();
            });

            buttonPanel.add(newProjectBtn);
            buttonPanel.add(skipBtn);
        } else {
            // Quick-Open-Mode: Durchsuchen + Schließen
            JButton browseBtn = UIComponentFactory.buildButton("Durchsuchen", AppColors.BTN_BG, AppColors.BTN_HOVER);
            JButton closeBtn = UIComponentFactory.buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);

            browseBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Images", "png", "jpg", "jpeg", "gif", "bmp"));
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(StartupDialog.this) == JFileChooser.APPROVE_OPTION) {
                    selectedPath = chooser.getSelectedFile();
                    dispose();
                }
            });

            closeBtn.addActionListener(e -> {
                selectedPath = null;
                dispose();
            });

            buttonPanel.add(browseBtn);
            buttonPanel.add(closeBtn);
        }
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setUndecorated(false);
    }

    private void addExtraGalleryTab(JTabbedPane tabPane) {
        extraTabCount++;
        int slot = extraTabCount; // slot 1, 2, 3…
        String tabTitle = "Bilder " + (slot + 1);
        List<String> paths = new ArrayList<>(recentByCategory.getOrDefault(
                LastProjectsManager.CAT_IMAGES, new ArrayList<>()));
        JPanel panel = createImagesPanel(paths, slot);
        int insertIdx = tabPane.getTabCount() - 1; // before "+"
        tabPane.insertTab(tabTitle, null, panel, "Liste " + (slot + 1) + " – lädt in zweite Gallery", insertIdx);
        tabPane.setSelectedIndex(insertIdx);
        selectedGallerySlot = slot;
        selectedCategory = LastProjectsManager.CAT_IMAGES;
    }

    private JTabbedPane createStyledTabbedPane() {
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setBackground(AppColors.BG_PANEL);
        tabPane.setForeground(AppColors.TEXT);
        tabPane.setTabPlacement(JTabbedPane.TOP);

        // Style the tab pane
        UIManager.put("TabbedPane.background", AppColors.BG_PANEL);
        UIManager.put("TabbedPane.foreground", AppColors.TEXT);
        UIManager.put("TabbedPane.contentAreaColor", AppColors.BG_DARK);
        UIManager.put("TabbedPane.selected", AppColors.ACCENT);
        UIManager.put("TabbedPane.selectHighlight", AppColors.ACCENT);

        return tabPane;
    }

    private JPanel createCategoryTab(List<String> paths, String category) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppColors.BG_DARK);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (paths.isEmpty()) {
            JLabel emptyLabel = new JLabel("Keine Einträge");
            emptyLabel.setForeground(AppColors.TEXT_MUTED);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(emptyLabel, BorderLayout.CENTER);
            return panel;
        }

        // Custom JList with dark styling
        JList<String> list = new JList<>(formatPaths(paths));
        list.setBackground(AppColors.BG_PANEL);
        list.setForeground(AppColors.TEXT);
        list.setSelectionBackground(AppColors.ACCENT);
        list.setSelectionForeground(Color.BLACK);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ProjectListCellRenderer());

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = list.getSelectedIndex();
                if (idx >= 0) {
                    selectedPath = new File(paths.get(idx));
                    dispose();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBackground(AppColors.BG_PANEL);
        scrollPane.getViewport().setBackground(AppColors.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Special panel for Images with delete buttons per item.
     * @param gallerySlot 0 = primary gallery, 1+ = extra gallery slots
     */
    private JPanel createImagesPanel(List<String> paths, int gallerySlot) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppColors.BG_DARK);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (paths.isEmpty()) {
            JLabel emptyLabel = new JLabel("Keine Bilder");
            emptyLabel.setForeground(AppColors.TEXT_MUTED);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(emptyLabel, BorderLayout.CENTER);
            return panel;
        }

        // List panel with delete buttons - use container that respects preferred sizes
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new java.awt.GridBagLayout());
        listPanel.setBackground(AppColors.BG_PANEL);

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        for (int i = 0; i < paths.size(); i++) {
            final String path = paths.get(i);
            final int idx = i;
            final JPanel[] itemPanelRef = new JPanel[1]; // Use array to allow assignment in lambda
            itemPanelRef[0] = createImageListItem(path, () -> {
                try {
                    LastProjectsManager.removeRecent(LastProjectsManager.CAT_IMAGES, path);
                    paths.remove(idx);
                    listPanel.remove(itemPanelRef[0]);
                    listPanel.revalidate();
                    listPanel.repaint();
                } catch (IOException e) {
                    System.err.println("[ERROR] Konnte Eintrag nicht löschen: " + e.getMessage());
                }
            }, () -> {
                selectedGallerySlot = gallerySlot;
                selectedPath = new File(path);
                dispose();
            });
            listPanel.add(itemPanelRef[0], gbc);
            gbc.gridy++;
        }

        // Add filler to push items to top
        gbc.weighty = 1.0;
        listPanel.add(new JPanel(), gbc);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBackground(AppColors.BG_PANEL);
        scrollPane.getViewport().setBackground(AppColors.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Single image list item with thumbnail and delete button.
     * Standard height of 120px showing the first image in the directory.
     */
    private JPanel createImageListItem(String path, Runnable onDelete, Runnable onSelect) {
        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setBackground(AppColors.BG_PANEL);
        item.setBorder(new EmptyBorder(4, 4, 4, 4));
        item.setPreferredSize(new Dimension(-1, 120)); // Standard height
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120)); // Force height with BoxLayout

        File dirFile = new File(path);

        // Thumbnail panel - load first image from directory
        JPanel thumbnailPanel = new JPanel() {
            private BufferedImage thumbnail = null;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                if (thumbnail != null) {
                    int w = getWidth();
                    int h = getHeight();
                    int imgW = thumbnail.getWidth();
                    int imgH = thumbnail.getHeight();

                    // Scale to fit while maintaining aspect ratio
                    double scale = Math.min((double)w / imgW, (double)h / imgH);
                    int scaledW = (int)(imgW * scale);
                    int scaledH = (int)(imgH * scale);
                    int x = (w - scaledW) / 2;
                    int y = (h - scaledH) / 2;

                    g2.drawImage(thumbnail, x, y, scaledW, scaledH, null);
                } else {
                    // Placeholder while loading
                    g2.setColor(AppColors.BG_DARK);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(AppColors.TEXT_MUTED);
                    g2.drawString("...", getWidth() / 2 - 10, getHeight() / 2 + 5);
                }
            }
        };
        thumbnailPanel.setBackground(AppColors.BG_DARK);
        thumbnailPanel.setPreferredSize(new Dimension(120, 120));

        // Load thumbnail asynchronously
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                File[] files = dirFile.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                });

                if (files != null && files.length > 0) {
                    BufferedImage img = javax.imageio.ImageIO.read(files[0]);
                    if (img != null) {
                        // Scale to ~120x120
                        int targetSize = 120;
                        double scale = Math.min((double)targetSize / img.getWidth(), (double)targetSize / img.getHeight());
                        int newW = (int)(img.getWidth() * scale);
                        int newH = (int)(img.getHeight() * scale);
                        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2 = scaled.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2.drawImage(img, 0, 0, newW, newH, null);
                        g2.dispose();
                        return scaled;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    BufferedImage result = get();
                    if (result != null) {
                        // Use reflection to set thumbnail
                        java.lang.reflect.Field field = thumbnailPanel.getClass().getDeclaredField("thumbnail");
                        field.setAccessible(true);
                        field.set(thumbnailPanel, result);
                        thumbnailPanel.repaint();
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Konnte Thumbnail nicht laden: " + e.getMessage());
                }
            }
        }.execute();

        // Info panel - directory name and path
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(AppColors.BG_PANEL);
        infoPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

        JLabel nameLabel = new JLabel(dirFile.getName());
        nameLabel.setForeground(AppColors.TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
        nameLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        JLabel pathLabel = new JLabel(dirFile.getParent());
        pathLabel.setForeground(AppColors.TEXT_MUTED);
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 10f));
        pathLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        // Click listeners on labels
        java.awt.event.MouseAdapter labelClickAdapter = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onSelect.run();
            }
        };
        nameLabel.addMouseListener(labelClickAdapter);
        pathLabel.addMouseListener(labelClickAdapter);

        infoPanel.add(nameLabel, BorderLayout.NORTH);
        infoPanel.add(pathLabel, BorderLayout.CENTER);

        // Add click listener to infoPanel
        infoPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onSelect.run();
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.TILE_HOVER_BG);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.BG_PANEL);
            }
        });

        // Delete button
        JButton deleteBtn = new JButton("✕");
        deleteBtn.setBackground(AppColors.DANGER);
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.setFont(deleteBtn.getFont().deriveFont(Font.BOLD, 12f));
        deleteBtn.setFocusPainted(false);
        deleteBtn.setBorderPainted(false);
        deleteBtn.setPreferredSize(new Dimension(32, 32));
        deleteBtn.setMargin(new Insets(0, 0, 0, 0));

        deleteBtn.addActionListener(e -> onDelete.run());
        deleteBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                deleteBtn.setBackground(AppColors.DANGER_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                deleteBtn.setBackground(AppColors.DANGER);
            }
        });

        // Hover effect on entire item
        java.awt.event.MouseAdapter hoverAdapter = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.TILE_HOVER_BG);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.BG_PANEL);
            }
        };

        // Thumbnail click handler
        thumbnailPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onSelect.run();
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.TILE_HOVER_BG);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                item.setBackground(AppColors.BG_PANEL);
            }
        });

        // Item hover effect
        item.addMouseListener(hoverAdapter);

        item.add(thumbnailPanel, BorderLayout.WEST);
        item.add(infoPanel, BorderLayout.CENTER);
        item.add(deleteBtn, BorderLayout.EAST);

        return item;
    }

    private String[] formatPaths(List<String> paths) {
        return paths.stream()
                .map(p -> {
                    File f = new File(p);
                    return "  " + f.getName() + "  —  " + f.getParent();
                })
                .toArray(String[]::new);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if ("maps".equals(s)) return "Scenes";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public File getSelectedPath() { return selectedPath; }
    public String getSelectedCategory() { return selectedCategory; }
    /** 0 = primary gallery, 1 = tileGallery2, 2 = tileGallery3 (future), … */
    public int getSelectedGallerySlot() { return selectedGallerySlot; }
    public int getCanvasIdx() { return canvasIdx; }

    public Mode getMode() {
        return mode;
    }

    /**
     * Custom cell renderer for project list items.
     */
    private static class ProjectListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (isSelected) {
                label.setBackground(AppColors.ACCENT);
                label.setForeground(Color.BLACK);
            } else {
                label.setBackground(AppColors.BG_PANEL);
                label.setForeground(AppColors.TEXT);
            }

            label.setOpaque(true);
            label.setBorder(new EmptyBorder(5, 5, 5, 5));
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));

            return label;
        }
    }
}
