package paint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Singleton store for CardEntry objects with folder-based persistence.
 * Folders live under %APPDATA%/TransparencyTool/cardfolders/<name>/cards.txt
 */
class CardListStore {

    private static final String DEFAULT_FOLDER = "default";

    private static CardListStore instance;
    private final List<CardEntry> entries = new ArrayList<>();
    private String currentFolder = DEFAULT_FOLDER;

    private CardListStore() {
        ensureFolderExists(DEFAULT_FOLDER);
        String saved = AppSettings.getInstance().getCardCurrentFolder();
        if (saved != null && !saved.isBlank()) currentFolder = saved;
        load();
    }

    static CardListStore get() {
        if (instance == null) instance = new CardListStore();
        return instance;
    }

    List<CardEntry> entries() { return entries; }

    String currentFolder() { return currentFolder; }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    void add(CardEntry e) { entries.add(e); save(); }

    void remove(CardEntry e) { entries.remove(e); save(); }

    // ── Folder management ─────────────────────────────────────────────────────

    /** Returns all existing folder names, sorted. */
    List<String> listFolders() {
        File root = foldersRoot();
        File[] dirs = root.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File d : dirs) names.add(d.getName());
        }
        if (!names.contains(DEFAULT_FOLDER)) names.add(0, DEFAULT_FOLDER);
        return names;
    }

    /** Creates a new folder (no-op if it already exists). */
    void createFolder(String name) {
        ensureFolderExists(name);
    }

    /** Switches to the given folder, loading its cards. */
    void switchFolder(String name) {
        save(); // save current before switching
        currentFolder = name;
        ensureFolderExists(name);
        AppSettings.getInstance().setCardCurrentFolder(name);
        try { AppSettings.getInstance().save(); } catch (Exception ex) { /* ignore */ }
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    void save() {
        File f = cardsFile(currentFolder);
        f.getParentFile().mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (CardEntry e : entries) {
                w.write("==CARD=="); w.newLine();
                w.write("id="     + escape(e.id));     w.newLine();
                w.write("textI="  + escape(e.textI));  w.newLine();
                w.write("textII=" + escape(e.textII)); w.newLine();
            }
        } catch (IOException ex) {
            System.err.println("[CardListStore] save error: " + ex.getMessage());
        }
    }

    private void load() {
        entries.clear();
        File f = cardsFile(currentFolder);
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            String id = null, ti = "", tii = "";
            while ((line = r.readLine()) != null) {
                if (line.equals("==CARD==")) {
                    if (id != null) entries.add(new CardEntry(id, ti, tii));
                    id = null; ti = ""; tii = "";
                } else if (line.startsWith("id="))     { id  = unescape(line.substring(3)); }
                  else if (line.startsWith("textI="))  { ti  = unescape(line.substring(6)); }
                  else if (line.startsWith("textII=")) { tii = unescape(line.substring(7)); }
            }
            if (id != null) entries.add(new CardEntry(id, ti, tii));
        } catch (IOException ex) {
            System.err.println("[CardListStore] load error: " + ex.getMessage());
        }
    }

    // ── Path helpers ──────────────────────────────────────────────────────────

    private static File foldersRoot() {
        File f = new File(AppPaths.getSettingsDir(), "cardfolders");
        f.mkdirs();
        return f;
    }

    private static File cardsFile(String folder) {
        return new File(new File(foldersRoot(), folder), "cards.txt");
    }

    private static void ensureFolderExists(String name) {
        new File(foldersRoot(), name).mkdirs();
    }

    // ── Escape ────────────────────────────────────────────────────────────────

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (slash) { sb.append(c == 'n' ? '\n' : c); slash = false; }
            else if (c == '\\') slash = true;
            else sb.append(c);
        }
        return sb.toString();
    }
}
