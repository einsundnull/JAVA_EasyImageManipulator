package paint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Reads and writes per-page layout manifests.
 *
 * <p>The manifest file sits next to the page PNG:
 * <pre>
 *   pages/
 *     page_001.png
 *     page_001.layout   ← written by this class
 * </pre>
 *
 * <p>Format (plain text, key: value):
 * <pre>
 *   #PageLayout:
 *   marginLeft: 20
 *   marginRight: 20
 *   marginTop: 25
 *   marginBottom: 25
 *   headerVisible: false
 *   footerVisible: false
 *   pageNumberVisible: false
 *   snapMode: NONE
 * </pre>
 */
class PageLayoutManifest {

    private PageLayoutManifest() {}

    // ── File location ─────────────────────────────────────────────────────────

    /** Returns the {@code .layout} file that belongs to a page PNG. */
    static File layoutFile(File pageFile) {
        String name = pageFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(pageFile.getParentFile(), base + ".layout");
    }

    /** Returns true if {@code file} is a book page (parent directory is named "pages"). */
    static boolean isBookPage(File file) {
        return file != null
                && file.getParentFile() != null
                && "pages".equals(file.getParentFile().getName());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Reads the layout manifest for {@code pageFile}.
     * Returns defaults if no manifest exists yet.
     */
    static PageLayout read(File pageFile) {
        PageLayout pl = new PageLayout();
        File lf = layoutFile(pageFile);
        if (!lf.exists()) return pl;
        try {
            for (String raw : Files.readAllLines(lf.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                switch (key) {
                    case "marginLeft"        -> pl.marginLeft        = parseInt(val, pl.marginLeft);
                    case "marginRight"       -> pl.marginRight       = parseInt(val, pl.marginRight);
                    case "marginTop"         -> pl.marginTop         = parseInt(val, pl.marginTop);
                    case "marginBottom"      -> pl.marginBottom      = parseInt(val, pl.marginBottom);
                    case "headerVisible"     -> pl.headerVisible     = Boolean.parseBoolean(val);
                    case "footerVisible"     -> pl.footerVisible     = Boolean.parseBoolean(val);
                    case "pageNumberVisible" -> pl.pageNumberVisible = Boolean.parseBoolean(val);
                    case "frameLayerMovable" -> pl.frameLayerMovable = Boolean.parseBoolean(val);
                    case "paperFormat"       -> pl.paperFormat       = val;
                    case "landscape"         -> pl.landscape         = Boolean.parseBoolean(val);
                    case "snapMode"          -> {
                        try { pl.snapMode = PageLayout.SnapMode.valueOf(val); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (IOException ignored) {}
        return pl;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Writes the layout manifest for {@code pageFile}. */
    static void write(File pageFile, PageLayout pl) {
        File lf = layoutFile(pageFile);
        List<String> lines = List.of(
            "#PageLayout:",
            "marginLeft: "        + pl.marginLeft,
            "marginRight: "       + pl.marginRight,
            "marginTop: "         + pl.marginTop,
            "marginBottom: "      + pl.marginBottom,
            "headerVisible: "     + pl.headerVisible,
            "footerVisible: "     + pl.footerVisible,
            "pageNumberVisible: " + pl.pageNumberVisible,
            "frameLayerMovable: " + pl.frameLayerMovable,
            "paperFormat: "       + pl.paperFormat,
            "landscape: "         + pl.landscape,
            "snapMode: "          + pl.snapMode.name()
        );
        try {
            Files.write(lf.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[PageLayoutManifest] Schreibfehler: " + ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }
}
