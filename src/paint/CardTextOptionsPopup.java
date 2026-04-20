package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Modeless dialog for configuring card font display and per-panel TTS language.
 * All changes are applied immediately and saved to AppSettings.
 */
class CardTextOptionsPopup extends JDialog {

    private static final String[] TTS_LANGS = CardTtsPlayer.availableLanguages();

    private final Runnable onChanged;
    private JButton colorSwatch;

    CardTextOptionsPopup(Window owner, Runnable onChanged) {
        super(owner, "Textoptionen – Karten", Dialog.ModalityType.MODELESS);
        this.onChanged = onChanged;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        build();
        pack();
        setLocationRelativeTo(owner);
    }

    private void build() {
        AppSettings s = AppSettings.getInstance();

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(AppColors.BG_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 10, 8));
        grid.setOpaque(false);

        // ── Font family ───────────────────────────────────────────────────────
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        JComboBox<String> familyBox = combo(fonts);
        familyBox.setSelectedItem(s.getCardFontFamily());
        familyBox.addActionListener(e -> {
            s.setCardFontFamily((String) familyBox.getSelectedItem());
            saveAndNotify();
        });
        grid.add(lbl("Schriftart")); grid.add(familyBox);

        // ── Font size ─────────────────────────────────────────────────────────
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(
                s.getCardFontSize(), 6, 72, 1));
        styleSpinner(sizeSpinner);
        sizeSpinner.addChangeListener(e -> {
            s.setCardFontSize((Integer) sizeSpinner.getValue());
            saveAndNotify();
        });
        grid.add(lbl("Schriftgröße")); grid.add(sizeSpinner);

        // ── Font color ────────────────────────────────────────────────────────
        colorSwatch = new JButton();
        colorSwatch.setPreferredSize(new java.awt.Dimension(70, 24));
        refreshSwatch(new Color(s.getCardFontColor()));
        colorSwatch.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Schriftfarbe",
                    new Color(AppSettings.getInstance().getCardFontColor()));
            if (chosen != null) {
                AppSettings.getInstance().setCardFontColor(chosen.getRGB());
                refreshSwatch(chosen);
                saveAndNotify();
            }
        });
        grid.add(lbl("Schriftfarbe")); grid.add(colorSwatch);

        // ── Separator ─────────────────────────────────────────────────────────
        grid.add(sep()); grid.add(sep());

        // ── TTS language left (Text I) ─────────────────────────────────────────
        JComboBox<String> langLeft = combo(TTS_LANGS);
        langLeft.setSelectedItem(s.getCardTtsLanguageLeft());
        langLeft.addActionListener(e -> {
            s.setCardTtsLanguageLeft((String) langLeft.getSelectedItem());
            saveAndNotify();
        });
        grid.add(lbl("TTS-Sprache (Text I)")); grid.add(langLeft);

        // ── TTS language right (Text II) ──────────────────────────────────────
        JComboBox<String> langRight = combo(TTS_LANGS);
        langRight.setSelectedItem(s.getCardTtsLanguageRight());
        langRight.addActionListener(e -> {
            s.setCardTtsLanguageRight((String) langRight.getSelectedItem());
            saveAndNotify();
        });
        grid.add(lbl("TTS-Sprache (Text II)")); grid.add(langRight);

        root.add(grid, BorderLayout.CENTER);

        // ── Close ─────────────────────────────────────────────────────────────
        JButton close = new JButton("Schließen");
        close.setFont(new Font("SansSerif", Font.PLAIN, 12));
        close.setBackground(AppColors.BTN_BG);
        close.setForeground(AppColors.TEXT);
        close.setFocusPainted(false);
        close.addActionListener(e -> setVisible(false));
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        foot.setOpaque(false);
        foot.add(close);
        root.add(foot, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void saveAndNotify() {
        try { AppSettings.getInstance().save(); } catch (Exception ex) { /* ignore */ }
        if (onChanged != null) onChanged.run();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshSwatch(Color c) {
        colorSwatch.setBackground(c);
        colorSwatch.setForeground(c.getRed() + c.getGreen() + c.getBlue() > 382
                ? Color.BLACK : Color.WHITE);
        colorSwatch.setText(c.getRed() + ", " + c.getGreen() + ", " + c.getBlue());
        colorSwatch.setOpaque(true);
    }

    private JComboBox<String> combo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(AppColors.BTN_BG);
        cb.setForeground(AppColors.TEXT);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return cb;
    }

    private void styleSpinner(JSpinner sp) {
        sp.setBackground(AppColors.BTN_BG);
        sp.getEditor().getComponent(0).setBackground(AppColors.BTN_BG);
        ((javax.swing.JSpinner.DefaultEditor) sp.getEditor())
                .getTextField().setForeground(AppColors.TEXT);
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(AppColors.TEXT);
        return l;
    }

    private JPanel sep() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new java.awt.Dimension(0, 6));
        return p;
    }
}
