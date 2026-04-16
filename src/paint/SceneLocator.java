package paint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Verwaltung von Szenen-Verzeichnissen für beide TransparencyTool und GameII.
 * Ermöglicht Cross-Application Szenen-Zugriff mit Drag & Drop zwischen den Anwendungen.
 *
 * Struktur:
 * - TransparencyTool: C:\Users\pc\AppData\Roaming\TransparencyTool\projects\{projectName}\scenes\
 * - GameII: C:\Users\pc\Games\{gameName}\scenes\
 *
 * Unterstützte Formate:
 * - JSON (neu, Standard)
 * - TXT (legacy, GameII-Format - nur zum Lesen, nicht implementiert)
 */
public class SceneLocator {

    // =====================================================
    // TransparencyTool Szenen (AppData)
    // =====================================================

    /**
     * Gibt das Szenen-Verzeichnis eines TransparencyTool-Projekts zurück.
     * Path: C:\Users\pc\AppData\Roaming\TransparencyTool\projects\{projectName}\scenes\
     */
    public static File getToolScenesDir(String projectName) {
        return AppPaths.getProjectScenesDir(projectName);
    }

    /**
     * Gibt alle verfügbaren TransparencyTool-Projekte zurück.
     */
    public static List<String> getToolProjects() {
        List<String> projects = new ArrayList<>();
        File projectsDir = AppPaths.getProjectsDir();
        if (projectsDir.exists()) {
            File[] dirs = projectsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    projects.add(dir.getName());
                }
            }
        }
        return projects;
    }

    /**
     * Gibt alle Szenen eines TransparencyTool-Projekts zurück.
     * Sucht nach Verzeichnissen mit {sceneName}.txt Dateien.
     */
    public static List<File> getToolScenes(String projectName) {
        List<File> scenes = new ArrayList<>();
        File scenesDir = getToolScenesDir(projectName);
        if (scenesDir.exists()) {
            File[] dirs = scenesDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    // Suche nach {dirName}.txt innerhalb des Verzeichnisses
                    File sceneFile = new File(dir, dir.getName() + ".txt");
                    if (sceneFile.exists()) {
                        scenes.add(sceneFile);
                    }
                }
            }
        }
        return scenes;
    }

    // =====================================================
    // GameII Szenen (Games/{gameName}/scenes/)
    // =====================================================

    /**
     * Gibt das Basis-Games-Verzeichnis zurück.
     * Path: C:\Users\pc\Games\ (oder wo auch immer Games installiert ist)
     */
    public static File getGamesDir() {
        // Fallback: suche im User-Home oder in bekannten Locations
        File potential = new File(System.getProperty("user.home"), "Games");
        if (potential.exists()) return potential;

        // Fallback 2: Check C:\Users\pc\Games
        File cDrive = new File("C:\\Users\\pc\\Games");
        if (cDrive.exists()) return cDrive;

        // Fallback 3: erstelle es im User-Home
        potential.mkdirs();
        return potential;
    }

    /**
     * Gibt das Szenen-Verzeichnis eines GameII-Spiels zurück.
     * Path: {GamesDir}\{gameName}\scenes\
     */
    public static File getGameScenesDir(String gameName) {
        File gameDir = new File(getGamesDir(), gameName);
        File scenesDir = new File(gameDir, "scenes");
        if (!scenesDir.exists()) {
            scenesDir.mkdirs();
        }
        return scenesDir;
    }

    /**
     * Gibt alle verfügbaren GameII-Spiele zurück (Verzeichnisse in Games/).
     */
    public static List<String> getAvailableGames() {
        List<String> games = new ArrayList<>();
        File gamesDir = getGamesDir();
        if (gamesDir.exists()) {
            File[] dirs = gamesDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    games.add(dir.getName());
                }
            }
        }
        return games;
    }

    /**
     * Gibt alle Szenen eines GameII-Spiels zurück.
     * Unterstützt beide Formate (JSON und TXT Legacy).
     */
    public static List<File> getGameScenes(String gameName) {
        List<File> scenes = new ArrayList<>();
        File scenesDir = getGameScenesDir(gameName);
        if (scenesDir.exists()) {
            File[] files = scenesDir.listFiles((dir, name) ->
                name.endsWith(".json") || name.endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    scenes.add(f);
                }
            }
        }
        return scenes;
    }

    // =====================================================
    // Cross-Application Utilities
    // =====================================================

    /**
     * Format-Enum für Szenen-Dateien.
     */
    public enum SceneFormat {
        JSON("json", "TransparencyTool / GameII Modern"),
        TXT("txt", "GameII Legacy Format");

        public final String extension;
        public final String description;

        SceneFormat(String ext, String desc) {
            this.extension = ext;
            this.description = desc;
        }

        public static SceneFormat fromFile(File f) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".json")) return JSON;
            if (name.endsWith(".txt")) return TXT;
            return null;
        }
    }

    /**
     * Bestimmt das Format einer Szenen-Datei.
     */
    public static SceneFormat getSceneFormat(File sceneFile) {
        return SceneFormat.fromFile(sceneFile);
    }

    /**
     * Kopiert eine Szene von einer Location zu einer anderen.
     * @param source Quell-Szenen-Datei
     * @param targetDir Ziel-Verzeichnis
     * @return true wenn erfolgreich
     */
    public static boolean copyScene(File source, File targetDir) {
        if (!source.exists()) return false;
        if (!targetDir.exists()) targetDir.mkdirs();

        try {
            File targetFile = new File(targetDir, source.getName());
            java.nio.file.Files.copy(
                source.toPath(),
                targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Fehler beim Kopieren der Szene: " + e.getMessage());
            return false;
        }
    }

    /**
     * Löscht eine Szenen-Datei.
     */
    public static boolean deleteScene(File sceneFile) {
        if (!sceneFile.exists()) return false;
        return sceneFile.delete();
    }

    /**
     * Gibt eine lesbare Beschreibung einer Szene zurück.
     * Format: "Projekt: myproject / Spiel: mygame"
     */
    public static String getSceneSource(File sceneFile) {
        String path = sceneFile.getAbsolutePath();

        // Überprüfe ob es eine TransparencyTool-Szene ist
        String appDataPath = AppPaths.getAppDataDir().getAbsolutePath();
        if (path.startsWith(appDataPath)) {
            try {
                String relative = path.substring(appDataPath.length());
                String[] parts = relative.split("\\\\");
                // Format: \projects\{projectName}\scenes\{filename}
                if (parts.length >= 4) {
                    String projectName = parts[2];
                    return "TransparencyTool Project: " + projectName;
                }
            } catch (Exception ignored) {}
        }

        // Überprüfe ob es eine GameII-Szene ist
        String gamesPath = getGamesDir().getAbsolutePath();
        if (path.startsWith(gamesPath)) {
            try {
                String relative = path.substring(gamesPath.length());
                String[] parts = relative.split("\\\\");
                // Format: \{gameName}\scenes\{filename}
                if (parts.length >= 3) {
                    String gameName = parts[1];
                    return "GameII Game: " + gameName;
                }
            } catch (Exception ignored) {}
        }

        return "Unknown source";
    }

    // =====================================================
    // AppData-Games (GameII-Scenes unter %APPDATA%\TransparencyTool\Games\)
    // =====================================================

    /**
     * Gibt das Basis-Verzeichnis für GameII-Spiele im AppData-Ordner zurück.
     * Pfad: %APPDATA%\TransparencyTool\Games\
     */
    public static File getAppDataGamesDir() {
        return new File(AppPaths.getAppDataDir(), "Games");
    }

    /**
     * Gibt alle GameII-Spielnamen zurück, die unter AppData\Games\ liegen.
     */
    public static List<String> getAppDataGames() {
        List<String> games = new ArrayList<>();
        File dir = getAppDataGamesDir();
        if (dir.exists()) {
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs != null) for (File d : subdirs) games.add(d.getName());
        }
        return games;
    }

    /**
     * Gibt alle Szenen eines GameII-Spiels zurück (AppData-Pfad).
     * Sucht nach Verzeichnissen mit &lt;dirName&gt;.txt (= Scene-Manifest).
     */
    public static List<File> getAppDataGameScenes(String gameName) {
        List<File> scenes = new ArrayList<>();
        File scenesDir = new File(new File(getAppDataGamesDir(), gameName), "scenes");
        if (!scenesDir.exists()) return scenes;
        File[] dirs = scenesDir.listFiles(File::isDirectory);
        if (dirs == null) return scenes;
        for (File dir : dirs) {
            File sceneFile = new File(dir, dir.getName() + ".txt");
            if (sceneFile.exists()) {
                scenes.add(sceneFile);
            } else if (new File(dir, "sprites").isDirectory()) {
                // GameII scene without manifest — use directory itself
                scenes.add(dir);
            }
        }
        return scenes;
    }

    // =====================================================
    // Legacy Format Support (Struktur-Skeleton)
    // =====================================================

    /**
     * LEGACY: Liest eine TXT-Szenen-Datei im GameII-Format.
     * Dieses Format wird hier nur als Struktur-Skeleton definiert.
     * Implementierung: siehe GameLegacySceneReader.java
     *
     * Format-Beispiel:
     * #Name:
     * -scene_name
     *
     * #Image:
     * -path/to/image.png
     *
     * #Entities:
     * -Entity: id=1, x=100, y=50, type=sprite, ...
     *
     * @param txtFile Quell-TXT-Datei
     * @return geparste Szenen-Daten (null wenn nicht implementiert)
     */
    public static Object readLegacyScene(File txtFile) {
        // LEGACY SUPPORT - nicht implementiert, nur Struktur
        // TODO: Implementieren in GameLegacySceneReader.java wenn nötig
        System.out.println("[INFO] Legacy TXT-Format erkannt: " + txtFile.getName());
        System.out.println("[TODO] GameLegacySceneReader.read(File) implementieren");
        return null;
    }

    /**
     * LEGACY: Schreibt eine Szene im GameII-TXT-Format.
     * Dieses Format wird hier nur als Struktur-Skeleton definiert.
     * Implementierung: siehe GameLegacySceneWriter.java
     *
     * @param scene Szenen-Daten
     * @param targetFile Ziel-TXT-Datei
     * @return true wenn erfolgreich
     */
    public static boolean writeLegacyScene(Object scene, File targetFile) {
        // LEGACY SUPPORT - nicht implementiert, nur Struktur
        // TODO: Implementieren in GameLegacySceneWriter.java wenn nötig
        System.out.println("[INFO] Legacy TXT-Format wird geschrieben: " + targetFile.getName());
        System.out.println("[TODO] GameLegacySceneWriter.write(Object, File) implementieren");
        return false;
    }
}
