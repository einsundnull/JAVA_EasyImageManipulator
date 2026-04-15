package paint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest eine Scene-Datei und ihre Referenzen (Paths, Texts).
 * Rekonstruiert die komplette Scene mit allen Layers.
 */
public class SceneFileReader {

    public static class SceneData {
        public String sceneName;
        public File backgroundImage;
        public List<TextLayer> textLayers = new ArrayList<>();
        public List<PathLayer> pathLayers = new ArrayList<>();
    }

    /**
     * Liest eine Scene-Datei aus dem angegebenen Verzeichnis.
     *
     * @param sceneDir Szenen-Verzeichnis (z.B. ~/.../scenes/myScene/)
     * @param sceneName Name der Scene (ohne .txt Extension)
     * @return SceneData mit allen geladenen Layers
     */
    public static SceneData readScene(File sceneDir, String sceneName) throws IOException {
        SceneData data = new SceneData();
        data.sceneName = sceneName;

        File sceneFile = new File(sceneDir, sceneName + ".txt");
        if (!sceneFile.exists()) {
            throw new IOException("Scene-Datei nicht gefunden: " + sceneFile.getAbsolutePath());
        }

        File pathsDir = new File(sceneDir, "paths");
        File textsDir = new File(sceneDir, "texts");

        try (BufferedReader r = new BufferedReader(new FileReader(sceneFile))) {
            String line;
            String section = null;

            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;

                String trimmed = line.trim();

                // Abschnitt-Header
                if (trimmed.startsWith("#")) {
                    section = trimmed.substring(1).trim();
                    continue;
                }

                if (section == null) continue;

                // Referenz-Eintrag (mit "-" Präfix)
                if (trimmed.startsWith("-")) {
                    trimmed = trimmed.substring(1).trim();
                }

                switch (section) {
                    case "Name:":
                        data.sceneName = trimmed;
                        break;

                    case "Images:":
                        // Background-Bild laden
                        if (!trimmed.isEmpty()) {
                            // TODO: Hier könnte das Bild geladen werden
                            // Für jetzt nur Referenz speichern
                            data.backgroundImage = new File(trimmed);
                        }
                        break;

                    case "Paths:":
                        if (!trimmed.isEmpty() && pathsDir.exists()) {
                            File pathFile = new File(pathsDir, trimmed);
                            if (pathFile.exists()) {
                                // TODO: PathLayer laden mit PathReader
                                // pathData = PathReader.readConfigFile(pathFile.getAbsolutePath());
                                // data.pathLayers.add(pathData);
                            }
                        }
                        break;

                    case "Texts:":
                        if (!trimmed.isEmpty() && textsDir.exists()) {
                            File textFile = new File(textsDir, trimmed);
                            if (textFile.exists()) {
                                TextLayer textLayer = TextReader.readConfigFile(textFile.getAbsolutePath());
                                if (textLayer != null) {
                                    data.textLayers.add(textLayer);
                                }
                            }
                        }
                        break;
                }
            }

        } catch (IOException e) {
            System.err.println("[SceneFileReader] " + e.getMessage());
            throw e;
        }

        System.out.println("[SceneFileReader] Scene geladen: " + sceneName);
        System.out.println("  - TextLayers: " + data.textLayers.size());
        System.out.println("  - PathLayers: " + data.pathLayers.size());

        return data;
    }
}
