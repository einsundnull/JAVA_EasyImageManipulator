package paint;

/**
 * Page-layout settings for one canvas side (Canvas I or II).
 * Holds margins (in mm), decoration flags, snap mode, frame-layer movement,
 * and paper format.
 */
class PageLayout {

    // Margins (mm, DIN A4 defaults)
    int marginLeft   = 20;
    int marginRight  = 20;
    int marginTop    = 25;
    int marginBottom = 25;

    // Page decoration toggles
    boolean headerVisible     = false;
    boolean footerVisible     = false;
    boolean pageNumberVisible = false;

    // Layer snap mode
    SnapMode snapMode = SnapMode.NONE;

    enum SnapMode { NONE, SNAP_TO_LAYER, SNAP_TO_MARGIN }

    // Frame TextLayer movement lock
    boolean frameLayerMovable = false;

    // Paper format
    /**
     * Anzeigename des Formats, z. B. {@code "A4"}, {@code "Letter"},
     * {@code "DIN lang"} — oder {@code "Custom"} für freie Maße.
     * <p><b>Dieser String wird geschrieben</b> ({@code page_NNN.layout},
     * Schlüssel {@code paperFormat}). Er darf sich nicht ändern.
     */
    String  paperFormat = "A4";
    boolean landscape   = false;

    /**
     * Die wählbaren Formatnamen — <b>abgeleitet</b> aus
     * {@link book.PaperFormat#selectable()}, seit 2026-08-01 nicht mehr
     * danebengeführt.
     * <p>Vorher standen hier sechs Namen (A3–A6, Letter, Legal), während
     * {@code book.PaperFormat} elf andere kannte: A5/A6 fehlten dort, A0–A2
     * und Tabloid fehlten hier. Zwei Listen, zwei Wahrheiten — genau das
     * Muster, das §25 als Fehlerursache führt.
     */
    static final String[] FORMAT_NAMES = book.PaperFormat.selectableNames();

    /**
     * Returns {widthMm, heightMm} for this layout (applies orientation),
     * or {@code null} if the format name is unknown.
     * <p><b>{@code null} ist ein gültiges Ergebnis und wird nicht zur
     * Ausnahme gemacht:</b> eine ältere {@code .layout}-Datei darf einen
     * Formatnamen tragen, den es nicht mehr gibt — sie muss trotzdem laden.
     */
    int[] formatMm() {
        book.PaperFormat.Format f = book.PaperFormat.byDisplayName(paperFormat);
        if (f == null) return null;
        int w = landscape ? f.getWidthLandscape()  : f.getWidthPortrait();
        int h = landscape ? f.getHeightLandscape() : f.getHeightPortrait();
        return new int[]{w, h};
    }

    /** Returns {widthPx, heightPx} at 96 DPI for this layout. */
    int[] formatPx() {
        int[] mm = formatMm();
        if (mm == null) return null;
        return new int[]{mmToPx(mm[0]), mmToPx(mm[1])};
    }

    // Pixel conversions (96 DPI) — der Faktor steht in book.PaperFormat,
    // nicht ein zweites Mal hier.
    static int mmToPx(int mm) {
        return (int) Math.round(book.PaperFormat.mmToPx(mm));
    }

    int marginLeftPx()   { return mmToPx(marginLeft);   }
    int marginRightPx()  { return mmToPx(marginRight);  }
    int marginTopPx()    { return mmToPx(marginTop);    }
    int marginBottomPx() { return mmToPx(marginBottom); }
}
