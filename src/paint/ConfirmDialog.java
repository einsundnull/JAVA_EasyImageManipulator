package paint;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Ja/Nein-Rückfrage im Stil der Anwendung — der erste Baustein aus §20.
 *
 * <p><b>Rolle:</b> genau eine Frage, genau zwei Antworten. Kein Ersatz für
 * {@code EditorDialogs.showUnsavedChangesDialog()} (das hat drei Antworten und
 * bleibt, wo es ist) und keine Vorwegnahme der vollen
 * {@code BaseDialog}-Extraktion — die bleibt der offene Schritt [9] mit
 * Redundanz-Audit und Mockup davor (§2/§20).
 *
 * <p><b>Warum es diese Klasse gibt:</b> §20 verbietet <i>neue</i>
 * {@code JOptionPane}-Aufrufe, und die Kontextmenüs brauchen eine Rückfrage vor
 * dem Löschen — denn ein gelöschter Layer ist <b>nicht</b> rückgängig zu machen
 * (Befund D01: das Undo-Band speichert nur {@code workingImage}). Der Baustein
 * entsteht damit an einem echten Bedarf statt auf Vorrat.
 *
 * <p><b>Verhalten, das eine Factory nicht teilen könnte</b> (genau der Punkt aus
 * §20, „eine Factory ist keine Basisklasse“): {@code Esc} und das Fenster-X
 * antworten <i>Nein</i>, {@code Enter} antwortet mit der <i>vorgewählten</i>
 * Schaltfläche, und vorgewählt ist bei einer zerstörenden Frage das
 * Abbrechen — nicht das Löschen.
 */
final class ConfirmDialog {

    private ConfirmDialog() {}

    /**
     * Stellt eine Ja/Nein-Frage.
     *
     * @param owner      Hauptfenster
     * @param title      Titelzeile
     * @param message    Frage; {@code \n} trennt Zeilen
     * @param yesLabel   Beschriftung der bejahenden Schaltfläche
     * @param destructive {@code true} färbt „Ja“ rot und lässt den Fokus auf
     *                    „Abbrechen“ — bei allem, was Daten vernichtet
     * @return {@code true}, wenn der Benutzer bejaht hat
     */
    static boolean ask(JFrame owner, String title, String message,
                       String yesLabel, boolean destructive) {

        JDialog d = UIComponentFactory.createBaseDialog(owner, title, 420, 190);

        JLabel text = UIComponentFactory.htmlLabel(
                message.replace("\n", "<br>"), AppColors.TEXT, 12);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, AppTheme.GAP_MD, AppTheme.GAP_LG));
        center.setBackground(AppColors.BG_PANEL);
        center.setBorder(BorderFactory.createEmptyBorder(AppTheme.PAD_XL, AppTheme.PAD_XL,
                AppTheme.PAD_XL, AppTheme.PAD_XL));
        center.add(text);
        d.add(center, BorderLayout.CENTER);

        boolean[] answer = { false };

        JButton yes = UIComponentFactory.buildButton(yesLabel,
                destructive ? AppColors.DANGER : AppColors.ACCENT_ACTIVE,
                destructive ? AppColors.DANGER_HOVER : AppColors.ACCENT_HOVER);
        JButton no  = UIComponentFactory.buildButton("Abbrechen",
                AppColors.BTN_BG, AppColors.BTN_HOVER);
        yes.addActionListener(e -> { answer[0] = true;  d.dispose(); });
        no .addActionListener(e -> { answer[0] = false; d.dispose(); });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, AppTheme.GAP_MD, AppTheme.GAP_MD));
        row.setBackground(AppColors.BG_PANEL);
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));
        row.add(yes);
        row.add(no);
        d.add(row, BorderLayout.SOUTH);

        // Esc und das Fenster-X bedeuten Nein. Ohne diese beiden Zeilen wäre
        // ein weggeklicktes Fenster von einem „Ja“ nicht zu unterscheiden.
        d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        d.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer[0] = false;
                d.dispose();
            }
        });
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { answer[0] = false; }
        });

        // Enter löst die vorgewählte Schaltfläche aus. Bei einer zerstörenden
        // Frage ist das ABBRECHEN — wer blind Enter drückt, verliert nichts.
        d.getRootPane().setDefaultButton(destructive ? no : yes);

        d.setVisible(true);   // modal
        return answer[0];
    }
}
