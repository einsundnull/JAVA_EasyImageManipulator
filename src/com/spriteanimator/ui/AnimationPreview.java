package com.spriteanimator.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.spriteanimator.engine.AnimationEngine;
import com.spriteanimator.model.AppState;

/**
 * Live animation preview. Rebuilds frames whenever state changes
 * and plays them back using a Swing Timer.
 */
public class AnimationPreview extends JPanel {

    private final AppState state;
    private final AnimationEngine engine;

    private BufferedImage[] frames;
    private int currentFrame = 0;
    private Timer playTimer;
    private boolean playing = true;

    // Preview background color
    private static final Color BG = new Color(30, 30, 30);

    public AnimationPreview(AppState state) {
        this.state  = state;
        this.engine = new AnimationEngine(state);

        setBackground(BG);
        setPreferredSize(new Dimension(256, 256));

        // Rebuild frames whenever state changes
        state.addListener(this::rebuildFrames);

        // Playback timer
        playTimer = new Timer(state.getFrameDelay(), e -> {
            if (frames != null && frames.length > 0) {
                currentFrame = (currentFrame + 1) % frames.length;
                repaint();
            }
        });
        playTimer.start();
    }

    public void rebuildFrames() {
        playTimer.setDelay(state.getFrameDelay());
        SwingUtilities.invokeLater(() -> {
            frames = engine.buildFrames();
            currentFrame = 0;
            repaint();
        });
    }

    public void setPlaying(boolean p) {
        playing = p;
        if (p) playTimer.start(); else playTimer.stop();
    }

    public boolean isPlaying() { return playing; }

    public BufferedImage[] getFrames() { return frames; }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        if (frames == null || frames.length == 0) {
            g.setColor(new Color(100, 100, 100));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Vorschau nicht verfügbar", 10, getHeight() / 2);
            return;
        }

        BufferedImage frame = frames[currentFrame];
        int fw = frame.getWidth();
        int fh = frame.getHeight();

        // Scale to fit preview panel, keeping pixel-perfect (nearest neighbor)
        int scale = Math.min(getWidth() / fw, getHeight() / fh);
        scale = Math.max(1, scale);

        int dw = fw * scale;
        int dh = fh * scale;
        int dx = (getWidth()  - dw) / 2;
        int dy = (getHeight() - dh) / 2;

        // Checkerboard
        drawChecker(g, dx, dy, dw, dh, 8);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, dx, dy, dw, dh, null);

        // Frame counter
        g.setColor(new Color(255, 255, 255, 180));
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.drawString("Frame " + (currentFrame + 1) + " / " + frames.length, dx + 4, dy + 14);
    }

    private void drawChecker(Graphics2D g, int x, int y, int w, int h, int tile) {
        for (int ty = 0; ty < h; ty += tile) {
            for (int tx = 0; tx < w; tx += tile) {
                boolean light = ((tx / tile + ty / tile) % 2 == 0);
                g.setColor(light ? new Color(80, 80, 80) : new Color(50, 50, 50));
                g.fillRect(x + tx, y + ty, Math.min(tile, w - tx), Math.min(tile, h - ty));
            }
        }
    }
}
