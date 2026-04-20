package paint;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Dialog for browsing, switching, and creating card folders.
 * Switching a folder replaces the current card list (auto-saves first).
 */
class CardFolderDialog extends JDialog {

    private final Runnable onFolderChanged;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String>            list  = new JList<>(model);

    CardFolderDialog(Window owner, Runnable onFolderChanged) {
        super(owner, "Kartenordner", Dialog.ModalityType.APPLICATION_MODAL);
        this.onFolderChanged = onFolderChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        build();
        pack();
        setMinimumSize(new Dimension(260, 300));
        setLocationRelativeTo(owner);
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(AppColors.BG_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title
        JLabel title = new JLabel("Aktuell: " + CardListStore.get().currentFolder());
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        title.setForeground(AppColors.ACCENT);
        root.add(title, BorderLayout.NORTH);

        // Folder list
        list.setBackground(AppColors.BG_DARK);
        list.setForeground(AppColors.TEXT);
        list.setFont(new Font("SansSerif", Font.PLAIN, 13));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) switchToSelected(title);
            }
        });
        refreshList();

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        TileGalleryPanel.applyDarkScrollBar(sp.getVerticalScrollBar());
        root.add(sp, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);

        JButton loadBtn = btn("Laden");
        loadBtn.setToolTipText("Ausgewählten Ordner laden (Doppelklick)");
        loadBtn.addActionListener(e -> switchToSelected(title));

        JButton addBtn = btn("+");
        addBtn.setToolTipText("Neuen Ordner erstellen");
        addBtn.addActionListener(e -> createFolder(title));

        JButton closeBtn = btn("Schließen");
        closeBtn.addActionListener(e -> dispose());

        btns.add(loadBtn);
        btns.add(addBtn);
        btns.add(closeBtn);
        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void switchToSelected(JLabel title) {
        String sel = list.getSelectedValue();
        if (sel == null || sel.equals(CardListStore.get().currentFolder())) return;
        CardListStore.get().switchFolder(sel);
        title.setText("Aktuell: " + sel);
        if (onFolderChanged != null) onFolderChanged.run();
    }

    private void createFolder(JLabel title) {
        String name = JOptionPane.showInputDialog(this,
                "Ordnername:", "Neuer Ordner", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        name = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        CardListStore.get().createFolder(name);
        CardListStore.get().switchFolder(name);
        title.setText("Aktuell: " + name);
        refreshList();
        if (onFolderChanged != null) onFolderChanged.run();
    }

    private void refreshList() {
        model.clear();
        List<String> folders = CardListStore.get().listFolders();
        for (String f : folders) model.addElement(f);
        String cur = CardListStore.get().currentFolder();
        list.setSelectedValue(cur, true);
    }

    private JButton btn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setBackground(AppColors.BTN_BG);
        b.setForeground(AppColors.TEXT);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(3, 8, 3, 8));
        return b;
    }
}
