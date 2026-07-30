package paint;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;

/**
 * Übersicht aller Tasten, Maus-Gesten und Abläufe — geöffnet mit Umschalt+F1
 * oder über den Knopf „?“ in der oberen Leiste.
 *
 * <p><b>Dieser Dialog enthält keinen eigenen Text.</b> Alles kommt aus
 * {@link KeyBindings#ALL} und {@link KeyBindings#GUIDE} (§25). Wer eine
 * Funktion ergänzt und den Registry-Eintrag vergisst, dessen Funktion taucht
 * hier nicht auf — und ist damit in der Oberfläche nicht auffindbar.
 *
 * <p>Layout nach {@code doc/Schema_KeyBindings_Dialog.txt} (freigegeben
 * 2026-07-30): <b>eine durchgehende Liste</b> mit Zwischenüberschriften, keine
 * Reiter, kein Suchfeld. Beschreibungen werden <b>umgebrochen, nie
 * abgeschnitten</b>.
 *
 * <p>Erzeugt über {@link UIComponentFactory#createBaseDialog} — solange es
 * keine {@code BaseDialog}-Klasse gibt, ist das der einzige erlaubte Weg für
 * ein neues Fenster (§20).
 */
final class KeyBindingsDialog {

    private KeyBindingsDialog() {}

    /** Breite der linken Spalte (Kürzel). Rest der Zeile gehört der Beschreibung. */
    private static final int COMBO_W = 190;
    private static final int DIALOG_W = 720;
    private static final int DIALOG_H = 560;

    static void show(JFrame owner) {
        JDialog d = UIComponentFactory.createBaseDialog(owner, "Tastatur, Maus und Abläufe",
                                                        DIALOG_W, DIALOG_H);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(AppColors.BG_PANEL);
        list.setBorder(AppTheme.pad(AppTheme.PAD_XL, AppTheme.PAD_XL + AppTheme.PAD_MD));

        // ── Tasten und Maus, nach Scope gruppiert ────────────────────────────
        for (KeyBindings.Scope scope : KeyBindings.Scope.values()) {
            List<KeyBindings.KeyBinding> inScope = KeyBindings.ALL.stream()
                    .filter(b -> b.scope() == scope).toList();
            if (inScope.isEmpty()) continue;

            list.add(sectionHeader(scope.title));
            for (KeyBindings.KeyBinding b : inScope) {
                list.add(row(b.combo(), b.description(), b.condition()));
            }
            list.add(Box.createVerticalStrut(AppTheme.GAP_LG));
        }

        // ── Anleitung ────────────────────────────────────────────────────────
        list.add(sectionHeader("Anleitung — kurze Abläufe"));
        for (KeyBindings.GuideEntry g : KeyBindings.GUIDE) {
            list.add(guideTitle(g.title()));
            int n = 1;
            for (String step : g.steps()) {
                list.add(row(n++ + ".", step, ""));
            }
            list.add(Box.createVerticalStrut(AppTheme.GAP_MD));
        }

        JScrollPane sp = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(AppColors.BG_PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        TileGalleryPanel.applyDarkScrollBar(sp.getVerticalScrollBar());
        d.add(sp, BorderLayout.CENTER);

        // ── Fußzeile ─────────────────────────────────────────────────────────
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppTheme.GAP_MD, AppTheme.PAD_LG));
        foot.setBackground(AppColors.BG_PANEL);
        var close = UIComponentFactory.buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        close.setPreferredSize(new Dimension(110, 30));
        close.addActionListener(e -> d.dispose());
        foot.add(close);
        d.add(foot, BorderLayout.SOUTH);

        // ESC schließt (§25: Dialog-lokal)
        JComponent root = (JComponent) d.getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeHelp");
        root.getActionMap().put("closeHelp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { d.dispose(); }
        });

        d.setVisible(true);
    }

    // ── Bausteine ────────────────────────────────────────────────────────────

    private static JComponent sectionHeader(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppColors.BG_PANEL);
        p.setBorder(AppTheme.pad(AppTheme.GAP_MD, 0, AppTheme.PAD_LG, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel(text);
        l.setForeground(AppColors.ACCENT);
        l.setFont(AppTheme.FONT_MD_BOLD);
        p.add(l, BorderLayout.NORTH);

        JPanel rule = new JPanel();
        rule.setBackground(AppColors.BORDER);
        rule.setPreferredSize(new Dimension(1, 1));
        p.add(rule, BorderLayout.SOUTH);

        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JComponent guideTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppColors.TEXT);
        l.setFont(AppTheme.FONT_BASE_BOLD);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(AppTheme.pad(AppTheme.PAD_LG, 0, AppTheme.PAD_MD, 0));
        return l;
    }

    /**
     * Eine Zeile: links das Kürzel, rechts die Beschreibung.
     *
     * <p>Die Beschreibung ist eine {@link JTextArea} mit Zeilenumbruch — kein
     * {@code JLabel}. Ein Label würde zu langen Text abschneiden, und §25
     * verlangt ausdrücklich: <b>umbrechen, nie abschneiden</b>.
     */
    private static JComponent row(String combo, String description, String condition) {
        JPanel p = new JPanel(new BorderLayout(AppTheme.GAP_MD, 0));
        p.setBackground(AppColors.BG_PANEL);
        p.setBorder(AppTheme.pad(AppTheme.PAD_XS, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel c = new JLabel(combo);
        c.setForeground(AppColors.TEXT);
        c.setFont(AppTheme.FONT_BASE_BOLD);
        c.setPreferredSize(new Dimension(COMBO_W, 18));
        c.setVerticalAlignment(JLabel.TOP);
        p.add(c, BorderLayout.WEST);

        String text = condition.isEmpty() ? description
                                          : description + "   (" + condition + ")";
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(null);
        t.setFont(AppTheme.FONT_BASE);
        t.setForeground(condition.isEmpty() ? AppColors.TEXT : AppColors.TEXT_MUTED);
        p.add(t, BorderLayout.CENTER);

        // Hoehe der Zeile ergibt sich aus dem umgebrochenen Text.
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }
}
