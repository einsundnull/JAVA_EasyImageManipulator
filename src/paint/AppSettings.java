package paint;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Globale App-Einstellungen (Singleton).
 * Liest/schreibt settings.json im AppData-Verzeichnis.
 * Keine externe JSON-Bibliothek — manuelles Parsing/Serialisierung.
 */
public class AppSettings {

    private static AppSettings instance;

    // Paint-Tool
    private int primaryColor = Color.BLACK.getRGB();
    private int secondaryColor = Color.WHITE.getRGB();
    private int strokeWidth = 3;
    private boolean antialias = true;
    private String fillMode = "SOLID";
    private String brushShape = "ROUND";
    private String activeTool = "PENCIL";

    // Text-Tool
    private String fontName = "SansSerif";
    private int fontSize = 24;
    private boolean textBold = false;
    private boolean textItalic = false;
    private int fontColor = Color.BLACK.getRGB();

    // Canvas
    private int bg1 = new Color(200, 200, 200).getRGB();
    private int bg2 = new Color(160, 160, 160).getRGB();

    // View
    private boolean showGrid = false;
    private boolean showRuler = true;
    private String rulerUnit = "PX";
    private boolean filmstripVisible = true;
    private String appMode = "ALPHA_EDITOR";

    // Zoom
    private double zoomMin = 0.05;
    private double zoomMax = 16.0;
    private double zoomStep = 0.10;
    private double zoomFactor = 1.08;

    // Window
    private int windowX = 100;
    private int windowY = 100;
    private int windowWidth = 1200;
    private int windowHeight = 800;
    private boolean windowMaximized = false;

    // Mouse
    private int mouseWheelSensitivity = 16;

    // Second gallery directories (per canvas)
    private String gallery2Dir0 = "";
    private String gallery2Dir1 = "";

    // Recent Files/Projects
    private List<String> recentProjects = new ArrayList<>();
    private List<String> recentFiles = new ArrayList<>();

    private AppSettings() {
    }

    public static AppSettings getInstance() {
        if (instance == null) {
            instance = new AppSettings();
        }
        return instance;
    }

    public static void load() throws IOException {
        if (instance == null) {
            instance = new AppSettings();
        }
        instance.loadFromFile();
    }

