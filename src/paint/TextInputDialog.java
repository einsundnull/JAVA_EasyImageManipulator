package paint;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Einzeilige Texteingabe im Stil der Anwendung — Gegenstück zu
 * {@link ConfirmDialog}, zweiter Baustein aus §20.
 *
 * <p><b>Rolle:</b> eine Frage, ein Textfeld, zwei Antworten. Ersetzt
 * {@code JOptionPane.showInputDialog} für <b>neuen</b> Code. Die drei
 * bestehenden Aufrufe (Seite, Buch, Szene umbenennen) sind dokumentierte
 * Altlast und bleiben unangetastet — sie umzubauen wäre ein unnötiger Schritt
 * (Univ. §0).
 *
 * <p><b>Warum überhaupt:</b> Ohne diese Klasse gäbe es für „Umbenennen“ im
 * neuen Kontextmenü keine Alternative zu {@code JOptionPane} — und genau davor
 * warnt §20: „{@code ConfirmDialog} und {@code MessageDialog} gehören in
 * denselben Schritt, sonst bleibt {@code JOptionPane} mangels Alternative
 * stehen.“
 */
final class TextInputDialog {

    private TextInputDialog() {}

    /**
     * Fragt nach einem Text.
     *
     * @param owner   Hauptfenster
     * @param title   Titelzeile
     * @param label   Beschriftung über dem Feld
     * @param preset  Vorbelegung; wird beim Öffnen markiert
     * @return der eingegebene Text (getrimmt), oder {@code null} bei Abbruch
     *         sowie bei leerer Eingabe
     */
    static String ask(JFrame owner, String title, String label, String preset) {

        JDialog d = UIComponentFactory.createBaseDialog(owner, title, 400, 200);

        JLabel caption = new JLabel(label);
        caption.setForeground(AppColors.TEXT);
        caption.setFont(AppTheme.FONT_BASE);
        caption.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JTextField field = new JTextField(preset == null ? "" : preset);
        field.setBackground(AppColors.BG_INPUT);
        field.setForeground(AppColors.TEXT);
        field.setCaretColor(AppColors.TEXT);
        field.setFont(AppTheme.FONT_MD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(AppTheme.PAD_LG, AppTheme.PAD_LG,
                        AppTheme.PAD_LG, AppTheme.PAD_LG)));
        field.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(AppColors.BG_PANEL);
        center.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_XL, AppTheme.PAD_XL,
                AppTheme.PAD_XL, AppTheme.PAD_XL));
        center.add(caption);
        center.add(javax.swing.Box.createVerticalStrut(AppTheme.GAP_MD));
        center.add(field);
        d.add(center, BorderLayout.CENTER);

        String[] result = { null };

        JButton ok     = UIComponentFactory.buildButton("OK", AppColors.ACCENT_ACTIVE, AppColors.ACCENT_HOVER);
        JButton cancel = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.addActionListener(e -> {
            String v = field.getText().trim();
            result[0] = v.isEmpty() ? null : v;
            d.dispose();
        });
        cancel.addActionListener(e -> { result[0] = null; d.dispose(); });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, AppTheme.GAP_MD, AppTheme.GAP_MD));
        row.setBackground(AppColors.BG_PANEL);
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));
        row.add(ok);
        row.add(cancel);
        d.add(row, BorderLayout.SOUTH);

        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        d.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                result[0] = null;
                d.dispose();
            }
        });
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Beim Umbenennen ist der ganze alte Name markiert — Tippen ersetzt
        // ihn, die Pfeiltaste behält ihn. Ohne selectAll müsste der Benutzer
        // erst löschen, was er meistens will.
        SwingUtilities.invokeLater(() -> { field.requestFocusInWindow(); field.selectAll(); });

        d.setVisible(true);   // modal
        return result[0];
    }
}
