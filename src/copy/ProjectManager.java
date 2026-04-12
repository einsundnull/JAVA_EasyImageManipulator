package paint.copy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet Projekte und Szenen.
 * Ein Projekt = Sammlung von Szenen (Bilder mit ihren Layern).
 */
public class ProjectManager {

    private String currentProject;
    private File currentProjectDir;

    public ProjectManager() {
    }

    /**
     * Erstellt ein neues Projekt mit dem gegebenen Namen.
     */
    public void newProject(String projectName) throws IOException {
        File projectDir = AppPaths.getProjectDir(projectName);
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }

        currentProject = projectName;
        currentProjectDir = projectDir;

        // Erstelle leere project.json
        saveProjectMetadata(projectName);
    }

    /**
     * Öffnet ein bestehendes Projekt.
     */
    public void openProject(File projectDir) throws IOException {
        if (!projectDir.exists()) {
            throw new IOException("Projekt-Verzeichnis existiert nicht: " + projectDir.getAbsolutePath());
        }

        currentProject = projectDir.getName();
        currentProjectDir = projectDir;

        // Stelle sicher, dass Verzeichnisse existieren
        AppPaths.getProjectScenesDir(currentProject);
    }

    /**
     * Speichert die Metadaten des aktuellen Projekts.
     */
    public void saveProjectMetadata(String projectName) throws IOException {
        File jsonFile = AppPaths.getProjectJsonFile(projectName);
        File projectDir = AppPaths.getProjectDir(projectName);

        // Sammle Szenen-Dateien aus dem scenes/-Verzeichnis
        List<String> sceneFiles = new ArrayList<>();
        File scenesDir = new File(projectDir, "scenes");
        if (scenesDir.exists()) {
            File[] files = scenesDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    sceneFiles.add(f.getName());
                }
            }
        }

        // Schreibe project.json
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile))) {
            writer.write("{\n");
            writer.write("  \"name\": \"" + projectName + "\",\n");
            writer.write("  \"lastOpened\": \"" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\",\n");
            writer.write("  \"scenes\": [\n");

            for (int i = 0; i < sceneFiles.size(); i++) {
                writer.write("    \"" + sceneFiles.get(i) + "\"");
                if (i < sceneFiles.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }

            writer.write("  ]\n");
            writer.write("}\n");
        }
    }

    /**
     * Speichert eine Szene (Bild + Layer) für die gegebene Datei.
     */
    public void saveScene(File imageFile, List<Layer> layers, double zoom, AppMode mode) throws IOException {
        if (currentProject == null) {
            return; // Kein Projekt offen
        }

        File sceneJsonFile = AppPaths.getSceneJsonFile(currentProject, imageFile);
        String layersJson = SceneSerializer.layersToJson(layers);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sceneJsonFile))) {
            writer.write("{\n");
            writer.write("  \"imageFile\": \"" + imageFile.getAbsolutePath() + "\",\n");
            writer.write("  \"zoom\": " + zoom + ",\n");
            writer.write("  \"appMode\": \"" + (mode != null ? mode.toString() : "ALPHA_EDITOR") + "\",\n");
            writer.write("  \"layers\": " + layersJson + "\n");
            writer.write("}\n");
        }
    }

    /**
     * Lädt die gespeicherten Layer für die gegebene Datei.
     * Gibt null zurück, wenn keine Szene gespeichert wurde.
     */
    public List<Layer> loadScene(File imageFile) throws IOException {
        if (currentProject == null) {
            return null;
        }

        File sceneJsonFile = AppPaths.getSceneJsonFile(currentProject, imageFile);
        if (!sceneJsonFile.exists()) {
            return null;
        }

        String layersJson = extractFieldFromFile(sceneJsonFile, "layers");
        if (layersJson != null) {
            return SceneSerializer.layersFromJson(layersJson);
        }

        return null;
    }

    /**
     * Lädt die Zoom-Stufe für die gegebene Datei.
     * Gibt -1 zurück, wenn keine Szene gespeichert wurde.
     */
    public double loadSceneZoom(File imageFile) throws IOException {
        if (currentProject == null) {
            return -1;
        }

        File sceneJsonFile = AppPaths.getSceneJsonFile(currentProject, imageFile);
        if (!sceneJsonFile.exists()) {
            return -1;
        }

        String zoomStr = extractFieldFromFile(sceneJsonFile, "zoom");
        if (zoomStr != null) {
            try {
                return Double.parseDouble(zoomStr.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    /**
     * Lädt den AppMode für die gegebene Datei.
     * Gibt null zurück, wenn keine Szene gespeichert wurde.
     */
    public AppMode loadSceneMode(File imageFile) throws IOException {
        if (currentProject == null) {
            return null;
        }

        File sceneJsonFile = AppPaths.getSceneJsonFile(currentProject, imageFile);
        if (!sceneJsonFile.exists()) {
            return null;
        }

        String modeStr = extractFieldFromFile(sceneJsonFile, "appMode");
        if (modeStr != null) {
            try {
                return AppMode.valueOf(modeStr.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Gibt das Verzeichnis des aktuellen Projekts zurück.
     */
    public File getProjectDir() {
        return currentProjectDir;
    }

    /**
     * Gibt den Namen des aktuellen Projekts zurück.
     */
    public String getProjectName() {
        return currentProject;
    }

    private static String extractFieldFromFile(File file, String fieldName) throws IOException {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append("\n");
            }
        }

        String content = json.toString();
        String pattern = "\"" + fieldName + "\":";
        int idx = content.indexOf(pattern);
        if (idx < 0) return null;

        int start = idx + pattern.length();
        while (start < content.length() && Character.isWhitespace(content.charAt(start))) start++;

        if (start >= content.length()) return null;

        // Unterscheide zwischen String (in "") und Array/Zahl
        if (content.charAt(start) == '"') {
            // String-Wert
            int end = start + 1;
            while (end < content.length()) {
                if (content.charAt(end) == '"' && content.charAt(end - 1) != '\\') break;
                end++;
            }
            return content.substring(start + 1, end);
        } else if (content.charAt(start) == '[') {
            // Array: finde schließende ]
            int depth = 0;
            int end = start;
            while (end < content.length()) {
                if (content.charAt(end) == '[') depth++;
                if (content.charAt(end) == ']') {
                    depth--;
                    if (depth == 0) {
                        end++;
                        break;
                    }
                }
                end++;
            }
            return content.substring(start, end).trim();
        } else {
            // Zahl oder Boolean
            int end = start;
            while (end < content.length() && content.charAt(end) != ',' && content.charAt(end) != '\n' && content.charAt(end) != '}') {
                end++;
            }
            return content.substring(start, end).trim();
        }
    }
}
