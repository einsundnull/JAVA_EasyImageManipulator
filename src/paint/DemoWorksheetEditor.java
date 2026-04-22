package paint;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public class DemoWorksheetEditor {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DemoWorksheetEditor().createUI());
    }

    private final List<WorksheetLine> lines = new ArrayList<>();
    private WorksheetLine selectedLine;

    private void createUI() {
        JFrame frame = new JFrame("Arbeitsblatt Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel toolbar = createToolbar();
        JScrollPane scrollPane = createWorksheetArea();

        root.add(toolbar, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(new Color(245, 245, 245));

        JButton btnYellowTitle = new JButton("Label");
        JButton btnGreenTitle = new JButton("2. Überschrift");
        JButton btnBullet = new JButton("Punkte");

        JButton btnBgYellow = new JButton("Gelb");
        JButton btnBgGreen = new JButton("Grün");
        JButton btnBgBlue = new JButton("Blau");
        JButton btnBgRed = new JButton("Rot");

        String[] fontNames = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        JComboBox<String> fontBox = new JComboBox<>(fontNames);
        fontBox.setSelectedItem("SansSerif");

        Integer[] sizes = {14, 16, 18, 20, 22, 24, 28, 32, 36, 40};
        JComboBox<Integer> sizeBox = new JComboBox<>(sizes);
        sizeBox.setSelectedItem(22);

        JButton btnTextBlack = new JButton("Schwarz");
        JButton btnTextBlue = new JButton("Blau");
        JButton btnTextRed = new JButton("Rot");
        JButton btnTextGreen = new JButton("Grün");

        JButton btnAddLine = new JButton("Neue Zeile");
        JButton btnDeleteLine = new JButton("Zeile löschen");

        btnYellowTitle.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                line.setMode(LineMode.TITLE_YELLOW);
            }
        });

        btnGreenTitle.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                line.setMode(LineMode.TITLE_GREEN);
            }
        });

        btnBullet.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                line.setMode(LineMode.BULLET_LINE);
            }
        });

        btnBgYellow.addActionListener(e -> applyBackgroundTheme(new Color(239, 208, 115), new Color(220, 187, 88)));
        btnBgGreen.addActionListener(e -> applyBackgroundTheme(new Color(170, 210, 160), new Color(120, 170, 110)));
        btnBgBlue.addActionListener(e -> applyBackgroundTheme(new Color(160, 200, 235), new Color(110, 150, 200)));
        btnBgRed.addActionListener(e -> applyBackgroundTheme(new Color(235, 170, 170), new Color(190, 110, 110)));

        fontBox.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                String fontName = (String) fontBox.getSelectedItem();
                line.setFontFamily(fontName);
            }
        });

        sizeBox.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                Integer size = (Integer) sizeBox.getSelectedItem();
                line.setFontSize(size);
            }
        });

        btnTextBlack.addActionListener(e -> applyTextColor(Color.BLACK));
        btnTextBlue.addActionListener(e -> applyTextColor(new Color(40, 90, 180)));
        btnTextRed.addActionListener(e -> applyTextColor(new Color(180, 50, 50)));
        btnTextGreen.addActionListener(e -> applyTextColor(new Color(50, 140, 70)));

        btnAddLine.addActionListener(e -> addNewLine("Neue Zeile"));
        btnDeleteLine.addActionListener(e -> deleteSelectedLine());

        bar.add(new JLabel("Typ:"));
        bar.add(btnYellowTitle);
        bar.add(btnGreenTitle);
        bar.add(btnBullet);

        bar.add(Box.createHorizontalStrut(15));

        bar.add(new JLabel("Hintergrund:"));
        bar.add(btnBgYellow);
        bar.add(btnBgGreen);
        bar.add(btnBgBlue);
        bar.add(btnBgRed);

        bar.add(Box.createHorizontalStrut(15));

        bar.add(new JLabel("Schriftart:"));
        bar.add(fontBox);

        bar.add(new JLabel("Größe:"));
        bar.add(sizeBox);

        bar.add(Box.createHorizontalStrut(15));

        bar.add(new JLabel("Textfarbe:"));
        bar.add(btnTextBlack);
        bar.add(btnTextBlue);
        bar.add(btnTextRed);
        bar.add(btnTextGreen);

        bar.add(Box.createHorizontalStrut(15));

        bar.add(btnAddLine);
        bar.add(btnDeleteLine);

        return bar;
    }

    private JScrollPane createWorksheetArea() {
        JPanel sheet = new JPanel();
        sheet.setLayout(new BoxLayout(sheet, BoxLayout.Y_AXIS));
        sheet.setBackground(Color.WHITE);
        sheet.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        addNewLine(sheet, "1. Schah und sprich", LineMode.TITLE_YELLOW);
        addNewLine(sheet, "2. Finde im Bild", LineMode.TITLE_GREEN);
        addNewLine(sheet, "Das ist eine", LineMode.BULLET_LINE);
        addNewLine(sheet, "das Buch", LineMode.BULLET_LINE);

        JScrollPane scrollPane = new JScrollPane(sheet);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        this.sheetPanel = sheet;
        return scrollPane;
    }

    private JPanel sheetPanel;

    private void addNewLine(String text) {
        addNewLine(sheetPanel, text, LineMode.BULLET_LINE);
        sheetPanel.revalidate();
        sheetPanel.repaint();
    }

    private void addNewLine(JPanel parent, String text, LineMode mode) {
        WorksheetLine line = new WorksheetLine(text, mode);
        lines.add(line);

        line.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectLine(line);
            }
        });

        line.getTextField().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectLine(line);
            }
        });

        parent.add(line);
        parent.add(Box.createVerticalStrut(10));

        if (selectedLine == null) {
            selectLine(line);
        }
    }

    private void selectLine(WorksheetLine line) {
        if (selectedLine != null) {
            selectedLine.setSelected(false);
        }

        selectedLine = line;
        selectedLine.setSelected(true);
        selectedLine.getTextField().requestFocusInWindow();
    }

    private WorksheetLine getSelectedLine() {
        return selectedLine;
    }

    private void applyTextColor(Color color) {
        WorksheetLine line = getSelectedLine();
        if (line != null) {
            line.setTextColor(color);
        }
    }

    private void applyBackgroundTheme(Color fill, Color border) {
        WorksheetLine line = getSelectedLine();
        if (line != null) {
            line.setFillColor(fill);
            line.setBorderColor(border);
        }
    }

    private void deleteSelectedLine() {
        if (selectedLine == null || sheetPanel == null) {
            return;
        }

        int index = lines.indexOf(selectedLine);
        if (index < 0) {
            return;
        }

        Component[] components = sheetPanel.getComponents();

        for (int i = 0; i < components.length; i++) {
            if (components[i] == selectedLine) {
                sheetPanel.remove(i);
                if (i < sheetPanel.getComponentCount()) {
                    Component maybeGap = sheetPanel.getComponent(i);
                    if (maybeGap instanceof Box.Filler) {
                        sheetPanel.remove(i);
                    }
                }
                break;
            }
        }

        lines.remove(selectedLine);
        selectedLine = null;

        if (!lines.isEmpty()) {
            selectLine(lines.get(Math.max(0, index - 1)));
        }

        sheetPanel.revalidate();
        sheetPanel.repaint();
    }

    enum LineMode {
        TITLE_YELLOW,
        TITLE_GREEN,
        BULLET_LINE
    }

    static class WorksheetLine extends JComponent {

        private final JTextField textField;

        private LineMode mode;
        private boolean selected = false;

        private Color fillColor;
        private Color borderColor;
        private Color textColor = Color.BLACK;
        private Color bulletColor = new Color(90, 160, 80);

        private String fontFamily = "SansSerif";
        private int fontSize = 22;
        private int fontStyle = Font.BOLD;

        public WorksheetLine(String text, LineMode mode) {
            this.mode = mode;
            setLayout(null);
            setOpaque(false);

            if (mode == LineMode.TITLE_YELLOW) {
                fillColor = new Color(239, 208, 115);
                borderColor = new Color(220, 187, 88);
            } else if (mode == LineMode.TITLE_GREEN) {
                fillColor = new Color(170, 210, 160);
                borderColor = new Color(120, 170, 110);
            } else {
                fillColor = null;
                borderColor = null;
            }

            textField = new JTextField(text);
            textField.setBorder(null);
            textField.setOpaque(false);
            textField.setForeground(textColor);
            textField.setCaretColor(textColor);
            textField.setFont(new Font(fontFamily, fontStyle, fontSize));

            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateSize(); }
                @Override public void removeUpdate(DocumentEvent e) { updateSize(); }
                @Override public void changedUpdate(DocumentEvent e) { updateSize(); }
            });

            add(textField);
            updateSize();
        }

        public JTextField getTextField() {
            return textField;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        public void setMode(LineMode mode) {
            this.mode = mode;

            if (mode == LineMode.TITLE_YELLOW) {
                fillColor = new Color(239, 208, 115);
                borderColor = new Color(220, 187, 88);
            } else if (mode == LineMode.TITLE_GREEN) {
                fillColor = new Color(170, 210, 160);
                borderColor = new Color(120, 170, 110);
            }

            updateSize();
        }

        public void setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            updateFont();
        }

        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
            updateFont();
        }

        public void setTextColor(Color textColor) {
            this.textColor = textColor;
            textField.setForeground(textColor);
            textField.setCaretColor(textColor);
            repaint();
        }

        public void setFillColor(Color fillColor) {
            this.fillColor = fillColor;
            repaint();
        }

        public void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
            repaint();
        }

        private void updateFont() {
            textField.setFont(new Font(fontFamily, fontStyle, fontSize));
            updateSize();
        }

        private void updateSize() {
            FontMetrics fm = textField.getFontMetrics(textField.getFont());
            String text = textField.getText();
            if (text == null) text = "";

            int textWidth = fm.stringWidth(text + "  ");
            int textHeight = fm.getHeight();

            int w;
            int h;

            if (mode == LineMode.BULLET_LINE) {
                w = Math.max(500, textWidth + 220);
                h = textHeight + 14;
                textField.setBounds(34, (h - textHeight) / 2, textWidth, textHeight);
            } else {
                w = textWidth + 90;
                h = textHeight + 22;
                textField.setBounds(44, (h - textHeight) / 2 - 1, textWidth, textHeight + 4);
            }

            Dimension d = new Dimension(w, h);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 6));
            setSize(d);

            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (mode == LineMode.TITLE_YELLOW || mode == LineMode.TITLE_GREEN) {
                Shape pill = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h - 2, h - 2);

                if (fillColor != null) {
                    g2.setColor(fillColor);
                    g2.fill(pill);
                }

                if (borderColor != null) {
                    g2.setColor(borderColor);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(pill);
                }

                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new Ellipse2D.Double(12, h / 2.0 - 9, 18, 18));
            } else if (mode == LineMode.BULLET_LINE) {
                g2.setColor(bulletColor);
                g2.fill(new Ellipse2D.Double(10, h / 2.0 - 6, 12, 12));

                int lineStart = textField.getX() + textField.getWidth() + 10;
                int lineY = h / 2;

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(Color.BLACK);
                g2.drawLine(lineStart, lineY, w - 10, lineY);
            }

            if (selected) {
                g2.setColor(new Color(60, 120, 255, 120));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
            }

            g2.dispose();
        }
    }
}