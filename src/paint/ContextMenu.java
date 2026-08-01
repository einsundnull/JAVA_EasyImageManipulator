package paint;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/**
 * Baukasten für die Rechtsklick-Menüs der Seitenleisten (Bild-, Szenen- und
 * Seitenlisten sowie das Layer-Panel).
 *
 * <p><b>Rolle:</b> Erzeugung und Aussehen eines Menüs — mehr nicht. Die Klasse
 * kennt <b>keine</b> Fachlogik: jeder Eintrag bekommt ein {@code Runnable},
 * das der jeweilige {@code *CallbacksFactory}-Verdrahter mitbringt (§22). Sie
 * lädt nichts, speichert nichts und kennt den Editor nicht.
 *
 * <p><b>Warum eine Fabrik und keine Basisklasse:</b> ein {@link JPopupMenu} hat
 * kein eigenes Verhalten zum Erben — kein ESC, kein Resize, kein Schließen-Knopf.
 * Die Basisklassen-Pflicht aus §2 zielt auf Fenster; hier ist geteiltes
 * <i>Aussehen</i> alles, was gebraucht wird.
 *
 * <p><b>Warum es keine Kürzel-Spalte gibt — das ist eine Entscheidung, kein
 * Vergessen:</b> Der erste Entwurf sah rechts eine Spalte mit „Strg+C“,
 * „Entf“, „F2“ vor. Nachgemessen an der Registry bedeuten diese Tasten im
 * Hauptfenster aber etwas <i>anderes</i> — {@code Strg+C} kopiert die
 * Bildauswahl als Layer, {@code Entf} löscht Auswahlinhalt oder gewählte
 * Layer, {@code F2} wechselt den Vorschau-Modus des Zweitfensters. Ein Kürzel
 * neben „Datei kopieren“ hätte also eine Taste versprochen, die etwas anderes
 * tut. <b>Genau dieser Fehler — von Hand gepflegte Kürzel ohne Handler — hat
 * am 2026-08-01 den Werkzeug-Kürzel-Task ausgelöst.</b> Solange es keine
 * eigenen, in {@link KeyBindings} registrierten Tasten für die Listen gibt,
 * trägt das Menü keine Kürzel (§25). Die <i>Geste</i> selbst steht in der
 * Registry (Scope {@code MOUSE_UI}).
 *
 * <p><b>Live-Zustand:</b> Das Menü wird bei <i>jedem</i> Rechtsklick neu
 * gebaut. Damit stammen „aktiv/grau“ und die Häkchen zwangsläufig aus dem
 * echten Zustand und können nicht desynchronisieren (Univ. §12). Ein
 * zwischengespeichertes {@code JPopupMenu} wäre der nächste Kandidat für ein
 * lügendes Häkchen.
 */
final class ContextMenu {

    /** Höhe/Breite des Häkchen-Symbols. Gezeichnet, nicht getippt — ein
     *  {@code JCheckBoxMenuItem} brächte den hellen System-Kasten mit. */
    private static final int CHECK_SIZE = 12;

    private final JPopupMenu popup = new JPopupMenu();
    /** Ein Trenner ist angefordert, aber noch nicht gesetzt. Siehe {@link #separator()}. */
    private boolean pendingSeparator = false;

