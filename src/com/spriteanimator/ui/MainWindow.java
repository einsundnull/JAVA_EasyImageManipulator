package com.spriteanimator.ui;

import com.spriteanimator.model.AppState;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window.
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────┐
 * │  ToolBar                                            │
 * ├──────────────┬───────────────────┬──────────────────┤
 * │  TilePanel   │  MaskPainter      │  AnimationPreview│
 * │  (sidebar)   │  (center canvas)  │  (right preview) │
 * └──────────────┴───────────────────┴──────────────────┘
 */
public class MainWindow extends JFrame {

    public MainWindow() {
        super("Sprite Animator – Tile Mask Painter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 750));

        // ── Dark theme ───────────────────────────────────────────────────────
        applyDarkTheme();

        // ── State ────────────────────────────────────────────────────────────
        AppState state = new AppState();

        // ── Panels ───────────────────────────────────────────────────────────
        MaskPainter     maskPainter = new MaskPainter(state);
        AnimationPreview preview    = new AnimationPreview(state);
        TilePanel       tilePanel   = new TilePanel(state);
        ToolBar         toolbar     = new ToolBar(state, maskPainter, preview);

        // ── Layout ───────────────────────────────────────────────────────────
        add(toolbar, BorderLayout.NORTH);

        // Center: scrollable mask painter
        JScrollPane painterScroll = new JScrollPane(maskPainter);
        painterScroll.getViewport().setBackground(new Color(40, 40, 40));
        painterScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

        // Right: preview + info
        JPanel rightPanel = buildRightPanel(preview, state);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                painterScroll, rightPanel);
        split.setResizeWeight(0.75);
        split.setDividerSize(4);
        split.setBackground(new Color(30, 30, 30));

        JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tilePanel, split);
        outerSplit.setDividerSize(4);
        outerSplit.setResizeWeight(0.0);
        outerSplit.setBackground(new Color(30, 30, 30));

        add(outerSplit, BorderLayout.CENTER);

        // ── Status bar ───────────────────────────────────────────────────────
        JLabel statusBar = new JLabel("  Linksklick = Malen  |  Rechtsklick = Löschen  |  Scroll = Zoom");
        statusBar.setForeground(Color.LIGHT_GRAY);
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        statusBar.setBackground(new Color(35, 35, 35));
        statusBar.setOpaque(true);
        add(statusBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Right panel ──────────────────────────────────────────────────────────

    private JPanel buildRightPanel(AnimationPreview preview, AppState state) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBackground(new Color(35, 35, 35));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        p.setPreferredSize(new Dimension(280, 400));

        // Preview label
        JLabel previewLabel = new JLabel("Live-Vorschau", SwingConstants.CENTER);
        previewLabel.setForeground(Color.LIGHT_GRAY);
        previewLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        p.add(previewLabel, BorderLayout.NORTH);

        // Preview itself (resizable)
        preview.setPreferredSize(new Dimension(256, 256));
        p.add(preview, BorderLayout.CENTER);

        // Tip text
        JTextArea tips = new JTextArea(
            "Tipps:\n" +
            "• Tile auswählen → Farbe auswählen\n" +
            "• Mit Linksklick Pixel bemalen\n" +
            "• Rechtsklick löscht Maske\n" +
            "• Scroll-Rad = Zoom\n" +
            "• Bewegungstyp:\n" +
            "  NONE = kein Effekt\n" +
            "  BOB = Auf/Ab-Wackeln\n" +
            "  SWING_L = linke Seite\n" +
            "  SWING_R = rechte Seite\n" +
            "• Intensität X/Y = Stärke\n" +
            "• Export → PNG + GIF"
        );
        tips.setEditable(false);
        tips.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tips.setBackground(new Color(45, 45, 48));
        tips.setForeground(new Color(180, 180, 180));
        tips.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        p.add(tips, BorderLayout.SOUTH);

        return p;
    }

    // ── Dark theme ───────────────────────────────────────────────────────────

    private void applyDarkTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        getContentPane().setBackground(new Color(40, 40, 40));

        // Override common UI defaults for a dark look
        UIManager.put("Panel.background",              new Color(40, 40, 40));
        UIManager.put("ScrollPane.background",         new Color(40, 40, 40));
        UIManager.put("Viewport.background",           new Color(40, 40, 40));
        UIManager.put("SplitPane.background",          new Color(30, 30, 30));
        UIManager.put("ToolBar.background",            new Color(45, 45, 48));
        UIManager.put("Label.foreground",              Color.LIGHT_GRAY);
        UIManager.put("Spinner.background",            new Color(60, 60, 60));
        UIManager.put("Spinner.foreground",            Color.WHITE);
        UIManager.put("TextField.background",          new Color(60, 60, 60));
        UIManager.put("TextField.foreground",          Color.WHITE);
        UIManager.put("TextField.caretForeground",     Color.WHITE);
        UIManager.put("ComboBox.background",           new Color(60, 60, 60));
        UIManager.put("ComboBox.foreground",           Color.WHITE);
        UIManager.put("Button.background",             new Color(60, 60, 80));
        UIManager.put("Button.foreground",             Color.WHITE);
        UIManager.put("Slider.background",             new Color(40, 40, 40));
        UIManager.put("CheckBox.background",           new Color(40, 40, 40));
        UIManager.put("CheckBox.foreground",           Color.WHITE);
        UIManager.put("TitledBorder.titleColor",       Color.LIGHT_GRAY);
        UIManager.put("OptionPane.background",         new Color(50, 50, 50));
        UIManager.put("OptionPane.messageForeground",  Color.WHITE);
    }
}
