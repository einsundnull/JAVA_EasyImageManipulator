package com.spriteanimator.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Central application state — single source of truth.
 */
public class AppState {

    // ── Tool mode ─────────────────────────────────────────────────────────────
    public enum Tool { BRUSH, ERASER }
    private Tool activeTool = Tool.BRUSH;

    // ── Sprite / Mask ─────────────────────────────────────────────────────────
    private BufferedImage spriteImage;
    private BufferedImage maskImage;

    // ── Tiles ─────────────────────────────────────────────────────────────────
    private final List<Tile> tiles = new ArrayList<>();
    private Tile activeTile;

    // ── Brush ─────────────────────────────────────────────────────────────────
    private int brushSize = 8;

    // ── Animation ─────────────────────────────────────────────────────────────
    private int    frameCount  = 8;
    private int    frameWidth  = 128;
    private int    frameHeight = 128;
    private int    frameDelay  = 110;

    // ── Export ────────────────────────────────────────────────────────────────
    private String exportName = "sprite";   // filename prefix

    // ── Listeners ─────────────────────────────────────────────────────────────
    public interface StateListener { void onStateChanged(); }
    private final List<StateListener> listeners = new ArrayList<>();
    public void addListener(StateListener l) { listeners.add(l); }
    public void fireChanged()                { listeners.forEach(StateListener::onStateChanged); }

    // ── Tool ──────────────────────────────────────────────────────────────────
    public Tool getActiveTool()              { return activeTool; }
    public void setActiveTool(Tool t)        { this.activeTool = t; fireChanged(); }

    // ── Sprite ────────────────────────────────────────────────────────────────
    public BufferedImage getSpriteImage()    { return spriteImage; }

    public void setSpriteImage(BufferedImage img) {
        this.spriteImage = img;
        if (img != null) {
            maskImage = new BufferedImage(img.getWidth(), img.getHeight(),
                                          BufferedImage.TYPE_INT_ARGB);
        }
        fireChanged();
    }

    public BufferedImage getMaskImage()      { return maskImage; }

    // ── Tiles ─────────────────────────────────────────────────────────────────
    public List<Tile> getTiles()             { return tiles; }

    public void addTile(Tile t) {
        tiles.add(t);
        if (activeTile == null) activeTile = t;
        fireChanged();
    }

    public void removeTile(Tile t) {
        tiles.remove(t);
        if (activeTile == t) activeTile = tiles.isEmpty() ? null : tiles.get(0);
        fireChanged();
    }

    public Tile getActiveTile()              { return activeTile; }
    public void setActiveTile(Tile t)        { this.activeTile = t; fireChanged(); }

    // ── Brush ─────────────────────────────────────────────────────────────────
    public int  getBrushSize()               { return brushSize; }
    public void setBrushSize(int s)          { this.brushSize = s; }

    // ── Animation ─────────────────────────────────────────────────────────────
    public int  getFrameCount()              { return frameCount; }
    public void setFrameCount(int n)         { this.frameCount = n; fireChanged(); }
    public int  getFrameWidth()              { return frameWidth; }
    public void setFrameWidth(int w)         { this.frameWidth = w; fireChanged(); }
    public int  getFrameHeight()             { return frameHeight; }
    public void setFrameHeight(int h)        { this.frameHeight = h; fireChanged(); }
    public int  getFrameDelay()              { return frameDelay; }
    public void setFrameDelay(int d)         { this.frameDelay = d; fireChanged(); }

    // ── Export ────────────────────────────────────────────────────────────────
    public String getExportName()            { return exportName; }
    public void   setExportName(String n)    { this.exportName = n.isBlank() ? "sprite" : n.trim(); }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public Tile getTileAtMaskPixel(int x, int y) {
        if (maskImage == null) return null;
        if (x < 0 || y < 0 || x >= maskImage.getWidth() || y >= maskImage.getHeight()) return null;
        int rgb = maskImage.getRGB(x, y);
        if (((rgb >> 24) & 0xFF) < 128) return null;
        Color c = new Color(rgb, true);
        for (Tile t : tiles) {
            Color tc = t.getMaskColor();
            if (Math.abs(tc.getRed()   - c.getRed())   < 30 &&
                Math.abs(tc.getGreen() - c.getGreen()) < 30 &&
                Math.abs(tc.getBlue()  - c.getBlue())  < 30) {
                return t;
            }
        }
        return null;
    }

    public static final Color[] DEFAULT_TILE_COLORS = {
        new Color(0xFF4444),
        new Color(0x44AA44),
        new Color(0x4488FF),
        new Color(0xFFAA00),
        new Color(0xAA44FF),
        new Color(0xFF44AA),
        new Color(0x00CCCC),
        new Color(0xFFFF44),
    };
}
