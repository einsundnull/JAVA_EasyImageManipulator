package paint;

import java.io.File;

/**
 * Zentrale Verwaltung aller Anwendungspfade.
 * Verwendet %APPDATA%\TransparencyTool\ als Standardspeicherort (Windows Best Practice).
 * Fallback auf user.home wenn %APPDATA% nicht verfügbar.
 */
public class AppPaths {

    private static File appDataDir;
    private static File projectsDir;
    private static File settingsDir;
    private static File lastProjectsDir;
    private static File assetsDir;
    private static File mapsDir;
    private static File settingsFile;

    static {
        initializePaths();
    }

    private static void initializePaths() {
        // %APPDATA% auslesen, Fallback auf user.home
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }

        appDataDir = new File(appData, "TransparencyTool");
        projectsDir = new File(appDataDir, "projects");
        settingsDir = new File(appDataDir, "settings");
        lastProjectsDir = new File(settingsDir, "lastProjects");
        assetsDir = new File(appDataDir, "assets");
        mapsDir = new File(appDataDir, "maps");
        settingsFile = new File(settingsDir, "default.txt");

        // Verzeichnisse anlegen wenn nicht vorhanden
        if (!appDataDir.exists()) appDataDir.mkdirs();
        if (!projectsDir.exists()) projectsDir.mkdirs();
        if (!settingsDir.exists()) settingsDir.mkdirs();
        if (!lastProjectsDir.exists()) lastProjectsDir.mkdirs();
        if (!assetsDir.exists()) assetsDir.mkdirs();
        if (!mapsDir.exists()) mapsDir.mkdirs();
    }

    /**
     * Gibt das Haupt-Anwendungsverzeichnis zurück: %APPDATA%\TransparencyTool\
     */
    public static File getAppDataDir() {
        return appDataDir;
    }

    /**
     * Gibt das settings/-Verzeichnis zurück.
     */
    public static File getSettingsDir() {
        return settingsDir;
    }

    /**
     * Gibt die settings/default.txt-Datei zurück.
     */
    public static File getSettingsFile() {
        return settingsFile;
    }

    /**
     * Gibt das settings/lastProjects/-Verzeichnis zurück.
     */
    public static File getLastProjectsDir() {
        return lastProjectsDir;
    }

    /**
     * Gibt die settings/lastProjects/{category}.txt-Datei zurück.
     */
    public static File getLastProjectsFile(String category) {
        return new File(lastProjectsDir, category + ".txt");
    }

    /**
     * Gibt das assets/-Verzeichnis zurück.
     */
    public static File getAssetsDir() {
        return assetsDir;
    }

    /**
     * Gibt das projects/-Verzeichnis zurück.
     */
    public static File getProjectsDir() {
        return projectsDir;
    }

    /**
     * Gibt das Verzeichnis für ein bestimmtes Projekt zurück.
     * Format: %APPDATA%\TransparencyTool\projects\{projectName}\
     */
    public static File getProjectDir(String projectName) {
        File dir = new File(projectsDir, projectName);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Gibt das scenes/-Verzeichnis für ein bestimmtes Projekt zurück.
     * Format: %APPDATA%\TransparencyTool\projects\{projectName}\scenes\
     */
    public static File getProjectScenesDir(String projectName) {
        File dir = new File(getProjectDir(projectName), "scenes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Gibt die project.json-Datei für ein bestimmtes Projekt zurück.
     */
    public static File getProjectJsonFile(String projectName) {
        return new File(getProjectDir(projectName), "project.json");
    }

    /**
     * Generiert einen eindeutigen Szenen-Dateinamen basierend auf dem Bild-Dateinamen.
     * Beispiel: C:\Bilder\meinbild.png → "meinbild.json"
     */
    public static String getSceneFileName(File imageFile) {
        String name = imageFile.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }
        return name + ".json";
    }

    /**
     * Gibt die Szenen-JSON-Datei für ein Bild innerhalb eines Projekts zurück.
     */
    public static File getSceneJsonFile(String projectName, File imageFile) {
        String fileName = getSceneFileName(imageFile);
        return new File(getProjectScenesDir(projectName), fileName);
    }

    /**
     * Gibt das maps/-Verzeichnis zurück.
     */
    public static File getMapsDir() {
        return mapsDir;
    }

    /**
     * Gibt die maps/{language}.json-Datei zurück.
     */
    public static File getMapFile(String language) {
        return new File(mapsDir, language + ".json");
    }
}
