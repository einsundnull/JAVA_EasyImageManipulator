package paint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Standalone text-formatting toolbar. Docked at the bottom, visible only
 * while a TextLayer is being edited. Controls font, size, bold, italic,
 * color, and provides commit (✓) / cancel (✗) actions.
 */
public class TextToolbar extends JPanel {

    public static final int TOOLBAR_H = 46;
    private static final int BTN_H    = 30;
    private static final int GAP      = 4;

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public interface Callbacks {
        void onTextPropsChanged(String font, int size, boolean bold, boolean italic, Color color);
        void onCommit();
        void onCancel();
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Callbacks    cb;
    private Color              textColor       = Color.BLACK;
    private boolean            suppressSync    = false;

    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String>  fontCombo;
    private JSpinner           sizeSpinner;
    private JToggleButton      boldBtn;
    private JToggleButton      italicBtn;
    private JLabel             colorSwatch;

    // =========================================================================
    public TextToolbar(java.awt.Window owner, Callbacks callbacks) {
        this.cb = callbacks;
        setLayout(new BorderLayout());
        setBackground(AppColors.BG_TOOLBAR);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));
        setPreferredSize(new Dimension(0, TOOLBAR_H));

        JPanel strip = buildStrip();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(strip, BorderLayout.CENTER);
        add(wrapper, BorderLayout.CENTER);

        setVisible(false);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Shows the toolbar and populates controls with the given text properties. */
    public void showToolbar(String font, int size, boolean bold, boolean italic, Color color) {
        setProps(font, size, bold, italic, color);
        setVisible(true);
        revalidate();
    }

    /** Hides the toolbar. */
    public void hideToolbar() {
        setVisible(false);
        revalidate();
    }

    /** Syncs controls without firing callbacks (called when canvas updates props). */
    public void setProps(String font, int size, boolean bold, boolean italic, Color color) {
        suppressSync = true;
        try {
            if (fontCombo   != null) fontCombo.setSelectedItem(font);
            if (sizeSpinner != null) sizeSpinner.setValue(size);
            if (boldBtn     != null) boldBtn.setSelected(bold);
            if (italicBtn   != null) italicBtn.setSelected(italic);
            textColor = color != null ? color : Color.BLACK;
            if (colorSwatch != null) colorSwatch.setBackground(textColor);
        } finally {
            suppressSync = false;
        }
    }

    // =========================================================================
    // Strip builder
    // =========================================================================
    private JPanel buildStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(AppColors.BG_TOOLBAR);
        strip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        // Label
        JLabel lbl = miniLabel("Text: ");
        strip.add(lbl);
        strip.add(Box.createHorizontalStrut(GAP));

        // Font family
        String[] sysfonts = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontCombo = styledCombo(sysfonts, 150);
        fontCombo.setSelectedItem("SansSerif");
        fontCombo.setToolTipText("Schriftart");
        fontCombo.addActionListener(e -> { if (!suppressSync) fire(); });
        strip.add(fontCombo);
        strip.add(Box.createHorizontalStrut(GAP));

        // Size
        sizeSpinner = new JSpinner(new SpinnerNumberModel(12, 6, 800, 1));
        sizeSpinner.setPreferredSize(new Dimension(60, BTN_H));
        sizeSpinner.setMaximumSize(new Dimension(60, BTN_H + 4));
        styleSpinner(sizeSpinner);
        sizeSpinner.setToolTipText("Schriftgröße");
        sizeSpinner.addChangeListener(e -> { if (!suppressSync) fire(); });
        strip.add(sizeSpinner);
        strip.add(Box.createHorizontalStrut(GAP));

        // Bold
        boldBtn = toggleBtn("B", "Fett");
        boldBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        boldBtn.addActionListener(e -> { if (!suppressSync) fire(); });
        strip.add(boldBtn);
        strip.add(Box.createHorizontalStrut(GAP));

