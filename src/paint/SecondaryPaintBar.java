package paint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Thin strip shown above the control bar when paint mode (Alt+P) is active in
 * the secondary window. Shows the current tool name and primary/secondary color
 * swatches; actual tool/color selection happens in the main PaintToolbar.
 */
class SecondaryPaintBar extends JPanel {

    private final SelectiveAlphaEditor ed;
    private final JLabel  toolLabel;
    private final JButton primarySwatch;
    private final JButton secondarySwatch;

    SecondaryPaintBar(SelectiveAlphaEditor ed) {
        this.ed = ed;
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        setBackground(new Color(35, 60, 35));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

        JLabel icon = new JLabel("🎨  Paint-Modus  |  Tool: ");
        icon.setFont(new Font("SansSerif", Font.BOLD, 12));
        icon.setForeground(new Color(120, 220, 120));
        add(icon);

        toolLabel = new JLabel("Pencil");
        toolLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        toolLabel.setForeground(AppColors.TEXT);
        add(toolLabel);

        JLabel colorLbl = new JLabel("   Farbe:");
        colorLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        colorLbl.setForeground(AppColors.TEXT_MUTED);
        add(colorLbl);

        primarySwatch   = swatch("Primärfarbe (im Hauptfenster ändern)");
        secondarySwatch = swatch("Sekundärfarbe (im Hauptfenster ändern)");
        add(primarySwatch);
        add(secondarySwatch);

        JLabel hint = new JLabel("   Alt+P = Beenden");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hint.setForeground(AppColors.TEXT_MUTED);
        add(hint);
    }

    /** Call periodically or on repaint to reflect current toolbar state. */
    void syncState() {
        PaintToolbar tb = ed.paintToolbar;
        if (tb == null) return;
        toolLabel.setText(tb.getActiveTool().name().replace("_", " "));
        Color p = tb.getPrimaryColor();
        Color s = tb.getSecondaryColor();
        primarySwatch.setBackground(p);
        primarySwatch.setForeground(p.getRed() + p.getGreen() + p.getBlue() > 382
                ? Color.BLACK : Color.WHITE);
        secondarySwatch.setBackground(s);
        secondarySwatch.setForeground(s.getRed() + s.getGreen() + s.getBlue() > 382
                ? Color.BLACK : Color.WHITE);
        repaint();
    }

    private JButton swatch(String tip) {
        JButton b = new JButton("  ");
        b.setPreferredSize(new Dimension(28, 18));
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setToolTipText(tip);
        b.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        return b;
    }
}
