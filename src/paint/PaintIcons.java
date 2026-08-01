package paint;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Gezeichnete Werkzeug-Symbole der Paint-Leiste — die einzige Quelle dafür.
 *
 * <p><b>Rolle:</b> liefert {@link Icon}-Objekte, die ihre Form mit
 * {@code Graphics2D} zeichnen. Sie ersetzen die Buchstaben- und
 * Unicode-Beschriftungen der Knöpfe in {@link PaintToolbar} und
 * {@link WandPanel}.
 *
 * <p><b>Was sie ausdrücklich nicht tut:</b> sie kennt weder Knöpfe noch
 * Werkzeugzustand noch Callbacks. Sie zeichnet Formen — die Verdrahtung
 * bleibt in der Leiste (§22: {@code PaintToolbar} ist kein Ablageplatz für
 * Zeichenlogik).
 *
 * <p><b>Warum gezeichnet und nicht als Zeichen getippt:</b> gemessen am
 * 2026-08-01 mit {@code Font.canDisplayUpTo()} über 59 Kandidaten in sechs
 * Schriftfamilien — die Logikfonts {@code SansSerif}/{@code Dialog} zeigen
 * 58 davon, aber ausgerechnet <b>🪣 U+1FAA3 (Fülleimer) in keiner einzigen</b>,
 * auch nicht in „Segoe UI Emoji". Ein Glyph-Tausch konnte den namentlich
 * gewünschten Fülleimer also nicht liefern. Belege und Vorschlagstabelle:
 * {@code doc/Schema_PaintToolbar_Icons.txt}, Umsetzung
 * {@code doc/Task_2026-08-01_0900_PaintToolbar-Icons.txt}.
 *
 * <p><b>Entwurfsraster:</b> jede Form wird auf einem {@link #GRID}×{@link #GRID}
 * großen Feld beschrieben und beim Zeichnen auf die gewünschte Kantenlänge
 * skaliert. Koordinaten im Quelltext sind deshalb <i>Rastereinheiten</i>,
 * keine Pixel.
 *
 * <p><b>Die elf Zauberstab-Varianten sind eine Familie, keine elf Symbole</b>
 * (§25-Denkweise, hier auf Symbole übertragen): gleiche Grundform
 * (Zauberstab bzw. Schere) plus ein Abzeichen unten rechts, das die Variante
 * trägt. Vorher zeigten elf Knöpfe im {@code WandPanel}-Raster zusammen nur
 * vier verschiedene Zeichen — sechsmal „⚡", dreimal „✂", je einmal „◠"/„◡".
 *
 * <p><b>Die Paare außen/innen zeigen nicht dieselbe Form gespiegelt.</b>
 * „◠" gegen „◡" war genau diese Falle: spiegelbildliche Symbole unterscheidet
 * niemand im Vorbeigehen. Ring-außen und Ring-innen zeigen deshalb <i>dieselbe
 * Fläche mit dem Ring an anderer Stelle</i> — und tragen im Raster zusätzlich
 * eine Beschriftung.
 */
public final class PaintIcons {

    /** Kantenlänge des Entwurfsrasters. Alle Koordinaten unten sind Einheiten davon. */
    public static final int GRID = 24;

    /** Übliche Kantenlänge in der waagerechten Werkzeugleiste. */
    public static final int SIZE = 30;

    /**
     * Übliche Kantenlänge im {@code WandPanel}-Raster (Grundform + Abzeichen).
     * <p><b>Größer als {@link #SIZE}, und das ist der Punkt:</b> im ersten
     * Entwurf trugen die Varianten-Abzeichen dieselben 26 px wie die übrigen
     * Icons — im Kontaktbogen waren sie dann nicht mehr auseinanderzuhalten,
     * also genau der Fehler, den diese Klasse beheben soll. Bei 50 px
     * Knopfkante ist der Platz vorhanden.
     */
    public static final int SIZE_BADGED = 36;

    private PaintIcons() { }

    // =========================================================================
    // Farbquelle
    // =========================================================================

    /**
     * Liefert die aktuell gewählten Malfarben.
     *
     * <p>Fülleimer, Pipette und die beiden Farbradierer zeigen die Farbe, mit
     * der sie arbeiten — dort ist sie die halbe Information.
     *
     * <p><b>§21:</b> das sind <b>benutzergewählte</b> Farben und damit
     * ausdrücklich <b>keine Tokens</b>. Sie kommen aus dem Zustand der Leiste
     * (und darüber aus {@code AppSettings}), nie aus {@link AppColors}. Wer
     * sie in die Palette einträgt, macht eine Benutzereinstellung zur
     * Design-Entscheidung.
     */
    public interface PaintColors {
        Color primary();
        Color secondary();
    }

    /** Rückfallquelle, wenn ein Icon ohne Farbbezug gezeichnet wird. */
    private static final PaintColors NEUTRAL = new PaintColors() {
        @Override public Color primary()   { return AppColors.ICON_LINE; }
        @Override public Color secondary() { return AppColors.ICON_LINE_MUTED; }
    };

    // =========================================================================
    // Öffentliche Fabrik
    // =========================================================================

    /** Ein Werkzeug-Symbol in der Standardgröße der Leiste. */
    public static Icon forTool(PaintEngine.Tool tool, PaintColors colors) {
        return forTool(tool, colors, isBadged(tool) ? SIZE_BADGED : SIZE);
    }

    /** Ein Werkzeug-Symbol in frei gewählter Kantenlänge. */
    public static Icon forTool(PaintEngine.Tool tool, PaintColors colors, int size) {
        return new VectorIcon(painterFor(tool), colors, size);
    }

    /** Ein Symbol für eine Leisten-Aktion (Drehen, Skalieren, Zwischenablage …). */
    public static Icon forAction(Action action) {
        return forAction(action, SIZE);
    }

    /** Ein Aktions-Symbol in frei gewählter Kantenlänge. */
    public static Icon forAction(Action action, int size) {
        return new VectorIcon(action.painter, NEUTRAL, size);
    }

    /**
     * Ein <b>Zeichen</b> als Icon — für die Knöpfe, deren Symbol bewusst
     * bleibt (↩ ↪ ↔ ↕ ↺ ↻ ⊞, Univ. §0).
     *
     * <p>Zweck ist nicht das Zeichnen, sondern die <b>Gleichbehandlung</b>:
     * ein Knopf kann nur <i>einen</i> Text tragen. Soll unter dem Symbol eine
     * Beschriftung stehen, muss das Symbol als Icon vorliegen — sonst
     * konkurrieren beide um dieselbe Eigenschaft und die Leiste zerfällt in
     * Knöpfe mit Beschriftung und Knöpfe mit Zeichen.
     *
     * <p><b>Schriftfamilie ist {@code Dialog}</b> (über
     * {@link AppTheme#FONT_SYMBOL}) und nicht die UI-Schrift. Gemessen am
     * 2026-08-01: „Segoe UI" stellt von 59 geprüften Symbolzeichen nur
     * <b>sechs</b> dar — mit der UI-Schrift wären diese Knöpfe leer.
     */
    public static Icon glyph(String text) {
        return glyph(text, SIZE);
    }

    /** Zeichen-Icon in frei gewählter Kantenlänge. */
    public static Icon glyph(String text, int size) {
        return new GlyphIcon(text, size);
    }

    /** Trägt ein Zeichen mittig — siehe {@link #glyph(String)}. */
    private static final class GlyphIcon implements Icon {
        private final String        text;
        private final int           size;
        private final java.awt.Font font;

        GlyphIcon(String text, int size) {
            this.text = text;
            this.size = size;
            this.font = AppTheme.FONT_SYMBOL.deriveFont((float) (size * 0.74));
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(font);
                g2.setColor(AppColors.ICON_LINE);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(text)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Trägt dieses Werkzeug ein Varianten-Abzeichen?
     * <p>Genau die elf Werkzeuge des {@code WandPanel}-Rasters — sie brauchen
     * etwas mehr Kantenlänge, weil Grundform und Abzeichen nebeneinander
     * Platz finden müssen.
     */
    public static boolean isBadged(PaintEngine.Tool tool) {
        return PaintToolbar.isWandTool(tool);
    }

    /**
     * Leisten-Aktionen mit gezeichnetem Symbol.
     *
     * <p><b>Bewusst unvollständig (Univ. §0):</b> Rückgängig/Wiederholen
     * (↩ ↪), Spiegeln (↔ ↕), 90°-Drehung (↺ ↻) und Raster (⊞) sind
     * konventionell, eindeutig und überall darstellbar. Sie behalten ihr
     * Zeichen. Ein Symbol zu tauschen, das niemand missversteht, ist ein
     * unnötiger Schritt. Hier stehen nur die Fälle, die im Befund vom
     * 2026-08-01 als unklar oder mehrfach belegt belegt wurden.
     */
    public enum Action {
        /** 45° im Uhrzeigersinn — vorher „↷", kaum von der 90°-Drehung zu trennen. */
        ROTATE_45_CW(PaintIcons::drawRotate45Cw),
        /** 45° gegen den Uhrzeigersinn — vorher „↶". */
        ROTATE_45_CCW(PaintIcons::drawRotate45Ccw),
        /** Freier Winkel — vorher „⟳°", ein Zeichen mit angeklebtem Gradzeichen. */
        ROTATE_FREE(PaintIcons::drawRotateFree),
        /** Skalieren — vorher „⤡", ein bloßer Diagonalpfeil. */
        SCALE(PaintIcons::drawScale),
        /** Kopieren — vorher „⎘", außerhalb von Unicode-Tabellen unbekannt. */
        COPY(PaintIcons::drawCopy),
        /** Einfügen — vorher „⎗", ebenso. */
        PASTE(PaintIcons::drawPaste),
        /** Lineal — vorher „⌇", eine Wellenlinie bedeutet kein Lineal. */
        RULER(PaintIcons::drawRuler),
        /** Kantenglättung — harte gegen weiche Kante nebeneinander. */
        ANTIALIAS(PaintIcons::drawAntialias),
        /**
         * Zauberstab-Panel ein/aus — vorher „⚡" und damit identisch mit
         * sechs Werkzeugen. Jetzt Zauberstab <i>mit Zahnrad</i>: es öffnet
         * die Einstellungen der Zauberstäbe, es ist keiner.
         */
        WAND_PANEL(PaintIcons::drawWandPanel),
        /**
         * Zeilenumbruch der Leiste — eine lange Reihe, ein Pfeil hinunter,
         * eine zweite kürzere Reihe. Bewusst <b>kein</b> Zeichen: „☰" und „≡"
         * bedeuten überall ein Menü, nicht einen Umbruch.
         */
        ROW_WRAP(PaintIcons::drawRowWrap);

        private final Painter painter;
        Action(Painter p) { this.painter = p; }
    }

    // =========================================================================
    // Icon-Implementierung
    // =========================================================================

    @FunctionalInterface
    private interface Painter {
        void draw(Graphics2D g, PaintColors c);
    }

    /**
     * Zeichnet eine Form des Entwurfsrasters in der geforderten Kantenlänge.
     *
     * <p>§24: gearbeitet wird auf {@code g.create()} — der Aufrufer bekommt
     * seinen {@code Graphics2D}-Zustand unverändert zurück, unabhängig davon,
     * was ein einzelner Zeichner an Transform, Stroke oder Farbe setzt.
     */
    private static final class VectorIcon implements Icon {
        private final Painter     painter;
        private final PaintColors colors;
        private final int         size;

        VectorIcon(Painter painter, PaintColors colors, int size) {
            this.painter = painter;
            this.colors  = colors != null ? colors : NEUTRAL;
            this.size    = size;
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                                    RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.scale(size / (double) GRID, size / (double) GRID);
                g2.setStroke(AppTheme.STROKE_ICON);
                g2.setColor(AppColors.ICON_LINE);
                painter.draw(g2, colors);
            } finally {
                g2.dispose();
            }
        }
    }

    // =========================================================================
    // Zeichen-Helfer
    //
    // Univ. §13: keine Allokation im Zeichenpfad. Die Formen unten sind
    // wiederverwendete Kratzflächen, keine je Aufruf erzeugten Objekte.
    // Sie sind NICHT threadsicher — das ist richtig so: gezeichnet wird
    // ausschließlich auf dem Event Dispatch Thread (§24).
    // =========================================================================

    private static final Line2D.Float           L  = new Line2D.Float();
    private static final Ellipse2D.Float        E  = new Ellipse2D.Float();
    private static final Rectangle2D.Float      R  = new Rectangle2D.Float();
    private static final RoundRectangle2D.Float RR = new RoundRectangle2D.Float();
    private static final Path2D.Float           P  = new Path2D.Float();

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        L.setLine(x1, y1, x2, y2);
        g.draw(L);
    }

    private static void oval(Graphics2D g, double x, double y, double w, double h) {
        E.setFrame(x, y, w, h);
        g.draw(E);
    }

    private static void ovalFill(Graphics2D g, double x, double y, double w, double h) {
        E.setFrame(x, y, w, h);
        g.fill(E);
    }

    private static void dot(Graphics2D g, double cx, double cy, double r) {
        ovalFill(g, cx - r, cy - r, r * 2, r * 2);
    }

    private static void rect(Graphics2D g, double x, double y, double w, double h) {
        R.setRect(x, y, w, h);
        g.draw(R);
    }

    private static void rectFill(Graphics2D g, double x, double y, double w, double h) {
        R.setRect(x, y, w, h);
        g.fill(R);
    }

    private static void roundRect(Graphics2D g, double x, double y, double w, double h, double r) {
        RR.setRoundRect(x, y, w, h, r, r);
        g.draw(RR);
    }

    private static void roundRectFill(Graphics2D g, double x, double y, double w, double h, double r) {
        RR.setRoundRect(x, y, w, h, r, r);
        g.fill(RR);
    }

    /** Setzt die gemeinsame Kratzfläche zurück und gibt sie zum Befüllen frei. */
    private static Path2D.Float path() {
        P.reset();
        return P;
    }

    /**
     * Schachbrett als Zeichen für „durchsichtig".
     * <p>Dasselbe Muster, das der Canvas hinter transparenten Stellen zeigt —
     * die Kopplung ist Absicht: wer es dort erkennt, erkennt es hier wieder.
     */
    private static void checker(Graphics2D g, double x, double y, double w, double h, double cell) {
        Graphics2D gc = (Graphics2D) g.create();
        try {
            R.setRect(x, y, w, h);
            gc.clip(R);
            gc.setColor(AppColors.ICON_CHECKER_DARK);
            gc.fill(R);
            gc.setColor(AppColors.ICON_CHECKER_LIGHT);
            int rows = (int) Math.ceil(h / cell);
            int cols = (int) Math.ceil(w / cell);
            for (int r = 0; r < rows; r++) {
                for (int c = (r % 2); c < cols; c += 2) {
                    R.setRect(x + c * cell, y + r * cell, cell, cell);
                    gc.fill(R);
                }
            }
        } finally {
            gc.dispose();
        }
    }

    /** Pfeilspitze am Ende einer Linie, gefüllt. */
    private static void arrowHead(Graphics2D g, double tipX, double tipY, double angle, double len) {
        double a1 = angle + Math.toRadians(150);
        double a2 = angle - Math.toRadians(150);
        Path2D.Float p = path();
        p.moveTo(tipX, tipY);
        p.lineTo(tipX + Math.cos(a1) * len, tipY + Math.sin(a1) * len);
        p.lineTo(tipX + Math.cos(a2) * len, tipY + Math.sin(a2) * len);
        p.closePath();
        g.fill(p);
    }

    /** Vierzackiger Funke — Bestandteil jeder Zauberstab-Grundform. */
    private static void sparkle(Graphics2D g, double cx, double cy, double r) {
        Path2D.Float p = path();
        double i = r * 0.32;
        p.moveTo(cx, cy - r);
        p.lineTo(cx + i, cy - i);
        p.lineTo(cx + r, cy);
        p.lineTo(cx + i, cy + i);
        p.lineTo(cx, cy + r);
        p.lineTo(cx - i, cy + i);
        p.lineTo(cx - r, cy);
        p.lineTo(cx - i, cy - i);
        p.closePath();
        g.fill(p);
    }

    // =========================================================================
    // Werkzeuge — Grundformen
    // =========================================================================

    private static Painter painterFor(PaintEngine.Tool tool) {
        return switch (tool) {
            case PENCIL        -> PaintIcons::drawPencil;
            case FLOODFILL     -> PaintIcons::drawFloodfill;
            case LINE          -> PaintIcons::drawLine;
            case CIRCLE        -> PaintIcons::drawCircle;
            case RECT          -> PaintIcons::drawRect;
            case ERASER        -> PaintIcons::drawEraser;
            case ERASER_BG     -> PaintIcons::drawEraserBg;
            case ERASER_COLOR  -> PaintIcons::drawEraserColor;
            case EYEDROPPER    -> PaintIcons::drawEyedropper;
            case SELECT        -> PaintIcons::drawSelect;
            case TEXT          -> PaintIcons::drawText;
            case PATH          -> PaintIcons::drawPath;
            case FREE_PATH     -> PaintIcons::drawFreePath;
            case SMEAR         -> PaintIcons::drawSmear;

            case WAND_I              -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeRegion);
            case WAND_II             -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeUntilColor);
            case WAND_III            -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeTransparent);
            case WAND_IV             -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeCollapse);
            case WAND_REPLACE_OUTER  -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeRingOuter);
            case WAND_REPLACE_INNER  -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeRingInner);
            case WAND_AA_OUTER       -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeSoftOuter);
            case WAND_AA_INNER       -> (g, c) -> wandWithBadge(g, c, PaintIcons::badgeSoftInner);

            case CUT_COLOR       -> (g, c) -> scissorsWithBadge(g, c, PaintIcons::badgeOneChip);
            case CUT_UNTIL_COLOR -> (g, c) -> scissorsWithBadge(g, c, PaintIcons::badgeChipUntil);
            case CUT_SAME_COLOR  -> (g, c) -> scissorsWithBadge(g, c, PaintIcons::badgeTwoChips);
        };
    }

    /** Bleistift, Spitze unten links — das gewöhnliche Malwerkzeug. */
    private static void drawPencil(Graphics2D g, PaintColors c) {
        Path2D.Float body = path();
        body.moveTo(4.2, 19.8);
        body.lineTo(6.6, 13.6);
        body.lineTo(16.0, 4.2);
        body.lineTo(19.8, 8.0);
        body.lineTo(10.4, 17.4);
        body.closePath();
        g.setColor(AppColors.ICON_FILL);
        g.fill(body);
        g.setColor(AppColors.ICON_LINE);
        g.draw(body);

        // Spitze gefüllt — sie macht aus dem Stab einen Stift
        Path2D.Float tip = path();
        tip.moveTo(4.2, 19.8);
        tip.lineTo(6.6, 13.6);
        tip.lineTo(10.4, 17.4);
        tip.closePath();
        g.fill(tip);

        // Zwinge zwischen Holz und Radierer
        line(g, 14.2, 6.0, 18.0, 9.8);
    }

    /**
     * Fülleimer mit Tropfen — namentlich gewünscht und der Grund, warum diese
     * Klasse überhaupt zeichnet statt Zeichen zu setzen (🪣 ist nicht darstellbar).
     */
    private static void drawFloodfill(Graphics2D g, PaintColors c) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-32), 11, 11);

        // Eimer: oben offen, nach unten verjüngt
        Path2D.Float bucket = path();
        bucket.moveTo(4.6, 6.0);
        bucket.lineTo(17.4, 6.0);
        bucket.lineTo(14.6, 17.6);
        bucket.lineTo(7.4, 17.6);
        bucket.closePath();
        g.setColor(c.primary());
        g.fill(bucket);
        g.setColor(AppColors.ICON_LINE);
        g.draw(bucket);

        // Bügel
        Path2D.Float handle = path();
        handle.moveTo(5.6, 5.4);
        handle.quadTo(11.0, -1.6, 16.4, 5.4);
        g.draw(handle);

        // Öffnung — die Ellipse macht den Eimer räumlich
        oval(g, 4.6, 3.6, 12.8, 4.8);

        g.setTransform(old);

        // Tropfen, der herausläuft
        Path2D.Float drop = path();
        drop.moveTo(19.0, 13.8);
        drop.quadTo(22.6, 18.4, 19.0, 20.8);
        drop.quadTo(15.4, 18.4, 19.0, 13.8);
        drop.closePath();
        g.setColor(c.primary());
        g.fill(drop);
        g.setColor(AppColors.ICON_LINE);
        g.draw(drop);
    }

    /** Gerade Linie mit beiden Endpunkten. */
    private static void drawLine(Graphics2D g, PaintColors c) {
        line(g, 5.0, 19.0, 19.0, 5.0);
        dot(g, 5.0, 19.0, 2.2);
        dot(g, 19.0, 5.0, 2.2);
    }

    /** Ellipse, Umriss. */
    private static void drawCircle(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_FILL);
        ovalFill(g, 3.5, 5.5, 17, 13);
        g.setColor(AppColors.ICON_LINE);
        oval(g, 3.5, 5.5, 17, 13);
    }

    /** Rechteck, Umriss. */
    private static void drawRect(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_FILL);
        rectFill(g, 3.5, 5.5, 17, 13);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 3.5, 5.5, 17, 13);
    }

    /**
     * Radierer über einer Schachbrett-Spur.
     * <p>Die Spur ist die eigentliche Aussage: dieser Radierer macht
     * <b>durchsichtig</b> — er malt nicht mit einer Farbe.
     */
    private static void drawEraser(Graphics2D g, PaintColors c) {
        checker(g, 3.0, 16.2, 18.0, 4.8, 2.4);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 3.0, 16.2, 18.0, 4.8);
        eraserBody(g);
    }

    /** Derselbe Radierer, aber die Spur trägt die Sekundärfarbe. */
    private static void drawEraserBg(Graphics2D g, PaintColors c) {
        g.setColor(c.secondary());
        rectFill(g, 3.0, 16.2, 18.0, 4.8);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 3.0, 16.2, 18.0, 4.8);
        eraserBody(g);
    }

    /**
     * Farbradierer: die Spur zeigt beide Farben und dazwischen den Pfeil —
     * Primärfarbe wird zu Sekundärfarbe, alles andere bleibt.
     */
    private static void drawEraserColor(Graphics2D g, PaintColors c) {
        g.setColor(c.primary());
        rectFill(g, 3.0, 16.2, 7.6, 4.8);
        g.setColor(c.secondary());
        rectFill(g, 13.4, 16.2, 7.6, 4.8);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 3.0, 16.2, 7.6, 4.8);
        rect(g, 13.4, 16.2, 7.6, 4.8);
        line(g, 10.9, 18.6, 13.1, 18.6);
        arrowHead(g, 13.4, 18.6, 0, 1.8);
        eraserBody(g);
    }

    /** Gemeinsamer Radiergummi-Körper der drei Radierer. */
    private static void eraserBody(Graphics2D g) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-22), 12, 10);
        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 5.4, 4.6, 13.2, 8.0, 2.4);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 5.4, 4.6, 13.2, 8.0, 2.4);
        line(g, 11.0, 4.6, 11.0, 12.6);
        g.setTransform(old);
    }

    /** Pipette mit Tropfen in der aufgenommenen Farbe. */
    private static void drawEyedropper(Graphics2D g, PaintColors c) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(45), 12, 12);

        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 9.0, 2.2, 6.0, 5.6, 2.0);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 9.0, 2.2, 6.0, 5.6, 2.0);
        rect(g, 10.4, 7.8, 3.2, 2.2);
        line(g, 10.9, 10.0, 10.9, 16.4);
        line(g, 13.1, 10.0, 13.1, 16.4);

        Path2D.Float tip = path();
        tip.moveTo(10.9, 16.4);
        tip.lineTo(13.1, 16.4);
        tip.lineTo(12.0, 20.6);
        tip.closePath();
        g.setColor(c.primary());
        g.fill(tip);
        g.setColor(AppColors.ICON_LINE);
        g.draw(tip);

        g.setTransform(old);
    }

    /** Auswahlrechteck mit den vier Eck-Anfassern. */
    private static void drawSelect(Graphics2D g, PaintColors c) {
        g.setStroke(AppTheme.STROKE_ICON_DASH);
        rect(g, 4.5, 5.5, 15.0, 13.0);
        g.setStroke(AppTheme.STROKE_ICON);
        double s = 2.8;
        rectFill(g,  4.5 - s / 2,  5.5 - s / 2, s, s);
        rectFill(g, 19.5 - s / 2,  5.5 - s / 2, s, s);
        rectFill(g,  4.5 - s / 2, 18.5 - s / 2, s, s);
        rectFill(g, 19.5 - s / 2, 18.5 - s / 2, s, s);
    }

    /** Text: ein „A" auf seiner Grundlinie. */
    private static void drawText(Graphics2D g, PaintColors c) {
        g.setStroke(AppTheme.STROKE_ICON);
        line(g,  6.2, 17.4, 12.0,  4.6);
        line(g, 12.0,  4.6, 17.8, 17.4);
        line(g,  8.6, 12.4, 15.4, 12.4);
        g.setColor(AppColors.ICON_LINE_MUTED);
        line(g, 4.0, 20.4, 20.0, 20.4);
    }

    /**
     * Pfad: eine Bézierkurve mit Kontrollpunkten und Tangentengriffen.
     * <p>Vorher stand hier „≈" — und das eigentliche Stift-Zeichen „✏" saß
     * auf dem <i>Freihand</i>-Pfad. Die beiden waren dadurch praktisch
     * vertauscht lesbar.
     */
    private static void drawPath(Graphics2D g, PaintColors c) {
        Path2D.Float curve = path();
        curve.moveTo(3.8, 17.8);
        curve.curveTo(8.4, 4.4, 15.6, 19.6, 20.2, 6.2);
        g.setColor(AppColors.ICON_LINE);
        g.draw(curve);

        // Tangenten zu den Griffen
        g.setColor(AppColors.ICON_LINE_MUTED);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        line(g,  3.8, 17.8,  8.4,  7.6);
        line(g, 20.2,  6.2, 15.6, 16.4);
        rect(g,  8.4 - 1.3,  7.6 - 1.3, 2.6, 2.6);
        rect(g, 15.6 - 1.3, 16.4 - 1.3, 2.6, 2.6);

        // Ankerpunkte — sie gewinnen gegen die Kante, wie im Hit-Test (§22)
        g.setStroke(AppTheme.STROKE_ICON);
        g.setColor(AppColors.ICON_LINE);
        dot(g,  3.8, 17.8, 2.2);
        dot(g, 20.2,  6.2, 2.2);
    }

    /** Freihand-Pfad: gezogene Kurve mit dem Stift an der Spitze. */
    private static void drawFreePath(Graphics2D g, PaintColors c) {
        Path2D.Float curve = path();
        curve.moveTo(2.8, 16.6);
        curve.curveTo(6.4, 6.0, 10.2, 20.4, 13.8, 11.4);
        curve.curveTo(15.2, 7.8, 16.6, 9.4, 18.0, 7.6);
        g.draw(curve);

        // Stiftspitze am Ende der Bewegung
        Path2D.Float nib = path();
        nib.moveTo(18.0, 7.6);
        nib.lineTo(22.0, 3.0);
        nib.lineTo(22.8, 6.4);
        nib.lineTo(19.6, 9.0);
        nib.closePath();
        g.setColor(AppColors.ICON_FILL);
        g.fill(nib);
        g.setColor(AppColors.ICON_LINE);
        g.draw(nib);
    }

    /**
     * Verwischen: Fingerkuppe mit einer Spur, die nach rechts verläuft.
     *
     * <p>Der erste Entwurf legte drei verschieden starke Striche übereinander —
     * im Kontaktbogen wurde daraus bei 26 px ein einziger Klecks. Eine
     * <b>abgestufte Spur aus getrennten Feldern</b> überlebt die Verkleinerung,
     * ein Verlauf aus überlagerten Linien nicht.
     */
    private static void drawSmear(Graphics2D g, PaintColors c) {
        // Schweif: nach rechts blasser und schmaler
        for (int i = 6; i >= 0; i--) {
            g.setColor(AppTheme.alpha(AppColors.ICON_LINE, 230 - i * 31));
            double h = 8.6 - i * 0.9;
            ovalFill(g, 7.0 + i * 2.3, 12.0 - h / 2, 3.4, h);
        }
        // Deckender Kopf, der die Spur zieht
        g.setColor(AppColors.ICON_LINE);
        dot(g, 6.0, 12.0, 4.6);
    }

    // =========================================================================
    // Zauberstab-Familie: Grundform + Abzeichen
    // =========================================================================

    /**
     * Zeichnet die Grundform verkleinert nach links oben und legt das
     * Varianten-Abzeichen nach rechts unten.
     *
     * <p>Das ist die Regel für alle elf Varianten: <b>die Grundform sagt,
     * welches Werkzeug es ist, das Abzeichen, welche Spielart.</b> Wer eine
     * zwölfte Variante ergänzt, ergänzt ein Abzeichen — keine neue Grundform.
     */
    private static void wandWithBadge(Graphics2D g, PaintColors c, Painter badge) {
        withBase(g, c, PaintIcons::drawWandBase, badge);
    }

    private static void scissorsWithBadge(Graphics2D g, PaintColors c, Painter badge) {
        withBase(g, c, PaintIcons::drawScissorsBase, badge);
    }

    private static void withBase(Graphics2D g, PaintColors c, Painter base, Painter badge) {
        AffineTransform old = g.getTransform();
        g.scale(0.66, 0.66);
        base.draw(g, c);
        g.setTransform(old);
        badgeZone(g, c, badge);
    }

    /**
     * Legt das Abzeichen-Feld unten rechts an und ruft den Zeichner darin auf.
     *
     * <p>Zwei Dinge, die nicht „vereinfacht" werden dürfen:
     * <ul>
     *   <li>Die <b>dunkle Scheibe</b> darunter ist kein Schmuck. Ohne sie
     *       verschwimmt das Abzeichen auf dem blauen Hintergrund des
     *       <i>ausgewählten</i> Knopfes ({@code AppColors.BTN_ACTIVE}).</li>
     *   <li>Die Abzeichen zeichnen in einem <b>eigenen Koordinatenfeld
     *       0…10</b>, nicht im 24er-Raster. Dadurch ändert eine Verschiebung
     *       oder Vergrößerung des Feldes genau eine Stelle — hier — statt
     *       elf Abzeichen einzeln.</li>
     * </ul>
     */
    private static void badgeZone(Graphics2D g, PaintColors c, Painter badge) {
        g.setColor(AppTheme.alpha(AppColors.BG_DARK, 205));
        roundRectFill(g, 10.8, 10.8, 13.2, 13.2, 3.8);

        AffineTransform old = g.getTransform();
        g.translate(11.9, 11.9);
        g.scale(1.11, 1.11);
        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        badge.draw(g, c);
        g.setTransform(old);
        g.setStroke(AppTheme.STROKE_ICON);
    }

    /** Zauberstab: Stab mit Funken — die Grundform aller acht Zauberstäbe. */
    private static void drawWandBase(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_MEDIUM);
        line(g, 4.0, 20.0, 16.4, 7.6);
        g.setStroke(AppTheme.STROKE_ICON);

        // Kopf des Stabs
        Path2D.Float head = path();
        head.moveTo(16.4, 7.6);
        head.lineTo(19.0, 5.0);
        head.lineTo(21.0, 7.0);
        head.lineTo(18.4, 9.6);
        head.closePath();
        g.setColor(AppColors.ICON_FILL);
        g.fill(head);
        g.setColor(AppColors.ICON_LINE);
        g.draw(head);

        sparkle(g, 21.4, 2.6, 2.4);
        sparkle(g, 14.8, 3.2, 1.6);
        sparkle(g, 22.2, 11.4, 1.5);
    }

    /** Schere — die Grundform der drei Ausschneide-Werkzeuge. */
    private static void drawScissorsBase(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE);
        line(g,  6.0,  2.6, 16.8, 16.4);
        line(g, 18.0,  2.6,  7.2, 16.4);
        oval(g,  3.6, 16.4, 5.2, 5.2);
        oval(g, 15.2, 16.4, 5.2, 5.2);
        dot(g, 12.0, 11.6, 1.2);
    }

    // ── Abzeichen ────────────────────────────────────────────────────────────
    // ALLE zeichnen im lokalen Feld 0…10, nicht im 24er-Raster (siehe
    // badgeZone). Wer ein Abzeichen ergänzt, hält sich daran — sonst wandert
    // es beim nächsten Verschieben des Feldes aus dem Knopf heraus.

    /** Region gleicher Farbe — gestrichelter Umriss. */
    private static void badgeRegion(Graphics2D g, PaintColors c) {
        g.setStroke(AppTheme.STROKE_ICON_DASH);
        Path2D.Float blob = path();
        blob.moveTo(0.8, 5.4);
        blob.curveTo(0.8, 1.4, 5.6, 0.2, 7.8, 2.6);
        blob.curveTo(10.4, 5.6, 7.4, 10.0, 4.0, 9.0);
        blob.curveTo(1.8, 8.4, 0.8, 7.2, 0.8, 5.4);
        blob.closePath();
        g.draw(blob);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
    }

    /** Bis Zielfarbe — dieselbe Region, aber mit Anschlag in der Sekundärfarbe. */
    private static void badgeUntilColor(Graphics2D g, PaintColors c) {
        g.setStroke(AppTheme.STROKE_ICON_DASH);
        oval(g, 0.4, 1.6, 6.4, 6.4);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        g.setColor(c.secondary());
        rectFill(g, 7.8, 0.4, 2.0, 9.2);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 7.8, 0.4, 2.0, 9.2);
    }

    /** Transparent — Schachbrett. */
    private static void badgeTransparent(Graphics2D g, PaintColors c) {
        checker(g, 0.6, 0.6, 8.8, 8.8, 2.2);
        g.setColor(AppColors.ICON_LINE);
        rect(g, 0.6, 0.6, 8.8, 8.8);
    }

    /** Inwards Collapse — vier Pfeile, die nach innen zusammenlaufen. */
    private static void badgeCollapse(Graphics2D g, PaintColors c) {
        g.setStroke(AppTheme.STROKE_ICON_DASH);
        rect(g, 0.4, 0.4, 9.2, 9.2);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        rectFill(g, 4.2, 4.2, 1.6, 1.6);
        double q = Math.toRadians(45);
        arrowHead(g, 3.9, 3.9,  q,             2.6);
        arrowHead(g, 6.1, 3.9,  Math.PI - q,   2.6);
        arrowHead(g, 3.9, 6.1, -q,             2.6);
        arrowHead(g, 6.1, 6.1,  Math.PI + q,   2.6);
    }

    /**
     * Ring AUSSERHALB der Fläche.
     * <p>Gemeinsam mit {@link #badgeRingInner} der Prüfstein des ganzen
     * Entwurfs: die beiden zeigen <b>dieselbe Fläche</b>, der Unterschied ist
     * allein, <b>wo</b> der betonte Ring liegt. Sie sind ausdrücklich nicht
     * dieselbe Form gespiegelt — genau daran scheiterten „◠" und „◡".
     */
    private static void badgeRingOuter(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE_MUTED);
        rectFill(g, 3.2, 3.2, 3.6, 3.6);
        g.setColor(AppColors.ACCENT);
        g.setStroke(AppTheme.STROKE_MEDIUM);
        rect(g, 0.9, 0.9, 8.2, 8.2);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        g.setColor(AppColors.ICON_LINE);
    }

    /** Ring INNERHALB der Fläche — siehe {@link #badgeRingOuter}. */
    private static void badgeRingInner(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE_MUTED);
        rect(g, 0.9, 0.9, 8.2, 8.2);
        g.setColor(AppColors.ACCENT);
        g.setStroke(AppTheme.STROKE_MEDIUM);
        rect(g, 3.2, 3.2, 3.6, 3.6);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        g.setColor(AppColors.ICON_LINE);
    }

    /**
     * Weiche Kante nach außen — deckender Kern, Saum strahlt nach außen.
     *
     * <p><b>Rund, während die Ring-Abzeichen eckig sind — das ist Absicht und
     * darf nicht „vereinheitlicht" werden.</b> Im zweiten Kontaktbogen waren
     * alle vier Abzeichen (Ring außen/innen, AA außen/innen) als eckige
     * Fläche mit blauem Rand gezeichnet und sahen zu viert wie dasselbe blaue
     * Quadrat aus. Zwei Merkmale zu variieren, die beide klein sind, reicht
     * nicht: es braucht <b>zwei verschiedene Umrisse</b>. Eckig = harter Ring
     * wird ersetzt, rund = weicher Übergang.
     */
    private static void badgeSoftOuter(Graphics2D g, PaintColors c) {
        for (int i = 4; i >= 1; i--) {
            g.setColor(AppTheme.alpha(AppColors.ACCENT, 20 + (5 - i) * 26));
            double r = 2.0 + i * 0.85;
            ovalFill(g, 5.0 - r, 5.0 - r, r * 2, r * 2);
        }
        g.setColor(AppColors.ICON_LINE);
        ovalFill(g, 3.0, 3.0, 4.0, 4.0);
    }

    /** Weiche Kante nach innen — scharfer Rand, Saum strahlt einwärts. */
    private static void badgeSoftInner(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_ICON);
        oval(g, 0.5, 0.5, 9.0, 9.0);
        for (int i = 1; i <= 4; i++) {
            g.setColor(AppTheme.alpha(AppColors.ACCENT, 20 + (5 - i) * 26));
            double r = 4.1 - i * 0.8;
            oval(g, 5.0 - r, 5.0 - r, r * 2, r * 2);
        }
        g.setStroke(AppTheme.STROKE_ICON_FINE);
    }

    /** Genau eine Zielfarbe. */
    private static void badgeOneChip(Graphics2D g, PaintColors c) {
        chip(g, 2.4, 2.4, c.secondary());
    }

    /** Zielfarbe plus Anschlag. */
    private static void badgeChipUntil(Graphics2D g, PaintColors c) {
        chip(g, 0.4, 2.4, c.secondary());
        g.setColor(AppColors.ICON_LINE);
        rectFill(g, 7.8, 0.4, 1.8, 9.2);
    }

    /** Zwei gleiche Farben — „schneidet nur diese eine Farbe". */
    private static void badgeTwoChips(Graphics2D g, PaintColors c) {
        chip(g, 0.2, 2.4, c.secondary());
        chip(g, 5.4, 2.4, c.secondary());
    }

    private static void chip(Graphics2D g, double x, double y, Color fill) {
        g.setColor(fill);
        roundRectFill(g, x, y, 4.4, 4.4, 1.2);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, x, y, 4.4, 4.4, 1.2);
    }

    // =========================================================================
    // Aktionen
    // =========================================================================

    private static void drawRotate45Cw(Graphics2D g, PaintColors c)  { rotateByAngle(g,  45); }
    private static void drawRotate45Ccw(Graphics2D g, PaintColors c) { rotateByAngle(g, -45); }

    /**
     * Drehung um einen festen Winkel: der Winkel wird <b>gezeichnet</b>,
     * nicht nur angedeutet. Genau das unterschied „↷" nicht von „↻".
     */
    private static void rotateByAngle(Graphics2D g, int deg) {
        // Die beiden Schenkel machen den Winkel sichtbar — sie sind der
        // Unterschied zur 90-Grad-Drehung, nicht die Krümmung des Pfeils.
        g.setColor(AppColors.ICON_LINE_MUTED);
        g.setStroke(AppTheme.STROKE_ICON);
        line(g, 12.0, 12.0, 12.0, 2.2);
        line(g, 12.0, 12.0,
                12.0 + Math.sin(Math.toRadians(deg)) * 9.8,
                12.0 - Math.cos(Math.toRadians(deg)) * 9.8);

        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_MEDIUM);
        double r = 7.0;
        arc(g, 0, deg, r);

        // Die Bewegungsrichtung IST der Tangentenwinkel: bei wachsendem
        // Winkel gleich dem Winkel selbst, bei fallendem um 180 Grad gedreht.
        double end = Math.toRadians(deg);
        arrowHead(g, 12.0 + Math.sin(end) * r, 12.0 - Math.cos(end) * r,
                  deg > 0 ? end : end + Math.PI, 3.6);
        g.setStroke(AppTheme.STROKE_ICON);
        dot(g, 12.0, 12.0, 1.5);
    }

    /** Kreisbogen um die Mitte, Winkel in Grad, 0 = oben, positiv im Uhrzeigersinn. */
    private static void arc(Graphics2D g, double fromDeg, double toDeg, double r) {
        Path2D.Float p = path();
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / (double) steps);
            double x = 12.0 + Math.sin(a) * r;
            double y = 12.0 - Math.cos(a) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        g.draw(p);
    }

    /** Freier Winkel: offener Kreispfeil mit frei stehendem Schenkel. */
    private static void drawRotateFree(Graphics2D g, PaintColors c) {
        double r = 8.0;
        g.setStroke(AppTheme.STROKE_MEDIUM);
        arc(g, -145, 145, r);
        double end = Math.toRadians(145);
        arrowHead(g, 12.0 + Math.sin(end) * r, 12.0 - Math.cos(end) * r, end, 3.6);

        // Der bewegliche Schenkel: hier wird der Winkel eingegeben
        g.setColor(AppColors.ICON_LINE_MUTED);
        g.setStroke(AppTheme.STROKE_ICON);
        line(g, 12.0, 12.0, 12.0, 4.8);
        line(g, 12.0, 12.0, 17.4, 16.6);
        g.setColor(AppColors.ICON_LINE);
        dot(g, 12.0, 12.0, 1.5);
    }

    /** Skalieren: kleines Rechteck wächst zum großen, Anfasser an der Ecke. */
    private static void drawScale(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE_MUTED);
        g.setStroke(AppTheme.STROKE_ICON_DASH);
        rect(g, 2.6, 2.6, 9.4, 9.4);
        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_ICON);
        rect(g, 2.6, 2.6, 16.8, 16.8);
        g.setStroke(AppTheme.STROKE_MEDIUM);
        line(g, 12.6, 12.6, 17.4, 17.4);
        arrowHead(g, 18.6, 18.6, Math.toRadians(45), 3.6);
        g.setStroke(AppTheme.STROKE_ICON);
        rectFill(g, 17.4, 17.4, 3.4, 3.4);
    }

    /** Kopieren: zwei Blätter übereinander. */
    private static void drawCopy(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_LINE_MUTED);
        roundRect(g, 3.4, 3.4, 12.4, 14.4, 2.0);
        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 8.2, 6.2, 12.4, 14.4, 2.0);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 8.2, 6.2, 12.4, 14.4, 2.0);
    }

    /** Einfügen: Klemmbrett mit Blatt. */
    private static void drawPaste(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 4.0, 4.4, 16.0, 16.6, 2.2);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 4.0, 4.4, 16.0, 16.6, 2.2);
        roundRectFill(g, 8.6, 2.4, 6.8, 4.2, 1.4);
        g.setColor(AppColors.ICON_LINE_MUTED);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        line(g,  7.4, 11.0, 16.6, 11.0);
        line(g,  7.4, 14.2, 16.6, 14.2);
        line(g,  7.4, 17.4, 13.4, 17.4);
        g.setStroke(AppTheme.STROKE_ICON);
    }

    /** Lineal mit Teilstrichen — vorher eine Wellenlinie. */
    /**
     * Zeilenumbruch: obere Reihe über die volle Breite, darunter eine kürzere
     * zweite Reihe und ein Pfeil, der von rechts oben hinunterführt.
     *
     * <p>Die zweite Reihe ist <b>kürzer</b>, nicht gleich lang — zwei gleich
     * lange Balken sähen wie eine Liste aus und nicht wie ein Umbruch. Der
     * Pfeil steht rechts, weil dort umgebrochen wird.
     */
    private static void drawRowWrap(Graphics2D g, PaintColors c) {
        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 2.0,  4.0, 20.0, 5.4, 1.6);
        roundRectFill(g, 2.0, 14.6, 11.0, 5.4, 1.6);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 2.0,  4.0, 20.0, 5.4, 1.6);
        roundRect(g, 2.0, 14.6, 11.0, 5.4, 1.6);
        // Umbruchpfeil: von der oberen Reihe hinunter und nach links.
        line(g, 19.4, 11.2, 19.4, 17.3);
        line(g, 19.4, 17.3, 16.2, 17.3);
        line(g, 16.2, 17.3, 17.8, 15.7);
        line(g, 16.2, 17.3, 17.8, 18.9);
    }

    private static void drawRuler(Graphics2D g, PaintColors c) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-20), 12, 12);
        g.setColor(AppColors.ICON_FILL);
        roundRectFill(g, 1.6, 8.4, 20.8, 7.2, 1.4);
        g.setColor(AppColors.ICON_LINE);
        roundRect(g, 1.6, 8.4, 20.8, 7.2, 1.4);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        for (int i = 1; i <= 6; i++) {
            double x = 1.6 + i * (20.8 / 7.0);
            double h = (i % 2 == 0) ? 4.0 : 2.4;
            line(g, x, 8.4, x, 8.4 + h);
        }
        g.setStroke(AppTheme.STROKE_ICON);
        g.setTransform(old);
    }

    /**
     * Kantenglättung: harte Treppe links, weiche Kante rechts.
     * <p>Die Treppe ist <b>gefüllt</b>, nicht gestrichelt — als Linienzug
     * verschwand sie bei kleiner Darstellung zu einer schrägen Linie und war
     * damit von der weichen Kante daneben nicht mehr zu unterscheiden.
     */
    private static void drawAntialias(Graphics2D g, PaintColors c) {
        Path2D.Float hard = path();
        hard.moveTo(1.6, 21.0);
        for (int i = 0; i < 5; i++) {
            double x = 1.6 + i * 1.9;
            double y = 21.0 - i * 3.8;
            hard.lineTo(x + 1.9, y);
            hard.lineTo(x + 1.9, y - 3.8);
        }
        hard.lineTo(1.6, 2.0);
        hard.closePath();
        g.setColor(AppColors.ICON_LINE);
        g.fill(hard);

        // Weiche Kante als gestaffelte Flächen — NICHT als Linien wachsender
        // Breite: dafür bräuchte es je Stufe ein new BasicStroke(...) im
        // Zeichenpfad, und das ist gleich zweimal verboten (§21 Literale,
        // Univ. §13 Allokation je Zeichnung).
        for (int i = 4; i >= 0; i--) {
            g.setColor(AppTheme.alpha(AppColors.ICON_LINE, 255 - i * 46));
            double dx = i * 1.3;
            Path2D.Float band = path();
            band.moveTo(13.4 - dx, 21.0);
            band.lineTo(22.4 - dx,  2.0);
            band.lineTo(22.4,       2.0);
            band.lineTo(22.4,      21.0);
            band.closePath();
            g.fill(band);
        }
    }

    /**
     * Zauberstab-Einstellungen: Stab <b>mit Zahnrad</b>.
     * <p>Der Knopf öffnet das Zauberstab-Panel — er ist selbst kein
     * Zauberstab. Vorher trug er dasselbe „⚡" wie sechs Werkzeuge.
     */
    private static void drawWandPanel(Graphics2D g, PaintColors c) {
        AffineTransform old = g.getTransform();
        g.scale(0.74, 0.74);
        drawWandBase(g, c);
        g.setTransform(old);

        g.setColor(AppTheme.alpha(AppColors.BG_DARK, 200));
        roundRectFill(g, 12.2, 12.2, 11.6, 11.6, 3.4);

        g.setColor(AppColors.ICON_LINE);
        g.setStroke(AppTheme.STROKE_ICON_FINE);
        double cx = 18.0, cy = 18.0;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            line(g, cx + Math.cos(a) * 2.4, cy + Math.sin(a) * 2.4,
                    cx + Math.cos(a) * 4.2, cy + Math.sin(a) * 4.2);
        }
        oval(g, cx - 2.9, cy - 2.9, 5.8, 5.8);
        g.setStroke(AppTheme.STROKE_ICON);
    }
}
