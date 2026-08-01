package book;

import java.util.ArrayList;
import java.util.List;

/**
 * Die eine Papierformat-Tabelle des Projekts: Maße in Millimetern plus
 * Standard-Ränder je Format.
 *
 * <p>Sie ist die Quelle für <em>alle</em> Formatauswahlen — den Dialog „Neue
 * Datei", „Neues Blatt", „Neue Seite" und (seit 2026-08-01) die Formatliste
 * der Seitenlayout-Leiste über {@code paint.PageLayout}. Sie zeichnet nicht,
 * speichert nicht und kennt keine Pixel außer über {@link #mmToPx(double)}.
 *
 * <p><b>Zwei Dinge, die nicht „vereinfacht" werden dürfen:</b>
 * <ul>
 *   <li><b>Neue Formate werden ANGEHÄNGT, nie eingefügt.</b> Drei Dialoge
 *       indizierten bis 2026-08-01 mit {@code values()} in ihre Auswahlliste;
 *       ein Einfügen in der Mitte hätte jede bestehende Auswahl verschoben.
 *       Sie gehen inzwischen über {@link #selectable()} — die Reihenfolge der
 *       Anzeige steht dort, nicht in der Deklarationsreihenfolge.</li>
 *   <li><b>Der Anzeigename ist Teil eines geschriebenen Formats.</b>
 *       {@code PageLayoutManifest} legt ihn als {@code paperFormat: A4} neben
 *       jeder Buchseite ab. Ein Name darf sich deshalb nicht ändern, und
 *       {@link #byDisplayName(String)} findet zusätzlich den enum-Namen —
 *       alte {@code .layout}-Dateien müssen unverändert laden.</li>
 * </ul>
 *
 * <p>Alle Formate sind im <b>Hochformat</b> deklariert (Breite ≤ Höhe); das
 * Querformat entsteht durch Tauschen, nicht durch einen zweiten Eintrag.
 */
public class PaperFormat {

    /** Millimeter → Pixel bei den projektweiten 96 dpi. */
    public static double mmToPx(double mm) {
        return mm * 96.0 / 25.4;
    }

    public enum Format {
        // ── Bestand: Reihenfolge und Werte unverändert ───────────────────────
        A0(841, 1189),
        A1(594, 841),
        A2(420, 594),
        A3(297, 420),
        A4(210, 297),

        LETTER(216, 279, "Letter"),
        LEGAL(216, 356, "Legal"),
        TABLOID(279, 432, "Tabloid"),

        // Dokument-Varianten: KEINE eigenen Papierformate, sondern Rand-Vorgaben.
        // Sie stehen seit 2026-08-01 nicht mehr in der Formatauswahl (siehe
        // selectable()), sondern speisen die mitgelieferten Vorlagen.
        A0_DOC(841, 1189, 60, 60, 70, 50, "A0 (Dokument)", true),
        A1_DOC(594, 841, 45, 45, 50, 40, "A1 (Dokument)", true),
        A2_DOC(420, 594, 35, 35, 40, 30, "A2 (Dokument)", true),
        A3_DOC(297, 420, 30, 30, 35, 25, "A3 (Dokument)", true),
        A4_DOC(210, 297, 22, 28, 28, 20, "A4 (Dokument)", true),

        LETTER_DOC(216, 279, 22, 28, 28, 20, "Letter (Dokument)", true),
        LEGAL_DOC(216, 356, 22, 28, 28, 20, "Legal (Dokument)", true),
        TABLOID_DOC(279, 432, 30, 30, 35, 25, "Tabloid (Dokument)", true),

        // ── Neu 2026-08-01: ANGEHÄNGT, nicht eingefügt ───────────────────────
        A5(148, 210),
        A6(105, 148),

        // Postkarten. „Postkarte" im Alltag ist A6 quer — dafür gibt es keinen
        // eigenen Eintrag, sondern A6 mit Querformat. Hier stehen nur die
        // Formate, die es sonst nicht gäbe.
        DIN_LANG(99, 210, "DIN lang"),
        US_POSTCARD(102, 152, "US-Postkarte (4 x 6\")"),
        PHOTO_13X18(127, 178, "Foto 13 x 18 (5 x 7\")"),

        // Weitere amerikanische Formate.
        // *** LEDGER GIBT ES HIER BEWUSST NICHT. *** Ledger (11 x 17") ist
        // Tabloid (17 x 11") im Querformat — beim ersten Anlauf stand es als
        // eigener Eintrag drin und war Zeile für Zeile mit TABLOID identisch
        // (279 x 432 mm, gemessen). Ein zweiter Name für dasselbe Format ist
        // genau die Sorte Eintrag, die eine Liste unglaubwürdig macht.
        EXECUTIVE(184, 267, "Executive"),
        HALF_LETTER(140, 216, "Half Letter");

        private final int width;
        private final int height;

        private final int marginTop;
        private final int marginBottom;
        private final int marginInner;
        private final int marginOuter;

        private final String displayName;
        private final boolean docVariant;

        // Standard ohne spezielle Dokument-Ränder
        Format(int width, int height) {
            this(width, height, 25, 25, 25, 25, null, false);
        }

        // Standardränder, aber eigener Anzeigename
        Format(int width, int height, String displayName) {
            this(width, height, 25, 25, 25, 25, displayName, false);
        }

        // Mit individuellen Dokument-Rändern
        Format(int width, int height,
               int marginTop, int marginBottom,
               int marginInner, int marginOuter) {
            this(width, height, marginTop, marginBottom, marginInner, marginOuter, null, false);
        }

