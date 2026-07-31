package paint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Typografie, Abstände, Radien und Linienstärken der Oberfläche.
 *
 * <p><b>Schwesterklasse von {@link AppColors}.</b> Die beiden zusammen sind die
 * Token-Quelle des Projekts (Guidelines §21, Univ. §3) und überschneiden sich
 * nicht:
 * <ul>
 *   <li>{@link AppColors} — <b>Farben</b>, sonst nichts.</li>
 *   <li>{@code AppTheme} — <b>Fonts, Abstände, Radien, Stroke-Breiten,
 *       Standardgrößen</b>, keine Farbe.</li>
 * </ul>
 * Eine Farbe gehört nie hierher, ein Font nie dorthin.
 *
 * <p><b>Regel (§21):</b> In neuem und berührtem Code steht kein
 * {@code new Font(...)} / {@code new BasicStroke(...)} und keine nackte Zahl
 * für Abstand oder Radius. Fehlt ein Token, wird es <b>hier angelegt</b> —
 * nicht das Literal getippt.
 *
 * <p><b>Herkunft der Werte (2026-07-30):</b> abgezählt aus dem Bestand, nicht
 * erfunden. Die Font-Skala deckt die gemessene Verteilung ab (SansSerif
 * PLAIN&nbsp;11 = 27×, PLAIN&nbsp;12 = 18×, BOLD&nbsp;11 = 13×,
 * PLAIN&nbsp;10 = 11× …), die Strichstärken 1f/1.5f/2f/3f ebenso
 * (19×/9×/7×/1×). <b>Kein bestehender Wert wurde verändert</b> — diese Klasse
 * ist rein additiv; die Ablösung der 726 Literale läuft danach pro Datei mit
 * Sichtprüfung.
 *
 * <p><b>Was hier NICHT hingehört:</b> Werte, die der Benutzer zur Laufzeit
 * wählt — Schriftart und -größe des Text-Werkzeugs, Kartenschrift, Pinselbreite
 * ({@code strokeWidth}). Die stehen in {@link AppSettings} (§31). Ein Token ist
 * eine Design-Entscheidung, keine Benutzereinstellung.
 */
public final class AppTheme {

    private AppTheme() {}

    // ── Schriftfamilie ────────────────────────────────────────────────────────
    /** Einzige UI-Schriftfamilie. Benutzergewählte Familien kommen aus AppSettings. */
    public static final String FAMILY = "SansSerif";

    // ── Font-Skala (abgezählt: 12 Fonts, mehr braucht die UI nicht) ───────────
    /** 8 pt — Mikro-Beschriftung, Eckwerte in Kacheln. */
    public static final Font FONT_TINY        = new Font(FAMILY, Font.PLAIN,  8);
    /** 9 pt — Hilfstext, Statuszeilen. */
    public static final Font FONT_XS          = new Font(FAMILY, Font.PLAIN,  9);
    /** 10 pt — dichte Listen, Zweitinformation. */
    public static final Font FONT_SM          = new Font(FAMILY, Font.PLAIN, 10);
    /** 11 pt — <b>Standard der Oberfläche</b> (häufigster Wert im Bestand). */
    public static final Font FONT_BASE        = new Font(FAMILY, Font.PLAIN, 11);
    /** 12 pt — Schaltflächen, etwas hervorgehobener Text. */
    public static final Font FONT_MD          = new Font(FAMILY, Font.PLAIN, 12);
    /** 13 pt — Dialogtitel-Zeilen, Kopfbereiche. */
    public static final Font FONT_LG          = new Font(FAMILY, Font.PLAIN, 13);

    public static final Font FONT_TINY_BOLD   = new Font(FAMILY, Font.BOLD,   8);
    public static final Font FONT_SM_BOLD     = new Font(FAMILY, Font.BOLD,  10);
    /** 11 pt fett — Standard-Hervorhebung. */
    public static final Font FONT_BASE_BOLD   = new Font(FAMILY, Font.BOLD,  11);
    public static final Font FONT_MD_BOLD     = new Font(FAMILY, Font.BOLD,  12);
    /** 15 pt fett — Überschrift eines Dialogs oder Panels. */
    public static final Font FONT_TITLE       = new Font(FAMILY, Font.BOLD,  15);

    /** 11 pt kursiv — Platzhalter-/Hinweistext. */
    public static final Font FONT_BASE_ITALIC = new Font(FAMILY, Font.ITALIC, 11);

    // ── Ergänzt 2026-07-31 bei der Migration von UIComponentFactory ───────────
    // Diese vier Werte kamen dort als Literal vor und passten auf KEIN
    // bestehendes Token. Sie wurden angelegt statt gerundet — Runden hätte das
    // Aussehen verändert (§21).

