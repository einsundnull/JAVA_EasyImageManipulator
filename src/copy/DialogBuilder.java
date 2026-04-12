package paint.copy;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Builder utility for creating dialogs with consistent styling.
 * Consolidates repetitive dialog creation patterns.
 */
public class DialogBuilder {

    /**
     * Create a base dialog window with title and dimensions.
     * Caller responsible for adding content and setting visible.
     */
    public static JDialog createBaseDialog(String title, int width, int height, Component parent) {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getContentPane().setBackground(AppColors.BG_PANEL);
        return dialog;
    }

    /**
     * Create a simple input dialog with a single text field.
     * @param title Dialog title
     * @param label Label for the input field
     * @param defaultValue Default value in text field
     * @param onOk Callback when OK is clicked (receives input value)
     * @param parent Parent component for positioning
     */
    public static JDialog createInputDialog(String title, String label, String defaultValue,
                                            java.util.function.Consumer<String> onOk,
                                            Component parent) {
        JDialog dialog = createBaseDialog(title, 280, 160, parent);
        JPanel content = new JPanel();
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        content.setBackground(AppColors.BG_PANEL);
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 24, 12, 24));

        JPanel fieldPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fieldPanel.setBackground(AppColors.BG_PANEL);
        JLabel lbl = new JLabel(label);
        lbl.setForeground(AppColors.TEXT);
        JTextField field = new JTextField(defaultValue, 6);
        field.setBackground(AppColors.BTN_BG);
        field.setForeground(AppColors.TEXT);
        field.setCaretColor(AppColors.TEXT);
        fieldPanel.add(lbl);
        fieldPanel.add(field);
        content.add(fieldPanel);
        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        okBtn.setForeground(Color.WHITE);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        okBtn.addActionListener(e -> {
            onOk.accept(field.getText().trim());
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    /**
     * Create an error dialog with a message.
     */
    public static JDialog createErrorDialog(String title, String message, Component parent) {
        JDialog dialog = createBaseDialog(title, 440, 215, parent);
        JPanel content = centeredColumnPanel(16, 24, 12);
        JLabel msgLabel = new JLabel(message);
        msgLabel.setForeground(AppColors.TEXT);
        content.add(msgLabel);
        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("OK", AppColors.DANGER, AppColors.DANGER_HOVER);
        okBtn.setForeground(Color.WHITE);
        okBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    /**
     * Create an info dialog with a message.
     */
    public static JDialog createInfoDialog(String title, String message, Component parent) {
        JDialog dialog = createBaseDialog(title, 400, 200, parent);
        JPanel content = centeredColumnPanel(16, 24, 12);
        JLabel msgLabel = new JLabel(message);
        msgLabel.setForeground(AppColors.TEXT);
        content.add(msgLabel);
        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        okBtn.setForeground(Color.WHITE);
        okBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    /**
     * Create a multi-field input dialog.
     * @param title Dialog title
     * @param fields List of (label, defaultValue) pairs
     * @param onOk Callback when OK is clicked (receives list of input values)
     * @param parent Parent component for positioning
     */
    public static JDialog createMultiFieldDialog(String title, List<java.util.AbstractMap.SimpleEntry<String, String>> fields,
                                                 java.util.function.Consumer<List<String>> onOk,
                                                 Component parent) {
        JDialog dialog = createBaseDialog(title, 300, 200 + fields.size() * 30, parent);
        JPanel content = centeredColumnPanel(16, 20, 12);

        JPanel grid = new JPanel(new GridLayout(fields.size(), 2, 6, 4));
        grid.setOpaque(false);
        List<JTextField> textFields = new ArrayList<>();

        for (var entry : fields) {
            JLabel lbl = new JLabel(entry.getKey());
            lbl.setForeground(AppColors.TEXT);
            JTextField field = new JTextField(entry.getValue(), 5);
            field.setBackground(AppColors.BTN_BG);
            field.setForeground(AppColors.TEXT);
            field.setCaretColor(AppColors.TEXT);
            grid.add(lbl);
            grid.add(field);
            textFields.add(field);
        }

        content.add(grid);
        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        okBtn.setForeground(Color.WHITE);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        okBtn.addActionListener(e -> {
            List<String> values = new ArrayList<>();
            for (JTextField tf : textFields) {
                values.add(tf.getText().trim());
            }
            onOk.accept(values);
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    /**
     * Create a dialog with checkbox options.
     * @param title Dialog title
     * @param label Main label
     * @param checkboxLabels List of checkbox labels
     * @param onOk Callback when OK is clicked (receives list of boolean values)
     * @param parent Parent component for positioning
     */
    public static JDialog createCheckboxDialog(String title, String label, List<String> checkboxLabels,
                                               java.util.function.Consumer<List<Boolean>> onOk,
                                               Component parent) {
        JDialog dialog = createBaseDialog(title, 300, 150 + checkboxLabels.size() * 25, parent);
        JPanel content = centeredColumnPanel(16, 20, 12);

        if (label != null && !label.isEmpty()) {
            JLabel titleLabel = new JLabel(label);
            titleLabel.setForeground(AppColors.TEXT);
            content.add(titleLabel);
        }

        List<JCheckBox> checkboxes = new ArrayList<>();
        for (String checkboxLabel : checkboxLabels) {
            JCheckBox cb = new JCheckBox(checkboxLabel, true);
            cb.setOpaque(false);
            cb.setForeground(AppColors.TEXT);
            content.add(cb);
            checkboxes.add(cb);
        }

        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton okBtn = UIComponentFactory.buildButton("OK", AppColors.ACCENT, AppColors.ACCENT_HOVER);
        okBtn.setForeground(Color.WHITE);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        okBtn.addActionListener(e -> {
            List<Boolean> values = new ArrayList<>();
            for (JCheckBox cb : checkboxes) {
                values.add(cb.isSelected());
            }
            onOk.accept(values);
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    /**
     * Create a confirmation dialog (Yes/No/Cancel).
     * @param title Dialog title
     * @param message Message to display
     * @param onYes Callback if Yes clicked
     * @param onNo Callback if No clicked (can be null)
     * @param parent Parent component for positioning
     */
    public static JDialog createConfirmDialog(String title, String message,
                                              Runnable onYes, Runnable onNo,
                                              Component parent) {
        JDialog dialog = createBaseDialog(title, 420, 310, parent);
        JPanel content = centeredColumnPanel(16, 24, 12);

        JLabel msgLabel = new JLabel(message);
        msgLabel.setForeground(AppColors.TEXT);
        content.add(msgLabel);
        content.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);
        JButton yesBtn = UIComponentFactory.buildButton("Ja", AppColors.SUCCESS, AppColors.SUCCESS_HOVER);
        JButton noBtn = UIComponentFactory.buildButton("Nein", AppColors.DANGER, AppColors.DANGER_HOVER);
        JButton cancelBtn = UIComponentFactory.buildButton("Abbrechen", AppColors.BTN_BG, AppColors.BTN_HOVER);
        yesBtn.setForeground(Color.WHITE);
        noBtn.setForeground(Color.WHITE);

        yesBtn.addActionListener(e -> {
            onYes.run();
            dialog.dispose();
        });
        if (onNo != null) {
            noBtn.addActionListener(e -> {
                onNo.run();
                dialog.dispose();
            });
        } else {
            noBtn.addActionListener(e -> dialog.dispose());
        }
        cancelBtn.addActionListener(e -> dialog.dispose());

        buttonPanel.add(yesBtn);
        buttonPanel.add(noBtn);
        buttonPanel.add(cancelBtn);
        content.add(buttonPanel);

        dialog.add(content);
        return dialog;
    }

    // ─────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Create a centered column panel with vertical box layout.
     */
    private static JPanel centeredColumnPanel(int top, int left, int bottom) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.BG_PANEL);
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(top, left, bottom, left));
        return panel;
    }
}
