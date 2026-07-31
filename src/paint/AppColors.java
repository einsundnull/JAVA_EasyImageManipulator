package paint;

import java.awt.Color;

/**
 * Shared color palette used by all UI classes.
 * Single source of truth – change here, affects everywhere.
 */
public final class AppColors {
    private AppColors() {}

    public static final Color BG_DARK        = new Color(30,  30,  30);
    public static final Color BG_PANEL       = new Color(45,  45,  45);
    public static final Color BG_TOOLBAR     = new Color(38,  38,  38);
    public static final Color ACCENT         = new Color(0,  150, 255);
    public static final Color ACCENT_HOVER   = new Color(0,  180, 255);
    public static final Color ACCENT_ACTIVE  = new Color(0,  120, 220);
    public static final Color BTN_BG         = new Color(60,  60,  60);
    public static final Color BTN_HOVER      = new Color(80,  80,  80);
    public static final Color BTN_ACTIVE     = new Color(0,  130, 230);
    public static final Color TEXT           = new Color(220, 220, 220);
    public static final Color TEXT_MUTED     = new Color(140, 140, 140);
    public static final Color BORDER         = new Color(70,  70,  70);
    public static final Color SUCCESS        = new Color(60,  180,  80);
    public static final Color SUCCESS_HOVER  = new Color(80,  200, 100);
    public static final Color DANGER         = new Color(200,  60,  60);
    public static final Color DANGER_HOVER   = new Color(220,  80,  80);
    public static final Color WARNING        = new Color(220, 160,   0);
    public static final Color HANDLE_BAR_TOP = new Color(28, 28, 28);
    public static final Color TILE_ACTIVE_BG = new Color(28, 52, 28);
    public static final Color TILE_HOVER_BG  = new Color(58, 58, 58);
    public static final Color TILE_DEFAULT_BG= new Color(48, 48, 48);
    public static final Color TILE_PLACEHOLDER= new Color(55, 55, 55);
    public static final Color SELECTION      = new Color(255, 140, 0);

    // ── Ergänzt 2026-07-31 bei der Migration von UIComponentFactory (§21) ────
    // Alle vier standen dort als Inline-Literal. Werte unverändert übernommen.

    /** Titelleiste eines Dialogs. Dunkler als BG_PANEL, heller als HANDLE_BAR_TOP. */
    public static final Color BG_TITLEBAR    = new Color(35, 35, 35);

    /** Runder Navigationsknopf über dem Bild (Vor/Zurück): Grundzustand. */
    public static final Color NAV_BG          = new Color(0, 0, 0, 110);
    /** Navigationsknopf unter dem Mauszeiger. */
    public static final Color NAV_BG_HOVER    = new Color(255, 255, 255, 55);
    /** Navigationsknopf, wenn es nichts zu blättern gibt. */
    public static final Color NAV_BG_DISABLED = new Color(0, 0, 0, 30);

    // ── Ergänzt 2026-07-31, Migration von UIBuilder ──────────────────────────
    /** Eckstück zwischen waagerechtem und senkrechtem Lineal. */
    public static final Color BG_RULER_CORNER = new Color(50, 50, 50);

    // ── Ergänzt 2026-07-31, Migration von TextToolbar ────────────────────────
    // Zwei davon teilen ihr RGB mit einem bestehenden Token und sind trotzdem
    // eigene Token: §21 verlangt, vor dem Zusammenlegen die BEDEUTUNG zu
    // prüfen, nicht den Wert. Wer sie zusammenlegt, koppelt Dinge, die sich
    // unabhängig ändern dürfen.

    /**
     * Hintergrund von Eingabefeldern (Auswahllisten, Zahlenfelder).
     * Gleiches RGB wie {@link #BG_RULER_CORNER}, aber <b>nicht dasselbe
     * Token</b> — ein Eingabefeld ist kein Lineal-Eckstück.
     */
    public static final Color BG_INPUT       = new Color(50, 50, 50);

    /**
     * Umschaltknopf im Zustand „aus".
     * Gleiches RGB wie {@link #TILE_PLACEHOLDER}, aber <b>nicht dasselbe
     * Token</b> — ein Knopf ist keine Platzhalter-Kachel.
     */
    public static final Color BTN_TOGGLE_OFF = new Color(55, 55, 55);

    /**
     * Kleine Beschriftungen in Werkzeugleisten („Schrift:", „Größe:").
     * Heller als {@link #TEXT_MUTED} (140), dunkler als {@link #TEXT} (220).
     */
    public static final Color TEXT_MINI      = new Color(160, 160, 160);

    // ── Ergänzt 2026-07-31, Migration von BaseSidebarPanel ───────────────────
    // Betrifft alle sechs Seitenleisten-Panels, die von BaseSidebarPanel erben.

    /** Kopfzeile einer Seitenleiste (Titel + Knöpfe). Heller als die Liste darunter. */
    public static final Color BG_SIDEBAR_HEADER = new Color(42, 42, 42);
    /** Listenfläche einer Seitenleiste unter der Kopfzeile. */
    public static final Color BG_SIDEBAR_LIST   = new Color(36, 36, 36);
}
