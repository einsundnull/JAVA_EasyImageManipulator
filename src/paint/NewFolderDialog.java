package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Shared dialog for creating new folders (Image Folder, Book, Game, Scene).
 * The caller specifies which type is pre-selected; the user may change it.
 */
class NewFolderDialog {

    enum FolderType {
        IMAGE_FOLDER("Image Folder"),
        BOOK        ("Buch"),
        GAME        ("Game"),
        SCENE       ("Szene");

        final String label;
        FolderType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /**
     * Shows the dialog and, on confirmation, creates the folder.
     * Calls {@code onCreated} on the EDT after successful creation.
     *
     * @param parent      Owner component
     * @param preselect   Which type to pre-select in the combo
     * @param imageDir    Current image directory (used for IMAGE_FOLDER type), may be null
     * @param onCreated   Called after the folder is successfully created
     */
    static void show(java.awt.Component parent, FolderType preselect,
                     File imageDir, Runnable onCreated) {

        // ── Build UI ────────────────────────────────────────────────────────────
        JComboBox<FolderType> typeCombo = new JComboBox<>(FolderType.values());
        typeCombo.setSelectedItem(preselect);

        JTextField nameField = new JTextField(22);
        nameField.setPreferredSize(new Dimension(180, 24));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(300, 70));
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 4, 4, 8);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        fc.insets = new Insets(4, 0, 4, 4);

        lc.gridx = 0; lc.gridy = 0; panel.add(new JLabel("Typ:"),  lc);
        fc.gridx = 1; fc.gridy = 0; panel.add(typeCombo,            fc);
        lc.gridx = 0; lc.gridy = 1; panel.add(new JLabel("Name:"), lc);
        fc.gridx = 1; fc.gridy = 1; panel.add(nameField,            fc);

        // Update dialog title on type change
        typeCombo.addActionListener(e -> {
            // title update happens implicitly — JOptionPane title is fixed,
            // but we can update the label if desired
        });

        // Focus the name field immediately
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);

        FolderType selected = preselect;
        String dialogTitle = "Neues " + selected.label + " erstellen";

        int result = JOptionPane.showConfirmDialog(
                parent, panel, dialogTitle,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        FolderType type = (FolderType) typeCombo.getSelectedItem();
        File created = createFolder(type, name, imageDir, parent);
        if (created != null && onCreated != null) {
            SwingUtilities.invokeLater(onCreated);
        }
    }

    // ── Folder creation ───────────────────────────────────────────────────────

    private static File createFolder(FolderType type, String name,
                                     File imageDir, java.awt.Component parent) {
        File dir;
        switch (type) {
            case IMAGE_FOLDER -> {
                File base = imageDir;
                if (base == null || !base.isDirectory()) {
                    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                    chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Übergeordnetes Verzeichnis wählen");
                    if (chooser.showOpenDialog(parent) != javax.swing.JFileChooser.APPROVE_OPTION)
                        return null;
                    base = chooser.getSelectedFile();
                }
                dir = new File(base, name);
            }
            case BOOK -> dir = new File(BookController.getBooksRoot(), name);
            case GAME -> dir = new File(SceneLocator.getGamesDir(), name);
            case SCENE -> {
                List<String> projects = SceneLocator.getToolProjects();
                String project = projects.isEmpty() ? "Default" : projects.get(0);
                dir = new File(SceneLocator.getToolScenesDir(project), name);
            }
            default -> { return null; }
        }

        if (dir.exists()) {
            JOptionPane.showMessageDialog(parent,
                    "Verzeichnis existiert bereits:\n" + dir.getAbsolutePath(),
                    "Bereits vorhanden", JOptionPane.INFORMATION_MESSAGE);
            return dir;
        }
        if (!dir.mkdirs()) {
            JOptionPane.showMessageDialog(parent,
                    "Verzeichnis konnte nicht erstellt werden:\n" + dir.getAbsolutePath(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return dir;
    }
}
