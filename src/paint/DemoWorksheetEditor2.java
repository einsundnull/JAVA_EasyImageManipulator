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

public class DemoWorksheetEditor2 extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DemoWorksheetEditor2 editor = new DemoWorksheetEditor2();
            editor.setVisible(true);
        });
    }

    private final JPanel sheetPanel;
    private final JScrollPane scrollPane;

    private final List<WorksheetLine> lines = new ArrayList<>();
    private WorksheetLine selectedLine;

    private final JComboBox<String> fontBox;
    private final JComboBox<Integer> sizeBox;
    private final JSpinner bulletLineLengthSpinner;
    private final JSpinner frameBodyHeightSpinner;

    public DemoWorksheetEditor2() {
        super("Arbeitsblatt Editor");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 850);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBackground(new Color(245, 245, 245));

        JButton btnNewLabel = new JButton("Neues Label");
        JButton btnNewHeading2 = new JButton("Neue Überschrift 2");
        JButton btnNewBullet = new JButton("Neuer Punkt");
        JButton btnNewFrame = new JButton("Neuer Rahmen");

        JButton btnIndent = new JButton("Einrücken");
        JButton btnOutdent = new JButton("Ausrücken");
        JButton btnDelete = new JButton("Zeile löschen");
        JButton btnDeselect = new JButton("Alles deselektieren");

        JButton btnYellow = new JButton("Label Gelb");
        JButton btnGreen = new JButton("Label Grün");
        JButton btnBlue = new JButton("Label Blau");
        JButton btnRed = new JButton("Label Rot");

        JButton btnTextBlack = new JButton("Schwarz");
        JButton btnTextBlue = new JButton("Blau");
        JButton btnTextRed = new JButton("Rot");
        JButton btnTextGreen = new JButton("Grün");

        String[] fontNames = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        fontBox = new JComboBox<>(fontNames);
        fontBox.setSelectedItem("SansSerif");

        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 48};
        sizeBox = new JComboBox<>(sizes);
        sizeBox.setSelectedItem(22);

        bulletLineLengthSpinner = new JSpinner(new SpinnerNumberModel(120, 20, 1200, 10));
        frameBodyHeightSpinner = new JSpinner(new SpinnerNumberModel(90, 20, 1200, 10));

        toolbar.add(new JLabel("Einfügen:"));
        toolbar.add(btnNewLabel);
        toolbar.add(btnNewHeading2);
        toolbar.add(btnNewBullet);
        toolbar.add(btnNewFrame);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Struktur:"));
        toolbar.add(btnIndent);
        toolbar.add(btnOutdent);
        toolbar.add(btnDelete);
        toolbar.add(btnDeselect);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Label-Farbe:"));
        toolbar.add(btnYellow);
        toolbar.add(btnGreen);
        toolbar.add(btnBlue);
        toolbar.add(btnRed);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Schriftart:"));
        toolbar.add(fontBox);
        toolbar.add(new JLabel("Größe:"));
        toolbar.add(sizeBox);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Textfarbe:"));
        toolbar.add(btnTextBlack);
        toolbar.add(btnTextBlue);
        toolbar.add(btnTextRed);
        toolbar.add(btnTextGreen);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Punkt-Linie:"));
        toolbar.add(bulletLineLengthSpinner);

        toolbar.add(Box.createHorizontalStrut(12));

        toolbar.add(new JLabel("Rahmen-Höhe:"));
        toolbar.add(frameBodyHeightSpinner);

        sheetPanel = new JPanel();
        sheetPanel.setLayout(new BoxLayout(sheetPanel, BoxLayout.Y_AXIS));
        sheetPanel.setBackground(Color.WHITE);
        sheetPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        sheetPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                deselectAllLines();
            }
        });

        scrollPane = new JScrollPane(sheetPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        root.add(toolbar, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);

        setContentPane(root);

        btnNewLabel.addActionListener(e -> insertLineBelowSelected(LineType.LABEL));
        btnNewHeading2.addActionListener(e -> insertLineBelowSelected(LineType.HEADING2));
        btnNewBullet.addActionListener(e -> insertLineBelowSelected(LineType.BULLET));
        btnNewFrame.addActionListener(e -> insertLineBelowSelected(LineType.FRAME));

        btnIndent.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                line.indent();
                refreshSheet();
            }
        });

        btnOutdent.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                line.outdent();
                refreshSheet();
            }
        });

        btnDelete.addActionListener(e -> deleteSelectedLine());
        btnDeselect.addActionListener(e -> deselectAllLines());

        btnYellow.addActionListener(e -> applyLabelTheme(
                new Color(239, 208, 115),
                new Color(220, 187, 88)
        ));
        btnGreen.addActionListener(e -> applyLabelTheme(
                new Color(170, 210, 160),
                new Color(120, 170, 110)
        ));
        btnBlue.addActionListener(e -> applyLabelTheme(
                new Color(160, 200, 235),
                new Color(110, 150, 200)
        ));
        btnRed.addActionListener(e -> applyLabelTheme(
                new Color(235, 170, 170),
                new Color(190, 110, 110)
        ));

        btnTextBlack.addActionListener(e -> applyTextColor(Color.BLACK));
        btnTextBlue.addActionListener(e -> applyTextColor(new Color(40, 90, 180)));
        btnTextRed.addActionListener(e -> applyTextColor(new Color(180, 50, 50)));
        btnTextGreen.addActionListener(e -> applyTextColor(new Color(50, 140, 70)));

        fontBox.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                String fontName = (String) fontBox.getSelectedItem();
                if (fontName != null) {
                    line.setFontFamily(fontName);
                    refreshSheet();
                }
            }
        });

        sizeBox.addActionListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null) {
                Integer size = (Integer) sizeBox.getSelectedItem();
                if (size != null) {
                    line.setFontSize(size);
                    refreshSheet();
                }
            }
        });

        bulletLineLengthSpinner.addChangeListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null && line.getType() == LineType.BULLET) {
                line.setBulletLineLength((Integer) bulletLineLengthSpinner.getValue());
                refreshSheet();
            }
        });

        frameBodyHeightSpinner.addChangeListener(e -> {
            WorksheetLine line = getSelectedLine();
            if (line != null && line.getType() == LineType.FRAME) {
                line.setFrameBodyHeight((Integer) frameBodyHeightSpinner.getValue());
                refreshSheet();
            }
        });

        addInitialContent();
    }

    private void addInitialContent() {
        addLineAtEnd(new WorksheetLine("1. Schah und sprich", LineType.LABEL, 0));
        addLineAtEnd(new WorksheetLine("Zeige im Bild:", LineType.HEADING2, 1));
        addLineAtEnd(new WorksheetLine("das Buch", LineType.BULLET, 2));
        addLineAtEnd(new WorksheetLine("Lehrerhilfe kurz:", LineType.FRAME, 0));

        if (!lines.isEmpty()) {
            selectLine(lines.get(0));
        }
    }

    private void addLineAtEnd(WorksheetLine line) {
        registerLine(line);
        lines.add(line);
        sheetPanel.add(line);
        sheetPanel.add(Box.createVerticalStrut(8));
    }

    private void registerLine(WorksheetLine line) {
        line.setSelectionListener(this::selectLine);

        line.getTextField().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectLine(line);
                e.consume();
            }
        });

        line.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectLine(line);
            }
        });
    }

    private void selectLine(WorksheetLine line) {
        if (selectedLine != null) {
            selectedLine.setSelected(false);
        }

        selectedLine = line;

        if (selectedLine != null) {
            selectedLine.setSelected(true);
            selectedLine.getTextField().requestFocusInWindow();
            fontBox.setSelectedItem(selectedLine.getFontFamily());
            sizeBox.setSelectedItem(selectedLine.getFontSizeValue());
            bulletLineLengthSpinner.setValue(selectedLine.getBulletLineLength());
            frameBodyHeightSpinner.setValue(selectedLine.getFrameBodyHeight());
        }

        refreshSheet();
    }

    private void deselectAllLines() {
        if (selectedLine != null) {
            selectedLine.setSelected(false);
            selectedLine = null;
        }
        sheetPanel.requestFocusInWindow();
        refreshSheet();
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

    private void applyLabelTheme(Color fill, Color border) {
        WorksheetLine line = getSelectedLine();
        if (line != null && (line.getType() == LineType.LABEL || line.getType() == LineType.FRAME)) {
            line.setFillColor(fill);
            line.setBorderColor(border);
        }
    }

    private void insertLineBelowSelected(LineType newType) {
        int indent = getSuggestedIndent(newType, selectedLine);
        String defaultText = switch (newType) {
            case LABEL -> "Neues Label";
            case HEADING2 -> "Neue Überschrift";
            case BULLET -> "Neuer Punkt";
            case FRAME -> "Neuer Rahmen";
        };

        WorksheetLine newLine = new WorksheetLine(defaultText, newType, indent);
        registerLine(newLine);

        if (selectedLine == null || lines.isEmpty()) {
            lines.add(newLine);
        } else {
            int lineIndex = lines.indexOf(selectedLine);
            if (lineIndex < 0) {
                lines.add(newLine);
            } else {
                lines.add(lineIndex + 1, newLine);
            }
        }

        rebuildSheetFromModel();
        selectLine(newLine);
    }

    private int getSuggestedIndent(LineType newType, WorksheetLine selected) {
        if (selected == null) {
            return switch (newType) {
                case LABEL, FRAME -> 0;
                case HEADING2 -> 1;
                case BULLET -> 2;
            };
        }

        if (newType == LineType.LABEL || newType == LineType.FRAME) {
            return 0;
        }

        if (newType == LineType.HEADING2) {
            if (selected.getType() == LineType.LABEL || selected.getType() == LineType.FRAME) {
                return selected.getIndentLevel() + 1;
            }
            return Math.max(1, selected.getIndentLevel());
        }

        if (newType == LineType.BULLET) {
            if (selected.getType() == LineType.HEADING2) {
                return selected.getIndentLevel() + 1;
            }
            if (selected.getType() == LineType.BULLET) {
                return selected.getIndentLevel();
            }
            return Math.max(2, selected.getIndentLevel() + 2);
        }

        return 0;
    }

    private void deleteSelectedLine() {
        if (selectedLine == null) {
            return;
        }

        int index = lines.indexOf(selectedLine);
        if (index < 0) {
            return;
        }

        lines.remove(index);
        selectedLine = null;

        rebuildSheetFromModel();

        if (!lines.isEmpty()) {
            int newIndex = Math.max(0, index - 1);
            selectLine(lines.get(newIndex));
        } else {
            refreshSheet();
        }
    }

    private void rebuildSheetFromModel() {
        sheetPanel.removeAll();
        for (WorksheetLine line : lines) {
            sheetPanel.add(line);
            sheetPanel.add(Box.createVerticalStrut(8));
        }
        refreshSheet();
    }

    private void refreshSheet() {
        for (WorksheetLine line : lines) {
            line.refreshLayout();
        }
        sheetPanel.revalidate();
        sheetPanel.repaint();
    }

    enum LineType {
        LABEL,
        HEADING2,
        BULLET,
        FRAME
    }

    interface SelectionListener {
        void onSelected(WorksheetLine line);
    }

    static class WorksheetLine extends JComponent {

        private final JTextField textField;

        private LineType type;
        private int indentLevel;
        private final int indentWidth = 28;

        private boolean selected = false;
        private SelectionListener selectionListener;

        private Color fillColor = new Color(239, 208, 115);
        private Color borderColor = new Color(220, 187, 88);
        private Color textColor = Color.BLACK;
        private Color bulletColor = new Color(90, 90, 90);
        private Color lineColor = Color.BLACK;

        private String fontFamily = "SansSerif";
        private int fontSize = 22;
        private int fontStyle = Font.BOLD;

        private final int labelLeftInnerPadding = 16;
        private final int labelRightInnerPadding = 18;
        private final int labelCircleDiameter = 18;
        private final int labelCircleGap = 12;

        private final int bulletDiameter = 12;
        private final int bulletTextGap = 12;
        private final int bulletLineGap = 10;
        private int bulletLineLength = 120;

        private final int headingLeftPadding = 8;
        private final int selectionExtraWidth = 12;

        private final int frameTopPadding = 10;
        private final int frameBottomPadding = 12;
        private final int frameSidePadding = 10;
        private int frameBodyHeight = 90;

        public WorksheetLine(String text, LineType type, int indentLevel) {
            this.type = type;
            this.indentLevel = Math.max(0, indentLevel);

            setLayout(null);
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            if (type == LineType.HEADING2) {
                fontSize = 20;
            }

            textField = new JTextField(text);
            textField.setBorder(null);
            textField.setOpaque(false);
            textField.setForeground(textColor);
            textField.setCaretColor(textColor);
            textField.setFont(new Font(fontFamily, fontStyle, fontSize));

            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    refreshLayout();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    refreshLayout();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    refreshLayout();
                }
            });

            textField.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (selectionListener != null) {
                        selectionListener.onSelected(WorksheetLine.this);
                    }
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (selectionListener != null) {
                        selectionListener.onSelected(WorksheetLine.this);
                    }
                }
            });

            add(textField);
            refreshLayout();
        }

        public void setSelectionListener(SelectionListener selectionListener) {
            this.selectionListener = selectionListener;
        }

        public JTextField getTextField() {
            return textField;
        }

        public LineType getType() {
            return type;
        }

        public int getIndentLevel() {
            return indentLevel;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        public void indent() {
            indentLevel++;
            refreshLayout();
        }

        public void outdent() {
            if (indentLevel > 0) {
                indentLevel--;
                refreshLayout();
            }
        }

        public void setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            updateFont();
        }

        public String getFontFamily() {
            return fontFamily;
        }

        public void setFontSize(int fontSize) {
            this.fontSize = fontSize;
            updateFont();
        }

        public Integer getFontSizeValue() {
            return fontSize;
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

        public int getBulletLineLength() {
            return bulletLineLength;
        }

        public void setBulletLineLength(int bulletLineLength) {
            this.bulletLineLength = Math.max(20, bulletLineLength);
            refreshLayout();
        }

        public int getFrameBodyHeight() {
            return frameBodyHeight;
        }

        public void setFrameBodyHeight(int frameBodyHeight) {
            this.frameBodyHeight = Math.max(20, frameBodyHeight);
            refreshLayout();
        }

        private void updateFont() {
            textField.setFont(new Font(fontFamily, fontStyle, fontSize));
            refreshLayout();
        }

        public void refreshLayout() {
            FontMetrics fm = textField.getFontMetrics(textField.getFont());
            String text = textField.getText();
            if (text == null) {
                text = "";
            }

            int textWidth = Math.max(20, fm.stringWidth(text + " "));
            int textHeight = fm.getHeight();
            int leftOffset = indentLevel * indentWidth;

            int width;
            int height;

            if (type == LineType.LABEL) {
                height = Math.max(34, textHeight + 16);

                int pillContentWidth =
                        labelLeftInnerPadding
                                + labelCircleDiameter
                                + labelCircleGap
                                + textWidth
                                + labelRightInnerPadding;

                int pillWidth = pillContentWidth;
                width = leftOffset + pillWidth + selectionExtraWidth;

                int pillX = leftOffset;
                int textX = pillX + labelLeftInnerPadding + labelCircleDiameter + labelCircleGap;
                int textY = (height - textHeight) / 2 - 1;

                textField.setBounds(textX, textY, textWidth + 4, textHeight + 4);

            } else if (type == LineType.HEADING2) {
                height = Math.max(28, textHeight + 8);
                width = leftOffset + headingLeftPadding + textWidth + 20 + selectionExtraWidth;

                int textX = leftOffset + headingLeftPadding;
                int textY = (height - textHeight) / 2 - 1;

                textField.setBounds(textX, textY, textWidth + 4, textHeight + 4);

            } else if (type == LineType.BULLET) {
                height = Math.max(28, textHeight + 8);

                int textX = leftOffset + 10 + bulletDiameter + bulletTextGap;
                int textY = (height - textHeight) / 2 - 1;

                width = textX + textWidth + bulletLineGap + bulletLineLength + 10 + selectionExtraWidth;

                textField.setBounds(textX, textY, textWidth + 4, textHeight + 4);

            } else {
                int titleHeight = Math.max(34, textHeight + 16);
                height = titleHeight + frameBodyHeight + frameBottomPadding;
                int innerWidth = frameSidePadding + textWidth + frameSidePadding;
                width = leftOffset + Math.max(innerWidth + 20, 260) + selectionExtraWidth;

                int textX = leftOffset + frameSidePadding + 6;
                int textY = frameTopPadding;

                textField.setBounds(textX, textY, textWidth + 4, textHeight + 4);
            }

            Dimension d = new Dimension(width, height);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            setSize(d);

            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int leftOffset = indentLevel * indentWidth;

            if (type == LineType.LABEL) {
                int pillWidth = Math.max(10, textField.getX() + textField.getWidth() + labelRightInnerPadding - leftOffset);
                Shape pill = new RoundRectangle2D.Double(leftOffset + 0.5, 0.5, pillWidth - 1, h - 1, h - 2, h - 2);

                g2.setColor(fillColor);
                g2.fill(pill);

                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(pill);

                int circleX = leftOffset + labelLeftInnerPadding;
                int circleY = (h - labelCircleDiameter) / 2;

                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new Ellipse2D.Double(circleX, circleY, labelCircleDiameter, labelCircleDiameter));

            } else if (type == LineType.BULLET) {
                int bulletX = leftOffset + 10;
                int bulletY = (h - bulletDiameter) / 2;

                g2.setColor(bulletColor);
                g2.fill(new Ellipse2D.Double(bulletX, bulletY, bulletDiameter, bulletDiameter));

                int lineStart = textField.getX() + textField.getWidth() + bulletLineGap;
                int lineEnd = lineStart + bulletLineLength;

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(lineColor);
                g2.drawLine(lineStart, h / 2, lineEnd, h / 2);

            } else if (type == LineType.FRAME) {
                FontMetrics fm = textField.getFontMetrics(textField.getFont());
                int textHeight = fm.getHeight();
                int titleHeight = Math.max(34, textHeight + 16);

                int frameX = leftOffset;
                int frameY = 0;
                int frameWidth = Math.max(260, textField.getX() + textField.getWidth() + frameSidePadding - leftOffset + 10);
                int frameHeight = h - 1;

                Shape titlePill = new RoundRectangle2D.Double(frameX + 0.5, frameY + 0.5, frameWidth - 1, titleHeight - 1, titleHeight - 2, titleHeight - 2);

                g2.setColor(fillColor.brighter());
                g2.fill(titlePill);

                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(titlePill);

                int bodyTop = titleHeight - 2;
                int bodyLeft = frameX + 8;
                int bodyRight = frameX + frameWidth - 8;
                int bodyBottom = frameHeight - 8;

                g2.setColor(new Color(217, 205, 185));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(bodyLeft, bodyTop, bodyLeft, bodyBottom);
                g2.drawLine(bodyRight, bodyTop, bodyRight, bodyBottom);
                g2.drawLine(bodyLeft, bodyBottom, bodyRight, bodyBottom);
            }

            if (selected) {
                g2.setColor(new Color(60, 120, 255, 120));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, Math.max(10, w - 3), Math.max(10, h - 3), 12, 12);
            }

            g2.dispose();
        }
    }
}