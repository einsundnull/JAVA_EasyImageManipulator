package paint;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Startup-Dialog: "Zuletzt verwendet" - zeigt Projekte pro Kategorie in Tabs.
 */
public class StartupDialog extends JDialog {

    private File selectedPath = null;
    private final Map<String, List<String>> recentByCategory;

    public StartupDialog(JFrame owner, Map<String, List<String>> recentByCategory) {
        super(owner, "Zuletzt verwendet", true);
        this.recentByCategory = recentByCategory;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(500, 400);
        setLocationRelativeTo(getOwner());

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Title
        JLabel titleLabel = new JLabel("Zuletzt geöffnete Projekte");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabPane = new JTabbedPane();

        String[] categories = {
            LastProjectsManager.CAT_TEACHING,
            LastProjectsManager.CAT_BOOKS,
            LastProjectsManager.CAT_GAMES,
            LastProjectsManager.CAT_IMAGES
        };

        for (String cat : categories) {
            List<String> paths = recentByCategory.getOrDefault(cat, new ArrayList<>());
            JPanel tabPanel = createCategoryTab(paths);
            tabPane.addTab(capitalize(cat), tabPanel);
        }

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(tabPane, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton newProjectBtn = new JButton("Neues Projekt");
        JButton skipBtn = new JButton("Überspringen");

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
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createCategoryTab(List<String> paths) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (paths.isEmpty()) {
            JLabel emptyLabel = new JLabel("Keine Einträge");
            emptyLabel.setForeground(Color.GRAY);
            panel.add(emptyLabel, BorderLayout.CENTER);
            return panel;
        }

        JList<String> list = new JList<>(paths.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = list.getSelectedValue();
                if (selected != null) {
                    selectedPath = new File(selected);
                    dispose();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public File getSelectedPath() {
        return selectedPath;
    }
}
