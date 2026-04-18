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
    /** Format name: "A3","A4","A5","A6","Letter","Legal","Custom" */
    String  paperFormat = "A4";
    boolean landscape   = false;

    // Paper format lookup (mm, portrait width x height)
    static final String[] FORMAT_NAMES = {"A3","A4","A5","A6","Letter","Legal"};
    static final int[][] FORMAT_MM = {{297,420},{210,297},{148,210},{105,148},{216,279},{216,356}};

    /** Returns {widthMm, heightMm} for this layout (applies orientation). */
    int[] formatMm() {
        for (int i = 0; i < FORMAT_NAMES.length; i++) {
            if (FORMAT_NAMES[i].equals(paperFormat)) {
                int w = FORMAT_MM[i][0], h = FORMAT_MM[i][1];
                return landscape ? new int[]{h, w} : new int[]{w, h};
            }
        }
        return null;
    }

    /** Returns {widthPx, heightPx} at 96 DPI for this layout. */
    int[] formatPx() {
        int[] mm = formatMm();
        if (mm == null) return null;
        return new int[]{mmToPx(mm[0]), mmToPx(mm[1])};
    }

    // Pixel conversions (96 DPI)
    static int mmToPx(int mm) {
        return (int) Math.round(mm * 96.0 / 25.4);
    }

    int marginLeftPx()   { return mmToPx(marginLeft);   }
    int marginRightPx()  { return mmToPx(marginRight);  }
    int marginTopPx()    { return mmToPx(marginTop);    }
    int marginBottomPx() { return mmToPx(marginBottom); }
}
