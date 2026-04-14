package paint;

import java.io.*;
import java.util.*;

/**
 * Verwaltet die "zuletzt verwendet"-Listen für Projekte nach Kategorie.
 * Speichert/lädt einfache Textdateien in settings/lastProjects/{category}.txt
 */
public class LastProjectsManager {

    public static final String CAT_TEACHING = "teaching";
    public static final String CAT_BOOKS    = "books";
    public static final String CAT_GAMES    = "games";
    public static final String CAT_IMAGES   = "images";

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
     * Löscht Duplikate, behält max 10 Einträge.
     */
    public static void addRecent(String category, String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        path = path.trim();

        List<String> recent = load(category);
        recent.remove(path); // Remove if already exists
        recent.add(0, path); // Add at the beginning

        // Keep only latest 10
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }

        save(category, recent);
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
        result.put(CAT_TEACHING, load(CAT_TEACHING));
        result.put(CAT_BOOKS,    load(CAT_BOOKS));
        result.put(CAT_GAMES,    load(CAT_GAMES));
        result.put(CAT_IMAGES,   load(CAT_IMAGES));
        return result;
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
