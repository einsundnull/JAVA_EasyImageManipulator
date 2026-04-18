package paint;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Konvertiert eine SceneFile zu einem "Virtual Image" mit Layers.
 * Ermöglicht 100% Recycling der TilePanel-Logik für Szenen.
 *
 * SceneFile → Virtual Image + cached Layers
 *         ↓
 *   Identisch mit geladenem Image + Layers
 *         ↓
 *   Gleiche Thumbnail-Generierung, Bearbeitung, Dragging
 */
public class SceneImageAdapter {

    public static class SceneAsImage {
        public File sceneFile;           // Original Scene-Datei
        public BufferedImage thumbnail;  // Gerendert: Background + Layers
        public List<Layer> layers;       // Gecachte Layers
    }

    /**
     * Lädt eine Scene und erstellt ein "Virtual Image" mit gerenderten Layers.
     *
     * @param sceneFile Die Scene-Datei (.txt)
     * @return SceneAsImage mit Thumbnail + Layers
     */
    public static SceneAsImage loadSceneAsImage(File sceneFile) {
        SceneAsImage result = new SceneAsImage();
        result.sceneFile = sceneFile;
        result.layers = new ArrayList<>();

        try {
            // Lade Scene-Daten
            File sceneDir = sceneFile.getParentFile();
            String sceneName = sceneFile.getName().replace(".txt", "");
            SceneFileReader.SceneData sceneData = SceneFileReader.readScene(sceneDir, sceneName);

            // Lade Background-Image
            BufferedImage backgroundImg = null;
            if (sceneData.backgroundImage != null && sceneData.backgroundImage.exists()) {
                backgroundImg = ImageLoader.loadImage(sceneData.backgroundImage);
            }

            if (backgroundImg == null) {
                System.err.println("[SceneImageAdapter] Background-Image nicht geladen");
                return null;
            }

            // Sammle alle Layers
            result.layers.addAll(sceneData.textLayers);
            result.layers.addAll(sceneData.pathLayers);

            // Lade weitere Images als ImageLayers
            int layerId = 10000;
            for (SceneFileReader.ImageLayerRef ref : sceneData.imageLayers) {
                if (ref.file.exists()) {
                    BufferedImage img = ImageLoader.loadImage(ref.file);
                    if (img != null) {
                        int w = ref.w > 0 ? ref.w : img.getWidth();
                        int h = ref.h > 0 ? ref.h : img.getHeight();
                        ImageLayer il = new ImageLayer(layerId++, img, ref.x, ref.y, w, h, ref.rotation, ref.opacity);
                        result.layers.add(il);
                    }
                }
            }

            // Erstelle Thumbnail: Background + gerenderte Layers
            result.thumbnail = renderThumbnail(backgroundImg, result.layers);

            System.out.println("[SceneImageAdapter] Scene geladen: " + sceneName + " mit " + result.layers.size() + " Layers");
            return result;

        } catch (Exception e) {
            System.err.println("[SceneImageAdapter] Fehler beim Laden: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Rendert ein Thumbnail: Background + alle Layers zusammen.
     * Ähnlich wie Canvas-Rendering für Preview.
     */
    private static BufferedImage renderThumbnail(BufferedImage background, List<Layer> layers) {
        // Starte mit Background
        BufferedImage result = new BufferedImage(
            background.getWidth(), background.getHeight(),
            BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Zeichne Background
        g2.drawImage(background, 0, 0, null);

        // Zeichne alle Layers
        for (Layer layer : layers) {
            if (layer instanceof ImageLayer il) {
                ElementController.drawImageLayer(g2, il);
            } else if (layer instanceof TextLayer tl && !tl.isHidden()) {
                BufferedImage txt = ElementController.renderTextLayerToImage(tl);
                g2.drawImage(txt, tl.x(), tl.y(), null);
            } else if (layer instanceof PathLayer pl && !pl.isHidden()) {
                renderPathLayerOnGraphics(g2, pl);
            }
        }

        g2.dispose();
        return result;
    }

    /**
     * Zeichnet einen PathLayer auf einen Graphics2D als verbundene Punkte.
     */
    private static void renderPathLayerOnGraphics(Graphics2D g2, PathLayer pl) {
        java.util.List<Point3D> points = pl.points();
        if (points.isEmpty()) {
            return;
        }

        // Zeichne Linien zwischen Punkten
        g2.setColor(java.awt.Color.BLACK);
        g2.setStroke(new java.awt.BasicStroke(1.0f));

        int[] xCoords = new int[points.size()];
        int[] yCoords = new int[points.size()];

        for (int i = 0; i < points.size(); i++) {
            Point3D p = points.get(i);
            xCoords[i] = pl.x() + (int) p.x;
            yCoords[i] = pl.y() + (int) p.y;
        }

        // Zeichne offene oder geschlossene Pfade
        if (pl.isClosed() && points.size() > 2) {
            g2.drawPolygon(xCoords, yCoords, points.size());
        } else if (points.size() > 1) {
            g2.drawPolyline(xCoords, yCoords, points.size());
        }

        // TODO: Zusätzlich optionales Bild (il.image()) entlang des Pfads rendern
    }

    /**
     * Exportiert eine Scene als echtes Image-File mit Layers.
     * Scene → Image (bidirectional dragging).
     */
    public static void exportSceneAsImage(File sceneFile, File outputImageFile) throws Exception {
        SceneAsImage sceneImg = loadSceneAsImage(sceneFile);
        if (sceneImg == null || sceneImg.thumbnail == null) {
            throw new Exception("Scene konnte nicht geladen werden");
        }

        // Speichere Thumbnail als Image
        javax.imageio.ImageIO.write(sceneImg.thumbnail, "png", outputImageFile);

        System.out.println("[SceneImageAdapter] Scene exportiert: " + outputImageFile.getAbsolutePath());
    }
}
