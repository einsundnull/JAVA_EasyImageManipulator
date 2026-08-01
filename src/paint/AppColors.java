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

    // ── Ergänzt 2026-07-31, Migration von NewImageDialog ─────────────────────

    /** Umriss der Seitenvorschau. Heller als {@link #BORDER} (70). */
    public static final Color BORDER_PAGE   = new Color(90, 90, 90);

    /**
     * Gestrichelte Randlinien in der Seitenvorschau.
     * <p><b>Abgeleitet</b> aus {@link #ACCENT_ACTIVE} statt als zweites
     * RGB-Tripel getippt (§21). Ändert sich der Akzent, ändert sich die
     * Randlinie mit.
     */
    public static final Color MARGIN_GUIDE  = new Color(
            ACCENT_ACTIVE.getRed(), ACCENT_ACTIVE.getGreen(), ACCENT_ACTIVE.getBlue(), 170);

    /**
     * Beschriftung der Randmaße in der Seitenvorschau („12mm").
     *
     * <p><b>Achtung, vermutlich ein alter Tippfehler:</b> der Wert ist
     * {@code (0,140,255)} und liegt damit <b>10 Grünwerte neben</b>
     * {@link #ACCENT} {@code (0,150,255)}. Ein sichtbarer Unterschied ist das
     * nicht, gewollt wirkt es aber auch nicht. <b>Unverändert übernommen</b> —
     * die Migration ist werterhaltend, und eine Angleichung an {@code ACCENT}
     * wäre eine Design-Entscheidung, keine Aufräumarbeit. Wer sie trifft,
     * ersetzt dieses Token durch {@code ACCENT} und löscht es hier.
     */
    public static final Color MARGIN_LABEL  = new Color(0, 140, 255);

    // ── Ergänzt 2026-07-31, Migration von TileGalleryPanel ───────────────────

    /**
     * Rahmen einer Kachel, die zur Mehrfachauswahl gehört (gelb).
     * <p>Nicht {@link #SELECTION} (orange) — das ist die <i>einzelne</i>
     * Auswahl. Zwei Zustände, zwei Farben, zwei Token.
     */
    public static final Color SELECTION_MULTI = new Color(255, 220, 0);

    // Dunkle Bildlaufleiste (DarkScrollBarUI). Eine zusammenhängende Gruppe:
    // sie wird über TileGalleryPanel.applyDarkScrollBar in der ganzen
    // Anwendung verwendet. Wer einen Wert ändert, sollte die anderen ansehen.
    /** Griff der Bildlaufleiste, Ruhezustand (gezeichnet). */
    public static final Color SCROLL_THUMB           = new Color( 70,  70,  70);
    /** Griff unter dem Mauszeiger. */
    public static final Color SCROLL_THUMB_HOVER     = new Color(105, 105, 105);
    /** Grundfarbe des Griffs (Swing-Feld {@code thumbColor}). */
    public static final Color SCROLL_THUMB_BASE      = new Color( 75,  75,  75);
    /**
     * Lichtkante des Griffs.
     * <p>Gleiches RGB wie {@link #BORDER_PAGE}, aber eigenes Token — ein
     * Seitenumriss ist keine Bildlaufleiste.
     */
    public static final Color SCROLL_HIGHLIGHT       = new Color( 90,  90,  90);
    /** Schattenkante des Griffs. */
    public static final Color SCROLL_SHADOW          = new Color( 55,  55,  55);
    /** Aufgehellte Bahn (Swing-Feld {@code trackHighlightColor}). */
    public static final Color SCROLL_TRACK_HIGHLIGHT = new Color( 40,  40,  40);
    // Die Bahn selbst ist BG_DARK (30,30,30) — dieselbe Bedeutung
    // („dunkelste Fläche"), deshalb kein eigenes Token.

    // ── Ergänzt 2026-07-31, Migration von PageLayoutToolbar ──────────────────

    /**
     * Grundton der Seitenlayout-Hilfslinien (Kopf-/Fußzeilen-Streifen,
     * Seitenzahl). Wird <b>nie deckend</b> verwendet — nur über die drei
     * abgeleiteten Varianten darunter. Ein eigener Ton, nicht {@link #ACCENT}:
     * die Hilfslinien sollen hinter dem Bild zurücktreten.
     */
    public static final Color LAYOUT_GUIDE = new Color(0, 80, 180);

    /** Füllung der Kopf-/Fußzeilen-Streifen (sehr blass). */
    public static final Color LAYOUT_STRIPE_FILL = alphaOf(LAYOUT_GUIDE,  45);
    /** Trennlinie der Kopf-/Fußzeilen-Streifen. */
    public static final Color LAYOUT_STRIPE_LINE = alphaOf(LAYOUT_GUIDE, 120);
    /** Seitenzahl-Markierung („N°"). */
    public static final Color LAYOUT_PAGENUM     = alphaOf(LAYOUT_GUIDE, 160);

    /**
     * Gestricheltes Rechteck um den Inhaltsbereich in der Layout-Vorschau.
     *
     * <p><b>Achtung, zweiter Beinahe-Treffer:</b> abgeleitet aus
     * {@link #ACCENT_ACTIVE} mit Alpha <b>180</b> — {@link #MARGIN_GUIDE} im
     * Dialog „Neue Datei" ist derselbe Grundton mit Alpha <b>170</b>. Zwei
     * gestrichelte Hilfslinien, zehn Alpha-Stufen auseinander; kaum sichtbar,
     * gewollt wirkt es nicht. <b>Unverändert übernommen</b> — die Migration ist
     * werterhaltend. Wer angleicht, trifft eine Design-Entscheidung.
     */
    public static final Color CONTENT_GUIDE = alphaOf(ACCENT_ACTIVE, 180);

    /**
     * Erzeugt eine transparente Variante — damit ein Grundton nur <b>einmal</b>
     * als RGB-Tripel dasteht (§21).
     *
     * <p>Bewusst hier und nicht {@code AppTheme.alpha(...)}: diese Klasse soll
     * für ihre eigenen Konstanten nicht von der Schwesterklasse abhängen.
     */
    private static Color alphaOf(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ── Ergänzt 2026-07-31, Migration von MapsPanel ──────────────────────────

    /** Fläche einer Karte/Kachel in einer Listenansicht. */
    public static final Color BG_CARD    = new Color(48, 48, 48);

    /**
     * Ruhezustand der kleinen Aktionsknöpfe in Karten (Löschen, Vorlesen).
     * <p>Dritter Wert mit dem RGB {@code (50,50,50)} neben {@link #BG_INPUT}
     * und {@link #BG_RULER_CORNER} — und wieder eine eigene Bedeutung: ein
     * Knopf ist weder Eingabefeld noch Lineal-Eckstück.
     */
    public static final Color BTN_MINI_BG = new Color(50, 50, 50);

    // Löschen-Knopf. Eigenes Rot, NICHT DANGER (200,60,60) und nicht
    // DANGER_HOVER (220,80,80): heller als das eine, gesättigter als das
    // andere. Unverändert übernommen (§21 — nicht auf einen Nachbarn runden).
    /** Schrift und Rahmen des Löschen-Knopfes. */
    public static final Color BTN_DELETE_FG       = new Color(220,  60,  60);
    /** Löschen-Knopf unter dem Mauszeiger (gefüllt). */
    public static final Color BTN_DELETE_HOVER_BG = new Color(180,  40,  40);

    // Vorlesen-Knopf (TTS). Eigenes Blau, unabhängig von ACCENT.
    /** Schrift und Rahmen des Vorlesen-Knopfes. */
    public static final Color BTN_TTS_FG          = new Color(100, 180, 220);
    /** Vorlesen-Knopf unter dem Mauszeiger (gefüllt). */
    public static final Color BTN_TTS_HOVER_BG    = new Color( 80, 150, 200);

    /** Kleine Zusatzangabe im Kartenkopf (Sprache, Anzahl). */
    public static final Color TEXT_INFO           = new Color(100, 150, 200);

    // ── Ergänzt 2026-07-31, Migration von ElementLayerPanel ──────────────────
    //
    // FAMILIE DER KLEINEN AKTIONSKNÖPFE. Alle folgen demselben Muster:
    //   Ruhe:  Schrift = *_FG, Rahmen = *_FG oder BORDER, Fläche = BTN_MINI_BG
    //   Hover: Fläche  = *_HOVER_BG, Schrift = weiß, Rahmen = *_FG
    // Sie stehen hier und nicht file-lokal, weil die Familie ÜBER DATEIEN
    // hinweg benutzt wird: BTN_DELETE_* kommt in MapsPanel UND in
    // ElementLayerPanel vor. Wer eine Variante ergänzt, hält sich an das
    // Muster oben.

    /** Als Bild exportieren („↓"). */
    public static final Color BTN_EXPORT_FG          = new Color( 60, 140, 220);
    public static final Color BTN_EXPORT_HOVER_BG    = new Color( 40, 100, 160);
    /** Auf den Canvas einbrennen. */
    public static final Color BTN_BURN_FG            = new Color(220, 140,  30);
    public static final Color BTN_BURN_HOVER_BG      = new Color(160,  90,  10);
    /** Drehung zurücksetzen. */
    public static final Color BTN_RESET_FG           = new Color(100, 180, 100);
    public static final Color BTN_RESET_HOVER_BG     = new Color( 80, 140,  80);
    /** Sichtbarkeit — Zustand „sichtbar". */
    public static final Color BTN_VISIBLE_FG         = new Color( 60, 180, 180);
    public static final Color BTN_VISIBLE_HOVER_BG   = new Color( 40, 140, 140);
    /** Sichtbarkeit — Zustand „ausgeblendet". */
    public static final Color BTN_HIDDEN_FG          = new Color(180,  60,  60);
    public static final Color BTN_HIDDEN_HOVER_BG    = new Color(140,  40,  40);
    /** Maus-Durchlässigkeit („⊘"/„⊙"), Zustand aktiv. */
    public static final Color BTN_MOUSETRANS_FG      = new Color(200, 150,  50);
    public static final Color BTN_MOUSETRANS_HOVER_BG= new Color(140, 100,  30);
    /** Als Karte exportieren („🗺"). */
    public static final Color BTN_MAP_FG             = new Color(180, 100, 200);
    public static final Color BTN_MAP_HOVER_BG       = new Color(140,  80, 160);
    /** Neutraler Zustand eines Umschalt-Knopfes (nicht aktiv). */
    public static final Color BTN_MINI_FG_OFF        = new Color(100, 100, 100);

    /**
     * Warmer Rahmen unter dem Mauszeiger.
     * <p>Bewusst geteilt: die Layer-Kachel und der Canvas verwenden denselben
     * Ton, damit dasselbe Element in Liste und Zeichenfläche gleich reagiert.
     * Der Canvas leitet daraus seine halbtransparente Variante ab — vorher war
     * die Übereinstimmung nur ein Kommentar („mirrors the canvas hover outline
     * colour") und musste von Hand gepflegt werden.
     */
    public static final Color HOVER_OUTLINE          = new Color(255, 200,  80);

    // ── Werkzeug-Icons (PaintIcons, ergänzt 2026-08-01) ───────────────────────

    /**
     * Kontur der gezeichneten Werkzeug-Icons.
     * <p><b>Bewusst an {@link #TEXT} gekoppelt, nicht als eigener Wert
     * getippt:</b> das Icon <i>ersetzt</i> die Buchstaben-Beschriftung des
     * Knopfes und ist damit dessen Vordergrund. Wer die Knopfschrift aufhellt,
     * soll die Icons mit aufhellen — genau das leistet die Ableitung (§21).
     */
    public static final Color ICON_LINE = TEXT;

    /** Zurückgenommene Binnenzeichnung im Icon (Hilfslinien, Schatten). */
    public static final Color ICON_LINE_MUTED = TEXT_MUTED;

    /** Flächenfüllung im Icon — angedeutet, nie deckend. */
    public static final Color ICON_FILL = alphaOf(TEXT, 45);

    /**
     * Helles Feld des Schachbretts, mit dem ein Icon <i>Transparenz</i> zeigt
     * (Radierer, Zauberstab III).
     * <p>Eigene Token, obwohl grau: es ist die Bedeutung „durchsichtig", nicht
     * „Fläche" — und das Paar muss auch dann kontrastieren, wenn die
     * Panel-Farben sich ändern.
     */
    public static final Color ICON_CHECKER_LIGHT = new Color(150, 150, 150);

    /** Dunkles Feld desselben Schachbretts. */
    public static final Color ICON_CHECKER_DARK  = new Color( 95,  95,  95);
}
