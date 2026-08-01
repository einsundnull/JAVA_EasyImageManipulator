package paint;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Register aller Tastatur- und Maus-Funktionen — die einzige Quelle (§25).
 *
 * <p><b>Neue Taste oder Geste → ZUERST ein Eintrag in {@link #ALL}, DANN der
 * Handler-Code.</b> Es gibt keinen stillen Shortcut. {@code KeyboardShortcutManager}
 * und {@code CanvasPanel} <i>verdrahten</i> die Funktionen; ihre Beschreibung
 * steht hier.
 *
 * <p>Aus dieser Liste speist sich der {@link KeyBindingsDialog} (SHIFT+F1) —
 * er enthält <b>keinen</b> eigenen Text. Eine Funktion ohne Eintrag ist in der
 * Oberfläche nicht auffindbar.
 *
 * <p><b>Beschriftung deutsch</b> (Strg, Umschalt, Entf, Rücktaste): so steht es
 * auf einer deutschen Tastatur, und die Oberfläche ist ebenfalls deutsch
 * (Entscheidung 2026-07-30).
 *
 * <p><b>Stand der Erfassung:</b> aus {@code KeyboardShortcutManager} und
 * {@code CanvasPanel} ausgelesen, nicht aus {@code doc/Shortcut Table.txt} —
 * die kannte 13 von über 40 Belegungen und ist überholt.
 */
final class KeyBindings {

    private KeyBindings() {}

    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CTRL  = InputEvent.CTRL_DOWN_MASK;

    /**
     * Umbruch der Mal-Leiste (2026-08-01) — Taste, Modifikatoren und
     * Beschriftung an <b>einer</b> Stelle.
     *
     * <p>Aus dieser einen Angabe speisen sich der Eintrag in {@link #ALL}, die
     * Verdrahtung im {@code KeyboardShortcutManager} <i>und</i> der Tooltip des
     * Knopfes in der Leiste. Dieselbe Zusage wie bei
     * {@link #TOOL_KEYS}: eine Taste wird nirgends ein zweites Mal getippt.
     *
     * <p><b>Warum {@code Strg + Umschalt + R}:</b> die einfachen Buchstaben
     * sind seit dem 2026-08-01 Werkzeug-Kürzel, {@code R} allein dreht das
     * Bild, und {@code F1–F7} sind vollständig vergeben. Frei war die
     * Dreier-Kombination — {@code R} wie „Reihen".
     */
    static final int    ROW_WRAP_KEY       = KeyEvent.VK_R;
    static final int    ROW_WRAP_MODIFIERS = CTRL | SHIFT;
    static final String ROW_WRAP_COMBO     = "Strg + Umschalt + R";

    /** Wo eine Belegung gilt. Die Reihenfolge ist die Anzeigereihenfolge. */
    enum Scope {
        GLOBAL   ("Global — wirkt in jedem Fenster"),
        WINDOW   ("Hauptfenster — wenn das Hauptfenster den Fokus hat"),
        // Die Bedingung steht EINMAL im Titel statt 26-mal in den Zeilen:
        // jede Belegung dieses Abschnitts trägt dieselbe.
        TOOL     ("Werkzeug wählen — nur bei sichtbarer Mal-Leiste, nicht während der Textbearbeitung"),
        CANVAS   ("Zeichenfläche"),
        TEXT     ("Textbearbeitung — überschreibt die Belegung darüber"),
        MOUSE    ("Maus auf der Zeichenfläche"),
        MOUSE_UI ("Maus in den Seitenleisten");

        final String title;
        Scope(String title) { this.title = title; }
    }

    /**
     * Eine Belegung.
     *
     * @param scope       wo sie gilt
     * @param combo       Tastenkürzel oder Geste, deutsch beschriftet
     * @param description was sie tut — ganzer Satz, wird im Dialog umgebrochen
     * @param condition   Einschränkung in Klammern, oder "" wenn keine
     */
    record KeyBinding(Scope scope, String combo, String description, String condition) {
        KeyBinding(Scope scope, String combo, String description) {
            this(scope, combo, description, "");
        }
    }

    /** Ein Ablauf in Klick-Reihenfolge für den Abschnitt „Anleitung“. */
    record GuideEntry(String title, List<String> steps) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Tastatur und Maus
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Alles außer den Werkzeug-Tasten. <b>Nicht direkt benutzen</b> — die
     * vollständige Liste ist {@link #ALL}; sie hängt die aus {@link #TOOL_KEYS}
     * erzeugten Einträge an.
     */
    private static final List<KeyBinding> BASE = List.of(

        // ── Global: KeyboardShortcutManager, KeyEventDispatcher ──────────────
        new KeyBinding(Scope.GLOBAL, "F1",  "Zweitfenster ein- oder ausblenden"),
        new KeyBinding(Scope.GLOBAL, "F2",  "Vorschau-Modus wechseln: Schnappschuss, live, live mit Bearbeitung"),
        new KeyBinding(Scope.GLOBAL, "F3",  "Schnappschuss neu aufnehmen"),
        new KeyBinding(Scope.GLOBAL, "F4",  "Zweitfenster als Vollbild"),
        new KeyBinding(Scope.GLOBAL, "F5",  "Fenster-Ebene wechseln: nach vorn, normal, nach hinten"),
        new KeyBinding(Scope.GLOBAL, "F6",  "Inhalt des Zweitfensters auf den Canvas anwenden"),
        new KeyBinding(Scope.GLOBAL, "F7",  "Canvas-Anzeige wechseln: nur I, nur II, aktiver"),
        new KeyBinding(Scope.GLOBAL, "Umschalt + F1", "Diese Übersicht öffnen"),
        new KeyBinding(Scope.GLOBAL, "Alt + T", "Textfeld im Zweitfenster anzeigen", "nur wenn das Zweitfenster offen ist"),
        new KeyBinding(Scope.GLOBAL, "Alt + P", "Mal-Leiste als schwebendes Fenster ein- oder andocken"),

        // ── Hauptfenster: InputMap WHEN_IN_FOCUSED_WINDOW ────────────────────
        new KeyBinding(Scope.WINDOW, "Strg + C", "Auswahl kopieren — sie wird sofort ein Layer"),
        new KeyBinding(Scope.WINDOW, "Strg + Umschalt + C", "Bereich außerhalb der Auswahl kopieren; die Auswahl wird ausgestanzt"),
        new KeyBinding(Scope.WINDOW, "Strg + X", "Auswahl ausschneiden — Layer entsteht, Canvas wird an der Stelle geleert"),
        new KeyBinding(Scope.WINDOW, "Strg + Umschalt + X", "Bereich außerhalb der Auswahl ausschneiden"),
        new KeyBinding(Scope.WINDOW, "Strg + V", "Einfügen — schwebt zunächst, Enter legt es fest"),
        new KeyBinding(Scope.WINDOW, "Strg + A", "Alle Layer auswählen", "nur im Malmodus"),
        new KeyBinding(Scope.WINDOW, "Umschalt + Alt + A", "Alle Layer auswählen", "in jedem Modus"),
        new KeyBinding(Scope.WINDOW, "Strg + Z", "Rückgängig — bricht zuerst eine schwebende Auswahl ab"),
        new KeyBinding(Scope.WINDOW, "Strg + Y", "Wiederholen"),
        new KeyBinding(Scope.WINDOW, "Strg + S", "Speichern"),
        new KeyBinding(Scope.WINDOW, "Strg + Alt + S", "In die Originaldatei speichern"),
        new KeyBinding(Scope.WINDOW, "Strg + Umschalt + S", "Kopie speichern, Layer eingebrannt"),
        new KeyBinding(Scope.WINDOW, "Strg + Alt + Umschalt + S", "Originaldatei speichern, Layer eingebrannt"),
        // Die drei einfachen Buchstaben-Belegungen des Fensters wirken seit dem
        // 2026-08-01 NICHT mehr während der Textbearbeitung. Vorher drehte ein
        // „r" mitten im Text das Bild um 90 Grad (siehe Scope TOOL).
        new KeyBinding(Scope.WINDOW, "R", "Um 90 Grad im Uhrzeigersinn drehen", "nicht während der Textbearbeitung"),
        new KeyBinding(Scope.WINDOW, "Umschalt + R", "Um 90 Grad gegen den Uhrzeigersinn drehen", "nicht während der Textbearbeitung"),
        new KeyBinding(Scope.WINDOW, "Umschalt + V", "Sichtbarkeit der gewählten Layer umschalten", "nicht während der Textbearbeitung"),
        // Modifikator-Kombination mit Absicht: einfache Buchstaben sind seit
        // dem 2026-08-01 Werkzeug-Kürzel, F1-F7 sind vollständig vergeben.
        new KeyBinding(Scope.WINDOW, ROW_WRAP_COMBO,
                "Knöpfe der Mal-Leiste umbrechen statt in einer Reihe scrollen",
                "nur bei sichtbarer Mal-Leiste"),
        new KeyBinding(Scope.WINDOW, "Esc", "Schwebende Auswahl abbrechen, sonst Layer-Auswahl aufheben, sonst Rahmen aufheben"),
        new KeyBinding(Scope.WINDOW, "Entf", "Inhalt der Auswahl löschen oder gewählte Layer entfernen"),
        new KeyBinding(Scope.WINDOW, "Rücktaste", "Alles außerhalb der Auswahl löschen — der Außenbereich wird durchsichtig"),
        new KeyBinding(Scope.WINDOW, "Enter", "Schwebende Auswahl festlegen oder gewählte Layer auf den Canvas brennen"),

        // ── Werkzeuge: NICHT hier, sondern in TOOL_KEYS ──────────────────────
        // Die Einträge des Scopes TOOL entstehen weiter unten aus TOOL_KEYS —
        // damit Anzeige, Verdrahtung UND Tooltip aus EINER Tabelle stammen.

        // ── Zeichenfläche ────────────────────────────────────────────────────
        new KeyBinding(Scope.CANVAS, "+  bzw.  Umschalt + =", "Neuen Pfadpunkt hinter dem gewählten einfügen", "nur wenn ein Pfadpunkt gewählt ist"),

        // ── Textbearbeitung (CanvasPanel überschreibt hier) ──────────────────
        new KeyBinding(Scope.TEXT, "Strg + A", "Den ganzen Text markieren"),
        new KeyBinding(Scope.TEXT, "Strg + C / X / V", "Text kopieren, ausschneiden, einfügen — betrifft den Text, nicht das Bild"),
        new KeyBinding(Scope.TEXT, "Strg + Z", "Textänderung zurücknehmen", "eigener Verlauf, getrennt vom Bild"),
        new KeyBinding(Scope.TEXT, "Enter", "Eingabe abschließen"),
        new KeyBinding(Scope.TEXT, "Umschalt + Enter", "Zeilenumbruch im Text"),
        new KeyBinding(Scope.TEXT, "Strg + Rücktaste", "Wort links vom Cursor löschen"),
        new KeyBinding(Scope.TEXT, "Strg + Entf", "Wort rechts vom Cursor löschen"),
        new KeyBinding(Scope.TEXT, "Esc", "Eingabe verwerfen"),

        // ── Maus auf der Zeichenfläche, in Trefferreihenfolge (§22) ──────────
        new KeyBinding(Scope.MOUSE, "Rad", "Senkrecht scrollen"),
        new KeyBinding(Scope.MOUSE, "Umschalt + Rad", "Waagerecht scrollen"),
        new KeyBinding(Scope.MOUSE, "Strg + Rad", "Zoomen, zum Mauszeiger hin"),
        new KeyBinding(Scope.MOUSE, "Strg + Rad", "Ändert die SCHRIFTGRÖSSE statt zu zoomen", "wenn ein Text-Layer gewählt ist"),
        new KeyBinding(Scope.MOUSE, "Alt + T halten + Rad", "Ändert die Deckkraft des gewählten Bild-Layers"),
        new KeyBinding(Scope.MOUSE, "Mittlere Taste ziehen", "Bildausschnitt verschieben"),
        new KeyBinding(Scope.MOUSE, "Strg + links ziehen", "Bildausschnitt verschieben"),
        new KeyBinding(Scope.MOUSE, "Rechts senkrecht ziehen", "Zoomen — ein Prozent je Pixel, ab fünf Pixeln Bewegung"),
        new KeyBinding(Scope.MOUSE, "Rechts klicken", "Mit der Sekundärfarbe malen"),
        new KeyBinding(Scope.MOUSE, "Umschalt + ziehen", "Gummiband: mehrere Layer auf einmal wählen"),
        new KeyBinding(Scope.MOUSE, "Umschalt + klicken", "Layer zur Auswahl hinzufügen oder daraus entfernen"),
        new KeyBinding(Scope.MOUSE, "Doppelklick auf Layer", "Layer bearbeiten"),
        new KeyBinding(Scope.MOUSE, "Doppelklick ins Leere", "Neue Seite anlegen", "nur im Buchmodus"),

        // ── Maus in den Seitenleisten ────────────────────────────────────────
        new KeyBinding(Scope.MOUSE_UI, "Rechts ziehen", "Datei in eine andere Liste kopieren"),
        // Die beiden folgenden teilen sich die Taste mit der Zeile darüber —
        // das Menü erscheint erst beim LOSLASSEN und nur, wenn NICHT gezogen
        // wurde. Der Konflikt ist gewollt und wird hier geführt, nicht
        // verschwiegen (§25).
        new KeyBinding(Scope.MOUSE_UI, "Rechts klicken",
                "Menü der Kachel: öffnen, kopieren, speichern unter, umbenennen, löschen",
                "in den Bild-, Szenen- und Seitenlisten; nicht beim Ziehen"),
        new KeyBinding(Scope.MOUSE_UI, "Rechts klicken",
                "Menü des Layers: duplizieren, sichtbar schalten, exportieren, einbrennen, löschen",
                "im Layer-Panel; nicht beim Ziehen")
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Werkzeug-Tasten — EINE Tabelle für drei Verbraucher
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Eine Werkzeug-Taste.
     *
     * @param tool        das Werkzeug, das die Taste wählt
     * @param keyCode     {@code KeyEvent.VK_*}
     * @param modifiers   {@code InputEvent.*_DOWN_MASK}, 0 für keine
     * @param combo       deutsche Beschriftung für Dialog und Tooltip
     * @param description was das Werkzeug tut — ganzer Satz
     */
    record ToolKey(PaintEngine.Tool tool, int keyCode, int modifiers,
                   String combo, String description) {}

    /**
     * Die Belegung der Werkzeuge — <b>die einzige Quelle</b>.
     *
     * <p>Drei Verbraucher lesen daraus, und keiner tippt sie nach:
     * <ul>
     *   <li>{@link #ALL} — die Zeilen des Hilfe-Dialogs</li>
     *   <li>{@code KeyboardShortcutManager.setupToolKeys} — die Verdrahtung</li>
     *   <li>{@code PaintToolbar.toolInfo} — das Kürzel im Tooltip</li>
     * </ul>
     *
     * <p><b>Genau das war der Fehler, der diesen Task ausgelöst hat:</b> die
     * Tooltips trugen von Hand gepflegte Kürzel „(P)", „(F)", „(R)" — ohne
     * Registry, ohne Handler, und {@code R} war in Wahrheit mit „90 Grad
     * drehen" belegt. Wer dem Tooltip folgte, drehte sein Bild. Eine zweite
     * Liste neben der Registry ist die Ursache des Problems, nicht die Lösung
     * (§25).
     *
     * <p><b>{@code R} fehlt absichtlich</b> — es dreht das Bild und bleibt
     * dabei. Deshalb trägt das Rechteck {@code V} („Viereck").
     *
     * <p><b>Die Taste wählt, sie schaltet nicht ab.</b> Der Knopf schaltet beim
     * zweiten Klick auf „kein Werkzeug"; bei einer Taste sähe dasselbe wie
     * „nichts passiert" aus.
     */
    static final List<ToolKey> TOOL_KEYS = List.of(
        // ── Hauptleiste ──────────────────────────────────────────────────────
        new ToolKey(PaintEngine.Tool.PENCIL,       KeyEvent.VK_P, 0,     "P", "Stift — freihändig malen"),
        new ToolKey(PaintEngine.Tool.FLOODFILL,    KeyEvent.VK_F, 0,     "F", "Fülleimer — zusammenhängende Fläche füllen"),
        new ToolKey(PaintEngine.Tool.LINE,         KeyEvent.VK_L, 0,     "L", "Linie"),
        new ToolKey(PaintEngine.Tool.CIRCLE,       KeyEvent.VK_E, 0,     "E", "Ellipse"),
        new ToolKey(PaintEngine.Tool.RECT,         KeyEvent.VK_V, 0,     "V", "Rechteck — Merkhilfe „Viereck“, weil R das Bild dreht"),
        new ToolKey(PaintEngine.Tool.ERASER,       KeyEvent.VK_G, 0,     "G", "Radierer — Merkhilfe „Gummi“; radiert auf durchsichtig"),
        new ToolKey(PaintEngine.Tool.ERASER_BG,    KeyEvent.VK_G, SHIFT, "Umschalt + G", "Radierer mit Sekundärfarbe statt durchsichtig"),
        new ToolKey(PaintEngine.Tool.ERASER_COLOR, KeyEvent.VK_G, CTRL,  "Strg + G", "Farbtausch — ersetzt die Primär- durch die Sekundärfarbe"),
        new ToolKey(PaintEngine.Tool.EYEDROPPER,   KeyEvent.VK_I, 0,     "I", "Pipette — Farbe vom Bild aufnehmen"),
        new ToolKey(PaintEngine.Tool.SELECT,       KeyEvent.VK_A, 0,     "A", "Auswahl — Rechteck aufziehen"),
        new ToolKey(PaintEngine.Tool.TEXT,         KeyEvent.VK_T, 0,     "T", "Text"),
        new ToolKey(PaintEngine.Tool.PATH,         KeyEvent.VK_B, 0,     "B", "Pfad — Bézierkurve mit Kontrollpunkten"),
        new ToolKey(PaintEngine.Tool.FREE_PATH,    KeyEvent.VK_B, SHIFT, "Umschalt + B", "Freihand-Pfad"),
        new ToolKey(PaintEngine.Tool.SMEAR,        KeyEvent.VK_W, 0,     "W", "Wischen"),

        // ── Zauberstab-Raster: die Zifferreihe in GENAU der Reihenfolge, in
        //    der die Knöpfe im Raster stehen (WandPanel.buildToolGrid).
        //    Die Paare außen/innen teilen sich eine Ziffer über Umschalt —
        //    dieselbe Familienlogik wie G/Umschalt+G und B/Umschalt+B.
        new ToolKey(PaintEngine.Tool.WAND_I,             KeyEvent.VK_1, 0,     "1", "Zauberstab I — Region anderer Farbe"),
        new ToolKey(PaintEngine.Tool.WAND_II,            KeyEvent.VK_2, 0,     "2", "Zauberstab II — bis zur Sekundärfarbe"),
        new ToolKey(PaintEngine.Tool.WAND_III,           KeyEvent.VK_3, 0,     "3", "Zauberstab III — Region durchsichtig machen"),
        new ToolKey(PaintEngine.Tool.WAND_IV,            KeyEvent.VK_4, 0,     "4", "Zauberstab IV — Polygon nach innen zusammenziehen"),
        new ToolKey(PaintEngine.Tool.WAND_REPLACE_OUTER, KeyEvent.VK_5, 0,     "5", "Ring außerhalb der Fläche überschreiben"),
        new ToolKey(PaintEngine.Tool.WAND_REPLACE_INNER, KeyEvent.VK_5, SHIFT, "Umschalt + 5", "Ring innerhalb der Fläche überschreiben"),
        new ToolKey(PaintEngine.Tool.WAND_AA_OUTER,      KeyEvent.VK_6, 0,     "6", "Ring außerhalb weich einblenden (Antialiasing)"),
        new ToolKey(PaintEngine.Tool.WAND_AA_INNER,      KeyEvent.VK_6, SHIFT, "Umschalt + 6", "Ring innerhalb weich einblenden (Antialiasing)"),
        new ToolKey(PaintEngine.Tool.CUT_COLOR,          KeyEvent.VK_7, 0,     "7", "Ausschneiden — alle Pixel der Sekundärfarbe"),
        new ToolKey(PaintEngine.Tool.CUT_UNTIL_COLOR,    KeyEvent.VK_8, 0,     "8", "Ausschneiden — vom Klickpunkt bis zur Sekundärfarbe"),
        new ToolKey(PaintEngine.Tool.CUT_SAME_COLOR,     KeyEvent.VK_9, 0,     "9", "Ausschneiden — zusammenhängende Fläche gleicher Farbe")
    );

    /** Taste, die das Zauberstab-Raster ein- und ausblendet — kein Werkzeug. */
    static final int  WAND_PANEL_KEY   = KeyEvent.VK_Z;
    static final String WAND_PANEL_COMBO = "Z";

    /**
     * Das Kürzel eines Werkzeugs, oder {@code ""} wenn es keines hat.
     * Für den Tooltip in {@code PaintToolbar} — <b>abgeleitet, nicht getippt</b>.
     */
    static String comboFor(PaintEngine.Tool tool) {
        for (ToolKey tk : TOOL_KEYS)
            if (tk.tool() == tool) return tk.combo();
        return "";
    }

    /**
     * Alle Belegungen — {@link #BASE} plus die aus {@link #TOOL_KEYS} erzeugten
     * Werkzeug-Zeilen plus die Taste fürs Zauberstab-Raster.
     */
    static final List<KeyBinding> ALL = buildAll();

    private static List<KeyBinding> buildAll() {
        List<KeyBinding> out = new java.util.ArrayList<>(BASE);
        for (ToolKey tk : TOOL_KEYS)
            out.add(new KeyBinding(Scope.TOOL, tk.combo(), tk.description()));
        out.add(new KeyBinding(Scope.TOOL, WAND_PANEL_COMBO,
                "Zauberstab-Raster ein- oder ausblenden"));
        return List.copyOf(out);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Anleitung — kurze Abläufe in Klick-Reihenfolge
    // *** NEUE FUNKTION = EIN ABSATZ HIER (§25). ***
    // ─────────────────────────────────────────────────────────────────────────
    static final List<GuideEntry> GUIDE = List.of(

        new GuideEntry("Einen Bildausschnitt zu einem Layer machen", List.of(
            "Werkzeug „Auswahl“ wählen",
            "Mit gedrückter linker Taste ein Rechteck aufziehen",
            "Strg + C — der Ausschnitt wird sofort ein eigener Layer",
            "Layer verschieben oder skalieren; Enter brennt ihn auf den Canvas")),

        new GuideEntry("Das Werkzeug mit der Tastatur wechseln", List.of(
            "In den Malmodus wechseln — die Mal-Leiste muss sichtbar sein",
            "P Stift · F Füllen · L Linie · E Ellipse · V Rechteck · G Radierer",
            "I Pipette · A Auswahl · T Text · B Pfad · W Wischen",
            "Umschalt macht die Variante: Umschalt+G radiert mit der Sekundärfarbe, Umschalt+B ist der Freihand-Pfad",
            "Z blendet das Zauberstab-Raster ein; darin liegen die Werkzeuge auf 1 bis 9",
            "Die Taste wählt immer — abschalten lässt sich ein Werkzeug nur über seinen Knopf",
            "Während der Textbearbeitung sind alle diese Tasten stumm; dort schreiben sie Text")),

        new GuideEntry("Eine Datei oder einen Layer über das Kontextmenü bearbeiten", List.of(
            "Rechtsklick auf eine Kachel der Bildliste — das Menü öffnet sich beim Loslassen",
            "Öffnen, in den anderen Canvas öffnen, kopieren, als Layer einfügen",
            "„Speichern unter …“ fragt nach dem Ziel; ist die Datei gerade offen, wird der bearbeitete Stand geschrieben",
            "Umbenennen, duplizieren, löschen — Löschen fragt nach und legt die Datei in den Papierkorb",
            "Rechtsklick auf eine Layer-Kachel bietet dieselben Funktionen wie die farbigen Knöpfe der Kachel",
            "Graue Einträge sind in dieser Lage nicht möglich — sie verschwinden nicht, damit sie auffindbar bleiben",
            "Wer die Kachel mit gedrückter rechter Taste ZIEHT, kopiert sie weiterhin; ein Menü erscheint dann nicht")),

        new GuideEntry("Etwas durchsichtig machen", List.of(
            "In den Alpha-Modus wechseln (obere Leiste)",
            "Bereiche anklicken oder aufziehen — sie werden markiert",
            "„Anwenden“ macht die markierten Bereiche durchsichtig",
            "Strg + S speichert als PNG mit Transparenz")),

        new GuideEntry("Mit zwei Bildern gleichzeitig arbeiten", List.of(
            "Zweiten Canvas über die obere Leiste einblenden",
            "Bild per Ziehen aus der Galerie auf den zweiten Canvas legen",
            "Layer mit Strg + C kopieren und im anderen Canvas mit Strg + V einfügen",
            "Der aktive Canvas ist am hellen Rahmen erkennbar")),

        new GuideEntry("Das Zweitfenster als Vorschau nutzen", List.of(
            "F1 öffnet das Zweitfenster",
            "F2 wechselt zwischen Schnappschuss und Live-Ansicht",
            "F4 schaltet auf Vollbild, F5 legt die Fenster-Ebene fest",
            "F6 überträgt den Inhalt des Zweitfensters zurück auf den Canvas")),

        new GuideEntry("Eine Szene für GameII anlegen", List.of(
            "Projekt über „Schnell öffnen“ wählen oder neu anlegen",
            "Hintergrundbild laden — es wird der erste Eintrag der Szene",
            "Weitere Bilder als Layer einfügen und platzieren",
            "Speichern legt Verzeichnis, Bilder und die Szenen-Datei an",
            "Format und Ablageort: doc/SCENE_FORMAT_READ_WRITE.md"))
    );
}
