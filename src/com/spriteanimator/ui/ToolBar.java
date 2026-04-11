package com.spriteanimator.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;

import com.spriteanimator.export.ExportEngine;
import com.spriteanimator.model.AppState;

/**
 * Top toolbar:
 *  - Open sprite / Image Drop hint
 *  - Brush / Eraser tool toggle
 *  - Brush size, Zoom, Mask opacity
 *  - Frame count, Delay, Width, Height
 *  - Export name field
 *  - Play/Pause
 *  - Export (sheet + gif + individual frames)
 */
public class ToolBar extends JToolBar {

    private final AppState        state;
    private final MaskPainter     painter;
    private final AnimationPreview preview;

    private final JSpinner brushSpinner;
    private final JSpinner zoomSpinner;
    private final JSlider  opacitySlider;
    private final JSpinner framesSpinner;
    private final JSpinner delaySpinner;
    private final JSpinner fwSpinner;
    private final JSpinner fhSpinner;
    private final JTextField exportNameField;

    private final JToggleButton brushBtn;
    private final JToggleButton eraserBtn;

    public ToolBar(AppState state, MaskPainter painter, AnimationPreview preview) {
        this.state   = state;
        this.painter = painter;
        this.preview = preview;

        setFloatable(false);
        setBackground(new Color(45, 45, 48));

        // ── Open ─────────────────────────────────────────────────────────────
        JButton openBtn = actionButton("📂 Öffnen", new Color(60, 120, 180));
        openBtn.addActionListener(e -> openSprite());
        add(openBtn);
        addSeparator();

        // ── Tool: Brush / Eraser ──────────────────────────────────────────────
        ButtonGroup toolGroup = new ButtonGroup();
        brushBtn  = toggleButton("🖌 Pinsel",  new Color(50, 100, 50),  true);
        eraserBtn = toggleButton("✕ Radierer", new Color(120, 60, 60),  false);
        toolGroup.add(brushBtn);
        toolGroup.add(eraserBtn);
        brushBtn.addActionListener(e  -> state.setActiveTool(AppState.Tool.BRUSH));
        eraserBtn.addActionListener(e -> state.setActiveTool(AppState.Tool.ERASER));
        add(brushBtn);
        add(eraserBtn);

        // Clear mask
        JButton clearBtn = actionButton("🗑 Maske löschen", new Color(140, 80, 40));
        clearBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(null,
                "Gesamte Maske löschen?", "Bestätigung", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) painter.clearMask();
        });
        add(clearBtn);
        addSeparator();

        // ── Brush size ────────────────────────────────────────────────────────
        addLabel("Pinsel:");
        brushSpinner = spinner(state.getBrushSize(), 1, 64, 1, 50);
        brushSpinner.addChangeListener(e -> state.setBrushSize((int) brushSpinner.getValue()));
        add(brushSpinner);

        // ── Zoom ─────────────────────────────────────────────────────────────
        addLabel("Zoom:");
        zoomSpinner = spinner(painter.getZoom(), 1, 16, 1, 50);
        zoomSpinner.addChangeListener(e -> painter.setZoom((int) zoomSpinner.getValue()));
        add(zoomSpinner);

        // ── Mask opacity ──────────────────────────────────────────────────────
        addLabel("Maske:");
        opacitySlider = new JSlider(0, 100, (int)(painter.getMaskOpacity() * 100));
        opacitySlider.setPreferredSize(new Dimension(70, 24));
        opacitySlider.setMaximumSize(new Dimension(70, 24));
        opacitySlider.setOpaque(false);
        opacitySlider.addChangeListener(e ->
            painter.setMaskOpacity(opacitySlider.getValue() / 100f));
        add(opacitySlider);
        addSeparator();

        // ── Animation settings ────────────────────────────────────────────────
        addLabel("Frames:");
        framesSpinner = spinner(state.getFrameCount(), 2, 64, 1, 50);
        framesSpinner.addChangeListener(e -> {
            state.setFrameCount((int) framesSpinner.getValue());
            preview.rebuildFrames();
        });
        add(framesSpinner);

        addLabel("ms:");
        delaySpinner = spinner(state.getFrameDelay(), 16, 1000, 10, 60);
        delaySpinner.addChangeListener(e -> {
            state.setFrameDelay((int) delaySpinner.getValue());
            preview.rebuildFrames();
        });
        add(delaySpinner);

        addLabel("W:");
        fwSpinner = spinner(state.getFrameWidth(), 16, 512, 16, 55);
        fwSpinner.addChangeListener(e -> {
            state.setFrameWidth((int) fwSpinner.getValue());
            preview.rebuildFrames();
        });
        add(fwSpinner);

        addLabel("H:");
        fhSpinner = spinner(state.getFrameHeight(), 16, 512, 16, 55);
        fhSpinner.addChangeListener(e -> {
            state.setFrameHeight((int) fhSpinner.getValue());
            preview.rebuildFrames();
        });
        add(fhSpinner);
        addSeparator();

        // ── Export name ───────────────────────────────────────────────────────
        addLabel("Name:");
        exportNameField = new JTextField(state.getExportName(), 8);
        exportNameField.setMaximumSize(new Dimension(90, 26));
        exportNameField.setBackground(new Color(60, 60, 60));
        exportNameField.setForeground(Color.WHITE);
        exportNameField.setCaretColor(Color.WHITE);
        exportNameField.addActionListener(e -> state.setExportName(exportNameField.getText()));
        exportNameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                state.setExportName(exportNameField.getText());
            }
        });
        add(exportNameField);
        addSeparator();

        // ── Play / Pause ──────────────────────────────────────────────────────
        JToggleButton playBtn = new JToggleButton("▶ Play", true);
        playBtn.setForeground(Color.WHITE);
        playBtn.setBackground(new Color(40, 100, 40));
        playBtn.setFocusPainted(false);
        playBtn.addActionListener(e -> {
            preview.setPlaying(playBtn.isSelected());
            playBtn.setText(playBtn.isSelected() ? "▶ Play" : "⏸ Pause");
        });
        add(playBtn);
        addSeparator();

        // ── Export ────────────────────────────────────────────────────────────
        JButton exportBtn = actionButton("💾 Exportieren", new Color(40, 100, 160));
        exportBtn.addActionListener(e -> doExport());
        add(exportBtn);

        // sync tool state
        state.addListener(() -> {
            brushBtn.setSelected( state.getActiveTool() == AppState.Tool.BRUSH);
            eraserBtn.setSelected(state.getActiveTool() == AppState.Tool.ERASER);
        });
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void openSprite() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Sprite-Bild öffnen");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Bilder (PNG, GIF, JPG)", "png", "gif", "jpg", "jpeg"));
        if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        loadImageFile(fc.getSelectedFile());
    }

    private void loadImageFile(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new Exception("Unbekanntes Bildformat.");
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(),
                                                   BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            state.setSpriteImage(argb);
            // Auto-set export name from filename
            String fname = f.getName().replaceFirst("[.][^.]+$", "");
            exportNameField.setText(fname);
            state.setExportName(fname);
            preview.rebuildFrames();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Fehler beim Laden:\n" + ex.getMessage(),
                "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doExport() {
        BufferedImage[] frames = preview.getFrames();
        if (frames == null || frames.length == 0) {
            JOptionPane.showMessageDialog(null,
                "Keine Frames. Bitte zuerst Sprite laden.",
                "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export-Ordner wählen");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;

        File dir = fc.getSelectedFile();
        String name = state.getExportName();
        try {
            ExportEngine.exportSpriteSheet(frames, new File(dir, name + "_sheet.png"));
            ExportEngine.exportGif(frames, state.getFrameDelay(), new File(dir, name + ".gif"));
            ExportEngine.exportFrames(frames, dir, name);

            JOptionPane.showMessageDialog(null,
                "Export erfolgreich nach:\n" + dir.getAbsolutePath() + "\n\n" +
                "  • " + name + "_sheet.png\n" +
                "  • " + name + ".gif\n" +
                "  • " + name + "_frame_00..png",
                "Export abgeschlossen", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Export fehlgeschlagen:\n" + ex.getMessage(),
                "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JButton actionButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setBorderPainted(false);
        return b;
    }

    private JToggleButton toggleButton(String text, Color bg, boolean selected) {
        JToggleButton b = new JToggleButton(text, selected);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        return b;
    }

    private void addLabel(String text) {
        JLabel l = new JLabel(" " + text);
        l.setForeground(Color.LIGHT_GRAY);
        add(l);
    }

    private JSpinner spinner(int val, int min, int max, int step, int width) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setMaximumSize(new Dimension(width, 26));
        s.setPreferredSize(new Dimension(width, 26));
        return s;
    }
}
