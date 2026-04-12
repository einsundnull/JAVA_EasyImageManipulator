package paint.copy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Dialog manager for SelectiveAlphaEditor.
 * Centralizes all dialog creation and handling.
 */
public class EditorDialogs {

    private final SelectiveAlphaEditor editor;

    public EditorDialogs(SelectiveAlphaEditor editor) {
        this.editor = editor;
    }

    // Simple dialogs
    public void showErrorDialog(String title, String message) {
        JDialog dialog = UIComponentFactory.createBaseDialog(editor, title, 440, 215);
        JPanel content = UIComponentFactory.centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = UIComponentFactory.htmlLabel(message.replace("\n", "<br>"), AppColors.TEXT, 12);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.DANGER, AppColors.DANGER_HOVER);
        ok.setForeground(Color.WHITE);
        ok.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        ok.addActionListener(e -> dialog.dispose());
        content.add(UIComponentFactory.styledLabel("✕", 26, AppColors.DANGER, Font.BOLD));
        content.add(Box.createVerticalStrut(6));
        content.add(UIComponentFactory.styledLabel(title, 13, AppColors.DANGER, Font.BOLD));
        content.add(Box.createVerticalStrut(8));
        content.add(msgLbl);
        content.add(Box.createVerticalStrut(16));
        content.add(ok);
        dialog.add(content);
        dialog.setVisible(true);
    }

    public void showInfoDialog(String title, String message) {
        JDialog dialog = UIComponentFactory.createBaseDialog(editor, title, 400, 200);
        JPanel content = UIComponentFactory.centeredColumnPanel(20, 28, 16);
        JLabel msgLbl = UIComponentFactory.htmlLabel(message.replace("\n", "<br>"), AppColors.TEXT, 13);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        ok.setForeground(Color.WHITE);
        ok.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        ok.addActionListener(e -> dialog.dispose());
        content.add(UIComponentFactory.styledLabel("ℹ", 26, AppColors.ACCENT, Font.PLAIN));
        content.add(Box.createVerticalStrut(8));
        content.add(msgLbl);
        content.add(Box.createVerticalStrut(16));
        content.add(ok);
        dialog.add(content);
        dialog.setVisible(true);
    }

    // Transformation dialogs
    public void doRotate() {
        if (editor.getWorkingImage() == null) return;
        JTextField angleField = new JTextField("90", 6);
        angleField.setBackground(AppColors.BTN_BG);
        angleField.setForeground(AppColors.TEXT);
        angleField.setCaretColor(AppColors.TEXT);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBackground(AppColors.BG_PANEL);
        JLabel lbl = new JLabel("Winkel (°):");
        lbl.setForeground(AppColors.TEXT);
        panel.add(lbl);
        panel.add(angleField);

        JDialog dialog = UIComponentFactory.createBaseDialog(editor, "Drehen", 280, 160);
        JPanel content = UIComponentFactory.centeredColumnPanel(16, 24, 12);
        content.add(panel);
        content.add(Box.createVerticalStrut(12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                double deg = Double.parseDouble(angleField.getText().trim());
                editor.pushUndo();
                editor.setWorkingImage(PaintEngine.rotate(editor.getWorkingImage(), deg));
                editor.markDirty();
                editor.getCanvasWrapper().revalidate();
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte eine Zahl eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok);
        row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    public void doScale() {
        if (editor.getWorkingImage() == null) return;
        int origW = editor.getWorkingImage().getWidth();
        int origH = editor.getWorkingImage().getHeight();

        JTextField wField = new JTextField(String.valueOf(origW), 5);
        JTextField hField = new JTextField(String.valueOf(origH), 5);
        JTextField pctField = new JTextField("100", 5);
        JCheckBox lockAR = new JCheckBox("Proportional", true);

        for (JTextField f : new JTextField[]{wField, hField, pctField}) {
            f.setBackground(AppColors.BTN_BG);
            f.setForeground(AppColors.TEXT);
            f.setCaretColor(AppColors.TEXT);
        }
        lockAR.setOpaque(false);
        lockAR.setForeground(AppColors.TEXT);

        pctField.addActionListener(ev -> {
            try {
                double pct = Double.parseDouble(pctField.getText().trim()) / 100.0;
                wField.setText(String.valueOf((int) (origW * pct)));
                hField.setText(String.valueOf((int) (origH * pct)));
            } catch (NumberFormatException ignored) {}
        });
        wField.addActionListener(ev -> {
            if (lockAR.isSelected()) {
                try {
                    int nw = Integer.parseInt(wField.getText().trim());
                    hField.setText(String.valueOf((int) (origH * ((double) nw / origW))));
                    pctField.setText(String.format("%.1f", 100.0 * nw / origW));
                } catch (NumberFormatException ignored) {}
            }
        });

        JPanel grid = new JPanel(new GridLayout(4, 2, 6, 4));
        grid.setOpaque(false);
        grid.removeAll();
        String[] labels = {"Breite (px):", "Höhe (px):", "Prozent:", ""};
        JComponent[] fields = {wField, hField, pctField, lockAR};
        for (int i = 0; i < labels.length; i++) {
            JLabel l = new JLabel(labels[i]);
            l.setForeground(AppColors.TEXT);
            grid.add(l);
            grid.add(fields[i]);
        }

        JDialog dialog = UIComponentFactory.createBaseDialog(editor, "Skalieren", 300, 230);
        JPanel content = UIComponentFactory.centeredColumnPanel(16, 20, 12);
        content.add(grid);
        content.add(Box.createVerticalStrut(12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Integer.parseInt(wField.getText().trim());
                int nh = Integer.parseInt(hField.getText().trim());
                editor.pushUndo();
                editor.setWorkingImage(PaintEngine.scale(editor.getWorkingImage(), nw, nh));
                editor.markDirty();
                editor.getCanvasWrapper().revalidate();
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte ganzzahlige Pixelwerte eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok);
        row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    public void doNewBitmap() {
        JTextField wField = new JTextField("1024", 5);
        JTextField hField = new JTextField("1024", 5);
        for (JTextField f : new JTextField[]{wField, hField}) {
            f.setBackground(AppColors.BTN_BG);
            f.setForeground(AppColors.TEXT);
            f.setCaretColor(AppColors.TEXT);
        }
        JPanel grid = new JPanel(new GridLayout(2, 2, 6, 4));
        grid.setOpaque(false);
        JLabel wl = new JLabel("Breite (px):");
        wl.setForeground(AppColors.TEXT);
        JLabel hl = new JLabel("Höhe  (px):");
        hl.setForeground(AppColors.TEXT);
        grid.add(wl);
        grid.add(wField);
        grid.add(hl);
        grid.add(hField);

        JDialog dialog = UIComponentFactory.createBaseDialog(editor, "Neue Bitmap", 300, 200);
        JPanel content = UIComponentFactory.centeredColumnPanel(16, 20, 12);
        content.add(grid);
        content.add(Box.createVerticalStrut(14));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JButton ok = UIComponentFactory.buildButton("Erstellen", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        JButton can = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        ok.setForeground(Color.WHITE);
        ok.addActionListener(e -> {
            try {
                int nw = Math.max(1, Integer.parseInt(wField.getText().trim()));
                int nh = Math.max(1, Integer.parseInt(hField.getText().trim()));
                if (editor.getSourceFile() != null) editor.saveCurrentState();
                editor.setWorkingImage(new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB));
                editor.setOriginalImage(editor.deepCopy(editor.getWorkingImage()));
                editor.setSourceFile(null);
                editor.clearUndoRedo();
                editor.swapToImageView();
                SwingUtilities.invokeLater(() -> {
                    editor.fitToViewport();
                    editor.centerCanvas();
                });
                editor.updateTitle();
                editor.updateStatus();
                editor.setBottomButtonsEnabled(true);
            } catch (NumberFormatException ex) {
                showErrorDialog("Ungültige Eingabe", "Bitte ganzzahlige Pixelwerte eingeben.");
            }
            dialog.dispose();
        });
        can.addActionListener(e -> dialog.dispose());
        row.add(ok);
        row.add(can);
        content.add(row);
        dialog.add(content);
        dialog.setVisible(true);
    }

    public void showCanvasBgDialog() {
        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                int cell = 16;
                for (int r = 0; r < getHeight(); r += cell)
                    for (int c = 0; c < getWidth(); c += cell) {
                        boolean even = ((r / cell) + (c / cell)) % 2 == 0;
                        g.setColor(even ? editor.getCanvasBg1() : editor.getCanvasBg2());
                        g.fillRect(c, r, Math.min(cell, getWidth() - c), Math.min(cell, getHeight() - r));
                    }
            }
        };
        preview.setPreferredSize(new Dimension(120, 60));

        JButton btn1 = UIComponentFactory.buildButton("Farbe 1", AppColors.BTN_BG, AppColors.BTN_HOVER);
        JButton btn2 = UIComponentFactory.buildButton("Farbe 2", AppColors.BTN_BG, AppColors.BTN_HOVER);
        btn1.addActionListener(e -> {
            Color c = JColorChooser.showDialog(editor, "Hintergrundfarbe 1", editor.getCanvasBg1());
            if (c != null) {
                editor.setCanvasBg1(c);
                preview.repaint();
                editor.getCanvasPanel().repaint();
            }
        });
        btn2.addActionListener(e -> {
            Color c = JColorChooser.showDialog(editor, "Hintergrundfarbe 2", editor.getCanvasBg2());
            if (c != null) {
                editor.setCanvasBg2(c);
                preview.repaint();
                editor.getCanvasPanel().repaint();
            }
        });

        JDialog dialog = UIComponentFactory.createBaseDialog(editor, "Canvas-Hintergrund", 320, 240);
        JPanel content = UIComponentFactory.centeredColumnPanel(16, 20, 12);
        content.add(preview);
        content.add(Box.createVerticalStrut(12));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.add(btn1);
        row.add(btn2);
        content.add(row);
        content.add(Box.createVerticalStrut(12));
        JButton closeBtn = UIComponentFactory.buildButton("Schließen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        closeBtn.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        closeBtn.addActionListener(e -> dialog.dispose());
        content.add(closeBtn);
        dialog.add(content);
        dialog.setVisible(true);
    }

    public void showZoomInput() {
        JTextField tf = new JTextField(String.valueOf(Math.round(editor.getZoom() * 100)), 5);
        tf.setBackground(AppColors.BTN_BG);
        tf.setForeground(AppColors.TEXT);
        tf.setCaretColor(AppColors.TEXT);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tf.setHorizontalAlignment(JTextField.CENTER);
        tf.setBorder(BorderFactory.createLineBorder(AppColors.ACCENT));

        JDialog popup = new JDialog(editor, false);
        popup.setUndecorated(true);
        popup.setSize(80, 28);
        popup.setLocationRelativeTo(editor.getZoomLabel());
        popup.add(tf);

        tf.selectAll();
        tf.addActionListener(ev -> {
            try {
                double pct = Double.parseDouble(tf.getText().trim().replace("%", ""));
                editor.setZoom(pct / 100.0, null);
            } catch (NumberFormatException ignored) {}
            popup.dispose();
        });
        tf.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) popup.dispose();
            }
        });
        popup.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {}

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                popup.dispose();
            }
        });
        popup.setVisible(true);
        tf.requestFocusInWindow();
    }
}
