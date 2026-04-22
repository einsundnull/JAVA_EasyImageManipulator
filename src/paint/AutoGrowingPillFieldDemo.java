package paint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class AutoGrowingPillFieldDemo {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Auto Growing Pill Field");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 250);
            frame.setLocationRelativeTo(null);

            JPanel root = new JPanel(new GridBagLayout());
            root.setBackground(new Color(245, 245, 245));

            AutoGrowingPillField pillField = new AutoGrowingPillField("1. Schah und sprich");
            root.add(pillField);

            frame.setContentPane(root);
            frame.setVisible(true);
        });
    }

    public static class AutoGrowingPillField extends JComponent {

        private final JTextField textField;

        private final Color fillColor = new Color(239, 208, 115);   // gold ähnlich wie im Bild
        private final Color borderColor = new Color(220, 187, 88);
        private final Color textColor = new Color(20, 20, 20);

        private final int arc = 42;
        private final int horizontalPadding = 20;
        private final int verticalPadding = 12;
        private final int gapAfterCircle = 14;
        private final int circleDiameter = 18;
        private final int minTextWidth = 180;

        public AutoGrowingPillField(String initialText) {
            setLayout(null);
            setOpaque(false);

            textField = new JTextField(initialText);
            textField.setBorder(null);
            textField.setOpaque(false);
            textField.setForeground(textColor);
            textField.setCaretColor(textColor);
            textField.setSelectionColor(new Color(255, 240, 180));
            textField.setSelectedTextColor(textColor);
            textField.setFont(new Font("SansSerif", Font.BOLD, 22));

            add(textField);

            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateSize(); }
                @Override public void removeUpdate(DocumentEvent e) { updateSize(); }
                @Override public void changedUpdate(DocumentEvent e) { updateSize(); }
            });

            updateSize();
        }

        private void updateSize() {
            FontMetrics fm = textField.getFontMetrics(textField.getFont());
            String text = textField.getText();
            if (text == null) text = "";

            int textWidth = Math.max(minTextWidth, fm.stringWidth(text) + 10);
            int textHeight = fm.getHeight();

            int totalWidth =
                    horizontalPadding
                    + circleDiameter
                    + gapAfterCircle
                    + textWidth
                    + horizontalPadding;

            int totalHeight =
                    verticalPadding
                    + Math.max(circleDiameter, textHeight)
                    + verticalPadding;

            setPreferredSize(new Dimension(totalWidth, totalHeight));
            setMinimumSize(new Dimension(totalWidth, totalHeight));
            setSize(new Dimension(totalWidth, totalHeight));

            int textX = horizontalPadding + circleDiameter + gapAfterCircle;
            int textY = (totalHeight - textHeight) / 2 - 1;
            textField.setBounds(textX, textY, textWidth, textHeight + 4);

            revalidate();
            repaint();

            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Shape pill = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc);

            // Hintergrund
            g2.setColor(fillColor);
            g2.fill(pill);

            // Rand
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(pill);

            // Kreis links
            int circleX = horizontalPadding;
            int circleY = (h - circleDiameter) / 2;

            g2.setColor(textColor);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(circleX, circleY, circleDiameter, circleDiameter));

            g2.dispose();
        }

        @Override
        public void doLayout() {
            updateSize();
        }
    }
}