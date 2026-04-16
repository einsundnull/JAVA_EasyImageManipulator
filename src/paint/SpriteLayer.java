package paint;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Ein ImageLayer der einen GameII-Sprite repräsentiert.
 *
 * TT kann Position und Größe bearbeiten.  Alle anderen Sprite-Felder
 * (Animationen, Physik, Links, KeyArea-Referenzen …) werden als
 * {@code rawLines} aufbewahrt und beim Speichern unverändert zurückgeschrieben.
 *
 * Koordinaten-Konvention (GameII ↔ TT):
 *   GameII: Ankerpunkt = Unterkante-Mitte des Frames  (x%, y%)
 *   TT:     x/y = obere-linke Ecke des Frames  (Pixel)
 *
 *   frameW  = DEFAULT_FRAME_SIZE * scaleX / 100   (DEFAULT_FRAME_SIZE = 100)
 *           = scaleX  [Pixel]
 *   frameH  = scaleY  [Pixel]
 *
 *   TT→GameII:
 *     anchorX = layerX + layerW / 2
 *     anchorY = layerY + layerH          (Unterkante)
 *     xPct    = anchorX / canvasW * 100
 *     yPct    = anchorY / canvasH * 100
 *     scaleX  = layerW
 *     scaleY  = layerH
 *
 *   GameII→TT:
 *     frameW  = scaleX
 *     frameH  = scaleY
 *     layerX  = anchorX - frameW / 2
 *     layerY  = anchorY - frameH
 */
public final class SpriteLayer extends ImageLayer {

    /** Absoluter Pfad zur .txt-Datei des Sprites. */
    private final String       spriteFilePath;
    /** Originalzeilen der .txt-Datei – für verlustfreies Rückschreiben. */
    private final List<String> rawLines;
    /** Z-Tiefe des Sprites (Zoom-Faktor, z. B. 1.0 = normal). */
    private final double       zDepth;

    public SpriteLayer(int id, BufferedImage image, int x, int y, int w, int h,
                       String spriteFilePath, List<String> rawLines, double zDepth) {
        super(id, image, x, y, w, h, 0.0, 100);
        this.spriteFilePath = spriteFilePath;
        this.rawLines       = List.copyOf(rawLines);
        this.zDepth         = zDepth;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       spriteFilePath() { return spriteFilePath; }
    public List<String> rawLines()       { return rawLines; }
    public double       zDepth()         { return zDepth; }

    // ── Mutations – erhalten rawLines / zDepth ────────────────────────────────

    @Override
    public SpriteLayer withPosition(int nx, int ny) {
        return new SpriteLayer(id(), image(), nx, ny, width(), height(),
                               spriteFilePath, rawLines, zDepth);
    }

    @Override
    public SpriteLayer withBounds(int nx, int ny, int nw, int nh) {
        return new SpriteLayer(id(), image(), nx, ny, nw, nh,
                               spriteFilePath, rawLines, zDepth);
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    @Override
    public String displayName() {
        String name = spriteFilePath;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        return name.replaceAll("\\.txt$", "");
    }
}
