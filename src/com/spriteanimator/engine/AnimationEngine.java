package com.spriteanimator.engine;

import com.spriteanimator.model.AppState;
import com.spriteanimator.model.Tile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Core animation engine.
 * Given an AppState (sprite + mask + tile definitions), it computes
 * each animation frame as a BufferedImage.
 *
 * Algorithm per frame:
 *  1. For each pixel in the sprite, look up which Tile owns it via the mask.
 *  2. Apply that tile's (dx, dy) offset for the current animation phase.
 *  3. After placing all pixels, fill any transparent gaps by interpolating
 *     the nearest colored neighbors vertically (stretch-fill).
 */
public class AnimationEngine {

    private final AppState state;

    public AnimationEngine(AppState state) {
        this.state = state;
    }

    /**
     * Build all animation frames.
     * @return array of ARGB BufferedImages, one per frame
     */
    public BufferedImage[] buildFrames() {
        int n         = state.getFrameCount();
        int fw        = state.getFrameWidth();
        int fh        = state.getFrameHeight();
        BufferedImage sprite = state.getSpriteImage();
        BufferedImage mask   = state.getMaskImage();

        if (sprite == null) return new BufferedImage[0];

        BufferedImage[] frames = new BufferedImage[n];

        for (int fi = 0; fi < n; fi++) {
            double phase = (double) fi / n * 2 * Math.PI;
            frames[fi] = buildSingleFrame(sprite, mask, phase, fw, fh);
        }
        return frames;
    }

    // ── Single frame ─────────────────────────────────────────────────────────

    private BufferedImage buildSingleFrame(BufferedImage sprite, BufferedImage mask,
                                           double phase, int fw, int fh) {
        int sw = sprite.getWidth();
        int sh = sprite.getHeight();

        // canvas: ARGB, transparent
        int[] canvas   = new int[fw * fh];   // ARGB packed
        boolean[] filled = new boolean[fw * fh];

        // Center offset so sprite is centered in frame
        int ox = (fw - sw) / 2;
        int oy = (fh - sh) / 2;

        // ── Pre-compute tile offset for this phase ───────────────────────────
        Map<Integer, float[]> tileOffsets = new HashMap<>();
        for (Tile t : state.getTiles()) {
            tileOffsets.put(t.getId(), t.getOffset(phase));
        }

        // ── Pass 1: place each sprite pixel on the canvas ────────────────────
        for (int sy = 0; sy < sh; sy++) {
            for (int sx = 0; sx < sw; sx++) {
                int spriteRgb = sprite.getRGB(sx, sy);
                int alpha = (spriteRgb >> 24) & 0xFF;
                if (alpha < 10) continue;  // transparent pixel – skip

                // find tile for this pixel
                Tile tile = (mask != null) ? state.getTileAtMaskPixel(sx, sy) : null;

                int dx = 0, dy = 0;
                if (tile != null && tile.isVisible()) {
                    float[] off = tileOffsets.get(tile.getId());
                    if (off != null) {
                        dx = Math.round(off[0]);
                        dy = Math.round(off[1]);
                    }
                }

                int cx = ox + sx + dx;
                int cy = oy + sy + dy;
                if (cx < 0 || cy < 0 || cx >= fw || cy >= fh) continue;

                int idx = cy * fw + cx;
                canvas[idx] = spriteRgb;
                filled[idx] = true;
            }
        }

        // ── Pass 2: gap filling ──────────────────────────────────────────────
        // For each column, scan top-to-bottom.
        // When we find a transparent gap between two filled pixels,
        // linearly interpolate the ARGB values to fill it.
        fillGaps(canvas, filled, fw, fh);

        // ── Compose into BufferedImage ────────────────────────────────────────
        BufferedImage out = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, fw, fh, canvas, 0, fw);
        return out;
    }

    // ── Gap filling ──────────────────────────────────────────────────────────

    private void fillGaps(int[] canvas, boolean[] filled, int fw, int fh) {
        for (int x = 0; x < fw; x++) {
            int gapStart = -1;
            int topColor = 0;

            for (int y = 0; y < fh; y++) {
                int idx = y * fw + x;

                if (filled[idx]) {
                    if (gapStart >= 0) {
                        // We found the bottom of a gap — fill it
                        int botColor = canvas[idx];
                        fillColumnGap(canvas, filled, x, fw, gapStart, y, topColor, botColor);
                        gapStart = -1;
                    }
                    topColor = canvas[idx];
                } else {
                    // transparent pixel
                    if (gapStart < 0 && topColor != 0) {
                        // start of a gap (only if we already saw a filled pixel above)
                        gapStart = y;
                    }
                }
            }
        }
    }

    private void fillColumnGap(int[] canvas, boolean[] filled,
                                int x, int fw,
                                int yStart, int yEnd,
                                int topArgb, int botArgb) {
        int gapSize = yEnd - yStart;
        if (gapSize <= 0 || gapSize > 12) return; // only fill small gaps (≤12px)

        float tA = ((topArgb >> 24) & 0xFF);
        float tR = ((topArgb >> 16) & 0xFF);
        float tG = ((topArgb >>  8) & 0xFF);
        float tB = ( topArgb        & 0xFF);

        float bA = ((botArgb >> 24) & 0xFF);
        float bR = ((botArgb >> 16) & 0xFF);
        float bG = ((botArgb >>  8) & 0xFF);
        float bB = ( botArgb        & 0xFF);

        for (int y = yStart; y < yEnd; y++) {
            float t = (float)(y - yStart) / gapSize;
            int a = Math.round(tA + (bA - tA) * t);
            int r = Math.round(tR + (bR - tR) * t);
            int g = Math.round(tG + (bG - tG) * t);
            int b = Math.round(tB + (bB - tB) * t);
            int idx = y * fw + x;
            canvas[idx] = (a << 24) | (r << 16) | (g << 8) | b;
            filled[idx] = true;
        }
    }
}
