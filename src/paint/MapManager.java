package paint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Verwaltet Translation Maps pro Sprache als JSON-Dateien.
 * Speichert/lädt maps/{language}.json mit allen Maps dieser Sprache.
 */
public class MapManager {

    /**
     * Lädt alle Maps für eine Sprache.
     * Rückgabe: Map mit id → TranslationMap-Objekt
     */
    public static java.util.Map<String, TranslationMap> loadMapsForLanguage(String language) throws IOException {
        File file = AppPaths.getMapFile(language);
        java.util.Map<String, TranslationMap> result = new LinkedHashMap<>();

        if (!file.exists()) {
            return result;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        String json = sb.toString().trim();
        if (json.isEmpty() || json.equals("[]")) {
            return result;
        }

        // Parse JSON array of maps
        parseJsonMapsArray(json, result);
        return result;
    }

    /**
     * Speichert alle Maps für eine Sprache als JSON-Array.
     */
    public static void saveMapsForLanguage(String language, Collection<TranslationMap> maps) throws IOException {
        File file = AppPaths.getMapFile(language);
        file.getParentFile().mkdirs();

        StringBuilder json = new StringBuilder("[\n");
        int idx = 0;
        for (TranslationMap m : maps) {
            json.append("  {\n");
            json.append("    \"id\": \"").append(escapeJsonString(m.id())).append("\",\n");
            json.append("    \"language\": \"").append(escapeJsonString(m.language())).append("\",\n");
            json.append("    \"section\": \"").append(escapeJsonString(m.section())).append("\",\n");
            json.append("    \"textI\": \"").append(escapeJsonString(m.textI())).append("\",\n");
            json.append("    \"textII\": \"").append(escapeJsonString(m.textII())).append("\",\n");
            json.append("    \"createdAt\": ").append(m.createdAt()).append(",\n");
            json.append("    \"modifiedAt\": ").append(m.modifiedAt()).append("\n");
            json.append("  }");
            if (idx < maps.size() - 1) {
                json.append(",");
            }
            json.append("\n");
            idx++;
        }
        json.append("]");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(json.toString());
        }
    }

    /**
     * Fügt eine neue Map hinzu oder aktualisiert sie (wenn id bereits existiert).
     * Speichert sofort.
     */
    public static void addOrUpdateMap(TranslationMap newMap) throws IOException {
        String language = newMap.language();
        java.util.Map<String, TranslationMap> maps = loadMapsForLanguage(language);
        maps.put(newMap.id(), newMap);
        saveMapsForLanguage(language, maps.values());
    }

    /**
     * Löscht eine Map aus der Sprache-Datei.
     */
    public static void deleteMap(String language, String mapId) throws IOException {
        java.util.Map<String, TranslationMap> maps = loadMapsForLanguage(language);
        maps.remove(mapId);
        saveMapsForLanguage(language, maps.values());
    }

    /**
     * Lädt eine spezifische Map nach ID.
     */
    public static TranslationMap getMapById(String language, String id) throws IOException {
        java.util.Map<String, TranslationMap> maps = loadMapsForLanguage(language);
        return maps.get(id);
    }

    /**
     * Lädt alle Maps aus allen Sprache-Dateien.
     */
    public static java.util.Map<String, List<TranslationMap>> loadAllMaps() throws IOException {
        java.util.Map<String, List<TranslationMap>> result = new LinkedHashMap<>();
        File mapsDir = AppPaths.getMapsDir();

        if (!mapsDir.exists() || !mapsDir.isDirectory()) {
            return result;
        }

        File[] files = mapsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String language = f.getName();
                language = language.substring(0, language.length() - 5); // Remove ".json"
                java.util.Map<String, TranslationMap> maps = loadMapsForLanguage(language);
                result.put(language, new ArrayList<>(maps.values()));
            }
        }

        return result;
    }

    /**
     * Generiert eine eindeutige ID für eine neue Map (z.B. "map_1609459200000").
     */
    public static String generateMapId() {
        return "map_" + System.currentTimeMillis();
    }

    // ── JSON Helper ────────────────────────────────────────────────────────

    /**
     * Parst ein JSON-Array von Maps und füllt die Map.
     */
    private static void parseJsonMapsArray(String json, java.util.Map<String, TranslationMap> result) {
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return;
        }

        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return;
        }

        // Split by objects: find balanced braces
        int braceCount = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (braceCount == 0) start = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    String objStr = content.substring(start, i + 1);
                    TranslationMap m = parseJsonMapObject(objStr);
                    if (m != null) {
                        result.put(m.id(), m);
                    }
                }
            }
        }
    }

    /**
     * Parst ein JSON-Objekt zu einer TranslationMap.
     * Robust parser that handles simple JSON objects.
     */
    private static TranslationMap parseJsonMapObject(String json) {
        java.util.Map<String, String> fields = new HashMap<>();

        // Extract key-value pairs using regex-like approach
        int pos = 0;
        while (pos < json.length()) {
            // Find next "key":
            int keyQuoteStart = json.indexOf('"', pos);
            if (keyQuoteStart == -1) break;

            int keyQuoteEnd = json.indexOf('"', keyQuoteStart + 1);
            if (keyQuoteEnd == -1) break;

            String key = json.substring(keyQuoteStart + 1, keyQuoteEnd);

            // Find colon after this key
            int colonPos = json.indexOf(':', keyQuoteEnd);
            if (colonPos == -1) break;

            // Find value start
            int valueStart = colonPos + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= json.length()) break;

            String value;
            int nextPos;

            // Parse value based on type
            if (json.charAt(valueStart) == '"') {
                // String value - find closing quote, handling escapes
                int valueEnd = valueStart + 1;
                while (valueEnd < json.length()) {
                    if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') {
                        break;
                    }
                    valueEnd++;
                }
                if (valueEnd >= json.length()) break;

                value = json.substring(valueStart + 1, valueEnd);
                nextPos = valueEnd + 1;
            } else {
                // Number, boolean, or null - find comma or closing brace
                int valueEnd = valueStart;
                while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                value = json.substring(valueStart, valueEnd).trim();
                nextPos = valueEnd;
            }

            fields.put(key, unescapeJsonString(value));

            // Move to next field
            pos = json.indexOf(',', nextPos);
            if (pos == -1) break;
            pos++;
        }

        if (fields.containsKey("id") && fields.containsKey("language") && fields.containsKey("section") &&
            (fields.containsKey("textI") || fields.containsKey("textII"))) {
            long createdAt = parseLong(fields.get("createdAt"), System.currentTimeMillis());
            long modifiedAt = parseLong(fields.get("modifiedAt"), createdAt);

            String textI = fields.getOrDefault("textI", "");
            String textII = fields.getOrDefault("textII", "");
            TranslationMap m = new TranslationMap(fields.get("id"), fields.get("language"), fields.get("section"),
                           textI, textII, createdAt);
            m.setModifiedTime(modifiedAt);
            return m;
        }
        return null;
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static long parseLong(String s, long defaultVal) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