    private ContextMenu() {
        popup.setBackground(AppColors.BG_PANEL);
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(AppTheme.PAD_MD, 0, AppTheme.PAD_MD, 0)));
    }

    static ContextMenu create() { return new ContextMenu(); }

    // =========================================================================
    // Einträge
    // =========================================================================

    /** Ein Eintrag. {@code enabled == false} macht ihn grau — nicht unsichtbar. */
    ContextMenu item(String label, boolean enabled, Runnable action) {
        return add(label, enabled, false, null, action);
    }

    ContextMenu item(String label, Runnable action) {
        return item(label, true, action);
    }

    /** Die Standardaktion (das, was ein Doppelklick täte) — fett, immer zuoberst. */
    ContextMenu itemBold(String label, boolean enabled, Runnable action) {
        return add(label, enabled, true, null, action);
    }

    /** Ein Eintrag mit Häkchen. {@code checked} wird beim Bauen abgefragt. */
    ContextMenu check(String label, boolean checked, boolean enabled, Runnable action) {
        return add(label, enabled, false, new CheckIcon(checked), action);
    }

    /**
     * Fordert einen Trenner an. Er entsteht erst, wenn danach wirklich ein
     * Eintrag folgt — dadurch dürfen Aufrufer Trenner bedingungslos setzen,
     * ohne führende, doppelte oder abschließende Linien zu erzeugen.
     */
    ContextMenu separator() {
        pendingSeparator = true;
        return this;
    }

    private ContextMenu add(String label, boolean enabled, boolean bold, Icon icon, Runnable action) {
        if (pendingSeparator) {
            pendingSeparator = false;
            if (popup.getComponentCount() > 0) {
                JSeparator sep = new JSeparator();
                sep.setForeground(AppColors.BORDER);
                sep.setBackground(AppColors.BG_PANEL);
                popup.add(sep);
            }
        }

        JMenuItem mi = new JMenuItem(label);
        mi.setOpaque(true);
        mi.setBackground(AppColors.BG_PANEL);
        mi.setForeground(enabled ? AppColors.TEXT : AppColors.TEXT_MUTED);
        mi.setFont(bold ? AppTheme.FONT_BASE_BOLD : AppTheme.FONT_BASE);
        mi.setBorder(BorderFactory.createEmptyBorder(
                AppTheme.PAD_LG, AppTheme.PAD_XL, AppTheme.PAD_LG, AppTheme.PAD_XL));
        mi.setEnabled(enabled);
        // Ein leeres Icon gleicher Breite hält die Beschriftungen bündig, auch
        // wenn nur ein Teil der Einträge ein Häkchen tragen kann.
        mi.setIcon(icon != null ? icon : new CheckIcon(false));
        mi.setIconTextGap(AppTheme.GAP_MD);

        // Der Hover-Ton wird über das Modell gesetzt, nicht über mouseEntered:
        // so leuchtet auch die Tastatur-Navigation (Pfeiltasten) mit.
        mi.getModel().addChangeListener(e -> {
            boolean armed = mi.getModel().isArmed() && mi.isEnabled();
            mi.setBackground(armed ? AppColors.ACCENT_ACTIVE : AppColors.BG_PANEL);
        });

        if (action != null) mi.addActionListener(e -> action.run());
        popup.add(mi);
        return this;
    }

    // =========================================================================
    // Wiederverwendbare Einträge, die keine Fachlogik brauchen
    // =========================================================================

    /**
     * „Im Explorer zeigen“ — öffnet den <b>Ordner</b> der Datei. Auf Plattformen
     * ohne Desktop-Unterstützung bleibt der Eintrag grau statt zu scheitern.
     */
    ContextMenu itemShowInExplorer(File file) {
        boolean ok = file != null && file.getParentFile() != null
                && java.awt.Desktop.isDesktopSupported()
                && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN);
        return item("Im Explorer zeigen", ok, () -> {
            try {
                java.awt.Desktop.getDesktop().open(file.getParentFile());
            } catch (Exception ex) {
                System.err.println("[ContextMenu] Explorer failed: " + ex.getMessage());
            }
        });
    }

    // =========================================================================
    // Anzeigen
    // =========================================================================

    boolean isEmpty() { return popup.getComponentCount() == 0; }

    void show(Component invoker, int x, int y) {
        if (isEmpty()) return;
        popup.show(invoker, x, y);
    }

    // =========================================================================
    // Auslöser
    // =========================================================================

    /**
     * Hängt die Rechtsklick-Erkennung an eine Komponente.
     *
     * <p><b>Drei Dinge, die hier nicht „vereinfacht“ werden dürfen:</b>
     * <ol>
     *   <li>Geprüft wird {@link MouseEvent#isPopupTrigger()} in
     *       <b>mousePressed UND mouseReleased</b>. Unter Windows meldet erst
     *       das Loslassen den Trigger, unter anderen Systemen das Drücken —
     *       wer nur eine der beiden Stellen bedient, bekommt auf der jeweils
     *       anderen Plattform kein Menü. Das bisherige
     *       {@code mouseClicked}+{@code isRightMouseButton} ist beides nicht.</li>
     *   <li><b>Der Zieh-Wächter.</b> Rechts-Ziehen in den Seitenleisten ist
     *       eine registrierte Geste („Datei in eine andere Liste kopieren“,
     *       §25). Nach einer Kopier-Bewegung darf kein Menü aufgehen, deshalb
     *       merkt sich der Listener den Druckpunkt und schweigt, sobald die
     *       Maus mehr als {@link #DRAG_TOLERANCE} Pixel gewandert ist.</li>
     *   <li>Das Menü wird bei jedem Klick <b>neu gebaut</b> (Live-Zustand,
     *       Univ. §12).</li>
     * </ol>
     *
     * @param comp   die Kachel oder Zeile, die das Menü tragen soll
     * @param filler füllt das frische Menü; fügt es nichts hinzu, erscheint nichts
     */
    static void install(JComponent comp, BiConsumer<MouseEvent, ContextMenu> filler) {
        MouseAdapter ma = new MouseAdapter() {
            private Point pressPoint = null;

            @Override public void mousePressed(MouseEvent e) {
                pressPoint = e.getPoint();
                maybeShow(e);
            }

            @Override public void mouseReleased(MouseEvent e) {
                maybeShow(e);
                pressPoint = null;
            }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                if (draggedAway(e)) return;      // Drag-to-Copy hat Vorrang
                ContextMenu m = ContextMenu.create();
                filler.accept(e, m);
                if (m.isEmpty()) return;
                e.consume();
                m.show(comp, e.getX(), e.getY());
            }

            private boolean draggedAway(MouseEvent e) {
                return pressPoint != null
                        && pressPoint.distance(e.getPoint()) > DRAG_TOLERANCE;
            }
        };
        comp.addMouseListener(ma);
    }

    /** Ab dieser Bewegung gilt der Rechtsklick als Ziehen, nicht als Klick. */
    private static final double DRAG_TOLERANCE = 5.0;

    // =========================================================================
    // Häkchen — gezeichnet
    // =========================================================================

    /**
     * Ein Häkchen oder nichts, in der Textfarbe.
     *
     * <p>Ein {@code JCheckBoxMenuItem} wäre der naheliegende Weg und ist der
     * falsche: es bringt den hellgrauen Kasten des Systems mit — derselbe
     * Stilbruch, den {@code JOptionPane} in diese dunkle Oberfläche trägt
     * (§20). Zwölf Zeichenbefehle sind billiger als eine Ausnahme.
     */
    private static final class CheckIcon implements Icon {
        private final boolean checked;
        CheckIcon(boolean checked) { this.checked = checked; }

        @Override public int getIconWidth()  { return CHECK_SIZE; }
        @Override public int getIconHeight() { return CHECK_SIZE; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            if (!checked) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = c.isEnabled() ? AppColors.TEXT : AppColors.TEXT_MUTED;
            g2.setColor(fg);
            g2.setStroke(AppTheme.STROKE_MEDIUM);
            g2.drawLine(x + 2, y + 6, x + 5, y + 9);
            g2.drawLine(x + 5, y + 9, x + 10, y + 3);
            g2.dispose();
        }
    }
}
