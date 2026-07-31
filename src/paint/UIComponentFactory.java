package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

/**
 * Factory for creating custom UI components with consistent styling.
 * Used throughout the editor for buttons, labels, dialogs, and panels.
 *
 * <p><b>Auf Tokens migriert am 2026-07-31</b> (§21, erste Datei der
 * Bereinigung). Farben aus {@link AppColors}, Fonts/Maße aus {@link AppTheme}.
 * <b>Werterhaltend:</b> jedes Literal wurde durch ein Token mit exakt
 * demselben Wert ersetzt, nachgewiesen per Vergleich der aufgelösten Werte.
 * Vorher 7&nbsp;{@code new Font} und 4&nbsp;{@code new Color}, jetzt 0 feste.
 *
 * <p><b>Drei Dinge bleiben bewusst Literal:</b>
 * <ul>
 *   <li>{@link #styledLabel} und {@link #htmlLabel} bekommen die Schriftgröße
 *       als <b>Parameter</b> — daraus kann kein Token werden. Zentralisiert ist
 *       dort nur die Familie ({@link AppTheme#FAMILY}).</li>
 *   <li>{@link #centeredColumnPanel} bekommt seine Abstände ebenfalls als
 *       Parameter.</li>
 *   <li>Der Innenabstand {@code (0, 5, 0, 5)} der Knöpfe: 5 px passt auf kein
 *       {@code PAD_*}-Token (4 oder 6). <b>Nicht gerundet</b> — das hätte das
 *       Aussehen geändert (§21). Entweder bleibt es so, oder es bekommt ein
 *       eigenes Token, wenn der Wert öfter auftaucht.</li>
 * </ul>
 */
public class UIComponentFactory {

    // Knopfgroesse kommt aus AppTheme (§21). Sie stand hier und als
    // TOPBAR_BTN_W/H in SelectiveAlphaEditor doppelt - ein Wert, zwei Quellen.

    // ── Dialog creation ────────────────────────────────────────────────────────
    public static JDialog createBaseDialog(JFrame owner, String title, int w, int h) {
        JDialog d = new JDialog(owner, title, true);
        d.setSize(w, h);
        d.setLocationRelativeTo(owner);
        d.setResizable(false);
        d.getContentPane().setBackground(AppColors.BG_PANEL);
        d.setLayout(new BorderLayout());

        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleBar.setBackground(AppColors.BG_TITLEBAR);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

        JLabel tl = new JLabel(title);
        tl.setForeground(AppColors.TEXT);
        tl.setFont(AppTheme.FONT_LG_BOLD);
        titleBar.add(tl);
        d.add(titleBar, BorderLayout.NORTH);

        return d;
    }

    // ── Panel helpers ──────────────────────────────────────────────────────────
    public static JPanel centeredColumnPanel(int vp, int hp, int bp) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppColors.BG_PANEL);
        p.setBorder(new EmptyBorder(vp, hp, bp, hp));
        return p;
    }

    // ── Label factories ────────────────────────────────────────────────────────
    public static JLabel styledLabel(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(AppTheme.FAMILY, style, size));
        l.setForeground(color);
        l.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return l;
    }

    public static JLabel htmlLabel(String html, Color color, int size) {
        JLabel l = new JLabel("<html><center>" + html + "</center></html>");
        l.setForeground(color);
        l.setFont(new Font(AppTheme.FAMILY, Font.PLAIN, size));
        l.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return l;
    }

    // ── Button factories ───────────────────────────────────────────────────────
    // Buttons in the top Button Row
    public static JButton buildButton(String text, Color bg, Color hover) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fillColor = getModel().isRollover() ? hover : (isEnabled() ? bg : AppColors.BTN_BG.darker());
                g2.setColor(fillColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_LG, AppTheme.RADIUS_LG);
                super.paintComponent(g);
            }
        };
        btn.setForeground(AppColors.TEXT);
        btn.setFont(AppTheme.FONT_MD);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        btn.setPreferredSize(new Dimension(AppTheme.BTN_W, AppTheme.BTN_H));
        btn.setMinimumSize(new Dimension(AppTheme.BTN_W, AppTheme.BTN_H));
        return btn;
    }

    public static JToggleButton buildModeToggleBtn(String symbol, String tooltip) {
        PanelToggleButton btn = new PanelToggleButton(symbol, PanelToggleButton.Style.MODE);
        btn.setFont(AppTheme.FONT_SYMBOL);
        btn.setForeground(AppColors.TEXT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        btn.setPreferredSize(new Dimension(AppTheme.BTN_W, AppTheme.BTN_H));
        btn.setMinimumSize(new Dimension(AppTheme.BTN_W, AppTheme.BTN_H));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Like {@link #buildModeToggleBtn} but with a slightly blue-tinted base color
     * to visually distinguish book-context buttons from regular mode buttons.
     */
    public static JToggleButton buildBookToggleBtn(String symbol, String tooltip) {
        PanelToggleButton btn = new PanelToggleButton(symbol, PanelToggleButton.Style.BOOK);
        btn.setFont(AppTheme.FONT_XL);
        btn.setForeground(AppColors.TEXT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(AppTheme.BTN_W, AppTheme.BTN_H));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setVisible(false); // hidden until book mode is active
        return btn;
    }

    public static JButton buildNavButton(String symbol) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgColor = !isEnabled() ? AppColors.NAV_BG_DISABLED
                        : getModel().isRollover() ? AppColors.NAV_BG_HOVER : AppColors.NAV_BG;
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppTheme.RADIUS_XXL, AppTheme.RADIUS_XXL);
                g2.setColor(isEnabled() ? AppColors.TEXT : AppColors.TEXT_MUTED);
                g2.setFont(AppTheme.FONT_NAV);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(symbol)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(symbol, tx, ty);
            }
        };
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
