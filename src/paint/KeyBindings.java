package paint;

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

    /** Wo eine Belegung gilt. Die Reihenfolge ist die Anzeigereihenfolge. */
    enum Scope {
        GLOBAL   ("Global — wirkt in jedem Fenster"),
        WINDOW   ("Hauptfenster — wenn das Hauptfenster den Fokus hat"),
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
    static final List<KeyBinding> ALL = List.of(

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
        new KeyBinding(Scope.WINDOW, "R", "Um 90 Grad im Uhrzeigersinn drehen"),
        new KeyBinding(Scope.WINDOW, "Umschalt + R", "Um 90 Grad gegen den Uhrzeigersinn drehen"),
        new KeyBinding(Scope.WINDOW, "Umschalt + V", "Sichtbarkeit der gewählten Layer umschalten"),
        new KeyBinding(Scope.WINDOW, "Esc", "Schwebende Auswahl abbrechen, sonst Layer-Auswahl aufheben, sonst Rahmen aufheben"),
        new KeyBinding(Scope.WINDOW, "Entf", "Inhalt der Auswahl löschen oder gewählte Layer entfernen"),
        new KeyBinding(Scope.WINDOW, "Rücktaste", "Alles außerhalb der Auswahl löschen — der Außenbereich wird durchsichtig"),
        new KeyBinding(Scope.WINDOW, "Enter", "Schwebende Auswahl festlegen oder gewählte Layer auf den Canvas brennen"),

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
        new KeyBinding(Scope.MOUSE_UI, "Rechts ziehen", "Datei in eine andere Liste kopieren")
    );

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
