package paint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Schreibt TT-Layer zurück in das GameII-Scene-Format.
 *
 * Für {@link SpriteLayer}:
 *   – Nur {@code #INIT_POSITION} (x%, y%, z) und {@code #SIZE} (scaleX, scaleY)
 *     werden aktualisiert.
 *   – Alle anderen Abschnitte (Animationen, Physik, Links …) werden aus
 *     {@link SpriteLayer#rawLines()} unverändert übernommen.
 *
 * Für {@link PathLayer} (KeyArea / ScenePath): TODO – noch nicht implementiert.
 *
 * Koordinaten-Umrechnung TT → GameII:
 *   anchorX  = layerX + layerW / 2
 *   anchorY  = layerY + layerH        (Ankerpunkt = Unterkante des Frames)
 *   xPct     = anchorX / canvasW * 100
 *   yPct     = anchorY / canvasH * 100
 *   scaleX   = layerW                 (da DEFAULT_FRAME_SIZE = 100)
 *   scaleY   = layerH
 */
public class GameSceneWriter {

    /**
     * Schreibt alle {@link SpriteLayer} aus {@code layers} zurück in ihre
     * jeweiligen .txt-Dateien.  PathLayer-Schreiben ist noch nicht implementiert.
     *
     * @param layers    aktive TT-Layer des Canvas
     * @param canvasW   Canvas-Breite in Pixel (für % ↔ Pixel)
     * @param canvasH   Canvas-Höhe  in Pixel
     */
    public static void writeScene(List<Layer> layers, int canvasW, int canvasH) throws IOException {
        for (Layer layer : layers) {
            if (layer instanceof SpriteLayer sl) {
                writeSprite(sl, canvasW, canvasH);
            }
            // PathLayer (KeyArea / ScenePath): TODO
        }
    }

    // ── Sprite ────────────────────────────────────────────────────────────────

    private static void writeSprite(SpriteLayer sl, int canvasW, int canvasH) throws IOException {
        // Koordinaten TT → GameII
        double anchorX = sl.x() + sl.width()  / 2.0;
        double anchorY = sl.y() + sl.height();          // Unterkante
        double xPct    = anchorX / canvasW * 100.0;
        double yPct    = anchorY / canvasH * 100.0;
        double scaleX  = sl.width();                    // DEFAULT_FRAME_SIZE = 100 → scaleX = frameW
        double scaleY  = sl.height();

        List<String> updated = buildUpdatedLines(sl.rawLines(), xPct, yPct, sl.zDepth(), scaleX, scaleY);

        Files.createDirectories(Paths.get(sl.spriteFilePath()).getParent());
        Files.write(Paths.get(sl.spriteFilePath()), updated);
        System.out.println("[GameSceneWriter] Sprite gespeichert: " + sl.spriteFilePath());
    }

    /**
     * Ersetzt nur die numerischen Werte in {@code #INIT_POSITION} und {@code #SIZE},
     * alle anderen Zeilen bleiben identisch.
     */
    private static List<String> buildUpdatedLines(List<String> rawLines,
            double xPct, double yPct, double zDepth, double scaleX, double scaleY) {

        List<String> out = new ArrayList<>(rawLines.size());
        String section = null;
        int posIdx = 0, sizeIdx = 0;

        for (String line : rawLines) {
            String t = line.trim();

            // Abschnitt-Erkennung (keine Sub-Abschnitte ##)
            if (t.startsWith("#") && !t.startsWith("##")) {
                section = t.substring(1).trim();
                posIdx = 0; sizeIdx = 0;
                out.add(line);
                continue;
            }

            // Zeilen in #INIT_POSITION ersetzen
            if ("INIT_POSITION".equals(section) && !t.isEmpty()
                    && !t.contains(":") && !t.startsWith("@")) {
                if      (posIdx == 0) out.add(fmt(xPct));
                else if (posIdx == 1) out.add(fmt(yPct));
                else if (posIdx == 2) out.add(fmt(zDepth));
                else                  out.add(line);
                posIdx++;
                continue;
            }

            // Zeilen in #SIZE ersetzen
            if ("SIZE".equals(section) && !t.isEmpty()
                    && !t.contains(":") && !t.startsWith("@")) {
                if      (sizeIdx == 0) out.add(fmt(scaleX));
                else if (sizeIdx == 1) out.add(fmt(scaleY));
                else                   out.add(line);
                sizeIdx++;
                continue;
            }

            out.add(line);
        }
        return out;
    }

    /** Formatiert einen Double-Wert ohne unnötige Nullen nach dem Komma. */
    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v))
            return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