        // Italic
        italicBtn = toggleBtn("I", "Kursiv");
        italicBtn.setFont(new Font("SansSerif", Font.ITALIC, 12));
        italicBtn.addActionListener(e -> { if (!suppressSync) fire(); });
        strip.add(italicBtn);
        strip.add(Box.createHorizontalStrut(GAP));

        // Color swatch
        colorSwatch = new JLabel("  ");
        colorSwatch.setOpaque(true);
        colorSwatch.setBackground(textColor);
        colorSwatch.setPreferredSize(new Dimension(26, 26));
        colorSwatch.setMaximumSize(new Dimension(26, 26));
        colorSwatch.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        colorSwatch.setToolTipText("Textfarbe");
        colorSwatch.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Color c = javax.swing.JColorChooser.showDialog(
                        SwingUtilities.getWindowAncestor(TextToolbar.this), "Textfarbe", textColor);
                if (c != null) {
                    textColor = c;
                    colorSwatch.setBackground(c);
                    fire();
                }
            }
        });
        strip.add(colorSwatch);

        // Spacer
        strip.add(Box.createHorizontalGlue());

        // Separator
        strip.add(vSep());
        strip.add(Box.createHorizontalStrut(GAP));

        // Cancel button (commit is implicit — changes apply immediately)
        JButton cancelBtn = actionBtn("✗ Abbrechen", "Bearbeitung abbrechen (Esc)");
        cancelBtn.addActionListener(e -> cb.onCancel());
        strip.add(cancelBtn);

        return strip;
    }

    private void fire() {
        String  font = fontCombo   != null ? (String) fontCombo.getSelectedItem() : "SansSerif";
        int     size = sizeSpinner != null ? (int)    sizeSpinner.getValue()       : 12;
        boolean bold = boldBtn     != null && boldBtn.isSelected();
        boolean ital = italicBtn   != null && italicBtn.isSelected();
        cb.onTextPropsChanged(font, size, bold, ital, textColor);
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private JComboBox<String> styledCombo(String[] items, int width) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setBackground(new Color(50, 50, 50));
        c.setForeground(AppColors.TEXT);
        c.setFont(new Font("SansSerif", Font.PLAIN, 11));
        c.setPreferredSize(new Dimension(width, BTN_H));
        c.setMaximumSize(new Dimension(width, BTN_H + 4));
        c.setFocusable(false);
        return c;
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(new Color(50, 50, 50));
        s.setForeground(AppColors.TEXT);
        s.setFont(new Font("SansSerif", Font.PLAIN, 11));
        if (s.getEditor() instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(new Color(50, 50, 50));
            de.getTextField().setForeground(AppColors.TEXT);
            de.getTextField().setCaretColor(AppColors.TEXT);
            de.getTextField().setFont(new Font("SansSerif", Font.PLAIN, 11));
        }
    }

    private JToggleButton toggleBtn(String text, String tip) {
        JToggleButton b = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? AppColors.ACCENT : new Color(55, 55, 55));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(isSelected() ? AppColors.ACCENT.brighter() : AppColors.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(AppColors.TEXT);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(30, BTN_H));
        b.setMaximumSize(new Dimension(30, BTN_H + 4));
        b.setToolTipText(tip);
        return b;
    }

    private JButton actionBtn(String text, String tip) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(AppColors.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(AppColors.TEXT);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int w = b.getFontMetrics(b.getFont()).stringWidth(text) + 20;
        b.setPreferredSize(new Dimension(w, BTN_H));
        b.setMaximumSize(new Dimension(w + 4, BTN_H + 4));
        b.setToolTipText(tip);
        return b;
    }

    private JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(160, 160, 160));
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        return l;
    }

    private JPanel vSep() {
        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(AppColors.BORDER);
                g.drawLine(0, 4, 0, getHeight() - 4);
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(1, BTN_H));
        sep.setMaximumSize(new Dimension(1, BTN_H + 4));
        return sep;
    }
}
