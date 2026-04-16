package paint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest eine GameII-Scene und gibt TT-Layer zurück.
 *
 * Verzeichnis-Struktur erwartet:
 *   <sceneRoot>/
 *     <SceneName>.txt          ← Manifest mit #GameII:-Sektion (Canvas-Größe)
 *     sprites/
 *       *.txt                  ← Sprite-Dateien  → SpriteLayer
 *       images/
 *         *.png                ← Sprite-Bilder
 *     keyAreas/
 *       *.txt                  ← KeyArea-Dateien → PathLayer (closed)
 *     paths/
 *       *.txt                  ← ScenePath-Dateien → PathLayer (open)
 *
 * DEFAULT_FRAME_SIZE = 100 px  (aus SpriteImage.DEFAULT_FRAME_SIZE)
 * Ankerpunkt eines Sprites = Unterkante-Mitte des Frames.
 */
public class GameSceneReader {

    private static final int DEFAULT_FRAME_SIZE = 100;

    // ── Ergebnis-Container ────────────────────────────────────────────────────

    public static class GameSceneData {
        public int         canvasW = 800;
        public int         canvasH = 600;
        public List<Layer> layers  = new ArrayList<>();
    }

    // ── Haupt-Methode ─────────────────────────────────────────────────────────

    /**
     * Liest die komplette GameII-Scene aus {@code sceneRoot}.
     *
     * @param sceneRoot  Scene-Verzeichnis (enthält &lt;Name&gt;.txt + sprites/ …)
     * @param sceneName  Name des Verzeichnisses (= Dateiname ohne .txt)
     */
    public static GameSceneData readScene(File sceneRoot, String sceneName) throws IOException {
        GameSceneData data = new GameSceneData();

        // Canvas-Größe aus Manifest lesen
        File manifest = new File(sceneRoot, sceneName + ".txt");
        if (manifest.exists()) readManifest(manifest, data);

        int nextId = 1;

        // ── Sprites ──────────────────────────────────────────────────────────
        File spritesDir = new File(sceneRoot, "sprites");
        // Images are stored alongside the .txt files in sprites/ directly.
        // Skip "sprites.txt" – it is a container/index file, not a sprite entity.
        if (spritesDir.exists()) {
            File[] txts = spritesDir.listFiles(f ->
                    f.isFile() && f.getName().endsWith(".txt")
                    && !f.getName().equalsIgnoreCase("sprites.txt"));
            if (txts != null) {
                java.util.Arrays.sort(txts);
                System.out.println("[GameSceneReader] Verarbeite " + txts.length + " Sprite-Dateien in: " + spritesDir);
                for (File f : txts) {
                    SpriteLayer sl = readSprite(f, spritesDir, nextId++, data.canvasW, data.canvasH);
                    if (sl != null) {
                        data.layers.add(sl);
                        System.out.println("[GameSceneReader]   + " + f.getName()
                                + " → x=" + sl.x() + " y=" + sl.y()
                                + " w=" + sl.width() + " h=" + sl.height());
                    } else {
                        System.err.println("[GameSceneReader]   ! " + f.getName() + " → null (übersprungen)");
                    }
                }
            }
        }

        // ── KeyAreas ──────────────────────────────────────────────────────────
        File keyAreasDir = new File(sceneRoot, "keyAreas");
        if (keyAreasDir.exists()) {
            File[] txts = keyAreasDir.listFiles(f -> f.isFile() && f.getName().endsWith(".txt"));
            if (txts != null) {
                for (File f : txts) {
                    PathLayer pl = readKeyArea(f, nextId++, data.canvasW, data.canvasH);
                    if (pl != null) data.layers.add(pl);
                }
            }
        }

        // ── ScenePaths ────────────────────────────────────────────────────────
        File pathsDir = new File(sceneRoot, "paths");
        if (pathsDir.exists()) {
            File[] txts = pathsDir.listFiles(f -> f.isFile() && f.getName().endsWith(".txt"));
            if (txts != null) {
                for (File f : txts) {
                    PathLayer pl = readScenePath(f, nextId++, data.canvasW, data.canvasH);
                    if (pl != null) data.layers.add(pl);
                }
            }
        }

        System.out.println("[GameSceneReader] Scene geladen: " + sceneName
                + " – " + data.layers.size() + " Layer, Canvas " + data.canvasW + "×" + data.canvasH);
        return data;
    }

