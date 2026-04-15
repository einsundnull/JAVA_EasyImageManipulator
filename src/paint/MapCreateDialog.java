package paint;

import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Dialog to create or edit a translation map.
 * Allows user to specify language, section, and confirm content.
 */
public class MapCreateDialog extends JDialog {

    private String language = "";
    private String section = "";
    private String textI = "";
    private String textII = "";
    private boolean accepted = false;

    public MapCreateDialog(JFrame owner, String initialTextI) {
        super(owner, "Translation Map erstellen", true);
        this.textI = initialTextI;
        this.textII = "";
        buildUI();
        setSize(480, 480);
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel contentPanel = UIComponentFactory.centeredColumnPanel(16, 24, 12);

        // Language
        JPanel langPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        langPanel.setBackground(AppColors.BG_PANEL);
        JLabel langLbl = new JLabel("Sprache (z.B. de, en, fr):");
        langLbl.setForeground(AppColors.TEXT);
        JTextField langField = new JTextField("de", 8);
        langField.setBackground(AppColors.BTN_BG);
        langField.setForeground(AppColors.TEXT);
        langField.setCaretColor(AppColors.TEXT);
        langPanel.add(langLbl);
        langPanel.add(langField);

        // Section
        JPanel secPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        secPanel.setBackground(AppColors.BG_PANEL);
        JLabel secLbl = new JLabel("Bereich (z.B. intro, chapter1):");
        secLbl.setForeground(AppColors.TEXT);
        JTextField secField = new JTextField("intro", 12);
        secField.setBackground(AppColors.BTN_BG);
        secField.setForeground(AppColors.TEXT);
        secField.setCaretColor(AppColors.TEXT);
        secPanel.add(secLbl);
        secPanel.add(secField);

        // Text I
        JLabel textILbl = new JLabel("Text I:");
        textILbl.setForeground(AppColors.TEXT);
        JTextArea textIArea = new JTextArea(3, 35);
        textIArea.setText(textI);
        textIArea.setBackground(AppColors.BTN_BG);
        textIArea.setForeground(AppColors.TEXT);
        textIArea.setCaretColor(AppColors.TEXT);
        textIArea.setLineWrap(true);
        textIArea.setWrapStyleWord(true);
        textIArea.setEditable(true);
        JScrollPane scrollPaneI = new JScrollPane(textIArea);
        scrollPaneI.setBackground(AppColors.BG_PANEL);
        scrollPaneI.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));

        // Text II
        JLabel textIILbl = new JLabel("Text II:");
        textIILbl.setForeground(AppColors.TEXT);
        JTextArea textIIArea = new JTextArea(3, 35);
        textIIArea.setText(textII);
        textIIArea.setBackground(AppColors.BTN_BG);
        textIIArea.setForeground(AppColors.TEXT);
        textIIArea.setCaretColor(AppColors.TEXT);
        textIIArea.setLineWrap(true);
        textIIArea.setWrapStyleWord(true);
        textIIArea.setEditable(true);
        JScrollPane scrollPaneII = new JScrollPane(textIIArea);
        scrollPaneII.setBackground(AppColors.BG_PANEL);
        scrollPaneII.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btnPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("Speichern", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        okBtn.setForeground(Color.WHITE);
        cancelBtn.setForeground(AppColors.TEXT);

        okBtn.addActionListener(e -> {
            language = langField.getText().trim();
            section = secField.getText().trim();
            textI = textIArea.getText();
            textII = textIIArea.getText();
            if (language.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Sprache muss angegeben werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            } else if (section.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bereich muss angegeben werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            } else {
                accepted = true;
                dispose();
            }
        });

        cancelBtn.addActionListener(e -> dispose());

        contentPanel.add(langPanel);
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(secPanel);
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(textILbl);
        contentPanel.add(scrollPaneI);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(textIILbl);
        contentPanel.add(scrollPaneII);
        contentPanel.add(Box.createVerticalStrut(12));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        contentPanel.add(btnPanel);

        add(contentPanel);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getLanguage() {
        return language;
    }

    public String getSection() {
        return section;
    }

    public String getTextI() {
        return textI;
    }

    public String getTextII() {
        return textII;
    }
}
