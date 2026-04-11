package paint;

import java.awt.BasicStroke;
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
 */
public class UIComponentFactory {

    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 50;

    // ── Dialog creation ────────────────────────────────────────────────────────
    public static JDialog createBaseDialog(JFrame owner, String title, int w, int h) {
        JDialog d = new JDialog(owner, title, true);
        d.setSize(w, h);
        d.setLocationRelativeTo(owner);
        d.setResizable(false);
        d.getContentPane().setBackground(AppColors.BG_PANEL);
        d.setLayout(new BorderLayout());

        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleBar.setBackground(new Color(35, 35, 35));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER));

        JLabel tl = new JLabel(title);
        tl.setForeground(AppColors.TEXT);
        tl.setFont(new Font("SansSerif", Font.BOLD, 13));
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
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        l.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return l;
    }

    public static JLabel htmlLabel(String html, Color color, int size) {
        JLabel l = new JLabel("<html><center>" + html + "</center></html>");
        l.setForeground(color);
        l.setFont(new Font("SansSerif", Font.PLAIN, size));
        l.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return l;
    }

    // ── Button factories ───────────────────────────────────────────────────────
    public static JButton buildButton(String text, Color bg, Color hover) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fillColor = getModel().isRollover() ? hover : (isEnabled() ? bg : AppColors.BTN_BG.darker());
                g2.setColor(fillColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setForeground(AppColors.TEXT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        btn.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        btn.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        return btn;
    }

    public static JToggleButton buildModeToggleBtn(String symbol, String tooltip) {
        JToggleButton btn = new JToggleButton(symbol) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected() ? AppColors.ACCENT_ACTIVE
                        : (getModel().isRollover() ? AppColors.BTN_HOVER : AppColors.BTN_BG);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isSelected()) {
                    g2.setColor(AppColors.ACCENT);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 16));
        btn.setForeground(AppColors.TEXT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JButton buildNavButton(String symbol) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgColor = !isEnabled() ? new Color(0, 0, 0, 30)
                        : getModel().isRollover() ? new Color(255, 255, 255, 55) : new Color(0, 0, 0, 110);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(isEnabled() ? AppColors.TEXT : AppColors.TEXT_MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 30));
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
