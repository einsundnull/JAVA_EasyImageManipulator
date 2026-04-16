package paint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet die "zuletzt verwendet"-Listen für Projekte nach Kategorie.
 * Speichert/lädt einfache Textdateien in settings/lastProjects/{category}.txt
 */
public class LastProjectsManager {

    public static final String CAT_TEACHING = "teaching";
    public static final String CAT_BOOKS    = "books";
    public static final String CAT_GAMES    = "games";
    public static final String CAT_IMAGES   = "images";
    public static final String CAT_MAPS     = "maps";
    public static final String CAT_SCENES   = "scenes";

    private static final int MAX_RECENT = 10;

    /**
     * Lädt die letzten Projekte für eine Kategorie.
     * Format: eine Zeile = ein Pfad, neueste oben.
     */
    public static List<String> load(String category) throws IOException {
        File file = AppPaths.getLastProjectsFile(category);
        List<String> result = new ArrayList<>();

        if (!file.exists()) {
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    /**
     * Fügt einen Pfad vorne in die Liste ein und speichert sofort.
     * Normalisiert den Pfad (kanonisch, kein trailing Slash) und
     * löscht Duplikate, behält max 10 Einträge.
     */
    public static void addRecent(String category, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) return;
        final String normalized = normalize(path);

        List<String> recent = load(category);
        // Remove any existing entry that normalizes to the same path
        recent.removeIf(p -> normalize(p).equalsIgnoreCase(normalized));
        recent.add(0, path);

        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        save(category, recent);
    }

    /** Returns canonical, trailing-slash-free, platform-consistent path string. */
    private static String normalize(String path) {
        try {
            String canonical = new File(path.trim()).getCanonicalPath();
            // Remove trailing separator
            while (canonical.endsWith(File.separator) && canonical.length() > 1)
                canonical = canonical.substring(0, canonical.length() - 1);
            return canonical;
        } catch (Exception e) {
            return path.trim();
        }
    }

    /**
     * Speichert eine Liste von Pfaden in die Kategorie-Datei.
     */
    private static void save(String category, List<String> paths) throws IOException {
        File file = AppPaths.getLastProjectsFile(category);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String path : paths) {
                writer.write(path);
                writer.newLine();
            }
        }
    }

    /**
     * Lädt alle Kategorien und ihre letzten Projekte.
     */
    public static Map<String, List<String>> loadAll() throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();

        // Scenes: always ensure the Default project scenes dir appears first
        List<String> scenes = load(CAT_SCENES);
        ensureDefaultScenesDirs(scenes);
        result.put(CAT_SCENES,   scenes);

        result.put(CAT_IMAGES,   load(CAT_IMAGES));
        result.put(CAT_BOOKS,    load(CAT_BOOKS));
        result.put(CAT_GAMES,    load(CAT_GAMES));
        result.put(CAT_TEACHING, load(CAT_TEACHING));
        result.put(CAT_MAPS,     load(CAT_MAPS));
        return result;
    }

    /**
     * Adds the scenes directories for all existing Tool projects to the list
     * if they are not already present. Ensures the user always sees them
     * even before they have manually opened the folder.
     */
    private static void ensureDefaultScenesDirs(List<String> scenes) {
        List<String> projects = SceneLocator.getToolProjects();
        if (projects.isEmpty()) projects = java.util.Arrays.asList("Default");
        for (String project : projects) {
            java.io.File dir = SceneLocator.getToolScenesDir(project);
            dir.mkdirs(); // create if not present
            String canonical = normalize(dir.getAbsolutePath());
            boolean already = scenes.stream().anyMatch(p -> normalize(p).equalsIgnoreCase(canonical));
            if (!already) scenes.add(canonical);
        }
    }

    /**
     * Erkennt die Kategorie für einen Pfad (sehr einfache Heuristik).
     * Fallback: CAT_IMAGES
     */
    public static String detectCategory(File dir) {
        String path = dir.getAbsolutePath().toLowerCase();
        if (path.contains("teaching")) return CAT_TEACHING;
        if (path.contains("book")) return CAT_BOOKS;
        if (path.contains("game")) return CAT_GAMES;
        if (path.contains("map")) return CAT_MAPS;
        return CAT_IMAGES;
    }

    /**
     * Entfernt einen Eintrag aus der Kategorie-Liste und speichert.
     */
    public static void removeRecent(String category, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        path = path.trim();

        List<String> recent = load(category);
        recent.remove(path);
        save(category, recent);
    }
}
