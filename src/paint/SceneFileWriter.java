package paint;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Schreibt eine Scene-Datei (Haupt-Szenen-Datei mit Referenzen).
 * Speichert Image-Referenzen, Pfad-Dateinamen und Text-Dateinamen.
 *
 * Format:
 * #Images:
 * -image1.png
 * -image2.png
 *
 * #Paths:
 * -path_walk.txt
 *
 * #Texts:
 * -text_title.txt
 */
public class SceneFileWriter {

    /**
     * Schreibt eine Scene-Datei mit allen Referenzen und speichert Paths/Texts einzeln.
     *
     * @param sceneDir Szenen-Verzeichnis (z.B. ~/.../scenes/myScene/)
     * @param sceneName Name der Scene
     * @param backgroundImageFile Background-Bild (für Referenz)
     * @param layers Alle Layers (TextLayer, PathLayer)
     */
    public static void writeScene(File sceneDir, String sceneName, File backgroundImageFile, List<Layer> layers) throws IOException {
        // Stelle sicher, dass Verzeichnis-Struktur existiert
        sceneDir.mkdirs();
        File pathsDir = new File(sceneDir, "paths");
        File textsDir = new File(sceneDir, "texts");
        pathsDir.mkdirs();
        textsDir.mkdirs();

        // Sammle Referenzen
        StringBuilder imageRefs = new StringBuilder();
        StringBuilder pathRefs = new StringBuilder();
        StringBuilder textRefs = new StringBuilder();

        // Background-Bild hinzufügen
        if (backgroundImageFile != null) {
            imageRefs.append("-").append(backgroundImageFile.getName()).append("\n");
        }

        // Durchlaufe alle Layers
        for (Layer layer : layers) {
            if (layer instanceof TextLayer tl) {
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
                // Für jetzt: nur Referenz, echtes Speichern kommt später
                pathRefs.append("-").append(pathFileName).append("\n");
            }
            // ImageLayer: nur Referenz (Bild wird nicht in Scene gespeichert)
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
}
