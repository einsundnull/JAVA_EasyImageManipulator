package paint;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Schreibt eine Scene-Datei (Haupt-Szenen-Datei mit Referenzen).
 *
 * Format:
 * #Images:
 * -background.png      (erstes Bild = Background)
 * -layer1.png          (weitere Bilder = ImageLayers)
 * -layer2.png
 *
 * #Paths:
 * -path_walk.txt
 *
 * #Texts:
 * -text_title.txt
 */
public class SceneFileWriter {

    /**
     * Schreibt eine Scene-Datei mit allen Referenzen und speichert Paths/Texts/Images einzeln.
     *
     * @param sceneDir Szenen-Verzeichnis (z.B. ~/.../scenes/myScene/)
     * @param sceneName Name der Scene
     * @param backgroundImageFile Background-Bild (Referenz)
     * @param layers Alle Layers (ImageLayer, TextLayer, PathLayer)
     */
    public static void writeScene(File sceneDir, String sceneName, File backgroundImageFile, List<Layer> layers) throws IOException {
        // Stelle sicher, dass Verzeichnis-Struktur existiert
        sceneDir.mkdirs();
        File pathsDir = new File(sceneDir, "paths");
        File textsDir = new File(sceneDir, "texts");
        File imagesDir = new File(sceneDir, "images");
        pathsDir.mkdirs();
        textsDir.mkdirs();
        imagesDir.mkdirs();

        // Sammle Referenzen
        StringBuilder imageRefs = new StringBuilder();
        StringBuilder pathRefs = new StringBuilder();
        StringBuilder textRefs = new StringBuilder();

        // Erstes Bild = Background (kopiere in images/ Verzeichnis)
        if (backgroundImageFile != null && backgroundImageFile.exists()) {
            String bgFileName = "background" + getFileExtension(backgroundImageFile.getName());
            File bgDestFile = new File(imagesDir, bgFileName);
            java.nio.file.Files.copy(backgroundImageFile.toPath(), bgDestFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            imageRefs.append("-").append(bgFileName).append("\n");
        }

        // Durchlaufe alle Layers
        int imageCounter = 1;
        for (Layer layer : layers) {
            if (layer instanceof ImageLayer il) {
                // ImageLayer: speichere Bild als Datei
                if (il.image() != null) {
                    String imageFileName = "image_" + imageCounter + ".png";
                    File imageFile = new File(imagesDir, imageFileName);
                    javax.imageio.ImageIO.write(il.image(), "png", imageFile);
                    imageRefs.append("-").append(imageFileName).append("\n");
                    imageCounter++;
                }
            } else if (layer instanceof TextLayer tl) {
                // Text: speichere als separate .txt Datei
                String textFileName = "text_" + tl.id() + ".txt";
                File textFile = new File(textsDir, textFileName);
                TextWriter.writeConfigFile(textFile.getAbsolutePath(), tl);
                textRefs.append("-").append(textFileName).append("\n");
            } else if (layer instanceof PathLayer pl) {
                // Path: speichere als separate .txt Datei
                String pathFileName = "path_" + pl.id() + ".txt";
                File pathFile = new File(pathsDir, pathFileName);
                // TODO: PathWriter.writeConfigFile(pathFile.getAbsolutePath(), pl);
                pathRefs.append("-").append(pathFileName).append("\n");
            }
        }

        // Haupt-Scene-Datei schreiben
        File sceneFile = new File(sceneDir, sceneName + ".txt");
        try (FileWriter w = new FileWriter(sceneFile)) {
            w.write("#Name:\n");
            w.write("-" + sceneName + "\n\n");

            if (imageRefs.length() > 0) {
                w.write("#Images:\n");
                w.write(imageRefs.toString());
                w.write("\n");
            }

            if (pathRefs.length() > 0) {
                w.write("#Paths:\n");
                w.write(pathRefs.toString());
                w.write("\n");
            }

            if (textRefs.length() > 0) {
                w.write("#Texts:\n");
                w.write(textRefs.toString());
                w.write("\n");
            }
        }

        System.out.println("[SceneFileWriter] Scene gespeichert: " + sceneFile.getAbsolutePath());
    }

    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(lastDot) : ".png";
    }
}