    private void loadFromFile() throws IOException {
        File file = AppPaths.getSettingsFile();
        if (!file.exists()) {
            return; // Datei existiert noch nicht, verwende Defaults
        }

        Map<String, String> data = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                // Parse: "key": "value" oder "key": number
                if (line.startsWith("\"") && line.contains("\":")) {
                    int colonIdx = line.indexOf(":");
                    if (colonIdx > 0) {
                        String key = line.substring(1, colonIdx - 1); // Key zwischen Anführungszeichen
                        String value = line.substring(colonIdx + 1).trim();

                        // Value-End: Komma oder } entfernen
                        if (value.endsWith(",")) value = value.substring(0, value.length() - 1);
                        if (value.endsWith("}")) value = value.substring(0, value.length() - 1);
                        value = value.trim();

                        // String-Werte: Anführungszeichen entfernen
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }

                        data.put(key, value);
                    }
                }
            }
        }

        // Parse alle bekannten Felder
        if (data.containsKey("primaryColor")) primaryColor = parseInt(data.get("primaryColor"));
        if (data.containsKey("secondaryColor")) secondaryColor = parseInt(data.get("secondaryColor"));
        if (data.containsKey("strokeWidth")) strokeWidth = parseInt(data.get("strokeWidth"));
        if (data.containsKey("antialias")) antialias = parseBoolean(data.get("antialias"));
        if (data.containsKey("fillMode")) fillMode = data.get("fillMode");
        if (data.containsKey("brushShape")) brushShape = data.get("brushShape");
        if (data.containsKey("activeTool")) activeTool = data.get("activeTool");

        if (data.containsKey("fontName")) fontName = data.get("fontName");
        if (data.containsKey("fontSize")) fontSize = parseInt(data.get("fontSize"));
        if (data.containsKey("textBold")) textBold = parseBoolean(data.get("textBold"));
        if (data.containsKey("textItalic")) textItalic = parseBoolean(data.get("textItalic"));
        if (data.containsKey("fontColor")) fontColor = parseInt(data.get("fontColor"));

        if (data.containsKey("bg1")) bg1 = parseInt(data.get("bg1"));
        if (data.containsKey("bg2")) bg2 = parseInt(data.get("bg2"));

        if (data.containsKey("showGrid")) showGrid = parseBoolean(data.get("showGrid"));
        if (data.containsKey("showRuler")) showRuler = parseBoolean(data.get("showRuler"));
        if (data.containsKey("rulerUnit")) rulerUnit = data.get("rulerUnit");
        if (data.containsKey("filmstrip")) filmstripVisible = parseBoolean(data.get("filmstrip"));
        if (data.containsKey("appMode")) appMode = data.get("appMode");

        if (data.containsKey("zoomMin")) zoomMin = parseDouble(data.get("zoomMin"));
        if (data.containsKey("zoomMax")) zoomMax = parseDouble(data.get("zoomMax"));
        if (data.containsKey("zoomStep")) zoomStep = parseDouble(data.get("zoomStep"));
        if (data.containsKey("zoomFactor")) zoomFactor = parseDouble(data.get("zoomFactor"));

        if (data.containsKey("windowX")) windowX = parseInt(data.get("windowX"));
        if (data.containsKey("windowY")) windowY = parseInt(data.get("windowY"));
        if (data.containsKey("windowW")) windowWidth = parseInt(data.get("windowW"));
        if (data.containsKey("windowH")) windowHeight = parseInt(data.get("windowH"));
        if (data.containsKey("maximized")) windowMaximized = parseBoolean(data.get("maximized"));

        if (data.containsKey("mouseWheelSensitivity")) mouseWheelSensitivity = parseInt(data.get("mouseWheelSensitivity"));

        if (data.containsKey("gallery2Dir0")) gallery2Dir0 = data.get("gallery2Dir0");
        if (data.containsKey("gallery2Dir1")) gallery2Dir1 = data.get("gallery2Dir1");

        // Parse recentProjects array
        if (data.containsKey("recentProjects")) {
            String arrStr = data.get("recentProjects");
            parseStringArray(arrStr, recentProjects);
        }

        // Parse recentFiles array
        if (data.containsKey("recentFiles")) {
            String arrStr = data.get("recentFiles");
            parseStringArray(arrStr, recentFiles);
        }
    }

    private static void parseStringArray(String arrStr, List<String> list) {
        if (arrStr == null || !arrStr.startsWith("[") || !arrStr.endsWith("]")) {
            return;
        }
        String content = arrStr.substring(1, arrStr.length() - 1).trim();
        if (content.isEmpty()) {
            return;
        }
        // Simple CSV parsing for quoted strings
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                if (c == '\\') current.append('\\');
                else if (c == '"') current.append('"');
                else current.append(c);
                escaped = false;
            } else if (c == '\\' && inQuotes) {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String item = current.toString().trim();
                if (!item.isEmpty()) list.add(item);
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String item = current.toString().trim();
        if (!item.isEmpty()) list.add(item);
    }

    public void save() throws IOException {
        File file = AppPaths.getSettingsFile();
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("{\n");

            writeField(writer, "primaryColor", primaryColor, true);
            writeField(writer, "secondaryColor", secondaryColor, true);
            writeField(writer, "strokeWidth", strokeWidth, true);
            writeField(writer, "antialias", antialias, true);
            writeField(writer, "fillMode", fillMode, true);
            writeField(writer, "brushShape", brushShape, true);
            writeField(writer, "activeTool", activeTool, true);

            writeField(writer, "fontName", fontName, true);
            writeField(writer, "fontSize", fontSize, true);
            writeField(writer, "textBold", textBold, true);
            writeField(writer, "textItalic", textItalic, true);
            writeField(writer, "fontColor", fontColor, true);

            writeField(writer, "bg1", bg1, true);
            writeField(writer, "bg2", bg2, true);

            writeField(writer, "showGrid", showGrid, true);
            writeField(writer, "showRuler", showRuler, true);
            writeField(writer, "rulerUnit", rulerUnit, true);
            writeField(writer, "filmstrip", filmstripVisible, true);
            writeField(writer, "appMode", appMode, true);

            writeField(writer, "zoomMin", zoomMin, true);
            writeField(writer, "zoomMax", zoomMax, true);
            writeField(writer, "zoomStep", zoomStep, true);
            writeField(writer, "zoomFactor", zoomFactor, true);

            writeField(writer, "windowX", windowX, true);
            writeField(writer, "windowY", windowY, true);
            writeField(writer, "windowW", windowWidth, true);
            writeField(writer, "windowH", windowHeight, true);
            writeField(writer, "maximized", windowMaximized, true);

            writeField(writer, "mouseWheelSensitivity", mouseWheelSensitivity, true);

            writeField(writer, "gallery2Dir0", gallery2Dir0, true);
            writeField(writer, "gallery2Dir1", gallery2Dir1, true);

            // recentProjects as JSON array
            writer.write("  \"recentProjects\": [");
            for (int i = 0; i < recentProjects.size(); i++) {
                writer.write("\"" + recentProjects.get(i).replace("\\", "\\\\") + "\"");
                if (i < recentProjects.size() - 1) writer.write(", ");
            }
            writer.write("],\n");

            // recentFiles as JSON array (last field, no comma)
            writer.write("  \"recentFiles\": [");
            for (int i = 0; i < recentFiles.size(); i++) {
                writer.write("\"" + recentFiles.get(i).replace("\\", "\\\\") + "\"");
                if (i < recentFiles.size() - 1) writer.write(", ");
            }
            writer.write("]\n");

            writer.write("\n}\n");
        }
    }

    private void writeField(BufferedWriter writer, String key, Object value, boolean comma) throws IOException {
        writer.write("  \"" + key + "\": ");
        if (value instanceof String) {
            writer.write("\"" + value + "\"");
        } else if (value instanceof Boolean) {
            writer.write(((Boolean) value) ? "true" : "false");
        } else {
            writer.write(value.toString());
        }
        if (comma) writer.write(",");
        writer.write("\n");
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean parseBoolean(String s) {
        return s != null && (s.trim().equalsIgnoreCase("true") || s.trim().equals("1"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Getter/Setter für alle Felder
    // ─────────────────────────────────────────────────────────────────

    public int getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(int c) { primaryColor = c; }

    public int getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(int c) { secondaryColor = c; }

    public int getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(int w) { strokeWidth = w; }

    public boolean isAntialias() { return antialias; }
    public void setAntialias(boolean a) { antialias = a; }

    public String getFillMode() { return fillMode; }
    public void setFillMode(String m) { fillMode = m; }

    public String getBrushShape() { return brushShape; }
    public void setBrushShape(String s) { brushShape = s; }

    public String getActiveTool() { return activeTool; }
    public void setActiveTool(String t) { activeTool = t; }

    public String getFontName() { return fontName; }
    public void setFontName(String n) { fontName = n; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int s) { fontSize = s; }

    public boolean isTextBold() { return textBold; }
    public void setTextBold(boolean b) { textBold = b; }

    public boolean isTextItalic() { return textItalic; }
    public void setTextItalic(boolean i) { textItalic = i; }

    public int getFontColor() { return fontColor; }
    public void setFontColor(int c) { fontColor = c; }

    public int getBg1() { return bg1; }
    public void setBg1(int c) { bg1 = c; }

    public int getBg2() { return bg2; }
    public void setBg2(int c) { bg2 = c; }

    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean s) { showGrid = s; }

    public boolean isShowRuler() { return showRuler; }
    public void setShowRuler(boolean s) { showRuler = s; }

    public String getRulerUnit() { return rulerUnit; }
    public void setRulerUnit(String u) { rulerUnit = u; }

    public boolean isFilmstripVisible() { return filmstripVisible; }
    public void setFilmstripVisible(boolean v) { filmstripVisible = v; }

    public String getAppMode() { return appMode; }
    public void setAppMode(String m) { appMode = m; }

    public double getZoomMin() { return zoomMin; }
    public void setZoomMin(double z) { zoomMin = z; }

    public double getZoomMax() { return zoomMax; }
    public void setZoomMax(double z) { zoomMax = z; }

    public double getZoomStep() { return zoomStep; }
    public void setZoomStep(double z) { zoomStep = z; }

    public double getZoomFactor() { return zoomFactor; }
    public void setZoomFactor(double z) { zoomFactor = z; }

    public int getWindowX() { return windowX; }
    public void setWindowX(int x) { windowX = x; }

    public int getWindowY() { return windowY; }
    public void setWindowY(int y) { windowY = y; }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int w) { windowWidth = w; }

    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int h) { windowHeight = h; }

    public boolean isWindowMaximized() { return windowMaximized; }
    public void setWindowMaximized(boolean m) { windowMaximized = m; }

    public int getMouseWheelSensitivity() { return mouseWheelSensitivity; }
    public void setMouseWheelSensitivity(int s) { mouseWheelSensitivity = s; }

    public List<String> getRecentProjects() { return new ArrayList<>(recentProjects); }
    public void setRecentProjects(List<String> l) { recentProjects = new ArrayList<>(l); }
    public void addRecentProject(String path) {
        recentProjects.remove(path);
        recentProjects.add(0, path);
        while (recentProjects.size() > 10) recentProjects.remove(recentProjects.size() - 1);
    }

    public String getGallery2Dir0() { return gallery2Dir0; }
    public void setGallery2Dir0(String d) { gallery2Dir0 = d != null ? d : ""; }

    public String getGallery2Dir1() { return gallery2Dir1; }
    public void setGallery2Dir1(String d) { gallery2Dir1 = d != null ? d : ""; }

    public List<String> getRecentFiles() { return new ArrayList<>(recentFiles); }
    public void setRecentFiles(List<String> l) { recentFiles = new ArrayList<>(l); }
    public void addRecentFile(String path) {
        recentFiles.remove(path);
        recentFiles.add(0, path);
        while (recentFiles.size() > 10) recentFiles.remove(recentFiles.size() - 1);
    }
}
