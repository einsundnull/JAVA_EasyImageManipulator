package paint;

import java.io.File;

/**
 * LEGACY SUPPORT: Liest GameII-Szenen im klassischen TXT-Format
 * (Umgekehrte Richtung: GameII-TXT → TransparencyTool).
 *
 * Diese Klasse erlaubt TransparencyTool, alte GameII-Szenen zu importieren.
 *
 * Format (INPUT von GameII):
 * #Name:
 * -scene_name
 *
 * #Image:
 * -path/to/image.png
 *
 * #Entities:
 * -Entity: id=1, x=100, y=50, width=64, height=64, ...
 *
 * #KeyAreas:
 * -@keyarea_name
 *
 * #Paths:
 * -@path_name
 *
 * HINWEIS: Diese Klasse ist ein Strukturbeispiel.
 * Implementierung erfolgt später, wenn Legacy-Import notwendig wird.
 * Das neue JSON-Format ist der Standard.
 *
 * @see SceneLocator
 * @see SceneSerializer
 */
public class ToolLegacySceneReader {

    /**
     * NICHT IMPLEMENTIERT - nur Struktur-Skeleton.
     *
     * Liest eine GameII-TXT-Szenen-Datei und konvertiert sie zu TransparencyTool-Layers.
     *
     * @param txtFile Quell-TXT-Datei im GameII-Legacy-Format
     * @return Liste von Layer-Objekten, oder null wenn Fehler
     */
    public static java.util.List<Layer> read(File txtFile) {
        // TODO: Implementieren wenn Legacy-Import benötigt
        System.out.println("[TODO] ToolLegacySceneReader.read() implementieren");
        System.out.println("[INFO] Quell-Datei: " + txtFile.getAbsolutePath());

        // Geplante Schritte:
        // 1. TXT-Datei zeilenweise einlesen
        // 2. Entities als ImageLayers erstellen
        // 3. KeyAreas als PathLayers referenzieren
        // 4. Layer-Liste zurückgeben

        return null;
    }

    /**
     * BEISPIEL: Wie eine geparste GameII-Szene konvertiert werden könnte.
     */
    public static class ImportedScene {
        public String name;
        public String imageReference;
        public java.util.List<ImportedEntity> entities = new java.util.ArrayList<>();
        public java.util.List<String> keyAreaReferences = new java.util.ArrayList<>();
        public java.util.List<String> pathReferences = new java.util.ArrayList<>();

        public static class ImportedEntity {
            public int id;
            public int x, y, width, height;
            public String spriteSheet;
            // → wird zu ImageLayer in TransparencyTool
        }
    }
}