    /** 13 pt fett — Titelzeile eines Dialogs. */
    public static final Font FONT_LG_BOLD = new Font(FAMILY, Font.BOLD, 13);
    /** 16 pt — Beschriftung der quadratischen Umschaltknöpfe der oberen Leiste. */
    public static final Font FONT_XL      = new Font(FAMILY, Font.PLAIN, 16);
    /**
     * 16 pt aus der Familie <b>Dialog</b> — für Knöpfe, deren Beschriftung ein
     * Symbol/Unicode-Zeichen ist. Bewusst nicht {@link #FAMILY}: „Dialog" deckt
     * mehr Symbole ab, SansSerif zeigt sonst Ersatzkästchen.
     */
    public static final Font FONT_SYMBOL  = new Font("Dialog", Font.PLAIN, 16);
    /** 30 pt — Pfeil der großen Navigationsknöpfe über dem Bild. */
    public static final Font FONT_NAV     = new Font(FAMILY, Font.PLAIN, 30);

    // ── Ergänzt 2026-07-31, Migration von UIBuilder ──────────────────────────
    /** 18 pt fett — Überschrift der Ablage-Fläche („Bilddatei hier ablegen"). */
    public static final Font FONT_HINT    = new Font(FAMILY, Font.BOLD, 18);

    // ── Ergänzt 2026-07-31, Migration von TextToolbar ────────────────────────
    /** 12 pt kursiv — Kursiv-Umschalter der Text-Werkzeugleiste. */
    public static final Font FONT_MD_ITALIC = new Font(FAMILY, Font.ITALIC, 12);

    // ── Linienstärken ─────────────────────────────────────────────────────────
    /** 1 px — Trennlinien, Raster, einfache Rahmen (häufigster Wert). */
    public static final Stroke STROKE_HAIRLINE = new BasicStroke(1f);
    /** 1,5 px — Auswahlrahmen, Hervorhebung. */
    public static final Stroke STROKE_THIN     = new BasicStroke(1.5f);
    /** 2 px — aktive Auswahl, Griffe. */
    public static final Stroke STROKE_MEDIUM   = new BasicStroke(2f);
    /** 3 px — starke Betonung, Fokusrahmen des aktiven Canvas. */
    public static final Stroke STROKE_THICK    = new BasicStroke(3f);

    // ── Eckradien (fillRoundRect / drawRoundRect) ─────────────────────────────
    public static final int RADIUS_SM   =  4;
    /** 6 px — kleine Schaltflächen, Kacheln. */
    public static final int RADIUS_MD   =  6;
    /** 8 px — Standard-Schaltfläche. */
    public static final int RADIUS_LG   =  8;
    public static final int RADIUS_XL   = 10;
    public static final int RADIUS_XXL  = 12;
    /** 20 px — Pillenform (rundes Ende). */
    public static final int RADIUS_PILL = 20;

    // ── Abstände ──────────────────────────────────────────────────────────────
    public static final int PAD_XS =  2;
    public static final int PAD_SM =  3;
    public static final int PAD_MD =  4;
    public static final int PAD_LG =  6;
    public static final int PAD_XL = 10;

    /** Abstand zwischen Bedienelementen in einer Leiste. */
    public static final int GAP_SM =  4;
    public static final int GAP_MD =  8;
    public static final int GAP_LG = 16;

    // ── Standardgrößen ────────────────────────────────────────────────────────
    /**
     * Kantenlänge der quadratischen Knöpfe der oberen Leiste (36×36).
     * Stand bisher doppelt in {@code SelectiveAlphaEditor} (TOPBAR_BTN_W/H)
     * und {@code UIComponentFactory} (BUTTON_WIDTH/HEIGHT).
     */
    public static final int BTN_W = 36;
    public static final int BTN_H = 36;

    // ── Helfer ────────────────────────────────────────────────────────────────

    /**
     * Leitet eine transparente Variante einer Token-Farbe ab.
     *
     * <p>§21: Alpha-Varianten werden <b>abgeleitet, nie als zweites RGB-Tripel
     * getippt</b>. Statt {@code new Color(255,255,255,55)} also
     * {@code AppTheme.alpha(AppColors.TEXT, 55)} — ändert sich das Token,
     * ändert sich die Variante mit.
     *
     * @param c     Grundfarbe, üblicherweise aus {@link AppColors}
     * @param alpha 0 (unsichtbar) bis 255 (deckend); wird geklemmt
     */
    public static Color alpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Gleichmäßiger Innenabstand auf allen vier Seiten. */
    public static Border pad(int all) {
        return new EmptyBorder(all, all, all, all);
    }

    /** Innenabstand vertikal/horizontal (oben=unten, links=rechts). */
    public static Border pad(int vertical, int horizontal) {
        return new EmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /** Innenabstand je Seite — Reihenfolge wie bei {@link EmptyBorder}. */
    public static Border pad(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }
}
