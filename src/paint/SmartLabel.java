package paint;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public class SmartLabel extends JComponent {

    public enum Mode {
        PILL,
        BULLET_LINE
    }

    public enum ThemeColor {
        YELLOW,
        GREEN,
        BLUE,
        RED
    }

    private final JTextField textField;

    private Mode mode;
    private ThemeColor themeColor;

    private final int paddingX = 16;
    private final int paddingY = 10;

    public SmartLabel(String text, Mode mode, ThemeColor themeColor) {
        this.mode = mode;
        this.themeColor = themeColor;

        setLayout(null);
        setOpaque(false);

        textField = new JTextField(text);
        textField.setBorder(null);
        textField.setOpaque(false);
        textField.setFont(new Font("SansSerif", Font.BOLD, 22));
        textField.setForeground(Color.BLACK);

        add(textField);

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateSize(); }
            @Override public void removeUpdate(DocumentEvent e) { updateSize(); }
            @Override public void changedUpdate(DocumentEvent e) { updateSize(); }
        });

        updateSize();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        updateSize();
    }

    public Mode getMode() {
        return mode;
    }

    public void setThemeColor(ThemeColor themeColor) {
        this.themeColor = themeColor;
        repaint();
    }

    public ThemeColor getThemeColor() {
        return themeColor;
    }

    public JTextField getTextField() {
        return textField;
    }

    private void updateSize() {
        FontMetrics fm = textField.getFontMetrics(textField.getFont());
        String text = textField.getText();
        if (text == null) text = "";

        int textWidth = fm.stringWidth(text + "  ");
        int textHeight = fm.getHeight();

        int w;
        int h;

        if (mode == Mode.BULLET_LINE) {
            w = textWidth + 220;
            h = textHeight + 12;
            textField.setBounds(30, (h - textHeight) / 2, textWidth, textHeight);
        } else {
            w = textWidth + 80;
            h = textHeight + 20;
            textField.setBounds(40, (h - textHeight) / 2 - 1, textWidth, textHeight + 4);
        }

        Dimension d = new Dimension(w, h);
        setPreferredSize(d);
        setMinimumSize(d);
        setSize(d);

        revalidate();
        repaint();

        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private Color getFillColor() {
        return switch (themeColor) {
            case YELLOW -> new Color(239, 208, 115);
            case GREEN  -> new Color(170, 210, 160);
            case BLUE   -> new Color(160, 200, 235);
            case RED    -> new Color(235, 170, 170);
        };
    }

    private Color getBorderColor() {
        return switch (themeColor) {
            case YELLOW -> new Color(220, 187, 88);
            case GREEN  -> new Color(120, 170, 110);
            case BLUE   -> new Color(110, 150, 200);
            case RED    -> new Color(190, 110, 110);
        };
    }

    private Color getBulletColor() {
        return switch (themeColor) {
            case YELLOW -> new Color(190, 160, 60);
            case GREEN  -> new Color(90, 160, 80);
            case BLUE   -> new Color(70, 130, 190);
            case RED    -> new Color(190, 80, 80);
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (mode == Mode.PILL) {
            Shape pill = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 40, 40);

            g2.setColor(getFillColor());
            g2.fill(pill);

            g2.setColor(getBorderColor());
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(pill);

            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(10, h / 2.0 - 9, 18, 18));
        }

        if (mode == Mode.BULLET_LINE) {
            g2.setColor(getBulletColor());
            g2.fill(new Ellipse2D.Double(10, h / 2.0 - 6, 12, 12));

            int lineStart = textField.getX() + textField.getWidth() + 10;
            int lineY = h / 2;

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.BLACK);
            g2.drawLine(lineStart, lineY, w - 10, lineY);
        }

        g2.dispose();
    }
}