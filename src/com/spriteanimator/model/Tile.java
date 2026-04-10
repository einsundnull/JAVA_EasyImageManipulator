package com.spriteanimator.model;

import java.awt.Color;

/**
 * Represents one animated tile/region of the sprite.
 *
 * Motion model:
 *   offset(phase) = sin(phase + startAngle) * intensity
 *
 * startAngle controls direction at frame 0:
 *   0        = starts moving RIGHT/DOWN
 *   PI       = starts moving LEFT/UP
 *   PI/2     = starts at positive peak
 *   3*PI/2   = starts at negative peak
 */
public class Tile {

    public enum MotionType {
        NONE,    // no movement
        BOB,     // vertical only (2x per cycle)
        SWING    // full X+Y swing with configurable start angle
    }

    public static final double DIR_RIGHT = 0;
    public static final double DIR_DOWN  = 0;
    public static final double DIR_LEFT  = Math.PI;
    public static final double DIR_UP    = Math.PI;

    private final int id;
    private String    name;
    private Color     maskColor;
    private MotionType motionType;
    private float     intensityX;
    private float     intensityY;
    private double    startAngle;   // phase offset in radians
    private boolean   visible;

    public Tile(int id, String name, Color maskColor, MotionType motionType,
                float intensityX, float intensityY, double startAngle) {
        this.id         = id;
        this.name       = name;
        this.maskColor  = maskColor;
        this.motionType = motionType;
        this.intensityX = intensityX;
        this.intensityY = intensityY;
        this.startAngle = startAngle;
        this.visible    = true;
    }

    public int        getId()                     { return id; }
    public String     getName()                   { return name; }
    public void       setName(String n)           { this.name = n; }
    public Color      getMaskColor()              { return maskColor; }
    public void       setMaskColor(Color c)       { this.maskColor = c; }
    public MotionType getMotionType()             { return motionType; }
    public void       setMotionType(MotionType m) { this.motionType = m; }
    public float      getIntensityX()             { return intensityX; }
    public void       setIntensityX(float v)      { this.intensityX = v; }
    public float      getIntensityY()             { return intensityY; }
    public void       setIntensityY(float v)      { this.intensityY = v; }
    public double     getStartAngle()             { return startAngle; }
    public void       setStartAngle(double a)     { this.startAngle = a; }
    public boolean    isVisible()                 { return visible; }
    public void       setVisible(boolean v)       { this.visible = v; }

    /**
     * Compute (dx, dy) offset at the given animation phase.
     * @param phase 0.0..2*PI
     */
    public float[] getOffset(double phase) {
        return switch (motionType) {
            case NONE  -> new float[]{ 0, 0 };
            case BOB   -> new float[]{ 0,
                (float)(Math.sin(phase * 2 + startAngle) * intensityY) };
            case SWING -> new float[]{
                (float)(Math.sin(phase + startAngle) * intensityX),
                (float)(Math.sin(phase + startAngle) * intensityY) };
        };
    }

    @Override public String toString() { return name; }
}
