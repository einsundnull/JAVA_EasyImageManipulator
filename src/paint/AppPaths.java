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
        settingsFile = new File(appDataDir, "settings.json");

        // Verzeichnisse anlegen wenn nicht vorhanden
        if (!appDataDir.exists()) appDataDir.mkdirs();
        if (!projectsDir.exists()) projectsDir.mkdirs();
    }

    /**
     * Gibt das Haupt-Anwendungsverzeichnis zurück: %APPDATA%\TransparencyTool\
     */
    public static File getAppDataDir() {
        return appDataDir;
    }

    /**
     * Gibt die settings.json-Datei zurück.
     */
    public static File getSettingsFile() {
        return settingsFile;
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
}
