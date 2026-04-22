package paint;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.*;

public class AutoGrowingPillFieldDemo2 extends JComponent {

    public enum Mode {
        PILL_YELLOW,
        PILL_GREEN,
        BULLET_LINE
    }

    private final JTextField textField;
    private Mode mode;

    private final int paddingX = 16;
    private final int paddingY = 10;

    public AutoGrowingPillFieldDemo2(String text, Mode mode) {
        this.mode = mode;

        setLayout(null);
        setOpaque(false);

        textField = new JTextField(text);
        textField.setBorder(null);
        textField.setOpaque(false);
        textField.setFont(new Font("SansSerif", Font.BOLD, 22));
        textField.setForeground(Color.BLACK);

        add(textField);

        textField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSize(); }
            public void removeUpdate(DocumentEvent e) { updateSize(); }
            public void changedUpdate(DocumentEvent e) { updateSize(); }
        });

        updateSize();
    }

    private void updateSize() {
        FontMetrics fm = textField.getFontMetrics(textField.getFont());
        String text = textField.getText();

        int textWidth = fm.stringWidth(text + "  ");
        int textHeight = fm.getHeight();

        int w, h;

        if (mode == Mode.BULLET_LINE) {
            w = textWidth + 200; // extra Platz für Linie
            h = textHeight + 10;
        } else {
            w = textWidth + 80;
            h = textHeight + 20;
        }

        setPreferredSize(new Dimension(w, h));
        setSize(w, h);

        textField.setBounds(40, (h - textHeight) / 2, textWidth, textHeight);

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (mode == Mode.PILL_YELLOW || mode == Mode.PILL_GREEN) {

            Color fill = (mode == Mode.PILL_YELLOW)
                    ? new Color(239, 208, 115)
                    : new Color(170, 210, 160);

            Color border = (mode == Mode.PILL_YELLOW)
                    ? new Color(220, 187, 88)
                    : new Color(120, 170, 110);

            Shape pill = new RoundRectangle2D.Double(0, 0, w, h, 40, 40);

            g2.setColor(fill);
            g2.fill(pill);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(pill);

            // Kreis links
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(10, h/2 - 9, 18, 18));
        }

        if (mode == Mode.BULLET_LINE) {

            // Punkt
            g2.setColor(new Color(90, 160, 80));
            g2.fill(new Ellipse2D.Double(10, h/2 - 6, 12, 12));

            // Linie
            int lineStart = textField.getX() + textField.getWidth() + 10;
            int lineY = h / 2;

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.BLACK);
            g2.drawLine(lineStart, lineY, w - 10, lineY);
        }

        g2.dispose();
    }
}