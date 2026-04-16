package paint;

/**
 * Page-layout settings for one canvas side (Canvas I or II).
 * Holds margins (in mm), decoration flags, and the current snap mode for
 * layer movement.
 *
 * One instance lives inside each {@link PageLayoutToolbar}; it is queried by
 * {@link BookController} when creating a new page and by the canvas overlay
 * renderer when painting margin guides.
 */
class PageLayout {

    // ── Margins (millimetres, DIN A4 defaults) ─────────────────────────────
    int marginLeft   = 20;
    int marginRight  = 20;
    int marginTop    = 25;
    int marginBottom = 25;

    // ── Page decoration toggles ───────────────────────────────────────────
    boolean headerVisible     = false;
    boolean footerVisible     = false;
    boolean pageNumberVisible = false;

    // ── Layer snap mode ───────────────────────────────────────────────────
    SnapMode snapMode = SnapMode.NONE;

    enum SnapMode { NONE, SNAP_TO_LAYER, SNAP_TO_MARGIN }

    // ── Pixel conversions (96 DPI) ────────────────────────────────────────

    static int mmToPx(int mm) {
        return (int) Math.round(mm * 96.0 / 25.4);
    }

    int marginLeftPx()   { return mmToPx(marginLeft);   }
    int marginRightPx()  { return mmToPx(marginRight);  }
    int marginTopPx()    { return mmToPx(marginTop);    }
    int marginBottomPx() { return mmToPx(marginBottom); }
}