        Format(int width, int height,
               int marginTop, int marginBottom,
               int marginInner, int marginOuter,
               String displayName, boolean docVariant) {
            this.width = width;
            this.height = height;
            this.marginTop = marginTop;
            this.marginBottom = marginBottom;
            this.marginInner = marginInner;
            this.marginOuter = marginOuter;
            this.displayName = displayName != null ? displayName : name();
            this.docVariant = docVariant;
        }

        public int getWidthPortrait() {
            return width;
        }

        public int getHeightPortrait() {
            return height;
        }

        public int getWidthLandscape() {
            return height;
        }

        public int getHeightLandscape() {
            return width;
        }

        public String portrait() {
            return width + " x " + height + " mm";
        }

        public String landscape() {
            return height + " x " + width + " mm";
        }

        public int getMarginTop() {
            return marginTop;
        }

        public int getMarginBottom() {
            return marginBottom;
        }

        public int getMarginInner() {
            return marginInner;
        }

        public int getMarginOuter() {
            return marginOuter;
        }

        public int getTextWidth() {
            return width - marginInner - marginOuter;
        }

        public int getTextHeight() {
            return height - marginTop - marginBottom;
        }

        /**
         * Der Name, der in der Oberfläche steht <b>und</b> in
         * {@code page_NNN.layout} geschrieben wird. Er ändert sich nicht.
         */
        public String displayName() {
            return displayName;
        }

        /**
         * True für die {@code *_DOC}-Einträge: das sind Rand-Vorgaben, keine
         * Papierformate. Sie stehen nicht in {@link #selectable()}.
         */
        public boolean isDocVariant() {
            return docVariant;
        }

        /** Anzeigezeile einer Auswahlliste, z. B. {@code "A4  (210 x 297 mm)"}. */
        public String label() {
            return displayName + "  (" + portrait() + ")";
        }

        /** Breite/Höhe in Pixel bei 96 dpi, Ausrichtung berücksichtigt. */
        public int[] sizePx(boolean landscape) {
            int wMm = landscape ? getWidthLandscape()  : getWidthPortrait();
            int hMm = landscape ? getHeightLandscape() : getHeightPortrait();
            return new int[] { (int) Math.round(mmToPx(wMm)), (int) Math.round(mmToPx(hMm)) };
        }
    }

    /**
     * Die Formate in <b>Anzeigereihenfolge</b> — ohne die
     * {@code *_DOC}-Varianten, die Rand-Vorgaben und keine Formate sind.
     *
     * <p>Wer eine Auswahlliste baut, iteriert hierüber und indiziert auch
     * hierin. <b>Nie {@code values()}</b> — dort stehen die Neuzugänge am
     * Ende, weil ein Einfügen in der Mitte jede gespeicherte Auswahl
     * verschieben würde.
     */
    public static List<Format> selectable() {
        return List.of(
                Format.A0, Format.A1, Format.A2, Format.A3, Format.A4,
                Format.A5, Format.A6,
                Format.DIN_LANG, Format.US_POSTCARD, Format.PHOTO_13X18,
                Format.LETTER, Format.LEGAL, Format.TABLOID,
                Format.EXECUTIVE, Format.HALF_LETTER);
    }

    /** Die Anzeigenamen von {@link #selectable()} in derselben Reihenfolge. */
    public static String[] selectableNames() {
        List<Format> fmts = selectable();
        String[] names = new String[fmts.size()];
        for (int i = 0; i < names.length; i++) names[i] = fmts.get(i).displayName();
        return names;
    }

    /** Die Beschriftungen von {@link #selectable()} inklusive Maßangabe. */
    public static String[] selectableLabels() {
        List<Format> fmts = selectable();
        String[] labels = new String[fmts.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = fmts.get(i).label();
        return labels;
    }

    /** Position eines Formats in {@link #selectable()}, oder 0. */
    public static int selectableIndexOf(Format fmt) {
        int i = selectable().indexOf(fmt);
        return i < 0 ? 0 : i;
    }

    /**
     * Findet ein Format über seinen Anzeigenamen — <b>ohne Rücksicht auf
     * Groß-/Kleinschreibung und zusätzlich über den enum-Namen</b>, damit
     * bestehende {@code .layout}-Dateien („A4", „Letter") unverändert laden.
     *
     * @return das Format oder {@code null}, wenn der Name unbekannt ist.
     *         <b>Der Aufrufer muss {@code null} vertragen</b> — eine Datei mit
     *         einem Format, das es nicht mehr gibt, darf nicht werfen.
     */
    public static Format byDisplayName(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (Format f : Format.values()) {
            if (f.displayName().equalsIgnoreCase(n) || f.name().equalsIgnoreCase(n)) return f;
        }
        return null;
    }

    /**
     * Die Dokument-Variante eines Formats, falls es eine gibt — die Quelle der
     * Rand-Vorgaben für die mitgelieferten Vorlagen.
     */
    public static Format docVariantOf(Format base) {
        if (base == null || base.isDocVariant()) return base;
        for (Format f : Format.values()) {
            if (f.isDocVariant() && f.name().equals(base.name() + "_DOC")) return f;
        }
        return null;
    }

    /** Alle Dokument-Varianten (Rand-Vorgaben) in Deklarationsreihenfolge. */
    public static List<Format> docVariants() {
        List<Format> out = new ArrayList<>();
        for (Format f : Format.values()) if (f.isDocVariant()) out.add(f);
        return out;
    }
}