    // ── Manifest ─────────────────────────────────────────────────────────────

    /** Liest canvasWidth / canvasHeight aus dem #GameII:-Abschnitt des Manifests. */
    private static void readManifest(File manifest, GameSceneData data) {
        try {
            List<String> lines = Files.readAllLines(manifest.toPath());
            boolean inGameII = false;
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("#")) {
                    inGameII = t.equals("#GameII:");
                    continue;
                }
                if (!inGameII) continue;
                if (t.startsWith("-")) t = t.substring(1).trim();
                if (t.startsWith("canvasWidth:")) {
                    try { data.canvasW = Integer.parseInt(t.substring("canvasWidth:".length()).trim()); }
                    catch (NumberFormatException ignored) {}
                } else if (t.startsWith("canvasHeight:")) {
                    try { data.canvasH = Integer.parseInt(t.substring("canvasHeight:".length()).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("[GameSceneReader] Manifest nicht lesbar: " + e.getMessage());
        }
    }

    // ── Sprite ────────────────────────────────────────────────────────────────

    private static SpriteLayer readSprite(File file, File imagesDir, int id,
                                          int canvasW, int canvasH) {
        try {
            List<String> rawLines = Files.readAllLines(file.toPath());

            // Felder extrahieren
            double x = 50, y = 50, z = 1;
            double scaleX = DEFAULT_FRAME_SIZE, scaleY = DEFAULT_FRAME_SIZE;
            String firstImageName = null;
            String section = null;
            int posIdx = 0, sizeIdx = 0;

            for (String line : rawLines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("##")) continue;
                if (t.startsWith("#")) {
                    section = t.substring(1).trim();
                    posIdx = 0; sizeIdx = 0;
                    continue;
                }
                if ("INIT_POSITION".equals(section) && !t.contains(":") && !t.startsWith("@")) {
                    try {
                        double val = Double.parseDouble(t.replace("°", "").trim());
                        if (posIdx == 0) x = val;
                        else if (posIdx == 1) y = val;
                        else if (posIdx == 2) z = val;
                        posIdx++;
                    } catch (NumberFormatException ignored) {}
                } else if ("SIZE".equals(section) && !t.contains(":") && !t.startsWith("@")) {
                    try {
                        double val = Double.parseDouble(t.trim());
                        if (sizeIdx == 0) { scaleX = val; scaleY = val; }
                        else if (sizeIdx == 1) scaleY = val;
                        sizeIdx++;
                    } catch (NumberFormatException ignored) {}
                } else if ("IMAGES".equals(section) && firstImageName == null
                        && !t.contains(":") && !t.startsWith("@") && !t.isEmpty()) {
                    firstImageName = t;
                }
            }

            // Bild laden
            BufferedImage img = null;
            if (firstImageName != null) {
                File imgFile = new File(imagesDir, firstImageName);
                if (imgFile.exists()) {
                    img = javax.imageio.ImageIO.read(imgFile);
                }
            }
            if (img == null) img = createPlaceholder((int) scaleX, (int) scaleY);

            // Koordinaten GameII → TT
            // frameW = scaleX, frameH = scaleY  (da DEFAULT_FRAME_SIZE = 100)
            int frameW = Math.max(1, (int) scaleX);
            int frameH = Math.max(1, (int) scaleY);
            int anchorX = (int) (x / 100.0 * canvasW);
            int anchorY = (int) (y / 100.0 * canvasH);
            int layerX  = anchorX - frameW / 2;
            int layerY  = anchorY - frameH;       // Ankerpunkt = Unterkante

            return new SpriteLayer(id, img, layerX, layerY, frameW, frameH,
                                   file.getAbsolutePath(), rawLines, z);

        } catch (IOException e) {
            System.err.println("[GameSceneReader] Sprite IOException: " + file.getName() + " – " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("[GameSceneReader] Sprite Fehler: " + file.getName() + " – " + e);
            e.printStackTrace();
            return null;
        }
    }

    // ── KeyArea ───────────────────────────────────────────────────────────────

    private static PathLayer readKeyArea(File file, int id, int canvasW, int canvasH) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<Point3D> points = new ArrayList<>();
            double offsetXPct = 0, offsetYPct = 0;
            String section = null;

            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("#")) { section = t.substring(1).trim(); continue; }
                if (t.startsWith("-")) t = t.substring(1).trim();

                if ("Properties:".equals(section)) {
                    if (t.startsWith("x:"))
                        try { offsetXPct = Double.parseDouble(t.substring(2).trim()); } catch (NumberFormatException ignored) {}
                    else if (t.startsWith("y:"))
                        try { offsetYPct = Double.parseDouble(t.substring(2).trim()); } catch (NumberFormatException ignored) {}
                } else if ("Points:".equals(section) && t.startsWith("point")) {
                    // Format: pointN: x=300, y=200, z=1
                    double px = parseInlineCoord(t, "x");
                    double py = parseInlineCoord(t, "y");
                    if (px >= 0 || py >= 0) {
                        double absX = px + offsetXPct / 100.0 * canvasW;
                        double absY = py + offsetYPct / 100.0 * canvasH;
                        points.add(new Point3D(absX, absY, 1));
                    }
                }
            }
            if (points.isEmpty()) return null;
            return PathLayer.of(id, points, null, true, 0, 0);

        } catch (IOException e) {
            System.err.println("[GameSceneReader] KeyArea nicht lesbar: " + file.getName() + " – " + e.getMessage());
            return null;
        }
    }

    // ── ScenePath ─────────────────────────────────────────────────────────────

    private static PathLayer readScenePath(File file, int id, int canvasW, int canvasH) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<Point3D> points = new ArrayList<>();
            double offsetXPct = 0, offsetYPct = 0;
            String section = null;

            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("#")) { section = t.substring(1).trim(); continue; }
                if (t.startsWith("-")) t = t.substring(1).trim();

                if ("Properties:".equals(section)) {
                    if (t.startsWith("x:"))
                        try { offsetXPct = Double.parseDouble(t.substring(2).trim()); } catch (NumberFormatException ignored) {}
                    else if (t.startsWith("y:"))
                        try { offsetYPct = Double.parseDouble(t.substring(2).trim()); } catch (NumberFormatException ignored) {}
                } else if ("Points:".equals(section) && t.startsWith("point:")) {
                    // Nur Hauptpunkte (depth=1: "point:"), keine Handles ("inf:")
                    double px = parseInlineCoord(t, "x");
                    double py = parseInlineCoord(t, "y");
                    double pz = parseInlineCoord(t, "z");
                    double absX = px + offsetXPct / 100.0 * canvasW;
                    double absY = py + offsetYPct / 100.0 * canvasH;
                    points.add(new Point3D(absX, absY, pz >= 0 ? pz : 1.0));
                }
            }
            if (points.isEmpty()) return null;
            return PathLayer.of(id, points, null, false, 0, 0);

        } catch (IOException e) {
            System.err.println("[GameSceneReader] ScenePath nicht lesbar: " + file.getName() + " – " + e.getMessage());
            return null;
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Parst einen Wert aus "x=300, y=200, z=1" – gibt -1 zurück bei Fehler. */
    private static double parseInlineCoord(String line, String key) {
        int idx = line.indexOf(key + "=");
        if (idx < 0) return -1;
        int start = idx + key.length() + 1;
        int end   = line.indexOf(',', start);
        String raw = (end > 0 ? line.substring(start, end) : line.substring(start)).trim();
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return -1; }
    }

    /** Erzeugt ein graues Platzhalter-Bild wenn kein Sprite-Bild gefunden wird. */
    private static BufferedImage createPlaceholder(int w, int h) {
        int pw = Math.max(10, w), ph = Math.max(10, h);
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(80, 80, 120, 180));
        g.fillRect(0, 0, pw, ph);
        g.setColor(new java.awt.Color(160, 160, 200));
        g.drawRect(0, 0, pw - 1, ph - 1);
        g.drawLine(0, 0, pw, ph);
        g.drawLine(pw, 0, 0, ph);
        g.dispose();
        return img;
    }
}
